package com.nexwatch.feature.watch

import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.MinuteWindow
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchAlarm
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSettingsViewModelTest {

    private val profile = UserProfile(sex = UserProfile.Sex.MALE, age = 30, heightCm = 175, weightKg = 70)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun readyClient() = FakeWatchClient().also { it.bind("AA:BB:CC:DD:EE:FF", profile) }

    @Test
    fun `refresh shows what the watch reports`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient())
        viewModel.uiState.launchIn(backgroundScope)

        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.settings?.alarms?.isEmpty() == true)
    }

    @Test
    fun `a saved change comes back as the value the watch reports`() = runTest {
        val client = readyClient()
        val viewModel = WatchSettingsViewModel(client)
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
        val viewModel = WatchSettingsViewModel(readyClient())
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
        val viewModel = WatchSettingsViewModel(client)
        viewModel.uiState.launchIn(backgroundScope)
        viewModel.refresh()
        advanceUntilIdle()

        client.unbind(keepWatchData = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.settings)
        assertEquals(false, viewModel.uiState.value.isReady)
    }

    @Test
    fun `dismissing clears the notice`() = runTest {
        val viewModel = WatchSettingsViewModel(readyClient())
        viewModel.uiState.launchIn(backgroundScope)
        viewModel.findWatch()
        advanceUntilIdle()

        viewModel.dismissNotice()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.notice)
    }
}
