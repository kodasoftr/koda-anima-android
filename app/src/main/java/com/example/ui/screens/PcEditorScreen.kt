package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatPaint
import com.example.model.BrushPreset
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.MediaAssetManager
import com.example.engine.TimelineTrackEngine
import com.example.model.AppScreen
import com.example.model.AppUiMode
import com.example.model.CameraMode
import com.example.model.DrawingStroke
import com.example.model.KodanimaTheme
import com.example.model.Layer
import com.example.model.MultiplayerRole
import com.example.model.StageSelection
import com.example.model.StageTargetType
import com.example.model.TimelineLayoutMode
import com.example.model.ToolType
import com.example.ui.components.BrushPresetsBar
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
import com.example.ui.components.LassoVectorIcon
import com.example.ui.components.LayersBottomSheet
import com.example.ui.components.ProjectSettingsDialog
import com.example.ui.components.QuickCopyPasteGestureMenu
import com.example.ui.components.QuickPaletteFloatingWindow
import com.example.ui.components.ReferenceImageChoiceDialog
import com.example.ui.components.Studio3DSplitNodesOverlay
import com.example.ui.components.Studio3DThreeWheelsSheet
import com.example.ui.components.Studio3DUvEditorScreen
import com.example.ui.components.Studio3DUvPaintScreen
import com.example.ui.components.ThreeWheelsIcon
import com.example.ui.components.TimelineLayerImageTransformHud
import com.example.ui.components.UvUnfoldWindowOverlay
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import androidx.compose.foundation.layout.BoxScope
import com.example.viewmodel.EditorDialog
import com.example.viewmodel.EditorUiState
import com.example.viewmodel.KodanimaViewModel
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType

/**
 * Authentic Desktop PC Studio Interface for Kodanima 2D.
 *
 * Faithfully reproduces the C++ Kodanima 2D layout as seen in the user's reference screenshots:
 * - Floating left vertical toolbar with the 7 core tools (Pencil, Eraser, Bucket, Lasso, Color Wheel, Eyedropper, Clapperboard/Render).
 * - Canvas in viewport with 1-finger draw, 2-finger zoom/pan/rotate, stylus pressure and virtual cursor.
 * - Top header with canvas rotation reset chip, layout switcher, and undo/redo.
 * - Two Timeline Modes:
 *     1) Heritage Junior: Compact floating timeline window centered at the bottom.
 *     2) Heritage Profissional: Full-width edge-to-edge timeline spanning the entire screen width.
 * - True Stacked Multi-Layer Timeline (like Windows Kodanima 2D):
 *     Each layer has its own horizontal track stacked vertically across all animation frames.
 * - Draggable duration handle (bolinha) + typed duration dialog with presets (1, 2, 3, 4, 6, 12, 24).
 * - FlipaClip-style floating selection actions (Flip H/V, Copy, Paste, Fill, Delete, Done).
 */
enum class StudioFocusZone(val label: String, val hint: String) {
    CANVAS("Canvas", "Roda do mouse amplia o desenho"),
    TIMELINE_RULER("Régua", "Roda do mouse move a linha do tempo"),
    TIMELINE_FRAMES("Quadros", "Roda do mouse amplia a visualização dos quadros"),
    LAYERS_ORDER("Camadas", "Roda do mouse rola a lista de camadas")
}

