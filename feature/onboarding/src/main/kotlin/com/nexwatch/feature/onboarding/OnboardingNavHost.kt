package com.nexwatch.feature.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.service.AssociationResult
import com.nexwatch.core.service.BackgroundRunning
import com.nexwatch.core.service.CompanionAssociatorEntryPoint
import dagger.hilt.android.EntryPointAccessors
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

        is OnboardingStep.Pairing -> {
            if (step.phase == PairingPhase.SUCCESS) {
                state.selectedDevice?.address?.let { CompanionAssociationEffect(it) }
            }
            PairingScreen(
                phase = step.phase,
                battery = state.pairedBattery,
                firmwareVersion = state.pairedFirmwareVersion,
                error = state.pairingError,
                onRetry = { viewModel.onEvent(OnboardingEvent.RetryPairing) },
                onContinue = { viewModel.onEvent(OnboardingEvent.PairingContinue) },
            )
        }

        OnboardingStep.KeepRunning -> {
            val context = LocalContext.current
            // The exemption is granted in a system dialog, so re-read it when the user comes back.
            var batteryUnrestricted by remember { mutableStateOf(BackgroundRunning.isUnrestricted(context)) }
            LifecycleResumeEffect(Unit) {
                batteryUnrestricted = BackgroundRunning.isUnrestricted(context)
                onPauseOrDispose {}
            }
            KeepRunningScreen(
                batteryUnrestricted = batteryUnrestricted,
                backgroundTest = state.backgroundTest,
                onBatteryOptimization = { BackgroundRunning.requestUnrestricted(context) },
                onAutostartHint = { BackgroundRunning.openAutostartSettings(context) },
                onTestBackgroundConnection = { viewModel.onEvent(OnboardingEvent.TestBackgroundConnection) },
                onDone = {
                    viewModel.onEvent(OnboardingEvent.FinishOnboarding)
                    onOnboardingComplete()
                },
            )
        }
    }
}

/**
 * §8.3: asks the system to associate the freshly paired watch, so CompanionPresenceService can
 * wake the app when it comes into range. Pairing has already succeeded either way — LOGIN from
 * launch and boot doesn't need the association — so a refusal or failure here is not an error.
 */
@Composable
private fun CompanionAssociationEffect(address: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val associator = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, CompanionAssociatorEntryPoint::class.java)
            .companionAssociator()
    }
    // The association is created by the system once the user accepts; nothing to do on return.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    LaunchedEffect(address) {
        if (!associator.isAvailable || associator.isAssociated(address)) return@LaunchedEffect
        val result = associator.associate(address)
        if (result is AssociationResult.NeedsConsent) {
            consent.launch(IntentSenderRequest.Builder(result.intentSender).build())
        }
    }
}
