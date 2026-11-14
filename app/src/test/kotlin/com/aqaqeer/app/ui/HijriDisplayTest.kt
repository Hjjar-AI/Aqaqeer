package com.aqaqeer.app.ui

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HijriDisplayTest {
    @Test fun independentCivilCalendarReferenceDates() {
        // Published arithmetic civil epoch and fixed-date reference conversions, not Android's Umm al-Qura tables.
        mapOf("0622-07-19" to "0001-01-01", "1970-01-01" to "1389-10-22",
            "2000-01-01" to "1420-09-24", "2024-07-08" to "1446-01-01").forEach { (g, h) ->
            assertEquals(g, h, HijriDisplay.date(LocalDate.parse(g)))
        }
        assertNull(HijriDisplay.date(LocalDate.of(622, 7, 18)))
    }
    @Test fun monthLeapYearAndThirtyYearCycleBoundaries() {
        val epoch = LocalDate.of(622, 7, 19)
        mapOf(29L to "0001-01-30", 30L to "0001-02-01", 58L to "0001-02-29", 59L to "0001-03-01",
            353L to "0001-12-29", 354L to "0002-01-01", 708L to "0002-12-30", 709L to "0003-01-01",
            10630L to "0030-12-29", 10631L to "0031-01-01").forEach { (days, expected) ->
            assertEquals(expected, HijriDisplay.date(epoch.plusDays(days)))
        }
    }
    @Test fun gregorianLeapDayAndExtremeBusinessDatesRemainRepresentable() {
        assertNotEquals(HijriDisplay.date(LocalDate.of(2024, 2, 28)), HijriDisplay.date(LocalDate.of(2024, 2, 29)))
        assertNull(HijriDisplay.date(LocalDate.of(1, 1, 1)))
        assertTrue(HijriDisplay.date(LocalDate.of(9999, 12, 31))!!.all { it in '0'..'9' || it == '-' })
    }
}
