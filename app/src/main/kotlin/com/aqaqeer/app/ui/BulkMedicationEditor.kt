package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.aqaqeer.app.data.*

private data class BulkFieldOption(val value: String?, val label: String)

@Composable internal fun BulkMedicationEditor(selection: AvailabilitySelection, catalog: CatalogSnapshot,
    busy: Boolean, message: String?, dismiss: () -> Unit, save: (BulkMedicationChanges) -> Unit,
    current: (BulkMedicationChanges) -> Boolean, isSaving: () -> Boolean = { busy }) {
    var statusName by rememberSaveable(selection.id) { mutableStateOf<String?>(null) }
    var formId by rememberSaveable(selection.id) { mutableStateOf<String?>(null) }
    var categoryId by rememberSaveable(selection.id) { mutableStateOf<String?>(null) }
    var preview by rememberSaveable(selection.id, selection.expected.keys.toList()) { mutableStateOf(false) }
    val status = statusName?.let { name -> MedicationPresentation.editableStatuses.find { it.name == name } }
    val changes = BulkMedicationChanges(status, formId, categoryId)
    val fieldsValid = !changes.isEmpty && (statusName == null || status != null) &&
        (formId == null || catalog.forms.any { it.id == formId }) &&
        (categoryId == null || catalog.categories.any { it.id == categoryId })
    val contextValid = selection.medications.isNotEmpty() &&
        selection.medications.map { it.id }.toSet() == selection.expected.keys &&
        catalog.pharmacy?.pharmacyId == selection.pharmacyId &&
        catalog.sections.any { it.id == selection.section.id && it.isActive } && current(changes)
    val savingNow by rememberUpdatedState(isSaving)
    fun changed(med: Medication) =
        (changes.status != null && selection.expected[med.id] != changes.status) ||
            (changes.formId != null && med.formId != changes.formId) ||
            (changes.categoryId != null && med.categoryId != changes.categoryId)
    val unchanged = BulkFieldOption(null, uiText(R.string.ui_bulk_medication_editor_001))
    val statuses = listOf(unchanged) + MedicationPresentation.editableStatuses.map { BulkFieldOption(it.name, statusLabel(it)) }
    val forms = listOf(unchanged) + catalog.forms.map { BulkFieldOption(it.id, it.nameAr) }
    val categories = listOf(unchanged) + catalog.categories.map { BulkFieldOption(it.id, it.nameAr) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        if (!preview) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                Box(Modifier.weight(1f)) {
                    ChoicePicker(uiText(R.string.ui_home_screen_024), statuses, statuses.find { it.value == statusName }, { it.label }, enabled = !busy) {
                        if (!savingNow()) statusName = it.value
                    }
                }
                Box(Modifier.weight(1f)) {
                    ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_074), forms, forms.find { it.value == formId }, { it.label }, enabled = !busy) {
                        if (!savingNow()) formId = it.value
                    }
                }
                Box(Modifier.weight(1f)) {
                    ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_073), categories, categories.find { it.value == categoryId }, { it.label }, enabled = !busy) {
                        if (!savingNow()) categoryId = it.value
                    }
                }
            }
            if (formId != null || categoryId != null) SupportingText(uiText(R.string.ui_bulk_medication_editor_002))
        } else {
            val count = selection.medications.count(::changed)
            Text(uiText(R.string.ui_bulk_medication_editor_003, MedicationPresentation.easternDigits(count.toString()), MedicationPresentation.easternDigits(selection.medications.size.toString())))
            selection.medications.forEach { med ->
                HorizontalDivider()
                MedicationSummary(med, prominent = true, form = selection.formNames[med.formId])
                changes.status?.let { result ->
                    Text(uiText(R.string.ui_bulk_medication_editor_004, statusLabel(selection.expected.getValue(med.id)), statusLabel(result)))
                }
                changes.formId?.let { target ->
                    Text(uiText(R.string.ui_bulk_medication_editor_006, selection.formNames[med.formId] ?: uiText(R.string.ui_bulk_medication_editor_005), catalog.forms.find { it.id == target }?.nameAr ?: uiText(R.string.ui_bulk_medication_editor_005)))
                }
                changes.categoryId?.let { target ->
                    Text(uiText(R.string.ui_bulk_medication_editor_007, selection.categoryNames[med.categoryId] ?: uiText(R.string.ui_bulk_medication_editor_005), catalog.categories.find { it.id == target }?.nameAr ?: uiText(R.string.ui_bulk_medication_editor_005)))
                }
                if (!changed(med)) SupportingText(uiText(R.string.ui_bulk_medication_editor_001))
            }
        }
        if (!contextValid && selection.medications.isNotEmpty()) Text(uiText(R.string.ui_bulk_medication_editor_008),
            color = MaterialTheme.colorScheme.error)
        if (preview && !fieldsValid) Text(uiText(R.string.ui_bulk_medication_editor_009),
            color = MaterialTheme.colorScheme.error)
        InlineOperationStatus(busy, message)
        ActionGroup {
            Button(enabled = fieldsValid && contextValid && !busy, onClick = {
                if (!savingNow() && fieldsValid && contextValid) {
                    if (preview) save(changes) else preview = true
                }
            }) { Text(if (preview) uiText(R.string.ui_pharmacy_admin_screen_109) else uiText(R.string.ui_bulk_medication_editor_010)) }
            if (preview) TextButton(enabled = !busy,
                onClick = { if (!savingNow()) preview = false }) { Text(uiText(R.string.ui_bulk_medication_editor_011)) }
            TextButton(enabled = !busy, onClick = { if (!savingNow()) dismiss() }) { Text(uiText(R.string.ui_bulk_medication_editor_012)) }
        }
    }
}
