package com.nexwatch.core.data.health

import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageDb
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DEVICE_ID = "AA:BB"

class HealthRepositoryTest {

    @Test
    fun `observeSleepNights returns empty list when nothing bound yet`() = runTest {
        val db = inMemoryTestDatabase()
        val repository = HealthRepository(db.deviceDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao())

        assertTrue(repository.observeSleepNights("2026-09-01", "2026-09-30").first().isEmpty())
        db.close()
    }

    @Test
    fun `observeSleepNights combines each session with its stages and sums totalMinutes`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE_ID, null, null, null, null, boundAtMs = 0))

        val night1Start = 1_000L
        db.sleepDao().replaceNight(
            session = SleepSessionEntity(
                pk = "night-1",
                meta = RecordMeta(
                    dedupeKey = "night-1", deviceId = DEVICE_ID, startTime = night1Start, endTime = night1Start + 8 * 3_600_000L,
                    zoneOffsetSec = 0, origin = Origin.MONITOR, ingestedAt = 0,
                ),
                nightDate = "2026-09-19",
                contentHash = "h1",
                score = 82,
                efficiency = 90,
            ),
            stages = listOf(
                SleepStageEntity(sessionId = "night-1", stage = SleepStageDb.LIGHT, startTime = 0L, endTime = 3_600_000L),
                SleepStageEntity(sessionId = "night-1", stage = SleepStageDb.DEEP, startTime = 3_600_000L, endTime = 3_600_000L + 1_800_000L),
            ),
        )
        db.sleepDao().replaceNight(
            session = SleepSessionEntity(
                pk = "night-2",
                meta = RecordMeta(
                    dedupeKey = "night-2", deviceId = DEVICE_ID, startTime = night1Start, endTime = night1Start + 8 * 3_600_000L,
                    zoneOffsetSec = 0, origin = Origin.MONITOR, ingestedAt = 0,
                ),
                nightDate = "2026-09-20",
                contentHash = "h2",
                score = 75,
                efficiency = 88,
            ),
            stages = listOf(
                SleepStageEntity(sessionId = "night-2", stage = SleepStageDb.REM, startTime = 0L, endTime = 1_200_000L),
            ),
        )

        val repository = HealthRepository(db.deviceDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao())

        val nights = repository.observeSleepNights("2026-09-01", "2026-09-30").first()

        assertEquals(2, nights.size)
        val first = nights.first { it.nightDate == "2026-09-19" }
        assertEquals(2, first.stages.size)
        assertEquals(90, first.totalMinutes)
        assertEquals(82, first.score)
        val second = nights.first { it.nightDate == "2026-09-20" }
        assertEquals(20, second.totalMinutes)
        db.close()
    }
}
