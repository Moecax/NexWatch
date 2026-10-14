package com.nexwatch.core.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.SyncDataStore
import com.nexwatch.core.database.RawIngestDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * `blood_pressure_measure` rows have been arriving since the feature shipped, but the decoder
 * had no branch for them: they were marked processed while decoding to nothing, so no later run
 * had any reason to look at them again. Once per install they go back in the queue, and the same
 * run's normalisation decodes them properly.
 *
 * The flag is written only after markUnprocessed returns. An install that dies in between simply
 * repeats it on the next sync, which is safe: the rows only return to unprocessed, and re-inserting
 * a record that is already there is a no-op (deterministic ids on IGNORE inserts).
 */
class BpMeasureReprocess @Inject constructor(
    @SyncDataStore private val dataStore: DataStore<Preferences>,
    private val rawIngestDao: RawIngestDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun runOnce(): Unit = withContext(dispatchers.io) {
        if (dataStore.data.first()[DONE_KEY] == true) return@withContext
        rawIngestDao.markUnprocessed(BLOOD_PRESSURE_MEASURE)
        dataStore.edit { it[DONE_KEY] = true }
    }

    private companion object {
        val DONE_KEY = booleanPreferencesKey("journal_reprocess_bp_measure_v1")
        const val BLOOD_PRESSURE_MEASURE = "blood_pressure_measure"
    }
}
