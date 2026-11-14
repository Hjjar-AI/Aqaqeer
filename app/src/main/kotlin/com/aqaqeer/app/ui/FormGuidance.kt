package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import com.aqaqeer.app.data.MedicationPresentation
import java.time.LocalDate
import java.time.ZoneId

/** Presentation hints reuse domain parsers; commands remain the authority. No secrets are retained. */
internal object FormGuidance {
    fun pin(value: String): String? = if (value.length >= 4 && value.all { Character.digit(it, 10) in 0..9 }) null
        else uiText(R.string.ui_form_guidance_001)
    fun simpleNumericPattern(value: String): Boolean {
        val digits = MedicationPresentation.westernDigits(value)
        if (digits.isEmpty()) return false
        return digits.toSet().size == 1 || digits.zipWithNext().all { (a, b) -> b - a == 1 } ||
            digits.zipWithNext().all { (a, b) -> a - b == 1 } ||
            (1..digits.length / 2).any { size -> digits.length % size == 0 && digits.chunked(size).distinct().size == 1 }
    }
    fun timezone(value: String): String? = if (runCatching { ZoneId.of(value) }.isSuccess) null
        else uiText(R.string.ui_form_guidance_002)
    fun date(value: String): String? = if (value.isBlank() || runCatching { LocalDate.parse(value.trim()) }.isSuccess) null
        else uiText(R.string.ui_form_guidance_003)
    fun threshold(value: String): String? = if (value.toIntOrNull()?.let { it >= 0 } == true) null
        else uiText(R.string.ui_form_guidance_004)
    fun group(value: String): String? = if (value.toLongOrNull()?.let { it < 0 } == true) null
        else uiText(R.string.ui_form_guidance_005)
}
