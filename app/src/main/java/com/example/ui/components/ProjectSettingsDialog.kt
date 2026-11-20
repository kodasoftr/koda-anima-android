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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.engine.KodanimaGraphicsEngine
import com.example.model.AnimationProject
import com.example.model.OnionSkinSettings
import com.example.model.ProjectResolution
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink

@Composable
fun ProjectSettingsDialog(
    project: AnimationProject,
    savedCustomResolutions: List<ProjectResolution> = emptyList(),
    onSaveCustomResolution: (String, Int, Int) -> ProjectResolution = { name, w, h ->
        ProjectResolution(width = w, height = h, name = name, isCustom = true)
    },
    onDeleteCustomResolution: (ProjectResolution) -> Unit = {},
    onUpdateProjectName: (String) -> Unit,
    onUpdateResolution: (ProjectResolution) -> Unit,
    onUpdateFps: (Int) -> Unit,
    onUpdateOnionSkin: (OnionSkinSettings) -> Unit,
    onClose: () -> Unit
) {
    var projectName by remember { mutableStateOf(project.name) }
    var selectedResolution by remember { mutableStateOf(project.resolution) }
    var fps by remember { mutableIntStateOf(project.fps) }

    var onionEnabled by remember { mutableStateOf(project.onionSkin.isEnabled) }
    var prevFrames by remember { mutableIntStateOf(project.onionSkin.prevFramesCount) }
    var nextFrames by remember { mutableIntStateOf(project.onionSkin.nextFramesCount) }
    var onionOpacity by remember { mutableFloatStateOf(project.onionSkin.opacity) }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("project_settings_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configurações do Projeto",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(text = "Nome da Animação", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                ResolutionSelectorSection(
                    selectedResolution = selectedResolution,
                    savedCustomResolutions = savedCustomResolutions,
                    onSelectResolution = { selectedResolution = it },
                    onSaveCustomResolution = { customName, w, h ->
                        val saved = onSaveCustomResolution(customName, w, h)
                        selectedResolution = saved
                        saved
                    },
                    onDeleteCustomResolution = { deleted ->
                        if (selectedResolution.width == deleted.width && selectedResolution.height == deleted.height && selectedResolution.name == deleted.name) {
                            selectedResolution = ProjectResolution.PRESETS[0]
                        }
                        onDeleteCustomResolution(deleted)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Taxa de Quadros (FPS)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "$fps FPS", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.toInt().coerceIn(1, 120) },
                    valueRange = 1f..120f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(8, 12, 24, 30, 60).forEach { presetFps ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (fps == presetFps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { fps = presetFps }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$presetFps",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fps == presetFps) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Camada Cebola (Onion Skin)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonPink
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ativar Camada Cebola", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = onionEnabled,
                        onCheckedChange = { onionEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonPink,
                            checkedTrackColor = NeonPink.copy(alpha = 0.3f)
                        )
                    )
                }

                if (onionEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quadros Anteriores (Vermelho): $prevFrames", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = prevFrames.toFloat(),
                            onValueChange = { prevFrames = it.toInt() },
                            valueRange = 1f..5f,
                            steps = 3,
                            modifier = Modifier.width(140.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quadros Posteriores (Azul): $nextFrames", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = nextFrames.toFloat(),
                            onValueChange = { nextFrames = it.toInt() },
                            valueRange = 0f..3f,
                            steps = 2,
                            modifier = Modifier.width(140.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Opacidade da Cebola: ${(onionOpacity * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = onionOpacity,
                            onValueChange = { onionOpacity = it },
                            valueRange = 0.1f..0.8f,
                            modifier = Modifier.width(140.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Motor Gráfico Mobile", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                            Text(KodanimaGraphicsEngine.engineBackendName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Pipeline Skia Hardware • Double Buffering Ativo", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onUpdateProjectName(projectName)
                        onUpdateResolution(selectedResolution)
                        onUpdateFps(fps)
                        onUpdateOnionSkin(
                            project.onionSkin.copy(
                                isEnabled = onionEnabled,
                                prevFramesCount = prevFrames,
                                nextFramesCount = nextFrames,
                                opacity = onionOpacity
                            )
                        )
                        onClose()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_settings_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Salvar Configurações", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
