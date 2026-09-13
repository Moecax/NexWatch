package com.nexwatch.core.watchfake

import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.RawBatch
import com.nexwatch.core.watchapi.SendResult
import com.nexwatch.core.watchapi.SyncProgress
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchNotReadyException
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WeatherForecast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

private val SIMULATED_DATA_TYPES = listOf("steps", "heart_rate", "sleep", "spo2")

/**
 * Drives UI development and tests against WatchClient without real Bluetooth (§4.2).
 * Every command routes through [mutex] to mirror the real client's serial-BLE discipline
 * (§4.3) even though nothing here actually contends for a radio — this keeps the two
 * implementations' call-timing behaviour comparable under test.
 */
@Singleton
class FakeWatchClient @Inject constructor() : WatchClient, WatchDebugController {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<WatchState>(WatchState.Unbound)
    private val _capabilities = MutableStateFlow<WatchCapabilities?>(null)
    private val _events = MutableSharedFlow<WatchEvent>(extraBufferCapacity = 8)
    private var fakeBattery = 82

    override val state: StateFlow<WatchState> = _state.asStateFlow()
    override val capabilities: StateFlow<WatchCapabilities?> = _capabilities.asStateFlow()
    override val events: SharedFlow<WatchEvent> = _events.asSharedFlow()

    override suspend fun bind(address: String, profile: UserProfile) = connect(profile)

    override suspend fun login(address: String, profile: UserProfile) = connect(profile)

    private suspend fun connect(profile: UserProfile) = mutex.withLock {
        _state.value = WatchState.Connecting
        delay(CONNECT_DELAY_MS)
        _capabilities.value = sampleCapabilities()
        _state.value = WatchState.Ready(battery = fakeBattery)
    }

    override suspend fun unbind(keepWatchData: Boolean) = mutex.withLock {
        _capabilities.value = null
        _state.value = WatchState.Unbound
    }

    override fun syncHealthData(): Flow<SyncProgress> = flow {
        val ready = _state.value
        if (ready !is WatchState.Ready) throw WatchNotReadyException(ready)
        val total = SIMULATED_DATA_TYPES.size
        SIMULATED_DATA_TYPES.forEachIndexed { index, dataType ->
            delay(SYNC_ITEM_DELAY_MS)
            emit(
                SyncProgress(
                    batch = RawBatch(dataType = dataType, payloadJson = "{\"type\":\"$dataType\"}"),
                    itemsSynced = index + 1,
                    totalItems = total,
                    completed = index + 1 == total,
                ),
            )
        }
    }

    override fun liveHeartRate(): Flow<Int> = flow {
        requireReady()
        while (true) {
            emit(Random.nextInt(55, 120))
            delay(HEART_RATE_INTERVAL_MS)
        }
    }

    override suspend fun batteryLevel(): Int = mutex.withLock {
        requireReady()
        fakeBattery
    }

    override suspend fun findWatch(): Unit = mutex.withLock {
        requireReady()
        delay(COMMAND_DELAY_MS)
    }

    override suspend fun sendNotification(n: OutgoingNotification): SendResult = mutex.withLock {
        val current = _state.value
        if (current !is WatchState.Ready) return@withLock SendResult.Dropped("watch not ready")
        delay(COMMAND_DELAY_MS)
        SendResult.Sent
    }

    override suspend fun applySettings(change: WatchSettingChange): Unit = mutex.withLock {
        requireReady()
        delay(COMMAND_DELAY_MS)
    }

    override suspend fun pushWeather(forecast: WeatherForecast): Unit = mutex.withLock {
        requireReady()
        delay(COMMAND_DELAY_MS)
    }

    override fun forceState(state: WatchState) {
        _state.value = state
        if (state !is WatchState.Ready) _capabilities.value = null
    }

    override fun forceBattery(percent: Int) {
        fakeBattery = percent.coerceIn(0, 100)
        val current = _state.value
        if (current is WatchState.Ready) _state.value = WatchState.Ready(battery = fakeBattery)
    }

    private fun requireReady() {
        val current = _state.value
        if (current !is WatchState.Ready) throw WatchNotReadyException(current)
    }

    private fun sampleCapabilities() = WatchCapabilities(
        heartRate = true,
        spo2 = true,
        bloodPressure = false,
        temperature = false,
        stress = true,
        sport = true,
        gps = true,
        advancedReminders = true,
        weather = true,
        contactsLimit = 20,
        firmwareVersion = "FAKE-1.0.0",
    )

    private companion object {
        const val CONNECT_DELAY_MS = 50L
        const val SYNC_ITEM_DELAY_MS = 20L
        const val COMMAND_DELAY_MS = 10L
        const val HEART_RATE_INTERVAL_MS = 1000L
    }
}
