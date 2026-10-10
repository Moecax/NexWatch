package com.nexwatch.feature.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchSettings
import com.nexwatch.core.watchapi.WatchState

@Composable
internal fun WatchOverviewScreen(
    state: WatchSettingsUiState,
    onOpen: (WatchSection) -> Unit,
    onFindWatch: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    presence: PresenceUiState = PresenceUiState(),
    onEnablePresence: () -> Unit = {},
) {
    val capabilities = state.capabilities
    val settings = state.settings
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ConnectionHeader(state)
        SecondaryButton(
            text = "Find watch",
            onClick = onFindWatch,
            enabled = state.isReady && !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
        NoticeLine(state.notice)
        if (!state.isReady) NotConnectedNote()

        // Only what this watch reports as supported gets a row (§4.5).
        val rows = buildList {
            add(Entry(Icons.Filled.Notifications, "Reminders & Do Not Disturb", remindersSummary(settings), WatchSection.REMINDERS))
            if (capabilities == null || capabilities.heartRate) {
                add(Entry(Icons.Filled.FavoriteBorder, "Health monitoring", monitoringSummary(settings), WatchSection.HEALTH_MONITORING))
            }
            if (capabilities?.alarmLimit != null) {
                add(Entry(Icons.Filled.Alarm, "Alarms", settings?.alarms?.let { "${it.size} set" }, WatchSection.ALARMS))
            }
            add(Entry(Icons.Filled.Watch, "Wrist raise", settings?.wristRaise?.let { if (it.enabled) "On" else "Off" }, WatchSection.WRIST_RAISE))
            add(Entry(Icons.Filled.Schedule, "Units & time", null, WatchSection.UNITS))
            if (capabilities?.contactsLimit != null) {
                add(Entry(Icons.Filled.Contacts, "Contacts", settings?.contacts?.let { "${it.size} of ${capabilities.contactsLimit}" }, WatchSection.CONTACTS))
            }
            if (capabilities?.weather == true) {
                add(Entry(Icons.Filled.Cloud, "Weather", null, WatchSection.WEATHER))
            }
        }
        rows.forEach { row ->
            SettingsEntry(row.icon, row.title, row.value, enabled = state.isReady) { onOpen(row.section) }
        }
        if (presence.available) PresenceEntry(presence, onEnablePresence)
        SettingsEntry(Icons.Filled.BugReport, "Diagnostics", null, enabled = true, onClick = onOpenDiagnostics)
    }
}

@Composable
private fun PresenceEntry(presence: PresenceUiState, onEnable: () -> Unit) {
    val value = when {
        presence.associated -> "On"
        presence.busy -> "Turning on…"
        else -> "Off"
    }
    Column {
        SettingsEntry(
            Icons.Filled.BluetoothSearching,
            "Wake when watch is nearby",
            value,
            enabled = !presence.associated && !presence.busy,
            onClick = onEnable,
        )
        Text(
            presence.failure ?: "Lets Android start NexWatch when the watch comes into range, even after it was closed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

private data class Entry(val icon: ImageVector, val title: String, val value: String?, val section: WatchSection)

@Composable
private fun ConnectionHeader(state: WatchSettingsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your watch", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        val (label, tone) = when (val watchState = state.watchState) {
            is WatchState.Ready -> "Connected" to StatusTone.SUCCESS
            WatchState.Connecting -> "Connecting" to StatusTone.WARNING
            is WatchState.Waiting -> "Not nearby" to StatusTone.NEUTRAL
            WatchState.BluetoothOff -> "Bluetooth is off" to StatusTone.WARNING
            is WatchState.AuthFailed -> "Paired with another app" to StatusTone.ERROR
            WatchState.Unbound -> "Not paired" to StatusTone.NEUTRAL
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusChip(label, tone)
            (state.watchState as? WatchState.Ready)?.battery?.let {
                Text("Battery $it%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.capabilities?.let {
            Text("Firmware ${it.firmwareVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Text(title, color = contentColor, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun remindersSummary(settings: WatchSettings?): String? {
    val dnd: DoNotDisturb = settings?.doNotDisturb ?: return null
    return when {
        dnd.allDay -> "Do Not Disturb on"
        dnd.scheduled -> "Quiet ${formatMinutes(dnd.window.startMinute)}–${formatMinutes(dnd.window.endMinute)}"
        else -> null
    }
}

private fun monitoringSummary(settings: WatchSettings?): String? =
    settings?.healthMonitoring?.let { if (it.enabled) "Every ${it.intervalMinutes} min" else "Off" }

@Preview(showBackground = true)
@Composable
private fun WatchOverviewReadyPreview() {
    WatchTheme {
        PremiumBackground {
            WatchOverviewScreen(
                state = WatchSettingsUiState(
                    watchState = WatchState.Ready(battery = 72),
                    capabilities = WatchCapabilities(
                        heartRate = true, spo2 = true, bloodPressure = true, temperature = false, stress = false,
                        sport = true, gps = false, advancedReminders = false, weather = true, contactsLimit = 10,
                        doNotDisturb = true, heartRateAlert = true, timeFormat = true,
                        sedentaryIntervalConfigurable = true, monitorIntervalConfigurable = true, alarmLimit = 5,
                        firmwareVersion = "00000105",
                    ),
                    settings = WatchSettings(alarms = emptyList(), contacts = emptyList()),
                ),
                onOpen = {},
                onFindWatch = {},
                onOpenDiagnostics = {},
                presence = PresenceUiState(available = true),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WatchOverviewOfflinePreview() {
    WatchTheme {
        PremiumBackground {
            WatchOverviewScreen(
                state = WatchSettingsUiState(watchState = WatchState.Waiting(nextRetryAt = null)),
                onOpen = {},
                onFindWatch = {},
                onOpenDiagnostics = {},
            )
        }
    }
}
