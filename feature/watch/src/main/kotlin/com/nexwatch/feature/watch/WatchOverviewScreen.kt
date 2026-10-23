package com.nexwatch.feature.watch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BackHand
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WifiTethering
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.ListRow
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.RowGroup
import com.nexwatch.core.designsystem.component.ScreenTitle
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.SectionLabel
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.component.WatchHands
import com.nexwatch.core.designsystem.component.WatchRender
import com.nexwatch.core.designsystem.component.WatchSwitch
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.MinuteWindow
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchSettings
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WristRaise
import com.nexwatch.core.watchapi.WristSide
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** The SDK doesn't say when the watch stops ringing, so the ringing state clears after this. */
private const val RINGING_SHOWN_MS = 8_000L
private const val WAVE_MS = 1800

private val lastSeenFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

@Composable
internal fun WatchOverviewScreen(
    state: WatchSettingsUiState,
    onOpen: (WatchSection) -> Unit,
    onFindWatch: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    presence: PresenceUiState = PresenceUiState(),
    onEnablePresence: () -> Unit = {},
    notificationsValue: String? = null,
    status: WatchStatusUiState = WatchStatusUiState(),
    onSyncNow: () -> Unit = {},
    onWristRaiseChange: (Boolean) -> Unit = {},
    onDismissNotice: () -> Unit = {},
) {
    val capabilities = state.capabilities
    val settings = state.settings
    val ringing = state.notice == Notice.Saved(WatchSettingsViewModel.RINGING)
    LaunchedEffect(ringing) {
        if (ringing) {
            delay(RINGING_SHOWN_MS)
            onDismissNotice()
        }
    }
    var index = 0
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntranceItem(index++) { ScreenTitle("Watch") }
        EntranceItem(index++) { Hero(state, status, ringing) }
        EntranceItem(index++) {
            if (state.isReady) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ringing) {
                        SecondaryButton("Stop", onClick = onDismissNotice, icon = Icons.Rounded.StopCircle, modifier = Modifier.weight(1f))
                    } else {
                        PrimaryButton("Find watch", onClick = onFindWatch, enabled = !state.isBusy, icon = Icons.Rounded.Vibration, modifier = Modifier.weight(1f))
                    }
                    SecondaryButton(
                        if (status.syncing) "Syncing…" else "Sync now",
                        onClick = onSyncNow,
                        enabled = !status.syncing,
                        icon = Icons.Rounded.Sync,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                AwayNote(state.watchState, status.lastConnectedMs)
            }
        }
        if (!ringing) NoticeLine(state.notice)

        val disabled = !state.isReady
        EntranceItem(index++) { SectionLabel("On your wrist", Modifier.padding(top = 10.dp)) }
        EntranceItem(index++) {
            RowGroup(
                rows = buildList<@Composable () -> Unit> {
                    // Not gated on the watch being connected: these are phone-side settings.
                    add { ListRow(Icons.AutoMirrored.Rounded.Message, "Notifications and calls", value = notificationsValue, onClick = { onOpen(WatchSection.NOTIFICATIONS) }) }
                    // Only what this watch reports as supported gets a row (§4.5).
                    if (capabilities == null || capabilities.heartRate) {
                        add { ListRow(Icons.Rounded.MonitorHeart, "Health monitoring", value = monitoringSummary(settings), enabled = !disabled, onClick = { onOpen(WatchSection.HEALTH_MONITORING) }) }
                    }
                    // Gated on capabilities like its siblings, so the row is in place (disabled)
                    // before the settings read lands instead of appearing after it (§4.5).
                    if (capabilities == null || capabilities.bloodPressure) {
                        add {
                            ListRow(
                                Icons.Rounded.Bloodtype, "Blood pressure reference",
                                value = bloodPressureSummary(settings),
                                enabled = !disabled, onClick = { onOpen(WatchSection.BLOOD_PRESSURE_REFERENCE) },
                            )
                        }
                    }
                    add { ListRow(Icons.Rounded.DoNotDisturbOn, "Reminders and DND", value = remindersSummary(settings), enabled = !disabled, onClick = { onOpen(WatchSection.REMINDERS) }) }
                    if (capabilities?.alarmLimit != null) {
                        add {
                            ListRow(
                                Icons.Rounded.Alarm, "Alarms",
                                value = settings?.alarms?.let { "${it.size} of ${capabilities.alarmLimit}" },
                                enabled = !disabled, onClick = { onOpen(WatchSection.ALARMS) },
                            )
                        }
                    }
                    add {
                        val wrist = settings?.wristRaise
                        ListRow(
                            Icons.Rounded.Watch, "Wrist raise",
                            description = wrist?.let { "Active ${formatMinutes(it.window.startMinute)} to ${formatMinutes(it.window.endMinute)}" } ?: "Wakes the screen when you look",
                            enabled = !disabled,
                            onClick = { onOpen(WatchSection.WRIST_RAISE) },
                            trailing = {
                                WatchSwitch(
                                    checked = wrist?.enabled == true,
                                    onCheckedChange = onWristRaiseChange,
                                    enabled = !disabled && wrist != null && !state.isBusy,
                                )
                            },
                        )
                    }
                    add {
                        ListRow(
                            Icons.Rounded.BackHand, "Worn on",
                            value = settings?.wristSide?.let { if (it == WristSide.LEFT) "Left wrist" else "Right wrist" },
                            enabled = !disabled, onClick = { onOpen(WatchSection.WRIST_SIDE) },
                        )
                    }
                    add {
                        ListRow(
                            Icons.AutoMirrored.Rounded.DirectionsWalk, "Daily step goal",
                            value = settings?.stepGoal?.let { "%,d steps".format(Locale.UK, it) },
                            enabled = !disabled, onClick = { onOpen(WatchSection.STEP_GOAL) },
                        )
                    }
                },
            )
        }
        EntranceItem(index++) { SectionLabel("From your phone", Modifier.padding(top = 10.dp)) }
        EntranceItem(index++) {
            RowGroup(
                rows = buildList<@Composable () -> Unit> {
                    if (capabilities?.contactsLimit != null) {
                        add {
                            ListRow(
                                Icons.Rounded.Contacts, "Contacts",
                                value = settings?.contacts?.let { "${it.size} of ${capabilities.contactsLimit}" },
                                enabled = !disabled, onClick = { onOpen(WatchSection.CONTACTS) },
                            )
                        }
                    }
                    if (capabilities?.weather == true) {
                        add { ListRow(Icons.Rounded.Cloud, "Weather", enabled = !disabled, onClick = { onOpen(WatchSection.WEATHER) }) }
                    }
                    add { ListRow(Icons.Rounded.Straighten, "Units and time", value = unitsSummary(settings), enabled = !disabled, onClick = { onOpen(WatchSection.UNITS) }) }
                },
            )
        }
        EntranceItem(index++) { SectionLabel("Staying connected", Modifier.padding(top = 10.dp)) }
        EntranceItem(index++) {
            RowGroup(
                rows = buildList<@Composable () -> Unit> {
                    if (presence.available) add { PresenceRow(presence, onEnablePresence) }
                    add { ListRow(Icons.Rounded.Troubleshoot, "Diagnostics", description = "Connection timeline and logs", onClick = onOpenDiagnostics) }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The phone's wall clock to the minute, for the hero's hands. Wakes once per minute boundary and
 * only while the screen is started, so it is a display tick, not a poll; previews stay on the
 * 10:10 product pose.
 */
@Composable
private fun rememberMinuteClock(): LocalTime {
    val inspection = LocalInspectionMode.current
    var now by remember { mutableStateOf(if (inspection) LocalTime.of(10, 10) else LocalTime.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, inspection) {
        if (inspection) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = LocalTime.now()
                now = current
                delay((60 - current.second) * 1_000L - current.nano / 1_000_000)
            }
        }
    }
    return now
}

@Composable
private fun Hero(state: WatchSettingsUiState, status: WatchStatusUiState, ringing: Boolean) {
    NexCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (ringing) RingingWaves()
                val time = rememberMinuteClock()
                WatchRender(headSize = 104.dp) { WatchHands(time.hour, time.minute) }
            }
            Column(Modifier.weight(1f)) {
                Text("Your watch", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                val idLine = listOfNotNull(
                    state.capabilities?.firmwareVersion?.let { "Firmware $it" },
                    status.idSuffix?.let { "ID ends $it" },
                ).joinToString(", ")
                if (idLine.isNotEmpty()) {
                    Text(idLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
                ConnectionChip(state.watchState, Modifier.padding(top = 10.dp))
                (state.watchState as? WatchState.Ready)?.let { ready -> ready.battery?.let { BatteryBar(it, ready.charging) } }
                SyncFailureLine(status, Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun ConnectionChip(watchState: WatchState, modifier: Modifier = Modifier) {
    when (watchState) {
        is WatchState.Ready -> StatusChip("Connected", StatusTone.SUCCESS, modifier, live = true)
        WatchState.Connecting -> StatusChip("Connecting", StatusTone.NEUTRAL, modifier, icon = Icons.Rounded.WifiTethering)
        is WatchState.Waiting -> StatusChip("Not nearby", StatusTone.NEUTRAL, modifier, icon = Icons.Rounded.BluetoothDisabled)
        WatchState.BluetoothOff -> StatusChip("Bluetooth is off", StatusTone.WARNING, modifier, icon = Icons.Rounded.BluetoothDisabled)
        is WatchState.AuthFailed -> StatusChip("Signed in elsewhere", StatusTone.ERROR, modifier)
        WatchState.Unbound -> StatusChip("Not paired", StatusTone.NEUTRAL, modifier)
    }
}

/**
 * The Watch tab's version of the Today strip's sync line. The warning tone never carries the
 * meaning on its own (§7): the icon and the word "failed" do too, and the Sync now button
 * below stays enabled so the sentence has an answer.
 */
@Composable
private fun SyncFailureLine(status: WatchStatusUiState, modifier: Modifier = Modifier) {
    val error = status.lastSyncError
    if (error == null || status.syncing) return
    val warning = WatchTheme.colors.warning
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(16.dp))
        Text(
            "Sync failed · $error",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = warning,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun BatteryBar(level: Int, charging: Boolean) {
    val color = if (level <= 20 && !charging) WatchTheme.colors.warning else WatchTheme.colors.success
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (charging) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            Text(
                if (charging) "Charging" else "Battery",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("$level%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.pill)) {
            Box(Modifier.fillMaxWidth(level.coerceIn(0, 100) / 100f).height(5.dp).background(color, WatchShapes.pill))
        }
    }
}

/** Ringing is a real, ongoing state on the watch, so it is allowed to loop until it stops. */
@Composable
private fun RingingWaves() {
    if (LocalInspectionMode.current) return
    val color = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "ringing")
    val phases = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(WAVE_MS, delayMillis = i * WAVE_MS / 3, easing = WatchMotion.easeOutStrong), RepeatMode.Restart),
            label = "wave$i",
        )
    }
    Canvas(Modifier.size(104.dp)) {
        phases.forEach { phase ->
            val p by phase
            drawCircle(color.copy(alpha = 0.7f * (1f - p)), radius = size.minDimension / 2 * (0.96f + 0.55f * p), style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun AwayNote(watchState: WatchState, lastConnectedMs: Long?) {
    val lastSeen = lastConnectedMs?.let { "Last seen ${lastSeenFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}. " } ?: ""
    // No Reconnect button anywhere: FcConnector owns reconnection (CLAUDE.md), so the note
    // says what will happen instead of offering a control that would fight it.
    val text = when (watchState) {
        is WatchState.Waiting -> "${lastSeen}NexWatch reconnects by itself once the watch is in range. Settings below unlock when it's back."
        WatchState.BluetoothOff -> "Bluetooth is off. Turn it on and the watch reconnects by itself."
        is WatchState.AuthFailed -> "Another app is signed in to the watch. Close it, then retry from Today."
        WatchState.Connecting -> "Connecting to your watch. Settings unlock once it's connected."
        WatchState.Unbound -> "No watch is paired with this phone."
        is WatchState.Ready -> return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PresenceRow(presence: PresenceUiState, onEnable: () -> Unit) {
    // Association is one-way from here: CDM has no in-app "off", so once on the switch stays on.
    ListRow(
        Icons.Rounded.BluetoothSearching,
        "Wake when watch is nearby",
        description = presence.failure ?: "Lets Android start NexWatch when the watch comes into range, even after it was closed.",
        trailing = {
            WatchSwitch(
                checked = presence.associated,
                onCheckedChange = { if (it) onEnable() },
                enabled = !presence.associated && !presence.busy,
            )
        },
    )
}

private fun remindersSummary(settings: WatchSettings?): String? {
    val dnd: DoNotDisturb = settings?.doNotDisturb ?: return null
    return when {
        dnd.allDay -> "DND all day"
        dnd.scheduled -> "Quiet from ${formatMinutes(dnd.window.startMinute)}"
        else -> null
    }
}

private fun monitoringSummary(settings: WatchSettings?): String? =
    settings?.healthMonitoring?.let { if (it.enabled) "Every ${it.intervalMinutes} min" else "Off" }

private fun bloodPressureSummary(settings: WatchSettings?): String? =
    settings?.bloodPressureReference?.let { if (it.enabled) "${it.systolic}/${it.diastolic} mmHg" else "Off" }

private fun unitsSummary(settings: WatchSettings?): String? = settings?.displayUnits?.let {
    "${if (it.imperialLength) "Imperial" else "Metric"}, ${if (it.use24HourClock) "24 h" else "12 h"}"
}

private val previewCapabilities = WatchCapabilities(
    heartRate = true, spo2 = true, bloodPressure = true, temperature = false, stress = false,
    sport = true, gps = false, advancedReminders = false, weather = true, contactsLimit = 10,
    doNotDisturb = true, heartRateAlert = true, timeFormat = true,
    sedentaryIntervalConfigurable = true, monitorIntervalConfigurable = true, alarmLimit = 5,
    firmwareVersion = "00000105",
)

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun WatchOverviewReadyPreview() {
    WatchTheme {
        PremiumBackground {
            WatchOverviewScreen(
                state = WatchSettingsUiState(
                    watchState = WatchState.Ready(battery = 72),
                    capabilities = previewCapabilities,
                    settings = WatchSettings(
                        alarms = emptyList(),
                        contacts = emptyList(),
                        wristRaise = WristRaise(enabled = true, window = MinuteWindow(420, 1320)),
                        doNotDisturb = DoNotDisturb(allDay = false, scheduled = true, window = MinuteWindow(1320, 420)),
                    ),
                ),
                onOpen = {},
                onFindWatch = {},
                onOpenDiagnostics = {},
                presence = PresenceUiState(available = true, associated = true),
                notificationsValue = "On, 9 apps",
                status = WatchStatusUiState(idSuffix = "7A0D"),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun WatchOverviewOfflinePreview() {
    WatchTheme {
        PremiumBackground {
            WatchOverviewScreen(
                state = WatchSettingsUiState(watchState = WatchState.Waiting(nextRetryAt = null), capabilities = previewCapabilities),
                onOpen = {},
                onFindWatch = {},
                onOpenDiagnostics = {},
                notificationsValue = "On, 9 apps",
                status = WatchStatusUiState(idSuffix = "7A0D", lastConnectedMs = 1_791_000_000_000),
            )
        }
    }
}
