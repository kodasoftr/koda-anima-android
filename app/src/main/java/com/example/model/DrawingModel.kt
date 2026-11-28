package com.example.model

import java.util.UUID

enum class ToolType(val labelPt: String, val labelEn: String) {
    PEN("Caneta", "Pen"),
    PENCIL("Lápis", "Pencil"),
    BEZIER_PEN("Caneta Vetorial", "Bezier Pen"),
    BRUSH("Pincel", "Brush"),
    ERASER("Borracha", "Eraser"),
    BUCKET("Balde", "Bucket"),
    MAGIC_WAND("Varinha Mágica", "Magic Wand"),
    LASSO("Laço", "Lasso"),
    LINE("Linha", "Line"),
    RECTANGLE("Retângulo", "Rectangle"),
    CIRCLE("Círculo", "Circle"),
    EYEDROPPER("Conta-gotas", "Eyedropper")
}

/**
 * Interactive cubic Bezier node used by the Caneta Vetorial (Bezier Pen) tool.
 */
data class BezierNode(
    val id: String = UUID.randomUUID().toString(),
    val anchorX: Float,
    val anchorY: Float,
    val handleInX: Float = anchorX,
    val handleInY: Float = anchorY,
    val handleOutX: Float = anchorX,
    val handleOutY: Float = anchorY
)

fun sampleCubicBezierNodesToStrokePoints(
    nodes: List<BezierNode>,
    closed: Boolean = false,
    samplesPerSegment: Int = 20
): List<StrokePoint> {
    if (nodes.isEmpty()) return emptyList()
    if (nodes.size == 1) return listOf(StrokePoint(nodes[0].anchorX, nodes[0].anchorY, 1.0f))

    val out = ArrayList<StrokePoint>(nodes.size * samplesPerSegment + 4)
    val segCount = if (closed && nodes.size >= 2) nodes.size else nodes.size - 1
    val steps = samplesPerSegment.coerceIn(4, 64)

    for (seg in 0 until segCount) {
        val a = nodes[seg]
        val b = nodes[(seg + 1) % nodes.size]
        val p0x = a.anchorX
        val p0y = a.anchorY
        val c1x = a.handleOutX
        val c1y = a.handleOutY
        val c2x = b.handleInX
        val c2y = b.handleInY
        val p3x = b.anchorX
        val p3y = b.anchorY

        val startStep = if (seg == 0) 0 else 1
        for (i in startStep..steps) {
            val t = i.toFloat() / steps.toFloat()
            val u = 1f - t
            val tt = t * t
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * t
            val x = uuu * p0x + 3f * uu * t * c1x + 3f * u * tt * c2x + ttt * p3x
            val y = uuu * p0y + 3f * uu * t * c1y + 3f * u * tt * c2y + ttt * p3y
            out.add(StrokePoint(x = x, y = y, pressure = 1.0f))
        }
    }
    return out
}

data class SelectionRegion(
    val isActive: Boolean = false,
    val points: List<StrokePoint> = emptyList(),
    val minX: Float = 0f,
    val minY: Float = 0f,
    val maxX: Float = 0f,
    val maxY: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val capturedStrokes: List<DrawingStroke> = emptyList(),
    val hiddenSourceStrokeIds: Set<String> = emptySet(),
    val outsideReplacementStrokes: List<DrawingStroke> = emptyList(),
    val isInverted: Boolean = false
) {
    val width: Float get() = (maxX - minX).coerceAtLeast(1f)
    val height: Float get() = (maxY - minY).coerceAtLeast(1f)
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class DrawingStroke(
    val id: String = UUID.randomUUID().toString(),
    val authorId: String = "",
    val tool: ToolType = ToolType.PEN,
    val color: Long = 0xFFFFFFFF, // ARGB Long
    val brushSize: Float = 6f,
    val opacity: Float = 1.0f,
    val points: List<StrokePoint> = emptyList(),
    val isFilled: Boolean = false,
    val brushPresetId: Int = 1,
    val customBrushParams: String? = null,
    val clipPolygon: List<StrokePoint>? = null,
    val clipInverted: Boolean = false,
    val clipBoundsWidth: Float = 0f,
    val clipBoundsHeight: Float = 0f,
    val minX: Float = Float.MAX_VALUE,
    val minY: Float = Float.MAX_VALUE,
    val maxX: Float = -Float.MAX_VALUE,
    val maxY: Float = -Float.MAX_VALUE
) {
    /**
     * Fast AABB intersection check for viewport chunk culling.
     */
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (points.isEmpty()) return false
        val halfB = brushSize * 1.6f + 16f
        val bMinX = (if (minX != Float.MAX_VALUE) minX else points.minOf { it.x }) - halfB
        val bMinY = (if (minY != Float.MAX_VALUE) minY else points.minOf { it.y }) - halfB
        val bMaxX = (if (maxX != -Float.MAX_VALUE) maxX else points.maxOf { it.x }) + halfB
        val bMaxY = (if (maxY != -Float.MAX_VALUE) maxY else points.maxOf { it.y }) + halfB
        return bMaxX >= left && bMinX <= right && bMaxY >= top && bMinY <= bottom
    }
}

