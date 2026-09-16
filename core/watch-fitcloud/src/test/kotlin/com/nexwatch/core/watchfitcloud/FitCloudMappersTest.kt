package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WeatherCondition
import com.nexwatch.core.watchapi.WeatherDayForecast
import com.nexwatch.core.watchapi.WeatherForecast
import com.nexwatch.core.watchapi.WeatherReading
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncDataType
import com.topstep.fitcloud.sdk.v2.model.message.FcNotificationType
import com.topstep.wearkit.base.connector.ConnectorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FitCloudMappersTest {

    @Test
    fun `connector states collapse onto the four connection-phase WatchStates`() {
        val retryAt = Instant.ofEpochMilli(1_700_000_000_000L)

        assertEquals(
            WatchState.Waiting(retryAt),
            ConnectorState.DISCONNECTED.toWatchState(battery = null, nextRetryAt = retryAt),
        )
        assertEquals(
            WatchState.Waiting(retryAt),
            ConnectorState.PRE_CONNECTING.toWatchState(battery = null, nextRetryAt = retryAt),
        )
        assertEquals(
            WatchState.Connecting,
            ConnectorState.CONNECTING.toWatchState(battery = null, nextRetryAt = retryAt),
        )
        assertEquals(
            WatchState.Connecting,
            ConnectorState.PRE_CONNECTED.toWatchState(battery = null, nextRetryAt = retryAt),
        )
        assertEquals(
            WatchState.Ready(battery = 61),
            ConnectorState.CONNECTED.toWatchState(battery = 61, nextRetryAt = null),
        )
    }

    @Test
    fun `sync data types get stable journal names`() {
        assertEquals("step", syncDataTypeName(FcSyncDataType.STEP))
        assertEquals("sleep", syncDataTypeName(FcSyncDataType.SLEEP))
        assertEquals("today_total", syncDataTypeName(FcSyncDataType.TODAY_TOTAL_DATA))
        // This SDK's "pressure" means stress, not blood pressure — the journal uses ours.
        assertEquals("stress", syncDataTypeName(FcSyncDataType.PRESSURE))
        assertEquals("blood_pressure", syncDataTypeName(FcSyncDataType.BLOOD_PRESSURE))
    }

    @Test
    fun `an unrecognised sync type is still named rather than dropped`() {
        assertEquals("unknown_199", syncDataTypeName(199))
    }

    @Test
    fun `notification types map onto the SDK's constants`() {
        assertEquals(
            FcNotificationType.SMS,
            OutgoingNotification.NotificationType.SMS.toFcNotificationType(),
        )
        assertEquals(
            FcNotificationType.TELEPHONY_INCOMING,
            OutgoingNotification.NotificationType.CALL.toFcNotificationType(),
        )
        assertEquals(
            FcNotificationType.OTHERS_APP,
            OutgoingNotification.NotificationType.OTHERS_APP.toFcNotificationType(),
        )
    }

    @Test
    fun `today's weather takes its range from the first forecast day`() {
        val today = forecast().toFcWeatherToday()

        assertEquals(12, today.currentTemperature)
        assertEquals(4, today.lowTemperature)
        assertEquals(15, today.highTemperature)
    }

    @Test
    fun `today's own temperature stands in when no daily range is available`() {
        val today = forecast(days = emptyList()).toFcWeatherToday()

        assertEquals(12, today.lowTemperature)
        assertEquals(12, today.highTemperature)
    }

    @Test
    fun `the forecast list starts at tomorrow because setWeather takes today separately`() {
        val days = forecast().toFcWeatherForecasts()

        assertEquals(1, days.size)
        assertEquals(2, days.single().lowTemperature)
        assertEquals(9, days.single().highTemperature)
    }

    private fun forecast(
        days: List<WeatherDayForecast> = listOf(
            WeatherDayForecast(WeatherCondition.CLOUDY, highC = 15, lowC = 4),
            WeatherDayForecast(WeatherCondition.RAIN, highC = 9, lowC = 2),
        ),
    ) = WeatherForecast(
        locationName = "Kampala",
        current = WeatherReading(WeatherCondition.SUNNY, temperatureC = 12),
        days = days,
    )
}
