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

        val imported = when {
            displayName.endsWith(".bundle", ignoreCase = true) ||
                displayName.endsWith(".zip", ignoreCase = true) ||
                isZipSignature(rawBytes) -> parseBundleBytes(rawBytes, nextId)

            displayName.endsWith(".gbr", ignoreCase = true) ->
                listOfNotNull(parseGbrBytes(rawBytes, displayName, nextId))

            displayName.endsWith(".json", ignoreCase = true) -> parseJsonPresetBytes(rawBytes, nextId)
            else -> listOfNotNull(parseKppBytes(rawBytes, displayName, nextId))
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

    fun parseBundleBytes(rawBytes: ByteArray, startingId: Int = 1001): List<BrushPreset> {
        val results = mutableListOf<BrushPreset>()
        var currentId = startingId
        val kppEntries = mutableListOf<Pair<String, ByteArray>>()
        val jsonEntries = mutableListOf<Pair<String, ByteArray>>()
        val gbrEntries = mutableListOf<Pair<String, ByteArray>>()
        val brushTipMap = HashMap<String, ByteArray>()

        try {
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory) {
                        val lower = name.replace('\\', '/').lowercase()
                        when {
                            lower.endsWith(".kpp") -> {
                                kppEntries.add(name to zis.readBytes())
                            }
                            lower.endsWith(".json") -> {
                                if (!lower.endsWith("manifest.json")) {
                                    jsonEntries.add(name to zis.readBytes())
                                }
                            }
                            lower.endsWith(".gbr") || lower.endsWith(".gih") || lower.endsWith(".png") -> {
                                val bytes = zis.readBytes()
                                if (!lower.contains("paintoppresets/")) {
                                    indexBrushTipBytes(brushTipMap, lower, bytes)
                                }
                                if (lower.endsWith(".gbr")) {
                                    gbrEntries.add(name to bytes)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            kppEntries.forEach { (name, bytes) ->
                val parsed = parseKppBytes(
                    rawBytes = bytes,
                    fileName = name.substringAfterLast('/'),
                    presetId = currentId,
                    externalBrushTips = brushTipMap
                )
                if (parsed != null) {
                    results.add(parsed)
                    currentId++
                }
            }

            jsonEntries.forEach { (_, bytes) ->
                val parsedList = parseJsonPresetBytes(bytes, currentId)
                parsedList.forEach { p ->
                    results.add(p.copy(id = currentId))
                    currentId++
                }
            }

            // Fallback to standalone .gbr brushes only if the archive has no .kpp/.json presets
            if (results.isEmpty()) {
                gbrEntries.forEach { (name, bytes) ->
                    val parsed = parseGbrBytes(bytes, name.substringAfterLast('/'), currentId)
                    if (parsed != null) {
                        results.add(parsed)
                        currentId++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing .bundle archive: ${e.message}")
        } finally {
            kppEntries.clear()
            jsonEntries.clear()
            gbrEntries.clear()
            brushTipMap.clear()
        }
        return results
    }

    private fun indexBrushTipBytes(map: MutableMap<String, ByteArray>, lowerPath: String, bytes: ByteArray) {
        map[lowerPath] = bytes
        val baseName = lowerPath.substringAfterLast('/')
        if (baseName.isNotEmpty()) {
            map[baseName] = bytes
            val noExt = baseName.substringBeforeLast('.')
            if (noExt.isNotEmpty()) {
                map.putIfAbsent(noExt, bytes)
            }
        }
        val afterBrushes = lowerPath.substringAfter("brushes/", "")
        if (afterBrushes.isNotEmpty()) {
            map[afterBrushes] = bytes
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

    fun parseKppBytes(
        rawBytes: ByteArray,
        fileName: String,
        presetId: Int = 1001,
        externalBrushTips: Map<String, ByteArray> = emptyMap()
    ): BrushPreset? {
        if (!isPngSignature(rawBytes)) return null

        val rawXml = extractKritaPresetXmlFromPng(rawBytes)
        val presetXml = rawXml?.let { decodeXmlBody(it) }
        val cleanBaseName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffix(".kpp")
            .removeSuffix(".KPP")
            .ifBlank { "Brush Krita #$presetId" }

        var parsedName = cleanBaseName
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

        if (!presetXml.isNullOrBlank()) {
            extractXmlAttr(presetXml, "name")?.takeIf { it.isNotBlank() }?.let {
                parsedName = decodeXmlEntities(it).trim()
            }
            extractXmlAttr(presetXml, "paintopid")?.takeIf { it.isNotBlank() }?.let {
                paintOpId = decodeXmlEntities(it).trim().lowercase()
            }

            // Krita stores the inner brush XML inside <param name="brush_definition">...</param>
            val brushDefXml = extractParamBody(presetXml, "brush_definition") ?: presetXml
            val maskRadius = extractMaskGeneratorAttr(brushDefXml, "radius")
                ?: extractMaskGeneratorAttr(presetXml, "radius")
            val brushAttrDiameter = extractBrushTagAttrFloat(brushDefXml, "diameter")
            val brushAttrScale = extractBrushTagAttrFloat(brushDefXml, "scale")

            parsedSize = extractParamFloat(presetXml, "Diameter")
                ?: brushAttrDiameter
                ?: maskRadius?.let { it * 2f }
                ?: extractParamFloat(presetXml, "Size")
                ?: brushAttrScale?.let { (it * 24f).coerceIn(4f, 64f) }

            parsedOpacity = extractParamFloat(presetXml, "OpacityValue")
                ?: extractParamFloat(presetXml, "opacity")
            parsedFlow = extractParamFloat(presetXml, "FlowValue")
                ?: extractParamFloat(presetXml, "flow")
            parsedSpacing = extractParamFloat(presetXml, "Spacing")
                ?: extractParamFloat(presetXml, "spacing")
                ?: extractBrushTagAttrFloat(brushDefXml, "spacing")
                ?: extractBrushTagAttrFloat(brushDefXml, "AutoSpacingCoeff")

            parsedSoftness = extractParamFloat(presetXml, "softness")
                ?: extractParamFloat(presetXml, "Softness")
                ?: extractMaskGeneratorAttr(brushDefXml, "softness")
                ?: extractMaskGeneratorAttr(presetXml, "softness")
            parsedRatio = extractMaskGeneratorAttr(brushDefXml, "ratio")
                ?: extractMaskGeneratorAttr(presetXml, "ratio")
            val maskSpikes = extractMaskGeneratorAttr(brushDefXml, "spikes")
                ?: extractMaskGeneratorAttr(presetXml, "spikes")
            if (maskSpikes != null) {
                parsedSpikes = maskSpikes.toInt().coerceIn(2, 12)
            }

            val rawShapeType = extractMaskGeneratorAttrStr(brushDefXml, "type")
                ?: extractMaskGeneratorAttrStr(presetXml, "type")
            val maskKindRaw = extractMaskGeneratorAttrStr(brushDefXml, "id")
                ?: extractMaskGeneratorAttrStr(presetXml, "id")
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
                ?: extractParamFloat(presetXml, "angle")
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

            // Link predefined raster brush tip from .bundle (`<Brush filename="..."/>`)
            val tipFilename = extractBrushTagAttrStr(brushDefXml, "filename")
                ?: extractBrushTagAttrStr(presetXml, "filename")
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
            val scatterBool = extractParamBool(presetXml, "Scatter") == true ||
                extractParamBool(presetXml, "PressureScatter") == true
            val scatterVal = extractParamFloat(presetXml, "ScatterValue")
                ?: extractParamFloat(presetXml, "Scatter")
                ?: extractMaskGeneratorAttr(brushDefXml, "randomness")
            parsedScatter = when {
                scatterVal != null && scatterVal > 0.02f -> scatterVal.coerceIn(0f, 1.8f)
                scatterBool -> 0.45f
                else -> 0f
            }

            val rotParamFloat = extractParamFloat(presetXml, "RotationValue")
                ?: extractParamFloat(presetXml, "Rotation")
            val rotEnabled = extractParamBool(presetXml, "Rotation") == true ||
                presetXml.contains("RotationOption", ignoreCase = true) ||
                (rotParamFloat != null && rotParamFloat > 0.02f)
            if (rotEnabled) {
                if (presetXml.contains("drawing-angle", ignoreCase = true) ||
                    presetXml.contains("DrawingAngle", ignoreCase = true)
                ) {
                    parsedFollowAngle = true
                }
                if (presetXml.contains("fuzzy", ignoreCase = true) ||
                    presetXml.contains("FuzzyPerDab", ignoreCase = true) ||
                    (rotParamFloat != null && rotParamFloat > 0.02f)
                ) {
                    parsedAngularJitter = (rotParamFloat ?: 0.65f).coerceIn(0.15f, 1.0f)
                }
            }

            val smudgeVal = extractParamFloat(presetXml, "SmudgeRate")
                ?: extractParamFloat(presetXml, "SmudgeRateValue")
                ?: extractParamFloat(presetXml, "ColorRateValue")
            if (smudgeVal != null && smudgeVal > 0.02f) {
                parsedSmudgeRate = smudgeVal.coerceIn(0f, 1.0f)
            } else if ("smudge" in paintOpId) {
                parsedSmudgeRate = 0.55f
            }

            val texEnabled = extractParamBool(presetXml, "Texture/Pattern/Enabled") == true ||
                presetXml.contains("Texture/Pattern/PatternFileName", ignoreCase = true)
            if (texEnabled) {
                parsedTextureStrength = (
                    extractParamFloat(presetXml, "Texture/Pattern/Strength")
                        ?: 0.55f
                    ).coerceIn(0.15f, 1.0f)
            }

            // Dual Color / ColorSource parsing
            val colorSourceType = extractParamBody(presetXml, "ColorSource/Type")
                ?.trim()?.lowercase()
                ?: extractXmlAttr(presetXml, "colorsource")?.lowercase()
                ?: ""
            val fgBgColor = extractParamBool(presetXml, "ForegroundBackground") == true ||
                extractParamBool(presetXml, "Color/ForegroundBackground") == true ||
                colorSourceType.contains("gradient") ||
                colorSourceType.contains("fg_bg") ||
                colorSourceType.contains("mix")
            val mixVal = extractParamFloat(presetXml, "MixValue")
                ?: extractParamFloat(presetXml, "Color/MixValue")
                ?: extractParamFloat(presetXml, "DarkenValue")
            val dualColorHex = extractParamBody(presetXml, "SecondaryColor")?.trim()
                ?: extractXmlAttr(presetXml, "secondarycolor")
            var parsedDualColorOn = fgBgColor || (mixVal != null && mixVal > 0.02f) || !dualColorHex.isNullOrBlank()
            val parsedDualColorMode = when {
                colorSourceType.contains("gradient") -> "gradient"
                colorSourceType.contains("alt") || colorSourceType.contains("hue") -> "alternating"
                colorSourceType.contains("edge") || colorSourceType.contains("darken") -> "edge"
                else -> "gradient"
            }
            val parsedDualColorMix = (mixVal ?: 0.5f).coerceIn(0.05f, 1.0f)
            val parsedSecondaryColor = parseHexColorOrNull(dualColorHex) ?: 0xFFFFFFFFL

            // Internal special dots / spray / particle parameters
            val sprayParticleCount = extractParamFloat(presetXml, "sprayArea/particleCount")
                ?: extractParamFloat(presetXml, "Spray/particleCount")
                ?: extractParamFloat(presetXml, "Particle/count")
            val sprayEnabled = extractParamBool(presetXml, "Spray/enabled") == true ||
                extractParamBool(presetXml, "Particle/enabled") == true ||
                extractParamBool(presetXml, "InnerDots/enabled") == true ||
                (sprayParticleCount != null && sprayParticleCount >= 1f) ||
                "spray" in paintOpId || "particle" in paintOpId
            val sprayDensity = extractParamFloat(presetXml, "Spray/density")
                ?: extractParamFloat(presetXml, "BrushTip/density")
                ?: extractParamFloat(presetXml, "InnerDots/density")
                ?: sprayParticleCount?.let { (it / 24f).coerceIn(0.2f, 1.0f) }
            val sprayDotSize = extractParamFloat(presetXml, "Spray/particleSize")
                ?: extractParamFloat(presetXml, "InnerDots/size")
            val parsedInnerDotsOn = sprayEnabled || (sprayDensity != null && sprayDensity > 0.02f)

            // Masked / Dual Brush (Multi-format tip combination inside the same brush)
            val maskingEnabled = extractParamBool(presetXml, "MaskingBrush/Enabled") == true ||
                extractParamBool(presetXml, "DualBrush/Enabled") == true
            val maskDefXml = extractParamBody(presetXml, "MaskingBrush/brush_definition")
                ?: extractParamBody(presetXml, "DualBrush/brush_definition")
            val maskMasterSize = extractParamFloat(presetXml, "MaskingBrush/MasterSize")
            var parsedDualShapeOn = maskingEnabled || !maskDefXml.isNullOrBlank()
            var parsedSecondaryShapeKind = "circle"
            var parsedSecondaryTipBase64: String? = null
            var parsedDualShapeScale = 0.68f
            var parsedDualShapeBlendMode = "combine"

            val secSourceXml = maskDefXml ?: run {
                // Detect secondary MaskGenerator / Brush tag inside presetXml if present
                val allBrushTags = Regex("""<Brush\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(presetXml).toList()
                val allMaskTags = Regex("""<MaskGenerator\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(presetXml).toList()
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
                val compOp = extractParamBody(presetXml, "MaskingBrush/CompositeOp")?.trim()?.lowercase() ?: ""
                parsedDualShapeBlendMode = if (compOp.contains("mult") || compOp.contains("sub") || compOp.contains("mask") || maskingEnabled) {
                    "mask"
                } else {
                    "combine"
                }
            } else if (parsedDualShapeOn) {
                parsedDualShapeBlendMode = if (maskingEnabled) "mask" else "combine"
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

            val thumbBase64 = encodePngThumbnailBase64(rawBytes)
            val finalSize = ((parsedSize ?: defaultSizeFor(engineKind)) * 0.65f).coerceIn(2f, 48f)
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
            tipStampBase64 = parsedTipBase64
        )

        val thumbBase64 = encodePngThumbnailBase64(rawBytes)
        val finalSize = ((parsedSize ?: defaultSizeFor(engineKind)) * 0.65f).coerceIn(2f, 48f)
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
            textureNamePt = if (parsedTipBase64 != null) "Krita ($paintOpId • Ponta)" else "Krita ($paintOpId)",
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
        val clean = decodeXmlEntities(tipFilename).replace('\\', '/').trim().lowercase()
        if (clean.isEmpty()) return null
        map[clean]?.let { return it }
        val base = clean.substringAfterLast('/')
        map[base]?.let { return it }
        val noExt = base.substringBeforeLast('.')
        return map[noExt]
    }

    private fun extractTipStampBase64(bytes: ByteArray, hintName: String): Pair<String, Float>? {
        val lower = hintName.lowercase()
        return when {
            isPngSignature(bytes) || lower.endsWith(".png") -> extractPngTipStamp(bytes)
            lower.endsWith(".gih") -> extractGihTipStamp(bytes)
            else -> extractGbrTipStamp(bytes)
        }
    }

    private fun extractGihTipStamp(bytes: ByteArray): Pair<String, Float>? {
        // GIMP Image Hose starts with 2 text lines followed by concatenated .gbr blocks (GIMP magic at header + 20)
        for (i in 20..bytes.size - 4) {
            if (bytes[i] == 'G'.code.toByte() &&
                bytes[i + 1] == 'I'.code.toByte() &&
                bytes[i + 2] == 'M'.code.toByte() &&
                bytes[i + 3] == 'P'.code.toByte()
            ) {
                val start = i - 20
                if (start >= 0 && start + 28 <= bytes.size) {
                    return extractGbrTipStamp(bytes, startOffset = start)
                }
            }
        }
        return null
    }

    private fun extractGbrTipStamp(rawBytes: ByteArray, startOffset: Int = 0): Pair<String, Float>? {
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
            val pixelStart = startOffset + headerSize
            val expectedBytes = width * height * bpp
            if (pixelStart + expectedBytes > rawBytes.size) return null

            val pixels = IntArray(width * height)
            var pIdx = pixelStart
            if (bpp == 1) {
                for (i in 0 until width * height) {
                    val alpha = rawBytes[pIdx++].toInt() and 0xFF
                    pixels[i] = (alpha shl 24) or 0x00FFFFFF
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
                    val maskAlpha = if (hasTrueAlpha) a else (255 - lum)
                    pixels[i] = (maskAlpha.coerceIn(0, 255) shl 24) or 0x00FFFFFF
                }
            } else {
                return null
            }

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            val encoded = encodeTipBitmapTo40pxBase64(bmp)
            val aspect = (minOf(width, height).toFloat() / maxOf(width, height).toFloat()).coerceIn(0.08f, 1.0f)
            encoded to aspect
        } catch (_: Exception) {
            null
        }
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
        val maxDim = 40
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
        while (offset + 12 <= pngBytes.size) {
            val len = ByteBuffer.wrap(pngBytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (len < 0 || offset + 12 + len > pngBytes.size) break
            val chunkType = String(pngBytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            when (chunkType) {
                "tEXt" -> {
                    val zeroIdx = findByte(pngBytes, dataStart, dataStart + len, 0)
                    if (zeroIdx > dataStart) {
                        val key = String(pngBytes, dataStart, zeroIdx - dataStart, Charsets.ISO_8859_1)
                        if (key.equals("preset", ignoreCase = true)) {
                            return String(pngBytes, zeroIdx + 1, dataStart + len - (zeroIdx + 1), Charsets.UTF_8)
                        }
                    }
                }
                "zTXt" -> {
                    val zeroIdx = findByte(pngBytes, dataStart, dataStart + len, 0)
                    if (zeroIdx > dataStart && zeroIdx + 2 <= dataStart + len) {
                        val key = String(pngBytes, dataStart, zeroIdx - dataStart, Charsets.ISO_8859_1)
                        if (key.equals("preset", ignoreCase = true)) {
                            val compressedStart = zeroIdx + 2 // skip null + compression method
                            val compressedLen = (dataStart + len) - compressedStart
                            if (compressedLen > 0) {
                                return decompressZlib(pngBytes, compressedStart, compressedLen)
                            }
                        }
                    }
                }
                "iTXt" -> {
                    val zeroIdx = findByte(pngBytes, dataStart, dataStart + len, 0)
                    if (zeroIdx > dataStart && zeroIdx + 5 < dataStart + len) {
                        val key = String(pngBytes, dataStart, zeroIdx - dataStart, Charsets.UTF_8)
                        if (key.equals("preset", ignoreCase = true)) {
                            val compFlag = pngBytes[zeroIdx + 1].toInt()
                            // skip compFlag(1), compMethod(1), langTag + \0, transKey + \0
                            var cursor = zeroIdx + 3
                            val langEnd = findByte(pngBytes, cursor, dataStart + len, 0)
                            if (langEnd >= cursor) {
                                cursor = langEnd + 1
                                val transEnd = findByte(pngBytes, cursor, dataStart + len, 0)
                                if (transEnd >= cursor) {
                                    cursor = transEnd + 1
                                    val textLen = (dataStart + len) - cursor
                                    if (textLen > 0) {
                                        return if (compFlag == 1) {
                                            decompressZlib(pngBytes, cursor, textLen)
                                        } else {
                                            String(pngBytes, cursor, textLen, Charsets.UTF_8)
                                        }
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
        return null
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
            val text = String(rawBytes, Charsets.UTF_8).trim()
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
        val low = "$paintOpId $name".lowercase()
        return when {
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

    private fun decodeXmlBody(xml: String): String {
        val cdataStripped = xml.replace("<![CDATA[", "").replace("]]>", "")
        return decodeXmlEntities(cdataStripped)
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun extractXmlAttr(xml: String, attr: String): String? {
        val regex = Regex("""\b$attr\s*=\s*"([^"]*)"""")
        return regex.find(xml)?.groupValues?.getOrNull(1)
    }

    private fun extractParamBody(xml: String, paramName: String): String? {
        val esc = Regex.escape(paramName)
        val bodyRegex = Regex("""<param[^>]*\bname\s*=\s*"$esc"[^>]*>([\s\S]*?)</param>""", RegexOption.IGNORE_CASE)
        val body = bodyRegex.find(xml)?.groupValues?.getOrNull(1)
        if (!body.isNullOrBlank()) return body
        val attrRegex = Regex("""<param[^>]*\bname\s*=\s*"$esc"[^>]*\bvalue\s*=\s*"([^"]*)"[^>]*/?>""", RegexOption.IGNORE_CASE)
        return attrRegex.find(xml)?.groupValues?.getOrNull(1) ?: body
    }

    private fun extractMaskGeneratorAttr(xml: String, attr: String): Float? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<MaskGenerator[^>]*\b$esc\s*=\s*"([-0-9.]+)"""", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun extractMaskGeneratorAttrStr(xml: String, attr: String): String? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<MaskGenerator[^>]*\b$esc\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractBrushTagAttrFloat(xml: String, attr: String): Float? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<Brush[^>]*\b$esc\s*=\s*"([-0-9.]+)"""", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun extractBrushTagAttrStr(xml: String, attr: String): String? {
        val esc = Regex.escape(attr)
        val regex = Regex("""<Brush[^>]*\b$esc\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractParamBool(xml: String, paramName: String): Boolean? {
        val esc = Regex.escape(paramName)
        val bodyRegex = Regex("""<param[^>]*\bname\s*=\s*"$esc"[^>]*>([\s\S]*?)</param>""", RegexOption.IGNORE_CASE)
        val body = bodyRegex.find(xml)?.groupValues?.getOrNull(1)?.trim()
        if (body != null) {
            if (body.equals("true", ignoreCase = true) || body == "1") return true
            if (body.equals("false", ignoreCase = true) || body == "0") return false
        }
        val selfCloseRegex = Regex("""<param[^>]*\bname\s*=\s*"$esc"[^>]*\bvalue\s*=\s*"([^"]*)"[^>]*/?>""", RegexOption.IGNORE_CASE)
        val sc = selfCloseRegex.find(xml)?.groupValues?.getOrNull(1)?.trim() ?: return null
        if (sc.equals("true", ignoreCase = true) || sc == "1") return true
        if (sc.equals("false", ignoreCase = true) || sc == "0") return false
        return null
    }

    private fun extractParamFloat(xml: String, paramName: String, subAttr: String? = null): Float? {
        val esc = Regex.escape(paramName)
        val bodyRegex = Regex("""<param[^>]*\bname\s*=\s*"$esc"[^>]*>([\s\S]*?)</param>""", RegexOption.IGNORE_CASE)
        val bodyMatch = bodyRegex.find(xml)?.groupValues?.getOrNull(1)
        if (bodyMatch != null) {
            return if (subAttr != null) {
                Regex("""\b${Regex.escape(subAttr)}\s*=\s*"([-0-9.]+)"""", RegexOption.IGNORE_CASE)
                    .find(bodyMatch)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            } else {
                bodyMatch.trim().toFloatOrNull()
            }
        }
        val selfCloseRegex = Regex("""<param[^>]*\bname\s*=\s*"$esc"[^>]*\bvalue\s*=\s*"([-0-9.]+)"[^>]*/?>""", RegexOption.IGNORE_CASE)
        return selfCloseRegex.find(xml)?.groupValues?.getOrNull(1)?.toFloatOrNull()
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
