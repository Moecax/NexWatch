package com.nexwatch.core.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Pure derivations over the event log: uptime and reconnect counts for "today". */
class DiagnosticsStatsTest {

    private val zone = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2026, 10, 10)

    private fun at(hour: Int, minute: Int = 0, on: LocalDate = day): Long =
        on.atTime(hour, minute).toInstant(zone).toEpochMilli()

    private fun event(atMs: Long, kind: DiagnosticEventKind, detail: String? = null) =
        DiagnosticEvent(atMs, kind, detail)

    @Test
    fun `uptime sums connect-to-disconnect intervals inside the day`() {
        val events = listOf(
            event(at(10), DiagnosticEventKind.CONNECTED),
            event(at(11, 30), DiagnosticEventKind.DISCONNECTED),
            event(at(14), DiagnosticEventKind.RECONNECTED),
            event(at(15), DiagnosticEventKind.DISCONNECTED),
        )
        val nowMs = at(20)
        assertEquals(150 * 60_000L, DiagnosticsStats.uptimeOn(events, day, zone, nowMs))
    }

    @Test
    fun `an ongoing connection counts up to now`() {
        val events = listOf(event(at(10), DiagnosticEventKind.CONNECTED))
        val nowMs = at(12, 30)
        assertEquals(150 * 60_000L, DiagnosticsStats.uptimeOn(events, day, zone, nowMs))
    }

    @Test
    fun `an overnight connection contributes only today's slice`() {
        val events = listOf(
            event(at(20, on = day.minusDays(1)), DiagnosticEventKind.CONNECTED),
            event(at(6), DiagnosticEventKind.DISCONNECTED),
        )
        val nowMs = at(20)
        assertEquals(6 * 3_600_000L, DiagnosticsStats.uptimeOn(events, day, zone, nowMs))
    }

    @Test
    fun `sync events never count toward uptime`() {
        val events = listOf(
            event(at(10), DiagnosticEventKind.SYNC_COMPLETED),
            event(at(11), DiagnosticEventKind.SYNC_COMPLETED),
        )
        assertEquals(0L, DiagnosticsStats.uptimeOn(events, day, zone, at(20)))
    }

    @Test
    fun `reconnects count only on the requested day`() {
        val events = listOf(
            event(at(9), DiagnosticEventKind.RECONNECTED),
            event(at(10, on = day.minusDays(1)), DiagnosticEventKind.RECONNECTED),
            event(at(13), DiagnosticEventKind.RECONNECTED),
            event(at(14), DiagnosticEventKind.CONNECTED),
            event(at(15, on = day.plusDays(1)), DiagnosticEventKind.RECONNECTED),
        )
        assertEquals(2, DiagnosticsStats.reconnectsOn(events, day, zone))
        assertEquals(1, DiagnosticsStats.reconnectsOn(events, day.minusDays(1), zone))
        assertEquals(1, DiagnosticsStats.reconnectsOn(events, day.plusDays(1), zone))
    }

    @Test
    fun `no events means no uptime and no reconnects`() {
        assertEquals(0L, DiagnosticsStats.uptimeOn(emptyList(), day, zone, at(20)))
        assertEquals(0, DiagnosticsStats.reconnectsOn(emptyList(), day, zone))
    }
}
