package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.nexwatch.core.designsystem.theme.PremiumGradientBottom
import com.nexwatch.core.designsystem.theme.PremiumGradientMid
import com.nexwatch.core.designsystem.theme.PremiumGradientTop

/**
 * Paints the vertical gradient once at the screen root (design-prompt.md
 * §Premium background), so callers stack a transparent Scaffold on top
 * instead of each screen re-declaring the gradient.
 */
@Composable
fun PremiumBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumGradientTop, PremiumGradientMid, PremiumGradientBottom),
                ),
            ),
    ) {
        content()
    }
}
