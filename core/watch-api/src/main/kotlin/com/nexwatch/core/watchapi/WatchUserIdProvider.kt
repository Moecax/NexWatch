package com.nexwatch.core.watchapi

/**
 * The userId from §4.4 is persisted by WatchIdentityStore in :core:data, but the client
 * that needs it lives in :core:watch-fitcloud, and the module rules forbid that edge.
 * Both sides may depend on :core:watch-api, so the dependency is inverted through here:
 * :core:data implements it, :app binds it, the client only sees this interface.
 */
fun interface WatchUserIdProvider {
    /** Generates and persists the id on first call; returns the same value forever after. */
    suspend fun userId(): String
}
