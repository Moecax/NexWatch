package com.nexwatch.core.model

/** One time bucket of heart rate, pre-aggregated in SQL (§9: charts never load raw samples). */
data class HeartRateRange(val atMs: Long, val avgBpm: Int, val minBpm: Int, val maxBpm: Int)

data class Spo2Reading(val atMs: Long, val percent: Int)

data class BloodPressureReading(val atMs: Long, val systolic: Int, val diastolic: Int)

/** Per local calendar day; [date] is ISO `yyyy-MM-dd`. */
data class DailyHeartRate(val date: String, val avgBpm: Int, val minBpm: Int, val maxBpm: Int)

data class DailySpo2(val date: String, val avgPercent: Int, val minPercent: Int)

data class DailyBloodPressure(val date: String, val systolic: Int, val diastolic: Int)
