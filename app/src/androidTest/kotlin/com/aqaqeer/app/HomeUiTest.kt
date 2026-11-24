package com.aqaqeer.app

import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import com.aqaqeer.app.ui.CatalogViewModel
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
    private var fixturePharmacy: String? = null
    private var originalSection: String? = null
    private lateinit var viewModel: CatalogViewModel
    @Before fun start() {
        if (BuildConfig.IS_DEMO) { com.aqaqeer.app.admin.DemoSession.reset(); com.aqaqeer.app.admin.DemoSession.setAdmin(false) }
        original = runBlocking { preferences.home.first() }
        compose.runOnUiThread { viewModel = ViewModelProvider(compose.activity)[CatalogViewModel::class.java] }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun reset(): Unit = runBlocking {
        if (BuildConfig.IS_DEMO) { com.aqaqeer.app.admin.DemoSession.reset(); com.aqaqeer.app.admin.DemoSession.setAdmin(false) }
        if (::original.isInitialized) {
            preferences.setSort(original.sort); preferences.setLayout(original.layout); preferences.setNames(original.names)
        }
        fixturePharmacy?.let { pharmacy ->
            originalSection?.let { preferences.setSection(pharmacy, it) } ?: preferences.clearSection(pharmacy)
        }
    }
    private fun card(medication: Medication) = "med_${medication.nameEn ?: medication.id}"
    private fun show(medication: Medication) {
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag(card(medication)))
        compose.onNodeWithTag(card(medication)).assertIsDisplayed()
    }
    private fun choosePreference(matcher: SemanticsMatcher, saved: (HomePreferences) -> Boolean) {
        compose.onNodeWithTag("settings").performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
        compose.waitUntil(15_000) { saved(runBlocking { preferences.home.first() }) }
    }
    private fun selectStockedSection(catalog: CatalogSnapshot, vararg medications: Medication): String {
        val pharmacy = requireNotNull(catalog.pharmacy).pharmacyId
        fixturePharmacy = pharmacy
        originalSection = runBlocking { preferences.sections.first()[pharmacy] }
        val section = catalog.sections.first { section ->
            section.isActive && medications.all { catalog.status(it.id, section.id) != AvailabilityStatus.OUT }
        }.id
        compose.onNodeWithTag("section_$section").performClick()
        return section
    }
    private fun awaitRows(section: String, query: String, vararg medications: Medication) {
        compose.waitUntil(15_000) {
            val browse = viewModel.homeBrowse.value
            browse.sectionId == section && browse.query == query &&
                medications.all { medication -> browse.visible.any { it.id == medication.id } } &&
                compose.onNodeWithTag("home").fetchSemanticsNode().config
                    .getOrNull(SemanticsActions.IndexForKey)?.action?.let { index ->
                        medications.all { index(it.id) >= 0 }
                    } == true
        }
        compose.waitForIdle()
    }
    @Test fun searchIsReachableDoesNotAutofocusAndClearRestoresBrowsing() {
        org.junit.Assume.assumeTrue(BuildConfig.IS_DEMO)
        val catalog = BuildSeed.catalog(); val target = catalog.medications.single { it.genericEn == "Sertraline" }
        val other = catalog.medications.single { it.genericEn == "Fluoxetine" }
        val section = selectStockedSection(catalog, target, other)
        awaitRows(section, "", target, other)
        compose.onNodeWithTag("search").assertIsDisplayed().assertIsNotFocused().performTextInput("SERTRALINE")
        awaitRows(section, "SERTRALINE", target)
        show(target); compose.onNodeWithTag(card(other)).assertDoesNotExist()
        compose.onNodeWithTag("clear_search").performClick()
        awaitRows(section, "", target, other)
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
        val medication = if (BuildConfig.IS_DEMO) BuildSeed.catalog().medications.single { it.genericEn == "Sertraline" } else null
        val section = medication?.let { selectStockedSection(BuildSeed.catalog(), it) }
        compose.onNodeWithTag("home_menu").performClick(); compose.onNodeWithText(uiText(R.string.navigation_settings_title)).performClick()
        val displayGroup = hasText(uiText(R.string.review_list_display))
        compose.onNodeWithTag("settings").performScrollToNode(displayGroup)
        compose.onNode(displayGroup).performClick()
        choosePreference(hasTestTag("sort_AVAILABILITY")) { it.sort == HomeSort.AVAILABILITY }
        choosePreference(hasText(uiText(R.string.settings_card_expanded_label))) { it.layout == CardLayout.EXPANDED }
        choosePreference(hasTestTag("names_ARABIC")) { it.names == NameDisplay.ARABIC }
        val expected = original.copy(sort = HomeSort.AVAILABILITY, layout = CardLayout.EXPANDED, names = NameDisplay.ARABIC)
        assertEquals(expected, runBlocking { preferences.home.first() })
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(expected, runBlocking { DisplayPreferences(compose.activity.applicationContext).home.first() })
        compose.onNodeWithTag("back").performClick()
        if (medication != null) {
            compose.runOnUiThread { viewModel = ViewModelProvider(compose.activity)[CatalogViewModel::class.java] }
            awaitRows(requireNotNull(section), "", medication)
            show(medication)
            compose.onNodeWithTag(card(medication)).assertTextContains("سيرترالين", substring = true)
        }
    }
}
