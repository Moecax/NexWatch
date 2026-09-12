# Phase 0 — Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the NexWatch repo into a building, multi-module Android project: a version catalog and `build-logic/` convention plugins, every module in the `CLAUDE.md` dependency table wired (most as empty shells; `:core:designsystem` and `:app` with real content), and a 4-tab `NavHost` (Today, Health, Watch, Data) with placeholder screens on the premium gradient background.

**Architecture:** Composite build (`build-logic`) supplies precompiled Kotlin-DSL convention plugins (`nexwatch.jvm.library`, `nexwatch.android.library`, `nexwatch.android.application`, `nexwatch.android.compose`) so every module's `build.gradle.kts` is a few lines applying a plugin plus its module-specific dependencies. Gradle's own dependency graph is the enforcement mechanism for module boundaries — a module that never declares a dependency on another module cannot import its types, so "illegal import fails the build" falls out of correct wiring rather than a separate tool.

**Tech Stack:** Kotlin 2.2.10, AGP 9.3.2, Jetpack Compose (BOM 2026.02.01) with Material 3, Navigation Compose 2.9.7 with type-safe routes via `kotlinx.serialization` 1.11.0, Hilt/Room/DataStore/WorkManager deferred to the phases that need them (see Global Constraints).

**Spec:** `docs/implementation-plan.md` §3 (Architecture), §12 Phase 0 row and its exit criteria; `CLAUDE.md` module map and dependency-rule table; `docs/design-prompt.md` Part A (tokens).

## Global Constraints

- Base package is `com.nexwatch` (`CLAUDE.md`). The existing `:app` module uses `io.github.moecax.nexwatch` and must be renamed as part of this plan.
- Module dependency rules are exactly the `CLAUDE.md` table: `:core:model`, `:core:watch-api`, `:core:sync-api` are pure Kotlin (no Android) and depend only on each other per the table; every other `:core:*` module is an Android library; `:app` may depend on anything.
- **Ruling (scope):** `:feature:*` modules are not created in this phase. The plan's 4 placeholder tabs live directly in `:app`; the first feature module (`:feature:onboarding`) is created in Phase 2 when there is real feature code to put in it. This avoids scaffolding module shells for features that don't exist yet.
- **Ruling (scope):** the version catalog gains entries only for what this phase's code actually compiles against (Navigation Compose, kotlinx-serialization). Hilt, Room, DataStore, WorkManager, Health Connect, a chart library, Timber and LeakCanary are added by the phase that first depends on them (Phase 1 onward), so their pinned versions are verified against real usage instead of guessed months ahead of use.
- Dark theme only, no dynamic color, colors from `docs/design-prompt.md` Part A — never hardcode a hex value inside a screen; every token lives in `:core:designsystem`.
- minSdk 26, compileSdk/targetSdk 37 (already set in the existing `:app` module — carry the same values into every Android module via the convention plugins).
- Every module's `build.gradle.kts` must match the dependency table exactly — no module declares a dependency it doesn't need yet, and no module is missing one it does need for the code in this plan.
- `./gradlew assembleDebug assembleRelease test lint` must all pass before this phase is done.

---

## Task 1: Version catalog and settings for the composite build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Produces: catalog aliases `libs.android.gradlePlugin`, `libs.kotlin.gradlePlugin`, `libs.androidx.navigation.compose`, `libs.kotlinx.serialization.json`, plugin alias `libs.plugins.kotlin.serialization`, and version aliases `libs.versions.androidCompileSdk`, `libs.versions.androidMinSdk`, `libs.versions.androidTargetSdk` (flat camelCase keys — Gradle only nests a catalog accessor when the alias itself contains a `-` or `.` separator, and these don't) — every later task and every convention plugin in Task 2 reads these.

- [ ] **Step 1: Add the new version and library/plugin entries to the catalog**

Edit `gradle/libs.versions.toml` to the following (keep every existing entry; these are additions):

