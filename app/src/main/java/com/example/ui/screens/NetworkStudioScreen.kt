package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.PermissionHelper
import com.example.model.AppScreen
import com.example.model.ConnectionStatus
import com.example.model.FriendEntry
import com.example.model.NetworkTransportMode
import com.example.model.RemoteRoomInfo
import com.example.ui.components.UserAvatarImage
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.OrangeAccent
import com.example.viewmodel.KodanimaViewModel

/**
 * Screen for local multiplayer synchronization, account management, local server hosting,
 * room discovery, and friends system.
 */
@Composable
fun NetworkStudioScreen(
    viewModel: KodanimaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val user = uiState.userAccount

    val colorScheme = MaterialTheme.colorScheme
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Minha Conta & Servidor", "Salas Locais", "Amigos & Parcerias")

    // Hoisted draft name so picking a gallery photo preserves any typed name
    var draftAccountName by remember(user.id) { mutableStateOf(user.name) }
    LaunchedEffect(user.name) {
        if (draftAccountName.isBlank() || (user.name.isNotBlank() && draftAccountName.startsWith("Animador ") && !user.name.startsWith("Animador "))) {
            draftAccountName = user.name
        }
    }

    // Photo picker for avatar
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateUserAccount(draftAccountName, uri.toString(), user.avatarColor)
            Toast.makeText(context, "Foto de perfil salva!", Toast.LENGTH_SHORT).show()
        }
    }

    // Keep NSD + LAN discovery running persistently across all tabs so Tecno BG6 & Moto G never miss requests/invites
    LaunchedEffect(Unit) {
        viewModel.startDiscoveringRooms()
    }

    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background),
        containerColor = colorScheme.background,
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = colorScheme.surface,
                tonalElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.HOME) },
                        modifier = Modifier.testTag("network_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kodanima Online & Servidor Local",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Desenho colaborativo em tempo real via Wi-Fi / Hotspot",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = colorScheme.primary
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> AccountAndServerTab(
                    viewModel = viewModel,
                    draftAccountName = draftAccountName,
                    onDraftAccountNameChange = { draftAccountName = it },
                    onPickAvatar = {
                        photoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }
                )
                1 -> DiscoveredRoomsTab(viewModel = viewModel)
                2 -> FriendsAndPartnersTab(viewModel = viewModel)
            }
        }
    }
}

/**
 * Tab 1: User Account & Local Server Hosting
 */
