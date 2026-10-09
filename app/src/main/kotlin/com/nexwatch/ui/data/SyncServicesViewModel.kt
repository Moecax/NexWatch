package com.nexwatch.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.syncengine.SyncProviderStatus
import com.nexwatch.core.data.syncengine.SyncRepository
import com.nexwatch.core.sync.healthconnect.HealthConnectAudit
import com.nexwatch.core.sync.healthconnect.HealthConnectAuditRow
import com.nexwatch.core.syncapi.Readiness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuditState {
    data object Idle : AuditState
    data object Running : AuditState
    data class Done(val rows: List<HealthConnectAuditRow>) : AuditState
    data class Failed(val message: String) : AuditState
}

@HiltViewModel
class SyncServicesViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val audit: HealthConnectAudit,
) : ViewModel() {

    val statuses: StateFlow<List<SyncProviderStatus>> = syncRepository.providerStatuses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _readiness = MutableStateFlow<Map<String, Readiness>>(emptyMap())
    val readiness: StateFlow<Map<String, Readiness>> = _readiness.asStateFlow()

    private val _audit = MutableStateFlow<AuditState>(AuditState.Idle)
    val auditState: StateFlow<AuditState> = _audit.asStateFlow()

    /** Permissions can change behind the app's back in system settings, so this re-runs on every resume. */
    fun refreshReadiness() = viewModelScope.launch {
        val ids = syncRepository.providerStatuses.first().map { it.id }
        _readiness.value = ids.associateWith { syncRepository.readiness(it) }
    }

    fun setEnabled(providerId: String, enabled: Boolean) = viewModelScope.launch {
        syncRepository.setEnabled(providerId, enabled)
        refreshReadiness()
    }

    fun syncNow(providerId: String) = syncRepository.syncNow(providerId)

    fun resendAll(providerId: String) = viewModelScope.launch { syncRepository.resendAll(providerId) }

    fun runAudit() = viewModelScope.launch {
        _audit.value = AuditState.Running
        _audit.value = runCatching { audit.run() }
            .fold({ AuditState.Done(it) }, { AuditState.Failed(it.message ?: it::class.simpleName.orEmpty()) })
    }
}
