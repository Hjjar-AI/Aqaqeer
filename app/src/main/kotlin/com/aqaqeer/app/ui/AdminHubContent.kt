package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun AdminHubContent(ready: CatalogState.Ready, vm: CatalogViewModel) {
    val uiText = rememberUiText()

    if (ready.admin && !ready.isDemo) SetupChecklist(ready) { vm.navigate(it) }
    if (!ready.admin) {
        NavigationCard(uiText(R.string.navigation_promote_admin_title), { vm.navigate(Route.Promote) })
        NavigationCard(uiText(R.string.navigation_maintenance_title), { vm.navigate(Route.Maintenance) })
        NavigationCard(uiText(R.string.navigation_leave_pharmacy_title), { vm.navigate(Route.Leave) }, destructive = true)
        return
    }
    NavigationCard(uiText(R.string.navigation_backup_title), onClick = { vm.navigate(Route.Backup) })
    NavigationCard(uiText(R.string.navigation_leave_pharmacy_title), { vm.navigate(Route.Leave) }, destructive = true)
    if (ready.lifecycle == "LOCAL_DRAFT") InformationCard(uiText(R.string.publication_local_draft_label), uiText(R.string.publication_local_draft_help))
    NavigationGroup(uiText(R.string.catalog_device_connection_heading)) {
        NavigationRow(uiText(R.string.navigation_history_title), { vm.openHistory() })
        HorizontalDivider()
        NavigationRow(uiText(R.string.maintenance_connection_pin_action), { vm.navigate(Route.Maintenance) })
    }
    if (ready.maintenancePaused) {
        Text(uiText(R.string.maintenance_editing_publication_blocked_message))
        NavigationCard(uiText(R.string.navigation_leave_pharmacy_title), onClick = { vm.navigate(Route.Leave) }, destructive = true)
        return
    }
    if (ready.requiresUpdate) {
        Text(uiText(R.string.shared_update_editing_blocked_help))
        NavigationCard(uiText(R.string.publication_review_retained_work_action), vm::review)
        return
    }
    NavigationGroup(uiText(R.string.catalog_catalog_heading)) {
        NavigationRow(uiText(R.string.navigation_add_medication_title), { vm.navigate(Route.EditMedication()) })
        HorizontalDivider()
        NavigationRow(uiText(R.string.navigation_taxonomy_title), { vm.navigate(Route.Taxonomy) })
        HorizontalDivider()
        NavigationRow(uiText(R.string.exchange_csv_import_navigation_label), { vm.navigate(Route.CsvImport) })
        HorizontalDivider()
        NavigationRow(uiText(R.string.exchange_reports_navigation_label), { vm.navigate(Route.Reports) })
    }
    NavigationGroup(uiText(R.string.connection_sharing_heading)) {
        if (ready.lifecycle == "LOCAL_DRAFT" || ready.isDemo) NavigationRow(uiText(R.string.navigation_telegram_setup_title), { vm.navigate(Route.Setup) })
        if (ready.lifecycle == "LOCAL_DRAFT" || ready.isDemo) HorizontalDivider()
        NavigationRow(uiText(R.string.navigation_publication_review_title), vm::review)
        HorizontalDivider()
        NavigationRow(uiText(R.string.navigation_connection_export_title), { vm.navigate(Route.ExportConnection) }, enabled = ready.lifecycle == "SHARED" && !ready.restorePaused)
    }
}
