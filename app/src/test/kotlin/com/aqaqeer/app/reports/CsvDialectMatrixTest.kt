package com.aqaqeer.app.reports

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayOutputStream

@RunWith(Parameterized::class)
class CsvDialectMatrixTest(private val label: String, private val delimiter: String,
    private val newline: String, private val bom: Boolean, private val value: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            for (delimiter in listOf(",", ";", "\t")) for (newline in listOf("\n", "\r\n", "\r"))
                for (bom in listOf(false, true)) for ((index, value) in listOf("Plain", "عربي English ٥٠٠", "a,;\tb", "line\n\"quoted\" 😀").withIndex())
                    add(arrayOf("delimiter=${delimiter.toCharArray().map { it.code }}/newline=${newline.length}/bom=$bom/value=$index", delimiter, newline, bom, value))
        }
    }
    @Test fun dialectImportsAndCanonicalExportPreserveExactCellContents() {
        fun quote(text: String) = "\"" + text.replace("\"", "\"\"") + "\""
        val text = (if (bom) "\uFEFF" else "") + listOf("genericEn", "note").joinToString(delimiter, transform = ::quote) + newline +
            listOf("Scientific", value).joinToString(delimiter, transform = ::quote) + newline
        val table = CsvCodec.read(text.byteInputStream()) {}
        assertEquals(label, listOf("genericEn", "note"), table.headers)
        assertEquals(listOf("Scientific", value), table.records.single().cells)
        assertEquals(2, table.records.single().number); assertFalse(table.records.single().malformed)
        val bytes = ByteArrayOutputStream().also { CsvCodec.write(it, sequenceOf(table.headers, table.records.single().cells), false) {} }.toByteArray()
        assertEquals(table, CsvCodec.read(bytes.inputStream()) {})
    }
}
