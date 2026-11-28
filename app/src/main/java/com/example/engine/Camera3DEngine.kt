package com.example.engine

import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.model.AnimationProject
import com.example.model.Camera3DKeyframe
import com.example.model.Camera3DSettings
import com.example.model.CameraBuiltInEffect
import com.example.model.CameraEasingCurve
import com.example.model.CameraEffectSpan
import com.example.model.CameraMode
import com.example.model.CameraPostEffectSettings
import com.example.model.CanvasBackgroundSettings
import com.example.model.CanvasBackgroundStyle
import com.example.model.Frame
import com.example.model.GradientColorStop
import com.example.model.LayerTransformTrack
import com.example.model.ObjectShaderMode
import com.example.model.ObjectTransformKeyframe
import com.example.model.ObjectUVMapSlot
import com.example.model.SceneObject3D
import com.example.model.ShaderNodeKind
import com.example.model.SkyBackdropMode
import com.example.model.StageShadingMode
import java.util.Arrays
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

data class ProjectedObjectBounds(
    val id: String,
    val centerX: Float,
    val centerY: Float,
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
    val avgDepth: Float
)

/**
 * 3D Camera, Scene Object & Post-Processing Engine for Kodanima 2D.
 *
 * Provides:
 * - Keyframe interpolation for Camera, 2D Layers, and 3D Scene Objects
 * - Built-in animated camera templates (Zoom In/Out, Pan Cinema, Tremor, Órbita 3D, Dolly Twist)
 * - Fast Z-buffered software triangle rasterizer with Pineda incremental edge stepping, UV texture mapping, and directional lighting
 * - Unified 2D + 3D depth compositing and off-canvas sky/environment rendering
 * - Live & exported post-effects (Vignette, Bloom, Chromatic Aberration, Film Grain, Sepia, Radial Blur, Contrast)
 */
object Camera3DEngine {

    private class Raster3DScratch {
        var pixels = IntArray(0)
        var zBuf = FloatArray(0)
        var ownerBuf = IntArray(0)
        var fxWorkPixels = IntArray(0)

        var projX = FloatArray(0)
        var projY = FloatArray(0)
        var invZ = FloatArray(0)
        var clipZ = FloatArray(0)
        var worldZ = FloatArray(0)
        var shade = FloatArray(0)
        var rimArr = FloatArray(0)

        val drawBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val vignettePaint = Paint()
        val camera = Camera()
        val camMatrix = Matrix()

        fun ensureFrameCapacity(totalPx: Int) {
            if (pixels.size < totalPx) {
                pixels = IntArray(totalPx)
                zBuf = FloatArray(totalPx)
                ownerBuf = IntArray(totalPx)
            }
            Arrays.fill(pixels, 0, totalPx, 0)
            Arrays.fill(zBuf, 0, totalPx, Float.MAX_VALUE)
            Arrays.fill(ownerBuf, 0, totalPx, 0)
        }

        fun ensureVertexCapacity(vCount: Int) {
            if (projX.size < vCount) {
                val nextCap = max(vCount, projX.size * 2).coerceAtLeast(256)
                projX = FloatArray(nextCap)
                projY = FloatArray(nextCap)
                invZ = FloatArray(nextCap)
                clipZ = FloatArray(nextCap)
                worldZ = FloatArray(nextCap)
                shade = FloatArray(nextCap)
                rimArr = FloatArray(nextCap)
            }
        }

        fun ensureFxWorkCapacity(totalPx: Int): IntArray {
            if (fxWorkPixels.size < totalPx) {
                fxWorkPixels = IntArray(totalPx)
            }
            return fxWorkPixels
        }
    }

    private val rasterScratchThreadLocal = ThreadLocal.withInitial { Raster3DScratch() }

    fun solveCubicBezierY(
        targetX: Float,
        p1x: Float = 0.25f,
        p1y: Float = 0.1f,
        p2x: Float = 0.25f,
        p2y: Float = 1.0f
    ): Float {
        val x = targetX.coerceIn(0f, 1f)
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val cx1 = p1x.coerceIn(0.01f, 0.99f)
        val cx2 = p2x.coerceIn(0.01f, 0.99f)

        fun evalX(s: Float): Float {
            val u = 1f - s
            return 3f * u * u * s * cx1 + 3f * u * s * s * cx2 + s * s * s
        }

        fun evalY(s: Float): Float {
            val u = 1f - s
            return 3f * u * u * s * p1y + 3f * u * s * s * p2y + s * s * s
        }

        var lo = 0f
        var hi = 1f
        var s = x
        repeat(16) {
            s = (lo + hi) * 0.5f
            val cur = evalX(s)
            if (cur < x) lo = s else hi = s
        }
        return evalY(s)
    }

    fun applyEasing(
        t: Float,
        curve: CameraEasingCurve,
        p1x: Float = 0.25f,
        p1y: Float = 0.1f,
        p2x: Float = 0.25f,
        p2y: Float = 1.0f
    ): Float {
        val u = t.coerceIn(0f, 1f)
        return when (curve) {
            CameraEasingCurve.LINEAR -> u
            CameraEasingCurve.BEZIER_SUAVE -> u * u * (3f - 2f * u)
            CameraEasingCurve.BEZIER_GRAFICO -> solveCubicBezierY(u, p1x, p1y, p2x, p2y)
            CameraEasingCurve.ACELERAR -> u * u * u
            CameraEasingCurve.DESACELERAR -> 1f - (1f - u).pow(3)
            CameraEasingCurve.DEGRAU -> if (u >= 1f) 1f else 0f
            CameraEasingCurve.ELASTICO -> {
                if (u == 0f || u == 1f) u
                else {
                    val p = 0.35f
                    2f.pow(-10f * u) * sin(((u - p / 4f) * (2f * PI.toFloat())) / p) + 1f
                }
            }
        }
    }

    fun evaluateObjectTransformAtFrame(
        keyframes: List<ObjectTransformKeyframe>,
        frameIndex: Int,
        autoInterpolate: Boolean = true
    ): ObjectTransformKeyframe = evaluateObjectTransformAtTick(keyframes, frameIndex.toFloat(), autoInterpolate)

    fun evaluateObjectTransformAtTick(
        keyframes: List<ObjectTransformKeyframe>,
        tickFloat: Float,
        autoInterpolate: Boolean = true
    ): ObjectTransformKeyframe {
        val frameIndex = tickFloat.roundToInt().coerceAtLeast(0)
        if (keyframes.isEmpty()) return ObjectTransformKeyframe(frameIndex = frameIndex)
        val kfs = keyframes.sortedBy { it.frameIndex }
        val first = kfs.first()
        if (tickFloat <= first.frameIndex.toFloat()) return first.copy(frameIndex = frameIndex)
        val last = kfs.last()
        if (tickFloat >= last.frameIndex.toFloat()) return last.copy(frameIndex = frameIndex)

        var prev = first
        var next = last
        for (i in 0 until kfs.lastIndex) {
            if (kfs[i].frameIndex.toFloat() <= tickFloat && tickFloat <= kfs[i + 1].frameIndex.toFloat()) {
                prev = kfs[i]
                next = kfs[i + 1]
                break
            }
        }
        val prevHoldEnd = prev.frameIndex.toFloat() + max(1, prev.holdTicks) - 1f
        if (!autoInterpolate || prev.frameIndex == next.frameIndex || tickFloat <= prevHoldEnd) {
            return prev.copy(frameIndex = frameIndex)
        }
        val effStart = min(prevHoldEnd, next.frameIndex.toFloat())
        val span = (next.frameIndex.toFloat() - effStart).coerceAtLeast(1f)
        val rawT = ((tickFloat - effStart) / span).coerceIn(0f, 1f)
        val eT = applyEasing(rawT, prev.easing)
        fun lerp(a: Float, b: Float): Float = a + (b - a) * eT
        return ObjectTransformKeyframe(
            frameIndex = frameIndex,
            x = lerp(prev.x, next.x),
            y = lerp(prev.y, next.y),
            z = lerp(prev.z, next.z),
            rotX = lerp(prev.rotX, next.rotX),
            rotY = lerp(prev.rotY, next.rotY),
            rotZ = lerp(prev.rotZ, next.rotZ),
            scaleX = lerp(prev.scaleX, next.scaleX).coerceIn(0.05f, 20f),
            scaleY = lerp(prev.scaleY, next.scaleY).coerceIn(0.05f, 20f),
            scaleZ = lerp(prev.scaleZ, next.scaleZ).coerceIn(0.05f, 20f),
            opacity = lerp(prev.opacity, next.opacity).coerceIn(0f, 1f),
            easing = prev.easing,
            holdTicks = prev.holdTicks
        )
    }

    fun evaluateLayerTransformAtFrame(
        tracks: List<LayerTransformTrack>,
        layerId: String,
        frameIndex: Int,
        autoInterpolate: Boolean = true
    ): ObjectTransformKeyframe = evaluateLayerTransformAtTick(tracks, layerId, frameIndex.toFloat(), autoInterpolate)

    fun evaluateLayerTransformAtTick(
        tracks: List<LayerTransformTrack>,
        layerId: String,
        tickFloat: Float,
        autoInterpolate: Boolean = true
    ): ObjectTransformKeyframe {
        val track = tracks.firstOrNull { it.layerId == layerId }
            ?: return ObjectTransformKeyframe(frameIndex = tickFloat.roundToInt().coerceAtLeast(0))
        return evaluateObjectTransformAtTick(track.keyframes, tickFloat, autoInterpolate)
    }

    fun evaluateCameraAtFrame(settings: Camera3DSettings, frameIndex: Int): Camera3DKeyframe =
        evaluateCameraAtTick(settings, frameIndex.toFloat())

