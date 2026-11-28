package com.example.engine

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.example.model.DrawingStroke
import com.example.model.Layer
import com.example.model.OnionSkinSettings
import com.example.model.ToolType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Predictive screen-space overscan margin (px) around the active viewport.
 * Clamped strictly within [10f, 40f] px in revealed pan directions.
 */
data class PredictiveMarginsPx(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class TileKey(
    val sliceTag: String,
    val contentHash: Int,
    val lodLevel: Int,
    val tileX: Int,
    val tileY: Int
)

data class TileCoord(
    val tileX: Int,
    val tileY: Int,
    val onScreen: Boolean
)

class CachedTile(
    val key: TileKey,
    val bitmap: Bitmap,
    var renderNode: Any? = null,
    val projBounds: RectF,
    val lodScale: Float,
    val byteSize: Int
)

data class TilePlan(
    val lodLevel: Int,
    val lodScale: Float,
    val tileSizePx: Int,
    val tileProjSpan: Float,
    val visibleFrustumProj: RectF,
    val prefetchFrustumProj: RectF,
    val visibleTileRect: Rect,
    val prefetchTileRect: Rect,
    val marginsPx: PredictiveMarginsPx,
    val isZoomingIn: Boolean,
    val isZoomingOut: Boolean,
    val orderedCoords: List<TileCoord>
)

@RequiresApi(Build.VERSION_CODES.Q)
private object RenderNodeTileHelper {
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun buildOrGet(existingNode: Any?, bitmap: Bitmap, tileSizePx: Int): Any? {
        if (bitmap.isRecycled) return null
        val node = (existingNode as? RenderNode) ?: RenderNode("KodanimaTile").also {
            it.setPosition(0, 0, tileSizePx, tileSizePx)
            it.setClipToBounds(false)
        }
        if (!node.hasDisplayList()) {
            node.setPosition(0, 0, tileSizePx, tileSizePx)
            val rc = node.beginRecording(tileSizePx, tileSizePx)
            try {
                rc.drawBitmap(bitmap, 0f, 0f, nodePaint)
            } finally {
                node.endRecording()
            }
        }
        return node
    }

    fun invalidate(node: Any?) {
        (node as? RenderNode)?.discardDisplayList()
    }

    fun draw(canvas: Canvas, node: Any?): Boolean {
        val rn = node as? RenderNode ?: return false
        if (!rn.hasDisplayList()) return false
        canvas.drawRenderNode(rn)
        return true
    }
}

/**
 * Tracks real-time Pan direction/velocity and Zoom direction across touch & mouse gestures
 * to compute predictive pre-rendering margins (10..40 px) and zoom culling triggers.
 */
class PredictiveViewportTracker {
    @Volatile
    private var velX: Float = 0f

    @Volatile
    private var velY: Float = 0f

    @Volatile
    private var lastEventMs: Long = 0L

    @Volatile
    private var lastZoomFactor: Float = 1f

    @Volatile
    private var lastZoomMs: Long = 0L

    @Synchronized
    fun recordTransformDelta(
        panDxPx: Float,
        panDyPx: Float,
        zoomFactor: Float,
        nowMs: Long = SystemClock.uptimeMillis()
    ) {
        val dtMs = if (lastEventMs > 0L) (nowMs - lastEventMs).coerceIn(4L, 250L) else 16L
        val dtSec = dtMs / 1000f

        if (abs(panDxPx) > 0.05f || abs(panDyPx) > 0.05f) {
            val instVx = (panDxPx / dtSec).coerceIn(-6500f, 6500f)
            val instVy = (panDyPx / dtSec).coerceIn(-6500f, 6500f)
            val staleGap = lastEventMs > 0L && (nowMs - lastEventMs) > 150L
            if (staleGap) {
                velX = instVx
                velY = instVy
            } else {
                velX = velX * 0.45f + instVx * 0.55f
                velY = velY * 0.45f + instVy * 0.55f
            }
        } else if (lastEventMs > 0L && (nowMs - lastEventMs) > 120L) {
            velX *= 0.4f
            velY *= 0.4f
        }

        if (abs(zoomFactor - 1f) > 0.002f) {
            lastZoomFactor = zoomFactor
            lastZoomMs = nowMs
        }
        lastEventMs = nowMs
    }

    @Synchronized
    fun reset() {
        velX = 0f
        velY = 0f
        lastEventMs = 0L
        lastZoomFactor = 1f
        lastZoomMs = 0L
    }

    @Synchronized
    fun snapshotVelocity(nowMs: Long = SystemClock.uptimeMillis()): Pair<Float, Float> {
        val age = if (lastEventMs > 0L) nowMs - lastEventMs else 9999L
        val decay = when {
            age <= 90L -> 1f
            age <= 260L -> 1f - ((age - 90f) / 170f) * 0.75f
            else -> 0f
        }
        return (velX * decay) to (velY * decay)
    }

    @Synchronized
    fun currentMargins(nowMs: Long = SystemClock.uptimeMillis()): PredictiveMarginsPx {
        val (vx, vy) = snapshotVelocity(nowMs)
        return computePredictiveOverscanMargins(vx, vy)
    }

    @Synchronized
    fun isZoomingIn(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        return lastZoomMs > 0L && (nowMs - lastZoomMs) <= 220L && lastZoomFactor > 1.004f
    }

    @Synchronized
    fun isZoomingOut(nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        return lastZoomMs > 0L && (nowMs - lastZoomMs) <= 220L && lastZoomFactor < 0.996f
    }

    companion object {
        const val MIN_PREDICTIVE_OVERSCAN_PX = 10f
        const val MAX_PREDICTIVE_OVERSCAN_PX = 40f
        private const val FULL_OVERSCAN_SPEED_PX_PER_SEC = 2000f

        /**
         * Pure function calculating directional screen-space overscan margins in [10f, 40f] px.
         * When panning canvas right (+velX), content reveals along the left edge -> expands left margin.
         * When panning canvas up (-velY), content reveals along the bottom edge -> expands bottom margin.
         */
        fun computePredictiveOverscanMargins(
            velXPxPerSec: Float,
            velYPxPerSec: Float
        ): PredictiveMarginsPx {
            val speed = hypot(velXPxPerSec, velYPxPerSec)
            if (speed < 12f) {
                return PredictiveMarginsPx(
                    left = MIN_PREDICTIVE_OVERSCAN_PX,
                    top = MIN_PREDICTIVE_OVERSCAN_PX,
                    right = MIN_PREDICTIVE_OVERSCAN_PX,
                    bottom = MIN_PREDICTIVE_OVERSCAN_PX
                )
            }
            val speedNorm = (speed / FULL_OVERSCAN_SPEED_PX_PER_SEC).coerceIn(0f, 1f)
            val span = MAX_PREDICTIVE_OVERSCAN_PX - MIN_PREDICTIVE_OVERSCAN_PX
            val ux = velXPxPerSec / speed
            val uy = velYPxPerSec / speed

            val left = (MIN_PREDICTIVE_OVERSCAN_PX + span * speedNorm * max(0f, ux))
                .coerceIn(MIN_PREDICTIVE_OVERSCAN_PX, MAX_PREDICTIVE_OVERSCAN_PX)
            val right = (MIN_PREDICTIVE_OVERSCAN_PX + span * speedNorm * max(0f, -ux))
                .coerceIn(MIN_PREDICTIVE_OVERSCAN_PX, MAX_PREDICTIVE_OVERSCAN_PX)
            val top = (MIN_PREDICTIVE_OVERSCAN_PX + span * speedNorm * max(0f, uy))
                .coerceIn(MIN_PREDICTIVE_OVERSCAN_PX, MAX_PREDICTIVE_OVERSCAN_PX)
            val bottom = (MIN_PREDICTIVE_OVERSCAN_PX + span * speedNorm * max(0f, -uy))
                .coerceIn(MIN_PREDICTIVE_OVERSCAN_PX, MAX_PREDICTIVE_OVERSCAN_PX)

            return PredictiveMarginsPx(left = left, top = top, right = right, bottom = bottom)
        }
    }
}

/**
 * Consolidated LRU Cache for world-space GPU RenderNode & pooled Bitmap tiles.
 * Enforces strict byte and display-list caps, releases bitmaps directly back into [KodanimaBitmapPool],
 * and supports immediate Zoom-In Frustum Culling.
 */
class CanvasTileCache(
    val maxBudgetBytes: Int = defaultBudgetBytes(),
    val maxRenderNodes: Int = 256
) {
    private val map = LinkedHashMap<TileKey, CachedTile>(256, 0.75f, true)
    private val emptyTileKeys = LinkedHashMap<TileKey, Boolean>(512, 0.75f, true)
    private val quarantinedTiles = ArrayDeque<Pair<Long, CachedTile>>()
    private val quarantinedNodes = ArrayDeque<Pair<Long, Any>>()
    private var currentBytes: Int = 0
    private var currentRenderNodes: Int = 0

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun usedBytes(): Int = currentBytes

    @Synchronized
    fun isKnownEmpty(key: TileKey): Boolean = emptyTileKeys.containsKey(key)

    @Synchronized
    fun markEmpty(key: TileKey) {
        map.remove(key)?.let { accountRemoved(it) }
        emptyTileKeys[key] = true
        while (emptyTileKeys.size > 1024) {
            val first = emptyTileKeys.entries.firstOrNull()?.key ?: break
            emptyTileKeys.remove(first)
        }
    }

    @Synchronized
    fun clearEmptyForSlice(sliceTag: String) {
        val iter = emptyTileKeys.keys.iterator()
        while (iter.hasNext()) {
            if (iter.next().sliceTag == sliceTag) iter.remove()
        }
    }

    @Synchronized
    fun clearEmptyForSliceHash(sliceTag: String, contentHash: Int) {
        val iter = emptyTileKeys.keys.iterator()
        while (iter.hasNext()) {
            val k = iter.next()
            if (k.sliceTag == sliceTag && k.contentHash == contentHash) iter.remove()
        }
    }

    @Synchronized
    fun get(key: TileKey): CachedTile? {
        val entry = map[key] ?: return null
        if (entry.bitmap.isRecycled) {
            removeInternal(key)
            return null
        }
        return entry
    }

    @Synchronized
    fun put(tile: CachedTile) {
        emptyTileKeys.remove(tile.key)
        map.remove(tile.key)?.let { old ->
            accountRemoved(old)
        }
        map[tile.key] = tile
        currentBytes += tile.byteSize
        if (tile.renderNode != null) {
            currentRenderNodes++
        }
        enforceLimitsLocked()
    }

    @Synchronized
    fun promoteRenderNode(key: TileKey, node: Any?) {
        val tile = map[key] ?: return
        if (tile.renderNode == null && node != null) {
            currentRenderNodes++
        }
        tile.renderNode = node
        enforceLimitsLocked()
    }

    @Synchronized
    fun invalidateSliceRenderNodes(sliceTag: String, contentHash: Int) {
        val now = safeUptimeMs()
        for ((k, v) in map) {
            if (k.sliceTag == sliceTag && k.contentHash == contentHash && v.renderNode != null) {
                quarantinedNodes.addLast(now to v.renderNode!!)
                v.renderNode = null
                currentRenderNodes = (currentRenderNodes - 1).coerceAtLeast(0)
            }
        }
    }

    /**
     * Removes all entries for a given slice whose contentHash no longer matches (keeps cache tight).
     */
    @Synchronized
    fun pruneStaleContent(sliceTag: String, activeContentHash: Int) {
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key.sliceTag == sliceTag && e.key.contentHash != activeContentHash) {
                iter.remove()
                accountRemoved(e.value)
            }
        }
        val emptyIter = emptyTileKeys.keys.iterator()
        while (emptyIter.hasNext()) {
            val k = emptyIter.next()
            if (k.sliceTag == sliceTag && k.contentHash != activeContentHash) {
                emptyIter.remove()
            }
        }
    }

    /**
     * Aggressive Zoom-In Frustum Culling: immediately drops/releases any cached tile outside [frustumProjRect]
     * or superseded coarser LOD tiles when magnified well past them.
     */
    @Synchronized
    fun pruneForZoomIn(
        frustumProjRect: RectF,
        activeLodLevel: Int
    ): Int {
        var evicted = 0
        val iter = map.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            val tile = e.value
            val outsideFrustum = !RectF.intersects(tile.projBounds, frustumProjRect)
            val staleTooCoarse = tile.key.lodLevel < activeLodLevel - 2
            if (outsideFrustum || staleTooCoarse) {
                iter.remove()
                accountRemoved(tile)
                evicted++
            }
        }
        return evicted
    }

    /**
     * Finds already-cached fallback tiles from other LOD levels covering [targetRectProj]
     * so rapid pinch-zoom renders continuously with zero blank frames.
     */
    @Synchronized
    fun findFallbackTiles(
        sliceTag: String,
        contentHash: Int,
        targetRectProj: RectF,
        excludeLodLevel: Int
    ): List<CachedTile> {
        val candidates = ArrayList<CachedTile>(16)
        for ((k, tile) in map) {
            if (k.sliceTag == sliceTag &&
                k.contentHash == contentHash &&
                k.lodLevel != excludeLodLevel &&
                !tile.bitmap.isRecycled &&
                RectF.intersects(tile.projBounds, targetRectProj)
            ) {
                candidates.add(tile)
            }
        }
        // Paint coarser LOD tiles before finer ones so detail overrides cleanly
        candidates.sortBy { it.key.lodLevel }
        return candidates
    }

    /**
     * Finds fallback tiles for a SINGLE missing tile rectangle at the single closest LOD level
     * (first checking [contentHash], then [fallbackContentHash]), avoiding double-alpha overlap.
     */
    @Synchronized
    fun findFallbackTilesForSingleLod(
        sliceTag: String,
        contentHash: Int,
        fallbackContentHash: Int?,
        tileProjRect: RectF,
        targetLodLevel: Int,
        fallbackContentHashes: List<Int> = emptyList()
    ): List<CachedTile> {
        fun collectForHash(hash: Int): List<CachedTile> {
            var bestLod = Int.MIN_VALUE
            var bestDist = Int.MAX_VALUE
            val matches = ArrayList<CachedTile>(4)
            for ((k, tile) in map) {
                if (k.sliceTag == sliceTag &&
                    k.contentHash == hash &&
                    k.lodLevel != targetLodLevel &&
                    !tile.bitmap.isRecycled &&
                    RectF.intersects(tile.projBounds, tileProjRect)
                ) {
                    val dist = abs(k.lodLevel - targetLodLevel)
                    if (dist < bestDist || (dist == bestDist && k.lodLevel > bestLod)) {
                        bestDist = dist
                        bestLod = k.lodLevel
                        matches.clear()
                        matches.add(tile)
                    } else if (k.lodLevel == bestLod) {
                        matches.add(tile)
                    }
                }
            }
            return matches
        }
        val primary = collectForHash(contentHash)
        if (primary.isNotEmpty()) return primary
        if (fallbackContentHash != null && fallbackContentHash != contentHash) {
            val hit = collectForHash(fallbackContentHash)
            if (hit.isNotEmpty()) return hit
        }
        for (fbHash in fallbackContentHashes) {
            if (fbHash == contentHash || fbHash == fallbackContentHash) continue
            val hit = collectForHash(fbHash)
            if (hit.isNotEmpty()) return hit
        }
        return emptyList()
    }

    @Synchronized
    fun snapshotAllMatchingTilesAcrossLods(
        sliceTag: String,
        contentHash: Int,
        boundsProj: RectF
    ): List<CachedTile> {
        val out = ArrayList<CachedTile>(8)
        for ((k, tile) in map) {
            if (k.sliceTag == sliceTag &&
                k.contentHash == contentHash &&
                !tile.bitmap.isRecycled &&
                RectF.intersects(tile.projBounds, boundsProj)
            ) {
                out.add(tile)
            }
        }
        return out
    }

    @Synchronized
    fun snapshotMatchingTiles(
        sliceTag: String,
        contentHash: Int,
        lodLevel: Int,
        tileRect: Rect
    ): List<CachedTile> {
        val out = ArrayList<CachedTile>((tileRect.width() + 1) * (tileRect.height() + 1))
        for (ty in tileRect.top..tileRect.bottom) {
            for (tx in tileRect.left..tileRect.right) {
                val key = TileKey(sliceTag, contentHash, lodLevel, tx, ty)
                val tile = get(key)
                if (tile != null) {
                    out.add(tile)
                }
            }
        }
        return out
    }

    /**
     * Releases quarantined bitmaps and RenderNodes ONLY on the UI thread after HWUI RenderThread
     * has finished consuming prior frames, preventing mid-draw transparent tile flicker.
     */
    @Synchronized
    fun drainQuarantineOnUiThread(nowMs: Long = safeUptimeMs(), forceAll: Boolean = false) {
        while (quarantinedNodes.isNotEmpty()) {
            val head = quarantinedNodes.first()
            if (!forceAll && (nowMs - head.first) < QUARANTINE_MS && quarantinedNodes.size <= 96) break
            quarantinedNodes.removeFirst()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RenderNodeTileHelper.invalidate(head.second)
            }
        }
        while (quarantinedTiles.isNotEmpty()) {
            val head = quarantinedTiles.first()
            if (!forceAll && (nowMs - head.first) < QUARANTINE_MS && quarantinedTiles.size <= 64) break
            quarantinedTiles.removeFirst()
            releaseTileResourcesImmediate(head.second)
        }
    }

    @Synchronized
    fun trimMemory(level: Int) {
        @Suppress("DEPRECATION")
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> trimToBudget(maxBudgetBytes / 3)
            else -> trimToBudget((maxBudgetBytes * 2) / 3)
        }
    }

    @Synchronized
    fun clear() {
        for (tile in map.values) {
            quarantinedTiles.addLast(0L to tile)
        }
        map.clear()
        emptyTileKeys.clear()
        currentBytes = 0
        currentRenderNodes = 0
        drainQuarantineOnUiThread(forceAll = true)
    }

    private fun removeInternal(key: TileKey) {
        map.remove(key)?.let { accountRemoved(it) }
    }

    private fun accountRemoved(tile: CachedTile) {
        currentBytes = (currentBytes - tile.byteSize).coerceAtLeast(0)
        if (tile.renderNode != null) {
            currentRenderNodes = (currentRenderNodes - 1).coerceAtLeast(0)
        }
        quarantinedTiles.addLast(safeUptimeMs() to tile)
        // Safety bound if background evicts many tiles without UI draws
        while (quarantinedTiles.size > 192) {
            val oldest = quarantinedTiles.removeFirst().second
            KodanimaBitmapPool.release(oldest.bitmap)
        }
    }

    private fun enforceLimitsLocked() {
        trimToBudget(maxBudgetBytes)
        if (currentRenderNodes > maxRenderNodes) {
            val now = safeUptimeMs()
            val iter = map.entries.iterator()
            while (currentRenderNodes > maxRenderNodes && iter.hasNext()) {
                val tile = iter.next().value
                if (tile.renderNode != null) {
                    quarantinedNodes.addLast(now to tile.renderNode!!)
                    tile.renderNode = null
                    currentRenderNodes = (currentRenderNodes - 1).coerceAtLeast(0)
                }
            }
        }
    }

    private fun trimToBudget(targetBytes: Int) {
        if (currentBytes <= targetBytes) return
        val iter = map.entries.iterator()
        while (currentBytes > targetBytes && iter.hasNext()) {
            val entry = iter.next()
            iter.remove()
            accountRemoved(entry.value)
        }
    }

    private fun releaseTileResourcesImmediate(tile: CachedTile) {
        if (tile.renderNode != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNodeTileHelper.invalidate(tile.renderNode)
        }
        tile.renderNode = null
        KodanimaBitmapPool.release(tile.bitmap)
    }

    private fun safeUptimeMs(): Long {
        return try {
            SystemClock.uptimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    companion object {
        private const val QUARANTINE_MS = 220L

        fun defaultBudgetBytes(): Int {
            val poolCap = HardwareCapabilitiesEngine.currentProfile.bitmapPoolMaxBytes
            return (poolCap * 0.75f).roundToInt().coerceIn(12 * 1024 * 1024, 64 * 1024 * 1024)
        }
    }
}

sealed class TileSliceSpec(
    val sliceTag: String,
    val contentHash: Int
) {
    class LayersSlice(
        sliceTag: String,
        contentHash: Int,
        val layers: List<Layer>,
        val projWidth: Int,
        val projHeight: Int,
        val densityMult: Float
    ) : TileSliceSpec(sliceTag, contentHash)

    class OnionSlice(
        contentHash: Int,
        val settings: OnionSkinSettings,
        val prevLayers: List<List<Layer>>,
        val nextLayers: List<List<Layer>>,
        val projWidth: Int,
        val projHeight: Int,
        val densityMult: Float
    ) : TileSliceSpec("onion", contentHash)
}

/**
 * Hardware-accelerated Tile-Based Canvas Rendering Engine for Kodanima 2D.
 *
 * - Decouples tile invalidation from Pan and Rotation so navigation runs smoothly on cached GPU tiles.
 * - Uses fixed 256x256 world/project-space tiles at octave LODs with RenderNode DisplayList caching (API 29+)
 *   backed by pooled ARGB_8888 Bitmaps via [KodanimaBitmapPool].
 * - Predictive pan pre-fetching computes directional 10..40 px margins based on gesture vector & velocity.
 * - Zoom Out expands prefetching in all directions; Zoom In applies aggressive Frustum Culling.
 */
class CanvasTileEngine(
    val tileCache: CanvasTileCache = CanvasTileCache(),
    val viewportTracker: PredictiveViewportTracker = PredictiveViewportTracker(),
    val tileSizePx: Int = DEFAULT_TILE_SIZE_PX
) {
    private val tileDrawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun recordTransformDelta(
        panDxPx: Float,
        panDyPx: Float,
        zoomFactor: Float,
        nowMs: Long = SystemClock.uptimeMillis()
    ) {
        viewportTracker.recordTransformDelta(panDxPx, panDyPx, zoomFactor, nowMs)
    }

    /**
     * Computes the active LOD, visible frustum, and predictive prefetch tile coordinates
     * from the current project-to-screen transform and gesture state.
     */
    fun computeTilePlan(
        projToScreenMatrix: Matrix,
        containerWidth: Float,
        containerHeight: Float,
        screenPxPerProjPx: Float,
        nowMs: Long = SystemClock.uptimeMillis(),
        forceZoomIn: Boolean = false,
        forceZoomOut: Boolean = false
    ): TilePlan? {
        val safeW = containerWidth.coerceAtLeast(1f)
        val safeH = containerHeight.coerceAtLeast(1f)
        val inv = Matrix()
        if (!projToScreenMatrix.invert(inv)) return null

        val lodLevel = selectLodLevel(screenPxPerProjPx)
        val lodScale = lodScaleForLevel(lodLevel)
        val tileProjSpan = (tileSizePx.toFloat() / lodScale).coerceAtLeast(1f)

        val margins = viewportTracker.currentMargins(nowMs)
        val isZoomIn = forceZoomIn || viewportTracker.isZoomingIn(nowMs)
        val isZoomOut = !isZoomIn && (forceZoomOut || viewportTracker.isZoomingOut(nowMs))

        val visibleFrustum = mapScreenRectToProjAabb(inv, 0f, 0f, safeW, safeH)
        val overscanFrustum = mapScreenRectToProjAabb(
            inv = inv,
            left = -margins.left,
            top = -margins.top,
            right = safeW + margins.right,
            bottom = safeH + margins.bottom
        )

        val minVisTx = floor(visibleFrustum.left / tileProjSpan).toInt()
        val maxVisTx = floor((visibleFrustum.right - 0.001f) / tileProjSpan).toInt().coerceAtLeast(minVisTx)
        val minVisTy = floor(visibleFrustum.top / tileProjSpan).toInt()
        val maxVisTy = floor((visibleFrustum.bottom - 0.001f) / tileProjSpan).toInt().coerceAtLeast(minVisTy)
        val visibleRect = Rect(minVisTx, minVisTy, maxVisTx, maxVisTy)

        var minPreTx = floor(overscanFrustum.left / tileProjSpan).toInt()
        var maxPreTx = floor((overscanFrustum.right - 0.001f) / tileProjSpan).toInt().coerceAtLeast(minPreTx)
        var minPreTy = floor(overscanFrustum.top / tileProjSpan).toInt()
        var maxPreTy = floor((overscanFrustum.bottom - 0.001f) / tileProjSpan).toInt().coerceAtLeast(minPreTy)

        // Zoom Out: expand prefetch ring in all 4 directions before newly exposed edges appear
        if (isZoomOut) {
            minPreTx -= 1
            maxPreTx += 1
            minPreTy -= 1
            maxPreTy += 1
        }

        // Bound prefetch grid safely against extreme aspect/degenerate matrices (OOM guard)
        val maxSpanTiles = 18
        if (maxPreTx - minPreTx > maxSpanTiles) {
            val midX = (minVisTx + maxVisTx) / 2
            minPreTx = midX - maxSpanTiles / 2
            maxPreTx = midX + maxSpanTiles / 2
        }
        if (maxPreTy - minPreTy > maxSpanTiles) {
            val midY = (minVisTy + maxVisTy) / 2
            minPreTy = midY - maxSpanTiles / 2
            maxPreTy = midY + maxSpanTiles / 2
        }

        val prefetchRect = Rect(minPreTx, minPreTy, maxPreTx, maxPreTy)
        val expandedFrustum = RectF(
            minPreTx * tileProjSpan,
            minPreTy * tileProjSpan,
            (maxPreTx + 1) * tileProjSpan,
            (maxPreTy + 1) * tileProjSpan
        )

        val (velX, velY) = viewportTracker.snapshotVelocity(nowMs)
        // Map screen reveal direction (-panVelScreen) into project space for tile build prioritization
        val centerScreen = floatArrayOf(safeW * 0.5f, safeH * 0.5f)
        val revealVecProj = floatArrayOf(0f, 0f)
        val centerProj = FloatArray(2)
        inv.mapPoints(centerProj, centerScreen)
        if (hypot(velX, velY) >= 12f) {
            val stepPts = floatArrayOf(safeW * 0.5f - velX * 0.1f, safeH * 0.5f - velY * 0.1f)
            val stepProj = FloatArray(2)
            inv.mapPoints(stepProj, stepPts)
            revealVecProj[0] = stepProj[0] - centerProj[0]
            revealVecProj[1] = stepProj[1] - centerProj[1]
            val len = hypot(revealVecProj[0], revealVecProj[1]).coerceAtLeast(1e-4f)
            revealVecProj[0] /= len
            revealVecProj[1] /= len
        }

        val coords = ArrayList<TileCoord>((maxPreTx - minPreTx + 1) * (maxPreTy - minPreTy + 1))
        for (ty in minPreTy..maxPreTy) {
            for (tx in minPreTx..maxPreTx) {
                val onScreen = tx in minVisTx..maxVisTx && ty in minVisTy..maxVisTy
                coords.add(TileCoord(tx, ty, onScreen))
            }
        }

        // Prioritize visible tiles closest to center first, then directional ahead-of-pan prefetch tiles
        coords.sortWith { a, b ->
            if (a.onScreen != b.onScreen) {
                return@sortWith if (a.onScreen) -1 else 1
            }
            val aCx = (a.tileX + 0.5f) * tileProjSpan - centerProj[0]
            val aCy = (a.tileY + 0.5f) * tileProjSpan - centerProj[1]
            val bCx = (b.tileX + 0.5f) * tileProjSpan - centerProj[0]
            val bCy = (b.tileY + 0.5f) * tileProjSpan - centerProj[1]
            if (!a.onScreen) {
                val aAlign = -(aCx * revealVecProj[0] + aCy * revealVecProj[1])
                val bAlign = -(bCx * revealVecProj[0] + bCy * revealVecProj[1])
                if (abs(aAlign - bAlign) > 1f) {
                    return@sortWith aAlign.compareTo(bAlign)
                }
            }
            val aDistSq = aCx * aCx + aCy * aCy
            val bDistSq = bCx * bCx + bCy * bCy
            aDistSq.compareTo(bDistSq)
        }

        // Zoom In: aggressively cull out-of-frustum & stale coarse tiles immediately from active cache
        if (isZoomIn) {
            tileCache.pruneForZoomIn(expandedFrustum, lodLevel)
        }

        return TilePlan(
            lodLevel = lodLevel,
            lodScale = lodScale,
            tileSizePx = tileSizePx,
            tileProjSpan = tileProjSpan,
            visibleFrustumProj = visibleFrustum,
            prefetchFrustumProj = expandedFrustum,
            visibleTileRect = visibleRect,
            prefetchTileRect = prefetchRect,
            marginsPx = margins,
            isZoomingIn = isZoomIn,
            isZoomingOut = isZoomOut,
            orderedCoords = coords
        )
    }

    fun hasMissingTiles(plan: TilePlan, slices: List<TileSliceSpec>): Boolean {
        if (slices.isEmpty()) return false
        for (coord in plan.orderedCoords) {
            for (spec in slices) {
                val key = TileKey(
                    sliceTag = spec.sliceTag,
                    contentHash = spec.contentHash,
                    lodLevel = plan.lodLevel,
                    tileX = coord.tileX,
                    tileY = coord.tileY
                )
                if (!tileCache.isKnownEmpty(key) && tileCache.get(key) == null) {
                    return true
                }
            }
        }
        return false
    }

    fun hasAllVisibleTilesForSlice(plan: TilePlan, spec: TileSliceSpec): Boolean {
        for (ty in plan.visibleTileRect.top..plan.visibleTileRect.bottom) {
            for (tx in plan.visibleTileRect.left..plan.visibleTileRect.right) {
                val key = TileKey(spec.sliceTag, spec.contentHash, plan.lodLevel, tx, ty)
                if (!tileCache.isKnownEmpty(key) && tileCache.get(key) == null) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Builds missing tiles for the given [slices] matching [plan].
     * Designed for execution on [KodanimaDispatchers.backgroundRender].
     * Calls [onBatchReady] periodically so UI renders tiles progressively without popping.
     */
    fun ensureTilesForPlan(
        plan: TilePlan,
        slices: List<TileSliceSpec>,
        maxTilesPerPass: Int = 64,
        onBatchReady: (() -> Unit)? = null
    ): Int {
        var builtCount = 0
        var completedAllCoords = true
        for (coord in plan.orderedCoords) {
            if (builtCount >= maxTilesPerPass) {
                completedAllCoords = false
                break
            }
            val leftProj = coord.tileX * plan.tileProjSpan
            val topProj = coord.tileY * plan.tileProjSpan
            val rightProj = leftProj + plan.tileProjSpan
            val bottomProj = topProj + plan.tileProjSpan
            val strokePad = (96f / plan.lodScale.coerceAtLeast(0.1f)).coerceIn(48f, 360f)
            val paddedBounds = RectF(
                leftProj - strokePad,
                topProj - strokePad,
                rightProj + strokePad,
                bottomProj + strokePad
            )
            for (spec in slices) {
                if (builtCount >= maxTilesPerPass) {
                    completedAllCoords = false
                    break
                }
                val key = TileKey(
                    sliceTag = spec.sliceTag,
                    contentHash = spec.contentHash,
                    lodLevel = plan.lodLevel,
                    tileX = coord.tileX,
                    tileY = coord.tileY
                )
                if (tileCache.isKnownEmpty(key) || tileCache.get(key) != null) continue
                if (!hasDrawableContentInBounds(spec, paddedBounds)) {
                    tileCache.markEmpty(key)
                    continue
                }
                val rendered = renderTile(spec, key, plan.lodScale, plan.tileSizePx, plan.tileProjSpan)
                tileCache.put(rendered)
                builtCount++
                if (builtCount % 4 == 0) {
                    onBatchReady?.invoke()
                }
            }
        }
        // Only prune old content hashes once the entire visible+prefetch plan for the new hash is complete (0-flicker)
        if (completedAllCoords && builtCount < maxTilesPerPass) {
            slices.forEach { spec ->
                if (hasAllVisibleTilesForSlice(plan, spec)) {
                    tileCache.pruneStaleContent(spec.sliceTag, spec.contentHash)
                }
            }
        }
        if (builtCount > 0 && builtCount % 4 != 0) {
            onBatchReady?.invoke()
        }
        return builtCount
    }

    private fun hasDrawableContentInBounds(spec: TileSliceSpec, bounds: RectF): Boolean {
        return when (spec) {
            is TileSliceSpec.LayersSlice -> {
                layersHaveDrawableContent(spec.layers, bounds)
            }
            is TileSliceSpec.OnionSlice -> {
                if (!spec.settings.isEnabled) return false
                spec.prevLayers.any { layersHaveDrawableContent(it, bounds) } ||
                    spec.nextLayers.any { layersHaveDrawableContent(it, bounds) }
            }
        }
    }

    private fun layersHaveDrawableContent(layers: List<Layer>, bounds: RectF): Boolean {
        for (layer in layers) {
            if (!layer.isVisible || layer.opacity <= 0f) continue
            val att = layer.imageAttachment
            if (att != null && att.uriString.isNotBlank()) return true
            for (stroke in layer.strokes) {
                if (stroke.intersects(bounds.left, bounds.top, bounds.right, bounds.bottom)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Synchronously renders one tile on demand (e.g., inside [ensureTilesForPlan] or unit tests).
     */
    fun renderTile(
        spec: TileSliceSpec,
        key: TileKey,
        lodScale: Float,
        tileSizePx: Int = this.tileSizePx,
        tileProjSpan: Float = tileSizePx.toFloat() / lodScale
    ): CachedTile {
        val leftProj = key.tileX * tileProjSpan
        val topProj = key.tileY * tileProjSpan
        val rightProj = leftProj + tileProjSpan
        val bottomProj = topProj + tileProjSpan
        val projBounds = RectF(leftProj, topProj, rightProj, bottomProj)

        // Pad culling bounds generously so thick strokes or particle dabs overlapping tile border never get culled
        val strokePad = (96f / lodScale.coerceAtLeast(0.1f)).coerceIn(48f, 360f)
        val paddedBounds = RectF(
            leftProj - strokePad,
            topProj - strokePad,
            rightProj + strokePad,
            bottomProj + strokePad
        )

        val bmp = KodanimaBitmapPool.acquire(
            tileSizePx,
            tileSizePx,
            Bitmap.Config.ARGB_8888,
            Color.TRANSPARENT
        )
        val tileMat = Matrix().apply {
            setTranslate(-leftProj, -topProj)
            postScale(lodScale, lodScale)
        }
        val canvas = Canvas(bmp)
        when (spec) {
            is TileSliceSpec.LayersSlice -> {
                if (spec.layers.isNotEmpty()) {
                    KodanimaGraphicsEngine.drawLayersStackToCanvas(
                        layers = spec.layers,
                        canvas = canvas,
                        targetWidth = spec.projWidth,
                        targetHeight = spec.projHeight,
                        visibleBounds = paddedBounds,
                        densityMultiplier = spec.densityMult,
                        allowOutOfBounds = true,
                        viewportMatrix = tileMat,
                        viewportWidth = tileSizePx,
                        viewportHeight = tileSizePx
                    )
                }
            }
            is TileSliceSpec.OnionSlice -> {
                if (spec.settings.isEnabled &&
                    (spec.prevLayers.any { it.isNotEmpty() } || spec.nextLayers.any { it.isNotEmpty() })
                ) {
                    canvas.save()
                    canvas.concat(tileMat)
                    val onionOpacity = spec.settings.opacity
                    val prevCount = spec.prevLayers.size
                    val prevFilter = KodanimaGraphicsEngine.createOnionSkinColorFilter(spec.settings.prevColor)
                    spec.prevLayers.forEachIndexed { idx, fLayers ->
                        val step = prevCount - idx
                        val alpha = (onionOpacity * (1f - (step - 1) * 0.25f)).coerceAtLeast(0.1f)
                        for (l in fLayers) {
                            if (l.isVisible) {
                                KodanimaGraphicsEngine.drawLayerToCanvas(
                                    l, canvas, spec.projWidth, spec.projHeight,
                                    alpha, prevFilter, paddedBounds, null, spec.densityMult,
                                    allowOutOfBounds = true
                                )
                            }
                        }
                    }
                    val nextFilter = KodanimaGraphicsEngine.createOnionSkinColorFilter(spec.settings.nextColor)
                    spec.nextLayers.forEachIndexed { idx, fLayers ->
                        val step = idx + 1
                        val alpha = (onionOpacity * (1f - (step - 1) * 0.25f)).coerceAtLeast(0.1f)
                        for (l in fLayers) {
                            if (l.isVisible) {
                                KodanimaGraphicsEngine.drawLayerToCanvas(
                                    l, canvas, spec.projWidth, spec.projHeight,
                                    alpha, nextFilter, paddedBounds, null, spec.densityMult,
                                    allowOutOfBounds = true
                                )
                            }
                        }
                    }
                    canvas.restore()
                }
            }
        }

        return CachedTile(
            key = key,
            bitmap = bmp,
            renderNode = null,
            projBounds = projBounds,
            lodScale = lodScale,
            byteSize = bmp.allocationByteCount
        )
    }

    /**
     * Draws all cached tiles for [sliceTag] + [contentHash] intersecting [plan] directly onto [canvas]
     * under the caller's live project-to-screen transform ([liveProjToScreen]).
     * Uses per-tile fallback resolution so partially built updates or LOD changes NEVER leave blank tiles
     * or double-alpha overlap, and drains quarantined GPU/Bitmap resources safely on UI thread.
     */
    fun drawSliceTiles(
        canvas: Canvas,
        liveProjToScreen: Matrix,
        plan: TilePlan,
        sliceTag: String,
        contentHash: Int,
        paintOverride: Paint? = null,
        fallbackContentHash: Int? = null,
        fallbackContentHashes: List<Int> = emptyList(),
        useRenderNode: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            HardwareCapabilitiesEngine.currentProfile.supportsRenderNode &&
            canvas.isHardwareAccelerated
    ) {
        tileCache.drainQuarantineOnUiThread()
        val drawPaint = paintOverride ?: tileDrawPaint.apply {
            isFilterBitmap = true
        }
        val allowNode = useRenderNode && paintOverride == null
        val prevHash = fallbackContentHash?.takeIf { it != contentHash }
        val prevHashes = fallbackContentHashes.filter { it != contentHash && it != prevHash }

        canvas.save()
        canvas.concat(liveProjToScreen)

        val tileSpan = plan.tileProjSpan
        for (ty in plan.prefetchTileRect.top..plan.prefetchTileRect.bottom) {
            for (tx in plan.prefetchTileRect.left..plan.prefetchTileRect.right) {
                val exactKey = TileKey(sliceTag, contentHash, plan.lodLevel, tx, ty)
                val exact = tileCache.get(exactKey)
                if (exact != null) {
                    drawSingleCachedTile(canvas, exact, drawPaint, allowNode)
                    continue
                }
                if (tileCache.isKnownEmpty(exactKey)) {
                    continue
                }

                // Exact tile is still building in background: fall back strictly for THIS tile coord
                var prevExact = prevHash?.let {
                    tileCache.get(TileKey(sliceTag, it, plan.lodLevel, tx, ty))
                }
                if (prevExact == null && prevHashes.isNotEmpty()) {
                    for (fbH in prevHashes) {
                        prevExact = tileCache.get(TileKey(sliceTag, fbH, plan.lodLevel, tx, ty))
                        if (prevExact != null) break
                    }
                }
                if (prevExact != null) {
                    drawSingleCachedTile(canvas, prevExact, drawPaint, allowNode)
                    continue
                }

                val leftProj = tx * tileSpan
                val topProj = ty * tileSpan
                val tileRectProj = RectF(leftProj, topProj, leftProj + tileSpan, topProj + tileSpan)
                val lodFallbacks = tileCache.findFallbackTilesForSingleLod(
                    sliceTag = sliceTag,
                    contentHash = contentHash,
                    fallbackContentHash = prevHash,
                    tileProjRect = tileRectProj,
                    targetLodLevel = plan.lodLevel,
                    fallbackContentHashes = prevHashes
                )
                if (lodFallbacks.isNotEmpty()) {
                    canvas.save()
                    canvas.clipRect(tileRectProj)
                    for (fb in lodFallbacks) {
                        drawSingleCachedTile(canvas, fb, drawPaint, allowNode)
                    }
                    canvas.restore()
                }
            }
        }

        canvas.restore()
    }

    private fun drawSingleCachedTile(
        canvas: Canvas,
        tile: CachedTile,
        paint: Paint,
        allowRenderNode: Boolean
    ) {
        if (tile.bitmap.isRecycled) return
        canvas.save()
        canvas.translate(tile.projBounds.left, tile.projBounds.top)
        val invLod = 1f / tile.lodScale
        canvas.scale(invLod, invLod)
        var drawnWithNode = false
        if (allowRenderNode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val node = RenderNodeTileHelper.buildOrGet(tile.renderNode, tile.bitmap, tile.bitmap.width)
            if (node != null && node !== tile.renderNode) {
                tileCache.promoteRenderNode(tile.key, node)
            }
            if (node != null) {
                drawnWithNode = RenderNodeTileHelper.draw(canvas, node)
            }
        }
        if (!drawnWithNode) {
            canvas.drawBitmap(tile.bitmap, 0f, 0f, paint)
        }
        canvas.restore()
    }

    /**
     * Copy-on-write synchronous stroke stamping & empty-tile materialization on ACTION_UP
     * so strokes never vanish or blink while the background worker rasterizes the new contentHash.
     */
    fun stampStrokeIntoCachedTiles(
        sliceTag: String,
        contentHash: Int,
        stroke: DrawingStroke,
        densityMult: Float
    ) {
        stampStrokeIntoCachedTiles(
            sliceTag = sliceTag,
            contentHash = contentHash,
            stroke = stroke,
            densityMult = densityMult,
            layers = null,
            projWidth = 0,
            projHeight = 0,
            activePlan = null
        )
    }

    fun stampStrokeIntoCachedTiles(
        sliceTag: String,
        contentHash: Int,
        stroke: DrawingStroke,
        densityMult: Float,
        layers: List<Layer>? = null,
        projWidth: Int = 0,
        projHeight: Int = 0,
        activePlan: TilePlan? = null
    ) {
        if (stroke.points.isEmpty()) return
        val sMinX = if (stroke.minX != Float.MAX_VALUE) stroke.minX else stroke.points.minOf { it.x }
        val sMinY = if (stroke.minY != Float.MAX_VALUE) stroke.minY else stroke.points.minOf { it.y }
        val sMaxX = if (stroke.maxX != -Float.MAX_VALUE) stroke.maxX else stroke.points.maxOf { it.x }
        val sMaxY = if (stroke.maxY != -Float.MAX_VALUE) stroke.maxY else stroke.points.maxOf { it.y }
        val pad = stroke.brushSize * 1.8f + 24f
        val sBounds = RectF(sMinX - pad, sMinY - pad, sMaxX + pad, sMaxY + pad)

        tileCache.clearEmptyForSlice(sliceTag)
        val existingTiles = tileCache.snapshotAllMatchingTilesAcrossLods(sliceTag, contentHash, sBounds)
        val specForFullRender = if (layers != null && projWidth > 0 && projHeight > 0) {
            TileSliceSpec.LayersSlice(sliceTag, contentHash, layers, projWidth, projHeight, densityMult)
        } else null

        for (oldTile in existingTiles) {
            if (oldTile.bitmap.isRecycled) continue
            val updatedTile = if (stroke.tool == ToolType.ERASER && specForFullRender != null) {
                renderTile(
                    spec = specForFullRender,
                    key = oldTile.key,
                    lodScale = oldTile.lodScale,
                    tileSizePx = oldTile.bitmap.width,
                    tileProjSpan = oldTile.projBounds.width()
                )
            } else {
                // Copy-on-write: never mutate an existing tile's Bitmap in-place while HWUI RenderThread reads it
                val w = oldTile.bitmap.width
                val h = oldTile.bitmap.height
                val copyBmp = KodanimaBitmapPool.acquire(w, h, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                val c = Canvas(copyBmp)
                c.drawBitmap(oldTile.bitmap, 0f, 0f, null)
                val mat = Matrix().apply {
                    setTranslate(-oldTile.projBounds.left, -oldTile.projBounds.top)
                    postScale(oldTile.lodScale, oldTile.lodScale)
                }
                c.save()
                c.concat(mat)
                KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, c, 1.0f, null, densityMult)
                c.restore()
                CachedTile(
                    key = oldTile.key,
                    bitmap = copyBmp,
                    renderNode = null,
                    projBounds = RectF(oldTile.projBounds),
                    lodScale = oldTile.lodScale,
                    byteSize = copyBmp.allocationByteCount
                )
            }
            tileCache.put(updatedTile)
        }

        // Materialize previously-empty tiles touched by this stroke at active LOD so stroke #1 never blinks!
        if (activePlan != null && stroke.tool != ToolType.ERASER) {
            val span = activePlan.tileProjSpan
            val minTx = max(activePlan.prefetchTileRect.left, floor(sBounds.left / span).toInt())
            val maxTx = min(activePlan.prefetchTileRect.right, floor((sBounds.right - 0.001f) / span).toInt())
            val minTy = max(activePlan.prefetchTileRect.top, floor(sBounds.top / span).toInt())
            val maxTy = min(activePlan.prefetchTileRect.bottom, floor((sBounds.bottom - 0.001f) / span).toInt())
            var materialized = 0
            materializeLoop@ for (ty in minTy..maxTy) {
                for (tx in minTx..maxTx) {
                    if (materialized >= 64) break@materializeLoop
                    val key = TileKey(sliceTag, contentHash, activePlan.lodLevel, tx, ty)
                    if (tileCache.get(key) != null) continue
                    val tile = if (specForFullRender != null) {
                        renderTile(specForFullRender, key, activePlan.lodScale, activePlan.tileSizePx, span)
                    } else {
                        val leftP = tx * span
                        val topP = ty * span
                        val bmp = KodanimaBitmapPool.acquire(activePlan.tileSizePx, activePlan.tileSizePx, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
                        val c = Canvas(bmp)
                        val mat = Matrix().apply {
                            setTranslate(-leftP, -topP)
                            postScale(activePlan.lodScale, activePlan.lodScale)
                        }
                        c.save()
                        c.concat(mat)
                        KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, c, 1.0f, null, densityMult)
                        c.restore()
                        CachedTile(
                            key = key,
                            bitmap = bmp,
                            renderNode = null,
                            projBounds = RectF(leftP, topP, leftP + span, topP + span),
                            lodScale = activePlan.lodScale,
                            byteSize = bmp.allocationByteCount
                        )
                    }
                    tileCache.put(tile)
                    materialized++
                }
            }
        }
    }

    /**
     * Samples a composite pixel directly at world/project coordinates (projX, projY) using a 1x1 render pass
     * across active visible layers — works both inside and outside the project paper bounds at any zoom.
     */
    fun sampleProjectPixelAt(
        layers: List<Layer>,
        projWidth: Int,
        projHeight: Int,
        projX: Float,
        projY: Float,
        densityMult: Float,
        baseBackgroundColor: Int = Color.WHITE
    ): Long {
        val sampleBmp = KodanimaBitmapPool.acquire(1, 1, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
        return try {
            val c = Canvas(sampleBmp)
            if (projX in 0f..projWidth.toFloat() && projY in 0f..projHeight.toFloat()) {
                c.drawColor(baseBackgroundColor)
            }
            val mat = Matrix().apply {
                setTranslate(-floor(projX), -floor(projY))
            }
            val bounds = RectF(projX - 64f, projY - 64f, projX + 64f, projY + 64f)
            KodanimaGraphicsEngine.drawLayersStackToCanvas(
                layers = layers,
                canvas = c,
                targetWidth = projWidth,
                targetHeight = projHeight,
                visibleBounds = bounds,
                densityMultiplier = densityMult,
                allowOutOfBounds = true,
                viewportMatrix = mat,
                viewportWidth = 1,
                viewportHeight = 1
            )
            sampleBmp.getPixel(0, 0).toLong() and 0xFFFFFFFFL
        } finally {
            KodanimaBitmapPool.release(sampleBmp)
        }
    }

    fun releaseAll() {
        tileCache.clear()
        viewportTracker.reset()
    }

    companion object {
        const val DEFAULT_TILE_SIZE_PX = 256
        const val MIN_LOD_LEVEL = -3
        const val MAX_LOD_LEVEL = 2

        fun selectLodLevel(screenPxPerProjPx: Float): Int {
            val safe = screenPxPerProjPx.coerceIn(0.05f, 16f)
            val raw = ceil(log2((safe * 0.85f).toDouble())).toInt()
            return raw.coerceIn(MIN_LOD_LEVEL, MAX_LOD_LEVEL)
        }

        fun lodScaleForLevel(lodLevel: Int): Float {
            return 2.0.pow(lodLevel.coerceIn(MIN_LOD_LEVEL, MAX_LOD_LEVEL)).toFloat()
        }

        fun mapScreenRectToProjAabb(
            inv: Matrix,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ): RectF {
            val corners = floatArrayOf(
                left, top,
                right, top,
                right, bottom,
                left, bottom
            )
            inv.mapPoints(corners)
            val minX = min(min(corners[0], corners[2]), min(corners[4], corners[6]))
            val maxX = max(max(corners[0], corners[2]), max(corners[4], corners[6]))
            val minY = min(min(corners[1], corners[3]), min(corners[5], corners[7]))
            val maxY = max(max(corners[1], corners[3]), max(corners[5], corners[7]))
            return RectF(minX, minY, maxX, maxY)
        }
    }
}
