package com.nexwatch.ui.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.backup.BackupPrefs
import com.nexwatch.core.data.backup.BackupSettings
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.data.storage.StorageRepository
import com.nexwatch.core.data.storage.StorageSummary
import com.nexwatch.core.data.syncengine.SyncRepository
import com.nexwatch.core.export.BackupWorker
import com.nexwatch.core.export.JsonlZipExporter
import com.nexwatch.core.export.ZipImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backup files are named by BackupWorker's runBackup; manual exports get whatever name the user picked. */
private const val AUTO_BACKUP_PREFIX = "nexwatch-backup-"

sealed interface TransferState {
    data object Idle : TransferState

    /** [written] maps each finished table to its record count, in the order they were written. */
    data class Exporting(val written: Map<String, Int>) : TransferState
    data class Exported(val uri: Uri, val fileName: String?, val sizeBytes: Long?, val records: Int) : TransferState
    data object Importing : TransferState
    data class Imported(val records: Int) : TransferState
    data class Failed(val message: String) : TransferState
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val exporter: JsonlZipExporter,
    private val importer: ZipImporter,
    private val exportRepository: ExportRepository,
    private val backupPrefs: BackupPrefs,
    private val syncRepository: SyncRepository,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val _transfer = MutableStateFlow<TransferState>(TransferState.Idle)
    val transfer: StateFlow<TransferState> = _transfer.asStateFlow()

    private val _storage = MutableStateFlow<StorageSummary?>(null)
    val storage: StateFlow<StorageSummary?> = _storage.asStateFlow()

    private val _lastBackupMs = MutableStateFlow<Long?>(null)
    val lastBackupMs: StateFlow<Long?> = _lastBackupMs.asStateFlow()

    val backupSettings: StateFlow<BackupSettings> = backupPrefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupSettings(enabled = false, folderUri = null, keepCount = 5))

    /** Called on resume and after every export or import; storage is read on demand, never polled. */
    fun refresh() = viewModelScope.launch {
        _storage.value = storageRepository.summary()
        _lastBackupMs.value = exportRepository.recentExports().firstOrNull { it.uri.startsWith(AUTO_BACKUP_PREFIX) }?.atMs
    }

    fun export(contentResolver: ContentResolver, target: Uri) {
        if (_transfer.value is TransferState.Exporting) return
        _transfer.value = TransferState.Exporting(emptyMap())
        viewModelScope.launch {
            runCatching {
                val out = contentResolver.openOutputStream(target) ?: error("Could not open the file for writing")
                val history = out.use { stream ->
                    exporter.export(stream) { table, count ->
                        _transfer.update { (it as? TransferState.Exporting)?.copy(written = it.written + (table to count)) ?: it }
                    }
                }
                exportRepository.recordExport(history.copy(uri = target.toString()))
                val (name, size) = contentResolver.describe(target)
                TransferState.Exported(target, name, size, history.recordCounts.filterKeys { it != "daily_summary" }.values.sum())
            }.onSuccess { _transfer.value = it }
                .onFailure { _transfer.value = TransferState.Failed(it.message ?: "Export failed") }
            refresh()
        }
    }

    fun import(contentResolver: ContentResolver, source: Uri) {
        _transfer.value = TransferState.Importing
        viewModelScope.launch {
            runCatching {
                val input = contentResolver.openInputStream(source) ?: error("Could not open the file for reading")
                input.use { importer.import(it) }
            }.onSuccess {
                // Imported rows land in the change log like synced ones; push them now, not at the next ingest.
                syncRepository.syncEnabledNow()
                _transfer.value = TransferState.Imported(it.recordCounts.filterKeys { k -> k != "daily_summary" }.values.sum())
            }.onFailure { _transfer.value = TransferState.Failed(it.message ?: "Import failed") }
            refresh()
        }
    }

    /** Turning it back on reuses the folder already granted, so the picker only shows the first time. */
    fun setAutoBackupEnabled(context: Context, enabled: Boolean) = viewModelScope.launch {
        backupPrefs.setEnabled(enabled)
        if (enabled) BackupWorker.schedulePeriodic(context)
    }

    fun setAutoBackupFolder(context: Context, uri: Uri) = viewModelScope.launch {
        backupPrefs.setFolder(uri.toString())
        backupPrefs.setEnabled(true)
        BackupWorker.schedulePeriodic(context)
    }

    fun dismiss() {
        _transfer.value = TransferState.Idle
    }

    private fun ContentResolver.describe(uri: Uri): Pair<String?, Long?> = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null to null
            val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
            val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
            name to size
        } ?: (null to null)
    }.getOrDefault(null to null)
}
