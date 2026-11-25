package com.aqaqeer.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.reports.*
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReportControlsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun control(id: Int) = compose.onNodeWithText(uiText(id)).performScrollTo()
    private fun content(body: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                AqaqeerTheme(ThemeChoice.DARK) {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { body() }
                }
            }
        }
    }
    @Test fun requiredScientificIdentityAndOptionalNotesRemainUsableAtLargeArabicText() {
        var options by mutableStateOf(ReportOptions())
        content { ReportPresentationControls(options, false) { options = it } }
        control(R.string.report_fields).performClick()
        control(R.string.report_field_scientific).assertIsOn().assertIsNotEnabled()
        control(R.string.report_field_note).assertIsOff().performClick()
        compose.runOnIdle { assertTrue(ReportField.NOTE in options.fields); assertTrue(ReportField.SCIENTIFIC in options.fields) }
        control(R.string.report_field_note).assertIsOn().performClick()
        compose.runOnIdle { assertFalse(ReportField.NOTE in options.fields) }
    }
    @Test fun layoutChoicesChangeOnlyPresentationAndBusyStateDisablesEdits() {
        var options by mutableStateOf(ReportOptions())
        var busy by mutableStateOf(false)
        content { ReportPresentationControls(options, busy) { options = it } }
        control(R.string.report_layout).performClick()
        control(R.string.report_paper_a5).assertIsDisplayed().assert(hasClickAction().not())
        control(R.string.report_sort_direction).performClick()
        compose.onNodeWithText(uiText(R.string.report_direction_descending)).performClick()
        control(R.string.report_side_margins).performClick()
        compose.onNodeWithText(uiText(R.string.report_margin_wide)).performClick()
        control(R.string.report_landscape).assertIsOff().performClick()
        control(R.string.report_compact).assertIsOn().performClick()
        control(R.string.report_group_category).assertIsOn().performClick()
        compose.runOnIdle {
            assertEquals(ReportOptions(landscape = true, compact = false, groupByCategory = false,
                sortDirection = ReportSortDirection.DESCENDING, sideMargins = ReportSideMargins.WIDE), options)
            busy = true
        }
        control(R.string.report_landscape).assertIsOn().assertIsNotEnabled()
        control(R.string.report_compact).assertIsOff().assertIsNotEnabled()
        control(R.string.report_side_margins).assertIsNotEnabled()
        control(R.string.report_sort_direction).assertIsNotEnabled()
    }
    @Test fun combinedAvailabilityAndFieldReorderingRespectRequiredFieldsAndBusyState() {
        var options by mutableStateOf(ReportOptions())
        var busy by mutableStateOf(false)
        content { ReportPresentationControls(options, busy) { options = it } }
        control(R.string.report_fields).performClick()
        control(R.string.report_availability_output).performClick()
        compose.onNodeWithText(uiText(R.string.report_availability_both)).performClick()
        control(R.string.report_field_order).performClick()
        val scientific = uiText(R.string.report_field_scientific)
        val earlier = compose.onNodeWithContentDescription(uiText(R.string.report_move_field_earlier, scientific))
        val later = compose.onNodeWithContentDescription(uiText(R.string.report_move_field_later, scientific))
        earlier.performScrollTo().assertIsNotEnabled()
        later.performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(ReportField.STRENGTH, ReportField.SCIENTIFIC, ReportField.FORM, ReportField.AVAILABILITY),
                options.selectedFieldsInOrder)
            assertEquals(ReportAvailability.BOTH, options.availability)
            busy = true
        }
        earlier.performScrollTo().assertIsNotEnabled()
        later.performScrollTo().assertIsNotEnabled()
        control(R.string.report_reset_field_order).assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        control(R.string.report_reset_field_order).performClick()
        compose.runOnIdle { assertEquals(ReportOptions(availability = ReportAvailability.BOTH), options) }
    }
    @Test fun sharedStepHeaderDisclosesProgressWithoutChangingTheCurrentStep() {
        val steps = listOf("مصدر الملف", "مراجعة المحتوى", "حفظ الملف")
        var current by mutableIntStateOf(1)
        content { StepHeader(steps, current) }
        fun state(id: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, uiText(id))
        fun activeState(position: Int, disclosure: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            listOf(uiText(R.string.shared_step_current), uiText(R.string.shared_step_position, position, steps.size),
                uiText(disclosure)).joinToString(" · "))
        val first = compose.onNodeWithText(steps.first())
        val second = compose.onNodeWithText(steps[1])
        val last = compose.onNodeWithText(steps.last())

        first.assertDoesNotExist()
        last.assertDoesNotExist()
        second.assert(activeState(2, R.string.shared_collapsed_state_label)).performClick()
        second.assert(activeState(2, R.string.shared_expanded_state_label))
        first.performScrollTo().assert(state(R.string.shared_step_completed)).assert(hasClickAction().not())
        last.performScrollTo().assert(state(R.string.shared_step_pending)).assert(hasClickAction().not())
        compose.runOnIdle { current = 2 }
        second.performScrollTo().assert(state(R.string.shared_step_completed))
        last.performScrollTo().assert(activeState(3, R.string.shared_expanded_state_label)).performClick()
        first.assertDoesNotExist()
        second.assertDoesNotExist()
        last.assert(activeState(3, R.string.shared_collapsed_state_label))
    }
    @Test fun medicationPickerSearchSelectAllAndClearRetainExplicitSelectionMeaning() {
        var selected by mutableStateOf(emptySet<String>())
        val values = (0..99).map { ReportChoice("$it", "Target %03d".format(it)) }
        content { ReportMultiPicker("Medication selection", values, selected, true) { selected = it } }
        compose.onNodeWithText("Medication selection").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Target 099")
        val target = hasText("Target 099") and isToggleable()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(target)
        compose.onNode(target).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(setOf("99"), selected) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText(uiText(R.string.report_select_all)).performClick()
        compose.runOnIdle { assertEquals(values.map { it.id }.toSet(), selected) }
        compose.onNodeWithText(uiText(R.string.report_clear_selection)).performClick()
        compose.runOnIdle { assertTrue(selected.isEmpty()) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(target)
        compose.onNode(target).assertIsOff()
    }
}
