# Phase 1 — Fake Watch & App State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `WatchClient` contract in `:core:watch-api`, a `FakeWatchClient` in `:core:watch-fake` that drives every `WatchState` with realistic delays and a debug override, a DataStore-backed `WatchIdentityStore` in `:core:data`, and wire all three into `:app` through Hilt with a debug screen that forces any `WatchState` without touching real Bluetooth.

**Architecture:** `:core:watch-api` stays pure Kotlin and defines the interface plus every domain type it references (`WatchState`, `WatchCapabilities`, `WatchEvent`, `UserProfile`, `SyncProgress`, `RawBatch`, `OutgoingNotification`, `SendResult`, `WatchSettingChange`, `WeatherForecast`, `WatchNotReadyException`) exactly as specified in `docs/implementation-plan.md` §4.2. `FakeWatchClient` in `:core:watch-fake` implements it with `flow {}`/`delay()` simulated timings — no dispatcher abstraction crosses that module boundary, since `:core:watch-fake` may only depend on `:core:watch-api` per `CLAUDE.md`'s module table, and coroutine `delay()` inherits the caller's (or test's) dispatcher for free. A separate `WatchDebugController` interface, also implemented by `FakeWatchClient`, is the only way anything forces a state — it never leaks into the production `WatchClient` contract. `:core:data` gets `WatchIdentityStore`, built on `androidx.datastore:datastore-preferences`, using the `CoroutineDispatchers` abstraction newly added to `:core:common`. Hilt arrives in this phase (`:app`'s tech stack list already names it): a `nexwatch.android.hilt` convention plugin wires KSP + the Hilt Gradle plugin into every module that needs one, `:app` gets `@HiltAndroidApp`/`@AndroidEntryPoint`, and a `WatchModule` binds `WatchClient`/`WatchDebugController` to the same `FakeWatchClient` singleton. The existing "Watch" placeholder tab becomes the real debug screen.

