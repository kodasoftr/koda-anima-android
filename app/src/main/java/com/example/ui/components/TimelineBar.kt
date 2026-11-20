package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.MediaAssetManager
import com.example.model.Frame
import com.example.model.KodanimaTheme
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
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
    var editingFrameIdx by remember { mutableIntStateOf(currentFrameIndex) }
    var draftHold by remember { mutableIntStateOf(frames.getOrNull(currentFrameIndex)?.holdFrames ?: 1) }

    val audioAsset = mediaAssets.firstOrNull { it.type == "audio" }
    val safeFps = max(1, fps)
    val totalTicks = remember(frames) { MediaAssetManager.computeTotalTicks(frames) }
    val animDurationMs = remember(totalTicks, safeFps) { (totalTicks * 1000L) / safeFps }
    val audioDurationMs = ((audioAsset?.durationSeconds ?: 0.0) * 1000.0).toLong()
    val timelineTotalMs = max(animDurationMs, audioDurationMs).coerceAtLeast(1000L)

    // Each timeline tick = 34.dp so holdFrames visibly stretches/shrinks blocks cleanly
    val dpPerTick = 34f
    val gapDp = 3f
    val totalTrackWidthDp = remember(frames, timelineTotalMs, safeFps) {
        val framesDp = frames.sumOf { (max(1, it.holdFrames) * dpPerTick + gapDp).toDouble() }.toFloat()
        val audioMinTicks = ((audioDurationMs * safeFps) / 1000f).coerceAtLeast(0f)
        max(framesDp, audioMinTicks * dpPerTick).coerceAtLeast(240f)
    }
    val totalTrackWidthPx = with(density) { totalTrackWidthDp.dp.toPx() }

    val currentTimeMs = activeScrubTimeMs ?: remember(frames, currentFrameIndex, safeFps) {
        MediaAssetManager.computeTimeMsForFrame(frames, currentFrameIndex, safeFps)
    }

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    // Keep Pinça & active frame smoothly visible during playback / stepping
    LaunchedEffect(currentFrameIndex, isPlaying) {
        if (currentFrameIndex in frames.indices && activeScrubTimeMs == null) {
            var xAccDp = 0f
            for (i in 0 until currentFrameIndex) {
                xAccDp += max(1, frames[i].holdFrames) * dpPerTick + gapDp
            }
            val targetPx = with(density) { xAccDp.dp.toPx() }.toInt()
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
                                    text = "Modificar tempo • Quadro ${editingFrameIdx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
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
                                        .clickable { draftHold = (draftHold + 1).coerceAtMost(120) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar tempo", tint = textColor, modifier = Modifier.size(14.dp))
                                }

                                // Apply & close tray
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onSetFrameHold(editingFrameIdx, draftHold)
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
                                            onSetFrameHold(editingFrameIdx, presetHold)
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

            // Scrollable Ruler + Pinça Scrubbing + Proportional Frames + Audio Waveform Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .onSizeChanged { viewportWidthPx = it.width.toFloat() }
                    .horizontalScroll(scrollState)
            ) {
                val contentWidthDp = totalTrackWidthDp.dp
                val pinçaFraction = (currentTimeMs.toFloat() / timelineTotalMs.toFloat()).coerceIn(0f, 1f)
                val animWidthDp = remember(frames) {
                    frames.sumOf { (max(1, it.holdFrames) * dpPerTick + gapDp).toDouble() }.toFloat().coerceAtLeast(100f)
                }
                val animWidthPx = with(density) { animWidthDp.dp.toPx() }
                // Align Pinça cleanly with animation ticks when scrubbing inside animation range
                val pinçaXFractionOfTimeline = if (timelineTotalMs > 0L) {
                    (currentTimeMs.toFloat() / max(1L, animDurationMs).toFloat()) * (animWidthPx / totalTrackWidthPx.coerceAtLeast(1f))
                } else pinçaFraction
                val pinçaXPosPx = (currentTimeMs.toDouble() * safeFps / 1000.0 * with(density) { (dpPerTick + gapDp * 0.25f).dp.toPx() })
                    .toFloat()
                    .coerceIn(0f, totalTrackWidthPx)

                Box(modifier = Modifier.width(contentWidthDp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Time Ruler ("Régua") with tap/drag Pinça audio scrubbing
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(bgColor.copy(alpha = 0.88f))
                                .pointerInput(safeFps, totalTrackWidthPx) {
                                    detectTapGestures { pos ->
                                        val pxPerSec = safeFps * with(density) { dpPerTick.dp.toPx() }
                                        val ms = ((pos.x / pxPerSec.coerceAtLeast(1f)) * 1000f).toLong().coerceAtLeast(0L)
                                        onScrubToMs(ms)
                                        onEndScrub()
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
                            val totalSecMarks = (timelineTotalMs / 1000L).toInt().coerceAtLeast(0)
                            for (sec in 0..totalSecMarks) {
                                val xDp = (sec * safeFps * dpPerTick).dp
                                Text(
                                    text = "${sec}s",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = secondaryAccent.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .offset(x = xDp + 2.dp, y = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // 2. Proportional Frames Track (width grows/shrinks based on holdFrames)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(gapDp.dp)
                        ) {
                            frames.forEachIndexed { index, frame ->
                                val isSelected = index == currentFrameIndex
                                val hold = max(1, frame.holdFrames)
                                val cellWidthDp = (hold * dpPerTick).coerceIn(34f, 408f).dp
                                val strokeCount = frame.layers.sumOf { it.strokes.size } + frame.localLayers.sumOf { it.strokes.size }
                                val borderColor by animateColorAsState(
                                    targetValue = if (isSelected) accentColor else subtleBorder,
                                    label = "frameBorderColor"
                                )
                                val cellBg = if (isSelected) accentColor.copy(alpha = 0.28f) else bgColor.copy(alpha = 0.6f)

                                Box(
                                    modifier = Modifier
                                        .width(cellWidthDp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cellBg)
                                        .border(if (isSelected) 1.5.dp else 0.7.dp, borderColor, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onSelectFrame(index)
                                            if (editingFrameIdx == index && showTimingTray) {
                                                showTimingTray = false
                                            } else {
                                                editingFrameIdx = index
                                                draftHold = hold
                                                showTimingTray = true
                                            }
                                        }
                                        .testTag("timeline_frame_$index"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) accentColor else textColor
                                        )
                                        if (hold > 1) {
                                            Text(
                                                text = "${hold}q",
                                                fontSize = 9.sp,
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

                    // Cyan Playhead "Pinça" Needle spanning ruler + frames + audio track (draggable for audio scrubbing)
                    val trackHeightDp = if (audioAsset != null) 85.dp else 60.dp
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((pinçaXPosPx - 10f).roundToInt(), 0) }
                            .width(20.dp)
                            .height(trackHeightDp)
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
