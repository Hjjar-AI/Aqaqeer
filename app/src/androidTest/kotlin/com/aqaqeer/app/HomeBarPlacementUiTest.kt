package com.aqaqeer.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.fixtures.homeProjection
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HomeBarPlacementUiTest {
    @get:Rule val compose = createComposeRule()
    private val second = Section(F.otherPharmacyId, "القسم الثاني")
    private val catalog = F.catalog().let { c -> c.copy(sections = c.sections + second,
        availability = c.availability.map { it.copy(status = AvailabilityStatus.OUT) } +
            Availability(c.medications.single().id, second.id, AvailabilityStatus.AVAILABLE, F.time, F.editor)) }

    private fun layout(sectionsBottom: Boolean, searchBottom: Boolean) {
        var state by mutableStateOf(AppUiState(sectionId = second.id, query = "Fixture", appliedQuery = "Fixture",
            filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE)),
            homePreferences = HomePreferences(sectionsAtBottom = sectionsBottom, searchAtBottom = searchBottom)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(560.dp).imePadding()) {
                    HomeScreen(CatalogState.Ready(catalog, false), state, F.time,
                        { state = state.copy(sectionId = it) }, { state = state.copy(query = it, appliedQuery = it) },
                        {}, {}, {}, {}, {}, projection = homeProjection(catalog, state))
                } }
            }
        }
        compose.onNodeWithTag("home").assertIsDisplayed()
        val list = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot
        val sections = compose.onNodeWithTag("section_${second.id}").fetchSemanticsNode().boundsInRoot
        if (searchBottom) assertTrue(search.top >= list.bottom) else assertTrue(search.bottom <= list.top)
        if (sectionsBottom) assertTrue(sections.top >= list.bottom) else assertTrue(sections.bottom <= list.top)
        if (sectionsBottom && searchBottom) assertTrue(sections.top >= search.bottom)
        if (!sectionsBottom && !searchBottom) assertTrue(sections.bottom <= search.top)
        compose.onNodeWithTag("section_${second.id}").assertIsSelected()
        compose.onNodeWithTag("search").assertTextContains("Fixture", substring = true)
        compose.onNodeWithContentDescription("الفلاتر", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("med_Fixture medication").assertIsDisplayed()
    }

    @Test fun bothBarsDefaultToBottomWithSectionsBottommost() {
        assertTrue(HomePreferences().sectionsAtBottom); assertTrue(HomePreferences().searchAtBottom)
        layout(true, true)
    }
    @Test fun sectionsCanBeBottomWhileSearchStaysAtTop() { layout(true, false) }
    @Test fun searchCanBeBottomWhileSectionsStayAtTop() { layout(false, true) }
    @Test fun bothBarsCanReturnToTheirOriginalTopOrder() { layout(false, false) }

    @Test fun changingPlacementPreservesQueryFiltersAndSelectedSection() {
        var state by mutableStateOf(AppUiState(sectionId = second.id, query = "Fixture", appliedQuery = "Fixture",
            filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE))))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(560.dp)) {
            HomeScreen(CatalogState.Ready(catalog, false), state, F.time, {}, {}, {}, {}, {}, {}, {},
                projection = homeProjection(catalog, state))
        } } }
        for ((sections, search) in listOf(true to true, false to true, true to false, false to false)) {
            compose.runOnIdle { state = state.copy(homePreferences = state.homePreferences.copy(
                sectionsAtBottom = sections, searchAtBottom = search)) }
            compose.onNodeWithTag("search").assertIsDisplayed().assertTextContains("Fixture", substring = true)
            compose.onNodeWithTag("section_${second.id}").assertIsSelected()
            compose.onNodeWithTag("med_Fixture medication").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("Fixture", state.query); assertEquals(second.id, state.sectionId)
                assertEquals(setOf(AvailabilityStatus.AVAILABLE), state.filters.statuses)
            }
        }
    }

    @Test fun keyboardHidesBottomSectionsAndDismissalRestoresSelectionAndQuery() {
        var state by mutableStateOf(AppUiState(sectionId = second.id))
        val keyboardVisible = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent {
            val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            SideEffect { keyboardVisible.set(ime) }
            AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.fillMaxSize().imePadding()) {
                HomeScreen(CatalogState.Ready(catalog, false), state, F.time,
                    { state = state.copy(sectionId = it) }, { state = state.copy(query = it, appliedQuery = it) },
                    {}, {}, {}, {}, {}, projection = homeProjection(catalog, state))
            } }
        }
        compose.onNodeWithTag("section_${second.id}").assertIsSelected()
        compose.onNodeWithTag("search").performClick().performTextInput("Fixture")
        compose.waitUntil(10000) { keyboardVisible.get() }
        compose.onNodeWithTag("section_${second.id}").assertDoesNotExist()
        compose.onNodeWithTag("search").assertIsDisplayed().assertTextContains("Fixture", substring = true)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10000) { !keyboardVisible.get() }
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("search").assertTextContains("Fixture", substring = true)
        compose.runOnIdle { assertEquals(second.id, state.sectionId); assertEquals("Fixture", state.query) }
    }

    @Test fun compactBottomSectionsRemainReachableAfterScrollingTheCatalog() {
        val large = catalog.copy(medications = (1..30).map { i -> catalog.medications.single().copy(
            id = "00000000-0000-4000-8000-%012d".format(java.util.Locale.ROOT, i), nameEn = "Medication $i", genericEn = "Medication $i") },
            availability = emptyList())
        val state = AppUiState(sectionId = second.id, homePreferences = HomePreferences(groupingEnabled = false))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(300.dp)) {
            HomeScreen(CatalogState.Ready(large, false), state, F.time, {}, {}, {}, {}, {}, {}, {},
                projection = homeProjection(large, state))
        } } }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Medication 9"))
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("search").assertIsDisplayed()
        val list = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("section_${second.id}").fetchSemanticsNode().boundsInRoot.top >= list.bottom)
    }
    @Test fun compactTopSectionsScrollWithTheGridWhileSearchStaysFixed() {
        val large = catalog.copy(medications = (1..30).map { i -> catalog.medications.single().copy(
            id = "00000000-0000-4000-8000-%012d".format(java.util.Locale.ROOT, i), nameEn = "Medication $i", genericEn = "Medication $i") },
            availability = emptyList())
        val state = AppUiState(sectionId = second.id, homePreferences = HomePreferences(groupingEnabled = false,
            sectionsAtBottom = false, searchAtBottom = false))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(300.dp)) {
            HomeScreen(CatalogState.Ready(large, false), state, F.time, {}, {}, {}, {}, {}, {}, {},
                projection = homeProjection(large, state))
        } } }
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Medication 9"))
        compose.onNodeWithTag("section_${second.id}").assertDoesNotExist()
        compose.onNodeWithTag("search").assertIsDisplayed()
    }

}
