package com.nexwatch.feature.watch

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nexwatch.core.service.NotificationAccess
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
    NOTIFICATIONS,
    NOTIFICATION_APPS,
    NOTIFICATION_ACTIVITY,
}

/**
 * The Watch tab. Sections are internal state rather than nav destinations: they are one
 * level deep, only ever entered from here, and back simply returns here.
 */
@Composable
fun WatchRoute(
    onOpenDiagnostics: () -> Unit,
    viewModel: WatchSettingsViewModel = hiltViewModel(),
    presenceViewModel: CompanionPresenceViewModel = hiltViewModel(),
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notifications by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    // Access and phone permissions are changed in system screens, so re-read them on every return.
    LifecycleResumeEffect(notificationsViewModel) {
        notificationsViewModel.refreshSystemState()
        onPauseOrDispose {}
    }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        notificationsViewModel.refreshSystemState()
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presence by presenceViewModel.uiState.collectAsStateWithLifecycle()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        presenceViewModel.onConsentResult(it.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(presenceViewModel) {
        presenceViewModel.consentRequests.collect { consent.launch(IntentSenderRequest.Builder(it).build()) }
    }
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
            presence = presence,
            onEnablePresence = presenceViewModel::enable,
            notificationsValue = when {
                !notifications.accessGranted -> "Access off"
                notifications.enabled -> "On, ${notifications.installedAllowedCount} apps"
                else -> "Off"
            },
        )
        WatchSection.REMINDERS -> RemindersScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.HEALTH_MONITORING -> HealthMonitoringScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.ALARMS -> AlarmsScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.WRIST_RAISE -> WristRaiseScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.UNITS -> UnitsScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.CONTACTS -> ContactsScreen(state, back) { viewModel.apply(*it.toTypedArray()) }
        WatchSection.WEATHER -> WeatherScreen(state, back, viewModel::pushTestWeather)
        WatchSection.NOTIFICATIONS -> NotificationsScreen(
            state = notifications,
            onBack = back,
            onEnabledChange = notificationsViewModel::setEnabled,
            onOpenAccessSettings = { NotificationAccess.openSettings(context) },
            onRequestCallPermissions = { callPermissions.launch(notifications.calls.missingPermissions.toTypedArray()) },
            onOpenApps = { section = WatchSection.NOTIFICATION_APPS },
            onOpenActivity = { section = WatchSection.NOTIFICATION_ACTIVITY },
        )
        WatchSection.NOTIFICATION_APPS -> NotificationAppsScreen(
            state = notifications,
            onBack = { section = WatchSection.NOTIFICATIONS },
            onAllowedChange = notificationsViewModel::setAppAllowed,
        )
        WatchSection.NOTIFICATION_ACTIVITY -> ForwardingActivityScreen(
            state = notifications,
            onBack = { section = WatchSection.NOTIFICATIONS },
        )
    }
}
