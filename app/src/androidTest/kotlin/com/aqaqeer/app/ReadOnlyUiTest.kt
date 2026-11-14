package com.aqaqeer.app

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import com.aqaqeer.app.data.DisplayPreferences
import com.aqaqeer.app.data.BuildSeed
import com.aqaqeer.app.data.ThemeChoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@dagger.hilt.android.testing.HiltAndroidTest
class ReadOnlyUiTest {
    @get:Rule(order = 0) val hilt = dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule(order = 2) val captures = CiFailureCaptureRule()
    private fun awaitHome() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun settings() {
        compose.onNodeWithTag("home_menu").performClick()
        compose.onNodeWithTag("settings_menu").performClick()
        compose.onNodeWithTag("settings").assertIsDisplayed()
    }

    @Test fun settingsAboutAndEmailAreReachableWithoutSendingMail() {
        awaitHome(); settings()
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("open_about"))
        compose.onNodeWithTag("open_about").performClick()
        compose.onNodeWithTag("about").performScrollToNode(hasTestTag("developer_email"))
        compose.onNodeWithText("محمد علي حجار").assertIsDisplayed()
        compose.onNodeWithTag("developer_email").assertIsDisplayed()
        Intents.init()
        try {
            val matcher = allOf(hasAction(Intent.ACTION_SENDTO), hasData("mailto:hjjarmhmmdali@gmail.com"))
            Intents.intending(matcher).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
            compose.onNodeWithTag("about").performScrollToNode(hasTestTag("email_contact"))
            compose.onNodeWithTag("email_contact").performClick()
            // Verify the intercepted intent directly; Espresso's view-root picker waits for
            // unrelated window focus on this Huawei after the cancelled activity stub.
            assertEquals(1, Intents.getIntents().count { matcher.matches(it) })
        } finally { Intents.release() }
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("settings").assertIsDisplayed()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("home").assertIsDisplayed()
    }

    @Test fun medicationDetailsRemainReadOnlyAndNavigateBack() {
        awaitHome()
        if (BuildConfig.IS_DEMO) {
            val medication = BuildSeed.catalog().medications.single { it.genericEn == "Paracetamol" }
            val cardTag = "med_${medication.nameEn ?: medication.id}"
            compose.onNodeWithTag("home").performScrollToNode(hasTestTag(cardTag))
            compose.onNodeWithTag(cardTag).performClick()
            compose.onNodeWithTag("medication_detail").assertIsDisplayed()
            compose.onAllNodesWithText("حفظ").assertCountEquals(0)
            compose.onNodeWithTag("back").performClick()
            compose.onNodeWithTag("home").assertIsDisplayed()
        } else {
            compose.onNodeWithText("لم يتم إعداد صيدلية على هذا الجهاز.").assertIsDisplayed()
            compose.onAllNodesWithTag("demo_notice").assertCountEquals(0)
        }
    }

    @Test fun themeAndAboutRouteSurviveActivityRecreation() {
        awaitHome(); settings()
        val prefs = DisplayPreferences(compose.activity.applicationContext)
        val originalTheme = runBlocking { prefs.theme.first() }
        try {
            compose.onNodeWithText("المظهر").performClick()
            compose.onNodeWithTag("theme_DARK").performScrollTo().performClick()
            runBlocking { withTimeout(5_000) { prefs.theme.first { it == ThemeChoice.DARK } } }
            compose.onNodeWithTag("settings").performScrollToNode(hasTestTag("open_about"))
            compose.onNodeWithTag("open_about").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("about").assertIsDisplayed()
            assertEquals(ThemeChoice.DARK, runBlocking { prefs.theme.first() })
        } finally { runBlocking { prefs.setTheme(originalTheme) } }
    }
}
