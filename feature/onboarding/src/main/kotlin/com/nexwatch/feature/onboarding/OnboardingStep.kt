package com.nexwatch.feature.onboarding

/** The seven B1 screens (docs/design-prompt.md Batch 1), in flow order. */
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object Profile : OnboardingStep
    data object Permissions : OnboardingStep
    data object FindWatch : OnboardingStep
    data object PairConfirm : OnboardingStep
    data class Pairing(val phase: PairingPhase) : OnboardingStep
    data object KeepRunning : OnboardingStep
}

/**
 * bind() (CLAUDE.md §4.4) is one suspend call; these sub-phases are synthesized by the
 * ViewModel around it (README: capabilities becoming non-null = "reading watch features",
 * syncHealthData() progress = "first sync") so the Pairing screen can show the B1 step list.
 */
enum class PairingPhase { CONNECTING, AUTHENTICATING, READING_FEATURES, FIRST_SYNC, SUCCESS, FAILED }
