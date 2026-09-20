package com.nexwatch.core.data.diagnostics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.DiagnosticsDataStore
import com.nexwatch.core.watchapi.notification.NotificationForwardedRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiagnosticsSnapshot(
    val lastConnectedAt: Long?,
    val lastSyncAt: Long?,
    val lastNotificationForwardedAt: Long?,
)

/** §8.4's "debug screen showing last connected, last sync, last notification forwarded." */
class DiagnosticsStore @Inject constructor(
    @DiagnosticsDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) : NotificationForwardedRecorder {
    val snapshot: Flow<DiagnosticsSnapshot> = dataStore.data.map { prefs ->
        DiagnosticsSnapshot(
            lastConnectedAt = prefs[LAST_CONNECTED_KEY],
            lastSyncAt = prefs[LAST_SYNC_KEY],
            lastNotificationForwardedAt = prefs[LAST_NOTIFICATION_KEY],
        )
    }

    suspend fun recordConnected(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[LAST_CONNECTED_KEY] = atMs }
        Unit
    }

    override suspend fun recordForwarded(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[LAST_NOTIFICATION_KEY] = atMs }
        Unit
    }

    private companion object {
        val LAST_CONNECTED_KEY = longPreferencesKey("last_connected_at")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync_at")
        val LAST_NOTIFICATION_KEY = longPreferencesKey("last_notification_forwarded_at")
    }
}
