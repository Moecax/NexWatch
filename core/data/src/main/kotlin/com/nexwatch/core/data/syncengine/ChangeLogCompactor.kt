package com.nexwatch.core.data.syncengine

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.ChangeLogDao
import com.nexwatch.core.database.SyncCursorDao
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * §7.4. Every cursor row protects the log above it, including a provider stopped by a Fatal error, so
 * "Reconnect" can resume. With no cursors left the whole log goes: a provider enabled later starts from a
 * snapshot, not from the log.
 */
class ChangeLogCompactor @Inject constructor(
    private val changeLogDao: ChangeLogDao,
    private val cursorDao: SyncCursorDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun compact(): Int = withContext(dispatchers.io) {
        val floor = cursorDao.minLastSeq()
        if (floor == null) changeLogDao.deleteAll() else changeLogDao.deleteUpTo(floor)
    }
}
