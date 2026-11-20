package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.util.Base64
import android.util.LruCache
import com.example.model.BrushDynamicParams
import com.example.model.BrushPreset
import com.example.model.DrawingStroke
import com.example.model.Frame
import com.example.model.KritaBrushEngineKind
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.model.StrokePoint
import com.example.model.ToolType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kodanima 2D High-Performance Graphics Engine for Android.
 *
 * Optimizations across ARMv7-a, ARM64-v8a, and TBDR Mobile GPUs (Mali-G57 Valhall, Adreno, PowerVR):
 * - Zero-allocation ThreadLocal Paint/Path/FloatArray scratch buffers (eliminates GC pauses).
 * - Batched JNI calls via Canvas.drawPoints / Canvas.drawLines processed natively by Skia's
 *   ARM64 ASIMD / ARMv7 NEON machine code and submitted efficiently to GPU command queues.
 * - Mali-G57 / PowerVR TBDR rule: eliminates unnecessary full-screen saveLayer() when no eraser is present,
 *   and tightens saveLayer(bounds) to stroke AABB + viewport bounds when erasing, keeping tiles in SRAM/AFBC.
 * - Integer bitwise hash noise + 1024-entry Sin/Cos LUT replacing slow transcendental trig in inner loops.
 * - Primitive IntArray ring/growable queue for Flood Fill & Magic Wand (zero Integer autoboxing).
 */
object KodanimaGraphicsEngine {

    val engineBackendName: String
        get() = HardwareCapabilitiesEngine.currentProfile.let { p ->
            val api = if (p.supportsVulkan) "${p.vulkanVersionString} / OpenGL ES" else "OpenGL ES 3.x"
            "Skia Hardware ($api • ${p.gpuRenderer})"
        }

    // 1024-entry Sin/Cos lookup table for fast register-friendly angle math
    private const val LUT_SIZE = 1024
    private const val LUT_MASK = LUT_SIZE - 1
    private val SIN_LUT = FloatArray(LUT_SIZE) { i ->
        sin((i.toDouble() / LUT_SIZE) * Math.PI * 2.0).toFloat()
    }
    private val COS_LUT = FloatArray(LUT_SIZE) { i ->
        cos((i.toDouble() / LUT_SIZE) * Math.PI * 2.0).toFloat()
    }
    private const val COS_45 = 0.70710677f
    private const val SIN_45 = 0.70710677f
    private val CLEAR_XFERMODE = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val DST_IN_XFERMODE = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

    private val tipBitmapCache = object : LruCache<String, Bitmap>(64) {}
    private val dynamicParamsCache = object : LruCache<String, BrushDynamicParams>(96) {}
    private val tintColorFilterCache = object : LruCache<Int, PorterDuffColorFilter>(64) {}
    private val layerImageBitmapCache = object : LruCache<String, Bitmap>(16) {}

    private fun resolveTipStampBitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        tipBitmapCache.get(base64)?.takeIf { !it.isRecycled }?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            tipBitmapCache.put(base64, decoded)
            decoded
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveTintFilter(rgb: Int): PorterDuffColorFilter {
        val colorKey = (0xFF shl 24) or (rgb and 0x00FFFFFF)
        tintColorFilterCache.get(colorKey)?.let { return it }
        return PorterDuffColorFilter(colorKey, PorterDuff.Mode.SRC_IN).also {
            tintColorFilterCache.put(colorKey, it)
        }
    }

    fun resolveLayerImageBitmap(uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        layerImageBitmapCache.get(uriString)?.takeIf { !it.isRecycled }?.let { return it }
        val filePath = when {
            uriString.startsWith("file://") -> uriString.removePrefix("file://")
            uriString.startsWith("/") -> uriString
            else -> return null
        }
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
            var inSample = 1
            val maxDim = 2048
            while ((boundsOpts.outWidth / inSample) > maxDim || (boundsOpts.outHeight / inSample) > maxDim) {
                inSample = inSample shl 1
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null
            layerImageBitmapCache.put(uriString, decoded)
            decoded
        } catch (_: Exception) {
            null
        }
    }

    fun applyLayerBlendMode(paint: Paint, effect: LayerBlendEffect) {
        if (Build.VERSION.SDK_INT >= 29) {
            paint.xfermode = null
            paint.blendMode = when (effect) {
                LayerBlendEffect.NORMAL -> null
                LayerBlendEffect.MULTIPLY -> BlendMode.MULTIPLY
                LayerBlendEffect.SCREEN -> BlendMode.SCREEN
                LayerBlendEffect.OVERLAY -> BlendMode.OVERLAY
                LayerBlendEffect.DARKEN -> BlendMode.DARKEN
                LayerBlendEffect.LIGHTEN -> BlendMode.LIGHTEN
                LayerBlendEffect.COLOR_DODGE -> BlendMode.COLOR_DODGE
                LayerBlendEffect.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
                LayerBlendEffect.ADD -> BlendMode.PLUS
                LayerBlendEffect.DIFFERENCE -> BlendMode.DIFFERENCE
            }
        } else {
            @Suppress("DEPRECATION")
            paint.xfermode = when (effect) {
                LayerBlendEffect.NORMAL -> null
                LayerBlendEffect.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                LayerBlendEffect.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                LayerBlendEffect.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                LayerBlendEffect.DARKEN -> PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                LayerBlendEffect.LIGHTEN -> PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                LayerBlendEffect.ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                LayerBlendEffect.COLOR_DODGE -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                LayerBlendEffect.SOFT_LIGHT -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                LayerBlendEffect.DIFFERENCE -> null
            }
        }
    }

    private const val PAINT_QUALITY_FLAGS =
        Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG

    private class StrokeScratch {
        val strokePaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
        }
        val layerAlphaPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val haloPaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val corePaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val glossPaint = Paint(PAINT_QUALITY_FLAGS).apply {
            color = 0xD0FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val particlePaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val eraserPaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = CLEAR_XFERMODE
        }
        val eraserFillPaint = Paint(PAINT_QUALITY_FLAGS).apply {
            style = Paint.Style.FILL
            xfermode = CLEAR_XFERMODE
        }
        val bristlePaints = Array(9) {
            Paint(PAINT_QUALITY_FLAGS).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
        val path = Path()
        val clipPath = Path()
        val rectF = RectF()
        var ptsBuffer = FloatArray(4096)
        var smoothXYZ = FloatArray(4096 * 3)

        fun ensureCapacity(requiredFloats: Int): FloatArray {
            if (ptsBuffer.size < requiredFloats) {
                var newCap = ptsBuffer.size
                while (newCap < requiredFloats) newCap = newCap shl 1
                ptsBuffer = FloatArray(newCap)
            }
            return ptsBuffer
        }

        fun ensureSmoothCapacity(requiredPoints: Int): FloatArray {
            val neededFloats = requiredPoints * 3
            if (smoothXYZ.size < neededFloats) {
                var newCap = smoothXYZ.size
                while (newCap < neededFloats) newCap = newCap shl 1
                smoothXYZ = FloatArray(newCap)
            }
            return smoothXYZ
        }
    }

