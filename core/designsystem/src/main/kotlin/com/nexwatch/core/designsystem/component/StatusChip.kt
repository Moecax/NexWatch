package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

enum class StatusTone { SUCCESS, WARNING, ERROR, NEUTRAL }

/** CLAUDE.md: "color is never the only signal" — every status chip carries an icon and label. */
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        StatusTone.SUCCESS -> WatchTheme.colors.success
        StatusTone.WARNING -> WatchTheme.colors.warning
        StatusTone.ERROR -> WatchTheme.colors.error
        StatusTone.NEUTRAL -> WatchTheme.colors.border
    }
    val icon = when (tone) {
        StatusTone.SUCCESS -> Icons.Filled.CheckCircle
        StatusTone.WARNING -> Icons.Filled.Warning
        StatusTone.ERROR -> Icons.Filled.Close
        StatusTone.NEUTRAL -> Icons.Filled.CheckCircle
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), WatchShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
