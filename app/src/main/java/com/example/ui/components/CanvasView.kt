package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Camera3DEngine
import com.example.engine.CanvasTileEngine
import com.example.engine.CursorBitmapWithHotspot
import com.example.engine.CursorHelper
import com.example.engine.HardwareCapabilitiesEngine
import com.example.engine.KodanimaBitmapPool
import com.example.engine.KodanimaDispatchers
import com.example.engine.KodanimaGraphicsEngine
import com.example.engine.MediaAssetManager
import com.example.engine.NativeGL3DEngine
import com.example.engine.PredictiveViewportTracker
import com.example.engine.Studio3DPaintEngine
import com.example.engine.ProjectedObjectBounds
import com.example.engine.SelectionGeometryHelper
import com.example.engine.TilePlan
import com.example.engine.TileSliceSpec
import com.example.engine.TimelineTrackEngine
import com.example.model.AnimationProject
import com.example.model.BezierNode
import com.example.model.Camera3DKeyframe
import com.example.model.Camera3DSettings
import com.example.model.CameraMode
import com.example.model.CameraToolAction
import com.example.model.CameraViewportNavTarget
import com.example.model.Canvas3DBackgroundRenderMode
import com.example.model.CanvasBackgroundSettings
import com.example.model.CanvasBackgroundStyle
import com.example.model.DrawingStroke
import com.example.model.GridRulerSettings
import com.example.model.KodanimaTheme
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.model.MultiFingerGesture
import com.example.model.OnionSkinSettings
import com.example.model.PerspectiveRulerSettings
import com.example.model.PressureCurve
import com.example.model.PressureMode
import com.example.model.ReferenceImageState
import com.example.model.SceneObject3D
import com.example.model.SelectionRegion
import com.example.model.SkyBackdropMode
import com.example.model.StageSelection
import com.example.model.StageShadingMode
import com.example.model.StageTargetType
import com.example.model.StrokePoint
import com.example.model.TabletDriverSettings
import com.example.model.ToolType
import com.example.model.sampleCubicBezierNodesToStrokePoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Holds pooled offscreen GPU/RAM bitmaps for active incremental stroke/eraser buffers,
 * Stage 3D layer cards, and Canvas 3D backdrops while world-space tile caching is driven by [CanvasTileEngine].
 */
private class CanvasBitmapCache {
    data class Stage3DObjectCacheEntry(
        val bitmap: Bitmap,
        val bounds: ProjectedObjectBounds?,
        val key: Any?
    )

    var cacheW: Int = 0
    var cacheH: Int = 0

    // Viewport-sized active stroke / eraser buffers with 40px predictive overscan
    var vpW: Int = 0
    var vpH: Int = 0
    var vpScale: Float = 1f
    var vpMarginPx: Int = 0
    val activeSessionMatrix: AndroidMatrix = AndroidMatrix()
    var activeSessionVpScale: Float = 1f
    var activeSessionMarginPx: Int = 0

    var eraserWorkBitmap: Bitmap? = null
    var eraserWorkCanvas: AndroidCanvas? = null
    var lastErasedPointIdx: Int = 0

    var activeStrokeBitmap: Bitmap? = null
    var activeStrokeCanvas: AndroidCanvas? = null
    var lastRenderedStrokeIdx: Int = 0

    // Per-layer cached transparent bitmaps for real 3D Stage viewport
    val stage3DLayerBitmaps: MutableMap<String, Bitmap> = mutableMapOf()
    val stage3DLayerKeys: MutableMap<String, LayerSliceCacheKey> = mutableMapOf()
    val stage3DOffCanvasBitmaps: MutableMap<String, Bitmap> = mutableMapOf()
    val stage3DOffCanvasKeys: MutableMap<String, LayerSliceCacheKey> = mutableMapOf()
    val stage3DObjectEntries: MutableMap<String, Stage3DObjectCacheEntry> = mutableMapOf()
    var stage3DBgBitmap: Bitmap? = null
    var stage3DBgKey: Any? = null
    var stage3DPovBitmap: Bitmap? = null
    var stage3DPovKey: Any? = null

    // "Mostrar 3D no Canvas" cached background bitmaps (Static 2D, Play pre-render frames, Real-time 3D)
    var canvas3DBgStaticBitmap: Bitmap? = null
    var canvas3DBgStaticKey: Any? = null
    val canvas3DBgPlayFrames: LinkedHashMap<Int, Bitmap> = LinkedHashMap()
    var canvas3DBgPlayKey: Any? = null
    var canvas3DBgRealtimeBitmap: Bitmap? = null
    var canvas3DBgRealtimeKey: Any? = null

    // Full-screen off-canvas 3D scene cache (matches active camera view outside paper bounds)
    var offCanvas3DBitmap: Bitmap? = null
    var offCanvas3DKey: Any? = null

    fun ensureSize(w: Int, h: Int) {
        if (w == cacheW && h == cacheH) return
        releaseStage3D()
        releaseCanvas3DBg()
        cacheW = w
        cacheH = h
    }

    fun ensureViewportSize(w: Int, h: Int, scale: Float, marginPx: Int = 0) {
        vpScale = scale
        if (w == vpW && h == vpH && marginPx == vpMarginPx) return
        releaseViewportLayers()
        vpW = w
        vpH = h
        vpMarginPx = marginPx
    }

    fun acquireOrReuseViewport(current: Bitmap?, clearColor: Int): Bitmap {
        val safeW = vpW.coerceAtLeast(1)
        val safeH = vpH.coerceAtLeast(1)
        if (current != null && !current.isRecycled && current.width == safeW && current.height == safeH) {
            current.eraseColor(clearColor)
            return current
        }
        KodanimaBitmapPool.release(current)
        return KodanimaBitmapPool.acquire(safeW, safeH, Bitmap.Config.ARGB_8888, clearColor)
    }

    fun beginEraserSession(
        sessionMatrix: AndroidMatrix,
        sessionVpScale: Float,
        sessionMarginPx: Int = vpMarginPx,
        activeLayers: List<Layer>,
        projWidth: Int,
        projHeight: Int,
        containerWidth: Float,
        containerHeight: Float,
        zoom: Float,
        activeSelection: SelectionRegion = SelectionRegion(),
        densityMult: Float = 1.0f
    ) {
        activeSessionMatrix.set(sessionMatrix)
        activeSessionVpScale = sessionVpScale
        activeSessionMarginPx = sessionMarginPx
        val dst = acquireOrReuseViewport(eraserWorkBitmap, AndroidColor.TRANSPARENT)
        eraserWorkBitmap = dst
        val c = eraserWorkCanvas ?: AndroidCanvas(dst)
        c.setBitmap(dst)
        eraserWorkCanvas = c

        val overscanScreenPx = (sessionMarginPx / sessionVpScale.coerceAtLeast(0.01f))
            .coerceAtLeast(PredictiveViewportTracker.MAX_PREDICTIVE_OVERSCAN_PX)
        val visibleBounds = KodanimaGraphicsEngine.computeOverscannedVisibleProjectBounds(
            projToScreenMatrix = sessionMatrix,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            screenOverscanPx = overscanScreenPx,
            zoom = zoom.coerceAtLeast(0.05f)
        )
        val vpMat = AndroidMatrix(sessionMatrix).apply {
            postScale(sessionVpScale, sessionVpScale)
            postTranslate(sessionMarginPx.toFloat(), sessionMarginPx.toFloat())
        }
        if (activeLayers.isNotEmpty() && activeLayers[0].isVisible) {
            c.save()
            c.concat(vpMat)
            KodanimaGraphicsEngine.drawLayerToCanvas(
                layer = activeLayers[0],
                canvas = c,
                targetWidth = projWidth,
                targetHeight = projHeight,
                alphaMultiplier = 1.0f,
                colorFilterOverride = null,
                visibleBounds = visibleBounds,
                extraStrokes = null,
                densityMultiplier = densityMult,
                allowOutOfBounds = true
            )
            c.restore()
        }
        if (activeSelection.isActive && activeSelection.capturedStrokes.isNotEmpty()) {
            c.save()
            c.concat(vpMat)
            c.translate(activeSelection.offsetX, activeSelection.offsetY)
            if (activeSelection.rotationDegrees != 0f) {
                c.rotate(activeSelection.rotationDegrees, activeSelection.centerX, activeSelection.centerY)
            }
            if (activeSelection.scaleX != 1f || activeSelection.scaleY != 1f) {
                c.scale(activeSelection.scaleX, activeSelection.scaleY, activeSelection.centerX, activeSelection.centerY)
            }
            for (cStroke in activeSelection.capturedStrokes) {
                KodanimaGraphicsEngine.drawStrokeToCanvas(cStroke, c, 1.0f, null, densityMult)
            }
            c.restore()
        }
        lastErasedPointIdx = 0
    }

    fun beginActiveStrokeSession(
        sessionMatrix: AndroidMatrix,
        sessionVpScale: Float,
        sessionMarginPx: Int = vpMarginPx
    ) {
        activeSessionMatrix.set(sessionMatrix)
        activeSessionVpScale = sessionVpScale
        activeSessionMarginPx = sessionMarginPx
        val dst = acquireOrReuseViewport(activeStrokeBitmap, AndroidColor.TRANSPARENT)
        activeStrokeBitmap = dst
        val c = activeStrokeCanvas ?: AndroidCanvas(dst)
        c.setBitmap(dst)
        activeStrokeCanvas = c
        lastRenderedStrokeIdx = 0
    }

    fun endActiveSessions() {
        lastErasedPointIdx = 0
        lastRenderedStrokeIdx = 0
        KodanimaBitmapPool.release(eraserWorkBitmap)
        eraserWorkBitmap = null
        eraserWorkCanvas = null
        KodanimaBitmapPool.release(activeStrokeBitmap)
        activeStrokeBitmap = null
        activeStrokeCanvas = null
    }

    fun releaseStage3D() {
        stage3DLayerBitmaps.values.forEach { KodanimaBitmapPool.release(it) }
        stage3DLayerBitmaps.clear()
        stage3DLayerKeys.clear()
        stage3DOffCanvasBitmaps.values.forEach { KodanimaBitmapPool.release(it) }
        stage3DOffCanvasBitmaps.clear()
        stage3DOffCanvasKeys.clear()
        stage3DObjectEntries.values.forEach { KodanimaBitmapPool.release(it.bitmap) }
        stage3DObjectEntries.clear()
        KodanimaBitmapPool.release(stage3DBgBitmap)
        stage3DBgBitmap = null
        stage3DBgKey = null
        KodanimaBitmapPool.release(stage3DPovBitmap)
        stage3DPovBitmap = null
        stage3DPovKey = null
    }

    fun releaseOffCanvas3D() {
        KodanimaBitmapPool.release(offCanvas3DBitmap)
        offCanvas3DBitmap = null
        offCanvas3DKey = null
    }

    fun releaseCanvas3DBg() {
        KodanimaBitmapPool.release(canvas3DBgStaticBitmap)
        canvas3DBgStaticBitmap = null
        canvas3DBgStaticKey = null
        canvas3DBgPlayFrames.values.forEach { KodanimaBitmapPool.release(it) }
        canvas3DBgPlayFrames.clear()
        canvas3DBgPlayKey = null
        KodanimaBitmapPool.release(canvas3DBgRealtimeBitmap)
        canvas3DBgRealtimeBitmap = null
        canvas3DBgRealtimeKey = null
    }

    fun releaseViewportLayers() {
        endActiveSessions()
        vpW = 0
        vpH = 0
    }

    fun releaseAll() {
        releaseViewportLayers()
        releaseStage3D()
        releaseCanvas3DBg()
        releaseOffCanvas3D()
        cacheW = 0
        cacheH = 0
    }
}

private class LiveViewportTransform(
    var zoom: Float = 1f,
    var panX: Float = 0f,
    var panY: Float = 0f,
    var rotation: Float = 0f
)

private data class LayerSliceCacheKey(
    val layers: List<Layer>,
    val w: Int,
    val h: Int,
    val densityMult: Float,
    val hiddenStrokeIds: Set<String> = emptySet(),
    val replacementStrokes: List<DrawingStroke> = emptyList(),
    val vpMatrixSignature: List<Float> = emptyList()
)

private data class OptimisticActiveFallback(
    val layerId: String?,
    val fallbackHashes: List<Int>,
    val strokeId: String,
    val seenInVm: Boolean = false
)

