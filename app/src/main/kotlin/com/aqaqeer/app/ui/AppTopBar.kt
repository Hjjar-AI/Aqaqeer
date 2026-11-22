package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppTopBar(title: String, titleMedication: Medication?, readyForTitle: CatalogState.Ready?, homeDestination: Boolean,
    configured: Boolean, drawerEligible: Boolean, dualPane: Boolean, homeModalOpen: Boolean, dataTimesOpen: Boolean,
    adminBusy: Boolean, publicationBusy: Boolean, showDataTimes: () -> Unit, openMenu: () -> Unit,
    back: () -> Unit, review: () -> Unit, medicationActions: @Composable (CatalogState.Ready, Medication) -> Unit) {
    val uiText = rememberUiText()
    val homeBarHeight = with(LocalDensity.current) {
        MaterialTheme.typography.titleLarge.lineHeight.toDp() +
            MaterialTheme.typography.labelMedium.lineHeight.toDp() + UiSpacing.related
    }.coerceAtLeast(UiSpacing.touch)
    TopAppBar(title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            if (homeDestination && configured) BrandEmblem(Modifier.size(Space.xxl), description = null)
            Column(Modifier.weight(1f).then(if (drawerEligible) Modifier
                .heightIn(min = UiSpacing.touch)
                .clickable(enabled = !homeModalOpen && !dataTimesOpen,
                    onClickLabel = uiText(R.string.home_data_times_description)) {
                    showDataTimes()
                } else Modifier), verticalArrangement = Arrangement.Center) {
                if (titleMedication != null && !dualPane) {
                    MedicationTitle(titleMedication, singleLine = true)
                } else Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("screen_title"))
                if (drawerEligible) {
                    val ready = requireNotNull(readyForTitle)
                    val timeLabel = when {
                        ready.isDemo -> uiText(R.string.home_demo_data_label)
                        ready.lifecycle == "LOCAL_DRAFT" -> uiText(R.string.publication_unpublished_local_draft_label)
                        else -> uiText(R.string.home_data_time_prefix) + (ready.publishedAt?.let {
                            bidi(MedicationPresentation.timestamp(it, ready.catalog.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE))
                        } ?: uiText(R.string.publication_no_verified_publication_message))
                    }
                    Text(timeLabel, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }, navigationIcon = {
        if (homeDestination) IconButton(
            enabled = !homeModalOpen && !dataTimesOpen,
            onClick = {
                if (!homeModalOpen && !dataTimesOpen) openMenu()
            }, modifier = Modifier.testTag("home_menu")) {
            AppIcon(if (drawerEligible) UiIcon.MENU else UiIcon.SETTINGS, description = if (drawerEligible) uiText(R.string.navigation_open_drawer_description) else uiText(R.string.navigation_settings_title))
        }
        if (!homeDestination) IconButton(enabled = !adminBusy, onClick = back, modifier = Modifier.testTag("back")) {
            AppIcon(UiIcon.BACK, description = uiText(R.string.navigation_back_description))
        }
    }, actions = {
        if (drawerEligible && readyForTitle != null && readyForTitle.admin && (readyForTitle.pendingCount > 0 || readyForTitle.uncertain || publicationBusy)) {
            AppButton(variant = AppButtonVariant.QUIET, enabled = !adminBusy, onClick = review) {
                Text(uiText(R.string.publication_toolbar_pending_count_format, MedicationPresentation.displayDigits(readyForTitle.pendingCount.toString())))
            }
        }
        if (!dualPane && readyForTitle != null && titleMedication != null)
            medicationActions(readyForTitle, titleMedication)
    }, expandedHeight = if (homeDestination && configured || titleMedication != null) homeBarHeight else TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}
