package com.example.model

import org.json.JSONObject

/**
 * Rendering engine kinds modeled after Krita's paintop presets.
 * Enables both the 12 native Krita presets and user-imported .kpp/.bundle brushes
 * to render identically across Host and Visitor devices.
 */
enum class KritaBrushEngineKind {
    PIXEL_ROUND_SIZE,    // Basic-5 Size (smooth variable pressure size)
    PIXEL_OPACITY,       // Basic-2 Opacity (pressure opacity glaze)
    INK_GPEN,            // Ink-3 G-Pen (tapered comic/anime ink)
    INK_ROUGH,           // Ink-4 Pen Rough (textured organic ink)
    PENCIL_GRAPHITE,     // Pencil-2B (textured graphite grain)
    CHARCOAL_SOFT,       // Charcoal Rock Soft (granular velvety charcoal)
    MARKER_CHISEL,       // Marker Chisel (angled translucent flat nib)
    BRISTLE_DRY,         // Dry Bristles (multi-bristle painterly strokes)
    BRISTLE_GLAZE,       // Bristles Glaze (layered wet-flat painting)
    CHALK_GRAINY,        // Chalk Grainy (porous pastel/chalk texture)
    AIRBRUSH_SOFT,       // Airbrush Soft (soft spray gradient)
    BLENDER_WET_SOFT     // Blender / Wet Soft (soft low-hardness blending halo)
}

/**
 * Portable brush parameters carried inside DrawingStroke.customBrushParams
 * so imported Krita brushes render with identical quality on LAN collaborators.
 */
