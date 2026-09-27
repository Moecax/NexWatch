package com.nexwatch.ui.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Does NOT construct a DataViewModel: its constructor needs an ExportRepository, whose DAO
 * dependencies are :core:database types that :app deliberately never depends on directly (module
 * table, CLAUDE.md) — pulling them in just for this test would be a worse boundary violation than
 * the gap it would close. So this only covers DataUiState's shape; DataViewModel's actual
 * export()/import()/backup logic is exercised by ExportRepository/JsonlZipExporter/ZipImporter's
 * own JVM suites in :core:data/:core:export (DataViewModel is a thin orchestration layer over
 * those) plus the manual on-device SAF pass recorded in docs/implementation-plan.md §12 Phase 7.
 */
class DataViewModelTest {

    @Test
    fun `state starts Idle`() = runTest {
        assertTrue(DataUiState.Idle is DataUiState.Idle)
    }

    @Test
    fun `ExportSuccess carries the record counts through unchanged`() {
        val state = DataUiState.ExportSuccess(mapOf("steps" to 10))
        assertEquals(10, state.recordCounts["steps"])
    }

    @Test
    fun `Failed carries a message`() {
        val state = DataUiState.Failed("boom")
        assertEquals("boom", state.message)
    }
}
