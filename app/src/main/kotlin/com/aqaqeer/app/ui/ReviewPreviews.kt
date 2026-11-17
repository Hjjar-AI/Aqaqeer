package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
