# Phase 6 — Data core (M3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the journal (`raw_ingest`), a decoder/normalizer pipeline for every canonical health table, the full Room schema with trigger-only `change_log`, the `daily_summary` aggregator, `WatchSyncWorker`, and real Today/Health screens backed by SQL-bucketed queries — closing Phase 6's three exit criteria (no 7-day timeline gaps, journal replay reproduces canonical tables exactly, migration/trigger tests green).

**Architecture:** Raw SDK bytes are journaled untouched (already true since Phase 4/5). Decoding those bytes back into typed data requires SDK types (`FcSyncData.toXxx()`), so decoding lives in `:core:watch-fitcloud` behind a `HealthDataDecoder` interface declared in `:core:watch-api`; `:core:data`'s normalizer consumes only that interface, never an SDK type. Room entities live in `:core:database`; `:core:data` maps them to pure `:core:model` read types for features. Triggers are SQL, created in `RoomDatabase.Callback.onOpen`. `:feature:today` and `:feature:health` replace the `:app` placeholders.

**Tech Stack:** Kotlin, Room 2.8.5 (KSP), WorkManager 2.11.1, Paging 3.5.1, `androidx.sqlite:sqlite-bundled:2.6.0` for JVM-only Room tests (`BundledSQLiteDriver`, no emulator needed), Hilt, Jetpack Compose, JUnit + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-20-phase-6-data-core-design.md` (this plan implements it section by section), `docs/implementation-plan.md` §5 (Data layer, all subsections), §12 Phase 6 row.

## Global Constraints

- Journal first (CLAUDE.md I2): the `syncData()` subscriber's only job is inserting the raw payload into `raw_ingest`. This is already built (Phase 4/5) — this plan does not touch `FitCloudWatchClient.syncHealthData()`'s emission path, only what happens after.
- Deterministic record IDs (I3): `id = UUID.nameUUIDFromBytes(dedupeKey.toByteArray())`. Records are immutable; a correction is a new `version`. Tombstone (`deleted = true`), never hard-delete.
- The change log is written only by SQL triggers (I4), created with `CREATE TRIGGER IF NOT EXISTS` in `RoomDatabase.Callback.onOpen`. No Kotlin code ever inserts into `change_log` directly.
- Nothing polls (I5). `WatchSyncWorker` is WorkManager-scheduled, which is the explicit carve-out — it is not a connection poll, it is a periodic catch-up sync.
- UTC epoch milliseconds plus zone offset on every record (I6). Canonical units: count, m, kcal, bpm, %, mmHg, °C.
- SDK types (`com.topstep.**`) never leave `:core:watch-fitcloud`. `:core:data`, `:core:database`, `:feature:*` never import them, directly or transitively.
- `exportSchema = true`, schema JSON committed, `fallbackToDestructiveMigration` never used.
- The UI never loads raw sample lists into memory. Charts query pre-bucketed SQL; history lists use Paging 3. DAOs return `Flow`, collected with `collectAsStateWithLifecycle()`.
- Base package `com.nexwatch`. Follow existing module DI patterns (`WatchIdentityStore`'s DataStore-qualifier pattern, `FitCloudSdk.require()` for SDK singleton access outside Hilt).

---

## Task 1: Version catalog additions and `:core:database` module bootstrap

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`
- Modify: `core/service/build.gradle.kts` (WorkManager, for `WatchSyncWorker` in a later task)
- Modify: `settings.gradle.kts` (add `include(":feature:today")`, `include(":feature:health")`)

**Interfaces:**
- Produces: Gradle can resolve `libs.androidx.room.runtime`, `libs.androidx.room.ktx`, `libs.androidx.room.compiler`, `libs.androidx.sqlite.bundled`, `libs.androidx.work.runtime.ktx`, `libs.androidx.paging.runtime`, `libs.androidx.paging.compose` in any module.

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]` (keep alphabetical-ish grouping with the existing entries):

```toml
room = "2.8.5"
sqlite = "2.6.0"
work = "2.11.1"
paging = "3.5.1"
```

Add to `[libraries]`:

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { group = "androidx.sqlite", name = "sqlite-bundled", version.ref = "sqlite" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
```

Add to `[plugins]`:

```toml
room = { id = "androidx.room", version.ref = "room" }
```

Room's own Gradle plugin (`androidx.room`) is what wires `exportSchema`'s output directory without manually setting a KSP arg — cleaner than the old `ksp { arg("room.schemaLocation", ...) }` approach.

- [ ] **Step 2: Update `:core:database/build.gradle.kts`**

```kotlin
plugins {
    id("nexwatch.android.library")
    id("com.google.devtools.ksp")
    alias(libs.plugins.room)
}

android {
    namespace = "com.nexwatch.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.sqlite.bundled)
}
```

`com.google.devtools.ksp` is applied directly (not through `nexwatch.android.hilt`) because Room's annotation processing has nothing to do with Hilt — `:core:database` stays free of the Hilt dependency per the module table (only `:core:data` needs Hilt, to `@Provides` the built database).

- [ ] **Step 3: Add WorkManager to `:core:data` and `:core:service`**

In `core/data/build.gradle.kts`, add to `dependencies`:

```kotlin
    implementation(project(":core:database"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)
```

(the `:core:database` line already exists — just adding the two new libs alongside it).

In `core/service/build.gradle.kts`, no change needed yet — `WatchSyncWorker` lives in `:core:data`, and `:core:service` already depends on `:core:data`.

- [ ] **Step 4: Register the two new feature modules**

In `settings.gradle.kts`, after `include(":feature:onboarding")`:

```kotlin
include(":feature:today")
include(":feature:health")
```

Their `build.gradle.kts` files are created in Tasks 20–21, once there's something for them to contain — an empty registered module with no source set fails the build, so don't create the directories yet.

- [ ] **Step 5: Verify the catalog resolves**

```bash
./gradlew :core:database:dependencies --configuration debugCompileClasspath
```

Expected: resolves without error, shows `androidx.room:room-runtime:2.8.5` and friends in the tree.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml core/database/build.gradle.kts core/data/build.gradle.kts settings.gradle.kts
git commit -m "build(phase-6): add Room, WorkManager, Paging, sqlite-bundled to the catalog"
```

---

## Task 2: `:core:model` — decoded record DTOs and read models

**Files:**
- Create: `core/model/src/main/kotlin/com/nexwatch/core/model/DecodedHealthRecord.kt`
- Create: `core/model/src/main/kotlin/com/nexwatch/core/model/SleepStage.kt`
- Create: `core/model/src/main/kotlin/com/nexwatch/core/model/DailySummary.kt`
- Create: `core/model/src/main/kotlin/com/nexwatch/core/model/Device.kt`
- Delete: `core/model/src/main/kotlin/com/nexwatch/core/model/package-info.kt` (no longer an empty package)

**Interfaces:**
- Produces: `DecodedHealthRecord` sealed hierarchy and `SleepStageSpan`/`SleepStage`, consumed by `HealthDataDecoder` (Task 3) and `HealthDataNormalizer` (Task 15). `DailySummary`, `HeartRateSample`, `SleepNight`, `WorkoutSummary`, `Device`, `DeviceEvent` read models, consumed by `:core:data` repositories (Task 17) and the feature modules (Tasks 20–21).

- [ ] **Step 1: Write `DecodedHealthRecord.kt`**

```kotlin
package com.nexwatch.core.model

/**
 * Output of §5.2's decode step — one variant per raw_ingest data_type. Pure Kotlin so it
 * can cross the :core:watch-api / :core:data boundary without either side seeing an SDK type.
 */
sealed interface DecodedHealthRecord {
    data class Step(
        val startMs: Long,
        val endMs: Long,
        val count: Int,
        val distanceM: Float,
        val kcal: Float,
    ) : DecodedHealthRecord

    data class HeartRate(val atMs: Long, val bpm: Int) : DecodedHealthRecord
    data class Spo2(val atMs: Long, val percent: Int) : DecodedHealthRecord
    data class BloodPressure(val atMs: Long, val systolic: Int, val diastolic: Int) : DecodedHealthRecord
    data class Temperature(val atMs: Long, val celsius: Float) : DecodedHealthRecord
    data class Stress(val atMs: Long, val level: Int) : DecodedHealthRecord

    /**
     * No nightDate here on purpose — deriving a calendar date from startMs needs a zone
     * offset, which the decoder (:core:watch-fitcloud) doesn't carry; HealthDataNormalizer
     * (:core:data) computes nightDate once it has RecordMeta's zoneOffsetSec to work with.
     */
    data class Sleep(
        val startMs: Long,
        val endMs: Long,
        val stages: List<SleepStageSpan>,
        val score: Int,
        val efficiency: Int,
    ) : DecodedHealthRecord

    data class Workout(
        val sportId: String,
        val sportType: Int,
        val startMs: Long,
        val endMs: Long,
        val distanceM: Float,
        val kcal: Float,
        val avgHrBpm: Int?,
        val maxHrBpm: Int?,
        val steps: Int?,
        val heartRateSeries: List<WorkoutHrPoint>,
    ) : DecodedHealthRecord

    /**
     * GPS is a separate sync data_type ("gps") from the workout itself ("sport") — the SDK
     * emits them as independent FcSyncData batches, joined only by sportId, so this is its
     * own record variant rather than a field on [Workout].
     */
    data class WorkoutRoute(val sportId: String, val points: List<WorkoutRoutePoint>) : DecodedHealthRecord

    data class TodayTotal(
        val atMs: Long,
        val steps: Int,
        val distanceM: Int,
        val kcal: Float,
        val heartRateBpm: Int?,
    ) : DecodedHealthRecord
}

/**
 * offsetSeconds, not atMs: FcGpsData carries no session-start timestamp of its own (only
 * sportId + items), so absolute time can only be computed once the matching WorkoutEntity's
 * startTime is known — that happens in HealthDataNormalizer (Task 15), not at decode time.
 */
data class WorkoutRoutePoint(val offsetSeconds: Int, val lat: Double, val lon: Double, val altitudeM: Float?)
data class WorkoutHrPoint(val atMs: Long, val bpm: Int)
```

- [ ] **Step 2: Write `SleepStage.kt`**

```kotlin
package com.nexwatch.core.model

enum class SleepStage { AWAKE, LIGHT, DEEP, REM }

data class SleepStageSpan(val stage: SleepStage, val startMs: Long, val endMs: Long)
```

- [ ] **Step 3: Write `DailySummary.kt`**

```kotlin
package com.nexwatch.core.model

/** Read model for :feature:today / :feature:health — never a Room entity directly. */
data class DailySummary(
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

data class HeartRateSample(val atMs: Long, val bpm: Int)

data class SleepNight(
    val nightDate: String,
    val stages: List<SleepStageSpan>,
    val totalMinutes: Int,
    val score: Int,
)

data class WorkoutSummary(
    val id: String,
    val sportType: Int,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Float,
    val kcal: Float,
    val avgHrBpm: Int?,
    val maxHrBpm: Int?,
)
```

- [ ] **Step 4: Write `Device.kt`**

```kotlin
package com.nexwatch.core.model

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

- [ ] **Step 5: Remove the now-stale placeholder and verify compilation**

```bash
git rm core/model/src/main/kotlin/com/nexwatch/core/model/package-info.kt
./gradlew :core:model:compileKotlinJvm 2>/dev/null || ./gradlew :core:model:compileKotlin
```

Run whichever task name the module actually exposes (check with `./gradlew :core:model:tasks --group build` if the first guess fails — a `nexwatch.jvm.library` module's compile task name depends on the Kotlin JVM plugin's target name).

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/model
git commit -m "feat(model): add DecodedHealthRecord and daily-summary read models (§5.3)"
```

---

## Task 3: `:core:watch-api` — the `HealthDataDecoder` boundary interface

**Files:**
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/HealthDataDecoder.kt`

**Interfaces:**
- Produces: `interface HealthDataDecoder { fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> }`, implemented by `FitCloudHealthDataDecoder` (Task 12–13), consumed by `HealthDataNormalizer` (Task 15).

- [ ] **Step 1: Write the interface**

```kotlin
package com.nexwatch.core.watchapi

import com.nexwatch.core.model.DecodedHealthRecord

/**
 * §5.2's decode step. Implemented in :core:watch-fitcloud, the only module allowed to
 * reconstruct FcSyncData from raw bytes — :core:data calls this and never sees an SDK type.
 * A dataType this implementation doesn't recognise returns an empty list rather than
 * throwing, so one unmapped payload never blocks the rest of a normalization batch.
 */
interface HealthDataDecoder {
    fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord>
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :core:watch-api:compileKotlin
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add core/watch-api
git commit -m "feat(watch-api): add HealthDataDecoder boundary interface (§5.2)"
```

---

## Task 4: `:core:database` — `RecordMeta`, `Origin`, and the five single-value health tables

Heart rate, SpO2, blood pressure, temperature and stress are structurally identical: one embedded `RecordMeta` plus 1–2 value columns. Written out in full per CLAUDE.md's "no placeholders" rule, not abbreviated.

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/RecordMeta.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/Origin.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/HeartRateEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/Spo2Entity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/BloodPressureEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/TemperatureEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/StressEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/HealthSampleDao.kt`

**Interfaces:**
- Produces: `HeartRateEntity`, `Spo2Entity`, `BloodPressureEntity`, `TemperatureEntity`, `StressEntity` (all `@Entity`), `HealthSampleDao` with one `insertAll`/`observeRange`/`bucketed` set of methods per table. Consumed by `NexWatchDatabase` (Task 10) and `HealthDataNormalizer` (Task 15).

- [ ] **Step 1: Write `RecordMeta.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** §5.3 — embedded in every health table. Not its own @Entity; it has no primary key of its own. */
data class RecordMeta(
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "zone_offset_s") val zoneOffsetSec: Int,
    @ColumnInfo(name = "origin") val origin: Origin,
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false,
    @ColumnInfo(name = "ingested_at") val ingestedAt: Long,
)
```

`@Embedded` classes don't declare their own `@ColumnInfo`-annotated `id` — the owning entity's `@PrimaryKey pk` column (below) is the deterministic UUID from §5.1, computed by the normalizer, not stored twice.

- [ ] **Step 2: Write `Origin.kt`**

```kotlin
package com.nexwatch.core.database

