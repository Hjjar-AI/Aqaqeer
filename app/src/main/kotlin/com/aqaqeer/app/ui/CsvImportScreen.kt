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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*

@Composable internal fun CsvImportScreen(ready: CatalogState.Ready?, vm: CatalogViewModel) {
    val uiText = rememberUiText()
    if (ready == null || !ready.admin || ready.isDemo || ready.requiresUpdate) {
        InformationCard(uiText(R.string.navigation_csv_import_title), uiText(R.string.exchange_report_access_required_message))
        return
    }
    val state by vm.csvImport.collectAsStateWithLifecycle()
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val session = state.session?.takeIf { it.pharmacyId == ready.catalog.pharmacy?.pharmacyId }
    var confirm by remember(session?.id) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::loadCsv) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), vm::saveCsv)
    fun export(kind: CsvSaveKind) {
        vm.beginCsvSave(kind)
        try { save.launch(when (kind) { CsvSaveKind.TEMPLATE -> "aqaqeer-template.csv"; CsvSaveKind.RESULTS -> "aqaqeer-import-results.csv"; CsvSaveKind.RETRY -> "aqaqeer-retry.csv" }) }
        catch (_: Exception) { vm.saveCsv(null); vm.adminMessage.value = uiText(R.string.exchange_report_save_picker_failed_message) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        item { StepHeader(listOf(uiText(R.string.exchange_csv_choose_preview_action), uiText(R.string.exchange_csv_session_title), uiText(R.string.exchange_csv_session_finished_help)),
            if (state.finished) 2 else if (session != null) 1 else 0) }
        item {
            GroupedCard(title = uiText(R.string.exchange_csv_import_navigation_label)) {
                Text(uiText(R.string.exchange_csv_import_limits_help))
                InlineOperationStatus(busy, message, cancel = vm::cancelAdminOperation)
                ActionGroup {
                    AppButton(enabled = !busy, onClick = {
                        try { pick.launch(arrayOf("*/*")) } catch (_: Exception) { vm.adminMessage.value = uiText(R.string.maintenance_file_picker_failed_message) }
                    }) { Text(uiText(R.string.exchange_csv_choose_preview_action)) }
                    AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { export(CsvSaveKind.TEMPLATE) }) { Text(uiText(R.string.exchange_csv_save_template_action)) }
                }
                Text(uiText(R.string.exchange_csv_template_section_ids_help))
                ready.catalog.sections.forEach { Text("${it.nameAr}: ${bidi("section:${it.id}")}") }
                Text(uiText(R.string.exchange_csv_save_completion_help))
                Text(uiText(R.string.exchange_csv_optional_clear_help))
            }
        }
        if (session != null) {
            item {
                GroupedCard(title = uiText(R.string.exchange_csv_session_title)) {
                    Text(uiText(R.string.exchange_csv_row_count_format, MedicationPresentation.displayDigits(session.rows.size.toString())))
                    if (!state.finished) {
                        Text(uiText(R.string.exchange_csv_row_selection_help))
                        ActionGroup {
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { vm.chooseCsvDuplicates(true) }) { Text(uiText(R.string.logic_csv_update_matches)) }
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { vm.chooseCsvDuplicates(false) }) { Text(uiText(R.string.logic_csv_skip_duplicates)) }
                        }
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = vm::selectValidCsv) { Text(uiText(R.string.exchange_csv_select_valid_rows_action)) }
                        AppButton(enabled = !busy && session.rows.isNotEmpty(), onClick = { confirm = true }) {
                            Text(if (session.rows.any { it.decision.selected && it.error == null }) uiText(R.string.exchange_csv_review_commit_action) else uiText(R.string.exchange_csv_finish_without_saving_action))
                        }
                    } else {
                        Text(uiText(R.string.exchange_csv_session_finished_help))
                        ExportButtons(!busy, listOf(uiText(R.string.exchange_csv_save_results_action) to { export(CsvSaveKind.RESULTS) }))
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy && state.results.values.any { it.outcome == CsvOutcome.FAILED }, onClick = { export(CsvSaveKind.RETRY) }) { Text(uiText(R.string.exchange_csv_save_retry_action)) }
                        Text(uiText(R.string.exchange_csv_retry_formula_warning))
                    }
                }
            }
            items(session.rows, key = { "${session.id}:${it.record.number}" }) { row ->
                CsvPreviewCard(row, session, state.results[row.record.number], state.finished, busy) { decision ->
                    vm.reviseCsv(row.record.number, decision)
                }
            }
        }
    }
    if (confirm && session != null) AppDialog(onDismissRequest = { confirm = false },
        title = { Text(uiText(R.string.exchange_csv_confirm_import_title)) },
        body = { Text(uiText(R.string.exchange_csv_confirm_import_help_format, MedicationPresentation.displayDigits(session.rows.count { it.decision.selected && it.error == null }.toString()), ready.catalog.pharmacy?.name)) },
        confirmButton = { AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { confirm = false; vm.commitCsv() }) { Text(uiText(R.string.exchange_csv_save_selected_rows_action)) } },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { confirm = false }) { Text(uiText(R.string.availability_bulk_cancel_action)) } })
}

