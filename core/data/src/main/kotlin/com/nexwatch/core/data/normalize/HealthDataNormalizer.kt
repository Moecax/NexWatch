package com.nexwatch.core.data.normalize

import androidx.room.Transactor.SQLiteTransactionType
import androidx.room.useWriterConnection
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.BloodPressureEntity
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageDb
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.Spo2Entity
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.StressEntity
import com.nexwatch.core.database.TemperatureEntity
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutHrEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val DAY_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * §5.2. One transaction per type per batch; a decode/insert failure for one row leaves that
 * row unprocessed (retried next run) without blocking the rest of the same type's batch.
 */
class HealthDataNormalizer @Inject constructor(
    private val db: NexWatchDatabase,
    private val decoder: HealthDataDecoder,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun processUnprocessed(): Set<String> = withContext(dispatchers.default) {
        val device = db.deviceDao().observeMostRecentlyBound().first() ?: return@withContext emptySet()
        val zone = ZoneId.systemDefault()
        val affectedDates = mutableSetOf<String>()

        for (dataType in db.rawIngestDao().findUnprocessedDataTypes()) {
            val rows = db.rawIngestDao().findUnprocessed(dataType)
            for (row in rows) {
                try {
                    val records = decoder.decode(row.dataType, row.payloadJson)
                    db.useWriterConnection { transactor ->
                        transactor.withTransaction(SQLiteTransactionType.DEFERRED) {
                            for (record in records) {
                                processRecord(record, device.address, zone, row.dataType)?.let(affectedDates::add)
                            }
                        }
                    }
                    db.rawIngestDao().update(row.copy(processedAt = System.currentTimeMillis()))
                } catch (e: Exception) {
                    db.rawIngestDao().update(row.copy(error = e.message ?: e.toString()))
                }
            }
        }
        affectedDates
    }

    /**
     * §5.1: the dedupe key includes origin, and "_measure" data types are an explicit
     * on-watch measurement (MEASURE) rather than continuous background sampling (MONITOR) —
     * the raw_ingest row's own data_type string (not the decoded record) is what carries
     * that distinction, since HeartRate/Spo2/etc. don't otherwise know which sync type
     * produced them.
     */
    private fun originFor(sourceDataType: String): Origin =
        if (sourceDataType.endsWith("_measure")) Origin.MEASURE else Origin.MONITOR

    /** Returns the affected local date string, if this record type contributes to daily_summary. */
    private suspend fun processRecord(
        record: DecodedHealthRecord,
        deviceId: String,
        zone: ZoneId,
        sourceDataType: String,
    ): String? {
        val origin = originFor(sourceDataType)
        return when (record) {
            is DecodedHealthRecord.Step -> {
                val key = stepsDedupeKey(deviceId, record.endMs)
                db.stepsDao().insertAll(
                    listOf(
                        StepsEntity(
                            deterministicId(key),
                            recordMeta(key, deviceId, record.startMs, record.endMs, Origin.MONITOR),
                            record.count, record.distanceM, record.kcal,
                        ),
                    ),
                )
                dateOf(record.endMs, zone)
            }
            is DecodedHealthRecord.HeartRate -> {
                val key = heartRateDedupeKey(deviceId, record.atMs, origin.name)
                db.healthSampleDao().insertHeartRate(
                    listOf(HeartRateEntity(deterministicId(key), recordMeta(key, deviceId, record.atMs, record.atMs, origin), record.bpm)),
                )
                dateOf(record.atMs, zone)
            }
            is DecodedHealthRecord.Spo2 -> {
                val key = spo2DedupeKey(deviceId, record.atMs, origin.name)
                db.healthSampleDao().insertSpo2(
                    listOf(Spo2Entity(deterministicId(key), recordMeta(key, deviceId, record.atMs, record.atMs, origin), record.percent)),
                )
                dateOf(record.atMs, zone)
            }
            is DecodedHealthRecord.BloodPressure -> {
                val key = bloodPressureDedupeKey(deviceId, record.atMs, origin.name)
                db.healthSampleDao().insertBloodPressure(
                    listOf(BloodPressureEntity(deterministicId(key), recordMeta(key, deviceId, record.atMs, record.atMs, origin), record.systolic, record.diastolic)),
                )
                dateOf(record.atMs, zone)
            }
            is DecodedHealthRecord.Temperature -> {
                val key = temperatureDedupeKey(deviceId, record.atMs, origin.name)
                db.healthSampleDao().insertTemperature(
                    listOf(TemperatureEntity(deterministicId(key), recordMeta(key, deviceId, record.atMs, record.atMs, origin), record.celsius)),
                )
                dateOf(record.atMs, zone)
            }
            is DecodedHealthRecord.Stress -> {
                val key = stressDedupeKey(deviceId, record.atMs, origin.name)
                db.healthSampleDao().insertStress(
                    listOf(StressEntity(deterministicId(key), recordMeta(key, deviceId, record.atMs, record.atMs, origin), record.level)),
                )
                dateOf(record.atMs, zone)
            }
            is DecodedHealthRecord.Sleep -> processSleep(record, deviceId, zone)
            is DecodedHealthRecord.Workout -> processWorkout(record, deviceId, zone)
            is DecodedHealthRecord.WorkoutRoute -> processWorkoutRoute(record, deviceId)
            is DecodedHealthRecord.TodayTotal -> null // updates daily_summary.live_steps_total directly; see Task 16
        }
    }

    private suspend fun processSleep(record: DecodedHealthRecord.Sleep, deviceId: String, zone: ZoneId): String {
        val nightDate = dateOf(record.startMs, zone)
        val contentHash = sleepContentHash(record)
        val existing = db.sleepDao().findByNight(deviceId, nightDate)
        if (existing != null && existing.contentHash == contentHash) return nightDate // unchanged, nothing to replace

        val key = sleepDedupeKey(deviceId, nightDate)
        val nextVersion = (existing?.meta?.version ?: 0) + 1
        val session = SleepSessionEntity(
            deterministicId(key),
            recordMeta(key, deviceId, record.startMs, record.endMs, Origin.MONITOR).copy(version = nextVersion),
            nightDate, contentHash, record.score, record.efficiency,
        )
        val stages = record.stages.map {
            SleepStageEntity(
                sessionId = session.pk,
                stage = SleepStageDb.valueOf(it.stage.name),
                startTime = it.startMs,
                endTime = it.endMs,
            )
        }
        db.sleepDao().replaceNight(session, stages)
        return nightDate
    }

    private suspend fun processWorkout(record: DecodedHealthRecord.Workout, deviceId: String, zone: ZoneId): String {
        val key = workoutDedupeKey(deviceId, record.sportId)
        val workout = WorkoutEntity(
            deterministicId(key),
            recordMeta(key, deviceId, record.startMs, record.endMs, Origin.MONITOR),
            record.sportId, record.sportType, ((record.endMs - record.startMs) / 1000).toInt(),
            record.distanceM, record.kcal, record.avgHrBpm, record.maxHrBpm, record.steps,
        )
        db.workoutDao().insertWorkouts(listOf(workout))
        if (record.heartRateSeries.isNotEmpty()) {
            db.workoutDao().insertHeartRateSeries(
                record.heartRateSeries.map { WorkoutHrEntity(workoutId = workout.pk, atMs = it.atMs, bpm = it.bpm) },
            )
        }
        return dateOf(record.startMs, zone)
    }

    /** May arrive before its parent workout is normalized — returns null (no date) if so, retried next run. */
    private suspend fun processWorkoutRoute(record: DecodedHealthRecord.WorkoutRoute, deviceId: String): String? {
        val workout = db.workoutDao().findBySportId(deviceId, record.sportId) ?: return null
        db.workoutDao().insertRoute(
            record.points.map {
                WorkoutRouteEntity(
                    workoutId = workout.pk,
                    atMs = workout.meta.startTime + it.offsetSeconds * 1000L,
                    lat = it.lat, lon = it.lon, altitudeM = it.altitudeM,
                )
            },
        )
        return null // route points don't independently affect daily_summary
    }

    private fun recordMeta(dedupeKey: String, deviceId: String, startMs: Long, endMs: Long, origin: Origin) = RecordMeta(
        dedupeKey = dedupeKey, deviceId = deviceId, startTime = startMs, endTime = endMs,
        zoneOffsetSec = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(startMs)).totalSeconds,
        origin = origin, ingestedAt = System.currentTimeMillis(),
    )

    private fun dateOf(atMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().format(DAY_DATE_FORMAT)

    private fun sleepContentHash(record: DecodedHealthRecord.Sleep): String {
        val digest = MessageDigest.getInstance("SHA-256")
        record.stages.forEach { digest.update("${it.stage}:${it.startMs}:${it.endMs}".toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
