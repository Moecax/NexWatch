package com.nexwatch.core.data.notification

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.watchapi.notification.ForwardingLogEntry
import com.nexwatch.core.watchapi.notification.ForwardingOutcome
import com.nexwatch.core.watchapi.notification.ForwardingSkip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory

class NotificationActivityLogTest {

    private val tempDir = createTempDirectory("activity-log").toFile()
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }
    private val diagnostics = DiagnosticsStore(
        PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "diagnostics.preferences_pb") }),
        dispatchers,
    )
    private val log = NotificationActivityLog(diagnostics)

    private fun entry(atMs: Long, outcome: ForwardingOutcome) =
        ForwardingLogEntry(atMs, "org.telegram.messenger", "Amina", outcome)

    @Test
    fun `entries are newest first and capped`() = runTest(dispatcher) {
        repeat(105) { log.record(entry(it.toLong(), ForwardingOutcome.Skipped(ForwardingSkip.DUPLICATE))) }
        val entries = log.entries.value
        assertEquals(100, entries.size)
        assertEquals(104L, entries.first().atMs)
    }

    @Test
    fun `only sent notifications count toward today and the last-forwarded time`() = runTest(dispatcher) {
        log.record(entry(1_000, ForwardingOutcome.Skipped(ForwardingSkip.WATCH_DISCONNECTED)))
        log.record(entry(2_000, ForwardingOutcome.Failed("timed out")))
        assertNull(diagnostics.snapshot.first().lastNotificationForwardedAt)

        log.record(entry(3_000, ForwardingOutcome.Sent))
        val snapshot = diagnostics.snapshot.first()
        assertEquals(3_000L, snapshot.lastNotificationForwardedAt)
    }

    @Test
    fun `the daily count resets on a new local day`() = runTest(dispatcher) {
        val day1 = LocalDate.of(2026, 10, 10)
        val noon1 = day1.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        diagnostics.recordForwarded(noon1, ZoneOffset.UTC)
        diagnostics.recordForwarded(noon1 + 60_000, ZoneOffset.UTC)
        assertEquals(2, diagnostics.snapshot.first().forwardedOn(day1))

        diagnostics.recordForwarded(noon1 + 86_400_000, ZoneOffset.UTC)
        val snapshot = diagnostics.snapshot.first()
        assertEquals(1, snapshot.forwardedOn(day1.plusDays(1)))
        assertEquals(0, snapshot.forwardedOn(day1))
    }
}