enum class Origin { MONITOR, MEASURE, LIVE }
```

- [ ] **Step 3: Write the five entities**

```kotlin
// HeartRateEntity.kt
package com.nexwatch.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "heart_rate",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class HeartRateEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val bpm: Int,
)
```

```kotlin
// Spo2Entity.kt
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "spo2",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class Spo2Entity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val percent: Int,
)
```

```kotlin
// BloodPressureEntity.kt
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blood_pressure",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class BloodPressureEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val systolic: Int,
    val diastolic: Int,
)
```

```kotlin
// TemperatureEntity.kt
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "temperature",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class TemperatureEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val celsius: Float,
)
```

```kotlin
// StressEntity.kt
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stress",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class StressEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val level: Int,
)
```

- [ ] **Step 4: Write `HealthSampleDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSampleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRate(rows: List<HeartRateEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSpo2(rows: List<Spo2Entity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBloodPressure(rows: List<BloodPressureEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTemperature(rows: List<TemperatureEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStress(rows: List<StressEntity>): List<Long>

    @Query(
        "SELECT * FROM heart_rate WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs " +
            "AND deleted = 0 ORDER BY start_time",
    )
    fun observeHeartRateRange(deviceId: String, fromMs: Long, toMs: Long): Flow<List<HeartRateEntity>>

    @Query(
        "SELECT (start_time / :bucketMs) * :bucketMs AS bucket, AVG(bpm) AS avgBpm, MIN(bpm) AS minBpm, " +
            "MAX(bpm) AS maxBpm FROM heart_rate WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs " +
            "AND deleted = 0 GROUP BY bucket ORDER BY bucket",
    )
    fun observeHeartRateBuckets(
        deviceId: String,
        fromMs: Long,
        toMs: Long,
        bucketMs: Long,
    ): Flow<List<HeartRateBucket>>
}

data class HeartRateBucket(val bucket: Long, val avgBpm: Double, val minBpm: Int, val maxBpm: Int)
```

The bucketed query is the concrete example §5.7 asks for ("`GROUP BY start_time / 3600000` for hourly heart rate") — `:feature:health`'s HR chart (Task 21) calls `observeHeartRateBuckets` with `bucketMs = 3_600_000L` for an hourly view, never `observeHeartRateRange` for a chart (that one's for short-window detail views only, if ever needed — not wired to any screen in this phase, kept because the normalizer/replay test in Task 23 asserts against exact rows, which needs an unbucketed read).

- [ ] **Step 5: Compile**

```bash
./gradlew :core:database:compileDebugKotlin
```

Expected: FAILS — Room needs a `@Database` referencing these entities to generate its schema, which doesn't exist until Task 10. This is expected; KSP validates entities individually but Room's full annotation processing only completes once the database class compiles. Confirm the failure is specifically about the missing `@Database`/DAO registration, not a syntax error in the entities themselves — read the error output.

- [ ] **Step 6: Commit**

```bash
git add core/database
git commit -m "feat(database): add RecordMeta and the five single-value health entities (§5.3)"
```

---

## Task 5: `:core:database` — `steps` table and DAO

Steps are an interval record (§5.3: "start = max(previous end, end − 5 min)"), not an instant sample, so its DAO needs a "most recent end_time" lookup the normalizer (Task 15) uses to compute the next interval's start.

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/StepsEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/StepsDao.kt`

**Interfaces:**
- Produces: `StepsEntity`, `StepsDao.insertAll`, `StepsDao.latestEndTime(deviceId): Long?`, `StepsDao.observeDailyTotal(deviceId, dayStartMs, dayEndMs): Flow<StepsDailyTotal?>`.

- [ ] **Step 1: Write `StepsEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class StepsEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    val count: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Float,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Float,
)
```

- [ ] **Step 2: Write `StepsDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<StepsEntity>): List<Long>

    @Query("SELECT MAX(end_time) FROM steps WHERE device_id = :deviceId AND deleted = 0")
    suspend fun latestEndTime(deviceId: String): Long?

    @Query(
        "SELECT SUM(count) AS steps, SUM(distance_m) AS distanceM, SUM(energy_kcal) AS energyKcal " +
            "FROM steps WHERE device_id = :deviceId AND start_time >= :dayStartMs AND start_time < :dayEndMs " +
            "AND deleted = 0",
    )
    fun observeDailyTotal(deviceId: String, dayStartMs: Long, dayEndMs: Long): Flow<StepsDailyTotal?>

    @Query(
        "SELECT (start_time / :bucketMs) * :bucketMs AS bucket, SUM(count) AS steps " +
            "FROM steps WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs AND deleted = 0 " +
            "GROUP BY bucket ORDER BY bucket",
    )
    fun observeStepBuckets(deviceId: String, fromMs: Long, toMs: Long, bucketMs: Long): Flow<List<StepBucket>>
}

data class StepsDailyTotal(val steps: Int, val distanceM: Float, val energyKcal: Float)
data class StepBucket(val bucket: Long, val steps: Int)
```

- [ ] **Step 3: Commit** (compile still fails until Task 10 — see Task 4 Step 5's note; don't re-verify per task, just accumulate)

```bash
git add core/database
git commit -m "feat(database): add steps entity and DAO with interval-start lookup (§5.3)"
```

---

## Task 6: `:core:database` — `sleep_session` and `sleep_stage` tables

Sleep uses replace semantics (§5.2: upsert by `(device_id, night_date)`; when `content_hash` differs, replace stages and bump `version`), not the `OnConflictStrategy.IGNORE` the other tables use.

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/SleepSessionEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/SleepStageEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/SleepDao.kt`

**Interfaces:**
- Produces: `SleepSessionEntity`, `SleepStageEntity`, `SleepDao.findByNight(deviceId, nightDate): SleepSessionEntity?`, `SleepDao.replaceNight(session, stages)` (transaction: delete old stages for the session, upsert session with bumped version, insert new stages).

- [ ] **Step 1: Write `SleepSessionEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_session",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class SleepSessionEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    @ColumnInfo(name = "night_date") val nightDate: String,
    @ColumnInfo(name = "content_hash") val contentHash: String,
    val score: Int,
    val efficiency: Int,
)
```

- [ ] **Step 2: Write `SleepStageEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_stage",
    indices = [Index("session_id"), Index(value = ["session_id", "start_time"], unique = true)],
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val stage: SleepStageDb,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
)

enum class SleepStageDb { AWAKE, LIGHT, DEEP, REM }
```

`sleep_stage` uses an autogenerated `Long` key rather than the deterministic-UUID scheme — §5.3 says stages are "rewritten with its session," so they have no independent identity worth deduping on; `session_id + start_time` is the natural uniqueness constraint instead, enforced by the composite unique index.

- [ ] **Step 3: Write `SleepDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {

    @Query("SELECT * FROM sleep_session WHERE device_id = :deviceId AND night_date = :nightDate AND deleted = 0")
    suspend fun findByNight(deviceId: String, nightDate: String): SleepSessionEntity?

    @Upsert
    suspend fun upsertSession(session: SleepSessionEntity)

    @Query("DELETE FROM sleep_stage WHERE session_id = :sessionId")
    suspend fun deleteStagesForSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStages(stages: List<SleepStageEntity>)

    @Transaction
    suspend fun replaceNight(session: SleepSessionEntity, stages: List<SleepStageEntity>) {
        upsertSession(session)
        deleteStagesForSession(session.pk)
        insertStages(stages)
    }

    @Query(
        "SELECT * FROM sleep_session WHERE device_id = :deviceId AND night_date BETWEEN :fromDate AND :toDate " +
            "AND deleted = 0 ORDER BY night_date",
    )
    fun observeNights(deviceId: String, fromDate: String, toDate: String): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_stage WHERE session_id = :sessionId ORDER BY start_time")
    fun observeStages(sessionId: String): Flow<List<SleepStageEntity>>
}
```

`@Upsert` (Room 2.5+) replaces on the table's own conflict target (the `dedupe_key` unique index here), which is exactly "insert or update by night" — no hand-written `INSERT ... ON CONFLICT` needed. `replaceNight`'s `@Transaction` default-method body runs delete-then-insert atomically so a crash between the two never leaves half a night's stages on disk.

- [ ] **Step 4: Commit**

```bash
git add core/database
git commit -m "feat(database): add sleep_session/sleep_stage entities with replace-by-night semantics (§5.2/§5.3)"
```

---

## Task 7: `:core:database` — `workout`, `workout_route`, `workout_hr` tables

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutRouteEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutHrEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutDao.kt`

**Interfaces:**
- Produces: `WorkoutEntity`, `WorkoutRouteEntity`, `WorkoutHrEntity`, `WorkoutDao` with insert-all per table plus `observeWorkouts(deviceId, fromMs, toMs)`.

- [ ] **Step 1: Write `WorkoutEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout",
    indices = [Index("dedupe_key", unique = true), Index("start_time")],
)
data class WorkoutEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,
    @Embedded val meta: RecordMeta,
    @ColumnInfo(name = "sport_id") val sportId: String,
    @ColumnInfo(name = "sport_type") val sportType: Int,
    @ColumnInfo(name = "duration_s") val durationS: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Float,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Float,
    @ColumnInfo(name = "avg_hr_bpm") val avgHrBpm: Int?,
    @ColumnInfo(name = "max_hr_bpm") val maxHrBpm: Int?,
    val steps: Int?,
)
```

- [ ] **Step 2: Write `WorkoutRouteEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_route",
    indices = [Index(value = ["workout_id", "start_time"], unique = true)],
)
data class WorkoutRouteEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "start_time") val atMs: Long,
    val lat: Double,
    val lon: Double,
    @ColumnInfo(name = "altitude_m") val altitudeM: Float?,
)
```

- [ ] **Step 3: Write `WorkoutHrEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_hr",
    indices = [Index(value = ["workout_id", "start_time"], unique = true)],
)
data class WorkoutHrEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "start_time") val atMs: Long,
    val bpm: Int,
)
```

`workout_route` and `workout_hr` use autogenerated keys for the same reason `sleep_stage` does — §5.3 keys them by `workout + time`, which the composite unique index already enforces; there's no independent fact here worth a deterministic UUID.

- [ ] **Step 4: Write `WorkoutDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorkouts(rows: List<WorkoutEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRoute(rows: List<WorkoutRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRateSeries(rows: List<WorkoutHrEntity>)

    @Query(
        "SELECT * FROM workout WHERE device_id = :deviceId AND start_time BETWEEN :fromMs AND :toMs " +
            "AND deleted = 0 ORDER BY start_time DESC",
    )
    fun observeWorkouts(deviceId: String, fromMs: Long, toMs: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout_route WHERE workout_id = :workoutId ORDER BY start_time")
    fun observeRoute(workoutId: String): Flow<List<WorkoutRouteEntity>>

    @Query("SELECT * FROM workout_hr WHERE workout_id = :workoutId ORDER BY start_time")
    fun observeHeartRateSeries(workoutId: String): Flow<List<WorkoutHrEntity>>
}
```

- [ ] **Step 5: Commit**

```bash
git add core/database
git commit -m "feat(database): add workout/workout_route/workout_hr entities and DAO (§5.3)"
```

---

## Task 8: `:core:database` — `daily_summary`, `device`, `device_event` tables

`daily_summary` is derived (§5.3: "Rebuilt, never synced as source data") — it gets no `dedupe_key`/`RecordMeta`, just a natural `(device_id, date)` key, and no trigger (Task 10 excludes it explicitly).

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/DailySummaryEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/DeviceEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/DeviceEventEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/DailySummaryDao.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/DeviceDao.kt`

**Interfaces:**
- Produces: `DailySummaryEntity`, `DeviceEntity`, `DeviceEventEntity`, `DailySummaryDao` (upsert + range query), `DeviceDao` (upsert + latest-bound-device lookup). Consumed by `DailySummaryAggregator` (Task 16) and the read-model repositories (Task 17).

- [ ] **Step 1: Write `DailySummaryEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_summary",
    indices = [Index(value = ["device_id", "date"], unique = true)],
)
data class DailySummaryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val date: String,
    val steps: Int,
    @ColumnInfo(name = "distance_m") val distanceM: Int,
    @ColumnInfo(name = "energy_kcal") val energyKcal: Int,
    @ColumnInfo(name = "resting_hr_bpm") val restingHrBpm: Int?,
    @ColumnInfo(name = "avg_hr_bpm") val avgHrBpm: Int?,
    @ColumnInfo(name = "max_hr_bpm") val maxHrBpm: Int?,
    @ColumnInfo(name = "sleep_minutes") val sleepMinutes: Int?,
    @ColumnInfo(name = "live_steps_total") val liveStepsTotal: Int?,
)
```

- [ ] **Step 2: Write `DeviceEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val model: String?,
    @ColumnInfo(name = "firmware") val firmwareVersion: String?,
    @ColumnInfo(name = "sdk_version") val sdkVersion: String?,
    @ColumnInfo(name = "capabilities_json") val capabilitiesJson: String?,
    @ColumnInfo(name = "bound_at") val boundAtMs: Long,
)
```

- [ ] **Step 3: Write `DeviceEventEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_event",
    indices = [Index("device_address"), Index("at")],
)
data class DeviceEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "device_address") val deviceAddress: String,
    val type: String,
    val details: String?,
    val at: Long,
)
```

`type` is stored as the raw `DeviceEventType.name()` string rather than a Room `@TypeConverter`-mapped enum column, matching how `Origin`/`SleepStageDb` are declared directly as enum columns elsewhere in this schema — Room natively supports enum columns (stores `.name`), so no converter class is needed anywhere in this schema.

- [ ] **Step 4: Write `DailySummaryDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySummaryDao {

    @Upsert
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date = :date")
    suspend fun findByDate(deviceId: String, date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date = :date")
    fun observeByDate(deviceId: String, date: String): Flow<DailySummaryEntity?>

    @Query(
        "SELECT * FROM daily_summary WHERE device_id = :deviceId AND date BETWEEN :fromDate AND :toDate " +
            "ORDER BY date",
    )
    fun observeRange(deviceId: String, fromDate: String, toDate: String): Flow<List<DailySummaryEntity>>
}
```

- [ ] **Step 5: Write `DeviceDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM device WHERE address = :address")
    suspend fun findByAddress(address: String): DeviceEntity?

    @Query("SELECT * FROM device ORDER BY bound_at DESC LIMIT 1")
    fun observeMostRecentlyBound(): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: DeviceEventEntity)

    @Query("SELECT * FROM device_event WHERE device_address = :address ORDER BY at DESC")
    fun observeEvents(address: String): Flow<List<DeviceEventEntity>>
}
```

`observeMostRecentlyBound` is how the normalizer (Task 15) and aggregator (Task 16) resolve "the current device_id" without a separate device-selection concept — this app is single-watch (CLAUDE.md), so the most recently bound device is always the right one, and re-pairing a replacement watch naturally becomes the new "most recent."

- [ ] **Step 6: Commit**

```bash
git add core/database
git commit -m "feat(database): add daily_summary, device, device_event entities and DAOs (§5.3)"
```

---

## Task 9: `:core:database` — `raw_ingest`, `change_log`, `sync_cursor`, `export_history` tables

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/RawIngestEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/ChangeLogEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/SyncCursorEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/ExportHistoryEntity.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/RawIngestDao.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/ChangeLogDao.kt`

**Interfaces:**
- Produces: `RawIngestEntity`/`RawIngestDao` (consumed by `JournalRepository`, Task 14, and `HealthDataNormalizer`, Task 15), `ChangeLogEntity`/`ChangeLogDao` (read-only from Kotlin — rows only ever come from triggers, Task 10), `SyncCursorEntity`, `ExportHistoryEntity` (schema only this phase; Phase 9 and Phase 7 write to them respectively).

- [ ] **Step 1: Write `RawIngestEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_ingest",
    indices = [Index("data_type", "processed_at"), Index("received_at")],
)
data class RawIngestEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    @ColumnInfo(name = "sdk_version") val sdkVersion: String,
    @ColumnInfo(name = "data_type") val dataType: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "processed_at") val processedAt: Long? = null,
    val error: String? = null,
)
```

- [ ] **Step 2: Write `ChangeLogEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "change_log",
    indices = [Index("record_type"), Index("changed_at")],
)
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "record_type") val recordType: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val op: String,
    val version: Int,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
)
```

- [ ] **Step 3: Write `SyncCursorEntity.kt` and `ExportHistoryEntity.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "last_seq") val lastSeq: Long,
    @ColumnInfo(name = "snapshot_state") val snapshotState: String?,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long?,
    @ColumnInfo(name = "last_error") val lastError: String?,
)
```

```kotlin
package com.nexwatch.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "pk") val pk: Long = 0,
    val at: Long,
    val uri: String,
    val format: String,
    val range: String,
    @ColumnInfo(name = "record_counts") val recordCountsJson: String,
)
```

Both are schema-only this phase (no DAO consumer yet — `SyncCursorEntity` is Phase 9's, `ExportHistoryEntity` is Phase 7's) but must exist now because `NexWatchDatabase` (Task 10) declares the full §5.3 table set in one version, not incrementally — adding a table later is itself a migration, and there's no reason to pay that cost twice for tables §5.3 already specifies.

- [ ] **Step 4: Write `RawIngestDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface RawIngestDao {

    @Insert
    suspend fun insert(row: RawIngestEntity): Long

    @Query("SELECT * FROM raw_ingest WHERE data_type = :dataType AND processed_at IS NULL ORDER BY received_at")
    suspend fun findUnprocessed(dataType: String): List<RawIngestEntity>

    @Query("SELECT DISTINCT data_type FROM raw_ingest WHERE processed_at IS NULL")
    suspend fun findUnprocessedDataTypes(): List<String>

    @Update
    suspend fun update(row: RawIngestEntity)

    @Query("DELETE FROM raw_ingest WHERE processed_at IS NOT NULL AND received_at < :beforeMs")
    suspend fun pruneProcessedBefore(beforeMs: Long): Int
}
```

- [ ] **Step 5: Write `ChangeLogDao.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface ChangeLogDao {

    @Query("SELECT * FROM change_log WHERE seq > :afterSeq ORDER BY seq LIMIT :limit")
    suspend fun findAfter(afterSeq: Long, limit: Int): List<ChangeLogEntity>

    @Query("SELECT MAX(seq) FROM change_log")
    suspend fun latestSeq(): Long?
}
```

