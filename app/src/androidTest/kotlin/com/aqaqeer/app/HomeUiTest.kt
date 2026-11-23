package com.aqaqeer.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.aqaqeer.app.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

@dagger.hilt.android.testing.HiltAndroidTest
class HomeUiTest {
    @get:Rule(order = 0) val hilt = dagger.hilt.android.testing.HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val preferences get() = DisplayPreferences(compose.activity.applicationContext)
    private lateinit var original: HomePreferences
    @Before fun start() {
        if (BuildConfig.IS_DEMO) { com.aqaqeer.app.admin.DemoSession.reset(); com.aqaqeer.app.admin.DemoSession.setAdmin(false) }
        original = runBlocking { preferences.home.first() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun reset(): Unit = runBlocking {
        if (BuildConfig.IS_DEMO) { com.aqaqeer.app.admin.DemoSession.reset(); com.aqaqeer.app.admin.DemoSession.setAdmin(false) }
        if (::original.isInitialized) {
            preferences.setSort(original.sort); preferences.setLayout(original.layout); preferences.setNames(original.names)
        }
    }
    private fun card(medication: Medication) = "med_${medication.nameEn ?: medication.id}"
    private fun show(medication: Medication) {
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag(card(medication)))
        compose.onNodeWithTag(card(medication)).assertIsDisplayed()
    }
    private fun choosePreference(tag: String, saved: (HomePreferences) -> Boolean) {
        val row = tag.substringBefore('_') + "_preference"
        compose.onNodeWithTag("settings").performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).performClick()
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(15_000) { saved(runBlocking { preferences.home.first() }) }
    }
    @Test fun searchIsReachableDoesNotAutofocusAndClearRestoresBrowsing() {
        org.junit.Assume.assumeTrue(BuildConfig.IS_DEMO)
        val catalog = BuildSeed.catalog(); val target = catalog.medications.single { it.genericEn == "Sertraline" }
        val other = catalog.medications.single { it.genericEn == "Fluoxetine" }
        compose.onNodeWithTag("search").assertIsDisplayed().assertIsNotFocused().performTextInput("SERTRALINE")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(card(target)).fetchSemanticsNodes().isNotEmpty() }
        show(target); compose.onNodeWithTag(card(other)).assertDoesNotExist()
        compose.onNodeWithTag("clear_search").performClick()
        show(other); compose.onNodeWithTag("search").assertIsDisplayed()
    }
    @Test fun adminFilterSheetOffersOnlyThreeStatesAndAppliesOrWithinStatuses() {
        org.junit.Assume.assumeTrue(BuildConfig.IS_DEMO)
        compose.runOnIdle { com.aqaqeer.app.admin.DemoSession.setAdmin(true) }
        val catalog = BuildSeed.catalog(); val section = catalog.sections.first().id
        compose.onNodeWithTag("section_$section").performClick()
        compose.onNodeWithContentDescription("الفلاتر", substring = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("filter_OUT").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("filter_UNKNOWN").assertDoesNotExist()
        compose.onNodeWithTag("filter_OUT").performScrollTo().performClick()
        compose.onNodeWithTag("filter_LOW").performScrollTo().performClick()
        compose.onNodeWithText("عرض النتائج").performClick()
        val expected = catalog.medications.filter { catalog.status(it.id, section) in setOf(AvailabilityStatus.OUT, AvailabilityStatus.LOW) }
        org.junit.Assert.assertTrue(expected.isNotEmpty())
        expected.take(3).forEach(::show)
        val excluded = catalog.medications.first { catalog.status(it.id, section) == AvailabilityStatus.AVAILABLE }
        compose.onNodeWithTag(card(excluded)).assertDoesNotExist()
        compose.onNodeWithContentDescription("الفلاتر", substring = true).performClick()
        compose.onNodeWithTag("clear_filters").performScrollTo().performClick()
        compose.onNodeWithText("عرض النتائج").performClick()
        show(excluded)
    }
    @Test fun savedSortLayoutAndNamesSurviveRecreationAndNewPreferenceReader() {
        compose.onNodeWithTag("home_menu").performClick(); compose.onNodeWithText(uiText(R.string.navigation_settings_title)).performClick()
        choosePreference("sort_AVAILABILITY") { it.sort == HomeSort.AVAILABILITY }
        choosePreference("layout_EXPANDED") { it.layout == CardLayout.EXPANDED }
        choosePreference("names_ARABIC") { it.names == NameDisplay.ARABIC }
        val expected = original.copy(sort = HomeSort.AVAILABILITY, layout = CardLayout.EXPANDED, names = NameDisplay.ARABIC)
        assertEquals(expected, runBlocking { preferences.home.first() })
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(expected, runBlocking { DisplayPreferences(compose.activity.applicationContext).home.first() })
        compose.onNodeWithTag("back").performClick()
        if (BuildConfig.IS_DEMO) {
            val medication = BuildSeed.catalog().medications.single { it.genericEn == "Sertraline" }
            show(medication)
            compose.onNodeWithTag(card(medication)).assertTextContains("سيرترالين", substring = true)
        }
    }
}
