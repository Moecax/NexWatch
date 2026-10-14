package com.nexwatch.core.data.notification

import com.nexwatch.core.watchapi.OutgoingNotification
import com.nexwatch.core.watchapi.SendResult
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import javax.inject.Inject

sealed interface WatchReachResult {
    data object Passed : WatchReachResult

    /** [reason] is a sentence for the user, ready to show as is. */
    data class Failed(val reason: String) : WatchReachResult
}

/**
 * Onboarding's and Diagnostics' "Test" button: a real notification through the same path
 * forwarding uses, so a pass means the service's connection can reach the wrist. It can't
 * prove the app survives being closed; battery and autostart settings are what make that likely.
 */
class WatchReachTest @Inject constructor(private val watchClient: WatchClient) {

    suspend fun run(): WatchReachResult {
        if (watchClient.state.value !is WatchState.Ready) return WatchReachResult.Failed(NOT_CONNECTED)
        return when (val sent = watchClient.sendNotification(TEST_NOTIFICATION)) {
            SendResult.Sent -> WatchReachResult.Passed
            is SendResult.Dropped -> WatchReachResult.Failed(
                when (sent.reason) {
                    SendResult.DropReason.WATCH_NOT_READY -> NOT_CONNECTED
                    SendResult.DropReason.WATCH_BUSY -> "The watch is busy syncing. Try again in a moment."
                },
            )
            is SendResult.Failed -> WatchReachResult.Failed("The watch didn't take it: ${sent.reason}")
        }
    }

    private companion object {
        const val NOT_CONNECTED = "The watch isn't connected. Keep it close and try again."

        val TEST_NOTIFICATION = OutgoingNotification(
            sourcePackage = "com.nexwatch",
            type = OutgoingNotification.NotificationType.OTHERS_APP,
            title = "NexWatch",
            content = "Connected. Notifications will reach this watch.",
        )
    }
}
