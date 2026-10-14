package com.nexwatch.core.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.theme.Manrope
import com.nexwatch.core.designsystem.theme.TextMuted
import com.nexwatch.core.designsystem.theme.WatchMotion
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import kotlin.math.abs
import kotlin.math.roundToInt

private const val RING_FILL_MS = 1100
private const val RING_STAGGER_MS = 90
private const val GRID_ALPHA = 0.45f
private val AXIS_LABEL_SPACE = 20.dp

private val axisStyle = TextStyle(fontFamily = Manrope, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)

data class RingSpec(val progress: Float, val color: Color)

/**
 * Concentric goal rings, outermost first. They fill once on first show with the strong
 * ease-in-out (Part C: ring fill is on-screen morphing), and re-animate when a value changes.
 */
@Composable
fun ActivityRings(
    rings: List<RingSpec>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    stroke: Dp = 11.dp,
    gap: Dp = 2.dp,
) {
    val inPreview = LocalInspectionMode.current
    val animated = rings.mapIndexed { index, ring ->
        val anim = remember { Animatable(if (inPreview) ring.progress else 0f) }
        LaunchedEffect(ring.progress) {
            anim.animateTo(
                ring.progress.coerceIn(0f, 1f),
                tween(RING_FILL_MS, delayMillis = if (anim.value == 0f) index * RING_STAGGER_MS else 0, easing = WatchMotion.easeInOutStrong),
            )
        }
        anim.value
    }
    Canvas(modifier = modifier.size(size).semantics { this.contentDescription = contentDescription }) {
        val strokePx = stroke.toPx()
        rings.forEachIndexed { index, ring ->
            val inset = strokePx / 2 + index * (strokePx + gap.toPx())
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(ring.color.copy(alpha = 0.15f), 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            val sweep = 360f * animated[index]
            if (sweep > 0.5f) {
                drawArc(ring.color, -90f, sweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            }
        }
    }
}

/** One time bucket on a day chart; [dayFraction] is the bucket's start as a fraction of 24 h. */
data class RangePoint(val dayFraction: Float, val avg: Float, val min: Float, val max: Float)

/**
 * A whole day as a min-max band with the average drawn over it, a dashed [reference] line,
 * and a dot at the latest point. Touch or drag reads a bucket; the cursor follows the finger
 * directly, with no easing on a value the user is steering.
 */
@Composable
fun DayRangeChart(
    points: List<RangePoint>,
    color: Color,
    contentDescription: String,
    tooltip: (RangePoint) -> String,
    modifier: Modifier = Modifier,
    reference: Float? = null,
    yMin: Float = 40f,
    yMax: Float = 150f,
    gridValues: List<Float> = listOf(60f, 100f, 140f),
    height: Dp = 128.dp,
    maxGapFraction: Float = 1f / 32f,
) {
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableIntStateOf(-1) }
    var widthPx by remember { mutableIntStateOf(0) }
    var tipWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val refColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = WatchTheme.colors.border.copy(alpha = GRID_ALPHA)
    val surface = MaterialTheme.colorScheme.surface
    val cursorColor = MaterialTheme.colorScheme.onSurface

    fun nearest(x: Float): Int {
        if (points.isEmpty() || widthPx == 0) return -1
        val fraction = x / widthPx
        return points.indices.minBy { abs(points[it].dayFraction - fraction) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width }
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(points) {
                detectTapGestures { offset -> selected = nearest(offset.x).takeIf { it != selected } ?: -1 }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures(
                    onDragStart = { selected = nearest(it.x) },
                    onHorizontalDrag = { change, _ -> selected = nearest(change.position.x) },
                )
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val labelSpace = AXIS_LABEL_SPACE.toPx()
            val top = 6.dp.toPx()
            val plotH = size.height - labelSpace - top
            fun x(f: Float) = f * size.width
            fun y(v: Float) = top + (1f - (v - yMin) / (yMax - yMin)) * plotH

            gridValues.forEach { v ->
                drawLine(gridColor, Offset(0f, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
                drawAxisLabel(measurer, v.roundToInt().toString(), size.width, y(v) - 3.dp.toPx(), alignEnd = true)
            }
            listOf("00:00", "06:00", "12:00", "18:00").forEachIndexed { i, label ->
                drawAxisLabel(measurer, label, size.width * i / 4f, size.height - 2.dp.toPx(), alignEnd = false, bottom = true)
            }
            if (reference != null) {
                drawLine(
                    refColor.copy(alpha = 0.55f), Offset(0f, y(reference)), Offset(size.width, y(reference)), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            }
            segments(points, maxGapFraction).forEach { seg ->
                val topPts = seg.map { Offset(x(it.dayFraction), y(it.max)) }
                val botPts = seg.map { Offset(x(it.dayFraction), y(it.min)) }.reversed()
                val band = smoothPath(topPts).apply {
                    lineTo(botPts.first().x, botPts.first().y)
                    appendSmooth(this, botPts)
                    close()
                }
                drawPath(band, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.1f))))
                drawPath(
                    smoothPath(seg.map { Offset(x(it.dayFraction), y(it.avg)) }),
                    color,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            points.lastOrNull()?.let { last ->
                val c = Offset(x(last.dayFraction), y(last.avg))
                drawCircle(surface, 6.dp.toPx(), c)
                drawCircle(color, 4.dp.toPx(), c)
            }
            points.getOrNull(selected)?.let { p ->
                val px = x(p.dayFraction)
                drawLine(cursorColor.copy(alpha = 0.6f), Offset(px, top), Offset(px, top + plotH), 1.dp.toPx())
                drawCircle(color, 5.5.dp.toPx(), Offset(px, y(p.avg)))
                drawCircle(cursorColor, 3.5.dp.toPx(), Offset(px, y(p.avg)))
            }
        }
        points.getOrNull(selected)?.let { p ->
            val pointX = p.dayFraction * widthPx
            val tipX = (pointX - tipWidth / 2f).coerceIn(0f, (widthPx - tipWidth).toFloat().coerceAtLeast(0f))
            Text(
                text = tooltip(p),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .offset { IntOffset(tipX.roundToInt(), with(density) { (-30).dp.roundToPx() }) }
                    .onSizeChanged { tipWidth = it.width }
                    .background(MaterialTheme.colorScheme.surfaceVariant, WatchShapes.pill)
                    .border(1.dp, WatchTheme.colors.border, WatchShapes.pill)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** A sleep span on one of four lanes (0 = top). Start and end are fractions of the night. */
data class HypnogramSpan(val lane: Int, val start: Float, val end: Float)

@Composable
fun Hypnogram(
    spans: List<HypnogramSpan>,
    laneColors: List<Color>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    Canvas(modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
        val lane = size.height / laneColors.size
        val radius = CornerRadius(2.dp.toPx())
        laneColors.indices.forEach { i ->
            drawRoundRect(track, Offset(0f, i * lane + 1), Size(size.width, lane - 2), radius)
        }
        spans.forEach { s ->
            val w = ((s.end - s.start) * size.width - 0.6f).coerceAtLeast(1.2f)
            drawRoundRect(laneColors[s.lane], Offset(s.start * size.width, s.lane * lane + 1), Size(w, lane - 2), radius)
        }
    }
}

enum class ColumnStyle { BAR, CAPSULE, LOLLIPOP }

/**
 * One day's column. [low] is ignored for [ColumnStyle.BAR], which always starts at the
 * baseline. [marker] draws a dot (resting heart rate on a range capsule). [emphasis] dims a
 * column, such as today's still-filling bar.
 */
data class ColumnDatum(val low: Float?, val high: Float, val marker: Float? = null, val emphasis: Float = 1f)

/** Day-by-day columns with optional grid, goal line and per-column labels (null = no label). */
@Composable
fun ColumnChart(
    columns: List<ColumnDatum?>,
    color: Color,
    style: ColumnStyle,
    yMin: Float,
    yMax: Float,
    xLabels: List<String?>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    gridValues: List<Float> = emptyList(),
    goal: Float? = null,
    goalLabel: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val gridColor = WatchTheme.colors.border.copy(alpha = GRID_ALPHA)
    Canvas(modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
        val labelSpace = AXIS_LABEL_SPACE.toPx() - 2.dp.toPx()
        val top = 4.dp.toPx()
        val base = size.height - labelSpace
        val plotH = base - top
        fun y(v: Float) = top + (1f - ((v - yMin) / (yMax - yMin)).coerceIn(0f, 1f)) * plotH
        val n = columns.size.coerceAtLeast(1)
        val slot = size.width / n
        val dense = n > 10

        gridValues.forEach { v -> drawLine(gridColor, Offset(0f, y(v)), Offset(size.width, y(v)), 1.dp.toPx()) }
        columns.forEachIndexed { i, d ->
            if (d == null) return@forEachIndexed
            val cx = (i + 0.5f) * slot
            val c = color.copy(alpha = color.alpha * d.emphasis)
            when (style) {
                ColumnStyle.BAR -> {
                    val w = minOf(if (dense) 6.dp.toPx() else 22.dp.toPx(), slot * 0.62f)
                    val t = y(d.high)
                    drawRoundRect(c, Offset(cx - w / 2, t), Size(w, base - t), CornerRadius(minOf(5.dp.toPx(), w / 2)))
                }
                ColumnStyle.CAPSULE -> {
                    val w = minOf(if (dense) 4.dp.toPx() else 10.dp.toPx(), slot * 0.5f)
                    val t = y(d.high)
                    val b = y(d.low ?: d.high)
                    drawRoundRect(c.copy(alpha = c.alpha * 0.5f), Offset(cx - w / 2, t), Size(w, (b - t).coerceAtLeast(w)), CornerRadius(w / 2))
                }
                ColumnStyle.LOLLIPOP -> {
                    val lw = if (dense) 1.dp.toPx() else 2.dp.toPx()
                    d.low?.let { drawLine(c.copy(alpha = c.alpha * 0.4f), Offset(cx, y(it)), Offset(cx, y(d.high)), lw, StrokeCap.Round) }
                    drawCircle(c, if (dense) 1.8.dp.toPx() else 3.2.dp.toPx(), Offset(cx, y(d.high)))
                }
            }
            d.marker?.let { drawCircle(c, if (dense) 2.2.dp.toPx() else 3.5.dp.toPx(), Offset(cx, y(it))) }
        }
        if (goal != null) {
            drawLine(
                color.copy(alpha = 0.7f), Offset(0f, y(goal)), Offset(size.width, y(goal)), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
            )
            if (goalLabel != null) drawAxisLabel(measurer, goalLabel, size.width, y(goal) - 3.dp.toPx(), alignEnd = true)
        }
        drawColumnLabels(measurer, xLabels, slot)
    }
}

/** Stacked columns, bottom segment first; [colors] lines up with each column's segments. */
@Composable
fun StackedColumnChart(
    columns: List<List<Float>?>,
    colors: List<Color>,
    yMax: Float,
    xLabels: List<String?>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(height).semantics { this.contentDescription = contentDescription }) {
        val labelSpace = AXIS_LABEL_SPACE.toPx() - 2.dp.toPx()
        val base = size.height - labelSpace
        val plotH = base - 4.dp.toPx()
        val n = columns.size.coerceAtLeast(1)
        val slot = size.width / n
        val w = minOf(if (n > 10) 6.dp.toPx() else 22.dp.toPx(), slot * 0.62f)
        columns.forEachIndexed { i, segments ->
            if (segments == null) return@forEachIndexed
            var bottom = base
            segments.forEachIndexed { k, value ->
                val h = value / yMax * plotH
                drawRoundRect(colors[k], Offset((i + 0.5f) * slot - w / 2, bottom - h), Size(w, (h - 1f).coerceAtLeast(0f)), CornerRadius(2.dp.toPx()))
                bottom -= h
            }
        }
        drawColumnLabels(measurer, xLabels, slot)
    }
}

private fun DrawScope.drawColumnLabels(measurer: TextMeasurer, labels: List<String?>, slot: Float) {
    labels.forEachIndexed { i, label ->
        if (label == null) return@forEachIndexed
        val layout = measurer.measure(label, axisStyle)
        val cx = ((i + 0.5f) * slot - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(cx, size.height - layout.size.height))
    }
}

private fun DrawScope.drawAxisLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baselineY: Float,
    alignEnd: Boolean,
    bottom: Boolean = false,
) {
    val layout = measurer.measure(text, axisStyle)
    val left = if (alignEnd) x - layout.size.width else x
    val topY = if (bottom) size.height - layout.size.height else baselineY - layout.size.height
    drawText(layout, topLeft = Offset(left.coerceAtLeast(0f), topY))
}

/** Splits where the watch recorded nothing for a while, so a gap isn't drawn as a straight line. */
private fun segments(points: List<RangePoint>, maxGap: Float): List<List<RangePoint>> {
    val out = mutableListOf<MutableList<RangePoint>>()
    points.forEach { p ->
        val current = out.lastOrNull()
        if (current == null || p.dayFraction - current.last().dayFraction > maxGap) out += mutableListOf(p) else current += p
    }
    return out
}

private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) lineTo(points[0].x + 0.5f, points[0].y)
    appendSmooth(this, points)
}

/** Catmull-Rom through [points] as cubic Béziers; assumes the path already sits at points[0]. */
private fun appendSmooth(path: Path, points: List<Offset>) {
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}
