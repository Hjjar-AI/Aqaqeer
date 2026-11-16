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


/** Existing medications expose immediate per-section changes without a details/sheet navigation step. */
@Composable internal fun MedicationAvailabilityControls(catalog: CatalogSnapshot, medication: Medication,
    busy: Boolean, isSaving: () -> Boolean, save: (AvailabilitySelection, AvailabilityStatus) -> Unit) {
    val pharmacyId = catalog.pharmacy?.pharmacyId ?: return
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_120)) {
        SupportingText(uiText(R.string.edit_availability_immediate_hint))
        if (catalog.sections.isEmpty()) SupportingText(uiText(R.string.edit_availability_no_sections))
        catalog.sections.forEach { section ->
            key(section.id) {
                val current = catalog.status(medication.id, section.id)
                Text(section.nameAr + if (section.isActive) "" else uiText(R.string.ui_aqaqeer_app_030),
                    style = MaterialTheme.typography.titleSmall)
                if (section.isActive) WrappingContent {
                    if (current == AvailabilityStatus.UNKNOWN) StatusBadge(current)
                    MedicationPresentation.editableStatuses.forEach { status ->
                        FilterChip(selected = current == status, enabled = !busy,
                            onClick = {
                                if (!isSaving() && status != current) save(
                                    AvailabilitySelection(pharmacyId, section, listOf(medication),
                                        mapOf(medication.id to current), bulk = false), status)
                            }, label = { Text(statusLabel(status)) },
                            modifier = Modifier.heightIn(min = UiSpacing.touch).semantics {
                                contentDescription = section.nameAr + " · " + statusLabel(status)
                            })
                    }
                } else StatusBadge(current)
            }
        }
    }
}