```toml
[versions]
agp = "9.3.2"
coreKtx = "1.19.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.11.0"
activityCompose = "1.13.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
androidCompileSdk = "37"
androidMinSdk = "26"
androidTargetSdk = "37"
navigationCompose = "2.9.7"
kotlinxSerializationJson = "1.11.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Wire the composite build into settings**

Edit `settings.gradle.kts` so the `pluginManagement` block includes the composite build (this must be the first line inside `pluginManagement`, before `repositories`):

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NexWatch"
include(":app")
include(":core:common")
include(":core:model")
include(":core:watch-api")
include(":core:watch-fake")
include(":core:watch-fitcloud")
include(":core:database")
include(":core:data")
include(":core:export")
include(":core:sync-api")
include(":core:service")
include(":core:designsystem")
```

- [ ] **Step 3: Verify the catalog parses**

Run: `./gradlew help`
Expected: it fails at this point, because `build-logic` doesn't exist yet and the new module directories have no `build.gradle.kts` — but the failure must be about the missing `build-logic` composite build or missing module build files, **not** a TOML parse error. Read the error and confirm it's one of those two causes before moving on.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts
git commit -m "build: extend version catalog and wire the build-logic composite build"
```

---

## Task 2: build-logic composite build and convention plugins

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/nexwatch.jvm.library.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/nexwatch.android.library.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/nexwatch.android.application.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/nexwatch.android.compose.gradle.kts`

**Interfaces:**
- Consumes: catalog aliases from Task 1 (`libs.android.gradlePlugin`, `libs.kotlin.gradlePlugin`, `libs.versions.androidCompileSdk`, `libs.versions.androidMinSdk`, `libs.versions.androidTargetSdk`, `libs.plugins.kotlin.android`, `libs.plugins.kotlin.compose`, `libs.junit`).
- Produces: plugin IDs `nexwatch.jvm.library`, `nexwatch.android.library`, `nexwatch.android.application`, `nexwatch.android.compose` — every module task from here on applies one or two of these instead of raw AGP/Kotlin plugins.

- [ ] **Step 1: Create the build-logic settings file**

`build-logic/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

- [ ] **Step 2: Create the convention module's build script**

`build-logic/convention/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}

group = "com.nexwatch.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}
```

- [ ] **Step 3: Write the JVM library convention plugin (for `:core:model`, `:core:watch-api`, `:core:sync-api`)**

`build-logic/convention/src/main/kotlin/nexwatch.jvm.library.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    "testImplementation"(libs.junit)
}
```

- [ ] **Step 4: Write the Android library convention plugin**

`build-logic/convention/src/main/kotlin/nexwatch.android.library.gradle.kts`. Mirror the AGP DSL shape already used successfully in this repo's `app/build.gradle.kts` (the `compileSdk { version = release(...) }` block form), applied to `com.android.build.api.dsl.LibraryExtension` instead of `ApplicationExtension`. If `release(...)` is not available on `LibraryExtension.compileSdk` in AGP 9.3.2, fall back to the plain `compileSdk = <int>` assignment — both are acceptable, the requirement is that `compileSdk`, `minSdk` and Java 11 compile options end up set identically to `:app`:

```kotlin
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<LibraryExtension> {
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    "testImplementation"(libs.junit)
}
```

- [ ] **Step 5: Write the Android application convention plugin**

`build-logic/convention/src/main/kotlin/nexwatch.android.application.gradle.kts`:

```kotlin
import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

extensions.configure<ApplicationExtension> {
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    "testImplementation"(libs.junit)
}
```

- [ ] **Step 6: Write the Compose convention plugin (applied on top of android.library or android.application)**

`build-logic/convention/src/main/kotlin/nexwatch.android.compose.gradle.kts`:

```kotlin
import com.android.build.api.dsl.CommonExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<CommonExtension<*, *, *, *, *, *>> {
    buildFeatures {
        compose = true
    }
}

