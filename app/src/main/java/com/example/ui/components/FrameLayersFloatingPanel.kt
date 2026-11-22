package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.KodanimaTheme
import com.example.model.Layer
import com.example.model.LayerBlendEffect
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import kotlin.math.roundToInt

/**
 * Painel flutuante e arrastável para gerenciamento de subcamadas isoladas por quadro
 * ("Camadas do Quadro"), com suporte a efeitos de mesclagem, opacidade e Máscara de Recorte.
 */
@Composable
fun FrameLayersFloatingPanel(
    visible: Boolean,
    currentFrameIndex: Int,
    localLayers: List<Layer>,
    activeLocalLayerIndex: Int,
    activeTargetIsFrameLocalLayer: Boolean,
    activeTimelineLayerName: String,
    theme: KodanimaTheme,
    onClose: () -> Unit,
    onSelectLocalLayer: (Int) -> Unit,
    onUseTimelineLayerTarget: () -> Unit,
    onAddLocalLayer: () -> Unit,
    onDeleteLocalLayer: (Int) -> Unit,
    onRenameLocalLayer: (Int, String) -> Unit,
    onMoveLocalLayerUp: (Int) -> Unit,
    onMoveLocalLayerDown: (Int) -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onToggleLock: (Int) -> Unit,
    onChangeOpacity: (Int, Float) -> Unit,
    onChangeBlendEffect: (Int, LayerBlendEffect) -> Unit,
    onToggleClippingMask: (Int) -> Unit,
    onAddBucketLayer: (() -> Unit)? = null,
    onToggleBucketReference: ((Int) -> Unit)? = null,
    onSelectBucketTargetLayer: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var renameTargetIdx by remember { mutableStateOf<Int?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val panelBg = Color(theme.panel)
    val bgDeep = Color(theme.bg)
    val accentColor = Color(theme.accent)
    val textColor = Color(theme.text)
    val mutedText = textColor.copy(alpha = 0.65f)
    val borderColor = accentColor.copy(alpha = 0.55f)

    val validLocalIdx = if (localLayers.isNotEmpty()) {
        activeLocalLayerIndex.coerceIn(0, localLayers.lastIndex)
    } else -1
    val selectedLocalLayer = if (validLocalIdx in localLayers.indices) localLayers[validLocalIdx] else null

    BoxWithConstraints(modifier = modifier) {
        val adaptiveMaxH = (maxHeight - 16.dp).coerceIn(220.dp, 520.dp)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f)
        ) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                    .widthIn(min = 276.dp, max = 336.dp)
                    .heightIn(max = adaptiveMaxH)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .border(1.4.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("frame_layers_floating_panel"),
                color = panelBg.copy(alpha = 0.97f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // Draggable Header Row with Icon + Title + X Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgDeep.copy(alpha = 0.55f))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY += dragAmount.y
                            }
                        }
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterNone,
                            contentDescription = "Camadas do Quadro",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Camadas do Quadro #${currentFrameIndex + 1}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeTargetIsFrameLocalLayer && selectedLocalLayer != null) {
                                    "Alvo: ${selectedLocalLayer.name}"
                                } else {
                                    "Alvo: $activeTimelineLayerName (Timeline)"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (activeTargetIsFrameLocalLayer) accentColor else mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("frame_layers_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar painel de camadas do quadro",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Target switcher back to main Timeline layer when drawing on a local sub-layer
                if (activeTargetIsFrameLocalLayer) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onUseTimelineLayerTarget() }
                            .testTag("frame_layer_back_to_timeline_btn"),
                        color = bgDeep.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, textColor.copy(alpha = 0.22f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Voltar a desenhar na Linha do Tempo ($activeTimelineLayerName)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                // Top CRUD Toolbar: + Nova, Delete, Up, Down, Rename
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddLocalLayer() }
                            .testTag("frame_layer_add_button"),
                        color = accentColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Nova subcamada neste quadro",
                                tint = if (theme.isLight) Color.White else Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Nova",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (theme.isLight) Color.White else Color.Black
                            )
                        }
                    }

                    if (onAddBucketLayer != null) {
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAddBucketLayer() }
                                .testTag("frame_layer_add_bucket_button"),
                            color = Color(0xFF00D2FF).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Nova subcamada Balde",
                                    tint = Color(0xFF00D2FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Balde",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00D2FF)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            if (validLocalIdx in localLayers.indices) {
                                renameDraft = localLayers[validLocalIdx].name
                                renameTargetIdx = validLocalIdx
                            }
                        },
                        enabled = selectedLocalLayer != null,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("frame_layer_rename_button")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Renomear subcamada",
                            tint = if (selectedLocalLayer != null) textColor else mutedText.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (validLocalIdx in localLayers.indices) onMoveLocalLayerUp(validLocalIdx) },
                        enabled = validLocalIdx in 0 until localLayers.lastIndex,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("frame_layer_move_up_button")
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Subir subcamada",
                            tint = if (validLocalIdx in 0 until localLayers.lastIndex) textColor else mutedText.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (validLocalIdx > 0) onMoveLocalLayerDown(validLocalIdx) },
                        enabled = validLocalIdx > 0,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("frame_layer_move_down_button")
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Descer subcamada",
                            tint = if (validLocalIdx > 0) textColor else mutedText.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (validLocalIdx in localLayers.indices) onDeleteLocalLayer(validLocalIdx) },
                        enabled = selectedLocalLayer != null,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("frame_layer_delete_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir subcamada",
                            tint = if (selectedLocalLayer != null) NeonPink else mutedText.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Sub-layers list (isolated to currentFrameIndex only)
                if (localLayers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgDeep.copy(alpha = 0.45f))
                            .border(1.dp, textColor.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Sem subcamadas neste quadro",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = "As subcamadas criadas aqui existem apenas no Quadro #${currentFrameIndex + 1} e não alteram a Linha do Tempo.",
                                fontSize = 10.5.sp,
                                color = mutedText
                            )
                            Button(
                                onClick = onAddLocalLayer,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (theme.isLight) Color.White else Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Criar Subcamada",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (theme.isLight) Color.White else Color.Black
                                )
                            }
                        }
                    }
                } else {
                    val reversedIndices = remember(localLayers.size) { localLayers.indices.reversed().toList() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 172.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(reversedIndices, key = { idx -> localLayers[idx].id }) { idx ->
                            val layer = localLayers[idx]
                            val isSelected = activeTargetIsFrameLocalLayer && idx == validLocalIdx
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { onSelectLocalLayer(idx) }
                                    .testTag("frame_layer_row_$idx"),
                                color = if (isSelected) accentColor.copy(alpha = 0.22f) else bgDeep.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    width = if (isSelected) 1.4.dp else 0.8.dp,
                                    color = if (isSelected) accentColor else textColor.copy(alpha = 0.16f)
                                ),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onToggleVisibility(idx) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (layer.isVisible) "Ocultar subcamada" else "Mostrar subcamada",
                                            tint = if (layer.isVisible) accentColor else mutedText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    if (layer.isClippingMask) {
                                        Icon(
                                            imageVector = Icons.Default.SubdirectoryArrowRight,
                                            contentDescription = "Máscara de Recorte ativa",
                                            tint = NeonGreen,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = layer.name,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (layer.isVisible) textColor else mutedText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (layer.isBucketLayer) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF00D2FF).copy(alpha = 0.22f))
                                                        .border(0.8.dp, Color(0xFF00D2FF), RoundedCornerShape(4.dp))
                                                        .clickable(enabled = onSelectBucketTargetLayer != null) {
                                                            onSelectBucketTargetLayer?.invoke(layer.id)
                                                        }
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("BALDE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D2FF))
                                                }
                                            }
                                            if (onToggleBucketReference != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (layer.isBucketReference) NeonGreen.copy(alpha = 0.22f)
                                                            else bgDeep.copy(alpha = 0.5f)
                                                        )
                                                        .border(
                                                            0.8.dp,
                                                            if (layer.isBucketReference) NeonGreen else textColor.copy(alpha = 0.22f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .clickable { onToggleBucketReference(idx) }
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        .testTag("frame_layer_ref_$idx")
                                                ) {
                                                    Text(
                                                        text = if (layer.isBucketReference) "Ref ✓" else "Ref",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (layer.isBucketReference) NeonGreen else mutedText
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = buildString {
                                                append("${(layer.opacity * 100).toInt()}%")
                                                append(" • ${layer.blendEffect.labelPt}")
                                                if (layer.isClippingMask) append(" • Recorte")
                                            },
                                            fontSize = 9.5.sp,
                                            color = if (layer.isClippingMask) NeonGreen else mutedText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { onToggleLock(idx) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (layer.isLocked) "Desbloquear subcamada" else "Bloquear subcamada",
                                            tint = if (layer.isLocked) NeonPink else mutedText,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Sub-layer Controls: Clipping Mask + Opacity + Blend Effect
                    if (selectedLocalLayer != null) {
                        HorizontalDivider(color = textColor.copy(alpha = 0.14f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Clipping Mask Toggle ("Máscara de Recorte")
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleClippingMask(validLocalIdx) }
                                    .testTag("frame_layer_clip_toggle"),
                                color = if (selectedLocalLayer.isClippingMask) NeonGreen.copy(alpha = 0.18f) else bgDeep.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedLocalLayer.isClippingMask) NeonGreen else textColor.copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Máscara de Recorte",
                                        tint = if (selectedLocalLayer.isClippingMask) NeonGreen else textColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Máscara de Recorte",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedLocalLayer.isClippingMask) NeonGreen else textColor
                                        )
                                        Text(
                                            text = if (selectedLocalLayer.isClippingMask) {
                                                "Ativa: pinta apenas dentro da camada abaixo"
                                            } else {
                                                "Recorta esta subcamada na base visível abaixo"
                                            },
                                            fontSize = 9.sp,
                                            color = mutedText
                                        )
                                    }
                                }
                            }

                            // Opacity Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Opacidade",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Slider(
                                    value = selectedLocalLayer.opacity,
                                    onValueChange = { onChangeOpacity(validLocalIdx, it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(22.dp)
                                        .testTag("frame_layer_opacity_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor
                                    )
                                )
                                Text(
                                    text = "${(selectedLocalLayer.opacity * 100).toInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }

                            // Blend Effects Row ("Efeito de Mesclagem")
                            Text(
                                text = "Efeito de Mesclagem",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                LayerBlendEffect.entries.forEach { effect ->
                                    val isChosen = selectedLocalLayer.blendEffect == effect
                                    Surface(
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onChangeBlendEffect(validLocalIdx, effect) }
                                            .testTag("frame_layer_blend_${effect.name}"),
                                        color = if (isChosen) accentColor else bgDeep.copy(alpha = 0.5f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isChosen) accentColor else textColor.copy(alpha = 0.2f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = effect.labelPt,
                                                fontSize = 10.sp,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isChosen) {
                                                    if (theme.isLight) Color.White else Color.Black
                                                } else textColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // Rename Sub-layer Dialog
    val idxToRename = renameTargetIdx
    if (idxToRename != null) {
        AlertDialog(
            onDismissRequest = { renameTargetIdx = null },
            title = { Text("Renomear Subcamada") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Nome da subcamada") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameLocalLayer(idxToRename, renameDraft)
                        renameTargetIdx = null
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetIdx = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
