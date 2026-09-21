package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

private const val DEVICE = "AA:BB"

class DailySummaryAggregatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `recompute sums steps and HR for the date without clobbering an existing live total`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val zone = ZoneId.systemDefault()
        val dayStart = java.time.LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val meta = { key: String, at: Long -> RecordMeta(key, DEVICE, at, at, 0, Origin.MONITOR, ingestedAt = 0) }
        db.stepsDao().insertAll(
            listOf(
                StepsEntity("s1", meta("s1", dayStart + 1_000), 100, 80f, 4f),
                StepsEntity("s2", meta("s2", dayStart + 2_000), 200, 160f, 8f),
            ),
        )
        db.healthSampleDao().insertHeartRate(
            listOf(HeartRateEntity("h1", meta("h1", dayStart + 1_000), 60), HeartRateEntity("h2", meta("h2", dayStart + 2_000), 80)),
        )
        db.dailySummaryDao().ensureRowExists(DEVICE, "2026-09-19")
        db.dailySummaryDao().updateLiveStepsTotal(DEVICE, "2026-09-19", 250)
        val aggregator = DailySummaryAggregator(db, dispatchers)

        aggregator.recompute(DEVICE, setOf("2026-09-19"))

        val summary = db.dailySummaryDao().findByDate(DEVICE, "2026-09-19")!!
        assertEquals(300, summary.steps)
        assertEquals(60, summary.restingHrBpm) // MIN()-of-day heuristic, see Step 6's note
        assertEquals(70, summary.avgHrBpm)
        assertEquals(80, summary.maxHrBpm)
        assertEquals(250, summary.liveStepsTotal) // preserved, not overwritten
        db.close()
    }
}