    /**
     * Subdivides raw pointer points using Quadratic Midpoint Bezier interpolation into a reusable
     * ThreadLocal scratch buffer (zero GC allocations) to eliminate polygonal kinks and jagged curves.
     * Each point is stored as 3 floats: (x, y, pressure). Returns total smoothed point count.
     */
    private fun smoothPointsQuadratic(
        points: List<StrokePoint>,
        scratch: StrokeScratch,
        maxSubdivisionsPerSeg: Int = 6
    ): Int {
        val n = points.size
        if (n <= 1) {
            if (n == 1) {
                val p = points[0]
                val buf = scratch.ensureSmoothCapacity(1)
                buf[0] = p.x; buf[1] = p.y; buf[2] = p.pressure
            }
            return n
        }
        if (n == 2) {
            val a = points[0]
            val b = points[1]
            val dist = hypot2(b.x - a.x, b.y - a.y)
            val steps = (dist / 4f).toInt().coerceIn(2, maxSubdivisionsPerSeg)
            val buf = scratch.ensureSmoothCapacity(steps + 1)
            val inv = 1f / steps
            for (s in 0..steps) {
                val t = s * inv
                val idx = s * 3
                buf[idx] = a.x + (b.x - a.x) * t
                buf[idx + 1] = a.y + (b.y - a.y) * t
                buf[idx + 2] = a.pressure + (b.pressure - a.pressure) * t
            }
            return steps + 1
        }

        // Upper bound estimation: 2 initial + (n - 2) * maxSubdivisionsPerSeg + 2 trailing
        val worstCase = 4 + (n - 2) * maxSubdivisionsPerSeg
        var buf = scratch.ensureSmoothCapacity(worstCase + 4)
        var outCount = 0

        fun emit(x: Float, y: Float, p: Float) {
            val base = outCount * 3
            if (base + 3 > buf.size) {
                buf = scratch.ensureSmoothCapacity(outCount + 64)
            }
            buf[base] = x
            buf[base + 1] = y
            buf[base + 2] = p
            outCount++
        }

        val pFirst = points[0]
        val pSecond = points[1]
        emit(pFirst.x, pFirst.y, pFirst.pressure)

        // Lead-in to first midpoint
        val firstMidX = (pFirst.x + pSecond.x) * 0.5f
        val firstMidY = (pFirst.y + pSecond.y) * 0.5f
        val firstMidP = (pFirst.pressure + pSecond.pressure) * 0.5f
        emit((pFirst.x + firstMidX) * 0.5f, (pFirst.y + firstMidY) * 0.5f, (pFirst.pressure + firstMidP) * 0.5f)
        emit(firstMidX, firstMidY, firstMidP)

        for (i in 1 until n - 1) {
            val prev = points[i - 1]
            val ctrl = points[i]
            val next = points[i + 1]

            val ax = (prev.x + ctrl.x) * 0.5f
            val ay = (prev.y + ctrl.y) * 0.5f
            val ap = (prev.pressure + ctrl.pressure) * 0.5f

            val bx = (ctrl.x + next.x) * 0.5f
            val by = (ctrl.y + next.y) * 0.5f
            val bp = (ctrl.pressure + next.pressure) * 0.5f

            val curveLen = hypot2(ctrl.x - ax, ctrl.y - ay) + hypot2(bx - ctrl.x, by - ctrl.y)
            val steps = (curveLen / 2.2f).toInt().coerceIn(2, maxSubdivisionsPerSeg)
            val inv = 1f / steps

            for (s in 1..steps) {
                val t = s * inv
                val omT = 1f - t
                val w0 = omT * omT
                val w1 = 2f * omT * t
                val w2 = t * t
                val sx = w0 * ax + w1 * ctrl.x + w2 * bx
                val sy = w0 * ay + w1 * ctrl.y + w2 * by
                val sp = w0 * ap + w1 * ctrl.pressure + w2 * bp
                emit(sx, sy, sp)
            }
        }

        val pLastPrev = points[n - 2]
        val pLast = points[n - 1]
        val lastMidX = (pLastPrev.x + pLast.x) * 0.5f
        val lastMidY = (pLastPrev.y + pLast.y) * 0.5f
        val lastMidP = (pLastPrev.pressure + pLast.pressure) * 0.5f
        emit((lastMidX + pLast.x) * 0.5f, (lastMidY + pLast.y) * 0.5f, (lastMidP + pLast.pressure) * 0.5f)
        emit(pLast.x, pLast.y, pLast.pressure)

        return outCount
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun hypot2(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

    /**
     * Public helper generating a smoothed quadratic Bezier polyline from raw pointer points.
     */
    fun computeSmoothedPolyline(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size <= 1) return points
        val scratch = scratchThreadLocal.get()!!
        val count = smoothPointsQuadratic(points, scratch)
        val buf = scratch.smoothXYZ
        val result = ArrayList<StrokePoint>(count)
        for (i in 0 until count) {
            val base = i * 3
            result.add(StrokePoint(buf[base], buf[base + 1], buf[base + 2]))
        }
        return result
    }

    private val scratchThreadLocal = object : ThreadLocal<StrokeScratch>() {
        override fun initialValue(): StrokeScratch = StrokeScratch()
    }

    /**
     * Fast integer bitwise hash returning [0f, 1f) without Math.sin overhead.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun fastHashNoise(x: Float, y: Float, seed: Int): Float {
        var h = (x.toInt() * 73856093) xor (y.toInt() * 19349663) xor (seed * 83492791)
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0x7FFFFF) * (1.0f / 8388608.0f)
    }

    /**
     * Primitive IntArray FIFO queue to avoid ArrayDeque<Int> Integer autoboxing churn.
     */
    private class PrimitiveIntQueue(initialCapacity: Int = 4096) {
        private var data = IntArray(initialCapacity)
        private var head = 0
        private var tail = 0
        private var mask = initialCapacity - 1

        val isEmpty: Boolean get() = head == tail

        fun add(value: Int) {
            data[tail] = value
            tail = (tail + 1) and mask
            if (tail == head) {
                grow()
            }
        }

        fun removeFirst(): Int {
            val v = data[head]
            head = (head + 1) and mask
            return v
        }

        private fun grow() {
            val oldCap = data.size
            val newCap = oldCap shl 1
            val next = IntArray(newCap)
            val rightLen = oldCap - head
            System.arraycopy(data, head, next, 0, rightLen)
            System.arraycopy(data, 0, next, rightLen, head)
            data = next
            head = 0
            tail = oldCap
            mask = newCap - 1
        }
    }

    /**
     * Renders a complete Frame into an existing or pooled Bitmap at target resolution.
     */
    fun renderFrameToBitmap(
        frame: Frame,
        width: Int,
        height: Int,
        backgroundColor: Int = android.graphics.Color.WHITE
    ): Bitmap {
        val bitmap = KodanimaBitmapPool.acquire(
            max(1, width),
            max(1, height),
            Bitmap.Config.ARGB_8888,
            clearColor = backgroundColor
        )
        drawFrameToBitmap(frame, bitmap, backgroundColor, clearBitmap = false)
        return bitmap
    }

    /**
     * Renders a complete Frame directly into a caller-supplied mutable Bitmap (zero allocation).
     */
    fun drawFrameToBitmap(
        frame: Frame,
        targetBitmap: Bitmap,
        backgroundColor: Int = android.graphics.Color.WHITE,
        clearBitmap: Boolean = true
    ) {
        val canvas = Canvas(targetBitmap)
        if (clearBitmap) {
            if (backgroundColor != android.graphics.Color.TRANSPARENT) {
                canvas.drawColor(backgroundColor)
            } else {
                canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            }
        }
        val w = targetBitmap.width
        val h = targetBitmap.height
        val combinedLayers = if (frame.localLayers.isEmpty()) {
            frame.layers
        } else {
            frame.layers + frame.localLayers
        }
        drawLayersStackToCanvas(
            layers = combinedLayers,
            canvas = canvas,
            targetWidth = w,
            targetHeight = h
        )
    }

    /**
     * Composites a stack of layers supporting LayerBlendEffect and Clipping Mask ("Máscara de Recorte").
     * Zero extra allocations on standard NORMAL non-clipped stacks.
     */
    fun drawLayersStackToCanvas(
        layers: List<Layer>,
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        colorFilterOverride: ColorFilter? = null,
        visibleBounds: RectF? = null,
        extraStrokesByLayerIndex: Map<Int, List<DrawingStroke>>? = null,
        baseLayerForFirstClipped: Layer? = null,
        baseExtraStrokes: List<DrawingStroke>? = null,
        densityMultiplier: Float = HardwareCapabilitiesEngine.currentProfile.particleDensityMultiplier,
        renderScale: Float = 1.0f
    ) {
        if (layers.isEmpty()) return
        val effScale = renderScale.coerceAtLeast(0.05f)
        val needsAdvancedCompositing = layers.any {
            it.isClippingMask || it.blendEffect != LayerBlendEffect.NORMAL
        }
        if (!needsAdvancedCompositing) {
            if (effScale != 1.0f) {
                canvas.save()
                canvas.scale(effScale, effScale)
            }
            for (i in layers.indices) {
                val layer = layers[i]
                val extra = extraStrokesByLayerIndex?.get(i)
                if (!layer.isVisible || layer.opacity <= 0f) continue
                drawLayerToCanvas(
                    layer = layer,
                    canvas = canvas,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    alphaMultiplier = 1.0f,
                    colorFilterOverride = colorFilterOverride,
                    visibleBounds = visibleBounds,
                    extraStrokes = extra,
                    densityMultiplier = densityMultiplier
                )
            }
            if (effScale != 1.0f) {
                canvas.restore()
            }
            return
        }

        val w = max(1, if (effScale == 1.0f) targetWidth else (targetWidth * effScale + 0.5f).toInt())
        val h = max(1, if (effScale == 1.0f) targetHeight else (targetHeight * effScale + 0.5f).toInt())
        var baseMaskBitmap: Bitmap? = null
        var workBitmap: Bitmap? = null
        var baseLayerRef: Layer? = baseLayerForFirstClipped?.takeIf { it.isVisible && it.opacity > 0f }
        var baseExtraRef: List<DrawingStroke>? = if (baseLayerRef != null) baseExtraStrokes else null
        var baseMaskReady = false

        val blendPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = DST_IN_XFERMODE
        }

        try {
            for (i in layers.indices) {
                val layer = layers[i]
                val extra = extraStrokesByLayerIndex?.get(i)
                if (!layer.isClippingMask) {
                    if (!layer.isVisible || layer.opacity <= 0f) {
                        baseLayerRef = null
                        baseExtraRef = null
                        baseMaskReady = false
                        continue
                    }
                    baseLayerRef = layer
                    baseExtraRef = extra
                    baseMaskReady = false

                    if (layer.blendEffect == LayerBlendEffect.NORMAL) {
                        if (effScale != 1.0f) {
                            canvas.save()
                            canvas.scale(effScale, effScale)
                        }
                        drawLayerToCanvas(
                            layer = layer,
                            canvas = canvas,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            alphaMultiplier = 1.0f,
                            colorFilterOverride = colorFilterOverride,
                            visibleBounds = visibleBounds,
                            extraStrokes = extra,
                            densityMultiplier = densityMultiplier
                        )
                        if (effScale != 1.0f) {
                            canvas.restore()
                        }
                    } else {
                        val wb = workBitmap ?: KodanimaBitmapPool.acquire(
                            w, h, Bitmap.Config.ARGB_8888, android.graphics.Color.TRANSPARENT
                        ).also { workBitmap = it }
                        val wc = Canvas(wb)
                        wc.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        if (effScale != 1.0f) {
                            wc.save()
                            wc.scale(effScale, effScale)
                        }
                        drawLayerToCanvas(
                            layer = layer,
                            canvas = wc,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            alphaMultiplier = 1.0f,
                            colorFilterOverride = colorFilterOverride,
                            visibleBounds = visibleBounds,
                            extraStrokes = extra,
                            densityMultiplier = densityMultiplier
                        )
                        if (effScale != 1.0f) {
                            wc.restore()
                        }
                        blendPaint.alpha = 255
                        applyLayerBlendMode(blendPaint, layer.blendEffect)
                        canvas.drawBitmap(wb, 0f, 0f, blendPaint)
                    }
                } else {
                    if (!layer.isVisible || layer.opacity <= 0f) continue
                    val base = baseLayerRef ?: continue

                    // Lazy-populate alpha mask bitmap of the nearest visible non-clipped layer below
                    if (!baseMaskReady) {
                        val bmb = baseMaskBitmap ?: KodanimaBitmapPool.acquire(
                            w, h, Bitmap.Config.ARGB_8888, android.graphics.Color.TRANSPARENT
                        ).also { baseMaskBitmap = it }
                        val bc = Canvas(bmb)
                        bc.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                        if (effScale != 1.0f) {
                            bc.save()
                            bc.scale(effScale, effScale)
                        }
                        drawLayerToCanvas(
                            layer = base,
                            canvas = bc,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            alphaMultiplier = 1.0f,
                            colorFilterOverride = colorFilterOverride,
                            visibleBounds = visibleBounds,
                            extraStrokes = baseExtraRef,
                            densityMultiplier = densityMultiplier
                        )
                        if (effScale != 1.0f) {
                            bc.restore()
                        }
                        baseMaskReady = true
                    }

                    val wb = workBitmap ?: KodanimaBitmapPool.acquire(
                        w, h, Bitmap.Config.ARGB_8888, android.graphics.Color.TRANSPARENT
                    ).also { workBitmap = it }
                    val wc = Canvas(wb)
                    wc.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    if (effScale != 1.0f) {
                        wc.save()
                        wc.scale(effScale, effScale)
                    }
                    drawLayerToCanvas(
                        layer = layer,
                        canvas = wc,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        alphaMultiplier = 1.0f,
                        colorFilterOverride = colorFilterOverride,
                        visibleBounds = visibleBounds,
                        extraStrokes = extra,
                        densityMultiplier = densityMultiplier
                    )
                    if (effScale != 1.0f) {
                        wc.restore()
                    }
                    // Clip strictly to the base layer's painted alpha pixels
                    wc.drawBitmap(baseMaskBitmap!!, 0f, 0f, maskPaint)

                    blendPaint.alpha = 255
                    applyLayerBlendMode(blendPaint, layer.blendEffect)
                    canvas.drawBitmap(wb, 0f, 0f, blendPaint)
                }
            }
        } finally {
            workBitmap?.let { KodanimaBitmapPool.release(it) }
            baseMaskBitmap?.let { KodanimaBitmapPool.release(it) }
        }
    }