@Composable
private fun AccountAndServerTab(
    viewModel: KodanimaViewModel,
    draftAccountName: String,
    onDraftAccountNameChange: (String) -> Unit,
    onPickAvatar: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val user = uiState.userAccount
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val successAccent = if (isLightTheme) Color(0xFF008744) else NeonGreen
    val warningAccent = if (isLightTheme) Color(0xFFD85A00) else OrangeAccent

    var selectedProjectForHost by remember(uiState.project) { mutableStateOf(uiState.project) }
    var accountSavedFeedback by remember { mutableStateOf(false) }

    val wifiPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.setNetworkTransportMode(NetworkTransportMode.WIFI_DIRECT)
    }

    val btPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.setNetworkTransportMode(NetworkTransportMode.BLUETOOTH)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. User Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Perfil do Artista",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    if (accountSavedFeedback || draftAccountName == user.name) {
                        Text(
                            text = if (accountSavedFeedback) "✓ Salvo no aparelho" else "Sincronizado",
                            fontSize = 10.sp,
                            color = successAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar Image / Placeholder
                    UserAvatarImage(
                        name = draftAccountName.ifBlank { user.name },
                        avatarUri = user.avatarUri,
                        avatarBase64 = user.avatarBase64,
                        avatarColor = user.avatarColor,
                        size = 68.dp,
                        borderWidth = 2.dp,
                        useInitialFallback = false,
                        modifier = Modifier.clickable { onPickAvatar() }
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = draftAccountName,
                            onValueChange = {
                                accountSavedFeedback = false
                                onDraftAccountNameChange(it)
                            },
                            label = { Text("Nome do Artista") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant,
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                focusedLabelColor = colorScheme.primary,
                                unfocusedLabelColor = colorScheme.onSurfaceVariant,
                                cursorColor = colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Device ID Pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ID do Smartphone: ${user.id}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copiar ID",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(user.id))
                                        Toast.makeText(context, "ID copiado!", Toast.LENGTH_SHORT).show()
                                    }
                            )
                        }
                    }
                }

                // Profile Color Accent Row
                val avatarColors = listOf(0xFF00E5FFL, 0xFF00E676L, 0xFFFF7A00L, 0xFFFF3366L, 0xFF987EDCL, 0xFFFFD700L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cor:", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                    avatarColors.forEach { cVal ->
                        val isSel = user.avatarColor == cVal
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(cVal))
                                .border(
                                    if (isSel) 2.dp else 1.dp,
                                    if (isSel) colorScheme.onSurface else colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .clickable {
                                    viewModel.updateUserAccount(draftAccountName, user.avatarUri, cVal)
                                    accountSavedFeedback = true
                                }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onPickAvatar() },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurface)
                    ) {
                        Text("Escolher Foto da Galeria", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.updateUserAccount(draftAccountName, user.avatarUri, user.avatarColor)
                            accountSavedFeedback = true
                            Toast.makeText(context, "Conta salva com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_account_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Text("Salvar Conta", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. Local Server Hosting Card ("Ativar Servidor Local")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (uiState.isLocalServerActive) successAccent.copy(alpha = 0.65f) else colorScheme.outlineVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = if (uiState.isLocalServerActive) successAccent else warningAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ativar Servidor Local",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.isLocalServerActive) "Servidor Ativo • Conectado à Rede Local" else "Permita que amigos na mesma rede desenhem com você",
                                fontSize = 11.sp,
                                color = if (uiState.isLocalServerActive) successAccent else colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = uiState.isLocalServerActive,
                        onCheckedChange = { active ->
                            if (active) {
                                viewModel.updateUserAccount(draftAccountName, user.avatarUri, user.avatarColor)
                                viewModel.startHostingLocalServer(selectedProjectForHost)
                                Toast.makeText(context, "Servidor Local Ativado!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.stopMultiplayerSession()
                                Toast.makeText(context, "Servidor Encerrado.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = successAccent,
                            checkedTrackColor = successAccent.copy(alpha = 0.35f)
                        )
                    )
                }

                // Connection Mode Selector: Wi-Fi 2.4G/5G | Wi-Fi Direct | Bluetooth Offline
                Text(
                    text = "Modo de Conexão (Roteador ou Offline sem Internet):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeMode = uiState.preferences.networkTransportMode
                    val modeOptions = listOf(
                        Triple(NetworkTransportMode.WIFI_LAN, "Wi-Fi 2.4G/5G", Icons.Default.Router),
                        Triple(NetworkTransportMode.WIFI_DIRECT, "Wi-Fi Direct", Icons.Default.Wifi),
                        Triple(NetworkTransportMode.BLUETOOTH, "Bluetooth", Icons.Default.Bluetooth)
                    )
                    modeOptions.forEach { (mode, label, icon) ->
                        val isSel = activeMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) colorScheme.primary else colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    when (mode) {
                                        NetworkTransportMode.WIFI_DIRECT -> {
                                            if (PermissionHelper.hasWifiDirectPermission(context)) {
                                                viewModel.setNetworkTransportMode(mode)
                                            } else {
                                                wifiPermLauncher.launch(PermissionHelper.getWifiDirectPermissions())
                                            }
                                        }
                                        NetworkTransportMode.BLUETOOTH -> {
                                            if (PermissionHelper.hasBluetoothPermission(context)) {
                                                viewModel.setNetworkTransportMode(mode)
                                            } else {
                                                btPermLauncher.launch(PermissionHelper.getBluetoothPermissions())
                                            }
                                        }
                                        NetworkTransportMode.WIFI_LAN -> viewModel.setNetworkTransportMode(mode)
                                    }
                                },
                            color = if (isSel) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (isSel) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) colorScheme.primary else colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // If not active, let user pick which project to share
                if (!uiState.isLocalServerActive) {
                    Text(
                        text = "Selecione o Projeto para Compartilhar com Amigos:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )

                    val projects = uiState.savedProjects.ifEmpty { listOf(uiState.project) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        projects.forEach { proj ->
                            val isSel = proj.id == selectedProjectForHost.id
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSel) 1.5.dp else 1.dp,
                                        color = if (isSel) colorScheme.primary else colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedProjectForHost = proj },
                                color = if (isSel) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = proj.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) colorScheme.primary else colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${proj.frames.size} quadros • ${proj.fps} FPS",
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updateUserAccount(draftAccountName, user.avatarUri, user.avatarColor)
                            viewModel.startHostingLocalServer(selectedProjectForHost)
                            Toast.makeText(context, "Sala iniciada com o projeto: ${selectedProjectForHost.name}!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("start_local_server_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Conectar Servidor Local & Criar Sala", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Active Room Details
                    val room = uiState.activeRoomInfo
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, successAccent.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ID da Sala: ${room?.roomId ?: "KD-ROOM"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = successAccent
                                )
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copiar ID da Sala",
                                    tint = successAccent,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(room?.roomId ?: ""))
                                            Toast.makeText(context, "ID da sala copiado!", Toast.LENGTH_SHORT).show()
                                        }
                                )
                            }
                            Text(
                                text = "IP Local: ${room?.hostIp}:${room?.hostPort}",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Projeto Ativo: ${uiState.project.name} (${uiState.project.frames.size} quadros)",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Pessoas na sessão: ${uiState.connectedPeerCount} colaborador(es)",
                                fontSize = 11.sp,
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Button to open canvas directly
                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.EDITOR) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Abrir Canvas Colaborativo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/**
 * Tab 2: Discovered Rooms (Wi-Fi 2.4G/5G), Wi-Fi Direct Pairing & Bluetooth Offline Coop
 */
