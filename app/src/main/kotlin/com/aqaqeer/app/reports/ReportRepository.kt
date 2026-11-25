package com.aqaqeer.app.reports

import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import com.aqaqeer.app.security.files.SnapshotWire
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

class ReportRepository @Inject constructor(private val admin: AdminRepository, private val output: DocumentOutput,
    private val pdf: PdfReports) {
    internal suspend fun source(includeUnpublished: Boolean): ReportSource {
        if (BuildConfig.IS_DEMO) return withContext(Dispatchers.Default) {
            val session = DemoSession.authorized()
            ReportSource(if (includeUnpublished) session.catalog else session.initial,
                includeUnpublished, session.publishedAt, session.workspaceId)
        }
        return admin.transaction { dao ->
            admin.guard(dao)
            val local = dao.catalog()
            val metadata = dao.snapshotMetadata()
            val catalog = if (includeUnpublished) local else {
                val text = dao.snapshotState()?.confirmedPayloadJson ?: throw CsvProblem(uiText(R.string.report_no_published_source))
                Json.decodeFromString<SnapshotWire>(text).supported().catalog
            }
            check(catalog.pharmacy?.pharmacyId == local.pharmacy?.pharmacyId)
            ReportSource(catalog, includeUnpublished, metadata?.publishedAt?.let(Instant::parse))
        }
    }

    internal suspend fun capture(request: ReportRequest): ReportSnapshot {
        val source = source(request.includeUnpublished)
        return withContext(Dispatchers.Default) {
            val catalog = source.catalog
            CatalogRules.validate(catalog)
            if (request.pharmacyId != null && request.pharmacyId != source.pharmacyId)
                throw CsvProblem(uiText(R.string.report_source_changed))
            val pharmacy = requireNotNull(catalog.pharmacy)
            val sections = catalog.sections.filter { section -> when {
                request.sectionIds != null -> section.id in request.sectionIds
                request.preset == ReportPreset.OUT && !request.allSections -> section.id == request.sectionId
                else -> section.isActive
            } }
            fun requireIds(selected: Set<String>, available: Set<String>) {
                if (!available.containsAll(selected)) throw CsvProblem(uiText(R.string.report_source_changed))
            }
            request.sectionIds?.let { requireIds(it, catalog.sections.map { s -> s.id }.toSet()) }
            requireIds(request.categoryIds, catalog.categories.map { it.id }.toSet())
            requireIds(request.formIds, catalog.forms.map { it.id }.toSet())
            request.medicationIds?.let { requireIds(it, catalog.medications.map { med -> med.id }.toSet()) }
            if (ReportField.SCIENTIFIC !in request.options.fields) throw CsvProblem(uiText(R.string.report_scientific_required))
            if (sections.isEmpty() && (ReportField.AVAILABILITY in request.options.fields || request.effectiveStatuses.isNotEmpty() || request.options.sort == ReportSort.AVAILABILITY))
                throw CsvProblem(uiText(R.string.report_sections_required))
            fun date(value: String): LocalDate? = if (value.isBlank()) null else try { LocalDate.parse(value) }
                catch (_: java.time.DateTimeException) { throw CsvProblem(uiText(R.string.report_invalid_dates)) }
            val from = date(request.fromDate); val through = date(request.throughDate)
            if (from != null && through != null && from > through) throw CsvProblem(uiText(R.string.report_invalid_dates))
            val now = Instant.now()
            val today = MedicationPresentation.today(now, pharmacy.timezone)
            val statuses = catalog.availability.associate { (it.medicationId to it.sectionId) to it.status }
            fun values(med: Medication) = sections.map { statuses[med.id to it.id] ?: AvailabilityStatus.OUT }
            fun statusMatches(med: Medication): Boolean {
                if (request.effectiveStatuses.isEmpty()) return true
                val sectionStatuses = values(med)
                return when (request.effectiveMatch) {
                    ReportStatusMatch.OVERALL -> combinedStatus(sectionStatuses) in request.effectiveStatuses
                    ReportStatusMatch.ANY_SECTION -> sectionStatuses.any { it in request.effectiveStatuses }
                    ReportStatusMatch.ALL_SECTIONS -> sectionStatuses.all { it in request.effectiveStatuses }
                }
            }
            val index = CatalogSearchIndex(catalog)
            val selected = index.browse(null, request.query, HomeFilters(categoryIds = request.categoryIds), HomeSort.SCIENTIFIC_NAME)
                .filter { med ->
                    currentCoroutineContext().ensureActive()
                    (request.medicationIds == null || med.id in request.medicationIds) &&
                        (request.formIds.isEmpty() || med.formId in request.formIds) && statusMatches(med) &&
                        (from == null || med.expiryDate?.let { it >= from } == true) &&
                        (through == null || med.expiryDate?.let { it <= through } == true) && when (request.effectiveExpiry) {
                            ReportExpiry.ALL -> true
                            ReportExpiry.UNDATED -> med.expiryDate == null
                            ReportExpiry.EXPIRED -> MedicationPresentation.expiry(med, today) == ExpiryState.EXPIRED
                            ReportExpiry.SOON -> MedicationPresentation.expiry(med, today) == ExpiryState.SOON
                            ReportExpiry.VALID -> med.expiryDate?.let { it >= today } == true
                        }
                }
            val scientificOrder = selected.withIndex().associate { it.value.id to it.index }
            val descending = request.options.sortDirection == ReportSortDirection.DESCENDING
            val primaryOrder = when (request.options.sort) {
                ReportSort.SCIENTIFIC -> compareBy<Medication> { scientificOrder.getValue(it.id) }
                ReportSort.TRADE -> compareBy { med: Medication -> normalizedName(when (request.options.names) {
                    ReportNames.ENGLISH -> med.nameEn ?: med.nameAr ?: MedicationPresentation.scientificName(med)
                    else -> med.nameAr ?: med.nameEn ?: MedicationPresentation.scientificName(med)
                }) }
                ReportSort.EXPIRY -> {
                    val dates = if (descending) reverseOrder<LocalDate>() else naturalOrder<LocalDate>()
                    compareBy<Medication, LocalDate?>(nullsLast(dates)) { it.expiryDate }
                }
                ReportSort.AVAILABILITY -> compareBy { med: Medication -> combinedStatus(values(med)).ordinal }
            }
            val order = (if (descending && request.options.sort != ReportSort.EXPIRY) primaryOrder.reversed()
                else primaryOrder).thenBy { scientificOrder.getValue(it.id) }
            val categories = catalog.categories.sortedWith(compareBy<Category> { it.orderIndex }.thenBy { it.id })
            val categoryOrder = categories.withIndex().associate { it.value.id to it.index }
            val medications = selected.sortedWith(if (request.options.groupByCategory)
                compareBy<Medication> { categoryOrder.getValue(it.categoryId) }.then(order) else order)
            val estimate = medications.sumOf { med -> listOf(med.nameEn, med.nameAr, med.genericAr, med.genericEn,
                med.brand, med.strength, med.note).sumOf { (it?.length ?: 0).toLong() * 2 } }
            val runtime = Runtime.getRuntime()
            if (estimate * 12 + 16L * 1024 * 1024 > runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()))
                throw CsvProblem(uiText(R.string.report_insufficient_memory))
            ReportSnapshot(pharmacy.pharmacyId, pharmacy.name, pharmacy.timezone, now, source.publishedAt, request,
                sections, medications, catalog.availability, categories, catalog.forms, source.demoWorkspaceId)
        }
    }

    private suspend fun authorize(snapshot: ReportSnapshot) {
        if (BuildConfig.IS_DEMO) {
            val session = DemoSession.authorized()
            check(session.catalog.pharmacy?.pharmacyId == snapshot.pharmacyId && session.workspaceId == snapshot.demoWorkspaceId)
        } else admin.transaction { dao ->
            admin.guard(dao); check(dao.pharmacy()?.pharmacyId == snapshot.pharmacyId)
        }
    }
    internal suspend fun excel(uri: Uri, snapshot: ReportSnapshot) {
        authorize(snapshot)
        output.write(uri) { stream -> ExcelReports.write(stream, snapshot, currentCoroutineContext()) }
    }
    internal suspend fun pdf(uri: Uri, snapshot: ReportSnapshot) {
        authorize(snapshot)
        withContext(Dispatchers.Default) {
            val document = pdf.render(snapshot)
            try { output.write(uri) { stream -> currentCoroutineContext().ensureActive(); document.writeTo(stream); stream.flush() } }
            finally { document.close() }
        }
    }
}
