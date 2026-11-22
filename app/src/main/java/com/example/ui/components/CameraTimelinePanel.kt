package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.Camera3DEngine
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
import com.example.model.KodanimaTheme
import com.example.model.SkyBackdropMode
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Painel inferior do Estúdio de Câmera 2D e 3D Animada:
 * - Modo Câmera 2D Plano e Modo Câmera 3D Espacial
 * - Dope Sheet com canais (Resumo, Loc X/Y, Zoom, Rot Z, FOV, Incl/Órbita 3D), diamantes ◆ e conexão entre chaves
 * - Editor de Curvas Gráficas (Bezier interativa P1 / P2 arrastáveis)
 * - Efeitos Automáticos aplicáveis entre 2 pontos ou na posição da pinça (N quadros)
 * - Pós-Efeitos de Cena, Pasta Animada e Fundo/Céu 3D & 2D
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
    modifier: Modifier = Modifier
) {
    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val secondaryColor = Color(theme.secondaryAccent)
    val textColor = Color(theme.text)

    // 0 = Timeline (Dope Sheet), 1 = Câmera (Transform/Lentes/Res.), 2 = Curvas, 3 = Efeitos & Pasta, 4 = Pós-FX, 5 = Fundo / Céu
    var activeTab by remember { mutableIntStateOf(0) }
    var customResW by remember(cameraSettings.captureWidth) { mutableStateOf(cameraSettings.captureWidth.toString()) }
    var customResH by remember(cameraSettings.captureHeight) { mutableStateOf(cameraSettings.captureHeight.toString()) }
    var templateNameInput by remember { mutableStateOf("") }

    // Pair selection for Dope Sheet & ranged effects
    var isPairSelectMode by remember { mutableStateOf(false) }
    var selectedPairA by remember { mutableStateOf<Int?>(null) }
    var selectedPairB by remember { mutableStateOf<Int?>(null) }
    var effectApplyToPair by remember { mutableStateOf(false) }
    var pinchEffectDuration by remember { mutableIntStateOf(8) }
    var effectIntensity by remember { mutableFloatStateOf(1.0f) }

    val is3DMode = cameraSettings.cameraMode == CameraMode.MODE_3D
    val evaluatedKf = remember(cameraSettings, currentFrameIndex) {
        Camera3DEngine.evaluateCameraAtFrame(cameraSettings, currentFrameIndex)
    }
    val hasKeyAtCurrent = cameraSettings.keyframes.any { it.frameIndex == currentFrameIndex }
    val activeOrNearestKf = remember(cameraSettings.keyframes, currentFrameIndex) {
        cameraSettings.keyframes.firstOrNull { it.frameIndex == currentFrameIndex }
            ?: cameraSettings.keyframes.minByOrNull { kotlin.math.abs(it.frameIndex - currentFrameIndex) }
            ?: evaluatedKf
    }
    val sortedKeyFrames = remember(cameraSettings.keyframes) {
        cameraSettings.keyframes.map { it.frameIndex }.sorted()
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
                                label = "Estúdio 3D",
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

                    // Pinned Close Button (never clipped off on mobile)
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
                            Text("Timeline", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }

                // Row 2: Dedicated Studio Tab Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CamTabPill("Timeline", activeTab == 0, accentColor, textColor, "cam_tab_dope") { activeTab = 0 }
                    CamTabPill("Câmera & Lente", activeTab == 1, OrangeAccent, textColor, "cam_tab_camera") { activeTab = 1 }
                    CamTabPill("Curvas", activeTab == 2, CyanAccent, textColor, "cam_tab_curves") { activeTab = 2 }
                    CamTabPill("Efeitos & Pasta", activeTab == 3, secondaryColor, textColor, "cam_tab_effects") { activeTab = 3 }
                    CamTabPill("Pós-FX", activeTab == 4, NeonPink, textColor, "cam_tab_post") { activeTab = 4 }
                    CamTabPill("Fundo / Céu", activeTab == 5, NeonGreen, textColor, "cam_tab_bg") { activeTab = 5 }
                }

                @Composable
                fun CameraTransformControlsContent(compactForSplit: Boolean) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Quick Nudge Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mover:", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.72f))
                            CamPill("←", false, CyanAccent, textColor) { onNudgeCameraPan(-24f, 0f) }
                            CamPill("↑", false, CyanAccent, textColor) { onNudgeCameraPan(0f, -24f) }
                            CamPill("↓", false, CyanAccent, textColor) { onNudgeCameraPan(0f, 24f) }
                            CamPill("→", false, CyanAccent, textColor) { onNudgeCameraPan(24f, 0f) }
                            CamPill("Zoom -", false, accentColor, textColor) { onZoomCameraBy(0.9f) }
                            CamPill("Zoom +", false, accentColor, textColor) { onZoomCameraBy(1.1f) }
                            CamPill("↺ 15°", false, secondaryColor, textColor) { onRotateCameraBy(-15f) }
                            CamPill("↻ 15°", false, secondaryColor, textColor) { onRotateCameraBy(15f) }
                            if (is3DMode) {
                                CamPill("Yaw-", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(-5f, 0f) }
                                CamPill("Yaw+", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(5f, 0f) }
                                CamPill("Pitch-", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(0f, -5f) }
                                CamPill("Pitch+", false, OrangeAccent, textColor) { onTiltOrbitCameraBy(0f, 5f) }
                            }
                            CamPill("Zerar Q${currentFrameIndex + 1}", false, CyanAccent, textColor, Icons.Default.RestartAlt) {
                                onResetCurrentKeyframe()
                            }
                            if (!compactForSplit) {
                                CamPill("Zerar Tudo", false, NeonPink, NeonPink, Icons.Default.RestartAlt) {
                                    onResetKeyframes()
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CamSlider("Zoom", "${((evaluatedKf.zoom * 100).roundToInt())}%", evaluatedKf.zoom, 0.3f..4.0f, accentColor, textColor, Modifier.weight(1f)) { z ->
                                onUpdateCurrentCamera { it.copy(zoom = z) }
                            }
                            CamSlider("Giro Z", "${evaluatedKf.rotationZ.roundToInt()}°", evaluatedKf.rotationZ, -180f..180f, accentColor, textColor, Modifier.weight(1f)) { rz ->
                                onUpdateCurrentCamera { it.copy(rotationZ = rz) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CamSlider("Pan X", "${evaluatedKf.x.roundToInt()}", evaluatedKf.x, -800f..800f, secondaryColor, textColor, Modifier.weight(1f)) { px ->
                                onUpdateCurrentCamera { it.copy(x = px) }
                            }
                            CamSlider("Pan Y", "${evaluatedKf.y.roundToInt()}", evaluatedKf.y, -800f..800f, secondaryColor, textColor, Modifier.weight(1f)) { py ->
                                onUpdateCurrentCamera { it.copy(y = py) }
                            }
                        }

                        CamSlider(
                            label = "Lente FOV",
                            valueText = "${evaluatedKf.fovDegrees.roundToInt()}°",
                            value = evaluatedKf.fovDegrees,
                            range = 18f..120f,
                            accentColor = CyanAccent,
                            textColor = textColor,
                            modifier = Modifier.fillMaxWidth()
                        ) { fov ->
                            onSetCameraFov(fov)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Lentes:", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.72f))
                            listOf(
                                "24mm (84°)" to 84f,
                                "35mm (63°)" to 63f,
                                "50mm (46°)" to 46f,
                                "85mm (28°)" to 28f
                            ).forEach { (lensLabel, fovVal) ->
                                val isSel = kotlin.math.abs(evaluatedKf.fovDegrees - fovVal) < 2f
                                CamPill(lensLabel, isSel, CyanAccent, textColor) {
                                    onSetCameraFov(fovVal)
                                }
                            }
                        }

                        if (is3DMode) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CamSlider("Incl. X", "${evaluatedKf.pitchX.roundToInt()}°", evaluatedKf.pitchX, -40f..40f, OrangeAccent, textColor, Modifier.weight(1f)) { px ->
                                    onUpdateCurrentCamera { it.copy(pitchX = px) }
                                }
                                CamSlider("Órbita Y", "${evaluatedKf.yawY.roundToInt()}°", evaluatedKf.yawY, -40f..40f, OrangeAccent, textColor, Modifier.weight(1f)) { yy ->
                                    onUpdateCurrentCamera { it.copy(yawY = yy) }
                                }
                            }
                            CamSlider("Prof. Z", "${cameraSettings.layerDepthSpacing.roundToInt()}", cameraSettings.layerDepthSpacing, 0f..120f, NeonGreen, textColor, Modifier.fillMaxWidth()) { d ->
                                onSetLayerDepthSpacing(d)
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
                                Text("Res. Câmera:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                                listOf(1280 to 720, 1920 to 1080, 1080 to 1080, 1080 to 1920).forEach { (w, h) ->
                                    val isSel = cameraSettings.captureWidth == w && cameraSettings.captureHeight == h
                                    CamPill("${w}×${h}", isSel, accentColor, textColor) {
                                        onSetCameraResolution(w, h)
                                    }
                                }
                                OutlinedTextField(
                                    value = customResW,
                                    onValueChange = { customResW = it.filter { ch -> ch.isDigit() }.take(4) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(62.dp).height(42.dp)
                                )
                                Text("×", color = textColor, fontSize = 11.sp)
                                OutlinedTextField(
                                    value = customResH,
                                    onValueChange = { customResH = it.filter { ch -> ch.isDigit() }.take(4) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(62.dp).height(42.dp)
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

                when (activeTab) {
                    0 -> {
                        val channels = remember(is3DMode) {
                            buildList {
                                add("Resumo")
                                add("Loc X/Y")
                                add("Zoom")
                                add("Rot Z")
                                add("FOV")
                                if (is3DMode) {
                                    add("Incl/Órb")
                                }
                            }
                        }

                        // Transport & Keyframe Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (pairMin != null && pairMax != null) {
                                    "Par: Q${pairMin + 1}–Q${pairMax + 1}"
                                } else {
                                    "Q${currentFrameIndex + 1}/$totalFrames • ◆${sortedKeyFrames.size}"
                                },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pairMin != null && pairMax != null) OrangeAccent else textColor.copy(alpha = 0.85f)
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
                                    label = if (hasKeyAtCurrent) "Atualizar ◆" else "+ ◆ Q${currentFrameIndex + 1}",
                                    active = hasKeyAtCurrent,
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    icon = Icons.Default.Add
                                ) { onAddOrUpdateKeyframe(null) }

                                CamPill("◆ ▶", false, CyanAccent, textColor, testTag = "camera_next_key_btn") { onNextKeyframe() }

                                CamPill(
                                    label = "Conectar ◆━◆",
                                    active = cameraSettings.autoInterpolate && sortedKeyFrames.size >= 2,
                                    activeColor = NeonGreen,
                                    textColor = textColor,
                                    icon = Icons.Default.Link,
                                    testTag = "camera_connect_keyframes_btn"
                                ) { onConnectTwoKeyframes() }

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

                                if (hasKeyAtCurrent) {
                                    CamPill(
                                        label = "Remover ◆",
                                        active = false,
                                        activeColor = NeonPink,
                                        textColor = NeonPink,
                                        icon = Icons.Default.DeleteOutline
                                    ) { onDeleteKeyframeAt(currentFrameIndex) }
                                }

                                CamPill(
                                    label = if (isPairSelectMode) "Par: ON" else "Sel. 2 Pontos",
                                    active = isPairSelectMode,
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    testTag = "camera_pair_select_toggle"
                                ) {
                                    isPairSelectMode = !isPairSelectMode
                                    if (!isPairSelectMode && selectedPairB == null) {
                                        selectedPairA = null
                                    }
                                }
                                if (selectedPairA != null || selectedPairB != null) {
                                    CamPill("Limpar Par", false, NeonPink, NeonPink) {
                                        selectedPairA = null
                                        selectedPairB = null
                                    }
                                }
                            }
                        }

                        @Composable
                        fun DopeSheetBlock(modifier: Modifier = Modifier) {
                            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bgColor.copy(alpha = 0.65f))
                                        .border(0.8.dp, textColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                ) {
                                    // Left channel labels column
                                    Column(
                                        modifier = Modifier
                                            .width(74.dp)
                                            .background(panelColor.copy(alpha = 0.9f))
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Quadro",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                        channels.forEachIndexed { cIdx, chName ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(18.dp)
                                                    .padding(horizontal = 6.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = chName,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = if (cIdx == 0) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (cIdx == 0) OrangeAccent else textColor.copy(alpha = 0.84f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Right Dope Sheet frame columns
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 3.dp, horizontal = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val firstKf = sortedKeyFrames.firstOrNull()
                                        val lastKf = sortedKeyFrames.lastOrNull()
                                        for (fIdx in 0 until totalFrames.coerceAtLeast(1)) {
                                            val isCurrent = fIdx == currentFrameIndex
                                            val kf = cameraSettings.keyframes.firstOrNull { it.frameIndex == fIdx }
                                            val isPairEndpoint = fIdx == selectedPairA || fIdx == selectedPairB
                                            val inPairSpan = pairMin != null && pairMax != null && fIdx in pairMin..pairMax
                                            val inInterpolatedSpan = firstKf != null && lastKf != null && firstKf != lastKf && fIdx in firstKf..lastKf

                                            Column(
                                                modifier = Modifier
                                                    .width(28.dp)
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(
                                                        when {
                                                            isCurrent -> accentColor.copy(alpha = 0.28f)
                                                            inPairSpan -> OrangeAccent.copy(alpha = 0.20f)
                                                            kf != null -> OrangeAccent.copy(alpha = 0.12f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .border(
                                                        width = when {
                                                            isPairEndpoint -> 1.4.dp
                                                            isCurrent -> 1.3.dp
                                                            else -> 0.5.dp
                                                        },
                                                        color = when {
                                                            isPairEndpoint -> Color(0xFFFFD600)
                                                            isCurrent -> accentColor
                                                            else -> textColor.copy(alpha = 0.12f)
                                                        },
                                                        shape = RoundedCornerShape(5.dp)
                                                    )
                                                    .clickable {
                                                        onSelectFrame(fIdx)
                                                        if (isPairSelectMode) {
                                                            when {
                                                                selectedPairA == null || (selectedPairA != null && selectedPairB != null) -> {
                                                                    selectedPairA = fIdx
                                                                    selectedPairB = null
                                                                }
                                                                selectedPairA != fIdx -> {
                                                                    selectedPairB = fIdx
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .testTag("dope_frame_$fIdx"),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "${fIdx + 1}",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrent) accentColor else textColor.copy(alpha = 0.88f)
                                                )
                                                channels.forEachIndexed { chIdx, _ ->
                                                    val isDiamond = kf != null
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(18.dp)
                                                            .background(
                                                                when {
                                                                    chIdx == 0 && inInterpolatedSpan && cameraSettings.autoInterpolate ->
                                                                        OrangeAccent.copy(alpha = 0.24f)
                                                                    inPairSpan -> OrangeAccent.copy(alpha = 0.16f)
                                                                    else -> Color.Transparent
                                                                }
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (isDiamond) "◆" else "·",
                                                            fontSize = if (isDiamond) 10.sp else 9.sp,
                                                            color = when {
                                                                isPairEndpoint -> Color(0xFFFFD600)
                                                                isDiamond -> OrangeAccent
                                                                else -> textColor.copy(alpha = 0.28f)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Easing curve strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Interp:", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.72f))
                                    CameraEasingCurve.entries.forEach { curve ->
                                        val isSel = evaluatedKf.easing == curve
                                        CamPill(curve.labelPt, isSel, accentColor, textColor) {
                                            onAddOrUpdateKeyframe(curve)
                                        }
                                    }
                                }
                            }
                        }

                        if (isWidePc) {
                            // True PC Studio Layout: Dope Sheet on the Left + Live Camera Inspector on the Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                DopeSheetBlock(modifier = Modifier.weight(1.3f))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 185.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    CameraTransformControlsContent(compactForSplit = true)
                                }
                            }
                        } else {
                            // Clean Portrait Mobile Layout: Dedicated spacious Dope Sheet + quick link pill to Câmera tab
                            DopeSheetBlock(modifier = Modifier.fillMaxWidth())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CamPill(
                                    label = "Zoom ${(evaluatedKf.zoom * 100).roundToInt()}% • Giro ${evaluatedKf.rotationZ.roundToInt()}° • FOV ${evaluatedKf.fovDegrees.roundToInt()}° (Ajustar ▸)",
                                    active = true,
                                    activeColor = OrangeAccent,
                                    textColor = textColor,
                                    icon = Icons.Default.Tune
                                ) { activeTab = 1 }
                                CamPill("←", false, CyanAccent, textColor) { onNudgeCameraPan(-24f, 0f) }
                                CamPill("→", false, CyanAccent, textColor) { onNudgeCameraPan(24f, 0f) }
                                CamPill("Z-", false, accentColor, textColor) { onZoomCameraBy(0.9f) }
                                CamPill("Z+", false, accentColor, textColor) { onZoomCameraBy(1.1f) }
                            }
                        }
                    }

                    1 -> {
                        // Dedicated Spacious Camera Transform, Nudge, Lenses & Resolution Tab
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            CameraTransformControlsContent(compactForSplit = false)
                        }
                    }

                    2 -> {
                    // Interactive Graph Curve Editor
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 190.dp, max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.ShowChart, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Curva de Interpolação (Q${activeOrNearestKf.frameIndex + 1})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanAccent
                                )
                            }
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
                }

                3 -> {
                    // Built-in Camera Auto Effects (Range / Pinch) + Persistent "Pasta Animada"
                    val targetStart = if (effectApplyToPair && pairMin != null && pairMax != null) pairMin else currentFrameIndex
                    val targetEnd = if (effectApplyToPair && pairMin != null && pairMax != null) pairMax else (currentFrameIndex + pinchEffectDuration - 1).coerceAtMost((totalFrames - 1).coerceAtLeast(currentFrameIndex))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 178.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Range Targeting Row (Entre 2 Pontos vs Pinça N quadros)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CamPill(
                                label = "Na Pinça (Q${currentFrameIndex + 1}..${(currentFrameIndex + pinchEffectDuration).coerceAtMost(totalFrames.coerceAtLeast(1))})",
                                active = !effectApplyToPair,
                                activeColor = secondaryColor,
                                textColor = textColor,
                                testTag = "effect_target_pinch_btn"
                            ) { effectApplyToPair = false }

                            CamPill(
                                label = if (pairMin != null && pairMax != null) "Entre Pontos (Q${pairMin + 1}..${pairMax + 1})" else "Entre 2 Pontos",
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

                        // Active effect spans applied to timeline
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

                        // Pasta Animada (Save & Reuse)
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
                }

                4 -> {
                    // Cena Post-Effects
                    val fx = cameraSettings.postEffects
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 185.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = NeonPink, modifier = Modifier.size(14.dp))
                                Text("Pós-Efeitos da Cena", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonPink)
                            }
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

                else -> {
                    // Tab 4: Fundo do Canvas & Céu 3D (afeta Palco 3D, Fundo do Canvas 2D quando Transparente/Configurado e Exportação)
                    val bgSwatches = listOf(
                        0xFFFFFFFFL,
                        0xFFFFF8E8L,
                        0xFFE5EAF5L,
                        0xFF9EA7B8L,
                        0xFF28314AL,
                        0xFF151926L,
                        0xFF0A0C12L,
                        0xFF00C853L,
                        0xFF00A2FFL,
                        0xFFFF6D00L
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 215.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Fundo da Folha (2D/3D) & Céu Espacial",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }

                        // Estilo do Fundo da Folha
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

                        if (canvasBackground.style != CanvasBackgroundStyle.TRANSPARENTE) {
                            BgColorSwatchRow(
                                label = "Cor 1:",
                                swatches = bgSwatches,
                                selectedColor = canvasBackground.primaryColor,
                                accentColor = NeonGreen,
                                textColor = textColor,
                                onSelect = { c ->
                                    onUpdateCanvasBackground(canvasBackground.copy(primaryColor = c))
                                }
                            )
                        }

                        if (canvasBackground.style == CanvasBackgroundStyle.DEGRADE || canvasBackground.style == CanvasBackgroundStyle.TEXTURA) {
                            BgColorSwatchRow(
                                label = "Cor 2:",
                                swatches = bgSwatches,
                                selectedColor = canvasBackground.secondaryColor,
                                accentColor = CyanAccent,
                                textColor = textColor,
                                onSelect = { c ->
                                    onUpdateCanvasBackground(canvasBackground.copy(secondaryColor = c))
                                }
                            )
                        }

                        if (canvasBackground.style == CanvasBackgroundStyle.DEGRADE) {
                            CamSlider(
                                label = "Ângulo",
                                valueText = "${canvasBackground.gradientAngleDeg.roundToInt()}°",
                                value = canvasBackground.gradientAngleDeg,
                                range = 0f..360f,
                                accentColor = CyanAccent,
                                textColor = textColor,
                                modifier = Modifier.fillMaxWidth()
                            ) { ang ->
                                onUpdateCanvasBackground(canvasBackground.copy(gradientAngleDeg = ang))
                            }
                        }

                        // Textura de Fundo
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
                                    } else {
                                        canvasBackground.style
                                    }
                                    onUpdateCanvasBackground(canvasBackground.copy(texturePattern = pat, style = nextStyle))
                                }
                            }
                        }
                        if (canvasBackground.texturePattern != CanvasTexturePattern.NENHUMA) {
                            CamSlider(
                                label = "Força Tex",
                                valueText = "${(canvasBackground.textureIntensity * 100).roundToInt()}%",
                                value = canvasBackground.textureIntensity,
                                range = 0.05f..0.85f,
                                accentColor = OrangeAccent,
                                textColor = textColor,
                                modifier = Modifier.fillMaxWidth()
                            ) { inten ->
                                onUpdateCanvasBackground(canvasBackground.copy(textureIntensity = inten))
                            }
                        }

                        // Céu do Estúdio 3D (e exibido no modo 2D quando papel estiver Transparente)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Céu 3D/2D:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.78f))
                            SkyBackdropMode.entries.forEach { sky ->
                                val isSel = canvasBackground.skyMode == sky
                                CamPill(sky.labelPt, isSel, secondaryColor, textColor, testTag = "cam_sky_mode_${sky.name}") {
                                    onUpdateCanvasBackground(canvasBackground.copy(skyMode = sky))
                                }
                            }
                        }

                        BgColorSwatchRow(
                            label = "Céu Topo:",
                            swatches = bgSwatches,
                            selectedColor = canvasBackground.skyTopColor,
                            accentColor = secondaryColor,
                            textColor = textColor,
                            onSelect = { c ->
                                onUpdateCanvasBackground(canvasBackground.copy(skyTopColor = c))
                            }
                        )
                        BgColorSwatchRow(
                            label = "Céu Base:",
                            swatches = bgSwatches,
                            selectedColor = canvasBackground.skyBottomColor,
                            accentColor = secondaryColor,
                            textColor = textColor,
                            onSelect = { c ->
                                onUpdateCanvasBackground(canvasBackground.copy(skyBottomColor = c))
                            }
                        )
                    }
                }
            }
        }
    }
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
    onHandlesChanged: (Float, Float, Float, Float) -> Unit
) {
    var localP1x by remember(p1x) { mutableFloatStateOf(p1x) }
    var localP1y by remember(p1y) { mutableFloatStateOf(p1y) }
    var localP2x by remember(p2x) { mutableFloatStateOf(p2x) }
    var localP2y by remember(p2y) { mutableFloatStateOf(p2y) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(142.dp)
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

        // Subtle grid lines
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

        // Linear diagonal guide
        drawLine(textColor.copy(alpha = 0.22f), p0, p3, strokeWidth = 1.2f)

        // Handle tangent stems
        drawLine(CyanAccent.copy(alpha = 0.65f), p0, p1, strokeWidth = 1.8f)
        drawLine(NeonPink.copy(alpha = 0.65f), p3, p2, strokeWidth = 1.8f)

        // Cubic Bezier curve
        val curvePath = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }
        drawPath(curvePath, color = OrangeAccent, style = Stroke(width = 3.2f))

        // Endpoints
        drawCircle(textColor.copy(alpha = 0.8f), radius = 4f, center = p0)
        drawCircle(textColor.copy(alpha = 0.8f), radius = 4f, center = p3)

        // Draggable P1 and P2 handles
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
        Text(label, fontSize = 9.sp, color = textColor.copy(alpha = 0.8f), modifier = Modifier.width(48.dp))
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
