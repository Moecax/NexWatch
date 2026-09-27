package com.nexwatch.ui.data

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.backup.BackupPrefs
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.export.BackupWorker
import com.nexwatch.core.export.JsonlZipExporter
import com.nexwatch.core.export.ZipImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DataUiState {
    data object Idle : DataUiState
    data object Working : DataUiState
    data class ExportSuccess(val recordCounts: Map<String, Int>) : DataUiState
    data class ImportSuccess(val recordCounts: Map<String, Int>) : DataUiState
    data class Failed(val message: String) : DataUiState
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val exporter: JsonlZipExporter,
    private val importer: ZipImporter,
    private val exportRepository: ExportRepository,
    val backupPrefs: BackupPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow<DataUiState>(DataUiState.Idle)
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun export(contentResolver: ContentResolver, target: Uri) {
        _state.value = DataUiState.Working
        viewModelScope.launch {
            runCatching {
                val out = contentResolver.openOutputStream(target) ?: error("Could not open $target for writing")
                val history = out.use { exporter.export(it) }
                exportRepository.recordExport(history.copy(uri = target.toString()))
                history
            }.onSuccess { _state.value = DataUiState.ExportSuccess(it.recordCounts) }
                .onFailure { _state.value = DataUiState.Failed(it.message ?: "Export failed") }
        }
    }

    fun import(contentResolver: ContentResolver, source: Uri) {
        _state.value = DataUiState.Working
        viewModelScope.launch {
            runCatching {
                val input = contentResolver.openInputStream(source) ?: error("Could not open $source for reading")
                input.use { importer.import(it) }
            }.onSuccess { _state.value = DataUiState.ImportSuccess(it.recordCounts) }
                .onFailure { _state.value = DataUiState.Failed(it.message ?: "Import failed") }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch { backupPrefs.setEnabled(enabled) }

    fun setAutoBackupFolder(uri: Uri) = viewModelScope.launch {
        backupPrefs.setFolder(uri.toString())
        backupPrefs.setEnabled(true)
    }

    fun scheduleAutoBackup(context: android.content.Context) = BackupWorker.schedulePeriodic(context)

    fun dismiss() { _state.value = DataUiState.Idle }
}
