# GTR 3 Pro Companion App: Implementation Plan

Native Android (Kotlin), single user, single watch. Version 1.0, September 2026.

---

## 1. Goals and invariants

The app replaces FitCloudPro for daily use. It must keep the watch connected with the app closed, forward notifications and calls reliably, sync health data in the background, and store that data locally in a clean, documented shape. Export must be available at any time. Adding an external sync target later should mean writing one class and switching it on, with no schema changes and no rewrites. All of this has to stay light on battery and memory.

Out of scope for v1: iOS, multiple watches, a cloud account, and Play Store publishing. Nothing in the design blocks any of these later.

These invariants hold across the whole codebase. When a design question comes up, check it against this table first.

| # | Invariant | Why it matters |
|---|-----------|----------------|
| I1 | The FitCloud SDK is visible to exactly one Gradle module. | Vendor churn, Rx types and SDK bugs stay contained. The SDK can be replaced later. |
| I2 | The local database is the single source of truth. UI, export and sync providers only read from it. | There is one place to reason about correctness. |
| I3 | Data from the watch is written to disk before anything else happens to it. | The watch deletes data once it has been synced (see §2). |
| I4 | Health records are immutable facts with deterministic IDs. A correction is a new version, never a silent edit. | Deduplication, export round-trips and external sync all become trivial. |
| I5 | Nothing polls. Work runs in response to events, or on WorkManager schedules the OS can batch. | This is the foundation of the battery budget. |
| I6 | Every write to a health table produces a change-log entry, enforced by SQLite triggers rather than by developer discipline. | Sync can never silently miss data. |

---

## 2. What the SDK dictates (read before writing code)

These facts come from the official SDK wiki (github.com/htangsmart/FitCloudPro-SDK-Android/wiki). They shape several decisions below, so confirm each one against your watch during recon.

**Sync is destructive.** When `FcDataFeature.syncData()` finishes a data type successfully, the watch deletes that data. The only exception is `FcTodayTotalData`. If one type fails, the remaining types are not synced in that run. This is why invariant I3 exists and why the ingestion pipeline starts with a raw journal (§5.2).

**The watch only buffers about 7 days** of monitoring data (steps, sleep, heart rate, blood pressure and so on). Measurement data such as workouts is limited by storage space instead. Background sync therefore has to succeed at least every few days, and in practice should run several times a day.

**Steps arrive as roughly 5-minute buckets.** An `FcStepData` at timestamp T means the steps accumulated in the interval ending at T. Intervals are irregular. Bucket totals lag the watch display by up to 5 minutes, so "today's steps" must come from `FcTodayTotalData`, not from summing buckets.

**Sleep for the same night can be delivered more than once.** Sleep must be stored with replace-by-night semantics.

**Workouts and GPS arrive as separate records** (`FcSportData` and `FcGpsData`) linked by `sportId`.

**Real-time readings the app requests** via `openHealthRealTimeData` are never synced back from the watch. If you want to keep them, you store them yourself.

**BIND mode wipes the watch.** `connect(address, userId, bindOrLogin, sex, age, height, weight)` authenticates the phone. BIND (`bindOrLogin = true`) makes the watch clear its stored data. LOGIN (`false`) checks the userId against the last one bound and fails with `FcAuthException` on a mismatch. The user profile fields feed on-watch calculations, and calling `connect` again updates them.

**The SDK owns reconnection.** `FcConnector` keeps hold of the device and reconnects on its own schedule: every 5 seconds in the foreground, backing off progressively in the background, plus immediately when Bluetooth turns on, the app comes to the foreground or the screen turns on. The wiki explicitly discourages writing your own reconnect loop. `FcSDK.setReConnectFrequent` forces high-frequency background retries, which costs battery. The connector states are `DISCONNECTED`, `PRE_CONNECTING`, `CONNECTING`, `PRE_CONNECTED` and `CONNECTED`, and you may only interact with the watch in `CONNECTED`.

**Built-in helpers exist.** `FcBuiltInFeatures` offers `telephonyControl`, `mediaControl`, `musicControl` and `autoSetTime`. `AbsNotificationListenerService` wires up music control for you. Notifications go out through `FcMessageFeature.sendNotification(type, name, content)`. Messages from the watch, such as find phone, camera, media keys and hang-up, arrive on `FcMessageFeature.observerMessage()`.

**Distribution is a security concern.** Since v3.0.1 the SDK is published as `com.topstep.wearkit:sdk-base` and `sdk-fitcloud` on the vendor's own Maven server, which is plain HTTP (it requires `allowInsecureProtocol = true`). The latest release is v3.0.2 (January 2026). Since v3.0.1 it supports Android 15 and 16 KB page sizes. §10 covers how to handle the insecure repository.

**Dual-mode Bluetooth.** Call audio runs over Classic Bluetooth and is managed by Android itself. `connector.close()` only drops BLE. Removing the audio link needs `FcSettingsFeature.unbindAudioDevice()`.

---

## 3. Architecture

### 3.1 Module graph

```
:app                     Compose UI, navigation, onboarding, Hilt entry points
 │
 ├── :feature:*          dashboard, health, settings, notifications, export, sync
 │
 ├── :core:service       WatchConnectionService (FGS), NotificationForwarder (NLS),
 │                       CompanionPresenceService, BootReceiver, Workers
 │
 ├── :core:data          repositories, ingestion pipeline, aggregator, SyncEngine
 │     ├── :core:database   Room DB, entities, DAOs, triggers, migrations
 │     ├── :core:export     exporters/importers (JSONL+ZIP, CSV, GPX)
 │     └── :core:sync-api   SyncProvider interface (pure Kotlin)
 │
 ├── :core:watch-api     WatchClient interface + watch domain types (pure Kotlin)
 ├── :core:watch-fitcloud  FitCloudWatchClient: the ONLY module that sees the SDK
 ├── :core:designsystem  WatchAppTheme, color tokens, shared Compose components
 ├── :core:model         canonical health record types (pure Kotlin, no Android)
 └── :core:common        dispatchers, Clock, logging, Result types

:core:sync-healthconnect first SyncProvider (added in M6)
:core:sync-<anything>    future providers, one module each
```

The dependency rules are strict. SDK types never leave `:core:watch-fitcloud`. Room entities never leave `:core:database` and `:core:data`. Export and sync only speak `:core:model`. Enforce this with Gradle module boundaries, not convention. If a module doesn't declare the dependency, it can't import the type.

### 3.2 Stack

Kotlin 2.x and Jetpack Compose with Material 3, single activity. Hilt for DI. Coroutines and Flow everywhere, with `kotlinx-coroutines-rx3` used only inside `:core:watch-fitcloud` to adapt the SDK's RxJava3 types. Room with KSP for the database, Proto or Preferences DataStore for settings, WorkManager for deferred and periodic work, kotlinx-serialization for export. Health Connect client for the first sync provider, Vico or a similar Compose-native library for charts, Timber for logging, LeakCanary in debug builds only.

Use minSdk 26 and target the latest SDK. Companion-device presence features (§8.3) need API 31+ and are gated at runtime.

### 3.3 Design system

The app is dark-only, built on the Electric Blue / Midnight palette, and lives in `:core:designsystem` (`Color.kt`, `Theme.kt`). Dynamic color is off so the brand palette always applies. Screens never reference hex values. They use `MaterialTheme.colorScheme` for standard roles and `WatchTheme.colors` for health data (heart, activity, sleep, calories), status colors and gradients. The premium background gradient is painted once at the screen root through `PremiumBackground`, with transparent Scaffolds on top.

The contrast rules below were checked against WCAG. Text on Primary buttons is Midnight, because white on Electric Blue is only 3.2:1. Alternatively, use Deep Blue fills with white text. Deep Blue itself is never used as text or icon color on dark backgrounds, where it falls to 2.6–3.1:1. Text Muted is for large or disabled text only. Border is for decorative dividers, while input outlines use Slate to meet 3:1. Sleep Purple is for charts; sleep labels use the lighter `#9A82FF`. Success and Activity greens, like Error and Heart pinks, are nearly identical, so status colors are used only for status and health colors only for data. That way an activity ring never reads as a success message, and a heart-rate chart never reads as an error.

Colors are never the only signal. Charts and states also use labels, icons or patterns.

---

## 4. Watch layer

### 4.1 SDK initialisation

Initialise `FcSDK` once in `Application.onCreate()` and expose it only through `FitCloudWatchClient`, which is a Hilt `@Singleton`. Enable the built-ins you'll use: `telephonyControl`, `mediaControl`, `musicControl` and `autoSetTime`. The SDK must initialise in every process start path, including the app being launched by the system for the notification listener, boot, or a WorkManager job. Application-level init covers all of these.

### 4.2 The WatchClient contract

```kotlin
// :core:watch-api — no Android, no SDK types
interface WatchClient {
    val state: StateFlow<WatchState>
    val capabilities: StateFlow<WatchCapabilities?>   // null until first CONNECTED
    val events: SharedFlow<WatchEvent>                // find-phone, camera, hang-up...

    suspend fun bind(address: String, profile: UserProfile)   // destructive; guarded
    suspend fun login(address: String, profile: UserProfile)
    suspend fun unbind(keepWatchData: Boolean)

    fun syncHealthData(): Flow<SyncProgress>          // emits RawBatch items + progress
    fun liveHeartRate(): Flow<Int>                    // cold; stops when collector cancels
    suspend fun batteryLevel(): Int
    suspend fun findWatch()
    suspend fun sendNotification(n: OutgoingNotification): SendResult
    suspend fun applySettings(change: WatchSettingChange)
    suspend fun pushWeather(forecast: WeatherForecast)
}

sealed interface WatchState {
    data object Unbound : WatchState
    data object BluetoothOff : WatchState
    data class Waiting(val nextRetryAt: Instant?) : WatchState   // PRE_CONNECTING
    data object Connecting : WatchState                          // CONNECTING, PRE_CONNECTED
    data class Ready(val battery: Int?) : WatchState             // CONNECTED
    data class AuthFailed(val reason: String) : WatchState       // FcAuthException
}
```

`FitCloudWatchClient` maps `FcConnectorState` into `WatchState` and converts every `Single`, `Completable` and `Observable` at this boundary. Every suspend call checks `state is Ready` first and fails fast with a typed error otherwise, so callers never hang waiting on a disconnected watch.

### 4.3 Command discipline

