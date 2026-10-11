package com.nexwatch.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.BezelHighlight
import com.nexwatch.core.designsystem.theme.BezelMid
import com.nexwatch.core.designsystem.theme.BezelShadow
import com.nexwatch.core.designsystem.theme.FaceDeep
import com.nexwatch.core.designsystem.theme.FaceGlass
import com.nexwatch.core.designsystem.theme.StrapBottom
import com.nexwatch.core.designsystem.theme.StrapTop
import com.nexwatch.core.designsystem.theme.WatchButton
import kotlin.math.cos
import kotlin.math.sin

private val bezelBrush = Brush.sweepGradient(listOf(BezelMid, BezelShadow, BezelHighlight, BezelShadow, BezelMid))
private val faceBrush = Brush.radialGradient(listOf(FaceGlass, FaceDeep))

/**
 * A generic round watch head: steel bezel and a dark face (Part A: no brands, no real
 * designs). [face] draws on the face, centred.
 */
@Composable
fun WatchGlyph(size: Dp, modifier: Modifier = Modifier, face: @Composable BoxScope.() -> Unit = { WatchHands() }) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).background(bezelBrush, CircleShape)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 0.84f).background(faceBrush, CircleShape), content = face)
    }
}

/** The watch with straps and side buttons, for the Watch tab's hero. */
@Composable
fun WatchRender(modifier: Modifier = Modifier, headSize: Dp = 112.dp, face: @Composable BoxScope.() -> Unit = { WatchHands() }) {
    val strapWidth = headSize * 0.5f
    val strapHeight = headSize * 0.36f
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(width = headSize + 8.dp, height = headSize + strapHeight * 1.2f)) {
        val strap = Brush.verticalGradient(listOf(StrapTop, StrapBottom))
        Box(Modifier.align(Alignment.TopCenter).size(strapWidth, strapHeight).background(strap, RoundedCornerShape(12.dp)))
        Box(Modifier.align(Alignment.BottomCenter).size(strapWidth, strapHeight).background(strap, RoundedCornerShape(12.dp)))
        Box(Modifier.align(Alignment.CenterEnd).offset(y = (-8).dp).size(4.dp, 14.dp).background(WatchButton, RoundedCornerShape(2.dp)))
        Box(Modifier.align(Alignment.CenterEnd).offset(y = 10.dp).size(4.dp, 9.dp).background(WatchButton, RoundedCornerShape(2.dp)))
        WatchGlyph(headSize, face = face)
    }
}

/**
 * Hands parked at 10:10, the product-shot pose. Deliberately static: a live clock would
 * need a ticking recomposition, and the screen's one loop belongs to the status dot.
 */
@Composable
fun BoxScope.WatchHands() {
    val hand = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        val stroke = r * 0.07f
        fun tip(degrees: Double, length: Float): Offset {
            val rad = Math.toRadians(degrees - 90)
            return Offset(c.x + (cos(rad) * length).toFloat(), c.y + (sin(rad) * length).toFloat())
        }
        for (i in 0 until 12) {
            val outer = tip(i * 30.0, r * 0.86f)
            val inner = tip(i * 30.0, r * if (i % 3 == 0) 0.70f else 0.77f)
            drawLine(hand.copy(alpha = if (i % 3 == 0) 0.55f else 0.25f), inner, outer, stroke * 0.6f, StrokeCap.Round)
        }
        drawLine(hand, c, tip(305.0, r * 0.45f), stroke, StrokeCap.Round)
        drawLine(hand, c, tip(60.0, r * 0.66f), stroke * 0.8f, StrokeCap.Round)
        drawCircle(accent, radius = stroke * 0.9f, center = c)
    }
}
