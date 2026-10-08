package com.nexwatch.core.data.syncengine

import com.nexwatch.core.database.ChangeLogDao
import com.nexwatch.core.database.SyncCursorDao
import com.nexwatch.core.database.SyncCursorEntity
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.syncapi.Op
import com.nexwatch.core.syncapi.PushOutcome
import com.nexwatch.core.syncapi.Readiness
import com.nexwatch.core.syncapi.RecordChange
import com.nexwatch.core.syncapi.RecordType
import com.nexwatch.core.syncapi.SyncProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

internal const val SYNC_BATCH_SIZE = 500

sealed interface SyncRunResult {
    data object CaughtUp : SyncRunResult
    data object NotEnabled : SyncRunResult
    data class NotReady(val readiness: Readiness) : SyncRunResult
    data class Retry(val after: Duration?) : SyncRunResult
    data class Fatal(val reason: String) : SyncRunResult
}

/** Persisted in `sync_cursor.snapshot_state` while a backfill runs, so an interrupted one resumes (§7.3). */
@Serializable
internal data class SnapshotState(val type: RecordType, val afterId: String, val pushed: Int, val total: Int)

internal fun decodeSnapshotState(json: String): SnapshotState = Json.decodeFromString(json)

/**
 * §7.2/§7.3. Drives one provider from its cursor to the head of the change log: a snapshot of every
 * supported table first, then the log itself. Delivery is at-least-once; providers make it exactly-once by
 * upserting on the record's deterministic id.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards SyncProvider>,
    private val prefs: SyncPrefs,
    private val cursorDao: SyncCursorDao,
    private val changeLogDao: ChangeLogDao,
    private val reader: SyncRecordReader,
) {
    // The after-ingest work and the periodic work have different unique names, so WorkManager alone
    // doesn't stop them overlapping, and two runs on one cursor would each push the same batch.
    private val locks = mutableMapOf<String, Mutex>()

    private fun lockFor(providerId: String): Mutex = synchronized(locks) { locks.getOrPut(providerId) { Mutex() } }

    suspend fun run(providerId: String): SyncRunResult = lockFor(providerId).withLock {
        val provider = providers.firstOrNull { it.id == providerId } ?: return SyncRunResult.NotEnabled
        if (providerId !in prefs.enabledProviderIds.first()) return SyncRunResult.NotEnabled

        val readiness = provider.readiness()
        if (readiness != Readiness.Ready) {
            cursorDao.find(providerId)?.let { cursorDao.upsert(it.copy(lastError = readiness.describe())) }
            return SyncRunResult.NotReady(readiness)
        }

        val types = RecordType.entries.filter { it in provider.supportedTypes }
        var cursor = cursorDao.find(providerId) ?: startSnapshot(providerId, types)

        cursor.snapshotState?.let { json ->
            when (val result = runSnapshot(provider, cursor, decodeSnapshotState(json), types)) {
                is StepResult.Done -> cursor = result.cursor
                is StepResult.Stopped -> return result.result
            }
        }
        when (val result = runTail(provider, cursor, types)) {
            is StepResult.Done -> SyncRunResult.CaughtUp
            is StepResult.Stopped -> result.result
        }
    }

    /** Waits for any run in flight, so the cursor it deletes can't be written back by that run. */
    suspend fun forgetCursor(providerId: String) = lockFor(providerId).withLock { cursorDao.delete(providerId) }

    private sealed interface StepResult {
        data class Done(val cursor: SyncCursorEntity) : StepResult
        data class Stopped(val result: SyncRunResult) : StepResult
    }

    private suspend fun startSnapshot(providerId: String, types: List<RecordType>): SyncCursorEntity {
        // Read the head before counting: anything written after this point gets a higher seq and is
        // picked up by the tail, so a record can be sent twice but never missed.
        val head = changeLogDao.latestSeq() ?: 0L
        val total = types.sumOf { reader.count(it) }
        val state = types.firstOrNull()?.let { SnapshotState(it, afterId = "", pushed = 0, total = total) }
        val cursor = SyncCursorEntity(
            providerId,
            lastSeq = head,
            snapshotState = state?.let { Json.encodeToString(it) },
            lastSuccessAt = null,
            lastError = null,
        )
        cursorDao.upsert(cursor)
        return cursor
    }

    private suspend fun runSnapshot(
        provider: SyncProvider,
        initial: SyncCursorEntity,
        initialState: SnapshotState,
        types: List<RecordType>,
    ): StepResult {
        var cursor = initial
        var state = initialState
        var typeIndex = types.indexOf(state.type)
        while (typeIndex in types.indices) {
            val page = reader.page(types[typeIndex], state.afterId, SYNC_BATCH_SIZE)
            if (page.isEmpty()) {
                typeIndex++
                state = state.copy(type = types.getOrNull(typeIndex) ?: break, afterId = "")
            } else {
                when (val outcome = provider.push(page.map { RecordChange(seq = 0, op = it.op, record = it) })) {
                    PushOutcome.Success -> state = state.copy(afterId = page.last().id, pushed = state.pushed + page.size)
                    is PushOutcome.Retry -> return StepResult.Stopped(SyncRunResult.Retry(outcome.after))
                    is PushOutcome.Fatal -> return StepResult.Stopped(fail(provider.id, cursor, outcome.reason))
                }
            }
            cursor = cursor.copy(snapshotState = Json.encodeToString(state), lastError = null)
            cursorDao.upsert(cursor)
        }
        cursor = cursor.copy(snapshotState = null, lastSuccessAt = System.currentTimeMillis(), lastError = null)
        cursorDao.upsert(cursor)
        return StepResult.Done(cursor)
    }

    private suspend fun runTail(provider: SyncProvider, initial: SyncCursorEntity, types: List<RecordType>): StepResult {
        var cursor = initial
        val tables = types.map { it.tableName }
        while (tables.isNotEmpty()) {
            // SQLite has one writer, so every seq at or below the committed MAX(seq) is already visible.
            val head = changeLogDao.latestSeq() ?: cursor.lastSeq
            if (head <= cursor.lastSeq) break
            val entries = changeLogDao.findRange(cursor.lastSeq, head, tables, SYNC_BATCH_SIZE)

            // A record changed several times in one batch is pushed once, at its current version.
            val latestSeqByRecord = entries.groupBy { it.recordType to it.recordId }
                .mapValues { (_, changes) -> changes.maxOf { it.seq } }
            val changes = latestSeqByRecord.keys.groupBy({ it.first }, { it.second }).flatMap { (table, ids) ->
                val type = recordTypeForTable(table) ?: return@flatMap emptyList()
                reader.byIds(type, ids).map { RecordChange(latestSeqByRecord.getValue(table to it.id), it.op, it) }
            }.sortedBy { it.seq }

            if (changes.isNotEmpty()) {
                when (val outcome = provider.push(changes)) {
                    PushOutcome.Success -> Unit
                    is PushOutcome.Retry -> return StepResult.Stopped(SyncRunResult.Retry(outcome.after))
                    is PushOutcome.Fatal -> return StepResult.Stopped(fail(provider.id, cursor, outcome.reason))
                }
            }
            // A short page means everything up to head that this provider cares about is done. Moving to head
            // rather than the last matching seq keeps unsupported types from pinning compaction (§7.4).
            val advancedTo = if (entries.size < SYNC_BATCH_SIZE) head else entries.last().seq
            cursor = cursor.copy(lastSeq = advancedTo)
            cursorDao.upsert(cursor)
        }
        cursor = cursor.copy(lastSuccessAt = System.currentTimeMillis(), lastError = null)
        cursorDao.upsert(cursor)
        return StepResult.Done(cursor)
    }

    /** The cursor is kept: "Reconnect" resumes from it, and compaction keeps the log above it (§7.4). */
    private suspend fun fail(providerId: String, cursor: SyncCursorEntity, reason: String): SyncRunResult {
        cursorDao.upsert(cursor.copy(lastError = reason))
        prefs.setEnabled(providerId, false)
        return SyncRunResult.Fatal(reason)
    }

    private val HealthRecord.op: Op get() = if (deleted) Op.DELETE else Op.UPSERT
}

internal fun Readiness.describe(): String = when (this) {
    Readiness.Ready -> "Ready"
    is Readiness.NeedsPermission -> "Needs permission"
    Readiness.NeedsAuth -> "Needs sign-in"
    is Readiness.Unavailable -> reason
}
