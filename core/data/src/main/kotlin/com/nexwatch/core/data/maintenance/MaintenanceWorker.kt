package com.nexwatch.core.data.maintenance

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexwatch.core.data.sync.JOURNAL_RETENTION_MS
import com.nexwatch.core.data.syncengine.ChangeLogCompactor
import com.nexwatch.core.database.RawIngestDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "maintenance-periodic"

/** §8.7: daily, idle and charging. Prunes the journal and compacts the change log (§7.4). */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val rawIngestDao: RawIngestDao,
    private val compactor: ChangeLogCompactor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        rawIngestDao.pruneProcessedBefore(System.currentTimeMillis() - JOURNAL_RETENTION_MS)
        compactor.compact()
        return Result.success()
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(true)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build(),
            )
        }
    }
}
