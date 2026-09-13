package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.Midnight
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes

/** design-prompt.md Batch 1: sex is a segmented control; the thumb slides with `easeInOutStrong`. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.control)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(200, easing = WatchMotion.easeInOutStrong),
            label = "segmentThumb",
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .height(36.dp)
                .width(segmentWidth)
                .background(MaterialTheme.colorScheme.primary, WatchShapes.control),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Text(
                    text = label,
                    color = if (selected) Midnight else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(index) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
