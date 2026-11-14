package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.*
import org.junit.Test

class HistoryProjectionTest {
    @Test fun unicodePreviewBoundsCodePointsWithoutSplittingSurrogates() {
        assertEquals(HistoryValue(null), historyPreview(null))
        val exact = "😀".repeat(512)
        assertEquals(HistoryValue(exact), historyPreview(exact))
        assertEquals(HistoryValue(exact, true), historyPreview(exact + "x"))
        assertEquals(HistoryValue(""), historyPreview(""))
    }
    @Test fun namesResolveAvailabilityPairsAndMissingEntitiesStayUnknown() {
        val c = F.catalog(); val m = c.medications.single()
        assertEquals(m.nameEn, c.historyName("availability", "${m.id}/${c.sections.single().id}"))
        assertEquals(c.pharmacy!!.name, c.historyName("pharmacy", F.pharmacyId))
        assertNull(c.historyName("medication", "missing")); assertNull(c.historyName("unknown", m.id))
    }
}
