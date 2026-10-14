package com.nexwatch.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.R

// Only the weights the app uses are bundled; anything else falls back to the nearest one.
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semi_bold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extra_bold, FontWeight.ExtraBold),
)

private val Base = Typography()

val WatchTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 56.sp),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 48.sp),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Manrope),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Manrope),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Manrope),
    titleLarge = Base.titleLarge.copy(fontFamily = Manrope),
    titleMedium = Base.titleMedium.copy(fontFamily = Manrope),
    titleSmall = Base.titleSmall.copy(fontFamily = Manrope),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = Base.bodySmall.copy(fontFamily = Manrope),
    labelLarge = Base.labelLarge.copy(fontFamily = Manrope),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp),
)
