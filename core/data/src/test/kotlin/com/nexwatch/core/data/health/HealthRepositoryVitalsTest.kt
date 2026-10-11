package com.nexwatch.core.data.health

import com.nexwatch.core.database.BloodPressureEntity
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.Spo2Entity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DEVICE = "AA:BB"
private val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi") // UTC+3, no DST

private fun meta(key: String, atMs: Long, deleted: Boolean = false) = RecordMeta(
    dedupeKey = key, deviceId = DEVICE, startTime = atMs, endTime = atMs, zoneOffsetSec = 3 * 3600,
    origin = Origin.MONITOR, deleted = deleted, ingestedAt = 0,
)

private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

class HealthRepositoryVitalsTest {

    @Test
    fun `daily heart rate groups by local day, not UTC day`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        db.healthSampleDao().insertHeartRate(
            listOf(
                // 23:00 local on the 9th and 01:30 local on the 10th, though both fall on the 9th in UTC.
                HeartRateEntity("a", meta("a", at("2026-10-09T20:00:00Z")), 60),
                HeartRateEntity("b", meta("b", at("2026-10-09T22:30:00Z")), 52),
                HeartRateEntity("c", meta("c", at("2026-10-10T09:00:00Z")), 140),
            ),
        )
        val repository = HealthRepository(db.deviceDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao())

        val days = repository.observeDailyHeartRate(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10), NAIROBI).first()

        assertEquals(listOf("2026-10-09", "2026-10-10"), days.map { it.date })
        assertEquals(60, days[0].maxBpm)
        assertEquals(52, days[1].minBpm)
        assertEquals(140, days[1].maxBpm)
        db.close()
    }

    @Test
    fun `latest readings skip deleted rows and the SpO2 low covers only its window`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val dao = db.healthSampleDao()
        dao.insertSpo2(
            listOf(
                Spo2Entity("s1", meta("s1", 1_000), 91),
                Spo2Entity("s2", meta("s2", 5_000), 97),
                Spo2Entity("s3", meta("s3", 9_000, deleted = true), 80),
            ),
        )
        dao.insertBloodPressure(listOf(BloodPressureEntity("bp", meta("bp", 4_000), 118, 77)))
        val repository = HealthRepository(db.deviceDao(), dao, db.sleepDao(), db.workoutDao())

        assertEquals(97, repository.observeLatestSpo2().first()?.percent)
        assertEquals(97, repository.observeSpo2Low(2_000, 10_000).first())
        assertEquals(118, repository.observeLatestBloodPressure().first()?.systolic)
        assertNull(repository.observeLatestHeartRate().first())
        db.close()
    }

    @Test
    fun `heart rate ranges keep each bucket's min and max`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        db.healthSampleDao().insertHeartRate(
            listOf(HeartRateEntity("x", meta("x", 60_000), 70), HeartRateEntity("y", meta("y", 120_000), 90)),
        )
        val repository = HealthRepository(db.deviceDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao())

        val buckets = repository.observeHeartRateRanges(0, 900_000, 900_000).first()

        assertEquals(1, buckets.size)
        assertEquals(70, buckets[0].minBpm)
        assertEquals(90, buckets[0].maxBpm)
        assertEquals(80, buckets[0].avgBpm)
        db.close()
    }
}
