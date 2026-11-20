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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import com.example.engine.ProjectCompressionManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.MaterialTheme
import com.example.engine.CursorHelper
import com.example.engine.PermissionHelper
import com.example.model.AppScreen
import com.example.model.AppUiMode
import com.example.model.NetworkTransportMode
import com.example.model.PressureCurve
import com.example.model.PressureMode
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.OrangeAccent
import com.example.viewmodel.KodanimaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: KodanimaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs = uiState.preferences
    val tabletSettings = prefs.tabletSettings
    val context = LocalContext.current

    var customLevelsText by remember { mutableStateOf(tabletSettings.customPressureLevels.toString()) }

    // Picker for custom cursor files (.cur, .ani, .png, etc.)
    val cursorFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = CursorHelper.saveCursorToInternalStorage(context, uri)
            if (savedPath != null) {
                viewModel.setCustomCursorPath(savedPath)
                Toast.makeText(context, "Cursor personalizado importado com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Não foi possível carregar o arquivo de cursor.", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler {
        viewModel.saveCurrentProject(context)
        viewModel.navigateTo(AppScreen.HOME)
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.saveCurrentProject(context)
                            viewModel.navigateTo(AppScreen.HOME)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "Configurações do Kodanima 2D v${com.example.BuildConfig.VERSION_NAME}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Aparência, 10 Temas, Mesa Digitalizadora, Rede e Motor",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Appearance Mode (Mobile vs PC)
            SectionHeader(title = "Aparência da Interface", subtitle = "Escolha o layout ideal para o seu dispositivo")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val isMobile = prefs.uiMode == AppUiMode.MOBILE
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isMobile) 2.dp else 1.dp,
                            color = if (isMobile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.setUiMode(AppUiMode.MOBILE) }
                        .testTag("mode_mobile_card"),
                    color = if (isMobile) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isMobile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = if (isMobile) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Aparência Mobile",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMobile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isMobile) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Controles táteis compactos com barra de ferramentas flutuante, timeline horizontal deslizável e navegação ergonômica com uma mão.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // PC Desktop Mode Card
                val isPc = prefs.uiMode == AppUiMode.PC
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (isPc) 2.dp else 1.dp,
                            color = if (isPc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.setUiMode(AppUiMode.PC) }
                        .testTag("mode_pc_card"),
                    color = if (isPc) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DesktopWindows,
                                contentDescription = null,
                                tint = if (isPc) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Aparência PC (Desktop Studio)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPc) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isPc) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mesma interface do Kodanima2D de computador: barra de menus clássica, doca lateral fixa com atalhos, painéis acopláveis e suporte a mesa digitalizadora.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Timeline Mode: Heritage Junior vs Heritage Profissional
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isJr = prefs.timelineLayoutMode == com.example.model.TimelineLayoutMode.HERITAGE_JUNIOR
                    val isPro = prefs.timelineLayoutMode == com.example.model.TimelineLayoutMode.HERITAGE_PROFISSIONAL

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (isJr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { viewModel.setTimelineLayoutMode(com.example.model.TimelineLayoutMode.HERITAGE_JUNIOR) },
                        color = if (isJr) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Heritage Junior", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isJr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text("Timeline flutuante compacta", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                            .clickable { viewModel.setTimelineLayoutMode(com.example.model.TimelineLayoutMode.HERITAGE_PROFISSIONAL) },
                        color = if (isPro) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Heritage Profissional", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text("Timeline de ponta a ponta", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Section 2: 10 Temas Globais do Kodanima 2D
            SectionHeader(
                title = "Temas da Interface (10 Estilos)",
                subtitle = "Paletas claras e escuras aplicadas globalmente em todo o estúdio"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.model.KodanimaTheme.entries.forEach { themeOption ->
                    val isSelected = prefs.theme == themeOption
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(themeOption.accent) else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.setTheme(themeOption) }
                            .testTag("theme_card_${themeOption.name.lowercase()}"),
                        color = if (isSelected) Color(themeOption.panel) else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(themeOption.bg))
                                        .border(1.dp, Color(themeOption.text).copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                                        .padding(5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(themeOption.panel))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(themeOption.accent))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(themeOption.secondaryAccent))
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = themeOption.namePt,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) Color(themeOption.accent) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(themeOption.accent).copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = if (themeOption.isLight) "Claro" else "Escuro",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(themeOption.accent),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = themeOption.taglinePt,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(themeOption.text).copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selecionado",
                                    tint = Color(themeOption.accent),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Seta / Cursor da Mesa Digitalizadora (Virtual Cursor & Custom Cursor)
            SectionHeader(
                title = "Seta da Mesa Digitalizadora (Cursor do Aplicativo)",
                subtitle = "Essencial para mesas que não mostram seta nativa no Android. Círculo que cresce com o tamanho do pincel."
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        title = "Ativar Seta do Aplicativo",
                        subtitle = "Exibe seta circular vazada com a cor do tema que cresce/diminui conforme o tamanho do pincel",
                        checked = tabletSettings.enableVirtualCursor,
                        onCheckedChange = { viewModel.setVirtualCursorEnabled(it) }
                    )

                    Text("Cor da Seta / Cursor:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val cursorColors = listOf(
                        0xFFFFFFFF to "Branco",
                        0xFF987EDC to "Roxo Kodanima",
                        0xFF00D2FF to "Ciano Elétrico",
                        0xFF00E676 to "Verde Neon",
                        0xFFFF3366 to "Rosa Magenta",
                        0xFFFF7A00 to "Laranja",
                        0xFFFFD700 to "Dourado",
                        0xFFFF1744 to "Vermelho"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cursorColors.forEach { (cVal, name) ->
                            val isSelected = tabletSettings.cursorColor == cVal
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(cVal))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setCursorColor(cVal) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = name,
                                        tint = if (cVal == 0xFFFFFFFF || cVal == 0xFFFFD700) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Aparência da Seta:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = if (tabletSettings.customCursorPath != null) "Cursor personalizado ativo (.cur / .ani / .png)"
                                else "Círculo vazado personalizável com realce ativo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val activeCursorColor = Color(tabletSettings.cursorColor)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(prefs.theme.bg))
                                .border(2.dp, activeCursorColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, activeCursorColor.copy(alpha = 0.6f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(activeCursorColor)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { cursorFilePicker.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Importar Seta (.cur / .ani / .png)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (tabletSettings.customCursorPath != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setCustomCursorPath(null)
                                    Toast.makeText(context, "Cursor padrão restaurado!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Padrão", fontSize = 11.sp)
                            }
                        }
                    }

                    Text("Inversão de Coordenadas da Caneta / Mesa", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    SettingToggleRow(
                        title = "Inverter Eixo X (Horizontal)",
                        subtitle = "Inverte as coordenadas horizontais para mesas que mapeiam a caneta invertida",
                        checked = tabletSettings.invertX,
                        onCheckedChange = { viewModel.setInvertX(it) }
                    )
                    SettingToggleRow(
                        title = "Inverter Eixo Y (Vertical)",
                        subtitle = "Inverte as coordenadas verticais para mesas com orientação Y invertida",
                        checked = tabletSettings.invertY,
                        onCheckedChange = { viewModel.setInvertY(it) }
                    )
                }
            }

            // Section 4: Modos de Pressão da Mesa
            SectionHeader(
                title = "Níveis de Pressão da Caneta",
                subtitle = "Configure os níveis de sensibilidade suportados pela sua mesa digitalizadora"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Modo de Resolução de Pressão:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    PressureMode.values().forEach { mode ->
                        val isSelected = tabletSettings.pressureMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable {
                                    val custom = customLevelsText.toIntOrNull() ?: 8192
                                    viewModel.setPressureMode(mode, custom)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.labelPt,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (mode) {
                                        PressureMode.AUTO -> "Detecta automaticamente a sensibilidade do hardware"
                                        PressureMode.LEVELS_1024 -> "Padrão de mesas básicas de entrada (1024 níveis)"
                                        PressureMode.LEVELS_2048 -> "Padrão clássico intermediário (2048 níveis)"
                                        PressureMode.LEVELS_8192 -> "Padrão profissional Wacom, Huion e XP-Pen (8192 níveis)"
                                        PressureMode.LEVELS_16384 -> "Ultra-alta precisão para mesas modernas (16384 níveis)"
                                        PressureMode.CUSTOM -> "Digite manualmente a quantidade de níveis da sua mesa"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (tabletSettings.pressureMode == PressureMode.CUSTOM) {
                        OutlinedTextField(
                            value = customLevelsText,
                            onValueChange = { str ->
                                if (str.all { it.isDigit() }) {
                                    customLevelsText = str
                                    str.toIntOrNull()?.let { lvls ->
                                        viewModel.setPressureMode(PressureMode.CUSTOM, lvls)
                                    }
                                }
                            },
                            label = { Text("Digitar Níveis de Pressão") },
                            placeholder = { Text("Ex: 4096 ou 32768") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Curva de Resposta de Pressão:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressureCurve.values().forEach { curve ->
                            val isSelected = tabletSettings.pressureCurve == curve
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setPressureCurve(curve) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = curve.labelPt.substringBefore(" ("),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Teclado, Mouse e Rejeição de Palma
            SectionHeader(
                title = "Teclado, Mouse e Rejeição de Palma",
                subtitle = "Compatibilidade com atalhos de hardware e prevenção de toques acidentais"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        title = "Rejeição Rigorosa de Palma",
                        subtitle = "Desenha apenas quando a ponta da caneta stylus toca a tela",
                        checked = tabletSettings.palmRejectionStrict,
                        onCheckedChange = {
                            viewModel.updateTabletSettings(tabletSettings.copy(palmRejectionStrict = it))
                        }
                    )

                    SettingToggleRow(
                        title = "Atalhos de Teclado Físico",
                        subtitle = "B (Caneta), E (Borracha), G (Balde), Espaço (Play), Ctrl+Z (Desfazer), Ctrl+S (Salvar)",
                        checked = tabletSettings.keyboardShortcutsEnabled,
                        onCheckedChange = {
                            viewModel.updateTabletSettings(tabletSettings.copy(keyboardShortcutsEnabled = it))
                        }
                    )

                    SettingToggleRow(
                        title = "Zoom com a Roda do Mouse (Scroll)",
                        subtitle = "Girar a roda do mouse aproxima ou afasta a área de desenho",
                        checked = tabletSettings.mouseScrollZoomEnabled,
                        onCheckedChange = {
                            viewModel.updateTabletSettings(tabletSettings.copy(mouseScrollZoomEnabled = it))
                        }
                    )
                }
            }

            // Section 5b: Linha do Tempo & Ferramentas
            SectionHeader(
                title = "Linha do Tempo e Ferramentas",
                subtitle = "Assistente de encaixe de quadros (Snap) e comportamento da borracha"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        title = "Assistente de Encaixe por Bolinhas na Timeline (Snap)",
                        subtitle = "Ao esticar o quadro com a bolinha, as bolinhas de segundos e quadros acendem e puxam o quadro automaticamente como assistente",
                        checked = prefs.timelineSnapAssistant,
                        onCheckedChange = { viewModel.toggleTimelineSnapAssistant() }
                    )

                    SettingToggleRow(
                        title = "Sensibilidade à Pressão na Borracha",
                        subtitle = "A borracha varia de espessura de acordo com a pressão da caneta stylus ou mesa digitalizadora",
                        checked = prefs.eraserUsesPressure,
                        onCheckedChange = { viewModel.setEraserUsesPressure(it) }
                    )
                }
            }

            // Section 6: Tamanho da Interface & Acessibilidade
            SectionHeader(
                title = "Tamanho da Interface",
                subtitle = "Diminua ou aumente os botões e painéis para ganhar mais espaço no canvas"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Escala de Densidade dos Controles:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    val scales = listOf(
                        0.70f to "70% (Muito Pequena)",
                        0.85f to "85% (Pequena / Padrão Kodanima)",
                        1.00f to "100% (Normal)",
                        1.15f to "115% (Grande)"
                    )

                    scales.forEach { (scaleVal, label) ->
                        val isSelected = Math.abs(prefs.uiScale - scaleVal) < 0.04f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .clickable { viewModel.setUiScale(scaleVal) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Section 7: Desempenho e Aceleração por Hardware
            SectionHeader(
                title = "Desempenho e Aceleração por Hardware",
                subtitle = "Substituição do DirectX Windows para Vulkan/OpenGL ES com sistema de chunks"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        title = "Sistema de Chunks (Culling de Viewport)",
                        subtitle = "Ao dar zoom, processa e renderiza somente os traços visíveis na tela, reduzindo travamentos",
                        checked = prefs.enableChunkCulling,
                        onCheckedChange = { viewModel.setChunkCullingEnabled(it) }
                    )

                    SettingToggleRow(
                        title = "Aceleração por Hardware GPU (Vulkan / OpenGL)",
                        subtitle = "Utiliza Skia GPU e instruções SIMD do processador multi-core para desenho instantâneo",
                        checked = prefs.enableGpuAcceleration,
                        onCheckedChange = { viewModel.setGpuAccelerationEnabled(it) }
                    )

                    SettingToggleRow(
                        title = "Modo Economia de Bateria / Baixo Desempenho",
                        subtitle = "Otimiza a taxa de quadros e desativa efeitos pesados em celulares modestos",
                        checked = prefs.lowPowerMode,
                        onCheckedChange = { viewModel.setLowPowerMode(it) }
                    )
                }
            }

            // Section 8: Armazenamento e Projetos Compactados (.koda2d)
            SectionHeader(
                title = "Armazenamento & Compactação (.koda2d)",
                subtitle = "Economiza espaço no celular compactando projetos ao fechar e limpando arquivos descompactados"
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingToggleRow(
                        title = "Compactar Projetos ao Fechar (.koda2d)",
                        subtitle = "Ao sair do projeto, compacta automaticamente em .koda2d e apaga o cache descompactado, liberando espaço",
                        checked = prefs.autoCompressOnClose,
                        onCheckedChange = { viewModel.setAutoCompressOnClose(it) }
                    )

                    val defaultDir = remember(context) { ProjectCompressionManager.getDefaultProjectsDirectory(context).absolutePath }
                    val currentResolvedDir = remember(prefs.customProjectsDirectory) {
                        ProjectCompressionManager.getProjectsDirectory(context, prefs.customProjectsDirectory).absolutePath
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pasta de Destino dos Projetos .koda2d:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentResolvedDir,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }

                        var customDirInput by remember(prefs.customProjectsDirectory) { mutableStateOf(prefs.customProjectsDirectory) }

                        OutlinedTextField(
                            value = customDirInput,
                            onValueChange = {
                                customDirInput = it
                                viewModel.setCustomProjectsDirectory(it.trim())
                            },
                            label = { Text("Caminho ou Nome de Pasta Personalizada") },
                            placeholder = { Text("Deixe em branco para usar o padrão KodanimaProjects") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (prefs.customProjectsDirectory.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        customDirInput = ""
                                        viewModel.setCustomProjectsDirectory("")
                                        Toast.makeText(context, "Pasta padrão restaurada!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeAccent)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restaurar Padrão", fontSize = 11.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    val count = viewModel.compactAllProjectsNow(context)
                                    Toast.makeText(context, "$count projetos compactados em .koda2d!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compactar Todos Agora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Durante o desenho, o salvamento (Ctrl+S ou ícone) grava instantaneamente na memória de trabalho sem comprimir. Ao fechar, tudo é empacotado em .koda2d e o cache é desalocado.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Multiplayer LAN & Cooperative Settings
            SectionHeader(
                title = "Rede & Multiplayer LAN (Coop)",
                subtitle = "Conexão local Wi-Fi 2.4G/5G, Wi-Fi Direct ou Bluetooth Offline sem roteador"
            )

            val settingsPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                Toast.makeText(context, "Status de permissões atualizado!", Toast.LENGTH_SHORT).show()
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingToggleRow(
                        title = "Ativar Multiplayer LAN",
                        subtitle = "Permite abrir salas locais, receber solicitações de amizade e desenhar em tempo real simultaneamente.",
                        checked = prefs.isMultiplayerEnabled,
                        onCheckedChange = { viewModel.setMultiplayerEnabled(it) }
                    )

                    SettingToggleRow(
                        title = "Mostrar Barra de Amigos no Canvas",
                        subtitle = "Exibe uma faixa horizontal compacta com fotos e ping dos amigos conectados. Desative para deixar o canvas mais limpo.",
                        checked = prefs.showCanvasCollaboratorsHud,
                        onCheckedChange = { viewModel.setShowCanvasCollaboratorsHud(it) }
                    )

                    SettingToggleRow(
                        title = "Oferecer brushes de amigos (LAN)",
                        subtitle = "Quando um amigo desenhar com um brush importado diferente, pergunta abaixo da barra de pincéis se deseja baixar para você.",
                        checked = prefs.autoPromptFriendBrushes,
                        onCheckedChange = { viewModel.setAutoPromptFriendBrushes(it) }
                    )

                    Text(
                        text = "Modo de Rede & Pareamento (Internet / Offline):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val transportOptions = listOf(
                        Triple(
                            NetworkTransportMode.WIFI_LAN,
                            "Wi-Fi Local (Roteador 2.4 GHz & 5 GHz)",
                            "Sincronização NSD + TCP sub-rede de baixa latência em roteadores de banda dupla ou Hotspot."
                        ),
                        Triple(
                            NetworkTransportMode.WIFI_DIRECT,
                            "Wi-Fi Direct (Pareamento por Proximidade)",
                            "Conecta dois celulares diretamente sem roteador e sem internet."
                        ),
                        Triple(
                            NetworkTransportMode.BLUETOOTH,
                            "Bluetooth Offline (Sem Internet / Sem Wi-Fi)",
                            "Usa conexão direta RFCOMM Bluetooth para desenhar em equipe em qualquer lugar."
                        )
                    )

                    transportOptions.forEach { (mode, title, desc) ->
                        val isSel = prefs.networkTransportMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.setNetworkTransportMode(mode)
                                    val missing = when (mode) {
                                        NetworkTransportMode.WIFI_DIRECT -> if (!PermissionHelper.hasWifiDirectPermission(context)) PermissionHelper.getWifiDirectPermissions() else emptyArray()
                                        NetworkTransportMode.BLUETOOTH -> if (!PermissionHelper.hasBluetoothPermission(context)) PermissionHelper.getBluetoothPermissions() else emptyArray()
                                        NetworkTransportMode.WIFI_LAN -> emptyArray()
                                    }
                                    if (missing.isNotEmpty()) {
                                        settingsPermLauncher.launch(missing)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val modeIcon = when (mode) {
                                    NetworkTransportMode.WIFI_LAN -> Icons.Default.Router
                                    NetworkTransportMode.WIFI_DIRECT -> Icons.Default.Wifi
                                    NetworkTransportMode.BLUETOOTH -> Icons.Default.Bluetooth
                                }
                                Icon(
                                    modeIcon,
                                    contentDescription = null,
                                    tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                                if (isSel) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Permissions Health Banner
                    val hasNotif = PermissionHelper.hasNotificationPermission(context)
                    val hasProx = PermissionHelper.hasWifiDirectPermission(context) && PermissionHelper.hasBluetoothPermission(context)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, null, tint = if (hasNotif) NeonGreen else OrangeAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasNotif) "Notificação do Servidor Local: Permitida" else "Notificação do Servidor Local: Pendente",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (hasNotif) MaterialTheme.colorScheme.secondary else OrangeAccent
                                )
                            }
                            Text(
                                text = if (hasProx) "Pareamento por Proximidade (Wi-Fi Direct / Bluetooth): Permitido" else "Pareamento por Proximidade: Pendente para conexão sem roteador",
                                fontSize = 11.sp,
                                color = if (hasProx) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Armazenamento limpo: ao desinstalar o app, todos os arquivos locais são removidos automaticamente sem backup na nuvem.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            )
                            if (PermissionHelper.hasMissingInitialPermissions(context)) {
                                Button(
                                    onClick = {
                                        settingsPermLauncher.launch(PermissionHelper.getInitialRuntimePermissions())
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                ) {
                                    Text("Conceder Permissões de Servidor e Proximidade", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = if (prefs.isMultiplayerEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = if (prefs.isMultiplayerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (prefs.isMultiplayerEnabled)
                                    "Multiplayer LAN ativo: desfrute de sincronização instantânea de traços, ping ao vivo e colaboração bidirecional."
                                else
                                    "Multiplayer desativado: servidores locais e busca na rede desligados para economizar bateria e rede.",
                                fontSize = 11.sp,
                                color = if (prefs.isMultiplayerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Pipeline Status Banner
            val hwProfile = remember { com.example.engine.HardwareCapabilitiesEngine.currentProfile }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(26.dp))
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "GPU: ${hwProfile.gpuRenderer} (${hwProfile.gpuFamily.displayName})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "CPU: ${hwProfile.cpuAbi.labelPt} • ${hwProfile.cpuCoreCount} Núcleos • ${hwProfile.totalRamMb} MB RAM",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Backend: ${if (hwProfile.supportsVulkan) hwProfile.vulkanVersionString else "OpenGL ES 3.x"} + Skia NEON Batching • Cache O(1) + BitmapPool (${hwProfile.bitmapPoolMaxBytes / (1024 * 1024)} MB)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            fontSize = 16.sp,
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

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}
