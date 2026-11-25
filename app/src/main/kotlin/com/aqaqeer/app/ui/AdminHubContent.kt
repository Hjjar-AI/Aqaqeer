package com.aqaqeer.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aqaqeer.app.R
import com.aqaqeer.app.data.CatalogState

@Composable internal fun AdminHubContent(ready: CatalogState.Ready, vm: CatalogViewModel) {
    val uiText = rememberUiText()

    if (!ready.admin) {
        AdminToolGroup(uiText(R.string.admin_hub_access_heading)) {
            AdminTool(R.string.navigation_promote_admin_title, UiIcon.SETUP,
                supporting = R.string.admin_hub_authorize_help) { vm.navigate(Route.Promote) }
            AppDivider()
            AdminTool(R.string.navigation_maintenance_title, UiIcon.TOOLS) { vm.navigate(Route.Maintenance) }
        }
    } else {
        val isDraft = ready.lifecycle == "LOCAL_DRAFT"
        when {
            ready.maintenancePaused -> NoticeStrip(Notice(
                uiText(R.string.maintenance_editing_publication_blocked_message), NoticeSeverity.WARNING,
                uiText(R.string.maintenance_review_first_action), { vm.navigate(Route.Maintenance) }))
            ready.requiresUpdate -> NoticeStrip(Notice(
                uiText(R.string.shared_update_editing_blocked_help), NoticeSeverity.WARNING,
                uiText(R.string.publication_review_retained_work_action), vm::review))
            ready.restorePaused -> NoticeStrip(Notice(
                uiText(R.string.backup_restore_admin_reconciliation_help), NoticeSeverity.WARNING,
                uiText(R.string.backup_complete_restore_review_action), { vm.navigate(Route.Backup) }))
            isDraft -> InformationCard(uiText(R.string.publication_local_draft_label),
                uiText(R.string.publication_local_draft_help))
        }

        if (!ready.isDemo) SetupChecklist(ready, vm::navigate)

        val canEditCatalog = !ready.maintenancePaused && !ready.requiresUpdate
        if (canEditCatalog) {
            AdminToolGroup(uiText(R.string.catalog_catalog_heading)) {
                AdminTool(R.string.navigation_add_medication_title, UiIcon.ADD) { vm.navigate(Route.EditMedication()) }
                AppDivider()
                AdminTool(R.string.navigation_taxonomy_title, UiIcon.CATEGORY) { vm.navigate(Route.Taxonomy) }
                AppDivider()
                AdminTool(R.string.exchange_csv_import_navigation_label, UiIcon.EDIT) { vm.navigate(Route.CsvImport) }
            }
        }
        AdminToolGroup(uiText(R.string.admin_hub_activity_heading)) {
            AdminTool(R.string.navigation_history_title, UiIcon.HISTORY) { vm.openHistory() }
            if (canEditCatalog) {
                AppDivider()
                AdminTool(R.string.exchange_reports_navigation_label, UiIcon.REPORT) { vm.navigate(Route.Reports) }
            }
        }
        if (canEditCatalog) {
            AdminToolGroup(uiText(R.string.connection_sharing_heading)) {
                if (isDraft || ready.isDemo) {
                    AdminTool(R.string.navigation_telegram_setup_title, UiIcon.SETUP) { vm.navigate(Route.Setup) }
                    AppDivider()
                }
                AdminTool(R.string.navigation_publication_review_title, UiIcon.PUBLISH, onClick = vm::review)
                AppDivider()
                AdminTool(R.string.navigation_connection_export_title, UiIcon.CONNECTION,
                    enabled = ready.lifecycle == "SHARED" && !ready.restorePaused,
                    supporting = when {
                        ready.restorePaused -> R.string.admin_hub_connection_after_restore
                        ready.lifecycle != "SHARED" -> R.string.admin_hub_connection_after_publication
                        else -> null
                    }) { vm.navigate(Route.ExportConnection) }
            }
        }
        AdminToolGroup(uiText(R.string.admin_hub_protection_heading)) {
            AdminTool(R.string.navigation_backup_title, UiIcon.BACKUP) { vm.navigate(Route.Backup) }
            AppDivider()
            AdminTool(R.string.maintenance_connection_pin_action, UiIcon.TOOLS) { vm.navigate(Route.Maintenance) }
        }
    }
    AdminToolGroup(uiText(R.string.admin_hub_device_heading)) {
        AdminTool(R.string.navigation_leave_pharmacy_title, UiIcon.WARNING,
            supporting = R.string.admin_hub_leave_help, destructive = true) { vm.navigate(Route.Leave) }
    }
}

@Composable private fun AdminToolGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    ContentSection(title = title) {
        AppCard(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable private fun AdminTool(
    @StringRes label: Int,
    icon: UiIcon,
    enabled: Boolean = true,
    @StringRes supporting: Int? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val uiText = rememberUiText()
    NavigationRow(uiText(label), onClick, enabled = enabled, supporting = supporting?.let { uiText(it) },
        destructive = destructive, icon = icon, contentPadding = UiSpacing.section)
}
