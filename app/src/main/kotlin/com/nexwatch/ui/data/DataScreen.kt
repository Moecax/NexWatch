package com.nexwatch.ui.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.data.backup.BackupSettings
import com.nexwatch.core.data.storage.StorageSummary
import com.nexwatch.core.data.storage.StoredDataType
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.ListRow
import com.nexwatch.core.designsystem.component.MetricText
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.RowGroup
import com.nexwatch.core.designsystem.component.ScreenTitle
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.SectionLabel
import com.nexwatch.core.designsystem.component.WatchSwitch
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sinceFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
private val lastRunFormat = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/** Export tables in the order JsonlZipExporter writes them, with the names people know them by. */
private val EXPORT_TABLES = listOf(
    "steps" to "Activity",
    "heart_rate" to "Heart rate",
    "spo2" to "SpO2",
    "blood_pressure" to "Blood pressure",
    "sleep_session" to "Sleep",
    "workout" to "Workouts",
)

@Composable
fun DataRoute(viewModel: DataViewModel = hiltViewModel()) {
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val backup by viewModel.backupSettings.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackupMs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resolver = context.contentResolver
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.export(resolver, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(resolver, it) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            resolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.setAutoBackupFolder(context, it)
        }
    }

    DataScreen(
        storage = storage,
        transfer = transfer,
        backup = backup,
        lastBackupMs = lastBackup,
        onExport = { exportLauncher.launch("nexwatch-export-${LocalDate.now()}.zip") },
        onImport = { importLauncher.launch(arrayOf("application/zip")) },
        onShare = { share(context, it) },
        onDismissTransfer = viewModel::dismiss,
        onAutoBackupChange = { enabled ->
            if (enabled && backup.folderUri == null) folderLauncher.launch(null) else viewModel.setAutoBackupEnabled(context, enabled)
        },
        onChangeFolder = { folderLauncher.launch(null) },
    )
}

private fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/zip")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share export"))
}

