package com.nexwatch.core.data.health

import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySummaryRepositoryTest {

    @Test
    fun `observeToday returns null when nothing bound yet`() = runTest {
        val db = inMemoryTestDatabase()
        val repository = DailySummaryRepository(db.deviceDao(), db.dailySummaryDao())

        assertNull(repository.observeToday().first())
        db.close()
    }

    @Test
    fun `observeToday maps the entity for the most recently bound device`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity("AA:BB", null, null, null, null, boundAtMs = 0))
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        db.dailySummaryDao().upsert(
            DailySummaryEntity(
                deviceId = "AA:BB", date = today, steps = 500, distanceM = 400, energyKcal = 20,
                restingHrBpm = 55, avgHrBpm = 70, maxHrBpm = 100, sleepMinutes = 420, liveStepsTotal = 500,
            ),
        )
        val repository = DailySummaryRepository(db.deviceDao(), db.dailySummaryDao())

        val result = repository.observeToday().first()

        assertEquals(500, result?.steps)
        db.close()
    }
}
