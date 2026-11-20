package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.HardwareCapabilitiesEngine
import com.example.engine.KodanimaBitmapPool
import com.example.engine.PermissionHelper
import com.example.model.AppScreen
import com.example.model.AppUiMode
import com.example.ui.components.ColdStartSplashManager
import com.example.ui.components.KodanimaColdStartSplashOverlay
import com.example.ui.components.UserAvatarImage
import com.example.ui.screens.AnimationEditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NetworkStudioScreen
import com.example.ui.screens.PcEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.viewmodel.KodanimaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: KodanimaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        enableEdgeToEdge()
        if (ColdStartSplashManager.shouldShowOnColdStart()) {
            ColdStartSplashManager.preloadPoster(applicationContext)
        }
        HardwareCapabilitiesEngine.initialize(applicationContext)
        viewModel.initializeWithContext(this)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val baseDensity = LocalDensity.current
            val currentScale = uiState.preferences.uiScale.coerceIn(0.6f, 1.4f)
            val customDensity = Density(
                density = baseDensity.density * currentScale,
                fontScale = baseDensity.fontScale * currentScale
            )

            var showColdStartSplash by remember {
                mutableStateOf(ColdStartSplashManager.shouldShowOnColdStart())
            }
            var dismissFirstLaunchPerms by remember { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                viewModel.markInitialPermissionsPrompted()
                dismissFirstLaunchPerms = true
                val grantedCount = results.values.count { it }
                if (grantedCount > 0) {
                    Toast.makeText(
                        this,
                        "Permissões ativadas! Servidor Local e pareamento por proximidade prontos.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val showInitialPermissionsDialog = !dismissFirstLaunchPerms &&
                !uiState.preferences.hasPromptedInitialPermissions &&
                PermissionHelper.hasMissingInitialPermissions(this)

            MyApplicationTheme(kodanimaTheme = uiState.preferences.theme) {
                val colorScheme = MaterialTheme.colorScheme
                CompositionLocalProvider(
                    LocalDensity provides customDensity
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (uiState.currentScreen) {
                                AppScreen.HOME -> {
                                    HomeScreen(viewModel = viewModel)
                                }
                                AppScreen.SETTINGS -> {
                                    SettingsScreen(viewModel = viewModel)
                                }
                                AppScreen.NETWORK_STUDIO -> {
                                    NetworkStudioScreen(viewModel = viewModel)
                                }
                                AppScreen.EDITOR -> {
                                    when (uiState.preferences.uiMode) {
                                        AppUiMode.PC -> {
                                            PcEditorScreen(viewModel = viewModel)
                                        }
                                        AppUiMode.MOBILE -> {
                                            AnimationEditorScreen(viewModel = viewModel)
                                        }
                                    }
                                }
                            }

                            // 0. First-Launch Play-Compliant Runtime Permissions Dialog
                            if (showInitialPermissionsDialog && !showColdStartSplash) {
                                Dialog(
                                    onDismissRequest = {
                                        viewModel.markInitialPermissionsPrompted()
                                        dismissFirstLaunchPerms = true
                                    },
                                    properties = DialogProperties(
                                        dismissOnClickOutside = false,
                                        usePlatformDefaultWidth = false
                                    )
                                ) {
                                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = colorScheme.surface,
                                            border = BorderStroke(1.5.dp, colorScheme.primary),
                                            tonalElevation = 10.dp
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(20.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.NotificationsActive,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = "Permissões do Kodanima 2D",
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = "Para que o Servidor Local e o modo cooperativo funcionem sem quedas, ative apenas as permissões essenciais:",
                                                    fontSize = 13.sp,
                                                    color = colorScheme.onSurfaceVariant,
                                                    lineHeight = 18.sp
                                                )
                                                Surface(
                                                    color = colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.Top) {
                                                            Icon(Icons.Default.NotificationsActive, null, tint = NeonGreen, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "Notificações: mantém o Servidor Local ativo com botão 'Encerrar Servidor' direto na notificação.",
                                                                fontSize = 12.sp,
                                                                color = colorScheme.onSurface
                                                            )
                                                        }
                                                        Row(verticalAlignment = Alignment.Top) {
                                                            Icon(Icons.Default.WifiTethering, null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "Pareamento por Proximidade (Wi-Fi Direct & Bluetooth): conecta amigos mesmo sem internet nem roteador.",
                                                                fontSize = 12.sp,
                                                                color = colorScheme.onSurface
                                                            )
                                                        }
                                                        Text(
                                                            text = "Armazenamento e Galeria: usa o seletor seguro do sistema (sem ler seus arquivos privados). Ao desinstalar, todos os arquivos do app são apagados.",
                                                            fontSize = 11.sp,
                                                            color = colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            val perms = PermissionHelper.getInitialRuntimePermissions()
                                                            if (perms.isNotEmpty()) {
                                                                permissionLauncher.launch(perms)
                                                            } else {
                                                                viewModel.markInitialPermissionsPrompted()
                                                                dismissFirstLaunchPerms = true
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                            .testTag("grant_initial_permissions_button"),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = colorScheme.primary,
                                                            contentColor = colorScheme.onPrimary
                                                        )
                                                    ) {
                                                        Text("Permitir Agora", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.markInitialPermissionsPrompted()
                                                            dismissFirstLaunchPerms = true
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 48.dp)
                                                    ) {
                                                        Text("Agora Não", color = colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 1. Floating Friend Request Window
                            val incomingReq = uiState.incomingFriendRequestModal
                            if (incomingReq != null) {
                                Dialog(
                                    onDismissRequest = { viewModel.dismissIncomingFriendRequest() },
                                    properties = DialogProperties(
                                        dismissOnClickOutside = false,
                                        usePlatformDefaultWidth = false
                                    )
                                ) {
                                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 22.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = colorScheme.surface,
                                            border = BorderStroke(1.5.dp, colorScheme.primary),
                                            tonalElevation = 10.dp
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.padding(20.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.People,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary
                                                    )
                                                    Text(
                                                        "Solicitação de Amizade LAN",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 17.sp,
                                                        color = colorScheme.onSurface
                                                    )
                                                }
                                                UserAvatarImage(
                                                    name = incomingReq.senderName,
                                                    avatarUri = incomingReq.senderAvatarUri,
                                                    avatarBase64 = incomingReq.senderAvatarBase64,
                                                    avatarColor = incomingReq.senderAvatarColor,
                                                    size = 64.dp,
                                                    borderWidth = 2.dp
                                                )
                                                Text(
                                                    text = incomingReq.senderName,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "ID: #${incomingReq.senderId}",
                                                    fontSize = 12.sp,
                                                    color = colorScheme.primary
                                                )
                                                Text(
                                                    text = "${incomingReq.senderName} enviou uma solicitação para ser seu parceiro no Kodanima2D!",
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.rejectFriendRequest(incomingReq.id) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                            .testTag("friend_request_reject_button")
                                                    ) {
                                                        Text("✕ Recusar", color = NeonPink, fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.acceptFriendRequest(incomingReq.id) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                            .testTag("friend_request_accept_button"),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = NeonGreen,
                                                            contentColor = Color.Black
                                                        )
                                                    ) {
                                                        Text("✓ Aceitar", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Global Canvas Partnership Knock Modal (Reliable hitboxes across Moto G & Tecno BG6)
                            val pendingKnock = uiState.pendingKnockRequest
                            if (pendingKnock != null) {
                                Dialog(
                                    onDismissRequest = { viewModel.respondToKnock(false) },
                                    properties = DialogProperties(
                                        dismissOnClickOutside = false,
                                        usePlatformDefaultWidth = false
                                    )
                                ) {
                                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 22.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = colorScheme.surface,
                                            border = BorderStroke(1.5.dp, NeonGreen),
                                            tonalElevation = 12.dp
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.padding(20.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PersonAdd,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary
                                                    )
                                                    Text(
                                                        "Solicitação de Parceria no Canvas",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 17.sp,
                                                        color = colorScheme.onSurface
                                                    )
                                                }
                                                UserAvatarImage(
                                                    name = pendingKnock.user.name,
                                                    avatarUri = pendingKnock.user.avatarUri,
                                                    avatarBase64 = pendingKnock.user.avatarBase64,
                                                    avatarColor = pendingKnock.user.avatarColor,
                                                    size = 64.dp,
                                                    borderWidth = 2.dp
                                                )
                                                Text(
                                                    text = pendingKnock.user.name,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "ID: #${pendingKnock.user.id}",
                                                    fontSize = 12.sp,
                                                    color = colorScheme.primary
                                                )
                                                Text(
                                                    text = "${pendingKnock.user.name} quer entrar na sua sessão colaborativa para desenhar com você agora. Permitir?",
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.respondToKnock(false) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 50.dp)
                                                            .testTag("knock_reject_button")
                                                    ) {
                                                        Text("✕ Não", color = NeonPink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.respondToKnock(true) },
                                                        modifier = Modifier
                                                            .weight(1.3f)
                                                            .heightIn(min = 50.dp)
                                                            .testTag("knock_accept_button"),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = NeonGreen,
                                                            contentColor = Color.Black
                                                        )
                                                    ) {
                                                        Text("✓ Sim, Permitir", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Global Room Invite Modal
                            val incomingInvite = uiState.incomingRoomInviteModal
                            if (incomingInvite != null) {
                                val room = incomingInvite.roomInfo
                                Dialog(
                                    onDismissRequest = { viewModel.dismissIncomingRoomInvite() },
                                    properties = DialogProperties(
                                        dismissOnClickOutside = false,
                                        usePlatformDefaultWidth = false
                                    )
                                ) {
                                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 22.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = colorScheme.surface,
                                            border = BorderStroke(1.5.dp, NeonGreen),
                                            tonalElevation = 10.dp
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(20.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Hub,
                                                        contentDescription = null,
                                                        tint = NeonGreen
                                                    )
                                                    Text(
                                                        "Convite para Desenhar Juntos",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 17.sp,
                                                        color = colorScheme.onSurface
                                                    )
                                                }
                                                UserAvatarImage(
                                                    name = room.hostName,
                                                    avatarUri = room.hostAvatarUri,
                                                    avatarBase64 = incomingInvite.inviterAvatarBase64 ?: room.hostAvatarBase64,
                                                    avatarColor = room.hostAvatarColor,
                                                    size = 64.dp,
                                                    borderWidth = 2.dp
                                                )
                                                Text(
                                                    text = room.hostName,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Projeto: ${room.roomName}",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colorScheme.primary
                                                )
                                                Text(
                                                    text = "Sala ${room.roomId} • ${room.frameCount} quadros • ${room.fps} FPS",
                                                    fontSize = 11.sp,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${room.hostName} convidou você para colaborar neste projeto em tempo real!",
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.dismissIncomingRoomInvite() },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .heightIn(min = 48.dp)
                                                            .testTag("invite_reject_button")
                                                    ) {
                                                        Text("✕ Recusar", color = NeonPink, fontWeight = FontWeight.Bold)
                                                    }
                                                    Button(
                                                        onClick = { viewModel.acceptRoomInvite(incomingInvite) },
                                                        modifier = Modifier
                                                            .weight(1.3f)
                                                            .heightIn(min = 48.dp)
                                                            .testTag("invite_accept_button"),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = NeonGreen,
                                                            contentColor = Color.Black
                                                        )
                                                    ) {
                                                        Text("✓ Aceitar e Entrar", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Host Shutdown Notification Modal ("o dono da casa saiu :(")
                            val hostShutdownMsg = uiState.hostShutdownAlertMessage
                            if (hostShutdownMsg != null) {
                                Dialog(
                                    onDismissRequest = { viewModel.dismissHostShutdownAlert() },
                                    properties = DialogProperties(
                                        dismissOnClickOutside = true,
                                        usePlatformDefaultWidth = false
                                    )
                                ) {
                                    CompositionLocalProvider(LocalDensity provides baseDensity) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp),
                                            shape = RoundedCornerShape(20.dp),
                                            color = colorScheme.surface,
                                            border = BorderStroke(1.5.dp, NeonPink),
                                            tonalElevation = 12.dp
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.padding(22.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.WifiTethering,
                                                    contentDescription = null,
                                                    tint = NeonPink,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Text(
                                                    text = "Servidor Desconectado",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                                Text(
                                                    text = hostShutdownMsg,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = TextAlign.Center,
                                                    color = NeonPink
                                                )
                                                Button(
                                                    onClick = { viewModel.dismissHostShutdownAlert() },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .testTag("host_shutdown_ok_button"),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = NeonPink,
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Text("Entendi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Cold-start Animated Studio Splash (only runs once per cold process launch)
                            if (showColdStartSplash) {
                                KodanimaColdStartSplashOverlay(
                                    onFinished = { showColdStartSplash = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        KodanimaBitmapPool.trimMemory(level)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        KodanimaBitmapPool.clear()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && viewModel.handlePhysicalKeyEvent(event, this)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
