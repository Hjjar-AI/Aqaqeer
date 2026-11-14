package com.aqaqeer.app.data

/** Null means leave that field unchanged, not clear it. Availability belongs to one captured section. */
data class BulkMedicationChanges(
    val status: AvailabilityStatus? = null,
    val formId: String? = null,
    val categoryId: String? = null,
) {
    val isEmpty: Boolean get() = status == null && formId == null && categoryId == null
}

data class BulkMedicationExpected(val status: AvailabilityStatus, val formId: String, val categoryId: String)

/** Memory-only receipt; undo is a new guarded edit, never a history or remote rollback. */
data class AvailabilityUndo(val pharmacyId: String, val sectionId: String,
    val fields: BulkMedicationChanges, val before: Map<String, BulkMedicationExpected>,
    val after: Map<String, BulkMedicationExpected>, val missingBefore: Set<String>)
