package com.nexwatch.core.watchapi.notification

import com.nexwatch.core.watchapi.OutgoingNotification

data class NotificationForwardingSettings(
    val enabled: Boolean,
    val allowedPackages: Set<String>,
)

sealed interface PipelineDecision {
    data class Forward(val notification: OutgoingNotification) : PipelineDecision
    data class Skip(val reason: SkipReason) : PipelineDecision
}

enum class SkipReason {
    DISABLED,
    OWN_APP,
    NOT_ALLOWED,
    /** Ongoing, a group summary, or a progress/transport/service/status category. */
    NOT_A_MESSAGE,
    DUPLICATE,
    THROTTLED,
}

private const val DEDUPE_WINDOW_MS = 60_000L
private const val DEDUPE_CAPACITY = 50
private const val THROTTLE_WINDOW_MS = 5_000L
private const val MAX_CONTENT_LENGTH = 200
private val DROPPED_CATEGORIES = setOf("progress", "transport", "service", "status")

private val PACKAGE_TO_TYPE = mapOf(
    "com.whatsapp" to OutgoingNotification.NotificationType.WHATSAPP,
    "org.telegram.messenger" to OutgoingNotification.NotificationType.TELEGRAM,
    "com.google.android.apps.messaging" to OutgoingNotification.NotificationType.SMS,
    "com.android.messaging" to OutgoingNotification.NotificationType.SMS,
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
    ): PipelineDecision {
        if (!settings.enabled) return PipelineDecision.Skip(SkipReason.DISABLED)
        if (incoming.packageName == ownPackageName) return PipelineDecision.Skip(SkipReason.OWN_APP)
        if (incoming.packageName !in settings.allowedPackages) return PipelineDecision.Skip(SkipReason.NOT_ALLOWED)
        if (incoming.isOngoing || incoming.isGroupSummary) return PipelineDecision.Skip(SkipReason.NOT_A_MESSAGE)
        if (incoming.category in DROPPED_CATEGORIES) return PipelineDecision.Skip(SkipReason.NOT_A_MESSAGE)

        val title = incoming.title ?: incoming.packageName
        val text = incoming.text.orEmpty()

        val contentHash = (incoming.packageName.hashCode() * 31 + title.hashCode()) * 31 + text.hashCode()
        val lastSeenAt = recentHashes[contentHash]
        if (lastSeenAt != null && nowMs - lastSeenAt < DEDUPE_WINDOW_MS) return PipelineDecision.Skip(SkipReason.DUPLICATE)
        recentHashes[contentHash] = nowMs

        val lastSentAt = lastSentPerPackage[incoming.packageName]
        if (lastSentAt != null && nowMs - lastSentAt < THROTTLE_WINDOW_MS) return PipelineDecision.Skip(SkipReason.THROTTLED)
        lastSentPerPackage[incoming.packageName] = nowMs

        val type = PACKAGE_TO_TYPE[incoming.packageName] ?: OutgoingNotification.NotificationType.OTHERS_APP
        return PipelineDecision.Forward(
            OutgoingNotification(
                sourcePackage = incoming.packageName,
                type = type,
                title = title,
                content = text.take(MAX_CONTENT_LENGTH),
            ),
        )
    }
}
