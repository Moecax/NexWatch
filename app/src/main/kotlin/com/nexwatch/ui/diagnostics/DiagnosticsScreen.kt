package com.nexwatch.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.data.diagnostics.DiagnosticEvent
import com.nexwatch.core.data.diagnostics.DiagnosticEventKind
import com.nexwatch.core.data.diagnostics.DiagnosticsStats
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.RowGroup
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.SectionLabel
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.component.WatchGlyph
import com.nexwatch.core.designsystem.component.WatchSwitch
import com.nexwatch.core.designsystem.component.metricStyle
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.WatchState
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Log lines and timeline timestamps are real data, so they get the platform monospace —
 * not as costume (Part C), but because columns of times must align (Part A, tabular).
 * Color comes from LocalContentColor; nothing here is a composable context.
 */
private val LogTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 16.sp,
)

private val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun clockOf(atMs: Long): String =
    Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).format(CLOCK_TIME)

/**
 * design-prompt.md Batch 8 #5 — Diagnostics: the watch's connection told as a timeline,
 * KPIs and a log you can export, replacing the raw-text debug dump. The Phase-4 dev
 * controls (fake/real toggle, forceable states) survive in a debug-only section at the
 * bottom so §4 keeps its verification surface without sitting in the user's way.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val useRealWatch by viewModel.useRealWatch.collectAsStateWithLifecycle()
    val backgroundTest by viewModel.backgroundTest.collectAsStateWithLifecycle()
    val exportedLog by viewModel.exportedLog.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportLogs(uri)
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    // Derived once per composition from the persisted log — never on a ticker (CLAUDE.md §9).
    val uptimeMs = DiagnosticsStats.uptimeOn(events, today, zone, System.currentTimeMillis())
    val reconnects = DiagnosticsStats.reconnectsOn(events, today, zone)
    val forwarded = diagnostics.forwardedOn(today)
    val battery = (state as? WatchState.Ready)?.battery
    val addressSuffix = identity?.boundAddress?.takeLast(4)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        EntranceItem(index = 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    "Diagnostics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        EntranceItem(index = 1) {
            NexCard(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    WatchGlyph(size = 56.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Your watch",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            addressSuffix?.let { "ID ends $it" } ?: "Not paired yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    ConnectionChip(state)
                }
                HorizontalDivider(
                    color = WatchTheme.colors.border.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Battery",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    WatchShapes.pill,
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((battery ?: 0) / 100f)
                                    .height(6.dp)
                                    // Same thresholds and colours as the Watch tab's battery bar.
                                    .background(
                                        if ((battery ?: 100) <= 20) WatchTheme.colors.warning else WatchTheme.colors.success,
                                        WatchShapes.pill,
                                    ),
                            )
                        }
                    }
                    Text(
                        battery?.let { "$it%" } ?: "--",
                        style = metricStyle(20.sp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                HorizontalDivider(
                    color = WatchTheme.colors.border.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Firmware",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        capabilities?.firmwareVersion ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        EntranceItem(index = 2) {
            NexCard(
                modifier = Modifier.padding(top = 4.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    KpiCell("Uptime today", formatUptime(uptimeMs), Modifier.weight(1f))
                    GridHairline()
                    KpiCell("Reconnects today", reconnects.toString(), Modifier.weight(1f))
                }
                HorizontalDivider(color = WatchTheme.colors.border.copy(alpha = 0.5f))
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    KpiCell("Forwarded today", forwarded.toString(), Modifier.weight(1f))
                    GridHairline()
                    KpiCell("Records on phone", formatRecords(storage?.totalRecords), Modifier.weight(1f))
                }
                HorizontalDivider(color = WatchTheme.colors.border.copy(alpha = 0.5f))
                SyncOutcomeRow(
                    "Last sync attempt",
                    diagnostics.lastSyncAttemptAt?.let { clockOf(it) } ?: "--",
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = WatchTheme.colors.border.copy(alpha = 0.5f))
                SyncOutcomeRow(
                    "Last sync error",
                    diagnostics.lastSyncError ?: "--",
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        EntranceItem(index = 3) {
            SectionLabel("Connection timeline", Modifier.padding(top = 20.dp))
        }
        EntranceItem(index = 4) {
            if (events.isEmpty()) {
                NexCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp),
                        )
                        Text(
                            "No connection events yet",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            "The timeline fills in the first time the watch connects.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            } else {
                val recent = events.reversed().take(8)
                RowGroup(
                    modifier = Modifier.padding(top = 8.dp),
                    rows = recent.map { event -> { TimelineRow(event) } },
                )
            }
        }

        EntranceItem(index = 5) {
            SectionLabel("Logs", Modifier.padding(top = 20.dp))
        }
        EntranceItem(index = 6) {
            NexCard(modifier = Modifier.padding(top = 8.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                ) {
                    val preview = events.takeLast(5)
                    if (preview.isEmpty()) {
                        Text("No log lines yet.", style = LogTextStyle)
                    } else {
                        preview.forEach { event ->
                            Text(logLine(event), style = LogTextStyle)
                        }
                    }
                }
                Spacer(Modifier.padding(top = 14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // The button stays, relabelled, after a result: a test is worth re-running.
                    SecondaryButton(
                        text = when (backgroundTest) {
                            BackgroundTestResult.Idle -> "Run background test"
                            BackgroundTestResult.Running -> "Testing…"
                            BackgroundTestResult.Passed, is BackgroundTestResult.Failed -> "Test again"
                        },
                        onClick = viewModel::runBackgroundTest,
                        enabled = backgroundTest != BackgroundTestResult.Running,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Export logs",
                        onClick = { exportLauncher.launch("nexwatch-diagnostics.log") },
                        modifier = Modifier.weight(1f),
                    )
                }
                when (val result = backgroundTest) {
                    BackgroundTestResult.Passed -> StatusChip(
                        "Test passed",
                        StatusTone.SUCCESS,
                        icon = Icons.Filled.CheckCircle,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    is BackgroundTestResult.Failed -> Column(modifier = Modifier.padding(top = 10.dp)) {
                        StatusChip("Test failed", StatusTone.ERROR, icon = Icons.Filled.Error)
                        Text(
                            result.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    BackgroundTestResult.Idle, BackgroundTestResult.Running -> Unit
                }
                Text(
                    // Connection times are behavioural data, so the claim stays this narrow.
                    "Stored on this phone. No health data or message content is included.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                exportedLog?.let { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatBytes(file.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { shareLog(context, file.uri) }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Share log",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.showImplToggle) {
            EntranceItem(index = 7) {
                SectionLabel("Developer", Modifier.padding(top = 20.dp))
            }
            EntranceItem(index = 8) {
                NexCard(modifier = Modifier.padding(top = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (useRealWatch) {
                                "Real watch (restart the app to apply)"
                            } else {
                                "Fake watch (restart the app to apply)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        WatchSwitch(checked = useRealWatch, onCheckedChange = viewModel::setUseRealWatch)
                    }
                    HorizontalDivider(
                        color = WatchTheme.colors.border.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    DeveloperStateBlock(
                        state = state,
                        capabilitiesLine = capabilities?.let { "Firmware ${it.firmwareVersion} · $it" },
                        fakeInUse = viewModel.fakeInUse,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionChip(state: WatchState) = when (state) {
    is WatchState.Ready -> StatusChip("Connected", StatusTone.SUCCESS, live = true)
    WatchState.Unbound -> StatusChip("Not paired", StatusTone.NEUTRAL)
    WatchState.BluetoothOff -> StatusChip("Bluetooth off", StatusTone.WARNING)
    is WatchState.Waiting -> StatusChip("Reconnecting", StatusTone.INFO)
    WatchState.Connecting -> StatusChip("Connecting", StatusTone.INFO)
    is WatchState.AuthFailed -> StatusChip("Failed", StatusTone.ERROR)
}

@Composable
private fun KpiCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = metricStyle(26.sp),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Vertical hairline between KPI cells; fills the row's intrinsic height. */
@Composable
private fun GridHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(WatchTheme.colors.border.copy(alpha = 0.5f)),
    )
}