    fun evaluateCameraAtTick(settings: Camera3DSettings, tickFloat: Float): Camera3DKeyframe {
        val frameIndex = tickFloat.roundToInt().coerceAtLeast(0)
        val kfs = settings.keyframes.sortedBy { it.frameIndex }
        var result = if (kfs.isEmpty()) {
            Camera3DKeyframe(
                frameIndex = frameIndex,
                depthSeparation = settings.layerDepthSpacing
            )
        } else {
            val first = kfs.first()
            if (tickFloat <= first.frameIndex.toFloat()) {
                first.copy(frameIndex = frameIndex)
            } else {
                val last = kfs.last()
                if (tickFloat >= last.frameIndex.toFloat()) {
                    last.copy(frameIndex = frameIndex)
                } else {
                    var prev = first
                    var next = last
                    for (i in 0 until kfs.lastIndex) {
                        if (kfs[i].frameIndex.toFloat() <= tickFloat && tickFloat <= kfs[i + 1].frameIndex.toFloat()) {
                            prev = kfs[i]
                            next = kfs[i + 1]
                            break
                        }
                    }
                    val prevHoldEnd = prev.frameIndex.toFloat() + max(1, prev.holdTicks) - 1f
                    if (!settings.autoInterpolate || prev.frameIndex == next.frameIndex || tickFloat <= prevHoldEnd) {
                        prev.copy(frameIndex = frameIndex)
                    } else {
                        val effStart = min(prevHoldEnd, next.frameIndex.toFloat())
                        val span = (next.frameIndex.toFloat() - effStart).coerceAtLeast(1f)
                        val rawT = ((tickFloat - effStart) / span).coerceIn(0f, 1f)
                        val easedT = applyEasing(
                            t = rawT,
                            curve = prev.easing,
                            p1x = prev.bezierP1X,
                            p1y = prev.bezierP1Y,
                            p2x = prev.bezierP2X,
                            p2y = prev.bezierP2Y
                        )
                        fun lerp(a: Float, b: Float): Float = a + (b - a) * easedT
                        Camera3DKeyframe(
                            frameIndex = frameIndex,
                            x = lerp(prev.x, next.x),
                            y = lerp(prev.y, next.y),
                            zoom = lerp(prev.zoom, next.zoom).coerceIn(0.25f, 6.0f),
                            rotationZ = lerp(prev.rotationZ, next.rotationZ),
                            pitchX = lerp(prev.pitchX, next.pitchX),
                            yawY = lerp(prev.yawY, next.yawY),
                            depthSeparation = lerp(prev.depthSeparation, next.depthSeparation),
                            fovDegrees = lerp(prev.fovDegrees, next.fovDegrees).coerceIn(18f, 120f),
                            easing = prev.easing,
                            bezierP1X = prev.bezierP1X,
                            bezierP1Y = prev.bezierP1Y,
                            bezierP2X = prev.bezierP2X,
                            bezierP2Y = prev.bezierP2Y,
                            holdTicks = prev.holdTicks
                        )
                    }
                }
            }
        }

        if (settings.effectSpans.isNotEmpty()) {
            var addX = 0f
            var addY = 0f
            var mulZoom = 1f
            var addRot = 0f
            var addPitch = 0f
            var addYaw = 0f
            val wBase = settings.captureWidth.coerceAtLeast(320).toFloat()
            val hBase = settings.captureHeight.coerceAtLeast(240).toFloat()

            for (span in settings.effectSpans) {
                val sF = min(span.startFrame, span.endFrame).toFloat()
                val eF = max(span.startFrame, span.endFrame).toFloat().coerceAtLeast(sF + 1f)
                if (tickFloat in sF..eF) {
                    val u = ((tickFloat - sF) / (eF - sF)).coerceIn(0f, 1f)
                    val smoothU = u * u * (3f - 2f * u)
                    val inten = span.intensity.coerceIn(0.2f, 2.5f)
                    when (span.effect) {
                        CameraBuiltInEffect.ZOOM_IN -> mulZoom *= (1f + 0.45f * smoothU * inten)
                        CameraBuiltInEffect.ZOOM_OUT -> mulZoom *= (1f + 0.45f * (1f - smoothU) * inten)
                        CameraBuiltInEffect.PAN_CINEMA -> addX += (-0.12f + 0.24f * smoothU) * wBase * inten
                        CameraBuiltInEffect.TREMOR_IMPACTO -> {
                            val decay = 1f - u
                            val signX = if (frameIndex % 2 == 0) 1f else -1f
                            val signY = if (frameIndex % 3 == 0) -1f else 1f
                            addX += signX * wBase * 0.032f * decay * inten
                            addY += signY * hBase * 0.032f * decay * inten
                            addRot += signX * 2.8f * decay * inten
                        }
                        CameraBuiltInEffect.ORBITA_3D -> {
                            val wave = sin(u * PI.toFloat())
                            addYaw += (u * 2f - 1f) * 14f * inten
                            addPitch += -6f * wave * inten
                        }
                        CameraBuiltInEffect.DOLLY_TWIST -> {
                            mulZoom *= (1f + 0.48f * smoothU * inten)
                            addRot += (-6f + 12f * smoothU) * inten
                            addYaw += (-6f + 12f * smoothU) * inten
                        }
                    }
                }
            }
            result = result.copy(
                x = result.x + addX,
                y = result.y + addY,
                zoom = (result.zoom * mulZoom).coerceIn(0.25f, 6.0f),
                rotationZ = (result.rotationZ + addRot).coerceIn(-180f, 180f),
                pitchX = (result.pitchX + addPitch).coerceIn(-45f, 45f),
                yawY = (result.yawY + addYaw).coerceIn(-45f, 45f)
            )
        }

        if (settings.cameraMode == CameraMode.MODE_2D) {
            result = result.copy(pitchX = 0f, yawY = 0f)
        }
        return result
    }

    fun buildBuiltInEffectKeyframes(
        effect: CameraBuiltInEffect,
        totalFrames: Int,
        projectWidth: Int,
        projectHeight: Int,
        depthSpacing: Float = 38f
    ): List<Camera3DKeyframe> {
        val lastIdx = (totalFrames - 1).coerceAtLeast(1)
        val midIdx = (lastIdx / 2).coerceAtLeast(1)
        val wShift = projectWidth * 0.12f
        val hShift = projectHeight * 0.05f

        return when (effect) {
            CameraBuiltInEffect.ZOOM_IN -> listOf(
                Camera3DKeyframe(0, x = 0f, y = 0f, zoom = 1.0f, depthSeparation = depthSpacing, easing = CameraEasingCurve.BEZIER_SUAVE),
                Camera3DKeyframe(lastIdx, x = 0f, y = 0f, zoom = 1.48f, depthSeparation = depthSpacing, easing = CameraEasingCurve.BEZIER_SUAVE)
            )
            CameraBuiltInEffect.ZOOM_OUT -> listOf(
                Camera3DKeyframe(0, x = 0f, y = 0f, zoom = 1.48f, depthSeparation = depthSpacing, easing = CameraEasingCurve.BEZIER_SUAVE),
                Camera3DKeyframe(lastIdx, x = 0f, y = 0f, zoom = 1.0f, depthSeparation = depthSpacing, easing = CameraEasingCurve.BEZIER_SUAVE)
            )
            CameraBuiltInEffect.PAN_CINEMA -> listOf(
                Camera3DKeyframe(0, x = -wShift, y = 0f, zoom = 1.18f, depthSeparation = depthSpacing, easing = CameraEasingCurve.BEZIER_SUAVE),
                Camera3DKeyframe(lastIdx, x = wShift, y = 0f, zoom = 1.18f, depthSeparation = depthSpacing, easing = CameraEasingCurve.BEZIER_SUAVE)
            )
            CameraBuiltInEffect.TREMOR_IMPACTO -> {
                val count = min(max(4, totalFrames), 10)
                (0 until count).map { step ->
                    val fIdx = ((step.toFloat() / (count - 1).coerceAtLeast(1)) * lastIdx).roundToInt()
                    val decay = 1f - (step.toFloat() / count.toFloat())
                    val signX = if (step % 2 == 0) 1f else -1f
                    val signY = if (step % 3 == 0) -1f else 1f
                    Camera3DKeyframe(
                        frameIndex = fIdx,
                        x = signX * wShift * 0.35f * decay,
                        y = signY * hShift * 0.55f * decay,
                        zoom = 1.08f + 0.06f * decay,
                        rotationZ = signX * 2.5f * decay,
                        depthSeparation = depthSpacing,
                        easing = CameraEasingCurve.LINEAR
                    )
                }.distinctBy { it.frameIndex }
            }
            CameraBuiltInEffect.ORBITA_3D -> listOf(
                Camera3DKeyframe(0, zoom = 1.12f, yawY = -14f, pitchX = 4f, depthSeparation = depthSpacing * 1.35f, easing = CameraEasingCurve.BEZIER_SUAVE),
                Camera3DKeyframe(midIdx, zoom = 1.18f, yawY = 0f, pitchX = -6f, depthSeparation = depthSpacing * 1.35f, easing = CameraEasingCurve.BEZIER_SUAVE),
                Camera3DKeyframe(lastIdx, zoom = 1.12f, yawY = 14f, pitchX = 4f, depthSeparation = depthSpacing * 1.35f, easing = CameraEasingCurve.BEZIER_SUAVE)
            ).distinctBy { it.frameIndex }
            CameraBuiltInEffect.DOLLY_TWIST -> listOf(
                Camera3DKeyframe(0, zoom = 1.0f, rotationZ = -6f, yawY = -8f, depthSeparation = depthSpacing, easing = CameraEasingCurve.ACELERAR),
                Camera3DKeyframe(lastIdx, zoom = 1.55f, rotationZ = 6f, yawY = 8f, depthSeparation = depthSpacing, easing = CameraEasingCurve.DESACELERAR)
            )
        }
    }

    /**
     * Composes a local-axis rotation delta (around the object's own tilted X/Y/Z axes)
     * onto the current Euler rotation (rotX, rotY, rotZ) returning updated (rotX, rotY, rotZ) degrees.
     */
    fun composeLocalAxisRotationEuler(
        rxDeg: Float,
        ryDeg: Float,
        rzDeg: Float,
        dLocalX: Float,
        dLocalY: Float,
        dLocalZ: Float
    ): Triple<Float, Float, Float> {
        fun eulerToMat(rx: Double, ry: Double, rz: Double): DoubleArray {
            val ax = Math.toRadians(rx); val ay = Math.toRadians(ry); val az = Math.toRadians(rz)
            val cx = cos(ax); val sx = sin(ax)
            val cy = cos(ay); val sy = sin(ay)
            val cz = cos(az); val sz = sin(az)
            // Intrinsic R = Rz * Ry * Rx
            return doubleArrayOf(
                cy * cz, cz * sx * sy - cx * sz, cx * cz * sy + sx * sz,
                cy * sz, cx * cz + sx * sy * sz, cx * sy * sz - cz * sx,
                -sy,     cy * sx,                cx * cy
            )
        }
        val a = eulerToMat(rxDeg.toDouble(), ryDeg.toDouble(), rzDeg.toDouble())
        val b = eulerToMat(dLocalX.toDouble(), dLocalY.toDouble(), dLocalZ.toDouble())
        val c = DoubleArray(9)
        for (r in 0..2) {
            for (col in 0..2) {
                c[r * 3 + col] = a[r * 3] * b[col] + a[r * 3 + 1] * b[3 + col] + a[r * 3 + 2] * b[6 + col]
            }
        }
        val syClamp = (-c[6]).coerceIn(-1.0, 1.0)
        val outY = Math.toDegrees(kotlin.math.asin(syClamp))
        val outX: Double
        val outZ: Double
        if (abs(syClamp) < 0.9998) {
            outX = Math.toDegrees(kotlin.math.atan2(c[7], c[8]))
            outZ = Math.toDegrees(kotlin.math.atan2(c[3], c[0]))
        } else {
            outX = Math.toDegrees(kotlin.math.atan2(-c[5], c[4]))
            outZ = 0.0
        }
        fun normAngle(deg: Double): Float {
            var d = deg % 360.0
            if (d > 180.0) d -= 360.0
            if (d < -180.0) d += 360.0
            return d.toFloat()
        }
        return Triple(normAngle(outX), normAngle(outY), normAngle(outZ))
    }

