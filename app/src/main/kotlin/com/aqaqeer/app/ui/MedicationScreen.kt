package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import kotlinx.coroutines.launch

@Composable internal fun MedicationScreen(ready: CatalogState.Ready, medication: Medication, now: java.time.Instant, showHijri: Boolean,
    pageHeader: (@Composable () -> Unit)?, vm: CatalogViewModel) {
    val uiText = rememberUiText()
    val catalog = ready.catalog
    var editor by rememberSaveable(catalog.pharmacy?.pharmacyId, medication.id, stateSaver = AvailabilitySelectionSaver) { mutableStateOf<AvailabilitySelection?>(null) }
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val canEdit = ready.canEditCatalog
    LaunchedEffect(canEdit, catalog.pharmacy?.pharmacyId) { if (!canEdit || editor?.pharmacyId != catalog.pharmacy?.pharmacyId) editor = null }
    SideEffect { if (editor?.restored == true) editor = editor?.resolve(catalog) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().testTag("medication_detail"), state = listState, contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
        item {
            WrappingContent {
                catalog.sections.forEachIndexed { index, section ->
                    AssistChip(onClick = { scope.launch { listState.animateScrollToItem(index + 1) } }, label = {
                        Text(section.nameAr + " · " + statusLabel(catalog.status(medication.id, section.id), text = uiText))
                    })
                }
            }
        }
        items(catalog.sections, key = { "section_${it.id}" }) { section ->
            var expanded by rememberSaveable(medication.id, section.id) { mutableStateOf(false) }
            val record = catalog.availability(medication.id, section.id)
            val captured = editor?.takeIf { it.section.id == section.id }
            val current = captured != null && !captured.restored && captured.expected[medication.id] == catalog.status(medication.id, section.id)
            GroupedCard {
                Text(section.nameAr + if (section.isActive) "" else uiText(R.string.catalog_closed_section_suffix), fontWeight = FontWeight.Bold)
                EditableStatusPill(catalog.status(medication.id, section.id), onOpen = if (canEdit && section.isActive && !busy) ({
                    editor = AvailabilitySelection(requireNotNull(catalog.pharmacy).pharmacyId, section, listOf(medication),
                        mapOf(medication.id to catalog.status(medication.id, section.id)), false)
                }) else null, expanded = captured != null, dismiss = { if (!busy) editor = null }, enabled = !busy && (captured == null || current),
                    editable = canEdit && section.isActive, saving = busy && captured != null,
                    choose = { status -> if (current && !vm.adminBusy.value) vm.saveAvailability(captured, BulkMedicationChanges(status = status)) { if (editor == captured) editor = null } })
                if (record == null) SupportingText(uiText(R.string.history_update_time_missing_message))
                else {
                    SupportingText(relativeTime(record.updatedAt, now, text = uiText))
                    CollapsibleGroup(uiText(R.string.history_technical_details_title), expanded, { expanded = !expanded }) {
                        SupportingText(bidi(MedicationPresentation.timestamp(record.updatedAt, catalog.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE)))
                    }
                }
                if (captured != null) InlineOperationStatus(busy, message)
            }
        }
        if (pageHeader != null) item { ContentSection { pageHeader() } }
        if (ready.isDemo) item { DemoNotice() }
        item {
            GroupedCard {
                MedicationPresentation.names(medication.genericAr, medication.genericEn, NameDisplay.BOTH).drop(1).forEach { Text(bidi(it.text)) }
                if (medication.nameAr != null || medication.nameEn != null)
                    Text(uiText(R.string.catalog_trade_name_heading) + ": " + bidi(MedicationPresentation.names(medication.nameAr, medication.nameEn, NameDisplay.BOTH).joinToString(" · ") { it.text }))
                LabeledValue(uiText(R.string.catalog_dosage_form_label), catalog.formsById[medication.formId]?.nameAr.orEmpty())
                LabeledValue(uiText(R.string.catalog_category_label), catalog.categoriesById[medication.categoryId]?.nameAr.orEmpty())
                medication.expiryDate?.let { LabeledValue(uiText(R.string.catalog_gregorian_expiry_date_label), bidi(it.toString())); SecondaryHijriDate(it, showHijri) }
                ExpiryBadge(medication, MedicationPresentation.today(now, catalog.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE))
                medication.brand?.let { LabeledValue(uiText(R.string.catalog_brand_label), bidi(it)) }
            }
        }
        medication.note?.let { note -> item { InformationCard(uiText(R.string.catalog_notes_heading), note) } }
    }
}
