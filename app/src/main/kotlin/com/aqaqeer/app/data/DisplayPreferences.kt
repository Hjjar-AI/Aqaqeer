package com.aqaqeer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.displayDataStore by preferencesDataStore("display")
enum class ThemeChoice { SYSTEM, LIGHT, DARK, AMOLED }
enum class ThemeAccent { GOLD, BLUE, GREEN, PURPLE, CRIMSON }

/** Only presentation preferences go here. No keys, credentials, PINs, or admin mode. */
class DisplayPreferences(private val context: Context) {
    private val data = context.displayDataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
    // Non-secret launch hints; DataStore remains authoritative. Each flow updates only its own key.
    val theme: Flow<ThemeChoice> = data.map { prefs -> ThemeChoice.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeChoice.SYSTEM }
        .onEach { cacheStartupValue("theme", it.name) }.flowOn(Dispatchers.IO)
    val accent: Flow<ThemeAccent> = data.map { prefs -> ThemeAccent.entries.firstOrNull { it.name == prefs[ACCENT] } ?: ThemeAccent.GOLD }
        .onEach { cacheStartupValue("accent", it.name) }.flowOn(Dispatchers.IO)
    private fun cacheStartupValue(key: String, value: String) {
        val cache = context.getSharedPreferences(STARTUP_CACHE, Context.MODE_PRIVATE)
        if (cache.getString(key, null) != value) cache.edit().putString(key, value).apply()
    }
    val sections: Flow<Map<String, String>> = data.map { prefs -> prefs.asMap().entries
        .filter { it.key.name.startsWith("selected_section_") }
        .associate { it.key.name.removePrefix("selected_section_") to it.value.toString() } }
    suspend fun setSection(pharmacyId: String, sectionId: String) {
        context.displayDataStore.edit { it[stringPreferencesKey("selected_section_$pharmacyId")] = sectionId }
    }
    suspend fun clearSection(pharmacyId: String) {
        context.displayDataStore.edit { it.remove(stringPreferencesKey("selected_section_$pharmacyId")) }
    }
    val home: Flow<HomePreferences> = data.map { prefs -> HomePreferences(
        HomeSort.entries.firstOrNull { it.name == prefs[SORT] } ?: HomeSort.SCIENTIFIC_NAME,
        CardLayout.entries.firstOrNull { it.name == prefs[LAYOUT] } ?: CardLayout.COMPACT,
        NameDisplay.entries.firstOrNull { it.name == prefs[NAMES] } ?: NameDisplay.BOTH,
        prefs[SECONDARY_HIJRI] ?: false,
        prefs[GROUPING_ENABLED] ?: true,
        HomeGrouping.entries.firstOrNull { it.name == prefs[GROUPING] } ?: HomeGrouping.CATEGORY,
        prefs[TWO_COLUMNS] ?: true,
    ) }
    suspend fun setTheme(value: ThemeChoice) { context.displayDataStore.edit { it[THEME] = value.name } }
    suspend fun setAccent(value: ThemeAccent) { context.displayDataStore.edit { it[ACCENT] = value.name } }
    suspend fun setSort(value: HomeSort) { context.displayDataStore.edit { it[SORT] = value.name } }
    suspend fun setLayout(value: CardLayout) { context.displayDataStore.edit { it[LAYOUT] = value.name } }
    suspend fun setTwoColumns(value: Boolean) { context.displayDataStore.edit { it[TWO_COLUMNS] = value } }
    suspend fun setNames(value: NameDisplay) { context.displayDataStore.edit { it[NAMES] = value.name } }
    suspend fun setSecondaryHijri(value: Boolean) { context.displayDataStore.edit { it[SECONDARY_HIJRI] = value } }
    suspend fun setGroupingEnabled(value: Boolean) { context.displayDataStore.edit { it[GROUPING_ENABLED] = value } }
    suspend fun setGrouping(value: HomeGrouping) { context.displayDataStore.edit { it[GROUPING] = value.name } }
    companion object {
        private const val STARTUP_CACHE = "startup_presentation"
        internal fun startupPresentation(context: Context): Pair<ThemeChoice, ThemeAccent> {
            val cache = context.getSharedPreferences(STARTUP_CACHE, Context.MODE_PRIVATE)
            return (ThemeChoice.entries.firstOrNull { it.name == cache.getString("theme", null) } ?: ThemeChoice.SYSTEM) to
                (ThemeAccent.entries.firstOrNull { it.name == cache.getString("accent", null) } ?: ThemeAccent.GOLD)
        }

        private val TWO_COLUMNS = booleanPreferencesKey("home_two_columns")
        private val GROUPING_ENABLED = booleanPreferencesKey("home_grouping_enabled")
        private val GROUPING = stringPreferencesKey("home_grouping")
        private val SECONDARY_HIJRI = booleanPreferencesKey("secondary_hijri")
        private val THEME = stringPreferencesKey("theme")
        private val ACCENT = stringPreferencesKey("theme_accent")
        private val SORT = stringPreferencesKey("home_sort")
        private val LAYOUT = stringPreferencesKey("card_layout")
        private val NAMES = stringPreferencesKey("name_display")
    }
}
