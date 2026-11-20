package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.AppUiMode
import com.example.model.BrushPreset
import com.example.model.MultiplayerRole
import com.example.model.ToolType
import com.example.ui.components.BrushStudioDrawer
import com.example.ui.components.CanvasView
import com.example.ui.components.CollaboratorHorizontalStrip
import com.example.ui.components.ColorPickerDialog
import com.example.ui.components.ExportDialog
import com.example.ui.components.ExtraToolsFloatingMenu
import com.example.ui.components.FloatingReferenceControlsBar
import com.example.ui.components.FloatingReferenceImageSurface
import com.example.ui.components.FrameLayersFloatingPanel
import com.example.ui.components.FriendBrushOfferBanner
import com.example.ui.components.KritaBrushStrokeWaveformPreview
import com.example.ui.components.LayersBottomSheet
import com.example.ui.components.ProcreateBrushPickerSheet
import com.example.ui.components.ProjectSettingsDialog
import com.example.ui.components.ProjectsListDialog
import com.example.ui.components.QuickCopyPasteGestureMenu
import com.example.ui.components.ReferenceImageChoiceDialog
import com.example.ui.components.ThreeWheelsIcon
import com.example.ui.components.TimelineBar
import com.example.ui.components.TimelineLayerImageTransformHud
import com.example.ui.components.ToolboxPalette
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
            .background(bgColor),
        containerColor = bgColor,
        bottomBar = {
            if (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideTimeline) {
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
                    onChangeOnionSkinOpacity = { viewModel.setOnionSkinOpacity(it) }
                )
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
            if (isFloatingRefBelow) {
                FloatingReferenceImageSurface(
                    refState = uiState.referenceImage,
                    theme = currentTheme,
                    onTransformDelta = { dx, dy, zoomDelta, rotDelta ->
                        viewModel.updateFloatingReferenceOffset(dx, dy)
                        if (abs(zoomDelta - 1f) > 0.002f) {
                            viewModel.updateFloatingReferenceScale(uiState.referenceImage.scale * zoomDelta)
                        }
                        if (abs(rotDelta) > 0.1f) {
                            viewModel.rotateFloatingReference(rotDelta)
                        }
                    }
                )
            }

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
                onTransformChange = { z, px, py, r ->
                    viewModel.updateTransform(z, px, py, r)
                },
                onStrokeCompleted = { stroke ->
                    viewModel.addStrokeToCurrentLayer(stroke)
                },
                onEyedropperColorPicked = { pickedColor ->
                    viewModel.setColor(pickedColor)
                    viewModel.selectTool(ToolType.PENCIL)
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
                isFloatingRefBelowCanvas = isFloatingRefBelow
            )

            if (uiState.referenceImage.isVisible && uiState.referenceImage.renderAboveCanvas) {
                FloatingReferenceImageSurface(
                    refState = uiState.referenceImage,
                    theme = currentTheme,
                    onTransformDelta = { dx, dy, zoomDelta, rotDelta ->
                        viewModel.updateFloatingReferenceOffset(dx, dy)
                        if (abs(zoomDelta - 1f) > 0.002f) {
                            viewModel.updateFloatingReferenceScale(uiState.referenceImage.scale * zoomDelta)
                        }
                        if (abs(rotDelta) > 0.1f) {
                            viewModel.rotateFloatingReference(rotDelta)
                        }
                    }
                )
            }

            // Unobstructed Top HUD Stack (Collaborators + Procreate Brush Trigger — zero top bar clutter)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 58.dp, end = 58.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Horizontal Compact Collaborators Strip (Never covered by toolbars, hideable in Settings)
                CollaboratorHorizontalStrip(
                    connectedFriends = uiState.connectedFriends,
                    showHud = uiState.preferences.showCanvasCollaboratorsHud,
                    fallbackPingMs = uiState.currentPingMs,
                    theme = currentTheme
                )

                if (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideBrushBar) {
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
                                    text = "${uiState.activeBrushPreset.shortLabel} • ${uiState.brushSize.toInt()}px",
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
                        if (abs(uiState.rotation) > 0.5f) {
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

                FriendBrushOfferBanner(
                    offer = uiState.pendingFriendBrushOffer,
                    theme = currentTheme,
                    onAccept = {
                        viewModel.acceptFriendBrushOffer(context)
                        Toast.makeText(context, "Brush baixado para seus Importados!", Toast.LENGTH_SHORT).show()
                    },
                    onDecline = { viewModel.declineFriendBrushOffer() }
                )

                // Floating Mobile Eraser Options Toolbar (when ToolType.ERASER is active)
                if (uiState.selectedTool == ToolType.ERASER) {
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
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Borracha",
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

                            Text(
                                text = "${uiState.brushSize.toInt()}px",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Slider(
                                value = uiState.brushSize,
                                onValueChange = { viewModel.setBrushSize(it) },
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

                // Floating FlipaClip-style Selection Actions Toolbar
                if (uiState.activeSelection.isActive) {
                    Surface(
                        modifier = Modifier
                            .shadow(10.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.5.dp, accentColor, RoundedCornerShape(20.dp))
                            .testTag("mobile_flipaclip_selection_toolbar"),
                        color = panelColor.copy(alpha = 0.96f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                if (uiState.isVideoTransformMode && activeVideoAsset != null) {
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
                if (activeLayerImageTarget != null && activeLayerImgAttachment != null) {
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

                if (uiState.referenceImage.isVisible) {
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
                        onResetTransform = { viewModel.resetFloatingReferenceTransform() },
                        onDeleteWindow = { viewModel.clearReferenceImage() }
                    )
                }

                // Procreate-style Krita Brush Library Drawer (12 Native + Imported .kpp/.bundle)
                ProcreateBrushPickerSheet(
                    visible = showProcreateBrushes,
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
                    onDismiss = { showProcreateBrushes = false }
                )
            }

            // Floating Left Toolbox Palette (Thumb-friendly touch tools + 3-wheels extra tools toggle)
            if (!uiState.isZenModeActive || !uiState.preferences.zenModeSettings.hideToolbar) {
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
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                )
            }

            // Floating Mobile Brush Studio Configurator Drawer
            BrushStudioDrawer(
                visible = uiState.isBrushStudioOpen && !showExtraToolsMenu,
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
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 58.dp)
            )

            // Painel Flutuante de Subcamadas Isoladas por Quadro ("Camadas do Quadro")
            val currFrameObj = uiState.project.frames.getOrNull(uiState.currentFrameIndex)
            val activeTimelineLayerName = currFrameObj?.layers?.getOrNull(uiState.activeLayerIndex)?.name ?: "Camada Principal"
            FrameLayersFloatingPanel(
                visible = uiState.isFrameLayersPanelOpen,
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
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
            )

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
                onDismiss = { viewModel.closeDialog() }
            )
        }
        EditorDialog.COLOR_PICKER -> {
            ColorPickerDialog(
                initialColor = uiState.currentColor,
                recentColors = uiState.recentColors,
                onColorSelected = { viewModel.setColor(it) },
                onDismiss = { viewModel.closeDialog() }
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
}
