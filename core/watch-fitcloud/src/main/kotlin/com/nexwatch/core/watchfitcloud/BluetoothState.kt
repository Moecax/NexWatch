package com.nexwatch.core.watchfitcloud

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [com.nexwatch.core.watchapi.WatchState.BluetoothOff] needs the adapter's state and the
 * SDK doesn't surface it. Driven by the system broadcast rather than a poll (I5); the
 * receiver is unregistered as soon as the last collector goes away.
 */
internal fun bluetoothEnabledFlow(context: Context): Flow<Boolean> = callbackFlow {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun emitCurrent() {
        trySend(adapter?.isEnabled == true)
    }

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = emitCurrent()
    }
    ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    emitCurrent()
    awaitClose { context.unregisterReceiver(receiver) }
}.distinctUntilChanged()