enum class LayerBlendEffect(val labelPt: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiplicar"),
    SCREEN("Tela"),
    OVERLAY("Sobrepor"),
    DARKEN("Escurecer"),
    LIGHTEN("Clarear"),
    COLOR_DODGE("Subexposição"),
    SOFT_LIGHT("Luz Suave"),
    ADD("Adicionar Brilho"),
    DIFFERENCE("Diferença")
}

data class LayerImageAttachment(
    val uriString: String = "",
    val scalePercent: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val opacity: Float = 1.0f
) {
    /**
     * Normalizes both normalized multipliers (0.1f..5.0f, where 1.0f = 100%)
     * and legacy percentage values (> 5f, e.g. 100f) to a consistent scale factor.
     */
    fun normalizedScaleFactor(): Float {
        val raw = if (scalePercent > 5.0f) scalePercent / 100f else scalePercent
        return raw.coerceIn(0.1f, 5.0f)
    }
}

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Camada 1",
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1.0f,
    val strokes: List<DrawingStroke> = emptyList(),
    val referenceImageUri: String? = null,
    val blendEffect: LayerBlendEffect = LayerBlendEffect.NORMAL,
    val isClippingMask: Boolean = false,
    val imageAttachment: LayerImageAttachment? = null,
    val isBucketLayer: Boolean = false,
    val isBucketReference: Boolean = false,
    val celHoldTicks: Int? = null,
    val celStartTick: Int? = null,
    val placeBelowFrame: Boolean = false
)

enum class BucketReferenceMode(val labelPt: String) {
    ALL_LAYERS("Todas as camadas"),
    SELECTED_LAYERS("Camadas selecionadas")
}

data class BucketFillConfig(
    val expandPixels: Int = 1,
    val tolerance: Int = 36,
    val closeGapsPixels: Int = 0,
    val paintUnderneathLineArt: Boolean = false,
    val referenceMode: BucketReferenceMode = BucketReferenceMode.ALL_LAYERS,
    val selectedReferenceLayerIds: Set<String> = emptySet(),
    val activeBucketTargetLayerId: String? = null
)

data class Frame(
    val id: String = UUID.randomUUID().toString(),
    val layers: List<Layer> = listOf(Layer()),
    val holdFrames: Int = 1, // Exposure hold (1 = 1 frame, 2 = held on 2s)
    val localLayers: List<Layer> = emptyList() // Per-frame isolated sub-layers (independent of timeline tracks)
)

data class SpecialMediaAsset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Mídia",
    val uriString: String = "",
    val type: String = "audio", // "audio", "video", "gif"
    val startSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val opacityPercent: Int = 100,
    val scalePercent: Double = 100.0,
    val rotationDegrees: Double = 0.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val audioVolumePercent: Int = 100,
    val audioMuted: Boolean = false,
    val waveformPeaks: List<Float> = emptyList()
)

