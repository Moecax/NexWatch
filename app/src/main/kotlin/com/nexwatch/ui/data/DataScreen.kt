package com.nexwatch.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.data.backup.BackupSettings

@Composable
fun DataRoute(viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupSettings by viewModel.backupPrefs.settings.collectAsStateWithLifecycle(
        initialValue = BackupSettings(enabled = false, folderUri = null, keepCount = 5),
    )
    val context = LocalContext.current
    val resolver = context.contentResolver

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.export(resolver, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(resolver, it) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setAutoBackupFolder(it)
            viewModel.scheduleAutoBackup(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Data", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = { exportLauncher.launch("nexwatch-export-${java.time.LocalDate.now()}.zip") }) {
            Text("Export data")
        }
        Button(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
            Text("Import data")
        }

        Column {
            Text("Auto-backup", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = backupSettings.enabled,
                onCheckedChange = { enabled ->
                    if (enabled && backupSettings.folderUri == null) {
                        folderLauncher.launch(null)
                    } else {
                        viewModel.setAutoBackupEnabled(enabled)
                    }
                },
            )
        }

        when (val current = state) {
            DataUiState.Idle -> Unit
            DataUiState.Working -> CircularProgressIndicator()
            is DataUiState.ExportSuccess -> Text("Exported ${current.recordCounts.values.sum()} records")
            is DataUiState.ImportSuccess -> Text("Imported ${current.recordCounts.values.sum()} records")
            is DataUiState.Failed -> Text("Failed: ${current.message}", color = MaterialTheme.colorScheme.error)
        }

        ConnectedServicesSection()
    }
}
