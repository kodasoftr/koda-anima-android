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
    val maxX: Float = Float.MIN_VALUE,
    val maxY: Float = Float.MIN_VALUE
) {
    /**
     * Fast AABB intersection check for viewport chunk culling.
     */
    fun intersects(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        if (points.isEmpty()) return false
        val halfB = brushSize / 2f + 4f
        val bMinX = (if (minX != Float.MAX_VALUE) minX else points.minOf { it.x }) - halfB
        val bMinY = (if (minY != Float.MAX_VALUE) minY else points.minOf { it.y }) - halfB
        val bMaxX = (if (maxX != Float.MIN_VALUE) maxX else points.maxOf { it.x }) + halfB
        val bMaxY = (if (maxY != Float.MIN_VALUE) maxY else points.maxOf { it.y }) + halfB
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
    val isBucketReference: Boolean = false
)

enum class BucketReferenceMode(val labelPt: String) {
    ALL_LAYERS("Todas as camadas"),
    SELECTED_LAYERS("Camadas selecionadas")
}

data class BucketFillConfig(
    val expandPixels: Int = 1,
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

data class CanvasBackgroundSettings(
    val style: CanvasBackgroundStyle = CanvasBackgroundStyle.SOLIDO,
    val primaryColor: Long = 0xFFFFFFFFL,
    val secondaryColor: Long = 0xFFE3ECFFL,
    val gradientAngleDeg: Float = 90f,
    val texturePattern: CanvasTexturePattern = CanvasTexturePattern.NENHUMA,
    val textureIntensity: Float = 0.28f,
    val skyMode: SkyBackdropMode = SkyBackdropMode.GRADE_ESPACIAL,
    val skyTopColor: Long = 0xFF151926L,
    val skyBottomColor: Long = 0xFF28314AL
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
    val bezierP2Y: Float = 1.0f
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
    val effectSpans: List<CameraEffectSpan> = emptyList()
)

