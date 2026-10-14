package com.nexwatch.feature.onboarding.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.StepProgressBar
import com.nexwatch.core.designsystem.theme.ElectricBlue
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
    val context = LocalContext.current
    var bluetoothOff by remember { mutableStateOf(isBluetoothOff(context)) }
    // The radio is toggled outside the app, so re-read it on every return.
    LifecycleResumeEffect(Unit) {
        bluetoothOff = isBluetoothOff(context)
        onPauseOrDispose {}
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        bluetoothOff = isBluetoothOff(context)
        if (!bluetoothOff) onStartScan()
    }

    LaunchedEffect(Unit) { if (!isScanning && devices.isEmpty() && !bluetoothOff) onStartScan() }

    // The scrolling part holds the list, so any number of nearby watches stays reachable, and
    // the state's one action stays pinned below it.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            EntranceItem(index = 0) {
                StepProgressBar(total = ONBOARDING_STEPS, current = 4, modifier = Modifier.padding(bottom = 16.dp))
            }
            when {
                bluetoothOff -> {
                    Heading("Bluetooth is off", "NexWatch needs Bluetooth to find your watch.")
                    EntranceItem(index = 3, modifier = Modifier.padding(vertical = 32.dp)) {
                        RadarScanner(active = false, icon = Icons.Filled.BluetoothDisabled)
                    }
                }

                devices.isNotEmpty() -> {
                    Heading("Finding your watch", "Keep it within 1 m and make sure it isn't already connected to another app.")
                    EntranceItem(index = 3, modifier = Modifier.padding(vertical = 32.dp)) {
                        RadarScanner(active = isScanning, icon = Icons.Filled.BluetoothSearching)
                    }
                    EntranceItem(index = 4) {
                        NexCard(contentPadding = PaddingValues(0.dp)) {
                            devices.forEachIndexed { index, device ->
                                if (index > 0) HorizontalDivider()
                                DeviceRow(device, onClick = { onDeviceSelected(device) })
                            }
                        }
                    }
                }

                scanTimedOut -> {
                    // No duration in the copy: the scan window lives in the ViewModel and the
                    // client, and a number here would drift from both.
                    Heading("Still looking", "No watch showed up nearby.")
                    EntranceItem(index = 3, modifier = Modifier.padding(vertical = 32.dp)) {
                        RadarScanner(active = false, icon = Icons.Filled.Search)
                    }
                    EntranceItem(index = 4) {
                        NexCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                            TipRow("Make sure the watch isn't connected to another app.")
                            TipRow("Keep it within 1 m of your phone.")
                        }
                    }
                }

                else -> {
                    Heading("Finding your watch", "Keep it within 1 m and make sure it isn't already connected to another app.")
                    EntranceItem(index = 3, modifier = Modifier.padding(vertical = 32.dp)) {
                        RadarScanner(active = isScanning, icon = Icons.Filled.BluetoothSearching)
                    }
                }
            }
        }
        when {
            bluetoothOff -> {
                EntranceItem(index = 5, modifier = Modifier.padding(top = 16.dp)) {
                    PrimaryButton(
                        text = "Turn on Bluetooth",
                        onClick = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    )
                }
                FootNote("Scanning starts as soon as it's on.")
            }
            devices.isNotEmpty() -> FootNote("Tap a watch to pair with it.")
            scanTimedOut -> EntranceItem(index = 5, modifier = Modifier.padding(top = 16.dp)) {
                PrimaryButton(text = "Scan again", onClick = onStartScan)
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    EntranceItem(index = 1) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    EntranceItem(index = 2) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FootNote(text: String) {
    EntranceItem(index = 6, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun isBluetoothOff(context: Context): Boolean =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == false

/**
 * Concentric rings expanding from the core while scanning. The one looping
 * animation on this screen, and only while a scan is genuinely running: when
 * it's off (Bluetooth off, or the 30-second timeout), the radar holds still.
 */
@Composable
private fun RadarScanner(active: Boolean, icon: ImageVector) {
    Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        // Composed only while scanning: an idle radar must not keep a frame clock ticking.
        val progress = if (active) radarProgress() else 0f
        val ringColor = ElectricBlue
        val coreColor = if (active) ElectricBlue else MaterialTheme.colorScheme.onSurfaceVariant
        val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.size(180.dp)) {
            val maxRadius = size.minDimension / 2f
            val coreRadius = maxRadius * 0.167f
            if (active) {
                listOf(0f, 0.33f, 0.66f).forEach { phaseOffset ->
                    val phase = (progress + phaseOffset) % 1f
                    drawCircle(
                        color = ringColor.copy(alpha = 1f - phase),
                        radius = coreRadius + (maxRadius - coreRadius) * phase,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            drawCircle(
                color = if (active) ringColor.copy(alpha = 0.18f) else surfaceColor,
                radius = coreRadius,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
        Icon(icon, contentDescription = null, tint = coreColor, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun radarProgress(): Float {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radarProgress",
    )
    return progress
}

@Composable
private fun TipRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        IconBadge(
            icon = Icons.Filled.Watch,
            tint = WatchTheme.colors.success,
            background = WatchTheme.colors.success.copy(alpha = 0.14f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(device.displayName, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "…${device.address.takeLast(4)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SignalBars(bars = device.signalBars)
    }
}

@Preview(showBackground = true, name = "Scanning, 1 result")
@Composable
private fun FindWatchScreenResultPreview() {
    WatchTheme {
        PremiumBackground {
            FindWatchScreen(
                isScanning = true,
                scanTimedOut = false,
                devices = listOf(DiscoveredDevice("AA:BB:CC:DD:EE:FF", "GTR 3 Pro", 3)),
                onStartScan = {},
                onDeviceSelected = {},
            )
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
