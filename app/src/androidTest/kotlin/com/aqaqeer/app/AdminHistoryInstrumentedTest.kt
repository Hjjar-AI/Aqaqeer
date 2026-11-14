package com.aqaqeer.app

import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.maintenance.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDate

class AdminHistoryInstrumentedTest {
    private lateinit var p: SuiteProfile
    @Before fun setup(): Unit = runBlocking { Assume.assumeFalse(BuildConfig.IS_DEMO); p = SuiteProfile(); p.create() }
    @After fun cleanup() { if (::p.isInitialized) p.close() }
    @Test fun staleBulkPreviewAndMissingTargetsRollBackEveryRow(): Unit = runBlocking {
        val a = p.medication("A"); val b = p.medication("B"); val section = p.ready().catalog.sections.single().id
        val expected = mapOf(a.id to AvailabilityStatus.UNKNOWN, b.id to AvailabilityStatus.UNKNOWN)
        p.admin.availability(a.id, section, AvailabilityStatus.LOW)
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        assertThrows(IllegalStateException::class.java) { runBlocking { p.admin.bulkAvailability(expected.keys, section, AvailabilityStatus.OUT, expected) } }
        assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.admin.bulkAvailability(setOf(a.id, "missing"), section, AvailabilityStatus.OUT) } }
        p.admin.bulkAvailability(setOf(a.id, b.id), section, AvailabilityStatus.OUT)
        assertTrue(p.ready().catalog.availability.all { it.status == AvailabilityStatus.OUT })
    }
    @Test fun unchangedUnknownSaveCreatesNoHistoryOrPendingWork(): Unit = runBlocking {
        val a = p.medication(); val section = p.ready().catalog.sections.single().id
        val before = p.admin.transaction { it.historyCount() to it.pending() }
        p.admin.availability(a.id, section, AvailabilityStatus.UNKNOWN)
        assertEquals(before, p.admin.transaction { it.historyCount() to it.pending() })
    }
    @Test fun categoryDeletionReassignsAndSystemEntitiesAndLastSectionAreProtected(): Unit = runBlocking {
        p.admin.saveTaxonomy("category", null, "تصنيف", null)
        val category = p.ready().catalog.categories.single { !it.isSystem }
        val a = p.medication(); p.admin.saveMedication(a.copy(categoryId = category.id))
        p.admin.delete("category", category.id)
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, p.ready().catalog.medications.single().categoryId)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.admin.delete("category", CatalogSeeds.UNCATEGORIZED_ID) } }
        val section = p.ready().catalog.sections.single()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.admin.delete("section", section.id, section.nameAr) } }
    }
    @Test fun historyCombinesTimezoneEditorAndMedicationFiltersAndRedactsVerifiers(): Unit = runBlocking {
        val med = p.medication(); val id = p.ready().catalog.pharmacy!!.pharmacyId
        val editor = p.admin.transaction { it.deviceIdentity()!!.id }
        val at = Instant.parse("2026-11-10T21:00:00Z") // Damascus next civil day.
        val entries = listOf(
            HistoryEntry(newId(), null, "medication", med.id, med.nameEn, "note", null, "\"new\"", editor, "محرر", at, null, null),
            HistoryEntry(newId(), null, "pharmacy", id, "صيدلية", "adminPinVerifier", "SECRET", "SECRET", null, null, at, null, null))
        p.admin.transaction { it.clearHistory(); it.insertHistory(entries) }
        val repo = HistoryRepository(p.factory)
        val filter = HistoryFilter(LocalDate.of(2026,11,11), LocalDate.of(2026,11,11), editor, med.id)
        val page = withTimeout(10000) { repo.observe(id, filter, 0).first() }
        assertEquals(listOf(entries.first().id), page.entries.map { it.id })
        val hidden = repo.observe(id, HistoryFilter(editorId = UNKNOWN_HISTORY_EDITOR), 0).first().entries.single()
        assertTrue(hidden.valuesHidden); assertNull(hidden.before.text); assertNull(hidden.after.text)
        assertTrue(repo.observe(id, HistoryFilter(through = LocalDate.of(2026,11,10)), 0).first().entries.isEmpty())
    }
    @Test fun historyPagesAreStableRetainDeletedNamesAndRejectReaderAccess(): Unit = runBlocking {
        val med = p.medication(); val pharmacy = p.ready().catalog.pharmacy!!.pharmacyId
        p.admin.transaction { dao ->
            dao.clearHistory()
            dao.insertHistory((0..50).map { i -> HistoryEntry(newId(), null, "medication", med.id, "Deleted name", "note", null,
                "😀".repeat(600), null, null, Instant.ofEpochMilli(i.toLong()), null, null) })
        }
        val repo = HistoryRepository(p.factory)
        val a = repo.observe(pharmacy, HistoryFilter(), 0).first(); val b = repo.observe(pharmacy, HistoryFilter(), 1).first()
        assertEquals(50, a.entries.size); assertTrue(a.hasMore); assertEquals(1, b.entries.size); assertFalse(b.hasMore)
        assertTrue(a.entries.map { it.id }.intersect(b.entries.map { it.id }.toSet()).isEmpty())
        assertTrue(a.entries.all { it.after.shortened && it.after.text!!.codePointCount(0, it.after.text!!.length) == 512 })
        p.admin.delete("medication", med.id)
        assertTrue(repo.observe(pharmacy, HistoryFilter(), 0).first().medications.any { it.id == med.id })
        p.admin.transaction { it.updateDeviceIdentity(it.deviceIdentity()!!.copy(adminMode = false)) }
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.observe(pharmacy, HistoryFilter(), 0).first() } }
    }
    @Test fun pinChangeBindsOwnOperationAndErasesAllCallerSecrets(): Unit = runBlocking {
        val current = "123456".toCharArray(); val next = "654321".toCharArray(); val confirmation = next.copyOf()
        val store = MaintenanceStore(p.secure)
        PinChanges(p.admin, store).change(current, next, confirmation)
        assertTrue(listOf(current,next,confirmation).all { chars -> chars.all { it == '\u0000' } })
        val record = store.decode<PinChangeJournal>("pin-change")!!
        val operation = p.admin.pending().single { it.field == "adminPinVerifier" }
        assertEquals(operation.id, record.operationId)
        val device = p.admin.transaction { it.deviceIdentity()!! }
        guardPinOperations(store, device, listOf(operation), record.previous)
        assertThrows(IllegalStateException::class.java) { guardPinOperations(store, device.copy(id = newId()), listOf(operation), record.previous) }
    }
    @Test fun wrongPinAndMismatchedConfirmationLeaveCatalogAndHistoryUnchanged(): Unit = runBlocking {
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        val secrets = listOf("000000".toCharArray(), "654321".toCharArray(), "654321".toCharArray())
        assertThrows(IllegalStateException::class.java) { runBlocking { PinChanges(p.admin, MaintenanceStore(p.secure)).change(secrets[0], secrets[1], secrets[2]) } }
        assertTrue(secrets.all { it.all { c -> c == '\u0000' } })
        assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
    }
}
