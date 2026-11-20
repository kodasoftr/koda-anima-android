package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.engine.KodanimaBitmapPool
import com.example.engine.KodanimaGraphicsEngine
import com.example.engine.PreferencesStorageManager
import com.example.engine.ProjectCompressionManager
import com.example.engine.ProjectStorageManager
import com.example.engine.SelectionGeometryHelper
import com.example.engine.SplashAudioEngine
import com.example.model.AnimationProject
import com.example.model.BezierNode
import com.example.model.DrawingStroke
import com.example.model.Frame
import com.example.model.KeyboardShortcutBinding
import com.example.model.Layer
import com.example.model.ProjectResolution
import com.example.model.ShortcutAction
import com.example.model.ShortcutRegistry
import com.example.model.StrokePoint
import com.example.model.ToolType
import com.example.viewmodel.KodanimaViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkflowFixesTest {

    @After
    fun tearDown() {
        KodanimaBitmapPool.clear()
    }

    @Test
    fun `splash audio volume is capped around 50 percent and all 5 harmonies are distinct`() {
        val sampleCount = 44100 * 2
        val waveforms = (1..5).map { variant ->
            val pcm = SplashAudioEngine.synthesizePs2ChordPcm(variant, sampleCount)
            val maxPeak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("Variant $variant should be clearly audible (peak=$maxPeak)", maxPeak > 1500)
            assertTrue("Variant $variant peak $maxPeak must respect ~50% ceiling (<= 16500)", maxPeak <= 16500)
            pcm
        }

        // Verify pairwise waveform divergence across all 5 chord harmonies
        for (i in waveforms.indices) {
            for (j in i + 1 until waveforms.size) {
                var diffEnergy = 0L
                val a = waveforms[i]
                val b = waveforms[j]
                for (k in 0 until minOf(a.size, b.size) step 32) {
                    diffEnergy += abs(a[k].toInt() - b[k].toInt())
                }
                assertTrue(
                    "Variants ${i + 1} and ${j + 1} must sound distinctly different",
                    diffEnergy > 500_000L
                )
            }
        }
    }

    @Test
    fun `project compression deduplicates renamed archives and skips blank Novo Projeto shell`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = AnimationProject(
            id = "proj-dedup-1",
            name = "Minha Animacao",
            frames = listOf(Frame(layers = listOf(Layer(name = "Base"))))
        )
        ProjectCompressionManager.compressAndCloseProject(context, original, null)
        val renamed = original.copy(name = "Minha Animacao Editada")
        ProjectCompressionManager.compressAndCloseProject(context, renamed, null)

        val listed = ProjectCompressionManager.listAllSavedProjects(context, null)
            .filter { it.project.id == "proj-dedup-1" }
        assertEquals("Renamed project must not create duplicate entries for the same ID", 1, listed.size)
        assertEquals("Minha Animacao Editada", listed[0].project.name)

        // Blank "Novo Projeto" must NOT be persisted when closed untouched
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        val beforeCount = vm.uiState.value.savedProjects.size
        vm.loadNewEmptyProject("Novo Projeto", ProjectResolution.PRESETS[0], 12)
        // Force untouched blank name without auto-suffix if one doesn't exist
        val untouchedId = vm.uiState.value.project.id
        vm.closeAndSaveCurrentProject(context)
        val afterIds = ProjectCompressionManager.listAllSavedProjects(context, null).map { it.project.id }
        assertFalse("Untouched blank Novo Projeto shell must not pollute saved projects", untouchedId in afterIds)
        assertTrue(vm.uiState.value.savedProjects.size <= beforeCount + 1)

        // Clean up test file
        ProjectCompressionManager.deleteProjectEntirely(context, "proj-dedup-1", null)
        ProjectStorageManager.deleteProject(context, "proj-dedup-1")
    }

    @Test
    fun `loadNewEmptyProject ensures unique names and duplicateProject creates single copy`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        vm.loadNewEmptyProject("Cena Unica", ProjectResolution.PRESETS[0], 12)
        val firstProj = vm.uiState.value.project.copy(
            frames = listOf(
                Frame(
                    layers = listOf(
                        Layer(
                            strokes = listOf(
                                DrawingStroke(
                                    tool = ToolType.PEN,
                                    points = listOf(StrokePoint(10f, 10f), StrokePoint(40f, 40f))
                                )
                            )
                        )
                    )
                )
            )
        )
        vm.openProject(firstProj)
        vm.closeAndSaveCurrentProject(context)

        // Creating another project with same desired name auto-suffixes to avoid duplicate name
        vm.loadNewEmptyProject("Cena Unica", ProjectResolution.PRESETS[0], 12)
        assertEquals("Cena Unica (2)", vm.uiState.value.project.name)

        // Duplicating creates exactly one distinct copy
        val countBeforeDup = vm.uiState.value.savedProjects.size
        vm.duplicateProject(context, firstProj.id)
        val matches = vm.uiState.value.savedProjects.filter { it.name.startsWith("Cena Unica") }
        assertEquals(countBeforeDup + 1, vm.uiState.value.savedProjects.size)
        assertEquals(2, matches.size)
        assertNotEquals(matches[0].id, matches[1].id)

        matches.forEach {
            ProjectCompressionManager.deleteProjectEntirely(context, it.id, null)
            ProjectStorageManager.deleteProject(context, it.id)
        }
    }

    @Test
    fun `keyboard shortcuts persist save and reset to factory defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        val customized = ShortcutRegistry.defaultMap().toMutableMap()
        customized[ShortcutAction.TOOL_BEZIER_PEN] = KeyboardShortcutBinding(
            action = ShortcutAction.TOOL_BEZIER_PEN,
            keyCode = KeyEvent.KEYCODE_K,
            ctrl = true,
            shift = false
        )
        vm.updateKeyboardShortcuts(customized)

        val reloadedPrefs = PreferencesStorageManager.loadPreferences(context)
        val savedBinding = reloadedPrefs.keyboardShortcuts[ShortcutAction.TOOL_BEZIER_PEN]!!
        assertEquals(KeyEvent.KEYCODE_K, savedBinding.keyCode)
        assertTrue(savedBinding.ctrl)

        val restored = vm.resetKeyboardShortcutsToFactory()
        assertEquals(KeyEvent.KEYCODE_V, restored[ShortcutAction.TOOL_BEZIER_PEN]!!.keyCode)
        assertFalse(restored[ShortcutAction.TOOL_BEZIER_PEN]!!.ctrl)

        val reloadedAfterReset = PreferencesStorageManager.loadPreferences(context)
        assertEquals(
            KeyEvent.KEYCODE_V,
            reloadedAfterReset.keyboardShortcuts[ShortcutAction.TOOL_BEZIER_PEN]!!.keyCode
        )
    }

    @Test
    fun `bezier pen confirms curve on Enter key in PC mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Teste Bezier", ProjectResolution.PRESETS[0], 12)
        vm.selectTool(ToolType.BEZIER_PEN)
        vm.setBezierNodes(
            listOf(
                BezierNode(anchorX = 20f, anchorY = 30f, handleOutX = 50f, handleOutY = 10f),
                BezierNode(anchorX = 140f, anchorY = 80f, handleInX = 110f, handleInY = 100f)
            )
        )
        assertEquals(2, vm.uiState.value.bezierNodes.size)

        val handled = vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER), context)
        assertTrue("Enter key must be handled when Bezier curve has >= 2 nodes", handled)
        assertTrue("Bezier nodes must clear after confirmation", vm.uiState.value.bezierNodes.isEmpty())

        val layer = vm.uiState.value.project.frames[0].layers[vm.uiState.value.activeLayerIndex]
        assertTrue(
            "Confirmed Bezier curve must commit a DrawingStroke with ToolType.BEZIER_PEN",
            layer.strokes.any { it.tool == ToolType.BEZIER_PEN && it.points.size >= 10 }
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `magic wand extracts ordered single square perimeter and splits strokes without leaving duplicates`() {
        // 1. Verify ordered square perimeter extraction
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val paint = android.graphics.Paint().apply { color = Color.RED }
        canvas.drawRect(30f, 30f, 80f, 80f, paint)

        val wand = KodanimaGraphicsEngine.createMagicWandRegion(bmp, 55, 55, tolerance = 16)
        bmp.recycle()

        assertTrue("Magic wand boundary must contain >= 4 perimeter points", wand.boundary.size >= 4)
        assertEquals(30f, wand.minX, 1.5f)
        assertEquals(30f, wand.minY, 1.5f)
        assertEquals(79f, wand.maxX, 1.5f)
        assertEquals(79f, wand.maxY, 1.5f)
        // Perimeter steps around a pure rectangle must be orthogonal (no crisscrossing diagonals)
        for (i in 0 until wand.boundary.size - 1) {
            val a = wand.boundary[i]
            val b = wand.boundary[i + 1]
            val isAxisAligned = abs(a.x - b.x) < 0.01f || abs(a.y - b.y) < 0.01f
            assertTrue("Square perimeter segment $a -> $b must be cleanly ordered", isAxisAligned)
        }

        // 2. Verify stroke splitting + move/delete only affects inside portion
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Teste Selecao", ProjectResolution.PRESETS[0], 12)
        val crossingStroke = DrawingStroke(
            id = "cross-1",
            tool = ToolType.PEN,
            color = 0xFFFF0000L,
            brushSize = 6f,
            points = listOf(StrokePoint(0f, 50f), StrokePoint(200f, 50f))
        )
        vm.addStrokeToCurrentLayer(crossingStroke)

        val box = listOf(
            StrokePoint(50f, 20f),
            StrokePoint(150f, 20f),
            StrokePoint(150f, 80f),
            StrokePoint(50f, 80f)
        )
        vm.setLassoSelection(box)
        val sel = vm.uiState.value.activeSelection
        assertTrue(sel.isActive)
        assertTrue("Original crossing stroke ID must be hidden from Layer 1", "cross-1" in sel.hiddenSourceStrokeIds)
        assertTrue("Captured inside segment must exist", sel.capturedStrokes.isNotEmpty())
        assertEquals("Two outside segments [0..50] and [150..200] must stay stationary", 2, sel.outsideReplacementStrokes.size)

        vm.updateSelectionOffset(0f, 40f)
        vm.applySelectionTransform()

        val layerIdx = vm.uiState.value.activeLayerIndex
        val finalStrokes = vm.uiState.value.project.frames[0].layers[layerIdx].strokes
        assertFalse("Original unsplit stroke must not remain underneath as a duplicate", finalStrokes.any { it.id == "cross-1" })
        assertTrue("Outside segments stay at y=50", finalStrokes.any { st -> st.points.all { abs(it.y - 50f) < 0.5f } })
        assertTrue("Inside segment moved to y=90", finalStrokes.any { st -> st.points.all { abs(it.y - 90f) < 0.5f } })
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `selection clipPolygon strictly blocks painting and bucket fill outside selection boundary`() {
        val clipSquare = listOf(
            StrokePoint(40f, 40f),
            StrokePoint(80f, 40f),
            StrokePoint(80f, 80f),
            StrokePoint(40f, 80f)
        )

        // 1. Stroke drawing clipped to clipSquare
        val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val clippedStroke = DrawingStroke(
            tool = ToolType.PEN,
            color = 0xFF0000FFL,
            brushSize = 12f,
            opacity = 1.0f,
            points = listOf(StrokePoint(10f, 60f), StrokePoint(110f, 60f)),
            clipPolygon = clipSquare
        )
        KodanimaGraphicsEngine.drawStrokeToCanvas(clippedStroke, canvas)

        assertEquals("Outside-left pixel (20,60) must stay WHITE", Color.WHITE, bmp.getPixel(20, 60))
        assertEquals("Outside-right pixel (100,60) must stay WHITE", Color.WHITE, bmp.getPixel(100, 60))
        assertNotEquals("Inside-center pixel (60,60) must be painted", Color.WHITE, bmp.getPixel(60, 60))

        // 2. Flood fill confined inside selectionPoly
        val fillBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        fillBmp.eraseColor(Color.WHITE)
        val spans = KodanimaGraphicsEngine.createFloodFillSpans(
            bitmap = fillBmp,
            startX = 60,
            startY = 60,
            tolerance = 20,
            selectionPoly = clipSquare
        )
        assertTrue("Flood fill inside selection must emit spans", spans.isNotEmpty())
        assertTrue(
            "Every flood fill span coordinate must lie inside clipSquare [40..80]",
            spans.all { it.x in 39.9f..80.1f && it.y in 39.9f..80.1f }
        )

        // Flood fill attempt outside selection must return empty
        val blockedSpans = KodanimaGraphicsEngine.createFloodFillSpans(
            bitmap = fillBmp,
            startX = 10,
            startY = 10,
            tolerance = 20,
            selectionPoly = clipSquare
        )
        assertTrue("Flood fill started outside selection must produce no spans", blockedSpans.isEmpty())

        bmp.recycle()
        fillBmp.recycle()
    }
}
