package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import com.aqaqeer.app.data.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import java.time.Instant

internal enum class HomeLeadingItem { BULK_HELP, OPERATION, NO_SECTION, SEARCH_SECTION, EMPTY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable internal fun HomeScreen(ready: CatalogState.Ready,
    state: AppUiState,
    now: Instant,
    availabilityBusy: Boolean = false,
    availabilityMessage: String? = null,
    pageHeader: (@Composable (Set<String>) -> Unit)? = null,
    availabilitySavedId: String? = null,
    publicationBusy: Boolean = false,
    publicationBlockReason: String? = null,
    projection: HomeBrowseResult = HomeBrowseResult(),
    highlightedMedication: String? = null,
    actions: HomeActions,
    savedQuery: StateFlow<String>? = null) {
    val uiText = rememberUiText()
    val selectSection = actions.selectSection
    val setQuery = actions.setQuery
    val toggleStatus = actions.toggleStatus
    val toggleCategory = actions.toggleCategory
    val clearFilters = actions.clearFilters
    val openMedication = actions.openMedication
    val openSettings = actions.openSettings
    val openConnection = actions.openConnection
    val refresh = actions.refresh
    val openCreate = actions.openCreate
    val openRestore = actions.openRestore
    val saveAvailability = actions.saveAvailability
    val openAddMedication = actions.openAddMedication
    val onModalVisibilityChanged = actions.onModalVisibilityChanged
    val availabilityInProgress = actions.availabilityInProgress
    val canOpenHomeModal = actions.canOpenHomeModal
    val setGroupingEnabled = actions.setGroupingEnabled
    val setGrouping = actions.setGrouping
    val saveBulk = actions.saveBulk
    val prefillMedication = actions.prefillMedication
    val onBulkModeChanged = actions.onBulkModeChanged
    val toggleExtraFilter = actions.toggleExtraFilter
    val onBottomBarsHeightChanged = actions.onBottomBarsHeightChanged
    val toggleAvailableFilter = actions.toggleAvailableFilter
    val editMedication = actions.editMedication
    val catalog = ready.catalog
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    if (catalog.pharmacy == null) {
        LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(UiSpacing.page)) {
            item { WelcomeScreen(openSettings, if (ready.isDemo) null else openConnection, if (ready.isDemo) null else openCreate, if (ready.isDemo) null else openRestore) }
        }
        return
    }
    val active = remember(catalog.sections) { catalog.sections.filter { it.isActive } }
    val medicationIds = remember(catalog.medications) { catalog.medications.mapTo(HashSet()) { it.id } }
    val selected = active.firstOrNull { it.id == state.sectionId } ?: active.firstOrNull()
    val canEdit = ready.canEditCatalog && saveAvailability != null
    val selectionState = rememberHomeSelectionState(catalog, selected, canEdit, availabilityInProgress, availabilitySavedId)
    var bulkMode by selectionState.bulkModeState
    var selectedIds by selectionState.selectedIdsState
    var editor by selectionState.editorState
    var bulkSheetOpen by selectionState.bulkSheetState
    val contextValid = selectionState.contextValid
    var filtersOpen by rememberSaveable(catalog.pharmacy.pharmacyId) { mutableStateOf(false) }
    var noticeDialogOpen by remember(catalog.pharmacy.pharmacyId) { mutableStateOf(false) }
    val notifyModal by rememberUpdatedState(onModalVisibilityChanged)
    val editorCurrent = editor?.let {
        contextValid && canEdit && !it.restored && (!it.bulk || saveBulk != null) &&
            it.pharmacyId == catalog.pharmacy.pharmacyId && it.section.id == selected?.id
    } == true
    val editorVisible = editorCurrent && editor?.bulk == false
    SideEffect { notifyModal(filtersOpen || editorVisible || bulkSheetOpen || noticeDialogOpen); onBulkModeChanged(bulkMode && contextValid) }
    val notifyBulk by rememberUpdatedState(onBulkModeChanged)
    DisposableEffect(Unit) { onDispose { notifyModal(false); notifyBulk(false) } }
    if (filtersOpen) HomeFilterSheet(catalog, state.filters, toggleStatus, toggleCategory, clearFilters,
        ready.admin, toggleExtraFilter, showStatuses = ready.admin,
        toggleAvailable = toggleAvailableFilter) { filtersOpen = false }
    // Deleted targets remain selected so confirmation cannot silently shrink the operation.
    val selectionValid = selectedIds.all { id -> id in medicationIds }
    fun selection(ids: Set<String>) = selectionState.capture(ids)
    fun changeBulkSelection(ids: Set<String>) = selectionState.changeBulkSelection(ids)
    fun exitBulk() = selectionState.exitBulk()
    BackHandler(bulkMode && !editorVisible && !filtersOpen && !bulkSheetOpen && canOpenHomeModal()) { exitBulk() }
    val today = MedicationPresentation.today(now, catalog.pharmacy.timezone)
    val projectionReady = projection.catalog == catalog && projection.sectionId == selected?.id &&
        projection.query == state.appliedQuery && projection.filters == state.filters && projection.preferences == state.homePreferences && projection.fetched == state.fetchedIds &&
        projection.unpublished == ready.unpublishedMedicationIds && projection.today == today &&
        projection.readerStockOnly == !ready.admin
    // Retain complete display rows only inside the same browsing/access context.
    // Bulk selection and availability markers still require an independently checked fresh projection.
    var lastComplete by remember(catalog.pharmacy.pharmacyId, selected?.id,
        state.homePreferences.grouping, state.homePreferences.groupingEnabled, !ready.admin) {
        mutableStateOf<HomeBrowseResult?>(null)
    }
    SideEffect { if (projectionReady) lastComplete = projection }
    val displayed = if (projectionReady) projection else lastComplete
    val visible = displayed?.visible.orEmpty()
    val groups = displayed?.groups.orEmpty()
    val otherSectionResults = if (!bulkMode) displayed?.otherSectionResults.orEmpty() else emptyList()
    val otherSectionPreferences = remember(state.homePreferences) { state.homePreferences.copy(groupingEnabled = false) }
    val displayQuery = displayed?.query.orEmpty()
    val normalizedDisplayQuery = remember(displayQuery) { MedicationPresentation.normalizedSearch(displayQuery) }
    val highlightTokens = remember(normalizedDisplayQuery) { MedicationPresentation.searchTokens(normalizedDisplayQuery) }
    val searching = highlightTokens.isNotEmpty()
    val editorSelectionValid = editor?.let { captured -> captured.expected.all { (id, before) ->
        id in medicationIds && catalog.status(id, captured.section.id) == before
    } } == true
    val searchText = rememberSaveable(catalog.pharmacy.pharmacyId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(savedQuery?.value ?: state.query))
    }
    LaunchedEffect(savedQuery, searchText) {
        val queryFlow = savedQuery ?: return@LaunchedEffect
        queryFlow.collect {
            val current = queryFlow.value
            if (searchText.value.text != current) searchText.value = TextFieldValue(current)
        }
    }
    fun changeSearch(value: TextFieldValue) {
        searchText.value = value
        setQuery(value.text)
    }
    val browseHeading: @Composable () -> Unit = {
        HomeSectionsBar(active, selected?.id, selectSection,
            availableSearchSections = if (projectionReady) projection.availableSearchSections else emptySet())
    }
    fun openCard(id: String) {
        if (editor != null || filtersOpen || noticeDialogOpen || !canOpenHomeModal()) return
        if (canEdit && editMedication != null) {
            if (!availabilityInProgress() && id in medicationIds) editMedication(id)
        } else openMedication(id)
    }
    val searchControls: @Composable () -> Unit = {
        HomeSearchControls(searchText, ::changeSearch, state.filters, openFilters = {
                if (editor == null && canOpenHomeModal()) {
                    focus.clearFocus(); notifyModal(true); filtersOpen = true
                }
            }, clearFilters = clearFilters, compact = state.homePreferences.searchAtBottom,
            showFetchedChip = !ready.admin && (state.fetchedIds.isNotEmpty() || state.filters.fetchedOnly),
            toggleFetched = { toggleExtraFilter("fetched") },
            // Adding belongs to the pharmacy, so resetting section-scoped selections must not move the search field.
            addMedication = if (canEdit && !bulkMode) openAddMedication?.let { add -> ({
                if (editor == null && !filtersOpen && !bulkSheetOpen && !noticeDialogOpen &&
                    !availabilityInProgress() && canOpenHomeModal()) {
                    focus.clearFocus(); add()
                }
            }) } else null,
            addEnabled = !availabilityBusy && editor == null && !filtersOpen && !bulkSheetOpen && !noticeDialogOpen)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = LocalLayoutMetrics.current.compact(maxHeight)
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        val keyboardOpen by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
        val showBottomBars = state.homePreferences.searchAtBottom || state.homePreferences.sectionsAtBottom && !keyboardOpen
        val reportBottomHeight by rememberUpdatedState(onBottomBarsHeightChanged)
        SideEffect { if (!showBottomBars) reportBottomHeight(0) }
        DisposableEffect(Unit) { onDispose { reportBottomHeight(0) } }
        val gridState = rememberLazyGridState()
        var lastGridQuery by rememberSaveable(catalog.pharmacy.pharmacyId) { mutableStateOf<String?>(null) }
        SideEffect {
            if (projectionReady && normalizedDisplayQuery != lastGridQuery) {
                // Override the grid's keyed scroll anchor as the replacement results are measured.
                if (lastGridQuery != null) gridState.requestScrollToItem(0)
                lastGridQuery = normalizedDisplayQuery
            }
        }
        var collapsedGroups by rememberSaveable(catalog.pharmacy.pharmacyId, selected?.id, state.homePreferences.grouping) { mutableStateOf(emptyList<String>()) }
        val leadingItems = buildList {
            if (canEdit && selected != null && bulkMode && contextValid) add(HomeLeadingItem.BULK_HELP)
            if (editor == null && (availabilityBusy || availabilityMessage != null)) add(HomeLeadingItem.OPERATION)
            if (selected == null) add(HomeLeadingItem.NO_SECTION)
            if (displayed != null && searching && selected != null) add(HomeLeadingItem.SEARCH_SECTION)
            if (displayed != null && visible.isEmpty() && otherSectionResults.isEmpty()) add(HomeLeadingItem.EMPTY)
        }
        var didScrollToHighlight by remember(catalog.pharmacy.pharmacyId, highlightedMedication) { mutableStateOf(false) }
        LaunchedEffect(highlightedMedication, projectionReady) {
            if (highlightedMedication != null && projectionReady && !didScrollToHighlight) {
                val medicationIndex = visible.indexOfFirst { it.id == highlightedMedication }
                if (medicationIndex >= 0) {
                    val containing = groups.firstOrNull { group -> group.medications.any { it.id == highlightedMedication } }?.key
                    if (containing != null) collapsedGroups = collapsedGroups - containing
                    val groupedIndex = if (!state.homePreferences.groupingEnabled) medicationIndex else {
                        var offset = 0
                        for (group in groups) {
                            val member = group.medications.indexOfFirst { it.id == highlightedMedication }
                            if (member >= 0) { offset += member + 1; break }
                            offset += (if (group.key in collapsedGroups) 0 else group.medications.size) + 1
                        }
                        offset
                    }
                    didScrollToHighlight = true
                    gridState.animateScrollToItem(leadingItems.size + groupedIndex)
                }
            }
        }
        val canSwipe = active.size > 1 && !bulkMode && editor == null && !filtersOpen && !bulkSheetOpen &&
            !availabilityBusy && canOpenHomeModal()
        val sectionSwipe = Modifier.sectionSwipe(canSwipe, active, selected, selectSection)
        val card: @Composable LazyGridItemScope.(Medication) -> Unit = { medication ->
            MedicationCard(requireNotNull(displayed).rows.getValue(medication.id), selected?.id, state.homePreferences, today,
                onQuickEdit = if (canEdit && !bulkMode && selected != null && !availabilityBusy && medication.id in medicationIds)
                    ({
                        if (!filtersOpen && editor == null && canOpenHomeModal() && !availabilityInProgress()) {
                            focus.clearFocus(); editor = selection(setOf(medication.id)); notifyModal(editor != null)
                        }
                    }) else null,
                reserveSelectionSpace = canEdit && saveBulk != null,
                editableStatus = canEdit && !bulkMode && selected != null && medication.id in medicationIds,
                quickMenuOpen = editorVisible && editor?.medications?.singleOrNull()?.id == medication.id,
                savingStatus = availabilityBusy && editor?.medications?.singleOrNull()?.id == medication.id,
                dismissQuickMenu = { if (!availabilityInProgress()) editor = null },
                chooseStatus = { status ->
                    val captured = editor
                    if (captured != null && editorCurrent && !availabilityInProgress() && captured.expected.all { (id, before) ->
                        id in medicationIds && catalog.status(id, captured.section.id) == before
                    }) saveAvailability?.invoke(captured, status) { if (editor == captured) editor = null }
                },
                bulkSelected = if (bulkMode && contextValid && canEdit) medication.id in selectedIds else null,
                toggleSelection = { if (projectionReady && !availabilityBusy) changeBulkSelection(if (medication.id in selectedIds)
                    selectedIds - medication.id else selectedIds + medication.id) },
                onClick = { openCard(medication.id) }, selectionEnabled = !availabilityBusy && medication.id in medicationIds && (!bulkMode || projectionReady) &&
                    (editor == null || editor?.bulk == true || editorSelectionValid),
                modifier = Modifier.animateItem(), highlightTokens = highlightTokens, highlighted = medication.id == highlightedMedication,
                unpublished = ready.admin && medication.id in ready.unpublishedMedicationIds,
                onLongClick = if (canEdit && saveBulk != null && selected != null && !bulkMode && !availabilityBusy && medication.id in medicationIds) ({
                    if (contextValid && editor == null && !filtersOpen && !availabilityInProgress() && canOpenHomeModal()) {
                        focus.clearFocus(); haptics.performHapticFeedback(HapticFeedbackType.LongPress); bulkMode = true; changeBulkSelection(setOf(medication.id))
                    }
                }) else null)
        }
        val noticeMaxHeight = maxHeight * if (bulkMode) 0.25f else 0.4f
        Column(Modifier.fillMaxSize()) {
            if (!state.homePreferences.searchAtBottom || !state.homePreferences.sectionsAtBottom) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ContentSection(modifier = Modifier.padding(horizontal = UiSpacing.page).padding(bottom = UiSpacing.related)) {
                        if (!state.homePreferences.sectionsAtBottom) browseHeading()
                        if (!state.homePreferences.searchAtBottom) searchControls()
                    }
                }
            }
            if (gridState.canScrollBackward) AppDivider()
            HomeNoticeStrip(ready, state, pageHeader, refresh, publicationBusy, publicationBlockReason,
                maxHeight = noticeMaxHeight, compact = compactHeight,
                onDialogVisibilityChanged = { noticeDialogOpen = it })
            Box(Modifier.weight(1f)) {
                RefreshContainer(refresh, state.refresh.busy) {
                    HomeGrid(state = gridState, adaptive = state.homePreferences.twoColumns,
                        modifier = sectionSwipe, projection = displayed, otherSections = otherSectionResults,
                        groupingEnabled = state.homePreferences.groupingEnabled, collapsedGroups = collapsedGroups,
                        toggleGroup = { group -> collapsedGroups = if (group in collapsedGroups) collapsedGroups - group else collapsedGroups + group },
                        leadingContent = {
                            leadingItems.forEach { leading ->
                                item(key = if (leading == HomeLeadingItem.SEARCH_SECTION) "current_search_section_${selected?.id}" else "home_leading_${leading.name}",
                                    span = { GridItemSpan(maxLineSpan) }, contentType = leading) {
                                    when (leading) {
                                        HomeLeadingItem.BULK_HELP -> SupportingText(uiText(R.string.home_bulk_selection_retention_help))
                                        HomeLeadingItem.OPERATION -> InlineOperationStatus(availabilityBusy, availabilityMessage)
                                        HomeLeadingItem.NO_SECTION -> InformationCard(uiText(R.string.home_no_active_sections_title), uiText(R.string.home_no_active_sections_help))
                                        HomeLeadingItem.SEARCH_SECTION -> ContentSection {
                                            SectionHeading(requireNotNull(selected).nameAr)
                                            if (visible.isEmpty() && otherSectionResults.isNotEmpty())
                                                SupportingText(uiText(R.string.review_no_results_in_section))
                                        }
                                        HomeLeadingItem.EMPTY -> GroupedCard(title = if (catalog.medications.isEmpty()) uiText(R.string.home_empty_catalog_title) else uiText(R.string.shared_no_matches_message)) {
                                            EmptyCapsule(Modifier.size(if (compactHeight) Size.illustrationSmall else Size.illustrationLarge).align(Alignment.CenterHorizontally))
                                            if (catalog.medications.isEmpty()) {
                                                SupportingText(uiText(R.string.home_empty_catalog_help))
                                                if (canEdit && openAddMedication != null) AppButton(onClick = openAddMedication) { Text(uiText(R.string.navigation_add_medication_title)) }
                                            } else {
                                                SupportingText(uiText(R.string.home_no_matches_help))
                                                if (canEdit) AppButton(variant = AppButtonVariant.QUIET, onClick = { prefillMedication(searchText.value.text) }) { Text(uiText(R.string.home_add_search_name_action)) }
                                                AppButton(variant = AppButtonVariant.QUIET, onClick = { focus.clearFocus(); changeSearch(TextFieldValue("")); clearFilters() }) { Text(uiText(R.string.home_clear_search_filters_action)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }, card = card, otherCard = { result, row ->
                            MedicationCard(row, result.section.id, otherSectionPreferences, today,
                                    onQuickEdit = null, bulkSelected = null, toggleSelection = {},
                                    onClick = { openCard(row.medication.id) },
                                    modifier = Modifier.animateItem(), highlightTokens = highlightTokens,
                                    highlighted = row.medication.id == highlightedMedication,
                                    unpublished = ready.admin && row.unpublished)
                        })
                }
            }
            if (canEdit && selected != null && bulkMode && contextValid) {
                HomeBulkBar(HomeBulkPresentation(selected.nameAr, selectedIds.size, selectionValid, availabilityBusy,
                    if (projectionReady) projection.visible.size else 0), cancel = ::exitBulk,
                    selectVisible = { if (projectionReady) changeBulkSelection(selectedIds + projection.visible.map { it.id }) },
                    clear = { changeBulkSelection(emptySet()) }, openEditor = { focus.clearFocus(); bulkSheetOpen = true })
                }
            if (showBottomBars) HomeBottomBars(
                searchAtBottom = state.homePreferences.searchAtBottom,
                sectionsAtBottom = state.homePreferences.sectionsAtBottom && !keyboardOpen,
                reportHeight = { reportBottomHeight(it) }, search = searchControls, sections = browseHeading)

            if (bulkSheetOpen && canEdit && selected != null && bulkMode && contextValid) {
                HomeBulkSheet(editor?.takeIf { it.bulk && editorCurrent }, catalog, availabilityBusy, availabilityMessage,
                    availabilityInProgress, { filtersOpen }, canOpenHomeModal, saveBulk, { editor == it },
                    dismissSheet = { bulkSheetOpen = false }, exitBulk = ::exitBulk,
                    complete = selectionState::clear)
            }
        }

    }
}

@Composable internal fun <T : Enum<T>> PreferenceChoices(title: String, choices: List<T>, selected: T,
    tag: String, select: (T) -> Unit, label: (T) -> String) {
    PreferenceChoiceRow(title, choices, selected, select, label,
        modifier = Modifier.testTag("${tag}_preference"),
        choiceModifier = { Modifier.testTag("${tag}_${it.name}") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RefreshContainer(refresh: (() -> Unit)?, busy: Boolean, content: @Composable BoxScope.() -> Unit) {
    if (refresh == null) Box(Modifier.fillMaxSize(), content = content)
    else PullToRefreshBox(isRefreshing = busy, onRefresh = refresh, modifier = Modifier.fillMaxSize(), content = content)
}

@Composable internal fun RefreshControls(state: RefreshUiState, refresh: () -> Unit) {
    val uiText = rememberUiText()
    Column {
        AppButton(variant = AppButtonVariant.SECONDARY, onClick = refresh, enabled = !state.busy, modifier = Modifier.testTag("refresh_catalog")) {
            Text(if (state.busy) uiText(R.string.home_refresh_progress) else uiText(R.string.home_refresh_action))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("refresh_error")) }
    }
}

@Composable internal fun FilterSummary(filters: HomeFilters, clear: () -> Unit) {
    val uiText = rememberUiText()
    if (!filters.hasAny) return
    ActionGroup(Modifier.testTag("filter_summary")) {
        Text(uiText(R.string.home_filter_summary_format, MedicationPresentation.displayDigits(filters.statuses.size.toString()), MedicationPresentation.displayDigits(filters.categoryIds.size.toString())))
        if (filters.hasAny)
            AppButton(variant = AppButtonVariant.QUIET, onClick = clear, modifier = Modifier.testTag("clear_filters")) { Text(uiText(R.string.history_clear_filters_action)) }
    }
}