@Composable
private fun DiscoveredRoomsTab(
    viewModel: KodanimaViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val successAccent = if (isLightTheme) Color(0xFF008744) else NeonGreen
    val warningAccent = if (isLightTheme) Color(0xFFD85A00) else OrangeAccent
    var manualIp by remember { mutableStateOf("") }
    var manualRoomId by remember { mutableStateOf("") }

    val wifiDirectPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshWifiDirectPeers()
    }
    val bluetoothPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshBluetoothPeers()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Discovery Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Salas Locais (Wi-Fi 2.4 GHz / 5 GHz)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Varredura automática NSD + TCP sub-rede para roteadores banda dupla",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { viewModel.startDiscoveringRooms() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar Salas", tint = colorScheme.primary)
            }
        }

        // Status banner if knocking
        if (uiState.connectionStatus == ConnectionStatus.KNOCKING) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = warningAccent.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, warningAccent)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = warningAccent, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Aguardando seu amigo permitir sua entrada no Canvas...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = warningAccent
                    )
                }
            }
        }

        // Discovered Rooms List (strictly excluding own hosted room)
        val visibleRooms = remember(uiState.discoveredRooms, uiState.userAccount.id, uiState.activeRoomInfo?.roomId) {
            uiState.discoveredRooms.filterNot { r ->
                r.hostId.equals(uiState.userAccount.id, ignoreCase = true) ||
                    (uiState.activeRoomInfo?.roomId != null && r.roomId == uiState.activeRoomInfo?.roomId)
            }
        }

        if (visibleRooms.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    Text(
                        text = "Nenhuma sala Wi-Fi encontrada no momento",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Peça para seu amigo ativar o Servidor Local ou use o pareamento direto (Wi-Fi Direct / Bluetooth) abaixo caso esteja sem roteador.",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleRooms.forEach { room ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                UserAvatarImage(
                                    name = room.hostName,
                                    avatarUri = room.hostAvatarUri,
                                    avatarBase64 = room.hostAvatarBase64,
                                    avatarColor = room.hostAvatarColor,
                                    size = 46.dp,
                                    borderWidth = 1.5.dp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = room.hostName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Projeto: ${room.roomName}",
                                        fontSize = 11.sp,
                                        color = colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Sala ${room.roomId} • ${room.frameCount} quadros • ${room.fps} FPS",
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.joinRoom(room)
                                    Toast.makeText(context, "Pedindo permissão para ${room.hostName}...", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text("Entrar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Wi-Fi Direct Direct Proximity Pairing Section (No Router Required)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pareamento Direto Wi-Fi Direct",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Text(
                            text = "Conecta celulares por proximidade Wi-Fi mesmo sem roteador",
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (PermissionHelper.hasWifiDirectPermission(context)) {
                                viewModel.refreshWifiDirectPeers()
                            } else {
                                wifiDirectPermLauncher.launch(PermissionHelper.getWifiDirectPermissions())
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buscar", fontSize = 11.sp)
                    }
                }

                if (uiState.wifiDirectPeers.isEmpty()) {
                    Text(
                        text = "Nenhum dispositivo Wi-Fi Direct listado. Toque em 'Buscar' nos dois aparelhos.",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.wifiDirectPeers.forEach { peer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.deviceName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                Text("${peer.deviceAddress} • ${peer.statusLabel}", fontSize = 10.sp, color = colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { viewModel.connectWifiDirectPeer(peer.deviceAddress) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                )
                            ) {
                                Text(if (peer.isConnected) "Conectado" else "Parear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Offline Bluetooth RFCOMM Coop Section (Zero Wi-Fi / Zero Internet)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, successAccent.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bluetooth Offline (Sem Internet / Roteador)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = successAccent
                        )
                        Text(
                            text = "Host ativa o servidor no modo Bluetooth; o amigo toca em Conectar BT",
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (PermissionHelper.hasBluetoothPermission(context)) {
                                viewModel.refreshBluetoothPeers()
                            } else {
                                bluetoothPermLauncher.launch(PermissionHelper.getBluetoothPermissions())
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buscar BT", fontSize = 11.sp)
                    }
                }

                if (uiState.bluetoothPeers.isEmpty()) {
                    Text(
                        text = "Toque em 'Buscar BT' para listar aparelhos pareados e próximos.",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.bluetoothPeers.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                                Text(
                                    text = if (dev.isBonded) "${dev.address} • Pareado" else "${dev.address} • Próximo",
                                    fontSize = 10.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.connectBluetoothHost(dev.address) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                            ) {
                                Text("Conectar BT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Manual Connection Option
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Conectar por IP / ID Manualmente",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("IP do Host (ex: 192.168.1.15)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedLabelColor = colorScheme.primary,
                            unfocusedLabelColor = colorScheme.onSurfaceVariant,
                            cursorColor = colorScheme.primary
                        )
                    )
                    OutlinedTextField(
                        value = manualRoomId,
                        onValueChange = { manualRoomId = it },
                        label = { Text("ID da Sala") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedLabelColor = colorScheme.primary,
                            unfocusedLabelColor = colorScheme.onSurfaceVariant,
                            cursorColor = colorScheme.primary
                        )
                    )
                }

                Button(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            val manualRoom = RemoteRoomInfo(
                                roomId = manualRoomId.ifBlank { "KD-MANUAL" },
                                roomName = "Sala Manual",
                                hostId = "UNKNOWN",
                                hostName = "Amigo",
                                hostAvatarColor = 0xFF00E5FF,
                                hostIp = manualIp.trim(),
                                hostPort = 8765
                            )
                            viewModel.joinRoom(manualRoom)
                        } else {
                            Toast.makeText(context, "Digite o IP do host!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.primary
                    )
                ) {
                    Text("Conectar ao IP Manualmente", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/**
 * Tab 3: Friends, Partnerships, and Request Notifications
 */
@Composable
private fun FriendsAndPartnersTab(
    viewModel: KodanimaViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isLightTheme = colorScheme.background.luminance() > 0.5f
    val successAccent = if (isLightTheme) Color(0xFF008744) else NeonGreen
    val warningAccent = if (isLightTheme) Color(0xFFD85A00) else OrangeAccent
    var searchFriendInput by remember { mutableStateOf("") }
    var friendBeingInvited by remember { mutableStateOf<FriendEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add Friend Input
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Adicionar Parceiro de Animação",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchFriendInput,
                        onValueChange = { searchFriendInput = it },
                        label = { Text("Nome ou ID (ex: Gabriel #KD-1024)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedLabelColor = colorScheme.primary,
                            unfocusedLabelColor = colorScheme.onSurfaceVariant,
                            cursorColor = colorScheme.primary
                        )
                    )
                    Button(
                        onClick = {
                            if (searchFriendInput.isNotBlank()) {
                                viewModel.sendFriendRequest(searchFriendInput)
                                Toast.makeText(context, "Pedido de parceria enviado na rede Wi-Fi!", Toast.LENGTH_SHORT).show()
                                searchFriendInput = ""
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar")
                    }
                }
            }
        }

        // Incoming Friend Requests (Notificações de Parceria)
        if (uiState.friendRequests.isNotEmpty()) {
            Text(
                text = "Pedidos de Amizade Recebidos",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = warningAccent
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.friendRequests.forEach { req ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, warningAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                UserAvatarImage(
                                    name = req.senderName,
                                    avatarUri = req.senderAvatarUri,
                                    avatarBase64 = req.senderAvatarBase64,
                                    avatarColor = req.senderAvatarColor,
                                    size = 38.dp,
                                    borderWidth = 1.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${req.senderName} (${req.senderId})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Quer ser seu parceiro de animação!",
                                        fontSize = 10.sp,
                                        color = warningAccent
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = { viewModel.acceptFriendRequest(req.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(successAccent.copy(alpha = 0.18f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Aceitar", tint = successAccent, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.rejectFriendRequest(req.id) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(NeonPink.copy(alpha = 0.18f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Recusar", tint = NeonPink, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Friends List
        Text(
            text = "Lista de Amigos & Parceiros (${uiState.friendsList.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )

        if (uiState.friendsList.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    Text(
                        text = "Nenhum amigo adicionado ainda",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Pesquise pelo ID do smartphone ou nome do seu amigo para desenharem juntos.",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.friendsList, key = { it.id }) { friend ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                UserAvatarImage(
                                    name = friend.name,
                                    avatarUri = friend.avatarUri,
                                    avatarBase64 = friend.avatarBase64,
                                    avatarColor = friend.avatarColor,
                                    size = 42.dp,
                                    borderWidth = 1.5.dp
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = friend.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = friend.id,
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = friend.status.labelPt,
                                        fontSize = 10.sp,
                                        color = if (friend.status == com.example.model.FriendStatus.ONLINE || friend.status == com.example.model.FriendStatus.IN_ROOM) successAccent else colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { friendBeingInvited = friend },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("invite_friend_${friend.id}"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary.copy(alpha = 0.16f),
                                        contentColor = colorScheme.primary
                                    )
                                ) {
                                    Text("Convidar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { viewModel.removeFriend(friend.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Remover", tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }

    // Project chooser modal when inviting a friend
    val targetFriend = friendBeingInvited
    if (targetFriend != null) {
        val availableProjects = remember(uiState.savedProjects, uiState.project) {
            val base = uiState.savedProjects.toMutableList()
            if (base.none { it.id == uiState.project.id }) {
                base.add(0, uiState.project)
            }
            base
        }
        var chosenProject by remember(targetFriend.id, availableProjects) {
            mutableStateOf(availableProjects.first())
        }

        AlertDialog(
            onDismissRequest = { friendBeingInvited = null },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = colorScheme.primary)
                    Text("Escolher Projeto para Convidar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatarImage(
                            name = targetFriend.name,
                            avatarUri = targetFriend.avatarUri,
                            avatarBase64 = targetFriend.avatarBase64,
                            avatarColor = targetFriend.avatarColor,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Selecione qual projeto compartilhar com ${targetFriend.name}:",
                            fontSize = 12.sp,
                            color = colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableProjects.forEach { proj ->
                            val isSelected = proj.id == chosenProject.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { chosenProject = proj },
                                color = if (isSelected) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = proj.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${proj.frames.size} quadros • ${proj.fps} FPS • ${proj.resolution.width}x${proj.resolution.height}",
                                            fontSize = 10.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.inviteFriendToProject(targetFriend, chosenProject)
                        Toast.makeText(
                            context,
                            "Convite de '${chosenProject.name}' enviado para ${targetFriend.name}!",
                            Toast.LENGTH_SHORT
                        ).show()
                        friendBeingInvited = null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_send_invite_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) {
                    Text("Enviar Convite", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { friendBeingInvited = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancelar", color = colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
