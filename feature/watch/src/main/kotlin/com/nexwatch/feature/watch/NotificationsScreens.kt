package com.nexwatch.feature.watch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.notification.ForwardingLogEntry
import com.nexwatch.core.watchapi.notification.ForwardingOutcome
import com.nexwatch.core.watchapi.notification.ForwardingSkip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Messaging apps go first in the app list, where the user is most likely to want them on. */
private val MESSAGING_PACKAGES = setOf(
    "com.whatsapp",
    "com.whatsapp.w4b",
    "org.telegram.messenger",
    "org.thunderdog.challegram",
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.android.messaging",
    "org.thoughtcrime.securesms",
    "com.facebook.orca",
    "com.viber.voip",
    "com.discord",
    "com.Slack",
    "com.microsoft.teams",
    "com.instagram.android",
    "org.linphone",
)

@Composable
internal fun NotificationsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenAccessSettings: () -> Unit,
    onRequestCallPermissions: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenActivity: () -> Unit,
) {
    SettingsScaffold(title = "Notifications and calls", onBack = onBack, notice = null) {
        if (!state.accessGranted) AccessMissingCard(onOpenAccessSettings)

        SettingsCard(title = "Notifications") {
            SwitchRow(
                label = "Forward notifications to watch",
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                supporting = forwardingStatus(state),
            )
        }

        SettingsCard(title = "Calls") {
            ReadinessRow("Incoming call alerts", state.calls.alerts)
            ReadinessRow("Caller name and number", state.calls.callerNames)
            ReadinessRow("Reject calls from watch", state.calls.rejectFromWatch)
            if (state.calls.missingPermissions.isNotEmpty()) {
                SecondaryButton("Allow phone access", onClick = onRequestCallPermissions)
            }
        }

        NavigationRow(
            title = "Apps",
            value = "${state.installedAllowedCount} allowed",
            onClick = onOpenApps,
        )
        NavigationRow(
            title = "Forwarding activity",
            value = if (state.log.isEmpty()) "None yet" else "${state.log.size} recent",
            onClick = onOpenActivity,
        )
    }
}

private fun forwardingStatus(state: NotificationsUiState): String = when {
    !state.accessGranted -> "Paused: notification access is off"
    !state.enabled -> "Nothing is sent to the watch"
    state.forwardedToday == 1 -> "1 forwarded today"
    else -> "${state.forwardedToday} forwarded today"
}

/** Batch 6.4. The one state where forwarding silently does nothing, so it gets the top of the screen. */
@Composable
private fun AccessMissingCard(onOpenAccessSettings: () -> Unit) {
    Surface(shape = WatchShapes.card, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.NotificationsOff, contentDescription = null, tint = WatchTheme.colors.error)
                Text("Notification access is off", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "Android only shares notifications with apps you allow. Until NexWatch has access, " +
                    "no messages reach your watch. Calls are not affected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Grant access", onClick = onOpenAccessSettings)
        }
    }
}

@Composable
private fun ReadinessRow(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        StatusChip(if (ready) "Ready" else "Needs permission", if (ready) StatusTone.SUCCESS else StatusTone.WARNING)
    }
}

