package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.SkyBlue
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** INFO is for in-progress states (syncing), which are neither good nor bad news. */
enum class StatusTone { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

/**
 * CLAUDE.md: "color is never the only signal" — every status chip carries an icon and a label.
 *
 * [live] shows the label alone, no glyph: an open connection is continuous rather than an
 * event, and it says so by not pretending to have happened (nothing to check off).
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
        if (!live) Icon(glyph, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(
            text,
            color = color,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            // Without a glyph the label carries the chip's own leading padding instead.
            modifier = Modifier.padding(start = if (live) 0.dp else 6.dp),
        )
    }
}
