package com.aqaqeer.app.reports

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CancellationException

class CsvCodecTest {
    private fun read(text: String) = CsvCodec.read(text.byteInputStream()) {}
    @Test fun bomQuotesMultilineCrLfAndDiagnosticColumnsRoundTrip() {
        val rows = listOf(listOf("nameEn", "note", "outcome"), listOf("A, B", "سطر\r\n\"quoted\"", "FAILED"))
        val bytes = ByteArrayOutputStream().also { CsvCodec.write(it, rows.asSequence(), false) {} }.toByteArray()
        assertArrayEquals(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()), bytes.take(3).toByteArray())
        val table = CsvCodec.read(bytes.inputStream()) {}
        assertEquals(listOf("nameEn", "note"), table.headers)
        assertEquals(listOf("A, B", "سطر\r\n\"quoted\""), table.records.single().cells)
        assertEquals(2, table.records.single().number)
    }
    @Test fun malformedWidthsAreRetainedAsRowErrors() {
        val rows = read("nameEn,note\nA\nB,C,D\nE,\n").records
        assertEquals(listOf(true, true, false), rows.map { it.malformed })
        assertEquals(listOf("B", "C", "D"), rows[1].cells)
    }
    @Test fun invalidHeadersAndQuoteStatesAreRejected() {
        listOf("", "nameEn,nameEn\n", "unknown\nA", ",nameEn\n", "outcome\nFAILED",
            "nameEn\nA\"B", "nameEn\n\"A\"B", "nameEn\n\"unfinished").forEach { text ->
            assertThrows(text, CsvProblem::class.java) { read(text) }
        }
    }
    @Test fun invalidUtf8IsNotReplaced() {
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            CsvCodec.read(("nameEn\n".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28)).inputStream()) {}
        }
    }
    @Test fun cellLimitUsesUtf8BytesAndAcceptsExactBoundary() {
        assertEquals(65536, read("note\n" + "a".repeat(65536)).records.single().cells.single().length)
        assertThrows(CsvProblem::class.java) { read("note\n" + "a".repeat(65537)) }
        assertEquals(32768, read("note\n" + "ش".repeat(32768)).records.single().cells.single().length)
        assertThrows(CsvProblem::class.java) { read("note\n" + "ش".repeat(32769)) }
    }
    @Test fun exactRowLimitAcceptsAndOneExtraRejects() {
        assertEquals(10000, read("nameEn\n" + "A\n".repeat(10000)).records.size)
        assertThrows(CsvProblem::class.java) { read("nameEn\n" + "A\n".repeat(10001)) }
    }
    @Test fun spreadsheetExportGuardsHiddenFormulaPrefixesButRetryPreservesRawData() {
        val cells = listOf("=SUM(A1)", " +1", "\u200f@cmd", "\ttext", "-1", "normal")
        fun encoded(safe: Boolean) = ByteArrayOutputStream().also {
            CsvCodec.write(it, sequenceOf(listOf("note")) + cells.asSequence().map { listOf(it) }, safe) {}
        }.toByteArray()
        val safe = CsvCodec.read(encoded(true).inputStream()) {}.records.map { it.cells.single() }
        assertEquals(cells.dropLast(1).map { "'$it" } + "normal", safe)
        assertEquals(cells, CsvCodec.read(encoded(false).inputStream()) {}.records.map { it.cells.single() })
    }
    @Test fun cancellationClosesInputAndStopsOutput() {
        var closed = false
        val input = object : ByteArrayInputStream("nameEn\nA".toByteArray()) { override fun close() { closed = true; super.close() } }
        assertThrows(CancellationException::class.java) { CsvCodec.read(input) { throw CancellationException() } }
        assertTrue(closed)
        assertThrows(CancellationException::class.java) { CsvCodec.write(ByteArrayOutputStream(), sequenceOf(listOf("A")), false) { throw CancellationException() } }
    }
}