dependencies {
    val bom = libs.androidx.compose.bom
    "implementation"(platform(bom))
    "androidTestImplementation"(platform(bom))
}
```

- [ ] **Step 7: Verify build-logic compiles on its own**

Run: `./gradlew -p build-logic build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add build-logic
git commit -m "build: add convention plugins for jvm/android library, application and compose"
```

---

## Task 3: Scaffold the ten stub `:core:*` modules

**Files:**
- Create: `core/common/build.gradle.kts`, `core/common/src/main/AndroidManifest.xml`, `core/common/src/main/kotlin/com/nexwatch/core/common/package-info.kt`
- Create: `core/model/build.gradle.kts`, `core/model/src/main/kotlin/com/nexwatch/core/model/package-info.kt`
- Create: `core/watch-api/build.gradle.kts`, `core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/package-info.kt`
- Create: `core/watch-fake/build.gradle.kts`, `core/watch-fake/src/main/AndroidManifest.xml`, `core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/package-info.kt`
- Create: `core/watch-fitcloud/build.gradle.kts`, `core/watch-fitcloud/src/main/AndroidManifest.xml`, `core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/package-info.kt`
- Create: `core/database/build.gradle.kts`, `core/database/src/main/AndroidManifest.xml`, `core/database/src/main/kotlin/com/nexwatch/core/database/package-info.kt`
- Create: `core/data/build.gradle.kts`, `core/data/src/main/AndroidManifest.xml`, `core/data/src/main/kotlin/com/nexwatch/core/data/package-info.kt`
- Create: `core/export/build.gradle.kts`, `core/export/src/main/AndroidManifest.xml`, `core/export/src/main/kotlin/com/nexwatch/core/export/package-info.kt`
- Create: `core/sync-api/build.gradle.kts`, `core/sync-api/src/main/kotlin/com/nexwatch/core/syncapi/package-info.kt`
- Create: `core/service/build.gradle.kts`, `core/service/src/main/AndroidManifest.xml`, `core/service/src/main/kotlin/com/nexwatch/core/service/package-info.kt`

**Interfaces:**
- Consumes: plugin IDs from Task 2 (`nexwatch.jvm.library`, `nexwatch.android.library`).
- Produces: the ten module coordinates (`:core:common`, `:core:model`, `:core:watch-api`, `:core:watch-fake`, `:core:watch-fitcloud`, `:core:database`, `:core:data`, `:core:export`, `:core:sync-api`, `:core:service`) that later phases add real code and dependencies to. Each module's `namespace`/package is fixed now and must not change later: `com.nexwatch.core.common`, `com.nexwatch.core.model`, `com.nexwatch.core.watchapi`, `com.nexwatch.core.watchfake`, `com.nexwatch.core.watchfitcloud`, `com.nexwatch.core.database`, `com.nexwatch.core.data`, `com.nexwatch.core.export`, `com.nexwatch.core.syncapi`, `com.nexwatch.core.service`.

This is one batched task — every module below is the same shape (a build file plus a one-line documentation file proving the package compiles). Do all ten in one pass, one commit.

- [ ] **Step 1: Pure-Kotlin modules — `:core:model`, `:core:watch-api`, `:core:sync-api`**

`core/model/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.jvm.library")
}
```

`core/model/src/main/kotlin/com/nexwatch/core/model/package-info.kt`:
```kotlin
/**
 * Canonical health record types (§5.3 of the implementation plan). Pure Kotlin,
 * no Android — populated starting Phase 6.
 */
package com.nexwatch.core.model
```

`core/watch-api/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
}
```

`core/watch-api/src/main/kotlin/com/nexwatch/core/watchapi/package-info.kt`:
```kotlin
/**
 * The [WatchClient] contract and watch domain types (§4.2). Pure Kotlin, no SDK
 * types — populated starting Phase 1.
 */
package com.nexwatch.core.watchapi
```

`core/sync-api/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.jvm.library")
}

dependencies {
    implementation(project(":core:model"))
}
```

`core/sync-api/src/main/kotlin/com/nexwatch/core/syncapi/package-info.kt`:
```kotlin
/**
 * The [SyncProvider] contract (§7.1). Pure Kotlin — populated starting Phase 9.
 */
package com.nexwatch.core.syncapi
```

- [ ] **Step 2: Android modules with no `:core` dependencies — `:core:common`**

`core/common/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
}
```

`core/common/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

Add `namespace = "com.nexwatch.core.common"` to `core/common/build.gradle.kts` inside an `android { }` block:
```kotlin
plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.common"
}
```

`core/common/src/main/kotlin/com/nexwatch/core/common/package-info.kt`:
```kotlin
/**
 * Dispatchers, Clock, logging and Result types (§3.2). Populated starting
 * Phase 1 (dispatchers/clock needed by WatchIdentityStore).
 */
package com.nexwatch.core.common
```

