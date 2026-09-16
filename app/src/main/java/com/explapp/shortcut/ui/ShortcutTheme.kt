package com.explapp.shortcut.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5FEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5FF),
    onPrimaryContainer = Color(0xFF17174B),
    secondary = Color(0xFF00A6A6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F3F0),
    tertiary = Color(0xFFFF7A45),
    background = Color(0xFFF7F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B9FF),
    primaryContainer = Color(0xFF393A9E),
    secondary = Color(0xFF5DDBD5),
    secondaryContainer = Color(0xFF00504F),
    tertiary = Color(0xFFFFB59A),
    background = Color(0xFF111218),
    surface = Color(0xFF191A22),
    surfaceVariant = Color(0xFF242631),
)

@Composable
fun ShortcutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
