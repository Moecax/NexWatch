package com.nexwatch.core.data.identity

import com.nexwatch.core.watchapi.UserProfile

/**
 * The only identity the watch ever sees is userId (§4.4). boundAddress/isBound persist
 * across process death, reboot and app update so every later connection uses LOGIN.
 *
 * [profile] rides along because §4.4 re-sends it on every login to keep the watch's copy
 * of the on-device calculations current — a LOGIN after a reboot has no UI to ask for it.
 */
data class WatchIdentity(
    val userId: String,
    val boundAddress: String?,
    val isBound: Boolean,
    val profile: UserProfile?,
)
