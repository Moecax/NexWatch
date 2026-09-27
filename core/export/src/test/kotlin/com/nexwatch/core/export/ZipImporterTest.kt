package com.nexwatch.core.export

import androidx.room.useWriterConnection
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DEVICE_ID = "AA:BB"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

/** RoomDatabase.clearAllTables() isn't available on Room's standard-jvm variant — see RoundTripTest.kt. */
private suspend fun wipeTables(db: NexWatchDatabase, vararg tables: String) {
    db.useWriterConnection { tx ->
        tables.forEach { table -> tx.usePrepared("DELETE FROM $table") { it.step() } }
    }
}

class ZipImporterTest {

    private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    private suspend fun exportOneStep(db: NexWatchDatabase, repo: ExportRepository): ByteArray {
        val dedupeKey = "steps:$DEVICE_ID:1"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(dedupeKey),
            RecordMeta(dedupeKey, DEVICE_ID, 1L, 1L, 0, Origin.MONITOR, ingestedAt = 1L), 1, 0f, 0f)))
        val out = ByteArrayOutputStream()
        JsonlZipExporter(repo, FixedAppVersion()).export(out)
        return out.toByteArray()
    }

    @Test
    fun `importing the same export twice does not duplicate rows`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val bytes = exportOneStep(db, repo)
        wipeTables(db, "steps")
        val importer = ZipImporter(repo)

        importer.import(ByteArrayInputStream(bytes))
        importer.import(ByteArrayInputStream(bytes))

        assertEquals(1, db.stepsDao().pageAfter("", 100).size)
        db.close()
    }

    @Test
    fun `a tampered records file is rejected instead of silently imported`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val bytes = exportOneStep(db, repo)
        val tampered = rewriteEntry(bytes, "records/steps.jsonl", "{ not valid, but still the right length! }\n".toByteArray())
        wipeTables(db, "steps")

        assertThrows(ImportException.ChecksumMismatch::class.java) {
            kotlinx.coroutines.runBlocking { ZipImporter(repo).import(ByteArrayInputStream(tampered)) }
        }
        db.close()
    }

    @Test
    fun `a manifest from a newer schema version is rejected rather than imported blind`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val bytes = exportOneStep(db, repo)
        val manifestJson = ByteArrayInputStream(bytes).use { input ->
            ZipInputStream(input).let { zip ->
                var entry = zip.nextEntry
                while (entry != null && entry.name != "manifest.json") entry = zip.nextEntry
                zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        val bumped = manifestJson.replace("\"dbSchemaVersion\":1", "\"dbSchemaVersion\":2")
        val tampered = rewriteEntry(bytes, "manifest.json", bumped.toByteArray())
        wipeTables(db, "steps")

        val thrown = assertThrows(ImportException.UnsupportedSchema::class.java) {
            kotlinx.coroutines.runBlocking { ZipImporter(repo).import(ByteArrayInputStream(tampered)) }
        }
        assertEquals(2, thrown.fileSchemaVersion)
        assertEquals(1, thrown.appSchemaVersion)
        db.close()
    }

    /** Rewrites one zip entry's bytes in place, leaving every other entry (including manifest.json) untouched. */
    private fun rewriteEntry(original: ByteArray, targetName: String, replacement: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zipOut ->
            ZipInputStream(original.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val content = if (entry.name == targetName) replacement else zipIn.readBytes()
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    zipOut.write(content)
                    zipOut.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        return out.toByteArray()
    }
}
