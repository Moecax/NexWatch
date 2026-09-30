package com.nexwatch.core.watchapi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one seam between the app and any watch implementation (§4.2). No Android types,
 * no SDK types — FitCloudWatchClient (Phase 4) and FakeWatchClient (this phase) are the
 * only two implementations, ever.
 */
interface WatchClient {
    val state: StateFlow<WatchState>
    val capabilities: StateFlow<WatchCapabilities?>
    val events: SharedFlow<WatchEvent>

    /**
     * Cold, and the only scan in the app (§9.2): collecting starts a scan, cancelling stops
     * it, so nothing can leave the radio searching from a screen that isn't on top. The SDK
     * gives up on its own after a fixed window, which ends the flow.
     */
    fun discoverWatches(): Flow<DiscoveredWatch>

    suspend fun bind(address: String, profile: UserProfile)
    suspend fun login(address: String, profile: UserProfile)
    suspend fun unbind(keepWatchData: Boolean)

    fun syncHealthData(): Flow<SyncProgress>
    fun liveHeartRate(): Flow<Int>
    suspend fun batteryLevel(): Int
    suspend fun findWatch()
    /**
     * Call once READ_PHONE_STATE is granted, and again on every return to the foreground
     * (§8.6) — the SDK does not persist this across activity resumes on its own.
     */
    suspend fun notifyPhoneStatePermissionGranted()
    suspend fun sendNotification(n: OutgoingNotification): SendResult
    suspend fun applySettings(change: WatchSettingChange)

    /**
     * What the watch reports right now, not what the app last wrote. Groups the connected
     * watch doesn't support come back `null` (§4.5).
     */
    suspend fun readSettings(): WatchSettings
    suspend fun pushWeather(forecast: WeatherForecast)
}