private class CanvasOverlayScratch {
    val srcRect = Rect()
    val dstRectF = RectF()
    val bitmapPaint = AndroidPaint(
        AndroidPaint.ANTI_ALIAS_FLAG or
            AndroidPaint.FILTER_BITMAP_FLAG or
            AndroidPaint.DITHER_FLAG
    )
    val eraserRingDarkPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(180, 0, 0, 0)
        style = AndroidPaint.Style.STROKE
        strokeWidth = 3.5f
    }
    val eraserRingLightPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.STROKE
        strokeWidth = 1.8f
    }
    val selPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.CYAN
        style = AndroidPaint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    val boxPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.STROKE
    }
    val handlePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL
    }
    val selPath = AndroidPath()
    val videoBitmapPaint = AndroidPaint(
        AndroidPaint.ANTI_ALIAS_FLAG or
            AndroidPaint.FILTER_BITMAP_FLAG or
            AndroidPaint.DITHER_FLAG
    )
    val videoBoxPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 138, 0)
        style = AndroidPaint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }
    val videoHandlePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 138, 0)
        style = AndroidPaint.Style.FILL
    }
    val bezierLinePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.CYAN
        style = AndroidPaint.Style.STROKE
    }
    val bezierHandleFillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.CYAN
        style = AndroidPaint.Style.FILL
    }
    val bezierAnchorFillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL
    }
    val bezierAnchorStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(0, 180, 255)
        style = AndroidPaint.Style.STROKE
    }
    val peerTextPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
    }
    val peerBgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
    }
    val srcCopyPaint = AndroidPaint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }
    val pixelGridPaint = AndroidPaint().apply {
        color = 0x3A7A869E
        style = AndroidPaint.Style.STROKE
        strokeWidth = 0f
        isAntiAlias = false
    }
    val gridRulerPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
    }
    val perspectiveRayPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
    }
    val perspectiveVpPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
    }
    val perspectiveVpStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.STROKE
    }
    val perspectiveLabelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
        textAlign = AndroidPaint.Align.CENTER
    }
    val whitePaperPaint = AndroidPaint().apply {
        color = AndroidColor.WHITE
        style = AndroidPaint.Style.FILL
    }
    val layerBlendPaint = AndroidPaint(
        AndroidPaint.ANTI_ALIAS_FLAG or
            AndroidPaint.FILTER_BITMAP_FLAG or
            AndroidPaint.DITHER_FLAG
    )
    val clipDstInPaint = AndroidPaint(
        AndroidPaint.ANTI_ALIAS_FLAG or
            AndroidPaint.FILTER_BITMAP_FLAG
    ).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    // Reusable checkerboard & post-effect / viewfinder paints (zero draw-frame allocations)
    val checkerLightPaint = AndroidPaint().apply {
        color = AndroidColor.rgb(245, 246, 250)
        style = AndroidPaint.Style.FILL
    }
    val checkerDarkPaint = AndroidPaint().apply {
        color = AndroidColor.rgb(212, 217, 228)
        style = AndroidPaint.Style.FILL
    }
    val fxSepiaPaint = AndroidPaint().apply { style = AndroidPaint.Style.FILL }
    val fxBloomPaint = AndroidPaint()
    var fxBloomKeyW = -1f
    var fxBloomKeyH = -1f
    var fxBloomKeyAlpha = -1
    val fxChromaticPaint = AndroidPaint().apply { style = AndroidPaint.Style.STROKE }
    val fxVignettePaint = AndroidPaint()
    var fxVignetteKeyW = -1f
    var fxVignetteKeyH = -1f
    var fxVignetteKeyAlpha = -1

    val camOutsideMaskPaint = AndroidPaint().apply {
        color = AndroidColor.argb(115, 8, 12, 22)
        style = AndroidPaint.Style.FILL
    }
    val camOuterBorderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(165, 92, 110, 140)
        style = AndroidPaint.Style.STROKE
    }
    val camActiveBorderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(41, 121, 255)
        style = AndroidPaint.Style.STROKE
    }
    val camSafeBoxPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(150, 0, 210, 255)
        style = AndroidPaint.Style.STROKE
    }
    val camCornerPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 145, 0)
        style = AndroidPaint.Style.STROKE
    }
    val camBadgeBgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(205, 16, 20, 34)
        style = AndroidPaint.Style.FILL
    }
    val camBadgeTextPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
    }

    // Reusable 3D Stage & Camera POV viewport paints, geometry buffers & matrices
    val povMattePaint = AndroidPaint()
    var povMatteKeyH = -1f
    val povFramePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 167, 38)
        style = AndroidPaint.Style.STROKE
    }
    val povInfoPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
    }
    val stageGridPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }
    val stageOriginPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(190, 255, 167, 38)
        style = AndroidPaint.Style.STROKE
    }
    val stagePolyMatrix = AndroidMatrix()
    val stageSrcPts = FloatArray(8)
    val stageDstPts = FloatArray(8)
    val stageLayerBmpPaint = AndroidPaint(
        AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.DITHER_FLAG
    )
    val stageCardStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }
    val stageLabelPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
    }
    val stageLabelBgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(175, 14, 17, 24)
        style = AndroidPaint.Style.FILL
    }
    val stageCardPath = AndroidPath()
    val stageRayPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }
    var stageRayDashDensity = -1f
    val stageFrustumStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }
    val stageFrustumFillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(230, 255, 145, 0)
        style = AndroidPaint.Style.FILL
    }
    val stageGizmoPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.STROKE }
    val stageGizmoHandleFillPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.FILL }
    val stageGizmoTextPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
        textAlign = AndroidPaint.Align.CENTER
    }
    val stageSelObjStrokePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(0, 230, 118)
        style = AndroidPaint.Style.STROKE
    }
    val stageAxisLinePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val stageAxisDotPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { style = AndroidPaint.Style.FILL }
    val stageAxisTextPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        isFakeBoldText = true
        textAlign = AndroidPaint.Align.CENTER
    }
    val liveMatrixScratch = AndroidMatrix()
    val invMatrixScratch = AndroidMatrix()
    val deltaMatrixScratch = AndroidMatrix()
    val vpMatrixScratch = AndroidMatrix()
    val paperMarginPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(115, 130, 150, 185)
        style = AndroidPaint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasView(
    project: AnimationProject,
    currentFrameIndex: Int,
    selectedTool: ToolType,
    currentColor: Long,
    brushSize: Float,
    brushOpacity: Float,
    brushPresetId: Int = 1,
    activeLayerIndex: Int = 0,
    activeLocalLayerIndex: Int = 0,
    activeTargetIsFrameLocalLayer: Boolean = false,
    onionSkinEnabled: Boolean,
    isStylusOnlyMode: Boolean,
    zoom: Float,
    panX: Float,
    panY: Float,
    rotation: Float,
    onTransformChange: (zoomChange: Float, panX: Float, panY: Float, rotationChange: Float) -> Unit,
    onStrokeCompleted: (DrawingStroke) -> Unit,
    onEyedropperColorPicked: (Long) -> Unit,
    activeSelection: SelectionRegion = SelectionRegion(),
    onBucketFill: ((Float, Float) -> Unit)? = null,
    onMagicWand: ((Float, Float) -> Unit)? = null,
    onLassoCompleted: ((List<StrokePoint>) -> Unit)? = null,
    onSelectionOffsetChange: ((Float, Float) -> Unit)? = null,
    tabletSettings: TabletDriverSettings = TabletDriverSettings(),
    themeAccentColor: Long = 0xFF987EDC,
    onFocusActive: (() -> Unit)? = null,
    remoteCursors: Map<String, com.example.model.RemotePeerCursor> = emptyMap(),
    pendingKnockRequest: com.example.model.JoinKnockRequest? = null,
    @Suppress("UNUSED_PARAMETER")
    onRespondToKnock: ((Boolean) -> Unit)? = null,
    onLocalCursorMoved: ((Float, Float, Boolean) -> Unit)? = null,
    connectedFriend: com.example.model.ConnectedFriendInfo? = null,
    connectedFriends: List<com.example.model.ConnectedFriendInfo> = emptyList(),
    remoteLiveStrokes: Map<String, com.example.model.RemoteLiveStroke> = emptyMap(),
    onLiveStrokePreview: ((DrawingStroke?) -> Unit)? = null,
    currentPingMs: Int = 0,
    lowPowerMode: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    enableChunkCulling: Boolean = true,
    eraserUsesPressure: Boolean = true,
    eraserBrushPresetId: Int = 1,
    activeBrushParamsJson: String? = null,
    mirrorHorizontal: Boolean = false,
    activeScrubTimeMs: Long? = null,
    isVideoTransformMode: Boolean = false,
    onVideoTransformChange: ((dx: Double, dy: Double, scaleFactor: Double, rotationDelta: Double) -> Unit)? = null,
    bezierNodes: List<BezierNode>? = null,
    isBezierClosed: Boolean? = null,
    showFloatingBezierBar: Boolean = true,
    onBezierNodesChange: ((List<BezierNode>) -> Unit)? = null,
    onToggleBezierClosed: (() -> Unit)? = null,
    onUndoBezierNode: (() -> Unit)? = null,
    onClearBezierNodes: (() -> Unit)? = null,
    onConfirmBezierCurve: (() -> Unit)? = null,
    gridRulerSettings: GridRulerSettings = GridRulerSettings(),
    perspectiveRulerSettings: PerspectiveRulerSettings = PerspectiveRulerSettings(),
    onPerspectiveVanishingPointDrag: ((vpIdx: Int, xRatio: Float, yRatio: Float) -> Unit)? = null,
    onMultiFingerGesture: ((MultiFingerGesture) -> Unit)? = null,
    isFloatingRefBelowCanvas: Boolean = false,
    eraserSize: Float = brushSize,
    referenceImage: ReferenceImageState = ReferenceImageState(),
    onFloatingRefTransformDelta: ((panX: Float, panY: Float, zoomFactor: Float, rotateDelta: Float) -> Unit)? = null,
    isCameraSideView3D: Boolean = false,
    isCameraModeActive: Boolean = false,
    cameraToolAction: CameraToolAction = CameraToolAction.PAN_2D,
    onCameraTransformDelta: ((dx: Float, dy: Float, zoomFactor: Float, rotDelta: Float, fovDelta: Float, dYaw: Float, dPitch: Float) -> Unit)? = null,
    cameraViewportNavTarget: CameraViewportNavTarget = CameraViewportNavTarget.CAMERA,
    stageSelection: StageSelection = StageSelection(),
    stageFocusNonce: Int = 0,
    onStageTargetSelected: ((StageSelection) -> Unit)? = null,
    onSelectedStageObjectDelta: ((dx: Float, dy: Float, dz: Float, dRotX: Float, dRotY: Float, dRotZ: Float, scaleFactor: Float) -> Unit)? = null,
    studio3DPaintEnabled: Boolean = false,
    onPaintSceneObjectUV: ((objectId: String, u0: Float, v0: Float, u1: Float, v1: Float) -> Unit)? = null,
    theme: KodanimaTheme = KodanimaTheme.ROXO_BLACK,
    currentTimelineTick: Int = currentFrameIndex,
    playbackTimeMs: Long? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentPoints = remember { mutableStateListOf<StrokePoint>() }
    var isDrawingActive by remember { mutableStateOf(false) }
    var activeStrokeId by remember { mutableStateOf("") }
    var activeStrokeVersion by remember { mutableIntStateOf(0) }
    var isMovingSelection by remember { mutableStateOf(false) }
    var lastSelTouchPos by remember { mutableStateOf(Offset.Zero) }
    var activePerspectiveDragIdx by remember { mutableIntStateOf(-1) }
    var lockedPerspectiveTargetVpIdx by remember { mutableIntStateOf(-1) }

    // Interactive Bezier Pen state (supports hoisted ViewModel state or standalone fallback)
    val workingBezierNodes = remember { mutableStateListOf<BezierNode>() }
    var internalIsBezierClosed by remember { mutableStateOf(false) }
    val activeIsBezierClosed = isBezierClosed ?: internalIsBezierClosed
    var activeBezierDragIdx by remember { mutableIntStateOf(-1) }
    var activeBezierDragPart by remember { mutableIntStateOf(0) } // 1=Anchor, 2=HandleIn, 3=HandleOut, 4=PullNew

    LaunchedEffect(bezierNodes) {
        if (bezierNodes != null) {
            workingBezierNodes.clear()
            workingBezierNodes.addAll(bezierNodes)
            activeStrokeVersion++
        }
    }

    // Video drag state
    var isMovingVideo by remember { mutableStateOf(false) }
    var lastVideoTouchProj by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentFrameIndex) {
        if (onClearBezierNodes == null) {
            workingBezierNodes.clear()
            internalIsBezierClosed = false
        }
        activeBezierDragIdx = -1
        activeBezierDragPart = 0
    }

    // Asynchronous FPS / scrub-synchronized Video/GIF Reference decoding (zero RenderThread blocking)
    val videoAsset = remember(project.mediaAssets) {
        project.mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }
    }
    val effectiveTickFloat = remember(activeScrubTimeMs, playbackTimeMs, currentTimelineTick, project.fps) {
        (activeScrubTimeMs ?: playbackTimeMs)?.let {
            TimelineTrackEngine.timeMsToTickFloat(it, project.fps)
        } ?: currentTimelineTick.toFloat()
    }
    val effectiveMediaTimeMs = remember(activeScrubTimeMs, playbackTimeMs, currentTimelineTick, project.fps) {
        activeScrubTimeMs ?: playbackTimeMs ?: TimelineTrackEngine.tickToTimeMs(
            currentTimelineTick,
            project.fps
        )
    }
    val videoFrameBitmap by produceState<Bitmap?>(
        initialValue = null,
        videoAsset?.uriString,
        videoAsset?.type,
        effectiveMediaTimeMs
    ) {
        value = if (videoAsset != null && videoAsset.uriString.isNotBlank()) {
            withContext(Dispatchers.IO) {
                MediaAssetManager.getVideoFrameAtMs(
                    videoAsset.uriString,
                    videoAsset.type,
                    effectiveMediaTimeMs
                )
            }
        } else null
    }

    // Multi-touch & Procreate gesture tracking states
    var isTransforming by remember { mutableStateOf(false) }
    var waitingForFullRelease by remember { mutableStateOf(false) }
    var lastDistance by remember { mutableStateOf(0f) }
    var lastCenter by remember { mutableStateOf(Offset.Zero) }
    var lastAngle by remember { mutableStateOf(0f) }
    var gestureSessionActive by remember { mutableStateOf(false) }
    var gestureMaxPointers by remember { mutableIntStateOf(0) }
    var gestureDownTimeMs by remember { mutableLongStateOf(0L) }
    var gestureStartCenter by remember { mutableStateOf(Offset.Zero) }
    var gestureLastCenter by remember { mutableStateOf(Offset.Zero) }
    var gestureStartDist by remember { mutableStateOf(0f) }
    var gestureStartAngle by remember { mutableStateOf(0f) }
    var didTransformMove by remember { mutableStateOf(false) }

    // Interactive 3D Stage Viewport orbit, pan, zoom & 3D Camera manipulation (when isCameraSideView3D is true)
    var stage3DMode by remember { mutableStateOf(Stage3DInteractionMode.ORBIT_STAGE) }
    var view3DYaw by remember { mutableFloatStateOf(-28f) }
    var view3DPitch by remember { mutableFloatStateOf(16f) }
    var view3DPanX by remember { mutableFloatStateOf(0f) }
    var view3DPanY by remember { mutableFloatStateOf(0f) }
    var view3DZoom by remember { mutableFloatStateOf(1f) }
    var isOrbiting3D by remember { mutableStateOf(false) }
    var isDraggingStage3DCam by remember { mutableStateOf(false) }
    var isPaintingStage3DObject by remember { mutableStateOf(false) }
    var paint3DTargetObjId by remember { mutableStateOf<String?>(null) }
    var paint3DLastUV by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var active3DGizmoAxis by remember { mutableIntStateOf(0) } // 0=none, 1=X, 2=Y, 3=Z, 4=Free
    var lastOrbitTouchPos by remember { mutableStateOf(Offset.Zero) }
    var stage3DDownPos by remember { mutableStateOf(Offset.Zero) }
    var stage3DMovedPastSlop by remember { mutableStateOf(false) }
    val stage3DGizmo = remember { Stage3DCamGizmoState() }
    val stage3DObjectBounds = remember { HashMap<String, ProjectedObjectBounds>() }
    val stage3DLayerBounds = remember { HashMap<String, RectF>() }

    // Camera Mode 1-finger drag state & Canvas-navigation pan in 2D Camera mode
    var isDraggingCameraTool by remember { mutableStateOf(false) }
    var lastCameraDragPos by remember { mutableStateOf(Offset.Zero) }
    var isCameraCanvasPanning by remember { mutableStateOf(false) }
    var lastCamCanvasPanPos by remember { mutableStateOf(Offset.Zero) }

    // PC Middle-mouse / Alt+Drag temporary viewport pan
    var isMiddleMousePanning by remember { mutableStateOf(false) }
    var lastMiddlePanPos by remember { mutableStateOf(Offset.Zero) }

    // Sub-frame synchronous viewport transform holder so high-rate MotionEvents never read stale state
    val liveTf = remember { LiveViewportTransform(zoom, panX, panY, rotation) }
    val projToScreenMatrix = remember { AndroidMatrix() }
    val screenToProjMatrix = remember { AndroidMatrix() }

    // Cursor & live stroke broadcast throttling (~30Hz cursor / ~20Hz live preview)
    var lastCursorBroadcastMs by remember { mutableLongStateOf(0L) }
    var lastLiveStrokeBroadcastMs by remember { mutableLongStateOf(0L) }
    var lastBroadcastX by remember { mutableStateOf(-10000f) }
    var lastBroadcastY by remember { mutableStateOf(-10000f) }
    var lastBroadcastDrawing by remember { mutableStateOf(false) }

    // Virtual tablet cursor state (local & remote custom .cur/.ani/.png bitmaps with hotspot)
    var cursorScreenPos by remember { mutableStateOf<Offset?>(null) }
    val customCursorData = remember(tabletSettings.customCursorPath) {
        tabletSettings.customCursorPath?.let { CursorHelper.loadCursorWithHotspotFromFile(it) }
    }
    DisposableEffect(customCursorData) {
        onDispose {
            val bmp = customCursorData?.bitmap
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
    }

    val remoteCursorKeys = connectedFriends.map { it.id to it.customCursorBase64 }
    val remoteCursorBitmaps = remember(remoteCursorKeys) {
        connectedFriends.mapNotNull { friend ->
            val b64 = friend.customCursorBase64
            val bmp = if (!b64.isNullOrBlank()) CursorHelper.decodeCursorFromBase64(b64) else null
            if (bmp != null) friend.id to bmp else null
        }.toMap()
    }
    DisposableEffect(remoteCursorBitmaps) {
        onDispose {
            remoteCursorBitmaps.values.forEach { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
    }

    // Offscreen GPU-backed bitmap cache holder + world-space GPU RenderNode Tile Engine
    val cacheHolder = remember { CanvasBitmapCache() }
    val tileEngine = remember { CanvasTileEngine() }
    val latestTilePlanRef = remember { AtomicReference<TilePlan?>(null) }
    val latestTileSlicesRef = remember { AtomicReference<List<TileSliceSpec>>(emptyList()) }
    val tileBuildTrigger = remember { Channel<Unit>(Channel.CONFLATED) }
    val optimisticActiveFallbackRef = remember { AtomicReference<OptimisticActiveFallback?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            tileEngine.releaseAll()
            cacheHolder.releaseAll()
        }
    }

    // Pre-allocated zero-churn overlay Paints & Paths
    val overlayScratch = remember { CanvasOverlayScratch() }

    val hwProfile = HardwareCapabilitiesEngine.currentProfile
    val densityMult = if (lowPowerMode) min(0.55f, hwProfile.particleDensityMultiplier) else hwProfile.particleDensityMultiplier
    val longestProj = max(project.resolution.width, project.resolution.height).coerceAtLeast(1)
    val maxCacheDim = if (lowPowerMode) min(1024, hwProfile.maxCacheDimensionPx) else hwProfile.maxCacheDimensionPx
    val cacheScale = if (longestProj > maxCacheDim) maxCacheDim.toFloat() / longestProj.toFloat() else 1.0f
    val cacheW = (project.resolution.width * cacheScale).roundToInt().coerceAtLeast(1)
    val cacheH = (project.resolution.height * cacheScale).roundToInt().coerceAtLeast(1)

    // Precompute cached offscreen bitmaps cleanly before DrawScope
    val currFrame = remember(project, currentTimelineTick, activeLayerIndex, currentFrameIndex) {
        if (project.frames.isEmpty()) null
        else TimelineTrackEngine.composeFrameAtTick(
            project = project,
            tick = currentTimelineTick,
            activeLayerIndex = activeLayerIndex,
            fallbackFrameIndex = currentFrameIndex
        )
    }
    val timelineLayers = currFrame?.layers ?: emptyList()
    val localLayers = currFrame?.localLayers ?: emptyList()
    val combinedLayers = remember(timelineLayers, localLayers) {
        KodanimaGraphicsEngine.composeOrderedFrameLayers(timelineLayers, localLayers)
    }
    val clampedTimelineLayerIdx = if (timelineLayers.isNotEmpty()) {
        activeLayerIndex.coerceIn(0, timelineLayers.lastIndex)
    } else 0
    val clampedLocalLayerIdx = if (localLayers.isNotEmpty()) {
        activeLocalLayerIndex.coerceIn(0, localLayers.lastIndex)
    } else 0
    val belowLocalCount = remember(localLayers) { localLayers.count { it.placeBelowFrame } }
    val activeGlobalLayerIdx = if (combinedLayers.isNotEmpty()) {
        if (activeTargetIsFrameLocalLayer && localLayers.isNotEmpty()) {
            val targetLocal = localLayers[clampedLocalLayerIdx]
            val localOrderedIdx = if (targetLocal.placeBelowFrame) {
                localLayers.subList(0, clampedLocalLayerIdx).count { it.placeBelowFrame }
            } else {
                belowLocalCount + timelineLayers.size + localLayers.subList(0, clampedLocalLayerIdx).count { !it.placeBelowFrame }
            }
            localOrderedIdx.coerceIn(0, combinedLayers.lastIndex)
        } else {
            (belowLocalCount + clampedTimelineLayerIdx).coerceIn(0, combinedLayers.lastIndex)
        }
    } else 0
    val activeLayerObj = combinedLayers.getOrNull(activeGlobalLayerIdx)

    val activeSelectionClipPoly = remember(activeSelection) {
        if (activeSelection.isActive && activeSelection.points.size >= 3) {
            SelectionGeometryHelper.transformSelectionPolygon(activeSelection)
        } else null
    }

    // Rebuild Stage 3D & Camera POV caches cleanly before DrawScope
    LaunchedEffect(
        cacheW,
        cacheH,
        currentTimelineTick,
        currentFrameIndex,
        activeGlobalLayerIdx,
        currFrame,
        project.canvasBackground,
        project.camera3D,
        isCameraModeActive,
        isCameraSideView3D,
        stage3DMode,
        densityMult,
        effectiveTickFloat
    ) {
        cacheHolder.ensureSize(cacheW, cacheH)

        // Per-layer transparent bitmaps + paper plane for interactive 3D Stage & animated 2D Camera layers
        if (isCameraSideView3D || isCameraModeActive) {
            val bgKey = Pair(project.canvasBackground, Pair(cacheW, cacheH))
            if (project.canvasBackground.style == CanvasBackgroundStyle.TRANSPARENTE) {
                KodanimaBitmapPool.release(cacheHolder.stage3DBgBitmap)
                cacheHolder.stage3DBgBitmap = null
                cacheHolder.stage3DBgKey = bgKey
            } else if (cacheHolder.stage3DBgKey != bgKey || cacheHolder.stage3DBgBitmap?.isRecycled != false) {
                val bgBmp = if (cacheHolder.stage3DBgBitmap != null && !cacheHolder.stage3DBgBitmap!!.isRecycled &&
                    cacheHolder.stage3DBgBitmap!!.width == cacheW && cacheHolder.stage3DBgBitmap!!.height == cacheH
                ) {
                    cacheHolder.stage3DBgBitmap!!.also { it.eraseColor(AndroidColor.TRANSPARENT) }
                } else {
                    KodanimaBitmapPool.release(cacheHolder.stage3DBgBitmap)
                    KodanimaBitmapPool.acquire(cacheW, cacheH, Bitmap.Config.ARGB_8888, AndroidColor.TRANSPARENT)
                }
                val c = AndroidCanvas(bgBmp)
                KodanimaGraphicsEngine.drawProjectBackgroundToCanvas(
                    canvas = c,
                    width = cacheW.toFloat(),
                    height = cacheH.toFloat(),
                    bgSettings = project.canvasBackground,
                    drawSkyWhenTransparent = false
                )
                cacheHolder.stage3DBgBitmap = bgBmp
                cacheHolder.stage3DBgKey = bgKey
            }

            val validLayerIds = combinedLayers.map { it.id }.toSet()
            val staleIds = cacheHolder.stage3DLayerBitmaps.keys.filter { it !in validLayerIds }
            staleIds.forEach { staleId ->
                KodanimaBitmapPool.release(cacheHolder.stage3DLayerBitmaps.remove(staleId))
                cacheHolder.stage3DLayerKeys.remove(staleId)
                KodanimaBitmapPool.release(cacheHolder.stage3DOffCanvasBitmaps.remove(staleId))
                cacheHolder.stage3DOffCanvasKeys.remove(staleId)
            }

            val projWf = project.resolution.width.toFloat()
            val projHf = project.resolution.height.toFloat()
            val offW = (cacheW * 2).coerceAtLeast(2)
            val offH = (cacheH * 2).coerceAtLeast(2)

            for (layer in combinedLayers) {
                val lKey = LayerSliceCacheKey(listOf(layer), cacheW, cacheH, densityMult)
                val existing = cacheHolder.stage3DLayerBitmaps[layer.id]
                if (existing == null || existing.isRecycled || cacheHolder.stage3DLayerKeys[layer.id] != lKey) {
                    val bmp = if (existing != null && !existing.isRecycled && existing.width == cacheW && existing.height == cacheH) {
                        existing.also { it.eraseColor(AndroidColor.TRANSPARENT) }
                    } else {
                        KodanimaBitmapPool.release(existing)
                        KodanimaBitmapPool.acquire(cacheW, cacheH, Bitmap.Config.ARGB_8888, AndroidColor.TRANSPARENT)
                    }
                    if (layer.isVisible && layer.opacity > 0f) {
                        val c = AndroidCanvas(bmp)
                        c.scale(cacheScale, cacheScale)
                        KodanimaGraphicsEngine.drawLayerToCanvas(
                            layer, c, project.resolution.width, project.resolution.height,
                            1.0f, null, null, null, densityMult
                        )
                    }
                    cacheHolder.stage3DLayerBitmaps[layer.id] = bmp
                    cacheHolder.stage3DLayerKeys[layer.id] = lKey
                }

                // Detect off-canvas artwork beyond defined project resolution without expanding the main layer card
                val hasOffCanvas = layer.isVisible && layer.opacity > 0f && layer.strokes.any { st ->
                    st.points.isNotEmpty() && (st.minX < -2f || st.minY < -2f || st.maxX > projWf + 2f || st.maxY > projHf + 2f)
                }
                if (!hasOffCanvas) {
                    cacheHolder.stage3DOffCanvasBitmaps.remove(layer.id)?.let { KodanimaBitmapPool.release(it) }
                    cacheHolder.stage3DOffCanvasKeys.remove(layer.id)
                } else {
                    val offKey = LayerSliceCacheKey(listOf(layer), offW, offH, densityMult)
                    val existingOff = cacheHolder.stage3DOffCanvasBitmaps[layer.id]
                    if (existingOff == null || existingOff.isRecycled || cacheHolder.stage3DOffCanvasKeys[layer.id] != offKey) {
                        val offBmp = if (existingOff != null && !existingOff.isRecycled && existingOff.width == offW && existingOff.height == offH) {
                            existingOff.also { it.eraseColor(AndroidColor.TRANSPARENT) }
                        } else {
                            KodanimaBitmapPool.release(existingOff)
                            KodanimaBitmapPool.acquire(offW, offH, Bitmap.Config.ARGB_8888, AndroidColor.TRANSPARENT)
                        }
                        val oc = AndroidCanvas(offBmp)
                        oc.translate(cacheW * 0.5f, cacheH * 0.5f)
                        oc.scale(cacheScale, cacheScale)
                        KodanimaGraphicsEngine.drawLayerToCanvas(
                            layer, oc, project.resolution.width, project.resolution.height,
                            1.0f, null, null, null, densityMult, allowOutOfBounds = true
                        )
                        cacheHolder.stage3DOffCanvasBitmaps[layer.id] = offBmp
                        cacheHolder.stage3DOffCanvasKeys[layer.id] = offKey
                    }
                }
            }

            // Live Camera POV preview bitmap when viewing through the 3D camera lens
            if (stage3DMode == Stage3DInteractionMode.CAMERA_POV && currFrame != null) {
                val camAsp = (project.camera3D.captureWidth.toFloat() / project.camera3D.captureHeight.coerceAtLeast(1).toFloat()).coerceIn(0.35f, 2.8f)
                val maxPovDim = min(maxCacheDim, 1024).coerceAtLeast(320)
                val povW = if (camAsp >= 1f) maxPovDim else (maxPovDim * camAsp).roundToInt().coerceAtLeast(160)
                val povH = if (camAsp >= 1f) (maxPovDim / camAsp).roundToInt().coerceAtLeast(160) else maxPovDim
                val povKey = listOf(currFrame, project.camera3D, currentTimelineTick, project.canvasBackground, povW, povH)
                if (cacheHolder.stage3DPovKey != povKey || cacheHolder.stage3DPovBitmap?.isRecycled != false) {
                    val existingPov = cacheHolder.stage3DPovBitmap
                    val povBmp = if (existingPov != null && !existingPov.isRecycled && existingPov.width == povW && existingPov.height == povH) {
                        existingPov
                    } else {
                        KodanimaBitmapPool.release(existingPov)
                        KodanimaBitmapPool.acquire(povW, povH, Bitmap.Config.ARGB_8888, AndroidColor.TRANSPARENT)
                    }
                    Camera3DEngine.renderFrameWithCameraAndEffects(
                        frame = currFrame,
                        settings = project.camera3D.copy(isEnabled = true),
                        frameIndex = currentTimelineTick,
                        projectWidth = project.resolution.width,
                        projectHeight = project.resolution.height,
                        targetBitmap = povBmp,
                        cropToCamera = true,
                        background = project.canvasBackground,
                        renderTransparentAsSky = true,
                        tickFloat = effectiveTickFloat
                    )
                    cacheHolder.stage3DPovBitmap = povBmp
                    cacheHolder.stage3DPovKey = povKey
                }
            }
        }
        activeStrokeVersion++
    }

    // "Mostrar 3D no Canvas": Static 2D cache & Pre-render on Play (tracks paused scrubbing tick without cancelling active playback cache)
    val canvas3DPausedTickKey = if (isPlaying) -1 else currentTimelineTick
    LaunchedEffect(
        cacheW,
        cacheH,
        project.resolution,
        project.canvasBackground,
        project.camera3D,
        project.fps,
        isCameraSideView3D,
        isPlaying,
        canvas3DPausedTickKey
    ) {
        if (!project.canvasBackground.show3DInCanvas || isCameraSideView3D) {
            if (cacheHolder.canvas3DBgStaticBitmap != null || cacheHolder.canvas3DBgPlayFrames.isNotEmpty() || cacheHolder.canvas3DBgRealtimeBitmap != null) {
                cacheHolder.releaseCanvas3DBg()
                activeStrokeVersion++
            }
            return@LaunchedEffect
        }
        val bgBaseKey = listOf(
            cacheW,
            cacheH,
            project.resolution,
            project.canvasBackground,
            project.camera3D.scene3DObjects,
            project.camera3D.keyframes,
            project.camera3D.effectSpans,
            project.camera3D.cameraMode,
            project.camera3D.autoInterpolate,
            project.camera3D.isEnabled,
            project.camera3D.layerDepthSpacing,
            project.fps
        )
        when (project.canvasBackground.canvas3DRenderMode) {
            Canvas3DBackgroundRenderMode.STATIC_2D_CACHE -> {
                val staticKey = Pair(bgBaseKey, canvas3DPausedTickKey)
                val cachedPair = cacheHolder.canvas3DBgStaticKey as? Pair<*, *>
                val sameBaseWhilePlaying = isPlaying &&
                    cachedPair?.first == bgBaseKey &&
                    cacheHolder.canvas3DBgStaticBitmap?.isRecycled == false
                if (!sameBaseWhilePlaying && (cacheHolder.canvas3DBgStaticKey != staticKey || cacheHolder.canvas3DBgStaticBitmap?.isRecycled != false)) {
                    val renderTick = if (isPlaying) 0f else effectiveTickFloat
                    val bmp = withContext(Dispatchers.Default) {
                        Camera3DEngine.renderCanvas3DBackgroundBitmap(
                            outW = cacheW,
                            outH = cacheH,
                            project = project,
                            tickFloat = renderTick,
                            fps = project.fps,
                            reuseBitmap = cacheHolder.canvas3DBgStaticBitmap
                        )
                    }
                    cacheHolder.canvas3DBgStaticBitmap = bmp
                    cacheHolder.canvas3DBgStaticKey = staticKey
                    activeStrokeVersion++
                }
            }
            Canvas3DBackgroundRenderMode.PRE_RENDER_ON_PLAY -> {
                val staticKey = Pair(bgBaseKey, canvas3DPausedTickKey)
                if (!isPlaying && (cacheHolder.canvas3DBgStaticKey != staticKey || cacheHolder.canvas3DBgStaticBitmap?.isRecycled != false)) {
                    val bmp = withContext(Dispatchers.Default) {
                        Camera3DEngine.renderCanvas3DBackgroundBitmap(
                            outW = cacheW,
                            outH = cacheH,
                            project = project,
                            tickFloat = effectiveTickFloat,
                            fps = project.fps,
                            reuseBitmap = cacheHolder.canvas3DBgStaticBitmap
                        )
                    }
                    cacheHolder.canvas3DBgStaticBitmap = bmp
                    cacheHolder.canvas3DBgStaticKey = staticKey
                    activeStrokeVersion++
                }
                val totalTicks = TimelineTrackEngine.computeTotalTicks(
                    project,
                    includeCameraRunway = project.camera3D.isEnabled
                ).coerceIn(1, 240)
                if (isPlaying && (cacheHolder.canvas3DBgPlayKey != bgBaseKey || cacheHolder.canvas3DBgPlayFrames.size < totalTicks)) {
                    val renderedMap = withContext(Dispatchers.Default) {
                        val map = LinkedHashMap<Int, Bitmap>(totalTicks)
                        for (t in 0 until totalTicks) {
                            map[t] = Camera3DEngine.renderCanvas3DBackgroundBitmap(
                                outW = cacheW,
                                outH = cacheH,
                                project = project,
                                tickFloat = t.toFloat(),
                                fps = project.fps
                            )
                        }
                        map
                    }
                    cacheHolder.canvas3DBgPlayFrames.values.forEach { KodanimaBitmapPool.release(it) }
                    cacheHolder.canvas3DBgPlayFrames.clear()
                    cacheHolder.canvas3DBgPlayFrames.putAll(renderedMap)
                    cacheHolder.canvas3DBgPlayKey = bgBaseKey
                    activeStrokeVersion++
                }
            }
            Canvas3DBackgroundRenderMode.REALTIME_3D -> Unit
        }
    }

    // "Mostrar 3D no Canvas" Mode 3: Real-time 3D update for animated characters/objects
    LaunchedEffect(
        cacheW,
        cacheH,
        project.resolution,
        project.canvasBackground,
        project.camera3D,
        project.fps,
        isCameraSideView3D,
        effectiveTickFloat
    ) {
        if (!project.canvasBackground.show3DInCanvas ||
            isCameraSideView3D ||
            project.canvasBackground.canvas3DRenderMode != Canvas3DBackgroundRenderMode.REALTIME_3D
        ) return@LaunchedEffect
        val bgBaseKey = listOf(
            cacheW,
            cacheH,
            project.resolution,
            project.canvasBackground,
            project.camera3D.scene3DObjects,
            project.camera3D.keyframes,
            project.camera3D.effectSpans,
            project.camera3D.cameraMode,
            project.camera3D.autoInterpolate,
            project.camera3D.isEnabled,
            project.camera3D.layerDepthSpacing,
            project.fps
        )
        val rtKey = Pair(bgBaseKey, (effectiveTickFloat * 20f).roundToInt())
        if (cacheHolder.canvas3DBgRealtimeKey != rtKey || cacheHolder.canvas3DBgRealtimeBitmap?.isRecycled != false) {
            val bmp = withContext(Dispatchers.Default) {
                Camera3DEngine.renderCanvas3DBackgroundBitmap(
                    outW = cacheW,
                    outH = cacheH,
                    project = project,
                    tickFloat = effectiveTickFloat,
                    fps = project.fps,
                    reuseBitmap = cacheHolder.canvas3DBgRealtimeBitmap
                )
            }
            cacheHolder.canvas3DBgRealtimeBitmap = bmp
            cacheHolder.canvas3DBgRealtimeKey = rtKey
            activeStrokeVersion++
        }
    }

    val stageUnitScaleRef = remember { mutableFloatStateOf(0.16f) }

    // Center 3D viewport on currently selected stage element when focus is requested
    LaunchedEffect(stageFocusNonce) {
        if (stageFocusNonce > 0) {
            val camSet = project.camera3D
            val kf = Camera3DEngine.evaluateCameraAtTick(camSet, effectiveTickFloat)
            view3DZoom = 1.32f
            val focusScale = stageUnitScaleRef.floatValue * 1.32f
            when (stageSelection.type) {
                StageTargetType.CAMERA -> {
                    view3DPanX = (-kf.x * focusScale).coerceIn(-900f, 900f)
                    view3DPanY = (-kf.y * focusScale).coerceIn(-900f, 900f)
                }
                StageTargetType.OBJECT_3D -> {
                    val obj = camSet.scene3DObjects.firstOrNull { it.id == stageSelection.id }
                    if (obj != null) {
                        val tf = Camera3DEngine.evaluateObjectTransformAtTick(obj.keyframes, effectiveTickFloat, camSet.autoInterpolate)
                        view3DPanX = (-tf.x * focusScale).coerceIn(-900f, 900f)
                        view3DPanY = (-tf.y * focusScale).coerceIn(-900f, 900f)
                    } else {
                        view3DPanX = 0f
                        view3DPanY = 0f
                    }
                }
                StageTargetType.LAYER -> {
                    val tf = Camera3DEngine.evaluateLayerTransformAtTick(camSet.layerTransformTracks, stageSelection.id, effectiveTickFloat, camSet.autoInterpolate)
                    view3DPanX = (-tf.x * focusScale).coerceIn(-900f, 900f)
                    view3DPanY = (-tf.y * focusScale).coerceIn(-900f, 900f)
                }
            }
        }
    }

    // Precompute trigonometric rotation coefficients once per rotation change
    val radNeg = remember(rotation) { Math.toRadians(-rotation.toDouble()) }
    val cosNegR = remember(radNeg) { cos(radNeg).toFloat() }
    val sinNegR = remember(radNeg) { sin(radNeg).toFloat() }
    val radPos = remember(rotation) { Math.toRadians(rotation.toDouble()) }
    val cosPosR = remember(radPos) { cos(radPos).toFloat() }
    val sinPosR = remember(radPos) { sin(radPos).toFloat() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .testTag("animation_canvas_container"),
        contentAlignment = Alignment.Center
    ) {
        val projWidth = project.resolution.width.toFloat()
        val projHeight = project.resolution.height.toFloat()
        val projAspect = projWidth / projHeight

        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val containerAspect = containerWidth / containerHeight

        val baseWidth: Float
        val baseHeight: Float
        if (containerAspect > projAspect) {
            baseHeight = containerHeight * 0.85f
            baseWidth = baseHeight * projAspect
        } else {
            baseWidth = containerWidth * 0.90f
            baseHeight = baseWidth / projAspect
        }

        val boxWidthPx = baseWidth.roundToInt().coerceAtLeast(1)
        val boxHeightPx = baseHeight.roundToInt().coerceAtLeast(1)
        val boxW = boxWidthPx.toFloat()
        val boxH = boxHeightPx.toFloat()

        val density = LocalDensity.current
        val halfContainerW = containerWidth * 0.5f
        val halfContainerH = containerHeight * 0.5f
        val invZoom = 1f / zoom.coerceAtLeast(0.05f)
        val screenToProjScaleX = projWidth / boxW
        val effectiveToolSize = if (selectedTool == ToolType.ERASER) eraserSize else brushSize
        val camSettings = project.camera3D
        val evaluatedCameraKf = remember(camSettings, effectiveTickFloat) {
            Camera3DEngine.evaluateCameraAtTick(camSettings, effectiveTickFloat)
        }

        // Screen-resolution off-canvas active stroke/eraser buffers with 40px predictive overscan
        val vpLongest = max(containerWidth, containerHeight).coerceAtLeast(1f)
        val vpScale = if (vpLongest > maxCacheDim) maxCacheDim.toFloat() / vpLongest else 1.0f
        val vpDims = KodanimaGraphicsEngine.computeViewportCacheDimensions(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            vpScale = vpScale,
            overscanScreenPx = PredictiveViewportTracker.MAX_PREDICTIVE_OVERSCAN_PX
        )
        val vpCacheW = vpDims.cacheWidth
        val vpCacheH = vpDims.cacheHeight
        val vpMarginPx = vpDims.marginPx

        // Partition frame layers cleanly into GPU tile slices decoupled from Pan / Rotation
        val onionSlicesData = remember(
            currentTimelineTick,
            onionSkinEnabled,
            project.onionSkin,
            project.resolution,
            densityMult
        ) {
            val prevCount = project.onionSkin.prevFramesCount
            val nextCount = project.onionSkin.nextFramesCount
            val enabled = onionSkinEnabled && project.onionSkin.isEnabled
            val prevList = if (enabled && prevCount > 0) {
                (prevCount downTo 1).map { step ->
                    TimelineTrackEngine.resolveOnionSkinLayersAtTick(project, currentTimelineTick, -step)
                }
            } else emptyList()
            val nextList = if (enabled && nextCount > 0) {
                (1..nextCount).map { step ->
                    TimelineTrackEngine.resolveOnionSkinLayersAtTick(project, currentTimelineTick, step)
                }
            } else emptyList()
            val oHash = listOf(currentTimelineTick, enabled, project.onionSkin, prevList, nextList, project.resolution.width, project.resolution.height, densityMult).hashCode()
            TileSliceSpec.OnionSlice(
                contentHash = oHash,
                settings = project.onionSkin.copy(isEnabled = enabled),
                prevLayers = prevList,
                nextLayers = nextList,
                projWidth = project.resolution.width,
                projHeight = project.resolution.height,
                densityMult = densityMult
            )
        }

        val belowSlice = remember(combinedLayers, activeGlobalLayerIdx) {
            if (combinedLayers.isNotEmpty() && activeGlobalLayerIdx > 0) {
                combinedLayers.subList(0, activeGlobalLayerIdx)
            } else emptyList()
        }
        val activeSlice = remember(combinedLayers, activeGlobalLayerIdx) {
            if (activeGlobalLayerIdx in combinedLayers.indices) {
                listOf(combinedLayers[activeGlobalLayerIdx])
            } else emptyList()
        }
        val aboveSlice = remember(combinedLayers, activeGlobalLayerIdx) {
            if (activeGlobalLayerIdx + 1 < combinedLayers.size) {
                combinedLayers.subList(activeGlobalLayerIdx + 1, combinedLayers.size)
            } else emptyList()
        }

        val rawActiveLayer = activeSlice.firstOrNull()
        val clipBaseLayer = remember(rawActiveLayer, belowSlice) {
            if (rawActiveLayer != null && rawActiveLayer.isClippingMask) {
                belowSlice.lastOrNull { !it.isClippingMask && it.isVisible && it.opacity > 0f }
            } else null
        }
        val cbSlice = remember(clipBaseLayer) {
            if (clipBaseLayer != null) listOf(clipBaseLayer) else emptyList()
        }

        val hideIds = if (activeSelection.isActive) activeSelection.hiddenSourceStrokeIds else emptySet()
        val outsideRepl = if (activeSelection.isActive) activeSelection.outsideReplacementStrokes else emptyList()
        val effectiveActiveSlice = remember(rawActiveLayer, activeSlice, hideIds, outsideRepl) {
            if (rawActiveLayer != null && (hideIds.isNotEmpty() || outsideRepl.isNotEmpty())) {
                listOf(rawActiveLayer.copy(strokes = rawActiveLayer.strokes.filter { it.id !in hideIds } + outsideRepl))
            } else {
                activeSlice
            }
        }
        val aboveNeedsAdvanced = remember(aboveSlice) {
            aboveSlice.any { it.isClippingMask || it.blendEffect != LayerBlendEffect.NORMAL }
        }

        val belowSpec = remember(belowSlice, project.resolution, densityMult) {
            val h = listOf(belowSlice, project.resolution.width, project.resolution.height, densityMult).hashCode()
            TileSliceSpec.LayersSlice("below", h, belowSlice, project.resolution.width, project.resolution.height, densityMult)
        }
        val clipBaseSpec = remember(cbSlice, project.resolution, densityMult) {
            if (cbSlice.isEmpty()) null else {
                val h = listOf(cbSlice, project.resolution.width, project.resolution.height, densityMult).hashCode()
                TileSliceSpec.LayersSlice("clipBase", h, cbSlice, project.resolution.width, project.resolution.height, densityMult)
            }
        }
        val activeSpec = remember(effectiveActiveSlice, project.resolution, densityMult) {
            val h = listOf(effectiveActiveSlice, project.resolution.width, project.resolution.height, densityMult).hashCode()
            TileSliceSpec.LayersSlice("active", h, effectiveActiveSlice, project.resolution.width, project.resolution.height, densityMult)
        }
        val aboveSimpleSpec = remember(aboveSlice, aboveNeedsAdvanced, project.resolution, densityMult) {
            if (aboveNeedsAdvanced || aboveSlice.isEmpty()) null else {
                val h = listOf(aboveSlice, project.resolution.width, project.resolution.height, densityMult).hashCode()
                TileSliceSpec.LayersSlice("above", h, aboveSlice, project.resolution.width, project.resolution.height, densityMult)
            }
        }
        val aboveAdvancedSpecs = remember(aboveSlice, aboveNeedsAdvanced, project.resolution, densityMult) {
            if (!aboveNeedsAdvanced || aboveSlice.isEmpty()) emptyList() else {
                aboveSlice.map { l ->
                    val single = listOf(l)
                    val h = listOf(single, project.resolution.width, project.resolution.height, densityMult).hashCode()
                    l to TileSliceSpec.LayersSlice("above_${l.id}", h, single, project.resolution.width, project.resolution.height, densityMult)
                }
            }
        }

        val allTileSlices = remember(
            onionSlicesData, belowSpec, clipBaseSpec, activeSpec, aboveSimpleSpec, aboveAdvancedSpecs
        ) {
            buildList {
                if (onionSlicesData.settings.isEnabled) add(onionSlicesData)
                if (belowSpec.layers.isNotEmpty()) add(belowSpec)
                if (clipBaseSpec != null) add(clipBaseSpec)
                if (activeSpec.layers.isNotEmpty()) add(activeSpec)
                if (aboveSimpleSpec != null) add(aboveSimpleSpec)
                aboveAdvancedSpecs.forEach { add(it.second) }
            }
        }

        SideEffect {
            latestTileSlicesRef.set(allTileSlices)
            val fb = optimisticActiveFallbackRef.get()
            if (fb != null) {
                val curLayerId = activeLayerObj?.id
                val vmHasStroke = activeLayerObj?.strokes?.any { it.id == fb.strokeId } == true
                when {
                    curLayerId != fb.layerId -> optimisticActiveFallbackRef.set(null)
                    !fb.seenInVm && vmHasStroke -> optimisticActiveFallbackRef.set(fb.copy(seenInVm = true))
                    fb.seenInVm && !vmHasStroke -> optimisticActiveFallbackRef.set(null)
                    fb.seenInVm && activeSpec.contentHash !in fb.fallbackHashes -> {
                        val curPlan = latestTilePlanRef.get()
                        if (curPlan != null && tileEngine.hasAllVisibleTilesForSlice(curPlan, activeSpec)) {
                            optimisticActiveFallbackRef.set(null)
                        }
                    }
                }
            }
        }

        LaunchedEffect(allTileSlices, containerWidth, containerHeight, zoom, panX, panY, rotation, mirrorHorizontal) {
            if (isCameraSideView3D || containerWidth <= 2f || containerHeight <= 2f) return@LaunchedEffect
            val safeZ = zoom.coerceAtLeast(0.05f)
            val mat = AndroidMatrix().apply {
                setScale(boxW / projWidth, boxH / projHeight)
                postTranslate(-boxW * 0.5f, -boxH * 0.5f)
                postScale(if (mirrorHorizontal) -safeZ else safeZ, safeZ)
                postRotate(rotation)
                postTranslate(halfContainerW + panX, halfContainerH + panY)
            }
            val spp = (boxW / projWidth) * safeZ
            tileEngine.computeTilePlan(mat, containerWidth, containerHeight, spp)?.let { plan ->
                latestTilePlanRef.set(plan)
            }
            tileBuildTrigger.trySend(Unit)
        }

        // Continuous non-cancelling GPU tile rasterization worker on backgroundRender pool
        LaunchedEffect(Unit) {
            for (ignored in tileBuildTrigger) {
                var pass = 0
                while (pass < 32) {
                    val plan = latestTilePlanRef.get() ?: break
                    val slices = latestTileSlicesRef.get()
                    if (slices.isEmpty()) break
                    val built = withContext(KodanimaDispatchers.backgroundRender) {
                        tileEngine.ensureTilesForPlan(
                            plan = plan,
                            slices = slices,
                            maxTilesPerPass = 8,
                            onBatchReady = null
                        )
                    }
                    if (built > 0) {
                        activeStrokeVersion++
                    }
                    val latestPlan = latestTilePlanRef.get() ?: plan
                    val latestSlices = latestTileSlicesRef.get()
                    if (built < 8 && !tileEngine.hasMissingTiles(latestPlan, latestSlices)) break
                    pass++
                }
            }
        }

        // Background-thread 3D rasterization for off-canvas backdrop (matches active camera angle & framing at 60fps UI)
        LaunchedEffect(
            isCameraSideView3D,
            isCameraModeActive,
            camSettings.isEnabled,
            camSettings.scene3DObjects,
            camSettings.autoInterpolate,
            camSettings.shadingMode,
            evaluatedCameraKf,
            currentTimelineTick,
            effectiveTickFloat,
            containerWidth,
            containerHeight,
            boxW,
            boxH,
            projWidth,
            zoom,
            panX,
            panY,
            rotation,
            project.fps
        ) {
            val hasVisible3DObjs = camSettings.scene3DObjects.any { it.isVisible }
            if (isCameraSideView3D || (!isCameraModeActive && !camSettings.isEnabled) || !hasVisible3DObjs || containerWidth <= 20f || containerHeight <= 20f) {
                if (cacheHolder.offCanvas3DBitmap != null) {
                    cacheHolder.releaseOffCanvas3D()
                    activeStrokeVersion++
                }
                return@LaunchedEffect
            }
            val rW = (containerWidth * 0.50f).roundToInt().coerceIn(240, 840)
            val rH = (containerHeight * 0.50f).roundToInt().coerceIn(180, 840)
            val resScale = rW / containerWidth.coerceAtLeast(1f)
            val fovScale = (tan(Math.toRadians(25.0)) / tan(Math.toRadians(evaluatedCameraKf.fovDegrees.coerceIn(18f, 120f) * 0.5)))
                .toFloat().coerceIn(0.35f, 2.8f)
            val worldUnitScale = (boxW / projWidth.coerceAtLeast(1f)) * zoom * resScale * evaluatedCameraKf.zoom
            val camYaw3D = -evaluatedCameraKf.yawY
            val camPitch3D = evaluatedCameraKf.pitchX
            val camRoll3D = rotation - evaluatedCameraKf.rotationZ
            val camPanX3D = evaluatedCameraKf.x * worldUnitScale
            val camPanY3D = evaluatedCameraKf.y * worldUnitScale
            val vpCx3D = (containerWidth * 0.5f + panX) * resScale
            val vpCy3D = (containerHeight * 0.5f + panY) * resScale
            val focal3D = max(boxW, boxH) * zoom * resScale * 0.85f * fovScale
            val animTimeSec = effectiveTickFloat / max(1, project.fps).toFloat()
            val offKey = listOf(
                rW, rH, camSettings.scene3DObjects, camSettings.shadingMode, currentTimelineTick,
                camSettings.autoInterpolate, camYaw3D, camPitch3D, camRoll3D,
                camPanX3D, camPanY3D, focal3D, vpCx3D, vpCy3D, worldUnitScale,
                (effectiveTickFloat * 20f).roundToInt()
            )
            if (cacheHolder.offCanvas3DKey != offKey || cacheHolder.offCanvas3DBitmap?.isRecycled != false) {
                val bmp = withContext(Dispatchers.Default) {
                    val res = Camera3DEngine.renderSceneObjectsSoftware3D(
                        outW = rW,
                        outH = rH,
                        sceneObjects = camSettings.scene3DObjects,
                        frameIndex = currentTimelineTick,
                        autoInterpolate = camSettings.autoInterpolate,
                        camYawDeg = camYaw3D,
                        camPitchDeg = camPitch3D,
                        camRollDeg = camRoll3D,
                        camPanX = camPanX3D,
                        camPanY = camPanY3D,
                        focalPx = focal3D,
                        vpCx = vpCx3D,
                        vpCy = vpCy3D,
                        worldUnitScale = worldUnitScale,
                        tickFloat = effectiveTickFloat,
                        animTimeSec = animTimeSec,
                        reuseBitmap = cacheHolder.offCanvas3DBitmap,
                        shadingMode = camSettings.shadingMode
                    )
                    res?.first
                }
                if (bmp != null) {
                    cacheHolder.offCanvas3DBitmap = bmp
                    cacheHolder.offCanvas3DKey = offKey
                    activeStrokeVersion++
                }
            }
        }

        // Background-thread 3D object rasterization for interactive 3D Stage viewport (zero draw-thread rasterization)
        LaunchedEffect(
            isCameraSideView3D,
            stage3DMode,
            camSettings.scene3DObjects,
            camSettings.autoInterpolate,
            camSettings.shadingMode,
            evaluatedCameraKf,
            currentTimelineTick,
            effectiveTickFloat,
            containerWidth,
            containerHeight,
            projWidth,
            view3DYaw,
            view3DPitch,
            view3DPanX,
            view3DPanY,
            view3DZoom,
            stageSelection,
            project.fps
        ) {
            if (!isCameraSideView3D || stage3DMode == Stage3DInteractionMode.CAMERA_POV || containerWidth <= 20f || containerHeight <= 20f) {
                return@LaunchedEffect
            }
            val visible3DObjs = camSettings.scene3DObjects.filter { it.isVisible }
            val validIds = visible3DObjs.map { it.id }.toSet()
            val staleObjIds = cacheHolder.stage3DObjectEntries.keys.filter { it !in validIds }
            staleObjIds.forEach { staleId ->
                cacheHolder.stage3DObjectEntries.remove(staleId)?.let { KodanimaBitmapPool.release(it.bitmap) }
            }
            if (visible3DObjs.isEmpty()) {
                if (staleObjIds.isNotEmpty()) activeStrokeVersion++
                return@LaunchedEffect
            }
            val maxStageDim = if (NativeGL3DEngine.isHardwareAvailable()) 1080f else 680f
            val scaleDown = min(1f, maxStageDim / max(containerWidth, containerHeight).coerceAtLeast(1f))
            val rW = (containerWidth * scaleDown).roundToInt().coerceAtLeast(64)
            val rH = (containerHeight * scaleDown).roundToInt().coerceAtLeast(64)
            val invScale = 1f / scaleDown
            val fovScale = (tan(Math.toRadians(25.0)) / tan(Math.toRadians(evaluatedCameraKf.fovDegrees.coerceIn(18f, 120f) * 0.5)))
                .toFloat().coerceIn(0.35f, 2.6f)
            val focal = max(containerWidth, containerHeight) * 0.85f * fovScale
            val vpCx = containerWidth * 0.5f + view3DPanX
            val vpCy = containerHeight * 0.50f + view3DPanY
            val stageProjScale = computeStage3DProjScale(containerWidth, containerHeight, projWidth, view3DZoom)
            val selectedId = if (stageSelection.type == StageTargetType.OBJECT_3D) stageSelection.id else null
            val animTimeSec = effectiveTickFloat / max(1, project.fps).toFloat()
            val tickBucket = (effectiveTickFloat * 20f).roundToInt()
            var anyUpdated = staleObjIds.isNotEmpty()

            for (obj in visible3DObjs) {
                val objKey = listOf(
                    obj, camSettings.shadingMode, currentTimelineTick, camSettings.autoInterpolate,
                    rW, rH, view3DYaw, view3DPitch, focal, vpCx, vpCy, stageProjScale,
                    selectedId == obj.id, tickBucket
                )
                val existing = cacheHolder.stage3DObjectEntries[obj.id]
                if (existing != null && !existing.bitmap.isRecycled && existing.key == objKey) {
                    continue
                }
                val rendered = withContext(Dispatchers.Default) {
                    Camera3DEngine.renderSceneObjectsSoftware3D(
                        outW = rW,
                        outH = rH,
                        sceneObjects = listOf(obj),
                        frameIndex = currentTimelineTick,
                        autoInterpolate = camSettings.autoInterpolate,
                        camYawDeg = view3DYaw,
                        camPitchDeg = view3DPitch,
                        camRollDeg = 0f,
                        camPanX = 0f,
                        camPanY = 0f,
                        focalPx = focal * scaleDown,
                        vpCx = vpCx * scaleDown,
                        vpCy = vpCy * scaleDown,
                        worldUnitScale = stageProjScale * scaleDown,
                        selectedObjectId = if (selectedId == obj.id) obj.id else null,
                        tickFloat = effectiveTickFloat,
                        animTimeSec = animTimeSec,
                        reuseBitmap = existing?.bitmap,
                        shadingMode = camSettings.shadingMode
                    )
                }
                if (rendered != null) {
                    val (bmp, bMap) = rendered
                    val b = bMap[obj.id]
                    val scaledBounds = if (b != null) {
                        ProjectedObjectBounds(
                            id = b.id,
                            centerX = b.centerX * invScale,
                            centerY = b.centerY * invScale,
                            minX = b.minX * invScale,
                            minY = b.minY * invScale,
                            maxX = b.maxX * invScale,
                            maxY = b.maxY * invScale,
                            avgDepth = b.avgDepth * invScale - focal
                        )
                    } else null
                    cacheHolder.stage3DObjectEntries[obj.id] = CanvasBitmapCache.Stage3DObjectCacheEntry(
                        bitmap = bmp,
                        bounds = scaledBounds,
                        key = objKey
                    )
                    anyUpdated = true
                }
            }
            if (anyUpdated) activeStrokeVersion++
        }

        SideEffect {
            stageUnitScaleRef.floatValue = computeStage3DProjScale(containerWidth, containerHeight, projWidth, 1f)
            if (!isTransforming) {
                liveTf.zoom = zoom
                liveTf.panX = panX
                liveTf.panY = panY
                liveTf.rotation = rotation
            }
        }

        fun syncProjectionMatrices() {
            val z = liveTf.zoom.coerceAtLeast(0.05f)
            projToScreenMatrix.reset()
            projToScreenMatrix.setScale(boxW / projWidth, boxH / projHeight)
            projToScreenMatrix.postTranslate(-boxW * 0.5f, -boxH * 0.5f)
            projToScreenMatrix.postScale(if (mirrorHorizontal) -z else z, z)
            projToScreenMatrix.postRotate(liveTf.rotation)
            projToScreenMatrix.postTranslate(halfContainerW + liveTf.panX, halfContainerH + liveTf.panY)
            projToScreenMatrix.invert(screenToProjMatrix)
        }

        fun screenToProjectCoords(screenX: Float, screenY: Float): Offset {
            syncProjectionMatrices()
            val pts = floatArrayOf(screenX, screenY)
            screenToProjMatrix.mapPoints(pts)
            return Offset(pts[0], pts[1])
        }

        fun applyTabletInversion(rawX: Float, rawY: Float): Offset {
            val px = if (tabletSettings.invertX) (projWidth - rawX) else rawX
            val py = if (tabletSettings.invertY) (projHeight - rawY) else rawY
            return Offset(px, py)
        }

        fun projectToScreenCoords(projX: Float, projY: Float): Offset {
            syncProjectionMatrices()
            val pts = floatArrayOf(projX, projY)
            projToScreenMatrix.mapPoints(pts)
            return Offset(pts[0], pts[1])
        }

        val curSelectedTool by rememberUpdatedState(selectedTool)
        val curColor by rememberUpdatedState(currentColor)
        val curEffToolSize by rememberUpdatedState(effectiveToolSize)
        val curBrushOpacity by rememberUpdatedState(brushOpacity)
        val curBrushPresetId by rememberUpdatedState(brushPresetId)
        val curEraserPresetId by rememberUpdatedState(eraserBrushPresetId)
        val curBrushParamsJson by rememberUpdatedState(activeBrushParamsJson)
        val curStylusOnly by rememberUpdatedState(isStylusOnlyMode)
        val curEraserPressure by rememberUpdatedState(eraserUsesPressure)
        val curMirrorH by rememberUpdatedState(mirrorHorizontal)
        val curIsSideView3D by rememberUpdatedState(isCameraSideView3D)
        val curStage3DMode by rememberUpdatedState(stage3DMode)
        val curIsCameraMode by rememberUpdatedState(isCameraModeActive)
        val curCameraToolAction by rememberUpdatedState(cameraToolAction)
        val curCamSettings by rememberUpdatedState(camSettings)
        val curIsVideoTransform by rememberUpdatedState(isVideoTransformMode)
        val curVideoAsset by rememberUpdatedState(videoAsset)
        val curActiveLayer by rememberUpdatedState(activeLayerObj)
        val curActiveSel by rememberUpdatedState(activeSelection)
        val curActiveSelClip by rememberUpdatedState(activeSelectionClipPoly)
        val curPerspSettings by rememberUpdatedState(perspectiveRulerSettings)
        val curTabletSettings by rememberUpdatedState(tabletSettings)
        val curEffectiveActiveSlice by rememberUpdatedState(effectiveActiveSlice)
        val curActiveSpecHash by rememberUpdatedState(activeSpec.contentHash)
        val curCombinedLayers by rememberUpdatedState(combinedLayers)
        val curOnTransformChange by rememberUpdatedState(onTransformChange)
        val curOnCameraDelta by rememberUpdatedState(onCameraTransformDelta)
        val curCamNavTarget by rememberUpdatedState(cameraViewportNavTarget)
        val curStageSelection by rememberUpdatedState(stageSelection)
        val curOnStageTargetSelected by rememberUpdatedState(onStageTargetSelected)
        val curOnStageObjectDelta by rememberUpdatedState(onSelectedStageObjectDelta)
        val curStudio3DPaintEnabled by rememberUpdatedState(studio3DPaintEnabled)
        val curOnPaintSceneObjectUV by rememberUpdatedState(onPaintSceneObjectUV)
        val curOnStrokeCompleted by rememberUpdatedState(onStrokeCompleted)
        val curOnEyedropper by rememberUpdatedState(onEyedropperColorPicked)
        val curOnBucket by rememberUpdatedState(onBucketFill)
        val curOnMagicWand by rememberUpdatedState(onMagicWand)
        val curOnLasso by rememberUpdatedState(onLassoCompleted)
        val curOnSelOffset by rememberUpdatedState(onSelectionOffsetChange)
        val curOnVideoTransform by rememberUpdatedState(onVideoTransformChange)
        val curOnMultiGesture by rememberUpdatedState(onMultiFingerGesture)
        val curOnLocalCursor by rememberUpdatedState(onLocalCursorMoved)
        val curOnLivePreview by rememberUpdatedState(onLiveStrokePreview)
        val curOnPerspVpDrag by rememberUpdatedState(onPerspectiveVanishingPointDrag)
        val curOnFocusActive by rememberUpdatedState(onFocusActive)
        val curOnBezierChange by rememberUpdatedState(onBezierNodesChange)

        fun mapPressureForEvent(
            motionEvent: MotionEvent,
            rawPressure: Float,
            isInitialDown: Boolean = false
        ): Float {
            val toolType = if (motionEvent.pointerCount > 0) {
                motionEvent.getToolType(0)
            } else {
                MotionEvent.TOOL_TYPE_UNKNOWN
            }
            if (toolType == MotionEvent.TOOL_TYPE_MOUSE) {
                return 1.0f
            }
            val isStylusInput = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
                toolType == MotionEvent.TOOL_TYPE_ERASER ||
                (motionEvent.source and android.view.InputDevice.SOURCE_STYLUS) == android.view.InputDevice.SOURCE_STYLUS

            val range = try {
                motionEvent.device?.getMotionRange(MotionEvent.AXIS_PRESSURE, motionEvent.source)
            } catch (_: Exception) {
                null
            }
            val normalizedRaw = if (range != null && (range.max - range.min) > 0.01f) {
                ((rawPressure - range.min) / (range.max - range.min)).coerceIn(0f, 1f)
            } else {
                rawPressure.coerceIn(0f, 1f)
            }

            if (!isStylusInput) {
                if (normalizedRaw <= 0.02f || normalizedRaw >= 0.99f) {
                    return 0.96f
                }
                return (0.86f + normalizedRaw * 0.14f).coerceIn(0.84f, 1.0f)
            }

            val stylusPressure = when {
                isInitialDown && normalizedRaw < 0.05f -> 0.35f
                normalizedRaw < 0.02f -> 0.18f
                else -> normalizedRaw
            }
            val quantizedP = when (tabletSettings.pressureMode) {
                PressureMode.AUTO -> stylusPressure
                PressureMode.LEVELS_1024 -> ((stylusPressure * 1024f).toInt() / 1024f).coerceIn(0.05f, 1f)
                PressureMode.LEVELS_2048 -> ((stylusPressure * 2048f).toInt() / 2048f).coerceIn(0.05f, 1f)
                PressureMode.LEVELS_8192 -> ((stylusPressure * 8192f).toInt() / 8192f).coerceIn(0.05f, 1f)
                PressureMode.LEVELS_16384 -> ((stylusPressure * 16384f).toInt() / 16384f).coerceIn(0.05f, 1f)
                PressureMode.CUSTOM -> {
                    val lvls = tabletSettings.customPressureLevels.coerceIn(64, 32768)
                    ((stylusPressure * lvls).toInt() / lvls.toFloat()).coerceIn(0.05f, 1f)
                }
            }
            return when (tabletSettings.pressureCurve) {
                PressureCurve.LINEAR -> quantizedP
                PressureCurve.SOFT -> sqrt(quantizedP)
                PressureCurve.FIRM -> quantizedP * quantizedP
            }.coerceIn(0.12f, 1.0f)
        }

        fun flushIncrementalRaster() {
            if (currentPoints.isEmpty()) return
            val sessionVpMat = AndroidMatrix(cacheHolder.activeSessionMatrix).apply {
                postScale(cacheHolder.activeSessionVpScale, cacheHolder.activeSessionVpScale)
                postTranslate(cacheHolder.activeSessionMarginPx.toFloat(), cacheHolder.activeSessionMarginPx.toFloat())
            }
            if (selectedTool == ToolType.ERASER) {
                val eCanvas = cacheHolder.eraserWorkCanvas ?: return
                val startIdx = max(0, cacheHolder.lastErasedPointIdx - 1)
                val endIdx = currentPoints.lastIndex
                if (endIdx > cacheHolder.lastErasedPointIdx || currentPoints.size == 1) {
                    val eraserStroke = DrawingStroke(
                        tool = ToolType.ERASER,
                        brushSize = effectiveToolSize,
                        opacity = brushOpacity,
                        points = currentPoints,
                        brushPresetId = eraserBrushPresetId,
                        clipPolygon = activeSelectionClipPoly,
                        clipInverted = activeSelectionClipPoly != null && activeSelection.isInverted,
                        clipBoundsWidth = projWidth,
                        clipBoundsHeight = projHeight
                    )
                    eCanvas.save()
                    eCanvas.concat(sessionVpMat)
                    KodanimaGraphicsEngine.drawStrokeSegmentRange(
                        eraserStroke, eCanvas, startIdx, endIdx, 1.0f, null, densityMult
                    )
                    eCanvas.restore()
                    cacheHolder.lastErasedPointIdx = currentPoints.size
                }
            } else if (
                selectedTool != ToolType.LINE &&
                selectedTool != ToolType.RECTANGLE &&
                selectedTool != ToolType.CIRCLE &&
                selectedTool != ToolType.LASSO &&
                selectedTool != ToolType.EYEDROPPER &&
                brushPresetId != 3
            ) {
                val sCanvas = cacheHolder.activeStrokeCanvas ?: return
                val activeStroke = DrawingStroke(
                    tool = selectedTool,
                    color = currentColor,
                    brushSize = effectiveToolSize,
                    opacity = brushOpacity,
                    points = currentPoints,
                    brushPresetId = brushPresetId,
                    customBrushParams = activeBrushParamsJson,
                    clipPolygon = activeSelectionClipPoly,
                    clipInverted = activeSelectionClipPoly != null && activeSelection.isInverted,
                    clipBoundsWidth = projWidth,
                    clipBoundsHeight = projHeight
                )
                if (currentPoints.size <= 280) {
                    sCanvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    sCanvas.save()
                    sCanvas.concat(sessionVpMat)
                    KodanimaGraphicsEngine.drawStrokeToCanvas(
                        activeStroke, sCanvas, 1.0f, null, densityMult
                    )
                    sCanvas.restore()
                    cacheHolder.lastRenderedStrokeIdx = currentPoints.size
                } else {
                    val startIdx = max(0, cacheHolder.lastRenderedStrokeIdx - 2)
                    val endIdx = currentPoints.lastIndex
                    if (endIdx > cacheHolder.lastRenderedStrokeIdx || currentPoints.size == 1) {
                        sCanvas.save()
                        sCanvas.concat(sessionVpMat)
                        KodanimaGraphicsEngine.drawStrokeSegmentRange(
                            activeStroke, sCanvas, startIdx, endIdx, 1.0f, null, densityMult
                        )
                        sCanvas.restore()
                        cacheHolder.lastRenderedStrokeIdx = currentPoints.size
                    }
                }
            }
        }

        fun shouldAcceptStrokePoint(x: Float, y: Float, pressure: Float): Boolean {
            if (currentPoints.isEmpty()) return true
            val prev = currentPoints[currentPoints.lastIndex]
            val dx = x - prev.x
            val dy = y - prev.y
            return (dx * dx + dy * dy) >= 1.3f || abs(pressure - prev.pressure) >= 0.06f
        }

        fun applyPerspectiveSnapIfEnabled(pt: Offset): Offset {
            if (!perspectiveRulerSettings.isEnabled || !perspectiveRulerSettings.snapToGuide) return pt
            if (selectedTool != ToolType.PEN && selectedTool != ToolType.PENCIL && selectedTool != ToolType.BRUSH) return pt
            val origin = currentPoints.firstOrNull() ?: return pt
            val vx = pt.x - origin.x
            val vy = pt.y - origin.y
            val dist = hypot(vx, vy)
            if (dist < 10f) return pt

            val pW = project.resolution.width.toFloat()
            val pH = project.resolution.height.toFloat()
            val vpList = buildList {
                add(Offset(perspectiveRulerSettings.vp1XRatio * pW, perspectiveRulerSettings.vp1YRatio * pH))
                if (perspectiveRulerSettings.vanishingPointsCount >= 2) {
                    add(Offset(perspectiveRulerSettings.vp2XRatio * pW, perspectiveRulerSettings.vp2YRatio * pH))
                }
                if (perspectiveRulerSettings.vanishingPointsCount >= 3) {
                    add(Offset(perspectiveRulerSettings.vp3XRatio * pW, perspectiveRulerSettings.vp3YRatio * pH))
                }
            }

            if (lockedPerspectiveTargetVpIdx !in vpList.indices && dist >= 14f) {
                var bestIdx = 0
                var bestCos = -1f
                val invD = 1f / dist
                vpList.forEachIndexed { idx, vp ->
                    val rx = vp.x - origin.x
                    val ry = vp.y - origin.y
                    val rLen = hypot(rx, ry).coerceAtLeast(1f)
                    val dotCos = abs(((vx * invD) * (rx / rLen)) + ((vy * invD) * (ry / rLen)))
                    if (dotCos > bestCos) {
                        bestCos = dotCos
                        bestIdx = idx
                    }
                }
                lockedPerspectiveTargetVpIdx = bestIdx
            }

            val chosenIdx = lockedPerspectiveTargetVpIdx
            if (chosenIdx !in vpList.indices) return pt
            val vp = vpList[chosenIdx]
            val rx = vp.x - origin.x
            val ry = vp.y - origin.y
            val rLen = hypot(rx, ry)
            if (rLen < 1f) return pt
            val ux = rx / rLen
            val uy = ry / rLen
            val projLen = vx * ux + vy * uy
            return Offset(origin.x + ux * projLen, origin.y + uy * projLen)
        }

        // Viewport touch interceptor for unified 1-finger draw, 2-finger zoom/pan/rotate, Camera controls & 2/3/4-finger gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { motionEvent ->
                    if (pendingKnockRequest != null) {
                        return@pointerInteropFilter false
                    }
                    val pointerCount = motionEvent.pointerCount

                    if (pointerCount >= 2) {
                        if (isDrawingActive) {
                            currentPoints.clear()
                            isDrawingActive = false
                            lockedPerspectiveTargetVpIdx = -1
                            cacheHolder.endActiveSessions()
                            curOnLivePreview?.invoke(null)
                            activeStrokeVersion++
                        }
                        isDraggingCameraTool = false
                        isOrbiting3D = false
                        isDraggingStage3DCam = false
                        active3DGizmoAxis = 0
                        isMiddleMousePanning = false
                        waitingForFullRelease = true

                        var sumX = 0f
                        var sumY = 0f
                        for (pi in 0 until pointerCount) {
                            sumX += motionEvent.getX(pi)
                            sumY += motionEvent.getY(pi)
                        }
                        val allCentroid = Offset(sumX / pointerCount, sumY / pointerCount)
                        gestureLastCenter = allCentroid

                        val p0x = motionEvent.getX(0); val p0y = motionEvent.getY(0)
                        val p1x = motionEvent.getX(1); val p1y = motionEvent.getY(1)
                        val currentCenter = Offset((p0x + p1x) * 0.5f, (p0y + p1y) * 0.5f)
                        val currentDist = hypot(p1x - p0x, p1y - p0y).coerceAtLeast(10f)
                        val currentAngle = Math.toDegrees(
                            atan2((p1y - p0y).toDouble(), (p1x - p0x).toDouble())
                        ).toFloat()

                        val densityScale = density.density.coerceAtLeast(1f)
                        val touchSlopScaled = 8f * densityScale

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                val prevMaxPointers = gestureMaxPointers
                                isTransforming = true
                                lastCenter = currentCenter
                                lastDistance = currentDist
                                lastAngle = currentAngle
                                if (!gestureSessionActive) {
                                    gestureSessionActive = true
                                    gestureMaxPointers = pointerCount
                                    gestureDownTimeMs = android.os.SystemClock.uptimeMillis()
                                    gestureStartCenter = allCentroid
                                    gestureLastCenter = allCentroid
                                    gestureStartDist = currentDist
                                    gestureStartAngle = currentAngle
                                    didTransformMove = false
                                } else {
                                    gestureMaxPointers = max(gestureMaxPointers, pointerCount)
                                    // If user was placing 3rd/4th finger slightly staggered (< ~40dp pinch drift),
                                    // do not let 2-finger pre-touch micro-slop cancel 3/4-finger tap or swipe gestures!
                                    if (prevMaxPointers < 3 && pointerCount >= 3 &&
                                        abs(currentDist - gestureStartDist) < 42f * densityScale
                                    ) {
                                        didTransformMove = false
                                    }
                                    gestureStartCenter = allCentroid
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                gestureMaxPointers = max(gestureMaxPointers, pointerCount)
                                if (pointerCount == 2 && (gestureMaxPointers == 2 || didTransformMove)) {
                                    var angleFromStart = currentAngle - gestureStartAngle
                                    while (angleFromStart > 180f) angleFromStart -= 360f
                                    while (angleFromStart < -180f) angleFromStart += 360f
                                    val movedFromStart = hypot(
                                        allCentroid.x - gestureStartCenter.x,
                                        allCentroid.y - gestureStartCenter.y
                                    )
                                    val distDeltaFromStart = abs(currentDist - gestureStartDist)
                                    val absAngleFromStart = abs(angleFromStart)
                                    if (!didTransformMove && (
                                            movedFromStart > touchSlopScaled ||
                                                distDeltaFromStart > touchSlopScaled ||
                                                absAngleFromStart > 3.0f
                                            )
                                    ) {
                                        didTransformMove = true
                                        lastCenter = currentCenter
                                        lastDistance = currentDist
                                        lastAngle = currentAngle
                                    }
                                    if (didTransformMove && isTransforming && lastDistance > 0f) {
                                        val rawZoomFactor = (currentDist / lastDistance).coerceIn(0.65f, 1.55f)
                                        val panDelta = currentCenter - lastCenter
                                        var angleDiff = currentAngle - lastAngle
                                        while (angleDiff > 180f) angleDiff -= 360f
                                        while (angleDiff < -180f) angleDiff += 360f

                                        when {
                                            curIsSideView3D -> {
                                                if ((curStage3DMode == Stage3DInteractionMode.MOVE_CAMERA ||
                                                        curStage3DMode == Stage3DInteractionMode.CAMERA_POV) &&
                                                    curOnCameraDelta != null
                                                ) {
                                                    val scaleProjX = projWidth / (boxW * view3DZoom.coerceAtLeast(0.25f))
                                                    curOnCameraDelta!!.invoke(
                                                        panDelta.x * scaleProjX,
                                                        panDelta.y * scaleProjX,
                                                        rawZoomFactor,
                                                        angleDiff,
                                                        0f, 0f, 0f
                                                    )
                                                } else {
                                                    view3DZoom = (view3DZoom * rawZoomFactor).coerceIn(0.35f, 4.0f)
                                                    view3DPanX += panDelta.x
                                                    view3DPanY += panDelta.y
                                                    view3DYaw = (view3DYaw + angleDiff * 0.55f) % 360f
                                                }
                                            }
                                            curIsCameraMode &&
                                                curCamSettings.isEnabled &&
                                                curCamNavTarget == CameraViewportNavTarget.CAMERA &&
                                                curOnCameraDelta != null -> {
                                                val scaleProjX = projWidth / (boxW * liveTf.zoom.coerceAtLeast(0.05f))
                                                curOnCameraDelta!!.invoke(
                                                    panDelta.x * scaleProjX,
                                                    panDelta.y * scaleProjX,
                                                    rawZoomFactor,
                                                    angleDiff,
                                                    0f, 0f, 0f
                                                )
                                            }
                                            curIsVideoTransform && curVideoAsset != null && curOnVideoTransform != null -> {
                                                val projDelta = screenToProjectCoords(currentCenter.x, currentCenter.y) -
                                                    screenToProjectCoords(lastCenter.x, lastCenter.y)
                                                curOnVideoTransform!!.invoke(
                                                    projDelta.x.toDouble(),
                                                    projDelta.y.toDouble(),
                                                    rawZoomFactor.toDouble(),
                                                    angleDiff.toDouble()
                                                )
                                            }
                                            else -> {
                                                val curZoom = liveTf.zoom.coerceAtLeast(0.05f)
                                                val targetZoom = (curZoom * rawZoomFactor).coerceIn(0.15f, 48.0f)
                                                val effZoomFactor = targetZoom / curZoom
                                                // Pivot around lastCenter using exact liveTf transform so no drift accumulates across sub-frame events
                                                val vx = lastCenter.x - (halfContainerW + liveTf.panX)
                                                val vy = lastCenter.y - (halfContainerH + liveTf.panY)
                                                val radScreen = Math.toRadians(angleDiff.toDouble())
                                                val cosA = cos(radScreen).toFloat()
                                                val sinA = sin(radScreen).toFloat()
                                                val rotScaledVx = (vx * cosA - vy * sinA) * effZoomFactor
                                                val rotScaledVy = (vx * sinA + vy * cosA) * effZoomFactor
                                                val compensatedPanX = (vx - rotScaledVx) + panDelta.x
                                                val compensatedPanY = (vy - rotScaledVy) + panDelta.y
                                                liveTf.zoom = targetZoom
                                                liveTf.panX += compensatedPanX
                                                liveTf.panY += compensatedPanY
                                                liveTf.rotation += angleDiff
                                                tileEngine.recordTransformDelta(compensatedPanX, compensatedPanY, effZoomFactor)
                                                curOnTransformChange(effZoomFactor, compensatedPanX, compensatedPanY, angleDiff)
                                            }
                                        }
                                        lastCenter = currentCenter
                                        lastDistance = currentDist
                                        lastAngle = currentAngle
                                    }
                                }
                            }
                            MotionEvent.ACTION_POINTER_UP -> {
                                waitingForFullRelease = true
                                isTransforming = false
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                waitingForFullRelease = false
                                isTransforming = false
                                gestureSessionActive = false
                                gestureMaxPointers = 0
                                didTransformMove = false
                            }
                        }
                        return@pointerInteropFilter true
                    }

                    if (pointerCount == 1) {
                        // A brand-new primary ACTION_DOWN always clears any lingering multi-touch lock
                        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                            waitingForFullRelease = false
                            isTransforming = false
                            gestureSessionActive = false
                            gestureMaxPointers = 1
                            didTransformMove = false
                        }

                        if (waitingForFullRelease) {
                            if (motionEvent.actionMasked == MotionEvent.ACTION_UP ||
                                motionEvent.actionMasked == MotionEvent.ACTION_CANCEL
                            ) {
                                if (motionEvent.actionMasked == MotionEvent.ACTION_UP &&
                                    gestureSessionActive &&
                                    !didTransformMove &&
                                    curOnMultiGesture != null
                                ) {
                                    val elapsed = android.os.SystemClock.uptimeMillis() - gestureDownTimeMs
                                    val dx = gestureLastCenter.x - gestureStartCenter.x
                                    val dy = gestureLastCenter.y - gestureStartCenter.y
                                    val disp = hypot(dx, dy)
                                    val dScale = density.density.coerceAtLeast(1f)
                                    val tapDisp = 22f * dScale
                                    val swipeDisp = 28f * dScale
                                    val detected = when {
                                        gestureMaxPointers == 2 && elapsed <= 450L && disp <= tapDisp ->
                                            MultiFingerGesture.TWO_FINGER_TAP
                                        gestureMaxPointers == 3 && elapsed <= 500L && disp <= tapDisp ->
                                            MultiFingerGesture.THREE_FINGER_TAP
                                        gestureMaxPointers == 3 && disp >= swipeDisp -> when {
                                            abs(dy) >= abs(dx) && dy < 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_UP
                                            abs(dy) >= abs(dx) && dy > 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_DOWN
                                            abs(dx) > abs(dy) && dx < 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_LEFT
                                            abs(dx) > abs(dy) && dx > 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_RIGHT
                                            else -> null
                                        }
                                        gestureMaxPointers >= 4 && elapsed <= 560L && disp <= (26f * dScale) ->
                                            MultiFingerGesture.FOUR_FINGER_TAP
                                        else -> null
                                    }
                                    if (detected != null) {
                                        curOnMultiGesture!!.invoke(detected)
                                    }
                                }
                                waitingForFullRelease = false
                                isTransforming = false
                                gestureSessionActive = false
                                gestureMaxPointers = 0
                                didTransformMove = false
                                isDrawingActive = false
                                isMovingSelection = false
                                isDraggingCameraTool = false
                                isCameraCanvasPanning = false
                                isOrbiting3D = false
                                isDraggingStage3DCam = false
                                active3DGizmoAxis = 0
                                isMiddleMousePanning = false
                                activePerspectiveDragIdx = -1
                                lockedPerspectiveTargetVpIdx = -1
                                currentPoints.clear()
                                cacheHolder.endActiveSessions()
                                curOnLivePreview?.invoke(null)
                            }
                            return@pointerInteropFilter true
                        }

                        val isStylus = motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                            motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                        if (curStylusOnly && !isStylus && !curIsSideView3D && !(curIsCameraMode && curCamSettings.isEnabled)) {
                            return@pointerInteropFilter false
                        }

                        val rawProjPt = screenToProjectCoords(motionEvent.x, motionEvent.y)
                        val projPt = applyTabletInversion(rawProjPt.x, rawProjPt.y)

                        if (!curIsSideView3D) {
                            cursorScreenPos = Offset(motionEvent.x, motionEvent.y)
                        } else {
                            cursorScreenPos = null
                        }

                        // Throttled multiplayer cursor sync (~30Hz)
                        if (curOnLocalCursor != null && !curIsSideView3D) {
                            val now = android.os.SystemClock.uptimeMillis()
                            val dxB = projPt.x - lastBroadcastX
                            val dyB = projPt.y - lastBroadcastY
                            val drawingStateForCursor = when (motionEvent.actionMasked) {
                                MotionEvent.ACTION_DOWN -> true
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
                                else -> isDrawingActive
                            }
                            if (drawingStateForCursor != lastBroadcastDrawing ||
                                (now - lastCursorBroadcastMs >= 33L && (dxB * dxB + dyB * dyB) > 2.25f)
                            ) {
                                lastCursorBroadcastMs = now
                                lastBroadcastX = projPt.x
                                lastBroadcastY = projPt.y
                                lastBroadcastDrawing = drawingStateForCursor
                                curOnLocalCursor!!.invoke(projPt.x, projPt.y, drawingStateForCursor)
                            }
                        }

                        val isDownAction = motionEvent.actionMasked == MotionEvent.ACTION_DOWN
                        val rawPressure = if (curSelectedTool == ToolType.ERASER && !curEraserPressure) {
                            1.0f
                        } else {
                            mapPressureForEvent(motionEvent, motionEvent.pressure, isInitialDown = isDownAction)
                        }

                        val isMiddleMouse = (motionEvent.buttonState and MotionEvent.BUTTON_TERTIARY) != 0
                        val isShiftHeld = (motionEvent.metaState and android.view.KeyEvent.META_SHIFT_ON) != 0

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                curOnFocusActive?.invoke()

                                // Interactive 3D Studio Viewport: 3D Paint, Gizmo X/Y/Z handles, Direct 3D Transform, Ray-Triangle Tap-to-Select, or Stage Orbit
                                if (curIsSideView3D) {
                                    lastOrbitTouchPos = Offset(motionEvent.x, motionEvent.y)
                                    stage3DDownPos = Offset(motionEvent.x, motionEvent.y)
                                    stage3DMovedPastSlop = false
                                    if (curStudio3DPaintEnabled &&
                                        curOnPaintSceneObjectUV != null &&
                                        curStage3DMode != Stage3DInteractionMode.CAMERA_POV
                                    ) {
                                        val fovScale = (tan(Math.toRadians(25.0)) / tan(Math.toRadians(evaluatedCameraKf.fovDegrees.coerceIn(18f, 120f) * 0.5)))
                                            .toFloat().coerceIn(0.35f, 2.6f)
                                        val stageFocal = max(containerWidth, containerHeight) * 0.85f * fovScale
                                        val stageVpCx = containerWidth * 0.5f + view3DPanX
                                        val stageVpCy = containerHeight * 0.50f + view3DPanY
                                        val stageProjScale = computeStage3DProjScale(containerWidth, containerHeight, projWidth, view3DZoom)
                                        val animTimeSec = effectiveTickFloat / max(1, project.fps).toFloat()
                                        val candidateObjs = if (curStageSelection.type == StageTargetType.OBJECT_3D) {
                                            val sel = curCamSettings.scene3DObjects.firstOrNull { it.id == curStageSelection.id }
                                            if (sel != null) listOf(sel) + curCamSettings.scene3DObjects.filter { it.id != sel.id }
                                            else curCamSettings.scene3DObjects
                                        } else curCamSettings.scene3DObjects

                                        var bestHit: com.example.engine.TrianglePickResult? = null
                                        for (obj in candidateObjs) {
                                            if (!obj.isVisible || obj.meshAssetPath.isBlank()) continue
                                            val h = Studio3DPaintEngine.pickObjectTriangleUV(
                                                obj = obj,
                                                frameIndex = currentTimelineTick,
                                                autoInterpolate = curCamSettings.autoInterpolate,
                                                screenX = motionEvent.x,
                                                screenY = motionEvent.y,
                                                camYawDeg = view3DYaw,
                                                camPitchDeg = view3DPitch,
                                                camRollDeg = 0f,
                                                camPanX = 0f,
                                                camPanY = 0f,
                                                focalPx = stageFocal,
                                                vpCx = stageVpCx,
                                                vpCy = stageVpCy,
                                                worldUnitScale = stageProjScale,
                                                tickFloat = effectiveTickFloat,
                                                animTimeSec = animTimeSec
                                            )
                                            if (h != null && (bestHit == null || h.depth < bestHit.depth)) {
                                                bestHit = h
                                            }
                                        }
                                        if (bestHit != null) {
                                            isPaintingStage3DObject = true
                                            paint3DTargetObjId = bestHit.objectId
                                            paint3DLastUV = bestHit.u to bestHit.v
                                            if (curStageSelection.type != StageTargetType.OBJECT_3D || curStageSelection.id != bestHit.objectId) {
                                                curOnStageTargetSelected?.invoke(StageSelection(StageTargetType.OBJECT_3D, bestHit.objectId))
                                            }
                                            curOnPaintSceneObjectUV!!.invoke(bestHit.objectId, bestHit.u, bestHit.v, bestHit.u, bestHit.v)
                                            return@pointerInteropFilter true
                                        }
                                    }
                                    if (curStage3DMode == Stage3DInteractionMode.CAMERA_POV) {
                                        isDraggingStage3DCam = true
                                        active3DGizmoAxis = 4
                                        return@pointerInteropFilter true
                                    }
                                    val canManipulateSelected = if (curStageSelection.type == StageTargetType.CAMERA) {
                                        curOnCameraDelta != null
                                    } else {
                                        curOnStageObjectDelta != null
                                    }
                                    if (stage3DGizmo.valid && canManipulateSelected) {
                                        val gHitR = 26f * density.density
                                        val gHitRSq = gHitR * gHitR
                                        fun distSq(ax: Float, ay: Float): Float {
                                            val ddx = motionEvent.x - ax
                                            val ddy = motionEvent.y - ay
                                            return ddx * ddx + ddy * ddy
                                        }
                                        val hitAxis = when {
                                            distSq(stage3DGizmo.xTipX, stage3DGizmo.xTipY) <= gHitRSq -> 1
                                            distSq(stage3DGizmo.yTipX, stage3DGizmo.yTipY) <= gHitRSq -> 2
                                            distSq(stage3DGizmo.zTipX, stage3DGizmo.zTipY) <= gHitRSq -> 3
                                            distSq(stage3DGizmo.apexX, stage3DGizmo.apexY) <= gHitRSq -> 4
                                            else -> 0
                                        }
                                        if (hitAxis != 0) {
                                            active3DGizmoAxis = hitAxis
                                            isDraggingStage3DCam = true
                                            return@pointerInteropFilter true
                                        }
                                    }
                                    if (curStage3DMode == Stage3DInteractionMode.MOVE_CAMERA && canManipulateSelected) {
                                        active3DGizmoAxis = 4
                                        isDraggingStage3DCam = true
                                        return@pointerInteropFilter true
                                    }
                                    isOrbiting3D = true
                                    return@pointerInteropFilter true
                                }

                                // Middle mouse button pans canvas (standard PC workflow)
                                if (isMiddleMouse) {
                                    isMiddleMousePanning = true
                                    lastMiddlePanPos = Offset(motionEvent.x, motionEvent.y)
                                    return@pointerInteropFilter true
                                }

                                // In 2D Camera Mode: either move Camera OR freely navigate the 2D Canvas (no accidental strokes)
                                if (curIsCameraMode) {
                                    if (curCamNavTarget == CameraViewportNavTarget.CAMERA &&
                                        curCamSettings.isEnabled &&
                                        curOnCameraDelta != null
                                    ) {
                                        isDraggingCameraTool = true
                                        lastCameraDragPos = Offset(motionEvent.x, motionEvent.y)
                                    } else {
                                        isCameraCanvasPanning = true
                                        lastCamCanvasPanPos = Offset(motionEvent.x, motionEvent.y)
                                    }
                                    return@pointerInteropFilter true
                                }

                                // Interactive Video Transform Mode: 1-finger moves video overlay
                                if (curIsVideoTransform && curVideoAsset != null && curOnVideoTransform != null) {
                                    isMovingVideo = true
                                    lastVideoTouchProj = projPt
                                    return@pointerInteropFilter true
                                }

                                // Interactive Caneta Vetorial (Bezier Pen)
                                if (curSelectedTool == ToolType.BEZIER_PEN) {
                                    val hitR = (28f * screenToProjScaleX / liveTf.zoom.coerceAtLeast(0.05f)).coerceAtLeast(18f)
                                    val hitRSq = hitR * hitR
                                    var foundIdx = -1
                                    var foundPart = 0
                                    for (i in workingBezierNodes.indices.reversed()) {
                                        val n = workingBezierNodes[i]
                                        val dOut = (projPt.x - n.handleOutX) * (projPt.x - n.handleOutX) +
                                            (projPt.y - n.handleOutY) * (projPt.y - n.handleOutY)
                                        if (dOut <= hitRSq && (n.handleOutX != n.anchorX || n.handleOutY != n.anchorY)) {
                                            foundIdx = i; foundPart = 3; break
                                        }
                                        val dIn = (projPt.x - n.handleInX) * (projPt.x - n.handleInX) +
                                            (projPt.y - n.handleInY) * (projPt.y - n.handleInY)
                                        if (dIn <= hitRSq && (n.handleInX != n.anchorX || n.handleInY != n.anchorY)) {
                                            foundIdx = i; foundPart = 2; break
                                        }
                                        val dAnc = (projPt.x - n.anchorX) * (projPt.x - n.anchorX) +
                                            (projPt.y - n.anchorY) * (projPt.y - n.anchorY)
                                        if (dAnc <= hitRSq) {
                                            foundIdx = i; foundPart = 1; break
                                        }
                                    }
                                    if (foundIdx >= 0) {
                                        activeBezierDragIdx = foundIdx
                                        activeBezierDragPart = foundPart
                                    } else {
                                        workingBezierNodes.add(BezierNode(anchorX = projPt.x, anchorY = projPt.y))
                                        curOnBezierChange?.invoke(workingBezierNodes.toList())
                                        activeBezierDragIdx = workingBezierNodes.lastIndex
                                        activeBezierDragPart = 4
                                    }
                                    activeStrokeVersion++
                                    return@pointerInteropFilter true
                                }

                                if (curActiveSel.isActive && (curSelectedTool == ToolType.LASSO || curSelectedTool == ToolType.MAGIC_WAND)) {
                                    val transformedPoly = curActiveSelClip
                                    val insidePoly = transformedPoly != null &&
                                        SelectionGeometryHelper.isPointInPolygon(projPt.x, projPt.y, transformedPoly)
                                    val selMinX = curActiveSel.minX + curActiveSel.offsetX
                                    val selMaxX = curActiveSel.maxX + curActiveSel.offsetX
                                    val selMinY = curActiveSel.minY + curActiveSel.offsetY
                                    val selMaxY = curActiveSel.maxY + curActiveSel.offsetY

                                    if (insidePoly || (projPt.x in selMinX..selMaxX && projPt.y in selMinY..selMaxY)) {
                                        isMovingSelection = true
                                        lastSelTouchPos = projPt
                                        return@pointerInteropFilter true
                                    }
                                }

                                if (curSelectedTool == ToolType.BUCKET) {
                                    curOnBucket?.invoke(projPt.x, projPt.y)
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }
                                if (curSelectedTool == ToolType.MAGIC_WAND) {
                                    curOnMagicWand?.invoke(projPt.x, projPt.y)
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }

                                if (curSelectedTool == ToolType.EYEDROPPER) {
                                    val baseBg = if (project.canvasBackground.style == CanvasBackgroundStyle.SOLIDO) {
                                        project.canvasBackground.primaryColor.toInt()
                                    } else AndroidColor.WHITE
                                    curOnEyedropper(
                                        tileEngine.sampleProjectPixelAt(
                                            layers = curCombinedLayers,
                                            projWidth = project.resolution.width,
                                            projHeight = project.resolution.height,
                                            projX = projPt.x,
                                            projY = projPt.y,
                                            densityMult = densityMult,
                                            baseBackgroundColor = baseBg
                                        )
                                    )
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }

                                // Drag Perspective Ruler Vanishing Point Handle if tapped (including edge-docked off-canvas VP badges)
                                if (curPerspSettings.isEnabled && curOnPerspVpDrag != null) {
                                    val hitR = (28f * density.density * screenToProjScaleX / liveTf.zoom.coerceAtLeast(0.05f)).coerceAtLeast(22f)
                                    val hitRSq = hitR * hitR
                                    val edgePad = (16f * density.density * screenToProjScaleX / liveTf.zoom.coerceAtLeast(0.05f)).coerceIn(10f, projWidth * 0.12f)
                                    val vps = buildList {
                                        add(Offset(curPerspSettings.vp1XRatio * projWidth, curPerspSettings.vp1YRatio * projHeight))
                                        if (curPerspSettings.vanishingPointsCount >= 2) {
                                            add(Offset(curPerspSettings.vp2XRatio * projWidth, curPerspSettings.vp2YRatio * projHeight))
                                        }
                                        if (curPerspSettings.vanishingPointsCount >= 3) {
                                            add(Offset(curPerspSettings.vp3XRatio * projWidth, curPerspSettings.vp3YRatio * projHeight))
                                        }
                                    }
                                    val hitIdx = vps.indexOfFirst { vp ->
                                        val dx = projPt.x - vp.x
                                        val dy = projPt.y - vp.y
                                        if ((dx * dx + dy * dy) <= hitRSq) {
                                            true
                                        } else if (vp.x < 0f || vp.x > projWidth || vp.y < 0f || vp.y > projHeight) {
                                            val dockX = vp.x.coerceIn(edgePad, (projWidth - edgePad).coerceAtLeast(edgePad))
                                            val dockY = vp.y.coerceIn(edgePad, (projHeight - edgePad).coerceAtLeast(edgePad))
                                            val ddx = projPt.x - dockX
                                            val ddy = projPt.y - dockY
                                            (ddx * ddx + ddy * ddy) <= hitRSq
                                        } else {
                                            false
                                        }
                                    }
                                    if (hitIdx >= 0) {
                                        activePerspectiveDragIdx = hitIdx
                                        return@pointerInteropFilter true
                                    }
                                }

                                // Guard against painting/erasing on locked or hidden layers
                                val isEditableTarget = curActiveLayer?.isLocked != true && curActiveLayer?.isVisible != false
                                if (!isEditableTarget) {
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }

                                syncProjectionMatrices()
                                cacheHolder.ensureViewportSize(vpCacheW, vpCacheH, vpScale, vpMarginPx)
                                if (curSelectedTool == ToolType.ERASER) {
                                    cacheHolder.beginEraserSession(
                                        sessionMatrix = projToScreenMatrix,
                                        sessionVpScale = vpScale,
                                        sessionMarginPx = vpMarginPx,
                                        activeLayers = curEffectiveActiveSlice,
                                        projWidth = project.resolution.width,
                                        projHeight = project.resolution.height,
                                        containerWidth = containerWidth,
                                        containerHeight = containerHeight,
                                        zoom = liveTf.zoom,
                                        activeSelection = curActiveSel,
                                        densityMult = densityMult
                                    )
                                } else {
                                    cacheHolder.beginActiveStrokeSession(
                                        sessionMatrix = projToScreenMatrix,
                                        sessionVpScale = vpScale,
                                        sessionMarginPx = vpMarginPx
                                    )
                                }

                                activeStrokeId = UUID.randomUUID().toString()
                                lockedPerspectiveTargetVpIdx = -1
                                isDrawingActive = true
                                currentPoints.clear()
                                currentPoints.add(StrokePoint(projPt.x, projPt.y, rawPressure))
                                flushIncrementalRaster()
                                activeStrokeVersion++
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (curIsSideView3D) {
                                    val moveDist = hypot(motionEvent.x - stage3DDownPos.x, motionEvent.y - stage3DDownPos.y)
                                    if (moveDist > 9f * density.density) stage3DMovedPastSlop = true
                                    if (isPaintingStage3DObject && curOnPaintSceneObjectUV != null) {
                                        val targetId = paint3DTargetObjId
                                        val targetObj = curCamSettings.scene3DObjects.firstOrNull { it.id == targetId }
                                        if (targetObj != null) {
                                            val fovScale = (tan(Math.toRadians(25.0)) / tan(Math.toRadians(evaluatedCameraKf.fovDegrees.coerceIn(18f, 120f) * 0.5)))
                                                .toFloat().coerceIn(0.35f, 2.6f)
                                            val stageFocal = max(containerWidth, containerHeight) * 0.85f * fovScale
                                            val stageVpCx = containerWidth * 0.5f + view3DPanX
                                            val stageVpCy = containerHeight * 0.50f + view3DPanY
                                            val stageProjScale = computeStage3DProjScale(containerWidth, containerHeight, projWidth, view3DZoom)
                                            val animTimeSec = effectiveTickFloat / max(1, project.fps).toFloat()
                                            val hit = Studio3DPaintEngine.pickObjectTriangleUV(
                                                obj = targetObj,
                                                frameIndex = currentTimelineTick,
                                                autoInterpolate = curCamSettings.autoInterpolate,
                                                screenX = motionEvent.x,
                                                screenY = motionEvent.y,
                                                camYawDeg = view3DYaw,
                                                camPitchDeg = view3DPitch,
                                                camRollDeg = 0f,
                                                camPanX = 0f,
                                                camPanY = 0f,
                                                focalPx = stageFocal,
                                                vpCx = stageVpCx,
                                                vpCy = stageVpCy,
                                                worldUnitScale = stageProjScale,
                                                tickFloat = effectiveTickFloat,
                                                animTimeSec = animTimeSec
                                            )
                                            if (hit != null) {
                                                val prev = paint3DLastUV ?: (hit.u to hit.v)
                                                curOnPaintSceneObjectUV!!.invoke(targetObj.id, prev.first, prev.second, hit.u, hit.v)
                                                paint3DLastUV = hit.u to hit.v
                                            }
                                        }
                                        return@pointerInteropFilter true
                                    }
                                }
                                if (isDraggingStage3DCam && curIsSideView3D) {
                                    val dx = motionEvent.x - lastOrbitTouchPos.x
                                    val dy = motionEvent.y - lastOrbitTouchPos.y
                                    val projStepScale = (projWidth / max(boxW, 300f) / view3DZoom.coerceAtLeast(0.35f)).coerceIn(0.4f, 4.5f)
                                    val isCamTarget = curStageSelection.type == StageTargetType.CAMERA || curStage3DMode == Stage3DInteractionMode.CAMERA_POV
                                    if (isCamTarget && curOnCameraDelta != null) {
                                        when (active3DGizmoAxis) {
                                            1 -> {
                                                val projAlong = dx * stage3DGizmo.xDirScreenX + dy * stage3DGizmo.xDirScreenY
                                                curOnCameraDelta!!.invoke(projAlong * projStepScale * 1.4f, 0f, 1f, 0f, 0f, 0f, 0f)
                                            }
                                            2 -> {
                                                val projAlong = dx * stage3DGizmo.yDirScreenX + dy * stage3DGizmo.yDirScreenY
                                                curOnCameraDelta!!.invoke(0f, projAlong * projStepScale * 1.4f, 1f, 0f, 0f, 0f, 0f)
                                            }
                                            3 -> {
                                                val projAlong = dx * stage3DGizmo.zDirScreenX + dy * stage3DGizmo.zDirScreenY
                                                val zFactor = (1f + projAlong * 0.006f).coerceIn(0.85f, 1.18f)
                                                curOnCameraDelta!!.invoke(0f, 0f, zFactor, 0f, 0f, 0f, 0f)
                                            }
                                            else -> {
                                                if (isShiftHeld || isMiddleMouse) {
                                                    val zFactor = (1f - dy * 0.005f).coerceIn(0.85f, 1.18f)
                                                    curOnCameraDelta!!.invoke(0f, 0f, zFactor, 0f, 0f, 0f, 0f)
                                                } else {
                                                    when (curCameraToolAction) {
                                                        CameraToolAction.PAN_2D ->
                                                            curOnCameraDelta!!.invoke(dx * projStepScale, dy * projStepScale, 1f, 0f, 0f, 0f, 0f)
                                                        CameraToolAction.ZOOM -> {
                                                            val zFactor = (1f - dy * 0.005f).coerceIn(0.85f, 1.18f)
                                                            curOnCameraDelta!!.invoke(0f, 0f, zFactor, 0f, 0f, 0f, 0f)
                                                        }
                                                        CameraToolAction.ROTATE_Z ->
                                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, dx * 0.35f, 0f, 0f, 0f)
                                                        CameraToolAction.FOV_LENS ->
                                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, 0f, -dy * 0.18f, 0f, 0f)
                                                        CameraToolAction.ORBIT_3D ->
                                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, 0f, 0f, dx * 0.25f, -dy * 0.25f)
                                                    }
                                                }
                                            }
                                        }
                                    } else if (!isCamTarget && curOnStageObjectDelta != null) {
                                        val objStep = 2.6f * projStepScale
                                        when (active3DGizmoAxis) {
                                            1 -> {
                                                val projAlong = dx * stage3DGizmo.xDirScreenX + dy * stage3DGizmo.xDirScreenY
                                                curOnStageObjectDelta!!.invoke(projAlong * objStep, 0f, 0f, 0f, 0f, 0f, 1f)
                                            }
                                            2 -> {
                                                val projAlong = dx * stage3DGizmo.yDirScreenX + dy * stage3DGizmo.yDirScreenY
                                                curOnStageObjectDelta!!.invoke(0f, projAlong * objStep, 0f, 0f, 0f, 0f, 1f)
                                            }
                                            3 -> {
                                                val projAlong = dx * stage3DGizmo.zDirScreenX + dy * stage3DGizmo.zDirScreenY
                                                curOnStageObjectDelta!!.invoke(0f, 0f, projAlong * objStep, 0f, 0f, 0f, 1f)
                                            }
                                            else -> {
                                                when (curCameraToolAction) {
                                                    CameraToolAction.PAN_2D ->
                                                        curOnStageObjectDelta!!.invoke(dx * objStep * 0.7f, dy * objStep * 0.7f, 0f, 0f, 0f, 0f, 1f)
                                                    CameraToolAction.ZOOM -> {
                                                        val sf = (1f - dy * 0.006f).coerceIn(0.85f, 1.18f)
                                                        curOnStageObjectDelta!!.invoke(0f, 0f, 0f, 0f, 0f, 0f, sf)
                                                    }
                                                    CameraToolAction.ROTATE_Z ->
                                                        curOnStageObjectDelta!!.invoke(0f, 0f, 0f, 0f, 0f, dx * 0.52f, 1f)
                                                    CameraToolAction.ORBIT_3D ->
                                                        curOnStageObjectDelta!!.invoke(0f, 0f, 0f, -dy * 0.52f, dx * 0.52f, 0f, 1f)
                                                    CameraToolAction.FOV_LENS ->
                                                        curOnStageObjectDelta!!.invoke(0f, 0f, -dy * objStep * 0.6f, 0f, 0f, 0f, 1f)
                                                }
                                            }
                                        }
                                    }
                                    lastOrbitTouchPos = Offset(motionEvent.x, motionEvent.y)
                                    return@pointerInteropFilter true
                                }
                                if (isOrbiting3D && curIsSideView3D) {
                                    val dx = motionEvent.x - lastOrbitTouchPos.x
                                    val dy = motionEvent.y - lastOrbitTouchPos.y
                                    if (isShiftHeld || isMiddleMouse) {
                                        view3DPanX += dx
                                        view3DPanY += dy
                                    } else {
                                        view3DYaw = (view3DYaw + dx * 0.42f) % 360f
                                        view3DPitch = (view3DPitch - dy * 0.36f).coerceIn(-78f, 78f)
                                    }
                                    lastOrbitTouchPos = Offset(motionEvent.x, motionEvent.y)
                                    return@pointerInteropFilter true
                                }
                                if (isMiddleMousePanning) {
                                    val dx = motionEvent.x - lastMiddlePanPos.x
                                    val dy = motionEvent.y - lastMiddlePanPos.y
                                    liveTf.panX += dx
                                    liveTf.panY += dy
                                    tileEngine.recordTransformDelta(dx, dy, 1f)
                                    curOnTransformChange(1.0f, dx, dy, 0f)
                                    lastMiddlePanPos = Offset(motionEvent.x, motionEvent.y)
                                    return@pointerInteropFilter true
                                }
                                if (isCameraCanvasPanning) {
                                    val dx = motionEvent.x - lastCamCanvasPanPos.x
                                    val dy = motionEvent.y - lastCamCanvasPanPos.y
                                    liveTf.panX += dx
                                    liveTf.panY += dy
                                    tileEngine.recordTransformDelta(dx, dy, 1f)
                                    curOnTransformChange(1.0f, dx, dy, 0f)
                                    lastCamCanvasPanPos = Offset(motionEvent.x, motionEvent.y)
                                    return@pointerInteropFilter true
                                }
                                if (isDraggingCameraTool && curIsCameraMode && curOnCameraDelta != null) {
                                    val dx = motionEvent.x - lastCameraDragPos.x
                                    val dy = motionEvent.y - lastCameraDragPos.y
                                    val scaleProj = projWidth / (boxW * liveTf.zoom.coerceAtLeast(0.05f))
                                    when (curCameraToolAction) {
                                        CameraToolAction.PAN_2D -> {
                                            curOnCameraDelta!!.invoke(dx * scaleProj, dy * scaleProj, 1f, 0f, 0f, 0f, 0f)
                                        }
                                        CameraToolAction.ZOOM -> {
                                            val zFactor = (1f - dy * 0.005f).coerceIn(0.85f, 1.18f)
                                            curOnCameraDelta!!.invoke(0f, 0f, zFactor, 0f, 0f, 0f, 0f)
                                        }
                                        CameraToolAction.ROTATE_Z -> {
                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, dx * 0.35f, 0f, 0f, 0f)
                                        }
                                        CameraToolAction.FOV_LENS -> {
                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, 0f, -dy * 0.18f, 0f, 0f)
                                        }
                                        CameraToolAction.ORBIT_3D -> {
                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, 0f, 0f, dx * 0.25f, -dy * 0.25f)
                                        }
                                    }
                                    lastCameraDragPos = Offset(motionEvent.x, motionEvent.y)
                                    return@pointerInteropFilter true
                                }
                                if (activePerspectiveDragIdx >= 0 && curOnPerspVpDrag != null) {
                                    curOnPerspVpDrag!!.invoke(
                                        activePerspectiveDragIdx,
                                        projPt.x / projWidth,
                                        projPt.y / projHeight
                                    )
                                    return@pointerInteropFilter true
                                }
                                if (isMovingVideo && curOnVideoTransform != null) {
                                    val dx = (projPt.x - lastVideoTouchProj.x).toDouble()
                                    val dy = (projPt.y - lastVideoTouchProj.y).toDouble()
                                    curOnVideoTransform!!.invoke(dx, dy, 1.0, 0.0)
                                    lastVideoTouchProj = projPt
                                    return@pointerInteropFilter true
                                }

                                if (curSelectedTool == ToolType.BEZIER_PEN && activeBezierDragIdx in workingBezierNodes.indices) {
                                    val node = workingBezierNodes[activeBezierDragIdx]
                                    workingBezierNodes[activeBezierDragIdx] = when (activeBezierDragPart) {
                                        1 -> {
                                            val dx = projPt.x - node.anchorX
                                            val dy = projPt.y - node.anchorY
                                            node.copy(
                                                anchorX = projPt.x,
                                                anchorY = projPt.y,
                                                handleInX = node.handleInX + dx,
                                                handleInY = node.handleInY + dy,
                                                handleOutX = node.handleOutX + dx,
                                                handleOutY = node.handleOutY + dy
                                            )
                                        }
                                        2 -> node.copy(
                                            handleInX = projPt.x,
                                            handleInY = projPt.y,
                                            handleOutX = 2f * node.anchorX - projPt.x,
                                            handleOutY = 2f * node.anchorY - projPt.y
                                        )
                                        3, 4 -> node.copy(
                                            handleOutX = projPt.x,
                                            handleOutY = projPt.y,
                                            handleInX = 2f * node.anchorX - projPt.x,
                                            handleInY = 2f * node.anchorY - projPt.y
                                        )
                                        else -> node
                                    }
                                    curOnBezierChange?.invoke(workingBezierNodes.toList())
                                    activeStrokeVersion++
                                    return@pointerInteropFilter true
                                }

                                if (isMovingSelection) {
                                    val dx = projPt.x - lastSelTouchPos.x
                                    val dy = projPt.y - lastSelTouchPos.y
                                    curOnSelOffset?.invoke(dx, dy)
                                    lastSelTouchPos = projPt
                                    return@pointerInteropFilter true
                                }

                                if (isDrawingActive) {
                                    val historySize = motionEvent.historySize
                                    for (h in 0 until historySize) {
                                        val hRaw = screenToProjectCoords(motionEvent.getHistoricalX(h), motionEvent.getHistoricalY(h))
                                        val hInv = applyTabletInversion(hRaw.x, hRaw.y)
                                        val hProj = applyPerspectiveSnapIfEnabled(hInv)
                                        val hp = if (curSelectedTool == ToolType.ERASER && !curEraserPressure) {
                                            1.0f
                                        } else {
                                            mapPressureForEvent(motionEvent, motionEvent.getHistoricalPressure(h))
                                        }
                                        if (shouldAcceptStrokePoint(hProj.x, hProj.y, hp)) {
                                            currentPoints.add(StrokePoint(hProj.x, hProj.y, hp))
                                        }
                                    }
                                    val snappedCur = applyPerspectiveSnapIfEnabled(projPt)
                                    if (shouldAcceptStrokePoint(snappedCur.x, snappedCur.y, rawPressure)) {
                                        currentPoints.add(StrokePoint(snappedCur.x, snappedCur.y, rawPressure))
                                    }
                                    flushIncrementalRaster()
                                    activeStrokeVersion++

                                    // Stream throttled live stroke preview to LAN peers (~25Hz with stable stroke ID)
                                    if (curOnLivePreview != null &&
                                        curSelectedTool != ToolType.LASSO &&
                                        curSelectedTool != ToolType.EYEDROPPER &&
                                        curSelectedTool != ToolType.BUCKET &&
                                        curSelectedTool != ToolType.MAGIC_WAND
                                    ) {
                                        val nowPreview = android.os.SystemClock.uptimeMillis()
                                        if (nowPreview - lastLiveStrokeBroadcastMs >= 40L && currentPoints.isNotEmpty()) {
                                            lastLiveStrokeBroadcastMs = nowPreview
                                            curOnLivePreview!!.invoke(
                                                DrawingStroke(
                                                    id = activeStrokeId.ifBlank { UUID.randomUUID().toString() },
                                                    tool = curSelectedTool,
                                                    color = curColor,
                                                    brushSize = curEffToolSize,
                                                    opacity = curBrushOpacity,
                                                    points = currentPoints.toList(),
                                                    brushPresetId = if (curSelectedTool == ToolType.ERASER) curEraserPresetId else curBrushPresetId,
                                                    customBrushParams = if (curSelectedTool == ToolType.ERASER) null else curBrushParamsJson,
                                                    clipPolygon = curActiveSelClip,
                                                    clipInverted = curActiveSelClip != null && curActiveSel.isInverted,
                                                    clipBoundsWidth = projWidth,
                                                    clipBoundsHeight = projHeight
                                                )
                                            )
                                        }
                                    }
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (isPaintingStage3DObject) {
                                    isPaintingStage3DObject = false
                                    paint3DTargetObjId = null
                                    paint3DLastUV = null
                                    return@pointerInteropFilter true
                                }
                                if (isDraggingStage3DCam || isOrbiting3D) {
                                    val wasTap = motionEvent.actionMasked == MotionEvent.ACTION_UP &&
                                        !stage3DMovedPastSlop &&
                                        curStage3DMode != Stage3DInteractionMode.CAMERA_POV
                                    isDraggingStage3DCam = false
                                    isOrbiting3D = false
                                    active3DGizmoAxis = 0
                                    if (wasTap && curOnStageTargetSelected != null) {
                                        val tx = motionEvent.x
                                        val ty = motionEvent.y
                                        val fovScale = (tan(Math.toRadians(25.0)) / tan(Math.toRadians(evaluatedCameraKf.fovDegrees.coerceIn(18f, 120f) * 0.5)))
                                            .toFloat().coerceIn(0.35f, 2.6f)
                                        val stageFocal = max(containerWidth, containerHeight) * 0.85f * fovScale
                                        val stageVpCx = containerWidth * 0.5f + view3DPanX
                                        val stageVpCy = containerHeight * 0.50f + view3DPanY
                                        val stageProjScale = computeStage3DProjScale(containerWidth, containerHeight, projWidth, view3DZoom)
                                        val animTimeSec = effectiveTickFloat / max(1, project.fps).toFloat()

                                        val exactObjId = Camera3DEngine.pickStageObject3DAtScreenPoint(
                                            sceneObjects = curCamSettings.scene3DObjects,
                                            frameIndex = currentTimelineTick,
                                            autoInterpolate = curCamSettings.autoInterpolate,
                                            screenX = tx,
                                            screenY = ty,
                                            camYawDeg = view3DYaw,
                                            camPitchDeg = view3DPitch,
                                            camRollDeg = 0f,
                                            camPanX = 0f,
                                            camPanY = 0f,
                                            focalPx = stageFocal,
                                            vpCx = stageVpCx,
                                            vpCy = stageVpCy,
                                            worldUnitScale = stageProjScale,
                                            tickFloat = effectiveTickFloat,
                                            animTimeSec = animTimeSec
                                        )

                                        val camApexHitR = 32f * density.density
                                        val hitCam = stage3DGizmo.valid &&
                                            hypot(tx - stage3DGizmo.apexX, ty - stage3DGizmo.apexY) <= camApexHitR
                                        when {
                                            hitCam -> curOnStageTargetSelected!!.invoke(StageSelection(StageTargetType.CAMERA, "camera"))
                                            exactObjId != null -> curOnStageTargetSelected!!.invoke(StageSelection(StageTargetType.OBJECT_3D, exactObjId))
                                            else -> {
                                                val pad = 16f * density.density
                                                val hitObj = stage3DObjectBounds.values
                                                    .filter { b -> tx in (b.minX - pad)..(b.maxX + pad) && ty in (b.minY - pad)..(b.maxY + pad) }
                                                    .minByOrNull { it.avgDepth }
                                                if (hitObj != null) {
                                                    curOnStageTargetSelected!!.invoke(StageSelection(StageTargetType.OBJECT_3D, hitObj.id))
                                                } else {
                                                    val hitLayerId = stage3DLayerBounds.entries.lastOrNull { (_, r) ->
                                                        tx in (r.left - pad)..(r.right + pad) && ty in (r.top - pad)..(r.bottom + pad)
                                                    }?.key
                                                    if (hitLayerId != null) {
                                                        curOnStageTargetSelected!!.invoke(StageSelection(StageTargetType.LAYER, hitLayerId))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    return@pointerInteropFilter true
                                }
                                if (isMiddleMousePanning) {
                                    isMiddleMousePanning = false
                                    return@pointerInteropFilter true
                                }
                                if (isCameraCanvasPanning) {
                                    isCameraCanvasPanning = false
                                    return@pointerInteropFilter true
                                }
                                if (isDraggingCameraTool) {
                                    isDraggingCameraTool = false
                                    return@pointerInteropFilter true
                                }
                                if (activePerspectiveDragIdx >= 0) {
                                    activePerspectiveDragIdx = -1
                                    return@pointerInteropFilter true
                                }
                                if (isMovingVideo) {
                                    isMovingVideo = false
                                    return@pointerInteropFilter true
                                }
                                if (curSelectedTool == ToolType.BEZIER_PEN) {
                                    activeBezierDragIdx = -1
                                    activeBezierDragPart = 0
                                    return@pointerInteropFilter true
                                }
                                if (isMovingSelection) {
                                    isMovingSelection = false
                                    return@pointerInteropFilter true
                                }

                                if (isDrawingActive) {
                                    val finalPt = applyPerspectiveSnapIfEnabled(projPt)
                                    currentPoints.add(StrokePoint(finalPt.x, finalPt.y, rawPressure))
                                    curOnLivePreview?.invoke(null)
                                    if (curSelectedTool == ToolType.LASSO) {
                                        curOnLasso?.invoke(currentPoints.toList())
                                    } else if (curSelectedTool != ToolType.EYEDROPPER &&
                                        curSelectedTool != ToolType.BUCKET &&
                                        curSelectedTool != ToolType.MAGIC_WAND &&
                                        currentPoints.isNotEmpty()
                                    ) {
                                        val completedStroke = DrawingStroke(
                                            id = activeStrokeId.ifBlank { UUID.randomUUID().toString() },
                                            tool = curSelectedTool,
                                            color = curColor,
                                            brushSize = curEffToolSize,
                                            opacity = curBrushOpacity,
                                            points = currentPoints.toList(),
                                            brushPresetId = if (curSelectedTool == ToolType.ERASER) curEraserPresetId else curBrushPresetId,
                                            customBrushParams = if (curSelectedTool == ToolType.ERASER) null else curBrushParamsJson,
                                            clipPolygon = curActiveSelClip,
                                            clipInverted = curActiveSelClip != null && curActiveSel.isInverted,
                                            clipBoundsWidth = projWidth,
                                            clipBoundsHeight = projHeight
                                        )
                                        val canCommitLocally = curActiveLayer?.isLocked != true && curActiveLayer?.isVisible != false
                                        if (canCommitLocally) {
                                            val prevHash = curActiveSpecHash
                                            val optimisticLayers = if (curEffectiveActiveSlice.isNotEmpty()) {
                                                val lastIdx = curEffectiveActiveSlice.lastIndex
                                                curEffectiveActiveSlice.mapIndexed { idx, l ->
                                                    if (idx == lastIdx) l.copy(strokes = l.strokes + completedStroke) else l
                                                }
                                            } else null
                                            val activePlanForStamp = latestTilePlanRef.get() ?: run {
                                                syncProjectionMatrices()
                                                val sppNow = (boxW / projWidth) * liveTf.zoom.coerceAtLeast(0.05f)
                                                tileEngine.computeTilePlan(projToScreenMatrix, containerWidth, containerHeight, sppNow)?.also {
                                                    latestTilePlanRef.set(it)
                                                }
                                            }
                                            tileEngine.stampStrokeIntoCachedTiles(
                                                sliceTag = "active",
                                                contentHash = prevHash,
                                                stroke = completedStroke,
                                                densityMult = densityMult,
                                                layers = optimisticLayers,
                                                projWidth = project.resolution.width,
                                                projHeight = project.resolution.height,
                                                activePlan = activePlanForStamp
                                            )
                                            val prevFb = optimisticActiveFallbackRef.get()
                                            val chainedHashes = (listOf(prevHash) +
                                                (prevFb?.takeIf { it.layerId == curActiveLayer?.id }?.fallbackHashes ?: emptyList()))
                                                .distinct()
                                                .take(4)
                                            optimisticActiveFallbackRef.set(
                                                OptimisticActiveFallback(
                                                    layerId = curActiveLayer?.id,
                                                    fallbackHashes = chainedHashes,
                                                    strokeId = completedStroke.id,
                                                    seenInVm = false
                                                )
                                            )
                                            curOnStrokeCompleted(completedStroke)
                                        }
                                    }
                                    currentPoints.clear()
                                    isDrawingActive = false
                                    lockedPerspectiveTargetVpIdx = -1
                                    cacheHolder.endActiveSessions()
                                    activeStrokeVersion++
                                }
                                true
                            }
                            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                                curOnFocusActive?.invoke()
                                val hoverRaw = screenToProjectCoords(motionEvent.x, motionEvent.y)
                                val hoverProj = applyTabletInversion(hoverRaw.x, hoverRaw.y)
                                if (!curIsSideView3D) {
                                    cursorScreenPos = Offset(motionEvent.x, motionEvent.y)
                                }
                                if (curOnLocalCursor != null && !curIsSideView3D) {
                                    val nowH = android.os.SystemClock.uptimeMillis()
                                    val dxB = hoverProj.x - lastBroadcastX
                                    val dyB = hoverProj.y - lastBroadcastY
                                    if (nowH - lastCursorBroadcastMs >= 33L && (dxB * dxB + dyB * dyB) > 2.25f) {
                                        lastCursorBroadcastMs = nowH
                                        lastBroadcastX = hoverProj.x
                                        lastBroadcastY = hoverProj.y
                                        lastBroadcastDrawing = false
                                        curOnLocalCursor!!.invoke(hoverProj.x, hoverProj.y, false)
                                    }
                                }
                                true
                            }
                            MotionEvent.ACTION_HOVER_EXIT -> {
                                cursorScreenPos = null
                                true
                            }
                            MotionEvent.ACTION_SCROLL -> {
                                curOnFocusActive?.invoke()
                                val vScroll = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                if (vScroll != 0f) {
                                    val isAltHeld = (motionEvent.metaState and android.view.KeyEvent.META_ALT_ON) != 0
                                    if (curIsSideView3D) {
                                        if (isAltHeld && curOnCameraDelta != null) {
                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, 0f, if (vScroll > 0f) -2.5f else 2.5f, 0f, 0f)
                                        } else if ((curStage3DMode == Stage3DInteractionMode.MOVE_CAMERA ||
                                                curStage3DMode == Stage3DInteractionMode.CAMERA_POV) &&
                                            curOnCameraDelta != null
                                        ) {
                                            val camZFactor = if (vScroll > 0f) 1.08f else 0.92f
                                            curOnCameraDelta!!.invoke(0f, 0f, camZFactor, 0f, 0f, 0f, 0f)
                                        } else {
                                            val zF = if (vScroll > 0f) 1.12f else 0.89f
                                            view3DZoom = (view3DZoom * zF).coerceIn(0.35f, 4.0f)
                                        }
                                        return@pointerInteropFilter true
                                    }
                                    if (curIsCameraMode &&
                                        curCamSettings.isEnabled &&
                                        curCamNavTarget == CameraViewportNavTarget.CAMERA &&
                                        curOnCameraDelta != null
                                    ) {
                                        if (isAltHeld) {
                                            curOnCameraDelta!!.invoke(0f, 0f, 1f, 0f, if (vScroll > 0f) -2.5f else 2.5f, 0f, 0f)
                                        } else {
                                            val zF = if (vScroll > 0f) 1.09f else 0.91f
                                            curOnCameraDelta!!.invoke(0f, 0f, zF, 0f, 0f, 0f, 0f)
                                        }
                                        return@pointerInteropFilter true
                                    }
                                    if (curTabletSettings.mouseScrollZoomEnabled) {
                                        val factor = if (vScroll > 0f) 1.12f else 0.88f
                                        val curZoom = liveTf.zoom.coerceAtLeast(0.05f)
                                        val nextZoom = (curZoom * factor).coerceIn(0.15f, 48.0f)
                                        val effFactor = nextZoom / curZoom
                                        val vx = motionEvent.x - (halfContainerW + liveTf.panX)
                                        val vy = motionEvent.y - (halfContainerH + liveTf.panY)
                                        val compX = vx * (1f - effFactor)
                                        val compY = vy * (1f - effFactor)
                                        liveTf.zoom = nextZoom
                                        liveTf.panX += compX
                                        liveTf.panY += compY
                                        tileEngine.recordTransformDelta(compX, compY, effFactor)
                                        curOnTransformChange(effFactor, compX, compY, 0f)
                                        return@pointerInteropFilter true
                                    }
                                }
                                false
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val effectiveFloatingRefBelow = (referenceImage.isVisible && !referenceImage.renderAboveCanvas) || isFloatingRefBelowCanvas
            if (isCameraSideView3D) {
                // Full 3D Stage Viewport replacing the 2D paper canvas (shows Studio Sky, 3D Perspective Floor Grid, Layered Z Cards, Interactive 3D Camera & POV Mode)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("camera_3d_stage_viewport")
                ) {
                    val trigger = activeStrokeVersion
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    if (stage3DMode == Stage3DInteractionMode.CAMERA_POV) {
                        stage3DGizmo.valid = false
                        drawCameraPovStageViewport(
                            nativeCanvas = nativeCanvas,
                            canvasW = size.width,
                            canvasH = size.height,
                            screenDensity = density.density,
                            camSettings = camSettings,
                            evaluatedKf = evaluatedCameraKf,
                            bgSettings = project.canvasBackground,
                            povBitmap = cacheHolder.stage3DPovBitmap,
                            scratch = overlayScratch
                        )
                    } else {
                        drawInteractive3DStageViewport(
                            nativeCanvas = nativeCanvas,
                            canvasW = size.width,
                            canvasH = size.height,
                            projWidth = projWidth,
                            projHeight = projHeight,
                            screenDensity = density.density,
                            camSettings = camSettings,
                            evaluatedKf = evaluatedCameraKf,
                            bgSettings = project.canvasBackground,
                            bgBitmap = cacheHolder.stage3DBgBitmap,
                            layers = combinedLayers,
                            layerBitmaps = cacheHolder.stage3DLayerBitmaps,
                            offCanvasLayerBitmaps = cacheHolder.stage3DOffCanvasBitmaps,
                            objectEntries = cacheHolder.stage3DObjectEntries,
                            view3DYaw = view3DYaw,
                            view3DPitch = view3DPitch,
                            view3DPanX = view3DPanX,
                            view3DPanY = view3DPanY,
                            view3DZoom = view3DZoom,
                            accentColorInt = themeAccentColor.toInt(),
                            isMoveCameraMode = stage3DMode == Stage3DInteractionMode.MOVE_CAMERA,
                            stageSelection = stageSelection,
                            gizmoOut = stage3DGizmo,
                            objectBoundsOut = stage3DObjectBounds,
                            layerBoundsOut = stage3DLayerBounds,
                            scratch = overlayScratch,
                            tickFloat = effectiveTickFloat
                        )
                    }
                    @Suppress("UNUSED_EXPRESSION")
                    trigger
                }
            } else {
                // Full-screen off-canvas backdrop (renders Sky + pre-rasterized 3D Objects aligned with active camera)
                if (isCameraModeActive || camSettings.isEnabled) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_offcanvas_backdrop")
                    ) {
                        val trigger = activeStrokeVersion
                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        KodanimaGraphicsEngine.drawSkyBackdropToCanvas(
                            nativeCanvas,
                            size.width,
                            size.height,
                            project.canvasBackground
                        )
                        val objBmp = cacheHolder.offCanvas3DBitmap
                        if (camSettings.scene3DObjects.any { it.isVisible } && objBmp != null && !objBmp.isRecycled) {
                            overlayScratch.srcRect.set(0, 0, objBmp.width, objBmp.height)
                            overlayScratch.dstRectF.set(0f, 0f, size.width, size.height)
                            overlayScratch.bitmapPaint.isFilterBitmap = true
                            nativeCanvas.drawBitmap(objBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                        }
                        @Suppress("UNUSED_EXPRESSION")
                        trigger
                    }
                }

                // Layer 0: Paper sheet backdrop (shadow, paper tint/texture/3D backdrop & synced video reference)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = panX
                            translationY = panY
                            scaleX = if (mirrorHorizontal) -zoom else zoom
                            scaleY = zoom
                            this.rotationZ = rotation
                        }
                        .size(
                            width = with(density) { boxWidthPx.toDp() },
                            height = with(density) { boxHeightPx.toDp() }
                        )
                        .shadow(if (isCameraModeActive) 6.dp else 16.dp, RectangleShape)
                        .clip(RectangleShape)
                        .background(Color.Transparent)
                ) {
                    if (referenceImage.isVisible && !referenceImage.renderAboveCanvas) {
                        FloatingReferenceImageSurface(
                            refState = referenceImage,
                            theme = theme,
                            onTransformDelta = { dx, dy, z, r ->
                                onFloatingRefTransformDelta?.invoke(dx, dy, z, r)
                            }
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("drawing_canvas_paper")
                    ) {
                        val trigger = activeStrokeVersion
                        val nativeCanvas = drawContext.canvas.nativeCanvas
                        val screenPxPerProjPx = (size.width / projWidth) * zoom
                        overlayScratch.bitmapPaint.isFilterBitmap = screenPxPerProjPx < 4.0f
                        overlayScratch.dstRectF.set(0f, 0f, size.width, size.height)

                        val isBgTransparent = project.canvasBackground.style == CanvasBackgroundStyle.TRANSPARENTE
                        val show3DBg = project.canvasBackground.show3DInCanvas
                        if (!effectiveFloatingRefBelow) {
                            if (show3DBg) {
                                val bg3DBmp = when (project.canvasBackground.canvas3DRenderMode) {
                                    Canvas3DBackgroundRenderMode.STATIC_2D_CACHE -> cacheHolder.canvas3DBgStaticBitmap
                                    Canvas3DBackgroundRenderMode.PRE_RENDER_ON_PLAY ->
                                        cacheHolder.canvas3DBgPlayFrames[currentTimelineTick]
                                            ?: cacheHolder.canvas3DBgStaticBitmap
                                            ?: cacheHolder.canvas3DBgPlayFrames.values.lastOrNull()
                                    Canvas3DBackgroundRenderMode.REALTIME_3D ->
                                        cacheHolder.canvas3DBgRealtimeBitmap ?: cacheHolder.canvas3DBgStaticBitmap
                                }
                                if (bg3DBmp != null && !bg3DBmp.isRecycled && bg3DBmp.width > 0 && bg3DBmp.height > 0) {
                                    overlayScratch.srcRect.set(0, 0, bg3DBmp.width, bg3DBmp.height)
                                    nativeCanvas.drawBitmap(bg3DBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                                } else {
                                    KodanimaGraphicsEngine.drawSkyBackdropToCanvas(
                                        nativeCanvas, size.width, size.height, project.canvasBackground
                                    )
                                }
                            } else if (isBgTransparent) {
                                if (!isCameraModeActive && !camSettings.isEnabled) {
                                    drawTransparencyCheckerboard(nativeCanvas, size.width, size.height, density.density, overlayScratch)
                                }
                            } else {
                                KodanimaGraphicsEngine.drawProjectBackgroundToCanvas(
                                    canvas = nativeCanvas,
                                    width = size.width,
                                    height = size.height,
                                    bgSettings = project.canvasBackground,
                                    drawSkyWhenTransparent = false
                                )
                            }
                        }

                        // Video / GIF Reference Layer synchronized to current frame / scrub timestamp
                        val vBmp = videoFrameBitmap
                        if (videoAsset != null && vBmp != null && !vBmp.isRecycled && vBmp.width > 0 && vBmp.height > 0) {
                            val scaleFactor = (videoAsset.scalePercent / 100.0).toFloat().coerceIn(0.1f, 5f)
                            val fitScale = min(size.width / vBmp.width.toFloat(), size.height / vBmp.height.toFloat())
                            val drawW = vBmp.width * fitScale * scaleFactor
                            val drawH = vBmp.height * fitScale * scaleFactor
                            val cx = size.width * 0.5f + videoAsset.offsetX.toFloat() * (size.width / projWidth)
                            val cy = size.height * 0.5f + videoAsset.offsetY.toFloat() * (size.height / projHeight)

                            nativeCanvas.save()
                            nativeCanvas.translate(cx, cy)
                            if (videoAsset.rotationDegrees != 0.0) {
                                nativeCanvas.rotate(videoAsset.rotationDegrees.toFloat())
                            }
                            overlayScratch.srcRect.set(0, 0, vBmp.width, vBmp.height)
                            overlayScratch.dstRectF.set(-drawW * 0.5f, -drawH * 0.5f, drawW * 0.5f, drawH * 0.5f)
                            overlayScratch.videoBitmapPaint.alpha =
                                ((videoAsset.opacityPercent.coerceIn(0, 100) / 100f) * 255f).roundToInt()
                            nativeCanvas.drawBitmap(vBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.videoBitmapPaint)
                            nativeCanvas.restore()
                        }
                        @Suppress("UNUSED_EXPRESSION")
                        trigger
                    }
                }

                // Layer 1: GPU-Accelerated World-Space Tile Compositing (RenderNode DisplayLists + Predictive Pan + Zoom Culling)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("drawing_canvas_static")
                ) {
                    val trigger = activeStrokeVersion
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val safeZ = zoom.coerceAtLeast(0.05f)
                    val liveMat = overlayScratch.liveMatrixScratch.apply {
                        reset()
                        setScale(boxW / projWidth, boxH / projHeight)
                        postTranslate(-boxW * 0.5f, -boxH * 0.5f)
                        postScale(if (mirrorHorizontal) -safeZ else safeZ, safeZ)
                        postRotate(rotation)
                        postTranslate(halfContainerW + panX, halfContainerH + panY)
                    }
                    val screenPxPerProjPx = (boxW / projWidth) * safeZ
                    overlayScratch.bitmapPaint.isFilterBitmap = screenPxPerProjPx < 4.0f
                    val livePlan = tileEngine.computeTilePlan(
                        projToScreenMatrix = liveMat,
                        containerWidth = size.width,
                        containerHeight = size.height,
                        screenPxPerProjPx = screenPxPerProjPx
                    )
                    if (livePlan != null) {
                        latestTilePlanRef.set(livePlan)
                        if (tileEngine.hasMissingTiles(livePlan, latestTileSlicesRef.get())) {
                            tileBuildTrigger.trySend(Unit)
                        }
                    }

                    val isBgTransparent = project.canvasBackground.style == CanvasBackgroundStyle.TRANSPARENTE
                    val show3DBg = project.canvasBackground.show3DInCanvas
                    val activeIsClip = activeLayerObj?.isClippingMask == true
                    val activeBlend = activeLayerObj?.blendEffect ?: LayerBlendEffect.NORMAL
                    val needsLayerStackBlend = activeIsClip ||
                        activeBlend != LayerBlendEffect.NORMAL ||
                        aboveNeedsAdvanced

                    var layerStackSaveCount = -1
                    if (needsLayerStackBlend && (effectiveFloatingRefBelow || isBgTransparent || show3DBg)) {
                        layerStackSaveCount = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                    }

                    if (livePlan != null) {
                        if (onionSlicesData.settings.isEnabled) {
                            tileEngine.drawSliceTiles(
                                canvas = nativeCanvas,
                                liveProjToScreen = liveMat,
                                plan = livePlan,
                                sliceTag = "onion",
                                contentHash = onionSlicesData.contentHash
                            )
                        }
                        if (belowSpec.layers.isNotEmpty()) {
                            tileEngine.drawSliceTiles(
                                canvas = nativeCanvas,
                                liveProjToScreen = liveMat,
                                plan = livePlan,
                                sliceTag = "below",
                                contentHash = belowSpec.contentHash
                            )
                        }
                    }

                    if (gridRulerSettings.isEnabled && gridRulerSettings.renderBehindActiveLayer) {
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        drawGridRulerCanvas(
                            nativeCanvas = nativeCanvas,
                            canvasScaleX = boxW / projWidth,
                            canvasScaleY = boxH / projHeight,
                            projWidth = projWidth,
                            projHeight = projHeight,
                            screenDensity = density.density,
                            zoom = zoom,
                            settings = gridRulerSettings,
                            scratch = overlayScratch,
                            applyScale = false
                        )
                        nativeCanvas.restore()
                    }
                    if (perspectiveRulerSettings.isEnabled && perspectiveRulerSettings.renderBehindActiveLayer) {
                        val activeSnapOrigin = if (isDrawingActive && perspectiveRulerSettings.snapToGuide) {
                            currentPoints.firstOrNull()?.let { Offset(it.x, it.y) }
                        } else null
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        drawPerspectiveRulerCanvas(
                            nativeCanvas = nativeCanvas,
                            canvasScaleX = boxW / projWidth,
                            canvasScaleY = boxH / projHeight,
                            projWidth = projWidth,
                            projHeight = projHeight,
                            screenDensity = density.density,
                            zoom = zoom,
                            settings = perspectiveRulerSettings,
                            activeSnapOrigin = activeSnapOrigin,
                            lockedSnapVpIdx = lockedPerspectiveTargetVpIdx,
                            scratch = overlayScratch,
                            applyScale = false
                        )
                        nativeCanvas.restore()
                    }

                    // Active layer (isolated viewport eraser buffer when erasing, or cached world-space GPU tiles)
                    val isUsingEraserBuf = isDrawingActive && selectedTool == ToolType.ERASER &&
                        cacheHolder.eraserWorkBitmap != null && !cacheHolder.eraserWorkBitmap!!.isRecycled
                    val activeFallbackHashes = optimisticActiveFallbackRef.get()?.takeIf {
                        it.layerId == activeLayerObj?.id
                    }?.fallbackHashes ?: emptyList()

                    if (activeLayerObj?.isVisible != false) {
                        if (isUsingEraserBuf) {
                            val activeBmp = cacheHolder.eraserWorkBitmap!!
                            val invSess = overlayScratch.invMatrixScratch
                            val hasSessDelta = cacheHolder.activeSessionMatrix.invert(invSess)
                            val activeMat = overlayScratch.vpMatrixScratch
                            if (hasSessDelta) activeMat.setConcat(liveMat, invSess) else activeMat.reset()
                            val invVpScale = 1f / cacheHolder.activeSessionVpScale.coerceAtLeast(0.01f)
                            val marginScreen = cacheHolder.activeSessionMarginPx * invVpScale
                            overlayScratch.srcRect.set(0, 0, activeBmp.width, activeBmp.height)
                            overlayScratch.dstRectF.set(
                                -marginScreen,
                                -marginScreen,
                                activeBmp.width * invVpScale - marginScreen,
                                activeBmp.height * invVpScale - marginScreen
                            )

                            nativeCanvas.save()
                            if (hasSessDelta) nativeCanvas.concat(activeMat)
                            if (!activeIsClip && activeBlend == LayerBlendEffect.NORMAL) {
                                nativeCanvas.drawBitmap(activeBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                            } else if (!activeIsClip) {
                                overlayScratch.layerBlendPaint.alpha = 255
                                KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, activeBlend)
                                nativeCanvas.drawBitmap(activeBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.layerBlendPaint)
                            } else if (clipBaseSpec != null && livePlan != null) {
                                nativeCanvas.restore()
                                overlayScratch.layerBlendPaint.alpha = 255
                                KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, activeBlend)
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                                nativeCanvas.save()
                                if (hasSessDelta) nativeCanvas.concat(activeMat)
                                nativeCanvas.drawBitmap(activeBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                                nativeCanvas.restore()
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = livePlan,
                                    sliceTag = "clipBase",
                                    contentHash = clipBaseSpec.contentHash,
                                    paintOverride = overlayScratch.clipDstInPaint
                                )
                                nativeCanvas.restoreToCount(sc)
                                nativeCanvas.save()
                            }
                            nativeCanvas.restore()
                        } else if (livePlan != null && activeSpec.layers.isNotEmpty()) {
                            if (!activeIsClip && activeBlend == LayerBlendEffect.NORMAL) {
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = livePlan,
                                    sliceTag = "active",
                                    contentHash = activeSpec.contentHash,
                                    fallbackContentHashes = activeFallbackHashes
                                )
                            } else if (!activeIsClip) {
                                overlayScratch.layerBlendPaint.alpha = 255
                                KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, activeBlend)
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = livePlan,
                                    sliceTag = "active",
                                    contentHash = activeSpec.contentHash,
                                    fallbackContentHashes = activeFallbackHashes
                                )
                                nativeCanvas.restoreToCount(sc)
                            } else if (clipBaseSpec != null) {
                                overlayScratch.layerBlendPaint.alpha = 255
                                KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, activeBlend)
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = livePlan,
                                    sliceTag = "active",
                                    contentHash = activeSpec.contentHash,
                                    fallbackContentHashes = activeFallbackHashes
                                )
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = livePlan,
                                    sliceTag = "clipBase",
                                    contentHash = clipBaseSpec.contentHash,
                                    paintOverride = overlayScratch.clipDstInPaint
                                )
                                nativeCanvas.restoreToCount(sc)
                            }
                        }
                    }

                    // Above layers
                    if (livePlan != null) {
                        if (!aboveNeedsAdvanced && aboveSimpleSpec != null) {
                            tileEngine.drawSliceTiles(
                                canvas = nativeCanvas,
                                liveProjToScreen = liveMat,
                                plan = livePlan,
                                sliceTag = "above",
                                contentHash = aboveSimpleSpec.contentHash
                            )
                        } else if (aboveNeedsAdvanced) {
                            var lastNonClippedSpec: TileSliceSpec.LayersSlice? = when {
                                activeLayerObj != null && !activeLayerObj.isClippingMask && activeLayerObj.isVisible && activeLayerObj.opacity > 0f -> activeSpec
                                activeLayerObj != null && activeLayerObj.isClippingMask -> clipBaseSpec
                                else -> null
                            }
                            aboveAdvancedSpecs.forEach { (advLayer, advSpec) ->
                                if (!advLayer.isClippingMask) {
                                    if (!advLayer.isVisible || advLayer.opacity <= 0f) {
                                        lastNonClippedSpec = null
                                    } else {
                                        lastNonClippedSpec = advSpec
                                        if (advLayer.blendEffect == LayerBlendEffect.NORMAL) {
                                            tileEngine.drawSliceTiles(
                                                canvas = nativeCanvas,
                                                liveProjToScreen = liveMat,
                                                plan = livePlan,
                                                sliceTag = advSpec.sliceTag,
                                                contentHash = advSpec.contentHash
                                            )
                                        } else {
                                            overlayScratch.layerBlendPaint.alpha = 255
                                            KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, advLayer.blendEffect)
                                            val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                                            tileEngine.drawSliceTiles(
                                                canvas = nativeCanvas,
                                                liveProjToScreen = liveMat,
                                                plan = livePlan,
                                                sliceTag = advSpec.sliceTag,
                                                contentHash = advSpec.contentHash
                                            )
                                            nativeCanvas.restoreToCount(sc)
                                        }
                                    }
                                } else if (advLayer.isVisible && advLayer.opacity > 0f && lastNonClippedSpec != null) {
                                    val maskSpec = lastNonClippedSpec!!
                                    overlayScratch.layerBlendPaint.alpha = 255
                                    KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, advLayer.blendEffect)
                                    val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                                    tileEngine.drawSliceTiles(
                                        canvas = nativeCanvas,
                                        liveProjToScreen = liveMat,
                                        plan = livePlan,
                                        sliceTag = advSpec.sliceTag,
                                        contentHash = advSpec.contentHash
                                    )
                                    tileEngine.drawSliceTiles(
                                        canvas = nativeCanvas,
                                        liveProjToScreen = liveMat,
                                        plan = livePlan,
                                        sliceTag = maskSpec.sliceTag,
                                        contentHash = maskSpec.contentHash,
                                        paintOverride = overlayScratch.clipDstInPaint
                                    )
                                    nativeCanvas.restoreToCount(sc)
                                }
                            }
                        }
                    }

                    if (layerStackSaveCount != -1) {
                        nativeCanvas.restoreToCount(layerStackSaveCount)
                    }
                    @Suppress("UNUSED_EXPRESSION")
                    trigger
                }

                // Layer 2: Dedicated incremental unclipped active stroke, selection & rulers overlay
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("drawing_canvas_active")
                ) {
                    val trigger = activeStrokeVersion
                    val canvasScaleX = boxW / projWidth
                    val canvasScaleY = boxH / projHeight
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val safeZ = zoom.coerceAtLeast(0.05f)
                    val liveMat = overlayScratch.liveMatrixScratch.apply {
                        reset()
                        setScale(canvasScaleX, canvasScaleY)
                        postTranslate(-boxW * 0.5f, -boxH * 0.5f)
                        postScale(if (mirrorHorizontal) -safeZ else safeZ, safeZ)
                        postRotate(rotation)
                        postTranslate(halfContainerW + panX, halfContainerH + panY)
                    }
                    val activeIsClip = activeLayerObj?.isClippingMask == true
                    val activeClipPlan = if (activeIsClip && clipBaseSpec != null) latestTilePlanRef.get() else null

                    // Render captured strokes of an active selection BEFORE the live stroke
                    val hideCapturedForLiveEraser = isDrawingActive && selectedTool == ToolType.ERASER
                    if (activeSelection.isActive && activeSelection.capturedStrokes.isNotEmpty() && !hideCapturedForLiveEraser) {
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        nativeCanvas.translate(activeSelection.offsetX, activeSelection.offsetY)
                        val cx = activeSelection.centerX
                        val cy = activeSelection.centerY
                        if (activeSelection.rotationDegrees != 0f) {
                            nativeCanvas.rotate(activeSelection.rotationDegrees, cx, cy)
                        }
                        if (activeSelection.scaleX != 1f || activeSelection.scaleY != 1f) {
                            nativeCanvas.scale(activeSelection.scaleX, activeSelection.scaleY, cx, cy)
                        }
                        for (cStroke in activeSelection.capturedStrokes) {
                            KodanimaGraphicsEngine.drawStrokeToCanvas(cStroke, nativeCanvas, 1.0f, null, densityMult)
                        }
                        nativeCanvas.restore()
                    }

                    if (currentPoints.isNotEmpty()) {
                        if (selectedTool == ToolType.ERASER) {
                            val lastPt = currentPoints[currentPoints.lastIndex]
                            val pts = floatArrayOf(lastPt.x, lastPt.y)
                            liveMat.mapPoints(pts)
                            val pFactor = if (eraserUsesPressure) lastPt.pressure else 1.0f
                            val eRadius = ((effectiveToolSize * pFactor * canvasScaleX * safeZ) * 0.5f).coerceAtLeast(3f)
                            nativeCanvas.drawCircle(pts[0], pts[1], eRadius, overlayScratch.eraserRingDarkPaint)
                            nativeCanvas.drawCircle(pts[0], pts[1], eRadius, overlayScratch.eraserRingLightPaint)
                        } else if (
                            selectedTool != ToolType.LINE &&
                            selectedTool != ToolType.RECTANGLE &&
                            selectedTool != ToolType.CIRCLE &&
                            selectedTool != ToolType.LASSO &&
                            selectedTool != ToolType.EYEDROPPER &&
                            brushPresetId != 3 &&
                            cacheHolder.activeStrokeBitmap != null &&
                            !cacheHolder.activeStrokeBitmap!!.isRecycled
                        ) {
                            val activeStrokeBmp = cacheHolder.activeStrokeBitmap!!
                            val invSession = overlayScratch.invMatrixScratch
                            val hasSessDelta = cacheHolder.activeSessionMatrix.invert(invSession)
                            val sessDelta = overlayScratch.deltaMatrixScratch
                            if (hasSessDelta) sessDelta.setConcat(liveMat, invSession) else sessDelta.reset()

                            overlayScratch.bitmapPaint.isFilterBitmap = (canvasScaleX * safeZ) < 4.0f
                            overlayScratch.srcRect.set(0, 0, activeStrokeBmp.width, activeStrokeBmp.height)
                            val invSessVp = 1f / cacheHolder.activeSessionVpScale.coerceAtLeast(0.01f)
                            val sessMarginScreen = cacheHolder.activeSessionMarginPx * invSessVp
                            overlayScratch.dstRectF.set(
                                -sessMarginScreen,
                                -sessMarginScreen,
                                activeStrokeBmp.width * invSessVp - sessMarginScreen,
                                activeStrokeBmp.height * invSessVp - sessMarginScreen
                            )
                            if (activeIsClip && clipBaseSpec != null && activeClipPlan != null) {
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                nativeCanvas.save()
                                if (hasSessDelta) nativeCanvas.concat(sessDelta)
                                nativeCanvas.drawBitmap(activeStrokeBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                                nativeCanvas.restore()
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = activeClipPlan,
                                    sliceTag = "clipBase",
                                    contentHash = clipBaseSpec.contentHash,
                                    paintOverride = overlayScratch.clipDstInPaint
                                )
                                nativeCanvas.restoreToCount(sc)
                            } else if (!activeIsClip) {
                                nativeCanvas.save()
                                if (hasSessDelta) nativeCanvas.concat(sessDelta)
                                nativeCanvas.drawBitmap(activeStrokeBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                                nativeCanvas.restore()
                            }
                        } else {
                            val previewStroke = DrawingStroke(
                                tool = selectedTool,
                                color = currentColor,
                                brushSize = effectiveToolSize,
                                opacity = brushOpacity,
                                points = currentPoints,
                                brushPresetId = brushPresetId,
                                customBrushParams = activeBrushParamsJson,
                                clipPolygon = activeSelectionClipPoly,
                                clipInverted = activeSelectionClipPoly != null && activeSelection.isInverted,
                                clipBoundsWidth = projWidth,
                                clipBoundsHeight = projHeight
                            )
                            if (activeIsClip && clipBaseSpec != null && activeClipPlan != null) {
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                nativeCanvas.save()
                                nativeCanvas.concat(liveMat)
                                KodanimaGraphicsEngine.drawStrokeToCanvas(previewStroke, nativeCanvas, 1.0f, null, densityMult)
                                nativeCanvas.restore()
                                tileEngine.drawSliceTiles(
                                    canvas = nativeCanvas,
                                    liveProjToScreen = liveMat,
                                    plan = activeClipPlan,
                                    sliceTag = "clipBase",
                                    contentHash = clipBaseSpec.contentHash,
                                    paintOverride = overlayScratch.clipDstInPaint
                                )
                                nativeCanvas.restoreToCount(sc)
                            } else if (!activeIsClip) {
                                nativeCanvas.save()
                                nativeCanvas.concat(liveMat)
                                KodanimaGraphicsEngine.drawStrokeToCanvas(previewStroke, nativeCanvas, 1.0f, null, densityMult)
                                nativeCanvas.restore()
                            }
                        }
                    }

                    // Live in-progress remote collaborator strokes (zero-lag multiplayer feedback, unclipped)
                    if (remoteLiveStrokes.isNotEmpty()) {
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        remoteLiveStrokes.values.forEach { live ->
                            val targetLayerStrokes = if (live.isLocalLayer) {
                                currFrame?.localLayers?.getOrNull(live.layerIndex)?.strokes
                            } else {
                                currFrame?.layers?.getOrNull(live.layerIndex)?.strokes
                            }
                            val alreadyCommitted = targetLayerStrokes?.any { it.id == live.stroke.id } == true
                            if (!alreadyCommitted &&
                                live.frameIndex == currentFrameIndex &&
                                live.stroke.points.isNotEmpty() &&
                                live.stroke.tool != ToolType.ERASER
                            ) {
                                KodanimaGraphicsEngine.drawStrokeToCanvas(
                                    live.stroke,
                                    nativeCanvas,
                                    1.0f,
                                    null,
                                    densityMult
                                )
                            }
                        }
                        nativeCanvas.restore()
                    }

                    // Active Selection Outline & Bounding Box
                    if (activeSelection.isActive && activeSelection.points.size >= 2) {
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        nativeCanvas.translate(activeSelection.offsetX, activeSelection.offsetY)

                        val cx = activeSelection.centerX
                        val cy = activeSelection.centerY
                        if (activeSelection.rotationDegrees != 0f) {
                            nativeCanvas.rotate(activeSelection.rotationDegrees, cx, cy)
                        }
                        if (activeSelection.scaleX != 1f || activeSelection.scaleY != 1f) {
                            nativeCanvas.scale(activeSelection.scaleX, activeSelection.scaleY, cx, cy)
                        }

                        overlayScratch.selPaint.color = if (activeSelection.isInverted) {
                            AndroidColor.rgb(255, 110, 64)
                        } else {
                            AndroidColor.CYAN
                        }
                        overlayScratch.selPaint.strokeWidth = 2.0f / (canvasScaleX * safeZ)
                        overlayScratch.selPath.reset()
                        val first = activeSelection.points[0]
                        overlayScratch.selPath.moveTo(first.x, first.y)
                        for (i in 1 until activeSelection.points.size) {
                            val p = activeSelection.points[i]
                            overlayScratch.selPath.lineTo(p.x, p.y)
                        }
                        nativeCanvas.drawPath(overlayScratch.selPath, overlayScratch.selPaint)

                        overlayScratch.boxPaint.strokeWidth = 1.5f / (canvasScaleX * safeZ)
                        val bLeft = activeSelection.minX
                        val bTop = activeSelection.minY
                        val bRight = activeSelection.maxX
                        val bBottom = activeSelection.maxY
                        val hRadius = 5f / (canvasScaleX * safeZ)

                        nativeCanvas.drawRect(bLeft, bTop, bRight, bBottom, overlayScratch.boxPaint)
                        nativeCanvas.drawCircle(bLeft, bTop, hRadius, overlayScratch.handlePaint)
                        nativeCanvas.drawCircle(bRight, bTop, hRadius, overlayScratch.handlePaint)
                        nativeCanvas.drawCircle(bRight, bBottom, hRadius, overlayScratch.handlePaint)
                        nativeCanvas.drawCircle(bLeft, bBottom, hRadius, overlayScratch.handlePaint)

                        nativeCanvas.restore()
                    }

                    // Interactive Video Transform Overlay Box
                    val vBmpBox = videoFrameBitmap
                    if (isVideoTransformMode && videoAsset != null) {
                        val vbW = if (vBmpBox != null && vBmpBox.width > 0) vBmpBox.width.toFloat() else projWidth
                        val vbH = if (vBmpBox != null && vBmpBox.height > 0) vBmpBox.height.toFloat() else projHeight
                        val fitScale = min(boxW / vbW, boxH / vbH)
                        val scaleFactor = (videoAsset.scalePercent / 100.0).toFloat().coerceIn(0.1f, 5f)
                        val drawW = (vbW * fitScale * scaleFactor) / canvasScaleX
                        val drawH = (vbH * fitScale * scaleFactor) / canvasScaleY
                        val cx = projWidth * 0.5f + videoAsset.offsetX.toFloat()
                        val cy = projHeight * 0.5f + videoAsset.offsetY.toFloat()
                        val hw = drawW * 0.5f
                        val hh = drawH * 0.5f

                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        nativeCanvas.translate(cx, cy)
                        if (videoAsset.rotationDegrees != 0.0) {
                            nativeCanvas.rotate(videoAsset.rotationDegrees.toFloat())
                        }
                        overlayScratch.videoBoxPaint.strokeWidth = 2.2f / (canvasScaleX * safeZ)
                        nativeCanvas.drawRect(-hw, -hh, hw, hh, overlayScratch.videoBoxPaint)
                        val vr = 6.5f / (canvasScaleX * safeZ)
                        nativeCanvas.drawCircle(-hw, -hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.drawCircle(hw, -hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.drawCircle(hw, hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.drawCircle(-hw, hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.restore()
                    }

                    // Interactive Caneta Vetorial (Bezier Pen) Spline & Tangent Handles (unbounded off-canvas!)
                    if (selectedTool == ToolType.BEZIER_PEN && workingBezierNodes.isNotEmpty()) {
                        val sampledPts = sampleCubicBezierNodesToStrokePoints(
                            workingBezierNodes.toList(),
                            closed = activeIsBezierClosed,
                            samplesPerSegment = 22
                        )
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        if (sampledPts.size >= 2) {
                            val previewStroke = DrawingStroke(
                                tool = ToolType.BEZIER_PEN,
                                color = currentColor,
                                brushSize = effectiveToolSize,
                                opacity = brushOpacity,
                                points = sampledPts,
                                brushPresetId = brushPresetId,
                                customBrushParams = activeBrushParamsJson,
                                clipPolygon = activeSelectionClipPoly,
                                clipInverted = activeSelectionClipPoly != null && activeSelection.isInverted,
                                clipBoundsWidth = projWidth,
                                clipBoundsHeight = projHeight
                            )
                            KodanimaGraphicsEngine.drawStrokeToCanvas(previewStroke, nativeCanvas, 1.0f, null, densityMult)
                        }

                        val screenScaleFactor = (canvasScaleX * safeZ).coerceAtLeast(0.05f)
                        overlayScratch.bezierLinePaint.strokeWidth = (1.5f / screenScaleFactor).coerceAtLeast(0.5f)
                        overlayScratch.bezierAnchorStrokePaint.strokeWidth = (1.8f / screenScaleFactor).coerceAtLeast(0.6f)
                        val handleR = (4.5f / screenScaleFactor).coerceAtLeast(1.5f)
                        val anchorHalf = (5.5f / screenScaleFactor).coerceAtLeast(2f)

                        workingBezierNodes.forEach { node ->
                            val hasIn = node.handleInX != node.anchorX || node.handleInY != node.anchorY
                            val hasOut = node.handleOutX != node.anchorX || node.handleOutY != node.anchorY
                            if (hasIn) {
                                nativeCanvas.drawLine(node.anchorX, node.anchorY, node.handleInX, node.handleInY, overlayScratch.bezierLinePaint)
                                nativeCanvas.drawCircle(node.handleInX, node.handleInY, handleR, overlayScratch.bezierHandleFillPaint)
                            }
                            if (hasOut) {
                                nativeCanvas.drawLine(node.anchorX, node.anchorY, node.handleOutX, node.handleOutY, overlayScratch.bezierLinePaint)
                                nativeCanvas.drawCircle(node.handleOutX, node.handleOutY, handleR, overlayScratch.bezierHandleFillPaint)
                            }
                            nativeCanvas.drawRect(
                                node.anchorX - anchorHalf,
                                node.anchorY - anchorHalf,
                                node.anchorX + anchorHalf,
                                node.anchorY + anchorHalf,
                                overlayScratch.bezierAnchorFillPaint
                            )
                            nativeCanvas.drawRect(
                                node.anchorX - anchorHalf,
                                node.anchorY - anchorHalf,
                                node.anchorX + anchorHalf,
                                node.anchorY + anchorHalf,
                                overlayScratch.bezierAnchorStrokePaint
                            )
                        }
                        nativeCanvas.restore()
                    }

                    // FlipaClip-style Grid Ruler Overlay (when rendered in front of active layer)
                    if (gridRulerSettings.isEnabled && !gridRulerSettings.renderBehindActiveLayer) {
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        drawGridRulerCanvas(
                            nativeCanvas = nativeCanvas,
                            canvasScaleX = canvasScaleX,
                            canvasScaleY = canvasScaleY,
                            projWidth = projWidth,
                            projHeight = projHeight,
                            screenDensity = density.density,
                            zoom = zoom,
                            settings = gridRulerSettings,
                            scratch = overlayScratch,
                            applyScale = false
                        )
                        nativeCanvas.restore()
                    }

                    // 1P / 2P / 3P Perspective Ruler Overlay in front when !renderBehindActiveLayer
                    if (perspectiveRulerSettings.isEnabled && !perspectiveRulerSettings.renderBehindActiveLayer) {
                        val activeSnapOrigin = if (isDrawingActive && perspectiveRulerSettings.snapToGuide) {
                            currentPoints.firstOrNull()?.let { Offset(it.x, it.y) }
                        } else null
                        nativeCanvas.save()
                        nativeCanvas.concat(liveMat)
                        drawPerspectiveRulerCanvas(
                            nativeCanvas = nativeCanvas,
                            canvasScaleX = canvasScaleX,
                            canvasScaleY = canvasScaleY,
                            projWidth = projWidth,
                            projHeight = projHeight,
                            screenDensity = density.density,
                            zoom = zoom,
                            settings = perspectiveRulerSettings,
                            activeSnapOrigin = activeSnapOrigin,
                            lockedSnapVpIdx = lockedPerspectiveTargetVpIdx,
                            scratch = overlayScratch,
                            applyScale = false
                        )
                        nativeCanvas.restore()
                    }

                    // Subtle project paper boundary hairline so off-canvas artists always see paper frame
                    nativeCanvas.save()
                    nativeCanvas.concat(liveMat)
                    overlayScratch.paperMarginPaint.strokeWidth = (1.1f * density.density / (canvasScaleX * safeZ)).coerceAtLeast(0.4f)
                    nativeCanvas.drawRect(0f, 0f, projWidth, projHeight, overlayScratch.paperMarginPaint)
                    nativeCanvas.restore()

                    // 1px crisp Pixel Grid overlay when deeply zoomed in (>= 9 screen px per project pixel)
                    val screenPxPerProjPxGrid = canvasScaleX * zoom
                    if (screenPxPerProjPxGrid >= 9.0f) {
                        val c0 = screenToProjectCoords(0f, 0f)
                        val c1 = screenToProjectCoords(containerWidth, 0f)
                        val c2 = screenToProjectCoords(0f, containerHeight)
                        val c3 = screenToProjectCoords(containerWidth, containerHeight)
                        val startPx = min(min(c0.x, c1.x), min(c2.x, c3.x)).toInt().coerceIn(0, project.resolution.width)
                        val endPx = (max(max(c0.x, c1.x), max(c2.x, c3.x)).toInt() + 1).coerceIn(0, project.resolution.width)
                        val startPy = min(min(c0.y, c1.y), min(c2.y, c3.y)).toInt().coerceIn(0, project.resolution.height)
                        val endPy = (max(max(c0.y, c1.y), max(c2.y, c3.y)).toInt() + 1).coerceIn(0, project.resolution.height)
                        if ((endPx - startPx) <= 200 && (endPy - startPy) <= 200) {
                            val topY = startPy.toFloat()
                            val bottomY = endPy.toFloat()
                            val leftX = startPx.toFloat()
                            val rightX = endPx.toFloat()
                            overlayScratch.pixelGridPaint.alpha =
                                ((screenPxPerProjPxGrid - 9.0f) / 14.0f).coerceIn(0.25f, 1.0f).let { (it * 72f).roundToInt() }
                            nativeCanvas.save()
                            nativeCanvas.concat(liveMat)
                            for (px in startPx..endPx) {
                                val lx = px.toFloat()
                                nativeCanvas.drawLine(lx, topY, lx, bottomY, overlayScratch.pixelGridPaint)
                            }
                            for (py in startPy..endPy) {
                                val ly = py.toFloat()
                                nativeCanvas.drawLine(leftX, ly, rightX, ly, overlayScratch.pixelGridPaint)
                            }
                            nativeCanvas.restore()
                        }
                    }
                    @Suppress("UNUSED_EXPRESSION")
                    trigger
                }
            } // end else (!isCameraSideView3D)

            // 5. Tablet Virtual Cursor Overlay (Custom .cur/.ani/.png scaled cleanly with exact hotspot)
            if (tabletSettings.enableVirtualCursor && cursorScreenPos != null && !isCameraSideView3D) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pos = cursorScreenPos ?: return@Canvas
                    val canvasScale = (boxW / projWidth) * zoom
                    val cursorRadius = (effectiveToolSize * canvasScale * 0.5f).coerceIn(4f, 250f)
                    val cursorRingColor = if (tabletSettings.cursorColor != 0L) Color(tabletSettings.cursorColor) else Color(themeAccentColor)
                    val cBmp = customCursorData?.bitmap

                    if (cBmp != null && !cBmp.isRecycled) {
                        val maxCursorPx = 32.dp.toPx()
                        val bmpW = cBmp.width.toFloat().coerceAtLeast(1f)
                        val bmpH = cBmp.height.toFloat().coerceAtLeast(1f)
                        val s = min(maxCursorPx / bmpW, maxCursorPx / bmpH)
                        val drawW = bmpW * s
                        val drawH = bmpH * s
                        val hsX = customCursorData.hotspotX.coerceIn(0f, 1f)
                        val hsY = customCursorData.hotspotY.coerceIn(0f, 1f)
                        val drawLeft = pos.x - drawW * hsX
                        val drawTop = pos.y - drawH * hsY
                        overlayScratch.srcRect.set(0, 0, cBmp.width, cBmp.height)
                        overlayScratch.dstRectF.set(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            cBmp,
                            overlayScratch.srcRect,
                            overlayScratch.dstRectF,
                            overlayScratch.bitmapPaint
                        )
                        drawCircle(
                            color = cursorRingColor,
                            radius = 2.dp.toPx(),
                            center = pos
                        )
                    } else {
                        drawCircle(
                            color = Color.White,
                            radius = cursorRadius,
                            center = pos,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = cursorRingColor,
                            radius = (cursorRadius - 1.5f).coerceAtLeast(1f),
                            center = pos,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        drawCircle(
                            color = cursorRingColor,
                            radius = 2.dp.toPx(),
                            center = pos
                        )
                    }
                }
            }

            // 6. Remote Collaborator Cursors Overlay (Pruned to active peers + synced custom .cur/.ani/.png reticles)
            val activePeerIds = remember(connectedFriends, connectedFriend) {
                when {
                    connectedFriends.isNotEmpty() -> connectedFriends.map { it.id }.toSet()
                    connectedFriend != null -> setOf(connectedFriend.id)
                    else -> null
                }
            }
            val visibleRemoteCursors = remember(remoteCursors, activePeerIds) {
                if (activePeerIds != null) {
                    remoteCursors.values.filter { it.peerId in activePeerIds }
                } else {
                    remoteCursors.values.toList()
                }
            }

            if (visibleRemoteCursors.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val textPx = 10.dp.toPx()
                    val padH = 5.dp.toPx()
                    val cornerR = 5.dp.toPx()
                    val maxRemoteCursorPx = 30.dp.toPx()
                    overlayScratch.peerTextPaint.textSize = textPx

                    visibleRemoteCursors.forEach { peerCursor ->
                        if (peerCursor.frameIndex == currentFrameIndex) {
                            val screenPos = projectToScreenCoords(peerCursor.x, peerCursor.y)
                            val peerCol = Color(peerCursor.color)
                            val customPeerBmp = remoteCursorBitmaps[peerCursor.peerId]

                            if (customPeerBmp != null && !customPeerBmp.isRecycled) {
                                val bw = customPeerBmp.width.toFloat().coerceAtLeast(1f)
                                val bh = customPeerBmp.height.toFloat().coerceAtLeast(1f)
                                val s = min(maxRemoteCursorPx / bw, maxRemoteCursorPx / bh)
                                val dw = bw * s
                                val dh = bh * s
                                overlayScratch.srcRect.set(0, 0, customPeerBmp.width, customPeerBmp.height)
                                overlayScratch.dstRectF.set(
                                    screenPos.x - dw * 0.5f,
                                    screenPos.y - dh * 0.5f,
                                    screenPos.x + dw * 0.5f,
                                    screenPos.y + dh * 0.5f
                                )
                                drawContext.canvas.nativeCanvas.drawBitmap(
                                    customPeerBmp,
                                    overlayScratch.srcRect,
                                    overlayScratch.dstRectF,
                                    overlayScratch.bitmapPaint
                                )
                                drawCircle(
                                    color = peerCol,
                                    radius = if (peerCursor.isDrawing) 4.dp.toPx() else 2.5.dp.toPx(),
                                    center = screenPos
                                )
                            } else {
                                drawCircle(
                                    color = peerCol,
                                    radius = if (peerCursor.isDrawing) 11.dp.toPx() else 9.dp.toPx(),
                                    center = screenPos,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = peerCol,
                                    radius = 3.dp.toPx(),
                                    center = screenPos
                                )
                            }

                            drawContext.canvas.nativeCanvas.apply {
                                overlayScratch.peerBgPaint.color = peerCursor.color.toInt()
                                val label = peerCursor.peerName
                                val textWidth = overlayScratch.peerTextPaint.measureText(label)
                                val pillLeft = screenPos.x - textWidth * 0.5f - padH
                                val pillTop = screenPos.y - 28.dp.toPx()
                                val pillRight = screenPos.x + textWidth * 0.5f + padH
                                val pillBottom = screenPos.y - 14.dp.toPx()

                                drawRoundRect(
                                    pillLeft, pillTop, pillRight, pillBottom,
                                    cornerR, cornerR, overlayScratch.peerBgPaint
                                )
                                drawText(label, pillLeft + padH, pillBottom - 3.5.dp.toPx(), overlayScratch.peerTextPaint)
                            }
                        }
                    }
                }
            }
        }

        // Above-canvas unclipped Camera Capture Frame & Post-Effects overlay in BOTH normal Canvas mode & Camera Mode!
        if (!isCameraSideView3D && (camSettings.isEnabled || camSettings.postEffects.hasAnyEffect())) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_sample_unclipped_overlay")
            ) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.translate(size.width * 0.5f + panX, size.height * 0.5f + panY)
                nativeCanvas.rotate(rotation)
                nativeCanvas.scale(if (mirrorHorizontal) -zoom else zoom, zoom)
                nativeCanvas.translate(-boxW * 0.5f, -boxH * 0.5f)
                drawCameraAndPostEffectsOverlay(
                    nativeCanvas = nativeCanvas,
                    canvasW = boxW,
                    canvasH = boxH,
                    projWidth = projWidth,
                    projHeight = projHeight,
                    screenDensity = density.density,
                    camSettings = camSettings,
                    evaluatedKf = evaluatedCameraKf,
                    scratch = overlayScratch
                )
                nativeCanvas.restore()
            }
        }

        // Above-canvas Floating Reference Window (rendered outside pointerInteropFilter Box but transformed in exact lockstep with canvas)
        if (referenceImage.isVisible && referenceImage.renderAboveCanvas) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = panX
                        translationY = panY
                        scaleX = if (mirrorHorizontal) -zoom else zoom
                        scaleY = zoom
                        this.rotationZ = rotation
                    }
                    .size(
                        width = with(density) { boxWidthPx.toDp() },
                        height = with(density) { boxHeightPx.toDp() }
                    )
                    .align(Alignment.Center)
            ) {
                FloatingReferenceImageSurface(
                    refState = referenceImage,
                    theme = theme,
                    onTransformDelta = { dx, dy, z, r ->
                        onFloatingRefTransformDelta?.invoke(dx, dy, z, r)
                    }
                )
            }
        }

        // 7. Interactive Caneta Vetorial (Bezier Pen) Floating Action Bar
        // Placed OUTSIDE the pointerInteropFilter Box so all taps register reliably in Mobile & Touch devices.
        AnimatedVisibility(
            visible = showFloatingBezierBar && selectedTool == ToolType.BEZIER_PEN && workingBezierNodes.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) {
            val accent = Color(themeAccentColor)
            Surface(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.2.dp, accent, RoundedCornerShape(999.dp))
                    .testTag("bezier_pen_action_bar"),
                color = Color(0xEE181822)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Vetorial (${workingBezierNodes.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Surface(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                if (onToggleBezierClosed != null) {
                                    onToggleBezierClosed()
                                } else {
                                    internalIsBezierClosed = !internalIsBezierClosed
                                }
                                activeStrokeVersion++
                            }
                            .border(
                                1.dp,
                                if (activeIsBezierClosed) accent else Color.White.copy(alpha = 0.25f),
                                RoundedCornerShape(999.dp)
                            )
                            .testTag("bezier_toggle_closed_btn"),
                        color = if (activeIsBezierClosed) accent.copy(alpha = 0.28f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Loop, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Fechar", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                if (onUndoBezierNode != null) {
                                    onUndoBezierNode()
                                } else if (workingBezierNodes.isNotEmpty()) {
                                    workingBezierNodes.removeAt(workingBezierNodes.lastIndex)
                                }
                                activeStrokeVersion++
                            }
                            .testTag("bezier_undo_node_btn"),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Desfazer", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                if (onClearBezierNodes != null) {
                                    onClearBezierNodes()
                                } else {
                                    workingBezierNodes.clear()
                                    internalIsBezierClosed = false
                                }
                                activeStrokeVersion++
                            }
                            .testTag("bezier_clear_btn"),
                        color = Color(0x33FF4081)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF6E9C), modifier = Modifier.size(14.dp))
                            Text("Limpar", fontSize = 10.sp, color = Color(0xFFFF6E9C))
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                if (onConfirmBezierCurve != null) {
                                    onConfirmBezierCurve()
                                } else {
                                    val sampledPts = sampleCubicBezierNodesToStrokePoints(
                                        workingBezierNodes.toList(),
                                        closed = activeIsBezierClosed,
                                        samplesPerSegment = 24
                                    )
                                    if (sampledPts.isNotEmpty()) {
                                        onStrokeCompleted(
                                            DrawingStroke(
                                                tool = ToolType.BEZIER_PEN,
                                                color = currentColor,
                                                brushSize = effectiveToolSize,
                                                opacity = brushOpacity,
                                                points = sampledPts,
                                                brushPresetId = brushPresetId,
                                                customBrushParams = activeBrushParamsJson,
                                                clipPolygon = activeSelectionClipPoly,
                                                clipInverted = activeSelectionClipPoly != null && activeSelection.isInverted,
                                                clipBoundsWidth = projWidth,
                                                clipBoundsHeight = projHeight
                                            )
                                        )
                                    }
                                    workingBezierNodes.clear()
                                    internalIsBezierClosed = false
                                }
                                activeStrokeVersion++
                            }
                            .testTag("bezier_apply_curve_btn"),
                        color = Color(0xFF00E676)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            Text("Aplicar", fontSize = 10.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Video Transform Mode subtle guide badge
        if (isVideoTransformMode && videoAsset != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = Color(0xDDFF7A00)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.OpenWith, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    Text(
                        text = "Arraste (1 dedo) ou Pinça/Giro (2 dedos) no vídeo",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        // Interactive 3D Stage Viewport Quick Mode HUD (Orbit Stage, Move Camera 3D, Camera POV, Center)
        if (isCameraSideView3D) {
            val accent = Color(themeAccentColor)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 52.dp, end = 52.dp)
                    .shadow(8.dp, RoundedCornerShape(999.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                    .testTag("stage_3d_hud_bar"),
                color = Color(0xE6181C26)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Stage3DHudPill(
                        label = "Girar Palco",
                        icon = Icons.Default.Explore,
                        active = stage3DMode == Stage3DInteractionMode.ORBIT_STAGE,
                        activeColor = Color(0xFF00D2FF),
                        testTag = "stage_3d_mode_orbit"
                    ) { stage3DMode = Stage3DInteractionMode.ORBIT_STAGE }

                    Stage3DHudPill(
                        label = "Mover Câmera 3D",
                        icon = Icons.Default.OpenWith,
                        active = stage3DMode == Stage3DInteractionMode.MOVE_CAMERA,
                        activeColor = Color(0xFFFF9800),
                        testTag = "stage_3d_mode_move_cam"
                    ) { stage3DMode = Stage3DInteractionMode.MOVE_CAMERA }

                    Stage3DHudPill(
                        label = "Ver pela Câmera",
                        icon = Icons.Default.Visibility,
                        active = stage3DMode == Stage3DInteractionMode.CAMERA_POV,
                        activeColor = Color(0xFF00E676),
                        testTag = "stage_3d_mode_pov"
                    ) { stage3DMode = Stage3DInteractionMode.CAMERA_POV }

                    Stage3DHudPill(
                        label = "Focar",
                        icon = Icons.Default.GpsFixed,
                        active = false,
                        activeColor = Color(0xFF00E676),
                        testTag = "stage_3d_focus_btn"
                    ) {
                        val kf = evaluatedCameraKf
                        view3DZoom = 1.32f
                        val focusScale = stageUnitScaleRef.floatValue * 1.32f
                        when (stageSelection.type) {
                            StageTargetType.CAMERA -> {
                                view3DPanX = (-kf.x * focusScale).coerceIn(-900f, 900f)
                                view3DPanY = (-kf.y * focusScale).coerceIn(-900f, 900f)
                            }
                            StageTargetType.OBJECT_3D -> {
                                val obj = camSettings.scene3DObjects.firstOrNull { it.id == stageSelection.id }
                                if (obj != null) {
                                    val tf = Camera3DEngine.evaluateObjectTransformAtTick(obj.keyframes, effectiveTickFloat, camSettings.autoInterpolate)
                                    view3DPanX = (-tf.x * focusScale).coerceIn(-900f, 900f)
                                    view3DPanY = (-tf.y * focusScale).coerceIn(-900f, 900f)
                                } else {
                                    view3DPanX = 0f; view3DPanY = 0f
                                }
                            }
                            StageTargetType.LAYER -> {
                                val tf = Camera3DEngine.evaluateLayerTransformAtTick(camSettings.layerTransformTracks, stageSelection.id, effectiveTickFloat, camSettings.autoInterpolate)
                                view3DPanX = (-tf.x * focusScale).coerceIn(-900f, 900f)
                                view3DPanY = (-tf.y * focusScale).coerceIn(-900f, 900f)
                            }
                        }
                    }

                    Stage3DHudPill(
                        label = "Centralizar",
                        icon = Icons.Default.CenterFocusWeak,
                        active = false,
                        activeColor = accent,
                        testTag = "stage_3d_reset_orbit"
                    ) {
                        view3DYaw = -28f
                        view3DPitch = 16f
                        view3DPanX = 0f
                        view3DPanY = 0f
                        view3DZoom = 1f
                    }
                }
            }
        }
    }
}

