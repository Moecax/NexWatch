package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.PairingPhase

private val PHASE_LABELS = listOf(
    PairingPhase.CONNECTING to "Connecting",
    PairingPhase.AUTHENTICATING to "Authenticating",
    PairingPhase.READING_FEATURES to "Reading watch features",
    PairingPhase.FIRST_SYNC to "First sync",
)

/** design-prompt.md Batch 1 #6 — Pairing in progress. */
@Composable
fun PairingScreen(
    phase: PairingPhase,
    battery: Int?,
    firmwareVersion: String?,
    error: String?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        EntranceItem(index = 0, modifier = Modifier.padding(vertical = 32.dp)) {
            WatchHeroRender()
        }
        when (phase) {
            PairingPhase.SUCCESS -> EntranceItem(index = 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connected", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Battery ${battery ?: "--"}% · Firmware ${firmwareVersion ?: "--"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    PrimaryButton(text = "Continue", onClick = onContinue)
                }
            }
            PairingPhase.FAILED -> EntranceItem(index = 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't connect", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        error ?: "The watch wasn't reachable.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    SecondaryButton(text = "Retry", onClick = onRetry)
                }
            }
            else -> EntranceItem(index = 1) {
                Column {
                    PHASE_LABELS.forEach { (step, label) ->
                        val done = step.ordinal < phase.ordinal
                        val current = step == phase
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            when {
                                done -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                current -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                            Text(
                                label,
                                modifier = Modifier.padding(start = 12.dp),
                                color = if (current) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Authenticating")
@Composable
private fun PairingScreenInProgressPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.AUTHENTICATING, null, null, null, {}, {})
        }
    }
}

@Preview(showBackground = true, name = "Success")
@Composable
private fun PairingScreenSuccessPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.SUCCESS, 82, "1.4.2", null, {}, {})
        }
    }
}

@Preview(showBackground = true, name = "Failed")
@Composable
private fun PairingScreenFailedPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.FAILED, null, null, "Watch not ready", {}, {})
        }
    }
}
