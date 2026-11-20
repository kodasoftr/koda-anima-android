package com.example.model

import android.view.KeyEvent

enum class AppScreen {
    HOME,
    EDITOR,
    SETTINGS,
    NETWORK_STUDIO
}

enum class AppUiMode(val labelPt: String, val descriptionPt: String) {
    MOBILE(
        "Aparência Mobile",
        "Otimizada para telas de toque com botões ao alcance do polegar e timeline inferior deslizável."
    ),
    PC(
        "Aparência PC (Desktop)",
        "Interface clássica do Kodanima2D de computador: barra de menus superior, painéis laterais de camadas e ferramentas, suporte a atalhos de teclado e mesa digitalizadora."
    )
}

enum class TimelineLayoutMode(val id: Int, val labelPt: String, val descriptionPt: String) {
    HERITAGE_JUNIOR(
        0,
        "Heritage Junior",
        "Timeline pequena, compacta e centralizada no rodapé como no clássico do Windows."
    ),
    HERITAGE_PROFISSIONAL(
        1,
        "Heritage Profissional",
        "Timeline de ponta a ponta (vai de um canto ao outro da tela) com mini-ícones compactos."
    )
}

enum class KodanimaTheme(
    val id: Int,
    val namePt: String,
    val bg: Long,
    val panel: Long,
    val accent: Long,
    val text: Long,
    val secondaryAccent: Long = 0xFF00E5FF,
    val isLight: Boolean = false,
    val taglinePt: String = ""
) {
    ROXO_BLACK(0, "Roxo Black (Padrão)", 0xFF120A1E, 0xFF1C152C, 0xFF987EDC, 0xFFEDE7F8, 0xFF00E5FF, false, "Violeta profundo clássico"),
    PINK_VINHO(1, "Pink Vinho", 0xFF26121F, 0xFF341C2B, 0xFFE26CA8, 0xFFF8E8F1, 0xFFFFB74D, false, "Borgonha vibrante com rosé"),
    BRANCO_CINZA_AZULADO(2, "Branco Gelo Azulado", 0xFFECEFF6, 0xFFFFFFFF, 0xFF3E63B3, 0xFF1B2230, 0xFFE65100, true, "Claro diurno estilo papel"),
    LARANJA_AMADEIRADO(3, "Laranja Amadeirado", 0xFF231912, 0xFF33251B, 0xFFE8964F, 0xFFF9ECDC, 0xFFFFD54F, false, "Estúdio quente em cedro"),
    CIANO_CYBER(4, "Ciano Cyber Neon", 0xFF0A1017, 0xFF121C28, 0xFF00E5FF, 0xFFE4F6FF, 0xFFFF4081, false, "Futurista alto contraste"),
    ESMERALDA_STUDIO(5, "Esmeralda Brasil", 0xFF0C1A13, 0xFF14271E, 0xFF00E676, 0xFFE4F7EC, 0xFFFFD600, false, "Verde criativo e dourado"),
    AZUL_MEIA_NOITE(6, "Azul Meia-Noite", 0xFF0A1124, 0xFF131D3A, 0xFF5C9DFF, 0xFFE5EEFF, 0xFF64FFDA, false, "Cobalto focado para longas sessões"),
    PAPEL_AQUARELA(7, "Papel Aquarela (Claro)", 0xFFF5EFE4, 0xFFFFFDF8, 0xFFB85D3B, 0xFF2A211B, 0xFF2E7D6F, true, "Pergaminho suave anti-fadiga"),
    CARMIM_SAKURA(8, "Carmim Sakura", 0xFF200D14, 0xFF2E141E, 0xFFFF5E7E, 0xFFFDECEF, 0xFFFFB74D, false, "Flor de cerejeira e rubi"),
    GRAFITE_PRO(9, "Grafite Estúdio Pro", 0xFF15171C, 0xFF20232A, 0xFF4DA6FF, 0xFFE8EBF0, 0xFFA6E22E, false, "Neutro calibrado para cor real");

    companion object {
        fun fromId(id: Int): KodanimaTheme = values().firstOrNull { it.id == id } ?: ROXO_BLACK
    }
}

enum class PressureCurve(val labelPt: String, val exponent: Float) {
    SOFT("Macio (Alta resposta)", 0.6f),
    LINEAR("Linear (Padrão)", 1.0f),
    FIRM("Firme (Mais controle)", 1.6f)
}

enum class PressureMode(val labelPt: String, val maxLevels: Int) {
    AUTO("Automático (Detecção da Mesa)", 0),
    LEVELS_1024("1024 Níveis", 1024),
    LEVELS_2048("2048 Níveis", 2048),
    LEVELS_8192("8192 Níveis (Padrão Pro)", 8192),
    LEVELS_16384("16384 Níveis (Alta Resolução)", 16384),
    CUSTOM("Personalizado (Manual)", -1);

