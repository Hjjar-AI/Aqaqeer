package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.text.BidiFormatter
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

internal fun bidi(value: String): String = BidiFormatter.getInstance(true).unicodeWrap(value)
internal fun statusLabel(status: AvailabilityStatus): String = uiText(when (status) {
    AvailabilityStatus.AVAILABLE -> R.string.status_available
    AvailabilityStatus.LOW -> R.string.status_low
    AvailabilityStatus.OUT -> R.string.status_out
    AvailabilityStatus.UNKNOWN -> R.string.status_unknown
})

@Composable fun AqaqeerApp(viewModel: CatalogViewModel) {
    val state by viewModel.shellState.collectAsStateWithLifecycle()
    AqaqeerTheme(state.theme, accent = state.accent) {
        AppContent(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppContent(state: AppUiState, viewModel: CatalogViewModel) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val contentInsets = ScaffoldDefaults.contentWindowInsets
    val horizontalInsets = with(density) {
        (contentInsets.getLeft(this, direction) + contentInsets.getRight(this, direction)).toDp()
    }
    val dualPane = maxWidth - horizontalInsets >= UiSpacing.listDetailBreakpoint
    fun destinationKey(route: String) = if (dualPane && route.startsWith("med:")) "home" else route
    val homeDestination = destinationKey(state.route) == "home"
    val connectionImport by viewModel.connectionImport.collectAsStateWithLifecycle()
    val availabilitySavedId by viewModel.availabilitySavedId.collectAsStateWithLifecycle()
    val adminBusy by viewModel.adminBusy.collectAsStateWithLifecycle()
    val adminMessage by viewModel.adminMessage.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.status.collectAsStateWithLifecycle()
    val configured = (state.catalog as? CatalogState.Ready)?.catalog?.pharmacy != null
    val medicationRouteId = when {
        state.route.startsWith("med:") -> state.route.removePrefix("med:")
        state.route.startsWith("edit-med:") -> state.route.removePrefix("edit-med:").takeUnless { it == "new" }
        else -> null
    }
    val missingMedication = state.catalog is CatalogState.Ready && medicationRouteId != null &&
        state.catalog.catalog.medications.none { it.id == medicationRouteId }
    LaunchedEffect(state.route, configured, missingMedication) {
        if ((state.route == "connection" && configured) || missingMedication) viewModel.navigate("home")
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refreshEligible = configured && !state.catalog.isDemo && !state.catalog.restorePaused && !state.catalog.maintenancePaused && state.catalog.lifecycle == "SHARED"
    DisposableEffect(lifecycle, refreshEligible) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { viewModel.onForeground(true); if (refreshEligible) viewModel.refreshCatalog(automatic = true) }
            if (event == Lifecycle.Event.ON_STOP) viewModel.onForeground(false)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { viewModel.onForeground(true); if (refreshEligible) viewModel.refreshCatalog(automatic = true) }
        onDispose { lifecycle.removeObserver(observer); viewModel.onForeground(false) }
    }
    val navigate = viewModel::navigate
    val back = viewModel::back
    val now = rememberPresentationDate((state.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.timezone ?: "Asia/Damascus")
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    LaunchedEffect(feedback?.id, state.route) {
        val message = feedback?.takeIf { it.route == state.route } ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.message, actionLabel = when {
            message.undo != null -> uiText(R.string.ui_aqaqeer_app_001); message.medicationId != null -> uiText(R.string.ui_aqaqeer_app_002); else -> null
        })
        if (result == SnackbarResult.ActionPerformed && viewModel.state.value.route == message.route) {
            message.undo?.let(viewModel::undo)
            message.medicationId?.let { navigate("med:${it}") }
        }
        viewModel.feedback.compareAndSet(message, null)
    }
    val readyForTitle = state.catalog as? CatalogState.Ready
    val titleMedication = state.route.takeIf { it.startsWith("med:") }?.removePrefix("med:")
        ?.let { id -> readyForTitle?.catalog?.medications?.find { it.id == id } }
    val title = when {
        titleMedication != null && !dualPane -> bidi(MedicationPresentation.identity(titleMedication))
        homeDestination -> readyForTitle?.catalog?.pharmacy?.name ?: screenTitle(state.route)
        else -> screenTitle(state.route)
    }
    val medicationActions: @Composable (CatalogState.Ready, Medication) -> Unit = { ready, medication ->
        val editable = ready.admin && !ready.requiresUpdate && !ready.maintenancePaused
        var detailMenuOpen by remember(medication.id) { mutableStateOf(false) }
        if (editable) IconButton(enabled = !adminBusy,
            onClick = { navigate("edit-med:${medication.id}") }) {
            AppIcon(UiIcon.EDIT, description = uiText(R.string.ui_aqaqeer_app_012))
        }
        if (ready.admin) Box {
            IconButton(onClick = { detailMenuOpen = true }) { AppIcon(UiIcon.MORE, description = uiText(R.string.ui_aqaqeer_app_013)) }
            DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {
                DropdownMenuItem(text = { Text(uiText(R.string.ui_aqaqeer_app_014)) },
                    leadingIcon = { AppIcon(UiIcon.HISTORY) },
                    onClick = { detailMenuOpen = false; viewModel.openHistory(medication.id) })
            }
        }
    }
    val pharmacyId = readyForTitle?.catalog?.pharmacy?.pharmacyId
    val drawerEligible = homeDestination && configured && readyForTitle != null
    var homeModalOpen by remember { mutableStateOf(false) }
    var dataTimesOpen by rememberSaveable(pharmacyId) { mutableStateOf(false) }
    val drawerState = remember(pharmacyId) {
        DrawerState(DrawerValue.Closed, confirmStateChange = { target ->
            target != DrawerValue.Open || (!homeModalOpen && !dataTimesOpen)
        })
    }
    val destinationStates = key(pharmacyId) { rememberSaveableStateHolder() }
    val focus = LocalFocusManager.current
    val latestPharmacyId by rememberUpdatedState(pharmacyId)
    val latestDrawerEligible by rememberUpdatedState(drawerEligible)
    fun afterDrawer(action: () -> Unit) {
        val origin = pharmacyId
        focus.clearFocus()
        scope.launch {
            drawerState.close()
            if (latestDrawerEligible && origin == latestPharmacyId && !homeModalOpen && !dataTimesOpen) action()
        }
    }
    LaunchedEffect(drawerEligible, homeModalOpen, dataTimesOpen) {
        if (!drawerEligible || homeModalOpen || dataTimesOpen) drawerState.close()
        if (!drawerEligible) { dataTimesOpen = false; homeModalOpen = false }
    }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val backAllowed by rememberUpdatedState(!adminBusy && !homeModalOpen && !dataTimesOpen && !drawerState.isOpen)
    PredictiveBackHandler(enabled = state.route != "home" && backAllowed) { progress ->
        val origin = viewModel.state.value.route
        val animate = android.animation.ValueAnimator.areAnimatorsEnabled()
        try {
            progress.collect { backProgress = if (animate) it.progress else 0f }
            if (backAllowed && viewModel.state.value.route == origin) back()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // A cancelled gesture leaves the route and its browsing state intact.
        } finally { backProgress = 0f }
    }
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
    if (dataTimesOpen && drawerEligible && !homeModalOpen)
        DataTimesDialog(readyForTitle, now, refresh = if (!readyForTitle.isDemo && readyForTitle.lifecycle == "SHARED" && !readyForTitle.restorePaused && !readyForTitle.maintenancePaused) ({ viewModel.refreshCatalog() }) else null, busy = state.refresh.busy) { dataTimesOpen = false }
    ModalNavigationDrawer(drawerState = drawerState,
        gesturesEnabled = drawerEligible && !homeModalOpen && !dataTimesOpen,
        drawerContent = {
            DrawerFiltersContent(viewModel) { filters -> HomeDrawer(readyForTitle, filters, adminBusy || sharing.busy,
                openRoute = { route -> afterDrawer { if (route != state.route) navigate(route) } },
                chooseCategory = { id -> afterDrawer { viewModel.setQuickCategory(id) } },
                showDataTimes = { afterDrawer { dataTimesOpen = true } }) }
        }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                val homeBarHeight = with(LocalDensity.current) {
                    MaterialTheme.typography.titleLarge.lineHeight.toDp() +
                        MaterialTheme.typography.labelMedium.lineHeight.toDp() + UiSpacing.related
                }.coerceAtLeast(UiSpacing.touch)
                TopAppBar(title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                        if (homeDestination && configured) BrandEmblem(Modifier.size(32.dp), description = null)
                        Column(Modifier.weight(1f).then(if (drawerEligible) Modifier
                            .heightIn(min = UiSpacing.touch)
                            .clickable(enabled = !homeModalOpen && !dataTimesOpen,
                                onClickLabel = uiText(R.string.ui_aqaqeer_app_003)) {
                                afterDrawer { dataTimesOpen = true }
                            } else Modifier), verticalArrangement = Arrangement.Center) {
                            if (titleMedication != null && !dualPane) {
                                Text(bidi(MedicationPresentation.scientificName(titleMedication)), fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("screen_title"))
                                Text(bidi(MedicationPresentation.westernDigits(titleMedication.strength)), style = MaterialTheme.typography.labelLarge)
                            } else Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("screen_title"))
                            if (drawerEligible) {
                                val ready = readyForTitle
                                val timeLabel = when {
                                    ready.isDemo -> uiText(R.string.ui_aqaqeer_app_004)
                                    ready.lifecycle == "LOCAL_DRAFT" -> uiText(R.string.ui_aqaqeer_app_005)
                                    else -> uiText(R.string.ui_aqaqeer_app_006) + (ready.publishedAt?.let {
                                        bidi(MedicationPresentation.timestamp(it, ready.catalog.pharmacy?.timezone ?: "Asia/Damascus"))
                                    } ?: uiText(R.string.ui_aqaqeer_app_007))
                                }
                                Text(timeLabel, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }, navigationIcon = {
                    if (homeDestination) IconButton(
                        enabled = !homeModalOpen && !dataTimesOpen,
                        onClick = {
                            if (!homeModalOpen && !dataTimesOpen) {
                                if (drawerEligible) { focus.clearFocus(); scope.launch { drawerState.open() } }
                                else navigate("settings")
                            }
                        }, modifier = Modifier.testTag("home_menu")) {
                        AppIcon(if (drawerEligible) UiIcon.MENU else UiIcon.SETTINGS, description = if (drawerEligible) uiText(R.string.ui_aqaqeer_app_008) else uiText(R.string.ui_screen_titles_012))
                    }
                    if (!homeDestination) IconButton(enabled = !adminBusy, onClick = back, modifier = Modifier.testTag("back")) {
                        AppIcon(UiIcon.BACK, description = uiText(R.string.ui_aqaqeer_app_009))
                    }
                }, actions = {
                    if (drawerEligible && readyForTitle.admin && (readyForTitle.pendingCount > 0 || readyForTitle.uncertain || sharing.busy)) {
                        TextButton(enabled = !adminBusy, onClick = viewModel::review) {
                            Text(uiText(R.string.ui_aqaqeer_app_011, MedicationPresentation.displayDigits(readyForTitle.pendingCount.toString())))
                        }
                    }
                    if (!dualPane && readyForTitle != null && titleMedication != null)
                        medicationActions(readyForTitle, titleMedication)
                }, expandedHeight = if (homeDestination && configured || titleMedication != null) homeBarHeight else TopAppBarDefaults.TopAppBarExpandedHeight,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { insets ->
            ScreenFrame(wide = state.route == "home" || state.route.startsWith("med:"), form = state.route in setOf("create", "promote", "leave", "setup", "export-connection", "connection", "backup", "maintenance", "taxonomy")
                || state.route.startsWith("edit-med:"),
                modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding()) {
                AnimatedContent(targetState = state, modifier = Modifier.graphicsLayer {
                    scaleX = 1f - 0.04f * backProgress
                    scaleY = scaleX
                    alpha = 1f - 0.12f * backProgress
                }, contentKey = { destinationKey(it.route) }, label = "route",
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) }) { displayed ->
                val destinationContent: @Composable () -> Unit = {
                val state = displayed
                if (state.catalog is CatalogState.RecoveryRequired && state.route !in setOf("settings", "about")) {
                    RecoveryScreen(viewModel::retryProtectedStorage) { navigate("settings") }
                } else when (state.route) {
                    "connection" -> ConnectionImportScreen(connectionImport, state.catalog,
                        { viewModel.openConnectionImport(it) }, viewModel::stageConnection,
                        viewModel::cancelConnectionImport, viewModel::discardPendingConnection,
                        viewModel::reloadConnectionImport, viewModel::verifyPendingConnection, viewModel::activatePendingConnection)
                    "settings" -> SettingsScreen(state.theme, viewModel::setTheme, state.homePreferences, viewModel::setSort,
                        viewModel::setLayout, viewModel::setNames, viewModel::setSecondaryHijri,
                        state.accent, viewModel::setAccent, viewModel::setTwoColumns,
                        viewModel::setSectionsAtBottom, viewModel::setSearchAtBottom, viewModel::setGroupingEnabled, viewModel::setGrouping,
                        if (state.catalog is CatalogState.Ready && !state.catalog.isDemo && !configured) ({ navigate("backup") }) else null,
                        null,
                        null, demo = BuildConfig.IS_DEMO, demoAdmin = (state.catalog as? CatalogState.Ready)?.admin == true,
                        changeDemoAdmin = viewModel::setDemoAdmin, resetDemo = viewModel::resetDemo) { navigate("about") }
                    "maintenance" -> MaintenanceScreen(state.catalog as? CatalogState.Ready, viewModel)
                    "backup" -> BackupScreen(state, viewModel)
                    "csv-import" -> CsvImportScreen(state.catalog as? CatalogState.Ready, viewModel)
                    "reports" -> ReportsScreen(state.catalog as? CatalogState.Ready, viewModel)
                    "about" -> AboutScreen {
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO,
                                "mailto:hjjarmhmmdali@gmail.com".toUri()))
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar(uiText(R.string.ui_aqaqeer_app_015)) }
                        } catch (_: SecurityException) {
                            scope.launch { snackbar.showSnackbar(uiText(R.string.ui_aqaqeer_app_016)) }
                        }
                    }
                    "create", "admin", "setup", "review", "taxonomy", "promote", "leave", "export-connection" -> PharmacyAdminScreen(state, viewModel)
                    else -> if (state.route == "history" || state.route.startsWith("history:")) HistoryScreen(
                        state.catalog as? CatalogState.Ready,
                        state.route.removePrefix("history:").takeIf { state.route.startsWith("history:") }, viewModel,
                        showHijri = state.homePreferences.secondaryHijri)
                    else if (state.route.startsWith("edit-med:")) PharmacyAdminScreen(state, viewModel)
                    else when (val catalogState = state.catalog) {
                        CatalogState.Loading -> LoadingScreen()
                        CatalogState.RecoveryRequired -> RecoveryScreen(viewModel::retryProtectedStorage) { navigate("settings") }
                        is CatalogState.Ready -> {
                            val selectedMedication = state.route.removePrefix("med:").takeIf { state.route.startsWith("med:") }
                            val medication = catalogState.catalog.medications.firstOrNull { it.id == selectedMedication }
                            val showReminder = catalogState.backupReminderAt != null
                            val pageHeader: (@Composable () -> Unit)? = if (catalogState.maintenancePaused || catalogState.restorePaused ||
                                showReminder || catalogState.admin && !catalogState.isDemo && catalogState.lifecycle == "LOCAL_DRAFT") ({
                                if (catalogState.maintenancePaused) {
                                    InformationCard(uiText(R.string.ui_aqaqeer_app_017), uiText(R.string.ui_aqaqeer_app_018))
                                    NavigationCard(uiText(R.string.ui_aqaqeer_app_019), { navigate("maintenance") })
                                }
                                if (catalogState.restorePaused) {
                                    InformationCard(uiText(R.string.ui_aqaqeer_app_020), uiText(R.string.ui_aqaqeer_app_021))
                                    NavigationCard(uiText(R.string.ui_aqaqeer_app_022), { navigate("backup") })
                                }
                                if (showReminder) DueBackupReminder(catalogState,
                                    { navigate("backup") }, { viewModel.command { viewModel.backups.dismissReminder() } })
                                if (catalogState.admin && !catalogState.isDemo && catalogState.lifecycle == "LOCAL_DRAFT")
                                    SupportingText(uiText(R.string.ui_pharmacy_admin_screen_038))
                            }) else null
                            Box(Modifier.fillMaxSize()) {
                            val browseContent: @Composable () -> Unit = {
                            HomeStateContent(viewModel) { state, browse, highlighted -> HomeScreen(catalogState, state, now, viewModel::selectSection, viewModel::setQuery,
                                viewModel::toggleStatus, viewModel::toggleCategory, viewModel::clearFilters,
                                { navigate("med:${it}") }, { navigate("settings") }, { viewModel.openConnectionImport() },
                                if (catalogState.isDemo || catalogState.restorePaused || catalogState.maintenancePaused || catalogState.lifecycle != "SHARED") null else ({ viewModel.refreshCatalog() }),
                                { navigate("create") }, { navigate("backup") }, adminBusy, adminMessage,
                                saveAvailability = { selection, status, complete ->
                                    viewModel.saveAvailability(selection, BulkMedicationChanges(status = status), complete)
                                }, pageHeader = pageHeader, openAddMedication = { viewModel.newMedicationName = ""; navigate("edit-med:new") },
                                availabilitySavedId = availabilitySavedId, publicationBusy = sharing.busy,
                                publicationBlockReason = publicationBlockedReason(catalogState, sharing, adminBusy || sharing.busy),
                                onModalVisibilityChanged = { homeModalOpen = it },
                                availabilityInProgress = { viewModel.adminBusy.value },
                                canOpenHomeModal = { !dataTimesOpen && !drawerState.isOpen },
                                setGroupingEnabled = viewModel::setGroupingEnabled, setGrouping = viewModel::setGrouping,
                                saveBulk = viewModel::saveAvailability, projection = browse, highlightedMedication = highlighted,
                                prefillMedication = { query -> viewModel.newMedicationName = query; navigate("edit-med:new") },
                                toggleExtraFilter = viewModel::toggleExtraFilter,
                                toggleAvailableFilter = viewModel::toggleAvailableFilter,
                                editMedication = { id -> navigate("edit-med:$id") }) }
                            }
                            val detailContent: @Composable () -> Unit = {
                                if (medication != null) MedicationScreen(catalogState, medication, now,
                                    state.homePreferences.secondaryHijri, pageHeader, viewModel)
                                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(uiText(R.string.ui_aqaqeer_app_023)) }
                            }
                            HomeDetailLayout(dualPane = dualPane, showDetail = medication != null,
                                list = browseContent, detail = detailContent,
                                detailHeader = if (medication != null) ({
                                        Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.related),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                                            IconButton(enabled = !adminBusy && !homeModalOpen && !dataTimesOpen,
                                                onClick = back, modifier = Modifier.testTag("back")) {
                                                AppIcon(UiIcon.BACK, description = uiText(R.string.ui_aqaqeer_app_009))
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(bidi(MedicationPresentation.scientificName(medication)),
                                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                Text(bidi(MedicationPresentation.westernDigits(medication.strength)),
                                                    style = MaterialTheme.typography.labelLarge)
                                            }
                                            medicationActions(catalogState, medication)
                                        }
                                }) else null)

                            }

                        }
                    }
                }
                }
                val displayedDestination = destinationKey(displayed.route)
                if (displayedDestination == "home" || displayed.route.startsWith("edit-med:"))
                    destinationStates.SaveableStateProvider(viewModel.destinationStateKey(displayedDestination), destinationContent)
                else destinationContent()
                }
            }
        }
    }
}

