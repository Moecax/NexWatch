package com.nexwatch.feature.onboarding.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** The seven B1 screens, in order. Welcome is 1; Keep it running is 7. */
const val ONBOARDING_STEPS = 7

/**
 * A row-trailing button sized to its label (never narrower than [minWidth]), so a longer
 * label or a larger font scale grows the button instead of clipping its text.
 */
fun Modifier.compactButton(minWidth: Dp = 88.dp): Modifier =
    widthIn(min = minWidth).width(IntrinsicSize.Max).height(40.dp)

/**
 * The destructive-bind warning (Batch 1 #5): amber-tinted callout with the
 * warning carrying the bold lead sentence. No border-left accent, no icon-only
 * signal: the words do the work (Part C's banned-hallmarks list).
 */
@Composable
fun WarningBanner(
    lead: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WatchTheme.colors.warning.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = Icons.Filled.Warning,
            tint = WatchTheme.colors.warning,
            background = WatchTheme.colors.warning.copy(alpha = 0.16f),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                    append(lead)
                }
                append(" ")
                append(body)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The pair-confirmation gate (Batch 1 #5). The checkbox draws its check on
 * toggle; the caller enables "Pair watch" only once it's checked.
 */
@Composable
fun UnderstoodCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (checked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(7.dp),
                )
                .border(
                    width = 1.5.dp,
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(7.dp),
                ),
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text("I understand", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A progress ring wrapping the watch render during pairing (Batch 1 #6). The
 * ring sweeps to [progress] once, on the strong ease-in-out; the watch inside
 * stays still, so the ring is the only moving part of the composition.
 */
@Composable
fun PairingProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = WatchMotion.easeInOutStrong),
        label = "pairingRing",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(236.dp)) {
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val fillColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.size(236.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (animated > 0f) {
                drawArc(
                    color = fillColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Four-bar signal strength for a discovered watch: filled bars in Primary. */
@Composable
fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    val heights = listOf(5.dp, 9.dp, 13.dp, 16.dp)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.height(16.dp),
    ) {
        heights.forEachIndexed { index, barHeight ->
            Box(
                Modifier
                    .size(width = 3.dp, height = barHeight)
                    .background(
                        if (index < bars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

/** "3 of 5 granted" plus the animated bar beside it (Batch 1 #3). */
@Composable
fun GrantProgress(granted: Int, total: Int, modifier: Modifier = Modifier) {
    if (total == 0) return
    val fraction by animateFloatAsState(
        targetValue = granted.toFloat() / total,
        animationSpec = tween(durationMillis = 480, easing = WatchMotion.easeOutStrong),
        label = "grantProgress",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$granted of $total granted",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.pill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, WatchShapes.pill),
            )
        }
    }
}

/** A small helper line: what's missing, and how to clear it. */
@Composable
fun HelperLine(text: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp).padding(top = 2.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The live pulse used by the background-connection row (Batch 1 #7). One
 * looping animation per screen, reserved for a state that genuinely repeats.
 */
@Composable
fun LivePulseDot(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = WatchMotion.easeOutStrong),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liveDotProgress",
    )
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(8.dp)
                .scale(0.4f + progress * 1.2f)
                .alpha(1f - progress)
                .background(color, CircleShape),
        )
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}
