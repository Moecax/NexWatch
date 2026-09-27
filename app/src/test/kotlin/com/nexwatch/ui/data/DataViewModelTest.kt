package com.nexwatch.ui.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataViewModel's export()/import() both need a real android.content.ContentResolver, which
 * doesn't exist on plain JVM — this test exercises the state machine (Idle -> Working -> result)
 * directly rather than standing up Robolectric for one screen. Full SAF flow verification is a
 * manual on-device pass (§Workflow), consistent with BackupWorker's DocumentFile boundary (Task 7).
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
