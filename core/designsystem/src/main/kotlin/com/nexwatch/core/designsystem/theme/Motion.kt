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
