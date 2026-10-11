package com.nexwatch.feature.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.designsystem.chart.ActivityRings
import com.nexwatch.core.designsystem.chart.DayRangeChart
import com.nexwatch.core.designsystem.chart.Hypnogram
import com.nexwatch.core.designsystem.chart.RangePoint
import com.nexwatch.core.designsystem.chart.RingSpec
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.MetricText
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.component.WatchGlyph
import com.nexwatch.core.designsystem.component.metricStyle
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.model.BloodPressureReading
import com.nexwatch.core.model.HeartRateRange
import com.nexwatch.core.model.HeartRateSample
import com.nexwatch.core.model.SleepNight
import com.nexwatch.core.model.SleepStage
import com.nexwatch.core.model.SleepStageSpan
import com.nexwatch.core.model.Spo2Reading
import com.nexwatch.core.model.WorkoutSummary
import com.nexwatch.core.service.NotificationAccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** There is no goal setting yet, so these are the design prompt's defaults (Part A sample day). */
private const val STEP_GOAL = 10_000
private const val CALORIE_GOAL = 600
private const val DAY_MS = 24 * 60 * 60_000f
private const val SYNC_SPIN_MS = 1000

private val headerDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

@Composable
fun TodayRoute(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val forwardingEnabled by viewModel.forwardingEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // §8.5: the user can revoke access in system settings at any time, and forwarding then stops
    // without any error to show. Re-checked on every return to this screen, along with the clock
    // that "4 min ago" is measured against (nothing ticks while the screen is open, per I5).
    var accessGranted by remember { mutableStateOf(true) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        accessGranted = NotificationAccess.isGranted(context)
        nowMs = System.currentTimeMillis()
        viewModel.onResume()
        onPauseOrDispose {}
    }
    TodayScreen(
        state = state,
        nowMs = maxOf(nowMs, state.lastSyncMs ?: 0),
        showAccessBanner = forwardingEnabled && !accessGranted && state.link != WatchLink.Unbound,
        onGrantAccess = { NotificationAccess.openSettings(context) },
        onSync = viewModel::syncNow,
        onRetryLogin = viewModel::retryLogin,
        onOpenBluetooth = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    nowMs: Long,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    showAccessBanner: Boolean = false,
    onGrantAccess: () -> Unit = {},
    onSync: () -> Unit = {},
    onRetryLogin: () -> Unit = {},
    onOpenBluetooth: () -> Unit = {},
) {
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    // Data shown while the watch is away is last-known, so every card says how old it is.
    val asOf = if (!state.connected) state.lastSyncMs?.let { "As of ${clock(it, zone)}" } else null
    var index = 0
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntranceItem(index++) {
            Column(Modifier.padding(top = 6.dp)) {
                Text(headerDate.format(now), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    greetingFor(now.hour),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (state.link == WatchLink.Unbound) {
            EntranceItem(index++) {
                NexCard {
                    Text("No watch paired", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Pair a watch to see your day here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            return@Column
        }
        (state.link as? WatchLink.SignedInElsewhere)?.let {
            EntranceItem(index++) {
                Banner(
                    icon = Icons.Rounded.Error,
                    title = "Your watch is signed in to another app",
                    description = "Close the vendor app or any other watch app on this phone, then retry.",
                    action = "Retry",
                    onAction = onRetryLogin,
                )
            }
        }
        if (showAccessBanner) {
            EntranceItem(index++) {
                Banner(
                    icon = Icons.Rounded.NotificationsOff,
                    title = "Messages aren't reaching your watch",
                    description = "Notification access for NexWatch is off in Android settings.",
                    action = "Turn on",
                    onAction = onGrantAccess,
                )
            }
        }
        EntranceItem(index++) { WatchStrip(state, nowMs, zone, onSync, onOpenBluetooth) }
        EntranceItem(index++) { ActivityCard(state.activity, asOf, state.firstDay) }
        EntranceItem(index++) { HeartCard(state.heart, state.dayStartMs, asOf, zone) }
        EntranceItem(index++) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                SleepCard(state.lastNight, zone, Modifier.weight(1.12f).fillMaxHeight())
                if (state.showSpo2 || state.showBloodPressure) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        if (state.showSpo2) Spo2Card(state.spo2, state.spo2LowLastDay)
                        if (state.showBloodPressure) BloodPressureCard(state.bloodPressure, nowMs, zone)
                    }
                }
            }
        }
        state.lastWorkout?.let { workout -> EntranceItem(index++) { LastWorkoutRow(workout, nowMs, zone) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Banner(icon: ImageVector, title: String, description: String, action: String, onAction: () -> Unit) {
    val error = WatchTheme.colors.error
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(error.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .border(1.dp, error.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = error)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
            TextButton(onClick = onAction, modifier = Modifier.padding(start = 0.dp)) { Text(action, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun WatchStrip(state: TodayUiState, nowMs: Long, zone: ZoneId, onSync: () -> Unit, onOpenBluetooth: () -> Unit) {
    val link = state.link
    NexCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WatchGlyph(42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                when {
                    state.sync != null -> StatusChip("Syncing", StatusTone.INFO, icon = Icons.Rounded.Sync)
                    link is WatchLink.Connected -> StatusChip("Connected", StatusTone.SUCCESS, live = true)
                    link is WatchLink.NotNearby -> StatusChip("Not nearby", StatusTone.NEUTRAL, icon = Icons.Rounded.BluetoothDisabled)
                    link is WatchLink.BluetoothOff -> StatusChip("Bluetooth is off", StatusTone.WARNING, icon = Icons.Rounded.BluetoothDisabled)
                    link is WatchLink.SignedInElsewhere -> StatusChip("Can't sign in", StatusTone.ERROR)
                    else -> StatusChip("Connecting", StatusTone.NEUTRAL, icon = Icons.Rounded.WifiTethering)
                }
                val synced = state.lastSyncMs?.let { "Synced ${relativeTime(nowMs, it, zone)}" } ?: "Not synced yet"
                when (link) {
                    is WatchLink.Connected -> Row(verticalAlignment = Alignment.CenterVertically) {
                        link.battery?.let {
                            Icon(Icons.Rounded.Battery5Bar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            StripText("$it%  ·  ")
                        }
                        StripText(synced)
                    }
                    is WatchLink.NotNearby -> StripText(
                        (link.lastSeenMs?.let { "Last seen ${relativeTime(nowMs, it, zone)}. " } ?: "") + "Reconnects on its own when in range.",
                    )
                    WatchLink.BluetoothOff -> StripText("Turn Bluetooth on to reconnect.")
                    else -> StripText(synced)
                }
            }
            when (link) {
                is WatchLink.Connected -> SyncButton(syncing = state.sync != null, onSync = onSync)
                WatchLink.BluetoothOff -> TextButton(onClick = onOpenBluetooth) { Text("Turn on", fontWeight = FontWeight.Bold) }
                else -> Unit
            }
        }
        AnimatedVisibility(
            visible = state.sync != null,
            enter = expandVertically(tween(260, easing = WatchMotion.easeOutStrong)) + fadeIn(),
            exit = shrinkVertically(tween(200, easing = WatchMotion.easeOutStrong)) + fadeOut(),
        ) {
            val sync = state.sync
            Column(Modifier.padding(top = 12.dp, end = 4.dp)) {
                ProgressBar(sync?.fraction ?: 0f)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(syncPhaseLabel(sync?.dataType), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    sync?.fraction?.let { Text("${(it * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun StripText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SyncButton(syncing: Boolean, onSync: () -> Unit) {
    // The spin is a real, ongoing state, and it replaces the connected pulse while it runs,
    // so the screen still has only one looping animation (Part C).
    val rotation = if (syncing) {
        val transition = rememberInfiniteTransition(label = "syncSpin")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(SYNC_SPIN_MS, easing = LinearEasing), RepeatMode.Restart), label = "spin").value
    } else {
        0f
    }
    IconButton(
        onClick = onSync,
        enabled = !syncing,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.border(1.dp, WatchTheme.colors.border.copy(alpha = 0.9f), CircleShape),
    ) {
        Icon(Icons.Rounded.Sync, contentDescription = if (syncing) "Syncing" else "Sync now", modifier = Modifier.rotate(rotation))
    }
}

@Composable
internal fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(520, easing = WatchMotion.easeOutStrong), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.pill)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .graphicsLayer {
                    scaleX = animated
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)), WatchShapes.pill),
        )
    }
}

@Composable
private fun CardHeader(icon: ImageVector, tint: Color, title: String, meta: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 7.dp).weight(1f))
        if (meta != null) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityCard(activity: TodayActivity?, asOf: String?, firstDay: Boolean) {
    val steps = activity?.steps ?: 0
    val kcal = activity?.energyKcal ?: 0
    NexCard {
        if (asOf != null) {
            Text(asOf, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End).padding(bottom = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ActivityRings(
                rings = listOf(
                    RingSpec(steps / STEP_GOAL.toFloat(), WatchTheme.colors.activity),
                    RingSpec(kcal / CALORIE_GOAL.toFloat(), WatchTheme.colors.calories),
                ),
                contentDescription = "Steps ${steps * 100 / STEP_GOAL} percent of goal, calories ${kcal * 100 / CALORIE_GOAL} percent of goal",
                size = 112.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ActivityLine(Icons.AutoMirrored.Rounded.DirectionsWalk, WatchTheme.colors.activity, "%,d".format(Locale.UK, steps), "/ %,d".format(Locale.UK, STEP_GOAL), "Steps")
                ActivityLine(Icons.Rounded.LocalFireDepartment, WatchTheme.colors.calories, kcal.toString(), "/ $CALORIE_GOAL kcal", "Calories")
                ActivityLine(Icons.Rounded.Straighten, MaterialTheme.colorScheme.primary, kilometres(activity?.distanceM ?: 0), null, "Distance")
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(1.dp).background(WatchTheme.colors.border.copy(alpha = 0.5f)))
        val footer = when {
            firstDay -> "Wear your watch to start counting"
            steps >= STEP_GOAL -> "Step goal reached"
            else -> "%,d steps to goal".format(Locale.UK, STEP_GOAL - steps)
        }
        Text(footer, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ActivityLine(icon: ImageVector, tint: Color, value: String, goal: String?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Column {
            MetricText(value, goal, valueSize = 20.sp, unitSize = 13.sp)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeartCard(heart: TodayHeart?, dayStartMs: Long, asOf: String?, zone: ZoneId) {
    val color = WatchTheme.colors.heartRate
    NexCard {
        CardHeader(Icons.Rounded.Favorite, color, "Heart rate", asOf ?: "Today")
        val latest = heart?.latest
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                MetricText(latest?.bpm?.toString() ?: "--", "bpm")
                latest?.let {
                    Text("At ${clock(it.atMs, zone)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (heart?.minBpm != null) {
                Stat("Resting", heart.restingBpm?.toString() ?: "--", "bpm")
                Spacer(Modifier.width(18.dp))
                Stat("Range", "${heart.minBpm}-${heart.maxBpm}", null)
            }
        }
        val points = heart?.buckets.orEmpty().map { it.toPoint(dayStartMs) }
        Box(Modifier.padding(top = 14.dp)) {
            DayRangeChart(
                points = points,
                color = color,
                reference = heart?.restingBpm?.toFloat(),
                contentDescription = heart?.let { "Heart rate today, ${it.minBpm ?: "no"} to ${it.maxBpm ?: "no"} beats per minute" } ?: "No heart rate yet today",
                tooltip = { p -> "${clock(dayStartMs + (p.dayFraction * DAY_MS).toLong(), zone)}  ${p.avg.roundToInt()} bpm (${p.min.roundToInt()}-${p.max.roundToInt()})" },
            )
            if (points.isEmpty()) {
                Text(
                    "Wear your watch for a few minutes to see your first readings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp, vertical = 0.dp).padding(bottom = 18.dp),
                )
            }
        }
    }
}

private fun HeartRateRange.toPoint(dayStartMs: Long) =
    RangePoint((atMs - dayStartMs) / DAY_MS, avgBpm.toFloat(), minBpm.toFloat(), maxBpm.toFloat())

@Composable
private fun Stat(label: String, value: String, unit: String?) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MetricText(value, unit, valueSize = 17.sp, unitSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SleepCard(night: SleepNight?, zone: ZoneId, modifier: Modifier = Modifier) {
    NexCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        CardHeader(Icons.Rounded.Bedtime, WatchTheme.colors.sleepText, "Sleep", null)
        if (night == null || night.stages.isEmpty()) {
            Text("--", style = metricStyle(28.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp))
            Text("Shows up after your first night with the watch on.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            return@NexCard
        }
        val (h, m) = hoursMinutes(night.totalMinutes)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
            MetricText(h, "h", valueSize = 28.sp, unitSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            MetricText(m, "m", valueSize = 28.sp, unitSize = 13.sp)
        }
        // The watch reports 0 when it didn't score the night; that isn't a score.
        if (night.score > 0) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text("Score ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(night.score.toString(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = WatchTheme.colors.sleepText)
            }
        }
        val colors = WatchTheme.colors
        Hypnogram(
            spans = hypnogramSpans(night),
            laneColors = listOf(colors.sleepAwake, colors.sleepRem, colors.sleepLight, colors.sleepDeep),
            contentDescription = "Sleep stages, ${night.totalMinutes} minutes in total",
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(clock(night.stages.minOf { it.startMs }, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(clock(night.stages.maxOf { it.endMs }, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Spo2Card(reading: Spo2Reading?, lowLastDay: Int?) {
    MiniCard(Icons.Rounded.WaterDrop, WatchTheme.colors.spo2, "SpO2") {
        if (reading == null) {
            MiniValue("--", null)
            MiniCaption("Measured while you wear the watch")
        } else {
            MiniValue(reading.percent.toString(), "%")
            MiniCaption(lowLastDay?.let { "Lowest $it% in 24 h" } ?: "Latest reading")
        }
    }
}

@Composable
private fun BloodPressureCard(reading: BloodPressureReading?, nowMs: Long, zone: ZoneId) {
    MiniCard(Icons.Rounded.MonitorHeart, WatchTheme.colors.bloodPressure, "Blood pressure") {
        if (reading == null) {
            MiniValue("--", null)
            MiniCaption("Measured through the day")
        } else {
            MiniValue("${reading.systolic}/${reading.diastolic}", null)
            MiniCaption("mmHg, ${relativeTime(nowMs, reading.atMs, zone)}")
        }
    }
}

@Composable
private fun MiniCard(icon: ImageVector, tint: Color, title: String, content: @Composable () -> Unit) {
    NexCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        CardHeader(icon, tint, title, null)
        content()
    }
}

@Composable
private fun MiniValue(value: String, unit: String?) {
    MetricText(value, unit, valueSize = 28.sp, unitSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun MiniCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun LastWorkoutRow(workout: WorkoutSummary, nowMs: Long, zone: ZoneId) {
    NexCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(Icons.Rounded.FitnessCenter, tint = WatchTheme.colors.calories, background = WatchTheme.colors.calories.copy(alpha = 0.12f))
            Column(Modifier.weight(1f)) {
                Text("Last workout", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Text(workoutLine(workout, nowMs, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            workout.avgHrBpm?.let { Text("Avg $it bpm", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

internal fun workoutLine(workout: WorkoutSummary, nowMs: Long, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(workout.startMs).atZone(zone)
    val day = if (start.toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()) "Today" else DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH).format(start)
    val minutes = ((workout.endMs - workout.startMs) / 60_000).coerceAtLeast(1)
    val parts = mutableListOf("$day at ${clock(workout.startMs, zone)}", "$minutes min")
    if (workout.distanceM > 0) parts += kilometres(workout.distanceM.roundToInt())
    return parts.joinToString(", ")
}

// ---- previews ----

private val previewZone = ZoneId.of("Africa/Nairobi")
private val previewDay = LocalDate.of(2026, 10, 10).atStartOfDay(previewZone).toInstant().toEpochMilli()
private val previewNow = previewDay + (16 * 60 + 24) * 60_000L

private fun previewState(link: WatchLink = WatchLink.Connected(72), sync: SyncState? = null) = TodayUiState(
    loading = false,
    link = link,
    sync = sync,
    lastSyncMs = previewNow - 4 * 60_000,
    dayStartMs = previewDay,
    activity = TodayActivity(steps = 8_432, energyKcal = 412, distanceM = 6_100),
    heart = TodayHeart(
        latest = HeartRateSample(previewDay + 975 * 60_000L, 72),
        restingBpm = 61, minBpm = 54, maxBpm = 138,
        buckets = (0 until 65).map { i ->
            val avg = when {
                i < 25 -> 58
                i in 49..51 -> 112
                else -> 76 + (i % 5)
            }
            HeartRateRange(previewDay + i * 15 * 60_000L, avg, avg - 4, avg + 6)
        },
    ),
    lastNight = SleepNight(
        nightDate = "2026-10-09",
        stages = listOf(
            SleepStageSpan(SleepStage.LIGHT, 0, 60), SleepStageSpan(SleepStage.DEEP, 60, 145),
            SleepStageSpan(SleepStage.REM, 145, 243), SleepStageSpan(SleepStage.AWAKE, 243, 261),
            SleepStageSpan(SleepStage.LIGHT, 261, 432),
        ).map { it.copy(startMs = previewDay - 53 * 60_000L + it.startMs * 60_000L, endMs = previewDay - 53 * 60_000L + it.endMs * 60_000L) },
        totalMinutes = 432,
        score = 84,
    ),
    spo2 = Spo2Reading(previewNow - 3_600_000, 97),
    spo2LowLastDay = 94,
    bloodPressure = BloodPressureReading(previewDay + 482 * 60_000L, 118, 77),
    lastWorkout = WorkoutSummary("w", 1, previewDay + 740 * 60_000L, previewDay + 772 * 60_000L, 2_900f, 160f, 104, 138),
)

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun TodayConnectedPreview() {
    WatchTheme { PremiumBackground { TodayScreen(previewState(), previewNow, zone = previewZone) } }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TodaySyncingPreview() {
    WatchTheme { PremiumBackground { TodayScreen(previewState(sync = SyncState(0.6f, "heart_rate")), previewNow, zone = previewZone) } }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TodayNotNearbyPreview() {
    WatchTheme { PremiumBackground { TodayScreen(previewState(link = WatchLink.NotNearby(previewDay + 492 * 60_000L)), previewNow, zone = previewZone) } }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun TodaySignedInElsewherePreview() {
    WatchTheme {
        PremiumBackground {
            TodayScreen(previewState(link = WatchLink.SignedInElsewhere("auth")), previewNow, zone = previewZone, showAccessBanner = true)
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun TodayFirstDayPreview() {
    WatchTheme {
        PremiumBackground {
            TodayScreen(TodayUiState(loading = false, link = WatchLink.Connected(72), lastSyncMs = previewNow, dayStartMs = previewDay), previewNow, zone = previewZone)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayUnboundPreview() {
    WatchTheme { PremiumBackground { TodayScreen(TodayUiState(loading = false, link = WatchLink.Unbound), previewNow, zone = previewZone) } }
}
