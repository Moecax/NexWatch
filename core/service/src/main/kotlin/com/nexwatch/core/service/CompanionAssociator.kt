package com.nexwatch.core.service

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

sealed interface AssociationResult {
    data object Associated : AssociationResult

    /** The system wants the user to confirm; launch this and the association completes on OK. */
    data class NeedsConsent(val intentSender: IntentSender) : AssociationResult

    data class Failed(val reason: String) : AssociationResult
}

/**
 * §8.3: associates the bound watch with CompanionDeviceManager so [CompanionPresenceService]
 * wakes the app when the watch comes into range. The association only exists once the user
 * accepts the system consent dialog, so callers must launch [AssociationResult.NeedsConsent].
 *
 * API 33+ only: the executor `associate()` overload and `myAssociations` both start there, and
 * LOGIN reconnection from boot and launch already works without any of this.
 */
class CompanionAssociator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager: CompanionDeviceManager?
        get() = context.getSystemService(CompanionDeviceManager::class.java)

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    fun isAssociated(address: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isAvailable) return false
        return findAssociation(address) != null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun associate(address: String): AssociationResult {
        val manager = manager ?: return AssociationResult.Failed("Companion devices aren't supported on this phone")
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
            .setSingleDevice(true)
            .build()
        return suspendCancellableCoroutine { continuation ->
            manager.associate(
                request,
                Runnable::run,
                // The system keeps this callback after the coroutine has resumed with
                // NeedsConsent: onAssociationCreated arrives once the user accepts, which is
                // when presence observation can start.
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        if (continuation.isActive) continuation.resume(AssociationResult.NeedsConsent(intentSender))
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        startObserving(manager, associationInfo)
                        if (continuation.isActive) continuation.resume(AssociationResult.Associated)
                    }

                    override fun onFailure(error: CharSequence?) {
                        if (continuation.isActive) {
                            continuation.resume(AssociationResult.Failed(error?.toString() ?: "The system refused the association"))
                        }
                    }
                },
            )
        }
    }

    /**
     * After the consent dialog returns OK. The system also reports the new association to the
     * callback in [associate], but nothing guarantees which arrives first, and observing twice
     * is harmless.
     */
    fun ensureObserving(address: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = manager ?: return
        findAssociation(address)?.let { startObserving(manager, it) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun findAssociation(address: String): AssociationInfo? =
        manager?.myAssociations?.firstOrNull { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startObserving(manager: CompanionDeviceManager, association: AssociationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            manager.startObservingDevicePresence(
                ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build(),
            )
        } else {
            val address = association.deviceMacAddress?.toString() ?: return
            @Suppress("DEPRECATION")
            manager.startObservingDevicePresence(address)
        }
    }
}

/** For composables that need [CompanionAssociator] outside a ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CompanionAssociatorEntryPoint {
    fun companionAssociator(): CompanionAssociator
}
