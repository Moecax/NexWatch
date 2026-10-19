package com.nexwatch.feature.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexwatch.core.designsystem.chart.ColumnChart
import com.nexwatch.core.designsystem.chart.ColumnDatum
import com.nexwatch.core.designsystem.chart.ColumnStyle
import com.nexwatch.core.designsystem.chart.StackedColumnChart
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.MetricText
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.ScreenTitle
import com.nexwatch.core.designsystem.component.SegmentedControl
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.model.DailyBloodPressure
import com.nexwatch.core.model.DailySpo2
import com.nexwatch.core.model.WorkoutSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val workoutWhen = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)
private const val RANGE_FADE_MS = 160

@Composable
fun HealthRoute(viewModel: HealthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    HealthScreen(state, onRangeSelected = viewModel::select)
}

@Composable
fun HealthScreen(state: HealthUiState, onRangeSelected: (HealthRange) -> Unit = {}, zone: ZoneId = ZoneId.systemDefault()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntranceItem(0) { ScreenTitle("Health", subtitle = "Trends from your watch, all stored on this phone.") }
        EntranceItem(1) {
            SegmentedControl(
                options = HealthRange.entries.map { it.label },
                selectedIndex = state.range.ordinal,
                onSelected = { onRangeSelected(HealthRange.entries[it]) },
            )
        }
        EntranceItem(2) {
            // One control drives every tile, so a range change cross-fades them together.
            AnimatedContent(
                targetState = state,
                contentKey = { it.range },
                transitionSpec = { fadeIn(tween(RANGE_FADE_MS, easing = WatchMotion.easeOutStrong)) togetherWith fadeOut(tween(RANGE_FADE_MS)) },
                label = "healthRange",
            ) { s -> Tiles(s, zone) }
        }
        if (state.unsupported.isNotEmpty()) {
            EntranceItem(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        "${state.unsupported.joinToString(" and ").replaceFirstChar { it.uppercase() }} aren't measured by this watch, so they're left out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Tiles(state: HealthUiState, zone: ZoneId) {
    val labels = xLabels(state.dates)
    val colors = WatchTheme.colors
    val days = state.dates.size
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(Icons.Rounded.Favorite, colors.heartRate, "Heart rate") {
            val range = state.heartRange()
            Kpis(
                main = { MetricText(state.averageResting()?.toString() ?: "--", "bpm", valueSize = 30.sp, unitSize = 14.sp) },
                mainCaption = "Average resting",
                second = range?.let { "${it.first}-${it.second}" } ?: "--",
                secondUnit = "bpm",
                secondCaption = "Range",
            )
            ChartOrEmpty(state.heart.all { it == null }, "No heart-rate readings in these $days days") {
                ColumnChart(
                    columns = state.heart.map { d -> d?.let { ColumnDatum(it.minBpm.toFloat(), it.maxBpm.toFloat(), it.restingBpm?.toFloat()) } },
                    color = colors.heartRate,
                    style = ColumnStyle.CAPSULE,
                    yMin = 40f,
                    yMax = 170f,
                    gridValues = listOf(60f, 100f, 140f),
                    xLabels = labels,
                    contentDescription = "Daily heart-rate range with resting heart rate",
                )
            }
        }
        Tile(Icons.Rounded.Bedtime, colors.sleepText, "Sleep") {
            Kpis(
                main = {
                    val avg = state.averageSleepMinutes()
                    if (avg == null) {
                        MetricText("--", null, valueSize = 30.sp)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            MetricText((avg / 60).toString(), "h", valueSize = 30.sp, unitSize = 14.sp)
                            Spacer(Modifier.size(6.dp))
                            MetricText("%02d".format(avg % 60), "m", valueSize = 30.sp, unitSize = 14.sp)
                        }
                    }
                },
                mainCaption = "Average per night",
                second = state.averageDeepMinutes()?.toString() ?: "--",
                secondUnit = "min",
                secondCaption = "Deep sleep",
            )
            val stageColors = listOf(colors.sleepDeep, colors.sleepRem, colors.sleepLight, colors.sleepAwake)
            ChartOrEmpty(state.sleep.all { it == null }, "No nights recorded in these $days days") {
                StackedColumnChart(
                    columns = state.sleep.map { d -> d?.let { listOf(it.deep, it.rem, it.light, it.awake).map(Int::toFloat) } },
                    colors = stageColors,
                    yMax = maxOf(540f, state.sleep.maxOfOrNull { it?.total ?: 0 }?.toFloat() ?: 0f),
                    xLabels = labels,
                    contentDescription = "Nightly sleep by stage",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
                listOf("Deep", "REM", "Light", "Awake").forEachIndexed { i, name -> LegendItem(stageColors[i], name) }
            }
        }
        Tile(Icons.AutoMirrored.Rounded.DirectionsWalk, colors.activity, "Activity") {
            Kpis(
                main = { MetricText(state.averageSteps()?.let { "%,d".format(Locale.UK, it) } ?: "--", null, valueSize = 30.sp) },
                mainCaption = "Average daily steps",
                second = "${state.daysAtGoal()} of $days",
                secondUnit = null,
                secondCaption = "Days at goal",
            )
            ChartOrEmpty(state.steps.all { it == null }, "No steps recorded in these $days days") {
                ColumnChart(
                    columns = state.steps.mapIndexed { i, s ->
                        // Today is still filling, so it's dimmed rather than read as a short day.
                        s?.let { ColumnDatum(null, it.toFloat(), emphasis = if (i == state.steps.lastIndex) 0.45f else if (it >= state.stepGoal) 1f else 0.78f) }
                    },
                    color = colors.activity,
                    style = ColumnStyle.BAR,
                    yMin = 0f,
                    yMax = maxOf(14_000f, state.stepGoal * 1.2f, (state.steps.maxOfOrNull { it ?: 0 } ?: 0) * 1.1f),
                    goal = state.stepGoal.toFloat(),
                    goalLabel = "Goal ${shortSteps(state.stepGoal)}",
                    xLabels = labels,
                    contentDescription = "Daily steps against a %,d goal".format(Locale.UK, state.stepGoal),
                )
            }
        }
        if (state.showSpo2 || state.showBloodPressure) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                if (state.showSpo2) Spo2Tile(state.spo2, labels, Modifier.weight(1f).fillMaxHeight())
                if (state.showBloodPressure) BloodPressureTile(state.bloodPressure, labels, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Tile(Icons.Rounded.FitnessCenter, colors.calories, "Workouts") {
            Kpis(
                main = { MetricText(state.workouts.size.toString(), null, valueSize = 30.sp) },
                mainCaption = "Workouts",
                second = if (state.workouts.isEmpty()) "--" else hoursAndMinutes(state.workoutMinutes()),
                secondUnit = null,
                secondCaption = "Total time",
            )
            if (state.workouts.isEmpty()) {
                Text(
                    "Workouts you record on the watch show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Column(Modifier.padding(top = 8.dp)) {
                    state.workouts.take(if (days <= 7) 3 else 4).forEachIndexed { i, w ->
                        if (i > 0) HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                        WorkoutRow(w, zone)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, tint: Color, title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    NexCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 7.dp))
        }
        content()
    }
}

@Composable
private fun Kpis(main: @Composable () -> Unit, mainCaption: String, second: String?, secondUnit: String?, secondCaption: String?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 10.dp)) {
        Column {
            main()
            Caption(mainCaption)
        }
        if (second != null && secondCaption != null) {
            Column {
                MetricText(second, secondUnit, valueSize = 17.sp, unitSize = 12.sp)
                Caption(secondCaption)
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
}

@Composable
private fun ChartOrEmpty(empty: Boolean, emptyText: String, chart: @Composable () -> Unit) {
    Box(Modifier.padding(top = 14.dp)) {
        if (empty) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            chart()
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Small tiles can't fit a label per day, so only the ends of the range are named. */
private fun endLabels(labels: List<String?>): List<String?> = labels.mapIndexed { i, l -> l.takeIf { i == 0 || i == labels.lastIndex } }

@Composable
private fun Spo2Tile(days: List<DailySpo2?>, labels: List<String?>, modifier: Modifier) {
    val state = HealthUiState(spo2 = days)
    Tile(Icons.Rounded.WaterDrop, WatchTheme.colors.spo2, "SpO2", modifier.fillMaxWidth()) {
        MetricText(state.averageSpo2()?.toString() ?: "--", state.averageSpo2()?.let { "%" }, valueSize = 26.sp, unitSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        Caption(state.lowestSpo2()?.let { "Average, low $it%" } ?: "No readings yet")
        if (days.any { it != null }) {
            ColumnChart(
                columns = days.map { d -> d?.let { ColumnDatum(it.minPercent.toFloat(), it.avgPercent.toFloat()) } },
                color = WatchTheme.colors.spo2,
                style = ColumnStyle.LOLLIPOP,
                yMin = 85f,
                yMax = 100f,
                gridValues = listOf(90f, 95f),
                xLabels = endLabels(labels),
                contentDescription = "Daily SpO2 average and lowest reading",
                height = 74.dp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun BloodPressureTile(days: List<DailyBloodPressure?>, labels: List<String?>, modifier: Modifier) {
    val latest = days.lastOrNull { it != null }
    Tile(Icons.Rounded.MonitorHeart, WatchTheme.colors.bloodPressure, "Blood pressure", modifier.fillMaxWidth()) {
        MetricText(latest?.let { "${it.systolic}/${it.diastolic}" } ?: "--", null, valueSize = 26.sp, modifier = Modifier.padding(top = 10.dp))
        Caption(if (latest != null) "Latest day, mmHg" else "No readings yet")
        if (latest != null) {
            ColumnChart(
                columns = days.map { d -> d?.let { ColumnDatum(it.diastolic.toFloat(), it.systolic.toFloat()) } },
                color = WatchTheme.colors.bloodPressure,
                style = ColumnStyle.CAPSULE,
                yMin = 50f,
                yMax = 150f,
                gridValues = listOf(80f, 120f),
                xLabels = endLabels(labels),
                contentDescription = "Daily blood pressure, systolic to diastolic",
                height = 74.dp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun WorkoutRow(workout: WorkoutSummary, zone: ZoneId) {
    val minutes = ((workout.endMs - workout.startMs) / 60_000).coerceAtLeast(1)
    val detail = buildList {
        add(workoutWhen.format(Instant.ofEpochMilli(workout.startMs).atZone(zone)))
        add("$minutes min")
        if (workout.distanceM > 0) add("%.1f km".format(Locale.UK, workout.distanceM / 1000f))
        if (workout.distanceM <= 0 && workout.kcal > 0) add("${workout.kcal.roundToInt()} kcal")
    }.joinToString(", ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 10.dp)) {
        IconBadge(Icons.Rounded.FitnessCenter, tint = WatchTheme.colors.calories, background = WatchTheme.colors.calories.copy(alpha = 0.12f))
        Column(Modifier.weight(1f)) {
            Text("Workout", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        workout.avgHrBpm?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text("$it bpm", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                Text("avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- previews ----

private fun previewState(range: HealthRange): HealthUiState {
    val today = LocalDate.of(2026, 10, 10)
    val dates = (range.days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val steps = listOf(11_240, 7_385, 10_512, 6_930, 9_815, 12_087, 8_432)
    return HealthUiState(
        range = range,
        dates = dates,
        heart = dates.mapIndexed { i, _ -> DayHeart(52 + i % 4, 120 + (i * 7) % 30, 58 + i % 5) },
        steps = dates.mapIndexed { i, _ -> steps[i % steps.size] },
        sleep = dates.mapIndexed { i, _ -> DaySleep(deep = 80 + i % 20, rem = 95 + i % 15, light = 220 + (i * 11) % 40, awake = 18) },
        spo2 = dates.mapIndexed { i, d -> DailySpo2(d.toString(), 96 + i % 2, 92 + i % 3) },
        bloodPressure = dates.mapIndexed { i, d -> DailyBloodPressure(d.toString(), 114 + i % 8, 74 + i % 5) },
        workouts = listOf(
            WorkoutSummary("a", 1, 1_791_000_000_000, 1_791_001_920_000, 2_900f, 160f, 104, 138),
            WorkoutSummary("b", 2, 1_790_700_000_000, 1_790_702_700_000, 0f, 386f, 131, 162),
        ),
        unsupported = listOf("Stress", "skin temperature"),
    )
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun HealthWeekPreview() {
    WatchTheme { PremiumBackground { HealthScreen(previewState(HealthRange.WEEK), zone = ZoneId.of("Africa/Nairobi")) } }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun HealthMonthPreview() {
    WatchTheme { PremiumBackground { HealthScreen(previewState(HealthRange.MONTH), zone = ZoneId.of("Africa/Nairobi")) } }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun HealthEmptyPreview() {
    val dates = (6 downTo 0).map { LocalDate.of(2026, 10, 10).minusDays(it.toLong()) }
    WatchTheme {
        PremiumBackground {
            HealthScreen(
                HealthUiState(dates = dates, heart = dates.map { null }, steps = dates.map { null }, sleep = dates.map { null }, spo2 = dates.map { null }, bloodPressure = dates.map { null }),
            )
        }
    }
}
