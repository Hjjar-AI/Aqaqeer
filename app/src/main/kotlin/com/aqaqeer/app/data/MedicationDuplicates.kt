package com.aqaqeer.app.data

import java.util.Locale

/** Comparison never infers equivalence between units or changes names/IDs. */
object MedicationDuplicates {
    fun strength(value: String): String = MedicationPresentation.westernDigits(value)
        .trim().replace(Regex("[\\s\\p{Z}]+"), " ")
    private fun strengthKey(value: String) = strength(value).filterNot { it.isWhitespace() }
        .lowercase(Locale.ROOT)
    fun names(med: Medication): Set<String> = listOfNotNull(med.genericAr, med.genericEn, med.nameAr, med.nameEn)
        .map(::normalizedName).filter(String::isNotEmpty).toSet()
    data class Key(val name: String, val strength: String, val formId: String)
    fun keys(med: Medication): Set<Key> = names(med).map { Key(it, strengthKey(med.strength), med.formId) }.toSet()
    fun matches(a: Medication, b: Medication): Boolean = keys(a).any { it in keys(b) }
    fun index(medications: List<Medication>): Map<Key, List<String>> = buildMap<Key, MutableList<String>> {
        medications.forEach { med -> keys(med).forEach { getOrPut(it) { mutableListOf() }.add(med.id) } }
    }
    fun find(med: Medication, index: Map<Key, List<String>>): List<String> =
        keys(med).flatMap { index[it].orEmpty() }.distinct().filterNot { it == med.id }
}
