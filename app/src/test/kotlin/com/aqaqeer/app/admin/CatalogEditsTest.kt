package com.aqaqeer.app.admin

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test

class CatalogEditsTest {
    private val base = F.catalog()
    private fun local(name: String = requireNotNull(base.medications.single().nameEn), note: String? = null) =
        base.copy(medications = base.medications.map { it.copy(nameEn = name, note = note) })
    @Test fun disjointFieldsMergeAndRemoteTimestampIsNotAConflict() {
        val local = local(note = "mine")
        val remote = local(name = "remote").copy(medications = local(name = "remote").medications.map { it.copy(updatedAt = F.time.plusSeconds(99)) })
        val result = CatalogEdits.merge(base, local, remote, CatalogEdits.diff(base, local, F.time, F.publishId))
        assertTrue(result.conflicts.isEmpty()); assertEquals("remote", result.catalog.medications.single().nameEn)
        assertEquals("mine", result.catalog.medications.single().note)
    }
    @Test fun conflictNeedsChoiceAndChoiceIsInvalidatedByChangedRemote() {
        val local = local(name = "mine"); val remote = local(name = "theirs")
        val ops = CatalogEdits.diff(base, local, F.time, F.publishId)
        val conflict = CatalogEdits.merge(base, local, remote, ops).conflicts.single()
        assertEquals("mine", CatalogEdits.merge(base, local, remote, ops, mapOf(conflict.key to true)).catalog.medications.single().nameEn)
        assertEquals("theirs", CatalogEdits.merge(base, local, remote, ops, mapOf(conflict.key to false)).catalog.medications.single().nameEn)
        assertEquals(1, CatalogEdits.merge(base, local, local(name = "changed again"), ops, mapOf(conflict.key to true)).conflicts.size)
    }
    @Test fun remoteDeletionRequiresExplicitRestoration() {
        val local = local(note = "mine"); val remote = base.copy(medications = emptyList(), availability = emptyList())
        val ops = CatalogEdits.diff(base, local, F.time, F.publishId)
        val conflict = CatalogEdits.merge(base, local, remote, ops).conflicts.single()
        assertTrue(CatalogEdits.merge(base, local, remote, ops, mapOf(conflict.key to false)).catalog.medications.isEmpty())
        assertEquals("mine", CatalogEdits.merge(base, local, remote, ops, mapOf(conflict.key to true)).catalog.medications.single().note)
    }
    @Test fun localDeletionCannotSilentlyDiscardRemoteChanges() {
        val local = base.copy(medications = emptyList(), availability = emptyList())
        val ops = CatalogEdits.diff(base, local, F.time, F.publishId)
        val remote = local(name = "updated remotely")
        val conflict = CatalogEdits.merge(base, local, remote, ops).conflicts.single()
        assertTrue(CatalogEdits.merge(base, local, remote, ops, mapOf(conflict.key to true)).catalog.medications.isEmpty())
        assertEquals("updated remotely", CatalogEdits.merge(base, local, remote, ops, mapOf(conflict.key to false)).catalog.medications.single().nameEn)
    }
    @Test fun alreadySatisfiedOperationDoesNotOverwriteRemoteMetadata() {
        val local = local(note = "same"); val remote = local.copy(medications = local.medications.map { it.copy(updatedAt = F.time.plusSeconds(12)) })
        val merged = CatalogEdits.merge(base, local, remote, CatalogEdits.diff(base, local, F.time, F.publishId))
        assertEquals(remote, merged.catalog)
    }
    @Test fun derivedTimestampNeverSelectsConflictingBusinessValues() {
        val local = local(name = "mine"); val remote = local(name = "theirs")
        val ops = CatalogEdits.diff(base, local, F.time.plusSeconds(5000), F.publishId)
        assertEquals(1, CatalogEdits.merge(base, local, remote, ops).conflicts.size)
        assertTrue(CatalogEdits.diff(base, base.copy(medications = base.medications.map { it.copy(updatedAt = F.time.plusSeconds(3)) }), F.time, F.publishId).isEmpty())
    }
    @Test fun pinUsesFreshSaltArabicDigitsAndConstantTimeVerification() {
        val first = PinSecurity.create("١٢٣٤٥٦".toCharArray()); val second = PinSecurity.create("123456".toCharArray())
        assertNotEquals(first.salt, second.salt); assertNotEquals(first.hash, second.hash)
        assertTrue(PinSecurity.matches("123456".toCharArray(), first)); assertFalse(PinSecurity.matches("123457".toCharArray(), first))
        assertThrows(AdminProblem::class.java) { PinSecurity.create("123".toCharArray()) }
    }
    @Test fun acceptedFieldsUseLatestOperationTimeRegardlessOfOperationOrder() {
        val local = local(name = "new", note = "new note")
        val operations = CatalogEdits.diff(base, local, F.time, F.publishId).map {
            it.copy(authoredAt = if (it.field == "note") F.time.plusSeconds(20) else F.time.plusSeconds(10))
        }
        val forward = CatalogEdits.merge(base, local, base, operations)
        val reverse = CatalogEdits.merge(base, local, base, operations.reversed())
        assertTrue(forward.conflicts.isEmpty()); assertEquals(forward, reverse)
        assertEquals(F.time.plusSeconds(20), forward.catalog.medications.single().updatedAt)
        assertEquals("new", forward.catalog.medications.single().nameEn)
        assertEquals("new note", forward.catalog.medications.single().note)
    }
    @Test fun tiedOperationTimesWithDistinctIdsHaveStableMergeAndPreserveLineage() {
        val local = local(name = "new", note = "new note")
        val operations = CatalogEdits.diff(base, local, F.time, F.publishId).map {
            it.copy(id = if (it.field == "note") F.publishId else F.installationId)
        }
        val result = CatalogEdits.merge(base, local, base, operations)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(result, CatalogEdits.merge(base, local, base, operations.reversed()))
        assertEquals(F.time, result.catalog.medications.single().updatedAt)
        assertTrue(operations.all { it.basePublishId == F.publishId })
    }

}
