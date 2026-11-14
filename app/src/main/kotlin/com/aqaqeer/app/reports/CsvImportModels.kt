package com.aqaqeer.app.reports

import com.aqaqeer.app.data.*
import java.time.Instant
import java.util.UUID

internal enum class CsvAction { CREATE, CREATE_ANOTHER, UPDATE, SKIP }
internal data class CsvDecision(val action: CsvAction? = null, val targetId: String? = null,
    val selected: Boolean = false, val clear: Set<String> = emptySet(), val approveTaxonomy: Boolean = false,
    val createdId: String = UUID.randomUUID().toString())
internal data class CsvFieldChange(val field: String, val before: String, val after: String)
internal data class CsvRowPlan(val medication: Medication, val category: Category?, val form: MedicationForm?,
    val statuses: Map<String, AvailabilityStatus>) {
    fun apply(catalog: CatalogSnapshot, author: EditorRef, now: Instant): CatalogSnapshot {
        val old = catalog.medications.find { it.id == medication.id }
        val changedPairs = statuses.filter { (section, status) -> catalog.status(medication.id, section) != status }
        return catalog.copy(categories = catalog.categories + listOfNotNull(category), forms = catalog.forms + listOfNotNull(form),
            medications = catalog.medications.filterNot { it.id == medication.id } + medication.copy(
                createdAt = old?.createdAt ?: now, updatedAt = now, updatedBy = author),
            availability = catalog.availability.filterNot { it.medicationId == medication.id && it.sectionId in changedPairs } +
                changedPairs.map { (section, status) -> Availability(medication.id, section, status, now, author) })
    }
}
internal data class CsvPreviewRow(val record: CsvRecord, val decision: CsvDecision, val plan: CsvRowPlan? = null,
    val changes: List<CsvFieldChange> = emptyList(), val duplicates: List<String> = emptyList(),
    val warnings: List<String> = emptyList(), val error: String? = null, val duplicateRows: List<Int> = emptyList())
internal data class CsvImportSession(val id: String, val pharmacyId: String, val baseline: CatalogSnapshot,
    val table: CsvTable, val rows: List<CsvPreviewRow>,
    val duplicateIndex: Map<MedicationDuplicates.Key, List<String>> = MedicationDuplicates.index(baseline.medications),
    val candidates: Map<Int, CsvRowPlan?> = emptyMap(),
    val identityKeys: Map<Int, Set<MedicationDuplicates.Key>> = emptyMap())
internal enum class CsvOutcome { CREATED, UPDATED, UNCHANGED, SKIPPED, FAILED }
internal data class CsvRowResult(val number: Int, val outcome: CsvOutcome, val warning: String = "", val error: String = "")
internal data class CsvImportState(val session: CsvImportSession? = null,
    val results: Map<Int, CsvRowResult> = emptyMap(), val finished: Boolean = false)

internal enum class CsvSaveKind { TEMPLATE, RESULTS, RETRY }
