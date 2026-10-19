package com.nexwatch.feature.watch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nexwatch.core.designsystem.component.NumberStepper
import com.nexwatch.core.designsystem.component.SegmentedControl
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchapi.WristSide

private val STEP_GOAL_RANGE = 1_000..50_000
private const val STEP_GOAL_STEP = 500

@Composable
internal fun WristSideScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Worn on", onBack, state.notice) {
        if (!state.isReady) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        val current = state.settings?.wristSide ?: run {
            UnreadNote()
            return@SettingsScaffold
        }
        var draft by remember(current) { mutableStateOf(current) }

        SettingsCard("Wrist") {
            SegmentedControl(
                options = listOf("Left wrist", "Right wrist"),
                selectedIndex = if (draft == WristSide.LEFT) 0 else 1,
                onSelected = { draft = if (it == 0) WristSide.LEFT else WristSide.RIGHT },
            )
            Text(
                "Tells the watch which wrist it's on, so raise to wake responds to the right movement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SaveButton(
            onClick = { onSave(listOf(WatchSettingChange.SetWristSide(draft))) },
            enabled = draft != current,
            busy = state.isBusy,
        )
    }
}

@Composable
internal fun StepGoalScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Daily step goal", onBack, state.notice) {
        if (!state.isReady) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        val settings = state.settings ?: run {
            UnreadNote()
            return@SettingsScaffold
        }
        // Whatever the watch doesn't report (a firmware that won't answer the goal query), the
        // app's own copy fills the stepper; a save still goes to the watch and reports a refusal.
        val current = settings.stepGoal ?: state.appStepGoal
        var draft by remember(current) { mutableIntStateOf(current) }

        SettingsCard("Goal") {
            NumberStepper("Steps a day", draft, "steps", { draft = it }, step = STEP_GOAL_STEP, range = STEP_GOAL_RANGE)
            Text(
                "Saved to the watch, and used for the rings on Today and the goal line in Health.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SaveButton(
            onClick = { onSave(listOf(WatchSettingChange.SetStepGoal(draft))) },
            enabled = draft != current,
            busy = state.isBusy,
        )
    }
}