    /**
     * Software Z-buffered UV-textured 3D rasterizer used by Camera POV, Exports, Canvas 3D BG, and Stage Viewport.
     * Supports glTF keyframe animation evaluation, near-plane clipping, 3-point studio lighting, and perspective-correct 1/z UVs.
     */
    fun renderSceneObjectsSoftware3D(
        outW: Int,
        outH: Int,
        sceneObjects: List<SceneObject3D>,
        frameIndex: Int,
        autoInterpolate: Boolean,
        camYawDeg: Float,
        camPitchDeg: Float,
        camRollDeg: Float,
        camPanX: Float,
        camPanY: Float,
        focalPx: Float,
        vpCx: Float,
        vpCy: Float,
        worldUnitScale: Float = 1f,
        zNearLimit: Float = -Float.MAX_VALUE,
        zFarLimit: Float = Float.MAX_VALUE,
        selectedObjectId: String? = null,
        tickFloat: Float = frameIndex.toFloat(),
        animTimeSec: Float = 0f,
        reuseBitmap: Bitmap? = null,
        shadingMode: StageShadingMode = StageShadingMode.RENDERED,
        preferHardwareGl: Boolean = true
    ): Pair<Bitmap, Map<String, ProjectedObjectBounds>>? {
        val visibleObjs = sceneObjects.filter { it.isVisible && it.meshAssetPath.isNotBlank() }
        if (visibleObjs.isEmpty() || outW <= 0 || outH <= 0) return null

        if (preferHardwareGl && NativeGL3DEngine.isHardwareAvailable()) {
            NativeGL3DEngine.renderSceneObjects(
                outW = outW,
                outH = outH,
                sceneObjects = visibleObjs,
                autoInterpolate = autoInterpolate,
                camYawDeg = camYawDeg,
                camPitchDeg = camPitchDeg,
                camRollDeg = camRollDeg,
                camPanX = camPanX,
                camPanY = camPanY,
                focalPx = focalPx,
                vpCx = vpCx,
                vpCy = vpCy,
                worldUnitScale = worldUnitScale,
                zNearLimit = zNearLimit,
                zFarLimit = zFarLimit,
                selectedObjectId = selectedObjectId,
                tickFloat = tickFloat,
                animTimeSec = animTimeSec,
                reuseBitmap = reuseBitmap,
                shadingMode = shadingMode
            )?.let { return it }
        }

        val totalPx = outW * outH
        val scratch = rasterScratchThreadLocal.get()!!
        scratch.ensureFrameCapacity(totalPx)
        val pixels = scratch.pixels
        val zBuf = scratch.zBuf
        val ownerBuf = scratch.ownerBuf
        val boundsMap = HashMap<String, ProjectedObjectBounds>(visibleObjs.size)

        val yawRad = Math.toRadians(camYawDeg.toDouble())
        val pitchRad = Math.toRadians(camPitchDeg.toDouble())
        val rollRad = Math.toRadians(camRollDeg.toDouble())
        val cosY = cos(yawRad).toFloat()
        val sinY = sin(yawRad).toFloat()
        val cosP = cos(pitchRad).toFloat()
        val sinP = sin(pitchRad).toFloat()
        val cosR = cos(rollRad).toFloat()
        val sinR = sin(rollRad).toFloat()
        val camDist = focalPx.coerceAtLeast(240f)
        val maxXBound = outW - 1
        val maxYBound = outH - 1

        data class PendingOutlinePass(
            val ownerTag: Int,
            val widthPx: Int,
            val outlineColor: Int,
            val minPx: Int,
            val minPy: Int,
            val maxPx: Int,
            val maxPy: Int
        )
        val pendingOutlines = ArrayList<PendingOutlinePass>(visibleObjs.size)

        // Precompute transformed vertices cleanly using reusable ThreadLocal primitive buffers
        for ((objIdx, obj) in visibleObjs.withIndex()) {
            val ownerTag = objIdx + 1
            val meshData = Mesh3DImporter.getOrLoadMesh(obj.meshAssetPath) ?: continue
            val objTf = evaluateObjectTransformAtTick(obj.keyframes, tickFloat, autoInterpolate)
            if (objTf.opacity <= 0.01f) continue

            // Resolve ShaderNodeGraph overrides when active
            val ng = obj.nodeGraph
            val ngNodes = if (ng.enabled) ng.nodes else emptyList()
            fun findNode(k: ShaderNodeKind) = ngNodes.firstOrNull { it.kind == k }
            val uvNode = findNode(ShaderNodeKind.UV_COORD)
            val lightNode = findNode(ShaderNodeKind.LUZ_OBJETO)
            val celNode = findNode(ShaderNodeKind.CEL_RAMP)
            val brushNode = findNode(ShaderNodeKind.BRUSH_GRAIN)
            val outlineNode = findNode(ShaderNodeKind.CONTORNO)
            val bsdfNode = findNode(ShaderNodeKind.PRINCIPLED_BSDF)
            val fresnelNode = findNode(ShaderNodeKind.FRESNEL)
            val mixRgbNode = findNode(ShaderNodeKind.MIX_RGB)
            val noiseNode = findNode(ShaderNodeKind.NOISE_PROC)
            val emissaoNode = findNode(ShaderNodeKind.EMISSAO)

            val effShader = if (shadingMode == StageShadingMode.WIREFRAME) {
                ObjectShaderMode.ARAMADO
            } else if (shadingMode == StageShadingMode.TEXTURE) {
                ObjectShaderMode.SEM_LUZ
            } else if (ng.enabled) {
                when {
                    brushNode != null && brushNode.paramA > 0.08f -> ObjectShaderMode.CARTOON_BRUSH
                    celNode != null -> ObjectShaderMode.ANIME_CEL
                    else -> obj.shaderMode
                }
            } else obj.shaderMode

            val celSteps = (celNode?.paramA?.roundToInt() ?: 3).coerceIn(2, 6)
            val brushStrength = (brushNode?.paramA ?: if (effShader == ObjectShaderMode.CARTOON_BRUSH) 0.42f else 0f).coerceIn(0f, 1f)
            val brushDensity = (brushNode?.paramB ?: 0.55f).coerceIn(0.15f, 1f)
            val bsdfRoughness = (bsdfNode?.paramA ?: 0.5f).coerceIn(0.05f, 1f)
            val bsdfMetallic = (bsdfNode?.paramB ?: 0f).coerceIn(0f, 1f)
            val fresnelBoost = (fresnelNode?.paramA ?: 0f).coerceIn(0f, 1.2f)
            val mixFactor = (mixRgbNode?.paramA ?: 0f).coerceIn(0f, 1f)
            val mixColor = mixRgbNode?.colorArgb ?: 0xFFFFFFFFL
            val noiseAmt = (noiseNode?.paramA ?: 0f).coerceIn(0f, 0.8f)
            val noiseScale = (noiseNode?.paramB ?: 0.5f).coerceIn(0.1f, 1f)
            val nodeEmissive = emissaoNode?.paramA?.coerceIn(0f, 1.5f) ?: 0f
            val nodeUvScaleMul = (uvNode?.paramA ?: 1f).coerceIn(0.25f, 6f)
            val nodeUvOffsetShift = (uvNode?.paramB ?: 0f)

            val effLightIntensity = (obj.light.intensity * (if (lightNode != null) lightNode.paramA * 1.4f else 1f) * (1f + (1f - bsdfRoughness) * 0.35f)).coerceIn(0.1f, 3f)
            val effAmbient = (obj.light.ambient + (lightNode?.paramB?.let { (it - 0.3f) * 0.6f } ?: 0f) - bsdfMetallic * 0.12f).coerceIn(0.05f, 0.9f)
            val effRim = (obj.light.rimStrength + (celNode?.paramB?.let { it * 0.45f } ?: 0f) + fresnelBoost * 0.65f + bsdfMetallic * 0.2f).coerceIn(0f, 1.5f)
            val effEmissive = (obj.light.emissiveGlow + nodeEmissive).coerceIn(0f, 1.5f)

            val lAzRad = Math.toRadians(obj.light.azimuthDeg.toDouble())
            val lElRad = Math.toRadians(obj.light.elevationDeg.toDouble())
            val lHoriz = cos(lElRad).toFloat()
            val lightDirX = (sin(lAzRad) * lHoriz).toFloat()
            val lightDirY = (-sin(lElRad)).toFloat()
            val lightDirZ = (-cos(lAzRad) * lHoriz).toFloat()
            val mixColR = ((mixColor ushr 16) and 0xFF) / 255f
            val mixColG = ((mixColor ushr 8) and 0xFF) / 255f
            val mixColB = (mixColor and 0xFF) / 255f
            val lightR = ((obj.light.lightColorArgb ushr 16) and 0xFF) / 255f * (1f - mixFactor) + mixColR * mixFactor
            val lightG = ((obj.light.lightColorArgb ushr 8) and 0xFF) / 255f * (1f - mixFactor) + mixColG * mixFactor
            val lightB = (obj.light.lightColorArgb and 0xFF) / 255f * (1f - mixFactor) + mixColB * mixFactor

            val baseSlot = obj.uvSlots.getOrNull(obj.activeUvSlotIndex) ?: obj.uvSlots.firstOrNull() ?: ObjectUVMapSlot()
            val effSlot = if (ng.enabled && uvNode != null) {
                baseSlot.copy(
                    scaleU = baseSlot.scaleU * nodeUvScaleMul,
                    scaleV = baseSlot.scaleV * nodeUvScaleMul,
                    offsetU = baseSlot.offsetU + nodeUvOffsetShift * 0.1f,
                    offsetV = baseSlot.offsetV + nodeUvOffsetShift * 0.1f
                )
            } else baseSlot

            val defaultContornoColor = 0xFF10131CL
            val effOutlineEnabled = if (shadingMode == StageShadingMode.RENDERED) {
                if (ng.enabled && outlineNode != null) (outlineNode.paramB > 0.15f) || obj.outline.enabled else obj.outline.enabled
            } else false
            val effOutlineWidth = if (ng.enabled && outlineNode != null) {
                if (obj.outline.enabled && outlineNode.paramA == 2.0f && obj.outline.width > 2.0f) obj.outline.width else outlineNode.paramA
            } else obj.outline.width
            val effOutlineColor = if (ng.enabled && outlineNode != null) {
                if (obj.outline.enabled && outlineNode.colorArgb == defaultContornoColor && obj.outline.colorArgb != defaultContornoColor) {
                    obj.outline.colorArgb.toInt()
                } else {
                    outlineNode.colorArgb.toInt()
                }
            } else obj.outline.colorArgb.toInt()

            val oxRad = Math.toRadians(objTf.rotX.toDouble())
            val oyRad = Math.toRadians(objTf.rotY.toDouble())
            val ozRad = Math.toRadians(objTf.rotZ.toDouble())
            val cox = cos(oxRad).toFloat(); val sox = sin(oxRad).toFloat()
            val coy = cos(oyRad).toFloat(); val soy = sin(oyRad).toFloat()
            val coz = cos(ozRad).toFloat(); val soz = sin(ozRad).toFloat()

            val posX = objTf.x * worldUnitScale
            val posY = objTf.y * worldUnitScale
            val posZ = objTf.z * worldUnitScale
            val sx = objTf.scaleX * worldUnitScale
            val sy = objTf.scaleY * worldUnitScale
            val sz = objTf.scaleZ * worldUnitScale

            val tintA = objTf.opacity.coerceIn(0f, 1f)
            val tintR = ((obj.colorTint ushr 16) and 0xFF) / 255f
            val tintG = ((obj.colorTint ushr 8) and 0xFF) / 255f
            val tintB = (obj.colorTint and 0xFF) / 255f
            val isSel = obj.id == selectedObjectId

            var bMinX = Float.MAX_VALUE; var bMinY = Float.MAX_VALUE
            var bMaxX = -Float.MAX_VALUE; var bMaxY = -Float.MAX_VALUE
            var depthSum = 0f; var depthCount = 0

            val activeSubMeshes = Mesh3DImporter.evaluateSceneAtTime(meshData, animTimeSec)
            var subMeshVertOffset = 0
            for (sm in activeSubMeshes) {
                val vCount = sm.vertices.size / 3
                if (vCount < 3 || sm.indices.size < 3) {
                    subMeshVertOffset += vCount
                    continue
                }
                scratch.ensureVertexCapacity(vCount)
                val projX = scratch.projX
                val projY = scratch.projY
                val invZArr = scratch.invZ
                val clipZArr = scratch.clipZ
                val worldZArr = scratch.worldZ
                val shadeArr = scratch.shade
                val rimArr = scratch.rimArr
                val verts = sm.vertices
                val norms = sm.normals
                val hasNorms = norms.size >= vCount * 3

                for (vi in 0 until vCount) {
                    val vOff = vi * 3
                    val vx0 = verts[vOff] * sx
                    val vy0 = verts[vOff + 1] * sy
                    val vz0 = verts[vOff + 2] * sz

                    val vy1 = vy0 * cox - vz0 * sox
                    val vz1 = vy0 * sox + vz0 * cox
                    val vx2 = vx0 * coy + vz1 * soy
                    val vz2 = -vx0 * soy + vz1 * coy
                    val vx3 = vx2 * coz - vy1 * soz
                    val vy3 = vx2 * soz + vy1 * coz

                    val wx = vx3 + posX - camPanX
                    val wy = vy3 + posY - camPanY
                    val wz = vz2 + posZ
                    worldZArr[vi] = wz

                    val nx0 = if (hasNorms) norms[vOff] else 0f
                    val ny0 = if (hasNorms) norms[vOff + 1] else -1f
                    val nz0 = if (hasNorms) norms[vOff + 2] else 0f
                    val ny1 = ny0 * cox - nz0 * sox
                    val nz1 = ny0 * sox + nz0 * cox
                    val nx2 = nx0 * coy + nz1 * soy
                    val nz2 = -nx0 * soy + nz1 * coy
                    val nx3 = nx2 * coz - ny1 * soz
                    val ny3 = nx2 * soz + ny1 * coz

                    val dotKey = nx3 * lightDirX + ny3 * lightDirY + nz2 * lightDirZ
                    // View-space facing for rim highlight
                    val viewRz = nx3 * sinY + nz2 * cosY
                    val viewNz = (ny3 * sinP + viewRz * cosP).coerceIn(-1f, 1f)
                    val rimVal = (1f - abs(viewNz)).coerceIn(0f, 1f).pow(2.2f) * effRim
                    val hemi = ((-ny3) * 0.5f + 0.5f).coerceIn(0f, 1f)
                    shadeArr[vi] = (effAmbient + hemi * 0.14f + max(0f, dotKey) * 0.62f * effLightIntensity + effEmissive * 0.55f)
                        .coerceIn(0.12f, 1.35f)
                    rimArr[vi] = rimVal

                    val rx = wx * cosY - wz * sinY
                    val rz = wx * sinY + wz * cosY
                    val ry = wy * cosP - rz * sinP
                    val rz2 = wy * sinP + rz * cosP

                    val clipZ = camDist + rz2
                    clipZArr[vi] = clipZ
                    val depth = clipZ.coerceAtLeast(24f)
                    val invZ = 1f / depth
                    val pScale = camDist * invZ
                    val sx2d = rx * pScale
                    val sy2d = ry * pScale
                    val finalX = vpCx + (sx2d * cosR - sy2d * sinR)
                    val finalY = vpCy + (sx2d * sinR + sy2d * cosR)

                    projX[vi] = finalX
                    projY[vi] = finalY
                    invZArr[vi] = invZ
                    if (clipZ >= 24f) {
                        if (finalX < bMinX) bMinX = finalX; if (finalX > bMaxX) bMaxX = finalX
                        if (finalY < bMinY) bMinY = finalY; if (finalY > bMaxY) bMaxY = finalY
                        depthSum += depth
                        depthCount++
                    }
                }

                // Resolve effective UVs + live composited paint texture (cached in Studio3DPaintEngine)
                val uvs = Studio3DPaintEngine.generateUVsForProjection(verts, sm.uvs, effSlot, subMeshVertOffset)
                subMeshVertOffset += vCount
                val useTexture = shadingMode == StageShadingMode.TEXTURE || shadingMode == StageShadingMode.RENDERED
                val compTex = if (useTexture) {
                    Studio3DPaintEngine.resolveCompositeObjectTexture(
                        obj = obj,
                        basePixels = sm.texturePixels,
                        baseW = sm.textureWidth,
                        baseH = sm.textureHeight,
                        baseColorArgb = sm.baseColorArgb
                    )
                } else null
                val texPix = if (useTexture) (compTex?.pixels ?: sm.texturePixels) else null
                val texW = if (useTexture) (compTex?.width ?: sm.textureWidth) else 0
                val texH = if (useTexture) (compTex?.height ?: sm.textureHeight) else 0
                val hasTex = useTexture && texPix != null && texW > 0 && texH > 0
                val texWMax = texW - 1
                val texHMax = texH - 1

                val baseR = ((sm.baseColorArgb ushr 16) and 0xFF) / 255f
                val baseG = ((sm.baseColorArgb ushr 8) and 0xFF) / 255f
                val baseB = (sm.baseColorArgb and 0xFF) / 255f
                val solidR255 = baseR * tintR * lightR * 255f
                val solidG255 = baseG * tintG * lightG * 255f
                val solidB255 = baseB * tintB * lightB * 255f
                val tintRInv255 = (tintR * lightR) / 255f
                val tintGInv255 = (tintG * lightG) / 255f
                val tintBInv255 = (tintB * lightB) / 255f
                val alphaByteSolid = (tintA * 255f + 0.5f).toInt().coerceIn(0, 255)
                val uvsSize = uvs.size
                val indices = sm.indices
                val indicesLimit = indices.size
                val brushStepPx = max(3.5f, 11f - brushDensity * 7f)

                var tri = 0
                while (tri + 2 < indicesLimit) {
                    val i0 = indices[tri]
                    val i1 = indices[tri + 1]
                    val i2 = indices[tri + 2]
                    tri += 3
                    if (i0 < 0 || i0 >= vCount || i1 < 0 || i1 >= vCount || i2 < 0 || i2 >= vCount) continue
                    if (clipZArr[i0] < 24f || clipZArr[i1] < 24f || clipZArr[i2] < 24f) continue

                    val avgWorldZ = (worldZArr[i0] + worldZArr[i1] + worldZArr[i2]) * (1f / 3f)
                    if (avgWorldZ < zNearLimit || avgWorldZ > zFarLimit) continue

                    val x0 = projX[i0]; val y0 = projY[i0]
                    val x1 = projX[i1]; val y1 = projY[i1]
                    val x2 = projX[i2]; val y2 = projY[i2]

                    val minPx = max(0, floor(min(x0, min(x1, x2))).toInt())
                    val maxPx = min(maxXBound, ceil(max(x0, max(x1, x2))).toInt())
                    if (minPx > maxPx) continue
                    val minPy = max(0, floor(min(y0, min(y1, y2))).toInt())
                    val maxPy = min(maxYBound, ceil(max(y0, max(y1, y2))).toInt())
                    if (minPy > maxPy) continue

                    val area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
                    if (abs(area) < 0.25f) continue
                    val absInv = 1f / abs(area)
                    val sgn = if (area < 0f) -1f else 1f

                    val iz0 = invZArr[i0]
                    val iz1 = invZArr[i1]
                    val iz2 = invZArr[i2]

                    val sz0 = shadeArr[i0] * iz0
                    val sz1 = shadeArr[i1] * iz1
                    val sz2 = shadeArr[i2] * iz2
                    val rz0 = rimArr[i0] * iz0
                    val rz1 = rimArr[i1] * iz1
                    val rz2 = rimArr[i2] * iz2

                    val uvOff0 = i0 * 2
                    val uvOff1 = i1 * 2
                    val uvOff2 = i2 * 2
                    val uz0 = (if (uvOff0 < uvsSize) uvs[uvOff0] else 0f) * iz0
                    val vz0 = (if (uvOff0 + 1 < uvsSize) uvs[uvOff0 + 1] else 0f) * iz0
                    val uz1 = (if (uvOff1 < uvsSize) uvs[uvOff1] else 0f) * iz1
                    val vz1 = (if (uvOff1 + 1 < uvsSize) uvs[uvOff1 + 1] else 0f) * iz1
                    val uz2 = (if (uvOff2 < uvsSize) uvs[uvOff2] else 0f) * iz2
                    val vz2 = (if (uvOff2 + 1 < uvsSize) uvs[uvOff2 + 1] else 0f) * iz2

                    val startX = minPx + 0.5f
                    val startY = minPy + 0.5f
                    val dw0x = (y1 - y2) * sgn; val dw0y = (x2 - x1) * sgn
                    val dw1x = (y2 - y0) * sgn; val dw1y = (x0 - x2) * sgn
                    val dw2x = (y0 - y1) * sgn; val dw2y = (x1 - x0) * sgn

                    var w0Row = ((x1 - startX) * (y2 - startY) - (y1 - startY) * (x2 - startX)) * sgn
                    var w1Row = ((x2 - startX) * (y0 - startY) - (y2 - startY) * (x0 - startX)) * sgn
                    var w2Row = ((x0 - startX) * (y1 - startY) - (y0 - startY) * (x1 - startX)) * sgn

                    var py = minPy
                    while (py <= maxPy) {
                        val rowOff = py * outW
                        var w0 = w0Row
                        var w1 = w1Row
                        var w2 = w2Row
                        var px = minPx
                        while (px <= maxPx) {
                            if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                                val b0 = w0 * absInv
                                val b1 = w1 * absInv
                                val b2 = w2 * absInv
                                val interpIz = b0 * iz0 + b1 * iz1 + b2 * iz2
                                if (interpIz > 1e-6f) {
                                    val depth = 1f / interpIz
                                    val pixIdx = rowOff + px
                                    if (depth < zBuf[pixIdx]) {
                                        val rawShade = (b0 * sz0 + b1 * sz1 + b2 * sz2) * depth
                                        val rim = (b0 * rz0 + b1 * rz1 + b2 * rz2) * depth
                                        var shadeVal = when (effShader) {
                                            ObjectShaderMode.SEM_LUZ -> 1.0f
                                            ObjectShaderMode.ARAMADO -> (rawShade * 0.35f + 0.15f).coerceIn(0.15f, 0.55f)
                                            ObjectShaderMode.ANIME_CEL -> {
                                                val q = floor(rawShade.coerceIn(0f, 1.15f) * celSteps) / celSteps.toFloat()
                                                (0.32f + q * 0.72f + if (rim > 0.32f) 0.24f else 0f).coerceIn(0.28f, 1.25f)
                                            }
                                            ObjectShaderMode.CARTOON_BRUSH -> {
                                                val q = floor(rawShade.coerceIn(0f, 1.15f) * celSteps) / celSteps.toFloat()
                                                val diag = (px + py * 0.65f) / brushStepPx
                                                val strokeWave = abs(diag - floor(diag) - 0.5f) * 2f
                                                val shadowBoost = if (q < 0.55f) (1f - strokeWave * brushStrength * 0.52f) else (1f - strokeWave * brushStrength * 0.12f)
                                                ((0.34f + q * 0.72f) * shadowBoost + if (rim > 0.30f) 0.22f else 0f).coerceIn(0.22f, 1.25f)
                                            }
                                            ObjectShaderMode.PADRAO -> (rawShade + rim * 0.35f).coerceIn(0.18f, 1.25f)
                                        }

                                        if (noiseAmt > 0.01f) {
                                            val nCell = (((px * noiseScale * 0.45f).toInt() * 73856093) xor ((py * noiseScale * 0.45f).toInt() * 19349663)) and 0xFF
                                            shadeVal = (shadeVal * (1f - noiseAmt * 0.35f + (nCell / 255f) * noiseAmt * 0.5f)).coerceIn(0.15f, 1.4f)
                                        }
                                        val isWireEdge = effShader == ObjectShaderMode.ARAMADO && (b0 < 0.05f || b1 < 0.05f || b2 < 0.05f)
                                        val isVertexDot = shadingMode == StageShadingMode.WIREFRAME && (b0 > 0.90f || b1 > 0.90f || b2 > 0.90f)

                                        if (hasTex && !isWireEdge && !isVertexDot) {
                                            val rawU = (b0 * uz0 + b1 * uz1 + b2 * uz2) * depth
                                            val rawV = (b0 * vz0 + b1 * vz1 + b2 * vz2) * depth
                                            var u = rawU - floor(rawU)
                                            var v = rawV - floor(rawV)
                                            if (u < 0f) u += 1f
                                            if (v < 0f) v += 1f
                                            val fx = u * texWMax
                                            val fy = v * texHMax
                                            val x0t = fx.toInt().coerceIn(0, texWMax)
                                            val y0t = fy.toInt().coerceIn(0, texHMax)
                                            val x1t = min(texWMax, x0t + 1)
                                            val y1t = min(texHMax, y0t + 1)
                                            val wx1 = fx - x0t
                                            val wy1 = fy - y0t
                                            val wx0 = 1f - wx1
                                            val wy0 = 1f - wy1
                                            val w00 = wx0 * wy0
                                            val w10 = wx1 * wy0
                                            val w01 = wx0 * wy1
                                            val w11 = wx1 * wy1
                                            val pixArr = texPix!!
                                            val c00 = pixArr[y0t * texW + x0t]
                                            val c10 = pixArr[y0t * texW + x1t]
                                            val c01 = pixArr[y1t * texW + x0t]
                                            val c11 = pixArr[y1t * texW + x1t]
                                            val tcAlphaF = ((c00 ushr 24) and 0xFF) * w00 +
                                                ((c10 ushr 24) and 0xFF) * w10 +
                                                ((c01 ushr 24) and 0xFF) * w01 +
                                                ((c11 ushr 24) and 0xFF) * w11
                                            val tcAlpha = (tcAlphaF + 0.5f).toInt().coerceIn(0, 255)
                                            if (tcAlpha > 5) {
                                                val tcRF = ((c00 ushr 16) and 0xFF) * w00 +
                                                    ((c10 ushr 16) and 0xFF) * w10 +
                                                    ((c01 ushr 16) and 0xFF) * w01 +
                                                    ((c11 ushr 16) and 0xFF) * w11
                                                val tcGF = ((c00 ushr 8) and 0xFF) * w00 +
                                                    ((c10 ushr 8) and 0xFF) * w10 +
                                                    ((c01 ushr 8) and 0xFF) * w01 +
                                                    ((c11 ushr 8) and 0xFF) * w11
                                                val tcBF = (c00 and 0xFF) * w00 +
                                                    (c10 and 0xFF) * w10 +
                                                    (c01 and 0xFF) * w01 +
                                                    (c11 and 0xFF) * w11

                                                var rF = tcRF * tintRInv255 * shadeVal
                                                var gF = tcGF * tintGInv255 * shadeVal
                                                var bF = tcBF * tintBInv255 * shadeVal
                                                if (effEmissive > 0.02f) {
                                                    rF += (tcRF / 255f) * effEmissive * 0.45f
                                                    gF += (tcGF / 255f) * effEmissive * 0.45f
                                                    bF += (tcBF / 255f) * effEmissive * 0.45f
                                                }
                                                if (isSel) {
                                                    rF = rF * 0.88f + 0.12f
                                                    gF = gF * 0.88f + 0.08f
                                                }
                                                val aOut = (tintA * tcAlpha + 0.5f).toInt().coerceIn(0, 255)
                                                val rOut = (rF * 255f + 0.5f).toInt().coerceIn(0, 255)
                                                val gOut = (gF * 255f + 0.5f).toInt().coerceIn(0, 255)
                                                val bOut = (bF * 255f + 0.5f).toInt().coerceIn(0, 255)
                                                if (aOut >= 248 || ownerBuf[pixIdx] == 0) {
                                                    zBuf[pixIdx] = depth
                                                    ownerBuf[pixIdx] = ownerTag
                                                    pixels[pixIdx] = (aOut shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                                                } else {
                                                    val dst = pixels[pixIdx]
                                                    val srcA = aOut / 255f
                                                    val invA = 1f - srcA
                                                    val dr = (dst ushr 16) and 0xFF
                                                    val dg = (dst ushr 8) and 0xFF
                                                    val db = dst and 0xFF
                                                    val da = (dst ushr 24) and 0xFF
                                                    val blendR = (rOut * srcA + dr * invA).toInt().coerceIn(0, 255)
                                                    val blendG = (gOut * srcA + dg * invA).toInt().coerceIn(0, 255)
                                                    val blendB = (bOut * srcA + db * invA).toInt().coerceIn(0, 255)
                                                    val blendA = max(aOut, da)
                                                    pixels[pixIdx] = (blendA shl 24) or (blendR shl 16) or (blendG shl 8) or blendB
                                                }
                                            }
                                        } else if (isVertexDot) {
                                            zBuf[pixIdx] = depth
                                            ownerBuf[pixIdx] = ownerTag
                                            pixels[pixIdx] = (alphaByteSolid shl 24) or 0x00FFAA00
                                        } else if (isWireEdge) {
                                            zBuf[pixIdx] = depth
                                            ownerBuf[pixIdx] = ownerTag
                                            pixels[pixIdx] = (alphaByteSolid shl 24) or 0x0000E5FF
                                        } else {
                                            zBuf[pixIdx] = depth
                                            ownerBuf[pixIdx] = ownerTag
                                            var rOut = (solidR255 * shadeVal + 0.5f).toInt()
                                            var gOut = (solidG255 * shadeVal + 0.5f).toInt()
                                            var bOut = (solidB255 * shadeVal + 0.5f).toInt()
                                            if (isSel) {
                                                rOut = (rOut * 0.88f + 30f).toInt()
                                                gOut = (gOut * 0.88f + 20f).toInt()
                                            }
                                            pixels[pixIdx] = (alphaByteSolid shl 24) or
                                                (rOut.coerceIn(0, 255) shl 16) or
                                                (gOut.coerceIn(0, 255) shl 8) or
                                                bOut.coerceIn(0, 255)
                                        }
                                    }
                                }
                            }
                            w0 += dw0x
                            w1 += dw1x
                            w2 += dw2x
                            px++
                        }
                        w0Row += dw0y
                        w1Row += dw1y
                        w2Row += dw2y
                        py++
                    }
                }
            }

            if (depthCount > 0 && bMinX <= bMaxX && bMinY <= bMaxY) {
                boundsMap[obj.id] = ProjectedObjectBounds(
                    id = obj.id,
                    centerX = (bMinX + bMaxX) * 0.5f,
                    centerY = (bMinY + bMaxY) * 0.5f,
                    minX = bMinX,
                    minY = bMinY,
                    maxX = bMaxX,
                    maxY = bMaxY,
                    avgDepth = depthSum / depthCount.toFloat()
                )
                if (effOutlineEnabled && effOutlineWidth >= 0.5f) {
                    val rad = effOutlineWidth.roundToInt().coerceIn(1, 5)
                    pendingOutlines.add(
                        PendingOutlinePass(
                            ownerTag = ownerTag,
                            widthPx = rad,
                            outlineColor = (alphaByteOutline(objTf.opacity) shl 24) or (effOutlineColor and 0x00FFFFFF),
                            minPx = max(rad, floor(bMinX).toInt() - rad),
                            minPy = max(rad, floor(bMinY).toInt() - rad),
                            maxPx = min(outW - 1 - rad, ceil(bMaxX).toInt() + rad),
                            maxPy = min(outH - 1 - rad, ceil(bMaxY).toInt() + rad)
                        )
                    )
                }
            }
        }

        // Screen-space silhouette Contorno (Outline) dilation pass per object
        for (op in pendingOutlines) {
            val r = op.widthPx
            val tag = op.ownerTag
            val col = op.outlineColor
            for (py in op.minPy..op.maxPy) {
                val rowOff = py * outW
                for (px in op.minPx..op.maxPx) {
                    val idx = rowOff + px
                    if (ownerBuf[idx] == tag) continue
                    var touchesObj = false
                    var oy = -r
                    while (oy <= r && !touchesObj) {
                        val nRow = (py + oy) * outW
                        var ox = -r
                        while (ox <= r) {
                            if (ox * ox + oy * oy <= r * r + 1 && ownerBuf[nRow + px + ox] == tag) {
                                touchesObj = true
                                break
                            }
                            ox++
                        }
                        oy++
                    }
                    if (touchesObj && ownerBuf[idx] == 0) {
                        pixels[idx] = col
                    }
                }
            }
        }

        val outBmp = if (reuseBitmap != null && !reuseBitmap.isRecycled && reuseBitmap.isMutable &&
            reuseBitmap.width == outW && reuseBitmap.height == outH
        ) {
            reuseBitmap
        } else {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        }
        outBmp.setPixels(pixels, 0, outW, 0, 0, outW, outH)
        return outBmp to boundsMap
    }