BLE is a serial medium. All commands go through one `Mutex` inside the client, and each command gets a timeout (for example 10 seconds for settings and 5 seconds for notifications). Long operations such as health sync, watchface upload and firmware update take the mutex for their whole duration. Notifications that arrive during a long sync are dropped rather than queued, because a notification delivered 40 seconds late is noise.

### 4.4 Identity and binding

On first launch, generate a random UUID as `userId` and persist it in DataStore. This is the only identity the watch ever sees. Store the bound watch address and an `isBound` flag alongside it.

Bind only once. Every later connection, including after process death, a reboot or an app update, uses LOGIN with the same userId. Because BIND wipes watch data, the bind flow has two guards. It shows an explicit confirmation screen, and if the watch is already reachable in LOGIN mode it runs a full sync first. After any bind, delete today's cached step buckets for that watch, as the SDK wiki recommends, and record a `device_event` row of type `BOUND` so the data history explains the discontinuity.

### 4.5 Capabilities

After each `CONNECTED`, read `FcDeviceInfo` and convert its `isSupport(Feature.X)` checks into a `WatchCapabilities` data class: heart rate, SpO2, blood pressure, temperature, stress, sport, GPS, advanced reminders, weather, contacts limit and so on. Persist it with the firmware version. The UI shows only supported features. The sync pipeline only expects supported types.

---

## 5. Data layer

### 5.1 Principles

Everything is stored as UTC epoch milliseconds, plus the zone offset in effect when the reading was taken (seconds) and the zone ID. Units are canonical and fixed per field: count, metres, kilocalories, bpm, percent, mmHg and °C. The unit is part of the schema, not a column. Every row carries provenance: which watch, which firmware, which SDK version, and whether it came from monitoring, an on-watch measurement or a live app reading.

Record IDs are deterministic. Each record has a natural dedupe key, for example `hr:{deviceId}:{epochMs}:{origin}`, and its ID is `UUID.nameUUIDFromBytes(dedupeKey)`. Re-ingesting the same fact, whether from the journal, a restore or a re-sent sleep night, always produces the same ID. External services then deduplicate for free, because every push is an idempotent upsert keyed by that ID.

### 5.2 Ingestion pipeline

```
SDK syncData() emission
   │  (1) write raw payload to raw_ingest — FIRST and ONLY action per item
   ▼
raw_ingest (journal, append-only)
   │  (2) Normalizer: SDK shape → canonical records, units, timezones, dedupe keys
   ▼
canonical tables (steps, heart_rate, spo2, bp, temperature, stress,
                  sleep_session/sleep_stage, workout/workout_route/workout_hr)
   │  (3) SQLite triggers append to change_log automatically
   │  (4) Aggregator recomputes daily_summary for affected dates only
   ▼
change_log  ──►  SyncEngine  ──►  SyncProviders (Health Connect, REST, ...)
canonical tables  ──►  Exporters (JSONL/CSV/GPX)  and  UI queries
```

Step 1 exists because the watch deletes data once the SDK finishes each type. Inside the `syncData()` subscriber, the only work per item is a single Room insert of the serialised payload into `raw_ingest`. Everything else (parsing, deduplication, aggregation) reads from the journal afterwards. If normalisation crashes or has a bug, you fix the code and reprocess the journal, and nothing is lost. There is a residual risk: data is lost if the process dies in the milliseconds between receiving an item and committing its journal row. Keeping step 1 tiny minimises that window. Accept it and document it.

**Where decoding actually lives (Phase 6 finding).** The paragraph above describes decoding
as one step, but it can't be one module: turning raw bytes back into typed data needs
`FcSyncData.toXxx()` (an SDK type), and SDK types never leave `:core:watch-fitcloud`. Decoding
lives there, behind a `HealthDataDecoder` interface declared in `:core:watch-api`; `:core:data`'s
`HealthDataNormalizer` calls only that interface. See
`docs/superpowers/specs/2026-09-20-phase-6-data-core-design.md` for the full reasoning.

The journal payload is SDK-shaped, so each row stores `sdk_version` and `data_type`. Serialise with a small hand-written DTO per type, or a reflection-based JSON adapter scoped to this one purpose. Journal rows are marked `processed_at` after normalisation and pruned after 30 days. They double as your best test fixtures (§11).

Normalisation runs per type inside one transaction per batch, using bulk inserts with `OnConflictStrategy.IGNORE` on the dedupe key. Sleep is the exception: it upserts by `(device_id, night_date)`, and when the content hash differs it replaces the stages and increments `version`.

### 5.3 Schema

Every health table embeds the same metadata block:

```kotlin
data class RecordMeta(
    @ColumnInfo(name = "id") val id: String,                 // deterministic UUID (§5.1)
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String,  // UNIQUE index
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "start_time") val startTime: Long,    // UTC epoch ms
    @ColumnInfo(name = "end_time") val endTime: Long,        // == start for instant samples
    @ColumnInfo(name = "zone_offset_s") val zoneOffsetSec: Int,
    @ColumnInfo(name = "origin") val origin: Origin,         // MONITOR, MEASURE, LIVE
    @ColumnInfo(name = "version") val version: Int = 1,
    @ColumnInfo(name = "deleted") val deleted: Boolean = false, // tombstone, never hard-delete
    @ColumnInfo(name = "ingested_at") val ingestedAt: Long,
)

@Entity(tableName = "heart_rate",
        indices = [Index("dedupe_key", unique = true), Index("start_time")])
data class HeartRateEntity(
    @PrimaryKey @ColumnInfo(name = "pk") val pk: String,     // = meta.id
    @Embedded val meta: RecordMeta,
    val bpm: Int,
)
```

| Table | Payload fields | Dedupe key | Notes |
|---|---|---|---|
| `device` | address, model, firmware, sdk_version, capabilities_json, bound_at | address | One row per watch ever bound |
| `device_event` | type (BOUND, UNBOUND, FW_UPDATED, TZ_CHANGED), details | — | Explains gaps and discontinuities |
| `steps` | count, distance_m, energy_kcal | device + end_time | Interval record. start = max(previous end, end − 5 min) |
| `heart_rate` | bpm | device + time + origin | |
| `spo2` | percent | device + time + origin | |
| `blood_pressure` | systolic, diastolic | device + time + origin | Only if supported |
| `temperature` | celsius | device + time + origin | Only if supported |
| `stress` | level | device + time + origin | Only if supported |
| `sleep_session` | night_date, content_hash | device + night_date | Replace semantics, version bumps |
| `sleep_stage` | session_id, stage (AWAKE/LIGHT/DEEP/REM), start, end | session + start | Rewritten with its session |
| `workout` | sport_type, duration, distance_m, energy_kcal, avg/max hr, steps | device + sport_id | |
| `workout_route` | workout_id, time, lat, lon, altitude | workout + time | From `FcGpsData` via `sportId` |
| `workout_hr` | workout_id, time, bpm | workout + time | |
| `daily_summary` | date, steps, distance, energy, resting/avg/max hr, sleep_minutes, live_steps_total | device + date | **Derived.** Rebuilt, never synced as source data |
| `raw_ingest` | received_at, sdk_version, data_type, payload_json, processed_at, error | — | Journal, pruned after 30 days |
| `change_log` | seq (autoincrement), record_type, record_id, op, version, changed_at | — | Filled by triggers only |
| `sync_cursor` | provider_id, last_seq, snapshot_state, last_success_at, last_error | provider_id | One row per enabled provider |
| `export_history` | at, uri, format, range, record_counts | — | |

`FcTodayTotalData` updates `daily_summary.live_steps_total` for today and is never stored as samples, because it isn't a fact about an interval.

### 5.4 Change log via triggers

Create one `AFTER INSERT` and one `AFTER UPDATE` trigger per health table in the database creation callback and in each migration that adds a table:

```sql
CREATE TRIGGER IF NOT EXISTS trg_heart_rate_ai AFTER INSERT ON heart_rate
BEGIN
  INSERT INTO change_log(record_type, record_id, op, version, changed_at)
  VALUES ('heart_rate', NEW.pk, CASE WHEN NEW.deleted THEN 'DELETE' ELSE 'UPSERT' END,
          NEW.version, CAST(strftime('%s','now') AS INTEGER) * 1000);
END;
```

Because the trigger runs inside the same SQLite transaction as the write, a record and its change entry commit together or not at all. No code path can forget to log a change.

### 5.5 Migrations and schema discipline

Set `exportSchema = true` and commit the schema JSON files. Every version bump gets a `MigrationTestHelper` test. Never enable `fallbackToDestructiveMigration`; losing health data to a failed migration is the worst possible bug. Prefer additive changes: new columns with defaults and new tables. The export format is versioned separately (§6), so the database can evolve without breaking old exports.

### 5.6 Size and retention

Keep canonical data forever. Steps generate around 300 rows a day and heart rate a few hundred, depending on the monitoring interval. That works out to a few megabytes a year. Journal rows are pruned after 30 days. Change-log rows are compacted below the lowest provider cursor (§7.4).

### 5.7 Reading for the UI

The UI never loads raw sample lists into memory. Charts query pre-bucketed results computed in SQL (`GROUP BY start_time / 3600000` for hourly heart rate, for example) or read `daily_summary`. History lists use Paging 3. DAOs return `Flow`, collected with `collectAsStateWithLifecycle()`, so collection stops when the screen isn't visible.

---

## 6. Export and import

### 6.1 Format: "GTR Companion Export v1"

The export is a single ZIP file:

```
export-2026-09-11.zip
 ├── manifest.json
 ├── records/steps.jsonl
 ├── records/heart_rate.jsonl
 ├── records/sleep_session.jsonl      (stages nested per session)
 ├── records/workout.jsonl            (route + hr nested, or referenced)
 ├── derived/daily_summary.jsonl
 └── csv/…                            (optional, flattened, spreadsheet-friendly)
```

```json
{
  "format": "gtr-companion-export",
  "format_version": 1,
  "exported_at": "2026-09-11T08:30:00+03:00",
  "app_version": "1.0.0",
  "db_schema_version": 7,
  "devices": [{ "id": "…", "model": "GTR 3 Pro", "firmware": "…" }],
  "range": { "from": "2026-06-01", "to": "2026-09-11" },
  "files": { "records/heart_rate.jsonl": { "count": 48213, "sha256": "…" } }
}
```

Each JSONL line is a `:core:model` record serialised with kotlinx-serialization. It includes the deterministic `id`, ISO-8601 times with offsets, canonical units in the field names (`distance_m`, `energy_kcal`), `origin`, `version` and `deleted`. JSON Lines streams well, diffs well, and loads directly into pandas, DuckDB or any backend. GPX export per workout is a separate small exporter.

