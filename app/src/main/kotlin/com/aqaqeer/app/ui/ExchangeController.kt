package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import android.net.Uri
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException

internal enum class ReportStep { SOURCE, SCOPE, OPTIONS, PREVIEW }

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
    private var reportSaveDraft: Pair<Long, ReportFormat>? = null
    private val sourceState = MutableStateFlow<ReportSource?>(null)
    internal val reportSource = sourceState.asStateFlow()
    private val draftState = MutableStateFlow(ReportRequest())
    internal val reportDraft = draftState.asStateFlow()
    private val stepState = MutableStateFlow(ReportStep.SOURCE)
    internal val reportStep = stepState.asStateFlow()
    private var reportGeneration = 0L

    internal fun invalidateReport() {
        reportGeneration++
        if (stepState.value == ReportStep.PREVIEW) stepState.value = ReportStep.OPTIONS
        reportState.value = null
    }

    private fun resetReport(pharmacyId: String? = null) {
        stepState.value = ReportStep.SOURCE
        invalidateReport()
        sourceState.value = null
        draftState.value = ReportRequest(pharmacyId = pharmacyId)
    }

    internal fun reviseReport(request: ReportRequest) {
        val pharmacyId = request.pharmacyId ?: return
        if (!exchangeEligible(pharmacyId)) return
        if (draftState.value.pharmacyId != pharmacyId) resetReport(pharmacyId)
        if (request.includeUnpublished != draftState.value.includeUnpublished) {
            stepState.value = ReportStep.SOURCE
            sourceState.value = null
        }
        draftState.value = request
        invalidateReport()
    }

    private fun hasReportSource(): Boolean {
        val draft = draftState.value
        return sourceState.value?.let { it.pharmacyId == draft.pharmacyId && it.includeUnpublished == draft.includeUnpublished } == true
    }

    internal fun moveReportStep(forward: Boolean) {
        val pharmacyId = draftState.value.pharmacyId ?: return
        if (!exchangeEligible(pharmacyId)) return
        stepState.value = when (stepState.value) {
            ReportStep.SOURCE -> if (forward && hasReportSource()) ReportStep.SCOPE else ReportStep.SOURCE
            ReportStep.SCOPE -> if (!forward) ReportStep.SOURCE else if (hasReportSource()) ReportStep.OPTIONS else ReportStep.SCOPE
            ReportStep.OPTIONS -> if (forward) ReportStep.OPTIONS else ReportStep.SCOPE
            ReportStep.PREVIEW -> if (forward) ReportStep.PREVIEW else ReportStep.OPTIONS
        }
    }

    private fun currentReportRequest(options: ReportOptions?): ReportRequest? {
        val draft = draftState.value
        val pharmacyId = draft.pharmacyId ?: return null
        if (options == null || !exchangeEligible(pharmacyId) || !hasReportSource()) return null
        return draft.copy(options = options)
    }

    internal fun loadReportSource() = command(queueOffline = false) {
        val pharmacy = (catalogState() as? CatalogState.Ready)?.catalog?.pharmacy?.pharmacyId ?: return@command
        if (!exchangeEligible(pharmacy)) return@command
        if (draftState.value.pharmacyId != pharmacy) resetReport(pharmacy)
        stepState.value = ReportStep.SOURCE
        val draft = draftState.value
        sourceState.value = null
        invalidateReport()
        val source = reports.source(draft.includeUnpublished)
        check(exchangeEligible(pharmacy) && source.pharmacyId == pharmacy)
        if (draftState.value === draft) sourceState.value = source
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
    internal fun prepareReport(request: ReportRequest, options: () -> ReportOptions?) = command(queueOffline = false) {
        if (stepState.value != ReportStep.OPTIONS || request != currentReportRequest(options())) return@command
        invalidateReport()
        val generation = reportGeneration
        val snapshot = reports.capture(request)
        check(exchangeEligible(snapshot.pharmacyId))
        if (generation == reportGeneration && snapshot.request == currentReportRequest(options())) {
            reportState.value = snapshot
            stepState.value = ReportStep.PREVIEW
        }
    }

    private fun currentPreview(options: ReportOptions?): ReportSnapshot? {
        if (stepState.value != ReportStep.PREVIEW) return null
        return reportState.value?.takeIf { it.request == currentReportRequest(options) }
    }

    internal fun beginReportSave(format: ReportFormat, options: ReportOptions?): Boolean {
        if (reportSaveDraft != null || currentPreview(options) == null) return false
        reportSaveDraft = reportGeneration to format
        return true
    }

    internal fun saveReport(uri: Uri?, options: () -> ReportOptions?) {
        val (generation, format) = reportSaveDraft ?: return
        reportSaveDraft = null
        if (uri == null || generation != reportGeneration) return
        command(queueOffline = false) {
            val preview = currentPreview(options()) ?: return@command
            if (generation != reportGeneration) return@command
            when (format) {
                ReportFormat.PDF -> reports.pdf(uri, preview)
                ReportFormat.XLSX -> reports.excel(uri, preview)
            }
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
        if (access?.second != true || draftState.value.pharmacyId?.let { it != pharmacyId } == true) resetReport()
    }
}
