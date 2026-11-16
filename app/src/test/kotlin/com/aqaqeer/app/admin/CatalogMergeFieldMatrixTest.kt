package com.aqaqeer.app.admin

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CatalogMergeFieldMatrixTest(private val field: String, private val remoteKind: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}/{1}")
        fun cases(): List<Array<String>> = listOf("nameEn", "nameAr", "genericEn", "genericAr", "brand", "note", "strength", "expirySoonThresholdDays")
            .flatMap { field -> listOf("unchanged", "disjoint", "same", "conflict").map { arrayOf(field, it) } }
    }
    private fun changed(m: Medication, value: String): Medication = when (field) {
        "nameEn" -> m.copy(nameEn = value); "nameAr" -> m.copy(nameAr = value)
        "genericEn" -> m.copy(genericEn = value); "genericAr" -> m.copy(genericAr = value)
        "brand" -> m.copy(brand = value); "note" -> m.copy(note = value)
        "strength" -> m.copy(strength = value)
        else -> m.copy(expirySoonThresholdDays = if (value == "mine") 10 else 20)
    }
    @Test fun mergePreservesUnrelatedFieldsAndRequiresExactConflictChoice() {
        val base = F.catalog(); val med = base.medications.single()
        val local = base.copy(medications = listOf(changed(med, "mine")))
        val theirs = when (remoteKind) {
            "same" -> changed(med, "mine")
            "conflict" -> changed(med, "theirs")
            "disjoint" -> med.copy(expiryDate = java.time.LocalDate.of(2030, 1, 1))
            else -> med
        }
        val remote = base.copy(medications = listOf(theirs.copy(updatedAt = F.time.plusSeconds(99))))
        val operations = CatalogEdits.diff(base, local, F.time, F.publishId)
        assertEquals(listOf(field), operations.map { it.field })
        val merged = CatalogEdits.merge(base, local, remote, operations)
        if (remoteKind == "conflict") {
            val conflict = merged.conflicts.single(); assertEquals(field, conflict.field)
            val mine = CatalogEdits.merge(base, local, remote, operations, mapOf(conflict.key to true))
            val other = CatalogEdits.merge(base, local, remote, operations, mapOf(conflict.key to false))
            assertTrue(mine.conflicts.isEmpty()); assertTrue(other.conflicts.isEmpty())
            assertTrue(CatalogEdits.diff(local, mine.catalog, F.time, F.publishId).isEmpty())
            assertEquals(remote, other.catalog)
            val newer = remote.copy(medications = listOf(changed(med, "third").copy(note = "remote context")))
            assertTrue(CatalogEdits.merge(base, local, newer, operations, mapOf(conflict.key to true)).conflicts.isNotEmpty())
        } else {
            assertTrue(merged.conflicts.isEmpty())
            val expected = changed(theirs, "mine")
            assertEquals(expected.copy(updatedAt = merged.catalog.medications.single().updatedAt), merged.catalog.medications.single())
            if (remoteKind == "same") assertEquals(remote, merged.catalog)
            CatalogRules.validate(merged.catalog)
        }
        assertEquals(med, base.medications.single())
    }
}
