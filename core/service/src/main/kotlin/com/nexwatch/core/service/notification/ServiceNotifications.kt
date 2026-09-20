package com.nexwatch.core.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

private const val CHANNEL_ID = "watch_connection"

/** §8.2: IMPORTANCE_LOW, silent, updated only on an actual text change. */
object ServiceNotifications {

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Watch connection",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, statusText: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("NexWatch")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // placeholder; a real app icon asset is a follow-up, not blocking this task
            .setOngoing(true)
            .setSilent(true)
            .build()
}
