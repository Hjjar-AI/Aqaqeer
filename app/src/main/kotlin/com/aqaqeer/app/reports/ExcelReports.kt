package com.aqaqeer.app.reports

import com.aqaqeer.app.R
import com.aqaqeer.app.uiText
import com.aqaqeer.app.data.AvailabilityStatus
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlSerializer
import android.util.Xml
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.CoroutineContext

/** A bounded, streaming workbook: explicit text cells, no formulas, macros or shared-string table. */
internal object ExcelReports {
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CELL_LIMIT = 30000 // Leave room for Excel's UTF-16 and line-feed limits.
    private fun column(index: Int): String {
        var n = index + 1
        var text = ""
        while (n > 0) { n--; text = ('A'.code + n % 26).toChar() + text; n /= 26 }
        return text
    }
    /** XML 1.0 cannot store control characters. OOXML's literal escape preserves them as text. */
    private fun text(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '_' && i + 6 < value.length && value[i + 1] == 'x' && value[i + 6] == '_' &&
                value.substring(i + 2, i + 6).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) append("_x005F_")
            else if (ch.code in 0..8 || ch.code in 11..12 || ch.code in 14..31 || ch == '\r' || ch == '\uFFFE' || ch == '\uFFFF')
                append("_x%04X_".format(java.util.Locale.ROOT, ch.code))
            else append(ch)
            i++
        }
    }
    /** Split before both the character and line-feed caps, preserving surrogate pairs and all text. */
    private fun chunks(value: String, width: Int = 80): List<String> {
        if (value.isEmpty()) return listOf("")
        return buildList {
            var start = 0
            while (start < value.length) {
                var end = start
                var lines = 1
                var lineWidth = 0
                val capacity = (width * 0.6).toInt().coerceAtLeast(6)
                while (end < value.length && end - start < CELL_LIMIT) {
                    val ch = value[end]
                    if (ch == '\n' || ch == '\r') { lines++; lineWidth = 0 }
                    else { lineWidth += if (ch.code >= 0x2E80) 2 else 1; if (lineWidth > capacity) { lines++; lineWidth = 1 } }
                    if (lines > 16) break
                    end++
                }
                if (end < value.length && end > start && Character.isHighSurrogate(value[end - 1])) end--
                add(value.substring(start, end)); start = end
            }
        }
    }
    fun write(destination: OutputStream, snapshot: ReportSnapshot, owner: CoroutineContext) {
        // Do not close the provider here: DocumentOutput owns destination close and success.
        val zip = ZipOutputStream(object : java.io.FilterOutputStream(destination) {
            override fun close() { flush() }
        })
        fun entry(name: String, body: (XmlSerializer) -> Unit) {
            owner.ensureActive()
            zip.putNextEntry(ZipEntry(name))
            val xml = Xml.newSerializer()
            xml.setOutput(zip, "UTF-8"); xml.startDocument("UTF-8", true)
            body(xml)
            xml.endDocument(); xml.flush(); zip.closeEntry()
        }
        fun XmlSerializer.start(name: String) { startTag(null, name) }
        fun XmlSerializer.end(name: String) { endTag(null, name) }
        fun XmlSerializer.attr(name: String, value: Any) { attribute(null, name, value.toString()) }
        fun XmlSerializer.empty(name: String, vararg attributes: Pair<String, Any>) {
            start(name); attributes.forEach { attr(it.first, it.second) }; end(name)
        }
        val columns = snapshot.columns()
        fun header(value: String): String {
            var end = minOf(180, value.length)
            if (end < value.length && end > 0 && Character.isHighSurrogate(value[end - 1])) end--
            return value.substring(0, end)
        }
        val headers = listOf(uiText(R.string.report_row_number)) + columns.mapIndexed { i, col -> "${i + 1} · ${header(col.title)}" }
        var failure: Throwable? = null
        try {
            entry("[Content_Types].xml") { x -> with(x) {
                start("Types"); attr("xmlns", "http://schemas.openxmlformats.org/package/2006/content-types")
                empty("Default", "Extension" to "rels", "ContentType" to "application/vnd.openxmlformats-package.relationships+xml")
                empty("Default", "Extension" to "xml", "ContentType" to "application/xml")
                empty("Override", "PartName" to "/xl/workbook.xml", "ContentType" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml")
                empty("Override", "PartName" to "/xl/styles.xml", "ContentType" to "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml")
                for (i in 1..2) empty("Override", "PartName" to "/xl/worksheets/sheet$i.xml", "ContentType" to "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml")
                empty("Override", "PartName" to "/xl/tables/table1.xml", "ContentType" to "application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml")
                end("Types")
            } }
            entry("_rels/.rels") { x -> with(x) {
                start("Relationships"); attr("xmlns", "http://schemas.openxmlformats.org/package/2006/relationships")
                empty("Relationship", "Id" to "rId1", "Type" to "$REL/officeDocument", "Target" to "xl/workbook.xml")
                end("Relationships")
            } }
            entry("xl/workbook.xml") { x -> with(x) {
                start("workbook"); attr("xmlns", NS); attr("xmlns:r", REL)
                start("bookViews"); empty("workbookView"); end("bookViews")
                start("sheets")
                empty("sheet", "name" to "Summary", "sheetId" to 1, "r:id" to "rId1")
                empty("sheet", "name" to "Medications", "sheetId" to 2, "r:id" to "rId2")
                end("sheets")
                start("definedNames"); start("definedName"); attr("name", "_xlnm.Print_Titles"); attr("localSheetId", 1)
                x.text("'Medications'!\$1:\$1"); end("definedName"); end("definedNames")
                end("workbook")
            } }
            entry("xl/_rels/workbook.xml.rels") { x -> with(x) {
                start("Relationships"); attr("xmlns", "http://schemas.openxmlformats.org/package/2006/relationships")
                for (i in 1..2) empty("Relationship", "Id" to "rId$i", "Type" to "$REL/worksheet", "Target" to "worksheets/sheet$i.xml")
                empty("Relationship", "Id" to "rId3", "Type" to "$REL/styles", "Target" to "styles.xml")
                end("Relationships")
            } }
            entry("xl/styles.xml") { x -> with(x) {
                start("styleSheet"); attr("xmlns", NS)
                start("fonts"); attr("count", 2)
                for (strong in listOf(false, true)) {
                    start("font"); empty("sz", "val" to 11); empty("name", "val" to "Arial")
                    if (strong) empty("b")
                    end("font")
                }; end("fonts")
                val fills = listOf(null, null, "FFE9E2F3", "FFF8F6FA", "FFE5F3E8", "FFFFF1CE", "FFFFE2DF")
                start("fills"); attr("count", fills.size)
                fills.forEachIndexed { i, color ->
                    start("fill"); start("patternFill"); attr("patternType", if (i == 0) "none" else if (i == 1) "gray125" else "solid")
                    if (color != null) { empty("fgColor", "rgb" to color); empty("bgColor", "indexed" to 64) }
                    end("patternFill"); end("fill")
                }; end("fills")
                start("borders"); attr("count", 1); start("border")
                listOf("left", "right", "top", "bottom", "diagonal").forEach { empty(it) }
                end("border"); end("borders")
                start("cellStyleXfs"); attr("count", 1); empty("xf", "numFmtId" to 0, "fontId" to 0, "fillId" to 0, "borderId" to 0); end("cellStyleXfs")
                start("cellXfs"); attr("count", 6)
                for (i in 0..5) {
                    start("xf"); attr("numFmtId", 49); attr("fontId", if (i == 1) 1 else 0)
                    attr("fillId", if (i == 0) 0 else i + 1); attr("borderId", 0); attr("xfId", 0)
                    attr("applyAlignment", 1); attr("applyNumberFormat", 1)
                    empty("alignment", "horizontal" to "right", "vertical" to "top", "wrapText" to 1, "readingOrder" to 2)
                    end("xf")
                }; end("cellXfs")
                start("cellStyles"); attr("count", 1); empty("cellStyle", "name" to "Normal", "xfId" to 0, "builtinId" to 0); end("cellStyles")
                end("styleSheet")
            } }
            var sheetWidths = emptyList<Int>()
            fun sheetStart(x: XmlSerializer, widths: List<Int>) = with(x) {
                sheetWidths = widths
                start("worksheet"); attr("xmlns", NS); attr("xmlns:r", REL)
                start("sheetPr"); empty("pageSetUpPr", "fitToPage" to 1); end("sheetPr")
                start("sheetViews"); start("sheetView"); attr("workbookViewId", 0); attr("rightToLeft", 1)
                empty("pane", "ySplit" to 1, "topLeftCell" to "A2", "activePane" to "bottomLeft", "state" to "frozen")
                empty("selection", "pane" to "bottomLeft", "activeCell" to "A2", "sqref" to "A2")
                end("sheetView"); end("sheetViews")
                empty("sheetFormatPr", "defaultRowHeight" to 18)
                start("cols"); widths.forEachIndexed { i, width -> empty("col", "min" to i + 1, "max" to i + 1, "width" to width, "customWidth" to 1) }; end("cols")
                start("sheetData")
            }
            fun row(x: XmlSerializer, number: Int, values: List<String>, styles: List<Int>) = with(x) {
                owner.ensureActive()
                if (number > 1_048_576) throw CsvProblem(uiText(R.string.report_excel_too_large))
                start("row"); attr("r", number)
                val lines = values.mapIndexed { i, value ->
                    val capacity = (sheetWidths[i] * 0.6).toInt().coerceAtLeast(6)
                    value.split('\n', '\r').sumOf { line -> maxOf(1, (line.sumOf { if (it.code >= 0x2E80) 2 else 1 } + capacity - 1) / capacity) }
                }.maxOrNull() ?: 1
                attr("ht", minOf(409, maxOf(20, lines * 16 + 4))); attr("customHeight", 1)
                values.forEachIndexed { i, value ->
                    start("c"); attr("r", "${column(i)}$number"); attr("s", styles[i]); attr("t", "inlineStr")
                    start("is"); start("t"); attr("xml:space", "preserve"); x.text(ExcelReports.text(value)); end("t"); end("is"); end("c")
                }; end("row")
            }
            fun sheetEnd(x: XmlSerializer, table: Boolean) = with(x) {
                end("sheetData")
                empty("pageMargins", "left" to 0.25, "right" to 0.25, "top" to 0.4, "bottom" to 0.4, "header" to 0.2, "footer" to 0.2)
                empty("pageSetup", "paperSize" to if (snapshot.request.options.paper == ReportPaper.A5) 11 else 9,
                    "orientation" to if (snapshot.request.options.landscape) "landscape" else "portrait", "fitToWidth" to 1, "fitToHeight" to 0)
                if (table) { start("tableParts"); attr("count", 1); empty("tablePart", "r:id" to "rId1"); end("tableParts") }
                end("worksheet")
            }
            entry("xl/worksheets/sheet1.xml") { x ->
                sheetStart(x, listOf(30, 80))
                row(x, 1, listOf(uiText(R.string.report_summary_title), ""), listOf(1, 1))
                var number = 1
                snapshot.summary().forEach { (label, value) -> chunks(value).forEachIndexed { i, chunk ->
                    row(x, ++number, listOf(if (i == 0) label else uiText(R.string.report_continuation), chunk), listOf(1, 0))
                } }
                sheetEnd(x, false)
            }
            var lastRow = 1
            entry("xl/worksheets/sheet2.xml") { x ->
                sheetStart(x, listOf(10) + columns.map { when (it.field) {
                    ReportField.SCIENTIFIC, ReportField.TRADE -> 32
                    ReportField.NOTE -> 60
                    else -> 22
                } })
                // Prefix with the ordinal to keep Excel table column names unique after section renames.
                row(x, 1, headers, List(columns.size + 1) { 1 })
                snapshot.medications.forEachIndexed { index, med ->
                    val cells = columns.mapIndexed { i, col -> chunks(col.value(snapshot, med), sheetWidths[i + 1]) }
                    val count = cells.maxOfOrNull { it.size } ?: 1
                    repeat(count) { continuation ->
                        val values = listOf(if (continuation == 0) (index + 1).toString() else "${index + 1} · ${uiText(R.string.report_continuation)}") +
                            columns.mapIndexed { i, col -> cells[i].getOrNull(continuation) ?: when {
                                col.field == ReportField.SCIENTIFIC && cells[i].size == 1 -> cells[i].first()
                                col.field == ReportField.SCIENTIFIC -> uiText(R.string.report_medication_continuation, (index + 1).toString())
                                col.field == ReportField.AVAILABILITY -> col.value(snapshot, med)
                                else -> ""
                            } }
                        val styles = listOf(if (index % 2 == 0) 0 else 2) + columns.map { col ->
                            when (col.status(snapshot, med)) { AvailabilityStatus.AVAILABLE -> 3; AvailabilityStatus.LOW -> 4; AvailabilityStatus.OUT -> 5
                                null -> if (index % 2 == 0) 0 else 2 }
                        }
                        row(x, ++lastRow, values, styles)
                    }
                }
                if (snapshot.medications.isEmpty()) row(x, ++lastRow, List(columns.size + 1) { "" }, List(columns.size + 1) { 0 })
                sheetEnd(x, true)
            }
            entry("xl/worksheets/_rels/sheet2.xml.rels") { x -> with(x) {
                start("Relationships"); attr("xmlns", "http://schemas.openxmlformats.org/package/2006/relationships")
                empty("Relationship", "Id" to "rId1", "Type" to "$REL/table", "Target" to "../tables/table1.xml")
                end("Relationships")
            } }
            entry("xl/tables/table1.xml") { x -> with(x) {
                start("table"); attr("xmlns", NS); attr("id", 1); attr("name", "MedicationReport"); attr("displayName", "MedicationReport")
                attr("ref", "A1:${column(columns.size)}$lastRow"); attr("totalsRowShown", 0)
                empty("autoFilter", "ref" to "A1:${column(columns.size)}$lastRow")
                start("tableColumns"); attr("count", columns.size + 1)
                empty("tableColumn", "id" to 1, "name" to headers.first())
                columns.forEachIndexed { i, _ -> empty("tableColumn", "id" to i + 2, "name" to ExcelReports.text(headers[i + 1])) }
                end("tableColumns")
                empty("tableStyleInfo", "name" to "TableStyleMedium2", "showFirstColumn" to 0, "showLastColumn" to 0, "showRowStripes" to 1, "showColumnStripes" to 0)
                end("table")
            } }
            owner.ensureActive(); zip.finish(); zip.flush()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val original = failure
            if (original == null) zip.close()
            else try { zip.close() } catch (closeError: Throwable) { original.addSuppressed(closeError) }
        }
    }
}
