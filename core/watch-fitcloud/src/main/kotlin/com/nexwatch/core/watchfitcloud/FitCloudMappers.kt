// FcNotificationType and the respiratory-rate sync types are deprecated in the SDK but
// still the only names for what the watch sends; see FitCloudWatchClient.sendNotification()
// for why the replacement notification API isn't taken up yet.
@file:Suppress("DEPRECATION")

package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.RawBatch
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WeatherCondition
import com.nexwatch.core.watchapi.WeatherForecast
import com.topstep.fitcloud.sdk.v2.FcConnector
import com.topstep.fitcloud.sdk.v2.model.config.FcDeviceInfo
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncData
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncDataType
import com.topstep.fitcloud.sdk.v2.model.message.FcMessageInfo
import com.topstep.fitcloud.sdk.v2.model.message.FcMessageType
import com.topstep.fitcloud.sdk.v2.model.message.FcNotificationType
import com.topstep.fitcloud.sdk.v2.model.settings.FcWeatherForecast
import com.topstep.fitcloud.sdk.v2.model.settings.FcWeatherToday
import com.topstep.wearkit.base.connector.ConnectorState
import java.time.Instant
import java.util.Base64

/**
 * Maps the connector's state onto [WatchState] (§4.2).
 *
 * DISCONNECTED collapses into [WatchState.Waiting] rather than getting a state of its own:
 * once we've asked to connect, the SDK owns reconnection and never stays disconnected on
 * purpose, so from the app's point of view it is always "between retries". The genuinely
 * terminal cases — never connected, Bluetooth off, auth rejected — are decided by the
 * caller before this is reached.
 */
internal fun ConnectorState.toWatchState(battery: Int?, nextRetryAt: Instant?): WatchState =
    when (this) {
        ConnectorState.DISCONNECTED, ConnectorState.PRE_CONNECTING -> WatchState.Waiting(nextRetryAt)
        ConnectorState.CONNECTING, ConnectorState.PRE_CONNECTED -> WatchState.Connecting
        ConnectorState.CONNECTED -> WatchState.Ready(battery)
    }

/**
 * §4.5. `contactsLimit` and `firmwareVersion` come from the connector rather than
 * [FcDeviceInfo], so they're passed in.
 */
internal fun FcDeviceInfo.toCapabilities(
    firmwareVersion: String,
    contactsLimit: Int?,
): WatchCapabilities = WatchCapabilities(
    heartRate = isSupportFeature(FcDeviceInfo.Feature.HEART_RATE),
    spo2 = isSupportFeature(FcDeviceInfo.Feature.OXYGEN),
    bloodPressure = isSupportFeature(FcDeviceInfo.Feature.BLOOD_PRESSURE),
    temperature = isSupportFeature(FcDeviceInfo.Feature.TEMPERATURE),
    stress = isSupportFeature(FcDeviceInfo.Feature.PRESSURE),
    sport = isSupportFeature(FcDeviceInfo.Feature.SPORT),
    gps = isSupportFeature(FcDeviceInfo.Feature.GPS),
    advancedReminders = isSupportFeature(FcDeviceInfo.Feature.ADVANCED_REMIND),
    weather = isSupportFeature(FcDeviceInfo.Feature.WEATHER),
    contactsLimit = contactsLimit?.takeIf { it > 0 && isSupportFeature(FcDeviceInfo.Feature.CONTACTS) },
    firmwareVersion = firmwareVersion,
)

/** The watch-initiated messages the app acts on (§8.6); everything else is ignored here. */
internal fun FcMessageInfo.toWatchEvent(): WatchEvent? = when (type) {
    FcMessageType.FIND_PHONE -> WatchEvent.FindPhoneRequested
    FcMessageType.CAMERA_WAKE_UP -> WatchEvent.CameraOpenRequested
    FcMessageType.CAMERA_EXIT -> WatchEvent.CameraCloseRequested
    FcMessageType.TELEPHONY_HANG_UP -> WatchEvent.HangUpRequested
    else -> null
}

internal fun OutgoingNotification.NotificationType.toFcNotificationType(): Int = when (this) {
    OutgoingNotification.NotificationType.SMS -> FcNotificationType.SMS
    OutgoingNotification.NotificationType.WHATSAPP -> FcNotificationType.WHATSAPP
    OutgoingNotification.NotificationType.TELEGRAM -> FcNotificationType.TELEGRAM
    OutgoingNotification.NotificationType.CALL -> FcNotificationType.TELEPHONY_INCOMING
    OutgoingNotification.NotificationType.OTHERS_APP -> FcNotificationType.OTHERS_APP
}

/** `connect()` takes sex as a boolean; the SDK's convention is true = male. */
internal val UserProfile.isMale: Boolean
    get() = sex == UserProfile.Sex.MALE

/**
 * §5.2 stores the payload exactly as the SDK handed it over — only the normaliser
 * (Phase 6) is allowed to interpret it. Base64 keeps the bytes intact and is JSON-safe,
 * so the array below needs no escaping.
 */
