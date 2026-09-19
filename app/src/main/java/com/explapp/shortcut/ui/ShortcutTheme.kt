package com.explapp.shortcut.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF6558E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF21185C),
    secondary = Color(0xFF008E7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5EE),
    onSecondaryContainer = Color(0xFF063D35),
    tertiary = Color(0xFFE97645),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7DB),
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF1B1B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B22),
    surfaceVariant = Color(0xFFF0F0F7),
    onSurfaceVariant = Color(0xFF666573),
    outline = Color(0xFFD3D1DC),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7C1FF),
    onPrimary = Color(0xFF312679),
    primaryContainer = Color(0xFF463B98),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFF75DDCC),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFA0F3E4),
    tertiary = Color(0xFFFFB694),
    onTertiary = Color(0xFF5B210A),
    tertiaryContainer = Color(0xFF7A371D),
    background = Color(0xFF101116),
    onBackground = Color(0xFFE6E2EB),
    surface = Color(0xFF18191F),
    onSurface = Color(0xFFE6E2EB),
    surfaceVariant = Color(0xFF262730),
    onSurfaceVariant = Color(0xFFC8C5D1),
    outline = Color(0xFF484955),
    error = Color(0xFFFFB4AB),
)

private val ShortcutShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val ShortcutTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)

@Composable
fun ShortcutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ShortcutTypography,
        shapes = ShortcutShapes,
        content = content,
    )
}
