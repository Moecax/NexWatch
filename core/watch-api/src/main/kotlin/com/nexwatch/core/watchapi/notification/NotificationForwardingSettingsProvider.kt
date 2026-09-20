package com.nexwatch.core.watchapi.notification

import kotlinx.coroutines.flow.Flow

/**
 * The §8.5 forwarding settings are persisted by NotificationForwardingPrefs in :core:data,
 * but the consumer that needs them (NotificationForwarder) lives in :core:watch-fitcloud, and
 * the module rules forbid that edge. Both sides may depend on :core:watch-api, so the
 * dependency is inverted through here, mirroring WatchUserIdProvider (§4.4).
 */
interface NotificationForwardingSettingsProvider {
    val settings: Flow<NotificationForwardingSettings>
}
