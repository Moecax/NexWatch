package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** design-prompt.md Batch 1 #7 — Keep it running. */
@Composable
fun KeepRunningScreen(
    onBatteryOptimization: () -> Unit,
    onAutostartHint: () -> Unit,
    onTestBackgroundConnection: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Keep it running", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "Android may stop the app in the background. These steps keep your watch connected.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
        EntranceItem(index = 2) {
            KeepRunningCard("Allow unrestricted battery use", "Prevents Android from pausing the connection.", "Allow", onBatteryOptimization)
        }
        EntranceItem(index = 3) {
            KeepRunningCard("Enable autostart", "Some phones need this turned on manually after a restart.", "Show me", onAutostartHint)
        }
        EntranceItem(index = 4) {
            KeepRunningCard("Test background connection", "Confirms the watch stays connected with the app closed.", "Test", onTestBackgroundConnection)
        }
        EntranceItem(index = 5, modifier = Modifier.padding(top = 24.dp)) {
            PrimaryButton(text = "Done", onClick = onDone)
        }
    }
}

@Composable
private fun KeepRunningCard(title: String, description: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, WatchShapes.card)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        SecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 12.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun KeepRunningScreenPreview() {
    WatchTheme {
        PremiumBackground {
            KeepRunningScreen(onBatteryOptimization = {}, onAutostartHint = {}, onTestBackgroundConnection = {}, onDone = {})
        }
    }
}
