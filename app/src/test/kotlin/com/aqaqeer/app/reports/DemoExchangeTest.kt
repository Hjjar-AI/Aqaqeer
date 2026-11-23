package com.aqaqeer.app.reports

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream

class DemoExchangeTest {
    private lateinit var admin: AdminRepository
    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private lateinit var output: DocumentOutput
    private lateinit var pdf: PdfReports
    private val files = mutableMapOf<Uri, ByteArray>()
    @Before fun setup() {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        DemoSession.reset(); DemoSession.setAdmin(true)
        admin = mockk(); resolver = mockk(); context = mockk(); pdf = mockk()
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } answers { files.getValue(firstArg()).inputStream() }
        every { resolver.openOutputStream(any(), "wt") } answers {
            val uri = firstArg<Uri>()
            object : ByteArrayOutputStream() { override fun close() { files[uri] = toByteArray(); super.close() } }
        }
        output = DocumentOutput(context)
    }
    @After fun cleanup() {
        if (::admin.isInitialized) verify { admin wasNot Called }
        if (BuildConfig.IS_DEMO) { DemoSession.reset(); DemoSession.setAdmin(false) }
    }
    private fun uri(text: String? = null): Uri = mockk<Uri>().also {
        every { it.scheme } returns "content"
        if (text != null) files[it] = text.toByteArray()
    }
    private fun reports() = ReportRepository(admin, output, pdf)
    private fun importer() = CsvImportRepository(admin, output, context)

    @Test fun reportsUsePublishedDataUntilDraftInclusionOrSimulatedPublish(): Unit = runBlocking {
        val baseline = DemoSession.authorized()
        val id = baseline.catalog.medications.first().id
        DemoSession.edit { c, _, _, _ -> c.copy(medications = c.medications.map { if (it.id == id) it.copy(note = "unpublished report note") else it }) }
        val repo = reports()
        val published = repo.capture(ReportRequest(ReportPreset.FULL))
        val draft = repo.capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true))
        assertEquals(40, published.medications.size)
        assertEquals(baseline.publishedAt, published.publishedAt)
        assertEquals(baseline.catalog.medications.first().note, published.medications.single { it.id == id }.note)
        assertEquals("unpublished report note", draft.medications.single { it.id == id }.note)
        DemoSession.publish()
        assertEquals("unpublished report note", repo.capture(ReportRequest(ReportPreset.FULL)).medications.single { it.id == id }.note)
        assertNotEquals("unpublished report note", published.medications.single { it.id == id }.note)
    }
    @Test fun shortageAndExpiryPresetsUseCompleteSeedAndExcludeExpired(): Unit = runBlocking {
        val seed = DemoSession.authorized().catalog; val repo = reports()
        val section = seed.sections.first().id
        val sectionOut = repo.capture(ReportRequest(ReportPreset.OUT, section))
        assertEquals(seed.medications.filterIndexed { i, _ -> i % 6 in setOf(2, 3) }.map { it.id }.toSet(), sectionOut.medications.map { it.id }.toSet())
        val allOut = repo.capture(ReportRequest(ReportPreset.OUT, allSections = true))
        assertEquals(seed.medications.filterIndexed { i, _ -> i % 6 in setOf(1, 2, 3) }.map { it.id }.toSet(), allOut.medications.map { it.id }.toSet())
        assertEquals(2, allOut.sections.size)
        assertEquals(listOf(seed.medications[1].id), repo.capture(ReportRequest(ReportPreset.EXPIRING_SOON)).medications.map { it.id })
    }
    @Test fun shortageIncludesMissingPairAndExcludesClosedOnlyStock(): Unit = runBlocking {
        val seed = DemoSession.authorized().catalog; val med = seed.medications.first()
        DemoSession.edit { c, _, _, _ -> c.copy(
            sections = c.sections.mapIndexed { i, s -> s.copy(isActive = i == 0) },
            availability = c.availability.filterNot { it.medicationId == med.id && it.sectionId == c.sections.first().id }) }
        val report = reports().capture(ReportRequest(ReportPreset.OUT, includeUnpublished = true, allSections = true))
        assertTrue(report.medications.any { it.id == med.id }); assertEquals(1, report.sections.size)
        assertEquals(AvailabilityStatus.OUT, report.status(med.id, seed.sections.first().id))
        DemoSession.edit { c, _, _, _ -> c.copy(sections = c.sections.map { it.copy(isActive = false) }) }
        assertThrows(CsvProblem::class.java) { runBlocking { reports().capture(ReportRequest(ReportPreset.OUT, includeUnpublished = true, allSections = true)) } }
    }
    @Test fun reportCsvWritesSampleMetadataAndEveryCapturedMedication(): Unit = runBlocking {
        val snapshot = reports().capture(ReportRequest(ReportPreset.FULL)); val target = uri()
        reports().csv(target, snapshot)
        val text = files.getValue(target).toString(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFF")); assertTrue(text.contains(snapshot.pharmacyName))
        assertTrue(text.contains("publishedAt")); assertTrue(text.contains("includeUnpublished"))
        snapshot.medications.forEach { assertTrue(text.contains(it.genericEn!!)) }
        assertFalse(text.contains("botToken")); assertFalse(text.contains("snapshotKey")); assertFalse(text.contains("UNKNOWN"))
    }
    @Test fun readerAndResetGenerationBlockReportOutputBeforeOpeningProvider(): Unit = runBlocking {
        val snapshot = reports().capture(ReportRequest(ReportPreset.FULL)); val target = uri()
        DemoSession.setAdmin(false)
        assertThrows(AdminProblem::class.java) { runBlocking { reports().capture(ReportRequest(ReportPreset.FULL)) } }
        assertThrows(AdminProblem::class.java) { runBlocking { reports().csv(target, snapshot) } }
        DemoSession.setAdmin(true); DemoSession.reset()
        assertThrows(IllegalStateException::class.java) { runBlocking { reports().csv(target, snapshot) } }
        verify(exactly = 0) { resolver.openOutputStream(any(), any()) }
    }
    @Test fun csvImportUsesSameDefaultsAndOnlyPublishesOnExplicitAction(): Unit = runBlocking {
        val before = DemoSession.authorized(); val repo = importer()
        val session = repo.selectValid(repo.load(uri("genericEn,strength\nDemo imported,25 mg")))
        val results = mutableListOf<CsvRowResult>(); repo.commit(session) { results += it }
        assertEquals(CsvOutcome.CREATED, results.single().outcome)
        val after = DemoSession.authorized(); val med = after.catalog.medications.single { it.genericEn == "Demo imported" }
        assertEquals(CatalogSeeds.TABLET_FORM_ID, med.formId)
        after.catalog.sections.forEach { assertEquals(AvailabilityStatus.AVAILABLE, after.catalog.status(med.id, it.id)) }
        assertEquals(before.initial, after.initial); assertTrue(after.pending.isNotEmpty()); assertTrue(after.history.isNotEmpty())
        DemoSession.publish(); assertTrue(DemoSession.authorized().initial.medications.any { it.id == med.id })
    }
    @Test fun csvStaleUpdateFailsWithoutErasingConcurrentEdit(): Unit = runBlocking {
        val repo = importer(); val med = DemoSession.authorized().catalog.medications.first()
        val session = repo.load(uri("genericEn,note\n${med.genericEn},CSV note"))
        val selected = repo.revise(session, 2, CsvDecision(CsvAction.UPDATE, med.id, selected = true))
        DemoSession.edit { c, _, _, _ -> c.copy(medications = c.medications.map { if (it.id == med.id) it.copy(note = "concurrent") else it }) }
        val before = DemoSession.authorized(); val results = mutableListOf<CsvRowResult>()
        repo.commit(selected) { results += it }
        assertEquals(CsvOutcome.FAILED, results.single().outcome); assertEquals(before, DemoSession.authorized())
    }
    @Test fun csvChunkResultsPreserveValidRowsAndRetryOnlyFailures(): Unit = runBlocking {
        val repo = importer(); val loaded = repo.load(uri("genericEn,strength\nValid demo,10 mg\nInvalid demo,"))
        val selected = repo.selectValid(loaded); val results = mutableListOf<CsvRowResult>()
        repo.commit(selected) { results += it }
        assertEquals(listOf(CsvOutcome.CREATED, CsvOutcome.FAILED), results.map { it.outcome })
        val state = CsvImportState(selected, results.associateBy { it.number }, true); val target = uri()
        repo.exportResults(target, state, true)
        val retry = CsvCodec.read(files.getValue(target).inputStream()) {}
        assertEquals("Invalid demo", retry.records.single().cells.first())
        assertTrue(DemoSession.authorized().catalog.medications.any { it.genericEn == "Valid demo" })
    }
    @Test fun csvCommitAfterResetFailsAndReaderCannotOpenImportOrTemplate(): Unit = runBlocking {
        val repo = importer(); val session = repo.selectValid(repo.load(uri("genericEn,strength\nStale demo,10 mg")))
        DemoSession.reset(); val before = DemoSession.authorized(); val results = mutableListOf<CsvRowResult>()
        repo.commit(session) { results += it }
        assertEquals(CsvOutcome.FAILED, results.single().outcome); assertEquals(before, DemoSession.authorized())
        DemoSession.setAdmin(false); clearMocks(resolver, answers = false)
        assertThrows(AdminProblem::class.java) { runBlocking { repo.load(uri()) } }
        assertThrows(AdminProblem::class.java) { runBlocking { repo.template(uri()) } }
        verify { resolver wasNot Called }
    }
    @Test fun fiftyOneCsvRowsCommitInTwoCompleteChunks(): Unit = runBlocking {
        val repo = importer(); val session = repo.selectValid(repo.load(uri("genericEn,strength\n" + (1..51).joinToString("\n") { "Demo row $it,10 mg" })))
        val batches = mutableListOf<List<CsvRowResult>>()
        repo.commit(session) { batches += it }
        assertEquals(listOf(50, 1), batches.map { it.size })
        assertTrue(batches.flatten().all { it.outcome == CsvOutcome.CREATED })
        assertEquals(91, DemoSession.authorized().catalog.medications.size)
    }
}
