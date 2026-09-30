package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.watchapi.DisplayUnits
import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.DrinkWaterReminder
import com.nexwatch.core.watchapi.HealthMonitoring
import com.nexwatch.core.watchapi.HeartRateAlert
import com.nexwatch.core.watchapi.MinuteWindow
import com.nexwatch.core.watchapi.SedentaryReminder
import com.nexwatch.core.watchapi.WatchAlarm
import com.nexwatch.core.watchapi.WatchContact
import com.nexwatch.core.watchapi.WristRaise
import com.topstep.fitcloud.sdk.v2.model.config.FcDNDConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcDrinkWaterConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcFunctionConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcHealthMonitorConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcHeartRateAlarmConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcSedentaryConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcTurnWristLightingConfig
import com.topstep.fitcloud.sdk.v2.model.settings.FcAlarm
import com.topstep.fitcloud.sdk.v2.model.settings.FcContacts
import java.time.DayOfWeek

// Every SDK config object is an immutable wrapper over the watch's own byte layout, and its
// Builder starts from those bytes. Writing therefore always starts from the config the watch
// last reported, so bytes this app has no field for survive the round trip untouched.

internal fun FcDNDConfig.toDomain() = DoNotDisturb(
    allDay = isEnabledAllDay(),
    scheduled = isEnabledPeriodTime(),
    window = MinuteWindow(getStart(), getEnd()),
)

internal fun DoNotDisturb.applyTo(current: FcDNDConfig): FcDNDConfig =
    FcDNDConfig.Builder(current.copyBytes())
        .setEnableAllDay(allDay)
        .setEnabledPeriodTime(scheduled)
        .setStart(window.startMinute)
        .setEnd(window.endMinute)
        .create()

internal fun FcSedentaryConfig.toDomain() = SedentaryReminder(
    enabled = isEnabled(),
    window = MinuteWindow(getStart(), getEnd()),
    intervalMinutes = getInterval(),
    respectsDoNotDisturb = isDNDEnabled(),
)

internal fun SedentaryReminder.applyTo(current: FcSedentaryConfig): FcSedentaryConfig =
    FcSedentaryConfig.Builder(current.copyBytes())
        .setEnabled(enabled)
        .setDNDEnabled(respectsDoNotDisturb)
        .setStart(window.startMinute)
        .setEnd(window.endMinute)
        .setInterval(intervalMinutes)
        .create()

internal fun FcDrinkWaterConfig.toDomain() = DrinkWaterReminder(
    enabled = isEnabled(),
    window = MinuteWindow(getStart(), getEnd()),
    intervalMinutes = getInterval(),
)

internal fun DrinkWaterReminder.applyTo(current: FcDrinkWaterConfig): FcDrinkWaterConfig =
    FcDrinkWaterConfig.Builder(current.copyBytes())
        .setEnabled(enabled)
        .setStart(window.startMinute)
        .setEnd(window.endMinute)
        .setInterval(intervalMinutes)
        .create()

internal fun FcHealthMonitorConfig.toDomain() = HealthMonitoring(
    enabled = isEnabled(),
    window = MinuteWindow(getStart(), getEnd()),
    intervalMinutes = getInterval(),
)

internal fun HealthMonitoring.applyTo(current: FcHealthMonitorConfig): FcHealthMonitorConfig =
    FcHealthMonitorConfig.Builder(current.copyBytes())
        .setEnabled(enabled)
        .setStart(window.startMinute)
        .setEnd(window.endMinute)
        .setInterval(intervalMinutes)
        .create()

internal fun FcHeartRateAlarmConfig.toDomain(lowSupported: Boolean) = HeartRateAlert(
    enabled = isStaticEnabled(),
    highBpm = getStaticValue(),
    lowBpm = getStaticLowValue().takeIf { lowSupported },
)

