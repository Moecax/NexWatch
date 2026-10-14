package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.StepProgressBar
import com.nexwatch.core.designsystem.component.WatchRender
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.PairingPhase

private val PHASE_LABELS = listOf(
    PairingPhase.CONNECTING to "Connecting",
    PairingPhase.AUTHENTICATING to "Authenticating",
    PairingPhase.READING_FEATURES to "Reading watch features",
    PairingPhase.FIRST_SYNC to "First sync",
)

/** design-prompt.md Batch 1 #6 — Pairing in progress, connected, failed. */
@Composable
fun PairingScreen(
    phase: PairingPhase,
    battery: Int?,
    firmwareVersion: String?,
    error: String?,
    address: String?,
    deviceName: String?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            EntranceItem(index = 0) {
                StepProgressBar(total = ONBOARDING_STEPS, current = 6, modifier = Modifier.padding(bottom = 16.dp))
            }
            when (phase) {
                PairingPhase.SUCCESS -> SuccessBody(battery, firmwareVersion, address, deviceName)
                PairingPhase.FAILED -> FailedBody(error)
                else -> ProgressBody(phase)
            }
        }
        when (phase) {
            PairingPhase.SUCCESS -> EntranceItem(index = 3, modifier = Modifier.padding(top = 16.dp)) {
                PrimaryButton(text = "Continue", onClick = onContinue)
            }
            PairingPhase.FAILED -> EntranceItem(index = 2, modifier = Modifier.padding(top = 16.dp)) {
                PrimaryButton(text = "Retry pairing", onClick = onRetry)
            }
            else -> Unit
        }
    }
}

@Composable
private fun ProgressBody(phase: PairingPhase) {
    val progress = when (phase) {
        PairingPhase.CONNECTING -> 0.25f
        PairingPhase.AUTHENTICATING -> 0.5f
        PairingPhase.READING_FEATURES -> 0.75f
        PairingPhase.FIRST_SYNC -> 0.9f
        else -> 1f
    }
    EntranceItem(index = 1) {
        Text("Pairing your watch", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    EntranceItem(index = 2) {
        Text(
            "Keep NexWatch open and the watch nearby.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    EntranceItem(index = 3, modifier = Modifier.padding(vertical = 24.dp)) {
        PairingProgressRing(progress = progress) {
            WatchRender(headSize = 132.dp)
        }
    }
    EntranceItem(index = 4) {
        NexCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
            PHASE_LABELS.forEachIndexed { index, (step, label) ->
                if (index > 0) HorizontalDivider()
                val done = step.ordinal < phase.ordinal
                val current = step == phase
                StepRow(label = label, done = done, current = current)
            }
        }
    }
}

@Composable
private fun SuccessBody(battery: Int?, firmwareVersion: String?, address: String?, deviceName: String?) {
    EntranceItem(index = 1, modifier = Modifier.padding(top = 12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(WatchTheme.colors.success.copy(alpha = 0.14f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = WatchTheme.colors.success,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                "Connected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "${deviceName ?: "Your watch"} is paired with this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    EntranceItem(index = 2, modifier = Modifier.padding(top = 24.dp)) {
        NexCard {
            DetailRow("Battery", battery?.let { "$it%" } ?: "--")
            HorizontalDivider()
            DetailRow("Firmware", firmwareVersion ?: "--")
            HorizontalDivider()
            DetailRow("Address", address?.let { "…${it.takeLast(4)}" } ?: "--")
        }
    }
}

@Composable
private fun FailedBody(error: String?) {
    EntranceItem(index = 1, modifier = Modifier.padding(top = 12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(WatchTheme.colors.error.copy(alpha = 0.12f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = WatchTheme.colors.error,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                "Couldn't connect",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                error ?: "The watch wasn't reachable. Keep it close and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
            when {
                done -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = WatchTheme.colors.success,
                    modifier = Modifier.size(22.dp),
                )
                current -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> Icon(
                    Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (done || current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true, name = "Authenticating")
@Composable
private fun PairingScreenInProgressPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.AUTHENTICATING, null, null, null, null, null, {}, {})
        }
    }
}

@Preview(showBackground = true, name = "Success")
@Composable
private fun PairingScreenSuccessPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.SUCCESS, 72, "1.05", null, "AA:BB:CC:DD:EE:FF", "GTR 3 Pro", {}, {})
        }
    }
}

@Preview(showBackground = true, name = "Failed")
@Composable
private fun PairingScreenFailedPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.FAILED, null, null, "Watch not ready", null, null, {}, {})
        }
    }
}