@Composable internal fun DemoNotice() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.page, vertical = UiSpacing.related)) {
            Text(uiText(R.string.ui_aqaqeer_app_024), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("demo_notice"))
            Text(uiText(R.string.ui_aqaqeer_app_025), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun MedicationScreen(ready: CatalogState.Ready, medication: Medication, now: java.time.Instant, showHijri: Boolean,
    pageHeader: (@Composable () -> Unit)?, vm: CatalogViewModel) {
    val catalog = ready.catalog
    var editor by rememberSaveable(catalog.pharmacy?.pharmacyId, medication.id, stateSaver = AvailabilitySelectionSaver) { mutableStateOf<AvailabilitySelection?>(null) }
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val canEdit = ready.admin && !ready.requiresUpdate && !ready.maintenancePaused
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
                        Text(section.nameAr + " · " + statusLabel(catalog.status(medication.id, section.id)))
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
                Text(section.nameAr + if (section.isActive) "" else uiText(R.string.ui_aqaqeer_app_030), fontWeight = FontWeight.Bold)
                EditableStatusPill(catalog.status(medication.id, section.id), onOpen = if (canEdit && section.isActive && !busy) ({
                    editor = AvailabilitySelection(requireNotNull(catalog.pharmacy).pharmacyId, section, listOf(medication),
                        mapOf(medication.id to catalog.status(medication.id, section.id)), false)
                }) else null, expanded = captured != null, dismiss = { if (!busy) editor = null }, enabled = !busy && (captured == null || current),
                    choose = { status -> if (current && !vm.adminBusy.value) vm.saveAvailability(captured, BulkMedicationChanges(status = status)) { if (editor == captured) editor = null } })
                if (record == null) SupportingText(uiText(R.string.ui_aqaqeer_app_031))
                else {
                    SupportingText(relativeTime(record.updatedAt, now))
                    CollapsibleGroup(uiText(R.string.ui_history_components_021), expanded, { expanded = !expanded }) {
                        SupportingText(bidi(MedicationPresentation.timestamp(record.updatedAt, catalog.pharmacy?.timezone ?: "Asia/Damascus")))
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
                    Text(uiText(R.string.ui_medication_components_002) + ": " + bidi(MedicationPresentation.names(medication.nameAr, medication.nameEn, NameDisplay.BOTH).joinToString(" · ") { it.text }))
                LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_074), catalog.formsById[medication.formId]?.nameAr.orEmpty())
                LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_073), catalog.categoriesById[medication.categoryId]?.nameAr.orEmpty())
                medication.expiryDate?.let { LabeledValue(uiText(R.string.ui_aqaqeer_app_028), bidi(it.toString())); SecondaryHijriDate(it, showHijri) }
                ExpiryBadge(medication, MedicationPresentation.today(now, catalog.pharmacy?.timezone ?: "Asia/Damascus"))
                medication.brand?.let { LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_069), bidi(it)) }
            }
        }
        medication.note?.let { note -> item { InformationCard(uiText(R.string.ui_aqaqeer_app_032), note) } }
    }
}

