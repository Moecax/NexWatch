package com.nexwatch.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.service.notification.ServiceNotifications
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §8.2. Keeps the process alive so the SDK connection, notification forwarding and
 * telephony survive the app being swiped away. Starts in LOGIN mode only — BIND stays
 * confined to the guarded onboarding flow.
 */
@AndroidEntryPoint
class WatchConnectionService : Service() {

    @Inject lateinit var watchClient: WatchClient
    @Inject lateinit var identityStore: WatchIdentityStore

    private val scope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ServiceNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            ServiceNotifications.build(this, "Connecting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        ensureLoggedIn()
        observeState()
        observeEvents()
        return START_STICKY
    }

    private fun ensureLoggedIn() {
        scope.launch {
            val identity = identityStore.identity.first()
            val address = identity.boundAddress
            val profile = identity.profile
            if (identity.isBound && address != null && profile != null) {
                runCatching { watchClient.login(address, profile) }
            }
        }
    }

    private fun observeState() {
        scope.launch {
            watchClient.state.collectLatest { state ->
                val text = state.toStatusText()
                startForeground(
                    NOTIFICATION_ID,
                    ServiceNotifications.build(this@WatchConnectionService, text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
                if (state is WatchState.Ready) {
                    watchClient.notifyPhoneStatePermissionGranted()
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            watchClient.events.collectLatest { event ->
                when (event) {
                    WatchEvent.FindPhoneRequested -> FindPhoneRinger.ring(this@WatchConnectionService)
                    WatchEvent.HangUpRequested -> CallHangUp.tryEndCall(this@WatchConnectionService)
                    WatchEvent.CameraOpenRequested, WatchEvent.CameraCloseRequested -> {
                        // Phase 8 (§12): camera remote is a watch-control settings feature.
                        // Nothing to do here yet — deliberately not stubbed further.
                    }
                }
            }
        }
    }

    private fun WatchState.toStatusText(): String = when (this) {
        is WatchState.Ready -> battery?.let { "Connected · $it%" } ?: "Connected"
        is WatchState.Connecting -> "Connecting…"
        is WatchState.Waiting -> "Waiting to reconnect…"
        is WatchState.BluetoothOff -> "Bluetooth off"
        is WatchState.Unbound -> "Not paired"
        is WatchState.AuthFailed -> "Connection failed"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchConnectionService::class.java))
        }
    }
}
