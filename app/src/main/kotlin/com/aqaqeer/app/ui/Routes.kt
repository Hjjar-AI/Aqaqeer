package com.aqaqeer.app.ui

/** UI destinations are typed; only RouteStorage interprets retained wire strings. */
sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data object About : Route
    data object Connection : Route
    data object Maintenance : Route
    data object Backup : Route
    data object CsvImport : Route
    data object Reports : Route
    data object Create : Route
    data object Admin : Route
    data object Setup : Route
    data object Review : Route
    data object Taxonomy : Route
    data object Promote : Route
    data object Leave : Route
    data object ExportConnection : Route
    data class Medication(val id: String) : Route
    data class EditMedication(val id: String? = null) : Route
    data class History(val medicationId: String? = null) : Route
    data class Unknown(val value: String) : Route
}

internal val Route.isWide: Boolean get() = this == Route.Home || this is Route.Medication
internal val Route.isForm: Boolean get() = when (this) {
    Route.Create, Route.Promote, Route.Leave, Route.Setup, Route.ExportConnection, Route.Connection,
    Route.Backup, Route.Maintenance, Route.Taxonomy -> true
    else -> false
}
internal val Route.medicationId: String? get() = when (this) {
    is Route.Medication -> id
    is Route.EditMedication -> id
    else -> null
}

/** Preserve existing saved navigation, feedback ownership and draft keys byte-for-byte. */
internal object RouteStorage {
    fun decode(value: String): Route = when {
        value == "home" -> Route.Home
        value == "settings" -> Route.Settings
        value == "about" -> Route.About
        value == "connection" -> Route.Connection
        value == "maintenance" -> Route.Maintenance
        value == "backup" -> Route.Backup
        value == "csv-import" -> Route.CsvImport
        value == "reports" -> Route.Reports
        value == "create" -> Route.Create
        value == "admin" -> Route.Admin
        value == "setup" -> Route.Setup
        value == "review" -> Route.Review
        value == "taxonomy" -> Route.Taxonomy
        value == "promote" -> Route.Promote
        value == "leave" -> Route.Leave
        value == "export-connection" -> Route.ExportConnection
        value == "history" -> Route.History()
        value.startsWith("med:") -> Route.Medication(value.removePrefix("med:"))
        value.startsWith("edit-med:") -> Route.EditMedication(value.removePrefix("edit-med:").takeUnless { it == "new" })
        value.startsWith("history:") -> Route.History(value.removePrefix("history:"))
        else -> Route.Unknown(value)
    }
    fun encode(route: Route): String = when (route) {
        Route.Home -> "home"
        Route.Settings -> "settings"
        Route.About -> "about"
        Route.Connection -> "connection"
        Route.Maintenance -> "maintenance"
        Route.Backup -> "backup"
        Route.CsvImport -> "csv-import"
        Route.Reports -> "reports"
        Route.Create -> "create"
        Route.Admin -> "admin"
        Route.Setup -> "setup"
        Route.Review -> "review"
        Route.Taxonomy -> "taxonomy"
        Route.Promote -> "promote"
        Route.Leave -> "leave"
        Route.ExportConnection -> "export-connection"
        is Route.Medication -> "med:${route.id}"
        is Route.EditMedication -> "edit-med:${route.id ?: "new"}"
        is Route.History -> route.medicationId?.let { "history:$it" } ?: "history"
        is Route.Unknown -> route.value
    }
}
