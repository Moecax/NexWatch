package com.nexwatch.core.watchapi

/**
 * Pushed by WeatherWorker (§8.7) via WatchClient.pushWeather().
 */
enum class WeatherCondition { SUNNY, CLOUDY, RAIN, SNOW, STORM, FOG }

data class WeatherReading(val condition: WeatherCondition, val temperatureC: Int)

data class WeatherDayForecast(val condition: WeatherCondition, val highC: Int, val lowC: Int)

data class WeatherForecast(
    val locationName: String,
    val current: WeatherReading,
    val days: List<WeatherDayForecast>,
)
