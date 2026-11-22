package com.aqaqeer.app.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aqaqeer.app.R

/** Caches a sampled decode of the launcher artwork for the app-owned launch pulse. */
internal object SplashArtwork {
    private var cached: Bitmap? = null
    @Synchronized fun bitmap(resources: Resources): Bitmap? = cached ?:
        BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_launcher,
            BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false }).also { cached = it }
}
