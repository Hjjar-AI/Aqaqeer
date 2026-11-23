package com.aqaqeer.app.reports

import com.aqaqeer.app.R
import com.aqaqeer.app.uiText
import com.aqaqeer.app.data.*
import kotlinx.serialization.Serializable
import java.time.Instant

internal enum class ReportPreset { OUT, FULL, EXPIRING_SOON }
internal enum class ReportFormat { PDF, XLSX }
@Serializable internal enum class ReportField(val label: Int) {
    SCIENTIFIC(R.string.report_field_scientific), STRENGTH(R.string.report_field_strength),
    FORM(R.string.report_field_form), AVAILABILITY(R.string.report_field_availability),
    TRADE(R.string.report_field_trade), CATEGORY(R.string.report_field_category),
    BRAND(R.string.report_field_brand), EXPIRY(R.string.report_field_expiry),
    THRESHOLD(R.string.report_field_threshold), NOTE(R.string.report_field_note)
}
@Serializable internal enum class ReportNames { ARABIC, ENGLISH, BOTH }
@Serializable internal enum class ReportSort { SCIENTIFIC, TRADE, EXPIRY, AVAILABILITY }
@Serializable internal enum class ReportAvailability { OVERALL, PER_SECTION }
@Serializable internal enum class ReportPaper { A5, A4 }
internal enum class ReportStatusMatch { OVERALL, ANY_SECTION, ALL_SECTIONS }
internal enum class ReportExpiry { ALL, UNDATED, EXPIRED, SOON, VALID }

/** Installation-local presentation defaults only; never pharmacy IDs, filters or source consent. */
@Serializable internal data class ReportOptions(
    val fields: Set<ReportField> = setOf(ReportField.SCIENTIFIC, ReportField.STRENGTH, ReportField.FORM, ReportField.AVAILABILITY),
    val names: ReportNames = ReportNames.BOTH, val sort: ReportSort = ReportSort.SCIENTIFIC,
    val groupByCategory: Boolean = true, val availability: ReportAvailability = ReportAvailability.OVERALL,
    val paper: ReportPaper = ReportPaper.A5, val landscape: Boolean = false, val compact: Boolean = true,
)

internal data class ReportRequest(val preset: ReportPreset = ReportPreset.FULL, val sectionId: String? = null,
    val includeUnpublished: Boolean = false, val allSections: Boolean = false,
    val pharmacyId: String? = null, val query: String = "", val categoryIds: Set<String> = emptySet(),
    val formIds: Set<String> = emptySet(), val sectionIds: Set<String>? = null,
    val medicationIds: Set<String>? = null, val statuses: Set<AvailabilityStatus> = emptySet(),
    val statusMatch: ReportStatusMatch = ReportStatusMatch.OVERALL, val expiry: ReportExpiry = ReportExpiry.ALL,
    val fromDate: String = "", val throughDate: String = "", val options: ReportOptions = ReportOptions(),
) {
    val effectiveStatuses get() = statuses.ifEmpty { if (preset == ReportPreset.OUT) setOf(AvailabilityStatus.OUT) else emptySet() }
    val effectiveMatch get() = if (preset == ReportPreset.OUT) ReportStatusMatch.ANY_SECTION else statusMatch
    val effectiveExpiry get() = if (preset == ReportPreset.EXPIRING_SOON) ReportExpiry.SOON else expiry
}

internal data class ReportSource(val catalog: CatalogSnapshot, val includeUnpublished: Boolean,
    val publishedAt: Instant?, val demoWorkspaceId: String? = null) {
    val pharmacyId get() = requireNotNull(catalog.pharmacy).pharmacyId
}

internal data class ReportSnapshot(val pharmacyId: String, val pharmacyName: String, val timezone: String,
    val generatedAt: Instant, val publishedAt: Instant?, val request: ReportRequest,
    val sections: List<Section>, val medications: List<Medication>, val availability: List<Availability>,
    val categories: List<Category>, val forms: List<MedicationForm>, val demoWorkspaceId: String? = null) {
    private val statuses = availability.associate { (it.medicationId to it.sectionId) to it.status }
    val formNames = forms.associate { it.id to it.nameAr }
    val categoryNames = categories.associate { it.id to it.nameAr }
    fun status(medication: String, section: String) = statuses[medication to section] ?: AvailabilityStatus.OUT
    fun overall(medication: String): AvailabilityStatus = combinedStatus(sections.map { status(medication, it.id) })
}

internal fun combinedStatus(values: List<AvailabilityStatus>): AvailabilityStatus = when {
    AvailabilityStatus.AVAILABLE in values -> AvailabilityStatus.AVAILABLE
    AvailabilityStatus.LOW in values -> AvailabilityStatus.LOW
    else -> AvailabilityStatus.OUT
}

