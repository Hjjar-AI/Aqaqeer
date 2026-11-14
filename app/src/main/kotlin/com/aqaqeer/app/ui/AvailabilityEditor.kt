package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
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
@Serializable
internal data class AvailabilitySelection(
    val pharmacyId: String,
    val section: Section,
    val medications: List<Medication>,
    val expected: Map<String, AvailabilityStatus>,
    val bulk: Boolean,
    val id: String = java.util.UUID.randomUUID().toString(),
    val formNames: Map<String, String> = emptyMap(),
    val categoryNames: Map<String, String> = emptyMap(),
)

internal val AvailabilitySelectionSaver = Saver<AvailabilitySelection?, String>(
    save = { it?.let { captured ->
        // Only identity fields/IDs/expected statuses are displayed or submitted. Do not put large notes into the Bundle.
        Json.encodeToString(captured.copy(medications = captured.medications.map { medication ->
            medication.copy(note = null, brand = null)
        }))
    } ?: "" },
    restore = { raw -> runCatching { Json.decodeFromString<AvailabilitySelection>(raw) }.getOrNull() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AvailabilityEditor(selection: AvailabilitySelection, busy: Boolean, message: String?,
    dismiss: () -> Unit, save: (AvailabilityStatus) -> Unit, current: Boolean = true, isSaving: () -> Boolean = { busy }) {
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
                if (!selection.bulk) selection.medications.single().let { medication ->
                    MedicationSummary(medication, prominent = true, form = selection.formNames[medication.formId])
                    Text(uiText(R.string.ui_availability_editor_003, statusLabel(selection.expected.getValue(medication.id))))
                }
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
                    Text(uiText(R.string.ui_bulk_medication_editor_003, MedicationPresentation.easternDigits(changed.toString()), MedicationPresentation.easternDigits(selection.medications.size.toString())))
                    // Include every selected medication, even when hidden by the Home filters.
                    selection.medications.forEach { medication ->
                        HorizontalDivider()
                        MedicationSummary(medication, prominent = true, form = selection.formNames[medication.formId])
                        val before = selection.expected.getValue(medication.id)
                        Text(uiText(R.string.ui_availability_editor_005, statusLabel(before), statusLabel(result)) + if (before == result) uiText(R.string.ui_availability_editor_006) else "")
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
