package com.nexwatch.core.syncapi

import com.nexwatch.core.model.HealthRecord
import kotlin.time.Duration

/**
 * §7.1. A provider lives in its own module, registers with Hilt `@IntoSet`, and is driven entirely by the
 * SyncEngine: it never reads Room, talks to the watch, or schedules work itself.
 */
interface SyncProvider {
    /** Stable forever: it keys the provider's `sync_cursor` row and its WorkManager work names. */
    val id: String
    val displayName: String
    val supportedTypes: Set<RecordType>
    val constraints: SyncConstraints

    suspend fun readiness(): Readiness

    /**
     * Must be idempotent: the engine delivers at least once, so the same record (same id, same version) can
     * arrive again after a retry or a re-run backfill.
     */
    suspend fun push(changes: List<RecordChange>): PushOutcome
}

enum class RecordType { STEPS, HEART_RATE, SPO2, BLOOD_PRESSURE, TEMPERATURE, STRESS, SLEEP_SESSION, WORKOUT }

val HealthRecord.recordType: RecordType
    get() = when (this) {
        is HealthRecord.Step -> RecordType.STEPS
        is HealthRecord.HeartRate -> RecordType.HEART_RATE
        is HealthRecord.Spo2 -> RecordType.SPO2
        is HealthRecord.BloodPressure -> RecordType.BLOOD_PRESSURE
        is HealthRecord.Temperature -> RecordType.TEMPERATURE
        is HealthRecord.Stress -> RecordType.STRESS
        is HealthRecord.SleepSession -> RecordType.SLEEP_SESSION
        is HealthRecord.Workout -> RecordType.WORKOUT
    }

data class SyncConstraints(
    val requiresNetwork: Boolean = false,
    val requiresCharging: Boolean = false,
)

sealed interface Readiness {
    data object Ready : Readiness
    data class NeedsPermission(val missing: Set<String>) : Readiness
    data object NeedsAuth : Readiness
    data class Unavailable(val reason: String) : Readiness
}

enum class Op { UPSERT, DELETE }

/** [seq] is the change-log position, or 0 for records sent by a backfill snapshot (§7.3). */
data class RecordChange(val seq: Long, val op: Op, val record: HealthRecord)

sealed interface PushOutcome {
    data object Success : PushOutcome
    data class Retry(val after: Duration? = null) : PushOutcome

    /** Stops the provider and surfaces [reason] in the UI until the user reconnects it. */
    data class Fatal(val reason: String) : PushOutcome
}
