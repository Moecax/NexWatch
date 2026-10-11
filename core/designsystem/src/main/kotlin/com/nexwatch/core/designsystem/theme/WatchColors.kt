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
    val spo2: Color,
    val bloodPressure: Color,
    val sleepDeep: Color,
    val sleepRem: Color,
    val sleepLight: Color,
    val sleepAwake: Color,
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
    // Part A charts SpO2 in Cyan. Blood pressure has no health color of its own, so it takes
    // Sky Blue, which Part A reserves for chart lines and links rather than status.
    spo2 = Cyan,
    bloodPressure = SkyBlue,
    sleepDeep = SleepDeepColor,
    sleepRem = SleepColor,
    sleepLight = SleepTextColor,
    sleepAwake = SleepAwakeColor,
    success = Success,
    warning = Warning,
    error = Error,
    border = MutedBlue,
)

val LocalWatchColors = staticCompositionLocalOf { DefaultWatchColors }
