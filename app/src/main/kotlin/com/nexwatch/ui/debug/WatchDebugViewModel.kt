package com.nexwatch.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchfake.WatchDebugController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class WatchDebugViewModel @Inject constructor(
    watchClient: WatchClient,
    private val debugController: WatchDebugController,
) : ViewModel() {

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
