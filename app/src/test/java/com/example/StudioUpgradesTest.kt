package com.example

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.engine.BuiltIn3DPrimitive
import com.example.engine.Camera3DEngine
import com.example.engine.FileImportExportManager
import com.example.engine.KodanimaGraphicsEngine
import com.example.engine.KritaBrushImporter
import com.example.engine.Mesh3DImporter
import com.example.engine.MultiplayerStudioEngine
import com.example.engine.PreferencesStorageManager
import com.example.engine.SelectionGeometryHelper
import com.example.engine.Studio3DPaintEngine
import com.example.engine.TimelineTrackEngine
import com.example.model.AnimationProject
import com.example.model.BucketFillConfig
import com.example.model.BucketReferenceMode
import com.example.model.BrushDynamicParams
import com.example.model.BrushPreset
import com.example.model.Camera3DKeyframe
import com.example.model.Camera3DSettings
import com.example.model.CameraBuiltInEffect
import com.example.model.CameraEasingCurve
import com.example.model.CameraEffectSpan
import com.example.model.CameraMode
import com.example.model.CameraPostEffectSettings
import com.example.model.CameraToolAction
import com.example.model.CameraViewportNavTarget
import com.example.model.Canvas3DBackgroundRenderMode
import com.example.model.CanvasBackgroundSettings
import com.example.model.CanvasBackgroundStyle
import com.example.model.CanvasTexturePattern
import com.example.model.ColorPaletteConfig
import com.example.model.DrawingStroke
import com.example.model.Estudio3DPaintTool
import com.example.viewmodel.EditorDialog
import com.example.model.Frame
import com.example.model.GestureAction
import com.example.model.GradientColorStop
import com.example.model.GridRulerSettings
import com.example.model.KritaBrushEngineKind
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.model.MultiFingerGesture
import com.example.model.ObjectLightSettings
import com.example.model.ObjectOutlineSettings
import com.example.model.ObjectShaderMode
import com.example.model.ObjectTexturePaintLayer
import com.example.model.ObjectTransformKeyframe
import com.example.model.ObjectUVMapSlot
import com.example.model.PerspectiveRulerSettings
import com.example.model.ProjectResolution
import com.example.model.SceneObject3D
import com.example.model.ShaderNodeGraph
import com.example.model.ShortcutAction
import com.example.model.ShortcutRegistry
import com.example.model.SkyBackdropMode
import com.example.model.StageSelection
import com.example.model.StageTargetType
import com.example.model.StrokePoint
import com.example.model.ToolType
import com.example.model.TouchGestureRegistry
import com.example.model.UvUnwrapProjection
import com.example.ui.components.computeStage3DZPositions
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
        assertTrue("Diameter from MaskGenerator radius=15*2 (~30px) should reflect parsed size", parsed.defaultSize in 25f..35f)

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

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun `inverted selection clips strokes outside polygon, live drawing keeps selection active, and independent eraser size & layer reorder work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("SelecaoInvertida", ProjectResolution.PRESETS[0], 24)

        // Independent brush vs eraser sizes
        vm.setBrushSize(18f)
        vm.setEraserSize(42f)
        assertEquals(18f, vm.uiState.value.brushSize, 0.01f)
        assertEquals(42f, vm.uiState.value.eraserSize, 0.01f)

        // Layer reordering: add top layer then move down / up
        vm.addLayer()
        val initialTopName = vm.uiState.value.project.frames[0].layers.last().name
        vm.moveLayerDown(1)
        assertEquals(initialTopName, vm.uiState.value.project.frames[0].layers[0].name)
        vm.moveLayerUp(0)
        assertEquals(initialTopName, vm.uiState.value.project.frames[0].layers[1].name)
        vm.setActiveLayer(0)

        // Lasso polygon centered at (100,100)..(200,200)
        val poly = listOf(
            StrokePoint(100f, 100f, 1f),
            StrokePoint(200f, 100f, 1f),
            StrokePoint(200f, 200f, 1f),
            StrokePoint(100f, 200f, 1f)
        )
        vm.setLassoSelection(poly)
        assertTrue(vm.uiState.value.activeSelection.isActive)
        assertFalse(vm.uiState.value.activeSelection.isInverted)

        // Normal (non-inverted) selection stamps clipPolygon with clipInverted=false and keeps activeSelection active
        vm.addStrokeToCurrentLayer(
            DrawingStroke(
                tool = ToolType.LINE,
                color = 0xFFFF0000L,
                brushSize = 20f,
                opacity = 1f,
                points = listOf(
                    StrokePoint(50f, 150f, 1f),  // outside
                    StrokePoint(180f, 150f, 1f)  // crosses into inside
                )
            )
        )
        assertTrue("Selection must stay active while drawing inside it", vm.uiState.value.activeSelection.isActive)
        val normalStrokes = vm.uiState.value.project.frames[0].layers[0].strokes
        assertTrue("Clipped stroke must exist on layer", normalStrokes.isNotEmpty())
        assertNotNull("Stroke must carry selection clipPolygon", normalStrokes.last().clipPolygon)
        assertFalse("Normal selection must set clipInverted=false", normalStrokes.last().clipInverted)

        val normalBmp = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = vm.uiState.value.project.frames[0],
            width = 300,
            height = 300,
            backgroundColor = android.graphics.Color.TRANSPARENT
        )
        assertEquals(
            "Normal selection must block paint outside polygon (50,150)",
            0,
            (normalBmp.getPixel(50, 150) ushr 24) and 0xFF
        )
        assertTrue(
            "Normal selection must allow paint inside polygon (150,150)",
            ((normalBmp.getPixel(150, 150) ushr 24) and 0xFF) > 200
        )
        normalBmp.recycle()

        // Invert selection -> now inside (150,150) is protected and outside (50,150) is editable
        vm.invertSelection()
        assertTrue(vm.uiState.value.activeSelection.isInverted)
        vm.addStrokeToCurrentLayer(
            DrawingStroke(
                tool = ToolType.LINE,
                color = 0xFF00FF00L,
                brushSize = 20f,
                opacity = 1f,
                points = listOf(
                    StrokePoint(50f, 150f, 1f),  // outside -> allowed!
                    StrokePoint(180f, 150f, 1f)  // inside -> blocked!
                )
            )
        )
        assertTrue("Inverted selection must remain active after stroke", vm.uiState.value.activeSelection.isActive)
        val afterInvertedStrokes = vm.uiState.value.project.frames[0].layers[0].strokes
        val greenStroke = afterInvertedStrokes.lastOrNull { it.color == 0xFF00FF00L }
        assertNotNull("Inverted selection must commit outside portion of stroke", greenStroke)
        assertTrue("Inverted stroke must set clipInverted=true", greenStroke!!.clipInverted)

        val invertedBmp = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = vm.uiState.value.project.frames[0],
            width = 300,
            height = 300,
            backgroundColor = android.graphics.Color.TRANSPARENT
        )
        val outPixel = invertedBmp.getPixel(50, 150)
        val inPixel = invertedBmp.getPixel(150, 150)
        assertTrue(
            "Inverted selection must paint green outside polygon (50,150)",
            android.graphics.Color.green(outPixel) > 200 && android.graphics.Color.red(outPixel) < 50
        )
        assertTrue(
            "Inverted selection must protect red interior (150,150) from green stroke",
            android.graphics.Color.red(inPixel) > 200 && android.graphics.Color.green(inPixel) < 50
        )
        invertedBmp.recycle()
    }

    @Test
    fun `color palette tray grid config, theory dice, instant color wheel & grid ruler behind-layer flag work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)

        // Instant color preview without polluting recents until dismiss
        val recentsBefore = vm.uiState.value.recentColors.size
        vm.setColor(0xFF123456L, recordToRecents = false)
        assertEquals(0xFF123456L, vm.uiState.value.currentColor)
        assertEquals(recentsBefore, vm.uiState.value.recentColors.size)
        vm.recordRecentColor(0xFF123456L)
        assertEquals(0xFF123456L, vm.uiState.value.recentColors.first())

        // Color Palette Tray open/grid/save/remove/dice
        vm.openPaletteTray()
        assertTrue(vm.uiState.value.isPaletteTrayOpen)
        vm.setPaletteGridSize(cols = 6, rows = 3)
        assertEquals(18, vm.uiState.value.preferences.colorPalette.maxSlots)

        vm.selectPaletteSlot(2)
        vm.setColor(0xFFE91E63L, recordToRecents = false)
        vm.saveCurrentColorToPaletteSlot(2)
        assertEquals(0xFFE91E63L, vm.uiState.value.preferences.colorPalette.swatches[2])

        val rule = vm.generateColorTheoryPaletteRow()
        assertTrue(rule.isNotBlank())
        val row0Colors = vm.uiState.value.preferences.colorPalette.swatches.take(6)
        assertTrue("Color theory dice must populate entire horizontal row", row0Colors.all { it != 0L })

        vm.removePaletteSlot(2)
        assertEquals(0L, vm.uiState.value.preferences.colorPalette.swatches[2])
        vm.closePaletteTray()
        assertFalse(vm.uiState.value.isPaletteTrayOpen)

        // Grid ruler behind active layer default & toggle
        val defaultGrid = GridRulerSettings()
        assertTrue("Grid ruler should default behind active layer", defaultGrid.renderBehindActiveLayer)
        vm.updateGridRulerSettings(defaultGrid.copy(isEnabled = true, renderBehindActiveLayer = false))
        assertFalse(vm.uiState.value.gridRulerSettings.renderBehindActiveLayer)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `3D camera interpolation, built-in effects, animated preset folder & post-effects render reliably`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Camera3DProj", ProjectResolution.PRESETS[0], 24)
        vm.addFrame()
        vm.addFrame()
        vm.addFrame() // 4 frames (0..3)

        // Built-in ZOOM_IN generates keyframes smoothly across frame range
        vm.applyCameraBuiltInEffect(CameraBuiltInEffect.ZOOM_IN)
        val cam = vm.uiState.value.project.camera3D
        assertTrue(cam.isEnabled)
        assertTrue(cam.autoInterpolate)
        val kf0 = Camera3DEngine.evaluateCameraAtFrame(cam, 0)
        val kfMid = Camera3DEngine.evaluateCameraAtFrame(cam, 1)
        val kfEnd = Camera3DEngine.evaluateCameraAtFrame(cam, 3)
        assertEquals(1.0f, kf0.zoom, 0.01f)
        assertTrue("Intermediate frame must interpolate smoothly between 1.0 and 1.48", kfMid.zoom in 1.05f..1.45f)
        assertEquals(1.48f, kfEnd.zoom, 0.01f)

        // Save camera configuration + After Effects post-effects to persistent Pasta Animada
        val postFx = CameraPostEffectSettings(vignette = 0.6f, bloom = 0.35f, sepia = 0.25f)
        vm.updateCameraPostEffects(postFx)
        vm.setCameraResolution(1280, 720)
        vm.saveCameraTemplateToFolder("Zoom Drama")
        val savedList = vm.uiState.value.preferences.savedCameraTemplates
        assertEquals(1, savedList.size)
        val savedTpl = savedList.first()
        assertEquals("Zoom Drama", savedTpl.name)
        assertEquals(1280, savedTpl.captureWidth)
        assertEquals(0.6f, savedTpl.postEffects.vignette, 0.01f)

        // Reset camera then re-apply saved template from Pasta Animada
        vm.resetCameraKeyframesToDefault()
        vm.resetCameraPostEffects()
        assertEquals(0f, vm.uiState.value.project.camera3D.postEffects.vignette, 0.001f)
        vm.applyCameraTemplateFromFolder(savedTpl)
        assertEquals(0.6f, vm.uiState.value.project.camera3D.postEffects.vignette, 0.01f)
        assertEquals(1280, vm.uiState.value.project.camera3D.captureWidth)

        // Render a frame with 3D camera + post-effects onto a target Bitmap
        val outBmp = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        Camera3DEngine.renderFrameWithCameraAndEffects(
            frame = vm.uiState.value.project.frames[1],
            settings = vm.uiState.value.project.camera3D,
            frameIndex = 1,
            projectWidth = 200,
            projectHeight = 120,
            targetBitmap = outBmp
        )
        // Corner pixel should be darkened noticeably by vignette=0.6f
        val centerPixel = outBmp.getPixel(80, 45)
        val cornerPixel = outBmp.getPixel(2, 2)
        val centerLuma = android.graphics.Color.red(centerPixel) + android.graphics.Color.green(centerPixel) + android.graphics.Color.blue(centerPixel)
        val cornerLuma = android.graphics.Color.red(cornerPixel) + android.graphics.Color.green(cornerPixel) + android.graphics.Color.blue(cornerPixel)
        assertTrue("After Effects vignette must darken corners ($cornerLuma) relative to center ($centerLuma)", cornerLuma < centerLuma)
        outBmp.recycle()

        vm.deleteCameraTemplate(savedTpl.id)
        assertTrue(vm.uiState.value.preferences.savedCameraTemplates.isEmpty())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `grid ruler line count and thickness clamp, and camera 2D mode, bezier handles, effect spans & export crop flag work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Camera2D3D", ProjectResolution.PRESETS[0], 24)
        vm.addFrame()
        vm.addFrame()
        vm.addFrame() // 4 frames

        // 1. GridRuler line count & thickness bounds
        vm.updateGridRulerSettings(
            GridRulerSettings(
                isEnabled = true,
                useLineCount = true,
                lineCountX = 1,
                lineCountY = 99,
                lineThicknessDp = 12.0f
            )
        )
        val grid = vm.uiState.value.gridRulerSettings
        assertTrue(grid.useLineCount)
        assertEquals(2, grid.lineCountX)
        assertEquals(64, grid.lineCountY)
        assertEquals(6.0f, grid.lineThicknessDp, 0.001f)

        // 2. Bezier Graph Solver & Keyframe handles
        val slowStartY = Camera3DEngine.solveCubicBezierY(0.25f, p1x = 0.8f, p1y = 0.05f, p2x = 0.9f, p2y = 0.2f)
        val fastStartY = Camera3DEngine.solveCubicBezierY(0.25f, p1x = 0.1f, p1y = 0.85f, p2x = 0.2f, p2y = 0.95f)
        assertTrue("Steep initial Bezier ($fastStartY) must exceed flat initial Bezier ($slowStartY)", fastStartY > slowStartY + 0.2f)

        vm.updateKeyframeBezierHandles(frameIndex = 0, p1x = 0.15f, p1y = 0.9f, p2x = 0.3f, p2y = 1.0f)
        val kf0Updated = vm.uiState.value.project.camera3D.keyframes.first { it.frameIndex == 0 }
        assertEquals(CameraEasingCurve.BEZIER_GRAFICO, kf0Updated.easing)
        assertEquals(0.15f, kf0Updated.bezierP1X, 0.001f)
        assertEquals(0.9f, kf0Updated.bezierP1Y, 0.001f)

        // 3. Ranged CameraEffectSpan (frames 1..3) applies only in range
        vm.applyCameraEffectInRange(CameraBuiltInEffect.ZOOM_IN, startFrame = 1, endFrame = 3, intensity = 1.2f)
        val spans = vm.uiState.value.project.camera3D.effectSpans
        assertEquals(1, spans.size)
        val camBeforeRange = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 0)
        val camAtRangeEnd = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 3)
        assertTrue("Ranged ZOOM_IN should boost zoom at endFrame ($camAtRangeEnd) vs frame 0 ($camBeforeRange)", camAtRangeEnd.zoom > camBeforeRange.zoom + 0.2f)

        vm.removeCameraEffectSpan(spans.first().id)
        assertTrue(vm.uiState.value.project.camera3D.effectSpans.isEmpty())

        // 4. CameraMode.MODE_2D zeroes 3D pitch & yaw cleanly while keeping 2D zoom/pan/rotate
        vm.applyCameraBuiltInEffect(CameraBuiltInEffect.ORBITA_3D)
        val cam3DVal = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 0)
        assertTrue("ORBITA_3D in MODE_3D should have non-zero yaw", kotlin.math.abs(cam3DVal.yawY) > 1f)
        vm.setCameraMode(CameraMode.MODE_2D)
        val cam2DVal = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 0)
        assertEquals(0f, cam2DVal.pitchX, 0.001f)
        assertEquals(0f, cam2DVal.yawY, 0.001f)
        assertFalse(vm.uiState.value.isCameraSideView3D)

        // 5. Export camera crop toggle & cropToCamera=false preserves full canvas
        vm.setExportCameraCropOnly(false)
        assertFalse(vm.uiState.value.exportCameraCropOnly)
        vm.setExportCameraCropOnly(true)
        assertTrue(vm.uiState.value.exportCameraCropOnly)

        // Draw dot in corner (10,10) that would be cropped out under zoom=2.0 when cropToCamera=true
        vm.addStrokeToCurrentLayer(
            DrawingStroke(
                tool = ToolType.RECTANGLE,
                color = 0xFFFF0000L,
                brushSize = 4f,
                isFilled = true,
                points = listOf(StrokePoint(0f, 0f), StrokePoint(30f, 30f))
            )
        )
        val zoomedCam = Camera3DSettings(
            isEnabled = true,
            cameraMode = CameraMode.MODE_2D,
            keyframes = listOf(Camera3DKeyframe(frameIndex = 0, zoom = 3.0f))
        )
        val uncropped = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Camera3DEngine.renderFrameWithCameraAndEffects(
            frame = vm.uiState.value.project.frames[0],
            settings = zoomedCam,
            frameIndex = 0,
            projectWidth = 200,
            projectHeight = 200,
            targetBitmap = uncropped,
            cropToCamera = false
        )
        assertTrue(
            "When cropToCamera=false, corner artwork at (15,15) must remain visible even at zoom=3.0",
            android.graphics.Color.red(uncropped.getPixel(15, 15)) > 200 && android.graphics.Color.green(uncropped.getPixel(15, 15)) < 50
        )
        uncropped.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `advanced bucket tool supports expandPixels, multi-layer reference, paint underneath and quick palette round-trips`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("BaldeEPalheta", ProjectResolution.PRESETS[0], 24)

        // 1. Add Bucket layer underneath active line art layer & frame-local bucket layer
        val initialLayersCount = vm.uiState.value.project.frames[0].layers.size
        vm.addBucketLayer(insertBelowActive = true)
        val f0 = vm.uiState.value.project.frames[0]
        assertEquals(initialLayersCount + 1, f0.layers.size)
        assertTrue(f0.layers[0].isBucketLayer)
        assertTrue(vm.uiState.value.bucketConfig.paintUnderneathLineArt)
        assertEquals(f0.layers[0].id, vm.uiState.value.bucketConfig.activeBucketTargetLayerId)

        // Toggle reference on Line Art layer (index 1)
        vm.toggleTimelineLayerBucketReference(1)
        assertTrue(vm.uiState.value.project.frames[0].layers[1].isBucketReference)
        assertTrue(f0.layers[1].id in vm.uiState.value.bucketConfig.selectedReferenceLayerIds)

        // Frame-local bucket sub-layer creation & reference toggle
        vm.addFrameLocalBucketLayer()
        val localsAfter = vm.uiState.value.project.frames[0].localLayers
        assertEquals(1, localsAfter.size)
        assertTrue(localsAfter[0].isBucketLayer)
        vm.toggleFrameLocalLayerBucketReference(0)
        assertTrue(vm.uiState.value.project.frames[0].localLayers[0].isBucketReference)

        // 2. Flood-fill expansion check (expandPixels=2 emits more fill pixels than expandPixels=0)
        val fillBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        fillBmp.eraseColor(android.graphics.Color.BLACK)
        val paintWhite = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        android.graphics.Canvas(fillBmp).drawRect(30f, 30f, 70f, 70f, paintWhite)
        val normalSpans = KodanimaGraphicsEngine.createFloodFillSpans(fillBmp, 50, 50, tolerance = 20, expandPixels = 0)
        val expandedSpans = KodanimaGraphicsEngine.createFloodFillSpans(fillBmp, 50, 50, tolerance = 20, expandPixels = 2)
        val normalWidthSum = normalSpans.chunked(2).sumOf { (a, b) -> (b.x - a.x).toDouble() }
        val expandedWidthSum = expandedSpans.chunked(2).sumOf { (a, b) -> (b.x - a.x).toDouble() }
        assertTrue("Expanded flood fill ($expandedWidthSum) must cover more pixels than exact fill ($normalWidthSum)", expandedWidthSum > normalWidthSum + 100.0)
        fillBmp.recycle()

        // 3. Selected-layer reference bitmap includes ONLY chosen layers
        val layerA = Layer(
            id = "ref-layer-a",
            name = "A",
            strokes = listOf(DrawingStroke(tool = ToolType.RECTANGLE, color = 0xFFFF0000L, isFilled = true, points = listOf(StrokePoint(5f, 5f), StrokePoint(40f, 40f))))
        )
        val layerB = Layer(
            id = "ref-layer-b",
            name = "B",
            strokes = listOf(DrawingStroke(tool = ToolType.RECTANGLE, color = 0xFF0000FFL, isFilled = true, points = listOf(StrokePoint(60f, 60f), StrokePoint(95f, 95f))))
        )
        val testFrame = Frame(layers = listOf(layerA, layerB))
        val onlyABmp = KodanimaGraphicsEngine.renderSelectedLayersToBitmap(testFrame, 100, 100, setOf("ref-layer-a"), android.graphics.Color.WHITE)
        assertTrue(android.graphics.Color.red(onlyABmp.getPixel(20, 20)) > 200)
        assertEquals("Unselected Layer B region must stay WHITE", android.graphics.Color.WHITE, onlyABmp.getPixel(80, 80))
        onlyABmp.recycle()

        // 4. Quick Palette toggle + Q shortcut + lock + Wheel/Eyedropper return to Palette Tray
        assertFalse(vm.uiState.value.isQuickPaletteOpen)
        val handledQ = vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q), context)
        assertTrue("Q shortcut must toggle floating Quick Palette", handledQ)
        assertTrue(vm.uiState.value.isQuickPaletteOpen)
        vm.toggleQuickPaletteLock()
        assertTrue(vm.uiState.value.isQuickPaletteLocked)

        // Wheel round-trip from Palette Tray
        vm.openPaletteTray()
        vm.openColorWheelFromPaletteTray()
        assertFalse(vm.uiState.value.isPaletteTrayOpen)
        assertEquals(EditorDialog.COLOR_PICKER, vm.uiState.value.activeDialog)
        assertTrue(vm.uiState.value.pendingReturnToPaletteTray)
        vm.closeDialog()
        assertTrue("Closing Color Wheel opened from Palette Tray must reopen Palette Tray", vm.uiState.value.isPaletteTrayOpen)
        assertFalse(vm.uiState.value.pendingReturnToPaletteTray)

        // Eyedropper round-trip from Palette Tray
        vm.selectTool(ToolType.BRUSH)
        vm.startEyedropperFromPaletteTray()
        assertEquals(ToolType.EYEDROPPER, vm.uiState.value.selectedTool)
        assertFalse(vm.uiState.value.isPaletteTrayOpen)
        vm.completeEyedropperColorPick(0xFF33AAFFL)
        assertEquals(0xFF33AAFFL, vm.uiState.value.currentColor)
        assertTrue("Picking a color with Eyedropper from Palette Tray must reopen Palette Tray", vm.uiState.value.isPaletteTrayOpen)
        assertEquals(ToolType.BRUSH, vm.uiState.value.selectedTool)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `camera FOV interpolation, connect two keyframes, camera toolbar controls, canvas sky background roundtrip and 3-finger swipe up Zen work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Estudio3DFov", ProjectResolution.PRESETS[0], 24)
        vm.addFrame()
        vm.addFrame()
        vm.addFrame() // 4 frames (0..3)

        // 1. FOV interpolation between keyframes
        vm.goToFrame(0)
        vm.setCameraFov(28f)
        vm.goToFrame(3)
        vm.setCameraFov(82f)
        val kf0 = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 0)
        val kfMid = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 1)
        val kfEnd = Camera3DEngine.evaluateCameraAtFrame(vm.uiState.value.project.camera3D, 3)
        assertEquals(28f, kf0.fovDegrees, 0.01f)
        assertEquals(82f, kfEnd.fovDegrees, 0.01f)
        assertTrue("Interpolated midpoint FOV (${kfMid.fovDegrees}) must sit between 28° and 82°", kfMid.fovDegrees in 35f..75f)

        // 2. Nudge / Zoom / Rotate / Orbit / AdjustFOV / Reset / ToolAction
        vm.setCameraToolAction(CameraToolAction.ORBIT_3D)
        assertEquals(CameraToolAction.ORBIT_3D, vm.uiState.value.cameraToolAction)
        vm.nudgeCameraPan(24f, -12f)
        vm.zoomCameraBy(1.5f)
        vm.rotateCameraBy(15f)
        vm.tiltOrbitCameraBy(10f, -6f)
        vm.adjustCameraFov(-5f)
        val editedEnd = vm.uiState.value.project.camera3D.keyframes.first { it.frameIndex == 3 }
        assertEquals(24f, editedEnd.x, 0.01f)
        assertEquals(-12f, editedEnd.y, 0.01f)
        assertEquals(1.5f, editedEnd.zoom, 0.01f)
        assertEquals(15f, editedEnd.rotationZ, 0.01f)
        assertEquals(10f, editedEnd.yawY, 0.01f)
        assertEquals(-6f, editedEnd.pitchX, 0.01f)
        assertEquals(77f, editedEnd.fovDegrees, 0.01f)

        vm.resetCameraAtCurrentFrame()
        val resetEnd = vm.uiState.value.project.camera3D.keyframes.first { it.frameIndex == 3 }
        assertEquals(0f, resetEnd.x, 0.01f)
        assertEquals(1.0f, resetEnd.zoom, 0.01f)
        assertEquals(50f, resetEnd.fovDegrees, 0.01f)

        // 3. connectTwoCameraKeyframes creates & enables interpolation cleanly from a blank camera track
        vm.resetCameraKeyframesToDefault()
        vm.goToFrame(0)
        vm.connectTwoCameraKeyframes()
        val connectedCam = vm.uiState.value.project.camera3D
        assertTrue(connectedCam.isEnabled)
        assertTrue(connectedCam.autoInterpolate)
        assertTrue("connectTwoCameraKeyframes must ensure >= 2 keyframes", connectedCam.keyframes.size >= 2)

        // 4. Canvas Background + Sky Backdrop persistence & transparent render
        val customBg = CanvasBackgroundSettings(
            style = CanvasBackgroundStyle.TRANSPARENTE,
            primaryColor = 0xFF102030L,
            secondaryColor = 0xFF405060L,
            gradientAngleDeg = 45f,
            texturePattern = CanvasTexturePattern.GRADE_FINA,
            textureIntensity = 0.42f,
            skyMode = SkyBackdropMode.ESTRELAS_NOTURNO,
            skyTopColor = 0xFF0A0D18L,
            skyBottomColor = 0xFF1C2438L
        )
        vm.updateCanvasBackground(customBg)
        assertEquals(CanvasBackgroundStyle.TRANSPARENTE, vm.uiState.value.project.canvasBackground.style)

        val json = FileImportExportManager.projectToJson(vm.uiState.value.project, compact = true)
        val restored = FileImportExportManager.projectFromJson(json)
        assertEquals(CanvasBackgroundStyle.TRANSPARENTE, restored.canvasBackground.style)
        assertEquals(SkyBackdropMode.ESTRELAS_NOTURNO, restored.canvasBackground.skyMode)
        assertEquals(CanvasTexturePattern.GRADE_FINA, restored.canvasBackground.texturePattern)
        assertEquals(0xFF0A0D18L, restored.canvasBackground.skyTopColor)
        assertTrue(restored.camera3D.keyframes.any { it.fovDegrees in 18f..120f })

        val transparentBmp = KodanimaGraphicsEngine.renderFrameToBitmap(
            frame = vm.uiState.value.project.frames[0],
            width = 64,
            height = 64,
            backgroundColor = android.graphics.Color.WHITE,
            background = customBg
        )
        assertEquals(
            "TRANSPARENTE paper style must keep empty pixels transparent",
            0,
            (transparentBmp.getPixel(32, 32) ushr 24) and 0xFF
        )
        transparentBmp.recycle()

        // 5. Three-finger swipe up gesture & CAMERA_* shortcuts
        assertEquals(GestureAction.TOGGLE_ZEN_FULLSCREEN, MultiFingerGesture.THREE_FINGER_SWIPE_UP.defaultAction)
        assertEquals(GestureAction.TOGGLE_ZEN_FULLSCREEN, TouchGestureRegistry.defaultMap()[MultiFingerGesture.THREE_FINGER_SWIPE_UP])
        assertFalse(vm.uiState.value.isZenModeActive)
        vm.executeMultiFingerGesture(MultiFingerGesture.THREE_FINGER_SWIPE_UP)
        assertTrue("3-finger swipe up must enter Zen/Focus mode", vm.uiState.value.isZenModeActive)
        vm.executeMultiFingerGesture(MultiFingerGesture.THREE_FINGER_SWIPE_UP)
        assertFalse("3-finger swipe up again must exit Zen/Focus mode", vm.uiState.value.isZenModeActive)

        val defaultShortcuts = ShortcutRegistry.defaultMap()
        assertNotNull(defaultShortcuts[ShortcutAction.CAMERA_TOGGLE_MODE])
        assertNotNull(defaultShortcuts[ShortcutAction.CAMERA_ADD_KEYFRAME])
        assertNotNull(defaultShortcuts[ShortcutAction.CAMERA_TOGGLE_3D_VIEW])
        assertNotNull(defaultShortcuts[ShortcutAction.CAMERA_FOV_WIDE])
        assertNotNull(defaultShortcuts[ShortcutAction.CAMERA_RESET])

        // Neutral naming verification in CameraMode & SkyBackdropMode
        assertFalse(CameraMode.MODE_2D.labelPt.contains("Clip", ignoreCase = true))
        assertFalse(CameraMode.MODE_3D.labelPt.contains("Blender", ignoreCase = true))
        SkyBackdropMode.entries.forEach { sky ->
            assertFalse(sky.labelPt.contains("Blender", ignoreCase = true))
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `stage 3D places camera strictly in front of layers and background behind all layers`() {
        val layout = computeStage3DZPositions(layerCount = 3, depthStep = 40f, cameraFrontGap = 100f)
        assertEquals(3, layout.layerZs.size)
        val bottomZ = layout.layerZs[0]
        val midZ = layout.layerZs[1]
        val topZ = layout.layerZs[2]
        assertTrue("Camera apex (${layout.cameraApexZ}) must be strictly in front of top layer ($topZ)", layout.cameraApexZ < topZ)
        assertTrue("Top layer ($topZ) must sit closer to camera than middle layer ($midZ)", topZ < midZ)
        assertTrue("Middle layer ($midZ) must sit closer to camera than bottom layer ($bottomZ)", midZ < bottomZ)
        assertTrue("Background paper (${layout.backgroundPlaneZ}) must sit strictly behind bottom layer ($bottomZ)", layout.backgroundPlaneZ > bottomZ)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `krita importer handles raw XML kpp, single quotes, scientific floats, 3bpp gbr, standalone gih and png tips, and corrupt bundle entries`() {
        // 1. Raw XML .kpp with single quotes, escaped &quot; in name, and scientific notation spacing
        val rawPresetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Preset name='Pincel &quot;Pro&quot;' paintopid='paintbrush'>
                <param name='brush_definition'><![CDATA[<Brush diameter='24.5' spacing='1.5e-2' filename='my%20custom%20tip.gbr'/>]]></param>
                <param name='OpacityValue'>0.85</param>
            </Preset>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val rawParsed = KritaBrushImporter.parseRawPresetXmlBytes(rawPresetXml, "raw_test.kpp", presetId = 2001)
        assertNotNull("Raw XML .kpp without PNG wrapper must parse", rawParsed)
        assertEquals("Pincel \"Pro\"", rawParsed!!.namePt)
        assertEquals(24.5f, rawParsed.defaultSize, 0.05f)
        assertEquals(0.85f, rawParsed.defaultOpacity, 0.01f)
        assertEquals(0.02f, rawParsed.dynamicParams.spacing, 0.01f) // 1.5e-2 coerced to 0.02f minimum

        // 2. Build a 4x4 3-bpp (RGB) .gbr brush tip
        val gbrName = "Tip RGB\u0000".toByteArray(Charsets.UTF_8)
        val gbrHeaderSize = 28 + gbrName.size
        val gbrOut = ByteArrayOutputStream()
        val hdrBuf = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN)
            .putInt(gbrHeaderSize)
            .putInt(2) // version 2
            .putInt(4) // width
            .putInt(4) // height
            .putInt(3) // 3 bpp RGB
            .putInt(0x47494D50) // "GIMP"
            .putInt(25) // spacing 0.25
        gbrOut.write(hdrBuf.array())
        gbrOut.write(gbrName)
        repeat(16) { idx ->
            val lum = if (idx in setOf(5, 6, 9, 10)) 0 else 255 // dark dot center -> high alpha
            gbrOut.write(lum)
            gbrOut.write(lum)
            gbrOut.write(lum)
        }
        val gbr3Bytes = gbrOut.toByteArray()
        val parsedGbr3 = KritaBrushImporter.parseGbrBytes(gbr3Bytes, "rgb_tip.gbr", presetId = 2002)
        assertNotNull("3-bpp .gbr must parse", parsedGbr3)
        assertNotNull("3-bpp .gbr must produce a valid tipStampBase64", parsedGbr3!!.dynamicParams.tipStampBase64)

        // 3. Standalone .gih hose wrapper around that .gbr block
        val gihHeader = "4 GIMP Hose Test\n1 1 cellwidth:4 cellheight:4\n".toByteArray(Charsets.US_ASCII)
        val gihBytes = ByteArray(gihHeader.size + gbr3Bytes.size).apply {
            System.arraycopy(gihHeader, 0, this, 0, gihHeader.size)
            System.arraycopy(gbr3Bytes, 0, this, gihHeader.size, gbr3Bytes.size)
        }
        val parsedGih = KritaBrushImporter.parseGihPresetBytes(gihBytes, "hose_test.gih", presetId = 2003)
        assertNotNull("Standalone .gih must parse", parsedGih)
        assertNotNull(parsedGih!!.dynamicParams.tipStampBase64)

        // 4. Standalone .png brush tip
        val tipBmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        tipBmp.eraseColor(android.graphics.Color.BLACK)
        val pngBytes = ByteArrayOutputStream().apply {
            tipBmp.compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        tipBmp.recycle()
        val parsedPngTip = KritaBrushImporter.parsePngTipPresetBytes(pngBytes, "soft_dot.png", presetId = 2004)
        assertNotNull("Standalone .png tip must parse", parsedPngTip)
        assertNotNull(parsedPngTip!!.dynamicParams.tipStampBase64)

        // 5. Bundle with 1 corrupt PNG-header .kpp, 1 valid XML .kpp pointing to URL-encoded tip in brushes/
        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("paintoppresets/broken.kpp"))
            zos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("brushes/my custom tip.gbr"))
            zos.write(gbr3Bytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("paintoppresets/valid.kpp"))
            zos.write(rawPresetXml)
            zos.closeEntry()
        }
        val bundleResults = KritaBrushImporter.parseBundleBytes(zipOut.toByteArray(), startingId = 3000)
        assertEquals("Bundle must still import valid presets even when another .kpp is corrupt", 1, bundleResults.size)
        val linkedBrush = bundleResults.first()
        assertEquals("Pincel \"Pro\"", linkedBrush.namePt)
        assertEquals("predefined", linkedBrush.dynamicParams.shapeKind)
        assertNotNull("Bundle must resolve URL-encoded brush tip filename", linkedBrush.dynamicParams.tipStampBase64)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `3d scene import obj mtl uv and glb hierarchy, multi-stop sky gradient, off-canvas zoomed-out render, stage object animation and keyframe drag work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. OBJ + MTL + PNG UV Texture Import
        val uvBmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        uvBmp.eraseColor(android.graphics.Color.rgb(235, 30, 30))
        val uvPngBytes = ByteArrayOutputStream().apply {
            uvBmp.compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        uvBmp.recycle()

        val mtlBytes = """
            newmtl RedTextured
            Kd 1.0 1.0 1.0
            map_Kd diffuse_red.png
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val objBytes = """
            mtllib scene_uv.mtl
            usemtl RedTextured
            v -1.0 -1.0 0.0
            v 1.0 -1.0 0.0
            v 0.0 1.0 0.0
            vt 0.0 0.0
            vt 1.0 0.0
            vt 0.5 1.0
            vn 0.0 0.0 1.0
            f 1/1/1 2/2/1 3/3/1
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val objScene = Mesh3DImporter.parseSceneBytesForTest(objBytes, "character.obj") { ref ->
            when {
                ref.endsWith(".mtl", ignoreCase = true) -> mtlBytes
                ref.endsWith(".png", ignoreCase = true) -> uvPngBytes
                else -> null
            }
        }
        assertNotNull("OBJ+MTL+PNG scene must parse", objScene)
        assertEquals(1, objScene!!.totalTriangles)
        val firstSub = objScene.subMeshes.first()
        assertNotNull("UV texture pixels must decode from MTL map_Kd", firstSub.texturePixels)
        assertTrue(android.graphics.Color.red(firstSub.texturePixels!![0]) > 200)

        // 2. Binary GLB 2.0 with Parent->Child Node Hierarchy
        val binBuf = ByteBuffer.allocate(36 + 6).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0f).putFloat(0f).putFloat(0f)
            .putFloat(1f).putFloat(0f).putFloat(0f)
            .putFloat(0f).putFloat(1f).putFloat(0f)
            .putShort(0).putShort(1).putShort(2)
            .array()
        val binPaddedLen = ((binBuf.size + 3) / 4) * 4
        val gltfJsonRaw = """
            {"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"translation":[2,0,0],"children":[1]},{"translation":[0,3,0],"mesh":0}],"meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1}]}],"buffers":[{"byteLength":${binBuf.size}}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":6}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},{"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"}]}
        """.trim().toByteArray(Charsets.UTF_8)
        val jsonPaddedLen = ((gltfJsonRaw.size + 3) / 4) * 4
        val totalGlbSize = 12 + 8 + jsonPaddedLen + 8 + binPaddedLen
        val glbOut = ByteBuffer.allocate(totalGlbSize).order(ByteOrder.LITTLE_ENDIAN)
            .put('g'.code.toByte()).put('l'.code.toByte()).put('T'.code.toByte()).put('F'.code.toByte())
            .putInt(2)
            .putInt(totalGlbSize)
            .putInt(jsonPaddedLen)
            .putInt(0x4E4F534A)
            .put(gltfJsonRaw)
        while (glbOut.position() < 12 + 8 + jsonPaddedLen) glbOut.put(0x20.toByte())
        glbOut.putInt(binPaddedLen)
            .putInt(0x004E4942)
            .put(binBuf)
        while (glbOut.position() < totalGlbSize) glbOut.put(0.toByte())

        val glbScene = Mesh3DImporter.parseSceneBytesForTest(glbOut.array(), "prop.glb")
        assertNotNull("Binary GLB scene with node hierarchy must parse", glbScene)
        assertEquals(1, glbScene!!.totalTriangles)
        assertTrue(glbScene.boundingRadius > 0.1f)

        // 3. Multi-Stop Sky Gradient (Light point, intermediate stop, Dark point)
        val skyBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val skyCfg = CanvasBackgroundSettings(
            style = CanvasBackgroundStyle.TRANSPARENTE,
            skyMode = SkyBackdropMode.GRADIENTE_CEU,
            skyLightColor = 0xFFFF0000L,
            skyDarkColor = 0xFF0000FFL,
            skyGradientStops = listOf(GradientColorStop(position = 0.5f, color = 0xFF00FF00L)),
            skyGradientAngleDeg = 90f
        )
        KodanimaGraphicsEngine.drawSkyBackdropToCanvas(android.graphics.Canvas(skyBmp), 100f, 100f, skyCfg)
        val topPix = skyBmp.getPixel(50, 2)
        val midPix = skyBmp.getPixel(50, 50)
        val botPix = skyBmp.getPixel(50, 98)
        assertTrue("Top/Light point must be red ($topPix)", android.graphics.Color.red(topPix) > 200 && android.graphics.Color.blue(topPix) < 50)
        assertTrue("Middle stop must be green ($midPix)", android.graphics.Color.green(midPix) > 200)
        assertTrue("Bottom/Dark point must be blue ($botPix)", android.graphics.Color.blue(botPix) > 200 && android.graphics.Color.red(botPix) < 50)
        skyBmp.recycle()

        // 4. Software 3D Rasterizer + Zoomed-out Off-Canvas Render
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Cena3D", ProjectResolution.PRESETS[0], 24)
        vm.addFrame()
        vm.addFrame()
        vm.addFrame() // 4 frames (0..3)

        vm.addBuiltIn3DPrimitive(BuiltIn3DPrimitive.CUBE)
        val addedObj = vm.uiState.value.project.camera3D.scene3DObjects.firstOrNull()
        assertNotNull("addBuiltIn3DPrimitive must append SceneObject3D", addedObj)
        assertEquals(StageTargetType.OBJECT_3D, vm.uiState.value.stageSelection.type)
        assertEquals(addedObj!!.id, vm.uiState.value.stageSelection.id)
        assertTrue(vm.uiState.value.stageFocusRequestNonce >= 1)

        val render3D = Camera3DEngine.renderSceneObjectsSoftware3D(
            outW = 160,
            outH = 120,
            sceneObjects = listOf(addedObj),
            frameIndex = 0,
            autoInterpolate = true,
            camYawDeg = 20f,
            camPitchDeg = 12f,
            camRollDeg = 0f,
            camPanX = 0f,
            camPanY = 0f,
            focalPx = 320f,
            vpCx = 80f,
            vpCy = 60f,
            worldUnitScale = 1.0f
        )
        assertNotNull("3D software renderer must produce bitmap", render3D)
        val (objBmp, objBounds) = render3D!!
        assertTrue("Projected 3D bounds must be recorded for tap selection", objBounds.containsKey(addedObj.id))
        var opaqueCount = 0
        for (py in 0 until objBmp.height) {
            for (px in 0 until objBmp.width) {
                if (((objBmp.getPixel(px, py) ushr 24) and 0xFF) > 128) opaqueCount++
            }
        }
        assertTrue("Rendered 3D cube must cover visible pixels ($opaqueCount)", opaqueCount > 50)
        objBmp.recycle()

        // Zoomed-out (zoom = 0.4f) render with transparent paper renders sky outside 2D canvas bounds
        val zoomOutCam = Camera3DSettings(
            isEnabled = true,
            cameraMode = CameraMode.MODE_2D,
            keyframes = listOf(Camera3DKeyframe(frameIndex = 0, zoom = 0.4f))
        )
        val zoomOutBmp = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        Camera3DEngine.renderFrameWithCameraAndEffects(
            frame = vm.uiState.value.project.frames[0],
            settings = zoomOutCam,
            frameIndex = 0,
            projectWidth = 200,
            projectHeight = 120,
            targetBitmap = zoomOutBmp,
            cropToCamera = true,
            background = CanvasBackgroundSettings(
                style = CanvasBackgroundStyle.TRANSPARENTE,
                skyMode = SkyBackdropMode.SOLIDO,
                skyLightColor = 0xFF11AA44L
            ),
            renderTransparentAsSky = true
        )
        val outerBorderPix = zoomOutBmp.getPixel(4, 4)
        assertEquals("Off-canvas area when zoomed out must render configured sky", 0xFF11AA44.toInt(), outerBorderPix)
        zoomOutBmp.recycle()

        // 5. Keyframe Dragging + Layer Animation + 2D Viewport Nav Target Toggle
        assertEquals(CameraViewportNavTarget.CAMERA, vm.uiState.value.cameraViewportNavTarget)
        vm.toggleCameraViewportNavTarget()
        assertEquals(CameraViewportNavTarget.CANVAS, vm.uiState.value.cameraViewportNavTarget)

        vm.goToFrame(1)
        vm.nudgeCameraPan(80f, -40f)
        assertTrue(vm.uiState.value.project.camera3D.keyframes.any { it.frameIndex == 1 })
        vm.moveCameraKeyframe(fromFrame = 1, toFrame = 3)
        assertFalse("Source frame 1 must no longer hold moved camera keyframe", vm.uiState.value.project.camera3D.keyframes.any { it.frameIndex == 1 })
        val movedCamKf = vm.uiState.value.project.camera3D.keyframes.firstOrNull { it.frameIndex == 3 }
        assertNotNull("Target frame 3 must receive moved camera keyframe", movedCamKf)
        assertEquals(80f, movedCamKf!!.x, 0.01f)

        // Layer Transform Track animation & long-press move
        val layer0Id = vm.uiState.value.project.frames[0].layers[0].id
        vm.selectStageTarget(StageSelection(StageTargetType.LAYER, layer0Id))
        vm.goToFrame(0)
        vm.updateSelectedObjectTransform { ObjectTransformKeyframe(frameIndex = 0, x = 0f, rotY = 0f) }
        vm.goToFrame(2)
        vm.updateSelectedObjectTransform { ObjectTransformKeyframe(frameIndex = 2, x = 200f, rotY = 60f) }
        val midLayerEval = Camera3DEngine.evaluateLayerTransformAtFrame(vm.uiState.value.project.camera3D.layerTransformTracks, layer0Id, frameIndex = 1)
        assertEquals(100f, midLayerEval.x, 10f)
        assertEquals(30f, midLayerEval.rotY, 5f)
        vm.moveSelectedTargetKeyframe(fromFrame = 2, toFrame = 3)
        val movedLayerTrack = vm.uiState.value.project.camera3D.layerTransformTracks.first { it.layerId == layer0Id }
        assertFalse(movedLayerTrack.keyframes.any { it.frameIndex == 2 })
        assertEquals(200f, movedLayerTrack.keyframes.first { it.frameIndex == 3 }.x, 0.01f)
    }

    @Test
    fun `decoupled per-layer cel timing stretch move onion skin and continuous camera timing work independently`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("CamadasIndependentes", ProjectResolution.PRESETS[0], 24)
        vm.addLayer() // Now 2 layers: index 0 and index 1
        vm.addFrame() // 2 frames (frameIndex 0 and frameIndex 1)

        // Mark distinct strokes on each layer/frame so composed frames can be verified
        vm.selectLayer(0)
        vm.selectFrame(0)
        vm.addStrokeToCurrentLayer(DrawingStroke(points = listOf(StrokePoint(10f, 10f)), color = 0xFFFF0000L, brushSize = 4f))
        vm.selectLayer(1)
        vm.selectFrame(0)
        vm.addStrokeToCurrentLayer(DrawingStroke(points = listOf(StrokePoint(20f, 20f)), color = 0xFF00FF00L, brushSize = 4f))
        vm.selectLayer(1)
        vm.selectFrame(1)
        vm.addStrokeToCurrentLayer(DrawingStroke(points = listOf(StrokePoint(30f, 30f)), color = 0xFF0000FFL, brushSize = 4f))

        // 1. Stretching layer 0 cel 0 to 4 ticks MUST NOT stretch or shift layer 1
        vm.setLayerCelDuration(layerIndex = 0, frameIndex = 0, holdTicks = 4)
        val projAfterStretch = vm.uiState.value.project
        val l0Cels = TimelineTrackEngine.computeLayerCels(projAfterStretch, 0)
        val l1Cels = TimelineTrackEngine.computeLayerCels(projAfterStretch, 1)
        assertEquals("Layer 0 cel 0 must have duration 4 ticks", 4, l0Cels[0].durationTicks)
        assertEquals("Layer 1 cel 0 must stay unaffected at 1 tick", 1, l1Cels[0].durationTicks)
        assertEquals("Layer 1 cel 1 must still start at tick 1", 1, l1Cels[1].startTick)
        assertEquals("Project total ticks must match longest layer", 4, TimelineTrackEngine.computeTotalTicks(projAfterStretch))

        // Composing tick 2 yields layer 0 cel 0's red stroke while layer 1 has moved past cel 1 (blank gap)
        val composedTick2 = TimelineTrackEngine.composeFrameAtTick(projAfterStretch, tick = 2, activeLayerIndex = 0)
        assertEquals(0xFFFF0000L, composedTick2.layers[0].strokes.firstOrNull()?.color)
        assertTrue("Layer 1 has ended by tick 2 without being stretched by layer 0", composedTick2.layers[1].strokes.isEmpty())

        // Onion skin at tick 2 (still inside stretched layer 0 cel 0) must NOT ghost cel 0 over itself
        val prevOnionAtTick2 = TimelineTrackEngine.resolveOnionSkinLayersAtTick(projAfterStretch, tick = 2, stepOffset = -1)
        assertFalse(
            "Stretched cel 0 on layer 0 must not ghost onto itself at tick 2",
            prevOnionAtTick2.any { it.strokes.any { s -> s.color == 0xFFFF0000L } }
        )

        // 2. Moving cel 1 on layer 1 to tick 3 shifts ONLY layer 1
        vm.moveLayerCelToTick(layerIndex = 1, frameIndex = 1, targetTick = 3)
        val projAfterMove = vm.uiState.value.project
        val movedL1Cels = TimelineTrackEngine.computeLayerCels(projAfterMove, 1)
        val untouchedL0Cels = TimelineTrackEngine.computeLayerCels(projAfterMove, 0)
        assertEquals("Layer 0 cel 0 remains at startTick 0", 0, untouchedL0Cels[0].startTick)
        assertEquals("Layer 1 cel 1 moved independently to tick 3", 3, movedL1Cels[1].startTick)
        val composedTick3 = TimelineTrackEngine.composeFrameAtTick(projAfterMove, tick = 3, activeLayerIndex = 1)
        assertEquals(0xFFFF0000L, composedTick3.layers[0].strokes.firstOrNull()?.color)
        assertEquals(0xFF0000FFL, composedTick3.layers[1].strokes.firstOrNull()?.color)

        // 3. Independent continuous camera timing & holdTicks across a single held drawing cel (8 ticks)
        vm.setLayerCelDuration(layerIndex = 0, frameIndex = 0, holdTicks = 8)
        vm.toggleCameraEnabled()
        vm.goToTimelineTick(0)
        vm.addOrUpdateCameraKeyframeAtCurrentFrame(CameraEasingCurve.LINEAR)
        vm.setCameraKeyframeHold(tickIndex = 0, holdTicks = 2) // Holds flat on ticks 0..1 before easing at tick 2
        vm.goToTimelineTick(6)
        vm.updateCameraAtCurrentFrame { it.copy(zoom = 2.0f, x = 200f) }

        val camSettings = vm.uiState.value.project.camera3D
        // During hold plateau (tick 1.0f <= end of hold at 1.0 + 1 -> transitions from tick 1? Wait: key0 at 0 with holdTicks=2 holds through 0..1)
        val evalHoldMid = Camera3DEngine.evaluateCameraAtTick(camSettings, tickFloat = 0.8f)
        assertEquals("Camera holds keyframe 0 value during holdTicks plateau", 1.0f, evalHoldMid.zoom, 0.01f)

        // Midpoint of transition ticks [1..6] is 3.5f -> 50% of zoom 1.0..2.0 = 1.5f
        val evalSubTickMid = Camera3DEngine.evaluateCameraAtTick(camSettings, tickFloat = 3.5f)
        assertTrue("Continuous sub-tick interpolation advances smoothly between keyframes (zoom=${evalSubTickMid.zoom})", evalSubTickMid.zoom > 1.25f && evalSubTickMid.zoom < 1.75f)
        assertTrue("Continuous X pan interpolates smoothly (${evalSubTickMid.x})", evalSubTickMid.x > 60f && evalSubTickMid.x < 140f)

        // 4. Serialization round-trip preserves celHoldTicks, celStartTick, and keyframe holdTicks
        val json = FileImportExportManager.projectToJson(vm.uiState.value.project)
        val restored: AnimationProject = FileImportExportManager.projectFromJson(json)
        assertEquals(8, restored.frames[0].layers[0].celHoldTicks)
        assertEquals(3, restored.frames[1].layers[1].celStartTick)
        assertEquals(2, restored.camera3D.keyframes.first { it.frameIndex == 0 }.holdTicks)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    fun `show 3D in canvas toggle modes serialization and 3D background rasterization work cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Fundo3DCanvas", ProjectResolution.PRESETS[0], 12)

        assertFalse(vm.uiState.value.project.canvasBackground.show3DInCanvas)
        assertEquals(Canvas3DBackgroundRenderMode.STATIC_2D_CACHE, vm.uiState.value.project.canvasBackground.canvas3DRenderMode)

        vm.toggleShow3DInCanvas()
        assertTrue(vm.uiState.value.project.canvasBackground.show3DInCanvas)

        vm.setCanvas3DBackgroundRenderMode(Canvas3DBackgroundRenderMode.PRE_RENDER_ON_PLAY)
        assertEquals(Canvas3DBackgroundRenderMode.PRE_RENDER_ON_PLAY, vm.uiState.value.project.canvasBackground.canvas3DRenderMode)

        vm.setCanvas3DBackgroundRenderMode(Canvas3DBackgroundRenderMode.REALTIME_3D)
        assertEquals(Canvas3DBackgroundRenderMode.REALTIME_3D, vm.uiState.value.project.canvasBackground.canvas3DRenderMode)

        val (cubeUri, _) = Mesh3DImporter.createBuiltInSceneAsset(BuiltIn3DPrimitive.CUBE)
        val projWithCube = vm.uiState.value.project.copy(
            camera3D = vm.uiState.value.project.camera3D.copy(
                scene3DObjects = listOf(
                    SceneObject3D(id = "cube_bg_test", name = "Cubo Palco", meshAssetPath = cubeUri)
                )
            )
        )
        val bgBitmap = Camera3DEngine.renderCanvas3DBackgroundBitmap(
            outW = 160,
            outH = 120,
            project = projWithCube,
            tickFloat = 0f,
            fps = 12
        )
        assertEquals(160, bgBitmap.width)
        assertEquals(120, bgBitmap.height)
        val pixels = IntArray(bgBitmap.width * bgBitmap.height)
        bgBitmap.getPixels(pixels, 0, bgBitmap.width, 0, 0, bgBitmap.width, bgBitmap.height)
        val uniqueColors = pixels.toSet().size
        assertTrue("3D canvas background should render sky, floor grid, and 3D cube ($uniqueColors colors)", uniqueColors > 4)
        bgBitmap.recycle()

        val json = FileImportExportManager.projectToJson(projWithCube)
        val restored = FileImportExportManager.projectFromJson(json)
        assertTrue(restored.canvasBackground.show3DInCanvas)
        assertEquals(Canvas3DBackgroundRenderMode.REALTIME_3D, restored.canvasBackground.canvas3DRenderMode)
    }

    @Test
    fun `glTF animated node evaluation updates triangle positions cleanly across time`() {
        // Minimal self-contained animated glTF triangle (translates along +X from 0 to +4 between t=0s and t=2s)
        val bb = java.nio.ByteBuffer.allocate(3 * 3 * 4 + 2 * 4 + 2 * 3 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        // Positions (3 verts: 36 bytes)
        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
        bb.putFloat(1f); bb.putFloat(0f); bb.putFloat(0f)
        bb.putFloat(0f); bb.putFloat(1f); bb.putFloat(0f)
        // Keyframe times (2 floats: 8 bytes at offset 36)
        bb.putFloat(0f); bb.putFloat(2f)
        // Keyframe translation values (2 vec3s: 24 bytes at offset 44)
        bb.putFloat(0f); bb.putFloat(0f); bb.putFloat(0f)
        bb.putFloat(4f); bb.putFloat(0f); bb.putFloat(0f)
        val binB64 = android.util.Base64.encodeToString(bb.array(), android.util.Base64.NO_WRAP)

        val gltfJson = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri": "data:application/octet-stream;base64,$binB64", "byteLength": ${bb.capacity()}}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                {"buffer": 0, "byteOffset": 36, "byteLength": 8},
                {"buffer": 0, "byteOffset": 44, "byteLength": 24}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3", "min": [0,0,0], "max": [1,1,0]},
                {"bufferView": 1, "componentType": 5126, "count": 2, "type": "SCALAR", "min": [0], "max": [2]},
                {"bufferView": 2, "componentType": 5126, "count": 2, "type": "VEC3"}
              ],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0}}]}],
              "nodes": [{"mesh": 0, "translation": [0, 0, 0]}],
              "scenes": [{"nodes": [0]}],
              "scene": 0,
              "animations": [{
                "name": "SlideX",
                "samplers": [{"input": 1, "interpolation": "LINEAR", "output": 2}],
                "channels": [{"sampler": 0, "target": {"node": 0, "path": "translation"}}]
              }]
            }
        """.trimIndent()

        val scene = Mesh3DImporter.parseSceneBytesForTest(gltfJson.toByteArray(Charsets.UTF_8), "anim_tri.gltf")
        assertNotNull(scene)
        assertTrue("glTF animation clip must be detected", scene!!.hasAnimations)
        val meshT0 = Mesh3DImporter.evaluateSceneAtTime(scene, 0f).first()
        val meshT1 = Mesh3DImporter.evaluateSceneAtTime(scene, 1f).first()
        assertTrue(
            "Vertex X should advance smoothly at t=1.0s (t0=${meshT0.vertices[0]}, t1=${meshT1.vertices[0]})",
            meshT1.vertices[0] > meshT0.vertices[0] + 0.5f
        )
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    fun `native brushes 7 through 12 render cleanly without overlap seam darkening`() {
        val brushes = BrushPreset.ALL_12_PRESETS.filter { it.id in 7..12 }
        assertEquals(6, brushes.size)

        val points = listOf(
            StrokePoint(16f, 64f, 0.8f),
            StrokePoint(48f, 58f, 0.9f),
            StrokePoint(80f, 64f, 0.9f),
            StrokePoint(112f, 70f, 0.8f)
        )

        brushes.forEach { preset ->
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val stroke = DrawingStroke(
                points = points,
                color = 0xCC2266DDL,
                brushSize = preset.defaultSize.coerceAtLeast(14f),
                opacity = preset.defaultOpacity,
                tool = preset.toolType,
                brushPresetId = preset.id,
                customBrushParams = preset.dynamicParams.toCompactJson()
            )
            KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, canvas)

            val px = IntArray(128 * 128)
            bmp.getPixels(px, 0, 128, 0, 0, 128, 128)
            val nonZero = px.count { ((it ushr 24) and 0xFF) > 0 }
            assertTrue("Brush #${preset.id} (${preset.namePt}) must render visible pixels (got $nonZero)", nonZero > 40)

            // Verify translucent #7 Marker & #9 Bristle Glaze stay smooth and never double-darken above single stroke alpha ceiling
            if (preset.id == 7 || preset.id == 9) {
                val maxAlpha = px.maxOf { (it ushr 24) and 0xFF }
                val expectedMax = ((204 * preset.defaultOpacity) + 12f).toInt()
                assertTrue(
                    "Brush #${preset.id} must isolate overlapping dabs (maxAlpha=$maxAlpha, ceiling=$expectedMax)",
                    maxAlpha <= expectedMax
                )
            }
            bmp.recycle()
        }
    }

    @Test
    fun `krita importer supports pattern files percentage opacity fade hardness paintopids and shared tips vault`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. GIMP .pat ('GPAT') pattern parsing
        val patW = 8
        val patH = 8
        val headerSize = 28 // 24 + "Quad" (4 bytes)
        val patBuf = java.nio.ByteBuffer.allocate(headerSize + patW * patH).order(java.nio.ByteOrder.BIG_ENDIAN)
        patBuf.putInt(headerSize)
        patBuf.putInt(1)
        patBuf.putInt(patW)
        patBuf.putInt(patH)
        patBuf.putInt(1)
        patBuf.put("GPAT".toByteArray(Charsets.US_ASCII))
        patBuf.put("Quad".toByteArray(Charsets.US_ASCII))
        for (i in 0 until patW * patH) {
            patBuf.put(((i % 2) * 200 + 30).toByte())
        }
        val patPreset = KritaBrushImporter.parsePatPresetBytes(patBuf.array(), "quad_grain.pat", 1201)
        assertNotNull(patPreset)
        assertNotNull(patPreset!!.dynamicParams.tipStampBase64)

        // 2. Save shared .gbr tip in vault, then parse standalone XML referencing it with % Opacity/Flow + fade + colorsmudge
        val gbrHeaderSize = 32
        val gbrBuf = java.nio.ByteBuffer.allocate(gbrHeaderSize + 16).order(java.nio.ByteOrder.BIG_ENDIAN)
        gbrBuf.putInt(gbrHeaderSize)
        gbrBuf.putInt(2)
        gbrBuf.putInt(4)
        gbrBuf.putInt(4)
        gbrBuf.putInt(1)
        gbrBuf.put("GIMP".toByteArray(Charsets.US_ASCII))
        gbrBuf.putInt(15)
        gbrBuf.put("leaf".toByteArray(Charsets.US_ASCII))
        repeat(16) { gbrBuf.put(220.toByte()) }

        KritaBrushImporter.saveSharedTips(context, mapOf("leaf_tip.gbr" to gbrBuf.array()))
        val loadedTips = KritaBrushImporter.loadSharedTipMap(context)
        assertTrue(loadedTips.containsKey("leaf_tip.gbr"))

        val xml = """
            <Preset name="Aquarela Leaf" paintopid="colorsmudge">
              <param name="OpacityValue">80</param>
              <param name="FlowValue">60</param>
              <param name="brush_definition"><![CDATA[<Brush type="gbr_brush" filename="leaf_tip.gbr" spacing="0.12"><MaskGenerator radius="12" fade="0.2" ratio="0.8"/></Brush>]]></param>
            </Preset>
        """.trimIndent()
        val parsed = KritaBrushImporter.parseRawPresetXmlBytes(
            rawBytes = xml.toByteArray(Charsets.UTF_8),
            fileName = "aquarela_leaf.xml",
            presetId = 1202,
            externalBrushTips = loadedTips
        )
        assertNotNull(parsed)
        assertEquals(KritaBrushEngineKind.BLENDER_WET_SOFT, parsed!!.engineKind)
        assertEquals(0.80f, parsed.defaultOpacity, 0.02f)
        assertEquals(0.60f, parsed.dynamicParams.flow, 0.02f)
        assertEquals("predefined", parsed.dynamicParams.shapeKind)
        assertNotNull(parsed.dynamicParams.tipStampBase64)
        assertTrue("fade=0.2 should yield soft hardness < 0.65 (got ${parsed.dynamicParams.hardness})", parsed.dynamicParams.hardness < 0.65f)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    fun `3D canvas background aligns with active camera parameters and reuses pooled bitmap`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("Alinhamento3D", ProjectResolution.PRESETS[0], 12)
        val (cubeUri, _) = Mesh3DImporter.createBuiltInSceneAsset(BuiltIn3DPrimitive.CUBE)

        val baseProj = vm.uiState.value.project.copy(
            canvasBackground = CanvasBackgroundSettings(
                style = CanvasBackgroundStyle.TRANSPARENTE,
                show3DInCanvas = true,
                canvas3DRenderMode = Canvas3DBackgroundRenderMode.REALTIME_3D
            ),
            camera3D = Camera3DSettings(
                isEnabled = false,
                cameraMode = CameraMode.MODE_3D,
                keyframes = listOf(
                    Camera3DKeyframe(frameIndex = 0, x = 0f, y = 0f, zoom = 1.0f, pitchX = 0f, yawY = 0f, fovDegrees = 50f)
                ),
                scene3DObjects = listOf(
                    SceneObject3D(id = "cube_align", name = "Cubo Alinhado", meshAssetPath = cubeUri)
                )
            )
        )

        val bmpHeadOn = Camera3DEngine.renderCanvas3DBackgroundBitmap(
            outW = 160,
            outH = 120,
            project = baseProj,
            tickFloat = 0f,
            fps = 12
        )
        val pxHeadOn = IntArray(160 * 120)
        bmpHeadOn.getPixels(pxHeadOn, 0, 160, 0, 0, 160, 120)

        // Rotated camera even while isEnabled == false must match the active camera angle instead of a hardcoded tilt
        val rotatedProj = baseProj.copy(
            camera3D = baseProj.camera3D.copy(
                keyframes = listOf(
                    Camera3DKeyframe(frameIndex = 0, x = 80f, y = -40f, zoom = 1.45f, pitchX = 24f, yawY = -36f, fovDegrees = 68f)
                )
            )
        )
        val bmpReused = Camera3DEngine.renderCanvas3DBackgroundBitmap(
            outW = 160,
            outH = 120,
            project = rotatedProj,
            tickFloat = 0f,
            fps = 12,
            reuseBitmap = bmpHeadOn
        )
        assertTrue("reuseBitmap should reuse the exact same Bitmap instance", bmpReused === bmpHeadOn)

        val pxRotated = IntArray(160 * 120)
        bmpReused.getPixels(pxRotated, 0, 160, 0, 0, 160, 120)
        val diffCount = pxHeadOn.indices.count { pxHeadOn[it] != pxRotated[it] }
        assertTrue(
            "3D canvas background must visibly follow camera pitch/yaw/zoom/fov parameters (diff=$diffCount)",
            diffCount > 1200
        )

        // With isEnabled == true in MODE_3D and matching keyframe, output should match renderCanvas3DBackgroundBitmap's sky+grid+cube framing
        val enabledProj = rotatedProj.copy(camera3D = rotatedProj.camera3D.copy(isEnabled = true))
        val bmpEnabled = Camera3DEngine.renderCanvas3DBackgroundBitmap(
            outW = 160,
            outH = 120,
            project = enabledProj,
            tickFloat = 0f,
            fps = 12
        )
        val pxEnabled = IntArray(160 * 120)
        bmpEnabled.getPixels(pxEnabled, 0, 160, 0, 0, 160, 120)
        assertTrue("Enabled and preview 3D background should match identically", pxRotated.contentEquals(pxEnabled))

        bmpHeadOn.recycle()
        bmpEnabled.recycle()
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    fun `off-canvas negative coordinates viewport matrix renders and roundtrips cleanly with undo redo and studio3d shortcuts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject("ForaDoCanvas", ProjectResolution.PRESETS[0], 24)

        // 1. Stroke drawn strictly outside canvas top-left (negative canvas coordinates)
        val offCanvasStroke = SelectionGeometryHelper.withRecomputedBounds(
            DrawingStroke(
                tool = ToolType.BRUSH,
                color = 0xFF00E676L,
                brushSize = 18f,
                opacity = 1.0f,
                points = listOf(
                    StrokePoint(-120f, -80f, 1.0f),
                    StrokePoint(-40f, -80f, 1.0f)
                )
            )
        )
        assertTrue("minX must preserve negative off-canvas coordinates", offCanvasStroke.minX < -100f)
        assertTrue("maxX must be negative (no -Float.MAX_VALUE overflow)", offCanvasStroke.maxX < 0f)

        // Render via screen-resolution viewportMatrix shifted so (-80, -80) maps to screen (80, 80)
        val vpBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        vpBmp.eraseColor(android.graphics.Color.TRANSPARENT)
        val vpMat = android.graphics.Matrix().apply { setTranslate(160f, 160f) }
        val layerWithOffCanvas = Layer(name = "Camada Livre", strokes = listOf(offCanvasStroke))
        KodanimaGraphicsEngine.drawLayersStackToCanvas(
            layers = listOf(layerWithOffCanvas),
            canvas = android.graphics.Canvas(vpBmp),
            targetWidth = 1280,
            targetHeight = 720,
            visibleBounds = android.graphics.RectF(-200f, -200f, 100f, 100f),
            viewportMatrix = vpMat,
            viewportWidth = 200,
            viewportHeight = 200
        )
        // (-80 + 160, -80 + 160) = (80, 80) must contain green off-canvas stroke pixels
        val px = vpBmp.getPixel(80, 80)
        assertTrue("Off-canvas stroke must render inside viewport bitmap", ((px ushr 24) and 0xFF) > 200)
        assertTrue("Rendered pixel must retain green stroke color", android.graphics.Color.green(px) > 180)
        vpBmp.recycle()

        // 2. Ctrl+Z Undo & Ctrl+Y / Ctrl+Shift+Z Redo keyboard shortcuts
        vm.addStrokeToCurrentLayer(offCanvasStroke)
        val countAfterDraw = vm.uiState.value.project.frames[0].layers[1].strokes.size
        assertTrue(countAfterDraw >= 1)

        val ctrlZ = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON)
        assertTrue(vm.handlePhysicalKeyEvent(ctrlZ, context))
        assertEquals(countAfterDraw - 1, vm.uiState.value.project.frames[0].layers[1].strokes.size)

        val ctrlY = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Y, 0, KeyEvent.META_CTRL_ON)
        assertTrue(vm.handlePhysicalKeyEvent(ctrlY, context))
        assertEquals(countAfterDraw, vm.uiState.value.project.frames[0].layers[1].strokes.size)

        assertTrue(vm.handlePhysicalKeyEvent(ctrlZ, context))
        assertEquals(countAfterDraw - 1, vm.uiState.value.project.frames[0].layers[1].strokes.size)

        val ctrlShiftZ = KeyEvent(
            0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        )
        assertTrue(vm.handlePhysicalKeyEvent(ctrlShiftZ, context))
        assertEquals(countAfterDraw, vm.uiState.value.project.frames[0].layers[1].strokes.size)

        // 3. Estúdio 3D Blender-style shortcuts (G, X, R, S, Shift+N)
        vm.toggleCameraTimeline()
        vm.toggleCameraSideView3D()
        assertTrue(vm.uiState.value.isCameraSideView3D)

        assertTrue(vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_G), context))
        assertEquals(1, vm.uiState.value.studio3DTransformShortcutMode)
        assertTrue(vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X), context))
        assertEquals(1, vm.uiState.value.studio3DActiveAxisLock)
        assertTrue(vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R), context))
        assertEquals(2, vm.uiState.value.studio3DTransformShortcutMode)
        assertTrue(vm.handlePhysicalKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S), context))
        assertEquals(3, vm.uiState.value.studio3DTransformShortcutMode)
        val shiftN = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_N, 0, KeyEvent.META_SHIFT_ON)
        assertTrue(vm.handlePhysicalKeyEvent(shiftN, context))
        assertTrue(vm.uiState.value.isStudio3DSplitNodesOpen)
    }

    @Test
    fun `fbx ascii and 3ds binary 3d parsers import geometry and UVs cleanly`() {
        // 1. Minimal ASCII FBX with 3 vertices, 1 triangle, and LayerElementUV
        val fbxAscii = """
            ; FBX 7.4.0 project file
            FBXHeaderExtension:  {
                FBXHeaderVersion: 1003
            }
            Objects:  {
                Geometry: 1001, "Geometry::Plane", "Mesh" {
                    Vertices: *9 {
                        a: -1.0,-1.0,0.0,1.0,-1.0,0.0,0.0,1.0,0.0
                    }
                    PolygonVertexIndex: *3 {
                        a: 0,1,-3
                    }
                    LayerElementUV: 0 {
                        MappingInformationType: "ByPolygonVertex"
                        ReferenceInformationType: "Direct"
                        UV: *6 {
                            a: 0.0,0.0,1.0,0.0,0.5,1.0
                        }
                    }
                }
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val fbxScene = Mesh3DImporter.parseSceneBytesForTest(fbxAscii, "plane_test.fbx")
        assertNotNull("ASCII .fbx must parse cleanly", fbxScene)
        assertEquals(1, fbxScene!!.totalTriangles)
        assertTrue("FBX submesh must retain non-zero UVs", fbxScene.subMeshes.first().uvs.any { it != 0f })

        // 2. Minimal Binary .3DS (0x4D4D -> 0x3D3D -> 0x4000 -> 0x4100 -> 0x4110 + 0x4120 + 0x4140)
        fun chunk(id: Int, payload: ByteArray): ByteArray {
            val buf = ByteBuffer.allocate(6 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(id.toShort())
            buf.putInt(6 + payload.size)
            buf.put(payload)
            return buf.array()
        }
        val vertsPayload = ByteBuffer.allocate(2 + 3 * 12).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(3)
            .putFloat(-1f).putFloat(0f).putFloat(-1f)
            .putFloat(1f).putFloat(0f).putFloat(-1f)
            .putFloat(0f).putFloat(0f).putFloat(1f)
            .array()
        val facesPayload = ByteBuffer.allocate(2 + 8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1)
            .putShort(0).putShort(1).putShort(2).putShort(7)
            .array()
        val uvsPayload = ByteBuffer.allocate(2 + 3 * 8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(3)
            .putFloat(0f).putFloat(0f)
            .putFloat(1f).putFloat(0f)
            .putFloat(0.5f).putFloat(1f)
            .array()
        val triMeshPayload = chunk(0x4110, vertsPayload) + chunk(0x4120, facesPayload) + chunk(0x4140, uvsPayload)
        val objName = "Tri3DS\u0000".toByteArray(Charsets.US_ASCII)
        val namedObjPayload = objName + chunk(0x4100, triMeshPayload)
        val tdsBytes = chunk(0x4D4D, chunk(0x3D3D, chunk(0x4000, namedObjPayload)))

        val tdsScene = Mesh3DImporter.parseSceneBytesForTest(tdsBytes, "prop_test.3ds")
        assertNotNull("Binary .3ds must parse cleanly", tdsScene)
        assertEquals(1, tdsScene!!.totalTriangles)
        assertEquals(6, tdsScene.subMeshes.first().uvs.size)
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test
    fun `studio 3d painting layers, wet smudge molhador, uv projections, merge, shader outline and coop sync work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (cubeUri, _) = Mesh3DImporter.createBuiltInSceneAsset(BuiltIn3DPrimitive.CUBE)
        val cubeMesh = Mesh3DImporter.getOrLoadMesh(cubeUri)!!
        val verts = cubeMesh.subMeshes.first().vertices

        // 1. UV Projection unwrapping modes
        for (proj in listOf(UvUnwrapProjection.PLANAR, UvUnwrapProjection.BOX, UvUnwrapProjection.ESFERICO, UvUnwrapProjection.CILINDRICO)) {
            val gen = Studio3DPaintEngine.generateUVsForProjection(
                vertices = verts,
                existingUvs = cubeMesh.subMeshes.first().uvs,
                slot = ObjectUVMapSlot(projection = proj, scaleU = 1f, scaleV = 1f)
            )
            assertEquals((verts.size / 3) * 2, gen.size)
            assertTrue("Projection $proj must produce valid UV range", gen.any { it in 0.01f..0.99f })
        }

        // 2. Surface ray-triangle picking + Pincel + Molhador + Layer Merge into 1 UV
        val sceneObj = SceneObject3D(
            id = "painted_cube",
            name = "Cubo Pintado",
            meshAssetPath = cubeUri,
            shaderMode = ObjectShaderMode.CARTOON_BRUSH,
            outline = ObjectOutlineSettings(enabled = true, width = 3f, colorArgb = 0xFF00FF00L),
            light = ObjectLightSettings(azimuthDeg = 45f, elevationDeg = 60f, intensity = 1.2f, emissiveGlow = 0.2f),
            nodeGraph = ShaderNodeGraph(enabled = true)
        )
        val pick = Studio3DPaintEngine.pickObjectTriangleUV(
            obj = sceneObj,
            frameIndex = 0,
            autoInterpolate = true,
            screenX = 80f,
            screenY = 60f,
            camYawDeg = 0f,
            camPitchDeg = 0f,
            camRollDeg = 0f,
            camPanX = 0f,
            camPanY = 0f,
            focalPx = 320f,
            vpCx = 80f,
            vpCy = 60f,
            worldUnitScale = 1.0f
        )
        assertNotNull("Center ray must hit 3D cube triangle and yield UV", pick)
        assertTrue(pick!!.u in 0f..1f && pick.v in 0f..1f)

        // Paint bright red stroke on Layer 1
        val baseLayer = ObjectTexturePaintLayer(id = "pl_1", name = "Cor Base", texWidth = 128, texHeight = 128)
        val paintedLayer = Studio3DPaintEngine.paintStrokeSegmentOnLayer(
            layer = baseLayer,
            u0 = 0.15f, v0 = 0.5f,
            u1 = 0.85f, v1 = 0.5f,
            tool = Estudio3DPaintTool.PINCEL,
            colorArgb = 0xFFFF1122L,
            brushSizePx = 16f,
            opacity = 1.0f,
            brushPresetId = 1
        )
        assertFalse(paintedLayer.pixelsBase64.isNullOrBlank())

        // Apply Molhador (wet smudge) drag across the stroke
        val smudgedLayer = Studio3DPaintEngine.paintStrokeSegmentOnLayer(
            layer = paintedLayer,
            u0 = 0.45f, v0 = 0.5f,
            u1 = 0.45f, v1 = 0.72f,
            tool = Estudio3DPaintTool.MOLHADOR,
            colorArgb = 0xFFFF1122L,
            brushSizePx = 18f,
            opacity = 0.9f
        )
        val smudgedBuf = Studio3DPaintEngine.decodePixelsFromBase64(smudgedLayer.pixelsBase64)!!
        val draggedAlpha = (smudgedBuf.pixels[(128 * 0.60f).toInt() * 128 + 58] ushr 24) and 0xFF
        assertTrue("Molhador must pull pigment downward into previously transparent region (alpha=$draggedAlpha)", draggedAlpha > 15)

        // Merge paint layers into single flattened UV
        val objWithLayers = sceneObj.copy(paintLayers = listOf(smudgedLayer))
        val mergedUvB64 = Studio3DPaintEngine.mergePaintLayersIntoSingleUv(objWithLayers, cubeMesh.subMeshes.first(), texSize = 128)
        assertTrue("Merged UV Base64 must not be blank", mergedUvB64.length > 50)

        val bakedObj = objWithLayers.copy(
            uvSlots = listOf(ObjectUVMapSlot(name = "UV Mesclado", textureBase64 = mergedUvB64)),
            paintLayers = emptyList(),
            bakedTextureBase64 = mergedUvB64
        )
        val composite = Studio3DPaintEngine.resolveCompositeObjectTexture(bakedObj, null, 0, 0, 0xFFFFFFFF.toInt())
        assertNotNull("Composite baked texture must resolve cleanly", composite)

        // Render object with CARTOON_BRUSH + ShaderNodeGraph + green silhouette Contorno
        val renderRes = Camera3DEngine.renderSceneObjectsSoftware3D(
            outW = 160,
            outH = 120,
            sceneObjects = listOf(bakedObj),
            frameIndex = 0,
            autoInterpolate = true,
            camYawDeg = 22f,
            camPitchDeg = 14f,
            camRollDeg = 0f,
            camPanX = 0f,
            camPanY = 0f,
            focalPx = 320f,
            vpCx = 80f,
            vpCy = 60f,
            worldUnitScale = 1.0f
        )
        assertNotNull(renderRes)
        val outBmp = renderRes!!.first
        val outPx = IntArray(160 * 120)
        outBmp.getPixels(outPx, 0, 160, 0, 0, 160, 120)
        val greenOutlineCount = outPx.count { px ->
            ((px ushr 24) and 0xFF) > 200 &&
                android.graphics.Color.green(px) > 220 &&
                android.graphics.Color.red(px) < 30 &&
                android.graphics.Color.blue(px) < 30
        }
        assertTrue("Green Contorno silhouette pass must draw border pixels (count=$greenOutlineCount)", greenOutlineCount > 20)
        outBmp.recycle()

        // 3. Multiplayer GZ1 Framing + Camera3D JSON sync roundtrip preserving shaders, outline, lights, nodes & baked UV
        val camSettings = Camera3DSettings(
            isEnabled = true,
            cameraMode = CameraMode.MODE_3D,
            scene3DObjects = listOf(bakedObj)
        )
        val serializedCam = FileImportExportManager.camera3DToJson(camSettings, includeTextures = true).toString()
        assertTrue("Payload > 768 bytes should trigger GZ1 line compression", serializedCam.length > 768)
        val framed = MultiplayerStudioEngine.encodeFrameLine(serializedCam)
        assertTrue("encodeFrameLine must apply GZ1: compression for large payloads", framed.startsWith("GZ1:"))
        assertEquals(framed, MultiplayerStudioEngine.encodeFrameLine(framed)) // idempotent
        assertEquals(serializedCam, MultiplayerStudioEngine.decodeFrameLine(framed))

        val parsedCam = FileImportExportManager.jsonToCamera3D(org.json.JSONObject(serializedCam), 1280, 720)
        val parsedObj = parsedCam.scene3DObjects.first()
        assertEquals(ObjectShaderMode.CARTOON_BRUSH, parsedObj.shaderMode)
        assertTrue(parsedObj.outline.enabled)
        assertTrue(parsedObj.nodeGraph.enabled)
        assertEquals(mergedUvB64, parsedObj.bakedTextureBase64)

        // 4. Negative off-canvas stroke multiplayer JSON roundtrip
        val negStroke = DrawingStroke(
            tool = ToolType.BRUSH,
            color = 0xFF112233L,
            brushSize = 12f,
            opacity = 0.85f,
            points = listOf(StrokePoint(-320f, -150f, 0.7f), StrokePoint(-10f, 20f, 0.9f))
        )
        val engine = MultiplayerStudioEngine(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        val parsedStroke = engine.parseStrokeFromJson(engine.strokeToJson(negStroke))
        assertEquals(-320f, parsedStroke.points[0].x, 0.5f)
        assertEquals(-150f, parsedStroke.points[0].y, 0.5f)
        assertNotNull(context)
    }
}