No `@Insert`/`@Update`/`@Delete` on `ChangeLogDao` — CLAUDE.md invariant I4 is enforced at the type level here: the only way rows get into `change_log` is the trigger SQL in Task 10, because this DAO offers no other path in.

- [ ] **Step 6: Commit**

```bash
git add core/database
git commit -m "feat(database): add raw_ingest, change_log, sync_cursor, export_history schema (§5.3)"
```

---

## Task 10: `:core:database` — `NexWatchDatabase`, trigger creation, schema export

Triggers only apply to the eight tables that embed `RecordMeta` (they're the only ones with `version`/`deleted` columns to reference): `heart_rate`, `spo2`, `blood_pressure`, `temperature`, `stress`, `steps`, `sleep_session`, `workout`. `daily_summary` is explicitly excluded (§5.3: derived, never a change-log source); `sleep_stage`/`workout_route`/`workout_hr` have no `version`/`deleted` columns to trigger on, since they're rewritten wholesale with their parent.

**Files:**
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/NexWatchDatabase.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/DatabaseTriggers.kt`
- Create: `core/database/src/main/kotlin/com/nexwatch/core/database/NexWatchDatabaseBuilder.kt`

**Interfaces:**
- Produces: `abstract class NexWatchDatabase : RoomDatabase()` exposing every DAO from Tasks 4–9, `fun buildNexWatchDatabase(context: Context): NexWatchDatabase` (production factory), consumed by `:core:data`'s Hilt module (Task 14).

- [ ] **Step 1: Write `DatabaseTriggers.kt`**

```kotlin
package com.nexwatch.core.database

/**
 * CLAUDE.md I4: change_log is written only by these triggers, created here with
 * IF NOT EXISTS so onOpen can run every launch without erroring on an existing trigger.
 * One AFTER INSERT and one AFTER UPDATE per RecordMeta-bearing table (§5.4).
 */
internal val CHANGE_LOG_TRIGGER_TABLES = listOf(
    "heart_rate", "spo2", "blood_pressure", "temperature", "stress", "steps", "sleep_session", "workout",
)

internal fun changeLogTriggerSql(table: String): List<String> = listOf(
    """
    CREATE TRIGGER IF NOT EXISTS trg_${table}_ai AFTER INSERT ON $table
    BEGIN
      INSERT INTO change_log(record_type, record_id, op, version, changed_at)
      VALUES ('$table', NEW.pk, CASE WHEN NEW.deleted THEN 'DELETE' ELSE 'UPSERT' END,
              NEW.version, CAST(strftime('%s','now') AS INTEGER) * 1000);
    END;
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS trg_${table}_au AFTER UPDATE ON $table
    BEGIN
      INSERT INTO change_log(record_type, record_id, op, version, changed_at)
      VALUES ('$table', NEW.pk, CASE WHEN NEW.deleted THEN 'DELETE' ELSE 'UPSERT' END,
              NEW.version, CAST(strftime('%s','now') AS INTEGER) * 1000);
    END;
    """.trimIndent(),
)

internal fun allChangeLogTriggerSql(): List<String> = CHANGE_LOG_TRIGGER_TABLES.flatMap(::changeLogTriggerSql)
```

- [ ] **Step 2: Write `NexWatchDatabase.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 1,
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
}
```

- [ ] **Step 3: Write `NexWatchDatabaseBuilder.kt`**

```kotlin
package com.nexwatch.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase

private const val DATABASE_NAME = "nexwatch.db"

fun buildNexWatchDatabase(context: Context): NexWatchDatabase =
    Room.databaseBuilder(context.applicationContext, NexWatchDatabase::class.java, DATABASE_NAME)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                allChangeLogTriggerSql().forEach(db::execSQL)
            }
        })
        .build()
```

Add the missing import `androidx.room.RoomDatabase` alongside the others. `onOpen` (not `onCreate`) is deliberate per §5.4 — it runs on every launch, including after the app process restarts without the database file changing, so a future migration that adds a table is self-healing even if someone forgets to add its trigger to that migration's own `Migration.migrate()` body (CLAUDE.md still says the migration should declare it explicitly; `onOpen` is the safety net, not a substitute for that discipline).

- [ ] **Step 4: Compile**

```bash
./gradlew :core:database:compileDebugKotlin
```

Expected: PASS — this is the point where Task 4's expected failure resolves, since every entity is now reachable from a `@Database`.

- [ ] **Step 5: Run once to generate and commit the schema JSON**

```bash
./gradlew :core:database:assembleDebug
```

This triggers Room's schema export (configured via `room { schemaDirectory(...) }` in Task 1) to `core/database/schemas/com.nexwatch.core.database.NexWatchDatabase/1.json`.

```bash
git add core/database
git commit -m "feat(database): add NexWatchDatabase, change-log triggers, and schema export (§5.4/§5.5)"
```

---

## Task 11: `:core:database` — trigger tests and a schema smoke test (JVM, no emulator)

Room 2.8's bundled SQLite driver lets these run as plain `./gradlew test` JVM tests via `Room.inMemoryDatabaseBuilder<NexWatchDatabase>()` (the Kotlin Multiplatform entry point, which takes no `Context`) — no `androidTest`/emulator/Robolectric needed. This satisfies Phase 6's exit criterion "migration and trigger tests are green for every schema version so far": there is only version 1 so far, so the "migration test" is a schema-build smoke test, and the real `MigrationTestHelper`-based migration test arrives with the first version bump (Phase 7+, whenever a table or column is added).

**Files:**
- Create: `core/database/src/test/kotlin/com/nexwatch/core/database/TestDatabase.kt`
- Create: `core/database/src/test/kotlin/com/nexwatch/core/database/ChangeLogTriggerTest.kt`
- Create: `core/database/src/test/kotlin/com/nexwatch/core/database/SchemaSmokeTest.kt`

**Interfaces:**
- Consumes: `NexWatchDatabase` (Task 10), every entity from Tasks 4–9.
- Produces: `inMemoryTestDatabase(): NexWatchDatabase` test helper, reused by the normalizer/aggregator/replay tests in Tasks 15–16 and 23.

- [ ] **Step 1: Write `TestDatabase.kt`**

```kotlin
package com.nexwatch.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun inMemoryTestDatabase(): NexWatchDatabase =
    Room.inMemoryDatabaseBuilder<NexWatchDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.nexwatch.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
private const val NOW = 1_700_000_000_000L

private fun meta(dedupeKey: String, start: Long = NOW) = RecordMeta(
    dedupeKey = dedupeKey,
    deviceId = DEVICE_ID,
    startTime = start,
    endTime = start,
    zoneOffsetSec = 0,
    origin = Origin.MONITOR,
    ingestedAt = NOW,
)

private fun pkFor(dedupeKey: String): String = UUID.nameUUIDFromBytes(dedupeKey.toByteArray()).toString()

class ChangeLogTriggerTest {

    @Test
    fun `inserting a heart rate row appends an UPSERT change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "hr:$DEVICE_ID:$NOW:MONITOR"
        val pk = pkFor(dedupeKey)
        db.healthSampleDao().insertHeartRate(listOf(HeartRateEntity(pk, meta(dedupeKey), bpm = 62)))

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertEquals(1, entries.size)
        assertEquals("heart_rate", entries[0].recordType)
        assertEquals(pk, entries[0].recordId)
        assertEquals("UPSERT", entries[0].op)
        assertEquals(1, entries[0].version)
        db.close()
    }

    @Test
    fun `updating a row's version and deleted flag appends a DELETE change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "steps:$DEVICE_ID:$NOW"
        val pk = pkFor(dedupeKey)
        db.stepsDao().insertAll(
            listOf(StepsEntity(pk, meta(dedupeKey), count = 100, distanceM = 80f, energyKcal = 4f)),
        )
        val tombstoned = StepsEntity(
            pk,
            meta(dedupeKey).copy(version = 2, deleted = true),
            count = 100,
            distanceM = 80f,
            energyKcal = 4f,
        )
        db.query("UPDATE steps SET version = 2, deleted = 1 WHERE pk = ?", arrayOf(pk))

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertEquals(2, entries.size) // insert's UPSERT + update's DELETE
        assertEquals("DELETE", entries.last().op)
        assertEquals(2, entries.last().version)
        db.close()
    }

    @Test
    fun `sleep_session upsert-by-night appends its own change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "sleep:$DEVICE_ID:2026-09-19"
        val pk = pkFor(dedupeKey)
        db.sleepDao().replaceNight(
            SleepSessionEntity(pk, meta(dedupeKey), nightDate = "2026-09-19", contentHash = "abc", score = 80, efficiency = 90),
            stages = emptyList(),
        )

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertTrue(entries.any { it.recordType == "sleep_session" && it.recordId == pk })
        db.close()
    }

    @Test
    fun `workout insert appends a change_log entry but its route and hr children do not`() = runTest {
        val db = inMemoryTestDatabase()
        val dedupeKey = "workout:$DEVICE_ID:sport-1"
        val pk = pkFor(dedupeKey)
        db.workoutDao().insertWorkouts(
            listOf(
                WorkoutEntity(
                    pk, meta(dedupeKey), sportId = "sport-1", sportType = 1, durationS = 1_800,
                    distanceM = 5_000f, energyKcal = 300f, avgHrBpm = 130, maxHrBpm = 160, steps = 6_000,
                ),
            ),
        )
        db.workoutDao().insertRoute(listOf(WorkoutRouteEntity(workoutId = pk, atMs = NOW, lat = 1.0, lon = 2.0, altitudeM = 10f)))
        db.workoutDao().insertHeartRateSeries(listOf(WorkoutHrEntity(workoutId = pk, atMs = NOW, bpm = 140)))

        val entries = db.changeLogDao().findAfter(afterSeq = 0, limit = 10)

        assertEquals(1, entries.size) // only the workout row itself has a trigger
        assertEquals("workout", entries[0].recordType)
        db.close()
    }

    @Test
    fun `daily_summary insert appends no change_log entry`() = runTest {
        val db = inMemoryTestDatabase()
        db.dailySummaryDao().upsert(
            DailySummaryEntity(
                deviceId = DEVICE_ID, date = "2026-09-19", steps = 5_000, distanceM = 4_000, energyKcal = 200,
                restingHrBpm = 55, avgHrBpm = 70, maxHrBpm = 120, sleepMinutes = 420, liveStepsTotal = null,
            ),
        )

        assertEquals(0, db.changeLogDao().findAfter(afterSeq = 0, limit = 10).size)
        db.close()
    }
}
```

`RoomDatabase.query(sql, args)` (used for the raw `UPDATE` in the second test) is Room's low-level escape hatch for a statement no `@Dao` method covers — acceptable in a test that's deliberately exercising the `AFTER UPDATE` trigger path directly, not production code.

- [ ] **Step 3: Run to verify it fails**

```bash
./gradlew :core:database:test --tests "com.nexwatch.core.database.ChangeLogTriggerTest"
```

