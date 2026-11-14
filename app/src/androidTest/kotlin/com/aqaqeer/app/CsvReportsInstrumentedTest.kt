package com.aqaqeer.app

import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
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
        val repo = importer(); val session = repo.selectValid(repo.load(document("nameEn,strength\nGood,10 mg\nBad,")))
        val results = mutableListOf<CsvRowResult>(); repo.commit(session) { results += it }
        assertEquals(listOf(CsvOutcome.CREATED, CsvOutcome.FAILED), results.map { it.outcome })
        assertEquals(listOf("Good"), p.ready().catalog.medications.map { it.nameEn })
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
        val taxonomy = repo.load(document("nameEn,strength,categoryAr\nAnother,10 mg,جديد"))
        assertFalse(repo.selectValid(taxonomy).rows.single().decision.selected)
        val approved = repo.revise(taxonomy, 2, taxonomy.rows.single().decision.copy(selected = true, approveTaxonomy = true))
        repo.commit(approved) { assertEquals(CsvOutcome.CREATED, it.outcome) }
        assertTrue(p.ready().catalog.categories.any { it.nameAr == "جديد" })
    }
    @Test fun cancellationBetweenRowsRetainsCompletedOutcomeAndDoesNotReplay(): Unit = runBlocking {
        val repo = importer(); val session = repo.selectValid(repo.load(document("nameEn,strength\nFirst,10 mg\nSecond,20 mg")))
        val results = mutableListOf<CsvRowResult>()
        val job = launch { repo.commit(session) { results += it; this@launch.cancel() } }
        job.join()
        assertEquals(1, results.size); assertEquals(CsvOutcome.CREATED, results.single().outcome)
        assertEquals(listOf("First"), p.ready().catalog.medications.map { it.nameEn })
    }
    @Test fun reportsDefaultToPublishedAndOutNeverIncludesUnknown(): Unit = runBlocking {
        val a = p.medication("Published"); p.medication("Unknown")
        val section = p.ready().catalog.sections.single().id
        p.admin.availability(a.id, section, AvailabilityStatus.OUT)
        p.publish(SuiteRemote()); p.admin.saveMedication(a.copy(nameEn = "Draft change"))
        val repo = reports(); val published = repo.capture(ReportRequest(ReportPreset.OUT, section))
        assertEquals(listOf("Published"), published.medications.map { it.nameEn })
        val draft = repo.capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true))
        assertTrue(draft.medications.any { it.nameEn == "Draft change" })
        assertEquals(AvailabilityStatus.UNKNOWN, draft.status(draft.medications.single { it.nameEn == "Unknown" }.id, section))
        val destination = document(); repo.csv(destination, draft)
        val text = p.context.contentResolver.openInputStream(destination)!!.reader(Charsets.UTF_8).use { it.readText() }
        assertTrue(text.contains("UNKNOWN")); assertTrue(text.contains("includeUnpublished"))
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
        val repo = reports(); val snapshot = repo.capture(ReportRequest(ReportPreset.FULL, includeUnpublished = true))
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
        assertThrows(IllegalStateException::class.java) { runBlocking { reports().csv(document(), snapshot) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { importer().load(Uri.parse("content://missing/file")) } }
    }
}
