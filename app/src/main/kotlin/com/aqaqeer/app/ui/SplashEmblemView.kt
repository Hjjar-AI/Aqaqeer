package com.aqaqeer.app.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.aqaqeer.app.R

/** Matches the selected 1254px gold/teal/navy master (SHA-256 prefix 5e6f757a2323d8c6). */
internal object SplashArtwork {
    private var cached: Bitmap? = null
    @Synchronized fun bitmap(resources: Resources): Bitmap = cached ?: requireNotNull(
        BitmapFactory.decodeResource(resources, R.drawable.aqaqeer_launcher,
            BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
    ).also { cached = it }
}

/** Moves only the source pixels inside the chemical rings; lettering and gold contours stay fixed. */
internal class SplashEmblemView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF(0f, 0f, 1254f, 1254f)
    // Measured convex hulls around the teal strokes, expanded 8% into their navy margins.
    // These masks contain all teal stroke pixels and no gold fill in the selected master.
    private val rings = listOf(
        Ring(475f, 621f, 1f, floatArrayOf(
            380.5f, 573.8f, 382.7f, 570.6f, 383.8f, 569.5f, 474.5f, 515.5f,
            476.7f, 514.4f, 482.1f, 514.4f, 484.2f, 515.5f, 500.4f, 525.2f,
            554.4f, 558.7f, 557.7f, 560.8f, 562.0f, 565.2f, 563.1f, 567.3f,
            563.1f, 673.2f, 562.0f, 676.4f, 558.7f, 679.6f, 520.9f, 702.3f,
            486.4f, 722.8f, 479.9f, 726.1f, 474.5f, 726.1f, 472.3f, 725.0f,
            383.8f, 672.1f, 382.7f, 671.0f, 380.5f, 666.7f,
        )),
        Ring(845f, 640f, -1f, floatArrayOf(
            769.7f, 607.5f, 770.8f, 605.3f, 774.1f, 602.1f, 844.3f, 557.8f,
            846.4f, 556.7f, 849.7f, 555.6f, 852.9f, 556.7f, 857.2f, 558.9f,
            870.2f, 566.4f, 886.4f, 576.2f, 905.8f, 588.0f, 914.5f, 593.4f,
            915.5f, 594.5f, 917.7f, 598.8f, 917.7f, 679.8f, 916.6f, 683.1f,
            915.5f, 685.2f, 914.5f, 686.3f, 856.1f, 720.9f, 849.7f, 724.1f,
            842.1f, 724.1f, 773.0f, 686.3f, 771.9f, 685.2f, 769.7f, 680.9f,
        )),
    )
    var ringDegrees: Float = 0f
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val original = canvas.save()
        canvas.scale(width / 1254f, height / 1254f)
        val fixed = canvas.save()
        rings.forEach { canvas.clipOutPath(it.mask) }
        canvas.drawBitmap(bitmap, null, bounds, paint)
        canvas.restoreToCount(fixed)
        rings.forEach { ring ->
            val rotated = canvas.save()
            canvas.clipPath(ring.mask)
            canvas.rotate(ringDegrees * ring.direction, ring.x, ring.y)
            // Clip source pixels too, so rotating a ring cannot import neighboring gold lettering.
            canvas.clipPath(ring.mask)
            canvas.drawBitmap(bitmap, null, bounds, paint)
            canvas.restoreToCount(rotated)
        }
        canvas.restoreToCount(original)
    }

    private class Ring(val x: Float, val y: Float, val direction: Float, points: FloatArray) {
        val mask = Path().apply {
            moveTo(points[0], points[1])
            var index = 2
            while (index < points.size) {
                lineTo(points[index], points[index + 1]); index += 2
            }
            close()
        }
    }
}
