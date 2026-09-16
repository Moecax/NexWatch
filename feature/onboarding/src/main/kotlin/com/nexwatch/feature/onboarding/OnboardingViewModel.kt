package com.nexwatch.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.watchapi.DiscoveredWatch
import com.nexwatch.core.watchapi.WatchClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val watchClient: WatchClient,
    private val watchIdentityStore: WatchIdentityStore,
) : ViewModel() {

    private var scanJob: Job? = null

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
                scanJob?.cancel()
                _uiState.update {
                    it.copy(selectedDevice = event.device, isScanning = false, step = OnboardingStep.PairConfirm)
                }
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
     * Results stream in for as long as the collector lives, so the window is bounded here
     * rather than by the SDK alone, and the job is cancelled the moment the user leaves the
     * screen — §9.2: nothing scans outside the pairing flow.
     */
    private fun startScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, scanTimedOut = false, discoveredDevices = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(SCAN_WINDOW_MS) {
                    watchClient.discoverWatches().collect(::onWatchDiscovered)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A scan that can't start (Bluetooth off, permission revoked between screens)
                // reads the same to the user as one that found nothing: try again.
                _uiState.update { it.copy(scanError = e.message) }
            }
            _uiState.update {
                it.copy(isScanning = false, scanTimedOut = it.discoveredDevices.isEmpty())
            }
        }
    }

    /** The same watch advertises repeatedly; keep one row per address, at its best signal. */
    private fun onWatchDiscovered(watch: DiscoveredWatch) = _uiState.update { state ->
        val found = DiscoveredDevice(
            address = watch.address,
            displayName = watch.name.ifBlank { "Unnamed watch" },
            signalBars = signalBars(watch.rssi),
        )
        val existing = state.discoveredDevices.indexOfFirst { it.address == found.address }
        val devices = when {
            existing < 0 -> state.discoveredDevices + found
            found.signalBars > state.discoveredDevices[existing].signalBars ->
                state.discoveredDevices.toMutableList().apply { this[existing] = found }
            else -> return@update state
        }
        state.copy(discoveredDevices = devices)
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
            // Persisted before the connect, so a crash mid-pairing still leaves enough for
            // WatchAutoConnect to retry in LOGIN mode rather than re-running a wipe.
            watchIdentityStore.saveProfile(profile)
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
            // The connector keeps its own retry loop running after a failed attempt (it owns
            // reconnection, §4.3). Nobody is watching it once pairing has visibly failed, so
            // close it rather than leave the radio working — keepWatchData, because a failed
            // bind must not reach for the watch's data on the way out.
            runCatching { watchClient.unbind(keepWatchData = true) }
            _uiState.update {
                it.copy(
                    step = OnboardingStep.Pairing(PairingPhase.FAILED),
                    pairingError = e.message ?: "Pairing failed",
                )
            }
        }
    }

    /** Three bars, because that's all FindWatchScreen renders. */
    private fun signalBars(rssi: Int): Int = when {
        rssi >= STRONG_RSSI -> 3
        rssi >= FAIR_RSSI -> 2
        else -> 1
    }

    private companion object {
        const val SCAN_WINDOW_MS = 20_000L
        const val STRONG_RSSI = -60
        const val FAIR_RSSI = -75
        const val PHASE_STEP_DELAY_MS = 300L
    }
}
