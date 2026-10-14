# Sync reliability, blood pressure, and watchfaces: implementation plan

**Written 2026-10-14** for an implementing agent. Read all of Part 0 before touching code. Parts A and B are bug
fixes and go on the existing `sync-fixes` branch. Part C (watchfaces) is a new phase and **must not start** until
Parts A and B are merged to `main` **and** the user has answered the decisions in C.1.

Project rules you must follow (from `CLAUDE.md`): SDK types never leave `:core:watch-fitcloud`; `:core:watch-api` and
`:core:model` stay pure Kotlin; nothing polls; never write reconnect logic; every watch command goes through the
client's one `Mutex` and has a timeout; no hex colours in screens; no comments that restate the code. After each
task run `./gradlew assembleDebug test lint` and fix failures before moving on. Keep
`docs/implementation-plan.md` §12 current in the same change that changes a status.

---

## Part 0: What was found (the evidence)

### 0.1 The "Synced on 11 Oct" problem

The "Synced …" label on Today and Watch reads `DiagnosticsSnapshot.lastSyncAt`. Only one place writes it:
`WatchSync.run()` (`core/data/src/main/kotlin/com/nexwatch/core/data/sync/WatchSync.kt:51`), and only when the
coordinator returned success **and** the watch client emitted a `SyncProgress(completed = true)`. So since 11 Oct,
no sync has both finished and succeeded. There are two ways that happens, and the code allows both:

1. **A sync hangs forever, and every later sync is silently skipped (most likely).**
   - `FitCloudWatchClient.syncHealthData()` (`core/watch-fitcloud/.../FitCloudWatchClient.kt:259-281`) holds the
     client's BLE mutex and collects `connector.dataFeature().syncData()` with **no timeout at all**. The
     `getSyncTypes().await()` call before it has no timeout either. Every other command in the file has one (§4.3).
   - `WatchSync.run()` holds its own mutex for the same duration, with no timeout.
   - The foreground service keeps the process alive for days, so a hung run is never cleared by process death.
   - While it is stuck: `WatchSync.requestSync()` returns immediately because `mutex.isLocked` (the Sync now buttons
     do nothing); `WatchConnectionService.observeSyncTriggers()` uses a plain `collect` and is blocked behind the
     stuck `run()`; the 6-hour `WatchSyncWorker` queues behind the same mutex; every other watch command waits on
     the client mutex and times out; notifications are dropped as `WATCH_BUSY`.
   - The vendor's own sample (`htangsmart/FitCloudPro-SDK-Android`, `DeviceManager.syncData()`) checks
     `dataFeature().isSyncing()` first and handles `FcSyncBusyException` and `BleDisconnectedException`. NexWatch
     does none of this.
2. **Every sync fails, and the failure is invisible.** `HealthSyncCoordinator.syncAndNormalize()` wraps everything
   in `runCatching`. Any exception (an SDK sync error, a disconnect mid-sync, `FcSyncBusyException`, an aggregator
   error) becomes `Result.failure`. `WatchSync` doesn't record it or log it, the status goes back to `Idle`, and the
   button spins for a moment and stops. Nobody can tell what went wrong.

Secondary problems found on the way:
- **Sync fires on every battery change.** `observeSyncTriggers()` debounces the whole `WatchState`, and
  `WatchState.Ready` carries `battery`. Every 1% change while connected starts a full sync plus a catch-up worker.
  That wastes battery and makes contention on the bus more likely.
- The Today screen doesn't sync when you open it. The only syncs are on connect, on battery change (accidental) and
  every 6 hours.

**The user also reports that data doesn't appear after tapping Sync now.** That's the same root cause: the button
only reaches the screens through `syncData()` → journal → normaliser → aggregator. A hung or failed SDK sync stops
before anything new is journalled. A third, quieter way to get "the sync ran but no data appears" is also
possible: `HealthDataNormalizer.processUnprocessed()` returns immediately, leaving every journal row unprocessed
with no error, when `deviceDao.observeMostRecentlyBound()` is null. A0 step 4 checks for this.

**You cannot tell from the code alone which of (1) or (2) is happening on the user's phone.** Task A0 finds out.
The fixes in A1 to A5 cover both, so do them either way.

### 0.2 The blood-pressure problem

- **Confirmed bug: on-watch BP measurements are thrown away.** `syncDataTypeName()` names SDK type 133
  `blood_pressure_measure` and the journal stores it, but `dispatchDecode()`
  (`core/watch-fitcloud/.../FitCloudHealthDataDecoder.kt:55-75`) has no branch for it, so it falls through to
  `else -> emptyList()`. The normaliser then marks the row **processed with zero records**. Every BP reading the
  user takes by hand on the watch never reaches the database. Only the background `blood_pressure` (type 5)
  readings are decoded.
