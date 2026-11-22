package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.KodanimaTheme
import com.example.model.ToolType
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent

@Composable
fun ToolboxPalette(
    selectedTool: ToolType,
    currentColor: Long,
    brushSize: Float,
    isStylusOnlyMode: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectTool: (ToolType) -> Unit,
    onChangeBrushSize: (Float) -> Unit,
    onOpenColorPicker: () -> Unit,
    onToggleStylusOnly: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    theme: KodanimaTheme = KodanimaTheme.ROXO_BLACK,
    eraserUsesPressure: Boolean = true,
    onToggleEraserPressure: (() -> Unit)? = null,
    eraserPresetId: Int = 1,
    onSelectEraserPreset: ((Int) -> Unit)? = null,
    isExtraToolsOpen: Boolean = false,
    onToggleExtraTools: (() -> Unit)? = null,
    onOpenBrushStudio: (() -> Unit)? = null,
    eraserSize: Float = brushSize,
    onChangeEraserSize: (Float) -> Unit = onChangeBrushSize,
    modifier: Modifier = Modifier
) {
    var showBrushSlider by remember { mutableStateOf(false) }

    val panelColor = Color(theme.panel)
    val accentColor = Color(theme.accent)
    val textColor = Color(theme.text)
    val borderColor = accentColor.copy(alpha = 0.35f)
    val isEraser = selectedTool == ToolType.ERASER
    val activeToolSize = if (isEraser) eraserSize else brushSize
    val onActiveSizeChange = if (isEraser) onChangeEraserSize else onChangeBrushSize

    // Distinct drawing tools (Lápis, Caneta Vetorial Bézier, Borracha, Balde, Varinha, Laço)
    val mainTools = listOf(
        Pair(ToolType.PENCIL, Icons.Default.Edit),
        Pair(ToolType.BEZIER_PEN, Icons.Default.Timeline),
        Pair(ToolType.ERASER, Icons.Default.Clear),
        Pair(ToolType.BUCKET, Icons.Default.FormatPaint),
        Pair(ToolType.MAGIC_WAND, Icons.Default.AutoFixHigh),
        Pair(ToolType.LASSO, Icons.Default.SelectAll)
    )

    BoxWithConstraints(
        modifier = modifier
    ) {
        val isCompactHeight = maxHeight < 420.dp
        val maxDockHeight = (maxHeight - 16.dp).coerceAtLeast(180.dp)
        Column(
            horizontalAlignment = Alignment.Start
        ) {
        // Floating Brush Size Slider Popup
        AnimatedVisibility(visible = showBrushSlider) {
            Surface(
                color = panelColor,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .width(180.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEraser) "Borracha (E):" else "Tamanho:",
                            color = if (isEraser) NeonPink else textColor,
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        InlineNumericPxInput(
                            valuePx = activeToolSize,
                            minPx = 1f,
                            maxPx = 120f,
                            accentColor = if (isEraser) NeonPink else accentColor,
                            textColor = textColor,
                            panelBgColor = panelColor,
                            testTag = "mobile_inline_px_input",
                            onCommitValue = onActiveSizeChange
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(activeToolSize.coerceIn(2f, 36f).dp)
                                .clip(CircleShape)
                                .background(if (isEraser) Color.White else Color(currentColor))
                                .border(1.dp, if (isEraser) NeonPink else Color.Transparent, CircleShape)
                        )
                    }
                    Slider(
                        value = activeToolSize,
                        onValueChange = onActiveSizeChange,
                        valueRange = 1f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isEraser) NeonPink else accentColor,
                            activeTrackColor = if (isEraser) NeonPink else accentColor,
                            inactiveTrackColor = (if (isEraser) NeonPink else accentColor).copy(alpha = 0.2f)
                        )
                    )

                    if (isEraser && onToggleEraserPressure != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onToggleEraserPressure() }
                                .border(1.dp, if (eraserUsesPressure) NeonPink else textColor.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
                            color = if (eraserUsesPressure) NeonPink.copy(alpha = 0.2f) else Color.Transparent
                        ) {
                            Text(
                                text = if (eraserUsesPressure) "Pressão da Caneta: ATIVA" else "Pressão: DESATIVADA",
                                fontSize = 10.sp,
                                color = if (eraserUsesPressure) NeonPink else textColor.copy(alpha = 0.65f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Tool Dock
            Surface(
                color = panelColor.copy(alpha = 0.95f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .heightIn(max = maxDockHeight)
            ) {
                Column(
                    modifier = Modifier
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 2.dp else 3.dp)
                ) {
                    // Color Picker Button with current color preview
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(2.dp, accentColor, CircleShape)
                            .background(Color(currentColor))
                            .clickable { onOpenColorPicker() }
                            .testTag("tool_color_picker_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                    }

                    // Brush/Eraser Size toggle button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showBrushSlider) accentColor.copy(alpha = 0.25f) else Color(theme.bg).copy(alpha = 0.6f))
                            .clickable { showBrushSlider = !showBrushSlider }
                            .testTag("tool_brush_size_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size((activeToolSize / 4.5f).coerceIn(4f, 14f).dp)
                                    .clip(CircleShape)
                                    .background(if (isEraser) NeonPink else accentColor)
                            )
                            Text(
                                text = "${activeToolSize.toInt()}px",
                                fontSize = 7.5.sp,
                                color = if (isEraser) NeonPink else textColor.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(1.dp)
                            .background(borderColor)
                    )

                    // Main Tool Buttons
                    mainTools.forEach { (tool, icon) ->
                        val isSelected = tool == selectedTool
                        val btnBg by animateColorAsState(
                            targetValue = if (isSelected) (if (tool == ToolType.ERASER) NeonPink else accentColor) else Color.Transparent,
                            label = "toolBg"
                        )
                        val iconTint = if (isSelected) Color(theme.bg) else textColor

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(btnBg)
                                .clickable { onSelectTool(tool) }
                                .testTag("tool_${tool.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            when (tool) {
                                ToolType.ERASER -> EraserVectorIcon(
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                ToolType.LASSO -> LassoVectorIcon(
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                else -> Icon(
                                    imageVector = icon,
                                    contentDescription = tool.labelPt,
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Eyedropper
                    val isEyeSelected = selectedTool == ToolType.EYEDROPPER
                    val eyeBg by animateColorAsState(
                        targetValue = if (isEyeSelected) accentColor else Color.Transparent,
                        label = "eyeBg"
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(eyeBg)
                            .clickable { onSelectTool(ToolType.EYEDROPPER) }
                            .testTag("tool_eyedropper"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Colorize,
                            contentDescription = ToolType.EYEDROPPER.labelPt,
                            tint = if (isEyeSelected) Color(theme.bg) else textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                // Divider
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(borderColor)
                )

                // Undo / Redo Row or Compact Icons
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("tool_undo")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Desfazer",
                        tint = if (canUndo) textColor else textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("tool_redo")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Refazer",
                        tint = if (canRedo) textColor else textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Stylus / Palm Rejection Quick Toggle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isStylusOnlyMode) OrangeAccent.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable { onToggleStylusOnly() }
                        .testTag("tool_stylus_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isStylusOnlyMode) Icons.Default.DoNotTouch else Icons.Default.TouchApp,
                        contentDescription = if (isStylusOnlyMode) "Modo Caneta (Rejeição de Palma Ativa)" else "Modo Toque & Caneta",
                        tint = if (isStylusOnlyMode) OrangeAccent else textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                    // End of Side Bar: 3 Overlapping Wheels button -> reveals extra tools
                    if (onToggleExtraTools != null) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isExtraToolsOpen) accentColor.copy(alpha = 0.28f) else Color(theme.bg).copy(alpha = 0.7f))
                                .border(
                                    width = 1.dp,
                                    color = if (isExtraToolsOpen) accentColor else borderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleExtraTools() }
                                .testTag("mobile_three_wheels_tools_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            ThreeWheelsIcon(
                                tint = textColor,
                                accentColor = accentColor,
                                size = 20.dp
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
