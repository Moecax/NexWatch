package com.nexwatch.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val NexWatchDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Midnight,
    primaryContainer = DeepBlue,
    onPrimaryContainer = TextPrimary,
    inversePrimary = DeepBlue,
    secondary = Cyan,
    onSecondary = Midnight,
    secondaryContainer = BlueNavy,
    onSecondaryContainer = TextPrimary,
    tertiary = Mint,
    onTertiary = Midnight,
    tertiaryContainer = MutedBlue,
    onTertiaryContainer = TextPrimary,
    background = Midnight,
    onBackground = TextPrimary,
    surface = Navy,
    onSurface = TextPrimary,
    surfaceVariant = BlueNavy,
    onSurfaceVariant = TextSecondary,
    surfaceTint = ElectricBlue,
    surfaceBright = BlueNavy,
    surfaceDim = DeepNavy,
    surfaceContainer = Navy,
    surfaceContainerLowest = Midnight,
    surfaceContainerLow = DeepNavy,
    surfaceContainerHigh = BlueNavy,
    surfaceContainerHighest = MutedBlue,
    inverseSurface = TextPrimary,
    inverseOnSurface = Midnight,
    outline = Slate,
    outlineVariant = MutedBlue,
    scrim = Midnight,
    error = Error,
    onError = TextPrimary,
    errorContainer = MutedBlue,
    onErrorContainer = TextPrimary,
)

/**
 * Dark-only theme (CLAUDE.md: "Dark theme only, no dynamic color") — there is
 * no light color scheme and no dynamicColor parameter.
 */
@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWatchColors provides DefaultWatchColors) {
        MaterialTheme(
            colorScheme = NexWatchDarkColorScheme,
            typography = WatchTypography,
            content = content,
        )
    }
}

object WatchTheme {
    val colors: WatchColors
        @Composable
        get() = LocalWatchColors.current
}
