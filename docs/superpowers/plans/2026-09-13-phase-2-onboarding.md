# Phase 2 — Onboarding Design System & UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the B1 onboarding/pairing flow (Welcome → Profile → Permissions → Find your watch → Pair confirmation → Pairing progress → Keep it running) as idiomatic Compose in a new `:feature:onboarding` module, driven by one `OnboardingViewModel` over `WatchClient`, gated behind `WatchIdentityStore.isBound`.

**Architecture:** Stateless `XScreen(state, onEvent)` composables per step, each independently previewable; one `OnboardingViewModel` owns a `StateFlow<OnboardingUiState>` and an `OnboardingStep` state machine; a new `OnboardingNavHost` sequences the steps; `:app` picks that host or the existing `NexWatchNavHost` based on `WatchIdentityStore.isBound`. Shared visual primitives (buttons, motion, shapes) land in `:core:designsystem` first; onboarding-only composites stay in `:feature:onboarding`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines/Flow, JUnit4 + kotlinx-coroutines-test + Turbine for tests. No new dependencies beyond what's already in the version catalog.

**Spec:** `docs/superpowers/specs/2026-09-13-phase-2-onboarding-design.md`

## Global Constraints

- Dark theme only, no dynamic color, no hardcoded hex values in screens (CLAUDE.md).
- Text on primary buttons is Midnight `#050816`, never white; Deep Blue is never text/icon color (CLAUDE.md).
- Color is never the only signal — pair with icon or label (CLAUDE.md).
- Base package `com.nexwatch`; this module's package is `com.nexwatch.feature.onboarding`.
- `bind()` is destructive and guarded; it is only ever called from the confirmed pairing step (CLAUDE.md, §4.4).
- Motion curves are never the platform defaults: `ease-out` = `cubic-bezier(.23,1,.32,1)`, `ease-in-out` = `cubic-bezier(.77,0,.175,1)`, `ease-drawer` = `cubic-bezier(.32,.72,0,1)` (design-prompt.md Part C).
- Press feedback is on press, not release: `scale(0.97)`, ~150ms, strong ease-out (design-prompt.md Part C) — non-negotiable per pressable component.
- Every screen plays one authored entrance (staggered opacity+translate+blur-clear, 60ms step, 500–600ms total), once, never on every recomposition (design-prompt.md Part C).
- `minSdk 26`; permission requirements are version-gated.
- Every screen and every documented state gets a `@Preview`.
- Build/test loop for every task: `./gradlew :feature:onboarding:testDebugUnitTest` (or the relevant module) then, once wiring lands, `./gradlew assembleDebug`.

---

### Task 1: Scaffold `:feature:onboarding` module

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/onboarding/build.gradle.kts`
- Create: `feature/onboarding/src/main/AndroidManifest.xml`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/.gitkeep` (placeholder so the source set exists; removed once Task 5 adds real files)

**Interfaces:**
- Produces: a buildable, empty Android library module `:feature:onboarding` that later tasks add sources to.

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, add after `include(":core:designsystem")`:

```kotlin
include(":feature:onboarding")
```

- [ ] **Step 2: Write the module's build script**

Create `feature/onboarding/build.gradle.kts`:

```kotlin
plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.compose")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.feature.onboarding"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 3: Write the module manifest**

Create `feature/onboarding/src/main/AndroidManifest.xml`. Declare every permission the Permissions screen (Task 9) will request, version-gated with `android:maxSdkVersion`/`minSdkVersion` handled at request time, not in the manifest (the manifest simply declares the permission exists):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

</manifest>
```

- [ ] **Step 4: Create an empty source set placeholder**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/.gitkeep` (empty file) so Gradle sees a `main` Kotlin source set before Task 5 adds real classes.

- [ ] **Step 5: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts feature/onboarding
git commit -m "feat(onboarding): scaffold :feature:onboarding module"
```

---

