package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.nexwatch.core.designsystem.theme.WatchMotion
import kotlinx.coroutines.flow.collectLatest

private const val PRESSED_SCALE = 0.97f
private const val RESTING_SCALE = 1f

/**
 * design-prompt.md Part C: "every pressable element responds on press, not on release" —
 * scale(0.97), ~150ms, strong ease-out. One implementation so every button/card in the app
 * shares the exact same feel instead of each screen reinventing it slightly differently.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    if (LocalInspectionMode.current) return this // keep static previews readable
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffectPressState(interactionSource) { pressed = it }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else RESTING_SCALE,
        animationSpec = tween(WatchMotion.PRESS_DURATION_MS, easing = WatchMotion.easeOutStrong),
        label = "pressScale",
    )
    return this.graphicsLayer(scaleX = scale, scaleY = scale)
}

@Composable
private fun LaunchedEffectPressState(interactionSource: InteractionSource, onPressed: (Boolean) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> onPressed(true)
                is PressInteraction.Release, is PressInteraction.Cancel -> onPressed(false)
            }
        }
    }
}
