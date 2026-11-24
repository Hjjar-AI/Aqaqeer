package com.aqaqeer.app.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

@Composable
internal fun LaunchSplash(waitingForStartup: Boolean, artwork: ImageBitmap, onDismiss: () -> Unit) {
    val animate = ValueAnimator.areAnimatorsEnabled()
    val scale = remember { Animatable(1f) }
    val opacity = remember { Animatable(1f) }
    var hasDrawn by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(waitingForStartup, animate) {
        if (waitingForStartup) {
            if (animate) {
                val leg = tween<Float>(durationMillis = 720, easing = FastOutSlowInEasing)
                while (isActive) {
                    scale.animateTo(1.65f, leg)
                    scale.animateTo(1f, leg)
                }
            }
        } else {
            if (animate && hasDrawn) opacity.animateTo(0f, tween(durationMillis = 100, easing = LinearEasing))
            dismiss()
        }
    }

    if (waitingForStartup || (animate && hasDrawn)) {
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { alpha = opacity.value }
                .background(MaterialTheme.colorScheme.background)
                .drawWithContent {
                    drawContent()
                    if (!hasDrawn) hasDrawn = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                modifier = Modifier.size(Size.launchArtwork).graphicsLayer {
                    scaleX = if (animate) scale.value else 1f
                    scaleY = scaleX
                },
            )
        }
    }
}
