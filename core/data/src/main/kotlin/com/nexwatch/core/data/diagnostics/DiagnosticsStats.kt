package com.nexwatch.core.data.diagnostics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure derivations over the event log, so the Diagnostics screen doesn't do
 * date math inline. The log is the only source: nothing here polls or guesses,
 * and an app that never observed a disconnect simply shows no reconnect.
 */
object DiagnosticsStats {

    /** [events] is oldest first, as stored. */
    fun eventsOn(events: List<DiagnosticEvent>, day: LocalDate, zone: ZoneId): List<DiagnosticEvent> =
        events.filter { dayOf(it.atMs, zone) == day }

    /**
     * Time the watch was connected during [day], in milliseconds: each
     * connect-to-disconnect interval that overlaps the day, plus the interval
     * since the last connect if the watch is connected right now. Intervals are
     * clipped to the day, so an overnight connection counts only today's slice.
     */
    fun uptimeOn(events: List<DiagnosticEvent>, day: LocalDate, zone: ZoneId, nowMs: Long): Long {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfNow = minOf(nowMs, dayEnd)
        var total = 0L
        var connectedAt: Long? = null
        events.forEach { event ->
            when (event.kind) {
                DiagnosticEventKind.CONNECTED, DiagnosticEventKind.RECONNECTED ->
                    if (connectedAt == null) connectedAt = event.atMs
                DiagnosticEventKind.DISCONNECTED -> {
                    connectedAt?.let { start ->
                        total += overlap(start, event.atMs, dayStart, dayEnd)
                    }
                    connectedAt = null
                }
                DiagnosticEventKind.SYNC_COMPLETED -> Unit
            }
        }
        connectedAt?.let { start -> total += overlap(start, endOfNow, dayStart, dayEnd) }
        return total.coerceAtLeast(0L)
    }

    /** Reconnects (as opposed to first connects) recorded during [day]. */
    fun reconnectsOn(events: List<DiagnosticEvent>, day: LocalDate, zone: ZoneId): Int =
        eventsOn(events, day, zone).count { it.kind == DiagnosticEventKind.RECONNECTED }

    fun dayOf(atMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate()

    private fun overlap(fromMs: Long, toMs: Long, dayStart: Long, dayEnd: Long): Long {
        if (toMs <= fromMs) return 0L
        return (minOf(toMs, dayEnd) - maxOf(fromMs, dayStart)).coerceAtLeast(0L)
    }
}