private enum class Stage3DInteractionMode {
    ORBIT_STAGE,
    MOVE_CAMERA,
    CAMERA_POV
}

private class Stage3DCamGizmoState {
    var valid: Boolean = false
    var apexX: Float = 0f
    var apexY: Float = 0f
    var xTipX: Float = 0f
    var xTipY: Float = 0f
    var yTipX: Float = 0f
    var yTipY: Float = 0f
    var zTipX: Float = 0f
    var zTipY: Float = 0f
    var xDirScreenX: Float = 1f
    var xDirScreenY: Float = 0f
    var yDirScreenX: Float = 0f
    var yDirScreenY: Float = 1f
    var zDirScreenX: Float = 0f
    var zDirScreenY: Float = 1f
}

@Composable
private fun Stage3DHudPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                width = 1.dp,
                color = if (active) activeColor else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable { onClick() }
            .testTag(testTag),
        color = if (active) activeColor.copy(alpha = 0.24f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (active) activeColor else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) activeColor else Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

private fun drawPerspectiveRulerCanvas(
    nativeCanvas: AndroidCanvas,
    canvasScaleX: Float,
    canvasScaleY: Float,
    projWidth: Float,
    projHeight: Float,
    screenDensity: Float,
    zoom: Float,
    settings: PerspectiveRulerSettings,
    activeSnapOrigin: Offset?,
    lockedSnapVpIdx: Int,
    scratch: CanvasOverlayScratch,
    applyScale: Boolean = true
) {
    val perspOp = settings.opacity.coerceIn(0.10f, 1.0f)
    val horizonColor = settings.vp1Color.toInt()
    val horizonAlpha = ((0.55f + 0.42f * perspOp) * perspOp * 255f).roundToInt().coerceIn(85, 248)
    val rayAlpha = ((0.38f + 0.56f * perspOp) * perspOp * 255f).roundToInt().coerceIn(54, 232)
    val vpFillAlpha = (perspOp * 0.42f * 255f).roundToInt().coerceIn(38, 150)
    val vpStrokeAlpha = (perspOp * 0.95f * 255f).roundToInt().coerceIn(96, 250)
    val vpCount = settings.vanishingPointsCount.coerceIn(1, 3)

    // Keep stroke widths & handles crisp in screen dp across all DPIs and zoom levels
    val effectiveScreenScale = (canvasScaleX * zoom.coerceAtLeast(0.25f)).coerceAtLeast(0.08f)
    val pxPerDpInProj = screenDensity.coerceAtLeast(1.0f) / effectiveScreenScale
    val rayStrokeWidth = (1.25f * pxPerDpInProj).coerceIn(0.8f, 36f)
    val horizonStrokeWidth = (1.85f * pxPerDpInProj).coerceIn(1.2f, 44f)
    val handleRadius = (13.5f * pxPerDpInProj).coerceIn(9f, 64f)
    val handleStrokeWidth = (2.1f * pxPerDpInProj).coerceIn(1.2f, 24f)
    val edgePad = (16f * pxPerDpInProj).coerceIn(10f, projWidth * 0.12f)

    nativeCanvas.save()
    if (applyScale) nativeCanvas.scale(canvasScaleX, canvasScaleY)

    // Horizon line connecting VP1 & VP2 (or horizontal through VP1 when 1-point)
    val vp1x = settings.vp1XRatio * projWidth
    val vp1y = settings.vp1YRatio * projHeight
    val vp2x = settings.vp2XRatio * projWidth
    val vp2y = settings.vp2YRatio * projHeight

    scratch.perspectiveRayPaint.color = (horizonColor and 0x00FFFFFF) or (horizonAlpha shl 24)
    scratch.perspectiveRayPaint.strokeWidth = horizonStrokeWidth
    if (vpCount >= 2 && abs(vp2x - vp1x) > 1f) {
        val slope = (vp2y - vp1y) / (vp2x - vp1x)
        val yLeft = vp1y - slope * vp1x
        val yRight = vp1y + slope * (projWidth - vp1x)
        nativeCanvas.drawLine(0f, yLeft, projWidth, yRight, scratch.perspectiveRayPaint)
    } else {
        nativeCanvas.drawLine(0f, vp1y, projWidth, vp1y, scratch.perspectiveRayPaint)
    }

    val raysCount = 32
    val baseDiag = hypot(projWidth, projHeight)
    val canvasMidX = projWidth * 0.5f
    val canvasMidY = projHeight * 0.5f

    for (vpIdx in 0 until vpCount) {
        val (rx, ry, colLong, label) = when (vpIdx) {
            0 -> Quadruple(settings.vp1XRatio, settings.vp1YRatio, settings.vp1Color, "P1")
            1 -> Quadruple(settings.vp2XRatio, settings.vp2YRatio, settings.vp2Color, "P2")
            else -> Quadruple(settings.vp3XRatio, settings.vp3YRatio, settings.vp3Color, "P3")
        }
        val px = rx * projWidth
        val py = ry * projHeight
        val vpColInt = colLong.toInt()
        val maxDiag = baseDiag * 2.4f + hypot(px - canvasMidX, py - canvasMidY)

        // Draw 32 full radial perspective guide rays
        scratch.perspectiveRayPaint.color = (vpColInt and 0x00FFFFFF) or (rayAlpha shl 24)
        scratch.perspectiveRayPaint.strokeWidth = rayStrokeWidth
        for (i in 0 until raysCount) {
            val angle = (2.0 * Math.PI * i) / raysCount
            val endX = px + (cos(angle).toFloat() * maxDiag)
            val endY = py + (sin(angle).toFloat() * maxDiag)
            nativeCanvas.drawLine(px, py, endX, endY, scratch.perspectiveRayPaint)
        }

        // If the vanishing point is dragged outside canvas bounds, add a focused ray fan aimed
        // directly across the canvas so guide lines stay dense inside the drawing area
        val isOffCanvas = px < 0f || px > projWidth || py < 0f || py > projHeight
        if (isOffCanvas) {
            val centerAngle = atan2((canvasMidY - py).toDouble(), (canvasMidX - px).toDouble())
            val fanSpread = Math.toRadians(38.0)
            val fanSteps = 12
            for (f in 0..fanSteps) {
                val frac = (f.toDouble() / fanSteps.toDouble()) * 2.0 - 1.0
                val a = centerAngle + frac * fanSpread
                val endX = px + (cos(a).toFloat() * maxDiag)
                val endY = py + (sin(a).toFloat() * maxDiag)
                nativeCanvas.drawLine(px, py, endX, endY, scratch.perspectiveRayPaint)
            }
        }

        // Active live Snap-to-Guide alignment ray connecting VP through stroke origin
        if (activeSnapOrigin != null && lockedSnapVpIdx == vpIdx) {
            val svx = activeSnapOrigin.x - px
            val svy = activeSnapOrigin.y - py
            val sLen = hypot(svx, svy)
            if (sLen > 1f) {
                val ux = svx / sLen
                val uy = svy / sLen
                scratch.perspectiveRayPaint.color = (vpColInt and 0x00FFFFFF) or (vpStrokeAlpha shl 24)
                scratch.perspectiveRayPaint.strokeWidth = rayStrokeWidth * 1.9f
                nativeCanvas.drawLine(
                    px - ux * maxDiag,
                    py - uy * maxDiag,
                    px + ux * maxDiag,
                    py + uy * maxDiag,
                    scratch.perspectiveRayPaint
                )
            }
        }

        // Draw VP Handle at true position or clamped edge dock if dragged off-canvas
        val drawHx = if (isOffCanvas) px.coerceIn(edgePad, (projWidth - edgePad).coerceAtLeast(edgePad)) else px
        val drawHy = if (isOffCanvas) py.coerceIn(edgePad, (projHeight - edgePad).coerceAtLeast(edgePad)) else py

        // Dark contrast halo so handle & text stay readable over white paper
        scratch.perspectiveVpStrokePaint.color = (0x66000000)
        scratch.perspectiveVpStrokePaint.strokeWidth = handleStrokeWidth + pxPerDpInProj * 1.4f
        nativeCanvas.drawCircle(drawHx, drawHy, handleRadius, scratch.perspectiveVpStrokePaint)

        scratch.perspectiveVpPaint.color = (vpColInt and 0x00FFFFFF) or (vpFillAlpha shl 24)
        nativeCanvas.drawCircle(drawHx, drawHy, handleRadius, scratch.perspectiveVpPaint)

        scratch.perspectiveVpStrokePaint.color = (vpColInt and 0x00FFFFFF) or (vpStrokeAlpha shl 24)
        scratch.perspectiveVpStrokePaint.strokeWidth = handleStrokeWidth
        nativeCanvas.drawCircle(drawHx, drawHy, handleRadius, scratch.perspectiveVpStrokePaint)

        // Subtle center reticle crosshairs
        val crossArm = handleRadius * 1.35f
        scratch.perspectiveRayPaint.color = (vpColInt and 0x00FFFFFF) or (vpStrokeAlpha shl 24)
        scratch.perspectiveRayPaint.strokeWidth = rayStrokeWidth
        nativeCanvas.drawLine(drawHx - crossArm, drawHy, drawHx + crossArm, drawHy, scratch.perspectiveRayPaint)
        nativeCanvas.drawLine(drawHx, drawHy - crossArm, drawHx, drawHy + crossArm, scratch.perspectiveRayPaint)

        scratch.perspectiveLabelPaint.color = (AndroidColor.WHITE and 0x00FFFFFF) or (vpStrokeAlpha shl 24)
        scratch.perspectiveLabelPaint.textSize = handleRadius * 0.85f
        val textOffsetY = handleRadius * 0.30f
        nativeCanvas.drawText(label, drawHx, drawHy + textOffsetY, scratch.perspectiveLabelPaint)
    }

    nativeCanvas.restore()
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private fun drawGridRulerCanvas(
    nativeCanvas: AndroidCanvas,
    canvasScaleX: Float,
    canvasScaleY: Float,
    projWidth: Float,
    projHeight: Float,
    screenDensity: Float,
    zoom: Float,
    settings: GridRulerSettings,
    scratch: CanvasOverlayScratch,
    applyScale: Boolean = true
) {
    val cellPx = settings.cellSizePx.coerceIn(12f, 256f)
    val baseCol = settings.color.toInt()
    val gridOp = settings.opacity.coerceIn(0.1f, 1.0f)
    val alphaMain = (gridOp * 255f).roundToInt()
    val alphaSub = (gridOp * 0.48f * 255f).roundToInt()
    val majorColor = (baseCol and 0x00FFFFFF) or (alphaMain shl 24)
    val minorColor = (baseCol and 0x00FFFFFF) or (alphaSub shl 24)
    val effScaleX = (canvasScaleX * zoom.coerceAtLeast(0.25f)).coerceAtLeast(0.08f)
    val effScaleY = (canvasScaleY * zoom.coerceAtLeast(0.25f)).coerceAtLeast(0.08f)
    val dpInProjX = screenDensity / effScaleX
    val dpInProjY = screenDensity / effScaleY
    val baseThicknessDp = settings.lineThicknessDp.coerceIn(0.4f, 6.0f)

    nativeCanvas.save()
    if (applyScale) nativeCanvas.scale(canvasScaleX, canvasScaleY)
    if (settings.useLineCount) {
        val countX = settings.lineCountX.coerceIn(1, 128)
        val countY = settings.lineCountY.coerceIn(1, 128)
        val stepX = projWidth / (countX + 1).toFloat()
        val stepY = projHeight / (countY + 1).toFloat()
        for (colIdx in 1..countX) {
            val gx = stepX * colIdx
            val isMajor = colIdx % 4 == 0
            scratch.gridRulerPaint.color = if (isMajor) majorColor else minorColor
            scratch.gridRulerPaint.strokeWidth = baseThicknessDp * (if (isMajor) 1.35f else 1.0f) * dpInProjX
            nativeCanvas.drawLine(gx, 0f, gx, projHeight, scratch.gridRulerPaint)
        }
        for (rowIdx in 1..countY) {
            val gy = stepY * rowIdx
            val isMajor = rowIdx % 4 == 0
            scratch.gridRulerPaint.color = if (isMajor) majorColor else minorColor
            scratch.gridRulerPaint.strokeWidth = baseThicknessDp * (if (isMajor) 1.35f else 1.0f) * dpInProjY
            nativeCanvas.drawLine(0f, gy, projWidth, gy, scratch.gridRulerPaint)
        }
    } else {
        var gx = cellPx
        var colIdx = 1
        while (gx < projWidth) {
            val isMajor = colIdx % 4 == 0
            scratch.gridRulerPaint.color = if (isMajor) majorColor else minorColor
            scratch.gridRulerPaint.strokeWidth = baseThicknessDp * (if (isMajor) 1.35f else 1.0f) * dpInProjX
            nativeCanvas.drawLine(gx, 0f, gx, projHeight, scratch.gridRulerPaint)
            gx += cellPx
            colIdx++
        }
        var gy = cellPx
        var rowIdx = 1
        while (gy < projHeight) {
            val isMajor = rowIdx % 4 == 0
            scratch.gridRulerPaint.color = if (isMajor) majorColor else minorColor
            scratch.gridRulerPaint.strokeWidth = baseThicknessDp * (if (isMajor) 1.35f else 1.0f) * dpInProjY
            nativeCanvas.drawLine(0f, gy, projWidth, gy, scratch.gridRulerPaint)
            gy += cellPx
            rowIdx++
        }
    }
    nativeCanvas.restore()
}

private data class Projected3DPt(val x: Float, val y: Float, val zDepth: Float)

private fun projectPoint3D(
    wx: Float,
    wy: Float,
    wz: Float,
    cosY: Float,
    sinY: Float,
    cosP: Float,
    sinP: Float,
    focal: Float,
    cx: Float,
    cy: Float
): Projected3DPt {
    val rx = wx * cosY - wz * sinY
    val rz = wx * sinY + wz * cosY
    val ry = wy * cosP - rz * sinP
    val zz = wy * sinP + rz * cosP
    val denom = (focal + zz).coerceAtLeast(24f)
    val persp = focal / denom
    return Projected3DPt(cx + rx * persp, cy + ry * persp, zz)
}

private fun computeStage3DProjScale(
    canvasW: Float,
    canvasH: Float,
    projWidth: Float,
    view3DZoom: Float
): Float {
    val cardW = min(canvasW, canvasH) * 0.28f * view3DZoom
    return (2f * cardW) / projWidth.coerceAtLeast(1f)
}

private fun drawTransparencyCheckerboard(
    nativeCanvas: AndroidCanvas,
    width: Float,
    height: Float,
    screenDensity: Float,
    scratch: CanvasOverlayScratch
) {
    val tile = (14f * screenDensity).coerceAtLeast(12f)
    nativeCanvas.drawRect(0f, 0f, width, height, scratch.checkerLightPaint)
    var row = 0
    var y = 0f
    while (y < height) {
        var col = 0
        var x = 0f
        while (x < width) {
            if ((row + col) % 2 == 1) {
                nativeCanvas.drawRect(x, y, min(width, x + tile), min(height, y + tile), scratch.checkerDarkPaint)
            }
            x += tile
            col++
        }
        y += tile
        row++
    }
}

private fun drawCameraAndPostEffectsOverlay(
    nativeCanvas: AndroidCanvas,
    canvasW: Float,
    canvasH: Float,
    projWidth: Float,
    projHeight: Float,
    screenDensity: Float,
    camSettings: Camera3DSettings,
    evaluatedKf: Camera3DKeyframe,
    scratch: CanvasOverlayScratch
) {
    val fx = camSettings.postEffects
    // 1. Live Post-Effects Preview (zero draw-thread allocations)
    if (fx.hasAnyEffect()) {
        if (fx.sepia > 0.01f) {
            scratch.fxSepiaPaint.color = AndroidColor.argb((fx.sepia.coerceIn(0f, 1f) * 90f).roundToInt(), 148, 102, 48)
            nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, scratch.fxSepiaPaint)
        }
        if (fx.bloom > 0.01f) {
            val bAlpha = (fx.bloom.coerceIn(0f, 1f) * 78f).roundToInt()
            if (scratch.fxBloomKeyW != canvasW || scratch.fxBloomKeyH != canvasH || scratch.fxBloomKeyAlpha != bAlpha) {
                scratch.fxBloomPaint.shader = RadialGradient(
                    canvasW * 0.5f,
                    canvasH * 0.5f,
                    max(canvasW, canvasH) * 0.65f,
                    intArrayOf(AndroidColor.argb(bAlpha, 255, 244, 224), 0x00000000),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                scratch.fxBloomKeyW = canvasW
                scratch.fxBloomKeyH = canvasH
                scratch.fxBloomKeyAlpha = bAlpha
            }
            nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, scratch.fxBloomPaint)
        }
        if (fx.chromaticAberration > 0.01f) {
            val shift = fx.chromaticAberration.coerceIn(0f, 1f) * 5f * screenDensity
            scratch.fxChromaticPaint.strokeWidth = 2.5f * screenDensity
            scratch.fxChromaticPaint.color = AndroidColor.argb(120, 255, 48, 100)
            nativeCanvas.drawRect(shift, 0f, canvasW, canvasH, scratch.fxChromaticPaint)
            scratch.fxChromaticPaint.color = AndroidColor.argb(120, 0, 210, 255)
            nativeCanvas.drawRect(0f, 0f, (canvasW - shift).coerceAtLeast(1f), canvasH, scratch.fxChromaticPaint)
        }
        if (fx.vignette > 0.01f) {
            val vAlpha = (fx.vignette.coerceIn(0f, 1f) * 200f).roundToInt().coerceIn(0, 230)
            if (scratch.fxVignetteKeyW != canvasW || scratch.fxVignetteKeyH != canvasH || scratch.fxVignetteKeyAlpha != vAlpha) {
                scratch.fxVignettePaint.shader = RadialGradient(
                    canvasW * 0.5f,
                    canvasH * 0.5f,
                    max(canvasW, canvasH) * 0.72f,
                    intArrayOf(0x00000000, 0x00000000, (vAlpha shl 24)),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                scratch.fxVignetteKeyW = canvasW
                scratch.fxVignetteKeyH = canvasH
                scratch.fxVignetteKeyAlpha = vAlpha
            }
            nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, scratch.fxVignettePaint)
        }
    }

    // 2. Camera Viewfinder Moldura + Outer Matte (2D active capture preview)
    if (camSettings.isEnabled && camSettings.showCaptureFrame) {
        val sx = canvasW / projWidth.coerceAtLeast(1f)
        val sy = canvasH / projHeight.coerceAtLeast(1f)
        val camAsp = camSettings.captureWidth.toFloat() / camSettings.captureHeight.coerceAtLeast(1).toFloat()
        val baseFrameW: Float
        val baseFrameH: Float
        if (canvasW / canvasH > camAsp) {
            baseFrameH = canvasH * 1.0f
            baseFrameW = baseFrameH * camAsp
        } else {
            baseFrameW = canvasW * 1.0f
            baseFrameH = baseFrameW / camAsp
        }
        val frameW = baseFrameW / evaluatedKf.zoom.coerceIn(0.25f, 6.0f)
        val frameH = baseFrameH / evaluatedKf.zoom.coerceIn(0.25f, 6.0f)
        val cx = canvasW * 0.5f + evaluatedKf.x * sx
        val cy = canvasH * 0.5f + evaluatedKf.y * sy
        val hw = frameW * 0.5f
        val hh = frameH * 0.5f
        val outerPad = min(frameW, frameH) * 0.06f
        val safeW = hw * 0.72f
        val safeH = hh * 0.72f

        nativeCanvas.save()
        nativeCanvas.translate(cx, cy)
        if (abs(evaluatedKf.rotationZ) > 0.05f) {
            nativeCanvas.rotate(evaluatedKf.rotationZ)
        }

        val bigSpan = max(canvasW, canvasH) * 4f
        nativeCanvas.drawRect(-bigSpan, -bigSpan, bigSpan, -hh, scratch.camOutsideMaskPaint)
        nativeCanvas.drawRect(-bigSpan, hh, bigSpan, bigSpan, scratch.camOutsideMaskPaint)
        nativeCanvas.drawRect(-bigSpan, -hh, -hw, hh, scratch.camOutsideMaskPaint)
        nativeCanvas.drawRect(hw, -hh, bigSpan, hh, scratch.camOutsideMaskPaint)

        scratch.camOuterBorderPaint.strokeWidth = 1.1f * screenDensity
        nativeCanvas.drawRect(-hw - outerPad, -hh - outerPad, hw + outerPad, hh + outerPad, scratch.camOuterBorderPaint)

        scratch.camActiveBorderPaint.strokeWidth = 2.2f * screenDensity
        nativeCanvas.drawRect(-hw, -hh, hw, hh, scratch.camActiveBorderPaint)

        scratch.camSafeBoxPaint.strokeWidth = 1.0f * screenDensity
        nativeCanvas.drawRect(-safeW, -safeH, safeW, safeH, scratch.camSafeBoxPaint)
        nativeCanvas.drawLine(-hw - outerPad, -hh - outerPad, -safeW, -safeH, scratch.camOuterBorderPaint)
        nativeCanvas.drawLine(hw + outerPad, -hh - outerPad, safeW, -safeH, scratch.camOuterBorderPaint)
        nativeCanvas.drawLine(-hw - outerPad, hh + outerPad, -safeW, safeH, scratch.camOuterBorderPaint)
        nativeCanvas.drawLine(hw + outerPad, hh + outerPad, safeW, safeH, scratch.camOuterBorderPaint)

        scratch.camCornerPaint.strokeWidth = 2.8f * screenDensity
        val arm = min(frameW, frameH) * 0.085f
        nativeCanvas.drawLine(-hw, -hh, -hw + arm, -hh, scratch.camCornerPaint)
        nativeCanvas.drawLine(-hw, -hh, -hw, -hh + arm, scratch.camCornerPaint)
        nativeCanvas.drawLine(hw, -hh, hw - arm, -hh, scratch.camCornerPaint)
        nativeCanvas.drawLine(hw, -hh, hw, -hh + arm, scratch.camCornerPaint)
        nativeCanvas.drawLine(-hw, hh, -hw + arm, hh, scratch.camCornerPaint)
        nativeCanvas.drawLine(-hw, hh, -hw, hh - arm, scratch.camCornerPaint)
        nativeCanvas.drawLine(hw, hh, hw - arm, hh, scratch.camCornerPaint)
        nativeCanvas.drawLine(hw, hh, hw, hh - arm, scratch.camCornerPaint)

        scratch.camBadgeTextPaint.textSize = 9.5f * screenDensity
        val modeTag = if (camSettings.cameraMode == CameraMode.MODE_2D) "2D" else "3D"
        val label = "CÂMERA $modeTag ${camSettings.captureWidth}×${camSettings.captureHeight} • ${(evaluatedKf.zoom * 100).roundToInt()}% • FOV ${evaluatedKf.fovDegrees.roundToInt()}°"
        val tw = scratch.camBadgeTextPaint.measureText(label)
        nativeCanvas.drawRoundRect(
            -hw + 4f * screenDensity,
            -hh - 20f * screenDensity,
            -hw + tw + 14f * screenDensity,
            -hh - 4f * screenDensity,
            5f * screenDensity,
            5f * screenDensity,
            scratch.camBadgeBgPaint
        )
        nativeCanvas.drawText(label, -hw + 9f * screenDensity, -hh - 8f * screenDensity, scratch.camBadgeTextPaint)
        nativeCanvas.restore()
    }
}

