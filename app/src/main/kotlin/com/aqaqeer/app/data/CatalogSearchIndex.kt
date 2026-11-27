package com.aqaqeer.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@androidx.compose.runtime.Immutable
data class MedicationRowUi(val medication: Medication, val form: MedicationForm?, val category: String,
    val status: AvailabilityStatus, val elsewhere: List<Section>, val unpublished: Boolean, val fetched: Boolean = false)
data class SectionSearchResults(val section: Section, val rows: List<MedicationRowUi>)
data class HomeBrowseResult(val catalog: CatalogSnapshot? = null, val sectionId: String? = null,
    val query: String = "", val filters: HomeFilters = HomeFilters(), val preferences: HomePreferences = HomePreferences(),
    val visible: List<Medication> = emptyList(), val groups: List<MedicationGroup> = emptyList(),
    val rows: Map<String, MedicationRowUi> = emptyMap(),
    val fetched: Set<String> = emptySet(), val unpublished: Set<String> = emptySet(),
    val today: java.time.LocalDate? = null, val otherSections: Map<String, Set<String>> = emptyMap(),
    val readerStockOnly: Boolean = false, val availableSearchSections: Set<String> = emptySet(),
    val otherSectionResults: List<SectionSearchResults> = emptyList())

/** Immutable normalized content; query work never normalizes inside a sort comparator. */
class CatalogSearchIndex private constructor(val catalog: CatalogSnapshot, private val entries: List<Entry>) {
    constructor(catalog: CatalogSnapshot) : this(catalog, catalog.medications.map(::index))

    private data class Entry(val medication: Medication, val fields: List<String>, val scientific: List<String>,
        val arabic: List<String>, val english: List<String>, val letter: String)

    private companion object {
        fun index(med: Medication): Entry {
            fun key(value: String?) = normalizedName(value.orEmpty())
            val scientific = MedicationPresentation.scientificName(med)
            return Entry(med, listOf(med.nameAr, med.nameEn, med.genericAr, med.genericEn, med.brand, med.strength)
                .map { MedicationPresentation.normalizedSearch(it.orEmpty()) },
                listOf(key(scientific), key(if (med.genericAr != null) med.genericEn else ""), key(med.strength)),
                listOf(key(med.nameAr ?: med.nameEn ?: scientific), key(med.nameEn)),
                listOf(key(med.nameEn ?: med.nameAr ?: med.genericEn ?: med.genericAr), key(med.nameAr)),
                MedicationPresentation.letter(med))
        }
    }

    internal fun withCatalog(next: CatalogSnapshot): CatalogSearchIndex =
        if (catalog.pharmacy != null && catalog.pharmacy.pharmacyId == next.pharmacy?.pharmacyId &&
            catalog.medications == next.medications) CatalogSearchIndex(next, entries)
        else CatalogSearchIndex(next)