- The SDK has the decoder: `FcSyncData.toBloodPressureMeasure(): List<FcBloodPressureMeasureData>`, where
  `FcBloodPressureMeasureData(timestamp, sbp, dbp, heartRate)` exposes `getSbp()`, `getDbp()` and
  `getHeartRate()` (checked with `javap` against the vendored 3.0.2.4 AAR). The vendor sample saves it with
  `saveBloodPressureMeasure(userId, data.toBloodPressureMeasure())`.
- **The lost readings can be recovered.** Processed journal rows are kept for 30 days (`JOURNAL_RETENTION_MS`), so
  every `blood_pressure_measure` row since about 14 Sep is still in `raw_ingest` with its original bytes. Health
  monitoring was only switched on around 11 Oct, so all of the user's real readings fall inside that window.
- **BP values may look wrong even once they arrive.** These watches estimate BP from the optical sensor and scale it
  using a reference BP stored on the watch (`FcConfigFeature.get/setBloodPressureConfig()`, a
  `FcBloodPressureConfig` with `sbp`, `dbp` and `isEnabled`, built with `FcBloodPressureConfig.Builder`). The
  vendor app asks the user for their own cuff reading. NexWatch never reads or sets it, so the watch is using
  whatever default it shipped with. Task B4 exposes this.
- The read side (`HealthSampleDao.observeLatestBloodPressure`, `observeBloodPressureDaily`, `HealthRepository`,
  the Today and Health cards) looks correct. It shows nothing new because nothing new is written.

### 0.3 Watchfaces: what the SDK and the watch support

Facts that are already established (recon probe 2026-10-10, `docs/recon.md` §3 Q4, §12 Phase 10):
- IC `IC_8762C` (Realtek). Watchface pushes go through **Realtek DFU** (`FcDfuManager`, `DfuMode.MODE_8762C`).
  That needs `com.realsil.sdk.dfu.*`, which is in `ext-realtek-dfu`, **which is not vendored**. Nothing can be pushed
  until it is.
- Features: `GUI`, `DIAL_PUSH`, `DIAL_CUSTOM` and `DIAL_COMPONENT` are supported. `FcDialPushInfo`: lcd `22`, round
  466×466, tool version `1.5`, four dial spaces. Three are built in and can't be pushed to. One `DIAL_TYPE_NORMAL`
  space at position 3 can be pushed to, and it holds store dial `114003` (bin flag `0xA1`), the face shown now.
  **Any push replaces the current face. There's no spare slot.**
- No `INTERNET` permission in any manifest today.

What I found in the vendored SDK (javap, 3.0.2.4) and the vendor's sample app:

| Step | API |
|---|---|
| Read slots, shape and tool version | `connector.settingsFeature().requestDialPushInfo(): Single<FcDialPushInfo>` (`lcd`, `shape: FcShape`, `toolVersion`, `dialSpaces: List<FcDialSpace>`, `currentDialNum`, `currentPosition`). `FcDialSpace`: `dialType`, `dialNum`, `binVersion`, `binFlag: Byte`, `spaceSize`, `components`, `isPushable()` |
| Get a DFU manager | `connector.newDfuManager(useDefault: Boolean, cacheDir: File): FcDfuManager` (the sample passes `true`) |
| Push a `.bin` | `dfuManager.start(DfuType.DIAL, uri: Uri, binFlag: Byte, …): Completable`. Progress comes from `observerStateProgress(): Observable<StateProgress(state: DfuState, progress: Int)>`. States: `NONE, DOWNLOAD_FILE, PREPARE_FILE, PREPARE_DFU, DFU_ING, DFU_SUCCESS, DFU_FAIL`. Errors are `FcDfuException(errorType, errorCode)`, for example `ERROR_CODE_DFU_MODE_LOW_BATTERY`. Call `release()` when finished |
| Build a custom "photo" face (GUI format) | Start from a **template `.bin` from the vendor server**. `DialDrawer.createDialBackground(bitmap, shape, ScaleType.CENTER_CROP)`, `DialDrawer.createDialPreview(bg, styleBitmap, shape, ScaleType.CENTER_CROP, position, styleBaseOnWidth, w, h)`, then `DialWriter(templateBin, background, preview, position, isGui).apply { setCopyFile(temp); setAutoScaleBackground(true); setAutoScalePreview(true) }.execute(): File` |
| After a GUI custom push | `settingsFeature().setDialComponent(spaceIndex, byteArrayOf(styleIndex.toByte()))` picks the clock style. The sample waits up to 90 s for reconnect first, because the watch reboots its UI after DFU |
| Delete | `settingsFeature().deleteDial(dialNum)` / `deleteDialByPosition(pos)` |

