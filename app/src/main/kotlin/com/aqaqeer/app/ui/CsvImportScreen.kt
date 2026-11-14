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
    if (ready == null || !ready.admin || ready.isDemo || ready.requiresUpdate) {
        InformationCard(uiText(R.string.ui_screen_titles_016), uiText(R.string.ui_reports_screen_001))
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
        catch (_: Exception) { vm.saveCsv(null); vm.adminMessage.value = uiText(R.string.ui_reports_screen_002) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        item {
            GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_046)) {
                Text(uiText(R.string.ui_csv_import_screen_001))
                InlineOperationStatus(busy, message, cancel = vm::cancelAdminOperation)
                ActionGroup {
                    Button(enabled = !busy, onClick = {
                        try { pick.launch(arrayOf("*/*")) } catch (_: Exception) { vm.adminMessage.value = uiText(R.string.ui_maintenance_screen_044) }
                    }) { Text(uiText(R.string.ui_csv_import_screen_002)) }
                    TextButton(enabled = !busy, onClick = { export(CsvSaveKind.TEMPLATE) }) { Text(uiText(R.string.ui_csv_import_screen_003)) }
                }
                Text(uiText(R.string.ui_csv_import_screen_004))
                ready.catalog.sections.forEach { Text("${it.nameAr}: ${bidi("section:${it.id}")}") }
                Text(uiText(R.string.ui_csv_import_screen_005))
                Text(uiText(R.string.ui_csv_import_screen_006))
            }
        }
        if (session != null) {
            item {
                GroupedCard(title = uiText(R.string.ui_csv_import_screen_007)) {
                    Text(uiText(R.string.ui_csv_import_screen_008, MedicationPresentation.easternDigits(session.rows.size.toString())))
                    if (!state.finished) {
                        Text(uiText(R.string.ui_csv_import_screen_009))
                        TextButton(enabled = !busy, onClick = vm::selectValidCsv) { Text(uiText(R.string.ui_csv_import_screen_010)) }
                        Button(enabled = !busy && session.rows.isNotEmpty(), onClick = { confirm = true }) {
                            Text(if (session.rows.any { it.decision.selected && it.error == null }) uiText(R.string.ui_csv_import_screen_011) else uiText(R.string.ui_csv_import_screen_012))
                        }
                    } else {
                        Text(uiText(R.string.ui_csv_import_screen_013))
                        ExportButtons(!busy, listOf(uiText(R.string.ui_csv_import_screen_014) to { export(CsvSaveKind.RESULTS) }))
                        TextButton(enabled = !busy && state.results.values.any { it.outcome == CsvOutcome.FAILED }, onClick = { export(CsvSaveKind.RETRY) }) { Text(uiText(R.string.ui_csv_import_screen_015)) }
                        Text(uiText(R.string.ui_csv_import_screen_016))
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
    if (confirm && session != null) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(uiText(R.string.ui_csv_import_screen_017)) },
        text = { DialogBody { Text(uiText(R.string.ui_csv_import_screen_018, MedicationPresentation.easternDigits(session.rows.count { it.decision.selected && it.error == null }.toString()), ready.catalog.pharmacy?.name)) } },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirm = false; vm.commitCsv() }) { Text(uiText(R.string.ui_csv_import_screen_019)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(uiText(R.string.ui_bulk_medication_editor_012)) } })
}