data class ProjectResolution(
    val width: Int = 1280,
    val height: Int = 720,
    val name: String = "HD 720p (16:9)",
    val isCustom: Boolean = false
) {
    companion object {
        val PRESETS = listOf(
            ProjectResolution(1280, 720, "HD 720p (16:9)"),
            ProjectResolution(1920, 1080, "Full HD 1080p (16:9)"),
            ProjectResolution(1080, 1080, "Instagram Post (1:1)"),
            ProjectResolution(1080, 1920, "Stories / TikTok (9:16)"),
            ProjectResolution(1024, 768, "Clássico 4:3"),
            ProjectResolution(640, 360, "Rápido / Leve (16:9)"),
            ProjectResolution(320, 320, "Pixel Art (1:1)")
        )
    }
}

data class OnionSkinSettings(
    val isEnabled: Boolean = true,
    val prevFramesCount: Int = 2,
    val nextFramesCount: Int = 1,
    val opacity: Float = 0.35f,
    val prevColor: Long = 0xFFFF3366, // Neon red/pink for past frames
    val nextColor: Long = 0xFF00D2FF  // Neon cyan for future frames
)

data class ReferenceImageState(
    val uriString: String? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 0.85f,
    val isVisible: Boolean = false,
    val isLocked: Boolean = false,
    val renderAboveCanvas: Boolean = true
)

enum class CanvasBackgroundStyle(val labelPt: String) {
    SOLIDO("Cor Sólida"),
    DEGRADE("Degradê"),
    TEXTURA("Textura de Fundo"),
    TRANSPARENTE("Transparente")
}

enum class CanvasTexturePattern(val labelPt: String) {
    NENHUMA("Sem Textura"),
    PAPEL_GRANULADO("Papel Granulado"),
    GRADE_FINA("Grade Fina"),
    PONTILHADO("Pontilhado"),
    LINHAS_CADERNO("Folha Pautada")
}

enum class SkyBackdropMode(val labelPt: String) {
    ESTUDIO_ESCURO("Estúdio Escuro"),
    GRADE_ESPACIAL("Grade Espacial"),
    GRADIENTE_CEU("Gradiente Céu"),
    ESTRELAS_NOTURNO("Noturno Estrelas"),
    SOLIDO("Cor Única")
}

enum class Canvas3DBackgroundRenderMode(val labelPt: String, val descPt: String) {
    STATIC_2D_CACHE(
        labelPt = "1x 2D Estático",
        descPt = "Renderiza 1 fundo 3D em 2D leve e reaproveita em todos os quadros"
    ),
    PRE_RENDER_ON_PLAY(
        labelPt = "Pré-render Play",
        descPt = "Ao tocar Play, pré-renderiza os quadros 3D em 2D leve em cache"
    ),
    REALTIME_3D(
        labelPt = "Tempo Real 3D",
        descPt = "Atualiza o fundo 3D em tempo real (ideal para personagens 3D animados)"
    )
}

data class GradientColorStop(
    val position: Float = 0.5f,
    val color: Long = 0xFF3A528CL,
    val id: String = UUID.randomUUID().toString()
)

data class CanvasBackgroundSettings(
    val style: CanvasBackgroundStyle = CanvasBackgroundStyle.SOLIDO,
    val primaryColor: Long = 0xFFFFFFFFL,
    val secondaryColor: Long = 0xFFE3ECFFL,
    val gradientAngleDeg: Float = 90f,
    val texturePattern: CanvasTexturePattern = CanvasTexturePattern.NENHUMA,
    val textureIntensity: Float = 0.28f,
    val skyMode: SkyBackdropMode = SkyBackdropMode.GRADE_ESPACIAL,
    val skyTopColor: Long = 0xFF151926L,
    val skyBottomColor: Long = 0xFF28314AL,
    val skyLightColor: Long = 0xFF3E4E75L,
    val skyDarkColor: Long = 0xFF0E1320L,
    val skyGradientStops: List<GradientColorStop> = emptyList(),
    val skyGradientAngleDeg: Float = 90f,
    val show3DInCanvas: Boolean = false,
    val canvas3DRenderMode: Canvas3DBackgroundRenderMode = Canvas3DBackgroundRenderMode.STATIC_2D_CACHE
)

enum class TimelineSymbolType(val labelPt: String) {
    COMMON("Símbolo Comum"),
    EFFECTS("Símbolo com Efeitos")
}