@Composable
private fun NavigationRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Batch 6.2. */
@Composable
internal fun NotificationAppsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onAllowedChange: (packageName: String, allowed: Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    ListScaffold(title = "Apps", onBack = onBack) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        val apps = state.apps
        if (apps == null) {
            item { Text("Loading apps…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@ListScaffold
        }
        val matching = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        val (suggested, others) = matching.partition { it.packageName in MESSAGING_PACKAGES }
        appGroup("Suggested", suggested, state.allowedPackages, onAllowedChange)
        appGroup(if (suggested.isEmpty()) "All apps" else "Other apps", others, state.allowedPackages, onAllowedChange)
        if (matching.isEmpty()) {
            item { Text("No apps match \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun LazyListScope.appGroup(
    title: String,
    apps: List<InstalledApp>,
    allowed: Set<String>,
    onAllowedChange: (String, Boolean) -> Unit,
) {
    if (apps.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title) }
    items(apps, key = { it.packageName }) { app ->
        val checked = app.packageName in allowed
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch) { onAllowedChange(app.packageName, it) }
                .padding(vertical = 8.dp),
        ) {
            AppIcon(app.packageName)
            Text(app.label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

private enum class LogFilter(val label: String) { ALL("All"), SENT("Sent"), SKIPPED("Skipped") }

/** Batch 6.3. In memory only, so it starts empty whenever the app process restarts. */
@Composable
internal fun ForwardingActivityScreen(state: NotificationsUiState, onBack: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    val entries = state.log.filter {
        when (filter) {
            LogFilter.ALL -> true
            LogFilter.SENT -> it.outcome == ForwardingOutcome.Sent
            LogFilter.SKIPPED -> it.outcome != ForwardingOutcome.Sent
        }
    }
    ListScaffold(title = "Forwarding activity", onBack = onBack) {
        item {
            ChoiceChips(LogFilter.entries, filter, LogFilter::label, { filter = it }, Modifier.padding(bottom = 8.dp))
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    if (state.log.isEmpty()) {
                        "Nothing yet. Notifications from allowed apps show up here, newest first, until NexWatch is restarted."
                    } else {
                        "Nothing in this filter."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(entries, key = { "${it.atMs}-${it.packageName}-${it.title}" }) { entry ->
            LogRow(entry, state.labelFor(entry.packageName))
        }
    }
}

@Composable
private fun LogRow(entry: ForwardingLogEntry, appLabel: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        AppIcon(entry.packageName)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$appLabel · ${formatLogTime(entry.atMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutcomeTag(entry.outcome)
        }
    }
}

/** Icon and words, never colour alone. */
@Composable
private fun OutcomeTag(outcome: ForwardingOutcome) {
    val (icon, tint, label) = when (outcome) {
        ForwardingOutcome.Sent -> Triple(Icons.Filled.CheckCircle, WatchTheme.colors.success, "Sent")
        is ForwardingOutcome.Skipped -> Triple(Icons.Filled.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Skipped: ${outcome.reason.label}")
        is ForwardingOutcome.Failed -> Triple(Icons.Filled.Error, WatchTheme.colors.error, "Failed: ${outcome.reason}")
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
    }
}

private val ForwardingSkip.label: String
    get() = when (this) {
        ForwardingSkip.DUPLICATE -> "duplicate"
        ForwardingSkip.THROTTLED -> "too many from this app at once"
        ForwardingSkip.WATCH_DISCONNECTED -> "watch disconnected"
        ForwardingSkip.WATCH_BUSY -> "watch busy syncing"
    }

private fun formatLogTime(atMs: Long): String {
    val at = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault())
    val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return if (at.toLocalDate() == LocalDate.now()) time else "${at.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} $time"
}

/** Off the main thread: a launcher icon can be an adaptive drawable that takes real work to rasterise. */
@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** [SettingsScaffold] for screens long enough to need a lazy list. */
@Composable
private fun ListScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(content = content)
    }
}

private val previewState = NotificationsUiState(
    accessGranted = true,
    enabled = true,
    allowedPackages = setOf("org.telegram.messenger", "com.google.android.apps.messaging"),
    forwardedToday = 12,
    apps = listOf(
        InstalledApp("com.google.android.gm", "Gmail"),
        InstalledApp("com.google.android.apps.messaging", "Messages"),
        InstalledApp("org.telegram.messenger", "Telegram"),
        InstalledApp("com.reddit.frontpage", "Reddit"),
    ),
    log = listOf(
        ForwardingLogEntry(1_760_100_000_000, "org.telegram.messenger", "Amina", ForwardingOutcome.Sent),
        ForwardingLogEntry(1_760_099_000_000, "org.telegram.messenger", "Amina", ForwardingOutcome.Skipped(ForwardingSkip.DUPLICATE)),
        ForwardingLogEntry(1_760_090_000_000, "com.google.android.apps.messaging", "M-PESA", ForwardingOutcome.Skipped(ForwardingSkip.WATCH_DISCONNECTED)),
        ForwardingLogEntry(1_760_080_000_000, "com.google.android.apps.messaging", "Bank", ForwardingOutcome.Failed("timed out after 5s")),
    ),
)

@Preview(showBackground = true)
@Composable
private fun NotificationsScreenPreview() {
    WatchTheme { PremiumBackground { NotificationsScreen(previewState, {}, {}, {}, {}, {}, {}) } }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsAccessMissingPreview() {
    WatchTheme {
        PremiumBackground {
            NotificationsScreen(
                previewState.copy(
                    accessGranted = false,
                    calls = CallReadiness(alerts = true, callerNames = false, rejectFromWatch = false, missingPermissions = listOf("x")),
                ),
                {}, {}, {}, {}, {}, {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationAppsPreview() {
    WatchTheme { PremiumBackground { NotificationAppsScreen(previewState, {}, { _, _ -> }) } }
}

@Preview(showBackground = true)
@Composable
private fun ForwardingActivityPreview() {
    WatchTheme { PremiumBackground { ForwardingActivityScreen(previewState, {}) } }
}

@Preview(showBackground = true)
@Composable
private fun ForwardingActivityEmptyPreview() {
    WatchTheme { PremiumBackground { ForwardingActivityScreen(previewState.copy(log = emptyList()), {}) } }
}
