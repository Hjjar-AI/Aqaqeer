package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@Composable fun AqaqeerApp(viewModel: CatalogViewModel) {
    val state by viewModel.shellState.collectAsStateWithLifecycle()
    AqaqeerTheme(state.theme, accent = state.accent) {
        CompositionLocalProvider(LocalNoticeScope provides ((state.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.pharmacyId ?: "unconfigured")) {
            val catalogLoading = state.catalog == CatalogState.Loading
            var startupResolved by remember { mutableStateOf(!catalogLoading) }
            var showSplash by remember { mutableStateOf(catalogLoading) }
            val waitingForStartup = !startupResolved && catalogLoading
            SideEffect { if (!catalogLoading) startupResolved = true }
            Box(Modifier.fillMaxSize()) {
                if (!waitingForStartup) AppShell(state, viewModel)
                if (showSplash) LaunchSplash(waitingForStartup) { showSplash = false }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppShell(state: AppUiState, viewModel: CatalogViewModel) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val uiText = rememberUiText()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val contentInsets = ScaffoldDefaults.contentWindowInsets
    val horizontalInsets = with(density) {
        (contentInsets.getLeft(this, direction) + contentInsets.getRight(this, direction)).toDp()
    }
    val dualPane = maxWidth - horizontalInsets >= UiSpacing.listDetailBreakpoint
    val homeDestination = appDestinationKey(state.destination, dualPane) == Route.Home
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
    val now = rememberPresentationDate((state.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE)
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
        val editable = ready.canEditCatalog
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
        DataTimesDialog(readyForTitle, now, refresh = if (readyForTitle.canRefreshCatalog) ({ viewModel.refreshCatalog() }) else null, busy = state.refresh.busy) { dataTimesOpen = false }
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
                AppTopBar(title, titleMedication, readyForTitle, homeDestination, configured, drawerEligible,
                    dualPane, homeModalOpen, dataTimesOpen, adminBusy, sharing.busy,
                    showDataTimes = { afterDrawer { dataTimesOpen = true } },
                    openMenu = {
                        if (!homeModalOpen && !dataTimesOpen) {
                            if (drawerEligible) { focus.clearFocus(); scope.launch { drawerState.open() } }
                            else navigate(Route.Settings)
                        }
                    }, back = back, review = viewModel::review, medicationActions = medicationActions)
            }, snackbarHost = { SnackbarHost(snackbar) },
        ) { insets ->
            ScreenFrame(wide = state.destination.isWide, form = state.destination.isForm,
                modifier = Modifier.padding(insets).consumeWindowInsets(insets).imePadding()) {
                AppNavHost(state, viewModel, now, dualPane, backProgress, destinationStates, connectionImport,
                    adminBusy, adminMessage, availabilitySavedId, sharing, configured, homeModalOpen, dataTimesOpen,
                    { homeModalOpen = it }, { !dataTimesOpen && !drawerState.isOpen }, navigate, back, snackbar, medicationActions)
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
