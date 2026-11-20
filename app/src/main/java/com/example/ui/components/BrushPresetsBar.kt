package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
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
import com.example.model.BrushPreset
import com.example.model.KodanimaTheme
import com.example.ui.theme.NeonPink

/**
 * Horizontal Krita Brush Bar for Kodanima 2D:
 * - Shows the 12 Most-Used Krita Native Brushes by default.
 * - Ends with a 3-overlapping-wheels button that switches the horizontal bar to
 *   user-imported Krita brushes (.kpp / .bundle) + "+ Importar .kpp".
 */
@Composable
fun BrushPresetsBar(
    activePreset: BrushPreset,
    onSelectPreset: (BrushPreset) -> Unit,
    theme: KodanimaTheme,
    nativeBrushes: List<BrushPreset> = BrushPreset.ALL_12_PRESETS,
    importedBrushes: List<BrushPreset> = emptyList(),
    onRequestImportKrita: (() -> Unit)? = null,
    onDeleteImportedBrush: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    var showImportedBar by remember { mutableStateOf(false) }

    val accentColor = Color(theme.accent)
    val secondaryAccent = Color(theme.secondaryAccent)
    val onAccentColor = if (theme.isLight) Color.White else Color(theme.bg)
    val panelColor = Color(theme.panel)
    val textColor = Color(theme.text)
    val displayList = if (showImportedBar) importedBrushes else nativeBrushes

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .testTag("brush_presets_bar"),
        color = panelColor.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = if (isCompact) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showImportedBar && onRequestImportKrita != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRequestImportKrita() }
                            .testTag("pc_import_krita_button"),
                        color = secondaryAccent.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, secondaryAccent)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isCompact) 3.dp else 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Importar Krita", tint = secondaryAccent, modifier = Modifier.size(13.dp))
                            Text(
                                text = "Importar .kpp",
                                fontSize = if (isCompact) 9.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryAccent
                            )
                        }
                    }
                }

                if (showImportedBar && displayList.isEmpty()) {
                    Text(
                        text = "Nenhum brush do Krita importado (.kpp / .bundle)",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                displayList.forEachIndexed { idx, preset ->
                    val isSelected = preset.id == activePreset.id

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectPreset(preset) }
                            .testTag("brush_preset_${preset.id}"),
                        color = if (isSelected) accentColor.copy(alpha = 0.24f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) accentColor else textColor.copy(alpha = 0.22f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = if (isCompact) 3.dp else 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isCompact) 14.dp else 16.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) accentColor else textColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = if (isCompact) 8.sp else 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) onAccentColor else textColor.copy(alpha = 0.85f)
                                )
                            }

                            Text(
                                text = preset.shortLabel,
                                fontSize = if (isCompact) 10.sp else 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accentColor else textColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            KritaBrushStrokeWaveformPreview(
                                preset = preset,
                                strokeColor = if (isSelected) accentColor else textColor.copy(alpha = 0.82f),
                                modifier = Modifier
                                    .width(if (isCompact) 22.dp else 30.dp)
                                    .height(if (isCompact) 10.dp else 13.dp)
                            )

                            if (showImportedBar && preset.isImportedKrita && onDeleteImportedBrush != null) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remover Brush",
                                    tint = NeonPink,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable { onDeleteImportedBrush(preset.id) }
                                )
                            }
                        }
                    }
                }
            }

            // 3-Overlapping-Wheels button at the end of the horizontal bar -> switches Native Krita <-> Imported Krita
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { showImportedBar = !showImportedBar }
                    .testTag("brush_bar_three_wheels_button"),
                color = if (showImportedBar) NeonPink.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (showImportedBar) NeonPink else accentColor.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = if (isCompact) 3.dp else 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThreeWheelsIcon(
                        tint = textColor,
                        accentColor = if (showImportedBar) NeonPink else accentColor,
                        size = if (isCompact) 16.dp else 18.dp
                    )
                    Text(
                        text = if (showImportedBar) "Krita (12)" else "Importados",
                        fontSize = if (isCompact) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (showImportedBar) NeonPink else accentColor
                    )
                }
            }
        }
    }
}

