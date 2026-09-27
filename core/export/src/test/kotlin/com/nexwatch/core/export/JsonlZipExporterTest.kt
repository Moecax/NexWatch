package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

private const val DEVICE_ID = "AA:BB"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

class JsonlZipExporterTest {

    private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `export writes a well-formed empty zip for a fresh database`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val exporter = JsonlZipExporter(repo, FixedAppVersion())
        val out = ByteArrayOutputStream()

        val history = exporter.export(out)

        assertEquals(0, history.recordCounts["steps"])
        val entries = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertTrue(entries.contains("manifest.json"))
        assertTrue(entries.contains("records/steps.jsonl"))
        db.close()
    }

    @Test
    fun `export walks a synthetic dataset across many pages without dropping or duplicating rows`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val rowCount = 2_500 // with PAGE_SIZE=1000 this forces 3 pages — proves the loop doesn't stop after page 1
        val rows = (0 until rowCount).map {
            val dedupeKey = "steps:$DEVICE_ID:$it"
            StepsEntity(pkFor(dedupeKey), RecordMeta(dedupeKey, DEVICE_ID, it.toLong(), it.toLong(), 0, Origin.MONITOR,
                ingestedAt = it.toLong()), count = 1, distanceM = 0f, energyKcal = 0f)
        }
        db.stepsDao().insertAll(rows)
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val exporter = JsonlZipExporter(repo, FixedAppVersion())
        val out = ByteArrayOutputStream()

        val history = exporter.export(out)

        assertEquals(rowCount, history.recordCounts["steps"])
        val jsonlLines = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.name != "records/steps.jsonl") entry = zip.nextEntry
            zip.bufferedReader(Charsets.UTF_8).readLines()
        }
        assertEquals(rowCount, jsonlLines.size)
        db.close()
    }

    @Test
    fun `manifest sha256 for each entry matches the actual bytes written`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val dedupeKey = "steps:$DEVICE_ID:1"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(dedupeKey),
            RecordMeta(dedupeKey, DEVICE_ID, 1L, 1L, 0, Origin.MONITOR, ingestedAt = 1L), 1, 0f, 0f)))
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val exporter = JsonlZipExporter(repo, FixedAppVersion())
        val out = ByteArrayOutputStream()

        exporter.export(out)

        val bytes = out.toByteArray()
        var manifestJson: String? = null
        var stepsJsonlBytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "manifest.json" -> manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                    "records/steps.jsonl" -> stepsJsonlBytes = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val actualSha = MessageDigest.getInstance("SHA-256").digest(stepsJsonlBytes!!).joinToString("") { "%02x".format(it) }
        assertTrue(manifestJson!!.contains(actualSha))
        db.close()
    }
}
