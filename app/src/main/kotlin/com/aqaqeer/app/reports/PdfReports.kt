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
        return A5Report(snapshot, normal, bold, emblem, currentCoroutineContext()).render()
    }
}

private data class PdfPart(
    val text: String, val strong: Boolean = false, val size: Float = 8.2f,
    val prefix: String = "", val isolate: Boolean = false,
)
private data class PdfSlice(val layout: StaticLayout, val top: Int, val height: Int, val offset: Float)
private data class PdfPortion(val slices: List<PdfSlice>, val height: Float)

/** A5 portrait, with one detail column at the RTL start and up to five status columns. */
private class A5Report(
    private val snapshot: ReportSnapshot,
    private val normal: Typeface,
    private val bold: Typeface,
    private val emblem: Bitmap,
    private val owner: CoroutineContext,
) {
    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = 0f
    private var category: Category? = null
    private var tableActive = false
    private val margin = 20f
    private val right = 400f
    private val bottom = 557f
    private val width = 380
    private val padding = 4f
    private val sectionWidth = when (snapshot.sections.size) { 1 -> 56f; 2 -> 52f; else -> 36f }
    private val detailLeft = margin + sectionWidth * snapshot.sections.size
    private val detailWidth = (right - detailLeft - padding * 2).toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(122, 90, 18); strokeWidth = 0.8f }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(215, 218, 221); strokeWidth = 0.4f }
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
        if (++pageNumber > 1000) throw CsvProblem("التقرير يحتاج أكثر من ١٠٠٠ صفحة؛ قلّل نطاقه")
        page = document.startPage(PdfDocument.PageInfo.Builder(420, 595, pageNumber).create())
        canvas.drawBitmap(emblem, null, RectF(margin, 18f, 54f, 52f), null)
        var headerY = 17f
        listOf(
            layout("تقرير عقاقير", 334, true, 12.5f),
            layout(bidi.unicodeWrap(short(snapshot.pharmacyName, 100)), 334, true, 9f),
        ).forEach { block -> draw(block, 66f, headerY); headerY += block.height + 1f }
        y = max(headerY, 55f)
        val metadata = listOf(
            "التوقيت: ${bidi.unicodeWrap(snapshot.timezone)} · أُنشئ: ${bidi.unicodeWrap(MedicationPresentation.timestamp(snapshot.generatedAt, snapshot.timezone))}",
            "النشر: " + (snapshot.publishedAt?.let { bidi.unicodeWrap(MedicationPresentation.timestamp(it, snapshot.timezone)) } ?: "لم يُنشر") +
                " · " + if (snapshot.request.includeUnpublished) "يشمل تعديلات غير منشورة" else "البيانات المنشورة فقط",
        )
        metadata.forEach { text -> val block = layout(text, width, size = 7.2f); draw(block, margin, y); y += block.height + 1f }
        canvas.drawLine(margin, y + 2f, right, y + 2f, rule)
        y += 9f
        canvas.drawLine(margin, 568f, right, 568f, border)
        draw(layout("عقاقير · صفحة ${MedicationPresentation.easternDigits(pageNumber.toString())}", width, size = 7.5f, centered = true), margin, 571f)
        if (tableActive) tableHeader()
        require(y + 45f < bottom) { "Repeated PDF headings leave no usable row space" }
    }

    private fun plain(parts: List<PdfPart>) {
        val flow = TextFlow(parts, width)
        while (!flow.done) {
            owner.ensureActive()
            val portion = flow.take(bottom - y)
            if (portion.slices.isEmpty()) { newPage(); continue }
            drawPortion(portion, margin, y, width)
            y += portion.height + 3f
            if (!flow.done) newPage()
        }
    }

    private fun tableHeader() {
        val heading = layout(short(requireNotNull(category).nameAr, 80), width - 8, true, 9.4f)
        fill.color = Color.rgb(246, 239, 218)
        canvas.drawRect(margin, y, right, y + heading.height + 6f, fill)
        draw(heading, margin + 4f, y + 3f)
        y += heading.height + 8f
        val detail = layout("الدواء والتفاصيل", detailWidth, true, 8.5f)
        val sections = snapshot.sections.mapIndexed { index, section ->
            layout("${MedicationPresentation.easternDigits((index + 1).toString())}\n${short(section.nameAr, 20)}",
                (sectionWidth - padding * 2).toInt(), true, 7.4f, centered = true)
        }
        val height = max(detail.height, sections.maxOf { it.height }).toFloat() + padding * 2
        fill.color = Color.rgb(242, 244, 245)
        canvas.drawRect(margin, y, right, y + height, fill)
        draw(detail, detailLeft + padding, y + padding)
        sections.forEachIndexed { index, block -> draw(block, detailLeft - (index + 1) * sectionWidth + padding, y + padding) }
        grid(y, height)
        y += height
    }

    private fun grid(top: Float, height: Float) {
        canvas.drawLine(margin, top, right, top, border)
        canvas.drawLine(margin, top + height, right, top + height, border)
        canvas.drawLine(right, top, right, top + height, border)
        for (index in 0..snapshot.sections.size) {
            val x = detailLeft - index * sectionWidth
            canvas.drawLine(x, top, x, top + height, border)
        }
    }

    private fun medication(med: Medication, number: Int) {
        val parts = mutableListOf<PdfPart>()
        med.genericAr?.let { parts += PdfPart(it, true, 9.4f, "${MedicationPresentation.easternDigits(number.toString())} · ") }
        med.genericEn?.let { parts += PdfPart(it, true, 9.4f,
            if (parts.isEmpty()) "${MedicationPresentation.easternDigits(number.toString())} · " else "", true) }
        val form = snapshot.forms.first { it.id == med.formId }.nameAr
        if (med.strength.length + form.length < 2048) {
            parts += PdfPart("العيار: ${bidi.unicodeWrap(med.strength)} · الشكل: $form")
        } else {
            parts += PdfPart(med.strength, prefix = "العيار: ", isolate = true)
            parts += PdfPart(form, prefix = "الشكل: ")
        }
        med.nameAr?.let { parts += PdfPart(it, prefix = "التجاري: ") }
        med.nameEn?.let { parts += PdfPart(it, prefix = if (med.nameAr == null) "التجاري: " else "", isolate = true) }
        med.brand?.let { parts += PdfPart(it, prefix = "العلامة: ", isolate = true) }
        med.expiryDate?.let { parts += PdfPart(it.toString(), prefix = "الانتهاء (ميلادي): ", isolate = true) }
        requireNotNull(category).let { if (short(it.nameAr, 80) != it.nameAr) parts += PdfPart(it.nameAr, prefix = "التصنيف: ") }
        med.note?.let { parts += PdfPart(it, prefix = "ملاحظات: ", isolate = true) }
        val flow = TextFlow(parts, detailWidth)
        val statuses = snapshot.sections.map { snapshot.status(med.id, it.id) }
        val labels = statuses.map { layout(MedicationPresentation.statusLabel(it), (sectionWidth - padding * 2).toInt(), size = 7.4f, centered = true) }
        val minimumHeight = max(32f, labels.maxOf { it.height }.toFloat() + padding * 2)
        var continued = false
        while (!flow.done) {
            owner.ensureActive()
            val continuation = if (continued) layout("متابعة الدواء ${MedicationPresentation.easternDigits(number.toString())}", detailWidth, true, 7.4f) else null
            val prefixHeight = continuation?.height?.toFloat() ?: 0f
            if (bottom - y < minimumHeight + prefixHeight) newPage()
            val portion = flow.take(bottom - y - padding * 2 - prefixHeight)
            if (portion.slices.isEmpty()) { newPage(); continue }
            val height = max(minimumHeight, portion.height + prefixHeight + padding * 2)
            fill.color = if (number % 2 == 0) Color.rgb(249, 250, 251) else Color.WHITE
            canvas.drawRect(margin, y, right, y + height, fill)
            continuation?.let { draw(it, detailLeft + padding, y + padding) }
            drawPortion(portion, detailLeft + padding, y + padding + prefixHeight, detailWidth)
            labels.forEachIndexed { index, label ->
                val left = detailLeft - (index + 1) * sectionWidth
                fill.color = when (statuses[index]) {
                    AvailabilityStatus.AVAILABLE -> Color.rgb(237, 246, 239)
                    AvailabilityStatus.LOW -> Color.rgb(255, 247, 225)
                    AvailabilityStatus.OUT -> Color.rgb(255, 237, 235)
                    AvailabilityStatus.UNKNOWN -> Color.rgb(240, 241, 244)
                }
                canvas.drawRect(left, y, left + sectionWidth, y + height, fill)
                draw(label, left + padding, y + padding)
            }
            grid(y, height)
            y += height
            if (!flow.done) { continued = true; newPage() }
        }
    }

    fun render(): PdfDocument {
        try {
            require(snapshot.sections.size in 1..5)
            val categoryIds = snapshot.categories.map { it.id }.toSet()
            require(categoryIds.size == snapshot.categories.size && snapshot.medications.all { it.categoryId in categoryIds }) {
                "Report medications require unique, known categories"
            }
            newPage()
            plain(listOf(PdfPart(snapshot.pharmacyName, true, 10f, "الصيدلية: ", true)))
            val title = when (snapshot.request.preset) {
                ReportPreset.OUT -> "الأدوية غير المتوفرة في القسم: ${snapshot.sections.single().nameAr}"
                ReportPreset.FULL -> "التوافر الكامل — يشمل غير معروف"
                ReportPreset.EXPIRING_SOON -> "الأدوية القريبة من الانتهاء — يستثني المنتهي، وفق حد التنبيه الخاص بكل دواء"
            }
            plain(listOf(PdfPart(title, true, 10f)))
            snapshot.sections.forEachIndexed { index, section ->
                plain(listOf(PdfPart(section.nameAr + if (!section.isActive) " (قسم مغلق)" else "", size = 7.8f,
                    prefix = "القسم ${MedicationPresentation.easternDigits((index + 1).toString())}: ", isolate = true)))
            }
            if (snapshot.medications.isEmpty()) plain(listOf(PdfPart("لا توجد أدوية مطابقة لهذا التقرير")))
            val grouped = snapshot.medications.groupBy { it.categoryId }
            var number = 0
            snapshot.categories.sortedBy { it.orderIndex }.forEach { group ->
                val medications = grouped[group.id] ?: return@forEach
                owner.ensureActive()
                category = group
                tableActive = true
                // Reserve a bounded category/column heading plus a first readable row.
                val heading = layout(short(group.nameAr, 80), width - 8, true, 9.4f)
                val columns = snapshot.sections.maxOf { layout("١\n${short(it.nameAr, 20)}", (sectionWidth - padding * 2).toInt(), true, 7.4f, true).height }
                if (bottom - y < heading.height + columns + 56f) newPage() else tableHeader()
                medications.forEach { med -> medication(med, ++number) }
                y += 8f
            }
            page?.let { document.finishPage(it); page = null }
            return document
        } catch (error: Throwable) {
            page?.let { runCatching { document.finishPage(it) }; page = null }
            document.close()
            throw error
        }
    }
}
