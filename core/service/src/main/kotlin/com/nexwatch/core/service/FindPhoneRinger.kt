package com.nexwatch.core.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/**
 * §8.6: find-phone plays a ringtone. No wakelock (§9.2) — MediaPlayer on the default
 * ringtone URI plays fine with the screen off, and this stops itself once playback ends.
 */
object FindPhoneRinger {
    fun ring(context: Context) {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getValidRingtoneUri(context)
            ?: return
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(context, uri)
            setOnCompletionListener { it.release() }
            isLooping = false
            prepare()
            start()
        }
        // Stop automatically after 15s in case the ringtone is unexpectedly long/looping.
        android.os.Handler(context.mainLooper).postDelayed({
            if (player.isPlaying) player.stop()
            player.release()
        }, 15_000)
    }
}
