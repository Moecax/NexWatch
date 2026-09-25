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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Test

private class FakeDecoder(private val records: List<DecodedHealthRecord>) : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String) = records
}

class HealthDataNormalizerTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `normalizing a step journal row inserts a steps entity and marks it processed`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity("AA:BB", null, null, null, null, boundAtMs = 0))
        db.rawIngestDao().insert(
            RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step", payloadJson = "[]"),
        )
        val stepEndMs = 1_700_000_000_000L
        val decoder = FakeDecoder(listOf(DecodedHealthRecord.Step(stepEndMs, stepEndMs, 14, 9.15f, 0.393f)))
        val normalizer = HealthDataNormalizer(db, decoder, dispatchers)

        val affectedDates = normalizer.processUnprocessed()

        val expectedDate = Instant.ofEpochMilli(stepEndMs).atZone(ZoneId.systemDefault())
            .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(setOf(expectedDate), affectedDates)

        val dayStart = Instant.ofEpochMilli(stepEndMs).atZone(ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = dayStart + 86_400_000L
        val stored = db.stepsDao().observeDailyTotal("AA:BB", dayStartMs = dayStart, dayEndMs = dayEnd).first()
        assertEquals(14, stored?.steps)

        db.close()
    }

    @Test
    fun `a decoder exception leaves the row unprocessed with an error recorded`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity("AA:BB", null, null, null, null, boundAtMs = 0))
        db.rawIngestDao().insert(
            RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step", payloadJson = "[]"),
        )
        val throwingDecoder = object : HealthDataDecoder {
            override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> =
                throw IllegalStateException("malformed payload")
        }
        val normalizer = HealthDataNormalizer(db, throwingDecoder, dispatchers)

        normalizer.processUnprocessed()

        val remaining = db.rawIngestDao().findUnprocessedDataTypes()
        assertEquals(listOf("step"), remaining)

        db.close()
    }
}
