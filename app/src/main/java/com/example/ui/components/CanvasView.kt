package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.example.engine.CursorHelper
import com.example.engine.HardwareCapabilitiesEngine
import com.example.engine.KodanimaBitmapPool
import com.example.engine.KodanimaGraphicsEngine
import com.example.engine.MediaAssetManager
import com.example.engine.SelectionGeometryHelper
import com.example.model.AnimationProject
import com.example.model.BezierNode
import com.example.model.DrawingStroke
import com.example.model.GridRulerSettings
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.model.MultiFingerGesture
import com.example.model.OnionSkinSettings
import com.example.model.PerspectiveRulerSettings
import com.example.model.PressureCurve
import com.example.model.PressureMode
import com.example.model.SelectionRegion
import com.example.model.StrokePoint
import com.example.model.TabletDriverSettings
import com.example.model.ToolType
import com.example.model.sampleCubicBezierNodesToStrokePoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Holds pooled offscreen GPU/RAM bitmaps for O(1) drawing, erasing, onion skinning,
 * and RenderThread 120 FPS zoom/pan/rotate without re-rasterizing committed strokes.
 */
private class CanvasBitmapCache {
    data class AdvancedCachedLayer(
        val layer: Layer,
        val bitmap: Bitmap,
        val key: LayerSliceCacheKey
    )

    var cacheW: Int = 0
    var cacheH: Int = 0

    var onionBitmap: Bitmap? = null
    var onionKey: Any? = null

    var belowLayersBitmap: Bitmap? = null
    var belowKey: Any? = null

    var clipBaseBitmap: Bitmap? = null
    var clipBaseKey: Any? = null

    var activeLayerBitmap: Bitmap? = null
    var activeKey: Any? = null

    var aboveLayersBitmap: Bitmap? = null
    var aboveKey: Any? = null

    val aboveAdvancedLayers: MutableList<AdvancedCachedLayer> = mutableListOf()
    var aboveAdvancedIsActive: Boolean = false

    var eraserWorkBitmap: Bitmap? = null
    var eraserWorkCanvas: AndroidCanvas? = null
    var lastErasedPointIdx: Int = 0

    var activeStrokeBitmap: Bitmap? = null
    var activeStrokeCanvas: AndroidCanvas? = null
    var lastRenderedStrokeIdx: Int = 0

    fun ensureSize(w: Int, h: Int) {
        if (w == cacheW && h == cacheH) return
        releaseAll()
        cacheW = w
        cacheH = h
    }

    fun acquireOrReuse(current: Bitmap?, clearColor: Int): Bitmap {
        if (current != null && !current.isRecycled && current.width == cacheW && current.height == cacheH) {
            current.eraseColor(clearColor)
            return current
        }
        KodanimaBitmapPool.release(current)
        return KodanimaBitmapPool.acquire(cacheW, cacheH, Bitmap.Config.ARGB_8888, clearColor)
    }

    fun beginEraserSession() {
        val base = activeLayerBitmap
        val dst = acquireOrReuse(eraserWorkBitmap, AndroidColor.TRANSPARENT)
        eraserWorkBitmap = dst
        val c = eraserWorkCanvas ?: AndroidCanvas(dst)
        c.setBitmap(dst)
        eraserWorkCanvas = c
        if (base != null && !base.isRecycled) {
            c.drawBitmap(base, 0f, 0f, null)
        }
        lastErasedPointIdx = 0
    }

    fun beginActiveStrokeSession() {
        val dst = acquireOrReuse(activeStrokeBitmap, AndroidColor.TRANSPARENT)
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

    fun sampleEyedropperPixel(px: Int, py: Int): Long {
        if (cacheW <= 0 || cacheH <= 0) return 0xFFFFFFFFL
        val sx = px.coerceIn(0, cacheW - 1)
        val sy = py.coerceIn(0, cacheH - 1)
        var argb = AndroidColor.WHITE
        belowLayersBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
            argb = blendOver(argb, bmp.getPixel(sx, sy))
        }
        activeLayerBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
            argb = blendOver(argb, bmp.getPixel(sx, sy))
        }
        if (aboveAdvancedIsActive) {
            aboveAdvancedLayers.forEach { adv ->
                if (adv.layer.isVisible && !adv.bitmap.isRecycled) {
                    argb = blendOver(argb, adv.bitmap.getPixel(sx, sy))
                }
            }
        } else {
            aboveLayersBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                argb = blendOver(argb, bmp.getPixel(sx, sy))
            }
        }
        return argb.toLong() and 0xFFFFFFFFL
    }

    private fun blendOver(dst: Int, src: Int): Int {
        val sa = (src ushr 24) and 0xFF
        if (sa == 0) return dst
        if (sa == 255) return src
        val invSa = 255 - sa
        val r = (((src ushr 16) and 0xFF) * sa + ((dst ushr 16) and 0xFF) * invSa) / 255
        val g = (((src ushr 8) and 0xFF) * sa + ((dst ushr 8) and 0xFF) * invSa) / 255
        val b = ((src and 0xFF) * sa + (dst and 0xFF) * invSa) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun clearAboveAdvanced() {
        aboveAdvancedLayers.forEach { KodanimaBitmapPool.release(it.bitmap) }
        aboveAdvancedLayers.clear()
        aboveAdvancedIsActive = false
    }

    fun releaseAll() {
        KodanimaBitmapPool.release(onionBitmap); onionBitmap = null; onionKey = null
        KodanimaBitmapPool.release(belowLayersBitmap); belowLayersBitmap = null; belowKey = null
        KodanimaBitmapPool.release(clipBaseBitmap); clipBaseBitmap = null; clipBaseKey = null
        KodanimaBitmapPool.release(activeLayerBitmap); activeLayerBitmap = null; activeKey = null
        KodanimaBitmapPool.release(aboveLayersBitmap); aboveLayersBitmap = null; aboveKey = null
        clearAboveAdvanced()
        endActiveSessions()
        cacheW = 0
        cacheH = 0
    }
}

private data class OnionCacheKey(
    val frameIndex: Int,
    val enabled: Boolean,
    val settings: OnionSkinSettings,
    val prevLayers: List<List<Layer>>,
    val nextLayers: List<List<Layer>>,
    val w: Int,
    val h: Int,
    val densityMult: Float
)

