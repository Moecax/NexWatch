package com.nexwatch.core.data.diagnostics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.DiagnosticsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DiagnosticsSnapshot(
    val lastConnectedAt: Long?,
    val lastSyncAt: Long?,
    val lastNotificationForwardedAt: Long?,
    /** When the last sync attempt ended, whether or not it succeeded. */
    val lastSyncAttemptAt: Long? = null,
    /** Why that attempt failed. Cleared by the next successful sync. */
    val lastSyncError: String? = null,
    /** The local day [forwardedOnDayCount] belongs to. Only one day is kept. */
    val forwardedDay: LocalDate? = null,
    val forwardedOnDayCount: Int = 0,
) {
    /** The last failure, while no later successful sync has superseded it. */
    val unresolvedSyncError: String?
        get() = lastSyncError?.takeIf { (lastSyncAttemptAt ?: 0L) > (lastSyncAt ?: 0L) }

    fun forwardedOn(day: LocalDate): Int = if (day == forwardedDay) forwardedOnDayCount else 0
}

/**
 * §8.4's "debug screen showing last connected, last sync, last notification forwarded",
 * plus the bounded event log behind the Diagnostics timeline (design Batch 8 #5).
 */
class DiagnosticsStore @Inject constructor(
    @DiagnosticsDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val snapshot: Flow<DiagnosticsSnapshot> = dataStore.data.map { prefs ->
        DiagnosticsSnapshot(
            lastConnectedAt = prefs[LAST_CONNECTED_KEY],
            lastSyncAt = prefs[LAST_SYNC_KEY],
            lastNotificationForwardedAt = prefs[LAST_NOTIFICATION_KEY],
            lastSyncAttemptAt = prefs[LAST_SYNC_ATTEMPT_KEY],
            lastSyncError = prefs[LAST_SYNC_ERROR_KEY],
            forwardedDay = prefs[FORWARDED_DAY_KEY]?.let(LocalDate::ofEpochDay),
            forwardedOnDayCount = prefs[FORWARDED_COUNT_KEY] ?: 0,
        )
    }

    /**
     * The connection timeline, oldest first. Its own flow rather than a snapshot field: the
     * snapshot is read by every tab on every DataStore write, and only Diagnostics needs the
     * decoded list, so only Diagnostics pays for decoding it, and only when it changes.
     */
    val events: Flow<List<DiagnosticEvent>> = dataStore.data
        .map { it[EVENTS_KEY] }
        .distinctUntilChanged()
        .map(::decodeEvents)

    suspend fun recordConnected(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[LAST_CONNECTED_KEY] = atMs }
        Unit
    }

    /**
     * A transition to connected, called once per session (not per Ready emission). A session
     * that was never closed means the process died while connected and saw no disconnect, so
     * it is closed first, at the last moment the log knows about, rather than counted as
     * connected through the whole time the app wasn't running.
     */
    suspend fun recordSessionStarted(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            val events = decodeEvents(prefs[EVENTS_KEY])
            val lastLink = events.lastOrNull { it.kind != DiagnosticEventKind.SYNC_COMPLETED }
            val added = when (lastLink?.kind) {
                null -> listOf(DiagnosticEvent(atMs, DiagnosticEventKind.CONNECTED))
                DiagnosticEventKind.DISCONNECTED -> listOf(DiagnosticEvent(atMs, DiagnosticEventKind.RECONNECTED))
                else -> listOf(
                    DiagnosticEvent(events.last().atMs, DiagnosticEventKind.DISCONNECTED, APP_STOPPED),
                    DiagnosticEvent(atMs, DiagnosticEventKind.RECONNECTED),
                )
            }
            appendEvents(prefs, *added.toTypedArray())
        }
        Unit
    }

    /** A transition away from connected. Ignored when no session is open, so it is idempotent. */
    suspend fun recordDisconnected(atMs: Long, detail: String? = null): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            val lastLink = decodeEvents(prefs[EVENTS_KEY]).lastOrNull { it.kind != DiagnosticEventKind.SYNC_COMPLETED }
            if (lastLink == null || lastLink.kind == DiagnosticEventKind.DISCONNECTED) return@edit
            appendEvents(prefs, DiagnosticEvent(atMs, DiagnosticEventKind.DISCONNECTED, detail))
        }
        Unit
    }

    /** One edit, so no reader ever sees the new sync time beside the old failure. */
    suspend fun recordSyncSucceeded(atMs: Long, detail: String? = null): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[LAST_SYNC_KEY] = atMs
            prefs[LAST_SYNC_ATTEMPT_KEY] = atMs
            prefs.remove(LAST_SYNC_ERROR_KEY)
            appendEvents(prefs, DiagnosticEvent(atMs, DiagnosticEventKind.SYNC_COMPLETED, detail))
        }
        Unit
    }

    /** [reason] is a short sentence for the user, never payload data (§9.5). */
    suspend fun recordSyncFailed(atMs: Long, reason: String): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[LAST_SYNC_ATTEMPT_KEY] = atMs
            prefs[LAST_SYNC_ERROR_KEY] = reason
        }
        Unit
    }

    suspend fun recordForwarded(atMs: Long, zone: ZoneId = ZoneId.systemDefault()): Unit = withContext(dispatchers.io) {
        val day = Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().toEpochDay()
        dataStore.edit { prefs ->
            prefs[LAST_NOTIFICATION_KEY] = atMs
            prefs[FORWARDED_COUNT_KEY] = if (prefs[FORWARDED_DAY_KEY] == day) (prefs[FORWARDED_COUNT_KEY] ?: 0) + 1 else 1
            prefs[FORWARDED_DAY_KEY] = day
        }
        Unit
    }

    private fun appendEvents(prefs: MutablePreferences, vararg added: DiagnosticEvent) {
        val events = decodeEvents(prefs[EVENTS_KEY]) + added
        prefs[EVENTS_KEY] = json.encodeToString(events.takeLast(MAX_EVENTS))
    }

    private fun decodeEvents(raw: String?): List<DiagnosticEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<DiagnosticEvent>>(raw) }.getOrDefault(emptyList())
    }

    internal companion object {
        const val MAX_EVENTS = 100
        const val APP_STOPPED = "app was stopped"

        val LAST_CONNECTED_KEY = longPreferencesKey("last_connected_at")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync_at")
        val LAST_SYNC_ATTEMPT_KEY = longPreferencesKey("last_sync_attempt_at")
        val LAST_SYNC_ERROR_KEY = stringPreferencesKey("last_sync_error")
        val LAST_NOTIFICATION_KEY = longPreferencesKey("last_notification_forwarded_at")
        val FORWARDED_DAY_KEY = longPreferencesKey("forwarded_day")
        val FORWARDED_COUNT_KEY = intPreferencesKey("forwarded_count")
        val EVENTS_KEY = stringPreferencesKey("connection_events")
    }
}
