package com.nexwatch.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Backs §4.4: userId is generated once and never changes; bind is guarded elsewhere
 * (the onboarding flow), this store only remembers the outcome so every later
 * connection can use LOGIN instead.
 */
class WatchIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val identity: Flow<WatchIdentity> = dataStore.data.map { prefs ->
        WatchIdentity(
            userId = prefs[USER_ID_KEY].orEmpty(),
            boundAddress = prefs[BOUND_ADDRESS_KEY],
            isBound = prefs[IS_BOUND_KEY] ?: false,
        )
    }

    suspend fun ensureUserId(): String = withContext(dispatchers.io) {
        val existing = dataStore.data.first()[USER_ID_KEY]
        if (existing != null) return@withContext existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[USER_ID_KEY] = generated }
        generated
    }

    suspend fun markBound(address: String) = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[BOUND_ADDRESS_KEY] = address
            prefs[IS_BOUND_KEY] = true
        }
    }

    suspend fun markUnbound(keepAddress: Boolean) = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[IS_BOUND_KEY] = false
            if (!keepAddress) prefs.remove(BOUND_ADDRESS_KEY)
        }
    }

    private companion object {
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val BOUND_ADDRESS_KEY = stringPreferencesKey("bound_address")
        val IS_BOUND_KEY = booleanPreferencesKey("is_bound")
    }
}
