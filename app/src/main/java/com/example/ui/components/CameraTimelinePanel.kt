package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BuiltIn3DPrimitive
import com.example.engine.Camera3DEngine
import com.example.engine.TimelineTrackEngine
import com.example.model.AnimationProject
import com.example.model.Camera3DKeyframe
import com.example.model.Camera3DSettings
import com.example.model.CameraBuiltInEffect
import com.example.model.CameraEasingCurve
import com.example.model.CameraMode
import com.example.model.CameraPostEffectSettings
import com.example.model.CameraPresetTemplate
import com.example.model.CanvasBackgroundSettings
import com.example.model.CanvasBackgroundStyle
import com.example.model.CanvasTexturePattern
import com.example.model.Frame
import com.example.model.GradientColorStop
import com.example.model.KodanimaTheme
import com.example.model.Layer
import com.example.model.ObjectTransformKeyframe
import com.example.model.SkyBackdropMode
import com.example.model.StageSelection
import com.example.model.StageTargetType
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Painel inferior do Estúdio de Câmera 2D e 3D Animada (5 abas consolidadas):
 * 0 - Timeline (Dope Sheet multi-faixas para Câmera, Camadas 2D e Objetos 3D, com toque longo para arrastar chaves)
 * 1 - Transform (Controles do alvo selecionado: Câmera/Lente FOV ou Posição/Rotação/Escala 3D da Camada/Objeto)
 * 2 - Cenas 3D (Importação .glb/.gltf/.obj+.mtl/.zip + Primitivas 3D + Seleção e Foco)
 * 3 - Efeitos (Auto Efeitos & Pasta Animada, Curva Gráfica Bézier e Pós-FX)
 * 4 - Fundo & Céu (Papel, Textura, Ponto Claro / Escuro + Pontos extras com Roda de Cores HSV inline)
 */
