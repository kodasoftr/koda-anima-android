package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.model.BrushDynamicParams
import com.example.model.BrushPreset
import com.example.model.KritaBrushEngineKind
import com.example.model.ToolType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * Krita .kpp / .bundle / .json Brush Importer & Local Vault Manager.
 *
 * Parses standard Krita PNG-wrapped `.kpp` preset chunks (`tEXt`, `zTXt`, `iTXt` -> "preset" XML),
 * `.bundle` ZIP archives (`paintoppresets/` entries), extracts thumbnail icons,
 * maps Krita `paintopid` + dynamics onto [KritaBrushEngineKind] + [BrushDynamicParams],
 * and persists imported presets in `filesDir/krita_brushes/imported_brushes.json`.
 */
object KritaBrushImporter {

    private const val TAG = "KritaBrushImporter"
    private const val VAULT_DIR = "krita_brushes"
    private const val TIPS_DIR = "tips"
    private const val VAULT_FILE = "imported_brushes.json"
    private const val FACTORY_FILE = "imported_brushes_factory.json"
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun importFromUri(context: Context, uri: Uri, existing: List<BrushPreset>): List<BrushPreset> {
        val rawBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading brush uri: ${e.message}")
            null
        } ?: return emptyList()

        val displayName = resolveDisplayName(context, uri)
        val nextId = ((existing.maxOfOrNull { it.id } ?: 1000).coerceAtLeast(1000)) + 1
        val sharedTips = loadSharedTipMap(context)
        val newlyCollectedTips = HashMap<String, ByteArray>()

        val imported = when {
            isZipSignature(rawBytes) -> parseBundleBytes(
                rawBytes = rawBytes,
                startingId = nextId,
                externalTipsIn = sharedTips,
                collectedTipsOut = newlyCollectedTips
            )
            isPngSignature(rawBytes) -> {
                val kpp = parseKppBytes(rawBytes, displayName, nextId, externalBrushTips = sharedTips)
                if (kpp != null) {
                    listOf(kpp)
                } else {
                    val pngTip = parsePngTipPresetBytes(rawBytes, displayName, nextId)
                    if (pngTip != null) newlyCollectedTips[displayName] = rawBytes
                    listOfNotNull(pngTip)
                }
            }
            isGimpBrushOrHoseSignature(rawBytes) -> {
                newlyCollectedTips[displayName] = rawBytes
                listOfNotNull(
                    parseGbrBytes(rawBytes, displayName, nextId)
                        ?: parseGihPresetBytes(rawBytes, displayName, nextId)
                )
            }
            isGimpPatternSignature(rawBytes) -> {
                newlyCollectedTips[displayName] = rawBytes
                listOfNotNull(parsePatPresetBytes(rawBytes, displayName, nextId))
            }
            isRawXmlPreset(rawBytes) -> listOfNotNull(
                parseRawPresetXmlBytes(rawBytes, displayName, nextId, externalBrushTips = sharedTips)
            )
            isJsonContent(rawBytes) -> parseJsonPresetBytes(rawBytes, nextId)
            displayName.endsWith(".bundle", ignoreCase = true) ||
                displayName.endsWith(".zip", ignoreCase = true) -> parseBundleBytes(
                rawBytes = rawBytes,
                startingId = nextId,
                externalTipsIn = sharedTips,
                collectedTipsOut = newlyCollectedTips
            )
            displayName.endsWith(".gbr", ignoreCase = true) -> {
                newlyCollectedTips[displayName] = rawBytes
                listOfNotNull(parseGbrBytes(rawBytes, displayName, nextId))
            }
            displayName.endsWith(".gih", ignoreCase = true) -> {
                newlyCollectedTips[displayName] = rawBytes
                listOfNotNull(parseGihPresetBytes(rawBytes, displayName, nextId))
            }
            displayName.endsWith(".pat", ignoreCase = true) -> {
                newlyCollectedTips[displayName] = rawBytes
                listOfNotNull(parsePatPresetBytes(rawBytes, displayName, nextId))
            }
            displayName.endsWith(".json", ignoreCase = true) -> parseJsonPresetBytes(rawBytes, nextId)
            displayName.endsWith(".xml", ignoreCase = true) ->
                listOfNotNull(parseRawPresetXmlBytes(rawBytes, displayName, nextId, externalBrushTips = sharedTips))
            else -> listOfNotNull(
                parseKppBytes(rawBytes, displayName, nextId, externalBrushTips = sharedTips)
                    ?: parseRawPresetXmlBytes(rawBytes, displayName, nextId, externalBrushTips = sharedTips)
                    ?: parseGbrBytes(rawBytes, displayName, nextId)?.also { newlyCollectedTips[displayName] = rawBytes }
                    ?: parseGihPresetBytes(rawBytes, displayName, nextId)?.also { newlyCollectedTips[displayName] = rawBytes }
                    ?: parsePatPresetBytes(rawBytes, displayName, nextId)?.also { newlyCollectedTips[displayName] = rawBytes }
                    ?: parsePngTipPresetBytes(rawBytes, displayName, nextId)?.also { newlyCollectedTips[displayName] = rawBytes }
            )
        }

        if (newlyCollectedTips.isNotEmpty()) {
            saveSharedTips(context, newlyCollectedTips)
        }

