package com.nexwatch.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Health and status colors, kept out of MaterialTheme.colorScheme because
 * they are single-purpose (CLAUDE.md: "Status colors are for status only.
 * Health colors are for data only.") — mixing them into colorScheme's
 * primary/secondary/error roles would invite exactly the misuse those rules
 * forbid.
 */
data class WatchColors(
    val heartRate: Color,
    val activity: Color,
    val sleep: Color,
    val sleepText: Color,
    val calories: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val border: Color,
)

val DefaultWatchColors = WatchColors(
    heartRate = HeartRateColor,
    activity = ActivityColor,
    sleep = SleepColor,
    sleepText = SleepTextColor,
    calories = CaloriesColor,
    success = Success,
    warning = Warning,
    error = Error,
    border = MutedBlue,
)

val LocalWatchColors = staticCompositionLocalOf { DefaultWatchColors }
