package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Compact, draggable & lockable ("Cadeado") floating window showing the user's saved palette
 * swatches ("Palheta já escolhida") for fast 1-tap switching while drawing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickPaletteFloatingWindow(
    swatches: List<Long>,
    currentColor: Long,
    isLocked: Boolean,
    panelColor: Color,
    accentColor: Color,
    textColor: Color,
    onSelectColor: (Long) -> Unit,
    onOpenFullPaletteTray: () -> Unit,
    onToggleLock: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(16f) }
    var offsetY by remember { mutableFloatStateOf(72f) }
    val validSwatches = remember(swatches) { swatches.filter { it != 0L } }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .widthIn(min = 180.dp, max = 250.dp)
            .testTag("quick_palette_floating_window"),
        shape = RoundedCornerShape(14.dp),
        color = panelColor.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, if (isLocked) accentColor else textColor.copy(alpha = 0.22f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(isLocked) {
                        if (!isLocked) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.DragIndicator,
                        contentDescription = null,
                        tint = if (isLocked) textColor.copy(alpha = 0.35f) else accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Palheta Rápida",
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onOpenFullPaletteTray,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("quick_palette_open_tray_btn")
                    ) {
                        Icon(
                            Icons.Default.ColorLens,
                            contentDescription = "Editar Palheta",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleLock,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("quick_palette_lock_btn")
                    ) {
                        Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "Destravar Palheta Rápida" else "Travar Posição da Palheta",
                            tint = if (isLocked) accentColor else textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("quick_palette_close_btn")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar Palheta Rápida",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (validSwatches.isEmpty()) {
                Text(
                    "Nenhuma cor salva. Toque no ícone de paleta acima para salvar cores.",
                    color = textColor.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    validSwatches.forEachIndexed { idx, colLong ->
                        val isSelected = colLong == currentColor
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(colLong.toInt()))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) accentColor else Color.White.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectColor(colLong) }
                                .testTag("quick_palette_swatch_$idx")
                        )
                    }
                }
            }
        }
    }
}
