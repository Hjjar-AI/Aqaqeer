package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.aqaqeer.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Refresh date-sensitive badges on resume and at pharmacy midnight, even without a DB edit. */
@Composable internal fun rememberPresentationTime(timezone: String): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(Instant.now(), lifecycle, timezone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = Instant.now()
                val midnight = value.atZone(ZoneId.of(timezone)).toLocalDate().plusDays(1)
                    .atStartOfDay(ZoneId.of(timezone)).toInstant()
                // Wake at pharmacy midnight; at most one minute between clock-change checks.
                delay((Duration.between(value, midnight).toMillis() + 1).coerceIn(1, 60_000))
            }
        }
    }.value
}

/** Minute clock checks do not emit while the pharmacy date is unchanged. */
@Composable internal fun rememberPresentationDate(timezone: String): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun dayStart() = Instant.now().atZone(ZoneId.of(timezone)).toLocalDate().atStartOfDay(ZoneId.of(timezone)).toInstant()
    return produceState(dayStart(), lifecycle, timezone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = dayStart()
                val next = value.atZone(ZoneId.of(timezone)).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant()
                delay((Duration.between(Instant.now(), next).toMillis() + 1).coerceIn(1, 60_000))
            }
        }
    }.value
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable internal fun HomeScreen(ready: CatalogState.Ready, state: AppUiState, now: Instant,
    selectSection: (String) -> Unit, setQuery: (String) -> Unit, toggleStatus: (AvailabilityStatus) -> Unit,
    toggleCategory: (String) -> Unit, clearFilters: () -> Unit, openMedication: (String) -> Unit,
    openSettings: () -> Unit, openConnection: (() -> Unit)? = null, refresh: (() -> Unit)? = null, openCreate: (() -> Unit)? = null, openRestore: (() -> Unit)? = null,
    availabilityBusy: Boolean = false, availabilityMessage: String? = null,
    saveAvailability: ((AvailabilitySelection, AvailabilityStatus, () -> Unit) -> Unit)? = null,
    pendingBanner: @Composable () -> Unit = {}, pageHeader: (@Composable () -> Unit)? = null,
    openAddMedication: (() -> Unit)? = null,
    availabilitySavedId: String? = null, publicationBusy: Boolean = false, publicationBlockReason: String? = null,
    onModalVisibilityChanged: (Boolean) -> Unit = {}, availabilityInProgress: () -> Boolean = { availabilityBusy },
    canOpenHomeModal: () -> Boolean = { true },
    setGroupingEnabled: (Boolean) -> Unit = {}, setGrouping: (HomeGrouping) -> Unit = {},
    saveBulk: ((AvailabilitySelection, BulkMedicationChanges, () -> Unit) -> Unit)? = null,
    projection: HomeBrowseResult = HomeBrowseResult(), highlightedMedication: String? = null,
    prefillMedication: (String) -> Unit = {}, onBulkModeChanged: (Boolean) -> Unit = {}) {
    val catalog = ready.catalog
    val focus = LocalFocusManager.current
    if (catalog.pharmacy == null) {
        LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(UiSpacing.page)) {
            item { WelcomeScreen(openSettings, if (ready.isDemo) null else openConnection, if (ready.isDemo) null else openCreate, if (ready.isDemo) null else openRestore) }
        }
        return
    }
    val active = catalog.sections.filter { it.isActive }
    val selected = active.firstOrNull { it.id == state.sectionId } ?: active.firstOrNull()
    val canEdit = ready.admin && !ready.requiresUpdate && !ready.maintenancePaused && !ready.isDemo && saveAvailability != null
    var bulkMode by rememberSaveable(catalog.pharmacy.pharmacyId, selected?.id, canEdit) { mutableStateOf(false) }
    var selectedIds by rememberSaveable(catalog.pharmacy.pharmacyId, selected?.id, canEdit,
        stateSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    var editor by rememberSaveable(catalog.pharmacy.pharmacyId, selected?.id, canEdit, stateSaver = AvailabilitySelectionSaver) { mutableStateOf<AvailabilitySelection?>(null) }
    val contextKey = "${catalog.pharmacy.pharmacyId}/${selected?.id}/${canEdit}"
    var capturedContext by rememberSaveable { mutableStateOf(contextKey) }
    val contextValid = capturedContext == contextKey
    LaunchedEffect(contextKey) {
        if (!contextValid) { bulkMode = false; selectedIds = emptySet(); editor = null; capturedContext = contextKey }
    }
    var bulkSheetOpen by rememberSaveable(contextKey) { mutableStateOf(false) }
    var filtersOpen by rememberSaveable(catalog.pharmacy.pharmacyId) { mutableStateOf(false) }
    val notifyModal by rememberUpdatedState(onModalVisibilityChanged)
    val editorCurrent = editor?.let {
        contextValid && canEdit && (!it.bulk || saveBulk != null) &&
            it.pharmacyId == catalog.pharmacy.pharmacyId && it.section.id == selected?.id
    } == true
    val editorVisible = editorCurrent && editor?.bulk == false
    SideEffect { notifyModal(filtersOpen || editorVisible || bulkSheetOpen); onBulkModeChanged(bulkMode && contextValid) }
    val notifyBulk by rememberUpdatedState(onBulkModeChanged)
    DisposableEffect(Unit) { onDispose { notifyModal(false); notifyBulk(false) } }
    if (filtersOpen) HomeFilterSheet(catalog, state.filters, toggleStatus, toggleCategory, clearFilters,
        state.homePreferences, setGroupingEnabled, setGrouping) { filtersOpen = false }
    LaunchedEffect(availabilitySavedId) {
        if (availabilitySavedId != null && editor?.id == availabilitySavedId) {
            editor = null; selectedIds = emptySet(); bulkMode = false; bulkSheetOpen = false
        }
    }
    // Never silently shrink a confirmed selection when a refresh deletes a target.
    val selectionValid = selectedIds.all { id -> catalog.medications.any { it.id == id } }
    fun selection(ids: Set<String>): AvailabilitySelection? = selected?.let { section ->
        AvailabilitySelection(catalog.pharmacy.pharmacyId, section, catalog.medications.filter { it.id in ids },
            ids.associateWith { catalog.status(it, section.id) }, bulkMode,
            formNames = catalog.forms.associate { it.id to it.nameAr },
            categoryNames = catalog.categories.associate { it.id to it.nameAr })
    }
    fun changeBulkSelection(ids: Set<String>) {
        if (availabilityInProgress()) return
        val fresh = selection(ids) ?: return
        val old = editor?.takeIf { it.bulk && contextValid }
        selectedIds = ids
        editor = fresh.copy(bulk = true, id = old?.id ?: fresh.id,
            medications = ids.mapNotNull { id -> old?.medications?.find { it.id == id }
                ?: fresh.medications.find { it.id == id } },
            expected = fresh.expected.mapValues { (id, value) -> old?.expected?.get(id) ?: value },
            formNames = fresh.formNames + old?.formNames.orEmpty(),
            categoryNames = fresh.categoryNames + old?.categoryNames.orEmpty())
    }
    fun exitBulk() { if (!availabilityInProgress()) { editor = null; selectedIds = emptySet(); bulkMode = false; bulkSheetOpen = false } }
    LaunchedEffect(bulkMode, contextValid) {
        if (bulkMode && contextValid && editor == null) changeBulkSelection(selectedIds)
    }
    BackHandler(bulkMode && !editorVisible && !filtersOpen && !bulkSheetOpen && canOpenHomeModal()) { exitBulk() }
    editor?.takeIf { editorVisible }?.let { captured ->
        AvailabilityEditor(captured, availabilityBusy, availabilityMessage,
            dismiss = { if (!availabilityInProgress()) editor = null },
            save = { status -> saveAvailability?.invoke(captured, status) {
                if (editor == captured) { editor = null; selectedIds = emptySet(); bulkMode = false; bulkSheetOpen = false }
            } }, current = captured.expected.all { (id, before) ->
                catalog.medications.any { it.id == id } && catalog.status(id, captured.section.id) == before
            }, isSaving = availabilityInProgress)
    }
    val today = MedicationPresentation.today(now, catalog.pharmacy.timezone)
    val projectionReady = projection.catalog == catalog && projection.sectionId == selected?.id &&
        projection.query == state.appliedQuery && projection.filters == state.filters && projection.preferences == state.homePreferences
    val visible = if (projectionReady) projection.visible else emptyList()
    val groups = if (projectionReady) projection.groups else emptyList()
    val searching = MedicationPresentation.normalizedSearch(state.appliedQuery).isNotEmpty()
    var searchText by rememberSaveable(catalog.pharmacy.pharmacyId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(state.query)) }
    fun changeSearch(value: TextFieldValue) { searchText = value; setQuery(value.text) }
    val browseHeading: @Composable () -> Unit = {
        if (active.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val style = MaterialTheme.typography.titleSmall
            val density = LocalDensity.current
            val required = remember(active, catalog.outCounts, style, density) {
                active.maxOf { section ->
                    val count = catalog.outCounts[section.id] ?: 0
                    val label = section.nameAr + if (count > 0) " ${MedicationPresentation.easternDigits(count.toString())}" else ""
                    with(density) { measurer.measure(androidx.compose.ui.text.AnnotatedString(label), style).size.width.toDp() } + UiSpacing.tabMeasurementAllowance
                } * active.size
            }
            val tabs: @Composable () -> Unit = {
                active.forEach { section ->
                    val out = catalog.outCounts[section.id] ?: 0
                    Tab(selected = selected?.id == section.id, onClick = { selectSection(section.id) },
                        modifier = Modifier.testTag("section_${section.id}").semantics {
                            contentDescription = section.nameAr + if (out > 0) uiText(R.string.ui_home_screen_001, MedicationPresentation.easternDigits(out.toString())) else ""
                        }) {
                        Row(Modifier.heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.related),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                            Text(section.nameAr, style = style)
                            if (out > 0) Badge { Text(MedicationPresentation.easternDigits(out.toString())) }
                        }
                    }
                }
            }
            if (required <= maxWidth) PrimaryTabRow(selectedTabIndex = active.indexOf(selected).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.background, tabs = tabs)
            else PrimaryScrollableTabRow(selectedTabIndex = active.indexOf(selected).coerceAtLeast(0), edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.background, tabs = tabs)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHeight = maxHeight < 480.dp || LocalDensity.current.fontScale > 1.4f
        val gridState = rememberLazyGridState()
        LaunchedEffect(highlightedMedication, projectionReady) {
            if (highlightedMedication != null && projectionReady) {
                val medicationIndex = visible.indexOfFirst { it.id == highlightedMedication }
                if (medicationIndex >= 0) {
                    val leading = (if (compactHeight) 1 else 0) + (if (bulkMode && canEdit && selected != null) 1 else 0) +
                        (if (editor == null && (availabilityBusy || availabilityMessage != null)) 1 else 0) + (if (selected == null) 1 else 0)
                    val groupedIndex = if (!state.homePreferences.groupingEnabled) medicationIndex else {
                        var offset = 0
                        for (group in groups) {
                            val member = group.medications.indexOfFirst { it.id == highlightedMedication }
                            if (member >= 0) { offset += member + 1; break }
                            offset += group.medications.size + 1
                        }
                        offset
                    }
                    gridState.animateScrollToItem(leading + groupedIndex)
                }
            }
        }
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        val canSwipe = active.size > 1 && !bulkMode && editor == null && !filtersOpen && !bulkSheetOpen &&
            !availabilityBusy && canOpenHomeModal()
        val sectionSwipe = if (!canSwipe) Modifier else Modifier.pointerInput(active.map { it.id }, selected?.id, rtl, swipeThreshold) {
            var distance = 0f
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f },
                onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
                onDragCancel = { distance = 0f },
                onDragEnd = {
                    if (kotlin.math.abs(distance) >= swipeThreshold) {
                        val next = active.indexOf(selected) + if ((distance > 0) == rtl) 1 else -1
                        active.getOrNull(next)?.let { selectSection(it.id) }
                    }
                    distance = 0f
                })
        }
        val showAdd = openAddMedication != null && canEdit && !bulkMode && contextValid
        val noticeMaxHeight = maxHeight * if (bulkMode) 0.25f else 0.4f
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.background) {
                ContentSection(modifier = Modifier.padding(horizontal = UiSpacing.page).padding(bottom = UiSpacing.related)) {
                    if (!compactHeight) browseHeading()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                        OutlinedTextField(value = searchText, onValueChange = ::changeSearch, singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            placeholder = { Text(uiText(R.string.ui_home_screen_004)) },
                            modifier = Modifier.weight(1f).testTag("search"),
                            leadingIcon = { AppIcon(UiIcon.SEARCH) },
                            trailingIcon = if (searchText.text.isNotEmpty()) ({
                                IconButton(onClick = { changeSearch(TextFieldValue("")); focus.clearFocus() },
                                    modifier = Modifier.testTag("clear_search")) { AppIcon(UiIcon.CLOSE, description = uiText(R.string.ui_home_screen_005)) }
                            }) else null)
                        val filterCount = state.filters.statuses.size + state.filters.categoryIds.size
                        OutlinedIconButton(onClick = {
                            if (editor == null && canOpenHomeModal()) {
                                focus.clearFocus(); notifyModal(true); filtersOpen = true
                            }
                        },
                            modifier = Modifier.size(UiSpacing.filterButton).semantics {
                                contentDescription = uiText(R.string.ui_home_screen_002) + if (filterCount > 0) uiText(R.string.ui_home_screen_003, MedicationPresentation.easternDigits(filterCount.toString())) else ""
                            }) {
                            BadgedBox(badge = {
                                if (filterCount > 0) Badge(Modifier.clearAndSetSemantics { }) { Text(MedicationPresentation.easternDigits(filterCount.toString())) }
                            }) { AppIcon(UiIcon.FILTER) }
                        }
                    }
                    if (searching || state.filters.statuses.isNotEmpty() || state.filters.categoryIds.isNotEmpty()) {
                        WrappingContent {
                            Text(uiText(R.string.ui_home_screen_006, MedicationPresentation.easternDigits(visible.size.toString()), MedicationPresentation.easternDigits(catalog.medications.size.toString())),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (state.filters.statuses.isNotEmpty() || state.filters.categoryIds.isNotEmpty())
                                TextButton(onClick = clearFilters) { Text(uiText(R.string.ui_history_screen_017)) }
                        }
                    }
                }
            }
            if (gridState.canScrollBackward) HorizontalDivider()
            if (canEdit && selected != null && bulkMode && contextValid) {
                BulkSelectionBar(selected.nameAr, selectedIds.size, selectionValid, availabilityBusy, cancel = ::exitBulk,
                    visibleCount = visible.size, selectVisible = { changeBulkSelection(selectedIds + visible.map { it.id }) },
                    clear = { changeBulkSelection(emptySet()) }) {
                    ActionGroup {
                        Button(enabled = selectedIds.isNotEmpty() && selectionValid && !availabilityBusy,
                            onClick = { focus.clearFocus(); bulkSheetOpen = true }) { Text(uiText(R.string.ui_home_screen_007)) }
                        TextButton(enabled = !availabilityBusy, onClick = { changeBulkSelection(emptySet()) }) { Text(uiText(R.string.ui_home_screen_008)) }
                    }
                }
            }
            HomeNoticeStrip(ready, state, now, pendingBanner, pageHeader, refresh, publicationBusy, publicationBlockReason,
                maxHeight = noticeMaxHeight)
            Box(Modifier.weight(1f)) {
                RefreshContainer(refresh, state.refresh.busy) {
                    LazyVerticalGrid(columns = GridCells.Fixed(if (state.homePreferences.twoColumns) 2 else 1), state = gridState,
                        modifier = Modifier.fillMaxSize().then(sectionSwipe).testTag("home"), contentPadding = PaddingValues(
                            start = UiSpacing.page, end = UiSpacing.page, top = UiSpacing.related,
                            bottom = if (showAdd) UiSpacing.fabClearance else UiSpacing.page),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.related),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                        if (compactHeight) item(key = "browse_context", span = { GridItemSpan(maxLineSpan) }) { browseHeading() }
                        if (canEdit && selected != null && bulkMode && contextValid) item(span = { GridItemSpan(maxLineSpan) }) {
                            SupportingText(uiText(R.string.ui_home_screen_009))
                        }
                        if (editor == null && (availabilityBusy || availabilityMessage != null)) item(span = { GridItemSpan(maxLineSpan) }) {
                            InlineOperationStatus(availabilityBusy, availabilityMessage)
                        }
                        if (!projectionReady) items(4, key = { "loading_$it" }) { MedicationSkeleton() }
                        if (selected == null) item(span = { GridItemSpan(maxLineSpan) }) { InformationCard(uiText(R.string.ui_home_screen_010), uiText(R.string.ui_home_screen_011)) }
                        if (projectionReady && visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            GroupedCard(title = if (catalog.medications.isEmpty()) uiText(R.string.ui_home_screen_012) else uiText(R.string.ui_choice_components_003)) {
                                EmptyCapsule(Modifier.size(96.dp).align(Alignment.CenterHorizontally))
                                if (catalog.medications.isEmpty()) {
                                    SupportingText(uiText(R.string.ui_home_screen_013))
                                    if (canEdit && openAddMedication != null) Button(onClick = openAddMedication) { Text(uiText(R.string.ui_screen_titles_009)) }
                                } else {
                                    SupportingText(uiText(R.string.ui_home_screen_014))
                                    if (canEdit) TextButton(onClick = { prefillMedication(searchText.text) }) { Text(uiText(R.string.ui_home_screen_015)) }
                                    TextButton(onClick = { changeSearch(TextFieldValue("")); clearFilters(); focus.clearFocus() }) { Text(uiText(R.string.ui_home_screen_016)) }
                                }
                            }
                        }
                        val card: @Composable LazyGridItemScope.(Medication) -> Unit = { medication ->
                            MedicationCard(projection.rows.getValue(medication.id), selected?.id, state.homePreferences, today,
                                onQuickEdit = if (canEdit && !bulkMode && selected != null && !availabilityBusy)
                                    ({
                                        if (!filtersOpen && editor == null && canOpenHomeModal() && !availabilityInProgress()) {
                                            focus.clearFocus(); editor = selection(setOf(medication.id)); notifyModal(editor != null)
                                        }
                                    }) else null,
                                bulkSelected = if (bulkMode && contextValid && canEdit) medication.id in selectedIds else null,
                                toggleSelection = { if (!availabilityBusy) changeBulkSelection(if (medication.id in selectedIds)
                                    selectedIds - medication.id else selectedIds + medication.id) },
                                onClick = { openMedication(medication.id) }, selectionEnabled = !availabilityBusy,
                                modifier = Modifier.animateItem(), query = state.appliedQuery, highlighted = medication.id == highlightedMedication,
                                unpublished = ready.admin && medication.id in ready.unpublishedMedicationIds,
                                onLongClick = if (canEdit && saveBulk != null && selected != null && !bulkMode && !availabilityBusy) ({
                                    if (contextValid && editor == null && !filtersOpen && !availabilityInProgress() && canOpenHomeModal()) {
                                        focus.clearFocus(); bulkMode = true; changeBulkSelection(setOf(medication.id))
                                    }
                                }) else null)
                        }
                        if (state.homePreferences.groupingEnabled) {
                            groups.forEach { group ->
                                stickyHeader(key = group.key) {
                                    Surface(color = MaterialTheme.colorScheme.background) {
                                        SectionHeading(group.label, Modifier.fillMaxWidth().padding(vertical = UiSpacing.related))
                                    }
                                }
                                items(group.medications, key = { it.id }, itemContent = card)
                            }
                        } else items(visible, key = { it.id }, itemContent = card)
                    }
                }
            }
            if (bulkSheetOpen && canEdit && selected != null && bulkMode && contextValid) {
                val savingNow by rememberUpdatedState(availabilityInProgress)
                val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
                    confirmValueChange = { it != SheetValue.Hidden || !savingNow() })
                ModalBottomSheet(onDismissRequest = { if (!availabilityInProgress()) bulkSheetOpen = false }, sheetState = sheet) {
                    Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight()).verticalScroll(rememberScrollState()).padding(UiSpacing.page)) {
                        editor?.takeIf { it.bulk && editorCurrent }?.let { captured ->
                            BulkMedicationEditor(captured, catalog, availabilityBusy, availabilityMessage, dismiss = ::exitBulk,
                                save = { changes ->
                                    if (editor == captured && !availabilityInProgress() && !filtersOpen && canOpenHomeModal())
                                        saveBulk?.invoke(captured, changes) {
                                            if (editor == captured) { editor = null; selectedIds = emptySet(); bulkMode = false; bulkSheetOpen = false }
                                        }
                                },
                                current = { changes -> captured.medications.all { before ->
                                    catalog.medications.find { it.id == before.id }?.let { live ->
                                        (changes.status == null || catalog.status(before.id, captured.section.id) == captured.expected[before.id]) &&
                                            (changes.formId == null || live.formId == before.formId) &&
                                            (changes.categoryId == null || live.categoryId == before.categoryId)
                                    } == true
                                } }, isSaving = availabilityInProgress)
                        }
                    }
                }
            }
        }

    }
}

