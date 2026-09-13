package com.nexwatch.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {

    @Test
    fun `ease-out strong matches the design-prompt cubic-bezier`() {
        // cubic-bezier(.23, 1, .32, 1) at input fraction 0.5 evaluates to ~0.966
        // (both control points sit at y=1, so this curve front-loads motion
        // hard without ever overshooting past 1) — nowhere near the ~0.5 a
        // linear or default Compose easing would give at the curve's x=0.5,
        // which is the regression this test catches: we didn't accidentally
        // wire up FastOutSlowIn or leave the curve unused.
        val eased = WatchMotion.easeOutStrong.transform(0.5f)
        assertEquals(0.966f, eased, 0.01f)
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
