package com.nexwatch.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import com.nexwatch.core.data.health.DailySummaryRepository
import com.nexwatch.core.data.health.HealthRepository
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.data.notification.NotificationForwardingPrefs
import com.nexwatch.core.data.sync.WatchSync
import com.nexwatch.core.data.sync.WatchSyncStatus
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val FIFTEEN_MIN_MS = 15 * 60_000L
private const val DAY_MS = 24 * 60 * 60_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    summaries: DailySummaryRepository,
    health: HealthRepository,
    private val watchClient: WatchClient,
    private val diagnostics: DiagnosticsStore,
    private val watchSync: WatchSync,
    forwardingPrefs: NotificationForwardingPrefs,
    private val identityStore: WatchIdentityStore,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Re-read on every resume, so a screen left open past midnight moves to the new day. */
    private val today = MutableStateFlow(LocalDate.now(zone))

    fun onResume() {
        today.value = LocalDate.now(zone)
        // A resume is an event, not a poll (invariant 5): the user is looking at the numbers
        // right now, so a link that is up but stale is worth refreshing on the spot.
        viewModelScope.launch {
            if (watchClient.state.first() !is WatchState.Ready) return@launch
            val lastSyncAt = diagnostics.snapshot.first().lastSyncAt
            if (lastSyncAt == null || System.currentTimeMillis() - lastSyncAt > FIFTEEN_MIN_MS) {
                watchSync.requestSync()
            }
        }
    }

    private val link = combine(watchClient.state, diagnostics.snapshot, identityStore.identity) { state, diag, identity ->
        when (state) {
            is WatchState.Ready -> WatchLink.Connected(state.battery)
            WatchState.Connecting -> WatchLink.Connecting
            WatchState.BluetoothOff -> WatchLink.BluetoothOff
            is WatchState.Waiting -> WatchLink.NotNearby(diag.lastConnectedAt)
            is WatchState.AuthFailed -> WatchLink.SignedInElsewhere(state.reason)
            WatchState.Unbound -> if (identity.isBound) WatchLink.Connecting else WatchLink.Unbound
        }
    }

    private val status = combine(link, watchSync.status, diagnostics.snapshot) { link, sync, diag ->
        Triple(link, (sync as? WatchSyncStatus.Running)?.let { SyncState(it.fraction, it.dataType) }, diag.lastSyncAt)
    }

    private val dayData = today.flatMapLatest { day ->
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = startMs + DAY_MS - 1
        val activity = summaries.observeToday().map { s ->
            s?.let { TodayActivity(steps = maxOf(it.steps, it.liveStepsTotal ?: 0), energyKcal = it.energyKcal, distanceM = it.distanceM) }
        }
        val heart = combine(
            health.observeHeartRateRanges(startMs, endMs, FIFTEEN_MIN_MS),
            health.observeLatestHeartRate(),
            summaries.observeToday(),
        ) { buckets, latest, summary ->
            TodayHeart(
                latest = latest,
                restingBpm = summary?.restingHrBpm,
                minBpm = buckets.minOfOrNull { it.minBpm },
                maxBpm = buckets.maxOfOrNull { it.maxBpm },
                buckets = buckets,
            )
        }
        val sleep = health.observeSleepNights(day.minusDays(1).toString(), day.toString()).map { nights ->
            nights.maxByOrNull { it.nightDate }
        }
        combine(activity, heart, sleep) { a, h, s -> DayData(startMs, a, h, s) }
    }

    private val vitals = today.flatMapLatest {
        val nowMs = System.currentTimeMillis()
        combine(
            health.observeLatestSpo2(),
            health.observeSpo2Low(nowMs - DAY_MS, Long.MAX_VALUE),
            health.observeLatestBloodPressure(),
            health.observeWorkouts(nowMs - 7 * DAY_MS, Long.MAX_VALUE).map { it.firstOrNull() },
            watchClient.capabilities,
        ) { spo2, low, bp, workout, caps -> Vitals(spo2, low, bp, workout, caps?.spo2 != false, caps?.bloodPressure != false) }
    }

    val uiState: StateFlow<TodayUiState> = combine(status, dayData, vitals) { (link, sync, lastSync), day, v ->
        TodayUiState(
            loading = false,
            link = link,
            sync = sync,
            lastSyncMs = lastSync,
            dayStartMs = day.startMs,
            activity = day.activity,
            heart = day.heart,
            lastNight = day.sleep,
            spo2 = v.spo2,
            spo2LowLastDay = v.spo2Low,
            bloodPressure = v.bloodPressure,
            lastWorkout = v.lastWorkout,
            showSpo2 = v.spo2Supported || v.spo2 != null,
            showBloodPressure = v.bpSupported || v.bloodPressure != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** Starts false so a turned-off switch never flashes the access banner. */
    val forwardingEnabled: StateFlow<Boolean> = forwardingPrefs.settings
        .map { it.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun syncNow() = watchSync.requestSync()

    /**
     * After the user closed the other app. LOGIN with the stored identity, never BIND: this
     * path must not be able to wipe the watch.
     */
    fun retryLogin() {
        viewModelScope.launch {
            val identity = identityStore.identity.first()
            val address = identity.boundAddress ?: return@launch
            val profile = identity.profile ?: return@launch
            try {
                watchClient.login(address, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The state flow reports the outcome; a failed retry leaves the banner up.
            }
        }
    }

    private data class DayData(
        val startMs: Long,
        val activity: TodayActivity?,
        val heart: TodayHeart,
        val sleep: com.nexwatch.core.model.SleepNight?,
    )

    private data class Vitals(
        val spo2: com.nexwatch.core.model.Spo2Reading?,
        val spo2Low: Int?,
        val bloodPressure: com.nexwatch.core.model.BloodPressureReading?,
        val lastWorkout: com.nexwatch.core.model.WorkoutSummary?,
        val spo2Supported: Boolean,
        val bpSupported: Boolean,
    )
}
