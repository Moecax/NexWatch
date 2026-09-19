package com.nexwatch.core.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.service.notification.ServiceNotifications
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    @Inject lateinit var diagnosticsStore: DiagnosticsStore

    // A bare SupervisorJob still routes an uncaught child exception to the thread's default
    // handler (it only isolates siblings from each other), which would otherwise kill the
    // process this service exists to keep alive.
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> stopSelf() }
    private val scope = CoroutineScope(SupervisorJob() + exceptionHandler)

    // onStartCommand re-enters on every startForegroundService() call (relaunch from AppRoot,
    // CompanionPresenceService, BootReceiver, or START_STICKY redelivery). Without this guard
    // each re-entry launches another set of collectors, so a single FindPhoneRequested would
    // ring the phone once per prior start.
    private var observersStarted = false

    override fun onCreate() {
        super.onCreate()
        ServiceNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(ServiceNotifications.build(this, "Connecting…"))
        if (!observersStarted) {
            observersStarted = true
            ensureLoggedIn()
            observeState()
            observeEvents()
        }
        return START_STICKY
    }

    /**
     * [ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE] only applies to the 3-arg
     * overload, API 29+; minSdk is 26. On API 34+ that type also requires a connected-device
     * permission (e.g. BLUETOOTH_CONNECT) to be held at call time; if the user revoked it
     * after onboarding granted it, startForeground throws SecurityException — stop rather
     * than let START_STICKY crash-loop the process.
     */
    private fun startForegroundCompat(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            stopSelf()
        }
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
            // §8.2: the notification is rebuilt only when the status text actually changes,
            // not on every WatchState.Ready(battery) emission.
            watchClient.state.map { it.toStatusText() }.distinctUntilChanged().collectLatest { text ->
                startForegroundCompat(ServiceNotifications.build(this@WatchConnectionService, text))
            }
        }
        scope.launch {
            watchClient.state.collectLatest { state ->
                if (state is WatchState.Ready) {
                    if (hasPhoneStatePermission()) {
                        runCatching { watchClient.notifyPhoneStatePermissionGranted() }
                    }
                    runCatching { diagnosticsStore.recordConnected(System.currentTimeMillis()) }
                }
            }
        }
    }

    private fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

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
