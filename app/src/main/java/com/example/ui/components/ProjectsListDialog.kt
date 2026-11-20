package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ProjectResolution
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent

@Composable
fun ProjectsListDialog(
    savedCustomResolutions: List<ProjectResolution> = emptyList(),
    onSaveCustomResolution: (String, Int, Int) -> ProjectResolution = { name, w, h ->
        ProjectResolution(width = w, height = h, name = name, isCustom = true)
    },
    onDeleteCustomResolution: (ProjectResolution) -> Unit = {},
    onNewProject: (name: String, res: ProjectResolution, fps: Int) -> Unit,
    onLoadDemo: () -> Unit,
    onPickProjectFile: () -> Unit,
    onPickReferenceImage: () -> Unit,
    onClose: () -> Unit
) {
    var showCreateProjectModal by remember { mutableStateOf(false) }

    if (showCreateProjectModal) {
        CreateProjectDialog(
            savedCustomResolutions = savedCustomResolutions,
            onSaveCustomResolution = onSaveCustomResolution,
            onDeleteCustomResolution = onDeleteCustomResolution,
            onCreate = { name, res, fps ->
                onNewProject(name, res, fps)
                showCreateProjectModal = false
                onClose()
            },
            onDismiss = { showCreateProjectModal = false }
        )
        return
    }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("projects_list_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gerenciar Animações",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ProjectActionCard(
                    icon = Icons.Default.AddCircleOutline,
                    title = "Novo Projeto em Branco",
                    subtitle = "Escolha resolução padrão ou personalizada e FPS",
                    accentColor = MaterialTheme.colorScheme.primary,
                    onClick = { showCreateProjectModal = true }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ProjectActionCard(
                    icon = Icons.Default.FileOpen,
                    title = "Importar Arquivo (.kodanima / .koda2d)",
                    subtitle = "Abra um arquivo salvo no armazenamento do Android",
                    accentColor = NeonGreen,
                    onClick = {
                        onPickProjectFile()
                        onClose()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                ProjectActionCard(
                    icon = Icons.Default.Lightbulb,
                    title = "Importar Imagem de Referência",
                    subtitle = "Carregue uma foto da galeria para decalcar / rotoscopia",
                    accentColor = NeonPink,
                    onClick = {
                        onPickReferenceImage()
                        onClose()
                    }
                )
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    savedCustomResolutions: List<ProjectResolution>,
    onSaveCustomResolution: (name: String, width: Int, height: Int) -> ProjectResolution,
    onDeleteCustomResolution: (ProjectResolution) -> Unit,
    onCreate: (name: String, res: ProjectResolution, fps: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("Minha Animação 2D") }
    var selectedRes by remember {
        mutableStateOf(savedCustomResolutions.firstOrNull() ?: ProjectResolution.PRESETS[0])
    }
    var fps by remember { mutableIntStateOf(12) }
    var fpsText by remember { mutableStateOf("12") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("create_project_dialog"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
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
                        text = "Criar Nova Animação",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Nome do Projeto", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_project_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                ResolutionSelectorSection(
                    selectedResolution = selectedRes,
                    savedCustomResolutions = savedCustomResolutions,
                    onSelectResolution = { selectedRes = it },
                    onSaveCustomResolution = { customName, w, h ->
                        val created = onSaveCustomResolution(customName, w, h)
                        selectedRes = created
                        created
                    },
                    onDeleteCustomResolution = { deleted ->
                        if (selectedRes.width == deleted.width && selectedRes.height == deleted.height && selectedRes.name == deleted.name) {
                            selectedRes = ProjectResolution.PRESETS[0]
                        }
                        onDeleteCustomResolution(deleted)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Taxa de Quadros Manual (1 a 120 FPS)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val next = (fps - 1).coerceIn(1, 120)
                            fps = next
                            fpsText = next.toString()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("create_project_fps_minus")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Diminuir FPS", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }

                    OutlinedTextField(
                        value = fpsText,
                        onValueChange = { raw ->
                            val digitsOnly = raw.filter { it.isDigit() }.take(3)
                            fpsText = digitsOnly
                            val parsed = digitsOnly.toIntOrNull()
                            if (parsed != null) {
                                fps = parsed.coerceIn(1, 120)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("FPS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("create_project_fps_input")
                    )

                    IconButton(
                        onClick = {
                            val next = (fps + 1).coerceIn(1, 120)
                            fps = next
                            fpsText = next.toString()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("create_project_fps_plus")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar FPS", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(8, 12, 24, 30, 60).forEach { f ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (fps == f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    fps = f
                                    fpsText = f.toString()
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$f",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fps == f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onCreate(name.ifBlank { "Sem Título" }, selectedRes, fps)
                        },
                        modifier = Modifier.testTag("create_project_confirm_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Criar e Abrir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ResolutionSelectorSection(
    selectedResolution: ProjectResolution,
    savedCustomResolutions: List<ProjectResolution>,
    onSelectResolution: (ProjectResolution) -> Unit,
    onSaveCustomResolution: (name: String, width: Int, height: Int) -> ProjectResolution,
    onDeleteCustomResolution: (ProjectResolution) -> Unit
) {
    var showCustomBuilder by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customWidthText by remember { mutableStateOf(selectedResolution.width.toString()) }
    var customHeightText by remember { mutableStateOf(selectedResolution.height.toString()) }

    val parsedW = customWidthText.toIntOrNull()?.coerceIn(128, 4096)
    val parsedH = customHeightText.toIntOrNull()?.coerceIn(128, 4096)
    val canSaveCustom = parsedW != null && parsedH != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Resolução do Canvas", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { showCustomBuilder = !showCustomBuilder },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.testTag("toggle_custom_res_builder_btn"),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        ) {
            Icon(Icons.Default.AspectRatio, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (showCustomBuilder) "Fechar" else "+ Personalizada",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    AnimatedVisibility(visible = showCustomBuilder) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Nova Resolução Personalizada (128 a 4096 px)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    singleLine = true,
                    label = { Text("Nome (ex: Cinema 2K)", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_res_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customWidthText,
                        onValueChange = { raw -> customWidthText = raw.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        label = { Text("Largura", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_res_width_input")
                    )

                    IconButton(
                        onClick = {
                            val tmp = customWidthText
                            customWidthText = customHeightText
                            customHeightText = tmp
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("custom_res_swap_btn")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Inverter largura e altura", tint = MaterialTheme.colorScheme.primary)
                    }

                    OutlinedTextField(
                        value = customHeightText,
                        onValueChange = { raw -> customHeightText = raw.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        label = { Text("Altura", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_res_height_input")
                    )
                }

                Button(
                    onClick = {
                        if (parsedW != null && parsedH != null) {
                            val saved = onSaveCustomResolution(customName, parsedW, parsedH)
                            onSelectResolution(saved)
                            customName = ""
                            showCustomBuilder = false
                        }
                    },
                    enabled = canSaveCustom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_custom_res_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.BookmarkAdded, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar Resolução Personalizada", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (savedCustomResolutions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Personalizadas Salvas (${savedCustomResolutions.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NeonGreen
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            savedCustomResolutions.forEachIndexed { idx, res ->
                val isSelected = res.width == selectedResolution.width &&
                    res.height == selectedResolution.height &&
                    res.name == selectedResolution.name
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.2.dp,
                            if (isSelected) NeonGreen else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectResolution(res) }
                        .testTag("saved_custom_res_item_$idx"),
                    color = if (isSelected) NeonGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) NeonGreen else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${res.width}x${res.height} px • Salva",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeleteCustomResolution(res) },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("delete_custom_res_btn_$idx")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remover resolução salva", tint = OrangeAccent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text("Predefinições Padrão", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProjectResolution.PRESETS.forEach { res ->
            val isSelected = !selectedResolution.isCustom &&
                res.width == selectedResolution.width &&
                res.height == selectedResolution.height &&
                res.name == selectedResolution.name
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectResolution(res) },
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        res.name,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text("${res.width}x${res.height}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ProjectActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
