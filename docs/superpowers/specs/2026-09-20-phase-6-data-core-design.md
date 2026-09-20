# Phase 6 — Data core (M3): design spec

Branch: `phase-6-data-core`. Implements `docs/implementation-plan.md` §12 Phase 6 against §5 (Data layer), §7.4 (change-log compaction, read-only in this phase), §9 (battery/perf), §11 (testing strategy).

## Scope

Build the journal (`raw_ingest`), a decoder/normalizer pipeline for every §5.3 table, the full Room schema with trigger-only `change_log`, the `daily_summary` aggregator, `WatchSyncWorker` as an event-driven-first/WorkManager-backup safety net, and real UI for the Today and Health tabs backed by SQL-bucketed queries. Wires `WatchConnectionService` (Phase 5) to actually call `syncHealthData()` now that there's a journal to receive it — closing the gap Phase 5 deliberately left open.

Out of scope: `SyncEngine`/`SyncProvider` (Phase 9), export/import (Phase 7), watch-control settings (Phase 8), change-log compaction logic (§7.4 — the log is written this phase, compacted once a sync provider exists to set the low-water mark).

## 1. The decoder/normalizer split (the key architectural finding)

§5.2 describes "the normaliser" as one step: SDK shape → canonical records. In practice it can't be one class in one module, because of a real SDK constraint discovered via `javap` against the vendored AAR:

