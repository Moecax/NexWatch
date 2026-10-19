package com.nexwatch.feature.watch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.goals.GoalPrefs
import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.MinuteWindow
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchAlarm
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchapi.WristSide
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSettingsViewModelTest {

    private val goals = GoalPrefs(
        FakePreferencesDataStore(),
        // Resolved per call: Main is only the test dispatcher once setUp has run.
        object : CoroutineDispatchers {
            override val io get() = Dispatchers.Main
            override val default get() = Dispatchers.Main
        },
    )

    private val profile = UserProfile(sex = UserProfile.Sex.MALE, age = 30, heightCm = 175, weightKg = 70)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun readyClient() = FakeWatchClient().also { it.bind("AA:BB:CC:DD:EE:FF", profile) }

    @Test
    fun `refresh shows what the watch reports`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient(), goals)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.settings?.alarms?.isEmpty() == true)
    }

    @Test
    fun `a saved change comes back as the value the watch reports`() = runTest {
        val client = readyClient()
        val viewModel = WatchSettingsViewModel(client, goals)
        viewModel.uiState.launchIn(backgroundScope)
        val dnd = DoNotDisturb(allDay = false, scheduled = true, window = MinuteWindow(23 * 60, 6 * 60))

        viewModel.apply(WatchSettingChange.SetDoNotDisturb(dnd))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(dnd, state.settings?.doNotDisturb)
        assertEquals(Notice.Saved("Saved"), state.notice)
        assertEquals(false, state.isBusy)
    }

    @Test
    fun `a refused write surfaces its reason and leaves the screen usable`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient(), goals)
        viewModel.uiState.launchIn(backgroundScope)
        val tooMany = (0..5).map { WatchAlarm(WatchAlarm.NEW_ID, 7, it, emptySet(), true, "") }

        viewModel.apply(WatchSettingChange.SetAlarms(tooMany))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.notice is Notice.Failed)
        assertEquals(false, state.isBusy)
    }

    @Test
    fun `settings are hidden again once the watch is no longer ready`() = runTest {
        val client = readyClient()
        val viewModel = WatchSettingsViewModel(client, goals)
        viewModel.uiState.launchIn(backgroundScope)
        viewModel.refresh()
        advanceUntilIdle()

        client.unbind(keepWatchData = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.settings)
        assertEquals(false, viewModel.uiState.value.isReady)
    }

    @Test
    fun `reading the watch keeps the app's step goal in step with it`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient(), goals)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(8_000, goals.stepGoal.first())
    }

    @Test
    fun `a saved step goal reaches both the watch and the app`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient(), goals)
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.apply(WatchSettingChange.SetStepGoal(12_500))
        advanceUntilIdle()

        assertEquals(12_500, viewModel.uiState.value.settings?.stepGoal)
        assertEquals(12_500, goals.stepGoal.first())
    }

    @Test
    fun `a failed read is reported as a read, not a save, and leaves the watch connected`() = runTest {
        val client = readyClient()
        val viewModel = WatchSettingsViewModel(client, goals)
        viewModel.uiState.launchIn(backgroundScope)
        client.nextReadError = IllegalStateException("The watch didn't answer")

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Notice.Failed("The watch didn't answer", what = "read"), state.notice)
        assertNull(state.settings)
        assertEquals(false, state.isBusy)
        assertEquals(true, state.isReady)
    }

    @Test
    fun `a retry after a failed read lands the settings`() = runTest {
        val client = readyClient()
        val viewModel = WatchSettingsViewModel(client, goals)
        viewModel.uiState.launchIn(backgroundScope)
        client.nextReadError = IllegalStateException("The watch didn't answer")
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.notice)
        assertEquals(WristSide.LEFT, viewModel.uiState.value.settings?.wristSide)
    }

    @Test
    fun `dismissing clears the notice`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient(), goals)
        viewModel.uiState.launchIn(backgroundScope)
        viewModel.findWatch()
        advanceUntilIdle()

        viewModel.dismissNotice()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.notice)
    }
}
