package com.aqaqeer.app

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.fixtures.SuiteRemote
import com.aqaqeer.app.admin.catalog
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.EmptyCoroutineContext

/** Uses Android's real XML serializer and PDF/font stack. Execution belongs to workflow 07. */
class ReportExportsInstrumentedTest {
    private lateinit var profile: SuiteProfile
    @Before fun setup() {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        profile = SuiteProfile()
    }
    @After fun cleanup() { if (::profile.isInitialized) profile.close() }
    private fun snapshot(options: ReportOptions = ReportOptions()): ReportSnapshot {
        val c = F.catalog()
        return ReportSnapshot(F.pharmacyId, c.pharmacy!!.name, "Asia/Damascus", F.time, F.time,
            ReportRequest(options = options), c.sections, c.medications, c.availability, c.categories, c.forms)
    }
    private fun workbook(snapshot: ReportSnapshot): ReportWorkbook {
        val bytes = ByteArrayOutputStream()
        ExcelReports.write(bytes, snapshot, EmptyCoroutineContext)
        return ReportWorkbook.read(bytes.toByteArray().inputStream()).also { it.assertStructure() }
    }

    @Test fun publishedAndLocalSourcesNeverFallbackAndRejectForeignSelections(): Unit = runBlocking {
        profile.create()
        val repo = ReportRepository(profile.admin, DocumentOutput(profile.context), PdfReports(profile.context))
        assertThrows(CsvProblem::class.java) { runBlocking { repo.capture(ReportRequest()) } }
        assertTrue(repo.capture(ReportRequest(includeUnpublished = true)).medications.isEmpty())
        val published = profile.medication("Published")
        profile.publish(SuiteRemote())
        val draft = profile.medication("Unpublished")
        assertEquals(listOf(published.id), repo.capture(ReportRequest()).medications.map { it.id })
        assertEquals(setOf(published.id, draft.id), repo.capture(ReportRequest(includeUnpublished = true)).medications.map { it.id }.toSet())
        assertThrows(CsvProblem::class.java) { runBlocking { repo.capture(ReportRequest(medicationIds = setOf(draft.id))) } }
        val pharmacy = profile.ready().catalog.pharmacy!!.pharmacyId
        assertThrows(CsvProblem::class.java) { runBlocking { repo.capture(ReportRequest(pharmacyId = newId())) } }
        assertEquals(pharmacy, repo.source(false).pharmacyId)
    }
    @Test fun filteredSnapshotDrivesBothFormatsWithoutChangingCatalogOrPendingWork(): Unit = runBlocking {
        profile.create()
        val one = profile.medication("Keep"); profile.medication("Exclude")
        val options = ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.STRENGTH, ReportField.NOTE), names = ReportNames.ENGLISH)
        profile.admin.saveMedication(one.copy(note = "عربي & English detail"))
        val repo = ReportRepository(profile.admin, DocumentOutput(profile.context), PdfReports(profile.context))
        val request = ReportRequest(includeUnpublished = true, query = "KEEP", medicationIds = setOf(one.id), options = options)
        val before = profile.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        val captured = repo.capture(request)
        assertEquals(listOf(one.id), captured.medications.map { it.id })
        val book = workbook(captured)
        assertEquals(listOf("1", "Keep", "10 mg", "عربي & English detail"), book.rows("Medications")[1])
        assertTrue(book.rows("Summary").contains(listOf(uiText(R.string.shared_search_label), "KEEP")))
        assertTrue(book.rows("Summary").contains(listOf(uiText(R.string.report_summary_source), uiText(R.string.report_source_local))))
        val bytes = ByteArrayOutputStream()
        PdfReports(profile.context).render(captured).use { it.writeTo(bytes) }
        assertTrue(bytes.toByteArray().take(5).toByteArray().contentEquals("%PDF-".toByteArray()))
        assertEquals(request, captured.request)
        assertEquals(before, profile.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
    }

    @Test fun workbookHasSummaryFilterableRowsRtlFrozenHeadersAndPrintSettings() {
        val snapshot = snapshot(ReportOptions(fields = ReportField.entries.toSet(), paper = ReportPaper.A4, landscape = true))
        val book = workbook(snapshot)
        val summary = book.rows("Summary").drop(1).associate { it[0] to it[1] }
        assertEquals(snapshot.pharmacyName, summary[uiText(R.string.report_summary_pharmacy)])
        assertEquals(uiText(R.string.report_source_published), summary[uiText(R.string.report_summary_source)])
        assertEquals("1", summary[uiText(R.string.report_summary_count)])
        assertTrue(summary.containsKey(uiText(R.string.report_summary_generated)))
        assertTrue(summary.containsKey(uiText(R.string.home_publication_time_label)))
        assertTrue(summary.containsKey(uiText(R.string.report_sections)))
        assertEquals(11, book.rows("Medications").single { it.first() == "1" }.size)
        for (path in listOf("xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")) {
            assertEquals("1", book.elements(path, "sheetView").single().getAttribute("rightToLeft"))
            val pane = book.elements(path, "pane").single()
            assertEquals("frozen", pane.getAttribute("state")); assertEquals("A2", pane.getAttribute("topLeftCell"))
            val print = book.elements(path, "pageSetup").single()
            assertEquals("9", print.getAttribute("paperSize")); assertEquals("landscape", print.getAttribute("orientation"))
            assertEquals("1", print.getAttribute("fitToWidth")); assertEquals("0", print.getAttribute("fitToHeight"))
            assertTrue(book.elements(path, "col").all { it.getAttribute("width").toDouble() >= 10 })
        }
        assertEquals("'Medications'!\$1:\$1", book.elements("xl/workbook.xml", "definedName").single().textContent)
    }
    @Test fun workbookContainsOnlySelectedFieldsAndPreservesFormulaLikeText() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.NOTE)))
        val note = "=HYPERLINK(\"https://invalid.example\")\n+SUM(A1)\n@formula\n-10 & <عربي>"
        val book = workbook(base.copy(medications = listOf(base.medications.single().copy(note = note, brand = "HIDDEN BRAND"))))
        val rows = book.rows("Medications")
        assertEquals(3, rows.first().size)
        assertEquals(note, rows.drop(1).joinToString("") { it.last() })
        assertFalse(rows.flatten().contains("HIDDEN BRAND"))
        assertFalse(rows.flatten().any { it.contains("botToken") || it.contains("snapshotKey") })
    }
    @Test fun longNotesPreserveEveryCharacterAcrossIdentifiedContinuationRows() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.AVAILABILITY, ReportField.NOTE)))
        val note = ("عربي English 😀 & < > \r\n_x0001_\u0001\t".repeat(1300)) + "END"
        assertTrue(note.length > 32767)
        val book = workbook(base.copy(medications = listOf(base.medications.single().copy(note = note))))
        val rows = book.rows("Medications").drop(1)
        assertTrue(rows.size > 1)
        assertEquals(note, rows.joinToString("") { it[3] })
        assertEquals("1", rows.first()[0])
        assertTrue(rows.drop(1).all { it[0].startsWith("1 · ") && it[0].contains(uiText(R.string.report_continuation)) })
        assertTrue(rows.all { it[1] == "Fixture medication" && it[2] == "متوفر" })
        assertTrue(rows.flatten().all { it.length <= 32767 })
        assertTrue(rows.all { row -> row.all { it.isEmpty() || (!it.first().isLowSurrogate() && !it.last().isHighSurrogate()) } })
        assertTrue(book.elements("xl/worksheets/sheet2.xml", "row").all { it.getAttribute("ht").toInt() in 20..409 })
    }
    @Test fun longScientificIdentityUsesExplicitMedicationReferencesWithoutTruncation() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.NOTE), names = ReportNames.BOTH))
        val scientific = "مادة علمية 😀 ".repeat(3000)
        val note = "detail ".repeat(40000)
        val book = workbook(base.copy(medications = listOf(base.medications.single().copy(genericAr = scientific, genericEn = "English identity", note = note))))
        val rows = book.rows("Medications").drop(1)
        val reference = uiText(R.string.report_medication_continuation, "1")
        assertTrue(rows.any { it[1] == reference })
        assertEquals(scientific + "\nEnglish identity", rows.filterNot { it[1] == reference }.joinToString("") { it[1] })
        assertEquals(note, rows.joinToString("") { it[2] })
    }
    @Test fun sectionColumnsRetainDistinctStatusesColorsAndUniqueHeaders() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.AVAILABILITY), availability = ReportAvailability.PER_SECTION))
        val sections = listOf(base.sections.single().copy(nameAr = "اسم مكرر"), Section(newId(), "اسم مكرر"), Section(newId(), "اسم مكرر", isActive = false))
        val med = base.medications.single()
        val book = workbook(base.copy(sections = sections, availability = listOf(
            Availability(med.id, sections[0].id, AvailabilityStatus.AVAILABLE, F.time),
            Availability(med.id, sections[1].id, AvailabilityStatus.LOW, F.time))))
        assertEquals(listOf("متوفر", "توفر محدود", "غير متوفر"), book.rows("Medications")[1].drop(2))
        val cells = book.elements("xl/worksheets/sheet2.xml", "c").filter { it.getAttribute("r") in setOf("C2", "D2", "E2") }
        assertEquals(listOf("3", "4", "5"), cells.map { it.getAttribute("s") })
        val fills = book.elements("xl/styles.xml", "fgColor").map { it.getAttribute("rgb") }
        assertTrue(fills.containsAll(listOf("FFE5F3E8", "FFFFF1CE", "FFFFE2DF")))
    }
    @Test fun categoryGroupingPreservesOrdinaryRowsAndAlternatingStyles() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.CATEGORY)))
        val one = base.medications.single(); val two = one.copy(id = newId(), genericEn = "Second")
        val book = workbook(base.copy(medications = listOf(one, two)))
        assertEquals(3, book.rows("Medications").size)
        assertEquals(listOf("1", "2"), book.rows("Medications").drop(1).map { it.first() })
        assertTrue(book.rows("Medications").drop(1).all { it.last() == base.categories.single { category -> category.id == one.categoryId }.nameAr })
        assertTrue(book.elements("xl/worksheets/sheet2.xml", "mergeCells").isEmpty())
        val styles = book.elements("xl/worksheets/sheet2.xml", "c").filter { it.getAttribute("r") in setOf("A2", "A3") }.map { it.getAttribute("s") }
        assertEquals(listOf("0", "2"), styles)
    }
    @Test fun manySectionColumnsAndLongHeadingsKeepFullScopeInSummary() {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.AVAILABILITY), availability = ReportAvailability.PER_SECTION))
        val sections = (1..30).map { Section(newId(), "قسم عربي طويل ".repeat(20) + it) }
        val book = workbook(base.copy(sections = sections, availability = emptyList()))
        assertEquals(32, book.rows("Medications").first().size)
        assertEquals("A1:AF2", book.elements("xl/tables/table1.xml", "table").single().getAttribute("ref"))
        val summary = book.rows("Summary").drop(1)
        val start = summary.indexOfFirst { it[0] == uiText(R.string.report_sections) }
        assertTrue(start >= 0)
        val value = buildString {
            append(summary[start][1])
            for (row in summary.drop(start + 1)) {
                if (row[0] != uiText(R.string.report_continuation)) break
                append(row[1])
            }
        }
        assertEquals(sections.joinToString("، ") { it.nameAr }, value)
        assertTrue(book.rows("Medications")[1].drop(2).all { it == "غير متوفر" })
    }
    @Test fun emptyWorkbookHasAValidFilterTableAndZeroCount() {
        val book = workbook(snapshot().copy(medications = emptyList()))
        assertEquals(2, book.rows("Medications").size)
        assertTrue(book.rows("Medications")[1].all { it.isEmpty() })
        assertEquals("A1:E2", book.elements("xl/tables/table1.xml", "table").single().getAttribute("ref"))
        assertTrue(book.rows("Summary").contains(listOf(uiText(R.string.report_summary_count), "0")))
    }
    @Test fun writerLeavesDestinationOpenAndPropagatesWriteFailures() {
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        ExcelReports.write(output, snapshot(), EmptyCoroutineContext)
        assertFalse(closed); ReportWorkbook.read(output.toByteArray().inputStream()).assertStructure()
        val failure = IOException("provider write failure")
        var writes = 0
        val broken = object : OutputStream() {
            override fun write(b: Int) { writes++; throw failure }
            override fun close() { closed = true }
        }
        assertSame(failure, assertThrows(IOException::class.java) { ExcelReports.write(broken, snapshot(), EmptyCoroutineContext) })
        assertTrue(writes > 0); assertFalse(closed)
    }
    @Test fun cancelledWriterStopsBeforeSuccessAndNeverClosesProvider() {
        val job = Job(); job.cancel()
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true } }
        assertThrows(CancellationException::class.java) { ExcelReports.write(output, snapshot(), job) }
        assertFalse(closed)
        val during = Job(); var observed = 0
        val cancelling = object : OutputStream() {
            override fun write(b: Int) { observed++; if (observed >= 500) during.cancel() }
        }
        assertThrows(CancellationException::class.java) { ExcelReports.write(cancelling, snapshot(), during) }
        assertTrue("Cancellation injection must occur after output begins", observed >= 500)
    }
    @Test fun pdfPaperOrientationAndDensityOptionsRemainRenderable(): Unit = runBlocking {
        val pdf = PdfReports(profile.context)
        for (paper in ReportPaper.entries) for (landscape in listOf(false, true)) for (compact in listOf(false, true)) {
            val captured = snapshot(ReportOptions(paper = paper, landscape = landscape, compact = compact))
            val file = File(profile.root, "${paper.name}-$landscape-$compact.pdf")
            pdf.render(captured).use { document -> file.outputStream().use { document.writeTo(it) } }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount >= 1)
                val portrait = if (paper == ReportPaper.A5) 420 to 595 else 595 to 842
                for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page ->
                    assertEquals(if (landscape) portrait.second else portrait.first, page.width)
                    assertEquals(if (landscape) portrait.first else portrait.second, page.height)
                }
            }
        }
    }
    @Test fun compactPdfUsesNoMorePagesThanComfortableForTheSameRecords(): Unit = runBlocking {
        val base = snapshot()
        val records = (1..100).map { base.medications.single().copy(id = newId(), genericAr = "اسم علمي", genericEn = "Medicine $it") }
        suspend fun pages(compact: Boolean): Int {
            val captured = base.copy(medications = records, request = base.request.copy(options = base.request.options.copy(compact = compact)))
            val file = File(profile.root, "density-$compact.pdf")
            PdfReports(profile.context).render(captured).use { document -> file.outputStream().use { document.writeTo(it) } }
            return PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> renderer.pageCount }
        }
        val compact = pages(true); val comfortable = pages(false)
        assertTrue(compact > 1); assertTrue(comfortable > 1)
        assertTrue("Compact=$compact, comfortable=$comfortable", compact <= comfortable)
    }
    @Test fun cancelledPdfRenderingPropagatesBeforeAnyDestinationIsOpened(): Unit = runBlocking {
        var completed = false
        val work = launch(start = CoroutineStart.LAZY) {
            currentCoroutineContext().cancel()
            PdfReports(profile.context).render(snapshot()).use { completed = true }
        }
        work.start(); work.join()
        assertTrue(work.isCancelled); assertFalse(completed)
    }
}
