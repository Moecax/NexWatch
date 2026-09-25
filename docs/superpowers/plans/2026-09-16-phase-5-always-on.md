# Phase 5 — Always-on service (M2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `:core:service` per `docs/implementation-plan.md` §8 so the watch stays connected, notifications and calls forward, and the connection survives reboots and the app being swiped away — closing Phase 5's two exit criteria (48h soak with notifications/calls arriving; battery/memory within §9.1 budgets).

**Architecture:** A foreground service (`WatchConnectionService`, type `connectedDevice`) owns the `WatchClient` connection and keeps the process alive. `NotificationForwarder` (extends the SDK's `AbsNotificationListenerService`) runs a pure, unit-testable filter pipeline before calling `WatchClient.sendNotification()`. `BootReceiver` and a `CompanionDeviceService` (API 31+) bring the service back after a reboot or when the watch comes into range, without the app ever polling. Telephony/media are already SDK built-ins (`FitCloudSdk.kt`); Phase 5 only adds the permission handshake and the app-side reaction to `WatchEvent.HangUpRequested`/`FindPhoneRequested`.

**Tech Stack:** Kotlin, Hilt (`@AndroidEntryPoint` for Service/BroadcastReceiver, since these are framework-instantiated and can't take constructor injection), DataStore (Preferences), Jetpack Compose for the Diagnostics screen, JUnit + Turbine for pure-logic tests.

**Spec:** `docs/implementation-plan.md` §8 (all subsections), §4.2 (`WatchClient`/`WatchEvent`), §12 Phase 5 row and its "Inherited from Phase 4" note.

## Global Constraints

- No custom reconnect logic — `FcConnector` owns it (CLAUDE.md, §2). This plan only calls `login()`/`bind()` and reacts to `WatchState`; it never retries a failed connect itself.
- BIND is only ever called from the guarded onboarding pairing flow. Every path this plan adds uses LOGIN.
- SDK types (`com.topstep.**`) never leave `:core:watch-fitcloud`. `:core:service` only ever imports `:core:watch-api` types.
- Nothing polls (CLAUDE.md invariant I5 / spec I5). Presence is CDM-driven, sync triggers are event-driven off `WatchState`, and the periodic `WatchSyncWorker` safety net is explicitly **descoped from this phase** (see Task 0 below) because it would sync destructively into a Phase-6 journal that doesn't exist yet.
- Never scan for devices outside the pairing screen (§9.2) — this plan doesn't touch `discoverWatches()`/`FcScanner`.
- Never hold wakelocks. `WatchConnectionService`'s foreground notification updates only on an actual text change (§8.2), never per heart-rate reading.
- Never log notification content in release builds (CLAUDE.md).
- Base package `com.nexwatch`; `:core:service` namespace is already `com.nexwatch.core.service`.

---

## Task 0: Descope the periodic data-sync worker, record it in the plan doc

§8.7 lists `WatchSyncWorker` as part of "background operation" and §8.2 says the service "triggers a health sync, debounced" on `Ready`. Both assume there's a journal (`raw_ingest`, Phase 6) to write into. Today, `WatchClient.syncHealthData()` is destructive on the watch (§2: the watch deletes each type once the SDK reports success) and nothing downstream persists the emitted `RawBatch`s — calling it now would permanently lose data with no journal to catch it, which violates invariant I3. This task records that scope correction in the plan doc itself, per CLAUDE.md's "fix §12 in the same change rather than drifting silently."

**Files:**
- Modify: `docs/implementation-plan.md` (Phase 5 section, ~line 712-723)

- [ ] **Step 1: Add a scope note to the Phase 5 section**

Insert this paragraph immediately after the existing "Inherited from Phase 4" paragraph (after line 719, before "**Exit criteria**"):

```markdown
**Descoped to Phase 6.** §8.2's "triggers a health sync, debounced, on `Ready`" and §8.7's
`WatchSyncWorker` both assume a journal to write into. `WatchClient.syncHealthData()` is
destructive on the watch (§2) — calling it with nothing downstream to persist the emitted
`RawBatch`s would violate I3 (journal-first) and permanently lose data. `WatchConnectionService`
in this phase logs in and stays connected, but does not call `syncHealthData()`; that wiring
moves to Phase 6 once `raw_ingest` exists to receive it. WorkManager and the version-catalog
entries for it are therefore not added in this phase either.
```

- [ ] **Step 2: Commit**

```bash
git add docs/implementation-plan.md
git commit -m "docs(phase-5): descope destructive health-sync trigger to Phase 6"
```

---

## Task 1: Branch cut

**Files:** none (git only)

- [ ] **Step 1: Confirm `main` is clean and up to date, then cut the branch**

```bash
git status
git checkout main
git pull
git checkout -b phase-5-always-on
```

- [ ] **Step 2: Update `docs/implementation-plan.md` §12's Phase 5 row to `In progress` and commit**

Change line 533 from:
```
| 5 | Always-on service (M2) | `phase-5-always-on` | Not started |
```
to:
```
| 5 | Always-on service (M2) | `phase-5-always-on` | In progress |
```

```bash
git add docs/implementation-plan.md
git commit -m "docs(phase-5): mark phase in progress"
```

---

## Task 2: `WatchClient` telephony-permission hook

§8.6: call `telephonyControlPhoneStatePermission()` once `READ_PHONE_STATE` is granted, and again on every return to the foreground. That SDK method lives on `FcConnector` (confirmed via `javap` against the vendored AAR — `public abstract void telephonyControlPhoneStatePermission()`, no arguments), so it must be reached through `WatchClient`, not called directly from `:core:service` or `:app`.

**Files:**
- Modify: `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/WatchClient.kt`
- Modify: `core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/FitCloudWatchClient.kt`
- Modify: `core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/FakeWatchClient.kt`
- Test: `core/watch-fitcloud/src/test/kotlin/com/nexwatch/core/watchfitcloud/FitCloudWatchClientTest.kt` (extend existing test file if present, else check for its actual name via `Glob "core/watch-fitcloud/src/test/**/*Test.kt"` first)

**Interfaces:**
- Produces: `WatchClient.notifyPhoneStatePermissionGranted(): Unit` (suspend), callable by `:core:service` and `:app` without seeing any SDK type.

- [ ] **Step 1: Add the method to the interface**

In `WatchClient.kt`, add next to `findWatch()`:

```kotlin
    /**
     * Call once READ_PHONE_STATE is granted, and again on every return to the foreground
     * (§8.6) — the SDK does not persist this across activity resumes on its own.
     */
    suspend fun notifyPhoneStatePermissionGranted()
```

- [ ] **Step 2: Implement in `FitCloudWatchClient`**

Add near `findWatch()`:

```kotlin
    override suspend fun notifyPhoneStatePermissionGranted() {
        command("phoneStatePermission") { connector.telephonyControlPhoneStatePermission() }
    }
```

`telephonyControlPhoneStatePermission()` is synchronous and void, so wrapping it in `command {}` just gets it the same mutex/timeout discipline as every other call (§4.3) — cheap and consistent, not because it can hang.

- [ ] **Step 3: Implement in `FakeWatchClient`**

```kotlin
    override suspend fun notifyPhoneStatePermissionGranted() {
        // No-op: the fake client has no telephony state to unlock.
    }
```

- [ ] **Step 4: Locate the existing FitCloud client test file and add a coverage case**

```bash
grep -rl "class FitCloudWatchClientTest" core/watch-fitcloud/src/test
```

Read that file to match its existing fake-connector/mock style, then add:

```kotlin
@Test
fun `notifyPhoneStatePermissionGranted calls through to the connector`() = runTest {
    client.notifyPhoneStatePermissionGranted()
    verify(connector).telephonyControlPhoneStatePermission()
}
```

(Match the exact mocking library — Mockito or a hand-rolled fake — already used in that file; adapt the verify syntax accordingly.)

- [ ] **Step 5: Run the module's tests**

```bash
./gradlew :core:watch-fitcloud:test :core:watch-fake:test
```

Expected: all pass, including the new case.

- [ ] **Step 6: Commit**

```bash
git add core/watch-api core/watch-fitcloud core/watch-fake
git commit -m "feat(watch-client): add notifyPhoneStatePermissionGranted for telephony control"
```

---

## Task 3: `NotificationForwardingPrefs` in `:core:data`

The §8.5 filter pipeline needs a master switch and a source allowlist. Following `WatchIdentityStore`'s exact DataStore pattern (same module, same DI style).

**Files:**
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/notification/NotificationForwardingPrefs.kt`
- Create: `core/data/src/test/kotlin/com/nexwatch/core/data/notification/NotificationForwardingPrefsTest.kt`
- Modify: `core/data/src/main/kotlin/com/nexwatch/core/data/di/DataModule.kt` (new `provideNotificationForwardingDataStore`)

**Interfaces:**
- Produces: `NotificationForwardingPrefs` with `val settings: Flow<NotificationForwardingSettings>`, `suspend fun setEnabled(enabled: Boolean)`, `suspend fun setAllowedPackages(packages: Set<String>)`.
- `NotificationForwardingSettings(val enabled: Boolean, val allowedPackages: Set<String>)`.
- Consumed by Task 5's `NotificationFilterPipeline`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.nexwatch.core.data.notification

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

class NotificationForwardingPrefsTest {

    private val tempDir = createTempDirectory("notif-prefs").toFile()
    private val dispatcher = StandardTestDispatcher()
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir, "notification_forwarding.preferences_pb") },
    )
    private val dispatchers = CoroutineDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    private val prefs = NotificationForwardingPrefs(dataStore, dispatchers)

    @Test
    fun `defaults to enabled with the built-in messaging allowlist`() = runTest(dispatcher) {
        val settings = prefs.settings.first()
        assertTrue(settings.enabled)
        assertTrue(settings.allowedPackages.contains("com.whatsapp"))
    }

    @Test
    fun `setEnabled persists`() = runTest(dispatcher) {
        prefs.setEnabled(false)
        assertEquals(false, prefs.settings.first().enabled)
    }

    @Test
    fun `setAllowedPackages replaces the set`() = runTest(dispatcher) {
        prefs.setAllowedPackages(setOf("com.example.app"))
        assertEquals(setOf("com.example.app"), prefs.settings.first().allowedPackages)
    }
}
```

Check `CoroutineDispatchers`'s actual constructor shape first (`Glob "core/common/**/CoroutineDispatchers.kt"`, then `Read`) and adjust the test's construction to match exactly — do not guess field names.

- [ ] **Step 2: Run to verify it fails**

```bash
./gradlew :core:data:test --tests "com.nexwatch.core.data.notification.NotificationForwardingPrefsTest"
```

Expected: FAIL — `NotificationForwardingPrefs` and `NotificationForwardingSettings` don't exist yet.

- [ ] **Step 3: Implement**

```kotlin
package com.nexwatch.core.data.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NotificationForwardingSettings(
    val enabled: Boolean,
    val allowedPackages: Set<String>,
)

/**
 * Backs the §8.5 filter pipeline's master switch and source allowlist. The default
 * allowlist covers the messaging apps §8.5 names explicitly (WhatsApp, Telegram, SMS);
 * everything else starts opted out until the user adds it — a companion-device app that
 * forwards every notification by default is surprising, not helpful.
 */
class NotificationForwardingPrefs @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val settings: Flow<NotificationForwardingSettings> = dataStore.data.map { prefs ->
        NotificationForwardingSettings(
            enabled = prefs[ENABLED_KEY] ?: true,
            allowedPackages = prefs[ALLOWED_PACKAGES_KEY] ?: DEFAULT_ALLOWED_PACKAGES,
        )
    }

    suspend fun setEnabled(enabled: Boolean): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[ENABLED_KEY] = enabled }
        Unit
    }

    suspend fun setAllowedPackages(packages: Set<String>): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[ALLOWED_PACKAGES_KEY] = packages }
        Unit
    }

    private companion object {
        val ENABLED_KEY = booleanPreferencesKey("forwarding_enabled")
        val ALLOWED_PACKAGES_KEY = stringSetPreferencesKey("allowed_packages")
        val DEFAULT_ALLOWED_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
        )
    }
}
```

- [ ] **Step 4: Wire the DataStore provider**

In `DataModule.kt`, add:

```kotlin
    @Provides
    @Singleton
    @NotificationForwardingDataStore
    fun provideNotificationForwardingDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("notification_forwarding") },
        )
```

Since this module now provides two `DataStore<Preferences>` instances, add a qualifier so Hilt can tell them apart. Create `core/data/src/main/kotlin/com/nexwatch/core/data/di/NotificationForwardingDataStore.kt`:

```kotlin
package com.nexwatch.core.data.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NotificationForwardingDataStore
```

Annotate the existing `provideWatchIdentityDataStore` with a matching `@WatchIdentityDataStore` qualifier (create it the same way) so both providers are unambiguous, and add `@WatchIdentityDataStore` to `WatchIdentityStore`'s constructor parameter. Then add `@NotificationForwardingDataStore` to `NotificationForwardingPrefs`'s constructor parameter from Step 3.

- [ ] **Step 5: Run to verify it passes**

```bash
./gradlew :core:data:test
```

Expected: PASS, including `WatchIdentityStoreTest` (unaffected by the qualifier since its provider now matches by qualifier, not by uniqueness-of-type).

- [ ] **Step 6: Commit**

```bash
git add core/data
git commit -m "feat(data): add NotificationForwardingPrefs for the §8.5 filter pipeline"
```

---

## Task 4: `NotificationFilterPipeline` — pure logic, fully unit-tested

This is the part of §8.5 worth testing hard: it's pure decision logic once notifications are reduced to a small data class. Framework extraction (`StatusBarNotification` → this data class) is a thin, untestable-without-Robolectric adapter kept separate in Task 5.

**Files:**
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/notification/IncomingNotification.kt`
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/notification/NotificationFilterPipeline.kt`
- Create: `core/service/src/test/kotlin/com/nexwatch/core/service/notification/NotificationFilterPipelineTest.kt`
- Modify: `core/service/build.gradle.kts` (add `:core:model` for `OutgoingNotification`'s enum if needed — check; add `junit`/`kotlinx-coroutines-test` test deps and `nexwatch.android.hilt`)

**Interfaces:**
- Consumes: `NotificationForwardingSettings` (Task 3), `WatchClient.sendNotification(OutgoingNotification)` (existing, `:core:watch-api`).
- Produces: `NotificationFilterPipeline.evaluate(incoming: IncomingNotification, settings: NotificationForwardingSettings, ownPackageName: String, nowMs: Long): OutgoingNotification?` — pure function, `null` means drop. Also `class NotificationFilterPipeline` holding the dedupe LRU and throttle state across calls (so it's a stateful instance, not a static function, but every method is deterministic given its inputs and the clock passed in — no wall-clock reads inside, so tests control time exactly).

- [ ] **Step 1: Define the data class**

```kotlin
package com.nexwatch.core.service.notification

/**
 * The §8.5 pipeline operates on this instead of `StatusBarNotification` directly, so the
 * decision logic is a pure function testable without Robolectric. `NotificationForwarder`
 * (Task 5) is the only place that builds one from a real `StatusBarNotification`.
 */
data class IncomingNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val category: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
)
```

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.nexwatch.core.service.notification

import com.nexwatch.core.data.notification.NotificationForwardingSettings
import com.nexwatch.core.watchapi.OutgoingNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFilterPipelineTest {

    private val settings = NotificationForwardingSettings(
        enabled = true,
        allowedPackages = setOf("com.whatsapp"),
    )
    private val ownPackage = "com.nexwatch"

    private fun notif(
        pkg: String = "com.whatsapp",
        title: String? = "Alice",
        text: String? = "Hello",
        category: String? = null,
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
    ) = IncomingNotification(pkg, title, text, category, ongoing, groupSummary)

    @Test
    fun `master switch off drops everything`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(), settings.copy(enabled = false), ownPackage, nowMs = 0)
        assertNull(result)
    }

    @Test
    fun `own package is always dropped`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(pkg = ownPackage), settings, ownPackage, nowMs = 0)
        assertNull(result)
    }

    @Test
    fun `package not on the allowlist is dropped`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(pkg = "com.random.app"), settings, ownPackage, nowMs = 0)
        assertNull(result)
    }

    @Test
    fun `ongoing and group-summary notifications are dropped`() {
        val pipeline = NotificationFilterPipeline()
        assertNull(pipeline.evaluate(notif(ongoing = true), settings, ownPackage, nowMs = 0))
        assertNull(pipeline.evaluate(notif(groupSummary = true), settings, ownPackage, nowMs = 0))
    }

    @Test
    fun `progress and service categories are dropped`() {
        val pipeline = NotificationFilterPipeline()
        for (category in listOf("progress", "transport", "service", "status")) {
            assertNull(pipeline.evaluate(notif(category = category), settings, ownPackage, nowMs = 0))
        }
    }

    @Test
    fun `an allowed notification maps to OTHERS_APP by default`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 0)
        assertEquals(OutgoingNotification.NotificationType.WHATSAPP, result?.type)
        assertEquals("Alice", result?.title)
        assertEquals("Hello", result?.content)
    }

    @Test
    fun `unmapped allowed package falls back to OTHERS_APP`() {
        val pipeline = NotificationFilterPipeline()
        val settingsWithOther = settings.copy(allowedPackages = setOf("com.example.other"))
        val result = pipeline.evaluate(notif(pkg = "com.example.other"), settingsWithOther, ownPackage, nowMs = 0)
        assertEquals(OutgoingNotification.NotificationType.OTHERS_APP, result?.type)
    }

    @Test
    fun `identical content within 60 seconds is deduped`() {
        val pipeline = NotificationFilterPipeline()
        val first = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 0)
        val second = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 30_000)
        assertEquals("Alice", first?.title)
        assertNull(second)
    }

    @Test
    fun `identical content after the 60 second window is forwarded again`() {
        val pipeline = NotificationFilterPipeline()
        pipeline.evaluate(notif(), settings, ownPackage, nowMs = 0)
        val afterWindow = pipeline.evaluate(notif(), settings, ownPackage, nowMs = 60_001)
        assertEquals("Alice", afterWindow?.title)
    }

    @Test
    fun `a second distinct notification from the same app within 5 seconds is throttled`() {
        val pipeline = NotificationFilterPipeline()
        pipeline.evaluate(notif(text = "first"), settings, ownPackage, nowMs = 0)
        val throttled = pipeline.evaluate(notif(text = "second"), settings, ownPackage, nowMs = 1_000)
        assertNull(throttled)
    }

    @Test
    fun `a notification from the same app after 5 seconds is not throttled`() {
        val pipeline = NotificationFilterPipeline()
        pipeline.evaluate(notif(text = "first"), settings, ownPackage, nowMs = 0)
        val later = pipeline.evaluate(notif(text = "second"), settings, ownPackage, nowMs = 5_001)
        assertEquals("second", later?.content)
    }

    @Test
    fun `title falls back when EXTRA_TITLE is absent`() {
        val pipeline = NotificationFilterPipeline()
        val result = pipeline.evaluate(notif(title = null), settings, ownPackage, nowMs = 0)
        assertEquals("com.whatsapp", result?.title)
    }
}
```

- [ ] **Step 3: Run to verify failure**

```bash
./gradlew :core:service:test --tests "com.nexwatch.core.service.notification.NotificationFilterPipelineTest"
```

Expected: FAIL — `NotificationFilterPipeline` doesn't exist.

- [ ] **Step 4: Implement**

```kotlin
package com.nexwatch.core.service.notification

