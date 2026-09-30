package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OrakleRedPrimary,
    onPrimary = Color.White,
    primaryContainer = OrakleRedDark,
    onPrimaryContainer = Color.White,
    secondary = OrakleSlate200,
    onSecondary = OrakleSlate900,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    outline = OrakleSlate600,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = OrakleRedPrimary,
    onPrimary = Color.White,
    primaryContainer = OrakleRedLight,
    onPrimaryContainer = OrakleOnRedContainer,
    secondary = OrakleSlate900,
    onSecondary = Color.White,
    tertiary = OrakleBlue,
    background = OrakleSlate50,
    surface = Color.White,
    onBackground = OrakleSlate900,
    onSurface = OrakleSlate900,
    outline = OrakleSlate200,
    surfaceVariant = OrakleSlate100,
    onSurfaceVariant = OrakleSlate700
)

@Composable
fun DukaanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