- `FcSyncData` (SDK type) is the only thing that can turn journaled raw bytes back into typed data — it exposes `toStep()`, `toHeartRate()`, `toSleep()`, `toSport()`, `toGps()`, `toTodayTotal()`, etc.
- `FcSyncData` is publicly constructible from `(type: Int, data: List<ByteArray>, deviceInfo: FcDeviceInfo, wrapper: <public 1-int-field class>)` — everything but `deviceInfo` is already in `raw_ingest`'s `data_type`/`payload_json`; `deviceInfo` is read live off the connected device at decode time (it changes rarely, and every canonical row already carries its own firmware/SDK provenance per §5.1, so a decode-time snapshot is accurate enough).
- SDK types never leave `:core:watch-fitcloud` (CLAUDE.md module table), and `:core:data` (§5.2's implied home for "the normaliser") may not depend on it.

**Resolution**, following the same interface-inversion already used for `WatchUserIdProvider` (Phase 4) and `NotificationForwardingSettingsProvider` (Phase 5):

```kotlin
// :core:model (pure Kotlin) — one DTO per §5.3 table, no SDK/Android types
sealed interface DecodedHealthRecord {
    data class Step(val startMs: Long, val endMs: Long, val count: Int, val distanceM: Float, val kcal: Float) : DecodedHealthRecord
    data class HeartRate(val atMs: Long, val bpm: Int) : DecodedHealthRecord
    data class Spo2(val atMs: Long, val percent: Int) : DecodedHealthRecord
    data class BloodPressure(val atMs: Long, val systolic: Int, val diastolic: Int) : DecodedHealthRecord
    data class Temperature(val atMs: Long, val celsius: Float) : DecodedHealthRecord
    data class Stress(val atMs: Long, val level: Int) : DecodedHealthRecord
    data class Sleep(val nightDate: String, val stages: List<SleepStageSpan>) : DecodedHealthRecord
    data class Workout(val sportId: Long, val sportType: Int, val startMs: Long, val endMs: Long, val distanceM: Float, val kcal: Float, val avgHr: Int?, val maxHr: Int?, val steps: Int?) : DecodedHealthRecord
    data class WorkoutRoute(val sportId: Long, val atMs: Long, val lat: Double, val lon: Double, val altitudeM: Float?) : DecodedHealthRecord
    data class WorkoutHr(val sportId: Long, val atMs: Long, val bpm: Int) : DecodedHealthRecord
    data class TodayTotal(val atMs: Long, val steps: Int, val distanceM: Int, val kcal: Int, val liveHeartRate: Int?) : DecodedHealthRecord
}
data class SleepStageSpan(val stage: SleepStage, val startMs: Long, val endMs: Long)
enum class SleepStage { AWAKE, LIGHT, DEEP, REM }

// :core:watch-api (pure Kotlin) — the boundary interface
interface HealthDataDecoder {
    fun decode(dataType: String, payloadBase64Json: String): List<DecodedHealthRecord>
}
```

`FitCloudHealthDataDecoder` (`:core:watch-fitcloud`) implements it: base64-decode each string back to `ByteArray`, reconstruct `FcSyncData(type, data, connector's current FcDeviceInfo, wrapper())`, dispatch on `dataType` to the matching `.toXxx()`, map SDK objects field-by-field into the `:core:model` DTOs above. Bound via a Hilt module in `:core:watch-fitcloud` itself (same pattern as existing bindings there — Hilt's graph is built in `:app`, so the binding just needs to be visible to it, not owned by it).

`:core:data`'s normalizer depends only on `HealthDataDecoder` (the interface) plus `:core:database`'s DAOs — it never sees an SDK type. This keeps invariant I2 (journal-first, raw bytes untouched at ingest) exactly as Phase 4/5 already built it; decoding is a read-time concern, not an ingest-time one.

**Follow-up correction**: §5.2's text ("Normalizer: SDK shape → canonical records") gets a one-line addendum in the same change this phase lands, noting the decoder lives in `:core:watch-fitcloud` and the normalizer in `:core:data` consumes it through `HealthDataDecoder`.

## 2. Module changes

- **`:core:model`** (pure Kotlin, currently empty): `DecodedHealthRecord` and friends (above), plus UI-facing read models used by `:core:data`'s repositories to hand data to features without leaking Room entities: `DailySummary`, `HeartRateSample`, `SleepNight`, `WorkoutSummary`, `Device`, `DeviceEvent`.
- **`:core:watch-api`**: `HealthDataDecoder` interface, `DecodedHealthRecord`'s home stays `:core:model` (watch-api already depends on it).
- **`:core:watch-fitcloud`**: `FitCloudHealthDataDecoder`, its Hilt binding module, unit tests using the recon fixtures (steps, today-total) plus hand-built synthetic byte fixtures for the other types (see §5 below).
- **`:core:database`** (currently empty — this phase builds it from scratch): Room entities for every §5.3 table (`RecordMeta` embedded per health table), DAOs, `NexWatchDatabase` with `exportSchema = true`, schema JSON committed, `RoomDatabase.Callback.onOpen` creating all triggers via `CREATE TRIGGER IF NOT EXISTS`, `MigrationTestHelper` scaffolding for future versions (only version 1 exists now — no migration to test yet, but the test harness is wired so version 2 isn't the first time it's touched).
- **`:core:data`**: `JournalRepository` (writes `RawBatch` → `raw_ingest`, the one thing the sync subscriber calls), `HealthDataNormalizer` (reads unprocessed journal rows, calls `HealthDataDecoder`, bulk-inserts per type in one transaction, marks `processed_at`, handles sleep's upsert-by-night-date/content-hash/version-bump rule), `DailySummaryAggregator` (recomputes affected dates only), repositories exposing `:core:model` read types to features (`HealthRepository`, `DailySummaryRepository`), `WatchSyncWorker` (WorkManager `CoroutineWorker`, periodic safety net — see §3).
- **`:core:service`**: `WatchConnectionService` gains the debounced `syncHealthData()` trigger on `Ready` (§8.2, explicitly descoped by Phase 5) — collects `SyncProgress`, hands each `RawBatch` to `JournalRepository`, and enqueues `WatchSyncWorker` once on `Ready` rather than relying on the periodic schedule alone.
- **`:feature:today`** (new module, replaces the `:app` placeholder): current-day `daily_summary` card, live step/HR/sleep-last-night tiles.
- **`:feature:health`** (new module, replaces the `:app` placeholder): history — daily/weekly bucketed charts per metric (steps, HR, sleep, SpO2 where supported), sourced from pre-bucketed SQL queries, Paging 3 for any list view.
- **`:app`**: swap the two placeholder routes for the new feature modules' entry composables; no other navigation changes.

## 3. Ingestion → normalization → aggregation flow

```
WatchConnectionService, on WatchState.Ready (debounced ~5s so a flappy
connection doesn't fire repeatedly):
   watchClient.syncHealthData().collect { progress ->
       progress.batch?.let { journalRepository.append(it) }   // (1) tiny, first action, per CLAUDE.md I2
   }
   on completion: normalizer.processUnprocessed()              // (2)

HealthDataNormalizer.processUnprocessed():
   for each data_type with unprocessed raw_ingest rows, in one transaction per type per batch:
     decoded = healthDataDecoder.decode(row.dataType, row.payloadJson)
     bulk insert into the matching table, OnConflictStrategy.IGNORE on dedupe_key
       (sleep: upsert by (device_id, night_date); if content_hash differs,
        replace stages and increment version)
     mark row.processed_at = now
   → triggers fire automatically inside the same transaction (§5.4), writing change_log
   → aggregator.recompute(affected dates)                      // (3), only touched dates

WatchSyncWorker (WorkManager periodic, e.g. every 6h, and enqueued once
immediately after any Ready-triggered sync completes):
   if watch is Ready: call the same syncHealthData() → journal → normalize path.
   This is the §8.7 safety net for missed event-driven triggers (app killed
   mid-sync, Ready fired before the debounce collector was listening, etc.)
   — not a poll of the watch's *connection*, just a scheduled catch-up sync,
   which is the WorkManager exception CLAUDE.md's "nothing polls" carves out.
```

Journal rows are pruned after 30 days (`processed_at IS NOT NULL AND received_at < now - 30d`), run from the same worker.

## 4. Schema and triggers

Exactly per §5.3/§5.4: every health table embeds `RecordMeta`, has a unique index on `dedupe_key` and an index on `start_time`; `change_log`, `raw_ingest`, `sync_cursor`, `export_history`, `device`, `device_event` as their own tables. One `AFTER INSERT` and one `AFTER UPDATE` trigger per health table, created in `onOpen` (idempotent `IF NOT EXISTS`, so this also self-heals if a future migration adds a table without remembering its trigger — though the migration should still declare it explicitly per §5.5). `daily_summary` gets no triggers — it's derived, never itself a change-log source.

## 5. Fixture and test strategy (mapped to Phase 6's exit criteria)

- **Real fixtures** (from `docs/recon/fixtures/`, Phase 3): steps, today-total. Decoder and normalizer tests for these assert exact output against real bytes.
- **Synthetic fixtures** (hand-built, shaped from the SDK's documented DTOs and the `FcSyncData.toXxx()` signatures found via `javap`): heart rate, SpO2, blood pressure, temperature, stress, sleep, workout/GPS/workout-HR. Each gets a small byte-level fixture constructed the same way `FcSyncData` would encode it, so the decoder test round-trips through real SDK decode logic even though the bytes aren't watch-captured. Labeled in test comments as synthetic, same spirit as Phase 3's "deliberately descoped" fixtures — correctness against real hardware gets verified opportunistically once real payloads accumulate (naturally, during Phase 6+ real usage, per Phase 3's own note that this was expected to happen here).
- **Journal replay** (exit criterion 2): a test that seeds `raw_ingest` with a mix of processed/unprocessed rows across all types, wipes the canonical tables, reruns `HealthDataNormalizer.processUnprocessed()` (with `processed_at` reset), and asserts the canonical tables match a pre-recorded snapshot exactly — this is the literal exit criterion, not just a unit test nicety.
- **7-day timeline** (exit criterion 1): an integration-style test that journals a synthetic week of steps/HR/sleep with realistic gaps and dedupe collisions, runs the full pipeline, and asserts `daily_summary` has one row per day with no null/missing dates.
- **Migration/trigger tests** (exit criterion 3): `MigrationTestHelper` for schema v1 (nothing to migrate from yet, but the harness must exist and pass), plus one trigger test per health table asserting insert/update produces the right `change_log` row.

## 6. Error handling

- Decoder failures (malformed bytes, unknown sub-type) are caught per-row inside `processUnprocessed()`: the row's `error` column is set, `processed_at` stays null so it's retried next run, and the batch transaction for *other* rows of the same type is unaffected (row-level try/catch inside the loop, not a batch-level one).
- A normalizer crash mid-batch rolls back that type's transaction only (Room's per-call transaction), leaving those rows unprocessed for the next run — nothing is lost, matching §5.2's "fix the code and reprocess the journal" philosophy.
- `WatchSyncWorker` uses `Result.retry()` with WorkManager's backoff on transient failures (watch not `Ready`, sync timeout) and `Result.failure()` only for permanent ones (decoder throwing on a payload shape decoding can't recover from — logged, journal row kept for manual inspection, never silently dropped).

## 7. What's explicitly not solved here

- `change_log` compaction (§7.4) — the log accumulates unbounded until Phase 9 introduces a provider cursor to compact against. Acceptable at this data volume (§5.6: a few MB/year).
- Any UI beyond Today/Health tabs (Watch tab settings are Phase 8; Data tab export/import is Phase 7).
- §9.1 battery/memory measurement isn't a Phase 6 exit criterion, but the sync/normalization/aggregation path this phase adds is exactly what Phase 5's deferred 48h soak (now folded into Phase 10) will be measuring — nothing new to do here, just noted so it isn't forgotten when that soak runs.
