package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.WatchMotion

/**
 * design-prompt.md Part C: every screen plays one authored entrance, content staggered in
 * (opacity + ~10dp translate, 60ms stagger step, 500ms per item), once, when the screen
 * first appears — never replayed on recomposition. [index] is the child's position in the
 * staggered sequence (0-based); pass it in the same order the content reads top to bottom.
 *
 * Blur-clear from Part C is intentionally omitted: Compose's render-effect blur requires
 * API 31+ with no first-class fallback, and would need its own gating story. Opacity +
 * translate alone still reads as an authored entrance; revisit blur when a later phase
 * needs the API-31 gate for other reasons.
 */
@Composable
fun EntranceItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val inPreview = LocalInspectionMode.current
    var played by remember { mutableFloatStateOf(if (inPreview) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!inPreview) {
            kotlinx.coroutines.delay(index * WatchMotion.ENTRANCE_STAGGER_STEP_MS.toLong())
            played = 1f
        }
    }
    val progress by animateFloatAsState(
        targetValue = played,
        animationSpec = tween(WatchMotion.ENTRANCE_ITEM_DURATION_MS, easing = WatchMotion.easeOutStrong),
        label = "entrance",
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .alpha(progress)
            .offset(y = ((1f - progress) * 10).dp),
    ) {
        content()
    }
}