@Composable private fun SettingsScreen(theme: ThemeChoice, setTheme: (ThemeChoice) -> Unit,
    preferences: HomePreferences, setSort: (HomeSort) -> Unit, setLayout: (CardLayout) -> Unit,
    setNames: (NameDisplay) -> Unit, setSecondaryHijri: (Boolean) -> Unit,
    accent: ThemeAccent, setAccent: (ThemeAccent) -> Unit, setTwoColumns: (Boolean) -> Unit,
    setSectionsAtBottom: (Boolean) -> Unit, setSearchAtBottom: (Boolean) -> Unit,
    setGroupingEnabled: (Boolean) -> Unit, setGrouping: (HomeGrouping) -> Unit,
    openBackup: (() -> Unit)? = null, openMaintenance: (() -> Unit)? = null, openLeave: (() -> Unit)? = null,
    demo: Boolean = false, demoAdmin: Boolean = false, changeDemoAdmin: (Boolean) -> Unit = {}, resetDemo: () -> Unit = {},
    openAbout: () -> Unit) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    LazyColumn(Modifier.fillMaxSize().testTag("settings"), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
        if (demo) item(key = "demo_mode") { DemoModeControls(demoAdmin, changeDemoAdmin, resetDemo) }
        item { SettingsGroup(uiText(R.string.review_appearance), initiallyExpanded = true) {
            SettingsChoiceRows(uiText(R.string.ui_aqaqeer_app_034), ThemeChoice.entries, theme, setTheme, {
                uiText(when (it) { ThemeChoice.SYSTEM -> R.string.ui_aqaqeer_app_035; ThemeChoice.LIGHT -> R.string.ui_aqaqeer_app_036
                    ThemeChoice.DARK -> R.string.ui_aqaqeer_app_037; ThemeChoice.AMOLED -> R.string.ui_aqaqeer_app_038 })
            }, choiceModifier = { Modifier.testTag("theme_${it.name}") })
            SettingsChoiceRows(uiText(R.string.ui_aqaqeer_app_039), ThemeAccent.entries, accent, setAccent, {
                uiText(when (it) { ThemeAccent.GOLD -> R.string.ui_aqaqeer_app_040; ThemeAccent.BLUE -> R.string.ui_aqaqeer_app_041
                    ThemeAccent.GREEN -> R.string.ui_aqaqeer_app_042; ThemeAccent.PURPLE -> R.string.ui_aqaqeer_app_043; ThemeAccent.CRIMSON -> R.string.ui_aqaqeer_app_044 })
            }, swatch = { appColorScheme(theme, it, systemDark).primary })
        } }
        item { SettingsGroup(uiText(R.string.review_list_display)) {
            SettingsChoiceRows(uiText(R.string.ui_aqaqeer_app_045), HomeSort.entries, preferences.sort, setSort, label = {
                uiText(when (it) { HomeSort.SCIENTIFIC_NAME -> R.string.ui_aqaqeer_app_046; HomeSort.ARABIC_NAME -> R.string.ui_aqaqeer_app_047
                    HomeSort.ENGLISH_NAME -> R.string.ui_aqaqeer_app_048; HomeSort.AVAILABILITY -> R.string.ui_aqaqeer_app_049 })
            }, modifier = Modifier.testTag("sort_preference"), choiceModifier = { Modifier.testTag("sort_${it.name}") })
            ContentSection {
                CardLayoutPicker(preferences.layout, setLayout)
                MedicationLayoutPreview(preferences.layout)
            }
            SwitchChoiceRow(preferences.twoColumns, uiText(R.string.review_adaptive_columns), uiText(R.string.review_adaptive_columns_help), Modifier.testTag("two_columns"), setTwoColumns)
            ContentSection {
                SwitchChoiceRow(preferences.groupingEnabled, uiText(R.string.ui_home_screen_025), uiText(R.string.ui_home_screen_026), change = setGroupingEnabled)
                if (preferences.groupingEnabled) SettingsChoiceRows(uiText(R.string.ui_home_screen_027), HomeGrouping.entries,
                    preferences.grouping, setGrouping, label = { uiText(if (it == HomeGrouping.CATEGORY) R.string.ui_pharmacy_admin_screen_073 else R.string.ui_home_screen_028) },
                    modifier = Modifier.testTag("grouping_preference"), choiceModifier = { Modifier.testTag("grouping_${it.name}") })
            }
            SettingsChoiceRows(uiText(R.string.ui_aqaqeer_app_056), NameDisplay.entries, preferences.names, setNames, label = {
                uiText(when (it) { NameDisplay.ARABIC -> R.string.ui_aqaqeer_app_057; NameDisplay.ENGLISH -> R.string.ui_aqaqeer_app_058; NameDisplay.BOTH -> R.string.ui_aqaqeer_app_059 })
            }, modifier = Modifier.testTag("names_preference"), choiceModifier = { Modifier.testTag("names_${it.name}") })
        } }
        item { SettingsGroup(uiText(R.string.review_dates_placement)) {
            SwitchChoiceRow(preferences.sectionsAtBottom, uiText(R.string.home_sections_bottom), uiText(R.string.home_sections_bottom_help), Modifier.testTag("sections_at_bottom"), setSectionsAtBottom)
            SwitchChoiceRow(preferences.searchAtBottom, uiText(R.string.home_search_bottom), uiText(R.string.home_search_bottom_help), Modifier.testTag("search_at_bottom"), setSearchAtBottom)
            ContentSection {
                SwitchChoiceRow(preferences.secondaryHijri, uiText(R.string.ui_aqaqeer_app_060), uiText(R.string.ui_aqaqeer_app_061), Modifier.testTag("secondary_hijri"), setSecondaryHijri)
                ExpandableInformation(uiText(R.string.ui_aqaqeer_app_063), uiText(R.string.ui_aqaqeer_app_064))
            }
        } }
        openBackup?.let { item { NavigationCard(uiText(R.string.ui_screen_titles_011), it) } }
        item { NavigationCard(uiText(R.string.ui_screen_titles_013), openAbout, Modifier.testTag("open_about")) }
    }
}