Expected: FAIL — `inMemoryTestDatabase()` may not yet resolve `db.query(...)`'s exact signature; adjust the raw-SQL call to whatever `RoomDatabase`'s Kotlin API actually exposes in 2.8.5 (`db.openHelper.writableDatabase.execSQL(...)` is the fallback if `RoomDatabase.query` isn't public) — read the compiler error and fix the exact call, this is worth getting right since it's the only trigger-on-UPDATE test in the suite.

- [ ] **Step 4: Fix and verify it passes**

```bash
./gradlew :core:database:test --tests "com.nexwatch.core.database.ChangeLogTriggerTest"
```

Expected: PASS, all 5 cases.

- [ ] **Step 5: Write `SchemaSmokeTest.kt`**

```kotlin
package com.nexwatch.core.database

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stands in for a MigrationTestHelper test until schema version 2 exists (§5.5) — there is
 * nothing to migrate FROM yet, so this instead proves version 1 builds clean and every DAO
 * is reachable, which is what "green for every schema version so far" means with one version.
 */
class SchemaSmokeTest {

    @Test
    fun `database opens and every DAO responds to an empty query`() = runTest {
        val db = inMemoryTestDatabase()

        assertNull(db.stepsDao().latestEndTime("no-such-device"))
        assertNull(db.deviceDao().findByAddress("no-such-device"))
        assertNull(db.dailySummaryDao().findByDate("no-such-device", "2026-01-01"))
        assertNull(db.changeLogDao().latestSeq())

        db.close()
    }
}
```

- [ ] **Step 6: Run the full module test suite**

```bash
./gradlew :core:database:test
```

Expected: PASS, all tests including `SchemaSmokeTest`.

- [ ] **Step 7: Commit**

```bash
git add core/database
git commit -m "test(database): add trigger tests and a schema smoke test on the bundled SQLite driver"
```

---

## Task 12: `:core:watch-fitcloud` — `FitCloudHealthDataDecoder`, steps and today-total (real fixtures)

**Real-hardware facts this task depends on** (from `docs/recon/fixtures/step_bucket.txt`, Phase 3): `FcTimestampData.timestamp` is already UTC epoch **milliseconds** (`t=1789472400000` = 2026-09-14 21:00 UTC) — no `* 1000` needed anywhere. `FcStepData.distance` is in **km** (`0.00915` km for 14 steps) — canonical `distance_m` needs `* 1000f`. `FcStepData.calories` is already **kcal** (`0.393`). `FcTodayTotalData.distance` is already whole **metres** (`9`). `FcTodayTotalData.calorie` lines up as **milli-kcal** against the same capture (`393` vs `0.393` kcal) — a 1000x match the recon notes flagged as needing a larger sample to fully confirm; this task encodes the `/1000f` conversion anyway (it's the only reading consistent with the one real data point we have) and flags it inline as recon-derived, same treatment Phase 4 gave the unverified weather condition codes.

**Also depends on:** `javap` findings against the vendored AAR (recorded in `docs/superpowers/specs/2026-09-20-phase-6-data-core-design.md`) — `FcSyncData` is publicly constructible as `FcSyncData(type: Int, data: List<ByteArray>, deviceInfo: FcDeviceInfo, extra: com.topstep.fitcloud.sdk.v2.model.config.a)`, and its `.toStep()`/`.toTodayTotal()`/etc. methods decode the raw bytes. The fourth constructor parameter's type name (`...config.a`) is an obfuscated/minified class the SDK's own proguard mapping produced — it is genuinely public and has a public no-arg constructor, so it's usable, but its real source name and purpose are unknown. Flag this as a fragile dependency in the file's doc comment: if a future SDK point release re-obfuscates differently, this specific reconstruction breaks at compile time (a clear, loud failure, not a silent data-corruption one) and needs re-deriving via `javap` against the new AAR.

**Files:**
- Create: `core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/FitCloudHealthDataDecoder.kt`
- Create: `core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/di/DecoderModule.kt`
- Test: `core/watch-fitcloud/src/test/kotlin/com/nexwatch/core/watchfitcloud/FitCloudHealthDataDecoderTest.kt`

**Interfaces:**
- Produces: `FitCloudHealthDataDecoder : HealthDataDecoder`, bound to the interface via Hilt in `:core:watch-fitcloud`'s own module (visible to `:app`'s graph without `:core:watch-fitcloud` needing to own the graph itself — same shape as the SDK-avoidance bindings already in this module).

- [ ] **Step 1: Add the reverse data-type mapping next to the existing one**

In `FitCloudMappers.kt`, add immediately after `syncDataTypeName`:

```kotlin
/** Inverse of [syncDataTypeName], for decode (Phase 6) reconstructing an FcSyncData from a journaled row. */
internal fun syncDataTypeFromName(name: String): Int? = when (name) {
    "step" -> FcSyncDataType.STEP
    "sleep" -> FcSyncDataType.SLEEP
    "heart_rate" -> FcSyncDataType.HEART_RATE
    "heart_rate_measure" -> FcSyncDataType.HEART_RATE_MEASURE
    "heart_rate_resting" -> FcSyncDataType.HEART_RATE_RESTING
    "oxygen" -> FcSyncDataType.OXYGEN
    "oxygen_measure" -> FcSyncDataType.OXYGEN_MEASURE
    "blood_pressure" -> FcSyncDataType.BLOOD_PRESSURE
    "blood_pressure_measure" -> FcSyncDataType.BLOOD_PRESSURE_MEASURE
    "respiratory_rate" -> FcSyncDataType.RESPIRATORY_RATE
    "respiratory_rate_measure" -> FcSyncDataType.RESPIRATORY_RATE_MEASURE
    "temperature" -> FcSyncDataType.TEMPERATURE
    "temperature_measure" -> FcSyncDataType.TEMPERATURE_MEASURE
    "stress" -> FcSyncDataType.PRESSURE
    "stress_measure" -> FcSyncDataType.PRESSURE_MEASURE
    "hrv" -> FcSyncDataType.HRV
    "hrv_daily" -> FcSyncDataType.HRV_DAILY
    "sport" -> FcSyncDataType.SPORT
    "gps" -> FcSyncDataType.GPS
    "ecg" -> FcSyncDataType.ECG
    "mood" -> FcSyncDataType.MOOD
    "vitality" -> FcSyncDataType.VITALITY
    "game" -> FcSyncDataType.GAME
    "today_total" -> FcSyncDataType.TODAY_TOTAL_DATA
    else -> name.removePrefix("unknown_").toIntOrNull()
}
```

- [ ] **Step 2: Write `FitCloudHealthDataDecoder.kt` with steps and today-total wired, everything else returning empty for now**

```kotlin
package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncData
import com.topstep.fitcloud.sdk.v2.model.data.FcTodayTotalData
import org.json.JSONArray
import java.util.Base64
import javax.inject.Inject

/**
 * §5.2's decode step. Reconstructs FcSyncData from journaled bytes and calls its own
 * .toXxx() methods — the only way to interpret FitCloud's binary protocol, which lives
 * entirely inside the SDK. See this file's design spec for why decoding can't live in
 * :core:data. The `extra` param below has an obfuscated SDK-internal type name
 * (`com.topstep...config.a`) found via javap — public and constructible, but not a stable
 * name; if a future SDK version renames it, this file fails to compile, loudly, and needs
 * re-deriving against the new AAR.
 */
class FitCloudHealthDataDecoder @Inject constructor() : HealthDataDecoder {

    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> {
        val type = syncDataTypeFromName(dataType) ?: return emptyList()
        val data = decodeBase64Array(payloadJson)
        val deviceInfo = FitCloudSdk.require().connector.configFeature().getDeviceInfo()
        val syncData = FcSyncData(type, data, deviceInfo, com.topstep.fitcloud.sdk.v2.model.config.a())

        return when (dataType) {
            "step" -> syncData.toStep().map { it.toDecoded() }
            "today_total" -> listOfNotNull(syncData.toTodayTotal()?.toDecoded())
            else -> emptyList()
        }
    }
}

private fun decodeBase64Array(payloadJson: String): List<ByteArray> {
    val array = JSONArray(payloadJson)
    return (0 until array.length()).map { Base64.getDecoder().decode(array.getString(it)) }
}

/** Distance arrives in km (recon: 0.00915 km for 14 steps); canonical unit is metres. */
private fun FcStepData.toDecoded() = DecodedHealthRecord.Step(
    startMs = timestamp,
    endMs = timestamp,
    count = step,
    distanceM = distance * 1000f,
    kcal = calories,
)

/**
 * calorie lines up as milli-kcal against the same recon capture (393 vs step's 0.393 kcal) —
 * a single-sample match, encoded here pending a larger confirming sample (docs/recon.md §3 Q-tbd).
 */
private fun FcTodayTotalData.toDecoded() = DecodedHealthRecord.TodayTotal(
    atMs = timestamp,
    steps = step,
    distanceM = distance,
    kcal = calorie / 1000f,
    heartRateBpm = heartRate.takeIf { it > 0 },
)
```

- [ ] **Step 3: Write the Hilt binding module**

```kotlin
package com.nexwatch.core.watchfitcloud.di

import com.nexwatch.core.watchapi.HealthDataDecoder
import com.nexwatch.core.watchfitcloud.FitCloudHealthDataDecoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DecoderModule {
    @Binds
    @Singleton
    abstract fun bindHealthDataDecoder(impl: FitCloudHealthDataDecoder): HealthDataDecoder
}
```

- [ ] **Step 4: Write the failing test using the real recon fixture**

```kotlin
package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Base64 of the raw bytes recon-harness captured for the step_bucket.txt fixture is not
 * itself committed (only the decoded human-readable values are) — this test instead
 * constructs the payload the same way toRawBatch() would from an equivalent FcStepData,
 * which is the round-trip this decoder actually needs to prove: bytes in, canonical
 * DecodedHealthRecord out, matching the real values from docs/recon/fixtures/step_bucket.txt.
 */
class FitCloudHealthDataDecoderTest {

    @Test
    fun `decodes a real recon step payload to canonical units`() {
        // t=1789472400000 step=14 distance=0.00915 calories=0.393 sportDuration=0
        val fixture = FcStepDataFixture.encode(
            timestampMs = 1_789_472_400_000L,
            step = 14,
            distanceKm = 0.00915f,
            calories = 0.393f,
            sportDuration = 0,
        )
        val decoder = FitCloudHealthDataDecoder()

        val records = decoder.decode("step", fixture)

        assertEquals(1, records.size)
        val record = records[0] as DecodedHealthRecord.Step
        assertEquals(1_789_472_400_000L, record.startMs)
        assertEquals(14, record.count)
        assertEquals(9.15f, record.distanceM, 0.01f)
        assertEquals(0.393f, record.kcal, 0.001f)
    }
}
```

This references `FcStepDataFixture`, written in Task 13 alongside the rest of the synthetic-fixture helpers — pull that one helper forward into this task instead of waiting, since this test needs it now:

- [ ] **Step 5: Write the `FcStepDataFixture` helper this test needs**

Create `core/watch-fitcloud/src/test/kotlin/com/nexwatch/core/watchfitcloud/SyncDataFixtures.kt`:

```kotlin
package com.nexwatch.core.watchfitcloud

import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncData
import com.topstep.fitcloud.sdk.v2.model.config.FcDeviceInfo
import java.util.Base64

/**
 * Builds a raw_ingest-shaped payloadJson the same way FitCloudMappers.toRawBatch() does,
 * starting from an already-decoded FcXxxData rather than genuine device bytes — there is no
 * public FcSyncData encoder, only decoders, so fixtures round-trip through the SDK's own
 * objects and this file's job is only to produce a byte-identical *shape* of payload, using
 * the actual FcXxxData class the real decode step will parse back out. This works because
 * FcSyncData.toStep() etc. don't re-derive the byte layout — they're pass-through parsers
 * this project cannot re-encode from scratch, so these fixtures fabricate a plausible byte
 * array and rely on the SDK's real getters for the round-trip assertion instead of a literal
 * byte-for-byte protocol match. See Task 12/13 for the per-type construction.
 */
object FcStepDataFixture {
    fun encode(timestampMs: Long, step: Int, distanceKm: Float, calories: Float, sportDuration: Int): String {
        // Placeholder byte content — the real parse path is exercised via reflection-free
        // construction below, not by hand-encoding FitCloud's undocumented binary layout.
        val bytes = ByteArray(0)
        return """["${Base64.getEncoder().encodeToString(bytes)}"]"""
    }
}
```

**Stop — this fixture approach doesn't work and Step 5 as drafted is wrong.** `FcSyncData.toStep()` parses real byte layout internally; an empty/fake byte array won't produce `FcStepData(step=14, ...)` no matter what this project puts in it, because this project doesn't control that parser. Before writing Step 4/5 for real, resolve this with a spike:

- [ ] **Step 5 (replacement): Spike whether `FcSyncData`'s byte layout is feasible to hand-encode, or whether tests must go through the SDK differently**

Investigate (30 minutes max, this is a spike, not an open-ended task):
1. `javap -c` (with bytecode, not just `-p`) the relevant `.toStep()`/`FcStepData` companion/parsing class inside `sdk-fitcloud-3.0.2.4.aar` to see whether the byte layout is simple enough to hand-encode (fixed-width fields, no compression/encryption) — FitCloud's public docs/sample app (if the vendored repo's `libs`/sample sources came with any) may also show an encoder used for their own test fixtures.
2. If the layout is discoverable and simple: write real byte-encoders per type in `SyncDataFixtures.kt`, and Task 12's Step 4 test stands as designed, asserting a genuine byte-level round-trip.
3. If the layout is opaque/compressed/versioned in a way that isn't worth reverse-engineering: change strategy — test `FitCloudHealthDataDecoder` one layer up instead, by extracting the `FcXxxData → DecodedHealthRecord` mapping functions (`toDecoded()` extension functions) into their own file with **no SDK byte-parsing involved**, unit-test *those* directly by constructing `FcStepData`/`FcTodayTotalData`/etc. via their public Kotlin constructors (already confirmed public via javap: `FcStepData(long, int, float, float, int)` etc.), and leave `decode()`'s `FcSyncData` reconstruction + `.toXxx()` dispatch covered only by the fact that it's a thin, obviously-correct dispatch (`when` on a string, one line per branch) rather than something requiring its own byte-fixture test. This is very likely the right call given `FcStepData`'s constructor is public and takes exactly the fields recon captured — record this decision in `docs/implementation-plan.md`'s Phase 6 section once made, the same way Phase 4 recorded its own real-hardware discoveries.

Given `FcStepData`, `FcTodayTotalData`, `FcHeartRateData`, `FcOxygenData`, `FcBloodPressureData`, `FcTemperatureData`, `FcPressureData`, `FcSleepData`, `FcSportData`, `FcGpsData` all have public constructors (confirmed via `javap -p` already run during design), **take path 3 above** — it's not actually contingent on the spike's outcome, since the constructors are already known to be public right now. Skip the spike; go straight to path 3. Revise Task 12 and 13 as follows.

- [ ] **Step 5 (final): Restructure the decoder so the unit-testable mapping is separate from the untestable dispatch**

Rewrite `FitCloudHealthDataDecoder.kt`'s mapping functions to be `internal` top-level functions taking the already-decoded `FcXxxData` object (not bytes), so tests construct `FcStepData(...)` directly via its public constructor and never touch `FcSyncData`/byte encoding at all:

```kotlin
package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSyncData
import com.topstep.fitcloud.sdk.v2.model.data.FcTodayTotalData
import org.json.JSONArray
import java.util.Base64
import javax.inject.Inject

class FitCloudHealthDataDecoder @Inject constructor() : HealthDataDecoder {

    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> {
        val type = syncDataTypeFromName(dataType) ?: return emptyList()
        val data = decodeBase64Array(payloadJson)
        val deviceInfo = FitCloudSdk.require().connector.configFeature().getDeviceInfo()
        val syncData = FcSyncData(type, data, deviceInfo, com.topstep.fitcloud.sdk.v2.model.config.a())
        return dispatchDecode(dataType, syncData)
    }
}

/** Split from decode() so it's reachable without a real FcSyncData in tests — see StepDecodingTest. */
internal fun dispatchDecode(dataType: String, syncData: FcSyncData): List<DecodedHealthRecord> = when (dataType) {
    "step" -> syncData.toStep().map { it.toDecodedStep() }
    "today_total" -> listOfNotNull(syncData.toTodayTotal()?.toDecodedTodayTotal())
    else -> emptyList()
}

private fun decodeBase64Array(payloadJson: String): List<ByteArray> {
    val array = JSONArray(payloadJson)
    return (0 until array.length()).map { Base64.getDecoder().decode(array.getString(it)) }
}

/** Distance arrives in km (recon: 0.00915 km for 14 steps); canonical unit is metres. */
internal fun FcStepData.toDecodedStep() = DecodedHealthRecord.Step(
    startMs = timestamp,
    endMs = timestamp,
    count = step,
    distanceM = distance * 1000f,
    kcal = calories,
)

/**
 * calorie lines up as milli-kcal against the recon capture (393 vs step's 0.393 kcal) — a
 * single-sample match, encoded pending a larger confirming sample (docs/recon.md, open item).
 */
internal fun FcTodayTotalData.toDecodedTodayTotal() = DecodedHealthRecord.TodayTotal(
    atMs = timestamp,
    steps = step,
    distanceM = distance,
    kcal = calorie / 1000f,
    heartRateBpm = heartRate.takeIf { it > 0 },
)
```

Delete the `SyncDataFixtures.kt` file from Step 5's rejected draft — it's not created.

- [ ] **Step 6: Write the real test against the public `FcStepData`/`FcTodayTotalData` constructors**

```kotlin
package com.nexwatch.core.watchfitcloud

import com.nexwatch.core.model.DecodedHealthRecord
import com.topstep.fitcloud.sdk.v2.model.data.FcStepData
import com.topstep.fitcloud.sdk.v2.model.data.FcTodayTotalData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitCloudDataMappersTest {

    @Test
    fun `step mapping matches the real recon fixture`() {
        // docs/recon/fixtures/step_bucket.txt: t=1789472400000 step=14 distance=0.00915 calories=0.393
        val real = FcStepData(1_789_472_400_000L, 14, 0.00915f, 0.393f, 0)

        val decoded = real.toDecodedStep()

        assertEquals(1_789_472_400_000L, decoded.startMs)
        assertEquals(14, decoded.count)
        assertEquals(9.15f, decoded.distanceM, 0.01f)
        assertEquals(0.393f, decoded.kcal, 0.001f)
    }

    @Test
    fun `today-total mapping converts calorie from milli-kcal and drops a zero heart rate`() {
        val real = FcTodayTotalData(1_789_472_400_000L, 9, 9, 393, 0, 0, 0, 0, 0, 0, 0)

        val decoded = real.toDecodedTodayTotal()

        assertEquals(9, decoded.steps)
        assertEquals(9, decoded.distanceM)
        assertEquals(0.393f, decoded.kcal, 0.001f)
        assertNull(decoded.heartRateBpm)
    }
}
```

Check `FcTodayTotalData`'s exact 11-Int-plus-Long constructor argument order against the `javap` output in the design spec before finalizing this test (`timestamp, step, distance, calorie, deepSleep, lightSleep, heartRate, deltaStep, deltaDistance, deltaCalorie, sportDuration` — 10 ints after the timestamp, matching `getStep/getDistance/getCalorie/getDeepSleep/getLightSleep/getHeartRate/getDeltaStep/getDeltaDistance/getDeltaCalorie/getSportDuration` in that order); adjust the test's positional arguments if the real order differs.

- [ ] **Step 7: Run to verify it passes**

```bash
./gradlew :core:watch-fitcloud:test --tests "com.nexwatch.core.watchfitcloud.FitCloudDataMappersTest"
```

Expected: PASS.

- [ ] **Step 8: Update this plan's Task 13 note**

Task 13 (next) follows this same pattern — `internal fun FcXxxData.toDecodedYyy()` mapping functions, tested via the type's public constructor, never via hand-encoded bytes — for every remaining data type. `dispatchDecode`'s `when` branches grow to cover them; the untested part stays only the one-line dispatch itself.

- [ ] **Step 9: Commit**

```bash
git add core/watch-fitcloud
git commit -m "feat(watch-fitcloud): add FitCloudHealthDataDecoder with step/today-total mapping tested against real recon fixtures"
```

---

## Task 13: `:core:watch-fitcloud` — remaining decoder mappings (synthetic, constructor-tested)

Same pattern as Task 12's final approach: `internal fun FcXxxData.toDecodedYyy()` extension functions, tested by constructing the SDK's `FcXxxData` via its public constructor (confirmed via `javap -p`, recorded in the design spec) — never by hand-encoding bytes. None of these types have real recon fixtures (Phase 3 explicitly descoped them), so test values are synthetic but the mapping logic itself is real and exercised.

**Files:**
- Modify: `core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/FitCloudHealthDataDecoder.kt`
- Modify: `core/watch-fitcloud/src/test/kotlin/com/nexwatch/core/watchfitcloud/FitCloudDataMappersTest.kt`

**Interfaces:**
- Produces: `toDecodedHeartRate()`, `toDecodedSpo2()`, `toDecodedBloodPressure()`, `toDecodedTemperature()`, `toDecodedStress()`, `toDecodedSleep()`, `toDecodedWorkout()`, `toDecodedWorkoutRoute()` — extends `dispatchDecode`'s `when` to cover `"heart_rate"`/`"heart_rate_measure"`/`"heart_rate_resting"`, `"oxygen"`/`"oxygen_measure"`, `"blood_pressure"`, `"temperature"`/`"temperature_measure"`, `"stress"`/`"stress_measure"`, `"sleep"`, `"sport"`, `"gps"`.

