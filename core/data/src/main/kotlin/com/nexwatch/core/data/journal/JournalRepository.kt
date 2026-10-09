package com.nexwatch.core.data.journal

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.sync.FITCLOUD_SDK_VERSION
import com.nexwatch.core.database.RawIngestDao
import com.nexwatch.core.database.RawIngestEntity
import com.nexwatch.core.watchapi.RawBatch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** CLAUDE.md I2 — the only thing that happens per synced item before it's safely on disk. */
class JournalRepository @Inject constructor(
    private val rawIngestDao: RawIngestDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun append(batch: RawBatch): Unit = withContext(dispatchers.io) {
        rawIngestDao.insert(
            RawIngestEntity(
                receivedAt = System.currentTimeMillis(),
                sdkVersion = FITCLOUD_SDK_VERSION,
                dataType = batch.dataType,
                payloadJson = batch.payloadJson,
            ),
        )
        Unit
    }
}
