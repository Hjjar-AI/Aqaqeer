package com.aqaqeer.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
        control(R.string.report_landscape).assertIsOff().performClick()
        control(R.string.report_compact).assertIsOn().performClick()
        control(R.string.report_group_category).assertIsOn().performClick()
        compose.runOnIdle {
            assertEquals(ReportOptions(landscape = true, compact = false, groupByCategory = false), options)
            busy = true
        }
        control(R.string.report_landscape).assertIsOn().assertIsNotEnabled()
        control(R.string.report_compact).assertIsOff().assertIsNotEnabled()
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