data class TimelineSymbolEffectSettings(
    val opacity: Float = 1.0f,
    val tintColorArgb: Long = 0xFFFFFFFFL,
    val tintStrength: Float = 0.0f,
    val glowRadius: Float = 0.0f,
    val glowColorArgb: Long = 0xFF00E5FFL,
    val blurRadius: Float = 0.0f,
    val dropShadowRadius: Float = 0.0f,
    val dropShadowOffsetX: Float = 4.0f,
    val dropShadowOffsetY: Float = 4.0f,
    val dropShadowColorArgb: Long = 0xAA000000L,
    val blendEffect: LayerBlendEffect = LayerBlendEffect.NORMAL
) {
    fun hasVisualEffects(): Boolean =
        opacity < 0.995f ||
            tintStrength > 0.01f ||
            glowRadius > 0.2f ||
            blurRadius > 0.2f ||
            dropShadowRadius > 0.2f ||
            blendEffect != LayerBlendEffect.NORMAL
}

data class SymbolInstanceKeyframe(
    val frameIndex: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationZ: Float = 0f,
    val opacity: Float = 1f,
    val glowRadius: Float = 0f,
    val blurRadius: Float = 0f,
    val easing: CameraEasingCurve = CameraEasingCurve.BEZIER_SUAVE,
    val bezierP1X: Float = 0.25f,
    val bezierP1Y: Float = 0.1f,
    val bezierP2X: Float = 0.25f,
    val bezierP2Y: Float = 1.0f,
    val holdTicks: Int = 1
)

data class TimelineSymbolDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Símbolo 1",
    val type: TimelineSymbolType = TimelineSymbolType.COMMON,
    val internalFrames: List<Frame> = listOf(Frame()),
    val totalInternalTicks: Int = 1,
    val loopInternalAnimation: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class TimelineSymbolInstance(
    val id: String = UUID.randomUUID().toString(),
    val symbolId: String = "",
    val name: String = "Símbolo 1",
    val startTick: Int = 0,
    val durationTicks: Int = 1,
    val autoInterpolate: Boolean = true,
    val isVisible: Boolean = true,
    val effects: TimelineSymbolEffectSettings = TimelineSymbolEffectSettings(),
    val keyframes: List<SymbolInstanceKeyframe> = listOf(SymbolInstanceKeyframe(frameIndex = 0))
)

data class AnimationProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Nova Animação",
    val resolution: ProjectResolution = ProjectResolution.PRESETS[0],
    val fps: Int = 12,
    val frames: List<Frame> = listOf(Frame()),
    val mediaAssets: List<SpecialMediaAsset> = emptyList(),
    val onionSkin: OnionSkinSettings = OnionSkinSettings(),
    val camera3D: Camera3DSettings = Camera3DSettings(),
    val canvasBackground: CanvasBackgroundSettings = CanvasBackgroundSettings(),
    val lastActiveFrameIndex: Int = 0,
    val symbols: List<TimelineSymbolDefinition> = emptyList(),
    val symbolInstances: List<TimelineSymbolInstance> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class GridRulerSettings(
    val isEnabled: Boolean = false,
    val cellSizePx: Float = 48f,
    val color: Long = 0xFF00D2FF,
    val opacity: Float = 0.45f,
    val renderBehindActiveLayer: Boolean = true,
    val useLineCount: Boolean = false,
    val lineCountX: Int = 12,
    val lineCountY: Int = 8,
    val lineThicknessDp: Float = 1.2f
)

data class PerspectiveRulerSettings(
    val isEnabled: Boolean = false,
    val vanishingPointsCount: Int = 1, // 1, 2, or 3 vanishing points
    val horizonYRatio: Float = 0.50f,
    val vp1XRatio: Float = 0.50f,
    val vp1YRatio: Float = 0.50f,
    val vp2XRatio: Float = 0.88f,
    val vp2YRatio: Float = 0.50f,
    val vp3XRatio: Float = 0.50f,
    val vp3YRatio: Float = 0.14f,
    val color: Long = 0xFFFF9100,
    val opacity: Float = 0.55f,
    val snapToGuide: Boolean = false,
    val vp1Color: Long = 0xFFFF9100,
    val vp2Color: Long = 0xFF00D2FF,
    val vp3Color: Long = 0xFFFF3366,
    val renderBehindActiveLayer: Boolean = false
)

