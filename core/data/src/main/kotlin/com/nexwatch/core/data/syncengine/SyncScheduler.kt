package com.nexwatch.core.data.syncengine

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nexwatch.core.syncapi.SyncProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** §8.7's `SyncEngineWorker` row: after every ingest, plus a periodic safety net, per provider. */
interface SyncScheduler {
    suspend fun enqueueEnabled()
    fun enqueue(providerId: String)
    fun enqueueAfter(providerId: String, delay: Duration)
    fun schedulePeriodic(providerId: String)
    fun cancelPeriodic(providerId: String)
    fun cancel(providerId: String)
}

internal fun syncWorkName(providerId: String) = "sync-$providerId"
private fun periodicWorkName(providerId: String) = "sync-$providerId-periodic"

class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providers: Set<@JvmSuppressWildcards SyncProvider>,
    private val prefs: SyncPrefs,
) : SyncScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override suspend fun enqueueEnabled() {
        prefs.enabledProviderIds.first().forEach(::enqueue)
    }

    override fun enqueue(providerId: String) {
        workManager.enqueueUniqueWork(syncWorkName(providerId), ExistingWorkPolicy.KEEP, oneTime(providerId).build())
    }

    // APPEND_OR_REPLACE because the caller is usually the running work itself: KEEP would drop the
    // follow-up, and REPLACE would cancel the worker that's asking for it.
    override fun enqueueAfter(providerId: String, delay: Duration) {
        workManager.enqueueUniqueWork(
            syncWorkName(providerId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            oneTime(providerId).setInitialDelay(delay.toJavaDuration()).build(),
        )
    }

    override fun schedulePeriodic(providerId: String) {
        val request = PeriodicWorkRequestBuilder<SyncEngineWorker>(PERIODIC_HOURS, TimeUnit.HOURS)
            .setConstraints(constraintsFor(providerId))
            .setInputData(workDataOf(SyncEngineWorker.KEY_PROVIDER_ID to providerId))
            .build()
        workManager.enqueueUniquePeriodicWork(periodicWorkName(providerId), ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelPeriodic(providerId: String) {
        workManager.cancelUniqueWork(periodicWorkName(providerId))
    }

    override fun cancel(providerId: String) {
        workManager.cancelUniqueWork(syncWorkName(providerId))
        workManager.cancelUniqueWork(periodicWorkName(providerId))
    }

    private fun oneTime(providerId: String): OneTimeWorkRequest.Builder =
        OneTimeWorkRequestBuilder<SyncEngineWorker>()
            .setConstraints(constraintsFor(providerId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncEngineWorker.KEY_PROVIDER_ID to providerId))

    private fun constraintsFor(providerId: String): Constraints {
        val declared = providers.firstOrNull { it.id == providerId }?.constraints
        return Constraints.Builder()
            .setRequiredNetworkType(if (declared?.requiresNetwork == true) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .setRequiresCharging(declared?.requiresCharging == true)
            .build()
    }

    private companion object {
        const val PERIODIC_HOURS = 6L
        const val BACKOFF_SECONDS = 30L
    }
}
