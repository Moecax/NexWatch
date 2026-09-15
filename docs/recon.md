# M0 Recon — GTR 3 Pro against the FitCloudPro SDK

Status: **in progress**. This file is filled in by hand, on your machine,
against the real watch — see `docs/implementation-plan.md` §12 Phase 3 for
why Claude Code can't do this part unattended.

## 0. Get something talking to the watch

The SDK repo is https://github.com/htangsmart/FitCloudPro-SDK-Android
(the two AARs already vendored into `third_party/maven/` for NexWatch itself
came from this repo's `libs/` folder — see `third_party/maven/README.md`).

**What actually worked**: the vendor's own `sample/` app failed to build in
the sandbox (a flaky KSP plugin resolution against their mirrors — not a
real network problem, just not worth fighting). Instead, a throwaway
`recon-harness/` module was added locally (not committed — added to
`settings.gradle.kts` only when in use) that calls the vendored SDK
directly: Scan / Connect (BIND) / Connect (LOGIN) / Sync buttons, logging
everything to a scrollable TextView and logcat tag `RECON`. It already
confirmed a real connection and pulled the capability list below.

To keep using it: re-add `include(":recon-harness")` to
`settings.gradle.kts`, `./gradlew :recon-harness:assembleDebug`, install,
launch. **Use "Connect (LOGIN)" once the watch has already been bound** —
BIND wipes the watch's accumulated data, which is the opposite of what you
want once steps/heart-rate/sleep have built up. Delete the module and
revert the `settings.gradle.kts` line once `docs/recon.md` is complete.

If you'd rather have the full vendor UI (device-info screens, settings
pages, etc.), the fallback is still:

1. Clone it somewhere outside this repo: `git clone https://github.com/htangsmart/FitCloudPro-SDK-Android.git`
2. Open `sample/` (not the repo root) in Android Studio as its own project.
   It has its own `gradlew`/`settings.gradle.kts` and pulls from the vendor's
   own mirrors (aliyun, jitpack, their own Maven server) — that's fine, it's
   *their* build, not NexWatch's, and none of that touches this repo.
3. Build and install `:app` on your phone. Android Studio's toolchain is
   more likely to resolve their mirrors cleanly than the sandbox was.
4. Uninstall or force-stop FitCloudPro first (§13 risk: it competes for the
   watch and causes `FcAuthException` flapping).
5. Pair the sample app with the GTR 3 Pro.

## 1. Capability list

Captured via `recon-harness` against a real GTR 3 Pro (address
`C1:A1:B2:29:7A:0D`), BIND-connected 2026-09-15. Raw `FcDeviceInfo` bytes:
`00000000492600480BD7000027F300005550511500000000000001052310241120527040BBCF`.

The harness only checked ~20 of the SDK's ~160 `FcDeviceInfo.Feature`
constants (the ones this table and §14 care about) — "Not supported" below
means "not in that checked subset," not "confirmed absent from all ~160."
Re-run with more constants in `MainActivity.FEATURE_NAMES` if something
else needs confirming later.

