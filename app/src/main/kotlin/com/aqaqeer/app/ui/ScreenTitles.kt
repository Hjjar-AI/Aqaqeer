package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

internal fun screenTitle(route: String): String = screenTitle(RouteStorage.decode(route))
internal fun screenTitle(route: Route): String = uiText(screenTitleResource(route))
internal fun screenTitleResource(route: Route): Int = when (route) {
    Route.Create -> R.string.navigation_create_pharmacy_title
    Route.Admin -> R.string.navigation_admin_tools_title
    Route.Setup -> R.string.navigation_telegram_setup_title
    Route.Review -> R.string.navigation_publication_review_title
    Route.Taxonomy -> R.string.navigation_taxonomy_title
    Route.Promote -> R.string.navigation_promote_admin_title
    Route.Leave -> R.string.navigation_leave_pharmacy_title
    Route.ExportConnection -> R.string.navigation_connection_export_title
    is Route.EditMedication -> if (route.id == null) R.string.navigation_add_medication_title else R.string.navigation_edit_medication_title
    Route.Backup -> R.string.navigation_backup_title
    Route.Settings -> R.string.navigation_settings_title
    Route.About -> R.string.navigation_about_title
    Route.Connection -> R.string.navigation_connection_file_title
    is Route.History -> R.string.navigation_history_title
    Route.CsvImport -> R.string.navigation_csv_import_title
    Route.Reports -> R.string.navigation_reports_title
    Route.Maintenance -> R.string.navigation_maintenance_title
    is Route.Medication -> R.string.navigation_medication_details_title
    Route.Home, is Route.Unknown -> R.string.navigation_app_title
}
