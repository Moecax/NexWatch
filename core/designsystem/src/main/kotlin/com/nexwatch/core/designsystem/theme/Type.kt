package com.nexwatch.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WatchTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 56.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 48.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp),
)
