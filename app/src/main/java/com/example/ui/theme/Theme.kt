package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorContainerCyan = androidx.compose.ui.graphics.Color(0xFF123244)
private val ColorContainerCoral = androidx.compose.ui.graphics.Color(0xFF3E1929)
private val ColorOutlineDim = androidx.compose.ui.graphics.Color(0xFF1F2D47)

private val LainDarkColorScheme = darkColorScheme(
    primary = NaviCyan,
    onPrimary = WiredVoid,
    primaryContainer = ColorContainerCyan,
    onPrimaryContainer = NaviCyan,
    secondary = SignalCoral,
    onSecondary = WiredVoid,
    secondaryContainer = ColorContainerCoral,
    onSecondaryContainer = SignalCoral,
    tertiary = DeskAmber,
    onTertiary = WiredVoid,
    background = WiredVoid,
    onBackground = TextPrimary,
    surface = WiredAbyss,
    onSurface = TextPrimary,
    surfaceVariant = WiredSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = WiredSurfaceHigh,
    outline = WiredBorder,
    outlineVariant = ColorOutlineDim
)

private val LainShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun LainTheme(
    isArabic: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LainDarkColorScheme,
        typography = lainTypography(isArabic = isArabic),
        shapes = LainShapes,
        content = content
    )
}
