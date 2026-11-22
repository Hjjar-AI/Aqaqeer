package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aqaqeer.app.data.*
import java.time.Duration
import java.time.Instant

/** One pinned entry point; expanding retains the existing notice actions and publication guards. */
@Composable internal fun HomeNoticeStrip(ready: CatalogState.Ready, state: AppUiState,
    pageHeader: (@Composable (Set<String>) -> Unit)?, refresh: (() -> Unit)?, publicationBusy: Boolean, publicationBlockReason: String?,
    maxHeight: androidx.compose.ui.unit.Dp, compact: Boolean = false, onDialogVisibilityChanged: (Boolean) -> Unit) {
    val uiText = rememberUiText()
    val clock = rememberPresentationTime(ready.catalog.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE)
    val noticeScope = LocalNoticeScope.current
    val allNotices = buildHomeNotices(ready, state, clock, refresh != null, publicationBusy, publicationBlockReason, uiText)
    val notices = allNotices.filterNot { UiSession.isDismissed(listOf(noticeScope, "home", it.kind), it.content) }
    SideEffect { UiSession.reconcile(listOf(noticeScope, "home"), allNotices.associate { listOf(noticeScope, "home", it.kind) to it.content }) }
    val notifyDialog by rememberUpdatedState(onDialogVisibilityChanged)
    DisposableEffect(Unit) { onDispose { notifyDialog(false) } }
    if (notices.isEmpty()) {
        SideEffect { notifyDialog(false) }
        return
    }
    var expanded by rememberSaveable(ready.catalog.pharmacy?.pharmacyId) { mutableStateOf(false) }
    SideEffect { notifyDialog(expanded && compact) }
    fun visible(kind: String) = notices.any { it.kind == kind }
    val noticeDetails: @Composable ColumnScope.() -> Unit = {
        if ((visible("stale") || visible("never-refreshed")) && compact && refresh != null && !ready.isDemo && ready.lifecycle == "SHARED" &&
            (ready.lastSuccessfulRefreshAt == null || Duration.between(ready.lastSuccessfulRefreshAt, clock) >= Duration.ofHours(24)))
            NoticeStrip(Notice(uiText(R.string.home_stale_refresh_notice), NoticeSeverity.WARNING,
                uiText(R.string.review_refresh_now), refresh), enabled = !state.refresh.busy)
        if (visible("update")) InformationCard(uiText(R.string.home_update_required_title), uiText(R.string.home_update_required_help))
        if (visible("refresh-error") && refresh != null) RefreshControls(state.refresh, refresh)
        if (visible("offline")) SupportingText(uiText(R.string.home_offline_notice))
        if (!ready.isDemo && ready.lifecycle == "SHARED") {
            if (visible("never-refreshed")) SupportingText(uiText(R.string.home_never_refreshed_notice))
            else if (visible("stale"))
                SupportingText(uiText(R.string.home_stale_refresh_notice))
        }

        pageHeader?.invoke(notices.map { it.kind }.toSet())
        if (visible("demo")) DemoNotice()
    }
    if (expanded && compact) AppDialog(onDismissRequest = { expanded = false }, text = {
        Column(Modifier.heightIn(max = viewportHeight(0.7f)).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.related), content = noticeDetails)
    }, confirmButton = {
        AppButton(variant = AppButtonVariant.QUIET, onClick = { expanded = false }) { Text(uiText(R.string.shared_close_action)) }
    })
    Surface(color = if (visible("update") || visible("refresh-error")) MaterialTheme.colorScheme.errorContainer
        else if (visible("stale"))
            LocalStatusColors.current.low.container else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).animateContentSize(tween(Motion.standard))) {
        Column(Modifier.padding(horizontal = UiSpacing.page, vertical = UiSpacing.hair)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            AppButton(variant = AppButtonVariant.QUIET, onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                AppIcon(if (visible("update") || visible("refresh-error")) UiIcon.WARNING else UiIcon.INFO)
                Text(notices.first().text + if (notices.size > 1) uiText(R.string.home_notice_count_suffix, MedicationPresentation.displayDigits(notices.size.toString())) else "",
                    modifier = Modifier.weight(1f), maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                ExpansionChevron(expanded)
            }
            IconButton(onClick = {
                val notice = notices.first()
                UiSession.dismiss(listOf(noticeScope, "home", notice.kind), notice.content)
                expanded = false
            }) { AppIcon(UiIcon.CLOSE, description = uiText(R.string.notice_dismiss)) }
            }
            if ((visible("stale") || visible("never-refreshed")) && !compact && refresh != null && !ready.isDemo && ready.lifecycle == "SHARED" &&
                (ready.lastSuccessfulRefreshAt == null || Duration.between(ready.lastSuccessfulRefreshAt, clock) >= Duration.ofHours(24)))
                NoticeStrip(Notice(uiText(R.string.home_stale_refresh_notice), NoticeSeverity.WARNING,
                    uiText(R.string.review_refresh_now), refresh), enabled = !state.refresh.busy)
            if (expanded && !compact) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.related), content = noticeDetails)
        }
    }
}

internal fun buildHomeNotices(ready: CatalogState.Ready, state: AppUiState, clock: Instant,
    refreshAvailable: Boolean, publicationBusy: Boolean, publicationBlockReason: String?, uiText: UiText): List<HomeNotice> {
    return buildList {
        if (ready.requiresUpdate) add(HomeNotice("update", uiText(R.string.home_update_required_notice)))
        if (ready.maintenancePaused) add(HomeNotice("maintenance", uiText(R.string.home_maintenance_paused_notice)))
        if (ready.restorePaused) add(HomeNotice("restore", uiText(R.string.home_restore_paused_notice)))
        if (ready.admin && !ready.isDemo && (ready.pendingCount > 0 || ready.uncertain || publicationBusy) &&
            !ready.requiresUpdate && !ready.maintenancePaused && !ready.restorePaused && publicationBlockReason != null)
            add(HomeNotice("publication-block", publicationBlockReason))
        if (refreshAvailable && state.refresh.error != null) add(HomeNotice("refresh-error", uiText(R.string.home_refresh_failed_notice), listOf(state.refresh.error.orEmpty())))
        if (ready.admin && !ready.isDemo) {
            if (publicationBusy) add(HomeNotice("publication-progress", uiText(R.string.home_publication_progress_notice)))
            if (ready.uncertain) add(HomeNotice("uncertain", uiText(R.string.home_publication_review_notice)))
            if (ready.backupReminderAt?.let { clock.toEpochMilli() >= it } == true) add(HomeNotice("backup", uiText(R.string.backup_reminder_title), listOf(ready.backupReminderAt.toString())))
            if (ready.lifecycle == "LOCAL_DRAFT") add(HomeNotice("local-draft", uiText(R.string.publication_local_draft_label)))
        }
        if (!state.online) add(HomeNotice("offline", uiText(R.string.home_offline_notice)))
        if (!ready.isDemo && ready.lifecycle == "SHARED") {
            val last = ready.lastSuccessfulRefreshAt
            if (last == null) add(HomeNotice("never-refreshed", uiText(R.string.home_never_refreshed_notice)))
            else if (Duration.between(last, clock) >= Duration.ofHours(24)) add(HomeNotice("stale", uiText(R.string.home_stale_refresh_notice), listOf(last.toString())))
        }
        if (ready.isDemo) add(HomeNotice("demo", uiText(R.string.home_demo_data_label)))
    }
}
