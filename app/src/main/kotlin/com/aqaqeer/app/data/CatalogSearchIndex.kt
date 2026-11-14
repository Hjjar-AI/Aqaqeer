package com.aqaqeer.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@androidx.compose.runtime.Immutable
data class MedicationRowUi(val medication: Medication, val form: MedicationForm?, val category: String,
    val status: AvailabilityStatus, val elsewhere: List<Section>, val unpublished: Boolean)
data class HomeBrowseResult(val catalog: CatalogSnapshot? = null, val sectionId: String? = null,
    val query: String = "", val filters: HomeFilters = HomeFilters(), val preferences: HomePreferences = HomePreferences(),
    val visible: List<Medication> = emptyList(), val groups: List<MedicationGroup> = emptyList(),
    val rows: Map<String, MedicationRowUi> = emptyMap())

/** Immutable normalized content; query work never normalizes inside a sort comparator. */
class CatalogSearchIndex(val catalog: CatalogSnapshot) {
    private data class Entry(val medication: Medication, val fields: List<String>, val scientific: List<String>,
        val arabic: List<String>, val english: List<String>, val letter: String)
    private val entries = catalog.medications.map { med ->
        fun key(value: String?) = normalizedName(value.orEmpty())
        val scientific = MedicationPresentation.scientificName(med)
        Entry(med, listOf(med.nameAr, med.nameEn, med.genericAr, med.genericEn, med.brand, med.strength)
            .map { MedicationPresentation.normalizedSearch(it.orEmpty()) },
            listOf(key(scientific), key(if (med.genericAr != null) med.genericEn else ""), key(med.strength)),
            listOf(key(med.nameAr ?: med.nameEn ?: scientific), key(med.nameEn)),
            listOf(key(med.nameEn ?: med.nameAr ?: med.genericEn ?: med.genericAr), key(med.nameAr)),
            MedicationPresentation.letter(med))
    }
    private fun compareKeys(a: List<String>, b: List<String>): Int {
        a.indices.forEach { i -> val comparison = a[i].compareTo(b[i]); if (comparison != 0) return comparison }
        return 0
    }
    private fun order(sort: HomeSort, section: String?) = Comparator<Entry> { a, b ->
        fun rank(entry: Entry) = when (section?.let { catalog.status(entry.medication.id, it) } ?: AvailabilityStatus.UNKNOWN) {
            AvailabilityStatus.OUT -> 0; AvailabilityStatus.LOW -> 1; AvailabilityStatus.UNKNOWN -> 2; AvailabilityStatus.AVAILABLE -> 3
        }
        val comparison = when (sort) {
            HomeSort.ARABIC_NAME -> compareKeys(a.arabic, b.arabic)
            HomeSort.ENGLISH_NAME -> compareKeys(a.english, b.english)
            HomeSort.SCIENTIFIC_NAME -> compareKeys(a.scientific, b.scientific)
            HomeSort.AVAILABILITY -> rank(a).compareTo(rank(b)).takeIf { it != 0 } ?: compareKeys(a.scientific, b.scientific)
        }
        comparison.takeIf { it != 0 } ?: a.medication.createdAt.compareTo(b.medication.createdAt).takeIf { it != 0 }
            ?: a.medication.id.compareTo(b.medication.id)
    }
    private fun score(entry: Entry, normalized: String, tokens: List<String>) = entry.fields.mapIndexedNotNull { index, field ->
        if (field.isEmpty() || tokens.none { it in field }) null else {
            val priority = when (index) { 2, 3 -> 0; 0 -> 1; 1 -> 2; else -> 3 }
            priority * 3 + when { field == normalized -> 0; field.startsWith(normalized) -> 1; else -> 2 }
        }
    }.minOrNull() ?: Int.MAX_VALUE
    fun browse(section: String?, query: String, filters: HomeFilters, sort: HomeSort): List<Medication> =
        ordered(section, query, filters, sort).first.map { it.medication }
    private fun ordered(section: String?, query: String, filters: HomeFilters, sort: HomeSort): Pair<List<Entry>, Map<String, Int>> {
        val normalized = MedicationPresentation.normalizedSearch(query)
        val tokens = normalized.split(' ').filter(String::isNotEmpty)
        val filtered = entries.filter { e ->
            (filters.categoryIds.isEmpty() || e.medication.categoryId in filters.categoryIds) &&
                (filters.statuses.isEmpty() || section != null && catalog.status(e.medication.id, section) in filters.statuses) &&
                tokens.all { token -> e.fields.any { token in it } }
        }
        val scores = if (tokens.isEmpty()) emptyMap() else filtered.associate { it.medication.id to score(it, normalized, tokens) }
        val comparator = if (tokens.isEmpty()) order(sort, section) else compareBy<Entry> { scores.getValue(it.medication.id) }
            .then(order(HomeSort.SCIENTIFIC_NAME, section))
        return filtered.sortedWith(comparator) to scores
    }
    fun groups(medications: List<Medication>, query: String, mode: HomeGrouping): List<MedicationGroup> {
        val ids = medications.map { it.id }.toSet()
        val normalized = MedicationPresentation.normalizedSearch(query)
        val tokens = normalized.split(' ').filter(String::isNotEmpty)
        val scores = entries.filter { it.medication.id in ids }.associate { it.medication.id to score(it, normalized, tokens) }
        return grouped(medications, mode, scores, tokens.isNotEmpty())
    }
    private fun grouped(medications: List<Medication>, mode: HomeGrouping, scores: Map<String, Int>, searching: Boolean): List<MedicationGroup> {
        val letters = entries.associate { it.medication.id to it.letter }
        val groups = medications.groupBy { if (mode == HomeGrouping.CATEGORY) it.categoryId else letters.getValue(it.id) }
            .map { (key, members) -> MedicationGroup("group_${mode.name}_$key",
                if (mode == HomeGrouping.CATEGORY) catalog.categoriesById[key]?.nameAr ?: "غير مصنف" else key, members) }
        val labels = groups.associate { it.key to normalizedName(it.label) }
        val alphabetical = compareBy<MedicationGroup>({ labels.getValue(it.key) }, { it.label }, { it.key })
        return groups.sortedWith(if (searching) compareBy<MedicationGroup> { group ->
            group.medications.minOf { scores.getValue(it.id) }
        }.then(alphabetical) else alphabetical)
    }
    suspend fun project(section: String?, query: String, filters: HomeFilters, preferences: HomePreferences,
        unpublished: Set<String>): HomeBrowseResult {
        currentCoroutineContext().ensureActive()
        val (ordered, scores) = ordered(section, query, filters, preferences.sort)
        val visible = ordered.map { it.medication }
        val groups = if (preferences.groupingEnabled) grouped(visible, preferences.grouping, scores,
            MedicationPresentation.normalizedSearch(query).isNotEmpty()) else emptyList()
        val rows = visible.associate { med ->
            currentCoroutineContext().ensureActive()
            med.id to MedicationRowUi(med, catalog.formsById[med.formId], catalog.categoriesById[med.categoryId]?.nameAr ?: "غير مصنف",
                section?.let { catalog.status(med.id, it) } ?: AvailabilityStatus.UNKNOWN,
                MedicationPresentation.availableElsewhere(catalog, med.id, section), med.id in unpublished)
        }
        return HomeBrowseResult(catalog, section, query, filters, preferences, visible, groups, rows)
    }
}