import com.nexwatch.core.data.notification.NotificationForwardingSettings
import com.nexwatch.core.watchapi.OutgoingNotification

private const val DEDUPE_WINDOW_MS = 60_000L
private const val DEDUPE_CAPACITY = 50
private const val THROTTLE_WINDOW_MS = 5_000L
private val DROPPED_CATEGORIES = setOf("progress", "transport", "service", "status")

private val PACKAGE_TO_TYPE = mapOf(
    "com.whatsapp" to OutgoingNotification.NotificationType.WHATSAPP,
    "org.telegram.messenger" to OutgoingNotification.NotificationType.TELEGRAM,
    "com.google.android.apps.messaging" to OutgoingNotification.NotificationType.SMS,
    "com.samsung.android.messaging" to OutgoingNotification.NotificationType.SMS,
)

/**
 * §8.5's filter pipeline, in stage order. Cheap checks (master switch, own package,
 * allowlist, type) run before the string hashing dedupe/throttle stages, so most
 * notifications are dropped before any hashing happens.
 */
class NotificationFilterPipeline {

    // LRU by insertion order: eldest-first iteration, capped at DEDUPE_CAPACITY.
    private val recentHashes = object : LinkedHashMap<Int, Long>(DEDUPE_CAPACITY, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?) =
            size > DEDUPE_CAPACITY
    }
    private val lastSentPerPackage = mutableMapOf<String, Long>()

    fun evaluate(
        incoming: IncomingNotification,
        settings: NotificationForwardingSettings,
        ownPackageName: String,
        nowMs: Long,
    ): OutgoingNotification? {
        if (!settings.enabled) return null
        if (incoming.packageName == ownPackageName) return null
        if (incoming.packageName !in settings.allowedPackages) return null
        if (incoming.isOngoing || incoming.isGroupSummary) return null
        if (incoming.category in DROPPED_CATEGORIES) return null

        val title = incoming.title ?: incoming.packageName
        val text = incoming.text.orEmpty()

        val contentHash = (incoming.packageName.hashCode() * 31 + title.hashCode()) * 31 + text.hashCode()
        val lastSeenAt = recentHashes[contentHash]
        if (lastSeenAt != null && nowMs - lastSeenAt < DEDUPE_WINDOW_MS) return null
        recentHashes[contentHash] = nowMs

        val lastSentAt = lastSentPerPackage[incoming.packageName]
        if (lastSentAt != null && nowMs - lastSentAt < THROTTLE_WINDOW_MS) return null
        lastSentPerPackage[incoming.packageName] = nowMs

        val type = PACKAGE_TO_TYPE[incoming.packageName] ?: OutgoingNotification.NotificationType.OTHERS_APP
        return OutgoingNotification(type = type, title = title, content = text)
    }
}
```

Check `OutgoingNotification`'s actual constructor (`Glob "core/watch-api/**/OutgoingNotification.kt"`, then `Read`) before finalizing this — the plan assumes `type`, `title`, `content` fields based on `FitCloudMappers.kt`'s usage (`n.type`, `n.title`, `n.content` at `sendNotification`), but confirm field names and order exactly.

- [ ] **Step 5: Run to verify all pass**

```bash
./gradlew :core:service:test
```

Expected: PASS, all 13 cases.

- [ ] **Step 6: Commit**

```bash
git add core/service
git commit -m "feat(service): add the §8.5 notification filter pipeline with full unit coverage"
```

---

## Task 5: `NotificationForwarder`

Wires the pipeline (Task 4) to the real `StatusBarNotification` and to `WatchClient`. This class extends the SDK's `AbsNotificationListenerService` (confirmed via `javap`: `com.topstep.fitcloud.sdk.v2.utils.notification.AbsNotificationListenerService extends NotificationListenerService`, with abstract `getFcSDK(Context): FcSDK` and `getNotificationType(Context, StatusBarNotification): Integer`, and overridable `onNotificationPosted`/`onNotificationRemoved`). Because it references `FcSDK`, this file must live where SDK types are legal — **not** `:core:service`, which may not import `com.topstep.**`. It goes in `:core:watch-fitcloud` instead, and `:core:service` only sees it through a small factory the `:app` manifest declares directly.

Re-read `CLAUDE.md`'s module table before writing this file: `:core:watch-fitcloud` may depend on `:core:watch-api` and `:core:common`, not `:core:data` (where `NotificationForwardingPrefs` lives) or `:core:service` (where `NotificationFilterPipeline` lives). This is a real constraint the file structure must respect — resolve it as follows:

**Decision:** `NotificationFilterPipeline` and `IncomingNotification` (Task 4) move down to `:core:watch-api` (pure Kotlin — no Android `StatusBarNotification` reference there, `IncomingNotification` is already framework-free) so both `:core:watch-fitcloud` and `:core:service` can use them without a dependency-rule violation. Re-open Task 4 and change its file locations before starting this task:

**Files:**
- Move: `core/service/src/main/kotlin/com/nexwatch/core/service/notification/IncomingNotification.kt` → `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/notification/IncomingNotification.kt`
- Move: `core/service/src/main/kotlin/com/nexwatch/core/service/notification/NotificationFilterPipeline.kt` → `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/notification/NotificationFilterPipeline.kt` (update its package declaration and the `import com.nexwatch.core.data.notification.NotificationForwardingSettings` — **this import must also move**: `NotificationForwardingSettings` cannot live in `:core:data` if `:core:watch-api` needs it and `:core:watch-api` may not depend on `:core:data`. Move `NotificationForwardingSettings` itself into `:core:watch-api` too (it's a plain data class with no DataStore dependency), and have `NotificationForwardingPrefs` (`:core:data`) import it from there instead of declaring it.)
- Move: `core/service/src/test/kotlin/com/nexwatch/core/service/notification/NotificationFilterPipelineTest.kt` → `core/watch-api/src/test/kotlin/com/nexwatch/core/watchapi/notification/NotificationFilterPipelineTest.kt`, update its `NotificationForwardingSettings` import.
- Modify: `core/data/src/main/kotlin/com/nexwatch/core/data/notification/NotificationForwardingPrefs.kt` — remove the local `NotificationForwardingSettings` declaration, import `com.nexwatch.core.watchapi.notification.NotificationForwardingSettings` instead.
- Create: `core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/NotificationForwarder.kt`
- Modify: `core/watch-fitcloud/build.gradle.kts` — confirm it already depends on `:core:common`/`:core:watch-api` (it does, per Phase 4); no new module deps needed since prefs now flow in via constructor injection of the interface, not `:core:data` directly (see Step 2).

**Interfaces:**
- Consumes: `NotificationFilterPipeline.evaluate(...)` (Task 4, now in `:core:watch-api`), `WatchClient.sendNotification()` (existing).
- `NotificationForwarder` needs `NotificationForwardingPrefs.settings: Flow<NotificationForwardingSettings>` from `:core:data` — but `:core:watch-fitcloud` may not depend on `:core:data`. Resolve this the same way §4.4 resolved `WatchUserIdProvider`: define a narrow read-only interface in `:core:watch-api` —

```kotlin
// :core:watch-api
interface NotificationForwardingSettingsProvider {
    val settings: kotlinx.coroutines.flow.Flow<com.nexwatch.core.watchapi.notification.NotificationForwardingSettings>
}
```

  `NotificationForwardingPrefs` (`:core:data`) implements it directly (it already exposes a `settings` flow of the right type — just add `: NotificationForwardingSettingsProvider` to its class declaration). `:core:watch-fitcloud` depends only on the interface.

- [ ] **Step 1: Do the Task-4 file moves and the `NotificationForwardingSettingsProvider` interface add**

```bash
mkdir -p core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/notification
git mv core/service/src/main/kotlin/com/nexwatch/core/service/notification/IncomingNotification.kt core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/notification/IncomingNotification.kt
git mv core/service/src/main/kotlin/com/nexwatch/core/service/notification/NotificationFilterPipeline.kt core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/notification/NotificationFilterPipeline.kt
mkdir -p core/watch-api/src/test/kotlin/com/nexwatch/core/watchapi/notification
git mv core/service/src/test/kotlin/com/nexwatch/core/service/notification/NotificationFilterPipelineTest.kt core/watch-api/src/test/kotlin/com/nexwatch/core/watchapi/notification/NotificationFilterPipelineTest.kt
```

Edit the three moved files' `package` lines to `com.nexwatch.core.watchapi.notification`. In `NotificationFilterPipeline.kt`, replace the `NotificationForwardingSettings` class body with just:

```kotlin
data class NotificationForwardingSettings(
    val enabled: Boolean,
    val allowedPackages: Set<String>,
)
```

(kept in the same file as the pipeline that consumes it — no separate file needed, it's small). Then edit `core/data/.../NotificationForwardingPrefs.kt`: delete its own `NotificationForwardingSettings` declaration, add `import com.nexwatch.core.watchapi.notification.NotificationForwardingSettings`, and add `: NotificationForwardingSettingsProvider` to the class header. Create the provider interface:

```kotlin
// core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/notification/NotificationForwardingSettingsProvider.kt
package com.nexwatch.core.watchapi.notification

