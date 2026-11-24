package com.aqaqeer.app.admin

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test

/** Guards against silent loss when individually valid branches are combined. */
class CatalogMergeSafetyTest {
    private val seed = F.catalog()
    private fun merge(base: CatalogSnapshot, local: CatalogSnapshot, remote: CatalogSnapshot,
        choices: Map<String, Boolean> = emptyMap(), operations: List<PendingOperation> =
            CatalogEdits.diff(base, local, F.time.plusSeconds(1), F.publishId)) =
        CatalogEdits.merge(base, local, remote, operations, choices)

    @Test fun explicitNullClearsOnlyTheSelectedFieldAndPreservesRemoteEdits() {
        val base = seed.copy(medications = seed.medications.map { it.copy(note = "old", brand = "brand") })
        val local = base.copy(medications = base.medications.map { it.copy(note = null) })
        val remote = base.copy(medications = base.medications.map { it.copy(brand = "remote brand") })
        val result = merge(base, local, remote)
        assertTrue(result.conflicts.isEmpty())
        assertNull(result.catalog.medications.single().note)
        assertEquals("remote brand", result.catalog.medications.single().brand)
        assertEquals("old", base.medications.single().note)
        CatalogRules.validate(result.catalog)
    }

    @Test fun clearVersusRemoteEditRequiresReviewAndHonorsBothChoices() {
        val base = seed.copy(medications = seed.medications.map { it.copy(note = "old") })
        val local = base.copy(medications = base.medications.map { it.copy(note = null) })
        val remote = base.copy(medications = base.medications.map { it.copy(note = "remote") })
        val ops = CatalogEdits.diff(base, local, F.time, F.publishId)
        val conflict = merge(base, local, remote, operations = ops).conflicts.single()
        assertEquals("note", conflict.field)
        val mine = merge(base, local, remote, mapOf(conflict.key to true), ops)
        val theirs = merge(base, local, remote, mapOf(conflict.key to false), ops)
        assertTrue(mine.conflicts.isEmpty()); assertTrue(theirs.conflicts.isEmpty())
        assertNull(mine.catalog.medications.single().note)
        assertEquals(remote, theirs.catalog)
    }

    @Test fun concurrentAvailabilityEditsToDifferentSectionsRemainIndependent() {
        val med = seed.medications.single()
        val second = Section(F.otherPharmacyId, "آخر")
        val base = seed.copy(sections = seed.sections + second, availability = seed.availability +
            Availability(med.id, second.id, AvailabilityStatus.AVAILABLE, F.time))
        val local = base.copy(availability = base.availability.map {
            if (it.sectionId == second.id) it.copy(status = AvailabilityStatus.OUT) else it })
        val remote = base.copy(availability = base.availability.map {
            if (it.sectionId != second.id) it.copy(status = AvailabilityStatus.LOW) else it })
        val result = merge(base, local, remote)
        assertTrue(result.conflicts.isEmpty())
        assertEquals(AvailabilityStatus.LOW, result.catalog.status(med.id, seed.sections.single().id))
        assertEquals(AvailabilityStatus.OUT, result.catalog.status(med.id, second.id))
        assertEquals(base.medications, result.catalog.medications)
        assertEquals(2, result.catalog.availability.size)
    }

    @Test fun deletedTaxonomyReferenceRequiresExplicitRestoreOrFallback() {
        val category = Category(F.otherPharmacyId, "محذوف")
        val base = seed.copy(categories = seed.categories + category)
        val local = base.copy(medications = base.medications.map { it.copy(categoryId = category.id) })
        val remote = seed
        val ops = CatalogEdits.diff(base, local, F.time, F.publishId)
        val conflict = merge(base, local, remote, operations = ops).conflicts.single()
        assertTrue(conflict.key.startsWith("reference:"))
        val restored = merge(base, local, remote, mapOf(conflict.key to true), ops)
        val fallback = merge(base, local, remote, mapOf(conflict.key to false), ops)
        assertTrue(restored.conflicts.isEmpty()); assertTrue(fallback.conflicts.isEmpty())
        assertEquals(category, restored.catalog.categories.single { it.id == category.id })
        assertEquals(category.id, restored.catalog.medications.single().categoryId)
        assertFalse(fallback.catalog.categories.any { it.id == category.id })
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, fallback.catalog.medications.single().categoryId)
        CatalogRules.validate(restored.catalog); CatalogRules.validate(fallback.catalog)
    }

    @Test fun normalizedCategoryCollisionKeepsChosenBranchAndUnrelatedRemoteAddition() {
        val mine = Category(F.otherPharmacyId, "أدوية")
        val theirs = Category(F.installationId, "ادويه")
        val independent = Category(F.pharmacyId, "مستقل")
        val local = seed.copy(categories = seed.categories + mine,
            medications = seed.medications.map { it.copy(categoryId = mine.id) })
        val remote = seed.copy(categories = seed.categories + theirs + independent)
        CatalogRules.validate(local); CatalogRules.validate(remote)
        val ops = CatalogEdits.diff(seed, local, F.time, F.publishId)
        val conflict = merge(seed, local, remote, operations = ops).conflicts.single()
        assertTrue(conflict.key.startsWith("names:category:"))
        for (keepMine in listOf(true, false)) {
            val result = merge(seed, local, remote, mapOf(conflict.key to keepMine), ops)
            assertTrue(result.conflicts.isEmpty())
            assertTrue(independent in result.catalog.categories)
            assertEquals(keepMine, result.catalog.categories.any { it.id == mine.id })
            assertEquals(!keepMine, result.catalog.categories.any { it.id == theirs.id })
            assertEquals(if (keepMine) mine.id else CatalogSeeds.UNCATEGORIZED_ID,
                result.catalog.medications.single().categoryId)
            CatalogRules.validate(result.catalog)
        }
    }

    @Test fun forgedOperationTargetsRejectBeforeMergeWithoutChangingBranches() {
        val local = seed.copy(medications = seed.medications.map { it.copy(note = "mine") })
        val op = CatalogEdits.diff(seed, local, F.time, F.publishId).single()
        for (invalid in listOf(op.copy(targetId = "not-a-uuid"), op.copy(entityType = "unknown"),
            op.copy(targetId = "${op.targetId}/${F.otherPharmacyId}"), op.copy(transactionId = "invalid"))) {
            assertThrows(IllegalArgumentException::class.java) {
                CatalogEdits.merge(seed, local, seed, listOf(invalid))
            }
        }
        assertNull(seed.medications.single().note)
        assertEquals("mine", local.medications.single().note)
    }
}
