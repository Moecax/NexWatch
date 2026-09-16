package com.nexwatch.core.watchfitcloud

import android.annotation.SuppressLint
import android.content.Context
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.watchapi.DiscoveredWatch
import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.SendResult
import com.nexwatch.core.watchapi.SyncProgress
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchCommandTimeoutException
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchNotReadyException
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WatchUserIdProvider
import com.nexwatch.core.watchapi.WeatherForecast
import com.topstep.fitcloud.sdk.exception.FcAuthException
import com.topstep.fitcloud.sdk.v2.FcConnector
import com.topstep.fitcloud.sdk.v2.model.data.FcHealthDataType
import com.topstep.wearkit.base.connector.ConnectorState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** §4.3: settings-shaped commands get 10s... */
private val COMMAND_TIMEOUT = 10.seconds

/** ...and notifications 5s, because a late notification is noise, not information. */
private val NOTIFICATION_TIMEOUT = 5.seconds

/**
 * Long enough for a watch that is asleep on the wrist to answer an advertisement, short
 * enough that a forgotten pairing screen can't sit on the radio. The SDK ends the scan
 * itself when this elapses.
 */
private const val SCAN_SECONDS = 15L

/**
 * Covers scan, connect and the SDK's auth handshake. Generous on purpose: this is the one
 * command whose duration depends on the watch being found at all, not on the radio.
 */
private val CONNECT_TIMEOUT = 60.seconds

/**
 * `FcConnector.getNextRetryTime()` is undocumented as to which clock it uses. Anything
 * earlier than 2001-01-01 is treated as "not a wall-clock instant" rather than shown to
 * the user as a 1970 timestamp.
 */
private const val MIN_PLAUSIBLE_EPOCH_MS = 978_307_200_000L

/**
 * The real [WatchClient] (§4). Every `Single`, `Completable` and `Observable` the SDK
 * exposes is converted to coroutines here and nowhere else, so no Rx type and no
 * `com.topstep.*` type is reachable from any other module.
 *
 * There is deliberately no reconnect logic in this file. `FcConnector` owns reconnection
 * and the SDK's own docs warn against layering another attempt loop on top of it.
 */
