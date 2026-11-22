package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ColorPaletteConfig
import com.example.model.KodanimaTheme
import com.example.ui.theme.NeonPink

/**
 * Bandeja flutuante "Palheta de cor" aberta pelas 3 rodas:
 * - Conta-gotas no topo para capturar cor diretamente do desenho
 * - Botão Dado que gera uma linha harmônica pela teoria das cores
 * - Botão Grade X × Y para configurar a quantidade de quadrados arredondados
 * - Quadrados selecionáveis + Salvar, Remover e X para fechar
 */
@Composable
fun ColorPaletteTrayModal(
    paletteConfig: ColorPaletteConfig,
    currentColor: Long,
    selectedSlotIndex: Int,
    theme: KodanimaTheme,
    onSelectSlot: (Int) -> Unit,
    onSaveToSlot: (Int) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onRollColorTheoryDice: () -> String,
    onChangeGridSize: (cols: Int, rows: Int) -> Unit,
    onActivateEyedropper: () -> Unit,
    onOpenColorWheel: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val panelColor = Color(theme.panel)
    val bgColor = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val secondaryColor = Color(theme.secondaryAccent)
    val textColor = Color(theme.text)
    var showGridConfig by remember { mutableStateOf(false) }
    var lastRolledRule by remember { mutableStateOf<String?>(null) }

    val cols = paletteConfig.cols.coerceIn(2, 12)
    val rows = paletteConfig.rows.coerceIn(1, 8)
    val activeSlot = selectedSlotIndex.coerceIn(0, (cols * rows - 1).coerceAtLeast(0))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .border(1.3.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .testTag("color_palette_tray_modal"),
            color = panelColor,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Palheta de Cor",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("palette_tray_close_btn")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar palheta",
                            tint = textColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Top Row: Conta-gotas + Roda de Cores + Dado + Grade X×Y + Cor atual
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Conta-gotas
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onActivateEyedropper() }
                            .testTag("palette_tray_eyedropper_btn"),
                        color = accentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Colorize,
                                contentDescription = "Conta-gotas",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Conta-gotas",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }

                    // Roda de Cores (retorna para a palheta ao fechar)
                    if (onOpenColorWheel != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenColorWheel() }
                                .testTag("palette_tray_color_wheel_btn"),
                            color = accentColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = "Roda de Cores",
                                    tint = accentColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Roda",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            }
                        }
                    }

                    // Dado (Teoria das cores horizontal)
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                lastRolledRule = onRollColorTheoryDice()
                            }
                            .testTag("palette_tray_dice_btn"),
                        color = secondaryColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, secondaryColor.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Casino,
                                contentDescription = "Dado de Teoria das Cores",
                                tint = secondaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Dado",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }

                    // Grade X × Y configurável
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showGridConfig = !showGridConfig }
                            .testTag("palette_tray_grid_config_btn"),
                        color = if (showGridConfig) accentColor.copy(alpha = 0.25f) else textColor.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (showGridConfig) accentColor else textColor.copy(alpha = 0.22f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = "Configurar grade",
                                tint = if (showGridConfig) accentColor else textColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${cols}×${rows}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }
                    }

                    // Current active color chip
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(currentColor))
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    )
                }

                if (!lastRolledRule.isNullOrBlank()) {
                    Text(
                        text = "Teoria aplicada: $lastRolledRule (linha ${(activeSlot / cols) + 1})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryColor
                    )
                }

                // Grid configuration X × Y panel
                if (showGridConfig) {
                    Surface(
                        color = bgColor.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StepperRow(
                                label = "Colunas (X)",
                                value = cols,
                                textColor = textColor,
                                accentColor = accentColor,
                                onDecrement = { onChangeGridSize((cols - 1).coerceAtLeast(2), rows) },
                                onIncrement = { onChangeGridSize((cols + 1).coerceAtMost(12), rows) }
                            )
                            StepperRow(
                                label = "Linhas (Y)",
                                value = rows,
                                textColor = textColor,
                                accentColor = accentColor,
                                onDecrement = { onChangeGridSize(cols, (rows - 1).coerceAtLeast(1)) },
                                onIncrement = { onChangeGridSize(cols, (rows + 1).coerceAtMost(8)) }
                            )
                        }
                    }
                }

                // Rounded-square swatch grid (X × Y)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (c in 0 until cols) {
                                val slotIdx = r * cols + c
                                val swatchColor = paletteConfig.swatches.getOrElse(slotIdx) { 0L }
                                val isEmpty = swatchColor == 0L
                                val isSelected = slotIdx == activeSlot
                                val borderWidth = if (isSelected) 2.5.dp else 1.dp
                                val borderColor = if (isSelected) accentColor else textColor.copy(alpha = 0.22f)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isEmpty) bgColor.copy(alpha = 0.45f) else Color(swatchColor))
                                        .border(borderWidth, borderColor, RoundedCornerShape(9.dp))
                                        .clickable { onSelectSlot(slotIdx) }
                                        .testTag("palette_slot_$slotIdx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isEmpty) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Espaço vazio ${slotIdx + 1}",
                                            tint = textColor.copy(alpha = 0.3f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom action row: Salvar + Remover
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onRemoveSlot(activeSlot) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("palette_tray_remove_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.65f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remover", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onSaveToSlot(activeSlot) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("palette_tray_save_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = if (theme.isLight) Color.White else Color(0xFF0A0A12)
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    textColor: Color,
    accentColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.78f),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(textColor.copy(alpha = 0.12f))
                    .clickable { onDecrement() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
            Text(
                text = "$value",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(textColor.copy(alpha = 0.12f))
                    .clickable { onIncrement() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}
