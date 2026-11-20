package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectedFriendInfo
import com.example.model.GridRulerSettings
import com.example.model.KodanimaTheme
import com.example.model.PerspectiveRulerSettings
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom drawn "3 Overlapping Wheels" (3 rodas um sobreposto ao outro) icon
 * used at the end of the side toolbars (PC & Mobile) and horizontal Krita brush bar.
 */
@Composable
fun ThreeWheelsIcon(
    tint: Color,
    accentColor: Color = CyanAccent,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = (w * 0.09f).coerceAtLeast(1.4f)
        val r = w * 0.23f

        val centers = listOf(
            Offset(w * 0.36f, h * 0.62f), // bottom-left wheel
            Offset(w * 0.66f, h * 0.62f), // bottom-right wheel (overlaps left)
            Offset(w * 0.51f, h * 0.34f)  // top-center wheel (overlaps both)
        )
        val colors = listOf(
            tint.copy(alpha = 0.85f),
            tint.copy(alpha = 0.92f),
            accentColor
        )

        centers.forEachIndexed { idx, c ->
            val col = colors[idx]
            drawCircle(
                color = col.copy(alpha = 0.22f),
                radius = r,
                center = c
            )
            drawCircle(
                color = col,
                radius = r,
                center = c,
                style = Stroke(width = strokeW)
            )
            // Hub dot
            drawCircle(
                color = col,
                radius = strokeW * 0.75f,
                center = c
            )
            // 4 tiny spokes so it reads unmistakably as overlapping wheels
            for (s in 0 until 4) {
                val rad = Math.toRadians((s * 90 + idx * 30).toDouble())
                drawLine(
                    color = col,
                    start = c,
                    end = Offset(
                        c.x + cos(rad).toFloat() * (r * 0.82f),
                        c.y + sin(rad).toFloat() * (r * 0.82f)
                    ),
                    strokeWidth = strokeW * 0.6f
                )
            }
        }
    }
}

/**
 * Unobstructed, ultra-compact horizontal list of connected LAN collaborators.
 * Placed cleanly in its own HUD strip (never under side toolbars) and hideable via Configurações.
 */
