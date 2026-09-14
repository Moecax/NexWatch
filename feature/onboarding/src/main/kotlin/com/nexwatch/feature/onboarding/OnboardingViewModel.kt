package com.nexwatch.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.watchapi.WatchClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val watchClient: WatchClient,
    private val watchIdentityStore: WatchIdentityStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.GetStarted -> goTo(OnboardingStep.Profile)

            is OnboardingEvent.SexChanged -> updateProfile { copy(sex = event.sex) }
            is OnboardingEvent.AgeChanged -> updateProfile { copy(age = event.age) }
            is OnboardingEvent.HeightChanged -> updateProfile { copy(heightCm = event.heightCm) }
            is OnboardingEvent.WeightChanged -> updateProfile { copy(weightKg = event.weightKg) }
            OnboardingEvent.ProfileContinue -> goTo(OnboardingStep.Permissions)

            is OnboardingEvent.PermissionResult -> setPermission(
                event.item,
                if (event.granted) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED,
            )
            is OnboardingEvent.PermissionSkipped -> setPermission(event.item, PermissionStatus.SKIPPED)
            OnboardingEvent.PermissionsContinue -> goTo(OnboardingStep.FindWatch)

            OnboardingEvent.StartScan -> startScan()
            is OnboardingEvent.DeviceSelected -> {
                _uiState.update { it.copy(selectedDevice = event.device, step = OnboardingStep.PairConfirm) }
            }
            OnboardingEvent.BackToFindWatch -> goTo(OnboardingStep.FindWatch)

            OnboardingEvent.BindUnderstoodToggled -> _uiState.update {
                it.copy(bindUnderstoodChecked = !it.bindUnderstoodChecked)
            }
            OnboardingEvent.ConfirmPair -> confirmPair()
            OnboardingEvent.RetryPairing -> confirmPair()
            OnboardingEvent.PairingContinue -> goTo(OnboardingStep.KeepRunning)
            OnboardingEvent.FinishOnboarding -> Unit // :app observes WatchIdentityStore.isBound to leave onboarding
        }
    }

    private fun goTo(step: OnboardingStep) = _uiState.update { it.copy(step = step) }

    private fun updateProfile(transform: ProfileInput.() -> ProfileInput) =
        _uiState.update { it.copy(profile = it.profile.transform()) }

    private fun setPermission(item: PermissionItem, status: PermissionStatus) = _uiState.update {
        it.copy(permissions = it.permissions + (item to status))
    }

    /**
     * WatchClient has no discovery method (bind()/login() take an address directly) — real
     * discovery is Companion Device Manager, landing in Phase 5. This simulates a short,
     * fixed device list so the Find-your-watch screen has something to select from.
     */
    private fun startScan() {
        // TODO(phase-5): scanTimedOut is currently unreachable — this simulated scan always
        // "succeeds", so FindWatchScreen's timeout branch has no driver yet. Wire it once real
        // Companion Device Manager discovery replaces this fixed device list.
        _uiState.update { it.copy(isScanning = true, scanTimedOut = false, discoveredDevices = emptyList()) }
        viewModelScope.launch {
            delay(SCAN_RESULT_DELAY_MS)
            val device = DiscoveredDevice(address = FAKE_ADDRESS, displayName = "GTR 3 Pro", signalBars = 3)
            _uiState.update { it.copy(isScanning = false, discoveredDevices = listOf(device)) }
        }
    }

    private fun confirmPair() {
        val state = _uiState.value
        val device = state.selectedDevice ?: return
        if (!state.bindUnderstoodChecked) return
        viewModelScope.launch { runPairing(device.address, state.profile.toUserProfile()) }
    }

    private suspend fun runPairing(address: String, profile: com.nexwatch.core.watchapi.UserProfile) {
        _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.CONNECTING), pairingError = null) }
        try {
            watchIdentityStore.ensureUserId()
            watchClient.bind(address, profile)

            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.AUTHENTICATING)) }
            delay(PHASE_STEP_DELAY_MS)

            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.READING_FEATURES)) }
            watchClient.capabilities.filterNotNull().first()

            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.FIRST_SYNC)) }
            watchClient.syncHealthData().collect { }

            watchIdentityStore.markBound(address)
            val battery = watchClient.batteryLevel()
            _uiState.update {
                it.copy(
                    step = OnboardingStep.Pairing(PairingPhase.SUCCESS),
                    pairedBattery = battery,
                    pairedFirmwareVersion = watchClient.capabilities.value?.firmwareVersion,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    step = OnboardingStep.Pairing(PairingPhase.FAILED),
                    pairingError = e.message ?: "Pairing failed",
                )
            }
        }
    }

    private companion object {
        const val FAKE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val SCAN_RESULT_DELAY_MS = 400L
        const val PHASE_STEP_DELAY_MS = 300L
    }
}