    private fun compareKeys(a: List<String>, b: List<String>): Int {
        a.indices.forEach { i -> val comparison = a[i].compareTo(b[i]); if (comparison != 0) return comparison }
        return 0
    }
    private fun order(sort: HomeSort, stockRanks: Map<String, Int> = emptyMap()) = Comparator<Entry> { a, b ->
        val comparison = when (sort) {
            HomeSort.ARABIC_NAME -> compareKeys(a.arabic, b.arabic)
            HomeSort.ENGLISH_NAME -> compareKeys(a.english, b.english)
            HomeSort.SCIENTIFIC_NAME -> compareKeys(a.scientific, b.scientific)
            HomeSort.AVAILABILITY -> stockRanks.getValue(a.medication.id).compareTo(stockRanks.getValue(b.medication.id))
                .takeIf { it != 0 } ?: compareKeys(a.scientific, b.scientific)
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
    private fun matches(entry: Entry, tokens: List<String>) = tokens.all { token -> entry.fields.any { token in it } }

    private fun matchesStock(medicationId: String, section: String?, statuses: Set<AvailabilityStatus>): Boolean =
        statuses.isEmpty() || section != null && catalog.status(medicationId, section) in statuses

    private fun rankMatches(candidates: List<Entry>, normalized: String, tokens: List<String>): Pair<List<Entry>, Map<String, Int>> {
        val scores = candidates.associate { it.medication.id to score(it, normalized, tokens) }
        return candidates.sortedWith(compareBy<Entry> { scores.getValue(it.medication.id) }
            .then(order(HomeSort.SCIENTIFIC_NAME))) to scores
    }

    fun browse(section: String?, query: String, filters: HomeFilters, sort: HomeSort): List<Medication> {
        val normalized = MedicationPresentation.normalizedSearch(query)
        val tokens = MedicationPresentation.searchTokens(normalized)
        val filtered = entries.filter { e ->
            (filters.categoryIds.isEmpty() || e.medication.categoryId in filters.categoryIds) &&
                matchesStock(e.medication.id, section, filters.statuses) && matches(e, tokens)
        }
        if (tokens.isNotEmpty()) return rankMatches(filtered, normalized, tokens).first.map { it.medication }
        val ranks = if (sort == HomeSort.AVAILABILITY) filtered.associate { entry ->
            val id = entry.medication.id
            val rank = when (section?.let { catalog.status(id, it) } ?: AvailabilityStatus.OUT) {
                AvailabilityStatus.OUT -> 0
                AvailabilityStatus.LOW -> 1
                AvailabilityStatus.AVAILABLE -> 2
            }
            id to rank
        } else emptyMap()
        return filtered.sortedWith(order(sort, ranks)).map { it.medication }
    }
    fun groups(medications: List<Medication>, query: String, mode: HomeGrouping): List<MedicationGroup> {
        val ids = medications.map { it.id }.toSet()
        val normalized = MedicationPresentation.normalizedSearch(query)
        val tokens = MedicationPresentation.searchTokens(normalized)
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
        unpublished: Set<String>, fetched: Set<String> = emptySet(),
        today: java.time.LocalDate = MedicationPresentation.today(java.time.Instant.now(), catalog.pharmacy?.timezone ?: "Asia/Damascus"),
        readerStockOnly: Boolean = false): HomeBrowseResult {
        val owner = currentCoroutineContext()
        owner.ensureActive()
        val stocked = setOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW)
        val effectiveFilters = if (readerStockOnly) filters.copy(statuses = stocked) else filters
        val normalized = MedicationPresentation.normalizedSearch(query)
        val tokens = MedicationPresentation.searchTokens(normalized)
        val searching = tokens.isNotEmpty()
        fun extra(med: Medication) = (!filters.expiringSoon || MedicationPresentation.expiry(med, today) == ExpiryState.SOON) &&
            (!filters.unpublishedOnly || med.id in unpublished) && (!filters.fetchedOnly || med.id in fetched)
        val (ordered, scores) = if (searching) rankMatches(entries.filter { entry ->
            owner.ensureActive()
            (filters.categoryIds.isEmpty() || entry.medication.categoryId in filters.categoryIds) &&
                matches(entry, tokens) && extra(entry.medication)
        }, normalized, tokens) else emptyList<Entry>() to emptyMap<String, Int>()
        val visible = if (searching) ordered.asSequence().map { it.medication }
            .filter { matchesStock(it.id, section, effectiveFilters.statuses) }.toList()
        else browse(section, "", effectiveFilters, preferences.sort).filter(::extra)

        val includeOtherSections = query.isNotBlank() && effectiveFilters.statuses.isNotEmpty()
        val here = if (includeOtherSections) visible.mapTo(HashSet()) { it.id } else emptySet()
        val searchStatuses = if (effectiveFilters.statuses.isEmpty()) stocked else effectiveFilters.statuses.intersect(stocked)
        val includeStockMatches = searching && searchStatuses.isNotEmpty()
        val otherSections = linkedMapOf<String, Set<String>>()
        val stockMatches = linkedMapOf<String, List<Medication>>()
        val searchSections = if (includeOtherSections || includeStockMatches) catalog.sections.filter { it.isActive } else emptyList()
        for (candidate in searchSections) {
            owner.ensureActive()
            val includeOther = includeOtherSections && candidate.id != section
            if (!includeOther && !includeStockMatches) continue
            val otherIds = linkedSetOf<String>()
            val stock = mutableListOf<Medication>()
            if (!searching) {
                // A nonblank query that normalizes to empty still has legacy section hints.
                browse(candidate.id, "", effectiveFilters, preferences.sort).filter(::extra)
                    .forEach { if (it.id !in here) otherIds.add(it.id) }
            } else {
                for (entry in ordered) {
                    owner.ensureActive()
                    val med = entry.medication
                    val status = catalog.status(med.id, candidate.id)
                    if (includeOther && status in effectiveFilters.statuses && med.id !in here) otherIds.add(med.id)
                    if (includeStockMatches && status in searchStatuses) stock.add(med)
                }
            }
            if (otherIds.isNotEmpty()) otherSections[candidate.id] = otherIds
            if (stock.isNotEmpty()) stockMatches[candidate.id] = stock
        }
        val availableSearchSections = stockMatches.keys
        val groups = if (preferences.groupingEnabled) grouped(visible, preferences.grouping, scores, searching) else emptyList()
        fun row(med: Medication, sectionId: String?): MedicationRowUi =
            MedicationRowUi(med, catalog.formsById[med.formId], catalog.categoriesById[med.categoryId]?.nameAr ?: "غير مصنف",
                sectionId?.let { catalog.status(med.id, it) } ?: AvailabilityStatus.OUT,
                if (preferences.showOtherSectionAvailability) MedicationPresentation.availableElsewhere(catalog, med.id, sectionId, onlyWhenOut = readerStockOnly) else emptyList(),
                med.id in unpublished, med.id in fetched)
        val rows = visible.associate { med ->
            owner.ensureActive()
            med.id to row(med, section)
        }
        val otherSectionResults = if (section == null) emptyList() else searchSections.filter { it.id != section }.mapNotNull { candidate ->
            val matches = stockMatches[candidate.id] ?: return@mapNotNull null
            SectionSearchResults(candidate, matches.map { med ->
                owner.ensureActive()
                row(med, candidate.id)
            })
        }
        return HomeBrowseResult(catalog, section, query, filters, preferences, visible, groups, rows, fetched, unpublished, today, otherSections, readerStockOnly, availableSearchSections, otherSectionResults)
    }
}