import kotlinx.coroutines.flow.Flow

interface NotificationForwardingSettingsProvider {
    val settings: Flow<NotificationForwardingSettings>
}
```

Add a Hilt binding in `:core:data` (new file `core/data/src/main/kotlin/com/nexwatch/core/data/di/NotificationModule.kt`):

```kotlin
package com.nexwatch.core.data.di

import com.nexwatch.core.data.notification.NotificationForwardingPrefs
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettingsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    @Singleton
    abstract fun bindNotificationForwardingSettingsProvider(
        impl: NotificationForwardingPrefs,
    ): NotificationForwardingSettingsProvider
}
```

- [ ] **Step 2: Update `core/service/build.gradle.kts`'s test deps and confirm the move compiles**

```bash
./gradlew :core:watch-api:test :core:data:test :core:service:test
```

Expected: PASS. Fix any stale imports the move left behind (`:core:service`'s old references to `com.nexwatch.core.service.notification.*`, if any tests referenced it before this task — there shouldn't be any yet since Task 5 is the first consumer in `:core:service`).

- [ ] **Step 3: Commit the relocation**

```bash
git add core/watch-api core/service core/data
git commit -m "refactor: move notification filter pipeline to :core:watch-api to satisfy module boundaries"
```

- [ ] **Step 4: Implement `NotificationForwarder`**

```kotlin
package com.nexwatch.core.watchfitcloud

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.notification.IncomingNotification
import com.nexwatch.core.watchapi.notification.NotificationFilterPipeline
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettingsProvider
import com.topstep.fitcloud.sdk.v2.FcSDK
import com.topstep.fitcloud.sdk.v2.utils.notification.AbsNotificationListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §8.5. Extends the SDK's own listener base so music-control wiring (built into
 * `AbsNotificationListenerService`) keeps working; NexWatch's own filter pipeline runs
 * inside `onNotificationPosted` before anything reaches `WatchClient`.
 */
