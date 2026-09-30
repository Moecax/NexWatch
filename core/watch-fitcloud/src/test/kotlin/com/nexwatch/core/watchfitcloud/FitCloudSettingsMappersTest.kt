package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.watchapi.DoNotDisturb
import com.nexwatch.core.watchapi.DrinkWaterReminder
import com.nexwatch.core.watchapi.HealthMonitoring
import com.nexwatch.core.watchapi.HeartRateAlert
import com.nexwatch.core.watchapi.MinuteWindow
import com.nexwatch.core.watchapi.SedentaryReminder
import com.nexwatch.core.watchapi.WatchAlarm
import com.nexwatch.core.watchapi.WristRaise
import com.topstep.fitcloud.sdk.v2.model.config.FcDNDConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcDrinkWaterConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcHealthMonitorConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcHeartRateAlarmConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcSedentaryConfig
import com.topstep.fitcloud.sdk.v2.model.config.FcTurnWristLightingConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class FitCloudSettingsMappersTest {

    @Test
    fun `do not disturb survives a write and read through the SDK's own byte layout`() {
        val wanted = DoNotDisturb(allDay = false, scheduled = true, window = MinuteWindow(22 * 60, 7 * 60 + 15))

        val written = wanted.applyTo(FcDNDConfig.Builder().create())

        assertEquals(wanted, written.toDomain())
    }

    @Test
    fun `sedentary reminder round-trips`() {
        val wanted = SedentaryReminder(
            enabled = true,
            window = MinuteWindow(9 * 60, 18 * 60),
            intervalMinutes = 60,
            respectsDoNotDisturb = true,
        )

        assertEquals(wanted, wanted.applyTo(FcSedentaryConfig.Builder().create()).toDomain())
    }

    @Test
    fun `drink water reminder round-trips`() {
        val wanted = DrinkWaterReminder(enabled = true, window = MinuteWindow(8 * 60, 20 * 60), intervalMinutes = 90)

        assertEquals(wanted, wanted.applyTo(FcDrinkWaterConfig.Builder().create()).toDomain())
    }

    @Test
    fun `health monitoring round-trips`() {
        val wanted = HealthMonitoring(enabled = true, window = MinuteWindow(6 * 60, 22 * 60), intervalMinutes = 10)

        assertEquals(wanted, wanted.applyTo(FcHealthMonitorConfig.Builder().create()).toDomain())
    }

    @Test
    fun `wrist raise round-trips`() {
        val wanted = WristRaise(enabled = true, window = MinuteWindow(7 * 60, 21 * 60))

        assertEquals(wanted, wanted.applyTo(FcTurnWristLightingConfig.Builder().create()).toDomain())
    }

    @Test
    fun `heart rate alert leaves the low threshold alone on a watch without one`() {
        val wanted = HeartRateAlert(enabled = true, highBpm = 150, lowBpm = 45)

        val withoutLow = wanted.applyTo(FcHeartRateAlarmConfig.Builder().create(), lowSupported = false)
        val withLow = wanted.applyTo(FcHeartRateAlarmConfig.Builder().create(), lowSupported = true)

        assertEquals(HeartRateAlert(enabled = true, highBpm = 150, lowBpm = null), withoutLow.toDomain(lowSupported = false))
        assertEquals(wanted, withLow.toDomain(lowSupported = true))
    }

    @Test
    fun `repeat days map to one distinct bit each and back`() {
        DayOfWeek.entries.forEach { day ->
            val mask = repeatMaskOf(setOf(day))
            assertEquals(1, Integer.bitCount(mask))
            assertEquals(setOf(day), repeatDaysOf(mask))
        }
        assertEquals(0, repeatMaskOf(emptySet()))
        assertEquals(DayOfWeek.entries.toSet(), repeatDaysOf(repeatMaskOf(DayOfWeek.entries.toSet())))
    }

    @Test
    fun `a new alarm gets an id its siblings aren't using`() {
        val alarms = listOf(
            WatchAlarm(id = WatchAlarm.NEW_ID, hour = 7, minute = 0, repeatDays = setOf(DayOfWeek.MONDAY), enabled = true, label = "Gym"),
            WatchAlarm(id = WatchAlarm.NEW_ID, hour = 8, minute = 30, repeatDays = emptySet(), enabled = false, label = ""),
        )

        val fc = alarms.toFcAlarms()

        assertEquals(2, fc.map { it.id }.toSet().size)
        assertEquals(listOf(7, 8), fc.map { it.hour })
        assertEquals(setOf(DayOfWeek.MONDAY), repeatDaysOf(fc[0].repeat))
    }
}
