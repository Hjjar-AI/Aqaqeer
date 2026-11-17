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
internal fun statusLabel(status: AvailabilityStatus, text: UiText? = null): String = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    uiText(when (status) {
    AvailabilityStatus.AVAILABLE -> R.string.status_available
    AvailabilityStatus.LOW -> R.string.status_low
    AvailabilityStatus.OUT -> R.string.status_out
    AvailabilityStatus.UNKNOWN -> R.string.status_unknown
})
}

@Composable fun AqaqeerApp(viewModel: CatalogViewModel) {
    val state by viewModel.shellState.collectAsStateWithLifecycle()
    AqaqeerTheme(state.theme, accent = state.accent) {
        CompositionLocalProvider(LocalNoticeScope provides ((state.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.pharmacyId ?: "unconfigured")) {
            AppContent(state, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppContent(state: AppUiState, viewModel: CatalogViewModel) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val uiText = rememberUiText()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val contentInsets = ScaffoldDefaults.contentWindowInsets
    val horizontalInsets = with(density) {
        (contentInsets.getLeft(this, direction) + contentInsets.getRight(this, direction)).toDp()
    }
    val dualPane = maxWidth - horizontalInsets >= UiSpacing.listDetailBreakpoint
    fun destinationKey(route: Route): Route = if (dualPane && route is Route.Medication) Route.Home else route
    val homeDestination = destinationKey(state.destination) == Route.Home
    val connectionImport by viewModel.connectionImport.collectAsStateWithLifecycle()
    val availabilitySavedId by viewModel.availabilitySavedId.collectAsStateWithLifecycle()
    val adminBusy by viewModel.adminBusy.collectAsStateWithLifecycle()
    val adminMessage by viewModel.adminMessage.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.status.collectAsStateWithLifecycle()
    val configured = (state.catalog as? CatalogState.Ready)?.catalog?.pharmacy != null
    val medicationRouteId = state.destination.medicationId
    val missingMedication = state.catalog is CatalogState.Ready && medicationRouteId != null &&
        state.catalog.catalog.medications.none { it.id == medicationRouteId }
    LaunchedEffect(state.destination, configured, missingMedication) {
        if ((state.destination == Route.Connection && configured) || missingMedication) viewModel.navigate(Route.Home)
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
    val navigate: (Route) -> Unit = viewModel::navigate
    val back = viewModel::back
    val now = rememberPresentationDate((state.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.timezone ?: "Asia/Damascus")
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    LaunchedEffect(feedback?.id, state.destination) {
        val message = feedback?.takeIf { it.destination == state.destination } ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.message, actionLabel = when {
            message.undo != null -> uiText(R.string.shared_undo_action); message.medicationId != null -> uiText(R.string.shared_view_action); else -> null
        }, withDismissAction = true, duration = if (message.undo != null || message.medicationId != null)
            SnackbarDuration.Long else SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed && viewModel.state.value.destination == message.destination) {
            message.undo?.let(viewModel::undo)
            message.medicationId?.let { navigate(Route.Medication(it)) }
        }
        viewModel.feedback.compareAndSet(message, null)
    }
    val readyForTitle = state.catalog as? CatalogState.Ready
    val titleMedication = (state.destination as? Route.Medication)?.id
        ?.let { id -> readyForTitle?.catalog?.medications?.find { it.id == id } }
    val title = when {
        titleMedication != null && !dualPane -> bidi(MedicationPresentation.identity(titleMedication))
        homeDestination -> readyForTitle?.catalog?.pharmacy?.name ?: uiText(screenTitleResource(state.destination))
        else -> uiText(screenTitleResource(state.destination))
    }
    val medicationActions: @Composable (CatalogState.Ready, Medication) -> Unit = { ready, medication ->
        val editable = ready.admin && !ready.requiresUpdate && !ready.maintenancePaused
        var detailMenuOpen by remember(medication.id) { mutableStateOf(false) }
        if (editable) IconButton(enabled = !adminBusy,
            onClick = { navigate(Route.EditMedication(medication.id)) }) {
            AppIcon(UiIcon.EDIT, description = uiText(R.string.catalog_edit_medication_availability_description))
        }
        if (ready.admin) Box {
            IconButton(onClick = { detailMenuOpen = true }) { AppIcon(UiIcon.MORE, description = uiText(R.string.catalog_medication_options_description)) }
            DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {
                DropdownMenuItem(text = { Text(uiText(R.string.history_medication_history_action)) },
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
    PredictiveBackHandler(enabled = state.destination != Route.Home && backAllowed) { progress ->
        val origin = viewModel.state.value.destination
        val animate = android.animation.ValueAnimator.areAnimatorsEnabled()
        try {
            progress.collect { backProgress = if (animate) it.progress else 0f }
            if (backAllowed && viewModel.state.value.destination == origin) back()
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
                openRoute = { route -> afterDrawer { if (route != state.destination) navigate(route) } },
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
                        if (homeDestination && configured) BrandEmblem(Modifier.size(Space.xxl), description = null)
                        Column(Modifier.weight(1f).then(if (drawerEligible) Modifier
                            .heightIn(min = UiSpacing.touch)
                            .clickable(enabled = !homeModalOpen && !dataTimesOpen,
                                onClickLabel = uiText(R.string.home_data_times_description)) {
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
                                    ready.isDemo -> uiText(R.string.home_demo_data_label)
                                    ready.lifecycle == "LOCAL_DRAFT" -> uiText(R.string.publication_unpublished_local_draft_label)
                                    else -> uiText(R.string.home_data_time_prefix) + (ready.publishedAt?.let {
                                        bidi(MedicationPresentation.timestamp(it, ready.catalog.pharmacy?.timezone ?: "Asia/Damascus"))
                                    } ?: uiText(R.string.publication_no_verified_publication_message))
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
                                else navigate(Route.Settings)
                            }
                        }, modifier = Modifier.testTag("home_menu")) {
                        AppIcon(if (drawerEligible) UiIcon.MENU else UiIcon.SETTINGS, description = if (drawerEligible) uiText(R.string.navigation_open_drawer_description) else uiText(R.string.navigation_settings_title))
                    }
                    if (!homeDestination) IconButton(enabled = !adminBusy, onClick = back, modifier = Modifier.testTag("back")) {
                        AppIcon(UiIcon.BACK, description = uiText(R.string.navigation_back_description))
                    }
                }, actions = {
                    if (drawerEligible && readyForTitle.admin && (readyForTitle.pendingCount > 0 || readyForTitle.uncertain || sharing.busy)) {
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !adminBusy, onClick = viewModel::review) {
                            Text(uiText(R.string.publication_toolbar_pending_count_format, MedicationPresentation.displayDigits(readyForTitle.pendingCount.toString())))
                        }
                    }
                    if (!dualPane && readyForTitle != null && titleMedication != null)
                        medicationActions(readyForTitle, titleMedication)
                }, expandedHeight = if (homeDestination && configured || titleMedication != null) homeBarHeight else TopAppBarDefaults.TopAppBarExpandedHeight,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { insets ->
            ScreenFrame(wide = state.destination.isWide, form = state.destination.isForm,
                modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding()) {
                AnimatedContent(targetState = state, modifier = Modifier.graphicsLayer {
                    scaleX = 1f - 0.04f * backProgress
                    scaleY = scaleX
                    alpha = 1f - 0.12f * backProgress
                }, contentKey = { destinationKey(it.destination) }, label = "route",
                    transitionSpec = { fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.standard)) }) { displayed ->
                val destinationContent: @Composable () -> Unit = {
                val state = displayed
                if (state.catalog is CatalogState.RecoveryRequired && state.destination !in setOf(Route.Settings, Route.About)) {
                    RecoveryScreen(viewModel::retryProtectedStorage) { navigate(Route.Settings) }
                } else when (state.destination) {
                    Route.Connection -> ConnectionImportScreen(connectionImport, state.catalog,
                        { viewModel.openConnectionImport(it) }, viewModel::stageConnection,
                        viewModel::cancelConnectionImport, viewModel::discardPendingConnection,
                        viewModel::reloadConnectionImport, viewModel::verifyPendingConnection, viewModel::activatePendingConnection)
                    Route.Settings -> SettingsScreen(theme = state.theme,
                                preferences = state.homePreferences,
                                accent = state.accent,
                                demo = BuildConfig.IS_DEMO,
                                demoAdmin = (state.catalog as? CatalogState.Ready)?.admin == true,
                                actions = rememberSettingsActions(SettingsCallbacks(
                                    setTheme = viewModel::setTheme,
                                    setSort = viewModel::setSort,
                                    setLayout = viewModel::setLayout,
                                    setNames = viewModel::setNames,
                                    setSecondaryHijri = viewModel::setSecondaryHijri,
                                    setAccent = viewModel::setAccent,
                                    setTwoColumns = viewModel::setTwoColumns,
                                    setSectionsAtBottom = viewModel::setSectionsAtBottom,
                                    setSearchAtBottom = viewModel::setSearchAtBottom,
                                    setGroupingEnabled = viewModel::setGroupingEnabled,
                                    setGrouping = viewModel::setGrouping,
                                    openBackup = if (state.catalog is CatalogState.Ready && !state.catalog.isDemo && !configured) ({ navigate(Route.Backup) }) else null,
                                    openMaintenance = null,
                                    openLeave = null,
                                    changeDemoAdmin = viewModel::setDemoAdmin,
                                    resetDemo = viewModel::resetDemo,
                                    openAbout = { navigate(Route.About) })))
                    Route.Maintenance -> MaintenanceScreen(state.catalog as? CatalogState.Ready, viewModel)
                    Route.Backup -> BackupScreen(state, viewModel)
                    Route.CsvImport -> CsvImportScreen(state.catalog as? CatalogState.Ready, viewModel)
                    Route.Reports -> ReportsScreen(state.catalog as? CatalogState.Ready, viewModel)
                    Route.About -> AboutScreen {
                        try {
                            context.startActivity(Intent(Intent.ACTION_SENDTO,
                                ("mailto:" + context.getString(R.string.contact_email)).toUri()))
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar(uiText(R.string.navigation_email_app_missing_message), withDismissAction = true, duration = SnackbarDuration.Short) }
                        } catch (_: SecurityException) {
                            scope.launch { snackbar.showSnackbar(uiText(R.string.navigation_email_app_open_failed_message), withDismissAction = true, duration = SnackbarDuration.Short) }
                        }
                    }
                    Route.Create, Route.Admin, Route.Setup, Route.Review, Route.Taxonomy, Route.Promote, Route.Leave, Route.ExportConnection -> PharmacyAdminScreen(state, viewModel)
                    is Route.History -> HistoryScreen(
                        state.catalog as? CatalogState.Ready,
                        (state.destination as Route.History).medicationId, viewModel,
                        showHijri = state.homePreferences.secondaryHijri)
                    is Route.EditMedication -> PharmacyAdminScreen(state, viewModel)
                    Route.Home, is Route.Medication, is Route.Unknown -> when (val catalogState = state.catalog) {
                        CatalogState.Loading -> LoadingScreen()
                        CatalogState.RecoveryRequired -> RecoveryScreen(viewModel::retryProtectedStorage) { navigate(Route.Settings) }
                        is CatalogState.Ready -> {
                            val selectedMedication = (state.destination as? Route.Medication)?.id
                            val medication = catalogState.catalog.medications.firstOrNull { it.id == selectedMedication }
                            val showReminder = catalogState.backupReminderAt != null
                            val noticeHeader: (@Composable (Set<String>) -> Unit)? = if (catalogState.maintenancePaused || catalogState.restorePaused ||
                                showReminder || catalogState.admin && !catalogState.isDemo && catalogState.lifecycle == "LOCAL_DRAFT") ({ visible ->
                                if (catalogState.maintenancePaused && "maintenance" in visible) {
                                    InformationCard(uiText(R.string.maintenance_publication_paused_title), uiText(R.string.maintenance_paused_browsing_help))
                                    NavigationCard(uiText(R.string.maintenance_continue_action), { navigate(Route.Maintenance) })
                                }
                                if (catalogState.restorePaused && "restore" in visible) {
                                    InformationCard(uiText(R.string.backup_restore_publication_paused_title), uiText(R.string.backup_restore_paused_browsing_help))
                                    NavigationCard(uiText(R.string.backup_review_restore_action), { navigate(Route.Backup) })
                                }
                                if (showReminder && "backup" in visible) DueBackupReminder(catalogState,
                                    { navigate(Route.Backup) }, { viewModel.command { viewModel.backups.dismissReminder() } })
                                if (catalogState.admin && !catalogState.isDemo && catalogState.lifecycle == "LOCAL_DRAFT" && "local-draft" in visible)
                                    SupportingText(uiText(R.string.publication_local_draft_label))
                            }) else null
                            val pageHeader: (@Composable () -> Unit)? = if (noticeHeader == null) null else ({
                                noticeHeader(setOf("maintenance", "restore", "backup", "local-draft"))
                            })
                            Box(Modifier.fillMaxSize()) {
                            val browseContent: @Composable () -> Unit = {
                            HomeStateContent(viewModel) { state, browse, highlighted -> HomeScreen(ready = catalogState,
                                state = state,
                                now = now,
                                availabilityBusy = adminBusy,
                                availabilityMessage = adminMessage,
                                pageHeader = noticeHeader,
                                availabilitySavedId = availabilitySavedId,
                                publicationBusy = sharing.busy,
                                publicationBlockReason = publicationBlockedReason(catalogState, sharing, adminBusy || sharing.busy),
                                projection = browse,
                                highlightedMedication = highlighted,
                                actions = rememberHomeActions(HomeCallbacks(
                                    selectSection = viewModel::selectSection,
                                    setQuery = viewModel::setQuery,
                                    toggleStatus = viewModel::toggleStatus,
                                    toggleCategory = viewModel::toggleCategory,
                                    clearFilters = viewModel::clearFilters,
                                    openMedication = { navigate(Route.Medication(it)) },
                                    openSettings = { navigate(Route.Settings) },
                                    openConnection = { viewModel.openConnectionImport() },
                                    refresh = if (catalogState.isDemo || catalogState.restorePaused || catalogState.maintenancePaused || catalogState.lifecycle != "SHARED") null else ({ viewModel.refreshCatalog() }),
                                    openCreate = { navigate(Route.Create) },
                                    openRestore = { navigate(Route.Backup) },
                                    saveAvailability = { selection, status, complete ->
                                    viewModel.saveAvailability(selection, BulkMedicationChanges(status = status), complete)
                                },
                                    openAddMedication = { viewModel.newMedicationName = ""; navigate(Route.EditMedication()) },
                                    onModalVisibilityChanged = { homeModalOpen = it },
                                    availabilityInProgress = { viewModel.adminBusy.value },
                                    canOpenHomeModal = { !dataTimesOpen && !drawerState.isOpen },
                                    setGroupingEnabled = viewModel::setGroupingEnabled,
                                    setGrouping = viewModel::setGrouping,
                                    saveBulk = viewModel::saveAvailability,
                                    prefillMedication = { query -> viewModel.newMedicationName = query; navigate(Route.EditMedication()) },
                                    toggleExtraFilter = viewModel::toggleExtraFilter,
                                    toggleAvailableFilter = viewModel::toggleAvailableFilter,
                                    editMedication = { id -> navigate(Route.EditMedication(id)) }))) }
                            }
                            val detailContent: @Composable () -> Unit = {
                                if (medication != null) MedicationScreen(catalogState, medication, now,
                                    state.homePreferences.secondaryHijri, pageHeader, viewModel)
                                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(uiText(R.string.catalog_detail_choose_medication_message)) }
                            }
                            HomeDetailLayout(dualPane = dualPane, showDetail = medication != null,
                                list = browseContent, detail = detailContent,
                                detailHeader = if (medication != null) ({
                                        Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.related),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                                            IconButton(enabled = !adminBusy && !homeModalOpen && !dataTimesOpen,
                                                onClick = back, modifier = Modifier.testTag("back")) {
                                                AppIcon(UiIcon.BACK, description = uiText(R.string.navigation_back_description))
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
                val displayedDestination = destinationKey(displayed.destination)
                if (displayedDestination == Route.Home || displayed.destination is Route.EditMedication)
                    destinationStates.SaveableStateProvider(viewModel.destinationStateKey(displayedDestination), destinationContent)
                else destinationContent()
                }
            }
        }
    }
}

@Composable internal fun DemoNotice() {
    val uiText = rememberUiText()
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.page, vertical = UiSpacing.related)) {
            Text(uiText(R.string.shared_demo_notice_title), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("demo_notice"))
            Text(uiText(R.string.shared_demo_notice_help), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun MedicationScreen(ready: CatalogState.Ready, medication: Medication, now: java.time.Instant, showHijri: Boolean,
    pageHeader: (@Composable () -> Unit)?, vm: CatalogViewModel) {
    val uiText = rememberUiText()
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
                    choose = { status -> if (current && !vm.adminBusy.value) vm.saveAvailability(captured, BulkMedicationChanges(status = status)) { if (editor == captured) editor = null } })
                if (record == null) SupportingText(uiText(R.string.history_update_time_missing_message))
                else {
                    SupportingText(relativeTime(record.updatedAt, now, text = uiText))
                    CollapsibleGroup(uiText(R.string.history_technical_details_title), expanded, { expanded = !expanded }) {
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
                    Text(uiText(R.string.catalog_trade_name_heading) + ": " + bidi(MedicationPresentation.names(medication.nameAr, medication.nameEn, NameDisplay.BOTH).joinToString(" · ") { it.text }))
                LabeledValue(uiText(R.string.catalog_dosage_form_label), catalog.formsById[medication.formId]?.nameAr.orEmpty())
                LabeledValue(uiText(R.string.catalog_category_label), catalog.categoriesById[medication.categoryId]?.nameAr.orEmpty())
                medication.expiryDate?.let { LabeledValue(uiText(R.string.catalog_gregorian_expiry_date_label), bidi(it.toString())); SecondaryHijriDate(it, showHijri) }
                ExpiryBadge(medication, MedicationPresentation.today(now, catalog.pharmacy?.timezone ?: "Asia/Damascus"))
                medication.brand?.let { LabeledValue(uiText(R.string.catalog_brand_label), bidi(it)) }
            }
        }
        medication.note?.let { note -> item { InformationCard(uiText(R.string.catalog_notes_heading), note) } }
    }
}

@Composable private fun SettingsScreen(theme: ThemeChoice,
    preferences: HomePreferences,
    accent: ThemeAccent,
    demo: Boolean = false,
    demoAdmin: Boolean = false,
    actions: SettingsActions) {
    val uiText = rememberUiText()
    val setTheme = actions.setTheme
    val setSort = actions.setSort
    val setLayout = actions.setLayout
    val setNames = actions.setNames
    val setSecondaryHijri = actions.setSecondaryHijri
    val setAccent = actions.setAccent
    val setTwoColumns = actions.setTwoColumns
    val setSectionsAtBottom = actions.setSectionsAtBottom
    val setSearchAtBottom = actions.setSearchAtBottom
    val setGroupingEnabled = actions.setGroupingEnabled
    val setGrouping = actions.setGrouping
    val openBackup = actions.openBackup
    val openMaintenance = actions.openMaintenance
    val openLeave = actions.openLeave
    val changeDemoAdmin = actions.changeDemoAdmin
    val resetDemo = actions.resetDemo
    val openAbout = actions.openAbout
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    LazyColumn(Modifier.fillMaxSize().testTag("settings"), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
        if (demo) item(key = "demo_mode") { DemoModeControls(demoAdmin, changeDemoAdmin, resetDemo) }
        item { SettingsGroup(uiText(R.string.review_appearance), initiallyExpanded = true) {
            SettingsChoiceRows(uiText(R.string.settings_theme_label), ThemeChoice.entries, theme, setTheme, {
                uiText(when (it) { ThemeChoice.SYSTEM -> R.string.settings_theme_system_label; ThemeChoice.LIGHT -> R.string.settings_theme_light_label
                    ThemeChoice.DARK -> R.string.settings_theme_dark_label; ThemeChoice.AMOLED -> R.string.settings_theme_amoled_label })
            }, choiceModifier = { Modifier.testTag("theme_${it.name}") })
            SettingsChoiceRows(uiText(R.string.settings_primary_accent_label), ThemeAccent.entries, accent, setAccent, {
                uiText(when (it) { ThemeAccent.GOLD -> R.string.settings_accent_gold_label; ThemeAccent.BLUE -> R.string.settings_accent_blue_label
                    ThemeAccent.GREEN -> R.string.settings_accent_green_label; ThemeAccent.PURPLE -> R.string.settings_accent_purple_label; ThemeAccent.CRIMSON -> R.string.settings_accent_crimson_label })
            }, swatch = { appColorScheme(theme, it, systemDark, resources).primary })
        } }
        item { SettingsGroup(uiText(R.string.review_list_display)) {
            SettingsChoiceRows(uiText(R.string.settings_medication_sort_label), HomeSort.entries, preferences.sort, setSort, label = {
                uiText(when (it) { HomeSort.SCIENTIFIC_NAME -> R.string.settings_sort_scientific_name_label; HomeSort.ARABIC_NAME -> R.string.settings_sort_arabic_trade_name_label
                    HomeSort.ENGLISH_NAME -> R.string.settings_sort_english_trade_name_label; HomeSort.AVAILABILITY -> R.string.settings_sort_section_availability_label })
            }, modifier = Modifier.testTag("sort_preference"), choiceModifier = { Modifier.testTag("sort_${it.name}") })
            ContentSection {
                CardLayoutPicker(preferences.layout, setLayout)
                MedicationLayoutPreview(preferences.layout)
            }
            SwitchChoiceRow(preferences.twoColumns, uiText(R.string.review_adaptive_columns), uiText(R.string.review_adaptive_columns_help), Modifier.testTag("two_columns"), setTwoColumns)
            ContentSection {
                SwitchChoiceRow(preferences.groupingEnabled, uiText(R.string.settings_grouping_label), uiText(R.string.settings_grouping_help), change = setGroupingEnabled)
                if (preferences.groupingEnabled) SettingsChoiceRows(uiText(R.string.settings_grouping_mode_label), HomeGrouping.entries,
                    preferences.grouping, setGrouping, label = { uiText(if (it == HomeGrouping.CATEGORY) R.string.catalog_category_label else R.string.settings_grouping_scientific_letter_label) },
                    modifier = Modifier.testTag("grouping_preference"), choiceModifier = { Modifier.testTag("grouping_${it.name}") })
            }
            SettingsChoiceRows(uiText(R.string.settings_medication_name_display_label), NameDisplay.entries, preferences.names, setNames, label = {
                uiText(when (it) { NameDisplay.ARABIC -> R.string.settings_names_arabic_label; NameDisplay.ENGLISH -> R.string.settings_names_english_label; NameDisplay.BOTH -> R.string.settings_names_both_label })
            }, modifier = Modifier.testTag("names_preference"), choiceModifier = { Modifier.testTag("names_${it.name}") })
        } }
        item { SettingsGroup(uiText(R.string.review_dates_placement)) {
            SwitchChoiceRow(preferences.sectionsAtBottom, uiText(R.string.home_sections_bottom), uiText(R.string.home_sections_bottom_help), Modifier.testTag("sections_at_bottom"), setSectionsAtBottom)
            SwitchChoiceRow(preferences.searchAtBottom, uiText(R.string.home_search_bottom), uiText(R.string.home_search_bottom_help), Modifier.testTag("search_at_bottom"), setSearchAtBottom)
            ContentSection {
                SwitchChoiceRow(preferences.secondaryHijri, uiText(R.string.settings_secondary_hijri_label), uiText(R.string.settings_secondary_hijri_help), Modifier.testTag("secondary_hijri"), setSecondaryHijri)
                ExpandableInformation(uiText(R.string.settings_hijri_method_title), uiText(R.string.settings_hijri_method_help))
            }
        } }
        openBackup?.let { item { NavigationCard(uiText(R.string.navigation_backup_title), it) } }
        item { NavigationCard(uiText(R.string.navigation_about_title), openAbout, Modifier.testTag("open_about")) }
    }
}

@Composable private fun AboutScreen(contact: () -> Unit) {
    val uiText = rememberUiText()
    LazyColumn(Modifier.fillMaxSize().testTag("about"), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section), horizontalAlignment = Alignment.CenterHorizontally) {
        item { BrandEmblem(Modifier.size(Size.emblemLarge)) }
        item { ExpandableInformation(uiText(R.string.review_legend), uiText(R.string.review_legend_body) + "\n" + uiText(R.string.review_changed) + ": " + uiText(R.string.review_changed_help)) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiText(R.string.navigation_app_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(bidi(BuildConfig.VERSION_NAME), fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (BuildConfig.IS_DEMO) Text(uiText(R.string.navigation_demo_build_label), color = MaterialTheme.colorScheme.secondary)
            }
        }
        item { InformationCard(uiText(R.string.navigation_about_app_title), uiText(R.string.navigation_about_app_help)) }
        item {
            GroupedCard(title = uiText(R.string.navigation_development_heading)) {
                Text(uiText(R.string.navigation_developer_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(bidi(uiText(R.string.contact_email)), fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.testTag("developer_email"))
                AppButton(variant = AppButtonVariant.SECONDARY, onClick = contact, modifier = Modifier.testTag("email_contact")) { Text(uiText(R.string.navigation_email_contact_action)) }
            }
        }
        item { InformationCard(uiText(R.string.navigation_app_update_title), uiText(R.string.navigation_manual_update_help)) }
    }
}

@Composable internal fun WelcomeScreen(openSettings: () -> Unit, openConnection: (() -> Unit)? = null, openCreate: (() -> Unit)? = null, openRestore: (() -> Unit)? = null) {
    val uiText = rememberUiText()
    val compactHeight = LocalLayoutMetrics.current.compact(viewportHeight(1f), LayoutTokens.welcomeCompactHeight)
    Column(Modifier.fillMaxWidth().padding(vertical = UiSpacing.section), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Text(uiText(R.string.navigation_welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        BrandEmblem(Modifier.size(if (compactHeight) Size.emblemCompact else Size.emblemLarge))
        openConnection?.let { action ->
            GroupedCard(title = uiText(R.string.connection_join_with_file_title)) {
                Text(uiText(R.string.connection_join_with_file_help))
                AppButton(onClick = action) { Text(uiText(R.string.connection_join_with_file_action)) }
            }
        }
        openCreate?.let { action ->
            GroupedCard(title = uiText(R.string.catalog_create_pharmacy_welcome_title)) {
                Text(uiText(R.string.catalog_create_pharmacy_welcome_help))
                AppButton(variant = AppButtonVariant.SECONDARY, onClick = action, modifier = Modifier.testTag("create_pharmacy")) { Text(uiText(R.string.navigation_create_pharmacy_title)) }
            }
        }
        ActionGroup {
            openRestore?.let { action -> AppButton(variant = AppButtonVariant.QUIET, onClick = action) { Text(uiText(R.string.backup_restore_welcome_action)) } }
            AppButton(variant = AppButtonVariant.QUIET, onClick = openSettings) { Text(uiText(R.string.navigation_settings_about_action)) }
        }
    }
}

@Composable private fun LoadingScreen() {
    val uiText = rememberUiText()
    Column(Modifier.fillMaxSize().padding(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Text(uiText(R.string.shared_catalog_loading_message), style = MaterialTheme.typography.bodyMedium)
        CircularProgressIndicator()
    }
}

@Composable private fun RecoveryScreen(retry: () -> Unit, openSettings: () -> Unit) {
    val uiText = rememberUiText()
    FormPage {
        Text(uiText(R.string.shared_protected_storage_retained_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionHeading(uiText(R.string.shared_protected_storage_unavailable_title))
        Text(uiText(R.string.shared_protected_storage_retry_help))
        AppButton(onClick = retry) { Text(uiText(R.string.history_retry_action)) }
        Text(uiText(R.string.shared_protected_storage_recovery_help))
        NavigationCard(uiText(R.string.navigation_help_about_action), openSettings)
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