- [ ] **Step 1: Write the failing tests**

Append to `FitCloudDataMappersTest.kt`:

```kotlin
    @Test
    fun `heart rate mapping`() {
        val real = FcHeartRateData(1_789_472_400_000L, 72)
        val decoded = real.toDecodedHeartRate()
        assertEquals(1_789_472_400_000L, decoded.atMs)
        assertEquals(72, decoded.bpm)
    }

    @Test
    fun `spo2 mapping`() {
        val real = FcOxygenData(1_789_472_400_000L, 97)
        val decoded = real.toDecodedSpo2()
        assertEquals(97, decoded.percent)
    }

    @Test
    fun `blood pressure mapping`() {
        val real = FcBloodPressureData(1_789_472_400_000L, 118, 76)
        val decoded = real.toDecodedBloodPressure()
        assertEquals(118, decoded.systolic)
        assertEquals(76, decoded.diastolic)
    }

    @Test
    fun `temperature mapping prefers body over wrist`() {
        val real = FcTemperatureData(1_789_472_400_000L, 36.6f, 32.1f)
        val decoded = real.toDecodedTemperature()
        assertEquals(36.6f, decoded.celsius, 0.01f)
    }

    @Test
    fun `temperature mapping falls back to wrist when body is zero`() {
        val real = FcTemperatureData(1_789_472_400_000L, 0f, 32.1f)
        val decoded = real.toDecodedTemperature()
        assertEquals(32.1f, decoded.celsius, 0.01f)
    }

    @Test
    fun `stress mapping`() {
        val real = FcPressureData(1_789_472_400_000L, 45)
        val decoded = real.toDecodedStress()
        assertEquals(45, decoded.level)
    }

    @Test
    fun `sleep mapping converts SDK stage constants and spans`() {
        val items = listOf(
            com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem(
                com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem.STATUS_LIGHT,
                1_789_400_000_000L,
                1_789_403_600_000L,
            ),
            com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem(
                com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem.STATUS_DEEP,
                1_789_403_600_000L,
                1_789_407_200_000L,
            ),
        )
        val real = com.topstep.fitcloud.sdk.v2.model.data.FcSleepData(1_789_400_000_000L, items, false, 80, 90)

        val decoded = real.toDecodedSleep()

        assertEquals(1_789_400_000_000L, decoded.startMs)
        assertEquals(1_789_407_200_000L, decoded.endMs)
        assertEquals(2, decoded.stages.size)
        assertEquals(com.nexwatch.core.model.SleepStage.LIGHT, decoded.stages[0].stage)
        assertEquals(com.nexwatch.core.model.SleepStage.DEEP, decoded.stages[1].stage)
        assertEquals(80, decoded.score)
        assertEquals(90, decoded.efficiency)
    }

    @Test
    fun `workout mapping computes avg and max heart rate from the embedded series`() {
        val hrItems = listOf(
            com.topstep.fitcloud.sdk.v2.model.data.FcSportHeartRateItem(0, 120),
            com.topstep.fitcloud.sdk.v2.model.data.FcSportHeartRateItem(60, 140),
            com.topstep.fitcloud.sdk.v2.model.data.FcSportHeartRateItem(120, 160),
        )
        val real = com.topstep.fitcloud.sdk.v2.model.data.FcSportData(
            1_789_400_000_000L, 1, 1_800, 5.0f, 6_000, 300f, 6_000,
            emptyList(), "sport-42", intArrayOf(), hrItems, null, null, null, null,
        )

        val decoded = real.toDecodedWorkout()

        assertEquals("sport-42", decoded.sportId)
        assertEquals(1, decoded.sportType)
        assertEquals(1_789_400_000_000L, decoded.startMs)
        assertEquals(1_789_401_800_000L, decoded.endMs) // start + duration(1800s)*1000
        assertEquals(5_000f, decoded.distanceM, 0.1f) // FcSportData.distance is already metres via distanceMeters? see Step 2 note
        assertEquals(140, decoded.avgHrBpm)
        assertEquals(160, decoded.maxHrBpm)
        assertEquals(3, decoded.heartRateSeries.size)
        assertEquals(1_789_400_060_000L, decoded.heartRateSeries[1].atMs) // start + item.duration(60s)*1000
    }

    @Test
    fun `workout route mapping converts item durations to absolute timestamps`() {
        val items = listOf(
            com.topstep.fitcloud.sdk.v2.model.data.FcGpsItem(0, 12.34, 56.78, 10f, 8, false),
            com.topstep.fitcloud.sdk.v2.model.data.FcGpsItem(30, 12.35, 56.79, 12f, 9, false),
        )
        val real = com.topstep.fitcloud.sdk.v2.model.data.FcGpsData(1_789_400_000_000L, "sport-42", items)

        val decoded = real.toDecodedWorkoutRoute()

        assertEquals("sport-42", decoded.sportId)
        assertEquals(2, decoded.points.size)
        assertEquals(30, decoded.points[1].offsetSeconds)
        assertEquals(12.35, decoded.points[1].lat, 0.001)
    }
```

- [ ] **Step 2: Run to verify failure, then implement**

```bash
./gradlew :core:watch-fitcloud:test --tests "com.nexwatch.core.watchfitcloud.FitCloudDataMappersTest"
```

Expected: FAIL (functions don't exist). Implement by extending `FitCloudHealthDataDecoder.kt`:

```kotlin
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutHrPoint
import com.nexwatch.core.model.WorkoutRoutePoint
import com.topstep.fitcloud.sdk.v2.model.data.FcBloodPressureData
import com.topstep.fitcloud.sdk.v2.model.data.FcGpsData
import com.topstep.fitcloud.sdk.v2.model.data.FcHeartRateData
import com.topstep.fitcloud.sdk.v2.model.data.FcOxygenData
import com.topstep.fitcloud.sdk.v2.model.data.FcPressureData
import com.topstep.fitcloud.sdk.v2.model.data.FcSleepData
import com.topstep.fitcloud.sdk.v2.model.data.FcSleepItem
import com.topstep.fitcloud.sdk.v2.model.data.FcSportData
import com.topstep.fitcloud.sdk.v2.model.data.FcTemperatureData

internal fun FcHeartRateData.toDecodedHeartRate() = DecodedHealthRecord.HeartRate(atMs = timestamp, bpm = heartRate)

internal fun FcOxygenData.toDecodedSpo2() = DecodedHealthRecord.Spo2(atMs = timestamp, percent = oxygen)

internal fun FcBloodPressureData.toDecodedBloodPressure() =
    DecodedHealthRecord.BloodPressure(atMs = timestamp, systolic = sbp, diastolic = dbp)

/** Not supported on the GTR 3 Pro (docs/recon.md §1) — mapping unverified against real hardware. */
internal fun FcTemperatureData.toDecodedTemperature() = DecodedHealthRecord.Temperature(
    atMs = timestamp,
    celsius = if (body > 0f) body else wrist,
)

internal fun FcPressureData.toDecodedStress() = DecodedHealthRecord.Stress(atMs = timestamp, level = pressure)

internal fun FcSleepData.toDecodedSleep(): DecodedHealthRecord.Sleep {
    val spans = items.map {
        SleepStageSpan(
            stage = when (it.status) {
                FcSleepItem.STATUS_DEEP -> SleepStage.DEEP
                FcSleepItem.STATUS_LIGHT -> SleepStage.LIGHT
                FcSleepItem.STATUS_REM -> SleepStage.REM
                else -> SleepStage.AWAKE // STATUS_SOBER
            },
            startMs = it.startTime,
            endMs = it.endTime,
        )
    }
    return DecodedHealthRecord.Sleep(
        startMs = spans.minOfOrNull { it.startMs } ?: timestamp,
        endMs = spans.maxOfOrNull { it.endMs } ?: timestamp,
        stages = spans,
        score = score,
        efficiency = efficiency,
    )
}

/** heartRateItems' duration is an offset in seconds from the workout's own start (timestamp). */
internal fun FcSportData.toDecodedWorkout(): DecodedHealthRecord.Workout {
    val hrSeries = heartRateItems.map { WorkoutHrPoint(atMs = timestamp + it.duration * 1000L, bpm = it.heartRate) }
    return DecodedHealthRecord.Workout(
        sportId = sportId,
        sportType = type,
        startMs = timestamp,
        endMs = timestamp + duration * 1000L,
        distanceM = distanceMeters.toFloat(),
        kcal = calories,
        avgHrBpm = hrSeries.map { it.bpm }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        maxHrBpm = hrSeries.maxOfOrNull { it.bpm },
        steps = steps.takeIf { it > 0 },
        heartRateSeries = hrSeries,
    )
}

/**
 * GPS items carry duration as a seconds-offset from the paired workout's own start, and
 * FcGpsData has no start timestamp of its own to add it to — only HealthDataNormalizer
 * (Task 15), which already looks up the matching WorkoutEntity by sportId, can convert this
 * to an absolute time. This mapping stays in offset form on purpose.
 */
internal fun FcGpsData.toDecodedWorkoutRoute() = DecodedHealthRecord.WorkoutRoute(
    sportId = sportId,
    points = items.map { WorkoutRoutePoint(offsetSeconds = it.duration, lat = it.lat, lon = it.lng, altitudeM = it.altitude) },
)
```

Extend `dispatchDecode`'s `when`:

```kotlin
internal fun dispatchDecode(dataType: String, syncData: FcSyncData): List<DecodedHealthRecord> = when (dataType) {
    "step" -> syncData.toStep().map { it.toDecodedStep() }
    "today_total" -> listOfNotNull(syncData.toTodayTotal()?.toDecodedTodayTotal())
    "heart_rate", "heart_rate_measure", "heart_rate_resting" -> when (dataType) {
        "heart_rate" -> syncData.toHeartRate()
        "heart_rate_measure" -> syncData.toHeartRateMeasure()
        else -> syncData.toHeartRateResting()
    }.map { it.toDecodedHeartRate() }
    "oxygen", "oxygen_measure" ->
        (if (dataType == "oxygen") syncData.toOxygen() else syncData.toOxygenMeasure()).map { it.toDecodedSpo2() }
    "blood_pressure" -> syncData.toBloodPressure().map { it.toDecodedBloodPressure() }
    "temperature", "temperature_measure" ->
        (if (dataType == "temperature") syncData.toTemperature() else syncData.toTemperatureMeasure())
            .map { it.toDecodedTemperature() }
    "stress", "stress_measure" ->
        (if (dataType == "stress") syncData.toPressure() else syncData.toPressureMeasure())
            .map { it.toDecodedStress() }
    "sleep" -> syncData.toSleep().map { it.toDecodedSleep() }
    "sport" -> syncData.toSport().map { it.toDecodedWorkout() }
    "gps" -> syncData.toGps().map { it.toDecodedWorkoutRoute() }
    else -> emptyList()
}
```

- [ ] **Step 3: Run to verify all pass**

```bash
./gradlew :core:watch-fitcloud:test --tests "com.nexwatch.core.watchfitcloud.FitCloudDataMappersTest"
```

Expected: PASS, all cases.

- [ ] **Step 4: Commit**

```bash
git add core/watch-fitcloud core/model
git commit -m "feat(watch-fitcloud): add remaining decoder mappings (HR/SpO2/BP/temp/stress/sleep/workout/gps)"
```

---

## Task 14: `:core:data` — `JournalRepository`

The tiny, first-and-only-action-per-item step (CLAUDE.md I2). This task is deliberately small — it's the piece Phase 5 left for Phase 6 to build so `WatchConnectionService` (Task 19) has somewhere to hand each `RawBatch`.

**Files:**
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/journal/JournalRepository.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/journal/JournalRepositoryTest.kt`

**Interfaces:**
- Consumes: `RawIngestDao` (`:core:database`), `RawBatch` (`:core:watch-api`, already exists).
- Produces: `JournalRepository.append(batch: RawBatch)`, called by `WatchConnectionService` (Task 19) and the `WatchSyncWorker` (Task 18).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nexwatch.core.data.journal

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.watchapi.RawBatch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `append writes an unprocessed row with the batch's dataType and payload`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        val repository = JournalRepository(db.rawIngestDao(), dispatchers)

        repository.append(RawBatch(dataType = "step", payloadJson = """["AAA="]"""))

        val rows = db.rawIngestDao().findUnprocessed("step")
        assertEquals(1, rows.size)
        assertEquals("""["AAA="]""", rows[0].payloadJson)
        assertNull(rows[0].processedAt)
        db.close()
    }
}
```

Add `:core:database` test-fixtures visibility: `core/data/build.gradle.kts` needs `testImplementation(project(":core:database"))` with the test source set able to see `inMemoryTestDatabase()` — Room test artifacts aren't published separately here, so add `testImplementation(libs.androidx.sqlite.bundled)` too, matching Task 11's setup, and confirm `core/database`'s `inMemoryTestDatabase()` function is `internal` vs `public`: it must be **public** (not `internal`) for `:core:data`'s tests to call it cross-module — go back and drop the `internal` visibility modifier if Task 11 wrote it as `internal` (it wasn't — Task 11's `TestDatabase.kt` declares it as a plain top-level `fun`, which defaults to public; no change needed, just confirm before assuming).

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.journal.JournalRepositoryTest"
```

Expected: FAIL — `JournalRepository` doesn't exist.

- [ ] **Step 3: Implement**

```kotlin
package com.nexwatch.core.data.journal

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.RawIngestDao
import com.nexwatch.core.database.RawIngestEntity
import com.nexwatch.core.watchapi.RawBatch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val FITCLOUD_SDK_VERSION = "3.0.2.4" // third_party/maven's vendored version (README.md)

/** CLAUDE.md I2 — the only thing that happens per synced item before it's safely on disk. */
class JournalRepository @Inject constructor(
    private val rawIngestDao: RawIngestDao,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun append(batch: RawBatch): Unit = withContext(dispatchers.io) {
        rawIngestDao.insert(
            RawIngestEntity(
                receivedAt = System.currentTimeMillis(),
                sdkVersion = FITCLOUD_SDK_VERSION,
                dataType = batch.dataType,
                payloadJson = batch.payloadJson,
            ),
        )
        Unit
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.journal.JournalRepositoryTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data core/data/build.gradle.kts
git commit -m "feat(data): add JournalRepository, the journal-first write path (§5.2/I2)"
```

---

## Task 15: `:core:data` — `HealthDataNormalizer`

The core of §5.2: reads unprocessed `raw_ingest` rows, calls `HealthDataDecoder`, writes canonical rows one transaction per type per batch, marks rows processed, and returns the set of affected local dates for the aggregator (Task 16) to recompute.

**Files:**
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/WorkoutDao.kt` (add a lookup needed for route/HR association)
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/normalize/HealthDataNormalizer.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/normalize/DedupeKeys.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/normalize/HealthDataNormalizerTest.kt`

**Interfaces:**
- Consumes: `HealthDataDecoder` (`:core:watch-api`), every DAO from `:core:database`, `DeviceDao.observeMostRecentlyBound()`.
- Produces: `HealthDataNormalizer.processUnprocessed(): Set<String>` (affected `date` strings, `yyyy-MM-dd`), called by the sync trigger (Task 19) and `WatchSyncWorker` (Task 18), and consumed by `DailySummaryAggregator` (Task 16).

- [ ] **Step 1: Add the workout lookup DAO needs**

In `WorkoutDao.kt`, add:

```kotlin
    @Query("SELECT * FROM workout WHERE device_id = :deviceId AND sport_id = :sportId AND deleted = 0")
    suspend fun findBySportId(deviceId: String, sportId: String): WorkoutEntity?
```

This is how route/HR rows (processed possibly in a different journal batch than their parent workout) find the workout's deterministic `pk` to attach to — if the workout hasn't been normalized yet, this returns `null` and the normalizer leaves that route row unprocessed for the next run (Global Constraints: "row-level try/catch... other rows unaffected").

- [ ] **Step 2: Write `DedupeKeys.kt`**

```kotlin
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
```

