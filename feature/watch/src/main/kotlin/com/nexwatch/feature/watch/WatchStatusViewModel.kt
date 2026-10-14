package com.nexwatch.feature.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.data.sync.WatchSync
import com.nexwatch.core.data.sync.WatchSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WatchStatusUiState(
    /** The last four hex digits of the Bluetooth address, enough to tell two watches apart. */
    val idSuffix: String? = null,
    val syncing: Boolean = false,
    val lastSyncMs: Long? = null,
    val lastConnectedMs: Long? = null,
    /** Why the most recent sync attempt failed, and only while nothing has resolved it since. */
    val lastSyncError: String? = null,
)

/** The phone-side facts in the Watch tab's header, kept apart from the watch's own settings. */
@HiltViewModel
class WatchStatusViewModel @Inject constructor(
    identityStore: WatchIdentityStore,
    private val watchSync: WatchSync,
    diagnostics: DiagnosticsStore,
) : ViewModel() {

    val uiState: StateFlow<WatchStatusUiState> = combine(identityStore.identity, watchSync.status, diagnostics.snapshot) { identity, sync, diag ->
        WatchStatusUiState(
            idSuffix = identity.boundAddress?.filter { it != ':' }?.takeLast(4),
            syncing = sync is WatchSyncStatus.Running,
            lastSyncMs = diag.lastSyncAt,
            lastConnectedMs = diag.lastConnectedAt,
            lastSyncError = diag.unresolvedSyncError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchStatusUiState())

    fun syncNow() = watchSync.requestSync()
}