data class FriendBrushOffer(
    val senderId: String,
    val senderName: String,
    val brushPreset: BrushPreset
)

enum class CameraMode(val labelPt: String) {
    MODE_2D("2D Plano"),
    MODE_3D("3D Espacial")
}

enum class CameraViewportNavTarget(val labelPt: String) {
    CAMERA("Mover Câmera"),
    CANVAS("Mover Canvas")
}

enum class StageTargetType(val labelPt: String) {
    CAMERA("Câmera"),
    LAYER("Camada"),
    OBJECT_3D("Objeto 3D")
}

data class StageSelection(
    val type: StageTargetType = StageTargetType.CAMERA,
    val id: String = "camera"
)

enum class CameraToolAction(val labelPt: String) {
    PAN_2D("Mover"),
    ZOOM("Zoom"),
    ROTATE_Z("Girar"),
    FOV_LENS("Lente FOV"),
    ORBIT_3D("Órbita 3D")
}

enum class CameraEasingCurve(val labelPt: String) {
    LINEAR("Linear"),
    BEZIER_SUAVE("Bézier Suave"),
    BEZIER_GRAFICO("Curva Gráfica"),
    ACELERAR("Acelerar"),
    DESACELERAR("Desacelerar"),
    DEGRAU("Degrau"),
    ELASTICO("Elástico")
}

enum class CameraBuiltInEffect(val labelPt: String, val descPt: String) {
    ZOOM_IN("Zoom Aproximar", "Aproxima suavemente do quadro inicial ao final"),
    ZOOM_OUT("Zoom Afastar", "Afasta gradualmente revelando o cenário"),
    PAN_CINEMA("Panorâmica Horizontal", "Desloca a câmera horizontalmente estilo cinema"),
    TREMOR_IMPACTO("Tremor de Impacto", "Vibração rápida de câmera para cenas de ação"),
    ORBITA_3D("Órbita 3D", "Inclinação em perspectiva 3D usando profundidade das camadas"),
    DOLLY_TWIST("Dolly com Giro", "Aproximação combinada com rotação dramática")
}

data class CameraPostEffectSettings(
    val vignette: Float = 0f,           // 0..1
    val bloom: Float = 0f,              // 0..1
    val chromaticAberration: Float = 0f,// 0..1
    val filmGrain: Float = 0f,          // 0..1
    val sepia: Float = 0f,              // 0..1
    val radialBlur: Float = 0f,         // 0..1
    val contrastBoost: Float = 0f       // -0.5..0.5
) {
    fun hasAnyEffect(): Boolean {
        return vignette > 0.01f ||
            bloom > 0.01f ||
            chromaticAberration > 0.01f ||
            filmGrain > 0.01f ||
            sepia > 0.01f ||
            radialBlur > 0.01f ||
            kotlin.math.abs(contrastBoost) > 0.01f
    }
}

data class Camera3DKeyframe(
    val frameIndex: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val zoom: Float = 1.0f,
    val rotationZ: Float = 0f,
    val pitchX: Float = 0f,
    val yawY: Float = 0f,
    val depthSeparation: Float = 38f,
    val fovDegrees: Float = 50f,
    val easing: CameraEasingCurve = CameraEasingCurve.BEZIER_SUAVE,
    val bezierP1X: Float = 0.25f,
    val bezierP1Y: Float = 0.1f,
    val bezierP2X: Float = 0.25f,
    val bezierP2Y: Float = 1.0f,
    val holdTicks: Int = 1
)

data class ObjectTransformKeyframe(
    val frameIndex: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f,
    val opacity: Float = 1f,
    val easing: CameraEasingCurve = CameraEasingCurve.BEZIER_SUAVE,
    val holdTicks: Int = 1
)

data class LayerTransformTrack(
    val layerId: String = "",
    val keyframes: List<ObjectTransformKeyframe> = emptyList()
)

enum class StageShadingMode(val labelPt: String) {
    WIREFRAME("Vértices"),
    SOLID("Sólido"),
    TEXTURE("Textura"),
    RENDERED("Luz & Shader")
}

