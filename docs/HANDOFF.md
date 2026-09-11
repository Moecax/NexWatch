# Handoff: building NexWatch with Claude Code

Work through these sessions in order. Each has a prompt to paste into Claude Code and a "done when" check. Start a fresh Claude Code session for each one, so the context stays focused. `CLAUDE.md` loads automatically.

**Before Session 1:** install Android Studio with the latest stable SDK and JDK 17+, then open a terminal in this folder and start Claude Code. Commit the scaffold to git first, so every session's changes are reviewable.

---

## Session 1: Gradle project and build setup

```
Read CLAUDE.md and docs/implementation-plan.md §3. Turn this scaffold into a
building Android project:

1. Create gradle/libs.versions.toml using the latest STABLE versions of AGP,
   Kotlin, KSP, Compose BOM, Hilt, Room, Navigation Compose, Lifecycle,
   WorkManager, DataStore, kotlinx-serialization, kotlinx-coroutines (incl.
   -rx3), Timber and LeakCanary. Check they are mutually compatible (note that
   AGP 9 has built-in Kotlin).
2. Create build-logic/ with convention plugins: nexwatch.android.application,
   nexwatch.android.library, nexwatch.android.library.compose,
   nexwatch.android.feature, nexwatch.android.hilt, nexwatch.android.room,
   nexwatch.jvm.library. Enable includeBuild("build-logic") in settings.
3. Add the Gradle wrapper and a build.gradle.kts for every module in
   settings.gradle.kts, applying the dependency rules in CLAUDE.md.
   :core:model, :core:watch-api and :core:sync-api are pure JVM modules.
4. Keep the seed files that already exist and make them compile.
5. :app gets a MainActivity with edge-to-edge, WatchAppTheme, and a
   NavHost with a 4-tab bottom bar (Today, Health, Watch, Data), each a
   placeholder screen from its feature module.
6. Add a .gitignore, and set up R8 for release with minify enabled.

Done when ./gradlew assembleDebug assembleRelease test all pass.
```

**Done when:** the app installs, shows the four placeholder tabs on the premium gradient background, and the release build succeeds.

---

## Session 2: FakeWatchClient and app state

```
Implement :core:watch-fake/FakeWatchClient per the WatchClient contract in
:core:watch-api. It must simulate: scanning (emits 1-2 fake watches over ~3s),
bind/login with realistic delays, state transitions including Waiting,
BluetoothOff and AuthFailed, battery level, a sync that emits progress, and
live heart rate. Expose a debug control so each state can be forced.
Bind WatchClient to FakeWatchClient in :app via Hilt for now.
Add a DataStore-backed WatchIdentityStore in :core:data holding userId
(random UUID generated once), bound watch address, isBound, and UserProfile.
Write unit tests for the fake and the identity store.
```

**Done when:** tests pass, and the app can be driven through every watch state from a debug menu.

---

## Session 3: Onboarding and pairing UI from Claude Design

```
Run /design-login, then import this Claude Design project:
https://claude.ai/design/p/8c5e0b74-a30b-40a0-b7ba-388b35763c0d?file=NexWatch+B1+Onboarding+%26+Pairing.dc.html

Read "NexWatch B1 Onboarding & Pairing.dc.html" and the files it imports
(android-frame.jsx, support.js, uploads/Smart watch app logo.png).

First, compare the design's colors, typography, spacing, radii and components
with :core:designsystem. Update the theme and add any missing tokens or
shared components there, following the design-system rules in CLAUDE.md.
Report what you changed.

Then implement the B1 flow in :feature:onboarding as idiomatic Compose
(don't port HTML structure): Welcome, Profile, Permissions, Find your watch,
Pair confirmation (data-wipe warning with "I understand" checkbox), Pairing
progress, Keep it running. Use one OnboardingViewModel with a state machine,
driven by WatchClient (FakeWatchClient for now). Wire real runtime
permission requests (BLUETOOTH_SCAN/CONNECT, POST_NOTIFICATIONS,
READ_PHONE_STATE, notification-listener settings intent, battery-optimisation
exemption intent). Add the logo as an adaptive launcher icon and use it on
the Welcome screen. Add @Preview for every screen and state.
Onboarding shows only when WatchIdentityStore.isBound is false.
```

**Done when:** the whole flow works end to end against the fake, every screen matches the design, and each screen has previews for its states.

Later design batches (Today, Health, Watch and so on) follow the same pattern: import the batch, reconcile the tokens, then implement it in its feature module.

---

## Session 4 (you, with the watch): Recon and vendoring the SDK (Milestone M0)

Claude Code can't do this part alone, because it needs the physical watch. Follow `third_party/maven/README.md` to vendor the SDK. Then run the SDK's sample app on your phone and record the results in `docs/recon.md`: the watch's supported features, raw payload samples for each data type, and the answers to the questions in `docs/implementation-plan.md` §14. Also record a 24-hour battery and memory baseline with FitCloudPro.

You can ask Claude Code to help analyse the sample app's code while you do this:

```
Read the FitCloud sample project in third_party/fitcloud-sample/. Summarise
how it initialises FcSDK, binds/logs in, syncs data and handles telephony and
notifications, and list the exact manifest permissions it declares. Write the
summary to docs/sdk-notes.md.
```

---

## Session 5: FitCloudWatchClient (Milestone M1)

```
Enable :core:watch-fitcloud in settings.gradle.kts. Depend on the vendored
SDK from third_party/maven with Gradle dependency verification. Implement
FitCloudWatchClient per docs/implementation-plan.md §4 and docs/sdk-notes.md:
FcSDK init in Application, state mapping to WatchState, Rx→Flow adapters,
command Mutex with timeouts, capability detection, guarded bind vs login.
No custom reconnect logic. Switch the Hilt binding to FitCloudWatchClient
for release builds; debug builds get a toggle between fake and real.
Add R8 keep rules for the SDK and confirm assembleRelease works.
```

**Done when:** the watch pairs once, then reconnects in LOGIN mode after an app restart and after a phone reboot.

---

## Session 6: Always-on service (Milestone M2)

```
Implement docs/implementation-plan.md §8 in :core:service: WatchConnectionService
(foregroundServiceType=connectedDevice, START_STICKY, low-importance status
notification updated only on change, debounced sync on Ready), companion device
association with DEVICE_PROFILE_WATCH and CompanionDeviceService presence on API 31+,
BootReceiver, NotificationForwarder extending the SDK's AbsNotificationListenerService
with the full filter pipeline from §8.5, telephony via FcBuiltInFeatures, and
find-phone / camera handling. Add the Diagnostics screen from design Batch 8.
```

**Done when:** a 48-hour soak passes. Notifications and calls arrive with the app swiped away, and battery and memory use stay within the §9.1 budgets.

---

## Sessions 7 onward

This file's session prompts stop here — it was the bootstrap script for the project, not the ongoing plan. From here, follow `docs/implementation-plan.md` §12, which is now the live, kept-up-to-date source of truth for phase order, scope and status (`CLAUDE.md` → Workflow explains the branch-per-phase process). Sessions 1–3 above map to Phases 0–2; §12 continues with Phase 3 (Recon) onward, one branch per phase, merged to `main` before the next one starts.

Use this prompt as the template for any phase:

```
Implement Phase <n> from docs/implementation-plan.md §12, following the
referenced sections. Plan first and show me the plan before writing code.
Done when every exit criterion for the phase is checked off and all tests pass.
```
