package com.aqaqeer.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.fixtures.SuiteRemote
import com.aqaqeer.app.admin.catalog
import com.aqaqeer.app.admin.newId
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs

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
        val document = PdfReports(profile.context).render(captured)
        try { document.writeTo(bytes) } finally { document.close() }
        assertTrue(bytes.toByteArray().take(5).toByteArray().contentEquals("%PDF-".toByteArray()))
        assertEquals(request, captured.request)
        assertEquals(before, profile.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
    }

    @Test fun workbookHasSummaryFilterableRowsRtlFrozenHeadersAndPrintSettings() {
        val snapshot = snapshot(ReportOptions(fields = ReportField.entries.toSet(), paper = ReportPaper.A4,
            landscape = true, sideMargins = ReportSideMargins.WIDE))
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
            assertEquals("11", print.getAttribute("paperSize")); assertEquals("landscape", print.getAttribute("orientation"))
            assertEquals("1", print.getAttribute("fitToWidth")); assertEquals("0", print.getAttribute("fitToHeight"))
            val margins = book.elements(path, "pageMargins").single()
            assertEquals("0.45", margins.getAttribute("left"))
            assertEquals("0.45", margins.getAttribute("right"))
            assertEquals("0.4", margins.getAttribute("top"))
            assertEquals("0.4", margins.getAttribute("bottom"))
            assertTrue(book.elements(path, "col").all { it.getAttribute("width").toDouble() >= 10 })
        }
        assertEquals("'Medications'!\$1:\$1", book.elements("xl/workbook.xml", "definedName").single().textContent)
    }
    @Test fun workbookPreservesCustomOrderWithOverallAndSectionStatusesTogether() {
        val base = snapshot(ReportOptions(
            fields = setOf(ReportField.SCIENTIFIC, ReportField.STRENGTH, ReportField.AVAILABILITY),
            names = ReportNames.ENGLISH,
            availability = ReportAvailability.BOTH,
            fieldOrder = listOf(ReportField.STRENGTH, ReportField.AVAILABILITY, ReportField.SCIENTIFIC),
        ))
        val inStock = base.sections.single()
        val shortage = Section(newId(), "فرع النقص")
        val medication = base.medications.single()
        val book = workbook(base.copy(sections = listOf(shortage, inStock)))
        val rows = book.rows("Medications")
        val labels = listOf(uiText(R.string.report_field_strength), uiText(R.string.report_overall_heading),
            shortage.nameAr, inStock.nameAr, uiText(R.string.report_field_scientific))
        assertEquals(labels.mapIndexed { index, label -> "${index + 1} · $label" }, rows.first().drop(1))
        assertEquals(listOf(medication.strength, "متوفر", "غير متوفر", "متوفر", medication.genericEn),
            rows.single { it.first() == "1" }.drop(1))
    }
    @Test fun expirySortChangesDirectionAndKeepsUndatedLastAndScientificTiesStable(): Unit = runBlocking {
        profile.create()
        suspend fun save(name: String, date: LocalDate?): String {
            val medication = profile.medication(name)
            profile.admin.saveMedication(medication.copy(expiryDate = date))
            return medication.id
        }
        val early = LocalDate.of(2027, 1, 1)
        val late = LocalDate.of(2028, 1, 1)
        val beta = save("Beta", late)
        val undated = save("Aardvark", null)
        val alpha = save("Alpha", late)
        val soon = save("Zeta", early)
        val repo = ReportRepository(profile.admin, DocumentOutput(profile.context), PdfReports(profile.context))
        val request = ReportRequest(includeUnpublished = true, options = ReportOptions(sort = ReportSort.EXPIRY))

        assertEquals(listOf(soon, alpha, beta, undated), repo.capture(request).medications.map { it.id })
        val descending = request.copy(options = request.options.copy(sortDirection = ReportSortDirection.DESCENDING))
        assertEquals(listOf(alpha, beta, soon, undated), repo.capture(descending).medications.map { it.id })
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
    @Test fun sectionColumnsRetainDistinctStatusesColorsAndUniqueHeaders(): Unit = runBlocking {
        val base = snapshot(ReportOptions(fields = setOf(ReportField.SCIENTIFIC, ReportField.AVAILABILITY), availability = ReportAvailability.PER_SECTION))
        val sections = listOf(base.sections.single().copy(nameAr = "اسم مكرر"), Section(newId(), "اسم مكرر"), Section(newId(), "اسم مكرر", isActive = false))
        val med = base.medications.single()
        val captured = base.copy(sections = sections, availability = listOf(
            Availability(med.id, sections[0].id, AvailabilityStatus.AVAILABLE, F.time),
            Availability(med.id, sections[1].id, AvailabilityStatus.LOW, F.time)))
        val book = workbook(captured)
        assertEquals(listOf("متوفر", "توفر محدود", "غير متوفر"), book.rows("Medications")[1].drop(2))
        val cells = book.elements("xl/worksheets/sheet2.xml", "c").filter { it.getAttribute("r") in setOf("C2", "D2", "E2") }
        assertEquals(listOf("3", "4", "5"), cells.map { it.getAttribute("s") })
        val expectedFills = listOf("FFD8F0FF", "FFF0D589", "FFEFB594")
        val stylesPath = "xl/styles.xml"
        val fills = book.elements(stylesPath, "fill")
        val fonts = book.elements(stylesPath, "font")
        val styles = book.elements(stylesPath, "cellXfs").single().getElementsByTagNameNS("*", "xf")
        assertEquals(expectedFills, cells.map { cell ->
            val style = styles.item(cell.getAttribute("s").toInt()) as Element
            val font = fonts[style.getAttribute("fontId").toInt()]
            val textColor = font.getElementsByTagNameNS("*", "color").item(0) as Element
            assertEquals("FF000000", textColor.getAttribute("rgb"))
            val fill = fills[style.getAttribute("fillId").toInt()]
            (fill.getElementsByTagNameNS("*", "fgColor").item(0) as Element).getAttribute("rgb")
        })

        val file = File(profile.root, "section-stock-palette.pdf")
        val document = PdfReports(profile.context).render(captured)
        try { file.outputStream().use { document.writeTo(it) } } finally { document.close() }
        assertPdfStockPalette(file, expectedFills.map { it.toLong(16).toInt() })
    }

    private fun assertPdfStockPalette(file: File, colors: List<Int>) {
        fun matches(pixel: Int, expected: Int) = abs(Color.red(pixel) - Color.red(expected)) <= 2 &&
            abs(Color.green(pixel) - Color.green(expected)) <= 2 && abs(Color.blue(pixel) - Color.blue(expected)) <= 2

        val navy = Color.rgb(0x14, 0x21, 0x3C)
        val scale = 3
        val transform = Matrix().apply { setScale(scale.toFloat(), scale.toFloat()) }
        var stockRow = false
        var navyRule = false
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val row = IntArray(bitmap.width)
                        val counts = IntArray(colors.size)
                        val first = IntArray(colors.size)
                        for (y in 0 until bitmap.height) {
                            bitmap.getPixels(row, 0, row.size, 0, y, row.size, 1)
                            if (!navyRule && index == 0 && y < 100 * scale) {
                                navyRule = row.count { matches(it, navy) } > row.size / 2
                            }
                            if (!stockRow) {
                                counts.fill(0)
                                first.fill(-1)
                                row.forEachIndexed { x, pixel ->
                                    val color = colors.indexOfFirst { matches(pixel, it) }
                                    if (color >= 0) {
                                        if (counts[color] == 0) first[color] = x
                                        counts[color]++
                                    }
                                }
                                stockRow = counts.all { it > 8 * scale } &&
                                    (1 until first.size).all { first[it - 1] > first[it] }
                            }
                            if (stockRow && navyRule) break
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                if (stockRow && navyRule) break
            }
        }
        assertTrue("PDF stock cells show Available, Low and Out in right-to-left column order", stockRow)
        assertTrue("PDF first-page header has a logo-navy rule", navyRule)
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
            val margins = if (landscape) ReportSideMargins.WIDE else ReportSideMargins.NARROW
            val captured = snapshot(ReportOptions(paper = paper, landscape = landscape, compact = compact, sideMargins = margins))
            val file = File(profile.root, "${paper.name}-$landscape-$compact.pdf")
            val document = pdf.render(captured)
            try { file.outputStream().use { document.writeTo(it) } } finally { document.close() }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount >= 1)
                for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page ->
                    assertEquals(if (landscape) 595 else 420, page.width)
                    assertEquals(if (landscape) 420 else 595, page.height)
                    if (paper == ReportPaper.A5 && i == 0) {
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val opening = IntArray(page.width * (page.height / 2))
                            bitmap.getPixels(opening, 0, page.width, 0, 0, page.width, page.height / 2)
                            assertTrue("A5 opening should contain printed content",
                                opening.count { Color.red(it) < 160 && Color.green(it) < 160 && Color.blue(it) < 160 } > 100)
                            val inset = if (landscape) 32 else 12
                            assertTrue("The opening section band should respect both A5 side margins",
                                (46 until page.height / 2).any { y ->
                                    val row = y * page.width
                                    val band = opening[row + inset + 2]
                                    band != Color.WHITE &&
                                        opening[row + page.width - inset - 3] == band &&
                                        opening[row + inset - 2] == Color.WHITE &&
                                        opening[row + page.width - inset + 1] == Color.WHITE
                                })
                        } finally {
                            bitmap.recycle()
                        }
                    }
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
            val document = PdfReports(profile.context).render(captured)
            try { file.outputStream().use { document.writeTo(it) } } finally { document.close() }
            return PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer -> renderer.pageCount }
        }
        val compact = pages(true); val comfortable = pages(false)
        assertTrue(compact > 1); assertTrue(comfortable > 1)
        assertTrue("Compact=$compact, comfortable=$comfortable", compact <= comfortable)
    }

    @Test fun pdfOverPageCapLeavesTheUserSelectedDestinationUntouched(): Unit = runBlocking {
        profile.create()
        val medicine = profile.medication("PDF page limit fixture")
        val note = "x\n".repeat(70_000) + "x"
        profile.admin.saveMedication(medicine.copy(note = note))
        val repository = ReportRepository(profile.admin, DocumentOutput(profile.context), PdfReports(profile.context))
        val snapshot = repository.capture(ReportRequest(includeUnpublished = true, options = ReportOptions(
            fields = setOf(ReportField.SCIENTIFIC, ReportField.NOTE), landscape = true)))
        assertTrue("Capture retained all forced line breaks", note == snapshot.medications.single().note)

        // Even a page with no margins, header or footer cannot hold enough forced lines.
        val paint = TextPaint().apply {
            typeface = requireNotNull(ResourcesCompat.getFont(profile.context, R.font.noto_naskh_arabic_regular))
            textSize = 9.2f
        }
        val sample = StaticLayout.Builder.obtain("x\nx", 0, 3, paint, ReportA5.heightPoints)
            .setIncludePad(true).setLineSpacing(1f, 1f).build()
        val lineAdvance = sample.getLineBaseline(1) - sample.getLineBaseline(0)
        assertTrue(lineAdvance > 0)
        val maxLinesPerPage = ReportA5.widthPoints / lineAdvance + 1
        assertTrue("Fixture must require more than 1,000 A5 landscape pages", note.count { it == '\n' } > 1000 * maxLinesPerPage)

        val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".suite-documents"
        val destination = Uri.parse("content://$authority/suite-${UUID.randomUUID()}")
        val resolver = profile.context.contentResolver
        val original = "Existing destination bytes".toByteArray()
        try {
            requireNotNull(resolver.openOutputStream(destination, "wt")).use { it.write(original) }
            val rejected = assertThrows(CsvProblem::class.java) { runBlocking { repository.pdf(destination, snapshot) } }
            assertEquals(uiText(R.string.report_pdf_too_large), rejected.reason)
            assertArrayEquals(original, requireNotNull(resolver.openInputStream(destination)).use { it.readBytes() })
        } finally {
            resolver.delete(destination, null, null)
        }
    }

    @Test fun cancelledPdfRenderingPropagatesBeforeAnyDestinationIsOpened(): Unit = runBlocking {
        var completed = false
        val work = launch(start = CoroutineStart.LAZY) {
            currentCoroutineContext().cancel()
            val document = PdfReports(profile.context).render(snapshot())
            try { completed = true } finally { document.close() }
        }
        work.start(); work.join()
        assertTrue(work.isCancelled); assertFalse(completed)
    }
}