/** A plain label/value line, for facts too wordy to sit in a KPI cell's metric type. */
@Composable
private fun SyncOutcomeRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimelineRow(event: DiagnosticEvent) {
    val colors = when (event.kind) {
        DiagnosticEventKind.CONNECTED ->
            WatchTheme.colors.success to WatchTheme.colors.success.copy(alpha = 0.14f)
        DiagnosticEventKind.RECONNECTED ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        DiagnosticEventKind.DISCONNECTED ->
            WatchTheme.colors.error to WatchTheme.colors.error.copy(alpha = 0.12f)
        DiagnosticEventKind.SYNC_COMPLETED ->
            MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            clockOf(event.atMs),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        IconBadge(
            icon = event.kind.icon(),
            tint = colors.first,
            background = colors.second,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.kind.title(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            event.kind.subtitle(event)?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun DiagnosticEventKind.icon(): ImageVector = when (this) {
    DiagnosticEventKind.CONNECTED -> Icons.Filled.Bluetooth
    DiagnosticEventKind.RECONNECTED -> Icons.Filled.BluetoothSearching
    DiagnosticEventKind.DISCONNECTED -> Icons.Filled.BluetoothDisabled
    DiagnosticEventKind.SYNC_COMPLETED -> Icons.Filled.Sync
}

private fun DiagnosticEventKind.title(): String = when (this) {
    DiagnosticEventKind.CONNECTED -> "Connected"
    DiagnosticEventKind.RECONNECTED -> "Reconnected"
    DiagnosticEventKind.DISCONNECTED -> "Disconnected"
    DiagnosticEventKind.SYNC_COMPLETED -> "Sync completed"
}

private fun DiagnosticEventKind.subtitle(event: DiagnosticEvent): String? = when (this) {
    DiagnosticEventKind.CONNECTED -> "session started"
    DiagnosticEventKind.RECONNECTED -> "back in range"
    DiagnosticEventKind.DISCONNECTED -> event.detail ?: "out of range"
    DiagnosticEventKind.SYNC_COMPLETED -> event.detail ?: "watch data"
}

/** The §4.5 verification surface: raw state and capability dump, debug builds only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperStateBlock(state: WatchState, capabilitiesLine: String?, fakeInUse: Boolean, viewModel: DiagnosticsViewModel) {
    Text(
        "State: $state",
        style = LogTextStyle,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        capabilitiesLine ?: "Capabilities: not read yet",
        style = LogTextStyle,
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        viewModel.forceableStates.forEach { (label, target) ->
            OutlinedButton(
                onClick = { viewModel.forceState(target) },
                shape = WatchShapes.pill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    // The fake client is the only one with these hooks, so offering them against a real watch
    // would set a flag nothing reads.
    if (fakeInUse) {
        Text(
            "The next sync will…",
            style = LogTextStyle,
            modifier = Modifier.padding(top = 14.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::armNextSyncToHang,
                shape = WatchShapes.pill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("hang forever", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = viewModel::armNextSyncToFail,
                shape = WatchShapes.pill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("fail immediately", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatUptime(ms: Long): String = when {
    ms <= 0L -> "--"
    else -> {
        val hours = ms / 3_600_000L
        val minutes = (ms % 3_600_000L) / 60_000L
        if (hours > 0) "$hours h $minutes m" else "$minutes m"
    }
}

private fun formatRecords(count: Int?): String =
    count?.let { NumberFormat.getIntegerInstance().format(it) } ?: "--"

private fun formatBytes(bytes: Long): String =
    if (bytes < 1024L) "$bytes B" else String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)

private fun shareLog(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share log"))
}
