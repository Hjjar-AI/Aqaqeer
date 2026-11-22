package com.aqaqeer.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun LaunchPulse() {
    val resources = LocalContext.current.resources
    val progress = remember { Animatable(0f) }
    var artwork by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        val bitmap = withTimeoutOrNull(120L) {
            withContext(Dispatchers.IO) { SplashArtwork.bitmap(resources) }
        } ?: return@LaunchedEffect
        artwork = bitmap.asImageBitmap()
        progress.animateTo(1f, tween(durationMillis = 260, easing = LinearEasing))
        artwork = null
    }
    val bitmap = artwork ?: return
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val edge = minOf(176.dp, maxWidth * 0.56f, maxHeight * 0.48f)
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(edge).graphicsLayer {
                val returning = progress.value >= 0.5f
                val phase = if (returning) (progress.value - 0.5f) * 2f else progress.value * 2f
                val eased = FastOutSlowInEasing.transform(phase)
                val expansion = if (returning) 1f - eased else eased
                scaleX = 1f + 0.55f * expansion
                scaleY = scaleX
                alpha = if (returning) 1f - phase * phase else 1f
            },
        )
    }
}
