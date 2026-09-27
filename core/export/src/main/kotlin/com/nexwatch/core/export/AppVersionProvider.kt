package com.nexwatch.core.export

/**
 * :core:export can't read :app's BuildConfig.VERSION_NAME directly (:app depends on
 * :core:export, not the other way around) — same inversion as WatchUserIdProvider (§4.4):
 * the interface lives where it's consumed, :app binds the real implementation (Task 8).
 */
fun interface AppVersionProvider {
    fun versionName(): String
}