@Composable private fun CsvPreviewCard(row: CsvPreviewRow, session: CsvImportSession, result: CsvRowResult?, locked: Boolean, busy: Boolean,
    change: (CsvDecision) -> Unit) {
    val uiText = rememberUiText()
    var expanded by remember(session.id, row.record.number) { mutableStateOf(false) }
    val decision = row.decision
    GroupedCard {
        Text(uiText(R.string.exchange_csv_row_number_format, MedicationPresentation.displayDigits(row.record.number.toString())))
        row.plan?.let { plan -> MedicationSummary(plan.medication, prominent = true,
            form = plan.form?.nameAr ?: session.baseline.forms.find { it.id == plan.medication.formId }?.nameAr) }
            ?: Text(bidi(row.record.cells.take(2).joinToString(" · ").take(512)))
        row.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        row.warnings.forEach { Text(bidi(it)) }
        result?.let { Text(uiText(R.string.exchange_csv_result_format, csvOutcomeLabel(it.outcome))); if (it.error.isNotEmpty()) Text(it.error, color = MaterialTheme.colorScheme.error) }
        if (!locked) {
            CheckChoiceRow(decision.selected, uiText(R.string.exchange_csv_select_row_description),
                enabled = !busy && row.plan != null && row.error == null &&
                    ((row.plan.category == null && row.plan.form == null) || decision.approveTaxonomy)) {
                change(decision.copy(selected = it))
            }
            if (row.duplicates.size == 1) AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                change(decision.copy(action = CsvAction.UPDATE, targetId = row.duplicates.single(), selected = false))
            }) { Text(uiText(R.string.logic_csv_update_match)) }
            ChoicePicker(uiText(R.string.exchange_csv_decision_label), CsvAction.entries, decision.action, ::csvActionLabel, enabled = !busy, itemKey = { it.name }) { change(decision.copy(action = it, selected = false)) }
            if (decision.action == CsvAction.UPDATE) ChoicePicker(uiText(R.string.exchange_csv_target_medication_label), session.baseline.medications,
                session.baseline.medications.find { it.id == decision.targetId }, { "${MedicationPresentation.identity(it, session.baseline.forms.find { form -> form.id == it.formId }?.nameAr)} · ${bidi(it.id)}" }, enabled = !busy, itemKey = { it.id }) {
                change(decision.copy(targetId = it.id, selected = false))
            }
            if (row.plan?.category != null || row.plan?.form != null) {
                CheckChoiceRow(decision.approveTaxonomy, uiText(R.string.exchange_csv_create_taxonomy_consent_label), enabled = !busy) {
                    change(decision.copy(approveTaxonomy = it, selected = false))
                }
            }
        }
        AppButton(variant = AppButtonVariant.QUIET, onClick = { expanded = !expanded }) { Text(if (expanded) uiText(R.string.exchange_csv_hide_details_action) else uiText(R.string.exchange_csv_values_changes_heading)) }
        if (expanded) {
            session.table.headers.zip(row.record.cells).forEach { (header, value) ->
                LabeledValue(bidi(header), bidi(historyPreview(value).let { it.text.orEmpty() + if (it.shortened) uiText(R.string.exchange_csv_truncated_suffix) else "" }))
            }
            row.duplicates.forEach { id -> Text(uiText(R.string.exchange_csv_existing_match_prefix) + bidi(session.baseline.medications.find { it.id == id }?.let { "${MedicationPresentation.identity(it, session.baseline.forms.find { form -> form.id == it.formId }?.nameAr)} · ${id}" } ?: id)) }
            FieldChangeRows(row.changes, session.baseline)
            if (!locked && decision.action == CsvAction.UPDATE) clearableCsvFields.forEach { field ->
                CheckChoiceRow(field in decision.clear,
                    uiText(R.string.exchange_csv_clear_value_prefix) + fieldLabel(field, text = uiText), enabled = !busy) { clear ->
                    change(decision.copy(clear = if (clear) decision.clear + field else decision.clear - field, selected = false))
                }
            }
        }
    }
}

internal fun csvActionLabel(action: CsvAction): String = when (action) {
    CsvAction.CREATE -> uiText(R.string.exchange_csv_create_action_label); CsvAction.CREATE_ANOTHER -> uiText(R.string.exchange_csv_create_another_action_label); CsvAction.UPDATE -> uiText(R.string.exchange_csv_update_selected_action_label); CsvAction.SKIP -> uiText(R.string.exchange_csv_skip_action_label)
}
private fun csvOutcomeLabel(outcome: CsvOutcome): String = when (outcome) {
    CsvOutcome.CREATED -> uiText(R.string.exchange_report_created_at_label); CsvOutcome.UPDATED -> uiText(R.string.exchange_csv_updated_outcome_label); CsvOutcome.UNCHANGED -> uiText(R.string.availability_bulk_unchanged_label); CsvOutcome.SKIPPED -> uiText(R.string.exchange_csv_skipped_outcome_label); CsvOutcome.FAILED -> uiText(R.string.exchange_csv_failed_outcome_label)
}

@Composable internal fun FieldChangeRows(changes: List<CsvFieldChange>, catalog: CatalogSnapshot) {
    val uiText = rememberUiText()
    changes.forEach { value ->
        val section = value.field.removePrefix("section:").takeIf { value.field.startsWith("section:") }
        val label = if (section == null) fieldLabel(value.field, text = uiText) else uiText(R.string.exchange_csv_availability_prefix) + (catalog.sections.find { it.id == section }?.nameAr ?: section)
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(uiText(R.string.exchange_csv_before_format, bidi(reviewValue(if (section == null) value.field else "status", value.before, catalog, text = uiText))))
        Text(uiText(R.string.exchange_csv_after_format, bidi(reviewValue(if (section == null) value.field else "status", value.after, catalog, text = uiText))))
    }
}