**Tech Stack:** Hilt 2.60.1 (first AGP-9-compatible stable line) with KSP 2.2.10-2.0.2 (matches this repo's Kotlin 2.2.10 exactly), kotlinx-coroutines 1.11.0, `androidx.datastore:datastore-preferences` 1.2.1, `androidx.hilt:hilt-navigation-compose` 1.4.0, Turbine 1.2.1 for Flow assertions in tests.

**Spec:** `docs/implementation-plan.md` §4.2 (`WatchClient` contract), §4.4 (identity and binding), §4.5 (capabilities), §12 Phase 1 row and exit criteria; `CLAUDE.md` module map and dependency-rule table.

## Global Constraints

- Base package `com.nexwatch` (`CLAUDE.md`).
- `:core:watch-fake` may depend on `:core:watch-api` only — no `:core:common`, no other project module (`CLAUDE.md` table). External libraries (Hilt, kotlinx-coroutines) are unaffected by this rule.
- `:core:watch-api` and `:core:sync-api`/`:core:model` are pure Kotlin; `:core:watch-api` may depend on `:core:model` (already wired in Phase 0) but this phase adds no type there, since watch-domain types (`UserProfile`, capabilities, etc.) are not canonical health records.
- Dark theme only, no dynamic color, no hardcoded hex values outside `:core:designsystem` (unchanged from Phase 0).
- **Ruling (scope):** `Clock` is not added to `:core:common` in this phase. Nothing in Phase 1's exit criteria needs a wall-clock timestamp (the `device_event`/`bound_at` row that would use one is a Room concept introduced in Phase 6). Only `CoroutineDispatchers` is added now; `Clock` lands with the phase that first needs it.
- **Ruling (scope):** the debug state-forcing menu reuses the existing "Watch" bottom-nav tab and route (`NexWatchDestination.Watch`) instead of adding a new tab or feature module, since `:feature:*` modules are still out of scope until Phase 2.
- Every module's `build.gradle.kts` must match the dependency table exactly plus this phase's additions — no undeclared dependency, no missing one.
- `./gradlew assembleDebug assembleRelease test lint` must all pass before this phase is done.
- Work happens on branch `phase-1-fake-watch`, cut from `main` (Phase 0 is merged).

---

## Task 0: Branch and mark the phase in progress

**Files:**
- Modify: `docs/implementation-plan.md`

**Interfaces:**
- None.

- [ ] **Step 1: Cut the branch**

Run: `git checkout main && git pull && git checkout -b phase-1-fake-watch`

- [ ] **Step 2: Mark the phase in progress**

In `docs/implementation-plan.md`, change the Phase 1 row:
```markdown
| 1 | Fake watch & app state | `phase-1-fake-watch` | In progress |
```

- [ ] **Step 3: Commit**

```bash
git add docs/implementation-plan.md
git commit -m "docs: mark Phase 1 (Fake watch & app state) in progress"
```

---

## Task 1: Version catalog and the Hilt convention plugin

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/nexwatch.android.hilt.gradle.kts`

**Interfaces:**
- Produces: catalog aliases `libs.hilt.android`, `libs.hilt.compiler`, `libs.androidx.hilt.navigation.compose`, `libs.androidx.hilt.compiler`, `libs.kotlinx.coroutines.core`, `libs.kotlinx.coroutines.android`, `libs.kotlinx.coroutines.test`, `libs.androidx.datastore.preferences`, `libs.turbine`, plugin aliases `libs.plugins.hilt.android`, `libs.plugins.ksp`; plugin ID `nexwatch.android.hilt` — every later task's `build.gradle.kts` applies it.

- [ ] **Step 1: Add the new catalog entries**

Edit `gradle/libs.versions.toml`, adding to each existing table (do not remove any existing entry):

```toml
[versions]
hilt = "2.60.1"
ksp = "2.2.10-2.0.2"
kotlinxCoroutines = "1.11.0"
datastorePreferences = "1.2.1"
androidxHiltNavigationCompose = "1.4.0"
turbine = "1.2.1"

[libraries]
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-android-gradlePlugin = { group = "com.google.dagger", name = "hilt-android-gradle-plugin", version.ref = "hilt" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "androidxHiltNavigationCompose" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "androidxHiltNavigationCompose" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Add the Hilt/KSP plugin classpaths to build-logic**

Edit `build-logic/convention/build.gradle.kts`, adding two `compileOnly` lines:

```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.hilt.android.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}
```

- [ ] **Step 3: Write the Hilt convention plugin**

`build-logic/convention/src/main/kotlin/nexwatch.android.hilt.gradle.kts`:

```kotlin
plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    "implementation"(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
}
```

- [ ] **Step 4: Verify build-logic compiles**

Run: `./gradlew -p build-logic build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build-logic
git commit -m "build: add Hilt/KSP catalog entries and the nexwatch.android.hilt convention plugin"
```

---

## Task 2: `:core:common` — `CoroutineDispatchers`

**Files:**
- Modify: `core/common/build.gradle.kts`
- Delete: `core/common/src/main/kotlin/com/nexwatch/core/common/package-info.kt` (Phase 0 stub, superseded by real content)
- Create: `core/common/src/main/kotlin/com/nexwatch/core/common/CoroutineDispatchers.kt`
- Create: `core/common/src/test/kotlin/com/nexwatch/core/common/DefaultCoroutineDispatchersTest.kt`
- Create: `core/common/src/main/kotlin/com/nexwatch/core/common/di/CommonModule.kt`

**Interfaces:**
- Produces: `interface CoroutineDispatchers { val io: CoroutineDispatcher; val default: CoroutineDispatcher }`, `class DefaultCoroutineDispatchers`, Hilt binding of `CoroutineDispatchers` — Task 5 (`WatchIdentityStore`) injects `CoroutineDispatchers` by interface.

- [ ] **Step 1: Wire the module's dependencies**

`core/common/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: Write the failing test**

`core/common/src/test/kotlin/com/nexwatch/core/common/DefaultCoroutineDispatchersTest.kt`:
```kotlin
package com.nexwatch.core.common

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCoroutineDispatchersTest {

    @Test
    fun `io dispatcher is the IO dispatcher`() {
        val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
        assertEquals(Dispatchers.IO, dispatchers.io)
    }

    @Test
    fun `default dispatcher is the Default dispatcher`() {
        val dispatchers: CoroutineDispatchers = DefaultCoroutineDispatchers()
        assertEquals(Dispatchers.Default, dispatchers.default)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:common:testDebugUnitTest --tests "com.nexwatch.core.common.DefaultCoroutineDispatchersTest"`
Expected: FAIL — `CoroutineDispatchers`/`DefaultCoroutineDispatchers` unresolved.

- [ ] **Step 4: Delete the Phase 0 stub and write the implementation**

```bash
git rm core/common/src/main/kotlin/com/nexwatch/core/common/package-info.kt
```

`core/common/src/main/kotlin/com/nexwatch/core/common/CoroutineDispatchers.kt`:
```kotlin
package com.nexwatch.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Indirection over [Dispatchers] so tests can substitute a [kotlinx.coroutines.test.TestDispatcher]
 * without touching production code (§9.3: heavy work never runs on the caller's dispatcher by accident).
 */
interface CoroutineDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultCoroutineDispatchers @Inject constructor() : CoroutineDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
```

- [ ] **Step 5: Bind it for Hilt**

`core/common/src/main/kotlin/com/nexwatch/core/common/di/CommonModule.kt`:
```kotlin
package com.nexwatch.core.common.di

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.common.DefaultCoroutineDispatchers
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun bindCoroutineDispatchers(impl: DefaultCoroutineDispatchers): CoroutineDispatchers
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:common:testDebugUnitTest --tests "com.nexwatch.core.common.DefaultCoroutineDispatchersTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/common
git commit -m "feat(common): add CoroutineDispatchers abstraction with Hilt binding"
```

---

## Task 3: `:core:watch-api` — domain types and the `WatchClient` contract

**Files:**
- Modify: `core/watch-api/build.gradle.kts`
- Delete: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/package-info.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchState.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchCapabilities.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchEvent.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/UserProfile.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/SyncProgress.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/OutgoingNotification.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchSettingChange.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WeatherForecast.kt`
- Create: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchClient.kt`
- Create: `core/watch-api/src/test/kotlin/com/nexwatch/core/watchapi/WatchNotReadyExceptionTest.kt`

**Interfaces:**
- Produces: every type below, verbatim member names — Task 4 (`FakeWatchClient`) implements `WatchClient` against these exact signatures, Task 6 (`:app`) references `WatchState` subtypes by name in the debug screen.

- [ ] **Step 1: Wire dependencies (coroutines needed for `Flow`/`StateFlow`/`SharedFlow`)**

`core/watch-api/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
```

- [ ] **Step 2: Delete the Phase 0 stub**

```bash
git rm core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/package-info.kt
```

- [ ] **Step 3: `WatchState`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchState.kt`:
```kotlin
package com.nexwatch.core.watchapi

import java.time.Instant

/**
 * Mirrors FcConnectorState (implementation-plan.md §4.2/§2): Unbound and BluetoothOff
 * are app-level states the SDK doesn't model directly, Waiting/Connecting/Ready/AuthFailed
 * map onto PRE_CONNECTING, CONNECTING+PRE_CONNECTED, CONNECTED and FcAuthException.
 */
sealed interface WatchState {
    data object Unbound : WatchState
    data object BluetoothOff : WatchState
    data class Waiting(val nextRetryAt: Instant?) : WatchState
    data object Connecting : WatchState
    data class Ready(val battery: Int?) : WatchState
    data class AuthFailed(val reason: String) : WatchState
}

/**
 * Thrown by every WatchClient command when [WatchState] isn't [WatchState.Ready], so
 * callers fail fast instead of hanging on a disconnected watch (§4.2).
 */
class WatchNotReadyException(val state: WatchState) :
    IllegalStateException("Watch not ready: $state")
```

- [ ] **Step 4: `WatchCapabilities`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchCapabilities.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * Converted from FcDeviceInfo.isSupport(Feature.X) checks after each CONNECTED (§4.5).
 * The UI shows only supported features; the sync pipeline only expects supported types.
 */
data class WatchCapabilities(
    val heartRate: Boolean,
    val spo2: Boolean,
    val bloodPressure: Boolean,
    val temperature: Boolean,
    val stress: Boolean,
    val sport: Boolean,
    val gps: Boolean,
    val advancedReminders: Boolean,
    val weather: Boolean,
    val contactsLimit: Int?,
    val firmwareVersion: String,
)
```

- [ ] **Step 5: `WatchEvent`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchEvent.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * Messages the watch initiates (§8.6). Delivered on WatchClient.events; the app never
 * polls for these.
 */
sealed interface WatchEvent {
    data object FindPhoneRequested : WatchEvent
    data object CameraOpenRequested : WatchEvent
    data object CameraCloseRequested : WatchEvent
    data object HangUpRequested : WatchEvent
}
```

- [ ] **Step 6: `UserProfile`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/UserProfile.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * Feeds on-watch calculations (§2); passed to both bind() and login(), and re-sending it
 * on every login updates the watch's copy.
 */
data class UserProfile(
    val sex: Sex,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
) {
    enum class Sex { MALE, FEMALE }
}
```

- [ ] **Step 7: `SyncProgress` and `RawBatch`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/SyncProgress.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * One SDK-shaped item pulled during syncData() (§5.2) — the journal writer in
 * :core:data stores payloadJson as-is, untouched, into raw_ingest.
 */
data class RawBatch(
    val dataType: String,
    val payloadJson: String,
)

data class SyncProgress(
    val batch: RawBatch?,
    val itemsSynced: Int,
    val totalItems: Int,
    val completed: Boolean,
)
```

- [ ] **Step 8: `OutgoingNotification` and `SendResult`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/OutgoingNotification.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * Built by the §8.5 filter pipeline before it reaches WatchClient.sendNotification().
 */
data class OutgoingNotification(
    val sourcePackage: String,
    val type: NotificationType,
    val title: String,
    val content: String,
) {
    enum class NotificationType { SMS, WHATSAPP, TELEGRAM, CALL, OTHERS_APP }
}

sealed interface SendResult {
    data object Sent : SendResult
    data class Dropped(val reason: String) : SendResult
    data class Failed(val reason: String) : SendResult
}
```

- [ ] **Step 9: `WatchSettingChange` and `WeatherForecast`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchSettingChange.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * Marker for applySettings() (§4.2). No subtypes yet — Phase 8 (Watch control) defines
 * the real settings (alarms, reminders, DND, units); this keeps the contract compiling
 * without guessing that shape early.
 */
sealed interface WatchSettingChange
```

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WeatherForecast.kt`:
```kotlin
package com.nexwatch.core.watchapi

/**
 * Pushed by WeatherWorker (§8.7) via WatchClient.pushWeather().
 */
enum class WeatherCondition { SUNNY, CLOUDY, RAIN, SNOW, STORM, FOG }

data class WeatherReading(val condition: WeatherCondition, val temperatureC: Int)

data class WeatherDayForecast(val condition: WeatherCondition, val highC: Int, val lowC: Int)

data class WeatherForecast(
    val locationName: String,
    val current: WeatherReading,
    val days: List<WeatherDayForecast>,
)
```

- [ ] **Step 10: `WatchClient`**

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchClient.kt`:
```kotlin
package com.nexwatch.core.watchapi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The one seam between the app and any watch implementation (§4.2). No Android types,
 * no SDK types — FitCloudWatchClient (Phase 4) and FakeWatchClient (this phase) are the
 * only two implementations, ever.
 */
interface WatchClient {
    val state: StateFlow<WatchState>
    val capabilities: StateFlow<WatchCapabilities?>
    val events: SharedFlow<WatchEvent>

    suspend fun bind(address: String, profile: UserProfile)
    suspend fun login(address: String, profile: UserProfile)
    suspend fun unbind(keepWatchData: Boolean)

    fun syncHealthData(): Flow<SyncProgress>
    fun liveHeartRate(): Flow<Int>
    suspend fun batteryLevel(): Int
    suspend fun findWatch()
    suspend fun sendNotification(n: OutgoingNotification): SendResult
    suspend fun applySettings(change: WatchSettingChange)
    suspend fun pushWeather(forecast: WeatherForecast)
}
```

- [ ] **Step 11: Write a test proving `WatchNotReadyException` carries the offending state**

`core/watch-api/src/test/kotlin/com/nexwatch/core/watchapi/WatchNotReadyExceptionTest.kt`:
```kotlin
package com.nexwatch.core.watchapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNotReadyExceptionTest {

    @Test
    fun `message names the offending state`() {
        val exception = WatchNotReadyException(WatchState.Unbound)
        assertEquals(WatchState.Unbound, exception.state)
        assertTrue(exception.message!!.contains("Unbound"))
    }
}
```

- [ ] **Step 12: Run the test and the module build**

Run: `./gradlew :core:watch-api:test`
Expected: BUILD SUCCESSFUL, the new test passes.

- [ ] **Step 13: Commit**

```bash
git add core/watch-api
git commit -m "feat(watch-api): add WatchClient contract and domain types (§4.2)"
```

---

## Task 4: `:core:watch-fake` — `FakeWatchClient` and `WatchDebugController`

**Files:**
- Modify: `core/watch-fake/build.gradle.kts`
- Delete: `core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/package-info.kt`
- Create: `core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/WatchDebugController.kt`
- Create: `core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/FakeWatchClient.kt`
- Create: `core/watch-fake/src/test/kotlin/com/nexwatch/core/watchfake/FakeWatchClientTest.kt`

**Interfaces:**
- Consumes: every type from Task 3 (`WatchClient`, `WatchState`, `WatchCapabilities`, `UserProfile`, `SyncProgress`, `RawBatch`, `OutgoingNotification`, `SendResult`, `WatchSettingChange`, `WeatherForecast`, `WatchNotReadyException`).
- Produces: `class FakeWatchClient` implementing both `WatchClient` and `WatchDebugController`; `interface WatchDebugController { fun forceState(state: WatchState) }` — Task 6 (`:app`) binds both from the same singleton and the debug screen calls `forceState`.

- [ ] **Step 1: Wire dependencies**

`core/watch-fake/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.watchfake"
}

dependencies {
    implementation(project(":core:watch-api"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Delete the Phase 0 stub**

```bash
git rm core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/package-info.kt
```

- [ ] **Step 3: `WatchDebugController`**

`core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/WatchDebugController.kt`:
```kotlin
package com.nexwatch.core.watchfake

import com.nexwatch.core.watchapi.WatchState

/**
 * Debug-only escape hatch (Phase 1 exit criteria: "a debug menu can force the app
 * through every WatchState"). Deliberately not part of WatchClient — production code
 * never calls this, only the debug screen does.
 */
interface WatchDebugController {
    fun forceState(state: WatchState)
    fun forceBattery(percent: Int)
}
```

- [ ] **Step 4: Write the failing test for the bind → Ready happy path**

`core/watch-fake/src/test/kotlin/com/nexwatch/core/watchfake/FakeWatchClientTest.kt`:
```kotlin
package com.nexwatch.core.watchfake

import app.cash.turbine.test
import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchNotReadyException
import com.nexwatch.core.watchapi.WatchState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FakeWatchClientTest {

    private val profile = UserProfile(
        sex = UserProfile.Sex.MALE,
        age = 30,
        heightCm = 175,
        weightKg = 70,
    )

    @Test
    fun `initial state is Unbound`() = runTest {
        val client = FakeWatchClient()
        assertEquals(WatchState.Unbound, client.state.value)
    }

    @Test
    fun `bind transitions through Connecting to Ready`() = runTest {
        val client = FakeWatchClient()
        client.state.test {
            assertEquals(WatchState.Unbound, awaitItem())
            client.bind("AA:BB:CC:DD:EE:FF", profile)
            assertEquals(WatchState.Connecting, awaitItem())
            val ready = awaitItem()
            assertTrue(ready is WatchState.Ready)
        }
    }

    @Test
    fun `unbind returns to Unbound`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        client.unbind(keepWatchData = true)
        assertEquals(WatchState.Unbound, client.state.value)
    }

    @Test
    fun `commands fail fast when not Ready`() = runTest {
        val client = FakeWatchClient()
        try {
            client.batteryLevel()
            fail("expected WatchNotReadyException")
        } catch (e: WatchNotReadyException) {
            assertEquals(WatchState.Unbound, e.state)
        }
    }

    @Test
    fun `sendNotification succeeds once Ready`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        val notification = OutgoingNotification(
            sourcePackage = "com.whatsapp",
            type = OutgoingNotification.NotificationType.WHATSAPP,
            title = "Ada",
            content = "hey",
        )
        val result = client.sendNotification(notification)
        assertTrue(result is com.nexwatch.core.watchapi.SendResult.Sent)
    }

    @Test
    fun `syncHealthData emits progress and completes`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        val progress = client.syncHealthData().toList()
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.last().completed)
        assertEquals(progress.last().itemsSynced, progress.last().totalItems)
    }

    @Test
    fun `liveHeartRate emits while collected`() = runTest {
        val client = FakeWatchClient()
        client.bind("AA:BB:CC:DD:EE:FF", profile)
        val first = client.liveHeartRate().first()
        assertTrue(first in 40..200)
    }

    @Test
    fun `forceState sets any state on demand`() = runTest {
        val client = FakeWatchClient()
        client.forceState(WatchState.AuthFailed("simulated"))
        assertEquals(WatchState.AuthFailed("simulated"), client.state.value)
    }
}
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew :core:watch-fake:testDebugUnitTest`
Expected: FAIL — `FakeWatchClient` unresolved.

- [ ] **Step 6: Implement `FakeWatchClient`**

`core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/FakeWatchClient.kt`:
```kotlin
package com.nexwatch.core.watchfake

import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.RawBatch
import com.nexwatch.core.watchapi.SendResult
import com.nexwatch.core.watchapi.SyncProgress
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchapi.WatchCapabilities
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchNotReadyException
import com.nexwatch.core.watchapi.WatchSettingChange
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.WeatherForecast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

private val SIMULATED_DATA_TYPES = listOf("steps", "heart_rate", "sleep", "spo2")

/**
 * Drives UI development and tests against WatchClient without real Bluetooth (§4.2).
 * Every command routes through [mutex] to mirror the real client's serial-BLE discipline
 * (§4.3) even though nothing here actually contends for a radio — this keeps the two
 * implementations' call-timing behaviour comparable under test.
 */
@Singleton
class FakeWatchClient @Inject constructor() : WatchClient, WatchDebugController {

    private val mutex = Mutex()
    private val _state = MutableStateFlow<WatchState>(WatchState.Unbound)
    private val _capabilities = MutableStateFlow<WatchCapabilities?>(null)
    private val _events = MutableSharedFlow<WatchEvent>(extraBufferCapacity = 8)
    private var fakeBattery = 82

    override val state: StateFlow<WatchState> = _state.asStateFlow()
    override val capabilities: StateFlow<WatchCapabilities?> = _capabilities.asStateFlow()
    override val events: SharedFlow<WatchEvent> = _events.asSharedFlow()

    override suspend fun bind(address: String, profile: UserProfile) = connect(profile)

    override suspend fun login(address: String, profile: UserProfile) = connect(profile)

    private suspend fun connect(profile: UserProfile) = mutex.withLock {
        _state.value = WatchState.Connecting
        delay(CONNECT_DELAY_MS)
        _capabilities.value = sampleCapabilities()
        _state.value = WatchState.Ready(battery = fakeBattery)
    }

    override suspend fun unbind(keepWatchData: Boolean) = mutex.withLock {
        _capabilities.value = null
        _state.value = WatchState.Unbound
    }

    override fun syncHealthData(): Flow<SyncProgress> = flow {
        val ready = _state.value
        if (ready !is WatchState.Ready) throw WatchNotReadyException(ready)
        val total = SIMULATED_DATA_TYPES.size
        SIMULATED_DATA_TYPES.forEachIndexed { index, dataType ->
            delay(SYNC_ITEM_DELAY_MS)
            emit(
                SyncProgress(
                    batch = RawBatch(dataType = dataType, payloadJson = "{\"type\":\"$dataType\"}"),
                    itemsSynced = index + 1,
                    totalItems = total,
                    completed = index + 1 == total,
                ),
            )
        }
    }

    override fun liveHeartRate(): Flow<Int> = flow {
        requireReady()
        while (true) {
            emit(Random.nextInt(55, 120))
            delay(HEART_RATE_INTERVAL_MS)
        }
    }

    override suspend fun batteryLevel(): Int = mutex.withLock {
        requireReady()
        fakeBattery
    }

    override suspend fun findWatch(): Unit = mutex.withLock {
        requireReady()
        delay(COMMAND_DELAY_MS)
    }

    override suspend fun sendNotification(n: OutgoingNotification): SendResult = mutex.withLock {
        val current = _state.value
        if (current !is WatchState.Ready) return@withLock SendResult.Dropped("watch not ready")
        delay(COMMAND_DELAY_MS)
        SendResult.Sent
    }

    override suspend fun applySettings(change: WatchSettingChange): Unit = mutex.withLock {
        requireReady()
        delay(COMMAND_DELAY_MS)
    }

    override suspend fun pushWeather(forecast: WeatherForecast): Unit = mutex.withLock {
        requireReady()
        delay(COMMAND_DELAY_MS)
    }

    override fun forceState(state: WatchState) {
        _state.value = state
        if (state !is WatchState.Ready) _capabilities.value = null
    }

    override fun forceBattery(percent: Int) {
        fakeBattery = percent.coerceIn(0, 100)
        val current = _state.value
        if (current is WatchState.Ready) _state.value = WatchState.Ready(battery = fakeBattery)
    }

    private fun requireReady() {
        val current = _state.value
        if (current !is WatchState.Ready) throw WatchNotReadyException(current)
    }

    private fun sampleCapabilities() = WatchCapabilities(
        heartRate = true,
        spo2 = true,
        bloodPressure = false,
        temperature = false,
        stress = true,
        sport = true,
        gps = true,
        advancedReminders = true,
        weather = true,
        contactsLimit = 20,
        firmwareVersion = "FAKE-1.0.0",
    )

    private companion object {
        const val CONNECT_DELAY_MS = 50L
        const val SYNC_ITEM_DELAY_MS = 20L
        const val COMMAND_DELAY_MS = 10L
        const val HEART_RATE_INTERVAL_MS = 1000L
    }
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :core:watch-fake:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all `FakeWatchClientTest` cases pass. (`liveHeartRate emits while collected` runs under `runTest`'s virtual time, so the 1000 ms interval doesn't cost real wall-clock time.)

- [ ] **Step 8: Commit**

```bash
git add core/watch-fake
git commit -m "feat(watch-fake): implement FakeWatchClient and WatchDebugController"
```

---

## Task 5: `:core:data` — `WatchIdentityStore`

**Files:**
- Modify: `core/data/build.gradle.kts`
- Delete: `core/data/src/main/kotlin/com/nexwatch/core/data/package-info.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/identity/WatchIdentity.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/identity/WatchIdentityStore.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/di/DataModule.kt`
- Create: `core/data/src/test/kotlin/com/nexwatch/core/data/identity/WatchIdentityStoreTest.kt`

**Interfaces:**
- Consumes: `CoroutineDispatchers` (Task 2).
- Produces: `data class WatchIdentity(userId: String, boundAddress: String?, isBound: Boolean)`, `class WatchIdentityStore` with `val identity: Flow<WatchIdentity>`, `suspend fun ensureUserId(): String`, `suspend fun markBound(address: String)`, `suspend fun markUnbound(keepAddress: Boolean)` — Phase 2's onboarding view model and Phase 4/5's login path consume these by name.

- [ ] **Step 1: Wire dependencies**

`core/data/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.data"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:sync-api"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.junit)
}
```

- [ ] **Step 2: Delete the Phase 0 stub**

```bash
git rm core/data/src/main/kotlin/com/nexwatch/core/data/package-info.kt
```

- [ ] **Step 3: `WatchIdentity`**

`core/data/src/main/kotlin/com/nexwatch/core/data/identity/WatchIdentity.kt`:
```kotlin
package com.nexwatch.core.data.identity

/**
 * The only identity the watch ever sees is userId (§4.4). boundAddress/isBound persist
 * across process death, reboot and app update so every later connection uses LOGIN.
 */
data class WatchIdentity(
    val userId: String,
    val boundAddress: String?,
    val isBound: Boolean,
)
```

- [ ] **Step 4: Write the failing test**

`core/data/src/test/kotlin/com/nexwatch/core/data/identity/WatchIdentityStoreTest.kt`:
```kotlin
package com.nexwatch.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchIdentityStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): WatchIdentityStore {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test-${System.nanoTime()}.preferences_pb") },
        )
        val dispatchers = object : CoroutineDispatchers {
            override val io = UnconfinedTestDispatcher()
            override val default = UnconfinedTestDispatcher()
        }
        return WatchIdentityStore(dataStore, dispatchers)
    }

    @Test
    fun `initial identity has no userId and is unbound`() = runTest {
        val store = newStore()
        val identity = store.identity.first()
        assertNull(identity.userId.takeIf { it.isEmpty() })
        assertFalse(identity.isBound)
        assertNull(identity.boundAddress)
    }

    @Test
    fun `ensureUserId generates once and persists`() = runTest {
        val store = newStore()
        val first = store.ensureUserId()
        val second = store.ensureUserId()
        assertEquals(first, second)
        assertEquals(first, store.identity.first().userId)
    }

    @Test
    fun `markBound then markUnbound keeping address preserves it`() = runTest {
        val store = newStore()
        store.markBound("AA:BB:CC:DD:EE:FF")
        assertTrue(store.identity.first().isBound)
        assertEquals("AA:BB:CC:DD:EE:FF", store.identity.first().boundAddress)

        store.markUnbound(keepAddress = true)
        val identity = store.identity.first()
        assertFalse(identity.isBound)
        assertEquals("AA:BB:CC:DD:EE:FF", identity.boundAddress)
    }

    @Test
    fun `markUnbound without keeping address clears it`() = runTest {
        val store = newStore()
        store.markBound("AA:BB:CC:DD:EE:FF")
        store.markUnbound(keepAddress = false)
        assertNull(store.identity.first().boundAddress)
    }
}
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.nexwatch.core.data.identity.WatchIdentityStoreTest"`
Expected: FAIL — `WatchIdentityStore` unresolved.

- [ ] **Step 6: Implement `WatchIdentityStore`**

`core/data/src/main/kotlin/com/nexwatch/core/data/identity/WatchIdentityStore.kt`:
```kotlin
package com.nexwatch.core.data.identity

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Backs §4.4: userId is generated once and never changes; bind is guarded elsewhere
 * (the onboarding flow), this store only remembers the outcome so every later
 * connection can use LOGIN instead.
 */
class WatchIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val identity: Flow<WatchIdentity> = dataStore.data.map { prefs ->
        WatchIdentity(
            userId = prefs[USER_ID_KEY].orEmpty(),
            boundAddress = prefs[BOUND_ADDRESS_KEY],
            isBound = prefs[IS_BOUND_KEY] ?: false,
        )
    }

    suspend fun ensureUserId(): String = withContext(dispatchers.io) {
        val existing = dataStore.data.first()[USER_ID_KEY]
        if (existing != null) return@withContext existing
        val generated = UUID.randomUUID().toString()
        dataStore.edit { it[USER_ID_KEY] = generated }
        generated
    }

    suspend fun markBound(address: String) = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[BOUND_ADDRESS_KEY] = address
            prefs[IS_BOUND_KEY] = true
        }
    }

    suspend fun markUnbound(keepAddress: Boolean) = withContext(dispatchers.io) {
        dataStore.edit { prefs ->
            prefs[IS_BOUND_KEY] = false
            if (!keepAddress) prefs.remove(BOUND_ADDRESS_KEY)
        }
    }

    private companion object {
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val BOUND_ADDRESS_KEY = stringPreferencesKey("bound_address")
        val IS_BOUND_KEY = booleanPreferencesKey("is_bound")
    }
}
```

- [ ] **Step 7: Provide the real `DataStore<Preferences>` for Hilt**

`core/data/src/main/kotlin/com/nexwatch/core/data/di/DataModule.kt`:
```kotlin
package com.nexwatch.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideWatchIdentityDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("watch_identity") },
        )
}
```

This needs `AndroidManifest.xml` to already declare the `com.nexwatch.core.data` namespace context APIs — no manifest change required, `Context` comes from Hilt's `@ApplicationContext`.

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :core:data:testDebugUnitTest --tests "com.nexwatch.core.data.identity.WatchIdentityStoreTest"`
Expected: BUILD SUCCESSFUL, all four cases pass.

- [ ] **Step 9: Commit**

```bash
git add core/data
git commit -m "feat(data): add DataStore-backed WatchIdentityStore (§4.4)"
```

---

## Task 6: `:app` — Hilt wiring and the watch-state debug screen

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/nexwatch/di/WatchModule.kt`
- Create: `app/src/main/kotlin/com/nexwatch/ui/debug/WatchDebugViewModel.kt`
- Create: `app/src/main/kotlin/com/nexwatch/ui/debug/WatchDebugScreen.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`

**Interfaces:**
- Consumes: `WatchClient`, `WatchState` (Task 3), `FakeWatchClient`, `WatchDebugController` (Task 4).
- Produces: nothing further downstream — this task is the leaf that exercises everything else.

- [ ] **Step 1: Add Hilt to `:app`**

`app/build.gradle.kts` — add the plugin and dependencies (keep every existing line):
```kotlin
plugins {
    id("nexwatch.android.application")
    id("nexwatch.android.compose")
    id("nexwatch.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}
```
Add to `dependencies { }`:
```kotlin
    implementation(project(":core:watch-api"))
    implementation(project(":core:watch-fake"))
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 2: `NexWatchApplication`**

`app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt`:
```kotlin
package com.nexwatch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexWatchApplication : Application()
```

- [ ] **Step 3: Register it and annotate the activity**

In `app/src/main/AndroidManifest.xml`, add `android:name=".NexWatchApplication"` to the `<application>` tag (keep every existing attribute).

In `app/src/main/kotlin/com/nexwatch/MainActivity.kt`, add the `@AndroidEntryPoint` annotation:
```kotlin
package com.nexwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.navigation.NexWatchNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatchTheme {
                PremiumBackground {
                    NexWatchNavHost()
                }
            }
        }
    }
}
```

- [ ] **Step 4: Bind `WatchClient` and `WatchDebugController` to the same `FakeWatchClient` singleton**

`app/src/main/kotlin/com/nexwatch/di/WatchModule.kt`:
```kotlin
package com.nexwatch.di

