package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutHrPoint
import com.nexwatch.core.model.WorkoutRoutePoint
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.topstep.fitcloud.sdk.v2.model.data.FcBloodPressureData
import com.topstep.fitcloud.sdk.v2.model.data.FcGpsData
import com.topstep.fitcloud.sdk.v2.model.data.FcHeartRateData
import com.topstep.fitcloud.sdk.v2.model.data.FcOxygenData
import com.topstep.fitcloud.sdk.v2.model.data.FcPressureData
import com.topstep.fitcloud.sdk.v2.model.data.FcSleepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem
import com.topstep.fitcloud.sdk.v2.model.data.FcSportData
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncData
import com.topstep.fitcloud.sdk.v2.model.data.FcTemperatureData
import com.topstep.fitcloud.sdk.v2.model.data.FcTodayTotalData
import org.json.JSONArray
import java.util.Base64
import javax.inject.Inject

/**
 * §5.2's decode step. Reconstructs FcSyncData from journaled bytes and calls its own
 * .toXxx() methods — the only way to interpret FitCloud's binary protocol, which lives
 * entirely inside the SDK. See this file's design spec for why decoding can't live in
 * :core:data. The `extra` param below has an obfuscated SDK-internal type name
 * (`com.topstep...config.a`) found via javap — public and constructible, but not a stable
 * name; if a future SDK version renames it, this file fails to compile, loudly, and needs
 * re-deriving against the new AAR.
 *
 * The `FcXxxData -> DecodedHealthRecord` mapping functions below are top-level `internal`
 * so they're unit-testable via the SDK's own public constructors (see
 * FitCloudDataMappersTest) without going through FcSyncData's undocumented byte layout,
 * which this project has no encoder for and isn't worth reverse-engineering just to test.
 */
class FitCloudHealthDataDecoder @Inject constructor() : HealthDataDecoder {

    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> {
        val type = syncDataTypeFromName(dataType) ?: return emptyList()
        val data = decodeBase64Array(payloadJson)
        val deviceInfo = FitCloudSdk.require().connector.configFeature().getDeviceInfo()
        val syncData = FcSyncData(type, data, deviceInfo, com.topstep.fitcloud.sdk.v2.model.config.a())
        return dispatchDecode(dataType, syncData)
    }
}

/** Split from decode() so it's reachable without a real FcSyncData in tests — see StepDecodingTest. */
internal fun dispatchDecode(dataType: String, syncData: FcSyncData): List<DecodedHealthRecord> = when (dataType) {
    "step" -> syncData.toStep().orEmpty().map { it.toDecodedStep() }
    "today_total" -> listOfNotNull(syncData.toTodayTotal()?.toDecodedTodayTotal())
    "heart_rate", "heart_rate_measure", "heart_rate_resting" -> when (dataType) {
        "heart_rate" -> syncData.toHeartRate()
        "heart_rate_measure" -> syncData.toHeartRateMeasure()
        else -> syncData.toHeartRateResting()
    }.orEmpty().map { it.toDecodedHeartRate() }
    "oxygen", "oxygen_measure" ->
        (if (dataType == "oxygen") syncData.toOxygen() else syncData.toOxygenMeasure())
            .orEmpty().map { it.toDecodedSpo2() }
    "blood_pressure" -> syncData.toBloodPressure().orEmpty().map { it.toDecodedBloodPressure() }
    "temperature", "temperature_measure" ->
        (if (dataType == "temperature") syncData.toTemperature() else syncData.toTemperatureMeasure())
            .orEmpty().map { it.toDecodedTemperature() }
    "stress", "stress_measure" ->
        (if (dataType == "stress") syncData.toPressure() else syncData.toPressureMeasure())
            .orEmpty().map { it.toDecodedStress() }
    "sleep" -> syncData.toSleep().orEmpty().map { it.toDecodedSleep() }
    "sport" -> syncData.toSport().orEmpty().map { it.toDecodedWorkout() }
    "gps" -> syncData.toGps().orEmpty().map { it.toDecodedWorkoutRoute() }
    else -> emptyList()
}

private fun decodeBase64Array(payloadJson: String): List<ByteArray> {
    val array = JSONArray(payloadJson)
    return (0 until array.length()).map { Base64.getDecoder().decode(array.getString(it)) }
}

