package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*

@Composable internal fun SettingsScreen(theme: ThemeChoice,
    preferences: HomePreferences,
    accent: ThemeAccent,
    demo: Boolean = false,
    demoAdmin: Boolean = false,
    actions: SettingsActions) {
    val uiText = rememberUiText()
    val setTheme = actions.setTheme
    val setSort = actions.setSort
    val setLayout = actions.setLayout
    val setNames = actions.setNames
    val setSecondaryHijri = actions.setSecondaryHijri
    val setAccent = actions.setAccent
    val setTwoColumns = actions.setTwoColumns
    val setSectionsAtBottom = actions.setSectionsAtBottom
    val setSearchAtBottom = actions.setSearchAtBottom
    val setGroupingEnabled = actions.setGroupingEnabled
    val setGrouping = actions.setGrouping
    val openBackup = actions.openBackup
    val openMaintenance = actions.openMaintenance
    val openLeave = actions.openLeave
    val changeDemoAdmin = actions.changeDemoAdmin
    val resetDemo = actions.resetDemo
    val openAbout = actions.openAbout
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    LazyColumn(Modifier.fillMaxSize().testTag("settings"), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
        if (demo) item(key = "demo_mode") { DemoModeControls(demoAdmin, changeDemoAdmin, resetDemo) }
        item { SettingsGroup(uiText(R.string.review_appearance), initiallyExpanded = true) {
            SettingsChoiceRows(uiText(R.string.settings_theme_label), ThemeChoice.entries, theme, setTheme, {
                uiText(when (it) { ThemeChoice.SYSTEM -> R.string.settings_theme_system_label; ThemeChoice.LIGHT -> R.string.settings_theme_light_label
                    ThemeChoice.DARK -> R.string.settings_theme_dark_label; ThemeChoice.AMOLED -> R.string.settings_theme_amoled_label })
            }, choiceModifier = { Modifier.testTag("theme_${it.name}") })
            SettingsChoiceRows(uiText(R.string.settings_primary_accent_label), ThemeAccent.entries, accent, setAccent, {
                uiText(when (it) { ThemeAccent.GOLD -> R.string.settings_accent_gold_label; ThemeAccent.BLUE -> R.string.settings_accent_blue_label
                    ThemeAccent.GREEN -> R.string.settings_accent_green_label; ThemeAccent.PURPLE -> R.string.settings_accent_purple_label; ThemeAccent.CRIMSON -> R.string.settings_accent_crimson_label })
            }, swatch = { appColorScheme(theme, it, systemDark, resources).primary })
        } }
        item { SettingsGroup(uiText(R.string.review_list_display)) {
            SettingsChoiceRows(uiText(R.string.settings_medication_sort_label), HomeSort.entries, preferences.sort, setSort, label = {
                uiText(when (it) { HomeSort.SCIENTIFIC_NAME -> R.string.settings_sort_scientific_name_label; HomeSort.ARABIC_NAME -> R.string.settings_sort_arabic_trade_name_label
                    HomeSort.ENGLISH_NAME -> R.string.settings_sort_english_trade_name_label; HomeSort.AVAILABILITY -> R.string.settings_sort_section_availability_label })
            }, modifier = Modifier.testTag("sort_preference"), choiceModifier = { Modifier.testTag("sort_${it.name}") })
            ContentSection {
                CardLayoutPicker(preferences.layout, setLayout)
                MedicationLayoutPreview(preferences.layout)
            }
            SwitchChoiceRow(preferences.twoColumns, uiText(R.string.review_adaptive_columns), uiText(R.string.review_adaptive_columns_help), Modifier.testTag("two_columns"), setTwoColumns)
            ContentSection {
                SwitchChoiceRow(preferences.groupingEnabled, uiText(R.string.settings_grouping_label), uiText(R.string.settings_grouping_help), change = setGroupingEnabled)
                if (preferences.groupingEnabled) SettingsChoiceRows(uiText(R.string.settings_grouping_mode_label), HomeGrouping.entries,
                    preferences.grouping, setGrouping, label = { uiText(if (it == HomeGrouping.CATEGORY) R.string.catalog_category_label else R.string.settings_grouping_scientific_letter_label) },
                    modifier = Modifier.testTag("grouping_preference"), choiceModifier = { Modifier.testTag("grouping_${it.name}") })
            }
            SettingsChoiceRows(uiText(R.string.settings_medication_name_display_label), NameDisplay.entries, preferences.names, setNames, label = {
                uiText(when (it) { NameDisplay.ARABIC -> R.string.settings_names_arabic_label; NameDisplay.ENGLISH -> R.string.settings_names_english_label; NameDisplay.BOTH -> R.string.settings_names_both_label })
            }, modifier = Modifier.testTag("names_preference"), choiceModifier = { Modifier.testTag("names_${it.name}") })
        } }
        item { SettingsGroup(uiText(R.string.review_dates_placement)) {
            SwitchChoiceRow(preferences.sectionsAtBottom, uiText(R.string.home_sections_bottom), uiText(R.string.home_sections_bottom_help), Modifier.testTag("sections_at_bottom"), setSectionsAtBottom)
            SwitchChoiceRow(preferences.searchAtBottom, uiText(R.string.home_search_bottom), uiText(R.string.home_search_bottom_help), Modifier.testTag("search_at_bottom"), setSearchAtBottom)
            ContentSection {
                SwitchChoiceRow(preferences.secondaryHijri, uiText(R.string.settings_secondary_hijri_label), uiText(R.string.settings_secondary_hijri_help), Modifier.testTag("secondary_hijri"), setSecondaryHijri)
                ExpandableInformation(uiText(R.string.settings_hijri_method_title), uiText(R.string.settings_hijri_method_help))
            }
        } }
        openBackup?.let { item { NavigationCard(uiText(R.string.navigation_backup_title), it) } }
        item { NavigationCard(uiText(R.string.navigation_about_title), openAbout, Modifier.testTag("open_about")) }
    }
}
