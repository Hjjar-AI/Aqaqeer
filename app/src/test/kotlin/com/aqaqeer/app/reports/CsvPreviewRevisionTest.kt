package com.aqaqeer.app.reports

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CancellationException

class CsvPreviewRevisionTest {
    private val catalog = F.catalog()
    private fun initial(text: String) = CsvRowPlanner.preview(F.publishId, catalog, CsvCodec.read(text.byteInputStream()) {}, emptyMap()) {}
    private fun revise(session: CsvImportSession, number: Int, decision: CsvDecision): CsvImportSession {
        val choices = session.rows.associate { it.record.number to it.decision } + (number to decision)
        return CsvRowPlanner.preview(session.id, session.baseline, session.table, choices, session, setOf(number)) {}
    }
    private fun assertMatchesFull(session: CsvImportSession) {
        val full = CsvRowPlanner.preview(session.id, session.baseline, session.table, session.rows.associate { it.record.number to it.decision }) {}
        assertEquals(full.rows, session.rows)
    }
    @Test fun unrelatedRevisionPreservesCreateIdsAndUnchangedPreviewObjects() {
        val first = initial("genericEn,strength\nAlpha,10 mg\nBeta,20 mg")
        val revised = revise(first, 2, first.rows[0].decision.copy(selected = true))
        assertSame(first.rows[1], revised.rows[1]); assertEquals(first.rows[0].plan!!.medication.id, revised.rows[0].plan!!.medication.id)
        assertMatchesFull(revised)
    }
    @Test fun repeatedTargetIsRevalidatedForBothRowsAndRecoversWhenSkipped() {
        val med = catalog.medications.single()
        val first = initial("note\nFirst\nSecond")
        val a = revise(first, 2, CsvDecision(CsvAction.UPDATE, med.id))
        val b = revise(a, 3, CsvDecision(CsvAction.UPDATE, med.id))
        assertTrue(b.rows.all { it.error != null }); assertMatchesFull(b)
        val c = revise(b, 3, b.rows[1].decision.copy(action = CsvAction.SKIP))
        assertNull(c.rows[0].error); assertNull(c.rows[1].plan); assertMatchesFull(c)
    }
    @Test fun changingUpdateTargetRevisitsOldAndNewDependencies() {
        val med = catalog.medications.single(); val first = initial("note\nFirst\nSecond")
        val a = revise(revise(first, 2, CsvDecision(CsvAction.UPDATE, med.id)), 3, CsvDecision(CsvAction.UPDATE, med.id))
        val b = revise(a, 3, a.rows[1].decision.copy(targetId = F.otherPharmacyId))
        assertNull(b.rows[0].error); assertNotNull(b.rows[1].error); assertMatchesFull(b)
    }
    @Test fun fileDuplicatesRequireExplicitChoicesAndExposeEachOther() {
        val first = initial("genericEn,strength\nAlpha,10mg\nALPHA,١٠ mg")
        assertEquals(listOf(3), first.rows[0].duplicateRows); assertEquals(listOf(2), first.rows[1].duplicateRows)
        assertTrue(first.rows.all { it.error != null })
        val second = revise(first, 2, first.rows[0].decision.copy(action = CsvAction.CREATE_ANOTHER))
        assertNull(second.rows[0].error); assertNotNull(second.rows[1].error); assertMatchesFull(second)
    }
    @Test fun stableTaxonomyIdsAreSharedWithinSessionAndSeparatedByKind() {
        val first = initial("genericEn,strength,categoryAr,formAr\nAlpha,10 mg,جديد,جديد\nBeta,20 mg,جديد,جديد")
        val a = first.rows[0].plan!!; val b = first.rows[1].plan!!
        assertEquals(a.category!!.id, b.category!!.id); assertEquals(a.form!!.id, b.form!!.id)
        assertNotEquals(a.category.id, a.form.id)
        val second = revise(first, 2, first.rows[0].decision.copy(approveTaxonomy = true))
        assertMatchesFull(second)
    }
    @Test fun sectionAliasIsBoundToIdAndCannotRetargetAfterRename() {
        val first = initial("genericEn,strength,section:Main section\nAlpha,10 mg,OUT")
        val id = catalog.sections.single().id; assertEquals(id, first.table.sectionIds["section:Main section"])
        val renamed = catalog.copy(sections = catalog.sections.map { it.copy(nameEn = "Renamed") })
        val row = first.rows.single()
        val plan = CsvRowPlanner.plan(first.id, renamed, first.table, row.record, row.decision)
        assertEquals(mapOf(id to AvailabilityStatus.OUT), plan.statuses)
        val replacement = renamed.copy(sections = listOf(renamed.sections.single().copy(id = F.otherPharmacyId, nameEn = "Main section")))
        assertThrows(CsvProblem::class.java) { CsvRowPlanner.plan(first.id, replacement, first.table, row.record, row.decision) }
    }
    @Test fun multipleAliasesForOneSectionRejectRatherThanOverwriteStatus() {
        val id = catalog.sections.single().id
        val session = initial("genericEn,strength,section:$id,section:Main section\nAlpha,10 mg,OUT,LOW")
        assertNotNull(session.rows.single().error)
    }
    @Test fun blankUpdateStatusesDoNotInventAvailabilityChanges() {
        val med = catalog.medications.single(); val first = initial("note,section:Main section\nNew note,")
        val updated = revise(first, 2, CsvDecision(CsvAction.UPDATE, med.id))
        assertTrue(updated.rows.single().plan!!.statuses.isEmpty())
        assertEquals(catalog.availability, updated.rows.single().plan!!.apply(catalog, F.time).availability)
    }
    @Test fun skipMakesMalformedRecordInertWithoutInventingPlan() {
        val first = initial("genericEn,strength\nAlpha")
        assertNotNull(first.rows.single().error)
        val skipped = revise(first, 2, first.rows.single().decision.copy(action = CsvAction.SKIP))
        assertNull(skipped.rows.single().error); assertNull(skipped.rows.single().plan); assertMatchesFull(skipped)
    }
    @Test fun revisionPreservesExistingCreationTimeAndUpdatesAttribution() {
        val med = catalog.medications.single(); val first = initial("note\nChanged")
        val updated = revise(first, 2, CsvDecision(CsvAction.UPDATE, med.id))
        val now = F.time.plusSeconds(10); val next = updated.rows.single().plan!!.apply(catalog, now).medications.single()
        assertEquals(med.createdAt, next.createdAt); assertEquals(now, next.updatedAt)
    }
    @Test fun previewCancellationPropagatesWithoutChangingBaseline() {
        val table = CsvCodec.read("genericEn,strength\nAlpha,10 mg".byteInputStream()) {}
        assertThrows(CancellationException::class.java) {
            CsvRowPlanner.preview(F.publishId, catalog, table, emptyMap()) { throw CancellationException("cancel") }
        }
        assertEquals(F.catalog(), catalog)
    }
}
