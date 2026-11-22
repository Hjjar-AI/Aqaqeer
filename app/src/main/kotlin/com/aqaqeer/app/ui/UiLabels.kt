package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.core.text.BidiFormatter
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*

internal fun bidi(value: String): String = BidiFormatter.getInstance(true).unicodeWrap(value)
internal fun statusLabel(status: AvailabilityStatus, text: UiText? = null): String = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    uiText(when (status) {
    AvailabilityStatus.AVAILABLE -> R.string.status_available
    AvailabilityStatus.LOW -> R.string.status_low
    AvailabilityStatus.OUT -> R.string.status_out
    AvailabilityStatus.UNKNOWN -> R.string.status_unknown
})
}

@Composable internal fun MedicationTitle(medication: Medication, singleLine: Boolean = false) {
    Text(bidi(MedicationPresentation.scientificName(medication)), fontWeight = FontWeight.Bold,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleLarge,
        modifier = if (singleLine) Modifier.testTag("screen_title") else Modifier)
    Text(bidi(MedicationPresentation.westernDigits(medication.strength)), style = MaterialTheme.typography.labelLarge)
}
