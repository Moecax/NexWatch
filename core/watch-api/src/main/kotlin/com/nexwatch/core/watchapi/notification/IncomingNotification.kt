package com.nexwatch.core.watchapi.notification

/**
 * The §8.5 pipeline operates on this instead of `StatusBarNotification` directly, so the
 * decision logic is a pure function testable without Robolectric. `NotificationForwarder`
 * (a later task) is the only place that builds one from a real `StatusBarNotification`.
 */
data class IncomingNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val category: String?,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
)
