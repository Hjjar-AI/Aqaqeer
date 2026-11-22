package com.aqaqeer.app.ui

import com.aqaqeer.app.data.*
import kotlinx.coroutines.launch

internal class PreferenceWriter(private val preferences: DisplayPreferences, private val scope: kotlinx.coroutines.CoroutineScope) {
    fun setSection(pharmacy: String, id: String) { scope.launch { preferences.setSection(pharmacy, id) } }
    fun setTheme(theme: ThemeChoice) { scope.launch { preferences.setTheme(theme) } }
    fun setAccent(accent: ThemeAccent) { scope.launch { preferences.setAccent(accent) } }
    fun setSort(value: HomeSort) { scope.launch { preferences.setSort(value) } }
    fun setGroupingEnabled(value: Boolean) { scope.launch { preferences.setGroupingEnabled(value) } }
    fun setGrouping(value: HomeGrouping) { scope.launch { preferences.setGrouping(value) } }
    fun setTwoColumns(value: Boolean) { scope.launch { preferences.setTwoColumns(value) } }
    fun setSectionsAtBottom(value: Boolean) { scope.launch { preferences.setSectionsAtBottom(value) } }
    fun setSearchAtBottom(value: Boolean) { scope.launch { preferences.setSearchAtBottom(value) } }
    fun setLayout(value: CardLayout) { scope.launch { preferences.setLayout(value) } }
    fun setSecondaryHijri(value: Boolean) { scope.launch { preferences.setSecondaryHijri(value) } }
    fun setNames(value: NameDisplay) { scope.launch { preferences.setNames(value) } }
}
