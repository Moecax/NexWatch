package com.nexwatch.core.data.diagnostics

import kotlinx.serialization.Serializable

/**
 * The kinds of event the connection timeline records (design Batch 8 #5).
 */
enum class DiagnosticEventKind { CONNECTED, RECONNECTED, DISCONNECTED, SYNC_COMPLETED }

/**
 * One line of the connection timeline. Persisted in DiagnosticsStore as JSON,
 * bounded to [DiagnosticsStore.MAX_EVENTS] entries. [detail] carries the human
 * reason ("out of range") or payload ("heart rate, steps") the timeline shows
 * under the event title.
 */
@Serializable
data class DiagnosticEvent(
    val atMs: Long,
    val kind: DiagnosticEventKind,
    val detail: String? = null,
)
