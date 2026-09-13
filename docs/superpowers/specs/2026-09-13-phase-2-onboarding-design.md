# Phase 2 — Onboarding design system & UI: design spec

Branch: `phase-2-onboarding`. Implements `docs/implementation-plan.md` §12 Phase 2 exit criteria against `docs/design-prompt.md` and `docs/design/NexWatch B1 Onboarding and Pairing (polished).html`.

## Scope

Build the Batch 1 onboarding/pairing flow (Welcome, Profile, Permissions, Find your watch, Pair confirmation, Pairing progress, Keep it running) as idiomatic Compose in a new `:feature:onboarding` module, driven by one `OnboardingViewModel` over the `WatchClient` interface (bound to `FakeWatchClient` for this phase), and gate it behind `WatchIdentityStore.isBound`. Add whatever `:core:designsystem` tokens/components the flow needs, first.

Out of scope: real Bluetooth discovery (Companion Device Manager lands in Phase 5), the FitCloud SDK (Phase 4), and any screen outside Batch 1.

## 1. Module & dependencies

New module `:feature:onboarding`:
- Convention plugins: `nexwatch.android.library`, `nexwatch.android.compose`, `nexwatch.android.hilt`.
- Depends on `:core:designsystem`, `:core:watch-api`, `:core:data`, `:core:model`, `:core:common`.
- Does **not** depend on `:core:watch-fake` or `:core:watch-fitcloud` — Hilt resolves whichever `WatchClient` `:app` has bound (still `FakeWatchClient` per Phase 1's `WatchModule`).
- Declares its own runtime-permission entries in its `AndroidManifest.xml`; they merge into `:app`'s manifest.
- Registered in `settings.gradle.kts` as `include(":feature:onboarding")`.

## 2. Design-system additions (`:core:designsystem`)

Add before touching feature code, since screens must not hardcode values:

- **`WatchMotion`** (new file, `theme/Motion.kt`): the three Part C easing curves as `CubicBezierEasing` (`easeOutStrong = cubic-bezier(.23,1,.32,1)`, `easeInOutStrong = cubic-bezier(.77,0,.175,1)`, `easeDrawer = cubic-bezier(.32,.72,0,1)`), plus duration constants: `pressDurationMs = 150`, `entranceStaggerStepMs = 60`, `entranceItemDurationMs = 500`, `sheetDurationMs = 350`.
- **`WatchShapes`** (new file, `theme/Shape.kt`): `cardRadius = 22.dp`, `controlRadius = 14.dp`, `pillRadius` (percent-based `CircleShape`/`RoundedCornerShape(50)`).
- **`Modifier.pressScale()`** (new file, `component/PressScale.kt`): scales to 0.97 on press using `WatchMotion.easeOutStrong` over `pressDurationMs`, restores on release/cancel. Every pressable component in this phase uses it.
- **Staggered entrance** (new file, `component/StaggeredEntrance.kt`): a composable/modifier that fades + translates ~10dp + clears a few px of blur on first composition only (`LaunchedEffect(Unit)`), 60ms step per child, respecting `LocalInspectionMode` (skip animation in previews) and honoring reduced-motion (drop translate/blur, keep opacity) via `LocalAccessibilityManager`/`Settings.Global.ANIMATOR_DURATION_SCALE` — if there's no clean way to read system reduced-motion in Compose without extra plumbing, fall back to always animating and note the gap rather than block the phase on it.
- **Primitives** (new files under `component/`): `PrimaryButton`, `SecondaryButton` (on-primary text is Midnight per CLAUDE.md, both use `pressScale()`), `SegmentedControl`, `NumberStepper` (cross-fades the value through blur per Part C "value changes"), `StatusChip` (icon + label + status color, never color alone).

Onboarding-only composites (permission row, radar scanner + device rows, progress-step list, destructive warning card, watch hero render drawn with `Canvas`) live in `:feature:onboarding`, not designsystem, since they're single-flow.

## 3. State machine

```kotlin
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object Profile : OnboardingStep
    data object Permissions : OnboardingStep
    data object FindWatch : OnboardingStep
    data object PairConfirm : OnboardingStep
    data class Pairing(val phase: PairingPhase) : OnboardingStep
    data object KeepRunning : OnboardingStep
}
```

`OnboardingViewModel` (Hilt, `:feature:onboarding`) holds one `StateFlow<OnboardingUiState>` combining the current step, in-progress `UserProfile` fields, per-permission grant state, the simulated discovered-device list, and pairing progress. Screens are stateless `XScreen(state, onEvent)` composables with thin route wrappers collecting the ViewModel — this is what makes every screen/state previewable without DI.

**Scanning is simulated inside the ViewModel**, not through `WatchClient` — the interface has no discovery method, and real discovery is Companion Device Manager (Phase 5). `FindWatch` produces a small fixed/randomized list of fake devices; picking one supplies the address for `bind()`.

Pairing flow: `PairConfirm` (guarded, "I understand" checkbox required) → `Pairing` calls `watchIdentityStore.ensureUserId()`, then `watchClient.bind(address, profile)`, then on success `watchIdentityStore.markBound(address)` and advances to `KeepRunning` on "Continue". This is the only path in the app allowed to call `bind()` — matches the §4.4 guard.

## 4. Permissions screen

Checklist items map to real Android permissions, version-gated:

| Item | Permission(s) | Mechanism |
|---|---|---|
| Nearby devices | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+); none needed below | `rememberLauncherForActivityResult(RequestMultiplePermissions)` |
| Notifications | `POST_NOTIFICATIONS` (API 33+) | same launcher |
| Notification access | n/a — special access | deep-link `ACTION_NOTIFICATION_LISTENER_SETTINGS`; re-checked via `NotificationManagerCompat.getEnabledListenerPackages` on `onResume`/lifecycle observer |
| Phone & contacts | `READ_PHONE_STATE`, `READ_CALL_LOG`, `READ_CONTACTS`, `ANSWER_PHONE_CALLS` | same launcher |
| Location (optional) | `ACCESS_COARSE_LOCATION` | same launcher, skippable |

The progress indicator ("3 of 5 granted") is derived state, not stored separately.

## 5. App-level wiring

`WatchIdentityStore.isBound` gates the whole flow. In `:app`, a small root composable collects `identity` (via `collectAsStateWithLifecycle`) and picks `OnboardingNavHost` (new, in `:feature:onboarding`, owns its own internal `NavHost` for the 7 steps) vs. the existing `NexWatchNavHost`. While the first DataStore emission is pending, show an empty `PremiumBackground` frame rather than flashing the wrong graph.

## 6. Testing

- `OnboardingViewModelTest` (`:feature:onboarding`, JUnit + coroutines-test + Turbine) drives the state machine against `FakeWatchClient`: happy path Welcome→KeepRunning, permission partial-grant states, bind failure surfacing `AuthFailed`.
- `@Preview` for every screen × every documented state (populated, scanning/1 result, scanning/none found, warning, pairing sub-phases, success). Verified visually, not asserted in CI.
- No new instrumented tests planned; flag if reviewers want them.

## Exit criteria (from implementation-plan.md §12, unchanged)

- [ ] `:core:designsystem` carries every token/component the polished reference uses; no screen hardcodes a hex value.
- [ ] The full onboarding flow works end to end against the fake client and matches the polished reference's states and motion.
- [ ] Every screen and state has a `@Preview`.