internal data class Stage3DZPositions(
    val cameraApexZ: Float,
    val layerZs: List<Float>, // Same index order as layers (0 = bottommost, last = topmost/frontmost)
    val backgroundPlaneZ: Float
)

/**
 * Pure 3D stage depth ordering:
 * Observer/Camera faces toward +Z, so:
 *   cameraApexZ < topLayerZ <= ... <= bottomLayerZ < backgroundPlaneZ.
 */
internal fun computeStage3DZPositions(
    layerCount: Int,
    depthStep: Float,
    cameraFrontGap: Float
): Stage3DZPositions {
    val count = layerCount.coerceAtLeast(1)
    val mid = (count - 1) * 0.5f
    val layers = (0 until count).map { idx -> (mid - idx) * depthStep }
    val frontLayerZ = layers.last()
    val backLayerZ = layers.first()
    val bgZ = backLayerZ + depthStep * 0.85f
    val camZ = frontLayerZ - cameraFrontGap.coerceAtLeast(12f)
    return Stage3DZPositions(
        cameraApexZ = camZ,
        layerZs = layers,
        backgroundPlaneZ = bgZ
    )
}

private data class Stage3DPlaneItem(
    val zOrder: Float,
    val label: String?,
    val bitmap: Bitmap?,
    val opacity: Float,
    val isBackground: Boolean,
    val pTL: Projected3DPt,
    val pTR: Projected3DPt,
    val pBR: Projected3DPt,
    val pBL: Projected3DPt
)