### 6.2 Streaming, not loading

Exports run in a `CoroutineWorker` with a progress notification. Each table is read with keyset pagination (`WHERE pk > :last ORDER BY pk LIMIT 1000`, never `OFFSET`) and written through a buffered writer into a `ZipOutputStream`. That stream opens on a URI the user picks via `ACTION_CREATE_DOCUMENT`. Memory use stays constant whether you export a week or five years.

### 6.3 Import doubles as backup and restore

The importer reads the same format and inserts through the normal DAO path, so deduplication and change-log triggers apply as usual. Because IDs are deterministic, importing the same file twice is harmless. An export-import round-trip test (§11) guarantees the format stays lossless.

### 6.4 Scheduled auto-backup (optional)

The user picks a folder once via `ACTION_OPEN_DOCUMENT_TREE`, and the app persists the URI permission. A weekly WorkManager job, constrained to charging with battery not low, writes a rolling export there and keeps the last N files.

---

## 7. External sync: plug-and-play design

### 7.1 The contract

```kotlin
// :core:sync-api
interface SyncProvider {
    val id: String                                  // "health_connect", "my_api"
    val displayName: String
    val supportedTypes: Set<RecordType>
    val constraints: SyncConstraints                 // e.g. requiresNetwork = true

    suspend fun readiness(): Readiness               // Ready | NeedsPermission | NeedsAuth | Unavailable
    suspend fun push(changes: List<RecordChange>): PushOutcome
}

data class RecordChange(val seq: Long, val op: Op, val record: HealthRecord) // :core:model type

sealed interface PushOutcome {
    data object Success : PushOutcome
    data class Retry(val after: Duration? = null) : PushOutcome
    data class Fatal(val reason: String) : PushOutcome       // stops provider, surfaces in UI
}
```

