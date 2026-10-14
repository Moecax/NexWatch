package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.Manrope
import com.nexwatch.core.designsystem.theme.Midnight
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** Large surfaces move less than buttons on press, or the whole card reads as wobbling. */
private const val CARD_PRESSED_SCALE = 0.985f

/**
 * The one card surface (Part A): Navy, 22dp corners, a faint 1dp border, no drop shadow.
 * Elevation comes from the lighter surface, not from a glow.
 */
@Composable
fun NexCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = if (onClick != null) {
        Modifier
            .pressScale(interactionSource, CARD_PRESSED_SCALE)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        shape = WatchShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, WatchTheme.colors.border.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth().then(clickable),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** A group label above a card. Sentence case on purpose: no small-caps kicker labels (Part C). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** Screen title plus an optional one-line subtitle, used at the top of each tab. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.padding(top = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * A metric value with its unit set smaller in Text Secondary beside it (Part A), always with
 * tabular figures so a changing number doesn't jitter sideways.
 */
@Composable
fun MetricText(
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 44.sp,
    unitSize: TextUnit = 15.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(SpanStyle(fontSize = unitSize, fontWeight = FontWeight.SemiBold, color = unitColor, letterSpacing = 0.sp)) {
                    append(" ")
                    append(unit)
                }
            }
        },
        style = metricStyle(valueSize),
        color = color,
        modifier = modifier,
    )
}

fun metricStyle(size: TextUnit): TextStyle = TextStyle(
    fontFamily = Manrope,
    fontSize = size,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.02 * size.value).sp,
    lineHeight = size * 1.05f,
    fontFeatureSettings = "tnum",
)

/** Switch with the app's colors: Cyan track when on (Part A: "toggles"), Midnight thumb. */
@Composable
fun WatchSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.secondary,
            checkedThumbColor = Midnight,
            checkedBorderColor = MaterialTheme.colorScheme.secondary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(36.dp).background(background, RoundedCornerShape(11.dp)),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * A card holding rows split by hairlines: one container instead of a stack of identical
 * floating cards (Part C).
 */
@Composable
fun RowGroup(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    NexCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = WatchTheme.colors.border.copy(alpha = 0.5f))
            row()
        }
    }
}

/**
 * A settings-style row: icon, title, optional description, then either the current [value]
 * with a chevron (when it opens something) or a [trailing] control.
 */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconBadge(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = titleColor)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 0.dp).size(20.dp),
            )
        }
    }
}
