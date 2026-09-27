package com.nexwatch.core.export

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexwatch.core.data.backup.BackupPrefs
import com.nexwatch.core.data.backup.BackupSettings
import com.nexwatch.core.data.export.ExportRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val PERIODIC_WORK_NAME = "backup-periodic"

/** Extracted so its policy (skip when disabled, write, prune) is testable without a CoroutineWorker instance. */
suspend fun runBackup(
    settings: BackupSettings,
    destination: BackupDestination,
    exporter: JsonlZipExporter,
    repository: ExportRepository,
): Boolean {
    if (!settings.enabled || !destination.isConfigured()) return true // nothing to do is still success

    val fileName = "nexwatch-backup-${LocalDate.now()}.zip"
    val out = destination.createFile(fileName) ?: return false
    val history = out.use { exporter.export(it) }
    repository.recordExport(history.copy(uri = fileName))

    destination.listExistingBackups().sortedByDescending { it.createdAtMs }.drop(settings.keepCount)
        .forEach { destination.delete(it) }
    return true
}

/** §6.4/§8.7: weekly, charging + battery not low. A no-op (not a failure) when the user hasn't opted in. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: BackupPrefs,
    private val destination: BackupDestination,
    private val exporter: JsonlZipExporter,
    private val repository: ExportRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        if (runBackup(prefs.settings.first(), destination, exporter, repository)) Result.success() else Result.retry()

    companion object {
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).setConstraints(constraints).build(),
            )
        }
    }
}
