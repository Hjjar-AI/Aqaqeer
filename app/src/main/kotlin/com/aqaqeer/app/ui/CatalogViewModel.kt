package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R
import com.aqaqeer.app.BuildConfig

import android.net.Uri
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aqaqeer.app.admin.*
import kotlinx.coroutines.delay
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.*
import com.aqaqeer.app.maintenance.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

private data class BrowseRequest(val section: String?, val query: String, val filters: HomeFilters,
    val preferences: HomePreferences, val unpublished: Set<String>, val fetched: Set<String>, val today: java.time.LocalDate, val readerStockOnly: Boolean)
internal data class UiFeedback(val message: String, val route: String, val medicationId: String? = null,
    val undo: AvailabilityUndo? = null, val id: String = java.util.UUID.randomUUID().toString()) {
    val destination: Route = RouteStorage.decode(route)
}

data class RefreshUiState(val busy: Boolean = false, val error: String? = null)

data class AppUiState(
    val catalog: CatalogState = CatalogState.Loading,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val route: String = "home",
    val sectionId: String? = null,
    val homePreferences: HomePreferences = HomePreferences(),
    val query: String = "",
    val appliedQuery: String = "",
    val filters: HomeFilters = HomeFilters(),
    val refresh: RefreshUiState = RefreshUiState(),
    val fetchedIds: Set<String> = emptySet(),
    val accent: ThemeAccent = ThemeAccent.PURPLE,
    val online: Boolean = true,
) {
    val destination: Route = RouteStorage.decode(route)
}

