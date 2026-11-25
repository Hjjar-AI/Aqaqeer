package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun PharmacyAdminScreen(state: AppUiState, vm: CatalogViewModel) {
    val uiText = rememberUiText()
    val ready = state.catalog as? CatalogState.Ready ?: return
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val sharing by vm.sharing.status.collectAsStateWithLifecycle()
    val pending by vm.pendingEdits.collectAsStateWithLifecycle()
    val catalog = ready.catalog
    val leaveNameState = rememberSaveable(state.route) { mutableStateOf("") }
    val confirmedLeaveState = rememberSaveable(state.route) { mutableStateOf(false) }
    val submitted = rememberSaveable(state.route) { mutableStateOf(false) }
    if (state.destination is Route.EditMedication) {
        if (!ready.admin || ready.requiresUpdate || ready.maintenancePaused) {
            FormPage { InformationCard(uiText(R.string.catalog_edit_unavailable_title), uiText(R.string.catalog_edit_authorization_required_message)) }
            return
        }
        val targetId = state.destination.id ?: "new"
        val medication = catalog.medications.find { it.id == targetId }
        if (targetId != "new" && medication == null) { FormPage { Text(uiText(R.string.catalog_medication_missing_message)) }; return }
        key(catalog.pharmacy?.pharmacyId, targetId) { MedicationEditor(catalog, medication, vm, busy) }
        return
    }
    if (state.destination == Route.Review && ready.admin) {
        PublicationReviewScreen(ready, vm, busy, message, sharing, pending)
        return
    }
    FormPage(showFieldConvention = state.destination in setOf(Route.Create, Route.Promote, Route.Setup, Route.ExportConnection, Route.Taxonomy)) {
        val formFocus = LocalFormFocus.current
        val formScope = rememberCoroutineScope()
        fun revealInvalid() { submitted.value = true; formScope.launch { formFocus?.focusFirstInvalid() } }
        OperationStatus(busy || sharing.busy, message, sharing.message)
        when {
            state.destination == Route.Create -> { CreatePharmacyContent(state, ready, vm, busy, submitted, ::revealInvalid) }
            state.destination == Route.Promote -> { PromoteAdminContent(vm, busy, submitted, ::revealInvalid) }
            state.destination == Route.Leave -> { LeavePharmacyContent(ready, vm, busy, submitted, ::revealInvalid, leaveNameState, confirmedLeaveState) }
            (!ready.admin && state.destination != Route.Admin) -> NoticeStrip(Notice(uiText(R.string.shared_admin_authorization_required_message), NoticeSeverity.WARNING))
            ready.requiresUpdate && state.destination !in setOf(Route.Review, Route.Admin) -> NoticeStrip(Notice(uiText(R.string.shared_compatible_version_required_message), NoticeSeverity.WARNING))
            ready.maintenancePaused && state.destination !in setOf(Route.Admin, Route.Review) -> NoticeStrip(Notice(uiText(R.string.maintenance_complete_before_editing_message), NoticeSeverity.WARNING))
            state.destination == Route.Setup -> TelegramSetup(vm, busy, catalog.medications.isEmpty())
            state.destination == Route.ExportConnection -> ConnectionExport(vm, ready.lifecycle == "SHARED", busy)
            state.destination == Route.Taxonomy -> TaxonomyEditor(catalog, vm, busy)
            else -> { AdminHubContent(ready, vm) }
        }
    }
}
