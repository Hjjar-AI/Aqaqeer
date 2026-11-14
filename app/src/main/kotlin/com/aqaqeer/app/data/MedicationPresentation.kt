package com.aqaqeer.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class HomeSort { SCIENTIFIC_NAME, ARABIC_NAME, ENGLISH_NAME, AVAILABILITY }
enum class CardLayout { COMPACT, EXPANDED }
enum class NameDisplay { ARABIC, ENGLISH, BOTH }
enum class HomeGrouping { SCIENTIFIC_LETTER, CATEGORY }
data class HomePreferences(val sort: HomeSort = HomeSort.SCIENTIFIC_NAME,
    val layout: CardLayout = CardLayout.COMPACT, val names: NameDisplay = NameDisplay.BOTH,
    val secondaryHijri: Boolean = false, val groupingEnabled: Boolean = true,
    val grouping: HomeGrouping = HomeGrouping.CATEGORY, val twoColumns: Boolean = true)
data class HomeFilters(val statuses: Set<AvailabilityStatus> = emptySet(), val categoryIds: Set<String> = emptySet())
data class MedicationGroup(val key: String, val label: String, val medications: List<Medication>)
enum class ExpiryState { EXPIRED, SOON }
data class DisplayName(val text: String, val english: Boolean)

object MedicationPresentation {
    /** UNKNOWN remains a legacy/missing-data state, not an editable status. */
    val editableStatuses = AvailabilityStatus.entries.filterNot { it == AvailabilityStatus.UNKNOWN }

    /** Search may ignore hamza without broadening taxonomy uniqueness or changing sort keys. */
    fun normalizedSearch(value: String): String = normalizedName(westernDigits(value).replace("ء", ""))

    fun statusLabel(status: AvailabilityStatus): String = when (status) {
        AvailabilityStatus.AVAILABLE -> "متوفر"; AvailabilityStatus.LOW -> "توفر محدود"
        AvailabilityStatus.OUT -> "غير متوفر"; AvailabilityStatus.UNKNOWN -> "غير معروف"
    }

    fun names(ar: String?, en: String?, mode: NameDisplay): List<DisplayName> {
        val arabic = ar?.takeIf { it.isNotBlank() }?.let { DisplayName(it, false) }
        val english = en?.takeIf { it.isNotBlank() }?.let { DisplayName(it, true) }
        return when (mode) {
            NameDisplay.ARABIC -> listOfNotNull(arabic ?: english)
            NameDisplay.ENGLISH -> listOfNotNull(english ?: arabic)
            NameDisplay.BOTH -> listOfNotNull(arabic, english)
        }
    }

    fun scientificName(med: Medication): String = med.genericAr ?: med.genericEn ?: med.nameAr ?: med.nameEn.orEmpty()
    fun identity(med: Medication, form: String? = null): String =
        listOfNotNull(scientificName(med), westernDigits(med.strength), form).joinToString(" · ")

    /** Historical records may predate the scientific-name requirement. Never rewrite their payloads. */
    fun recordIdentity(record: kotlinx.serialization.json.JsonObject, forms: List<MedicationForm> = emptyList()): String? {
        fun value(key: String) = (record[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content?.takeIf { it.isNotBlank() }
        val name = listOf("genericAr", "genericEn", "nameAr", "nameEn", "name").firstNotNullOfOrNull(::value) ?: return null
        return listOfNotNull(name, value("strength")?.let(::westernDigits),
            value("formId")?.let { id -> forms.find { it.id == id }?.nameAr }).joinToString(" · ")
    }

    fun letter(med: Medication): String = normalizedName(scientificName(med)).firstOrNull()
        ?.let { if (it.isLetter()) it.toString().uppercase(Locale.ROOT) else "#" } ?: "#"

    private val combiningMarks = setOf(Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(), Character.ENCLOSING_MARK.toInt())

    /** Map normalized search offsets back to original UTF-16 ranges, including combining marks. */
    fun matchRanges(text: String, query: String): List<IntRange> {
        val tokens = normalizedSearch(query).split(' ').filter(String::isNotEmpty)
        if (tokens.isEmpty()) return emptyList()
        val normalized = StringBuilder()
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        var offset = 0
        while (offset < text.length) {
            val start = offset
            offset += Character.charCount(text.codePointAt(offset))
            while (offset < text.length && Character.getType(text.codePointAt(offset)) in
                combiningMarks)
                offset += Character.charCount(text.codePointAt(offset))
            val cluster = text.substring(start, offset)
            val part = if (cluster.first().isWhitespace()) " " else normalizedSearch(cluster)
            if (part.isEmpty() && ends.isNotEmpty()) ends[ends.lastIndex] = offset
            part.forEach { char ->
                if (char == ' ' && normalized.lastOrNull() == ' ') ends[ends.lastIndex] = offset
                else { normalized.append(char); starts += start; ends += offset }
            }
        }
        return tokens.flatMap { token ->
            buildList {
                var from = 0
                while (from <= normalized.length - token.length) {
                    val found = normalized.indexOf(token, from)
                    if (found < 0) break
                    add(starts[found] until ends[found + token.length - 1]); from = found + token.length
                }
            }
        }
    }

    fun browse(catalog: CatalogSnapshot, sectionId: String?, query: String, filters: HomeFilters,
        sort: HomeSort): List<Medication> = CatalogSearchIndex(catalog).browse(sectionId, query, filters, sort)
    fun groups(medications: List<Medication>, catalog: CatalogSnapshot, query: String,
        mode: HomeGrouping): List<MedicationGroup> = CatalogSearchIndex(catalog).groups(medications, query, mode)

    fun today(now: Instant, timezone: String): LocalDate = now.atZone(ZoneId.of(timezone)).toLocalDate()
    fun expiry(med: Medication, today: LocalDate): ExpiryState? {
        val date = med.expiryDate ?: return null
        val days = ChronoUnit.DAYS.between(today, date)
        return when { days < 0 -> ExpiryState.EXPIRED; days <= med.expirySoonThresholdDays.toLong() -> ExpiryState.SOON; else -> null }
    }
    fun availableElsewhere(catalog: CatalogSnapshot, medId: String, currentSectionId: String?): List<Section> =
        if (currentSectionId == null || catalog.status(medId, currentSectionId) != AvailabilityStatus.OUT) emptyList()
        else catalog.sections.filter { it.isActive && it.id != currentSectionId && catalog.status(medId, it.id) == AvailabilityStatus.AVAILABLE }

    fun easternDigits(value: String): String = value.map { if (it in '0'..'9') ('٠'.code + (it - '0')).toChar() else it }.joinToString("")
    fun westernDigits(value: String): String = value.map { when (it) {
        in '٠'..'٩' -> ('0'.code + (it - '٠')).toChar()
        in '۰'..'۹' -> ('0'.code + (it - '۰')).toChar()
        else -> it
    } }.joinToString("")
    fun timestamp(value: Instant, timezone: String): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        .withZone(ZoneId.of(timezone)).format(value)
}
