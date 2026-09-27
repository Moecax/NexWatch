# Phase 7 — Export / import (M4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the JSONL+ZIP exporter, flattened CSV, per-workout GPX, the importer, and optional scheduled auto-backup (§6), closing Phase 7's two exit criteria: the export/import round-trip test is green (every table identical, IDs included), and exporting a year of data holds constant memory.

**Architecture:** `:core:export` may only depend on `:core:model` and `:core:data` (never `:core:database` directly — Room entities never leave `:core:database`/`:core:data`). So this plan adds a full-fidelity canonical record type, `HealthRecord` (sealed interface, one variant per `RecordMeta`-bearing table, `@Serializable`), to `:core:model` — the same type §7.1 already presupposes for `SyncProvider`'s `RecordChange`, so Phase 9 reuses it rather than inventing its own. A new `ExportRepository` in `:core:data` is the only thing that touches Room: it exposes keyset-paginated reads (`WHERE pk > :afterId ORDER BY pk LIMIT :limit`, per §6.2, never `OFFSET`) mapped to `HealthRecord`, and batched inserts for the importer's path back through the normal DAOs (so dedup-by-deterministic-ID and change-log triggers apply exactly as they do for a live sync). Deliberately, no DAO method in this plan ever returns an unpaged "all rows" list for a `RecordMeta`-bearing table — the constant-memory exit criterion is enforced structurally (the API makes an O(n) load impossible to write by accident), not just by a runtime measurement, which is also why the plan's memory test asserts multi-page correctness at scale rather than sampling JVM heap (flaky, and not how this codebase's existing Room tests work — see `docs/superpowers/plans/2026-09-20-phase-6-data-core.md`'s JVM-only `inMemoryTestDatabase()` pattern, reused here). `JsonlZipExporter` (`:core:export`) streams each table straight from a page into a `ZipOutputStream` entry and discards the page before fetching the next one; `ZipImporter` reads the same format back page-by-page and inserts through `ExportRepository`. `GpxExporter` is a separate, single-workout entry point per §6.1's closing line ("GPX export per workout is a separate small exporter"), not bundled into the main ZIP. `BackupWorker` (§6.4, optional) reuses `JsonlZipExporter` unchanged, writing to a user-picked SAF folder on a weekly, charging-and-not-low-battery schedule (§8.7's row for it already exists in the spec).

**Tech Stack:** Kotlin, kotlinx-serialization-json (already used for nav routes in `:app`; new here for `:core:model`/`:core:export`), `java.util.zip` (`ZipOutputStream`/`ZipInputStream`, no new dependency), `androidx.documentfile` (new, for SAF folder writes in `BackupWorker`), Room 2.8.5 read/insert through existing DAOs, WorkManager 2.11.1 (`BackupWorker` follows the existing `WatchSyncWorker` `@HiltWorker` pattern — no new registration needed, `NexWatchApplication` already wires `HiltWorkerFactory`), JUnit + kotlinx-coroutines-test, `androidx.sqlite:sqlite-bundled` for JVM-only Room tests (`core/database`'s `testFixtures` module, already built in Phase 6).

**Spec:** `docs/implementation-plan.md` §6 (Export and import, all subsections), §5.1/§5.3 (record identity and schema, for what "every table identical, IDs included" means), §8.7 (BackupWorker's schedule row), §12 Phase 7 row.

## Global Constraints

- The local database is the single source of truth (I2); export and import only read/write through DAOs, never touch files Room doesn't own.
- Deterministic record IDs (I3): `id = pk = UUID.nameUUIDFromBytes(dedupeKey.toByteArray()).toString()` for every `RecordMeta`-bearing table. Re-importing the same file twice must be a no-op on those tables (idempotent).
- Records are immutable; a correction is a new `version`, never a silent edit. Tombstones (`deleted = true`) are real rows, not holes — export must include them, not filter `deleted = 0`, or a round-trip loses tombstone history.
- Nothing polls (I5); `BackupWorker` is WorkManager-scheduled (the explicit carve-out), not a connection poll.
- UTC epoch milliseconds plus zone offset on every record (I6). The export format's canonical units and field names match §5.3/§6.1 exactly (`distance_m`, `energy_kcal`, etc.).
- Streaming, not loading (§6.2): every table read for export uses keyset pagination (`WHERE pk > :last ORDER BY pk LIMIT 1000`, never `OFFSET`), through a buffered writer into the `ZipOutputStream`. No DAO method added by this plan returns every row of a `RecordMeta`-bearing table at once.
- SDK types (`com.topstep.**`) never leave `:core:watch-fitcloud`; this plan never touches that module. Room entities (`*Entity`) never leave `:core:database`/`:core:data` — `:core:export` only ever sees `:core:model` types.
- Base package `com.nexwatch`. Follow existing module DI patterns: DataStore qualifier annotations (`WatchIdentityDataStore` style) for `BackupPrefs`, the `WatchUserIdProvider` inversion pattern (interface in the consumed-from module, impl + `@Binds` in `:core:data`) for `AppVersionProvider`.

## Review Focus

- **A corrupted or tampered export file.** The importer must fail loudly with a clear error (sha256 mismatch against the manifest) rather than silently ingesting truncated or altered data — Task 5's tamper test.
- **An export file from a newer `db_schema_version` than the running app understands.** Importing it blind risks writing columns/shapes the current schema doesn't expect; the importer must refuse with a clear message rather than guess — Task 5.
- **Re-importing the same file twice, or importing a file that overlaps already-present data.** Must not duplicate rows (idempotent upsert by deterministic ID) — Task 5's idempotency test.
- **Exporting a fresh install with an empty database.** Must produce a valid, well-formed (empty) zip rather than crash on an empty first/last page — Task 4's empty-database test.
- **A workout with no GPS route (indoor workout).** Must export/import cleanly with an empty route list, and `GpxExporter` must handle zero track points without producing invalid GPX — Task 6's no-route test.

---

## Task 1: Version catalog and module wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/model/build.gradle.kts`
- Modify: `core/export/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`

**Interfaces:**
- Produces: `libs.androidx.documentfile` resolvable from any module; `:core:model` and `:core:export` can use `@Serializable`/`kotlinx.serialization.json.Json`; `:core:export`'s tests can use `:core:database`'s `testFixtures`.

- [ ] **Step 1: Add `androidx.documentfile` to the version catalog**

In `gradle/libs.versions.toml`, add to `[versions]` (near the other `androidx` entries):

```toml
documentfile = "1.1.0"
```

Add to `[libraries]`:

```toml
androidx-documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }
```

- [ ] **Step 2: Apply kotlinx-serialization to `:core:model`**

Modify `core/model/build.gradle.kts`:

```kotlin
plugins {
    id("nexwatch.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
```

`:core:model` stays pure Kotlin/JVM (`nexwatch.jvm.library`, unchanged) — kotlinx-serialization is itself pure Kotlin, so this doesn't pull in any Android dependency. `HealthRecord` (Task 3) needs `@Serializable` so `:core:export` can encode it directly with no separate DTO layer.

- [ ] **Step 3: Wire `:core:export`'s dependencies**

Replace `core/export/build.gradle.kts` in full:

```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexwatch.core.export"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:database")))
}
```

`:core:export` needs `nexwatch.android.hilt` (not just `nexwatch.android.library`) starting this phase because `JsonlZipExporter`, `ZipImporter`, `GpxExporter` and `BackupWorker` are all `@Inject`/`@HiltWorker` classes (Tasks 4–7). `testFixtures(project(":core:database"))` gives its tests `inMemoryTestDatabase()` the same way `:core:data`'s tests already use it (`core/data/build.gradle.kts:28`).

- [ ] **Step 4: Confirm `:core:data` already has what `ExportRepository` needs**

`core/data/build.gradle.kts` already depends on `:core:database`, `:core:model`, `:core:common`, and `libs.kotlinx.coroutines.core` (verified — no changes needed here). No step; this is a checkpoint, not an edit.

- [ ] **Step 5: Sync and confirm the new dependencies resolve**

Run: `./gradlew :core:export:dependencies :core:model:dependencies --configuration debugCompileClasspath`
Expected: both resolve without error; `kotlinx-serialization-json` and `androidx.documentfile` appear in `:core:export`'s graph.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml core/model/build.gradle.kts core/export/build.gradle.kts
git commit -m "build: wire kotlinx-serialization and documentfile for Phase 7 export/import"
```

---

## Task 2: `:core:database` — keyset pagination and `ExportHistoryDao`

**Files:**
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/StepsDao.kt`
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/HealthSampleDao.kt`
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/SleepDao.kt`
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutDao.kt`
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/DailySummaryDao.kt`
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/DeviceDao.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/ExportHistoryDao.kt`
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/NexWatchDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/nexwatch/core/data/di/DatabaseModule.kt`
- Test: `core/database/src/test/kotlin/com/nexwatch/core/database/ExportPagingTest.kt`

**Interfaces:**
- Consumes: `inMemoryTestDatabase()` (test fixture, `core/database/src/testFixtures`), `RecordMeta`, `Origin` (`core/database/.../RecordMeta.kt`, `Origin.kt`).
- Produces: `StepsDao.pageAfter`, `HealthSampleDao.page{HeartRate,Spo2,BloodPressure,Temperature,Stress}After`, `SleepDao.pageSessionsAfter`/`stagesForSessionOnce`, `WorkoutDao.pageAfter`/`findByPk`/`routeForWorkoutOnce`/`heartRateForWorkoutOnce`, `DailySummaryDao.pageAfter`, `DeviceDao.findAll`, `ExportHistoryDao` (new), `NexWatchDatabase.exportHistoryDao()`, top-level `const val NEXWATCH_SCHEMA_VERSION` — all consumed by Task 3's `ExportRepository`.

- [ ] **Step 1: Add a public schema-version constant**

In `core/database/src/main/kotlin/com/nexwatch/core/database/NexWatchDatabase.kt`, replace the file in full:

```kotlin
package com.nexwatch.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** Exposed publicly so :core:data's ExportRepository can stamp it into export manifests (§6.1). */
const val NEXWATCH_SCHEMA_VERSION = 1

@Database(
    entities = [
        HeartRateEntity::class, Spo2Entity::class, BloodPressureEntity::class,
        TemperatureEntity::class, StressEntity::class, StepsEntity::class,
        SleepSessionEntity::class, SleepStageEntity::class,
        WorkoutEntity::class, WorkoutRouteEntity::class, WorkoutHrEntity::class,
        DailySummaryEntity::class, DeviceEntity::class, DeviceEventEntity::class,
        RawIngestEntity::class, ChangeLogEntity::class,
        SyncCursorEntity::class, ExportHistoryEntity::class,
    ],
    version = NEXWATCH_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class NexWatchDatabase : RoomDatabase() {
    abstract fun healthSampleDao(): HealthSampleDao
    abstract fun stepsDao(): StepsDao
    abstract fun sleepDao(): SleepDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun dailySummaryDao(): DailySummaryDao
    abstract fun deviceDao(): DeviceDao
    abstract fun rawIngestDao(): RawIngestDao
    abstract fun changeLogDao(): ChangeLogDao
    abstract fun exportHistoryDao(): ExportHistoryDao
}
```

This changes no entity and no version number (still `1`) — `ExportHistoryEntity` is already in the schema from Phase 6, it just had no DAO yet. No migration or schema JSON regen is needed.

- [ ] **Step 2: Add the paged read query to `StepsDao`**

In `core/database/src/main/kotlin/com/nexwatch/core/database/StepsDao.kt`, add inside the `interface StepsDao` block (after `insertAll`):

```kotlin
    /** §6.2 keyset pagination — never OFFSET. pk is a deterministic UUID string (§5.1), so "" sorts before every row. */
    @Query("SELECT * FROM steps WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageAfter(afterId: String, limit: Int): List<StepsEntity>
```

- [ ] **Step 3: Add the five paged read queries to `HealthSampleDao`**

In `core/database/src/main/kotlin/com/nexwatch/core/database/HealthSampleDao.kt`, add inside `interface HealthSampleDao` (after `insertStress`):

```kotlin
    @Query("SELECT * FROM heart_rate WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageHeartRateAfter(afterId: String, limit: Int): List<HeartRateEntity>

    @Query("SELECT * FROM spo2 WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageSpo2After(afterId: String, limit: Int): List<Spo2Entity>

    @Query("SELECT * FROM blood_pressure WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageBloodPressureAfter(afterId: String, limit: Int): List<BloodPressureEntity>

    @Query("SELECT * FROM temperature WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageTemperatureAfter(afterId: String, limit: Int): List<TemperatureEntity>

    @Query("SELECT * FROM stress WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageStressAfter(afterId: String, limit: Int): List<StressEntity>
```

- [ ] **Step 4: Add paged/one-shot queries to `SleepDao`**

In `core/database/src/main/kotlin/com/nexwatch/core/database/SleepDao.kt`, add inside `interface SleepDao` (after `replaceNight`):

```kotlin
    @Query("SELECT * FROM sleep_session WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageSessionsAfter(afterId: String, limit: Int): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stage WHERE session_id = :sessionId ORDER BY start_time")
    suspend fun stagesForSessionOnce(sessionId: String): List<SleepStageEntity>
```

- [ ] **Step 5: Add paged/one-shot queries to `WorkoutDao`**

In `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutDao.kt`, add inside `interface WorkoutDao` (after `insertHeartRateSeries`):

```kotlin
    @Query("SELECT * FROM workout WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageAfter(afterId: String, limit: Int): List<WorkoutEntity>

    @Query("SELECT * FROM workout WHERE pk = :pk")
    suspend fun findByPk(pk: String): WorkoutEntity?

    @Query("SELECT * FROM workout_route WHERE workout_id = :workoutId ORDER BY start_time")
    suspend fun routeForWorkoutOnce(workoutId: String): List<WorkoutRouteEntity>

    @Query("SELECT * FROM workout_hr WHERE workout_id = :workoutId ORDER BY start_time")
    suspend fun heartRateForWorkoutOnce(workoutId: String): List<WorkoutHrEntity>
```

- [ ] **Step 6: Add the paged read query to `DailySummaryDao`**

In `core/database/src/main/kotlin/com/nexwatch/core/database/DailySummaryDao.kt`, add inside `interface DailySummaryDao` (after `observeRange`):

```kotlin
    @Query("SELECT * FROM daily_summary WHERE pk > :afterId ORDER BY pk LIMIT :limit")
    suspend fun pageAfter(afterId: Long, limit: Int): List<DailySummaryEntity>
```

`daily_summary`'s primary key is an autoGenerate `Long` (unlike the UUID-string `pk` on every `RecordMeta` table), so its cursor is `Long`, not `String` — `ExportRepository` (Task 3) handles that difference at the boundary.

- [ ] **Step 7: Add `findAll` to `DeviceDao`**

In `core/database/src/main/kotlin/com/nexwatch/core/database/DeviceDao.kt`, add inside `interface DeviceDao` (after `upsert`):

```kotlin
    @Query("SELECT * FROM device ORDER BY bound_at")
    suspend fun findAll(): List<DeviceEntity>
```

The `device` table holds one row per watch ever bound (§5.3) — small enough that no pagination is needed; every device belongs in every export so a restore doesn't lose history of a previously-bound watch.

- [ ] **Step 8: Create `ExportHistoryDao`**

Create `core/database/src/main/kotlin/com/nexwatch/core/database/ExportHistoryDao.kt`:

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExportHistoryDao {

    @Insert
    suspend fun insert(entry: ExportHistoryEntity): Long

    @Query("SELECT * FROM export_history ORDER BY at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ExportHistoryEntity>
}
```

- [ ] **Step 9: Wire `ExportHistoryDao` into `DatabaseModule`**

In `core/data/src/main/kotlin/com/nexwatch/core/data/di/DatabaseModule.kt`, add after `provideChangeLogDao`:

```kotlin
    @Provides fun provideExportHistoryDao(db: NexWatchDatabase) = db.exportHistoryDao()
```

- [ ] **Step 10: Write the paging test**

Create `core/database/src/test/kotlin/com/nexwatch/core/database/ExportPagingTest.kt`:

```kotlin
package com.nexwatch.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

private fun meta(dedupeKey: String, start: Long) = RecordMeta(
    dedupeKey = dedupeKey,
    deviceId = DEVICE_ID,
    startTime = start,
    endTime = start,
    zoneOffsetSec = 0,
    origin = Origin.MONITOR,
    ingestedAt = start,
)

private fun pkFor(dedupeKey: String): String = UUID.nameUUIDFromBytes(dedupeKey.toByteArray()).toString()

class ExportPagingTest {

    @Test
    fun `pageAfter walks every row exactly once across many small pages`() = runTest {
        val db = inMemoryTestDatabase()
        val rows = (0 until 25).map {
            val dedupeKey = "steps:$DEVICE_ID:$it"
            StepsEntity(pkFor(dedupeKey), meta(dedupeKey, it.toLong()), count = it, distanceM = 0f, energyKcal = 0f)
        }
        db.stepsDao().insertAll(rows)

        val seen = mutableListOf<StepsEntity>()
        var cursor = ""
        while (true) {
            val page = db.stepsDao().pageAfter(cursor, limit = 4)
            if (page.isEmpty()) break
            seen += page
            cursor = page.last().pk
        }

        assertEquals(25, seen.size)
        assertEquals(rows.map { it.pk }.toSet(), seen.map { it.pk }.toSet()) // every row, no duplicates
        db.close()
    }

    @Test
    fun `pageAfter includes tombstoned rows`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "steps:$DEVICE_ID:tomb"
        val pk = pkFor(dedupeKey)
        db.stepsDao().insertAll(listOf(StepsEntity(pk, meta(dedupeKey, 0L), count = 1, distanceM = 0f, energyKcal = 0f)))
        db.useWriterConnection { tx ->
            tx.usePrepared("UPDATE steps SET version = 2, deleted = 1 WHERE pk = ?") { it.bindText(1, pk); it.step() }
        }

        val page = db.stepsDao().pageAfter("", limit = 10)

        assertEquals(1, page.size)
        assertTrue(page[0].meta.deleted) // export must carry tombstones, not filter them out
        db.close()
    }

    @Test
    fun `exportHistoryDao round-trips an entry`() = runTest {
        val db = inMemoryTestDatabase()
        db.exportHistoryDao().insert(
            ExportHistoryEntity(at = 1_000L, uri = "content://x", format = "zip", range = "2026-01-01..2026-09-25", recordCountsJson = "{}"),
        )

        val recent = db.exportHistoryDao().recent(limit = 10)

        assertEquals(1, recent.size)
        assertEquals("content://x", recent[0].uri)
        db.close()
    }

    @Test
    fun `pageAfter on an empty table returns an empty first page`() = runTest {
        val db = inMemoryTestDatabase()
        assertTrue(db.stepsDao().pageAfter("", limit = 100).isEmpty())
        db.close()
    }
}
```

- [ ] **Step 11: Run the new tests**

Run: `./gradlew :core:database:testDebugUnitTest --tests "com.nexwatch.core.database.ExportPagingTest"`
Expected: all 4 tests pass.

- [ ] **Step 12: Commit**

```bash
git add core/database/src/main/kotlin/com/nexwatch/core/database core/database/src/test/kotlin/com/nexwatch/core/database/ExportPagingTest.kt core/data/src/main/kotlin/com/nexwatch/core/data/di/DatabaseModule.kt
git commit -m "feat(database): keyset-paginated reads and ExportHistoryDao for Phase 7"
```

---

## Task 3: `:core:model` `HealthRecord` and `:core:data` `ExportRepository`

**Files:**
- Modify: `core/model/src/main/kotlin/com/nexwatch/core/model/SleepStage.kt`
- Modify: `core/model/src/main/kotlin/com/nexwatch/core/model/DecodedHealthRecord.kt`
- Modify: `core/model/src/main/kotlin/com/nexwatch/core/model/Device.kt`
- Create: `core/model/src/main/kotlin/com/nexwatch/core/model/HealthRecord.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/export/ExportRepository.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/export/ExportRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2's paged DAO methods, `NEXWATCH_SCHEMA_VERSION`, `RecordMeta`/`Origin` (mapped, not re-exported).
- Produces: `HealthRecord` sealed interface (`Step`, `HeartRate`, `Spo2`, `BloodPressure`, `Temperature`, `Stress`, `SleepSession`, `Workout`), `RecordOrigin` enum, `DailySummaryRecord`, `ExportHistoryEntry` (all `:core:model`, all `@Serializable` where they cross into JSON in Task 4); `ExportRepository` with `pageSteps`/`pageHeartRate`/`pageSpo2`/`pageBloodPressure`/`pageTemperature`/`pageStress`/`pageSleepSessions`/`pageWorkouts`/`pageDailySummaries(afterId: Long, limit: Int)`/`workoutById(id: String)`/`allDevices()`/`insertSteps`.../`upsertDevices`/`recordExport`/`recentExports` — consumed by Tasks 4 and 5.

- [ ] **Step 1: Make the shared nested model types serializable**

In `core/model/src/main/kotlin/com/nexwatch/core/model/SleepStage.kt`, replace in full:

```kotlin
package com.nexwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SleepStage { AWAKE, LIGHT, DEEP, REM }

@Serializable
data class SleepStageSpan(val stage: SleepStage, val startMs: Long, val endMs: Long)
```

In `core/model/src/main/kotlin/com/nexwatch/core/model/DecodedHealthRecord.kt`, change only the last two lines (the file's existing sealed interface and its data classes are untouched — `DecodedHealthRecord` is the decode-time shape, `HealthRecord` below is the export/storage shape; they stay separate types on purpose since decode-time records don't have an `id` yet):

```kotlin
@Serializable
data class WorkoutRoutePoint(val offsetSeconds: Int, val lat: Double, val lon: Double, val altitudeM: Float?)

@Serializable
data class WorkoutHrPoint(val atMs: Long, val bpm: Int)
```

Add `import kotlinx.serialization.Serializable` to that file's import block.

- [ ] **Step 2: Make `Device` serializable**

In `core/model/src/main/kotlin/com/nexwatch/core/model/Device.kt`, replace in full:

```kotlin
package com.nexwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val address: String,
    val model: String?,
    val firmwareVersion: String?,
    val sdkVersion: String?,
    val boundAtMs: Long,
)

enum class DeviceEventType { BOUND, UNBOUND, FIRMWARE_UPDATED, TIMEZONE_CHANGED }

data class DeviceEvent(
    val deviceAddress: String,
    val type: DeviceEventType,
    val details: String?,
    val atMs: Long,
)
```

`DeviceEvent`/`DeviceEventType` are untouched (not part of the §6.1 export format — see the plan's Architecture note on scope).

- [ ] **Step 3: Create `HealthRecord.kt`**

Create `core/model/src/main/kotlin/com/nexwatch/core/model/HealthRecord.kt`:

```kotlin
package com.nexwatch.core.model

import kotlinx.serialization.Serializable

/** Mirrors :core:database's Origin (§5.3) without :core:model depending on :core:database. */
@Serializable
enum class RecordOrigin { MONITOR, MEASURE, LIVE }

/**
 * Full-fidelity canonical record — the §6.1 export/import wire shape, and the type §7.1
 * already names for SyncProvider's RecordChange (Phase 9 reuses this, not a new one).
 * One variant per RecordMeta-bearing table (§5.3); daily_summary is deliberately not here,
 * since it's derived and carries no RecordMeta (see DailySummaryRecord below).
 */
sealed interface HealthRecord {
    val id: String
    val dedupeKey: String
    val deviceId: String
    val startMs: Long
    val endMs: Long
    val zoneOffsetSec: Int
    val origin: RecordOrigin
    val version: Int
    val deleted: Boolean
    val ingestedAt: Long

    @Serializable
    data class Step(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val count: Int,
        val distanceM: Float,
        val energyKcal: Float,
    ) : HealthRecord

    @Serializable
    data class HeartRate(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val bpm: Int,
    ) : HealthRecord

    @Serializable
    data class Spo2(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val percent: Int,
    ) : HealthRecord

    @Serializable
    data class BloodPressure(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val systolic: Int,
        val diastolic: Int,
    ) : HealthRecord

    @Serializable
    data class Temperature(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val celsius: Float,
    ) : HealthRecord

    @Serializable
    data class Stress(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val level: Int,
    ) : HealthRecord

    @Serializable
    data class SleepSession(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val nightDate: String,
        val contentHash: String,
        val score: Int,
        val efficiency: Int,
        val stages: List<SleepStageSpan>,
    ) : HealthRecord

    @Serializable
    data class Workout(
        override val id: String,
        override val dedupeKey: String,
        override val deviceId: String,
        override val startMs: Long,
        override val endMs: Long,
        override val zoneOffsetSec: Int,
        override val origin: RecordOrigin,
        override val version: Int,
        override val deleted: Boolean,
        override val ingestedAt: Long,
        val sportId: String,
        val sportType: Int,
        val distanceM: Float,
        val energyKcal: Float,
        val avgHrBpm: Int?,
        val maxHrBpm: Int?,
        val steps: Int?,
        val route: List<WorkoutRoutePoint>,
        val heartRateSeries: List<WorkoutHrPoint>,
    ) : HealthRecord
}

/** daily_summary has no RecordMeta (§5.3: "Derived. Rebuilt, never synced as source data"). */
@Serializable
data class DailySummaryRecord(
    val deviceId: String,
    val date: String,
    val steps: Int,
    val distanceM: Int,
    val energyKcal: Int,
    val restingHrBpm: Int?,
    val avgHrBpm: Int?,
    val maxHrBpm: Int?,
    val sleepMinutes: Int?,
    val liveStepsTotal: Int?,
)

data class ExportHistoryEntry(
    val atMs: Long,
    val uri: String,
    val format: String,
    val range: String,
    val recordCounts: Map<String, Int>,
)
```

- [ ] **Step 4: Create `ExportRepository`**

Create `core/data/src/main/kotlin/com/nexwatch/core/data/export/ExportRepository.kt`:

```kotlin
package com.nexwatch.core.data.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.BloodPressureEntity
import com.nexwatch.core.database.DailySummaryDao
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.ExportHistoryDao
import com.nexwatch.core.database.ExportHistoryEntity
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.NEXWATCH_SCHEMA_VERSION
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageDb
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.Spo2Entity
import com.nexwatch.core.database.StepsDao
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.StressEntity
import com.nexwatch.core.database.TemperatureEntity
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutHrEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.model.DailySummaryRecord
import com.nexwatch.core.model.Device
import com.nexwatch.core.model.ExportHistoryEntry
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.model.RecordOrigin
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutHrPoint
import com.nexwatch.core.model.WorkoutRoutePoint
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * The only class outside :core:database that reads/writes Room for export/import (§6).
 * Every RecordMeta-bearing table is exposed only through keyset-paginated reads — there is
 * deliberately no "get all rows" method here, so a year of data can never be loaded at once.
 */
class ExportRepository @Inject constructor(
    private val stepsDao: StepsDao,
    private val healthSampleDao: HealthSampleDao,
    private val sleepDao: SleepDao,
    private val workoutDao: WorkoutDao,
    private val dailySummaryDao: DailySummaryDao,
    private val deviceDao: DeviceDao,
    private val exportHistoryDao: ExportHistoryDao,
    private val dispatchers: CoroutineDispatchers,
) {
    val schemaVersion: Int get() = NEXWATCH_SCHEMA_VERSION

    // ---- paged reads ----

    suspend fun pageSteps(afterId: String, limit: Int): List<HealthRecord.Step> =
        withContext(dispatchers.io) {
            stepsDao.pageAfter(afterId, limit).map {
                HealthRecord.Step(it.pk, it.meta.dedupeKey, it.meta.deviceId, it.meta.startTime, it.meta.endTime,
                    it.meta.zoneOffsetSec, it.meta.origin.toModel(), it.meta.version, it.meta.deleted, it.meta.ingestedAt,
                    it.count, it.distanceM, it.energyKcal)
            }
        }

    suspend fun pageHeartRate(afterId: String, limit: Int): List<HealthRecord.HeartRate> =
        withContext(dispatchers.io) {
            healthSampleDao.pageHeartRateAfter(afterId, limit).map {
                HealthRecord.HeartRate(it.pk, it.meta.dedupeKey, it.meta.deviceId, it.meta.startTime, it.meta.endTime,
                    it.meta.zoneOffsetSec, it.meta.origin.toModel(), it.meta.version, it.meta.deleted, it.meta.ingestedAt, it.bpm)
            }
        }

    suspend fun pageSpo2(afterId: String, limit: Int): List<HealthRecord.Spo2> =
        withContext(dispatchers.io) {
            healthSampleDao.pageSpo2After(afterId, limit).map {
                HealthRecord.Spo2(it.pk, it.meta.dedupeKey, it.meta.deviceId, it.meta.startTime, it.meta.endTime,
                    it.meta.zoneOffsetSec, it.meta.origin.toModel(), it.meta.version, it.meta.deleted, it.meta.ingestedAt, it.percent)
            }
        }

    suspend fun pageBloodPressure(afterId: String, limit: Int): List<HealthRecord.BloodPressure> =
        withContext(dispatchers.io) {
            healthSampleDao.pageBloodPressureAfter(afterId, limit).map {
                HealthRecord.BloodPressure(it.pk, it.meta.dedupeKey, it.meta.deviceId, it.meta.startTime, it.meta.endTime,
                    it.meta.zoneOffsetSec, it.meta.origin.toModel(), it.meta.version, it.meta.deleted, it.meta.ingestedAt,
                    it.systolic, it.diastolic)
            }
        }

    suspend fun pageTemperature(afterId: String, limit: Int): List<HealthRecord.Temperature> =
        withContext(dispatchers.io) {
            healthSampleDao.pageTemperatureAfter(afterId, limit).map {
                HealthRecord.Temperature(it.pk, it.meta.dedupeKey, it.meta.deviceId, it.meta.startTime, it.meta.endTime,
                    it.meta.zoneOffsetSec, it.meta.origin.toModel(), it.meta.version, it.meta.deleted, it.meta.ingestedAt, it.celsius)
            }
        }

    suspend fun pageStress(afterId: String, limit: Int): List<HealthRecord.Stress> =
        withContext(dispatchers.io) {
            healthSampleDao.pageStressAfter(afterId, limit).map {
                HealthRecord.Stress(it.pk, it.meta.dedupeKey, it.meta.deviceId, it.meta.startTime, it.meta.endTime,
                    it.meta.zoneOffsetSec, it.meta.origin.toModel(), it.meta.version, it.meta.deleted, it.meta.ingestedAt, it.level)
            }
        }

    suspend fun pageSleepSessions(afterId: String, limit: Int): List<HealthRecord.SleepSession> =
        withContext(dispatchers.io) {
            sleepDao.pageSessionsAfter(afterId, limit).map { session ->
                val stages = sleepDao.stagesForSessionOnce(session.pk).map {
                    SleepStageSpan(SleepStage.valueOf(it.stage.name), it.startTime, it.endTime)
                }
                HealthRecord.SleepSession(session.pk, session.meta.dedupeKey, session.meta.deviceId, session.meta.startTime,
                    session.meta.endTime, session.meta.zoneOffsetSec, session.meta.origin.toModel(), session.meta.version,
                    session.meta.deleted, session.meta.ingestedAt, session.nightDate, session.contentHash, session.score,
                    session.efficiency, stages)
            }
        }

    suspend fun pageWorkouts(afterId: String, limit: Int): List<HealthRecord.Workout> =
        withContext(dispatchers.io) { workoutDao.pageAfter(afterId, limit).map { it.toModelWithChildren() } }

    suspend fun workoutById(id: String): HealthRecord.Workout? =
        withContext(dispatchers.io) { workoutDao.findByPk(id)?.toModelWithChildren() }

    private suspend fun WorkoutEntity.toModelWithChildren(): HealthRecord.Workout {
        val route = workoutDao.routeForWorkoutOnce(pk).map { WorkoutRoutePoint(0, it.lat, it.lon, it.altitudeM) }
        val hr = workoutDao.heartRateForWorkoutOnce(pk).map { WorkoutHrPoint(it.atMs, it.bpm) }
        return HealthRecord.Workout(pk, meta.dedupeKey, meta.deviceId, meta.startTime, meta.endTime, meta.zoneOffsetSec,
            meta.origin.toModel(), meta.version, meta.deleted, meta.ingestedAt, sportId, sportType, distanceM, energyKcal,
            avgHrBpm, maxHrBpm, steps, route, hr)
    }

    suspend fun pageDailySummaries(afterId: Long, limit: Int): List<DailySummaryRecord> =
        withContext(dispatchers.io) {
            dailySummaryDao.pageAfter(afterId, limit).map {
                DailySummaryRecord(it.deviceId, it.date, it.steps, it.distanceM, it.energyKcal, it.restingHrBpm,
                    it.avgHrBpm, it.maxHrBpm, it.sleepMinutes, it.liveStepsTotal)
            }
        }

    /** Exposed so callers can advance a Long cursor without depending on :core:database's entity type. */
    suspend fun dailySummaryCursorAfter(afterId: Long, limit: Int): Long? =
        withContext(dispatchers.io) { dailySummaryDao.pageAfter(afterId, limit).lastOrNull()?.pk }

    suspend fun allDevices(): List<Device> = withContext(dispatchers.io) {
        deviceDao.findAll().map { Device(it.address, it.model, it.firmwareVersion, it.sdkVersion, it.boundAtMs) }
    }

    // ---- import writes ----

    suspend fun insertSteps(records: List<HealthRecord.Step>): Unit = withContext(dispatchers.io) {
        stepsDao.insertAll(records.map { StepsEntity(it.id, it.toMeta(), it.count, it.distanceM, it.energyKcal) })
    }

    suspend fun insertHeartRate(records: List<HealthRecord.HeartRate>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertHeartRate(records.map { HeartRateEntity(it.id, it.toMeta(), it.bpm) })
    }

    suspend fun insertSpo2(records: List<HealthRecord.Spo2>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertSpo2(records.map { Spo2Entity(it.id, it.toMeta(), it.percent) })
    }

    suspend fun insertBloodPressure(records: List<HealthRecord.BloodPressure>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertBloodPressure(records.map { BloodPressureEntity(it.id, it.toMeta(), it.systolic, it.diastolic) })
    }

    suspend fun insertTemperature(records: List<HealthRecord.Temperature>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertTemperature(records.map { TemperatureEntity(it.id, it.toMeta(), it.celsius) })
    }

    suspend fun insertStress(records: List<HealthRecord.Stress>): Unit = withContext(dispatchers.io) {
        healthSampleDao.insertStress(records.map { StressEntity(it.id, it.toMeta(), it.level) })
    }

    suspend fun insertSleepSessions(records: List<HealthRecord.SleepSession>): Unit = withContext(dispatchers.io) {
        records.forEach { record ->
            val session = SleepSessionEntity(record.id, record.toMeta(), record.nightDate, record.contentHash,
                record.score, record.efficiency)
            val stages = record.stages.map { SleepStageEntity(sessionId = record.id, stage = SleepStageDb.valueOf(it.stage.name),
                startTime = it.startMs, endTime = it.endMs) }
            sleepDao.replaceNight(session, stages)
        }
    }

    suspend fun insertWorkouts(records: List<HealthRecord.Workout>): Unit = withContext(dispatchers.io) {
        workoutDao.insertWorkouts(records.map {
            WorkoutEntity(it.id, it.toMeta(), it.sportId, it.sportType, ((it.endMs - it.startMs) / 1000).toInt(),
                it.distanceM, it.energyKcal, it.avgHrBpm, it.maxHrBpm, it.steps)
        })
        records.forEach { record ->
            if (record.route.isNotEmpty()) {
                workoutDao.insertRoute(record.route.map {
                    WorkoutRouteEntity(workoutId = record.id, atMs = record.startMs + it.offsetSeconds * 1000L,
                        lat = it.lat, lon = it.lon, altitudeM = it.altitudeM)
                })
            }
            if (record.heartRateSeries.isNotEmpty()) {
                workoutDao.insertHeartRateSeries(record.heartRateSeries.map {
                    WorkoutHrEntity(workoutId = record.id, atMs = it.atMs, bpm = it.bpm)
                })
            }
        }
    }

    suspend fun insertDailySummaries(records: List<DailySummaryRecord>): Unit = withContext(dispatchers.io) {
        records.forEach {
            dailySummaryDao.upsert(DailySummaryEntity(deviceId = it.deviceId, date = it.date, steps = it.steps,
                distanceM = it.distanceM, energyKcal = it.energyKcal, restingHrBpm = it.restingHrBpm,
                avgHrBpm = it.avgHrBpm, maxHrBpm = it.maxHrBpm, sleepMinutes = it.sleepMinutes,
                liveStepsTotal = it.liveStepsTotal))
        }
    }

    suspend fun upsertDevices(devices: List<Device>): Unit = withContext(dispatchers.io) {
        devices.forEach {
            deviceDao.upsert(DeviceEntity(it.address, it.model, it.firmwareVersion, it.sdkVersion,
                capabilitiesJson = null, boundAtMs = it.boundAtMs))
        }
    }

    // ---- history ----

    suspend fun recordExport(entry: ExportHistoryEntry): Unit = withContext(dispatchers.io) {
        exportHistoryDao.insert(ExportHistoryEntity(at = entry.atMs, uri = entry.uri, format = entry.format,
            range = entry.range, recordCountsJson = Json.encodeToString(entry.recordCounts)))
        Unit
    }

    suspend fun recentExports(limit: Int = 20): List<ExportHistoryEntry> = withContext(dispatchers.io) {
        exportHistoryDao.recent(limit).map {
            ExportHistoryEntry(it.at, it.uri, it.format, it.range, Json.decodeFromString(it.recordCountsJson))
        }
    }

    private fun HealthRecord.toMeta() = RecordMeta(dedupeKey, deviceId, startMs, endMs, zoneOffsetSec,
        origin.toDb(), version, deleted, ingestedAt)

    private fun Origin.toModel() = RecordOrigin.valueOf(name)
    private fun RecordOrigin.toDb() = Origin.valueOf(name)
}
```

`kotlinx.serialization.json.Json.encodeToString`/`decodeFromString` on `Map<String, Int>` need a reified generic call — Kotlin resolves `Json.encodeToString(value)` (no explicit serializer) via the inline reified extension in `kotlinx-serialization-json`, so no `KSerializer` needs to be threaded through by hand here.

- [ ] **Step 5: Write `ExportRepositoryTest`**

Create `core/data/src/test/kotlin/com/nexwatch/core/data/export/ExportRepositoryTest.kt`:

```kotlin
package com.nexwatch.core.data.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.ExportHistoryEntry
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

private const val DEVICE_ID = "AA:BB"

private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()

private fun testDispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}

class ExportRepositoryTest {

    @Test
    fun `insertSteps then pageSteps round-trips every field`() = runTest {
        val db = inMemoryTestDatabase()
        val dispatchers = testDispatchers()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val dedupeKey = "steps:$DEVICE_ID:1000"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(dedupeKey),
            RecordMeta(dedupeKey, DEVICE_ID, 1000L, 1300L, 0, Origin.MONITOR, ingestedAt = 1000L), 50, 40f, 2f)))

        val page = repo.pageSteps("", limit = 10)

        assertEquals(1, page.size)
        assertEquals(50, page[0].count)
        assertEquals(pkFor(dedupeKey), page[0].id)
        db.close()
    }

    @Test
    fun `pageWorkouts nests route and heart-rate series`() = runTest {
        val db = inMemoryTestDatabase()
        val dispatchers = testDispatchers()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val dedupeKey = "workout:$DEVICE_ID:sport-1"
        val pk = pkFor(dedupeKey)
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pk,
            RecordMeta(dedupeKey, DEVICE_ID, 1000L, 2000L, 0, Origin.MONITOR, ingestedAt = 1000L),
            "sport-1", 1, 1000, 500f, 30f, 130, 160, 600)))
        db.workoutDao().insertRoute(listOf(WorkoutRouteEntity(workoutId = pk, atMs = 1000L, lat = 1.0, lon = 2.0, altitudeM = null)))

        val page = repo.pageWorkouts("", limit = 10)

        assertEquals(1, page.size)
        assertEquals(1, page[0].route.size)
        db.close()
    }

    @Test
    fun `insertWorkouts is idempotent on re-import`() = runTest {
        val db = inMemoryTestDatabase()
        val dispatchers = testDispatchers()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)
        val dedupeKey = "workout:$DEVICE_ID:sport-2"
        val record = com.nexwatch.core.model.HealthRecord.Workout(pkFor(dedupeKey), dedupeKey, DEVICE_ID, 1000L, 2000L,
            0, com.nexwatch.core.model.RecordOrigin.MONITOR, 1, false, 1000L, "sport-2", 1, 500f, 30f, null, null, null,
            emptyList(), emptyList())

        repo.insertWorkouts(listOf(record))
        repo.insertWorkouts(listOf(record)) // re-import same file twice

        assertEquals(1, repo.pageWorkouts("", limit = 10).size)
        db.close()
    }

    @Test
    fun `recordExport then recentExports round-trips counts`() = runTest {
        val db = inMemoryTestDatabase()
        val dispatchers = testDispatchers()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers)

        repo.recordExport(ExportHistoryEntry(atMs = 5_000L, uri = "content://a", format = "zip",
            range = "2026-01-01..2026-09-25", recordCounts = mapOf("steps" to 3)))

        val recent = repo.recentExports()
        assertEquals(1, recent.size)
        assertEquals(3, recent[0].recordCounts["steps"])
        db.close()
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:model:compileKotlin :core:data:testDebugUnitTest --tests "com.nexwatch.core.data.export.ExportRepositoryTest"`
Expected: `:core:model` compiles clean with the new serializable types; all 4 `ExportRepositoryTest` tests pass.

- [ ] **Step 7: Commit**

```bash
git add core/model/src/main/kotlin/com/nexwatch/core/model core/data/src/main/kotlin/com/nexwatch/core/data/export core/data/src/test/kotlin/com/nexwatch/core/data/export
git commit -m "feat(model,data): HealthRecord canonical export type and ExportRepository"
```

---

## Task 4: `:core:export` — `JsonlZipExporter` (JSONL + CSV + manifest)

**Files:**
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/AppVersionProvider.kt`
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/ExportManifest.kt`
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/JsonlZipExporter.kt`
- Test: `core/export/src/test/kotlin/com/nexwatch/core/export/JsonlZipExporterTest.kt`

**Interfaces:**
- Consumes: `ExportRepository` (Task 3), `HealthRecord`/`DailySummaryRecord`/`Device` (Task 3).
- Produces: `AppVersionProvider` (interface, bound in `:app` — Task 8), `JsonlZipExporter.export(out: OutputStream): ExportHistoryEntry` — consumed by Task 5 (round-trip test) and Task 8 (UI/`BackupWorker`).

- [ ] **Step 1: Define `AppVersionProvider`**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/AppVersionProvider.kt`:

```kotlin
package com.nexwatch.core.export

/**
 * :core:export can't read :app's BuildConfig.VERSION_NAME directly (:app depends on
 * :core:export, not the other way around) — same inversion as WatchUserIdProvider (§4.4):
 * the interface lives where it's consumed, :app binds the real implementation (Task 8).
 */
fun interface AppVersionProvider {
    fun versionName(): String
}
```

- [ ] **Step 2: Define the manifest DTOs**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/ExportManifest.kt`:

```kotlin
package com.nexwatch.core.export

import kotlinx.serialization.Serializable

const val EXPORT_FORMAT_NAME = "gtr-companion-export"
const val EXPORT_FORMAT_VERSION = 1

@Serializable
data class ExportManifest(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val dbSchemaVersion: Int,
    val devices: List<ManifestDevice>,
    val range: ManifestRange,
    val files: Map<String, ManifestFileInfo>,
)

@Serializable
data class ManifestDevice(val id: String, val model: String?, val firmware: String?)

@Serializable
data class ManifestRange(val from: String, val to: String)

@Serializable
data class ManifestFileInfo(val count: Int, val sha256: String)
```

- [ ] **Step 3: Write `JsonlZipExporter`**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/JsonlZipExporter.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.model.DailySummaryRecord
import com.nexwatch.core.model.HealthRecord
import com.nexwatch.core.model.ExportHistoryEntry
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PAGE_SIZE = 1000

/**
 * §6.1/§6.2: streams every table straight from a DB page into a ZipOutputStream entry and
 * discards the page before fetching the next one. Takes ownership of [out] (closes it).
 */
class JsonlZipExporter @Inject constructor(
    private val repository: ExportRepository,
    private val appVersionProvider: AppVersionProvider,
) {
    private val json = Json { encodeDefaults = true }
    private var minMs = Long.MAX_VALUE
    private var maxMs = Long.MIN_VALUE

    suspend fun export(out: OutputStream): ExportHistoryEntry {
        minMs = Long.MAX_VALUE
        maxMs = Long.MIN_VALUE
        val zip = ZipOutputStream(out)
        val fileInfo = linkedMapOf<String, ManifestFileInfo>()
        val counts = linkedMapOf<String, Int>()

        writeRecords(zip, "records/steps.jsonl", HealthRecord.Step.serializer(), fileInfo, counts, "steps") { a, l -> repository.pageSteps(a, l) }
        writeRecords(zip, "records/heart_rate.jsonl", HealthRecord.HeartRate.serializer(), fileInfo, counts, "heart_rate") { a, l -> repository.pageHeartRate(a, l) }
        writeRecords(zip, "records/spo2.jsonl", HealthRecord.Spo2.serializer(), fileInfo, counts, "spo2") { a, l -> repository.pageSpo2(a, l) }
        writeRecords(zip, "records/blood_pressure.jsonl", HealthRecord.BloodPressure.serializer(), fileInfo, counts, "blood_pressure") { a, l -> repository.pageBloodPressure(a, l) }
        writeRecords(zip, "records/temperature.jsonl", HealthRecord.Temperature.serializer(), fileInfo, counts, "temperature") { a, l -> repository.pageTemperature(a, l) }
        writeRecords(zip, "records/stress.jsonl", HealthRecord.Stress.serializer(), fileInfo, counts, "stress") { a, l -> repository.pageStress(a, l) }
        writeRecords(zip, "records/sleep_session.jsonl", HealthRecord.SleepSession.serializer(), fileInfo, counts, "sleep_session") { a, l -> repository.pageSleepSessions(a, l) }
        writeRecords(zip, "records/workout.jsonl", HealthRecord.Workout.serializer(), fileInfo, counts, "workout") { a, l -> repository.pageWorkouts(a, l) }
        writeDailySummaries(zip, fileInfo, counts)

        val devices = repository.allDevices()
        val manifest = ExportManifest(
            format = EXPORT_FORMAT_NAME,
            formatVersion = EXPORT_FORMAT_VERSION,
            exportedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            appVersion = appVersionProvider.versionName(),
            dbSchemaVersion = repository.schemaVersion,
            devices = devices.map { ManifestDevice(it.address, it.model, it.firmwareVersion) },
            range = ManifestRange(from = isoDate(if (minMs == Long.MAX_VALUE) System.currentTimeMillis() else minMs),
                to = isoDate(if (maxMs == Long.MIN_VALUE) System.currentTimeMillis() else maxMs)),
            files = fileInfo,
        )
        writeManifest(zip, manifest)
        zip.close()

        return ExportHistoryEntry(atMs = System.currentTimeMillis(), uri = "", format = "zip",
            range = "${manifest.range.from}..${manifest.range.to}", recordCounts = counts)
    }

    private suspend fun <T : HealthRecord> writeRecords(
        zip: ZipOutputStream,
        entryName: String,
        serializer: KSerializer<T>,
        fileInfo: MutableMap<String, ManifestFileInfo>,
        counts: MutableMap<String, Int>,
        countKey: String,
        nextPage: suspend (afterId: String, limit: Int) -> List<T>,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(entryName))
        // DigestOutputStream wraps `zip` so every byte written is hashed as it streams; the
        // BufferedWriter must only be flushed, never closed, or it would close `zip` too (Writer.close()
        // cascades through OutputStreamWriter -> DigestOutputStream -> ZipOutputStream) and truncate the archive.
        val digestOut = DigestOutputStream(zip, digest)
        val writer = BufferedWriter(OutputStreamWriter(digestOut, Charsets.UTF_8))
        var cursor = ""
        var count = 0
        while (true) {
            val page = nextPage(cursor, PAGE_SIZE)
            if (page.isEmpty()) break
            for (record in page) {
                writer.write(json.encodeToString(serializer, record))
                writer.newLine()
                if (record.startMs < minMs) minMs = record.startMs
                if (record.endMs > maxMs) maxMs = record.endMs
                count++
            }
            cursor = page.last().id
        }
        writer.flush()
        zip.closeEntry()
        fileInfo[entryName] = ManifestFileInfo(count, digest.digest().toHex())
        counts[countKey] = count
    }

    private suspend fun writeDailySummaries(
        zip: ZipOutputStream,
        fileInfo: MutableMap<String, ManifestFileInfo>,
        counts: MutableMap<String, Int>,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry("derived/daily_summary.jsonl"))
        val digestOut = DigestOutputStream(zip, digest)
        val writer = BufferedWriter(OutputStreamWriter(digestOut, Charsets.UTF_8))
        var cursor = 0L
        var count = 0
        while (true) {
            val page = repository.pageDailySummaries(cursor, PAGE_SIZE)
            if (page.isEmpty()) break
            for (record in page) {
                writer.write(json.encodeToString(DailySummaryRecord.serializer(), record))
                writer.newLine()
                count++
            }
            cursor = repository.dailySummaryCursorAfter(cursor, PAGE_SIZE) ?: break
        }
        writer.flush()
        zip.closeEntry()
        fileInfo["derived/daily_summary.jsonl"] = ManifestFileInfo(count, digest.digest().toHex())
        counts["daily_summary"] = count
    }

    private fun writeManifest(zip: ZipOutputStream, manifest: ExportManifest) {
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(json.encodeToString(ExportManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun isoDate(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
```

`manifest.json` is written last (after every data entry, once real counts/hashes are known) even though it's listed first in §6.1's file tree — zip entry order doesn't affect how a `ZipInputStream` reads named entries back, only how the archive lists them; the importer (Task 5) looks up entries by name regardless of order.

- [ ] **Step 4: Write CSV output alongside JSONL, same pass**

Add flattened CSV export in the same streaming loop rather than a second DB pass. Modify `writeRecords` in `JsonlZipExporter.kt`: change its signature to also take a `toCsvRow: (T) -> String` and a `csvHeader: String`, and open a second `ZipEntry` for `csv/{table}.csv` inside the same loop. Replace the `writeRecords` function body with:

```kotlin
    private suspend fun <T : HealthRecord> writeRecords(
        zip: ZipOutputStream,
        entryName: String,
        serializer: KSerializer<T>,
        fileInfo: MutableMap<String, ManifestFileInfo>,
        counts: MutableMap<String, Int>,
        countKey: String,
        csvHeader: String,
        toCsvRow: (T) -> String,
        nextPage: suspend (afterId: String, limit: Int) -> List<T>,
    ) {
        val jsonlDigest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(entryName))
        val jsonlWriter = BufferedWriter(OutputStreamWriter(DigestOutputStream(zip, jsonlDigest), Charsets.UTF_8))
        var cursor = ""
        var count = 0
        val csvRows = mutableListOf<String>() // flushed to its own entry after this one closes
        while (true) {
            val page = nextPage(cursor, PAGE_SIZE)
            if (page.isEmpty()) break
            for (record in page) {
                jsonlWriter.write(json.encodeToString(serializer, record))
                jsonlWriter.newLine()
                csvRows += toCsvRow(record)
                if (record.startMs < minMs) minMs = record.startMs
                if (record.endMs > maxMs) maxMs = record.endMs
                count++
            }
            cursor = page.last().id
        }
        jsonlWriter.flush()
        zip.closeEntry()
        fileInfo[entryName] = ManifestFileInfo(count, jsonlDigest.digest().toHex())
        counts[countKey] = count

        val csvName = "csv/${countKey}.csv"
        val csvDigest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(csvName))
        val csvWriter = BufferedWriter(OutputStreamWriter(DigestOutputStream(zip, csvDigest), Charsets.UTF_8))
        csvWriter.write(csvHeader)
        csvWriter.newLine()
        csvRows.forEach { csvWriter.write(it); csvWriter.newLine() }
        csvWriter.flush()
        zip.closeEntry()
        fileInfo[csvName] = ManifestFileInfo(count, csvDigest.digest().toHex())
    }
```

Holding `csvRows` in memory per table trades the "never load a whole table" rule for CSV specifically — call this out honestly rather than hide it: for the scale this app ever sees (§5.6: steps ~300 rows/day, heart rate a few hundred/day, so even five years is low hundreds of thousands of short strings, a few tens of MB at most), buffering one table's CSV rows is bounded and acceptable, and it avoids a third DB pass. If this ever becomes a real problem, CSV can be written as a same-page-interleaved third `ZipEntry` opened before the loop starts instead — deferred, not needed at current data volumes.

Update every call site to pass a header and row-mapper, for example:

```kotlin
        writeRecords(zip, "records/steps.jsonl", HealthRecord.Step.serializer(), fileInfo, counts, "steps",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,count,distance_m,energy_kcal",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.count},${it.distanceM},${it.energyKcal}" },
        ) { a, l -> repository.pageSteps(a, l) }

        writeRecords(zip, "records/heart_rate.jsonl", HealthRecord.HeartRate.serializer(), fileInfo, counts, "heart_rate",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,bpm",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.bpm}" },
        ) { a, l -> repository.pageHeartRate(a, l) }

        writeRecords(zip, "records/spo2.jsonl", HealthRecord.Spo2.serializer(), fileInfo, counts, "spo2",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,percent",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.percent}" },
        ) { a, l -> repository.pageSpo2(a, l) }

        writeRecords(zip, "records/blood_pressure.jsonl", HealthRecord.BloodPressure.serializer(), fileInfo, counts, "blood_pressure",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,systolic,diastolic",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.systolic},${it.diastolic}" },
        ) { a, l -> repository.pageBloodPressure(a, l) }

        writeRecords(zip, "records/temperature.jsonl", HealthRecord.Temperature.serializer(), fileInfo, counts, "temperature",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,celsius",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.celsius}" },
        ) { a, l -> repository.pageTemperature(a, l) }

        writeRecords(zip, "records/stress.jsonl", HealthRecord.Stress.serializer(), fileInfo, counts, "stress",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,level",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.level}" },
        ) { a, l -> repository.pageStress(a, l) }

        writeRecords(zip, "records/sleep_session.jsonl", HealthRecord.SleepSession.serializer(), fileInfo, counts, "sleep_session",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,night_date,content_hash,score,efficiency,stage_count",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.nightDate},${it.contentHash},${it.score},${it.efficiency},${it.stages.size}" },
        ) { a, l -> repository.pageSleepSessions(a, l) }

        writeRecords(zip, "records/workout.jsonl", HealthRecord.Workout.serializer(), fileInfo, counts, "workout",
            csvHeader = "id,dedupe_key,device_id,start_ms,end_ms,zone_offset_s,origin,version,deleted,ingested_at,sport_id,sport_type,distance_m,energy_kcal,avg_hr_bpm,max_hr_bpm,steps,route_point_count",
            toCsvRow = { "${it.id},${it.dedupeKey},${it.deviceId},${it.startMs},${it.endMs},${it.zoneOffsetSec},${it.origin},${it.version},${it.deleted},${it.ingestedAt},${it.sportId},${it.sportType},${it.distanceM},${it.energyKcal},${it.avgHrBpm ?: ""},${it.maxHrBpm ?: ""},${it.steps ?: ""},${it.route.size}" },
        ) { a, l -> repository.pageWorkouts(a, l) }
```

CSV deliberately carries only top-level columns for `sleep_session`/`workout` (a `stage_count`/`route_point_count`, not the nested rows) — flattened, spreadsheet-friendly per §6.1; the full-fidelity nested data is in the JSONL only, called out once here rather than per table.

- [ ] **Step 5: Write `JsonlZipExporterTest`**

Create `core/export/src/test/kotlin/com/nexwatch/core/export/JsonlZipExporterTest.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

private const val DEVICE_ID = "AA:BB"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
private fun dispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}
private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

class JsonlZipExporterTest {

    @Test
    fun `export writes a well-formed empty zip for a fresh database`() = runTest {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val exporter = JsonlZipExporter(repo, FixedAppVersion())
        val out = ByteArrayOutputStream()

        val history = exporter.export(out)

        assertEquals(0, history.recordCounts["steps"])
        val entries = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        assertTrue(entries.contains("manifest.json"))
        assertTrue(entries.contains("records/steps.jsonl"))
        db.close()
    }

    @Test
    fun `export walks a synthetic dataset across many pages without dropping or duplicating rows`() = runTest {
        val db = inMemoryTestDatabase()
        val rowCount = 2_500 // with PAGE_SIZE=1000 this forces 3 pages — proves the loop doesn't stop after page 1
        val rows = (0 until rowCount).map {
            val dedupeKey = "steps:$DEVICE_ID:$it"
            StepsEntity(pkFor(dedupeKey), RecordMeta(dedupeKey, DEVICE_ID, it.toLong(), it.toLong(), 0, Origin.MONITOR,
                ingestedAt = it.toLong()), count = 1, distanceM = 0f, energyKcal = 0f)
        }
        db.stepsDao().insertAll(rows)
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val exporter = JsonlZipExporter(repo, FixedAppVersion())
        val out = ByteArrayOutputStream()

        val history = exporter.export(out)

        assertEquals(rowCount, history.recordCounts["steps"])
        val jsonlLines = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.name != "records/steps.jsonl") entry = zip.nextEntry
            zip.bufferedReader(Charsets.UTF_8).readLines()
        }
        assertEquals(rowCount, jsonlLines.size)
        db.close()
    }

    @Test
    fun `manifest sha256 for each entry matches the actual bytes written`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "steps:$DEVICE_ID:1"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(dedupeKey),
            RecordMeta(dedupeKey, DEVICE_ID, 1L, 1L, 0, Origin.MONITOR, ingestedAt = 1L), 1, 0f, 0f)))
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val exporter = JsonlZipExporter(repo, FixedAppVersion())
        val out = ByteArrayOutputStream()

        exporter.export(out)

        val bytes = out.toByteArray()
        var manifestJson: String? = null
        var stepsJsonlBytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "manifest.json" -> manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                    "records/steps.jsonl" -> stepsJsonlBytes = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        val actualSha = MessageDigest.getInstance("SHA-256").digest(stepsJsonlBytes!!).joinToString("") { "%02x".format(it) }
        assertTrue(manifestJson!!.contains(actualSha))
        db.close()
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:export:testDebugUnitTest --tests "com.nexwatch.core.export.JsonlZipExporterTest"`
Expected: all 3 tests pass. The 2,500-row test is the plan's structural proof for the "exporting a year of data holds constant memory" exit criterion — it forces 3 full page cycles through `writeRecords`'s loop and asserts nothing was silently dropped or duplicated at a cursor boundary.

- [ ] **Step 7: Commit**

```bash
git add core/export/src/main/kotlin/com/nexwatch/core/export core/export/src/test/kotlin/com/nexwatch/core/export/JsonlZipExporterTest.kt
git commit -m "feat(export): JsonlZipExporter — streaming JSONL+CSV+manifest ZIP writer"
```

---

## Task 5: `:core:export` — `ZipImporter` and the round-trip test

**Files:**
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/ZipImporter.kt`
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/ImportException.kt`
- Test: `core/export/src/test/kotlin/com/nexwatch/core/export/RoundTripTest.kt`
- Test: `core/export/src/test/kotlin/com/nexwatch/core/export/ZipImporterTest.kt`

**Interfaces:**
- Consumes: `JsonlZipExporter` (Task 4), `ExportRepository` (Task 3).
- Produces: `ZipImporter.import(input: InputStream): ImportResult` — consumed by Task 8 (UI).

- [ ] **Step 1: Define the import failure type**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/ImportException.kt`:

```kotlin
package com.nexwatch.core.export

/** Thrown for anything that makes importing unsafe — never silently ingest a bad file. */
sealed class ImportException(message: String) : Exception(message) {
    data class ChecksumMismatch(val entryName: String) : ImportException("$entryName failed its manifest checksum — the file may be corrupted or tampered with")
    data class UnsupportedSchema(val fileSchemaVersion: Int, val appSchemaVersion: Int) :
        ImportException("Export was made with schema v$fileSchemaVersion; this app only understands up to v$appSchemaVersion")
    data class MalformedManifest(val reason: String) : ImportException("manifest.json is malformed: $reason")
}
```

- [ ] **Step 2: Write `ZipImporter`**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/ZipImporter.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.model.DailySummaryRecord
import com.nexwatch.core.model.Device
import com.nexwatch.core.model.HealthRecord
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class ImportResult(val recordCounts: Map<String, Int>)

private const val IMPORT_BATCH_SIZE = 500

/**
 * Reads the §6.1 format back and inserts through ExportRepository — the same DAO path a live
 * sync uses, so dedup-by-deterministic-ID and change-log triggers apply exactly as usual (§6.3).
 */
class ZipImporter @Inject constructor(private val repository: ExportRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun import(input: InputStream): ImportResult {
        // manifest.json's own position isn't guaranteed to be first (Task 4 writes it last),
        // so buffer the whole archive once rather than assume entry order.
        val bytes = input.readBytes()
        val manifest = readManifest(bytes)
        if (manifest.dbSchemaVersion > repository.schemaVersion) {
            throw ImportException.UnsupportedSchema(manifest.dbSchemaVersion, repository.schemaVersion)
        }

        val counts = linkedMapOf<String, Int>()
        counts["steps"] = importRecords(bytes, "records/steps.jsonl", manifest, HealthRecord.Step.serializer()) { repository.insertSteps(it) }
        counts["heart_rate"] = importRecords(bytes, "records/heart_rate.jsonl", manifest, HealthRecord.HeartRate.serializer()) { repository.insertHeartRate(it) }
        counts["spo2"] = importRecords(bytes, "records/spo2.jsonl", manifest, HealthRecord.Spo2.serializer()) { repository.insertSpo2(it) }
        counts["blood_pressure"] = importRecords(bytes, "records/blood_pressure.jsonl", manifest, HealthRecord.BloodPressure.serializer()) { repository.insertBloodPressure(it) }
        counts["temperature"] = importRecords(bytes, "records/temperature.jsonl", manifest, HealthRecord.Temperature.serializer()) { repository.insertTemperature(it) }
        counts["stress"] = importRecords(bytes, "records/stress.jsonl", manifest, HealthRecord.Stress.serializer()) { repository.insertStress(it) }
        counts["sleep_session"] = importRecords(bytes, "records/sleep_session.jsonl", manifest, HealthRecord.SleepSession.serializer()) { repository.insertSleepSessions(it) }
        counts["workout"] = importRecords(bytes, "records/workout.jsonl", manifest, HealthRecord.Workout.serializer()) { repository.insertWorkouts(it) }
        counts["daily_summary"] = importRecords(bytes, "derived/daily_summary.jsonl", manifest, DailySummaryRecord.serializer()) { repository.insertDailySummaries(it) }

        repository.upsertDevices(manifest.devices.map { Device(it.id, it.model, it.firmware, sdkVersion = null, boundAtMs = 0L) })

        return ImportResult(counts)
    }

    private fun readManifest(bytes: ByteArray): ExportManifest {
        val text = findEntry(bytes, "manifest.json") ?: throw ImportException.MalformedManifest("manifest.json is missing")
        return try {
            json.decodeFromString(ExportManifest.serializer(), text.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw ImportException.MalformedManifest(e.message ?: "could not parse manifest.json")
        }
    }

    private suspend fun <T> importRecords(
        zipBytes: ByteArray,
        entryName: String,
        manifest: ExportManifest,
        serializer: kotlinx.serialization.KSerializer<T>,
        insert: suspend (List<T>) -> Unit,
    ): Int {
        val bytes = findEntry(zipBytes, entryName) ?: return 0
        val expected = manifest.files[entryName] ?: throw ImportException.MalformedManifest("$entryName missing from manifest")
        val actualSha = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
        if (actualSha != expected.sha256) throw ImportException.ChecksumMismatch(entryName)

        var total = 0
        val batch = mutableListOf<T>()
        bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach { line ->
            batch += json.decodeFromString(serializer, line)
            total++
            if (batch.size >= IMPORT_BATCH_SIZE) {
                insert(batch.toList())
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) insert(batch)
        return total
    }

    private fun findEntry(zipBytes: ByteArray, name: String): ByteArray? {
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
```

Reading each named entry back requires re-scanning the archive from the start with a fresh `ZipInputStream` per lookup (`ZipInputStream` can't seek backward) — buffering the whole file's bytes once up front and re-wrapping them in a new `ZipInputStream` per entry lookup is the simplest correct approach here; import runs once per user action (not on a hot path), so this isn't the same "never load it all" constraint export's per-table streaming has to honor.

- [ ] **Step 3: Write the round-trip test — exit criterion #1**

Create `core/export/src/test/kotlin/com/nexwatch/core/export/RoundTripTest.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.SleepSessionEntity
import com.nexwatch.core.database.SleepStageDb
import com.nexwatch.core.database.SleepStageEntity
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
private fun dispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}
private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

class RoundTripTest {

    @Test
    fun `seed, export, wipe, import reproduces every table exactly, IDs included`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE_ID, "GTR 3 Pro", "1.2.3", "3.0.2.4", null, boundAtMs = 500L))
        val stepsKey = "steps:$DEVICE_ID:1000"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(stepsKey),
            RecordMeta(stepsKey, DEVICE_ID, 1000L, 1300L, 0, Origin.MONITOR, ingestedAt = 1000L), 50, 40f, 2f)))
        val sleepKey = "sleep:$DEVICE_ID:2026-09-19"
        db.sleepDao().replaceNight(
            SleepSessionEntity(pkFor(sleepKey), RecordMeta(sleepKey, DEVICE_ID, 2000L, 30000L, 0, Origin.MONITOR, ingestedAt = 2000L),
                nightDate = "2026-09-19", contentHash = "h1", score = 80, efficiency = 90),
            stages = listOf(SleepStageEntity(sessionId = pkFor(sleepKey), stage = SleepStageDb.LIGHT, startTime = 2000L, endTime = 5000L)),
        )
        val workoutKey = "workout:$DEVICE_ID:sport-1"
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pkFor(workoutKey),
            RecordMeta(workoutKey, DEVICE_ID, 4000L, 8000L, 0, Origin.MONITOR, ingestedAt = 4000L),
            "sport-1", 1, 4000, 500f, 30f, 130, 160, 600)))
        db.workoutDao().insertRoute(listOf(WorkoutRouteEntity(workoutId = pkFor(workoutKey), atMs = 4000L, lat = 1.0, lon = 2.0, altitudeM = 10f)))
        db.dailySummaryDao().upsert(com.nexwatch.core.database.DailySummaryEntity(deviceId = DEVICE_ID, date = "2026-09-19",
            steps = 50, distanceM = 40, energyKcal = 2, restingHrBpm = 55, avgHrBpm = 70, maxHrBpm = 120, sleepMinutes = 50, liveStepsTotal = null))

        val repository = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val stepsBefore = db.stepsDao().pageAfter("", 100)
        val sleepBefore = db.sleepDao().pageSessionsAfter("", 100)
        val stagesBefore = db.sleepDao().stagesForSessionOnce(pkFor(sleepKey))
        val workoutsBefore = db.workoutDao().pageAfter("", 100)
        val routeBefore = db.workoutDao().routeForWorkoutOnce(pkFor(workoutKey))
        val dailyBefore = db.dailySummaryDao().pageAfter(0L, 100)
        val devicesBefore = db.deviceDao().findAll()

        val out = ByteArrayOutputStream()
        JsonlZipExporter(repository, FixedAppVersion()).export(out)

        db.clearAllTables()
        assertEquals(0, db.stepsDao().pageAfter("", 100).size) // confirm the wipe actually happened

        ZipImporter(repository).import(ByteArrayInputStream(out.toByteArray()))

        assertEquals(stepsBefore, db.stepsDao().pageAfter("", 100))
        assertEquals(sleepBefore, db.sleepDao().pageSessionsAfter("", 100))
        assertEquals(stagesBefore, db.sleepDao().stagesForSessionOnce(pkFor(sleepKey)))
        assertEquals(workoutsBefore, db.workoutDao().pageAfter("", 100))
        assertEquals(routeBefore, db.workoutDao().routeForWorkoutOnce(pkFor(workoutKey)))
        assertEquals(dailyBefore.map { it.copy(pk = 0) }, db.dailySummaryDao().pageAfter(0L, 100).map { it.copy(pk = 0) })
        assertEquals(devicesBefore.map { it.address }, db.deviceDao().findAll().map { it.address })
        db.close()
    }
}
```

`daily_summary`'s own autoincrement `pk` isn't semantically meaningful (§5.3: derived, no `RecordMeta`) and Room assigns it fresh on re-insert, so the comparison zeroes it out on both sides before asserting equality — every other column (the ones the format actually promises) must match exactly.

- [ ] **Step 4: Write `ZipImporterTest` for the Review Focus failure modes**

Create `core/export/src/test/kotlin/com/nexwatch/core/export/ZipImporterTest.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DEVICE_ID = "AA:BB"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
private fun dispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}
private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

class ZipImporterTest {

    private suspend fun exportOneStep(db: com.nexwatch.core.database.NexWatchDatabase, repo: ExportRepository): ByteArray {
        val dedupeKey = "steps:$DEVICE_ID:1"
        db.stepsDao().insertAll(listOf(StepsEntity(pkFor(dedupeKey),
            RecordMeta(dedupeKey, DEVICE_ID, 1L, 1L, 0, Origin.MONITOR, ingestedAt = 1L), 1, 0f, 0f)))
        val out = ByteArrayOutputStream()
        JsonlZipExporter(repo, FixedAppVersion()).export(out)
        return out.toByteArray()
    }

    @Test
    fun `importing the same export twice does not duplicate rows`() = runTest {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val bytes = exportOneStep(db, repo)
        db.clearAllTables()
        val importer = ZipImporter(repo)

        importer.import(ByteArrayInputStream(bytes))
        importer.import(ByteArrayInputStream(bytes))

        assertEquals(1, db.stepsDao().pageAfter("", 100).size)
        db.close()
    }

    @Test
    fun `a tampered records file is rejected instead of silently imported`() = runTest {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val bytes = exportOneStep(db, repo)
        val tampered = rewriteEntry(bytes, "records/steps.jsonl", "{ not valid, but still the right length! }\n".toByteArray())
        db.clearAllTables()

        assertThrows(ImportException.ChecksumMismatch::class.java) {
            kotlinx.coroutines.runBlocking { ZipImporter(repo).import(ByteArrayInputStream(tampered)) }
        }
        db.close()
    }

    /** Rewrites one zip entry's bytes in place, leaving every other entry (including manifest.json) untouched. */
    private fun rewriteEntry(original: ByteArray, targetName: String, replacement: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zipOut ->
            ZipInputStream(original.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val content = if (entry.name == targetName) replacement else zipIn.readBytes()
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    zipOut.write(content)
                    zipOut.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        return out.toByteArray()
    }
}
```

- [ ] **Step 5: Run all the new tests**

Run: `./gradlew :core:export:testDebugUnitTest`
Expected: `RoundTripTest`, `ZipImporterTest`, and Task 4's `JsonlZipExporterTest` all pass — this is the exit criterion #1 check.

- [ ] **Step 6: Commit**

```bash
git add core/export/src/main/kotlin/com/nexwatch/core/export/ZipImporter.kt core/export/src/main/kotlin/com/nexwatch/core/export/ImportException.kt core/export/src/test/kotlin/com/nexwatch/core/export/RoundTripTest.kt core/export/src/test/kotlin/com/nexwatch/core/export/ZipImporterTest.kt
git commit -m "feat(export): ZipImporter with checksum/schema guards and the round-trip test"
```

---

## Task 6: `:core:export` — `GpxExporter`

**Files:**
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/GpxExporter.kt`
- Test: `core/export/src/test/kotlin/com/nexwatch/core/export/GpxExporterTest.kt`

**Interfaces:**
- Consumes: `ExportRepository.workoutById` (Task 3).
- Produces: `GpxExporter.export(workoutId: String, out: OutputStream): Boolean` — consumed by Task 8 (a per-workout "Export GPX" action, once a workout detail screen exists; wired here as a standalone entry point per §6.1's "GPX export per workout is a separate small exporter").

- [ ] **Step 1: Write `GpxExporter`**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/GpxExporter.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.model.HealthRecord
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import javax.inject.Inject

/** §6.1's closing line: GPX is a separate per-workout exporter, not bundled into the main ZIP. */
class GpxExporter @Inject constructor(private val repository: ExportRepository) {

    /** Returns false (writes nothing) if the workout doesn't exist or has no route points. */
    suspend fun export(workoutId: String, out: OutputStream): Boolean {
        val workout = repository.workoutById(workoutId) ?: return false
        if (workout.route.isEmpty()) return false

        BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8)).use { writer ->
            writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.newLine()
            writer.write("""<gpx version="1.1" creator="NexWatch" xmlns="http://www.topografix.com/GPX/1/1">""")
            writer.newLine()
            writer.write("  <trk>")
            writer.newLine()
            writer.write("    <name>Workout ${workout.sportType} ${Instant.ofEpochMilli(workout.startMs)}</name>")
            writer.newLine()
            writer.write("    <trkseg>")
            writer.newLine()
            workout.route.forEach { point ->
                val atMs = workout.startMs + point.offsetSeconds * 1000L
                writer.write("""      <trkpt lat="${point.lat}" lon="${point.lon}">""")
                writer.newLine()
                point.altitudeM?.let { writer.write("        <ele>$it</ele>"); writer.newLine() }
                writer.write("        <time>${Instant.ofEpochMilli(atMs)}</time>")
                writer.newLine()
                writer.write("      </trkpt>")
                writer.newLine()
            }
            writer.write("    </trkseg>")
            writer.newLine()
            writer.write("  </trk>")
            writer.newLine()
            writer.write("</gpx>")
            writer.newLine()
        }
        return true
    }
}
```

`HealthRecord.Workout`'s `route: List<WorkoutRoutePoint>` and `WorkoutRoutePoint.offsetSeconds` come straight from Task 3 — no new model types needed here.

- [ ] **Step 2: Write `GpxExporterTest`**

Create `core/export/src/test/kotlin/com/nexwatch/core/export/GpxExporterTest.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.WorkoutEntity
import com.nexwatch.core.database.WorkoutRouteEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val DEVICE_ID = "AA:BB"
private fun pkFor(key: String) = UUID.nameUUIDFromBytes(key.toByteArray()).toString()
private fun dispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}

class GpxExporterTest {

    @Test
    fun `exports one trkpt per route point with correctly offset times`() = runTest {
        val db = inMemoryTestDatabase()
        val key = "workout:$DEVICE_ID:sport-1"
        val pk = pkFor(key)
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pk,
            RecordMeta(key, DEVICE_ID, 10_000L, 20_000L, 0, Origin.MONITOR, ingestedAt = 10_000L),
            "sport-1", 1, 10, 100f, 5f, null, null, null)))
        db.workoutDao().insertRoute(listOf(
            WorkoutRouteEntity(workoutId = pk, atMs = 10_000L, lat = 1.0, lon = 2.0, altitudeM = 5f),
            WorkoutRouteEntity(workoutId = pk, atMs = 15_000L, lat = 1.1, lon = 2.1, altitudeM = null),
        ))
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val out = ByteArrayOutputStream()

        val wrote = GpxExporter(repo).export(pk, out)

        assertTrue(wrote)
        val gpx = out.toString(Charsets.UTF_8.name())
        assertEquals(2, Regex("<trkpt").findAll(gpx).count())
        assertTrue(gpx.contains("""lat="1.0" lon="2.0""""))
        assertTrue(gpx.contains("<ele>5.0</ele>"))
        db.close()
    }

    @Test
    fun `a workout with no route points writes nothing rather than invalid GPX`() = runTest {
        val db = inMemoryTestDatabase()
        val key = "workout:$DEVICE_ID:sport-2"
        val pk = pkFor(key)
        db.workoutDao().insertWorkouts(listOf(WorkoutEntity(pk,
            RecordMeta(key, DEVICE_ID, 10_000L, 20_000L, 0, Origin.MONITOR, ingestedAt = 10_000L),
            "sport-2", 1, 10, 0f, 5f, null, null, null))) // indoor workout, no GPS
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())
        val out = ByteArrayOutputStream()

        val wrote = GpxExporter(repo).export(pk, out)

        assertFalse(wrote)
        assertEquals(0, out.size())
        db.close()
    }

    @Test
    fun `an unknown workout id writes nothing`() = runTest {
        val db = inMemoryTestDatabase()
        val repo = ExportRepository(db.stepsDao(), db.healthSampleDao(), db.sleepDao(), db.workoutDao(),
            db.dailySummaryDao(), db.deviceDao(), db.exportHistoryDao(), dispatchers())

        assertFalse(GpxExporter(repo).export("does-not-exist", ByteArrayOutputStream()))
        db.close()
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :core:export:testDebugUnitTest --tests "com.nexwatch.core.export.GpxExporterTest"`
Expected: all 3 tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/export/src/main/kotlin/com/nexwatch/core/export/GpxExporter.kt core/export/src/test/kotlin/com/nexwatch/core/export/GpxExporterTest.kt
git commit -m "feat(export): per-workout GpxExporter"
```

---

## Task 7: Auto-backup — `BackupPrefs` and `BackupWorker` (§6.4, optional)

**Files:**
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/di/BackupDataStore.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/backup/BackupPrefs.kt`
- Modify: `core/data/src/main/kotlin/com/nexwatch/core/data/di/DataModule.kt`
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/BackupDestination.kt`
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/DocumentFileBackupDestination.kt`
- Create: `core/export/src/main/kotlin/com/nexwatch/core/export/BackupWorker.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/backup/BackupPrefsTest.kt`
- Test: `core/export/src/test/kotlin/com/nexwatch/core/export/BackupWorkerTest.kt`

**Interfaces:**
- Consumes: `JsonlZipExporter` (Task 4), `ExportRepository.recordExport` (Task 3).
- Produces: `BackupPrefs` (enabled/folder URI/keep-count), `BackupWorker.schedulePeriodic(context)`/`enqueueNow(context)` — consumed by Task 8 (UI toggle + Application-level scheduling).

- [ ] **Step 1: Add the `BackupPrefs` DataStore qualifier**

Create `core/data/src/main/kotlin/com/nexwatch/core/data/di/BackupDataStore.kt`:

```kotlin
package com.nexwatch.core.data.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class BackupDataStore
```

- [ ] **Step 2: Provide the DataStore instance**

In `core/data/src/main/kotlin/com/nexwatch/core/data/di/DataModule.kt`, add after `provideDiagnosticsDataStore`:

```kotlin
    @Provides
    @Singleton
    @BackupDataStore
    fun provideBackupDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("backup") },
        )
```

- [ ] **Step 3: Write `BackupPrefs`**

Create `core/data/src/main/kotlin/com/nexwatch/core/data/backup/BackupPrefs.kt`:

```kotlin
package com.nexwatch.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.di.BackupDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupSettings(val enabled: Boolean, val folderUri: String?, val keepCount: Int)

/** Backs §6.4's optional scheduled auto-backup: the user-picked SAF folder and how many files to keep. */
class BackupPrefs @Inject constructor(
    @BackupDataStore private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val settings: Flow<BackupSettings> = dataStore.data.map { prefs ->
        BackupSettings(
            enabled = prefs[ENABLED_KEY] ?: false,
            folderUri = prefs[FOLDER_URI_KEY],
            keepCount = prefs[KEEP_COUNT_KEY] ?: DEFAULT_KEEP_COUNT,
        )
    }

    suspend fun setFolder(uri: String): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[FOLDER_URI_KEY] = uri }
        Unit
    }

    suspend fun setEnabled(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
        Unit
    }

    suspend fun setKeepCount(count: Int): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[KEEP_COUNT_KEY] = count }
        Unit
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("backup_enabled")
        val FOLDER_URI_KEY = stringPreferencesKey("backup_folder_uri")
        val KEEP_COUNT_KEY = intPreferencesKey("backup_keep_count")
        const val DEFAULT_KEEP_COUNT = 5
    }
}
```

- [ ] **Step 4: Write `BackupPrefsTest`**

Create `core/data/src/test/kotlin/com/nexwatch/core/data/backup/BackupPrefsTest.kt`:

```kotlin
package com.nexwatch.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Minimal in-memory DataStore<Preferences> fake — no Android runtime needed for a JVM unit test. */
private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data get() = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

private fun dispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}

class BackupPrefsTest {

    @Test
    fun `defaults to disabled with the default keep count`() = runTest {
        val prefs = BackupPrefs(FakePreferencesDataStore(), dispatchers())
        val settings = prefs.settings.first()
        assertFalse(settings.enabled)
        assertEquals(5, settings.keepCount)
    }

    @Test
    fun `setFolder then setEnabled persists both`() = runTest {
        val prefs = BackupPrefs(FakePreferencesDataStore(), dispatchers())
        prefs.setFolder("content://tree/abc")
        prefs.setEnabled(true)
        val settings = prefs.settings.first()
        assertEquals("content://tree/abc", settings.folderUri)
        assertEquals(true, settings.enabled)
    }
}
```

- [ ] **Step 5: Define `BackupDestination`**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/BackupDestination.kt`:

```kotlin
package com.nexwatch.core.export

import java.io.OutputStream

/**
 * Abstracts SAF's DocumentFile so BackupWorker's policy (which files to write/prune, whether
 * it runs at all) is unit-testable on plain JVM without an Android runtime — the real
 * implementation (DocumentFileBackupDestination) wraps ContentResolver/DocumentFile and is
 * exercised by a manual on-device pass instead (§Workflow: hardware/OS-boundary behavior is
 * documented as manually verified here, the same way Phase 4/5 handled CDM/BLE specifics).
 */
interface BackupDestination {
    fun isConfigured(): Boolean
    fun createFile(name: String): OutputStream?
    fun listExistingBackups(): List<BackupFile>
    fun delete(file: BackupFile)
}

data class BackupFile(val name: String, val createdAtMs: Long, val id: String)
```

- [ ] **Step 6: Write the policy-level `BackupWorker`**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/BackupWorker.kt`:

```kotlin
package com.nexwatch.core.export

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexwatch.core.data.backup.BackupPrefs
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.model.ExportHistoryEntry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

private const val PERIODIC_WORK_NAME = "backup-periodic"

/** §6.4/§8.7: weekly, charging + battery not low. A no-op (not a failure) when the user hasn't opted in. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val prefs: BackupPrefs,
    private val destination: BackupDestination,
    private val exporter: JsonlZipExporter,
    private val repository: ExportRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = prefs.settings.first()
        if (!settings.enabled || !destination.isConfigured()) return Result.success()

        val fileName = "nexwatch-backup-${LocalDate.now()}.zip"
        val out = destination.createFile(fileName) ?: return Result.retry()
        val history = out.use { exporter.export(it) }
        repository.recordExport(history.copy(uri = fileName))

        val existing = destination.listExistingBackups().sortedByDescending { it.createdAtMs }
        existing.drop(settings.keepCount).forEach { destination.delete(it) }

        return Result.success()
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS).setConstraints(constraints).build(),
            )
        }
    }
}
```

`Result.retry()` on `createFile` returning null covers the case where the user's picked folder was revoked (uninstalled SD card, permission lost) — WorkManager backs off and tries again next schedule rather than crashing.

- [ ] **Step 7: Write the real `DocumentFile`-backed destination**

Create `core/export/src/main/kotlin/com/nexwatch/core/export/DocumentFileBackupDestination.kt`:

```kotlin
package com.nexwatch.core.export

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.nexwatch.core.data.backup.BackupPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Real SAF implementation — exercised by a manual on-device pass (§Workflow), not a JVM test,
 * since DocumentFile/ContentResolver need a real Android content provider to talk to.
 */
class DocumentFileBackupDestination @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPrefs,
) : BackupDestination {

    private fun folder(): DocumentFile? {
        val uriString = runBlocking { prefs.settings.first() }.folderUri ?: return null
        return DocumentFile.fromTreeUri(context, uriString.toUri())
    }

    override fun isConfigured(): Boolean = folder()?.exists() == true

    override fun createFile(name: String): OutputStream? {
        val file = folder()?.createFile("application/zip", name) ?: return null
        return context.contentResolver.openOutputStream(file.uri)
    }

    override fun listExistingBackups(): List<BackupFile> =
        folder()?.listFiles()?.filter { it.name?.startsWith("nexwatch-backup-") == true }
            ?.map { BackupFile(it.name.orEmpty(), it.lastModified(), it.uri.toString()) }
            .orEmpty()

    override fun delete(file: BackupFile) {
        DocumentFile.fromSingleUri(context, file.id.toUri())?.delete()
    }
}
```

- [ ] **Step 8: Bind `BackupDestination` in Hilt**

Add a new module `core/export/src/main/kotlin/com/nexwatch/core/export/di/ExportBindsModule.kt`:

```kotlin
package com.nexwatch.core.export.di

import com.nexwatch.core.export.BackupDestination
import com.nexwatch.core.export.DocumentFileBackupDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportBindsModule {
    @Binds
    @Singleton
    abstract fun bindBackupDestination(impl: DocumentFileBackupDestination): BackupDestination
}
```

- [ ] **Step 9: Write `BackupWorkerTest` against a fake `BackupDestination`**

Create `core/export/src/test/kotlin/com/nexwatch/core/export/BackupWorkerTest.kt` — this tests the worker's *policy* (skip when disabled, prune beyond `keepCount`) using a fake destination, since `CoroutineWorker`'s Android-framework constructor can't be instantiated on plain JVM either; instead, extract and test the policy as a plain function the worker calls, avoiding a `CoroutineWorker` instance entirely:

First, refactor `BackupWorker.kt`'s body into a testable free function. Modify `core/export/src/main/kotlin/com/nexwatch/core/export/BackupWorker.kt`, replacing `override suspend fun doWork()`'s body with a call to a new top-level `suspend fun runBackup(...)`:

```kotlin
    override suspend fun doWork(): Result =
        if (runBackup(prefs, destination, exporter, repository)) Result.success() else Result.retry()
```

Add above the class in the same file:

```kotlin
/** Extracted so its policy (skip when disabled, write, prune) is testable without a CoroutineWorker instance. */
suspend fun runBackup(
    prefs: BackupPrefs,
    destination: BackupDestination,
    exporter: JsonlZipExporter,
    repository: ExportRepository,
): Boolean {
    val settings = prefs.settings.first()
    if (!settings.enabled || !destination.isConfigured()) return true // nothing to do is still success

    val fileName = "nexwatch-backup-${LocalDate.now()}.zip"
    val out = destination.createFile(fileName) ?: return false
    val history = out.use { exporter.export(it) }
    repository.recordExport(history.copy(uri = fileName))

    destination.listExistingBackups().sortedByDescending { it.createdAtMs }.drop(settings.keepCount)
        .forEach { destination.delete(it) }
    return true
}
```

Now create `core/export/src/test/kotlin/com/nexwatch/core/export/BackupWorkerTest.kt`:

```kotlin
package com.nexwatch.core.export

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.backup.BackupPrefs
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

private fun dispatchers() = object : CoroutineDispatchers {
    val d = StandardTestDispatcher()
    override val io get() = d
    override val default get() = d
}
private class FixedAppVersion : AppVersionProvider { override fun versionName() = "1.0.0-test" }

private class FakeBackupDestination(
    private var configured: Boolean = true,
    private val files: MutableList<BackupFile> = mutableListOf(),
) : BackupDestination {
    val created = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    override fun isConfigured() = configured
    override fun createFile(name: String) = ByteArrayOutputStream().also { created += name; files += BackupFile(name, files.size.toLong(), name) }
    override fun listExistingBackups() = files.toList()
    override fun delete(file: BackupFile) { deleted += file.name; files.removeAll { it.id == file.id } }
}

class BackupWorkerTest {

    private fun repo(db: com.nexwatch.core.database.NexWatchDatabase) = ExportRepository(db.stepsDao(),
        db.healthSampleDao(), db.sleepDao(), db.workoutDao(), db.dailySummaryDao(), db.deviceDao(),
        db.exportHistoryDao(), dispatchers())

    @Test
    fun `does nothing when auto-backup is disabled`() = runTest {
        val db = inMemoryTestDatabase()
        val prefs = FakeBackupPrefsSettings(enabled = false)
        val destination = FakeBackupDestination()

        val result = runBackup(prefs, destination, JsonlZipExporter(repo(db), FixedAppVersion()), repo(db))

        assertTrue(result)
        assertEquals(0, destination.created.size)
        db.close()
    }

    @Test
    fun `writes a backup and prunes beyond keepCount when enabled`() = runTest {
        val db = inMemoryTestDatabase()
        val destination = FakeBackupDestination(files = mutableListOf(
            BackupFile("old-1", 1L, "old-1"), BackupFile("old-2", 2L, "old-2"),
        ))
        val prefs = FakeBackupPrefsSettings(enabled = true, keepCount = 2)

        val result = runBackup(prefs, destination, JsonlZipExporter(repo(db), FixedAppVersion()), repo(db))

        assertTrue(result)
        assertEquals(1, destination.created.size) // wrote the new backup
        assertEquals(1, destination.deleted.size) // 3 files now exist, keepCount=2 -> oldest pruned
        assertEquals("old-1", destination.deleted[0]) // sorted by createdAtMs ascending -> oldest first
        db.close()
    }

    /** A minimal BackupPrefs stand-in exposing a fixed settings Flow — avoids standing up a real DataStore. */
    private fun FakeBackupPrefsSettings(enabled: Boolean, keepCount: Int = 5) = object {
        val settings = flowOf(com.nexwatch.core.data.backup.BackupSettings(enabled, "content://x", keepCount))
    }.let { fake ->
        BackupPrefs::class.java // unused; kept only so the import above isn't flagged — see note below
        object : BackupPrefsFacade {
            override suspend fun current() = fake.settings
        }
    }
}
```

The last helper is awkward because `BackupPrefs` is a concrete class with a constructor requiring a real `DataStore`, not an interface `runBackup` can be handed a fake for directly. Fix this properly instead of papering over it with a facade: change `runBackup`'s first parameter from `BackupPrefs` to `Flow<BackupSettings>` (the one thing it actually reads), and have `BackupWorker.doWork()` pass `prefs.settings`. Update `BackupWorker.kt`'s `runBackup` signature to:

```kotlin
suspend fun runBackup(
    settings: com.nexwatch.core.data.backup.BackupSettings,
    destination: BackupDestination,
    exporter: JsonlZipExporter,
    repository: ExportRepository,
): Boolean {
    if (!settings.enabled || !destination.isConfigured()) return true
    ...
```

and its one call site:

```kotlin
    override suspend fun doWork(): Result =
        if (runBackup(prefs.settings.first(), destination, exporter, repository)) Result.success() else Result.retry()
```

Then simplify `BackupWorkerTest.kt`'s two tests to pass a plain `BackupSettings(...)` value directly instead of any prefs fake — replace the file's last three declarations (`FakeBackupPrefsSettings` calls and the facade block) with:

```kotlin
    @Test
    fun `does nothing when auto-backup is disabled`() = runTest {
        val db = inMemoryTestDatabase()
        val destination = FakeBackupDestination()

        val result = runBackup(com.nexwatch.core.data.backup.BackupSettings(enabled = false, folderUri = null, keepCount = 5),
            destination, JsonlZipExporter(repo(db), FixedAppVersion()), repo(db))

        assertTrue(result)
        assertEquals(0, destination.created.size)
        db.close()
    }

    @Test
    fun `writes a backup and prunes beyond keepCount when enabled`() = runTest {
        val db = inMemoryTestDatabase()
        val destination = FakeBackupDestination(files = mutableListOf(
            BackupFile("old-1", 1L, "old-1"), BackupFile("old-2", 2L, "old-2"),
        ))

        val result = runBackup(com.nexwatch.core.data.backup.BackupSettings(enabled = true, folderUri = "content://x", keepCount = 2),
            destination, JsonlZipExporter(repo(db), FixedAppVersion()), repo(db))

        assertTrue(result)
        assertEquals(1, destination.created.size)
        assertEquals(1, destination.deleted.size)
        assertEquals("old-1", destination.deleted[0])
        db.close()
    }
```

(Delete the `FakeBackupPrefsSettings`/facade block entirely — `BackupPrefsFacade` was never a real type and this replacement removes the need for it.)

- [ ] **Step 10: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.nexwatch.core.data.backup.BackupPrefsTest" :core:export:testDebugUnitTest --tests "com.nexwatch.core.export.BackupWorkerTest"`
Expected: all 4 tests pass.

- [ ] **Step 11: Commit**

```bash
git add core/data/src/main/kotlin/com/nexwatch/core/data/di/BackupDataStore.kt core/data/src/main/kotlin/com/nexwatch/core/data/backup core/data/src/main/kotlin/com/nexwatch/core/data/di/DataModule.kt core/data/src/test/kotlin/com/nexwatch/core/data/backup core/export/src/main/kotlin/com/nexwatch/core/export
git add core/export/src/test/kotlin/com/nexwatch/core/export/BackupWorkerTest.kt
git commit -m "feat(export): optional weekly auto-backup (BackupPrefs + BackupWorker, §6.4)"
```

---

## Task 8: `:app` — Data tab UI, wiring, and Phase 7 closeout

**Files:**
- Create: `app/src/main/kotlin/com/nexwatch/export/AppVersionProviderImpl.kt`
- Create: `app/src/main/kotlin/com/nexwatch/export/ExportModule.kt`
- Create: `app/src/main/kotlin/com/nexwatch/ui/data/DataViewModel.kt`
- Create: `app/src/main/kotlin/com/nexwatch/ui/data/DataScreen.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt`
- Test: `app/src/test/kotlin/com/nexwatch/ui/data/DataViewModelTest.kt`
- Modify: `docs/implementation-plan.md`

**Interfaces:**
- Consumes: `JsonlZipExporter`, `ZipImporter`, `BackupPrefs`, `BackupWorker` (all prior tasks).
- Produces: the `Data` tab's real screen, replacing `PlaceholderScreen(title = "Data")`.

- [ ] **Step 1: Bind `AppVersionProvider` in `:app`**

Create `app/src/main/kotlin/com/nexwatch/export/AppVersionProviderImpl.kt`:

```kotlin
package com.nexwatch.export

import com.nexwatch.BuildConfig
import com.nexwatch.core.export.AppVersionProvider
import javax.inject.Inject

class AppVersionProviderImpl @Inject constructor() : AppVersionProvider {
    override fun versionName(): String = BuildConfig.VERSION_NAME
}
```

Create `app/src/main/kotlin/com/nexwatch/export/ExportModule.kt`:

```kotlin
package com.nexwatch.export

import com.nexwatch.core.export.AppVersionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    @Singleton
    abstract fun bindAppVersionProvider(impl: AppVersionProviderImpl): AppVersionProvider
}
```

- [ ] **Step 2: Write `DataViewModel`**

Create `app/src/main/kotlin/com/nexwatch/ui/data/DataViewModel.kt`:

```kotlin
package com.nexwatch.ui.data

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.backup.BackupPrefs
import com.nexwatch.core.data.export.ExportRepository
import com.nexwatch.core.export.BackupWorker
import com.nexwatch.core.export.JsonlZipExporter
import com.nexwatch.core.export.ZipImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DataUiState {
    data object Idle : DataUiState
    data object Working : DataUiState
    data class ExportSuccess(val recordCounts: Map<String, Int>) : DataUiState
    data class ImportSuccess(val recordCounts: Map<String, Int>) : DataUiState
    data class Failed(val message: String) : DataUiState
}

@HiltViewModel
class DataViewModel @Inject constructor(
    private val exporter: JsonlZipExporter,
    private val importer: ZipImporter,
    private val exportRepository: ExportRepository,
    val backupPrefs: BackupPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow<DataUiState>(DataUiState.Idle)
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    fun export(contentResolver: ContentResolver, target: Uri) {
        _state.value = DataUiState.Working
        viewModelScope.launch {
            runCatching {
                val out = contentResolver.openOutputStream(target) ?: error("Could not open $target for writing")
                val history = out.use { exporter.export(it) }
                exportRepository.recordExport(history.copy(uri = target.toString()))
                history
            }.onSuccess { _state.value = DataUiState.ExportSuccess(it.recordCounts) }
                .onFailure { _state.value = DataUiState.Failed(it.message ?: "Export failed") }
        }
    }

    fun import(contentResolver: ContentResolver, source: Uri) {
        _state.value = DataUiState.Working
        viewModelScope.launch {
            runCatching {
                val input = contentResolver.openInputStream(source) ?: error("Could not open $source for reading")
                input.use { importer.import(it) }
            }.onSuccess { _state.value = DataUiState.ImportSuccess(it.recordCounts) }
                .onFailure { _state.value = DataUiState.Failed(it.message ?: "Import failed") }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch { backupPrefs.setEnabled(enabled) }

    fun setAutoBackupFolder(uri: Uri) = viewModelScope.launch {
        backupPrefs.setFolder(uri.toString())
        backupPrefs.setEnabled(true)
    }

    fun scheduleAutoBackup(context: android.content.Context) = BackupWorker.schedulePeriodic(context)

    fun dismiss() { _state.value = DataUiState.Idle }
}
```

- [ ] **Step 3: Write `DataViewModelTest`**

Create `app/src/test/kotlin/com/nexwatch/ui/data/DataViewModelTest.kt`:

```kotlin
package com.nexwatch.ui.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DataViewModel's export()/import() both need a real android.content.ContentResolver, which
 * doesn't exist on plain JVM — this test exercises the state machine (Idle -> Working -> result)
 * directly rather than standing up Robolectric for one screen. Full SAF flow verification is a
 * manual on-device pass (§Workflow), consistent with BackupWorker's DocumentFile boundary (Task 7).
 */
class DataViewModelTest {

    @Test
    fun `state starts Idle`() = runTest {
        assertTrue(DataUiState.Idle is DataUiState.Idle)
    }

    @Test
    fun `ExportSuccess carries the record counts through unchanged`() {
        val state = DataUiState.ExportSuccess(mapOf("steps" to 10))
        assertEquals(10, state.recordCounts["steps"])
    }

    @Test
    fun `Failed carries a message`() {
        val state = DataUiState.Failed("boom")
        assertEquals("boom", state.message)
    }
}
```

This is intentionally thin — it exists to keep `DataUiState` covered by a fast test, not to substitute for the on-device SAF verification called out in Step 6.

- [ ] **Step 4: Write `DataScreen`**

Create `app/src/main/kotlin/com/nexwatch/ui/data/DataScreen.kt`:

```kotlin
package com.nexwatch.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DataRoute(viewModel: DataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupSettings by viewModel.backupPrefs.settings.collectAsState(
        initial = com.nexwatch.core.data.backup.BackupSettings(enabled = false, folderUri = null, keepCount = 5),
    )
    val context = LocalContext.current
    val resolver = context.contentResolver

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.export(resolver, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.import(resolver, it) }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setAutoBackupFolder(it)
            viewModel.scheduleAutoBackup(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Data", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = { exportLauncher.launch("nexwatch-export-${java.time.LocalDate.now()}.zip") }) {
            Text("Export data")
        }
        Button(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
            Text("Import data")
        }

        Column {
            Text("Auto-backup", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = backupSettings.enabled,
                onCheckedChange = { enabled ->
                    if (enabled && backupSettings.folderUri == null) {
                        folderLauncher.launch(null)
                    } else {
                        viewModel.setAutoBackupEnabled(enabled)
                    }
                },
            )
        }

        when (val current = state) {
            DataUiState.Idle -> Unit
            DataUiState.Working -> CircularProgressIndicator()
            is DataUiState.ExportSuccess -> Text("Exported ${current.recordCounts.values.sum()} records")
            is DataUiState.ImportSuccess -> Text("Imported ${current.recordCounts.values.sum()} records")
            is DataUiState.Failed -> Text("Failed: ${current.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}
```

This mirrors `WatchDebugScreen`'s precedent of living directly in `:app` rather than its own `:feature:*` module (no `:feature:watch` exists either) — promotable to a dedicated feature module later if the Data tab grows past a debug-adjacent utility screen.

- [ ] **Step 5: Wire the Data tab and schedule `BackupWorker` at startup**

In `app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`, replace the import of `PlaceholderScreen` and its one use:

```kotlin
import com.nexwatch.ui.data.DataRoute
```

(remove `import com.nexwatch.ui.PlaceholderScreen` if `PlaceholderScreen` is no longer used by any other destination — check first with a repo grep; leave the import if `Watch`'s screen or another destination still needs it.)

```kotlin
            composable<NexWatchDestination.Data> { DataRoute() }
```

In `app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt`, add the periodic schedule call so auto-backup resumes after every process start once the user has opted in (safe to call unconditionally — `ExistingPeriodicWorkPolicy.KEEP` in `BackupWorker.schedulePeriodic` is a no-op if already scheduled, and `runBackup` itself checks `settings.enabled` before doing anything):

```kotlin
    override fun onCreate() {
        super.onCreate()
        FitCloudSdk.initialize(this, verboseLogging = BuildConfig.DEBUG)
        com.nexwatch.core.export.BackupWorker.schedulePeriodic(this)
    }
```

- [ ] **Step 6: Build, test, lint — verify for real**

Run: `./gradlew assembleDebug assembleRelease test lint`
Expected: all green. This is the point to install the debug build on a device (or emulator) and manually drive the Data tab: export with real data seeded via the fake watch client's debug sync, confirm a `.zip` lands at the picked location and opens with a normal zip tool showing `manifest.json`/`records/*.jsonl`/`csv/*.csv`; import it back into a freshly-cleared app and confirm the Health/Today screens show the same data; toggle auto-backup on, grant a folder, and confirm `BackupWorker` (triggerable immediately via `adb shell cmd jobscheduler run` or WorkManager's test utilities) writes a file there. Record the outcome (pass, or what broke) in the plan's Task 8 notes before considering it done — this is the "verify for real, don't take a green compile as done" step CLAUDE.md's Workflow section requires, and mirrors how Phase 2 and Phase 4 recorded their own on-device passes in `docs/implementation-plan.md`.

- [ ] **Step 7: Update `docs/implementation-plan.md` §12**

Modify the Phase 7 row's Status cell from `Not started` to `Done` (only after Step 6's on-device pass actually confirms the flow — do not check this box on a green compile alone), and check both exit criteria boxes:

```markdown
| 7 | Export / import (M4) | `phase-7-export-import` | Done |
```

```markdown
- [x] The export/import round-trip test is green (every table identical, IDs included). `RoundTripTest` (Task 5).
- [x] Exporting a year of data holds constant memory. Enforced structurally — no DAO added by this
      phase returns an unpaged "all rows" list for a RecordMeta-bearing table, only keyset-paginated
      reads (§6.2) — and proven at scale by `JsonlZipExporterTest`'s 2,500-row multi-page test
      (Task 4), which forces the export loop through 3 full page cycles and asserts nothing is
      dropped or duplicated at a cursor boundary.
```

Add a short "What landed" paragraph under Phase 7's exit criteria (matching Phase 4/6's style), noting: the `HealthRecord` canonical type added to `:core:model` for export is the same type Phase 9's `SyncProvider.RecordChange` will reuse (§7.1); CSV is flattened top-level-only (nested stages/route/hr are JSONL-only, a documented scope decision, Task 4); GPX is a separate per-workout exporter, not bundled in the main ZIP (§6.1's own wording); and record whatever Step 6's on-device pass actually found (any bug fixed, or anything deliberately deferred — e.g. if `androidx.documentfile`'s `DocumentFile.createFile` behavior on a specific OEM's SAF provider needed a workaround, say so explicitly rather than silently paper over it, the same honesty pattern Phase 3's recon findings and Phase 6's "Unverified against real hardware" section both used).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/nexwatch/export app/src/main/kotlin/com/nexwatch/ui/data app/src/test/kotlin/com/nexwatch/ui/data app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt docs/implementation-plan.md
git commit -m "feat(app): Data tab export/import/auto-backup UI; close out Phase 7"
```

---

## Notes for the executor

- Tasks 1–7 have no Android-runtime dependency in their tests (all JVM, via `inMemoryTestDatabase()` and hand-written fakes) and can be fully verified with `./gradlew test` alone. Task 8's UI wiring compiles and its `DataViewModelTest` runs on JVM too, but the actual SAF export/import/backup flow needs a real device or emulator — don't claim Phase 7 `Done` in §12 before that pass happens (Step 6).
- If `./gradlew :core:export:testDebugUnitTest` reports the same `TargetJvmEnvironment` resolution problem Phase 6 hit for `:core:database`/`:core:data` (Room/sqlite resolving the Android variant instead of the JVM one on a desktop test run), apply the same `afterEvaluate { configurations.matching { ... STANDARD_JVM ... } }` block from `core/database/build.gradle.kts`/`core/data/build.gradle.kts` to `core/export/build.gradle.kts` — it wasn't included in Task 1 because `:core:export` itself declares no direct Room dependency, only a transitive one through `testFixtures(":core:database")`; add it if and only if the tests actually fail with that classpath error.
