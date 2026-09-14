package com.nexwatch.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.feature.onboarding.ui.FindWatchScreen
import com.nexwatch.feature.onboarding.ui.KeepRunningScreen
import com.nexwatch.feature.onboarding.ui.PairConfirmScreen
import com.nexwatch.feature.onboarding.ui.PairingScreen
import com.nexwatch.feature.onboarding.ui.PermissionsScreen
import com.nexwatch.feature.onboarding.ui.ProfileScreen
import com.nexwatch.feature.onboarding.ui.WelcomeScreen

/**
 * The seven B1 screens are one linear state machine (OnboardingViewModel), not a
 * navigation-compose graph — there's no back-stack subtlety here (Cancel/Back events go
 * through onEvent, same as forward progress), so a `when` over the current step is simpler
 * than a NavHost with seven single-purpose routes.
 */
@Composable
fun OnboardingNavHost(onOnboardingComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val step = state.step) {
        OnboardingStep.Welcome -> WelcomeScreen(
            onGetStarted = { viewModel.onEvent(OnboardingEvent.GetStarted) },
        )

        OnboardingStep.Profile -> ProfileScreen(
            profile = state.profile,
            onSexChanged = { viewModel.onEvent(OnboardingEvent.SexChanged(it)) },
            onAgeChanged = { viewModel.onEvent(OnboardingEvent.AgeChanged(it)) },
            onHeightChanged = { viewModel.onEvent(OnboardingEvent.HeightChanged(it)) },
            onWeightChanged = { viewModel.onEvent(OnboardingEvent.WeightChanged(it)) },
            onContinue = { viewModel.onEvent(OnboardingEvent.ProfileContinue) },
        )

        OnboardingStep.Permissions -> PermissionsScreen(
            permissions = state.permissions,
            onPermissionResult = { item, granted -> viewModel.onEvent(OnboardingEvent.PermissionResult(item, granted)) },
            onPermissionSkipped = { viewModel.onEvent(OnboardingEvent.PermissionSkipped(it)) },
            onContinue = { viewModel.onEvent(OnboardingEvent.PermissionsContinue) },
        )

        OnboardingStep.FindWatch -> FindWatchScreen(
            isScanning = state.isScanning,
            scanTimedOut = state.scanTimedOut,
            devices = state.discoveredDevices,
            onStartScan = { viewModel.onEvent(OnboardingEvent.StartScan) },
            onDeviceSelected = { viewModel.onEvent(OnboardingEvent.DeviceSelected(it)) },
        )

        OnboardingStep.PairConfirm -> state.selectedDevice?.let { device ->
            PairConfirmScreen(
                device = device,
                understood = state.bindUnderstoodChecked,
                onUnderstoodToggled = { viewModel.onEvent(OnboardingEvent.BindUnderstoodToggled) },
                onCancel = { viewModel.onEvent(OnboardingEvent.BackToFindWatch) },
                onPair = { viewModel.onEvent(OnboardingEvent.ConfirmPair) },
            )
        }

        is OnboardingStep.Pairing -> PairingScreen(
            phase = step.phase,
            battery = state.pairedBattery,
            firmwareVersion = state.pairedFirmwareVersion,
            error = state.pairingError,
            onRetry = { viewModel.onEvent(OnboardingEvent.RetryPairing) },
            onContinue = { viewModel.onEvent(OnboardingEvent.PairingContinue) },
        )

        OnboardingStep.KeepRunning -> KeepRunningScreen(
            onBatteryOptimization = {},
            onAutostartHint = {},
            onTestBackgroundConnection = {},
            onDone = {
                viewModel.onEvent(OnboardingEvent.FinishOnboarding)
                onOnboardingComplete()
            },
        )
    }
}
