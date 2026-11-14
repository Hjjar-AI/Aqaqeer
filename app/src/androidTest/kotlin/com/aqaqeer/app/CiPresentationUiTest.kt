package com.aqaqeer.app

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.BuildSeed
import com.aqaqeer.app.data.DisplayPreferences
import com.aqaqeer.app.data.ThemeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Selected offline demo checks. Screenshots still require human visual/accessibility review. */
@dagger.hilt.android.testing.HiltAndroidTest
class CiPresentationUiTest {
    @get:Rule(order = 0) val hilt = dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 2) val captures = CiFailureCaptureRule()

    @Test fun arabicLargeTextThemeAndRecreationRemainUsable() {
        check(BuildConfig.IS_DEMO)
        val choice = ThemeChoice.valueOf(requireNotNull(InstrumentationRegistry.getArguments().getString("ciTheme")))
        check(choice in listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.AMOLED))
        val preferences = DisplayPreferences(compose.activity.applicationContext)
        val original = runBlocking { preferences.theme.first() }
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
            // Set only the non-secret local presentation preference; exercise restoration in a new activity.
            runBlocking { preferences.setTheme(choice) }
            compose.waitUntil(10_000) {
                runBlocking { preferences.theme.first() } == choice &&
                    compose.activity.getSharedPreferences("startup_presentation", 0).getString("theme", null) == choice.name
            }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                assertEquals("ar", compose.activity.resources.configuration.locales[0].language)
                assertEquals(View.LAYOUT_DIRECTION_RTL, compose.activity.resources.configuration.layoutDirection)
                assertTrue("Large font setting must actually be active", compose.activity.resources.configuration.fontScale >= 1.29f)
                val background = (compose.activity.window.decorView.background as ColorDrawable).color
                val brightness = (Color.red(background) + Color.green(background) + Color.blue(background)) / 3
                when (choice) {
                    ThemeChoice.LIGHT -> assertTrue("Light window background", brightness > 220)
                    ThemeChoice.DARK -> assertTrue("Dark window background", brightness < 80)
                    ThemeChoice.AMOLED -> assertEquals("AMOLED window background", Color.BLACK, background)
                    else -> error("Unexpected theme")
                }
            }
            assertEquals(choice, runBlocking { preferences.theme.first() })
            val medication = BuildSeed.catalog().medications.single { it.genericEn == "Paracetamol" }
            val cardTag = "med_${medication.nameEn ?: medication.id}"
            compose.onNodeWithTag("home").performScrollToNode(hasTestTag(cardTag))
            compose.onNodeWithTag(cardTag).assertIsDisplayed().performClick()
            compose.onNodeWithTag("medication_detail").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText("باراسيتامول", substring = true).fetchSemanticsNodes().isNotEmpty())
            compose.onAllNodesWithText("حفظ").assertCountEquals(0)
            captureCiScreen("${choice.name.lowercase()}-large-arabic-detail")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("medication_detail").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(choice, runBlocking { preferences.theme.first() })
            compose.onNodeWithTag("back").assertIsDisplayed().performClick()
            compose.onNodeWithTag("home").assertIsDisplayed()
            captureCiScreen("${choice.name.lowercase()}-large-arabic-home")
        } catch (failure: Throwable) {
            try { captureCiScreen("assertion-${choice.name.lowercase()}") }
            catch (capture: Throwable) { failure.addSuppressed(capture) }
            throw failure
        } finally {
            runBlocking { preferences.setTheme(original) }
        }
    }
}