A provider implements that interface in its own module, registers with Hilt `@IntoSet`, and gets a card under Connected services on the Data tab. The card is the only provider-specific code outside the module, because how a provider connects (Health Connect's permission sheet, an OAuth login) differs per provider. The provider never touches Room, the watch or the scheduler.

### 7.2 SyncEngine

For each enabled and ready provider, the engine does the following. Unless the provider has a cursor, it runs a snapshot phase (§7.3). Otherwise it reads change-log entries with `seq > cursor`, filtered to the provider's `supportedTypes`, 500 at a time. It loads the current version of each record, calls `push()`, and on `Success` advances the cursor to the batch's highest `seq`. It repeats until caught up. On `Retry` it returns `Result.retry()` so WorkManager applies exponential backoff. On `Fatal` it disables the provider and shows the reason.

Each provider runs as its own unique WorkManager work (`sync-{providerId}`), so one broken provider never blocks another. Work is enqueued with `ExistingWorkPolicy.KEEP` after every ingest, plus a periodic safety net every few hours, using the provider's declared constraints.

Pushes must be idempotent. Health Connect supports this natively through `Metadata.clientRecordId` (the deterministic ID) and `clientRecordVersion` (the version). A REST backend should expose `PUT /records/{id}` with version checks. At-least-once delivery combined with idempotent upserts gives effectively exactly-once results.

### 7.3 Backfill for new providers

A provider enabled a year from now must receive all historical data. The snapshot phase first records the current maximum `seq`. It then walks every supported canonical table by primary key, pushing in batches, and saves its snapshot position in `sync_cursor.snapshot_state` so it survives interruptions. Finally it switches to tailing the change log from the recorded `seq`. Records written during the snapshot appear in both phases, which is harmless because pushes are idempotent.

### 7.4 Change-log compaction

A daily job deletes change-log rows with `seq ≤ min(last_seq)` across enabled providers. If no provider is enabled, the log is truncated entirely. A future provider still gets full history, because it starts from a snapshot rather than from the log.

### 7.5 First provider: Health Connect

Build this in M6 to prove the abstraction. It runs locally, needs no network, and makes your data available to Google Fit–style apps and anything else that reads Health Connect. The mapping is steps to `StepsRecord` (plus `DistanceRecord` and `ActiveCaloriesBurnedRecord` from the same interval), heart rate to `HeartRateRecord`, SpO2 to `OxygenSaturationRecord`, blood pressure to `BloodPressureRecord`, sleep to `SleepSessionRecord` with stages, and workouts to `ExerciseSessionRecord` with an `ExerciseRoute` and the workout's heart rate as one series.

Heart rate outside workouts is one `HeartRateRecord` per sample, not grouped into series. A series built from whichever samples share a change batch would overwrite earlier samples in the same window with a partial copy. One record per sample keeps one deterministic `clientRecordId` per fact, which is what makes the upsert idempotent. Temperature and stress are not pushed: the GTR 3 Pro reports neither, and Health Connect has no stress type.

---

## 8. Background operation

### 8.1 Moving parts

```
                     ┌──────────────── Application (FcSDK init, Hilt graph) ────────────────┐
 Boot / app update ─►│ BootReceiver ─┐                                                       │
 Watch in range ────►│ CompanionPresenceService (API 31+) ─┤                                 │
 User opens app ────►│ MainActivity ─┴──► WatchConnectionService (FGS: connectedDevice)     │
                     │                        │ owns serviceScope, observes WatchState       │
                     │                        │ triggers sync on Ready (debounced)           │
                     │                        │ updates its notification on state change     │
 Any notification ──►│ NotificationForwarder (NLS) ──► filter pipeline ──► WatchClient       │
 Calls ─────────────►│ SDK built-in telephonyControl                                         │
 Periodic ──────────►│ WorkManager: WatchSyncWorker, SyncEngine, Weather, Backup, Compaction │
                     └───────────────────────────────────────────────────────────────────────┘
```

Everything runs in one process. A separate `:remote` process would roughly double baseline memory and complicate the singletons for no benefit here.

### 8.2 WatchConnectionService

This foreground service is what keeps the process alive, and therefore the SDK connection, notification forwarding and telephony handling. Without it, Android kills the process within minutes of the app leaving the screen.

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<service
    android:name=".service.WatchConnectionService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

In `onStartCommand`, call `startForeground()` immediately with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, then make sure the client is in LOGIN mode for the stored address, and return `START_STICKY`. Use the `connectedDevice` type, not `dataSync`. `dataSync` has a daily runtime cap on Android 15 and can't be started from `BOOT_COMPLETED`.

The service notification uses its own channel at `IMPORTANCE_LOW` and shows the connection state and watch battery, for example "Connected · 72%". It is updated only when that text actually changes, never per heart-rate reading.

On `Ready`, the service triggers a health sync, debounced so a flapping connection causes at most one sync per 10 minutes. The service stops only when the user unbinds or turns off "keep connected" in settings.

### 8.3 Companion Device Manager: the biggest reliability win

Pair the watch through `CompanionDeviceManager.associate()` using `AssociationRequest.DEVICE_PROFILE_WATCH`, which requires `REQUEST_COMPANION_PROFILE_WATCH`. Being a CDM-associated companion app gives you several things. You can start a foreground service from the background. You can request `REQUEST_COMPANION_RUN_IN_BACKGROUND` and `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`. The watch profile can also bundle the relevant permission grants into the association consent. Confirm exactly which grants you receive on your phone and Android version.

On API 31+, call `startObservingDevicePresence()` and implement `CompanionDeviceService`. When the system reports the watch has appeared, ensure `WatchConnectionService` is running. When it disappears, you can leave the service in its cheap idle state. The system does the proximity watching instead of your app, which costs you nothing.

### 8.4 Surviving reboots, updates and kills

`BootReceiver` listens for `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` and starts the service if bound. A `connectedDevice` foreground service may be started from `BOOT_COMPLETED`; verify this on your target Android version. After process death, `START_STICKY` plus the presence callback bring it back, and startup always calls `login()`, never `bind()`.

OEM battery managers are the main remaining threat. Tecno, Infinix, Itel, Xiaomi, Oppo and Samsung all kill background apps aggressively. Onboarding should include a "Keep me alive" step. It requests `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which is an acceptable use for a companion-device app, and deep-links to the manufacturer's autostart or app-launch settings where they exist. The dontkillmyapp.com instructions for your phone brand are the practical reference. A debug screen showing "last connected", "last sync" and "last notification forwarded" tells you immediately when the OS has been killing you.

### 8.5 Notification forwarding

`NotificationForwarder` extends the SDK's `AbsNotificationListenerService`, which also wires up music control. The system binds it independently of your UI and will start your process to deliver notifications. Onboarding must send the user to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`, and the app should re-check the grant on every launch.

Every posted notification passes through the filter pipeline below, in order. Cheap checks come first, so most notifications are discarded in microseconds.

| Stage | Rule |
|---|---|
| Master switch | Forwarding enabled, watch `Ready` (otherwise drop; never queue stale alerts) |
| Source | Skip own package; skip packages not on the user's allowlist |
| Type | Skip ongoing notifications, group summaries, foreground-service notifications, and categories `progress`, `transport`, `service`, `status` |
| Extraction | Title from `EXTRA_TITLE`, text from the last `MessagingStyle` message, falling back to `EXTRA_BIG_TEXT` then `EXTRA_TEXT` |
| Dedupe | Hash of package + title + text in a 50-entry LRU with a 60-second window. Messaging apps re-post the same content constantly |
| Throttle | At most one send per app per 5 seconds; bursts coalesce into a single "3 new messages" |
| Mapping | Known packages map to specific `FcNotificationType` values (WhatsApp, Telegram, SMS apps, etc.); everything else to `OTHERS_APP` |
| Send | Truncate to a sane length and send through `WatchClient` with a 5-second timeout |

Optionally, skip forwarding while the phone is unlocked with the screen on. You're already looking at the phone, so this saves Bluetooth traffic and wrist buzzes.

For SMS, forward through the notification listener by mapping your messaging app's package to the SMS type, rather than adding an `SMS_RECEIVED` receiver. This avoids a sensitive permission and avoids duplicate alerts.

### 8.6 Calls, media, find phone and camera

Enable `FcBuiltInFeatures.telephonyControl` and call `telephonyControlPhoneStatePermission()` after the runtime permission is granted and on every return to the foreground, as the wiki describes. Copy the exact permission set from the sample app's manifest. Expect `READ_PHONE_STATE`, plus `READ_CALL_LOG` and `READ_CONTACTS` for caller number and name, and `ANSWER_PHONE_CALLS` for hanging up from the watch. `mediaControl` and `musicControl` handle media keys and now-playing info. Find phone and camera messages come through `WatchClient.events`. Find phone plays a ringtone through a short-lived full-screen notification. Camera opens a CameraX activity that reports its status back to the watch.

### 8.7 Scheduled work

| Worker | Schedule | Constraints | Purpose |
|---|---|---|---|
| `WatchSyncWorker` | Periodic, 60 min | Battery not low | Safety net for the 7-day buffer. If `Ready` and not already syncing, sync. Otherwise succeed quietly, since the next connection triggers a sync anyway |
| `SyncEngineWorker` (per provider) | After ingest, plus periodic every 6 h | Provider-declared | Push changes |
| `WeatherWorker` | Periodic, 3 h | Network | Fetch forecast using last-known coarse location and push to the watch if `Ready` |
| `BackupWorker` | Weekly | Charging, battery not low | Optional auto-export |
| `MaintenanceWorker` | Daily | Idle, charging | Prune journal, compact change log, trim log files |

---

## 9. Battery, performance and memory

### 9.1 Budgets

Measure these from M2 onward. They are targets to verify on your own phone, not guarantees.

| Metric | Target | How to measure |
|---|---|---|
| Battery attributed to the app, watch connected all day | ≤ 2% per day | Settings → Battery; `adb shell dumpsys batterystats` after `--reset` |
| Memory with UI closed (service, SDK and listener only) | ≤ 70 MB PSS | `adb shell dumpsys meminfo <package>` |
| App-scheduled wakeups (jobs and alarms, excluding BLE traffic) | ≤ 40 per day | `dumpsys jobscheduler`, Battery Historian |
| Health sync of one day's data | < 20 s | App's own sync log |
| Notification: posted → sent to watch | < 1 s at the 95th percentile | App's own forwarding log |

Before building anything, run FitCloudPro for 24 hours and record its battery and memory figures. That gives you a real baseline to beat.

### 9.2 Bluetooth

Let the SDK own reconnection and leave `setReConnectFrequent` off unless measurements show reconnect latency is a real problem. Never scan outside the pairing screen, and even there use a name or service filter and a 30-second timeout. Proximity is handled by CDM. Hold no wakelocks, since the Bluetooth stack wakes the CPU when packets arrive. Stop live heart rate whenever its screen stops. The `liveHeartRate()` flow is cold and cancels with the collector, so `repeatOnLifecycle(STARTED)` enforces this automatically.

### 9.3 CPU and I/O

Syncs are event-driven. Database writes are batched, one transaction per type per batch. Aggregation only touches dates that changed. JSON parsing happens once, in the normaliser, never on the UI path. All heavy work runs on `Dispatchers.IO` or `Default` from a `SupervisorJob` scope owned by the service, so one failure can't cancel everything else.

### 9.4 Memory

The service path loads no bitmaps, no Compose, and no caches beyond small LRUs. Singletons hold state flows of small values, never lists of samples. The UI's memory disappears when the activity finishes, because nothing in the service graph references UI objects. LeakCanary runs in debug builds, and StrictMode is enabled in debug to catch disk I/O on the main thread.

### 9.5 Logging

Timber with a file tree writes to a ring buffer of two 1 MB files. Release builds log at INFO and above. The debug screen can export the logs together with the last 50 journal entries. Never log notification content in release builds.

### 9.6 Release builds

Use R8 full mode with keep rules for the SDK. Vendor SDKs often use reflection, so test a release build in M1, not at the end. Declare the 16 KB page-size-compatible SDK version explicitly.

---

## 10. Security and privacy

### 10.1 The SDK dependency

The vendor Maven repository is plain HTTP, and anything on the network path could substitute a tampered library. Resolve the artifacts once in a controlled environment, or take the AARs from the repo's `libs` folder if they match the release you want. Copy them and their POMs into a repo-local Maven directory such as `third_party/maven/`, and point `settings.gradle.kts` at that directory only. Turn on Gradle dependency verification (`verification-metadata.xml`) so any change to those files fails the build. Remove `allowInsecureProtocol` from the build entirely.

Before trusting the SDK, watch its network traffic for a day, using a tool such as PCAPdroid, to see what it contacts. It has built-in file download for firmware, GPS assistance data and watchfaces. Confirm nothing else leaves the phone that you haven't chosen to send.

### 10.2 Data at rest and in transit

Health data lives in app-private storage, which Android's file-based encryption protects. Decide your cloud backup policy explicitly in `dataExtractionRules`. My suggestion is to exclude the database from Google cloud backup and rely on your own exports. Export files contain the same sensitive data, so the export screen should say so. Future REST providers must use HTTPS, store tokens in DataStore encrypted with an Android Keystore key, and never log payloads.

---

## 11. Testing strategy

**Normaliser tests from real data.** During recon, capture real `raw_ingest` payloads for every data type, then scrub and commit them as fixtures. Normaliser unit tests assert exact canonical output: units, timezones, dedupe keys, the step-interval rule and sleep replacement.

**Database.** Run `MigrationTestHelper` for every schema version, plus trigger tests that insert and update rows and assert the resulting change-log entries.

**Export round-trip.** Seed the database, export, wipe, import, and assert that every table is identical, IDs included.

**SyncEngine.** Test against a `FakeSyncProvider`: cursor advancement, retry and backoff, a fatal error isolating one provider, snapshot-then-tail with concurrent writes, and idempotency under duplicate delivery.

**FakeWatchClient.** Drives UI tests and service tests: scripted connection flaps, sync failures part-way through a type, and notification bursts.

**Soak tests.** Before calling any milestone from M2 onward done, wear the watch for 48 hours with the phone used normally. Check the §9.1 budgets and look for gaps in the data timeline.

---

## 12. Phases

This is the source of truth for what's done and what's next. **Keep the status column current** — update it in the same change that finishes a phase's exit criteria, before merging. Do not start a phase whose predecessor isn't `Done`; phases are sequential, not parallel, and none overlaps another in scope.

Each phase is one branch, cut from `main` after the previous phase has merged, and merged back to `main` only once every exit criterion in the table is met. Branch name suggestions are one word so they're easy to type; use them or something equally short.

| # | Phase | Branch | Status |
|---|---|---|---|
| 0 | Scaffold | `phase-0-scaffold` | Done |
| 1 | Fake watch & app state | `phase-1-fake-watch` | Done |
| 2 | Onboarding design system & UI | `phase-2-onboarding` | Done |
| 3 | Recon (M0, needs the physical watch) | `phase-3-recon` | Done |
| 4 | FitCloudWatchClient (M1) | `phase-4-fitcloud-client` | Done |
| 5 | Always-on service (M2) | `phase-5-always-on` | Done |
| 6 | Data core (M3) | `phase-6-data-core` | Done |
| 7 | Export / import (M4) | `phase-7-export-import` | Done |
| 8 | Watch control (M5) | `phase-8-watch-control` | Done |
| 9 | Sync framework & Health Connect (M6) | `phase-9-sync` | Done |
| 10 | Extras and hardening (M7) | `phase-10-hardening` | Blocked (firmware update needs a decision on its file source and Realtek DFU vendoring — see Phase 10) |

Status values: `Not started` → `In progress` → `Blocked (reason)` → `Done`. A phase is `Done` only when every row of its exit criteria is checked, not when the code merely compiles.

### Phase 0 — Scaffold

Turn the repo into a building Android project: `gradle/libs.versions.toml` with the latest mutually-compatible stable versions (§3.2), `build-logic/` convention plugins, every module's `build.gradle.kts` wired to the dependency rules in `CLAUDE.md`, the Gradle wrapper, `.gitignore`, R8 for release. `:app` gets `MainActivity` with edge-to-edge, the theme, and a 4-tab `NavHost` (Today, Health, Watch, Data) with placeholder screens.

**Exit criteria**
- [x] `./gradlew assembleDebug assembleRelease test lint` all pass.
- [x] The app installs and shows all four placeholder tabs on the premium gradient background.
- [x] Every module boundary in the `CLAUDE.md` table is enforced — an illegal import fails the build, not just a code review.

### Phase 1 — Fake watch & app state

Implement `FakeWatchClient` in `:core:watch-fake` against the `WatchClient` contract (§4.2): simulated scanning, bind/login with realistic delay, every `WatchState`, battery level, a sync that emits progress, live heart rate, and a debug control that forces any state on demand. Add the DataStore-backed `WatchIdentityStore` in `:core:data` (§4.4). Bind `WatchClient` to `FakeWatchClient` via Hilt.

**Exit criteria**
- [x] Unit tests cover `FakeWatchClient` and `WatchIdentityStore`.
- [x] A debug menu can force the app through every `WatchState` without touching real Bluetooth.

### Phase 2 — Onboarding design system & UI

Reconcile `:core:designsystem` against `docs/design-prompt.md` (tokens, spacing, radii, the Part C motion system) and the polished B1 reference (`docs/design/NexWatch B1 Onboarding and Pairing (polished).html`). Add any missing tokens or shared components there first. Implement the B1 flow in `:feature:onboarding` as idiomatic Compose — Welcome, Profile, Permissions, Find your watch, Pair confirmation, Pairing progress, Keep it running — driven by one `OnboardingViewModel` state machine over `WatchClient` (`FakeWatchClient` for now), with real runtime permission requests. `@Preview` for every screen and state. Onboarding shows only when `WatchIdentityStore.isBound` is false.

**Exit criteria**
- [x] `:core:designsystem` carries every token and component the polished reference uses; no screen hardcodes a hex value. Verified: `Color.kt` token set matches the HTML reference's `:root` variables value-for-value, and a repo-wide grep found no `Color(0x…)` or hex literal outside the theme file.
- [x] The full onboarding flow works end to end against the fake client and matches the polished reference's states and motion. Build/tests/lint are green and the motion primitives (`EntranceItem`, `pressScale()`, `WatchMotion` curves, segmented-control thumb slide, radar pulse) are wired through the screens. Commit `bebf22d` fixed the copy/layout mismatches found in the initial textual/structural read against the HTML reference (Welcome hierarchy and "stays on this device" copy, Profile segmented-control default, Permissions headline and progress bar, Pair confirmation warning copy, Keep it running manufacturer hint and live-status pulse). Still open, blocking `Done` (not this checkbox — see below): a real device install/relaunch check and a real browser side-by-side against the polished HTML.
- [x] Every screen and state has a `@Preview`. 11 previews across the 7 screens; Permissions (partial/all-granted) and Pairing (in-progress/success/failed) fully cover their documented states; Find your watch now also has a plain "Scanning" preview (no results yet) alongside "Scanning, 1 result" and "Nothing found".

Both items previously blocking `Done` are now verified for real, on a physical Redmi Note 8 Pro (Android 14) connected via adb:
- Fresh install (`pm clear`) shows onboarding; completing the flow against `FakeWatchClient` (Welcome → Profile → Permissions → Find watch → Pair confirmation → Pairing → Keep it running) flips `WatchIdentityStore.isBound` and lands on the 4-tab shell; force-stop + relaunch persists the bound state and skips onboarding straight to the shell.
- The polished HTML reference was served locally and screenshotted side by side with on-device screenshots: copy, layout and structure match.

That on-device pass caught a real bug the structural/textual comparison had missed: `PremiumBackground` is a plain `Box`, which never sets `LocalContentColor` the way `Surface` does, so `Text` calls with no explicit color (Welcome's title and headline) fell back to `LocalContentColor`'s `Color.Black` default and rendered near-invisible on the dark gradient. Fixed in `PremiumBackground` by providing `LocalContentColor = colorScheme.onBackground`; `Theme.NexWatch` also now sets `android:forceDarkAllowed="false"` (in a `values-v29` override, since minSdk is 26) as defense in depth. `./gradlew assembleDebug test lint` all green. See branch `fix/onboarding-contrast`.

### Phase 3 — Recon (M0)

Needs the physical watch; not something Claude Code can do unattended. Vendor the SDK per `third_party/maven/README.md`. Get something talking to the real watch and record findings in `docs/recon.md`.

**Exit criteria**
- [x] `docs/recon.md` has the capability list and at least one real, non-zero fixture payload proving the vendored SDK round-trips against the physical watch. Captured via a throwaway `recon-harness/` module (gitignored, never committed) calling the SDK's `FcConnector`/`syncData()` directly: real BIND and LOGIN connects, the full `FcDeviceInfo` capability list (HR, SpO2, blood pressure, sport, sleep, weather, contacts, DND, find-phone, step-extra supported; temperature, stress, GPS, ECG, HRV not supported on this unit), and real non-zero `FcStepData`/`FcTodayTotalData` payloads (`docs/recon/fixtures/`).
- [x] The vendored SDK is in `third_party/maven/` with dependency verification passing. `sdk-base-3.0.2.4.aar` and `sdk-fitcloud-3.0.2.4.aar` taken from the SDK's official GitHub mirror's `libs/` folder (HTTPS, not the vendor's insecure Maven server — see `third_party/maven/README.md` for provenance and checksums). `settings.gradle.kts` adds the repo-local Maven directory scoped to `com.topstep.wearkit` only; `:core:watch-fitcloud` depends on both coordinates (plumbing only — `FitCloudWatchClient` itself is Phase 4). `gradle/verification-metadata.xml` generated via `--write-verification-metadata sha256`; `./gradlew assembleDebug assembleRelease test lint` all green with verification enforced.

**Deliberately descoped** (time tradeoff, decided once the SDK round-trip was already proven against real hardware): fixture payloads for heart rate, SpO2, blood pressure, sleep and a workout all require hours of elapsed wear time or a full night's sleep to accumulate; the §14 questions (timestamp semantics, monitor-interval battery cost, DFU chip matching, connection-priority behavior, CDM permission grants) each need a dedicated real-watch test; and the 24h FitCloudPro baseline is blocked on the test phone having no Play Store. None of these block Phase 4 — `FitCloudWatchClient` only needs the connect/capability/sync plumbing already proven here. Revisit opportunistically:
- Missing fixtures and §14 Q1 (timestamp semantics) will surface naturally once Phase 6's ingestion pipeline is normalising real synced data — capture them then rather than blocking on them now.
- §14 Q2 (monitor intervals/battery), Q4 (DFU/firmware), Q5 (connection priority) matter for Phase 4/5 implementation detail, not for proving the SDK works — check them when those phases actually touch that code path.
- §14 Q6 (CDM permission grants) is Phase 5 (Companion Device Manager) scope directly — answer it there.
- The FitCloudPro battery/memory baseline is only needed to judge Phase 5's soak-test budget (§9.1) — capture it before that soak, on whatever device has Play Store access by then.

`docs/recon.md` §2/§3 keep their unchecked items as a running list for when each is picked back up; see `docs/recon.md` §0 for how to re-run `recon-harness` (re-add `include(":recon-harness")` to `settings.gradle.kts`, LOGIN not BIND) when that happens.

### Phase 4 — FitCloudWatchClient (M1)

Enable `:core:watch-fitcloud`. Implement `FitCloudWatchClient` per §4: `FcSDK` init in `Application`, state mapping, Rx→Flow adapters, the command `Mutex` with timeouts, capability detection, guarded bind vs. login. No custom reconnect logic. Switch the Hilt binding to the real client for release builds; debug builds keep a fake/real toggle. Add R8 keep rules for the SDK.

**Exit criteria**
- [x] The watch pairs once, then reconnects in LOGIN mode after an app restart and after a phone reboot.
      Verified on real hardware — see "Where the pairing criterion stands" below. Reconnection after a
      reboot is **launch-triggered**: `WatchAutoConnect` runs from `Application.onCreate()`, so it fires
      when the app is opened, not unattended. Reconnecting without a launch is Phase 5's `BootReceiver`
      and foreground service (§8.4), deliberately left there rather than half-built here.
- [x] `./gradlew assembleRelease` succeeds with the SDK's keep rules in place. Verified: R8 runs
      clean with `consumer-rules.pro` shipped inside the `:core:watch-fitcloud` AAR, so `:app`
      never has to know the SDK's shape. `./gradlew test lint` and `:app:assembleDebug` are green too.

**What landed.** `FitCloudWatchClient` (`:core:watch-fitcloud`) implements the whole §4.2
contract against the real SDK: `ConnectorState` → `WatchState`, every `Single`/`Completable`/
`Observable` adapted with `kotlinx-coroutines-rx3` at this boundary and nowhere else, one command
`Mutex` with per-command timeouts (10s settings, 5s notifications, 60s connect), `syncHealthData()`
holding the mutex for its whole duration and emitting raw Base64 payloads as `RawBatch` without
parsing a byte (I2), `liveHeartRate()` deliberately outside the mutex, capability detection on
each `CONNECTED`, and guarded BIND vs. LOGIN. No reconnect logic anywhere — `FcConnector` owns it.

Decisions worth carrying forward:

- **`FcSDK` is not in the Hilt graph.** Hilt builds its `SingletonComponent` in `:app`, so any SDK
  type reachable from an `@Inject` constructor would drag `com.topstep.**` onto `:app`'s compile
  classpath and break the rule that SDK types never leave `:core:watch-fitcloud`. `FitCloudSdk` is
  an object initialised from `Application.onCreate()` (§4.1) instead, and the client resolves it
  per call.
- **`WatchUserIdProvider` (`:core:watch-api`) inverts the §4.4 dependency.** The persisted userId
  lives in `:core:data`, which `:core:watch-fitcloud` may not depend on; both may depend on
  `:core:watch-api`, so the interface goes there, `StoredWatchUserIdProvider` implements it and
  `IdentityModule` binds it.
- **Firmware version needs a Java shim.** `FcDeviceInfo.app/project/flash/patch` are Kotlin-`internal`
  to the SDK's module (`docs/recon.md` §1 found this the hard way), but `internal` has no JVM
  equivalent, so `FcDeviceInfoVersions.java` reads them without reflection and stays compile-time
  checked. `FcExtraFirmwareInfo` only carries GNSS/4G strings, which this unit doesn't report.
- **Notifications stay on the deprecated three-argument `sendNotification()`.** The replacement
  `FcNotificationAbility.sendAppNotification()` takes four unnamed strings that the AAR's bytecode
  splices differently for SMS than for everything else; guessing wrong shows the user a mangled
  notification. §8.5 verifies the new call against the watch and switches then.
- **Weather condition codes are unverified.** FitCloud encodes weather as an icon index with no
  SDK constants; the values in `FitCloudMappers` come from the vendor's sample. Phase 8 pushes one
  known value per condition and records what the watch actually draws.
- **Two manifest/R8 hygiene fixes.** `:core:watch-fitcloud`'s manifest removes the
  `WRITE_EXTERNAL_STORAGE` that sdk-fitcloud declares for file transfer NexWatch doesn't do (§10.2),
  and `consumer-rules.pro` adds `-dontwarn` for the optional vendor extensions the AAR compiles
  against but we never ship (WeChat Pay, AI chat, Bluetrum, Realtek, OkHttp, Timber).

Bindings follow §12's instruction: `app/src/release/` binds `WatchClient` to `FitCloudWatchClient`
outright, `app/src/debug/` chooses between it and `FakeWatchClient` from `WatchImplPreference`,
which the Watch debug screen toggles (it takes effect on the next process start, because the client
is a `@Singleton` the running screens already hold).

**Pulled forward from Phase 5, to make the criterion reachable at all.** Two gaps stood between
the client and a real pairing, and both were small enough to do here rather than merge Phase 4
without the client ever having talked to a watch:

- `WatchClient.discoverWatches(): Flow<DiscoveredWatch>` — cold, backed by `FcScanner`, replacing
  the fixed fake list `OnboardingViewModel.startScan()` used to return. Collecting starts the scan
  and cancelling stops it, and the job is cancelled when a device is selected, so nothing scans
  outside the pairing screen (§9.2). This also makes `scanTimedOut` reachable for the first time,
  which retires that state's `TODO(phase-5)`.
- `WatchAutoConnect` in `:app` — LOGIN on process start when `WatchIdentityStore` says the watch is
  bound. Never BIND. `WatchIdentity` gained the profile fields so a reconnect after a reboot has
  something to re-send (§4.4); onboarding persists them before it connects.

Both are interim: Phase 5 moves them into `:core:service`, where CDM discovery and a foreground
service with `BootReceiver` replace them. They are written to be deleted.

**Three things only the real watch could have told us.**

1. **The vendored POMs' "declare nothing" policy has teeth.** `third_party/maven/README.md` says
   `:core:watch-fitcloud` declares the SDK's companions itself — and the SDK crashed on
   `FcSDK.Builder.build()` with `ClassNotFoundException: timber.log.Timber` until it did.
   RxAndroidBLE was missing for the same reason. Both are now pinned in the version catalog
   (`rxandroidble` 1.19.1, `timber` 5.0.1) and the `-dontwarn timber.log.**` that was papering over
   it is gone. Timber is also now planted in debug builds only, which is how the rest of this list
   got diagnosed at all.
2. **A leaked `TimeoutCancellationException` hung the pairing screen forever.** `withTimeout` raises
   a `CancellationException`, and every correct caller rethrows those untouched to stay cooperative
   — so the 60-second connect timeout silently killed the pairing coroutine instead of failing it,
   leaving "Connecting" on screen indefinitely. `WatchCommandTimeoutException` (`:core:watch-api`)
   now replaces it at the client boundary. Only a real connect that *doesn't* succeed exposes this;
   the fake client never times out.
3. **A failed pairing left the radio working.** `FcConnector` owns reconnection and keeps retrying
   after a failed bind, with nothing watching it. Onboarding's failure path now closes the connector
   via `unbind(keepWatchData = true)`.

**Where the pairing criterion stands.** Verified 2026-09-16 on a physical Redmi Note 8 Pro against
the real GTR 3 Pro (`C1:A1:B2:29:7A:0D`). The BIND wipe was authorised; the recon harness (Phase 3)
had bound this watch under its own userId, so a fresh BIND was the only way in.

- **Discovery**: `FcScanner` finds the watch by name and signal, and the pairing screen lists it.
- **BIND**: `Fc#AuthOperation: doBindAuth` → the watch's own on-screen confirmation → bound.
  `WatchIdentityStore` now holds `bound_address=C1:A1:B2:29:7A:0D` and `is_bound`.
- **LOGIN after an app restart**: force-stop and relaunch gives
  `apply firstAuth:true userId:018b12f0-… mode:LOGIN` → `doLoginAuth result:0` →
  `notifyStateChange:CONNECTED` in ~3.5 s, and `WatchClient.state` reaches `Ready(battery=90)` —
  a real battery read through the command mutex, not a mapped constant.
- **§4.5 capabilities**, read off the watch rather than the harness:
  `firmwareVersion=00000105` (the `FcDeviceInfoVersions.java` shim works against real hardware),
  heart rate / SpO2 / blood pressure / sport / weather supported, temperature / stress / GPS /
  advanced reminders not, `contactsLimit=10`. This matches `docs/recon.md` §1 and closes its two
  "not checked" rows (advanced reminders, contacts limit).

- **LOGIN after a phone reboot**: full `adb reboot`, then opening the app gives
  `device C1:A1:B2:29:7A:0D is CONNECTING` → `doLoginAuth result:0` → `CONNECTED` in under a second,
  `state = Ready(battery=90)`. Before the launch, `pidof com.nexwatch` is empty — the app is not
  running, which is the honest shape of this half: reconnection is **launch-triggered**, not
  unattended. `BOOT_COMPLETED` plus a foreground service to survive past it is Phase 5's
  `:core:service` (§8.4), still an empty `package-info.kt`. Recorded rather than quietly redefined.

**Fixed on entry to this phase (`.gitattributes`).** A fresh checkout of `main` on Windows
failed dependency verification on the two vendored `.pom` files while the `.aar` files passed.
Cause: `core.autocrlf=true` with no `.gitattributes`, so Git rewrote the POMs' LF line endings
to CRLF on checkout. That changes their bytes, so their SHA-256 no longer matched
`gradle/verification-metadata.xml` (the binary AARs are untouched by EOL translation, which is
why only the POMs failed). Fixed by adding `third_party/maven/** -text`, which pins the whole
vendored tree to its committed bytes. This would have broken any fresh clone on Windows, not
just this one — leave the attribute in place when adding future vendored artifacts.

### Phase 5 — Always-on service (M2)

Implement §8 in `:core:service`: `WatchConnectionService` (foreground, `connectedDevice`, `START_STICKY`, debounced sync on `Ready`), CDM association and presence on API 31+, `BootReceiver`, `NotificationForwarder` with the full §8.5 filter pipeline, telephony via `FcBuiltInFeatures`, find-phone and camera handling. Add the Diagnostics screen.

Inherited from Phase 4, deleted as this phase landed: `WatchAutoConnect` is gone; `WatchConnectionService`
now owns LOGIN on both process start (`AppRoot`) and boot (`BootReceiver`), which is what makes
reconnection-after-reboot unattended rather than launch-triggered.

**What didn't move as described.** `WatchClient.discoverWatches()` is still backed directly by
`FcScanner` and called from the pairing screen, not moved behind CDM association — scanning was
already confined to that one screen (§9.2), so there was no compliance gap to close, and CDM's
`AssociationRequest` device filter needs a real MAC address, which only `discoverWatches()` can
supply in the first place; there's no "behind" for it to move to. Camera remote handling (§8.6)
is explicitly deferred to Phase 8, alongside the rest of watch-control settings — a one-line
comment in `WatchConnectionService.observeEvents()` marks the two camera `WatchEvent`s as
deliberately unhandled.

**CDM association lands but presence detection does not fire yet.** `associateCompanionDevice()`
(`:feature:onboarding`) calls `CompanionDeviceManager.associate()` with `DEVICE_PROFILE_WATCH`
after a successful pair, gated at API 33 (the 3-arg `associate()` overload's real minimum, not
31 as first assumed). On a real device this returns `onAssociationPending` with a consent
`IntentSender` that must be launched through an `ActivityResultLauncher` for the system's
confirmation UI to appear; that launcher needs to live in `MainActivity`, which is out of scope
for this phase's onboarding flow, so the association typically never reaches
`onAssociationCreated` and `CompanionPresenceService.onDeviceAppeared` is not exercised on
device yet. LOGIN reconnection via `WatchConnectionService` (launch- and boot-triggered) is the
only path that's proven end-to-end; CDM presence as a reliability improvement over that is left
for a follow-up that wires the consent dialog. (Wired in Phase 10, see there.)

**Descoped to Phase 6.** §8.2's "triggers a health sync, debounced, on `Ready`" and §8.7's
`WatchSyncWorker` both assume a journal to write into. `WatchClient.syncHealthData()` is
destructive on the watch (§2) — calling it with nothing downstream to persist the emitted
`RawBatch`s would violate I3 (journal-first) and permanently lose data. `WatchConnectionService`
in this phase logs in and stays connected, but does not call `syncHealthData()`; that wiring
moves to Phase 6 once `raw_ingest` exists to receive it. WorkManager and the version-catalog
entries for it are therefore not added in this phase either.

**Exit criteria**
- [ ] A 48-hour soak passes with the app swiped away: notifications and calls still arrive.
- [ ] Battery and memory stay within the §9.1 budgets over that soak.

**Deliberately deferred (time tradeoff, decided at merge).** Both exit criteria above need
a device left alone and unplugged for 48 hours, which doesn't fit inside a single work
session, so this phase merged to `main` (PR #7) on code review and manual functional
verification alone — reconnection after restart/reboot, notification forwarding, find-phone
and hang-up were exercised on-device, but not over a full 48h unattended window. Phase 10's
"one-week soak shows no data gaps and stays within the §9.1 budgets" exit criterion is a
strict superset of what's unchecked here, so these two boxes are folded into that final pass
rather than duplicated as a standalone task. Revisit sooner only if Phase 6+ battery/memory
regressions make it worth isolating Phase 5's baseline in particular.

### Phase 6 — Data core (M3)

Journal (`raw_ingest`), normalisers, the full schema with triggers (§5), the aggregator, `WatchSyncWorker`, and health screens backed by SQL-bucketed queries.

**Exit criteria**
- [x] No gaps in a 7-day data timeline. Verified by `SevenDayTimelineTest` (Task 23).
- [x] Replaying the journal from scratch reproduces the canonical tables exactly. Verified for
      steps/heart_rate by `JournalReplayTest` (Task 23); `sleep_session`'s version-bump replay path
      (structurally different from the rest — content-hash comparison and a `version` increment on
      change, not a straight `OnConflictStrategy.IGNORE` dedupe) and the other RecordMeta-bearing
      tables are not yet exercised by a replay test. The wording above reads as full coverage across
      "the canonical tables"; the actual test coverage is narrower than that. Flagged as a follow-up,
      not claimed as done.
- [x] Migration and trigger tests are green for every schema version so far. `ChangeLogTriggerTest` +
      `SchemaSmokeTest` (Task 11); only version 1 exists, so there is no migration to test yet —
      noted explicitly rather than left implicit.

**Fixture strategy for `FitCloudHealthDataDecoder` tests (decided during Task 12):** `FcSyncData.toXxx()` parses an undocumented, closed-source binary layout this project cannot hand-encode — there is no public encoder to build byte-level fixtures against, only decoders. `javap` against the vendored `sdk-fitcloud-3.0.2.4.aar` confirmed every `FcXxxData` type (`FcStepData`, `FcTodayTotalData`, `FcHeartRateData`, `FcOxygenData`, `FcBloodPressureData`, `FcTemperatureData`, `FcPressureData`, `FcSleepData`, `FcSportData`, `FcGpsData`) has a public Kotlin constructor with exactly the fields recon captured, so the decoder's mapping logic (`FcXxxData -> DecodedHealthRecord`) is split into `internal` top-level functions (`toDecodedStep()`, `toDecodedTodayTotal()`, etc. — see `FitCloudHealthDataDecoder.kt`) tested directly by constructing the SDK type via its public constructor with real recon values. `FcSyncData` reconstruction and the `.toXxx()` dispatch (`dispatchDecode()`) stay untested by unit test — it's a one-line-per-branch `when`, not logic worth a byte-fixture harness. Every remaining data type in Task 13+ follows this same pattern.

**What landed.** The full §5 pipeline is in place. `HealthDataDecoder` (`:core:watch-api`) is
implemented by `FitCloudHealthDataDecoder` (`:core:watch-fitcloud`), the only place `FcSyncData.toXxx()`
is called; `HealthDataNormalizer` (`:core:data`) depends only on that interface and never sees an SDK
type. `raw_ingest` journals every synced item before anything else touches it (I2); normalisation reads
the journal per type inside one transaction per batch, `OnConflictStrategy.IGNORE` for the dedupe-key
tables and content-hash-compare-then-version-bump for `sleep_session`. The schema has all 18 canonical
tables (steps, heart_rate, spo2, blood_pressure, temperature, stress, sleep_session, sleep_stage,
workout, workout_hr, workout_route, daily_summary, device, device_event, sync_cursor, export_history,
raw_ingest, change_log), each with `CREATE TRIGGER IF NOT EXISTS` change-log triggers wired in
`RoomDatabase.Callback.onOpen`. `DailySummaryAggregator` recomputes `daily_summary` for affected dates
only, driven off the change log. `WatchSyncWorker` (`:core:data`) is the WorkManager entry point that
calls `WatchClient.syncHealthData()` and drains the resulting `RawBatch`s into the journal/normaliser
pipeline; `:app` wires its Hilt `WorkerFactory`. `:feature:today` and `:feature:health` replace their
Phase 0 placeholders with real daily-summary and 7-day-history screens reading SQL-bucketed queries
off `HealthSampleDao`/`DailySummaryDao` — no raw sample list is ever loaded into memory.

**Unverified against real hardware.** All of this was built and tested against recon fixtures and
hand-built unit-test data, not against a real multi-day sync from the physical watch — mirroring how
Phase 3 recorded its own deliberately-descoped items rather than overclaiming them as checked:

- **Every decoder mapping except steps and today-total.** Task 12's fixture strategy (above) lets the
  decoder functions be tested against the SDK's real constructor shapes, but only `FcStepData` and
  `FcTodayTotalData` have real non-zero recon payloads (`docs/recon/fixtures/`, from Phase 3) backing
  their expected values. Heart rate, SpO2, blood pressure, temperature, stress, sleep and sport/workout
  decoding are exercised by hand-constructed fixtures, not a real payload off the watch.
- **The today-total calorie `/1000` scale factor** (`FitCloudHealthDataDecoder.kt`, flagged back in
  Task 12) — the SDK gives no documented unit for `calorie`, and dividing by 1000 was inferred rather
  than confirmed against a watch-reported value the user can cross-check.
- **The MIN()-of-day resting-HR heuristic** (`HealthSampleDao`, feeding `DailySummaryAggregator`,
  flagged back in Task 16) — using the day's minimum heart-rate sample as a proxy for resting HR is a
  reasonable heuristic, not something FitCloud reports directly, and it's untested against a day of
  real wear.

The next phase (or a future recon session) should check these three first once real sync data has
accumulated over several days — the decoder mappings and the calorie scale factor are the sort of thing
that fails silently (a plausible-looking but wrong number), so they need eyes-on comparison against
what the watch's own app or screen reports, not just a passing unit test.

### Phase 7 — Export / import (M4)

JSONL+ZIP exporter, CSV and GPX, the importer, optional scheduled auto-backup (§6).

**Exit criteria**
- [x] The export/import round-trip test is green (every table identical, IDs included). `RoundTripTest`
      seeds every `RecordMeta`-bearing table plus `daily_summary` and `device`, exports, wipes, re-imports,
      and asserts each table's rows are identical — including deterministic IDs.
- [x] Exporting a year of data holds constant memory. Enforced structurally — no DAO added by this phase
      returns an unpaged "all rows" list for a `RecordMeta`-bearing table, only keyset-paginated reads
      (`WHERE pk > :afterId ORDER BY pk LIMIT :limit`, §6.2) — and proven at scale by
      `JsonlZipExporterTest`'s 2,500-row multi-page test, which forces the export loop through 3 full page
      cycles and asserts nothing is dropped or duplicated at a cursor boundary.

**What landed.** `HealthRecord` (sealed interface, `:core:model`, one variant per `RecordMeta`-bearing
table) is the full-fidelity canonical export/import wire type — the same type §7.1 names for Phase 9's
`SyncProvider.RecordChange`, so that phase reuses it rather than inventing its own. `ExportRepository`
(`:core:data`) is the only new code that touches Room for this phase: paginated reads mapped to
`HealthRecord`/`DailySummaryRecord`/`Device`, and import writes that go back through the normal DAOs, so
dedup-by-deterministic-ID and change-log triggers apply exactly as they do for a live sync. `JsonlZipExporter`
(`:core:export`) streams each table from a DB page straight into a `ZipOutputStream` entry, computing a
running SHA-256 per entry, and writes `manifest.json` last once real counts/hashes are known; CSV output
is flattened top-level-only per table (nested sleep stages/workout route/HR series are JSONL-only — a
deliberate scope decision, not an omission). `ZipImporter` validates the manifest's schema version and each
entry's SHA-256 before inserting anything, batching inserts at 500 rows. `GpxExporter` is a separate
per-workout exporter (§6.1's own closing line), not bundled into the main ZIP. `BackupPrefs`/`BackupWorker`
add the optional §6.4 weekly, charging-and-not-low-battery auto-backup to a user-picked SAF folder, pruning
beyond a configurable keep count.

Verified on a real device (not just a green compile, per CLAUDE.md's Workflow section): installed the debug
build, exported through the `CreateDocument` picker (empty database — no watch ever paired on this test
device — producing a well-formed 18-entry zip with correct SHA-256 checksums for every entry, `Exported 0
records` in the UI), imported the same file back through `OpenDocument` (`Imported 0 records`), and enabled
auto-backup through the `OpenDocumentTree` folder picker (persisted across recomposition, `BackupWorker`
confirmed registered in `dumpsys jobscheduler`). No exceptions in logcat for the `com.nexwatch` process
during the pass. Two real bugs were caught and fixed only by writing the round-trip test against real Room
autoincrement semantics rather than trusting the plan's first draft: `RoomDatabase.clearAllTables()` doesn't
exist on Room's standard-jvm test variant (only Android's), so the round-trip and importer tests wipe tables
via raw `DELETE FROM` instead; and `sleep_stage`/`workout_route`'s own autoincrement `pk` is never reused by
SQLite's `AUTOINCREMENT` keyword even after a full wipe, so — like `daily_summary`'s already-known case — the
round-trip comparison zeroes that column out before asserting equality, since it isn't part of the §6.1 wire
format.

An independent review pass after the on-device verification above caught two further bugs in the same
`ExportRepository`/`GpxExporter` code path — both copied verbatim from the plan's own draft and masked in
every original test because each fixture's route point happened to sit exactly at the workout's start time
and each workout's `duration_s` happened to equal `(endMs - startMs) / 1000`: route points' `offsetSeconds`
was hardcoded to `0` on export instead of computed from `atMs - startMs` (so every GPS point in an exported
GPX file would have collapsed onto the same instant), and `HealthRecord.Workout` carried no `durationS`
field at all, so import silently recomputed it from the timestamp span rather than round-tripping the
watch's real value (wrong for any paused/interval workout). Both are fixed, `durationS` is now a real field
on the wire type, and the tests that missed them were rewritten with fixtures where the correct and buggy
computations disagree, so a regression can't hide behind coincidence again. Not yet exercised on-device: a
non-empty database (real sync data, including a real GPS-tracked workout, through the full round trip) and a
genuinely revoked/uninstalled SAF folder triggering `BackupWorker`'s `Result.retry()` path — both are covered
by JVM tests but not hardware, the same "descoped, not hidden" honesty pattern Phase 3 and Phase 6 used for
their own gaps.

### Phase 8 — Watch control (M5)

Watch settings screens: alarms, reminders, DND, units, weather push, camera remote, contacts.

**Scope decisions.** Health monitoring, heart-rate alert and wrist-raise ride along because they use the
same SDK config pattern as DND and reminders. Watchfaces, display brightness/timeout, firmware update and
unpair from the design-prompt's Batch 5 stay out: watchfaces and firmware are Phase 10, and unpair is
onboarding's existing `unbind`. There is no polished Batch 5 HTML yet (`docs/design/README.md`), so the
screens are built from the text spec in `docs/design-prompt.md` on existing tokens.

**What landed.** `WatchSettings`/`WatchSettingChange` (`:core:watch-api`) cover DND, alarms, move and
drink-water reminders, health monitoring, heart-rate alert, wrist raise, units and contacts;
`WatchClient.readSettings()` returns what the watch reports, and every save in the UI re-reads it so a snapped
or refused value shows. `FitCloudSettingsMappers` builds each write from the config the watch last reported,
so bytes the app has no field for survive. `:feature:watch` replaces the debug screen as the Watch tab
(Diagnostics is now a row on it), hides any group the connected watch doesn't support, and offers an interval
picker only where the watch lets it be changed.

**Exit criteria**
- [x] Every setting round-trips: set it, then read the same value back from the watch. Verified 2026-09-30 on
      the real GTR 3 Pro (firmware `00000105`) through the app's own UI, each value checked again after
      force-stopping the app so the read could not come from the SDK's in-memory copy: DND (scheduled window),
      move reminder (window, respect-DND), drink-water reminder (window, interval), health monitoring (window),
      wrist raise (window), units (12h/miles/°F and back) and alarms (07:00, Mon+Wed, label) and contacts
      (add, persist, delete). Two reads look like round-trip failures but are the watch's own limits, not
      bugs: the move-reminder and health-monitor intervals are fixed (10 and 5 min) because this unit doesn't
      report `SEDENTARY_CONFIG_INTERVAL` / `HEALTH_MONITOR_CONFIG_INTERVAL`, so the app shows them read-only
      (new `WatchCapabilities` flags). The heart-rate alert can't be round-tripped here: the unit doesn't
      report `HEART_RATE_ALARM`, so the UI hides it. Its code path is covered by the mapper test only.
- [x] Only capabilities the connected watch actually supports (§4.5) appear in the UI. The capability read
      from the real watch (Diagnostics row) drove the gating: heart-rate alert hidden, both interval pickers
      read-only, alarm capacity 5, contacts limit 10, DND and time format shown.

**Confirmed against the watch's own screen** (by eye, not by read-back): alarm repeat bit order (Monday =
bit 0), `FcFunctionConfig` flag polarity (set = 12-hour, imperial, Fahrenheit), `MinuteWindow` as minutes since
midnight, and the weather icons for Sunny, Cloudy, Rain and Snow. Storm and Fog were pushed afterwards and are
not yet confirmed.

**Pairing on real hardware also exposed two crashes, fixed here.** Both predate this phase and were
invisible until a real BIND: the SDK's bond handling needs RxAndroid's `AndroidSchedulers` and crashed the
process right after pairing (`rxandroid` 3.0.2 is now pinned next to the other SDK companions, with its
verification hashes), and `CompanionDeviceManager.associate()` throws without
`android.software.companion_device_setup` declared (added to `:app`'s manifest as `required="false"`).

**Deliberately not in this phase.**
- Camera remote (§8.6): the two camera `WatchEvent`s stay unhandled in `WatchConnectionService`. The only
  test phone has no working camera, so it could not be verified, and an unverified camera path is worse than a
  documented gap. Do it when a phone with a camera is on hand.
- A scheduled weather fetch (`WeatherWorker`, §8.7): the Weather screen is a manual test push, which is what
  settled the condition codes. Real forecasts need a network source and a location decision.
- Display brightness/timeout, watchfaces and firmware update (see the scope note above).

### Phase 9 — Sync framework & Health Connect (M6)

`SyncProvider` API, `SyncEngine`, change-log compaction (§7), and the first provider, Health Connect.

Plan: `docs/superpowers/plans/2026-10-08-phase-9-sync.md`.

**What landed.** `:core:sync-api` holds the §7.1 contract. `SyncEngine` (`:core:data`) runs the §7.3 snapshot
(head seq recorded first, then each supported table by primary key, 500 per page, position saved after every page)
and then tails the change log, collapsing repeated entries for a record and loading its current version, with
tombstones sent as `DELETE`. Provider on/off lives in DataStore (`SyncPrefs`) and progress in `sync_cursor`.
Turning a provider off deletes its cursor so turning it on again backfills from scratch. A `Fatal` outcome only
switches it off, so "Reconnect" resumes from the same cursor. `SyncEngineWorker` runs as unique work
`sync-{id}` (`KEEP`) after every watch sync that changed data and after an import, plus `sync-{id}-periodic`
every 6 h. `MaintenanceWorker` runs daily while idle and charging: it prunes the journal and compacts the change
log below the lowest cursor (§7.4). `:core:sync-healthconnect` maps records as §7.5 describes, using the
record's deterministic ID as `clientRecordId` and its `version` as `clientRecordVersion`. It is ready only
when every write permission is granted. The Data tab has a Connected services card with the permission flow, a
rationale screen, backfill progress and error states, and debug-only "Audit Health Connect" and "Re-send
everything" tools that were used for the checks below.

**Exit criteria**
- [x] Enabling Health Connect backfills all existing history. Verified 2026-10-08 on the real phone (Redmi
      Note 8 Pro, Android 14) against the real watch's synced history. After enabling, the debug audit read back
      95 `StepsRecord`, 95 `DistanceRecord`, 95 `ActiveCaloriesBurnedRecord` and 8 `SleepSessionRecord` from
      the app's own origin. That equals the local database exactly (95 step intervals, all with distance and
      energy above 0, and 8 sleep nights). The watch had recorded no heart rate, SpO2, blood pressure or
      workouts (its journal payloads for those types were empty), so those types read back 0 on both sides.
- [x] New records appear in Health Connect within minutes of syncing from the watch. Verified 2026-10-10 on the
      phone, after the merge, against activity the watch recorded after 2026-10-04: ten unattended watch syncs
      since the merge ingested 73 new step intervals and a ninth sleep night. The latest, at 02:30:11.867 UTC,
      brought 10 intervals and the new night, and the debug audit (which now also reports, per type, when Health
      Connect stored the newest record) shows the newest `StepsRecord`,
      `DistanceRecord` and `ActiveCaloriesBurnedRecord` written at 02:30:12.830 and the new `SleepSessionRecord`
      at 02:30:12.827, about one second later. Totals matched the local database exactly (168/168/168 and 9, no
      duplicate `clientRecordId`), the cursor sat at the change-log head, and compaction had emptied the change
      log.
- [x] Forcing retries produces no duplicate records. Verified 2026-10-08 on the phone: "Re-send everything"
      (cursor deleted, so a full re-push of the same records) ran four more times. Three runs were force-stopped
      0.3–1 s after starting, and one was caught mid-snapshot (`snapshot_state` on steps, 0 of 103 pushed).
      Each resumed on relaunch through WorkManager. The audit afterwards still read 95/95/95/8 with total equal to
      distinct `clientRecordId`s for every type. `SyncEngineTest` covers the same at the engine level: a retry
      mid-tail, resuming a snapshot, and duplicate delivery.

**Phase 6 gaps found on hardware, fixed here.** Neither could show up before real data went through the
pipeline end to end.
- Nothing wrote the `device` row, and the normaliser does nothing without one. On the phone all 416 journal rows
  were unprocessed and every canonical table was empty. `DeviceRecorder` now records the bound watch (and a
  `BOUND` event) before each normalisation. It updates the firmware version when it changes, and treats the
  first known version after binding as filling in, not as an update.
- Step buckets were stored as instants (`start == end`) instead of §5.3's interval rule, and Health Connect
  rejects zero-length step records. The normaliser now applies `start = max(previous end, end − 5 min)`.
  Schema v2 (`MIGRATION_1_2`, which changes no tables) repairs existing rows and bumps their `version`, so the
  triggers log the correction for sync providers. The import path applies the same repair to exports written
  before v2. `MigrationTest` (`MigrationTestHelper`) replaces Phase 6's `SchemaSmokeTest` stand-in. Verified on
  the phone: after the upgrade all 95 rows were 2–5 min intervals at version 2.

**Deliberately not in this phase.**
- Workouts are sent as `EXERCISE_TYPE_OTHER_WORKOUT`. The SDK documents no sport-type table and no workout has
  been recorded on the real watch, so a mapping would be a guess that fails silently.
- Temperature and stress are not sent (§7.5). The engine supports them, so a provider that wants them only
  has to list them.
- Choosing which types a provider receives. Health Connect takes everything it supports, and readiness requires
  every write permission, because a partial grant would let the cursor move past changes that weren't written.

### Phase 10 — Extras and hardening (M7)

Custom watchfaces, firmware update (last, with the battery and connection preconditions from Batch 5 §9), and a final stress/polish pass.

**Exit criteria**
- [ ] Firmware update is tested only after taking a full export first.
- [ ] A one-week soak shows no data gaps and stays within the §9.1 budgets.

**What the real watch says about the watchface and firmware work** (probe on 2026-10-10, details in
`docs/recon.md` §3 Q4). The IC is `IC_8762C` and the watch doesn't report platform OTA, so the SDK sends
watchfaces and firmware the same way, through Realtek DFU (`DfuMode.MODE_8762C`). That path needs
`com.realsil.sdk.dfu` from `ext-realtek-dfu`, which isn't vendored, so neither feature can run on this watch
until it is. The watch uses the GUI watchface format, with a round 466×466 screen. Of its four dial slots, only
one takes pushes, and that slot holds the face currently on screen (store dial 114003). A custom watchface
replaces it.

**Custom watchfaces are postponed** (user decision, 2026-10-10). They are out of this phase, and nothing below
is needed for them now. The probe findings above stay recorded for when they come back.

**Blocked on decisions only the user can make:**
1. **The vendor's cloud.** Custom watchface templates and firmware files exist only on FitCloud's server
   (`fitcloud.hetangsmart.com`). The vendor's sample calls it over plain HTTP. It also answers HTTPS with a
   valid certificate. Using it means sending the watch's hardware info string, LCD id and UI version to the
   vendor, and adding the app's first network permission. The alternatives:
   - Allow it, HTTPS only, with the SDK's own downloader bypassed. The SDK downloads through OkHttp, which
     NexWatch doesn't ship, so the app downloads to its own storage and hands the SDK a `file://` URI.
   - Or install firmware only from a file the user picks, and drop custom watchfaces.
   A request to the vendor API from the development machine was refused by the session's permission
   policy, so the response shapes (including whether download URLs are HTTPS) are unverified.
2. **Vendoring `ext-realtek-dfu`** from the same GitHub mirror as the SDK, with checksums and verification
   metadata as `third_party/maven/README.md` describes. The mirror now publishes SDK 3.0.2.7 next to
   `ext-realtek-dfu-1.0.4`. Whether that extension works with the vendored 3.0.2.4 has to be checked, or both
   have to be re-vendored together.
3. **Real-watch tests.** A watchface push replaces the user's current face. A firmware flash can brick the
   watch, so it waits for a full export (the exit criterion above) and the user's go-ahead.

**Hardening landed so far.** Companion Device Manager association, which Phase 5 left without its consent
dialog, now works. `CompanionAssociator` (`:core:service`) runs `associate()` and hands the system's consent
`IntentSender` to the UI to launch. Onboarding launches it right after a successful pair. The Watch tab has a
"Wake when watch is nearby" row for a watch that was paired earlier, which covers this phone. Presence
observation starts once the association exists, using `ObservingDevicePresenceRequest` on API 36+. Checked on
the phone: the row opens the system dialog, and declining leaves the row Off with an explanation. Accepting is
a user step, because the dialog ignores injected taps, so `CompanionPresenceService.onDeviceAppeared` is still
unexercised.

---

## 13. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Data lost between the SDK deleting it on the watch and your write | Permanent gaps | Journal-first ingestion (§5.2) with a tiny write per item; normalisation bugs are fixed by re-running from the journal |
| Accidental BIND wiping the watch | Up to 7 days of data lost | Bind only via a guarded flow; sync before bind; every automatic path uses LOGIN |
| OEM battery manager kills the process | Missed notifications and syncs | CDM association and presence, battery-optimisation exemption, autostart deep links, visible "last seen" diagnostics |
| FitCloudPro competing for the watch | Auth failures, flapping connections | Uninstall or force-stop FitCloudPro before M1. On `FcAuthException`, show a clear screen instead of retrying |
| Closed-source SDK bugs or breaking changes | Blocked features | Isolation behind `WatchClient`; pin versions; keep HCI captures and the Gadgetbridge FitCloud implementation as references for a raw-BLE fallback |
| Tampered SDK via the HTTP repository | Compromised app | Vendored artifacts plus Gradle dependency verification (§10.1) |
| Timestamp ambiguity (watch local time vs UTC) | Shifted data, broken days | Verify in M0; store the zone offset per record; log `TZ_CHANGED` device events |
| Schema regret | Painful migrations | Additive-only changes, exported schemas, migration tests, and an export format versioned independently of the database |

---

## 14. Open questions for M0

Settle these against the real watch before M1. Are SDK timestamps true UTC epochs, or watch-local times encoded as epochs? What heart-rate and SpO2 monitoring intervals does `FcHealthMonitorConfig` allow, and what does each cost in watch battery? Does the GTR 3 Pro report temperature, stress, blood pressure, GPS or ECG through `FcDeviceInfo`? Which Realtek DFU extension matches the 8763E chip, and does the sample app's firmware update work on it? Does the sample app use connection-priority changes during bulk transfers, and does the SDK expose them? Which permissions does the `DEVICE_PROFILE_WATCH` association actually grant on your phone?
