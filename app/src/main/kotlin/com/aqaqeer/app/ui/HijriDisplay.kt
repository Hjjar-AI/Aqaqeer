package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Presentation only: arithmetic Islamic civil calendar, never an input or business date. */
internal object HijriDisplay {
    private val epoch = LocalDate.of(622, 7, 19)

    fun date(gregorian: LocalDate): String? {
        val elapsed = ChronoUnit.DAYS.between(epoch, gregorian)
        if (elapsed < 0) return null
        var year = elapsed / 10_631 * 30 + 1
        var remaining = elapsed % 10_631
        fun leap(year: Long) = (11 * year + 14) % 30 < 11
        while (true) {
            val length = if (leap(year)) 355 else 354
            if (remaining < length) break
            remaining -= length
            year++
        }
        var month = 1
        while (month < 12) {
            val length = if (month % 2 == 1) 30 else 29
            if (remaining < length) break
            remaining -= length
            month++
        }
        val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
        return "${remaining + 1} ${months[month - 1]} $year"
    }
}

@Composable internal fun SecondaryHijriDate(gregorian: LocalDate, enabled: Boolean) {
    val uiText = rememberUiText()
    if (!enabled) return
    val date = HijriDisplay.date(gregorian)
    Text(if (date == null) uiText(R.string.shared_hijri_before_epoch_message)
        else uiText(R.string.shared_hijri_date_format, bidi(date)), style = MaterialTheme.typography.bodySmall)
}
