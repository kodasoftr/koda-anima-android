package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import com.example.engine.CachedTile
import com.example.engine.CanvasTileCache
import com.example.engine.CanvasTileEngine
import com.example.engine.MultiplayerStudioEngine
import com.example.engine.PredictiveViewportTracker
import com.example.engine.TileKey
import com.example.engine.TileSliceSpec
import com.example.model.DrawingStroke
import com.example.model.Layer
import com.example.model.StrokePoint
import com.example.model.ToolType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CanvasTileAndLanTest {

    private fun countNonTransparentPixels(bmp: Bitmap): Int {
        var count = 0
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        for (p in pixels) {
            if (Color.alpha(p) > 0) count++
        }
        return count
    }

    @Test
    fun predictiveOverscanMargins_clampsBetween10And40PxWithDirectionalPolarity() {
        val stationary = PredictiveViewportTracker.computePredictiveOverscanMargins(0f, 0f)
        assertEquals(10f, stationary.left, 0.01f)
        assertEquals(10f, stationary.top, 0.01f)
        assertEquals(10f, stationary.right, 0.01f)
        assertEquals(10f, stationary.bottom, 0.01f)

        // Panning canvas right (+vx) reveals content on the left edge -> left margin expands to max 40f
        val fastRight = PredictiveViewportTracker.computePredictiveOverscanMargins(4500f, 0f)
        assertEquals(40f, fastRight.left, 0.01f)
        assertEquals(10f, fastRight.right, 0.01f)
        assertEquals(10f, fastRight.top, 0.01f)
        assertEquals(10f, fastRight.bottom, 0.01f)

        // Panning canvas left-up (-vx, -vy) reveals right and bottom edges
        val diagonalFast = PredictiveViewportTracker.computePredictiveOverscanMargins(-3000f, -3000f)
        assertEquals(10f, diagonalFast.left, 0.01f)
        assertEquals(10f, diagonalFast.top, 0.01f)
        assertTrue(diagonalFast.right in 25f..40f)
        assertTrue(diagonalFast.bottom in 25f..40f)

        // Moderate speed scales proportionally inside (10f, 40f)
        val mediumDown = PredictiveViewportTracker.computePredictiveOverscanMargins(0f, 1000f)
        assertTrue(mediumDown.top > 10f && mediumDown.top < 40f)
        assertEquals(10f, mediumDown.bottom, 0.01f)
    }

    @Test
    fun tilePlan_zoomOutExpandsAllDirectionsAndZoomInPrunesOutOfFrustum() {
        val tightBudget = 4 * 256 * 256 * 4 // 1 MB (exact 4 tiles of 256x256 ARGB_8888)
        val cache = CanvasTileCache(maxBudgetBytes = tightBudget, maxRenderNodes = 16)
        val engine = CanvasTileEngine(tileCache = cache)

        val projToScreen = Matrix().apply {
            setScale(1f, 1f)
        }

        val basePlan = engine.computeTilePlan(
            projToScreenMatrix = projToScreen,
            containerWidth = 512f,
            containerHeight = 512f,
            screenPxPerProjPx = 1f,
            nowMs = 1000L
        )
        assertNotNull(basePlan)
        basePlan!!

        // Zoom Out via gesture tracker + explicit flag expands prefetchTileRect by +1 on all 4 sides
        engine.recordTransformDelta(0f, 0f, 0.85f, nowMs = 1100L)
        val zoomOutPlan = engine.computeTilePlan(
            projToScreenMatrix = projToScreen,
            containerWidth = 512f,
            containerHeight = 512f,
            screenPxPerProjPx = 1f,
            nowMs = 1116L,
            forceZoomOut = true
        )!!
        assertTrue(zoomOutPlan.isZoomingOut)
        assertEquals(basePlan.prefetchTileRect.left - 1, zoomOutPlan.prefetchTileRect.left)
        assertEquals(basePlan.prefetchTileRect.top - 1, zoomOutPlan.prefetchTileRect.top)
        assertEquals(basePlan.prefetchTileRect.right + 1, zoomOutPlan.prefetchTileRect.right)
        assertEquals(basePlan.prefetchTileRect.bottom + 1, zoomOutPlan.prefetchTileRect.bottom)

        // Seed cache with:
        // 1) Visible tile at active LOD (0) -> should survive Zoom In
        // 2) Far offscreen tile outside frustum -> should be culled on Zoom In
        // 3) Stale coarse LOD tile (-3 < 0 - 2) -> should be culled on Zoom In
        fun makeTile(tx: Int, ty: Int, lod: Int, span: Float): CachedTile {
            val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val left = tx * span
            val top = ty * span
            return CachedTile(
                key = TileKey("static", 123, lod, tx, ty),
                bitmap = bmp,
                projBounds = RectF(left, top, left + span, top + span),
                lodScale = CanvasTileEngine.lodScaleForLevel(lod),
                byteSize = bmp.allocationByteCount
            )
        }

        val visibleKey = TileKey("static", 123, 0, 0, 0)
        val offscreenKey = TileKey("static", 123, 0, 25, 25)
        val staleCoarseKey = TileKey("static", 123, -3, 0, 0)

        cache.put(makeTile(0, 0, 0, 256f))
        cache.put(makeTile(25, 25, 0, 256f))
        cache.put(makeTile(0, 0, -3, 2048f))
        assertEquals(3, cache.size())

        // Zoom In triggers aggressive Frustum Culling
        engine.recordTransformDelta(0f, 0f, 1.25f, nowMs = 1200L)
        val zoomInPlan = engine.computeTilePlan(
            projToScreenMatrix = projToScreen,
            containerWidth = 512f,
            containerHeight = 512f,
            screenPxPerProjPx = 1f,
            nowMs = 1216L,
            forceZoomIn = true
        )!!
        assertTrue(zoomInPlan.isZoomingIn)
        assertNotNull("Visible tile must stay cached during Zoom In", cache.get(visibleKey))
        assertNull("Off-frustum tile must be evicted during Zoom In", cache.get(offscreenKey))
        assertNull("Stale ultra-coarse LOD tile must be evicted during Zoom In", cache.get(staleCoarseKey))

        // Bounded LRU verification: inserting 6 tiles into 4-tile budget must cap bytes <= tightBudget
        for (i in 0 until 6) {
            cache.put(makeTile(i, 0, 0, 256f))
        }
        assertTrue(
            "Cache byte usage ${cache.usedBytes()} must stay <= budget $tightBudget",
            cache.usedBytes() <= tightBudget
        )
        assertTrue(cache.size() <= 4)
        engine.releaseAll()
    }

    @Test
    fun worldSpaceTiles_renderOffCanvasAndRotatedStrokesAndSkipEmptyTiles() {
        val engine = CanvasTileEngine()
        val offCanvasStroke = DrawingStroke(
            id = "s_off",
            tool = ToolType.PEN,
            color = 0xFF00E5FF,
            brushSize = 18f,
            points = listOf(
                StrokePoint(-220f, 120f),
                StrokePoint(200f, 120f),
                StrokePoint(1400f, 120f)
            ),
            minX = -220f,
            minY = 120f,
            maxX = 1400f,
            maxY = 120f
        )
        val layer = Layer(id = "l1", isVisible = true, opacity = 1f, strokes = listOf(offCanvasStroke))
        val slice = TileSliceSpec.LayersSlice(
            sliceTag = "static",
            contentHash = 777,
            layers = listOf(layer),
            projWidth = 1280,
            projHeight = 720,
            densityMult = 1f
        )

        // Rotated -4 deg viewport covering negative X off-canvas and right off-canvas coordinates
        val mat = Matrix().apply {
            setRotate(-4f, 640f, 360f)
            postTranslate(300f, 100f)
        }
        val plan = engine.computeTilePlan(
            projToScreenMatrix = mat,
            containerWidth = 1080f,
            containerHeight = 720f,
            screenPxPerProjPx = 1f,
            nowMs = 2000L
        )!!

        val built = engine.ensureTilesForPlan(plan, listOf(slice), maxTilesPerPass = 128)
        assertTrue("Expected at least one tile rasterized along stroke", built > 0)

        // Tile (-1, 0) covers X=[-256, 0), Y=[0, 256) outside project paper left edge
        val offLeftKey = TileKey("static", 777, plan.lodLevel, -1, 0)
        val offLeftTile = engine.tileCache.get(offLeftKey)
            ?: engine.renderTile(slice, offLeftKey, plan.lodScale, plan.tileSizePx, plan.tileProjSpan)
        assertTrue(
            "Off-canvas left tile at x=-220 must contain rendered stroke pixels",
            countNonTransparentPixels(offLeftTile.bitmap) > 0
        )

        // Tile (5, 0) covers X=[1280, 1536), Y=[0, 256) outside project paper right edge (width 1280)
        val offRightKey = TileKey("static", 777, plan.lodLevel, 5, 0)
        val offRightTile = engine.tileCache.get(offRightKey)
            ?: engine.renderTile(slice, offRightKey, plan.lodScale, plan.tileSizePx, plan.tileProjSpan)
        assertTrue(
            "Off-canvas right tile at x=1400 must contain rendered stroke pixels",
            countNonTransparentPixels(offRightTile.bitmap) > 0
        )

        // Far empty tile (e.g., y = 2 -> [512, 768)) inside plan should be marked known-empty without allocating a tile bitmap
        val emptyKey = TileKey("static", 777, plan.lodLevel, 0, 2)
        if (emptyKey.tileX in plan.prefetchTileRect.left..plan.prefetchTileRect.right &&
            emptyKey.tileY in plan.prefetchTileRect.top..plan.prefetchTileRect.bottom
        ) {
            assertTrue("Empty tile must be skipped with zero bitmap allocation", engine.tileCache.isKnownEmpty(emptyKey))
            assertNull(engine.tileCache.get(emptyKey))
        }

        // Verify drawSliceTiles composites cleanly onto rotated Canvas without throwing
        val targetBmp = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888)
        val targetCanvas = Canvas(targetBmp)
        engine.drawSliceTiles(targetCanvas, mat, plan, "static", 777, useRenderNode = false)
        assertTrue("Composite frame must have non-transparent pixels", countNonTransparentPixels(targetBmp) > 0)
        targetBmp.recycle()

        // Verify synchronous stroke stamping updates cached tile and clears empty-tile flag
        val newStroke = DrawingStroke(
            id = "s_stamp",
            tool = ToolType.PEN,
            color = 0xFFFF4081,
            brushSize = 20f,
            points = listOf(StrokePoint(-180f, 180f), StrokePoint(-80f, 180f))
        )
        engine.tileCache.put(offLeftTile)
        engine.stampStrokeIntoCachedTiles("static", 777, newStroke, 1f)
        assertFalse(engine.tileCache.isKnownEmpty(emptyKey))
        engine.releaseAll()
    }

    @Test
    fun lanCoop_enforcesVectorOnlyPayloadsAndLosslessStrokeSyncWithPreviewDecimation() {
        val validTypes = listOf(
            """{"type":"STROKE","frameIndex":0,"layerIndex":0,"stroke":{"id":"1"}}""",
            """{"type":"STROKE_PREVIEW","frameIndex":0,"layerIndex":0}""",
            """{"type":"CURSOR","userId":"u1","x":10.5,"y":-40.2}""",
            """{"type":"UNDO_STROKE","frameIndex":0,"layerIndex":0,"strokeIds":["1"]}""",
            """{"type":"REDO_STROKE","frameIndex":0,"layerIndex":0,"strokes":[]}""",
            """{"type":"ADD_LAYER","layerName":"Ink"}""",
            """{"type":"MOVE_LAYER","fromIndex":0,"toIndex":1}""",
            """{"type":"ADD_FRAME","insertAt":1}""",
            """{"type":"CLEAR_FRAME","frameIndex":0}""",
            """{"type":"SYNC_FRAME_LOCAL_LAYERS","frameIndex":0,"localLayers":[]}"""
        )
        for (pkt in validTypes) {
            assertTrue("Expected vector packet to pass validation: $pkt", MultiplayerStudioEngine.isVectorOnlySyncPayload(pkt))
        }

        val forbiddenRasterPayloads = listOf(
            """{"type":"STROKE","pixelsBase64":"AAAA"}""",
            """{"type":"CURSOR","bakedTextureBase64":"BBBB"}""",
            """{"type":"SYNC_FRAME_LOCAL_LAYERS","bytesBase64":"CCCC"}""",
            """{"type":"STROKE","textureBase64":"DDDD"}""",
            """{"type":"STROKE_PREVIEW","bitmapBase64":"EEEE"}""",
            """{"type":"STROKE","imageBase64":"FFFF"}"""
        )
        for (bad in forbiddenRasterPayloads) {
            assertFalse("Heavy raster payload must be rejected: $bad", MultiplayerStudioEngine.isVectorOnlySyncPayload(bad))
        }

        // Dense live stroke preview decimation (>500 pts -> <=200 pts) preserves exact endpoints
        val densePoints = (0..550).map { i ->
            StrokePoint(x = -120f + i * 2.5f, y = 40f + (i % 7) * 3f, pressure = 0.75f, timestamp = 1000L + i)
        }
        val denseStroke = DrawingStroke(
            id = "dense_1",
            authorId = "peer_a",
            tool = ToolType.PEN,
            color = 0xFF00E5FF,
            brushSize = 14f,
            opacity = 0.88f,
            points = densePoints,
            minX = -120f,
            minY = 40f,
            maxX = -120f + 550 * 2.5f,
            maxY = 58f
        )

        val preview = MultiplayerStudioEngine.decimateStrokePreview(denseStroke, maxPoints = 200)
        assertTrue("Decimated preview must have <= 200 points (got ${preview.points.size})", preview.points.size <= 200)
        assertEquals(densePoints.first().x, preview.points.first().x, 0.001f)
        assertEquals(densePoints.first().y, preview.points.first().y, 0.001f)
        assertEquals(densePoints.last().x, preview.points.last().x, 0.001f)
        assertEquals(densePoints.last().y, preview.points.last().y, 0.001f)

        // Full committed stroke round-trips losslessly through MultiplayerStudioEngine JSON serializer
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val engine = MultiplayerStudioEngine(scope)
            val json = engine.strokeToJson(denseStroke)
            val wireLine = JSONObject().apply {
                put("type", "STROKE")
                put("frameIndex", 0)
                put("layerIndex", 0)
                put("stroke", json)
            }.toString()
            assertTrue(MultiplayerStudioEngine.isVectorOnlySyncPayload(wireLine))

            val parsed = engine.parseStrokeFromJson(json)
            assertEquals(denseStroke.id, parsed.id)
            assertEquals(denseStroke.points.size, parsed.points.size)
            assertEquals(denseStroke.minX, parsed.minX, 0.01f)
            assertEquals(denseStroke.maxX, parsed.maxX, 0.01f)
            assertEquals(denseStroke.brushSize, parsed.brushSize, 0.01f)
        } finally {
            scope.cancel()
        }
    }
}
