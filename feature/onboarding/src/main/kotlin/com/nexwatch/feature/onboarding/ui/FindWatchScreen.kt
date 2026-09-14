package com.nexwatch.feature.onboarding.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.theme.ElectricBlue
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.DiscoveredDevice

/** design-prompt.md Batch 1 #4 — Find your watch. */
@Composable
fun FindWatchScreen(
    isScanning: Boolean,
    scanTimedOut: Boolean,
    devices: List<DiscoveredDevice>,
    onStartScan: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
) {
    LaunchedEffect(Unit) { if (!isScanning && devices.isEmpty()) onStartScan() }

    val headline = if (scanTimedOut) "Still looking" else "Finding your watch"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        EntranceItem(index = 0) {
            Text(headline, style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1, modifier = Modifier.padding(vertical = 32.dp)) {
            RadarScanner(active = isScanning)
        }
        when {
            devices.isNotEmpty() -> devices.forEachIndexed { index, device ->
                EntranceItem(index = 2 + index) {
                    DeviceRow(device, onClick = { onDeviceSelected(device) })
                }
            }
            scanTimedOut -> EntranceItem(index = 2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing found in 30 seconds.", style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(text = "Scan again", onClick = onStartScan, modifier = Modifier.padding(top = 16.dp))
                }
            }
            else -> EntranceItem(index = 2) {
                Text(
                    "Keep it within 1 m and make sure it isn't already connected to another app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RadarScanner(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "radarProgress",
    )
    Canvas(Modifier.size(160.dp)) {
        val maxRadius = size.minDimension / 2f
        if (active) {
            listOf(0f, 0.33f, 0.66f).forEach { phaseOffset ->
                val phase = (progress + phaseOffset) % 1f
                drawCircle(
                    color = ElectricBlue.copy(alpha = 1f - phase),
                    radius = maxRadius * phase,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        drawCircle(color = ElectricBlue, radius = maxRadius * 0.15f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, WatchShapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "•".repeat(device.signalBars) + "  ${device.address.takeLast(4)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true, name = "Scanning, 1 result")
@Composable
private fun FindWatchScreenResultPreview() {
    WatchTheme {
        PremiumBackground {
            FindWatchScreen(
                isScanning = false,
                scanTimedOut = false,
                devices = listOf(DiscoveredDevice("AA:BB:CC:DD:EE:FF", "GTR 3 Pro", 3)),
                onStartScan = {},
                onDeviceSelected = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Scanning")
@Composable
private fun FindWatchScreenScanningPreview() {
    WatchTheme {
        PremiumBackground {
            FindWatchScreen(isScanning = true, scanTimedOut = false, devices = emptyList(), onStartScan = {}, onDeviceSelected = {})
        }
    }
}

@Preview(showBackground = true, name = "Nothing found")
@Composable
private fun FindWatchScreenTimeoutPreview() {
    WatchTheme {
        PremiumBackground {
            FindWatchScreen(isScanning = false, scanTimedOut = true, devices = emptyList(), onStartScan = {}, onDeviceSelected = {})
        }
    }
}
