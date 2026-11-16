package com.aqaqeer.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.splashscreen.SplashScreenViewProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The exit callback can arrive before the decor has usable bounds. Wait for layout, not a timer. */
internal suspend fun awaitSplashLayout(decor: ViewGroup) {
    if (decor.width > 0 && decor.height > 0) return
    suspendCancellableCoroutine<Unit> { continuation ->
        val observer = decor.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (decor.width > 0 && decor.height > 0) {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    if (continuation.isActive) continuation.resume(Unit)
                }
                return true
            }
        }
        observer.addOnPreDrawListener(listener)
        continuation.invokeOnCancellation {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
        decor.requestLayout()
    }
}

/** Counter-rotating internal ring lines, followed by a fade into the ready application. */
internal fun animateSplashExit(provider: SplashScreenViewProvider, decor: ViewGroup, background: Int, bitmap: Bitmap): Animator? {
    if (!ValueAnimator.areAnimatorsEnabled() || decor.width == 0 || decor.height == 0) {
        provider.remove()
        return null
    }
    val width = decor.width
    val height = decor.height
    val density = decor.resources.displayMetrics.density
    val icon = provider.iconView
    val iconLocation = IntArray(2).also(icon::getLocationOnScreen)
    val decorLocation = IntArray(2).also(decor::getLocationOnScreen)
    // Keep the full logo stationary at the native splash size and center, without its icon mask.
    val nativeEdge = if (icon.width > 0) icon.width * 0.66f else 190f * density
    val edge = minOf(nativeEdge, width * 0.86f, height * 0.62f, 420f * density).toInt().coerceAtLeast(1)
    val centerX = if (icon.width > 0) iconLocation[0] - decorLocation[0] + icon.width / 2 else width / 2
    val centerY = if (icon.height > 0) iconLocation[1] - decorLocation[1] + icon.height / 2 else height / 2
    val layer = FrameLayout(decor.context).apply {
        setBackgroundColor(background)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
    }
    val emblem = SplashEmblemView(decor.context, bitmap)
    layer.addView(emblem, FrameLayout.LayoutParams(edge, edge, Gravity.TOP or Gravity.LEFT).apply {
        leftMargin = centerX - edge / 2
        topMargin = centerY - edge / 2
    })
    // Use an ordinary attached child so drawing/invalidation participates in the normal view tree.
    decor.addView(layer, ViewGroup.LayoutParams(width, height))
    layer.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    layer.layout(0, 0, width, height)
    provider.remove()
    return AnimatorSet().apply {
        val rings = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 640
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { emblem.ringDegrees = it.animatedValue as Float }
        }
        val fade = ObjectAnimator.ofFloat(layer, View.ALPHA, 1f, 0f).apply {
            duration = 160
            startDelay = 560
            interpolator = PathInterpolator(0.22f, 0f, 0.2f, 1f)
        }
        playTogether(rings, fade)
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { decor.removeView(layer) }
        })
        start()
    }
}