    private fun alphaByteOutline(opacity: Float): Int = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(24, 255)

    /**
     * Exact ray-triangle screen picking across all visible 3D objects, returning the front-most hit object ID.
     */
    fun pickStageObject3DAtScreenPoint(
        sceneObjects: List<SceneObject3D>,
        frameIndex: Int,
        autoInterpolate: Boolean,
        screenX: Float,
        screenY: Float,
        camYawDeg: Float,
        camPitchDeg: Float,
        camRollDeg: Float,
        camPanX: Float,
        camPanY: Float,
        focalPx: Float,
        vpCx: Float,
        vpCy: Float,
        worldUnitScale: Float,
        tickFloat: Float = frameIndex.toFloat(),
        animTimeSec: Float = 0f
    ): String? {
        var bestId: String? = null
        var bestDepth = Float.MAX_VALUE
        for (obj in sceneObjects) {
            if (!obj.isVisible || obj.meshAssetPath.isBlank()) continue
            val hit = Studio3DPaintEngine.pickObjectTriangleUV(
                obj = obj,
                frameIndex = frameIndex,
                autoInterpolate = autoInterpolate,
                screenX = screenX,
                screenY = screenY,
                camYawDeg = camYawDeg,
                camPitchDeg = camPitchDeg,
                camRollDeg = camRollDeg,
                camPanX = camPanX,
                camPanY = camPanY,
                focalPx = focalPx,
                vpCx = vpCx,
                vpCy = vpCy,
                worldUnitScale = worldUnitScale,
                tickFloat = tickFloat,
                animTimeSec = animTimeSec
            )
            if (hit != null && hit.depth < bestDepth) {
                bestDepth = hit.depth
                bestId = obj.id
            }
        }
        return bestId
    }