    /**
     * Draws a single layer onto a Canvas with viewport chunk culling and Mali-G57 TBDR optimization.
     * Avoids allocating an offscreen saveLayer() FBO unless the layer actually contains ERASER strokes;
     * when ERASER strokes are present, restricts saveLayer() to the tight bounding rect of the layer.
     */
    fun drawLayerToCanvas(
        layer: Layer,
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        alphaMultiplier: Float = 1.0f,
        colorFilterOverride: ColorFilter? = null,
        visibleBounds: RectF? = null,
        extraStrokes: List<DrawingStroke>? = null,
        densityMultiplier: Float = HardwareCapabilitiesEngine.currentProfile.particleDensityMultiplier
    ) {
        val layerAlpha = (layer.opacity * alphaMultiplier).coerceIn(0f, 1f)
        if (layerAlpha <= 0f) return

        val baseStrokes = layer.strokes
        val extraCount = extraStrokes?.size ?: 0
        val attachment = layer.imageAttachment
        val hasAttachment = attachment != null && attachment.uriString.isNotBlank()
        if (baseStrokes.isEmpty() && extraCount == 0 && !hasAttachment) return

        // Scan once to check for eraser strokes and compute tight layer bounds (for TBDR saveLayer)
        var hasEraser = false
        var minX = if (hasAttachment) 0f else Float.MAX_VALUE
        var minY = if (hasAttachment) 0f else Float.MAX_VALUE
        var maxX = if (hasAttachment) targetWidth.toFloat() else -Float.MAX_VALUE
        var maxY = if (hasAttachment) targetHeight.toFloat() else -Float.MAX_VALUE

        for (i in baseStrokes.indices) {
            val s = baseStrokes[i]
            if (s.tool == ToolType.ERASER) hasEraser = true
            if (s.points.isNotEmpty() && s.minX != Float.MAX_VALUE && s.maxX != Float.MIN_VALUE) {
                val pad = s.brushSize + 8f
                if (s.minX - pad < minX) minX = s.minX - pad
                if (s.minY - pad < minY) minY = s.minY - pad
                if (s.maxX + pad > maxX) maxX = s.maxX + pad
                if (s.maxY + pad > maxY) maxY = s.maxY + pad
            } else {
                minX = 0f; minY = 0f; maxX = targetWidth.toFloat(); maxY = targetHeight.toFloat()
            }
        }
        if (extraStrokes != null) {
            for (i in extraStrokes.indices) {
                val s = extraStrokes[i]
                if (s.tool == ToolType.ERASER) hasEraser = true
                if (s.points.isNotEmpty() && s.minX != Float.MAX_VALUE && s.maxX != Float.MIN_VALUE) {
                    val pad = s.brushSize + 8f
                    if (s.minX - pad < minX) minX = s.minX - pad
                    if (s.minY - pad < minY) minY = s.minY - pad
                    if (s.maxX + pad > maxX) maxX = s.maxX + pad
                    if (s.maxY + pad > maxY) maxY = s.maxY + pad
                } else {
                    minX = 0f; minY = 0f; maxX = targetWidth.toFloat(); maxY = targetHeight.toFloat()
                }
            }
        }

        // Isolate via bounded saveLayer when ERASER is present OR layer opacity < 1f so strokes never
        // compound alpha irregularly or "dissolve" on Onion Skin / semi-transparent layers.
        val scratch = scratchThreadLocal.get()!!
        val needsLayerIsolation = hasEraser || layerAlpha < 0.999f
        val layerCount = if (needsLayerIsolation) {
            val lLeft = max(0f, if (visibleBounds != null) max(minX, visibleBounds.left) else minX)
            val lTop = max(0f, if (visibleBounds != null) max(minY, visibleBounds.top) else minY)
            val lRight = min(targetWidth.toFloat(), if (visibleBounds != null) min(maxX, visibleBounds.right) else maxX)
            val lBottom = min(targetHeight.toFloat(), if (visibleBounds != null) min(maxY, visibleBounds.bottom) else maxY)
            if (lRight <= lLeft || lBottom <= lTop) return

            val layerPaint = if (layerAlpha < 0.999f) {
                scratch.layerAlphaPaint.apply {
                    alpha = (layerAlpha * 255f).toInt().coerceIn(0, 255)
                    colorFilter = null
                }
            } else null
            canvas.saveLayer(lLeft, lTop, lRight, lBottom, layerPaint)
        } else {
            -1
        }

        val strokeAlphaMult = if (layerCount != -1) 1.0f else layerAlpha

        if (attachment != null) {
            val bmp = resolveLayerImageBitmap(attachment.uriString)
            if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                val baseFit = min(
                    targetWidth.toFloat() / bmp.width.toFloat(),
                    targetHeight.toFloat() / bmp.height.toFloat()
                )
                val totalScale = baseFit * attachment.normalizedScaleFactor()
                val cx = targetWidth * 0.5f + attachment.offsetX * targetWidth
                val cy = targetHeight * 0.5f + attachment.offsetY * targetHeight
                val imgPaint = scratch.layerAlphaPaint
                imgPaint.alpha = (attachment.opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
                imgPaint.colorFilter = colorFilterOverride
                canvas.save()
                canvas.translate(cx, cy)
                if (abs(attachment.rotationDegrees) > 0.01f) {
                    canvas.rotate(attachment.rotationDegrees)
                }
                canvas.scale(totalScale, totalScale)
                canvas.drawBitmap(bmp, -bmp.width * 0.5f, -bmp.height * 0.5f, imgPaint)
                imgPaint.colorFilter = null
                canvas.restore()
            }
        }

        for (i in baseStrokes.indices) {
            val stroke = baseStrokes[i]
            if (visibleBounds != null && !stroke.intersects(visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom)) {
                continue
            }
            drawStrokeToCanvas(stroke, canvas, strokeAlphaMult, colorFilterOverride, densityMultiplier)
        }

        if (extraStrokes != null) {
            for (i in extraStrokes.indices) {
                val stroke = extraStrokes[i]
                if (visibleBounds != null && !stroke.intersects(visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom)) {
                    continue
                }
                drawStrokeToCanvas(stroke, canvas, strokeAlphaMult, colorFilterOverride, densityMultiplier)
            }
        }

        if (layerCount != -1) {
            canvas.restoreToCount(layerCount)
        }
    }

    /**
     * Incrementally draws only a slice of stroke points [startPointIdx..endPointIdx] for O(1) active stroke/eraser updates.
     */
    fun drawStrokeSegmentRange(
        stroke: DrawingStroke,
        canvas: Canvas,
        startPointIdx: Int,
        endPointIdx: Int,
        alphaMultiplier: Float = 1.0f,
        colorFilterOverride: ColorFilter? = null,
        densityMultiplier: Float = HardwareCapabilitiesEngine.currentProfile.particleDensityMultiplier
    ) {
        val pts = stroke.points
        if (pts.isEmpty()) return
        val from = startPointIdx.coerceIn(0, pts.lastIndex)
        val to = endPointIdx.coerceIn(from, pts.lastIndex)
        if (from == to && pts.size > 1) return
        val subPoints = if (from == 0 && to == pts.lastIndex) pts else pts.subList(from, to + 1)
        val slice = stroke.copy(points = subPoints)
        drawStrokeToCanvas(slice, canvas, alphaMultiplier, colorFilterOverride, densityMultiplier)
    }