- [ ] **Step 3: `:core:watch-fake` (depends on `:core:watch-api`)**

`core/watch-fake/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.watchfake"
}

dependencies {
    implementation(project(":core:watch-api"))
}
```

`core/watch-fake/src/main/AndroidManifest.xml`: same empty-manifest contents as Step 2.

`core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/package-info.kt`:
```kotlin
/**
 * FakeWatchClient, used for UI development and tests (§4.2). Populated in
 * Phase 1.
 */
package com.nexwatch.core.watchfake
```

- [ ] **Step 4: `:core:watch-fitcloud` (depends on `:core:watch-api`, `:core:common`)**

`core/watch-fitcloud/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.watchfitcloud"
}

dependencies {
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))
}
```

`core/watch-fitcloud/src/main/AndroidManifest.xml`: same empty-manifest contents as Step 2.

`core/watch-fitcloud/src/main/kotlin/com/nexwatch/core/watchfitcloud/package-info.kt`:
```kotlin
/**
 * FitCloudWatchClient — the only module allowed to import the FitCloud SDK
 * (CLAUDE.md, I1). The SDK is vendored in Phase 3 and this module implements
 * the real client in Phase 4. Stays empty until then.
 */
package com.nexwatch.core.watchfitcloud
```

- [ ] **Step 5: `:core:database` (depends on `:core:model`, `:core:common`)**

`core/database/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.database"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
```

`core/database/src/main/AndroidManifest.xml`: same empty-manifest contents as Step 2.

`core/database/src/main/kotlin/com/nexwatch/core/database/package-info.kt`:
```kotlin
/**
 * Room DB, entities, DAOs, triggers, migrations (§5). Populated in Phase 6.
 */
package com.nexwatch.core.database
```

- [ ] **Step 6: `:core:data` (depends on `:core:database`, `:core:watch-api`, `:core:sync-api`, `:core:model`, `:core:common`)**

`core/data/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
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
}
```

`core/data/src/main/AndroidManifest.xml`: same empty-manifest contents as Step 2.

`core/data/src/main/kotlin/com/nexwatch/core/data/package-info.kt`:
```kotlin
/**
 * Repositories, ingestion pipeline, aggregator, SyncEngine (§5, §7.2).
 * WatchIdentityStore lands here in Phase 1; the rest in Phase 6 onward.
 */
package com.nexwatch.core.data
```

- [ ] **Step 7: `:core:export` (depends on `:core:model`, `:core:data`)**

`core/export/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.export"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
}
```

`core/export/src/main/AndroidManifest.xml`: same empty-manifest contents as Step 2.

`core/export/src/main/kotlin/com/nexwatch/core/export/package-info.kt`:
```kotlin
/**
 * JSONL+ZIP, CSV and GPX exporters and the importer (§6). Populated in
 * Phase 7.
 */
package com.nexwatch.core.export
```

- [ ] **Step 8: `:core:service` (depends on `:core:data`, `:core:watch-api`, `:core:common`)**

`core/service/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.service"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))
}
```

`core/service/src/main/AndroidManifest.xml`: same empty-manifest contents as Step 2.

`core/service/src/main/kotlin/com/nexwatch/core/service/package-info.kt`:
```kotlin
/**
 * WatchConnectionService, NotificationForwarder, CompanionPresenceService,
 * BootReceiver, Workers (§8). Populated in Phase 5.
 */
package com.nexwatch.core.service
```

- [ ] **Step 9: Verify every stub module compiles**

Run: `./gradlew :core:common:assemble :core:model:assemble :core:watch-api:assemble :core:watch-fake:assemble :core:watch-fitcloud:assemble :core:database:assemble :core:data:assemble :core:export:assemble :core:sync-api:assemble :core:service:assemble`
Expected: BUILD SUCCESSFUL for all ten.

- [ ] **Step 10: Verify a module boundary actually holds**

