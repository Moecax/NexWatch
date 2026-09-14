package com.nexwatch.feature.onboarding.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** design-prompt.md Batch 1 #7 — Keep it running. */
@Composable
fun KeepRunningScreen(
    onBatteryOptimization: () -> Unit,
    onAutostartHint: () -> Unit,
    onTestBackgroundConnection: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Keep it running", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "Android may stop the app in the background. These steps keep your watch connected.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
        EntranceItem(index = 2) {
            KeepRunningCard("Allow unrestricted battery use", "Prevents Android from pausing the connection.", "Allow", onBatteryOptimization)
        }
        EntranceItem(index = 3) {
            KeepRunningCard(
                title = "Enable autostart",
                description = "Some phones need this turned on manually after a restart.",
                actionLabel = "Enable",
                onAction = onAutostartHint,
                hint = "Tecno / Infinix / Xiaomi: Settings → Apps → NexWatch → Autostart → On.",
            )
        }
        EntranceItem(index = 4) {
            KeepRunningCard(
                title = "Test background connection",
                description = "Confirms the watch stays connected with the app closed.",
                actionLabel = "Test",
                onAction = onTestBackgroundConnection,
                leading = { LivePulseIndicator() },
            )
        }
        EntranceItem(index = 5, modifier = Modifier.padding(top = 24.dp)) {
            PrimaryButton(text = "Done", onClick = onDone)
        }
    }
}

@Composable
private fun KeepRunningCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    hint: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, WatchShapes.card)
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (leading != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    leading()
                    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Text(title, style = MaterialTheme.typography.bodyLarge)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (hint != null) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.card)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun LivePulseIndicator() {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = WatchMotion.easeOutStrong),
            repeatMode = RepeatMode.Restart,
        ),
        label = "livePulseProgress",
    )
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(8.dp)
                .scale(0.4f + progress * 1.2f)
                .alpha((0.9f * (1f - progress)).coerceIn(0f, 1f))
                .background(WatchTheme.colors.success, CircleShape),
        )
        Box(
            Modifier
                .size(8.dp)
                .background(WatchTheme.colors.success, CircleShape),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KeepRunningScreenPreview() {
    WatchTheme {
        PremiumBackground {
            KeepRunningScreen(onBatteryOptimization = {}, onAutostartHint = {}, onTestBackgroundConnection = {}, onDone = {})
        }
    }
}