internal fun HeartRateAlert.applyTo(
    current: FcHeartRateAlarmConfig,
    lowSupported: Boolean,
): FcHeartRateAlarmConfig {
    val builder = FcHeartRateAlarmConfig.Builder(current.copyBytes())
        .setStaticEnabled(enabled)
        .setStaticValue(highBpm)
    val low = lowBpm
    if (lowSupported && low != null) builder.setStaticLowValue(low)
    return builder.create()
}

internal fun FcTurnWristLightingConfig.toDomain() = WristRaise(
    enabled = isEnabled(),
    window = MinuteWindow(getStart(), getEnd()),
)

internal fun WristRaise.applyTo(current: FcTurnWristLightingConfig): FcTurnWristLightingConfig =
    FcTurnWristLightingConfig.Builder(current.copyBytes())
        .setEnabled(enabled)
        .setStart(window.startMinute)
        .setEnd(window.endMinute)
        .create()

// The flag polarity (set = 12-hour, imperial, Fahrenheit) follows the vendor sample and is
// checked against the watch's own settings screen rather than assumed to hold on every firmware.
internal fun FcFunctionConfig.toDisplayUnits() = DisplayUnits(
    use24HourClock = !isFlagEnabled(FcFunctionConfig.Flag.TIME_FORMAT),
    imperialLength = isFlagEnabled(FcFunctionConfig.Flag.LENGTH_UNIT),
    fahrenheit = isFlagEnabled(FcFunctionConfig.Flag.TEMPERATURE_UNIT),
)

internal fun DisplayUnits.applyTo(current: FcFunctionConfig, timeFormatSupported: Boolean): FcFunctionConfig {
    val builder = FcFunctionConfig.Builder(current.copyBytes())
    if (timeFormatSupported) builder.setFlagEnabled(FcFunctionConfig.Flag.TIME_FORMAT, !use24HourClock)
    return builder
        .setFlagEnabled(FcFunctionConfig.Flag.LENGTH_UNIT, imperialLength)
        .setFlagEnabled(FcFunctionConfig.Flag.TEMPERATURE_UNIT, fahrenheit)
        .create()
}

/**
 * Bit `i` of `FcAlarm.repeat` is the day at index `i` here. The order is the vendor
 * convention as far as it is documented; the alarm round trip on the real watch is what
 * confirms it, so it lives in one place.
 */
private val REPEAT_BIT_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

internal fun repeatMaskOf(days: Set<DayOfWeek>): Int =
    REPEAT_BIT_ORDER.foldIndexed(0) { bit, mask, day -> if (day in days) mask or (1 shl bit) else mask }

internal fun repeatDaysOf(mask: Int): Set<DayOfWeek> =
    REPEAT_BIT_ORDER.filterIndexed { bit, _ -> mask and (1 shl bit) != 0 }.toSet()

internal fun FcAlarm.toDomain() = WatchAlarm(
    id = id,
    hour = hour,
    minute = minute,
    repeatDays = repeatDaysOf(repeat),
    enabled = isEnabled,
    label = label.orEmpty(),
)

/** New alarms (id == [WatchAlarm.NEW_ID]) get the lowest id the list isn't already using. */
internal fun List<WatchAlarm>.toFcAlarms(): List<FcAlarm> {
    val built = ArrayList<FcAlarm>(size)
    for (alarm in this) {
        val id = if (alarm.id == WatchAlarm.NEW_ID) FcAlarm.Companion.findNewAlarmId(built) else alarm.id
        built += FcAlarm(id).also {
            it.hour = alarm.hour
            it.minute = alarm.minute
            it.repeat = repeatMaskOf(alarm.repeatDays)
            it.isEnabled = alarm.enabled
            it.label = alarm.label
            it.adjust()
        }
    }
    return built
}

internal fun FcContacts.toDomain() = WatchContact(name = name, number = number)

// The SDK's factory is the only way to build one and returns null for input it rejects.
internal fun WatchContact.toFcContacts(): FcContacts =
    requireNotNull(FcContacts.Companion.create(name, number)) { "the watch rejects this contact" }
