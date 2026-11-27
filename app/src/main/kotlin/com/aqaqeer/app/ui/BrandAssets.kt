package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ContextThemeWrapper
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.aqaqeer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/** Bounded sampled decodes of the selected wordmark are shared by the in-app emblems. */
internal object BrandAssets {
    private val samples = android.util.LruCache<Int, Bitmap>(3)
    @Synchronized fun bitmap(resources: Resources, sample: Int): Bitmap = samples.get(sample) ?:
        requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_logo,
            BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }))
            .also { samples.put(sample, it) }
}

/** Reuse unchanged launcher pixels without retaining a full-size bitmap under memory pressure. */
internal object LauncherArtwork {
    @Volatile private var decoded = WeakReference<ImageBitmap>(null)
    fun cached(): ImageBitmap? = decoded.get()
    suspend fun load(resources: Resources): ImageBitmap = withContext(Dispatchers.IO) {
        synchronized(this@LauncherArtwork) {
            decoded.get() ?: requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_launcher,
                BitmapFactory.Options().apply { inScaled = false })).asImageBitmap().also { decoded = WeakReference(it) }
        }
    }
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
    ThemedIllustration(R.drawable.empty_state_capsule, null, modifier, alpha = Alpha.ghost)
}

/** Each palette has its own resource theme, which Compose uses as part of its vector cache key. */
internal fun illustrationContext(context: Context, dark: Boolean): Context = ContextThemeWrapper(
    context, if (dark) R.style.IllustrationDark else R.style.IllustrationLight,
)

@Composable internal fun ThemedIllustration(@DrawableRes resource: Int, description: String?, modifier: Modifier, alpha: Float = 1f) {
    val context = LocalIllustrationContext.current ?: run {
        val base = LocalContext.current
        val dark = isSystemInDarkTheme()
        remember(base, dark) { illustrationContext(base, dark) }
    }
    CompositionLocalProvider(LocalContext provides context) {
        Image(painterResource(resource), contentDescription = description, modifier = modifier, alpha = alpha)
    }
}