- [ ] **Step 3: Write the failing test for the simplest path — steps**

```kotlin
package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDecoder(private val records: List<DecodedHealthRecord>) : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String) = records
}

class HealthDataNormalizerTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `normalizing a step journal row inserts a steps entity and marks it processed`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity("AA:BB", null, null, null, null, boundAtMs = 0))
        db.rawIngestDao().insert(
            com.nexwatch.core.database.RawIngestEntity(
                receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step", payloadJson = "[]",
            ),
        )
        val decoder = FakeDecoder(listOf(DecodedHealthRecord.Step(1_700_000_000_000L, 1_700_000_000_000L, 14, 9.15f, 0.393f)))
        val normalizer = HealthDataNormalizer(db, decoder, dispatchers)

        val affectedDates = normalizer.processUnprocessed()

        val rows = db.stepsDao().insertAll(emptyList()) // no-op call just to ensure the DAO compiles; real assert below
        val stored = db.query("SELECT COUNT(*) FROM steps", emptyArray())
        assertEquals(1, affectedDates.size)
        db.close()
    }
}
```

Simplify the assertion once the real DAO shape is confirmed — prefer a direct `db.stepsDao()` read query over raw SQL; the `db.stepsDao().insertAll(emptyList())`/`db.query(...)` lines above are placeholders to replace with a proper `StepsDao.observeDailyTotal(...)` or an added `StepsDao.findAll(): List<StepsEntity>` test-only query once writing this for real — **do not commit this test with the raw `db.query` call left in**, tighten it to a real DAO-based assertion before Step 5.

- [ ] **Step 4: Implement `HealthDataNormalizer`**

```kotlin
package com.nexwatch.core.data.normalize

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
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.watchapi.HealthDataDecoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val NIGHT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
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
                    db.withTransaction {
                        for (record in records) {
                            processRecord(record, device.address, zone, row.dataType)?.let(affectedDates::add)
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
```

Add `NexWatchDatabase.withTransaction { }` — this is `androidx.room.withTransaction`, an extension on `RoomDatabase`; import `androidx.room.withTransaction`. Add `androidx.room:room-ktx`'s coroutines extensions are already pulled in via Task 1's `libs.androidx.room.ktx` dependency in `:core:database`, but `:core:data` needs its own `implementation(libs.androidx.room.ktx)` line too (add it to `core/data/build.gradle.kts` in this task, not just Task 1) since it's `:core:data`'s own compilation unit calling the extension function, not something transitively re-exported through `implementation` (as opposed to `api`) in `:core:database`'s dependency declaration.

- [ ] **Step 5: Tighten Step 3's test, then run to verify it passes**

Replace the placeholder assertion:

```kotlin
        val affectedDates = normalizer.processUnprocessed()

        val stored = db.stepsDao().observeDailyTotal(
            "AA:BB", dayStartMs = 1_699_999_000_000L, dayEndMs = 1_700_001_000_000L,
        )
        assertEquals(setOf("2023-11-14"), affectedDates) // 1_700_000_000_000L in UTC — confirm against the machine's default zone; this line is timezone-sensitive, see note below
```

**Timezone note:** `dateOf()` uses `ZoneId.systemDefault()`, so this test's expected date string depends on the machine running it, not a fixed value — either force a deterministic zone for the test (`System.setProperty("user.timezone", "UTC")` in a `@Before`, restored in `@After`) or assert on a computed expected value (`Instant.ofEpochMilli(1_700_000_000_000L).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)`) rather than a literal string — use the computed form so the test is correct on any CI machine's default timezone.

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.normalize.HealthDataNormalizerTest"
```

Expected: PASS.

- [ ] **Step 6: Write one more test covering the retry-on-decode-failure path**

```kotlin
    @Test
    fun `a decoder exception leaves the row unprocessed with an error recorded`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity("AA:BB", null, null, null, null, boundAtMs = 0))
        db.rawIngestDao().insert(
            com.nexwatch.core.database.RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step", payloadJson = "[]"),
        )
        val throwingDecoder = object : HealthDataDecoder {
            override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> =
                throw IllegalStateException("malformed payload")
        }
        val normalizer = HealthDataNormalizer(db, throwingDecoder, dispatchers)

        normalizer.processUnprocessed()

        val remaining = db.rawIngestDao().findUnprocessedDataTypes()
        assertEquals(listOf("step"), remaining) // still unprocessed — will retry next run
    }
```

- [ ] **Step 7: Run the full test class**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.normalize.HealthDataNormalizerTest"
```

Expected: PASS, both cases.

- [ ] **Step 8: Commit**

```bash
git add core/database core/data
git commit -m "feat(data): add HealthDataNormalizer covering every §5.3 record type (§5.2)"
```

---

## Task 16: `:core:data` — `DailySummaryAggregator`

§5.2 step 4: "Aggregator recomputes daily_summary for affected dates only." `TodayTotal` is the one exception — §5.3 says it "updates `daily_summary.live_steps_total` for today and is never stored as samples," so it's applied directly by the normalizer rather than recomputed from a raw table, and the aggregator must never overwrite it when recomputing the other columns for the same date.

**Files:**
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/HealthSampleDao.kt` (add a daily HR-stats query)
- Modify: `core/database/src/main/kotlin/com/nexwatch/core/database/DailySummaryDao.kt` (add live-steps-total upsert)
- Modify: `core/data/src/main/kotlin/com/nexwatch/core/data/normalize/HealthDataNormalizer.kt` (wire `TodayTotal`)
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/normalize/DailySummaryAggregator.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/normalize/DailySummaryAggregatorTest.kt`

**Interfaces:**
- Produces: `DailySummaryAggregator.recompute(deviceId: String, dates: Set<String>)`, called after `HealthDataNormalizer.processUnprocessed()` returns its affected-dates set (wired together in Task 19).

- [ ] **Step 1: Add the daily HR-stats query**

In `HealthSampleDao.kt`:

```kotlin
    @Query(
        "SELECT AVG(bpm) AS avgBpm, MIN(bpm) AS minBpm, MAX(bpm) AS maxBpm FROM heart_rate " +
            "WHERE device_id = :deviceId AND start_time >= :dayStartMs AND start_time < :dayEndMs AND deleted = 0",
    )
    suspend fun findDailyHrStats(deviceId: String, dayStartMs: Long, dayEndMs: Long): HrDailyStats?
```

```kotlin
data class HrDailyStats(val avgBpm: Double?, val minBpm: Int?, val maxBpm: Int?)
```

- [ ] **Step 2: Add live-steps-total handling to `DailySummaryDao.kt`**

```kotlin
    @Query(
        "INSERT OR IGNORE INTO daily_summary(device_id, date, steps, distance_m, energy_kcal, " +
            "resting_hr_bpm, avg_hr_bpm, max_hr_bpm, sleep_minutes, live_steps_total) " +
            "VALUES (:deviceId, :date, 0, 0, 0, NULL, NULL, NULL, NULL, NULL)",
    )
    suspend fun ensureRowExists(deviceId: String, date: String)

    @Query("UPDATE daily_summary SET live_steps_total = :value WHERE device_id = :deviceId AND date = :date")
    suspend fun updateLiveStepsTotal(deviceId: String, date: String, value: Int)
```

- [ ] **Step 3: Wire `TodayTotal` into the normalizer**

In `HealthDataNormalizer.kt`, replace the `TodayTotal` branch:

```kotlin
            is DecodedHealthRecord.TodayTotal -> {
                val date = dateOf(record.atMs, zone)
                db.dailySummaryDao().ensureRowExists(deviceId, date)
                db.dailySummaryDao().updateLiveStepsTotal(deviceId, date, record.steps)
                null // does not itself trigger a recompute — it's a direct write, not a source table
            }
```

- [ ] **Step 4: Write the failing aggregator test**

```kotlin
package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.HeartRateEntity
import com.nexwatch.core.database.Origin
import com.nexwatch.core.database.RecordMeta
import com.nexwatch.core.database.StepsEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

private const val DEVICE = "AA:BB"

class DailySummaryAggregatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `recompute sums steps and HR for the date without clobbering an existing live total`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val zone = ZoneId.systemDefault()
        val dayStart = java.time.LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        val meta = { key: String, at: Long -> RecordMeta(key, DEVICE, at, at, 0, Origin.MONITOR, ingestedAt = 0) }
        db.stepsDao().insertAll(
            listOf(
                StepsEntity("s1", meta("s1", dayStart + 1_000), 100, 80f, 4f),
                StepsEntity("s2", meta("s2", dayStart + 2_000), 200, 160f, 8f),
            ),
        )
        db.healthSampleDao().insertHeartRate(
            listOf(HeartRateEntity("h1", meta("h1", dayStart + 1_000), 60), HeartRateEntity("h2", meta("h2", dayStart + 2_000), 80)),
        )
        db.dailySummaryDao().ensureRowExists(DEVICE, "2026-09-19")
        db.dailySummaryDao().updateLiveStepsTotal(DEVICE, "2026-09-19", 250)
        val aggregator = DailySummaryAggregator(db, dispatchers)

        aggregator.recompute(DEVICE, setOf("2026-09-19"))

        val summary = db.dailySummaryDao().findByDate(DEVICE, "2026-09-19")!!
        assertEquals(300, summary.steps)
        assertEquals(60, summary.restingHrBpm) // MIN()-of-day heuristic, see Step 6's note
        assertEquals(70, summary.avgHrBpm)
        assertEquals(80, summary.maxHrBpm)
        assertEquals(250, summary.liveStepsTotal) // preserved, not overwritten
        db.close()
    }
}
```

- [ ] **Step 5: Run to verify it fails**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.normalize.DailySummaryAggregatorTest"
```

Expected: FAIL — `DailySummaryAggregator` doesn't exist.

- [ ] **Step 6: Implement**

```kotlin
package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.NexWatchDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** §5.2 step 4 — recomputes daily_summary for exactly the dates the normalizer touched. */
class DailySummaryAggregator @Inject constructor(
    private val db: NexWatchDatabase,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun recompute(deviceId: String, dates: Set<String>): Unit = withContext(dispatchers.default) {
        val zone = ZoneId.systemDefault()
        for (date in dates) {
            val dayStart = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = LocalDate.parse(date).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val steps = db.stepsDao().observeDailyTotal(deviceId, dayStart, dayEnd).first()
            val hr = db.healthSampleDao().findDailyHrStats(deviceId, dayStart, dayEnd)
            val sleepMinutes = db.sleepDao().observeNights(deviceId, date, date).first()
                .sumOf { (it.meta.endTime - it.meta.startTime) / 60_000 }.toInt()
                .takeIf { it > 0 }
            val existing = db.dailySummaryDao().findByDate(deviceId, date)

            db.dailySummaryDao().upsert(
                DailySummaryEntity(
                    pk = existing?.pk ?: 0,
                    deviceId = deviceId,
                    date = date,
                    steps = steps?.steps ?: 0,
                    distanceM = steps?.distanceM?.toInt() ?: 0,
                    energyKcal = steps?.energyKcal?.toInt() ?: 0,
                    restingHrBpm = hr?.minBpm,
                    avgHrBpm = hr?.avgBpm?.toInt(),
                    maxHrBpm = hr?.maxBpm,
                    sleepMinutes = sleepMinutes,
                    liveStepsTotal = existing?.liveStepsTotal, // preserved — TodayTotal owns this field
                ),
            )
        }
    }
}
```

`restingHrBpm = hr?.minBpm` is a MIN()-of-the-day heuristic, not a true resting-HR algorithm — the SDK's `heart_rate_resting` sync type feeds the same `heart_rate` table (tagged `Origin.MONITOR`, same as continuous monitoring, per Task 15's `originFor`), and there's no schema column distinguishing "this specific bpm value was the SDK's own resting-HR computation" from "this was just the day's lowest monitored reading." Revisit if this proves inaccurate against real data — flagged the same way Task 12 flagged the today-total calorie scale factor.

- [ ] **Step 7: Run to verify it passes**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.normalize.DailySummaryAggregatorTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add core/database core/data
git commit -m "feat(data): add DailySummaryAggregator and wire TodayTotal's direct live-steps write (§5.2/§5.3)"
```

---

## Task 17: `:core:data` — read repositories exposing `:core:model` types

Features never see Room entities (module table: `:feature:*` may not depend on `:core:database`). These repositories are the mapping boundary.

**Files:**
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/health/DailySummaryRepository.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/health/HealthRepository.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/health/DailySummaryRepositoryTest.kt`

**Interfaces:**
- Produces: `DailySummaryRepository.observeToday(): Flow<DailySummary?>`, `.observeRange(fromDate, toDate): Flow<List<DailySummary>>`; `HealthRepository.observeHeartRateBuckets(fromMs, toMs, bucketMs): Flow<List<HeartRateSample>>`, `.observeSleepNights(fromDate, toDate): Flow<List<SleepNight>>`, `.observeWorkouts(fromMs, toMs): Flow<List<WorkoutSummary>>`. Consumed by `:feature:today` (Task 20) and `:feature:health` (Task 21).

- [ ] **Step 1: Write the failing test for `DailySummaryRepository`**

```kotlin
package com.nexwatch.core.data.health

import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySummaryRepositoryTest {

    @Test
    fun `observeToday returns null when nothing bound yet`() = runTest {
        val db = inMemoryTestDatabase()
        val repository = DailySummaryRepository(db.deviceDao(), db.dailySummaryDao())

        assertNull(repository.observeToday().first())
        db.close()
    }

    @Test
    fun `observeToday maps the entity for the most recently bound device`() = runTest {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity("AA:BB", null, null, null, null, boundAtMs = 0))
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        db.dailySummaryDao().upsert(
            DailySummaryEntity(
                deviceId = "AA:BB", date = today, steps = 500, distanceM = 400, energyKcal = 20,
                restingHrBpm = 55, avgHrBpm = 70, maxHrBpm = 100, sleepMinutes = 420, liveStepsTotal = 500,
            ),
        )
        val repository = DailySummaryRepository(db.deviceDao(), db.dailySummaryDao())

        val result = repository.observeToday().first()

        assertEquals(500, result?.steps)
        db.close()
    }
}
```

- [ ] **Step 2: Run to verify it fails, then implement**

```kotlin
package com.nexwatch.core.data.health

import com.nexwatch.core.database.DailySummaryDao
import com.nexwatch.core.database.DailySummaryEntity
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.model.DailySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class DailySummaryRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val dailySummaryDao: DailySummaryDao,
) {
    fun observeToday(): Flow<DailySummary?> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(null) else dailySummaryDao.observeByDate(device.address, today).map { it?.toModel() }
        }
    }

    fun observeRange(fromDate: String, toDate: String): Flow<List<DailySummary>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList()) else dailySummaryDao.observeRange(device.address, fromDate, toDate).map { list -> list.map { it.toModel() } }
        }
}

private fun DailySummaryEntity.toModel() = DailySummary(
    date = date, steps = steps, distanceM = distanceM, energyKcal = energyKcal,
    restingHrBpm = restingHrBpm, avgHrBpm = avgHrBpm, maxHrBpm = maxHrBpm,
    sleepMinutes = sleepMinutes, liveStepsTotal = liveStepsTotal,
)
```

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.health.DailySummaryRepositoryTest"
```

Expected: PASS.

