package com.aqaqeer.app.ui

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HijriDisplayTest {
    @Test fun independentCivilCalendarReferenceDates() {
        // Published arithmetic civil epoch and fixed-date reference conversions, not Android's Umm al-Qura tables.
        mapOf("0622-07-19" to "1 محرم 1", "1970-01-01" to "22 شوال 1389",
            "2000-01-01" to "24 رمضان 1420", "2024-07-08" to "1 محرم 1446").forEach { (g, h) ->
            assertEquals(g, h, HijriDisplay.date(LocalDate.parse(g)))
        }
        assertNull(HijriDisplay.date(LocalDate.of(622, 7, 18)))
    }
    @Test fun monthLeapYearAndThirtyYearCycleBoundaries() {
        val epoch = LocalDate.of(622, 7, 19)
        mapOf(29L to "30 محرم 1", 30L to "1 صفر 1", 58L to "29 صفر 1", 59L to "1 ربيع الأول 1",
            353L to "29 ذو الحجة 1", 354L to "1 محرم 2", 708L to "30 ذو الحجة 2", 709L to "1 محرم 3",
            10630L to "29 ذو الحجة 30", 10631L to "1 محرم 31").forEach { (days, expected) ->
            assertEquals(expected, HijriDisplay.date(epoch.plusDays(days)))
        }
    }
    @Test fun gregorianLeapDayAndExtremeBusinessDatesRemainRepresentable() {
        assertNotEquals(HijriDisplay.date(LocalDate.of(2024, 2, 28)), HijriDisplay.date(LocalDate.of(2024, 2, 29)))
        assertNull(HijriDisplay.date(LocalDate.of(1, 1, 1)))
        val display = requireNotNull(HijriDisplay.date(LocalDate.of(9999, 12, 31)))
        assertTrue(display.any { it in '0'..'9' }); assertFalse(display.any { it in '٠'..'٩' || it in '۰'..'۹' })
    }
}