@Composable private fun AboutScreen(contact: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("about"), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section), horizontalAlignment = Alignment.CenterHorizontally) {
        item { BrandEmblem(Modifier.size(160.dp)) }
        item { ExpandableInformation(uiText(R.string.review_legend), uiText(R.string.review_legend_body) + "\n" + uiText(R.string.review_changed) + ": " + uiText(R.string.review_changed_help)) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiText(R.string.ui_screen_titles_020), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(bidi(BuildConfig.VERSION_NAME), fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (BuildConfig.IS_DEMO) Text(uiText(R.string.ui_aqaqeer_app_068), color = MaterialTheme.colorScheme.secondary)
            }
        }
        item { InformationCard(uiText(R.string.ui_aqaqeer_app_069), uiText(R.string.ui_aqaqeer_app_070)) }
        item {
            GroupedCard(title = uiText(R.string.ui_aqaqeer_app_071)) {
                Text(uiText(R.string.ui_aqaqeer_app_072), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(bidi("hjjarmhmmdali@gmail.com"), fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.testTag("developer_email"))
                OutlinedButton(onClick = contact, modifier = Modifier.testTag("email_contact")) { Text(uiText(R.string.ui_aqaqeer_app_073)) }
            }
        }
        item { InformationCard(uiText(R.string.ui_aqaqeer_app_074), uiText(R.string.ui_aqaqeer_app_075)) }
    }
}

