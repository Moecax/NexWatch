package com.nexwatch.feature.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nexwatch.core.designsystem.component.SegmentedControl
import com.nexwatch.core.watchapi.WatchSettingChange

@Composable
internal fun UnitsScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Units & time", onBack, state.notice) {
        val units = state.settings?.displayUnits
        if (!state.isReady || units == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var draft by remember(units) { mutableStateOf(units) }

        SettingsCard("Display") {
            if (state.capabilities?.timeFormat == true) {
                SegmentedControl(
                    options = listOf("12-hour", "24-hour"),
                    selectedIndex = if (draft.use24HourClock) 1 else 0,
                    onSelected = { draft = draft.copy(use24HourClock = it == 1) },
                )
            }
            SegmentedControl(
                options = listOf("Kilometres", "Miles"),
                selectedIndex = if (draft.imperialLength) 1 else 0,
                onSelected = { draft = draft.copy(imperialLength = it == 1) },
            )
            SegmentedControl(
                options = listOf("°C", "°F"),
                selectedIndex = if (draft.fahrenheit) 1 else 0,
                onSelected = { draft = draft.copy(fahrenheit = it == 1) },
            )
        }
        SaveButton(
            onClick = { onSave(listOf(WatchSettingChange.SetDisplayUnits(draft))) },
            enabled = draft != units,
            busy = state.isBusy,
        )
    }
}

@Composable
internal fun WristRaiseScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Wrist raise", onBack, state.notice) {
        val current = state.settings?.wristRaise
        if (!state.isReady || current == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var draft by remember(current) { mutableStateOf(current) }

        SettingsCard("Raise to wake") {
            SwitchRow(
                "Light the screen when I raise my wrist",
                draft.enabled,
                { draft = draft.copy(enabled = it) },
                supporting = "Keeping the screen on more often uses more watch battery.",
            )
            if (draft.enabled) WindowRows(draft.window) { draft = draft.copy(window = it) }
        }
        SaveButton(
            onClick = { onSave(listOf(WatchSettingChange.SetWristRaise(draft))) },
            enabled = draft != current,
            busy = state.isBusy,
        )
    }
}
