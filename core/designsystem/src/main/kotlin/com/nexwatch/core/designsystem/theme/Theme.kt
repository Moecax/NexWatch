package com.nexwatch.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val NexWatchDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Midnight,
    secondary = Cyan,
    onSecondary = Midnight,
    tertiary = Mint,
    background = Midnight,
    onBackground = TextPrimary,
    surface = Navy,
    onSurface = TextPrimary,
    surfaceVariant = BlueNavy,
    onSurfaceVariant = TextSecondary,
    outline = Slate,
    error = Error,
    onError = TextPrimary,
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
