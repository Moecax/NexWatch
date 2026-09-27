package com.nexwatch.core.database

import androidx.room.useWriterConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

private fun meta(dedupeKey: String, start: Long) = RecordMeta(
    dedupeKey = dedupeKey,
    deviceId = DEVICE_ID,
    startTime = start,
    endTime = start,
    zoneOffsetSec = 0,
    origin = Origin.MONITOR,
    ingestedAt = start,
)

private fun pkFor(dedupeKey: String): String = UUID.nameUUIDFromBytes(dedupeKey.toByteArray()).toString()

class ExportPagingTest {

    @Test
    fun `pageAfter walks every row exactly once across many small pages`() = runTest {
        val db = inMemoryTestDatabase()
        val rows = (0 until 25).map {
            val dedupeKey = "steps:$DEVICE_ID:$it"
            StepsEntity(pkFor(dedupeKey), meta(dedupeKey, it.toLong()), count = it, distanceM = 0f, energyKcal = 0f)
        }
        db.stepsDao().insertAll(rows)

        val seen = mutableListOf<StepsEntity>()
        var cursor = ""
        while (true) {
            val page = db.stepsDao().pageAfter(cursor, limit = 4)
            if (page.isEmpty()) break
            seen += page
            cursor = page.last().pk
        }

        assertEquals(25, seen.size)
        assertEquals(rows.map { it.pk }.toSet(), seen.map { it.pk }.toSet()) // every row, no duplicates
        db.close()
    }

    @Test
    fun `pageAfter includes tombstoned rows`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "steps:$DEVICE_ID:tomb"
        val pk = pkFor(dedupeKey)
        db.stepsDao().insertAll(listOf(StepsEntity(pk, meta(dedupeKey, 0L), count = 1, distanceM = 0f, energyKcal = 0f)))
        db.useWriterConnection { tx ->
            tx.usePrepared("UPDATE steps SET version = 2, deleted = 1 WHERE pk = ?") { it.bindText(1, pk); it.step() }
        }

        val page = db.stepsDao().pageAfter("", limit = 10)

        assertEquals(1, page.size)
        assertTrue(page[0].meta.deleted) // export must carry tombstones, not filter them out
        db.close()
    }

    @Test
    fun `exportHistoryDao round-trips an entry`() = runTest {
        val db = inMemoryTestDatabase()
        db.exportHistoryDao().insert(
            ExportHistoryEntity(at = 1_000L, uri = "content://x", format = "zip", range = "2026-01-01..2026-09-25", recordCountsJson = "{}"),
        )

        val recent = db.exportHistoryDao().recent(limit = 10)

        assertEquals(1, recent.size)
        assertEquals("content://x", recent[0].uri)
        db.close()
    }

    @Test
    fun `pageAfter on an empty table returns an empty first page`() = runTest {
        val db = inMemoryTestDatabase()
        assertTrue(db.stepsDao().pageAfter("", limit = 100).isEmpty())
        db.close()
    }
}