@HiltViewModel
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogViewModel @Inject constructor(
    repository: CatalogRepository,
    private val preferences: DisplayPreferences,
    private val savedState: SavedStateHandle,
    private val connectionImports: ConnectionImportRepository,
    private val refreshes: SnapshotRefreshRepository,
    private val histories: HistoryRepository,
    private val csvImports: CsvImportRepository,
    private val reports: ReportRepository,
    internal val admin: AdminRepository,
    internal val sharing: PharmacySharing,
    internal val backups: com.aqaqeer.app.backup.BackupRepository,
    internal val maintenance: MaintenanceCoordinator,
    internal val pinChanges: PinChanges,
    @ApplicationContext context: Context,

) : ViewModel() {
    init { viewModelScope.launch { runCatching { backups.cleanupAbandoned() } } }
    internal val feedback = MutableStateFlow<UiFeedback?>(null)
    internal var newMedicationName: String = ""
    internal val highlightedMedication = MutableStateFlow<String?>(null)
    internal val availabilitySavedId = MutableStateFlow<String?>(null)
    internal val adminBusy = MutableStateFlow(false)
    internal val adminMessage = MutableStateFlow<String?>(null)
    internal val pendingEdits = MutableStateFlow<List<PendingOperation>>(emptyList())
    private val exchangeController = ExchangeController(csvImports, reports,
        catalogState = ::currentCatalog, currentRoute = ::currentRoute, isOnline = ::currentOnline,
        feedback = feedback, markReconnectSafely = { markReconnectSafely() },
        runCommand = { queueOffline, action -> command(queueOffline = queueOffline, action = action) })
    internal val csvImport = exchangeController.csvImport
    internal val reportSnapshot = exchangeController.reportSnapshot
    internal fun loadCsv(uri: Uri) = exchangeController.loadCsv(uri)
    internal fun reviseCsv(number: Int, decision: CsvDecision) = exchangeController.reviseCsv(number, decision)
    internal fun chooseCsvDuplicates(update: Boolean) = exchangeController.chooseCsvDuplicates(update)
    internal fun selectValidCsv() = exchangeController.selectValidCsv()
    internal fun commitCsv() = exchangeController.commitCsv()
    internal fun beginCsvSave(kind: CsvSaveKind) = exchangeController.beginCsvSave(kind)
    internal fun saveCsv(uri: Uri?) = exchangeController.saveCsv(uri)
    internal fun prepareReport(request: ReportRequest) = exchangeController.prepareReport(request)
    internal fun beginReportSave(pdf: Boolean) = exchangeController.beginReportSave(pdf)
    internal fun saveReport(uri: Uri?) = exchangeController.saveReport(uri)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var online = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    private var foreground = false
    private var reconnectJob: Job? = null
    private val networkObserver = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            viewModelScope.launch {
                val available = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val recovered = available && !online
                val lost = !available && online
                online = available
                onlineState.value = online
                if (lost && readyForReconnect()) markReconnectSafely()
                if (recovered) resumeReconnect()
            }
        }
        override fun onLost(network: Network) { viewModelScope.launch {
            online = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            onlineState.value = online
            if (!online && readyForReconnect()) markReconnectSafely()
        } }
    }
    private var adminJob: Job? = null
    internal fun cancelAdminOperation() { adminJob?.cancel() }
    internal fun command(destination: String, queueOffline: Boolean = true, action: suspend () -> Unit) =
        command(RouteStorage.decode(destination), queueOffline, action)

    internal fun command(destination: Route? = null, queueOffline: Boolean = true, action: suspend () -> Unit) {
        if (adminBusy.value) { feedback.value = UiFeedback(uiText(R.string.shared_operation_busy_message), route.value); return }
        val origin = route.value
        refreshJob?.cancel()
        adminBusy.value = true; adminMessage.value = null
        adminJob = viewModelScope.launch {
            try { action(); if (queueOffline && !online) markReconnectSafely(); if (destination != null) navigate(destination) }
            catch (e: CancellationException) { throw e }
            catch (e: MaintenanceReview) { maintenance.conflicts.value = e.conflicts; adminMessage.value = uiText(R.string.maintenance_review_choices_error) }
            catch (e: Exception) { adminMessage.value = (e as? com.aqaqeer.app.admin.AdminProblem)?.reason ?: (e as? MaintenanceProblem)?.reason ?: (e as? CsvProblem)?.reason ?: when ((e as? com.aqaqeer.app.security.files.EnvelopeException)?.error) {
                com.aqaqeer.app.security.files.EnvelopeError.AUTHENTICATION -> uiText(R.string.backup_operation_authentication_error)
                com.aqaqeer.app.security.files.EnvelopeError.TOO_LARGE -> uiText(R.string.backup_operation_size_error)
                com.aqaqeer.app.security.files.EnvelopeError.RESOURCE_LIMIT -> uiText(R.string.backup_operation_resources_error)
                com.aqaqeer.app.security.files.EnvelopeError.UNSUPPORTED_FORMAT -> uiText(R.string.backup_operation_format_unsupported_error)
                com.aqaqeer.app.security.files.EnvelopeError.INVALID_CREDENTIAL -> uiText(R.string.backup_operation_passphrase_error)
                else -> uiText(R.string.shared_operation_failed_message)
            } }
            finally {
                if (route.value == origin) adminMessage.value?.let { feedback.value = UiFeedback(it, origin) }
                else adminMessage.value = null
                adminBusy.value = false
            }
        }
    }
    internal fun secretCommand(destination: String, vararg secrets: CharArray, queueOffline: Boolean = true, action: suspend () -> Unit) =
        secretCommand(RouteStorage.decode(destination), *secrets, queueOffline = queueOffline, action = action)

    internal fun secretCommand(destination: Route?, vararg secrets: CharArray, queueOffline: Boolean = true, action: suspend () -> Unit) {
        if (adminBusy.value) { secrets.forEach { it.fill('\u0000') }; feedback.value = UiFeedback(uiText(R.string.shared_operation_busy_message), route.value); return }
        command(destination, queueOffline = queueOffline) { try { action() } finally { secrets.forEach { it.fill('\u0000') } } }
        // Also clear transferred secrets if a cancelled scope never starts the coroutine body.
        adminJob?.invokeOnCompletion { secrets.forEach { it.fill('\u0000') } }
    }
    internal fun saveAvailability(selection: AvailabilitySelection, changes: BulkMedicationChanges, complete: () -> Unit) = command {
        val receipt = admin.bulkMedicationChanges(selection.expected.keys, selection.section.id, changes,
            selection.medications.associate { med -> med.id to BulkMedicationExpected(selection.expected.getValue(med.id), med.formId, med.categoryId) },
            selection.pharmacyId)
        availabilitySavedId.value = selection.id
        complete()
        feedback.value = UiFeedback(uiText(R.string.availability_saved_local_message), route.value, undo = receipt)
    }
    internal fun undo(receipt: AvailabilityUndo) = command { admin.undoAvailability(receipt) }
    internal fun medicationSaved(id: String, added: Boolean) {
        navigate(Route.Home)
        highlightedMedication.value = id
        feedback.value = UiFeedback(if (added) uiText(R.string.catalog_medication_added_message) else uiText(R.string.catalog_medication_saved_message), "home", medicationId = id)
    }
    internal fun review() = command(Route.Review) { pendingEdits.value = admin.pending() }
    internal fun publish(choices: Map<String, Boolean> = emptyMap(), reviewOnFailure: Boolean = false,
        recoverPin: Boolean = false) = command {
        check(!BuildConfig.IS_DEMO)
        sharing.publish(choices, recoverPin); pendingEdits.value = admin.pending(); admin.clearReconnectIfDone(sharing.status.value.verified)
        if (reviewOnFailure && !sharing.status.value.verified) navigate(Route.Review)
    }
    internal fun onForeground(active: Boolean) {
        foreground = active
        if (active) {
            if (!online && readyForReconnect()) viewModelScope.launch { markReconnectSafely() }
            resumeReconnect()
        }
    }
    private suspend fun markReconnectSafely() {
        if (BuildConfig.IS_DEMO) return
        try { admin.markReconnect() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Best effort; the pending edits remain durable. */ }
    }
    private fun readyForReconnect(): Boolean = (state.value.catalog as? CatalogState.Ready)?.let {
        it.admin && !it.isDemo && !it.requiresUpdate && !it.restorePaused && !it.maintenancePaused && it.lifecycle == "SHARED"
    } == true
    private fun resumeReconnect() {
        // Essential startup alone owns initial database/key creation. Connectivity callbacks
        // must not open another handle while local startup is still loading or recovering.
        if (!foreground || !online || !readyForReconnect() || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            try {
            if (!admin.shouldReconnect()) return@launch
            repeat(3) { attempt ->
                if (!foreground || !online) return@launch
                sharing.publish()
                admin.clearReconnectIfDone(sharing.status.value.verified)
                if (!admin.shouldReconnect() || !sharing.status.value.retryable) return@launch
                delay(maxOf(2000L shl attempt, sharing.status.value.retryAfterSeconds * 1000L))
            }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { feedback.value = UiFeedback(uiText(R.string.publication_resume_failed_message), "home") }
        }
    }
    override fun onCleared() { connectivity.unregisterNetworkCallback(networkObserver); super.onCleared() }
    private val refreshState = MutableStateFlow(RefreshUiState())
    private val connectionController = ConnectionImportController(connectionImports, viewModelScope) { navigate(it) }
    val connectionImport: StateFlow<ConnectionImportUiState> = connectionController.connectionImport
    private val onlineState = MutableStateFlow(online)
    private val navigation = NavigationController(savedState,
        routeAllowed = { !BuildConfig.IS_DEMO || demoRouteAllowed(it) },
        beforeRoute = ::beforeRoute, beforeBack = ::beforeBack)
    private val route = navigation.route
    private val section = savedState.getStateFlow<String?>("section", null)
    private val query = savedState.getStateFlow("query", "")
    private val appliedQuery = query.debounce { if (MedicationPresentation.normalizedSearch(it).isEmpty()) 0L else 250L }
    private val statuses = savedState.getStateFlow("statuses", arrayListOf<String>())
    private val categories = savedState.getStateFlow("categories", arrayListOf<String>())
    private val extraFilters = savedState.getStateFlow("extraFilters", arrayListOf<String>())
    private val browsing = combine(query, appliedQuery, statuses, categories, extraFilters) { query, applied, statuses, categories, extra ->
        Triple(query, applied, HomeFilters(MedicationPresentation.editableStatuses.filter { it.name in statuses }.toSet(), categories.toSet(), "expiry" in extra, "unpublished" in extra, "fetched" in extra))
    }
    private val catalogRetry = MutableStateFlow(0)
    internal fun retryProtectedStorage() {
        if (state.value.catalog == CatalogState.RecoveryRequired) catalogRetry.update { it + 1 }
    }
    private val preferenceWriter = PreferenceWriter(preferences, viewModelScope)
    private val sectionPreference = combine(section, preferences.sections) { selected, retained -> selected to retained }
    private val base = combine(catalogRetry.flatMapLatest { attempt -> flow<CatalogState> {
        if (attempt > 0) try { repository.resetProtectedStorage() } catch (error: Exception) {
            if (error is CancellationException) throw error
            emit(CatalogState.RecoveryRequired)
            return@flow
        }
        if (BuildConfig.IS_DEMO) DemoSession.initialize()
        emitAll(if (BuildConfig.IS_DEMO) combine(repository.observe(), DemoSession.state) { source, session ->
            if (source is CatalogState.Ready && session != null) DemoSession.ready(source, session) else source
        } else repository.observe())
    } }, preferences.theme, preferences.accent, route, sectionPreference) {
        catalog, theme, accent, route, section ->
        val snapshot = (catalog as? CatalogState.Ready)?.catalog
        val requested = if (savedState.get<String>("sectionPharmacy") == snapshot?.pharmacy?.pharmacyId) section.first else null
        val selected = requested ?: snapshot?.pharmacy?.pharmacyId?.let { section.second[it] }
        AppUiState(catalog, theme, if (BuildConfig.IS_DEMO && !demoRouteAllowed(RouteStorage.decode(route))) "home" else route, snapshot?.sections?.firstOrNull { it.isActive && it.id == selected }?.id
            ?: snapshot?.sections?.firstOrNull { it.isActive }?.id, accent = accent)
    }
    private val liveBase = combine(base, FetchedChanges.ids) { base, ids ->
        val ready = base.catalog as? CatalogState.Ready
        base.copy(fetchedIds = ids[ready?.catalog?.pharmacy?.pharmacyId].orEmpty())
    }
    val state: StateFlow<AppUiState> = combine(liveBase, preferences.home, browsing, refreshState, onlineState) { base, home, browsing, refresh, online ->
        val ready = base.catalog as? CatalogState.Ready
        val readerStockOnly = ready != null && !ready.admin
        base.copy(homePreferences = home, query = browsing.first, appliedQuery = browsing.second,
            filters = browsing.third.copy(
                statuses = if (readerStockOnly) emptySet() else browsing.third.statuses,
                unpublishedOnly = browsing.third.unpublishedOnly && ready?.admin == true),
            refresh = refresh, online = online)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DisplayPreferences.startupPresentation(context).let { (theme, accent) ->
        AppUiState(theme = theme, accent = accent)
    })
    init {
        viewModelScope.launch {
            state.collect { current ->
                if (BuildConfig.IS_DEMO && current.catalog is CatalogState.Ready && !demoRouteAllowed(current.destination)) navigate(Route.Home)
            }
        }
        viewModelScope.launch {
            state.map { current -> (current.catalog as? CatalogState.Ready)?.let { ready ->
                ready.catalog.pharmacy?.pharmacyId to (ready.admin && !ready.isDemo)
            } }.distinctUntilChanged().collect { maintenance.conflicts.value = emptyList() }
        }
        viewModelScope.launch {
            state.map { current -> (current.catalog as? CatalogState.Ready)?.let { ready ->
                ready.catalog.pharmacy?.pharmacyId to (ready.admin && !ready.isDemo && !ready.requiresUpdate && !ready.maintenancePaused)
            } }.distinctUntilChanged().collect { access ->
                exchangeController.reconcileAccess(access)
            }
        }
    }
    internal val shellState = state.map { it.copy(query = "", appliedQuery = "", filters = HomeFilters(), refresh = RefreshUiState()) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value)
    internal val drawerFilters = state.map { it.filters }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeFilters())
    private val searchIndex = state.map { (it.catalog as? CatalogState.Ready)?.catalog }.distinctUntilChanged()
        .mapLatest { catalog -> withContext(kotlinx.coroutines.Dispatchers.Default) { catalog?.let(::CatalogSearchIndex) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val browseDay = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), Instant.now())
    internal val homeBrowse = combine(searchIndex, combine(state, browseDay) { current, now -> current to now }.map { (current, now) ->
        BrowseRequest(current.sectionId, current.appliedQuery, current.filters, current.homePreferences,
            (current.catalog as? CatalogState.Ready)?.unpublishedMedicationIds.orEmpty(), current.fetchedIds,
            MedicationPresentation.today(now, (current.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE),
            (current.catalog as? CatalogState.Ready)?.let { !it.admin } == true)
    }.distinctUntilChanged()) { index, request -> index to request }.mapLatest { (index, request) ->
        withContext(kotlinx.coroutines.Dispatchers.Default) {
            index?.project(request.section, request.query, request.filters, request.preferences, request.unpublished, request.fetched, request.today, readerStockOnly = request.readerStockOnly) ?: HomeBrowseResult()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HomeBrowseResult())
    private val historyRequest = MutableStateFlow(HistoryRequest())
    internal val history: StateFlow<HistoryUiState> = combine(
        state.map { current ->
            val ready = current.catalog as? CatalogState.Ready
            Triple(ready?.catalog?.pharmacy?.pharmacyId, current.destination, ready?.admin == true)
        }.distinctUntilChanged(), historyRequest,
    ) { access, request -> access to request }.flatMapLatest { (access, request) ->
        val (pharmacyId, destination, authorized) = access
        if (!authorized || pharmacyId == null || destination !is Route.History)
            flowOf(HistoryUiState())
        else {
            val medication = destination.medicationId
            val effective = request.copy(filter = request.filter.copy(medicationId = medication ?: request.filter.medicationId))
            histories.observe(pharmacyId, effective.filter, effective.page)
                .map { HistoryUiState(effective, page = it, pharmacyId = pharmacyId, medicationScope = medication) }
                .onStart { emit(HistoryUiState(effective, loading = true, pharmacyId = pharmacyId, medicationScope = medication)) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(HistoryUiState(effective, error = uiText(R.string.history_load_failed_message),
                        pharmacyId = pharmacyId, medicationScope = medication))
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HistoryUiState())

    internal fun openHistory(medication: String? = null) {
        historyRequest.value = HistoryRequest(filter = HistoryFilter(medicationId = medication))
        navigate(Route.History(medication))
    }
    internal fun filterHistory(filter: HistoryFilter) { historyRequest.value = HistoryRequest(filter) }
    internal fun historyPage(page: Int) {
        if (page >= 0) historyRequest.update { it.copy(page = page) }
    }
    internal fun retryHistory() { historyRequest.update { it.copy(refresh = it.refresh + 1) } }

    private var refreshJob: Job? = null
    fun refreshCatalog(automatic: Boolean = false) {
        val ready = state.value.catalog as? CatalogState.Ready ?: return
        if (ready.isDemo || ready.restorePaused || ready.maintenancePaused || ready.lifecycle != "SHARED" || ready.catalog.pharmacy == null || refreshState.value.busy) return
        val offline = if (automatic) !online else connectivity.activeNetwork == null
        if (adminBusy.value || sharing.status.value.busy || offline) {
            if (!automatic) feedback.value = UiFeedback(if (offline) uiText(R.string.logic_offline) else uiText(R.string.logic_busy), route.value)
            return
        }
        val age = ready.lastSuccessfulRefreshAt?.let { java.time.Duration.between(it, Instant.now()).toMillis() }
        if (automatic && age != null && age in 0 until 5 * 60 * 1000L) return
        val origin = route.value
        val pharmacyId = ready.catalog.pharmacy.pharmacyId
        refreshState.value = RefreshUiState(busy = true)
        refreshJob = viewModelScope.launch {
            try {
                val result = refreshes.refresh()
                if (!automatic && route.value == origin && (state.value.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.pharmacyId == pharmacyId)
                    feedback.value = UiFeedback(if (result == com.aqaqeer.app.data.RefreshOutcome.APPLIED) uiText(R.string.logic_refresh_applied) else uiText(R.string.logic_refresh_current), origin)
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if ((state.value.catalog as? CatalogState.Ready)?.catalog?.pharmacy?.pharmacyId == pharmacyId)
                    refreshState.value = RefreshUiState(error = refreshMessage(error))
            }
            finally { refreshState.update { it.copy(busy = false) } }
        }
    }

    private fun currentCatalog(): CatalogState = state.value.catalog
    private fun currentRoute(): String = route.value
    private fun currentOnline(): Boolean = online
    private fun beforeRoute(destination: Route) {
        if (destination !is Route.History) historyRequest.value = HistoryRequest()
        adminMessage.value = null
        feedback.value = null
    }
    private fun beforeBack(destination: Route) {
        if (destination == Route.EditMedication()) newMedicationName = ""
        if (destination == Route.Connection) connectionController.onBack()
    }

    internal fun destinationStateKey(destination: String): String = navigation.destinationStateKey(RouteStorage.decode(destination))
    internal fun destinationStateKey(destination: Route): String = navigation.destinationStateKey(destination)
    fun navigate(destination: String) = navigate(RouteStorage.decode(destination))
    fun navigate(destination: Route) = navigation.navigate(destination)
    private fun demoRouteAllowed(destination: Route): Boolean = when (destination) {
        Route.Home, Route.Settings, Route.About, is Route.Medication -> true
        Route.Admin, Route.Taxonomy, Route.Review, is Route.History, is Route.EditMedication -> DemoSession.initialize().admin
        else -> false
    }
    internal fun setDemoAdmin(value: Boolean) {
        if (!BuildConfig.IS_DEMO || adminBusy.value) return
        DemoSession.setAdmin(value)
        pendingEdits.value = emptyList()
        navigate(Route.Home)
    }
    internal fun resetDemo() {
        if (!BuildConfig.IS_DEMO || adminBusy.value) return
        DemoSession.reset()
        pendingEdits.value = emptyList(); highlightedMedication.value = null; availabilitySavedId.value = null
        clearFilters(); setQuery(""); navigate(Route.Home)
    }
    fun back() = navigation.back()

    init { reloadConnectionImport(); connectivity.registerDefaultNetworkCallback(networkObserver) }

    fun openConnectionImport(uri: Uri? = null) = connectionController.openConnectionImport(uri)
    fun reloadConnectionImport() = connectionController.reloadConnectionImport()
    fun stageConnection(password: CharArray) = connectionController.stageConnection(password)
    fun cancelConnectionImport() = connectionController.cancelConnectionImport()
    fun verifyPendingConnection(id: String) = connectionController.verifyPendingConnection(id)
    fun activatePendingConnection(id: String) = connectionController.activatePendingConnection(id)
    fun discardPendingConnection(id: String) = connectionController.discardPendingConnection(id)

    fun selectSection(id: String) {
        val ready = state.value.catalog as? CatalogState.Ready ?: return
        val pharmacy = ready.catalog.pharmacy?.pharmacyId ?: return
        if (ready.catalog.sections.none { it.id == id && it.isActive }) return
        savedState["sectionPharmacy"] = pharmacy
        savedState["section"] = id
        preferenceWriter.setSection(pharmacy, id)
    }
    fun setTheme(theme: ThemeChoice) = preferenceWriter.setTheme(theme)
    fun setAccent(accent: ThemeAccent) = preferenceWriter.setAccent(accent)
    fun setQuery(value: String) { savedState["query"] = value }
    fun toggleStatus(value: AvailabilityStatus) {
        val ready = state.value.catalog as? CatalogState.Ready ?: return
        if (!ready.admin) return
        if (value !in MedicationPresentation.editableStatuses) return
        savedState["statuses"] = ArrayList(statuses.value.toSet().let { if (value.name in it) it - value.name else it + value.name })
    }
    internal fun toggleAvailableFilter() {
        val ready = state.value.catalog as? CatalogState.Ready ?: return
        if (!ready.admin) return
        val available = setOf(AvailabilityStatus.AVAILABLE.name, AvailabilityStatus.LOW.name)
        savedState["statuses"] = ArrayList(if (statuses.value.toSet() == available) emptySet() else available)
    }
    fun toggleCategory(id: String) {
        savedState["categories"] = ArrayList(categories.value.toSet().let { if (id in it) it - id else it + id })
    }
    /** Quick drawer choice replaces categories only; advanced filters retain multi-select. */
    fun setQuickCategory(id: String?) {
        val ready = state.value.catalog as? CatalogState.Ready ?: return
        if (id != null && ready.catalog.categories.none { it.id == id }) return
        savedState["categories"] = if (id == null) arrayListOf<String>() else arrayListOf(id)
    }
    internal fun toggleExtraFilter(key: String) {
        val current = extraFilters.value.toSet()
        savedState["extraFilters"] = ArrayList(if (key in current) current - key else current + key)
    }
    fun clearFilters() { savedState["statuses"] = arrayListOf<String>(); savedState["categories"] = arrayListOf<String>(); savedState["extraFilters"] = arrayListOf<String>() }
    fun setSort(value: HomeSort) = preferenceWriter.setSort(value)
    fun setGroupingEnabled(value: Boolean) = preferenceWriter.setGroupingEnabled(value)
    fun setGrouping(value: HomeGrouping) = preferenceWriter.setGrouping(value)
    fun setTwoColumns(value: Boolean) = preferenceWriter.setTwoColumns(value)
    fun setSectionsAtBottom(value: Boolean) = preferenceWriter.setSectionsAtBottom(value)
    fun setSearchAtBottom(value: Boolean) = preferenceWriter.setSearchAtBottom(value)
    fun setLayout(value: CardLayout) = preferenceWriter.setLayout(value)
    fun setSecondaryHijri(value: Boolean) = preferenceWriter.setSecondaryHijri(value)
    fun setNames(value: NameDisplay) = preferenceWriter.setNames(value)
}
