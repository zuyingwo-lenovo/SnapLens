package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LuminaDarkColorScheme = darkColorScheme(
    primary = LeicaAmber,
    onPrimary = ObsidianBlack,
    primaryContainer = DarkroomElevated,
    onPrimaryContainer = AmberGlow,
    secondary = OpticalCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = GunmetalBorder,
    onSecondaryContainer = IvoryText,
    tertiary = HalideOrange,
    onTertiary = IvoryText,
    background = ObsidianBlack,
    onBackground = IvoryText,
    surface = DarkroomSurface,
    onSurface = IvoryText,
    surfaceVariant = DarkroomElevated,
    onSurfaceVariant = MutedSilver,
    outline = GunmetalBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuminaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