    /**
     * Draws a single stroke onto a Canvas with zero-allocation Paint reuse and Skia NEON batching.
     */
    fun drawStrokeToCanvas(
        stroke: DrawingStroke,
        canvas: Canvas,
        alphaMultiplier: Float = 1.0f,
        colorFilterOverride: ColorFilter? = null,
        densityMultiplier: Float = HardwareCapabilitiesEngine.currentProfile.particleDensityMultiplier
    ) {
        val points = stroke.points
        if (points.isEmpty()) return

        val scratch = scratchThreadLocal.get()!!
        val baseColor = stroke.color.toInt()
        val originalAlpha = (baseColor ushr 24) and 0xFF
        val effStrokeOpacity = ((originalAlpha / 255f) * stroke.opacity * alphaMultiplier).coerceIn(0f, 1f)
        val isolateSemiTransparentStroke = points.size > 1 &&
            effStrokeOpacity < 0.999f &&
            stroke.tool != ToolType.ERASER &&
            stroke.tool != ToolType.BUCKET &&
            stroke.tool != ToolType.LINE &&
            stroke.tool != ToolType.RECTANGLE &&
            stroke.tool != ToolType.CIRCLE &&
            !(stroke.tool == ToolType.LASSO && stroke.isFilled)

        val finalAlpha = if (isolateSemiTransparentStroke) {
            255
        } else {
            (effStrokeOpacity * 255f).toInt().coerceIn(0, 255)
        }
        val strokeColor = (finalAlpha shl 24) or (baseColor and 0x00FFFFFF)

        val clipPoly = stroke.clipPolygon
        val hasClip = clipPoly != null && clipPoly.size >= 3
        if (hasClip) {
            val cp = scratch.clipPath
            cp.reset()
            cp.moveTo(clipPoly!![0].x, clipPoly[0].y)
            for (i in 1 until clipPoly.size) {
                cp.lineTo(clipPoly[i].x, clipPoly[i].y)
            }
            cp.close()
            canvas.save()
            canvas.clipPath(cp)
        }
        var strokeSaveLayerCount = -1
        if (isolateSemiTransparentStroke) {
            var sMinX = stroke.minX
            var sMinY = stroke.minY
            var sMaxX = stroke.maxX
            var sMaxY = stroke.maxY
            if (sMinX == Float.MAX_VALUE || sMaxX == Float.MIN_VALUE) {
                sMinX = Float.MAX_VALUE; sMinY = Float.MAX_VALUE
                sMaxX = -Float.MAX_VALUE; sMaxY = -Float.MAX_VALUE
                for (pt in points) {
                    if (pt.x < sMinX) sMinX = pt.x
                    if (pt.y < sMinY) sMinY = pt.y
                    if (pt.x > sMaxX) sMaxX = pt.x
                    if (pt.y > sMaxY) sMaxY = pt.y
                }
            }
            val pad = stroke.brushSize * 1.85f + 16f
            val isoPaint = scratch.layerAlphaPaint.apply {
                alpha = (effStrokeOpacity * 255f).toInt().coerceIn(0, 255)
                colorFilter = null
            }
            strokeSaveLayerCount = canvas.saveLayer(
                sMinX - pad, sMinY - pad, sMaxX + pad, sMaxY + pad, isoPaint
            )
        }
        try {
            when (stroke.tool) {
                ToolType.ERASER -> {
                    val params = resolveDynamicParams(stroke)
                    val eraserAlpha = (stroke.opacity * params.opacityScale * 255f).toInt().coerceIn(24, 255)
                    if (points.size == 1) {
                        val p = points[0]
                        val rad = max(1.5f, (stroke.brushSize * params.sizeScale * p.pressure) / 2f)
                        scratch.eraserFillPaint.alpha = eraserAlpha
                        canvas.drawCircle(p.x, p.y, rad, scratch.eraserFillPaint)
                    } else {
                        val ep = scratch.eraserPaint
                        ep.alpha = eraserAlpha
                        val smCount = smoothPointsQuadratic(points, scratch)
                        val sm = scratch.smoothXYZ
                        for (i in 1 until smCount) {
                            val b0 = (i - 1) * 3
                            val b1 = i * 3
                            val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.15f, 1.05f)
                            ep.strokeWidth = max(1.5f, stroke.brushSize * params.sizeScale * avgPress)
                            canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], ep)
                        }
                    }
                }
                ToolType.LINE -> {
                    val p = scratch.strokePaint.apply {
                        color = strokeColor
                        strokeWidth = stroke.brushSize
                        colorFilter = colorFilterOverride
                    }
                    val first = points[0]
                    val last = points[points.lastIndex]
                    canvas.drawLine(first.x, first.y, last.x, last.y, p)
                }
                ToolType.RECTANGLE -> {
                    val p = if (stroke.isFilled) scratch.fillPaint else scratch.strokePaint
                    p.color = strokeColor
                    p.strokeWidth = stroke.brushSize
                    p.colorFilter = colorFilterOverride
                    val first = points[0]
                    val last = points[points.lastIndex]
                    canvas.drawRect(
                        min(first.x, last.x),
                        min(first.y, last.y),
                        max(first.x, last.x),
                        max(first.y, last.y),
                        p
                    )
                }
                ToolType.CIRCLE -> {
                    val p = if (stroke.isFilled) scratch.fillPaint else scratch.strokePaint
                    p.color = strokeColor
                    p.strokeWidth = stroke.brushSize
                    p.colorFilter = colorFilterOverride
                    val first = points[0]
                    val last = points[points.lastIndex]
                    scratch.rectF.set(
                        min(first.x, last.x),
                        min(first.y, last.y),
                        max(first.x, last.x),
                        max(first.y, last.y)
                    )
                    canvas.drawOval(scratch.rectF, p)
                }
                ToolType.BUCKET -> {
                    val p = scratch.strokePaint.apply {
                        color = strokeColor
                        strokeWidth = 2.5f
                        colorFilter = colorFilterOverride
                    }
                    val pairCount = (points.size / 2) * 2
                    if (pairCount >= 2) {
                        val buf = scratch.ensureCapacity(pairCount * 2)
                        var outIdx = 0
                        var i = 0
                        while (i < pairCount) {
                            val pt = points[i]
                            buf[outIdx++] = pt.x
                            buf[outIdx++] = pt.y
                            i++
                        }
                        // Single batched JNI drawLines call -> processed via ARM NEON/ASIMD in libhwui
                        canvas.drawLines(buf, 0, outIdx, p)
                    }
                }
                ToolType.BEZIER_PEN -> {
                    val params = resolveDynamicParams(stroke)
                    val bezAlpha = ((originalAlpha * stroke.opacity * params.opacityScale * params.flow * alphaMultiplier).toInt()).coerceIn(6, 255)
                    val bezColor = (bezAlpha shl 24) or (baseColor and 0x00FFFFFF)
                    val p = scratch.strokePaint.apply {
                        color = bezColor
                        strokeWidth = max(0.8f, stroke.brushSize * params.sizeScale)
                        colorFilter = colorFilterOverride
                    }
                    if (points.size == 1) {
                        canvas.drawCircle(points[0].x, points[0].y, p.strokeWidth * 0.5f, scratch.fillPaint.apply {
                            color = bezColor
                            colorFilter = colorFilterOverride
                        })
                    } else {
                        val path = scratch.path
                        path.reset()
                        path.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size - 1) {
                            val curr = points[i]
                            val next = points[i + 1]
                            path.quadTo(curr.x, curr.y, (curr.x + next.x) * 0.5f, (curr.y + next.y) * 0.5f)
                        }
                        val last = points[points.lastIndex]
                        path.lineTo(last.x, last.y)
                        canvas.drawPath(path, p)
                    }
                }
                ToolType.LASSO -> {
                    if (stroke.isFilled && points.size >= 3) {
                        val path = scratch.path
                        path.reset()
                        path.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            path.lineTo(points[i].x, points[i].y)
                        }
                        path.close()
                        val fp = scratch.fillPaint.apply {
                            color = strokeColor
                            colorFilter = colorFilterOverride
                        }
                        canvas.drawPath(path, fp)
                    } else {
                        drawCustomTexturedBrushStroke(
                            canvas = canvas,
                            stroke = stroke,
                            strokeColor = strokeColor,
                            colorFilterOverride = colorFilterOverride,
                            scratch = scratch,
                            densityMultiplier = densityMultiplier
                        )
                    }
                }
                ToolType.PEN, ToolType.PENCIL, ToolType.BRUSH, ToolType.EYEDROPPER, ToolType.MAGIC_WAND -> {
                    drawCustomTexturedBrushStroke(
                        canvas = canvas,
                        stroke = stroke,
                        strokeColor = strokeColor,
                        colorFilterOverride = colorFilterOverride,
                        scratch = scratch,
                        densityMultiplier = densityMultiplier
                    )
                }
            }
        } finally {
            if (strokeSaveLayerCount != -1) {
                canvas.restoreToCount(strokeSaveLayerCount)
            }
            if (hasClip) {
                canvas.restore()
            }
        }
    }

    private fun resolveDynamicParams(stroke: DrawingStroke): BrushDynamicParams {
        val raw = stroke.customBrushParams
        if (!raw.isNullOrEmpty()) {
            dynamicParamsCache.get(raw)?.let { return it }
            BrushDynamicParams.fromJsonOrNull(raw)?.let { parsed ->
                dynamicParamsCache.put(raw, parsed)
                return parsed
            }
        }
        val preset = BrushPreset.findById(stroke.brushPresetId)
        return preset.dynamicParams
    }

    private fun lerpRgb(primaryRgb: Int, secondaryRgb: Int, mix: Float): Int {
        val t = mix.coerceIn(0f, 1f)
        if (t <= 0.001f) return primaryRgb and 0x00FFFFFF
        if (t >= 0.999f) return secondaryRgb and 0x00FFFFFF
        val r1 = (primaryRgb ushr 16) and 0xFF
        val g1 = (primaryRgb ushr 8) and 0xFF
        val b1 = primaryRgb and 0xFF
        val r2 = (secondaryRgb ushr 16) and 0xFF
        val g2 = (secondaryRgb ushr 8) and 0xFF
        val b2 = secondaryRgb and 0xFF
        val r = (r1 + (r2 - r1) * t + 0.5f).toInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t + 0.5f).toInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t + 0.5f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun computeContrastDotRgb(baseRgb: Int): Int {
        val r = (baseRgb ushr 16) and 0xFF
        val g = (baseRgb ushr 8) and 0xFF
        val b = baseRgb and 0xFF
        val luma = r * 0.299f + g * 0.587f + b * 0.114f
        val shift = if (luma > 135f) -68 else 68
        val nr = (r + shift).coerceIn(0, 255)
        val ng = (g + shift).coerceIn(0, 255)
        val nb = (b + shift).coerceIn(0, 255)
        return (nr shl 16) or (ng shl 8) or nb
    }

    private fun drawKritaParametricOrTipStroke(
        canvas: Canvas,
        points: List<StrokePoint>,
        baseWidth: Float,
        strokeColor: Int,
        params: BrushDynamicParams,
        colorFilterOverride: ColorFilter?,
        scratch: StrokeScratch,
        densityMultiplier: Float
    ) {
        val effectiveOrigAlpha = (((strokeColor ushr 24) and 0xFF) * params.opacityScale * params.flow).toInt().coerceIn(8, 255)
        val rgb = strokeColor and 0x00FFFFFF
        val tipBmp = resolveTipStampBitmap(params.tipStampBase64)
        val secTipBmp = if (params.dualShapeEnabled) resolveTipStampBitmap(params.secondaryTipStampBase64) else null

        if (points.size == 1) {
            val p = points[0]
            stampKritaDab(
                canvas = canvas,
                cx = p.x,
                cy = p.y,
                diameter = max(1.6f, baseWidth * p.pressure),
                strokeDirDeg = 0f,
                dabIdx = 0,
                progress = 0.5f,
                tipBmp = tipBmp,
                secTipBmp = secTipBmp,
                colorFilterOverride = colorFilterOverride,
                rgb = rgb,
                alpha = effectiveOrigAlpha,
                params = params,
                scratch = scratch,
                densityMultiplier = densityMultiplier
            )
            return
        }

        val smCount = smoothPointsQuadratic(points, scratch, maxSubdivisionsPerSeg = 6)
        val sm = scratch.smoothXYZ
        val spacingRatio = params.spacing.coerceIn(0.05f, 0.85f)
        val minStep = max(1.4f, baseWidth * spacingRatio * 0.68f)

        var accum = minStep // stamp first point immediately
        var dabIdx = 0
        for (i in 1 until smCount) {
            val b0 = (i - 1) * 3
            val b1 = i * 3
            val x0 = sm[b0]; val y0 = sm[b0 + 1]; val p0 = sm[b0 + 2]
            val x1 = sm[b1]; val y1 = sm[b1 + 1]; val p1 = sm[b1 + 2]
            val dx = x1 - x0
            val dy = y1 - y0
            val segLen = sqrt(dx * dx + dy * dy)
            if (segLen < 0.01f) continue

            val dirDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val invSeg = 1f / segLen
            var walked = 0f
            while (walked < segLen) {
                val needed = minStep - accum
                if (walked + needed <= segLen) {
                    walked += needed
                    val t = (walked * invSeg).coerceIn(0f, 1f)
                    val sx = x0 + dx * t
                    val sy = y0 + dy * t
                    val sp = (p0 + (p1 - p0) * t).coerceIn(0.12f, 1.15f)
                    val prog = (i.toFloat() / max(1, smCount - 1)).coerceIn(0f, 1f)
                    val taper = if (params.taperStrength > 0.05f) {
                        val sT = if (prog < 0.09f) (1f - (1f - prog / 0.09f) * 0.6f * params.taperStrength) else 1f
                        val eT = if (prog > 0.90f) (1f - ((prog - 0.90f) / 0.10f) * 0.6f * params.taperStrength) else 1f
                        min(sT, eT).coerceIn(0.2f, 1f)
                    } else 1f
                    stampKritaDab(
                        canvas = canvas,
                        cx = sx,
                        cy = sy,
                        diameter = max(1.5f, baseWidth * sp * taper),
                        strokeDirDeg = dirDeg,
                        dabIdx = dabIdx++,
                        progress = prog,
                        tipBmp = tipBmp,
                        secTipBmp = secTipBmp,
                        colorFilterOverride = colorFilterOverride,
                        rgb = rgb,
                        alpha = effectiveOrigAlpha,
                        params = params,
                        scratch = scratch,
                        densityMultiplier = densityMultiplier
                    )
                    accum = 0f
                } else {
                    accum += (segLen - walked)
                    walked = segLen
                }
            }
        }
    }

    private fun drawSingleShapeStamp(
        canvas: Canvas,
        halfW: Float,
        halfH: Float,
        shapeKind: String,
        spikes: Int,
        hardness: Float,
        tipBmp: Bitmap?,
        dabRgb: Int,
        dabAlpha: Int,
        colorFilterOverride: ColorFilter?,
        scratch: StrokeScratch,
        overrideXfermode: PorterDuffXfermode? = null
    ) {
        if (tipBmp != null && !tipBmp.isRecycled) {
            val bmpPaint = scratch.layerAlphaPaint
            bmpPaint.alpha = dabAlpha
            bmpPaint.colorFilter = colorFilterOverride ?: resolveTintFilter(dabRgb)
            bmpPaint.xfermode = overrideXfermode
            scratch.rectF.set(-halfW, -halfH, halfW, halfH)
            canvas.drawBitmap(tipBmp, null, scratch.rectF, bmpPaint)
            bmpPaint.xfermode = null
            bmpPaint.colorFilter = null
            return
        }
        val fillPaint = scratch.fillPaint.apply {
            color = (dabAlpha shl 24) or dabRgb
            colorFilter = colorFilterOverride
            xfermode = overrideXfermode
        }
        val softness = (1f - hardness).coerceIn(0f, 0.9f)
        if (spikes > 2 && overrideXfermode == null) {
            val p = scratch.path
            p.reset()
            val vertices = spikes * 2
            for (v in 0 until vertices) {
                val a = (v.toFloat() / vertices) * Math.PI * 2.0
                val rScale = if (v % 2 == 0) 1.0f else (0.45f + 0.35f * hardness)
                val px = (cos(a) * halfW * rScale).toFloat()
                val py = (sin(a) * halfH * rScale).toFloat()
                if (v == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            canvas.drawPath(p, fillPaint)
        } else if (shapeKind == "rect") {
            canvas.drawRect(-halfW, -halfH, halfW, halfH, fillPaint)
        } else if ((softness > 0.2f || shapeKind == "gauss" || shapeKind == "soft") && overrideXfermode == null) {
            fillPaint.alpha = (dabAlpha * 0.35f).toInt().coerceIn(3, 255)
            scratch.rectF.set(-halfW * 1.28f, -halfH * 1.28f, halfW * 1.28f, halfH * 1.28f)
            canvas.drawOval(scratch.rectF, fillPaint)
            fillPaint.alpha = (dabAlpha * 0.85f).toInt().coerceIn(4, 255)
            scratch.rectF.set(-halfW * 0.82f, -halfH * 0.82f, halfW * 0.82f, halfH * 0.82f)
            canvas.drawOval(scratch.rectF, fillPaint)
        } else {
            scratch.rectF.set(-halfW, -halfH, halfW, halfH)
            canvas.drawOval(scratch.rectF, fillPaint)
        }
        fillPaint.xfermode = null
    }

    private fun stampKritaDab(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        diameter: Float,
        strokeDirDeg: Float,
        dabIdx: Int,
        progress: Float,
        tipBmp: Bitmap?,
        secTipBmp: Bitmap?,
        colorFilterOverride: ColorFilter?,
        rgb: Int,
        alpha: Int,
        params: BrushDynamicParams,
        scratch: StrokeScratch,
        densityMultiplier: Float
    ) {
        val halfW = max(0.8f, diameter * 0.5f)
        val halfH = max(0.6f, halfW * params.aspectRatio.coerceIn(0.08f, 1.0f))

        var drawX = cx
        var drawY = cy
        if (params.scatter > 0.01f) {
            val angleLut = (fastHashNoise(cx, cy, dabIdx * 11 + 3) * LUT_SIZE).toInt() and LUT_MASK
            val scatterR = halfW * params.scatter * 1.4f * fastHashNoise(cx, cy, dabIdx * 17 + 7)
            drawX += COS_LUT[angleLut] * scatterR
            drawY += SIN_LUT[angleLut] * scatterR
        }

        val grainMod = if (params.grainAmount > 0.1f) {
            1f - params.grainAmount * 0.42f * fastHashNoise(drawX, drawY, dabIdx * 5 + 1)
        } else 1f
        val dabAlpha = (alpha * grainMod).toInt().coerceIn(4, 255)

        val secRawRgb = (params.secondaryColor.toInt() and 0x00FFFFFF)
        val dabRgb = if (params.dualColorEnabled) {
            when (params.dualColorMode) {
                "gradient" -> lerpRgb(rgb, secRawRgb, progress * (0.35f + 0.65f * params.secondaryColorMix))
                "alternating" -> if ((dabIdx and 1) == 1) lerpRgb(rgb, secRawRgb, max(0.65f, params.secondaryColorMix)) else rgb
                "edge" -> lerpRgb(rgb, secRawRgb, params.secondaryColorMix * 0.25f)
                else -> rgb
            }
        } else rgb

        var totalRot = params.angleDeg
        if (params.followStrokeAngle) totalRot += strokeDirDeg
        if (params.angularJitter > 0.01f) {
            totalRot += (fastHashNoise(drawX, drawY, dabIdx * 19 + 11) - 0.5f) * 180f * params.angularJitter
        }

        canvas.save()
        canvas.translate(drawX, drawY)
        if (abs(totalRot) > 0.25f) {
            canvas.rotate(totalRot)
        }
        try {
            if (params.smudgeRate > 0.12f) {
                val wetPaint = scratch.fillPaint.apply {
                    color = dabRgb or ((dabAlpha * 0.22f * params.smudgeRate).toInt().coerceIn(3, 180) shl 24)
                    colorFilter = colorFilterOverride
                    xfermode = null
                }
                scratch.rectF.set(-halfW * 1.25f, -halfH * 1.25f, halfW * 1.25f, halfH * 1.25f)
                canvas.drawOval(scratch.rectF, wetPaint)
            }

            // Dual-color "edge" outer rim behind the primary dab
            if (params.dualColorEnabled && params.dualColorMode == "edge") {
                val edgeScale = 1.18f + 0.22f * params.secondaryColorMix
                val edgeAlpha = (dabAlpha * (0.45f + 0.45f * params.secondaryColorMix)).toInt().coerceIn(6, 255)
                drawSingleShapeStamp(
                    canvas = canvas,
                    halfW = halfW * edgeScale,
                    halfH = halfH * edgeScale,
                    shapeKind = params.shapeKind,
                    spikes = params.spikes,
                    hardness = params.hardness,
                    tipBmp = tipBmp,
                    dabRgb = secRawRgb,
                    dabAlpha = edgeAlpha,
                    colorFilterOverride = colorFilterOverride,
                    scratch = scratch
                )
            }

            val isMaskedDualShape = params.dualShapeEnabled && params.dualShapeBlendMode == "mask"
            val secScale = params.dualShapeScale.coerceIn(0.25f, 1.8f)
            val maskSaveCount = if (isMaskedDualShape) {
                val mW = max(halfW * 1.45f, halfW * secScale * 1.35f)
                val mH = max(halfH * 1.45f, halfH * secScale * 1.35f)
                canvas.saveLayer(-mW, -mH, mW, mH, null)
            } else -1

            try {
                drawSingleShapeStamp(
                    canvas = canvas,
                    halfW = halfW,
                    halfH = halfH,
                    shapeKind = params.shapeKind,
                    spikes = params.spikes,
                    hardness = params.hardness,
                    tipBmp = tipBmp,
                    dabRgb = dabRgb,
                    dabAlpha = dabAlpha,
                    colorFilterOverride = colorFilterOverride,
                    scratch = scratch
                )

                if (params.dualShapeEnabled) {
                    if (isMaskedDualShape) {
                        canvas.save()
                        canvas.rotate((dabIdx * 23f) % 360f)
                        drawSingleShapeStamp(
                            canvas = canvas,
                            halfW = halfW * secScale,
                            halfH = halfH * secScale,
                            shapeKind = params.secondaryShapeKind,
                            spikes = 0,
                            hardness = params.hardness,
                            tipBmp = secTipBmp,
                            dabRgb = 0xFFFFFF,
                            dabAlpha = 255,
                            colorFilterOverride = null,
                            scratch = scratch,
                            overrideXfermode = DST_IN_XFERMODE
                        )
                        canvas.restore()
                    } else {
                        // "combine": stamps secondary shape/tip alongside primary shape
                        val secColor = if (params.dualColorEnabled) secRawRgb else dabRgb
                        val secAlpha = (dabAlpha * 0.80f).toInt().coerceIn(4, 255)
                        val offsetX = halfW * 0.14f * ((dabIdx and 1) * 2 - 1)
                        val offsetY = halfH * 0.14f * (((dabIdx ushr 1) and 1) * 2 - 1)
                        canvas.save()
                        canvas.translate(offsetX, offsetY)
                        canvas.rotate(18f + (dabIdx % 4) * 15f)
                        drawSingleShapeStamp(
                            canvas = canvas,
                            halfW = halfW * secScale,
                            halfH = halfH * secScale,
                            shapeKind = params.secondaryShapeKind,
                            spikes = 0,
                            hardness = params.hardness,
                            tipBmp = secTipBmp,
                            dabRgb = secColor,
                            dabAlpha = secAlpha,
                            colorFilterOverride = colorFilterOverride,
                            scratch = scratch
                        )
                        canvas.restore()
                    }
                }
            } finally {
                if (maskSaveCount != -1) {
                    canvas.restoreToCount(maskSaveCount)
                }
            }

            if (tipBmp == null && params.grainAmount > 0.25f) {
                val speckCount = (4 * densityMultiplier).toInt().coerceIn(1, 5)
                val buf = scratch.ensureCapacity(speckCount * 2)
                for (s in 0 until speckCount) {
                    buf[s * 2] = (fastHashNoise(drawX, drawY, dabIdx * 7 + s) - 0.5f) * halfW * 1.8f
                    buf[s * 2 + 1] = (fastHashNoise(drawY, drawX, dabIdx * 9 + s) - 0.5f) * halfH * 1.8f
                }
                val ptPaint = scratch.particlePaint.apply {
                    color = ((dabAlpha * 0.7f).toInt().coerceIn(4, 255) shl 24) or dabRgb
                    colorFilter = colorFilterOverride
                    strokeWidth = max(1.1f, halfW * 0.16f)
                }
                canvas.drawPoints(buf, 0, speckCount * 2, ptPaint)
            }

            // Crisp internal dots inside the dab bounds
            if (params.innerDotsEnabled) {
                val dotCount = ((3f + params.innerDotsDensity * 11f) * densityMultiplier).toInt().coerceIn(2, 16)
                val dotRgb = if (params.innerDotsUseSecondaryColor) {
                    if (secRawRgb != dabRgb) secRawRgb else computeContrastDotRgb(dabRgb)
                } else {
                    computeContrastDotRgb(dabRgb)
                }
                val dotAlpha = (dabAlpha * 0.92f).toInt().coerceIn(12, 255)
                val dotPaint = scratch.fillPaint.apply {
                    color = (dotAlpha shl 24) or dotRgb
                    colorFilter = colorFilterOverride
                    xfermode = null
                }
                val dotRadius = max(0.8f, min(halfW, halfH) * (0.07f + 0.15f * params.innerDotsSize))
                for (d in 0 until dotCount) {
                    val lutIdx = (fastHashNoise(drawX, drawY, dabIdx * 29 + d * 13) * LUT_SIZE).toInt() and LUT_MASK
                    val radial = sqrt(fastHashNoise(drawY, drawX, dabIdx * 37 + d * 17)) * 0.76f
                    val dxPos = COS_LUT[lutIdx] * radial * halfW
                    val dyPos = SIN_LUT[lutIdx] * radial * halfH
                    canvas.drawCircle(dxPos, dyPos, dotRadius, dotPaint)
                }
            }
        } finally {
            canvas.restore()
        }
    }

    private fun drawCustomTexturedBrushStroke(
        canvas: Canvas,
        stroke: DrawingStroke,
        strokeColor: Int,
        colorFilterOverride: ColorFilter?,
        scratch: StrokeScratch,
        densityMultiplier: Float
    ) {
        val points = stroke.points
        if (points.isEmpty()) return

        val params = resolveDynamicParams(stroke)
        if (params.hasCustomKritaShapeOrTip()) {
            drawKritaParametricOrTipStroke(
                canvas = canvas,
                points = points,
                baseWidth = stroke.brushSize * params.sizeScale,
                strokeColor = strokeColor,
                params = params,
                colorFilterOverride = colorFilterOverride,
                scratch = scratch,
                densityMultiplier = densityMultiplier
            )
            return
        }

        val engine = params.engineKind
        val baseWidth = stroke.brushSize * params.sizeScale
        val effectiveOrigAlpha = (((strokeColor ushr 24) and 0xFF) * params.opacityScale * params.flow).toInt().coerceIn(8, 255)
        val rgb = strokeColor and 0x00FFFFFF
        val effColor = (effectiveOrigAlpha shl 24) or rgb

        val paint = scratch.strokePaint.apply {
            color = effColor
            colorFilter = colorFilterOverride
        }
        val fillPaint = scratch.fillPaint.apply {
            color = effColor
            colorFilter = colorFilterOverride
        }
        val ptPaint = scratch.particlePaint.apply {
            color = effColor
            colorFilter = colorFilterOverride
        }

        // Single tap stamp
        if (points.size == 1) {
            val p = points[0]
            val rad = max(1.2f, (baseWidth * p.pressure) * 0.5f)
            when (engine) {
                KritaBrushEngineKind.PIXEL_ROUND_SIZE, KritaBrushEngineKind.INK_GPEN -> {
                    canvas.drawCircle(p.x, p.y, rad, fillPaint)
                }
                KritaBrushEngineKind.PIXEL_OPACITY, KritaBrushEngineKind.BLENDER_WET_SOFT -> {
                    fillPaint.alpha = (effectiveOrigAlpha * 0.45f).toInt().coerceIn(6, 255)
                    canvas.drawCircle(p.x, p.y, rad * 1.25f, fillPaint)
                    fillPaint.alpha = (effectiveOrigAlpha * 0.85f).toInt()
                    canvas.drawCircle(p.x, p.y, rad * 0.72f, fillPaint)
                }
                KritaBrushEngineKind.PENCIL_GRAPHITE, KritaBrushEngineKind.INK_ROUGH,
                KritaBrushEngineKind.CHARCOAL_SOFT, KritaBrushEngineKind.CHALK_GRAINY -> {
                    fillPaint.alpha = (effectiveOrigAlpha * 0.58f).toInt().coerceIn(8, 255)
                    canvas.drawCircle(p.x, p.y, rad * 0.85f, fillPaint)
                    val specks = if (engine == KritaBrushEngineKind.CHALK_GRAINY) 16 else 12
                    val buf = scratch.ensureCapacity(specks * 2)
                    for (s in 0 until specks) {
                        val lutIdx = (fastHashNoise(p.x, p.y, s) * LUT_SIZE).toInt() and LUT_MASK
                        val dist = rad * (0.15f + 0.85f * fastHashNoise(p.x, p.y, s + 20))
                        buf[s * 2] = p.x + COS_LUT[lutIdx] * dist
                        buf[s * 2 + 1] = p.y + SIN_LUT[lutIdx] * dist
                    }
                    ptPaint.alpha = (effectiveOrigAlpha * 0.82f).toInt().coerceIn(10, 255)
                    ptPaint.strokeWidth = if (engine == KritaBrushEngineKind.CHARCOAL_SOFT || engine == KritaBrushEngineKind.CHALK_GRAINY) 2.6f else 1.8f
                    canvas.drawPoints(buf, 0, specks * 2, ptPaint)
                }
                KritaBrushEngineKind.MARKER_CHISEL -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(42f)
                    fillPaint.alpha = (effectiveOrigAlpha * 0.65f).toInt().coerceIn(10, 255)
                    canvas.drawRect(-rad * 1.35f, -rad * 0.42f, rad * 1.35f, rad * 0.42f, fillPaint)
                    canvas.restore()
                }
                KritaBrushEngineKind.AIRBRUSH_SOFT -> {
                    val sprayRad = rad * 1.65f
                    val count = (26 * densityMultiplier).toInt().coerceIn(8, 28)
                    val buf = scratch.ensureCapacity(count * 2)
                    for (s in 0 until count) {
                        val lutIdx = (fastHashNoise(p.x, p.y, s) * LUT_SIZE).toInt() and LUT_MASK
                        val dist = sprayRad * sqrt(fastHashNoise(p.x, p.y, s + 30))
                        buf[s * 2] = p.x + COS_LUT[lutIdx] * dist
                        buf[s * 2 + 1] = p.y + SIN_LUT[lutIdx] * dist
                    }
                    ptPaint.alpha = (effectiveOrigAlpha * 0.42f).toInt().coerceIn(8, 255)
                    ptPaint.strokeWidth = 2.4f
                    canvas.drawPoints(buf, 0, count * 2, ptPaint)
                }
                KritaBrushEngineKind.BRISTLE_DRY, KritaBrushEngineKind.BRISTLE_GLAZE -> {
                    for (k in -2..2) {
                        val offX = k * (rad * 0.34f)
                        fillPaint.alpha = (effectiveOrigAlpha * (if (k % 2 == 0) 0.9f else 0.65f)).toInt().coerceIn(8, 255)
                        canvas.drawCircle(p.x + offX, p.y, rad * 0.32f, fillPaint)
                    }
                }
            }
            return
        }

        // Multi-point smoothed Bezier curve rendering (anti-aliased silky strokes identical across Host & Collaborators)
        val smCount = smoothPointsQuadratic(points, scratch, maxSubdivisionsPerSeg = 6)
        val sm = scratch.smoothXYZ

        when (engine) {
            KritaBrushEngineKind.PIXEL_ROUND_SIZE -> { // Krita Basic-5 Size
                val softnessRatio = (1f - params.hardness).coerceIn(0f, 0.95f)
                val coreAlpha = effectiveOrigAlpha
                if (softnessRatio > 0.15f) {
                    val haloPaint = scratch.haloPaint.apply {
                        color = effColor
                        alpha = (effectiveOrigAlpha * 0.28f * softnessRatio).toInt().coerceIn(4, 255)
                        colorFilter = colorFilterOverride
                    }
                    for (i in 1 until smCount) {
                        val b0 = (i - 1) * 3; val b1 = i * 3
                        val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.15f, 1.05f)
                        haloPaint.strokeWidth = max(1.4f, baseWidth * avgPress * (1f + softnessRatio * 0.45f))
                        canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], haloPaint)
                    }
                }
                paint.alpha = coreAlpha
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3
                    val b1 = i * 3
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.15f, 1.05f)
                    val prog = i.toFloat() / max(1, smCount - 1)
                    val startTaper = if (params.taperStrength > 0.05f && prog < 0.10f) {
                        (1f - (1f - prog / 0.10f) * 0.65f * params.taperStrength).coerceIn(0.25f, 1f)
                    } else 1f
                    val endTaper = if (params.taperStrength > 0.05f && prog > 0.90f) {
                        (1f - ((prog - 0.90f) / 0.10f) * 0.65f * params.taperStrength).coerceIn(0.25f, 1f)
                    } else 1f
                    paint.strokeWidth = max(0.9f, baseWidth * avgPress * startTaper * endTaper)
                    canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], paint)
                }
            }

            KritaBrushEngineKind.PIXEL_OPACITY -> { // Krita Basic-2 Opacity
                val corePaint = scratch.corePaint.apply {
                    color = effColor
                    colorFilter = colorFilterOverride
                }
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3
                    val b1 = i * 3
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.15f, 1.05f)
                    corePaint.alpha = (effectiveOrigAlpha * (0.28f + 0.72f * avgPress * params.hardness)).toInt().coerceIn(8, 255)
                    corePaint.strokeWidth = max(1.2f, baseWidth * (0.72f + 0.28f * avgPress))
                    canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], corePaint)
                }
            }

            KritaBrushEngineKind.INK_GPEN -> { // Krita Ink-3 G-Pen (Silky comic/anime calligraphy)
                paint.alpha = effectiveOrigAlpha
                val taperMult = params.taperStrength.coerceIn(0.05f, 1.0f)
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3
                    val b1 = i * 3
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.12f, 1.05f)
                    val progress = i.toFloat() / max(1, smCount - 1)
                    var taper = 1.0f
                    if (progress < 0.09f) {
                        val rawT = (progress / 0.09f).coerceIn(0.15f, 1f)
                        taper = 1f - (1f - rawT) * taperMult
                    }
                    if (progress > 0.88f) {
                        val rawT = ((1f - progress) / 0.12f).coerceIn(0.12f, 1f)
                        taper = min(taper, 1f - (1f - rawT) * taperMult)
                    }
                    val pressCurve = avgPress * sqrt(avgPress.coerceAtLeast(0.01f))
                    paint.strokeWidth = max(0.75f, baseWidth * pressCurve * 1.05f * taper)
                    canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], paint)
                }
            }

            KritaBrushEngineKind.INK_ROUGH -> { // Krita Ink-4 Pen Rough
                paint.alpha = effectiveOrigAlpha
                var ptCount = 0
                val stepDiv = 2.8f
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3
                    val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.15f, 1.05f)
                    val w = max(1.1f, baseWidth * avgPress)
                    paint.strokeWidth = w * 0.88f
                    canvas.drawLine(x0, y0, x1, y1, paint)

                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val steps = max(1, (dist / stepDiv).toInt()).coerceAtMost(10)
                    val invDist = if (dist > 0.001f) 1f / dist else 0f
                    val nx = -dy * invDist; val ny = dx * invDist
                    val buf = scratch.ensureCapacity((ptCount + steps + 1) * 2)
                    val invS = 1f / steps
                    for (s in 0..steps) {
                        val t = s * invS
                        val cx = x0 + dx * t; val cy = y0 + dy * t
                        val jitter = (fastHashNoise(cx, cy, s * 13) - 0.5f) * w * 0.92f
                        buf[ptCount * 2] = cx + nx * jitter
                        buf[ptCount * 2 + 1] = cy + ny * jitter
                        ptCount++
                    }
                }
                if (ptCount > 0) {
                    ptPaint.alpha = (effectiveOrigAlpha * 0.82f).toInt().coerceIn(10, 255)
                    ptPaint.strokeWidth = max(1.4f, baseWidth * 0.25f)
                    canvas.drawPoints(scratch.ptsBuffer, 0, ptCount * 2, ptPaint)
                }
            }

            KritaBrushEngineKind.PENCIL_GRAPHITE -> { // Krita Pencil-2B
                paint.alpha = (effectiveOrigAlpha * 0.65f).toInt().coerceIn(8, 255)
                var pointCount = 0
                val stepDiv = if (densityMultiplier < 0.75f) 4.0f else 2.8f
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3
                    val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.15f, 1.05f)
                    val w = max(1.0f, baseWidth * 0.85f * avgPress)
                    paint.strokeWidth = w * 0.68f
                    canvas.drawLine(x0, y0, x1, y1, paint)

                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val steps = max(1, (dist / stepDiv).toInt()).coerceAtMost(12)
                    val invDist = if (dist > 0.001f) 1f / dist else 0f
                    val normalX = -dy * invDist; val normalY = dx * invDist
                    val buf = scratch.ensureCapacity((pointCount + steps + 1) * 2)
                    val invSteps = 1f / steps
                    for (s in 0..steps) {
                        val t = s * invSteps
                        val cx = x0 + dx * t; val cy = y0 + dy * t
                        val seed = cx.toInt() * 10 + cy.toInt() * 5 + s
                        val noiseOffset = (fastHashNoise(cx, cy, seed) - 0.5f) * w * 1.15f
                        buf[pointCount * 2] = cx + normalX * noiseOffset
                        buf[pointCount * 2 + 1] = cy + normalY * noiseOffset
                        pointCount++
                    }
                }
                if (pointCount > 0) {
                    ptPaint.alpha = (effectiveOrigAlpha * 0.68f).toInt().coerceIn(10, 255)
                    ptPaint.strokeWidth = 2.0f
                    canvas.drawPoints(scratch.ptsBuffer, 0, pointCount * 2, ptPaint)
                }
            }

            KritaBrushEngineKind.CHARCOAL_SOFT -> { // Krita Charcoal Rock Soft
                paint.alpha = (effectiveOrigAlpha * 0.55f).toInt().coerceIn(8, 255)
                var ptCount = 0
                val stepDiv = if (densityMultiplier < 0.75f) 3.4f else 2.2f
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.2f, 1.05f)
                    val w = max(1.8f, baseWidth * avgPress)
                    paint.strokeWidth = w * 0.62f
                    canvas.drawLine(x0, y0, x1, y1, paint)

                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val steps = max(1, (dist / stepDiv).toInt()).coerceAtMost(12)
                    val invDist = if (dist > 0.001f) 1f / dist else 0f
                    val nx = -dy * invDist; val ny = dx * invDist
                    val specks = (5 * densityMultiplier).toInt().coerceIn(2, 6)
                    val buf = scratch.ensureCapacity((ptCount + (steps + 1) * specks) * 2)
                    val invS = 1f / steps
                    for (s in 0..steps) {
                        val t = s * invS
                        val cx = x0 + dx * t; val cy = y0 + dy * t
                        for (k in 0 until specks) {
                            val j = (fastHashNoise(cx, cy, s * 17 + k) - 0.5f) * w * 1.25f
                            val f = (fastHashNoise(cx, cy, s * 31 + k) - 0.5f) * 2.5f
                            buf[ptCount * 2] = cx + nx * j + f
                            buf[ptCount * 2 + 1] = cy + ny * j + f
                            ptCount++
                        }
                    }
                }
                if (ptCount > 0) {
                    ptPaint.alpha = (effectiveOrigAlpha * 0.78f).toInt().coerceIn(10, 255)
                    ptPaint.strokeWidth = 2.6f
                    canvas.drawPoints(scratch.ptsBuffer, 0, ptCount * 2, ptPaint)
                }
            }

            KritaBrushEngineKind.MARKER_CHISEL -> { // Krita Marker Chisel
                fillPaint.alpha = (effectiveOrigAlpha * 0.62f).toInt().coerceIn(8, 255)
                val quadPath = scratch.path
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.25f, 1.05f)
                    val halfW = max(2.0f, baseWidth * 0.72f * avgPress)
                    val offX = COS_45 * halfW
                    val offY = SIN_45 * halfW
                    quadPath.reset()
                    quadPath.moveTo(sm[b0] - offX, sm[b0 + 1] - offY)
                    quadPath.lineTo(sm[b0] + offX, sm[b0 + 1] + offY)
                    quadPath.lineTo(sm[b1] + offX, sm[b1 + 1] + offY)
                    quadPath.lineTo(sm[b1] - offX, sm[b1 + 1] - offY)
                    quadPath.close()
                    canvas.drawPath(quadPath, fillPaint)
                }
            }

            KritaBrushEngineKind.BRISTLE_DRY -> { // Krita Dry Bristles
                val numBristles = params.bristleCount.coerceIn(3, 9)
                val bristlePaints = scratch.bristlePaints
                val dimAlpha = (effectiveOrigAlpha * (0.68f - params.grainAmount * 0.22f)).toInt().coerceIn(8, 255)
                for (k in 0 until numBristles) {
                    bristlePaints[k].color = if (k % 2 == 0) (effectiveOrigAlpha shl 24) or rgb else (dimAlpha shl 24) or rgb
                    bristlePaints[k].colorFilter = colorFilterOverride
                }
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.25f, 1.05f)
                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val invDist = if (dist > 0.001f) 1f / dist else 0f
                    val normX = -dy * invDist; val normY = dx * invDist
                    val totalSpread = baseWidth * (0.66f + params.spacing * 0.4f) * avgPress
                    val bWidth = max(1.1f, (totalSpread / numBristles) * 1.15f)
                    val denom = max(1, numBristles - 1).toFloat()
                    for (b in 0 until numBristles) {
                        val fraction = (b / denom) - 0.5f
                        val offset = fraction * totalSpread
                        val bp = bristlePaints[b]
                        bp.strokeWidth = bWidth
                        canvas.drawLine(
                            x0 + normX * offset, y0 + normY * offset,
                            x1 + normX * offset, y1 + normY * offset, bp
                        )
                    }
                }
            }

            KritaBrushEngineKind.BRISTLE_GLAZE -> { // Krita Bristles Glaze (Layered translucent flat)
                fillPaint.alpha = (effectiveOrigAlpha * 0.78f).toInt().coerceIn(8, 255)
                val flatQuad = scratch.path
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.25f, 1.05f)
                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val invDist = if (dist > 0.001f) 1f / dist else 0f
                    val normX = -dy * invDist; val normY = dx * invDist
                    val halfW = max(1.8f, baseWidth * 0.58f * avgPress)
                    val offX = normX * halfW; val offY = normY * halfW
                    flatQuad.reset()
                    flatQuad.moveTo(x0 - offX, y0 - offY)
                    flatQuad.lineTo(x0 + offX, y0 + offY)
                    flatQuad.lineTo(x1 + offX, y1 + offY)
                    flatQuad.lineTo(x1 - offX, y1 - offY)
                    flatQuad.close()
                    canvas.drawPath(flatQuad, fillPaint)
                }
            }

            KritaBrushEngineKind.CHALK_GRAINY -> { // Krita Chalk Grainy
                var ptCount = 0
                val stepDiv = if (densityMultiplier < 0.75f) 3.4f else 2.4f
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.25f, 1.05f)
                    val w = max(2.0f, baseWidth * 0.90f * avgPress)
                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val steps = max(1, (dist / stepDiv).toInt()).coerceAtMost(12)
                    val invDist = if (dist > 0.001f) 1f / dist else 0f
                    val normX = -dy * invDist; val normY = dx * invDist
                    val numSpecks = ((w * 0.65f * densityMultiplier).toInt()).coerceIn(2, 11)
                    val targetBuf = scratch.ensureCapacity((ptCount + (steps + 1) * numSpecks) * 2)
                    val invSteps = 1f / steps
                    for (s in 0..steps) {
                        val t = s * invSteps
                        val cx = x0 + dx * t; val cy = y0 + dy * t
                        val seed = cx.toInt() * 11 + cy.toInt() * 17 + s
                        for (k in 0 until numSpecks) {
                            if (fastHashNoise(cx, cy, seed + k * 5) > 0.84f) continue
                            val jitterNorm = (fastHashNoise(cx, cy, seed + k * 3) - 0.5f) * w * 1.32f
                            targetBuf[ptCount * 2] = cx + normX * jitterNorm
                            targetBuf[ptCount * 2 + 1] = cy + normY * jitterNorm
                            ptCount++
                        }
                    }
                }
                if (ptCount > 0) {
                    ptPaint.alpha = (effectiveOrigAlpha * 0.76f).toInt().coerceIn(10, 255)
                    ptPaint.strokeWidth = 2.8f
                    canvas.drawPoints(scratch.ptsBuffer, 0, ptCount * 2, ptPaint)
                }
            }

            KritaBrushEngineKind.AIRBRUSH_SOFT -> { // Krita Airbrush Soft
                val dropletsPerStep = (15 * densityMultiplier).toInt().coerceIn(5, 16)
                val minStepDist = max(3f, baseWidth * 0.38f)
                var ptCount = 0
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val x0 = sm[b0]; val y0 = sm[b0 + 1]
                    val x1 = sm[b1]; val y1 = sm[b1 + 1]
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.2f, 1.05f)
                    val sprayRad = max(4f, baseWidth * 1.45f * avgPress)
                    val dx = x1 - x0; val dy = y1 - y0
                    val dist = sqrt(dx * dx + dy * dy)
                    val steps = max(1, (dist / minStepDist).toInt()).coerceAtMost(12)
                    val invSteps = 1f / steps
                    val targetBuf = scratch.ensureCapacity((ptCount + (steps + 1) * dropletsPerStep) * 2)
                    for (s in 0..steps) {
                        val t = s * invSteps
                        val cx = x0 + dx * t; val cy = y0 + dy * t
                        val baseSeed = cx.toInt() * 7 + cy.toInt() * 13 + s * 16
                        for (d in 0 until dropletsPerStep) {
                            val seed = baseSeed + d
                            val lutIdx = (fastHashNoise(cx, cy, seed) * LUT_SIZE).toInt() and LUT_MASK
                            val r = sprayRad * sqrt(fastHashNoise(cx, cy, seed + 10))
                            targetBuf[ptCount * 2] = cx + COS_LUT[lutIdx] * r
                            targetBuf[ptCount * 2 + 1] = cy + SIN_LUT[lutIdx] * r
                            ptCount++
                        }
                    }
                }
                if (ptCount > 0) {
                    ptPaint.alpha = (effectiveOrigAlpha * 0.35f).toInt().coerceIn(8, 255)
                    ptPaint.strokeWidth = 2.4f
                    canvas.drawPoints(scratch.ptsBuffer, 0, ptCount * 2, ptPaint)
                }
            }

            KritaBrushEngineKind.BLENDER_WET_SOFT -> { // Krita Blender / Wet Soft
                val corePaint = scratch.corePaint.apply {
                    color = effColor
                    alpha = (effectiveOrigAlpha * 0.72f).toInt().coerceIn(8, 255)
                    colorFilter = colorFilterOverride
                }
                val haloPaint = scratch.haloPaint.apply {
                    color = effColor
                    alpha = (effectiveOrigAlpha * 0.24f).toInt().coerceIn(6, 255)
                    colorFilter = colorFilterOverride
                }
                for (i in 1 until smCount) {
                    val b0 = (i - 1) * 3; val b1 = i * 3
                    val avgPress = ((sm[b0 + 2] + sm[b1 + 2]) * 0.5f).coerceIn(0.2f, 1.05f)
                    val w = max(1.5f, baseWidth * avgPress)
                    haloPaint.strokeWidth = w * 1.48f
                    canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], haloPaint)
                    corePaint.strokeWidth = w * 0.78f
                    canvas.drawLine(sm[b0], sm[b0 + 1], sm[b1], sm[b1 + 1], corePaint)
                }
            }
        }
    }

    /**
     * Generates a PorterDuff color filter for onion skinning (camada cebola).
     */
    fun createOnionSkinColorFilter(tintColorLong: Long): ColorFilter {
        return PorterDuffColorFilter(tintColorLong.toInt(), PorterDuff.Mode.SRC_IN)
    }

    /**
     * 4-way flood fill using zero-boxing PrimitiveIntQueue.
     */
    fun floodFill(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        fillColor: Int,
        tolerance: Int = 10
    ): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (startX !in 0 until width || startY !in 0 until height) return false

        val targetColor = bitmap.getPixel(startX, startY)
        if (colorMatch(targetColor, fillColor, 0)) return false

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val queue = PrimitiveIntQueue(4096)
        val visited = BooleanArray(width * height)

        val startIdx = startY * width + startX
        visited[startIdx] = true
        queue.add(startX or (startY shl 16))

        while (!queue.isEmpty) {
            val packed = queue.removeFirst()
            val x = packed and 0xFFFF
            val y = packed ushr 16
            val index = y * width + x

            if (colorMatch(pixels[index], targetColor, tolerance)) {
                pixels[index] = fillColor

                if (x > 0) {
                    val n = index - 1
                    if (!visited[n]) { visited[n] = true; queue.add((x - 1) or (y shl 16)) }
                }
                if (x < width - 1) {
                    val n = index + 1
                    if (!visited[n]) { visited[n] = true; queue.add((x + 1) or (y shl 16)) }
                }
                if (y > 0) {
                    val n = index - width
                    if (!visited[n]) { visited[n] = true; queue.add(x or ((y - 1) shl 16)) }
                }
                if (y < height - 1) {
                    val n = index + width
                    if (!visited[n]) { visited[n] = true; queue.add(x or ((y + 1) shl 16)) }
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return true
    }

    private fun colorMatch(c1: Int, c2: Int, tolerance: Int): Boolean {
        if (c1 == c2) return true
        if (tolerance == 0) return false

        val a1 = (c1 ushr 24) and 0xFF
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val a2 = (c2 ushr 24) and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF

        return abs(a1 - a2) <= tolerance &&
            abs(r1 - r2) <= tolerance &&
            abs(g1 - g2) <= tolerance &&
            abs(b1 - b2) <= tolerance
    }

    /**
     * Executes flood fill with zero-boxing PrimitiveIntQueue and returns horizontal stroke spans.
     * When [selectionPoly] is provided, strictly confines flood fill inside the selection polygon.
     */
    fun createFloodFillSpans(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int = 20,
        selectionPoly: List<StrokePoint>? = null
    ): List<StrokePoint> {
        val width = bitmap.width
        val height = bitmap.height
        if (startX !in 0 until width || startY !in 0 until height) return emptyList()
        val hasSelectionClip = selectionPoly != null && selectionPoly.size >= 3
        if (hasSelectionClip && !SelectionGeometryHelper.isPointInPolygon(startX.toFloat(), startY.toFloat(), selectionPoly!!)) {
            return emptyList()
        }

        val targetColor = bitmap.getPixel(startX, startY)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val visited = BooleanArray(width * height)
        val filledMask = BooleanArray(width * height)
        val queue = PrimitiveIntQueue(4096)

        val startIdx = startY * width + startX
        visited[startIdx] = true
        queue.add(startX or (startY shl 16))
        var minY = startY
        var maxY = startY

        fun canVisitPixel(px: Int, py: Int, nIdx: Int): Boolean {
            if (visited[nIdx]) return false
            if (hasSelectionClip && !SelectionGeometryHelper.isPointInPolygon(px.toFloat(), py.toFloat(), selectionPoly!!)) {
                return false
            }
            return true
        }

        while (!queue.isEmpty) {
            val packed = queue.removeFirst()
            val x = packed and 0xFFFF
            val y = packed ushr 16
            val idx = y * width + x

            if (colorMatch(pixels[idx], targetColor, tolerance)) {
                filledMask[idx] = true
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) {
                    val n = idx - 1
                    if (canVisitPixel(x - 1, y, n)) { visited[n] = true; queue.add((x - 1) or (y shl 16)) }
                }
                if (x < width - 1) {
                    val n = idx + 1
                    if (canVisitPixel(x + 1, y, n)) { visited[n] = true; queue.add((x + 1) or (y shl 16)) }
                }
                if (y > 0) {
                    val n = idx - width
                    if (canVisitPixel(x, y - 1, n)) { visited[n] = true; queue.add(x or ((y - 1) shl 16)) }
                }
                if (y < height - 1) {
                    val n = idx + width
                    if (canVisitPixel(x, y + 1, n)) { visited[n] = true; queue.add(x or ((y + 1) shl 16)) }
                }
            }
        }

        val spans = ArrayList<StrokePoint>(256)
        for (y in minY..maxY) {
            var inSpan = false
            var spanStart = 0
            val rowOffset = y * width

            for (x in 0 until width) {
                val isFilled = filledMask[rowOffset + x]
                if (isFilled && !inSpan) {
                    inSpan = true
                    spanStart = x
                } else if (!isFilled && inSpan) {
                    inSpan = false
                    spans.add(StrokePoint(spanStart.toFloat(), y.toFloat()))
                    spans.add(StrokePoint((x - 1).toFloat(), y.toFloat()))
                }
            }
            if (inSpan) {
                spans.add(StrokePoint(spanStart.toFloat(), y.toFloat()))
                spans.add(StrokePoint((width - 1).toFloat(), y.toFloat()))
            }
        }
        return spans
    }

    data class MagicWandResult(
        val boundary: List<StrokePoint>,
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    )

    /**
     * Ordered 4-connected crack/corner perimeter extractor for Magic Wand.
     * Builds a contiguous mask via BFS and walks its outer perimeter in order,
     * collapsing collinear segments so no jagged multi-branch zigzags occur.
     */
    fun createMagicWandRegion(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int = 24
    ): MagicWandResult {
        val width = bitmap.width
        val height = bitmap.height
        if (startX !in 0 until width || startY !in 0 until height) {
            return MagicWandResult(emptyList(), 0f, 0f, 0f, 0f)
        }

        val targetColor = bitmap.getPixel(startX, startY)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val visited = BooleanArray(width * height)
        val mask = BooleanArray(width * height)
        val queue = PrimitiveIntQueue(4096)

        val startIdx = startY * width + startX
        visited[startIdx] = true
        queue.add(startX or (startY shl 16))

        var minPxX = width
        var minPxY = height
        var maxPxX = -1
        var maxPxY = -1
        var firstSeedX = -1
        var firstSeedY = -1

        while (!queue.isEmpty) {
            val packed = queue.removeFirst()
            val x = packed and 0xFFFF
            val y = packed ushr 16
            val idx = y * width + x

            if (colorMatch(pixels[idx], targetColor, tolerance)) {
                mask[idx] = true
                if (x < minPxX) minPxX = x
                if (y < minPxY) minPxY = y
                if (x > maxPxX) maxPxX = x
                if (y > maxPxY) maxPxY = y
                if (firstSeedY == -1 || y < firstSeedY || (y == firstSeedY && x < firstSeedX)) {
                    firstSeedX = x
                    firstSeedY = y
                }

                if (x > 0) {
                    val n = idx - 1
                    if (!visited[n]) { visited[n] = true; queue.add((x - 1) or (y shl 16)) }
                }
                if (x < width - 1) {
                    val n = idx + 1
                    if (!visited[n]) { visited[n] = true; queue.add((x + 1) or (y shl 16)) }
                }
                if (y > 0) {
                    val n = idx - width
                    if (!visited[n]) { visited[n] = true; queue.add(x or ((y - 1) shl 16)) }
                }
                if (y < height - 1) {
                    val n = idx + width
                    if (!visited[n]) { visited[n] = true; queue.add(x or ((y + 1) shl 16)) }
                }
            }
        }

        if (firstSeedX < 0 || maxPxX < minPxX || maxPxY < minPxY) {
            return MagicWandResult(emptyList(), 0f, 0f, 0f, 0f)
        }

        fun inMask(px: Int, py: Int): Boolean {
            if (px !in 0 until width || py !in 0 until height) return false
            return mask[py * width + px]
        }

        // Crack/vertex clockwise perimeter walker with object kept on the right of directed edge:
        // dir: 0=East(+x), 1=South(+y), 2=West(-x), 3=North(-y)
        val rawVerts = ArrayList<StrokePoint>(256)
        val startVx = firstSeedX
        val startVy = firstSeedY
        var vx = startVx
        var vy = startVy
        var dir = 0
        rawVerts.add(StrokePoint(vx.toFloat(), vy.toFloat()))

        val maxSteps = max(64, (maxPxX - minPxX + 2) * (maxPxY - minPxY + 2) * 2)
        var steps = 0
        while (steps < maxSteps) {
            steps++
            when (dir) {
                0 -> vx += 1
                1 -> vy += 1
                2 -> vx -= 1
                3 -> vy -= 1
            }

            if (vx == startVx && vy == startVy) {
                break
            }

            // Pick next direction maintaining interior pixel on the right:
            // Try Right -> Straight -> Left -> U-turn
            val frontRight = when (dir) {
                0 -> inMask(vx, vy)
                1 -> inMask(vx - 1, vy)
                2 -> inMask(vx - 1, vy - 1)
                else -> inMask(vx, vy - 1)
            }
            val frontLeft = when (dir) {
                0 -> inMask(vx, vy - 1)
                1 -> inMask(vx, vy)
                2 -> inMask(vx - 1, vy)
                else -> inMask(vx - 1, vy - 1)
            }

            val nextDir = when {
                !frontRight && !frontLeft -> (dir + 1) and 3 // turn right around convex corner
                frontRight && !frontLeft -> dir              // continue straight along outer edge
                frontLeft -> (dir + 3) and 3                 // turn left around concave corner
                else -> (dir + 2) and 3
            }

            if (nextDir != dir) {
                rawVerts.add(StrokePoint(vx.toFloat(), vy.toFloat()))
                dir = nextDir
            }
        }

        // Collapse strictly collinear vertices (if any) and close the loop cleanly
        val simplified = ArrayList<StrokePoint>(rawVerts.size + 1)
        for (pt in rawVerts) {
            if (simplified.size >= 2) {
                val a = simplified[simplified.size - 2]
                val b = simplified[simplified.size - 1]
                val cross = (b.x - a.x) * (pt.y - b.y) - (b.y - a.y) * (pt.x - b.x)
                if (abs(cross) < 0.01f) {
                    simplified[simplified.size - 1] = pt
                    continue
                }
            }
            simplified.add(pt)
        }
        if (simplified.isNotEmpty()) {
            val first = simplified[0]
            val last = simplified.last()
            if (abs(first.x - last.x) > 0.01f || abs(first.y - last.y) > 0.01f) {
                simplified.add(first)
            }
        }

        return MagicWandResult(
            boundary = simplified,
            minX = minPxX.toFloat(),
            minY = minPxY.toFloat(),
            maxX = (maxPxX + 1).toFloat(),
            maxY = (maxPxY + 1).toFloat()
        )
    }

    /**
     * Magic Wand boundary extractor delegating to ordered 4-connected contour tracer.
     */
    fun createMagicWandBoundary(
        bitmap: Bitmap,
        startX: Int,
        startY: Int,
        tolerance: Int = 20
    ): List<StrokePoint> {
        return createMagicWandRegion(bitmap, startX, startY, tolerance).boundary
    }

    /**
     * Builds a Sprite Sheet containing all frames using a pooled scratch bitmap (minimal memory churn).
     */
    fun generateSpriteSheet(
        frames: List<Frame>,
        frameWidth: Int,
        frameHeight: Int,
        columns: Int = 4
    ): Bitmap {
        val totalFrames = max(1, frames.size)
        val cols = min(columns, totalFrames)
        val rows = (totalFrames + cols - 1) / cols

        val sheetBitmap = Bitmap.createBitmap(
            frameWidth * cols,
            frameHeight * rows,
            Bitmap.Config.ARGB_8888
        )
        val sheetCanvas = Canvas(sheetBitmap)
        sheetCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scratchBitmap = KodanimaBitmapPool.acquire(
            frameWidth,
            frameHeight,
            Bitmap.Config.ARGB_8888,
            android.graphics.Color.TRANSPARENT
        )
        try {
            for (i in frames.indices) {
                val col = i % cols
                val row = i / cols
                drawFrameToBitmap(frames[i], scratchBitmap, android.graphics.Color.TRANSPARENT, clearBitmap = true)
                sheetCanvas.drawBitmap(scratchBitmap, (col * frameWidth).toFloat(), (row * frameHeight).toFloat(), null)
            }
        } finally {
            KodanimaBitmapPool.release(scratchBitmap)
        }

        return sheetBitmap
    }
}
