package com.aqaqeer.app.ui

import android.content.res.Resources
import com.aqaqeer.app.AppStrings
import com.aqaqeer.app.R
import io.mockk.*
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class UiGuidanceTest {
    // Local JVM tests do not start AqaqeerApplication. Stub only resource lookup;
    // screen routing and validation still execute the production functions.
    @Before fun setupStrings() {
        val strings = mapOf(
            R.string.ui_screen_titles_001 to "إنشاء صيدلية",
            R.string.ui_screen_titles_002 to "أدوات الإدارة",
            R.string.ui_screen_titles_003 to "إعداد تيليغرام",
            R.string.ui_screen_titles_004 to "مراجعة التعديلات والنشر",
            R.string.ui_screen_titles_005 to "الأقسام والتصنيفات والأشكال",
            R.string.ui_screen_titles_006 to "تفعيل الإدارة",
            R.string.ui_screen_titles_007 to "مغادرة الصيدلية",
            R.string.ui_screen_titles_008 to "إصدار ملف اتصال",
            R.string.ui_screen_titles_009 to "إضافة دواء",
            R.string.ui_screen_titles_010 to "تعديل الدواء",
            R.string.ui_screen_titles_015 to "السجل المحلي",
            R.string.ui_screen_titles_019 to "تفاصيل الدواء",
            R.string.ui_screen_titles_020 to "عقاقير",
            R.string.ui_form_guidance_001 to "أدخل أربعة أرقام عربية أو إنجليزية على الأقل",
            R.string.ui_form_guidance_002 to "أدخل منطقة زمنية صحيحة مثل Asia/Damascus",
            R.string.ui_form_guidance_003 to "أدخل تاريخاً ميلادياً صحيحاً مثل 2026-11-12",
            R.string.ui_form_guidance_004 to "أدخل عدداً صحيحاً غير سالب للأيام",
            R.string.ui_form_guidance_005 to "أدخل معرّف المجموعة السالب الصحيح",
        )
        val resources = mockk<Resources>()
        every { resources.getString(any()) } answers { strings.getValue(firstArg<Int>()) }
        mockkObject(AppStrings)
        every { AppStrings.resources } returns resources
    }
    @After fun restoreStrings() { unmockkObject(AppStrings) }

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
