package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.SavedProjectEntry
import com.example.model.AnimationProject
import com.example.model.AppScreen
import com.example.model.AppUiMode
import com.example.model.ProjectResolution
import com.example.ui.components.KeyboardShortcutsDialog
import com.example.ui.components.TouchGesturesDialog
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrangeAccent
import com.example.viewmodel.KodanimaViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: KodanimaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<AnimationProject?>(null) }
    var showKeyboardShortcutTrigger by remember { mutableStateOf(false) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showTouchGesturesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initializeWithContext(context)
    }

    // File picker for .koda2d and .kodanima project packages
    val projectFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val isKoda2d = uri.toString().endsWith(".koda2d", ignoreCase = true) ||
                        (context.contentResolver.getType(uri)?.contains("zip") == true)
                if (isKoda2d) {
                    viewModel.importKoda2dFromUri(context, uri)
                    Toast.makeText(context, "Pacote .koda2d importado com sucesso!", Toast.LENGTH_SHORT).show()
                } else {
                    // Try JSON
                    val jsonText = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                    } ?: ""
                    if (jsonText.isBlank()) throw IllegalArgumentException("Arquivo vazio")
                    viewModel.importProjectFromJson(jsonText)
                    viewModel.saveCurrentProject(context)
                    Toast.makeText(context, "Projeto importado e salvo!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                try {
                    viewModel.importKoda2dFromUri(context, uri)
                    Toast.makeText(context, "Projeto .koda2d importado com sucesso!", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Erro ao carregar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_kodanima_k),
                            contentDescription = "Kodanima2D",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column {
                            Text(
                                text = "Kodanima2D",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Estúdio 2D • v${com.example.BuildConfig.VERSION_NAME}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Mode chip & Settings button
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Current mode indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { viewModel.navigateTo(AppScreen.SETTINGS) }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.preferences.uiMode == AppUiMode.PC) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (uiState.preferences.uiMode == AppUiMode.PC) "PC" else "Mobile",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Internet / Servidor Local / Online Button
                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.NETWORK_STUDIO) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_network_button")
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    Icons.Default.WifiTethering,
                                    contentDescription = "Online & Servidor Local",
                                    tint = if (uiState.isLocalServerActive || uiState.multiplayerRole != com.example.model.MultiplayerRole.NONE) NeonGreen else MaterialTheme.colorScheme.primary
                                )
                                if (uiState.isLocalServerActive || uiState.multiplayerRole != com.example.model.MultiplayerRole.NONE) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(NeonGreen)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_settings_button")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Action Banner: Primary buttons (Criar Projeto / Importar / Demo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Big Create Project Button
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp)
                        .testTag("home_create_project_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Novo Projeto", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Import File button
                OutlinedButton(
                    onClick = { projectFilePicker.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("home_import_file_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Collaborative & Friends Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { viewModel.navigateTo(AppScreen.NETWORK_STUDIO) }
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .testTag("home_collaborate_banner"),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                        }
                        Column {
                            Text(
                                text = "Modo Online & Servidor Local",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Conecte com amigos e desenhe no mesmo canvas",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text("Amigos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Incoming Friend Request Notifications
            val pendingRequests = uiState.friendRequests
            if (pendingRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingRequests.forEach { req ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Pedido de Parceria Recebido!",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = "Deseja ser parceiro de ${req.senderName} (#${req.senderId})?",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { viewModel.rejectFriendRequest(req.id) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF3366).copy(alpha = 0.18f))
                                            .border(1.dp, Color(0xFFFF3366), CircleShape)
                                            .testTag("reject_friend_${req.id}")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Recusar", tint = Color(0xFFFF3366), modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = { viewModel.acceptFriendRequest(req.id) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(NeonGreen.copy(alpha = 0.18f))
                                            .border(1.dp, NeonGreen, CircleShape)
                                            .testTag("accept_friend_${req.id}")
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Aceitar", tint = NeonGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Header for Saved Projects
            val totalProjects = if (uiState.savedProjectEntries.isNotEmpty()) uiState.savedProjectEntries.size else uiState.savedProjects.size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Projetos Salvos ($totalProjects)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        val count = viewModel.compactAllProjectsNow(context)
                        Toast.makeText(context, "$count projetos compactados em .koda2d!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Compactar .koda2d", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = { showKeyboardShortcutTrigger = !showKeyboardShortcutTrigger },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_lightbulb_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Dicas e Atalhos",
                            tint = if (showKeyboardShortcutTrigger) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    AnimatedVisibility(visible = showKeyboardShortcutTrigger) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { showShortcutsDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_keyboard_shortcuts_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = "Configurar Atalhos de Teclado",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { showTouchGesturesDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_touch_gestures_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = "Configurar Gestos de Toque",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Grid of Projects
            if (totalProjects == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nenhum projeto salvo ainda",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toque em '+ Novo Projeto' para iniciar sua primeira animação!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.savedProjectEntries.isNotEmpty()) {
                        items(uiState.savedProjectEntries, key = { it.project.id }) { entry ->
                            ProjectCard(
                                project = entry.project,
                                isCompressed = entry.isCompressed,
                                fileSizeLabel = entry.formattedSize,
                                onOpen = { viewModel.openProjectEntry(context, entry) },
                                onDuplicate = { viewModel.duplicateProject(context, entry.project.id) },
                                onDelete = { projectToDelete = entry.project }
                            )
                        }
                    } else {
                        items(uiState.savedProjects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                isCompressed = false,
                                fileSizeLabel = null,
                                onOpen = { viewModel.openProject(project) },
                                onDuplicate = { viewModel.duplicateProject(context, project.id) },
                                onDelete = { projectToDelete = project }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create New Project Dialog (with saved custom resolutions + builder + manual FPS)
    if (showCreateDialog) {
        com.example.ui.components.CreateProjectDialog(
            savedCustomResolutions = uiState.preferences.savedCustomResolutions,
            onSaveCustomResolution = { resName, w, h ->
                viewModel.saveCustomResolution(resName, w, h)
            },
            onDeleteCustomResolution = { res ->
                viewModel.deleteCustomResolution(res)
            },
            onCreate = { name, res, fps ->
                viewModel.createAndOpenNewProject(context, name, res, fps)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Delete Confirmation Dialog
    if (projectToDelete != null) {
        Dialog(onDismissRequest = { projectToDelete = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Excluir Animação?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangeAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tem certeza de que deseja excluir '${projectToDelete?.name}'? Esta ação não pode ser desfeita.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { projectToDelete = null }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                projectToDelete?.let { viewModel.deleteProject(context, it.id) }
                                projectToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeAccent, contentColor = Color.White)
                        ) {
                            Text("Excluir")
                        }
                    }
                }
            }
        }
    }

    // Keyboard Shortcuts Configurator Dialog
    if (showShortcutsDialog) {
        KeyboardShortcutsDialog(
            initialBindings = uiState.preferences.keyboardShortcuts,
            onSave = { updatedMap ->
                viewModel.updateKeyboardShortcuts(updatedMap)
                Toast.makeText(context, "Atalhos salvos com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onResetFactory = {
                val restored = viewModel.resetKeyboardShortcutsToFactory()
                Toast.makeText(context, "Atalhos redefinidos para padrão de fábrica!", Toast.LENGTH_SHORT).show()
                restored
            },
            onDismiss = { showShortcutsDialog = false }
        )
    }

    // Procreate Multi-Finger Touch Gestures Configurator Dialog
    if (showTouchGesturesDialog) {
        TouchGesturesDialog(
            initialGestures = uiState.preferences.touchGestures,
            onSave = { updatedGestures ->
                viewModel.updateTouchGestures(updatedGestures)
                Toast.makeText(context, "Gestos de toque salvos!", Toast.LENGTH_SHORT).show()
            },
            onResetFactory = {
                val restored = viewModel.resetTouchGesturesToFactory()
                Toast.makeText(context, "Gestos redefinidos para o padrão de fábrica!", Toast.LENGTH_SHORT).show()
                restored
            },
            onDismiss = { showTouchGesturesDialog = false }
        )
    }
}

@Composable
private fun ProjectCard(
    project: AnimationProject,
    isCompressed: Boolean = false,
    fileSizeLabel: String? = null,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(project.updatedAt) { dateFormat.format(Date(project.updatedAt)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val targetFrame = project.frames.getOrNull(project.lastActiveFrameIndex) ?: project.frames.firstOrNull()
            val hasDrawableContent = remember(targetFrame) {
                targetFrame != null && (
                    targetFrame.layers.any { it.strokes.isNotEmpty() || it.imageAttachment != null } ||
                        targetFrame.localLayers.any { it.strokes.isNotEmpty() || it.imageAttachment != null }
                    )
            }
            val thumbBitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                initialValue = null,
                project.id,
                project.updatedAt,
                project.lastActiveFrameIndex,
                hasDrawableContent
            ) {
                value = if (hasDrawableContent && targetFrame != null) {
                    kotlinx.coroutines.withContext(com.example.engine.KodanimaDispatchers.backgroundRender) {
                        val srcW = project.resolution.width.coerceAtLeast(1)
                        val srcH = project.resolution.height.coerceAtLeast(1)
                        val maxThumb = 280f
                        val scale = kotlin.math.min(maxThumb / srcW.toFloat(), maxThumb / srcH.toFloat()).coerceAtMost(1f)
                        val tw = (srcW * scale).toInt().coerceAtLeast(1)
                        val th = (srcH * scale).toInt().coerceAtLeast(1)
                        val bmp = android.graphics.Bitmap.createBitmap(tw, th, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        canvas.save()
                        canvas.scale(scale, scale)
                        val combinedLayers = com.example.engine.KodanimaGraphicsEngine.composeOrderedFrameLayers(targetFrame)
                        com.example.engine.KodanimaGraphicsEngine.drawLayersStackToCanvas(
                            layers = combinedLayers,
                            canvas = canvas,
                            targetWidth = srcW,
                            targetHeight = srcH
                        )
                        canvas.restore()
                        bmp.asImageBitmap()
                    }
                } else null
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val cachedThumb = thumbBitmap
                if (cachedThumb != null) {
                    androidx.compose.foundation.Image(
                        bitmap = cachedThumb,
                        contentDescription = "Miniatura de ${project.name}",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!hasDrawableContent) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "${project.frames.size} quadros",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Q ${project.lastActiveFrameIndex + 1}/${project.frames.size}",
                        fontSize = 9.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (isCompressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = if (isCompressed) "${fileSizeLabel ?: ".koda2d"}" else "Local",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = project.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Text(
                text = "${project.resolution.name} • ${project.fps} FPS",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = formattedDate,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = OrangeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

