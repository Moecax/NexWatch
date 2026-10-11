package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.SkyBlue
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** INFO is for in-progress states (syncing), which are neither good nor bad news. */
enum class StatusTone { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

private const val PULSE_DURATION_MS = 2200
private const val PULSE_MAX_SCALE = 2.8f

/**
 * CLAUDE.md: "color is never the only signal" — every status chip carries an icon (or the
 * live dot) and a label.
 *
 * [live] swaps the icon for a pulsing dot. Part C allows one looping animation per screen,
 * and only for a state that is genuinely continuous, such as an open connection.
 */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    live: Boolean = false,
) {
    val color = when (tone) {
        StatusTone.SUCCESS -> WatchTheme.colors.success
        StatusTone.WARNING -> WatchTheme.colors.warning
        StatusTone.ERROR -> WatchTheme.colors.error
        StatusTone.INFO -> SkyBlue
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = if (tone == StatusTone.NEUTRAL) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = 0.15f)
    val glyph = icon ?: when (tone) {
        StatusTone.SUCCESS -> Icons.Filled.CheckCircle
        StatusTone.WARNING -> Icons.Filled.Warning
        StatusTone.ERROR -> Icons.Filled.Error
        StatusTone.INFO, StatusTone.NEUTRAL -> Icons.Filled.Info
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(background, WatchShapes.pill)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    ) {
        if (live) LiveDot(color) else Icon(glyph, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text,
            color = color,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun LiveDot(color: Color) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
        if (!LocalInspectionMode.current) {
            val transition = rememberInfiniteTransition(label = "liveDot")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PULSE_DURATION_MS, easing = WatchMotion.easeOutStrong), RepeatMode.Restart),
                label = "liveDotPulse",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val scale = 1f + (PULSE_MAX_SCALE - 1f) * progress
                        scaleX = scale
                        scaleY = scale
                    }
                    .alpha(0.6f * (1f - progress))
                    .background(color, CircleShape),
            )
        }
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}
