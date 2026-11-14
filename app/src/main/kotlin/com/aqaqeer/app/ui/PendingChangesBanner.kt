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
    if (!ready.admin || ready.isDemo || (ready.pendingCount == 0 && !ready.uncertain && !sharing.busy)) return
    val working = busy || sharing.busy
    val blocked = publicationBlockedReason(ready, sharing, working)
    GroupedCard {
        Text(uiText(R.string.ui_pending_changes_banner_001, MedicationPresentation.easternDigits(ready.pendingCount.toString())))
        if (blocked != null) Text(blocked, style = MaterialTheme.typography.bodyMedium)
        else Text(if (ready.lifecycle == "LOCAL_DRAFT") uiText(R.string.ui_pending_changes_banner_002)
            else uiText(R.string.ui_pending_changes_banner_003), style = MaterialTheme.typography.bodyMedium)
        sharing.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        ActionGroup {
            TextButton(enabled = !working, onClick = review) { Text(uiText(R.string.ui_pending_changes_banner_004)) }
            Button(enabled = blocked == null, onClick = publish) {
                AppIcon(UiIcon.PUBLISH)
                Spacer(Modifier.width(UiSpacing.hair))
                Text(uiText(R.string.ui_pending_changes_banner_005))
            }
        }
    }
}

internal fun publicationBlockedReason(ready: CatalogState.Ready, sharing: SharingStatus, working: Boolean): String? = when {
        ready.requiresUpdate -> uiText(R.string.ui_pending_changes_banner_006)
        ready.maintenancePaused -> uiText(R.string.ui_pharmacy_admin_screen_032)
        ready.restorePaused -> uiText(R.string.ui_pharmacy_admin_screen_033)
        working -> uiText(R.string.ui_pending_changes_banner_007)
        !ready.hasPublicationConnection -> uiText(R.string.ui_pending_changes_banner_008)
        sharing.conflicts.isNotEmpty() -> uiText(R.string.ui_pending_changes_banner_009)
        ready.uncertain -> uiText(R.string.ui_pending_changes_banner_010)
        else -> null
    }
