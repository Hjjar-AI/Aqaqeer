package com.aqaqeer.app

import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.ui.AqaqeerTheme
import com.aqaqeer.app.ui.LaunchSplash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LaunchSplashUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readyDestinationTakesOverDuringSplashWithCurrentAnimationSetting() {
        val animated = ValueAnimator.areAnimatorsEnabled()
        var waiting by mutableStateOf(true)
        var dismissed by mutableStateOf(false)
        val marker = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }.asImageBitmap()
        val host = "splash-timing"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AqaqeerTheme(ThemeChoice.LIGHT) {
                Box(Modifier.fillMaxSize().testTag(host)) {
                    if (!waiting) Text("Ready destination")
                    if (!dismissed) LaunchSplash(waiting, marker) { dismissed = true }
                }
            }
        }

        fun markerHeight(): Int {
            val pixels = compose.onNodeWithTag(host).captureToImage().toPixelMap()
            return (0 until pixels.height).count { y -> pixels[pixels.width / 2, y] == Color.Magenta }
        }

        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val resting = markerHeight()
        assertTrue(resting > 0)

        if (animated) {
            compose.mainClock.advanceTimeBy(360)
            val rising = markerHeight().toFloat() / resting
            assertTrue("The first half-leg was still rising, ratio=$rising", rising in 1.30f..1.58f)

            compose.mainClock.advanceTimeBy(360)
            val turnaround = markerHeight().toFloat() / resting
            assertTrue("The logo reached the 1.65× turnaround, ratio=$turnaround", turnaround in 1.60f..1.69f)

            compose.mainClock.advanceTimeBy(720)
            val returned = markerHeight().toFloat() / resting
            assertTrue("The logo returned after the full pulse, ratio=$returned", returned in 0.98f..1.06f)
        } else {
            compose.mainClock.advanceTimeBy(360)
            assertEquals(resting, markerHeight())
        }

        compose.mainClock.advanceTimeBy(208)
        compose.runOnIdle { waiting = false }
        compose.mainClock.advanceTimeBy(if (animated) 160 else 32)
        compose.runOnIdle { assertTrue(dismissed) }
        compose.onNodeWithText("Ready destination").assertIsDisplayed()
        assertEquals(0, markerHeight())
    }
}
