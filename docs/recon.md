# M0 Recon — GTR 3 Pro against the FitCloudPro SDK

Status: **in progress**. This file is filled in by hand, on your machine,
against the real watch — see `docs/implementation-plan.md` §12 Phase 3 for
why Claude Code can't do this part unattended.

## 0. Get the vendor's sample app running

The SDK repo is https://github.com/htangsmart/FitCloudPro-SDK-Android
(the two AARs already vendored into `third_party/maven/` for NexWatch itself
came from this repo's `libs/` folder — see `third_party/maven/README.md`).

1. Clone it somewhere outside this repo: `git clone https://github.com/htangsmart/FitCloudPro-SDK-Android.git`
2. Open `sample/` (not the repo root) in Android Studio as its own project.
   It has its own `gradlew`/`settings.gradle.kts` and pulls from the vendor's
   own mirrors (aliyun, jitpack, their own Maven server) — that's fine, it's
   *their* build, not NexWatch's, and none of that touches this repo.
3. Build and install `:app` on your phone. If a plugin/dependency fails to
   resolve, it's almost always a flaky mirror — retry, or add
   `google()`/`mavenCentral()` ahead of the vendor mirrors in
   `sample/settings.gradle.kts` locally (don't commit that; it's a throwaway
   clone).
4. Uninstall or force-stop FitCloudPro first (§13 risk: it competes for the
   watch and causes `FcAuthException` flapping).
5. Pair the sample app with the GTR 3 Pro.

## 1. Capability list

Read `FcDeviceInfo` after connecting (the sample app's device-info screen
shows this). Fill in what `isSupport(Feature.X)` reports:

| Feature | Supported? | Notes |
|---|---|---|
| Heart rate | | |
| SpO2 | | |
| Blood pressure | | |
| Temperature | | |
| Stress | | |
| Sport / workout | | |
| GPS | | |
| Advanced reminders | | |
| Weather push | | |
| Contacts (max count) | | |
| Firmware version | | |
| Other capabilities worth noting | | |

## 2. Fixture payloads

For each data type NexWatch will ingest, capture one real `syncData()`
payload (or the closest equivalent the sample app exposes — logcat around
the sync call is usually enough) and save it under
`docs/recon/fixtures/<type>.json` (scrub anything personally identifying
first — real phone numbers, contact names, precise GPS traces).

- [ ] `FcStepData` (steps bucket)
- [ ] `FcTodayTotalData`
- [ ] Heart rate sample
- [ ] SpO2 sample (if supported)
- [ ] Blood pressure sample (if supported)
- [ ] Temperature sample (if supported)
- [ ] Stress sample (if supported)
- [ ] Sleep night (`FcSleepItem` / sleep summary) — ideally two payloads for
      the *same* night, to confirm the "delivered more than once" behavior
      §2 describes
- [ ] `FcSportData` (one workout)
- [ ] `FcGpsData` for that same workout, linked by `sportId`

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
   - Answer:
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
