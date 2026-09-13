package com.nexwatch.core.data.identity

/**
 * The only identity the watch ever sees is userId (§4.4). boundAddress/isBound persist
 * across process death, reboot and app update so every later connection uses LOGIN.
 */
data class WatchIdentity(
    val userId: String,
    val boundAddress: String?,
    val isBound: Boolean,
)
