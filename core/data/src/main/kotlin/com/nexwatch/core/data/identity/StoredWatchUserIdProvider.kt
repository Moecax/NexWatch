package com.nexwatch.core.data.identity

import com.nexwatch.core.watchapi.WatchUserIdProvider
import javax.inject.Inject

/**
 * Adapts [WatchIdentityStore] to the :core:watch-api seam so :core:watch-fitcloud can read
 * the userId without depending on :core:data (see [WatchUserIdProvider]).
 */
class StoredWatchUserIdProvider @Inject constructor(
    private val store: WatchIdentityStore,
) : WatchUserIdProvider {
    override suspend fun userId(): String = store.ensureUserId()
}
