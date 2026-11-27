package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Cross-section results must obey the same filters without acquiring current-section state. */
class HomeProjectionRegressionTest {
    private val seed: CatalogSnapshot = F.catalog()
    private val here: Section = seed.sections.single()
    private val other: Section = Section(F.otherPharmacyId, "آخر")
    private val closed: Section = Section(F.installationId, "مغلق", isActive = false)
    private val day: LocalDate = LocalDate.of(2026, 11, 24)
    private val a: Medication = seed.medications.single().copy(genericEn = "Needle A", expiryDate = day)
    private val b: Medication = a.copy(id = F.publishId, genericEn = "Needle B", categoryId = F.pharmacyId, expiryDate = day.plusDays(61))
    private val catalog: CatalogSnapshot = seed.copy(sections = listOf(here, other, closed),
        categories = seed.categories + Category(F.pharmacyId, "آخر"), medications = listOf(b, a),
        availability = listOf(
            Availability(a.id, here.id, AvailabilityStatus.LOW, F.time),
            Availability(a.id, other.id, AvailabilityStatus.AVAILABLE, F.time),
            Availability(b.id, other.id, AvailabilityStatus.LOW, F.time),
            Availability(b.id, closed.id, AvailabilityStatus.AVAILABLE, F.time)))

    private suspend fun project(query: String = "Needle", filters: HomeFilters = HomeFilters(),
        preferences: HomePreferences = HomePreferences(), reader: Boolean = false): HomeBrowseResult =
        CatalogSearchIndex(catalog).project(here.id, query, filters, preferences,
            unpublished = setOf(a.id), fetched = setOf(a.id, b.id), today = day, readerStockOnly = reader)

