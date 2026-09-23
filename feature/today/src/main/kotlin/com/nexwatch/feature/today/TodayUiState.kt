package com.nexwatch.feature.today

import com.nexwatch.core.model.DailySummary

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoDeviceBound : TodayUiState
    data class Loaded(val summary: DailySummary) : TodayUiState
}
