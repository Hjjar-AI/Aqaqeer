package com.aqaqeer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*

@Composable internal fun ReportsScreen(ready: CatalogState.Ready?, vm: CatalogViewModel) {
    val text = rememberUiText()
    if (ready == null || !ready.admin || ready.requiresUpdate || ready.maintenancePaused) {
        FormPage { InformationCard(text(R.string.navigation_reports_title), text(R.string.exchange_report_access_required_message)) }
        return
    }
    val pharmacy = ready.catalog.pharmacy?.pharmacyId ?: return
    val draft by vm.reportDraft.collectAsStateWithLifecycle()
    val storedOptions by vm.reportOptions.collectAsStateWithLifecycle()
    val sourceState by vm.reportSource.collectAsStateWithLifecycle()
    val saved by vm.reportSnapshot.collectAsStateWithLifecycle()
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val options = storedOptions ?: ReportOptions()
    val request = draft.copy(pharmacyId = pharmacy, options = options)
    val source = sourceState?.takeIf { it.pharmacyId == pharmacy && it.includeUnpublished == request.includeUnpublished }
    var attempted by remember(pharmacy, request.includeUnpublished) { mutableStateOf(false) }
    LaunchedEffect(pharmacy, request.includeUnpublished, busy) {
        if (!attempted && !busy && source == null) { attempted = true; vm.loadReportSource() }
    }
    val current = saved?.takeIf { it.pharmacyId == pharmacy && it.request == request }
    var filtersOpen by remember(pharmacy) { mutableStateOf(false) }
    var showAll by remember(current) { mutableStateOf(false) }
    val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), vm::saveReport)
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), vm::saveReport)
    fun change(value: ReportRequest) = vm.reviseReport(value.copy(pharmacyId = pharmacy))
    fun export(format: ReportFormat) {
        vm.beginReportSave(format)
        try { when (format) {
            ReportFormat.PDF -> pdf.launch("aqaqeer-report.pdf")
            ReportFormat.XLSX -> excel.launch("aqaqeer-report.xlsx")
        } } catch (_: Exception) { vm.saveReport(null); vm.adminMessage.value = text(R.string.exchange_report_save_picker_failed_message) }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
            item {
                ContentSection {
                    OperationStatus(busy, message, cancel = vm::cancelAdminOperation)
                    GroupedCard(title = text(R.string.exchange_report_prepare_title)) {
                        ChoicePicker(text(R.string.report_summary_source), listOf(false, true), request.includeUnpublished, {
                            text(if (it) R.string.report_source_local else R.string.report_source_published)
                        }, enabled = !busy, itemKey = { it.toString() }) { include ->
                            change(ReportRequest(includeUnpublished = include, pharmacyId = pharmacy))
                        }
                        SupportingText(text(R.string.report_source_help))
                        ActionGroup {
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                                change(ReportRequest(includeUnpublished = request.includeUnpublished, pharmacyId = pharmacy))
                            }) { Text(text(R.string.report_all_medications)) }
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                                change(ReportRequest(includeUnpublished = request.includeUnpublished, pharmacyId = pharmacy,
                                    statuses = setOf(AvailabilityStatus.OUT), statusMatch = ReportStatusMatch.ANY_SECTION))
                            }) { Text(text(R.string.report_shortages_any)) }
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                                change(ReportRequest(includeUnpublished = request.includeUnpublished, pharmacyId = pharmacy, expiry = ReportExpiry.SOON))
                            }) { Text(text(R.string.report_expiry_soon)) }
                        }
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { vm.loadReportSource() }) {
                            Text(text(R.string.report_reload_source))
                        }
                        source?.let { selectedSource ->
                            val catalog = selectedSource.catalog
                            val sectionChoices = remember(catalog.sections) { catalog.sections.map { ReportChoice(it.id, it.nameAr + if (!it.isActive) text(R.string.catalog_closed_section_suffix) else "") } }
                            val sections = request.sectionIds ?: catalog.sections.filter { it.isActive }.map { it.id }.toSet()
                            ReportMultiPicker(text(R.string.report_sections), sectionChoices, sections, !busy) { change(request.copy(sectionIds = it)) }
                            if (sections.isEmpty() && (ReportField.AVAILABILITY in options.fields || request.statuses.isNotEmpty() || options.sort == ReportSort.AVAILABILITY))
                                Text(text(R.string.report_sections_required), color = MaterialTheme.colorScheme.error)
                            CollapsibleGroup(text(R.string.report_filters), filtersOpen, { filtersOpen = !filtersOpen }) {
                                FormField(text(R.string.shared_search_label), request.query, { change(request.copy(query = it)) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false,
                                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None))
                                val categories = remember(catalog.categories) { catalog.categories.map { ReportChoice(it.id, it.nameAr) } }
                                val forms = remember(catalog.forms) { catalog.forms.map { ReportChoice(it.id, it.nameAr) } }
                                ReportMultiPicker(text(R.string.report_categories), categories, request.categoryIds, !busy, true) { change(request.copy(categoryIds = it)) }
                                ReportMultiPicker(text(R.string.report_forms), forms, request.formIds, !busy, true) { change(request.copy(formIds = it)) }
                                Text(text(R.string.report_availability_filter), style = MaterialTheme.typography.titleSmall)
                                WrappingContent { AvailabilityStatus.entries.forEach { status ->
                                    AppChip(status in request.statuses, enabled = !busy, onClick = {
                                        change(request.copy(statuses = if (status in request.statuses) request.statuses - status else request.statuses + status))
                                    }, label = { Text(statusLabel(status, text = text)) })
                                } }
                                SupportingText(text(R.string.report_empty_filter_all))
                                ChoicePicker(text(R.string.report_status_match), ReportStatusMatch.entries, request.statusMatch,
                                    { text(statusMatchLabel(it)) }, enabled = !busy, itemKey = { it.name }) { change(request.copy(statusMatch = it)) }
                                ChoicePicker(text(R.string.report_expiry_filter), ReportExpiry.entries, request.expiry, { text(expiryLabel(it)) },
                                    enabled = !busy, itemKey = { it.name }) { change(request.copy(expiry = it)) }
                                GregorianDateField(text(R.string.report_date_from), request.fromDate, { change(request.copy(fromDate = it)) })
                                GregorianDateField(text(R.string.report_date_through), request.throughDate, { change(request.copy(throughDate = it)) })
                                CheckChoiceRow(request.medicationIds == null, text(R.string.report_all_medications), enabled = !busy) {
                                    change(request.copy(medicationIds = if (it) null else emptySet()))
                                }
                                if (request.medicationIds != null) {
                                    val medications = remember(catalog.medications) { catalog.medications.map { ReportChoice(it.id, MedicationPresentation.identity(it)) } }
                                    ReportMultiPicker(text(R.string.report_medication_selection), medications, request.medicationIds, !busy) { change(request.copy(medicationIds = it)) }
                                }
                                AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                                    change(ReportRequest(includeUnpublished = request.includeUnpublished, pharmacyId = pharmacy))
                                }) { Text(text(R.string.report_reset_filters)) }
                            }
                        }
                        ReportPresentationControls(options, busy || storedOptions == null, vm::setReportOptions)
                        SupportingText(text(R.string.report_default_fields, options.fields.joinToString("، ") { text(it.label) }))
                        AppButton(enabled = !busy && storedOptions != null && source != null, onClick = { vm.prepareReport(request) }) {
                            Text(text(R.string.exchange_report_prepare_preview_action))
                        }
                    }
                }
            }
            if (current != null) {
                item { GroupedCard(title = text(R.string.exchange_report_preview_title)) {
                    current.summary().forEach { (label, value) -> LabeledValue(label, value) }
                } }
                items(if (showAll) current.medications else current.medications.take(5), key = { it.id }) { med ->
                    GroupedCard {
                        current.columns().forEach { col ->
                            val value = col.value(current, med)
                            if (value.isNotEmpty()) LabeledValue(col.title, value)
                        }
                    }
                }
                if (current.medications.size > 5) item { AppButton(variant = AppButtonVariant.QUIET, onClick = { showAll = !showAll }) {
                    Text(text(if (showAll) R.string.exchange_report_show_first_five_action else R.string.exchange_report_show_all_action))
                } }
            }
        }
        if (current != null) Surface(shadowElevation = UiSpacing.hair) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.related)) {
                Text(text(R.string.exchange_report_save_full_count_format, current.medications.size.toString()), style = MaterialTheme.typography.labelLarge)
                ExportButtons(!busy, listOf(text(R.string.report_save_excel) to { export(ReportFormat.XLSX) },
                    text(R.string.exchange_report_save_pdf_action) to { export(ReportFormat.PDF) }))
            }
        }
    }
}
