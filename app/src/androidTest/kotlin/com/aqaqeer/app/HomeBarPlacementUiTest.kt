package com.aqaqeer.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.admin.newId
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
            Availability(c.medications.single().id, second.id, AvailabilityStatus.AVAILABLE, F.time)) }

    private fun layout(sectionsBottom: Boolean, searchBottom: Boolean) {
        var state by mutableStateOf(AppUiState(sectionId = second.id, query = "Fixture", appliedQuery = "Fixture",
            filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE)),
            homePreferences = HomePreferences(sectionsAtBottom = sectionsBottom, searchAtBottom = searchBottom)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(560.dp).imePadding()) {
                    HomeScreen(CatalogState.Ready(catalog, false), state, F.time,
                        projection = homeProjection(catalog, state, readerStockOnly = true),
                        actions = rememberHomeActions(HomeCallbacks(
                            selectSection = { state = state.copy(sectionId = it) },
                            setQuery = { state = state.copy(query = it, appliedQuery = it) },
                            toggleStatus = {},
                            toggleCategory = {},
                            clearFilters = {},
                            openMedication = {},
                            openSettings = {})))
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

    @Test fun sectionSwitchKeepsAdminAddAndSearchBoundsStableInBothPlacements() {
        val firstId = catalog.sections.first().id
        var state by mutableStateOf(AppUiState(sectionId = firstId, query = "Fixture", appliedQuery = "Fixture",
            homePreferences = HomePreferences(sectionsAtBottom = false, searchAtBottom = false)))
        var adds = 0
        var busy by mutableStateOf(false)
        compose.setContent {
            AqaqeerTheme(ThemeChoice.LIGHT) {
                Box(Modifier.width(320.dp).height(560.dp)) {
                    HomeScreen(CatalogState.Ready(catalog, false, admin = true), state, F.time,
                        availabilityBusy = busy, projection = homeProjection(catalog, state),
                        actions = rememberHomeActions(HomeCallbacks(
                            selectSection = { state = state.copy(sectionId = it) },
                            setQuery = { state = state.copy(query = it, appliedQuery = it) },
                            toggleStatus = {},
                            toggleCategory = {},
                            clearFilters = {},
                            openMedication = {},
                            openSettings = {},
                            saveAvailability = { _, _, _ -> },
                            openAddMedication = { adds++ },
                            availabilityInProgress = { busy })))
                }
            }
        }
        val search = compose.onNodeWithTag("search")
        val add = compose.onNodeWithContentDescription(uiText(R.string.navigation_add_medication_title))
        for (bottom in listOf(false, true)) {
            compose.runOnIdle {
                state = state.copy(sectionId = firstId, homePreferences = state.homePreferences.copy(
                    sectionsAtBottom = bottom, searchAtBottom = bottom))
            }
            compose.onNodeWithTag("section_$firstId").assertIsSelected()
            add.assertIsDisplayed().assertIsEnabled()
            val searchBounds = search.fetchSemanticsNode().boundsInRoot
            val addBounds = add.fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            try {
                val target = compose.onNodeWithTag("section_${second.id}")
                target.performClick()
                // Inspect the first frame of the new section before waiting for reconciliation and motion.
                compose.mainClock.advanceTimeByFrame()
                target.assertIsSelected()
                add.assertIsDisplayed().assertIsEnabled()
                assertEquals(searchBounds, search.fetchSemanticsNode().boundsInRoot)
                assertEquals(addBounds, add.fetchSemanticsNode().boundsInRoot)
            } finally {
                compose.mainClock.autoAdvance = true
            }
            search.assertTextContains("Fixture", substring = true)
            assertEquals(searchBounds, search.fetchSemanticsNode().boundsInRoot)
            assertEquals(addBounds, add.fetchSemanticsNode().boundsInRoot)
            add.performClick()
        }
        compose.runOnIdle { busy = true; assertEquals(2, adds) }
        add.assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun changingPlacementPreservesQueryFiltersAndSelectedSection() {
        var state by mutableStateOf(AppUiState(sectionId = second.id, query = "Fixture", appliedQuery = "Fixture",
            filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE))))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(560.dp)) {
            HomeScreen(CatalogState.Ready(catalog, false), state, F.time,
                projection = homeProjection(catalog, state, readerStockOnly = true),
                actions = rememberHomeActions(HomeCallbacks(
                    selectSection = {},
                    setQuery = {},
                    toggleStatus = {},
                    toggleCategory = {},
                    clearFilters = {},
                    openMedication = {},
                    openSettings = {})))
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

    @Test fun switchingTopSectionsWithKeyboardOpenPreservesQueryAndCursorAtLargeRtlText() {
        val first = catalog.sections.first()
        var state by mutableStateOf(AppUiState(sectionId = first.id,
            filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE)),
            homePreferences = HomePreferences(sectionsAtBottom = false, searchAtBottom = false)))
        val keyboardVisible = java.util.concurrent.atomic.AtomicBoolean()
        compose.setContent {
            val density = LocalDensity.current
            val ime = WindowInsets.ime.getBottom(density) > 0
            SideEffect { keyboardVisible.set(ime) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(360.dp).fillMaxHeight().imePadding()) {
                    AqaqeerTheme(ThemeChoice.LIGHT) {
                        HomeScreen(CatalogState.Ready(catalog, false, admin = true), state, F.time,
                            projection = homeProjection(catalog, state),
                            actions = rememberHomeActions(HomeCallbacks(
                                selectSection = { state = state.copy(sectionId = it) },
                                setQuery = { state = state.copy(query = it, appliedQuery = it) },
                                toggleStatus = {},
                                toggleCategory = {},
                                clearFilters = {},
                                openMedication = {},
                                openSettings = {},
                                openAddMedication = {})))
                    }
                }
            }
        }
        val search = compose.onNodeWithTag("search")
        search.performClick().performTextInput("Fixture")
        compose.waitUntil(10_000) { keyboardVisible.get() }
        search.performSemanticsAction(SemanticsActions.SetSelection) { it(2, 2, false) }
        val cursor = SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(2))
        search.assert(cursor)
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed().performClick().assertIsSelected()
        search.assertIsDisplayed().assertIsFocused().assertTextContains("Fixture", substring = true).assert(cursor)
        assertTrue(keyboardVisible.get())
        compose.onNodeWithContentDescription(uiText(R.string.navigation_add_medication_title))
            .assertIsDisplayed().assertIsEnabled()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10_000) { !keyboardVisible.get() }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Fixture medication"))
        compose.onNodeWithTag("med_Fixture medication").assertIsDisplayed()
    }

    @Test fun groupedCrossSectionSearchReturnsToCurrentResultsAndCannotSelectStaleRows() {
        val first = catalog.sections.first()
        val other = catalog.medications.single()
        val local = other.copy(id = newId(), nameEn = "Local medicine", genericEn = "Local medicine",
            nameAr = null, genericAr = null)
        val crossSectionMatches = (1..18).map { index ->
            val name = "Fixture extra ${index.toString().padStart(2, '0')}"
            local.copy(id = newId(), nameEn = name, genericEn = name)
        }
        val groupedCatalog = catalog.copy(medications = catalog.medications + local + crossSectionMatches,
            availability = catalog.availability + Availability(local.id, first.id, AvailabilityStatus.AVAILABLE, F.time) +
                crossSectionMatches.map { Availability(it.id, second.id, AvailabilityStatus.AVAILABLE, F.time) })
        var state by mutableStateOf(AppUiState(sectionId = first.id, query = "Fixture", appliedQuery = "Fixture",
            filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE)),
            homePreferences = HomePreferences(groupingEnabled = true)))
        var projectedQuery by mutableStateOf(state.appliedQuery)
        compose.setContent {
            AqaqeerTheme(ThemeChoice.LIGHT) {
                HomeScreen(CatalogState.Ready(groupedCatalog, false, admin = true), state, F.time,
                    projection = homeProjection(groupedCatalog, state.copy(appliedQuery = projectedQuery)),
                    actions = rememberHomeActions(HomeCallbacks(
                        selectSection = {},
                        setQuery = { state = state.copy(query = it, appliedQuery = it); projectedQuery = it },
                        toggleStatus = {},
                        toggleCategory = {},
                        clearFilters = {},
                        openMedication = {},
                        openSettings = {},
                        saveBulk = { _, _, _ -> fail("This selection must never write a bulk change") })))
            }
        }
        val grid = compose.onNodeWithTag("home")
        val crossSection = compose.onNodeWithTag("med_Fixture medication")
        val heading = hasText(first.nameAr) and hasAnyAncestor(hasTestTag("home"))
        grid.performScrollToNode(hasTestTag("med_Fixture medication"))
        crossSection.assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        grid.performScrollToNode(hasTestTag("med_Fixture extra 18"))
        compose.onNode(heading).assertIsNotDisplayed()
        compose.onNodeWithTag("search").performTextReplacement("Fixture medication")
        compose.onNode(heading).assertIsDisplayed()
        compose.onNodeWithTag("search").performTextClearance()
        compose.onNode(heading).assertDoesNotExist()
        val category = requireNotNull(groupedCatalog.categoriesById[local.categoryId]).nameAr
        compose.onNode(hasText(category) and hasClickAction() and hasAnyAncestor(hasTestTag("home")))
            .assertIsDisplayed()

        val localTag = "med_Local medicine"
        grid.performScrollToNode(hasTestTag(localTag))
        compose.onNodeWithTag(localTag).performTouchInput { longClick() }
        val selectVisible = compose.onNodeWithText(uiText(R.string.availability_select_visible_action))
        selectVisible.assertIsEnabled()
        compose.runOnIdle { state = state.copy(appliedQuery = "Fixture medication") }
        compose.onNodeWithTag(localTag).assertExists()
        selectVisible.assertIsNotEnabled()
        compose.runOnIdle { projectedQuery = state.appliedQuery }
        compose.onNodeWithTag(localTag).assertDoesNotExist()
        selectVisible.assertIsNotEnabled()
    }

    @Test fun keyboardHidesBottomSectionsAndDismissalRestoresSelectionAndQuery() {
        var state by mutableStateOf(AppUiState(sectionId = second.id))
        val keyboardVisible = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent {
            val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            SideEffect { keyboardVisible.set(ime) }
            AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.fillMaxSize().imePadding()) {
                HomeScreen(CatalogState.Ready(catalog, false), state, F.time,
                    projection = homeProjection(catalog, state, readerStockOnly = true),
                    actions = rememberHomeActions(HomeCallbacks(
                        selectSection = { state = state.copy(sectionId = it) },
                        setQuery = { state = state.copy(query = it, appliedQuery = it) },
                        toggleStatus = {},
                        toggleCategory = {},
                        clearFilters = {},
                        openMedication = {},
                        openSettings = {})))
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
            availability = emptyList()).let { snapshot -> snapshot.copy(availability = snapshot.medications.map {
                Availability(it.id, second.id, AvailabilityStatus.AVAILABLE, F.time)
            }) }
        val state = AppUiState(sectionId = second.id, homePreferences = HomePreferences(groupingEnabled = false))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(300.dp)) {
            HomeScreen(CatalogState.Ready(large, false), state, F.time,
                projection = homeProjection(large, state, readerStockOnly = true),
                actions = rememberHomeActions(HomeCallbacks(
                    selectSection = {},
                    setQuery = {},
                    toggleStatus = {},
                    toggleCategory = {},
                    clearFilters = {},
                    openMedication = {},
                    openSettings = {})))
        } } }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Medication 9"))
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("search").assertIsDisplayed()
        val list = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("section_${second.id}").fetchSemanticsNode().boundsInRoot.top >= list.bottom)
    }
    @Test fun compactTopSectionsStayPinnedWhileTheGridScrolls() {
        val large = catalog.copy(medications = (1..30).map { i -> catalog.medications.single().copy(
            id = "00000000-0000-4000-8000-%012d".format(java.util.Locale.ROOT, i), nameEn = "Medication $i", genericEn = "Medication $i") },
            availability = emptyList()).let { snapshot -> snapshot.copy(availability = snapshot.medications.map {
                Availability(it.id, second.id, AvailabilityStatus.AVAILABLE, F.time)
            }) }
        val state = AppUiState(sectionId = second.id, homePreferences = HomePreferences(groupingEnabled = false,
            sectionsAtBottom = false, searchAtBottom = false))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp).height(300.dp)) {
            HomeScreen(CatalogState.Ready(large, false), state, F.time,
                projection = homeProjection(large, state, readerStockOnly = true),
                actions = rememberHomeActions(HomeCallbacks(
                    selectSection = {},
                    setQuery = {},
                    toggleStatus = {},
                    toggleCategory = {},
                    clearFilters = {},
                    openMedication = {},
                    openSettings = {})))
        } } }
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Medication 9"))
        compose.onNodeWithTag("section_${second.id}").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("search").assertIsDisplayed()
        val list = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
        assertTrue(compose.onNodeWithTag("section_${second.id}").fetchSemanticsNode().boundsInRoot.bottom <= list.top)
    }

    @Test fun largeTextKeepsAllFourBarPlacementsPinnedAndSearchVisible() {
        var state by mutableStateOf(AppUiState(sectionId = second.id,
            homePreferences = HomePreferences(groupingEnabled = false)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                AqaqeerTheme(ThemeChoice.DARK) { Box(Modifier.width(420.dp).height(640.dp)) {
                    HomeScreen(CatalogState.Ready(catalog, false), state, F.time,
                        projection = homeProjection(catalog, state, readerStockOnly = true),
                        actions = rememberHomeActions(HomeCallbacks(
                            selectSection = {},
                            setQuery = {},
                            toggleStatus = {},
                            toggleCategory = {},
                            clearFilters = {},
                            openMedication = {},
                            openSettings = {})))
                } }
            }
        }
        for ((sectionsBottom, searchBottom) in listOf(true to true, true to false, false to true, false to false)) {
            compose.runOnIdle { state = state.copy(homePreferences = state.homePreferences.copy(
                sectionsAtBottom = sectionsBottom, searchAtBottom = searchBottom)) }
            compose.onNodeWithTag("search").assertIsDisplayed()
            compose.onNodeWithTag("section_${second.id}").assertIsDisplayed().assertIsSelected()
            val list = compose.onNodeWithTag("home").fetchSemanticsNode().boundsInRoot
            val tabs = compose.onNodeWithTag("section_${second.id}").fetchSemanticsNode().boundsInRoot
            assertTrue(list.height > 0f)
            if (sectionsBottom) assertTrue(tabs.top >= list.bottom) else assertTrue(tabs.bottom <= list.top)
        }
    }

}
