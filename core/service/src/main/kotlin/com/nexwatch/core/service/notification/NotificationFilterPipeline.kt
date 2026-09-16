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
        return OutgoingNotification(
            sourcePackage = incoming.packageName,
            type = type,
            title = title,
            content = text,
        )
    }
}
