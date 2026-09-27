package com.nexwatch.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.BackupDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupSettings(val enabled: Boolean, val folderUri: String?, val keepCount: Int)

/** Backs §6.4's optional scheduled auto-backup: the user-picked SAF folder and how many files to keep. */
class BackupPrefs @Inject constructor(
    @BackupDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val settings: Flow<BackupSettings> = dataStore.data.map { prefs ->
        BackupSettings(
            enabled = prefs[ENABLED_KEY] ?: false,
            folderUri = prefs[FOLDER_URI_KEY],
            keepCount = prefs[KEEP_COUNT_KEY] ?: DEFAULT_KEEP_COUNT,
        )
    }

    suspend fun setFolder(uri: String): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[FOLDER_URI_KEY] = uri }
        Unit
    }

    suspend fun setEnabled(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
        Unit
    }

    suspend fun setKeepCount(count: Int): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[KEEP_COUNT_KEY] = count }
        Unit
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("backup_enabled")
        val FOLDER_URI_KEY = stringPreferencesKey("backup_folder_uri")
        val KEEP_COUNT_KEY = intPreferencesKey("backup_keep_count")
        const val DEFAULT_KEEP_COUNT = 5
    }
}
