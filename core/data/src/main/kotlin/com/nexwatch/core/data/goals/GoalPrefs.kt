package com.nexwatch.core.data.goals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.GoalsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

const val DEFAULT_STEP_GOAL = 10_000

/**
 * The app's copy of the step goal the watch holds. The watch stays the source of truth (a goal
 * changed on the watch itself wins), and this copy exists so Today and Health can draw against
 * it without a Bluetooth round trip, or with the watch out of range.
 */
class GoalPrefs @Inject constructor(
    @GoalsDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val stepGoal: Flow<Int> = dataStore.data.map { it[STEP_GOAL_KEY] ?: DEFAULT_STEP_GOAL }.distinctUntilChanged()

    suspend fun setStepGoal(steps: Int): Unit = withContext(dispatchers.io) {
        require(steps > 0) { "a step goal must be positive" }
        dataStore.edit { it[STEP_GOAL_KEY] = steps }
        Unit
    }

    private companion object {
        val STEP_GOAL_KEY = intPreferencesKey("step_goal")
    }
}
