package com.aqaqeer.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class PresentationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hijriIsOptionalAndGregorianInputDateIsUnchanged() {
        var enabled by mutableStateOf(false)
        val date = LocalDate.of(2024,7,8)
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { SecondaryHijriDate(date, enabled) } }
        compose.onAllNodesWithText("1 محرم 1446", substring = true).assertCountEquals(0)
        compose.runOnIdle { enabled = true }
        compose.onNodeWithText("1 محرم 1446", substring = true).assertIsDisplayed()
        assertEquals("2024-07-08", date.toString())
        compose.runOnIdle { enabled = false }
        compose.onAllNodesWithText("1 محرم 1446", substring = true).assertCountEquals(0)
    }
    @Test fun quickAvailabilityCancelIsInertAndSaveUsesChosenStatus() {
        var expanded by mutableStateOf(false)
        var cancelled = 0; val saved = mutableListOf<AvailabilityStatus>()
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) {
            EditableStatusPill(AvailabilityStatus.AVAILABLE, { expanded = true }, expanded,
                { cancelled++; expanded = false }, { saved += it; expanded = false })
        } }
        compose.onNodeWithText(MedicationPresentation.statusLabel(AvailabilityStatus.AVAILABLE)).performClick()
        compose.onNodeWithText(MedicationPresentation.statusLabel(AvailabilityStatus.OUT)).performClick()
        assertEquals(listOf(AvailabilityStatus.OUT), saved)
        compose.onNodeWithText(MedicationPresentation.statusLabel(AvailabilityStatus.AVAILABLE)).performClick()
        androidx.test.espresso.Espresso.pressBack()
        assertEquals(1, cancelled); assertEquals(1, saved.size)
    }

    @Test fun bulkSelectionRequiresPreviewAndBusyDisablesSaveAndCancel() {
        val c = F.catalog(); val a = c.medications.single(); val b = a.copy(id = "second", nameEn = "Second medication")
        val selection = AvailabilitySelection(F.pharmacyId,c.sections.single(),listOf(a,b),mapOf(a.id to AvailabilityStatus.AVAILABLE,b.id to AvailabilityStatus.OUT),true)
        var busy by mutableStateOf(false); var saves = 0
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { BulkMedicationEditor(selection,c,busy,null,{}, { saves++ }, { true }) } }
        compose.onNodeWithText(uiText(R.string.availability_bulk_preview_action)).assertIsNotEnabled()
        compose.onNodeWithText(MedicationPresentation.statusLabel(AvailabilityStatus.OUT)).performClick()
        compose.onNodeWithText(uiText(R.string.availability_bulk_preview_action)).performClick()
        assertEquals(0, saves)
        compose.onNodeWithText("Second medication", substring = true).assertExists()
        compose.runOnIdle { busy = true }
        compose.onNodeWithText(uiText(R.string.catalog_save_local_action)).assertIsNotEnabled(); compose.onNodeWithText("إلغاء").assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithText(uiText(R.string.catalog_save_local_action)).performClick(); assertEquals(1, saves)
    }
    @Test fun historyRedactionAndDeletedNamesAreVisibleWithoutSensitiveValues() {
        val entry = HistoryDisplayEntry("id","pharmacy",F.pharmacyId,"محفوظ","adminPinVerifier",HistoryValue(null),HistoryValue(null),F.time,null,null,true)
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AqaqeerTheme(ThemeChoice.LIGHT) { HistoryEntryCard(entry,F.catalog(),"Asia/Damascus",emptyMap(),null,true) }
        } }
        compose.onNodeWithText("لا تُعرض قيم بيانات التحقق أو محتوى سجل الصيدلية هنا.").assertExists()
        compose.onAllNodesWithText("PBKDF2",substring = true).assertCountEquals(0)
        compose.onNodeWithText("هجري حسابي مدني:",substring = true).assertExists()
    }
}
