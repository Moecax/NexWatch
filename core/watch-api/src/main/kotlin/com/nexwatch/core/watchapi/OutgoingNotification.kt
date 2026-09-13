package com.nexwatch.core.watchapi

/**
 * Built by the §8.5 filter pipeline before it reaches WatchClient.sendNotification().
 */
data class OutgoingNotification(
    val sourcePackage: String,
    val type: NotificationType,
    val title: String,
    val content: String,
) {
    enum class NotificationType { SMS, WHATSAPP, TELEGRAM, CALL, OTHERS_APP }
}

sealed interface SendResult {
    data object Sent : SendResult
    data class Dropped(val reason: String) : SendResult
    data class Failed(val reason: String) : SendResult
}
