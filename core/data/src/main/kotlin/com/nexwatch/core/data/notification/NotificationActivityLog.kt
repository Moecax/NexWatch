package com.nexwatch.core.data.notification

import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.watchapi.notification.ForwardingLogEntry
import com.nexwatch.core.watchapi.notification.ForwardingOutcome
import com.nexwatch.core.watchapi.notification.NotificationForwardedRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch 6's forwarding activity log. Held in memory on purpose: notification titles are names
 * of people and conversations, and a log that only lives as long as the process never puts them
 * on disk. The count and last-forwarded time, which carry no content, are persisted.
 */
@Singleton
class NotificationActivityLog @Inject constructor(
    private val diagnostics: DiagnosticsStore,
) : NotificationForwardedRecorder {

    private val _entries = MutableStateFlow<List<ForwardingLogEntry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<ForwardingLogEntry>> = _entries.asStateFlow()

    override suspend fun record(entry: ForwardingLogEntry) {
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
        if (entry.outcome == ForwardingOutcome.Sent) diagnostics.recordForwarded(entry.atMs)
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
