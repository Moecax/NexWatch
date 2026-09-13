package com.nexwatch.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** design-prompt.md: cards use 20–24dp corner radius; controls use a tighter radius. */
object WatchShapes {
    val card: Shape = RoundedCornerShape(22.dp)
    val control: Shape = RoundedCornerShape(14.dp)
    val pill: Shape = RoundedCornerShape(50)
}
