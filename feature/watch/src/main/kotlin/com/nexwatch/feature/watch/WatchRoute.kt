package com.nexwatch.feature.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal enum class WatchSection {
    REMINDERS,
    HEALTH_MONITORING,
    ALARMS,
    WRIST_RAISE,
    UNITS,
    CONTACTS,
    WEATHER,
}

/**
 * The Watch tab. Sections are internal state rather than nav destinations: they are one
 * level deep, only ever entered from here, and back simply returns here.
 */
@Composable
fun WatchRoute(
    onOpenDiagnostics: () -> Unit,
    viewModel: WatchSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf<WatchSection?>(null) }

    // Keyed on readiness so a reconnect re-reads, and a screen that isn't showing never does.
    LaunchedEffect(state.isReady) {
        if (state.isReady) viewModel.refresh()
    }

    val back = {
        viewModel.dismissNotice()
        section = null
    }
    when (section) {
        null -> WatchOverviewScreen(
            state = state,
            onOpen = {
                viewModel.dismissNotice()
                section = it
            },
            onFindWatch = viewModel::findWatch,
            onOpenDiagnostics = onOpenDiagnostics,
        )
        WatchSection.REMINDERS -> RemindersScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.HEALTH_MONITORING -> HealthMonitoringScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.ALARMS -> AlarmsScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.WRIST_RAISE -> WristRaiseScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.UNITS -> UnitsScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.CONTACTS -> ContactsScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.WEATHER -> WeatherScreen(state, back, viewModel::pushTestWeather)
    }
}
