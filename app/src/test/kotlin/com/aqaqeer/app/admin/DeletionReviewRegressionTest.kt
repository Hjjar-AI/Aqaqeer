package com.aqaqeer.app.admin

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test

class DeletionReviewRegressionTest {
    private val category = Category(newId(), "تصنيف محذوف")
    private val other = Category(newId(), "تصنيف آخر")
    private val base = F.catalog().let { c -> c.copy(categories = c.categories + category + other,
        medications = c.medications.map { it.copy(categoryId = category.id) }) }
    private val deleted = base.copy(categories = base.categories - category,
        medications = base.medications.map { it.copy(categoryId = CatalogSeeds.UNCATEGORIZED_ID) },
        tombstones = listOf(Tombstone("category", category.id, F.time, newId())))
    private val operations = CatalogEdits.diff(base, deleted, F.editor, F.time, F.publishId)
    private fun merge(remote: CatalogSnapshot, choices: Map<String, Boolean> = emptyMap()) =
        CatalogEdits.merge(base, deleted, remote, operations, choices)

    @Test fun unrelatedRemoteTaxonomyEditDoesNotPromptAndSurvivesDeletion() {
        val remote = base.copy(categories = base.categories.map { if (it.id == other.id) it.copy(nameAr = "اسم جديد") else it })
        val result = merge(remote)
        assertTrue(result.conflicts.isEmpty())
        assertFalse(result.catalog.categories.any { it.id == category.id })
        assertEquals("اسم جديد", result.catalog.categories.single { it.id == other.id }.nameAr)
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, result.catalog.medications.single().categoryId)
    }

    @Test fun dependentRemoteEditRequiresReviewAndKeepingEntityCancelsItsReassignment() {
        val remote = base.copy(medications = base.medications.map { it.copy(note = "remote") })
        val conflict = merge(remote).conflicts.single()
        assertTrue(conflict.key.startsWith("delete:"))
        val kept = merge(remote, mapOf(conflict.key to false))
        assertEquals(remote, kept.catalog)
        val confirmed = merge(remote, mapOf(conflict.key to true))
        assertTrue(confirmed.conflicts.isEmpty())
        assertEquals("remote", confirmed.catalog.medications.single().note)
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, confirmed.catalog.medications.single().categoryId)
    }

    @Test fun newlyLinkedRemoteMedicationIsReviewedAndReassignedOnConfirmation() {
        val added = base.medications.single().copy(id = newId(), genericEn = "New linked medication")
        val remote = base.copy(medications = base.medications + added)
        val conflict = merge(remote).conflicts.single()
        val confirmed = merge(remote, mapOf(conflict.key to true))
        assertTrue(confirmed.conflicts.isEmpty())
        assertEquals(2, confirmed.catalog.medications.size)
        assertTrue(confirmed.catalog.medications.all { it.categoryId == CatalogSeeds.UNCATEGORIZED_ID })
    }

    @Test fun confirmedDeletionPreservesRemoteMoveToAnotherCategory() {
        val remote = base.copy(medications = base.medications.map { it.copy(categoryId = other.id) })
        val conflict = merge(remote).conflicts.single()
        val confirmed = merge(remote, mapOf(conflict.key to true))
        assertTrue(confirmed.conflicts.isEmpty())
        assertEquals(other.id, confirmed.catalog.medications.single().categoryId)
    }

    @Test fun unrelatedPublicationDoesNotInvalidateConfirmationButChangedDependentDoes() {
        val remote = base.copy(medications = base.medications.map { it.copy(note = "reviewed") })
        val conflict = merge(remote).conflicts.single()
        val unrelated = remote.copy(categories = remote.categories + Category(newId(), "إضافة مستقلة"))
        assertTrue(merge(unrelated, mapOf(conflict.key to true)).conflicts.isEmpty())
        val changed = unrelated.copy(medications = unrelated.medications.map { it.copy(note = "changed again") })
        assertTrue(merge(changed, mapOf(conflict.key to true)).conflicts.any { it.key != conflict.key })
    }

    @Test fun derivedAttributionAloneDoesNotRequireDeletionReview() {
        val remote = base.copy(medications = base.medications.map { it.copy(updatedAt = F.time.plusSeconds(20)) })
        assertTrue(merge(remote).conflicts.isEmpty())
    }

    @Test fun sectionDeletionReviewsNewAvailabilityAndRecomputesItsCascade() {
        val original = F.catalog().copy(sections = F.catalog().sections + Section(newId(), "قسم باقٍ"))
        val target = original.sections.first()
        val local = original.copy(sections = original.sections - target, availability = emptyList())
        val added = original.medications.single().copy(id = newId(), genericEn = "New medication")
        val remote = original.copy(medications = original.medications + added,
            availability = original.availability + Availability(added.id, target.id, AvailabilityStatus.OUT, F.time, F.editor))
        val ops = CatalogEdits.diff(original, local, F.editor, F.time, F.publishId)
        val review = CatalogEdits.merge(original, local, remote, ops).conflicts.single()
        val confirmed = CatalogEdits.merge(original, local, remote, ops, mapOf(review.key to true))
        assertTrue(confirmed.conflicts.isEmpty())
        assertEquals(listOf(original.sections.last()), confirmed.catalog.sections)
        assertTrue(confirmed.catalog.availability.isEmpty())
        assertEquals(2, confirmed.catalog.medications.size)
    }

    @Test fun formDeletionDoesNotReplayOldFallbackOverRemoteReassignment() {
        val form = MedicationForm(newId(), "شكل محذوف")
        val original = F.catalog().let { it.copy(forms = it.forms + form,
            medications = it.medications.map { med -> med.copy(formId = form.id) }) }
        val local = original.copy(forms = original.forms - form,
            medications = original.medications.map { it.copy(formId = CatalogSeeds.OTHER_FORM_ID) })
        val remote = original.copy(medications = original.medications.map { it.copy(formId = CatalogSeeds.TABLET_FORM_ID) })
        val ops = CatalogEdits.diff(original, local, F.editor, F.time, F.publishId)
        val review = CatalogEdits.merge(original, local, remote, ops).conflicts.single()
        val confirmed = CatalogEdits.merge(original, local, remote, ops, mapOf(review.key to true))
        assertTrue(confirmed.conflicts.isEmpty())
        assertEquals(CatalogSeeds.TABLET_FORM_ID, confirmed.catalog.medications.single().formId)
        assertFalse(confirmed.catalog.forms.any { it.id == form.id })
    }

    @Test fun restoredMedicationLosesRemoteTombstoneWhileUnrelatedTombstonesSurvive() {
        val original = F.catalog().copy(availability = emptyList())
        val med = original.medications.single()
        val local = original.copy(medications = listOf(med.copy(note = "restore")))
        val marker = Tombstone("medication", med.id, F.time, newId())
        val unrelated = Tombstone("medication", newId(), F.time, newId())
        val remote = original.copy(medications = emptyList(), tombstones = listOf(marker, unrelated))
        val ops = CatalogEdits.diff(original, local, F.editor, F.time, F.publishId)
        val choice = CatalogEdits.merge(original, local, remote, ops).conflicts.single()
        val restored = CatalogEdits.merge(original, local.copy(tombstones = listOf(marker.copy(operationId = newId()))),
            remote, ops, mapOf(choice.key to true))
        assertTrue(restored.conflicts.isEmpty())
        assertEquals("restore", restored.catalog.medications.single().note)
        assertEquals(listOf(unrelated), restored.catalog.tombstones)
        val discarded = CatalogEdits.merge(original, local, remote, ops, mapOf(choice.key to false))
        assertEquals(listOf(marker, unrelated), discarded.catalog.tombstones)
    }
}
