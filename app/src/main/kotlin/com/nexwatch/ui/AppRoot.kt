package com.nexwatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.feature.onboarding.OnboardingNavHost
import com.nexwatch.navigation.NexWatchNavHost
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.nexwatch.core.data.identity.WatchIdentityStore
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

// Top-level, not private: Hilt's generated factory (a different file in this module) must
// be able to construct this class, and Kotlin's top-level `private` is file-scoped.
@HiltViewModel
internal class AppRootViewModel @Inject constructor(
    watchIdentityStore: WatchIdentityStore,
) : ViewModel() {
    val isBound = watchIdentityStore.identity
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}

/**
 * Onboarding shows only when WatchIdentityStore.isBound is false (docs spec §5) — but only
 * as the *entry* decision. `runPairing` (Task 7) calls `markBound()` partway through the
 * Pairing step, before the user sees "Connected" or "Keep it running"; if this screen
 * re-derived its route on every `isBound` emission, that write would yank the user out of
 * onboarding mid-flow. So the route is decided once, from the first real DataStore read,
 * and afterwards changes only via `onOnboardingComplete`.
 */
@Composable
fun AppRoot() {
    val viewModel: AppRootViewModel = hiltViewModel()
    val identity by viewModel.isBound.collectAsStateWithLifecycle()
    var showOnboarding by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(identity) {
        val resolved = identity
        if (showOnboarding == null && resolved != null) {
            showOnboarding = !resolved.isBound
        }
    }

    when (showOnboarding) {
        null -> Unit // first DataStore read still pending; premium background alone is the frame
        true -> OnboardingNavHost(onOnboardingComplete = { showOnboarding = false })
        false -> NexWatchNavHost()
    }
}