@AndroidEntryPoint
class NotificationForwarder : AbsNotificationListenerService() {

    @Inject lateinit var watchClient: WatchClient
    @Inject lateinit var settingsProvider: NotificationForwardingSettingsProvider
    @Inject lateinit var dispatchers: CoroutineDispatchers

    private val pipeline = NotificationFilterPipeline()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    override fun getFcSDK(context: Context): FcSDK = FitCloudSdk.require()

    // The SDK asks this for its own type mapping; NexWatch does its own mapping inside
    // the pipeline (Task 4) and ignores whatever this returns for forwarding decisions.
    override fun getNotificationType(context: Context, sbn: StatusBarNotification): Int? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val incoming = sbn.toIncomingNotification()
        scope.launch {
            if (watchClient.state.value !is com.nexwatch.core.watchapi.WatchState.Ready) return@launch
            val settings = settingsProvider.settings.first()
            val outgoing = pipeline.evaluate(
                incoming = incoming,
                settings = settings,
                ownPackageName = applicationContext.packageName,
                nowMs = System.currentTimeMillis(),
            ) ?: return@launch
            watchClient.sendNotification(outgoing)
        }
    }

    private fun StatusBarNotification.toIncomingNotification(): IncomingNotification {
        val extras = notification.extras
        return IncomingNotification(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extractText(notification),
            category = notification.category,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    /** §8.5's extraction order: last MessagingStyle message, then EXTRA_BIG_TEXT, then EXTRA_TEXT. */
    private fun extractText(notification: Notification): String? {
        val messagingLines = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val lastMessage = messagingLines?.lastOrNull() as? android.os.Bundle
        return lastMessage?.getCharSequence("text")?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }
}
```

Verify `Notification.EXTRA_MESSAGES`' bundle key (`"text"`) against the platform docs for `Notification.MessagingStyle.Message` — this plan uses the documented raw-bundle key since `NotificationCompat.MessagingStyle.Message.getMessages()` isn't available without the AndroidX core library; if `:core:watch-fitcloud` already depends on `androidx.core` for other reasons, prefer `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages?.lastOrNull()?.text` instead, which is the documented, non-fragile API — check `core/watch-fitcloud/build.gradle.kts` for an existing `androidx.core:core` dependency before choosing.

- [ ] **Step 5: Manual verification (no unit test — this class only compiles/links against Android framework types unavailable to a JVM unit test)**

This step happens on-device in Task 10's manual verification pass, not here. Note it now so it isn't forgotten: post a WhatsApp-style notification via `adb shell cmd notification post`, confirm the watch receives it, confirm a second identical post within 60s is deduped, confirm a differently-worded post from the same app within 5s is throttled.

- [ ] **Step 6: Commit**

```bash
git add core/watch-fitcloud
git commit -m "feat(watch-fitcloud): implement NotificationForwarder over the §8.5 filter pipeline"
```

---

## Task 6: `WatchConnectionService`

**Files:**
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/WatchConnectionService.kt`
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/notification/ServiceNotifications.kt`
- Modify: `core/service/build.gradle.kts` — add `nexwatch.android.hilt` plugin, `:core:data` (already present), and `androidx.core:core-ktx` if not transitively available (check first)

**Interfaces:**
- Consumes: `WatchClient.state: StateFlow<WatchState>`, `WatchClient.events: SharedFlow<WatchEvent>`, `WatchClient.login()`, `WatchClient.notifyPhoneStatePermissionGranted()` (Task 2), `WatchIdentityStore.identity` (`:core:data`).
- Produces: `WatchConnectionService.start(context: Context)` companion helper other components (BootReceiver, CDM service, `:app`) call to ensure the service is running, using `ContextCompat.startForegroundService`.

- [ ] **Step 1: Apply the Hilt plugin to `:core:service`**

```kotlin
// core/service/build.gradle.kts
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.service"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

Check `libs.androidx.core.ktx` exists in `gradle/libs.versions.toml` under that exact alias (`Grep "core-ktx" gradle/libs.versions.toml`); if the alias differs, use the real one.

- [ ] **Step 2: Implement the notification-channel/builder helper**

```kotlin
package com.nexwatch.core.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.nexwatch.core.service.R

private const val CHANNEL_ID = "watch_connection"

/** §8.2: IMPORTANCE_LOW, silent, updated only on an actual text change. */
object ServiceNotifications {

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watch connection",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, statusText: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("NexWatch")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // placeholder; swap for a real app icon asset
            .setOngoing(true)
            .setSilent(true)
            .build()
}
```

Flag the placeholder icon explicitly rather than silently shipping it — `:core:designsystem` or `:app`'s `res/drawable` should have (or gain) a proper small icon before this ships; note it as a follow-up, don't block Phase 5 on icon design.

- [ ] **Step 3: Implement the service**

```kotlin
package com.nexwatch.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.service.notification.ServiceNotifications
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchEvent
import com.nexwatch.core.watchapi.WatchState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §8.2. Keeps the process alive so the SDK connection, notification forwarding and
 * telephony survive the app being swiped away. Starts in LOGIN mode only — BIND stays
 * confined to the guarded onboarding flow.
 */
@AndroidEntryPoint
class WatchConnectionService : Service() {

    @Inject lateinit var watchClient: WatchClient
    @Inject lateinit var identityStore: WatchIdentityStore

    private val scope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        ServiceNotifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            ServiceNotifications.build(this, "Connecting…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        ensureLoggedIn()
        observeState()
        observeEvents()
        return START_STICKY
    }

    private fun ensureLoggedIn() {
        scope.launch {
            val identity = identityStore.identity.first()
            val address = identity.boundAddress
            val profile = identity.profile
            if (identity.isBound && address != null && profile != null) {
                runCatching { watchClient.login(address, profile) }
            }
        }
    }

    private fun observeState() {
        scope.launch {
            watchClient.state.distinctUntilChanged().collectLatest { state ->
                val text = state.toStatusText()
                startForeground(
                    NOTIFICATION_ID,
                    ServiceNotifications.build(this@WatchConnectionService, text),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
                if (state is WatchState.Ready) {
                    watchClient.notifyPhoneStatePermissionGranted()
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            watchClient.events.collectLatest { event ->
                when (event) {
                    WatchEvent.FindPhoneRequested -> FindPhoneRinger.ring(this@WatchConnectionService)
                    WatchEvent.HangUpRequested -> CallHangUp.tryEndCall(this@WatchConnectionService)
                    WatchEvent.CameraOpenRequested, WatchEvent.CameraCloseRequested -> {
                        // Phase 8 (§12): camera remote is a watch-control settings feature.
                        // Nothing to do here yet — deliberately not stubbed further.
                    }
                }
            }
        }
    }

    private fun WatchState.toStatusText(): String = when (this) {
        is WatchState.Ready -> battery?.let { "Connected · $it%" } ?: "Connected"
        is WatchState.Connecting -> "Connecting…"
        is WatchState.Waiting -> "Waiting to reconnect…"
        is WatchState.BluetoothOff -> "Bluetooth off"
        is WatchState.Unbound -> "Not paired"
        is WatchState.AuthFailed -> "Connection failed"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WatchConnectionService::class.java))
        }
    }
}
```

Add `import kotlinx.coroutines.cancel` for `scope.cancel()`. `FindPhoneRinger` and `CallHangUp` are created in Task 7 — reference them here now, implement them next.

- [ ] **Step 4: Run the module build (service classes can't run as JVM unit tests — Android `Service` requires instrumentation)**

```bash
./gradlew :core:service:compileDebugKotlin
```

Expected: fails to compile until Task 7's `FindPhoneRinger`/`CallHangUp` exist — proceed to Task 7 before trying a clean compile of this file, or comment out those two calls temporarily and restore them once Task 7 lands. Prefer doing Task 7 immediately after this step in the same work session so the module compiles at each commit.

- [ ] **Step 5: Commit** (after Task 7 makes this compile)

```bash
git add core/service
git commit -m "feat(service): implement WatchConnectionService (§8.2)"
```

---

## Task 7: Find-phone ringer and call hang-up

§8.6: find-phone plays a ringtone through a short-lived notification; `HangUpRequested` ends the active call. Both are small, self-contained, framework-only pieces.

**Files:**
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/FindPhoneRinger.kt`
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/CallHangUp.kt`

- [ ] **Step 1: Implement `FindPhoneRinger`**

```kotlin
package com.nexwatch.core.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/**
 * §8.6: find-phone plays a ringtone. No wakelock (§9.2) — MediaPlayer on the default
 * ringtone URI plays fine with the screen off, and this stops itself once playback ends.
 */
object FindPhoneRinger {
    fun ring(context: Context) {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getValidRingtoneUri(context)
            ?: return
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(context, uri)
            setOnCompletionListener { it.release() }
            isLooping = false
            prepare()
            start()
        }
        // Stop automatically after 15s in case the ringtone is unexpectedly long/looping.
        android.os.Handler(context.mainLooper).postDelayed({
            if (player.isPlaying) player.stop()
            player.release()
        }, 15_000)
    }
}
```

- [ ] **Step 2: Implement `CallHangUp`**

```kotlin
package com.nexwatch.core.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * §8.6: the watch can ask to hang up the active call. `TelecomManager.endCall()` needs
 * ANSWER_PHONE_CALLS (API 28+) — silently does nothing without it rather than crashing,
 * since a missing permission here is a settings problem, not a bug to surface as a crash.
 */
object CallHangUp {
    fun tryEndCall(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val telecomManager = context.getSystemService<TelecomManager>() ?: return
        telecomManager.endCall()
    }
}
```

- [ ] **Step 3: Compile the module now that `WatchConnectionService` resolves**

```bash
./gradlew :core:service:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add core/service
git commit -m "feat(service): implement find-phone ringer and call hang-up (§8.6)"
```

---

## Task 8: `BootReceiver`

**Files:**
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/BootReceiver.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.nexwatch.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexwatch.core.data.identity.WatchIdentityStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §8.4: starts the service after a reboot or an app update, but only if a watch is
 * actually bound — an unbound install has nothing to reconnect to.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var identityStore: WatchIdentityStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (identityStore.identity.first().isBound) {
                    WatchConnectionService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :core:service:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add core/service
git commit -m "feat(service): implement BootReceiver (§8.4)"
```

---

## Task 9: `CompanionPresenceService` (API 31+)

**Files:**
- Create: `core/service/src/main/kotlin/com/nexwatch/core/service/CompanionPresenceService.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.nexwatch.core.service

import android.annotation.SuppressLint
import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint

/**
 * §8.3: on API 31+, the system watches for the associated device instead of the app
 * scanning for it. When the watch appears, make sure `WatchConnectionService` is running;
 * when it disappears, do nothing — the service already handles disconnection via
 * `WatchState`, and CDM's job is presence, not connection management.
 */
@RequiresApi(Build.VERSION_CODES.S)
@AndroidEntryPoint
class CompanionPresenceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: android.companion.AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        WatchConnectionService.start(this)
    }

    override fun onDeviceDisappeared(associationInfo: android.companion.AssociationInfo) {
        super.onDeviceDisappeared(associationInfo)
    }
}
```

- [ ] **Step 2: Add a CDM association call after a successful BIND**

CDM presence only fires for a device the app has associated via `CompanionDeviceManager.associate()`. Onboarding's pairing flow (`:feature:onboarding`) currently binds via `FcScanner`/`WatchClient.bind()` directly, with no CDM association at all — moving the *scanning* UI onto CDM's own picker is out of scope for this phase (§9.2's scan-only-on-the-pairing-screen rule is already satisfied by the existing `discoverWatches()`; CDM's picker would be a separate, larger onboarding rework). Scope this task to just the association call, so presence observation (Step 1) has something to observe:

Find the onboarding ViewModel's post-bind-success code path (`Glob "feature/onboarding/**/OnboardingViewModel.kt"`, then search for where `watchClient.bind(...)` succeeds and `identityStore.markBound(...)` is called). Immediately after that point, launch a CDM association request:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val deviceManager = context.getSystemService(CompanionDeviceManager::class.java)
    val request = AssociationRequest.Builder()
        .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
        .setSingleDevice(true)
        .build()
    deviceManager.associate(
        request,
        object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: android.content.IntentSender) {
                // Surface this to the UI as a system consent dialog to launch, same pattern
                // as a permission request — the exact wiring depends on OnboardingViewModel's
                // existing event/effect channel; follow that file's established pattern for
                // launching a system IntentSender rather than inventing a new one here.
            }
            override fun onFailure(error: CharSequence?) {
                // Non-fatal: CDM presence is a reliability improvement (§8.3), not a
                // requirement for the connection to work. Log and move on.
            }
        },
        null,
    )
}
```

This step touches `:feature:onboarding`, not `:core:service` — read `OnboardingViewModel.kt` in full before writing the actual patch so the event-channel wiring matches its existing style exactly rather than the sketch above. Requires `REQUEST_COMPANION_PROFILE_WATCH` in the manifest (Task 10).

- [ ] **Step 3: Compile**

```bash
./gradlew :core:service:compileDebugKotlin :feature:onboarding:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git add core/service feature/onboarding
git commit -m "feat(service): add CompanionPresenceService and post-bind CDM association (§8.3)"
```

---

## Task 10: `:app` wiring — manifest, permissions, remove `WatchAutoConnect`, Diagnostics screen

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/nexwatch/NexWatchApplication.kt` (remove `WatchAutoConnect` injection/call)
- Delete: `app/src/main/kotlin/com/nexwatch/watch/WatchAutoConnect.kt` (superseded by `WatchConnectionService`)
- Modify: `app/src/main/kotlin/com/nexwatch/MainActivity.kt` (start the service post-onboarding, call `notifyPhoneStatePermissionGranted()` on resume)
- Create: `app/src/main/kotlin/com/nexwatch/ui/diagnostics/DiagnosticsScreen.kt`
- Create: `app/src/main/kotlin/com/nexwatch/ui/diagnostics/DiagnosticsViewModel.kt`
- Create: `core/data/src/main/kotlin/com/nexwatch/core/data/diagnostics/DiagnosticsStore.kt` (+ DI wiring, same DataStore pattern as Task 3)

**Interfaces:**
- Produces: `DiagnosticsStore` with `val snapshot: Flow<DiagnosticsSnapshot>` (`lastConnectedAt: Long?`, `lastSyncAt: Long?`, `lastNotificationForwardedAt: Long?`) and `suspend fun recordConnected()/recordNotificationForwarded()`. `WatchConnectionService` calls `recordConnected()` on `Ready`; `NotificationForwarder` calls `recordNotificationForwarded()` after a successful send. `recordSync` is a no-op placeholder until Phase 6 — do not wire it to anything real yet (Task 0's descope applies here too).

- [ ] **Step 1: Manifest — permissions and components**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.REQUEST_COMPANION_PROFILE_WATCH" />
    <uses-permission android:name="android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND" />
    <uses-permission android:name="android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND" />

    <application
        android:name=".NexWatchApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.NexWatch">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.NexWatch"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name="com.nexwatch.core.service.WatchConnectionService"
            android:exported="false"
            android:foregroundServiceType="connectedDevice" />

        <service
            android:name="com.nexwatch.core.watchfitcloud.NotificationForwarder"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <service
            android:name="com.nexwatch.core.service.CompanionPresenceService"
            android:exported="true"
            android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE">
            <intent-filter>
                <action android:name="android.companion.CompanionDeviceService" />
            </intent-filter>
        </service>

        <receiver
            android:name="com.nexwatch.core.service.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
    </application>

</manifest>
```

Check whether `:app`'s `build.gradle.kts` already depends on `:core:service` and `:core:watch-fitcloud`; add them if not (`:app` may depend on everything per the module table, so this is just making sure the dependency line exists so the manifest merger can find these classes).

- [ ] **Step 2: Remove `WatchAutoConnect`**

```bash
git rm app/src/main/kotlin/com/nexwatch/watch/WatchAutoConnect.kt
git rm -r app/src/test/kotlin/com/nexwatch/watch  # if a WatchAutoConnectTest exists there; check first with Glob
```

Update `NexWatchApplication.kt`:

```kotlin
package com.nexwatch

import android.app.Application
import com.nexwatch.core.watchfitcloud.FitCloudSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NexWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // §4.1: every process start path goes through here. WatchConnectionService
        // (started from MainActivity, BootReceiver or CompanionPresenceService) now owns
        // the LOGIN reconnect that used to live here as WatchAutoConnect (§12 Phase 5).
        FitCloudSdk.initialize(this, verboseLogging = BuildConfig.DEBUG)
    }
}
```

- [ ] **Step 3: `MainActivity` — start the service if bound, request the telephony permission handshake on resume**

Read the existing `MainActivity.kt` and `AppRoot()`/nav graph to find where onboarding-complete is observed (likely `WatchIdentityStore.identity` collected somewhere in `:app`'s navigation root) before editing — don't duplicate that observation. Add an `onResume` override:

```kotlin
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch { watchClient.notifyPhoneStatePermissionGranted() }
        }
    }
```

This needs `@Inject lateinit var watchClient: WatchClient` on the activity (Hilt `@AndroidEntryPoint` already applied) and, separately, a call to `WatchConnectionService.start(this)` once `WatchIdentityStore.identity.first().isBound` is true — place that call wherever onboarding-completion is currently detected (likely the same spot `AppRoot()` decides to show the 4-tab shell instead of onboarding).

- [ ] **Step 4: `DiagnosticsStore`**

Follow the exact `NotificationForwardingPrefs` pattern from Task 3 (own DataStore file `diagnostics.preferences_pb`, its own qualifier annotation, `Long?` fields via `longPreferencesKey`, nullable by "key absent"):

```kotlin
package com.nexwatch.core.data.diagnostics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.nexwatch.core.common.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DiagnosticsSnapshot(
    val lastConnectedAt: Long?,
    val lastSyncAt: Long?,
    val lastNotificationForwardedAt: Long?,
)

/** §8.4's "debug screen showing last connected, last sync, last notification forwarded." */
class DiagnosticsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: CoroutineDispatchers,
) {
    val snapshot: Flow<DiagnosticsSnapshot> = dataStore.data.map { prefs ->
        DiagnosticsSnapshot(
            lastConnectedAt = prefs[LAST_CONNECTED_KEY],
            lastSyncAt = prefs[LAST_SYNC_KEY],
            lastNotificationForwardedAt = prefs[LAST_NOTIFICATION_KEY],
        )
    }

    suspend fun recordConnected(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[LAST_CONNECTED_KEY] = atMs }
        Unit
    }

    suspend fun recordNotificationForwarded(atMs: Long): Unit = withContext(dispatchers.io) {
        dataStore.edit { it[LAST_NOTIFICATION_KEY] = atMs }
        Unit
    }

    private companion object {
        val LAST_CONNECTED_KEY = longPreferencesKey("last_connected_at")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync_at")
        val LAST_NOTIFICATION_KEY = longPreferencesKey("last_notification_forwarded_at")
    }
}
```

Add its DataStore `@Provides` (own qualifier, e.g. `@DiagnosticsDataStore`) in `DataModule.kt`, following Task 3's exact pattern. Wire `WatchConnectionService.observeState()` to call `diagnosticsStore.recordConnected(System.currentTimeMillis())` inside the `is WatchState.Ready ->` branch (inject `DiagnosticsStore` alongside `identityStore`), and `NotificationForwarder.onNotificationPosted` to call `diagnosticsStore.recordNotificationForwarded(...)` right after a successful `watchClient.sendNotification(outgoing)`.

- [ ] **Step 5: `DiagnosticsViewModel` and `DiagnosticsScreen`**

Follow `WatchDebugViewModel`/`WatchDebugScreen`'s existing pattern exactly (read both files first). Sketch:

```kotlin
package com.nexwatch.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.diagnostics.DiagnosticsSnapshot
import com.nexwatch.core.data.diagnostics.DiagnosticsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    diagnosticsStore: DiagnosticsStore,
) : ViewModel() {
    val snapshot: StateFlow<DiagnosticsSnapshot> = diagnosticsStore.snapshot.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DiagnosticsSnapshot(null, null, null),
    )
}
```

```kotlin
package com.nexwatch.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    Column(Modifier.padding(16.dp)) {
        Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        DiagnosticRow("Last connected", snapshot.lastConnectedAt)
        DiagnosticRow("Last sync", snapshot.lastSyncAt)
        DiagnosticRow("Last notification forwarded", snapshot.lastNotificationForwardedAt)
    }
}

@Composable
private fun DiagnosticRow(label: String, atMs: Long?) {
    val text = atMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Never"
    Text("$label: $text", style = MaterialTheme.typography.bodyMedium)
}
```

Wire it into the existing debug menu/nav graph (`Glob "app/src/main/kotlin/com/nexwatch/navigation/**"`) next to wherever `WatchDebugScreen` is already reachable — match that exact routing pattern rather than inventing a new nav mechanism.

- [ ] **Step 6: Build and test the whole app module**

```bash
./gradlew assembleDebug test lint
```

Expected: PASS. Fix any fallout from the `WatchAutoConnect` removal (its Hilt injection site, any lingering test referencing it).

- [ ] **Step 7: Commit**

```bash
git add app core/data
git commit -m "feat(app): wire WatchConnectionService, remove WatchAutoConnect, add Diagnostics screen"
```

---

## Task 11: Manual on-device verification and soak test

This phase's exit criteria are explicitly not satisfiable by a green build alone (CLAUDE.md: "verify them for real"). Do not mark Phase 5 `Done` without this.

- [ ] **Step 1: Fresh install, pair, force-stop, confirm notifications and calls survive**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Pair through onboarding (BIND, guarded flow), grant notification-listener access (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`) and the phone-state/call/contacts/POST_NOTIFICATIONS runtime permissions, then:

```bash
adb shell am force-stop com.nexwatch
adb shell cmd notification post -S bigtext -t "Alice" "tag1" "Hello from WhatsApp" # simulate; real device test should use actual WhatsApp
```

Confirm the watch shows the notification with the app not running (`adb shell pidof com.nexwatch` empty beforehand, non-empty after the listener wakes the process).

Place a real call to the test phone; confirm it can be answered/hung up from the watch.

- [ ] **Step 2: Reboot survival**

```bash
adb reboot
```

Wait for boot, then without opening the app: `adb shell dumpsys activity services | grep WatchConnectionService` should show it running (via `BootReceiver`), and the watch should show `Ready` within a reasonable time.

- [ ] **Step 3: 48-hour soak**

Wear the watch normally for 48 hours with the phone used as a daily driver. At the end:

```bash
adb shell dumpsys batterystats --reset   # run at soak start, not end
adb shell dumpsys batterystats com.nexwatch   # at soak end
adb shell dumpsys meminfo com.nexwatch
adb shell dumpsys jobscheduler | grep -A5 com.nexwatch
```

Compare against §9.1's budgets (≤2%/day battery, ≤70MB PSS idle, ≤40 scheduled wakeups/day). Record actual figures in `docs/implementation-plan.md`'s Phase 5 section (add a "What landed" subsection matching Phase 4's style) whether they pass or not — a failed budget is a real finding, not something to omit.

- [ ] **Step 4: Update `docs/implementation-plan.md` §12**

Check both exit-criteria boxes only if Steps 1-3 actually passed on real hardware. Change the status cell from `In progress` to `Done`. If a budget was missed, record it as `Blocked (reason)` instead and describe what's needed to close the gap — do not mark `Done` with an unmet criterion.

- [ ] **Step 5: Commit**

```bash
git add docs/implementation-plan.md
git commit -m "docs(phase-5): record soak-test results and mark phase status"
```

Do not merge to `main` — per CLAUDE.md, the user merges the branch after reviewing.

---

## Self-Review Notes

- **Spec coverage:** §8.2 (Task 6), §8.3 (Task 9), §8.4 (Tasks 8, 11), §8.5 (Tasks 3-5), §8.6 (Tasks 2, 7), §8.7 (Task 0 — explicitly descoped with rationale, not silently dropped). §9.1 budgets verified in Task 11. §4.2's `WatchClient` extension in Task 2.
- **Known risk carried forward, not hidden:** Task 9's CDM association wiring is sketched, not fully specified, because it depends on `OnboardingViewModel`'s exact event-channel shape, which this plan's author has not read line-by-line. The task says so explicitly and tells the executor to read that file first rather than presenting invented code as final.
- **Module boundary correction:** Task 5 catches and fixes a boundary violation Task 4 would otherwise have shipped (`NotificationFilterPipeline` needing both `:core:data` and `:core:service` types it isn't allowed to see). Resolved by moving the pure logic to `:core:watch-api` and adding a narrow provider interface, mirroring the existing `WatchUserIdProvider` pattern from Phase 4.
