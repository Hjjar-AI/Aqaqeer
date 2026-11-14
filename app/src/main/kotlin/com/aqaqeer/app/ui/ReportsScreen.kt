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
    if (ready == null || !ready.admin || ready.isDemo || ready.requiresUpdate) {
        FormPage { InformationCard(uiText(R.string.ui_screen_titles_017), uiText(R.string.ui_reports_screen_001)) }
        return
    }
    val pharmacyId = ready.catalog.pharmacy?.pharmacyId
    val saved by vm.reportSnapshot.collectAsStateWithLifecycle()
    val snapshot = saved?.takeIf { it.pharmacyId == pharmacyId }
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    var preset by rememberSaveable(pharmacyId) { mutableStateOf(ReportPreset.OUT) }
    var section by rememberSaveable(pharmacyId) { mutableStateOf(ready.catalog.sections.firstOrNull()?.id) }
    var unpublished by rememberSaveable(pharmacyId) { mutableStateOf(false) }
    var capturedPharmacy by rememberSaveable { mutableStateOf(pharmacyId) }
    LaunchedEffect(pharmacyId) {
        if (capturedPharmacy != pharmacyId) {
            preset = ReportPreset.OUT; section = ready.catalog.sections.firstOrNull()?.id; unpublished = false
            capturedPharmacy = pharmacyId
        }
    }
    val request = ReportRequest(preset, if (preset == ReportPreset.OUT) section else null, unpublished)
    val current = snapshot?.takeIf { it.request == request && capturedPharmacy == pharmacyId }
    var showAll by rememberSaveable(current?.generatedAt?.toString(), current?.request.toString()) { mutableStateOf(false) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), vm::saveReport)
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), vm::saveReport)
    fun export(asPdf: Boolean) {
        vm.beginReportSave(asPdf)
        try { if (asPdf) pdf.launch("aqaqeer-report.pdf") else csv.launch("aqaqeer-report.csv") }
        catch (_: Exception) { vm.saveReport(null); vm.adminMessage.value = uiText(R.string.ui_reports_screen_002) }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
            item {
                ContentSection {
                    OperationStatus(busy, message, cancel = vm::cancelAdminOperation)
                    GroupedCard(title = uiText(R.string.ui_reports_screen_003)) {
                        ChoicePicker(uiText(R.string.ui_reports_screen_004), ReportPreset.entries, preset, {
                            when (it) { ReportPreset.OUT -> uiText(R.string.ui_reports_screen_005); ReportPreset.FULL -> uiText(R.string.ui_reports_screen_006); ReportPreset.EXPIRING_SOON -> uiText(R.string.ui_reports_screen_007) }
                        }, enabled = !busy) { preset = it }
                        if (preset == ReportPreset.OUT) ChoicePicker(uiText(R.string.ui_reports_screen_008), ready.catalog.sections,
                            ready.catalog.sections.find { it.id == section }, { it.nameAr }, enabled = !busy) { section = it.id }
                        CheckChoiceRow(unpublished, uiText(R.string.ui_reports_screen_009), enabled = !busy) { unpublished = it }
                        Button(enabled = !busy, onClick = { vm.prepareReport(request) }) { Text(uiText(R.string.ui_reports_screen_010)) }
                        ExpandableInformation(uiText(R.string.ui_reports_screen_011),
                            uiText(R.string.ui_reports_screen_012))
                    }
                }
            }
            if (current != null) {
                item {
                    GroupedCard(title = uiText(R.string.ui_reports_screen_013)) {
                        Text(current.pharmacyName, style = MaterialTheme.typography.titleMedium)
                        Text(uiText(R.string.ui_reports_screen_014, MedicationPresentation.easternDigits(current.medications.size.toString())))
                        Text(if (current.request.includeUnpublished) uiText(R.string.ui_reports_screen_015) else uiText(R.string.ui_reports_screen_016))
                        LabeledValue(uiText(R.string.ui_reports_screen_017), bidi(MedicationPresentation.timestamp(current.generatedAt, current.timezone)))
                        ExpandableInformation(uiText(R.string.ui_reports_screen_018), uiText(R.string.ui_reports_screen_019, bidi(current.timezone)) +
                            (current.publishedAt?.let { bidi(MedicationPresentation.timestamp(it, current.timezone)) } ?: uiText(R.string.ui_reports_screen_020)))
                    }
                }
                items(if (showAll) current.medications else current.medications.take(5), key = { it.id }) { med ->
                    GroupedCard {
                        MedicationSummary(med, prominent = true, form = current.forms.firstOrNull { it.id == med.formId }?.nameAr)
                        med.expiryDate?.let { LabeledValue(uiText(R.string.ui_reports_screen_021), bidi(it.toString())) }
                        current.sections.forEach { s ->
                            WrappingContent { Text(s.nameAr); StatusBadge(current.status(med.id, s.id)) }
                        }
                    }
                }
                if (current.medications.size > 5) item {
                    TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) uiText(R.string.ui_reports_screen_022) else uiText(R.string.ui_reports_screen_023)) }
                }
            }
        }
        if (current != null) Surface(shadowElevation = UiSpacing.hair) {
            Column(Modifier.fillMaxWidth().padding(UiSpacing.related)) {
                Text(uiText(R.string.ui_reports_screen_024, MedicationPresentation.easternDigits(current.medications.size.toString())),
                    style = MaterialTheme.typography.labelLarge)
                ExportButtons(!busy, listOf(uiText(R.string.ui_reports_screen_025) to { export(false) }, uiText(R.string.ui_reports_screen_026) to { export(true) }))
            }
        }
    }
}