@Composable internal fun WelcomeScreen(openSettings: () -> Unit, openConnection: (() -> Unit)? = null, openCreate: (() -> Unit)? = null, openRestore: (() -> Unit)? = null) {
    val compactHeight = viewportHeight(1f) < 640.dp || LocalDensity.current.fontScale > 1.4f
    Column(Modifier.fillMaxWidth().padding(vertical = UiSpacing.section), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Text(uiText(R.string.ui_aqaqeer_app_076), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        BrandEmblem(Modifier.size(if (compactHeight) 120.dp else 160.dp))
        openConnection?.let { action ->
            GroupedCard(title = uiText(R.string.ui_aqaqeer_app_077)) {
                Text(uiText(R.string.ui_aqaqeer_app_078))
                Button(onClick = action) { Text(uiText(R.string.ui_aqaqeer_app_079)) }
            }
        }
        openCreate?.let { action ->
            GroupedCard(title = uiText(R.string.ui_aqaqeer_app_080)) {
                Text(uiText(R.string.ui_aqaqeer_app_081))
                OutlinedButton(onClick = action, modifier = Modifier.testTag("create_pharmacy")) { Text(uiText(R.string.ui_screen_titles_001)) }
            }
        }
        ActionGroup {
            openRestore?.let { action -> TextButton(onClick = action) { Text(uiText(R.string.ui_aqaqeer_app_082)) } }
            TextButton(onClick = openSettings) { Text(uiText(R.string.ui_aqaqeer_app_083)) }
        }
    }
}

@Composable private fun LoadingScreen() {
    Column(Modifier.fillMaxSize().padding(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Text(uiText(R.string.ui_aqaqeer_app_084), style = MaterialTheme.typography.bodyMedium)
        CircularProgressIndicator()
    }
}

@Composable private fun RecoveryScreen(retry: () -> Unit, openSettings: () -> Unit) {
    FormPage {
        Text(uiText(R.string.ui_aqaqeer_app_085), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionHeading(uiText(R.string.ui_aqaqeer_app_086))
        Text(uiText(R.string.ui_aqaqeer_app_087))
        Button(onClick = retry) { Text(uiText(R.string.ui_history_screen_006)) }
        Text(uiText(R.string.ui_aqaqeer_app_088))
        NavigationCard(uiText(R.string.ui_aqaqeer_app_089), openSettings)
    }
}

@Composable internal fun InformationCard(title: String, body: String) {
    GroupedCard(title = title) { SupportingText(body) }
}

@Composable private fun HomeStateContent(vm: CatalogViewModel,
    content: @Composable (AppUiState, HomeBrowseResult, String?) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val browse by vm.homeBrowse.collectAsStateWithLifecycle()
    val highlighted by vm.highlightedMedication.collectAsStateWithLifecycle()
    LaunchedEffect(highlighted) { if (highlighted != null) { kotlinx.coroutines.delay(2500); vm.highlightedMedication.compareAndSet(highlighted, null) } }
    content(state, browse, highlighted)
}



@Composable private fun DueBackupReminder(ready: CatalogState.Ready, backup: () -> Unit, dismiss: () -> Unit) {
    val now = rememberPresentationTime(ready.catalog.pharmacy?.timezone ?: "Asia/Damascus")
    if (ready.backupReminderAt?.let { now.toEpochMilli() >= it } == true) BackupReminderCard(backup, dismiss)
}

@Composable private fun DrawerFiltersContent(vm: CatalogViewModel, content: @Composable (HomeFilters) -> Unit) {
    val filters by vm.drawerFilters.collectAsStateWithLifecycle()
    content(filters)
}
