package com.nexwatch.core.sync.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nexwatch.core.common.CoroutineDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * [newestWriteAt] is when Health Connect last stored a record of this type for the newest data, which against
 * the local `ingested_at` shows how long a watch sync took to reach Health Connect.
 */
data class HealthConnectAuditRow(
    val type: String,
    val total: Int,
    val distinctClientIds: Int,
    val newestStart: Instant?,
    val newestWriteAt: Instant?,
)

/**
 * Debug-only check for Phase 9's exit criteria: how many records this app has in Health Connect per type,
 * and whether any clientRecordId appears twice. Needs the read permissions that only the debug manifest
 * declares.
 */
class HealthConnectAudit @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun run(): List<HealthConnectAuditRow> = withContext(dispatchers.io) {
        val client = HealthConnectClient.getOrCreate(context)
        AUDITED.map { type ->
            val clientIds = mutableListOf<String?>()
            var newest: Record? = null
            var pageToken: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = type,
                        timeRangeFilter = TimeRangeFilter.after(Instant.EPOCH),
                        dataOriginFilter = setOf(DataOrigin(context.packageName)),
                        pageSize = 5_000,
                        pageToken = pageToken,
                    ),
                )
                response.records.mapTo(clientIds) { it.metadata.clientRecordId }
                newest = (response.records + listOfNotNull(newest)).maxByOrNull { it.start() }
                pageToken = response.pageToken
            } while (pageToken != null)
            HealthConnectAuditRow(
                type = type.simpleName.orEmpty(),
                total = clientIds.size,
                distinctClientIds = clientIds.toSet().size,
                newestStart = newest?.start(),
                newestWriteAt = newest?.metadata?.lastModifiedTime,
            )
        }
    }

    // IntervalRecord and InstantRecord are internal to the client library, so each audited type is listed.
    private fun Record.start(): Instant = when (this) {
        is StepsRecord -> startTime
        is DistanceRecord -> startTime
        is ActiveCaloriesBurnedRecord -> startTime
        is HeartRateRecord -> startTime
        is SleepSessionRecord -> startTime
        is ExerciseSessionRecord -> startTime
        is OxygenSaturationRecord -> time
        is BloodPressureRecord -> time
        else -> Instant.EPOCH
    }

    companion object {
        private val AUDITED: List<KClass<out Record>> = listOf(
            StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class, HeartRateRecord::class,
            OxygenSaturationRecord::class, BloodPressureRecord::class, SleepSessionRecord::class,
            ExerciseSessionRecord::class,
        )

        val readPermissions: Set<String> = AUDITED.map { HealthPermission.getReadPermission(it) }.toSet()
    }
}
