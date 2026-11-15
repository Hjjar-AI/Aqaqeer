package com.aqaqeer.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.aqaqeer.app.data.DisplayPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@dagger.hilt.android.testing.HiltAndroidTest
class BottomPlacementSettingsUiTest {
    @get:Rule(order = 0) val hilt = dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun independentSwitchesPersistAcrossActivityRecreationAndNewPreferenceReader() {
        org.junit.Assume.assumeTrue(BuildConfig.IS_DEMO)
        val preferences = DisplayPreferences(compose.activity.applicationContext)
        val original = runBlocking { preferences.home.first() }
        try {
            runBlocking { preferences.setSectionsAtBottom(true); preferences.setSearchAtBottom(true) }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home_menu").performClick()
            compose.onNodeWithText(uiText(R.string.ui_screen_titles_012)).performScrollTo().performClick()
            compose.onNodeWithTag("sections_at_bottom").performScrollTo().assertIsOn().performClick()
            compose.waitUntil(10000) { !runBlocking { preferences.home.first() }.sectionsAtBottom }
            compose.onNodeWithTag("search_at_bottom").performScrollTo().assertIsOn().performClick()
            compose.waitUntil(10000) { !runBlocking { preferences.home.first() }.searchAtBottom }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("sections_at_bottom").performScrollTo().assertIsOff()
            compose.onNodeWithTag("search_at_bottom").performScrollTo().assertIsOff()
            compose.onNodeWithTag("sections_at_bottom").performScrollTo().performClick()
            compose.waitUntil(10000) { runBlocking { preferences.home.first() }.sectionsAtBottom }
            val restored = runBlocking { DisplayPreferences(compose.activity.applicationContext).home.first() }
            assertTrue(restored.sectionsAtBottom); assertFalse(restored.searchAtBottom)
            assertEquals(original.copy(sectionsAtBottom = true, searchAtBottom = false), restored)
        } finally {
            runBlocking {
                preferences.setSectionsAtBottom(original.sectionsAtBottom)
                preferences.setSearchAtBottom(original.searchAtBottom)
            }
        }
    }
}
