package com.aqaqeer.app.reports

import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.CodingErrorAction

internal val medicationColumns = listOf("genericAr", "genericEn", "nameAr", "nameEn", "brand", "formAr", "strength",
    "categoryAr", "note", "expiryDate", "expirySoonThresholdDays")
internal val visibleMedicationColumns = medicationColumns
internal val resultColumns = listOf("rowNumber", "outcome", "warning", "error")
internal data class CsvRecord(val number: Int, val cells: List<String>, val malformed: Boolean = false)
internal data class CsvTable(val headers: List<String>, val records: List<CsvRecord>)
internal class CsvProblem(val reason: String) : Exception()

/** Strict, bounded CSV. No database writes occur while reading. */
internal object CsvCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 10_000
    private const val MAX_CELL_BYTES = 64 * 1024
    private class BoundedInput(input: InputStream) : FilterInputStream(input) {
        private var count = 0
        private fun account(n: Int): Int {
            if (n > 0) count += n
            if (count > MAX_BYTES) throw CsvProblem("ملف CSV أكبر من ١٠ ميبيبايت")
            return n
        }
        override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            account(`in`.read(bytes, offset, minOf(length, MAX_BYTES - count + 1)))
    }

    fun read(input: InputStream, checkpoint: () -> Unit): CsvTable {
        val runtime = Runtime.getRuntime()
        if (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) < 8L * MAX_BYTES + 16L * 1024 * 1024)
            throw CsvProblem("لا توجد ذاكرة كافية لمعاينة الملف بأمان")
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return InputStreamReader(BoundedInput(input), decoder).use { reader ->
            var header: List<String>? = null
            val records = mutableListOf<CsvRecord>()
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var state = 0 // 0=start, 1=unquoted, 2=quoted, 3=after closing quote
            var started = false
            var skipLf = false
            var first = true
            var number = 1
            fun endCell() {
                val value = cell.toString()
                if (value.toByteArray(Charsets.UTF_8).size > MAX_CELL_BYTES) throw CsvProblem("خلية CSV تتجاوز ٦٤ كيبيبايت")
                cells += value; cell.setLength(0); state = 0
                if (cells.size > medicationColumns.size + 5 + resultColumns.size) throw CsvProblem("عدد أعمدة CSV غير مدعوم")
            }
            fun endRecord() {
                endCell()
                if (header == null) {
                    if (cells.isEmpty() || cells.any { it.isEmpty() } || cells.distinct().size != cells.size)
                        throw CsvProblem("رأس CSV فارغ أو يحتوي أعمدة مكررة")
                    if (cells.any { it !in medicationColumns && it !in resultColumns && !it.startsWith("section:") })
                        throw CsvProblem("يحتوي CSV أعمدة غير معروفة")
                    header = cells.toList()
                } else {
                    if (records.size >= MAX_ROWS) throw CsvProblem("عدد صفوف CSV يتجاوز ١٠٠٠٠")
                    records += CsvRecord(number, cells.toList())
                }
                number++; cells.clear(); started = false
            }
            while (true) {
                checkpoint()
                val next = reader.read()
                if (next < 0) break
                val char = next.toChar()
                if (first) { first = false; if (char == '\uFEFF') continue }
                if (skipLf) { skipLf = false; if (char == '\n') continue }
                started = true
                when (state) {
                    2 -> if (char == '"') state = 3 else cell.append(char)
                    else -> when (char) {
                        ',' -> endCell()
                        '\r', '\n' -> { endRecord(); skipLf = char == '\r' }
                        '"' -> when (state) {
                            0 -> state = 2
                            3 -> { cell.append('"'); state = 2 }
                            else -> throw CsvProblem("علامة اقتباس غير صالحة في CSV")
                        }
                        else -> {
                            if (state == 3) throw CsvProblem("أحرف بعد إغلاق اقتباس CSV")
                            cell.append(char); state = 1
                        }
                    }
                }
                if (cell.length > MAX_CELL_BYTES) throw CsvProblem("خلية CSV تتجاوز الحد المسموح")
            }
            if (state == 2) throw CsvProblem("خلية CSV مقتبسة غير مكتملة")
            if (started || cells.isNotEmpty() || cell.isNotEmpty()) endRecord()
            val headers = header ?: throw CsvProblem("ملف CSV فارغ")
            val retained = headers.indices.filter { headers[it] !in resultColumns }
            if (retained.isEmpty()) throw CsvProblem("لا توجد أعمدة بيانات دوائية")
            // Preserve malformed row widths for a row-level error rather than silently padding.
            CsvTable(retained.map { headers[it] }, records.map { record ->
                if (record.cells.size != headers.size) CsvRecord(record.number, record.cells, malformed = true)
                else CsvRecord(record.number, retained.map { record.cells[it] })
            })
        }
    }

    fun write(output: OutputStream, rows: Sequence<List<String>>, spreadsheetSafe: Boolean, checkpoint: () -> Unit) {
        output.write(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        rows.forEach { row ->
            checkpoint()
            val line = row.joinToString(",") { raw ->
                val value = if (spreadsheetSafe && formulaLike(raw)) "'$raw" else raw
                "\"" + value.replace("\"", "\"\"") + "\""
            } + "\r\n"
            output.write(line.toByteArray(Charsets.UTF_8))
        }
        output.flush()
    }

    private fun formulaLike(value: String): Boolean {
        val meaningful = value.dropWhile { it.isWhitespace() || it <= ' ' || Character.getType(it) == Character.FORMAT.toInt() }
        return meaningful.firstOrNull() in listOf('=', '+', '-', '@') || value.firstOrNull() in listOf('\t', '\r', '\n')
    }
}