| Feature | Supported? | Notes |
|---|---|---|
| Heart rate | Yes | `Feature.HEART_RATE` |
| SpO2 | Yes | `Feature.OXYGEN` |
| Blood pressure | Yes | `Feature.BLOOD_PRESSURE` |
| Temperature | **No** | `Feature.TEMPERATURE` not reported |
| Stress | **No** | `Feature.PRESSURE` not reported (this SDK's "pressure" = stress, not blood pressure) |
| Sport / workout | Yes | `Feature.SPORT` |
| GPS | **No** | Neither `Feature.GPS` nor `Feature.GNSS_GPS` reported — workouts have no route |
| ECG | **No** | Neither `Feature.ECG` nor `Feature.TI_ECG` reported |
| HRV | **No** | `Feature.HRV` not reported |
| Sleep | Yes | `Feature.SLEEP`; `Feature.SLEEP_REM` **not** reported — no REM stage, and `Feature.SLEEP_SCORE`/`CONTACTS_100` weren't checked (SDK-internal, see harness comment) |
| Advanced reminders | Not checked | not in the harness's checked subset yet |
| Weather push | Yes | `Feature.WEATHER` |
| Contacts | Yes | `Feature.CONTACTS`; max count not checked (`CONTACTS_100` is SDK-internal, inaccessible from outside the SDK's own module) |
| DND | Yes | `Feature.DND` |
| Find phone | Yes | `Feature.FIND_DEVICE` |
| Extra step data | Yes | `Feature.STEP_EXTRA` |
| Precise battery level | Not checked | in `FEATURE_NAMES` but result not recorded yet — re-run |
| Firmware version | Not read yet | `FcDeviceInfo.app`/`.project`/`.flash`/`.patch` are public at the JVM level but Kotlin-`internal` to the SDK's own module, so the external harness can't call them directly. `FcExtraFirmwareInfo` (`configFeature().getExtraFirmwareInfo()`) is the accessible path — not wired into the harness yet. |
| Other capabilities worth noting | — | This watch has no GPS, ECG, HRV, temperature or stress sensors — schema-wise, those canonical tables (§5.3 `blood_pressure` is supported but `temperature`/`stress` tables and `workout_route`/GPS won't get real data from this unit) |

## 2. Fixture payloads

For each data type NexWatch will ingest, capture one real `syncData()`
payload (or the closest equivalent the sample app exposes — logcat around
the sync call is usually enough) and save it under
`docs/recon/fixtures/<type>.json` (scrub anything personally identifying
first — real phone numbers, contact names, precise GPS traces).

- [x] `FcTodayTotalData` — captured 2026-09-15 16:06 with a real non-zero
      value (step=14, distance=9m, calorie=393) via **Connect (LOGIN)** +
      **Sync**. See `docs/recon/fixtures/today_total.txt` — flags a possible
      units mismatch against the STEP bucket's distance/calorie fields that
      needs confirming with a larger sample.
- [x] `FcStepData` (steps bucket) — captured, one bucket
      (`docs/recon/fixtures/step_bucket.txt`). Only one bucket synced so far
      since the watch had just started accumulating steps after the earlier
      BIND wipe; re-sync later for a multi-bucket sample if the ingestion
      logic needs to see bucket boundaries.
- [ ] Heart rate sample — synced empty this round (no measurements
      accumulated yet); needs more wear time for the watch's HR
      auto-measurement interval to produce a reading
- [ ] SpO2 sample — synced empty this round, same reason as heart rate
- [ ] Blood pressure sample — synced empty this round, same reason (device
      supports it, §1)
- [x] Temperature sample — **N/A**, not supported on this unit (§1)
- [x] Stress sample — **N/A**, not supported on this unit (§1)
- [ ] Sleep night (`FcSleepItem` / sleep summary) — needs an actual night of
      sleep with the watch on; ideally two payloads for the *same* night, to
      confirm the "delivered more than once" behavior §2 describes
- [ ] `FcSportData` (one workout) — needs a recorded workout
- [x] `FcGpsData` — **N/A**, no GPS on this unit (§1)

## 3. §14 open questions

Answer each against the real watch:

1. **Are SDK timestamps true UTC epochs, or watch-local times encoded as
   epochs?** Set the watch to a timezone that differs from the phone's,
   take a reading, and check whether the SDK's epoch matches real UTC or is
   offset by the watch's local time.
   - Answer:
2. **What heart-rate and SpO2 monitoring intervals does
   `FcHealthMonitorConfig` allow, and what does each cost in watch battery?**
   - Answer:
3. **Does the GTR 3 Pro report temperature, stress, blood pressure, GPS or
   ECG through `FcDeviceInfo`?** (duplicates the capability table above —
   record here too since this is the §14 answer that gates the schema)
   - Answer: Blood pressure yes; temperature, stress (`PRESSURE`), GPS
     (`GPS`/`GNSS_GPS`) and ECG (`ECG`/`TI_ECG`) all no, per §1. This means
     `docs/implementation-plan.md` §5.3's `temperature`, `stress`,
     `workout_route` tables will stay empty for this unit — worth confirming
     in Phase 6 whether to still build the ingestion path for them (future
     watch model) or skip until needed.
4. **Which Realtek DFU extension matches the 8763E chip, and does the
   sample app's firmware update work on it?**
   - Answer:
5. **Does the sample app use connection-priority changes during bulk
   transfers, and does the SDK expose them?**
   - Answer:
6. **Which permissions does the `DEVICE_PROFILE_WATCH` association actually
   grant on your phone?** Pair via `CompanionDeviceManager.associate()` in a
   throwaway test (or check the sample app if it already does this) and
   record exactly which grants come bundled versus which still need a
   separate runtime request.
   - Answer:

## 4. 24-hour FitCloudPro baseline

Before uninstalling FitCloudPro, run it normally (watch connected) for 24
hours and record:

- Battery attributed to FitCloudPro (Settings → Battery):
- Memory footprint (`adb shell dumpsys meminfo com.topstep.fitcloud...`
  — fill in the actual package name):
- Any gaps you notice in FitCloudPro's own data timeline:

This is the number Phase 5's soak test (§9.1 budgets) has to beat.

## 5. Anything else worth flagging

Free-form notes: quirks, undocumented behavior, anything that contradicts
what `docs/implementation-plan.md` §2 assumes from the wiki.