@Composable
fun DataScreen(
    storage: StorageSummary?,
    transfer: TransferState,
    backup: BackupSettings,
    lastBackupMs: Long?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShare: (Uri) -> Unit,
    onDismissTransfer: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onChangeFolder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntranceItem(0) { ScreenTitle("Data", subtitle = "Everything stays on this phone until you export it.") }
        EntranceItem(1) { StorageCard(storage) }
        EntranceItem(2) { SectionLabel("Backup", Modifier.padding(top = 10.dp)) }
        EntranceItem(3) { BackupCard(storage, transfer, onExport, onImport, onShare, onDismissTransfer) }
        EntranceItem(4) { AutoBackupCard(backup, lastBackupMs, onAutoBackupChange, onChangeFolder) }
        EntranceItem(5) { SectionLabel("Connected services", Modifier.padding(top = 10.dp)) }
        EntranceItem(6) { ConnectedServicesSection() }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StoredDataType.color(): Color = when (this) {
    StoredDataType.HEART_RATE -> WatchTheme.colors.heartRate
    StoredDataType.ACTIVITY -> WatchTheme.colors.activity
    StoredDataType.SPO2 -> WatchTheme.colors.spo2
    StoredDataType.SLEEP -> WatchTheme.colors.sleep
    StoredDataType.BLOOD_PRESSURE -> WatchTheme.colors.bloodPressure
    StoredDataType.WORKOUTS -> WatchTheme.colors.calories
    StoredDataType.TEMPERATURE, StoredDataType.STRESS -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val StoredDataType.label: String
    get() = when (this) {
        StoredDataType.HEART_RATE -> "Heart rate"
        StoredDataType.ACTIVITY -> "Activity"
        StoredDataType.SPO2 -> "SpO2"
        StoredDataType.SLEEP -> "Sleep nights"
        StoredDataType.BLOOD_PRESSURE -> "Blood pressure"
        StoredDataType.WORKOUTS -> "Workouts"
        StoredDataType.TEMPERATURE -> "Temperature"
        StoredDataType.STRESS -> "Stress"
    }

@Composable
private fun StorageCard(storage: StorageSummary?) {
    NexCard {
        MetricText(storage?.totalRecords?.let { "%,d".format(Locale.UK, it) } ?: "--", "records", valueSize = 38.sp)
        val since = storage?.earliestMs?.let { sinceFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
        Text(
            listOfNotNull(storage?.let { megabytes(it.databaseBytes) }, since?.let { "collected since $it" }).joinToString(", ").ifEmpty { "Nothing stored yet" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        val counts = storage?.counts.orEmpty()
        if (counts.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 16.dp).fillMaxWidth().height(12.dp)) {
                counts.forEach { (type, count) ->
                    // A floor keeps a 167-row type visible next to a 30,000-row one.
                    Box(Modifier.weight(maxOf(count.toFloat(), storage!!.totalRecords / 80f)).height(12.dp).background(type.color(), RoundedCornerShape(4.dp)))
                }
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                counts.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEach { (type, count) -> LegendCell(type.color(), type.label, count, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCell(color: Color, label: String, count: Int, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).weight(1f))
        Text("%,d".format(Locale.UK, count), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun BackupCard(
    storage: StorageSummary?,
    transfer: TransferState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onShare: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = transfer is TransferState.Exporting || transfer is TransferState.Importing
    NexCard(modifier = Modifier.animateContentSize(tween(260, easing = WatchMotion.easeOutStrong))) {
        Text("Full backup", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Text(
            "One ZIP with every record, readable as JSON lines and CSV. Restoring it skips anything already here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
            PrimaryButton(
                text = when (transfer) {
                    is TransferState.Exporting -> "Exporting…"
                    is TransferState.Exported -> "Export again"
                    else -> "Export"
                },
                onClick = onExport,
                enabled = !busy,
                icon = Icons.Rounded.Download,
                modifier = Modifier.weight(1.25f),
            )
            SecondaryButton(
                if (transfer is TransferState.Importing) "Restoring…" else "Restore",
                onClick = onImport,
                enabled = !busy,
                icon = Icons.Rounded.UploadFile,
                modifier = Modifier.weight(1f),
            )
        }
        when (transfer) {
            is TransferState.Exporting -> ExportProgress(transfer.written, storage)
            is TransferState.Exported -> ResultRow(
                icon = Icons.Rounded.CheckCircle,
                tint = WatchTheme.colors.success,
                title = transfer.fileName ?: "Export saved",
                detail = listOfNotNull(transfer.sizeBytes?.let(::megabytes), "%,d records".format(Locale.UK, transfer.records)).joinToString(", "),
                action = { IconButton(onClick = { onShare(transfer.uri) }) { Icon(Icons.Rounded.Share, contentDescription = "Share export") } },
            )
            is TransferState.Imported -> ResultRow(
                icon = Icons.Rounded.CheckCircle,
                tint = WatchTheme.colors.success,
                title = "Restore finished",
                detail = "%,d records read. Anything already stored was skipped.".format(Locale.UK, transfer.records),
                action = { TextButton(onClick = onDismiss) { Text("Done") } },
            )
            is TransferState.Failed -> ResultRow(
                icon = Icons.Rounded.Error,
                tint = WatchTheme.colors.error,
                title = "That didn't work",
                detail = transfer.message,
                action = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
            )
            TransferState.Importing, TransferState.Idle -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 14.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text("Exports contain your health data. Keep them somewhere private.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExportProgress(written: Map<String, Int>, storage: StorageSummary?) {
    val present = storage?.counts?.keys.orEmpty()
    val tables = EXPORT_TABLES.filter { (key, _) -> key in written || storage == null || present.any { it.exportKey() == key } }
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tables.forEach { (key, label) ->
            val done = written[key]
            val fill by animateFloatAsState(if (done != null) 1f else 0f, tween(420, easing = WatchMotion.easeOutStrong), label = "exportLine")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp))
                Box(Modifier.weight(1f).height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.pill)) {
                    Box(
                        Modifier.fillMaxWidth().height(5.dp)
                            .graphicsLayer {
                                scaleX = fill
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .background(MaterialTheme.colorScheme.primary, WatchShapes.pill),
                    )
                }
                Text(
                    done?.let { "%,d".format(Locale.UK, it) } ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.width(58.dp),
                )
            }
        }
    }
}

private fun StoredDataType.exportKey(): String = when (this) {
    StoredDataType.HEART_RATE -> "heart_rate"
    StoredDataType.ACTIVITY -> "steps"
    StoredDataType.SPO2 -> "spo2"
    StoredDataType.SLEEP -> "sleep_session"
    StoredDataType.BLOOD_PRESSURE -> "blood_pressure"
    StoredDataType.WORKOUTS -> "workout"
    StoredDataType.TEMPERATURE -> "temperature"
    StoredDataType.STRESS -> "stress"
}

@Composable
private fun ResultRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, detail: String, action: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        IconBadge(icon, tint = tint, background = tint.copy(alpha = 0.14f))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
private fun AutoBackupCard(backup: BackupSettings, lastBackupMs: Long?, onChange: (Boolean) -> Unit, onChangeFolder: () -> Unit) {
    RowGroup(
        rows = buildList<@Composable () -> Unit> {
            add {
                ListRow(
                    Icons.Rounded.Backup,
                    "Auto-backup",
                    description = "Weekly, only while charging",
                    trailing = { WatchSwitch(checked = backup.enabled, onCheckedChange = onChange) },
                )
            }
            backup.folderUri?.let { folder ->
                add {
                    Column(Modifier.padding(start = 66.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                        KeyValue("Folder", folderName(folder), onClick = onChangeFolder)
                        KeyValue("Keeps", "Last ${backup.keepCount} backups")
                        KeyValue("Last run", lastBackupMs?.let { lastRunFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "Not yet")
                    }
                }
            }
        },
    )
}

@Composable
private fun KeyValue(key: String, value: String, onClick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (onClick != null) {
            TextButton(onClick = onClick) { Text(value, fontWeight = FontWeight.SemiBold) }
        } else {
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** "primary:Documents/NexWatch" from a SAF tree URI becomes "Documents/NexWatch". */
private fun folderName(treeUri: String): String = runCatching {
    DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)).substringAfter(':').ifEmpty { "Phone storage" }
}.getOrDefault("Chosen folder")

internal fun megabytes(bytes: Long): String = "%.1f MB".format(Locale.UK, bytes / 1_048_576f)
