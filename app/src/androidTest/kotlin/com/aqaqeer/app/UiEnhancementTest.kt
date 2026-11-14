package com.aqaqeer.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class UiEnhancementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fieldErrorsWaitForInteractionAndDisappearAfterCorrection() {
        var value by mutableStateOf("")
        var submitted by mutableStateOf(false)
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { Column {
            FormField("اسم مطلوب", value, { value = it }, required = true, submitted = submitted)
            FormField("حقل آخر", "", {}, required = false)
            Button(onClick = { submitted = true }) { Text("إرسال") }
        } } }
        compose.onNodeWithText("أكمل هذا الحقل المطلوب").assertDoesNotExist()
        compose.onNodeWithTag("اسم مطلوب").performClick()
        compose.onNodeWithTag("حقل آخر").performClick()
        compose.onNodeWithText("أكمل هذا الحقل المطلوب").assertExists()
        compose.onNodeWithTag("اسم مطلوب").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.onNodeWithTag("اسم مطلوب").performTextInput("اسم صحيح")
        compose.onNodeWithText("أكمل هذا الحقل المطلوب").assertDoesNotExist()
        compose.onNodeWithTag("اسم مطلوب").performTextClearance()
        compose.onNodeWithText("إرسال").performClick()
        compose.onNodeWithText("أكمل هذا الحقل المطلوب").assertExists()
    }

    @Test fun bulkCheckboxIsOneControlAndDoesNotNavigate() {
        val c = F.catalog(); val med = c.medications.single()
        var selected by mutableStateOf(false); var opened = 0
        compose.setContent { AqaqeerTheme(ThemeChoice.DARK) {
            MedicationCard(c, med, c.sections.first().id, HomePreferences(), LocalDate.of(2026,11,12), null,
                selected, { selected = !selected }, { opened++ })
        } }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertCountEquals(1)
        compose.onNodeWithTag("select_${med.id}").performClick().assertIsOn()
        compose.runOnIdle { assertTrue(selected); assertEquals(0,opened) }
        compose.onNodeWithText(med.nameEn, substring = true, useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1,opened) }
    }

    private fun many(): CatalogSnapshot {
        val c = F.catalog(); val med = c.medications.single()
        return c.copy(sections = c.sections + c.sections.first().copy(id = "second", nameAr = "القسم الثاني"),
            medications = (0..29).map { med.copy(id = "item$it", nameEn = "Item %02d".format(it)) }, availability = emptyList())
    }

    @Test fun selectionStaysPinnedAndPreviewIncludesHiddenSelection() {
        val c = many(); var state by mutableStateOf(AppUiState())
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) {
            HomeScreen(CatalogState.Ready(c,false,admin=true),state,F.time,
                { state = state.copy(sectionId=it) }, {}, {}, {}, {}, {}, {},
                saveAvailability = { _,_,_ -> })
        } }
        compose.onNodeWithTag("home").performScrollToNode(hasText("تحديد أدوية لتعديل التوافر"))
        compose.onNodeWithText("تحديد أدوية لتعديل التوافر").performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("select_item0"))
        compose.onNodeWithTag("select_item0").performScrollTo().performClick()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Item 29"))
        compose.onNodeWithTag("bulk_selection_bar").assertIsDisplayed()
        compose.onNodeWithText("اختيار الحالة والمعاينة").assertIsEnabled()
        compose.runOnIdle { state = state.copy(appliedQuery = "no match") }
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("bulk_selection_bar"))
        compose.onNodeWithText("اختيار الحالة والمعاينة").performClick()
        compose.onNode(hasText("غير متوفر") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithText("معاينة جميع الأدوية المحددة").performClick()
        compose.onNodeWithText("Item 00", substring = true).assertExists()
    }

    @Test fun sectionChangeClearsSelectionAndRestrictedReaderHasNoBulkAction() {
        val c = many(); var state by mutableStateOf(AppUiState()); var admin by mutableStateOf(true)
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) {
            HomeScreen(CatalogState.Ready(c,false,admin=admin),state,F.time,
                { state = state.copy(sectionId=it) }, {}, {}, {}, {}, {}, {}, saveAvailability = { _,_,_ -> })
        } }
        compose.onNodeWithTag("home").performScrollToNode(hasText("تحديد أدوية لتعديل التوافر"))
        compose.onNodeWithText("تحديد أدوية لتعديل التوافر").performClick()
        compose.runOnIdle { state = state.copy(sectionId="second") }
        compose.onNodeWithTag("bulk_selection_bar").assertDoesNotExist()
        compose.runOnIdle { admin = false }
        compose.onNodeWithText("تحديد أدوية لتعديل التوافر").assertDoesNotExist()
    }

    @Test fun filterSummaryCountsSelectionsAndResetClearsBothGroups() {
        var filters by mutableStateOf(HomeFilters(setOf(AvailabilityStatus.OUT, AvailabilityStatus.UNKNOWN),setOf("a")))
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { FilterSummary(filters) { filters=HomeFilters() } } }
        compose.onNodeWithText("الحالات المحددة: ٢ · التصنيفات المحددة: ١").assertIsDisplayed()
        compose.onNodeWithTag("clear_filters").performClick()
        compose.onNodeWithText("الحالات المحددة: ٠ · التصنيفات المحددة: ٠").assertIsDisplayed()
        compose.onNodeWithTag("clear_filters").assertDoesNotExist()
    }

    @Test fun longContentAndActionsRemainReachableAcrossThemeFontAndViewportMatrix() {
        var theme by mutableStateOf(ThemeChoice.LIGHT); var scale by mutableFloatStateOf(1f)
        var width by mutableIntStateOf(320); var height by mutableIntStateOf(600)
        val longName = "دواء ذو اسم عربي طويل مع EnglishMedicationNameWithoutSpaces12345678901234567890"
        compose.setContent {
            BoxWithConstraints {
                val original = LocalDensity.current
                val factor = minOf(1f,maxWidth.value/width,maxHeight.value/height)
                CompositionLocalProvider(LocalDensity provides Density(original.density*factor,scale)) {
                    AqaqeerTheme(theme) { Box(Modifier.size(width.dp,height.dp)) { FormPage {
                        SectionHeading("عنوان القسم")
                        MedicationNameLines(longName,longName,NameDisplay.BOTH)
                        LabeledValue("المعرّف", "1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz")
                        FormField("الاسم", "", {}, required=true)
                        BulkSelectionBar("القسم الرئيسي",1,true,false,{},{})
                        ActionGroup {
                            Button(onClick={}) { Text("حفظ التغييرات") }
                            TextButton(onClick={}) { Text("إلغاء") }
                        }
                    } } }
                }
            }
        }
        for (t in listOf(ThemeChoice.LIGHT,ThemeChoice.DARK)) for (f in listOf(1f,1.3f,2f))
            for ((w,h) in listOf(320 to 600,640 to 320)) {
                compose.runOnIdle { theme=t;scale=f;width=w;height=h }
                compose.onNodeWithText("حفظ التغييرات").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("إلغاء").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("اختيار الحالة والمعاينة").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("الاسم").performScrollTo().assertIsDisplayed()
                compose.onAllNodesWithText(longName,substring=true).assertCountEquals(2)
            }
    }

    @Test fun longDialogPreviewScrollsWhileSaveAndCancelStayReachable() {
        val c=many();var saves=0;var cancellations=0
        val selection=AvailabilitySelection(F.pharmacyId,c.sections.first(),c.medications,
            c.medications.associate { it.id to AvailabilityStatus.UNKNOWN },true)
        compose.setContent {
            val d=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density,2f)) {
                AqaqeerTheme(ThemeChoice.DARK) { AvailabilityEditor(selection,false,null,{cancellations++},{saves++}) }
            }
        }
        compose.onNodeWithText("غير متوفر").performScrollTo().performClick()
        compose.onNodeWithText("معاينة جميع الأدوية المحددة").performClick()
        compose.onNodeWithText("Item 29",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("حفظ").assertIsDisplayed().performClick()
        compose.onNodeWithText("إلغاء").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1,saves);assertEquals(1,cancellations) }
    }

    @Test fun busySelectionBarPreventsPreviewAndCancellation() {
        var count=0
        compose.setContent { AqaqeerTheme(ThemeChoice.DARK) { BulkSelectionBar("قسم",1,true,true,{count++},{count++}) } }
        compose.onNodeWithText("اختيار الحالة والمعاينة").assertIsNotEnabled()
        compose.onNodeWithText("إلغاء التحديد").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0,count) }
    }
}
