package com.aqaqeer.app.reports

import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import io.mockk.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneId

/** Real capture/validation over a small, explicit demo catalog; no database or writer mocks participate. */
class ReportSelectionTest {
    private lateinit var admin: AdminRepository
    private lateinit var repo: ReportRepository
    private lateinit var catalog: CatalogSnapshot
    private lateinit var today: LocalDate
    private val meds get() = catalog.medications
    @Before fun setup() {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        installReportStrings()
        DemoSession.reset(); DemoSession.setAdmin(true)
        admin = mockk(); repo = ReportRepository(admin, mockk(), mockk())
        val seed = DemoSession.authorized().catalog
        today = LocalDate.now(ZoneId.of(seed.pharmacy!!.timezone))
        val sections = seed.sections.take(2) + Section(newId(), "Closed", isActive = false)
        val category = Category(newId(), "Second category", orderIndex = seed.categories.maxOf { it.orderIndex } + 1)
        val template = seed.medications.first()
        val records = listOf(
            template.copy(id = newId(), genericAr = null, genericEn = "Alpha", nameAr = null, nameEn = "Zulu", strength = "500 mg", expiryDate = today.minusDays(1)),
            template.copy(id = newId(), genericAr = null, genericEn = "Beta", nameAr = null, nameEn = "Bravo", strength = "10 mg", expiryDate = today, formId = CatalogSeeds.OTHER_FORM_ID),
            template.copy(id = newId(), genericAr = null, genericEn = "Gamma", nameAr = null, nameEn = "Alpha", strength = "20 mg", expiryDate = today.plusDays(30), expirySoonThresholdDays = 30, categoryId = category.id),
            template.copy(id = newId(), genericAr = null, genericEn = "Delta", nameAr = null, nameEn = "Delta", strength = "40 mg", expiryDate = null, categoryId = category.id),
        )
        val availability = listOf(
            Availability(records[0].id, sections[0].id, AvailabilityStatus.OUT, template.updatedAt),
            Availability(records[0].id, sections[1].id, AvailabilityStatus.AVAILABLE, template.updatedAt),
            Availability(records[1].id, sections[0].id, AvailabilityStatus.LOW, template.updatedAt),
            Availability(records[1].id, sections[1].id, AvailabilityStatus.OUT, template.updatedAt),
            Availability(records[2].id, sections[0].id, AvailabilityStatus.OUT, template.updatedAt),
            Availability(records[2].id, sections[1].id, AvailabilityStatus.OUT, template.updatedAt),
            Availability(records[3].id, sections[2].id, AvailabilityStatus.AVAILABLE, template.updatedAt),
        )
        catalog = seed.copy(sections = sections, categories = seed.categories + category, medications = records, availability = availability)
        DemoSession.edit { _, _, _, _ -> catalog }
    }
    @After fun cleanup() {
        if (::admin.isInitialized) verify { admin wasNot Called }
        if (BuildConfig.IS_DEMO) { DemoSession.reset(); DemoSession.setAdmin(false); restoreReportStrings() }
    }
    private suspend fun capture(request: ReportRequest = ReportRequest()) = repo.capture(request.copy(includeUnpublished = true))
    private fun ids(snapshot: ReportSnapshot) = snapshot.medications.map { it.id }.toSet()
    private fun reject(request: ReportRequest) {
        assertThrows(CsvProblem::class.java) { runBlocking { capture(request) } }
    }