/**
 * Distance arrives in km (recon: 0.00915 km for 14 steps); canonical unit is metres. The watch stamps only the
 * bucket's end; the normaliser derives its start from the previous bucket (§5.3), so start is left at the end here.
 */
internal fun FcStepData.toDecodedStep() = DecodedHealthRecord.Step(
    startMs = timestamp,
    endMs = timestamp,
    count = step,
    distanceM = distance * 1000f,
    kcal = calories,
)

/**
 * calorie lines up as milli-kcal against the recon capture (393 vs step's 0.393 kcal) — a
 * single-sample match, encoded pending a larger confirming sample (docs/recon.md, open item).
 */
internal fun FcTodayTotalData.toDecodedTodayTotal() = DecodedHealthRecord.TodayTotal(
    atMs = timestamp,
    steps = step,
    distanceM = distance,
    kcal = calorie / 1000f,
    heartRateBpm = heartRate.takeIf { it > 0 },
)

internal fun FcHeartRateData.toDecodedHeartRate() = DecodedHealthRecord.HeartRate(atMs = timestamp, bpm = heartRate)

internal fun FcOxygenData.toDecodedSpo2() = DecodedHealthRecord.Spo2(atMs = timestamp, percent = oxygen)

internal fun FcBloodPressureData.toDecodedBloodPressure() =
    DecodedHealthRecord.BloodPressure(atMs = timestamp, systolic = sbp, diastolic = dbp)

/** Not supported on the GTR 3 Pro (docs/recon.md §1) — mapping unverified against real hardware. */
internal fun FcTemperatureData.toDecodedTemperature() = DecodedHealthRecord.Temperature(
    atMs = timestamp,
    celsius = if (body > 0f) body else wrist,
)

internal fun FcPressureData.toDecodedStress() = DecodedHealthRecord.Stress(atMs = timestamp, level = pressure)

internal fun FcSleepData.toDecodedSleep(): DecodedHealthRecord.Sleep {
    val spans = items.map {
        SleepStageSpan(
            stage = when (it.status) {
                FcSleepItem.STATUS_DEEP -> SleepStage.DEEP
                FcSleepItem.STATUS_LIGHT -> SleepStage.LIGHT
                FcSleepItem.STATUS_REM -> SleepStage.REM
                else -> SleepStage.AWAKE // STATUS_SOBER
            },
            startMs = it.startTime,
            endMs = it.endTime,
        )
    }
    return DecodedHealthRecord.Sleep(
        startMs = spans.minOfOrNull { it.startMs } ?: timestamp,
        endMs = spans.maxOfOrNull { it.endMs } ?: timestamp,
        stages = spans,
        score = score,
        efficiency = efficiency,
    )
}

/**
 * heartRateItems' duration is an offset in seconds from the workout's own start (timestamp).
 *
 * distanceM comes from [FcSportData.distance] (km, confirmed via javap against the vendored
 * AAR — same convention as [FcStepData.distance]), not [FcSportData.distanceMeters]: despite
 * its name, distanceMeters does not hold the canonical metres value here.
 */
internal fun FcSportData.toDecodedWorkout(): DecodedHealthRecord.Workout {
    val hrSeries =
        heartRateItems.orEmpty().map { WorkoutHrPoint(atMs = timestamp + it.duration * 1000L, bpm = it.heartRate) }
    return DecodedHealthRecord.Workout(
        sportId = sportId.orEmpty(),
        sportType = type,
        startMs = timestamp,
        endMs = timestamp + duration * 1000L,
        distanceM = distance * 1000f,
        kcal = calories,
        avgHrBpm = hrSeries.map { it.bpm }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        maxHrBpm = hrSeries.maxOfOrNull { it.bpm },
        steps = steps.takeIf { it > 0 },
        heartRateSeries = hrSeries,
    )
}

/**
 * GPS items carry duration as a seconds-offset from the paired workout's own start, and
 * FcGpsData has no start timestamp of its own to add it to — only HealthDataNormalizer
 * (Task 15), which already looks up the matching WorkoutEntity by sportId, can convert this
 * to an absolute time. This mapping stays in offset form on purpose.
 */
internal fun FcGpsData.toDecodedWorkoutRoute() = DecodedHealthRecord.WorkoutRoute(
    sportId = sportId,
    points = items.map { WorkoutRoutePoint(offsetSeconds = it.duration, lat = it.lat, lon = it.lng, altitudeM = it.altitude) },
)
