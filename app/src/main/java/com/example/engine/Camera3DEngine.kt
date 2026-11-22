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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * 3D Camera & Post-Processing Engine for Kodanima 2D.
 *
 * Provides:
 * - Keyframe interpolation with studio easing curves (Linear, Bézier Suave, Acelerar, Desacelerar, Degrau, Elástico)
 * - Built-in animated camera templates (Zoom In/Out, Pan Cinema, Tremor, Órbita 3D, Dolly Twist)
 * - Multi-layer 3D depth parallax + configurable FOV rendering using android.graphics.Camera & 3D perspective Matrix
 * - Live & exported post-effects (Vignette, Bloom, Chromatic Aberration, Film Grain, Sepia, Radial Blur, Contrast)
 */
object Camera3DEngine {

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

        // Bisection solver (16 iterations -> accuracy ~1.5e-5, unconditionally monotone)
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

    fun evaluateCameraAtFrame(settings: Camera3DSettings, frameIndex: Int): Camera3DKeyframe {
        val kfs = settings.keyframes.sortedBy { it.frameIndex }
        var result = if (kfs.isEmpty()) {
            Camera3DKeyframe(
                frameIndex = frameIndex,
                depthSeparation = settings.layerDepthSpacing
            )
        } else {
            val first = kfs.first()
            if (frameIndex <= first.frameIndex) {
                first.copy(frameIndex = frameIndex)
            } else {
                val last = kfs.last()
                if (frameIndex >= last.frameIndex) {
                    last.copy(frameIndex = frameIndex)
                } else {
                    var prev = first
                    var next = last
                    for (i in 0 until kfs.lastIndex) {
                        if (kfs[i].frameIndex <= frameIndex && frameIndex <= kfs[i + 1].frameIndex) {
                            prev = kfs[i]
                            next = kfs[i + 1]
                            break
                        }
                    }
                    if (!settings.autoInterpolate || prev.frameIndex == next.frameIndex) {
                        prev.copy(frameIndex = frameIndex)
                    } else {
                        val span = (next.frameIndex - prev.frameIndex).coerceAtLeast(1).toFloat()
                        val rawT = ((frameIndex - prev.frameIndex) / span).coerceIn(0f, 1f)
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
                            bezierP2Y = prev.bezierP2Y
                        )
                    }
                }
            }
        }

