package com.nexwatch.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Minimal in-memory Preferences DataStore — same shape as the fake used in WatchIdentityStoreTest. */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** CoroutineDispatchers is an interface (`:core:common`) with no test fake shipped yet — this is it. */
private object UnconfinedDispatchers : CoroutineDispatchers {
    override val io = Dispatchers.Unconfined
    override val default = Dispatchers.Unconfined
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    /**
     * viewModelScope needs a Main dispatcher; unit tests have none by default. It must share
     * runTest's own scheduler (passed to runTest below), otherwise delay() inside
     * viewModelScope coroutines runs against an unrelated virtual clock that nothing advances.
     */
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() = Dispatchers.setMain(mainDispatcher)

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel(client: FakeWatchClient = FakeWatchClient()) =
        OnboardingViewModel(client, WatchIdentityStore(InMemoryPreferencesDataStore(), UnconfinedDispatchers)) to client

    @Test
    fun `starts on Welcome`() = runTest(mainDispatcher) {
        val (viewModel, _) = viewModel()
        assertEquals(OnboardingStep.Welcome, viewModel.uiState.value.step)
    }

    @Test
    fun `Get started advances to Profile`() = runTest(mainDispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        assertEquals(OnboardingStep.Profile, viewModel.uiState.value.step)
    }

    @Test
    fun `profile continue advances to Permissions with edited values retained`() = runTest(mainDispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.SexChanged(UserProfile.Sex.FEMALE))
        viewModel.onEvent(OnboardingEvent.AgeChanged(41))
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.Permissions, state.step)
        assertEquals(UserProfile.Sex.FEMALE, state.profile.sex)
        assertEquals(41, state.profile.age)
    }

    @Test
    fun `permissions continue advances to FindWatch and starting a scan populates devices`() = runTest(mainDispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        assertEquals(OnboardingStep.FindWatch, viewModel.uiState.value.step)
        viewModel.onEvent(OnboardingEvent.StartScan)
        advanceUntilIdle() // resolve the simulated scan delay on the shared test scheduler
        val state = viewModel.uiState.value
        assertTrue(state.discoveredDevices.isNotEmpty())
    }

    @Test
    fun `selecting a device and confirming requires the understood checkbox`() = runTest(mainDispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        viewModel.onEvent(OnboardingEvent.StartScan)
        advanceUntilIdle() // resolve the simulated scan delay on the shared test scheduler
        val device = viewModel.uiState.value.discoveredDevices.first()
        viewModel.onEvent(OnboardingEvent.DeviceSelected(device))
        assertEquals(OnboardingStep.PairConfirm, viewModel.uiState.value.step)

        viewModel.onEvent(OnboardingEvent.ConfirmPair)
        assertEquals(OnboardingStep.PairConfirm, viewModel.uiState.value.step) // unchanged: checkbox not ticked

        viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled)
        viewModel.onEvent(OnboardingEvent.ConfirmPair)
        assertTrue(viewModel.uiState.value.step is OnboardingStep.Pairing)
    }

    @Test
    fun `confirming pair drives PairingPhase through to SUCCESS against the fake client`() = runTest(mainDispatcher) {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        viewModel.onEvent(OnboardingEvent.StartScan)
        advanceUntilIdle() // resolve the simulated scan delay on the shared test scheduler
        val device = viewModel.uiState.value.discoveredDevices.first()
        viewModel.onEvent(OnboardingEvent.DeviceSelected(device))
        viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled)

        viewModel.uiState.test {
            skipItems(1) // current PairConfirm state before the event below
            viewModel.onEvent(OnboardingEvent.ConfirmPair)
            // Every intermediate phase is emitted in order, ending on SUCCESS.
            val phases = mutableListOf<PairingPhase>()
            while (phases.lastOrNull() != PairingPhase.SUCCESS) {
                val step = awaitItem().step
                if (step is OnboardingStep.Pairing) phases += step.phase
            }
            assertEquals(
                listOf(
                    PairingPhase.CONNECTING,
                    PairingPhase.AUTHENTICATING,
                    PairingPhase.READING_FEATURES,
                    PairingPhase.FIRST_SYNC,
                    PairingPhase.SUCCESS,
                ),
                phases,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishing onboarding continues to KeepRunning then completes`() = runTest(mainDispatcher) {
        val (viewModel, client) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        viewModel.onEvent(OnboardingEvent.StartScan)
        advanceUntilIdle() // resolve the simulated scan delay on the shared test scheduler
        val device = viewModel.uiState.value.discoveredDevices.first()
        viewModel.onEvent(OnboardingEvent.DeviceSelected(device))
        viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled)

        viewModel.uiState.test {
            skipItems(1) // current PairConfirm state before the event below
            viewModel.onEvent(OnboardingEvent.ConfirmPair)
            while (awaitItem().step != OnboardingStep.Pairing(PairingPhase.SUCCESS)) { /* let coroutines resolve */ }
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.onEvent(OnboardingEvent.PairingContinue)
        assertEquals(OnboardingStep.KeepRunning, viewModel.uiState.value.step)
        assertTrue(client.state.value is com.nexwatch.core.watchapi.WatchState.Ready)
    }
}