private fun drawCameraPovStageViewport(
    nativeCanvas: AndroidCanvas,
    canvasW: Float,
    canvasH: Float,
    screenDensity: Float,
    camSettings: Camera3DSettings,
    evaluatedKf: Camera3DKeyframe,
    bgSettings: CanvasBackgroundSettings,
    povBitmap: Bitmap?,
    scratch: CanvasOverlayScratch
) {
    // Matte studio viewport framing the live rendered 3D camera output
    if (scratch.povMatteKeyH != canvasH) {
        scratch.povMattePaint.shader = LinearGradient(
            0f, 0f, 0f, canvasH,
            intArrayOf(AndroidColor.rgb(20, 23, 31), AndroidColor.rgb(30, 34, 44)),
            null,
            Shader.TileMode.CLAMP
        )
        scratch.povMatteKeyH = canvasH
    }
    nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, scratch.povMattePaint)

    val camAsp = (camSettings.captureWidth.toFloat() / camSettings.captureHeight.coerceAtLeast(1).toFloat()).coerceIn(0.35f, 2.8f)
    val padH = 44f * screenDensity
    val availW = (canvasW - 24f * screenDensity).coerceAtLeast(100f)
    val availH = (canvasH - padH - 18f * screenDensity).coerceAtLeast(100f)
    val viewW: Float
    val viewH: Float
    if (availW / availH > camAsp) {
        viewH = availH
        viewW = viewH * camAsp
    } else {
        viewW = availW
        viewH = viewW / camAsp
    }
    val left = (canvasW - viewW) * 0.5f
    val top = padH + (availH - viewH) * 0.5f
    scratch.dstRectF.set(left, top, left + viewW, top + viewH)

    if (povBitmap != null && !povBitmap.isRecycled) {
        if (bgSettings.style == CanvasBackgroundStyle.TRANSPARENTE) {
            nativeCanvas.save()
            nativeCanvas.clipRect(scratch.dstRectF)
            KodanimaGraphicsEngine.drawSkyBackdropToCanvas(
                nativeCanvas, canvasW, canvasH,
                if (bgSettings.skyMode == SkyBackdropMode.GRADE_ESPACIAL) bgSettings.copy(skyMode = SkyBackdropMode.ESTUDIO_ESCURO) else bgSettings
            )
            nativeCanvas.restore()
        }
        scratch.srcRect.set(0, 0, povBitmap.width, povBitmap.height)
        scratch.bitmapPaint.isFilterBitmap = true
        nativeCanvas.drawBitmap(povBitmap, scratch.srcRect, scratch.dstRectF, scratch.bitmapPaint)
    }

    scratch.povFramePaint.strokeWidth = 2.2f * screenDensity
    nativeCanvas.drawRect(scratch.dstRectF, scratch.povFramePaint)

    scratch.povInfoPaint.textSize = 10f * screenDensity
    val badge = "VISÃO DA CÂMERA • ${camSettings.captureWidth}×${camSettings.captureHeight} • Zoom ${(evaluatedKf.zoom * 100).roundToInt()}% • FOV ${evaluatedKf.fovDegrees.roundToInt()}° • Arraste para mover"
    nativeCanvas.drawText(badge, left, top - 6f * screenDensity, scratch.povInfoPaint)
}

