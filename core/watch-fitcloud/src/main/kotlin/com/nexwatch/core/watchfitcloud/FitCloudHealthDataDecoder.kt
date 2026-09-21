package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncData
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
    else -> emptyList()
}

private fun decodeBase64Array(payloadJson: String): List<ByteArray> {
    val array = JSONArray(payloadJson)
    return (0 until array.length()).map { Base64.getDecoder().decode(array.getString(it)) }
}

/** Distance arrives in km (recon: 0.00915 km for 14 steps); canonical unit is metres. */
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