enum class TransformSpaceMode(val labelPt: String) {
    GLOBAL("Global"),
    LOCAL("Próprio Eixo")
}

enum class ObjectShaderMode(val labelPt: String) {
    PADRAO("Padrão PBR"),
    ANIME_CEL("Cel Anime"),
    CARTOON_BRUSH("Cartoon Brush"),
    SEM_LUZ("Sem Luz (Flat)"),
    ARAMADO("Aramado (Wire)")
}

data class ObjectOutlineSettings(
    val enabled: Boolean = false,
    val width: Float = 2.0f,
    val colorArgb: Long = 0xFF10131CL
)

data class ObjectLightSettings(
    val azimuthDeg: Float = 40f,
    val elevationDeg: Float = 52f,
    val intensity: Float = 1.0f,
    val ambient: Float = 0.28f,
    val rimStrength: Float = 0.25f,
    val emissiveGlow: Float = 0f,
    val lightColorArgb: Long = 0xFFFFFFFFL
)

enum class UvUnwrapProjection(val labelPt: String) {
    ORIGINAL("UV Original"),
    PLANAR("Planar"),
    BOX("Caixa (Box)"),
    ESFERICO("Esférico"),
    CILINDRICO("Cilíndrico")
}

data class ObjectUVMapSlot(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "UVMap Principal",
    val projection: UvUnwrapProjection = UvUnwrapProjection.ORIGINAL,
    val scaleU: Float = 1f,
    val scaleV: Float = 1f,
    val offsetU: Float = 0f,
    val offsetV: Float = 0f,
    val textureBase64: String? = null,
    val textureUri: String? = null,
    val customUvs: List<Float> = emptyList()
)

enum class Estudio3DPaintTool(val labelPt: String) {
    PINCEL("Pincel"),
    BORRACHA("Borracha"),
    MOLHADOR("Molhador")
}

data class ObjectTexturePaintLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Pintura 1",
    val isVisible: Boolean = true,
    val opacity: Float = 1f,
    val blendEffect: LayerBlendEffect = LayerBlendEffect.NORMAL,
    val pixelsBase64: String? = null,
    val texWidth: Int = 256,
    val texHeight: Int = 256
)

enum class ShaderNodeKind(val labelPt: String, val accentArgb: Long) {
    UV_COORD("Coord. UV", 0xFF8E44ADL),
    TEXTURA_UV("Textura UV", 0xFFD35400L),
    LUZ_OBJETO("Luz Própria", 0xFFF39C12L),
    CEL_RAMP("Rampa Cel/Anime", 0xFF00BCD4L),
    BRUSH_GRAIN("Traço Cartoon", 0xFFE91E63L),
    CONTORNO("Contorno", 0xFF2ECC71L),
    PRINCIPLED_BSDF("Principled BSDF", 0xFF3498DBL),
    FRESNEL("Fresnel / Borda", 0xFF1ABC9CL),
    MIX_RGB("Misturar Cores", 0xFF9B59B6L),
    NOISE_PROC("Ruído Procedural", 0xFF7F8C8DL),
    EMISSAO("Emissão / Brilho", 0xFFF1C40FL),
    SAIDA_MATERIAL("Saída Material", 0xFFC0392BL)
}

data class CustomShaderNodePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Meu Node",
    val graph: ShaderNodeGraph = ShaderNodeGraph()
)

data class ShaderNode(
    val id: String,
    val kind: ShaderNodeKind,
    val x: Float,
    val y: Float,
    val paramA: Float = 0.5f,
    val paramB: Float = 0.5f,
    val colorArgb: Long = 0xFFFFFFFFL
)

data class ShaderNodeLink(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val toNodeId: String
)

