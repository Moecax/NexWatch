package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.ElectricBlue
import com.nexwatch.core.designsystem.theme.SkyBlue

/**
 * Position in a multi-step flow (the seven-step onboarding). The filled length is
 * the signal, the Sky Blue segment marks "you are here"; no numbers, because the
 * user can count the filled segments themselves.
 *
 * Static on purpose: it changes once per step, which a user spends seconds on,
 * not the hundred-times-a-day action Part C's no-animation rule is about.
 */
@Composable
fun StepProgressBar(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val description = "Step $current of $total"
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(total) { index ->
            val step = index + 1
            val color = when {
                step < current -> ElectricBlue
                step == current -> SkyBlue
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}
