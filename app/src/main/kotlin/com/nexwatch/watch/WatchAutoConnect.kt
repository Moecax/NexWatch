package com.nexwatch.watch

import com.nexwatch.core.common.CoroutineDispatchers
import com.nexwatch.core.data.identity.WatchIdentityStore
import com.nexwatch.core.watchapi.WatchClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconnects to an already-bound watch on process start (§4.4, §12 Phase 4's exit criterion).
 *
 * **Always LOGIN, never BIND** — BIND wipes the watch, and the only place allowed to call it
 * is the guarded pairing flow after the user has confirmed. This reads the address and profile
 * that pairing persisted and re-sends them, which is also how the watch's copy of the profile
 * stays current.
 *
 * Interim by design: Phase 5 moves this into `WatchConnectionService`, where a foreground
 * service and `BootReceiver` can keep it alive past the activity. Until then it lives in
 * `Application.onCreate()`, which still covers every process start path.
 */
@Singleton
class WatchAutoConnect @Inject constructor(
    private val watchClient: WatchClient,
    private val identityStore: WatchIdentityStore,
    dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    fun start() {
        scope.launch {
            val identity = identityStore.identity.first()
            if (!identity.isBound) return@launch
            val address = identity.boundAddress ?: return@launch
            val profile = identity.profile ?: return@launch
            // A failed reconnect isn't an error worth crashing on: the connector keeps
            // retrying on its own, and `WatchClient.state` already tells the UI where it got to.
            runCatching { watchClient.login(address, profile) }
        }
    }
}
