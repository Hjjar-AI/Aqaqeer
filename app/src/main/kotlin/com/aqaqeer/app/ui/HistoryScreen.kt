package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HistoryScreen(ready: CatalogState.Ready?, medicationId: String?, vm: CatalogViewModel, showHijri: Boolean = false) {
    val uiText = rememberUiText()
    if (ready == null || !ready.admin) {
        FormPage { InformationCard(uiText(R.string.ui_screen_titles_015), uiText(R.string.ui_history_screen_001)) }
        return
    }
    val observed by vm.history.collectAsStateWithLifecycle()
    val state = observed.takeIf { it.pharmacyId == ready.catalog.pharmacy?.pharmacyId && it.medicationScope == medicationId }
        ?: HistoryUiState(request = HistoryRequest(HistoryFilter(medicationId = medicationId)), loading = true)
    val page = state.page
    val medicationNames = remember(page?.medications) { page?.medications.orEmpty().associate { it.id to it.name } }
    val listState = rememberLazyListState()
    var filtersOpen by rememberSaveable(pharmacyKey(ready), medicationId) { mutableStateOf(false) }
    LaunchedEffect(state.request.page, state.request.filter) { listState.scrollToItem(0) }
    if (filtersOpen) ModalBottomSheet(onDismissRequest = { filtersOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DialogBody(Modifier.padding(UiSpacing.page)) {
            HistoryFilters(state.request.filter, medicationId, page?.medications.orEmpty(), ready.catalog.pharmacy?.timezone ?: "Asia/Damascus") {
                vm.filterHistory(it); filtersOpen = false
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        item {
            ActionGroup {
                val activeFilter = state.request.filter.let { it.from != null || it.through != null || it.entityType != null || (medicationId == null && it.medicationId != null) }
                AppButton(variant = AppButtonVariant.QUIET, onClick = { filtersOpen = true }, colors = ButtonDefaults.textButtonColors(
                    containerColor = if (activeFilter) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)) {
                    AppIcon(UiIcon.FILTER); Text(uiText(R.string.ui_history_screen_002) + if (activeFilter) " ●" else "")
                }
                Text(uiText(R.string.ui_history_screen_003, MedicationPresentation.displayDigits((state.request.page + 1).toString())))
            }
            ExpandableInformation(uiText(R.string.ui_history_screen_004), uiText(R.string.ui_history_screen_005))
            InlineOperationStatus(state.loading, state.error)
            if (state.error != null) AppButton(variant = AppButtonVariant.QUIET, onClick = vm::retryHistory) { Text(uiText(R.string.ui_history_screen_006)) }
        }
        if (!state.loading && state.error == null && page?.entries?.isEmpty() == true) item {
            EmptyState(uiText(R.string.ui_history_screen_007), uiText(R.string.ui_history_screen_008))
        }
        val zone = java.time.ZoneId.of(page?.timezone ?: ready.catalog.pharmacy?.timezone ?: "Asia/Damascus")
        val today = java.time.Instant.now().atZone(zone).toLocalDate()
        page?.entries.orEmpty().groupBy { (it.editedAt ?: it.observedAt)?.atZone(zone)?.toLocalDate() }.forEach { (day, entries) ->
            item(key = "day_$day") { SectionHeading(when (day) {
                today -> uiText(R.string.review_today)
                today.minusDays(1) -> uiText(R.string.review_yesterday)
                null -> uiText(R.string.ui_history_components_018)
                else -> bidi(day.toString())
            }) }
            items(entries, key = { it.id }) { entry ->
                HistoryEntryCard(entry, ready.catalog, zone.id, medicationNames = medicationNames, showHijri = showHijri,
                    editMedication = if (ready.requiresUpdate || ready.maintenancePaused) null else ({ id -> vm.navigate(Route.EditMedication(id)) }))
            }
        }
        item {
            ActionGroup {
                AppButton(variant = AppButtonVariant.QUIET, enabled = !state.loading && state.request.page > 0,
                    onClick = { vm.historyPage(state.request.page - 1) }) { Text(uiText(R.string.ui_history_screen_009)) }
                Text(uiText(R.string.ui_history_screen_003, MedicationPresentation.displayDigits((state.request.page + 1).toString())))
                AppButton(variant = AppButtonVariant.QUIET, enabled = !state.loading && page?.hasMore == true,
                    onClick = { vm.historyPage(state.request.page + 1) }) { Text(uiText(R.string.ui_history_screen_010)) }
            }
        }
    }
}

@Composable internal fun HistoryFilters(filter: HistoryFilter, medicationScope: String?, medications: List<HistoryOption>,
    timezone: String, apply: (HistoryFilter) -> Unit) {
    val uiText = rememberUiText()
    var from by rememberSaveable(filter.from, medicationScope) { mutableStateOf(filter.from?.toString().orEmpty()) }
    var through by rememberSaveable(filter.through, medicationScope) { mutableStateOf(filter.through?.toString().orEmpty()) }
    var medication by rememberSaveable(filter.medicationId, medicationScope) { mutableStateOf(filter.medicationId) }
    var entityType by rememberSaveable(filter.entityType, medicationScope) { mutableStateOf(filter.entityType) }
    val fromDate = historyDate(from)
    val throughDate = historyDate(through)
    val valid = (from.isBlank() || fromDate != null) && (through.isBlank() || throughDate != null) &&
        (fromDate == null || throughDate == null || fromDate <= throughDate)
    GroupedCard(title = uiText(R.string.ui_history_screen_002)) {
        val today = rememberPresentationTime(timezone).atZone(java.time.ZoneId.of(timezone)).toLocalDate()
        val ranges = listOf(R.string.review_today to today, R.string.review_7_days to today.minusDays(6),
            R.string.review_since_sunday to today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY)))
        var custom by rememberSaveable(filter.from, filter.through) { mutableStateOf(ranges.none { it.second == filter.from && filter.through == today }) }
        WrappingContent {
            ranges.forEach { (label, start) -> AppChip(selected = !custom && from == start.toString() && through == today.toString(),
                onClick = { custom = false; from = start.toString(); through = today.toString() }, label = { Text(uiText(label)) }) }
            AppChip(selected = custom, onClick = { custom = true }, label = { Text(uiText(R.string.review_custom)) })
        }
        if (custom) {
        GregorianDateField(uiText(R.string.ui_history_screen_011), from, { from = it })
        GregorianDateField(uiText(R.string.ui_history_screen_012), through, { through = it })
        }
        HistoryFilterPicker(uiText(R.string.logic_history_type), listOf(HistoryOption("availability", uiText(R.string.logic_history_availability)), HistoryOption("medication", uiText(R.string.logic_history_medication)),
            HistoryOption("taxonomy", uiText(R.string.logic_history_taxonomy)), HistoryOption("pharmacy", uiText(R.string.logic_history_pharmacy)), HistoryOption("snapshot", uiText(R.string.logic_history_publication)),
            HistoryOption("maintenance", uiText(R.string.logic_history_maintenance))), entityType) { entityType = it }
        if (medicationScope == null) HistoryFilterPicker(uiText(R.string.ui_history_screen_014), medications, medication) { medication = it }
        if (!valid) Text(uiText(R.string.ui_history_screen_015), color = MaterialTheme.colorScheme.error)
        ActionGroup {
            AppButton(enabled = valid, onClick = { apply(HistoryFilter(fromDate, throughDate, medicationScope ?: medication, entityType)) }) { Text(uiText(R.string.ui_history_screen_016)) }
            AppButton(variant = AppButtonVariant.QUIET, onClick = {
                from = ""; through = ""; medication = medicationScope
                apply(HistoryFilter(medicationId = medicationScope))
            }) { Text(uiText(R.string.ui_history_screen_017)) }
        }
    }
}

@Composable private fun HistoryFilterPicker(label: String, values: List<HistoryOption>, selectedId: String?, choose: (String?) -> Unit) {
    val uiText = rememberUiText()
    val all = HistoryOption("", uiText(R.string.ui_history_screen_018))
    val selected = if (selectedId == null) all else values.find { it.id == selectedId } ?: HistoryOption(selectedId, uiText(R.string.ui_history_screen_019, selectedId))
    ChoicePicker(label, listOf(all) + values, selected, {
        if (it.id.isEmpty()) it.name else "${bidi(it.name)} · ${bidi(it.id)}"
    }, itemKey = { it.id }) { choose(it.id.takeIf(String::isNotEmpty)) }
}

private fun historyDate(value: String): LocalDate? = runCatching {
    require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value))
    LocalDate.parse(value).also { require(it.year in 1..9999) }
}.getOrNull()

private fun pharmacyKey(ready: CatalogState.Ready) = ready.catalog.pharmacy?.pharmacyId
