package com.nexwatch.core.data.diagnostics

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * The event log behind the Diagnostics timeline (design Batch 8 #5): appended only on
 * real connection transitions, bounded, and readable even when its payload drifts.
 */
class DiagnosticsEventLogTest {

    private val tempDir = createTempDirectory("diagnostics-events").toFile()
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir, "diagnostics.preferences_pb") },
    )
    private val store = DiagnosticsStore(dataStore, dispatchers)

    @Test
    fun `the first session is a CONNECTED event`() = runTest(dispatcher) {
        store.recordSessionStarted(1_000)
        val events = store.events.first()
        assertEquals(listOf(DiagnosticEventKind.CONNECTED), events.map { it.kind })
    }

    @Test
    fun `last-connected updates on every sighting but adds no event`() = runTest(dispatcher) {
        store.recordSessionStarted(1_000)
        store.recordConnected(2_000) // Ready keeps emitting (battery updates); one session, one event
        store.recordConnected(3_000)
        assertEquals(1, store.events.first().size)
        assertEquals(3_000L, store.snapshot.first().lastConnectedAt)
    }

    @Test
    fun `a session after an observed disconnect is a reconnect`() = runTest(dispatcher) {
        store.recordSessionStarted(1_000)
        store.recordDisconnected(2_000, detail = "out of range")
        store.recordSessionStarted(3_000)
        val events = store.events.first()
        assertEquals(
            listOf(DiagnosticEventKind.CONNECTED, DiagnosticEventKind.DISCONNECTED, DiagnosticEventKind.RECONNECTED),
            events.map { it.kind },
        )
        assertEquals("out of range", events[1].detail)
    }

    @Test
    fun `a session never closed is closed at the last known event before the next one opens`() = runTest(dispatcher) {
        store.recordSessionStarted(1_000)
        store.recordSyncSucceeded(4_000, detail = "heart rate")
        // The process died here while connected: no disconnect was ever observed.
        store.recordSessionStarted(90_000)
        val events = store.events.first()
        assertEquals(
            listOf(
                DiagnosticEventKind.CONNECTED,
                DiagnosticEventKind.SYNC_COMPLETED,
                DiagnosticEventKind.DISCONNECTED,
                DiagnosticEventKind.RECONNECTED,
            ),
            events.map { it.kind },
        )
        assertEquals(4_000L, events[2].atMs)
        assertEquals(DiagnosticsStore.APP_STOPPED, events[2].detail)
    }

    @Test
    fun `a disconnect with no open session is ignored`() = runTest(dispatcher) {
        store.recordDisconnected(1_000)
        store.recordSessionStarted(2_000)
        store.recordDisconnected(3_000)
        store.recordDisconnected(4_000)
        assertEquals(
            listOf(DiagnosticEventKind.CONNECTED, DiagnosticEventKind.DISCONNECTED),
            store.events.first().map { it.kind },
        )
    }

    @Test
    fun `a successful sync updates last-sync, clears the error and lands in the log`() = runTest(dispatcher) {
        store.recordSyncFailed(4_000, "The watch stopped responding")
        store.recordSyncSucceeded(5_000, detail = "heart rate, step")
        val snapshot = store.snapshot.first()
        assertEquals(5_000L, snapshot.lastSyncAt)
        assertEquals(null, snapshot.unresolvedSyncError)
        val last = store.events.first().last()
        assertEquals(DiagnosticEventKind.SYNC_COMPLETED, last.kind)
        assertEquals("heart rate, step", last.detail)
    }

    @Test
    fun `the log is capped, keeping the newest`() = runTest(dispatcher) {
        repeat(DiagnosticsStore.MAX_EVENTS + 5) { store.recordSyncSucceeded(it.toLong()) }
        val events = store.events.first()
        assertEquals(DiagnosticsStore.MAX_EVENTS, events.size)
        assertEquals(5L, events.first().atMs)
    }

    @Test
    fun `a corrupt event payload reads as empty instead of failing the flow`() = runTest(dispatcher) {
        store.recordConnected(1_000)
        dataStore.edit { it[stringPreferencesKey("connection_events")] = "{not json" }
        assertTrue(store.events.first().isEmpty())
        // The last-* timestamps live in their own keys; only the list degrades.
        assertEquals(1_000L, store.snapshot.first().lastConnectedAt)
    }
}
