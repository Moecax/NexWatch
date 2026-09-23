package com.nexwatch.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.health.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val HOUR_MS = 3_600_000L
private const val SEVEN_DAYS_MS = 7L * 24 * HOUR_MS

@HiltViewModel
class HealthViewModel @Inject constructor(
    repository: HealthRepository,
) : ViewModel() {

    val uiState: StateFlow<HealthUiState> = run {
        val nowMs = System.currentTimeMillis()
        // minusDays(6), not 7: observeSleepNights' date range is inclusive on both ends, so
        // minusDays(6)..now is 7 calendar dates, matching the HR/workout windows' 7-day span.
        val fromDate = LocalDate.now(ZoneId.systemDefault()).minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val toDate = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)

        combine(
            repository.observeHeartRateBuckets(nowMs - SEVEN_DAYS_MS, nowMs, HOUR_MS),
            repository.observeSleepNights(fromDate, toDate),
            repository.observeWorkouts(nowMs - SEVEN_DAYS_MS, nowMs),
        ) { hr, nights, workouts -> HealthUiState(hr, nights, workouts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())
    }
}
