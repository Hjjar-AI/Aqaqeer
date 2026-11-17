package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.aqaqeer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The original artwork stays intact. Bounded sampled decodes are shared by the in-app emblems. */
internal object BrandAssets {
    private val samples = android.util.LruCache<Int, Bitmap>(3)
    @Synchronized fun bitmap(resources: Resources, sample: Int): Bitmap = samples.get(sample) ?:
        requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_logo,
            BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }))
            .also { samples.put(sample, it) }
}

@Composable internal fun BrandEmblem(modifier: Modifier, description: String? = uiText(R.string.navigation_brand_logo_description)) {
    val resources = LocalContext.current.resources
    BoxWithConstraints(modifier) {
        val pixels = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).toPx() }.coerceAtLeast(1f)
        val sample = when { pixels <= 150 -> 8; pixels <= 300 -> 4; else -> 2 }
        val bitmap by produceState<ImageBitmap?>(null, resources, sample) {
            value = withContext(Dispatchers.IO) { BrandAssets.bitmap(resources, sample).asImageBitmap() }
        }
        bitmap?.let { Image(it, description, filterQuality = FilterQuality.Medium) }
    }
}

/** A dedicated decorative illustration keeps empty states independent of logo crop geometry. */
@Composable internal fun EmptyCapsule(modifier: Modifier = Modifier) {
    Image(androidx.compose.ui.res.painterResource(R.drawable.empty_state_capsule),
        contentDescription = null, modifier = modifier, alpha = Alpha.ghost)
}