private data class LayerSliceCacheKey(
    val layers: List<Layer>,
    val w: Int,
    val h: Int,
    val densityMult: Float,
    val hiddenStrokeIds: Set<String> = emptySet(),
    val replacementStrokes: List<DrawingStroke> = emptyList()
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
    val effectiveMediaTimeMs = remember(activeScrubTimeMs, project.frames, currentFrameIndex, project.fps) {
        activeScrubTimeMs ?: MediaAssetManager.computeTimeMsForFrame(
            project.frames,
            currentFrameIndex,
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

    // Cursor & live stroke broadcast throttling (~30Hz cursor / ~20Hz live preview)
    var lastCursorBroadcastMs by remember { mutableLongStateOf(0L) }
    var lastLiveStrokeBroadcastMs by remember { mutableLongStateOf(0L) }
    var lastBroadcastX by remember { mutableStateOf(-10000f) }
    var lastBroadcastY by remember { mutableStateOf(-10000f) }
    var lastBroadcastDrawing by remember { mutableStateOf(false) }

    // Virtual tablet cursor state (local & remote custom .cur/.ani/.png bitmaps)
    var cursorScreenPos by remember { mutableStateOf<Offset?>(null) }
    val customCursorBitmap = remember(tabletSettings.customCursorPath) {
        tabletSettings.customCursorPath?.let { CursorHelper.loadCursorBitmapFromFile(it) }
    }
    DisposableEffect(customCursorBitmap) {
        onDispose {
            if (customCursorBitmap != null && !customCursorBitmap.isRecycled) {
                customCursorBitmap.recycle()
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

    // Offscreen GPU-backed bitmap cache holder
    val cacheHolder = remember { CanvasBitmapCache() }
    DisposableEffect(Unit) {
        onDispose {
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
    val currFrame = project.frames.getOrNull(currentFrameIndex)
    val timelineLayers = currFrame?.layers ?: emptyList()
    val localLayers = currFrame?.localLayers ?: emptyList()
    val combinedLayers = remember(timelineLayers, localLayers) { timelineLayers + localLayers }
    val clampedTimelineLayerIdx = if (timelineLayers.isNotEmpty()) {
        activeLayerIndex.coerceIn(0, timelineLayers.lastIndex)
    } else 0
    val clampedLocalLayerIdx = if (localLayers.isNotEmpty()) {
        activeLocalLayerIndex.coerceIn(0, localLayers.lastIndex)
    } else 0
    val activeGlobalLayerIdx = if (combinedLayers.isNotEmpty()) {
        if (activeTargetIsFrameLocalLayer && localLayers.isNotEmpty()) {
            (timelineLayers.size + clampedLocalLayerIdx).coerceIn(0, combinedLayers.lastIndex)
        } else {
            clampedTimelineLayerIdx.coerceIn(0, combinedLayers.lastIndex)
        }
    } else 0
    val activeLayerObj = combinedLayers.getOrNull(activeGlobalLayerIdx)

    val activeSelectionClipPoly = remember(activeSelection) {
        if (activeSelection.isActive && activeSelection.points.size >= 3) {
            SelectionGeometryHelper.transformSelectionPolygon(activeSelection)
        } else null
    }

    // Rebuild static caches only when underlying frame/layer/onion skin/selection split changes (ZERO work on pan/zoom/move)
    LaunchedEffect(
        cacheW,
        cacheH,
        currentFrameIndex,
        activeGlobalLayerIdx,
        currFrame,
        onionSkinEnabled,
        project.onionSkin,
        densityMult,
        activeSelection.isActive,
        activeSelection.hiddenSourceStrokeIds,
        activeSelection.outsideReplacementStrokes
    ) {
        cacheHolder.ensureSize(cacheW, cacheH)

        // 1. Onion Skin Cache (includes timeline layers + per-frame localLayers)
        val prevList = if (onionSkinEnabled && project.onionSkin.isEnabled) {
            (project.onionSkin.prevFramesCount downTo 1).mapNotNull { step ->
                project.frames.getOrNull(currentFrameIndex - step)?.let { it.layers + it.localLayers }
            }
        } else emptyList()
        val nextList = if (onionSkinEnabled && project.onionSkin.isEnabled) {
            (1..project.onionSkin.nextFramesCount).mapNotNull { step ->
                project.frames.getOrNull(currentFrameIndex + step)?.let { it.layers + it.localLayers }
            }
        } else emptyList()

        val oKey = OnionCacheKey(
            currentFrameIndex,
            onionSkinEnabled && project.onionSkin.isEnabled,
            project.onionSkin,
            prevList,
            nextList,
            cacheW,
            cacheH,
            densityMult
        )
        if (cacheHolder.onionKey != oKey) {
            val bmp = cacheHolder.acquireOrReuse(cacheHolder.onionBitmap, AndroidColor.TRANSPARENT)
            if (oKey.enabled && (prevList.isNotEmpty() || nextList.isNotEmpty())) {
                val c = AndroidCanvas(bmp)
                c.scale(cacheScale, cacheScale)
                val onionOpacity = project.onionSkin.opacity

                val prevFilter = KodanimaGraphicsEngine.createOnionSkinColorFilter(project.onionSkin.prevColor)
                val prevCount = project.onionSkin.prevFramesCount
                for (step in prevCount downTo 1) {
                    val f = project.frames.getOrNull(currentFrameIndex - step) ?: continue
                    val fLayers = f.layers + f.localLayers
                    val alpha = (onionOpacity * (1f - (step - 1) * 0.25f)).coerceAtLeast(0.1f)
                    for (l in fLayers) {
                        if (l.isVisible) {
                            KodanimaGraphicsEngine.drawLayerToCanvas(
                                l, c, project.resolution.width, project.resolution.height,
                                alpha, prevFilter, null, null, densityMult
                            )
                        }
                    }
                }

                val nextFilter = KodanimaGraphicsEngine.createOnionSkinColorFilter(project.onionSkin.nextColor)
                val nextCount = project.onionSkin.nextFramesCount
                for (step in 1..nextCount) {
                    val f = project.frames.getOrNull(currentFrameIndex + step) ?: continue
                    val fLayers = f.layers + f.localLayers
                    val alpha = (onionOpacity * (1f - (step - 1) * 0.25f)).coerceAtLeast(0.1f)
                    for (l in fLayers) {
                        if (l.isVisible) {
                            KodanimaGraphicsEngine.drawLayerToCanvas(
                                l, c, project.resolution.width, project.resolution.height,
                                alpha, nextFilter, null, null, densityMult
                            )
                        }
                    }
                }
            }
            cacheHolder.onionBitmap = bmp
            cacheHolder.onionKey = oKey
        }

        // 2. Static Frame Layers (Below Active, Clip Base if needed, Active, Above Active)
        val belowSlice = if (combinedLayers.isNotEmpty() && activeGlobalLayerIdx > 0) {
            combinedLayers.subList(0, activeGlobalLayerIdx)
        } else emptyList()
        val activeSlice = if (activeGlobalLayerIdx in combinedLayers.indices) {
            listOf(combinedLayers[activeGlobalLayerIdx])
        } else emptyList()
        val aboveSlice = if (activeGlobalLayerIdx + 1 < combinedLayers.size) {
            combinedLayers.subList(activeGlobalLayerIdx + 1, combinedLayers.size)
        } else emptyList()

        val bKey = LayerSliceCacheKey(belowSlice, cacheW, cacheH, densityMult)
        if (cacheHolder.belowKey != bKey) {
            val bmp = cacheHolder.acquireOrReuse(cacheHolder.belowLayersBitmap, AndroidColor.TRANSPARENT)
            if (belowSlice.isNotEmpty()) {
                val c = AndroidCanvas(bmp)
                KodanimaGraphicsEngine.drawLayersStackToCanvas(
                    layers = belowSlice,
                    canvas = c,
                    targetWidth = project.resolution.width,
                    targetHeight = project.resolution.height,
                    densityMultiplier = densityMult,
                    renderScale = cacheScale
                )
            }
            cacheHolder.belowLayersBitmap = bmp
            cacheHolder.belowKey = bKey
        }

        val rawActiveLayer = activeSlice.firstOrNull()
        val clipBaseLayer = if (rawActiveLayer != null && rawActiveLayer.isClippingMask) {
            belowSlice.lastOrNull { !it.isClippingMask && it.isVisible && it.opacity > 0f }
        } else null
        val cbSlice = if (clipBaseLayer != null) listOf(clipBaseLayer) else emptyList()
        val cbKey = LayerSliceCacheKey(cbSlice, cacheW, cacheH, densityMult)
        if (cacheHolder.clipBaseKey != cbKey) {
            if (cbSlice.isNotEmpty()) {
                val bmp = cacheHolder.acquireOrReuse(cacheHolder.clipBaseBitmap, AndroidColor.TRANSPARENT)
                val c = AndroidCanvas(bmp)
                c.scale(cacheScale, cacheScale)
                KodanimaGraphicsEngine.drawLayerToCanvas(
                    cbSlice[0], c, project.resolution.width, project.resolution.height,
                    1.0f, null, null, null, densityMult
                )
                cacheHolder.clipBaseBitmap = bmp
            } else {
                KodanimaBitmapPool.release(cacheHolder.clipBaseBitmap)
                cacheHolder.clipBaseBitmap = null
            }
            cacheHolder.clipBaseKey = cbKey
        }

        val hideIds = if (activeSelection.isActive) activeSelection.hiddenSourceStrokeIds else emptySet()
        val outsideRepl = if (activeSelection.isActive) activeSelection.outsideReplacementStrokes else emptyList()
        val effectiveActiveSlice = if (rawActiveLayer != null && (hideIds.isNotEmpty() || outsideRepl.isNotEmpty())) {
            listOf(rawActiveLayer.copy(strokes = rawActiveLayer.strokes.filter { it.id !in hideIds } + outsideRepl))
        } else {
            activeSlice
        }
        val aKey = LayerSliceCacheKey(effectiveActiveSlice, cacheW, cacheH, densityMult, hideIds, outsideRepl)
        if (cacheHolder.activeKey != aKey) {
            val bmp = cacheHolder.acquireOrReuse(cacheHolder.activeLayerBitmap, AndroidColor.TRANSPARENT)
            if (effectiveActiveSlice.isNotEmpty()) {
                val l = effectiveActiveSlice[0]
                if (l.isVisible) {
                    val c = AndroidCanvas(bmp)
                    c.scale(cacheScale, cacheScale)
                    KodanimaGraphicsEngine.drawLayerToCanvas(
                        l, c, project.resolution.width, project.resolution.height,
                        1.0f, null, null, null, densityMult
                    )
                }
            }
            cacheHolder.activeLayerBitmap = bmp
            cacheHolder.activeKey = aKey
        }

        val aboveNeedsAdvanced = aboveSlice.any {
            it.isClippingMask || it.blendEffect != LayerBlendEffect.NORMAL
        }
        if (!aboveNeedsAdvanced) {
            if (cacheHolder.aboveAdvancedIsActive) {
                cacheHolder.clearAboveAdvanced()
            }
            val abKey = LayerSliceCacheKey(aboveSlice, cacheW, cacheH, densityMult)
            if (cacheHolder.aboveKey != abKey) {
                val bmp = cacheHolder.acquireOrReuse(cacheHolder.aboveLayersBitmap, AndroidColor.TRANSPARENT)
                if (aboveSlice.isNotEmpty()) {
                    val c = AndroidCanvas(bmp)
                    c.scale(cacheScale, cacheScale)
                    for (l in aboveSlice) {
                        if (l.isVisible) {
                            KodanimaGraphicsEngine.drawLayerToCanvas(
                                l, c, project.resolution.width, project.resolution.height,
                                1.0f, null, null, null, densityMult
                            )
                        }
                    }
                }
                cacheHolder.aboveLayersBitmap = bmp
                cacheHolder.aboveKey = abKey
            }
        } else {
            cacheHolder.aboveKey = null
            // Rebuild only layers in aboveSlice whose strokes/settings changed
            val oldList = cacheHolder.aboveAdvancedLayers.toList()
            val nextList = ArrayList<CanvasBitmapCache.AdvancedCachedLayer>(aboveSlice.size)
            for (idx in aboveSlice.indices) {
                val l = aboveSlice[idx]
                val lKey = LayerSliceCacheKey(listOf(l), cacheW, cacheH, densityMult)
                val reused = oldList.getOrNull(idx)
                if (reused != null && reused.key == lKey && !reused.bitmap.isRecycled) {
                    nextList.add(reused)
                } else {
                    val bmp = KodanimaBitmapPool.acquire(cacheW, cacheH, Bitmap.Config.ARGB_8888, AndroidColor.TRANSPARENT)
                    if (l.isVisible && l.opacity > 0f) {
                        val c = AndroidCanvas(bmp)
                        c.scale(cacheScale, cacheScale)
                        KodanimaGraphicsEngine.drawLayerToCanvas(
                            l, c, project.resolution.width, project.resolution.height,
                            1.0f, null, null, null, densityMult
                        )
                    }
                    nextList.add(CanvasBitmapCache.AdvancedCachedLayer(l, bmp, lKey))
                }
            }
            val keptBmps = nextList.map { it.bitmap }.toSet()
            oldList.forEach { old ->
                if (old.bitmap !in keptBmps) {
                    KodanimaBitmapPool.release(old.bitmap)
                }
            }
            cacheHolder.aboveAdvancedLayers.clear()
            cacheHolder.aboveAdvancedLayers.addAll(nextList)
            cacheHolder.aboveAdvancedIsActive = true
        }
        activeStrokeVersion++
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

        val density = LocalDensity.current
        val halfContainerW = containerWidth * 0.5f
        val halfContainerH = containerHeight * 0.5f
        val halfBaseW = baseWidth * 0.5f
        val halfBaseH = baseHeight * 0.5f
        val invZoom = 1f / zoom.coerceAtLeast(0.05f)
        val screenToProjScaleX = projWidth / baseWidth
        val screenToProjScaleY = projHeight / baseHeight
        val projToScreenScaleX = baseWidth / projWidth
        val projToScreenScaleY = baseHeight / projHeight

        fun screenToProjectCoords(screenX: Float, screenY: Float): Offset {
            val dx = (screenX - halfContainerW - panX) * invZoom
            val flipDx = if (mirrorHorizontal) -dx else dx
            val dy = (screenY - halfContainerH - panY) * invZoom
            val rx = flipDx * cosNegR - dy * sinNegR
            val ry = flipDx * sinNegR + dy * cosNegR
            val px = (rx + halfBaseW) * screenToProjScaleX
            val py = (ry + halfBaseH) * screenToProjScaleY
            return Offset(px, py)
        }

        fun applyTabletInversion(rawX: Float, rawY: Float): Offset {
            val px = if (tabletSettings.invertX) (projWidth - rawX).coerceIn(0f, projWidth) else rawX
            val py = if (tabletSettings.invertY) (projHeight - rawY).coerceIn(0f, projHeight) else rawY
            return Offset(px, py)
        }

        fun projectToScreenCoords(projX: Float, projY: Float): Offset {
            val localX = projX * projToScreenScaleX - halfBaseW
            val localY = projY * projToScreenScaleY - halfBaseH
            val rx = localX * cosPosR - localY * sinPosR
            val ry = localX * sinPosR + localY * cosPosR
            val finalRx = if (mirrorHorizontal) -rx else rx
            return Offset(halfContainerW + panX + finalRx * zoom, halfContainerH + panY + ry * zoom)
        }

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
                // Capacitive touch drivers vary wildly across devices (e.g. Moto G 0.25..0.55 vs Tecno 1.0/0.0).
                // Keep brushSize faithful (e.g. 6px stays 6px across Host & Visitor) with subtle weight feel.
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
            if (selectedTool == ToolType.ERASER) {
                val eCanvas = cacheHolder.eraserWorkCanvas ?: return
                val startIdx = max(0, cacheHolder.lastErasedPointIdx - 1)
                val endIdx = currentPoints.lastIndex
                if (endIdx > cacheHolder.lastErasedPointIdx || currentPoints.size == 1) {
                    val eraserStroke = DrawingStroke(
                        tool = ToolType.ERASER,
                        brushSize = brushSize,
                        opacity = brushOpacity,
                        points = currentPoints,
                        brushPresetId = eraserBrushPresetId
                    )
                    eCanvas.save()
                    eCanvas.scale(cacheScale, cacheScale)
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
                brushPresetId != 3 // Krita Ink-3 G-Pen uses end tapering
            ) {
                val sCanvas = cacheHolder.activeStrokeCanvas ?: return
                val activeStroke = DrawingStroke(
                    tool = selectedTool,
                    color = currentColor,
                    brushSize = brushSize,
                    opacity = brushOpacity,
                    points = currentPoints,
                    brushPresetId = brushPresetId,
                    customBrushParams = activeBrushParamsJson,
                    clipPolygon = activeSelectionClipPoly
                )
                if (currentPoints.size <= 280) {
                    // Full quadratic midpoint Bezier curve pass on active buffer for silky-smooth live strokes
                    sCanvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    sCanvas.save()
                    sCanvas.scale(cacheScale, cacheScale)
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
                        sCanvas.scale(cacheScale, cacheScale)
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

        // Viewport touch interceptor for unified 1-finger draw, 2-finger zoom/pan/rotate & 2/3/4-finger gestures
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
                            onLiveStrokePreview?.invoke(null)
                            activeStrokeVersion++
                        }
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

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_POINTER_DOWN -> {
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
                                    gestureStartCenter = allCentroid
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                gestureMaxPointers = max(gestureMaxPointers, pointerCount)
                                if (pointerCount == 2 && gestureMaxPointers == 2) {
                                    var angleFromStart = currentAngle - gestureStartAngle
                                    while (angleFromStart > 180f) angleFromStart -= 360f
                                    while (angleFromStart < -180f) angleFromStart += 360f
                                    val movedFromStart = hypot(
                                        allCentroid.x - gestureStartCenter.x,
                                        allCentroid.y - gestureStartCenter.y
                                    )
                                    if (!didTransformMove && (
                                            movedFromStart > 14f ||
                                                abs(currentDist - gestureStartDist) > 14f ||
                                                abs(angleFromStart) > 4.5f
                                            )
                                    ) {
                                        didTransformMove = true
                                        lastCenter = currentCenter
                                        lastDistance = currentDist
                                        lastAngle = currentAngle
                                    }
                                    if (didTransformMove && isTransforming && lastDistance > 0f) {
                                        val zoomFactor = (currentDist / lastDistance).coerceIn(0.7f, 1.4f)
                                        val panDelta = currentCenter - lastCenter
                                        var angleDiff = currentAngle - lastAngle
                                        while (angleDiff > 180f) angleDiff -= 360f
                                        while (angleDiff < -180f) angleDiff += 360f
                                        if (isVideoTransformMode && videoAsset != null && onVideoTransformChange != null) {
                                            val projDelta = screenToProjectCoords(currentCenter.x, currentCenter.y) -
                                                screenToProjectCoords(lastCenter.x, lastCenter.y)
                                            onVideoTransformChange(
                                                projDelta.x.toDouble(),
                                                projDelta.y.toDouble(),
                                                zoomFactor.toDouble(),
                                                angleDiff.toDouble()
                                            )
                                        } else {
                                            onTransformChange(zoomFactor, panDelta.x, panDelta.y, angleDiff)
                                        }
                                        lastCenter = currentCenter
                                        lastDistance = currentDist
                                        lastAngle = currentAngle
                                    }
                                }
                            }
                            MotionEvent.ACTION_POINTER_UP -> waitingForFullRelease = true
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
                                    onMultiFingerGesture != null
                                ) {
                                    val elapsed = android.os.SystemClock.uptimeMillis() - gestureDownTimeMs
                                    val dx = gestureLastCenter.x - gestureStartCenter.x
                                    val dy = gestureLastCenter.y - gestureStartCenter.y
                                    val disp = hypot(dx, dy)
                                    val detected = when {
                                        gestureMaxPointers == 2 && elapsed <= 420L && disp <= 42f ->
                                            MultiFingerGesture.TWO_FINGER_TAP
                                        gestureMaxPointers == 3 && elapsed <= 460L && disp <= 45f ->
                                            MultiFingerGesture.THREE_FINGER_TAP
                                        gestureMaxPointers == 3 && disp >= 60f -> when {
                                            abs(dy) >= abs(dx) && dy > 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_DOWN
                                            abs(dx) > abs(dy) && dx < 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_LEFT
                                            abs(dx) > abs(dy) && dx > 0f -> MultiFingerGesture.THREE_FINGER_SWIPE_RIGHT
                                            else -> null
                                        }
                                        gestureMaxPointers >= 4 && elapsed <= 520L && disp <= 55f ->
                                            MultiFingerGesture.FOUR_FINGER_TAP
                                        else -> null
                                    }
                                    if (detected != null) {
                                        onMultiFingerGesture.invoke(detected)
                                    }
                                }
                                waitingForFullRelease = false
                                isTransforming = false
                                gestureSessionActive = false
                                gestureMaxPointers = 0
                                didTransformMove = false
                                isDrawingActive = false
                                isMovingSelection = false
                                activePerspectiveDragIdx = -1
                                lockedPerspectiveTargetVpIdx = -1
                                currentPoints.clear()
                                cacheHolder.endActiveSessions()
                                onLiveStrokePreview?.invoke(null)
                            }
                            return@pointerInteropFilter true
                        }

                        val isStylus = motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                            motionEvent.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                        if (isStylusOnlyMode && !isStylus) {
                            return@pointerInteropFilter false
                        }

                        val rawProjPt = screenToProjectCoords(motionEvent.x, motionEvent.y)
                        val projPt = applyTabletInversion(rawProjPt.x, rawProjPt.y)

                        cursorScreenPos = projectToScreenCoords(projPt.x, projPt.y)

                        // Throttled multiplayer cursor sync (~30Hz)
                        if (onLocalCursorMoved != null) {
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
                                onLocalCursorMoved.invoke(projPt.x, projPt.y, drawingStateForCursor)
                            }
                        }

                        val isDownAction = motionEvent.actionMasked == MotionEvent.ACTION_DOWN
                        val rawPressure = if (selectedTool == ToolType.ERASER && !eraserUsesPressure) {
                            1.0f
                        } else {
                            mapPressureForEvent(motionEvent, motionEvent.pressure, isInitialDown = isDownAction)
                        }

                        when (motionEvent.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                // Interactive Video Transform Mode: 1-finger moves video overlay
                                if (isVideoTransformMode && videoAsset != null && onVideoTransformChange != null) {
                                    isMovingVideo = true
                                    lastVideoTouchProj = projPt
                                    return@pointerInteropFilter true
                                }

                                // Interactive Caneta Vetorial (Bezier Pen)
                                if (selectedTool == ToolType.BEZIER_PEN) {
                                    val hitR = (28f * screenToProjScaleX * invZoom).coerceAtLeast(18f)
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
                                        onBezierNodesChange?.invoke(workingBezierNodes.toList())
                                        activeBezierDragIdx = workingBezierNodes.lastIndex
                                        activeBezierDragPart = 4
                                    }
                                    activeStrokeVersion++
                                    return@pointerInteropFilter true
                                }

                                if (activeSelection.isActive && (selectedTool == ToolType.LASSO || selectedTool == ToolType.MAGIC_WAND)) {
                                    val transformedPoly = activeSelectionClipPoly
                                    val insidePoly = transformedPoly != null &&
                                        SelectionGeometryHelper.isPointInPolygon(projPt.x, projPt.y, transformedPoly)
                                    val selMinX = activeSelection.minX + activeSelection.offsetX
                                    val selMaxX = activeSelection.maxX + activeSelection.offsetX
                                    val selMinY = activeSelection.minY + activeSelection.offsetY
                                    val selMaxY = activeSelection.maxY + activeSelection.offsetY

                                    if (insidePoly || (projPt.x in selMinX..selMaxX && projPt.y in selMinY..selMaxY)) {
                                        isMovingSelection = true
                                        lastSelTouchPos = projPt
                                        return@pointerInteropFilter true
                                    }
                                }

                                if (selectedTool == ToolType.BUCKET) {
                                    onBucketFill?.invoke(projPt.x, projPt.y)
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }
                                if (selectedTool == ToolType.MAGIC_WAND) {
                                    onMagicWand?.invoke(projPt.x, projPt.y)
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }

                                if (selectedTool == ToolType.EYEDROPPER) {
                                    val sx = (projPt.x * cacheScale).roundToInt()
                                    val sy = (projPt.y * cacheScale).roundToInt()
                                    onEyedropperColorPicked(cacheHolder.sampleEyedropperPixel(sx, sy))
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }

                                // Drag Perspective Ruler Vanishing Point Handle if tapped (including edge-docked off-canvas VP badges)
                                if (perspectiveRulerSettings.isEnabled && onPerspectiveVanishingPointDrag != null) {
                                    val hitR = (28f * density.density * screenToProjScaleX * invZoom).coerceAtLeast(22f)
                                    val hitRSq = hitR * hitR
                                    val edgePad = (16f * density.density * screenToProjScaleX * invZoom).coerceIn(10f, projWidth * 0.12f)
                                    val vps = buildList {
                                        add(Offset(perspectiveRulerSettings.vp1XRatio * projWidth, perspectiveRulerSettings.vp1YRatio * projHeight))
                                        if (perspectiveRulerSettings.vanishingPointsCount >= 2) {
                                            add(Offset(perspectiveRulerSettings.vp2XRatio * projWidth, perspectiveRulerSettings.vp2YRatio * projHeight))
                                        }
                                        if (perspectiveRulerSettings.vanishingPointsCount >= 3) {
                                            add(Offset(perspectiveRulerSettings.vp3XRatio * projWidth, perspectiveRulerSettings.vp3YRatio * projHeight))
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
                                val isEditableTarget = activeLayerObj?.isLocked != true && activeLayerObj?.isVisible != false
                                if (!isEditableTarget) {
                                    isDrawingActive = false
                                    return@pointerInteropFilter true
                                }

                                cacheHolder.ensureSize(cacheW, cacheH)
                                if (selectedTool == ToolType.ERASER) {
                                    cacheHolder.beginEraserSession()
                                } else {
                                    cacheHolder.beginActiveStrokeSession()
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
                                if (activePerspectiveDragIdx >= 0 && onPerspectiveVanishingPointDrag != null) {
                                    onPerspectiveVanishingPointDrag.invoke(
                                        activePerspectiveDragIdx,
                                        projPt.x / projWidth,
                                        projPt.y / projHeight
                                    )
                                    return@pointerInteropFilter true
                                }
                                if (isMovingVideo && onVideoTransformChange != null) {
                                    val dx = (projPt.x - lastVideoTouchProj.x).toDouble()
                                    val dy = (projPt.y - lastVideoTouchProj.y).toDouble()
                                    onVideoTransformChange(dx, dy, 1.0, 0.0)
                                    lastVideoTouchProj = projPt
                                    return@pointerInteropFilter true
                                }

                                if (selectedTool == ToolType.BEZIER_PEN && activeBezierDragIdx in workingBezierNodes.indices) {
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
                                    onBezierNodesChange?.invoke(workingBezierNodes.toList())
                                    activeStrokeVersion++
                                    return@pointerInteropFilter true
                                }

                                if (isMovingSelection) {
                                    val dx = projPt.x - lastSelTouchPos.x
                                    val dy = projPt.y - lastSelTouchPos.y
                                    onSelectionOffsetChange?.invoke(dx, dy)
                                    lastSelTouchPos = projPt
                                    return@pointerInteropFilter true
                                }

                                if (isDrawingActive) {
                                    val historySize = motionEvent.historySize
                                    for (h in 0 until historySize) {
                                        val hRaw = screenToProjectCoords(motionEvent.getHistoricalX(h), motionEvent.getHistoricalY(h))
                                        val hInv = applyTabletInversion(hRaw.x, hRaw.y)
                                        val hProj = applyPerspectiveSnapIfEnabled(hInv)
                                        val hp = if (selectedTool == ToolType.ERASER && !eraserUsesPressure) {
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
                                    if (onLiveStrokePreview != null &&
                                        selectedTool != ToolType.LASSO &&
                                        selectedTool != ToolType.EYEDROPPER &&
                                        selectedTool != ToolType.BUCKET &&
                                        selectedTool != ToolType.MAGIC_WAND
                                    ) {
                                        val nowPreview = android.os.SystemClock.uptimeMillis()
                                        if (nowPreview - lastLiveStrokeBroadcastMs >= 40L && currentPoints.isNotEmpty()) {
                                            lastLiveStrokeBroadcastMs = nowPreview
                                            onLiveStrokePreview.invoke(
                                                DrawingStroke(
                                                    id = activeStrokeId.ifBlank { UUID.randomUUID().toString() },
                                                    tool = selectedTool,
                                                    color = currentColor,
                                                    brushSize = brushSize,
                                                    opacity = brushOpacity,
                                                    points = currentPoints.toList(),
                                                    brushPresetId = if (selectedTool == ToolType.ERASER) eraserBrushPresetId else brushPresetId,
                                                    customBrushParams = if (selectedTool == ToolType.ERASER) null else activeBrushParamsJson
                                                )
                                            )
                                        }
                                    }
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (activePerspectiveDragIdx >= 0) {
                                    activePerspectiveDragIdx = -1
                                    return@pointerInteropFilter true
                                }
                                if (isMovingVideo) {
                                    isMovingVideo = false
                                    return@pointerInteropFilter true
                                }
                                if (selectedTool == ToolType.BEZIER_PEN) {
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
                                    onLiveStrokePreview?.invoke(null)
                                    if (selectedTool == ToolType.LASSO) {
                                        onLassoCompleted?.invoke(currentPoints.toList())
                                    } else if (selectedTool != ToolType.EYEDROPPER &&
                                        selectedTool != ToolType.BUCKET &&
                                        selectedTool != ToolType.MAGIC_WAND &&
                                        currentPoints.isNotEmpty()
                                    ) {
                                        val completedStroke = DrawingStroke(
                                            id = activeStrokeId.ifBlank { UUID.randomUUID().toString() },
                                            tool = selectedTool,
                                            color = currentColor,
                                            brushSize = brushSize,
                                            opacity = brushOpacity,
                                            points = currentPoints.toList(),
                                            brushPresetId = if (selectedTool == ToolType.ERASER) eraserBrushPresetId else brushPresetId,
                                            customBrushParams = if (selectedTool == ToolType.ERASER) null else activeBrushParamsJson,
                                            clipPolygon = activeSelectionClipPoly
                                        )
                                        // Synchronously commit onto cached activeLayerBitmap BEFORE clearing active overlay
                                        // only when the active layer is unlocked and visible (prevents vanished phantom strokes).
                                        val canCommitLocally = activeLayerObj?.isLocked != true && activeLayerObj?.isVisible != false
                                        if (canCommitLocally) {
                                            cacheHolder.ensureSize(cacheW, cacheH)
                                            val targetBmp = cacheHolder.activeLayerBitmap?.takeIf { !it.isRecycled }
                                                ?: cacheHolder.acquireOrReuse(null, AndroidColor.TRANSPARENT).also {
                                                    cacheHolder.activeLayerBitmap = it
                                                }
                                            val commitCanvas = AndroidCanvas(targetBmp)
                                            val eraserBuf = cacheHolder.eraserWorkBitmap
                                            if (selectedTool == ToolType.ERASER && eraserBuf != null && !eraserBuf.isRecycled) {
                                                flushIncrementalRaster()
                                                commitCanvas.drawBitmap(eraserBuf, 0f, 0f, overlayScratch.srcCopyPaint)
                                            } else {
                                                commitCanvas.save()
                                                commitCanvas.scale(cacheScale, cacheScale)
                                                KodanimaGraphicsEngine.drawStrokeToCanvas(
                                                    completedStroke,
                                                    commitCanvas,
                                                    1.0f,
                                                    null,
                                                    densityMult
                                                )
                                                commitCanvas.restore()
                                            }
                                            onStrokeCompleted(completedStroke)
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
                                onFocusActive?.invoke()
                                val hoverRaw = screenToProjectCoords(motionEvent.x, motionEvent.y)
                                val hoverProj = applyTabletInversion(hoverRaw.x, hoverRaw.y)
                                cursorScreenPos = projectToScreenCoords(hoverProj.x, hoverProj.y)
                                if (onLocalCursorMoved != null) {
                                    val nowH = android.os.SystemClock.uptimeMillis()
                                    val dxB = hoverProj.x - lastBroadcastX
                                    val dyB = hoverProj.y - lastBroadcastY
                                    if (nowH - lastCursorBroadcastMs >= 33L && (dxB * dxB + dyB * dyB) > 2.25f) {
                                        lastCursorBroadcastMs = nowH
                                        lastBroadcastX = hoverProj.x
                                        lastBroadcastY = hoverProj.y
                                        lastBroadcastDrawing = false
                                        onLocalCursorMoved.invoke(hoverProj.x, hoverProj.y, false)
                                    }
                                }
                                true
                            }
                            MotionEvent.ACTION_SCROLL -> {
                                onFocusActive?.invoke()
                                if (tabletSettings.mouseScrollZoomEnabled) {
                                    val vScroll = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                    if (vScroll > 0f) {
                                        onTransformChange(1.12f, 0f, 0f, 0f)
                                    } else if (vScroll < 0f) {
                                        onTransformChange(0.88f, 0f, 0f, 0f)
                                    }
                                    return@pointerInteropFilter true
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
            // Main Hardware-Accelerated Drawing Canvas (transforms execute 100% on GPU RenderThread)
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
                        width = with(density) { baseWidth.toDp() },
                        height = with(density) { baseHeight.toDp() }
                    )
                    .shadow(16.dp, RectangleShape)
                    .clip(RectangleShape)
                    .background(if (isFloatingRefBelowCanvas) Color.White.copy(alpha = 0.86f) else Color.White)
            ) {
                // Layer 1: Cached GPU texture compositing (O(1) per frame)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("drawing_canvas_static")
                ) {
                    val trigger = activeStrokeVersion
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val screenPxPerProjPx = (size.width / projWidth) * zoom
                    // Keep silky bilinear AA smoothing normally; switch to crisp MediBang nearest-neighbor pixels on deep zoom
                    overlayScratch.bitmapPaint.isFilterBitmap = screenPxPerProjPx < 4.0f
                    overlayScratch.srcRect.set(0, 0, cacheW, cacheH)
                    overlayScratch.dstRectF.set(0f, 0f, size.width, size.height)

                    val activeIsClip = activeLayerObj?.isClippingMask == true
                    val activeBlend = activeLayerObj?.blendEffect ?: LayerBlendEffect.NORMAL
                    val needsLayerStackBlend = activeIsClip ||
                        activeBlend != LayerBlendEffect.NORMAL ||
                        cacheHolder.aboveAdvancedIsActive

                    if (needsLayerStackBlend) {
                        // Guarantee opaque white paper base inside Layer 1 so GPU BlendModes blend cleanly
                        nativeCanvas.drawRect(0f, 0f, size.width, size.height, overlayScratch.whitePaperPaint)
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
                        overlayScratch.srcRect.set(0, 0, cacheW, cacheH)
                        overlayScratch.dstRectF.set(0f, 0f, size.width, size.height)
                    }

                    // Onion skin under current layers
                    cacheHolder.onionBitmap?.takeIf { !it.isRecycled && onionSkinEnabled && project.onionSkin.isEnabled }?.let { bmp ->
                        nativeCanvas.drawBitmap(bmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                    }
                    // Below active layer
                    cacheHolder.belowLayersBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                        nativeCanvas.drawBitmap(bmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                    }
                    // Perspective Ruler behind active layer strokes when configured
                    if (perspectiveRulerSettings.isEnabled && perspectiveRulerSettings.renderBehindActiveLayer) {
                        val activeSnapOrigin = if (isDrawingActive && perspectiveRulerSettings.snapToGuide) {
                            currentPoints.firstOrNull()?.let { Offset(it.x, it.y) }
                        } else null
                        drawPerspectiveRulerCanvas(
                            nativeCanvas = nativeCanvas,
                            canvasScaleX = size.width / projWidth,
                            canvasScaleY = size.height / projHeight,
                            projWidth = projWidth,
                            projHeight = projHeight,
                            screenDensity = density.density,
                            zoom = zoom,
                            settings = perspectiveRulerSettings,
                            activeSnapOrigin = activeSnapOrigin,
                            lockedSnapVpIdx = lockedPerspectiveTargetVpIdx,
                            scratch = overlayScratch
                        )
                    }
                    // Active layer (isolated eraser working buffer when erasing, or cached committed layer)
                    val activeBmp = if (isDrawingActive && selectedTool == ToolType.ERASER && cacheHolder.eraserWorkBitmap != null) {
                        cacheHolder.eraserWorkBitmap
                    } else {
                        cacheHolder.activeLayerBitmap
                    }
                    val clipBaseBmp = cacheHolder.clipBaseBitmap?.takeIf { !it.isRecycled }
                    activeBmp?.takeIf { !it.isRecycled && (activeLayerObj?.isVisible != false) }?.let { bmp ->
                        if (!activeIsClip && activeBlend == LayerBlendEffect.NORMAL) {
                            nativeCanvas.drawBitmap(bmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                        } else if (!activeIsClip) {
                            overlayScratch.layerBlendPaint.alpha = 255
                            KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, activeBlend)
                            nativeCanvas.drawBitmap(bmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.layerBlendPaint)
                        } else if (clipBaseBmp != null) {
                            overlayScratch.layerBlendPaint.alpha = 255
                            KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, activeBlend)
                            val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                            nativeCanvas.drawBitmap(bmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                            nativeCanvas.drawBitmap(clipBaseBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.clipDstInPaint)
                            nativeCanvas.restoreToCount(sc)
                        }
                    }
                    // Above active layer
                    if (!cacheHolder.aboveAdvancedIsActive) {
                        cacheHolder.aboveLayersBitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                            nativeCanvas.drawBitmap(bmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                        }
                    } else {
                        var lastNonClippedBmp: Bitmap? = when {
                            activeLayerObj != null && !activeLayerObj.isClippingMask && activeLayerObj.isVisible && activeLayerObj.opacity > 0f -> activeBmp
                            activeLayerObj != null && activeLayerObj.isClippingMask -> clipBaseBmp
                            else -> null
                        }
                        cacheHolder.aboveAdvancedLayers.forEach { adv ->
                            val advLayer = adv.layer
                            if (!advLayer.isClippingMask) {
                                if (!advLayer.isVisible || advLayer.opacity <= 0f || adv.bitmap.isRecycled) {
                                    lastNonClippedBmp = null
                                } else {
                                    lastNonClippedBmp = adv.bitmap
                                    if (advLayer.blendEffect == LayerBlendEffect.NORMAL) {
                                        nativeCanvas.drawBitmap(adv.bitmap, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                                    } else {
                                        overlayScratch.layerBlendPaint.alpha = 255
                                        KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, advLayer.blendEffect)
                                        nativeCanvas.drawBitmap(adv.bitmap, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.layerBlendPaint)
                                    }
                                }
                            } else if (advLayer.isVisible && advLayer.opacity > 0f && !adv.bitmap.isRecycled) {
                                val maskBmp = lastNonClippedBmp
                                if (maskBmp != null && !maskBmp.isRecycled) {
                                    overlayScratch.layerBlendPaint.alpha = 255
                                    KodanimaGraphicsEngine.applyLayerBlendMode(overlayScratch.layerBlendPaint, advLayer.blendEffect)
                                    val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, overlayScratch.layerBlendPaint)
                                    nativeCanvas.drawBitmap(adv.bitmap, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.bitmapPaint)
                                    nativeCanvas.drawBitmap(maskBmp, overlayScratch.srcRect, overlayScratch.dstRectF, overlayScratch.clipDstInPaint)
                                    nativeCanvas.restoreToCount(sc)
                                }
                            }
                        }
                    }
                    @Suppress("UNUSED_EXPRESSION")
                    trigger
                }

                // Layer 2: Dedicated incremental active stroke & selection canvas (60-120 FPS zero-alloc overlay)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("drawing_canvas_active")
                ) {
                    val trigger = activeStrokeVersion
                    val canvasScaleX = size.width / projWidth
                    val canvasScaleY = size.height / projHeight
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    val activeIsClip = activeLayerObj?.isClippingMask == true
                    val activeClipMaskBmp = if (activeIsClip) cacheHolder.clipBaseBitmap?.takeIf { !it.isRecycled } else null

                    if (currentPoints.isNotEmpty()) {
                        if (selectedTool == ToolType.ERASER) {
                            val lastPt = currentPoints[currentPoints.lastIndex]
                            val pFactor = if (eraserUsesPressure) lastPt.pressure else 1.0f
                            val eRadius = ((brushSize * pFactor * canvasScaleX) * 0.5f).coerceAtLeast(3f)
                            val cx = lastPt.x * canvasScaleX
                            val cy = lastPt.y * canvasScaleY
                            // Crisp dual-ring without slow software ShadowLayer (Mali-G57 TBDR friendly!)
                            nativeCanvas.drawCircle(cx, cy, eRadius, overlayScratch.eraserRingDarkPaint)
                            nativeCanvas.drawCircle(cx, cy, eRadius, overlayScratch.eraserRingLightPaint)
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
                            overlayScratch.bitmapPaint.isFilterBitmap = ((size.width / projWidth) * zoom) < 4.0f
                            overlayScratch.srcRect.set(0, 0, cacheW, cacheH)
                            overlayScratch.dstRectF.set(0f, 0f, size.width, size.height)
                            if (activeIsClip && activeClipMaskBmp != null) {
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                nativeCanvas.drawBitmap(
                                    cacheHolder.activeStrokeBitmap!!,
                                    overlayScratch.srcRect,
                                    overlayScratch.dstRectF,
                                    overlayScratch.bitmapPaint
                                )
                                nativeCanvas.drawBitmap(
                                    activeClipMaskBmp,
                                    overlayScratch.srcRect,
                                    overlayScratch.dstRectF,
                                    overlayScratch.clipDstInPaint
                                )
                                nativeCanvas.restoreToCount(sc)
                            } else if (!activeIsClip) {
                                nativeCanvas.drawBitmap(
                                    cacheHolder.activeStrokeBitmap!!,
                                    overlayScratch.srcRect,
                                    overlayScratch.dstRectF,
                                    overlayScratch.bitmapPaint
                                )
                            }
                        } else {
                            val previewStroke = DrawingStroke(
                                tool = selectedTool,
                                color = currentColor,
                                brushSize = brushSize,
                                opacity = brushOpacity,
                                points = currentPoints,
                                brushPresetId = brushPresetId,
                                customBrushParams = activeBrushParamsJson,
                                clipPolygon = activeSelectionClipPoly
                            )
                            if (activeIsClip && activeClipMaskBmp != null) {
                                val sc = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                nativeCanvas.save()
                                nativeCanvas.scale(canvasScaleX, canvasScaleY)
                                KodanimaGraphicsEngine.drawStrokeToCanvas(previewStroke, nativeCanvas, 1.0f, null, densityMult)
                                nativeCanvas.restore()
                                overlayScratch.srcRect.set(0, 0, cacheW, cacheH)
                                overlayScratch.dstRectF.set(0f, 0f, size.width, size.height)
                                nativeCanvas.drawBitmap(
                                    activeClipMaskBmp,
                                    overlayScratch.srcRect,
                                    overlayScratch.dstRectF,
                                    overlayScratch.clipDstInPaint
                                )
                                nativeCanvas.restoreToCount(sc)
                            } else if (!activeIsClip) {
                                nativeCanvas.save()
                                nativeCanvas.scale(canvasScaleX, canvasScaleY)
                                KodanimaGraphicsEngine.drawStrokeToCanvas(previewStroke, nativeCanvas, 1.0f, null, densityMult)
                                nativeCanvas.restore()
                            }
                        }
                    }

                    // Live in-progress remote collaborator strokes (zero-lag multiplayer feedback, skip committed duplicates)
                    if (remoteLiveStrokes.isNotEmpty()) {
                        nativeCanvas.save()
                        nativeCanvas.scale(canvasScaleX, canvasScaleY)
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
                        nativeCanvas.scale(canvasScaleX, canvasScaleY)
                        nativeCanvas.translate(activeSelection.offsetX, activeSelection.offsetY)

                        val cx = activeSelection.centerX
                        val cy = activeSelection.centerY
                        if (activeSelection.rotationDegrees != 0f) {
                            nativeCanvas.rotate(activeSelection.rotationDegrees, cx, cy)
                        }
                        if (activeSelection.scaleX != 1f || activeSelection.scaleY != 1f) {
                            nativeCanvas.scale(activeSelection.scaleX, activeSelection.scaleY, cx, cy)
                        }

                        if (activeSelection.capturedStrokes.isNotEmpty()) {
                            for (cStroke in activeSelection.capturedStrokes) {
                                KodanimaGraphicsEngine.drawStrokeToCanvas(cStroke, nativeCanvas, 1.0f, null, densityMult)
                            }
                        }

                        overlayScratch.selPaint.strokeWidth = 2.0f / canvasScaleX
                        overlayScratch.selPath.reset()
                        val first = activeSelection.points[0]
                        overlayScratch.selPath.moveTo(first.x, first.y)
                        for (i in 1 until activeSelection.points.size) {
                            val p = activeSelection.points[i]
                            overlayScratch.selPath.lineTo(p.x, p.y)
                        }
                        nativeCanvas.drawPath(overlayScratch.selPath, overlayScratch.selPaint)

                        overlayScratch.boxPaint.strokeWidth = 1.5f / canvasScaleX
                        val bLeft = activeSelection.minX
                        val bTop = activeSelection.minY
                        val bRight = activeSelection.maxX
                        val bBottom = activeSelection.maxY
                        val hRadius = 5f / canvasScaleX

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
                        val fitScale = min(size.width / vbW, size.height / vbH)
                        val scaleFactor = (videoAsset.scalePercent / 100.0).toFloat().coerceIn(0.1f, 5f)
                        val drawW = vbW * fitScale * scaleFactor
                        val drawH = vbH * fitScale * scaleFactor
                        val cx = size.width * 0.5f + videoAsset.offsetX.toFloat() * canvasScaleX
                        val cy = size.height * 0.5f + videoAsset.offsetY.toFloat() * canvasScaleY
                        val hw = drawW * 0.5f
                        val hh = drawH * 0.5f

                        nativeCanvas.save()
                        nativeCanvas.translate(cx, cy)
                        if (videoAsset.rotationDegrees != 0.0) {
                            nativeCanvas.rotate(videoAsset.rotationDegrees.toFloat())
                        }
                        overlayScratch.videoBoxPaint.strokeWidth = 2.2f
                        nativeCanvas.drawRect(-hw, -hh, hw, hh, overlayScratch.videoBoxPaint)
                        val vr = 6.5f
                        nativeCanvas.drawCircle(-hw, -hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.drawCircle(hw, -hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.drawCircle(hw, hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.drawCircle(-hw, hh, vr, overlayScratch.videoHandlePaint)
                        nativeCanvas.restore()
                    }

                    // Interactive Caneta Vetorial (Bezier Pen) Spline & Tangent Handles
                    if (selectedTool == ToolType.BEZIER_PEN && workingBezierNodes.isNotEmpty()) {
                        val sampledPts = sampleCubicBezierNodesToStrokePoints(
                            workingBezierNodes.toList(),
                            closed = activeIsBezierClosed,
                            samplesPerSegment = 22
                        )
                        nativeCanvas.save()
                        nativeCanvas.scale(canvasScaleX, canvasScaleY)
                        if (sampledPts.size >= 2) {
                            val previewStroke = DrawingStroke(
                                tool = ToolType.BEZIER_PEN,
                                color = currentColor,
                                brushSize = brushSize,
                                opacity = brushOpacity,
                                points = sampledPts,
                                brushPresetId = brushPresetId,
                                customBrushParams = activeBrushParamsJson,
                                clipPolygon = activeSelectionClipPoly
                            )
                            KodanimaGraphicsEngine.drawStrokeToCanvas(previewStroke, nativeCanvas, 1.0f, null, densityMult)
                        }

                        overlayScratch.bezierLinePaint.strokeWidth = (1.5f / canvasScaleX).coerceAtLeast(0.5f)
                        overlayScratch.bezierAnchorStrokePaint.strokeWidth = (1.8f / canvasScaleX).coerceAtLeast(0.6f)
                        val handleR = (4.5f / canvasScaleX).coerceAtLeast(1.5f)
                        val anchorHalf = (5.5f / canvasScaleX).coerceAtLeast(2f)

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

                    // FlipaClip-style Grid Ruler Overlay
                    if (gridRulerSettings.isEnabled) {
                        val cellPx = gridRulerSettings.cellSizePx.coerceIn(12f, 256f)
                        val baseCol = gridRulerSettings.color.toInt()
                        val gridOp = gridRulerSettings.opacity.coerceIn(0.1f, 1.0f)
                        val alphaMain = (gridOp * 255f).roundToInt()
                        val alphaSub = (gridOp * 0.48f * 255f).roundToInt()
                        val majorColor = (baseCol and 0x00FFFFFF) or (alphaMain shl 24)
                        val minorColor = (baseCol and 0x00FFFFFF) or (alphaSub shl 24)
                        val effScaleX = (canvasScaleX * zoom.coerceAtLeast(0.25f)).coerceAtLeast(0.08f)
                        val effScaleY = (canvasScaleY * zoom.coerceAtLeast(0.25f)).coerceAtLeast(0.08f)
                        val dpInProjX = density.density / effScaleX
                        val dpInProjY = density.density / effScaleY

                        nativeCanvas.save()
                        nativeCanvas.scale(canvasScaleX, canvasScaleY)
                        var gx = cellPx
                        var colIdx = 1
                        while (gx < projWidth) {
                            val isMajor = colIdx % 4 == 0
                            overlayScratch.gridRulerPaint.color = if (isMajor) majorColor else minorColor
                            overlayScratch.gridRulerPaint.strokeWidth = (if (isMajor) 1.35f else 0.85f) * dpInProjX
                            nativeCanvas.drawLine(gx, 0f, gx, projHeight, overlayScratch.gridRulerPaint)
                            gx += cellPx
                            colIdx++
                        }
                        var gy = cellPx
                        var rowIdx = 1
                        while (gy < projHeight) {
                            val isMajor = rowIdx % 4 == 0
                            overlayScratch.gridRulerPaint.color = if (isMajor) majorColor else minorColor
                            overlayScratch.gridRulerPaint.strokeWidth = (if (isMajor) 1.35f else 0.85f) * dpInProjY
                            nativeCanvas.drawLine(0f, gy, projWidth, gy, overlayScratch.gridRulerPaint)
                            gy += cellPx
                            rowIdx++
                        }
                        nativeCanvas.restore()
                    }

                    // 1P / 2P / 3P Perspective Ruler Overlay in front when !renderBehindActiveLayer
                    if (perspectiveRulerSettings.isEnabled && !perspectiveRulerSettings.renderBehindActiveLayer) {
                        val activeSnapOrigin = if (isDrawingActive && perspectiveRulerSettings.snapToGuide) {
                            currentPoints.firstOrNull()?.let { Offset(it.x, it.y) }
                        } else null
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
                            scratch = overlayScratch
                        )
                    }

                    // MediBang Paint-style 1px crisp Pixel Grid overlay when deeply zoomed in (>= 9 screen px per project pixel)
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
                            val topY = startPy * canvasScaleY
                            val bottomY = endPy * canvasScaleY
                            val leftX = startPx * canvasScaleX
                            val rightX = endPx * canvasScaleX
                            overlayScratch.pixelGridPaint.alpha =
                                ((screenPxPerProjPxGrid - 9.0f) / 14.0f).coerceIn(0.25f, 1.0f).let { (it * 72f).roundToInt() }
                            for (px in startPx..endPx) {
                                val lx = px * canvasScaleX
                                nativeCanvas.drawLine(lx, topY, lx, bottomY, overlayScratch.pixelGridPaint)
                            }
                            for (py in startPy..endPy) {
                                val ly = py * canvasScaleY
                                nativeCanvas.drawLine(leftX, ly, rightX, ly, overlayScratch.pixelGridPaint)
                            }
                        }
                    }
                    @Suppress("UNUSED_EXPRESSION")
                    trigger
                }
            }

            // 5. Tablet Virtual Cursor Overlay (Custom .cur/.ani/.png scaled cleanly)
            if (tabletSettings.enableVirtualCursor && cursorScreenPos != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val pos = cursorScreenPos ?: return@Canvas
                    val canvasScale = (baseWidth / projWidth) * zoom
                    val cursorRadius = (brushSize * canvasScale * 0.5f).coerceIn(4f, 250f)
                    val cursorRingColor = if (tabletSettings.cursorColor != 0L) Color(tabletSettings.cursorColor) else Color(themeAccentColor)

                    if (customCursorBitmap != null && !customCursorBitmap.isRecycled) {
                        val maxCursorPx = 32.dp.toPx()
                        val bmpW = customCursorBitmap.width.toFloat().coerceAtLeast(1f)
                        val bmpH = customCursorBitmap.height.toFloat().coerceAtLeast(1f)
                        val s = min(maxCursorPx / bmpW, maxCursorPx / bmpH)
                        val drawW = bmpW * s
                        val drawH = bmpH * s
                        overlayScratch.srcRect.set(0, 0, customCursorBitmap.width, customCursorBitmap.height)
                        overlayScratch.dstRectF.set(
                            pos.x - drawW * 0.5f,
                            pos.y - drawH * 0.5f,
                            pos.x + drawW * 0.5f,
                            pos.y + drawH * 0.5f
                        )
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            customCursorBitmap,
                            overlayScratch.srcRect,
                            overlayScratch.dstRectF,
                            overlayScratch.bitmapPaint
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
                                                brushSize = brushSize,
                                                opacity = brushOpacity,
                                                points = sampledPts,
                                                brushPresetId = brushPresetId,
                                                customBrushParams = activeBrushParamsJson,
                                                clipPolygon = activeSelectionClipPoly
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
    scratch: CanvasOverlayScratch
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
    nativeCanvas.scale(canvasScaleX, canvasScaleY)

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

