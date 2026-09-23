package com.nexwatch.core.data.normalize

import androidx.room.useWriterConnection
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

private const val DEVICE = "AA:BB"

/** Deterministic: dataType -> the exact records to hand back, ignoring payloadJson entirely. */
private class ScriptedDecoder(private val script: Map<String, List<DecodedHealthRecord>>) : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> = script[dataType].orEmpty()
}

class JournalReplayTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `reprocessing the journal from scratch reproduces the same canonical rows`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val script = mapOf(
            "step" to listOf(DecodedHealthRecord.Step(1_700_000_000_000L, 1_700_000_060_000L, 100, 80f, 4f)),
            "heart_rate" to listOf(DecodedHealthRecord.HeartRate(1_700_000_000_000L, 65)),
        )
        db.rawIngestDao().insert(RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step", payloadJson = "[]"))
        db.rawIngestDao().insert(RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "heart_rate", payloadJson = "[]"))
        val normalizer = HealthDataNormalizer(db, ScriptedDecoder(script), dispatchers)

        normalizer.processUnprocessed()
        val stepsAfterFirstRun = db.stepsDao().observeDailyTotal(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L).first()
        val hrAfterFirstRun = db.healthSampleDao().findDailyHrStats(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L)
        // Every insert above goes through a change-log trigger (CLAUDE.md I4); the trigger only
        // fires for rows SQLite actually writes, so its row count is a duplication oracle that a
        // coincidentally-equal SUM()/AVG() aggregate could not catch (e.g. a duplicated 65 bpm row
        // averaging to the same 65).
        val changeLogAfterFirstRun = db.changeLogDao().findAfter(afterSeq = 0, limit = 100)
        assertEquals(2, changeLogAfterFirstRun.size) // one UPSERT for the steps row, one for the heart-rate row

        // Simulate "replay from scratch": reset processed_at on every row and reprocess.
        db.useWriterConnection { transactor ->
            transactor.usePrepared("UPDATE raw_ingest SET processed_at = NULL") { stmt -> stmt.step() }
        }
        normalizer.processUnprocessed()
        val stepsAfterReplay = db.stepsDao().observeDailyTotal(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L).first()
        val hrAfterReplay = db.healthSampleDao().findDailyHrStats(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L)
        val changeLogAfterReplay = db.changeLogDao().findAfter(afterSeq = 0, limit = 100)

        // OnConflictStrategy.IGNORE means the replay is a no-op on already-present dedupe keys —
        // exactly "reproduces the canonical tables exactly," not "doubles every row."
        assertEquals(stepsAfterFirstRun, stepsAfterReplay)
        assertEquals(hrAfterFirstRun, hrAfterReplay)
        assertEquals(changeLogAfterFirstRun.size, changeLogAfterReplay.size) // no new change_log entries from the replay
        db.close()
    }
}
