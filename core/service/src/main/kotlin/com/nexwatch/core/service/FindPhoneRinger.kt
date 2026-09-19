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
        // Released is tracked explicitly: the completion listener and the 15s watchdog
        // below both race to release() the same player, and MediaPlayer throws on any
        // method call (including isPlaying) after release() — most ringtones are shorter
        // than 15s, so the completion listener wins that race on the common path.
        var released = false
        val player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                setOnCompletionListener {
                    released = true
                    it.release()
                }
                isLooping = false
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Bad/unreadable ringtone URI (IOException from setDataSource/prepare, or
            // IllegalStateException) must not crash the always-on service's event collector.
            return
        }
        // Stop automatically after 15s in case the ringtone is unexpectedly long/looping.
        android.os.Handler(context.mainLooper).postDelayed({
            if (!released) {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }, 15_000)
    }
}
