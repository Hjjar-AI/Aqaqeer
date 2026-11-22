package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.aqaqeer.app.data.*
import java.time.Instant

@Composable internal fun DataTimesDialog(ready: CatalogState.Ready, dateAnchor: Instant, refresh: (() -> Unit)? = null, busy: Boolean = false, dismiss: () -> Unit) {
    val uiText = rememberUiText()
    val timezone = ready.catalog.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE
    val now = rememberPresentationTime(timezone)
    val last = ready.lastSuccessfulRefreshAt
    AppDialog(onDismissRequest = dismiss, title = { Text(uiText(R.string.home_data_times_title)) },
        text = {
            DialogBody {
                LabeledValue(uiText(R.string.home_last_refresh_label), last?.let { relativeTime(it, now, text = uiText) } ?: uiText(R.string.home_never_refreshed_label))
                LabeledValue(uiText(R.string.home_last_successful_refresh_label), last?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.home_not_yet_label))
                LabeledValue(uiText(R.string.home_publication_time_label), ready.publishedAt?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.publication_no_verified_publication_message))
                LabeledValue(uiText(R.string.home_pharmacy_timezone_label), bidi(timezone))
            }
        }, dismissButton = if (refresh != null) ({ AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = refresh) { Text(uiText(R.string.review_refresh_now)) } }) else null,
        confirmButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = dismiss) { Text(uiText(R.string.shared_close_action)) } })
}
