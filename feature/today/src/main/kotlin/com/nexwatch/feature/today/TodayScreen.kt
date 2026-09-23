package com.nexwatch.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.model.DailySummary

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(state)
}

@Composable
fun TodayScreen(state: TodayUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
private fun TodayScreenNoDevicePreview() {
    WatchTheme {
        PremiumBackground {
            TodayScreen(TodayUiState.NoDeviceBound)
        }
    }
}
