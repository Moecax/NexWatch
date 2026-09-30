package com.nexwatch.feature.watch

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nexwatch.core.designsystem.component.NumberStepper
import com.nexwatch.core.watchapi.WatchSettingChange

private val MONITOR_INTERVALS = listOf(5, 10, 15, 30, 60)

@Composable
internal fun HealthMonitoringScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Health monitoring", onBack, state.notice) {
        val settings = state.settings
        if (!state.isReady || settings == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var monitoring by remember(settings.healthMonitoring) { mutableStateOf(settings.healthMonitoring) }
        var alert by remember(settings.heartRateAlert) { mutableStateOf(settings.heartRateAlert) }

        monitoring?.let { current ->
            SettingsCard("Continuous heart rate") {
                SwitchRow("Measure in the background", current.enabled, { monitoring = current.copy(enabled = it) })
                if (current.enabled) {
                    WindowRows(current.window) { monitoring = current.copy(window = it) }
                    Column {
                        Text(
                            "Every",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ChoiceChips(
                            options = (MONITOR_INTERVALS + current.intervalMinutes).distinct().sorted(),
                            selected = current.intervalMinutes,
                            label = { "$it min" },
                            onSelected = { monitoring = current.copy(intervalMinutes = it) },
                        )
                    }
                    Text(
                        "More frequent monitoring uses more watch battery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        alert?.let { current ->
            SettingsCard("Heart-rate alert") {
                SwitchRow("Alert me on the watch", current.enabled, { alert = current.copy(enabled = it) })
                if (current.enabled) {
                    NumberStepper("High", current.highBpm, "bpm", { alert = current.copy(highBpm = it) }, step = 5, range = 100..220)
                    current.lowBpm?.let { low ->
                        NumberStepper("Low", low, "bpm", { alert = current.copy(lowBpm = it) }, step = 5, range = 30..100)
                    }
                }
            }
        }

        val changes = buildList<WatchSettingChange> {
            monitoring?.takeIf { it != settings.healthMonitoring }?.let { add(WatchSettingChange.SetHealthMonitoring(it)) }
            alert?.takeIf { it != settings.heartRateAlert }?.let { add(WatchSettingChange.SetHeartRateAlert(it)) }
        }
        SaveButton(onClick = { onSave(changes) }, enabled = changes.isNotEmpty(), busy = state.isBusy)
    }
}
