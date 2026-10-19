package com.nexwatch.core.data.goals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data get() = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class GoalPrefsTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `a watch that has never reported a goal gets the usual ten thousand`() = runTest(dispatcher) {
        assertEquals(DEFAULT_STEP_GOAL, GoalPrefs(FakePreferencesDataStore(), dispatchers).stepGoal.first())
    }

    @Test
    fun `a stored goal replaces the default`() = runTest(dispatcher) {
        val prefs = GoalPrefs(FakePreferencesDataStore(), dispatchers)

        prefs.setStepGoal(7_500)

        assertEquals(7_500, prefs.stepGoal.first())
    }
}
