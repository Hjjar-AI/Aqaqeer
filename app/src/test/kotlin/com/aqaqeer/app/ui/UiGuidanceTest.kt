package com.aqaqeer.app.ui

import org.junit.Assert.*
import org.junit.Test

class UiGuidanceTest {
    @Test fun titlesDistinguishCreationEditingAndAdministration() {
        val expected = mapOf("create" to "إنشاء صيدلية", "admin" to "أدوات الإدارة", "setup" to "إعداد تيليغرام",
            "review" to "مراجعة التعديلات والنشر", "taxonomy" to "الأقسام والتصنيفات والأشكال", "promote" to "تفعيل الإدارة",
            "leave" to "مغادرة الصيدلية", "export-connection" to "إصدار ملف اتصال", "edit-med:new" to "إضافة دواء",
            "edit-med:id" to "تعديل الدواء", "history:id" to "السجل المحلي", "med:id" to "تفاصيل الدواء")
        expected.forEach { (route, title) -> assertEquals(route, title, screenTitle(route)) }
        assertEquals("عقاقير", screenTitle("home"))
    }
    @Test fun pinHintsAcceptArabicDigitsAndRejectLengthAndText() {
        assertNull(FormGuidance.pin("١٢٣٤٥٦")); assertNull(FormGuidance.pin("123456"))
        listOf("", "123", "1234 ", "12a456", "      ").forEach { assertNotNull(FormGuidance.pin(it)) }
    }
    @Test fun simpleNumericWarningsDoNotRejectValidCredentials() {
        listOf("1111", "1234", "4321", "121212", "١٢٣٤").forEach {
            assertTrue(it, FormGuidance.simpleNumericPattern(it)); assertNull(FormGuidance.pin(it))
        }
        assertFalse(FormGuidance.simpleNumericPattern("1907"))
        assertFalse(FormGuidance.simpleNumericPattern(""))
    }
    @Test fun dateThresholdTimezoneAndGroupHintsRespectBoundaries() {
        assertNull(FormGuidance.date("2024-02-29")); assertNull(FormGuidance.date(""))
        assertNotNull(FormGuidance.date("2025-02-29")); assertNotNull(FormGuidance.date("tomorrow"))
        assertNull(FormGuidance.threshold("0")); assertNull(FormGuidance.threshold(Int.MAX_VALUE.toString()))
        assertNotNull(FormGuidance.threshold("-1")); assertNotNull(FormGuidance.threshold("2147483648"))
        assertNull(FormGuidance.timezone("Asia/Damascus")); assertNotNull(FormGuidance.timezone("missing/zone"))
        assertNull(FormGuidance.group(Long.MIN_VALUE.toString()))
        listOf("0", "42", "", "-999999999999999999999").forEach { assertNotNull(FormGuidance.group(it)) }
    }
}