@Composable
fun PcEditorScreen(
    viewModel: KodanimaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTheme = uiState.preferences.theme
    val themeText = Color(currentTheme.text)
    val themeMuted = themeText.copy(alpha = 0.62f)
    val themeBorder = themeText.copy(alpha = 0.22f)
    val adaptiveCyan = if (currentTheme.isLight) Color(currentTheme.secondaryAccent) else CyanAccent
    val adaptiveGreen = if (currentTheme.isLight) Color(0xFF008744) else NeonGreen
    val adaptivePink = if (currentTheme.isLight) Color(0xFFD81B60) else NeonPink
    val adaptiveOrange = if (currentTheme.isLight) Color(0xFFD84315) else OrangeAccent
    val coroutineScope = rememberCoroutineScope()
    var activeFocusZone by remember { mutableStateOf(StudioFocusZone.CANVAS) }
    val pcFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { pcFocusRequester.requestFocus() }
    }
    LaunchedEffect(uiState.selectedTool, uiState.bezierNodes.size) {
        if (uiState.selectedTool == ToolType.BEZIER_PEN) {
            runCatching { pcFocusRequester.requestFocus() }
        }
    }

    var showExtraToolsMenu by remember { mutableStateOf(false) }

    BackHandler {
        if (uiState.multiplayerRole == MultiplayerRole.CLIENT) {
            viewModel.leaveRemoteSessionAsVisitor(context, AppScreen.HOME)
        } else {
            viewModel.navigateTo(AppScreen.HOME)
        }
    }

    var showOnionOpacityDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showMediaImportChoice by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }

    val kritaBrushPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importKritaBrushesFromUri(context, uri)
            Toast.makeText(context, "Importando brush do Krita...", Toast.LENGTH_SHORT).show()
        }
    }

    val scene3DPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
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

    var pendingExportObjId by remember { mutableStateOf<String?>(null) }
    val export3DObjLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        val objId = pendingExportObjId
        if (uri != null && objId != null) {
            viewModel.exportSelected3DObjectToUri(context, objId, uri)
        }
    }

    val refImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setReferenceImageUri(uri.toString())
        }
    }

    // Media Pickers using persistent storage
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudioFile(context, uri)
            Toast.makeText(context, "Áudio importado para a timeline!", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importVideoFile(context, uri)
            Toast.makeText(context, "Mídia de referência importada!", Toast.LENGTH_SHORT).show()
        }
    }

    val activeBrushParamsJson = remember(uiState.activeBrushPreset) {
        uiState.activeBrushPreset.dynamicParams.toCompactJson()
    }
    val isFloatingRefBelow = uiState.referenceImage.isVisible && !uiState.referenceImage.renderAboveCanvas

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(currentTheme.bg))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("pc_editor_screen")
            .focusRequester(pcFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    viewModel.handlePhysicalKeyEvent(keyEvent.nativeKeyEvent, context)
                } else {
                    false
                }
            }
    ) {

        // 1. Viewport Canvas (Draw & Multi-touch, Hardware Acceleration, Stylus & Virtual Cursor)
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
                activeFocusZone = StudioFocusZone.CANVAS
                runCatching { pcFocusRequester.requestFocus() }
            },
            onTransformChange = { zoomChange, pX, pY, rotChange ->
                viewModel.updateTransform(zoomChange, pX, pY, rotChange)
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
            showFloatingBezierBar = false,
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
            theme = currentTheme,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Unobstructed Top HUD Column (Top Header removed -> Krita Brush Bar positioned cleanly at top)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 2a. Horizontal Krita 12 Brushes + 3-Wheels Toggle to Imported Krita Brushes (hidden during Camera Mode)
            if (!uiState.isCameraTimelineOpen && (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideBrushBar)) {
                BrushPresetsBar(
                    activePreset = uiState.activeBrushPreset,
                    onSelectPreset = { viewModel.selectBrushPreset(it) },
                    theme = currentTheme,
                    nativeBrushes = uiState.nativeBrushPresets,
                    importedBrushes = uiState.importedKritaBrushes,
                    onRequestImportKrita = { kritaBrushPicker.launch(arrayOf("*/*")) },
                    onDeleteImportedBrush = { viewModel.deleteImportedKritaBrush(context, it) },
                    onOpenBrushStudio = { viewModel.toggleBrushStudio() },
                    selectedTool = uiState.selectedTool,
                    onSelectTool = { viewModel.selectTool(it) },
                    isCompact = false,
                    modifier = Modifier
                        .padding(start = 68.dp, end = 12.dp)
                        .shadow(6.dp, RoundedCornerShape(8.dp))
                        .testTag("pc_brush_presets_bar")
                )
            }

            // Banner de oferta de brush importado por amigo diretamente abaixo da barra de brush
            if (!uiState.isZenModeActive) {
                FriendBrushOfferBanner(
                    offer = uiState.pendingFriendBrushOffer,
                    theme = currentTheme,
                    onAccept = {
                        viewModel.acceptFriendBrushOffer(context)
                        Toast.makeText(context, "Brush baixado para seus Importados!", Toast.LENGTH_SHORT).show()
                    },
                    onDecline = { viewModel.declineFriendBrushOffer() },
                    modifier = Modifier.padding(start = 68.dp, end = 12.dp)
                )

                // 2c. Unobstructed Compact Horizontal Collaborators Strip (Hideable via Configurações)
                CollaboratorHorizontalStrip(
                    connectedFriends = uiState.connectedFriends,
                    showHud = uiState.preferences.showCanvasCollaboratorsHud,
                    fallbackPingMs = uiState.currentPingMs,
                    theme = currentTheme,
                    modifier = Modifier.padding(start = 68.dp, end = 12.dp)
                )
            }

            // 2d. Minimalist Fine Eraser Bar (pequena, fina e minimalista)
            if (!uiState.isCameraTimelineOpen && uiState.selectedTool == ToolType.ERASER && (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideToolbar)) {
                Surface(
                    modifier = Modifier
                        .height(24.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, NeonPink.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .testTag("pc_eraser_options_bar"),
                    color = Color(currentTheme.panel).copy(alpha = 0.96f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EraserVectorIcon(
                            tint = NeonPink,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Borracha (E)",
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
                                        color = if (isChosen) adaptivePink else themeBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    ),
                                color = if (isChosen) adaptivePink.copy(alpha = 0.25f) else Color.Transparent
                            ) {
                                Text(
                                    text = preset.shortLabel,
                                    fontSize = 8.5.sp,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isChosen) adaptivePink else themeText.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        InlineNumericPxInput(
                            valuePx = uiState.eraserSize,
                            minPx = 2f,
                            maxPx = 120f,
                            accentColor = adaptivePink,
                            textColor = themeText,
                            panelBgColor = Color(currentTheme.panel),
                            testTag = "pc_eraser_px_input",
                            onCommitValue = { viewModel.setEraserSize(it) }
                        )
                        Slider(
                            value = uiState.eraserSize,
                            onValueChange = { viewModel.setEraserSize(it) },
                            valueRange = 2f..80f,
                            modifier = Modifier
                                .width(60.dp)
                                .height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = adaptivePink,
                                activeTrackColor = adaptivePink
                            )
                        )

                        val hasPressure = uiState.preferences.eraserUsesPressure
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { viewModel.setEraserUsesPressure(!hasPressure) }
                                .border(
                                    1.dp,
                                    if (hasPressure) adaptivePink else themeBorder,
                                    RoundedCornerShape(4.dp)
                                ),
                            color = if (hasPressure) adaptivePink.copy(alpha = 0.25f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Gesture,
                                    contentDescription = null,
                                    tint = if (hasPressure) adaptivePink else themeMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = if (hasPressure) "Pressão" else "Fixo",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasPressure) adaptivePink else themeMuted
                                )
                            }
                        }
                    }
                }
            }

            // 2e. PC Mode Caneta Vetorial Bézier HUD Bar (Confirmar via botão ou Enter)
            if (!uiState.isCameraTimelineOpen && uiState.selectedTool == ToolType.BEZIER_PEN &&
                (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideToolbar)
            ) {
                Surface(
                    modifier = Modifier
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.2.dp, Color(currentTheme.accent), RoundedCornerShape(16.dp))
                        .testTag("pc_bezier_action_bar"),
                    color = Color(currentTheme.panel).copy(alpha = 0.96f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            tint = Color(currentTheme.accent),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (uiState.bezierNodes.isEmpty()) "Bézier (clique para pontos)" else "Bézier (${uiState.bezierNodes.size} nós)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(currentTheme.text)
                        )

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.toggleBezierClosed() }
                                .border(
                                    1.dp,
                                    if (uiState.isBezierClosed) Color(currentTheme.accent) else themeBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .testTag("pc_bezier_toggle_closed_btn"),
                            color = if (uiState.isBezierClosed) Color(currentTheme.accent).copy(alpha = 0.25f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Loop, contentDescription = "Fechar curva", tint = Color(currentTheme.text), modifier = Modifier.size(12.dp))
                                Text("Fechar", fontSize = 9.sp, color = Color(currentTheme.text), fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (uiState.bezierNodes.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.undoLastBezierNode() }
                                    .testTag("pc_bezier_undo_btn"),
                                color = Color(currentTheme.text).copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfazer ponto", tint = Color(currentTheme.text), modifier = Modifier.size(12.dp))
                                    Text("Desfazer", fontSize = 9.sp, color = Color(currentTheme.text))
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.clearBezierNodes() }
                                    .testTag("pc_bezier_clear_btn"),
                                color = NeonPink.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpar curva", tint = NeonPink, modifier = Modifier.size(12.dp))
                                    Text("Limpar", fontSize = 9.sp, color = NeonPink)
                                }
                            }
                        }

                        if (uiState.bezierNodes.size >= 2) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.confirmBezierCurve() }
                                    .testTag("pc_bezier_confirm_btn"),
                                color = NeonGreen
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Confirmar Bézier (Enter)", tint = Color.Black, modifier = Modifier.size(13.dp))
                                    Text("Confirmar (Enter)", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Floating FlipaClip-style Selection Actions Toolbar (when Lasso/MagicWand selection is active)
            if (!uiState.isCameraTimelineOpen && uiState.activeSelection.isActive && !uiState.isZenModeActive) {
                Surface(
                    modifier = Modifier
                        .shadow(12.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, Color(currentTheme.accent), RoundedCornerShape(20.dp))
                        .testTag("flipaclip_selection_toolbar"),
                    color = Color(currentTheme.panel).copy(alpha = 0.96f)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.invertSelection() }
                                .border(
                                    1.dp,
                                    if (uiState.activeSelection.isInverted) adaptiveOrange else themeBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .testTag("pc_invert_selection_btn"),
                            color = if (uiState.activeSelection.isInverted) adaptiveOrange.copy(alpha = 0.25f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.CompareArrows,
                                    contentDescription = "Inverter Seleção",
                                    tint = if (uiState.activeSelection.isInverted) adaptiveOrange else Color(currentTheme.text),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Inverter",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.activeSelection.isInverted) adaptiveOrange else Color(currentTheme.text)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.flipSelectionH() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Espelhar H", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.flipSelectionV() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Espelhar V", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.rotateSelection(-15f) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "Girar -15°", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.rotateSelection(15f) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Girar +15°", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.scaleSelection(0.85f) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Reduzir Escala", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.scaleSelection(1.15f) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Aumentar Escala", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            viewModel.copySelection()
                            Toast.makeText(context, "Seleção copiada!", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = {
                            viewModel.pasteSelection()
                            Toast.makeText(context, "Colado!", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Colar", tint = Color(currentTheme.text), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.fillSelectionWithCurrentColor() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.FormatPaint, contentDescription = "Preencher com Cor", tint = Color(currentTheme.accent), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.deleteSelection() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir Seleção", tint = NeonPink, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { viewModel.applySelectionTransform() }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Check, contentDescription = "Concluir Transformação", tint = NeonGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Floating Video Transform HUD (when Transformar Vídeo mode is active)
            val activeVideoAsset = uiState.project.mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }
            if (!uiState.isCameraTimelineOpen && uiState.isVideoTransformMode && activeVideoAsset != null && !uiState.isZenModeActive) {
                Surface(
                    modifier = Modifier
                        .shadow(10.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, OrangeAccent, RoundedCornerShape(16.dp))
                        .testTag("pc_video_transform_hud"),
                    color = Color(currentTheme.panel).copy(alpha = 0.96f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.OpenWith, contentDescription = null, tint = OrangeAccent, modifier = Modifier.size(15.dp))
                        Text(
                            text = "Transformar Vídeo",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrangeAccent
                        )
                        IconButton(onClick = { viewModel.scaleVideoAsset(activeVideoAsset.id, 0.9) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Reduzir Escala", tint = Color(currentTheme.text), modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "${activeVideoAsset.scalePercent.toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(currentTheme.text)
                        )
                        IconButton(onClick = { viewModel.scaleVideoAsset(activeVideoAsset.id, 1.1) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Aumentar Escala", tint = Color(currentTheme.text), modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { viewModel.rotateVideoAsset(activeVideoAsset.id, -5.0) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "Girar -5°", tint = Color(currentTheme.text), modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "${activeVideoAsset.rotationDegrees.roundToInt()}°",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(currentTheme.text)
                        )
                        IconButton(onClick = { viewModel.rotateVideoAsset(activeVideoAsset.id, 5.0) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Girar +5°", tint = Color(currentTheme.text), modifier = Modifier.size(15.dp))
                        }
                        Slider(
                            value = activeVideoAsset.opacityPercent / 100f,
                            onValueChange = { viewModel.updateSpecialAssetOpacity(activeVideoAsset.id, it.toDouble()) },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.width(56.dp).height(18.dp),
                            colors = SliderDefaults.colors(thumbColor = OrangeAccent, activeTrackColor = OrangeAccent)
                        )
                        IconButton(onClick = { viewModel.resetVideoTransform(activeVideoAsset.id) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Resetar", tint = Color(currentTheme.text), modifier = Modifier.size(15.dp))
                        }
                        IconButton(onClick = { viewModel.setVideoTransformMode(false) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Check, contentDescription = "Concluir", tint = NeonGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            val activeLayerImageTargetPc = if (!uiState.activeTargetIsFrameLocalLayer) {
                uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers?.getOrNull(uiState.activeLayerIndex)
            } else null
            val activeLayerImgAttachmentPc = activeLayerImageTargetPc?.imageAttachment
            if (!uiState.isCameraTimelineOpen && activeLayerImageTargetPc != null && activeLayerImgAttachmentPc != null && !uiState.isZenModeActive) {
                TimelineLayerImageTransformHud(
                    layerName = activeLayerImageTargetPc.name,
                    attachment = activeLayerImgAttachmentPc,
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
        }

        // 3. Floating Left Vertical Toolbar OR Camera Mode Dedicated Toolbar
        if ((!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideToolbar) &&
            !uiState.isStudio3DSplitNodesOpen && !uiState.isStudio3DUvPaintScreenOpen && !uiState.isStudio3DUvEditorOpen
        ) {
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
                    isUvEditorOpen = uiState.isStudio3DUvEditorOpen,
                    onOpenUvEditor = { viewModel.openStudio3DUvEditor() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                ) {
                    val maxToolbarH = (maxHeight - 16.dp).coerceAtLeast(180.dp)
                    Surface(
                        modifier = Modifier
                            .heightIn(max = maxToolbarH)
                            .shadow(12.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, Color(currentTheme.accent).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .testTag("pc_floating_toolbar"),
                        color = Color(currentTheme.panel).copy(alpha = 0.95f),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(4.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DesktopToolButton(
                                icon = Icons.Default.Edit,
                                tooltip = "Lápis (P)",
                                isSelected = uiState.selectedTool == ToolType.PENCIL || uiState.selectedTool == ToolType.PEN || uiState.selectedTool == ToolType.BRUSH,
                                accentColor = Color(currentTheme.accent),
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.PENCIL) }
                            )

                            val activeStrokePx = if (uiState.selectedTool == ToolType.ERASER) uiState.eraserSize else uiState.brushSize
                            InlineNumericPxInput(
                                valuePx = activeStrokePx,
                                minPx = 1f,
                                maxPx = 120f,
                                accentColor = if (uiState.selectedTool == ToolType.ERASER) adaptivePink else Color(currentTheme.accent),
                                textColor = themeText,
                                panelBgColor = Color(currentTheme.panel),
                                testTag = "pc_inline_px_input",
                                onCommitValue = { px ->
                                    if (uiState.selectedTool == ToolType.ERASER) viewModel.setEraserSize(px)
                                    else viewModel.setBrushSize(px)
                                }
                            )

                            DesktopToolButton(
                                icon = Icons.Default.Timeline,
                                tooltip = "Caneta Vetorial Bézier",
                                isSelected = uiState.selectedTool == ToolType.BEZIER_PEN,
                                accentColor = adaptiveCyan,
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.BEZIER_PEN) }
                            )

                            DesktopCustomToolButton(
                                tooltip = "Borracha (E)",
                                isSelected = uiState.selectedTool == ToolType.ERASER,
                                accentColor = adaptivePink,
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.ERASER) }
                            ) { tint ->
                                EraserVectorIcon(tint = tint, modifier = Modifier.size(18.dp))
                            }

                            DesktopToolButton(
                                icon = Icons.Default.FormatPaint,
                                tooltip = "Balde de Tinta (G) - toque novamente para Config. Balde",
                                isSelected = uiState.selectedTool == ToolType.BUCKET,
                                accentColor = adaptiveCyan,
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.BUCKET) }
                            )

                            DesktopToolButton(
                                icon = Icons.Default.AutoFixHigh,
                                tooltip = "Varinha Mágica (W)",
                                isSelected = uiState.selectedTool == ToolType.MAGIC_WAND,
                                accentColor = Color(currentTheme.accent),
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.MAGIC_WAND) }
                            )

                            DesktopCustomToolButton(
                                tooltip = "Laço de Seleção (S)",
                                isSelected = uiState.selectedTool == ToolType.LASSO,
                                accentColor = adaptiveOrange,
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.LASSO) }
                            ) { tint ->
                                LassoVectorIcon(tint = tint, modifier = Modifier.size(18.dp))
                            }

                            DesktopToolButton(
                                icon = Icons.Default.ColorLens,
                                tooltip = "Roda de Cores",
                                isSelected = false,
                                accentColor = Color(uiState.currentColor),
                                defaultTint = themeText,
                                onClick = { viewModel.openDialog(EditorDialog.COLOR_PICKER) }
                            )

                            DesktopToolButton(
                                icon = Icons.Default.Colorize,
                                tooltip = "Conta-Gotas (I)",
                                isSelected = uiState.selectedTool == ToolType.EYEDROPPER,
                                accentColor = adaptiveGreen,
                                defaultTint = themeText,
                                onClick = { viewModel.selectTool(ToolType.EYEDROPPER) }
                            )

                            DesktopToolButton(
                                icon = Icons.Default.Movie,
                                tooltip = "Renderizar / Exportar (F12)",
                                isSelected = false,
                                accentColor = Color(currentTheme.accent),
                                defaultTint = themeText,
                                onClick = { viewModel.openDialog(EditorDialog.EXPORT) }
                            )

                            // End of PC Sidebar: 3 Overlapping Wheels button -> opens extra tools + migrated header menu
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showExtraToolsMenu = !showExtraToolsMenu }
                                    .testTag("pc_three_wheels_tools_button"),
                                color = if (showExtraToolsMenu) Color(currentTheme.accent).copy(alpha = 0.35f) else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (showExtraToolsMenu) 1.5.dp else 1.dp,
                                    color = if (showExtraToolsMenu) Color(currentTheme.accent) else Color(currentTheme.accent).copy(alpha = 0.45f)
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    ThreeWheelsIcon(
                                        tint = Color(currentTheme.text),
                                        accentColor = Color(currentTheme.accent),
                                        size = 20.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val isProfLayout = uiState.preferences.timelineLayoutMode == TimelineLayoutMode.HERITAGE_PROFISSIONAL

            // Floating Brush Studio Configurator Drawer (Nativos / Importados + Loupe Search + Sliders + Save/Reset)
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
                    .padding(start = 62.dp)
            )

            // Extra Tools Floating Menu anchored next to PC Sidebar (includes migrated Top Bar actions)
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
                onPickReferenceImage = { refImagePicker.launch("image/*") },
                onCopySelection = { viewModel.copySelection() },
                onPasteSelection = { viewModel.pasteSelection() },
                onToggleStylusOnly = { viewModel.setStylusOnlyMode(!uiState.isStylusOnlyMode) },
                onResetCamera = { viewModel.resetCanvasTransform() },
                onClearFrame = { viewModel.clearCurrentFrame() },
                onOpenProjectSettings = { viewModel.openDialog(EditorDialog.PROJECT_SETTINGS) },
                isVideoTransformMode = uiState.isVideoTransformMode,
                onToggleVideoTransformMode = { viewModel.toggleVideoTransformMode() },
                timelineLayoutLabel = if (isProfLayout) "Profissional" else "Junior",
                onToggleTimelineLayout = {
                    val nextMode = if (isProfLayout) TimelineLayoutMode.HERITAGE_JUNIOR else TimelineLayoutMode.HERITAGE_PROFISSIONAL
                    viewModel.setTimelineLayoutMode(nextMode)
                },
                isZenModeActive = uiState.isZenModeActive,
                onToggleZenMode = { viewModel.toggleZenMode() },
                onOpenNetworkStudio = { viewModel.navigateTo(AppScreen.NETWORK_STUDIO) },
                onSwitchToMobileMode = { viewModel.setUiMode(AppUiMode.MOBILE) },
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
                    .padding(start = 62.dp)
            )

            // Popover Config. Balde (Expandir px + Pintar por baixo + Camadas Referência)
            if (!uiState.isCameraTimelineOpen && uiState.showBucketConfig && !uiState.isZenModeActive) {
                val currFrameBucketPc = uiState.project.frames.getOrNull(uiState.currentFrameIndex)
                BucketConfigPopover(
                    config = uiState.bucketConfig,
                    frameLayers = currFrameBucketPc?.layers ?: emptyList(),
                    localLayers = currFrameBucketPc?.localLayers ?: emptyList(),
                    panelColor = Color(currentTheme.panel),
                    accentColor = Color(currentTheme.accent),
                    textColor = themeText,
                    onUpdateConfig = { viewModel.updateBucketConfig(it) },
                    onToggleTimelineLayerReference = { viewModel.toggleTimelineLayerBucketReference(it) },
                    onToggleLocalLayerReference = { viewModel.toggleFrameLocalLayerBucketReference(it) },
                    onCreateBucketLayer = { viewModel.addBucketLayer(insertBelowActive = true) },
                    onSelectBucketTargetLayer = { viewModel.setActiveBucketTargetLayer(it) },
                    onDismiss = { viewModel.closeBucketConfig() },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 62.dp)
                )
            }
        }

        // Janela Flutuante de Palheta Rápida (Já Escolhida, Arrastável e com Cadeado, atalho Q)
        if (uiState.isQuickPaletteOpen && !uiState.isZenModeActive) {
            QuickPaletteFloatingWindow(
                swatches = uiState.preferences.colorPalette.swatches,
                currentColor = uiState.currentColor,
                isLocked = uiState.isQuickPaletteLocked,
                panelColor = Color(currentTheme.panel),
                accentColor = Color(currentTheme.accent),
                textColor = themeText,
                onSelectColor = { viewModel.setColor(it) },
                onOpenFullPaletteTray = { viewModel.openPaletteTray() },
                onToggleLock = { viewModel.toggleQuickPaletteLock() },
                onClose = { viewModel.closeQuickPalette() },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // 4. Timeline at Bottom (Heritage Junior vs Heritage Profissional OR 3D Camera Timeline)
        val hideTimelineForFullscreenStudio = uiState.isStudio3DSplitNodesOpen || uiState.isStudio3DUvPaintScreenOpen || uiState.isStudio3DUvEditorOpen
        if ((!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideTimeline) && !hideTimelineForFullscreenStudio) {
            PcEditorBottomTimelineSection(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                currentTheme = currentTheme,
                themeText = themeText,
                themeMuted = themeMuted,
                themeBorder = themeBorder,
                adaptiveCyan = adaptiveCyan,
                adaptiveGreen = adaptiveGreen,
                adaptivePink = adaptivePink,
                adaptiveOrange = adaptiveOrange,
                coroutineScope = coroutineScope,
                activeFocusZone = activeFocusZone,
                onFocusZoneChange = { activeFocusZone = it },
                onShowThemeMenu = { showThemeMenu = true },
                onShowOnionOpacityDialog = { showOnionOpacityDialog = true },
                onShowMediaImportChoice = { showMediaImportChoice = true },
                onShowDurationDialog = { showDurationDialog = true },
                onShowFpsDialog = { showFpsDialog = true },
                onLaunchAudioPicker = { audioPicker.launch("audio/*") },
                onLaunchVideoPicker = { videoPicker.launch("video/*") },
                onLaunchScene3DPicker = { scene3DPicker.launch(arrayOf("*/*")) }
            )
        }

        // Painel Flutuante de Subcamadas Isoladas por Quadro ("Camadas do Quadro")
        val currFramePc = uiState.project.frames.getOrNull(uiState.currentFrameIndex)
        val activeTimelineLayerNamePc = currFramePc?.layers?.getOrNull(uiState.activeLayerIndex)?.name ?: "Camada Principal"
        FrameLayersFloatingPanel(
            visible = uiState.isFrameLayersPanelOpen && !uiState.isZenModeActive,
            currentFrameIndex = uiState.currentFrameIndex,
            localLayers = currFramePc?.localLayers ?: emptyList(),
            activeLocalLayerIndex = uiState.activeLocalLayerIndex,
            activeTargetIsFrameLocalLayer = uiState.activeTargetIsFrameLocalLayer,
            activeTimelineLayerName = activeTimelineLayerNamePc,
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
            onSetLocalLayerPlacement = { idx, below -> viewModel.setFrameLocalLayerPlacement(idx, below) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        )


        // Floating Zen Mode Exit Pill (when bars are hidden, tap to restore)
        if (uiState.isZenModeActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { viewModel.setZenModeActive(false) }
                    .border(1.dp, adaptiveCyan, RoundedCornerShape(20.dp))
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .testTag("pc_exit_zen_btn"),
                color = Color(currentTheme.panel).copy(alpha = 0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = "Sair do Modo Foco",
                        tint = adaptiveCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Sair do Foco (TAB)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = adaptiveCyan
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
                    onOpenUvVertexEditor = { viewModel.openStudio3DUvEditor() },
                    onOpenSplitNodes = {
                        viewModel.closeStudio3DThreeWheels()
                        viewModel.toggleStudio3DSplitNodes()
                    },
                    onOpenFullscreenUvPaint = { viewModel.openStudio3DUvPaintScreen() },
                    onRequestImport3D = { scene3DPicker.launch(arrayOf("*/*")) },
                    onExportObjectZip = { obj ->
                        pendingExportObjId = obj.id
                        export3DObjLauncher.launch("${obj.name.ifBlank { "modelo_3d" }}.zip")
                    },
                    shadingMode = uiState.project.camera3D.shadingMode,
                    onSelectShadingMode = { viewModel.setStageShadingMode(it) },
                    showFloorGrid = uiState.project.camera3D.showFloorGrid,
                    onToggleFloorGrid = { viewModel.toggleStageFloorGrid() },
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
                    bgColor = Color(currentTheme.panel),
                    textColor = themeText,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                )
            }

            if (uiState.isStudio3DUVUnfoldOpen && selectedStudio3DObj != null && !uiState.isStudio3DUvPaintScreenOpen && !uiState.isStudio3DUvEditorOpen) {
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
                    onOpenUvVertexEditor = { viewModel.openStudio3DUvEditor() },
                    onClose = { viewModel.toggleStudio3DUVUnfold() },
                    bgColor = Color(currentTheme.panel),
                    textColor = themeText,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 14.dp)
                )
            }

            if (uiState.isStudio3DSplitNodesOpen) {
                BackHandler {
                    viewModel.closeStudio3DSplitNodes()
                }
                Studio3DSplitNodesOverlay(
                    selectedObject = selectedStudio3DObj,
                    onUpdateObject = { id, bump, tf -> viewModel.updateSceneObject3D(id, bump, tf) },
                    customPresets = uiState.customShaderNodePresets,
                    onSaveCustomPreset = { name, graph -> viewModel.saveCustomShaderNodePreset(name, graph) },
                    onApplyCustomPreset = { objId, preset -> viewModel.applyCustomShaderNodePreset(objId, preset) },
                    onDeleteCustomPreset = { presetId -> viewModel.deleteCustomShaderNodePreset(presetId) },
                    onClose = { viewModel.closeStudio3DSplitNodes() },
                    bgColor = Color(currentTheme.panel),
                    textColor = themeText,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.isStudio3DUvPaintScreenOpen) {
                Studio3DUvPaintScreen(
                    sceneObjects = studio3DObjs,
                    selectedObjectId = selectedStudio3DObj?.id,
                    onSelectObject = { viewModel.selectStageTarget(StageSelection(StageTargetType.OBJECT_3D, it)) },
                    onUpdateObject = { id, bump, tf -> viewModel.updateSceneObject3D(id, bump, tf) },
                    onPaintUVSegment = { objId, u0, v0, u1, v1 ->
                        viewModel.paintSceneObjectSurfaceSegment(objId, u0, v0, u1, v1)
                    },
                    onMergePaintLayers = { viewModel.mergeSceneObjectPaintLayers(it) },
                    onExportObjectZip = { obj ->
                        pendingExportObjId = obj.id
                        export3DObjLauncher.launch("${obj.name.ifBlank { "modelo_3d" }}.zip")
                    },
                    paintTool = uiState.studio3DPaintTool,
                    onSelectPaintTool = { viewModel.setStudio3DPaintTool(it) },
                    paintSize = uiState.studio3DPaintSize,
                    onChangePaintSize = { viewModel.setStudio3DPaintSize(it) },
                    paintOpacity = uiState.studio3DPaintOpacity,
                    onChangePaintOpacity = { viewModel.setStudio3DPaintOpacity(it) },
                    activeBrushPreset = uiState.activeBrushPreset,
                    nativeBrushPresets = uiState.nativeBrushPresets,
                    importedBrushes = uiState.importedKritaBrushes,
                    onSelectBrushPreset = { viewModel.selectBrushPreset(it) },
                    onSaveBrushCustomization = { preset, sz, op, params ->
                        viewModel.saveBrushCustomization(context, preset, sz, op, params)
                    },
                    onResetBrushCustomization = { preset ->
                        viewModel.resetBrushCustomization(context, preset)
                    },
                    onRequestImportKrita = { kritaBrushPicker.launch(arrayOf("*/*")) },
                    onDeleteImportedBrush = { viewModel.deleteImportedKritaBrush(context, it) },
                    currentColorArgb = uiState.currentColor,
                    onOpenColorPicker = { viewModel.openDialog(EditorDialog.COLOR_PICKER) },
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onOpenUvVertexEditor = { viewModel.openStudio3DUvEditor() },
                    theme = currentTheme,
                    onClose = { viewModel.closeStudio3DUvPaintScreen() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (uiState.isStudio3DUvEditorOpen) {
                Studio3DUvEditorScreen(
                    sceneObjects = studio3DObjs,
                    selectedObjectId = selectedStudio3DObj?.id,
                    onSelectObject = { viewModel.selectStageTarget(StageSelection(StageTargetType.OBJECT_3D, it)) },
                    onUpdateObject = { id, bump, tf -> viewModel.updateSceneObject3D(id, bump, tf) },
                    onCommitCustomUvs = { objId, flatUvs, coalesce -> viewModel.updateSceneObjectUvVertices(objId, flatUvs, coalesce) },
                    onResetCustomUvs = { objId -> viewModel.resetSceneObjectCustomUvs(objId) },
                    onExportObjectZip = { obj ->
                        pendingExportObjId = obj.id
                        export3DObjLauncher.launch("${obj.name.ifBlank { "modelo_3d" }}.zip")
                    },
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    theme = currentTheme,
                    onClose = { viewModel.closeStudio3DUvEditor() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        PcEditorDialogsAndModals(
            uiState = uiState,
            viewModel = viewModel,
            context = context,
            currentTheme = currentTheme,
            themeMuted = themeMuted,
            themeBorder = themeBorder,
            showThemeMenu = showThemeMenu,
            onDismissThemeMenu = { showThemeMenu = false },
            showMediaImportChoice = showMediaImportChoice,
            onDismissMediaImportChoice = { showMediaImportChoice = false },
            showOnionOpacityDialog = showOnionOpacityDialog,
            onDismissOnionOpacityDialog = { showOnionOpacityDialog = false },
            showFpsDialog = showFpsDialog,
            onDismissFpsDialog = { showFpsDialog = false },
            showDurationDialog = showDurationDialog,
            onDismissDurationDialog = { showDurationDialog = false },
            onLaunchAudioPicker = { audioPicker.launch("audio/*") },
            onLaunchVideoPicker = { videoPicker.launch("video/*") }
        )
    }
}

@Composable
private fun BoxScope.PcEditorBottomTimelineSection(
    uiState: EditorUiState,
    viewModel: KodanimaViewModel,
    context: Context,
    currentTheme: KodanimaTheme,
    themeText: Color,
    themeMuted: Color,
    themeBorder: Color,
    adaptiveCyan: Color,
    adaptiveGreen: Color,
    adaptivePink: Color,
    adaptiveOrange: Color,
    coroutineScope: CoroutineScope,
    activeFocusZone: StudioFocusZone,
    onFocusZoneChange: (StudioFocusZone) -> Unit,
    onShowThemeMenu: () -> Unit,
    onShowOnionOpacityDialog: () -> Unit,
    onShowMediaImportChoice: () -> Unit,
    onShowDurationDialog: () -> Unit,
    onShowFpsDialog: () -> Unit,
    onLaunchAudioPicker: () -> Unit,
    onLaunchVideoPicker: () -> Unit,
    onLaunchScene3DPicker: () -> Unit
) {
    var draggingCel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var movingCel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var liveMoveTick by remember { mutableStateOf<Int?>(null) }
    var snapHoverTick by remember { mutableStateOf<Int?>(null) }
    var liveDraggingHold by remember { mutableStateOf<Int?>(null) }

    val isProf = uiState.preferences.timelineLayoutMode == TimelineLayoutMode.HERITAGE_PROFISSIONAL
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
            onImport3DSceneClick = onLaunchScene3DPicker,
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
            onAddKeyframeAtTick = { tick -> viewModel.addSelectedTargetKeyframeAtTick(tick) },
            onSetTransformSpace = { viewModel.setStageTransformSpace(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isProf) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(0.85f)
                    }
                )
        )
    } else {
        val fps = uiState.project.fps.coerceAtLeast(1)
        val audioAsset = uiState.project.mediaAssets.firstOrNull { it.type == "audio" }
        val videoAsset = uiState.project.mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (isProf) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(0.85f)
                    }
                )
                .shadow(16.dp, if (isProf) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp))
                .clip(if (isProf) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp) else RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = Color(currentTheme.accent).copy(alpha = 0.45f),
                    shape = if (isProf) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp) else RoundedCornerShape(10.dp)
                )
                .background(Color(currentTheme.panel).copy(alpha = 0.98f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(currentTheme.bg).copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TimelineIconButton(icon = Icons.Default.Add, tooltip = "Nova camada", tint = themeText) {
                        viewModel.addLayer()
                    }
                    TimelineIconButton(icon = Icons.Default.Delete, tooltip = "Excluir camada", tint = themeText) {
                        viewModel.deleteLayer(uiState.activeLayerIndex)
                    }
                    TimelineIconButton(icon = Icons.Default.KeyboardArrowUp, tooltip = "Subir camada", tint = themeText) {
                        viewModel.moveLayerUp(uiState.activeLayerIndex)
                    }
                    TimelineIconButton(icon = Icons.Default.KeyboardArrowDown, tooltip = "Descer camada", tint = themeText) {
                        viewModel.moveLayerDown(uiState.activeLayerIndex)
                    }
                    val currLayer = uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers?.getOrNull(uiState.activeLayerIndex)
                    val isVis = currLayer?.isVisible ?: true
                    TimelineIconButton(
                        icon = if (isVis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        tooltip = "Visibilidade da camada",
                        tint = themeText
                    ) {
                        viewModel.toggleLayerVisibility(uiState.activeLayerIndex)
                    }
                    TimelineIconButton(icon = Icons.Default.ColorLens, tooltip = "Mudar Tema", tint = themeText) {
                        onShowThemeMenu()
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(uiState.currentColor))
                            .border(1.dp, themeText.copy(alpha = 0.5f), CircleShape)
                            .clickable { viewModel.openDialog(EditorDialog.COLOR_PICKER) }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val onionActive = uiState.onionSkinEnabled && uiState.project.onionSkin.isEnabled
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (onionActive) Color(currentTheme.accent).copy(alpha = 0.25f) else Color.Transparent)
                            .border(1.dp, if (onionActive) Color(currentTheme.accent) else themeBorder, RoundedCornerShape(4.dp))
                            .clickable { viewModel.toggleOnionSkin() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = "Onion Skin",
                            tint = if (onionActive) Color(currentTheme.accent) else themeMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "% ${(uiState.project.onionSkin.opacity * 100).toInt()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (onionActive) Color(currentTheme.accent) else themeMuted,
                            modifier = Modifier.clickable { onShowOnionOpacityDialog() }
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    TimelineIconButton(
                        icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        tooltip = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                        tint = if (uiState.isPlaying) adaptiveOrange else themeText
                    ) {
                        viewModel.togglePlayback()
                    }
                    TimelineIconButton(
                        icon = Icons.Default.Repeat,
                        tooltip = "Repetição contínua (Loop)",
                        tint = if (uiState.isLooping) Color(currentTheme.accent) else themeMuted
                    ) {
                        viewModel.toggleLoop()
                    }
                    TimelineTextButton(text = "+ Quadro", tooltip = "Adicionar quadro no final", textColor = themeText) {
                        viewModel.addNewFrame()
                    }
                    TimelineTextButton(text = "Inserir", tooltip = "Inserir quadro nesta posição", textColor = themeText) {
                        viewModel.insertFrameAtCurrentPosition()
                    }
                    TimelineTextButton(text = "✕", tooltip = "Excluir quadro atual", textColor = adaptivePink) {
                        viewModel.deleteCurrentFrame()
                    }
                    TimelineIconButton(icon = Icons.Default.Download, tooltip = "Importar Áudio / Vídeo", tint = themeText) {
                        onShowMediaImportChoice()
                    }

                    val hasAudio = uiState.project.mediaAssets.any { it.type == "audio" }
                    TimelineIconButton(
                        icon = if (hasAudio) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeMute
                        },
                        tooltip = "Áudio da Timeline",
                        tint = if (hasAudio) adaptiveGreen else themeMuted
                    ) {
                        if (!hasAudio) {
                            onLaunchAudioPicker()
                        } else {
                            Toast.makeText(context, "Áudio já carregado na timeline", Toast.LENGTH_SHORT).show()
                        }
                    }

                    val activeCel = remember(uiState.project, uiState.activeLayerIndex, uiState.currentTimelineTick) {
                        TimelineTrackEngine.resolveCelAtTick(uiState.project, uiState.activeLayerIndex, uiState.currentTimelineTick)
                    }
                    val activeHold = if (draggingCel?.first == uiState.activeLayerIndex && liveDraggingHold != null) {
                        liveDraggingHold!!
                    } else {
                        activeCel?.durationTicks ?: (uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.holdFrames ?: 1)
                    }
                    val frameDurSec = activeHold.toDouble() / fps
                    Text(
                        text = "Dur. ${String.format(java.util.Locale.US, "%.3f", frameDurSec)} (${activeHold}q)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (draggingCel != null) adaptiveCyan else themeText.copy(alpha = 0.88f),
                        modifier = Modifier
                            .clickable { onShowDurationDialog() }
                            .padding(horizontal = 4.dp)
                            .testTag("timeline_frame_dur_btn")
                    )

                    Text(
                        text = "FPS ${uiState.project.fps}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(currentTheme.accent),
                        modifier = Modifier
                            .clickable { onShowFpsDialog() }
                            .padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val isSnapActive = uiState.preferences.timelineSnapAssistant
                    TimelineIconButton(
                        icon = Icons.Default.AutoFixHigh,
                        tooltip = if (isSnapActive) "Assistente de Encaixe por Bolinhas (ATIVADO)" else "Assistente de Encaixe (DESATIVADO)",
                        tint = if (isSnapActive) adaptiveCyan else themeMuted
                    ) {
                        viewModel.toggleTimelineSnapAssistant()
                        Toast.makeText(
                            context,
                            if (!isSnapActive) "Assistente de Encaixe Ativado!" else "Assistente Desativado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    TimelineIconButton(
                        icon = Icons.Default.Videocam,
                        tooltip = "Câmera 3D & Pós-Efeitos",
                        tint = if (uiState.project.camera3D.isEnabled) adaptiveOrange else themeText
                    ) {
                        viewModel.toggleCameraTimeline()
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val totalTicks = remember(uiState.project) {
                        TimelineTrackEngine.computeTotalTicks(uiState.project, includeCameraRunway = uiState.project.camera3D.isEnabled)
                    }
                    val totalSecs = totalTicks.toDouble() / fps
                    Text(
                        text = "Total: ${uiState.project.frames.size} quadros | ${totalTicks}q (${String.format(java.util.Locale.US, "%.3f", totalSecs)}s)",
                        fontSize = 10.sp,
                        color = themeMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    TimelineIconButton(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        tooltip = "Desfazer (Ctrl+Z)",
                        tint = if (uiState.canUndo) themeText else themeMuted
                    ) {
                        if (uiState.canUndo) viewModel.undo()
                    }
                    TimelineIconButton(
                        icon = Icons.AutoMirrored.Filled.Redo,
                        tooltip = "Refazer (Ctrl+Y)",
                        tint = if (uiState.canRedo) themeText else themeMuted
                    ) {
                        if (uiState.canRedo) viewModel.redo()
                    }

                    if (abs(uiState.rotation) > 0.5f) {
                        TimelineTextButton(
                            text = "Giro ${uiState.rotation.roundToInt()}°",
                            tooltip = "Zerar giro da tela",
                            textColor = adaptiveOrange
                        ) {
                            viewModel.resetRotation()
                        }
                    }

                    TimelineTextButton(
                        text = "${(uiState.zoom * 100).toInt()}%",
                        tooltip = "Resetar zoom/câmera",
                        textColor = themeText
                    ) {
                        viewModel.resetCanvasTransform()
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(0.8.dp, adaptiveCyan.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .clickable {
                                Toast.makeText(context, "Foco: ${activeFocusZone.label} - ${activeFocusZone.hint}", Toast.LENGTH_SHORT).show()
                            },
                        color = adaptiveCyan.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = activeFocusZone.label,
                            color = adaptiveCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val nextMode = if (isProf) TimelineLayoutMode.HERITAGE_JUNIOR else TimelineLayoutMode.HERITAGE_PROFISSIONAL
                                viewModel.setTimelineLayoutMode(nextMode)
                            }
                            .testTag("toggle_timeline_layout_btn"),
                        color = Color(currentTheme.accent).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(currentTheme.accent))
                    ) {
                        Text(
                            text = if (isProf) "Profissional" else "Junior",
                            color = Color(currentTheme.accent),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    TimelineIconButton(
                        icon = Icons.Default.PhoneAndroid,
                        tooltip = "Modo Mobile",
                        tint = Color(currentTheme.text)
                    ) {
                        viewModel.setUiMode(AppUiMode.MOBILE)
                    }
                    TimelineIconButton(
                        icon = Icons.Default.Language,
                        tooltip = "Online & Servidor Local",
                        tint = if (uiState.isLocalServerActive || uiState.multiplayerRole != MultiplayerRole.NONE) adaptiveGreen else themeText
                    ) {
                        viewModel.navigateTo(AppScreen.NETWORK_STUDIO)
                    }
                    TimelineIconButton(
                        icon = Icons.Default.Settings,
                        tooltip = "Configurações do App",
                        tint = themeText
                    ) {
                        viewModel.navigateTo(AppScreen.SETTINGS)
                    }
                    TimelineIconButton(
                        icon = Icons.Default.Fullscreen,
                        tooltip = "Modo Foco / Zen (TAB)",
                        tint = if (uiState.isZenModeActive) adaptiveCyan else themeText
                    ) {
                        viewModel.toggleZenMode()
                    }
                    TimelineIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tooltip = "Voltar aos Projetos",
                        tint = Color(currentTheme.text)
                    ) {
                        if (uiState.multiplayerRole == MultiplayerRole.CLIENT) {
                            viewModel.leaveRemoteSessionAsVisitor(context, AppScreen.HOME)
                        } else {
                            viewModel.closeAndSaveCurrentProject(context)
                            viewModel.navigateTo(AppScreen.HOME)
                        }
                    }
                }

                val projectLayers = uiState.project.frames.firstOrNull()?.layers ?: listOf(Layer(name = "Camada 1"))
                val timelineBodyHeight = (24 + (projectLayers.size * 26) + 48).coerceIn(120, 220).dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(timelineBodyHeight)
                ) {
                    val layersScrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .width(135.dp)
                            .fillMaxHeight()
                            .background(Color(currentTheme.panel))
                            .border(androidx.compose.foundation.BorderStroke(1.dp, themeBorder))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            PointerEventType.Move, PointerEventType.Enter -> {
                                                onFocusZoneChange(StudioFocusZone.LAYERS_ORDER)
                                            }
                                            PointerEventType.Scroll -> {
                                                onFocusZoneChange(StudioFocusZone.LAYERS_ORDER)
                                                val scrollChange = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                                if (scrollChange != 0f) {
                                                    coroutineScope.launch {
                                                        layersScrollState.scrollBy(scrollChange * 35f)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .background(Color(currentTheme.bg))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "CAMADAS / ORDEM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = themeMuted
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(layersScrollState)
                        ) {
                            projectLayers.indices.reversed().forEach { index ->
                                val layer = projectLayers[index]
                                val isSelected = index == uiState.activeLayerIndex
                                val currFrameLayer = uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers?.getOrNull(index)
                                val isVis = currFrameLayer?.isVisible ?: layer.isVisible

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                        .background(
                                            if (isSelected) Color(currentTheme.accent).copy(alpha = 0.28f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.5.dp,
                                            color = if (isSelected) Color(currentTheme.accent) else themeBorder.copy(alpha = 0.4f)
                                        )
                                        .clickable { viewModel.selectLayer(index) }
                                        .padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isVis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Visibilidade",
                                        tint = if (isVis) (if (isSelected) Color(currentTheme.accent) else themeText.copy(alpha = 0.8f)) else themeMuted,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { viewModel.toggleLayerVisibility(index) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = layer.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(currentTheme.accent) else themeText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(Color(currentTheme.bg).copy(alpha = 0.6f))
                                .border(0.5.dp, themeBorder)
                                .clickable {
                                    if (audioAsset == null) onLaunchAudioPicker()
                                    else Toast.makeText(context, "Faixa de áudio: ${audioAsset.name}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = if (audioAsset != null) adaptiveGreen else themeMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (audioAsset != null) audioAsset.name else "Música",
                                fontSize = 10.sp,
                                color = if (audioAsset != null) adaptiveGreen else themeMuted,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(Color(currentTheme.bg).copy(alpha = 0.6f))
                                .border(0.5.dp, themeBorder)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    if (videoAsset == null) onLaunchVideoPicker()
                                    else Toast.makeText(context, "Mídia: ${videoAsset.name}", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    Icons.Default.VideoFile,
                                    contentDescription = null,
                                    tint = if (videoAsset != null) adaptiveOrange else themeMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Vídeo / ...",
                                    fontSize = 10.sp,
                                    color = if (videoAsset != null) adaptiveOrange else themeMuted
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "tr",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isVideoTransformMode) Color.White else themeText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (uiState.isVideoTransformMode) adaptiveOrange else themeBorder.copy(alpha = 0.45f))
                                        .padding(horizontal = 3.dp)
                                        .clickable {
                                            if (videoAsset != null) viewModel.toggleVideoTransformMode()
                                            else onLaunchVideoPicker()
                                        }
                                        .testTag("pc_video_tr_button")
                                )
                                Text(
                                    text = "a%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(themeBorder.copy(alpha = 0.45f))
                                        .padding(horizontal = 2.dp)
                                        .clickable {
                                            if (videoAsset != null) viewModel.updateSpecialAssetOpacity(videoAsset.id, 0.5)
                                        }
                                )
                                Text(
                                    text = "↺",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeText,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(themeBorder.copy(alpha = 0.45f))
                                        .padding(horizontal = 2.dp)
                                        .clickable {
                                            if (videoAsset != null) viewModel.resetSpecialAssetTransform(videoAsset.id)
                                        }
                                )
                            }
                        }
                    }

                    val tickWidth = (24 * uiState.timelineZoom).dp
                    val totalProjectTicks = remember(uiState.project) {
                        TimelineTrackEngine.computeTotalTicks(
                            uiState.project,
                            includeCameraRunway = uiState.project.camera3D.isEnabled
                        )
                    }
                    val numTicks = maxOf(totalProjectTicks + 24, fps * 3, 48)
                    val totalTrackWidth = (numTicks * 24 * uiState.timelineZoom).dp
                    val timelineScrollState = rememberScrollState()

                    val density = LocalDensity.current
                    val playheadTickFloat = (uiState.activeScrubTimeMs ?: uiState.playbackTimeMs)?.let {
                        TimelineTrackEngine.timeMsToTickFloat(it, fps).coerceIn(0f, (numTicks - 1).toFloat())
                    } ?: uiState.currentTimelineTick.toFloat().coerceIn(0f, (numTicks - 1).toFloat())
                    val playheadTickRounded = kotlin.math.round(playheadTickFloat).toInt()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(timelineScrollState)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(totalTrackWidth)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(Color(currentTheme.bg))
                                    .border(androidx.compose.foundation.BorderStroke(0.5.dp, themeBorder))
                                    .pointerInput(fps, uiState.timelineZoom, totalProjectTicks) {
                                        val tickPx = with(density) { tickWidth.toPx() }.coerceAtLeast(1f)
                                        val maxScrubMs = ((totalProjectTicks.coerceAtLeast(1).toDouble() / fps.toDouble()) * 1000.0).toLong()
                                        detectHorizontalDragGestures(
                                            onDragStart = { startOffset ->
                                                onFocusZoneChange(StudioFocusZone.TIMELINE_RULER)
                                                val tickPos = (startOffset.x / tickPx).coerceAtLeast(0f)
                                                val targetMs = ((tickPos / fps.toFloat()) * 1000.0).toLong().coerceIn(0L, maxScrubMs)
                                                viewModel.scrubToTimelineMs(targetMs)
                                            },
                                            onDragEnd = { viewModel.endAudioScrub() },
                                            onDragCancel = { viewModel.endAudioScrub() }
                                        ) { change, _ ->
                                            change.consume()
                                            val tickPos = (change.position.x / tickPx).coerceAtLeast(0f)
                                            val targetMs = ((tickPos / fps.toFloat()) * 1000.0).toLong().coerceIn(0L, maxScrubMs)
                                            viewModel.scrubToTimelineMs(targetMs)
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                when (event.type) {
                                                    PointerEventType.Move, PointerEventType.Enter -> {
                                                        onFocusZoneChange(StudioFocusZone.TIMELINE_RULER)
                                                    }
                                                    PointerEventType.Scroll -> {
                                                        onFocusZoneChange(StudioFocusZone.TIMELINE_RULER)
                                                        val scrollChange = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                                        if (scrollChange != 0f) {
                                                            coroutineScope.launch {
                                                                timelineScrollState.scrollBy(scrollChange * 60f)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (0 until numTicks).forEach { t ->
                                    val isSnapHover = snapHoverTick == t
                                    val isSecondMark = t % fps == 0
                                    val isPlayheadTick = t == playheadTickRounded

                                    Column(
                                        modifier = Modifier
                                            .width(tickWidth)
                                            .fillMaxHeight()
                                            .clickable {
                                                viewModel.goToTimelineTick(t)
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(13.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSecondMark) {
                                                Text(
                                                    text = "${t / fps}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(currentTheme.text).copy(alpha = 0.9f)
                                                )
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(15.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isPlayheadTick) {
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = "Agulha da Timeline (Pinça)",
                                                    tint = adaptiveCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(if (isSnapHover) 7.dp else if (isSecondMark) 4.dp else 2.5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                isSnapHover -> adaptiveCyan
                                                                isSecondMark -> Color(currentTheme.accent).copy(alpha = 0.85f)
                                                                else -> themeMuted.copy(alpha = 0.45f)
                                                            }
                                                        )
                                                        .then(
                                                            if (isSnapHover) Modifier.border(1.dp, themeText, CircleShape)
                                                            else Modifier
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(layersScrollState)
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                when (event.type) {
                                                    PointerEventType.Move, PointerEventType.Enter -> {
                                                        onFocusZoneChange(StudioFocusZone.TIMELINE_FRAMES)
                                                    }
                                                    PointerEventType.Scroll -> {
                                                        onFocusZoneChange(StudioFocusZone.TIMELINE_FRAMES)
                                                        val scrollChange = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                                        if (scrollChange < 0f) {
                                                            viewModel.zoomTimeline(1.15f)
                                                        } else if (scrollChange > 0f) {
                                                            viewModel.zoomTimeline(0.85f)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                projectLayers.indices.reversed().forEach { lIdx ->
                                    val isCurrentLayer = lIdx == uiState.activeLayerIndex
                                    val layerCels = remember(uiState.project, lIdx) {
                                        TimelineTrackEngine.computeLayerCels(uiState.project, lIdx)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(26.dp)
                                            .background(
                                                if (isCurrentLayer) Color(currentTheme.panel).copy(alpha = 0.5f)
                                                else Color(currentTheme.bg).copy(alpha = 0.25f)
                                            )
                                            .border(0.5.dp, themeBorder)
                                            .pointerInput(lIdx, numTicks, uiState.timelineZoom) {
                                                val tickPx = with(density) { tickWidth.toPx() }.coerceAtLeast(1f)
                                                detectTapGestures { offset ->
                                                    val tappedTick = (offset.x / tickPx).toInt().coerceIn(0, (numTicks - 1).coerceAtLeast(0))
                                                    viewModel.activateOrAddCelOnLayerAtTick(lIdx, tappedTick)
                                                }
                                            }
                                    ) {
                                        layerCels.forEach { cel ->
                                            val celKey = lIdx to cel.frameIndex
                                            val displayStart = if (movingCel == celKey && liveMoveTick != null) {
                                                liveMoveTick!!
                                            } else cel.startTick
                                            val displayHold = if (draggingCel == celKey && liveDraggingHold != null) {
                                                liveDraggingHold!!
                                            } else cel.durationTicks
                                            val cellWidth = (tickWidth * displayHold).coerceAtLeast(tickWidth)
                                            val isCurrentFrame = uiState.currentTimelineTick in displayStart until (displayStart + displayHold)
                                            val isCurrentCell = isCurrentLayer && isCurrentFrame
                                            val hasStrokes = cel.layer.strokes.isNotEmpty() || cel.layer.imageAttachment != null

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = tickWidth * displayStart)
                                                    .width(cellWidth)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 0.5.dp, vertical = 2.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(
                                                        when {
                                                            movingCel == celKey -> adaptiveGreen.copy(alpha = 0.42f)
                                                            isCurrentCell -> Color(currentTheme.accent).copy(alpha = 0.5f)
                                                            isCurrentFrame -> Color(currentTheme.accent).copy(alpha = 0.2f)
                                                            isCurrentLayer -> Color(currentTheme.panel).copy(alpha = 0.8f)
                                                            else -> themeText.copy(alpha = 0.08f)
                                                        }
                                                    )
                                                    .border(
                                                        width = if (movingCel == celKey || isCurrentCell) 1.5.dp else if (isCurrentFrame) 1.dp else 0.5.dp,
                                                        color = when {
                                                            movingCel == celKey -> adaptiveGreen
                                                            isCurrentCell -> Color(currentTheme.accent)
                                                            isCurrentFrame -> Color(currentTheme.accent).copy(alpha = 0.6f)
                                                            else -> Color.Transparent
                                                        },
                                                        shape = RoundedCornerShape(3.dp)
                                                    )
                                                    .pointerInput(celKey, cel.startTick, numTicks, uiState.timelineZoom) {
                                                        var accum = 0f
                                                        val unitPx = with(density) { tickWidth.toPx() }.coerceAtLeast(1f)
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                accum = 0f
                                                                movingCel = celKey
                                                                liveMoveTick = cel.startTick
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                accum += dragAmount.x
                                                                val delta = (accum / unitPx).toInt()
                                                                liveMoveTick = (cel.startTick + delta).coerceIn(0, (numTicks - 1).coerceAtLeast(0))
                                                                if (uiState.preferences.timelineSnapAssistant) {
                                                                    snapHoverTick = liveMoveTick
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                val dst = liveMoveTick ?: cel.startTick
                                                                movingCel = null
                                                                liveMoveTick = null
                                                                snapHoverTick = null
                                                                if (dst != cel.startTick) {
                                                                    viewModel.moveLayerCelToTick(lIdx, cel.frameIndex, dst)
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                movingCel = null
                                                                liveMoveTick = null
                                                                snapHoverTick = null
                                                            }
                                                        )
                                                    }
                                                    .clickable {
                                                        viewModel.selectLayerCel(lIdx, cel.frameIndex, cel.startTick)
                                                    }
                                                    .testTag("pc_cel_${lIdx}_${cel.frameIndex}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                ) {
                                                    if (cel.frameIndex == 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(7.dp)
                                                                .clip(RoundedCornerShape(1.dp))
                                                                .background(if (hasStrokes) NeonGreen else NeonGreen.copy(alpha = 0.5f))
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "${cel.frameIndex + 1}",
                                                            fontSize = 9.sp,
                                                            fontWeight = if (isCurrentCell) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isCurrentCell) themeText else themeMuted
                                                        )
                                                    }

                                                    if (hasStrokes && cel.frameIndex != 0) {
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(currentTheme.accent))
                                                        )
                                                    }

                                                    if (displayHold > 1) {
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "${displayHold}q",
                                                            fontSize = 8.sp,
                                                            color = Color(currentTheme.accent).copy(alpha = 0.9f),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                // Independent per-layer right-edge stretch handle on EVERY layer
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .padding(end = 1.dp)
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when {
                                                                draggingCel == celKey -> adaptiveCyan
                                                                isCurrentCell -> adaptiveCyan
                                                                isCurrentLayer -> themeText.copy(alpha = 0.55f)
                                                                else -> themeText.copy(alpha = 0.30f)
                                                            }
                                                        )
                                                        .border(1.dp, Color(currentTheme.panel).copy(alpha = 0.9f), CircleShape)
                                                        .pointerInput(celKey, cel.durationTicks, cel.startTick, uiState.timelineZoom) {
                                                            var accum = 0f
                                                            detectHorizontalDragGestures(
                                                                onDragStart = {
                                                                    draggingCel = celKey
                                                                    accum = 0f
                                                                    liveDraggingHold = cel.durationTicks
                                                                },
                                                                onDragEnd = {
                                                                    val finalHold = liveDraggingHold ?: cel.durationTicks
                                                                    viewModel.setLayerCelDuration(lIdx, cel.frameIndex, finalHold)
                                                                    draggingCel = null
                                                                    snapHoverTick = null
                                                                    liveDraggingHold = null
                                                                },
                                                                onDragCancel = {
                                                                    draggingCel = null
                                                                    snapHoverTick = null
                                                                    liveDraggingHold = null
                                                                }
                                                            ) { change, dragAmount ->
                                                                change.consume()
                                                                accum += dragAmount
                                                                val unitPx = tickWidth.toPx().coerceAtLeast(1f)
                                                                val delta = kotlin.math.round(accum / unitPx).toInt()
                                                                val candidateHold = (cel.durationTicks + delta).coerceIn(1, 240)
                                                                liveDraggingHold = candidateHold

                                                                if (uiState.preferences.timelineSnapAssistant) {
                                                                    snapHoverTick = cel.startTick + candidateHold
                                                                }
                                                            }
                                                        }
                                                        .testTag("pc_stretch_${lIdx}_${cel.frameIndex}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .height(24.dp)
                                    .fillMaxWidth()
                                    .background(Color(currentTheme.bg).copy(alpha = 0.7f))
                                    .border(0.5.dp, themeBorder)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (audioAsset != null) {
                                    val peaks = if (audioAsset.waveformPeaks.isNotEmpty()) {
                                        audioAsset.waveformPeaks
                                    } else {
                                        listOf(0.25f, 0.55f, 0.8f, 0.4f, 0.65f, 0.9f, 0.5f, 0.7f)
                                    }
                                    val audioTicks = if (audioAsset.durationSeconds > 0.0) {
                                        (audioAsset.durationSeconds * fps).toFloat().coerceIn(4f, numTicks.toFloat())
                                    } else {
                                        totalProjectTicks.coerceAtLeast(12).toFloat()
                                    }
                                    val audioWidthDp = tickWidth * audioTicks
                                    val barColor = if (audioAsset.audioMuted) themeMuted else adaptiveGreen

                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier
                                            .width(audioWidthDp)
                                            .height(18.dp)
                                    ) {
                                        val w = size.width
                                        val h = size.height
                                        val midY = h * 0.5f
                                        val barCount = ((w / 4f).toInt()).coerceIn(peaks.size, 320)
                                        val stepX = w / barCount.toFloat()
                                        val strokeW = (stepX * 0.56f).coerceIn(1.5f, 3.2f)
                                        for (i in 0 until barCount) {
                                            val peakIdx = ((i.toFloat() / barCount.toFloat()) * peaks.size).toInt()
                                                .coerceIn(0, peaks.lastIndex)
                                            val amp = peaks[peakIdx].coerceIn(0.08f, 1f)
                                            val halfH = (amp * h * 0.46f).coerceAtLeast(1.5f)
                                            val x = i * stepX + stepX * 0.5f
                                            drawLine(
                                                color = barColor.copy(alpha = 0.88f),
                                                start = androidx.compose.ui.geometry.Offset(x, midY - halfH),
                                                end = androidx.compose.ui.geometry.Offset(x, midY + halfH),
                                                strokeWidth = strokeW,
                                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Nenhuma trilha de áudio carregada",
                                        fontSize = 9.sp,
                                        color = themeMuted,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .height(24.dp)
                                    .fillMaxWidth()
                                    .background(Color(currentTheme.bg).copy(alpha = 0.6f))
                                    .border(0.5.dp, themeBorder)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (videoAsset != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(adaptiveOrange.copy(alpha = 0.25f))
                                            .border(1.dp, adaptiveOrange, RoundedCornerShape(3.dp)),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "Mídia de Referência: ${videoAsset.name} (Opacidade ${videoAsset.opacityPercent}%, Escala ${videoAsset.scalePercent.toInt()}%)",
                                            fontSize = 9.sp,
                                            color = adaptiveOrange,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Nenhuma mídia de vídeo/referência carregada",
                                        fontSize = 9.sp,
                                        color = themeMuted,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        val playheadOffset = (tickWidth * playheadTickFloat) + (tickWidth / 2)
                        Box(
                            modifier = Modifier
                                .padding(start = playheadOffset)
                                .width(1.8.dp)
                                .fillMaxHeight()
                                .background(adaptiveCyan.copy(alpha = 0.92f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PcEditorDialogsAndModals(
    uiState: EditorUiState,
    viewModel: KodanimaViewModel,
    context: Context,
    currentTheme: KodanimaTheme,
    themeMuted: Color,
    themeBorder: Color,
    showThemeMenu: Boolean,
    onDismissThemeMenu: () -> Unit,
    showMediaImportChoice: Boolean,
    onDismissMediaImportChoice: () -> Unit,
    showOnionOpacityDialog: Boolean,
    onDismissOnionOpacityDialog: () -> Unit,
    showFpsDialog: Boolean,
    onDismissFpsDialog: () -> Unit,
    showDurationDialog: Boolean,
    onDismissDurationDialog: () -> Unit,
    onLaunchAudioPicker: () -> Unit,
    onLaunchVideoPicker: () -> Unit
) {
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

    when (uiState.activeDialog) {
        EditorDialog.LAYERS -> {
            LayersBottomSheet(
                layers = uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers ?: emptyList(),
                activeLayerIndex = uiState.activeLayerIndex,
                mediaAssets = uiState.project.mediaAssets,
                onSelectLayer = { viewModel.setActiveLayer(it) },
                onAddLayer = { viewModel.addLayer() },
                onDeleteActiveLayer = { viewModel.deleteActiveLayer() },
                onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                onToggleLock = { viewModel.toggleLayerLock(it) },
                onChangeOpacity = { idx, op -> viewModel.setLayerOpacity(idx, op) },
                onImportAudio = onLaunchAudioPicker,
                onImportVideo = onLaunchVideoPicker,
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
                onColorSelected = { newColor ->
                    viewModel.setColor(newColor, recordToRecents = false)
                },
                onDismiss = {
                    viewModel.recordRecentColor(uiState.currentColor)
                    viewModel.closeDialog()
                }
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
        else -> {}
    }

    if (showThemeMenu) {
        AlertDialog(
            onDismissRequest = onDismissThemeMenu,
            title = { Text("Escolher Tema da Interface") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KodanimaTheme.values().forEach { th ->
                        val isSel = th == currentTheme
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(th.accent).copy(alpha = 0.25f) else Color(th.panel))
                                .border(1.dp, if (isSel) Color(th.accent) else themeBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.setTheme(th)
                                    onDismissThemeMenu()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(th.bg))
                                        .border(1.5.dp, Color(th.accent), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = th.namePt,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = Color(th.text)
                                )
                            }
                            if (isSel) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(th.accent))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissThemeMenu) { Text("Fechar") }
            }
        )
    }

    if (showMediaImportChoice) {
        AlertDialog(
            onDismissRequest = onDismissMediaImportChoice,
            title = { Text("Importar Mídia para a Timeline") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onDismissMediaImportChoice()
                            onLaunchAudioPicker()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Audiotrack, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Faixa de Áudio (.mp3, .wav, .aac)")
                    }
                    Button(
                        onClick = {
                            onDismissMediaImportChoice()
                            onLaunchVideoPicker()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vídeo ou GIF de Referência")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissMediaImportChoice) { Text("Cancelar") }
            }
        )
    }

    if (showOnionOpacityDialog) {
        val initialOpacity = remember(showOnionOpacityDialog) { uiState.project.onionSkin.opacity }
        var sliderVal by remember(showOnionOpacityDialog) { mutableStateOf(initialOpacity) }
        AlertDialog(
            onDismissRequest = onDismissOnionOpacityDialog,
            title = { Text("Opacidade da Camada Cebola") },
            text = {
                Column {
                    Text("Ajuste ao vivo a opacidade dos quadros anteriores e próximos:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = sliderVal.coerceIn(0.05f, 1.0f),
                        onValueChange = {
                            sliderVal = it
                            viewModel.setOnionSkinOpacity(it)
                        },
                        valueRange = 0.05f..1.0f
                    )
                    Text(
                        text = "${(sliderVal * 100).roundToInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = Color(currentTheme.accent)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setOnionSkinOpacity(sliderVal)
                    onDismissOnionOpacityDialog()
                }) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setOnionSkinOpacity(initialOpacity)
                    onDismissOnionOpacityDialog()
                }) { Text("Cancelar") }
            }
        )
    }

    if (showFpsDialog) {
        var selectedFps by remember { mutableStateOf(uiState.project.fps) }
        AlertDialog(
            onDismissRequest = onDismissFpsDialog,
            title = { Text("Taxa de Quadros por Segundo (FPS)") },
            text = {
                Column {
                    listOf(12, 24, 30, 60).forEach { fps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFps = fps }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$fps FPS",
                                fontSize = 14.sp,
                                fontWeight = if (selectedFps == fps) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedFps == fps) Color(currentTheme.accent) else Color(currentTheme.text)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setProjectFps(selectedFps)
                    onDismissFpsDialog()
                }) {
                    Text("Confirmar")
                }
            }
        )
    }

    if (showDurationDialog) {
        val activeLayer = uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.layers?.getOrNull(uiState.activeLayerIndex)
        val activeLayerLabel = activeLayer?.name ?: "Camada ${uiState.activeLayerIndex + 1}"
        val currHold = TimelineTrackEngine.resolveCelAtTick(uiState.project, uiState.activeLayerIndex, uiState.currentTimelineTick)?.durationTicks
            ?: activeLayer?.celHoldTicks
            ?: (uiState.project.frames.getOrNull(uiState.currentFrameIndex)?.holdFrames ?: 1)
        var inputHoldText by remember { mutableStateOf(currHold.toString()) }
        var applyToAll by remember { mutableStateOf(false) }
        val parsedHold = inputHoldText.toIntOrNull() ?: currHold
        val calcSec = parsedHold / uiState.project.fps.toFloat()

        AlertDialog(
            onDismissRequest = onDismissDurationDialog,
            title = { Text("Duração da Célula ($activeLayerLabel)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Defina a duração independente do quadro ${uiState.currentFrameIndex + 1} na camada $activeLayerLabel (ou aplique a todos):",
                        fontSize = 12.sp,
                        color = Color(currentTheme.text).copy(alpha = 0.8f)
                    )

                    OutlinedTextField(
                        value = inputHoldText,
                        onValueChange = { inputHoldText = it.filter { char -> char.isDigit() }.take(3) },
                        label = { Text("Número de Quadros") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    Text(
                        text = "= ${String.format("%.3f", calcSec)} segundos a ${uiState.project.fps} FPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(currentTheme.accent)
                    )

                    Text(
                        text = "Predefinições Rápidas:",
                        fontSize = 11.sp,
                        color = themeMuted
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 3, 4, 6, 12, 24).forEach { preset ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (parsedHold == preset) Color(currentTheme.accent).copy(alpha = 0.3f) else Color(currentTheme.panel))
                                    .border(1.dp, if (parsedHold == preset) Color(currentTheme.accent) else themeBorder, RoundedCornerShape(6.dp))
                                    .clickable { inputHoldText = preset.toString() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${preset}q",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (parsedHold == preset) Color(currentTheme.accent) else Color(currentTheme.text)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyToAll = !applyToAll }
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Aplicar a todos os quadros da animação",
                            fontSize = 12.sp,
                            color = Color(currentTheme.text)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalHold = parsedHold.coerceIn(1, 300)
                        if (applyToAll) {
                            viewModel.setAllFramesHoldDuration(finalHold)
                            Toast.makeText(context, "Duração de ${finalHold}q aplicada a todos os quadros!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setLayerCelDuration(uiState.activeLayerIndex, uiState.currentFrameIndex, finalHold)
                            Toast.makeText(context, "$activeLayerLabel • Q${uiState.currentFrameIndex + 1}: ${finalHold}q (${String.format("%.3f", finalHold / uiState.project.fps.toFloat())}s)", Toast.LENGTH_SHORT).show()
                        }
                        onDismissDurationDialog()
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDurationDialog) { Text("Cancelar") }
            }
        )
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

@Composable
private fun DesktopCustomToolButton(
    tooltip: String,
    isSelected: Boolean,
    accentColor: Color,
    defaultTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    iconContent: @Composable (Color) -> Unit
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) accentColor.copy(alpha = 0.25f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = if (isSelected) accentColor else Color.Transparent
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            iconContent(if (isSelected) accentColor else defaultTint)
        }
    }
}

@Composable
private fun DesktopToolButton(
    icon: ImageVector,
    tooltip: String,
    isSelected: Boolean,
    accentColor: Color,
    defaultTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) accentColor.copy(alpha = 0.25f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = if (isSelected) accentColor else Color.Transparent
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = tooltip,
                tint = if (isSelected) accentColor else defaultTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TimelineIconButton(
    icon: ImageVector,
    tooltip: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = tooltip,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun TimelineTextButton(
    text: String,
    tooltip: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