data class BrushDynamicParams(
    val engineKind: KritaBrushEngineKind = KritaBrushEngineKind.PIXEL_ROUND_SIZE,
    val hardness: Float = 0.85f,       // 0.05..1.0
    val spacing: Float = 0.12f,        // 0.02..1.0
    val flow: Float = 1.0f,            // 0.05..1.0
    val grainAmount: Float = 0.0f,     // 0.0..1.0
    val bristleCount: Int = 5,         // 3..9
    val taperStrength: Float = 0.0f,   // 0.0..1.0
    val sizeScale: Float = 1.0f,       // 0.4..2.0
    val opacityScale: Float = 1.0f,    // 0.1..1.0
    val aspectRatio: Float = 1.0f,     // 0.08..1.0 (Krita MaskGenerator ratio / tip H:W)
    val angleDeg: Float = 0.0f,        // -180..180 deg
    val scatter: Float = 0.0f,         // 0.0..2.0
    val angularJitter: Float = 0.0f,   // 0.0..1.0
    val followStrokeAngle: Boolean = false,
    val smudgeRate: Float = 0.0f,      // 0.0..1.0 (colorsmudge/wet blend)
    val spikes: Int = 2,               // 2..12 (Krita MaskGenerator spikes)
    val shapeKind: String = "circle",  // "circle", "rect", "gauss", "soft", "predefined"
    val tipStampBase64: String? = null, // Compact 40x40 PNG alpha mask from .gbr/.gih/.png
    // 2 Colors per brush (Krita ColorSource / gradient / edge / alternating)
    val dualColorEnabled: Boolean = false,
    val secondaryColor: Long = 0xFFFFFFFFL,
    val secondaryColorMix: Float = 0.5f,   // 0.05..1.0
    val dualColorMode: String = "gradient", // "gradient", "edge", "alternating"
    // Internal special dots / speckles ("pontinhos especiais dentro do brush")
    val innerDotsEnabled: Boolean = false,
    val innerDotsDensity: Float = 0.45f,   // 0.1..1.0
    val innerDotsSize: Float = 0.22f,      // 0.08..0.6
    val innerDotsUseSecondaryColor: Boolean = true,
    // Multiple formats / shapes inside the same brush (Krita Masked/Dual brush)
    val dualShapeEnabled: Boolean = false,
    val secondaryShapeKind: String = "circle", // "circle", "rect", "gauss", "predefined"
    val secondaryTipStampBase64: String? = null,
    val dualShapeScale: Float = 0.68f,         // 0.2..1.5
    val dualShapeBlendMode: String = "combine" // "combine", "mask"
) {
    fun hasCustomKritaShapeOrTip(): Boolean {
        return !tipStampBase64.isNullOrBlank() ||
            scatter > 0.02f ||
            aspectRatio < 0.94f ||
            kotlin.math.abs(angleDeg) > 1.0f ||
            followStrokeAngle ||
            angularJitter > 0.03f ||
            spikes > 2 ||
            shapeKind == "rect" ||
            shapeKind == "gauss" ||
            dualColorEnabled ||
            innerDotsEnabled ||
            dualShapeEnabled ||
            !secondaryTipStampBase64.isNullOrBlank()
    }

    fun toCompactJson(): String = JSONObject().apply {
        put("eng", engineKind.name)
        put("hard", hardness.toDouble())
        put("spac", spacing.toDouble())
        put("flow", flow.toDouble())
        put("grain", grainAmount.toDouble())
        put("bris", bristleCount)
        put("taper", taperStrength.toDouble())
        put("sSc", sizeScale.toDouble())
        put("oSc", opacityScale.toDouble())
        if (aspectRatio < 0.99f) put("asp", aspectRatio.toDouble())
        if (kotlin.math.abs(angleDeg) > 0.05f) put("ang", angleDeg.toDouble())
        if (scatter > 0.005f) put("scat", scatter.toDouble())
        if (angularJitter > 0.005f) put("aj", angularJitter.toDouble())
        if (followStrokeAngle) put("fAng", true)
        if (smudgeRate > 0.005f) put("smud", smudgeRate.toDouble())
        if (spikes != 2) put("spk", spikes)
        if (shapeKind != "circle") put("shp", shapeKind)
        if (!tipStampBase64.isNullOrBlank()) put("tip", tipStampBase64)
        if (dualColorEnabled) {
            put("dcOn", true)
            put("dcCol", secondaryColor)
            put("dcMix", secondaryColorMix.toDouble())
            if (dualColorMode != "gradient") put("dcMod", dualColorMode)
        }
        if (innerDotsEnabled) {
            put("idOn", true)
            put("idDen", innerDotsDensity.toDouble())
            put("idSz", innerDotsSize.toDouble())
            if (!innerDotsUseSecondaryColor) put("idSec", false)
        }
        if (dualShapeEnabled) {
            put("dsOn", true)
            if (secondaryShapeKind != "circle") put("dsShp", secondaryShapeKind)
            if (!secondaryTipStampBase64.isNullOrBlank()) put("dsTip", secondaryTipStampBase64)
            put("dsSc", dualShapeScale.toDouble())
            if (dualShapeBlendMode != "combine") put("dsMod", dualShapeBlendMode)
        }
    }.toString()

    companion object {
        fun fromJsonOrNull(jsonStr: String?): BrushDynamicParams? {
            if (jsonStr.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(jsonStr)
                val engName = obj.optString("eng", KritaBrushEngineKind.PIXEL_ROUND_SIZE.name)
                val engine = try {
                    KritaBrushEngineKind.valueOf(engName)
                } catch (_: Exception) {
                    KritaBrushEngineKind.PIXEL_ROUND_SIZE
                }
                BrushDynamicParams(
                    engineKind = engine,
                    hardness = obj.optDouble("hard", 0.85).toFloat().coerceIn(0.05f, 1.0f),
                    spacing = obj.optDouble("spac", 0.12).toFloat().coerceIn(0.02f, 1.0f),
                    flow = obj.optDouble("flow", 1.0).toFloat().coerceIn(0.05f, 1.0f),
                    grainAmount = obj.optDouble("grain", 0.0).toFloat().coerceIn(0f, 1f),
                    bristleCount = obj.optInt("bris", 5).coerceIn(3, 9),
                    taperStrength = obj.optDouble("taper", 0.0).toFloat().coerceIn(0f, 1f),
                    sizeScale = obj.optDouble("sSc", 1.0).toFloat().coerceIn(0.3f, 2.5f),
                    opacityScale = obj.optDouble("oSc", 1.0).toFloat().coerceIn(0.1f, 1.0f),
                    aspectRatio = obj.optDouble("asp", 1.0).toFloat().coerceIn(0.08f, 1.0f),
                    angleDeg = obj.optDouble("ang", 0.0).toFloat().coerceIn(-180f, 180f),
                    scatter = obj.optDouble("scat", 0.0).toFloat().coerceIn(0f, 2.0f),
                    angularJitter = obj.optDouble("aj", 0.0).toFloat().coerceIn(0f, 1.0f),
                    followStrokeAngle = obj.optBoolean("fAng", false),
                    smudgeRate = obj.optDouble("smud", 0.0).toFloat().coerceIn(0f, 1.0f),
                    spikes = obj.optInt("spk", 2).coerceIn(2, 12),
                    shapeKind = obj.optString("shp", "circle").ifBlank { "circle" },
                    tipStampBase64 = if (obj.has("tip") && !obj.isNull("tip")) {
                        obj.optString("tip", "").ifBlank { null }
                    } else null,
                    dualColorEnabled = obj.optBoolean("dcOn", false),
                    secondaryColor = obj.optLong("dcCol", 0xFFFFFFFFL),
                    secondaryColorMix = obj.optDouble("dcMix", 0.5).toFloat().coerceIn(0.05f, 1.0f),
                    dualColorMode = obj.optString("dcMod", "gradient").ifBlank { "gradient" },
                    innerDotsEnabled = obj.optBoolean("idOn", false),
                    innerDotsDensity = obj.optDouble("idDen", 0.45).toFloat().coerceIn(0.1f, 1.0f),
                    innerDotsSize = obj.optDouble("idSz", 0.22).toFloat().coerceIn(0.08f, 0.6f),
                    innerDotsUseSecondaryColor = obj.optBoolean("idSec", true),
                    dualShapeEnabled = obj.optBoolean("dsOn", false),
                    secondaryShapeKind = obj.optString("dsShp", "circle").ifBlank { "circle" },
                    secondaryTipStampBase64 = if (obj.has("dsTip") && !obj.isNull("dsTip")) {
                        obj.optString("dsTip", "").ifBlank { null }
                    } else null,
                    dualShapeScale = obj.optDouble("dsSc", 0.68).toFloat().coerceIn(0.2f, 1.5f),
                    dualShapeBlendMode = obj.optString("dsMod", "combine").ifBlank { "combine" }
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * 12 Most-Used Krita Brush Presets (native) + support for .kpp / .bundle imported presets.
 */
data class BrushPreset(
    val id: Int,
    val namePt: String,
    val shortLabel: String,
    val defaultSize: Float,
    val defaultOpacity: Float,
    val toolType: ToolType = ToolType.PEN,
    val textureNamePt: String,
    val textureSymbol: String,
    val descriptionPt: String,
    val engineKind: KritaBrushEngineKind = KritaBrushEngineKind.PIXEL_ROUND_SIZE,
    val dynamicParams: BrushDynamicParams = BrushDynamicParams(engineKind = engineKind),
    val isImportedKrita: Boolean = false,
    val kritaPaintOpId: String = "paintbrush",
    val iconBase64: String? = null
) {
    companion object {
        val ALL_12_PRESETS = listOf(
            BrushPreset(
                id = 1,
                namePt = "b) Basic-5 Size",
                shortLabel = "Basic-5",
                defaultSize = 7f,
                defaultOpacity = 1.0f,
                toolType = ToolType.PEN,
                textureNamePt = "Krita Basic-5 (Tamanho/Pressão)",
                textureSymbol = "●",
                descriptionPt = "Pincel redondo clássico do Krita com controle suave de espessura por pressão.",
                engineKind = KritaBrushEngineKind.PIXEL_ROUND_SIZE,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.PIXEL_ROUND_SIZE,
                    hardness = 0.92f,
                    spacing = 0.08f,
                    flow = 1.0f,
                    taperStrength = 0.12f
                )
            ),
            BrushPreset(
                id = 2,
                namePt = "b) Basic-2 Opacity",
                shortLabel = "Basic-2",
                defaultSize = 14f,
                defaultOpacity = 0.78f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Krita Basic-2 (Fluxo/Opacidade)",
                textureSymbol = "◐",
                descriptionPt = "Pintura em camadas suaves com variação dinâmica de opacidade por pressão.",
                engineKind = KritaBrushEngineKind.PIXEL_OPACITY,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.PIXEL_OPACITY,
                    hardness = 0.72f,
                    spacing = 0.10f,
                    flow = 0.70f,
                    opacityScale = 0.85f
                )
            ),
            BrushPreset(
                id = 3,
                namePt = "g) Ink-3 G-Pen",
                shortLabel = "G-Pen",
                defaultSize = 6f,
                defaultOpacity = 1.0f,
                toolType = ToolType.PEN,
                textureNamePt = "Krita Ink-3 G-Pen (Arte-Final)",
                textureSymbol = "✒",
                descriptionPt = "Bico de pena japonês do Krita com pontas afiadas e alta nitidez para lineart.",
                engineKind = KritaBrushEngineKind.INK_GPEN,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.INK_GPEN,
                    hardness = 1.0f,
                    spacing = 0.06f,
                    flow = 1.0f,
                    taperStrength = 0.75f
                )
            ),
            BrushPreset(
                id = 4,
                namePt = "g) Ink-4 Pen Rough",
                shortLabel = "Ink Rough",
                defaultSize = 6.5f,
                defaultOpacity = 1.0f,
                toolType = ToolType.PEN,
                textureNamePt = "Krita Ink-4 Rough (Nanquim Orgânico)",
                textureSymbol = "〰",
                descriptionPt = "Caneta nanquim com borda levemente texturizada de papel para estilo HQs.",
                engineKind = KritaBrushEngineKind.INK_ROUGH,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.INK_ROUGH,
                    hardness = 0.90f,
                    spacing = 0.09f,
                    flow = 0.96f,
                    grainAmount = 0.35f,
                    taperStrength = 0.35f
                )
            ),
            BrushPreset(
                id = 5,
                namePt = "d) Pencil-2B",
                shortLabel = "Pencil-2B",
                defaultSize = 4.5f,
                defaultOpacity = 0.88f,
                toolType = ToolType.PENCIL,
                textureNamePt = "Krita Pencil-2B (Grafite Realista)",
                textureSymbol = "✏",
                descriptionPt = "Lápis grafite 2B do Krita com grão natural para rascunho e animatic.",
                engineKind = KritaBrushEngineKind.PENCIL_GRAPHITE,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.PENCIL_GRAPHITE,
                    hardness = 0.68f,
                    spacing = 0.12f,
                    flow = 0.82f,
                    grainAmount = 0.55f
                )
            ),
            BrushPreset(
                id = 6,
                namePt = "e) Charcoal Rock Soft",
                shortLabel = "Charcoal",
                defaultSize = 16f,
                defaultOpacity = 0.86f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Krita Charcoal Rock Soft (Carvão)",
                textureSymbol = "▓",
                descriptionPt = "Carvão mineral macio com textura porosa rica para sombreamento e cenários.",
                engineKind = KritaBrushEngineKind.CHARCOAL_SOFT,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.CHARCOAL_SOFT,
                    hardness = 0.50f,
                    spacing = 0.14f,
                    flow = 0.84f,
                    grainAmount = 0.72f
                )
            ),
            BrushPreset(
                id = 7,
                namePt = "f) Marker Chisel",
                shortLabel = "Marker",
                defaultSize = 18f,
                defaultOpacity = 0.65f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Krita Marker Chisel (Chanfrado)",
                textureSymbol = "▰",
                descriptionPt = "Marcador chanfrado translúcido estilo Copic para bloqueio de sombras e luzes.",
                engineKind = KritaBrushEngineKind.MARKER_CHISEL,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.MARKER_CHISEL,
                    hardness = 0.90f,
                    spacing = 0.08f,
                    flow = 0.65f
                )
            ),
            BrushPreset(
                id = 8,
                namePt = "f) Dry Bristles",
                shortLabel = "Dry Brist.",
                defaultSize = 20f,
                defaultOpacity = 0.90f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Pincel Cerdas Secas (Pintura)",
                textureSymbol = "≣",
                descriptionPt = "Pincel de cerdas secas orgânicas com falha de pigmento para pintura tradicional.",
                engineKind = KritaBrushEngineKind.BRISTLE_DRY,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.BRISTLE_DRY,
                    hardness = 0.80f,
                    spacing = 0.08f,
                    flow = 0.88f,
                    bristleCount = 9,
                    grainAmount = 0.35f,
                    taperStrength = 0.35f
                )
            ),
            BrushPreset(
                id = 9,
                namePt = "f) Bristles Glaze",
                shortLabel = "Glaze",
                defaultSize = 22f,
                defaultOpacity = 0.72f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Veladura Plana Suave",
                textureSymbol = "▀",
                descriptionPt = "Pincelada larga, sedosa e translúcida sem emendas para fundos e pintura atmosférica.",
                engineKind = KritaBrushEngineKind.BRISTLE_GLAZE,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.BRISTLE_GLAZE,
                    hardness = 0.72f,
                    spacing = 0.08f,
                    flow = 0.72f,
                    bristleCount = 7
                )
            ),
            BrushPreset(
                id = 10,
                namePt = "e) Chalk Grainy",
                shortLabel = "Chalk",
                defaultSize = 15f,
                defaultOpacity = 0.84f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Giz Pastel Texturizado",
                textureSymbol = "▒",
                descriptionPt = "Giz pastel cremoso com granulação orgânica de papel para sombreamento rico.",
                engineKind = KritaBrushEngineKind.CHALK_GRAINY,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.CHALK_GRAINY,
                    hardness = 0.62f,
                    spacing = 0.09f,
                    flow = 0.82f,
                    grainAmount = 0.78f
                )
            ),
            BrushPreset(
                id = 11,
                namePt = "a) Airbrush Soft",
                shortLabel = "Airbrush",
                defaultSize = 32f,
                defaultOpacity = 0.42f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Aerógrafo Suave Gaussiano",
                textureSymbol = "∴",
                descriptionPt = "Aerógrafo aveludado contínuo para gradientes, brilho luminoso e sombras difusas.",
                engineKind = KritaBrushEngineKind.AIRBRUSH_SOFT,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.AIRBRUSH_SOFT,
                    hardness = 0.14f,
                    spacing = 0.07f,
                    flow = 0.45f
                )
            ),
            BrushPreset(
                id = 12,
                namePt = "k) Wet Soft Aquarela",
                shortLabel = "Wet Soft",
                defaultSize = 24f,
                defaultOpacity = 0.56f,
                toolType = ToolType.BRUSH,
                textureNamePt = "Pincel Úmido Aquarelado",
                textureSymbol = "◈",
                descriptionPt = "Pincel úmido suave com borda orgânica aquarelada e transição cremosa de tons.",
                engineKind = KritaBrushEngineKind.BLENDER_WET_SOFT,
                dynamicParams = BrushDynamicParams(
                    engineKind = KritaBrushEngineKind.BLENDER_WET_SOFT,
                    hardness = 0.24f,
                    spacing = 0.07f,
                    flow = 0.58f,
                    opacityScale = 0.76f
                )
            )
        )

        fun findById(id: Int, importedBrushes: List<BrushPreset> = emptyList()): BrushPreset {
            return importedBrushes.firstOrNull { it.id == id }
                ?: ALL_12_PRESETS.firstOrNull { it.id == id }
                ?: ALL_12_PRESETS.first()
        }

        fun applyOverride(preset: BrushPreset, override: BrushOverride?): BrushPreset {
            if (override == null || override.brushId != preset.id) return preset
            return preset.copy(
                defaultSize = override.defaultSize.coerceIn(1f, 100f),
                defaultOpacity = override.defaultOpacity.coerceIn(0.05f, 1.0f),
                engineKind = override.dynamicParams.engineKind,
                dynamicParams = override.dynamicParams
            )
        }

        fun getFactoryNativePresetById(id: Int): BrushPreset? {
            return ALL_12_PRESETS.firstOrNull { it.id == id }
        }
    }
}

data class BrushOverride(
    val brushId: Int,
    val defaultSize: Float,
    val defaultOpacity: Float,
    val dynamicParams: BrushDynamicParams
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", brushId)
        put("size", defaultSize.toDouble())
        put("opacity", defaultOpacity.toDouble())
        put("dyn", dynamicParams.toCompactJson())
    }

    companion object {
        fun fromJsonOrNull(obj: JSONObject): BrushOverride? {
            return try {
                val id = obj.getInt("id")
                val size = obj.optDouble("size", 8.0).toFloat().coerceIn(1f, 100f)
                val opacity = obj.optDouble("opacity", 1.0).toFloat().coerceIn(0.05f, 1.0f)
                val dyn = BrushDynamicParams.fromJsonOrNull(obj.optString("dyn", ""))
                    ?: BrushDynamicParams()
                BrushOverride(brushId = id, defaultSize = size, defaultOpacity = opacity, dynamicParams = dyn)
            } catch (_: Exception) {
                null
            }
        }
    }
}

