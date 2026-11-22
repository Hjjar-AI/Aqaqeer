package com.aqaqeer.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import com.aqaqeer.app.data.*

@Composable internal fun Modifier.sectionSwipe(canSwipe: Boolean, active: List<Section>, selected: Section?, selectSection: (String) -> Unit): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val swipeThreshold = with(LocalDensity.current) { LayoutTokens.swipeDistance.toPx() }
    val drawerEdge = with(LocalDensity.current) { LayoutTokens.drawerEdge.toPx() }
    return if (!canSwipe) this else pointerInput(active.map { it.id }, selected?.id, rtl, swipeThreshold, drawerEdge) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val fromDrawerEdge = if (rtl) down.position.x >= size.width - drawerEdge else down.position.x <= drawerEdge
            if (fromDrawerEdge) return@awaitEachGesture
            var distance = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                distance += overSlop
                change.consume()
            } ?: return@awaitEachGesture
            val completed = horizontalDrag(drag.id) { change ->
                distance += change.position.x - change.previousPosition.x
                change.consume()
            }
            if (completed && kotlin.math.abs(distance) >= swipeThreshold) {
                val next = active.indexOf(selected) + if ((distance > 0) == rtl) 1 else -1
                active.getOrNull(next)?.let { selectSection(it.id) }
            }
        }
    }
}
