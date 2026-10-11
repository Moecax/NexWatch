package com.nexwatch.ui.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Does NOT construct a DataViewModel: its constructor needs an ExportRepository, whose DAO
 * dependencies are :core:database types that :app deliberately never depends on directly (module
 * table, CLAUDE.md) — pulling them in just for this test would be a worse boundary violation than
 * the gap it would close. So this only covers TransferState's shape and the screen's formatting; DataViewModel's actual
 * export()/import()/backup logic is exercised by ExportRepository/JsonlZipExporter/ZipImporter's
 * own JVM suites in :core:data/:core:export (DataViewModel is a thin orchestration layer over
 * those) plus the manual on-device SAF pass recorded in docs/implementation-plan.md §12 Phase 7.
 */
class DataViewModelTest {

    @Test
    fun `an export in progress collects tables in the order they finish`() = runTest {
        var state: TransferState = TransferState.Exporting(emptyMap())
        state = (state as TransferState.Exporting).copy(written = state.written + ("steps" to 10))
        state = state.copy(written = state.written + ("heart_rate" to 4))
        assertEquals(listOf("steps", "heart_rate"), (state as TransferState.Exporting).written.keys.toList())
    }

    @Test
    fun `Failed carries a message`() {
        val state = TransferState.Failed("boom")
        assertEquals("boom", state.message)
        assertTrue(state !is TransferState.Exporting)
    }

    @Test
    fun `sizes read in megabytes with one decimal`() {
        assertEquals("21.6 MB", megabytes(22_649_241))
        assertEquals("0.0 MB", megabytes(0))
    }
}
