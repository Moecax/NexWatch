package com.nexwatch.feature.watch

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.nexwatch.core.designsystem.component.NumberStepper
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.watchapi.WeatherCondition

/**
 * A manual push, not the scheduled fetch of §8.7: the condition codes FitCloud draws are
 * unverified (Phase 4), and this is the way to see what the watch shows for each one.
 */
@Composable
internal fun WeatherScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onPush: (WeatherCondition, Int) -> Unit,
) {
    SettingsScaffold("Weather", onBack, state.notice) {
        if (!state.isReady) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var condition by remember { mutableStateOf(WeatherCondition.SUNNY) }
        var temperature by remember { mutableIntStateOf(DEFAULT_TEMPERATURE_C) }

        SettingsCard("Send a forecast") {
            Text(
                "Pushes a three-day forecast so you can see how the watch draws each condition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceChips(
                options = WeatherCondition.entries,
                selected = condition,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                onSelected = { condition = it },
            )
            NumberStepper("Temperature", temperature, "°C", { temperature = it }, range = -30..50)
        }
        PrimaryButton(
            text = if (state.isBusy) "Sending…" else "Send to watch",
            onClick = { onPush(condition, temperature) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val DEFAULT_TEMPERATURE_C = 22
