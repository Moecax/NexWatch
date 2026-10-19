package com.nexwatch.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.goals.DEFAULT_STEP_GOAL
import com.nexwatch.core.data.goals.GoalPrefs
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
    /** [what] is the verb that failed ("save", "read"), so a refused read doesn't claim it couldn't save. */
    data class Failed(val reason: String, val what: String = "save") : Notice
}

data class WatchSettingsUiState(
    val watchState: WatchState = WatchState.Unbound,
    val capabilities: WatchCapabilities? = null,
    /** Read back from the watch, never the app's own last write. */
    val settings: WatchSettings? = null,
    /** The app's own copy of the step goal (GoalPrefs), what the goal screen drafts against when the watch doesn't report one. */
    val appStepGoal: Int = DEFAULT_STEP_GOAL,
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
    private val goals: GoalPrefs,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<WatchSettingsUiState> = combine(
        watchClient.state,
        watchClient.capabilities,
        goals.stepGoal,
        transient,
    ) { state, capabilities, appGoal, t ->
        WatchSettingsUiState(
            watchState = state,
            capabilities = capabilities,
            settings = t.settings.takeIf { state is WatchState.Ready },
            appStepGoal = appGoal,
            isLoading = t.isLoading,
            isBusy = t.isBusy,
            notice = t.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSettingsUiState())

    /** Called by the screen when it becomes visible with a ready watch — nothing here polls (I5). */
    fun refresh() {
        if (transient.value.isLoading) return
        viewModelScope.launch {
            // A retry must supersede the failed read it follows, the way a save clears the last notice.
            transient.update { it.copy(isLoading = true, notice = null) }
            try {
                val fresh = readSettings()
                transient.update { it.copy(settings = fresh, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                transient.update { it.copy(isLoading = false, notice = Notice.Failed(e.reason(), what = "read")) }
                return@launch
            }
            // The hero shows the level from WatchClient.state; asking here keeps it honest on a
            // watch that has been charging without reporting it. A miss keeps the last level.
            try {
                watchClient.batteryLevel()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun apply(vararg changes: WatchSettingChange, what: String = "Saved") {
        runCommand(what) {
            changes.forEach { watchClient.applySettings(it) }
            // The screen shows what the watch reports, so a value it snapped or refused is visible.
            val fresh = readSettings()
            transient.update { it.copy(settings = fresh) }
        }
    }

    fun findWatch() = runCommand(RINGING) { watchClient.findWatch() }

    /** Flips only the on/off switch; the active-hours window stays as the watch reports it. */
    fun setWristRaise(enabled: Boolean) {
        val current = uiState.value.settings?.wristRaise ?: return
        apply(WatchSettingChange.SetWristRaise(current.copy(enabled = enabled)), what = if (enabled) "Wrist raise on" else "Wrist raise off")
    }

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

    /** Every read refreshes the app's copy of the goal, so a goal changed on the watch shows up on Today too. */
    private suspend fun readSettings(): WatchSettings =
        watchClient.readSettings().also { fresh -> fresh.stepGoal?.takeIf { it > 0 }?.let { goals.setStepGoal(it) } }

    private fun Exception.reason() = message ?: "The watch didn't answer"

    companion object {
        /** The notice text find-watch succeeds with; the overview shows its ringing state while it's up. */
        const val RINGING = "Watch is ringing"
        private const val TEST_FORECAST_DAYS = 3
    }
}
