package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.RawIngestEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private const val DEVICE = "AA:BB"

class SevenDayTimelineTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `seven consecutive days of synced steps produce seven daily_summary rows with no gaps`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val normalizer = HealthDataNormalizer(db, FakeDecoderPerRow(zone), dispatchers)
        val aggregator = DailySummaryAggregator(db, dispatchers)

        for (dayOffset in 0 until 7) {
            db.rawIngestDao().insert(
                RawIngestEntity(
                    receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step:$dayOffset",
                    payloadJson = "[]", // FakeDecoderPerRow below keys off dataType, not payload
                ),
            )
        }

        val affectedDates = normalizer.processUnprocessed()
        assertEquals(7, affectedDates.size) // each row lands on its own distinct day, none dropped
        aggregator.recompute(DEVICE, affectedDates)

        val fromDate = today.minusDays(6).toString()
        val toDate = today.toString()
        val summaries = db.dailySummaryDao().observeRange(DEVICE, fromDate, toDate).first()

        assertEquals(7, summaries.size)
        assertEquals((0 until 7).map { today.minusDays(it.toLong()).toString() }.sorted(), summaries.map { it.date }.sorted())
        summaries.forEach { assertEquals(1_000, it.steps) } // every day has real, non-null step data — no gaps
        db.close()
    }
}

/**
 * dataType "step:$n" decodes to one Step record for today-minus-n-days, timestamped mid-day.
 * `HealthDataNormalizer.originFor()` (Task 15) dispatches MEASURE vs MONITOR origin off whether
 * the source dataType ends with "_measure" — "step:$n" never does, so this fixture's dispatch is
 * always Origin.MONITOR regardless of `n`, which is what the real "step" dataType also resolves to.
 */
private class FakeDecoderPerRow(private val zone: ZoneId) : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> {
        val dayOffset = dataType.removePrefix("step:").toIntOrNull() ?: return emptyList()
        val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
        val atMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return listOf(DecodedHealthRecord.Step(atMs, atMs, 1_000, 800f, 40f))
    }
}
