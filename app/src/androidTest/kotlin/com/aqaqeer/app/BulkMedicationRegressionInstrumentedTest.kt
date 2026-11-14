package com.aqaqeer.app

import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.maintenance.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Offline production commands against an isolated SQLCipher profile; no Telegram or app install mutation. */
class BulkMedicationRegressionInstrumentedTest {
    private lateinit var p: SuiteProfile
    @Before fun setup(): Unit = runBlocking { Assume.assumeFalse(BuildConfig.IS_DEMO); p = SuiteProfile(); p.create() }
    @After fun cleanup() { if (::p.isInitialized) p.close() }
    private suspend fun section() = p.ready().catalog.sections.single().id
    private suspend fun expected(vararg meds: Medication): Map<String, BulkMedicationExpected> {
        val catalog = p.ready().catalog; val section = section()
        return meds.associate { med -> med.id to BulkMedicationExpected(catalog.status(med.id, section), med.formId, med.categoryId) }
    }
    private suspend fun pharmacy() = p.ready().catalog.pharmacy!!.pharmacyId
    private suspend fun state() = p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }

    @Test fun statusAndFormCommitTogetherAndUndoRestoresBoth(): Unit = runBlocking {
        val a = p.medication("A"); val b = p.medication("B"); val section = section()
        val receipt = p.admin.bulkMedicationChanges(setOf(a.id, b.id), section,
            BulkMedicationChanges(AvailabilityStatus.OUT, CatalogSeeds.TABLET_FORM_ID), expected(a, b), pharmacy())!!
        val changed = p.ready().catalog
        assertTrue(changed.medications.all { it.formId == CatalogSeeds.TABLET_FORM_ID })
        assertTrue(changed.medications.all { changed.status(it.id, section) == AvailabilityStatus.OUT })
        assertEquals(setOf(a.id, b.id), receipt.before.keys)
        p.admin.undoAvailability(receipt)
        val restored = p.ready().catalog
        assertTrue(restored.medications.all { it.formId == a.formId && restored.status(it.id, section) == AvailabilityStatus.AVAILABLE })
    }
    @Test fun unchangedRequestedValuesCreateNoReceiptHistoryOrPending(): Unit = runBlocking {
        val a = p.medication(); val before = state()
        assertNull(p.admin.bulkMedicationChanges(setOf(a.id), section(), BulkMedicationChanges(status = AvailabilityStatus.AVAILABLE), expected(a), pharmacy()))
        assertEquals(before, state())
    }
    @Test fun staleSelectedFieldRollsBackEveryMedication(): Unit = runBlocking {
        val a = p.medication("A"); val b = p.medication("B"); val expected = expected(a, b)
        p.admin.availability(b.id, section(), AvailabilityStatus.LOW); val before = state()
        assertThrows(AdminProblem::class.java) { runBlocking {
            p.admin.bulkMedicationChanges(setOf(a.id, b.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected, pharmacy())
        } }
        assertEquals(before, state())
    }
    @Test fun changedUncheckedFieldDoesNotRejectOrOverwriteStatusEdit(): Unit = runBlocking {
        val a = p.medication(); val expected = expected(a)
        p.admin.saveMedication(a.copy(formId = CatalogSeeds.TABLET_FORM_ID, note = "concurrent note"))
        p.admin.bulkMedicationChanges(setOf(a.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected, pharmacy())
        val actual = p.ready().catalog.medications.single()
        assertEquals(CatalogSeeds.TABLET_FORM_ID, actual.formId); assertEquals("concurrent note", actual.note)
    }
    @Test fun deletedSelectedTargetCannotResurrectDuringBulkEdit(): Unit = runBlocking {
        val a = p.medication("A"); val b = p.medication("B"); val expected = expected(a, b)
        p.admin.delete("medication", b.id); val before = state()
        assertThrows(AdminProblem::class.java) { runBlocking {
            p.admin.bulkMedicationChanges(setOf(a.id, b.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected, pharmacy())
        } }
        assertEquals(before, state())
    }
    @Test fun undoRejectsStaleSelectedFieldAtomically(): Unit = runBlocking {
        val a = p.medication("A"); val b = p.medication("B")
        val receipt = p.admin.bulkMedicationChanges(setOf(a.id, b.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected(a, b), pharmacy())!!
        p.admin.availability(b.id, section(), AvailabilityStatus.LOW); val before = state()
        assertThrows(AdminProblem::class.java) { runBlocking { p.admin.undoAvailability(receipt) } }
        assertEquals(before, state())
    }
    @Test fun undoPreservesConcurrentUncheckedFields(): Unit = runBlocking {
        val a = p.medication()
        val receipt = p.admin.bulkMedicationChanges(setOf(a.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected(a), pharmacy())!!
        p.admin.saveMedication(a.copy(note = "keep", formId = CatalogSeeds.TABLET_FORM_ID))
        p.admin.undoAvailability(receipt)
        val actual = p.ready().catalog.medications.single()
        assertEquals("keep", actual.note); assertEquals(CatalogSeeds.TABLET_FORM_ID, actual.formId)
        assertEquals(AvailabilityStatus.AVAILABLE, p.ready().catalog.status(a.id, section()))
    }
    @Test fun undoRestoresMissingLegacyPairAsAbsence(): Unit = runBlocking {
        val a = p.medication(); p.admin.transaction { it.clearAvailability() }
        val receipt = p.admin.bulkMedicationChanges(setOf(a.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected(a), pharmacy())!!
        assertEquals(setOf(a.id), receipt.missingBefore)
        p.admin.undoAvailability(receipt)
        assertNull(p.ready().catalog.availability(a.id, section()))
        assertEquals(AvailabilityStatus.UNKNOWN, p.ready().catalog.status(a.id, section()))
    }
    @Test fun wrongPharmacyRejectsBeforeAnyMutation(): Unit = runBlocking {
        val a = p.medication(); val expected = expected(a); val before = state()
        assertThrows(AdminProblem::class.java) { runBlocking {
            p.admin.bulkMedicationChanges(setOf(a.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), expected, newId())
        } }
        assertEquals(before, state())
    }
    @Test fun expectedKeysMustExactlyMatchSelectedIds(): Unit = runBlocking {
        val a = p.medication(); val before = state()
        assertThrows(AdminProblem::class.java) { runBlocking {
            p.admin.bulkMedicationChanges(setOf(a.id), section(), BulkMedicationChanges(status = AvailabilityStatus.OUT), emptyMap(), pharmacy())
        } }
        assertEquals(before, state())
    }
    @Test fun staleRequiredExistingEditorDoesNotRecreateDeletedMedication(): Unit = runBlocking {
        val a = p.medication(); val pharmacy = pharmacy(); p.admin.delete("medication", a.id); val before = state()
        assertThrows(AdminProblem::class.java) { runBlocking { p.admin.saveMedication(a.copy(note = "late"), pharmacy, requireExisting = true) } }
        assertEquals(before, state())
    }
    @Test fun samePinInDifferentDigitScriptRejectsWithoutPendingWork(): Unit = runBlocking {
        val before = state(); val a = "123456".toCharArray(); val b = "١٢٣٤٥٦".toCharArray(); val c = b.copyOf()
        assertThrows(AdminProblem::class.java) { runBlocking { PinChanges(p.admin, MaintenanceStore(p.secure)).change(a, b, c) } }
        assertEquals(before, state()); assertTrue(listOf(a, b, c).all { it.all { digit -> digit == '\u0000' } })
        assertNull(MaintenanceStore(p.secure).decode<PinChangeJournal>("pin-change"))
    }
    @Test fun fourDigitPinChangeCreatesBoundOperationAndWipesInputs(): Unit = runBlocking {
        val a = "123456".toCharArray(); val b = "1907".toCharArray(); val c = b.copyOf()
        val store = MaintenanceStore(p.secure); PinChanges(p.admin, store).change(a, b, c)
        val operation = p.admin.pending().single { it.field == "adminPinVerifier" }
        assertEquals(operation.id, store.decode<PinChangeJournal>("pin-change")!!.operationId)
        assertTrue(listOf(a, b, c).all { it.all { digit -> digit == '\u0000' } })
        assertTrue(PinSecurity.matches("١٩٠٧".toCharArray(), p.ready().catalog.pharmacy!!.adminPinVerifier))
    }
    @Test fun historyTypeFilterSeparatesAvailabilityFromMedication(): Unit = runBlocking {
        val a = p.medication(); p.admin.availability(a.id, section(), AvailabilityStatus.OUT)
        val repo = HistoryRepository(p.factory); val id = pharmacy()
        val availability = repo.observe(id, HistoryFilter(entityType = "availability"), 0).first()
        assertTrue(availability.entries.isNotEmpty()); assertTrue(availability.entries.all { it.entityType == "availability" })
        val medication = repo.observe(id, HistoryFilter(entityType = "medication"), 0).first()
        assertTrue(medication.entries.isNotEmpty()); assertTrue(medication.entries.all { it.entityType == "medication" })
    }
    @Test fun taxonomyReassignmentPreservesUntouchedAvailabilityAndMedication(): Unit = runBlocking {
        val a = p.medication("A"); val b = p.medication("B")
        p.admin.saveTaxonomy("category", null, "جديد", null)
        val category = p.ready().catalog.categories.single { !it.isSystem }
        p.admin.saveMedication(a.copy(categoryId = category.id))
        val before = p.ready().catalog
        p.admin.delete("category", category.id)
        val after = p.ready().catalog
        assertEquals(before.availability, after.availability)
        assertEquals(before.medications.single { it.id == b.id }, after.medications.single { it.id == b.id })
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, after.medications.single { it.id == a.id }.categoryId)
    }
}
