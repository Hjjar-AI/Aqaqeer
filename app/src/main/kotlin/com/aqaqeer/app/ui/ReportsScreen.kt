package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*

@Composable internal fun ReportsScreen(ready: CatalogState.Ready?, vm: CatalogViewModel) {
    val uiText = rememberUiText()
    if (ready == null || !ready.admin || ready.requiresUpdate) {
        FormPage { InformationCard(uiText(R.string.navigation_reports_title), uiText(R.string.exchange_report_access_required_message)) }
        return
    }
    val pharmacyId = ready.catalog.pharmacy?.pharmacyId
    val saved by vm.reportSnapshot.collectAsStateWithLifecycle()
    val snapshot = saved?.takeIf { it.pharmacyId == pharmacyId }
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    var preset by rememberSaveable(pharmacyId) { mutableStateOf(ReportPreset.OUT) }
    var section by rememberSaveable(pharmacyId) { mutableStateOf(ready.catalog.sections.firstOrNull()?.id) }
    var allSections by rememberSaveable(pharmacyId) { mutableStateOf(false) }
    var unpublished by rememberSaveable(pharmacyId) { mutableStateOf(false) }
    var capturedPharmacy by rememberSaveable { mutableStateOf(pharmacyId) }
    LaunchedEffect(pharmacyId) {
        if (capturedPharmacy != pharmacyId) {
            preset = ReportPreset.OUT; section = ready.catalog.sections.firstOrNull()?.id; unpublished = false
            capturedPharmacy = pharmacyId
        }
    }
    val request = ReportRequest(preset, if (preset == ReportPreset.OUT && !allSections) section else null, unpublished, preset == ReportPreset.OUT && allSections)
    val current = snapshot?.takeIf { it.request == request && capturedPharmacy == pharmacyId }
    var showAll by rememberSaveable(current?.generatedAt?.toString(), current?.request.toString()) { mutableStateOf(false) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), vm::saveReport)
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), vm::saveReport)
    fun export(asPdf: Boolean) {
        vm.beginReportSave(asPdf)
        try { if (asPdf) pdf.launch("aqaqeer-report.pdf") else csv.launch("aqaqeer-report.csv") }
        catch (_: Exception) { vm.saveReport(null); vm.adminMessage.value = uiText(R.string.exchange_report_save_picker_failed_message) }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
            item {
                ContentSection {
                    OperationStatus(busy, message, cancel = vm::cancelAdminOperation)
                    GroupedCard(title = uiText(R.string.exchange_report_prepare_title)) {
                        ChoicePicker(uiText(R.string.exchange_report_preset_label), ReportPreset.entries, preset, {
                            when (it) { ReportPreset.OUT -> uiText(R.string.exchange_report_out_in_section_label); ReportPreset.FULL -> uiText(R.string.exchange_report_all_availability_label); ReportPreset.EXPIRING_SOON -> uiText(R.string.exchange_report_expiring_not_expired_label) }
                        }, enabled = !busy, itemKey = { it.name }) { preset = it }
                        if (preset == ReportPreset.OUT) CheckChoiceRow(allSections, uiText(R.string.logic_all_shortages), enabled = !busy) { allSections = it }
                        if (preset == ReportPreset.OUT && !allSections) ChoicePicker(uiText(R.string.exchange_report_section_label), ready.catalog.sections,
                            ready.catalog.sections.find { it.id == section }, { it.nameAr }, enabled = !busy, itemKey = { it.id }) { section = it.id }
                        CheckChoiceRow(unpublished, uiText(R.string.exchange_report_include_drafts_label), enabled = !busy) { unpublished = it }
                        AppButton(enabled = !busy, onClick = { vm.prepareReport(request) }) { Text(uiText(R.string.exchange_report_prepare_preview_action)) }
                        ExpandableInformation(uiText(R.string.exchange_report_source_help_title),
                            uiText(R.string.exchange_report_source_help))
                    }
                }
            }
            if (current != null) {
                item {
                    GroupedCard(title = uiText(R.string.exchange_report_preview_title)) {
                        Text(current.pharmacyName, style = MaterialTheme.typography.titleMedium)
                        Text(uiText(R.string.exchange_report_medication_count_format, MedicationPresentation.displayDigits(current.medications.size.toString())))
                        Text(if (current.request.includeUnpublished) uiText(R.string.exchange_report_drafts_included_label) else uiText(R.string.exchange_report_published_only_label))
                        LabeledValue(uiText(R.string.exchange_report_created_at_label), bidi(MedicationPresentation.timestamp(current.generatedAt, current.timezone)))
                        ExpandableInformation(uiText(R.string.exchange_report_times_heading), uiText(R.string.exchange_report_pharmacy_publication_time_prefix, bidi(current.timezone)) +
                            (current.publishedAt?.let { bidi(MedicationPresentation.timestamp(it, current.timezone)) } ?: uiText(R.string.exchange_report_unpublished_label)))
                    }
                }
                items(if (showAll) current.medications else current.medications.take(5), key = { it.id }) { med ->
                    GroupedCard {
                        MedicationSummary(med, prominent = true, form = current.forms.firstOrNull { it.id == med.formId }?.nameAr)
                        med.expiryDate?.let { LabeledValue(uiText(R.string.exchange_report_expiry_label), bidi(it.toString())) }
                        current.sections.forEach { s ->
                            WrappingContent { Text(s.nameAr); StatusBadge(current.status(med.id, s.id)) }
                        }
                    }
                }
                if (current.medications.size > 5) item {
                    AppButton(variant = AppButtonVariant.QUIET, onClick = { showAll = !showAll }) { Text(if (showAll) uiText(R.string.exchange_report_show_first_five_action) else uiText(R.string.exchange_report_show_all_action)) }
                }
            }
        }
        if (current != null) Surface(shadowElevation = UiSpacing.hair) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.related)) {
                Text(uiText(R.string.exchange_report_save_full_count_format, MedicationPresentation.displayDigits(current.medications.size.toString())),
                    style = MaterialTheme.typography.labelLarge)
                ExportButtons(!busy, listOf(uiText(R.string.exchange_report_save_csv_action) to { export(false) }, uiText(R.string.exchange_report_save_pdf_action) to { export(true) }))
            }
        }
    }
}
