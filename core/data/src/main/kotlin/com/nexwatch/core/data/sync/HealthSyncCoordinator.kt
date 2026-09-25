package com.nexwatch.core.data.sync

import com.nexwatch.core.data.journal.JournalRepository
import com.nexwatch.core.data.normalize.DailySummaryAggregator
import com.nexwatch.core.data.normalize.HealthDataNormalizer
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.RawIngestDao
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val JOURNAL_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

/**
 * §5.2's full pipeline in one call: sync → journal → normalize → aggregate. Both the
 * debounced Ready-trigger (WatchConnectionService, Task 19) and WatchSyncWorker call this —
 * one orchestration, two schedules, per CLAUDE.md's "don't repeat the code" spirit.
 */
class HealthSyncCoordinator @Inject constructor(
    private val watchClient: WatchClient,
    private val journalRepository: JournalRepository,
    private val normalizer: HealthDataNormalizer,
    private val aggregator: DailySummaryAggregator,
    private val deviceDao: DeviceDao,
    private val rawIngestDao: RawIngestDao,
) {
    suspend fun syncAndNormalize(): Result<Set<String>> = runCatching {
        if (watchClient.state.first() !is WatchState.Ready) return@runCatching emptySet()

        watchClient.syncHealthData().collect { progress ->
            progress.batch?.let { journalRepository.append(it) }
        }
        val affectedDates = normalizer.processUnprocessed()
        val device = deviceDao.observeMostRecentlyBound().first()
        if (device != null && affectedDates.isNotEmpty()) {
            aggregator.recompute(device.address, affectedDates)
        }
        rawIngestDao.pruneProcessedBefore(System.currentTimeMillis() - JOURNAL_RETENTION_MS)
        affectedDates
    }
}
