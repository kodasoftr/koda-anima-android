package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.KodanimaTheme

@Composable
fun MyApplicationTheme(
    kodanimaTheme: KodanimaTheme = KodanimaTheme.ROXO_BLACK,
    content: @Composable () -> Unit
) {
    val bg = Color(kodanimaTheme.bg)
    val panel = Color(kodanimaTheme.panel)
    val accent = Color(kodanimaTheme.accent)
    val text = Color(kodanimaTheme.text)
    val secondary = Color(kodanimaTheme.secondaryAccent)

    val colorScheme = if (kodanimaTheme.isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.16f),
            onPrimaryContainer = text,
            secondary = secondary,
            onSecondary = Color.White,
            background = bg,
            onBackground = text,
            surface = panel,
            onSurface = text,
            surfaceVariant = Color(0xFFE3E7F0),
            onSurfaceVariant = text.copy(alpha = 0.72f),
            surfaceContainerLow = panel,
            surfaceContainer = Color(0xFFF3F5FA),
            surfaceContainerHigh = Color(0xFFE8ECF5),
            surfaceContainerHighest = Color(0xFFDCE2EF),
            outline = accent.copy(alpha = 0.38f),
            outlineVariant = text.copy(alpha = 0.16f)
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = bg,
            primaryContainer = accent.copy(alpha = 0.24f),
            onPrimaryContainer = text,
            secondary = secondary,
            onSecondary = bg,
            background = bg,
            onBackground = text,
            surface = panel,
            onSurface = text,
            surfaceVariant = Color(
                red = (panel.red + 0.04f).coerceAtMost(1f),
                green = (panel.green + 0.04f).coerceAtMost(1f),
                blue = (panel.blue + 0.05f).coerceAtMost(1f)
            ),
            onSurfaceVariant = text.copy(alpha = 0.72f),
            surfaceContainerLow = panel,
            surfaceContainer = panel,
            surfaceContainerHigh = Color(
                red = (panel.red + 0.05f).coerceAtMost(1f),
                green = (panel.green + 0.05f).coerceAtMost(1f),
                blue = (panel.blue + 0.07f).coerceAtMost(1f)
            ),
            surfaceContainerHighest = Color(
                red = (panel.red + 0.09f).coerceAtMost(1f),
                green = (panel.green + 0.09f).coerceAtMost(1f),
                blue = (panel.blue + 0.12f).coerceAtMost(1f)
            ),
            outline = accent.copy(alpha = 0.42f),
            outlineVariant = text.copy(alpha = 0.18f)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
