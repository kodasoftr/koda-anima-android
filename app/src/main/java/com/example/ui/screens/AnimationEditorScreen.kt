package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.AppUiMode
import com.example.model.BrushPreset
import com.example.model.CameraMode
import com.example.model.MultiplayerRole
import com.example.model.StageSelection
import com.example.model.StageTargetType
import com.example.model.ToolType
import com.example.ui.components.BrushStudioDrawer
import com.example.ui.components.BucketConfigPopover
import com.example.ui.components.CameraSideToolbar
import com.example.ui.components.CameraTimelinePanel
import com.example.ui.components.CanvasView
import com.example.ui.components.CollaboratorHorizontalStrip
import com.example.ui.components.ColorPaletteTrayModal
import com.example.ui.components.ColorPickerDialog
import com.example.ui.components.EraserVectorIcon
import com.example.ui.components.ExportDialog
import com.example.ui.components.ExtraToolsFloatingMenu
import com.example.ui.components.FloatingReferenceControlsBar
import com.example.ui.components.FrameLayersFloatingPanel
import com.example.ui.components.FriendBrushOfferBanner
import com.example.ui.components.InlineNumericPxInput
import com.example.ui.components.KritaBrushStrokeWaveformPreview
import com.example.ui.components.LayersBottomSheet
import com.example.ui.components.ProcreateBrushPickerSheet
import com.example.ui.components.ProjectSettingsDialog
import com.example.ui.components.ProjectsListDialog
import com.example.ui.components.QuickCopyPasteGestureMenu
import com.example.ui.components.QuickPaletteFloatingWindow
import com.example.ui.components.ReferenceImageChoiceDialog
import com.example.ui.components.Studio3DSplitNodesOverlay
import com.example.ui.components.Studio3DThreeWheelsSheet
import com.example.ui.components.ThreeWheelsIcon
import com.example.ui.components.TimelineBar
import com.example.ui.components.TimelineLayerImageTransformHud
import com.example.ui.components.ToolboxPalette
import com.example.ui.components.UvUnfoldWindowOverlay
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import com.example.viewmodel.EditorDialog
import com.example.viewmodel.KodanimaViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AnimationEditorScreen(
    viewModel: KodanimaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTheme = uiState.preferences.theme

    val bgColor = Color(currentTheme.bg)
    val panelColor = Color(currentTheme.panel)
    val accentColor = Color(currentTheme.accent)
    val textColor = Color(currentTheme.text)
    val borderCol = accentColor.copy(alpha = 0.35f)

    var showProcreateBrushes by remember { mutableStateOf(false) }
    var showExtraToolsMenu by remember { mutableStateOf(false) }
    val mobileFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { mobileFocusRequester.requestFocus() }
    }

    BackHandler {
        if (uiState.multiplayerRole == MultiplayerRole.CLIENT) {
            viewModel.leaveRemoteSessionAsVisitor(context, AppScreen.HOME)
        } else {
            viewModel.navigateTo(AppScreen.HOME)
        }
    }

    val kritaBrushPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importKritaBrushesFromUri(context, uri)
            Toast.makeText(context, "Importando brush do Krita...", Toast.LENGTH_SHORT).show()
        }
    }

    val scene3DPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.import3DSceneFromUri(context, uri)
        }
    }

    var pendingTexObjId by remember { mutableStateOf<String?>(null) }
    var pendingTexSlotIdx by remember { mutableIntStateOf(0) }
    val studio3DTexturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val objId = pendingTexObjId
        if (uri != null && objId != null) {
            viewModel.applySceneObjectSlotTextureUri(context, objId, pendingTexSlotIdx, uri)
        }
    }

    // File picker for .kodanima project files
    val projectFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val isKoda2d = uri.toString().endsWith(".koda2d", ignoreCase = true) ||
                    (context.contentResolver.getType(uri)?.contains("zip") == true)
                if (isKoda2d) {
                    viewModel.importKoda2dFromUri(context, uri)
                    Toast.makeText(context, "Pacote .koda2d importado com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    val jsonText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                    } ?: ""
                    if (jsonText.isBlank()) throw IllegalArgumentException("Arquivo vazio")
                    viewModel.importProjectFromJson(jsonText)
                    Toast.makeText(context, "Projeto carregado com sucesso!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                try {
                    viewModel.importKoda2dFromUri(context, uri)
                    Toast.makeText(context, "Pacote .koda2d importado com sucesso!", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Erro ao carregar arquivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Media picker for reference images
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setReferenceImageUri(uri.toString())
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importAudioFile(context, uri)
            Toast.makeText(context, "Áudio importado com sucesso!", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importVideoFile(context, uri)
            Toast.makeText(context, "Vídeo de referência importado!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .focusRequester(mobileFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    viewModel.handlePhysicalKeyEvent(keyEvent.nativeKeyEvent, context)
                } else {
                    false
                }
            },
        containerColor = bgColor,
        bottomBar = {
            if (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideTimeline) {
                if (uiState.isCameraTimelineOpen) {
                    CameraTimelinePanel(
                        cameraSettings = uiState.project.camera3D,
                        currentFrameIndex = uiState.currentFrameIndex,
                        totalFrames = uiState.project.frames.size,
                        isPlaying = uiState.isPlaying,
                        isSideView3D = uiState.isCameraSideView3D,
                        savedTemplates = uiState.preferences.savedCameraTemplates,
                        theme = currentTheme,
                        onSelectFrame = { viewModel.goToFrame(it) },
                        onTogglePlay = { viewModel.togglePlayback() },
                        onToggleCameraEnabled = { viewModel.toggleCameraEnabled() },
                        onToggleSideView3D = { viewModel.toggleCameraSideView3D() },
                        onToggleAutoInterpolate = { viewModel.toggleCameraAutoInterpolate() },
                        onToggleCaptureFrameOverlay = { viewModel.toggleCameraShowCaptureFrame() },
                        onUpdateCurrentCamera = { viewModel.updateCameraAtCurrentFrame(it) },
                        onAddOrUpdateKeyframe = { viewModel.addOrUpdateCameraKeyframeAtCurrentFrame(it) },
                        onDeleteKeyframeAt = { viewModel.deleteCameraKeyframeAt(it) },
                        onResetKeyframes = { viewModel.resetCameraKeyframesToDefault() },
                        onSetCameraResolution = { w, h -> viewModel.setCameraResolution(w, h) },
                        onSetLayerDepthSpacing = { viewModel.setCameraLayerDepthSpacing(it) },
                        onApplyBuiltInEffect = { viewModel.applyCameraBuiltInEffect(it) },
                        onSaveTemplateToFolder = { viewModel.saveCameraTemplateToFolder(it) },
                        onApplyTemplateFromFolder = { viewModel.applyCameraTemplateFromFolder(it) },
                        onDeleteTemplate = { viewModel.deleteCameraTemplate(it) },
                        onUpdatePostEffects = { viewModel.updateCameraPostEffects(it) },
                        onResetPostEffects = { viewModel.resetCameraPostEffects() },
                        onCloseCameraTimeline = { viewModel.closeCameraTimeline() },
                        onSetCameraMode = { viewModel.setCameraMode(it) },
                        onUpdateBezierHandles = { fIdx, p1x, p1y, p2x, p2y ->
                            viewModel.updateKeyframeBezierHandles(fIdx, p1x, p1y, p2x, p2y)
                        },
                        onApplyEffectInRange = { eff, sf, ef, inten ->
                            viewModel.applyCameraEffectInRange(eff, sf, ef, inten)
                        },
                        onRemoveEffectSpan = { viewModel.removeCameraEffectSpan(it) },
                        canvasBackground = uiState.project.canvasBackground,
                        onUpdateCanvasBackground = { viewModel.updateCanvasBackground(it) },
                        onConnectTwoKeyframes = { viewModel.connectTwoCameraKeyframes() },
                        onPrevKeyframe = { viewModel.jumpToPrevCameraKeyframe() },
                        onNextKeyframe = { viewModel.jumpToNextCameraKeyframe() },
                        onResetCurrentKeyframe = { viewModel.resetCameraAtCurrentFrame() },
                        onSetCameraFov = { viewModel.setCameraFov(it) },
                        onNudgeCameraPan = { dx, dy -> viewModel.nudgeCameraPan(dx, dy) },
                        onZoomCameraBy = { viewModel.zoomCameraBy(it) },
                        onRotateCameraBy = { viewModel.rotateCameraBy(it) },
                        onTiltOrbitCameraBy = { dYaw, dPitch -> viewModel.tiltOrbitCameraBy(dYaw, dPitch) },
                        onAddFrame = { viewModel.addFrame() },
                        layers = uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers ?: emptyList(),
                        stageSelection = uiState.stageSelection,
                        onSelectStageTarget = { viewModel.selectStageTarget(it) },
                        onFocusStageTarget = { viewModel.focusSelectedStageTarget() },
                        onMoveKeyframe = { fromFrame, toFrame -> viewModel.moveSelectedTargetKeyframe(fromFrame, toFrame) },
                        onUpdateSelectedObjectTransform = { viewModel.updateSelectedObjectTransform(it) },
                        onAddOrUpdateSelectedKeyframe = { viewModel.addOrUpdateSelectedTargetKeyframe(it) },
                        onDeleteSelectedKeyframeAt = { viewModel.deleteSelectedTargetKeyframe(it) },
                        onResetSelectedTarget = { viewModel.resetSelectedObjectTransformAtCurrentFrame() },
                        onImport3DSceneClick = { scene3DPicker.launch(arrayOf("*/*")) },
                        onAddBuiltIn3DPrimitive = { viewModel.addBuiltIn3DPrimitive(it) },
                        onToggle3DObjectVisibility = { viewModel.toggleSceneObject3DVisibility(it) },
                        onDelete3DObject = { viewModel.deleteSceneObject3D(it) },
                        onSet3DObjectTint = { id, tint -> viewModel.setSceneObject3DTint(id, tint) },
                        projectFrames = uiState.project.frames,
                        fps = uiState.project.fps,
                        activeLayerIndex = uiState.activeLayerIndex,
                        currentTimelineTick = uiState.currentTimelineTick,
                        activeScrubTimeMs = uiState.activeScrubTimeMs,
                        playbackTimeMs = uiState.playbackTimeMs,
                        onSelectTick = { viewModel.goToTimelineTick(it) },
                        onScrubToMs = { viewModel.scrubToTimelineMs(it) },
                        onEndScrub = { viewModel.endAudioScrub() },
                        onSetLayerCelHold = { lIdx, fIdx, h -> viewModel.setLayerCelDuration(lIdx, fIdx, h) },
                        onMoveLayerCel = { lIdx, fIdx, tick -> viewModel.moveLayerCelToTick(lIdx, fIdx, tick) },
                        onAddCelAtTick = { lIdx, tick -> viewModel.activateOrAddCelOnLayerAtTick(lIdx, tick) },
                        onSetKeyframeHold = { tickIdx, hold -> viewModel.setSelectedTargetKeyframeHold(tickIdx, hold) },
                        onApplyTweenBetween = { tickA, tickB, curve -> viewModel.applyTweenBetweenTwoPoints(tickA, tickB, curve) },
                        onAddKeyframeAtTick = { tick -> viewModel.addSelectedTargetKeyframeAtTick(tick) }
                    )
                } else {
                    TimelineBar(
                        frames = uiState.project.frames,
                        currentFrameIndex = uiState.currentFrameIndex,
                        isPlaying = uiState.isPlaying,
                        onionSkinEnabled = uiState.onionSkinEnabled,
                        layerCount = uiState.project.frames[uiState.currentFrameIndex].layers.size,
                        fps = uiState.project.fps,
                        onSelectFrame = { viewModel.goToFrame(it) },
                        onTogglePlay = { viewModel.togglePlayback() },
                        onNextFrame = { viewModel.nextFrame() },
                        onPrevFrame = { viewModel.prevFrame() },
                        onAddFrame = { viewModel.addFrame() },
                        onDuplicateFrame = { viewModel.duplicateCurrentFrame() },
                        onDeleteFrame = { viewModel.deleteCurrentFrame() },
                        onToggleOnionSkin = { viewModel.toggleOnionSkin() },
                        onOpenLayers = { viewModel.openDialog(EditorDialog.LAYERS) },
                        onOpenSettings = { viewModel.openDialog(EditorDialog.PROJECT_SETTINGS) },
                        theme = currentTheme,
                        mediaAssets = uiState.project.mediaAssets,
                        activeScrubTimeMs = uiState.activeScrubTimeMs,
                        onSetFrameHold = { idx, hold -> viewModel.setFrameHoldDuration(idx, hold) },
                        onScrubToMs = { viewModel.scrubToTimelineMs(it) },
                        onEndScrub = { viewModel.endAudioScrub() },
                        onionSkinOpacity = uiState.project.onionSkin.opacity,
                        onChangeOnionSkinOpacity = { viewModel.setOnionSkinOpacity(it) },
                        layers = uiState.project.frames.firstOrNull()?.layers ?: emptyList(),
                        activeLayerIndex = uiState.activeLayerIndex,
                        currentTimelineTick = uiState.currentTimelineTick,
                        playbackTimeMs = uiState.playbackTimeMs,
                        onSelectLayerCel = { lIdx, fIdx, tick -> viewModel.selectLayerCel(lIdx, fIdx, tick) },
                        onSetLayerCelHold = { lIdx, fIdx, hold -> viewModel.setLayerCelDuration(lIdx, fIdx, hold) },
                        onMoveLayerCel = { lIdx, fIdx, tick -> viewModel.moveLayerCelToTick(lIdx, fIdx, tick) },
                        onAddCelAtTick = { lIdx, tick -> viewModel.activateOrAddCelOnLayerAtTick(lIdx, tick) },
                        onSelectTick = { viewModel.goToTimelineTick(it) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val activeBrushParamsJson = remember(uiState.activeBrushPreset) {
            uiState.activeBrushPreset.dynamicParams.toCompactJson()
        }
        val isFloatingRefBelow = uiState.referenceImage.isVisible && !uiState.referenceImage.renderAboveCanvas

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // Main Hardware-Accelerated Animation Canvas (Hardware Accel, Stylus & Virtual Cursor)
            CanvasView(
                project = uiState.project,
                currentFrameIndex = uiState.currentFrameIndex,
                selectedTool = uiState.selectedTool,
                currentColor = uiState.currentColor,
                brushSize = uiState.brushSize,
                brushOpacity = uiState.brushOpacity,
                brushPresetId = uiState.activeBrushPreset.id,
                activeLayerIndex = uiState.activeLayerIndex,
                activeLocalLayerIndex = uiState.activeLocalLayerIndex,
                activeTargetIsFrameLocalLayer = uiState.activeTargetIsFrameLocalLayer,
                onionSkinEnabled = uiState.onionSkinEnabled,
                isStylusOnlyMode = uiState.isStylusOnlyMode,
                zoom = uiState.zoom,
                panX = uiState.panX,
                panY = uiState.panY,
                rotation = uiState.rotation,
                activeSelection = uiState.activeSelection,
                tabletSettings = uiState.preferences.tabletSettings,
                themeAccentColor = currentTheme.accent,
                onFocusActive = {
                    runCatching { mobileFocusRequester.requestFocus() }
                },
                onTransformChange = { z, px, py, r ->
                    viewModel.updateTransform(z, px, py, r)
                },
                onStrokeCompleted = { stroke ->
                    viewModel.addStrokeToCurrentLayer(stroke)
                },
                onEyedropperColorPicked = { pickedColor ->
                    viewModel.completeEyedropperColorPick(pickedColor)
                    Toast.makeText(context, "Cor capturada!", Toast.LENGTH_SHORT).show()
                },
                onBucketFill = { x, y ->
                    viewModel.performBucketFill(x, y)
                },
                onMagicWand = { x, y ->
                    viewModel.performMagicWand(x, y)
                },
                onLassoCompleted = { lassoPoints ->
                    viewModel.setLassoSelection(lassoPoints)
                },
                onSelectionOffsetChange = { dx, dy ->
                    viewModel.updateSelectionOffset(dx, dy)
                },
                remoteCursors = uiState.remoteCursors,
                pendingKnockRequest = uiState.pendingKnockRequest,
                onRespondToKnock = { viewModel.respondToKnock(it) },
                onLocalCursorMoved = { x, y, isDrawing ->
                    viewModel.broadcastLocalCursor(x, y, isDrawing)
                },
                connectedFriend = uiState.connectedFriend,
                connectedFriends = uiState.connectedFriends,
                remoteLiveStrokes = uiState.remoteLiveStrokes,
                onLiveStrokePreview = { viewModel.broadcastLocalLiveStroke(it) },
                currentPingMs = uiState.currentPingMs,
                lowPowerMode = uiState.preferences.lowPowerMode,
                enableChunkCulling = uiState.preferences.enableChunkCulling,
                eraserUsesPressure = uiState.preferences.eraserUsesPressure,
                eraserBrushPresetId = uiState.preferences.eraserBrushPresetId,
                activeBrushParamsJson = activeBrushParamsJson,
                mirrorHorizontal = uiState.mirrorCanvasHorizontal,
                activeScrubTimeMs = uiState.activeScrubTimeMs,
                isVideoTransformMode = uiState.isVideoTransformMode,
                onVideoTransformChange = { dx, dy, scaleFactor, rotDelta ->
                    val vAsset = uiState.project.mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }
                    if (vAsset != null) {
                        viewModel.updateVideoAssetTransform(vAsset.id, dx, dy, scaleFactor, rotDelta)
                    }
                },
                bezierNodes = uiState.bezierNodes,
                isBezierClosed = uiState.isBezierClosed,
                showFloatingBezierBar = true,
                onBezierNodesChange = { viewModel.setBezierNodes(it) },
                onToggleBezierClosed = { viewModel.toggleBezierClosed() },
                onUndoBezierNode = { viewModel.undoLastBezierNode() },
                onClearBezierNodes = { viewModel.clearBezierNodes() },
                onConfirmBezierCurve = { viewModel.confirmBezierCurve() },
                gridRulerSettings = uiState.gridRulerSettings,
                perspectiveRulerSettings = uiState.perspectiveRulerSettings,
                onPerspectiveVanishingPointDrag = { vpIdx, rx, ry ->
                    viewModel.updatePerspectiveVanishingPoint(vpIdx, rx, ry)
                },
                onMultiFingerGesture = { gesture ->
                    viewModel.executeMultiFingerGesture(gesture)
                },
                isFloatingRefBelowCanvas = isFloatingRefBelow,
                eraserSize = uiState.eraserSize,
                referenceImage = uiState.referenceImage,
                onFloatingRefTransformDelta = { dx, dy, zoomDelta, rotDelta ->
                    viewModel.updateFloatingReferenceOffset(dx, dy)
                    if (abs(zoomDelta - 1f) > 0.002f) {
                        viewModel.updateFloatingReferenceScale(uiState.referenceImage.scale * zoomDelta)
                    }
                    if (abs(rotDelta) > 0.1f) {
                        viewModel.rotateFloatingReference(rotDelta)
                    }
                },
                isCameraSideView3D = uiState.isCameraSideView3D,
                isCameraModeActive = uiState.isCameraTimelineOpen,
                cameraToolAction = uiState.cameraToolAction,
                onCameraTransformDelta = { dx, dy, zoomFactor, rotDelta, fovDelta, dYaw, dPitch ->
                    if (abs(dx) > 0.01f || abs(dy) > 0.01f) viewModel.nudgeCameraPan(dx, dy)
                    if (abs(zoomFactor - 1f) > 0.001f) viewModel.zoomCameraBy(zoomFactor)
                    if (abs(rotDelta) > 0.01f) viewModel.rotateCameraBy(rotDelta)
                    if (abs(fovDelta) > 0.01f) viewModel.adjustCameraFov(fovDelta)
                    if (abs(dYaw) > 0.01f || abs(dPitch) > 0.01f) viewModel.tiltOrbitCameraBy(dYaw, dPitch)
                },
                cameraViewportNavTarget = uiState.cameraViewportNavTarget,
                stageSelection = uiState.stageSelection,
                stageFocusNonce = uiState.stageFocusRequestNonce,
                onStageTargetSelected = { viewModel.selectStageTarget(it) },
                onSelectedStageObjectDelta = { dx, dy, dz, rx, ry, rz, sf ->
                    viewModel.applySelectedStageObjectDelta(dx, dy, dz, rx, ry, rz, sf)
                },
                studio3DPaintEnabled = uiState.studio3DPaintEnabled && uiState.isCameraTimelineOpen,
                onPaintSceneObjectUV = { objId, u0, v0, u1, v1 ->
                    viewModel.paintSceneObjectSurfaceSegment(objId, u0, v0, u1, v1)
                },
                currentTimelineTick = uiState.currentTimelineTick,
                playbackTimeMs = uiState.playbackTimeMs,
                isPlaying = uiState.isPlaying,
                theme = currentTheme
            )

            // Unobstructed Top HUD Stack (Collaborators + Procreate Brush Trigger — zero top bar clutter)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 58.dp, end = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Horizontal Compact Collaborators Strip (Never covered by toolbars, hidden during Zen Mode)
                if (!uiState.isZenModeActive) {
                    CollaboratorHorizontalStrip(
                        connectedFriends = uiState.connectedFriends,
                        showHud = uiState.preferences.showCanvasCollaboratorsHud,
                        fallbackPingMs = uiState.currentPingMs,
                        theme = currentTheme
                    )
                }

                if (!uiState.isCameraTimelineOpen && (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideBrushBar)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Procreate-Style Active Krita Brush Pill (exact same compact size, clears status bar)
                        Surface(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(999.dp))
                                .clip(RoundedCornerShape(999.dp))
                                .border(
                                    1.dp,
                                    if (showProcreateBrushes) accentColor else borderCol,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { showProcreateBrushes = !showProcreateBrushes }
                                .testTag("mobile_procreate_brush_trigger"),
                            color = panelColor.copy(alpha = 0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KritaBrushStrokeWaveformPreview(
                                    preset = uiState.activeBrushPreset,
                                    strokeColor = accentColor,
                                    modifier = Modifier
                                        .width(34.dp)
                                        .height(14.dp)
                                )
                                Text(
                                    text = "${uiState.activeBrushPreset.shortLabel} • ${(if (uiState.selectedTool == ToolType.ERASER) uiState.eraserSize else uiState.brushSize).toInt()}px",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1
                                )
                                ThreeWheelsIcon(
                                    tint = textColor,
                                    accentColor = accentColor,
                                    size = 15.dp
                                )
                            }
                        }

                        // Subtle optional Rotation Reset chip (replaces rotated angle badge moved out of removed top bar)
                        if (!uiState.isZenModeActive && abs(uiState.rotation) > 0.5f) {
                            val rotDeg = uiState.rotation.roundToInt()
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, OrangeAccent, RoundedCornerShape(999.dp))
                                    .clickable {
                                        viewModel.resetRotation()
                                        Toast.makeText(context, "Giro zerado (0°)", Toast.LENGTH_SHORT).show()
                                    },
                                color = panelColor.copy(alpha = 0.92f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.RestartAlt,
                                        contentDescription = "Zerar giro",
                                        tint = OrangeAccent,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${rotDeg}°",
                                        color = OrangeAccent,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (!uiState.isZenModeActive) {
                    FriendBrushOfferBanner(
                        offer = uiState.pendingFriendBrushOffer,
                        theme = currentTheme,
                        onAccept = {
                            viewModel.acceptFriendBrushOffer(context)
                            Toast.makeText(context, "Brush baixado para seus Importados!", Toast.LENGTH_SHORT).show()
                        },
                        onDecline = { viewModel.declineFriendBrushOffer() }
                    )
                }

                // Floating Mobile Eraser Options Toolbar (when ToolType.ERASER is active)
                if (!uiState.isCameraTimelineOpen && uiState.selectedTool == ToolType.ERASER && !uiState.isZenModeActive) {
                    Surface(
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, NeonPink.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                            .testTag("mobile_eraser_options_bar"),
                        color = panelColor.copy(alpha = 0.96f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EraserVectorIcon(
                                tint = NeonPink,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Borracha",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPink
                            )

                            BrushPreset.ALL_12_PRESETS.take(4).forEach { preset ->
                                val isChosen = uiState.preferences.eraserBrushPresetId == preset.id
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { viewModel.setEraserBrushPresetId(preset.id) }
                                        .border(
                                            width = if (isChosen) 1.dp else 0.5.dp,
                                            color = if (isChosen) NeonPink else textColor.copy(alpha = 0.24f),
                                            shape = RoundedCornerShape(4.dp)
                                        ),
                                    color = if (isChosen) NeonPink.copy(alpha = 0.25f) else Color.Transparent
                                ) {
                                    Text(
                                        text = preset.shortLabel,
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) NeonPink else textColor.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            InlineNumericPxInput(
                                valuePx = uiState.eraserSize,
                                minPx = 2f,
                                maxPx = 120f,
                                accentColor = NeonPink,
                                textColor = textColor,
                                panelBgColor = panelColor,
                                testTag = "mobile_eraser_px_input",
                                onCommitValue = { viewModel.setEraserSize(it) }
                            )
                            Slider(
                                value = uiState.eraserSize,
                                onValueChange = { viewModel.setEraserSize(it) },
                                valueRange = 2f..80f,
                                modifier = Modifier
                                    .width(55.dp)
                                    .height(18.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonPink,
                                    activeTrackColor = NeonPink
                                )
                            )

                            val hasPressure = uiState.preferences.eraserUsesPressure
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.setEraserUsesPressure(!hasPressure) }
                                    .border(
                                        1.dp,
                                        if (hasPressure) NeonPink else textColor.copy(alpha = 0.24f),
                                        RoundedCornerShape(4.dp)
                                    ),
                                color = if (hasPressure) NeonPink.copy(alpha = 0.25f) else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Gesture,
                                        contentDescription = null,
                                        tint = if (hasPressure) NeonPink else textColor.copy(alpha = 0.4f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = if (hasPressure) "Pressão" else "Fixo",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasPressure) NeonPink else textColor.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Config. Balde chip when Bucket tool is selected
                if (!uiState.isCameraTimelineOpen && uiState.selectedTool == ToolType.BUCKET && !uiState.isZenModeActive) {
                    Surface(
                        modifier = Modifier
                            .shadow(6.dp, RoundedCornerShape(999.dp))
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, accentColor, RoundedCornerShape(999.dp))
                            .clickable { viewModel.toggleBucketConfig() }
                            .testTag("mobile_bucket_config_chip"),
                        color = panelColor.copy(alpha = 0.96f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.FormatPaint, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                            Text(
                                text = "Config. Balde (+${uiState.bucketConfig.expandPixels}px)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }
                }

                // Floating FlipaClip-style Selection Actions Toolbar
                if (!uiState.isCameraTimelineOpen && uiState.activeSelection.isActive && !uiState.isZenModeActive) {
                    Surface(
                        modifier = Modifier
                            .shadow(10.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, accentColor, RoundedCornerShape(20.dp))
                            .testTag("mobile_flipaclip_selection_toolbar"),
                        color = panelColor.copy(alpha = 0.96f)
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.invertSelection() }
                                    .border(
                                        1.dp,
                                        if (uiState.activeSelection.isInverted) OrangeAccent else textColor.copy(alpha = 0.25f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("mobile_invert_selection_btn"),
                                color = if (uiState.activeSelection.isInverted) OrangeAccent.copy(alpha = 0.25f) else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CompareArrows,
                                        contentDescription = "Inverter Seleção",
                                        tint = if (uiState.activeSelection.isInverted) OrangeAccent else textColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Inverter",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.activeSelection.isInverted) OrangeAccent else textColor
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.flipSelectionH() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Espelhar H", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.flipSelectionV() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Espelhar V", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.rotateSelection(-15f) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.RotateLeft, contentDescription = "Girar -15°", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.rotateSelection(15f) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Girar +15°", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.scaleSelection(0.85f) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Reduzir Escala", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.scaleSelection(1.15f) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Aumentar Escala", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                viewModel.copySelection()
                                Toast.makeText(context, "Seleção copiada!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                viewModel.pasteSelection()
                                Toast.makeText(context, "Colado!", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Colar", tint = textColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.fillSelectionWithCurrentColor() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.FormatPaint, contentDescription = "Preencher", tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteSelection() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = NeonPink, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.applySelectionTransform() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Concluir", tint = NeonGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Floating Mobile Video Transform HUD (when Transformar Vídeo mode is active)
                val activeVideoAsset = uiState.project.mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }
                if (!uiState.isCameraTimelineOpen && uiState.isVideoTransformMode && activeVideoAsset != null && !uiState.isZenModeActive) {
                    Surface(
                        modifier = Modifier
                            .shadow(8.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, OrangeAccent, RoundedCornerShape(14.dp))
                            .testTag("mobile_video_transform_hud"),
                        color = panelColor.copy(alpha = 0.96f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.OpenWith, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(14.dp))
                            IconButton(onClick = { viewModel.scaleVideoAsset(activeVideoAsset.id, 0.9) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Reduzir", tint = textColor, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = "${activeVideoAsset.scalePercent.toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            IconButton(onClick = { viewModel.scaleVideoAsset(activeVideoAsset.id, 1.1) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Ampliar", tint = textColor, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = { viewModel.rotateVideoAsset(activeVideoAsset.id, -5.0) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.RotateLeft, contentDescription = "Girar -5°", tint = textColor, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = "${activeVideoAsset.rotationDegrees.roundToInt()}°",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            IconButton(onClick = { viewModel.rotateVideoAsset(activeVideoAsset.id, 5.0) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Girar +5°", tint = textColor, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = { viewModel.resetVideoTransform(activeVideoAsset.id) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Resetar", tint = textColor, modifier = Modifier.size(15.dp))
                            }
                            IconButton(onClick = { viewModel.setVideoTransformMode(false) }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Concluir", tint = NeonGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                val activeLayerImageTarget = if (!uiState.activeTargetIsFrameLocalLayer) {
                    uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers?.getOrNull(uiState.activeLayerIndex)
                } else null
                val activeLayerImgAttachment = activeLayerImageTarget?.imageAttachment
                if (!uiState.isCameraTimelineOpen && activeLayerImageTarget != null && activeLayerImgAttachment != null && !uiState.isZenModeActive) {
                    TimelineLayerImageTransformHud(
                        layerName = activeLayerImageTarget.name,
                        attachment = activeLayerImgAttachment,
                        theme = currentTheme,
                        onNudge = { dx, dy ->
                            viewModel.updateLayerImageTransform(uiState.activeLayerIndex, offsetXDelta = dx, offsetYDelta = dy)
                        },
                        onScaleChange = { sc ->
                            viewModel.updateLayerImageTransform(uiState.activeLayerIndex, scalePercent = sc)
                        },
                        onRotationChange = { rot ->
                            viewModel.updateLayerImageTransform(uiState.activeLayerIndex, rotationDegrees = rot)
                        },
                        onOpacityChange = { op ->
                            viewModel.setLayerImageOpacity(uiState.activeLayerIndex, op)
                        },
                        onReset = {
                            viewModel.resetLayerImageTransform(uiState.activeLayerIndex)
                        }
                    )
                }

                if (uiState.referenceImage.isVisible && !uiState.isZenModeActive) {
                    FloatingReferenceControlsBar(
                        refState = uiState.referenceImage,
                        theme = currentTheme,
                        onToggleLock = { viewModel.toggleFloatingReferenceLock() },
                        onToggleZOrder = { viewModel.toggleFloatingReferenceZOrder() },
                        onChangeOpacity = { viewModel.setFloatingReferenceOpacity(it) },
                        onZoomStep = { step ->
                            viewModel.updateFloatingReferenceScale(uiState.referenceImage.scale + step)
                        },
                        onRotateStep = { deg -> viewModel.rotateFloatingReference(deg) },
                        onScaleChange = { viewModel.setFloatingReferenceScaleDirect(it) },
                        onRotationChange = { viewModel.setFloatingReferenceRotationDirect(it) },
                        onResetTransform = { viewModel.resetFloatingReferenceTransform() },
                        onDeleteWindow = { viewModel.clearReferenceImage() }
                    )
                }

                // Procreate-style Krita Brush Library Drawer (12 Native + Imported .kpp/.bundle)
                ProcreateBrushPickerSheet(
                    visible = showProcreateBrushes && !uiState.isZenModeActive && !uiState.isCameraTimelineOpen,
                    activePreset = uiState.activeBrushPreset,
                    brushSize = uiState.brushSize,
                    brushOpacity = uiState.brushOpacity,
                    importedBrushes = uiState.importedKritaBrushes,
                    theme = currentTheme,
                    onSelectPreset = { viewModel.selectBrushPreset(it) },
                    onChangeSize = { viewModel.setBrushSize(it) },
                    onChangeOpacity = { viewModel.setBrushOpacity(it) },
                    onRequestImportKrita = { kritaBrushPicker.launch(arrayOf("*/*")) },
                    onDeleteImportedBrush = { viewModel.deleteImportedKritaBrush(context, it) },
                    onDismiss = { showProcreateBrushes = false },
                    onOpenBrushStudio = {
                        viewModel.toggleBrushStudio()
                        showProcreateBrushes = false
                    },
                    selectedTool = uiState.selectedTool,
                    onSelectTool = { viewModel.selectTool(it) }
                )
            }

            // Floating Left Toolbox Palette OR Camera Mode Dedicated Toolbar
            if (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideToolbar) {
                if (uiState.isCameraTimelineOpen) {
                    CameraSideToolbar(
                        selectedAction = uiState.cameraToolAction,
                        is3DMode = uiState.project.camera3D.cameraMode == CameraMode.MODE_3D,
                        isSideView3D = uiState.isCameraSideView3D,
                        showCaptureFrame = uiState.project.camera3D.showCaptureFrame,
                        theme = currentTheme,
                        onSelectAction = { viewModel.setCameraToolAction(it) },
                        onAddOrUpdateKeyframe = { viewModel.addOrUpdateCameraKeyframeAtCurrentFrame(null) },
                        onConnectTwoKeyframes = { viewModel.connectTwoCameraKeyframes() },
                        onToggleCaptureFrame = { viewModel.toggleCameraShowCaptureFrame() },
                        onToggleSideView3D = { viewModel.toggleCameraSideView3D() },
                        onResetCurrentKeyframe = { viewModel.resetCameraAtCurrentFrame() },
                        onCloseCameraMode = { viewModel.closeCameraTimeline() },
                        cameraViewportNavTarget = uiState.cameraViewportNavTarget,
                        onToggleViewportNavTarget = { viewModel.toggleCameraViewportNavTarget() },
                        onFocusSelected = { viewModel.focusSelectedStageTarget() },
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        isThreeWheels3DOpen = uiState.isStudio3DThreeWheelsOpen,
                        onToggleThreeWheels3D = { viewModel.toggleStudio3DThreeWheels() },
                        isSplitNodesOpen = uiState.isStudio3DSplitNodesOpen,
                        onToggleSplitNodes = { viewModel.toggleStudio3DSplitNodes() },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    )
                } else {
                    ToolboxPalette(
                        selectedTool = uiState.selectedTool,
                        currentColor = uiState.currentColor,
                        brushSize = uiState.brushSize,
                        isStylusOnlyMode = uiState.isStylusOnlyMode,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        onSelectTool = { viewModel.selectTool(it) },
                        onChangeBrushSize = { viewModel.setBrushSize(it) },
                        onOpenColorPicker = { viewModel.openDialog(EditorDialog.COLOR_PICKER) },
                        onToggleStylusOnly = { viewModel.setStylusOnlyMode(!uiState.isStylusOnlyMode) },
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onResetZoom = { viewModel.resetCanvasTransform() },
                        theme = currentTheme,
                        eraserUsesPressure = uiState.preferences.eraserUsesPressure,
                        onToggleEraserPressure = { viewModel.setEraserUsesPressure(!uiState.preferences.eraserUsesPressure) },
                        eraserPresetId = uiState.preferences.eraserBrushPresetId,
                        onSelectEraserPreset = { viewModel.setEraserBrushPresetId(it) },
                        isExtraToolsOpen = showExtraToolsMenu,
                        onToggleExtraTools = { showExtraToolsMenu = !showExtraToolsMenu },
                        onOpenBrushStudio = { viewModel.toggleBrushStudio() },
                        eraserSize = uiState.eraserSize,
                        onChangeEraserSize = { viewModel.setEraserSize(it) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    )
                }
            }

            // Floating Mobile Brush Studio Configurator Drawer
            BrushStudioDrawer(
                visible = uiState.isBrushStudioOpen && !showExtraToolsMenu && !uiState.isZenModeActive && !uiState.isCameraTimelineOpen,
                activePreset = uiState.activeBrushPreset,
                nativePresets = uiState.nativeBrushPresets,
                importedPresets = uiState.importedKritaBrushes,
                theme = currentTheme,
                onSelectPreset = { viewModel.selectBrushPreset(it) },
                onSaveBrushCustomization = { preset, sz, op, params ->
                    viewModel.saveBrushCustomization(context, preset, sz, op, params)
                },
                onResetBrushCustomization = { preset ->
                    viewModel.resetBrushCustomization(context, preset)
                },
                onRequestImportKrita = { kritaBrushPicker.launch(arrayOf("*/*")) },
                onClose = { viewModel.closeBrushStudio() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 58.dp)
            )

            // Extra Tools Floating Menu anchored cleanly next to the Left Toolbox Palette (houses migrated Top Bar actions)
            ExtraToolsFloatingMenu(
                visible = showExtraToolsMenu,
                onionSkinEnabled = uiState.onionSkinEnabled,
                mirrorHorizontal = uiState.mirrorCanvasHorizontal,
                isStylusOnlyMode = uiState.isStylusOnlyMode,
                theme = currentTheme,
                onDismiss = { showExtraToolsMenu = false },
                onOpenLayers = { viewModel.openDialog(EditorDialog.LAYERS) },
                onOpenFrameLayers = { viewModel.openFrameLayersPanel() },
                onToggleOnionSkin = { viewModel.toggleOnionSkin() },
                onToggleMirror = { viewModel.toggleMirrorCanvasHorizontal() },
                onPickReferenceImage = { imagePicker.launch("image/*") },
                onCopySelection = { viewModel.copySelection() },
                onPasteSelection = { viewModel.pasteSelection() },
                onToggleStylusOnly = { viewModel.setStylusOnlyMode(!uiState.isStylusOnlyMode) },
                onResetCamera = { viewModel.resetCanvasTransform() },
                onClearFrame = { viewModel.clearCurrentFrame() },
                onOpenProjectSettings = { viewModel.openDialog(EditorDialog.PROJECT_SETTINGS) },
                isVideoTransformMode = uiState.isVideoTransformMode,
                onToggleVideoTransformMode = { viewModel.toggleVideoTransformMode() },
                isZenModeActive = uiState.isZenModeActive,
                onToggleZenMode = { viewModel.toggleZenMode() },
                onSaveProject = {
                    if (uiState.multiplayerRole == MultiplayerRole.CLIENT) {
                        Toast.makeText(context, "Visitante: arquivo salvo apenas no dono da sala!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.saveCurrentProject(context)
                        Toast.makeText(context, "Projeto Salvo!", Toast.LENGTH_SHORT).show()
                    }
                },
                onExportProject = { viewModel.openDialog(EditorDialog.EXPORT) },
                onOpenNetworkStudio = { viewModel.navigateTo(AppScreen.NETWORK_STUDIO) },
                onSwitchUiMode = { viewModel.setUiMode(AppUiMode.PC) },
                switchUiModeLabel = "Modo PC Desktop",
                switchUiModeIcon = Icons.Default.DesktopWindows,
                onOpenAppSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },
                onNavigateBackHome = {
                    if (uiState.multiplayerRole == MultiplayerRole.CLIENT) {
                        viewModel.leaveRemoteSessionAsVisitor(context, AppScreen.HOME)
                    } else {
                        viewModel.navigateTo(AppScreen.HOME)
                    }
                },
                gridRulerSettings = uiState.gridRulerSettings,
                onToggleGridRuler = { viewModel.toggleGridRuler() },
                onUpdateGridRulerSettings = { viewModel.updateGridRulerSettings(it) },
                perspectiveRulerSettings = uiState.perspectiveRulerSettings,
                onTogglePerspectiveRuler = { viewModel.togglePerspectiveRuler() },
                onUpdatePerspectiveRulerSettings = { viewModel.updatePerspectiveRulerSettings(it) },
                onionSkinOpacity = uiState.project.onionSkin.opacity,
                onChangeOnionSkinOpacity = { viewModel.setOnionSkinOpacity(it) },
                onOpenPaletteTray = { viewModel.openPaletteTray() },
                isQuickPaletteOpen = uiState.isQuickPaletteOpen,
                onToggleQuickPalette = { viewModel.toggleQuickPalette() },
                onOpenCameraTimeline = { viewModel.toggleCameraTimeline() },
                isCameraTimelineOpen = uiState.isCameraTimelineOpen,
                show3DInCanvas = uiState.project.canvasBackground.show3DInCanvas,
                canvas3DRenderMode = uiState.project.canvasBackground.canvas3DRenderMode,
                onToggleShow3DInCanvas = { viewModel.toggleShow3DInCanvas() },
                onSelectCanvas3DRenderMode = { viewModel.setCanvas3DBackgroundRenderMode(it) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 58.dp)
            )

            // Popover Flutuante Config. Balde (Expandir px + Pintar por baixo + Camadas Referência)
            if (!uiState.isCameraTimelineOpen && uiState.showBucketConfig && !uiState.isZenModeActive) {
                val currFrameBucket = uiState.project.frames.getOrNull(uiState.currentFrameIndex)
                BucketConfigPopover(
                    config = uiState.bucketConfig,
                    frameLayers = currFrameBucket?.layers ?: emptyList(),
                    localLayers = currFrameBucket?.localLayers ?: emptyList(),
                    panelColor = panelColor,
                    accentColor = accentColor,
                    textColor = textColor,
                    onUpdateConfig = { viewModel.updateBucketConfig(it) },
                    onToggleTimelineLayerReference = { viewModel.toggleTimelineLayerBucketReference(it) },
                    onToggleLocalLayerReference = { viewModel.toggleFrameLocalLayerBucketReference(it) },
                    onCreateBucketLayer = { viewModel.addBucketLayer(insertBelowActive = true) },
                    onSelectBucketTargetLayer = { viewModel.setActiveBucketTargetLayer(it) },
                    onDismiss = { viewModel.closeBucketConfig() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 58.dp)
                )
            }

            // Janela Flutuante de Palheta Rápida (Já Escolhida, Arrastável e com Cadeado)
            if (uiState.isQuickPaletteOpen && !uiState.isZenModeActive) {
                QuickPaletteFloatingWindow(
                    swatches = uiState.preferences.colorPalette.swatches,
                    currentColor = uiState.currentColor,
                    isLocked = uiState.isQuickPaletteLocked,
                    panelColor = panelColor,
                    accentColor = accentColor,
                    textColor = textColor,
                    onSelectColor = { viewModel.setColor(it) },
                    onOpenFullPaletteTray = { viewModel.openPaletteTray() },
                    onToggleLock = { viewModel.toggleQuickPaletteLock() },
                    onClose = { viewModel.closeQuickPalette() },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Painel Flutuante de Subcamadas Isoladas por Quadro ("Camadas do Quadro")
            val currFrameObj = uiState.project.frames.getOrNull(uiState.currentFrameIndex)
            val activeTimelineLayerName = currFrameObj?.layers?.getOrNull(uiState.activeLayerIndex)?.name ?: "Camada Principal"
            FrameLayersFloatingPanel(
                visible = uiState.isFrameLayersPanelOpen && !uiState.isZenModeActive,
                currentFrameIndex = uiState.currentFrameIndex,
                localLayers = currFrameObj?.localLayers ?: emptyList(),
                activeLocalLayerIndex = uiState.activeLocalLayerIndex,
                activeTargetIsFrameLocalLayer = uiState.activeTargetIsFrameLocalLayer,
                activeTimelineLayerName = activeTimelineLayerName,
                theme = currentTheme,
                onClose = { viewModel.closeFrameLayersPanel() },
                onSelectLocalLayer = { viewModel.selectFrameLocalLayer(it) },
                onUseTimelineLayerTarget = { viewModel.useTimelineLayerTarget() },
                onAddLocalLayer = { viewModel.addFrameLocalLayer() },
                onDeleteLocalLayer = { viewModel.deleteFrameLocalLayer(it) },
                onRenameLocalLayer = { idx, newName -> viewModel.renameFrameLocalLayer(idx, newName) },
                onMoveLocalLayerUp = { viewModel.moveFrameLocalLayerUp(it) },
                onMoveLocalLayerDown = { viewModel.moveFrameLocalLayerDown(it) },
                onToggleVisibility = { viewModel.toggleFrameLocalLayerVisibility(it) },
                onToggleLock = { viewModel.toggleFrameLocalLayerLock(it) },
                onChangeOpacity = { idx, op -> viewModel.setFrameLocalLayerOpacity(idx, op) },
                onChangeBlendEffect = { idx, eff -> viewModel.setFrameLocalLayerBlendEffect(idx, eff) },
                onToggleClippingMask = { viewModel.toggleFrameLocalLayerClippingMask(it) },
                onAddBucketLayer = { viewModel.addFrameLocalBucketLayer() },
                onToggleBucketReference = { viewModel.toggleFrameLocalLayerBucketReference(it) },
                onSelectBucketTargetLayer = { viewModel.setActiveBucketTargetLayer(it) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
            )

            // Dedicated Top-End Undo / Redo Floating Pill (always reachable in Canvas & Estúdio 3D)
            if (!uiState.isZenModeActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 10.dp)
                        .shadow(6.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, borderCol, RoundedCornerShape(999.dp)),
                    color = panelColor.copy(alpha = 0.94f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { if (uiState.canUndo) viewModel.undo() },
                            enabled = uiState.canUndo,
                            modifier = Modifier
                                .size(34.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("undo_action_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Desfazer (Ctrl+Z)",
                                tint = if (uiState.canUndo) textColor else textColor.copy(alpha = 0.32f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (uiState.canRedo) viewModel.redo() },
                            enabled = uiState.canRedo,
                            modifier = Modifier
                                .size(34.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("redo_action_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Refazer (Ctrl+Y)",
                                tint = if (uiState.canRedo) textColor else textColor.copy(alpha = 0.32f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Floating Mobile Zen / Focus Mode Exit Pill
            if (uiState.isZenModeActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 10.dp, end = 12.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { viewModel.setZenModeActive(false) }
                        .border(1.dp, accentColor, RoundedCornerShape(20.dp))
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .testTag("mobile_exit_zen_btn"),
                    color = panelColor.copy(alpha = 0.94f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FullscreenExit,
                            contentDescription = "Sair do Modo Foco",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Sair do Foco",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }
            // Estúdio 3D: 3 Rodas (Textura & UV, Shader & Luz, Pintura 3D), Janela UV Desdobrado & Nodes Split-Screen
            if (uiState.isCameraTimelineOpen && !uiState.isZenModeActive) {
                val studio3DObjs = uiState.project.camera3D.scene3DObjects
                val selectedStudio3DObj = if (uiState.stageSelection.type == StageTargetType.OBJECT_3D) {
                    studio3DObjs.firstOrNull { it.id == uiState.stageSelection.id } ?: studio3DObjs.firstOrNull()
                } else {
                    studio3DObjs.firstOrNull()
                }

                if (uiState.isStudio3DThreeWheelsOpen) {
                    Studio3DThreeWheelsSheet(
                        sceneObjects = studio3DObjs,
                        selectedObjectId = selectedStudio3DObj?.id,
                        onSelectObject = { viewModel.selectStageTarget(StageSelection(StageTargetType.OBJECT_3D, it)) },
                        onUpdateObject = { id, bump, tf -> viewModel.updateSceneObject3D(id, bump, tf) },
                        onPickTextureImage = { objId, slotIdx ->
                            pendingTexObjId = objId
                            pendingTexSlotIdx = slotIdx
                            studio3DTexturePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        isUvUnfoldOpen = uiState.isStudio3DUVUnfoldOpen,
                        onToggleUvUnfold = { viewModel.toggleStudio3DUVUnfold() },
                        onOpenSplitNodes = {
                            viewModel.closeStudio3DThreeWheels()
                            viewModel.toggleStudio3DSplitNodes()
                        },
                        studio3DPaintEnabled = uiState.studio3DPaintEnabled,
                        onToggleStudio3DPaint = { viewModel.setStudio3DPaintEnabled(it) },
                        paintTool = uiState.studio3DPaintTool,
                        onSelectPaintTool = { viewModel.setStudio3DPaintTool(it) },
                        paintSize = uiState.studio3DPaintSize,
                        onChangePaintSize = { viewModel.setStudio3DPaintSize(it) },
                        paintOpacity = uiState.studio3DPaintOpacity,
                        onChangePaintOpacity = { viewModel.setStudio3DPaintOpacity(it) },
                        activeBrushId = uiState.activeBrushPreset.id,
                        importedBrushes = uiState.importedKritaBrushes,
                        onSelectBrushPreset = { viewModel.selectBrushPreset(it) },
                        currentColorArgb = uiState.currentColor,
                        onMergePaintLayers = { viewModel.mergeSceneObjectPaintLayers(it) },
                        onClose = { viewModel.closeStudio3DThreeWheels() },
                        bgColor = panelColor,
                        textColor = textColor,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 56.dp, end = 10.dp, bottom = 8.dp)
                    )
                }

                if (uiState.isStudio3DUVUnfoldOpen && selectedStudio3DObj != null) {
                    UvUnfoldWindowOverlay(
                        selectedObject = selectedStudio3DObj,
                        paintTool = uiState.studio3DPaintTool,
                        paintSize = uiState.studio3DPaintSize,
                        paintOpacity = uiState.studio3DPaintOpacity,
                        activeBrushId = uiState.activeBrushPreset.id,
                        activeBrushParamsJson = activeBrushParamsJson,
                        currentColorArgb = uiState.currentColor,
                        onPaintUVSegment = { objId, u0, v0, u1, v1 ->
                            viewModel.paintSceneObjectSurfaceSegment(objId, u0, v0, u1, v1)
                        },
                        onClose = { viewModel.toggleStudio3DUVUnfold() },
                        bgColor = panelColor,
                        textColor = textColor,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 50.dp, end = 10.dp)
                    )
                }

                if (uiState.isStudio3DSplitNodesOpen) {
                    BackHandler {
                        viewModel.closeStudio3DSplitNodes()
                    }
                    Studio3DSplitNodesOverlay(
                        selectedObject = selectedStudio3DObj,
                        studio3DPaintEnabled = uiState.studio3DPaintEnabled,
                        onToggleStudio3DPaint = { viewModel.setStudio3DPaintEnabled(it) },
                        onPaintObjectUV = { objId, u0, v0, u1, v1 ->
                            viewModel.paintSceneObjectSurfaceSegment(objId, u0, v0, u1, v1)
                        },
                        onUpdateObject = { id, bump, tf -> viewModel.updateSceneObject3D(id, bump, tf) },
                        onClose = { viewModel.closeStudio3DSplitNodes() },
                        bgColor = panelColor,
                        textColor = textColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    QuickCopyPasteGestureMenu(
        visible = uiState.isQuickGestureMenuOpen,
        theme = currentTheme,
        hasActiveSelection = uiState.activeSelection.isActive,
        onDismiss = { viewModel.closeQuickGestureMenu() },
        onCopySelection = { viewModel.copySelection() },
        onCutSelection = {
            viewModel.copySelection()
            viewModel.deleteSelection()
        },
        onPasteSelection = { viewModel.pasteSelection() },
        onClearLayer = { viewModel.clearActiveLayer() },
        onUndo = { viewModel.undo() },
        onRedo = { viewModel.redo() }
    )

    // Modal Dialogs
    when (uiState.activeDialog) {
        EditorDialog.LAYERS -> {
            LayersBottomSheet(
                layers = uiState.project.frames[uiState.currentFrameIndex].layers,
                activeLayerIndex = uiState.activeLayerIndex,
                mediaAssets = uiState.project.mediaAssets,
                onSelectLayer = { viewModel.setActiveLayer(it) },
                onAddLayer = { viewModel.addLayer() },
                onDeleteActiveLayer = { viewModel.deleteActiveLayer() },
                onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                onToggleLock = { viewModel.toggleLayerLock(it) },
                onChangeOpacity = { idx, op -> viewModel.setLayerOpacity(idx, op) },
                onImportAudio = { audioPicker.launch("audio/*") },
                onImportVideo = { videoPicker.launch("video/*") },
                onRemoveAsset = { viewModel.deleteMediaAsset(it) },
                onToggleAudioMute = { viewModel.toggleAudioMute(it) },
                onSetAudioVolume = { id, vol -> viewModel.setAudioVolume(id, vol) },
                onUpdateAssetOpacity = { id, op -> viewModel.updateSpecialAssetOpacity(id, op) },
                onUpdateAssetScale = { id, sc -> viewModel.updateSpecialAssetScale(id, sc) },
                onResetAssetTransform = { viewModel.resetSpecialAssetTransform(it) },
                onToggleVideoTransformMode = { viewModel.toggleVideoTransformMode() },
                onMoveLayerUp = { viewModel.moveLayerUp(it) },
                onMoveLayerDown = { viewModel.moveLayerDown(it) },
                onAddBucketLayer = { viewModel.addBucketLayer(insertBelowActive = true) },
                onSelectBucketTargetLayer = { viewModel.setActiveBucketTargetLayer(it) },
                onToggleBucketReference = { viewModel.toggleTimelineLayerBucketReference(it) },
                onDismiss = { viewModel.closeDialog() }
            )
        }
        EditorDialog.COLOR_PICKER -> {
            ColorPickerDialog(
                initialColor = uiState.currentColor,
                recentColors = uiState.recentColors,
                onColorSelected = { viewModel.setColor(it, recordToRecents = false) },
                onDismiss = {
                    viewModel.recordRecentColor(uiState.currentColor)
                    viewModel.closeDialog()
                }
            )
        }
        EditorDialog.EXPORT -> {
            ExportDialog(
                exportState = uiState.exportState,
                onExportMp4 = { viewModel.exportToMp4(context) },
                onExportGif = { viewModel.exportToGif(context) },
                onExportSpriteSheet = { viewModel.exportToSpriteSheet(context) },
                onExportPngFrame = { viewModel.exportCurrentFramePng(context) },
                onExportFramePackZip = { viewModel.exportFramePackZip(context) },
                onExportProjectFile = { viewModel.exportProjectFile(context) },
                onDismissState = { viewModel.dismissExportState() },
                onClose = {
                    viewModel.dismissExportState()
                    viewModel.closeDialog()
                },
                cameraEnabled = uiState.project.camera3D.isEnabled,
                cameraCropOnly = uiState.exportCameraCropOnly,
                onToggleCameraCropOnly = { viewModel.setExportCameraCropOnly(it) }
            )
        }
        EditorDialog.PROJECT_SETTINGS -> {
            ProjectSettingsDialog(
                project = uiState.project,
                savedCustomResolutions = uiState.preferences.savedCustomResolutions,
                onSaveCustomResolution = { n, w, h -> viewModel.saveCustomResolution(n, w, h) },
                onDeleteCustomResolution = { viewModel.deleteCustomResolution(it) },
                onUpdateProjectName = { viewModel.setProjectName(it) },
                onUpdateResolution = { viewModel.setResolution(it) },
                onUpdateFps = { viewModel.setFps(it) },
                onUpdateOnionSkin = { viewModel.updateOnionSkinSettings(it) },
                onClose = { viewModel.closeDialog() }
            )
        }
        EditorDialog.PROJECTS_LIST -> {
            ProjectsListDialog(
                savedCustomResolutions = uiState.preferences.savedCustomResolutions,
                onSaveCustomResolution = { n, w, h -> viewModel.saveCustomResolution(n, w, h) },
                onDeleteCustomResolution = { viewModel.deleteCustomResolution(it) },
                onNewProject = { name, res, fps ->
                    viewModel.loadNewEmptyProject(name, res, fps)
                },
                onLoadDemo = {
                    viewModel.loadDemoProject()
                },
                onPickProjectFile = {
                    projectFilePicker.launch(arrayOf("application/json", "*/*"))
                },
                onPickReferenceImage = {
                    imagePicker.launch("image/*")
                },
                onClose = { viewModel.closeDialog() }
            )
        }
        else -> { /* No dialog active */ }
    }

    ReferenceImageChoiceDialog(
        pendingUri = uiState.pendingReferenceImageUri,
        theme = currentTheme,
        onChooseTimelineLayer = { uri ->
            viewModel.applyPickedImageAsTimelineLayer(context, uri)
        },
        onChooseFloatingWindow = { uri ->
            viewModel.applyPickedImageAsFloatingWindow(context, uri)
        },
        onDismiss = { viewModel.dismissReferenceImageChoice() }
    )

    if (uiState.isPaletteTrayOpen) {
        ColorPaletteTrayModal(
            paletteConfig = uiState.preferences.colorPalette,
            currentColor = uiState.currentColor,
            selectedSlotIndex = uiState.selectedPaletteSlotIndex,
            theme = currentTheme,
            onSelectSlot = { viewModel.selectPaletteSlot(it) },
            onSaveToSlot = { viewModel.saveCurrentColorToPaletteSlot(it) },
            onRemoveSlot = { viewModel.removePaletteSlot(it) },
            onRollColorTheoryDice = { viewModel.generateColorTheoryPaletteRow() },
            onChangeGridSize = { cols, rows -> viewModel.setPaletteGridSize(cols, rows) },
            onActivateEyedropper = {
                viewModel.startEyedropperFromPaletteTray()
            },
            onOpenColorWheel = {
                viewModel.openColorWheelFromPaletteTray()
            },
            onDismiss = { viewModel.closePaletteTray() }
        )
    }
}
