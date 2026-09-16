package com.nexwatch.core.watchapi

/**
 * One watch seen during [WatchClient.discoverWatches]. The address is what bind() and
 * login() take, and it is the only part the app persists.
 */
data class DiscoveredWatch(
    val address: String,
    val name: String,
    val rssi: Int,
)
