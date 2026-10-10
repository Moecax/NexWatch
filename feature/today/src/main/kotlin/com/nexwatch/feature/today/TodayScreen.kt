package com.nexwatch.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.model.DailySummary
import com.nexwatch.core.service.NotificationAccess

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val forwardingEnabled by viewModel.forwardingEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // §8.5: the user can revoke access in system settings at any time, and forwarding then stops
    // without any error to show. Re-checked on every return to this screen.
    var accessGranted by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        accessGranted = NotificationAccess.isGranted(context)
        onPauseOrDispose {}
    }
    TodayScreen(
        state = state,
        showAccessBanner = forwardingEnabled && !accessGranted && state != TodayUiState.NoDeviceBound,
        onGrantAccess = { NotificationAccess.openSettings(context) },
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    showAccessBanner: Boolean = false,
    onGrantAccess: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        if (showAccessBanner) NotificationAccessBanner(onGrantAccess)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TodayContent(state)
        }
    }
}

@Composable
private fun NotificationAccessBanner(onGrantAccess: () -> Unit) {
    Surface(shape = WatchShapes.card, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = WatchTheme.colors.error)
                Text(
                    "Messages aren't reaching your watch",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "Notification access for NexWatch is off in Android settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Turn on", onClick = onGrantAccess)
        }
    }
}

@Composable
private fun TodayContent(state: TodayUiState) {
    when (state) {
        TodayUiState.Loading -> Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TodayUiState.NoDeviceBound -> Text(
            text = "Pair a watch to see today's data",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is TodayUiState.Loaded -> TodaySummaryContent(state.summary)
    }
}

@Composable
private fun TodaySummaryContent(summary: DailySummary) {
    Text(
        text = "${summary.steps} steps",
        style = MaterialTheme.typography.displaySmall,
        color = WatchTheme.colors.activity,
    )
    Text(
        text = "${summary.energyKcal} kcal",
        style = MaterialTheme.typography.titleMedium,
        color = WatchTheme.colors.calories,
    )
    summary.avgHrBpm?.let { bpm ->
        Text(
            text = "Avg HR $bpm bpm",
            style = MaterialTheme.typography.bodyLarge,
            color = WatchTheme.colors.heartRate,
        )
    }
    summary.sleepMinutes?.let { minutes ->
        Text(
            text = "Slept ${minutes / 60}h ${minutes % 60}m",
            style = MaterialTheme.typography.bodyLarge,
            color = WatchTheme.colors.sleepText,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenLoadedPreview() {
    WatchTheme {
        PremiumBackground {
            TodayScreen(
                TodayUiState.Loaded(
                    DailySummary(
                        date = "2026-09-19", steps = 8_432, distanceM = 6_200, energyKcal = 410,
                        restingHrBpm = 54, avgHrBpm = 72, maxHrBpm = 140, sleepMinutes = 431, liveStepsTotal = 8_432,
                    ),
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenAccessOffPreview() {
    WatchTheme {
        PremiumBackground {
            TodayScreen(
                TodayUiState.Loaded(
                    DailySummary(
                        date = "2026-09-19", steps = 8_432, distanceM = 6_200, energyKcal = 410,
                        restingHrBpm = 54, avgHrBpm = 72, maxHrBpm = 140, sleepMinutes = 431, liveStepsTotal = 8_432,
                    ),
                ),
                showAccessBanner = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenNoDevicePreview() {
    WatchTheme {
        PremiumBackground {
            TodayScreen(TodayUiState.NoDeviceBound)
        }
    }
}
