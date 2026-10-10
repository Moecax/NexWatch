package com.nexwatch.core.watchfitcloud

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.core.watchapi.SendResult
import com.nexwatch.core.watchapi.notification.ForwardingLogEntry
import com.nexwatch.core.watchapi.notification.ForwardingOutcome
import com.nexwatch.core.watchapi.notification.ForwardingSkip
import com.nexwatch.core.watchapi.notification.IncomingNotification
import com.nexwatch.core.watchapi.notification.NotificationFilterPipeline
import com.nexwatch.core.watchapi.notification.NotificationForwardedRecorder
import com.nexwatch.core.watchapi.notification.NotificationForwardingSettingsProvider
import com.nexwatch.core.watchapi.notification.PipelineDecision
import com.nexwatch.core.watchapi.notification.SkipReason
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
    @Inject lateinit var forwardedRecorder: NotificationForwardedRecorder
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
            val settings = settingsProvider.settings.first()
            val nowMs = System.currentTimeMillis()
            // Notifications arrive on parallel coroutines and the pipeline's dedupe and throttle
            // maps aren't thread-safe.
            val decision = synchronized(pipeline) {
                pipeline.evaluate(incoming, settings, ownPackageName = applicationContext.packageName, nowMs = nowMs)
            }
            val outcome = when (decision) {
                is PipelineDecision.Skip -> when (decision.reason) {
                    SkipReason.DUPLICATE -> ForwardingOutcome.Skipped(ForwardingSkip.DUPLICATE)
                    SkipReason.THROTTLED -> ForwardingOutcome.Skipped(ForwardingSkip.THROTTLED)
                    // Not worth a log line, and the log must never list apps the user didn't allow.
                    SkipReason.DISABLED, SkipReason.OWN_APP, SkipReason.NOT_ALLOWED, SkipReason.NOT_A_MESSAGE -> return@launch
                }
                is PipelineDecision.Forward -> when (val result = watchClient.sendNotification(decision.notification)) {
                    SendResult.Sent -> ForwardingOutcome.Sent
                    is SendResult.Dropped -> ForwardingOutcome.Skipped(
                        when (result.reason) {
                            SendResult.DropReason.WATCH_NOT_READY -> ForwardingSkip.WATCH_DISCONNECTED
                            SendResult.DropReason.WATCH_BUSY -> ForwardingSkip.WATCH_BUSY
                        },
                    )
                    is SendResult.Failed -> ForwardingOutcome.Failed(result.reason)
                }
            }
            val title = (decision as? PipelineDecision.Forward)?.notification?.title ?: incoming.title ?: incoming.packageName
            runCatching { forwardedRecorder.record(ForwardingLogEntry(nowMs, incoming.packageName, title, outcome)) }
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
