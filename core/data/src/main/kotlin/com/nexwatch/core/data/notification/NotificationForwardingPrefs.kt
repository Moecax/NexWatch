package com.nexwatch.core.data.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.NotificationForwardingDataStore
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettings
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettingsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the §8.5 filter pipeline's master switch and source allowlist. The default
 * allowlist covers the messaging apps §8.5 names explicitly (WhatsApp, Telegram, SMS);
 * everything else starts opted out until the user adds it — a companion-device app that
 * forwards every notification by default is surprising, not helpful.
 */
class NotificationForwardingPrefs @Inject constructor(
    @NotificationForwardingDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) : NotificationForwardingSettingsProvider {
    override val settings: Flow<NotificationForwardingSettings> = dataStore.data.map { prefs ->
        NotificationForwardingSettings(
            enabled = prefs[ENABLED_KEY] ?: true,
            allowedPackages = prefs[ALLOWED_PACKAGES_KEY] ?: DEFAULT_ALLOWED_PACKAGES,
        )
    }

    suspend fun setEnabled(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
        Unit
    }

    suspend fun setAllowedPackages(packages: Set<String>): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[ALLOWED_PACKAGES_KEY] = packages }
        Unit
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("forwarding_enabled")
        val ALLOWED_PACKAGES_KEY = stringSetPreferencesKey("allowed_packages")
        val DEFAULT_ALLOWED_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )
    }
}
