package com.nexwatch.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchapi.WatchSettings
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WeatherCondition
import com.nexwatch.core.watchapi.WeatherDayForecast
import com.nexwatch.core.watchapi.WeatherForecast
import com.nexwatch.core.watchapi.WeatherReading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the last watch command came back with; shown once, in words as well as colour. */
sealed interface Notice {
    data class Saved(val what: String) : Notice
    data class Failed(val reason: String) : Notice
}

data class WatchSettingsUiState(
    val watchState: WatchState = WatchState.Unbound,
    val capabilities: WatchCapabilities? = null,
    /** Read back from the watch, never the app's own last write. */
    val settings: WatchSettings? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val notice: Notice? = null,
) {
    val isReady: Boolean get() = watchState is WatchState.Ready
}

private data class Transient(
    val settings: WatchSettings? = null,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val notice: Notice? = null,
)

@HiltViewModel
class WatchSettingsViewModel @Inject constructor(
    private val watchClient: WatchClient,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<WatchSettingsUiState> = combine(
        watchClient.state,
        watchClient.capabilities,
        transient,
    ) { state, capabilities, t ->
        WatchSettingsUiState(
            watchState = state,
            capabilities = capabilities,
            settings = t.settings.takeIf { state is WatchState.Ready },
            isLoading = t.isLoading,
            isBusy = t.isBusy,
            notice = t.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSettingsUiState())

    /** Called by the screen when it becomes visible with a ready watch — nothing here polls (I5). */
    fun refresh() {
        if (transient.value.isLoading) return
        viewModelScope.launch {
            transient.update { it.copy(isLoading = true) }
            try {
                val fresh = watchClient.readSettings()
                transient.update { it.copy(settings = fresh, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transient.update { it.copy(isLoading = false, notice = Notice.Failed(e.reason())) }
            }
        }
    }

    fun apply(vararg changes: WatchSettingChange, what: String = "Saved") {
        runCommand(what) {
            changes.forEach { watchClient.applySettings(it) }
            // The screen shows what the watch reports, so a value it snapped or refused is visible.
            val fresh = watchClient.readSettings()
            transient.update { it.copy(settings = fresh) }
        }
    }

    fun findWatch() = runCommand("Watch is ringing") { watchClient.findWatch() }

    fun pushTestWeather(condition: WeatherCondition, temperatureC: Int) = runCommand("Forecast sent") {
        watchClient.pushWeather(
            WeatherForecast(
                locationName = "NexWatch",
                current = WeatherReading(condition, temperatureC),
                // Each forecast day gets a different condition, so one push shows several of
                // the watch's icons at once.
                days = List(TEST_FORECAST_DAYS) { offset ->
                    val next = WeatherCondition.entries[(condition.ordinal + offset + 1) % WeatherCondition.entries.size]
                    WeatherDayForecast(next, highC = temperatureC + 2, lowC = temperatureC - 2)
                },
            ),
        )
    }

    fun dismissNotice() = transient.update { it.copy(notice = null) }

    private fun runCommand(successMessage: String, block: suspend () -> Unit) {
        if (transient.value.isBusy) return
        viewModelScope.launch {
            transient.update { it.copy(isBusy = true, notice = null) }
            val notice = try {
                block()
                Notice.Saved(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Notice.Failed(e.reason())
            }
            transient.update { it.copy(isBusy = false, notice = notice) }
        }
    }

    private fun Exception.reason() = message ?: "The watch didn't answer"

    private companion object {
        const val TEST_FORECAST_DAYS = 3
    }
}
