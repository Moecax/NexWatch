package com.nexwatch.feature.watch

import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.service.AssociationResult
import com.nexwatch.core.service.CompanionAssociator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PresenceUiState(
    /** False below API 33, without the system feature, or with no bound watch: the row is hidden. */
    val available: Boolean = false,
    val associated: Boolean = false,
    val busy: Boolean = false,
    val failure: String? = null,
)

private data class PresenceTransient(val busy: Boolean = false, val failure: String? = null, val checks: Int = 0)

/**
 * §8.3 for a watch that was paired before onboarding launched the consent dialog: without an
 * association, CompanionPresenceService never runs.
 */
@HiltViewModel
class CompanionPresenceViewModel @Inject constructor(
    private val associator: CompanionAssociator,
    private val identityStore: WatchIdentityStore,
) : ViewModel() {

    private val transient = MutableStateFlow(PresenceTransient())

    private val consent = Channel<IntentSender>(Channel.BUFFERED)
    val consentRequests: Flow<IntentSender> = consent.receiveAsFlow()

    // `checks` only exists to re-run this: the association list lives in the system, and
    // nothing notifies the app when it changes.
    val uiState: StateFlow<PresenceUiState> = combine(identityStore.identity, transient) { identity, t ->
        val address = identity.boundAddress?.takeIf { identity.isBound }
        PresenceUiState(
            available = associator.isAvailable && address != null,
            associated = address != null && associator.isAssociated(address),
            busy = t.busy,
            failure = t.failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresenceUiState())

    fun enable() {
        if (transient.value.busy || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        viewModelScope.launch {
            val address = boundAddress() ?: return@launch
            transient.update { it.copy(busy = true, failure = null) }
            when (val result = associator.associate(address)) {
                AssociationResult.Associated -> transient.update { it.copy(busy = false, checks = it.checks + 1) }
                is AssociationResult.NeedsConsent -> consent.send(result.intentSender)
                is AssociationResult.Failed -> transient.update { it.copy(busy = false, failure = result.reason) }
            }
        }
    }

    fun onConsentResult(accepted: Boolean) {
        viewModelScope.launch {
            if (accepted) boundAddress()?.let(associator::ensureObserving)
            transient.update {
                it.copy(
                    busy = false,
                    failure = if (accepted) null else "Not allowed. NexWatch still reconnects when it's open or after a restart.",
                    checks = it.checks + 1,
                )
            }
        }
    }

    private suspend fun boundAddress(): String? =
        identityStore.identity.first().let { identity -> identity.boundAddress?.takeIf { identity.isBound } }
}
