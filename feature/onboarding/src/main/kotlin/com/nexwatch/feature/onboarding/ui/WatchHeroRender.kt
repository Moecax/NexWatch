package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.theme.ElectricBlue
import com.nexwatch.core.designsystem.theme.Navy
import com.nexwatch.core.designsystem.theme.SkyBlue

/**
 * design-prompt.md: "a generic round smartwatch ... no brand names, no logos". Drawn with
 * Canvas rather than a bitmap asset (CLAUDE.md 9.4: the service graph loads no bitmaps —
 * shared code between onboarding and pairing-progress, so keep this vector-based).
 */
@Composable
fun WatchHeroRender(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(200.dp)) {
        val bezelStroke = size.minDimension * 0.06f
        drawCircle(
            brush = Brush.linearGradient(listOf(SkyBlue, ElectricBlue)),
            radius = size.minDimension / 2f - bezelStroke / 2f,
            style = Stroke(width = bezelStroke),
        )
        drawCircle(
            color = Navy,
            radius = size.minDimension / 2f - bezelStroke,
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}
