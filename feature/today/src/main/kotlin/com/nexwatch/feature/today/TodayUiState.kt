package com.nexwatch.feature.today

import com.nexwatch.core.model.BloodPressureReading
import com.nexwatch.core.model.HeartRateRange
import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.Spo2Reading
import com.nexwatch.core.model.WorkoutSummary

/** What the watch strip at the top of Today says about the connection. */
sealed interface WatchLink {
    data object Unbound : WatchLink
    data object Connecting : WatchLink
    data object BluetoothOff : WatchLink
    data class NotNearby(val lastSeenMs: Long?) : WatchLink
    data class SignedInElsewhere(val reason: String) : WatchLink
    data class Connected(val battery: Int?) : WatchLink
}

/** A sync in progress; [fraction] is null until the watch reports how much it will send. */
data class SyncState(val fraction: Float?, val dataType: String?)

data class TodayActivity(val steps: Int, val energyKcal: Int, val distanceM: Int)

data class TodayHeart(
    val latest: HeartRateSample?,
    val restingBpm: Int?,
    val minBpm: Int?,
    val maxBpm: Int?,
    /** 15-minute buckets since local midnight. */
    val buckets: List<HeartRateRange>,
)

data class TodayUiState(
    val loading: Boolean = true,
    val link: WatchLink = WatchLink.Connecting,
    val sync: SyncState? = null,
    val lastSyncMs: Long? = null,
    /** Why the most recent sync attempt failed, and only while nothing has resolved it since. */
    val lastSyncError: String? = null,
    val dayStartMs: Long = 0,
    val activity: TodayActivity? = null,
    val heart: TodayHeart? = null,
    val lastNight: SleepNight? = null,
    val spo2: Spo2Reading? = null,
    val spo2LowLastDay: Int? = null,
    val bloodPressure: BloodPressureReading? = null,
    val lastWorkout: WorkoutSummary? = null,
    /** False only when the connected watch says it has no such sensor. */
    val showSpo2: Boolean = true,
    val showBloodPressure: Boolean = true,
) {
    val connected: Boolean get() = link is WatchLink.Connected

    /** Nothing at all recorded yet, as on the first day with a new watch. */
    val firstDay: Boolean
        get() = (activity == null || activity.steps == 0) && heart?.buckets.isNullOrEmpty() && lastNight == null &&
            spo2 == null && bloodPressure == null
}
