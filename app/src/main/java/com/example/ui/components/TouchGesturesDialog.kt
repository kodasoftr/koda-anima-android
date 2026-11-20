package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FriendBrushOffer
import com.example.model.GestureAction
import com.example.model.KodanimaTheme
import com.example.model.MultiFingerGesture
import com.example.model.TouchGestureRegistry
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink

/**
 * Configurator Dialog for Procreate-style Multi-Finger Touch Gestures (Mobile & PC).
 * Launched from the Home screen Lightbulb menu alongside Keyboard Shortcuts.
 */
@Composable
fun TouchGesturesDialog(
    initialGestures: Map<MultiFingerGesture, GestureAction>,
    onSave: (Map<MultiFingerGesture, GestureAction>) -> Unit,
    onResetFactory: () -> Map<MultiFingerGesture, GestureAction>,
    onDismiss: () -> Unit
) {
    var draftMap by remember(initialGestures) {
        mutableStateOf(
            TouchGestureRegistry.defaultMap().toMutableMap().apply {
                putAll(initialGestures)
            }
        )
    }
    val colorScheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .heightIn(max = 600.dp)
                .testTag("touch_gestures_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface,
            border = BorderStroke(1.5.dp, colorScheme.primary.copy(alpha = 0.65f)),
            tonalElevation = 10.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Gestos com os Dedos (Procreate)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Funciona no Modo Mobile e Modo PC • 2, 3 e 4 dedos",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = colorScheme.onSurface)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(MultiFingerGesture.entries, key = { it.name }) { gesture ->
                        val selectedAction = draftMap[gesture]
                            ?: TouchGestureRegistry.defaultMap()[gesture]
                            ?: GestureAction.DISABLED
                        var expanded by remember { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.52f),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = gesture.labelPt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = gesture.hintPt,
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }

                                Box {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { expanded = true }
                                            .testTag("gesture_picker_${gesture.name}"),
                                        shape = RoundedCornerShape(10.dp),
                                        color = colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = selectedAction.labelPt,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 132.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ExpandMore,
                                                contentDescription = "Selecionar ação",
                                                tint = colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        GestureAction.entries.forEach { act ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = act.labelPt,
                                                        fontWeight = if (act == selectedAction) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (act == selectedAction) colorScheme.primary else colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    val updated = draftMap.toMutableMap()
                                                    updated[gesture] = act
                                                    draftMap = updated
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Bar: Reset Factory + Save
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val restored = onResetFactory()
                                draftMap = restored.toMutableMap()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("touch_gestures_reset_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colorScheme.outline)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Redefinir Padrão", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                onSave(draftMap)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("touch_gestures_save_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact prompt displayed directly below the brush bar in LAN Multiplayer when a peer
 * uses a custom/imported brush that the local user doesn't have yet.
 */
@Composable
fun FriendBrushOfferBanner(
    offer: FriendBrushOffer?,
    theme: KodanimaTheme,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = offer != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (offer != null) {
            val panelColor = Color(theme.panel)
            val textColor = Color(theme.text)
            val accentColor = Color(theme.accent)
            val greenColor = if (theme.isLight) Color(0xFF008744) else NeonGreen

            Surface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .border(1.2.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .testTag("friend_brush_offer_banner"),
                shape = RoundedCornerShape(12.dp),
                color = panelColor.copy(alpha = 0.96f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.widthIn(max = 175.dp)) {
                        Text(
                            text = "${offer.senderName} usou '${offer.brushPreset.namePt}'",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Baixar este brush para você?",
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.72f),
                            maxLines = 1
                        )
                    }

                    KritaBrushStrokeWaveformPreview(
                        preset = offer.brushPreset,
                        strokeColor = accentColor,
                        modifier = Modifier
                            .width(44.dp)
                            .height(18.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, greenColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .clickable { onAccept() }
                            .testTag("friend_brush_offer_yes"),
                        color = greenColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Sim", tint = greenColor, modifier = Modifier.size(13.dp))
                            Text("Sim", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = greenColor)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, NeonPink.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                            .clickable { onDecline() }
                            .testTag("friend_brush_offer_no"),
                        color = NeonPink.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Não", tint = NeonPink, modifier = Modifier.size(13.dp))
                            Text("Não", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonPink)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Procreate-style Quick Copy/Paste & Layer Actions popup triggered by 3-finger swipe down.
 */
@Composable
fun QuickCopyPasteGestureMenu(
    visible: Boolean,
    theme: KodanimaTheme,
    hasActiveSelection: Boolean,
    onDismiss: () -> Unit,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onPasteSelection: () -> Unit,
    onClearLayer: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    if (!visible) return
    val panelColor = Color(theme.panel)
    val textColor = Color(theme.text)
    val accentColor = Color(theme.accent)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 320.dp)
                .border(1.3.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .testTag("quick_gesture_copy_paste_menu"),
            shape = RoundedCornerShape(18.dp),
            color = panelColor.copy(alpha = 0.97f),
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Ações Rápidas (Gesto)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionTile(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        label = "Desfazer",
                        enabled = true,
                        textColor = textColor,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    ) { onUndo(); onDismiss() }

                    QuickActionTile(
                        icon = Icons.AutoMirrored.Filled.Redo,
                        label = "Refazer",
                        enabled = true,
                        textColor = textColor,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    ) { onRedo(); onDismiss() }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionTile(
                        icon = Icons.Default.ContentCopy,
                        label = "Copiar",
                        enabled = hasActiveSelection,
                        textColor = textColor,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    ) { onCopySelection(); onDismiss() }

                    QuickActionTile(
                        icon = Icons.Default.ContentCut,
                        label = "Recortar",
                        enabled = hasActiveSelection,
                        textColor = textColor,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    ) { onCutSelection(); onDismiss() }

                    QuickActionTile(
                        icon = Icons.Default.ContentPaste,
                        label = "Colar",
                        enabled = true,
                        textColor = textColor,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f)
                    ) { onPasteSelection(); onDismiss() }
                }

                QuickActionTile(
                    icon = Icons.Default.LayersClear,
                    label = "Limpar Camada Atual",
                    enabled = true,
                    textColor = NeonPink,
                    accentColor = NeonPink,
                    modifier = Modifier.fillMaxWidth()
                ) { onClearLayer(); onDismiss() }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (enabled) accentColor.copy(alpha = 0.45f) else textColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) accentColor.copy(alpha = 0.10f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) textColor else textColor.copy(alpha = 0.35f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) textColor else textColor.copy(alpha = 0.35f),
                maxLines = 1
            )
        }
    }
}
