package com.aqaqeer.app

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aqaqeer.app.data.*
import com.aqaqeer.app.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Larger catalog fixture stays in androidTest; no additional starter categories/sections ship. */
class HomeFixtureUiTest {
    private fun homeNode(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    @get:Rule val compose = createComposeRule()
    private fun catalog(): CatalogSnapshot {
        val seed = CatalogSeeds.unconfigured()
        val editor = EditorRef("test", "Test")
        val sections = seed.sections + Section("second", "الفرع الآخر", orderIndex = 1)
        val categories = seed.categories + Category("long", "تصنيف ذو اسم عربي طويل يمكن الوصول إليه بالتمرير الأفقي", orderIndex = 1)
        val med = Medication("m", "English only", "10 mg", CatalogSeeds.OTHER_FORM_ID, "long",
            Instant.EPOCH, Instant.EPOCH, editor, expiryDate = LocalDate.of(2026, 11, 9), priceMinor = 1250, currency = Currency.USD)
        return seed.copy(pharmacy = Pharmacy("test", "صيدلية الاختبار", "Asia/Damascus", PinVerifier("", 200_000, "")),
            sections = sections, categories = categories, medications = listOf(med), availability = listOf(
                Availability(med.id, sections[0].id, AvailabilityStatus.OUT, Instant.EPOCH, editor),
                Availability(med.id, sections[1].id, AvailabilityStatus.AVAILABLE, Instant.EPOCH, editor)))
    }

    @Test fun narrowRtlRowsAllowLongCategoriesAndSectionChangeRemovesOutTip() {
        val catalog = catalog()
        var state by mutableStateOf(AppUiState(homePreferences = HomePreferences(layout = CardLayout.EXPANDED, names = NameDisplay.ARABIC)))
        val now = Instant.parse("2026-11-09T10:00:00Z")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                AqaqeerTheme(ThemeChoice.LIGHT) { Box(Modifier.width(320.dp)) {
                    HomeScreen(CatalogState.Ready(catalog, false), state, now,
                        { state = state.copy(sectionId = it) }, {}, {},
                        { state = state.copy(filters = HomeFilters(categoryIds = setOf(it))) }, {}, {}, {})
                } }
            }
        }
        homeNode("category_long").performScrollTo().performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_English only"))
        compose.onNodeWithTag("tip_m", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("English only", substring = true).assertExists()
        homeNode("search").assertIsDisplayed()
        homeNode("status_filters").assertIsDisplayed()
        homeNode("category_filters").assertIsDisplayed()
        homeNode("section_second").performClick()
        compose.onNodeWithTag("tip_m", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun expiryRecomputesAtPharmacyMidnightWithoutChangingAvailability() {
        val catalog = catalog()
        var now by mutableStateOf(Instant.parse("2026-11-09T20:59:59Z"))
        compose.setContent {
            AqaqeerTheme(ThemeChoice.DARK) {
                HomeScreen(CatalogState.Ready(catalog, false), AppUiState(), now, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_English only"))
        compose.onNodeWithTag("expiry_m", useUnmergedTree = true).assertTextEquals("قريب الانتهاء")
        compose.runOnIdle { now = Instant.parse("2026-11-09T21:00:00Z") }
        compose.onNodeWithTag("expiry_m", useUnmergedTree = true).assertTextEquals("منتهي الصلاحية")
        compose.onNodeWithTag("med_English only").assertTextContains("غير متوفر")
    }
    @Test fun futureCompatibilityNoticeKeepsCachedMedicationAndBrowsingAvailable() {
        compose.setContent {
            AqaqeerTheme(ThemeChoice.SYSTEM) {
                HomeScreen(CatalogState.Ready(catalog(),false,requiresUpdate=true),AppUiState(),
                    Instant.parse("2026-11-09T10:00:00Z"),{}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("home").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()
        }
        compose.onNodeWithTag("home").performScrollToNode(hasText("يلزم إصدار متوافق من التطبيق"))
        compose.onNodeWithText("يلزم إصدار متوافق من التطبيق").assertIsDisplayed()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_English only"))
        compose.onNodeWithTag("med_English only").assertIsDisplayed()
        homeNode("search").assertIsDisplayed()
        homeNode("status_filters").assertIsDisplayed()
    }

}
