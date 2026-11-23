package com.aqaqeer.app

import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
import com.aqaqeer.app.admin.AdminProblem
import com.aqaqeer.app.admin.catalog
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.util.UUID

class CsvReportsInstrumentedTest {
    private lateinit var p: SuiteProfile
    private val documents = mutableListOf<Uri>()
    @Before fun setup(): Unit = runBlocking { Assume.assumeFalse(BuildConfig.IS_DEMO); p = SuiteProfile(); p.create() }
    @After fun cleanup() {
        if (::p.isInitialized) { documents.forEach { p.context.contentResolver.delete(it, null, null) }; p.close() }
    }
    private fun document(text: String? = null): Uri {
        val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".suite-documents"
        val uri = Uri.parse("content://$authority/suite-${UUID.randomUUID()}"); documents += uri
        p.context.contentResolver.openOutputStream(uri, "wt")!!.use { if (text != null) it.write(text.toByteArray()) }
        return uri
    }
    private fun importer() = CsvImportRepository(p.admin, DocumentOutput(p.context), p.context)
    private fun reports() = ReportRepository(p.admin, DocumentOutput(p.context), PdfReports(p.context))
    @Test fun contentProviderImportCommitsValidRowsAndRetainsFailedRowResults(): Unit = runBlocking {
        val repo = importer(); val session = repo.selectValid(repo.load(document("genericEn,strength\nGood,10 mg\nBad,")))
        val results = mutableListOf<CsvRowResult>(); repo.commit(session) { results += it }
        assertEquals(listOf(CsvOutcome.CREATED, CsvOutcome.FAILED), results.map { it.outcome })
        assertEquals(listOf("Good"), p.ready().catalog.medications.map { it.genericEn })
        val state = CsvImportState(session, results.associateBy { it.number }, true)
        val retry = document(); repo.exportResults(retry, state, true)
        val table = p.context.contentResolver.openInputStream(retry)!!.use { CsvCodec.read(it) {} }
        assertEquals(listOf("Bad", ""), table.records.single().cells)
        val duplicateRun = mutableListOf<CsvRowResult>(); repo.commit(session) { duplicateRun += it }
        assertEquals(CsvOutcome.FAILED, duplicateRun.first().outcome)
        val template = document(); repo.template(template)
        val header = p.context.contentResolver.openInputStream(template)!!.use { CsvCodec.read(it) {} }
        assertEquals(medicationColumns + p.ready().catalog.sections.map { "section:${it.id}" }, header.headers)
    }
    @Test fun staleUpdateRollsBackAndTaxonomyRequiresExplicitConsent(): Unit = runBlocking {
        val med = p.medication(); val repo = importer()
        var session = repo.load(document("nameEn,note\n${med.nameEn},CSV"))
        session = repo.revise(session, 2, CsvDecision(CsvAction.UPDATE, med.id, selected = true))
        p.admin.saveMedication(med.copy(note = "concurrent"))
        val results = mutableListOf<CsvRowResult>(); repo.commit(session) { results += it }
        assertEquals(CsvOutcome.FAILED, results.single().outcome)
        assertEquals("concurrent", p.ready().catalog.medications.single().note)
        val taxonomy = repo.load(document("genericEn,strength,categoryAr\nAnother,10 mg,جديد"))
        assertFalse(repo.selectValid(taxonomy).rows.single().decision.selected)
        val approved = repo.revise(taxonomy, 2, taxonomy.rows.single().decision.copy(selected = true, approveTaxonomy = true))
        repo.commit(approved) { assertEquals(CsvOutcome.CREATED, it.single().outcome) }
        assertTrue(p.ready().catalog.categories.any { it.nameAr == "جديد" })
    }
    @Test fun cancellationAfterChunkCallbackRetainsAllCommittedRows(): Unit = runBlocking {
        val repo = importer(); val session = repo.selectValid(repo.load(document("genericEn,strength\nFirst,10 mg\nSecond,20 mg")))
        val results = mutableListOf<CsvRowResult>()
        val job = launch { repo.commit(session) { results += it; this@launch.cancel() } }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(2, results.size); assertTrue(results.all { it.outcome == CsvOutcome.CREATED })
        // Catalog order is nameEn/id, so generic-only rows need not retain CSV order.
        assertEquals(listOf("First", "Second"), p.ready().catalog.medications.sortedBy { it.genericEn }.map { it.genericEn })
    }
    @Test fun reportsDefaultToPublishedAndOutIncludesMissingAvailability(): Unit = runBlocking {
        val a = p.medication("Published"); p.medication("Missing availability")
        val section = p.ready().catalog.sections.single().id
        p.admin.availability(a.id, section, AvailabilityStatus.OUT)
        p.admin.transaction { dao -> dao.deleteAvailability(dao.availability().filter { it.medicationId != a.id }) }
        p.publish(SuiteRemote()); p.admin.saveMedication(a.copy(nameEn = "Draft change"))
        val repo = reports(); val published = repo.capture(ReportRequest(ReportPreset.OUT, section))
        assertEquals(setOf("Published", "Missing availability"), published.medications.map { it.nameEn }.toSet())
        val draft = repo.capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true))
        assertTrue(draft.medications.any { it.nameEn == "Draft change" })
        assertEquals(AvailabilityStatus.OUT, draft.status(draft.medications.single { it.nameEn == "Missing availability" }.id, section))
        val destination = document(); repo.excel(destination, draft)
        val workbook = p.context.contentResolver.openInputStream(destination)!!.use { ReportWorkbook.read(it) }
        assertEquals(2, workbook.rows("Medications").size - 1)
        assertTrue(workbook.rows("Medications").drop(1).all { it.last() == "غير متوفر" })
        val summary = workbook.rows("Summary").flatten()
        assertTrue(summary.contains(uiText(R.string.report_source_local)))
        assertFalse(summary.any { it.contains("UNKNOWN") })
    }
    @Test fun draftsRequireOptInAndExpiryPresetExcludesExpired(): Unit = runBlocking {
        val repo = reports()
        assertThrows(CsvProblem::class.java) { runBlocking { repo.capture(ReportRequest(ReportPreset.FULL)) } }
        val today = MedicationPresentation.today(java.time.Instant.now(), "Asia/Damascus")
        val a = p.medication("Expired"); val b = p.medication("Soon")
        p.admin.saveMedication(a.copy(expiryDate = today.minusDays(1)))
        p.admin.saveMedication(b.copy(expiryDate = today.plusDays(1)))
        assertEquals(listOf("Soon"), repo.capture(ReportRequest(ReportPreset.EXPIRING_SOON, includeUnpublished = true)).medications.map { it.nameEn })
    }
    @Test fun arabicPdfRendersMultiplePagesAndHasNoBlankContinuationPages(): Unit = runBlocking {
        val med = p.medication("English 10 mg")
        p.admin.saveMedication(med.copy(nameAr = "دواء عربي", note = "نص عربي طويل مع English 123\n".repeat(400)))
        val repo = reports(); val snapshot = repo.capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true,
            options = ReportOptions(fields = ReportField.entries.toSet())))
        val uri = document(); repo.pdf(uri, snapshot)
        val file = File(p.root, "report.pdf")
        p.context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount > 1)
            for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page ->
                val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(android.graphics.Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val pixels = IntArray(bitmap.width * bitmap.height); bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                    assertTrue("Page $i must contain ink", pixels.count { it != android.graphics.Color.WHITE } > 100)
                } finally { bitmap.recycle() }
            }
        }
    }
    @Test fun authorizationLossBlocksExternalOutputAndImportBeforeFileAccess(): Unit = runBlocking {
        val snapshot = reports().capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true))
        p.admin.transaction { it.updateDeviceIdentity(it.deviceIdentity()!!.copy(adminMode = false)) }
        assertThrows(AdminProblem::class.java) { runBlocking { reports().excel(Uri.parse("content://missing/never-open"), snapshot) } }
        assertThrows(AdminProblem::class.java) { runBlocking { importer().load(Uri.parse("content://missing/file")) } }
    }
    @Test fun fiftyOneRowsReportCommittedChunksInOrder(): Unit = runBlocking {
        val repo = importer()
        val text = "genericEn,strength\n" + (1..51).joinToString("\n") { "Medicine $it,10 mg" }
        val session = repo.selectValid(repo.load(document(text)))
        val batches = mutableListOf<List<CsvRowResult>>()
        repo.commit(session) { batch ->
            batches += batch
            assertEquals(batches.sumOf { it.size }, p.ready().catalog.medications.size)
        }
        assertEquals(listOf(50, 1), batches.map { it.size })
        assertEquals((2..52).toList(), batches.flatten().map { it.number })
        assertTrue(batches.flatten().all { it.outcome == CsvOutcome.CREATED })
        assertEquals(51, p.ready().catalog.availability.size)
    }
    @Test fun cancellationAfterFirstFiftyRowsDoesNotStartSecondChunk(): Unit = runBlocking {
        val repo = importer()
        val session = repo.selectValid(repo.load(document("genericEn,strength\n" + (1..51).joinToString("\n") { "Medicine $it,10 mg" })))
        val results = mutableListOf<CsvRowResult>()
        val job = launch { repo.commit(session) { batch -> results += batch; this@launch.cancel() } }
        job.join()
        assertTrue(job.isCancelled); assertEquals(50, results.size)
        assertTrue(results.all { it.outcome == CsvOutcome.CREATED })
        val catalog = p.ready().catalog
        assertEquals(50, catalog.medications.size); assertFalse(catalog.medications.any { it.genericEn == "Medicine 51" })
    }
    @Test fun failedStorageWriteRollsBackWholeChunkAndReportsNoSuccesses(): Unit = runBlocking {
        val repo = importer()
        val session = repo.selectValid(repo.load(document("genericEn,strength\n" + (1..50).joinToString("\n") { "Medicine $it,10 mg" })))
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        val lease = p.factory.acquire()
        try {
            lease.database.openHelper.writableDatabase.execSQL("""CREATE TRIGGER csv_injected_failure BEFORE INSERT ON medications
                WHEN NEW.genericEn = 'Medicine 26' BEGIN SELECT RAISE(ABORT, 'injected chunk failure'); END""")
            val results = mutableListOf<CsvRowResult>()
            repo.commit(session) { results += it }
            assertEquals(50, results.size); assertTrue(results.all { it.outcome == CsvOutcome.FAILED && it.error.isNotBlank() })
            assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
        } finally {
            try { lease.database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS csv_injected_failure") }
            finally { lease.close() }
        }
    }
    @Test fun semicolonSectionAliasImportPersistsExplicitAndDefaultStatuses(): Unit = runBlocking {
        p.admin.saveTaxonomy("section", null, "ثان", "Second")
        val repo = importer(); val session = repo.selectValid(repo.load(document("genericEn;strength;section:Second\nNew;٥٠٠mg;OUT")))
        val results = mutableListOf<CsvRowResult>(); repo.commit(session) { results += it }
        assertEquals(CsvOutcome.CREATED, results.single().outcome)
        val catalog = p.ready().catalog; val med = catalog.medications.single()
        assertEquals("500mg", med.strength)
        assertEquals(AvailabilityStatus.OUT, catalog.status(med.id, catalog.sections.single { it.nameEn == "Second" }.id))
        assertEquals(AvailabilityStatus.AVAILABLE, catalog.status(med.id, catalog.sections.single { it.nameEn != "Second" }.id))
    }
    @Test fun duplicateChoiceUpdatesExactlyOneExistingMedication(): Unit = runBlocking {
        val med = p.medication("Existing"); val repo = importer()
        val loaded = repo.load(document("genericEn,strength,formAr,note\nExisting,10mg,أخرى,Updated"))
        assertNotNull(loaded.rows.single().error)
        val chosen = repo.selectValid(repo.chooseDuplicates(loaded, update = true))
        val results = mutableListOf<CsvRowResult>(); repo.commit(chosen) { results += it }
        assertEquals(CsvOutcome.UPDATED, results.single().outcome)
        val actual = p.ready().catalog.medications.single()
        assertEquals(med.id, actual.id); assertEquals("Updated", actual.note)
    }
    @Test fun staleAvailabilityFailsImportWithoutChangingMedicationFields(): Unit = runBlocking {
        val med = p.medication(); val section = p.ready().catalog.sections.single().id; val repo = importer()
        val loaded = repo.load(document("note,section:$section\nCSV,OUT"))
        val chosen = repo.revise(loaded, 2, CsvDecision(CsvAction.UPDATE, med.id, selected = true))
        p.admin.availability(med.id, section, AvailabilityStatus.LOW)
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        val results = mutableListOf<CsvRowResult>(); repo.commit(chosen) { results += it }
        assertEquals(CsvOutcome.FAILED, results.single().outcome)
        assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
    }
    @Test fun allSectionShortagesIncludeMissingAvailabilityButExcludeInactiveOnly(): Unit = runBlocking {
        val active = p.ready().catalog.sections.single().id
        p.admin.saveTaxonomy("section", null, "مغلق", "Closed", active = false)
        val closed = p.ready().catalog.sections.single { !it.isActive }.id
        val a = p.medication("Active shortage"); val b = p.medication("Inactive shortage"); val c = p.medication("Missing availability")
        p.admin.availability(a.id, active, AvailabilityStatus.OUT)
        p.admin.availability(b.id, closed, AvailabilityStatus.OUT)
        p.admin.transaction { dao -> dao.deleteAvailability(dao.availability().filter { it.medicationId == c.id }) }
        val report = reports().capture(ReportRequest(ReportPreset.OUT, includeUnpublished = true, allSections = true))
        assertEquals(setOf(a.id, c.id), report.medications.map { it.id }.toSet())
        assertEquals(listOf(active), report.sections.map { it.id })
    }
    @Test fun emptyA5ReportStillProducesRenderablePage(): Unit = runBlocking {
        val repo = reports(); val report = repo.capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true))
        assertTrue(report.medications.isEmpty())
        val uri = document(); repo.pdf(uri, report)
        val file = File(p.root, "empty-a5.pdf")
        p.context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount >= 1)
            renderer.openPage(0).use { page -> assertEquals(420, page.width); assertEquals(595, page.height) }
        }
    }
}