import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchfake.FakeWatchClient
import com.nexwatch.core.watchfake.WatchDebugController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Both interfaces bind to the same @Singleton FakeWatchClient instance, so the debug
 * screen's forceState() calls are visible through the WatchClient the rest of the app
 * observes. Phase 4 replaces this module's targets with FitCloudWatchClient for release
 * builds and keeps this fake binding for debug builds.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WatchModule {
    @Binds
    @Singleton
    abstract fun bindWatchClient(impl: FakeWatchClient): WatchClient

    @Binds
    @Singleton
    abstract fun bindWatchDebugController(impl: FakeWatchClient): WatchDebugController
}
```

- [ ] **Step 5: `WatchDebugViewModel`**

`app/src/main/kotlin/com/nexwatch/ui/debug/WatchDebugViewModel.kt`:
```kotlin
package com.nexwatch.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchfake.WatchDebugController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class WatchDebugViewModel @Inject constructor(
    watchClient: WatchClient,
    private val debugController: WatchDebugController,
) : ViewModel() {

    val state: StateFlow<WatchState> = watchClient.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WatchState.Unbound,
    )

    val forceableStates: List<Pair<String, WatchState>> = listOf(
        "Unbound" to WatchState.Unbound,
        "Bluetooth off" to WatchState.BluetoothOff,
        "Waiting to retry" to WatchState.Waiting(nextRetryAt = Instant.now().plusSeconds(30)),
        "Connecting" to WatchState.Connecting,
        "Ready (72%)" to WatchState.Ready(battery = 72),
        "Auth failed" to WatchState.AuthFailed(reason = "simulated mismatch"),
    )

    fun forceState(state: WatchState) = debugController.forceState(state)
}
```

- [ ] **Step 6: `WatchDebugScreen`**

`app/src/main/kotlin/com/nexwatch/ui/debug/WatchDebugScreen.kt`:
```kotlin
package com.nexwatch.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WatchDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: WatchDebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Current state: $state",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(viewModel.forceableStates) { (label, target) ->
                Button(onClick = { viewModel.forceState(target) }) {
                    Text(label)
                }
            }
        }
    }
}
```

- [ ] **Step 7: Wire it into the Watch tab**

In `app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`, replace the Watch tab's placeholder composable and import:
```kotlin
import com.nexwatch.ui.debug.WatchDebugScreen
```
```kotlin
            composable<NexWatchDestination.Watch> { WatchDebugScreen() }
