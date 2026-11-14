package com.aqaqeer.app.reports

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test

class CsvRowPlannerTest {
    private val catalog = F.catalog()
    private val med = catalog.medications.single()
    private fun preview(text: String, decisions: Map<Int, CsvDecision> = emptyMap()) =
        CsvRowPlanner.preview(F.publishId, catalog, CsvCodec.read(text.byteInputStream()) {}, decisions) {}

    @Test fun newRowsDefaultToCreateWithTabletAndAvailableDefaults() {
        val row = preview("genericEn,strength,nameAr\nNew,10 mg,\n").rows.single()
        assertNull(row.error); assertEquals(CsvAction.CREATE, row.decision.action)
        assertNull(row.plan!!.medication.nameAr)
        assertEquals(CatalogSeeds.TABLET_FORM_ID, row.plan.medication.formId)
        assertEquals(60, row.plan.medication.expirySoonThresholdDays)
        assertEquals(catalog.sections.associate { it.id to AvailabilityStatus.AVAILABLE }, row.plan.statuses)
        CatalogRules.validate(row.plan.apply(catalog, F.editor, F.time))
    }
    @Test fun duplicatesUseStrengthAndFormAndRepeatedUpdateTargetsAreRejected() {
        val text = "genericEn,strength,formAr\n${med.genericEn},10 mg,أخرى\n${med.genericEn},10mg,أخرى"
        assertTrue(preview(text).rows.all { it.error != null && it.warnings.isNotEmpty() })
        val update = CsvDecision(CsvAction.UPDATE, med.id)
        assertTrue(preview(text, mapOf(2 to update, 3 to update)).rows.all { it.error != null })
        assertNull(preview(text, mapOf(2 to CsvDecision(CsvAction.CREATE_ANOTHER), 3 to CsvDecision(CsvAction.SKIP))).rows.first().error)
        assertNull(preview("genericEn,strength,formAr\n${med.genericEn},20 mg,أخرى").rows.single().error)
    }
    @Test fun blankUpdatesPreserveValuesAndExplicitClearingRemovesOptionalValues() {
        val enriched = catalog.copy(medications = listOf(med.copy(nameAr = "تجاري", nameEn = "Commercial", note = "keep", brand = "Brand", expiryDate = java.time.LocalDate.of(2027, 1, 1))))
        val table = CsvCodec.read("nameEn,note,brand\n,,".byteInputStream()) {}
        fun plan(clear: Set<String>) = CsvRowPlanner.plan(F.publishId, enriched, table, table.records.single(), CsvDecision(CsvAction.UPDATE, med.id, clear = clear))
        assertEquals("keep", plan(emptySet()).medication.note)
        assertEquals("Brand", plan(emptySet()).medication.brand)
        assertNull(plan(setOf("note")).medication.note)
        assertEquals("Brand", plan(setOf("note")).medication.brand)
        val cleared = plan(setOf("nameAr", "nameEn", "brand", "note", "expiryDate")).medication
        assertNull(cleared.nameAr); assertNull(cleared.nameEn); assertNull(cleared.brand)
        assertNull(cleared.note); assertNull(cleared.expiryDate)
        assertEquals(med.genericEn, cleared.genericEn)
    }
    @Test fun unsupportedClearFieldsRejectRatherThanSilentlyErase() {
        val table = CsvCodec.read("genericEn\nNew".byteInputStream()) {}
        for (field in listOf("strength", "formAr", "categoryAr", "expirySoonThresholdDays", "unknown")) {
            assertThrows(CsvProblem::class.java) {
                CsvRowPlanner.plan(F.publishId, catalog, table, table.records.single(), CsvDecision(CsvAction.UPDATE, med.id, clear = setOf(field)))
            }
        }
    }
    @Test fun invalidDatesReferencesAndMissingScientificNameProduceRowErrors() {
        listOf("genericEn,strength,expiryDate\nNew,10 mg,2026-02-30", "genericEn\nNew",
            "nameEn,strength\nCommercial only,10 mg", "genericEn,strength,section:missing\nNew,10 mg,OUT",
            "genericEn,strength,expirySoonThresholdDays\nNew,10 mg,-1").forEach { assertNotNull(it, preview(it).rows.single().error) }
    }
    @Test fun taxonomyIdsAreStableAndArabicDigitsPreserveGregorianBusinessDates() {
        val text = "genericEn,strength,categoryAr,formAr,expiryDate\nNew,١٠ mg,جديد,جديد,٢٠٢٦-١٢-٣١"
        val a = preview(text).rows.single().plan!!; val b = preview(text).rows.single().plan!!
        assertEquals(a.category!!.id, b.category!!.id); assertEquals(a.form!!.id, b.form!!.id)
        assertEquals("10 mg", a.medication.strength); assertEquals("2026-12-31", a.medication.expiryDate.toString())
    }
    @Test fun clearingLastScientificNameRejectsButRetainingAlternateSucceeds() {
        val table = CsvCodec.read("genericEn\n\"\"".byteInputStream()) {}
        val decision = CsvDecision(CsvAction.UPDATE, med.id, clear = setOf("genericEn"))
        assertThrows(CsvProblem::class.java) { CsvRowPlanner.plan(F.publishId, catalog, table, table.records.single(), decision) }
        val bilingual = catalog.copy(medications = listOf(med.copy(genericAr = "علمي")))
        assertNull(CsvRowPlanner.plan(F.publishId, bilingual, table, table.records.single(), decision).medication.genericEn)
    }
}