@Composable private fun CsvPreviewCard(row: CsvPreviewRow, session: CsvImportSession, result: CsvRowResult?, locked: Boolean, busy: Boolean,
    change: (CsvDecision) -> Unit) {
    var expanded by remember(session.id, row.record.number) { mutableStateOf(false) }
    val decision = row.decision
    GroupedCard {
        Text(uiText(R.string.ui_csv_import_screen_020, MedicationPresentation.easternDigits(row.record.number.toString())))
        row.plan?.let { plan -> MedicationSummary(plan.medication, prominent = true,
            form = plan.form?.nameAr ?: session.baseline.forms.find { it.id == plan.medication.formId }?.nameAr) }
            ?: Text(bidi(row.record.cells.take(2).joinToString(" · ").take(512)))
        row.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        row.warnings.forEach { Text(bidi(it)) }
        result?.let { Text(uiText(R.string.ui_csv_import_screen_021, csvOutcomeLabel(it.outcome))); if (it.error.isNotEmpty()) Text(it.error, color = MaterialTheme.colorScheme.error) }
        if (!locked) {
            CheckChoiceRow(decision.selected, uiText(R.string.ui_csv_import_screen_022),
                enabled = !busy && row.plan != null && row.error == null &&
                    ((row.plan.category == null && row.plan.form == null) || decision.approveTaxonomy)) {
                change(decision.copy(selected = it))
            }
            ChoicePicker(uiText(R.string.ui_csv_import_screen_023), CsvAction.entries, decision.action, ::csvActionLabel, enabled = !busy) { change(decision.copy(action = it, selected = false)) }
            if (decision.action == CsvAction.UPDATE) ChoicePicker(uiText(R.string.ui_csv_import_screen_024), session.baseline.medications,
                session.baseline.medications.find { it.id == decision.targetId }, { "${MedicationPresentation.identity(it, session.baseline.forms.find { form -> form.id == it.formId }?.nameAr)} · ${bidi(it.id)}" }, enabled = !busy) {
                change(decision.copy(targetId = it.id, selected = false))
            }
            if (row.plan?.category != null || row.plan?.form != null) {
                CheckChoiceRow(decision.approveTaxonomy, uiText(R.string.ui_csv_import_screen_025), enabled = !busy) {
                    change(decision.copy(approveTaxonomy = it, selected = false))
                }
            }
        }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) uiText(R.string.ui_csv_import_screen_026) else uiText(R.string.ui_csv_import_screen_027)) }
        if (expanded) {
            session.table.headers.zip(row.record.cells).forEach { (header, value) ->
                LabeledValue(bidi(header), bidi(historyPreview(value).let { it.text.orEmpty() + if (it.shortened) uiText(R.string.ui_csv_import_screen_028) else "" }))
            }
            row.duplicates.forEach { id -> Text(uiText(R.string.ui_csv_import_screen_029) + bidi(session.baseline.medications.find { it.id == id }?.let { "${MedicationPresentation.identity(it, session.baseline.forms.find { form -> form.id == it.formId }?.nameAr)} · ${id}" } ?: id)) }
            FieldChangeRows(row.changes, session.baseline)
            if (!locked && decision.action == CsvAction.UPDATE) clearableCsvFields.forEach { field ->
                CheckChoiceRow(field in decision.clear,
                    uiText(R.string.ui_csv_import_screen_030) + fieldLabel(field), enabled = !busy) { clear ->
                    change(decision.copy(clear = if (clear) decision.clear + field else decision.clear - field, selected = false))
                }
            }
        }
    }
}

internal fun csvActionLabel(action: CsvAction): String = when (action) {
    CsvAction.CREATE -> uiText(R.string.ui_csv_import_screen_031); CsvAction.CREATE_ANOTHER -> uiText(R.string.ui_csv_import_screen_032); CsvAction.UPDATE -> uiText(R.string.ui_csv_import_screen_033); CsvAction.SKIP -> uiText(R.string.ui_csv_import_screen_034)
}
private fun csvOutcomeLabel(outcome: CsvOutcome): String = when (outcome) {
    CsvOutcome.CREATED -> uiText(R.string.ui_reports_screen_017); CsvOutcome.UPDATED -> uiText(R.string.ui_csv_import_screen_035); CsvOutcome.UNCHANGED -> uiText(R.string.ui_bulk_medication_editor_001); CsvOutcome.SKIPPED -> uiText(R.string.ui_csv_import_screen_036); CsvOutcome.FAILED -> uiText(R.string.ui_csv_import_screen_037)
}

@Composable internal fun FieldChangeRows(changes: List<CsvFieldChange>, catalog: CatalogSnapshot) {
    changes.forEach { value ->
        val section = value.field.removePrefix("section:").takeIf { value.field.startsWith("section:") }
        val label = if (section == null) fieldLabel(value.field) else uiText(R.string.ui_csv_import_screen_038) + (catalog.sections.find { it.id == section }?.nameAr ?: section)
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(uiText(R.string.ui_csv_import_screen_039, bidi(reviewValue(if (section == null) value.field else "status", value.before, catalog))))
        Text(uiText(R.string.ui_csv_import_screen_040, bidi(reviewValue(if (section == null) value.field else "status", value.after, catalog))))
    }
}
