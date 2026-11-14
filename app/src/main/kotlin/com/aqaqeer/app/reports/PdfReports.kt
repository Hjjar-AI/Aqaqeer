package com.aqaqeer.app.reports

import android.content.Context
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withClip
import androidx.core.text.BidiFormatter
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/** PDF is built before opening the destination. Actual Arabic rendering still needs device review. */
class PdfReports @Inject constructor(@param:ApplicationContext private val context: Context) {
    internal suspend fun render(snapshot: ReportSnapshot): PdfDocument {
        val document = PdfDocument()
        val normal = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_naskh_arabic_regular))
        val bold = requireNotNull(ResourcesCompat.getFont(context, R.font.noto_naskh_arabic_bold))
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = normal; textSize = 11f; color = Color.BLACK }
        val emblem = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.aqaqeer_logo))
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(122, 90, 18); strokeWidth = 1f }
        var page: PdfDocument.Page? = null
        var pageNumber = 0
        var y = 0f
        val width = 515
        val bottom = 795f
        fun isolate(value: String) = BidiFormatter.getInstance(true).unicodeWrap(value)
        fun layout(text: String, face: Typeface = normal, size: Float = 11f): StaticLayout {
            paint.typeface = face; paint.textSize = size
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(true).setLineSpacing(2f, 1f).build()
        }
        fun draw(text: String, face: Typeface = normal, size: Float = 11f) {
            val block = layout(text, face, size)
            require(block.height < 180) // Repeated header/footer must leave usable page space.
            val canvas = requireNotNull(page).canvas
            canvas.withTranslation(40f, y) { block.draw(this) }; y += block.height + 3f
        }
        fun nextPage() {
            page?.let { document.finishPage(it) }
            if (++pageNumber > 1000) throw CsvProblem("التقرير يحتاج أكثر من ١٠٠٠ صفحة؛ قلّل نطاقه")
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            y = 26f
            requireNotNull(page).canvas.drawBitmap(emblem, null, RectF(40f, 20f, 88f, 68f), null)
            draw("تقرير عقاقير", bold, 15f)
            y = maxOf(y, 72f)
            // Long names are rendered fully in the report body; keep the repeated header bounded.
            val headerName = snapshot.pharmacyName.let { name ->
                name.substring(0, name.offsetByCodePoints(0, minOf(100, name.codePointCount(0, name.length))))
            }
            draw("الصيدلية: " + isolate(headerName), bold)
            draw("التوقيت: ${isolate(snapshot.timezone)} · أُنشئ: ${isolate(MedicationPresentation.timestamp(snapshot.generatedAt, snapshot.timezone))}", size = 9f)
            draw("النشر: " + (snapshot.publishedAt?.let { isolate(MedicationPresentation.timestamp(it, snapshot.timezone)) } ?: "لم يُنشر") +
                " · " + if (snapshot.request.includeUnpublished) "يشمل تعديلات غير منشورة" else "البيانات المنشورة فقط", size = 9f)
            val footer = layout("صفحة ${MedicationPresentation.easternDigits(pageNumber.toString())}", size = 9f)
            requireNotNull(page).canvas.withTranslation(40f, 807f) { footer.draw(this) }
            requireNotNull(page).canvas.drawLine(40f, y + 2f, 555f, y + 2f, accent)
            y += 10f
        }
        suspend fun block(text: String, face: Typeface = normal) {
            // Chunk long fields before layout; no text is discarded. Each chunk can span pages.
            var offset = 0
            do {
                currentCoroutineContext().ensureActive()
                var end = minOf(offset + 8192, text.length)
                if (end < text.length && end > offset && Character.isHighSurrogate(text[end - 1])) end--
                val part = text.substring(offset, end)
                val lines = layout(part, face)
                var firstLine = 0
                while (firstLine < lines.lineCount) {
                    currentCoroutineContext().ensureActive()
                    var lastLine = firstLine
                    val top = lines.getLineTop(firstLine)
                    while (lastLine < lines.lineCount && y + lines.getLineBottom(lastLine) - top <= bottom) lastLine++
                    if (lastLine == firstLine) { nextPage(); draw("متابعة", size = 9f); continue }
                    val height = lines.getLineBottom(lastLine - 1) - top
                    val canvas = requireNotNull(page).canvas
                    // Layout retains its TextPaint, so restore its face/size before drawing.
                    paint.typeface = face; paint.textSize = 11f
                    canvas.withClip(40f, y, 555f, y + height) {
                        withTranslation(40f, y - top) { lines.draw(this) }
                    }
                    y += height + 5f; firstLine = lastLine
                }
                offset = end
            } while (offset < text.length)
        }
        try {
            nextPage()
            block("الصيدلية: ${isolate(snapshot.pharmacyName)}", bold)
            block(when (snapshot.request.preset) {
                ReportPreset.OUT -> "الأدوية غير المتوفرة في القسم: ${snapshot.sections.single().nameAr}"
                ReportPreset.FULL -> "التوافر الكامل — يشمل غير معروف"
                ReportPreset.EXPIRING_SOON -> "الأدوية القريبة من الانتهاء — يستثني المنتهي، وفق حد التنبيه الخاص بكل دواء"
            }, bold)
            if (snapshot.medications.isEmpty()) block("لا توجد أدوية مطابقة لهذا التقرير")
            snapshot.medications.forEach { med ->
                currentCoroutineContext().ensureActive()
                block(listOfNotNull(med.genericAr, med.genericEn?.let(::isolate)).joinToString("\n"), bold)
                block("العيار: ${isolate(med.strength)}\nالشكل: ${snapshot.forms.first { it.id == med.formId }.nameAr}\nالتصنيف: ${snapshot.categories.first { it.id == med.categoryId }.nameAr}")
                val commercial = listOfNotNull(med.nameAr, med.nameEn?.let(::isolate))
                if (commercial.isNotEmpty()) block("الاسم التجاري: " + commercial.joinToString("\n"))
                med.brand?.let { block("العلامة: ${isolate(it)}") }
                med.expiryDate?.let { block("تاريخ الانتهاء (ميلادي): ${isolate(it.toString())}") }
                snapshot.sections.forEach { section ->
                    block(section.nameAr + (if (!section.isActive) " (قسم مغلق)" else "") + ": " + MedicationPresentation.statusLabel(snapshot.status(med.id, section.id)))
                }
                med.note?.let { block("ملاحظات: ${isolate(it)}") }
                y += 10f
            }
            page?.let { document.finishPage(it); page = null }
            return document
        } catch (error: Throwable) {
            page?.let { runCatching { document.finishPage(it) } }
            document.close(); throw error
        }
    }
}
