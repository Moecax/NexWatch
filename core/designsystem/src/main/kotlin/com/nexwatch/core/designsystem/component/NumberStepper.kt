package com.nexwatch.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.WatchMotion

/**
 * design-prompt.md Part C: "value changes ... cross-fade through a few px of blur rather
 * than snapping" — AnimatedContent's fade stands in for the blur-clear here for the same
 * API-31 reason noted on EntranceItem; the cross-fade is the load-bearing part of that rule.
 */
@Composable
fun NumberStepper(
    label: String,
    value: Int,
    unit: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.5f)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepperButton(symbol = "–", enabled = value - step >= range.first) {
            onValueChange((value - step).coerceIn(range))
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                fadeIn(tween(150, easing = WatchMotion.easeOutStrong)) togetherWith
                    fadeOut(tween(150, easing = WatchMotion.easeOutStrong))
            },
            label = "stepperValue",
        ) { animatedValue ->
            Text(
                text = "$animatedValue",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepperButton(symbol = "+", enabled = value + step <= range.last) {
            onValueChange((value + step).coerceIn(range))
        }
    }
}

@Composable
private fun RowScope.StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        modifier = Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        textAlign = TextAlign.Center,
    )
}
