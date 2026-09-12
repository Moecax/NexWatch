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

val NexWatchDestination.label: String
    get() = when (this) {
        NexWatchDestination.Today -> "Today"
        NexWatchDestination.Health -> "Health"
        NexWatchDestination.Watch -> "Watch"
        NexWatchDestination.Data -> "Data"
    }

val NexWatchDestination.icon: ImageVector
    get() = when (this) {
        NexWatchDestination.Today -> Icons.Filled.Dashboard
        NexWatchDestination.Health -> Icons.Filled.Favorite
        NexWatchDestination.Watch -> Icons.Filled.Watch
        NexWatchDestination.Data -> Icons.Filled.Storage
    }
