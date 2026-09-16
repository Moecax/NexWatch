package com.nexwatch.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchfake.WatchDebugController
import com.nexwatch.BuildConfig
import com.nexwatch.di.WatchImplPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class WatchDebugViewModel @Inject constructor(
    watchClient: WatchClient,
    private val debugController: WatchDebugController,
    private val watchImplPreference: WatchImplPreference,
) : ViewModel() {

    /** The §12 Phase 4 fake/real toggle. Debug builds only; release has no choice to offer. */
    val showImplToggle: Boolean = BuildConfig.DEBUG

    private val _useRealWatch = MutableStateFlow(watchImplPreference.useRealWatch)
    val useRealWatch: StateFlow<Boolean> = _useRealWatch.asStateFlow()

    /** Takes effect on the next process start; the client is a @Singleton already in use. */
    fun setUseRealWatch(enabled: Boolean) {
        watchImplPreference.useRealWatch = enabled
        _useRealWatch.value = enabled
    }

    val capabilities: StateFlow<WatchCapabilities?> = watchClient.capabilities.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val state: StateFlow<WatchState> = watchClient.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WatchState.Unbound,
    )

    val forceableStates: List<Pair<String, WatchState>> = listOf(
        "Unbound" to WatchState.Unbound,
        "Bluetooth off" to WatchState.BluetoothOff,
        "Waiting to retry" to WatchState.Waiting(nextRetryAt = Instant.now().plusSeconds(30)),
        "Connecting" to WatchState.Connecting,
        "Ready (72%)" to WatchState.Ready(battery = 72),
        "Auth failed" to WatchState.AuthFailed(reason = "simulated mismatch"),
    )

    fun forceState(state: WatchState) = debugController.forceState(state)
}
