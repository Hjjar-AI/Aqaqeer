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
            R.string.navigation_create_pharmacy_title to "إنشاء صيدلية",
            R.string.navigation_admin_tools_title to "أدوات الإدارة",
            R.string.navigation_telegram_setup_title to "إعداد تيليغرام",
            R.string.navigation_publication_review_title to "مراجعة التعديلات والنشر",
            R.string.navigation_taxonomy_title to "الأقسام والتصنيفات والأشكال",
            R.string.navigation_promote_admin_title to "تفعيل الإدارة",
            R.string.navigation_leave_pharmacy_title to "مغادرة الصيدلية",
            R.string.navigation_connection_export_title to "إصدار ملف اتصال",
            R.string.navigation_add_medication_title to "إضافة دواء",
            R.string.navigation_edit_medication_title to "تعديل الدواء",
            R.string.navigation_history_title to "السجل المحلي",
            R.string.navigation_medication_details_title to "تفاصيل الدواء",
            R.string.navigation_app_title to "عقاقير",
            R.string.shared_pin_digits_error to "أدخل أربعة أرقام عربية أو إنجليزية على الأقل",
            R.string.shared_timezone_invalid_error to "أدخل منطقة زمنية صحيحة مثل Asia/Damascus",
            R.string.shared_gregorian_date_invalid_error to "أدخل تاريخاً ميلادياً صحيحاً مثل 2026-11-12",
            R.string.shared_nonnegative_days_error to "أدخل عدداً صحيحاً غير سالب للأيام",
            R.string.shared_telegram_group_id_error to "أدخل معرّف المجموعة السالب الصحيح",
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
