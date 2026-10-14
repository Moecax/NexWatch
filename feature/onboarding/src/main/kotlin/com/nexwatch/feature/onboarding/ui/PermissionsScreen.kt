package com.nexwatch.feature.onboarding.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.RowGroup
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.component.StepProgressBar
import com.nexwatch.core.designsystem.component.pressScale
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.PermissionItem
import com.nexwatch.feature.onboarding.PermissionStatus
import com.nexwatch.feature.onboarding.permissions.PermissionCatalog
import com.nexwatch.feature.onboarding.permissions.PermissionRuntime

private data class PermissionCopy(val title: String, val reason: String, val icon: ImageVector)

private val PERMISSION_COPY = mapOf(
    PermissionItem.BLUETOOTH to PermissionCopy("Nearby devices", "Find and connect to your watch over Bluetooth.", Icons.Filled.Bluetooth),
    PermissionItem.NOTIFICATIONS to PermissionCopy("Notifications", "Show sync and connection status.", Icons.Filled.Notifications),
    PermissionItem.NOTIFICATION_ACCESS to PermissionCopy("Notification access", "Forward phone notifications to your wrist.", Icons.Filled.NotificationsActive),
    PermissionItem.PHONE_AND_CONTACTS to PermissionCopy("Phone & contacts", "Show caller ID and let the watch reject calls.", Icons.Filled.Call),
    PermissionItem.LOCATION to PermissionCopy("Location", "Optional. Pushes local weather to the watch.", Icons.Filled.LocationOn),
)

/** Nearby devices only; see PermissionCatalog.isOptional for why the rest can be skipped. */
private val REQUIRED_PERMISSIONS = PermissionItem.entries.filterNot { PermissionCatalog.isOptional(it) }

/** design-prompt.md Batch 1 #3 — Permissions checklist. */
@Composable
fun PermissionsScreen(
    permissions: Map<PermissionItem, PermissionStatus>,
    onPermissionResult: (PermissionItem, Boolean) -> Unit,
    onPermissionSkipped: (PermissionItem) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val grantedCount = permissions.values.count { it != PermissionStatus.NOT_GRANTED }
    val missingRequired = REQUIRED_PERMISSIONS.filter { permissions[it] != PermissionStatus.GRANTED }
    // Items Android will no longer show a dialog for: their button opens the app's settings page.
    val blocked = remember { mutableStateMapOf<PermissionItem, Boolean>() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPermissions by rememberUpdatedState(permissions)
    val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)
    DisposableEffect(lifecycleOwner) {
        // Anything can be turned on outside this screen (notification access, or a blocked
        // permission from app settings), so every item not yet granted is re-read on return.
        // Only a grant is reported: a re-read must never undo the user's Skip.
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            currentPermissions.filterValues { it != PermissionStatus.GRANTED }.keys.forEach { item ->
                if (PermissionRuntime.isGranted(context, item, Build.VERSION.SDK_INT)) {
                    blocked.remove(item)
                    currentOnPermissionResult(item, true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        EntranceItem(index = 0) {
            StepProgressBar(total = ONBOARDING_STEPS, current = 3, modifier = Modifier.padding(bottom = 16.dp))
        }
        EntranceItem(index = 1) {
            Text("Let's get you connected", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        EntranceItem(index = 2) {
            Text(
                "NexWatch needs a few permissions to talk to your watch.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
        EntranceItem(index = 3) {
            GrantProgress(granted = grantedCount, total = permissions.size)
        }
        if (missingRequired.isNotEmpty()) {
            EntranceItem(index = 4, modifier = Modifier.padding(top = 12.dp)) {
                HelperLine("Nearby devices is needed to pair. You can skip the rest and allow them later.")
            }
        }
        EntranceItem(index = 5, modifier = Modifier.padding(top = 16.dp)) {
            RowGroup(
                rows = PermissionItem.entries.map { item ->
                    {
                        val copy = PERMISSION_COPY.getValue(item)
                        val status = permissions.getValue(item)
                        val required = PermissionCatalog.runtimePermissions(item, Build.VERSION.SDK_INT)
                        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                            val granted = results.values.all { it }
                            // After a denial with no rationale to show, Android has stopped asking:
                            // another tap on Grant would do nothing, so it becomes "Settings".
                            val activity = context.findActivity()
                            blocked[item] = !granted && activity != null &&
                                required.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                            onPermissionResult(item, granted)
                        }
                        PermissionRow(
                            item = item,
                            copy = copy,
                            status = status,
                            blocked = blocked[item] == true,
                            onGrant = {
                                when {
                                    PermissionCatalog.isSpecialAccess(item) ->
                                        context.startActivity(PermissionRuntime.notificationListenerSettingsIntent())
                                    blocked[item] == true -> context.startActivity(PermissionRuntime.appSettingsIntent(context))
                                    required.isEmpty() -> onPermissionResult(item, true)
                                    else -> launcher.launch(required.toTypedArray())
                                }
                            },
                            onSkip = { onPermissionSkipped(item) },
                        )
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        EntranceItem(index = 6) {
            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                enabled = missingRequired.isEmpty(),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    copy: PermissionCopy,
    status: PermissionStatus,
    blocked: Boolean,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconBadge(
            icon = copy.icon,
            tint = MaterialTheme.colorScheme.primary,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(copy.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                if (blocked && status == PermissionStatus.NOT_GRANTED) "Turned off. Allow it in Settings." else copy.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        when (status) {
            PermissionStatus.GRANTED -> StatusChip("Granted", StatusTone.SUCCESS)
            PermissionStatus.SKIPPED -> StatusChip("Skipped", StatusTone.NEUTRAL)
            PermissionStatus.NOT_GRANTED -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (PermissionCatalog.isOptional(item)) {
                    TextButton(onClick = onSkip) {
                        Text("Skip", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                GrantButton(label = if (blocked) "Settings" else "Grant", onClick = onGrant)
            }
        }
    }
}

/** Compact pill button: the checklist's rows stay short with this beside the copy. */
@Composable
private fun GrantButton(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        shape = WatchShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        interactionSource = interactionSource,
        modifier = Modifier.height(34.dp).pressScale(interactionSource),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, name = "Partial grant")
@Composable
private fun PermissionsScreenPartialPreview() {
    WatchTheme {
        PremiumBackground {
            PermissionsScreen(
                permissions = mapOf(
                    PermissionItem.BLUETOOTH to PermissionStatus.GRANTED,
                    PermissionItem.NOTIFICATIONS to PermissionStatus.GRANTED,
                    PermissionItem.NOTIFICATION_ACCESS to PermissionStatus.GRANTED,
                    PermissionItem.PHONE_AND_CONTACTS to PermissionStatus.NOT_GRANTED,
                    PermissionItem.LOCATION to PermissionStatus.NOT_GRANTED,
                ),
                onPermissionResult = { _, _ -> },
                onPermissionSkipped = {},
                onContinue = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "All granted")
@Composable
private fun PermissionsScreenGrantedPreview() {
    WatchTheme {
        PremiumBackground {
            PermissionsScreen(
                permissions = PermissionItem.entries.associateWith { PermissionStatus.GRANTED },
                onPermissionResult = { _, _ -> },
                onPermissionSkipped = {},
                onContinue = {},
            )
        }
    }
}