    companion object {
        fun fromLevels(levels: Int): PressureMode = values().firstOrNull { it.maxLevels == levels } ?: CUSTOM
    }
}

data class TabletDriverSettings(
    val pressureCurve: PressureCurve = PressureCurve.LINEAR,
    val pressureMode: PressureMode = PressureMode.AUTO,
    val customPressureLevels: Int = 8192,
    val enableStylusHover: Boolean = true,
    val enableVirtualCursor: Boolean = true, // Seta do aplicativo (círculo vazado que cresce com tamanho do brush)
    val cursorColor: Long = 0xFF987EDC,     // Cor personalizável do cursor/seta
    val customCursorPath: String? = null,   // Caminho para cursor importado (.cur, .ani, .png)
    val invertX: Boolean = false,           // Inversão de coordenada X (alinha com a ponta da caneta)
    val invertY: Boolean = false,           // Inversão de coordenada Y (alinha com a ponta da caneta)
    val autoDetectStylusEraser: Boolean = true,
    val palmRejectionStrict: Boolean = true,
    val keyboardShortcutsEnabled: Boolean = true,
    val mouseScrollZoomEnabled: Boolean = true
)

enum class NetworkTransportMode(val labelPt: String, val descriptionPt: String) {
    WIFI_LAN(
        "Wi-Fi / Roteador (2.4 GHz & 5 GHz)",
        "Usa mDNS, UDP e varredura TCP direta na sub-rede para parear entre redes 2.4 GHz, 5 GHz e Roteador."
    ),
    WIFI_DIRECT(
        "Wi-Fi Direct (Pareamento Direto)",
        "Conecta dois celulares diretamente via Wi-Fi P2P por proximidade, sem precisar de roteador ou internet."
    ),
    BLUETOOTH(
        "Bluetooth Offline (Sem Internet)",
        "Transfere traços e projetos via Bluetooth Clássico quando não há internet nem roteador por perto."
    )
}

enum class ShortcutAction(
    val labelPt: String,
    val categoryPt: String,
    val defaultKeyCode: Int,
    val defaultCtrl: Boolean = false,
    val defaultShift: Boolean = false
) {
    TOOL_PEN("Caneta / Contorno", "Ferramentas", KeyEvent.KEYCODE_B),
    TOOL_PENCIL("Lápis", "Ferramentas", KeyEvent.KEYCODE_P),
    TOOL_BEZIER_PEN("Caneta Vetorial Bézier", "Ferramentas", KeyEvent.KEYCODE_V),
    TOOL_ERASER("Borracha", "Ferramentas", KeyEvent.KEYCODE_E),
    TOOL_BUCKET("Balde de Tinta", "Ferramentas", KeyEvent.KEYCODE_G),
    TOOL_MAGIC_WAND("Varinha Mágica", "Ferramentas", KeyEvent.KEYCODE_W),
    TOOL_LASSO("Laço de Seleção", "Ferramentas", KeyEvent.KEYCODE_S),
    TOOL_EYEDROPPER("Conta-Gotas", "Ferramentas", KeyEvent.KEYCODE_I),
    TOOL_LINE("Linha Reta", "Ferramentas", KeyEvent.KEYCODE_L),
    TOOL_RECTANGLE("Retângulo", "Ferramentas", KeyEvent.KEYCODE_U),
    TOOL_CIRCLE("Círculo", "Ferramentas", KeyEvent.KEYCODE_C),

    CONFIRM_BEZIER("Confirmar Curva Bézier", "Vetorial & Pincel", KeyEvent.KEYCODE_ENTER),
    BRUSH_SIZE_DOWN("Diminuir Tamanho do Pincel", "Vetorial & Pincel", KeyEvent.KEYCODE_LEFT_BRACKET),
    BRUSH_SIZE_UP("Aumentar Tamanho do Pincel", "Vetorial & Pincel", KeyEvent.KEYCODE_RIGHT_BRACKET),
    TOGGLE_MIRROR("Espelhar Canvas Horizontal", "Vetorial & Pincel", KeyEvent.KEYCODE_M),

    PLAY_PAUSE("Reproduzir / Pausar", "Animação & Timeline", KeyEvent.KEYCODE_SPACE),
    PREV_FRAME("Quadro Anterior", "Animação & Timeline", KeyEvent.KEYCODE_DPAD_LEFT),
    NEXT_FRAME("Próximo Quadro", "Animação & Timeline", KeyEvent.KEYCODE_DPAD_RIGHT),
    TOGGLE_ONION_SKIN("Casca de Cebola (Onion Skin)", "Animação & Timeline", KeyEvent.KEYCODE_O),
    ADD_FRAME("Adicionar Novo Quadro", "Animação & Timeline", KeyEvent.KEYCODE_N, defaultCtrl = true),
    CLEAR_FRAME("Limpar Quadro Atual", "Animação & Timeline", KeyEvent.KEYCODE_FORWARD_DEL, defaultCtrl = true),

    UNDO("Desfazer", "Edição & Arquivo", KeyEvent.KEYCODE_Z, defaultCtrl = true),
    REDO("Refazer", "Edição & Arquivo", KeyEvent.KEYCODE_Y, defaultCtrl = true),
    SAVE_PROJECT("Salvar Projeto", "Edição & Arquivo", KeyEvent.KEYCODE_S, defaultCtrl = true),
    TOGGLE_ZEN("Modo Foco / Ocultar Barras", "Visualização", KeyEvent.KEYCODE_TAB),
    TOGGLE_FRAME_LAYERS("Camadas do Quadro Atual", "Visualização", KeyEvent.KEYCODE_F7),
    OPEN_EXPORT("Exportar / Renderizar", "Edição & Arquivo", KeyEvent.KEYCODE_F12)
}