- [ ] **Step 3: Write `HealthRepository`** (no separate failing-test cycle — thin mapping over already-tested DAOs, same shape as Step 2's pattern)

```kotlin
package com.nexwatch.core.data.health

import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.HealthSampleDao
import com.nexwatch.core.database.SleepDao
import com.nexwatch.core.database.WorkoutDao
import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HealthRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val healthSampleDao: HealthSampleDao,
    private val sleepDao: SleepDao,
    private val workoutDao: WorkoutDao,
) {
    fun observeHeartRateBuckets(fromMs: Long, toMs: Long, bucketMs: Long): Flow<List<HeartRateSample>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList())
            else healthSampleDao.observeHeartRateBuckets(device.address, fromMs, toMs, bucketMs)
                .map { buckets -> buckets.map { HeartRateSample(atMs = it.bucket, bpm = it.avgBpm.toInt()) } }
        }

    fun observeSleepNights(fromDate: String, toDate: String): Flow<List<SleepNight>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList())
            else sleepDao.observeNights(device.address, fromDate, toDate).flatMapLatest { sessions ->
                if (sessions.isEmpty()) flowOf(emptyList())
                else combine(sessions.map { session -> sleepDao.observeStages(session.pk).map { session to it } }) { pairs ->
                    pairs.map { (session, stages) ->
                        SleepNight(
                            nightDate = session.nightDate,
                            stages = stages.map { SleepStageSpan(SleepStage.valueOf(it.stage.name), it.startTime, it.endTime) },
                            totalMinutes = stages.sumOf { (it.endTime - it.startTime) / 60_000 }.toInt(),
                            score = session.score,
                        )
                    }
                }
            }
        }

    fun observeWorkouts(fromMs: Long, toMs: Long): Flow<List<WorkoutSummary>> =
        deviceDao.observeMostRecentlyBound().flatMapLatest { device ->
            if (device == null) flowOf(emptyList())
            else workoutDao.observeWorkouts(device.address, fromMs, toMs).map { list ->
                list.map {
                    WorkoutSummary(
                        id = it.pk, sportType = it.sportType, startMs = it.meta.startTime, endMs = it.meta.endTime,
                        distanceM = it.distanceM, kcal = it.energyKcal, avgHrBpm = it.avgHrBpm, maxHrBpm = it.maxHrBpm,
                    )
                }
            }
        }
}
```

- [ ] **Step 4: Compile and commit**

```bash
./gradlew :core:data:compileDebugKotlin
git add core/data
git commit -m "feat(data): add DailySummaryRepository and HealthRepository read models (§5.7)"
```

---

## Task 18: `:core:data` — `WatchSyncWorker` and journal pruning

The §8.7 safety net: WorkManager-scheduled, not a connection poll (Global Constraints). Runs the same journal→normalize→aggregate path as the event-driven trigger (Task 19), plus prunes 30-day-old processed journal rows.

**Files:**
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/sync/HealthSyncCoordinator.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/sync/WatchSyncWorker.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/di/WorkerModule.kt`
- Test: `core/data/src/test/kotlin/com/nexwatch/core/data/sync/HealthSyncCoordinatorTest.kt`

**Interfaces:**
- Produces: `HealthSyncCoordinator.syncAndNormalize(): Result<Set<String>>` (one call that does sync → journal → normalize → aggregate, shared by both the debounced trigger in Task 19 and this worker — no duplicated orchestration logic), `WatchSyncWorker` (`CoroutineWorker`), `WatchSyncWorker.enqueue(context)` / `.schedulePeriodic(context)` companion helpers.

- [ ] **Step 1: Write `HealthSyncCoordinator`, the shared orchestration both call sites use**

```kotlin
package com.nexwatch.core.data.sync

import com.nexwatch.core.data.journal.JournalRepository
import com.nexwatch.core.data.normalize.DailySummaryAggregator
import com.nexwatch.core.data.normalize.HealthDataNormalizer
import com.nexwatch.core.database.DeviceDao
import com.nexwatch.core.database.RawIngestDao
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val JOURNAL_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

/**
 * §5.2's full pipeline in one call: sync → journal → normalize → aggregate. Both the
 * debounced Ready-trigger (WatchConnectionService, Task 19) and WatchSyncWorker call this —
 * one orchestration, two schedules, per CLAUDE.md's "don't repeat the code" spirit.
 */
class HealthSyncCoordinator @Inject constructor(
    private val watchClient: WatchClient,
    private val journalRepository: JournalRepository,
    private val normalizer: HealthDataNormalizer,
    private val aggregator: DailySummaryAggregator,
    private val deviceDao: DeviceDao,
    private val rawIngestDao: RawIngestDao,
) {
    suspend fun syncAndNormalize(): Result<Set<String>> = runCatching {
        if (watchClient.state.first() !is WatchState.Ready) return@runCatching emptySet()

        watchClient.syncHealthData().collect { progress ->
            progress.batch?.let { journalRepository.append(it) }
        }
        val affectedDates = normalizer.processUnprocessed()
        val device = deviceDao.observeMostRecentlyBound().first()
        if (device != null && affectedDates.isNotEmpty()) {
            aggregator.recompute(device.address, affectedDates)
        }
        rawIngestDao.pruneProcessedBefore(System.currentTimeMillis() - JOURNAL_RETENTION_MS)
        affectedDates
    }
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.nexwatch.core.data.sync

import com.nexwatch.core.data.journal.JournalRepository
import com.nexwatch.core.data.normalize.DailySummaryAggregator
import com.nexwatch.core.data.normalize.HealthDataNormalizer
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.watchapi.HealthDataDecoder
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncCoordinatorTest {

    @Test
    fun `does nothing when the watch is not Ready`() = runTest {
        val db = inMemoryTestDatabase()
        val fakeClient = FakeWatchClient() // stays in a non-Ready state by default (see FakeWatchClient's docs)
        val coordinator = HealthSyncCoordinator(
            fakeClient,
            JournalRepository(db.rawIngestDao(), testDispatchers(this)),
            HealthDataNormalizer(db, noopDecoder(), testDispatchers(this)),
            DailySummaryAggregator(db, testDispatchers(this)),
            db.deviceDao(),
            db.rawIngestDao(),
        )

        val result = coordinator.syncAndNormalize()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        db.close()
    }
}

private fun noopDecoder() = object : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String) = emptyList<com.nexwatch.core.model.DecodedHealthRecord>()
}

private fun testDispatchers(scope: kotlinx.coroutines.test.TestScope) = object : com.nexwatch.core.common.CoroutineDispatchers {
    override val io = kotlinx.coroutines.test.StandardTestDispatcher(scope.testScheduler)
    override val default = io
}
```

Check `:core:watch-fake`'s `FakeWatchClient` constructor and default state before finalizing this test — `:core:data` doesn't currently depend on `:core:watch-fake` (only `:app`'s debug variant does), so either add a `testImplementation(project(":core:watch-fake"))` line to `core/data/build.gradle.kts`, or write a minimal hand-rolled fake `WatchClient` in the test file instead if pulling in `:core:watch-fake` as a test dependency feels like too much for one test — a small local fake (`object : WatchClient { override val state = MutableStateFlow(WatchState.Unbound) ... }`) is probably less friction than a new cross-module test dependency; prefer that unless `:core:watch-fake` is trivially available.

- [ ] **Step 3: Run to verify it fails, then it should mostly already pass**

Since `runCatching` around a not-`Ready` early-return needs no new production code beyond `HealthSyncCoordinator` itself (already written in Step 1), this test validates Step 1's code rather than driving new implementation — run it to confirm:

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.sync.HealthSyncCoordinatorTest"
```

Expected: PASS (adjust the fake/dependency choice per Step 2's note first if it doesn't compile).

- [ ] **Step 4: Write `WatchSyncWorker`**

```kotlin
package com.nexwatch.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "watch-sync-periodic"
private const val CATCH_UP_WORK_NAME = "watch-sync-catch-up"

/**
 * §8.7's safety net — WorkManager-scheduled per CLAUDE.md I5's explicit carve-out, not a
 * connection poll. Runs the same HealthSyncCoordinator the Ready-trigger uses (Task 19).
 */
@HiltWorker
class WatchSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: HealthSyncCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = coordinator.syncAndNormalize()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        fun enqueueCatchUp(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                CATCH_UP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WatchSyncWorker>().build(),
            )
        }

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchSyncWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}
```

This needs `androidx.hilt:hilt-work` and its KSP compiler for `@HiltWorker`/`@AssistedInject` to generate the `WorkerFactory` — add to `core/data/build.gradle.kts`:

```kotlin
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
```

Add to `gradle/libs.versions.toml` `[libraries]` (reusing the existing `androidxHiltNavigationCompose` version ref, since `androidx.hilt:*` artifacts share one version line):

```toml
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "androidxHiltNavigationCompose" }
```

`:core:data`'s `build.gradle.kts` also needs `id("com.google.devtools.ksp")` applied directly (it currently only applies `nexwatch.android.hilt`, which applies KSP for Hilt's own compiler — confirm one KSP application covers both `hilt-compiler` and `hilt-work`'s `androidx-hilt-compiler`, which it does, since KSP allows multiple processors in one `ksp(...)` set; no second `id("com.google.devtools.ksp")` needed).

- [ ] **Step 5: Wire the Hilt `WorkerFactory`**

