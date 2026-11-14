package com.aqaqeer.app.reports

import android.net.Uri
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.files.SnapshotWire
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

internal enum class ReportPreset { OUT, FULL, EXPIRING_SOON }
internal data class ReportRequest(val preset: ReportPreset = ReportPreset.OUT, val sectionId: String? = null,
    val includeUnpublished: Boolean = false, val allSections: Boolean = false)
internal data class ReportSnapshot(val pharmacyId: String, val pharmacyName: String, val timezone: String,
    val generatedAt: Instant, val publishedAt: Instant?, val request: ReportRequest,
    val sections: List<Section>, val medications: List<Medication>, val availability: List<Availability>,
    val categories: List<Category>, val forms: List<MedicationForm>) {
    private val statuses = availability.associate { (it.medicationId to it.sectionId) to it.status }
    fun status(medication: String, section: String) = statuses[medication to section] ?: AvailabilityStatus.UNKNOWN
}

class ReportRepository @Inject constructor(private val admin: AdminRepository, private val output: DocumentOutput,
    private val pdf: PdfReports) {
    internal suspend fun capture(request: ReportRequest): ReportSnapshot = admin.transaction { dao ->
        admin.guard(dao)
        val local = dao.catalog()
        val saved = dao.snapshotState()?.confirmedPayloadJson?.let { Json.decodeFromString<SnapshotWire>(it) }
        val catalog = if (request.includeUnpublished) local else saved?.supported()?.catalog
            ?: throw CsvProblem("لا توجد نسخة منشورة؛ اختر تضمين التعديلات غير المنشورة صراحة")
        CatalogRules.validate(catalog)
        val pharmacy = requireNotNull(catalog.pharmacy)
        check(pharmacy.pharmacyId == local.pharmacy?.pharmacyId)
        val generated = Instant.now()
        val section = if (request.preset == ReportPreset.OUT && !request.allSections) catalog.sections.find { it.id == request.sectionId }
            ?: throw CsvProblem("اختر قسماً موجوداً في مصدر التقرير") else null
        val sections = if (section != null) listOf(section) else if (request.preset == ReportPreset.OUT && request.allSections)
            catalog.sections.filter { it.isActive } else catalog.sections
        if (request.preset == ReportPreset.OUT && request.allSections && sections.isEmpty())
            throw CsvProblem("لا توجد أقسام نشطة لإعداد تقرير النواقص")
        val today = MedicationPresentation.today(generated, pharmacy.timezone)
        val medications = MedicationPresentation.browse(catalog, section?.id, "", HomeFilters(), HomeSort.SCIENTIFIC_NAME).filter { med ->
            when (request.preset) {
                ReportPreset.OUT -> sections.any { catalog.status(med.id, it.id) == AvailabilityStatus.OUT }
                ReportPreset.FULL -> true
                ReportPreset.EXPIRING_SOON -> MedicationPresentation.expiry(med, today) == ExpiryState.SOON
            }
        }
        val estimate = medications.sumOf { med -> listOf(med.nameEn, med.nameAr, med.genericAr, med.genericEn, med.brand,
            med.strength, med.note).sumOf { (it?.length ?: 0).toLong() * 2 } }
        val runtime = Runtime.getRuntime()
        if (estimate * 12 + 16L * 1024 * 1024 > runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()))
            throw CsvProblem("لا توجد ذاكرة كافية لإعداد التقرير بأمان")
        ReportSnapshot(pharmacy.pharmacyId, pharmacy.name, pharmacy.timezone, generated, saved?.publishedAt, request,
            sections, medications, catalog.availability, catalog.categories, catalog.forms)
    }
    private suspend fun authorize(snapshot: ReportSnapshot) = admin.transaction { dao ->
        admin.guard(dao); check(dao.pharmacy()?.pharmacyId == snapshot.pharmacyId)
    }
    internal suspend fun csv(uri: Uri, snapshot: ReportSnapshot) {
        authorize(snapshot)
        val jobContext = currentCoroutineContext()
        output.write(uri) { stream ->
            CsvCodec.write(stream, sequence {
                yield(listOf("pharmacy", snapshot.pharmacyName))
                yield(listOf("timezone", snapshot.timezone))
                yield(listOf("generatedAt", snapshot.generatedAt.toString()))
                yield(listOf("publishedAt", snapshot.publishedAt?.toString() ?: "لم يُنشر"))
                yield(listOf("includeUnpublished", snapshot.request.includeUnpublished.toString()))
                yield(listOf("preset", snapshot.request.preset.name))
                if (snapshot.request.preset == ReportPreset.OUT) yield(listOf("allSections", snapshot.request.allSections.toString()))
                if (snapshot.request.preset == ReportPreset.EXPIRING_SOON)
                    yield(listOf("expiryPolicy", "صلاحية قصيرة وفق حد كل دواء؛ يستثني المنتهي"))
                yield(emptyList())
                yield(visibleMedicationColumns + snapshot.sections.map { "${it.nameAr} (section:${it.id})" })
                snapshot.medications.forEach { med ->
                    yield(listOf(med.genericAr.orEmpty(), med.genericEn.orEmpty(), med.nameAr.orEmpty(), med.nameEn.orEmpty(), med.brand.orEmpty(),
                        snapshot.forms.first { it.id == med.formId }.nameAr, med.strength,
                        snapshot.categories.first { it.id == med.categoryId }.nameAr, med.note.orEmpty(),
                        med.expiryDate?.toString().orEmpty(), med.expirySoonThresholdDays.toString()) +
                        snapshot.sections.map { snapshot.status(med.id, it.id).name })
                }
            }, true) { jobContext.ensureActive() }
        }
    }
    internal suspend fun pdf(uri: Uri, snapshot: ReportSnapshot) {
        authorize(snapshot)
        withContext(Dispatchers.Default) {
            val document = pdf.render(snapshot)
            try {
                output.write(uri) { stream -> currentCoroutineContext().ensureActive(); document.writeTo(stream); stream.flush() }
            } finally { document.close() }
        }
    }
}
