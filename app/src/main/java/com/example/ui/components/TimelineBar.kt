package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.TimelineTrackEngine
import com.example.model.AnimationProject
import com.example.model.Frame
import com.example.model.KodanimaTheme
import com.example.model.Layer
import com.example.model.SpecialMediaAsset
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Mobile Timeline Bar with:
 * - Time Ruler ("Régua") & Draggable Cyan Playhead ("Pinça") with real-time Lip-Sync Audio Scrubbing.
 * - Proportional Frame Cells reflecting each frame's `holdFrames` (matches PC timeline timing).
 * - Compact "Modificar tempo" mini-tray on frame tap -> grows/shrinks frame cell live & closes on apply.
 * - Audio Waveform ("gravetinhos") track directly synced underneath when an audio track is loaded.
 */
@Composable
fun TimelineBar(
    frames: List<Frame>,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    onionSkinEnabled: Boolean,
    layerCount: Int,
    fps: Int,
    onSelectFrame: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNextFrame: () -> Unit,
    onPrevFrame: () -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onToggleOnionSkin: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenSettings: () -> Unit,
    theme: KodanimaTheme = KodanimaTheme.ROXO_BLACK,
    mediaAssets: List<SpecialMediaAsset> = emptyList(),
    activeScrubTimeMs: Long? = null,
    onSetFrameHold: (Int, Int) -> Unit = { _, _ -> },
    onScrubToMs: (Long) -> Unit = {},
    onEndScrub: () -> Unit = {},
    onionSkinOpacity: Float = 0.25f,
    onChangeOnionSkinOpacity: ((Float) -> Unit)? = null,
    layers: List<Layer> = frames.getOrNull(currentFrameIndex)?.layers ?: emptyList(),
    activeLayerIndex: Int = 0,
    currentTimelineTick: Int = currentFrameIndex,
    playbackTimeMs: Long? = null,
    onSelectLayerCel: (layerIndex: Int, frameIndex: Int, tick: Int) -> Unit = { _, fIdx, _ -> onSelectFrame(fIdx) },
    onSetLayerCelHold: (layerIndex: Int, frameIndex: Int, holdTicks: Int) -> Unit = { _, fIdx, hold -> onSetFrameHold(fIdx, hold) },
    onMoveLayerCel: (layerIndex: Int, frameIndex: Int, targetTick: Int) -> Unit = { _, _, _ -> },
    onAddCelAtTick: (layerIndex: Int, tick: Int) -> Unit = { _, _ -> onAddFrame() },
    onSelectTick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val tracksVerticalScrollState = rememberScrollState()
    val density = LocalDensity.current

    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val secondaryAccent = Color(theme.secondaryAccent)
    val onAccentColor = if (theme.isLight) Color.White else bgColor
    val textColor = Color(theme.text)
    val subtleBorder = accentColor.copy(alpha = 0.3f)

    // Mini "Modificar tempo" tray & Onion Skin quick opacity tray
    var showTimingTray by remember { mutableStateOf(false) }
    var showOnionOpacityTray by remember { mutableStateOf(false) }
    var editingLayerIdx by remember { mutableIntStateOf(activeLayerIndex) }
    var editingFrameIdx by remember { mutableIntStateOf(currentFrameIndex) }
    var draftHold by remember {
        mutableIntStateOf(
            frames.getOrNull(currentFrameIndex)?.layers?.getOrNull(activeLayerIndex)?.celHoldTicks
                ?: frames.getOrNull(currentFrameIndex)?.holdFrames
                ?: 1
        )
    }

    // Per-cel live stretch & move drag feedback (never leaks into sibling timeline layers)
    var draggingCel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var liveDraggingHold by remember { mutableStateOf<Int?>(null) }
    var movingCel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var liveMoveTick by remember { mutableStateOf<Int?>(null) }

    val audioAsset = mediaAssets.firstOrNull { it.type == "audio" }
    val safeFps = max(1, fps)
    val trackProject = remember(frames, safeFps) {
        AnimationProject(fps = safeFps, frames = frames)
    }
    val effectiveLayers = remember(layers, frames) {
        layers.ifEmpty { frames.firstOrNull()?.layers ?: listOf(Layer(name = "Camada 1")) }
    }
    val totalTicks = remember(trackProject) {
        TimelineTrackEngine.computeTotalTicks(trackProject, includeCameraRunway = false)
    }
    val animDurationMs = remember(totalTicks, safeFps) { (totalTicks * 1000L) / safeFps }
    val audioDurationMs = ((audioAsset?.durationSeconds ?: 0.0) * 1000.0).toLong()
    val timelineTotalMs = max(animDurationMs, audioDurationMs).coerceAtLeast(1000L)

    // Exact uniform tick width so ruler, all independent layer tracks, and Cyan Pinca match 1:1
    val dpPerTick = 32f
    val minRulerTicks = remember(totalTicks, timelineTotalMs, safeFps) {
        val audioTicks = ((audioDurationMs * safeFps) / 1000f).roundToInt()
        maxOf(totalTicks + 6, audioTicks + 4, 10)
    }
    val totalTrackWidthDp = (minRulerTicks * dpPerTick).coerceAtLeast(240f)
    val totalTrackWidthPx = with(density) { totalTrackWidthDp.dp.toPx() }

    val currentTimeMs = activeScrubTimeMs ?: playbackTimeMs ?: TimelineTrackEngine.tickToTimeMs(currentTimelineTick, safeFps)
    val pinçaTickFloat = remember(activeScrubTimeMs, playbackTimeMs, currentTimelineTick, safeFps, minRulerTicks) {
        val raw = (activeScrubTimeMs ?: playbackTimeMs)?.let {
            TimelineTrackEngine.timeMsToTickFloat(it, safeFps)
        } ?: currentTimelineTick.toFloat()
        raw.coerceIn(0f, (minRulerTicks - 1).toFloat())
    }

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    // Keep Pinca & active tick smoothly visible during playback / stepping
    LaunchedEffect(currentTimelineTick, isPlaying) {
        if (activeScrubTimeMs == null) {
            val targetPx = with(density) { (currentTimelineTick * dpPerTick).dp.toPx() }.toInt()
            val halfView = (viewportWidthPx * 0.35f).toInt()
            scrollState.animateScrollTo((targetPx - halfView).coerceAtLeast(0))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = panelColor,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, subtleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Compact "Modificar tempo" Tray (appears above the timeline when tapping a frame)
            AnimatedVisibility(visible = showTimingTray && editingFrameIdx in frames.indices) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .testTag("mobile_modify_timing_tray"),
                    color = bgColor.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val editLayerName = effectiveLayers.getOrNull(editingLayerIdx)?.name ?: "Camada ${editingLayerIdx + 1}"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "$editLayerName • Quadro ${editingFrameIdx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sec = String.format(java.util.Locale.US, "%.2fs", draftHold.toFloat() / safeFps)
                                Text(
                                    text = "($sec)",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(panelColor)
                                        .border(1.dp, subtleBorder, RoundedCornerShape(6.dp))
                                        .clickable { draftHold = (draftHold - 1).coerceAtLeast(1) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir tempo", tint = textColor, modifier = Modifier.size(14.dp))
                                }

                                Text(
                                    text = "${draftHold}q",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(panelColor)
                                        .border(1.dp, subtleBorder, RoundedCornerShape(6.dp))
                                        .clickable { draftHold = (draftHold + 1).coerceAtMost(240) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar tempo", tint = textColor, modifier = Modifier.size(14.dp))
                                }

                                // Apply & close tray (updates ONLY editingLayerIdx)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onSetLayerCelHold(editingLayerIdx, editingFrameIdx, draftHold)
                                            showTimingTray = false
                                        }
                                        .testTag("mobile_apply_timing_button"),
                                    color = accentColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = onAccentColor, modifier = Modifier.size(13.dp))
                                        Text("Aplicar", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = onAccentColor)
                                    }
                                }

                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Fechar bandeja",
                                    tint = textColor.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { showTimingTray = false }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf(1, 2, 3, 4, 6, 8, 12, 24).forEach { presetHold ->
                                val active = draftHold == presetHold
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) accentColor.copy(alpha = 0.25f) else panelColor)
                                        .border(
                                            width = 1.dp,
                                            color = if (active) accentColor else subtleBorder,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            draftHold = presetHold
                                            onSetLayerCelHold(editingLayerIdx, editingFrameIdx, presetHold)
                                            showTimingTray = false
                                        }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${presetHold}q",
                                        fontSize = 10.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) accentColor else textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Inline live Onion Skin Opacity slider tray for Mobile
            AnimatedVisibility(visible = onionSkinEnabled && showOnionOpacityTray && onChangeOnionSkinOpacity != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .border(1.dp, NeonPink.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .testTag("mobile_onion_opacity_tray"),
                    color = bgColor.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Opac. Cebola",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )
                        Slider(
                            value = onionSkinOpacity.coerceIn(0.05f, 1.0f),
                            onValueChange = { onChangeOnionSkinOpacity?.invoke(it) },
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .testTag("mobile_onion_opacity_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonPink,
                                activeTrackColor = NeonPink
                            )
                        )
                        Text(
                            text = "${(onionSkinOpacity.coerceIn(0.05f, 1.0f) * 100).roundToInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonPink
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar opacidade onion skin",
                            tint = textColor.copy(alpha = 0.65f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { showOnionOpacityTray = false }
                        )
                    }
                }
            }

            // Upper row: Playback controls, frame counter, onion skin & layers toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    IconButton(
                        onClick = onPrevFrame,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_prev_button")
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Quadro anterior",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) OrangeAccent else accentColor)
                            .clickable { onTogglePlay() }
                            .testTag("timeline_play_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                            tint = bgColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onNextFrame,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("timeline_next_button")
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Próximo quadro",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor.copy(alpha = 0.7f))
                            .border(0.5.dp, subtleBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${currentFrameIndex + 1}/${frames.size} • ${String.format(java.util.Locale.US, "%.2fs", currentTimeMs / 1000.0)}",
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${fps}fps",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val onionBg by animateColorAsState(
                        targetValue = if (onionSkinEnabled) NeonPink.copy(alpha = 0.25f) else bgColor.copy(alpha = 0.5f),
                        label = "onionSkinBg"
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(onionBg)
                            .border(
                                1.dp,
                                if (onionSkinEnabled) NeonPink else subtleBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleOnionSkin() }
                            .testTag("timeline_onion_skin_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Camada Cebola (Onion Skin)",
                                tint = if (onionSkinEnabled) NeonPink else textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Cebola",
                                fontSize = 7.5.sp,
                                color = if (onionSkinEnabled) NeonPink else textColor.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (onionSkinEnabled && onChangeOnionSkinOpacity != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showOnionOpacityTray) NeonPink.copy(alpha = 0.25f) else bgColor.copy(alpha = 0.65f))
                                .border(0.8.dp, NeonPink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .clickable { showOnionOpacityTray = !showOnionOpacityTray }
                                .padding(horizontal = 5.dp, vertical = 4.dp)
                                .testTag("timeline_onion_opacity_pill"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${(onionSkinOpacity.coerceIn(0.05f, 1.0f) * 100).roundToInt()}%",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPink
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor.copy(alpha = 0.6f))
                            .border(1.dp, subtleBorder, RoundedCornerShape(8.dp))
                            .clickable { onOpenLayers() }
                            .testTag("timeline_layers_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = accentColor, contentColor = bgColor) {
                                    Text("$layerCount", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Gerenciar Camadas",
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDuplicateFrame,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("timeline_duplicate_frame")
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplicar quadro",
                            tint = textColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteFrame,
                        enabled = frames.size > 1,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("timeline_delete_frame")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir quadro",
                            tint = if (frames.size > 1) OrangeAccent else textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor)
                            .clickable { onAddFrame() }
                            .testTag("timeline_add_frame"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Adicionar quadro",
                            tint = bgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Scrollable Ruler + Independent Per-Layer Cel Tracks + Audio Waveform + Aligned Cyan Pinca
            val rowH = 30f
            val tracksBoxMaxH = 92f
            val tracksHeightDp = (effectiveLayers.size.coerceAtLeast(1) * rowH).coerceAtMost(tracksBoxMaxH)
            val totalPinçaHeightDp = 20f + 2f + tracksHeightDp + if (audioAsset != null) 25f else 0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            ) {
                // Pinned Left Layer Rail (stays visible while scrolling horizontally)
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .padding(end = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp))
                            .background(bgColor.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "T${currentTimelineTick + 1}",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = secondaryAccent,
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = tracksBoxMaxH.dp)
                            .verticalScroll(tracksVerticalScrollState),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        effectiveLayers.indices.reversed().forEach { lIdx ->
                            val layerObj = effectiveLayers[lIdx]
                            val isActiveLayer = lIdx == activeLayerIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((rowH - 2f).dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isActiveLayer) accentColor.copy(alpha = 0.22f) else bgColor.copy(alpha = 0.6f))
                                    .border(
                                        width = if (isActiveLayer) 1.dp else 0.5.dp,
                                        color = if (isActiveLayer) accentColor else subtleBorder,
                                        shape = RoundedCornerShape(5.dp)
                                    )
                                    .clickable {
                                        val resolved = TimelineTrackEngine.resolvePreferredFrameIndexAtTick(
                                            trackProject, lIdx, currentTimelineTick, currentFrameIndex
                                        )
                                        onSelectLayerCel(lIdx, resolved, currentTimelineTick)
                                    }
                                    .padding(horizontal = 4.dp)
                                    .testTag("mobile_layer_row_$lIdx"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = layerObj.name,
                                    fontSize = 8.sp,
                                    fontWeight = if (isActiveLayer) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActiveLayer) accentColor else textColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (audioAsset != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(NeonGreen.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // Right Scrollable Ruler + Decoupled Per-Layer Cels + Cyan Pinca Needle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { viewportWidthPx = it.width.toFloat() }
                        .horizontalScroll(scrollState)
                ) {
                    val contentWidthDp = totalTrackWidthDp.dp
                    val pinçaXPosPx = with(density) { ((pinçaTickFloat + 0.5f) * dpPerTick).dp.toPx() }
                        .coerceIn(0f, totalTrackWidthPx)

                    Box(modifier = Modifier.width(contentWidthDp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 1. Time Ruler ("Régua") with tap/drag Pinça scrubbing
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(topEnd = 6.dp))
                                    .background(bgColor.copy(alpha = 0.88f))
                                    .pointerInput(safeFps, totalTrackWidthPx, onSelectTick) {
                                        detectTapGestures { pos ->
                                            val stepPx = with(density) { dpPerTick.dp.toPx() }.coerceAtLeast(1f)
                                            val tick = (pos.x / stepPx).toInt().coerceIn(0, (minRulerTicks - 1).coerceAtLeast(0))
                                            if (onSelectTick != null) {
                                                onSelectTick(tick)
                                            } else {
                                                val ms = TimelineTrackEngine.tickToTimeMs(tick, safeFps)
                                                onScrubToMs(ms)
                                                onEndScrub()
                                            }
                                        }
                                    }
                                    .pointerInput(safeFps, totalTrackWidthPx) {
                                        detectHorizontalDragGestures(
                                            onDragStart = { startPos ->
                                                val pxPerSec = safeFps * with(density) { dpPerTick.dp.toPx() }
                                                val ms = ((startPos.x / pxPerSec.coerceAtLeast(1f)) * 1000f).toLong().coerceAtLeast(0L)
                                                onScrubToMs(ms)
                                            },
                                            onHorizontalDrag = { change, _ ->
                                                change.consume()
                                                val pxPerSec = safeFps * with(density) { dpPerTick.dp.toPx() }
                                                val ms = ((change.position.x / pxPerSec.coerceAtLeast(1f)) * 1000f).toLong().coerceAtLeast(0L)
                                                onScrubToMs(ms)
                                            },
                                            onDragEnd = { onEndScrub() },
                                            onDragCancel = { onEndScrub() }
                                        )
                                    }
                                    .testTag("mobile_timeline_ruler")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stepPx = with(density) { dpPerTick.dp.toPx() }
                                    val totalMarks = (size.width / stepPx).toInt().coerceAtLeast(1)
                                    for (t in 0..totalMarks) {
                                        val x = t * stepPx
                                        val isSecondMark = t % safeFps == 0
                                        val isHalfSec = (t * 2) % safeFps == 0
                                        val tickH = when {
                                            isSecondMark -> size.height * 0.72f
                                            isHalfSec -> size.height * 0.45f
                                            else -> size.height * 0.25f
                                        }
                                        drawLine(
                                            color = if (isSecondMark) secondaryAccent.copy(alpha = 0.9f) else textColor.copy(alpha = 0.35f),
                                            start = Offset(x, size.height - tickH),
                                            end = Offset(x, size.height),
                                            strokeWidth = if (isSecondMark) 1.6f else 1f
                                        )
                                    }
                                }
                                val totalSecMarks = ((minRulerTicks - 1) / safeFps).coerceAtLeast(0)
                                for (sec in 0..totalSecMarks) {
                                    val xDp = (sec * safeFps * dpPerTick).dp
                                    Text(
                                        text = "${sec}s",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = secondaryAccent.copy(alpha = 0.9f),
                                        modifier = Modifier.offset(x = xDp + 2.dp, y = 1.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // 2. Decoupled Per-Timeline-Layer Cel Tracks (stretch & move independently!)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = tracksBoxMaxH.dp)
                                    .verticalScroll(tracksVerticalScrollState),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                effectiveLayers.indices.reversed().forEach { lIdx ->
                                    val layerCels = remember(trackProject, lIdx) {
                                        TimelineTrackEngine.computeLayerCels(trackProject, lIdx)
                                    }
                                    val isActiveLayer = lIdx == activeLayerIndex

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((rowH - 2f).dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(
                                                if (isActiveLayer) bgColor.copy(alpha = 0.7f) else bgColor.copy(alpha = 0.42f)
                                            )
                                            .pointerInput(lIdx, trackProject, safeFps, minRulerTicks) {
                                                val tickPx = with(density) { dpPerTick.dp.toPx() }.coerceAtLeast(1f)
                                                detectTapGestures { tapOffset ->
                                                    val tick = (tapOffset.x / tickPx).toInt().coerceIn(0, (minRulerTicks - 1).coerceAtLeast(0))
                                                    onAddCelAtTick(lIdx, tick)
                                                }
                                            }
                                    ) {
                                        layerCels.forEach { cel ->
                                            val isDraggingThis = draggingCel == (lIdx to cel.frameIndex)
                                            val isMovingThis = movingCel == (lIdx to cel.frameIndex)
                                            val displayDur = if (isDraggingThis && liveDraggingHold != null) {
                                                liveDraggingHold!!
                                            } else {
                                                cel.durationTicks
                                            }
                                            val displayStart = if (isMovingThis && liveMoveTick != null) {
                                                liveMoveTick!!
                                            } else {
                                                cel.startTick
                                            }
                                            val isTickCovered = currentTimelineTick in cel.startTick until cel.endTickExclusive
                                            val isSelected = isActiveLayer && (cel.frameIndex == currentFrameIndex || isTickCovered)
                                            val strokeCount = cel.layer.strokes.size
                                            val cellWidthDp = (displayDur * dpPerTick - 2f).coerceAtLeast(24f).dp
                                            val borderColor = when {
                                                isMovingThis -> NeonGreen
                                                isSelected -> accentColor
                                                isTickCovered -> secondaryAccent.copy(alpha = 0.7f)
                                                else -> subtleBorder
                                            }
                                            val cellBg = when {
                                                isMovingThis -> NeonGreen.copy(alpha = 0.25f)
                                                isSelected -> accentColor.copy(alpha = 0.30f)
                                                isTickCovered -> secondaryAccent.copy(alpha = 0.14f)
                                                else -> panelColor.copy(alpha = 0.75f)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .offset(x = (displayStart * dpPerTick).dp)
                                                    .width(cellWidthDp)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(cellBg)
                                                    .border(if (isSelected || isMovingThis) 1.4.dp else 0.7.dp, borderColor, RoundedCornerShape(5.dp))
                                                    .pointerInput(lIdx, cel.frameIndex, cel.startTick) {
                                                        var accum = 0f
                                                        val tickPx = with(density) { dpPerTick.dp.toPx() }.coerceAtLeast(1f)
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                accum = 0f
                                                                movingCel = lIdx to cel.frameIndex
                                                                liveMoveTick = cel.startTick
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                accum += dragAmount.x
                                                                val deltaTicks = (accum / tickPx).roundToInt()
                                                                liveMoveTick = (cel.startTick + deltaTicks).coerceIn(0, 4096)
                                                            },
                                                            onDragEnd = {
                                                                val dest = liveMoveTick ?: cel.startTick
                                                                movingCel = null
                                                                liveMoveTick = null
                                                                if (dest != cel.startTick) {
                                                                    onMoveLayerCel(lIdx, cel.frameIndex, dest)
                                                                }
                                                            },
                                                            onDragCancel = {
                                                                movingCel = null
                                                                liveMoveTick = null
                                                            }
                                                        )
                                                    }
                                                    .clickable {
                                                        onSelectLayerCel(lIdx, cel.frameIndex, cel.startTick)
                                                        if (editingLayerIdx == lIdx && editingFrameIdx == cel.frameIndex && showTimingTray) {
                                                            showTimingTray = false
                                                        } else {
                                                            editingLayerIdx = lIdx
                                                            editingFrameIdx = cel.frameIndex
                                                            draftHold = cel.durationTicks
                                                            showTimingTray = true
                                                        }
                                                    }
                                                    .then(
                                                        if (lIdx == activeLayerIndex) Modifier.testTag("timeline_frame_${cel.frameIndex}")
                                                        else Modifier.testTag("timeline_layer_${lIdx}_cel_${cel.frameIndex}")
                                                    )
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .padding(start = 3.dp, end = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = "${cel.frameIndex + 1}",
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                        color = if (isSelected) accentColor else textColor
                                                    )
                                                    if (displayDur > 1) {
                                                        Text(
                                                            text = "${displayDur}q",
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = secondaryAccent
                                                        )
                                                    }
                                                    if (strokeCount > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSelected) accentColor else textColor.copy(alpha = 0.45f))
                                                        )
                                                    }
                                                }

                                                // Independent right-edge stretch handle (stretches ONLY this cel on this layer!)
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterEnd)
                                                        .width(10.dp)
                                                        .fillMaxHeight()
                                                        .background(
                                                            if (isDraggingThis || isSelected) secondaryAccent.copy(alpha = 0.35f)
                                                            else textColor.copy(alpha = 0.08f)
                                                        )
                                                        .pointerInput(lIdx, cel.frameIndex, cel.durationTicks) {
                                                            var accum = 0f
                                                            val tickPx = with(density) { dpPerTick.dp.toPx() }.coerceAtLeast(1f)
                                                            detectHorizontalDragGestures(
                                                                onDragStart = {
                                                                    accum = 0f
                                                                    draggingCel = lIdx to cel.frameIndex
                                                                    liveDraggingHold = cel.durationTicks
                                                                },
                                                                onHorizontalDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    accum += dragAmount
                                                                    val delta = (accum / tickPx).roundToInt()
                                                                    liveDraggingHold = (cel.durationTicks + delta).coerceIn(1, 240)
                                                                },
                                                                onDragEnd = {
                                                                    val finalHold = liveDraggingHold ?: cel.durationTicks
                                                                    draggingCel = null
                                                                    liveDraggingHold = null
                                                                    if (finalHold != cel.durationTicks) {
                                                                        onSetLayerCelHold(lIdx, cel.frameIndex, finalHold)
                                                                    }
                                                                },
                                                                onDragCancel = {
                                                                    draggingCel = null
                                                                    liveDraggingHold = null
                                                                }
                                                            )
                                                        }
                                                        .testTag("mobile_stretch_${lIdx}_${cel.frameIndex}"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(2.dp)
                                                            .height(10.dp)
                                                            .clip(RoundedCornerShape(1.dp))
                                                            .background(if (isSelected) secondaryAccent else textColor.copy(alpha = 0.45f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Audio Track Row ("Gravetinhos") for Lip-Sync visual reference when audio is present
                            if (audioAsset != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                val audioWidthDp = (((audioAsset.durationSeconds.coerceAtLeast(1.0) * safeFps) * dpPerTick)
                                    .toFloat()
                                    .coerceIn(80f, totalTrackWidthDp)).dp
                                Box(
                                    modifier = Modifier
                                        .width(audioWidthDp)
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(NeonGreen.copy(alpha = 0.14f))
                                        .border(0.8.dp, NeonGreen.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                                        .testTag("mobile_audio_waveform_track")
                                ) {
                                    val peaks = audioAsset.waveformPeaks
                                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)) {
                                        val count = peaks.size.coerceAtLeast(24)
                                        val barW = (size.width / count).coerceAtLeast(1.5f)
                                        val midY = size.height * 0.5f
                                        for (i in 0 until count) {
                                            val amp = if (peaks.isNotEmpty()) peaks[i % peaks.size].coerceIn(0.06f, 1f) else 0.25f
                                            val halfH = (amp * size.height * 0.48f).coerceAtLeast(1.5f)
                                            val x = i * barW + barW * 0.4f
                                            drawLine(
                                                color = if (audioAsset.audioMuted) Color.Gray else NeonGreen.copy(alpha = 0.9f),
                                                start = Offset(x, midY - halfH),
                                                end = Offset(x, midY + halfH),
                                                strokeWidth = max(1.4f, barW * 0.58f)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Audiotrack,
                                            contentDescription = null,
                                            tint = NeonGreen,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Cyan Playhead "Pinça" Needle spanning ruler + all layer tracks + audio track (draggable for scrubbing)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((pinçaXPosPx - 10f).roundToInt(), 0) }
                                .width(20.dp)
                                .height(totalPinçaHeightDp.dp)
                                .pointerInput(safeFps, totalTrackWidthPx) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            val pxPerSec = safeFps * with(density) { dpPerTick.dp.toPx() }
                                            val ms = ((pinçaXPosPx / pxPerSec.coerceAtLeast(1f)) * 1000f).toLong().coerceAtLeast(0L)
                                            onScrubToMs(ms)
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val pxPerSec = safeFps * with(density) { dpPerTick.dp.toPx() }
                                            val currentPx = (currentTimeMs / 1000f) * pxPerSec
                                            val nextPx = (currentPx + dragAmount).coerceIn(0f, totalTrackWidthPx)
                                            val ms = ((nextPx / pxPerSec.coerceAtLeast(1f)) * 1000f).toLong().coerceAtLeast(0L)
                                            onScrubToMs(ms)
                                        },
                                        onDragEnd = { onEndScrub() },
                                        onDragCancel = { onEndScrub() }
                                    )
                                }
                                .testTag("mobile_timeline_pinca")
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width * 0.5f
                                val headPath = Path().apply {
                                    moveTo(cx - 7f, 0f)
                                    lineTo(cx + 7f, 0f)
                                    lineTo(cx + 7f, 9f)
                                    lineTo(cx, 16f)
                                    lineTo(cx - 7f, 9f)
                                    close()
                                }
                                drawPath(headPath, color = secondaryAccent)
                                drawLine(
                                    color = secondaryAccent,
                                    start = Offset(cx, 15f),
                                    end = Offset(cx, size.height),
                                    strokeWidth = 2.4f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
