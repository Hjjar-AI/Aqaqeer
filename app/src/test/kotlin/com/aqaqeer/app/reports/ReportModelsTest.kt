package com.aqaqeer.app.reports

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*

class ReportModelsTest {
    @Before fun strings() = installReportStrings()
    @After fun cleanup() = restoreReportStrings()
    private fun snapshot(options: ReportOptions = ReportOptions()): ReportSnapshot {
        val c = F.catalog()
        return ReportSnapshot(F.pharmacyId, c.pharmacy!!.name, "Asia/Damascus", F.time, F.time,
            ReportRequest(options = options), c.sections, c.medications, c.availability, c.categories, c.forms)
    }

    @Test fun defaultsExportAllPublishedMedicationsWithOnlyCoreFields() {
        val request = ReportRequest()
        assertEquals(ReportPreset.FULL, request.preset)
        assertFalse(request.includeUnpublished)
        assertNull(request.medicationIds); assertNull(request.sectionIds)
        assertTrue(request.effectiveStatuses.isEmpty())
        assertEquals(ReportExpiry.ALL, request.effectiveExpiry)
        assertEquals(setOf(ReportField.SCIENTIFIC, ReportField.STRENGTH, ReportField.FORM, ReportField.AVAILABILITY), request.options.fields)
        assertEquals(ReportPaper.A5, request.options.paper)
        assertFalse(request.options.landscape); assertTrue(request.options.compact)
    }
    @Test fun aggregationCoversEveryThreeSectionCombinationAndEmptySelection() {
        val a = AvailabilityStatus.AVAILABLE; val l = AvailabilityStatus.LOW; val o = AvailabilityStatus.OUT
        for (one in listOf(a,l,o)) for (two in listOf(a,l,o)) for (three in listOf(a,l,o)) {
            val values = listOf(one,two,three)
            val expected = if (values.contains(a)) a else if (values.contains(l)) l else o
            assertEquals(values.toString(), expected, combinedStatus(values))
        }
        assertEquals(o, combinedStatus(emptyList()))
    }
    @Test fun aggregateUsesOnlyCapturedSectionsAndMissingPairsAreOut() {
        val base = snapshot(); val med = base.medications.single()
        val other = Section(F.otherPharmacyId, "فرع")
        val captured = base.copy(sections = listOf(other))
        assertEquals(AvailabilityStatus.OUT, captured.status(med.id, other.id))
        assertEquals(AvailabilityStatus.OUT, captured.overall(med.id))
        assertEquals(AvailabilityStatus.AVAILABLE, base.overall(med.id))
    }
    @Test fun perSectionColumnsPreserveScopeOrderAndNeverExposeUnselectedFields() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.AVAILABILITY), availability = ReportAvailability.PER_SECTION))
        val sections = base.sections + Section(F.otherPharmacyId, "فرع", isActive = false)
        val columns = base.copy(sections = sections).columns()
        assertEquals(listOf(ReportField.SCIENTIFIC, ReportField.AVAILABILITY, ReportField.AVAILABILITY), columns.map { it.field })
        assertEquals(sections, columns.drop(1).map { it.section })
        assertNotEquals(columns[1].title, columns[2].title)
        assertTrue(columns.all { it.core })
        assertFalse(ReportColumn(ReportField.NOTE).core)
    }
    @Test fun languageSelectionFallsBackAndBothPreservesArabicAndEnglish() {
        val base = snapshot(); val med = base.medications.single().copy(genericAr = "باراسيتامول", genericEn = "Paracetamol", nameAr = "اسم", nameEn = "Trade")
        val scientific = ReportColumn(ReportField.SCIENTIFIC); val trade = ReportColumn(ReportField.TRADE)
        fun with(names: ReportNames) = base.copy(request = base.request.copy(options = base.request.options.copy(names = names)))
        assertEquals("باراسيتامول", scientific.value(with(ReportNames.ARABIC), med))
        assertEquals("Paracetamol", scientific.value(with(ReportNames.ENGLISH), med))
        assertEquals("باراسيتامول\nParacetamol", scientific.value(with(ReportNames.BOTH), med))
        assertEquals("Trade", trade.value(with(ReportNames.ARABIC), med.copy(nameAr = null)))
        assertEquals("باراسيتامول", scientific.value(with(ReportNames.ENGLISH), med.copy(genericEn = null)))
        assertEquals("Paracetamol", scientific.value(with(ReportNames.BOTH), med.copy(genericAr = "Paracetamol")))
    }
    @Test fun everyBusinessFieldRetainsItsCompleteValue() {
        val base = snapshot(); val med = base.medications.single().copy(note = "ملاحظة\nEnglish & <detail>", brand = "Brand", expiryDate = java.time.LocalDate.of(2028,2,29), expirySoonThresholdDays = 17)
        val expected = mapOf(ReportField.STRENGTH to "10 mg", ReportField.FORM to base.forms.single { it.id == med.formId }.nameAr,
            ReportField.CATEGORY to base.categories.single { it.id == med.categoryId }.nameAr, ReportField.BRAND to "Brand",
            ReportField.EXPIRY to "2028-02-29", ReportField.THRESHOLD to "17", ReportField.NOTE to med.note!!)
        expected.forEach { (field, value) -> assertEquals(field.name, value, ReportColumn(field).value(base, med)) }
        assertEquals("", ReportColumn(ReportField.NOTE).value(base, med.copy(note = null)))
        assertNull(ReportColumn(ReportField.NOTE).status(base, med))
    }
    @Test fun presentationSerializationContainsNoSourceConsentOrPharmacyFilters() {
        val options = ReportOptions(fields = ReportField.entries.toSet(), names = ReportNames.ENGLISH, sort = ReportSort.EXPIRY,
            groupByCategory = false, availability = ReportAvailability.PER_SECTION, paper = ReportPaper.A4, landscape = true, compact = false)
        val json = Json.encodeToString(options)
        assertEquals(options, Json.decodeFromString<ReportOptions>(json))
        for (privateField in listOf("pharmacyId", "sectionIds", "medicationIds", "includeUnpublished", "query", "botToken", "snapshotKey")) assertFalse(privateField, json.contains(privateField))
        assertEquals(ReportOptions(), Json.decodeFromString<ReportOptions>("{}"))
    }
    @Test fun legacyShortcutsRetainTheirExplicitMeaning() {
        val out = ReportRequest(ReportPreset.OUT, "section")
        assertEquals(setOf(AvailabilityStatus.OUT), out.effectiveStatuses)
        assertEquals(ReportStatusMatch.ANY_SECTION, out.effectiveMatch)
        assertEquals(ReportExpiry.SOON, ReportRequest(ReportPreset.EXPIRING_SOON).effectiveExpiry)
    }
}
