package com.nexwatch.feature.watch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nexwatch.core.designsystem.component.NumberStepper
import com.nexwatch.core.watchapi.WatchSettingChange

private val SYSTOLIC_RANGE = 70..200
private val DIASTOLIC_RANGE = 40..130

@Composable
internal fun BloodPressureReferenceScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Blood pressure reference", onBack, state.notice) {
        val settings = state.settings
        if (!state.isReady || settings == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var reference by remember(settings.bloodPressureReference) { mutableStateOf(settings.bloodPressureReference) }

        if (reference == null) {
            Text(
                "This watch doesn't report a blood pressure reference.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SettingsScaffold
        }
        reference?.let { current ->
            SettingsCard("Cuff reading") {
                SwitchRow("Calibrate the watch", current.enabled, { reference = current.copy(enabled = it) })
                if (current.enabled) {
                    NumberStepper("Systolic", current.systolic, "mmHg", { reference = current.copy(systolic = it) }, range = SYSTOLIC_RANGE)
                    NumberStepper("Diastolic", current.diastolic, "mmHg", { reference = current.copy(diastolic = it) }, range = DIASTOLIC_RANGE)
                    Text(
                        "Enter a reading from a cuff monitor. The watch uses it to calibrate its estimates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (current.systolic <= current.diastolic) {
                        Text(
                            "Systolic has to be higher than diastolic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // A reading the watch would reject never reaches the save button, so the screen can't
        // leave a half-written reference behind.
        val changes = buildList<WatchSettingChange> {
            reference
                ?.takeIf { it != settings.bloodPressureReference && it.systolic > it.diastolic }
                ?.let { add(WatchSettingChange.SetBloodPressureReference(it)) }
        }
        SaveButton(onClick = { onSave(changes) }, enabled = changes.isNotEmpty(), busy = state.isBusy)
    }
}
