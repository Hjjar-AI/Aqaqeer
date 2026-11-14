package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Captured IDs and values keep a selector/preview bound to its original section. No credentials. */
internal data class AvailabilitySelection(
    val pharmacyId: String,
    val section: Section,
    val medications: List<Medication>,
    val expected: Map<String, AvailabilityStatus>,
    val bulk: Boolean,
    val id: String = java.util.UUID.randomUUID().toString(),
    val formNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
    val originalFields: Map<String, SelectionFields> = medications.associate { it.id to SelectionFields(it.formId, it.categoryId) },
    val restored: Boolean = false,
) {
    fun resolve(catalog: CatalogSnapshot): AvailabilitySelection {
        if (!restored) return this
        val live = catalog.medications.associateBy { it.id }
        return copy(section = catalog.sections.find { it.id == section.id } ?: section,
            medications = expected.keys.mapNotNull { id -> live[id]?.let { med ->
                val fields = originalFields[id] ?: return@let null
                med.copy(formId = fields.formId, categoryId = fields.categoryId)
            } }, formNames = catalog.forms.associate { it.id to it.nameAr },
            categoryNames = catalog.categories.associate { it.id to it.nameAr }, restored = false)
    }
}

@Serializable internal data class SelectionFields(val formId: String, val categoryId: String)
@Serializable private data class SavedAvailabilitySelection(val pharmacyId: String, val sectionId: String,
    val expected: Map<String, AvailabilityStatus>, val fields: Map<String, SelectionFields>, val bulk: Boolean, val id: String)

internal val AvailabilitySelectionSaver = Saver<AvailabilitySelection?, String>(
    save = { captured -> captured?.let {
        Json.encodeToString(SavedAvailabilitySelection(it.pharmacyId, it.section.id, it.expected,
            it.originalFields, it.bulk, it.id))
    } ?: "" },
    restore = { raw -> runCatching {
        val saved = Json.decodeFromString<SavedAvailabilitySelection>(raw)
        AvailabilitySelection(saved.pharmacyId, Section(saved.sectionId, ""), emptyList(), saved.expected,
            saved.bulk, saved.id, originalFields = saved.fields, restored = true)
    }.getOrNull() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AvailabilityEditor(selection: AvailabilitySelection, busy: Boolean, message: String?,
    dismiss: () -> Unit, save: (AvailabilityStatus) -> Unit, current: Boolean = true, isSaving: () -> Boolean = { busy }) {
    val selectedMedications = remember(selection.medications) { selection.medications.associateBy { it.id } }
    var statusName by rememberSaveable(selection) {
        mutableStateOf<String?>(null)
    }
    var preview by rememberSaveable(selection) { mutableStateOf(false) }
    val status = if (selection.bulk) statusName?.let(AvailabilityStatus::valueOf) else selection.expected.values.single()
    val previewing = selection.bulk && preview
    val savingNow by rememberUpdatedState(isSaving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !savingNow() || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = { if (!savingNow()) dismiss() }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight()).padding(UiSpacing.page),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            SectionHeading(if (previewing) uiText(R.string.ui_availability_editor_001) else uiText(R.string.ui_pharmacy_admin_screen_120))
            Text(uiText(R.string.ui_availability_editor_002, selection.section.nameAr), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                if (!selection.bulk) selection.medications.singleOrNull()?.let { medication ->
                    MedicationSummary(medication, prominent = true, form = selection.formNames[medication.formId])
                    Text(uiText(R.string.ui_availability_editor_003, statusLabel(selection.expected.getValue(medication.id))))
                }
                if (!selection.bulk && selection.medications.isEmpty()) Text(uiText(R.string.logic_missing_medication))
                if (!previewing) {
                    if (!selection.bulk) SupportingText(uiText(R.string.ui_availability_editor_004))
                    RadioChoiceRows(MedicationPresentation.editableStatuses, status, { chosen ->
                        if (!savingNow() && current) {
                            if (selection.bulk) statusName = chosen.name
                            else if (chosen != status) save(chosen)
                        }
                    }, ::statusLabel, enabled = !busy && current, content = { StatusBadge(it) })
                } else {
                    val result = requireNotNull(status)
                    val changed = selection.expected.values.count { it != result }
                    Text(uiText(R.string.ui_bulk_medication_editor_003, MedicationPresentation.easternDigits(changed.toString()), MedicationPresentation.easternDigits(selection.expected.size.toString())))
                    // Include every selected medication, even when hidden by the Home filters.
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = viewportHeight() * 0.55f)) {
                    items(selection.expected.keys.toList(), key = { it }) { id ->
                        val medication = selectedMedications[id]
                        if (medication == null) Text(uiText(R.string.logic_missing_medication) + " · " + bidi(id))
                        else {
                        HorizontalDivider()
                        MedicationSummary(medication, prominent = true, form = selection.formNames[medication.formId])
                        val before = selection.expected.getValue(medication.id)
                        Text(uiText(R.string.ui_availability_editor_005, statusLabel(before), statusLabel(result)) + if (before == result) uiText(R.string.ui_availability_editor_006) else "")
                        }
                    }
                    }
                }
                SupportingText(uiText(R.string.ui_availability_editor_007))
                if (!current) Text(uiText(R.string.ui_availability_editor_008),
                    color = MaterialTheme.colorScheme.error)
                InlineOperationStatus(busy, message)
            }
            ActionGroup {
                if (selection.bulk) Button(enabled = status != null && !busy && current, onClick = {
                    if (!savingNow() && current) {
                        if (!previewing) preview = true else save(requireNotNull(status))
                    }
                }) { Text(if (selection.bulk && !previewing) uiText(R.string.ui_availability_editor_009) else uiText(R.string.ui_pharmacy_admin_screen_132)) }
                if (previewing) TextButton(enabled = !busy, onClick = { if (!savingNow()) preview = false }) { Text(uiText(R.string.ui_availability_editor_010)) }
                TextButton(enabled = !busy, onClick = { if (!savingNow()) dismiss() }) { Text(if (selection.bulk) uiText(R.string.ui_bulk_medication_editor_012) else uiText(R.string.ui_choice_components_004)) }
            }
        }
    }
}
