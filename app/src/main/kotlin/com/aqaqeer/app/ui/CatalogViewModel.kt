package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

private data class BrowseRequest(val section: String?, val query: String, val filters: HomeFilters,
    val preferences: HomePreferences, val unpublished: Set<String>)
internal data class UiFeedback(val message: String, val route: String, val medicationId: String? = null,
    val undo: AvailabilityUndo? = null, val id: String = java.util.UUID.randomUUID().toString())

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
    val accent: ThemeAccent = ThemeAccent.GOLD,
    val online: Boolean = true,
)

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
    private val csvState = MutableStateFlow(CsvImportState())
    internal val csvImport = csvState.asStateFlow()
    private val reportState = MutableStateFlow<ReportSnapshot?>(null)
    internal val reportSnapshot = reportState.asStateFlow()
    private var csvSaveDraft: Pair<CsvSaveKind, CsvImportState>? = null
    private var reportSaveDraft: Pair<ReportSnapshot, Boolean>? = null

    internal fun loadCsv(uri: Uri) = command(queueOffline = false) {
        val session = csvImports.load(uri)
        check(exchangeEligible(session.pharmacyId))
        csvState.value = CsvImportState(session)
    }
    internal fun reviseCsv(number: Int, decision: CsvDecision) = command(queueOffline = false) {
        val current = csvState.value
        val session = current.session ?: return@command
        if (!current.finished) {
            val revised = csvImports.revise(session, number, decision)
            if (csvState.value.session?.id == session.id && exchangeEligible(session.pharmacyId)) csvState.value = current.copy(session = revised)
        }
    }
    internal fun chooseCsvDuplicates(update: Boolean) = command(queueOffline = false) {
        val current = csvState.value; val session = current.session ?: return@command
        if (!current.finished && exchangeEligible(session.pharmacyId)) {
            val revised = csvImports.chooseDuplicates(session, update)
            if (csvState.value.session?.id == session.id) csvState.value = current.copy(session = revised)
        }
    }
    internal fun selectValidCsv() = command(queueOffline = false) {
        val current = csvState.value; val session = current.session ?: return@command
        if (!current.finished && exchangeEligible(session.pharmacyId)) csvState.value = current.copy(session = csvImports.selectValid(session))
    }
    internal fun commitCsv() = command(queueOffline = false) {
        val current = csvState.value; val session = current.session ?: return@command
        if (current.finished) return@command
        try {
            csvImports.commit(session) { results ->
                if (csvState.value.session?.id != session.id) throw CancellationException()
                csvState.update { it.copy(results = it.results + results.associateBy { result -> result.number }) }
                if (!online && results.any { it.outcome in setOf(CsvOutcome.CREATED, CsvOutcome.UPDATED) }) markReconnectSafely()
            }
        } finally {
            if (csvState.value.session?.id == session.id) csvState.update { latest ->
                val unprocessed = session.rows.filter { it.record.number !in latest.results }.associate { row ->
                    val skipped = row.decision.action == CsvAction.SKIP || (!row.decision.selected && row.error == null)
                    row.record.number to CsvRowResult(row.record.number, if (skipped) CsvOutcome.SKIPPED else CsvOutcome.FAILED,
                        row.warnings.joinToString(uiText(R.string.ui_catalog_view_model_001)), if (skipped) "" else row.error ?: uiText(R.string.ui_catalog_view_model_002))
                }
                latest.copy(results = latest.results + unprocessed, finished = true)
            }
        }
    }
    internal fun beginCsvSave(kind: CsvSaveKind) { csvSaveDraft = kind to csvState.value }
    internal fun saveCsv(uri: Uri?) {
        val draft = csvSaveDraft; csvSaveDraft = null
        if (uri == null || draft == null) return
        command(queueOffline = false) {
            if (draft.first == CsvSaveKind.TEMPLATE) csvImports.template(uri)
            else csvImports.exportResults(uri, draft.second, draft.first == CsvSaveKind.RETRY)
            if (route.value == "csv-import") feedback.value = UiFeedback(uiText(R.string.ui_catalog_view_model_003), "csv-import")
        }
    }
    internal fun prepareReport(request: ReportRequest) = command(queueOffline = false) {
        val snapshot = reports.capture(request)
        check(exchangeEligible(snapshot.pharmacyId))
        reportState.value = snapshot
    }
    internal fun beginReportSave(pdf: Boolean) { reportState.value?.let { reportSaveDraft = it to pdf } }
    internal fun saveReport(uri: Uri?) {
        val draft = reportSaveDraft; reportSaveDraft = null
        if (uri == null || draft == null) return
        command(queueOffline = false) {
            if (draft.second) reports.pdf(uri, draft.first) else reports.csv(uri, draft.first)
            if (route.value == "reports") feedback.value = UiFeedback(uiText(R.string.ui_catalog_view_model_004), "reports")
        }
    }
    private fun exchangeEligible(pharmacyId: String): Boolean = (state.value.catalog as? CatalogState.Ready)?.let {
        it.admin && !it.isDemo && !it.requiresUpdate && !it.maintenancePaused && it.catalog.pharmacy?.pharmacyId == pharmacyId
    } == true
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
    internal fun command(destination: String? = null, queueOffline: Boolean = true, action: suspend () -> Unit) {
        if (adminBusy.value) { feedback.value = UiFeedback(uiText(R.string.ui_catalog_view_model_005), route.value); return }
        val origin = route.value
        refreshJob?.cancel()
        adminBusy.value = true; adminMessage.value = null
        adminJob = viewModelScope.launch {
            try { action(); if (queueOffline && !online) markReconnectSafely(); if (destination != null) navigate(destination) }
            catch (e: CancellationException) { throw e }
            catch (e: MaintenanceReview) { maintenance.conflicts.value = e.conflicts; adminMessage.value = uiText(R.string.ui_catalog_view_model_006) }
            catch (e: Exception) { adminMessage.value = (e as? com.aqaqeer.app.admin.AdminProblem)?.reason ?: (e as? MaintenanceProblem)?.reason ?: (e as? CsvProblem)?.reason ?: when ((e as? com.aqaqeer.app.security.files.EnvelopeException)?.error) {
                com.aqaqeer.app.security.files.EnvelopeError.AUTHENTICATION -> uiText(R.string.ui_catalog_view_model_007)
                com.aqaqeer.app.security.files.EnvelopeError.TOO_LARGE -> uiText(R.string.ui_catalog_view_model_008)
                com.aqaqeer.app.security.files.EnvelopeError.RESOURCE_LIMIT -> uiText(R.string.ui_catalog_view_model_009)
                com.aqaqeer.app.security.files.EnvelopeError.UNSUPPORTED_FORMAT -> uiText(R.string.ui_catalog_view_model_010)
                com.aqaqeer.app.security.files.EnvelopeError.INVALID_CREDENTIAL -> uiText(R.string.ui_catalog_view_model_011)
                else -> uiText(R.string.ui_catalog_view_model_012)
            } }
            finally {
                if (route.value == origin) adminMessage.value?.let { feedback.value = UiFeedback(it, origin) }
                else adminMessage.value = null
                adminBusy.value = false
            }
        }
    }
    internal fun secretCommand(destination: String?, vararg secrets: CharArray, queueOffline: Boolean = true, action: suspend () -> Unit) {
        if (adminBusy.value) { secrets.forEach { it.fill('\u0000') }; feedback.value = UiFeedback(uiText(R.string.ui_catalog_view_model_005), route.value); return }
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
        feedback.value = UiFeedback(uiText(R.string.ui_catalog_view_model_013), route.value, undo = receipt)
    }
    internal fun undo(receipt: AvailabilityUndo) = command { admin.undoAvailability(receipt) }
    internal fun medicationSaved(id: String, added: Boolean) {
        navigate("home")
        highlightedMedication.value = id
        feedback.value = UiFeedback(if (added) uiText(R.string.ui_catalog_view_model_014) else uiText(R.string.ui_catalog_view_model_015), "home", medicationId = id)
    }
    internal fun review() = command("review") { pendingEdits.value = admin.pending() }
    internal fun publish(choices: Map<String, Boolean> = emptyMap(), reviewOnFailure: Boolean = false) = command {
        sharing.publish(choices); pendingEdits.value = admin.pending(); admin.clearReconnectIfDone(sharing.status.value.verified)
        if (reviewOnFailure && !sharing.status.value.verified) navigate("review")
    }
    internal fun onForeground(active: Boolean) {
        foreground = active
        if (active) {
            if (!online && readyForReconnect()) viewModelScope.launch { markReconnectSafely() }
            resumeReconnect()
        }
    }
    private suspend fun markReconnectSafely() {
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
            catch (_: Exception) { feedback.value = UiFeedback(uiText(R.string.ui_catalog_view_model_016), "home") }
        }
    }
    override fun onCleared() { connectivity.unregisterNetworkCallback(networkObserver); super.onCleared() }
    private val refreshState = MutableStateFlow(RefreshUiState())
    private val importState = MutableStateFlow(ConnectionImportUiState())
    val connectionImport: StateFlow<ConnectionImportUiState> = importState.asStateFlow()
    private var importOperation: Job? = null
    private val onlineState = MutableStateFlow(online)
    private val route = savedState.getStateFlow("route", "home")
    private val section = savedState.getStateFlow<String?>("section", null)
    private val query = savedState.getStateFlow("query", "")
    private val appliedQuery = query.debounce { if (MedicationPresentation.normalizedSearch(it).isEmpty()) 0L else 250L }
    private val statuses = savedState.getStateFlow("statuses", arrayListOf<String>())
    private val categories = savedState.getStateFlow("categories", arrayListOf<String>())
    private val browsing = combine(query, appliedQuery, statuses, categories) { query, applied, statuses, categories ->
        Triple(query, applied, HomeFilters(MedicationPresentation.editableStatuses.filter { it.name in statuses }.toSet(), categories.toSet()))
    }
    private val catalogRetry = MutableStateFlow(0)
    internal fun retryProtectedStorage() {
        if (state.value.catalog == CatalogState.RecoveryRequired) catalogRetry.update { it + 1 }
    }
    private val sectionPreference = combine(section, preferences.sections) { selected, retained -> selected to retained }
    private val base = combine(catalogRetry.flatMapLatest { attempt -> flow<CatalogState> {
        if (attempt > 0) try { repository.resetProtectedStorage() } catch (error: Exception) {
            if (error is CancellationException) throw error
            emit(CatalogState.RecoveryRequired)
            return@flow
        }
        emitAll(repository.observe())
    } }, preferences.theme, preferences.accent, route, sectionPreference) {
        catalog, theme, accent, route, section ->
        val snapshot = (catalog as? CatalogState.Ready)?.catalog
        val requested = if (savedState.get<String>("sectionPharmacy") == snapshot?.pharmacy?.pharmacyId) section.first else null
        val selected = requested ?: snapshot?.pharmacy?.pharmacyId?.let { section.second[it] }
        AppUiState(catalog, theme, route, snapshot?.sections?.firstOrNull { it.isActive && it.id == selected }?.id
            ?: snapshot?.sections?.firstOrNull { it.isActive }?.id, accent = accent)
    }
    val state: StateFlow<AppUiState> = combine(base, preferences.home, browsing, refreshState, onlineState) { base, home, browsing, refresh, online ->
        base.copy(homePreferences = home, query = browsing.first, appliedQuery = browsing.second, filters = browsing.third, refresh = refresh, online = online)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DisplayPreferences.startupPresentation(context).let { (theme, accent) ->
        AppUiState(theme = theme, accent = accent)
    })
    init {
        viewModelScope.launch {
            state.map { current -> (current.catalog as? CatalogState.Ready)?.let { ready ->
                ready.catalog.pharmacy?.pharmacyId to (ready.admin && !ready.isDemo)
            } }.distinctUntilChanged().collect { maintenance.conflicts.value = emptyList() }
        }
        viewModelScope.launch {
            state.map { current -> (current.catalog as? CatalogState.Ready)?.let { ready ->
                ready.catalog.pharmacy?.pharmacyId to (ready.admin && !ready.isDemo && !ready.requiresUpdate && !ready.maintenancePaused)
            } }.distinctUntilChanged().collect { access ->
                val pharmacyId = access?.first
                if (access?.second != true || (csvState.value.session?.pharmacyId?.let { it != pharmacyId } == true)) {
                    csvState.value = CsvImportState(); csvSaveDraft = null
                }
                if (access?.second != true || (reportState.value?.pharmacyId?.let { it != pharmacyId } == true)) {
                    reportState.value = null; reportSaveDraft = null
                }
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
    internal val homeBrowse = combine(searchIndex, state.map { current ->
        BrowseRequest(current.sectionId, current.appliedQuery, current.filters, current.homePreferences,
            (current.catalog as? CatalogState.Ready)?.unpublishedMedicationIds.orEmpty())
    }.distinctUntilChanged()) { index, request -> index to request }.mapLatest { (index, request) ->
        withContext(kotlinx.coroutines.Dispatchers.Default) {
            index?.project(request.section, request.query, request.filters, request.preferences, request.unpublished) ?: HomeBrowseResult()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HomeBrowseResult())
    private val historyRequest = MutableStateFlow(HistoryRequest())
    internal val history: StateFlow<HistoryUiState> = combine(
        state.map { current ->
            val ready = current.catalog as? CatalogState.Ready
            Triple(ready?.catalog?.pharmacy?.pharmacyId, current.route, ready?.admin == true && !ready.isDemo)
        }.distinctUntilChanged(), historyRequest,
    ) { access, request -> access to request }.flatMapLatest { (access, request) ->
        val (pharmacyId, destination, authorized) = access
        if (!authorized || pharmacyId == null || (destination != "history" && !destination.startsWith("history:")))
            flowOf(HistoryUiState())
        else {
            val medication = destination.removePrefix("history:").takeIf { destination.startsWith("history:") }
            val effective = request.copy(filter = request.filter.copy(medicationId = medication ?: request.filter.medicationId))
            histories.observe(pharmacyId, effective.filter, effective.page)
                .map { HistoryUiState(effective, page = it, pharmacyId = pharmacyId, medicationScope = medication) }
                .onStart { emit(HistoryUiState(effective, loading = true, pharmacyId = pharmacyId, medicationScope = medication)) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(HistoryUiState(effective, error = uiText(R.string.ui_catalog_view_model_017),
                        pharmacyId = pharmacyId, medicationScope = medication))
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), HistoryUiState())

    internal fun openHistory(medication: String? = null) {
        historyRequest.value = HistoryRequest(filter = HistoryFilter(medicationId = medication))
        navigate(if (medication == null) "history" else "history:${medication}")
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
        if (adminBusy.value || sharing.status.value.busy || !online) {
            if (!automatic) feedback.value = UiFeedback(if (!online) uiText(R.string.logic_offline) else uiText(R.string.logic_busy), route.value)
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

    internal fun destinationStateKey(destination: String): String = if (destination.startsWith("edit-med:"))
        destination + ":" + savedState.get<String>("draft:$destination").orEmpty() else destination
    fun navigate(destination: String) {
        if (destination == route.value) return
        if (destination.startsWith("edit-med:")) savedState["draft:$destination"] = java.util.UUID.randomUUID().toString()
        val stack = savedState.get<ArrayList<String>>("backStack") ?: arrayListOf("home").also {
            if (route.value != "home") it.add(route.value)
        }
        if (destination == "home") stack.clear()
        if (stack.lastOrNull() != destination) stack.add(destination)
        savedState["backStack"] = stack
        changeRoute(destination)
    }
    private fun changeRoute(destination: String) {
        if (destination != "history" && !destination.startsWith("history:")) historyRequest.value = HistoryRequest()
        adminMessage.value = null
        feedback.value = null
        savedState["route"] = destination
    }
    fun back() {
        if (route.value == "edit-med:new") newMedicationName = ""
        if (route.value == "connection") {
            if (importState.value.busy) importOperation?.cancel()
            importState.update { it.copy(selectedUri = null, error = null) }
        }
        val stack = savedState.get<ArrayList<String>>("backStack") ?: arrayListOf("home", route.value)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) stack.add("home")
        savedState["backStack"] = stack
        changeRoute(stack.last())
    }

    init { reloadConnectionImport(); connectivity.registerDefaultNetworkCallback(networkObserver) }

    fun openConnectionImport(uri: Uri? = null) {
        if (importState.value.busy) return
        importState.update { it.copy(selectedUri = uri, error = null, verification = null) }
        navigate("connection")
    }

    fun reloadConnectionImport() {
        if (importOperation?.isActive == true) return
        importState.update { it.copy(loading = true, error = null) }
        importOperation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { restorePendingConnection() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(error)) } }
            finally { importState.update { it.copy(loading = false) } }
        }
    }

    /** Takes ownership of password; never place it in SavedStateHandle or a UI state flow. */
    fun stageConnection(password: CharArray) {
        val current = importState.value
        val uri = current.selectedUri
        if (current.loading || current.busy || !current.storageReady || current.pending != null || uri == null) {
            password.fill('\u0000'); return
        }
        importState.update { it.copy(busy = true, error = null) }
        importOperation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val pending = connectionImports.stage(uri, password)
                importState.update { it.copy(pending = pending.info, requirements = pending.requirements,
                    verification = null, selectedUri = null) }
            } catch (error: CancellationException) {
                // A protected atomic write may have finished before dispatch-back cancellation.
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) {
                importState.update { it.copy(error = connectionImportMessage(error)) }
            } finally {
                password.fill('\u0000')
                importState.update { it.copy(busy = false) }
            }
        }.also { operation -> operation.invokeOnCompletion { password.fill('\u0000') } }
    }

    fun cancelConnectionImport() { if (importState.value.busy) importOperation?.cancel() }

    fun verifyPendingConnection(id: String) {
        val current = importState.value
        if (current.loading || current.busy || !current.storageReady || current.pending?.id != id) return
        importState.update { it.copy(busy = true, verifying = true, verification = null, error = null) }
        importOperation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val verification = connectionImports.verify(id)
                importState.update { if (it.pending?.id == id)
                    it.copy(verification = verification, requirements = verification.requirements) else it }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    try {
                        restorePendingConnection()
                        importState.update { it.copy(error = connectionVerificationMessage(error)) }
                    } catch (failure: Exception) {
                        importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) }
                    }
                }
            } finally { importState.update { it.copy(busy = false, verifying = false) } }
        }
    }

    fun activatePendingConnection(id: String) {
        val current = importState.value
        if (current.loading || current.busy || !current.storageReady || current.pending?.id != id) return
        importState.update { it.copy(busy = true, activating = true, verification = null, error = null) }
        importOperation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                connectionImports.activate(id)
                restorePendingConnection()
                navigate("home")
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    try {
                        restorePendingConnection()
                        // A completed transaction may only have needed pending-marker cleanup.
                        if (importState.value.pending == null) navigate("home")
                        else importState.update { it.copy(error = connectionVerificationMessage(error)) }
                    } catch (failure: Exception) {
                        importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) }
                    }
                }
            } finally { importState.update { it.copy(busy = false, activating = false) } }
        }
    }

    fun discardPendingConnection(id: String) {
        if (importState.value.loading || importState.value.busy || !importState.value.storageReady) return
        importState.update { it.copy(busy = true, error = null) }
        importOperation = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                connectionImports.discard(id)
                restorePendingConnection()
                importState.update { it.copy(selectedUri = null) }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) { importState.update { it.copy(error = connectionImportMessage(error)) } }
            finally { importState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun restorePendingConnection() {
        val pending = connectionImports.loadPending()
        importState.update { it.copy(pending = pending.info, requirements = pending.requirements,
            verification = null, storageReady = true) }
    }
    fun selectSection(id: String) {
        val ready = state.value.catalog as? CatalogState.Ready ?: return
        val pharmacy = ready.catalog.pharmacy?.pharmacyId ?: return
        if (ready.catalog.sections.none { it.id == id && it.isActive }) return
        savedState["sectionPharmacy"] = pharmacy
        savedState["section"] = id
        viewModelScope.launch { preferences.setSection(pharmacy, id) }
    }
    fun setTheme(theme: ThemeChoice) { viewModelScope.launch { preferences.setTheme(theme) } }
    fun setAccent(accent: ThemeAccent) { viewModelScope.launch { preferences.setAccent(accent) } }
    fun setQuery(value: String) { savedState["query"] = value }
    fun toggleStatus(value: AvailabilityStatus) {
        if (value !in MedicationPresentation.editableStatuses) return
        savedState["statuses"] = ArrayList(statuses.value.toSet().let { if (value.name in it) it - value.name else it + value.name })
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
    fun clearFilters() { savedState["statuses"] = arrayListOf<String>(); savedState["categories"] = arrayListOf<String>() }
    fun setSort(value: HomeSort) { viewModelScope.launch { preferences.setSort(value) } }
    fun setGroupingEnabled(value: Boolean) { viewModelScope.launch { preferences.setGroupingEnabled(value) } }
    fun setGrouping(value: HomeGrouping) { viewModelScope.launch { preferences.setGrouping(value) } }
    fun setTwoColumns(value: Boolean) { viewModelScope.launch { preferences.setTwoColumns(value) } }
    fun setLayout(value: CardLayout) { viewModelScope.launch { preferences.setLayout(value) } }
    fun setSecondaryHijri(value: Boolean) { viewModelScope.launch { preferences.setSecondaryHijri(value) } }
    fun setNames(value: NameDisplay) { viewModelScope.launch { preferences.setNames(value) } }
}
