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
    @Test fun newRowsDefaultToCreateAndBlankArabicStaysNull() {
        val row = preview("nameEn,strength,nameAr\nNew,10 mg,\n").rows.single()
        assertNull(row.error); assertEquals(CsvAction.CREATE, row.decision.action)
        assertNull(row.plan!!.medication.nameAr)
        assertEquals(CatalogSeeds.OTHER_FORM_ID, row.plan.medication.formId)
        assertEquals(60, row.plan.medication.expirySoonThresholdDays)
        CatalogRules.validate(row.plan.apply(catalog, F.editor, F.time))
    }
    @Test fun duplicatesRequireExplicitChoiceAndRepeatedUpdateTargetsAreRejected() {
        val text = "nameEn,strength\n${med.nameEn},10 mg\n${med.nameEn},20 mg"
        assertTrue(preview(text).rows.all { it.error != null && it.warnings.isNotEmpty() })
        val update = CsvDecision(CsvAction.UPDATE, med.id)
        assertTrue(preview(text, mapOf(2 to update, 3 to update)).rows.all { it.error != null })
        assertNull(preview(text, mapOf(2 to CsvDecision(CsvAction.CREATE_ANOTHER), 3 to CsvDecision(CsvAction.SKIP))).rows.first().error)
    }
    @Test fun blankUpdatesPreserveValuesAndExplicitClearingRemovesOptionalValues() {
        val enriched = catalog.copy(medications = listOf(med.copy(note = "keep", priceMinor = 123L, currency = Currency.USD)))
        val table = CsvCodec.read("nameEn,note,price,currency\n,,,".byteInputStream()) {}
        fun plan(clear: Set<String>) = CsvRowPlanner.plan(F.publishId, enriched, table, table.records.single(), CsvDecision(CsvAction.UPDATE, med.id, clear = clear))
        assertEquals("keep", plan(emptySet()).medication.note)
        assertEquals(123L, plan(emptySet()).medication.priceMinor)
        val cleared = plan(setOf("note", "price")).medication
        assertNull(cleared.note); assertNull(cleared.priceMinor); assertNull(cleared.currency)
        assertThrows(CsvProblem::class.java) { plan(setOf("nameEn")) }
    }
    @Test fun invalidPricesDatesReferencesAndMissingRequiredFieldsProduceRowErrors() {
        listOf("nameEn,strength,price\nNew,10 mg,12", "nameEn,strength,expiryDate\nNew,10 mg,2026-02-30",
            "nameEn\nNew", "nameEn,strength,section:missing\nNew,10 mg,OUT",
            "nameEn,strength,expirySoonThresholdDays\nNew,10 mg,-1").forEach { assertNotNull(it, preview(it).rows.single().error) }
    }
    @Test fun taxonomyIdsAreStableAndArabicDigitsParseWithoutChangingBusinessDates() {
        val text = "nameEn,strength,categoryAr,formAr,price,currency,expiryDate\nNew,10 mg,جديد,جديد,١٢.٣٤,USD,٢٠٢٦-١٢-٣١"
        val a = preview(text).rows.single().plan!!; val b = preview(text).rows.single().plan!!
        assertEquals(a.category!!.id, b.category!!.id); assertEquals(a.form!!.id, b.form!!.id)
        assertEquals(1234L, a.medication.priceMinor); assertEquals("2026-12-31", a.medication.expiryDate.toString())
    }
}
