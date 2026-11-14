package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

internal fun screenTitle(route: String): String = when {
    route == "create" -> uiText(R.string.ui_screen_titles_001)
    route == "admin" -> uiText(R.string.ui_screen_titles_002)
    route == "setup" -> uiText(R.string.ui_screen_titles_003)
    route == "review" -> uiText(R.string.ui_screen_titles_004)
    route == "taxonomy" -> uiText(R.string.ui_screen_titles_005)
    route == "promote" -> uiText(R.string.ui_screen_titles_006)
    route == "leave" -> uiText(R.string.ui_screen_titles_007)
    route == "export-connection" -> uiText(R.string.ui_screen_titles_008)
    route == "edit-med:new" -> uiText(R.string.ui_screen_titles_009)
    route.startsWith("edit-med:") -> uiText(R.string.ui_screen_titles_010)
    route == "backup" -> uiText(R.string.ui_screen_titles_011)
    route == "settings" -> uiText(R.string.ui_screen_titles_012)
    route == "about" -> uiText(R.string.ui_screen_titles_013)
    route == "connection" -> uiText(R.string.ui_screen_titles_014)
    route == "history" || route.startsWith("history:") -> uiText(R.string.ui_screen_titles_015)
    route == "csv-import" -> uiText(R.string.ui_screen_titles_016)
    route == "reports" -> uiText(R.string.ui_screen_titles_017)
    route == "maintenance" -> uiText(R.string.ui_screen_titles_018)
    route.startsWith("med:") -> uiText(R.string.ui_screen_titles_019)
    else -> uiText(R.string.ui_screen_titles_020)
}
