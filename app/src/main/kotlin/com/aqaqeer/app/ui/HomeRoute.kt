package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeRoute(state: AppUiState, catalogState: CatalogState.Ready, viewModel: CatalogViewModel, now: java.time.Instant,
    adminBusy: Boolean, adminMessage: String?, availabilitySavedId: String?, sharing: com.aqaqeer.app.admin.SharingStatus,
    dualPane: Boolean, homeModalOpen: Boolean, dataTimesOpen: Boolean,
    onHomeModalChanged: (Boolean) -> Unit, canOpenHomeModal: () -> Boolean, navigate: (Route) -> Unit, back: () -> Unit,
    medicationActions: @Composable (CatalogState.Ready, Medication) -> Unit) {
    val uiText = rememberUiText()

    val selectedMedication = (state.destination as? Route.Medication)?.id
    val medication = catalogState.catalog.medications.firstOrNull { it.id == selectedMedication }
    val showReminder = catalogState.backupReminderAt != null
    val noticeHeader: (@Composable (Set<String>) -> Unit)? = if (catalogState.maintenancePaused || catalogState.restorePaused ||
        showReminder || catalogState.admin && !catalogState.isDemo && catalogState.lifecycle == "LOCAL_DRAFT") ({ visible ->
        if (catalogState.maintenancePaused && "maintenance" in visible) {
            InformationCard(uiText(R.string.maintenance_publication_paused_title), uiText(R.string.maintenance_paused_browsing_help))
            NavigationCard(uiText(R.string.maintenance_continue_action), { navigate(Route.Maintenance) })
        }
        if (catalogState.restorePaused && "restore" in visible) {
            InformationCard(uiText(R.string.backup_restore_publication_paused_title), uiText(R.string.backup_restore_paused_browsing_help))
            NavigationCard(uiText(R.string.backup_review_restore_action), { navigate(Route.Backup) })
        }
        if (showReminder && "backup" in visible) DueBackupReminder(catalogState,
            { navigate(Route.Backup) }, { viewModel.command { viewModel.backups.dismissReminder() } })
        if (catalogState.admin && !catalogState.isDemo && catalogState.lifecycle == "LOCAL_DRAFT" && "local-draft" in visible)
            SupportingText(uiText(R.string.publication_local_draft_label))
    }) else null
    val pageHeader: (@Composable () -> Unit)? = if (noticeHeader == null) null else ({
        noticeHeader(setOf("maintenance", "restore", "backup", "local-draft"))
    })
    Box(Modifier.fillMaxSize()) {
        val browseContent: @Composable () -> Unit = {
            HomeStateContent(viewModel) { state, browse, highlighted -> HomeScreen(ready = catalogState,
                state = state,
                now = now,
                availabilityBusy = adminBusy,
                availabilityMessage = adminMessage,
                pageHeader = noticeHeader,
                availabilitySavedId = availabilitySavedId,
                publicationBusy = sharing.busy,
                publicationBlockReason = publicationBlockedReason(catalogState, sharing, adminBusy || sharing.busy),
                projection = browse,
                highlightedMedication = highlighted,
                actions = rememberHomeActions(HomeCallbacks(
                    selectSection = viewModel::selectSection,
                    setQuery = viewModel::setQuery,
                    toggleStatus = viewModel::toggleStatus,
                    toggleCategory = viewModel::toggleCategory,
                    clearFilters = viewModel::clearFilters,
                    openMedication = { navigate(Route.Medication(it)) },
                    openSettings = { navigate(Route.Settings) },
                    openConnection = { viewModel.openConnectionImport() },
                    refresh = if (!catalogState.canRefreshCatalog) null else ({ viewModel.refreshCatalog() }),
                    openCreate = { navigate(Route.Create) },
                    openRestore = { navigate(Route.Backup) },
                    saveAvailability = { selection, status, complete ->
                    viewModel.saveAvailability(selection, BulkMedicationChanges(status = status), complete)
                },
                    openAddMedication = { viewModel.newMedicationName = ""; navigate(Route.EditMedication()) },
                    onModalVisibilityChanged = onHomeModalChanged,
                    availabilityInProgress = { viewModel.adminBusy.value },
                    canOpenHomeModal = canOpenHomeModal,
                    setGroupingEnabled = viewModel::setGroupingEnabled,
                    setGrouping = viewModel::setGrouping,
                    saveBulk = viewModel::saveAvailability,
                    prefillMedication = { query -> viewModel.newMedicationName = query; navigate(Route.EditMedication()) },
                    toggleExtraFilter = viewModel::toggleExtraFilter,
                    toggleAvailableFilter = viewModel::toggleAvailableFilter,
                    editMedication = { id -> navigate(Route.EditMedication(id)) }))) }
        }
        val detailContent: @Composable () -> Unit = {
            if (medication != null) MedicationScreen(catalogState, medication, now,
                state.homePreferences.secondaryHijri, pageHeader, viewModel)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(uiText(R.string.catalog_detail_choose_medication_message)) }
        }
        HomeDetailLayout(dualPane = dualPane, showDetail = medication != null,
            list = browseContent, detail = detailContent,
            detailHeader = if (medication != null) ({
                    Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.related),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                        AppIconButton(enabled = !adminBusy && !homeModalOpen && !dataTimesOpen,
                            onClick = back, modifier = Modifier.testTag("back")) {
                            AppIcon(UiIcon.BACK, description = uiText(R.string.navigation_back_description))
                        }
                        Column(Modifier.weight(1f)) {
                            MedicationTitle(medication)
                        }
                        medicationActions(catalogState, medication)
                    }
            }) else null)

    }
}
