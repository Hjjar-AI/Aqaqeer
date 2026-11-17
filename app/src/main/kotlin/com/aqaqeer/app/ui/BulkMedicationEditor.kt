package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.aqaqeer.app.data.*

private data class BulkFieldOption(val value: String?, val label: String)

@Composable internal fun BulkMedicationEditor(selection: AvailabilitySelection, catalog: CatalogSnapshot,
    busy: Boolean, message: String?, dismiss: () -> Unit, save: (BulkMedicationChanges) -> Unit,
    current: (BulkMedicationChanges) -> Boolean, isSaving: () -> Boolean = { busy }) {
    val uiText = rememberUiText()
    val selectedMedications = remember(selection.medications) { selection.medications.associateBy { it.id } }
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
    val unchanged = BulkFieldOption(null, uiText(R.string.availability_bulk_unchanged_label))
    val statuses = listOf(unchanged) + MedicationPresentation.editableStatuses.map { BulkFieldOption(it.name, statusLabel(it, text = uiText)) }
    val forms = listOf(unchanged) + catalog.forms.map { BulkFieldOption(it.id, it.nameAr) }
    val categories = listOf(unchanged) + catalog.categories.map { BulkFieldOption(it.id, it.nameAr) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        if (!preview) {
            WrappingContent {
                statuses.forEach { option -> AppChip(selected = option.value == statusName, enabled = !busy,
                    onClick = { if (!savingNow()) statusName = option.value }, label = { Text(option.label) }) }
            }
            AdaptivePickers(first = {
                ChoicePicker(uiText(R.string.catalog_dosage_form_label), forms, forms.find { it.value == formId }, { it.label }, enabled = !busy, itemKey = { option -> option.value?.let { "value:$it" } ?: "unchanged" }) {
                    if (!savingNow()) formId = it.value
                }
            }, second = {
                ChoicePicker(uiText(R.string.catalog_category_label), categories, categories.find { it.value == categoryId }, { it.label }, enabled = !busy, itemKey = { option -> option.value?.let { "value:$it" } ?: "unchanged" }) {
                    if (!savingNow()) categoryId = it.value
                }
            })
            if (formId != null || categoryId != null) SupportingText(uiText(R.string.availability_bulk_shared_fields_help))
        } else {
            val count = selection.medications.count(::changed)
            Text(uiText(R.string.availability_bulk_change_count_format, MedicationPresentation.displayDigits(count.toString()), MedicationPresentation.displayDigits(selection.expected.size.toString())))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = viewportHeight() * 0.55f)) {
            items(selection.expected.keys.toList(), key = { it }) { id ->
                val med = selectedMedications[id]
                if (med == null) Text(uiText(R.string.logic_missing_medication) + " · " + bidi(id))
                else {
                HorizontalDivider()
                MedicationSummary(med, prominent = true, form = selection.formNames[med.formId])
                changes.status?.let { result ->
                    Text(uiText(R.string.availability_bulk_status_change_format, statusLabel(selection.expected.getValue(med.id), text = uiText), statusLabel(result, text = uiText)))
                }
                changes.formId?.let { target ->
                    Text(uiText(R.string.availability_bulk_form_change_format, selection.formNames[med.formId] ?: uiText(R.string.availability_bulk_unavailable_label), catalog.forms.find { it.id == target }?.nameAr ?: uiText(R.string.availability_bulk_unavailable_label)))
                }
                changes.categoryId?.let { target ->
                    Text(uiText(R.string.availability_bulk_category_change_format, selection.categoryNames[med.categoryId] ?: uiText(R.string.availability_bulk_unavailable_label), catalog.categories.find { it.id == target }?.nameAr ?: uiText(R.string.availability_bulk_unavailable_label)))
                }
                if (!changed(med)) SupportingText(uiText(R.string.availability_bulk_unchanged_label))
                }
            }
            }
        }
        if (!contextValid && selection.medications.isNotEmpty()) Text(uiText(R.string.availability_bulk_stale_selection_error),
            color = MaterialTheme.colorScheme.error)
        if (preview && !fieldsValid) Text(uiText(R.string.availability_bulk_invalid_choices_error),
            color = MaterialTheme.colorScheme.error)
        InlineOperationStatus(busy, message)
        ActionGroup {
            AppButton(enabled = fieldsValid && contextValid && !busy, onClick = {
                if (!savingNow() && fieldsValid && contextValid) {
                    if (preview) save(changes) else preview = true
                }
            }) { Text(if (preview) uiText(R.string.catalog_save_local_action) else uiText(R.string.availability_bulk_preview_action)) }
            if (preview) AppButton(variant = AppButtonVariant.QUIET, enabled = !busy,
                onClick = { if (!savingNow()) preview = false }) { Text(uiText(R.string.availability_bulk_change_choices_action)) }
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { if (!savingNow()) dismiss() }) { Text(uiText(R.string.availability_bulk_cancel_action)) }
        }
    }
}
