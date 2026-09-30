package com.nexwatch.feature.watch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.DrinkWaterReminder
import com.nexwatch.core.watchapi.MinuteWindow
import com.nexwatch.core.watchapi.SedentaryReminder
import com.nexwatch.core.watchapi.WatchSettingChange

private val REMINDER_INTERVALS = listOf(30, 60, 90, 120)

@Composable
internal fun RemindersScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Reminders & Do Not Disturb", onBack, state.notice) {
        val settings = state.settings
        if (!state.isReady || settings == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var dnd by remember(settings.doNotDisturb) { mutableStateOf(settings.doNotDisturb) }
        var sedentary by remember(settings.sedentaryReminder) { mutableStateOf(settings.sedentaryReminder) }
        var water by remember(settings.drinkWaterReminder) { mutableStateOf(settings.drinkWaterReminder) }

        dnd?.let { current -> DoNotDisturbCard(current) { dnd = it } }
        sedentary?.let { current -> SedentaryCard(current) { sedentary = it } }
        water?.let { current -> DrinkWaterCard(current) { water = it } }

        val changes = buildList<WatchSettingChange> {
            dnd?.takeIf { it != settings.doNotDisturb }?.let { add(WatchSettingChange.SetDoNotDisturb(it)) }
            sedentary?.takeIf { it != settings.sedentaryReminder }?.let { add(WatchSettingChange.SetSedentaryReminder(it)) }
            water?.takeIf { it != settings.drinkWaterReminder }?.let { add(WatchSettingChange.SetDrinkWaterReminder(it)) }
        }
        SaveButton(onClick = { onSave(changes) }, enabled = changes.isNotEmpty(), busy = state.isBusy)
    }
}

@Composable
private fun DoNotDisturbCard(value: DoNotDisturb, onChange: (DoNotDisturb) -> Unit) {
    SettingsCard("Do Not Disturb") {
        SwitchRow("All day", value.allDay, { onChange(value.copy(allDay = it)) })
        SwitchRow("Quiet hours", value.scheduled, { onChange(value.copy(scheduled = it)) })
        if (value.scheduled) {
            WindowRows(value.window) { onChange(value.copy(window = it)) }
        }
    }
}

@Composable
private fun SedentaryCard(value: SedentaryReminder, onChange: (SedentaryReminder) -> Unit) {
    SettingsCard("Move reminder") {
        SwitchRow("Remind me to move", value.enabled, { onChange(value.copy(enabled = it)) })
        if (value.enabled) {
            WindowRows(value.window) { onChange(value.copy(window = it)) }
            IntervalPicker(value.intervalMinutes) { onChange(value.copy(intervalMinutes = it)) }
            SwitchRow(
                "Respect Do Not Disturb",
                value.respectsDoNotDisturb,
                { onChange(value.copy(respectsDoNotDisturb = it)) },
            )
        }
    }
}

@Composable
private fun DrinkWaterCard(value: DrinkWaterReminder, onChange: (DrinkWaterReminder) -> Unit) {
    SettingsCard("Drink water") {
        SwitchRow("Remind me to drink", value.enabled, { onChange(value.copy(enabled = it)) })
        if (value.enabled) {
            WindowRows(value.window) { onChange(value.copy(window = it)) }
            IntervalPicker(value.intervalMinutes) { onChange(value.copy(intervalMinutes = it)) }
        }
    }
}

@Composable
internal fun WindowRows(window: MinuteWindow, onChange: (MinuteWindow) -> Unit) {
    TimeRow("From", window.startMinute, { onChange(window.copy(startMinute = it)) })
    TimeRow("Until", window.endMinute, { onChange(window.copy(endMinute = it)) })
}

@Composable
private fun IntervalPicker(minutes: Int, onChange: (Int) -> Unit) {
    Column {
        Text("Every", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        // A read-back value outside the presets (the watch snapped it) still has to show up.
        ChoiceChips(
            options = (REMINDER_INTERVALS + minutes).distinct().sorted(),
            selected = minutes,
            label = { "$it min" },
            onSelected = onChange,
        )
    }
}
