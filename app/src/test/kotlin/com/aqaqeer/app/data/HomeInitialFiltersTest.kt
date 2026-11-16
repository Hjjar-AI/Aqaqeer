package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeInitialFiltersTest {
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val seed: CatalogSnapshot = F.catalog()
    private val first: String = seed.sections.single().id
    private val second: Section = Section(F.otherPharmacyId, "آخر")
    private val a: Medication = seed.medications.single().copy(genericEn = "Needle A", expiryDate = day.plusDays(60))
    private val b: Medication = a.copy(id = F.installationId, genericEn = "Needle B", categoryId = "other", expiryDate = day.minusDays(1))
    private val c: Medication = a.copy(id = F.publishId, genericEn = "Needle C", expiryDate = day.plusDays(61))
    private val catalog: CatalogSnapshot = seed.copy(categories = seed.categories + Category("other", "آخر"),
        sections = seed.sections + second, medications = listOf(a, b, c), availability = listOf(
            Availability(a.id, first, AvailabilityStatus.OUT, F.time),
            Availability(b.id, first, AvailabilityStatus.LOW, F.time),
            Availability(c.id, first, AvailabilityStatus.AVAILABLE, F.time),
            Availability(a.id, second.id, AvailabilityStatus.LOW, F.time),
            Availability(b.id, second.id, AvailabilityStatus.AVAILABLE, F.time),
            Availability(c.id, second.id, AvailabilityStatus.LOW, F.time)))
    private suspend fun project(filters: HomeFilters, query: String = "Needle", at: LocalDate = day,
        section: String = first): HomeBrowseResult = CatalogSearchIndex(catalog).project(section, query, filters, HomePreferences(),
            unpublished = setOf(a.id, b.id), fetched = setOf(a.id, c.id), today = at)

    @Test fun statusesUseOrWhileCategoriesExpiryUnpublishedAndFetchedUseAnd(): Unit = runBlocking {
        val filters = HomeFilters(setOf(AvailabilityStatus.OUT, AvailabilityStatus.LOW),
            setOf(a.categoryId), expiringSoon = true, unpublishedOnly = true, fetchedOnly = true)
        val result = project(filters)
        assertEquals(listOf(a.id), result.visible.map { it.id })
        assertTrue(result.rows.getValue(a.id).fetched); assertTrue(result.rows.getValue(a.id).unpublished)
        assertEquals(listOf(a.id, b.id), project(HomeFilters(setOf(AvailabilityStatus.OUT, AvailabilityStatus.LOW))).visible.map { it.id })
    }
    @Test fun expiryIncludesThresholdExcludesExpiredAndInvalidatesAtMidnight(): Unit = runBlocking {
        assertEquals(setOf(a.id), project(HomeFilters(expiringSoon = true)).rows.keys)
        assertEquals(setOf(a.id, c.id), project(HomeFilters(expiringSoon = true), at = day.plusDays(1)).rows.keys)
        val noDate = catalog.copy(medications = listOf(a.copy(expiryDate = null)), availability = emptyList())
        assertTrue(CatalogSearchIndex(noDate).project(first, "", HomeFilters(expiringSoon = true), HomePreferences(), emptySet(), today = day).visible.isEmpty())
    }
    @Test fun otherSectionsHintCountsOnlyAdditionalDistinctMedications(): Unit = runBlocking {
        val filters = HomeFilters(statuses = setOf(AvailabilityStatus.LOW))
        val result = project(filters)
        assertEquals(setOf(b.id), result.rows.keys)
        assertEquals(mapOf(second.id to setOf(a.id, c.id)), result.otherSections)
        val switched = project(filters, section = second.id)
        assertEquals(filters, switched.filters); assertEquals(result.query, switched.query)
        assertEquals(setOf(a.id, c.id), switched.rows.keys)
        assertEquals(mapOf(first to setOf(b.id)), switched.otherSections)
    }
    @Test fun hintsRequireQueryStatusFilterAndActiveOtherSection(): Unit = runBlocking {
        assertTrue(project(HomeFilters(), query = "Needle").otherSections.isEmpty())
        assertTrue(project(HomeFilters(statuses = setOf(AvailabilityStatus.OUT)), query = "").otherSections.isEmpty())
        val inactive = catalog.copy(sections = catalog.sections.map { if (it.id == second.id) it.copy(isActive = false) else it })
        assertTrue(CatalogSearchIndex(inactive).project(first, "Needle", HomeFilters(statuses = setOf(AvailabilityStatus.LOW)), HomePreferences(), emptySet(), today = day).otherSections.isEmpty())
    }
    @Test fun projectionCarriesAllInputsWithoutMutatingCatalogOrFilters(): Unit = runBlocking {
        val filters = HomeFilters(fetchedOnly = true)
        val result = project(filters)
        assertEquals(filters, result.filters); assertEquals(day, result.today)
        assertEquals(setOf(a.id, b.id), result.unpublished); assertEquals(setOf(a.id, c.id), result.fetched)
        assertEquals(setOf(a.id, c.id), result.rows.keys); assertEquals(catalog, result.catalog)
        assertEquals(3, catalog.medications.size)
    }
}
