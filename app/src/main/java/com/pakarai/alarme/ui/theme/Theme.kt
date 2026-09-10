package com.pakarai.alarme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF453A),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF44201E),
    onPrimaryContainer = Color(0xFFFFB4A9),
    secondary = Color(0xFFFFA726),
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFC9C9C9),
    error = Color(0xFFFF5252),
    onError = Color.Black,
)

@Composable
fun AlarmePakaraiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}