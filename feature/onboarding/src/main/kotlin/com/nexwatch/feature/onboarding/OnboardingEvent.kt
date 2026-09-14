package com.nexwatch.feature.onboarding

import com.nexwatch.core.watchapi.UserProfile

sealed interface OnboardingEvent {
    data object GetStarted : OnboardingEvent
    data class SexChanged(val sex: UserProfile.Sex) : OnboardingEvent
    data class AgeChanged(val age: Int) : OnboardingEvent
    data class HeightChanged(val heightCm: Int) : OnboardingEvent
    data class WeightChanged(val weightKg: Int) : OnboardingEvent
    data object ProfileContinue : OnboardingEvent
    data class PermissionResult(val item: PermissionItem, val granted: Boolean) : OnboardingEvent
    data class PermissionSkipped(val item: PermissionItem) : OnboardingEvent
    data object PermissionsContinue : OnboardingEvent
    data object StartScan : OnboardingEvent
    data class DeviceSelected(val device: DiscoveredDevice) : OnboardingEvent
    data object BackToFindWatch : OnboardingEvent
    data object BindUnderstoodToggled : OnboardingEvent
    data object ConfirmPair : OnboardingEvent
    data object PairingContinue : OnboardingEvent
    data object RetryPairing : OnboardingEvent
    data object FinishOnboarding : OnboardingEvent
}
