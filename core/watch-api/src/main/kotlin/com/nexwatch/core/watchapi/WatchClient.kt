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
    suspend fun sendNotification(n: OutgoingNotification): SendResult
    suspend fun applySettings(change: WatchSettingChange)
    suspend fun pushWeather(forecast: WeatherForecast)
}
