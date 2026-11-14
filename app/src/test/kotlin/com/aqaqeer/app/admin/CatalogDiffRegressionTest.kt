package com.aqaqeer.app.admin

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test

class CatalogDiffRegressionTest {
    private val base = F.catalog()
    private fun diff(next: CatalogSnapshot) = CatalogEdits.diff(base, next, F.editor, F.time, F.publishId)
    @Test fun rowProjectionIncludesOnlyChangedTypedRows() {
        val med = base.medications.single(); val next = base.copy(medications = listOf(med.copy(note = "new")))
        assertEquals(setOf("medication:${med.id}"), CatalogEdits.rows(next, base).keys)
        assertEquals(setOf("medication:${med.id}"), CatalogEdits.rows(base, next).keys)
        assertEquals(listOf("note"), diff(next).map { it.field })
    }
    @Test fun targetSelectionExcludesUnrelatedChangesWithoutLosingRequestedRows() {
        val med = base.medications.single()
        val next = base.copy(medications = listOf(med.copy(note = "new")), pharmacy = base.pharmacy!!.copy(name = "Changed"))
        assertEquals(setOf("medication:${med.id}"), CatalogEdits.rows(next, base, setOf("medication:${med.id}")).keys)
        assertTrue(CatalogEdits.rows(next, base, emptySet()).isEmpty())
    }
    @Test fun addingMedicationCreatesOneIndivisibleEntityOperation() {
        val extra = base.medications.single().copy(id = F.otherPharmacyId, genericEn = "Other")
        val operation = diff(base.copy(medications = base.medications + extra)).single()
        assertEquals("@entity", operation.field); assertEquals(extra.id, operation.targetId)
        assertEquals("null", operation.baseValueJson); assertTrue(operation.intendedValueJson.contains("Other"))
        assertEquals(F.publishId, operation.basePublishId); assertEquals(F.editor.id, operation.authorId)
    }
    @Test fun deletionIncludesLinkedPairAndKeepsSingleTransaction() {
        val operations = diff(base.copy(medications = emptyList(), availability = emptyList()))
        assertEquals(setOf("medication", "availability"), operations.map { it.entityType }.toSet())
        assertEquals(1, operations.map { it.transactionId }.distinct().size)
        assertTrue(operations.all { it.field == "@entity" && it.intendedValueJson == "null" })
    }
    @Test fun reorderedCollectionsCreateNoPendingOperationsOrObservations() {
        val next = base.copy(categories = base.categories.reversed(), forms = base.forms.reversed(), sections = base.sections.reversed(),
            medications = base.medications.reversed(), availability = base.availability.reversed())
        assertTrue(diff(next).isEmpty()); assertTrue(RemoteObservationHistory.changes(base, next, F.time, 2).isEmpty())
        assertTrue(base.sameContents(next))
    }
    @Test fun metadataOnlyChangesAreExcludedFromPendingBusinessFields() {
        val next = base.copy(medications = base.medications.map { it.copy(updatedAt = F.time.plusSeconds(9), updatedBy = F.editor.copy(name = "Other editor")) })
        assertTrue(diff(next).isEmpty()); assertTrue(RemoteObservationHistory.changes(base, next, F.time, 2).isEmpty())
    }
    @Test fun availabilityProjectionUsesCompleteCompositeTarget() {
        val pair = base.availability.single(); val next = base.copy(availability = listOf(pair.copy(status = AvailabilityStatus.OUT)))
        val op = diff(next).single(); assertEquals("${pair.medicationId}/${pair.sectionId}", op.targetId)
        assertEquals("availability", op.entityType); assertEquals("status", op.field)
        assertEquals("\"AVAILABLE\"", op.baseValueJson); assertEquals("\"OUT\"", op.intendedValueJson)
    }
    @Test fun tombstonesWithSameEntityRetainDistinctOperationIdentity() {
        val a = Tombstone("medication", F.otherPharmacyId, F.time, F.publishId)
        val b = a.copy(operationId = F.editor.id)
        val catalog = base.copy(tombstones = listOf(a, b))
        val history = RemoteObservationHistory.changes(base, catalog, F.time, 2)
        assertEquals(2, history.size); assertEquals(2, history.map { it.entityId }.distinct().size)
        assertTrue(history.all { it.entityType == "tombstone" && it.field == "record" })
        assertFalse(catalog.sameContents(base.copy(tombstones = listOf(a))))
        assertTrue(catalog.sameContents(catalog.copy(tombstones = listOf(b, a))))
    }
}
