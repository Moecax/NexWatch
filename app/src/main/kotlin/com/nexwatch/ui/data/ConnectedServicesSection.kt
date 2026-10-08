package com.nexwatch.ui.data

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.BuildConfig
import com.nexwatch.core.data.syncengine.SyncProviderStatus
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.sync.healthconnect.HealthConnectAudit
import com.nexwatch.core.sync.healthconnect.HealthConnectPermissions
import com.nexwatch.core.sync.healthconnect.HealthConnectSyncProvider
import com.nexwatch.core.syncapi.Readiness
import com.nexwatch.core.syncapi.RecordType
import java.text.NumberFormat

/** design-prompt Batch 7 §6: one card per registered provider, then a placeholder for future ones. */
@Composable
fun ConnectedServicesSection(viewModel: SyncServicesViewModel = hiltViewModel()) {
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshReadiness() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connected services", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        statuses.forEach { status ->
            if (status.id == HealthConnectSyncProvider.PROVIDER_ID) {
                HealthConnectCard(status, readiness[status.id], viewModel)
            }
        }
        AddServicePlaceholder()
    }
}

@Composable
private fun HealthConnectCard(status: SyncProviderStatus, readiness: Readiness?, viewModel: SyncServicesViewModel) {
    val context = LocalContext.current
    // Connecting is provider-specific UI: Health Connect grants through its own system sheet, so this card,
    // not the generic engine, owns the launcher.
    val connectLauncher = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) { granted ->
        if (granted.containsAll(HealthConnectPermissions.required)) {
            viewModel.setEnabled(status.id, true)
        } else {
            viewModel.refreshReadiness()
            Toast.makeText(context, "Health Connect needs every permission to stay in sync", Toast.LENGTH_LONG).show()
        }
    }
    val connect: () -> Unit = {
        when (readiness) {
            is Readiness.Unavailable -> Toast.makeText(context, readiness.reason, Toast.LENGTH_LONG).show()
            Readiness.Ready -> viewModel.setEnabled(status.id, true)
            else -> connectLauncher.launch(HealthConnectPermissions.required)
        }
    }

    Surface(shape = WatchShapes.card, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = status.enabled,
                    role = Role.Switch,
                    onValueChange = { on -> if (on) connect() else viewModel.setEnabled(status.id, false) },
                ),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(status.displayName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        statusLine(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = status.enabled, onCheckedChange = null)
            }

            status.backfill?.takeIf { status.enabled && it.total > 0 }?.let { backfill ->
                LinearProgressIndicator(
                    progress = { backfill.pushed.toFloat() / backfill.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val problem = problemFor(status, readiness)
            if (problem != null) {
                StatusChip(problem, StatusTone.ERROR)
                TextButton(onClick = connect) { Text("Reconnect") }
            } else if (status.enabled && status.backfill == null && status.lastSuccessAt != null) {
                StatusChip("Up to date", StatusTone.SUCCESS)
            }

            Text(
                "Shares ${status.supportedTypes.sortedBy { it.ordinal }.joinToString { it.label }}. Data already sent " +
                    "stays in Health Connect if you turn this off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (status.enabled) {
                SecondaryButton("Sync now", onClick = { viewModel.syncNow(status.id) })
            }
            if (BuildConfig.DEBUG) {
                HealthConnectDebugTools(status.id, viewModel)
            }
        }
    }
}

@Composable
private fun HealthConnectDebugTools(providerId: String, viewModel: SyncServicesViewModel) {
    val auditState by viewModel.auditState.collectAsStateWithLifecycle()
    val readAccessLauncher = rememberLauncherForActivityResult(HealthConnectPermissions.requestContract()) {
        viewModel.runAudit()
    }
    Text("Debug", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SecondaryButton("Audit Health Connect", onClick = { readAccessLauncher.launch(HealthConnectAudit.readPermissions) })
    SecondaryButton("Re-send everything", onClick = { viewModel.resendAll(providerId) })
    when (val state = auditState) {
        AuditState.Idle -> Unit
        AuditState.Running -> Text("Reading Health Connect…", color = MaterialTheme.colorScheme.onSurface)
        is AuditState.Failed -> Text("Audit failed: ${state.message}", color = MaterialTheme.colorScheme.onSurface)
        is AuditState.Done -> Column {
            state.rows.forEach { row ->
                val duplicates = row.total - row.distinctClientIds
                Text(
                    "${row.type}: ${row.total}" + if (duplicates > 0) " ($duplicates duplicates)" else " (no duplicates)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AddServicePlaceholder() {
    val border = WatchTheme.colors.border
    Surface(
        shape = WatchShapes.card,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        modifier = Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(
                color = border,
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                cornerRadius = CornerRadius(16.dp.toPx()),
            )
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Add service", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "More services coming",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLine(status: SyncProviderStatus): String {
    val backfill = status.backfill
    val number = NumberFormat.getIntegerInstance()
    return when {
        !status.enabled -> "Off"
        backfill != null -> "Sending history… ${number.format(backfill.pushed)} of ${number.format(backfill.total)} records"
        status.lastSuccessAt != null -> "Up to date · " + DateUtils.getRelativeTimeSpanString(
            status.lastSuccessAt!!, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        )
        else -> "Starting…"
    }
}

/** A stopped provider (Fatal) is off but keeps its reason; an enabled one can still lose its permissions. */
private fun problemFor(status: SyncProviderStatus, readiness: Readiness?): String? = when {
    !status.enabled && status.lastError != null -> status.lastError
    status.enabled && readiness is Readiness.NeedsPermission -> "Permission revoked"
    status.enabled && readiness is Readiness.Unavailable -> readiness.reason
    else -> null
}

private val RecordType.label: String
    get() = when (this) {
        RecordType.STEPS -> "steps, distance and calories"
        RecordType.HEART_RATE -> "heart rate"
        RecordType.SPO2 -> "SpO2"
        RecordType.BLOOD_PRESSURE -> "blood pressure"
        RecordType.TEMPERATURE -> "temperature"
        RecordType.STRESS -> "stress"
        RecordType.SLEEP_SESSION -> "sleep"
        RecordType.WORKOUT -> "workouts"
    }