@Composable
fun CameraTimelinePanel(
    cameraSettings: Camera3DSettings,
    currentFrameIndex: Int,
    totalFrames: Int,
    isPlaying: Boolean,
    isSideView3D: Boolean,
    savedTemplates: List<CameraPresetTemplate>,
    theme: KodanimaTheme,
    onSelectFrame: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleCameraEnabled: () -> Unit,
    onToggleSideView3D: () -> Unit,
    onToggleAutoInterpolate: () -> Unit,
    onToggleCaptureFrameOverlay: () -> Unit,
    onUpdateCurrentCamera: ((Camera3DKeyframe) -> Camera3DKeyframe) -> Unit,
    onAddOrUpdateKeyframe: (CameraEasingCurve?) -> Unit,
    onDeleteKeyframeAt: (Int) -> Unit,
    onResetKeyframes: () -> Unit,
    onSetCameraResolution: (Int, Int) -> Unit,
    onSetLayerDepthSpacing: (Float) -> Unit,
    onApplyBuiltInEffect: (CameraBuiltInEffect) -> Unit,
    onSaveTemplateToFolder: (String) -> Unit,
    onApplyTemplateFromFolder: (CameraPresetTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onUpdatePostEffects: (CameraPostEffectSettings) -> Unit,
    onResetPostEffects: () -> Unit,
    onCloseCameraTimeline: () -> Unit,
    onSetCameraMode: ((CameraMode) -> Unit)? = null,
    onUpdateBezierHandles: ((Int, Float, Float, Float, Float) -> Unit)? = null,
    onApplyEffectInRange: ((CameraBuiltInEffect, Int, Int, Float) -> Unit)? = null,
    onRemoveEffectSpan: ((String) -> Unit)? = null,
    canvasBackground: CanvasBackgroundSettings = CanvasBackgroundSettings(),
    onUpdateCanvasBackground: (CanvasBackgroundSettings) -> Unit = {},
    onConnectTwoKeyframes: () -> Unit = {},
    onPrevKeyframe: () -> Unit = {},
    onNextKeyframe: () -> Unit = {},
    onResetCurrentKeyframe: () -> Unit = {},
    onSetCameraFov: (Float) -> Unit = {},
    onNudgeCameraPan: (Float, Float) -> Unit = { _, _ -> },
    onZoomCameraBy: (Float) -> Unit = {},
    onRotateCameraBy: (Float) -> Unit = {},
    onTiltOrbitCameraBy: (Float, Float) -> Unit = { _, _ -> },
    onAddFrame: (() -> Unit)? = null,
    layers: List<Layer> = emptyList(),
    stageSelection: StageSelection = StageSelection(),
    onSelectStageTarget: (StageSelection) -> Unit = {},
    onFocusStageTarget: () -> Unit = {},
    onMoveKeyframe: (fromFrame: Int, toFrame: Int) -> Unit = { _, _ -> },
    onUpdateSelectedObjectTransform: ((ObjectTransformKeyframe) -> ObjectTransformKeyframe) -> Unit = {},
    onAddOrUpdateSelectedKeyframe: (CameraEasingCurve?) -> Unit = {},
    onDeleteSelectedKeyframeAt: (Int) -> Unit = {},
    onResetSelectedTarget: () -> Unit = {},
    onImport3DSceneClick: () -> Unit = {},
    onAddBuiltIn3DPrimitive: (BuiltIn3DPrimitive) -> Unit = {},
    onToggle3DObjectVisibility: (String) -> Unit = {},
    onDelete3DObject: (String) -> Unit = {},
    onSet3DObjectTint: (String, Long) -> Unit = { _, _ -> },
    projectFrames: List<Frame> = emptyList(),
    fps: Int = 24,
    activeLayerIndex: Int = 0,
    currentTimelineTick: Int = currentFrameIndex,
    activeScrubTimeMs: Long? = null,
    playbackTimeMs: Long? = null,
    onSelectTick: (Int) -> Unit = onSelectFrame,
    onScrubToMs: (Long) -> Unit = {},
    onEndScrub: () -> Unit = {},
    onSetLayerCelHold: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onMoveLayerCel: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onAddCelAtTick: (Int, Int) -> Unit = { _, _ -> },
    onSetKeyframeHold: (Int, Int) -> Unit = { _, _ -> },
    onApplyTweenBetween: (Int, Int, CameraEasingCurve) -> Unit = { _, _, _ -> },
    onAddKeyframeAtTick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val secondaryColor = Color(theme.secondaryAccent)
    val textColor = Color(theme.text)
    val density = LocalDensity.current

    // 5 Consolidated Studio Tabs:
    // 0 = Timeline, 1 = Transform, 2 = Cenas 3D, 3 = Efeitos (Auto/Curvas/Pós-FX), 4 = Fundo & Céu
    var activeTab by remember { mutableIntStateOf(0) }
    // Sub-section inside Tab 3 (Efeitos): 0 = Auto & Pasta, 1 = Curva Gráfica, 2 = Pós-FX
    var activeEffectsSubTab by remember { mutableIntStateOf(0) }

    var customResW by remember(cameraSettings.captureWidth) { mutableStateOf(cameraSettings.captureWidth.toString()) }
    var customResH by remember(cameraSettings.captureHeight) { mutableStateOf(cameraSettings.captureHeight.toString()) }
    var templateNameInput by remember { mutableStateOf("") }

    // 2-Point Span Selection & Tweening state (Animate-inspired)
    var isPairSelectMode by remember { mutableStateOf(false) }
    var selectedPairA by remember { mutableStateOf<Int?>(null) }
    var selectedPairB by remember { mutableStateOf<Int?>(null) }
    var showInlineBezierCurve by remember { mutableStateOf(false) }
    var effectApplyToPair by remember { mutableStateOf(false) }
    var pinchEffectDuration by remember { mutableIntStateOf(8) }
    var effectIntensity by remember { mutableFloatStateOf(1.0f) }

    // Long-press keyframe drag state on Keyframe Track
    var dragSourceFrame by remember { mutableStateOf<Int?>(null) }
    var dragTargetFrame by remember { mutableStateOf<Int?>(null) }

    // Fundo & Céu Color Wheel Target:
    // 0 = Ponto Claro (skyLightColor/skyTopColor), 1 = Ponto Escuro (skyDarkColor/skyBottomColor),
    // 2 = Parada intermediária selecionada, 3 = Papel Cor 1, 4 = Papel Cor 2
    var skyWheelTarget by remember { mutableIntStateOf(0) }
    var selectedSkyStopId by remember { mutableStateOf<String?>(null) }

    // Independent camera runway expansion, zoom & stretch/move states
    var extraRunwayTicks by remember { mutableIntStateOf(0) }
    var timelineTickWidthDp by remember { mutableFloatStateOf(32f) }
    var draggingCamCel by remember { mutableStateOf<Int?>(null) }
    var liveCamCelHold by remember { mutableStateOf<Int?>(null) }
    var movingCamCel by remember { mutableStateOf<Int?>(null) }
    var liveMoveCamCelTick by remember { mutableStateOf<Int?>(null) }
    var draggingKfTick by remember { mutableStateOf<Int?>(null) }
    var liveDraggingKfHold by remember { mutableStateOf<Int?>(null) }

    val safeFps = max(1, fps)
    val effectiveTickFloat = remember(activeScrubTimeMs, playbackTimeMs, currentTimelineTick, safeFps) {
        (activeScrubTimeMs ?: playbackTimeMs)?.let { TimelineTrackEngine.timeMsToTickFloat(it, safeFps) }
            ?: currentTimelineTick.toFloat()
    }
    val projForTrack = remember(projectFrames, totalFrames, safeFps, cameraSettings) {
        AnimationProject(
            fps = safeFps,
            frames = projectFrames.ifEmpty { List(totalFrames.coerceAtLeast(1)) { Frame() } },
            camera3D = cameraSettings
        )
    }
    val totalRulerTicks = remember(projForTrack, totalFrames, safeFps, extraRunwayTicks) {
        maxOf(
            TimelineTrackEngine.computeTotalTicks(projForTrack, includeCameraRunway = true) + 8,
            totalFrames + 8,
            safeFps * 2,
            24
        ) + extraRunwayTicks
    }

    val is3DMode = cameraSettings.cameraMode == CameraMode.MODE_3D
    val evaluatedKf = remember(cameraSettings, effectiveTickFloat) {
        Camera3DEngine.evaluateCameraAtTick(cameraSettings, effectiveTickFloat)
    }

    val selectedLayerObj = remember(layers, stageSelection) {
        if (stageSelection.type == StageTargetType.LAYER) layers.firstOrNull { it.id == stageSelection.id } else null
    }
    val selected3DObj = remember(cameraSettings.scene3DObjects, stageSelection) {
        if (stageSelection.type == StageTargetType.OBJECT_3D) {
            cameraSettings.scene3DObjects.firstOrNull { it.id == stageSelection.id }
        } else null
    }
    val activeObjectKeyframes: List<ObjectTransformKeyframe> = remember(cameraSettings, stageSelection) {
        when (stageSelection.type) {
            StageTargetType.OBJECT_3D ->
                cameraSettings.scene3DObjects.firstOrNull { it.id == stageSelection.id }?.keyframes ?: emptyList()
            StageTargetType.LAYER ->
                cameraSettings.layerTransformTracks.firstOrNull { it.layerId == stageSelection.id }?.keyframes ?: emptyList()
            StageTargetType.CAMERA -> emptyList()
        }
    }
    val evaluatedObjKf = remember(activeObjectKeyframes, effectiveTickFloat, cameraSettings.autoInterpolate) {
        Camera3DEngine.evaluateObjectTransformAtTick(
            keyframes = activeObjectKeyframes,
            tickFloat = effectiveTickFloat,
            autoInterpolate = cameraSettings.autoInterpolate
        )
    }

    val isCameraTarget = stageSelection.type == StageTargetType.CAMERA ||
        (stageSelection.type == StageTargetType.OBJECT_3D && selected3DObj == null) ||
        (stageSelection.type == StageTargetType.LAYER && selectedLayerObj == null && cameraSettings.layerTransformTracks.none { it.layerId == stageSelection.id })

    val hasKeyAtCurrent = if (isCameraTarget) {
        cameraSettings.keyframes.any { it.frameIndex == currentTimelineTick }
    } else {
        activeObjectKeyframes.any { it.frameIndex == currentTimelineTick }
    }
    val currentKfHold = if (isCameraTarget) {
        cameraSettings.keyframes.firstOrNull { it.frameIndex == currentTimelineTick }?.holdTicks?.coerceAtLeast(1) ?: 1
    } else {
        activeObjectKeyframes.firstOrNull { it.frameIndex == currentTimelineTick }?.holdTicks?.coerceAtLeast(1) ?: 1
    }

    val activeOrNearestKf = remember(cameraSettings.keyframes, currentTimelineTick, evaluatedKf) {
        cameraSettings.keyframes.firstOrNull { it.frameIndex == currentTimelineTick }
            ?: cameraSettings.keyframes.minByOrNull { abs(it.frameIndex - currentTimelineTick) }
            ?: evaluatedKf
    }
    val sortedKeyFrames = remember(isCameraTarget, cameraSettings.keyframes, activeObjectKeyframes) {
        if (isCameraTarget) {
            cameraSettings.keyframes.map { it.frameIndex }.sorted()
        } else {
            activeObjectKeyframes.map { it.frameIndex }.sorted()
        }
    }
    val activeKeyframeHolds = remember(isCameraTarget, cameraSettings.keyframes, activeObjectKeyframes) {
        if (isCameraTarget) {
            cameraSettings.keyframes.sortedBy { it.frameIndex }.map { it.frameIndex to it.holdTicks.coerceAtLeast(1) }
        } else {
            activeObjectKeyframes.sortedBy { it.frameIndex }.map { it.frameIndex to it.holdTicks.coerceAtLeast(1) }
        }
    }
    val pairMin = remember(selectedPairA, selectedPairB) {
        val a = selectedPairA
        val b = selectedPairB
        if (a != null && b != null) minOf(a, b) else null
    }
    val pairMax = remember(selectedPairA, selectedPairB) {
        val a = selectedPairA
        val b = selectedPairB
        if (a != null && b != null) maxOf(a, b) else null
    }

    val targetDisplayName = when {
        isCameraTarget -> "Câmera"
        stageSelection.type == StageTargetType.LAYER -> "Camada: ${selectedLayerObj?.name ?: "Camada"}"
        else -> "3D: ${selected3DObj?.name ?: "Objeto"}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .border(1.2.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .testTag("camera_timeline_panel"),
        color = panelColor.copy(alpha = 0.96f),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        shadowElevation = 14.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWidePc = maxWidth >= 660.dp

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Row 1: Pinned Header Ribbon (Controls scroll on left, Close button ALWAYS pinned on right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Icon(Icons.Default.Videocam, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))

                        CamPill(
                            label = if (cameraSettings.isEnabled) "Câm: ON" else "Câm: OFF",
                            active = cameraSettings.isEnabled,
                            activeColor = NeonGreen,
                            textColor = textColor,
                            testTag = "camera_enable_toggle"
                        ) { onToggleCameraEnabled() }

                        if (onSetCameraMode != null) {
                            CamPill(
                                label = "2D Plano",
                                active = !is3DMode,
                                activeColor = CyanAccent,
                                textColor = textColor,
                                testTag = "camera_mode_2d_btn"
                            ) { onSetCameraMode(CameraMode.MODE_2D) }

                            CamPill(
                                label = "3D Espacial",
                                active = is3DMode,
                                activeColor = OrangeAccent,
                                textColor = textColor,
                                testTag = "camera_mode_3d_btn"
                            ) { onSetCameraMode(CameraMode.MODE_3D) }
                        }

                        if (is3DMode) {
                            CamPill(
                                label = "Palco 3D",
                                active = isSideView3D,
                                activeColor = secondaryColor,
                                textColor = textColor,
                                icon = Icons.Default.Layers,
                                testTag = "camera_side_view_3d_btn"
                            ) { onToggleSideView3D() }
                        }

                        CamPill(
                            label = if (cameraSettings.autoInterpolate) "Auto: ON" else "Degrau",
                            active = cameraSettings.autoInterpolate,
                            activeColor = OrangeAccent,
                            textColor = textColor
                        ) { onToggleAutoInterpolate() }

                        CamPill(
                            label = "Moldura",
                            active = cameraSettings.showCaptureFrame,
                            activeColor = accentColor,
                            textColor = textColor,
                            icon = Icons.Default.CropFree
                        ) { onToggleCaptureFrameOverlay() }
                    }

                    // Pinned Close Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCloseCameraTimeline() }
                            .border(0.8.dp, textColor.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .testTag("close_camera_timeline_btn"),
                        color = textColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Voltar à timeline normal", tint = textColor, modifier = Modifier.size(13.dp))
                            Text("Sair Câm.", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }

                // Row 2: 5 Streamlined Studio Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CamTabPill("Timeline", activeTab == 0, accentColor, textColor, "cam_tab_dope") { activeTab = 0 }
                    CamTabPill("Transform", activeTab == 1, OrangeAccent, textColor, "cam_tab_transform") { activeTab = 1 }
                    CamTabPill("Cenas 3D (${cameraSettings.scene3DObjects.size})", activeTab == 2, CyanAccent, textColor, "cam_tab_scenes") { activeTab = 2 }
                    CamTabPill("Efeitos", activeTab == 3, secondaryColor, textColor, "cam_tab_effects") { activeTab = 3 }
                    CamTabPill("Fundo & Céu", activeTab == 4, NeonGreen, textColor, "cam_tab_bg") { activeTab = 4 }
                }

                @Composable
                fun TargetTransformControlsContent(compactForSplit: Boolean) {
                    // Minimalist 4-in-1 grouped Estúdio 3D transform bar (0=Posição, 1=Rotação, 2=Zoom/Escala, 3=Ajustes/Res.)
                    var activeTransformGroup by remember { mutableIntStateOf(1) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Top Compact 4-Icon Group Bar + Quick Focar/Chave
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CamPill(
                                label = "Posição",
                                active = activeTransformGroup == 0,
                                activeColor = CyanAccent,
                                textColor = textColor,
                                icon = Icons.Default.OpenWith,
                                testTag = "tf_group_pos"
                            ) { activeTransformGroup = 0 }

                            CamPill(
                                label = "Rotação",
                                active = activeTransformGroup == 1,
                                activeColor = OrangeAccent,
                                textColor = textColor,
                                icon = Icons.Default.RotateRight,
                                testTag = "tf_group_rot"
                            ) { activeTransformGroup = 1 }

                            CamPill(
                                label = if (isCameraTarget) "Lente & Zoom" else "Escala & Opac.",
                                active = activeTransformGroup == 2,
                                activeColor = accentColor,
                                textColor = textColor,
                                icon = Icons.Default.ZoomIn,
                                testTag = "tf_group_zoom"
                            ) { activeTransformGroup = 2 }

                            CamPill(
                                label = if (isCameraTarget) "Res. & Reset" else "Chave & Reset",
                                active = activeTransformGroup == 3,
                                activeColor = NeonGreen,
                                textColor = textColor,
                                icon = Icons.Default.AspectRatio,
                                testTag = "tf_group_meta"
                            ) { activeTransformGroup = 3 }

                            if (is3DMode || !isCameraTarget) {
                                CamPill(
                                    label = "Focar",
                                    active = false,
                                    activeColor = CyanAccent,
                                    textColor = textColor,
                                    icon = Icons.Default.CenterFocusStrong,
                                    testTag = "transform_focus_btn"
                                ) { onFocusStageTarget() }
                            }
                        }

                        Surface(
                            color = bgColor.copy(alpha = 0.56f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, textColor.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isCameraTarget) {
                                    when (activeTransformGroup) {
                                        0 -> {
                                            // Group 1 (4-in-1): Posição Pan X / Pan Y + 4 direções
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CamPill("←", false, CyanAccent, textColor) { onNudgeCameraPan(-24f, 0f) }
                                                CamPill("↑", false, CyanAccent, textColor) { onNudgeCameraPan(0f, -24f) }
                                                CamPill("↓", false, CyanAccent, textColor) { onNudgeCameraPan(0f, 24f) }
                                                CamPill("→", false, CyanAccent, textColor) { onNudgeCameraPan(24f, 0f) }
                                                Spacer(Modifier.weight(1f))
                                                Text(
                                                    "X:${evaluatedKf.x.roundToInt()}  Y:${evaluatedKf.y.roundToInt()}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CyanAccent
                                                )
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                CamSlider("Pan X", "${evaluatedKf.x.roundToInt()}", evaluatedKf.x, -1200f..1200f, secondaryColor, textColor, Modifier.weight(1f)) { px ->
                                                    onUpdateCurrentCamera { it.copy(x = px) }
                                                }
                                                CamSlider("Pan Y", "${evaluatedKf.y.roundToInt()}", evaluatedKf.y, -1200f..1200f, secondaryColor, textColor, Modifier.weight(1f)) { py ->
                                                    onUpdateCurrentCamera { it.copy(y = py) }
                                                }
                                            }
                                        }
                                        1 -> {
                                            // Group 2 (4-in-1): Smooth Interactive Rotation Dial + Giro Z / Incl. X / Órbita Y
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TransformRotationDial(
                                                    angleDeg = evaluatedKf.rotationZ,
                                                    accentColor = OrangeAccent,
                                                    textColor = textColor,
                                                    testTag = "camera_rot_dial"
                                                ) { newRot ->
                                                    onUpdateCurrentCamera { it.copy(rotationZ = newRot) }
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                        CamPill("↺ 15°", false, secondaryColor, textColor) { onRotateCameraBy(-15f) }
                                                        CamPill("↻ 15°", false, secondaryColor, textColor) { onRotateCameraBy(15f) }
                                                        CamPill("0°", false, CyanAccent, textColor) {
                                                            onUpdateCurrentCamera { it.copy(rotationZ = 0f, pitchX = 0f, yawY = 0f) }
                                                        }
                                                    }
                                                    CamSlider("Giro Z", "${evaluatedKf.rotationZ.roundToInt()}°", evaluatedKf.rotationZ, -180f..180f, accentColor, textColor) { rz ->
                                                        onUpdateCurrentCamera { it.copy(rotationZ = rz) }
                                                    }
                                                    if (is3DMode) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            CamSlider("Incl. X", "${evaluatedKf.pitchX.roundToInt()}°", evaluatedKf.pitchX, -45f..45f, OrangeAccent, textColor, Modifier.weight(1f)) { px ->
                                                                onUpdateCurrentCamera { it.copy(pitchX = px) }
                                                            }
                                                            CamSlider("Órbita Y", "${evaluatedKf.yawY.roundToInt()}°", evaluatedKf.yawY, -60f..60f, OrangeAccent, textColor, Modifier.weight(1f)) { yy ->
                                                                onUpdateCurrentCamera { it.copy(yawY = yy) }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        2 -> {
                                            // Group 3 (4-in-1): Zoom + 4 Lentes (24/35/50/85mm) + FOV + Profundidade Z
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                CamPill("Zoom -", false, accentColor, textColor) { onZoomCameraBy(0.9f) }
                                                CamPill("Zoom +", false, accentColor, textColor) { onZoomCameraBy(1.1f) }
                                                CamSlider("Zoom", "${((evaluatedKf.zoom * 100).roundToInt())}%", evaluatedKf.zoom, 0.2f..4.5f, accentColor, textColor, Modifier.weight(1f)) { z ->
                                                    onUpdateCurrentCamera { it.copy(zoom = z) }
                                                }
                                            }
                                            CamSlider("Lente FOV", "${evaluatedKf.fovDegrees.roundToInt()}°", evaluatedKf.fovDegrees, 18f..120f, CyanAccent, textColor, Modifier.fillMaxWidth()) { fov ->
                                                onSetCameraFov(fov)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("24mm" to 84f, "35mm" to 63f, "50mm" to 46f, "85mm" to 28f).forEach { (lbl, fv) ->
                                                    CamPill(lbl, kotlin.math.abs(evaluatedKf.fovDegrees - fv) < 2f, CyanAccent, textColor) { onSetCameraFov(fv) }
                                                }
                                                if (is3DMode) {
                                                    CamPill("Prof. Z ${cameraSettings.layerDepthSpacing.roundToInt()}", false, NeonGreen, textColor) {
                                                        val nextD = if (cameraSettings.layerDepthSpacing >= 90f) 24f else cameraSettings.layerDepthSpacing + 18f
                                                        onSetLayerDepthSpacing(nextD)
                                                    }
                                                }
                                            }
                                            if (is3DMode) {
                                                CamSlider("Prof. Z", "${cameraSettings.layerDepthSpacing.roundToInt()}", cameraSettings.layerDepthSpacing, 0f..140f, NeonGreen, textColor, Modifier.fillMaxWidth()) { d ->
                                                    onSetLayerDepthSpacing(d)
                                                }
                                            }
                                        }
                                        else -> {
                                            // Group 4 (4-in-1): Reset Quadro / Reset Tudo / Resolução presets & Custom
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CamPill("Zerar T${currentTimelineTick + 1}", false, CyanAccent, textColor, Icons.Default.RestartAlt) {
                                                    onResetCurrentKeyframe()
                                                }
                                                if (!compactForSplit) {
                                                    CamPill("Zerar Tudo", false, NeonPink, NeonPink, Icons.Default.RestartAlt) {
                                                        onResetKeyframes()
                                                    }
                                                }
                                                if (is3DMode) {
                                                    CamPill("Yaw-", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(-5f, 0f) }
                                                    CamPill("Yaw+", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(5f, 0f) }
                                                    CamPill("Pitch-", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(0f, -5f) }
                                                    CamPill("Pitch+", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(0f, 5f) }
                                                }
                                            }
                                            if (!compactForSplit) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text("Res:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                                                    listOf(1280 to 720, 1920 to 1080, 1080 to 1080, 1080 to 1920).forEach { (w, h) ->
                                                        val isSel = cameraSettings.captureWidth == w && cameraSettings.captureHeight == h
                                                        CamPill("${w}×${h}", isSel, accentColor, textColor) { onSetCameraResolution(w, h) }
                                                    }
                                                    OutlinedTextField(
                                                        value = customResW,
                                                        onValueChange = { customResW = it.filter { ch -> ch.isDigit() }.take(4) },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.width(60.dp).height(40.dp)
                                                    )
                                                    Text("×", color = textColor, fontSize = 11.sp)
                                                    OutlinedTextField(
                                                        value = customResH,
                                                        onValueChange = { customResH = it.filter { ch -> ch.isDigit() }.take(4) },
                                                        singleLine = true,
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        modifier = Modifier.width(60.dp).height(40.dp)
                                                    )
                                                    CamPill("Aplicar", false, NeonGreen, textColor) {
                                                        val w = customResW.toIntOrNull() ?: cameraSettings.captureWidth
                                                        val h = customResH.toIntOrNull() ?: cameraSettings.captureHeight
                                                        onSetCameraResolution(w, h)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Layer / 3D Object Minimalist 4-in-1 Transform Inspector
                                    when (activeTransformGroup) {
                                        0 -> {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                CamSlider("Pos X", "${evaluatedObjKf.x.roundToInt()}", evaluatedObjKf.x, -1200f..1200f, CyanAccent, textColor, Modifier.weight(1f)) { nx ->
                                                    onUpdateSelectedObjectTransform { it.copy(x = nx) }
                                                }
                                                CamSlider("Pos Y", "${evaluatedObjKf.y.roundToInt()}", evaluatedObjKf.y, -1200f..1200f, CyanAccent, textColor, Modifier.weight(1f)) { ny ->
                                                    onUpdateSelectedObjectTransform { it.copy(y = ny) }
                                                }
                                            }
                                            CamSlider("Pos Z", "${evaluatedObjKf.z.roundToInt()}", evaluatedObjKf.z, -800f..800f, NeonGreen, textColor, Modifier.fillMaxWidth()) { nz ->
                                                onUpdateSelectedObjectTransform { it.copy(z = nz) }
                                            }
                                        }
                                        1 -> {
                                            // Intuitive Rotation Dial + Eixos X/Y/Z
                                            var rotDialAxis by remember { mutableIntStateOf(2) } // 0=X, 1=Y, 2=Z
                                            val currentAxisAngle = when (rotDialAxis) {
                                                0 -> evaluatedObjKf.rotX
                                                1 -> evaluatedObjKf.rotY
                                                else -> evaluatedObjKf.rotZ
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TransformRotationDial(
                                                    angleDeg = currentAxisAngle,
                                                    accentColor = when (rotDialAxis) {
                                                        0 -> NeonPink
                                                        1 -> NeonGreen
                                                        else -> CyanAccent
                                                    },
                                                    textColor = textColor,
                                                    testTag = "object_rot_dial"
                                                ) { deg ->
                                                    onUpdateSelectedObjectTransform {
                                                        when (rotDialAxis) {
                                                            0 -> it.copy(rotX = deg)
                                                            1 -> it.copy(rotY = deg)
                                                            else -> it.copy(rotZ = deg)
                                                        }
                                                    }
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Eixo:", fontSize = 8.5.sp, color = textColor.copy(alpha = 0.72f))
                                                        CamPill("X", rotDialAxis == 0, NeonPink, textColor) { rotDialAxis = 0 }
                                                        CamPill("Y", rotDialAxis == 1, NeonGreen, textColor) { rotDialAxis = 1 }
                                                        CamPill("Z", rotDialAxis == 2, CyanAccent, textColor) { rotDialAxis = 2 }
                                                        CamPill("0°", false, OrangeAccent, textColor) {
                                                            onUpdateSelectedObjectTransform { it.copy(rotX = 0f, rotY = 0f, rotZ = 0f) }
                                                        }
                                                    }
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        CamSlider("Rot X", "${evaluatedObjKf.rotX.roundToInt()}°", evaluatedObjKf.rotX, -180f..180f, NeonPink, textColor, Modifier.weight(1f)) { rx ->
                                                            onUpdateSelectedObjectTransform { it.copy(rotX = rx) }
                                                        }
                                                        CamSlider("Rot Y", "${evaluatedObjKf.rotY.roundToInt()}°", evaluatedObjKf.rotY, -180f..180f, NeonGreen, textColor, Modifier.weight(1f)) { ry ->
                                                            onUpdateSelectedObjectTransform { it.copy(rotY = ry) }
                                                        }
                                                    }
                                                    CamSlider("Rot Z", "${evaluatedObjKf.rotZ.roundToInt()}°", evaluatedObjKf.rotZ, -180f..180f, CyanAccent, textColor, Modifier.fillMaxWidth()) { rz ->
                                                        onUpdateSelectedObjectTransform { it.copy(rotZ = rz) }
                                                    }
                                                }
                                            }
                                        }
                                        2 -> {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                CamSlider("Escala", "${(evaluatedObjKf.scaleX * 100).roundToInt()}%", evaluatedObjKf.scaleX, 0.1f..5.0f, OrangeAccent, textColor, Modifier.weight(1f)) { sc ->
                                                    onUpdateSelectedObjectTransform { it.copy(scaleX = sc, scaleY = sc, scaleZ = sc) }
                                                }
                                                CamSlider("Opac.", "${(evaluatedObjKf.opacity * 100).roundToInt()}%", evaluatedObjKf.opacity, 0f..1f, NeonPink, textColor, Modifier.weight(1f)) { op ->
                                                    onUpdateSelectedObjectTransform { it.copy(opacity = op) }
                                                }
                                            }
                                        }
                                        else -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(targetDisplayName, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = OrangeAccent)
                                                CamPill(if (hasKeyAtCurrent) "Atualizar ◆" else "+ ◆ T${currentTimelineTick + 1}", hasKeyAtCurrent, OrangeAccent, textColor, Icons.Default.Add) {
                                                    onAddOrUpdateSelectedKeyframe(null)
                                                }
                                                CamPill("Zerar T${currentTimelineTick + 1}", false, NeonPink, NeonPink, Icons.Default.RestartAlt) {
                                                    onResetSelectedTarget()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (activeTab) {
                    // =========================================================================
                    // TAB 0: TIMELINE ESTILO CLÁSSICO (2 Pontos + Interpolação Direta no Trecho)
                    // =========================================================================
                    0 -> {
                        val activeKeyframeMeta = remember(isCameraTarget, cameraSettings.keyframes, activeObjectKeyframes) {
                            if (isCameraTarget) {
                                cameraSettings.keyframes.sortedBy { it.frameIndex }
                                    .map { Triple(it.frameIndex, it.holdTicks.coerceAtLeast(1), it.easing) }
                            } else {
                                activeObjectKeyframes.sortedBy { it.frameIndex }
                                    .map { Triple(it.frameIndex, it.holdTicks.coerceAtLeast(1), it.easing) }
                            }
                        }

                        // Resolve active 2-point span: explicit user pair OR current bracketed keyframe pair around Pinça
                        val bracketPrevTick = activeKeyframeMeta.lastOrNull { it.first <= currentTimelineTick }?.first
                        val bracketNextTick = activeKeyframeMeta.firstOrNull { it.first > currentTimelineTick }?.first
                        val activeSpanStart: Int? = pairMin ?: if (bracketPrevTick != null && bracketNextTick != null) bracketPrevTick else null
                        val activeSpanEnd: Int? = pairMax ?: if (bracketPrevTick != null && bracketNextTick != null) bracketNextTick else null
                        val activeSpanEasing: CameraEasingCurve? = activeSpanStart?.let { sTick ->
                            activeKeyframeMeta.firstOrNull { it.first == sTick }?.third
                        }

                        // Target Track Selector Strip (Câmera / Camadas / Objetos 3D)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Alvo:", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.72f))
                            CamPill(
                                label = "Câmera (${cameraSettings.keyframes.size})",
                                active = isCameraTarget,
                                activeColor = accentColor,
                                textColor = textColor,
                                testTag = "target_track_camera"
                            ) {
                                onSelectStageTarget(StageSelection(StageTargetType.CAMERA, "camera"))
                            }
                            layers.forEach { layer ->
                                val isSel = stageSelection.type == StageTargetType.LAYER && stageSelection.id == layer.id
                                val kfCount = cameraSettings.layerTransformTracks.firstOrNull { it.layerId == layer.id }?.keyframes?.size ?: 0
                                CamPill(
                                    label = if (kfCount > 0) "${layer.name} (◆$kfCount)" else layer.name,
                                    active = isSel,
                                    activeColor = CyanAccent,
                                    textColor = textColor,
                                    testTag = "target_track_layer_${layer.id}"
                                ) {
                                    onSelectStageTarget(StageSelection(StageTargetType.LAYER, layer.id))
                                }
                            }
                            cameraSettings.scene3DObjects.forEach { obj ->
                                val isSel = stageSelection.type == StageTargetType.OBJECT_3D && stageSelection.id == obj.id
                                CamPill(
                                    label = "3D: ${obj.name} (◆${obj.keyframes.size})",
                                    active = isSel,
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    testTag = "target_track_obj_${obj.id}"
                                ) {
                                    onSelectStageTarget(StageSelection(StageTargetType.OBJECT_3D, obj.id))
                                }
                            }
                        }

                        // Transport & Keyframe Status Row
                        val curSecText = String.format(
                            Locale.US,
                            "%.2fs",
                            TimelineTrackEngine.tickToTimeMs(currentTimelineTick, safeFps) / 1000.0
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    dragSourceFrame != null && dragTargetFrame != null ->
                                        "Movendo ◆ T${dragSourceFrame!! + 1} → T${dragTargetFrame!! + 1}"
                                    movingCamCel != null && liveMoveCamCelTick != null ->
                                        "Movendo Cel Q${movingCamCel!! + 1} → T${liveMoveCamCelTick!! + 1}"
                                    draggingCamCel != null && liveCamCelHold != null ->
                                        "Esticando Cel Q${draggingCamCel!! + 1}: ${liveCamCelHold}q"
                                    draggingKfTick != null && liveDraggingKfHold != null ->
                                        "Sustentar ◆ T${draggingKfTick!! + 1}: ${liveDraggingKfHold}q"
                                    isPairSelectMode && selectedPairA != null && selectedPairB == null ->
                                        "Ponto 1: T${selectedPairA!! + 1} • Toque no Ponto 2"
                                    pairMin != null && pairMax != null ->
                                        "2 Pontos: T${pairMin + 1} ━━► T${pairMax + 1}"
                                    else ->
                                        "T${currentTimelineTick + 1} ($curSecText) • ◆${sortedKeyFrames.size}"
                                },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    dragSourceFrame != null || movingCamCel != null -> NeonGreen
                                    draggingCamCel != null || draggingKfTick != null -> CyanAccent
                                    isPairSelectMode || (pairMin != null && pairMax != null) -> OrangeAccent
                                    else -> textColor.copy(alpha = 0.88f)
                                }
                            )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CamPill("◀ ◆", false, CyanAccent, textColor, testTag = "camera_prev_key_btn") { onPrevKeyframe() }

                                CamPill(
                                    label = if (hasKeyAtCurrent) "Atualizar ◆" else "+ ◆ T${currentTimelineTick + 1}",
                                    active = hasKeyAtCurrent,
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    icon = Icons.Default.Add
                                ) {
                                    if (isCameraTarget) onAddOrUpdateKeyframe(null)
                                    else onAddOrUpdateSelectedKeyframe(null)
                                }

                                CamPill("◆ ▶", false, CyanAccent, textColor, testTag = "camera_next_key_btn") { onNextKeyframe() }

                                CamPill(
                                    label = when {
                                        isPairSelectMode && selectedPairA == null -> "Escolher Ponto 1..."
                                        isPairSelectMode && selectedPairA != null && selectedPairB == null -> "Escolher Ponto 2..."
                                        pairMin != null && pairMax != null -> "2 Pontos: T${pairMin + 1}–T${pairMax + 1}"
                                        else -> "Escolher 2 Pontos"
                                    },
                                    active = isPairSelectMode || (pairMin != null && pairMax != null),
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    icon = Icons.Default.Link,
                                    testTag = "camera_pair_select_toggle"
                                ) {
                                    isPairSelectMode = !isPairSelectMode
                                    if (isPairSelectMode && selectedPairA != null && selectedPairB != null) {
                                        selectedPairA = null
                                        selectedPairB = null
                                    } else if (!isPairSelectMode && selectedPairB == null) {
                                        selectedPairA = null
                                    }
                                }

                                if (selectedPairA != null || selectedPairB != null) {
                                    CamPill("Limpar", false, NeonPink, NeonPink) {
                                        selectedPairA = null
                                        selectedPairB = null
                                        isPairSelectMode = false
                                    }
                                }

                                CamPill(
                                    label = "Conectar ◆━◆",
                                    active = cameraSettings.autoInterpolate && sortedKeyFrames.size >= 2,
                                    activeColor = NeonGreen,
                                    textColor = textColor,
                                    testTag = "camera_connect_keyframes_btn"
                                ) { onConnectTwoKeyframes() }

                                if (hasKeyAtCurrent) {
                                    val shownHold = if (draggingKfTick == currentTimelineTick && liveDraggingKfHold != null) {
                                        liveDraggingKfHold!!
                                    } else currentKfHold
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(OrangeAccent.copy(alpha = 0.14f))
                                            .border(0.8.dp, OrangeAccent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "-1q",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier
                                                .clickable { onSetKeyframeHold(currentTimelineTick, (shownHold - 1).coerceIn(1, 240)) }
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                        Text(
                                            text = "Pausa ${shownHold}q",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OrangeAccent
                                        )
                                        Text(
                                            text = "+1q",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            modifier = Modifier
                                                .clickable { onSetKeyframeHold(currentTimelineTick, (shownHold + 1).coerceIn(1, 240)) }
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }

                                    CamPill(
                                        label = "Remover ◆",
                                        active = false,
                                        activeColor = NeonPink,
                                        textColor = NeonPink,
                                        icon = Icons.Default.DeleteOutline
                                    ) {
                                        if (isCameraTarget) onDeleteKeyframeAt(currentTimelineTick)
                                        else onDeleteSelectedKeyframeAt(currentTimelineTick)
                                    }
                                }

                                if (onAddFrame != null) {
                                    CamPill(
                                        label = "+ Quadro",
                                        active = false,
                                        activeColor = accentColor,
                                        textColor = textColor,
                                        icon = Icons.Default.Add,
                                        testTag = "camera_add_frame_btn"
                                    ) { onAddFrame() }
                                }

                                CamPill(
                                    label = "+ Tempo",
                                    active = false,
                                    activeColor = CyanAccent,
                                    textColor = CyanAccent,
                                    icon = Icons.Default.Add,
                                    testTag = "camera_add_runway_btn"
                                ) { extraRunwayTicks += safeFps }

                                CamPill("-", false, textColor, textColor) {
                                    timelineTickWidthDp = (timelineTickWidthDp - 4f).coerceIn(22f, 48f)
                                }
                                CamPill("+", false, textColor, textColor) {
                                    timelineTickWidthDp = (timelineTickWidthDp + 4f).coerceIn(22f, 48f)
                                }
                            }
                        }

                        @Composable
                        fun DopeSheetBlock(modifier: Modifier = Modifier) {
                            val tickWidthDp = timelineTickWidthDp
                            val tickWidth = tickWidthDp.dp
                            val tickPx = with(density) { tickWidth.toPx() }.coerceAtLeast(1f)
                            val rulerHeightDp = 22.dp
                            val celRowHeightDp = 24.dp
                            val keyTrackHeightDp = 40.dp
                            val showCameraContextRow = !isCameraTarget && cameraSettings.keyframes.isNotEmpty()
                            val contextRowHeightDp = 22.dp
                            val gridTotalHeightDp = rulerHeightDp + celRowHeightDp + keyTrackHeightDp +
                                (if (showCameraContextRow) contextRowHeightDp else 0.dp)
                            val gridWidthDp = tickWidth * totalRulerTicks
                            val dopeScrollState = rememberScrollState()

                            val activeLayerName = layers.getOrNull(activeLayerIndex)?.name ?: "Camada"
                            val activeCels = remember(projForTrack, activeLayerIndex) {
                                TimelineTrackEngine.computeLayerCels(projForTrack, activeLayerIndex)
                            }
                            val keyframeSet = sortedKeyFrames.toSet()

                            fun pickPairPoint(tIdx: Int) {
                                when {
                                    selectedPairA == null || (selectedPairA != null && selectedPairB != null) -> {
                                        selectedPairA = tIdx
                                        selectedPairB = null
                                    }
                                    selectedPairA != tIdx -> {
                                        selectedPairB = tIdx
                                        isPairSelectMode = false
                                    }
                                }
                            }

                            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                // Main Unified Timeline Strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(bgColor.copy(alpha = 0.70f))
                                        .border(0.9.dp, textColor.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                                ) {
                                    // Left Pinned Track Headers
                                    Column(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .height(gridTotalHeightDp)
                                            .background(panelColor.copy(alpha = 0.94f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(rulerHeightDp)
                                                .background(bgColor.copy(alpha = 0.78f))
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = "Tempo (${safeFps}f)",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CyanAccent,
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(celRowHeightDp)
                                                .background(accentColor.copy(alpha = 0.08f))
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                text = activeLayerName,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor.copy(alpha = 0.85f),
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(keyTrackHeightDp)
                                                .background(OrangeAccent.copy(alpha = 0.12f))
                                                .border(0.4.dp, OrangeAccent.copy(alpha = 0.25f))
                                                .padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Column {
                                                Text(
                                                    text = targetDisplayName,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrangeAccent,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "Chaves & Interp",
                                                    fontSize = 7.sp,
                                                    color = textColor.copy(alpha = 0.62f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (showCameraContextRow) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(contextRowHeightDp)
                                                    .clickable {
                                                        onSelectStageTarget(StageSelection(StageTargetType.CAMERA, "camera"))
                                                    }
                                                    .padding(horizontal = 6.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = "Câmera (◆${cameraSettings.keyframes.size})",
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = accentColor,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Right Scrollable Time Ruler + Cels + Visual Tween Span Track + Cyan Pinça
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(gridTotalHeightDp)
                                            .horizontalScroll(dopeScrollState)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(gridWidthDp)
                                                .height(gridTotalHeightDp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                // 1. Time Ruler (Tap = move Pinça, Drag = scrub continuously)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(rulerHeightDp)
                                                        .background(bgColor.copy(alpha = 0.88f))
                                                        .testTag("camera_timeline_ruler")
                                                        .pointerInput(totalRulerTicks, safeFps, tickPx) {
                                                            val maxScrubMs = TimelineTrackEngine.tickToTimeMs(
                                                                (totalRulerTicks - 1).coerceAtLeast(1),
                                                                safeFps
                                                            )
                                                            detectHorizontalDragGestures(
                                                                onDragStart = { startOffset ->
                                                                    val tickPos = (startOffset.x / tickPx).coerceAtLeast(0f)
                                                                    val targetMs = ((tickPos / safeFps.toFloat()) * 1000.0)
                                                                        .toLong()
                                                                        .coerceIn(0L, maxScrubMs)
                                                                    onScrubToMs(targetMs)
                                                                },
                                                                onDragEnd = { onEndScrub() },
                                                                onDragCancel = { onEndScrub() }
                                                            ) { change, _ ->
                                                                change.consume()
                                                                val tickPos = (change.position.x / tickPx).coerceAtLeast(0f)
                                                                val targetMs = ((tickPos / safeFps.toFloat()) * 1000.0)
                                                                    .toLong()
                                                                    .coerceIn(0L, maxScrubMs)
                                                                onScrubToMs(targetMs)
                                                            }
                                                        }
                                                ) {
                                                    val quarterStep = max(1, safeFps / 4)
                                                    for (t in 0 until totalRulerTicks) {
                                                        val isSec = t % safeFps == 0
                                                        val isQuarter = t % quarterStep == 0
                                                        val isCurrentTick = t == currentTimelineTick
                                                        Column(
                                                            modifier = Modifier
                                                                .width(tickWidth)
                                                                .fillMaxHeight()
                                                                .clickable {
                                                                    onSelectTick(t)
                                                                    if (isPairSelectMode) pickPairPoint(t)
                                                                },
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = when {
                                                                    isSec -> "${t / safeFps}s"
                                                                    (t + 1) % 5 == 0 -> "${t + 1}"
                                                                    else -> ""
                                                                },
                                                                fontSize = if (isSec) 8.sp else 7.sp,
                                                                fontWeight = if (isSec || isCurrentTick) FontWeight.Bold else FontWeight.Normal,
                                                                color = when {
                                                                    isCurrentTick -> CyanAccent
                                                                    isSec -> textColor.copy(alpha = 0.92f)
                                                                    else -> textColor.copy(alpha = 0.45f)
                                                                },
                                                                maxLines = 1
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(if (isSec) 1.5.dp else 1.dp)
                                                                    .height(if (isSec) 6.dp else if (isQuarter) 4.dp else 2.dp)
                                                                    .background(
                                                                        when {
                                                                            isCurrentTick -> CyanAccent
                                                                            isSec -> accentColor.copy(alpha = 0.85f)
                                                                            else -> textColor.copy(alpha = 0.3f)
                                                                        }
                                                                    )
                                                            )
                                                        }
                                                    }
                                                }

                                                // 2. Active Drawing Layer Independent Cels Strip
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(celRowHeightDp)
                                                        .background(bgColor.copy(alpha = 0.45f))
                                                        .pointerInput(totalRulerTicks, activeLayerIndex, tickPx) {
                                                            detectTapGestures { offset ->
                                                                val tappedTick = (offset.x / tickPx).toInt().coerceIn(0, totalRulerTicks - 1)
                                                                onAddCelAtTick(activeLayerIndex, tappedTick)
                                                            }
                                                        }
                                                ) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        for (t in 0 until totalRulerTicks) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(tickWidth)
                                                                    .fillMaxHeight()
                                                                    .border(0.3.dp, textColor.copy(alpha = 0.06f))
                                                            )
                                                        }
                                                    }
                                                    activeCels.forEach { cel ->
                                                        val displayStart = if (movingCamCel == cel.frameIndex && liveMoveCamCelTick != null) {
                                                            liveMoveCamCelTick!!
                                                        } else cel.startTick
                                                        val displayHold = if (draggingCamCel == cel.frameIndex && liveCamCelHold != null) {
                                                            liveCamCelHold!!
                                                        } else cel.durationTicks
                                                        val isCurrentCel = currentTimelineTick in displayStart until (displayStart + displayHold)
                                                        val hasInk = cel.layer.strokes.isNotEmpty() || cel.layer.imageAttachment != null
                                                        Box(
                                                            modifier = Modifier
                                                                .offset(x = tickWidth * displayStart)
                                                                .width((tickWidth * displayHold - 2.dp).coerceAtLeast(tickWidth - 2.dp))
                                                                .fillMaxHeight()
                                                                .padding(vertical = 2.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(
                                                                    when {
                                                                        movingCamCel == cel.frameIndex -> NeonGreen.copy(alpha = 0.35f)
                                                                        isCurrentCel -> accentColor.copy(alpha = 0.36f)
                                                                        hasInk -> CyanAccent.copy(alpha = 0.20f)
                                                                        else -> textColor.copy(alpha = 0.10f)
                                                                    }
                                                                )
                                                                .border(
                                                                    width = if (isCurrentCel) 1.1.dp else 0.7.dp,
                                                                    color = when {
                                                                        movingCamCel == cel.frameIndex -> NeonGreen
                                                                        isCurrentCel -> CyanAccent
                                                                        else -> textColor.copy(alpha = 0.26f)
                                                                    },
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .pointerInput(cel.frameIndex, cel.startTick, totalRulerTicks, tickPx) {
                                                                    var accumX = 0f
                                                                    detectDragGesturesAfterLongPress(
                                                                        onDragStart = {
                                                                            accumX = 0f
                                                                            movingCamCel = cel.frameIndex
                                                                            liveMoveCamCelTick = cel.startTick
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            accumX += dragAmount.x
                                                                            val deltaTicks = (accumX / tickPx).roundToInt()
                                                                            liveMoveCamCelTick = (cel.startTick + deltaTicks).coerceIn(0, (totalRulerTicks - 1).coerceAtLeast(0))
                                                                        },
                                                                        onDragEnd = {
                                                                            val dst = liveMoveCamCelTick ?: cel.startTick
                                                                            movingCamCel = null
                                                                            liveMoveCamCelTick = null
                                                                            if (dst != cel.startTick) {
                                                                                onMoveLayerCel(activeLayerIndex, cel.frameIndex, dst)
                                                                            }
                                                                        },
                                                                        onDragCancel = {
                                                                            movingCamCel = null
                                                                            liveMoveCamCelTick = null
                                                                        }
                                                                    )
                                                                }
                                                                .clickable { onSelectTick(cel.startTick) }
                                                                .testTag("camera_layer_cel_${cel.frameIndex}"),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            Text(
                                                                text = if (displayHold > 1) "Q${cel.frameIndex + 1} (${displayHold}q)" else "Q${cel.frameIndex + 1}",
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCurrentCel) textColor else textColor.copy(alpha = 0.85f),
                                                                maxLines = 1,
                                                                modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                                                            )
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.CenterEnd)
                                                                    .width(10.dp)
                                                                    .fillMaxHeight()
                                                                    .background(if (draggingCamCel == cel.frameIndex) CyanAccent.copy(alpha = 0.7f) else textColor.copy(alpha = 0.14f))
                                                                    .pointerInput(cel.frameIndex, cel.durationTicks, tickPx) {
                                                                        var accumX = 0f
                                                                        detectHorizontalDragGestures(
                                                                            onDragStart = {
                                                                                accumX = 0f
                                                                                draggingCamCel = cel.frameIndex
                                                                                liveCamCelHold = cel.durationTicks
                                                                            },
                                                                            onDragEnd = {
                                                                                val finalHold = liveCamCelHold ?: cel.durationTicks
                                                                                draggingCamCel = null
                                                                                liveCamCelHold = null
                                                                                onSetLayerCelHold(activeLayerIndex, cel.frameIndex, finalHold)
                                                                            },
                                                                            onDragCancel = {
                                                                                draggingCamCel = null
                                                                                liveCamCelHold = null
                                                                            }
                                                                        ) { change, dragAmount ->
                                                                            change.consume()
                                                                            accumX += dragAmount
                                                                            val delta = (accumX / tickPx).roundToInt()
                                                                            liveCamCelHold = (cel.durationTicks + delta).coerceIn(1, 240)
                                                                        }
                                                                    }
                                                                    .testTag("camera_cel_stretch_${cel.frameIndex}"),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .width(2.dp)
                                                                        .height(10.dp)
                                                                        .clip(RoundedCornerShape(1.dp))
                                                                        .background(if (isCurrentCel) CyanAccent else textColor.copy(alpha = 0.55f))
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // 3. Hero Track: Interactive Keyframes + Classic Tween Span Bars (◆ ━━━━► ◆)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(keyTrackHeightDp)
                                                        .background(bgColor.copy(alpha = 0.55f))
                                                ) {
                                                    // Base tick slots (Tap = select tick/pair point, Double-tap = insert ◆ keyframe)
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        for (tIdx in 0 until totalRulerTicks) {
                                                            val isCurrent = tIdx == currentTimelineTick
                                                            val isPairEndpoint = tIdx == selectedPairA || tIdx == selectedPairB
                                                            val inPairSpan = pairMin != null && pairMax != null && tIdx in pairMin..pairMax
                                                            val isDragDropTarget = dragTargetFrame == tIdx && dragSourceFrame != null

                                                            Box(
                                                                modifier = Modifier
                                                                    .width(tickWidth)
                                                                    .fillMaxHeight()
                                                                    .background(
                                                                        when {
                                                                            isDragDropTarget -> NeonGreen.copy(alpha = 0.34f)
                                                                            isPairEndpoint -> Color(0xFFFFD600).copy(alpha = 0.24f)
                                                                            inPairSpan -> OrangeAccent.copy(alpha = 0.14f)
                                                                            isCurrent -> accentColor.copy(alpha = 0.20f)
                                                                            else -> Color.Transparent
                                                                        }
                                                                    )
                                                                    .border(
                                                                        width = when {
                                                                            isDragDropTarget -> 1.5.dp
                                                                            isPairEndpoint -> 1.3.dp
                                                                            isCurrent -> 1.0.dp
                                                                            else -> 0.4.dp
                                                                        },
                                                                        color = when {
                                                                            isDragDropTarget -> NeonGreen
                                                                            isPairEndpoint -> Color(0xFFFFD600)
                                                                            isCurrent -> accentColor
                                                                            else -> textColor.copy(alpha = 0.10f)
                                                                        }
                                                                    )
                                                                    .pointerInput(tIdx, isPairSelectMode, selectedPairA, selectedPairB) {
                                                                        detectTapGestures(
                                                                            onTap = {
                                                                                onSelectTick(tIdx)
                                                                                if (isPairSelectMode) pickPairPoint(tIdx)
                                                                            },
                                                                            onDoubleTap = {
                                                                                onAddKeyframeAtTick(tIdx)
                                                                            }
                                                                        )
                                                                    }
                                                                    .testTag("dope_frame_$tIdx"),
                                                                contentAlignment = Alignment.BottomCenter
                                                            ) {
                                                                if (tIdx !in keyframeSet) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .padding(bottom = 3.dp)
                                                                            .size(3.dp)
                                                                            .clip(CircleShape)
                                                                            .background(
                                                                                if (isPairEndpoint) Color(0xFFFFD600)
                                                                                else textColor.copy(alpha = 0.18f)
                                                                            )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // User-picked 2-Point preview span bar (when user picked two arbitrary points A & B!)
                                                    if (pairMin != null && pairMax != null && pairMax > pairMin) {
                                                        val startX = tickWidth * (pairMin + 0.5f)
                                                        val spanW = (tickWidth * (pairMax - pairMin)).coerceAtLeast(tickWidth)
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.CenterStart)
                                                                .offset(x = startX)
                                                                .width(spanW)
                                                                .height(24.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(Color(0xFFFFD600).copy(alpha = 0.16f))
                                                                .border(1.2.dp, Color(0xFFFFD600).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                                                        )
                                                    }

                                                    // Classic Tween Span Bars (◆ ━━━━━━► ◆) between every consecutive pair of keyframes!
                                                    for (i in 0 until activeKeyframeMeta.lastIndex) {
                                                        val (kStart, kHold, kEasing) = activeKeyframeMeta[i]
                                                        val kEnd = activeKeyframeMeta[i + 1].first
                                                        if (kEnd <= kStart || kStart >= totalRulerTicks) continue
                                                        val isThisSpanSelected = activeSpanStart == kStart && activeSpanEnd == kEnd
                                                        val isTweenActive = cameraSettings.autoInterpolate && kEasing != CameraEasingCurve.DEGRAU
                                                        val effectiveHold = if (draggingKfTick == kStart && liveDraggingKfHold != null) {
                                                            liveDraggingKfHold!!
                                                        } else kHold

                                                        val barColor = when {
                                                            !isTweenActive -> textColor.copy(alpha = 0.10f)
                                                            kEasing == CameraEasingCurve.LINEAR -> CyanAccent
                                                            kEasing == CameraEasingCurve.ELASTICO -> NeonPink
                                                            kEasing == CameraEasingCurve.ACELERAR -> OrangeAccent
                                                            kEasing == CameraEasingCurve.DESACELERAR -> NeonGreen
                                                            else -> accentColor
                                                        }
                                                        val spanStartX = tickWidth * (kStart + 0.5f)
                                                        val spanWidth = (tickWidth * (kEnd - kStart)).coerceAtLeast(12.dp)

                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.CenterStart)
                                                                .offset(x = spanStartX)
                                                                .width(spanWidth)
                                                                .height(22.dp)
                                                                .clip(RoundedCornerShape(5.dp))
                                                                .background(
                                                                    if (isTweenActive) barColor.copy(alpha = if (isThisSpanSelected) 0.34f else 0.22f)
                                                                    else textColor.copy(alpha = 0.08f)
                                                                )
                                                                .border(
                                                                    width = if (isThisSpanSelected) 1.5.dp else 0.8.dp,
                                                                    color = when {
                                                                        isThisSpanSelected -> Color(0xFFFFD600)
                                                                        isTweenActive -> barColor.copy(alpha = 0.72f)
                                                                        else -> textColor.copy(alpha = 0.28f)
                                                                    },
                                                                    shape = RoundedCornerShape(5.dp)
                                                                )
                                                                .clickable {
                                                                    // Tap ANY tween bar to select those exact 2 points!
                                                                    selectedPairA = kStart
                                                                    selectedPairB = kEnd
                                                                    isPairSelectMode = false
                                                                    val midTick = (kStart + kEnd) / 2
                                                                    onSelectTick(midTick)
                                                                }
                                                                .testTag("tween_span_${kStart}_${kEnd}"),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            // Arrow shaft + chevron inside span bar
                                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                                val cy = size.height * 0.5f
                                                                val pad = 10f
                                                                if (size.width > 24f) {
                                                                    if (isTweenActive) {
                                                                        drawLine(
                                                                            color = barColor.copy(alpha = 0.9f),
                                                                            start = Offset(pad, cy),
                                                                            end = Offset(size.width - pad, cy),
                                                                            strokeWidth = 2.4f
                                                                        )
                                                                        val ax = size.width - pad
                                                                        drawLine(barColor, Offset(ax - 7f, cy - 5f), Offset(ax, cy), strokeWidth = 2.4f)
                                                                        drawLine(barColor, Offset(ax - 7f, cy + 5f), Offset(ax, cy), strokeWidth = 2.4f)
                                                                    } else {
                                                                        // Dashed/step line for Degrau
                                                                        var dx = pad
                                                                        while (dx < size.width - pad - 4f) {
                                                                            drawLine(
                                                                                color = textColor.copy(alpha = 0.45f),
                                                                                start = Offset(dx, cy),
                                                                                end = Offset((dx + 6f).coerceAtMost(size.width - pad), cy),
                                                                                strokeWidth = 1.6f
                                                                            )
                                                                            dx += 12f
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            if (kEnd - kStart >= 2) {
                                                                Text(
                                                                    text = if (isTweenActive) kEasing.labelPt else "Sem Interp",
                                                                    fontSize = 7.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isTweenActive) textColor else textColor.copy(alpha = 0.65f),
                                                                    maxLines = 1,
                                                                    modifier = Modifier
                                                                        .background(bgColor.copy(alpha = 0.72f), RoundedCornerShape(3.dp))
                                                                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                                )
                                                            }
                                                            // Hold plateau indicator if keyframe holds > 1q
                                                            if (effectiveHold > 1) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.CenterStart)
                                                                        .width((tickWidth * (effectiveHold - 1)).coerceAtMost(spanWidth))
                                                                        .fillMaxHeight()
                                                                        .background(OrangeAccent.copy(alpha = 0.25f))
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Draggable Keyframe Nodes (◆) positioned cleanly over the track
                                                    activeKeyframeMeta.forEach { (kfTick, kfHold, _) ->
                                                        if (kfTick !in 0 until totalRulerTicks) return@forEach
                                                        val isNodeCurrent = kfTick == currentTimelineTick
                                                        val isNodePairEnd = kfTick == selectedPairA || kfTick == selectedPairB
                                                        val isDragSource = dragSourceFrame == kfTick
                                                        val nodeStartX = tickWidth * (kfTick + 0.5f) - 11.dp

                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.CenterStart)
                                                                .offset(x = nodeStartX)
                                                                .size(22.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    when {
                                                                        isDragSource -> NeonGreen
                                                                        isNodePairEnd -> Color(0xFFFFD600)
                                                                        isNodeCurrent -> CyanAccent
                                                                        else -> OrangeAccent
                                                                    }.copy(alpha = 0.25f)
                                                                )
                                                                .border(
                                                                    width = if (isNodePairEnd || isNodeCurrent) 1.5.dp else 1.dp,
                                                                    color = when {
                                                                        isDragSource -> NeonGreen
                                                                        isNodePairEnd -> Color(0xFFFFD600)
                                                                        isNodeCurrent -> CyanAccent
                                                                        else -> OrangeAccent
                                                                    },
                                                                    shape = CircleShape
                                                                )
                                                                .pointerInput(kfTick, totalRulerTicks, stageSelection, tickPx) {
                                                                    var accumX = 0f
                                                                    detectDragGesturesAfterLongPress(
                                                                        onDragStart = {
                                                                            accumX = 0f
                                                                            dragSourceFrame = kfTick
                                                                            dragTargetFrame = kfTick
                                                                        },
                                                                        onDrag = { change, dragAmount ->
                                                                            change.consume()
                                                                            accumX += dragAmount.x
                                                                            val deltaCols = (accumX / tickPx).roundToInt()
                                                                            dragTargetFrame = (kfTick + deltaCols).coerceIn(0, (totalRulerTicks - 1).coerceAtLeast(0))
                                                                        },
                                                                        onDragEnd = {
                                                                            val src = dragSourceFrame
                                                                            val dst = dragTargetFrame
                                                                            dragSourceFrame = null
                                                                            dragTargetFrame = null
                                                                            if (src != null && dst != null && src != dst) {
                                                                                onMoveKeyframe(src, dst)
                                                                            }
                                                                        },
                                                                        onDragCancel = {
                                                                            dragSourceFrame = null
                                                                            dragTargetFrame = null
                                                                        }
                                                                    )
                                                                }
                                                                .clickable {
                                                                    onSelectTick(kfTick)
                                                                    if (isPairSelectMode) pickPairPoint(kfTick)
                                                                }
                                                                .testTag("keyframe_node_$kfTick"),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = if (kfHold > 1) "◆" else "◆",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = when {
                                                                    isDragSource -> NeonGreen
                                                                    isNodePairEnd -> Color(0xFFFFD600)
                                                                    isNodeCurrent -> CyanAccent
                                                                    else -> OrangeAccent
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // 4. Optional Camera Context Row when editing Layer/3D Object
                                                if (showCameraContextRow) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(contextRowHeightDp)
                                                            .background(bgColor.copy(alpha = 0.35f))
                                                            .clickable {
                                                                onSelectStageTarget(StageSelection(StageTargetType.CAMERA, "camera"))
                                                            }
                                                    ) {
                                                        cameraSettings.keyframes.forEach { ckf ->
                                                            if (ckf.frameIndex in 0 until totalRulerTicks) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.CenterStart)
                                                                        .offset(x = tickWidth * (ckf.frameIndex + 0.5f) - 5.dp)
                                                                        .size(10.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text("◆", fontSize = 8.sp, color = accentColor.copy(alpha = 0.78f))
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Full-height Draggable Cyan Pinça Needle
                                            val pincaCenterPx = (effectiveTickFloat.coerceIn(0f, (totalRulerTicks - 1).toFloat()) + 0.5f) * tickPx
                                            val halfNeedlePx = with(density) { 6.dp.toPx() }
                                            Box(
                                                modifier = Modifier
                                                    .offset { IntOffset((pincaCenterPx - halfNeedlePx).roundToInt(), 0) }
                                                    .width(12.dp)
                                                    .height(gridTotalHeightDp)
                                                    .testTag("camera_timeline_pinca")
                                                    .pointerInput(totalRulerTicks, safeFps, tickPx) {
                                                        val maxScrubMs = TimelineTrackEngine.tickToTimeMs(
                                                            (totalRulerTicks - 1).coerceAtLeast(1),
                                                            safeFps
                                                        )
                                                        var scrubMsFloat = TimelineTrackEngine.tickToTimeMs(currentTimelineTick, safeFps).toFloat()
                                                        detectHorizontalDragGestures(
                                                            onDragStart = {
                                                                scrubMsFloat = (effectiveTickFloat / safeFps.toFloat()) * 1000f
                                                                onScrubToMs(scrubMsFloat.toLong().coerceIn(0L, maxScrubMs))
                                                            },
                                                            onDragEnd = { onEndScrub() },
                                                            onDragCancel = { onEndScrub() }
                                                        ) { change, dragAmount ->
                                                            change.consume()
                                                            val deltaTicks = dragAmount / tickPx
                                                            scrubMsFloat = (scrubMsFloat + (deltaTicks / safeFps.toFloat()) * 1000f)
                                                                .coerceIn(0f, maxScrubMs.toFloat())
                                                            onScrubToMs(scrubMsFloat.toLong())
                                                        }
                                                    },
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(10.dp)
                                                            .height(7.dp)
                                                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                                                            .background(CyanAccent)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .width(2.dp)
                                                            .weight(1f)
                                                            .background(CyanAccent.copy(alpha = 0.92f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2-Point Span Interpolation Selector Bar (Animate-style direct tween editor!)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("two_point_tween_inspector"),
                                    color = bgColor.copy(alpha = 0.68f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.9.dp,
                                        if (activeSpanStart != null && activeSpanEnd != null) OrangeAccent.copy(alpha = 0.55f)
                                        else textColor.copy(alpha = 0.16f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val headerInfo = when {
                                                isPairSelectMode && selectedPairA != null && selectedPairB == null ->
                                                    "Ponto 1: T${selectedPairA!! + 1} ◆ — Toque no Ponto 2 na linha do tempo"
                                                activeSpanStart != null && activeSpanEnd != null -> {
                                                    val durSpan = (activeSpanEnd - activeSpanStart).coerceAtLeast(1)
                                                    val curveTxt = if (!cameraSettings.autoInterpolate) "Desligada"
                                                    else (activeSpanEasing?.labelPt ?: "Bézier Suave")
                                                    "Trecho: T${activeSpanStart + 1} ◆ ━━► ◆ T${activeSpanEnd + 1} (${durSpan}q) • $curveTxt"
                                                }
                                                else ->
                                                    "Toque em 'Escolher 2 Pontos' ou toque duplo na trilha para criar chaves ◆"
                                            }
                                            Text(
                                                text = headerInfo,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeSpanStart != null && activeSpanEnd != null) OrangeAccent else textColor.copy(alpha = 0.80f),
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCameraTarget && activeSpanStart != null && activeSpanEnd != null) {
                                                CamPill(
                                                    label = if (showInlineBezierCurve) "Fechar Curva" else "Curva Manual",
                                                    active = showInlineBezierCurve,
                                                    activeColor = CyanAccent,
                                                    textColor = textColor,
                                                    icon = Icons.Default.ShowChart
                                                ) {
                                                    showInlineBezierCurve = !showInlineBezierCurve
                                                }
                                            }
                                        }

                                        // Curve Chips with Visual Mini Easing Graph Previews
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CameraEasingCurve.entries.forEach { curve ->
                                                val isSel = activeSpanEasing == curve || (activeSpanEasing == null && evaluatedKf.easing == curve)
                                                val chipColor = when (curve) {
                                                    CameraEasingCurve.DEGRAU -> NeonPink
                                                    CameraEasingCurve.LINEAR -> CyanAccent
                                                    CameraEasingCurve.BEZIER_SUAVE -> accentColor
                                                    CameraEasingCurve.BEZIER_GRAFICO -> NeonGreen
                                                    CameraEasingCurve.ACELERAR -> OrangeAccent
                                                    CameraEasingCurve.DESACELERAR -> NeonGreen
                                                    CameraEasingCurve.ELASTICO -> NeonPink
                                                }
                                                val labelText = if (curve == CameraEasingCurve.DEGRAU) "Sem Interp." else curve.labelPt
                                                Surface(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(7.dp))
                                                        .clickable {
                                                            if (activeSpanStart != null && activeSpanEnd != null) {
                                                                onApplyTweenBetween(activeSpanStart, activeSpanEnd, curve)
                                                                if (curve == CameraEasingCurve.BEZIER_GRAFICO && isCameraTarget) {
                                                                    showInlineBezierCurve = true
                                                                }
                                                            } else {
                                                                if (isCameraTarget) onAddOrUpdateKeyframe(curve)
                                                                else onAddOrUpdateSelectedKeyframe(curve)
                                                            }
                                                        }
                                                        .testTag("tween_curve_chip_${curve.name}"),
                                                    color = if (isSel) chipColor.copy(alpha = 0.24f) else textColor.copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(7.dp),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = if (isSel) 1.2.dp else 0.7.dp,
                                                        color = if (isSel) chipColor else textColor.copy(alpha = 0.20f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        MiniEasingCurveIcon(
                                                            curve = curve,
                                                            color = if (isSel) chipColor else textColor.copy(alpha = 0.78f)
                                                        )
                                                        Text(
                                                            text = labelText,
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSel) chipColor else textColor
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Inline Interactive Bezier Curve Graph for the selected 2-point span!
                                        if (showInlineBezierCurve && isCameraTarget && onUpdateBezierHandles != null) {
                                            val bezierSourceKf = activeSpanStart?.let { s ->
                                                cameraSettings.keyframes.firstOrNull { it.frameIndex == s }
                                            } ?: activeOrNearestKf
                                            BezierGraphCurveEditor(
                                                p1x = bezierSourceKf.bezierP1X,
                                                p1y = bezierSourceKf.bezierP1Y,
                                                p2x = bezierSourceKf.bezierP2X,
                                                p2y = bezierSourceKf.bezierP2Y,
                                                bgColor = bgColor,
                                                textColor = textColor,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(92.dp)
                                            ) { np1x, np1y, np2x, np2y ->
                                                onUpdateBezierHandles(bezierSourceKf.frameIndex, np1x, np1y, np2x, np2y)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isWidePc) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                DopeSheetBlock(modifier = Modifier.weight(1.3f))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 195.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    TargetTransformControlsContent(compactForSplit = true)
                                }
                            }
                        } else {
                            DopeSheetBlock(modifier = Modifier.fillMaxWidth())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val quickLabel = if (isCameraTarget) {
                                    "Zoom ${(evaluatedKf.zoom * 100).roundToInt()}% • Giro ${evaluatedKf.rotationZ.roundToInt()}° • FOV ${evaluatedKf.fovDegrees.roundToInt()}° (Transform ▸)"
                                } else {
                                    "$targetDisplayName • X:${evaluatedObjKf.x.roundToInt()} Y:${evaluatedObjKf.y.roundToInt()} Z:${evaluatedObjKf.z.roundToInt()} (Transform ▸)"
                                }
                                CamPill(
                                    label = quickLabel,
                                    active = true,
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    icon = Icons.Default.Tune
                                ) { activeTab = 1 }
                                if (isCameraTarget) {
                                    CamPill("←", false, CyanAccent, textColor) { onNudgeCameraPan(-24f, 0f) }
                                    CamPill("→", false, CyanAccent, textColor) { onNudgeCameraPan(24f, 0f) }
                                    CamPill("Z-", false, accentColor, textColor) { onZoomCameraBy(0.9f) }
                                    CamPill("Z+", false, accentColor, textColor) { onZoomCameraBy(1.1f) }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // TAB 1: TRANSFORM (Câmera & Lente OU Camada 2D / Objeto 3D selecionado)
                    // =========================================================================
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 215.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            TargetTransformControlsContent(compactForSplit = false)
                        }
                    }

                    // =========================================================================
                    // TAB 2: CENAS 3D (Importação de Cenas + Texturas UV + Primitivas + Seleção)
                    // =========================================================================
                    2 -> {
                        val sceneTintSwatches = listOf(
                            0xFFFFFFFFL, 0xFF42A5F5L, 0xFFFFB74DL,
                            0xFF66BB6AL, 0xFFEF5350L, 0xFFAB47BCL
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 225.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable { onImport3DSceneClick() }
                                        .testTag("import_3d_scene_btn"),
                                    color = CyanAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(9.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(15.dp))
                                        Text(
                                            text = "Importar Cena 3D (.glb/.gltf/.obj/.fbx/.max/.3ds)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                                CamPill("Focar Sel.", false, OrangeAccent, textColor, Icons.Default.CenterFocusStrong) {
                                    onFocusStageTarget()
                                }
                            }

                            // Built-in 3D Primitives Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Adicionar 3D:", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.72f))
                                BuiltIn3DPrimitive.entries.forEach { prim ->
                                    CamPill(
                                        label = "+ ${prim.labelPt}",
                                        active = false,
                                        activeColor = NeonGreen,
                                        textColor = textColor,
                                        icon = Icons.Default.ViewInAr,
                                        testTag = "add_3d_prim_${prim.name}"
                                    ) { onAddBuiltIn3DPrimitive(prim) }
                                }
                            }

                            // Outliner: Câmera + Objetos 3D + Camadas 2D
                            Surface(
                                color = if (isCameraTarget) accentColor.copy(alpha = 0.16f) else bgColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isCameraTarget) 1.2.dp else 0.7.dp,
                                    color = if (isCameraTarget) accentColor else textColor.copy(alpha = 0.16f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectStageTarget(StageSelection(StageTargetType.CAMERA, "camera")) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                        Text("Câmera Principal (◆${cameraSettings.keyframes.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    }
                                    CamPill("Focar", false, CyanAccent, textColor, Icons.Default.CenterFocusStrong) {
                                        onSelectStageTarget(StageSelection(StageTargetType.CAMERA, "camera"))
                                        onFocusStageTarget()
                                    }
                                }
                            }

                            cameraSettings.scene3DObjects.forEach { obj ->
                                val isSel = stageSelection.type == StageTargetType.OBJECT_3D && stageSelection.id == obj.id
                                Surface(
                                    color = if (isSel) OrangeAccent.copy(alpha = 0.16f) else bgColor.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSel) 1.3.dp else 0.7.dp,
                                        color = if (isSel) OrangeAccent else textColor.copy(alpha = 0.16f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectStageTarget(StageSelection(StageTargetType.OBJECT_3D, obj.id)) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(Icons.Default.ViewInAr, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(14.dp))
                                                Text("${obj.name} [${obj.formatTag}] • ◆${obj.keyframes.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                CamPill("Focar", false, CyanAccent, textColor) {
                                                    onSelectStageTarget(StageSelection(StageTargetType.OBJECT_3D, obj.id))
                                                    onFocusStageTarget()
                                                }
                                                IconButton(onClick = { onToggle3DObjectVisibility(obj.id) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(
                                                        if (obj.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Visibilidade 3D",
                                                        tint = if (obj.isVisible) NeonGreen else textColor.copy(alpha = 0.4f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                IconButton(onClick = { onDelete3DObject(obj.id) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remover Objeto 3D", tint = NeonPink, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                        if (isSel) {
                                            BgColorSwatchRow(
                                                label = "Matiz 3D:",
                                                swatches = sceneTintSwatches,
                                                selectedColor = obj.colorTint,
                                                accentColor = OrangeAccent,
                                                textColor = textColor,
                                                onSelect = { c -> onSet3DObjectTint(obj.id, c) }
                                            )
                                        }
                                    }
                                }
                            }

                            layers.forEach { layer ->
                                val isSel = stageSelection.type == StageTargetType.LAYER && stageSelection.id == layer.id
                                val trackCount = cameraSettings.layerTransformTracks.firstOrNull { it.layerId == layer.id }?.keyframes?.size ?: 0
                                Surface(
                                    color = if (isSel) CyanAccent.copy(alpha = 0.16f) else bgColor.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSel) 1.2.dp else 0.7.dp,
                                        color = if (isSel) CyanAccent else textColor.copy(alpha = 0.14f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectStageTarget(StageSelection(StageTargetType.LAYER, layer.id)) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Icon(Icons.Default.Layers, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(13.dp))
                                            Text("Camada: ${layer.name} (${layer.strokes.size} traços • ◆$trackCount)", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                        }
                                        CamPill("Focar", false, CyanAccent, textColor) {
                                            onSelectStageTarget(StageSelection(StageTargetType.LAYER, layer.id))
                                            onFocusStageTarget()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // TAB 3: EFEITOS (Sub-abas: Auto Efeitos & Pasta | Curva Gráfica | Pós-FX)
                    // =========================================================================
                    3 -> {
                        val targetStart = if (effectApplyToPair && pairMin != null && pairMax != null) pairMin else currentTimelineTick
                        val targetEnd = if (effectApplyToPair && pairMin != null && pairMax != null) pairMax else (currentTimelineTick + pinchEffectDuration - 1).coerceAtMost((totalRulerTicks - 1).coerceAtLeast(currentTimelineTick))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 175.dp, max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CamPill("Auto & Pasta", activeEffectsSubTab == 0, secondaryColor, textColor, testTag = "cam_tab_effects_auto") {
                                    activeEffectsSubTab = 0
                                }
                                CamPill("Curva Gráfica", activeEffectsSubTab == 1, CyanAccent, textColor, Icons.Default.ShowChart, testTag = "cam_tab_curves") {
                                    activeEffectsSubTab = 1
                                }
                                CamPill("Pós-FX", activeEffectsSubTab == 2, NeonPink, textColor, Icons.Default.Tune, testTag = "cam_tab_post") {
                                    activeEffectsSubTab = 2
                                }
                            }

                            when (activeEffectsSubTab) {
                                0 -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CamPill(
                                            label = "Na Pinça (T${currentTimelineTick + 1}..${(currentTimelineTick + pinchEffectDuration).coerceAtMost(totalRulerTicks.coerceAtLeast(1))})",
                                            active = !effectApplyToPair,
                                            activeColor = secondaryColor,
                                            textColor = textColor,
                                            testTag = "effect_target_pinch_btn"
                                        ) { effectApplyToPair = false }

                                        CamPill(
                                            label = if (pairMin != null && pairMax != null) "Entre Pontos (T${pairMin + 1}..T${pairMax + 1})" else "Entre 2 Pontos",
                                            active = effectApplyToPair,
                                            activeColor = OrangeAccent,
                                            textColor = textColor,
                                            testTag = "effect_target_pair_btn"
                                        ) { effectApplyToPair = true }

                                        if (!effectApplyToPair) {
                                            Text("Dur.:", fontSize = 9.sp, color = textColor.copy(alpha = 0.8f))
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(textColor.copy(alpha = 0.14f))
                                                    .clickable { pinchEffectDuration = (pinchEffectDuration - 1).coerceIn(2, 60) },
                                                contentAlignment = Alignment.Center
                                            ) { Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor) }
                                            Text("${pinchEffectDuration}q", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = secondaryColor)
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(textColor.copy(alpha = 0.14f))
                                                    .clickable { pinchEffectDuration = (pinchEffectDuration + 1).coerceIn(2, 60) },
                                                contentAlignment = Alignment.Center
                                            ) { Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor) }
                                        }
                                    }

                                    CamSlider(
                                        label = "Força",
                                        valueText = "${(effectIntensity * 100).roundToInt()}%",
                                        value = effectIntensity,
                                        range = 0.25f..2.0f,
                                        accentColor = secondaryColor,
                                        textColor = textColor,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { effectIntensity = it }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CameraBuiltInEffect.entries.forEach { eff ->
                                            Surface(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .clickable {
                                                        if (onApplyEffectInRange != null) {
                                                            onApplyEffectInRange(eff, targetStart, targetEnd, effectIntensity)
                                                        } else {
                                                            onApplyBuiltInEffect(eff)
                                                        }
                                                    }
                                                    .testTag("camera_apply_effect_${eff.name}"),
                                                color = secondaryColor.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(9.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, secondaryColor.copy(alpha = 0.45f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = secondaryColor, modifier = Modifier.size(13.dp))
                                                    Text(eff.labelPt, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                }
                                            }
                                        }
                                    }

                                    if (cameraSettings.effectSpans.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            cameraSettings.effectSpans.forEach { span ->
                                                Surface(
                                                    color = bgColor.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(7.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, secondaryColor.copy(alpha = 0.5f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(start = 7.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            "${span.effect.labelPt}: Q${span.startFrame + 1}–Q${span.endFrame + 1}",
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textColor
                                                        )
                                                        IconButton(
                                                            onClick = { onRemoveEffectSpan?.invoke(span.id) },
                                                            modifier = Modifier.size(18.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Remover efeito", tint = NeonPink, modifier = Modifier.size(11.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(15.dp))
                                        OutlinedTextField(
                                            value = templateNameInput,
                                            onValueChange = { templateNameInput = it },
                                            placeholder = { Text("Nome na Pasta Animada...", fontSize = 10.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onSaveTemplateToFolder(templateNameInput)
                                                    templateNameInput = ""
                                                },
                                            color = OrangeAccent.copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.65f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.CreateNewFolder, contentDescription = "Salvar na Pasta Animada", tint = OrangeAccent, modifier = Modifier.size(14.dp))
                                                Text("Salvar Pasta", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            }
                                        }
                                    }

                                    if (savedTemplates.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            savedTemplates.forEach { tpl ->
                                                Surface(
                                                    color = bgColor.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent.copy(alpha = 0.4f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .clickable { onApplyTemplateFromFolder(tpl) }
                                                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(tpl.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                        IconButton(
                                                            onClick = { onDeleteTemplate(tpl.id) },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Excluir pasta", tint = NeonPink, modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                1 -> {
                                    // Bezier Curve Graph Editor
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Curva de Interpolação (Q${activeOrNearestKf.frameIndex + 1})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                        Text(
                                            text = "P1(${String.format("%.2f", activeOrNearestKf.bezierP1X)}, ${String.format("%.2f", activeOrNearestKf.bezierP1Y)}) • P2(${String.format("%.2f", activeOrNearestKf.bezierP2X)}, ${String.format("%.2f", activeOrNearestKf.bezierP2Y)})",
                                            fontSize = 9.sp,
                                            color = textColor.copy(alpha = 0.72f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CameraEasingCurve.entries.forEach { curve ->
                                            val isSel = activeOrNearestKf.easing == curve
                                            CamPill(curve.labelPt, isSel, CyanAccent, textColor) {
                                                onAddOrUpdateKeyframe(curve)
                                            }
                                        }
                                    }

                                    BezierGraphCurveEditor(
                                        p1x = activeOrNearestKf.bezierP1X,
                                        p1y = activeOrNearestKf.bezierP1Y,
                                        p2x = activeOrNearestKf.bezierP2X,
                                        p2y = activeOrNearestKf.bezierP2Y,
                                        bgColor = bgColor,
                                        textColor = textColor,
                                        onHandlesChanged = { np1x, np1y, np2x, np2y ->
                                            if (onUpdateBezierHandles != null) {
                                                onUpdateBezierHandles(activeOrNearestKf.frameIndex, np1x, np1y, np2x, np2y)
                                            } else {
                                                onUpdateCurrentCamera {
                                                    it.copy(
                                                        easing = CameraEasingCurve.BEZIER_GRAFICO,
                                                        bezierP1X = np1x,
                                                        bezierP1Y = np1y,
                                                        bezierP2X = np2x,
                                                        bezierP2Y = np2y
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                else -> {
                                    // Pós-FX
                                    val fx = cameraSettings.postEffects
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Pós-Efeitos da Cena", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonPink)
                                        CamPill("Zerar Pós-Efeitos", false, NeonPink, NeonPink) { onResetPostEffects() }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CamSlider("Vinheta", "${(fx.vignette * 100).roundToInt()}%", fx.vignette, 0f..1f, NeonPink, textColor, Modifier.weight(1f)) {
                                            onUpdatePostEffects(fx.copy(vignette = it))
                                        }
                                        CamSlider("Bloom", "${(fx.bloom * 100).roundToInt()}%", fx.bloom, 0f..1f, NeonPink, textColor, Modifier.weight(1f)) {
                                            onUpdatePostEffects(fx.copy(bloom = it))
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CamSlider("Aberração", "${(fx.chromaticAberration * 100).roundToInt()}%", fx.chromaticAberration, 0f..1f, secondaryColor, textColor, Modifier.weight(1f)) {
                                            onUpdatePostEffects(fx.copy(chromaticAberration = it))
                                        }
                                        CamSlider("Grão Filme", "${(fx.filmGrain * 100).roundToInt()}%", fx.filmGrain, 0f..1f, secondaryColor, textColor, Modifier.weight(1f)) {
                                            onUpdatePostEffects(fx.copy(filmGrain = it))
                                        }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CamSlider("Sépia", "${(fx.sepia * 100).roundToInt()}%", fx.sepia, 0f..1f, OrangeAccent, textColor, Modifier.weight(1f)) {
                                            onUpdatePostEffects(fx.copy(sepia = it))
                                        }
                                        CamSlider("Desf. Radial", "${(fx.radialBlur * 100).roundToInt()}%", fx.radialBlur, 0f..1f, OrangeAccent, textColor, Modifier.weight(1f)) {
                                            onUpdatePostEffects(fx.copy(radialBlur = it))
                                        }
                                    }
                                    CamSlider("Contraste", "${(fx.contrastBoost * 100).roundToInt()}%", fx.contrastBoost, -0.45f..0.45f, accentColor, textColor, Modifier.fillMaxWidth()) {
                                        onUpdatePostEffects(fx.copy(contrastBoost = it))
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // TAB 4: FUNDO & CÉU (Papel + Ponto Claro / Escuro + Stops + Roda de Cores HSV)
                    // =========================================================================
                    else -> {
                        val effectiveLightColor = if (canvasBackground.skyLightColor != 0L) canvasBackground.skyLightColor else canvasBackground.skyTopColor
                        val effectiveDarkColor = if (canvasBackground.skyDarkColor != 0L) canvasBackground.skyDarkColor else canvasBackground.skyBottomColor
                        val sortedStops = remember(canvasBackground.skyGradientStops) {
                            canvasBackground.skyGradientStops.sortedBy { it.position }
                        }
                        val activeStop = sortedStops.firstOrNull { it.id == selectedSkyStopId } ?: sortedStops.firstOrNull()

                        val currentWheelColor = when (skyWheelTarget) {
                            0 -> effectiveLightColor
                            1 -> effectiveDarkColor
                            2 -> activeStop?.color ?: effectiveLightColor
                            3 -> canvasBackground.primaryColor
                            4 -> canvasBackground.secondaryColor
                            else -> effectiveLightColor
                        }

                        fun applyWheelColor(newColor: Long) {
                            when (skyWheelTarget) {
                                0 -> onUpdateCanvasBackground(
                                    canvasBackground.copy(skyLightColor = newColor, skyTopColor = newColor)
                                )
                                1 -> onUpdateCanvasBackground(
                                    canvasBackground.copy(skyDarkColor = newColor, skyBottomColor = newColor)
                                )
                                2 -> {
                                    val stopId = activeStop?.id ?: return
                                    val updatedStops = canvasBackground.skyGradientStops.map {
                                        if (it.id == stopId) it.copy(color = newColor) else it
                                    }
                                    onUpdateCanvasBackground(canvasBackground.copy(skyGradientStops = updatedStops))
                                }
                                3 -> onUpdateCanvasBackground(canvasBackground.copy(primaryColor = newColor))
                                4 -> onUpdateCanvasBackground(canvasBackground.copy(secondaryColor = newColor))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Estilo do Papel + Textura
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Papel:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                                CanvasBackgroundStyle.entries.forEach { st ->
                                    val isSel = canvasBackground.style == st
                                    CamPill(st.labelPt, isSel, NeonGreen, textColor, testTag = "cam_bg_style_${st.name}") {
                                        onUpdateCanvasBackground(canvasBackground.copy(style = st))
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Textura:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                                CanvasTexturePattern.entries.forEach { pat ->
                                    val isSel = canvasBackground.texturePattern == pat
                                    CamPill(pat.labelPt, isSel, OrangeAccent, textColor) {
                                        val nextStyle = if (pat != CanvasTexturePattern.NENHUMA && canvasBackground.style == CanvasBackgroundStyle.SOLIDO) {
                                            CanvasBackgroundStyle.TEXTURA
                                        } else canvasBackground.style
                                        onUpdateCanvasBackground(canvasBackground.copy(texturePattern = pat, style = nextStyle))
                                    }
                                }
                            }
                            if (canvasBackground.texturePattern != CanvasTexturePattern.NENHUMA) {
                                CamSlider("Força Tex", "${(canvasBackground.textureIntensity * 100).roundToInt()}%", canvasBackground.textureIntensity, 0.05f..0.85f, OrangeAccent, textColor, Modifier.fillMaxWidth()) { inten ->
                                    onUpdateCanvasBackground(canvasBackground.copy(textureIntensity = inten))
                                }
                            }

                            // Modo Céu + Ângulo do Céu
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Céu:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                                SkyBackdropMode.entries.forEach { sky ->
                                    val isSel = canvasBackground.skyMode == sky
                                    CamPill(sky.labelPt, isSel, secondaryColor, textColor, testTag = "cam_sky_mode_${sky.name}") {
                                        onUpdateCanvasBackground(canvasBackground.copy(skyMode = sky))
                                    }
                                }
                            }

                            CamSlider(
                                label = "Âng. Céu",
                                valueText = "${canvasBackground.skyGradientAngleDeg.roundToInt()}°",
                                value = canvasBackground.skyGradientAngleDeg,
                                range = 0f..360f,
                                accentColor = secondaryColor,
                                textColor = textColor,
                                modifier = Modifier.fillMaxWidth()
                            ) { ang ->
                                onUpdateCanvasBackground(canvasBackground.copy(skyGradientAngleDeg = ang))
                            }

                            // Multi-Stop Sky Gradient Preview Bar
                            val previewColorStops = remember(effectiveLightColor, effectiveDarkColor, sortedStops) {
                                buildList {
                                    add(0f to Color(effectiveLightColor))
                                    sortedStops.forEach { st ->
                                        add(st.position.coerceIn(0.02f, 0.98f) to Color(st.color))
                                    }
                                    add(1f to Color(effectiveDarkColor))
                                }.toTypedArray()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Brush.horizontalGradient(colorStops = previewColorStops))
                                    .border(0.8.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            )

                            // Ponto Claro / Ponto Escuro / Pontos extras + Papel Cores
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ColorPointPill(
                                    label = "Ponto Claro",
                                    color = Color(effectiveLightColor),
                                    selected = skyWheelTarget == 0,
                                    accentColor = OrangeAccent,
                                    textColor = textColor,
                                    testTag = "sky_point_light_btn"
                                ) { skyWheelTarget = 0 }

                                ColorPointPill(
                                    label = "Ponto Escuro",
                                    color = Color(effectiveDarkColor),
                                    selected = skyWheelTarget == 1,
                                    accentColor = CyanAccent,
                                    textColor = textColor,
                                    testTag = "sky_point_dark_btn"
                                ) { skyWheelTarget = 1 }

                                sortedStops.forEachIndexed { idx, stop ->
                                    val isSel = skyWheelTarget == 2 && activeStop?.id == stop.id
                                    ColorPointPill(
                                        label = "Cor #${idx + 1} (${(stop.position * 100).roundToInt()}%)",
                                        color = Color(stop.color),
                                        selected = isSel,
                                        accentColor = NeonGreen,
                                        textColor = textColor,
                                        testTag = "sky_stop_pill_$idx"
                                    ) {
                                        selectedSkyStopId = stop.id
                                        skyWheelTarget = 2
                                    }
                                }

                                CamPill("+ Ponto", false, NeonGreen, textColor, Icons.Default.Add, testTag = "add_sky_stop_btn") {
                                    val nextPos = if (sortedStops.isEmpty()) 0.5f else ((sortedStops.last().position + 0.15f).coerceIn(0.1f, 0.9f))
                                    val newStop = GradientColorStop(position = nextPos, color = 0xFF3E6C9AL)
                                    val updated = (canvasBackground.skyGradientStops + newStop).sortedBy { it.position }
                                    onUpdateCanvasBackground(canvasBackground.copy(skyGradientStops = updated))
                                    selectedSkyStopId = newStop.id
                                    skyWheelTarget = 2
                                }

                                if (skyWheelTarget == 2 && activeStop != null) {
                                    CamPill("Remover", false, NeonPink, NeonPink, Icons.Default.DeleteOutline) {
                                        val updated = canvasBackground.skyGradientStops.filterNot { it.id == activeStop.id }
                                        onUpdateCanvasBackground(canvasBackground.copy(skyGradientStops = updated))
                                        selectedSkyStopId = updated.firstOrNull()?.id
                                        if (updated.isEmpty()) skyWheelTarget = 0
                                    }
                                }

                                if (canvasBackground.style != CanvasBackgroundStyle.TRANSPARENTE) {
                                    ColorPointPill(
                                        label = "Papel 1",
                                        color = Color(canvasBackground.primaryColor),
                                        selected = skyWheelTarget == 3,
                                        accentColor = accentColor,
                                        textColor = textColor
                                    ) { skyWheelTarget = 3 }
                                    if (canvasBackground.style == CanvasBackgroundStyle.DEGRADE || canvasBackground.style == CanvasBackgroundStyle.TEXTURA) {
                                        ColorPointPill(
                                            label = "Papel 2",
                                            color = Color(canvasBackground.secondaryColor),
                                            selected = skyWheelTarget == 4,
                                            accentColor = secondaryColor,
                                            textColor = textColor
                                        ) { skyWheelTarget = 4 }
                                    }
                                }
                            }

                            if (skyWheelTarget == 2 && activeStop != null) {
                                CamSlider(
                                    label = "Posição",
                                    valueText = "${(activeStop.position * 100).roundToInt()}%",
                                    value = activeStop.position,
                                    range = 0.05f..0.95f,
                                    accentColor = NeonGreen,
                                    textColor = textColor,
                                    modifier = Modifier.fillMaxWidth()
                                ) { pos ->
                                    val updated = canvasBackground.skyGradientStops.map {
                                        if (it.id == activeStop.id) it.copy(position = pos) else it
                                    }
                                    onUpdateCanvasBackground(canvasBackground.copy(skyGradientStops = updated))
                                }
                            }

                            // Inline HSV Roda de Cores (sem precisar de diálogo ou confirmar!)
                            InlineSkyHsvWheel(
                                selectedColor = currentWheelColor,
                                textColor = textColor,
                                accentColor = accentColor,
                                onColorChanged = { applyWheelColor(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPointPill(
    label: String,
    color: Color,
    selected: Boolean,
    accentColor: Color,
    textColor: Color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.08f))
            .border(if (selected) 1.3.dp else 0.7.dp, if (selected) accentColor else textColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .let { if (testTag != null) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .border(0.8.dp, Color.White.copy(alpha = 0.7f), CircleShape)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) accentColor else textColor
        )
    }
}

@Composable
private fun InlineSkyHsvWheel(
    selectedColor: Long,
    textColor: Color,
    accentColor: Color,
    onColorChanged: (Long) -> Unit
) {
    val hsvInit = remember(selectedColor) {
        val arr = FloatArray(3)
        val argb = selectedColor.toInt()
        android.graphics.Color.RGBToHSV(
            (argb ushr 16) and 0xFF,
            (argb ushr 8) and 0xFF,
            argb and 0xFF,
            arr
        )
        arr
    }
    var hue by remember { mutableFloatStateOf(hsvInit[0]) }
    var sat by remember { mutableFloatStateOf(hsvInit[1]) }
    var bri by remember { mutableFloatStateOf(hsvInit[2]) }

    LaunchedEffect(selectedColor) {
        val currentArgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri))
        if ((0xFF000000L or (currentArgb.toLong() and 0xFFFFFFL)) != selectedColor) {
            hue = hsvInit[0]
            sat = hsvInit[1]
            bri = hsvInit[2]
        }
    }

    fun emitColor(h: Float, s: Float, v: Float) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
        onColorChanged(0xFF000000L or (rgb.toLong() and 0xFFFFFFL))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Roda de Cores HSV (anel Matiz + disco interno Saturação)
        Canvas(
            modifier = Modifier
                .size(92.dp)
                .testTag("sky_hsv_color_wheel")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val rMax = size.width / 2f
                        val ringInner = rMax * 0.68f
                        val dist0 = hypot(down.position.x - cx, down.position.y - cy)
                        val editingRing = dist0 >= ringInner

                        fun applyPos(pos: Offset) {
                            val dx = pos.x - cx
                            val dy = pos.y - cy
                            var ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            if (ang < 0f) ang += 360f
                            if (editingRing) {
                                hue = ang
                            } else {
                                val dist = hypot(dx, dy).coerceIn(0f, ringInner)
                                hue = ang
                                sat = (dist / ringInner.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            }
                            emitColor(hue, sat, bri)
                        }

                        down.consume()
                        applyPos(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            applyPos(ch.position)
                        }
                    }
                }
        ) {
            val radius = size.width / 2f
            val center = Offset(radius, radius)
            val strokeW = radius * 0.28f
            val ringMidR = radius - strokeW / 2f
            val innerR = radius - strokeW

            val sweepColors = listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
            drawCircle(
                brush = Brush.sweepGradient(sweepColors, center),
                radius = ringMidR,
                style = Stroke(width = strokeW)
            )
            drawCircle(
                brush = Brush.sweepGradient(sweepColors, center),
                radius = innerR - 2f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = innerR - 2f
                ),
                radius = innerR - 2f
            )

            // Hue indicator dot on ring
            val rad = Math.toRadians(hue.toDouble())
            val hPos = Offset(
                center.x + (ringMidR * cos(rad)).toFloat(),
                center.y + (ringMidR * sin(rad)).toFloat()
            )
            drawCircle(Color.White, radius = 5f, center = hPos)
            drawCircle(Color.Black, radius = 5f, center = hPos, style = Stroke(1.5f))

            // Saturation indicator dot inside disk
            val sR = (innerR - 3f) * sat
            val sPos = Offset(
                center.x + (sR * cos(rad)).toFloat(),
                center.y + (sR * sin(rad)).toFloat()
            )
            drawCircle(Color.White, radius = 5.5f, center = sPos)
            drawCircle(Color(selectedColor), radius = 3.5f, center = sPos)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(selectedColor))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                )
                Text(
                    text = "Roda Livre • H:${hue.roundToInt()}° S:${(sat * 100).roundToInt()}% L:${(bri * 100).roundToInt()}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor.copy(alpha = 0.85f)
                )
            }
            CamSlider("Brilho", "${(bri * 100).roundToInt()}%", bri, 0.02f..1f, accentColor, textColor) { newV ->
                bri = newV
                emitColor(hue, sat, bri)
            }
            CamSlider("Saturação", "${(sat * 100).roundToInt()}%", sat, 0f..1f, NeonGreen, textColor) { newS ->
                sat = newS
                emitColor(hue, sat, bri)
            }
        }
    }
}

@Composable
private fun MiniEasingCurveIcon(
    curve: CameraEasingCurve,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(16.dp, 12.dp)) {
        val w = size.width
        val h = size.height
        val pad = 1.5f
        val uw = (w - pad * 2f).coerceAtLeast(2f)
        val uh = (h - pad * 2f).coerceAtLeast(2f)
        val p = Path()
        val steps = 10
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val yVal = Camera3DEngine.applyEasing(t, curve).coerceIn(-0.15f, 1.15f)
            val px = pad + t * uw
            val py = pad + (1f - yVal) * uh
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        drawPath(p, color = color, style = Stroke(width = 1.7f))
    }
}

@Composable
private fun BezierGraphCurveEditor(
    p1x: Float,
    p1y: Float,
    p2x: Float,
    p2y: Float,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier.fillMaxWidth().height(136.dp),
    onHandlesChanged: (Float, Float, Float, Float) -> Unit
) {
    var localP1x by remember(p1x) { mutableFloatStateOf(p1x) }
    var localP1y by remember(p1y) { mutableFloatStateOf(p1y) }
    var localP2x by remember(p2x) { mutableFloatStateOf(p2x) }
    var localP2y by remember(p2y) { mutableFloatStateOf(p2y) }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor.copy(alpha = 0.78f))
            .border(1.dp, CyanAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .testTag("camera_bezier_graph_canvas")
            .pointerInput(Unit) {
                val padX = 18f
                val padY = 14f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val usableW = (size.width - padX * 2f).coerceAtLeast(10f)
                    val usableH = (size.height - padY * 2f).coerceAtLeast(10f)
                    val h1 = Offset(padX + localP1x * usableW, padY + (1f - localP1y) * usableH)
                    val h2 = Offset(padX + localP2x * usableW, padY + (1f - localP2y) * usableH)
                    val d1 = hypot(down.position.x - h1.x, down.position.y - h1.y)
                    val d2 = hypot(down.position.x - h2.x, down.position.y - h2.y)
                    val dragTarget = if (d1 <= d2) 1 else 2

                    fun updateHandle(pos: Offset) {
                        val nx = ((pos.x - padX) / usableW).coerceIn(0.02f, 0.98f)
                        val ny = (1f - (pos.y - padY) / usableH).coerceIn(-0.25f, 1.25f)
                        if (dragTarget == 1) {
                            localP1x = nx; localP1y = ny
                        } else {
                            localP2x = nx; localP2y = ny
                        }
                        onHandlesChanged(localP1x, localP1y, localP2x, localP2y)
                    }

                    down.consume()
                    updateHandle(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        updateHandle(ch.position)
                    }
                }
            }
    ) {
        val padX = 18f
        val padY = 14f
        val usableW = (size.width - padX * 2f).coerceAtLeast(10f)
        val usableH = (size.height - padY * 2f).coerceAtLeast(10f)

        for (i in 0..4) {
            val f = i / 4f
            val x = padX + f * usableW
            val y = padY + f * usableH
            drawLine(textColor.copy(alpha = 0.10f), Offset(x, padY), Offset(x, padY + usableH), strokeWidth = 1f)
            drawLine(textColor.copy(alpha = 0.10f), Offset(padX, y), Offset(padX + usableW, y), strokeWidth = 1f)
        }

        val p0 = Offset(padX, padY + usableH)
        val p1 = Offset(padX + localP1x * usableW, padY + (1f - localP1y) * usableH)
        val p2 = Offset(padX + localP2x * usableW, padY + (1f - localP2y) * usableH)
        val p3 = Offset(padX + usableW, padY)

        drawLine(textColor.copy(alpha = 0.22f), p0, p3, strokeWidth = 1.2f)
        drawLine(CyanAccent.copy(alpha = 0.65f), p0, p1, strokeWidth = 1.8f)
        drawLine(NeonPink.copy(alpha = 0.65f), p3, p2, strokeWidth = 1.8f)

        val curvePath = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }
        drawPath(curvePath, color = OrangeAccent, style = Stroke(width = 3.2f))

        drawCircle(textColor.copy(alpha = 0.8f), radius = 4f, center = p0)
        drawCircle(textColor.copy(alpha = 0.8f), radius = 4f, center = p3)
        drawCircle(Color.White, radius = 8f, center = p1)
        drawCircle(CyanAccent, radius = 6f, center = p1)
        drawCircle(Color.White, radius = 8f, center = p2)
        drawCircle(NeonPink, radius = 6f, center = p2)
    }
}

@Composable
private fun CamPill(
    label: String,
    active: Boolean,
    activeColor: Color,
    textColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val mod = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(if (active) activeColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.08f))
        .border(0.8.dp, if (active) activeColor else textColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
        .clickable { onClick() }
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, tint = if (active) activeColor else textColor, modifier = Modifier.size(13.dp))
        }
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) activeColor else textColor
        )
    }
}

@Composable
private fun CamTabPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    textColor: Color,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.28f) else Color.Transparent)
            .border(1.dp, if (selected) accentColor else textColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) accentColor else textColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun CamSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 9.sp, color = textColor.copy(alpha = 0.8f), modifier = Modifier.width(50.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .height(18.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            )
        )
        Text(valueText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accentColor, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun BgColorSwatchRow(
    label: String,
    swatches: List<Long>,
    selectedColor: Long,
    accentColor: Color,
    textColor: Color,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor.copy(alpha = 0.8f),
            modifier = Modifier.width(56.dp)
        )
        swatches.forEach { swatchLong ->
            val isSel = selectedColor == swatchLong
            Box(
                modifier = Modifier
                    .size(if (isSel) 20.dp else 17.dp)
                    .clip(CircleShape)
                    .background(Color(swatchLong))
                    .border(
                        width = if (isSel) 2.dp else 0.8.dp,
                        color = if (isSel) accentColor else textColor.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(swatchLong) }
            )
        }
    }
}

@Composable
private fun TransformRotationDial(
    angleDeg: Float,
    accentColor: Color,
    textColor: Color,
    testTag: String,
    onAngleChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(textColor.copy(alpha = 0.06f))
            .border(1.3.dp, accentColor.copy(alpha = 0.55f), CircleShape)
            .testTag(testTag)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    fun emit(pos: Offset) {
                        var deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat() + 90f
                        while (deg > 180f) deg -= 360f
                        while (deg < -180f) deg += 360f
                        onAngleChange(deg.coerceIn(-180f, 180f))
                    }
                    down.consume()
                    emit(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        emit(ch.position)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width * 0.40f
            val rad = Math.toRadians((angleDeg - 90f).toDouble())
            val tip = Offset(c.x + (r * cos(rad)).toFloat(), c.y + (r * sin(rad)).toFloat())
            drawCircle(accentColor.copy(alpha = 0.25f), radius = r, center = c, style = Stroke(2f))
            drawLine(accentColor, c, tip, strokeWidth = 2.6f)
            drawCircle(accentColor, radius = 5f, center = tip)
        }
        Text(
            text = "${angleDeg.roundToInt()}°",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

