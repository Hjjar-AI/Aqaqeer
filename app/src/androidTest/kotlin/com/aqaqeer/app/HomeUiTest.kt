package com.aqaqeer.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.aqaqeer.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

@dagger.hilt.android.testing.HiltAndroidTest
class HomeUiTest {
    private fun homeNode(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    @get:Rule(order = 0) val hilt = dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = DisplayPreferences(compose.activity.applicationContext)
    private fun defaults() = runBlocking {
        preferences.setSort(HomeSort.ARABIC_NAME)
        preferences.setLayout(CardLayout.COMPACT)
        preferences.setNames(NameDisplay.BOTH)
    }
    @Before fun start() {
        defaults()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun reset() { defaults() }

    private fun choosePreference(tag: String, saved: (HomePreferences) -> Boolean) {
        val row = tag.substringBefore('_') + "_preference"
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).performScrollTo().performClick()
        // Scroll the row itself fully into view, then await its asynchronous persistence before
        // scrolling to another card. A matched lazy item can still have a clipped clickable row.
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
        // Keep advancing Compose's clock while ViewModel callbacks and state collectors run.
        // Blocking on Flow.first outside the test clock can leave frame-dispatched work queued.
        var latest = runBlocking { preferences.home.first() }
        try {
            compose.waitUntil(15_000) {
                latest = runBlocking { preferences.home.first() }
                saved(latest)
            }
        } catch (error: Exception) {
            throw AssertionError("Preference $tag was not saved; last value: $latest", error)
        }
    }

    @Test fun searchIsReachableDoesNotAutofocusAndClearRestoresBrowsing() {
        if (!BuildConfig.IS_DEMO) return
        homeNode("search").assertIsDisplayed().assertIsNotFocused()
        homeNode("status_filters").assertIsDisplayed()
        homeNode("category_filters").assertIsDisplayed()
        homeNode("search").performTextInput("SALINE")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("med_Saline nasal spray").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("med_Paracetamol").assertDoesNotExist()
        compose.onNodeWithTag("clear_search").performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Paracetamol"))
        compose.onNodeWithTag("med_Paracetamol").assertIsDisplayed()
        homeNode("search").assertIsDisplayed()
        homeNode("category_filters").assertIsDisplayed()
    }

    @Test fun unknownFilterIncludesMissingPairsAndOrSelectionRemainsVisibleWhileScrolling() {
        if (!BuildConfig.IS_DEMO) return
        homeNode("filter_UNKNOWN").performScrollTo().performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Saline nasal spray"))
        compose.onNodeWithTag("med_Saline nasal spray").assertIsDisplayed()
        compose.onNodeWithTag("med_Paracetamol").assertDoesNotExist()
        homeNode("filter_OUT").performScrollTo().performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Cetirizine"))
        compose.onNodeWithTag("med_Cetirizine").assertIsDisplayed()
        homeNode("category_${CatalogSeeds.UNCATEGORIZED_ID}").performClick()
        homeNode("search").assertIsDisplayed()
        homeNode("status_filters").assertIsDisplayed()
        homeNode("category_filters").assertIsDisplayed()
        homeNode("clear_filters").performScrollTo().performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Paracetamol"))
        compose.onNodeWithTag("med_Paracetamol").assertIsDisplayed()
    }

    @Test fun savedSortLayoutAndNamesSurviveRecreationAndNewPreferenceReader() {
        compose.onNodeWithTag("home_menu").performClick()
        compose.onNodeWithTag("settings_menu").performClick()
        choosePreference("sort_AVAILABILITY") { it.sort == HomeSort.AVAILABILITY }
        choosePreference("layout_EXPANDED") { it.layout == CardLayout.EXPANDED }
        choosePreference("names_ARABIC") { it.names == NameDisplay.ARABIC }
        val expected = HomePreferences(HomeSort.AVAILABILITY, CardLayout.EXPANDED, NameDisplay.ARABIC)
        runBlocking { withTimeout(5_000) { preferences.home.first { it == expected } } }
        compose.activityRule.scenario.recreate()
        assertEquals(expected, runBlocking { preferences.home.first() })
        compose.onNodeWithTag("back").performClick()
        if (BuildConfig.IS_DEMO) {
            compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Saline nasal spray"))
            compose.onNodeWithTag("med_Saline nasal spray").assertTextContains("Saline nasal spray", substring = true)
            compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Paracetamol"))
            compose.onNodeWithTag("med_Paracetamol").assertIsDisplayed()
            compose.onNodeWithTag("med_Paracetamol").assertTextContains("١٣٬٠٠٠ ل.س", substring = true)
        }
    }
}
