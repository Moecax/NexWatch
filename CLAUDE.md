# NexWatch: project guide for Claude Code

NexWatch is a native Android companion app for a Zeblaze GTR 3 Pro smartwatch. The watch uses the FitCloudPro platform, and this app replaces the vendor app. It is a personal, single-user, single-watch, Android-only app, written in Kotlin with Jetpack Compose.

The full design rationale is in `docs/implementation-plan.md`. Read the relevant section before starting any milestone. The ordered work list is in `docs/HANDOFF.md`.

## Stack

Kotlin, Jetpack Compose with Material 3, single activity, Navigation Compose with type-safe routes, and Hilt. Coroutines and Flow everywhere. Room (KSP) for the database, DataStore for settings, WorkManager for deferred and periodic work, and kotlinx-serialization. The build uses a Gradle version catalog and convention plugins in `build-logic/`. Always use the latest **stable** versions and verify that they build together; never use alpha or beta versions.

minSdk 26, compileSdk and targetSdk set to the latest stable API. Companion Device Manager presence features need API 31+ and must be gated at runtime.

## Module map and dependency rules

| Module | Purpose | May depend on |
|---|---|---|
| `:app` | Activity, navigation host, bottom nav, Hilt entry point, binding selection | everything |
| `:feature:*` | One Compose feature each (screens + ViewModels) | core modules except `watch-fitcloud` and `database` |
| `:core:designsystem` | Theme, color tokens, shared Compose components | nothing else in the project |
| `:core:model` | Canonical health record types. **Pure Kotlin, no Android** | nothing |
| `:core:watch-api` | `WatchClient` interface and watch domain types. **Pure Kotlin** | `:core:model` |
| `:core:watch-fake` | `FakeWatchClient`, used for UI development and tests | `:core:watch-api` |
| `:core:watch-fitcloud` | The **only** module that may import the FitCloud SDK | `:core:watch-api`, `:core:common` |
| `:core:database` | Room DB, entities, DAOs, SQLite triggers, migrations | `:core:model`, `:core:common` |
| `:core:data` | Repositories, ingestion pipeline, aggregator, SyncEngine | `database`, `watch-api`, `sync-api`, `model`, `common` |
| `:core:export` | JSONL+ZIP, CSV and GPX exporters and the importer | `:core:model`, `:core:data` |
| `:core:sync-api` | `SyncProvider` interface. **Pure Kotlin** | `:core:model` |
| `:core:service` | Foreground service, notification listener, companion presence, boot receiver, workers | `data`, `watch-api`, `common` |
| `:core:common` | Dispatchers, Clock, logging, result types | nothing |

Enforce these rules through Gradle dependencies. If a module doesn't declare a dependency, it must not be able to import the type. SDK types never leave `:core:watch-fitcloud`, and Room entities never leave `:core:database` and `:core:data`.

## Non-negotiable invariants

1. **The local database is the single source of truth.** The UI, export and sync providers only read from it.
2. **Journal first.** The watch deletes data once it has been synced. Inside the SDK `syncData()` subscriber, the only work per item is inserting the raw payload into `raw_ingest`. Normalisation reads from the journal afterwards.
3. **Deterministic record IDs:** `UUID.nameUUIDFromBytes(dedupeKey)`. Records are immutable; a correction is a new `version`. Use tombstones, never hard deletes.
4. **The change log is written only by SQLite triggers.** Triggers are created with `CREATE TRIGGER IF NOT EXISTS` in `RoomDatabase.Callback.onOpen`.
5. **Nothing polls.** Work is event-driven or scheduled through WorkManager.
6. **Store UTC epoch milliseconds plus the zone offset** on every record. Canonical units: count, m, kcal, bpm, %, mmHg, °C.

## FitCloud SDK rules

- **Never write reconnect logic.** `FcConnector` owns reconnection, and the SDK docs explicitly discourage doing it yourself. Leave `setReConnectFrequent` off.
- **BIND mode wipes the watch.** Call `connect(..., bindOrLogin = true)` only from the guarded pairing flow, after the user has confirmed. Every other path uses LOGIN with the persisted `userId`.
- Adapt the SDK's RxJava3 types to coroutines and Flow only inside `:core:watch-fitcloud`, using `kotlinx-coroutines-rx3`.
- Run all watch commands through one `Mutex`, and give each command a timeout.
- The SDK comes from a vendored local Maven repository in `third_party/maven/`, with Gradle dependency verification. **Never** add the vendor's HTTP repository or `allowInsecureProtocol`.

## Design system rules

- Dark theme only, no dynamic color. Screens use `MaterialTheme.colorScheme` or `WatchTheme.colors` and never hardcode hex values.
- Text on primary buttons is Midnight `#050816`, not white. Deep Blue is never used as text or icon color.
- Text Muted is for large or disabled text only. Border is for decorative dividers only; input outlines use `outline` (Slate).
- Status colors (success, warning, error) are for status only. Health colors (heart, activity, sleep, calories) are for data only.
- Sleep labels use `WatchTheme.colors.sleepText`; `sleep` is for charts only.
- Color is never the only signal. Pair it with an icon or label.
- When implementing Claude Design screens, translate them into idiomatic Compose built on the theme. Do not port HTML or CSS structure literally. If a design introduces a new token, add it to `:core:designsystem` first.

## Battery and performance rules

- Never scan for devices outside the pairing screen, and never hold wakelocks.
- Collect live data with `collectAsStateWithLifecycle()` or `repeatOnLifecycle`, so it stops when the screen isn't visible.
- Charts query pre-bucketed data from SQL. Never load raw sample lists into memory; use Paging for long lists.
- Everything runs in one process. Do not add `android:process`.
- Never log notification content in release builds.

## Database rules

- `exportSchema = true`, with schema JSON files committed. Every version bump gets a `MigrationTestHelper` test.
- **Never** use `fallbackToDestructiveMigration`.

## Workflow

- Build: `./gradlew assembleDebug`. Unit tests: `./gradlew test`. Lint: `./gradlew lint`.
- After each task, run the build and tests and fix failures before reporting done.
- Keep changes scoped to the current session in `docs/HANDOFF.md`. If the plan needs to change, update `docs/implementation-plan.md` in the same change.
- Base package: `com.nexwatch` (for example `com.nexwatch.core.model`).
