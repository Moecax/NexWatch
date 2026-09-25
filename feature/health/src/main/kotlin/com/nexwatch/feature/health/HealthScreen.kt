package com.nexwatch.feature.health

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.WorkoutSummary

@Composable
fun HealthRoute(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HealthScreen(state)
}

@Composable
fun HealthScreen(state: HealthUiState) {
    LazyColumn {
        item {
            Text(
                text = "Last 7 days",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            Text(
                text = "${state.heartRateBuckets.size} hourly HR buckets",
                style = MaterialTheme.typography.bodyMedium,
                color = WatchTheme.colors.heartRate,
            )
        }
        items(state.recentNights) { night -> SleepNightRow(night) }
        items(state.recentWorkouts) { workout -> WorkoutRow(workout) }
    }
}

@Composable
private fun SleepNightRow(night: SleepNight) {
    Text(
        text = "${night.nightDate}: ${night.totalMinutes / 60}h ${night.totalMinutes % 60}m, score ${night.score}",
        style = MaterialTheme.typography.bodyLarge,
        color = WatchTheme.colors.sleepText,
    )
}

@Composable
private fun WorkoutRow(workout: WorkoutSummary) {
    Text(
        text = "Workout ${workout.sportType}: ${workout.distanceM.toInt()}m, ${workout.kcal.toInt()} kcal",
        style = MaterialTheme.typography.bodyLarge,
        color = WatchTheme.colors.activity,
    )
}

@Preview(showBackground = true)
@Composable
private fun HealthScreenPreview() {
    WatchTheme {
        PremiumBackground {
            HealthScreen(
                HealthUiState(
                    recentNights = listOf(SleepNight("2026-09-19", emptyList(), totalMinutes = 431, score = 82)),
                    recentWorkouts = listOf(WorkoutSummary("w1", 1, 0L, 1_800_000L, 5_000f, 300f, 130, 160)),
                ),
            )
        }
    }
}
