package com.nexwatch.core.data.syncengine

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncEngineWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val providerId = inputData.getString(KEY_PROVIDER_ID) ?: return Result.failure()
        return when (val result = engine.run(providerId)) {
            SyncRunResult.CaughtUp, SyncRunResult.NotEnabled, is SyncRunResult.NotReady -> Result.success()
            is SyncRunResult.Retry -> {
                val after = result.after
                if (after == null) {
                    Result.retry()
                } else {
                    scheduler.enqueueAfter(providerId, after)
                    Result.success()
                }
            }
            is SyncRunResult.Fatal -> {
                // Only the periodic work: cancelling this unique work would cancel the worker running now.
                scheduler.cancelPeriodic(providerId)
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_PROVIDER_ID = "provider_id"
    }
}
