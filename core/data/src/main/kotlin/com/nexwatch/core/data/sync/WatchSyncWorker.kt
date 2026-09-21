package com.nexwatch.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "watch-sync-periodic"
private const val CATCH_UP_WORK_NAME = "watch-sync-catch-up"

/**
 * §8.7's safety net — WorkManager-scheduled per CLAUDE.md I5's explicit carve-out, not a
 * connection poll. Runs the same HealthSyncCoordinator the Ready-trigger uses (Task 19).
 */
@HiltWorker
class WatchSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: HealthSyncCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = coordinator.syncAndNormalize()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        fun enqueueCatchUp(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                CATCH_UP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WatchSyncWorker>().build(),
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchSyncWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
