package com.aqaqeer.app.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

internal enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }
internal enum class WindowHeightClass { COMPACT, MEDIUM, EXPANDED }
internal enum class FontScaleClass { STANDARD, LARGE }
internal data class LayoutMetrics(val width: Dp, val height: Dp, val fontScale: Float) {
    val widthClass = when {
        width < LayoutTokens.mediumWidth -> WindowWidthClass.COMPACT
        width < LayoutTokens.expandedWidth -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }
    val heightClass = when {
        height < LayoutTokens.compactHeight -> WindowHeightClass.COMPACT
        height < LayoutTokens.welcomeCompactHeight -> WindowHeightClass.MEDIUM
        else -> WindowHeightClass.EXPANDED
    }
    val fontScaleClass = if (fontScale > LayoutTokens.largeFontScale) FontScaleClass.LARGE else FontScaleClass.STANDARD
    fun compact(availableHeight: Dp = height, threshold: Dp = LayoutTokens.compactHeight): Boolean =
        availableHeight < threshold || fontScaleClass == FontScaleClass.LARGE
}
internal val LocalLayoutMetrics = staticCompositionLocalOf { LayoutMetrics(Dp.Infinity, Dp.Infinity, 1f) }

@Composable internal fun ProvideLayoutMetrics(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale = LocalDensity.current.fontScale
        val metrics = remember(maxWidth, maxHeight, fontScale) { LayoutMetrics(maxWidth, maxHeight, fontScale) }
        CompositionLocalProvider(LocalLayoutMetrics provides metrics, content = content)
    }
}
