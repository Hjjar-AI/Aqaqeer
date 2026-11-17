package com.aqaqeer.app.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aqaqeer.app.R

/** Shares a bounded decode of the selected launcher artwork with the splash exit. */
internal object SplashArtwork {
    private var cached: Bitmap? = null
    @Synchronized fun bitmap(resources: Resources): Bitmap = cached ?: requireNotNull(
        BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_launcher,
            BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
    ).also { cached = it }
}
