package com.example

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.engine.FileImportExportManager
import com.example.engine.KodanimaGraphicsEngine
import com.example.engine.KritaBrushImporter
import com.example.engine.PreferencesStorageManager
import com.example.model.BrushDynamicParams
import com.example.model.BrushPreset
import com.example.model.DrawingStroke
import com.example.model.GestureAction
import com.example.model.GridRulerSettings
import com.example.model.KritaBrushEngineKind
import com.example.model.LayerBlendEffect
import com.example.model.MultiFingerGesture
import com.example.model.PerspectiveRulerSettings
import com.example.model.ProjectResolution
import com.example.model.StrokePoint
import com.example.model.ToolType
import com.example.model.TouchGestureRegistry
import com.example.viewmodel.KodanimaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioUpgradesTest {

    private fun buildSyntheticKppPngWithPresetXml(presetXml: String): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.DKGRAY)
        val rawPng = ByteArrayOutputStream().apply {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        bmp.recycle()

        // Insert a standard PNG tEXt chunk ("preset\0<xml>") right before IEND (last 12 bytes)
        val keywordAndValue = ("preset\u0000$presetXml").toByteArray(Charsets.UTF_8)
        val typeBytes = "tEXt".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(keywordAndValue)
        }.value.toInt()

        val chunkBuf = ByteBuffer.allocate(4 + 4 + keywordAndValue.size + 4).order(ByteOrder.BIG_ENDIAN)
        chunkBuf.putInt(keywordAndValue.size)
        chunkBuf.put(typeBytes)
        chunkBuf.put(keywordAndValue)
        chunkBuf.putInt(crc)

        val out = ByteArrayOutputStream(rawPng.size + chunkBuf.capacity())
        out.write(rawPng, 0, rawPng.size - 12)
        out.write(chunkBuf.array())
        out.write(rawPng, rawPng.size - 12, 12)
        return out.toByteArray()
    }

    private fun buildSyntheticGbrBytes(name: String, width: Int, height: Int, spacing: Int): ByteArray {
        val nameBytes = (name + "\u0000").toByteArray(Charsets.UTF_8)
        val headerSize = 28 + nameBytes.size
        val pixelBytes = ByteArray(width * height) { 128.toByte() }
        val buf = ByteBuffer.allocate(headerSize + pixelBytes.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(headerSize)
        buf.putInt(2) // GIMP v2
        buf.putInt(width)
        buf.putInt(height)
        buf.putInt(1) // 1 byte per pixel
        buf.put("GIMP".toByteArray(Charsets.US_ASCII))
        buf.putInt(spacing)
        buf.put(nameBytes)
        buf.put(pixelBytes)
        return buf.array()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `krita importer parses CDATA XML entities MaskGenerator and GBR and deduplicates safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val xml = """
            <![CDATA[<Preset name="G-Pen &amp; Nanquim" paintopid="ink">
                <MaskGenerator radius="15" softness="0.2" ratio="0.65"/>
                <param name="Spacing" value="0.06"/>
                <param name="FlowValue" value="0.95"/>
            </Preset>]]>
        """.trimIndent()

        val kppBytes = buildSyntheticKppPngWithPresetXml(xml)
        val parsed = KritaBrushImporter.parseKppBytes(kppBytes, "gpen.kpp", 1010)
        assertNotNull("Krita .kpp preset with CDATA & entities must parse cleanly", parsed)
        parsed!!
        assertEquals("G-Pen & Nanquim", parsed.namePt)
        assertEquals(KritaBrushEngineKind.INK_GPEN, parsed.engineKind)
        assertEquals(0.06f, parsed.dynamicParams.spacing, 0.01f)
        assertTrue("Diameter from MaskGenerator radius=15*2*0.65 (~19.5) should reflect parsed size", parsed.defaultSize in 15f..25f)

        // JSON serialization roundtrip for LAN BRUSH_DEF transfer
        val json = KritaBrushImporter.brushPresetToJson(parsed)
        val roundtripped = KritaBrushImporter.jsonToBrushPreset(json)
        assertNotNull(roundtripped)
        assertEquals(parsed.namePt, roundtripped!!.namePt)
        assertEquals(parsed.engineKind, roundtripped.engineKind)
        assertEquals(KritaBrushImporter.brushFingerprint(parsed), KritaBrushImporter.brushFingerprint(roundtripped))

        // .gbr parser verification
        val gbrBytes = buildSyntheticGbrBytes("Carvao Textura", 40, 40, 22)
        val gbrPreset = KritaBrushImporter.parseGbrBytes(gbrBytes, "carvao.gbr", 1011)
        assertNotNull("GIMP .gbr v2 brush tip must parse cleanly", gbrPreset)
        assertEquals("Carvao Textura", gbrPreset!!.namePt)
        assertEquals(0.22f, gbrPreset.dynamicParams.spacing, 0.01f)

        // Saving same fingerprint twice must NOT duplicate, while different fingerprint with same name adds " (2)"
        val (firstList, firstSaved) = KritaBrushImporter.saveSingleImportedBrush(context, parsed, emptyList())
        assertEquals(1, firstList.size)
        val (dupSameFpList, _) = KritaBrushImporter.saveSingleImportedBrush(context, parsed.copy(id = 9999), firstList)
        assertEquals("Identical brush fingerprint must not be duplicated", 1, dupSameFpList.size)

        val variantSameName = parsed.copy(
            id = 1012,
            engineKind = KritaBrushEngineKind.INK_ROUGH,
            dynamicParams = parsed.dynamicParams.copy(engineKind = KritaBrushEngineKind.INK_ROUGH, spacing = 0.25f)
        )
        val (renamedList, renamedSaved) = KritaBrushImporter.saveSingleImportedBrush(context, variantSameName, dupSameFpList)
        assertEquals(2, renamedList.size)
        assertEquals("${firstSaved.namePt} (2)", renamedSaved.namePt)
    }

    @Test
    fun `multi-finger touch gestures and autoPromptFriendBrushes persist and reset properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        val customMap = TouchGestureRegistry.defaultMap().toMutableMap()
        customMap[MultiFingerGesture.TWO_FINGER_TAP] = GestureAction.CLEAR_ACTIVE_LAYER
        customMap[MultiFingerGesture.FOUR_FINGER_TAP] = GestureAction.TOGGLE_GRID_RULER
        vm.updateTouchGestures(customMap)
        vm.setAutoPromptFriendBrushes(false)

        val reloaded = PreferencesStorageManager.loadPreferences(context)
        assertEquals(GestureAction.CLEAR_ACTIVE_LAYER, reloaded.touchGestures[MultiFingerGesture.TWO_FINGER_TAP])
        assertEquals(GestureAction.TOGGLE_GRID_RULER, reloaded.touchGestures[MultiFingerGesture.FOUR_FINGER_TAP])
        assertFalse(reloaded.autoPromptFriendBrushes)

        // Execute 4-finger tap mapped to TOGGLE_GRID_RULER
        assertFalse(vm.uiState.value.gridRulerSettings.isEnabled)
        vm.executeMultiFingerGesture(MultiFingerGesture.FOUR_FINGER_TAP)
        assertTrue(vm.uiState.value.gridRulerSettings.isEnabled)

        // Reset gestures to Procreate factory defaults
        val factory = vm.resetTouchGesturesToFactory()
        assertEquals(GestureAction.UNDO, factory[MultiFingerGesture.TWO_FINGER_TAP])
        assertEquals(GestureAction.TOGGLE_ZEN_FULLSCREEN, factory[MultiFingerGesture.FOUR_FINGER_TAP])
        assertEquals(GestureAction.UNDO, PreferencesStorageManager.loadPreferences(context).touchGestures[MultiFingerGesture.TWO_FINGER_TAP])

        vm.setAutoPromptFriendBrushes(true)
        assertTrue(PreferencesStorageManager.loadPreferences(context).autoPromptFriendBrushes)
    }

    @Test
    fun `manual FPS range, grid and perspective rulers, and friend brush offer accept or decline work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        // Manual FPS supports 1..120
        vm.loadNewEmptyProject("Projeto 48fps", ProjectResolution.PRESETS[0], 48)
        assertEquals(48, vm.uiState.value.project.fps)
        vm.setFps(120)
        assertEquals(120, vm.uiState.value.project.fps)
        vm.setFps(1)
        assertEquals(1, vm.uiState.value.project.fps)
        vm.setFps(0)
        assertEquals(1, vm.uiState.value.project.fps)

        // Grid & Perspective Rulers
        vm.updateGridRulerSettings(GridRulerSettings(isEnabled = true, cellSizePx = 64f, color = 0xFF00E676))
        assertTrue(vm.uiState.value.gridRulerSettings.isEnabled)
        assertEquals(64f, vm.uiState.value.gridRulerSettings.cellSizePx, 0.01f)
        assertEquals(0xFF00E676L, vm.uiState.value.gridRulerSettings.color)

        vm.updatePerspectiveRulerSettings(PerspectiveRulerSettings(isEnabled = true, vanishingPointsCount = 3, snapToGuide = true))
        vm.updatePerspectiveVanishingPoint(1, 0.92f, 0.44f)
        assertEquals(3, vm.uiState.value.perspectiveRulerSettings.vanishingPointsCount)
        assertEquals(0.92f, vm.uiState.value.perspectiveRulerSettings.vp2XRatio, 0.001f)
        assertEquals(0.44f, vm.uiState.value.perspectiveRulerSettings.vp2YRatio, 0.001f)

        // Simulate receiving a friend's custom imported brush via LAN multiplayer callback
        val friendBrush = BrushPreset(
            id = 2050,
            namePt = "Pincel Secreto do Amigo",
            shortLabel = "AmigoBrush",
            defaultSize = 14f,
            defaultOpacity = 0.85f,
            textureNamePt = "Krita",
            textureSymbol = "✒",
            descriptionPt = "Compartilhado via LAN",
            engineKind = KritaBrushEngineKind.BRISTLE_DRY,
            dynamicParams = BrushDynamicParams(engineKind = KritaBrushEngineKind.BRISTLE_DRY, grainAmount = 0.42f),
            isImportedKrita = true
        )
        vm.handleIncomingBrushDefinition("friend-99", "Lucas", friendBrush)
        val offer = vm.uiState.value.pendingFriendBrushOffer
        assertNotNull("Friend custom brush must trigger prompt banner when autoPromptFriendBrushes=true", offer)
        assertEquals("Lucas", offer!!.senderName)

        // Accepting adds brush to importedKritaBrushes and clears banner
        vm.acceptFriendBrushOffer(context)
        assertNull(vm.uiState.value.pendingFriendBrushOffer)
        assertTrue(vm.uiState.value.importedKritaBrushes.any { it.namePt == "Pincel Secreto do Amigo" })

        // Declining a second brush hides banner and remembers dismissal per peer/fingerprint
        val declinedBrush = friendBrush.copy(id = 2051, namePt = "Carvao Recusado", engineKind = KritaBrushEngineKind.CHARCOAL_SOFT)
        vm.handleIncomingBrushDefinition("friend-99", "Lucas", declinedBrush)
        assertNotNull(vm.uiState.value.pendingFriendBrushOffer)
        vm.declineFriendBrushOffer()
        assertNull(vm.uiState.value.pendingFriendBrushOffer)
        vm.handleIncomingBrushDefinition("friend-99", "Lucas", declinedBrush)
        assertNull("Declined brush must not keep nagging for same peer", vm.uiState.value.pendingFriendBrushOffer)

        // Setting autoPromptFriendBrushes=false suppresses new offers completely
        vm.setAutoPromptFriendBrushes(false)
        val silencedBrush = friendBrush.copy(id = 2052, namePt = "Airbrush Silenciado", engineKind = KritaBrushEngineKind.AIRBRUSH_SOFT)
        vm.handleIncomingBrushDefinition("friend-100", "Ana", silencedBrush)
        assertNull("Offer must stay suppressed when autoPromptFriendBrushes=false", vm.uiState.value.pendingFriendBrushOffer)
        vm.setAutoPromptFriendBrushes(true)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `krita bundle links gbr tip stamp, parses scatter and rotation, and renders without breaking native presets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gbrTipBytes = buildSyntheticGbrBytes("Carvao Tip", 28, 28, 18)
        val kppXml = """
            <Preset name="Carvao Bundle Tip" paintopid="paintbrush">
                <Brush type="gbr_brush" filename="carvao_tip.gbr" angle="24" spacing="0.14"/>
                <MaskGenerator radius="18" softness="0.35" ratio="0.8"/>
                <param name="Scatter" value="0.45"/>
                <param name="Rotation" value="0.35"/>
            </Preset>
        """.trimIndent()
        val kppBytes = buildSyntheticKppPngWithPresetXml(kppXml)

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("brushes/carvao_tip.gbr"))
            zos.write(gbrTipBytes)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("paintoppresets/carvao_bundle.kpp"))
            zos.write(kppBytes)
            zos.closeEntry()
        }

        val imported = KritaBrushImporter.parseBundleBytes(zipOut.toByteArray(), 3000)
        val linkedPreset = imported.firstOrNull { preset -> preset.namePt == "Carvao Bundle Tip" }
        assertNotNull("Preset inside .bundle must be imported", linkedPreset)
        linkedPreset!!
        assertFalse(
            "Linked .gbr brush tip stamp must populate tipStampBase64",
            linkedPreset.dynamicParams.tipStampBase64.isNullOrBlank()
        )
        assertTrue(linkedPreset.dynamicParams.scatter >= 0.4f)
        assertTrue(linkedPreset.dynamicParams.angularJitter >= 0.3f)

        // Ensure JSON roundtrip preserves tipStampBase64 for LAN transfer
        val json = KritaBrushImporter.brushPresetToJson(linkedPreset)
        val decoded = KritaBrushImporter.jsonToBrushPreset(json)
        assertNotNull(decoded)
        assertEquals(linkedPreset.dynamicParams.tipStampBase64, decoded!!.dynamicParams.tipStampBase64)

        // Ensure graphics engine renders non-empty pixels using linked tipStampBase64 and keeps all 12 native presets
        assertEquals(12, BrushPreset.ALL_12_PRESETS.size)
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = android.graphics.Canvas(bmp)
        KodanimaGraphicsEngine.drawStrokeToCanvas(
            stroke = DrawingStroke(
                tool = ToolType.BRUSH,
                color = 0xFFFF2244L,
                brushSize = 18f,
                opacity = 1.0f,
                brushPresetId = linkedPreset.id,
                customBrushParams = linkedPreset.dynamicParams.toCompactJson(),
                points = listOf(
                    StrokePoint(20f, 64f, 0.8f),
                    StrokePoint(64f, 64f, 1.0f),
                    StrokePoint(108f, 64f, 0.9f)
                )
            ),
            canvas = canvas
        )
        var nonTransparentCount = 0
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                if ((bmp.getPixel(x, y) ushr 24) > 0) nonTransparentCount++
            }
        }
        assertTrue("Custom .bundle brush tip must paint non-transparent pixels", nonTransparentCount > 40)
        bmp.recycle()
        assertNotNull(context)
    }

    @Test
    fun `perspective vanishing points individual colors, ruler opacity, and zen mode state update properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        // Grid ruler opacity clamped to [0.1f, 1.0f]
        vm.updateGridRulerSettings(GridRulerSettings(isEnabled = true, cellSizePx = 48f, color = 0xFF00D2FFL, opacity = 0.72f))
        assertEquals(0.72f, vm.uiState.value.gridRulerSettings.opacity, 0.001f)
        vm.updateGridRulerSettings(GridRulerSettings(isEnabled = true, opacity = 0.01f))
        assertEquals(0.10f, vm.uiState.value.gridRulerSettings.opacity, 0.001f)

        // Perspective ruler individual P1/P2/P3 colors & opacity
        vm.updatePerspectiveRulerSettings(
            PerspectiveRulerSettings(
                isEnabled = true,
                vanishingPointsCount = 3,
                vp1Color = 0xFFFF3366L,
                vp2Color = 0xFF00E676L,
                vp3Color = 0xFF7C4DFFL,
                opacity = 0.85f
            )
        )
        val persp = vm.uiState.value.perspectiveRulerSettings
        assertEquals(0xFFFF3366L, persp.vp1Color)
        assertEquals(0xFF00E676L, persp.vp2Color)
        assertEquals(0xFF7C4DFFL, persp.vp3Color)
        assertEquals(0.85f, persp.opacity, 0.001f)

        // Mobile Focus / Zen Mode toggle
        assertFalse(vm.uiState.value.isZenModeActive)
        vm.toggleZenMode()
        assertTrue(vm.uiState.value.isZenModeActive)
        vm.setZenModeActive(false)
        assertFalse(vm.uiState.value.isZenModeActive)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `per-frame localLayers stay isolated to active frame, survive JSON roundtrip, respond to F7, and composite clipping mask & multiply`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Subcamadas Teste", ProjectResolution.PRESETS[0], 24)
        vm.addFrame() // Now 2 frames: index 0 and index 1; currently at index 1
        vm.goToFrame(0)

        val initialTimelineLayersCountF0 = vm.uiState.value.project.frames[0].layers.size
        val initialTimelineLayersCountF1 = vm.uiState.value.project.frames[1].layers.size

        // Toggle panel with F7 physical shortcut
        assertFalse(vm.uiState.value.isFrameLayersPanelOpen)
        val handledF7 = vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F7), context)
        assertTrue("F7 shortcut must be handled for Camadas do Quadro", handledF7)
        assertTrue(vm.uiState.value.isFrameLayersPanelOpen)

        // Create two sub-layers in Frame 0 ONLY: Base and clipped Multiply overlay
        vm.addFrameLocalLayer("Base Sombra")
        assertTrue(vm.uiState.value.activeTargetIsFrameLocalLayer)
        assertEquals(1, vm.uiState.value.project.frames[0].localLayers.size)
        assertEquals(0, vm.uiState.value.project.frames[1].localLayers.size)
        assertEquals(initialTimelineLayersCountF0, vm.uiState.value.project.frames[0].layers.size)
        assertEquals(initialTimelineLayersCountF1, vm.uiState.value.project.frames[1].layers.size)

        // Paint opaque square on local layer 0 around center (100,100)
        vm.addStrokeToCurrentLayer(
            DrawingStroke(
                tool = ToolType.RECTANGLE,
                color = 0xFFFF0000L,
                brushSize = 4f,
                opacity = 1.0f,
                isFilled = true,
                points = listOf(StrokePoint(80f, 80f, 1f), StrokePoint(120f, 120f, 1f))
            )
        )

        // Add local layer 1 with MULTIPLY and Clipping Mask enabled
        vm.addFrameLocalLayer("Brilho Recorte")
        vm.setFrameLocalLayerBlendEffect(1, LayerBlendEffect.MULTIPLY)
        vm.toggleFrameLocalLayerClippingMask(1)
        vm.addStrokeToCurrentLayer(
            DrawingStroke(
                tool = ToolType.RECTANGLE,
                color = 0xFF0000FFL,
                brushSize = 4f,
                opacity = 1.0f,
                isFilled = true,
                points = listOf(StrokePoint(10f, 10f, 1f), StrokePoint(180f, 180f, 1f))
            )
        )

        val f0 = vm.uiState.value.project.frames[0]
        assertEquals(2, f0.localLayers.size)
        assertEquals(LayerBlendEffect.MULTIPLY, f0.localLayers[1].blendEffect)
        assertTrue(f0.localLayers[1].isClippingMask)
        assertEquals(1, f0.localLayers[0].strokes.size)
        assertEquals(1, f0.localLayers[1].strokes.size)
        // Frame 1 timeline tracks and localLayers remain untouched!
        assertEquals(0, vm.uiState.value.project.frames[1].localLayers.size)

        // Verify JSON serialization roundtrip preserves localLayers, blendEffect & isClippingMask
        val json = FileImportExportManager.projectToJson(vm.uiState.value.project, compact = true)
        val deserialized = FileImportExportManager.projectFromJson(json)
        val df0 = deserialized.frames[0]
        assertEquals(2, df0.localLayers.size)
        assertEquals(LayerBlendEffect.MULTIPLY, df0.localLayers[1].blendEffect)
        assertTrue(df0.localLayers[1].isClippingMask)

        // Verify Clipping Mask + Multiply pixel output on Frame 0 (transparent background)
        val outBmp = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = f0,
            width = 200,
            height = 200,
            backgroundColor = android.graphics.Color.TRANSPARENT
        )
        val outsideAlpha = (outBmp.getPixel(25, 25) ushr 24) and 0xFF
        val insidePixel = outBmp.getPixel(100, 100)
        val insideAlpha = (insidePixel ushr 24) and 0xFF
        assertEquals("Pixels outside base layer must be clipped away (alpha=0)", 0, outsideAlpha)
        assertTrue("Pixels inside base layer must be visible", insideAlpha > 200)
        outBmp.recycle()

        // Rename & delete test
        vm.renameFrameLocalLayer(0, "Base Renomeada")
        assertEquals("Base Renomeada", vm.uiState.value.project.frames[0].localLayers[0].name)
        vm.deleteFrameLocalLayer(1)
        assertEquals(1, vm.uiState.value.project.frames[0].localLayers.size)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `dual color, inner dots, dual shape json roundtrip, pixel rendering and Krita MaskingBrush import work`() {
        val params = BrushDynamicParams(
            engineKind = KritaBrushEngineKind.PIXEL_ROUND_SIZE,
            hardness = 0.9f,
            spacing = 0.12f,
            dualColorEnabled = true,
            secondaryColor = 0xFF00E676L,
            secondaryColorMix = 0.7f,
            dualColorMode = "edge",
            innerDotsEnabled = true,
            innerDotsDensity = 0.85f,
            innerDotsSize = 0.25f,
            innerDotsUseSecondaryColor = true,
            dualShapeEnabled = true,
            secondaryShapeKind = "rect",
            dualShapeScale = 0.75f,
            dualShapeBlendMode = "combine"
        )
        assertTrue(params.hasCustomKritaShapeOrTip())
        val compact = params.toCompactJson()
        val decoded = BrushDynamicParams.fromJsonOrNull(compact)
        assertNotNull(decoded)
        decoded!!
        assertTrue(decoded.dualColorEnabled)
        assertEquals(0xFF00E676L, decoded.secondaryColor)
        assertEquals("edge", decoded.dualColorMode)
        assertTrue(decoded.innerDotsEnabled)
        assertTrue(decoded.dualShapeEnabled)
        assertEquals("rect", decoded.secondaryShapeKind)
        assertEquals("combine", decoded.dualShapeBlendMode)

        // Render stroke with dual-color + inner-dots + dual-shape and verify secondary green channel exists in painted pixels
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = android.graphics.Canvas(bmp)
        KodanimaGraphicsEngine.drawStrokeToCanvas(
            stroke = DrawingStroke(
                tool = ToolType.BRUSH,
                color = 0xFFFF2244L,
                brushSize = 26f,
                opacity = 1.0f,
                brushPresetId = 1,
                customBrushParams = compact,
                points = listOf(
                    StrokePoint(24f, 64f, 1.0f),
                    StrokePoint(64f, 64f, 1.0f),
                    StrokePoint(104f, 64f, 1.0f)
                )
            ),
            canvas = canvas
        )
        var nonTransparentCount = 0
        var greenishPixelCount = 0
        for (y in 0 until 128) {
            for (x in 0 until 128) {
                val px = bmp.getPixel(x, y)
                val a = (px ushr 24) and 0xFF
                if (a > 20) {
                    nonTransparentCount++
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    if (g > r && g > 120) greenishPixelCount++
                }
            }
        }
        assertTrue("Dual-shape stroke should paint sizable region", nonTransparentCount > 150)
        assertTrue("Dual-color / inner-dots should produce secondary green accents", greenishPixelCount > 0)
        bmp.recycle()

        // Krita MaskingBrush + dual color + spray/particle XML import check
        val kppXml = """
            <Preset name="Dual Shape Ink" paintopid="paintbrush">
                <MaskGenerator radius="12" softness="0.6" ratio="1.0"/>
                <param name="MaskingBrush/Enabled" value="true"/>
                <param name="MaskingBrush/CompositeOp" value="multiply"/>
                <param name="MaskingBrush/MasterSize" value="0.65"/>
                <param name="MaskingBrush/brush_definition"><![CDATA[<Brush type="rect" radius="8"/>]]></param>
                <param name="PaintOpAction" value="2"/>
                <param name="ColorSource/Type" value="gradient"/>
                <param name="sprayArea/particleCount" value="14"/>
            </Preset>
        """.trimIndent()
        val kppBytes = buildSyntheticKppPngWithPresetXml(kppXml)
        val parsed = KritaBrushImporter.parseKppBytes(kppBytes, "dual.kpp", 4001)
        assertNotNull(parsed)
        parsed!!
        assertTrue("MaskingBrush enabled must set dualShapeEnabled", parsed.dynamicParams.dualShapeEnabled)
        assertEquals("mask", parsed.dynamicParams.dualShapeBlendMode)
        assertTrue("Gradient ColorSource must enable dualColorEnabled", parsed.dynamicParams.dualColorEnabled)
        assertTrue("Spray/particle count must enable innerDotsEnabled", parsed.dynamicParams.innerDotsEnabled)
    }

    @Test
    fun `frame local layer target persists across frame navigation and locked timeline layer blocks phantom strokes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("LocalPersist", ProjectResolution.PRESETS[0], 24)
        vm.addFrame() // Frame 0 & 1
        vm.goToFrame(0)

        vm.addFrameLocalLayer("Esboço Local F0")
        assertTrue(vm.uiState.value.activeTargetIsFrameLocalLayer)
        assertEquals(0, vm.uiState.value.activeLocalLayerIndex)

        // Switch to Frame 1 (which has no localLayers): target falls back safely to timeline layer
        vm.goToFrame(1)
        assertFalse(vm.uiState.value.activeTargetIsFrameLocalLayer)

        // Return to Frame 0: localLayers target must be automatically restored!
        vm.goToFrame(0)
        assertTrue(
            "Returning to Frame 0 must restore previous Camadas do Quadro selection",
            vm.uiState.value.activeTargetIsFrameLocalLayer
        )
        assertEquals(0, vm.uiState.value.activeLocalLayerIndex)

        // Lock timeline layer and verify strokes are NOT added to locked layer
        vm.useTimelineLayerTarget()
        vm.toggleLayerLock(0)
        val beforeCount = vm.uiState.value.project.frames[0].layers[0].strokes.size
        vm.addStrokeToCurrentLayer(
            DrawingStroke(
                tool = ToolType.PEN,
                color = 0xFF000000L,
                brushSize = 6f,
                opacity = 1.0f,
                points = listOf(StrokePoint(10f, 10f, 1f), StrokePoint(40f, 40f, 1f))
            )
        )
        assertEquals(
            "Locked layer must reject strokes",
            beforeCount,
            vm.uiState.value.project.frames[0].layers[0].strokes.size
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `non-dissolving alpha compositing yields uniform stroke opacity when semi-transparent`() {
        val stroke = DrawingStroke(
            tool = ToolType.PEN,
            color = 0xFFFF0000L,
            brushSize = 18f,
            opacity = 0.5f,
            points = listOf(
                StrokePoint(30f, 64f, 1.0f),
                StrokePoint(64f, 64f, 1.0f),
                StrokePoint(98f, 64f, 1.0f),
                StrokePoint(64f, 64f, 1.0f) // self-overlap over center
            )
        )
        val frame = com.example.model.Frame(
            layers = listOf(
                com.example.model.Layer(
                    name = "Translúcida",
                    opacity = 0.8f,
                    strokes = listOf(stroke)
                )
            )
        )
        val outBmp = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = frame,
            width = 128,
            height = 128,
            backgroundColor = android.graphics.Color.TRANSPARENT
        )
        val alphaLeft = (outBmp.getPixel(36, 64) ushr 24) and 0xFF
        val alphaOverlapCenter = (outBmp.getPixel(64, 64) ushr 24) and 0xFF
        // Expected composite alpha ~ 0.5 * 0.8 * 255 = 102, WITHOUT double-darkening overlap (>150)!
        assertTrue("Semi-transparent stroke should be visible", alphaLeft in 60..145)
        assertTrue(
            "Self-overlapping stroke center ($alphaOverlapCenter) must match non-overlapping interior ($alphaLeft) without blotchy compounding",
            kotlin.math.abs(alphaOverlapCenter - alphaLeft) <= 15
        )
        outBmp.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `reference image choice tray creates exportable timeline layer or non-exportable floating window, plus perspective z-order and onion skin clamping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imgFile = java.io.File(context.cacheDir, "test_ref_sample.png")
        val sampleBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        sampleBmp.eraseColor(android.graphics.Color.BLUE)
        java.io.FileOutputStream(imgFile).use { fos ->
            sampleBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        sampleBmp.recycle()

        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("RefTest", ProjectResolution.PRESETS[0], 24)
        vm.addFrame() // 2 frames
        vm.goToFrame(0)

        // 1. Picking an image sets pendingReferenceImageUri to show Choice Dialog
        vm.setReferenceImageUri(imgFile.absolutePath)
        assertEquals(imgFile.absolutePath, vm.uiState.value.pendingReferenceImageUri)

        // 2. Choose "Camada normal da Timeline" -> added across ALL frames & rendered in renderFrameToBitmap
        vm.applyPickedImageAsTimelineLayer(context, imgFile.absolutePath)
        assertNull(vm.uiState.value.pendingReferenceImageUri)
        val layersF0 = vm.uiState.value.project.frames[0].layers
        val layersF1 = vm.uiState.value.project.frames[1].layers
        assertEquals(2, layersF0.size)
        assertEquals(2, layersF1.size)
        assertNotNull(layersF0[1].imageAttachment)
        assertNotNull(layersF1[1].imageAttachment)

        vm.updateLayerImageTransform(1, scalePercent = 1.25f, rotationDegrees = 15f)
        vm.setLayerImageOpacity(1, 0.7f)
        assertEquals(1.25f, vm.uiState.value.project.frames[0].layers[1].imageAttachment!!.scalePercent, 0.01f)
        assertEquals(0.7f, vm.uiState.value.project.frames[0].layers[1].imageAttachment!!.opacity, 0.01f)

        val renderedWithImgLayer = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = vm.uiState.value.project.frames[0],
            width = 128,
            height = 128,
            backgroundColor = android.graphics.Color.TRANSPARENT
        )
        val centerAlpha = (renderedWithImgLayer.getPixel(64, 64) ushr 24) and 0xFF
        assertTrue("Timeline image layer must render into exported/frame bitmap", centerAlpha > 100)
        renderedWithImgLayer.recycle()

        // 3. Choose "Janela flutuante" -> excluded from export bitmap, supports lock, Z-order & opacity
        vm.deleteLayer(1) // remove timeline image layer so frame is clean
        vm.setReferenceImageUri(imgFile.absolutePath)
        vm.applyPickedImageAsFloatingWindow(context, imgFile.absolutePath)
        assertTrue(vm.uiState.value.referenceImage.isVisible)
        assertFalse(vm.uiState.value.referenceImage.isLocked)
        assertTrue(vm.uiState.value.referenceImage.renderAboveCanvas)

        vm.toggleFloatingReferenceZOrder()
        assertFalse(vm.uiState.value.referenceImage.renderAboveCanvas)
        vm.toggleFloatingReferenceLock()
        assertTrue(vm.uiState.value.referenceImage.isLocked)
        vm.updateFloatingReferenceOffset(100f, 100f)
        assertEquals("Locked floating window must ignore drag", 0f, vm.uiState.value.referenceImage.offsetX, 0.01f)
        vm.setFloatingReferenceOpacity(0.45f)
        assertEquals(0.45f, vm.uiState.value.referenceImage.opacity, 0.01f)

        val renderedWithFloatingRef = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = vm.uiState.value.project.frames[0],
            width = 128,
            height = 128,
            backgroundColor = android.graphics.Color.TRANSPARENT
        )
        assertEquals(
            "Floating reference window must NEVER be baked into exported frame bitmap",
            0,
            (renderedWithFloatingRef.getPixel(64, 64) ushr 24) and 0xFF
        )
        renderedWithFloatingRef.recycle()

        vm.clearReferenceImage()
        assertFalse(vm.uiState.value.referenceImage.isVisible)

        // 4. Perspective Z-order & Onion Skin opacity clamping (0.05..1.0)
        vm.updatePerspectiveRulerSettings(
            PerspectiveRulerSettings(isEnabled = true, renderBehindActiveLayer = true)
        )
        assertTrue(vm.uiState.value.perspectiveRulerSettings.renderBehindActiveLayer)

        vm.setOnionSkinOpacity(0.01f)
        assertEquals(0.05f, vm.uiState.value.project.onionSkin.opacity, 0.001f)
        vm.setOnionSkinOpacity(0.62f)
        assertEquals(0.62f, vm.uiState.value.project.onionSkin.opacity, 0.001f)

        imgFile.delete()
    }

    @Test
    fun `custom resolutions save, clamp, deduplicate, persist, delete, and normalize image attachment scales`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        // 1. Clamp bounds [128..4096], auto-name blank label, and persist
        val clamped = vm.saveCustomResolution("", width = 50, height = 9000)
        assertEquals(128, clamped.width)
        assertEquals(4096, clamped.height)
        assertEquals("Personalizada 128x4096", clamped.name)
        assertTrue(clamped.isCustom)

        // 2. Custom resolution named & persisted across fresh ViewModel
        val widescreen = vm.saveCustomResolution("Cinema 2.39:1", width = 2560, height = 1072)
        assertEquals("Cinema 2.39:1", widescreen.name)
        assertEquals(2560, widescreen.width)
        assertEquals(1072, widescreen.height)

        // Saving same dimensions and name (case-insensitive) deduplicates instead of duplicating entry
        val renamed = vm.saveCustomResolution("cinema 2.39:1", width = 2560, height = 1072)
        val matches = vm.uiState.value.preferences.savedCustomResolutions.filter { it.width == 2560 && it.height == 1072 }
        assertEquals(1, matches.size)
        assertEquals("cinema 2.39:1", renamed.name)

        val freshVm = KodanimaViewModel()
        freshVm.initializeWithContext(context)
        assertTrue(
            "Saved custom resolution must survive restart via PreferencesStorageManager",
            freshVm.uiState.value.preferences.savedCustomResolutions.any {
                it.width == 2560 && it.height == 1072 && it.name == "cinema 2.39:1" && it.isCustom
            }
        )

        // 3. Create project using custom resolution & verify lastActiveFrameIndex JSON roundtrip
        vm.loadNewEmptyProject("LocalSubTest", ProjectResolution.PRESETS[0], 24)
        vm.addFrameLocalLayer("Temporária")
        assertTrue(vm.uiState.value.activeTargetIsFrameLocalLayer)

        vm.createAndOpenNewProject(context, "Curta Cinema", widescreen, 24)
        assertFalse("Creating new project must reset local-layer target", vm.uiState.value.activeTargetIsFrameLocalLayer)
        assertFalse(vm.uiState.value.isFrameLayersPanelOpen)
        assertEquals(2560, vm.uiState.value.project.resolution.width)
        assertEquals(1072, vm.uiState.value.project.resolution.height)
        assertTrue(vm.uiState.value.project.resolution.isCustom)

        vm.addFrame()
        vm.addFrame()
        vm.goToFrame(2)
        val projectSnapshot = vm.uiState.value.project.copy(lastActiveFrameIndex = 2)
        val json = FileImportExportManager.projectToJson(projectSnapshot, compact = true)
        val restored = FileImportExportManager.projectFromJson(json)
        assertEquals(2, restored.lastActiveFrameIndex)
        assertTrue(restored.resolution.isCustom)
        assertEquals(2560, restored.resolution.width)
        assertEquals(1072, restored.resolution.height)

        // 4. Delete custom resolution cleanly
        vm.deleteCustomResolution(clamped)
        vm.deleteCustomResolution(renamed)
        assertFalse(
            PreferencesStorageManager.loadPreferences(context).savedCustomResolutions.any {
                it.width == 2560 && it.height == 1072
            }
        )

        // 5. LayerImageAttachment default & legacy scalePercent normalization
        val defaultAtt = com.example.model.LayerImageAttachment()
        assertEquals(1.0f, defaultAtt.scalePercent, 0.001f)
        assertEquals(1.0f, defaultAtt.normalizedScaleFactor(), 0.001f)

        val legacyAtt = com.example.model.LayerImageAttachment(uriString = "file://a.png", scalePercent = 250f)
        assertEquals(2.5f, legacyAtt.normalizedScaleFactor(), 0.001f)

        val legacyProject = projectSnapshot.copy(
            frames = listOf(
                com.example.model.Frame(
                    layers = listOf(
                        com.example.model.Layer(
                            name = "Imagem Legado",
                            imageAttachment = legacyAtt
                        )
                    )
                )
            )
        )
        val decodedLegacy = FileImportExportManager.projectFromJson(
            FileImportExportManager.projectToJson(legacyProject, compact = true)
        )
        assertEquals(
            "Deserialized legacy 250f image scale must normalize to 2.5f instead of 10x distortion",
            2.5f,
            decodedLegacy.frames[0].layers[0].imageAttachment!!.scalePercent,
            0.01f
        )
    }
}


