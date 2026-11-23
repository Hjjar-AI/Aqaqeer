package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OtherSectionHintsTest {
    private fun catalog(status: AvailabilityStatus = AvailabilityStatus.OUT): CatalogSnapshot {
        val base = F.catalog(); val med = base.medications.single(); val here = base.sections.single()
        return base.copy(sections = listOf(here, Section("available", "متوفر"), Section("low", "محدود"), Section("closed", "مغلق", isActive = false)),
            availability = listOf(Availability(med.id, here.id, status, F.time),
                Availability(med.id, "available", AvailabilityStatus.AVAILABLE, F.time),
                Availability(med.id, "low", AvailabilityStatus.LOW, F.time),
                Availability(med.id, "closed", AvailabilityStatus.AVAILABLE, F.time)))
    }
    @Test fun defaultOffOmitsHintsWithoutRemovingMedicationRows(): Unit = runBlocking {
        assertFalse(HomePreferences().showOtherSectionAvailability)
        val c = catalog(); val med = c.medications.single()
        val projected = CatalogSearchIndex(c).project(c.sections.first().id, "", HomeFilters(), HomePreferences(), emptySet())
        assertEquals(listOf(med), projected.visible)
        assertTrue(projected.rows.getValue(med.id).elsewhere.isEmpty())
    }
    @Test fun enabledHintsIncludeOnlyAvailableActiveOtherSections(): Unit = runBlocking {
        val c = catalog(); val med = c.medications.single()
        val projected = CatalogSearchIndex(c).project(c.sections.first().id, "", HomeFilters(), HomePreferences(showOtherSectionAvailability = true), emptySet())
        assertEquals(listOf("available"), projected.rows.getValue(med.id).elsewhere.map { it.id })
        assertEquals(AvailabilityStatus.OUT, projected.rows.getValue(med.id).status)
        assertTrue(MedicationPresentation.availableElsewhere(c, med.id, null).isEmpty())
    }
    @Test fun readerEligibilityRemainsOutOnlyWhileAdminCanSeeOtherStock(): Unit = runBlocking {
        for (status in listOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW)) {
            val c = catalog(status); val med = c.medications.single(); val index = CatalogSearchIndex(c)
            val options = HomePreferences(showOtherSectionAvailability = true)
            val reader = index.project(c.sections.first().id, "", HomeFilters(), options, emptySet(), readerStockOnly = true)
            assertTrue(reader.rows.getValue(med.id).elsewhere.isEmpty())
            val admin = index.project(c.sections.first().id, "", HomeFilters(), options, emptySet(), readerStockOnly = false)
            assertEquals(listOf("available"), admin.rows.getValue(med.id).elsewhere.map { it.id })
        }
    }
    @Test fun togglingHintsDoesNotChangeCrossSectionSearchResultsOrFilters(): Unit = runBlocking {
        val c = catalog(); val index = CatalogSearchIndex(c); val med = c.medications.single()
        val filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE))
        val off = index.project(c.sections.first().id, "Fixture", filters, HomePreferences(), emptySet(), readerStockOnly = true)
        val on = index.project(c.sections.first().id, "Fixture", filters, HomePreferences(showOtherSectionAvailability = true), emptySet(), readerStockOnly = true)
        assertTrue(off.visible.isEmpty())
        assertEquals(setOf("available", "low"), off.availableSearchSections)
        assertEquals(listOf(med.id, med.id), off.otherSectionResults.flatMap { it.rows }.map { it.medication.id })
        assertEquals(off.visible, on.visible); assertEquals(off.otherSections, on.otherSections)
        assertEquals(off.availableSearchSections, on.availableSearchSections)
        assertEquals(off.otherSectionResults.map { it.section.id to it.rows.map { row -> row.medication.id } },
            on.otherSectionResults.map { it.section.id to it.rows.map { row -> row.medication.id } })
    }
}
