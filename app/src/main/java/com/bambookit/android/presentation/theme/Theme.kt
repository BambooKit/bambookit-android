package com.bambookit.android.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BambooGreen,
    secondary = BambooGreenBright,
    tertiary = StatusRunning,
    background = BambooObsidian,
    surface = BambooSurface,
    surfaceVariant = BambooSurfaceElevated,
    surfaceDim = BambooObsidian,
    surfaceBright = BambooSurfaceHigh,
    surfaceContainerLowest = BambooObsidian,
    surfaceContainerLow = BambooSurface,
    surfaceContainer = BambooSurface,
    surfaceContainerHigh = BambooSurfaceElevated,
    surfaceContainerHighest = BambooSurfaceHigh,
    onPrimary = BambooObsidian,
    onSecondary = BambooObsidian,
    onTertiary = BambooObsidian,
    primaryContainer = BambooGreenSubtle,
    onPrimaryContainer = TextPrimary,
    secondaryContainer = BambooGreenSubtle,
    onSecondaryContainer = TextPrimary,
    onSurfaceVariant = TextSecondary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BambooBorderStrong,
    outlineVariant = BambooBorder,
    error = StatusFailed,
    onError = BambooObsidian,
    errorContainer = StatusFailedTint,
    onErrorContainer = StatusFailed,
)

private val BambooShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun BambooKitTheme(content: @Composable () -> Unit) {
    // BambooKit is dark-only; TierPaletteLight is there for a light scheme.
    CompositionLocalProvider(LocalTierPalette provides TierPaletteDark) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            shapes = BambooShapes,
            content = content
        )
    }
}