@Composable
fun CollaboratorHorizontalStrip(
    connectedFriends: List<ConnectedFriendInfo>,
    showHud: Boolean,
    fallbackPingMs: Int,
    theme: KodanimaTheme,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showHud && connectedFriends.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, Color(theme.accent).copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                .testTag("collaborator_horizontal_strip"),
            color = Color(theme.panel).copy(alpha = 0.90f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                connectedFriends.forEach { friend ->
                    val effPing = if (friend.pingMs > 0) friend.pingMs else fallbackPingMs
                    val pingColor = when {
                        effPing in 1..45 -> if (theme.isLight) Color(0xFF008744) else NeonGreen
                        effPing in 46..120 -> if (theme.isLight) Color(0xFFB76E00) else Color(0xFFFFD600)
                        else -> if (theme.isLight) Color(0xFFD84315) else Color(0xFFFF9100)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(theme.bg).copy(alpha = 0.65f))
                            .border(1.dp, Color(friend.avatarColor).copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                            .padding(start = 3.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UserAvatarImage(
                            name = friend.name,
                            avatarUri = friend.avatarUri,
                            avatarBase64 = friend.avatarBase64,
                            avatarColor = friend.avatarColor,
                            size = 20.dp,
                            borderColor = pingColor,
                            borderWidth = 1.dp
                        )
                        Text(
                            text = friend.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(theme.text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 72.dp)
                        )
                        if (friend.isHost) {
                            Text(
                                text = "Dono",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(theme.accent)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(pingColor)
                        )
                        Text(
                            text = if (effPing > 0) "${effPing}ms" else "<1ms",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = pingColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating extra tools menu revealed when clicking the 3-wheels button on the side toolbar
 * (both Mobile and PC modes). Houses helpful secondary canvas/production tools without bloating the dock.
 */
@Composable
fun ExtraToolsFloatingMenu(
    visible: Boolean,
    onionSkinEnabled: Boolean,
    mirrorHorizontal: Boolean,
    isStylusOnlyMode: Boolean,
    theme: KodanimaTheme,
    onDismiss: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenFrameLayers: (() -> Unit)? = null,
    onToggleOnionSkin: () -> Unit,
    onToggleMirror: () -> Unit,
    onPickReferenceImage: () -> Unit,
    onCopySelection: () -> Unit,
    onPasteSelection: () -> Unit,
    onToggleStylusOnly: () -> Unit,
    onResetCamera: () -> Unit,
    onClearFrame: () -> Unit,
    onOpenProjectSettings: () -> Unit,
    isVideoTransformMode: Boolean = false,
    onToggleVideoTransformMode: (() -> Unit)? = null,
    timelineLayoutLabel: String? = null,
    onToggleTimelineLayout: (() -> Unit)? = null,
    isZenModeActive: Boolean = false,
    onToggleZenMode: (() -> Unit)? = null,
    onOpenNetworkStudio: (() -> Unit)? = null,
    onSwitchToMobileMode: (() -> Unit)? = null,
    onSwitchUiMode: (() -> Unit)? = null,
    switchUiModeLabel: String = "Trocar para Modo Mobile",
    switchUiModeIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Smartphone,
    onSaveProject: (() -> Unit)? = null,
    onExportProject: (() -> Unit)? = null,
    onOpenAppSettings: (() -> Unit)? = null,
    onNavigateBackHome: (() -> Unit)? = null,
    gridRulerSettings: GridRulerSettings = GridRulerSettings(),
    onToggleGridRuler: (() -> Unit)? = null,
    onUpdateGridRulerSettings: ((GridRulerSettings) -> Unit)? = null,
    perspectiveRulerSettings: PerspectiveRulerSettings = PerspectiveRulerSettings(),
    onTogglePerspectiveRuler: (() -> Unit)? = null,
    onUpdatePerspectiveRulerSettings: ((PerspectiveRulerSettings) -> Unit)? = null,
    onionSkinOpacity: Float = 0.25f,
    onChangeOnionSkinOpacity: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val panelColor = Color(theme.panel)
        val accentColor = Color(theme.accent)
        val textColor = Color(theme.text)

        Surface(
            modifier = Modifier
                .widthIn(min = 204.dp, max = 246.dp)
                .heightIn(max = 460.dp)
                .border(1.2.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .testTag("extra_tools_floating_menu"),
            color = panelColor.copy(alpha = 0.97f),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThreeWheelsIcon(tint = textColor, accentColor = accentColor, size = 16.dp)
                        Text(
                            text = "Mais Ferramentas",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = "Fechar",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                ExtraToolRow(Icons.Default.Layers, "Camadas", false, textColor, accentColor) {
                    onOpenLayers(); onDismiss()
                }
                if (onOpenFrameLayers != null) {
                    ExtraToolRow(
                        icon = Icons.Default.FilterNone,
                        label = "Camadas do Quadro",
                        isActive = false,
                        textColor = textColor,
                        accentColor = accentColor,
                        trailingActionText = "F7",
                        modifier = Modifier.testTag("three_wheels_frame_layers_btn")
                    ) {
                        onOpenFrameLayers(); onDismiss()
                    }
                }
                ExtraToolRow(Icons.Default.Visibility, "Onion Skin (Cebola)", onionSkinEnabled, textColor, accentColor) {
                    onToggleOnionSkin()
                }
                if (onionSkinEnabled && onChangeOnionSkinOpacity != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(textColor.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Opac. Cebola",
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.8f)
                        )
                        Slider(
                            value = onionSkinOpacity.coerceIn(0.05f, 1.0f),
                            onValueChange = onChangeOnionSkinOpacity,
                            valueRange = 0.05f..1.0f,
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp)
                                .testTag("extra_menu_onion_opacity_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonPink,
                                activeTrackColor = NeonPink
                            )
                        )
                        Text(
                            text = "${(onionSkinOpacity.coerceIn(0.05f, 1.0f) * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )
                    }
                }
                ExtraToolRow(Icons.Default.Flip, "Espelhar Visualização", mirrorHorizontal, textColor, accentColor) {
                    onToggleMirror()
                }
                if (onToggleGridRuler != null) {
                    var showGridConfig by remember { mutableStateOf(false) }
                    ExtraToolRow(
                        Icons.Default.GridOn,
                        if (gridRulerSettings.isEnabled) "Régua Quadriculada (Ativa)" else "Régua Quadriculada",
                        gridRulerSettings.isEnabled,
                        textColor,
                        accentColor,
                        trailingActionText = "Cor/Opac"
                    ) {
                        onToggleGridRuler()
                        showGridConfig = true
                    }
                    if (showGridConfig || gridRulerSettings.isEnabled) {
                        RulerConfigPanel(
                            title = "Grade (${gridRulerSettings.cellSizePx.toInt()}px)",
                            selectedColorLong = gridRulerSettings.color,
                            opacity = gridRulerSettings.opacity,
                            onChangeOpacity = { op ->
                                onUpdateGridRulerSettings?.invoke(gridRulerSettings.copy(opacity = op.coerceIn(0.1f, 1.0f)))
                            },
                            textColor = textColor,
                            accentColor = accentColor,
                            onSelectColor = { c ->
                                onUpdateGridRulerSettings?.invoke(gridRulerSettings.copy(color = c))
                            },
                            extraContent = {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(24f, 32f, 48f, 64f, 96f).forEach { spacing ->
                                        val sel = gridRulerSettings.cellSizePx.toInt() == spacing.toInt()
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) accentColor else textColor.copy(alpha = 0.12f))
                                                .clickable {
                                                    onUpdateGridRulerSettings?.invoke(gridRulerSettings.copy(cellSizePx = spacing, isEnabled = true))
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                "${spacing.toInt()}px",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sel) Color.Black else textColor
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                if (onTogglePerspectiveRuler != null) {
                    ExtraToolRow(
                        Icons.Default.Architecture,
                        if (perspectiveRulerSettings.isEnabled) "Perspectiva (${perspectiveRulerSettings.vanishingPointsCount}P)" else "Régua de Perspectiva",
                        perspectiveRulerSettings.isEnabled,
                        textColor,
                        accentColor,
                        trailingActionText = "Cores/Opac"
                    ) {
                        onTogglePerspectiveRuler()
                    }
                    if (perspectiveRulerSettings.isEnabled) {
                        RulerConfigPanel(
                            title = "Pontos de Fuga (${perspectiveRulerSettings.vanishingPointsCount}P)",
                            selectedColorLong = perspectiveRulerSettings.vp1Color,
                            showDefaultColorRow = false,
                            opacity = perspectiveRulerSettings.opacity,
                            onChangeOpacity = { op ->
                                onUpdatePerspectiveRulerSettings?.invoke(
                                    perspectiveRulerSettings.copy(opacity = op.coerceIn(0.1f, 1.0f))
                                )
                            },
                            textColor = textColor,
                            accentColor = accentColor,
                            onSelectColor = { c ->
                                onUpdatePerspectiveRulerSettings?.invoke(
                                    perspectiveRulerSettings.copy(color = c, vp1Color = c)
                                )
                            },
                            extraContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(1, 2, 3).forEach { count ->
                                        val sel = perspectiveRulerSettings.vanishingPointsCount == count
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) accentColor else textColor.copy(alpha = 0.12f))
                                                .clickable {
                                                    onUpdatePerspectiveRulerSettings?.invoke(perspectiveRulerSettings.copy(vanishingPointsCount = count))
                                                }
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                "${count}P",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sel) Color.Black else textColor
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (perspectiveRulerSettings.snapToGuide) NeonGreen.copy(alpha = 0.28f) else textColor.copy(alpha = 0.12f))
                                            .clickable {
                                                onUpdatePerspectiveRulerSettings?.invoke(
                                                    perspectiveRulerSettings.copy(snapToGuide = !perspectiveRulerSettings.snapToGuide)
                                                )
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            if (perspectiveRulerSettings.snapToGuide) "Alinhar: ON" else "Alinhar: OFF",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (perspectiveRulerSettings.snapToGuide) NeonGreen else textColor
                                        )
                                    }
                                }

                                var selectedVpTab by remember { mutableIntStateOf(0) }
                                val activeVpTab = selectedVpTab.coerceIn(0, (perspectiveRulerSettings.vanishingPointsCount - 1).coerceAtLeast(0))
                                val vpTabColors = listOf(
                                    perspectiveRulerSettings.vp1Color,
                                    perspectiveRulerSettings.vp2Color,
                                    perspectiveRulerSettings.vp3Color
                                )

                                // Compact P1 | P2 | P3 tab selector + single color swatch row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (vpIdx in 0 until perspectiveRulerSettings.vanishingPointsCount.coerceIn(1, 3)) {
                                        val isTabSel = vpIdx == activeVpTab
                                        val vpDotCol = Color(vpTabColors[vpIdx])
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isTabSel) vpDotCol.copy(alpha = 0.22f) else textColor.copy(alpha = 0.08f))
                                                .border(
                                                    1.dp,
                                                    if (isTabSel) vpDotCol else textColor.copy(alpha = 0.16f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { selectedVpTab = vpIdx }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(vpDotCol)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "P${vpIdx + 1}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isTabSel) textColor else textColor.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                }

                                RulerColorSwatchRow(
                                    label = "Cor P${activeVpTab + 1}:",
                                    selectedColorLong = vpTabColors[activeVpTab],
                                    textColor = textColor,
                                    accentColor = accentColor,
                                    onSelectColor = { c ->
                                        val updated = when (activeVpTab) {
                                            0 -> perspectiveRulerSettings.copy(color = c, vp1Color = c)
                                            1 -> perspectiveRulerSettings.copy(vp2Color = c)
                                            else -> perspectiveRulerSettings.copy(vp3Color = c)
                                        }
                                        onUpdatePerspectiveRulerSettings?.invoke(updated)
                                    }
                                )

                                // Z-order option: behind selected layer strokes vs in front of all strokes
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val behind = perspectiveRulerSettings.renderBehindActiveLayer
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (behind) accentColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.08f))
                                            .border(0.8.dp, if (behind) accentColor else textColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                onUpdatePerspectiveRulerSettings?.invoke(
                                                    perspectiveRulerSettings.copy(renderBehindActiveLayer = true)
                                                )
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Atrás da camada",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (behind) accentColor else textColor
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (!behind) accentColor.copy(alpha = 0.22f) else textColor.copy(alpha = 0.08f))
                                            .border(0.8.dp, if (!behind) accentColor else textColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                onUpdatePerspectiveRulerSettings?.invoke(
                                                    perspectiveRulerSettings.copy(renderBehindActiveLayer = false)
                                                )
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Na frente de tudo",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (!behind) accentColor else textColor
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                if (onToggleVideoTransformMode != null) {
                    ExtraToolRow(Icons.Default.OpenWith, "Transformar Vídeo (Mover/Escala)", isVideoTransformMode, textColor, accentColor) {
                        onToggleVideoTransformMode(); onDismiss()
                    }
                }
                ExtraToolRow(Icons.Default.Image, "Imagem de Referência", false, textColor, accentColor) {
                    onPickReferenceImage(); onDismiss()
                }
                ExtraToolRow(Icons.Default.ContentCopy, "Copiar Seleção", false, textColor, accentColor) {
                    onCopySelection(); onDismiss()
                }
                ExtraToolRow(Icons.Default.ContentPaste, "Colar Seleção", false, textColor, accentColor) {
                    onPasteSelection(); onDismiss()
                }
                ExtraToolRow(Icons.Default.Gesture, if (isStylusOnlyMode) "Caneta: Apenas Stylus" else "Caneta + Toque", isStylusOnlyMode, textColor, accentColor) {
                    onToggleStylusOnly()
                }
                ExtraToolRow(Icons.Default.RestartAlt, "Resetar Câmera", false, textColor, accentColor) {
                    onResetCamera(); onDismiss()
                }
                ExtraToolRow(Icons.Default.DeleteSweep, "Limpar Quadro Atual", false, NeonPink, NeonPink) {
                    onClearFrame(); onDismiss()
                }
                ExtraToolRow(Icons.Default.Tune, "Configurações do Projeto", false, textColor, accentColor) {
                    onOpenProjectSettings(); onDismiss()
                }
                if (onToggleTimelineLayout != null) {
                    ExtraToolRow(
                        Icons.Default.ViewTimeline,
                        "Timeline: ${timelineLayoutLabel ?: "Alternar"}",
                        false,
                        textColor,
                        accentColor
                    ) {
                        onToggleTimelineLayout()
                    }
                }
                if (onToggleZenMode != null) {
                    ExtraToolRow(Icons.Default.CenterFocusStrong, "Modo Foco / Zen (TAB)", isZenModeActive, textColor, accentColor) {
                        onToggleZenMode(); onDismiss()
                    }
                }
                if (onSaveProject != null) {
                    ExtraToolRow(Icons.Default.Save, "Salvar Projeto", false, textColor, accentColor) {
                        onSaveProject(); onDismiss()
                    }
                }
                if (onExportProject != null) {
                    ExtraToolRow(Icons.Default.IosShare, "Exportar Vídeo / GIF / PNG", false, textColor, accentColor) {
                        onExportProject(); onDismiss()
                    }
                }
                if (onOpenNetworkStudio != null) {
                    ExtraToolRow(Icons.Default.WifiTethering, "Online & Servidor Local", false, textColor, accentColor) {
                        onOpenNetworkStudio(); onDismiss()
                    }
                }
                if (onSwitchUiMode != null) {
                    ExtraToolRow(switchUiModeIcon, switchUiModeLabel, false, textColor, accentColor) {
                        onSwitchUiMode(); onDismiss()
                    }
                } else if (onSwitchToMobileMode != null) {
                    ExtraToolRow(Icons.Default.Smartphone, "Trocar para Modo Mobile", false, textColor, accentColor) {
                        onSwitchToMobileMode(); onDismiss()
                    }
                }
                if (onOpenAppSettings != null) {
                    ExtraToolRow(Icons.Default.Settings, "Configurações do App", false, textColor, accentColor) {
                        onOpenAppSettings(); onDismiss()
                    }
                }
                if (onNavigateBackHome != null) {
                    ExtraToolRow(Icons.AutoMirrored.Filled.ArrowBack, "Voltar aos Projetos", false, textColor, accentColor) {
                        onNavigateBackHome(); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraToolRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    textColor: Color,
    accentColor: Color,
    trailingActionText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                width = 0.8.dp,
                color = if (isActive) accentColor.copy(alpha = 0.65f) else textColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) accentColor else textColor.copy(alpha = 0.88f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) accentColor else textColor,
            modifier = Modifier.weight(1f)
        )
        if (!trailingActionText.isNullOrBlank()) {
            Text(
                text = trailingActionText,
                fontSize = 9.sp,
                color = textColor.copy(alpha = 0.65f)
            )
        }
    }
}

private val RULER_COLOR_SWATCHES = listOf(
    0xFF00D2FFL,
    0xFFFF7A00L,
    0xFFFF3366L,
    0xFF00E676L,
    0xFF7C4DFFL,
    0xFF1F2937L,
    0xFFFFFFFFL
)

@Composable
private fun RulerColorSwatchRow(
    label: String,
    selectedColorLong: Long,
    textColor: Color,
    accentColor: Color,
    onSelectColor: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 9.sp, color = textColor.copy(alpha = 0.78f))
        RULER_COLOR_SWATCHES.forEach { swatchLong ->
            val isSel = selectedColorLong == swatchLong
            Box(
                modifier = Modifier
                    .size(if (isSel) 18.dp else 15.dp)
                    .clip(CircleShape)
                    .background(Color(swatchLong))
                    .border(
                        width = if (isSel) 2.dp else 0.8.dp,
                        color = if (isSel) accentColor else textColor.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .clickable { onSelectColor(swatchLong) }
            )
        }
    }
}

@Composable
private fun RulerConfigPanel(
    title: String,
    selectedColorLong: Long,
    textColor: Color,
    accentColor: Color,
    onSelectColor: (Long) -> Unit,
    showDefaultColorRow: Boolean = true,
    opacity: Float? = null,
    onChangeOpacity: ((Float) -> Unit)? = null,
    extraContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(textColor.copy(alpha = 0.06f))
            .border(0.7.dp, textColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.82f)
        )
        extraContent()
        if (showDefaultColorRow) {
            RulerColorSwatchRow(
                label = "Cor:",
                selectedColorLong = selectedColorLong,
                textColor = textColor,
                accentColor = accentColor,
                onSelectColor = onSelectColor
            )
        }
        if (opacity != null && onChangeOpacity != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Opac:",
                    fontSize = 9.sp,
                    color = textColor.copy(alpha = 0.78f)
                )
                Slider(
                    value = opacity.coerceIn(0.1f, 1.0f),
                    onValueChange = onChangeOpacity,
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    )
                )
                Text(
                    text = "${(opacity.coerceIn(0.1f, 1.0f) * 100).toInt()}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}
