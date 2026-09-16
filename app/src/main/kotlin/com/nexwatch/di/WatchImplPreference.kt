package com.nexwatch.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which `WatchClient` a debug build wires up (Phase 4). Release builds ignore this
 * entirely — see the release variant's `WatchModule`.
 *
 * Plain SharedPreferences rather than the DataStore the rest of the app uses, because the
 * value is read once while Hilt is building the singleton graph, and that read can't
 * suspend. The flip only takes effect on the next process start for the same reason: the
 * client is a `@Singleton` and swapping it under a running app would leave half the
 * screens holding the old one.
 */
@Singleton
class WatchImplPreference @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs get() = context.getSharedPreferences("debug_watch_impl", Context.MODE_PRIVATE)

    /** Defaults to the fake, so a debug build never touches the radio unless asked. */
    var useRealWatch: Boolean
        get() = prefs.getBoolean(KEY_USE_REAL_WATCH, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_REAL_WATCH, value).apply()

    private companion object {
        const val KEY_USE_REAL_WATCH = "use_real_watch"
    }
}