```
(leave `Today`, `Health` and `Data` on `PlaceholderScreen` — untouched by this phase).

- [ ] **Step 8: Verify the build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Manual check (only if a device/emulator is attached)**

Run: `./gradlew installDebug`, open the app, switch to the Watch tab, tap through each of the six buttons, and confirm the "Current state:" text updates immediately for each. If no device is attached, skip and record that it was skipped.

- [ ] **Step 10: Commit**

```bash
git add app
git commit -m "feat(app): wire Hilt, bind FakeWatchClient, add the watch-state debug screen"
```

---

## Task 7: Full verification and close out the phase

**Files:**
- Modify: `docs/implementation-plan.md`

**Interfaces:**
- None.

- [ ] **Step 1: Run the full verification suite**

Run: `./gradlew assembleDebug assembleRelease test lint`
Expected: BUILD SUCCESSFUL for all four. Fix any R8/Hilt keep-rule issue here (Hilt ships its own consumer rules; if release fails on a missing Dagger-generated class, add the minimal rule to `app/proguard-rules.pro` rather than disabling optimization).

- [ ] **Step 2: Re-confirm the module-boundary rule still holds for `:core:watch-fake`**

Run: `git grep "com.nexwatch.core.common" -- core/watch-fake` and `git grep "com.nexwatch.core.data" -- core/watch-fake`
Expected: both return nothing — `FakeWatchClient` uses no dispatcher abstraction and no identity store, matching the Global Constraints ruling.

- [ ] **Step 3: Update the implementation plan's status table and checklist**

In `docs/implementation-plan.md`, change the Phase 1 row and check its exit criteria:
```markdown
| 1 | Fake watch & app state | `phase-1-fake-watch` | Done |
```
```markdown
**Exit criteria**
- [x] Unit tests cover `FakeWatchClient` and `WatchIdentityStore`.
- [x] A debug menu can force the app through every `WatchState` without touching real Bluetooth.
```

- [ ] **Step 4: Commit**

```bash
git add docs/implementation-plan.md
git commit -m "docs: mark Phase 1 (Fake watch & app state) done"
```
