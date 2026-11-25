package com.aqaqeer.app.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.text.BidiFormatter
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText
import com.aqaqeer.app.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.min

/** Render before opening the destination. Actual Android rendering needs separate acceptance. */
class PdfReports @Inject constructor(@param:ApplicationContext private val context: Context) {
    internal suspend fun render(snapshot: ReportSnapshot): PdfDocument {
        val normal = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_naskh_arabic_regular))
        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_naskh_arabic_bold))
        val emblem = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.aqaqeer_logo))
        return TableReport(snapshot, normal, bold, emblem, currentCoroutineContext()).render()
    }
}

private data class PdfPart(
    val text: String, val strong: Boolean = false, val size: Float = 8.2f,
    val prefix: String = "", val isolate: Boolean = false,
)
private data class PdfSlice(val layout: StaticLayout, val top: Int, val height: Int, val offset: Float)
private data class PdfPortion(val slices: List<PdfSlice>, val height: Float)

/** Content-sized table columns plus full-width optional details. */
private class TableReport(
    private val snapshot: ReportSnapshot, private val normal: Typeface, private val bold: Typeface,
    private val emblem: Bitmap, private val owner: CoroutineContext,
) {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private var category: Category? = null
    private var tableActive = false
    private val options = snapshot.request.options
    private val paper = options.paper
    private val pageWidth = if (options.landscape) paper.heightPoints else paper.widthPoints
    private val pageHeight = if (options.landscape) paper.widthPoints else paper.heightPoints
    private val margin = options.sideMargins.pdfPoints
    private val right = pageWidth - margin
    private val bottom = pageHeight - 38f
    private val width = (right - margin).toInt()
    private val padding = if (options.compact) 2f else 4f
    private val reportColumns = snapshot.columns()
    private val columns = reportColumns.filter { it.core }
    private val details = reportColumns.filterNot { it.core }
    private val weights = columns.map { if (it.field == ReportField.SCIENTIFIC) 3.2f else 1.2f }
    private val widths = weights.map { width * it / weights.sum() }
    private val lefts = widths.runningFold(right) { x, w -> x - w }.drop(1)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(122, 90, 18); strokeWidth = 0.8f }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(215, 218, 221); strokeWidth = 0.4f }
    private val outline = Paint(border).apply { style = Paint.Style.STROKE }
    private val bidi = BidiFormatter.getInstance(true)
    private val canvas get() = requireNotNull(page).canvas

    private fun short(text: String, limit: Int): String {
        var end = min(text.length, limit * 4)
        if (end < text.length && end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        val compact = text.substring(0, end).replace(Regex("\\s+"), " ").trim()
        val length = compact.codePointCount(0, compact.length)
        val label = if (length <= limit) compact else compact.substring(0, compact.offsetByCodePoints(0, limit))
        return label + if (end < text.length || length > limit) "…" else ""
    }

    private fun layout(text: String, cellWidth: Int, strong: Boolean = false, size: Float = 8.2f,
                       centered: Boolean = false): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (strong) bold else normal
            textSize = size
            color = Color.BLACK
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, cellWidth)
            .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(true).setLineSpacing(1f, 1f).build()
    }

    private fun draw(block: StaticLayout, x: Float, top: Float) {
        canvas.withTranslation(x, top) { block.draw(this) }
    }

    /** Only one bounded layout per field is retained; no long field is discarded. */
    private inner class TextFlow(private val parts: List<PdfPart>, private val cellWidth: Int) {
        private var part = 0
        private var offset = 0
        private var current: StaticLayout? = null
        private var firstLine = 0
        val done get() = part >= parts.size && current == null

        private fun load(): StaticLayout? {
            if (current != null) return current
            if (part >= parts.size) return null
            owner.ensureActive()
            val value = parts[part]
            var end = min(offset + 8192, value.text.length)
            if (end < value.text.length && end > offset && Character.isHighSurrogate(value.text[end - 1])) end--
            val chunk = value.text.substring(offset, end)
            val text = (if (offset == 0) value.prefix else "") +
                if (value.isolate) bidi.unicodeWrap(chunk) else chunk
            current = layout(text, cellWidth, value.strong, value.size)
            firstLine = 0
            offset = end
            return current
        }

        fun take(available: Float): PdfPortion {
            val slices = mutableListOf<PdfSlice>()
            var used = 0f
            while (!done) {
                owner.ensureActive()
                val block = load() ?: break
                val top = block.getLineTop(firstLine)
                var end = firstLine
                while (end < block.lineCount && block.getLineBottom(end) - top <= available - used) end++
                if (end == firstLine) break
                val height = block.getLineBottom(end - 1) - top
                slices += PdfSlice(block, top, height, used)
                used += height
                firstLine = end
                if (firstLine == block.lineCount) {
                    current = null
                    if (offset >= parts[part].text.length) { part++; offset = 0 }
                    used += min(2f, max(0f, available - used))
                }
            }
            return PdfPortion(slices, used)
        }
    }

    private fun drawPortion(portion: PdfPortion, x: Float, top: Float, cellWidth: Int) {
        portion.slices.forEach { slice ->
            canvas.withClip(x, top + slice.offset, x + cellWidth, top + slice.offset + slice.height) {
                withTranslation(x, top + slice.offset - slice.top) { slice.layout.draw(this) }
            }
        }
    }

    private fun newPage() {
        owner.ensureActive()
        page?.let { document.finishPage(it); page = null }
        if (++pageNumber > 1000) throw CsvProblem(uiText(R.string.report_pdf_too_large))
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas.drawBitmap(emblem, null, RectF(margin, 16f, margin + 30f, 46f), null)
        val title = layout(bidi.unicodeWrap(short(snapshot.pharmacyName, 80)), width - 42, true, 10f)
        draw(title, margin + 42f, 16f)
        y = max(48f, 16f + title.height)
        val metadata = listOf(
            uiText(if (snapshot.request.includeUnpublished) R.string.report_source_local else R.string.report_source_published),
            MedicationPresentation.timestamp(snapshot.generatedAt, snapshot.timezone) + " · " + snapshot.timezone,
        )
        metadata.forEach { text -> val block = layout(bidi.unicodeWrap(text), width, size = 7.2f); draw(block, margin, y); y += block.height }
        canvas.drawLine(margin, y + 2f, right, y + 2f, rule); y += 6f
        canvas.drawLine(margin, pageHeight - 27f, right, pageHeight - 27f, border)
        draw(layout(uiText(R.string.report_pdf_page, pageNumber.toString()), width, size = 7.5f, centered = true), margin, pageHeight - 25f)
        if (tableActive) tableHeader()
        require(y + 45f < bottom) { "Repeated PDF headings leave no usable row space" }
    }
    private fun plain(parts: List<PdfPart>) {
        val flow = TextFlow(parts, width)
        while (!flow.done) {
            val portion = flow.take(bottom - y)
            if (portion.slices.isEmpty()) { newPage(); continue }
            drawPortion(portion, margin, y, width); y += portion.height + padding
            if (!flow.done) newPage()
        }
    }
    private fun tableHeader() {
        category?.let {
            val heading = layout(bidi.unicodeWrap(short(it.nameAr, 80)), width - 8, true, 9.4f)
            fill.color = Color.rgb(246, 239, 218)
            canvas.drawRect(margin, y, right, y + heading.height + padding * 2, fill)
            draw(heading, margin + padding, y + padding); y += heading.height + padding * 2
        }
        val headings = columns.mapIndexed { i, col ->
            layout(bidi.unicodeWrap(short(col.title, 24)), (widths[i] - padding * 2).toInt(), true, 8.2f, centered = true)
        }
        val height = headings.maxOf { it.height }.toFloat() + padding * 2
        fill.color = Color.rgb(242, 244, 245); canvas.drawRect(margin, y, right, y + height, fill)
        headings.forEachIndexed { i, h -> draw(h, lefts[i] + padding, y + padding) }
        grid(y, height, true); y += height
    }
    private fun grid(top: Float, height: Float, cells: Boolean) {
        canvas.drawRect(margin, top, right, top + height, outline)
        if (cells) lefts.dropLast(1).forEach { x -> canvas.drawLine(x, top, x, top + height, border) }
    }
    private fun medication(med: Medication, number: Int) {
        val flows = columns.mapIndexed { i, col -> TextFlow(listOf(PdfPart(col.value(snapshot, med),
            strong = col.field == ReportField.SCIENTIFIC, size = if (col.field == ReportField.SCIENTIFIC) 9.4f else 8.2f,
            prefix = if (col.field == ReportField.SCIENTIFIC) "$number · " else "", isolate = true)),
            (widths[i] - padding * 2).toInt()) }
        val stockHeight = columns.mapIndexed { i, col -> if (col.field == ReportField.AVAILABILITY)
            layout(col.value(snapshot, med), (widths[i] - padding * 2).toInt()).height.toFloat() else 0f }.maxOrNull() ?: 0f
        val continuationText = uiText(R.string.report_medication_continuation, number.toString()) +
            columns.filter { it.field == ReportField.AVAILABILITY }.joinToString("", prefix = "") { col ->
                " · ${bidi.unicodeWrap(short(col.title, 16))}: ${col.value(snapshot, med)}"
            }
        var continued = false
        while (flows.any { !it.done }) {
            owner.ensureActive()
            val continuation = if (continued) layout(continuationText, width, true, 7.4f) else null
            val prefix = continuation?.height?.toFloat() ?: 0f
            if (bottom - y < prefix + padding * 2 + max(24f, stockHeight)) newPage()
            continuation?.let { draw(it, margin, y); y += it.height }
            val portions = flows.map { it.take(bottom - y - padding * 2) }
            if (portions.all { it.slices.isEmpty() }) { newPage(); continue }
            val height = max(stockHeight, portions.maxOf { it.height }) + padding * 2
            columns.forEachIndexed { i, col ->
                fill.color = when (col.status(snapshot, med)) {
                    AvailabilityStatus.AVAILABLE -> Color.rgb(237, 246, 239)
                    AvailabilityStatus.LOW -> Color.rgb(255, 247, 225)
                    AvailabilityStatus.OUT -> Color.rgb(255, 237, 235)
                    null -> if (number % 2 == 0) Color.rgb(249, 250, 251) else Color.WHITE
                }
                canvas.drawRect(lefts[i], y, lefts[i] + widths[i], y + height, fill)
                drawPortion(portions[i], lefts[i] + padding, y + padding, (widths[i] - padding * 2).toInt())
                // Repeat the short stock label when other core cells continue onto another page.
                if (portions[i].slices.isEmpty() && col.field == ReportField.AVAILABILITY)
                    draw(layout(col.value(snapshot, med), (widths[i] - padding * 2).toInt()), lefts[i] + padding, y + padding)
            }
            grid(y, height, true); y += height
            if (flows.any { !it.done }) { continued = true; newPage() }
        }
        val extra = details.mapNotNull { col -> col.value(snapshot, med).takeIf { it.isNotEmpty() }?.let {
            PdfPart(it, prefix = col.title + ": ", isolate = true)
        } }
        if (extra.isNotEmpty()) {
            val flow = TextFlow(extra, width - (padding * 2).toInt())
            var detailContinued = false
            while (!flow.done) {
                owner.ensureActive()
                if (bottom - y < 28f) { newPage(); detailContinued = true }
                if (detailContinued) {
                    val title = layout(continuationText, width, true, 7.4f)
                    draw(title, margin, y); y += title.height
                }
                val portion = flow.take(bottom - y - padding * 2)
                if (portion.slices.isEmpty()) { newPage(); detailContinued = true; continue }
                drawPortion(portion, margin + padding, y + padding, width - (padding * 2).toInt())
                grid(y, portion.height + padding * 2, false); y += portion.height + padding * 2
                if (!flow.done) { newPage(); detailContinued = true }
            }
        }
    }
    fun render(): PdfDocument {
        try {
            require(ReportField.SCIENTIFIC in options.fields)
            newPage()
            snapshot.summary().forEach { (label, value) -> plain(listOf(PdfPart(value, prefix = "$label: ", isolate = true))) }
            if (snapshot.medications.isEmpty()) plain(listOf(PdfPart(uiText(R.string.report_empty))))
            var previous: String? = null
            snapshot.medications.forEachIndexed { i, med ->
                owner.ensureActive()
                if (!tableActive || options.groupByCategory && med.categoryId != previous) {
                    category = if (options.groupByCategory) snapshot.categories.first { it.id == med.categoryId } else null
                    category?.let { if (short(it.nameAr, 80) != it.nameAr) {
                        tableActive = false
                        plain(listOf(PdfPart(it.nameAr, true, 9.4f, isolate = true)))
                    } }
                    tableActive = true
                    // Reserve the actual category/column headings and a readable first line.
                    val categoryHeight = category?.let { layout(bidi.unicodeWrap(short(it.nameAr, 80)), width - 8, true, 9.4f).height + padding * 2 } ?: 0f
                    val columnHeight = columns.mapIndexed { n, col -> layout(bidi.unicodeWrap(short(col.title, 24)), (widths[n] - padding * 2).toInt(), true, 8.2f, true).height }.maxOf { it }
                    if (bottom - y < categoryHeight + columnHeight + padding * 2 + 32f) newPage() else tableHeader()
                    previous = med.categoryId
                }
                medication(med, i + 1)
            }
            page?.let { document.finishPage(it); page = null }
            return document
        } catch (error: Throwable) {
            page?.let { runCatching { document.finishPage(it) }; page = null }
            document.close(); throw error
        }
    }
}