internal fun FcSyncData.toRawBatch(): RawBatch = RawBatch(
    dataType = syncDataTypeName(type),
    payloadJson = data.joinToString(separator = ",", prefix = "[", postfix = "]") {
        "\"" + Base64.getEncoder().encodeToString(it) + "\""
    },
)

/**
 * The journal's `data_type` column is read by humans and by the Phase 6 normaliser, so it
 * gets a stable name rather than the SDK's raw int. Unknown types keep the int instead of
 * being dropped: a payload we can't name today is still a payload worth journalling.
 */
internal fun syncDataTypeName(type: Int): String = when (type) {
    FcSyncDataType.STEP -> "step"
    FcSyncDataType.SLEEP -> "sleep"
    FcSyncDataType.HEART_RATE -> "heart_rate"
    FcSyncDataType.HEART_RATE_MEASURE -> "heart_rate_measure"
    FcSyncDataType.HEART_RATE_RESTING -> "heart_rate_resting"
    FcSyncDataType.OXYGEN -> "oxygen"
    FcSyncDataType.OXYGEN_MEASURE -> "oxygen_measure"
    FcSyncDataType.BLOOD_PRESSURE -> "blood_pressure"
    FcSyncDataType.BLOOD_PRESSURE_MEASURE -> "blood_pressure_measure"
    FcSyncDataType.RESPIRATORY_RATE -> "respiratory_rate"
    FcSyncDataType.RESPIRATORY_RATE_MEASURE -> "respiratory_rate_measure"
    FcSyncDataType.TEMPERATURE -> "temperature"
    FcSyncDataType.TEMPERATURE_MEASURE -> "temperature_measure"
    FcSyncDataType.PRESSURE -> "stress"
    FcSyncDataType.PRESSURE_MEASURE -> "stress_measure"
    FcSyncDataType.HRV -> "hrv"
    FcSyncDataType.HRV_DAILY -> "hrv_daily"
    FcSyncDataType.SPORT -> "sport"
    FcSyncDataType.GPS -> "gps"
    FcSyncDataType.ECG -> "ecg"
    FcSyncDataType.MOOD -> "mood"
    FcSyncDataType.VITALITY -> "vitality"
    FcSyncDataType.GAME -> "game"
    FcSyncDataType.TODAY_TOTAL_DATA -> "today_total"
    else -> "unknown_$type"
}

/**
 * §4.5 persists a firmware version alongside the capabilities. The app build is the version
 * the user sees on the watch; the GNSS and 4G strings are reported only by units that have
 * that hardware, so on the GTR 3 Pro they stay empty (`docs/recon.md` §1).
 */
internal fun firmwareVersionOf(connector: FcConnector): String {
    val config = connector.configFeature()
    val extra = runCatching { config.getExtraFirmwareInfo() }.getOrNull()
    return listOfNotNull(
        FcDeviceInfoVersions.app(config.getDeviceInfo())?.takeIf { it.isNotBlank() },
        extra?.gnssGpsVersion?.takeIf { it.isNotBlank() }?.let { "GNSS $it" },
        extra?.modem4GVersion?.takeIf { it.isNotBlank() }?.let { "4G $it" },
    ).joinToString(" / ").ifBlank { "unknown" }
}

/**
 * FitCloud encodes the weather as an icon index, not a condition enum, and the SDK ships
 * no constants for it. These indices come from the vendor's own weather push sample and
 * are **unverified against the GTR 3 Pro** — Phase 8 pushes a known value per condition
 * and records what the watch actually draws (`docs/recon.md` §5).
 */
private fun WeatherCondition.toFcWeatherCode(): Int = when (this) {
    WeatherCondition.SUNNY -> 0
    WeatherCondition.CLOUDY -> 1
    WeatherCondition.RAIN -> 3
    WeatherCondition.SNOW -> 4
    WeatherCondition.STORM -> 5
    WeatherCondition.FOG -> 6
}

/**
 * Only the fields NexWatch actually sources are filled in. The rest (pressure, wind, UV,
 * air quality, humidity) have no provider yet, and the SDK treats 0 as "not reported".
 */
internal fun WeatherForecast.toFcWeatherToday(): FcWeatherToday {
    val today = days.firstOrNull()
    return FcWeatherToday(
        lowTemperature = today?.lowC ?: current.temperatureC,
        highTemperature = today?.highC ?: current.temperatureC,
        weatherCode = current.condition.toFcWeatherCode(),
        currentTemperature = current.temperatureC,
        pressure = 0,
        windForce = 0,
        visibility = 0,
        ultraviolet = 0,
        visibilityMeters = 0,
        airQuality = 0,
        humidity = 0,
    )
}

/** `setWeather()` takes today separately, so the forecast list starts at tomorrow. */
internal fun WeatherForecast.toFcWeatherForecasts(): List<FcWeatherForecast> =
    days.drop(1).map {
        FcWeatherForecast(
            lowTemperature = it.lowC,
            highTemperature = it.highC,
            weatherCode = it.condition.toFcWeatherCode(),
        )
    }
