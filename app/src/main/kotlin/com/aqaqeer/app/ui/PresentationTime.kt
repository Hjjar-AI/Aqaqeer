package com.aqaqeer.app.ui

import com.aqaqeer.app.R

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aqaqeer.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Refresh date-sensitive badges on resume and at pharmacy midnight, even without a DB edit. */
@Composable internal fun rememberPresentationTime(timezone: String): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(Instant.now(), lifecycle, timezone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = Instant.now()
                val midnight = value.atZone(ZoneId.of(timezone)).toLocalDate().plusDays(1)
                    .atStartOfDay(ZoneId.of(timezone)).toInstant()
                // Wake at pharmacy midnight; at most one minute between clock-change checks.
                delay((Duration.between(value, midnight).toMillis() + 1).coerceIn(1, 60_000))
            }
        }
    }.value
}

/** Minute clock checks do not emit while the pharmacy date is unchanged. */
@Composable internal fun rememberPresentationDate(timezone: String): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun dayStart() = Instant.now().atZone(ZoneId.of(timezone)).toLocalDate().atStartOfDay(ZoneId.of(timezone)).toInstant()
    return produceState(dayStart(), lifecycle, timezone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = dayStart()
                val next = value.atZone(ZoneId.of(timezone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant()
                delay((Duration.between(Instant.now(), next).toMillis() + 1).coerceIn(1, 60_000))
            }
        }
    }.value
}

internal const val DEFAULT_PRESENTATION_TIMEZONE = "Asia/Damascus"

internal fun relativeTime(at: Instant, now: Instant, text: UiText? = null): String {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    val seconds = Duration.between(at, now).seconds
    return when {
        seconds < 0 -> uiText(R.string.shared_relative_time_future_message)
        seconds < 60 -> uiText(R.string.shared_relative_time_now_label)
        seconds < 3600 -> uiText(R.string.shared_relative_time_minutes_format, MedicationPresentation.displayDigits((seconds / 60).toString()))
        seconds < 86400 -> uiText(R.string.shared_relative_time_hours_format, MedicationPresentation.displayDigits((seconds / 3600).toString()))
        else -> uiText(R.string.shared_relative_time_days_format, MedicationPresentation.displayDigits((seconds / 86400).toString()))
    }
}
