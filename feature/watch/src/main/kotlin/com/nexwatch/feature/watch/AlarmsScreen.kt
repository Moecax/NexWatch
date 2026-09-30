package com.nexwatch.feature.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.watchapi.WatchAlarm
import com.nexwatch.core.watchapi.WatchSettingChange
import java.time.DayOfWeek
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

private const val DEFAULT_ALARM_HOUR = 7

@Composable
internal fun AlarmsScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Alarms", onBack, state.notice) {
        val alarms = state.settings?.alarms
        val limit = state.capabilities?.alarmLimit
        if (!state.isReady || alarms == null || limit == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var draft by remember(alarms) { mutableStateOf(alarms) }

        if (draft.isEmpty()) {
            Text("No alarms on the watch.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        draft.forEachIndexed { index, alarm ->
            AlarmCard(
                alarm = alarm,
                onChange = { changed -> draft = draft.toMutableList().also { it[index] = changed } },
                onDelete = { draft = draft.toMutableList().also { it.removeAt(index) } },
            )
        }
        SecondaryButton(
            text = "Add alarm (${draft.size} of $limit)",
            onClick = {
                draft = draft + WatchAlarm(WatchAlarm.NEW_ID, DEFAULT_ALARM_HOUR, 0, emptySet(), enabled = true, label = "")
            },
            enabled = draft.size < limit,
            modifier = Modifier.fillMaxWidth(),
        )
        SaveButton(
            onClick = { onSave(listOf(WatchSettingChange.SetAlarms(draft))) },
            enabled = draft != alarms,
            busy = state.isBusy,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(alarm: WatchAlarm, onChange: (WatchAlarm) -> Unit, onDelete: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    SettingsCard(formatMinutes(alarm.hour * 60 + alarm.minute)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwitchRow("Enabled", alarm.enabled, { onChange(alarm.copy(enabled = it)) }, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete alarm at ${formatMinutes(alarm.hour * 60 + alarm.minute)}")
            }
        }
        TimeRow("Time", alarm.hour * 60 + alarm.minute, { onChange(alarm.copy(hour = it / 60, minute = it % 60)) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in alarm.repeatDays,
                    onClick = {
                        val days = if (day in alarm.repeatDays) alarm.repeatDays - day else alarm.repeatDays + day
                        onChange(alarm.copy(repeatDays = days))
                    },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                )
            }
        }
        if (alarm.repeatDays.isEmpty()) {
            Text("Rings once, at the next occurrence.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = alarm.label,
            onValueChange = { onChange(alarm.copy(label = it)) },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
