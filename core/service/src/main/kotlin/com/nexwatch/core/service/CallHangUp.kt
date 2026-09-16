package com.nexwatch.core.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * §8.6: the watch can ask to hang up the active call. `TelecomManager.endCall()` needs
 * ANSWER_PHONE_CALLS (API 28+) — silently does nothing without it rather than crashing,
 * since a missing permission here is a settings problem, not a bug to surface as a crash.
 */
object CallHangUp {
    fun tryEndCall(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val telecomManager = context.getSystemService<TelecomManager>() ?: return
        telecomManager.endCall()
    }
}
