package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BucketFillConfig
import com.example.model.BucketReferenceMode
import com.example.model.Layer

/**
 * Floating Config. Balde popover opened when tapping the Bucket tool (or its gear icon).
 * Exposes Expandir Área (px), Pintar Sempre Por Baixo da Line Art, Referência (Todas / Selecionadas),
 * and quick multi-layer reference checkboxes + creation of multiple Bucket fill layers.
 */
@Composable
fun BucketConfigPopover(
    config: BucketFillConfig,
    frameLayers: List<Layer>,
    localLayers: List<Layer>,
    panelColor: Color,
    accentColor: Color,
    textColor: Color,
    onUpdateConfig: (BucketFillConfig) -> Unit,
    onToggleTimelineLayerReference: (Int) -> Unit,
    onToggleLocalLayerReference: (Int) -> Unit,
    onCreateBucketLayer: () -> Unit,
    onSelectBucketTargetLayer: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandText by remember(config.expandPixels) { mutableStateOf(config.expandPixels.toString()) }
    val allAvailableLayers = remember(frameLayers, localLayers) {
        val list = ArrayList<Triple<Layer, Boolean, Int>>(frameLayers.size + localLayers.size)
        frameLayers.forEachIndexed { idx, l -> list.add(Triple(l, false, idx)) }
        localLayers.forEachIndexed { idx, l -> list.add(Triple(l, true, idx)) }
        list
    }
    val bucketLayers = allAvailableLayers.filter { it.first.isBucketLayer }
    val activeBucketName = allAvailableLayers.firstOrNull {
        it.first.id == config.activeBucketTargetLayerId
    }?.first?.name ?: bucketLayers.lastOrNull()?.first?.name ?: "Criada automaticamente"

    Surface(
        modifier = modifier
            .widthIn(min = 250.dp, max = 310.dp)
            .testTag("bucket_config_popover"),
        shape = RoundedCornerShape(16.dp),
        color = panelColor.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.FormatColorFill,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Config. do Balde",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fechar Config. do Balde",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = textColor.copy(alpha = 0.1f))

            // 1. Expandir Área (Crescer Pixels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Expandir área (px)",
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Evita bordas brancas no contorno",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 9.sp
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
                            .background(textColor.copy(alpha = 0.1f))
                            .clickable {
                                val next = (config.expandPixels - 1).coerceIn(-8, 24)
                                expandText = next.toString()
                                onUpdateConfig(config.copy(expandPixels = next))
                            }
                            .testTag("bucket_expand_minus"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir expansão", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                    BasicTextField(
                        value = expandText,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() || it == '-' }.take(3)
                            expandText = clean
                            clean.toIntOrNull()?.let { v ->
                                onUpdateConfig(config.copy(expandPixels = v.coerceIn(-8, 24)))
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(accentColor),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("bucket_expand_input")
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(textColor.copy(alpha = 0.1f))
                            .clickable {
                                val next = (config.expandPixels + 1).coerceIn(-8, 24)
                                expandText = next.toString()
                                onUpdateConfig(config.copy(expandPixels = next))
                            }
                            .testTag("bucket_expand_plus"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar expansão", tint = textColor, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // 2. Pintar Sempre Por Baixo da Line Art
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(textColor.copy(alpha = 0.05f))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pintar por baixo da line art",
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (config.paintUnderneathLineArt) "Alvo: $activeBucketName"
                        else "Preenche direto na camada ativa",
                        color = if (config.paintUnderneathLineArt) accentColor else textColor.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = config.paintUnderneathLineArt,
                    onCheckedChange = { checked ->
                        onUpdateConfig(config.copy(paintUnderneathLineArt = checked))
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor),
                    modifier = Modifier.testTag("bucket_paint_under_switch")
                )
            }

            if (config.paintUnderneathLineArt) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Camadas Balde:", color = textColor.copy(alpha = 0.72f), fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { onCreateBucketLayer() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                            .testTag("bucket_add_layer_btn")
                    ) {
                        Text("+ Balde", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (bucketLayers.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bucketLayers.take(3).forEach { (layer, _, _) ->
                            val isTarget = layer.id == config.activeBucketTargetLayerId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isTarget) accentColor else textColor.copy(alpha = 0.08f))
                                    .clickable { onSelectBucketTargetLayer(layer.id) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    layer.name,
                                    color = if (isTarget) Color.White else textColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = textColor.copy(alpha = 0.1f))

            // 3. Modo de Referência (Todas vs Múltiplas Camadas Selecionadas)
            Text(
                "Camadas de Referência (Limite):",
                color = textColor.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BucketReferenceMode.entries.forEach { mode ->
                    val selected = config.referenceMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accentColor else textColor.copy(alpha = 0.08f))
                            .clickable { onUpdateConfig(config.copy(referenceMode = mode)) }
                            .padding(vertical = 6.dp)
                            .testTag("bucket_ref_mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (mode == BucketReferenceMode.ALL_LAYERS) "Todas" else "Selecionadas",
                            color = if (selected) Color.White else textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (config.referenceMode == BucketReferenceMode.SELECTED_LAYERS) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    allAvailableLayers.reversed().take(5).forEach { (layer, isLocal, idx) ->
                        val isRef = layer.isBucketReference || layer.id in config.selectedReferenceLayerIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isRef) accentColor.copy(alpha = 0.16f) else textColor.copy(alpha = 0.04f))
                                .clickable {
                                    if (isLocal) onToggleLocalLayerReference(idx)
                                    else onToggleTimelineLayerReference(idx)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isRef) accentColor else textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isLocal) "${layer.name} (Quadro)" else layer.name,
                                color = textColor,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (isRef) "REF ✓" else "Marcar",
                                color = if (isRef) accentColor else textColor.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
