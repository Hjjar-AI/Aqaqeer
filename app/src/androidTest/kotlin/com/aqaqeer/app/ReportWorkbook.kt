package com.aqaqeer.app

import org.junit.Assert.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Independent reader for the generated ZIP/XML, rather than assertions over writer internals. */
internal class ReportWorkbook(private val parts: Map<String, ByteArray>) {
    fun xml(path: String): Document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(parts.getValue(path).inputStream())
    fun elements(path: String, name: String): List<Element> = xml(path).getElementsByTagNameNS("*", name).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }
    fun rows(sheet: String): List<List<String>> = elements(sheetPath(sheet), "row").map { row ->
        val cells = row.getElementsByTagNameNS("*", "c")
        (0 until cells.length).map { i ->
            val cell = cells.item(i) as Element
            assertEquals("inlineStr", cell.getAttribute("t"))
            decode(cell.getElementsByTagNameNS("*", "t").item(0).textContent)
        }
    }
    private fun sheetPath(sheet: String): String {
        val selected = elements("xl/workbook.xml", "sheet").single { it.getAttribute("name") == sheet }
        val id = selected.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        val relationship = elements("xl/_rels/workbook.xml.rels", "Relationship").single { it.getAttribute("Id") == id }
        return "xl/" + relationship.getAttribute("Target")
    }
    fun assertStructure() {
        assertEquals(setOf("Summary", "Medications"), elements("xl/workbook.xml", "sheet").map { it.getAttribute("name") }.toSet())
        assertEquals("xl/workbook.xml", elements("_rels/.rels", "Relationship").single().getAttribute("Target"))
        elements("[Content_Types].xml", "Override").forEach { assertTrue(it.getAttribute("PartName"), parts.containsKey(it.getAttribute("PartName").removePrefix("/"))) }
        for (path in parts.keys.filter { it.endsWith(".xml") || it.endsWith(".rels") }) {
            val document = xml(path)
            assertEquals(path, 0, document.getElementsByTagNameNS("*", "f").length)
        }
        assertFalse(parts.keys.any { it.contains("vba", ignoreCase = true) || it.contains("externalLink") })
        val table = elements("xl/tables/table1.xml", "table").single()
        val rows = rows("Medications")
        val tableColumns = elements("xl/tables/table1.xml", "tableColumn")
        assertEquals(rows.first().size, tableColumns.size)
        assertEquals(rows.first(), tableColumns.map { decode(it.getAttribute("name")) })
        assertEquals(tableColumns.size, tableColumns.map { it.getAttribute("name") }.toSet().size)
        val lastCell = elements("xl/worksheets/sheet2.xml", "c").last().getAttribute("r")
        assertEquals("A1:$lastCell", table.getAttribute("ref"))
        assertEquals(table.getAttribute("ref"), elements("xl/tables/table1.xml", "autoFilter").single().getAttribute("ref"))
        assertEquals("../tables/table1.xml", elements("xl/worksheets/_rels/sheet2.xml.rels", "Relationship").single().getAttribute("Target"))
        val styleCount = elements("xl/styles.xml", "cellXfs").single().getAttribute("count").toInt()
        assertTrue(elements("xl/worksheets/sheet2.xml", "c").all { it.getAttribute("s").toInt() in 0 until styleCount })
        assertTrue(elements("xl/styles.xml", "alignment").all { it.getAttribute("wrapText") == "1" })
        assertEquals("1", elements("xl/tables/table1.xml", "tableStyleInfo").single().getAttribute("showRowStripes"))
    }
    companion object {
        private fun decode(text: String): String = Regex("_x([0-9A-Fa-f]{4})_").replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
        fun read(input: InputStream): ReportWorkbook {
            val parts = linkedMapOf<String, ByteArray>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    check(parts.put(entry.name, zip.readBytes()) == null) { "Duplicate ZIP part" }
                }
            }
            assertTrue("A genuine OOXML workbook must contain its content types", parts.containsKey("[Content_Types].xml"))
            return ReportWorkbook(parts)
        }
    }
}