`core/watch-fake/build.gradle.kts` declares no dependency on `:core:model`. Temporarily add the line `import com.nexwatch.core.model.PackageMarker` to `core/watch-fake/src/main/kotlin/com/nexwatch/core/watchfake/package-info.kt` (the symbol doesn't need to exist — the package itself isn't even on this module's classpath) and run `./gradlew :core:watch-fake:compileDebugKotlin`. Expected: it fails with an unresolved-reference/unresolved-package error, proving the boundary is real. Then revert the temporary line — do not commit it.

- [ ] **Step 11: Commit**

```bash
git add core
git commit -m "build: scaffold the ten stub core modules per the CLAUDE.md dependency table"
```

---

## Task 4: `:core:designsystem` — theme, tokens and premium background

**Files:**
- Create: `core/designsystem/build.gradle.kts`
- Create: `core/designsystem/src/main/AndroidManifest.xml`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Color.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/WatchColors.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Type.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Theme.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/PremiumBackground.kt`

**Interfaces:**
- Consumes: plugin IDs `nexwatch.android.library`, `nexwatch.android.compose` from Task 2.
- Produces: `WatchTheme` composable, `WatchTheme.colors` (a `WatchColors` accessor exposing `heartRate`, `activity`, `sleep`, `sleepText`, `calories`), `MaterialTheme.colorScheme` roles, `PremiumBackground` composable. Task 5 (`:app`) consumes all of these by name.

- [ ] **Step 1: Module wiring**

`core/designsystem/build.gradle.kts`:
```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.compose")
}

android {
    namespace = "com.nexwatch.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
}
```

`core/designsystem/src/main/AndroidManifest.xml`: same empty-manifest contents as Task 3 Step 2.

- [ ] **Step 2: Raw color tokens**

`core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Color.kt` — every hex value from `docs/design-prompt.md` Part A, and nowhere else in the app:

```kotlin
package com.nexwatch.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand
val ElectricBlue = Color(0xFF168CFF)
val DeepBlue = Color(0xFF1554D1)
val SkyBlue = Color(0xFF28B8FF)
val Cyan = Color(0xFF16D9E3)
val Aqua = Color(0xFF43F2C5)
val Mint = Color(0xFF38E8A5)

// Surfaces
val Midnight = Color(0xFF050816)
val DeepNavy = Color(0xFF080D20)
val Navy = Color(0xFF0D1428)
val BlueNavy = Color(0xFF121B35)
val MutedBlue = Color(0xFF243252)

// Text
val TextPrimary = Color(0xFFF7FAFF)
val TextSecondary = Color(0xFFA8B4CC)
val TextMuted = Color(0xFF687590)
val Slate = MutedBlue

// Status (status only — never for health data)
val Success = Color(0xFF20D889)
val Warning = Color(0xFFFFB52E)
val Error = Color(0xFFFF4969)

// Health (health data only — never for status)
val HeartRateColor = Color(0xFFFF4775)
val ActivityColor = Color(0xFF25D98B)
val SleepColor = Color(0xFF7655E8)
val SleepTextColor = Color(0xFF9A82FF)
val CaloriesColor = Color(0xFFFF8A32)

// Premium background gradient stops (top to bottom)
val PremiumGradientTop = Midnight
val PremiumGradientMid = Navy
val PremiumGradientBottom = Color(0xFF111B38)
```

- [ ] **Step 3: `WatchColors` — the health/status accessor CLAUDE.md requires**

`core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/WatchColors.kt`:

```kotlin
package com.nexwatch.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Health and status colors, kept out of MaterialTheme.colorScheme because
 * they are single-purpose (CLAUDE.md: "Status colors are for status only.
 * Health colors are for data only.") — mixing them into colorScheme's
 * primary/secondary/error roles would invite exactly the misuse those rules
 * forbid.
 */
data class WatchColors(
    val heartRate: Color,
    val activity: Color,
    val sleep: Color,
    val sleepText: Color,
    val calories: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val border: Color,
)

val DefaultWatchColors = WatchColors(
    heartRate = HeartRateColor,
    activity = ActivityColor,
    sleep = SleepColor,
    sleepText = SleepTextColor,
    calories = CaloriesColor,
    success = Success,
    warning = Warning,
    error = Error,
    border = MutedBlue,
)

val LocalWatchColors = staticCompositionLocalOf { DefaultWatchColors }
```

- [ ] **Step 4: Typography**

`core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Type.kt` — default system sans for now (Part A names Inter/Manrope/Plus Jakarta Sans as options; bundling a font family is not required for Phase 0's placeholder screens, so this uses `FontFamily.Default` and stays swappable):

```kotlin
package com.nexwatch.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WatchTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 56.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 48.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 40.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.4.sp),
)
```

- [ ] **Step 5: `WatchTheme`**

`core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Theme.kt`:

```kotlin
package com.nexwatch.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val NexWatchDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Midnight,
    secondary = Cyan,
    onSecondary = Midnight,
    tertiary = Mint,
    background = Midnight,
    onBackground = TextPrimary,
    surface = Navy,
    onSurface = TextPrimary,
    surfaceVariant = BlueNavy,
    onSurfaceVariant = TextSecondary,
    outline = Slate,
    error = Error,
    onError = TextPrimary,
)

