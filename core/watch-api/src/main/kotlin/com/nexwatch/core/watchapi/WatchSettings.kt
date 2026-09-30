package com.nexwatch.core.watchapi

import java.time.DayOfWeek

/** A daily span, in minutes since local midnight. `end` may be earlier than `start` (overnight). */
data class MinuteWindow(val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY && endMinute in 0 until MINUTES_PER_DAY) {
            "MinuteWindow bounds must be within one day: $startMinute..$endMinute"
        }
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

data class DoNotDisturb(
    val allDay: Boolean,
    val scheduled: Boolean,
    val window: MinuteWindow,
)

data class WatchAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** Empty means a one-off alarm for the next occurrence of [hour]:[minute]. */
    val repeatDays: Set<DayOfWeek>,
    val enabled: Boolean,
    val label: String,
) {
    companion object {
        /** For an alarm the watch hasn't seen yet; the client assigns a real id on write. */
        const val NEW_ID = -1
    }
}

data class SedentaryReminder(
    val enabled: Boolean,
    val window: MinuteWindow,
    val intervalMinutes: Int,
    /** The SDK's `isDNDEnabled` flag on this reminder; its exact watch-side behaviour is unverified. */
    val respectsDoNotDisturb: Boolean,
)

data class DrinkWaterReminder(
    val enabled: Boolean,
    val window: MinuteWindow,
    val intervalMinutes: Int,
)

data class HealthMonitoring(
    val enabled: Boolean,
    val window: MinuteWindow,
    val intervalMinutes: Int,
)

data class HeartRateAlert(
    val enabled: Boolean,
    val highBpm: Int,
    /** Null when the watch has no low-heart-rate alert. */
    val lowBpm: Int?,
)

data class WristRaise(
    val enabled: Boolean,
    val window: MinuteWindow,
)

data class DisplayUnits(
    val use24HourClock: Boolean,
    val imperialLength: Boolean,
    val fahrenheit: Boolean,
)

data class WatchContact(val name: String, val number: String)

/**
 * A snapshot of what the watch itself reports, read back through
 * [WatchClient.readSettings]. A group is `null` when the watch doesn't support it (§4.5),
 * so the UI shows only what the connected watch can actually do.
 */
data class WatchSettings(
    val doNotDisturb: DoNotDisturb? = null,
    val alarms: List<WatchAlarm>? = null,
    val sedentaryReminder: SedentaryReminder? = null,
    val drinkWaterReminder: DrinkWaterReminder? = null,
    val healthMonitoring: HealthMonitoring? = null,
    val heartRateAlert: HeartRateAlert? = null,
    val wristRaise: WristRaise? = null,
    val displayUnits: DisplayUnits? = null,
    val contacts: List<WatchContact>? = null,
)

/**
 * One write to the watch (§4.2's `applySettings`). Each subtype replaces its whole group,
 * because the watch stores every group as a single unit and a partial update would have to
 * be reassembled from a stale copy.
 */
sealed interface WatchSettingChange {
    data class SetDoNotDisturb(val value: DoNotDisturb) : WatchSettingChange
    data class SetAlarms(val value: List<WatchAlarm>) : WatchSettingChange
    data class SetSedentaryReminder(val value: SedentaryReminder) : WatchSettingChange
    data class SetDrinkWaterReminder(val value: DrinkWaterReminder) : WatchSettingChange
    data class SetHealthMonitoring(val value: HealthMonitoring) : WatchSettingChange
    data class SetHeartRateAlert(val value: HeartRateAlert) : WatchSettingChange
    data class SetWristRaise(val value: WristRaise) : WatchSettingChange
    data class SetDisplayUnits(val value: DisplayUnits) : WatchSettingChange
    data class SetContacts(val value: List<WatchContact>) : WatchSettingChange
}