internal data class ReportColumn(val field: ReportField, val section: Section? = null) {
    val title get() = section?.let { it.nameAr + if (!it.isActive) uiText(R.string.catalog_closed_section_suffix) else "" }
        ?: uiText(if (field == ReportField.AVAILABILITY) R.string.report_overall_heading else field.label)
    val core get() = field in setOf(ReportField.SCIENTIFIC, ReportField.STRENGTH, ReportField.FORM, ReportField.AVAILABILITY)
    fun status(snapshot: ReportSnapshot, med: Medication): AvailabilityStatus? = if (field != ReportField.AVAILABILITY) null
        else section?.let { snapshot.status(med.id, it.id) } ?: snapshot.overall(med.id)
    fun value(snapshot: ReportSnapshot, med: Medication): String {
        fun names(ar: String?, en: String?): String = when (snapshot.request.options.names) {
            ReportNames.ARABIC -> ar ?: en.orEmpty()
            ReportNames.ENGLISH -> en ?: ar.orEmpty()
            ReportNames.BOTH -> listOfNotNull(ar, en).distinct().joinToString("\n")
        }
        return when (field) {
            ReportField.SCIENTIFIC -> names(med.genericAr, med.genericEn)
            ReportField.TRADE -> names(med.nameAr, med.nameEn)
            ReportField.STRENGTH -> med.strength
            ReportField.FORM -> snapshot.formNames.getValue(med.formId)
            ReportField.CATEGORY -> snapshot.categoryNames.getValue(med.categoryId)
            ReportField.BRAND -> med.brand.orEmpty()
            ReportField.EXPIRY -> med.expiryDate?.toString().orEmpty()
            ReportField.THRESHOLD -> med.expirySoonThresholdDays.toString()
            ReportField.NOTE -> med.note.orEmpty()
            ReportField.AVAILABILITY -> MedicationPresentation.statusLabel(requireNotNull(status(snapshot, med)))
        }
    }
}

internal fun ReportSnapshot.columns(): List<ReportColumn> = ReportField.entries.filter { it in request.options.fields }.flatMap { field ->
    if (field == ReportField.AVAILABILITY && request.options.availability == ReportAvailability.PER_SECTION)
        sections.map { ReportColumn(field, it) } else listOf(ReportColumn(field))
}

/** The summary describes actual criteria, not just the shortcut used to populate them. */
internal fun ReportSnapshot.summary(): List<Pair<String, String>> = buildList {
    add(uiText(R.string.report_summary_pharmacy) to pharmacyName)
    add(uiText(R.string.report_summary_source) to uiText(if (request.includeUnpublished) R.string.report_source_local else R.string.report_source_published))
    add(uiText(R.string.home_pharmacy_timezone_label) to timezone)
    add(uiText(R.string.report_summary_generated) to MedicationPresentation.timestamp(generatedAt, timezone))
    add(uiText(R.string.home_publication_time_label) to (publishedAt?.let { MedicationPresentation.timestamp(it, timezone) } ?: uiText(R.string.exchange_report_unpublished_label)))
    add(uiText(R.string.report_summary_count) to medications.size.toString())
    add(uiText(R.string.report_sections) to sections.joinToString("، ") { it.nameAr + if (!it.isActive) uiText(R.string.catalog_closed_section_suffix) else "" })
    add(uiText(R.string.report_fields) to columns().joinToString("، ") { it.title })
    add(uiText(R.string.report_name_language) to uiText(when (request.options.names) {
        ReportNames.ARABIC -> R.string.report_names_arabic
        ReportNames.ENGLISH -> R.string.report_names_english
        ReportNames.BOTH -> R.string.report_names_both
    }))
    if (ReportField.AVAILABILITY in request.options.fields && request.options.availability == ReportAvailability.OVERALL)
        add(uiText(R.string.report_overall_heading) to uiText(R.string.report_combined_rule))
    if (request.query.isNotBlank()) add(uiText(R.string.shared_search_label) to request.query)
    if (request.categoryIds.isNotEmpty()) add(uiText(R.string.report_categories) to categories.filter { it.id in request.categoryIds }.joinToString("، ") { it.nameAr })
    if (request.formIds.isNotEmpty()) add(uiText(R.string.report_forms) to forms.filter { it.id in request.formIds }.joinToString("، ") { it.nameAr })
    add(uiText(R.string.report_medication_selection) to (request.medicationIds?.size?.toString() ?: uiText(R.string.report_all_medications)))
    if (request.effectiveStatuses.isNotEmpty()) add(uiText(statusMatchLabel(request.effectiveMatch)) to request.effectiveStatuses.joinToString("، ") { MedicationPresentation.statusLabel(it) })
    add(uiText(R.string.report_expiry_filter) to uiText(expiryLabel(request.effectiveExpiry)))
    if (request.fromDate.isNotEmpty() || request.throughDate.isNotEmpty()) add(uiText(R.string.report_expiry_range) to "${request.fromDate} — ${request.throughDate}")
    add(uiText(R.string.report_sort) to uiText(sortLabel(request.options.sort)))
    add(uiText(R.string.report_grouping) to uiText(if (request.options.groupByCategory) R.string.report_group_category else R.string.report_group_none))
}

internal fun statusMatchLabel(value: ReportStatusMatch) = when (value) {
    ReportStatusMatch.OVERALL -> R.string.report_match_overall
    ReportStatusMatch.ANY_SECTION -> R.string.report_match_any
    ReportStatusMatch.ALL_SECTIONS -> R.string.report_match_all
}
internal fun expiryLabel(value: ReportExpiry) = when (value) {
    ReportExpiry.ALL -> R.string.report_expiry_all
    ReportExpiry.UNDATED -> R.string.report_expiry_undated
    ReportExpiry.EXPIRED -> R.string.report_expiry_expired
    ReportExpiry.SOON -> R.string.report_expiry_soon
    ReportExpiry.VALID -> R.string.report_expiry_valid
}
internal fun sortLabel(value: ReportSort) = when (value) {
    ReportSort.SCIENTIFIC -> R.string.report_sort_scientific
    ReportSort.TRADE -> R.string.report_sort_trade
    ReportSort.EXPIRY -> R.string.report_sort_expiry
    ReportSort.AVAILABILITY -> R.string.report_sort_availability
}
