package com.nexwatch.core.data.syncengine

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.SyncDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Which providers the user has switched on. Progress lives in `sync_cursor`, not here. */
class SyncPrefs @Inject constructor(
    @SyncDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val enabledProviderIds: Flow<Set<String>> = dataStore.data.map { it[ENABLED_KEY].orEmpty() }

    suspend fun setEnabled(providerId: String, enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            val current = prefs[ENABLED_KEY].orEmpty()
            prefs[ENABLED_KEY] = if (enabled) current + providerId else current - providerId
        }
        Unit
    }

    private companion object {
        val ENABLED_KEY = stringSetPreferencesKey("enabled_provider_ids")
    }
}
