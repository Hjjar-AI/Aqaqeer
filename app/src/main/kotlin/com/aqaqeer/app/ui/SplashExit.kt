package com.aqaqeer.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.splashscreen.SplashScreenViewProvider
import com.aqaqeer.app.R

/** A native-splash exit transition; never holds startup for a timer or a network request. */
internal fun animateSplashExit(provider: SplashScreenViewProvider, decor: ViewGroup, background: Int): Animator? {
    if (!ValueAnimator.areAnimatorsEnabled()) {
        provider.remove()
        return null
    }
    val density = decor.resources.displayMetrics.density
    val width = decor.width
    val height = decor.height
    if (width == 0 || height == 0) {
        provider.remove()
        return null
    }
    val layer = FrameLayout(decor.context).apply {
        setBackgroundColor(background)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        layout(0, 0, width, height)
    }
    // Outside the system icon mask, the complete bitmap can grow without clipping the lettering.
    val edge = minOf(width * 0.86f, height * 0.62f, 420f * density).toInt()
    val icon = provider.iconView
    val iconLocation = IntArray(2).also(icon::getLocationOnScreen)
    val decorLocation = IntArray(2).also(decor::getLocationOnScreen)
    // Match the native 17%-inset bitmap and its position exactly before enlarging it.
    val initialScale = if (icon.width > 0) (icon.width * 0.66f / edge).coerceAtMost(1f) else 1f
    val emblem = ImageView(decor.context).apply {
        setImageBitmap(BrandAssets.bitmap(resources, 2))
        scaleType = ImageView.ScaleType.FIT_CENTER
        scaleX = initialScale
        scaleY = initialScale
        translationX = iconLocation[0] - decorLocation[0] + icon.width / 2f - width / 2f
        translationY = iconLocation[1] - decorLocation[1] + icon.height / 2f - height / 2f
    }
    layer.addView(emblem, FrameLayout.LayoutParams(edge, edge, Gravity.CENTER))
    layer.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    layer.layout(0, 0, width, height)
    // An overlay cannot consume touches or become a second navigation destination.
    decor.overlay.add(layer)
    provider.remove()
    return AnimatorSet().apply {
        val fade = ObjectAnimator.ofFloat(layer, View.ALPHA, 1f, 0f).apply {
            duration = 180
            interpolator = PathInterpolator(0.22f, 0f, 0.2f, 1f)
        }
        val growth = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(emblem, View.SCALE_X, initialScale, 1f),
                ObjectAnimator.ofFloat(emblem, View.SCALE_Y, initialScale, 1f),
            )
            duration = 180
            interpolator = PathInterpolator(0.22f, 0f, 0.2f, 1f)
        }
        playTogether(growth, fade)
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { decor.overlay.remove(layer) }
        })
        start()
    }
}
