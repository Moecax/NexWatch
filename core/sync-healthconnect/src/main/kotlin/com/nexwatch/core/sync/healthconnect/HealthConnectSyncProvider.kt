package com.nexwatch.core.sync.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.syncapi.Op
import com.nexwatch.core.syncapi.PushOutcome
import com.nexwatch.core.syncapi.Readiness
import com.nexwatch.core.syncapi.RecordChange
import com.nexwatch.core.syncapi.RecordType
import com.nexwatch.core.syncapi.SyncConstraints
import com.nexwatch.core.syncapi.SyncProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "HealthConnectSync"

// Health Connect caps the memory of one bulk insert without publishing the number; 500 small records
// stays well inside it.
private const val INSERT_CHUNK = 500

/** §7.5. Local, no network, no account: Health Connect is the provider that proves the §7.1 abstraction. */
@Singleton
class HealthConnectSyncProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : SyncProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Health Connect"
    override val supportedTypes: Set<RecordType> = setOf(
        RecordType.STEPS, RecordType.HEART_RATE, RecordType.SPO2, RecordType.BLOOD_PRESSURE,
        RecordType.SLEEP_SESSION, RecordType.WORKOUT,
    )
    override val constraints: SyncConstraints = SyncConstraints()

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun readiness(): Readiness = withContext(dispatchers.io) {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val missing = HealthConnectPermissions.required - client.permissionController.getGrantedPermissions()
                if (missing.isEmpty()) Readiness.Ready else Readiness.NeedsPermission(missing)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                Readiness.Unavailable("Health Connect needs an update")
            else -> Readiness.Unavailable("Health Connect isn't available on this phone")
        }
    }

    override suspend fun push(changes: List<RecordChange>): PushOutcome = withContext(dispatchers.io) {
        try {
            val (deletes, upserts) = changes.partition { it.op == Op.DELETE }
            insert(upserts.flatMap { HealthConnectRecordMapper.toRecords(it.record) })
            deletes.flatMap { HealthConnectRecordMapper.refsFor(it.record) }
                .groupBy({ it.type }, { it.clientRecordId })
                .forEach { (type, clientIds) -> client.deleteRecords(type, emptyList(), clientIds) }
            PushOutcome.Success
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            PushOutcome.Fatal("Health Connect permission revoked")
        } catch (e: Exception) {
            // Remote errors and rate limiting surface as assorted runtime exceptions; all of them are worth
            // another attempt later rather than stopping the provider.
            Log.w(TAG, "Push of ${changes.size} changes failed; retrying later", e)
            PushOutcome.Retry()
        }
    }

    private suspend fun insert(records: List<Record>) {
        records.chunked(INSERT_CHUNK).forEach { chunk ->
            try {
                client.insertRecords(chunk)
            } catch (_: IllegalArgumentException) {
                // Health Connect validates more than its constructors do. Insert one at a time so a single
                // rejected record is skipped instead of failing the whole batch forever.
                var skipped = 0
                chunk.forEach { record ->
                    try {
                        client.insertRecords(listOf(record))
                    } catch (_: IllegalArgumentException) {
                        skipped++
                    }
                }
                Log.w(TAG, "Health Connect rejected $skipped of ${chunk.size} records; skipped them")
            }
        }
    }

    companion object {
        const val PROVIDER_ID = "health_connect"
    }
}
