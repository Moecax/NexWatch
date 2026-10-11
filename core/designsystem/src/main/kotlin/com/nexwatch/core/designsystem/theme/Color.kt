package com.nexwatch.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand
val ElectricBlue = Color(0xFF168CFF)
val DeepBlue = Color(0xFF1554D1)
val SkyBlue = Color(0xFF28B8FF)
val Cyan = Color(0xFF16D9E3)
val Aqua = Color(0xFF43F2C5)
val Mint = Color(0xFF38E8A5)

// Surfaces
val Midnight = Color(0xFF050816)
val DeepNavy = Color(0xFF080D20)
val Navy = Color(0xFF0D1428)
val BlueNavy = Color(0xFF121B35)
val MutedBlue = Color(0xFF243252)

// Text
val TextPrimary = Color(0xFFF7FAFF)
val TextSecondary = Color(0xFFA8B4CC)
val TextMuted = Color(0xFF687590)

// Slate is deliberately its own token, lighter than MutedBlue, so input outlines
// (colorScheme.outline) clear the 3:1 WCAG contrast minimum against Midnight
// (#050816) — MutedBlue alone computes to ~1.57:1 and fails that bar.
val Slate = Color(0xFF5C6E96)

// Status (status only — never for health data)
val Success = Color(0xFF20D889)
val Warning = Color(0xFFFFB52E)
val Error = Color(0xFFFF4969)

// Health (health data only — never for status)
val HeartRateColor = Color(0xFFFF4775)
val ActivityColor = Color(0xFF25D98B)
val SleepColor = Color(0xFF7655E8)
val SleepTextColor = Color(0xFF9A82FF)
val CaloriesColor = Color(0xFFFF8A32)

// Sleep stages: four tints of SleepColor, deepest for Deep and lightest for Awake, so a
// hypnogram reads by lightness as well as by lane position.
val SleepDeepColor = Color(0xFF5B3FCF)
val SleepAwakeColor = Color(0xFFCFC3FF)

// Premium background gradient stops (top to bottom)
val PremiumGradientTop = Midnight
val PremiumGradientMid = Navy
val PremiumGradientBottom = Color(0xFF111B38)

// Watch depiction (Part A: polished steel bezel, black strap). Used only by WatchRender.
val BezelHighlight = Color(0xFF4C5A86)
val BezelMid = Color(0xFF3A4468)
val BezelShadow = Color(0xFF131A30)
val StrapTop = Color(0xFF161D33)
val StrapBottom = Color(0xFF0C1122)
val WatchButton = Color(0xFF2A3454)
val FaceGlass = Color(0xFF10182E)
val FaceDeep = Color(0xFF050914)