### Task 2: Design-system motion and shape tokens

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Motion.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Shape.kt`
- Test: `core/designsystem/src/test/kotlin/com/nexwatch/core/designsystem/theme/MotionTest.kt`

**Interfaces:**
- Produces: `WatchMotion.easeOutStrong`, `WatchMotion.easeInOutStrong`, `WatchMotion.easeDrawer` (all `androidx.compose.animation.core.Easing`), `WatchMotion.PRESS_DURATION_MS`, `WatchMotion.ENTRANCE_STAGGER_STEP_MS`, `WatchMotion.ENTRANCE_ITEM_DURATION_MS`, `WatchMotion.SHEET_DURATION_MS` (all `Int`); `WatchShapes.card`, `WatchShapes.control`, `WatchShapes.pill` (all `androidx.compose.ui.graphics.Shape`).

`:core:designsystem`'s existing `build.gradle.kts` only declares `androidx.compose.ui`/`ui-graphics`/`material3`; `CubicBezierEasing` lives in `androidx.compose.animation:animation-core`, which those already transitively pull in via the Compose BOM used elsewhere — no new dependency needed, but this task's first build is the check.

- [ ] **Step 1: Write the failing test**

Create `core/designsystem/src/test/kotlin/com/nexwatch/core/designsystem/theme/MotionTest.kt`:

```kotlin
package com.nexwatch.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {

    @Test
    fun `ease-out strong matches the design-prompt cubic-bezier`() {
        // cubic-bezier(.23, 1, .32, 1) sampled at t=0.5 must land near 1.05
        // (strong ease-out overshoots past 1 before settling), not the ~0.5
        // a linear or default Compose easing would give — this is the
        // regression check that we didn't accidentally wire up FastOutSlowIn.
        val eased = WatchMotion.easeOutStrong.transform(0.5f)
        assertEquals(1.05f, eased, 0.05f)
    }

    @Test
    fun `press duration is within the 100 to 160 ms budget`() {
        assertEquals(150, WatchMotion.PRESS_DURATION_MS)
    }

    @Test
    fun `entrance choreography matches Part C timings`() {
        assertEquals(60, WatchMotion.ENTRANCE_STAGGER_STEP_MS)
        assertEquals(500, WatchMotion.ENTRANCE_ITEM_DURATION_MS)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*.MotionTest"`
Expected: FAIL — `WatchMotion` is unresolved.

- [ ] **Step 3: Implement `WatchMotion`**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Motion.kt`:

```kotlin
package com.nexwatch.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * design-prompt.md Part C: the platform's default easings ("FastOutSlowIn" etc.) read as
 * too weak to register as intentional motion. These three named curves are the only
 * easings this app uses for authored motion.
 */
object WatchMotion {
    /** Anything entering, or responding to input (buttons, reveals). */
    val easeOutStrong: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Anything moving/morphing on screen (segmented control thumb, ring fill). */
    val easeInOutStrong: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Sheets and drawers (iOS-style). */
    val easeDrawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    const val PRESS_DURATION_MS = 150
    const val ENTRANCE_STAGGER_STEP_MS = 60
    const val ENTRANCE_ITEM_DURATION_MS = 500
    const val SHEET_DURATION_MS = 350
}
```

- [ ] **Step 4: Implement `WatchShapes`**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Shape.kt`:

```kotlin
package com.nexwatch.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** design-prompt.md: cards use 20–24dp corner radius; controls use a tighter radius. */
object WatchShapes {
    val card: Shape = RoundedCornerShape(22.dp)
    val control: Shape = RoundedCornerShape(14.dp)
    val pill: Shape = RoundedCornerShape(50)
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*.MotionTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Motion.kt \
        core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/theme/Shape.kt \
        core/designsystem/src/test/kotlin/com/nexwatch/core/designsystem/theme/MotionTest.kt
git commit -m "feat(designsystem): add WatchMotion and WatchShapes tokens"
```

---

### Task 3: `pressScale()` modifier and staggered entrance

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/PressScale.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/StaggeredEntrance.kt`

**Interfaces:**
- Consumes: `WatchMotion.easeOutStrong`, `WatchMotion.PRESS_DURATION_MS`, `WatchMotion.ENTRANCE_STAGGER_STEP_MS`, `WatchMotion.ENTRANCE_ITEM_DURATION_MS` (Task 2).
- Produces: `Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier`; `@Composable fun EntranceItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit)`.

No dedicated unit test — this is Compose UI wiring with no pure logic to assert against in a JVM unit test (`graphicsLayer`/`animate*AsState` require a composition). It is exercised visually through every screen's `@Preview` in later tasks; that is this task's verification.

- [ ] **Step 1: Implement `pressScale()`**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/PressScale.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.nexwatch.core.designsystem.theme.WatchMotion
import kotlinx.coroutines.flow.collectLatest

private const val PRESSED_SCALE = 0.97f
private const val RESTING_SCALE = 1f

/**
 * design-prompt.md Part C: "every pressable element responds on press, not on release" —
 * scale(0.97), ~150ms, strong ease-out. One implementation so every button/card in the app
 * shares the exact same feel instead of each screen reinventing it slightly differently.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    if (LocalInspectionMode.current) return this // keep static previews readable
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffectPressState(interactionSource) { pressed = it }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else RESTING_SCALE,
        animationSpec = tween(WatchMotion.PRESS_DURATION_MS, easing = WatchMotion.easeOutStrong),
        label = "pressScale",
    )
    return this.graphicsLayer(scaleX = scale, scaleY = scale)
}

@Composable
private fun LaunchedEffectPressState(interactionSource: InteractionSource, onPressed: (Boolean) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> onPressed(true)
                is PressInteraction.Release, is PressInteraction.Cancel -> onPressed(false)
            }
        }
    }
}
```

- [ ] **Step 2: Implement staggered entrance**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/StaggeredEntrance.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.WatchMotion

/**
 * design-prompt.md Part C: every screen plays one authored entrance, content staggered in
 * (opacity + ~10dp translate, 60ms stagger step, 500ms per item), once, when the screen
 * first appears — never replayed on recomposition. [index] is the child's position in the
 * staggered sequence (0-based); pass it in the same order the content reads top to bottom.
 *
 * Blur-clear from Part C is intentionally omitted: Compose's render-effect blur requires
 * API 31+ with no first-class fallback, and would need its own gating story. Opacity +
 * translate alone still reads as an authored entrance; revisit blur when a later phase
 * needs the API-31 gate for other reasons.
 */
@Composable
fun EntranceItem(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val inPreview = LocalInspectionMode.current
    var played by remember { mutableFloatStateOf(if (inPreview) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!inPreview) {
            kotlinx.coroutines.delay(index * WatchMotion.ENTRANCE_STAGGER_STEP_MS.toLong())
            played = 1f
        }
    }
    val progress by animateFloatAsState(
        targetValue = played,
        animationSpec = tween(WatchMotion.ENTRANCE_ITEM_DURATION_MS, easing = WatchMotion.easeOutStrong),
        label = "entrance",
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .alpha(progress)
            .offset(y = ((1f - progress) * 10).dp),
    ) {
        content()
    }
}
```

- [ ] **Step 3: Verify the module still builds**

Run: `./gradlew :core:designsystem:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/PressScale.kt \
        core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/StaggeredEntrance.kt
git commit -m "feat(designsystem): add pressScale modifier and staggered entrance"
```

---

### Task 4: Design-system primitives (buttons, segmented control, stepper, status chip)

**Files:**
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/Buttons.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/SegmentedControl.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/NumberStepper.kt`
- Create: `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/StatusChip.kt`

**Interfaces:**
- Consumes: `WatchMotion`, `WatchShapes` (Task 2), `Modifier.pressScale()` (Task 3), `WatchTheme.colors` (existing).
- Produces: `@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`; `@Composable fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`; `@Composable fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier)`; `@Composable fun NumberStepper(label: String, value: Int, unit: String, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, step: Int = 1, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE)`; `enum class StatusTone { SUCCESS, WARNING, ERROR, NEUTRAL }`; `@Composable fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Implement buttons**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/Buttons.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.Midnight
import com.nexwatch.core.designsystem.theme.WatchShapes

/** CLAUDE.md: text on primary buttons is Midnight, never white. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = WatchShapes.control,
        colors = ButtonDefaults.buttonColors(contentColor = Midnight),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interactionSource),
    ) {
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = WatchShapes.control,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(interactionSource),
    ) {
        Text(text)
    }
}
```

- [ ] **Step 2: Implement `SegmentedControl`**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/SegmentedControl.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.Midnight
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes

/** design-prompt.md Batch 1: sex is a segmented control; the thumb slides with `easeInOutStrong`. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.control)
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = tween(200, easing = WatchMotion.easeInOutStrong),
            label = "segmentThumb",
        )
        Box(
            Modifier
                .offset(x = thumbOffset)
                .height(36.dp)
                .width(segmentWidth)
                .background(MaterialTheme.colorScheme.primary, WatchShapes.control),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Text(
                    text = label,
                    color = if (selected) Midnight else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(index) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Implement `NumberStepper`**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/NumberStepper.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.WatchMotion

/**
 * design-prompt.md Part C: "value changes ... cross-fade through a few px of blur rather
 * than snapping" — AnimatedContent's fade stands in for the blur-clear here for the same
 * API-31 reason noted on EntranceItem; the cross-fade is the load-bearing part of that rule.
 */
@Composable
fun NumberStepper(
    label: String,
    value: Int,
    unit: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        StepperButton(symbol = "–", enabled = value - step >= range.first) {
            onValueChange((value - step).coerceIn(range))
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                fadeIn(tween(150, easing = WatchMotion.easeOutStrong)) togetherWith
                    fadeOut(tween(150, easing = WatchMotion.easeOutStrong))
            },
            label = "stepperValue",
        ) { animatedValue ->
            Text(
                text = "$animatedValue",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepperButton(symbol = "+", enabled = value + step <= range.last) {
            onValueChange((value + step).coerceIn(range))
        }
    }
}

@Composable
private fun RowScope.StepperButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        modifier = Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
```

- [ ] **Step 4: Implement `StatusChip`**

Create `core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/StatusChip.kt`:

```kotlin
package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

enum class StatusTone { SUCCESS, WARNING, ERROR, NEUTRAL }

/** CLAUDE.md: "color is never the only signal" — every status chip carries an icon and label. */
@Composable
fun StatusChip(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        StatusTone.SUCCESS -> WatchTheme.colors.success
        StatusTone.WARNING -> WatchTheme.colors.warning
        StatusTone.ERROR -> WatchTheme.colors.error
        StatusTone.NEUTRAL -> WatchTheme.colors.border
    }
    val icon = when (tone) {
        StatusTone.SUCCESS -> Icons.Filled.CheckCircle
        StatusTone.WARNING -> Icons.Filled.Warning
        StatusTone.ERROR -> Icons.Filled.Error
        StatusTone.NEUTRAL -> Icons.Filled.CheckCircle
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.16f), WatchShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}
```

- [ ] **Step 5: Verify the module builds**

Run: `./gradlew :core:designsystem:assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any import mistakes flagged by the compiler (the inline notes above call out the ones easy to miss).

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/Buttons.kt \
        core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/SegmentedControl.kt \
        core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/NumberStepper.kt \
        core/designsystem/src/main/kotlin/com/nexwatch/core/designsystem/component/StatusChip.kt
git commit -m "feat(designsystem): add button, segmented control, stepper and status chip primitives"
```

---

### Task 5: Onboarding domain types

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingStep.kt`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingUiState.kt`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingEvent.kt`
- Delete: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/.gitkeep`

**Interfaces:**
- Consumes: `UserProfile`, `UserProfile.Sex` (`:core:watch-api`).
- Produces: `sealed interface OnboardingStep` with objects `Welcome`, `Profile`, `Permissions`, `FindWatch`, `PairConfirm`, data class `Pairing(val phase: PairingPhase)`, object `KeepRunning`; `enum class PairingPhase { CONNECTING, AUTHENTICATING, READING_FEATURES, FIRST_SYNC, SUCCESS, FAILED }`; `enum class PermissionItem { BLUETOOTH, NOTIFICATIONS, NOTIFICATION_ACCESS, PHONE_AND_CONTACTS, LOCATION }`; `enum class PermissionStatus { NOT_GRANTED, GRANTED, SKIPPED }`; `data class DiscoveredDevice(val address: String, val displayName: String, val signalBars: Int)`; `data class ProfileInput(val sex: UserProfile.Sex, val age: Int, val heightCm: Int, val weightKg: Int)` with `fun toUserProfile(): UserProfile`; `data class OnboardingUiState(...)` (full shape below); `sealed interface OnboardingEvent` (full shape below).

- [ ] **Step 1: Remove the placeholder file**

```bash
git rm feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/.gitkeep
```

- [ ] **Step 2: Write `OnboardingStep.kt`**

```kotlin
package com.nexwatch.feature.onboarding

