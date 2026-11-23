package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aqaqeer.app.admin.SharingStatus
import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.data.MedicationPresentation

@Composable internal fun PendingChangesBanner(ready: CatalogState.Ready, sharing: SharingStatus, busy: Boolean,
    review: () -> Unit, publish: () -> Unit) {
    val uiText = rememberUiText()
    if (!ready.admin || (ready.pendingCount == 0 && !ready.uncertain && !sharing.busy)) return
    val working = busy || sharing.busy
    val blocked = publicationBlockedReason(ready, sharing, working)
    GroupedCard {
        Text(uiText(R.string.publication_pending_banner_count_format, MedicationPresentation.displayDigits(ready.pendingCount.toString())))
        if (blocked != null) Text(blocked, style = MaterialTheme.typography.bodyMedium)
        else Text(if (ready.lifecycle == "LOCAL_DRAFT") uiText(R.string.publication_pending_local_draft_help)
            else uiText(R.string.publication_pending_reconnect_help), style = MaterialTheme.typography.bodyMedium)
        sharing.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        ActionGroup {
            AppButton(variant = AppButtonVariant.QUIET, enabled = !working, onClick = review) { Text(uiText(R.string.publication_pending_review_action)) }
            AppButton(enabled = blocked == null, onClick = publish) {
                AppIcon(UiIcon.PUBLISH)
                Spacer(Modifier.width(UiSpacing.hair))
                Text(uiText(R.string.publication_pending_publish_action))
            }
        }
    }
}

internal fun publicationBlockedReason(ready: CatalogState.Ready, sharing: SharingStatus, working: Boolean): String? = when {
        ready.requiresUpdate -> uiText(R.string.publication_pending_update_required_message)
        ready.maintenancePaused -> uiText(R.string.publication_maintenance_block_message)
        ready.restorePaused -> uiText(R.string.publication_restore_review_block_message)
        working -> uiText(R.string.publication_pending_busy_message)
        !ready.hasPublicationConnection -> uiText(R.string.publication_pending_setup_required_message)
        sharing.conflicts.isNotEmpty() -> uiText(R.string.publication_pending_conflicts_message)
        ready.uncertain -> uiText(R.string.publication_pending_attempt_review_message)
        else -> null
    }