@Composable internal fun DataTimesDialog(ready: CatalogState.Ready, dateAnchor: Instant, dismiss: () -> Unit) {
    val timezone = ready.catalog.pharmacy?.timezone ?: "Asia/Damascus"
    val now = rememberPresentationTime(timezone)
    val last = ready.lastSuccessfulRefreshAt
    AlertDialog(onDismissRequest = dismiss, title = { Text(uiText(R.string.ui_aqaqeer_app_010)) },
        text = {
            DialogBody {
                LabeledValue(uiText(R.string.ui_home_screen_017), last?.let { relativeTime(it, now) } ?: uiText(R.string.ui_home_screen_018))
                LabeledValue(uiText(R.string.ui_home_screen_019), last?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.ui_home_screen_020))
                LabeledValue(uiText(R.string.ui_home_screen_021), ready.publishedAt?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.ui_aqaqeer_app_007))
                LabeledValue(uiText(R.string.ui_home_screen_022), bidi(timezone))
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text(uiText(R.string.ui_choice_components_004)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeFilterSheet(catalog: CatalogSnapshot, filters: HomeFilters,
    toggleStatus: (AvailabilityStatus) -> Unit, toggleCategory: (String) -> Unit, clear: () -> Unit,
    preferences: HomePreferences, setGroupingEnabled: (Boolean) -> Unit,
    setGrouping: (HomeGrouping) -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = viewportHeight()).padding(UiSpacing.page),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            SectionHeading(uiText(R.string.ui_home_screen_023))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                SectionHeading(uiText(R.string.ui_home_screen_024))
                WrappingContent(Modifier.testTag("status_filters")) {
                    MedicationPresentation.editableStatuses.forEach { status -> FilterChip(selected = status in filters.statuses,
                        onClick = { toggleStatus(status) }, label = { Text(statusLabel(status)) },
                        modifier = Modifier.testTag("filter_${status.name}")) }
                }
                SectionHeading(uiText(R.string.ui_pharmacy_admin_screen_073))
                WrappingContent(Modifier.testTag("category_filters")) {
                    catalog.categories.forEach { category -> FilterChip(selected = category.id in filters.categoryIds,
                        onClick = { toggleCategory(category.id) }, label = { Text(category.nameAr) },
                        modifier = Modifier.testTag("category_${category.id}")) }
                }
                FilterSummary(filters, clear)
                SwitchChoiceRow(preferences.groupingEnabled, uiText(R.string.ui_home_screen_025),
                    uiText(R.string.ui_home_screen_026), change = setGroupingEnabled)
                if (preferences.groupingEnabled) PreferenceChoices(uiText(R.string.ui_home_screen_027), HomeGrouping.entries,
                    preferences.grouping, "grouping", setGrouping) { mode -> when (mode) {
                        HomeGrouping.SCIENTIFIC_LETTER -> uiText(R.string.ui_home_screen_028)
                        HomeGrouping.CATEGORY -> uiText(R.string.ui_pharmacy_admin_screen_073)
                    } }
            }
            Button(onClick = dismiss) { Text(uiText(R.string.ui_home_screen_029)) }
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
    Column {
        OutlinedButton(onClick = refresh, enabled = !state.busy, modifier = Modifier.testTag("refresh_catalog")) {
            Text(if (state.busy) uiText(R.string.ui_home_screen_030) else uiText(R.string.ui_home_screen_031))
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("refresh_error")) }
    }
}

