package com.nexwatch.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/**
 * One object per bottom-nav tab (design-prompt.md Part A: "bottom navigation
 * with 4 destinations: Today, Health, Watch, Data"). Each is a type-safe
 * Navigation Compose route — no string route matching anywhere in :app.
 *
 * A sealed interface (rather than a sealed class with a `label`/`icon`
 * constructor) is used because kotlinx.serialization requires every
 * @Serializable subtype's supertype to itself be serializable with a
 * zero-arg constructor; label/icon are carried as extension properties
 * below instead of stored state on each route object.
 */
sealed interface NexWatchDestination {
    @Serializable
    data object Today : NexWatchDestination

    @Serializable
    data object Health : NexWatchDestination

    @Serializable
    data object Watch : NexWatchDestination

    @Serializable
    data object Data : NexWatchDestination

    companion object {
        val bottomNavItems = listOf(Today, Health, Watch, Data)
    }
}

/** Reached from the Watch tab, so it is a route but not a tab. */
@Serializable
data object WatchDiagnostics

val NexWatchDestination.label: String
    get() = when (this) {
        NexWatchDestination.Today -> "Today"
        NexWatchDestination.Health -> "Health"
        NexWatchDestination.Watch -> "Watch"
        NexWatchDestination.Data -> "Data"
    }

/** Part A: outlined icons, with the filled variant only for the selected tab. */
fun NexWatchDestination.icon(selected: Boolean): ImageVector = when (this) {
    NexWatchDestination.Today -> if (selected) Icons.Filled.Today else Icons.Outlined.Today
    NexWatchDestination.Health -> if (selected) Icons.Filled.MonitorHeart else Icons.Outlined.MonitorHeart
    NexWatchDestination.Watch -> if (selected) Icons.Filled.Watch else Icons.Outlined.Watch
    NexWatchDestination.Data -> if (selected) Icons.Filled.Storage else Icons.Outlined.Storage
}