        val addedPresets = mutableListOf<BrushPreset>()
        if (imported.isNotEmpty()) {
            val updated = existing.toMutableList()
            val factoryList = loadFactorySnapshots(context).toMutableList()
            var runningId = ((updated.maxOfOrNull { it.id } ?: 1000).coerceAtLeast(1000)) + 1
            imported.forEach { rawPreset ->
                var candidateName = rawPreset.namePt.ifBlank { "Brush Krita #$runningId" }
                var suffix = 2
                while (updated.any { it.namePt.equals(candidateName, ignoreCase = true) }) {
                    candidateName = "${rawPreset.namePt} ($suffix)"
                    suffix++
                }
                val finalPreset = rawPreset.copy(
                    id = runningId,
                    namePt = candidateName,
                    shortLabel = candidateName.take(12)
                )
                runningId++
                updated.add(finalPreset)
                factoryList.removeAll { it.id == finalPreset.id }
                factoryList.add(finalPreset)
                addedPresets.add(finalPreset)
            }
            saveImportedBrushes(context, updated)
            saveFactorySnapshots(context, factoryList)
        }
        return addedPresets
    }

    fun parseBundleBytes(
        rawBytes: ByteArray,
        startingId: Int = 1001,
        externalTipsIn: Map<String, ByteArray> = emptyMap(),
        collectedTipsOut: MutableMap<String, ByteArray>? = null
    ): List<BrushPreset> {
        val results = mutableListOf<BrushPreset>()
        var currentId = startingId
        val kppEntries = mutableListOf<Pair<String, ByteArray>>()
        val xmlEntries = mutableListOf<Pair<String, ByteArray>>()
        val jsonEntries = mutableListOf<Pair<String, ByteArray>>()
        val tipEntries = mutableListOf<Pair<String, ByteArray>>()
        val brushTipMap = HashMap<String, ByteArray>(externalTipsIn)

        try {
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        val lower = name.replace('\\', '/').lowercase()
                        try {
                            when {
                                lower.endsWith(".kpp") -> {
                                    kppEntries.add(name to zis.readBytes())
                                }
                                lower.endsWith(".json") -> {
                                    if (!lower.endsWith("manifest.json")) {
                                        jsonEntries.add(name to zis.readBytes())
                                    }
                                }
                                lower.endsWith(".xml") && !lower.endsWith("manifest.xml") -> {
                                    val bytes = zis.readBytes()
                                    if (isRawXmlPreset(bytes)) {
                                        xmlEntries.add(name to bytes)
                                    }
                                }
                                lower.endsWith(".gbr") || lower.endsWith(".gih") || lower.endsWith(".png") || lower.endsWith(".pat") -> {
                                    val bytes = zis.readBytes()
                                    if (!lower.contains("paintoppresets/")) {
                                        indexBrushTipBytes(brushTipMap, lower, bytes)
                                        val tipBase = urlDecodeSafe(name.replace('\\', '/')).substringAfterLast('/')
                                        if (tipBase.isNotEmpty()) {
                                            collectedTipsOut?.put(tipBase, bytes)
                                        }
                                        tipEntries.add(name to bytes)
                                    } else if (isPngSignature(bytes) && extractKritaPresetXmlFromPng(bytes) != null) {
                                        kppEntries.add(name to bytes)
                                    }
                                }
                            }
                        } catch (entryErr: Exception) {
                            Log.w(TAG, "Skipping corrupt ZIP entry '$name': ${entryErr.message}")
                        }
                    }
                    try { zis.closeEntry() } catch (_: Exception) {}
                    entry = zis.nextEntry
                }
            }

            kppEntries.forEach { (name, bytes) ->
                try {
                    val fileName = name.substringAfterLast('/')
                    val parsed = if (isRawXmlPreset(bytes)) {
                        parseRawPresetXmlBytes(bytes, fileName, currentId, brushTipMap)
                    } else {
                        parseKppBytes(
                            rawBytes = bytes,
                            fileName = fileName,
                            presetId = currentId,
                            externalBrushTips = brushTipMap
                        )
                    }
                    if (parsed != null) {
                        results.add(parsed)
                        currentId++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping failing .kpp '$name': ${e.message}")
                }
            }

            xmlEntries.forEach { (name, bytes) ->
                try {
                    val parsed = parseRawPresetXmlBytes(bytes, name.substringAfterLast('/'), currentId, brushTipMap)
                    if (parsed != null) {
                        results.add(parsed)
                        currentId++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping failing .xml preset '$name': ${e.message}")
                }
            }

            jsonEntries.forEach { (name, bytes) ->
                try {
                    val parsedList = parseJsonPresetBytes(bytes, currentId)
                    parsedList.forEach { p ->
                        results.add(p.copy(id = currentId))
                        currentId++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping failing .json preset '$name': ${e.message}")
                }
            }

            // Fallback to standalone .gbr/.gih/.pat/.png tips only if no .kpp/.xml/.json presets were extracted
            if (results.isEmpty()) {
                tipEntries.forEach { (name, bytes) ->
                    try {
                        val fileName = name.substringAfterLast('/')
                        val lower = fileName.lowercase()
                        val parsed = when {
                            lower.endsWith(".gih") -> parseGihPresetBytes(bytes, fileName, currentId)
                            lower.endsWith(".pat") -> parsePatPresetBytes(bytes, fileName, currentId)
                            lower.endsWith(".png") -> parsePngTipPresetBytes(bytes, fileName, currentId)
                            else -> parseGbrBytes(bytes, fileName, currentId)
                        }
                        if (parsed != null) {
                            results.add(parsed)
                            currentId++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping failing tip '$name': ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing .bundle archive: ${e.message}")
        } finally {
            kppEntries.clear()
            xmlEntries.clear()
            jsonEntries.clear()
            tipEntries.clear()
            brushTipMap.clear()
        }
        return results
    }

    private fun indexBrushTipBytes(map: MutableMap<String, ByteArray>, lowerPath: String, bytes: ByteArray) {
        val decodedPath = urlDecodeSafe(lowerPath)
        map[lowerPath] = bytes
        map[decodedPath] = bytes
        val baseName = decodedPath.substringAfterLast('/')
        if (baseName.isNotEmpty()) {
            map[baseName] = bytes
            val noExt = baseName.substringBeforeLast('.')
            if (noExt.isNotEmpty()) {
                map.putIfAbsent(noExt, bytes)
            }
        }
        val afterBrushes = decodedPath.substringAfter("brushes/", "")
        if (afterBrushes.isNotEmpty()) {
            map[afterBrushes] = bytes
        }
        val afterPatterns = decodedPath.substringAfter("patterns/", "")
        if (afterPatterns.isNotEmpty()) {
            map[afterPatterns] = bytes
        }
    }

    fun parseGbrBytes(rawBytes: ByteArray, fileName: String, presetId: Int = 1001): BrushPreset? {
        if (rawBytes.size < 28) return null
        return try {
            val header = ByteBuffer.wrap(rawBytes, 0, 28).order(ByteOrder.BIG_ENDIAN)
            val headerSize = header.int
            val version = header.int
            val width = header.int
            val height = header.int
            val bytesPerPixel = header.int
            val magicInt = header.int
            val magicBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(magicInt).array()
            val magic = String(magicBytes, Charsets.US_ASCII)
            if (magic != "GIMP" || (version != 1 && version != 2) || headerSize < 28 || width <= 0 || height <= 0) {
                return null
            }
            val spacing = if (version == 2) (header.int / 100f).coerceIn(0.04f, 0.8f) else 0.12f
            val nameLen = (headerSize - 28).coerceAtMost(rawBytes.size - 28).coerceAtLeast(0)
            val rawName = if (nameLen > 0) {
                String(rawBytes, 28, nameLen, Charsets.UTF_8).trimEnd('\u0000').trim()
            } else ""
            val fallbackName = fileName.substringAfterLast('/').substringAfterLast('\\')
                .removeSuffix(".gbr").removeSuffix(".GBR")
            val finalName = rawName.ifBlank { fallbackName }.ifBlank { "GIMP Tip #$presetId" }
            val engineKind = mapKritaMetaToEngine("gbr_${bytesPerPixel}bpp", finalName)
            val baseSize = (maxOf(width, height).toFloat() * 0.45f).coerceIn(4f, 44f)
            val tipStamp = extractGbrTipStamp(rawBytes)
            val naturalRatio = (minOf(width, height).toFloat() / maxOf(width, height).toFloat()).coerceIn(0.12f, 1.0f)
            BrushPreset(
                id = presetId,
                namePt = finalName,
                shortLabel = finalName.take(12),
                defaultSize = baseSize,
                defaultOpacity = defaultOpacityFor(engineKind),
                toolType = ToolType.BRUSH,
                textureNamePt = "Ponta .gbr (${width}x${height})",
                textureSymbol = symbolForEngine(engineKind),
                descriptionPt = "Ponta raster importada (.gbr ${width}x${height}px).",
                engineKind = engineKind,
                dynamicParams = BrushDynamicParams(
                    engineKind = engineKind,
                    hardness = defaultHardnessFor(engineKind),
                    spacing = spacing,
                    flow = 0.88f,
                    grainAmount = if (engineKind == KritaBrushEngineKind.CHARCOAL_SOFT || engineKind == KritaBrushEngineKind.CHALK_GRAINY) 0.6f else 0.18f,
                    aspectRatio = if (width != height) naturalRatio else 1.0f,
                    shapeKind = if (tipStamp != null) "predefined" else "circle",
                    tipStampBase64 = tipStamp?.first
                ),
                isImportedKrita = true,
                kritaPaintOpId = "gbr_tip",
                iconBase64 = tipStamp?.first
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parseGihPresetBytes(rawBytes: ByteArray, fileName: String, presetId: Int = 1001): BrushPreset? {
        val tipStamp = extractGihTipStamp(rawBytes) ?: return null
        val fallbackName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .removeSuffix(".gih").removeSuffix(".GIH")
            .ifBlank { "GIMP Hose #$presetId" }
        val engineKind = mapKritaMetaToEngine("gih_hose", fallbackName)
        return BrushPreset(
            id = presetId,
            namePt = fallbackName,
            shortLabel = fallbackName.take(12),
            defaultSize = 20f,
            defaultOpacity = defaultOpacityFor(engineKind),
            toolType = ToolType.BRUSH,
            textureNamePt = "Ponta animada .gih",
            textureSymbol = symbolForEngine(engineKind),
            descriptionPt = "Ponta raster importada (.gih GIMP Image Hose).",
            engineKind = engineKind,
            dynamicParams = BrushDynamicParams(
                engineKind = engineKind,
                hardness = defaultHardnessFor(engineKind),
                spacing = 0.14f,
                flow = 0.88f,
                grainAmount = 0.25f,
                aspectRatio = tipStamp.second,
                angularJitter = 0.35f,
                shapeKind = "predefined",
                tipStampBase64 = tipStamp.first
            ),
            isImportedKrita = true,
            kritaPaintOpId = "gih_tip",
            iconBase64 = tipStamp.first
        )
    }

    fun parsePatPresetBytes(rawBytes: ByteArray, fileName: String, presetId: Int = 1001): BrushPreset? {
        if (rawBytes.size < 24) return null
        return try {
            val header = ByteBuffer.wrap(rawBytes, 0, 24).order(ByteOrder.BIG_ENDIAN)
            val headerSize = header.int
            val version = header.int
            val width = header.int
            val height = header.int
            val bytesPerPixel = header.int
            val magicInt = header.int
            val magic = String(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(magicInt).array(), Charsets.US_ASCII)
            if (magic != "GPAT" || version != 1 || headerSize < 24 || width <= 0 || height <= 0) {
                return null
            }
            val nameLen = (headerSize - 24).coerceAtMost(rawBytes.size - 24).coerceAtLeast(0)
            val rawName = if (nameLen > 0) {
                String(rawBytes, 24, nameLen, Charsets.UTF_8).trimEnd('\u0000').trim()
            } else ""
            val fallbackName = fileName.substringAfterLast('/').substringAfterLast('\\')
                .removeSuffix(".pat").removeSuffix(".PAT")
            val finalName = rawName.ifBlank { fallbackName }.ifBlank { "Padrão .pat #$presetId" }
            val tipStamp = extractPatTipStamp(rawBytes) ?: return null
            val engineKind = mapKritaMetaToEngine("pat_${bytesPerPixel}bpp", finalName)
            val naturalRatio = (minOf(width, height).toFloat() / maxOf(width, height).toFloat()).coerceIn(0.12f, 1.0f)
            BrushPreset(
                id = presetId,
                namePt = finalName,
                shortLabel = finalName.take(12),
                defaultSize = (maxOf(width, height).toFloat() * 0.45f).coerceIn(8f, 44f),
                defaultOpacity = defaultOpacityFor(engineKind),
                toolType = ToolType.BRUSH,
                textureNamePt = "Padrão .pat (${width}x${height})",
                textureSymbol = symbolForEngine(engineKind),
                descriptionPt = "Padrão/textura raster importado (.pat ${width}x${height}px).",
                engineKind = engineKind,
                dynamicParams = BrushDynamicParams(
                    engineKind = engineKind,
                    hardness = defaultHardnessFor(engineKind),
                    spacing = 0.14f,
                    flow = 0.88f,
                    grainAmount = 0.48f,
                    aspectRatio = if (width != height) naturalRatio else 1.0f,
                    shapeKind = "predefined",
                    tipStampBase64 = tipStamp.first
                ),
                isImportedKrita = true,
                kritaPaintOpId = "pat_tip",
                iconBase64 = tipStamp.first
            )
        } catch (_: Exception) {
            null
        }
    }

    fun parsePngTipPresetBytes(rawBytes: ByteArray, fileName: String, presetId: Int = 1001): BrushPreset? {
        if (!isPngSignature(rawBytes)) return null
        val tipStamp = extractPngTipStamp(rawBytes) ?: return null
        val fallbackName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .removeSuffix(".png").removeSuffix(".PNG")
            .removeSuffix(".kpp").removeSuffix(".KPP")
            .ifBlank { "Ponta PNG #$presetId" }
        val engineKind = mapKritaMetaToEngine("png_tip", fallbackName)
        return BrushPreset(
            id = presetId,
            namePt = fallbackName,
            shortLabel = fallbackName.take(12),
            defaultSize = defaultSizeFor(engineKind),
            defaultOpacity = defaultOpacityFor(engineKind),
            toolType = ToolType.BRUSH,
            textureNamePt = "Ponta PNG importada",
            textureSymbol = symbolForEngine(engineKind),
            descriptionPt = "Ponta raster importada (.png).",
            engineKind = engineKind,
            dynamicParams = BrushDynamicParams(
                engineKind = engineKind,
                hardness = defaultHardnessFor(engineKind),
                spacing = 0.12f,
                flow = 0.90f,
                aspectRatio = tipStamp.second,
                shapeKind = "predefined",
                tipStampBase64 = tipStamp.first
            ),
            isImportedKrita = true,
            kritaPaintOpId = "png_tip",
            iconBase64 = encodePngThumbnailBase64(rawBytes) ?: tipStamp.first
        )
    }

    fun parseRawPresetXmlBytes(
        rawBytes: ByteArray,
        fileName: String,
        presetId: Int = 1001,
        externalBrushTips: Map<String, ByteArray> = emptyMap()
    ): BrushPreset? {
        if (!isRawXmlPreset(rawBytes)) return null
        val xmlText = String(rawBytes, Charsets.UTF_8).trimStart('\uFEFF')
        val cleanBaseName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffix(".kpp").removeSuffix(".KPP")
            .removeSuffix(".xml").removeSuffix(".XML")
            .ifBlank { "Brush Krita #$presetId" }
        return buildPresetFromXmlAndThumbnail(
            presetXml = xmlText,
            rawNameFallback = cleanBaseName,
            presetId = presetId,
            thumbnailPngBytes = null,
            externalBrushTips = externalBrushTips
        )
    }

    fun parseKppBytes(
        rawBytes: ByteArray,
        fileName: String,
        presetId: Int = 1001,
        externalBrushTips: Map<String, ByteArray> = emptyMap()
    ): BrushPreset? {
        if (isRawXmlPreset(rawBytes)) {
            return parseRawPresetXmlBytes(rawBytes, fileName, presetId, externalBrushTips)
        }
        if (!isPngSignature(rawBytes)) return null

        val rawXml = extractKritaPresetXmlFromPng(rawBytes)
        val cleanBaseName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffix(".kpp")
            .removeSuffix(".KPP")
            .ifBlank { "Brush Krita #$presetId" }

        return buildPresetFromXmlAndThumbnail(
            presetXml = rawXml,
            rawNameFallback = cleanBaseName,
            presetId = presetId,
            thumbnailPngBytes = rawBytes,
            externalBrushTips = externalBrushTips
        )
    }

    private fun buildPresetFromXmlAndThumbnail(
        presetXml: String?,
        rawNameFallback: String,
        presetId: Int,
        thumbnailPngBytes: ByteArray?,
        externalBrushTips: Map<String, ByteArray>
    ): BrushPreset {
        val cleanOuterXml = presetXml?.replace("<![CDATA[", "")?.replace("]]>", "")?.trimStart('\uFEFF')
        var parsedName = rawNameFallback
        var paintOpId = "paintbrush"
        var parsedSize: Float? = null
        var parsedOpacity: Float? = null
        var parsedFlow: Float? = null
        var parsedSpacing: Float? = null
        var parsedSoftness: Float? = null
        var parsedRatio: Float? = null
        var parsedAngleDeg = 0.0f
        var parsedSpikes = 2
        var parsedShapeKind = "circle"
        var parsedScatter = 0.0f
        var parsedAngularJitter = 0.0f
        var parsedFollowAngle = false
        var parsedSmudgeRate = 0.0f
        var parsedTextureStrength: Float? = null
        var parsedTipBase64: String? = null
        var tipAspectFromImage: Float? = null
        var parsedDualColorOn = false
        var parsedDualColorMode = "gradient"
        var parsedDualColorMix = 0.5f
        var parsedSecondaryColor = 0xFFFFFFFFL
        var parsedInnerDotsOn = false
        var sprayDensity: Float? = null
        var sprayDotSize: Float? = null
        var parsedDualShapeOn = false
        var parsedSecondaryShapeKind = "circle"
        var parsedSecondaryTipBase64: String? = null
        var parsedDualShapeScale = 0.68f
        var parsedDualShapeBlendMode = "combine"

        if (!cleanOuterXml.isNullOrBlank()) {
            extractXmlAttr(cleanOuterXml, "name")?.takeIf { it.isNotBlank() }?.let {
                parsedName = it.trim()
            }
            extractXmlAttr(cleanOuterXml, "paintopid")?.takeIf { it.isNotBlank() }?.let {
                paintOpId = it.trim().lowercase()
            }

            // Decode inner <param name="brush_definition"> carefully without breaking outer quotes
            val brushDefRaw = extractParamBody(cleanOuterXml, "brush_definition") ?: cleanOuterXml
            val brushDefXml = decodeEmbeddedXml(brushDefRaw)
            val fallbackSearchXml = if (brushDefXml === cleanOuterXml) cleanOuterXml else "$brushDefXml\n$cleanOuterXml"

            val maskRadius = extractMaskGeneratorAttr(brushDefXml, "radius")
                ?: extractMaskGeneratorAttr(cleanOuterXml, "radius")
            val brushAttrDiameter = extractBrushTagAttrFloat(brushDefXml, "diameter")
                ?: extractBrushTagAttrFloat(cleanOuterXml, "diameter")
            val brushAttrScale = extractBrushTagAttrFloat(brushDefXml, "scale")
                ?: extractBrushTagAttrFloat(cleanOuterXml, "scale")

            parsedSize = extractParamFloat(cleanOuterXml, "Diameter")
                ?: brushAttrDiameter
                ?: maskRadius?.let { it * 2f }
                ?: extractParamFloat(cleanOuterXml, "Size")
                ?: brushAttrScale?.let { (it * 24f).coerceIn(4f, 64f) }

            val rawOpacity = extractParamFloat(cleanOuterXml, "OpacityValue")
                ?: extractParamFloat(cleanOuterXml, "opacity")
            parsedOpacity = rawOpacity?.let { v -> if (v > 1.05f) (v / 100f).coerceIn(0.05f, 1.0f) else v.coerceIn(0.05f, 1.0f) }
            val rawFlow = extractParamFloat(cleanOuterXml, "FlowValue")
                ?: extractParamFloat(cleanOuterXml, "flow")
            parsedFlow = rawFlow?.let { v -> if (v > 1.05f) (v / 100f).coerceIn(0.05f, 1.0f) else v.coerceIn(0.05f, 1.0f) }
            val explicitSpacing = extractParamFloat(cleanOuterXml, "Spacing")
                ?: extractParamFloat(cleanOuterXml, "spacing")
                ?: extractBrushTagAttrFloat(brushDefXml, "spacing")
            val autoSpacingCoeff = extractBrushTagAttrFloat(brushDefXml, "AutoSpacingCoeff")
                ?: extractBrushTagAttrFloat(cleanOuterXml, "AutoSpacingCoeff")
            val useAutoSpacing = extractBrushTagAttrStr(brushDefXml, "useAutoSpacing")
                ?.let { it == "1" || it.equals("true", ignoreCase = true) }
                ?: (explicitSpacing == null && autoSpacingCoeff != null)
            parsedSpacing = if (useAutoSpacing && autoSpacingCoeff != null) {
                (autoSpacingCoeff * 0.14f).coerceIn(0.03f, 0.35f)
            } else {
                explicitSpacing ?: autoSpacingCoeff?.let { (it * 0.14f).coerceIn(0.03f, 0.35f) }
            }

            val rawFade = extractMaskGeneratorAttr(brushDefXml, "fade")
                ?: extractMaskGeneratorAttr(cleanOuterXml, "fade")
            parsedSoftness = extractParamFloat(cleanOuterXml, "softness")
                ?: extractParamFloat(cleanOuterXml, "Softness")
                ?: extractMaskGeneratorAttr(brushDefXml, "softness")
                ?: extractMaskGeneratorAttr(cleanOuterXml, "softness")
                ?: rawFade?.let { fade -> (1f - fade.coerceIn(0f, 1f)) * 1.4f }
            parsedRatio = extractMaskGeneratorAttr(brushDefXml, "ratio")
                ?: extractMaskGeneratorAttr(cleanOuterXml, "ratio")
            val maskSpikes = extractMaskGeneratorAttr(brushDefXml, "spikes")
                ?: extractMaskGeneratorAttr(cleanOuterXml, "spikes")
            if (maskSpikes != null) {
                parsedSpikes = maskSpikes.toInt().coerceIn(2, 12)
            }

            val rawShapeType = extractMaskGeneratorAttrStr(brushDefXml, "type")
                ?: extractMaskGeneratorAttrStr(cleanOuterXml, "type")
            val maskKindRaw = extractMaskGeneratorAttrStr(brushDefXml, "id")
                ?: extractMaskGeneratorAttrStr(cleanOuterXml, "id")
            parsedShapeKind = when {
                rawShapeType?.equals("rect", ignoreCase = true) == true ||
                    maskKindRaw?.contains("rect", ignoreCase = true) == true -> "rect"
                rawShapeType?.equals("gauss", ignoreCase = true) == true ||
                    maskKindRaw?.contains("gauss", ignoreCase = true) == true -> "gauss"
                rawShapeType?.equals("soft", ignoreCase = true) == true ||
                    maskKindRaw?.contains("soft", ignoreCase = true) == true -> "soft"
                else -> "circle"
            }

            // Rotation angle (Krita Brush/MaskGenerator stores radians in [0..6.283] or degrees)
            val rawAngle = extractBrushTagAttrFloat(brushDefXml, "angle")
                ?: extractMaskGeneratorAttr(brushDefXml, "angle")
                ?: extractParamFloat(cleanOuterXml, "angle")
            if (rawAngle != null && kotlin.math.abs(rawAngle) > 0.001f) {
                var deg = if (kotlin.math.abs(rawAngle) <= 6.3f) {
                    Math.toDegrees(rawAngle.toDouble()).toFloat()
                } else {
                    rawAngle
                }
                while (deg > 180f) deg -= 360f
                while (deg < -180f) deg += 360f
                parsedAngleDeg = deg
            }

            // Link predefined raster brush tip from .bundle or shared tip vault (`<Brush filename="..."/>`)
            val tipFilename = extractBrushTagAttrStr(brushDefXml, "filename")
                ?: extractBrushTagAttrStr(cleanOuterXml, "filename")
                ?: extractBrushTagAttrStr(fallbackSearchXml, "filename")
            if (!tipFilename.isNullOrBlank() && externalBrushTips.isNotEmpty()) {
                val resolvedBytes = findBrushTipBytes(tipFilename, externalBrushTips)
                if (resolvedBytes != null) {
                    val tipResult = extractTipStampBase64(resolvedBytes, tipFilename)
                    if (tipResult != null) {
                        parsedTipBase64 = tipResult.first
                        tipAspectFromImage = tipResult.second
                        parsedShapeKind = "predefined"
                    }
                }
            }

            // Dynamics: Scatter, Rotation sensors, Smudge/Wet, Texture/Pattern
            val scatterBool = extractParamBool(cleanOuterXml, "Scatter") == true ||
                extractParamBool(cleanOuterXml, "PressureScatter") == true
            val scatterVal = extractParamFloat(cleanOuterXml, "ScatterValue")
                ?: extractParamFloat(cleanOuterXml, "Scatter")
                ?: extractMaskGeneratorAttr(brushDefXml, "randomness")
            parsedScatter = when {
                scatterVal != null && scatterVal > 0.02f -> scatterVal.coerceIn(0f, 1.8f)
                scatterBool -> 0.45f
                else -> 0f
            }

            val rotParamFloat = extractParamFloat(cleanOuterXml, "RotationValue")
                ?: extractParamFloat(cleanOuterXml, "Rotation")
            val rotEnabled = extractParamBool(cleanOuterXml, "Rotation") == true ||
                cleanOuterXml.contains("RotationOption", ignoreCase = true) ||
                (rotParamFloat != null && rotParamFloat > 0.02f)
            if (rotEnabled) {
                if (cleanOuterXml.contains("drawing-angle", ignoreCase = true) ||
                    cleanOuterXml.contains("DrawingAngle", ignoreCase = true)
                ) {
                    parsedFollowAngle = true
                }
                if (cleanOuterXml.contains("fuzzy", ignoreCase = true) ||
                    cleanOuterXml.contains("FuzzyPerDab", ignoreCase = true) ||
                    (rotParamFloat != null && rotParamFloat > 0.02f)
                ) {
                    parsedAngularJitter = (rotParamFloat ?: 0.65f).coerceIn(0.15f, 1.0f)
                }
            }

            val smudgeVal = extractParamFloat(cleanOuterXml, "SmudgeRate")
                ?: extractParamFloat(cleanOuterXml, "SmudgeRateValue")
                ?: extractParamFloat(cleanOuterXml, "ColorRateValue")
            if (smudgeVal != null && smudgeVal > 0.02f) {
                parsedSmudgeRate = smudgeVal.coerceIn(0f, 1.0f)
            } else if ("smudge" in paintOpId) {
                parsedSmudgeRate = 0.55f
            }

            val texEnabled = extractParamBool(cleanOuterXml, "Texture/Pattern/Enabled") == true ||
                cleanOuterXml.contains("Texture/Pattern/PatternFileName", ignoreCase = true)
            if (texEnabled) {
                parsedTextureStrength = (
                    extractParamFloat(cleanOuterXml, "Texture/Pattern/Strength")
                        ?: 0.55f
                    ).coerceIn(0.15f, 1.0f)
            }

            // Dual Color / ColorSource parsing
            val colorSourceType = extractParamBody(cleanOuterXml, "ColorSource/Type")
                ?.trim()?.lowercase()
                ?: extractXmlAttr(cleanOuterXml, "colorsource")?.lowercase()
                ?: ""
            val fgBgColor = extractParamBool(cleanOuterXml, "ForegroundBackground") == true ||
                extractParamBool(cleanOuterXml, "Color/ForegroundBackground") == true ||
                colorSourceType.contains("gradient") ||
                colorSourceType.contains("fg_bg") ||
                colorSourceType.contains("mix")
            val mixVal = extractParamFloat(cleanOuterXml, "MixValue")
                ?: extractParamFloat(cleanOuterXml, "Color/MixValue")
                ?: extractParamFloat(cleanOuterXml, "DarkenValue")
            val dualColorHex = extractParamBody(cleanOuterXml, "SecondaryColor")?.trim()
                ?: extractXmlAttr(cleanOuterXml, "secondarycolor")
            parsedDualColorOn = fgBgColor || (mixVal != null && mixVal > 0.02f) || !dualColorHex.isNullOrBlank()
            parsedDualColorMode = when {
                colorSourceType.contains("gradient") -> "gradient"
                colorSourceType.contains("alt") || colorSourceType.contains("hue") -> "alternating"
                colorSourceType.contains("edge") || colorSourceType.contains("darken") -> "edge"
                else -> "gradient"
            }
            parsedDualColorMix = (mixVal ?: 0.5f).coerceIn(0.05f, 1.0f)
            parsedSecondaryColor = parseHexColorOrNull(dualColorHex) ?: 0xFFFFFFFFL

            // Internal special dots / spray / particle parameters
            val sprayParticleCount = extractParamFloat(cleanOuterXml, "sprayArea/particleCount")
                ?: extractParamFloat(cleanOuterXml, "Spray/particleCount")
                ?: extractParamFloat(cleanOuterXml, "Particle/count")
            val sprayEnabled = extractParamBool(cleanOuterXml, "Spray/enabled") == true ||
                extractParamBool(cleanOuterXml, "Particle/enabled") == true ||
                extractParamBool(cleanOuterXml, "InnerDots/enabled") == true ||
                (sprayParticleCount != null && sprayParticleCount >= 1f) ||
                "spray" in paintOpId || "particle" in paintOpId
            sprayDensity = extractParamFloat(cleanOuterXml, "Spray/density")
                ?: extractParamFloat(cleanOuterXml, "BrushTip/density")
                ?: extractParamFloat(cleanOuterXml, "InnerDots/density")
                ?: sprayParticleCount?.let { (it / 24f).coerceIn(0.2f, 1.0f) }
            sprayDotSize = extractParamFloat(cleanOuterXml, "Spray/particleSize")
                ?: extractParamFloat(cleanOuterXml, "InnerDots/size")
            parsedInnerDotsOn = sprayEnabled || (sprayDensity != null && sprayDensity > 0.02f)

            // Masked / Dual Brush (Multi-format tip combination inside the same brush)
            val maskingEnabled = extractParamBool(cleanOuterXml, "MaskingBrush/Enabled") == true ||
                extractParamBool(cleanOuterXml, "DualBrush/Enabled") == true
            val maskDefRaw = extractParamBody(cleanOuterXml, "MaskingBrush/brush_definition")
                ?: extractParamBody(cleanOuterXml, "DualBrush/brush_definition")
            val maskDefXml = maskDefRaw?.let { decodeEmbeddedXml(it) }
            val maskMasterSize = extractParamFloat(cleanOuterXml, "MaskingBrush/MasterSize")
            parsedDualShapeOn = maskingEnabled || !maskDefXml.isNullOrBlank()

            val secSourceXml = maskDefXml ?: run {
                val allBrushTags = Regex("""<Brush\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(fallbackSearchXml).toList()
                val allMaskTags = Regex("""<MaskGenerator\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(fallbackSearchXml).toList()
                buildString {
                    if (allBrushTags.size >= 2) append(allBrushTags[1].value)
                    if (allMaskTags.size >= 2) append(allMaskTags[1].value)
                }.ifBlank { null }
            }
            if (!secSourceXml.isNullOrBlank()) {
                parsedDualShapeOn = true
                val secType = extractMaskGeneratorAttrStr(secSourceXml, "type")
                    ?: extractMaskGeneratorAttrStr(secSourceXml, "id")
                parsedSecondaryShapeKind = when {
                    secType?.contains("rect", ignoreCase = true) == true -> "rect"
                    secType?.contains("gauss", ignoreCase = true) == true -> "gauss"
                    else -> "circle"
                }
                val secTipFilename = extractBrushTagAttrStr(secSourceXml, "filename")
                if (!secTipFilename.isNullOrBlank() && externalBrushTips.isNotEmpty()) {
                    val resolvedSecBytes = findBrushTipBytes(secTipFilename, externalBrushTips)
                    if (resolvedSecBytes != null) {
                        val secTipRes = extractTipStampBase64(resolvedSecBytes, secTipFilename)
                        if (secTipRes != null) {
                            parsedSecondaryTipBase64 = secTipRes.first
                            parsedSecondaryShapeKind = "predefined"
                        }
                    }
                }
                val secDiam = extractBrushTagAttrFloat(secSourceXml, "diameter")
                    ?: extractMaskGeneratorAttr(secSourceXml, "radius")?.let { it * 2f }
                val baseDiam = parsedSize ?: extractMaskGeneratorAttr(brushDefXml, "radius")?.let { it * 2f } ?: 20f
                if (secDiam != null && baseDiam > 0.5f) {
                    parsedDualShapeScale = (secDiam / baseDiam).coerceIn(0.25f, 1.45f)
                } else if (maskMasterSize != null) {
                    parsedDualShapeScale = maskMasterSize.coerceIn(0.25f, 1.45f)
                }
                val compOp = extractParamBody(cleanOuterXml, "MaskingBrush/CompositeOp")?.trim()?.lowercase() ?: ""
                parsedDualShapeBlendMode = if (compOp.contains("mult") || compOp.contains("sub") || compOp.contains("mask") || maskingEnabled) {
                    "mask"
                } else {
                    "combine"
                }
            } else if (parsedDualShapeOn) {
                parsedDualShapeBlendMode = if (maskingEnabled) "mask" else "combine"
            }
        }

        val engineKind = mapKritaMetaToEngine(paintOpId, parsedName)
        val effectiveHardness = if (parsedSoftness != null) {
            (1f - (parsedSoftness * 0.45f)).coerceIn(0.12f, 1.0f)
        } else {
            defaultHardnessFor(engineKind)
        }
        val baseGrain = when (engineKind) {
            KritaBrushEngineKind.PENCIL_GRAPHITE -> 0.55f
            KritaBrushEngineKind.CHARCOAL_SOFT -> 0.72f
            KritaBrushEngineKind.CHALK_GRAINY -> 0.85f
            KritaBrushEngineKind.INK_ROUGH -> 0.35f
            KritaBrushEngineKind.BRISTLE_DRY -> 0.28f
            else -> 0.0f
        }
        val effectiveGrain = maxOf(baseGrain, parsedTextureStrength ?: 0f).coerceIn(0f, 1f)
        val effectiveAspect = (parsedRatio ?: tipAspectFromImage ?: 1.0f).coerceIn(0.08f, 1.0f)

        val defaultDynamics = BrushDynamicParams(
            engineKind = engineKind,
            hardness = effectiveHardness,
            spacing = (parsedSpacing ?: 0.10f).coerceIn(0.02f, 0.8f),
            flow = (parsedFlow ?: 0.90f).coerceIn(0.08f, 1.0f),
            grainAmount = effectiveGrain,
            bristleCount = if (engineKind == KritaBrushEngineKind.BRISTLE_DRY) 6 else 5,
            taperStrength = if (engineKind == KritaBrushEngineKind.INK_GPEN) 0.75f
            else if (parsedRatio != null && parsedRatio < 0.75f) (1f - parsedRatio).coerceIn(0.15f, 0.7f)
            else 0.15f,
            aspectRatio = effectiveAspect,
            angleDeg = parsedAngleDeg,
            scatter = parsedScatter,
            angularJitter = parsedAngularJitter,
            followStrokeAngle = parsedFollowAngle,
            smudgeRate = parsedSmudgeRate,
            spikes = parsedSpikes,
            shapeKind = parsedShapeKind,
            tipStampBase64 = parsedTipBase64,
            dualColorEnabled = parsedDualColorOn,
            secondaryColor = parsedSecondaryColor,
            secondaryColorMix = parsedDualColorMix,
            dualColorMode = parsedDualColorMode,
            innerDotsEnabled = parsedInnerDotsOn,
            innerDotsDensity = (sprayDensity ?: 0.52f).coerceIn(0.1f, 1.0f),
            innerDotsSize = (sprayDotSize ?: 0.22f).coerceIn(0.08f, 0.6f),
            innerDotsUseSecondaryColor = parsedDualColorOn,
            dualShapeEnabled = parsedDualShapeOn,
            secondaryShapeKind = parsedSecondaryShapeKind,
            secondaryTipStampBase64 = parsedSecondaryTipBase64,
            dualShapeScale = parsedDualShapeScale,
            dualShapeBlendMode = parsedDualShapeBlendMode
        )

        val thumbBase64 = thumbnailPngBytes?.let { encodePngThumbnailBase64(it) } ?: parsedTipBase64
        val finalSize = (parsedSize ?: defaultSizeFor(engineKind)).coerceIn(2f, 64f)
        val finalOpacity = (parsedOpacity ?: defaultOpacityFor(engineKind)).coerceIn(0.15f, 1.0f)

        return BrushPreset(
            id = presetId,
            namePt = parsedName,
            shortLabel = parsedName.take(12),
            defaultSize = finalSize,
            defaultOpacity = finalOpacity,
            toolType = if (engineKind == KritaBrushEngineKind.PENCIL_GRAPHITE) ToolType.PENCIL
            else if (engineKind == KritaBrushEngineKind.INK_GPEN || engineKind == KritaBrushEngineKind.PIXEL_ROUND_SIZE) ToolType.PEN
            else ToolType.BRUSH,
            textureNamePt = buildString {
                append("Krita ($paintOpId")
                if (parsedTipBase64 != null) append(" • Ponta")
                if (parsedDualShapeOn) append(" • Multi")
                if (parsedDualColorOn) append(" • 2Cores")
                append(")")
            },
            textureSymbol = symbolForEngine(engineKind),
            descriptionPt = "Brush importado do Krita (.kpp • motor $paintOpId).",
            engineKind = engineKind,
            dynamicParams = defaultDynamics,
            isImportedKrita = true,
            kritaPaintOpId = paintOpId,
            iconBase64 = thumbBase64
        )
    }

    private fun findBrushTipBytes(tipFilename: String, map: Map<String, ByteArray>): ByteArray? {
        val decoded = urlDecodeSafe(decodeXmlEntities(tipFilename)).replace('\\', '/').trim().lowercase()
        if (decoded.isEmpty()) return null
        map[decoded]?.let { return it }
        val base = decoded.substringAfterLast('/')
        map[base]?.let { return it }
        val noExt = base.substringBeforeLast('.')
        return map[noExt]
    }

    private fun extractTipStampBase64(bytes: ByteArray, hintName: String): Pair<String, Float>? {
        val lower = hintName.lowercase()
        return when {
            isPngSignature(bytes) || lower.endsWith(".png") -> extractPngTipStamp(bytes)
            isGimpPatternSignature(bytes) || lower.endsWith(".pat") -> extractPatTipStamp(bytes)
            lower.endsWith(".gih") -> extractGihTipStamp(bytes)
            else -> extractGbrTipStamp(bytes) ?: extractGihTipStamp(bytes) ?: extractPatTipStamp(bytes)
        }
    }

    private fun extractGihTipStamp(bytes: ByteArray): Pair<String, Float>? {
        // GIMP Image Hose starts with 2 text lines followed by concatenated .gbr blocks (GIMP magic at header + 20)
        var fallbackTip: Pair<String, Float>? = null
        var i = 20
        while (i <= bytes.size - 4) {
            if (bytes[i] == 'G'.code.toByte() &&
                bytes[i + 1] == 'I'.code.toByte() &&
                bytes[i + 2] == 'M'.code.toByte() &&
                bytes[i + 3] == 'P'.code.toByte()
            ) {
                val start = i - 20
                if (start >= 0 && start + 28 <= bytes.size) {
                    val header = ByteBuffer.wrap(bytes, start, 28).order(ByteOrder.BIG_ENDIAN)
                    val headerSize = header.int
                    val version = header.int
                    val width = header.int
                    val height = header.int
                    val bpp = header.int
                    val blockPayload = width.toLong() * height.toLong() * bpp.toLong()
                    val decoded = extractGbrTipStampWithCoverage(bytes, startOffset = start)
                    if (decoded != null) {
                        val (tip, nonEmptyAlphaPixels) = decoded
                        if (nonEmptyAlphaPixels > 0) return tip
                        if (fallbackTip == null) fallbackTip = tip
                    }
                    if ((version == 1 || version == 2) && headerSize >= 28 && blockPayload > 0L && start + headerSize + blockPayload <= bytes.size) {
                        i = (start + headerSize + blockPayload.toInt() + 20).coerceAtLeast(i + 4)
                        continue
                    }
                }
            }
            i++
        }
        return fallbackTip
    }

    private fun extractPatTipStamp(rawBytes: ByteArray): Pair<String, Float>? {
        if (rawBytes.size < 24) return null
        return try {
            val header = ByteBuffer.wrap(rawBytes, 0, 24).order(ByteOrder.BIG_ENDIAN)
            val headerSize = header.int
            val version = header.int
            val width = header.int
            val height = header.int
            val bpp = header.int
            val magicInt = header.int
            val magic = String(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(magicInt).array(), Charsets.US_ASCII)
            if (magic != "GPAT" || version != 1 || headerSize < 24 || width <= 0 || height <= 0 || width > 2048 || height > 2048) {
                return null
            }
            convertRawGimpMaskPixels(rawBytes, headerSize, width, height, bpp)?.first
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGbrTipStamp(rawBytes: ByteArray, startOffset: Int = 0): Pair<String, Float>? {
        return extractGbrTipStampWithCoverage(rawBytes, startOffset)?.first
    }

    private fun extractGbrTipStampWithCoverage(rawBytes: ByteArray, startOffset: Int = 0): Pair<Pair<String, Float>, Int>? {
        if (rawBytes.size - startOffset < 28) return null
        return try {
            val header = ByteBuffer.wrap(rawBytes, startOffset, 28).order(ByteOrder.BIG_ENDIAN)
            val headerSize = header.int
            val version = header.int
            val width = header.int
            val height = header.int
            val bpp = header.int
            val magicInt = header.int
            val magic = String(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(magicInt).array(), Charsets.US_ASCII)
            if (magic != "GIMP" || (version != 1 && version != 2) || headerSize < 28 || width <= 0 || height <= 0 || width > 2048 || height > 2048) {
                return null
            }
            convertRawGimpMaskPixels(rawBytes, startOffset + headerSize, width, height, bpp)
        } catch (_: Exception) {
            null
        }
    }

    private fun convertRawGimpMaskPixels(
        rawBytes: ByteArray,
        pixelStart: Int,
        width: Int,
        height: Int,
        bpp: Int
    ): Pair<Pair<String, Float>, Int>? {
        val expectedBytes = width * height * bpp
        if (pixelStart < 0 || pixelStart + expectedBytes > rawBytes.size) return null

        val pixels = IntArray(width * height)
        var pIdx = pixelStart
        var nonEmptyCount = 0
        if (bpp == 1) {
            for (i in 0 until width * height) {
                val alpha = rawBytes[pIdx++].toInt() and 0xFF
                if (alpha > 8) nonEmptyCount++
                pixels[i] = (alpha shl 24) or 0x00FFFFFF
            }
        } else if (bpp == 2) {
            for (i in 0 until width * height) {
                val gray = rawBytes[pIdx++].toInt() and 0xFF
                val a = rawBytes[pIdx++].toInt() and 0xFF
                val maskAlpha = (if (a > 0 && a < 250) a else (255 - gray)).coerceIn(0, 255)
                if (maskAlpha > 8) nonEmptyCount++
                pixels[i] = (maskAlpha shl 24) or 0x00FFFFFF
            }
        } else if (bpp == 3) {
            for (i in 0 until width * height) {
                val r = rawBytes[pIdx++].toInt() and 0xFF
                val g = rawBytes[pIdx++].toInt() and 0xFF
                val b = rawBytes[pIdx++].toInt() and 0xFF
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                val maskAlpha = (255 - lum).coerceIn(0, 255)
                if (maskAlpha > 8) nonEmptyCount++
                pixels[i] = (maskAlpha shl 24) or 0x00FFFFFF
            }
        } else if (bpp == 4) {
            var minAlpha = 255
            for (i in 0 until width * height) {
                val a = rawBytes[pIdx + i * 4 + 3].toInt() and 0xFF
                if (a < minAlpha) minAlpha = a
            }
            val hasTrueAlpha = minAlpha < 245
            for (i in 0 until width * height) {
                val r = rawBytes[pIdx++].toInt() and 0xFF
                val g = rawBytes[pIdx++].toInt() and 0xFF
                val b = rawBytes[pIdx++].toInt() and 0xFF
                val a = rawBytes[pIdx++].toInt() and 0xFF
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                val maskAlpha = (if (hasTrueAlpha) a else (255 - lum)).coerceIn(0, 255)
                if (maskAlpha > 8) nonEmptyCount++
                pixels[i] = (maskAlpha shl 24) or 0x00FFFFFF
            }
        } else {
            return null
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        val encoded = encodeTipBitmapTo40pxBase64(bmp)
        val aspect = (minOf(width, height).toFloat() / maxOf(width, height).toFloat()).coerceIn(0.08f, 1.0f)
        return (encoded to aspect) to nonEmptyCount
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, maxDim: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        var inSample = 1
        val outW = boundsOpts.outWidth
        val outH = boundsOpts.outHeight
        if (outW > maxDim || outH > maxDim) {
            while ((outW / inSample) > maxDim * 2 || (outH / inSample) > maxDim * 2) {
                inSample = inSample shl 1
            }
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = inSample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    private fun parseHexColorOrNull(raw: String?): Long? {
        val clean = raw?.trim()?.removePrefix("#") ?: return null
        if (clean.length != 6 && clean.length != 8) return null
        return try {
            val v = clean.toLong(16)
            if (clean.length == 6) (0xFF000000L or v) else v
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPngTipStamp(pngBytes: ByteArray): Pair<String, Float>? {
        return try {
            val bmp = decodeSampledBitmapFromBytes(pngBytes, 160) ?: return null
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0) {
                bmp.recycle()
                return null
            }
            val rawPixels = IntArray(w * h)
            bmp.getPixels(rawPixels, 0, w, 0, 0, w, h)
            var minAlpha = 255
            for (px in rawPixels) {
                val a = (px ushr 24) and 0xFF
                if (a < minAlpha) minAlpha = a
            }
            val useAlphaChannel = bmp.hasAlpha() && minAlpha < 245
            for (i in rawPixels.indices) {
                val px = rawPixels[i]
                val a = (px ushr 24) and 0xFF
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val lum = (r * 77 + g * 150 + b * 29) shr 8
                val maskAlpha = if (useAlphaChannel) a else (255 - lum)
                rawPixels[i] = (maskAlpha.coerceIn(0, 255) shl 24) or 0x00FFFFFF
            }
            val maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            maskBmp.setPixels(rawPixels, 0, w, 0, 0, w, h)
            bmp.recycle()
            val encoded = encodeTipBitmapTo40pxBase64(maskBmp)
            val aspect = (minOf(w, h).toFloat() / maxOf(w, h).toFloat()).coerceIn(0.08f, 1.0f)
            encoded to aspect
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeTipBitmapTo40pxBase64(maskBmp: Bitmap): String {
        val maxDim = 96
        val w = maskBmp.width
        val h = maskBmp.height
        val targetW: Int
        val targetH: Int
        if (w >= h) {
            targetW = maxDim
            targetH = ((h.toFloat() / w.toFloat()) * maxDim).toInt().coerceIn(8, maxDim)
        } else {
            targetH = maxDim
            targetW = ((w.toFloat() / h.toFloat()) * maxDim).toInt().coerceIn(8, maxDim)
        }
        val scaled = if (w == targetW && h == targetH) maskBmp else Bitmap.createScaledBitmap(maskBmp, targetW, targetH, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
        if (scaled !== maskBmp) scaled.recycle()
        maskBmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun extractKritaPresetXmlFromPng(pngBytes: ByteArray): String? {
        if (!isPngSignature(pngBytes)) return null
        var offset = 8
        var fallbackXmlCandidate: String? = null
        while (offset + 12 <= pngBytes.size) {
            val len = ByteBuffer.wrap(pngBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (len < 0 || offset + 12L + len.toLong() > pngBytes.size.toLong()) break
            val chunkType = String(pngBytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            when (chunkType) {
                "tEXt" -> {
                    val zeroIdx = findByte(pngBytes, dataStart, dataStart + len, 0)
                    if (zeroIdx > dataStart) {
                        val key = String(pngBytes, dataStart, zeroIdx - dataStart, Charsets.ISO_8859_1)
                        val text = String(pngBytes, zeroIdx + 1, dataStart + len - (zeroIdx + 1), Charsets.UTF_8)
                        if (key.equals("preset", ignoreCase = true)) {
                            return text
                        }
                        if (fallbackXmlCandidate == null && looksLikeKritaPresetText(text)) {
                            fallbackXmlCandidate = text
                        }
                    }
                }
                "zTXt" -> {
                    val zeroIdx = findByte(pngBytes, dataStart, dataStart + len, 0)
                    if (zeroIdx > dataStart && zeroIdx + 2 <= dataStart + len) {
                        val key = String(pngBytes, dataStart, zeroIdx - dataStart, Charsets.ISO_8859_1)
                        val compressedStart = zeroIdx + 2
                        val compressedLen = (dataStart + len) - compressedStart
                        if (compressedLen > 0) {
                            val text = decompressZlib(pngBytes, compressedStart, compressedLen)
                            if (key.equals("preset", ignoreCase = true)) {
                                return text
                            }
                            if (fallbackXmlCandidate == null && text != null && looksLikeKritaPresetText(text)) {
                                fallbackXmlCandidate = text
                            }
                        }
                    }
                }
                "iTXt" -> {
                    val zeroIdx = findByte(pngBytes, dataStart, dataStart + len, 0)
                    if (zeroIdx > dataStart && zeroIdx + 5 < dataStart + len) {
                        val key = String(pngBytes, dataStart, zeroIdx - dataStart, Charsets.UTF_8)
                        val compFlag = pngBytes[zeroIdx + 1].toInt()
                        var cursor = zeroIdx + 3
                        val langEnd = findByte(pngBytes, cursor, dataStart + len, 0)
                        if (langEnd >= cursor) {
                            cursor = langEnd + 1
                            val transEnd = findByte(pngBytes, cursor, dataStart + len, 0)
                            if (transEnd >= cursor) {
                                cursor = transEnd + 1
                                val textLen = (dataStart + len) - cursor
                                if (textLen > 0) {
                                    val text = if (compFlag == 1) {
                                        decompressZlib(pngBytes, cursor, textLen)
                                    } else {
                                        String(pngBytes, cursor, textLen, Charsets.UTF_8)
                                    }
                                    if (key.equals("preset", ignoreCase = true)) {
                                        return text
                                    }
                                    if (fallbackXmlCandidate == null && text != null && looksLikeKritaPresetText(text)) {
                                        fallbackXmlCandidate = text
                                    }
                                }
                            }
                        }
                    }
                }
                "IEND" -> break
            }
            offset += 12 + len
        }
        return fallbackXmlCandidate
    }

    fun loadImportedBrushes(context: Context): List<BrushPreset> {
        return try {
            val file = File(File(context.filesDir, VAULT_DIR), VAULT_FILE)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val list = mutableListOf<BrushPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                jsonToBrushPreset(obj)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading imported brushes: ${e.message}")
            emptyList()
        }
    }

    fun saveImportedBrushes(context: Context, brushes: List<BrushPreset>) {
        try {
            val dir = File(context.filesDir, VAULT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val arr = JSONArray()
            brushes.forEach { b -> arr.put(brushPresetToJson(b)) }
            File(dir, VAULT_FILE).writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving imported brushes: ${e.message}")
        }
    }

    fun loadSharedTipMap(context: Context): HashMap<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        try {
            val tipsDir = File(File(context.filesDir, VAULT_DIR), TIPS_DIR)
            if (!tipsDir.exists()) return map
            tipsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() in 8..(8 * 1024 * 1024)) {
                    val bytes = file.readBytes()
                    indexBrushTipBytes(map, file.name.lowercase(), bytes)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading shared brush tips: ${e.message}")
        }
        return map
    }

    fun saveSharedTips(context: Context, tips: Map<String, ByteArray>) {
        if (tips.isEmpty()) return
        try {
            val tipsDir = File(File(context.filesDir, VAULT_DIR), TIPS_DIR)
            if (!tipsDir.exists()) tipsDir.mkdirs()
            tips.forEach { (rawName, bytes) ->
                val cleanName = rawName.replace('\\', '/').substringAfterLast('/')
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    .trim('_')
                if (cleanName.isNotEmpty() && bytes.size >= 8) {
                    File(tipsDir, cleanName).writeBytes(bytes)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving shared brush tips: ${e.message}")
        }
    }

    fun deleteImportedBrush(context: Context, brushId: Int): List<BrushPreset> {
        val remaining = loadImportedBrushes(context).filterNot { it.id == brushId }
        saveImportedBrushes(context, remaining)
        val remainingFactory = loadFactorySnapshots(context).filterNot { it.id == brushId }
        saveFactorySnapshots(context, remainingFactory)
        return remaining
    }

    fun updateImportedBrush(context: Context, updatedPreset: BrushPreset): List<BrushPreset> {
        val current = loadImportedBrushes(context).toMutableList()
        val factoryList = loadFactorySnapshots(context).toMutableList()
        val idx = current.indexOfFirst { it.id == updatedPreset.id }
        if (idx >= 0) {
            if (factoryList.none { it.id == updatedPreset.id }) {
                factoryList.add(current[idx])
                saveFactorySnapshots(context, factoryList)
            }
            current[idx] = updatedPreset
            saveImportedBrushes(context, current)
        }
        return current
    }

    fun resetImportedBrushToFactory(context: Context, brushId: Int): Pair<List<BrushPreset>, BrushPreset?> {
        val factory = loadFactorySnapshots(context).firstOrNull { it.id == brushId }
        val current = loadImportedBrushes(context).toMutableList()
        if (factory != null) {
            val idx = current.indexOfFirst { it.id == brushId }
            if (idx >= 0) {
                current[idx] = factory
                saveImportedBrushes(context, current)
            }
            return current to factory
        }
        return current to current.firstOrNull { it.id == brushId }
    }

    private fun loadFactorySnapshots(context: Context): List<BrushPreset> {
        return try {
            val file = File(File(context.filesDir, VAULT_DIR), FACTORY_FILE)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            val list = mutableListOf<BrushPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                jsonToBrushPreset(obj)?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFactorySnapshots(context: Context, brushes: List<BrushPreset>) {
        try {
            val dir = File(context.filesDir, VAULT_DIR)
            if (!dir.exists()) dir.mkdirs()
            val arr = JSONArray()
            brushes.forEach { b -> arr.put(brushPresetToJson(b)) }
            File(dir, FACTORY_FILE).writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun parseJsonPresetBytes(rawBytes: ByteArray, startingId: Int): List<BrushPreset> {
        return try {
            val text = String(rawBytes, Charsets.UTF_8).trimStart('\uFEFF').trim()
            if (text.startsWith("[")) {
                val arr = JSONArray(text)
                (0 until arr.length()).mapNotNull { idx ->
                    val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                    jsonToBrushPreset(obj, overrideId = startingId + idx)
                }
            } else {
                listOfNotNull(jsonToBrushPreset(JSONObject(text), overrideId = startingId))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun brushFingerprint(b: BrushPreset): String {
        val normName = b.namePt.trim().lowercase()
        return "$normName|${b.engineKind.name}|${b.kritaPaintOpId.lowercase()}"
    }

    fun saveSingleImportedBrush(context: Context, preset: BrushPreset, existing: List<BrushPreset>): Pair<List<BrushPreset>, BrushPreset> {
        val fp = brushFingerprint(preset)
        existing.firstOrNull { brushFingerprint(it) == fp }?.let { already ->
            return existing to already
        }
        val updated = existing.toMutableList()
        val runningId = ((updated.maxOfOrNull { it.id } ?: 1000).coerceAtLeast(1000)) + 1
        var candidateName = preset.namePt.ifBlank { "Brush Amigo #$runningId" }
        var suffix = 2
        while (updated.any { it.namePt.equals(candidateName, ignoreCase = true) }) {
            candidateName = "${preset.namePt} ($suffix)"
            suffix++
        }
        val finalPreset = preset.copy(
            id = runningId,
            namePt = candidateName,
            shortLabel = candidateName.take(12),
            isImportedKrita = true
        )
        updated.add(finalPreset)
        saveImportedBrushes(context, updated)
        val factoryList = loadFactorySnapshots(context).toMutableList()
        factoryList.add(finalPreset)
        saveFactorySnapshots(context, factoryList)
        return updated to finalPreset
    }

    fun brushPresetToJson(b: BrushPreset): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("namePt", b.namePt)
        put("shortLabel", b.shortLabel)
        put("defaultSize", b.defaultSize.toDouble())
        put("defaultOpacity", b.defaultOpacity.toDouble())
        put("toolType", b.toolType.name)
        put("textureNamePt", b.textureNamePt)
        put("textureSymbol", b.textureSymbol)
        put("descriptionPt", b.descriptionPt)
        put("engineKind", b.engineKind.name)
        put("dynamicParams", b.dynamicParams.toCompactJson())
        put("isImportedKrita", b.isImportedKrita)
        put("kritaPaintOpId", b.kritaPaintOpId)
        if (!b.iconBase64.isNullOrBlank()) put("iconBase64", b.iconBase64)
    }

    fun jsonToBrushPreset(obj: JSONObject, overrideId: Int? = null): BrushPreset? {
        return try {
            val id = overrideId ?: obj.optInt("id", 1001)
            val name = obj.optString("namePt", "Krita Brush #$id")
            val engineName = obj.optString("engineKind", KritaBrushEngineKind.PIXEL_ROUND_SIZE.name)
            val engine = try { KritaBrushEngineKind.valueOf(engineName) } catch (_: Exception) { KritaBrushEngineKind.PIXEL_ROUND_SIZE }
            val dynStr = obj.optString("dynamicParams", "")
            val dyn = BrushDynamicParams.fromJsonOrNull(dynStr) ?: BrushDynamicParams(engineKind = engine)
            val tool = try { ToolType.valueOf(obj.optString("toolType", ToolType.BRUSH.name)) } catch (_: Exception) { ToolType.BRUSH }
            BrushPreset(
                id = id,
                namePt = name,
                shortLabel = obj.optString("shortLabel", name.take(12)),
                defaultSize = obj.optDouble("defaultSize", 10.0).toFloat(),
                defaultOpacity = obj.optDouble("defaultOpacity", 0.9).toFloat(),
                toolType = tool,
                textureNamePt = obj.optString("textureNamePt", "Krita Importado"),
                textureSymbol = obj.optString("textureSymbol", symbolForEngine(engine)),
                descriptionPt = obj.optString("descriptionPt", "Brush importado do Krita"),
                engineKind = engine,
                dynamicParams = dyn,
                isImportedKrita = true,
                kritaPaintOpId = obj.optString("kritaPaintOpId", "paintbrush"),
                iconBase64 = if (obj.has("iconBase64") && !obj.isNull("iconBase64")) obj.optString("iconBase64").ifBlank { null } else null
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun mapKritaMetaToEngine(paintOpId: String, name: String): KritaBrushEngineKind {
        val idLow = paintOpId.lowercase()
        val low = "$idLow $name".lowercase()
        return when {
            "colorsmudge" in idLow || "deformbrush" in idLow -> KritaBrushEngineKind.BLENDER_WET_SOFT
            "spraybrush" in idLow || "particle" in idLow -> KritaBrushEngineKind.AIRBRUSH_SOFT
            "hatch" in idLow -> KritaBrushEngineKind.BRISTLE_DRY
            "sketchbrush" in idLow || "curvebrush" in idLow -> {
                if ("ink" in low || "rough" in low) KritaBrushEngineKind.INK_ROUGH else KritaBrushEngineKind.PENCIL_GRAPHITE
            }
            "gridbrush" in idLow -> KritaBrushEngineKind.PIXEL_ROUND_SIZE
            "gpen" in low || "g-pen" in low || "g_pen" in low || "pena" in low -> KritaBrushEngineKind.INK_GPEN
            "rough" in low && "ink" in low -> KritaBrushEngineKind.INK_ROUGH
            "ink" in low || "fineliner" in low || "nanquim" in low -> KritaBrushEngineKind.INK_GPEN
            "pencil" in low || "lapis" in low || "graphite" in low -> KritaBrushEngineKind.PENCIL_GRAPHITE
            "charcoal" in low || "carvao" in low || "carvão" in low -> KritaBrushEngineKind.CHARCOAL_SOFT
            "chalk" in low || "pastel" in low || "giz" in low -> KritaBrushEngineKind.CHALK_GRAINY
            "marker" in low || "chisel" in low || "felt" in low || "marcador" in low -> KritaBrushEngineKind.MARKER_CHISEL
            "airbrush" in low || "spray" in low || "aero" in low -> KritaBrushEngineKind.AIRBRUSH_SOFT
            "glaze" in low || "flat" in low || "rake" in low -> KritaBrushEngineKind.BRISTLE_GLAZE
            "bristle" in low || "hairy" in low || "oil" in low || "cerdas" in low -> KritaBrushEngineKind.BRISTLE_DRY
            "smudge" in low || "blender" in low || "wet" in low || "water" in low || "sketch" in low -> KritaBrushEngineKind.BLENDER_WET_SOFT
            "opacity" in low || "basic-2" in low || "soft" in low -> KritaBrushEngineKind.PIXEL_OPACITY
            else -> KritaBrushEngineKind.PIXEL_ROUND_SIZE
        }
    }

    private fun defaultHardnessFor(engine: KritaBrushEngineKind): Float = when (engine) {
        KritaBrushEngineKind.INK_GPEN -> 1.0f
        KritaBrushEngineKind.AIRBRUSH_SOFT -> 0.2f
        KritaBrushEngineKind.BLENDER_WET_SOFT -> 0.35f
        KritaBrushEngineKind.CHARCOAL_SOFT -> 0.52f
        else -> 0.85f
    }

    private fun defaultSizeFor(engine: KritaBrushEngineKind): Float = when (engine) {
        KritaBrushEngineKind.PENCIL_GRAPHITE -> 5f
        KritaBrushEngineKind.INK_GPEN, KritaBrushEngineKind.INK_ROUGH -> 7f
        KritaBrushEngineKind.AIRBRUSH_SOFT -> 28f
        else -> 15f
    }

    private fun defaultOpacityFor(engine: KritaBrushEngineKind): Float = when (engine) {
        KritaBrushEngineKind.AIRBRUSH_SOFT -> 0.38f
        KritaBrushEngineKind.BLENDER_WET_SOFT -> 0.55f
        KritaBrushEngineKind.MARKER_CHISEL -> 0.65f
        else -> 0.92f
    }

    private fun symbolForEngine(engine: KritaBrushEngineKind): String = when (engine) {
        KritaBrushEngineKind.PIXEL_ROUND_SIZE -> "●"
        KritaBrushEngineKind.PIXEL_OPACITY -> "◐"
        KritaBrushEngineKind.INK_GPEN -> "✒"
        KritaBrushEngineKind.INK_ROUGH -> "〰"
        KritaBrushEngineKind.PENCIL_GRAPHITE -> "✏"
        KritaBrushEngineKind.CHARCOAL_SOFT -> "▓"
        KritaBrushEngineKind.MARKER_CHISEL -> "▰"
        KritaBrushEngineKind.BRISTLE_DRY -> "≣"
        KritaBrushEngineKind.BRISTLE_GLAZE -> "▀"
        KritaBrushEngineKind.CHALK_GRAINY -> "▒"
        KritaBrushEngineKind.AIRBRUSH_SOFT -> "∴"
        KritaBrushEngineKind.BLENDER_WET_SOFT -> "◈"
    }

    private fun decodeEmbeddedXml(raw: String): String {
        var cur = raw.replace("<![CDATA[", "").replace("]]>", "").trim()
        repeat(2) {
            if (!cur.contains('<') && (cur.contains("&lt;") || cur.contains("&quot;") || cur.contains("&apos;"))) {
                cur = decodeXmlEntities(cur)
            } else if (cur.contains("&lt;Brush") || cur.contains("&lt;MaskGenerator") || cur.contains("&lt;params")) {
                cur = decodeXmlEntities(cur)
            }
        }
        return cur
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&amp;", "&")
    }

    private fun parseFloatLenient(raw: String?): Float? {
        val clean = raw?.trim()?.replace(',', '.') ?: return null
        if (clean.isEmpty()) return null
        return clean.toFloatOrNull()?.takeIf { it.isFinite() }
    }

    private fun urlDecodeSafe(raw: String): String {
        return try {
            java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    private fun extractXmlAttr(xml: String, attr: String): String? {
        val esc = Regex.escape(attr)
        val regex = Regex("""\b$esc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val m = regex.find(xml) ?: return null
        val raw = m.groupValues[1].ifEmpty { m.groupValues[2] }
        return decodeXmlEntities(raw)
    }

    private fun extractParamBody(xml: String, paramName: String): String? {
        val esc = Regex.escape(paramName)
        val bodyRegex = Regex("""<param\b[^>]*\bname\s*=\s*(?:"$esc"|'$esc')[^>]*>([\s\S]*?)</param>""", RegexOption.IGNORE_CASE)
        val body = bodyRegex.find(xml)?.groupValues?.getOrNull(1)
        if (!body.isNullOrBlank()) return decodeXmlEntities(body)
        val attrRegex = Regex("""<param\b[^>]*\bname\s*=\s*(?:"$esc"|'$esc')[^>]*\bvalue\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*/?>""", RegexOption.IGNORE_CASE)
        val m = attrRegex.find(xml)
        if (m != null) {
            return decodeXmlEntities(m.groupValues[1].ifEmpty { m.groupValues[2] })
        }
        return body?.let { decodeXmlEntities(it) }
    }

    private fun extractMaskGeneratorAttr(xml: String, attr: String): Float? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<MaskGenerator\b[^>]*\b$esc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val m = regex.find(xml) ?: return null
        return parseFloatLenient(m.groupValues[1].ifEmpty { m.groupValues[2] })
    }

    private fun extractMaskGeneratorAttrStr(xml: String, attr: String): String? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<MaskGenerator\b[^>]*\b$esc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val m = regex.find(xml) ?: return null
        return decodeXmlEntities(m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
    }

    private fun extractBrushTagAttrFloat(xml: String, attr: String): Float? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<Brush\b[^>]*\b$esc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val m = regex.find(xml) ?: return null
        return parseFloatLenient(m.groupValues[1].ifEmpty { m.groupValues[2] })
    }

    private fun extractBrushTagAttrStr(xml: String, attr: String): String? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<Brush\b[^>]*\b$esc\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
        val m = regex.find(xml) ?: return null
        return decodeXmlEntities(m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
    }

    private fun extractParamBool(xml: String, paramName: String): Boolean? {
        val esc = Regex.escape(paramName)
        val bodyRegex = Regex("""<param\b[^>]*\bname\s*=\s*(?:"$esc"|'$esc')[^>]*>([\s\S]*?)</param>""", RegexOption.IGNORE_CASE)
        val body = bodyRegex.find(xml)?.groupValues?.getOrNull(1)?.trim()
        if (body != null) {
            if (body.equals("true", ignoreCase = true) || body == "1") return true
            if (body.equals("false", ignoreCase = true) || body == "0") return false
        }
        val selfCloseRegex = Regex("""<param\b[^>]*\bname\s*=\s*(?:"$esc"|'$esc')[^>]*\bvalue\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*/?>""", RegexOption.IGNORE_CASE)
        val m = selfCloseRegex.find(xml) ?: return null
        val sc = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
        if (sc.equals("true", ignoreCase = true) || sc == "1") return true
        if (sc.equals("false", ignoreCase = true) || sc == "0") return false
        return null
    }

    private fun extractParamFloat(xml: String, paramName: String, subAttr: String? = null): Float? {
        val esc = Regex.escape(paramName)
        val bodyRegex = Regex("""<param\b[^>]*\bname\s*=\s*(?:"$esc"|'$esc')[^>]*>([\s\S]*?)</param>""", RegexOption.IGNORE_CASE)
        val bodyMatch = bodyRegex.find(xml)?.groupValues?.getOrNull(1)
        if (bodyMatch != null) {
            return if (subAttr != null) {
                val subM = Regex("""\b${Regex.escape(subAttr)}\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
                    .find(bodyMatch)
                parseFloatLenient(subM?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } })
            } else {
                parseFloatLenient(bodyMatch)
            }
        }
        val selfCloseRegex = Regex("""<param\b[^>]*\bname\s*=\s*(?:"$esc"|'$esc')[^>]*\bvalue\s*=\s*(?:"([^"]*)"|'([^']*)')[^>]*/?>""", RegexOption.IGNORE_CASE)
        val m = selfCloseRegex.find(xml) ?: return null
        return parseFloatLenient(m.groupValues[1].ifEmpty { m.groupValues[2] })
    }

    private fun encodePngThumbnailBase64(pngBytes: ByteArray): String? {
        return try {
            val bmp = decodeSampledBitmapFromBytes(pngBytes, 144) ?: return null
            val scaled = if (bmp.width == 72 && bmp.height == 72) bmp else Bitmap.createScaledBitmap(bmp, 72, 72, true)
            if (scaled !== bmp) bmp.recycle()
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
            scaled.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun isPngSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        for (i in 0 until 8) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false
        }
        return true
    }

    private fun isZipSignature(bytes: ByteArray): Boolean {
        return bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
    }

    private fun isGimpBrushOrHoseSignature(bytes: ByteArray): Boolean {
        if (bytes.size >= 24 &&
            bytes[20] == 'G'.code.toByte() &&
            bytes[21] == 'I'.code.toByte() &&
            bytes[22] == 'M'.code.toByte() &&
            bytes[23] == 'P'.code.toByte()
        ) return true
        val scanLen = minOf(bytes.size, 256)
        val head = String(bytes, 0, scanLen, Charsets.ISO_8859_1)
        return head.contains("GIMP") && head.contains('\n')
    }

    private fun isGimpPatternSignature(bytes: ByteArray): Boolean {
        return bytes.size >= 24 &&
            bytes[20] == 'G'.code.toByte() &&
            bytes[21] == 'P'.code.toByte() &&
            bytes[22] == 'A'.code.toByte() &&
            bytes[23] == 'T'.code.toByte()
    }

    private fun isRawXmlPreset(bytes: ByteArray): Boolean {
        val sample = String(bytes, 0, minOf(bytes.size, 256), Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return looksLikeKritaPresetText(sample)
    }

    private fun looksLikeKritaPresetText(text: String): Boolean {
        return text.startsWith("<Preset", ignoreCase = true) ||
            (text.startsWith("<?xml", ignoreCase = true) && text.contains("Preset", ignoreCase = true)) ||
            (text.contains("<Preset", ignoreCase = true) && text.contains("paintopid", ignoreCase = true))
    }

    private fun isJsonContent(bytes: ByteArray): Boolean {
        val sample = String(bytes, 0, minOf(bytes.size, 64), Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return sample.startsWith("{") || sample.startsWith("[")
    }

    private fun findByte(bytes: ByteArray, start: Int, end: Int, target: Byte): Int {
        for (i in start until end) {
            if (bytes[i] == target) return i
        }
        return -1
    }

    private fun decompressZlib(data: ByteArray, offset: Int, length: Int): String? {
        return try {
            val inflater = Inflater()
            inflater.setInput(data, offset, length)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, count)
                }
            }
            inflater.end()
            out.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx) ?: "krita_brush.kpp"
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "krita_brush.kpp"
    }
}
