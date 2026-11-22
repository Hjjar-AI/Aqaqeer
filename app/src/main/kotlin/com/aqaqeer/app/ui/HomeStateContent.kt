package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.*

@Composable internal fun HomeStateContent(vm: CatalogViewModel,
    content: @Composable (AppUiState, HomeBrowseResult, String?) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val browse by vm.homeBrowse.collectAsStateWithLifecycle()
    val highlighted by vm.highlightedMedication.collectAsStateWithLifecycle()
    LaunchedEffect(highlighted) { if (highlighted != null) { kotlinx.coroutines.delay(2500); vm.highlightedMedication.compareAndSet(highlighted, null) } }
    content(state, browse, highlighted)
}

@Composable internal fun DueBackupReminder(ready: CatalogState.Ready, backup: () -> Unit, dismiss: () -> Unit) {
    val now = rememberPresentationTime(ready.catalog.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE)
    if (ready.backupReminderAt?.let { now.toEpochMilli() >= it } == true) BackupReminderCard(backup, dismiss)
}

@Composable internal fun DrawerFiltersContent(vm: CatalogViewModel, content: @Composable (HomeFilters) -> Unit) {
    val filters by vm.drawerFilters.collectAsStateWithLifecycle()
    content(filters)
}