    @Test fun sameMedicationAppearsInEachMatchingSectionWithThatSectionsStatus(): Unit = runBlocking {
        val result = project()
        assertEquals(setOf(a.id, b.id), result.rows.keys)
        assertEquals(AvailabilityStatus.LOW, result.rows.getValue(a.id).status)
        assertEquals(AvailabilityStatus.OUT, result.rows.getValue(b.id).status)
        val elsewhere = result.otherSectionResults.single()
        assertEquals(other, elsewhere.section)
        assertEquals(listOf(a.id, b.id), elsewhere.rows.map { it.medication.id })
        assertEquals(listOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW), elsewhere.rows.map { it.status })
        assertEquals(setOf(here.id, other.id), result.availableSearchSections)
    }

    @Test fun stockOnlySnapshotUpdatesReaderResultsAndAvailabilityOrder(): Unit = runBlocking {
        val index = CatalogSearchIndex(catalog)
        suspend fun reader(current: CatalogSearchIndex) = current.project(here.id, "Needle", HomeFilters(),
            HomePreferences(), emptySet(), today = day, readerStockOnly = true)
        fun stockOrder(current: CatalogSearchIndex) = current.browse(here.id, "", HomeFilters(), HomeSort.AVAILABILITY)
            .map { it.id }

        val before = reader(index)
        assertEquals(listOf(a.id), before.visible.map { it.id })
        assertEquals(listOf(a.id, b.id), before.otherSectionResults.single().rows.map { it.medication.id })
        assertEquals(listOf(b.id, a.id), stockOrder(index))

        val updated = catalog.copy(availability = listOf(
            Availability(a.id, other.id, AvailabilityStatus.OUT, F.time),
            Availability(b.id, here.id, AvailabilityStatus.AVAILABLE, F.time)))
        val updatedIndex = index.withCatalog(updated)
        val after = reader(updatedIndex)
        assertSame(updated, after.catalog)
        assertEquals(listOf(b.id), after.visible.map { it.id })
        assertEquals(AvailabilityStatus.AVAILABLE, after.rows.getValue(b.id).status)
        assertEquals(setOf(here.id), after.availableSearchSections)
        assertTrue(after.otherSectionResults.isEmpty())
        assertEquals(listOf(a.id, b.id), stockOrder(updatedIndex))
        assertEquals(before, reader(index))
    }

    @Test fun statusIntersectionExcludesLowAndOutFromOtherSectionSearch(): Unit = runBlocking {
        val result = project(filters = HomeFilters(statuses = setOf(AvailabilityStatus.AVAILABLE)))
        assertTrue(result.visible.isEmpty())
        assertEquals(setOf(other.id), result.availableSearchSections)
        assertEquals(listOf(a.id), result.otherSectionResults.single().rows.map { it.medication.id })
    }

    @Test fun outOnlyFilterNeverMarksASectionAsStocked(): Unit = runBlocking {
        val result = project(filters = HomeFilters(statuses = setOf(AvailabilityStatus.OUT)))
        assertEquals(listOf(b.id), result.visible.map { it.id })
        assertTrue(result.availableSearchSections.isEmpty())
        assertTrue(result.otherSectionResults.isEmpty())
    }

    @Test fun readerShowsOnlyStockedCurrentRowsAndKeepsActualOtherSectionStatus(): Unit = runBlocking {
        val result = project(reader = true)
        assertEquals(listOf(a.id), result.visible.map { it.id })
        assertEquals(listOf(a.id, b.id), result.otherSectionResults.single().rows.map { it.medication.id })
        assertTrue(result.readerStockOnly)
        assertEquals(HomeFilters(), result.filters)
    }

    @Test fun extraFiltersApplyToCurrentRowsOtherRowsAndTabMarkersTogether(): Unit = runBlocking {
        for (filters in listOf(HomeFilters(expiringSoon = true), HomeFilters(unpublishedOnly = true),
            HomeFilters(categoryIds = setOf(a.categoryId)), HomeFilters(fetchedOnly = true, expiringSoon = true))) {
            val result = project(filters = filters)
            assertEquals(filters.toString(), listOf(a.id), result.visible.map { it.id })
            assertEquals(listOf(a.id), result.otherSectionResults.single().rows.map { it.medication.id })
            assertEquals(setOf(here.id, other.id), result.availableSearchSections)
            assertEquals(filters, result.filters)
        }
    }

    @Test fun clearingOrNormalizingAwayTheQueryRemovesCrossSectionResults(): Unit = runBlocking {
        for (query in listOf("", "   ", "ء")) {
            val result = project(query)
            assertTrue(query, result.otherSectionResults.isEmpty())
            assertTrue(query, result.availableSearchSections.isEmpty())
            assertEquals(setOf(a.id, b.id), result.rows.keys)
        }
    }

    @Test fun otherSectionRowsRetainPerMedicationMarkersWithoutInventingHints(): Unit = runBlocking {
        val result = project(preferences = HomePreferences(showOtherSectionAvailability = true))
        val rows = result.otherSectionResults.single().rows.associateBy { it.medication.id }
        assertTrue(rows.getValue(a.id).unpublished)
        assertFalse(rows.getValue(b.id).unpublished)
        assertTrue(rows.values.all { it.fetched })
        assertTrue(rows.values.all { it.elsewhere.isEmpty() })
        assertEquals(catalog, result.catalog)
    }

    @Test fun groupingPartitionsVisibleRowsOnceAndLeavesCrossSectionResultsIntact(): Unit = runBlocking {
        val plain = project()
        for (grouping in HomeGrouping.entries) {
            val grouped = project(preferences = HomePreferences(groupingEnabled = true, grouping = grouping))
            assertEquals(plain.visible, grouped.visible)
            val members = grouped.groups.flatMap { it.medications }.map { it.id }
            assertEquals(2, members.size)
            assertEquals(plain.rows.keys, members.toSet())
            assertEquals(plain.otherSectionResults, grouped.otherSectionResults)
        }
    }

    @Test fun reversingInputCollectionsKeepsDeterministicMedicationOrdering(): Unit = runBlocking {
        val reversed = catalog.copy(medications = catalog.medications.reversed(), availability = catalog.availability.reversed())
        for (sort in HomeSort.entries) {
            val preferences = HomePreferences(sort = sort)
            val expected = project(preferences = preferences)
            val actual = CatalogSearchIndex(reversed).project(here.id, "Needle", HomeFilters(), preferences,
                setOf(a.id), setOf(a.id, b.id), day)
            assertEquals(sort.name, expected.visible, actual.visible)
            assertEquals(expected.otherSectionResults, actual.otherSectionResults)
        }
    }

    @Test fun readerProjectionCannotExposeOutRowsEvenWithAnExplicitOutFilter(): Unit = runBlocking {
        val result = project(filters = HomeFilters(statuses = setOf(AvailabilityStatus.OUT)), reader = true)
        assertEquals(listOf(a.id), result.visible.map { it.id })
        assertEquals(setOf(AvailabilityStatus.LOW), result.rows.values.map { it.status }.toSet())
        assertEquals(listOf(a.id, b.id), result.otherSectionResults.single().rows.map { it.medication.id })
        assertTrue(result.otherSectionResults.flatMap { it.rows }.none { it.status == AvailabilityStatus.OUT })
        assertTrue(result.readerStockOnly)
    }

    @Test fun fetchedAndUnpublishedFiltersIntersectWithoutReclassifyingOtherRows(): Unit = runBlocking {
        val index = CatalogSearchIndex(catalog)
        val fetched = index.project(here.id, "Needle", HomeFilters(fetchedOnly = true), HomePreferences(),
            unpublished = setOf(a.id), fetched = setOf(b.id), today = day)
        assertEquals(listOf(b.id), fetched.visible.map { it.id })
        val row = fetched.otherSectionResults.single().rows.single()
        assertEquals(b.id, row.medication.id)
        assertEquals(AvailabilityStatus.LOW, row.status)
        assertTrue(row.fetched); assertFalse(row.unpublished)
        assertEquals(setOf(other.id), fetched.availableSearchSections)
        val intersection = index.project(here.id, "Needle", HomeFilters(fetchedOnly = true, unpublishedOnly = true),
            HomePreferences(), unpublished = setOf(a.id), fetched = setOf(b.id), today = day)
        assertTrue(intersection.visible.isEmpty())
        assertTrue(intersection.otherSectionResults.isEmpty())
        assertTrue(intersection.availableSearchSections.isEmpty())
    }

    @Test fun categoryExpiryAndChangeFiltersAlsoRestrictOtherSectionResults(): Unit = runBlocking {
        val filters = HomeFilters(categoryIds = setOf(a.categoryId), expiringSoon = true,
            unpublishedOnly = true, fetchedOnly = true)
        val result = project(filters = filters)
        assertEquals(listOf(a.id), result.visible.map { it.id })
        assertEquals(listOf(a.id), result.otherSectionResults.single().rows.map { it.medication.id })
        assertEquals(setOf(here.id, other.id), result.availableSearchSections)
        val empty = project(filters = filters.copy(categoryIds = setOf(b.categoryId)))
        assertTrue(empty.visible.isEmpty())
        assertTrue(empty.otherSectionResults.isEmpty())
        assertTrue(empty.availableSearchSections.isEmpty())
    }

    @Test fun alreadyCancelledProjectionCannotReturnAUsableResult(): Unit = runBlocking {
        var entered = false
        val job = async {
            currentCoroutineContext().cancel()
            entered = true
            project()
        }
        try { job.await(); fail("Cancelled projection returned data") }
        catch (_: CancellationException) { assertTrue(job.isCancelled); assertTrue(entered) }
    }
}
