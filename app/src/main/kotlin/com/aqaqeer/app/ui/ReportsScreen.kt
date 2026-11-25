package com.aqaqeer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*

/** Keep the document callbacks registered while a picker is open, even if Reports leaves composition. */
@Composable internal fun rememberReportDocumentPicker(vm: CatalogViewModel): (ReportFormat) -> Unit {
    val text = rememberUiText()
    val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), vm::saveReport)
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), vm::saveReport)
    return { format ->
        if (vm.beginReportSave(format)) {
            try {
                when (format) {
                    ReportFormat.PDF -> pdf.launch("aqaqeer-report.pdf")
                    ReportFormat.XLSX -> excel.launch("aqaqeer-report.xlsx")
                }
            } catch (_: Exception) {
                vm.saveReport(null)
                vm.adminMessage.value = text(R.string.exchange_report_save_picker_failed_message)
            }
        }
    }
}

@Composable internal fun ReportsScreen(ready: CatalogState.Ready?, vm: CatalogViewModel, export: (ReportFormat) -> Unit) {
    val text = rememberUiText()
    if (ready == null || !ready.admin || ready.requiresUpdate || ready.maintenancePaused) {
        FormPage { InformationCard(text(R.string.navigation_reports_title), text(R.string.exchange_report_access_required_message)) }
        return
    }
    val pharmacy = ready.catalog.pharmacy?.pharmacyId ?: return
    val draft by vm.reportDraft.collectAsStateWithLifecycle()
    val storedOptions by vm.reportOptions.collectAsStateWithLifecycle()
    val writingOptions by vm.reportOptionsWriting.collectAsStateWithLifecycle()
    val sourceState by vm.reportSource.collectAsStateWithLifecycle()
    val saved by vm.reportSnapshot.collectAsStateWithLifecycle()
    val sessionStep by vm.reportStep.collectAsStateWithLifecycle()
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val options = storedOptions ?: ReportOptions()
    val pharmacyDraft = draft.takeIf { it.pharmacyId == pharmacy } ?: ReportRequest(pharmacyId = pharmacy)
    val request = pharmacyDraft.copy(options = options)
    val source = sourceState?.takeIf { it.pharmacyId == pharmacy && it.includeUnpublished == request.includeUnpublished }
    val step = if (draft.pharmacyId == pharmacy) sessionStep else ReportStep.SOURCE
    var attempted by remember(pharmacy, request.includeUnpublished) { mutableStateOf(false) }
    LaunchedEffect(step, pharmacy, request.includeUnpublished, busy) {
        if (step == ReportStep.SOURCE && !attempted && !busy && source == null) {
            attempted = true
            vm.loadReportSource()
        }
    }

    val current = saved?.takeIf { step == ReportStep.PREVIEW && storedOptions != null &&
        !writingOptions && source != null && it.pharmacyId == pharmacy && it.request == request }
    val columns = remember(current) { current?.columns().orEmpty() }
    var filtersOpen by rememberSaveable(pharmacy) { mutableStateOf(false) }
    var showAll by remember(current) { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun change(value: ReportRequest) = vm.reviseReport(value.copy(pharmacyId = pharmacy))
    fun move(forward: Boolean) {
        focus.clearFocus()
        vm.moveReportStep(forward)
    }
    Column(Modifier.fillMaxSize()) {
        key(pharmacy, step) {
            val listState = rememberLazyListState()
            LaunchedEffect(message) {
                if (message != null) listState.scrollToItem(1)
            }
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(UiSpacing.page),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
                item {
                    StepHeader(listOf(
                        text(R.string.report_summary_source),
                        text(R.string.report_scope_step),
                        text(R.string.report_options_step),
                        text(R.string.exchange_report_preview_title),
                    ), step.ordinal)
                }
                if (busy || message != null) item { OperationStatus(busy, message) }

                when (step) {
                    ReportStep.SOURCE -> item {
                        GroupedCard {
                            ChoicePicker(text(R.string.report_summary_source), listOf(false, true), request.includeUnpublished, {
                                text(if (it) R.string.report_source_local else R.string.report_source_published)
                            }, enabled = !busy, itemKey = { it.toString() }) { include ->
                                change(ReportRequest(includeUnpublished = include, pharmacyId = pharmacy))
                            }
                            SupportingText(text(R.string.report_source_help))
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = vm::loadReportSource) {
                                Text(text(R.string.report_reload_source))
                            }
                        }
                    }
                    ReportStep.SCOPE -> if (source != null) item {
                        ReportScopeControls(source.catalog, request, busy, filtersOpen, { filtersOpen = !filtersOpen }, ::change)
                    }
                    ReportStep.OPTIONS -> item {
                        GroupedCard {
                            ReportPresentationControls(options, busy || writingOptions || storedOptions == null, vm::setReportOptions)
                            SupportingText(text(R.string.report_default_fields, options.fields.joinToString("، ") { text(it.label) }))
                        }
                    }
                    ReportStep.PREVIEW -> if (current != null) {
                        item {
                            GroupedCard(title = text(R.string.report_summary_title)) {
                                current.summary().forEach { (label, value) -> LabeledValue(label, value) }
                            }
                        }
                        items(if (showAll) current.medications else current.medications.take(5), key = { it.id }) { med ->
                            GroupedCard {
                                columns.forEach { column ->
                                    val value = column.value(current, med)
                                    if (value.isNotEmpty()) LabeledValue(column.title, value)
                                }
                            }
                        }
                        if (current.medications.size > 5) item {
                            AppButton(variant = AppButtonVariant.QUIET, onClick = { showAll = !showAll }) {
                                Text(text(if (showAll) R.string.exchange_report_show_first_five_action else R.string.exchange_report_show_all_action))
                            }
                        }
                    }
                }
            }
        }
        Surface(shadowElevation = UiSpacing.hair) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.related), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                if (current != null) Text(text(R.string.exchange_report_save_full_count_format, current.medications.size.toString()),
                    style = MaterialTheme.typography.labelLarge)
                if (busy) {
                    AppButton(variant = AppButtonVariant.QUIET, onClick = vm::cancelAdminOperation) {
                        Text(text(R.string.shared_cancel_operation_action))
                    }
                } else ActionGroup {
                    if (step != ReportStep.SOURCE) {
                        AppButton(variant = AppButtonVariant.QUIET, onClick = { move(forward = false) }) {
                            Text(text(R.string.report_previous_step_action))
                        }
                    }
                    when (step) {
                        ReportStep.SOURCE, ReportStep.SCOPE -> {
                            AppButton(enabled = source != null, onClick = { move(forward = true) }) {
                                Text(text(R.string.report_next_step_action))
                            }
                        }
                        ReportStep.OPTIONS -> {
                            AppButton(enabled = !writingOptions && storedOptions != null && source != null,
                                onClick = { vm.prepareReport(request) }) {
                                Text(text(R.string.exchange_report_prepare_preview_action))
                            }
                        }
                        ReportStep.PREVIEW -> {
                            AppButton(enabled = current != null, onClick = { export(ReportFormat.XLSX) }) {
                                Text(text(R.string.report_save_excel))
                            }
                            AppButton(enabled = current != null, onClick = { export(ReportFormat.PDF) }) {
                                Text(text(R.string.exchange_report_save_pdf_action))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportScopeControls(catalog: CatalogSnapshot, request: ReportRequest, busy: Boolean,
    filtersOpen: Boolean, toggleFilters: () -> Unit, change: (ReportRequest) -> Unit) {
    val text = rememberUiText()
    val defaults = ReportRequest(includeUnpublished = request.includeUnpublished, pharmacyId = request.pharmacyId)
    val sectionChoices = remember(catalog.sections, text) {
        catalog.sections.map {
            ReportChoice(it.id, it.nameAr + if (!it.isActive) text(R.string.catalog_closed_section_suffix) else "")
        }
    }
    val sections = request.sectionIds ?: catalog.sections.filter { it.isActive }.map { it.id }.toSet()

    GroupedCard {
        ActionGroup {
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { change(defaults) }) {
                Text(text(R.string.report_all_medications))
            }
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                change(defaults.copy(statuses = setOf(AvailabilityStatus.OUT), statusMatch = ReportStatusMatch.ANY_SECTION))
            }) { Text(text(R.string.report_shortages_any)) }
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { change(defaults.copy(expiry = ReportExpiry.SOON)) }) {
                Text(text(R.string.report_expiry_soon))
            }
        }
        ReportMultiPicker(text(R.string.report_sections), sectionChoices, sections, !busy) { change(request.copy(sectionIds = it)) }
        if (sections.isEmpty() && (ReportField.AVAILABILITY in request.options.fields ||
                request.effectiveStatuses.isNotEmpty() || request.options.sort == ReportSort.AVAILABILITY)) {
            Text(text(R.string.report_sections_required), color = MaterialTheme.colorScheme.error)
        }
        CollapsibleGroup(text(R.string.report_filters), filtersOpen, toggleFilters) {
            FormField(text(R.string.shared_search_label), request.query, { change(request.copy(query = it)) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None))
            val categories = remember(catalog.categories) { catalog.categories.map { ReportChoice(it.id, it.nameAr) } }
            val forms = remember(catalog.forms) { catalog.forms.map { ReportChoice(it.id, it.nameAr) } }
            ReportMultiPicker(text(R.string.report_categories), categories, request.categoryIds, !busy, true) { change(request.copy(categoryIds = it)) }
            ReportMultiPicker(text(R.string.report_forms), forms, request.formIds, !busy, true) { change(request.copy(formIds = it)) }
            Text(text(R.string.report_availability_filter), style = MaterialTheme.typography.titleSmall)
            WrappingContent {
                AvailabilityStatus.entries.forEach { status ->
                    AppChip(status in request.statuses, enabled = !busy, onClick = {
                        val statuses = if (status in request.statuses) request.statuses - status else request.statuses + status
                        change(request.copy(statuses = statuses))
                    }, label = { Text(statusLabel(status, text = text)) })
                }
            }
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
            val selectedMedications = request.medicationIds
            if (selectedMedications != null) {
                val medications = remember(catalog.medications) { catalog.medications.map { ReportChoice(it.id, MedicationPresentation.identity(it)) } }
                ReportMultiPicker(text(R.string.report_medication_selection), medications, selectedMedications, !busy) {
                    change(request.copy(medicationIds = it))
                }
            }
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { change(defaults) }) {
                Text(text(R.string.report_reset_filters))
            }
        }
    }
}