data class KeyboardShortcutBinding(
    val action: ShortcutAction,
    val keyCode: Int = action.defaultKeyCode,
    val ctrl: Boolean = action.defaultCtrl,
    val shift: Boolean = action.defaultShift
) {
    fun formatDisplay(): String = ShortcutRegistry.formatBinding(keyCode, ctrl, shift)
}

object ShortcutRegistry {
    fun defaultMap(): Map<ShortcutAction, KeyboardShortcutBinding> {
        return ShortcutAction.entries.associateWith { act ->
            KeyboardShortcutBinding(
                action = act,
                keyCode = act.defaultKeyCode,
                ctrl = act.defaultCtrl,
                shift = act.defaultShift
            )
        }
    }

    val selectableQuickKeys: List<Pair<Int, String>> = listOf(
        KeyEvent.KEYCODE_ENTER to "Enter",
        KeyEvent.KEYCODE_SPACE to "Espaço",
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_FORWARD_DEL to "Delete",
        KeyEvent.KEYCODE_DPAD_LEFT to "Seta Esq.",
        KeyEvent.KEYCODE_DPAD_RIGHT to "Seta Dir.",
        KeyEvent.KEYCODE_LEFT_BRACKET to "[",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
        KeyEvent.KEYCODE_F7 to "F7",
        KeyEvent.KEYCODE_F12 to "F12",
        KeyEvent.KEYCODE_A to "A",
        KeyEvent.KEYCODE_B to "B",
        KeyEvent.KEYCODE_C to "C",
        KeyEvent.KEYCODE_D to "D",
        KeyEvent.KEYCODE_E to "E",
        KeyEvent.KEYCODE_F to "F",
        KeyEvent.KEYCODE_G to "G",
        KeyEvent.KEYCODE_H to "H",
        KeyEvent.KEYCODE_I to "I",
        KeyEvent.KEYCODE_K to "K",
        KeyEvent.KEYCODE_L to "L",
        KeyEvent.KEYCODE_M to "M",
        KeyEvent.KEYCODE_N to "N",
        KeyEvent.KEYCODE_O to "O",
        KeyEvent.KEYCODE_P to "P",
        KeyEvent.KEYCODE_Q to "Q",
        KeyEvent.KEYCODE_R to "R",
        KeyEvent.KEYCODE_S to "S",
        KeyEvent.KEYCODE_T to "T",
        KeyEvent.KEYCODE_U to "U",
        KeyEvent.KEYCODE_V to "V",
        KeyEvent.KEYCODE_W to "W",
        KeyEvent.KEYCODE_X to "X",
        KeyEvent.KEYCODE_Y to "Y",
        KeyEvent.KEYCODE_Z to "Z"
    )

    fun keyCodeDisplayName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
            KeyEvent.KEYCODE_SPACE -> "Espaço"
            KeyEvent.KEYCODE_TAB -> "Tab"
            KeyEvent.KEYCODE_DEL -> "Backspace"
            KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
            KeyEvent.KEYCODE_DPAD_LEFT -> "←"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
            KeyEvent.KEYCODE_DPAD_UP -> "↑"
            KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
            KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            KeyEvent.KEYCODE_ESCAPE -> "Esc"
            KeyEvent.KEYCODE_F7 -> "F7"
            KeyEvent.KEYCODE_F12 -> "F12"
            else -> {
                selectableQuickKeys.firstOrNull { it.first == keyCode }?.second
                    ?: KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            }
        }
    }

    fun formatBinding(keyCode: Int, ctrl: Boolean, shift: Boolean): String {
        val parts = ArrayList<String>(3)
        if (ctrl) parts.add("Ctrl")
        if (shift) parts.add("Shift")
        parts.add(keyCodeDisplayName(keyCode))
        return parts.joinToString("+")
    }
}

