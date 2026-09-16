package com.nexwatch.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.watchapi.UserProfile
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
            profile = prefs.readProfile(),
        )
    }

    private fun Preferences.readProfile(): UserProfile? {
        val age = this[PROFILE_AGE_KEY] ?: return null
        return UserProfile(
            sex = if (this[PROFILE_IS_MALE_KEY] != false) UserProfile.Sex.MALE else UserProfile.Sex.FEMALE,
            age = age,
            heightCm = this[PROFILE_HEIGHT_KEY] ?: return null,
            weightKg = this[PROFILE_WEIGHT_KEY] ?: return null,
        )
    }

    suspend fun saveProfile(profile: UserProfile): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[PROFILE_IS_MALE_KEY] = profile.sex == UserProfile.Sex.MALE
            prefs[PROFILE_AGE_KEY] = profile.age
            prefs[PROFILE_HEIGHT_KEY] = profile.heightCm
            prefs[PROFILE_WEIGHT_KEY] = profile.weightKg
        }
        Unit
    }

    suspend fun ensureUserId(): String = withContext(dispatchers.io) {
        val existing = dataStore.data.first()[USER_ID_KEY]
        if (existing != null) return@withContext existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[USER_ID_KEY] = generated }
        generated
    }

    suspend fun markBound(address: String): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[BOUND_ADDRESS_KEY] = address
            prefs[IS_BOUND_KEY] = true
        }
        Unit
    }

    suspend fun markUnbound(keepAddress: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[IS_BOUND_KEY] = false
            if (!keepAddress) prefs.remove(BOUND_ADDRESS_KEY)
        }
        Unit
    }

    private companion object {
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val BOUND_ADDRESS_KEY = stringPreferencesKey("bound_address")
        val IS_BOUND_KEY = booleanPreferencesKey("is_bound")
        val PROFILE_IS_MALE_KEY = booleanPreferencesKey("profile_is_male")
        val PROFILE_AGE_KEY = intPreferencesKey("profile_age")
        val PROFILE_HEIGHT_KEY = intPreferencesKey("profile_height_cm")
        val PROFILE_WEIGHT_KEY = intPreferencesKey("profile_weight_kg")
    }
}