    @Test fun defaultScopeContainsAllRecordsAndOnlyActiveSections(): Unit = runBlocking {
        val snapshot = capture()
        assertEquals(meds.map { it.id }.toSet(), ids(snapshot))
        assertEquals(catalog.sections.take(2), snapshot.sections)
        assertEquals(AvailabilityStatus.OUT, snapshot.overall(meds[3].id))
    }
    @Test fun overallAnyAndAllSectionFiltersHaveDifferentResults(): Unit = runBlocking {
        val expected = mapOf(ReportStatusMatch.OVERALL to setOf(meds[2].id, meds[3].id),
            ReportStatusMatch.ANY_SECTION to meds.map { it.id }.toSet(),
            ReportStatusMatch.ALL_SECTIONS to setOf(meds[2].id, meds[3].id))
        expected.forEach { (mode, expectedIds) -> assertEquals(mode.name, expectedIds, ids(capture(ReportRequest(statuses = setOf(AvailabilityStatus.OUT), statusMatch = mode)))) }
        assertEquals(setOf(meds[0].id), ids(capture(ReportRequest(statuses = setOf(AvailabilityStatus.AVAILABLE)))))
        assertEquals(setOf(meds[1].id), ids(capture(ReportRequest(statuses = setOf(AvailabilityStatus.LOW)))))
        assertTrue(capture(ReportRequest(statuses = setOf(AvailabilityStatus.AVAILABLE), statusMatch = ReportStatusMatch.ALL_SECTIONS)).medications.isEmpty())
    }
    @Test fun explicitClosedSectionIsAllowedAndDoesNotSilentlyAddActiveSections(): Unit = runBlocking {
        val closed = catalog.sections.last()
        val snapshot = capture(ReportRequest(sectionIds = setOf(closed.id), statuses = setOf(AvailabilityStatus.AVAILABLE)))
        assertEquals(listOf(closed), snapshot.sections); assertEquals(setOf(meds[3].id), ids(snapshot))
    }
    @Test fun searchCategoryFormAndIndividualSelectionIntersect(): Unit = runBlocking {
        val request = ReportRequest(query = "BETA ١٠", categoryIds = setOf(meds[1].categoryId), formIds = setOf(meds[1].formId), medicationIds = setOf(meds[0].id, meds[1].id))
        assertEquals(setOf(meds[1].id), ids(capture(request)))
        assertTrue(capture(request.copy(categoryIds = setOf(meds[2].categoryId))).medications.isEmpty())
        assertTrue(capture(request.copy(medicationIds = emptySet())).medications.isEmpty())
        assertEquals(4, capture(ReportRequest(medicationIds = null)).medications.size)
    }
    @Test fun expiryIncludesTodayAndThresholdBoundaryButExcludesExpiredFromSoon(): Unit = runBlocking {
        val expected = mapOf(ReportExpiry.ALL to meds.map { it.id }.toSet(), ReportExpiry.UNDATED to setOf(meds[3].id),
            ReportExpiry.EXPIRED to setOf(meds[0].id), ReportExpiry.SOON to setOf(meds[1].id, meds[2].id), ReportExpiry.VALID to setOf(meds[1].id, meds[2].id))
        expected.forEach { (filter, expectedIds) -> assertEquals(filter.name, expectedIds, ids(capture(ReportRequest(expiry = filter)))) }
    }
    @Test fun dateRangeIsInclusiveAndNeverMatchesUndatedRecords(): Unit = runBlocking {
        assertEquals(setOf(meds[1].id, meds[2].id), ids(capture(ReportRequest(fromDate = today.toString(), throughDate = today.plusDays(30).toString()))))
        assertEquals(setOf(meds[1].id), ids(capture(ReportRequest(fromDate = today.toString(), throughDate = today.toString()))))
        assertEquals(setOf(meds[0].id), ids(capture(ReportRequest(throughDate = today.minusDays(1).toString()))))
        assertEquals(setOf(meds[2].id), ids(capture(ReportRequest(fromDate = today.plusDays(1).toString()))))
    }
    @Test fun malformedAndReversedDatesRejectRatherThanBroadenSelection() {
        listOf("2025-02-29", "2026-13-01", "tomorrow").forEach { reject(ReportRequest(fromDate = it)) }
        reject(ReportRequest(fromDate = "2028-01-02", throughDate = "2028-01-01"))
    }
    @Test fun staleSelectionsAndForeignPharmacyRejectBeforeReturningPreview() {
        reject(ReportRequest(pharmacyId = newId()))
        reject(ReportRequest(sectionIds = setOf(newId())))
        reject(ReportRequest(categoryIds = setOf(newId())))
        reject(ReportRequest(formIds = setOf(newId())))
        reject(ReportRequest(medicationIds = setOf(newId())))
    }
    @Test fun scientificIdentityCannotBeOmitted() {
        reject(ReportRequest(options = ReportOptions(fields = ReportField.entries.toSet() - ReportField.SCIENTIFIC)))
    }
    @Test fun emptySectionScopeRejectsEveryAvailabilityDependency(): Unit = runBlocking {
        reject(ReportRequest(sectionIds = emptySet()))
        val identityOnly = ReportOptions(fields = setOf(ReportField.SCIENTIFIC))
        reject(ReportRequest(sectionIds = emptySet(), options = identityOnly, statuses = setOf(AvailabilityStatus.OUT)))
        reject(ReportRequest(sectionIds = emptySet(), options = identityOnly.copy(sort = ReportSort.AVAILABILITY)))
        assertEquals(4, capture(ReportRequest(sectionIds = emptySet(), options = identityOnly)).medications.size)
    }
    @Test fun sortingAndCategoryGroupingUseCapturedBusinessValues(): Unit = runBlocking {
        val options = ReportOptions(groupByCategory = false, names = ReportNames.ENGLISH)
        suspend fun ordered(sort: ReportSort) = capture(ReportRequest(options = options.copy(sort = sort))).medications.map { it.genericEn }
        assertEquals(listOf("Alpha", "Beta", "Delta", "Gamma"), ordered(ReportSort.SCIENTIFIC))
        assertEquals(listOf("Gamma", "Beta", "Delta", "Alpha"), ordered(ReportSort.TRADE))
        assertEquals(listOf("Alpha", "Beta", "Gamma", "Delta"), ordered(ReportSort.EXPIRY))
        assertEquals(listOf("Alpha", "Beta", "Delta", "Gamma"), ordered(ReportSort.AVAILABILITY))
        val grouped = capture(ReportRequest(options = options.copy(groupByCategory = true, sort = ReportSort.TRADE)))
        assertEquals(listOf("Beta", "Alpha", "Gamma", "Delta"), grouped.medications.map { it.genericEn })
    }
    @Test fun captureIsReadOnlyAndPreviouslyCapturedSelectionIsImmutable(): Unit = runBlocking {
        val before = DemoSession.authorized()
        val snapshot = capture(ReportRequest(medicationIds = setOf(meds[0].id)))
        assertEquals(before, DemoSession.authorized())
        DemoSession.edit { c, _, _, _ -> c.copy(medications = c.medications.map { it.copy(note = "Later edit") }) }
        assertEquals(before.catalog.medications.first().note, snapshot.medications.single().note)
        assertEquals(setOf(meds[0].id), ids(snapshot))
    }
}
