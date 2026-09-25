package com.nexwatch.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.health.DailySummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    repository: DailySummaryRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = repository.observeToday()
        .map { summary -> if (summary == null) TodayUiState.NoDeviceBound else TodayUiState.Loaded(summary) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)
}
