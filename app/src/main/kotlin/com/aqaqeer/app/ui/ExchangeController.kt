package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import android.net.Uri
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException

internal class ExchangeController(private val csvImports: CsvImportRepository, private val reports: ReportRepository,
    private val catalogState: () -> CatalogState, private val currentRoute: () -> String,
    private val isOnline: () -> Boolean, private val feedback: MutableStateFlow<UiFeedback?>,
    private val markReconnectSafely: suspend () -> Unit,
    private val runCommand: (Boolean, suspend () -> Unit) -> Unit) {
    private fun command(queueOffline: Boolean = true, action: suspend () -> Unit) = runCommand(queueOffline, action)

    private val csvState = MutableStateFlow(CsvImportState())
    internal val csvImport = csvState.asStateFlow()
    private val reportState = MutableStateFlow<ReportSnapshot?>(null)
    internal val reportSnapshot = reportState.asStateFlow()
    private var csvSaveDraft: Pair<CsvSaveKind, CsvImportState>? = null
    private var reportSaveDraft: Pair<ReportSnapshot, ReportFormat>? = null
    private val sourceState = MutableStateFlow<ReportSource?>(null)
    internal val reportSource = sourceState.asStateFlow()
    private val draftState = MutableStateFlow(ReportRequest())
    internal val reportDraft = draftState.asStateFlow()
    private var reportGeneration = 0L
    internal fun invalidateReport() { reportGeneration++; reportState.value = null; reportSaveDraft = null }
    internal fun reviseReport(request: ReportRequest) {
        if (request.includeUnpublished != draftState.value.includeUnpublished) sourceState.value = null
        draftState.value = request
        invalidateReport()
    }
    internal fun loadReportSource() = command(queueOffline = false) {
        val draft = draftState.value
        sourceState.value = null
        invalidateReport()
        val pharmacy = (catalogState() as? CatalogState.Ready)?.catalog?.pharmacy?.pharmacyId ?: return@command
        val source = reports.source(draft.includeUnpublished)
        check(exchangeEligible(pharmacy) && source.pharmacyId == pharmacy)
        if (draftState.value.includeUnpublished == draft.includeUnpublished) sourceState.value = source
    }

    internal fun loadCsv(uri: Uri) = command(queueOffline = false) {
        val session = csvImports.load(uri)
        check(exchangeEligible(session.pharmacyId))
        csvState.value = CsvImportState(session)
    }
    internal fun reviseCsv(number: Int, decision: CsvDecision) = command(queueOffline = false) {
        val current = csvState.value
        val session = current.session ?: return@command
        if (!current.finished) {
            val revised = csvImports.revise(session, number, decision)
            if (csvState.value.session?.id == session.id && exchangeEligible(session.pharmacyId)) csvState.value = current.copy(session = revised)
        }
    }
    internal fun chooseCsvDuplicates(update: Boolean) = command(queueOffline = false) {
        val current = csvState.value; val session = current.session ?: return@command
        if (!current.finished && exchangeEligible(session.pharmacyId)) {
            val revised = csvImports.chooseDuplicates(session, update)
            if (csvState.value.session?.id == session.id) csvState.value = current.copy(session = revised)
        }
    }
    internal fun selectValidCsv() = command(queueOffline = false) {
        val current = csvState.value; val session = current.session ?: return@command
        if (!current.finished && exchangeEligible(session.pharmacyId)) csvState.value = current.copy(session = csvImports.selectValid(session))
    }
    internal fun commitCsv() = command(queueOffline = false) {
        val current = csvState.value; val session = current.session ?: return@command
        if (current.finished) return@command
        try {
            csvImports.commit(session) { results ->
                if (csvState.value.session?.id != session.id) throw CancellationException()
                csvState.update { it.copy(results = it.results + results.associateBy { result -> result.number }) }
                if (!isOnline() && results.any { it.outcome in setOf(CsvOutcome.CREATED, CsvOutcome.UPDATED) }) markReconnectSafely()
            }
        } finally {
            if (csvState.value.session?.id == session.id) csvState.update { latest ->
                val unprocessed = session.rows.filter { it.record.number !in latest.results }.associate { row ->
                    val skipped = row.decision.action == CsvAction.SKIP || (!row.decision.selected && row.error == null)
                    row.record.number to CsvRowResult(row.record.number, if (skipped) CsvOutcome.SKIPPED else CsvOutcome.FAILED,
                        row.warnings.joinToString(uiText(R.string.exchange_csv_warning_separator)), if (skipped) "" else row.error ?: uiText(R.string.exchange_csv_unprocessed_row_error))
                }
                latest.copy(results = latest.results + unprocessed, finished = true)
            }
        }
    }
    internal fun beginCsvSave(kind: CsvSaveKind) { csvSaveDraft = kind to csvState.value }
    internal fun saveCsv(uri: Uri?) {
        val draft = csvSaveDraft; csvSaveDraft = null
        if (uri == null || draft == null) return
        command(queueOffline = false) {
            if (draft.first == CsvSaveKind.TEMPLATE) csvImports.template(uri)
            else csvImports.exportResults(uri, draft.second, draft.first == CsvSaveKind.RETRY)
            if (currentRoute() == "csv-import") feedback.value = UiFeedback(uiText(R.string.exchange_csv_file_saved_message), "csv-import")
        }
    }
    internal fun prepareReport(request: ReportRequest) = command(queueOffline = false) {
        val generation = reportGeneration
        val snapshot = reports.capture(request)
        check(exchangeEligible(snapshot.pharmacyId))
        if (generation == reportGeneration) reportState.value = snapshot
    }
    internal fun beginReportSave(format: ReportFormat) { reportState.value?.let { reportSaveDraft = it to format } }
    internal fun saveReport(uri: Uri?) {
        val draft = reportSaveDraft; reportSaveDraft = null
        if (uri == null || draft == null) return
        command(queueOffline = false) {
            when (draft.second) { ReportFormat.PDF -> reports.pdf(uri, draft.first); ReportFormat.XLSX -> reports.excel(uri, draft.first) }
            if (currentRoute() == "reports") feedback.value = UiFeedback(uiText(R.string.exchange_report_file_saved_message), "reports")
        }
    }
    private fun exchangeEligible(pharmacyId: String): Boolean = (catalogState() as? CatalogState.Ready)?.let {
        it.admin && !it.requiresUpdate && !it.maintenancePaused && it.catalog.pharmacy?.pharmacyId == pharmacyId
    } == true

    fun reconcileAccess(access: Pair<String?, Boolean>?) {
        val pharmacyId = access?.first
        if (access?.second != true || (csvState.value.session?.pharmacyId?.let { it != pharmacyId } == true)) {
            csvState.value = CsvImportState(); csvSaveDraft = null
        }
        if (access?.second != true || (reportState.value?.pharmacyId?.let { it != pharmacyId } == true) ||
            sourceState.value?.pharmacyId?.let { it != pharmacyId } == true ||
            draftState.value.pharmacyId?.let { it != pharmacyId } == true) {
            invalidateReport(); sourceState.value = null
            draftState.value = ReportRequest()
        }
    }
}