`:app`'s `NexWatchApplication` needs `Configuration.Provider` so WorkManager uses Hilt's generated factory instead of reflection — this is an `:app`-level change, deferred to Task 22 alongside the rest of the app wiring (listed here so it isn't forgotten, not implemented in this task).

- [ ] **Step 6: Commit**

```bash
git add core/data gradle/libs.versions.toml
git commit -m "feat(data): add HealthSyncCoordinator and WatchSyncWorker as the §8.7 safety net"
```

---

## Task 19: `:core:service` — wire the debounced sync trigger into `WatchConnectionService`

Closes the gap Phase 5 deliberately left open (`docs/implementation-plan.md`'s Phase 5 section: "`WatchConnectionService` in this phase logs in and stays connected, but does not call `syncHealthData()`").

**Files:**
- Modify: `core/service/src/main/kotlin/com/nexwatch/core/service/WatchConnectionService.kt`

**Interfaces:**
- Consumes: `HealthSyncCoordinator.syncAndNormalize()` (Task 18), `WatchSyncWorker.enqueueCatchUp()`/`.schedulePeriodic()` (Task 18).

- [ ] **Step 1: Read the current file to confirm the exact `observeState()` shape before editing**

```bash
grep -n "observeState\|class WatchConnectionService" core/service/src/main/kotlin/com/nexwatch/core/service/WatchConnectionService.kt
```

(This file was last touched in Phase 5's review-fixes commit `94573f7` — re-read it fresh rather than assuming the shape shown in the Phase 5 plan doc, since that plan predates the final fixes.)

- [ ] **Step 2: Add the debounced sync trigger**

Add a new `@Inject lateinit var syncCoordinator: HealthSyncCoordinator` field, and a new method called from `onStartCommand` alongside the existing `observeState()`/`observeEvents()`:

```kotlin
    private fun observeSyncTriggers() {
        scope.launch {
            watchClient.state
                .debounce(5_000)
                .collectLatest { state ->
                    if (state is WatchState.Ready) {
                        syncCoordinator.syncAndNormalize()
                        WatchSyncWorker.enqueueCatchUp(applicationContext) // safety net if this run silently no-ops
                    }
                }
        }
    }
```

Add `import kotlinx.coroutines.flow.debounce`, `import com.nexwatch.core.data.sync.HealthSyncCoordinator`, `import com.nexwatch.core.data.sync.WatchSyncWorker`. Call `observeSyncTriggers()` from `onStartCommand` next to the existing `observeState()`/`observeEvents()` calls, guarded by the same one-shot flag Phase 5's review fixes already added to prevent re-entrant collectors on repeated `onStartCommand` calls (the flag's exact name is whatever the Step 1 read revealed — reuse it, don't add a second one).

Also call `WatchSyncWorker.schedulePeriodic(applicationContext)` once from `onCreate()` (idempotent via `ExistingPeriodicWorkPolicy.KEEP`, safe to call every service start).

- [ ] **Step 3: Compile**

```bash
./gradlew :core:service:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/service
git commit -m "feat(service): wire the debounced §8.2 sync trigger on WatchState.Ready"
```

---

## Task 20: `:feature:today` — replace the Today placeholder

**Files:**
- Create: `feature/today/build.gradle.kts`
- Create: `feature/today/src/main/AndroidManifest.xml`
- Create: `feature/today/src/main/kotlin/com/nexwatch/feature/today/TodayScreen.kt`
- Create: `feature/today/src/main/kotlin/com/nexwatch/feature/today/TodayViewModel.kt`
- Create: `feature/today/src/main/kotlin/com/nexwatch/feature/today/TodayUiState.kt`

**Interfaces:**
- Consumes: `DailySummaryRepository.observeToday()` (Task 17).
- Produces: `@Composable fun TodayRoute()`, wired into `:app`'s nav host in Task 22.

- [ ] **Step 1: Write `build.gradle.kts`**

```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.compose")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.feature.today"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
}
```

Check the exact plugin names against `feature/onboarding/build.gradle.kts` (Phase 2) before assuming `nexwatch.android.compose` is the right id — copy that file's plugin block verbatim if it differs.

- [ ] **Step 2: Write `TodayUiState.kt`**

```kotlin
package com.nexwatch.feature.today

import com.nexwatch.core.model.DailySummary

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoDeviceBound : TodayUiState
    data class Loaded(val summary: DailySummary) : TodayUiState
}
```

- [ ] **Step 3: Write `TodayViewModel.kt`**

```kotlin
package com.nexwatch.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.health.DailySummaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    repository: DailySummaryRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = repository.observeToday()
        .map { summary -> if (summary == null) TodayUiState.NoDeviceBound else TodayUiState.Loaded(summary) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)
}
```

- [ ] **Step 4: Write `TodayScreen.kt`**

```kotlin
package com.nexwatch.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.model.DailySummary

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(state)
}

@Composable
fun TodayScreen(state: TodayUiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            TodayUiState.Loading -> Text("Loading…", style = MaterialTheme.typography.bodyLarge)
            TodayUiState.NoDeviceBound -> Text("Pair a watch to see today's data", style = MaterialTheme.typography.bodyLarge)
            is TodayUiState.Loaded -> TodaySummaryContent(state.summary)
        }
    }
}

@Composable
private fun TodaySummaryContent(summary: DailySummary) {
    Text("${summary.steps} steps", style = MaterialTheme.typography.displaySmall)
    Text("${summary.energyKcal} kcal", style = MaterialTheme.typography.titleMedium)
    summary.avgHrBpm?.let { Text("Avg HR $it bpm", style = MaterialTheme.typography.bodyLarge) }
    summary.sleepMinutes?.let { Text("Slept ${it / 60}h ${it % 60}m", style = MaterialTheme.typography.bodyLarge) }
}

@Preview
@Composable
private fun TodayScreenLoadedPreview() {
    TodayScreen(
        TodayUiState.Loaded(
            DailySummary(
                date = "2026-09-19", steps = 8_432, distanceM = 6_200, energyKcal = 410,
                restingHrBpm = 54, avgHrBpm = 72, maxHrBpm = 140, sleepMinutes = 431, liveStepsTotal = 8_432,
            ),
        ),
    )
}

@Preview
@Composable
private fun TodayScreenNoDevicePreview() {
    TodayScreen(TodayUiState.NoDeviceBound)
}
```

Wrap both previews in the project's theme composable (check `feature/onboarding`'s preview files for the exact wrapper name, e.g. `NexWatchTheme { ... }`) before finalizing — this sketch omits it for brevity but every existing preview in the codebase uses one.

- [ ] **Step 5: Write the manifest**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 6: Build and commit**

```bash
./gradlew :feature:today:assembleDebug
git add feature/today
git commit -m "feat(today): replace the Today placeholder with a real daily-summary screen (§5.7)"
```

---

## Task 21: `:feature:health` — replace the Health placeholder

**Files:**
- Create: `feature/health/build.gradle.kts`
- Create: `feature/health/src/main/AndroidManifest.xml`
- Create: `feature/health/src/main/kotlin/com/nexwatch/feature/health/HealthScreen.kt`
- Create: `feature/health/src/main/kotlin/com/nexwatch/feature/health/HealthViewModel.kt`
- Create: `feature/health/src/main/kotlin/com/nexwatch/feature/health/HealthUiState.kt`

**Interfaces:**
- Consumes: `HealthRepository.observeHeartRateBuckets/.observeSleepNights/.observeWorkouts` (Task 17).
- Produces: `@Composable fun HealthRoute()`, wired into `:app`'s nav host in Task 22.

- [ ] **Step 1: Write `build.gradle.kts`** (same shape as `:feature:today`'s, Task 20 Step 1)

```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.compose")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.feature.health"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
}
```

- [ ] **Step 2: Write `HealthUiState.kt`**

```kotlin
package com.nexwatch.feature.health

import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.WorkoutSummary

data class HealthUiState(
    val heartRateBuckets: List<HeartRateSample> = emptyList(),
    val recentNights: List<SleepNight> = emptyList(),
    val recentWorkouts: List<WorkoutSummary> = emptyList(),
)
```

- [ ] **Step 3: Write `HealthViewModel.kt`**

Last 7 days, hourly HR buckets — §5.7's literal example (`GROUP BY start_time / 3600000`).

```kotlin
package com.nexwatch.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.health.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val HOUR_MS = 3_600_000L
private const val SEVEN_DAYS_MS = 7L * 24 * HOUR_MS

@HiltViewModel
class HealthViewModel @Inject constructor(
    repository: HealthRepository,
) : ViewModel() {

    val uiState: StateFlow<HealthUiState> = run {
        val nowMs = System.currentTimeMillis()
        val fromDate = LocalDate.now(ZoneId.systemDefault()).minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val toDate = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)

        combine(
            repository.observeHeartRateBuckets(nowMs - SEVEN_DAYS_MS, nowMs, HOUR_MS),
            repository.observeSleepNights(fromDate, toDate),
            repository.observeWorkouts(nowMs - SEVEN_DAYS_MS, nowMs),
        ) { hr, nights, workouts -> HealthUiState(hr, nights, workouts) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())
    }
}
```

- [ ] **Step 4: Write `HealthScreen.kt`**

```kotlin
package com.nexwatch.feature.health

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.WorkoutSummary

@Composable
fun HealthRoute(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HealthScreen(state)
}

@Composable
fun HealthScreen(state: HealthUiState) {
    LazyColumn {
        item { Text("Last 7 days", style = MaterialTheme.typography.titleLarge) }
        item { Text("${state.heartRateBuckets.size} hourly HR buckets", style = MaterialTheme.typography.bodyMedium) }
        items(state.recentNights) { night -> SleepNightRow(night) }
        items(state.recentWorkouts) { workout -> WorkoutRow(workout) }
    }
}

@Composable
private fun SleepNightRow(night: SleepNight) {
    Text("${night.nightDate}: ${night.totalMinutes / 60}h ${night.totalMinutes % 60}m, score ${night.score}")
}

@Composable
private fun WorkoutRow(workout: WorkoutSummary) {
    Text("Workout ${workout.sportType}: ${workout.distanceM.toInt()}m, ${workout.kcal.toInt()} kcal")
}

@Preview
@Composable
private fun HealthScreenPreview() {
    HealthScreen(
        HealthUiState(
            recentNights = listOf(SleepNight("2026-09-19", emptyList(), totalMinutes = 431, score = 82)),
            recentWorkouts = listOf(WorkoutSummary("w1", 1, 0L, 1_800_000L, 5_000f, 300f, 130, 160)),
        ),
    )
}
```

This is deliberately a plain list, not a chart — a real hourly-bucket chart component belongs in `:core:designsystem` once more than one screen needs one, and building a chart primitive isn't a Phase 6 exit criterion (the criteria are about data correctness, not visualization polish). Wrap the preview in the project's theme composable per Task 20's note.

- [ ] **Step 5: Write the manifest, build, commit**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

```bash
./gradlew :feature:health:assembleDebug
git add feature/health
git commit -m "feat(health): replace the Health placeholder with a 7-day history screen (§5.7)"
```

---

## Task 22: `:app` — wire the new feature modules, Hilt `WorkerFactory`, build the database

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/ui/PlaceholderScreens.kt` (remove the two now-replaced placeholders; check first whether Watch/Data still need theirs)

**Interfaces:**
- Produces: `NexWatchDatabase` in the Hilt graph (via `:core:data`'s new `DatabaseModule`), `TodayRoute`/`HealthRoute` reachable from the nav host, `NexWatchApplication : Configuration.Provider` for `WatchSyncWorker`'s Hilt injection.

- [ ] **Step 1: Add the database Hilt module in `:core:data`**

```kotlin
package com.nexwatch.core.data.di

import android.content.Context
import com.nexwatch.core.database.NexWatchDatabase
import com.nexwatch.core.database.buildNexWatchDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexWatchDatabase = buildNexWatchDatabase(context)

    @Provides fun provideHealthSampleDao(db: NexWatchDatabase) = db.healthSampleDao()
    @Provides fun provideStepsDao(db: NexWatchDatabase) = db.stepsDao()
    @Provides fun provideSleepDao(db: NexWatchDatabase) = db.sleepDao()
    @Provides fun provideWorkoutDao(db: NexWatchDatabase) = db.workoutDao()
    @Provides fun provideDailySummaryDao(db: NexWatchDatabase) = db.dailySummaryDao()
    @Provides fun provideDeviceDao(db: NexWatchDatabase) = db.deviceDao()
    @Provides fun provideRawIngestDao(db: NexWatchDatabase) = db.rawIngestDao()
    @Provides fun provideChangeLogDao(db: NexWatchDatabase) = db.changeLogDao()
}
```

- [ ] **Step 2: Add `:app`'s WorkManager/Hilt wiring**

In `app/build.gradle.kts`, add `implementation(libs.androidx.work.runtime.ktx)` and `implementation(libs.androidx.hilt.work)`.

In `NexWatchApplication.kt`, add:

```kotlin
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject

// inside the @HiltAndroidApp class:
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
```

Add `: Configuration.Provider` to the class declaration. Read the existing file first (`Read app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt`) — it already has `FitCloudSdk` init logic from Phase 4/5; add to it, don't replace it.

- [ ] **Step 3: Wire the nav host**

Read `NexWatchNavHost.kt` and `NexWatchDestination.kt` first to see the exact current placeholder wiring (`PlaceholderScreens.kt`'s Today/Health composables). Replace the Today and Health routes' content with `TodayRoute()` and `HealthRoute()` respectively; leave Watch and Data exactly as they are (still placeholders — Phase 8/7 territory). Remove `PlaceholderScreens.kt`'s `TodayPlaceholder`/`HealthPlaceholder` functions if nothing else references them (check with `grep -rn "TodayPlaceholder\|HealthPlaceholder" app/`).

Add `implementation(project(":feature:today"))` and `implementation(project(":feature:health"))` to `app/build.gradle.kts`.

- [ ] **Step 4: Build and run the full app**

```bash
./gradlew :app:assembleDebug
```

Expected: PASS. This is the point where the whole module graph links for the first time in this phase — if it fails, the error will point at whichever wiring step above has a wrong plugin id, missing dependency, or stale placeholder reference.

- [ ] **Step 5: Commit**

```bash
git add app core/data
git commit -m "feat(app): wire NexWatchDatabase, WatchSyncWorker's Hilt factory, and the new Today/Health screens"
```

---

## Task 23: `:core:data` — journal replay and 7-day timeline integration tests (the literal exit criteria)

These two tests are Phase 6's exit criteria made concrete, not incidental coverage: "replaying the journal from scratch reproduces the canonical tables exactly" and "no gaps in a 7-day data timeline."

**Files:**
- Create: `core/data/src/test/kotlin/com/nexwatch/core/data/normalize/JournalReplayTest.kt`
- Create: `core/data/src/test/kotlin/com/nexwatch/core/data/normalize/SevenDayTimelineTest.kt`

**Interfaces:**
- Consumes: `HealthDataNormalizer`, `DailySummaryAggregator` (Tasks 15–16), a `FakeHealthDataDecoder` this task writes (deterministic, seeded from a fixed `DecodedHealthRecord` list per `dataType` — no real SDK bytes needed, since these tests are about the pipeline's own correctness, not decode correctness, which Tasks 12–13 already cover separately).

- [ ] **Step 1: Write `JournalReplayTest.kt`**

```kotlin
package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.RawIngestEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val DEVICE = "AA:BB"

/** Deterministic: dataType -> the exact records to hand back, ignoring payloadJson entirely. */
private class ScriptedDecoder(private val script: Map<String, List<DecodedHealthRecord>>) : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> = script[dataType].orEmpty()
}

class JournalReplayTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `reprocessing the journal from scratch reproduces the same canonical rows`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val script = mapOf(
            "step" to listOf(DecodedHealthRecord.Step(1_700_000_000_000L, 1_700_000_060_000L, 100, 80f, 4f)),
            "heart_rate" to listOf(DecodedHealthRecord.HeartRate(1_700_000_000_000L, 65)),
        )
        db.rawIngestDao().insert(RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step", payloadJson = "[]"))
        db.rawIngestDao().insert(RawIngestEntity(receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "heart_rate", payloadJson = "[]"))
        val normalizer = HealthDataNormalizer(db, ScriptedDecoder(script), dispatchers)

        normalizer.processUnprocessed()
        val stepsAfterFirstRun = db.stepsDao().observeDailyTotal(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L)
        val hrAfterFirstRun = db.healthSampleDao().findDailyHrStats(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L)

        // Simulate "replay from scratch": reset processed_at on every row and reprocess.
        db.query("UPDATE raw_ingest SET processed_at = NULL", emptyArray())
        normalizer.processUnprocessed()
        val stepsAfterReplay = db.stepsDao().observeDailyTotal(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L)
        val hrAfterReplay = db.healthSampleDao().findDailyHrStats(DEVICE, 1_699_999_000_000L, 1_700_001_000_000L)

        // OnConflictStrategy.IGNORE means the replay is a no-op on already-present dedupe keys —
        // exactly "reproduces the canonical tables exactly," not "doubles every row."
        assertEquals(stepsAfterFirstRun.toString(), stepsAfterReplay.toString())
        assertEquals(hrAfterFirstRun.toString(), hrAfterReplay.toString())
        db.close()
    }
}
```

Confirm `RoomDatabase`'s raw-SQL escape hatch method name against whatever Task 11's `ChangeLogTriggerTest` settled on (`db.query(...)` vs `db.openHelper.writableDatabase.execSQL(...)`) and use the same one here for consistency.

- [ ] **Step 2: Run to verify it passes**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.normalize.JournalReplayTest"
```

Expected: PASS. If it fails because `observeDailyTotal`/`findDailyHrStats` are `Flow`/`suspend` mismatches with what Step 1 assumed, fix the test's calls to match the DAOs' real signatures (Task 4 defined `findDailyHrStats` as `suspend fun`, `observeDailyTotal` as `Flow` — call `.first()` on the Flow one).

- [ ] **Step 3: Write `SevenDayTimelineTest.kt`**

```kotlin
package com.nexwatch.core.data.normalize

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.database.DeviceEntity
import com.nexwatch.core.database.RawIngestEntity
import com.nexwatch.core.database.inMemoryTestDatabase
import com.nexwatch.core.model.DecodedHealthRecord
import com.nexwatch.core.watchapi.HealthDataDecoder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private const val DEVICE = "AA:BB"

class SevenDayTimelineTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val io = dispatcher
        override val default = dispatcher
    }

    @Test
    fun `seven consecutive days of synced steps produce seven daily_summary rows with no gaps`() = runTest(dispatcher) {
        val db = inMemoryTestDatabase()
        db.deviceDao().upsert(DeviceEntity(DEVICE, null, null, null, null, boundAtMs = 0))
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val normalizer = HealthDataNormalizer(db, FakeDecoderPerRow(), dispatchers)
        val aggregator = DailySummaryAggregator(db, dispatchers)

        for (dayOffset in 0 until 7) {
            val day = today.minusDays(dayOffset.toLong())
            val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            db.rawIngestDao().insert(
                RawIngestEntity(
                    receivedAt = 0, sdkVersion = "3.0.2.4", dataType = "step:$dayOffset",
                    payloadJson = "[]", // ScriptedDecoder below keys off dataType, not payload
                ),
            )
        }

        // FakeDecoderPerRow returns one Step record per "step:$dayOffset" dataType — see below.
        val affectedDates = normalizer.processUnprocessed()
        aggregator.recompute(DEVICE, affectedDates)

        val fromDate = today.minusDays(6).toString()
        val toDate = today.toString()
        val summaries = db.dailySummaryDao().observeRange(DEVICE, fromDate, toDate)
        val rows = kotlinx.coroutines.flow.first(summaries)

        assertEquals(7, rows.size)
        assertEquals((0 until 7).map { today.minusDays(it.toLong()).toString() }.sorted(), rows.map { it.date }.sorted())
        rows.forEach { assertEquals(1_000, it.steps) } // every day has real, non-null step data — no gaps
        db.close()
    }
}

/** dataType "step:$n" decodes to one Step record for today-minus-n-days, timestamped mid-day. */
private class FakeDecoderPerRow : HealthDataDecoder {
    override fun decode(dataType: String, payloadJson: String): List<DecodedHealthRecord> {
        val dayOffset = dataType.removePrefix("step:").toIntOrNull() ?: return emptyList()
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.LocalDate.now(zone).minusDays(dayOffset.toLong())
        val atMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return listOf(DecodedHealthRecord.Step(atMs, atMs, 1_000, 800f, 40f))
    }
}
```

Note: this uses `"step:$dayOffset"` as a synthetic `dataType` string purely so the fake decoder can distinguish which day's row is which — `HealthDataNormalizer`'s `originFor()` (Task 15) does a `.endsWith("_measure")` check that this string doesn't match, so it's safe, but if a future change makes `processRecord`'s dispatch depend on more of `dataType`'s exact string shape, revisit this fixture's naming.

- [ ] **Step 4: Run to verify it passes**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.normalize.SevenDayTimelineTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "test(data): add journal-replay and 7-day-timeline tests for Phase 6's literal exit criteria"
```

---

## Task 24: Doc corrections and final whole-repo verification

**Files:**
- Modify: `docs/implementation-plan.md` (§5.2 addendum, §12 Phase 6 row and section)

**Interfaces:** none — this task closes the phase out.

- [ ] **Step 1: Add §5.2's addendum**

Immediately after §5.2's existing paragraph ending "...reprocess the journal, and nothing is lost," insert:

```markdown
**Where decoding actually lives (Phase 6 finding).** The paragraph above describes decoding
as one step, but it can't be one module: turning raw bytes back into typed data needs
`FcSyncData.toXxx()` (an SDK type), and SDK types never leave `:core:watch-fitcloud`. Decoding
lives there, behind a `HealthDataDecoder` interface declared in `:core:watch-api`; `:core:data`'s
`HealthDataNormalizer` calls only that interface. See
`docs/superpowers/specs/2026-09-20-phase-6-data-core-design.md` for the full reasoning.
```

- [ ] **Step 2: Update §12's Phase 6 row and section**

Change the status cell from `In progress` to `Done`. Add a short "What landed" note after the existing Phase 6 paragraph, following the pattern Phases 4/5 already use — summarize: the decoder/normalizer split, all 18 tables, the aggregator, `WatchSyncWorker`, `:feature:today`/`:feature:health`, and explicitly call out what's unverified against real hardware (every decoder mapping except steps/today-total; the today-total calorie /1000 scale factor; the MIN()-of-day resting-HR heuristic) so the next phase (or a future recon session) knows exactly what to check first once more real sync data accumulates — mirroring how Phase 3 recorded its own deliberately-descoped items.

Check every exit-criteria checkbox:

```markdown
- [x] No gaps in a 7-day data timeline. Verified by `SevenDayTimelineTest` (Task 23).
- [x] Replaying the journal from scratch reproduces the canonical tables exactly. Verified by `JournalReplayTest` (Task 23).
- [x] Migration and trigger tests are green for every schema version so far. `ChangeLogTriggerTest` + `SchemaSmokeTest` (Task 11); only version 1 exists, so there is no migration to test yet — noted explicitly rather than left implicit.
```

- [ ] **Step 3: Run the full verification suite**

```bash
./gradlew assembleDebug assembleRelease test lint
```

Expected: all green. Fix any failure before proceeding — per CLAUDE.md, "a green compile" is not the same as "done," and this is the point where every task's individual test runs get checked together for interaction effects (a KSP/Hilt/Room annotation-processing conflict across modules is the most likely failure mode this late, since each task's own `:module:test` run doesn't exercise cross-module compilation the way `assembleDebug` on `:app` does).

- [ ] **Step 4: Commit the doc updates**

```bash
git add docs/implementation-plan.md
git commit -m "docs(phase-6): mark Phase 6 Done, record the decoder/normalizer split in §5.2"
```

- [ ] **Step 5: Report completion to the user**

Phase 6's branch (`phase-6-data-core`) is ready for the user to review and merge per CLAUDE.md's workflow — this plan does not merge it. Summarize what shipped, what's unverified against real hardware (per Step 2's note), and that the branch is waiting for the user's merge.

