package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.aqaqeer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The original artwork stays intact. Bounded sampled decodes are shared by Compose and splash. */
internal object BrandAssets {
    private val samples = android.util.LruCache<Int, Bitmap>(3)
    @Synchronized fun bitmap(resources: Resources, sample: Int): Bitmap = samples.get(sample) ?:
        requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_logo,
            BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }))
            .also { samples.put(sample, it) }
}

@Composable internal fun BrandEmblem(modifier: Modifier, description: String? = uiText(R.string.ui_brand_assets_001)) {
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

/** Draw the original capsule's source pixels, faded; generated reinterpretations are not used. */
@Composable internal fun EmptyCapsule(modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val bitmap by produceState<ImageBitmap?>(null, resources) {
        value = withContext(Dispatchers.IO) { BrandAssets.bitmap(resources, 4).asImageBitmap() }
    }
    Canvas(modifier) {
        bitmap?.let { image ->
            val width = size.width.toInt()
            // Measured original capsule alpha bounds: x=208..377, y=815..883 in the 1254px master.
            // Include transparent padding and round outward so sampled decoding never clips its edge.
            val source = IntSize(kotlin.math.ceil(image.width * 180.0 / 1254).toInt(),
                kotlin.math.ceil(image.height * 80.0 / 1254).toInt())
            val height = (width.toFloat() * source.height / source.width).toInt()
            drawImage(image, srcOffset = IntOffset((image.width * 204.0 / 1254).toInt(), (image.height * 811.0 / 1254).toInt()),
                srcSize = source,
                dstOffset = IntOffset(0, ((size.height - height) / 2).toInt()), dstSize = IntSize(width, height),
                alpha = 0.30f, filterQuality = FilterQuality.Medium)
        }
    }
}