Vendor server (the sample's `ApiService`, base `fitcloud.hetangsmart.com`, which also answers HTTPS; all endpoints are form-encoded `POST`s):
- `/public/dial/customgui`, fields `lcd`, `toolVersion`, `platform` (`"gui"` for 8762C). Returns the custom-face
  template: `binUrl`, `binSize`, `previewImgUrl`, and `components[0].urls`, which are clock-style overlay images drawn
  at **2× the screen width** (so `styleBaseOnWidth = shape.width * 2`).
- `/public/dial/list`, fields `hardwareInfo`, `lcd`, `toolVersion`. Returns the store catalogue
  (`dialNum`, `binUrl`, `imgUrl`, `previewImgUrl`, …).
- `/public/dial/get`, field `data` = JSON int array of dial numbers. Use it to look up `114003`, the user's current
  face, so it can be **restored**.

So a custom face is never generated from scratch. The SDK patches a vendor template. **Some network access to the
vendor is required, at least once per template.** The plan below downloads each file once and caches it.

Correction to §12 Phase 10's note: `ext-realtek-dfu-1.0.4.aar` was **already in the vendor repo at the exact commit
we vendored 3.0.2.4 from** (`7409d0f`, `libs/ext/ext-realtek-dfu-1.0.4.aar`, 852,270 bytes). That pair shipped
together, so no SDK upgrade is needed.

---

## Part A: Sync reliability (branch `sync-fixes`)

`sync-fixes` already has uncommitted Manrope work. Commit that first, on its own, before starting A0, so the
diffs stay separate.

### A0. Diagnose on the phone first (about 15 minutes; needs the user's phone over USB)

The goal is to find out whether it's a hang or a failure, and record it in the PR description. Install the
**current** debug build (`./gradlew installDebug`). Debug builds plant a Timber tree, so the SDK's own sync tracing
reaches logcat.

1. Before tapping anything, look at Today. **A sync spinner that never stops** means a hang. A static button means
   failure.
2. Watch → Notifications and calls → log. Many "Skipped · watch busy" entries mean a hang (the bus is held).
3. Run `adb logcat -c && adb logcat | grep -iE "sync|Fc|nexwatch"`, then tap Sync now. Capture the output, and save
   the relevant 50 lines to the PR description.
4. If the build is debuggable, pull the DB and look at the journal:
   `adb exec-out run-as com.nexwatch cat databases/<db name> > nw.db` (the name is in `NexWatchDatabaseBuilder.kt`),
   then `SELECT data_type, COUNT(*), MAX(received_at), SUM(error IS NOT NULL) FROM raw_ingest GROUP BY data_type;`.
   Rows newer than 11 Oct mean syncs are pulling data, so the problem is after the journal (the aggregator or the
   `completed` flag). No newer rows means the SDK sync itself is stuck or failing. Also run
   `SELECT COUNT(*) FROM raw_ingest WHERE processed_at IS NULL;` and `SELECT * FROM device;`. Many unprocessed
   rows plus an empty or unbound `device` table means the normaliser is skipping everything. If so, fix
   `DeviceRecorder`/`HealthSyncCoordinator` so the bound device row always exists before normalising, and have
   `processUnprocessed()` throw instead of returning `emptySet()` when there's no device, so A2 records it as an
   error instead of a silent no-op. Add a test for that.

If no phone is available, skip A0 and say so in the PR. Don't invent results.

### A1. Watch-client hardening (`:core:watch-api`, `:core:watch-fitcloud`, `:core:watch-fake`)

1. In `core/watch-api/.../WatchClient.kt` (or wherever `WatchCommandTimeoutException` and `WatchNotReadyException`
   are declared, so look there first), add:
   ```kotlin
   /** The watch is already in a sync that this client didn't start, or the SDK refused a second one. */
   class WatchSyncBusyException : RuntimeException("watch is already syncing")
   ```
2. In `FitCloudWatchClient.syncHealthData()`:
   - Right after `requireReady()`, add `if (connector.dataFeature().isSyncing()) throw WatchSyncBusyException()`.
   - Wrap the `getSyncTypes()` call: `runCatching { withOperationTimeout("syncTypes", COMMAND_TIMEOUT) { … } }`.
   - Put an **inactivity** timeout on the SDK stream, plus a hard cap on the whole sync:
     ```kotlin
     private val SYNC_IDLE_TIMEOUT = 60.seconds   // no item from the watch for this long = stalled
     private val SYNC_TOTAL_TIMEOUT = 10.minutes  // a week of minute-level data is well under this
     ```
     Use `withOperationTimeout("sync", SYNC_TOTAL_TIMEOUT) { … }` around the collect, and
     `.asFlow().timeout(SYNC_IDLE_TIMEOUT)` (`kotlinx.coroutines.flow.timeout`, `@OptIn(FlowPreview::class)`) on the
     SDK observable. `timeout` throws `TimeoutCancellationException`. Catch it **inside** the flow and rethrow
     `WatchCommandTimeoutException("sync")`, for the same reason `withOperationTimeout` exists (read its KDoc at the
     bottom of the file).
   - Translate SDK exceptions at the boundary: `com.topstep.fitcloud.sdk.exception.FcSyncBusyException` →
     `WatchSyncBusyException`. `com.polidea.rxandroidble3.exceptions.BleDisconnectedException` →
     `WatchNotReadyException(state.value)`. Check that both classes are on the classpath. If one isn't, catch by
     `javaClass.simpleName` and leave a one-line comment explaining why.
   - Cancelling the collector disposes the Rx subscription (kotlinx-coroutines-rx3 does this), so nothing else is
     needed to stop the SDK.
3. `FakeWatchClient`: add two debug and test hooks next to `forceState`, `var nextSyncHangs: Boolean` and
   `var nextSyncError: Throwable?`, which the fake honours once and then resets. If `WatchDebugController` is the
   interface the debug screen uses, add them there too, so the hang can be reproduced from the debug screen.

### A2. Make `WatchSync` impossible to wedge, and record every outcome (`:core:data`)

1. `DiagnosticsStore`: add `lastSyncAttemptAt: Long?` and `lastSyncError: String?` to `DiagnosticsSnapshot`, add
   new keys `last_sync_attempt_at` and `last_sync_error`, and add:
   ```kotlin
   suspend fun recordSyncAttempt(atMs: Long, error: String?)  // error == null clears it
   ```
   It's a DataStore change, not Room, so there's no migration.
2. `WatchSync.run()`:
   - Wrap the coordinator call in `withTimeout(SYNC_GUARD_TIMEOUT)` with `SYNC_GUARD_TIMEOUT = 12.minutes`, so
     it's always longer than the client's own cap. This is the belt-and-braces guard that guarantees the mutex is
     released. Catch `TimeoutCancellationException` and turn it into `Result.failure(WatchCommandTimeoutException("sync"))`.
     **Do not** swallow other `CancellationException`s.
   - After the run: on success with `completed`, call `recordSync(now)` and `recordSyncAttempt(now, null)`. On
     success without `completed` (the watch wasn't connected), record nothing. On failure, call
     `recordSyncAttempt(now, reason)`, where `reason` is a short human string built from the exception type:
     "Watch was busy", "Watch disconnected during sync", "Watch stopped responding", otherwise the exception's simple
     class name. **Never** put payload data in it.
   - Log the failure with `Timber.w` in debug only. Check how `:core:data` logs today (`grep -r Timber core/data`).
     If it doesn't log at all, skip logging. The persisted error is enough.
3. `HealthSyncCoordinator`: no behaviour change. Leave the `runCatching`, because `WatchSync` now records what it
   catches.
4. Tests in `WatchSyncTest` (it already has `MemoryPreferences` and `testDispatchers`):
   - `a sync that throws records the error and leaves lastSyncAt alone`
   - `a hung sync is abandoned after the guard timeout and the next sync runs` (use `nextSyncHangs` with
     virtual time from `runTest`, call `run()` twice, and assert the second one completes)
   - `a successful sync clears the previous error`

### A3. Trigger syncs on the right events (`:core:service`, `:feature:today`)

1. `WatchConnectionService.observeSyncTriggers()`: only trigger on the **transition** to Ready:
   ```kotlin
   watchClient.state
       .map { it is WatchState.Ready }
       .distinctUntilChanged()
       .debounce(5_000)
       .filter { it }
       .collect { watchSync.run(); WatchSyncWorker.enqueueCatchUp(applicationContext) }
   ```
   Keep the plain `collect` and its comment. That reasoning still holds.
2. Sync when the user opens the app. In `TodayViewModel.onResume()`, add: if the watch is `Ready` and
   `lastSyncAt` is null or older than 15 minutes, call `watchSync.requestSync()`. This is event-driven (a screen
   resume), not polling, so it's allowed under invariant 5. Read `lastSyncAt` with
   `diagnostics.snapshot.first()` inside `viewModelScope.launch`.
3. Leave the 6-hour periodic worker as it is.

### A4. Show the failure to the user (`:feature:today`, `:feature:watch`)

1. `TodayUiState` / `WatchStatusUiState`: add `lastSyncError: String?`, filled only when
   `lastSyncAttemptAt > (lastSyncAt ?: 0)`.
2. Today `WatchStrip` (`TodayScreen.kt` around line 269): when `lastSyncError != null` and not syncing, replace the
   "Synced …" line with "Sync failed · {reason}" using `WatchTheme.colors` warning status colour **and** a warning
   icon (colour is never the only signal), and keep the Sync button enabled so it can be retried. Do the same on
   the Watch tab hero next to Sync now.
3. Add a Compose preview for the failed state next to the existing previews.
4. Debug screen (`app/.../WatchDebugScreen.kt`): add "Last sync attempt" and "Last sync error" lines, plus buttons
   wired to the fake's `nextSyncHangs` and `nextSyncError` hooks when the fake client is in use.

### A5. Verify (exit criteria for Part A)

- [ ] `./gradlew assembleDebug test lint` passes.
- [ ] On the debug screen with the fake watch: "next sync hangs" → tap Sync now → after the guard timeout the
      spinner stops, the strip shows "Sync failed · Watch stopped responding", and the next Sync now succeeds.
      (For a manual check, temporarily lower the timeouts in a local edit. Don't commit that.)
- [ ] On the real phone: walk about 200 steps, tap Sync now on Today. The label changes to "Synced just now"
      **and** the step count and heart-rate chart include the new data. Leave the phone overnight;
      the next morning, opening the app shows a sync time from that morning.
- [ ] The notifications log shows no new "watch busy" skips outside an actual sync.
- [ ] The PR description records what A0 found (hang or failure) and the logcat excerpt.

---

## Part B: Blood pressure (branch `sync-fixes`, after Part A)

### B1. Decode `blood_pressure_measure` (`:core:watch-fitcloud`)

In `FitCloudHealthDataDecoder.kt`:
```kotlin
import com.topstep.fitcloud.sdk.v2.model.data.FcBloodPressureMeasureData
…
"blood_pressure" -> syncData.toBloodPressure().orEmpty().map { it.toDecodedBloodPressure() }
"blood_pressure_measure" -> syncData.toBloodPressureMeasure().orEmpty().flatMap { it.toDecodedRecords() }
…
/** An on-watch BP measurement also measures heart rate; keep both, each under its own table. */
internal fun FcBloodPressureMeasureData.toDecodedRecords(): List<DecodedHealthRecord> = listOfNotNull(
    DecodedHealthRecord.BloodPressure(atMs = timestamp, systolic = sbp, diastolic = dbp),
    heartRate.takeIf { it > 0 }?.let { DecodedHealthRecord.HeartRate(atMs = timestamp, bpm = it) },
)
```
The normaliser already derives `Origin.MEASURE` from the `_measure` suffix, so the heart-rate sample is stored as a
MEASURE sample with its own dedupe key. Nothing changes in `HealthDataNormalizer`.

Also skip zero readings in both BP mappers. A `sbp == 0 || dbp == 0` reading is a failed measurement, not a
value. Use `takeIf`, return an empty list, and add a short comment saying so.

Tests in `FitCloudDataMappersTest` (it builds SDK data classes with their public constructors, so follow the
existing `blood pressure mapping` test): `FcBloodPressureMeasureData(ts, 128, 82, 71)` gives one BP and one HR
record. `FcBloodPressureMeasureData(ts, 128, 82, 0)` gives BP only. A zero sbp gives nothing.

### B2. Recover the readings that were thrown away (`:core:database`, `:core:data`)

1. `RawIngestDao`: add
   ```kotlin
   @Query("UPDATE raw_ingest SET processed_at = NULL, error = NULL WHERE data_type = :dataType")
   suspend fun markUnprocessed(dataType: String): Int
   ```
   This is a query, not a schema change, so there's no version bump and no migration.
2. Add a one-shot "reprocess" step that runs once per install after upgrade. Put it in `HealthSyncCoordinator`
   right before `normalizer.processUnprocessed()`, guarded by a DataStore flag
   (`journal_reprocess_bp_measure_v1`) in the existing sync or diagnostics DataStore. Read
   `core/data/.../di/SyncDataStore.kt` and use the same pattern. Set the flag only **after** `markUnprocessed`
   returns. Re-inserts are safe: records have deterministic IDs and the inserts are `OnConflictStrategy.IGNORE`
   (invariant 3).
3. Test in `JournalReplayTest` (or a new `BpReprocessTest`): insert a processed `blood_pressure_measure` row, run
   the coordinator twice, and assert `markUnprocessed` ran once and the row ended up processed again.

### B3. Make Health Connect pick them up

Nothing to build. New BP rows fire the change-log triggers, and `syncScheduler.enqueueEnabled()` already runs when
`affectedDates` is not empty. Check this on the phone: after B2, Health Connect shows the old manual readings.

### B4. Reference BP on the watch (optional; ask the user before starting it)

The watch scales its BP estimate with a stored reference. Expose it as a watch setting, following the existing
`SetHeartRateAlert` / `SetHealthMonitoring` pattern end to end:
- `:core:watch-api`: `data class BloodPressureReference(val enabled: Boolean, val systolic: Int, val diastolic: Int)`,
  `WatchSettings.bloodPressureReference: BloodPressureReference?`, and
  `WatchSettingChange.SetBloodPressureReference(value)`.
- `:core:watch-fitcloud`: read it with `config.getBloodPressureConfig()` (only when
  `config.supports(FcDeviceInfo.Feature.BLOOD_PRESSURE)`). Write it with
  `FcBloodPressureConfig.Builder().setEnabled(..).setSbp(..).setDbp(..).create()` and
  `config.setBloodPressureConfig(it).await()`. Put the mappers in `FitCloudSettingsMappers.kt` with tests.
- `:feature:watch`: a "Blood pressure reference" row under the health settings group, with two number fields
  (systolic 70–200, diastolic 40–130, systolic > diastolic) and helper text: "Enter a reading from a cuff
  monitor. The watch uses it to calibrate its estimates."
- Fake client: store it like the other settings.

### B5. Verify (exit criteria for Part B)

- [ ] Unit tests pass, including the new mapper and reprocess tests.
- [ ] On the phone, before taking any new reading: after installing, the Today BP card and the Health BP chart
      show readings from 11 to 14 Oct that were missing before. Compare against what the user remembers measuring.
- [ ] Take a manual BP reading on the watch, then Sync now. It appears on Today within one sync, and in Health
      Connect within a few minutes.
- [ ] If B4 was done: the reference set in the app reads back the same after reopening the screen.
- [ ] Update `docs/implementation-plan.md` §12: under Phase 11's follow-ups (or a new "Fixes on `sync-fixes`" note),
      record the sync hardening, the BP measure decode and the replay. Then open the PR. The user merges it.

---

## Part C: Watchfaces (new Phase 12, branch `phase-12-watchfaces` cut from `main` after `sync-fixes` merges)

### C.1 Decisions the user must make first (STOP here and ask; don't pick for them)

| # | Decision | Recommendation |
|---|---|---|
| D1 | Allow NexWatch to make network requests (the `INTERNET` permission) to the vendor server `fitcloud.hetangsmart.com` over HTTPS. Each request sends the watch's hardware info string, LCD id and tool version. Today the app makes no network requests at all | Allow it, **only** from the watchface screen and only when the user taps something. Cache every download |
| D2 | Vendor `ext-realtek-dfu-1.0.4.aar` from the same commit (`7409d0f`) as the SDK, with checksums and verification metadata | Yes. It's required: without it nothing can be pushed |
| D3 | Accept that a push **replaces the current face** (store dial 114003) because there's only one writable slot | Yes, with a built-in "Restore original face" that re-pushes 114003 from the vendor catalogue |
| D4 | Scope: (a) photo or custom background face only; (b) also browse and install store faces | (a) plus restoring 114003. (b) later |
| D5 | A design for the watchface screens. `CLAUDE.md` needs a polished HTML file under `docs/design/` before UI work | Ask the user to produce `docs/design/NexWatch Watchfaces (polished).html` (Batch 5 area), or agree to a minimal functional screen built from existing components only, to be restyled later |

Write the answers into §12 Phase 12 before writing any code.

### C.2 Add Phase 12 to §12 and update §10.1

The row is already in §12 as `Not started` (added with this plan). When you start, set it to `In progress`. Add
the exit criteria from C.9 as a checklist. Update §10.1 (security) to say the app now contacts the vendor server for
watchfaces only, over HTTPS, on user action. Update `third_party/maven/README.md`'s "Not vendored" section.

### C.3 Vendor the Realtek DFU extension

1. Download `libs/ext/ext-realtek-dfu-1.0.4.aar` from `https://github.com/htangsmart/FitCloudPro-SDK-Android` at
   commit `7409d0f65d9a786c56610e3734e9e1c51a5b5a29` (use `gh api` or raw.githubusercontent over HTTPS), into its own
   empty temp directory.
2. Unzip it and inspect it (`classes.jar`, `AndroidManifest.xml`, any `proguard.txt`). Write down any manifest
   permissions it merges in. Write a hand-made POM (`com.topstep.wearkit:ext-realtek-dfu:1.0.4`, no dependencies),
   in the same style as the existing two under `third_party/maven/`.
3. Add it to `gradle/libs.versions.toml` and to `:core:watch-fitcloud` **only**. Regenerate checksums:
   `./gradlew --write-verification-metadata sha256 assembleDebug assembleRelease test lint`. Update the provenance
   table in `third_party/maven/README.md` with the SHA-256.
4. Add keep rules for `com.realsil.sdk.**` to `:core:watch-fitcloud`'s consumer ProGuard file if the AAR has none,
   and confirm `assembleRelease` still works.
5. Check that `androidx.palette` is not required: `javap -v` on `DialDrawer`/`DialWriter` showed no reference to it.
   If a `NoClassDefFoundError` shows up at runtime, add `androidx.palette:palette-ktx` (latest stable) to
   `:core:watch-fitcloud`.

### C.4 Watch API (pure Kotlin, `:core:watch-api`)

New file `WatchFaces.kt`:
```kotlin
data class WatchFaceInfo(
    val round: Boolean,
    val widthPx: Int,
    val heightPx: Int,
    val lcd: Int,
    val toolVersion: String,
    val hardwareInfo: String,
    val supportsCustom: Boolean,      // Feature.GUI && Feature.DIAL_CUSTOM
    val slots: List<WatchFaceSlot>,
    val currentSlot: Int,
)

data class WatchFaceSlot(
    val index: Int,
    val dialNum: Int,
    val pushable: Boolean,
    val binFlag: Byte,
    val capacityBytes: Int,
)

enum class WatchFaceAnchor { TOP, BOTTOM, LEFT, RIGHT }  // where the clock style sits; maps to DialDrawer.Position

sealed interface WatchFacePush {
    data object Preparing : WatchFacePush
    data class Sending(val percent: Int) : WatchFacePush
    data object Done : WatchFacePush
}

class WatchFacePushException(val reason: Reason, message: String) : RuntimeException(message) {
    enum class Reason { LOW_BATTERY, DISCONNECTED, FILE_INVALID, TOO_LARGE, OTHER }
}
```
Additions to `WatchClient`, which file paths keep pure Kotlin. Note that `android.graphics.Bitmap` must not appear in
`:core:watch-api`:
```kotlin
suspend fun readWatchFaceInfo(): WatchFaceInfo
/** Builds a pushable .bin from a vendor GUI template and two images; returns the output file path. */
suspend fun composeCustomWatchFace(templateBinPath: String, backgroundImagePath: String, styleImagePath: String,
                                   styleBaseWidthPx: Int, anchor: WatchFaceAnchor, outputDir: String): String
/** Holds the BLE bus for the whole push (§4.3). */
fun pushWatchFace(binPath: String, slot: WatchFaceSlot): Flow<WatchFacePush>
suspend fun selectWatchFaceStyle(slotIndex: Int, styleIndex: Int)
```
`FakeWatchClient` implements all four: a fixed round 466 info with one pushable slot, compose copies the template to
the output, push emits Preparing → Sending(0..100 step 20) → Done with small delays, and it honours a
`nextPushError` hook.

### C.5 FitCloud implementation (`:core:watch-fitcloud`)

- `readWatchFaceInfo()`: `command("dialInfo") { … }` with `requestDialPushInfo().await()` and
  `configFeature().getDeviceInfo()`. `hardwareInfo = deviceInfo.toString()` (that's what the vendor sample sends).
  If `shape == null`, throw `WatchFacePushException(OTHER, "watch has no pushable screen")`. Put the mapping in a
  new `FitCloudWatchFaceMappers.kt`, with unit tests built from SDK constructors (`FcDialSpace`, `FcShape`).
- `composeCustomWatchFace()`: run on `Dispatchers.IO`, **outside** the mutex (it's file work, not BLE). Decode the
  two images with `BitmapFactory`, then build the background, preview and `DialWriter` exactly as in the table in
  0.3, with `isGui = true`, `setCopyFile(File(outputDir, "custom_${System.currentTimeMillis()}.bin"))`, and both
  auto-scale flags set. Return `execute().absolutePath`. Map `DialWriter.WriterException` →
  `WatchFacePushException(FILE_INVALID, …)`. Recycle bitmaps in `finally`.
- `pushWatchFace()`: `flow { mutex.withLock { requireReady(); … } }`. Create
  `connector.newDfuManager(true, File(context.cacheDir, "dfu"))`. Collect `observerStateProgress().asFlow()` in a
  child coroutine and map it (`PREPARE_FILE`/`PREPARE_DFU` → Preparing, `DFU_ING` → Sending(progress)). Then
  `start(DfuType.DIAL, Uri.fromFile(File(binPath)), slot.binFlag, false, SilentMode.DEFAULT).await()`. Use the
  overload that matches the javap signature `start(DfuType, Uri, byte, boolean, SilentMode)`. Check what the boolean
  means in the SDK docs under `document/` in the vendor repo, and record it in a comment. Then emit `Done`. Use
  `withOperationTimeout("watchfacePush", 5.minutes)`. Always call `release()` in `finally`. Map `FcDfuException`
  error codes to `Reason` (low battery, disconnect/abort, file errors) and leave everything else as `OTHER`.
  - **The watch disconnects and reconnects after a DFU.** Don't write reconnect logic. `FcConnector` does it.
- `selectWatchFaceStyle()`: wait (`withTimeout(90.seconds)`) for `state.first { it is WatchState.Ready }`, then
  `command("dialStyle") { settingsFeature().setDialComponent(slotIndex, byteArrayOf(styleIndex.toByte())).await() }`.
- While a push holds the mutex, `WatchSync` will block on it. That's intended: the sync waits and doesn't fail.
  Check that the A2 guard timeout (12 min) is longer than the push cap (5 min) plus a sync, or have `WatchSync` skip
  when a push is running. The simplest version is a `StateFlow<Boolean> pushing` on the client, checked in
  `HealthSyncCoordinator` the same way it checks `Ready`.

### C.6 Catalogue and cache (`:core:data`, new package `watchface`)

- `WatchFaceCatalog`: uses `HttpsURLConnection`, so no new HTTP library. Use form-encoded POSTs to
  `https://fitcloud.hetangsmart.com` + `/public/dial/customgui` and `/public/dial/get`, and parse the JSON with
  kotlinx-serialization (`ignoreUnknownKeys = true`). Write the response DTOs from the vendor sample's
  `DialPacketComplexBean`; fetch that file from the vendor repo to get the exact field names. Never use `http://`.
  Use 15 s connect and read timeouts.
- `WatchFaceFiles`: downloads `binUrl` and the style/preview images into `filesDir/watchfaces/<sha-of-url>`. Check
  the size of the downloaded bin against `binSize`, rewrite any `http://` URL to `https://`, and fail if the host
  isn't the vendor's (or its CDN, whichever the first real response uses; record the host in a constant with a
  comment). Cached files are reused, so the network is used once per template.
- `WatchFaceRepository` (what the ViewModel uses):
  `info()`, `customTemplate(info)` → `(templateBin, styles: List<StylePreview>, defaultBackground)`,
  `buildAndPush(backgroundUri, styleIndex, anchor)` → `Flow<WatchFacePush>`, and
  `restoreOriginal(dialNum = 114003)`. Persist the **original** dial number the first time `info()` is read
  (DataStore), so restore still works after a custom push has replaced it.
- Copy a picked photo (`content://` URI) into `cacheDir` before handing a path to the client.
- `app/src/main/AndroidManifest.xml`: add `<uses-permission android:name="android.permission.INTERNET" />`
  (only if D1 = yes).
- Tests: DTO parsing from a captured JSON fixture (save the first real response, scrubbed, under
  `docs/recon/fixtures/dial_customgui.json`), cache-hit behaviour, rejection of non-HTTPS and foreign hosts, and the
  repository with `FakeWatchClient` (progress order and error mapping).

### C.7 UI (`:feature:watch`)

Follow the D5 design. Whatever the design, the flow is:
1. A Watch tab row "Watch face" opens `WatchFaceScreen` (a type-safe route in the watch nav graph).
2. The screen shows a round preview (466 px scaled) of the background with the selected clock style anchored at
   the chosen position. Controls: "Choose photo" (system photo picker, `PickVisualMedia`, no storage permission),
   the style carousel from the template, and the position (Top, Bottom, Left, Right).
3. "Send to watch" opens a confirmation sheet: "This replaces the face on your watch. You can restore the original
   later." Disable it under 30% battery (from `WatchState.Ready.battery`) and say why.
4. Progress: Preparing → Sending N% → Done, with a looping Part C animation. Keep the screen on during the push only
   (`Modifier.keepScreenOn`/`FLAG_KEEP_SCREEN_ON` scoped to the composable, **not** a wakelock). Errors map to plain
   sentences per `Reason`.
5. "Restore original face" row at the bottom.
6. Colours from `WatchTheme.colors`/`MaterialTheme` only, Midnight text on primary buttons, and Previews for idle,
   pushing, error and disconnected states.

### C.8 First push: prove the pipeline without changing anything

Before the custom flow is used, **re-push the face that's already on the watch** (114003) through
`restoreOriginal()`. If that succeeds, DFU, the vendored extension, the catalogue and the reconnect all work, and the
user sees no change. Only then try a custom face. Record the result in §12 Phase 12.

### C.9 Phase 12 exit criteria

- [ ] D1 to D5 answered and recorded in §12.
- [ ] `ext-realtek-dfu` vendored with checksums. `assembleDebug assembleRelease test lint` pass.
- [ ] Re-pushing 114003 succeeds on the real watch, and the watch reconnects without the app doing anything.
- [ ] A custom face from a phone photo, with a chosen style and position, appears on the watch and survives a watch
      reboot.
- [ ] Restore original works after a custom push.
- [ ] A push started under 30% battery is refused in the UI. A push interrupted by turning Bluetooth off fails with
      a clear message, and a retry succeeds.
- [ ] No network request is made unless the user is on the watchface screen (check with PCAPdroid or logcat).
- [ ] §10.1, §12 and `third_party/maven/README.md` updated. The user reviews and merges.

---

## Hand-off checklist for the implementing agent

1. Commit the pending Manrope work on `sync-fixes` on its own.
2. A0 → A1 → A2 → A3 → A4 → A5. Commit after each task (Conventional Commits, for example
   `fix(sync): time out a stalled watch sync`).
3. B1 → B2 → B3 → (ask about B4) → B5. Update §12. Open the PR. **Don't merge.**
4. Stop. Ask the user the C.1 questions. Start Part C only after `sync-fixes` is merged and the answers are written
   into §12.