/**
 * Dark-only theme (CLAUDE.md: "Dark theme only, no dynamic color") — there is
 * no light color scheme and no dynamicColor parameter.
 */
@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWatchColors provides DefaultWatchColors) {
        MaterialTheme(
            colorScheme = NexWatchDarkColorScheme,
            typography = WatchTypography,
            content = content,
        )
    }
}

object WatchTheme {
    val colors: WatchColors
        @Composable
        get() = LocalWatchColors.current
}
```

- [ ] **Step 6: `PremiumBackground`**

`core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/PremiumBackground.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.nexwatch.core.designsystem.theme.PremiumGradientBottom
import com.nexwatch.core.designsystem.theme.PremiumGradientMid
import com.nexwatch.core.designsystem.theme.PremiumGradientTop

/**
 * Paints the vertical gradient once at the screen root (design-prompt.md
 * §Premium background), so callers stack a transparent Scaffold on top
 * instead of each screen re-declaring the gradient.
 */
@Composable
fun PremiumBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumGradientTop, PremiumGradientMid, PremiumGradientBottom),
                    start = Offset(0f, 0f),
                    end = Offset(0f, Float.POSITIVE_INFINITY),
                ),
            ),
    ) {
        content()
    }
}
```

- [ ] **Step 7: Verify**

Run: `./gradlew :core:designsystem:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add core/designsystem
git commit -m "feat(designsystem): add WatchTheme, WatchColors and PremiumBackground"
```

---

## Task 5: `:app` — rename to `com.nexwatch`, 4-tab NavHost with type-safe routes

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Move+modify: `app/src/main/java/io/github/moecax/nexwatch/MainActivity.kt` → `app/src/main/kotlin/com/nexwatch/MainActivity.kt`
- Create: `app/src/main/kotlin/com/nexwatch/navigation/NexWatchDestination.kt`
- Create: `app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`
- Create: `app/src/main/kotlin/com/nexwatch/ui/PlaceholderScreens.kt`
- Delete: `app/src/main/java/io/github/moecax/nexwatch/ui/theme/Color.kt`, `Theme.kt`, `Type.kt` (superseded by `:core:designsystem`)
- Delete: `app/src/main/java/io/github/moecax/nexwatch/` (whole old package tree, once everything is moved)

**Interfaces:**
- Consumes: `com.nexwatch.core.designsystem.theme.WatchTheme`, `com.nexwatch.core.designsystem.component.PremiumBackground` (Task 4).
- Produces: `MainActivity`, four `@Serializable` route objects (`Today`, `Health`, `Watch`, `Data` under `NexWatchDestination`) — Phase 2 onward replaces the placeholder screens these routes point at, keeping the routes themselves.

- [ ] **Step 1: Rename the application ID and namespace**

Edit `app/build.gradle.kts`. Replace `io.github.moecax.nexwatch` with `com.nexwatch` in both `namespace` and `applicationId`, apply the new convention plugins, add navigation-compose and kotlinx-serialization, and switch to `nexwatch.android.compose` for the Compose setup:

```kotlin
plugins {
    id("nexwatch.android.application")
    id("nexwatch.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexwatch"

    defaultConfig {
        applicationId = "com.nexwatch"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

Keep whatever `compileSdk`/`defaultConfig`/`buildTypes`/`compileOptions` block shape the convention plugin from Task 2 already establishes — do not duplicate `compileSdk`, `minSdk` or `targetSdk` here, only what's shown above plus `applicationId`/`versionCode`/`versionName`, which are application-only and belong in this file, not the convention plugin.

- [ ] **Step 2: Move the source tree**

```bash
mkdir -p app/src/main/kotlin/com/nexwatch/navigation app/src/main/kotlin/com/nexwatch/ui
git mv app/src/main/java/io/github/moecax/nexwatch/MainActivity.kt app/src/main/kotlin/com/nexwatch/MainActivity.kt
git rm app/src/main/java/io/github/moecax/nexwatch/ui/theme/Color.kt app/src/main/java/io/github/moecax/nexwatch/ui/theme/Theme.kt app/src/main/java/io/github/moecax/nexwatch/ui/theme/Type.kt
```

If the `java/` directory is now empty, remove it: `git status` should show no leftover files under `app/src/main/java/`.

- [ ] **Step 3: Define the routes**

`app/src/main/kotlin/com/nexwatch/navigation/NexWatchDestination.kt`:

```kotlin
package com.nexwatch.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * One object per bottom-nav tab (design-prompt.md Part A: "bottom navigation
 * with 4 destinations: Today, Health, Watch, Data"). Each is a type-safe
 * Navigation Compose route — no string route matching anywhere in :app.
 */
sealed class NexWatchDestination(val label: String, val icon: ImageVector) {
    @Serializable
    data object Today : NexWatchDestination("Today", Icons.Filled.Dashboard)

    @Serializable
    data object Health : NexWatchDestination("Health", Icons.Filled.Favorite)

    @Serializable
    data object Watch : NexWatchDestination("Watch", Icons.Filled.Watch)

    @Serializable
    data object Data : NexWatchDestination("Data", Icons.Filled.Storage)

    companion object {
        val bottomNavItems = listOf(Today, Health, Watch, Data)
    }
}
```

This needs the Material Icons Extended artifact. Add it to `app/build.gradle.kts` dependencies:
```kotlin
    implementation("androidx.compose.material:material-icons-extended")
```
placed under the existing `implementation(platform(libs.androidx.compose.bom))` line so its version comes from the BOM.

- [ ] **Step 4: Placeholder screens**

`app/src/main/kotlin/com/nexwatch/ui/PlaceholderScreens.kt`:

```kotlin
package com.nexwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nexwatch.core.designsystem.theme.WatchTheme

/**
 * Stands in for the real per-tab screens until Phase 2 (onboarding) and the
 * later feature phases replace them one tab at a time.
 */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title)
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    WatchTheme {
        PlaceholderScreen(title = "Today")
    }
}
```

- [ ] **Step 5: The NavHost**

`app/src/main/kotlin/com/nexwatch/navigation/NexWatchNavHost.kt`:

```kotlin
package com.nexwatch.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.nexwatch.ui.PlaceholderScreen

@Composable
fun NexWatchNavHost() {
    val navController = rememberNavController()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = currentBackStackEntry?.destination

            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                NexWatchDestination.bottomNavItems.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(destination::class)
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexWatchDestination.Today,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<NexWatchDestination.Today> { PlaceholderScreen(title = "Today") }
            composable<NexWatchDestination.Health> { PlaceholderScreen(title = "Health") }
            composable<NexWatchDestination.Watch> { PlaceholderScreen(title = "Watch") }
            composable<NexWatchDestination.Data> { PlaceholderScreen(title = "Data") }
        }
    }
}
```

This needs `import androidx.navigation.NavDestination.Companion.hasRoute` and `androidx.navigation.NavDestination.hierarchy` — add:
```kotlin
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.hierarchy
```
at the top alongside the other imports.

- [ ] **Step 6: `MainActivity`**

Replace the contents of `app/src/main/kotlin/com/nexwatch/MainActivity.kt`:

```kotlin
package com.nexwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.navigation.NexWatchNavHost

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

- [ ] **Step 7: Update the manifest**

In `app/src/main/AndroidManifest.xml`, no package changes are needed (namespace comes from Gradle), but confirm `android:label="@string/app_name"` still resolves — check `app/src/main/res/values/strings.xml` still declares `app_name` as "NexWatch" (leave as-is if it already does).

- [ ] **Step 8: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Then run `./gradlew installDebug` if a device/emulator is attached, open the app, and confirm all four tabs (Today, Health, Watch, Data) switch between placeholder screens on the dark gradient background. If no device is attached, skip the install step but record that it was skipped.

- [ ] **Step 9: Commit**

```bash
git add -A app
git commit -m "feat(app): rename to com.nexwatch package and add the 4-tab type-safe NavHost"
```

---

## Task 6: Release build (R8) and lint

**Files:**
- Modify: `app/build.gradle.kts`

**Interfaces:**
- None — this task only changes build configuration, no new symbols.

- [ ] **Step 1: Enable optimization for the release build type**

In `app/build.gradle.kts`, inside the `android { buildTypes { release { ... } } }` block, flip the existing `optimization { enable = false }` to `enable = true` (this is the AGP 9 DSL already present in this file — do not introduce the older `isMinifyEnabled` property, which AGP 9's `ApplicationExtension` does not expose the same way). If AGP 9's `optimization` block requires an explicit proguard/keep-rules reference to run at all, add one pointing at `getDefaultProguardFile("proguard-android-optimize.txt")` plus `app/proguard-rules.pro` (create an empty `proguard-rules.pro` with a one-line comment if it doesn't already exist) using whatever API `optimization { }` exposes for that in this AGP version — inspect the AGP 9.3.2 `ApplicationExtension`/`Optimization` API via your IDE's autocomplete or `./gradlew :app:help` error messages if the exact property name isn't obvious from the existing file.

- [ ] **Step 2: Verify the release build**

Run: `./gradlew :app:assembleRelease`
Expected: BUILD SUCCESSFUL, and the resulting APK/AAB is smaller or equal to the debug one (shrinking ran). If it fails with a missing-class or reflection error, that means R8 stripped something Compose needs — add the minimal consumer/keep rule to `app/proguard-rules.pro` to fix it (Compose's own AARs ship most of the consumer rules they need already, so this should not require much).

- [ ] **Step 3: Lint**

Run: `./gradlew lint`
Expected: BUILD SUCCESSFUL with no new lint errors introduced by this phase (pre-existing warnings from the default template are fine; do not silence them beyond what's needed to pass).

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/proguard-rules.pro
git commit -m "build(app): enable R8 optimization for release builds"
```

---

## Task 7: Full verification and close out the phase

**Files:**
- Modify: `docs/implementation-plan.md`

**Interfaces:**
- None.

- [ ] **Step 1: Run the full verification suite**

Run: `./gradlew assembleDebug assembleRelease test lint`
Expected: BUILD SUCCESSFUL for all four. If anything fails, fix it in this task (do not defer to a later phase — Phase 0's exit criteria requires this exact command to pass).

- [ ] **Step 2: Re-confirm the module-boundary exit criterion end to end**

Confirm Task 3 Step 10 actually ran and its temporary illegal import was reverted before committing: `git log -p -- core/watch-fake` should show no illegal `import com.nexwatch.core.model...` line in any commit, and `git grep "com.nexwatch.core.model" -- core/watch-fake` should return nothing. This is the proof that "an illegal import fails the build" — the earlier step demonstrated the failure, this step confirms the demonstration didn't leak into the committed tree.

- [ ] **Step 3: Update the implementation plan's status table**

In `docs/implementation-plan.md`, change the Phase 0 row in the §12 table from `Not started` to `Done`, and check every box under "Phase 0 — Scaffold" → **Exit criteria**:
```markdown
| 0 | Scaffold | `phase-0-scaffold` | Done |
```
```markdown
**Exit criteria**
- [x] `./gradlew assembleDebug assembleRelease test lint` all pass.
- [x] The app installs and shows all four placeholder tabs on the premium gradient background.
- [x] Every module boundary in the `CLAUDE.md` table is enforced — an illegal import fails the build, not just a code review.
```

- [ ] **Step 4: Commit**

```bash
git add docs/implementation-plan.md
git commit -m "docs: mark Phase 0 (Scaffold) done"
```
