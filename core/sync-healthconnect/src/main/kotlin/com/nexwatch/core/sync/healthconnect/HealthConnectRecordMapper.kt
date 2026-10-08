package com.nexwatch.core.sync.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.model.SleepStage
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/** One Health Connect record we write, addressed the way a later delete needs it. */
internal data class HealthConnectRef(val type: KClass<out Record>, val clientRecordId: String)

private val WATCH = Device(type = Device.TYPE_WATCH)

/**
 * §7.5. Each Health Connect record's clientRecordId is the canonical record's deterministic id (plus a
 * suffix where one fact becomes several records), and its clientRecordVersion is the canonical version, so
 * re-sending a record overwrites its earlier copy instead of adding a second one.
 *
 * Anything Health Connect's own constructors reject (zero steps, an out-of-range bpm, an empty interval) is
 * dropped here rather than failing the batch: one malformed row must not stall the whole provider.
 */
internal object HealthConnectRecordMapper {

    fun toRecords(record: HealthRecord): List<Record> = when (record) {
        is HealthRecord.Step -> stepRecords(record)
        is HealthRecord.HeartRate -> listOfNotNull(valid {
            val at = Instant.ofEpochMilli(record.startMs)
            HeartRateRecord(at, record.offset, at, record.offset, listOf(HeartRateRecord.Sample(at, record.bpm.toLong())),
                record.metadata(record.id))
        })
        is HealthRecord.Spo2 -> listOfNotNull(valid {
            OxygenSaturationRecord(Instant.ofEpochMilli(record.startMs), record.offset, Percentage(record.percent.toDouble()),
                record.metadata(record.id))
        })
        is HealthRecord.BloodPressure -> listOfNotNull(valid {
            BloodPressureRecord(
                time = Instant.ofEpochMilli(record.startMs),
                zoneOffset = record.offset,
                metadata = record.metadata(record.id),
                systolic = Pressure.millimetersOfMercury(record.systolic.toDouble()),
                diastolic = Pressure.millimetersOfMercury(record.diastolic.toDouble()),
            )
        })
        is HealthRecord.SleepSession -> listOfNotNull(sleepRecord(record))
        is HealthRecord.Workout -> workoutRecords(record)
        // Not in supportedTypes: the GTR 3 Pro reports neither, and Health Connect has no stress type.
        is HealthRecord.Temperature, is HealthRecord.Stress -> emptyList()
    }

    fun refsFor(record: HealthRecord): List<HealthConnectRef> = when (record) {
        is HealthRecord.Step -> listOf(
            HealthConnectRef(StepsRecord::class, record.id),
            HealthConnectRef(DistanceRecord::class, "${record.id}/distance"),
            HealthConnectRef(ActiveCaloriesBurnedRecord::class, "${record.id}/energy"),
        )
        is HealthRecord.HeartRate -> listOf(HealthConnectRef(HeartRateRecord::class, record.id))
        is HealthRecord.Spo2 -> listOf(HealthConnectRef(OxygenSaturationRecord::class, record.id))
        is HealthRecord.BloodPressure -> listOf(HealthConnectRef(BloodPressureRecord::class, record.id))
        is HealthRecord.SleepSession -> listOf(HealthConnectRef(SleepSessionRecord::class, record.id))
        is HealthRecord.Workout -> listOf(
            HealthConnectRef(ExerciseSessionRecord::class, record.id),
            HealthConnectRef(HeartRateRecord::class, "${record.id}/hr"),
        )
        is HealthRecord.Temperature, is HealthRecord.Stress -> emptyList()
    }

    private fun stepRecords(record: HealthRecord.Step): List<Record> {
        val start = Instant.ofEpochMilli(record.startMs)
        val end = Instant.ofEpochMilli(record.endMs)
        val offset = record.offset
        return listOfNotNull<Record>(
            valid { StepsRecord(start, offset, end, offset, record.count.toLong(), record.metadata(record.id)) },
            if (record.distanceM > 0f) {
                valid {
                    DistanceRecord(start, offset, end, offset, Length.meters(record.distanceM.toDouble()),
                        record.metadata("${record.id}/distance"))
                }
            } else null,
            if (record.energyKcal > 0f) {
                valid {
                    ActiveCaloriesBurnedRecord(start, offset, end, offset, Energy.kilocalories(record.energyKcal.toDouble()),
                        record.metadata("${record.id}/energy"))
                }
            } else null,
        )
    }

    private fun sleepRecord(record: HealthRecord.SleepSession): Record? = valid {
        // Health Connect rejects overlapping stages and stages outside the session, so the watch's spans are
        // sorted and clipped rather than trusted.
        var cursor = record.startMs
        val stages = record.stages.sortedBy { it.startMs }.mapNotNull { span ->
            val start = maxOf(span.startMs, cursor)
            val end = minOf(span.endMs, record.endMs)
            if (end <= start) return@mapNotNull null
            cursor = end
            SleepSessionRecord.Stage(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), span.stage.toHealthConnect())
        }
        SleepSessionRecord(
            startTime = Instant.ofEpochMilli(record.startMs),
            startZoneOffset = record.offset,
            endTime = Instant.ofEpochMilli(record.endMs),
            endZoneOffset = record.offset,
            metadata = record.metadata(record.id),
            stages = stages,
        )
    }

    private fun workoutRecords(record: HealthRecord.Workout): List<Record> {
        val start = Instant.ofEpochMilli(record.startMs)
        val end = Instant.ofEpochMilli(record.endMs)
        val locations = record.route.mapNotNull { point ->
            val at = record.startMs + point.offsetSeconds * 1000L
            if (at < record.startMs || at > record.endMs) return@mapNotNull null
            valid {
                ExerciseRoute.Location(
                    time = Instant.ofEpochMilli(at),
                    latitude = point.lat,
                    longitude = point.lon,
                    altitude = point.altitudeM?.let { Length.meters(it.toDouble()) },
                )
            }
        }.distinctBy { it.time }
        val session = valid {
            ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = record.offset,
                endTime = end,
                endZoneOffset = record.offset,
                metadata = record.metadata(record.id),
                // The SDK documents no sport-type table and no workout has been recorded on the real watch yet,
                // so every workout is "other" until the codes are confirmed (§12 Phase 9).
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                exerciseRoute = if (locations.isEmpty()) null else ExerciseRoute(locations),
            )
        }
        val samples = record.heartRateSeries
            .filter { it.atMs in record.startMs..record.endMs && it.bpm in 1..300 }
            .distinctBy { it.atMs }
            .map { HeartRateRecord.Sample(Instant.ofEpochMilli(it.atMs), it.bpm.toLong()) }
        val heartRate = if (samples.isEmpty()) null else valid {
            HeartRateRecord(start, record.offset, end, record.offset, samples, record.metadata("${record.id}/hr"))
        }
        return listOfNotNull<Record>(session, heartRate)
    }

    private fun SleepStage.toHealthConnect(): Int = when (this) {
        SleepStage.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
        SleepStage.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
        SleepStage.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
        SleepStage.REM -> SleepSessionRecord.STAGE_TYPE_REM
    }

    private val HealthRecord.offset: ZoneOffset get() = ZoneOffset.ofTotalSeconds(zoneOffsetSec)

    private fun HealthRecord.metadata(clientRecordId: String) =
        Metadata.autoRecorded(device = WATCH, clientRecordId = clientRecordId, clientRecordVersion = version.toLong())

    private inline fun <T> valid(build: () -> T): T? = try {
        build()
    } catch (_: IllegalArgumentException) {
        null
    }
}
