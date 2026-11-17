package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

internal fun screenTitle(route: String): String = screenTitle(RouteStorage.decode(route))
internal fun screenTitle(route: Route): String = uiText(screenTitleResource(route))
internal fun screenTitleResource(route: Route): Int = when (route) {
    Route.Create -> R.string.ui_screen_titles_001
    Route.Admin -> R.string.ui_screen_titles_002
    Route.Setup -> R.string.ui_screen_titles_003
    Route.Review -> R.string.ui_screen_titles_004
    Route.Taxonomy -> R.string.ui_screen_titles_005
    Route.Promote -> R.string.ui_screen_titles_006
    Route.Leave -> R.string.ui_screen_titles_007
    Route.ExportConnection -> R.string.ui_screen_titles_008
    is Route.EditMedication -> if (route.id == null) R.string.ui_screen_titles_009 else R.string.ui_screen_titles_010
    Route.Backup -> R.string.ui_screen_titles_011
    Route.Settings -> R.string.ui_screen_titles_012
    Route.About -> R.string.ui_screen_titles_013
    Route.Connection -> R.string.ui_screen_titles_014
    is Route.History -> R.string.ui_screen_titles_015
    Route.CsvImport -> R.string.ui_screen_titles_016
    Route.Reports -> R.string.ui_screen_titles_017
    Route.Maintenance -> R.string.ui_screen_titles_018
    is Route.Medication -> R.string.ui_screen_titles_019
    Route.Home, is Route.Unknown -> R.string.ui_screen_titles_020
}
