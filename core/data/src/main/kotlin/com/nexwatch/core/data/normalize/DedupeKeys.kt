package com.nexwatch.core.data.normalize

import java.util.UUID

/** §5.1: deterministic IDs, so re-ingesting the same fact always produces the same row. */
internal fun deterministicId(dedupeKey: String): String = UUID.nameUUIDFromBytes(dedupeKey.toByteArray()).toString()

internal fun heartRateDedupeKey(deviceId: String, atMs: Long, origin: String) = "hr:$deviceId:$atMs:$origin"
internal fun spo2DedupeKey(deviceId: String, atMs: Long, origin: String) = "spo2:$deviceId:$atMs:$origin"
internal fun bloodPressureDedupeKey(deviceId: String, atMs: Long, origin: String) = "bp:$deviceId:$atMs:$origin"
internal fun temperatureDedupeKey(deviceId: String, atMs: Long, origin: String) = "temp:$deviceId:$atMs:$origin"
internal fun stressDedupeKey(deviceId: String, atMs: Long, origin: String) = "stress:$deviceId:$atMs:$origin"
internal fun stepsDedupeKey(deviceId: String, endMs: Long) = "steps:$deviceId:$endMs"
internal fun sleepDedupeKey(deviceId: String, nightDate: String) = "sleep:$deviceId:$nightDate"
internal fun workoutDedupeKey(deviceId: String, sportId: String) = "workout:$deviceId:$sportId"
