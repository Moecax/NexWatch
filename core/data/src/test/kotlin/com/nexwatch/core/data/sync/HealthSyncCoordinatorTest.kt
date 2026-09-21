package com.nexwatch.core.data.sync

import com.nexwatch.core.data.journal.JournalRepository
import com.nexwatch.core.data.normalize.DailySummaryAggregator
import com.nexwatch.core.data.normalize.HealthDataNormalizer
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncCoordinatorTest {

    @Test
    fun `does nothing when the watch is not Ready`() = runTest {
        val db = inMemoryTestDatabase()
        val fakeClient = FakeWatchClient() // stays in a non-Ready state by default (see FakeWatchClient's docs)
        val coordinator = HealthSyncCoordinator(
            fakeClient,
            JournalRepository(db.rawIngestDao(), testDispatchers(this)),
            HealthDataNormalizer(db, noopDecoder(), testDispatchers(this)),
            DailySummaryAggregator(db, testDispatchers(this)),
            db.deviceDao(),
            db.rawIngestDao(),
        )

        val result = coordinator.syncAndNormalize()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        db.close()
    }
}

private fun noopDecoder() = object : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String) = emptyList<com.nexwatch.core.model.DecodedHealthRecord>()
}

private fun testDispatchers(scope: kotlinx.coroutines.test.TestScope) = object : com.nexwatch.core.common.CoroutineDispatchers {
    override val io = kotlinx.coroutines.test.StandardTestDispatcher(scope.testScheduler)
    override val default = io
}
