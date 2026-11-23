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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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


/** Initial choices are drafts until the new medication and all its availability rows commit together. */
internal val InitialAvailabilitySaver = Saver<Map<String, AvailabilityStatus>, ArrayList<String>>(
    save = { choices -> ArrayList(choices.flatMap { (id, status) -> listOf(id, status.name) }) },
    restore = { saved -> runCatching {
        require(saved.size % 2 == 0)
        saved.chunked(2).associate { (id, status) -> id to AvailabilityStatus.valueOf(status) }
    }.getOrNull() },
)

@Composable internal fun NewMedicationAvailabilityControls(catalog: CatalogSnapshot,
    choices: Map<String, AvailabilityStatus>, busy: Boolean, reset: () -> Unit,
    change: (String, AvailabilityStatus) -> Unit) {
    val uiText = rememberUiText()
    GroupedCard(title = uiText(R.string.new_availability_title)) {
        SupportingText(uiText(R.string.new_availability_hint))
        if (catalog.sections.isEmpty()) SupportingText(uiText(R.string.edit_availability_no_sections))
        catalog.sections.forEach { section ->
            key(section.id) {
                val current = choices[section.id] ?: AvailabilityStatus.AVAILABLE
                SectionAvailabilityChoices(section, current, busy) { status ->
                    if (!busy) change(section.id, status)
                }
            }
        }
        if (choices.isNotEmpty()) AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = reset) {
            Text(uiText(R.string.new_availability_reset))
        }
    }
}

/** Existing medications expose immediate per-section changes without a details/sheet navigation step. */
@Composable internal fun MedicationAvailabilityControls(catalog: CatalogSnapshot, medication: Medication,
    busy: Boolean, isSaving: () -> Boolean, save: (AvailabilitySelection, AvailabilityStatus) -> Unit) {
    val uiText = rememberUiText()
    val pharmacyId = catalog.pharmacy?.pharmacyId ?: return
    GroupedCard(title = uiText(R.string.availability_edit_action)) {
        SupportingText(uiText(R.string.edit_availability_immediate_hint))
        if (catalog.sections.isEmpty()) SupportingText(uiText(R.string.edit_availability_no_sections))
        catalog.sections.forEach { section ->
            key(section.id) {
                val current = catalog.status(medication.id, section.id)
                SectionAvailabilityChoices(section, current, busy) { status ->
                    if (!isSaving() && status != current) save(
                        AvailabilitySelection(pharmacyId, section, listOf(medication),
                            mapOf(medication.id to current), bulk = false), status)
                }
            }
        }
    }
}

@Composable private fun SectionAvailabilityChoices(section: Section, current: AvailabilityStatus,
    busy: Boolean, choose: (AvailabilityStatus) -> Unit) {
    val uiText = rememberUiText()
    Text(section.nameAr + if (section.isActive) "" else uiText(R.string.catalog_closed_section_suffix),
        style = MaterialTheme.typography.titleSmall)
    if (section.isActive) WrappingContent {
        MedicationPresentation.editableStatuses.forEach { status ->
            AppChip(selected = current == status, enabled = !busy,
                onClick = { choose(status) }, label = { Text(statusLabel(status, text = uiText)) },
                modifier = Modifier.heightIn(min = UiSpacing.touch).semantics {
                    contentDescription = section.nameAr + " · " + statusLabel(status, text = uiText)
                })
        }
    } else StatusBadge(current)
}
