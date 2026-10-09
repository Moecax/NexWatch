package com.nexwatch.core.data.syncengine

import com.nexwatch.core.database.SyncCursorDao
import com.nexwatch.core.syncapi.Readiness
import com.nexwatch.core.syncapi.RecordType
import com.nexwatch.core.syncapi.SyncProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class BackfillProgress(val pushed: Int, val total: Int)

data class SyncProviderStatus(
    val id: String,
    val displayName: String,
    val supportedTypes: Set<RecordType>,
    val enabled: Boolean,
    /** Non-null while the history snapshot (§7.3) is still being sent. */
    val backfill: BackfillProgress?,
    val lastSuccessAt: Long?,
    /** Set by a Fatal outcome (the provider is then off) or by the last not-ready run. */
    val lastError: String?,
)

/** Settings → Sync (§7.1): every registered provider, whether the user has it on, and how far it has got. */
class SyncRepository @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards SyncProvider>,
    private val prefs: SyncPrefs,
    private val cursorDao: SyncCursorDao,
    private val scheduler: SyncScheduler,
    private val engine: SyncEngine,
) {
    val providerStatuses: Flow<List<SyncProviderStatus>> =
        combine(prefs.enabledProviderIds, cursorDao.observeAll()) { enabled, cursors ->
            providers.sortedBy { it.displayName }.map { provider ->
                val cursor = cursors.firstOrNull { it.providerId == provider.id }
                SyncProviderStatus(
                    id = provider.id,
                    displayName = provider.displayName,
                    supportedTypes = provider.supportedTypes,
                    enabled = provider.id in enabled,
                    backfill = cursor?.snapshotState?.let(::decodeSnapshotState)?.let { BackfillProgress(it.pushed, it.total) },
                    lastSuccessAt = cursor?.lastSuccessAt,
                    lastError = cursor?.lastError,
                )
            }
        }

    suspend fun readiness(providerId: String): Readiness =
        providers.firstOrNull { it.id == providerId }?.readiness() ?: Readiness.Unavailable("Unknown provider")

    /**
     * Turning a provider off forgets its cursor, so turning it on again backfills from scratch. That is safe
     * because pushes are idempotent, and it means compaction never has to keep the log for a switched-off
     * provider. Data already sent stays in the destination.
     */
    suspend fun setEnabled(providerId: String, enabled: Boolean) {
        prefs.setEnabled(providerId, enabled)
        if (enabled) {
            cursorDao.find(providerId)?.let { cursorDao.upsert(it.copy(lastError = null)) }
            scheduler.schedulePeriodic(providerId)
            scheduler.enqueue(providerId)
        } else {
            scheduler.cancel(providerId)
            engine.forgetCursor(providerId)
        }
    }

    fun syncNow(providerId: String) = scheduler.enqueue(providerId)

    suspend fun syncEnabledNow() = scheduler.enqueueEnabled()

    /** Re-sends everything through a fresh snapshot without switching the provider off. */
    suspend fun resendAll(providerId: String) {
        scheduler.cancel(providerId)
        engine.forgetCursor(providerId)
        scheduler.schedulePeriodic(providerId)
        scheduler.enqueue(providerId)
    }
}