enum class GestureAction(val labelPt: String) {
    UNDO("Desfazer traço"),
    REDO("Refazer traço"),
    COPY_PASTE_MENU("Menu Rápido (Copiar / Colar / Limpar)"),
    CLEAR_ACTIVE_LAYER("Limpar camada atual"),
    TOGGLE_ZEN_FULLSCREEN("Tela cheia / Ocultar barras"),
    PREV_FRAME("Quadro anterior"),
    NEXT_FRAME("Próximo quadro"),
    RESET_CAMERA("Resetar zoom e giro"),
    TOGGLE_GRID_RULER("Alternar Régua Quadriculada"),
    DISABLED("Desativado")
}

enum class MultiFingerGesture(
    val labelPt: String,
    val hintPt: String,
    val defaultAction: GestureAction
) {
    TWO_FINGER_TAP(
        "Toque com 2 dedos",
        "Toque rápido simultâneo com 2 dedos no canvas",
        GestureAction.UNDO
    ),
    THREE_FINGER_TAP(
        "Toque com 3 dedos",
        "Toque rápido simultâneo com 3 dedos no canvas",
        GestureAction.REDO
    ),
    THREE_FINGER_SWIPE_DOWN(
        "Deslizar 3 dedos para baixo",
        "Arraste 3 dedos para baixo no canvas (estilo Procreate)",
        GestureAction.COPY_PASTE_MENU
    ),
    THREE_FINGER_SWIPE_LEFT(
        "Deslizar 3 dedos à esquerda",
        "Arraste 3 dedos para a esquerda no canvas",
        GestureAction.PREV_FRAME
    ),
    THREE_FINGER_SWIPE_RIGHT(
        "Deslizar 3 dedos à direita",
        "Arraste 3 dedos para a direita no canvas",
        GestureAction.NEXT_FRAME
    ),
    FOUR_FINGER_TAP(
        "Toque com 4 dedos",
        "Toque rápido com 4 dedos no canvas",
        GestureAction.TOGGLE_ZEN_FULLSCREEN
    )
}

object TouchGestureRegistry {
    fun defaultMap(): Map<MultiFingerGesture, GestureAction> =
        MultiFingerGesture.entries.associateWith { it.defaultAction }
}

data class AppPreferences(
    val uiMode: AppUiMode = AppUiMode.MOBILE,
    val theme: KodanimaTheme = KodanimaTheme.ROXO_BLACK,
    val timelineLayoutMode: TimelineLayoutMode = TimelineLayoutMode.HERITAGE_PROFISSIONAL,
    val tabletSettings: TabletDriverSettings = TabletDriverSettings(),
    val uiScale: Float = 0.85f,               // Escala de tamanho da interface (0.70x, 0.85x, 1.0x, 1.15x)
    val enableChunkCulling: Boolean = true,   // Sistema de chunks: só desenha o que estiver dentro da tela ao dar zoom
    val enableGpuAcceleration: Boolean = true,// Renderização acelerada por hardware GPU Skia
    val lowPowerMode: Boolean = false,        // Otimização para dispositivos com baixa performance
    val autoCompressOnClose: Boolean = true,  // Compacta em .koda2d ao fechar e apaga pasta temporária
    val customProjectsDirectory: String = "", // Pasta personalizada para salvar projetos .koda2d
    val autoSaveIntervalSeconds: Int = 30,
    val showFpsCounter: Boolean = true,
    val timelineSnapAssistant: Boolean = true,// Assistente de atração/encaixe nas bolinhas da régua de tempo
    val eraserUsesPressure: Boolean = true,   // Borracha responde à pressão da caneta/mesa
    val eraserBrushPresetId: Int = 1,          // Ponta de brush aplicada à borracha
    val isMultiplayerEnabled: Boolean = true, // Ativa/desativa suporte a multiplayer e coop LAN
    val networkTransportMode: NetworkTransportMode = NetworkTransportMode.WIFI_LAN,
    val hasPromptedInitialPermissions: Boolean = false,
    val showCanvasCollaboratorsHud: Boolean = true, // Exibe barra horizontal compacta de amigos conectados no canvas
    val autoPromptFriendBrushes: Boolean = true,    // Pergunta se deseja baixar brushes importados que amigos usam no LAN
    val zenModeSettings: ZenModeSettings = ZenModeSettings(), // Escolha de quais barras somem ao apertar TAB
    val keyboardShortcuts: Map<ShortcutAction, KeyboardShortcutBinding> = ShortcutRegistry.defaultMap(),
    val touchGestures: Map<MultiFingerGesture, GestureAction> = TouchGestureRegistry.defaultMap(),
    val savedCustomResolutions: List<com.example.model.ProjectResolution> = emptyList()
)

data class ZenModeSettings(
    val hideTopBar: Boolean = true,
    val hideTimeline: Boolean = true,
    val hideToolbar: Boolean = true,
    val hideBrushBar: Boolean = true
)