/** The seven B1 screens (docs/design-prompt.md Batch 1), in flow order. */
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object Profile : OnboardingStep
    data object Permissions : OnboardingStep
    data object FindWatch : OnboardingStep
    data object PairConfirm : OnboardingStep
    data class Pairing(val phase: PairingPhase) : OnboardingStep
    data object KeepRunning : OnboardingStep
}

/**
 * bind() (CLAUDE.md §4.4) is one suspend call; these sub-phases are synthesized by the
 * ViewModel around it (README: capabilities becoming non-null = "reading watch features",
 * syncHealthData() progress = "first sync") so the Pairing screen can show the B1 step list.
 */
enum class PairingPhase { CONNECTING, AUTHENTICATING, READING_FEATURES, FIRST_SYNC, SUCCESS, FAILED }
```

- [ ] **Step 3: Write `OnboardingUiState.kt`**

```kotlin
package com.nexwatch.feature.onboarding

import com.nexwatch.core.watchapi.UserProfile

enum class PermissionItem { BLUETOOTH, NOTIFICATIONS, NOTIFICATION_ACCESS, PHONE_AND_CONTACTS, LOCATION }

enum class PermissionStatus { NOT_GRANTED, GRANTED, SKIPPED }

data class DiscoveredDevice(val address: String, val displayName: String, val signalBars: Int)

data class ProfileInput(
    val sex: UserProfile.Sex = UserProfile.Sex.MALE,
    val age: Int = 30,
    val heightCm: Int = 170,
    val weightKg: Int = 70,
) {
    fun toUserProfile() = UserProfile(sex = sex, age = age, heightCm = heightCm, weightKg = weightKg)
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val profile: ProfileInput = ProfileInput(),
    val permissions: Map<PermissionItem, PermissionStatus> =
        PermissionItem.entries.associateWith { PermissionStatus.NOT_GRANTED },
    val isScanning: Boolean = false,
    val scanTimedOut: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDevice: DiscoveredDevice? = null,
    val bindUnderstoodChecked: Boolean = false,
    val pairedBattery: Int? = null,
    val pairedFirmwareVersion: String? = null,
    val pairingError: String? = null,
) {
    val grantedPermissionCount: Int
        get() = permissions.values.count { it == PermissionStatus.GRANTED || it == PermissionStatus.SKIPPED }
}
```

- [ ] **Step 4: Write `OnboardingEvent.kt`**

```kotlin
package com.nexwatch.feature.onboarding

import com.nexwatch.core.watchapi.UserProfile

sealed interface OnboardingEvent {
    data object GetStarted : OnboardingEvent
    data class SexChanged(val sex: UserProfile.Sex) : OnboardingEvent
    data class AgeChanged(val age: Int) : OnboardingEvent
    data class HeightChanged(val heightCm: Int) : OnboardingEvent
    data class WeightChanged(val weightKg: Int) : OnboardingEvent
    data object ProfileContinue : OnboardingEvent
    data class PermissionResult(val item: PermissionItem, val granted: Boolean) : OnboardingEvent
    data class PermissionSkipped(val item: PermissionItem) : OnboardingEvent
    data object PermissionsContinue : OnboardingEvent
    data object StartScan : OnboardingEvent
    data class DeviceSelected(val device: DiscoveredDevice) : OnboardingEvent
    data object BackToFindWatch : OnboardingEvent
    data object BindUnderstoodToggled : OnboardingEvent
    data object ConfirmPair : OnboardingEvent
    data object PairingContinue : OnboardingEvent
    data object RetryPairing : OnboardingEvent
    data object FinishOnboarding : OnboardingEvent
}
```

- [ ] **Step 5: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingStep.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingUiState.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingEvent.kt
git commit -m "feat(onboarding): add onboarding domain types"
```

---

### Task 6: Permission catalog

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionCatalog.kt`
- Test: `feature/onboarding/src/test/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionCatalogTest.kt`

**Interfaces:**
- Consumes: `PermissionItem` (Task 5).
- Produces: `object PermissionCatalog { fun runtimePermissions(item: PermissionItem, sdkInt: Int): List<String>; fun isSpecialAccess(item: PermissionItem): Boolean; fun isOptional(item: PermissionItem): Boolean }`.

`android.Manifest.permission.*` constants are compile-time `String` constants present in the unit-test Android stub jar (they are not method bodies Robolectric would need to fake), so this is safely unit-testable without instrumentation.

- [ ] **Step 1: Write the failing test**

Create `feature/onboarding/src/test/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionCatalogTest.kt`:

```kotlin
package com.nexwatch.feature.onboarding.permissions

