package com.aqaqeer.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MedicationBadgeRegressionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readOnlyBadgeHasWrittenMeaningWithoutButtonOrClickSemantics() {
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) {
            AvailabilityBadge("توفر محدود", statusTone(AvailabilityStatus.LOW), Modifier.testTag("badge"))
        } }
        compose.onNodeWithText("توفر محدود").assertIsDisplayed()
        compose.onNodeWithTag("badge").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test fun editableBadgeHasMinimumTouchTargetAndInvokesExactlyOneAction() {
        var clicks = 0
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) {
            AvailabilityBadge("متوفر", statusTone(AvailabilityStatus.AVAILABLE), Modifier.testTag("badge"),
                dense = true, onClick = { clicks++ }, actionDescription = "توافر الدواء", actionLabel = "تعديل")
        } }
        compose.onNodeWithTag("badge").assertIsEnabled().assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun savingAndDisabledBadgeRemoveEditActionAndRetainGeometry() {
        var saving by mutableStateOf(false); var enabled by mutableStateOf(true); var clicks = 0
        compose.setContent { AqaqeerTheme(ThemeChoice.DARK) {
            AvailabilityBadge("غير متوفر", statusTone(AvailabilityStatus.OUT), Modifier.testTag("badge"),
                dense = true, onClick = { clicks++ }, enabled = enabled, saving = saving)
        } }
        val idle = compose.onNodeWithTag("badge").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { saving = true }
        compose.onNodeWithTag("badge").assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, uiText(R.string.availability_saving_description)))
            .performTouchInput { click() }
        val busy = compose.onNodeWithTag("badge").fetchSemanticsNode().boundsInRoot
        assertEquals(idle.width, busy.width, 0.5f); assertEquals(idle.height, busy.height, 0.5f)
        compose.runOnIdle { saving = false; enabled = false }
        compose.onNodeWithTag("badge").assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .performTouchInput { click() }
        val disabled = compose.onNodeWithTag("badge").fetchSemanticsNode().boundsInRoot
        assertEquals(idle.width, disabled.width, 0.5f); assertEquals(idle.height, disabled.height, 0.5f)
        compose.runOnIdle { assertEquals(0, clicks); enabled = true }
        compose.onNodeWithTag("badge").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun expiryBadgeIsReadOnlyAndUpdatesAtTheMedicationBoundary() {
        val day = LocalDate.of(2026, 11, 24)
        val med = F.catalog().medications.single().copy(expiryDate = day, expirySoonThresholdDays = 0)
        var today by mutableStateOf(day.minusDays(1))
        compose.setContent { AqaqeerTheme(ThemeChoice.AMOLED) { ExpiryBadge(med, today, dense = true) } }
        compose.onNodeWithTag("expiry_${med.id}").assertDoesNotExist()
        compose.runOnIdle { today = day }
        compose.onNodeWithTag("expiry_${med.id}").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText(uiText(R.string.catalog_expiring_soon_badge_label)).assertIsDisplayed()
        compose.runOnIdle { today = day.plusDays(1) }
        compose.onNodeWithText("منتهي الصلاحية").assertIsDisplayed()
        compose.onNodeWithTag("expiry_${med.id}").assertHasNoClickAction()
    }

    @Test fun narrowRtlCardsKeepDoseAndBothBadgesReachableInBothLayoutsAndThemes() {
        val seed = F.catalog(); val day = LocalDate.of(2026, 11, 24)
        val med = seed.medications.single().copy(genericAr = "اسم علمي عربي طويل للتحقق من التفاف النص",
            genericEn = "LongScientificMedicationNameWithoutSpaces123456789", strength = "500 mg", expiryDate = day)
        val row = MedicationRowUi(med, seed.formsById[med.formId], "تصنيف", AvailabilityStatus.LOW, emptyList(), false)
        var layout by mutableStateOf(CardLayout.COMPACT); var theme by mutableStateOf(ThemeChoice.LIGHT)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale), LocalLayoutDirection provides LayoutDirection.Rtl) {
                AqaqeerTheme(theme) { Column(Modifier.width((140f * scale).dp).fillMaxHeight()
                    .then(Modifier.testTag("viewport")).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    MedicationCard(row, seed.sections.single().id, HomePreferences(layout = layout), day,
                        null, null, {}, {})
                } }
            }
        }
        for (choice in listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.AMOLED))
            for (mode in CardLayout.entries) for (font in listOf(1f, 1.5f)) {
            compose.runOnIdle { theme = choice; layout = mode; scale = font }
            compose.onNodeWithText("500 mg", substring = true, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("توفر محدود", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("expiry_${med.id}", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            for (text in listOf("توفر محدود", uiText(R.string.catalog_expiring_soon_badge_label))) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(1, layouts.size)
                assertFalse("Badge text overflow: $choice/$mode/$font", layouts.single().hasVisualOverflow)
            }
            val card = compose.onNodeWithTag("med_${med.nameEn}").fetchSemanticsNode().boundsInRoot
            val expiry = compose.onNodeWithTag("expiry_${med.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(expiry.left >= card.left - 0.5f); assertTrue(expiry.right <= card.right + 0.5f)
        }
    }
}
