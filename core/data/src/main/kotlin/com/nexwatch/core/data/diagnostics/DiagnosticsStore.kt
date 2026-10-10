package com.nexwatch.core.data.diagnostics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.DiagnosticsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DiagnosticsSnapshot(
    val lastConnectedAt: Long?,
    val lastSyncAt: Long?,
    val lastNotificationForwardedAt: Long?,
    /** The local day [forwardedOnDayCount] belongs to. Only one day is kept. */
    val forwardedDay: LocalDate? = null,
    val forwardedOnDayCount: Int = 0,
) {
    fun forwardedOn(day: LocalDate): Int = if (day == forwardedDay) forwardedOnDayCount else 0
}

/** §8.4's "debug screen showing last connected, last sync, last notification forwarded." */
class DiagnosticsStore @Inject constructor(
    @DiagnosticsDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val snapshot: Flow<DiagnosticsSnapshot> = dataStore.data.map { prefs ->
        DiagnosticsSnapshot(
            lastConnectedAt = prefs[LAST_CONNECTED_KEY],
            lastSyncAt = prefs[LAST_SYNC_KEY],
            lastNotificationForwardedAt = prefs[LAST_NOTIFICATION_KEY],
            forwardedDay = prefs[FORWARDED_DAY_KEY]?.let(LocalDate::ofEpochDay),
            forwardedOnDayCount = prefs[FORWARDED_COUNT_KEY] ?: 0,
        )
    }

    suspend fun recordConnected(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[LAST_CONNECTED_KEY] = atMs }
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

    private companion object {
        val LAST_CONNECTED_KEY = longPreferencesKey("last_connected_at")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync_at")
        val LAST_NOTIFICATION_KEY = longPreferencesKey("last_notification_forwarded_at")
        val FORWARDED_DAY_KEY = longPreferencesKey("forwarded_day")
        val FORWARDED_COUNT_KEY = intPreferencesKey("forwarded_count")
    }
}
