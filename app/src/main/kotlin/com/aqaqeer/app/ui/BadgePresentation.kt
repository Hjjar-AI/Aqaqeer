package com.aqaqeer.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.aqaqeer.app.R
import com.aqaqeer.app.data.AvailabilityStatus
import com.aqaqeer.app.data.ExpiryState
import com.aqaqeer.app.uiText

internal data class BadgePresentation(val text: String, val tone: StatusTone, val icon: UiIcon? = null)

@Composable internal fun availabilityBadge(status: AvailabilityStatus): BadgePresentation = BadgePresentation(
    statusLabel(status, rememberUiText()), statusTone(status), when (status) {
        AvailabilityStatus.AVAILABLE -> UiIcon.CHECK
        AvailabilityStatus.LOW -> UiIcon.WARNING
        AvailabilityStatus.OUT -> UiIcon.CLOSE
        AvailabilityStatus.UNKNOWN -> UiIcon.UNKNOWN
    })

@Composable internal fun expiryBadge(expiry: ExpiryState): BadgePresentation {
    val uiText = rememberUiText()
    val expired = expiry == ExpiryState.EXPIRED
    return BadgePresentation(uiText(if (expired) R.string.catalog_expired_badge_label else R.string.catalog_expiring_soon_badge_label),
        if (expired) LocalStatusColors.current.out else LocalStatusColors.current.low, UiIcon.WARNING)
}

/** Pending and fetched markers retain their existing text-only presentation, distinct from availability. */
@Composable internal fun changeBadge(fetched: Boolean): BadgePresentation = BadgePresentation(
    rememberUiText()(if (fetched) R.string.review_changed else R.string.review_unpublished),
    StatusTone(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outline))