private fun drawInteractive3DStageViewport(
    nativeCanvas: AndroidCanvas,
    canvasW: Float,
    canvasH: Float,
    projWidth: Float,
    projHeight: Float,
    screenDensity: Float,
    camSettings: Camera3DSettings,
    evaluatedKf: Camera3DKeyframe,
    bgSettings: CanvasBackgroundSettings,
    bgBitmap: Bitmap?,
    layers: List<Layer>,
    layerBitmaps: Map<String, Bitmap>,
    offCanvasLayerBitmaps: Map<String, Bitmap>,
    objectEntries: Map<String, CanvasBitmapCache.Stage3DObjectCacheEntry>,
    view3DYaw: Float,
    view3DPitch: Float,
    view3DPanX: Float,
    view3DPanY: Float,
    view3DZoom: Float,
    accentColorInt: Int,
    isMoveCameraMode: Boolean,
    stageSelection: StageSelection,
    gizmoOut: Stage3DCamGizmoState,
    objectBoundsOut: MutableMap<String, ProjectedObjectBounds>,
    layerBoundsOut: MutableMap<String, RectF>,
    scratch: CanvasOverlayScratch,
    tickFloat: Float
) {
    objectBoundsOut.clear()
    layerBoundsOut.clear()

    // 1. Clean 3D Studio Backdrop (NO 2D square screen-space grid in the 3D stage!)
    val stageSkySettings = if (bgSettings.skyMode == SkyBackdropMode.GRADE_ESPACIAL) {
        bgSettings.copy(skyMode = SkyBackdropMode.ESTUDIO_ESCURO)
    } else {
        bgSettings
    }
    KodanimaGraphicsEngine.drawSkyBackdropToCanvas(nativeCanvas, canvasW, canvasH, stageSkySettings)

    val yawRad = Math.toRadians(view3DYaw.toDouble())
    val pitchRad = Math.toRadians(view3DPitch.toDouble())
    val cosY = cos(yawRad).toFloat()
    val sinY = sin(yawRad).toFloat()
    val cosP = cos(pitchRad).toFloat()
    val sinP = sin(pitchRad).toFloat()

    val fovScale = (tan(Math.toRadians(25.0)) / tan(Math.toRadians(evaluatedKf.fovDegrees.coerceIn(18f, 120f) * 0.5)))
        .toFloat().coerceIn(0.35f, 2.6f)
    val focal = max(canvasW, canvasH) * 0.85f * fovScale
    val vpCx = canvasW * 0.5f + view3DPanX
    val vpCy = canvasH * 0.50f + view3DPanY

    // 2. Studio Floor 3D Perspective XZ Grid (optional visibility so final renders/previews stay clean)
    val floorY = min(canvasW, canvasH) * 0.25f * view3DZoom
    if (camSettings.showFloorGrid) {
        val gridHalf = min(canvasW, canvasH) * 0.62f * view3DZoom
        val gridPaint = scratch.stageGridPaint
        val gridSteps = 10
        for (i in -gridSteps..gridSteps) {
            val t = i.toFloat() / gridSteps.toFloat()
            val pos = t * gridHalf
            val edgeFade = (1f - abs(t) * 0.65f).coerceIn(0.25f, 1f)
            val minorAlpha = (42f * edgeFade).roundToInt()

            gridPaint.color = if (i == 0) AndroidColor.argb(190, 229, 77, 72) else AndroidColor.argb(minorAlpha, 210, 218, 232)
            gridPaint.strokeWidth = if (i == 0) 1.6f * screenDensity else 0.9f * screenDensity
            val a1 = projectPoint3D(-gridHalf, floorY, pos, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            val b1 = projectPoint3D(gridHalf, floorY, pos, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            nativeCanvas.drawLine(a1.x, a1.y, b1.x, b1.y, gridPaint)

            gridPaint.color = if (i == 0) AndroidColor.argb(190, 86, 189, 98) else AndroidColor.argb(minorAlpha, 210, 218, 232)
            gridPaint.strokeWidth = if (i == 0) 1.6f * screenDensity else 0.9f * screenDensity
            val a2 = projectPoint3D(pos, floorY, -gridHalf, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            val b2 = projectPoint3D(pos, floorY, gridHalf, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            nativeCanvas.drawLine(a2.x, a2.y, b2.x, b2.y, gridPaint)
        }
        val originPt = projectPoint3D(0f, floorY, 0f, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        scratch.stageOriginPaint.strokeWidth = 1.4f * screenDensity
        nativeCanvas.drawCircle(originPt.x, originPt.y, 4.5f * screenDensity, scratch.stageOriginPaint)
    }

    // 3. Collect Background Plane (+Z) + Transparent Layer Planes (unified projection scale)
    val cardW = min(canvasW, canvasH) * 0.28f * view3DZoom
    val cardH = cardW * (projHeight / projWidth.coerceAtLeast(1f)).coerceIn(0.42f, 1.55f)
    val stageProjScale = (2f * cardW) / projWidth.coerceAtLeast(1f)
    val layerCount = layers.size.coerceAtLeast(1)
    val depthStep = ((camSettings.layerDepthSpacing / 120f).coerceIn(0.10f, 1f) * (min(canvasW, canvasH) * 0.085f) + 14f * screenDensity) * view3DZoom
    val baseCamGap = min(canvasW, canvasH) * 0.34f * view3DZoom / evaluatedKf.zoom.coerceIn(0.30f, 3.5f)
    val zLayout = computeStage3DZPositions(layerCount, depthStep, baseCamGap)

    val planes = ArrayList<Stage3DPlaneItem>(layerCount + 1)

    if (bgSettings.style != CanvasBackgroundStyle.TRANSPARENTE && bgBitmap != null && !bgBitmap.isRecycled) {
        val bgZ = zLayout.backgroundPlaneZ
        val pTL = projectPoint3D(-cardW, -cardH, bgZ, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val pTR = projectPoint3D(cardW, -cardH, bgZ, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val pBR = projectPoint3D(cardW, cardH, bgZ, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val pBL = projectPoint3D(-cardW, cardH, bgZ, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val centerZ = (pTL.zDepth + pTR.zDepth + pBR.zDepth + pBL.zDepth) * 0.25f
        planes.add(Stage3DPlaneItem(centerZ, "Fundo (${bgSettings.style.labelPt})", bgBitmap, 1f, true, pTL, pTR, pBR, pBL))
    }

    var selectedTargetCenter3D: Triple<Float, Float, Float>? = null

    layers.forEachIndexed { idx, layer ->
        if (!layer.isVisible || layer.opacity <= 0f) return@forEachIndexed
        val lTf = Camera3DEngine.evaluateLayerTransformAtTick(
            camSettings.layerTransformTracks,
            layer.id,
            tickFloat,
            camSettings.autoInterpolate
        )
        val lOffX = lTf.x * stageProjScale
        val lOffY = lTf.y * stageProjScale
        val wz = zLayout.layerZs[idx] + lTf.z * stageProjScale
        val lw = cardW * lTf.scaleX
        val lh = cardH * lTf.scaleY
        val pTL = projectPoint3D(lOffX - lw, lOffY - lh, wz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val pTR = projectPoint3D(lOffX + lw, lOffY - lh, wz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val pBR = projectPoint3D(lOffX + lw, lOffY + lh, wz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val pBL = projectPoint3D(lOffX - lw, lOffY + lh, wz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val centerZ = (pTL.zDepth + pTR.zDepth + pBR.zDepth + pBL.zDepth) * 0.25f
        val isSel = stageSelection.type == StageTargetType.LAYER && stageSelection.id == layer.id
        if (isSel) selectedTargetCenter3D = Triple(lOffX, lOffY, wz)
        layerBoundsOut[layer.id] = RectF(
            min(min(pTL.x, pTR.x), min(pBR.x, pBL.x)),
            min(min(pTL.y, pTR.y), min(pBR.y, pBL.y)),
            max(max(pTL.x, pTR.x), max(pBR.x, pBL.x)),
            max(max(pTL.y, pTR.y), max(pBR.y, pBL.y))
        )
        val showLabel = isSel || idx == layers.lastIndex || layers.size <= 3
        val offBmp = offCanvasLayerBitmaps[layer.id]
        if (offBmp != null && !offBmp.isRecycled) {
            val ow = lw * 2f
            val oh = lh * 2f
            val oTL = projectPoint3D(lOffX - ow, lOffY - oh, wz + 0.8f, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            val oTR = projectPoint3D(lOffX + ow, lOffY - oh, wz + 0.8f, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            val oBR = projectPoint3D(lOffX + ow, lOffY + oh, wz + 0.8f, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            val oBL = projectPoint3D(lOffX - ow, lOffY + oh, wz + 0.8f, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
            planes.add(
                Stage3DPlaneItem(
                    zOrder = centerZ + 0.6f,
                    label = "Fora do Canvas (${layer.name})",
                    bitmap = offBmp,
                    opacity = (layer.opacity * lTf.opacity * 0.92f).coerceIn(0f, 1f),
                    isBackground = true,
                    pTL = oTL, pTR = oTR, pBR = oBR, pBL = oBL
                )
            )
        }
        planes.add(
            Stage3DPlaneItem(
                zOrder = centerZ,
                label = if (showLabel) (if (isSel) "★ ${layer.name}" else layer.name) else null,
                bitmap = layerBitmaps[layer.id],
                opacity = (layer.opacity * lTf.opacity).coerceIn(0f, 1f),
                isBackground = false,
                pTL = pTL, pTR = pTR, pBR = pBR, pBL = pBL
            )
        )
    }

    // 4. Camera Frustum Cone (aim matches Camera yaw/pitch/roll/zoom/x/y identically)
    val camDistZ = zLayout.cameraApexZ
    val targetX = evaluatedKf.x * stageProjScale
    val targetY = evaluatedKf.y * stageProjScale
    val camLookDist = max(cardW, cardH) * 0.65f
    val camShiftX = targetX + sin(Math.toRadians(evaluatedKf.yawY.toDouble())).toFloat() * camLookDist
    val camShiftY = targetY - sin(Math.toRadians(evaluatedKf.pitchX.toDouble())).toFloat() * camLookDist
    val pyramidLen = min(canvasW, canvasH) * 0.13f * view3DZoom
    val sensScale = (tan(Math.toRadians(evaluatedKf.fovDegrees.coerceIn(18f, 120f) * 0.5)) / tan(Math.toRadians(25.0))).toFloat().coerceIn(0.35f, 2.2f)
    val camAspect = (camSettings.captureWidth.toFloat() / camSettings.captureHeight.coerceAtLeast(1).toFloat()).coerceIn(0.45f, 2.2f)
    val sensW = cardW * 0.42f * sensScale
    val sensH = (sensW / camAspect).coerceIn(cardH * 0.22f, cardH * 0.85f)
    val nearZ = camDistZ + pyramidLen

    val rollRad = Math.toRadians(evaluatedKf.rotationZ.toDouble())
    val cosR = cos(rollRad).toFloat()
    val sinR = sin(rollRad).toFloat()
    fun rotCamCorner(lx: Float, ly: Float, zPlane: Float, cxOff: Float, cyOff: Float): Projected3DPt {
        val rx = lx * cosR - ly * sinR + cxOff
        val ry = lx * sinR + ly * cosR + cyOff
        return projectPoint3D(rx, ry, zPlane, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
    }

    val apex = projectPoint3D(camShiftX, camShiftY, camDistZ, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
    val cTL = rotCamCorner(-sensW, -sensH, nearZ, camShiftX * 0.72f + targetX * 0.28f, camShiftY * 0.72f + targetY * 0.28f)
    val cTR = rotCamCorner(sensW, -sensH, nearZ, camShiftX * 0.72f + targetX * 0.28f, camShiftY * 0.72f + targetY * 0.28f)
    val cBR = rotCamCorner(sensW, sensH, nearZ, camShiftX * 0.72f + targetX * 0.28f, camShiftY * 0.72f + targetY * 0.28f)
    val cBL = rotCamCorner(-sensW, sensH, nearZ, camShiftX * 0.72f + targetX * 0.28f, camShiftY * 0.72f + targetY * 0.28f)
    val upTriTip = rotCamCorner(0f, -sensH * 1.48f, nearZ, camShiftX * 0.72f + targetX * 0.28f, camShiftY * 0.72f + targetY * 0.28f)
    val camCenterZ = (apex.zDepth + (cTL.zDepth + cBR.zDepth) * 0.5f) * 0.5f

    val frontZ = zLayout.layerZs.last()
    val tgtScale = (1f / evaluatedKf.zoom.coerceIn(0.35f, 3.5f)) * sensScale.coerceIn(0.45f, 1.85f)
    val tTL = rotCamCorner(-cardW * tgtScale, -cardH * tgtScale, frontZ, targetX, targetY)
    val tTR = rotCamCorner(cardW * tgtScale, -cardH * tgtScale, frontZ, targetX, targetY)
    val tBR = rotCamCorner(cardW * tgtScale, cardH * tgtScale, frontZ, targetX, targetY)
    val tBL = rotCamCorner(-cardW * tgtScale, cardH * tgtScale, frontZ, targetX, targetY)

    // Precompute selected 3D object center if applicable
    if (stageSelection.type == StageTargetType.OBJECT_3D) {
        camSettings.scene3DObjects.firstOrNull { it.id == stageSelection.id }?.let { selObj ->
            val tf = Camera3DEngine.evaluateObjectTransformAtTick(selObj.keyframes, tickFloat, camSettings.autoInterpolate)
            selectedTargetCenter3D = Triple(
                tf.x * stageProjScale,
                tf.y * stageProjScale,
                tf.z * stageProjScale
            )
        }
    }

    val polyMatrix = scratch.stagePolyMatrix
    val layerBmpPaint = scratch.stageLayerBmpPaint
    val cardStrokePaint = scratch.stageCardStrokePaint
    val labelPaint = scratch.stageLabelPaint
    labelPaint.textSize = 9.5f * screenDensity
    val labelBgPaint = scratch.stageLabelBgPaint
    val cardPath = scratch.stageCardPath
    val srcPts = scratch.stageSrcPts
    val dstPts = scratch.stageDstPts

    fun drawPlane(plane: Stage3DPlaneItem) {
        val bmp = plane.bitmap
        if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            srcPts[0] = 0f; srcPts[1] = 0f
            srcPts[2] = bw; srcPts[3] = 0f
            srcPts[4] = bw; srcPts[5] = bh
            srcPts[6] = 0f; srcPts[7] = bh
            dstPts[0] = plane.pTL.x; dstPts[1] = plane.pTL.y
            dstPts[2] = plane.pTR.x; dstPts[3] = plane.pTR.y
            dstPts[4] = plane.pBR.x; dstPts[5] = plane.pBR.y
            dstPts[6] = plane.pBL.x; dstPts[7] = plane.pBL.y
            if (polyMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
                layerBmpPaint.alpha = (plane.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
                nativeCanvas.save()
                nativeCanvas.concat(polyMatrix)
                nativeCanvas.drawBitmap(bmp, 0f, 0f, layerBmpPaint)
                nativeCanvas.restore()
            }
        }
        cardPath.reset()
        cardPath.moveTo(plane.pTL.x, plane.pTL.y)
        cardPath.lineTo(plane.pTR.x, plane.pTR.y)
        cardPath.lineTo(plane.pBR.x, plane.pBR.y)
        cardPath.lineTo(plane.pBL.x, plane.pBL.y)
        cardPath.close()
        cardStrokePaint.color = when {
            plane.label?.startsWith("★ ") == true -> AndroidColor.rgb(0, 230, 118)
            plane.isBackground -> AndroidColor.argb(185, 165, 185, 220)
            else -> (accentColorInt and 0x00FFFFFF) or (200 shl 24)
        }
        cardStrokePaint.strokeWidth = if (plane.label?.startsWith("★ ") == true) 2.4f * screenDensity else 1.4f * screenDensity
        nativeCanvas.drawPath(cardPath, cardStrokePaint)
        plane.label?.let { lbl ->
            val lx = plane.pTL.x + 4f * screenDensity
            val ly = if (plane.isBackground) plane.pBL.y + 13f * screenDensity else plane.pTL.y - 6f * screenDensity
            val tw = labelPaint.measureText(lbl)
            nativeCanvas.drawRoundRect(
                lx - 4f * screenDensity,
                ly - 10f * screenDensity,
                lx + tw + 4f * screenDensity,
                ly + 3f * screenDensity,
                4f * screenDensity,
                4f * screenDensity,
                labelBgPaint
            )
            nativeCanvas.drawText(lbl, lx, ly, labelPaint)
        }
    }

    fun drawCameraFrustumOnly() {
        val isCamSelected = stageSelection.type == StageTargetType.CAMERA
        val rayPaint = scratch.stageRayPaint
        rayPaint.color = AndroidColor.argb(if (isMoveCameraMode || isCamSelected) 150 else 85, 255, 179, 64)
        rayPaint.strokeWidth = 1.1f * screenDensity
        if (scratch.stageRayDashDensity != screenDensity) {
            rayPaint.pathEffect = DashPathEffect(floatArrayOf(5f * screenDensity, 5f * screenDensity), 0f)
            scratch.stageRayDashDensity = screenDensity
        }
        nativeCanvas.drawLine(cTL.x, cTL.y, tTL.x, tTL.y, rayPaint)
        nativeCanvas.drawLine(cTR.x, cTR.y, tTR.x, tTR.y, rayPaint)
        nativeCanvas.drawLine(cBR.x, cBR.y, tBR.x, tBR.y, rayPaint)
        nativeCanvas.drawLine(cBL.x, cBL.y, tBL.x, tBL.y, rayPaint)
        nativeCanvas.drawLine(tTL.x, tTL.y, tTR.x, tTR.y, rayPaint)
        nativeCanvas.drawLine(tTR.x, tTR.y, tBR.x, tBR.y, rayPaint)
        nativeCanvas.drawLine(tBR.x, tBR.y, tBL.x, tBL.y, rayPaint)
        nativeCanvas.drawLine(tBL.x, tBL.y, tTL.x, tTL.y, rayPaint)

        val frustumStroke = scratch.stageFrustumStrokePaint
        frustumStroke.color = if (isCamSelected) AndroidColor.rgb(255, 193, 7) else AndroidColor.argb(170, 255, 167, 38)
        frustumStroke.strokeWidth = if (isCamSelected) 2.5f * screenDensity else 1.7f * screenDensity
        val frustumFill = scratch.stageFrustumFillPaint
        nativeCanvas.drawLine(apex.x, apex.y, cTL.x, cTL.y, frustumStroke)
        nativeCanvas.drawLine(apex.x, apex.y, cTR.x, cTR.y, frustumStroke)
        nativeCanvas.drawLine(apex.x, apex.y, cBR.x, cBR.y, frustumStroke)
        nativeCanvas.drawLine(apex.x, apex.y, cBL.x, cBL.y, frustumStroke)
        nativeCanvas.drawLine(cTL.x, cTL.y, cTR.x, cTR.y, frustumStroke)
        nativeCanvas.drawLine(cTR.x, cTR.y, cBR.x, cBR.y, frustumStroke)
        nativeCanvas.drawLine(cBR.x, cBR.y, cBL.x, cBL.y, frustumStroke)
        nativeCanvas.drawLine(cBL.x, cBL.y, cTL.x, cTL.y, frustumStroke)

        val topMid1X = cTL.x + (cTR.x - cTL.x) * 0.25f
        val topMid1Y = cTL.y + (cTR.y - cTL.y) * 0.25f - 3f * screenDensity
        val topMid2X = cTL.x + (cTR.x - cTL.x) * 0.75f
        val topMid2Y = cTL.y + (cTR.y - cTL.y) * 0.75f - 3f * screenDensity
        cardPath.reset()
        cardPath.moveTo(topMid1X, topMid1Y)
        cardPath.lineTo(topMid2X, topMid2Y)
        cardPath.lineTo(upTriTip.x, upTriTip.y)
        cardPath.close()
        nativeCanvas.drawPath(cardPath, frustumFill)

        nativeCanvas.drawCircle(apex.x, apex.y, 5.5f * screenDensity, frustumFill)
    }

    fun drawActiveGizmoAt(gx: Float, gy: Float, gz: Float) {
        val center = projectPoint3D(gx, gy, gz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val handleLen = 46f * screenDensity
        val xEnd = projectPoint3D(gx + handleLen, gy, gz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val yEnd = projectPoint3D(gx, gy + handleLen, gz, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)
        val zEnd = projectPoint3D(gx, gy, gz + handleLen, cosY, sinY, cosP, sinP, focal, vpCx, vpCy)

        val gizmoPaint = scratch.stageGizmoPaint
        gizmoPaint.strokeWidth = 2.3f * screenDensity
        val gizmoHandleFill = scratch.stageGizmoHandleFillPaint
        val gizmoTextPaint = scratch.stageGizmoTextPaint
        gizmoTextPaint.textSize = 8f * screenDensity

        gizmoPaint.color = AndroidColor.rgb(239, 83, 80)
        gizmoHandleFill.color = gizmoPaint.color
        nativeCanvas.drawLine(center.x, center.y, xEnd.x, xEnd.y, gizmoPaint)
        nativeCanvas.drawCircle(xEnd.x, xEnd.y, 7f * screenDensity, gizmoHandleFill)
        nativeCanvas.drawText("X", xEnd.x, xEnd.y + 3f * screenDensity, gizmoTextPaint)

        gizmoPaint.color = AndroidColor.rgb(102, 187, 106)
        gizmoHandleFill.color = gizmoPaint.color
        nativeCanvas.drawLine(center.x, center.y, yEnd.x, yEnd.y, gizmoPaint)
        nativeCanvas.drawCircle(yEnd.x, yEnd.y, 7f * screenDensity, gizmoHandleFill)
        nativeCanvas.drawText("Y", yEnd.x, yEnd.y + 3f * screenDensity, gizmoTextPaint)

        gizmoPaint.color = AndroidColor.rgb(66, 165, 245)
        gizmoHandleFill.color = gizmoPaint.color
        nativeCanvas.drawLine(center.x, center.y, zEnd.x, zEnd.y, gizmoPaint)
        nativeCanvas.drawCircle(zEnd.x, zEnd.y, 7f * screenDensity, gizmoHandleFill)
        nativeCanvas.drawText("Z", zEnd.x, zEnd.y + 3f * screenDensity, gizmoTextPaint)

        gizmoHandleFill.color = AndroidColor.rgb(255, 193, 7)
        nativeCanvas.drawCircle(center.x, center.y, 6.5f * screenDensity, gizmoHandleFill)
        gizmoPaint.color = AndroidColor.WHITE
        gizmoPaint.strokeWidth = 1.4f * screenDensity
        nativeCanvas.drawCircle(center.x, center.y, 6.5f * screenDensity, gizmoPaint)

        val xdx = xEnd.x - center.x; val xdy = xEnd.y - center.y
        val xLen = hypot(xdx, xdy).coerceAtLeast(1f)
        val ydx = yEnd.x - center.x; val ydy = yEnd.y - center.y
        val yLen = hypot(ydx, ydy).coerceAtLeast(1f)
        val zdx = zEnd.x - center.x; val zdy = zEnd.y - center.y
        val zLen = hypot(zdx, zdy).coerceAtLeast(1f)

        gizmoOut.valid = true
        gizmoOut.apexX = center.x; gizmoOut.apexY = center.y
        gizmoOut.xTipX = xEnd.x; gizmoOut.xTipY = xEnd.y
        gizmoOut.yTipX = yEnd.x; gizmoOut.yTipY = yEnd.y
        gizmoOut.zTipX = zEnd.x; gizmoOut.zTipY = zEnd.y
        gizmoOut.xDirScreenX = xdx / xLen; gizmoOut.xDirScreenY = xdy / xLen
        gizmoOut.yDirScreenX = ydx / yLen; gizmoOut.yDirScreenY = ydy / yLen
        gizmoOut.zDirScreenX = zdx / zLen; gizmoOut.zDirScreenY = zdy / zLen
    }

    // Painter's order across 2D planes, background-rasterized 3D objects, and Camera Frustum
    val visible3DObjs = camSettings.scene3DObjects.filter { it.isVisible }
    val drawItems = ArrayList<Pair<Float, () -> Unit>>(planes.size + visible3DObjs.size + 1)
    planes.forEach { p -> drawItems.add(p.zOrder to { drawPlane(p) }) }

    scratch.dstRectF.set(0f, 0f, canvasW, canvasH)
    for (obj in visible3DObjs) {
        val entry = objectEntries[obj.id] ?: continue
        val bmp = entry.bitmap
        val b = entry.bounds
        if (bmp.isRecycled || b == null) continue
        objectBoundsOut[obj.id] = b
        drawItems.add(b.avgDepth to {
            scratch.srcRect.set(0, 0, bmp.width, bmp.height)
            layerBmpPaint.alpha = 255
            nativeCanvas.drawBitmap(bmp, scratch.srcRect, scratch.dstRectF, layerBmpPaint)
            if (stageSelection.type == StageTargetType.OBJECT_3D && stageSelection.id == obj.id) {
                nativeCanvas.drawText("★ ${obj.name}", b.minX, b.minY - 5f * screenDensity, labelPaint)
            }
        })
    }

    drawItems.add(camCenterZ to { drawCameraFrustumOnly() })
    drawItems.sortedByDescending { it.first }.forEach { it.second.invoke() }

    // Draw interactive 3D gizmo always visible on top of the selected stage element
    val gizmoPos = selectedTargetCenter3D ?: Triple(camShiftX, camShiftY, camDistZ)
    drawActiveGizmoAt(gizmoPos.first, gizmoPos.second, gizmoPos.third)

    // 5. Top-right XYZ Axis Orientation Gizmo
    val gizmoCx = canvasW - 36f * screenDensity
    val gizmoCy = 66f * screenDensity
    val gizmoArm = 20f * screenDensity
    val xTip = projectPoint3D(gizmoArm, 0f, 0f, cosY, sinY, cosP, sinP, 10000f, gizmoCx, gizmoCy)
    val yTip = projectPoint3D(0f, -gizmoArm, 0f, cosY, sinY, cosP, sinP, 10000f, gizmoCx, gizmoCy)
    val zTip = projectPoint3D(0f, 0f, gizmoArm, cosY, sinY, cosP, sinP, 10000f, gizmoCx, gizmoCy)
    val axisList = listOf(
        Triple(xTip, AndroidColor.rgb(239, 83, 80), "X"),
        Triple(yTip, AndroidColor.rgb(102, 187, 106), "Z"),
        Triple(zTip, AndroidColor.rgb(66, 165, 245), "Y")
    ).sortedByDescending { it.first.zDepth }
    scratch.stageAxisLinePaint.strokeWidth = 2.0f * screenDensity
    scratch.stageAxisTextPaint.textSize = 8f * screenDensity
    axisList.forEach { (tip, col, lbl) ->
        scratch.stageAxisLinePaint.color = col
        scratch.stageAxisDotPaint.color = col
        nativeCanvas.drawLine(gizmoCx, gizmoCy, tip.x, tip.y, scratch.stageAxisLinePaint)
        nativeCanvas.drawCircle(tip.x, tip.y, 6.5f * screenDensity, scratch.stageAxisDotPaint)
        nativeCanvas.drawText(lbl, tip.x, tip.y + 3f * screenDensity, scratch.stageAxisTextPaint)
    }

    val selName = when (stageSelection.type) {
        StageTargetType.CAMERA -> "Câmera"
        StageTargetType.LAYER -> layers.firstOrNull { it.id == stageSelection.id }?.name ?: "Camada"
        StageTargetType.OBJECT_3D -> camSettings.scene3DObjects.firstOrNull { it.id == stageSelection.id }?.name ?: "Objeto 3D"
    }
    val modeHint = if (isMoveCameraMode) "Mover $selName" else "Órbita (${view3DYaw.roundToInt()}°, ${view3DPitch.roundToInt()}°)"
    val hudHint = "ESTÚDIO 3D • Sel: $selName • $modeHint • FOV ${evaluatedKf.fovDegrees.roundToInt()}° • Z ${(evaluatedKf.zoom * 100).roundToInt()}%"
    nativeCanvas.drawText(hudHint, 12f * screenDensity, canvasH - 12f * screenDensity, labelPaint)
}