@Singleton
class FitCloudWatchClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userIdProvider: WatchUserIdProvider,
    dispatchers: CoroutineDispatchers,
) : WatchClient {

    /** BLE is a serial medium — one command at a time, app-wide (§4.3). */
    private val mutex = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    /**
     * Resolved per call rather than injected: see [FitCloudSdk] for why the SDK is kept
     * out of the Hilt graph entirely.
     */
    private val connector: FcConnector get() = FitCloudSdk.require().connector

    /**
     * False until something asks for a connection, which is what separates
     * [WatchState.Unbound] from the retry states the connector reports while idle.
     */
    private val connectRequested = MutableStateFlow(false)

    /** Bumped by each [connect]; clears a stale [WatchState.AuthFailed] on a fresh attempt. */
    private val connectAttempts = MutableStateFlow(0)

    private val connectorStates: Flow<ConnectorState> by lazy {
        connector.observerConnectorState().asFlow()
            .onStart { emit(connector.getConnectorState()) }
            .distinctUntilChanged()
    }

    /**
     * Sticky until the next successful connection or the next connect attempt: an auth
     * rejection is the one failure the user has to act on, so it must survive the
     * connector's own retry churn instead of being overwritten by it.
     */
    private val authFailure: Flow<String?> by lazy {
        merge(
            connector.observerConnectorError().asFlow()
                .mapNotNull { (it.throwable as? FcAuthException)?.let(::authFailureReason) },
            connectorStates.filter { it == ConnectorState.CONNECTED }.map { null },
            connectAttempts.map { null },
        ).onStart { emit(null) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val batteryPercent: Flow<Int?> by lazy {
        connectorStates.map { it == ConnectorState.CONNECTED }.distinctUntilChanged()
            .flatMapLatest { connected ->
                if (!connected) {
                    flowOf(null)
                } else {
                    flow {
                        // Before the request, not after: `connect()` holds the bus until
                        // `state` reports Ready, and `state` can't report anything until
                        // this flow has produced a value. Emitting first breaks that cycle.
                        emit(null)
                        emit(runCatching { lockedCommand("battery") { readBattery() } }.getOrNull())
                        // Watches that push battery unprompted save us a poll (I5).
                        val ability = FitCloudSdk.require().batteryAbility
                        if (ability.isSupportObserve()) {
                            emitAll(ability.observeBattery().asFlow().map { it.percentage })
                        }
                    }
                }
            }
    }

    override val state: StateFlow<WatchState> by lazy {
        combine(
            connectorStates,
            bluetoothEnabledFlow(context),
            authFailure,
            connectRequested,
            batteryPercent,
        ) { connectorState, bluetoothOn, authReason, requested, battery ->
            when {
                authReason != null -> WatchState.AuthFailed(authReason)
                !requested -> WatchState.Unbound
                !bluetoothOn -> WatchState.BluetoothOff
                else -> connectorState.toWatchState(battery, nextRetryAt())
            }
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, WatchState.Unbound)
    }

    override val capabilities: StateFlow<WatchCapabilities?> by lazy {
        connectorStates.map { it == ConnectorState.CONNECTED }.distinctUntilChanged()
            .map { connected ->
                if (connected) runCatching { readCapabilities() }.getOrNull() else null
            }
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    override val events: SharedFlow<WatchEvent> by lazy {
        connector.messageFeature().observerMessage().asFlow()
            .mapNotNull { it.toWatchEvent() }
            .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)
    }

    // ScanResult is the public return type of the SDK's own public scan() call, but its
    // accessors carry @RestrictTo(LIBRARY_GROUP) — reading them is the only way to use the
    // API at all, and this module is the one place allowed to touch SDK types.
    @SuppressLint("RestrictedApi")
    override fun discoverWatches(): Flow<DiscoveredWatch> =
        FitCloudSdk.require().scanner
            // The second flag keeps unnamed advertisements out: a watch always advertises a
            // name, and without it there is nothing to show the user but a MAC address.
            .scan(SCAN_SECONDS, TimeUnit.SECONDS, true, false)
            .asFlow()
            .map { DiscoveredWatch(address = it.address, name = it.name.orEmpty(), rssi = it.rssi) }

    /** Destructive: BIND wipes the watch. Only the guarded pairing flow may call this. */
    override suspend fun bind(address: String, profile: UserProfile) =
        connect(address, profile, bindOrLogin = true)

    override suspend fun login(address: String, profile: UserProfile) =
        connect(address, profile, bindOrLogin = false)

    private suspend fun connect(address: String, profile: UserProfile, bindOrLogin: Boolean) {
        val userId = userIdProvider.userId()
        mutex.withLock {
            connectAttempts.value++
            connectRequested.value = true
            connector.connect(
                address,
                userId,
                bindOrLogin,
                profile.isMale,
                profile.age,
                profile.heightCm.toFloat(),
                profile.weightKg.toFloat(),
            )
            val settled = withOperationTimeout(
                if (bindOrLogin) "bind" else "login",
                CONNECT_TIMEOUT,
            ) {
                state.first { it is WatchState.Ready || it is WatchState.AuthFailed }
            }
            if (settled !is WatchState.Ready) throw WatchNotReadyException(settled)
        }
    }

    override suspend fun unbind(keepWatchData: Boolean) {
        // Only the watch-side user record can be cleared, and only while the watch can
        // still hear us. A local unbind has to succeed either way, so this is best-effort.
        if (!keepWatchData && state.value is WatchState.Ready) {
            runCatching {
                lockedCommand("unbind") { connector.settingsFeature().unbindUser().await() }
            }
        }
        connectRequested.value = false
        connector.close()
    }

    override fun syncHealthData(): Flow<SyncProgress> = flow {
        // Held for the whole sync (§4.3) — a sync is one long command, not many short ones.
        mutex.withLock {
            requireReady()
            val total = runCatching { connector.dataFeature().getSyncTypes().await().size }
                .getOrDefault(0)
            var synced = 0
            connector.dataFeature().syncData().asFlow().collect { data ->
                synced++
                // I2, journal first: the only work per item is handing the bytes on
                // untouched. Nothing here parses, validates or normalises them.
                emit(
                    SyncProgress(
                        batch = data.toRawBatch(),
                        itemsSynced = synced,
                        totalItems = total,
                        completed = false,
                    ),
                )
            }
            emit(SyncProgress(batch = null, itemsSynced = synced, totalItems = total, completed = true))
        }
    }

    /**
     * Cold, and outside [mutex] on purpose: a live measurement is meant to coexist with
     * other commands rather than hold the bus for as long as a screen is open. The SDK
     * closes the measurement when the last collector cancels.
     */
    override fun liveHeartRate(): Flow<Int> = flow {
        requireReady()
        emitAll(
            connector.dataFeature().openHealthRealTimeData(FcHealthDataType.HEART_RATE).asFlow()
                .map { it.heartRate }
                .filter { it > 0 },
        )
    }

    override suspend fun batteryLevel(): Int = command("battery") { readBattery() }

    override suspend fun findWatch() {
        command("findWatch") { connector.messageFeature().findDevice().await() }
    }

    override suspend fun sendNotification(n: OutgoingNotification): SendResult {
        if (state.value !is WatchState.Ready) return SendResult.Dropped("watch not ready")
        // §4.3: dropped, never queued. The bus is busy with something longer, and a
        // notification queued behind it would arrive too late to be worth anything.
        if (!mutex.tryLock()) return SendResult.Dropped("watch bus busy")
        return try {
            withTimeout(NOTIFICATION_TIMEOUT) {
                @Suppress("DEPRECATION")
                // The SDK points at FcNotificationAbility.sendAppNotification() instead, but
                // that takes four strings whose roles the AAR doesn't name, and the bytecode
                // splices them differently for SMS than for everything else. Guessing wrong
                // here shows the user a mangled notification, so this stays on the three-arg
                // call until §8.5 can verify the new one against the watch.
                connector.messageFeature()
                    .sendNotification(n.type.toFcNotificationType(), n.title, n.content)
                    .await()
            }
            SendResult.Sent
        } catch (e: TimeoutCancellationException) {
            SendResult.Failed("timed out after $NOTIFICATION_TIMEOUT")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never the notification's own text — §9.5 forbids it reaching any sink.
            SendResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun applySettings(change: WatchSettingChange) {
        // WatchSettingChange has no subtypes until Phase 8 defines them, so there is no
        // instance that can reach this — it is unreachable today, not unimplemented.
        requireReady()
        throw UnsupportedOperationException("No WatchSettingChange subtypes exist yet (§4.2, Phase 8)")
    }

    override suspend fun pushWeather(forecast: WeatherForecast) {
        command("pushWeather") {
            connector.settingsFeature().setWeather(
                forecast.locationName,
                System.currentTimeMillis(),
                forecast.toFcWeatherToday(),
                forecast.toFcWeatherForecasts(),
            ).await()
        }
    }

    private suspend fun readBattery(): Int =
        FitCloudSdk.require().batteryAbility.requestBattery().await().percentage

    private suspend fun readCapabilities(): WatchCapabilities = lockedCommand("capabilities") {
        val config = connector.configFeature()
        config.refresh()
        val contactsLimit =
            runCatching { FitCloudSdk.require().contactsAbility.getContactsMaxNumber() }.getOrNull()
        config.getDeviceInfo().toCapabilities(
            firmwareVersion = firmwareVersionOf(connector),
            contactsLimit = contactsLimit,
        )
    }

    private fun nextRetryAt(): Instant? =
        connector.getNextRetryTime().takeIf { it >= MIN_PLAUSIBLE_EPOCH_MS }?.let(Instant::ofEpochMilli)

    private fun requireReady() {
        val current = state.value
        if (current !is WatchState.Ready) throw WatchNotReadyException(current)
    }

    /** Takes the bus but skips the [WatchState.Ready] check, for the reads that *build* that state. */
    private suspend fun <T> lockedCommand(
        operation: String,
        timeout: Duration = COMMAND_TIMEOUT,
        block: suspend () -> T,
    ): T = mutex.withLock { withOperationTimeout(operation, timeout, block) }

    private suspend fun <T> command(
        operation: String,
        timeout: Duration = COMMAND_TIMEOUT,
        block: suspend () -> T,
    ): T {
        requireReady()
        return lockedCommand(operation, timeout, block)
    }
}

private fun authFailureReason(e: FcAuthException): String = when (e.reason) {
    FcAuthException.REASON_FAILED -> "The watch rejected this phone's user id"
    FcAuthException.REASON_BIND_CANCEL -> "Pairing was cancelled on the watch"
    FcAuthException.REASON_BIND_TIMEOUT -> "The watch timed out waiting for pairing confirmation"
    FcAuthException.REASON_SHOULD_RESET -> "The watch must be reset before it can pair again"
    else -> "The watch refused the connection"
}

/**
 * `withTimeout` signals expiry with a [TimeoutCancellationException], which is a
 * `CancellationException` — and every well-behaved caller rethrows those untouched to stay
 * cooperative. Leaking one out of the client therefore kills the caller's coroutine in
 * silence instead of failing the command, which is exactly what stranded the pairing screen
 * on "Connecting" the first time this ran against the real watch. Translate at the boundary.
 */
private suspend fun <T> withOperationTimeout(
    operation: String,
    timeout: Duration,
    block: suspend () -> T,
): T = try {
    withTimeout(timeout) { block() }
} catch (e: TimeoutCancellationException) {
    throw WatchCommandTimeoutException(operation)
}