import android.Manifest
import android.os.Build
import com.nexwatch.feature.onboarding.PermissionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCatalogTest {

    @Test
    fun `bluetooth requires no runtime permission below API 31`() {
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissions(PermissionItem.BLUETOOTH, sdkInt = 30))
    }

    @Test
    fun `bluetooth requires scan and connect from API 31`() {
        val result = PermissionCatalog.runtimePermissions(PermissionItem.BLUETOOTH, sdkInt = Build.VERSION_CODES.S)
        assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), result)
    }

    @Test
    fun `notifications require POST_NOTIFICATIONS from API 33`() {
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissions(PermissionItem.NOTIFICATIONS, sdkInt = 32))
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            PermissionCatalog.runtimePermissions(PermissionItem.NOTIFICATIONS, sdkInt = Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun `phone and contacts is four permissions at every supported SDK level`() {
        val result = PermissionCatalog.runtimePermissions(PermissionItem.PHONE_AND_CONTACTS, sdkInt = 26)
        assertEquals(
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS,
            ),
            result,
        )
    }

    @Test
    fun `notification access is a special access, not a runtime permission`() {
        assertTrue(PermissionCatalog.isSpecialAccess(PermissionItem.NOTIFICATION_ACCESS))
        assertEquals(emptyList<String>(), PermissionCatalog.runtimePermissions(PermissionItem.NOTIFICATION_ACCESS, sdkInt = 34))
    }

    @Test
    fun `only location is optional`() {
        assertTrue(PermissionCatalog.isOptional(PermissionItem.LOCATION))
        assertFalse(PermissionCatalog.isOptional(PermissionItem.BLUETOOTH))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.PermissionCatalogTest"`
Expected: FAIL — `PermissionCatalog` is unresolved.

- [ ] **Step 3: Implement `PermissionCatalog`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionCatalog.kt`:

```kotlin
package com.nexwatch.feature.onboarding.permissions

import android.Manifest
import android.os.Build
import com.nexwatch.feature.onboarding.PermissionItem

/**
 * Maps each B1 permission checklist item (docs/design-prompt.md Batch 1) to the actual
 * Android permission strings it needs, version-gated. `sdkInt` is a parameter rather than
 * reading `Build.VERSION.SDK_INT` directly so this stays a pure, unit-testable function.
 */
object PermissionCatalog {

    fun runtimePermissions(item: PermissionItem, sdkInt: Int): List<String> = when (item) {
        PermissionItem.BLUETOOTH ->
            if (sdkInt >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                emptyList()
            }

        PermissionItem.NOTIFICATIONS ->
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }

        PermissionItem.NOTIFICATION_ACCESS -> emptyList()

        PermissionItem.PHONE_AND_CONTACTS -> listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )

        PermissionItem.LOCATION -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /** Notification access has no runtime dialog; it's granted through a Settings deep link. */
    fun isSpecialAccess(item: PermissionItem): Boolean = item == PermissionItem.NOTIFICATION_ACCESS

    /** Every checklist item is required except location, which the Permissions screen lets you skip. */
    fun isOptional(item: PermissionItem): Boolean = item == PermissionItem.LOCATION
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.PermissionCatalogTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionCatalog.kt \
        feature/onboarding/src/test/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionCatalogTest.kt
git commit -m "feat(onboarding): add version-gated permission catalog"
```

---

### Task 7: `OnboardingViewModel`

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingViewModel.kt`
- Test: `feature/onboarding/src/test/kotlin/com/nexwatch/feature/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Consumes: `WatchClient`, `WatchNotReadyException` (`:core:watch-api`); `WatchIdentityStore` (`:core:data`); `FakeWatchClient` (test-only, `:core:watch-fake` test dependency); `OnboardingStep`, `OnboardingUiState`, `OnboardingEvent`, `PairingPhase`, `DiscoveredDevice`, `PermissionItem`, `PermissionStatus` (Task 5).
- Produces: `class OnboardingViewModel(watchClient: WatchClient, watchIdentityStore: WatchIdentityStore) : ViewModel() { val uiState: StateFlow<OnboardingUiState>; fun onEvent(event: OnboardingEvent) }`.

Add `testImplementation(project(":core:watch-fake"))` to `feature/onboarding/build.gradle.kts` for this task's test only — the production module still never depends on it (Global Constraints / spec §1).

- [ ] **Step 1: Add the test-only watch-fake dependency**

In `feature/onboarding/build.gradle.kts`, add under the existing `testImplementation` lines:

```kotlin
testImplementation(project(":core:watch-fake"))
```

- [ ] **Step 2: Write the failing test**

Create `feature/onboarding/src/test/kotlin/com/nexwatch/feature/onboarding/OnboardingViewModelTest.kt`:

```kotlin
package com.nexwatch.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.core.watchfake.FakeWatchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Minimal in-memory Preferences DataStore — same shape as the fake used in WatchIdentityStoreTest. */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/** CoroutineDispatchers is an interface (`:core:common`) with no test fake shipped yet — this is it. */
private object UnconfinedDispatchers : CoroutineDispatchers {
    override val io = Dispatchers.Unconfined
    override val default = Dispatchers.Unconfined
}

class OnboardingViewModelTest {

    private fun viewModel(client: FakeWatchClient = FakeWatchClient()) =
        OnboardingViewModel(client, WatchIdentityStore(InMemoryPreferencesDataStore(), UnconfinedDispatchers)) to client

    @Test
    fun `starts on Welcome`() = runTest {
        val (viewModel, _) = viewModel()
        assertEquals(OnboardingStep.Welcome, viewModel.uiState.value.step)
    }

    @Test
    fun `Get started advances to Profile`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        assertEquals(OnboardingStep.Profile, viewModel.uiState.value.step)
    }

    @Test
    fun `profile continue advances to Permissions with edited values retained`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.SexChanged(UserProfile.Sex.FEMALE))
        viewModel.onEvent(OnboardingEvent.AgeChanged(41))
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        val state = viewModel.uiState.value
        assertEquals(OnboardingStep.Permissions, state.step)
        assertEquals(UserProfile.Sex.FEMALE, state.profile.sex)
        assertEquals(41, state.profile.age)
    }

    @Test
    fun `permissions continue advances to FindWatch and starting a scan populates devices`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        assertEquals(OnboardingStep.FindWatch, viewModel.uiState.value.step)
        viewModel.onEvent(OnboardingEvent.StartScan)
        val state = viewModel.uiState.value
        assertTrue(state.discoveredDevices.isNotEmpty())
    }

    @Test
    fun `selecting a device and confirming requires the understood checkbox`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        viewModel.onEvent(OnboardingEvent.StartScan)
        val device = viewModel.uiState.value.discoveredDevices.first()
        viewModel.onEvent(OnboardingEvent.DeviceSelected(device))
        assertEquals(OnboardingStep.PairConfirm, viewModel.uiState.value.step)

        viewModel.onEvent(OnboardingEvent.ConfirmPair)
        assertEquals(OnboardingStep.PairConfirm, viewModel.uiState.value.step) // unchanged: checkbox not ticked

        viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled)
        viewModel.onEvent(OnboardingEvent.ConfirmPair)
        assertTrue(viewModel.uiState.value.step is OnboardingStep.Pairing)
    }

    @Test
    fun `confirming pair drives PairingPhase through to SUCCESS against the fake client`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        viewModel.onEvent(OnboardingEvent.StartScan)
        val device = viewModel.uiState.value.discoveredDevices.first()
        viewModel.onEvent(OnboardingEvent.DeviceSelected(device))
        viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled)

        viewModel.uiState.test {
            skipItems(1) // current PairConfirm state before the event below
            viewModel.onEvent(OnboardingEvent.ConfirmPair)
            // Every intermediate phase is emitted in order, ending on SUCCESS.
            val phases = mutableListOf<PairingPhase>()
            while (phases.lastOrNull() != PairingPhase.SUCCESS) {
                val step = awaitItem().step
                if (step is OnboardingStep.Pairing) phases += step.phase
            }
            assertEquals(
                listOf(
                    PairingPhase.CONNECTING,
                    PairingPhase.AUTHENTICATING,
                    PairingPhase.READING_FEATURES,
                    PairingPhase.FIRST_SYNC,
                    PairingPhase.SUCCESS,
                ),
                phases,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishing onboarding continues to KeepRunning then completes`() = runTest {
        val (viewModel, client) = viewModel()
        viewModel.onEvent(OnboardingEvent.GetStarted)
        viewModel.onEvent(OnboardingEvent.ProfileContinue)
        viewModel.onEvent(OnboardingEvent.PermissionsContinue)
        viewModel.onEvent(OnboardingEvent.StartScan)
        val device = viewModel.uiState.value.discoveredDevices.first()
        viewModel.onEvent(OnboardingEvent.DeviceSelected(device))
        viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled)
        viewModel.onEvent(OnboardingEvent.ConfirmPair)
        while (viewModel.uiState.value.step != OnboardingStep.Pairing(PairingPhase.SUCCESS)) { /* let coroutines resolve */ }
        viewModel.onEvent(OnboardingEvent.PairingContinue)
        assertEquals(OnboardingStep.KeepRunning, viewModel.uiState.value.step)
        assertTrue(client.state.value is com.nexwatch.core.watchapi.WatchState.Ready)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.OnboardingViewModelTest"`
Expected: FAIL — `OnboardingViewModel` is unresolved.

- [ ] **Step 4: Implement `OnboardingViewModel`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingViewModel.kt`:

```kotlin
package com.nexwatch.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchNotReadyException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val watchClient: WatchClient,
    private val watchIdentityStore: WatchIdentityStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.GetStarted -> goTo(OnboardingStep.Profile)

            is OnboardingEvent.SexChanged -> updateProfile { copy(sex = event.sex) }
            is OnboardingEvent.AgeChanged -> updateProfile { copy(age = event.age) }
            is OnboardingEvent.HeightChanged -> updateProfile { copy(heightCm = event.heightCm) }
            is OnboardingEvent.WeightChanged -> updateProfile { copy(weightKg = event.weightKg) }
            OnboardingEvent.ProfileContinue -> goTo(OnboardingStep.Permissions)

            is OnboardingEvent.PermissionResult -> setPermission(
                event.item,
                if (event.granted) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED,
            )
            is OnboardingEvent.PermissionSkipped -> setPermission(event.item, PermissionStatus.SKIPPED)
            OnboardingEvent.PermissionsContinue -> goTo(OnboardingStep.FindWatch)

            OnboardingEvent.StartScan -> startScan()
            is OnboardingEvent.DeviceSelected -> {
                _uiState.update { it.copy(selectedDevice = event.device, step = OnboardingStep.PairConfirm) }
            }
            OnboardingEvent.BackToFindWatch -> goTo(OnboardingStep.FindWatch)

            OnboardingEvent.BindUnderstoodToggled -> _uiState.update {
                it.copy(bindUnderstoodChecked = !it.bindUnderstoodChecked)
            }
            OnboardingEvent.ConfirmPair -> confirmPair()
            OnboardingEvent.RetryPairing -> confirmPair()
            OnboardingEvent.PairingContinue -> goTo(OnboardingStep.KeepRunning)
            OnboardingEvent.FinishOnboarding -> Unit // :app observes WatchIdentityStore.isBound to leave onboarding
        }
    }

    private fun goTo(step: OnboardingStep) = _uiState.update { it.copy(step = step) }

    private fun updateProfile(transform: ProfileInput.() -> ProfileInput) =
        _uiState.update { it.copy(profile = it.profile.transform()) }

    private fun setPermission(item: PermissionItem, status: PermissionStatus) = _uiState.update {
        it.copy(permissions = it.permissions + (item to status))
    }

    /**
     * WatchClient has no discovery method (bind()/login() take an address directly) — real
     * discovery is Companion Device Manager, landing in Phase 5. This simulates a short,
     * fixed device list so the Find-your-watch screen has something to select from.
     */
    private fun startScan() {
        _uiState.update { it.copy(isScanning = true, scanTimedOut = false, discoveredDevices = emptyList()) }
        viewModelScope.launch {
            delay(SCAN_RESULT_DELAY_MS)
            val device = DiscoveredDevice(address = FAKE_ADDRESS, displayName = "GTR 3 Pro", signalBars = 3)
            _uiState.update { it.copy(isScanning = false, discoveredDevices = listOf(device)) }
        }
    }

    private fun confirmPair() {
        val state = _uiState.value
        val device = state.selectedDevice ?: return
        if (!state.bindUnderstoodChecked) return
        viewModelScope.launch { runPairing(device.address, state.profile.toUserProfile()) }
    }

    private suspend fun runPairing(address: String, profile: com.nexwatch.core.watchapi.UserProfile) {
        _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.CONNECTING), pairingError = null) }
        try {
            watchIdentityStore.ensureUserId()
            watchClient.bind(address, profile)

            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.AUTHENTICATING)) }
            delay(PHASE_STEP_DELAY_MS)

            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.READING_FEATURES)) }
            watchClient.capabilities.filterNotNull().first()

            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.FIRST_SYNC)) }
            watchClient.syncHealthData().collect { }

            watchIdentityStore.markBound(address)
            val battery = watchClient.batteryLevel()
            _uiState.update {
                it.copy(
                    step = OnboardingStep.Pairing(PairingPhase.SUCCESS),
                    pairedBattery = battery,
                    pairedFirmwareVersion = watchClient.capabilities.value?.firmwareVersion,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WatchNotReadyException) {
            _uiState.update { it.copy(step = OnboardingStep.Pairing(PairingPhase.FAILED), pairingError = e.message) }
        }
    }

    private companion object {
        const val FAKE_ADDRESS = "AA:BB:CC:DD:EE:FF"
        const val SCAN_RESULT_DELAY_MS = 400L
        const val PHASE_STEP_DELAY_MS = 300L
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :feature:onboarding:testDebugUnitTest --tests "*.OnboardingViewModelTest"`
Expected: PASS. If the `SUCCESS`-phase test is flaky under `runTest`'s virtual time, replace the `while` polling loop in `finishing onboarding...` with the same `uiState.test { }` Turbine pattern used in the phase-sequence test above, awaiting items until `Pairing(SUCCESS)`.

- [ ] **Step 6: Commit**

```bash
git add feature/onboarding/build.gradle.kts \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingViewModel.kt \
        feature/onboarding/src/test/kotlin/com/nexwatch/feature/onboarding/OnboardingViewModelTest.kt
git commit -m "feat(onboarding): add OnboardingViewModel state machine"
```

---

### Task 8: Welcome and Profile screens

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/WelcomeScreen.kt`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/ProfileScreen.kt`

**Interfaces:**
- Consumes: `PrimaryButton`, `SegmentedControl`, `NumberStepper`, `EntranceItem`, `PremiumBackground`, `WatchTheme` (designsystem); `ProfileInput`, `OnboardingUiState` (Task 5).
- Produces: `@Composable fun WelcomeScreen(onGetStarted: () -> Unit)`; `@Composable fun ProfileScreen(profile: ProfileInput, onSexChanged: (UserProfile.Sex) -> Unit, onAgeChanged: (Int) -> Unit, onHeightChanged: (Int) -> Unit, onWeightChanged: (Int) -> Unit, onContinue: () -> Unit)`.

Reference `docs/design/NexWatch B1 Onboarding and Pairing (polished).html` lines ~700–800 for the exact copy and layout these two screens reproduce (hero render placement, headline copy, stepper rows) — the Kotlin below implements the same structure and states idiomatically rather than porting HTML/CSS (CLAUDE.md design system rules).

- [ ] **Step 1: Implement `WelcomeScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/WelcomeScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.theme.WatchTheme

/** design-prompt.md Batch 1 #1 — Welcome. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        EntranceItem(index = 0) { WatchHeroRender(modifier = Modifier.padding(bottom = 32.dp)) }
        EntranceItem(index = 1) {
            Text(
                text = "NexWatch",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
        }
        EntranceItem(index = 2) {
            Text(
                text = "Your watch. Your data. On your phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
            )
        }
        EntranceItem(index = 3) {
            PrimaryButton(text = "Get started", onClick = onGetStarted)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    WatchTheme { PremiumBackground { WelcomeScreen(onGetStarted = {}) } }
}
```

- [ ] **Step 2: Implement the shared watch hero render**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/WatchHeroRender.kt` (used by Welcome and later by Pairing):

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.ElectricBlue
import com.nexwatch.core.designsystem.theme.Navy
import com.nexwatch.core.designsystem.theme.SkyBlue

/**
 * design-prompt.md: "a generic round smartwatch ... no brand names, no logos". Drawn with
 * Canvas rather than a bitmap asset (CLAUDE.md 9.4: the service graph loads no bitmaps —
 * shared code between onboarding and pairing-progress, so keep this vector-based).
 */
@Composable
fun WatchHeroRender(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(200.dp)) {
        val bezelStroke = size.minDimension * 0.06f
        drawCircle(
            brush = Brush.linearGradient(listOf(SkyBlue, ElectricBlue)),
            radius = size.minDimension / 2f - bezelStroke / 2f,
            style = Stroke(width = bezelStroke),
        )
        drawCircle(
            color = Navy,
            radius = size.minDimension / 2f - bezelStroke,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}
```

- [ ] **Step 3: Implement `ProfileScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/ProfileScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.NumberStepper
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SegmentedControl
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.feature.onboarding.ProfileInput

/** design-prompt.md Batch 1 #2 — Your profile. */
@Composable
fun ProfileScreen(
    profile: ProfileInput,
    onSexChanged: (UserProfile.Sex) -> Unit,
    onAgeChanged: (Int) -> Unit,
    onHeightChanged: (Int) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Your profile", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "The watch uses this for calorie and distance calculations.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
        EntranceItem(index = 2) {
            SegmentedControl(
                options = listOf("Male", "Female"),
                selectedIndex = if (profile.sex == UserProfile.Sex.MALE) 0 else 1,
                onSelected = { onSexChanged(if (it == 0) UserProfile.Sex.MALE else UserProfile.Sex.FEMALE) },
            )
        }
        EntranceItem(index = 3) {
            NumberStepper("Age", profile.age, "yrs", onAgeChanged, range = 10..100)
        }
        EntranceItem(index = 4) {
            NumberStepper("Height", profile.heightCm, "cm", onHeightChanged, range = 100..230)
        }
        EntranceItem(index = 5) {
            NumberStepper("Weight", profile.weightKg, "kg", onWeightChanged, range = 30..200)
        }
        EntranceItem(index = 6, modifier = Modifier.padding(top = 24.dp)) {
            PrimaryButton(text = "Continue", onClick = onContinue)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    WatchTheme {
        PremiumBackground {
            ProfileScreen(ProfileInput(), {}, {}, {}, {}, {})
        }
    }
}
```

- [ ] **Step 4: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/WelcomeScreen.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/WatchHeroRender.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/ProfileScreen.kt
git commit -m "feat(onboarding): add Welcome and Profile screens"
```

---

### Task 9: Permissions screen

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PermissionsScreen.kt`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionRuntime.kt`

**Interfaces:**
- Consumes: `PermissionCatalog` (Task 6); `PermissionItem`, `PermissionStatus` (Task 5); `StatusChip`, `PrimaryButton`, `EntranceItem` (designsystem).
- Produces: `@Composable fun PermissionsScreen(permissions: Map<PermissionItem, PermissionStatus>, onPermissionResult: (PermissionItem, Boolean) -> Unit, onPermissionSkipped: (PermissionItem) -> Unit, onContinue: () -> Unit)`; `object PermissionRuntime { fun isNotificationListenerEnabled(context: Context): Boolean; fun notificationListenerSettingsIntent(): Intent }`.

- [ ] **Step 1: Implement `PermissionRuntime`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionRuntime.kt`:

```kotlin
package com.nexwatch.feature.onboarding.permissions

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** The one item in the checklist that isn't a runtime permission (docs spec §4). */
object PermissionRuntime {
    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun notificationListenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
```

- [ ] **Step 2: Implement `PermissionsScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PermissionsScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.PermissionItem
import com.nexwatch.feature.onboarding.PermissionStatus
import com.nexwatch.feature.onboarding.permissions.PermissionCatalog
import com.nexwatch.feature.onboarding.permissions.PermissionRuntime

private data class PermissionCopy(val title: String, val reason: String)

private val PERMISSION_COPY = mapOf(
    PermissionItem.BLUETOOTH to PermissionCopy("Nearby devices", "To find and connect to your watch."),
    PermissionItem.NOTIFICATIONS to PermissionCopy("Notifications", "So the app can show connection and sync status."),
    PermissionItem.NOTIFICATION_ACCESS to PermissionCopy("Notification access", "To forward phone notifications to your watch."),
    PermissionItem.PHONE_AND_CONTACTS to PermissionCopy("Phone & contacts", "For caller ID and rejecting calls from the watch."),
    PermissionItem.LOCATION to PermissionCopy("Location", "Optional — used only for local weather on the watch."),
)

/** design-prompt.md Batch 1 #3 — Permissions checklist. */
@Composable
fun PermissionsScreen(
    permissions: Map<PermissionItem, PermissionStatus>,
    onPermissionResult: (PermissionItem, Boolean) -> Unit,
    onPermissionSkipped: (PermissionItem) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val grantedCount = permissions.values.count { it != PermissionStatus.NOT_GRANTED }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "$grantedCount of ${permissions.size} granted",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(PermissionItem.entries.toList()) { item ->
                val copy = PERMISSION_COPY.getValue(item)
                val status = permissions.getValue(item)
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    onPermissionResult(item, results.values.all { it })
                }
                PermissionRow(
                    title = copy.title,
                    reason = copy.reason,
                    status = status,
                    optional = PermissionCatalog.isOptional(item),
                    onGrant = {
                        when {
                            PermissionCatalog.isSpecialAccess(item) -> {
                                context.startActivity(PermissionRuntime.notificationListenerSettingsIntent())
                                onPermissionResult(item, PermissionRuntime.isNotificationListenerEnabled(context))
                            }
                            else -> {
                                val required = PermissionCatalog.runtimePermissions(item, Build.VERSION.SDK_INT)
                                if (required.isEmpty()) onPermissionResult(item, true) else launcher.launch(required.toTypedArray())
                            }
                        }
                    },
                    onSkip = { onPermissionSkipped(item) },
                )
            }
        }
        EntranceItem(index = PermissionItem.entries.size + 1, modifier = Modifier.padding(top = 16.dp)) {
            PrimaryButton(text = "Continue", onClick = onContinue)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    status: PermissionStatus,
    optional: Boolean,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (status) {
            PermissionStatus.GRANTED -> StatusChip("Granted", StatusTone.SUCCESS)
            PermissionStatus.SKIPPED -> StatusChip("Skipped", StatusTone.NEUTRAL)
            PermissionStatus.NOT_GRANTED -> {
                Row {
                    if (optional) {
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }
                    TextButton(onClick = onGrant) { Text("Grant") }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Partial grant")
@Composable
private fun PermissionsScreenPartialPreview() {
    WatchTheme {
        PremiumBackground {
            PermissionsScreen(
                permissions = mapOf(
                    PermissionItem.BLUETOOTH to PermissionStatus.GRANTED,
                    PermissionItem.NOTIFICATIONS to PermissionStatus.GRANTED,
                    PermissionItem.NOTIFICATION_ACCESS to PermissionStatus.NOT_GRANTED,
                    PermissionItem.PHONE_AND_CONTACTS to PermissionStatus.NOT_GRANTED,
                    PermissionItem.LOCATION to PermissionStatus.SKIPPED,
                ),
                onPermissionResult = { _, _ -> },
                onPermissionSkipped = {},
                onContinue = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "All granted")
@Composable
private fun PermissionsScreenGrantedPreview() {
    WatchTheme {
        PremiumBackground {
            PermissionsScreen(
                permissions = PermissionItem.entries.associateWith { PermissionStatus.GRANTED },
                onPermissionResult = { _, _ -> },
                onPermissionSkipped = {},
                onContinue = {},
            )
        }
    }
}
```

- [ ] **Step 3: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL. If `ActivityResultContracts` fails to resolve, confirm `implementation(libs.androidx.activity.compose)` is present in `feature/onboarding/build.gradle.kts` (added in Task 1).

- [ ] **Step 4: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PermissionsScreen.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/permissions/PermissionRuntime.kt
git commit -m "feat(onboarding): add Permissions screen with real runtime permission requests"
```

---

### Task 10: Find your watch and Pair confirmation screens

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/FindWatchScreen.kt`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PairConfirmScreen.kt`

**Interfaces:**
- Consumes: `DiscoveredDevice` (Task 5); `PrimaryButton`, `SecondaryButton`, `EntranceItem`, `WatchMotion` (designsystem).
- Produces: `@Composable fun FindWatchScreen(isScanning: Boolean, scanTimedOut: Boolean, devices: List<DiscoveredDevice>, onStartScan: () -> Unit, onDeviceSelected: (DiscoveredDevice) -> Unit)`; `@Composable fun PairConfirmScreen(device: DiscoveredDevice, understood: Boolean, onUnderstoodToggled: () -> Unit, onCancel: () -> Unit, onPair: () -> Unit)`.

- [ ] **Step 1: Implement `FindWatchScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/FindWatchScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.theme.ElectricBlue
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.DiscoveredDevice

/** design-prompt.md Batch 1 #4 — Find your watch. */
@Composable
fun FindWatchScreen(
    isScanning: Boolean,
    scanTimedOut: Boolean,
    devices: List<DiscoveredDevice>,
    onStartScan: () -> Unit,
    onDeviceSelected: (DiscoveredDevice) -> Unit,
) {
    LaunchedEffect(Unit) { if (!isScanning && devices.isEmpty()) onStartScan() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        EntranceItem(index = 0) {
            Text("Find your watch", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1, modifier = Modifier.padding(vertical = 32.dp)) {
            RadarScanner(active = isScanning)
        }
        when {
            devices.isNotEmpty() -> devices.forEachIndexed { index, device ->
                EntranceItem(index = 2 + index) {
                    DeviceRow(device, onClick = { onDeviceSelected(device) })
                }
            }
            scanTimedOut -> EntranceItem(index = 2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing found after 30 s", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Make sure the watch isn't connected to another app. Keep it within 1 m.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    PrimaryButton(text = "Scan again", onClick = onStartScan, modifier = Modifier.padding(top = 16.dp))
                }
            }
            else -> EntranceItem(index = 2) {
                Text("Scanning…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RadarScanner(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "radarProgress",
    )
    Canvas(Modifier.size(160.dp)) {
        val maxRadius = size.minDimension / 2f
        if (active) {
            listOf(0f, 0.33f, 0.66f).forEach { phaseOffset ->
                val phase = (progress + phaseOffset) % 1f
                drawCircle(
                    color = ElectricBlue.copy(alpha = 1f - phase),
                    radius = maxRadius * phase,
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        drawCircle(color = ElectricBlue, radius = maxRadius * 0.15f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

@Composable
private fun DeviceRow(device: DiscoveredDevice, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, WatchShapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "•".repeat(device.signalBars) + "  ${device.address.takeLast(4)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true, name = "Scanning, 1 result")
@Composable
private fun FindWatchScreenResultPreview() {
    WatchTheme {
        PremiumBackground {
            FindWatchScreen(
                isScanning = false,
                scanTimedOut = false,
                devices = listOf(DiscoveredDevice("AA:BB:CC:DD:EE:FF", "GTR 3 Pro", 3)),
                onStartScan = {},
                onDeviceSelected = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Nothing found")
@Composable
private fun FindWatchScreenTimeoutPreview() {
    WatchTheme {
        PremiumBackground {
            FindWatchScreen(isScanning = false, scanTimedOut = true, devices = emptyList(), onStartScan = {}, onDeviceSelected = {})
        }
    }
}
```

- [ ] **Step 2: Implement `PairConfirmScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PairConfirmScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.DiscoveredDevice

/**
 * design-prompt.md Batch 1 #5 — destructive-bind warning. CLAUDE.md §4.4: this is the
 * only screen in the app that leads to bind(); the checkbox gate is deliberate, not decoration.
 */
@Composable
fun PairConfirmScreen(
    device: DiscoveredDevice,
    understood: Boolean,
    onUnderstoodToggled: () -> Unit,
    onCancel: () -> Unit,
    onPair: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Pair ${device.displayName}?", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1, modifier = Modifier.padding(top = 20.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer, WatchShapes.card)
                    .padding(16.dp),
            ) {
                Text(
                    "Pairing as a new user clears the data currently stored on the watch. " +
                        "This can't be undone.",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Checkbox(checked = understood, onCheckedChange = { onUnderstoodToggled() })
                    Text("I understand", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        EntranceItem(index = 2, modifier = Modifier.padding(top = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Pair watch", onClick = onPair, enabled = understood, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairConfirmScreenPreview() {
    WatchTheme {
        PremiumBackground {
            PairConfirmScreen(
                device = DiscoveredDevice("AA:BB:CC:DD:EE:FF", "GTR 3 Pro", 3),
                understood = false,
                onUnderstoodToggled = {},
                onCancel = {},
                onPair = {},
            )
        }
    }
}
```

(`Modifier.weight` inside a `Row` needs `import androidx.compose.foundation.layout.RowScope` implicit receiver — since `SecondaryButton`/`PrimaryButton` calls are directly inside the `Row {}` lambda, `Modifier.weight(1f)` resolves via the `RowScope` receiver already in scope; no extra import needed beyond what's listed.)

- [ ] **Step 3: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/FindWatchScreen.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PairConfirmScreen.kt
git commit -m "feat(onboarding): add Find your watch and Pair confirmation screens"
```

---

### Task 11: Pairing progress and Keep it running screens

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PairingScreen.kt`
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/KeepRunningScreen.kt`

**Interfaces:**
- Consumes: `PairingPhase` (Task 5); `WatchHeroRender` (Task 8); `PrimaryButton`, `SecondaryButton`, `EntranceItem` (designsystem).
- Produces: `@Composable fun PairingScreen(phase: PairingPhase, battery: Int?, firmwareVersion: String?, error: String?, onRetry: () -> Unit, onContinue: () -> Unit)`; `@Composable fun KeepRunningScreen(onBatteryOptimization: () -> Unit, onAutostartHint: () -> Unit, onTestBackgroundConnection: () -> Unit, onDone: () -> Unit)`.

- [ ] **Step 1: Implement `PairingScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PairingScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.PairingPhase

private val PHASE_LABELS = listOf(
    PairingPhase.CONNECTING to "Connecting",
    PairingPhase.AUTHENTICATING to "Authenticating",
    PairingPhase.READING_FEATURES to "Reading watch features",
    PairingPhase.FIRST_SYNC to "First sync",
)

/** design-prompt.md Batch 1 #6 — Pairing in progress. */
@Composable
fun PairingScreen(
    phase: PairingPhase,
    battery: Int?,
    firmwareVersion: String?,
    error: String?,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        EntranceItem(index = 0, modifier = Modifier.padding(vertical = 32.dp)) {
            WatchHeroRender()
        }
        when (phase) {
            PairingPhase.SUCCESS -> EntranceItem(index = 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Connected", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Battery ${battery ?: "--"}% · Firmware ${firmwareVersion ?: "--"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    PrimaryButton(text = "Continue", onClick = onContinue)
                }
            }
            PairingPhase.FAILED -> EntranceItem(index = 1) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't connect", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        error ?: "The watch wasn't reachable.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                    SecondaryButton(text = "Retry", onClick = onRetry)
                }
            }
            else -> EntranceItem(index = 1) {
                Column {
                    PHASE_LABELS.forEach { (step, label) ->
                        val done = step.ordinal < phase.ordinal
                        val current = step == phase
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp),
                        ) {
                            when {
                                done -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                current -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            }
                            Text(
                                label,
                                modifier = Modifier.padding(start = 12.dp),
                                color = if (current) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Authenticating")
@Composable
private fun PairingScreenInProgressPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.AUTHENTICATING, null, null, null, {}, {})
        }
    }
}

@Preview(showBackground = true, name = "Success")
@Composable
private fun PairingScreenSuccessPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.SUCCESS, 82, "1.4.2", null, {}, {})
        }
    }
}

@Preview(showBackground = true, name = "Failed")
@Composable
private fun PairingScreenFailedPreview() {
    WatchTheme {
        PremiumBackground {
            PairingScreen(PairingPhase.FAILED, null, null, "Watch not ready", {}, {})
        }
    }
}
```

- [ ] **Step 2: Implement `KeepRunningScreen`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/KeepRunningScreen.kt`:

```kotlin
package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme

/** design-prompt.md Batch 1 #7 — Keep it running. */
@Composable
fun KeepRunningScreen(
    onBatteryOptimization: () -> Unit,
    onAutostartHint: () -> Unit,
    onTestBackgroundConnection: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Keep it running", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "Android may stop the app in the background. These steps keep your watch connected.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
        EntranceItem(index = 2) {
            KeepRunningCard("Allow unrestricted battery use", "Prevents Android from pausing the connection.", "Allow", onBatteryOptimization)
        }
        EntranceItem(index = 3) {
            KeepRunningCard("Enable autostart", "Some phones need this turned on manually after a restart.", "Show me", onAutostartHint)
        }
        EntranceItem(index = 4) {
            KeepRunningCard("Test background connection", "Confirms the watch stays connected with the app closed.", "Test", onTestBackgroundConnection)
        }
        EntranceItem(index = 5, modifier = Modifier.padding(top = 24.dp)) {
            PrimaryButton(text = "Done", onClick = onDone)
        }
    }
}

@Composable
private fun KeepRunningCard(title: String, description: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surface, WatchShapes.card)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        SecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 12.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun KeepRunningScreenPreview() {
    WatchTheme {
        PremiumBackground {
            KeepRunningScreen(onBatteryOptimization = {}, onAutostartHint = {}, onTestBackgroundConnection = {}, onDone = {})
        }
    }
}
```

- [ ] **Step 3: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/PairingScreen.kt \
        feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/ui/KeepRunningScreen.kt
git commit -m "feat(onboarding): add Pairing progress and Keep it running screens"
```

---

### Task 12: `OnboardingNavHost`

**Files:**
- Create: `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingNavHost.kt`

**Interfaces:**
- Consumes: `OnboardingViewModel` (Task 7), all seven screens (Tasks 8–11), `androidx.hilt.navigation.compose.hiltViewModel`.
- Produces: `@Composable fun OnboardingNavHost(onOnboardingComplete: () -> Unit)`.

This task has no ViewModel logic of its own to unit-test; it is wiring, verified by building and by exercising it manually once Task 13 wires it into `MainActivity`.

- [ ] **Step 1: Implement `OnboardingNavHost`**

Create `feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingNavHost.kt`:

```kotlin
package com.nexwatch.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.feature.onboarding.ui.FindWatchScreen
import com.nexwatch.feature.onboarding.ui.KeepRunningScreen
import com.nexwatch.feature.onboarding.ui.PairConfirmScreen
import com.nexwatch.feature.onboarding.ui.PairingScreen
import com.nexwatch.feature.onboarding.ui.PermissionsScreen
import com.nexwatch.feature.onboarding.ui.ProfileScreen
import com.nexwatch.feature.onboarding.ui.WelcomeScreen

/**
 * The seven B1 screens are one linear state machine (OnboardingViewModel), not a
 * navigation-compose graph — there's no back-stack subtlety here (Cancel/Back events go
 * through onEvent, same as forward progress), so a `when` over the current step is simpler
 * than a NavHost with seven single-purpose routes.
 */
@Composable
fun OnboardingNavHost(onOnboardingComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val step = state.step) {
        OnboardingStep.Welcome -> WelcomeScreen(
            onGetStarted = { viewModel.onEvent(OnboardingEvent.GetStarted) },
        )

        OnboardingStep.Profile -> ProfileScreen(
            profile = state.profile,
            onSexChanged = { viewModel.onEvent(OnboardingEvent.SexChanged(it)) },
            onAgeChanged = { viewModel.onEvent(OnboardingEvent.AgeChanged(it)) },
            onHeightChanged = { viewModel.onEvent(OnboardingEvent.HeightChanged(it)) },
            onWeightChanged = { viewModel.onEvent(OnboardingEvent.WeightChanged(it)) },
            onContinue = { viewModel.onEvent(OnboardingEvent.ProfileContinue) },
        )

        OnboardingStep.Permissions -> PermissionsScreen(
            permissions = state.permissions,
            onPermissionResult = { item, granted -> viewModel.onEvent(OnboardingEvent.PermissionResult(item, granted)) },
            onPermissionSkipped = { viewModel.onEvent(OnboardingEvent.PermissionSkipped(it)) },
            onContinue = { viewModel.onEvent(OnboardingEvent.PermissionsContinue) },
        )

        OnboardingStep.FindWatch -> FindWatchScreen(
            isScanning = state.isScanning,
            scanTimedOut = state.scanTimedOut,
            devices = state.discoveredDevices,
            onStartScan = { viewModel.onEvent(OnboardingEvent.StartScan) },
            onDeviceSelected = { viewModel.onEvent(OnboardingEvent.DeviceSelected(it)) },
        )

        OnboardingStep.PairConfirm -> state.selectedDevice?.let { device ->
            PairConfirmScreen(
                device = device,
                understood = state.bindUnderstoodChecked,
                onUnderstoodToggled = { viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled) },
                onCancel = { viewModel.onEvent(OnboardingEvent.BackToFindWatch) },
                onPair = { viewModel.onEvent(OnboardingEvent.ConfirmPair) },
            )
        }

        is OnboardingStep.Pairing -> PairingScreen(
            phase = step.phase,
            battery = state.pairedBattery,
            firmwareVersion = state.pairedFirmwareVersion,
            error = state.pairingError,
            onRetry = { viewModel.onEvent(OnboardingEvent.RetryPairing) },
            onContinue = { viewModel.onEvent(OnboardingEvent.PairingContinue) },
        )

        OnboardingStep.KeepRunning -> KeepRunningScreen(
            onBatteryOptimization = {},
            onAutostartHint = {},
            onTestBackgroundConnection = {},
            onDone = {
                viewModel.onEvent(OnboardingEvent.FinishOnboarding)
                onOnboardingComplete()
            },
        )
    }
}
```

- [ ] **Step 2: Verify the module builds**

Run: `./gradlew :feature:onboarding:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/nexwatch/feature/onboarding/OnboardingNavHost.kt
git commit -m "feat(onboarding): add OnboardingNavHost sequencing the seven B1 screens"
```

---

### Task 13: Gate the app behind `WatchIdentityStore.isBound`

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/com/nexwatch/ui/AppRoot.kt`
- Modify: `app/src/main/kotlin/com/nexwatch/MainActivity.kt`

**Interfaces:**
- Consumes: `WatchIdentityStore.identity: Flow<WatchIdentity>` (`:core:data`, existing); `OnboardingNavHost` (Task 12); `NexWatchNavHost` (existing).
- Produces: `@Composable fun AppRoot()`.

- [ ] **Step 1: Add the `:feature:onboarding` dependency to `:app`**

In `app/build.gradle.kts`, add alongside the existing `implementation(project(":core:watch-fake"))` line:

```kotlin
implementation(project(":feature:onboarding"))
```

- [ ] **Step 2: Implement `AppRoot`**

Create `app/src/main/kotlin/com/nexwatch/ui/AppRoot.kt`:

```kotlin
package com.nexwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.feature.onboarding.OnboardingNavHost
import com.nexwatch.navigation.NexWatchNavHost
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.nexwatch.core.data.identity.WatchIdentityStore
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

// Top-level, not private: Hilt's generated factory (a different file in this module) must
// be able to construct this class, and Kotlin's top-level `private` is file-scoped.
@HiltViewModel
internal class AppRootViewModel @Inject constructor(
    watchIdentityStore: WatchIdentityStore,
) : ViewModel() {
    val isBound = watchIdentityStore.identity
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}

/**
 * Onboarding shows only when WatchIdentityStore.isBound is false (docs spec §5) — but only
 * as the *entry* decision. `runPairing` (Task 7) calls `markBound()` partway through the
 * Pairing step, before the user sees "Connected" or "Keep it running"; if this screen
 * re-derived its route on every `isBound` emission, that write would yank the user out of
 * onboarding mid-flow. So the route is decided once, from the first real DataStore read,
 * and afterwards changes only via `onOnboardingComplete`.
 */
@Composable
fun AppRoot() {
    val viewModel: AppRootViewModel = hiltViewModel()
    val identity by viewModel.isBound.collectAsStateWithLifecycle()
    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(identity) {
        val resolved = identity
        if (showOnboarding == null && resolved != null) {
            showOnboarding = !resolved.isBound
        }
    }

    when (showOnboarding) {
        null -> Unit // first DataStore read still pending; premium background alone is the frame
        true -> OnboardingNavHost(onOnboardingComplete = { showOnboarding = false })
        false -> NexWatchNavHost()
    }
}
```

- [ ] **Step 3: Wire `AppRoot` into `MainActivity`**

In `app/src/main/kotlin/com/nexwatch/MainActivity.kt`, replace the `NexWatchNavHost()` call:

```kotlin
import com.nexwatch.ui.AppRoot
```

(add to imports, remove the now-unused `import com.nexwatch.navigation.NexWatchNavHost`)

```kotlin
        setContent {
            WatchTheme {
                PremiumBackground {
                    AppRoot()
                }
            }
        }
```

- [ ] **Step 4: Verify the app builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manually verify the gate**

Install the debug build (`./gradlew installDebug`) on an emulator/device with a clean app data state. Confirm the app opens on the Welcome screen (not the 4-tab shell). Complete the flow through to "Done" on Keep it running, then relaunch the app (force-stop, reopen) and confirm it now opens on the 4-tab `NexWatchNavHost` — this proves `WatchIdentityStore.isBound` persisted across process death, matching §4.4.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/nexwatch/ui/AppRoot.kt app/src/main/kotlin/com/nexwatch/MainActivity.kt
git commit -m "feat(app): gate onboarding behind WatchIdentityStore.isBound"
```

---

### Task 14: Final verification and phase sign-off

**Files:**
- Modify: `docs/implementation-plan.md`

**Interfaces:** None — this task only runs checks and updates status.

- [ ] **Step 1: Run the full build and test suite**

Run: `./gradlew assembleDebug assembleRelease test lint`
Expected: BUILD SUCCESSFUL, all tests green, no new lint errors. Fix anything that fails before continuing.

- [ ] **Step 2: Confirm module boundaries are still enforced**

Run: `./gradlew :feature:onboarding:dependencies --configuration debugRuntimeClasspath | grep -E "watch-fitcloud|core:database"`
Expected: no output — `:feature:onboarding` must not resolve either module (CLAUDE.md module table).

- [ ] **Step 3: Confirm every screen and state has a `@Preview`**

Run: `grep -rn "@Preview" feature/onboarding/src/main/kotlin | wc -l`
Expected: at least one `@Preview` per screen file (7 screens), with multiple in `PermissionsScreen.kt`, `FindWatchScreen.kt`, and `PairingScreen.kt` covering their documented states (partial/full grant; scanning/found/timeout; in-progress/success/failed) per the spec's exit criteria.

- [ ] **Step 4: Visually compare against the polished reference**

Open `docs/design/NexWatch B1 Onboarding and Pairing (polished).html` in a browser alongside the running app (or its Compose previews) and check each of the seven screens for layout, copy, and the Part C motion behaviors (entrance stagger, press scale, segmented-control thumb slide, radar pulse). This is a manual/subjective check — note any mismatches as follow-up fixes rather than blocking the whole phase on pixel parity.

- [ ] **Step 5: Update `docs/implementation-plan.md` §12**

Check off Phase 2's three exit criteria boxes. Leave the status as `In progress` if Step 4 surfaced visual mismatches worth fixing first; set it to `Done` only once all three boxes are genuinely true — per CLAUDE.md, merging to `main` is the user's call, not something to do automatically here.

- [ ] **Step 6: Commit**

```bash
git add docs/implementation-plan.md
git commit -m "docs: check off Phase 2 exit criteria"
```
