package com.aqaqeer.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import com.aqaqeer.app.data.*

internal fun Modifier.statusEdge(status: AvailabilityStatus, edge: Color, unavailableStripe: Color, rtl: Boolean): Modifier = drawWithCache {

    val stripes = Path().apply {
        val spacing = Size.stripeSpacing.toPx()
        var x = -size.height
        while (x < size.width) {
            moveTo(x, 0f); lineTo(x + size.height, size.height)
            x += spacing
        }
    }
    val stripeStroke = Stroke(AppStroke.emphasis.toPx())
    val stroke = AppStroke.accent.toPx()
    val x = if (rtl) size.width - stroke / 2 else stroke / 2
    onDrawBehind {
        if (status == AvailabilityStatus.OUT) drawPath(stripes, unavailableStripe, style = stripeStroke)
        drawLine(edge, Offset(x, 0f), Offset(x, size.height), stroke)
    }
}
