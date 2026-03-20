package com.toxictrace.nexusconnect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

val NexusBlue = Color(0xFF1A3CA8)
val NexusBlueDark = Color(0xFF0D2580)
val NexusBlueLight = Color(0xFF4A6FD4)

val AccentColors = listOf(
    Color(0xFF1A3CA8), // Blue
    Color(0xFF7B3FA0), // Purple
    Color(0xFF007A6E), // Teal
    Color(0xFF8B2252), // Rose
    Color(0xFF2E7D32), // Green
)

private val LightColors = lightColorScheme(
    primary = NexusBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFF4A6FD4),
    background = Color(0xFFF5F5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F1F5),
    outline = Color(0xFFE0E0E0)
)

private val DarkColors = darkColorScheme(
    primary = NexusBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D2580),
    secondary = Color(0xFF7A9AFF),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    outline = Color(0xFF3A3A3A)
)

@Composable
fun NexusConnectTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