@Composable internal fun FilterSummary(filters: HomeFilters, clear: () -> Unit) {
    if (filters.statuses.isEmpty() && filters.categoryIds.isEmpty()) return
    ActionGroup(Modifier.testTag("filter_summary")) {
        Text(uiText(R.string.ui_home_screen_032, MedicationPresentation.easternDigits(filters.statuses.size.toString()), MedicationPresentation.easternDigits(filters.categoryIds.size.toString())))
        if (filters.statuses.isNotEmpty() || filters.categoryIds.isNotEmpty())
            TextButton(onClick = clear, modifier = Modifier.testTag("clear_filters")) { Text(uiText(R.string.ui_history_screen_017)) }
    }
}

/** One pinned entry point; expanding retains the existing notice actions and publication guards. */
@Composable private fun HomeNoticeStrip(ready: CatalogState.Ready, state: AppUiState, now: Instant,
    pendingBanner: @Composable () -> Unit, pageHeader: (@Composable () -> Unit)?, refresh: (() -> Unit)?, publicationBusy: Boolean, publicationBlockReason: String?,
    maxHeight: androidx.compose.ui.unit.Dp) {
    val clock = rememberPresentationTime(ready.catalog.pharmacy?.timezone ?: "Asia/Damascus")
    val notices = buildList {
        if (ready.requiresUpdate) add(uiText(R.string.ui_home_screen_036))
        if (ready.maintenancePaused) add(uiText(R.string.ui_home_screen_037))
        if (ready.restorePaused) add(uiText(R.string.ui_home_screen_038))
        if (ready.admin && !ready.isDemo && (ready.pendingCount > 0 || ready.uncertain || publicationBusy) &&
            !ready.requiresUpdate && !ready.maintenancePaused && !ready.restorePaused && publicationBlockReason != null)
            add(publicationBlockReason)
        if (refresh != null && state.refresh.error != null) add(uiText(R.string.ui_home_screen_039))
        if (ready.admin && !ready.isDemo) {
            if (publicationBusy) add(uiText(R.string.ui_home_screen_040))
            if (ready.uncertain) add(uiText(R.string.ui_home_screen_041))
            if (ready.pendingCount > 0) add(uiText(R.string.ui_home_screen_042, MedicationPresentation.easternDigits(ready.pendingCount.toString())))
            if (ready.backupReminderAt?.let { clock.toEpochMilli() >= it } == true) add(uiText(R.string.ui_admin_components_021))
            if (ready.lifecycle == "LOCAL_DRAFT") add(uiText(R.string.ui_pharmacy_admin_screen_038))
        }
        if (!state.online) add(uiText(R.string.ui_home_screen_033))
        if (!ready.isDemo && ready.lifecycle == "SHARED") {
            val last = ready.lastSuccessfulRefreshAt
            if (last == null) add(uiText(R.string.ui_home_screen_034))
            else if (Duration.between(last, clock) >= Duration.ofHours(24)) add(uiText(R.string.ui_home_screen_035))
        }
        if (ready.isDemo) add(uiText(R.string.ui_aqaqeer_app_004))
    }
    if (notices.isEmpty()) return
    var expanded by rememberSaveable(ready.catalog.pharmacy?.pharmacyId) { mutableStateOf(false) }
    Surface(color = if (ready.requiresUpdate || state.refresh.error != null) MaterialTheme.colorScheme.errorContainer
        else if (ready.lastSuccessfulRefreshAt?.let { Duration.between(it, clock) >= Duration.ofHours(24) } == true)
            LocalStatusColors.current.low.container else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).animateContentSize(tween(180))) {
        Column(Modifier.padding(horizontal = UiSpacing.page, vertical = UiSpacing.hair)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                AppIcon(if (ready.requiresUpdate || state.refresh.error != null) UiIcon.WARNING else UiIcon.INFO)
                Text(notices.first() + if (notices.size > 1) uiText(R.string.ui_home_screen_043, MedicationPresentation.easternDigits(notices.size.toString())) else "",
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                RowChevron(down = true)
            }
            if (!expanded && ready.admin && ready.pendingCount > 0 && !notices.first().endsWith(uiText(R.string.ui_home_screen_044)))
                Text(uiText(R.string.ui_home_screen_042, MedicationPresentation.easternDigits(ready.pendingCount.toString())), style = MaterialTheme.typography.bodyMedium)
            if (expanded) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                if (ready.requiresUpdate) InformationCard(uiText(R.string.ui_home_screen_045), uiText(R.string.ui_home_screen_046))
                if (refresh != null && state.refresh.error != null) RefreshControls(state.refresh, refresh)
                if (!state.online) SupportingText(uiText(R.string.ui_home_screen_033))
                if (!ready.isDemo && ready.lifecycle == "SHARED") {
                    if (ready.lastSuccessfulRefreshAt == null) SupportingText(uiText(R.string.ui_home_screen_034))
                    else if (Duration.between(ready.lastSuccessfulRefreshAt, clock) >= Duration.ofHours(24))
                        SupportingText(uiText(R.string.ui_home_screen_035))
                }
                pendingBanner()
                pageHeader?.invoke()
                if (ready.isDemo) DemoNotice()
            }
        }
    }
}

internal fun relativeTime(at: Instant, now: Instant): String {
    val seconds = Duration.between(at, now).seconds
    return when {
        seconds < 0 -> uiText(R.string.ui_home_screen_047)
        seconds < 60 -> uiText(R.string.ui_home_screen_048)
        seconds < 3600 -> uiText(R.string.ui_home_screen_049, MedicationPresentation.easternDigits((seconds / 60).toString()))
        seconds < 86400 -> uiText(R.string.ui_home_screen_050, MedicationPresentation.easternDigits((seconds / 3600).toString()))
        else -> uiText(R.string.ui_home_screen_051, MedicationPresentation.easternDigits((seconds / 86400).toString()))
    }
}
