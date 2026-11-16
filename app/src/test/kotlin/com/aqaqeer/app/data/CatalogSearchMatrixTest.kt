package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CatalogSearchMatrixTest(private val label: String, private val query: String,
    private val statuses: Set<AvailabilityStatus>, private val categories: Set<String>, private val sort: HomeSort) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            for (query in listOf("", "ALPHA", "٥٠٠", "alpha brand", "missing"))
                for (statuses in listOf(emptySet(), setOf(AvailabilityStatus.OUT), setOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW)))
                    for (categories in listOf(emptySet(), setOf(CatalogSeeds.UNCATEGORIZED_ID)))
                        for (sort in HomeSort.entries)
                            add(arrayOf("$query/$statuses/$categories/$sort", query, statuses, categories, sort))
        }
    }
    @Test fun filtersSearchAndProjectionAgreeWithoutLosingHiddenRows(): Unit = runBlocking {
        val seed = F.catalog(); val section = seed.sections.single().id
        val a = seed.medications.single().copy(genericEn = "Alpha", nameEn = "Alpha commercial", strength = "500 mg", brand = "Brand")
        val b = a.copy(id = F.otherPharmacyId, genericEn = "Beta", nameEn = "Beta commercial", strength = "10 mg", categoryId = "other", brand = null)
        val c = a.copy(id = F.installationId, genericEn = "Alpha extra", nameEn = null, categoryId = "other")
        val catalog = seed.copy(medications = listOf(c, b, a), categories = seed.categories + Category("other", "آخر"),
            availability = listOf(Availability(a.id, section, AvailabilityStatus.OUT, F.time),
                Availability(b.id, section, AvailabilityStatus.AVAILABLE, F.time)))
        val matchingQuery = when (query) { "" -> setOf(a.id, b.id, c.id); "ALPHA", "٥٠٠", "alpha brand" -> setOf(a.id, c.id); else -> emptySet() }
        val expected = matchingQuery.filter { id ->
            (categories.isEmpty() || id == a.id) && (statuses.isEmpty() || (if (id == a.id) AvailabilityStatus.OUT else if (id == b.id) AvailabilityStatus.AVAILABLE else AvailabilityStatus.UNKNOWN) in statuses)
        }.toSet()
        val index = CatalogSearchIndex(catalog)
        val visible = index.browse(section, query, HomeFilters(statuses, categories), sort)
        assertEquals(label, expected, visible.map { it.id }.toSet())
        assertEquals(visible, CatalogSearchIndex(catalog.copy(medications = catalog.medications.reversed())).browse(section, query, HomeFilters(statuses, categories), sort))
        val projected = index.project(section, query, HomeFilters(statuses, categories), HomePreferences(sort = sort), setOf(a.id))
        assertEquals(visible, projected.visible); assertEquals(expected, projected.rows.keys)
        assertEquals(expected, projected.groups.flatMap { it.medications }.map { it.id }.toSet())
        projected.rows.values.forEach { assertEquals(it.medication.id == a.id, it.unpublished) }
        assertEquals(3, catalog.medications.size)
    }
}