        // Layer localized CameraEffectSpans (e.g. between 2 selected keyframes or at playhead range)
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
                val sF = min(span.startFrame, span.endFrame)
                val eF = max(span.startFrame, span.endFrame).coerceAtLeast(sF + 1)
                if (frameIndex in sF..eF) {
                    val u = ((frameIndex - sF).toFloat() / (eF - sF).toFloat()).coerceIn(0f, 1f)
                    val smoothU = u * u * (3f - 2f * u)
                    val inten = span.intensity.coerceIn(0.2f, 2.5f)
                    when (span.effect) {
                        CameraBuiltInEffect.ZOOM_IN -> {
                            mulZoom *= (1f + 0.45f * smoothU * inten)
                        }
                        CameraBuiltInEffect.ZOOM_OUT -> {
                            mulZoom *= (1f + 0.45f * (1f - smoothU) * inten)
                        }
                        CameraBuiltInEffect.PAN_CINEMA -> {
                            addX += (-0.12f + 0.24f * smoothU) * wBase * inten
                        }
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
     * Renders a single animation frame using the active 3D camera transform, per-layer Z parallax,
     * camera capture resolution scaling, and After Effects post-effects onto [targetBitmap].
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
        renderTransparentAsSky: Boolean = false
    ) {
        val outW = targetBitmap.width.coerceAtLeast(1)
        val outH = targetBitmap.height.coerceAtLeast(1)
        val targetCanvas = Canvas(targetBitmap)

        if (!settings.isEnabled || !cropToCamera) {
            if (outW == projectWidth && outH == projectHeight) {
                KodanimaGraphicsEngine.drawFrameToBitmap(
                    frame = frame,
                    targetBitmap = targetBitmap,
                    clearBitmap = true,
                    background = background,
                    drawSkyWhenTransparent = renderTransparentAsSky
                )
            } else {
                val projBmp = KodanimaBitmapPool.acquire(projectWidth, projectHeight, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                KodanimaGraphicsEngine.drawFrameToBitmap(
                    frame = frame,
                    targetBitmap = projBmp,
                    clearBitmap = true,
                    background = background,
                    drawSkyWhenTransparent = renderTransparentAsSky
                )
                targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                targetCanvas.drawBitmap(projBmp, null, android.graphics.Rect(0, 0, outW, outH), Paint(Paint.FILTER_BITMAP_FLAG))
                KodanimaBitmapPool.release(projBmp)
            }
            if (settings.postEffects.hasAnyEffect()) {
                applyPostEffectsToBitmap(targetBitmap, settings.postEffects)
            }
            return
        }

        val kf = evaluateCameraAtFrame(settings, frameIndex)
        val allLayers = frame.layers + frame.localLayers
        val has3DTilt = settings.cameraMode == CameraMode.MODE_3D &&
            (abs(kf.pitchX) > 0.1f || abs(kf.yawY) > 0.1f || abs(kf.fovDegrees - 50f) > 1.5f)
        val fitScale = min(outW.toFloat() / projectWidth.toFloat(), outH.toFloat() / projectHeight.toFloat())
        val cxOut = outW * 0.5f
        val cyOut = outH * 0.5f
        val cxProj = projectWidth * 0.5f
        val cyProj = projectHeight * 0.5f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

        // Draw 3D Sky or Base Background first
        if (renderTransparentAsSky || background.style == CanvasBackgroundStyle.TRANSPARENTE) {
            if (renderTransparentAsSky) {
                KodanimaGraphicsEngine.drawSkyBackdropToCanvas(targetCanvas, outW.toFloat(), outH.toFloat(), background)
            } else {
                targetCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        } else {
            KodanimaGraphicsEngine.drawSkyBackdropToCanvas(targetCanvas, outW.toFloat(), outH.toFloat(), background)
        }

        if (!has3DTilt) {
            val fovBoost = (tan(Math.toRadians(25.0)) / tan(Math.toRadians((kf.fovDegrees.coerceIn(18f, 120f) * 0.5f).toDouble()))).toFloat()
            val projBmp = KodanimaBitmapPool.acquire(projectWidth, projectHeight, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
            try {
                val pc = Canvas(projBmp)
                if (background.style != CanvasBackgroundStyle.TRANSPARENTE) {
                    KodanimaGraphicsEngine.drawProjectBackgroundToCanvas(
                        pc, projectWidth.toFloat(), projectHeight.toFloat(), background, drawSkyWhenTransparent = false
                    )
                }
                KodanimaGraphicsEngine.drawLayersStackToCanvas(
                    layers = allLayers,
                    canvas = pc,
                    targetWidth = projectWidth,
                    targetHeight = projectHeight
                )
                targetCanvas.save()
                targetCanvas.translate(cxOut, cyOut)
                targetCanvas.scale(fitScale * kf.zoom * fovBoost.coerceIn(0.45f, 2.5f), fitScale * kf.zoom * fovBoost.coerceIn(0.45f, 2.5f))
                if (abs(kf.rotationZ) > 0.05f) {
                    targetCanvas.rotate(-kf.rotationZ)
                }
                targetCanvas.translate(-(cxProj + kf.x), -(cyProj + kf.y))
                targetCanvas.drawBitmap(projBmp, 0f, 0f, paint)
                targetCanvas.restore()
            } finally {
                KodanimaBitmapPool.release(projBmp)
            }
        } else {
            val layerBmp = KodanimaBitmapPool.acquire(projectWidth, projectHeight, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
            val camera = Camera()
            val camMatrix = Matrix()
            val midLayer = (allLayers.size - 1) * 0.5f
            val refTan = tan(Math.toRadians(25.0))
            val curTan = tan(Math.toRadians((kf.fovDegrees.coerceIn(18f, 120f) * 0.5f).toDouble())).coerceAtLeast(0.1)
            val fovFactor = (refTan / curTan).toFloat().coerceIn(0.35f, 2.8f)

            try {
                // Draw Canvas Background Paper Plane as the backmost 3D plane (unless transparent)
                if (background.style != CanvasBackgroundStyle.TRANSPARENTE) {
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
                    if (!layer.isVisible || layer.opacity <= 0f) continue
                    val lc = Canvas(layerBmp)
                    lc.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    KodanimaGraphicsEngine.drawLayerToCanvas(layer, lc, projectWidth, projectHeight)

                    val zDepth = (midLayer - idx) * kf.depthSeparation
                    camera.save()
                    camera.setLocation(0f, 0f, (-8f * fovFactor).coerceIn(-30f, -2.5f))
                    camera.translate(0f, 0f, zDepth)
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
                    KodanimaGraphicsEngine.applyLayerBlendMode(paint, layer.blendEffect)
                    targetCanvas.drawBitmap(layerBmp, camMatrix, paint)
                    paint.xfermode = null
                }
            } finally {
                KodanimaBitmapPool.release(layerBmp)
            }
        }

        if (settings.postEffects.hasAnyEffect()) {
            applyPostEffectsToBitmap(targetBitmap, settings.postEffects)
        }
    }

    /**
     * Applies After Effects-style post-processing directly onto [bitmap] in-place.
     */
    fun applyPostEffectsToBitmap(bitmap: Bitmap, effects: CameraPostEffectSettings) {
        if (!effects.hasAnyEffect() || bitmap.isRecycled) return
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return

        val canvas = Canvas(bitmap)

        // 1. Contrast & Sepia color grading pass
        if (kotlin.math.abs(effects.contrastBoost) > 0.01f || effects.sepia > 0.01f) {
            val cm = ColorMatrix()
            if (kotlin.math.abs(effects.contrastBoost) > 0.01f) {
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
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val colorPaint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(copy, 0f, 0f, colorPaint)
            copy.recycle()
        }

        // 2. Chromatic Aberration (RGB horizontal prism shift)
        if (effects.chromaticAberration > 0.01f) {
            val shiftPx = max(1, (effects.chromaticAberration * w * 0.012f).roundToInt()).coerceAtMost(24)
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = pixels.copyOf()
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
            val glowSmall = Bitmap.createScaledBitmap(bitmap, downW, downH, true)
            val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (effects.bloom.coerceIn(0f, 1f) * 145f).roundToInt().coerceIn(0, 200)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            canvas.drawBitmap(glowSmall, null, android.graphics.Rect(0, 0, w, h), glowPaint)
            glowSmall.recycle()
        }

        // 4. Radial Motion Blur
        if (effects.radialBlur > 0.01f) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val blurPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                alpha = (effects.radialBlur.coerceIn(0f, 1f) * 65f).roundToInt().coerceIn(10, 110)
            }
            val cx = w * 0.5f
            val cy = h * 0.5f
            val stepScale = 1f + effects.radialBlur * 0.035f
            for (pass in 1..2) {
                val s = stepScale.pow(pass)
                canvas.save()
                canvas.translate(cx, cy)
                canvas.scale(s, s)
                canvas.translate(-cx, -cy)
                canvas.drawBitmap(copy, 0f, 0f, blurPaint)
                canvas.restore()
            }
            copy.recycle()
        }

        // 5. Film Grain
        if (effects.filmGrain > 0.01f) {
            val strength = (effects.filmGrain.coerceIn(0f, 1f) * 44f).roundToInt()
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var seed = 0x9E3779B9.toInt()
            for (i in pixels.indices) {
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
            val vPaint = Paint().apply {
                shader = RadialGradient(
                    w * 0.5f,
                    h * 0.5f,
                    rad,
                    intArrayOf(0x00000000, 0x00000000, (vAlpha shl 24)),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vPaint)
        }
    }
}
