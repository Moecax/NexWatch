package com.nexwatch.core.watchfitcloud

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.WatchState
import com.nexwatch.core.watchapi.notification.IncomingNotification
import com.nexwatch.core.watchapi.notification.NotificationFilterPipeline
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettingsProvider
import com.topstep.fitcloud.sdk.v2.FcSDK
import com.topstep.fitcloud.sdk.v2.utils.notification.AbsNotificationListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * §8.5. Extends the SDK's own listener base so music-control wiring (built into
 * `AbsNotificationListenerService`) keeps working; NexWatch's own filter pipeline runs
 * inside `onNotificationPosted` before anything reaches `WatchClient`.
 */
@AndroidEntryPoint
class NotificationForwarder : AbsNotificationListenerService() {

    @Inject lateinit var watchClient: WatchClient
    @Inject lateinit var settingsProvider: NotificationForwardingSettingsProvider
    @Inject lateinit var dispatchers: CoroutineDispatchers

    private val pipeline = NotificationFilterPipeline()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    override fun getFcSDK(context: Context): FcSDK = FitCloudSdk.require()

    // NexWatch does its own package-to-type mapping inside the pipeline (see
    // NotificationFilterPipeline); this override exists only because the SDK base
    // class requires it, and its return value is not used for forwarding decisions.
    override fun getNotificationType(context: Context, sbn: StatusBarNotification): Int? = null

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val incoming = sbn.toIncomingNotification()
        scope.launch {
            if (watchClient.state.value !is WatchState.Ready) return@launch
            val settings = settingsProvider.settings.first()
            val outgoing = pipeline.evaluate(
                incoming = incoming,
                settings = settings,
                ownPackageName = applicationContext.packageName,
                nowMs = System.currentTimeMillis(),
            ) ?: return@launch
            watchClient.sendNotification(outgoing)
        }
    }

    private fun StatusBarNotification.toIncomingNotification(): IncomingNotification {
        val extras = notification.extras
        return IncomingNotification(
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extractText(notification),
            category = notification.category,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    /** §8.5's extraction order: last MessagingStyle message, then EXTRA_BIG_TEXT, then EXTRA_TEXT. */
    private fun extractText(notification: Notification): String? {
        val lastMessage = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
            ?.messages
            ?.lastOrNull()
        return lastMessage?.text?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
