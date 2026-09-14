package com.nexwatch.feature.onboarding

import com.nexwatch.core.watchapi.UserProfile

enum class PermissionItem { BLUETOOTH, NOTIFICATIONS, NOTIFICATION_ACCESS, PHONE_AND_CONTACTS, LOCATION }

enum class PermissionStatus { NOT_GRANTED, GRANTED, SKIPPED }

data class DiscoveredDevice(val address: String, val displayName: String, val signalBars: Int)

data class ProfileInput(
    val sex: UserProfile.Sex = UserProfile.Sex.MALE,
    val age: Int = 30,
    val heightCm: Int = 170,
    val weightKg: Int = 70,
) {
    fun toUserProfile() = UserProfile(sex = sex, age = age, heightCm = heightCm, weightKg = weightKg)
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val profile: ProfileInput = ProfileInput(),
    val permissions: Map<PermissionItem, PermissionStatus> =
        PermissionItem.entries.associateWith { PermissionStatus.NOT_GRANTED },
    val isScanning: Boolean = false,
    val scanTimedOut: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDevice: DiscoveredDevice? = null,
    val bindUnderstoodChecked: Boolean = false,
    val pairedBattery: Int? = null,
    val pairedFirmwareVersion: String? = null,
    val pairingError: String? = null,
) {
    val grantedPermissionCount: Int
        get() = permissions.values.count { it == PermissionStatus.GRANTED || it == PermissionStatus.SKIPPED }
}