    private fun drawStudio3DFloorGridToCanvas(
        canvas: Canvas,
        outW: Float,
        outH: Float,
        camYawDeg: Float,
        camPitchDeg: Float,
        camRollDeg: Float,
        camPanX: Float,
        camPanY: Float,
        focalPx: Float,
        floorWorldScale: Float
    ) {
        val yawRad = Math.toRadians(camYawDeg.toDouble())
        val pitchRad = Math.toRadians(camPitchDeg.toDouble())
        val rollRad = Math.toRadians(camRollDeg.toDouble())
        val cosY = cos(yawRad).toFloat(); val sinY = sin(yawRad).toFloat()
        val cosP = cos(pitchRad).toFloat(); val sinP = sin(pitchRad).toFloat()
        val cosR = cos(rollRad).toFloat(); val sinR = sin(rollRad).toFloat()
        val camDist = focalPx.coerceAtLeast(240f)
        val vpCx = outW * 0.5f
        val vpCy = outH * 0.5f

        val floorY = floorWorldScale * 0.28f
        val gridHalf = floorWorldScale * 0.90f
        val gridPaint = rasterScratchThreadLocal.get()!!.gridPaint

        fun projectFloor(wx: Float, wy: Float, wz: Float): Pair<Float, Float>? {
            val px = wx - camPanX
            val py = wy - camPanY
            val rx = px * cosY - wz * sinY
            val rz = px * sinY + wz * cosY
            val ry = py * cosP - rz * sinP
            val rz2 = py * sinP + rz * cosP
            val clipZ = camDist + rz2
            if (clipZ < 24f) return null
            val scale = camDist / clipZ
            val sx = rx * scale
            val sy = ry * scale
            return (vpCx + sx * cosR - sy * sinR) to (vpCy + sx * sinR + sy * cosR)
        }

        val steps = 10
        val baseStroke = max(1f, min(outW, outH) * 0.0022f)
        for (i in -steps..steps) {
            val t = i.toFloat() / steps.toFloat()
            val pos = t * gridHalf
            val alpha = (44f * (1f - abs(t) * 0.65f).coerceIn(0.25f, 1f)).roundToInt()

            gridPaint.color = if (i == 0) Color.argb(175, 229, 77, 72) else Color.argb(alpha, 210, 222, 240)
            gridPaint.strokeWidth = if (i == 0) baseStroke * 1.6f else baseStroke
            val a1 = projectFloor(-gridHalf, floorY, pos)
            val b1 = projectFloor(gridHalf, floorY, pos)
            if (a1 != null && b1 != null) canvas.drawLine(a1.first, a1.second, b1.first, b1.second, gridPaint)

            gridPaint.color = if (i == 0) Color.argb(175, 86, 189, 98) else Color.argb(alpha, 210, 222, 240)
            gridPaint.strokeWidth = if (i == 0) baseStroke * 1.6f else baseStroke
            val a2 = projectFloor(pos, floorY, -gridHalf)
            val b2 = projectFloor(pos, floorY, gridHalf)
            if (a2 != null && b2 != null) canvas.drawLine(a2.first, a2.second, b2.first, b2.second, gridPaint)
        }
    }

