package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import com.aqaqeer.app.data.MedicationPresentation
import java.time.LocalDate
import java.time.ZoneId

/** Presentation hints reuse domain parsers; commands remain the authority. No secrets are retained. */
internal object FormGuidance {
    fun pin(value: String): String? = if (value.length == 6 && value.all { Character.digit(it, 10) in 0..9 }) null
        else uiText(R.string.ui_form_guidance_001)
    fun timezone(value: String): String? = if (runCatching { ZoneId.of(value) }.isSuccess) null
        else uiText(R.string.ui_form_guidance_002)
    fun date(value: String): String? = if (value.isBlank() || runCatching { LocalDate.parse(value.trim()) }.isSuccess) null
        else uiText(R.string.ui_form_guidance_003)
    fun threshold(value: String): String? = if (value.toIntOrNull()?.let { it >= 0 } == true) null
        else uiText(R.string.ui_form_guidance_004)
    fun group(value: String): String? = if (value.toLongOrNull()?.let { it < 0 } == true) null
        else uiText(R.string.ui_form_guidance_005)
}
