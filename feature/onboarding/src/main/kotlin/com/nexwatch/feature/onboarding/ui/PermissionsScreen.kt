package com.nexwatch.feature.onboarding.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.PermissionItem
import com.nexwatch.feature.onboarding.PermissionStatus
import com.nexwatch.feature.onboarding.permissions.PermissionCatalog
import com.nexwatch.feature.onboarding.permissions.PermissionRuntime

private data class PermissionCopy(val title: String, val reason: String)

private val PERMISSION_COPY = mapOf(
    PermissionItem.BLUETOOTH to PermissionCopy("Nearby devices", "To find and connect to your watch."),
    PermissionItem.NOTIFICATIONS to PermissionCopy("Notifications", "So the app can show connection and sync status."),
    PermissionItem.NOTIFICATION_ACCESS to PermissionCopy("Notification access", "To forward phone notifications to your watch."),
    PermissionItem.PHONE_AND_CONTACTS to PermissionCopy("Phone & contacts", "For caller ID and rejecting calls from the watch."),
    PermissionItem.LOCATION to PermissionCopy("Location", "Optional — used only for local weather on the watch."),
)

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

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "$grantedCount of ${permissions.size} granted",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(PermissionItem.entries.toList()) { item ->
                val copy = PERMISSION_COPY.getValue(item)
                val status = permissions.getValue(item)
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    onPermissionResult(item, results.values.all { it })
                }
                PermissionRow(
                    title = copy.title,
                    reason = copy.reason,
                    status = status,
                    optional = PermissionCatalog.isOptional(item),
                    onGrant = {
                        when {
                            PermissionCatalog.isSpecialAccess(item) -> {
                                context.startActivity(PermissionRuntime.notificationListenerSettingsIntent())
                                onPermissionResult(item, PermissionRuntime.isNotificationListenerEnabled(context))
                            }
                            else -> {
                                val required = PermissionCatalog.runtimePermissions(item, Build.VERSION.SDK_INT)
                                if (required.isEmpty()) onPermissionResult(item, true) else launcher.launch(required.toTypedArray())
                            }
                        }
                    },
                    onSkip = { onPermissionSkipped(item) },
                )
            }
        }
        EntranceItem(index = PermissionItem.entries.size + 1, modifier = Modifier.padding(top = 16.dp)) {
            PrimaryButton(text = "Continue", onClick = onContinue)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    reason: String,
    status: PermissionStatus,
    optional: Boolean,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (status) {
            PermissionStatus.GRANTED -> StatusChip("Granted", StatusTone.SUCCESS)
            PermissionStatus.SKIPPED -> StatusChip("Skipped", StatusTone.NEUTRAL)
            PermissionStatus.NOT_GRANTED -> {
                Row {
                    if (optional) {
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }
                    TextButton(onClick = onGrant) { Text("Grant") }
                }
            }
        }
    }
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
                    PermissionItem.NOTIFICATION_ACCESS to PermissionStatus.NOT_GRANTED,
                    PermissionItem.PHONE_AND_CONTACTS to PermissionStatus.NOT_GRANTED,
                    PermissionItem.LOCATION to PermissionStatus.SKIPPED,
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
