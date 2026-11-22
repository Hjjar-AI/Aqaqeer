package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.aqaqeer.app.data.*
import java.time.Instant
import java.time.LocalDate

@Preview(name = "Light RTL", locale = "ar", widthDp = 360, fontScale = 1f)
@Composable private fun ReviewLightPreview() = ReviewPreview(ThemeChoice.LIGHT)
@Preview(name = "Dark RTL large text", locale = "ar", widthDp = 360, fontScale = 1.6f)
@Composable private fun ReviewDarkPreview() = ReviewPreview(ThemeChoice.DARK)
@Preview(name = "AMOLED RTL large text", locale = "ar", widthDp = 600, fontScale = 1.6f)
@Composable private fun ReviewAmoledPreview() = ReviewPreview(ThemeChoice.AMOLED)
@Preview(name = "Expanded RTL", locale = "ar", widthDp = 600, fontScale = 1f)
@Composable private fun ReviewExpandedPreview() = ReviewPreview(ThemeChoice.LIGHT, CardLayout.EXPANDED)

@Composable private fun ReviewPreview(theme: ThemeChoice, layout: CardLayout = CardLayout.COMPACT) {
    val form = CatalogSeeds.forms.first()
    val medication = Medication("00000000-0000-4000-8000-000000000001", strength = "500 mg", formId = form.id,
        categoryId = CatalogSeeds.UNCATEGORIZED_ID, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        genericAr = "باراسيتامول", genericEn = "Paracetamol", nameEn = "Panadol")
    AqaqeerTheme(theme) {
        Surface {
            Column(Modifier.padding(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                MedicationCard(MedicationRowUi(medication, form, "مسكنات", AvailabilityStatus.OUT, emptyList(), true, true),
                    "preview", HomePreferences(layout = layout), LocalDate.of(2026, 11, 16), onQuickEdit = {},
                    bulkSelected = false, toggleSelection = {}, onClick = {}, unpublished = true, reserveSelectionSpace = true)
                NoticeStrip(Notice("يمكن تحديث البيانات الآن", NoticeSeverity.WARNING, "تحديث الآن", {}))
                SettingsGroup("عرض القائمة", initiallyExpanded = true) { CardLayoutPicker(layout) {} }
            }
        }
    }
}

@Preview(name = "Search RTL narrow", locale = "ar", widthDp = 360, heightDp = 220)
@Preview(name = "Search RTL 2x", locale = "ar", widthDp = 360, heightDp = 320, fontScale = 2f)
@Composable private fun SearchControlsPreview() {
    AqaqeerTheme(ThemeChoice.LIGHT) {
        Surface {
            Column(Modifier.padding(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                listOf(false, true).forEach { compact ->
                    HomeSearchControls(androidx.compose.ui.text.input.TextFieldValue("باراسيتامول"), {}, true,
                        HomeFilters(fetchedOnly = true), 1, 20, {}, {}, compact = compact,
                        showFetchedChip = true)
                }
            }
        }
    }
}

@Preview(name = "Bulk RTL narrow 2x", locale = "ar", widthDp = 360, fontScale = 2f)
@Composable private fun BulkBarPreview() {
    AqaqeerTheme(ThemeChoice.LIGHT) {
        HomeBulkBar(HomeBulkPresentation("القسم الرئيسي للأدوية", 123, true, false, 12), {}, {}, {}, {})
    }
}

@Preview(name = "Drawer RTL", locale = "ar", widthDp = 360, heightDp = 800)
@Preview(name = "Drawer RTL 2x", locale = "ar", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable private fun DrawerPreview() {
    AqaqeerTheme(ThemeChoice.LIGHT) {
        HomeDrawer(previewReady(), HomeFilters(), false, {}, {}, {})
    }
}

@Preview(name = "Welcome RTL 2x", locale = "ar", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable private fun WelcomePreview() {
    AqaqeerTheme(ThemeChoice.LIGHT) {
        Surface { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            WelcomeScreen({}, {}, {}, {})
        } }
    }
}

@Preview(name = "List detail RTL 840", locale = "ar", widthDp = 840, heightDp = 800)
@Composable private fun DualPanePreview() {
    val ready = previewReady()
    val medication = ready.catalog.medications.single()
    AqaqeerTheme(ThemeChoice.LIGHT) {
        Surface {
            HomeDetailLayout(true, true,
                list = { Column(Modifier.padding(UiSpacing.page)) {
                    HomeSearchControls(androidx.compose.ui.text.input.TextFieldValue(""), {}, false, HomeFilters(), 1, 1, {}, {}, showFetchedChip = true)
                    MedicationCard(MedicationRowUi(medication, ready.catalog.forms.first(), "مسكنات", AvailabilityStatus.AVAILABLE, emptyList(), false, false),
                        "preview-section", HomePreferences(), LocalDate.of(2026, 11, 22), null, null, {}, {})
                } },
                detail = { Column(Modifier.padding(UiSpacing.page)) { MedicationTitle(medication) } },
                detailHeader = { Text("تفاصيل الدواء", Modifier.padding(UiSpacing.related)) })
        }
    }
}

/** Public illustrative data only; never initializes a repository, session or credential store. */
private fun previewReady(): CatalogState.Ready {
    val form = CatalogSeeds.forms.first()
    return CatalogState.Ready(CatalogSnapshot(
        Pharmacy("preview", "صيدلية العرض", DEFAULT_PRESENTATION_TIMEZONE, PinVerifier("preview", 200_000, "preview")),
        listOf(Category(CatalogSeeds.UNCATEGORIZED_ID, "غير مصنف", isSystem = true)), listOf(form),
        listOf(Section("preview-section", "القسم الرئيسي للأدوية")),
        listOf(Medication("preview-medication", strength = "500 mg", formId = form.id,
            categoryId = CatalogSeeds.UNCATEGORIZED_ID, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            genericAr = "باراسيتامول", genericEn = "Paracetamol"))), isDemo = true)
}
