package com.nexwatch.feature.watch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.notification.NotificationActivityLog
import com.nexwatch.core.data.notification.NotificationForwardingPrefs
import com.nexwatch.core.service.NotificationAccess
import com.nexwatch.core.watchapi.notification.ForwardingLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

/** What the SDK's built-in call handling (§8.6) can do with the permissions granted right now. */
data class CallReadiness(
    val alerts: Boolean,
    val callerNames: Boolean,
    val rejectFromWatch: Boolean,
    val missingPermissions: List<String>,
)

data class NotificationsUiState(
    val accessGranted: Boolean = true,
    val enabled: Boolean = true,
    val allowedPackages: Set<String> = emptySet(),
    val forwardedToday: Int = 0,
    val lastForwardedAt: Long? = null,
    val calls: CallReadiness = CallReadiness(alerts = true, callerNames = true, rejectFromWatch = true, missingPermissions = emptyList()),
    /** Null until the installed-app list has loaded. */
    val apps: List<InstalledApp>? = null,
    val log: List<ForwardingLogEntry> = emptyList(),
) {
    val installedAllowedCount: Int get() = apps?.count { it.packageName in allowedPackages } ?: allowedPackages.size

    fun labelFor(packageName: String): String = apps?.firstOrNull { it.packageName == packageName }?.label ?: packageName
}

private data class SystemState(val accessGranted: Boolean, val calls: CallReadiness)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: NotificationForwardingPrefs,
    activityLog: NotificationActivityLog,
    diagnostics: DiagnosticsStore,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {

    // Access and permissions live in the system and can change while NexWatch is in the
    // background, so the screen re-reads them on every resume instead of trusting a cache.
    private val system = MutableStateFlow(readSystemState())
    private val apps = MutableStateFlow<List<InstalledApp>?>(null)

    val uiState: StateFlow<NotificationsUiState> = combine(
        prefs.settings,
        diagnostics.snapshot,
        activityLog.entries,
        system,
        apps,
    ) { settings, snapshot, log, system, apps ->
        NotificationsUiState(
            accessGranted = system.accessGranted,
            enabled = settings.enabled,
            allowedPackages = settings.allowedPackages,
            forwardedToday = snapshot.forwardedOn(LocalDate.now()),
            lastForwardedAt = snapshot.lastNotificationForwardedAt,
            calls = system.calls,
            apps = apps,
            log = log,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    init {
        viewModelScope.launch { apps.value = withContext(dispatchers.io) { loadLaunchableApps() } }
    }

    fun refreshSystemState() {
        system.value = readSystemState()
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setEnabled(enabled) }
    }

    fun setAppAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { prefs.setPackageAllowed(packageName, allowed) }
    }

    private fun readSystemState(): SystemState {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val callPermissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )
        return SystemState(
            accessGranted = NotificationAccess.isGranted(context),
            calls = CallReadiness(
                alerts = granted(Manifest.permission.READ_PHONE_STATE),
                callerNames = granted(Manifest.permission.READ_CALL_LOG) && granted(Manifest.permission.READ_CONTACTS),
                rejectFromWatch = granted(Manifest.permission.ANSWER_PHONE_CALLS),
                missingPermissions = callPermissions.filterNot(::granted),
            ),
        )
    }

    /** Launchable apps only: services and system plumbing don't post messages worth a buzz. */
    private fun loadLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
