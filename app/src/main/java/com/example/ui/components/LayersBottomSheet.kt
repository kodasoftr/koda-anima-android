package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import com.example.model.Layer
import com.example.model.SpecialMediaAsset
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrangeAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersBottomSheet(
    layers: List<Layer>,
    activeLayerIndex: Int,
    mediaAssets: List<SpecialMediaAsset> = emptyList(),
    onSelectLayer: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteActiveLayer: () -> Unit,
    onToggleVisibility: (Int) -> Unit,
    onToggleLock: (Int) -> Unit,
    onChangeOpacity: (Int, Float) -> Unit,
    onImportAudio: () -> Unit = {},
    onImportVideo: () -> Unit = {},
    onRemoveAsset: (String) -> Unit = {},
    onToggleAudioMute: (String) -> Unit = {},
    onSetAudioVolume: (String, Int) -> Unit = { _, _ -> },
    onUpdateAssetOpacity: (String, Double) -> Unit = { _, _ -> },
    onUpdateAssetScale: (String, Double) -> Unit = { _, _ -> },
    onResetAssetTransform: (String) -> Unit = {},
    onToggleVideoTransformMode: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("layers_bottom_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Camadas da Animação",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val reversedIndices = layers.indices.reversed().toList()
                itemsIndexed(reversedIndices) { _, originalIndex ->
                    val layer = layers[originalIndex]
                    val isActive = originalIndex == activeLayerIndex

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectLayer(originalIndex) }
                            .testTag("layer_item_$originalIndex"),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column {
                                        Text(
                                            text = layer.name,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${layer.strokes.size} traços",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onToggleLock(originalIndex) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (layer.isLocked) "Destravar camada" else "Travar camada",
                                            tint = if (layer.isLocked) OrangeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onToggleVisibility(originalIndex) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (layer.isVisible) "Ocultar camada" else "Exibir camada",
                                            tint = if (layer.isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (isActive) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Opacidade: ${(layer.opacity * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(90.dp)
                                    )
                                    Slider(
                                        value = layer.opacity,
                                        onValueChange = { onChangeOpacity(originalIndex, it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDeleteActiveLayer,
                    enabled = layers.size > 1,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("delete_layer_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OrangeAccent
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (layers.size > 1) OrangeAccent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Excluir")
                }

                Button(
                    onClick = onAddLayer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("add_layer_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nova Camada", fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Camadas Especiais (Música & Vídeo)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            val audioAsset = mediaAssets.firstOrNull { it.type == "audio" }
            val videoAsset = mediaAssets.firstOrNull { it.type == "video" || it.type == "gif" }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (audioAsset != null) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .testTag("mobile_audio_layer_card"),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = if (audioAsset != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (audioAsset != null) audioAsset.name else "Música / Trilha Sonora",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (audioAsset != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (audioAsset != null) "Áudio carregado na animação" else "Nenhum áudio importado",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (audioAsset != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onToggleAudioMute(audioAsset.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (audioAsset.audioMuted) {
                                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeMute
                                        } else {
                                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = "Mudo",
                                        tint = if (audioAsset.audioMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveAsset(audioAsset.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remover",
                                        tint = OrangeAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onImportAudio,
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Importar", fontSize = 11.sp)
                            }
                        }
                    }

                    if (audioAsset != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Volume: ${audioAsset.audioVolumePercent}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(85.dp)
                            )
                            Slider(
                                value = audioAsset.audioVolumePercent.toFloat(),
                                onValueChange = { onSetAudioVolume(audioAsset.id, it.toInt()) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.secondary,
                                    activeTrackColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (videoAsset != null) OrangeAccent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .testTag("mobile_video_layer_card"),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = if (videoAsset != null) OrangeAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (videoAsset != null) videoAsset.name else "Vídeo / Referência",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (videoAsset != null) OrangeAccent else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (videoAsset != null) "Mídia de fundo ativa" else "Nenhum vídeo/GIF importado",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (videoAsset != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onToggleVideoTransformMode != null) {
                                    IconButton(
                                        onClick = {
                                            onToggleVideoTransformMode()
                                            onDismiss()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("mobile_layers_transform_video_btn")
                                    ) {
                                        Icon(
                                            Icons.Default.OpenWith,
                                            contentDescription = "Transformar Vídeo (Escala / Rotação / Posição)",
                                            tint = OrangeAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onResetAssetTransform(videoAsset.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.RestartAlt,
                                        contentDescription = "Redefinir",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveAsset(videoAsset.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remover",
                                        tint = OrangeAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onImportVideo,
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OrangeAccent)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Importar", fontSize = 11.sp)
                            }
                        }
                    }

                    if (videoAsset != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Opacidade: ${videoAsset.opacityPercent}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(95.dp)
                            )
                            Slider(
                                value = videoAsset.opacityPercent.toFloat() / 100f,
                                onValueChange = { onUpdateAssetOpacity(videoAsset.id, it.toDouble()) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = OrangeAccent,
                                    activeTrackColor = OrangeAccent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Escala: ${videoAsset.scalePercent.toInt()}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(95.dp)
                            )
                            Slider(
                                value = videoAsset.scalePercent.toFloat(),
                                onValueChange = { onUpdateAssetScale(videoAsset.id, it.toDouble()) },
                                valueRange = 30f..250f,
                                colors = SliderDefaults.colors(
                                    thumbColor = OrangeAccent,
                                    activeTrackColor = OrangeAccent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