    /**
     * Renders a lightweight 2D snapshot of the full 3D environment (Sky + 3D Studio Floor + Animated 3D Scene Objects)
     * for use behind 2D drawing layers when "Mostrar 3D no Canvas" is enabled.
     */
    fun renderCanvas3DBackgroundBitmap(
        outW: Int,
        outH: Int,
        project: AnimationProject,
        tickFloat: Float,
        fps: Int = project.fps,
        reuseBitmap: Bitmap? = null
    ): Bitmap {
        val w = outW.coerceAtLeast(1)
        val h = outH.coerceAtLeast(1)
        val bmp = if (reuseBitmap != null && !reuseBitmap.isRecycled && reuseBitmap.isMutable &&
            reuseBitmap.width == w && reuseBitmap.height == h
        ) {
            reuseBitmap
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bmp)
        renderCanvas3DBackgroundToCanvas(
            canvas = canvas,
            outW = w,
            outH = h,
            settings = project.camera3D,
            background = project.canvasBackground,
            projectWidth = project.resolution.width,
            projectHeight = project.resolution.height,
            tickFloat = tickFloat,
            fps = fps
        )
        return bmp
    }

    private fun renderCanvas3DBackgroundToCanvas(
        canvas: Canvas,
        outW: Int,
        outH: Int,
        settings: Camera3DSettings,
        background: CanvasBackgroundSettings,
        projectWidth: Int,
        projectHeight: Int,
        tickFloat: Float,
        fps: Int
    ) {
        val skySettings = if (background.skyMode == SkyBackdropMode.GRADE_ESPACIAL) {
            background.copy(skyMode = SkyBackdropMode.ESTUDIO_ESCURO)
        } else {
            background
        }
        KodanimaGraphicsEngine.drawSkyBackdropToCanvas(canvas, outW.toFloat(), outH.toFloat(), skySettings)

        // Always evaluate the active camera parameters (pitchX, yawY, rotationZ, x, y, zoom, fovDegrees)
        // so the 3D background inside Canvas matches the exact Camera angle & framing at every tick.
        val kf = evaluateCameraAtTick(settings, tickFloat)
        val camYaw = -kf.yawY
        val camPitch = kf.pitchX
        val fitScale = min(outW.toFloat() / projectWidth.coerceAtLeast(1), outH.toFloat() / projectHeight.coerceAtLeast(1))
        val worldUnitScale = fitScale * kf.zoom
        val refTan = tan(Math.toRadians(25.0))
        val curTan = tan(Math.toRadians((kf.fovDegrees.coerceIn(18f, 120f) * 0.5f).toDouble())).coerceAtLeast(0.1)
        val fovFactor = (refTan / curTan).toFloat().coerceIn(0.35f, 2.8f)
        val focal3D = max(outW, outH) * 0.85f * fovFactor
        val floorWorldScale = min(projectWidth.coerceAtLeast(1), projectHeight.coerceAtLeast(1)).toFloat() * worldUnitScale

        if (settings.showFloorGrid) {
            drawStudio3DFloorGridToCanvas(
                canvas = canvas,
                outW = outW.toFloat(),
                outH = outH.toFloat(),
                camYawDeg = camYaw,
                camPitchDeg = camPitch,
                camRollDeg = -kf.rotationZ,
                camPanX = kf.x * worldUnitScale,
                camPanY = kf.y * worldUnitScale,
                focalPx = focal3D,
                floorWorldScale = floorWorldScale
            )
        }

        val animTimeSec = tickFloat / max(1, fps).toFloat()
        val has3DObjects = settings.scene3DObjects.any { it.isVisible && it.meshAssetPath.isNotBlank() }
        if (has3DObjects) {
            val pooledMeshBmp = KodanimaBitmapPool.acquire(outW, outH, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
            try {
                renderSceneObjectsSoftware3D(
                    outW = outW,
                    outH = outH,
                    sceneObjects = settings.scene3DObjects,
                    frameIndex = tickFloat.roundToInt(),
                    autoInterpolate = settings.autoInterpolate,
                    camYawDeg = camYaw,
                    camPitchDeg = camPitch,
                    camRollDeg = -kf.rotationZ,
                    camPanX = kf.x * worldUnitScale,
                    camPanY = kf.y * worldUnitScale,
                    focalPx = focal3D,
                    vpCx = outW * 0.5f,
                    vpCy = outH * 0.5f,
                    worldUnitScale = worldUnitScale,
                    tickFloat = tickFloat,
                    animTimeSec = animTimeSec,
                    reuseBitmap = pooledMeshBmp,
                    shadingMode = settings.shadingMode
                )?.let { (meshBmp, _) ->
                    val drawPaint = rasterScratchThreadLocal.get()!!.drawBitmapPaint
                    drawPaint.alpha = 255
                    drawPaint.xfermode = null
                    canvas.drawBitmap(meshBmp, 0f, 0f, drawPaint)
                }
            } finally {
                KodanimaBitmapPool.release(pooledMeshBmp)
            }
        }
    }

    /**
     * Renders a single animation frame using the active 2D/3D camera transform, per-layer animated
     * transforms, 3D textured scene objects, off-canvas sky/backdrop, and post-effects onto [targetBitmap].
     */
    fun renderFrameWithCameraAndEffects(
        frame: Frame,
        settings: Camera3DSettings,
        frameIndex: Int,
        projectWidth: Int,
        projectHeight: Int,
        targetBitmap: Bitmap,
        cropToCamera: Boolean = true,
        background: CanvasBackgroundSettings = CanvasBackgroundSettings(),
        renderTransparentAsSky: Boolean = false,
        tickFloat: Float = frameIndex.toFloat(),
        fps: Int = 12
    ) {
        val outW = targetBitmap.width.coerceAtLeast(1)
        val outH = targetBitmap.height.coerceAtLeast(1)
        val targetCanvas = Canvas(targetBitmap)
        val scratch = rasterScratchThreadLocal.get()!!
        val paint = scratch.drawBitmapPaint
        paint.alpha = 255
        paint.xfermode = null
        paint.colorFilter = null

        val has3DObjects = settings.scene3DObjects.any { it.isVisible && it.meshAssetPath.isNotBlank() }
        val animTimeSec = tickFloat / max(1, fps).toFloat()

        if (!settings.isEnabled || !cropToCamera) {
            if (background.show3DInCanvas) {
                renderCanvas3DBackgroundToCanvas(
                    canvas = targetCanvas,
                    outW = outW,
                    outH = outH,
                    settings = settings,
                    background = background,
                    projectWidth = projectWidth,
                    projectHeight = projectHeight,
                    tickFloat = tickFloat,
                    fps = fps
                )
                val projBmp = KodanimaBitmapPool.acquire(projectWidth, projectHeight, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                try {
                    val pc = Canvas(projBmp)
                    KodanimaGraphicsEngine.drawLayersStackToCanvas(
                        layers = KodanimaGraphicsEngine.composeOrderedFrameLayers(frame),
                        canvas = pc,
                        targetWidth = projectWidth,
                        targetHeight = projectHeight
                    )
                    targetCanvas.drawBitmap(projBmp, null, android.graphics.Rect(0, 0, outW, outH), paint)
                } finally {
                    KodanimaBitmapPool.release(projBmp)
                }
            } else if (outW == projectWidth && outH == projectHeight && !has3DObjects) {
                KodanimaGraphicsEngine.drawFrameToBitmap(
                    frame = frame,
                    targetBitmap = targetBitmap,
                    clearBitmap = true,
                    background = background,
                    drawSkyWhenTransparent = renderTransparentAsSky
                )
            } else {
                val projBmp = KodanimaBitmapPool.acquire(projectWidth, projectHeight, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                try {
                    KodanimaGraphicsEngine.drawFrameToBitmap(
                        frame = frame,
                        targetBitmap = projBmp,
                        clearBitmap = true,
                        background = background,
                        drawSkyWhenTransparent = renderTransparentAsSky
                    )
                    targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    targetCanvas.drawBitmap(projBmp, null, android.graphics.Rect(0, 0, outW, outH), paint)
                } finally {
                    KodanimaBitmapPool.release(projBmp)
                }
                if (has3DObjects) {
                    val kf = evaluateCameraAtTick(settings, tickFloat)
                    val fitScale = min(outW.toFloat() / projectWidth.coerceAtLeast(1), outH.toFloat() / projectHeight.coerceAtLeast(1))
                    val worldUnitScale = fitScale * kf.zoom
                    val refTan = tan(Math.toRadians(25.0))
                    val curTan = tan(Math.toRadians((kf.fovDegrees.coerceIn(18f, 120f) * 0.5f).toDouble())).coerceAtLeast(0.1)
                    val fovFactor = (refTan / curTan).toFloat().coerceIn(0.35f, 2.8f)
                    val focal3D = max(outW, outH) * 0.85f * fovFactor
                    val pooledMeshBmp = KodanimaBitmapPool.acquire(outW, outH, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                    try {
                        renderSceneObjectsSoftware3D(
                            outW = outW,
                            outH = outH,
                            sceneObjects = settings.scene3DObjects,
                            frameIndex = frameIndex,
                            autoInterpolate = settings.autoInterpolate,
                            camYawDeg = -kf.yawY,
                            camPitchDeg = kf.pitchX,
                            camRollDeg = -kf.rotationZ,
                            camPanX = kf.x * worldUnitScale,
                            camPanY = kf.y * worldUnitScale,
                            focalPx = focal3D,
                            vpCx = outW * 0.5f,
                            vpCy = outH * 0.5f,
                            worldUnitScale = worldUnitScale,
                            tickFloat = tickFloat,
                            animTimeSec = animTimeSec,
                            reuseBitmap = pooledMeshBmp,
                            shadingMode = settings.shadingMode
                        )?.let { (meshBmp, _) ->
                            paint.alpha = 255
                            paint.xfermode = null
                            targetCanvas.drawBitmap(meshBmp, 0f, 0f, paint)
                        }
                    } finally {
                        KodanimaBitmapPool.release(pooledMeshBmp)
                    }
                }
            }
            if (settings.postEffects.hasAnyEffect()) {
                applyPostEffectsToBitmap(targetBitmap, settings.postEffects)
            }
            return
        }

        val kf = evaluateCameraAtTick(settings, tickFloat)
        val allLayers = KodanimaGraphicsEngine.composeOrderedFrameLayers(frame)
        val hasAnimatedLayers = settings.layerTransformTracks.isNotEmpty()
        val has3DTilt = (settings.cameraMode == CameraMode.MODE_3D &&
            (abs(kf.pitchX) > 0.1f || abs(kf.yawY) > 0.1f || abs(kf.fovDegrees - 50f) > 1.5f)) ||
            hasAnimatedLayers
        val fitScale = min(outW.toFloat() / projectWidth.toFloat(), outH.toFloat() / projectHeight.toFloat())
        val cxOut = outW * 0.5f
        val cyOut = outH * 0.5f
        val cxProj = projectWidth * 0.5f
        val cyProj = projectHeight * 0.5f

        // Draw Full-Viewport Off-Canvas Environment Sky first (so zooming out renders everything outside the 2D paper)
        val isZoomedOutOrPanned = kf.zoom < 0.99f || abs(kf.x) > 1f || abs(kf.y) > 1f
        if (renderTransparentAsSky || background.show3DInCanvas || background.style != CanvasBackgroundStyle.TRANSPARENTE || isZoomedOutOrPanned || has3DObjects) {
            val skyCfg = if (background.show3DInCanvas && background.skyMode == SkyBackdropMode.GRADE_ESPACIAL) {
                background.copy(skyMode = SkyBackdropMode.ESTUDIO_ESCURO)
            } else background
            KodanimaGraphicsEngine.drawSkyBackdropToCanvas(targetCanvas, outW.toFloat(), outH.toFloat(), skyCfg)
        } else {
            targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        val midLayer = (allLayers.size - 1) * 0.5f
        val worldUnitScale = fitScale * kf.zoom
        val refTan = tan(Math.toRadians(25.0))
        val curTan = tan(Math.toRadians((kf.fovDegrees.coerceIn(18f, 120f) * 0.5f).toDouble())).coerceAtLeast(0.1)
        val fovFactor = (refTan / curTan).toFloat().coerceIn(0.35f, 2.8f)
        val focal3D = max(outW, outH) * 0.85f * fovFactor
        val floorWorldScale = min(projectWidth.coerceAtLeast(1), projectHeight.coerceAtLeast(1)).toFloat() * worldUnitScale

        val camYaw3D = -kf.yawY
        val camPitch3D = kf.pitchX

        if (background.show3DInCanvas && settings.showFloorGrid) {
            drawStudio3DFloorGridToCanvas(
                canvas = targetCanvas,
                outW = outW.toFloat(),
                outH = outH.toFloat(),
                camYawDeg = camYaw3D,
                camPitchDeg = camPitch3D,
                camRollDeg = -kf.rotationZ,
                camPanX = kf.x * worldUnitScale,
                camPanY = kf.y * worldUnitScale,
                focalPx = focal3D,
                floorWorldScale = floorWorldScale
            )
        }

        val pooledObjBmp = if (has3DObjects) {
            KodanimaBitmapPool.acquire(outW, outH, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
        } else null

        try {
            // Render background 3D Scene objects (Z >= midLayer front) behind layers
            if (has3DObjects && pooledObjBmp != null) {
                val backZThreshold = (midLayer * 0.25f) * kf.depthSeparation
                renderSceneObjectsSoftware3D(
                    outW = outW,
                    outH = outH,
                    sceneObjects = settings.scene3DObjects,
                    frameIndex = frameIndex,
                    autoInterpolate = settings.autoInterpolate,
                    camYawDeg = camYaw3D,
                    camPitchDeg = camPitch3D,
                    camRollDeg = -kf.rotationZ,
                    camPanX = kf.x * worldUnitScale,
                    camPanY = kf.y * worldUnitScale,
                    focalPx = focal3D,
                    vpCx = cxOut,
                    vpCy = cyOut,
                    worldUnitScale = worldUnitScale,
                    zNearLimit = backZThreshold,
                    zFarLimit = Float.MAX_VALUE,
                    tickFloat = tickFloat,
                    animTimeSec = animTimeSec,
                    reuseBitmap = pooledObjBmp,
                    shadingMode = settings.shadingMode
                )?.let { (meshBmp, _) ->
                    targetCanvas.drawBitmap(meshBmp, 0f, 0f, paint)
                }
            }

            if (!has3DTilt) {
                val camMat = android.graphics.Matrix().apply {
                    postTranslate(-(cxProj + kf.x), -(cyProj + kf.y))
                    if (abs(kf.rotationZ) > 0.05f) postRotate(-kf.rotationZ)
                    postScale(fitScale * kf.zoom, fitScale * kf.zoom)
                    postTranslate(cxOut, cyOut)
                }
                if (!background.show3DInCanvas && background.style != CanvasBackgroundStyle.TRANSPARENTE) {
                    targetCanvas.save()
                    targetCanvas.concat(camMat)
                    targetCanvas.clipRect(0f, 0f, projectWidth.toFloat(), projectHeight.toFloat())
                    KodanimaGraphicsEngine.drawProjectBackgroundToCanvas(
                        targetCanvas, projectWidth.toFloat(), projectHeight.toFloat(), background, drawSkyWhenTransparent = false
                    )
                    targetCanvas.restore()
                }
                KodanimaGraphicsEngine.drawLayersStackToCanvas(
                    layers = allLayers,
                    canvas = targetCanvas,
                    targetWidth = projectWidth,
                    targetHeight = projectHeight,
                    allowOutOfBounds = true,
                    viewportMatrix = camMat,
                    viewportWidth = outW,
                    viewportHeight = outH
                )
            } else {
                val layerBmp = KodanimaBitmapPool.acquire(projectWidth, projectHeight, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                val camera = scratch.camera
                val camMatrix = scratch.camMatrix

                try {
                    // Canvas Background Paper Plane as backmost 3D plane (unless transparent or show3DInCanvas)
                    if (!background.show3DInCanvas && background.style != CanvasBackgroundStyle.TRANSPARENTE) {
                        val bgCanvas = Canvas(layerBmp)
                        KodanimaGraphicsEngine.drawProjectBackgroundToCanvas(
                            bgCanvas, projectWidth.toFloat(), projectHeight.toFloat(), background, drawSkyWhenTransparent = false
                        )
                        val bgZ = (midLayer + 1f) * kf.depthSeparation
                        camera.save()
                        camera.setLocation(0f, 0f, (-8f * fovFactor).coerceIn(-30f, -2.5f))
                        camera.translate(0f, 0f, bgZ)
                        camera.rotateX(kf.pitchX)
                        camera.rotateY(-kf.yawY)
                        camera.getMatrix(camMatrix)
                        camera.restore()
                        camMatrix.preTranslate(-(cxProj + kf.x), -(cyProj + kf.y))
                        if (abs(kf.rotationZ) > 0.05f) {
                            camMatrix.postRotate(-kf.rotationZ)
                        }
                        camMatrix.postScale(fitScale * kf.zoom, fitScale * kf.zoom)
                        camMatrix.postTranslate(cxOut, cyOut)
                        paint.alpha = 255
                        paint.xfermode = null
                        targetCanvas.drawBitmap(layerBmp, camMatrix, paint)
                    }

                    for (idx in allLayers.indices) {
                        val layer = allLayers[idx]
                        val layerTf = evaluateLayerTransformAtTick(
                            settings.layerTransformTracks,
                            layer.id,
                            tickFloat,
                            settings.autoInterpolate
                        )
                        if (!layer.isVisible || layer.opacity <= 0f || layerTf.opacity <= 0.01f) continue
                        val lc = Canvas(layerBmp)
                        lc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        KodanimaGraphicsEngine.drawLayerToCanvas(layer, lc, projectWidth, projectHeight)

                        val zDepth = (midLayer - idx) * kf.depthSeparation + layerTf.z
                        camera.save()
                        camera.setLocation(0f, 0f, (-8f * fovFactor).coerceIn(-30f, -2.5f))
                        camera.translate(0f, 0f, zDepth)
                        camera.rotateX(kf.pitchX + layerTf.rotX)
                        camera.rotateY(-kf.yawY + layerTf.rotY)
                        camera.getMatrix(camMatrix)
                        camera.restore()

                        camMatrix.preTranslate(-(cxProj + kf.x - layerTf.x), -(cyProj + kf.y - layerTf.y))
                        if (abs(layerTf.scaleX - 1f) > 0.005f || abs(layerTf.scaleY - 1f) > 0.005f) {
                            camMatrix.preScale(layerTf.scaleX, layerTf.scaleY, cxProj, cyProj)
                        }
                        if (abs(layerTf.rotZ) > 0.05f) {
                            camMatrix.preRotate(layerTf.rotZ, cxProj, cyProj)
                        }
                        if (abs(kf.rotationZ) > 0.05f) {
                            camMatrix.postRotate(-kf.rotationZ)
                        }
                        camMatrix.postScale(fitScale * kf.zoom, fitScale * kf.zoom)
                        camMatrix.postTranslate(cxOut, cyOut)

                        paint.alpha = (layerTf.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
                        KodanimaGraphicsEngine.applyLayerBlendMode(paint, layer.blendEffect)
                        targetCanvas.drawBitmap(layerBmp, camMatrix, paint)
                        paint.xfermode = null
                    }
                } finally {
                    paint.alpha = 255
                    paint.xfermode = null
                    KodanimaBitmapPool.release(layerBmp)
                }
            }

            // Render foreground 3D Scene objects in front of 2D layers
            if (has3DObjects && pooledObjBmp != null) {
                val frontZThreshold = (midLayer * 0.25f) * kf.depthSeparation
                renderSceneObjectsSoftware3D(
                    outW = outW,
                    outH = outH,
                    sceneObjects = settings.scene3DObjects,
                    frameIndex = frameIndex,
                    autoInterpolate = settings.autoInterpolate,
                    camYawDeg = camYaw3D,
                    camPitchDeg = camPitch3D,
                    camRollDeg = -kf.rotationZ,
                    camPanX = kf.x * worldUnitScale,
                    camPanY = kf.y * worldUnitScale,
                    focalPx = focal3D,
                    vpCx = cxOut,
                    vpCy = cyOut,
                    worldUnitScale = worldUnitScale,
                    zNearLimit = -Float.MAX_VALUE,
                    zFarLimit = frontZThreshold - 0.001f,
                    tickFloat = tickFloat,
                    animTimeSec = animTimeSec,
                    reuseBitmap = pooledObjBmp,
                    shadingMode = settings.shadingMode
                )?.let { (meshBmp, _) ->
                    paint.alpha = 255
                    paint.xfermode = null
                    targetCanvas.drawBitmap(meshBmp, 0f, 0f, paint)
                }
            }
        } finally {
            if (pooledObjBmp != null) KodanimaBitmapPool.release(pooledObjBmp)
        }

        if (settings.postEffects.hasAnyEffect()) {
            applyPostEffectsToBitmap(targetBitmap, settings.postEffects)
        }
    }

    /**
     * Applies After Effects-style post-processing directly onto [bitmap] in-place using pooled buffers.
     */
    fun applyPostEffectsToBitmap(bitmap: Bitmap, effects: CameraPostEffectSettings) {
        if (!effects.hasAnyEffect() || bitmap.isRecycled) return
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return

        val totalPx = w * h
        val scratch = rasterScratchThreadLocal.get()!!
        val fxPaint = scratch.fxPaint
        val canvas = Canvas(bitmap)

        // 1. Contrast & Sepia color grading pass
        if (abs(effects.contrastBoost) > 0.01f || effects.sepia > 0.01f) {
            val cm = ColorMatrix()
            if (abs(effects.contrastBoost) > 0.01f) {
                val c = (1f + effects.contrastBoost).coerceIn(0.4f, 1.8f)
                val t = (-0.5f * c + 0.5f) * 255f
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            if (effects.sepia > 0.01f) {
                val s = effects.sepia.coerceIn(0f, 1f)
                val inv = 1f - s
                val sepMatrix = ColorMatrix(
                    floatArrayOf(
                        inv + s * 0.393f, s * 0.769f, s * 0.189f, 0f, 0f,
                        s * 0.349f, inv + s * 0.686f, s * 0.168f, 0f, 0f,
                        s * 0.272f, s * 0.534f, inv + s * 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sepMatrix)
            }
            val copy = KodanimaBitmapPool.acquire(w, h, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
            try {
                Canvas(copy).drawBitmap(bitmap, 0f, 0f, null)
                fxPaint.alpha = 255
                fxPaint.xfermode = null
                fxPaint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(copy, 0f, 0f, fxPaint)
            } finally {
                fxPaint.colorFilter = null
                KodanimaBitmapPool.release(copy)
            }
        }

        // 2. Chromatic Aberration (RGB horizontal prism shift)
        if (effects.chromaticAberration > 0.01f) {
            val shiftPx = max(1, (effects.chromaticAberration * w * 0.012f).roundToInt()).coerceAtMost(24)
            scratch.ensureFrameCapacity(totalPx)
            val pixels = scratch.pixels
            val out = scratch.ensureFxWorkCapacity(totalPx)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            for (y in 0 until h) {
                val rowOff = y * w
                for (x in 0 until w) {
                    val leftX = (x - shiftPx).coerceIn(0, w - 1)
                    val rightX = (x + shiftPx).coerceIn(0, w - 1)
                    val r = (pixels[rowOff + leftX] ushr 16) and 0xFF
                    val g = (pixels[rowOff + x] ushr 8) and 0xFF
                    val b = pixels[rowOff + rightX] and 0xFF
                    val a = (pixels[rowOff + x] ushr 24) and 0xFF
                    out[rowOff + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(out, 0, w, 0, 0, w, h)
        }

        // 3. Bloom / Glow (soft additive highlight bleed)
        if (effects.bloom > 0.01f) {
            val downW = (w / 10).coerceIn(32, 180)
            val downH = (h / 10).coerceIn(32, 180)
            val glowSmall = KodanimaBitmapPool.acquire(downW, downH, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
            try {
                fxPaint.alpha = 255
                fxPaint.xfermode = null
                fxPaint.colorFilter = null
                Canvas(glowSmall).drawBitmap(bitmap, null, android.graphics.Rect(0, 0, downW, downH), fxPaint)
                fxPaint.alpha = (effects.bloom.coerceIn(0f, 1f) * 145f).roundToInt().coerceIn(0, 200)
                fxPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                canvas.drawBitmap(glowSmall, null, android.graphics.Rect(0, 0, w, h), fxPaint)
            } finally {
                fxPaint.alpha = 255
                fxPaint.xfermode = null
                KodanimaBitmapPool.release(glowSmall)
            }
        }

        // 4. Radial Motion Blur
        if (effects.radialBlur > 0.01f) {
            val copy = KodanimaBitmapPool.acquire(w, h, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
            try {
                Canvas(copy).drawBitmap(bitmap, 0f, 0f, null)
                fxPaint.xfermode = null
                fxPaint.colorFilter = null
                fxPaint.alpha = (effects.radialBlur.coerceIn(0f, 1f) * 65f).roundToInt().coerceIn(10, 110)
                val cx = w * 0.5f
                val cy = h * 0.5f
                val stepScale = 1f + effects.radialBlur * 0.035f
                for (pass in 1..2) {
                    val s = stepScale.pow(pass)
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.scale(s, s)
                    canvas.translate(-cx, -cy)
                    canvas.drawBitmap(copy, 0f, 0f, fxPaint)
                    canvas.restore()
                }
            } finally {
                fxPaint.alpha = 255
                KodanimaBitmapPool.release(copy)
            }
        }

        // 5. Film Grain
        if (effects.filmGrain > 0.01f) {
            val strength = (effects.filmGrain.coerceIn(0f, 1f) * 44f).roundToInt()
            scratch.ensureFrameCapacity(totalPx)
            val pixels = scratch.pixels
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var seed = 0x9E3779B9.toInt()
            for (i in 0 until totalPx) {
                seed = seed xor (seed shl 13)
                seed = seed xor (seed ushr 17)
                seed = seed xor (seed shl 5)
                val noise = ((seed and 0xFF) - 128) * strength / 128
                val px = pixels[i]
                val a = (px ushr 24) and 0xFF
                val r = (((px ushr 16) and 0xFF) + noise).coerceIn(0, 255)
                val g = (((px ushr 8) and 0xFF) + noise).coerceIn(0, 255)
                val b = ((px and 0xFF) + noise).coerceIn(0, 255)
                pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        // 6. Vignette
        if (effects.vignette > 0.01f) {
            val vAlpha = (effects.vignette.coerceIn(0f, 1f) * 215f).roundToInt().coerceIn(0, 235)
            val rad = max(w, h) * 0.72f
            val vPaint = scratch.vignettePaint
            vPaint.shader = RadialGradient(
                w * 0.5f,
                h * 0.5f,
                rad,
                intArrayOf(0x00000000, 0x00000000, (vAlpha shl 24)),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vPaint)
            vPaint.shader = null
        }
    }
}
