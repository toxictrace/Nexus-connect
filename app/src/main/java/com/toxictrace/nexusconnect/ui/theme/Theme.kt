package com.toxictrace.nexusconnect.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val AccentColors = listOf(
    Color(0xFF1A3CA8), // Blue
    Color(0xFF7B3FA0), // Purple
    Color(0xFF007A6E), // Teal
    Color(0xFF8B2252), // Rose
    Color(0xFF2E7D32), // Green
    Color(0xFFB85C00), // Orange
    Color(0xFFC62828), // Red
    Color(0xFF00695C), // Dark Teal
    Color(0xFF283593), // Indigo
    Color(0xFF558B2F), // Olive
)

private fun lightScheme(primary: Color) = lightColorScheme(
    primary          = primary,
    onPrimary        = Color.White,
    primaryContainer = primary.copy(alpha = 0.15f).compositeOver(Color.White),
    secondary        = primary.copy(alpha = 0.7f).compositeOver(Color.White),
    background       = Color(0xFFF5F5F7),
    surface          = Color.White,
    surfaceVariant   = Color(0xFFF0F1F5),
    outline          = Color(0xFFE0E0E0)
)

private fun darkScheme(primary: Color) = darkColorScheme(
    primary          = primary.copy(alpha = 0.85f).compositeOver(Color.White),
    onPrimary        = Color.White,
    primaryContainer = primary.copy(alpha = 0.3f).compositeOver(Color(0xFF121212)),
    secondary        = primary.copy(alpha = 0.6f).compositeOver(Color.White),
    background       = Color(0xFF121212),
    surface          = Color(0xFF1E1E1E),
    surfaceVariant   = Color(0xFF2A2A2A),
    outline          = Color(0xFF3A3A3A)
)

// Blend two colors
private fun Color.compositeOver(background: Color): Color {
    val fg = this
    val a = fg.alpha
    return Color(
        red   = fg.red   * a + background.red   * (1 - a),
        green = fg.green * a + background.green * (1 - a),
        blue  = fg.blue  * a + background.blue  * (1 - a),
        alpha = 1f
    )
}

@Composable
fun NexusConnectTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> {
            val accent = AccentColors.getOrElse(accentIndex) { AccentColors[0] }
            if (darkTheme) darkScheme(accent) else lightScheme(accent)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
