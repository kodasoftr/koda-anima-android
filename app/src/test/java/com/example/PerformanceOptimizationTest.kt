package com.example

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.engine.FileImportExportManager
import com.example.engine.GifEncoder
import com.example.engine.GpuVendorFamily
import com.example.engine.HardwareCapabilitiesEngine
import com.example.engine.KodanimaBitmapPool
import com.example.engine.KodanimaGraphicsEngine
import com.example.model.AnimationProject
import com.example.model.DrawingStroke
import com.example.model.Frame
import com.example.model.Layer
import com.example.model.StrokePoint
import com.example.model.ToolType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceOptimizationTest {

    @After
    fun tearDown() {
        KodanimaBitmapPool.clear()
    }

    @Test
    fun `hardware capabilities classifies Mali-G57 and major mobile GPUs accurately`() {
        assertEquals(
            GpuVendorFamily.MALI_VALHALL,
            HardwareCapabilitiesEngine.classifyGpuFamily("Mali-G57 MC2", "ARM")
        )
        assertEquals(
            GpuVendorFamily.MALI_VALHALL,
            HardwareCapabilitiesEngine.classifyGpuFamily("Mali-G78 MP14", "ARM")
        )
        assertEquals(
            GpuVendorFamily.MALI_BIFROST_MIDGARD,
            HardwareCapabilitiesEngine.classifyGpuFamily("Mali-G52", "ARM")
        )
        assertEquals(
            GpuVendorFamily.IMMORTALIS,
            HardwareCapabilitiesEngine.classifyGpuFamily("Immortalis-G715", "ARM")
        )
        assertEquals(
            GpuVendorFamily.ADRENO,
            HardwareCapabilitiesEngine.classifyGpuFamily("Adreno (TM) 740", "Qualcomm")
        )
        assertEquals(
            GpuVendorFamily.POWERVR,
            HardwareCapabilitiesEngine.classifyGpuFamily("PowerVR Rogue GE8320", "Imagination Technologies")
        )
        assertEquals(
            GpuVendorFamily.XCLIPSE,
            HardwareCapabilitiesEngine.classifyGpuFamily("Samsung Xclipse 920", "Samsung")
        )
        assertEquals(
            GpuVendorFamily.SOFTWARE_OR_EMU,
            HardwareCapabilitiesEngine.classifyGpuFamily("Android Emulator OpenGL ES Translator", "Google")
        )
    }

    @Test
    fun `hardware capabilities initializes cleanly on context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HardwareCapabilitiesEngine.initialize(context)
        val profile = HardwareCapabilitiesEngine.currentProfile
        assertTrue(profile.cpuCoreCount >= 1)
        assertTrue(profile.bitmapPoolMaxBytes >= 4 * 1024 * 1024)
        assertTrue(profile.maxUndoHistory >= 12)
    }

    @Test
    fun `bitmap pool reuses released mutable bitmap and respects trimMemory`() {
        KodanimaBitmapPool.clear()
        KodanimaBitmapPool.configureMaxBytes(16 * 1024 * 1024)

        val bmp1 = KodanimaBitmapPool.acquire(64, 64, Bitmap.Config.ARGB_8888, Color.WHITE)
        assertEquals(Color.WHITE, bmp1.getPixel(10, 10))
        KodanimaBitmapPool.release(bmp1)

        val bmp2 = KodanimaBitmapPool.acquire(64, 64, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)
        assertSame(bmp1, bmp2)
        assertEquals(Color.TRANSPARENT, bmp2.getPixel(10, 10))

        KodanimaBitmapPool.release(bmp2)
        @Suppress("DEPRECATION")
        KodanimaBitmapPool.trimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        assertTrue(bmp2.isRecycled)
    }

    @Test
    fun `fastHashNoise is deterministic and strictly bounded in zero to one`() {
        for (i in 0 until 200) {
            val n1 = KodanimaGraphicsEngine.fastHashNoise(i * 1.5f, i * 2.25f, 42)
            val n2 = KodanimaGraphicsEngine.fastHashNoise(i * 1.5f, i * 2.25f, 42)
            assertEquals(n1, n2, 0.0f)
            assertTrue("Noise out of range: $n1", n1 in 0.0f..1.0f && n1 < 1.0f)
        }
    }

    @Test
    fun `createFloodFillSpans isolates enclosed interior region without leaking`() {
        val bmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        // Draw a 10x10 black hollow border from (5,5) to (14,14)
        for (x in 5..14) {
            bmp.setPixel(x, 5, Color.BLACK)
            bmp.setPixel(x, 14, Color.BLACK)
        }
        for (y in 5..14) {
            bmp.setPixel(5, y, Color.BLACK)
            bmp.setPixel(14, y, Color.BLACK)
        }

        val spans = KodanimaGraphicsEngine.createFloodFillSpans(bmp, 9, 9, tolerance = 10)
        assertTrue(spans.isNotEmpty())
        // Interior is 8x8 -> 8 horizontal spans (16 StrokePoint endpoints: start & end)
        assertEquals(16, spans.size)
        assertTrue(spans.all { it.x in 6f..13f && it.y in 6f..13f })
        bmp.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `drawLayerToCanvas isolates eraser clear inside layer without wiping background`() {
        val output = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        output.eraseColor(Color.BLUE)
        val canvas = Canvas(output)

        val paintStroke = DrawingStroke(
            id = "s1",
            tool = ToolType.BRUSH,
            color = 0xFFFF0000L, // Red
            brushSize = 16f,
            opacity = 1f,
            brushPresetId = 0,
            points = listOf(StrokePoint(5f, 20f, 1f), StrokePoint(35f, 20f, 1f)),
            minX = 5f,
            minY = 20f,
            maxX = 35f,
            maxY = 20f
        )

        val eraserStroke = DrawingStroke(
            id = "s2",
            tool = ToolType.ERASER,
            color = 0xFFFFFFFFL,
            brushSize = 10f,
            opacity = 1f,
            brushPresetId = 0,
            points = listOf(StrokePoint(20f, 10f, 1f), StrokePoint(20f, 30f, 1f)),
            minX = 20f,
            minY = 10f,
            maxX = 20f,
            maxY = 30f
        )

        val layer = Layer(
            id = "l1",
            name = "Layer 1",
            strokes = listOf(paintStroke, eraserStroke)
        )

        KodanimaGraphicsEngine.drawLayerToCanvas(layer, canvas, 40, 40)

        // Left side of red line remains red; erased center reveals underlying blue background (not black!)
        assertEquals(Color.RED, output.getPixel(8, 20))
        assertEquals(Color.BLUE, output.getPixel(20, 20))
        output.recycle()
    }

    @Test
    fun `json roundtrip preserves brushPresetId and recomputes stroke bounds`() {
        val stroke = DrawingStroke(
            id = "st-brush-7",
            tool = ToolType.BRUSH,
            color = 0xFF112233L,
            brushSize = 12f,
            opacity = 0.85f,
            brushPresetId = 7,
            points = listOf(
                StrokePoint(12f, 24f, 0.5f),
                StrokePoint(96f, 140f, 0.9f)
            )
        )
        val project = AnimationProject(
            name = "Preset Bounds Test",
            frames = listOf(
                Frame(
                    layers = listOf(Layer(name = "Camada 1", strokes = listOf(stroke)))
                )
            )
        )

        val json = FileImportExportManager.projectToJson(project)
        val parsed = FileImportExportManager.projectFromJson(json)

        val parsedStroke = parsed.frames.first().layers.first().strokes.first()
        assertEquals(7, parsedStroke.brushPresetId)
        assertEquals(12f, parsedStroke.minX, 0.01f)
        assertEquals(24f, parsedStroke.minY, 0.01f)
        assertEquals(96f, parsedStroke.maxX, 0.01f)
        assertEquals(140f, parsedStroke.maxY, 0.01f)
        assertNotEquals(Float.MAX_VALUE, parsedStroke.minX)
    }

    @Test
    fun `gif encoder encodeFramesWithHolds produces valid GIF89a header`() {
        val f1 = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val f2 = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }

        val bytes = GifEncoder().encodeFramesWithHolds(listOf(f1 to 2, f2 to 1), fps = 12)
        f1.recycle()
        f2.recycle()

        assertTrue(bytes.size > 20)
        val header = String(bytes, 0, 6, Charsets.US_ASCII)
        assertEquals("GIF89a", header)
        assertEquals(0x3B.toByte(), bytes.last())
    }

    @Test
    fun `isOwnRoom strictly excludes local user room by hostId roomId or localIp`() {
        val localIps = setOf("192.168.1.42", "10.0.0.5")

        // Matches by host ID
        assertTrue(
            com.example.engine.MultiplayerStudioEngine.isOwnRoom(
                roomHostId = "KD-1234",
                roomId = "KD-ROOM-1",
                roomHostIp = "192.168.1.99",
                localUserId = "kd-1234",
                localRoomId = null,
                localIps = localIps
            )
        )
        // Matches by room ID
        assertTrue(
            com.example.engine.MultiplayerStudioEngine.isOwnRoom(
                roomHostId = "KD-OTHER",
                roomId = "KD-SELF-88",
                roomHostIp = "192.168.1.99",
                localUserId = "KD-1234",
                localRoomId = "KD-SELF-88",
                localIps = localIps
            )
        )
        // Matches by local interface IPv4
        assertTrue(
            com.example.engine.MultiplayerStudioEngine.isOwnRoom(
                roomHostId = "KD-OTHER",
                roomId = "KD-ROOM-2",
                roomHostIp = "192.168.1.42",
                localUserId = "KD-1234",
                localRoomId = null,
                localIps = localIps
            )
        )
        // Remote friend's room MUST NOT be excluded
        assertTrue(
            !com.example.engine.MultiplayerStudioEngine.isOwnRoom(
                roomHostId = "KD-FRIEND",
                roomId = "KD-FRIEND-55",
                roomHostIp = "192.168.1.77",
                localUserId = "KD-1234",
                localRoomId = "KD-MYROOM-11",
                localIps = localIps
            )
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `avatar transfer helper encodes decodes and persists peer avatar across devices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val srcBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val out = java.io.ByteArrayOutputStream()
        srcBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        srcBmp.recycle()
        val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)

        val savedPath = com.example.engine.AvatarTransferHelper.savePeerAvatarFromBase64(context, "KD-PEER-77", b64)
        assertTrue(!savedPath.isNullOrBlank())

        val decoded = com.example.engine.AvatarTransferHelper.decodeAvatarBitmap(context, null, b64)
        assertTrue(decoded != null && decoded.width > 0 && decoded.height > 0)
        decoded?.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `quadratic bezier smoothing subdivides sparse points and renders smooth curved stroke`() {
        val sparseCorner = listOf(
            StrokePoint(10f, 50f, 0.7f),
            StrokePoint(50f, 10f, 0.9f),
            StrokePoint(90f, 50f, 0.8f)
        )
        val smoothed = KodanimaGraphicsEngine.computeSmoothedPolyline(sparseCorner)
        assertTrue("Expected smoothed point count > 3, was ${smoothed.size}", smoothed.size > 6)

        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val stroke = DrawingStroke(
            id = "curve-test",
            tool = ToolType.PEN,
            color = 0xFF000000L,
            brushSize = 6f,
            opacity = 1f,
            brushPresetId = 3,
            points = sparseCorner
        )
        KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, canvas)

        // Curve midpoint arches below sharp apex (50, 10) around y=20..30 smoothly
        var foundInkNearCurve = false
        for (y in 18..34) {
            if (bmp.getPixel(50, y) != Color.WHITE) {
                foundInkNearCurve = true
                break
            }
        }
        assertTrue("Smoothed curve should rasterize smoothly along Bezier midpoint arc", foundInkNearCurve)
        bmp.recycle()
    }

    @Test
    fun `buildSubnetCandidateIps covers dual-band 24 bit LAN while excluding self IP`() {
        val candidates = com.example.engine.MultiplayerStudioEngine.buildSubnetCandidateIps("192.168.15.42")
        assertEquals(253, candidates.size)
        assertTrue(candidates.contains("192.168.15.1"))
        assertTrue(candidates.contains("192.168.15.254"))
        assertTrue(!candidates.contains("192.168.15.42"))
    }

    @Test
    fun `user account and network transport preferences commit synchronously and persist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val acc = com.example.model.UserAccount(
            id = "KD-9988",
            name = "Gabriel Studio",
            avatarUri = "file:///data/user/0/com.example/files/avatars/my_profile_avatar.jpg",
            avatarBase64 = "AAECAw==",
            avatarColor = 0xFFFF7A00L
        )
        com.example.engine.PreferencesStorageManager.saveUserAccount(context, acc)
        val loadedAcc = com.example.engine.PreferencesStorageManager.loadUserAccount(context)
        assertEquals("KD-9988", loadedAcc.id)
        assertEquals("Gabriel Studio", loadedAcc.name)
        assertEquals("AAECAw==", loadedAcc.avatarBase64)
        assertEquals(0xFFFF7A00L, loadedAcc.avatarColor)

        val prefs = com.example.model.AppPreferences(
            networkTransportMode = com.example.model.NetworkTransportMode.BLUETOOTH,
            hasPromptedInitialPermissions = true
        )
        com.example.engine.PreferencesStorageManager.savePreferences(context, prefs)
        val loadedPrefs = com.example.engine.PreferencesStorageManager.loadPreferences(context)
        assertEquals(com.example.model.NetworkTransportMode.BLUETOOTH, loadedPrefs.networkTransportMode)
        assertTrue(loadedPrefs.hasPromptedInitialPermissions)
    }

    @Test
    fun `compact projectToJson outputs single-line NDJSON safe payload and roundtrips`() {
        val project = AnimationProject(name = "Coop Compact")
        val compact = FileImportExportManager.projectToJson(project, compact = true)
        assertTrue("Compact JSON must not contain newlines for NDJSON stream safety", !compact.contains("\n"))
        val decoded = FileImportExportManager.projectFromJson(compact)
        assertEquals("Coop Compact", decoded.name)
    }

    @Test
    fun `initial runtime permissions comply with Google Play and omit broad storage`() {
        val perms = com.example.engine.PermissionHelper.getInitialRuntimePermissions().toSet()
        assertTrue(perms.contains(android.Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(perms.contains(android.Manifest.permission.NEARBY_WIFI_DEVICES))
        assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(perms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(!perms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertTrue(!perms.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertTrue(!perms.contains("android.permission.READ_MEDIA_IMAGES"))
        assertTrue(!perms.contains("android.permission.READ_MEDIA_VIDEO"))
    }

    @Test
    fun `application disables cloud backup for clean uninstall guarantee`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = context.applicationInfo.flags
        assertEquals(0, flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `all 12 native Krita presets render smoothly and BrushDynamicParams roundtrips`() {
        val presets = com.example.model.BrushPreset.ALL_12_PRESETS
        assertEquals(12, presets.size)
        assertEquals(12, presets.map { it.id }.toSet().size)

        val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        presets.forEach { preset ->
            bmp.eraseColor(Color.WHITE)
            val compact = preset.dynamicParams.toCompactJson()
            val decoded = com.example.model.BrushDynamicParams.fromJsonOrNull(compact)
            assertTrue("Dynamic params failed roundtrip for ${preset.namePt}", decoded != null)
            assertEquals(preset.engineKind, decoded?.engineKind)

            val stroke = DrawingStroke(
                id = "krita-${preset.id}",
                tool = preset.toolType,
                color = 0xFF1E88E5L,
                brushSize = preset.defaultSize,
                opacity = preset.defaultOpacity,
                brushPresetId = preset.id,
                customBrushParams = compact,
                points = listOf(
                    StrokePoint(10f, 20f, 0.5f),
                    StrokePoint(40f, 55f, 0.9f),
                    StrokePoint(70f, 25f, 0.7f)
                )
            )
            KodanimaGraphicsEngine.drawStrokeToCanvas(stroke, canvas)
        }
        bmp.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `KritaBrushImporter parses kpp PNG preset chunk and bundle archive`() {
        // Build a minimal valid 8x8 PNG with a tEXt "preset" Krita XML chunk
        val baseBmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val baseOut = java.io.ByteArrayOutputStream()
        baseBmp.compress(Bitmap.CompressFormat.PNG, 100, baseOut)
        baseBmp.recycle()
        val pngBytes = baseOut.toByteArray()

        // Inject tEXt chunk right before trailing IEND (last 12 bytes)
        val xmlPayload = """<Preset name="Krita Neon GPen" paintopid="paintbrush"><param name="Diameter">18</param><param name="OpacityValue">0.95</param></Preset>"""
        val textData = "preset".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) + xmlPayload.toByteArray(Charsets.UTF_8)
        val crc = java.util.zip.CRC32().apply {
            update("tEXt".toByteArray(Charsets.US_ASCII))
            update(textData)
        }.value

        val kppOut = java.io.ByteArrayOutputStream()
        kppOut.write(pngBytes, 0, pngBytes.size - 12)
        kppOut.write(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(textData.size).array())
        kppOut.write("tEXt".toByteArray(Charsets.US_ASCII))
        kppOut.write(textData)
        kppOut.write(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(crc.toInt()).array())
        kppOut.write(pngBytes, pngBytes.size - 12, 12)
        val kppBytes = kppOut.toByteArray()

        val parsedKpp = com.example.engine.KritaBrushImporter.parseKppBytes(kppBytes, "neon_ink.kpp", presetId = 1005)
        assertTrue(parsedKpp != null)
        assertEquals(1005, parsedKpp!!.id)
        assertEquals("Krita Neon GPen", parsedKpp.namePt)
        assertTrue(parsedKpp.isImportedKrita)
        assertEquals(com.example.model.KritaBrushEngineKind.INK_GPEN, parsedKpp.engineKind)

        // Wrap the .kpp inside a .bundle ZIP archive and parse bundle
        val zipOut = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("paintoppresets/neon_ink.kpp"))
            zos.write(kppBytes)
            zos.closeEntry()
        }
        val bundlePresets = com.example.engine.KritaBrushImporter.parseBundleBytes(zipOut.toByteArray(), startingId = 1010)
        assertEquals(1, bundlePresets.size)
        assertEquals(1010, bundlePresets.first().id)
        assertEquals("Krita Neon GPen", bundlePresets.first().namePt)
    }

    @Test
    fun `MultiplayerStudioEngine preserves customBrushParams across LAN stroke JSON serialization`() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val engine = com.example.engine.MultiplayerStudioEngine(scope)
        val paramsJson = com.example.model.BrushDynamicParams(
            engineKind = com.example.model.KritaBrushEngineKind.CHARCOAL_SOFT,
            hardness = 0.45f,
            grainAmount = 0.78f
        ).toCompactJson()
        val stroke = DrawingStroke(
            id = "lan-1",
            tool = ToolType.BRUSH,
            color = 0xFFFF5500L,
            brushSize = 14f,
            opacity = 0.8f,
            brushPresetId = 6,
            customBrushParams = paramsJson,
            points = listOf(StrokePoint(10f, 12f, 0.6f), StrokePoint(45f, 60f, 0.9f))
        )
        val jsonObj = engine.strokeToJson(stroke)
        val decoded = engine.parseStrokeFromJson(jsonObj)
        assertEquals(stroke.brushPresetId, decoded.brushPresetId)
        assertEquals(paramsJson, decoded.customBrushParams)
        assertEquals(2, decoded.points.size)
    }

    @Test
    fun `collaborator canvas HUD preference persists cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hiddenPrefs = com.example.model.AppPreferences(showCanvasCollaboratorsHud = false)
        com.example.engine.PreferencesStorageManager.savePreferences(context, hiddenPrefs)
        val loaded = com.example.engine.PreferencesStorageManager.loadPreferences(context)
        assertEquals(false, loaded.showCanvasCollaboratorsHud)
    }

    @Test
    fun `LAN visitor client never saves host project locally on save close or exit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Clean projects directory first
        val projDir = com.example.engine.ProjectCompressionManager.getProjectsDirectory(context, "")
        projDir.listFiles()?.forEach { it.deleteRecursively() }

        val vm = com.example.viewmodel.KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.joinRoom(
            com.example.model.RemoteRoomInfo(
                roomId = "KD-HOST-99",
                roomName = "Projeto Exclusivo do Dono",
                hostId = "KD-HOST",
                hostName = "Dono da Casa",
                hostIp = "127.0.0.1",
                hostPort = 59999
            )
        )
        assertEquals(com.example.model.MultiplayerRole.CLIENT, vm.uiState.value.multiplayerRole)

        vm.saveCurrentProject(context)
        vm.closeAndSaveCurrentProject(context)
        vm.leaveRemoteSessionAsVisitor(context)

        val savedOnVisitor = com.example.engine.ProjectCompressionManager.listAllSavedProjects(context, "")
        assertEquals(0, savedOnVisitor.size)
        assertEquals(com.example.model.MultiplayerRole.NONE, vm.uiState.value.multiplayerRole)
    }

    @Test
    fun `per-user undo and redo preserves collaborator strokes and supports remote undo redo ops`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = com.example.viewmodel.KodanimaViewModel()
        vm.initializeWithContext(context)
        vm.loadNewEmptyProject(
            "Coop Undo Test",
            com.example.model.ProjectResolution.PRESETS[0],
            12
        )
        val localAuthorId = vm.uiState.value.userAccount.id
        val layerIdx = vm.uiState.value.activeLayerIndex

        // 1. Local user draws stroke A
        val localStroke = DrawingStroke(
            id = "local-stroke-1",
            tool = ToolType.PEN,
            color = 0xFF00E5FFL,
            brushSize = 6f,
            opacity = 1f,
            brushPresetId = 3,
            points = listOf(StrokePoint(10f, 10f, 0.9f), StrokePoint(50f, 50f, 0.9f))
        )
        vm.addStrokeToCurrentLayer(localStroke)

        // 2. Remote collaborator draws stroke B on same frame & layer
        val remoteStroke = DrawingStroke(
            id = "peer-stroke-1",
            authorId = "KD-FRIEND-2",
            tool = ToolType.PEN,
            color = 0xFFFF7A00L,
            brushSize = 6f,
            opacity = 1f,
            brushPresetId = 3,
            points = listOf(StrokePoint(80f, 20f, 0.9f), StrokePoint(120f, 60f, 0.9f))
        )
        vm.receiveRemoteStroke(0, layerIdx, remoteStroke)

        var strokes = vm.uiState.value.project.frames[0].layers[layerIdx].strokes
        assertEquals(2, strokes.size)
        assertEquals(localAuthorId, strokes.first { it.id == "local-stroke-1" }.authorId)

        // 3. Local Undo removes ONLY local-stroke-1; peer-stroke-1 remains untouched
        vm.undo()
        strokes = vm.uiState.value.project.frames[0].layers[layerIdx].strokes
        assertEquals(1, strokes.size)
        assertEquals("peer-stroke-1", strokes[0].id)

        // 4. Local Redo restores local-stroke-1 alongside peer-stroke-1
        vm.redo()
        strokes = vm.uiState.value.project.frames[0].layers[layerIdx].strokes
        assertEquals(2, strokes.size)
        assertTrue(strokes.any { it.id == "local-stroke-1" })
        assertTrue(strokes.any { it.id == "peer-stroke-1" })

        // 5. Remote UNDO_STROKE removes only peer-stroke-1; REDO_STROKE restores it
        vm.applyRemoteUndoRedoOp("UNDO_STROKE", "KD-FRIEND-2", 0, layerIdx, listOf("peer-stroke-1"), emptyList())
        strokes = vm.uiState.value.project.frames[0].layers[layerIdx].strokes
        assertEquals(1, strokes.size)
        assertEquals("local-stroke-1", strokes[0].id)

        vm.applyRemoteUndoRedoOp("REDO_STROKE", "KD-FRIEND-2", 0, layerIdx, emptyList(), listOf(remoteStroke))
        strokes = vm.uiState.value.project.frames[0].layers[layerIdx].strokes
        assertEquals(2, strokes.size)
        assertTrue(strokes.any { it.id == "peer-stroke-1" })
    }

    @Test
    fun `stroke JSON serialization preserves authorId and normalizes brush thickness and pressure`() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val engine = com.example.engine.MultiplayerStudioEngine(scope)
        val stroke = DrawingStroke(
            id = "s-norm-6px",
            authorId = "KD-VISITOR-9",
            tool = ToolType.PEN,
            color = 0xFF222222L,
            brushSize = 6f,
            opacity = 1f,
            brushPresetId = 3,
            points = listOf(StrokePoint(5f, 5f, 0.96f), StrokePoint(40f, 40f, 0.96f))
        )
        val decoded = engine.parseStrokeFromJson(engine.strokeToJson(stroke))
        assertEquals("KD-VISITOR-9", decoded.authorId)
        assertEquals(6f, decoded.brushSize, 0.001f)
        assertEquals(0.96f, decoded.points[0].pressure, 0.001f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `custom cursor Base64 encoder and decoder preserves cursor bitmap for LAN sync`() {
        val src = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(24, 24, Color.CYAN)
            setPixel(25, 24, Color.MAGENTA)
        }
        val b64 = com.example.engine.CursorHelper.encodeCursorBitmapToBase64(src, maxDim = 64)
        src.recycle()
        assertTrue(!b64.isNullOrBlank())

        val decoded = com.example.engine.CursorHelper.decodeCursorFromBase64(b64)
        assertTrue(decoded != null && decoded.width == 48 && decoded.height == 48)
        assertEquals(Color.CYAN, decoded!!.getPixel(24, 24))
        assertEquals(Color.MAGENTA, decoded.getPixel(25, 24))
        decoded.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `bezier pen cubic sampling generates smooth curve and rasterizes on canvas`() {
        val nodes = listOf(
            com.example.model.BezierNode(
                anchorX = 10f,
                anchorY = 80f,
                handleOutX = 20f,
                handleOutY = 10f
            ),
            com.example.model.BezierNode(
                anchorX = 90f,
                anchorY = 80f,
                handleInX = 80f,
                handleInY = 10f
            )
        )
        val openPts = com.example.model.sampleCubicBezierNodesToStrokePoints(nodes, closed = false, samplesPerSegment = 16)
        assertEquals(17, openPts.size)
        assertEquals(10f, openPts.first().x, 0.01f)
        assertEquals(90f, openPts.last().x, 0.01f)
        // Curve peaks upward cleanly between anchors
        assertTrue(openPts[8].y < 50f)

        val closedPts = com.example.model.sampleCubicBezierNodesToStrokePoints(nodes, closed = true, samplesPerSegment = 12)
        assertEquals(25, closedPts.size)

        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val canvas = Canvas(bmp)
        val bezierStroke = DrawingStroke(
            id = "bezier-curve-1",
            tool = ToolType.BEZIER_PEN,
            color = 0xFFFF0000L,
            brushSize = 5f,
            opacity = 1f,
            brushPresetId = 3,
            points = openPts
        )
        KodanimaGraphicsEngine.drawStrokeToCanvas(bezierStroke, canvas)
        val midPt = openPts[8]
        assertNotEquals(Color.WHITE, bmp.getPixel(midPt.x.toInt(), midPt.y.toInt()))
        bmp.recycle()
    }

    @Test
    fun `brush override persistence saves applies and resets cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val targetId = 3
        com.example.engine.PreferencesStorageManager.resetBrushOverride(context, targetId)

        val customParams = com.example.model.BrushDynamicParams(
            engineKind = com.example.model.KritaBrushEngineKind.BRISTLE_DRY,
            flow = 0.72f,
            hardness = 0.65f,
            spacing = 0.14f,
            grainAmount = 0.42f,
            taperStrength = 0.5f,
            bristleCount = 7,
            sizeScale = 1.25f
        )
        val overrides = com.example.engine.PreferencesStorageManager.saveBrushOverride(
            context,
            com.example.model.BrushOverride(
                brushId = targetId,
                defaultSize = 24f,
                defaultOpacity = 0.8f,
                dynamicParams = customParams
            )
        )
        val basePreset = com.example.model.BrushPreset.getFactoryNativePresetById(targetId)!!
        val applied = com.example.model.BrushPreset.applyOverride(basePreset, overrides[targetId])
        assertEquals(24f, applied.defaultSize, 0.01f)
        assertEquals(0.8f, applied.defaultOpacity, 0.01f)
        assertEquals(7, applied.dynamicParams.bristleCount)
        assertEquals(com.example.model.KritaBrushEngineKind.BRISTLE_DRY, applied.engineKind)

        val afterReset = com.example.engine.PreferencesStorageManager.resetBrushOverride(context, targetId)
        assertTrue(!afterReset.containsKey(targetId))
    }

    @Test
    fun `media timeline weighted holdFrames maps timeMs and scrub frames accurately`() {
        val frames = listOf(
            Frame(holdFrames = 2), // ticks 0..1 -> at 10fps: 0..199ms
            Frame(holdFrames = 3), // ticks 2..4 -> at 10fps: 200..499ms
            Frame(holdFrames = 1)  // tick 5    -> at 10fps: 500..599ms
        )
        assertEquals(6, com.example.engine.MediaAssetManager.computeTotalTicks(frames))
        assertEquals(600L, com.example.engine.MediaAssetManager.computeTotalDurationMs(frames, fps = 10))
        assertEquals(0L, com.example.engine.MediaAssetManager.computeTimeMsForFrame(frames, 0, fps = 10))
        assertEquals(200L, com.example.engine.MediaAssetManager.computeTimeMsForFrame(frames, 1, fps = 10))
        assertEquals(500L, com.example.engine.MediaAssetManager.computeTimeMsForFrame(frames, 2, fps = 10))

        assertEquals(0, com.example.engine.MediaAssetManager.findFrameIndexAtTimeMs(frames, 150L, fps = 10))
        assertEquals(1, com.example.engine.MediaAssetManager.findFrameIndexAtTimeMs(frames, 350L, fps = 10))
        assertEquals(2, com.example.engine.MediaAssetManager.findFrameIndexAtTimeMs(frames, 520L, fps = 10))
    }

    @Test
    fun `special media asset offsetX offsetY rotationDegrees and waveformPeaks roundtrip`() {
        val video = com.example.model.SpecialMediaAsset(
            id = "vid-1",
            name = "ref.mp4",
            uriString = "file:///tmp/ref.mp4",
            type = "video",
            opacityPercent = 65,
            scalePercent = 135.0,
            rotationDegrees = -18.5,
            offsetX = 42.0,
            offsetY = -27.5
        )
        val audio = com.example.model.SpecialMediaAsset(
            id = "aud-1",
            name = "lip.m4a",
            uriString = "file:///tmp/lip.m4a",
            type = "audio",
            waveformPeaks = listOf(0.1f, 0.85f, 0.45f)
        )
        val project = AnimationProject(
            name = "Media Sync Roundtrip",
            mediaAssets = listOf(video, audio)
        )
        val json = FileImportExportManager.projectToJson(project)
        val parsed = FileImportExportManager.projectFromJson(json)
        val parsedVid = parsed.mediaAssets.first { it.id == "vid-1" }
        val parsedAud = parsed.mediaAssets.first { it.id == "aud-1" }

        assertEquals(42.0, parsedVid.offsetX, 0.01)
        assertEquals(-27.5, parsedVid.offsetY, 0.01)
        assertEquals(-18.5, parsedVid.rotationDegrees, 0.01)
        assertEquals(135.0, parsedVid.scalePercent, 0.01)
        assertEquals(3, parsedAud.waveformPeaks.size)
        assertEquals(0.85f, parsedAud.waveformPeaks[1], 0.01f)
    }

    @Test
    fun `app version config matches 3_0 release metadata`() {
        assertEquals("3.0", BuildConfig.VERSION_NAME)
        assertEquals(30, BuildConfig.VERSION_CODE)
    }
}