data class ShaderNodeGraph(
    val enabled: Boolean = false,
    val presetName: String = "Cartoon Studio",
    val nodes: List<ShaderNode> = defaultNodes(),
    val links: List<ShaderNodeLink> = defaultLinks()
) {
    companion object {
        fun defaultNodes(): List<ShaderNode> = listOf(
            ShaderNode("uv", ShaderNodeKind.UV_COORD, 40f, 120f, paramA = 1f, paramB = 1f),
            ShaderNode("tex", ShaderNodeKind.TEXTURA_UV, 210f, 60f, paramA = 1f, paramB = 0f),
            ShaderNode("light", ShaderNodeKind.LUZ_OBJETO, 210f, 210f, paramA = 0.7f, paramB = 0.3f),
            ShaderNode("ramp", ShaderNodeKind.CEL_RAMP, 390f, 110f, paramA = 3f, paramB = 0.25f),
            ShaderNode("brush", ShaderNodeKind.BRUSH_GRAIN, 390f, 250f, paramA = 0.35f, paramB = 0.5f),
            ShaderNode("outline", ShaderNodeKind.CONTORNO, 560f, 240f, paramA = 2.0f, paramB = 1f, colorArgb = 0xFF10131CL),
            ShaderNode("out", ShaderNodeKind.SAIDA_MATERIAL, 570f, 100f, paramA = 1f, paramB = 0f)
        )

        fun defaultLinks(): List<ShaderNodeLink> = listOf(
            ShaderNodeLink("l1", "uv", "tex"),
            ShaderNodeLink("l2", "tex", "ramp"),
            ShaderNodeLink("l3", "light", "ramp"),
            ShaderNodeLink("l4", "ramp", "brush"),
            ShaderNodeLink("l5", "brush", "out"),
            ShaderNodeLink("l6", "outline", "out")
        )
    }
}

data class SceneObject3D(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Objeto 3D",
    val meshAssetPath: String = "",
    val formatTag: String = "obj",
    val isVisible: Boolean = true,
    val colorTint: Long = 0xFFFFFFFFL,
    val keyframes: List<ObjectTransformKeyframe> = listOf(ObjectTransformKeyframe(frameIndex = 0)),
    val shaderMode: ObjectShaderMode = ObjectShaderMode.PADRAO,
    val outline: ObjectOutlineSettings = ObjectOutlineSettings(),
    val light: ObjectLightSettings = ObjectLightSettings(),
    val uvSlots: List<ObjectUVMapSlot> = listOf(ObjectUVMapSlot()),
    val activeUvSlotIndex: Int = 0,
    val paintLayers: List<ObjectTexturePaintLayer> = emptyList(),
    val activePaintLayerIndex: Int = 0,
    val nodeGraph: ShaderNodeGraph = ShaderNodeGraph(),
    val bakedTextureBase64: String? = null
)

data class CameraEffectSpan(
    val id: String = UUID.randomUUID().toString(),
    val effect: CameraBuiltInEffect = CameraBuiltInEffect.ZOOM_IN,
    val startFrame: Int = 0,
    val endFrame: Int = 6,
    val intensity: Float = 1.0f
)

data class CameraPresetTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Predefinição de Câmera",
    val keyframes: List<Camera3DKeyframe> = emptyList(),
    val postEffects: CameraPostEffectSettings = CameraPostEffectSettings(),
    val captureWidth: Int = 1280,
    val captureHeight: Int = 720,
    val createdAt: Long = System.currentTimeMillis()
)

data class Camera3DSettings(
    val isEnabled: Boolean = false,
    val showCaptureFrame: Boolean = true,
    val autoInterpolate: Boolean = true,
    val captureWidth: Int = 1280,
    val captureHeight: Int = 720,
    val layerDepthSpacing: Float = 38f,
    val keyframes: List<Camera3DKeyframe> = listOf(Camera3DKeyframe(frameIndex = 0)),
    val postEffects: CameraPostEffectSettings = CameraPostEffectSettings(),
    val cameraMode: CameraMode = CameraMode.MODE_3D,
    val effectSpans: List<CameraEffectSpan> = emptyList(),
    val layerTransformTracks: List<LayerTransformTrack> = emptyList(),
    val scene3DObjects: List<SceneObject3D> = emptyList(),
    val showFloorGrid: Boolean = true,
    val shadingMode: StageShadingMode = StageShadingMode.RENDERED,
    val transformSpace: TransformSpaceMode = TransformSpaceMode.GLOBAL,
    val isDetachedFromLayers: Boolean = false
)

