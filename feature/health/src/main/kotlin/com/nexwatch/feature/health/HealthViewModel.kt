package com.nexwatch.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.health.DailySummaryRepository
import com.nexwatch.core.data.health.HealthRepository
import com.nexwatch.core.model.DailyBloodPressure
import com.nexwatch.core.model.DailyHeartRate
import com.nexwatch.core.model.DailySpo2
import com.nexwatch.core.model.DailySummary
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.WorkoutSummary
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HealthViewModel @Inject constructor(
    private val health: HealthRepository,
    private val summaries: DailySummaryRepository,
    private val watchClient: WatchClient,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val selection = MutableStateFlow(HealthRange.WEEK to LocalDate.now(zone))

    fun select(range: HealthRange) {
        selection.value = range to LocalDate.now(zone)
    }

    /** A screen left open past midnight moves its window on the next resume. */
    fun onResume() {
        selection.value = selection.value.first to LocalDate.now(zone)
    }

    val uiState: StateFlow<HealthUiState> = selection.flatMapLatest { (range, today) ->
        val from = today.minusDays(range.days - 1L)
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val perDay = combine(
            health.observeDailyHeartRate(from, today, zone),
            summaries.observeRange(from.toString(), today.toString()),
            // A night is listed under the date it started; it can end on the first day of the window.
            health.observeSleepNights(from.minusDays(1).toString(), today.toString()),
            health.observeDailySpo2(from, today, zone),
            health.observeDailyBloodPressure(from, today, zone),
        ) { hr, days, nights, spo2, bp -> PerDay(hr, days, nights, spo2, bp) }
        combine(perDay, health.observeWorkouts(fromMs, toMs), watchClient.capabilities) { d, workouts, caps ->
            buildHealthState(range, from, today, d.heartRate, d.summaries, d.nights, d.spo2, d.bloodPressure, workouts, caps, zone)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    private data class PerDay(
        val heartRate: List<DailyHeartRate>,
        val summaries: List<DailySummary>,
        val nights: List<SleepNight>,
        val spo2: List<DailySpo2>,
        val bloodPressure: List<DailyBloodPressure>,
    )
}

internal fun buildHealthState(
    range: HealthRange,
    from: LocalDate,
    to: LocalDate,
    heartRate: List<DailyHeartRate>,
    summaries: List<DailySummary>,
    nights: List<SleepNight>,
    spo2: List<DailySpo2>,
    bloodPressure: List<DailyBloodPressure>,
    workouts: List<WorkoutSummary>,
    capabilities: WatchCapabilities?,
    zone: ZoneId,
): HealthUiState {
    val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    val hrByDate = heartRate.associateBy { it.date }
    val summaryByDate = summaries.associateBy { it.date }
    val sleepByWakeDate = nights.filter { it.stages.isNotEmpty() }.associate { night ->
        Instant.ofEpochMilli(night.stages.maxOf { it.endMs }).atZone(zone).toLocalDate().toString() to night.toDaySleep()
    }
    val spo2ByDate = spo2.associateBy { it.date }
    val bpByDate = bloodPressure.associateBy { it.date }
    val keys = dates.map { it.toString() }
    return HealthUiState(
        range = range,
        dates = dates,
        heart = keys.map { k -> hrByDate[k]?.let { DayHeart(it.minBpm, it.maxBpm, summaryByDate[k]?.restingHrBpm) } },
        steps = keys.map { k -> summaryByDate[k]?.let { maxOf(it.steps, it.liveStepsTotal ?: 0) }?.takeIf { it > 0 } },
        sleep = keys.map { sleepByWakeDate[it] },
        spo2 = keys.map { spo2ByDate[it] },
        bloodPressure = keys.map { bpByDate[it] },
        workouts = workouts.sortedByDescending { it.startMs },
        showSpo2 = capabilities?.spo2 != false || spo2.isNotEmpty(),
        showBloodPressure = capabilities?.bloodPressure != false || bloodPressure.isNotEmpty(),
        unsupported = buildList {
            if (capabilities?.stress == false) add("Stress")
            if (capabilities?.temperature == false) add("skin temperature")
        },
    )
}

private fun SleepNight.toDaySleep(): DaySleep {
    fun minutes(stage: SleepStage) = stages.filter { it.stage == stage }.sumOf { (it.endMs - it.startMs) / 60_000 }.toInt()
    return DaySleep(deep = minutes(SleepStage.DEEP), rem = minutes(SleepStage.REM), light = minutes(SleepStage.LIGHT), awake = minutes(SleepStage.AWAKE))
}
