package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.*

@Composable internal fun PharmacyAdminScreen(state: AppUiState, vm: CatalogViewModel) {
    val uiText = rememberUiText()
    val ready = state.catalog as? CatalogState.Ready ?: return
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val sharing by vm.sharing.status.collectAsStateWithLifecycle()
    val pending by vm.pendingEdits.collectAsStateWithLifecycle()
    val catalog = ready.catalog
    var leaveName by rememberSaveable(state.route) { mutableStateOf("") }
    var confirmedLeave by rememberSaveable(state.route) { mutableStateOf(false) }
    var formSubmitted by rememberSaveable(state.route) { mutableStateOf(false) }
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
        val configuration = LocalConfiguration.current
        var typeFilter by rememberSaveable(catalog.pharmacy?.pharmacyId) { mutableStateOf<String?>(null) }
        var choices by remember(catalog.pharmacy?.pharmacyId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
        val types = remember(pending) { pending.groupBy { it.entityType } }
        val grouped = remember(pending) { pending.groupBy { it.entityType to it.targetId.substringBefore('/') } }
        val visibleGroups = remember(grouped, typeFilter) {
            grouped.entries.filter { typeFilter == null || it.key.first == typeFilter }
        }
        val medicationsById = remember(catalog.medications) { catalog.medications.associateBy { it.id } }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.page),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
            item(key = "review_header") {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                    OperationStatus(busy || sharing.busy, message, sharing.message)
                    if (ready.maintenancePaused) NavigationCard(uiText(R.string.maintenance_review_first_action), onClick = { vm.navigate(Route.Maintenance) })
                    if (ready.restorePaused) NavigationCard(uiText(R.string.backup_complete_restore_review_action), onClick = { vm.navigate(Route.Backup) })
                    Text(uiText(R.string.publication_pending_count_format, MedicationPresentation.displayDigits(ready.pendingCount.toString())), style = MaterialTheme.typography.titleLarge)
                    Text(if (ready.maintenancePaused) uiText(R.string.publication_maintenance_block_message) else if (ready.restorePaused) uiText(R.string.publication_restore_review_block_message) else uiText(R.string.publication_automatic_reconnect_help))
                    WrappingContent {
                        AppChip(typeFilter == null, onClick = { typeFilter = null }, label = { Text(uiText(R.string.review_all) + " (${pending.size})") })
                        types.forEach { (type, operations) ->
                            AppChip(typeFilter == type, onClick = { typeFilter = type }, label = { Text(entityLabel(type, text = uiText) + " (${operations.size})") })
                        }
                    }
                }
            }
            items(visibleGroups, key = { "pending_${it.key.first.length}:${it.key.first}${it.key.second}" }) { group ->
                val operations = group.value
                val first = operations.first()
                val name = medicationsById[group.key.second]
                    ?.let { MedicationPresentation.identity(it, catalog.formsById[it.formId]?.nameAr) } ?: entityLabel(first.entityType, text = uiText)
                GroupedCard(title = name) {
                    operations.forEach { op ->
                        key(op.id) {
                            val before = remember(op.field, op.baseValueJson, catalog, configuration) {
                                reviewValue(op.field, op.baseValueJson, catalog, text = uiText)
                            }
                            val after = remember(op.field, op.intendedValueJson, catalog, configuration) {
                                reviewValue(op.field, op.intendedValueJson, catalog, text = uiText)
                            }
                            Text(fieldLabel(op.field, op.entityType, text = uiText), style = MaterialTheme.typography.titleSmall)
                            LabeledValue(uiText(R.string.publication_before_label), before)
                            LabeledValue(uiText(R.string.publication_after_label), after)
                        }
                    }
                }
            }
            item(key = "review_actions") {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                    if (ready.isDemo) SupportingText(uiText(R.string.review_demo_local_only))
                    ConflictChoices(sharing.conflicts, choices, catalog) { choices = it }
                    AppButton(enabled = !ready.isDemo && (ready.pendingCount > 0 || ready.uncertain || ready.lifecycle == "LOCAL_DRAFT") && !busy && !sharing.busy && !ready.requiresUpdate && !ready.restorePaused && !ready.maintenancePaused, onClick = { vm.publish(choices) }) { Text(uiText(R.string.publication_publish_verify_action)) }
                    if (!ready.isDemo && ready.lifecycle == "SHARED") PinRecoveryAction(
                        enabled = !busy && !sharing.busy && !ready.requiresUpdate && !ready.restorePaused && !ready.maintenancePaused,
                        recover = { vm.publish(choices, recoverPin = true) })
                }
            }
        }
        return
    }
    FormPage(showFieldConvention = state.destination in setOf(Route.Create, Route.Promote, Route.Setup, Route.ExportConnection, Route.Taxonomy)) {
        val formFocus = LocalFormFocus.current
        val formScope = rememberCoroutineScope()
        fun revealInvalid() { formSubmitted = true; formScope.launch { formFocus?.focusFirstInvalid() } }
        OperationStatus(busy || sharing.busy, message, sharing.message)
        when {
            state.destination == Route.Create -> {
                if (catalog.pharmacy != null || ready.isDemo) { Text(uiText(R.string.catalog_device_already_configured_message)); return@FormPage }
                var name by rememberSaveable(state.route) { mutableStateOf("") }; var zone by rememberSaveable(state.route) { mutableStateOf("Asia/Damascus") }
                var section by rememberSaveable(state.route) { mutableStateOf(uiText(R.string.catalog_initial_section_default_name)) }
                var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
                var pinEntryVersion by remember { mutableStateOf(0) }
                val focus = LocalFocusManager.current
                val creating = busy || vm.adminBusy.value
                GroupedCard(title = uiText(R.string.catalog_pharmacy_setup_title)) {
                    FormField(uiText(R.string.catalog_pharmacy_name_label), name, { name = it }, submitted = formSubmitted, required = true); TimezoneField(zone) { zone = it }
                    FormField(uiText(R.string.catalog_initial_section_name_label), section, { section = it }, submitted = formSubmitted, required = true)
                    if (creating) SupportingText(uiText(R.string.catalog_creation_progress))
                    else key(pinEntryVersion) {
                        SecretField(uiText(R.string.catalog_creation_admin_pin_label), pin, { pin = it }, submitted = formSubmitted, required = true, numericSecret = true, validate = FormGuidance::pin, rule = uiText(R.string.catalog_creation_admin_pin_rule), tag = uiText(R.string.catalog_admin_pin_label_with_guidance))
                        SecretField(uiText(R.string.catalog_admin_pin_confirmation_label), confirm, { confirm = it }, submitted = formSubmitted, required = true, numericSecret = true, imeAction = ImeAction.Done, validate = {
                                if (it.map { c -> Character.digit(c, 10) } == pin.map { c -> Character.digit(c, 10) }) null
                                else uiText(R.string.catalog_admin_pin_mismatch_error)
                            })
                    }
                }
                Text(uiText(R.string.catalog_offline_creation_help))
                AppButton(enabled = !creating, onClick = {
                    if (vm.adminBusy.value) return@AppButton
                    formSubmitted = true
                    if (name.isBlank() || section.isBlank() || FormGuidance.timezone(zone) != null ||
                        FormGuidance.pin(pin) != null || FormGuidance.pin(confirm) != null ||
                        pin.map { Character.digit(it, 10) } != confirm.map { Character.digit(it, 10) }) {
                        revealInvalid(); return@AppButton
                    }
                    val a = pin.toCharArray(); val b = confirm.toCharArray()
                    formSubmitted = false; pinEntryVersion++
                    focus.clearFocus()
                    pin = ""; confirm = ""
                    vm.secretCommand(Route.Home, a, b) { vm.admin.create(name, zone, section, a, b) }
                }) { Text(uiText(R.string.catalog_create_pharmacy_action)) }
            }
            state.destination == Route.Promote -> {
                var pin by remember { mutableStateOf("") }
                GroupedCard(title = uiText(R.string.catalog_authorize_admin_title)) {
                    SecretField(uiText(R.string.catalog_admin_pin_label), pin, { pin = it }, submitted = formSubmitted, required = true, numericSecret = true, imeAction = ImeAction.Done, validate = FormGuidance::pin)
                }
                Text(uiText(R.string.catalog_admin_authorization_help))
                AppButton(enabled = !busy, onClick = {
                    formSubmitted = true
                    if (FormGuidance.pin(pin) != null) { revealInvalid(); return@AppButton }
                    val p = pin.toCharArray(); pin = ""; vm.secretCommand(Route.Home, p) { vm.admin.promote(p) }
                }) { Text(uiText(R.string.navigation_promote_admin_title)) }
            }
            state.destination == Route.Leave -> {
                Text(uiText(R.string.navigation_leave_pharmacy_title), style = MaterialTheme.typography.headlineSmall)
                GroupedCard(title = uiText(R.string.catalog_leave_confirmation_title)) {
                    Text(uiText(R.string.catalog_leave_consequences_help))
                    if (ready.admin) FormField(uiText(R.string.catalog_leave_pharmacy_name_label), leaveName, { leaveName = it }, submitted = formSubmitted, required = true, validate = { if (it == catalog.pharmacy?.name) null else uiText(R.string.catalog_leave_pharmacy_name_mismatch_error) })
                    CheckChoiceRow(confirmedLeave, uiText(R.string.catalog_leave_acknowledgement_label)) { confirmedLeave = it }
                    if (ready.uncertain) NoticeStrip(Notice(uiText(R.string.catalog_leave_uncertain_publication_warning), NoticeSeverity.WARNING))
                    AppButton(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), enabled = confirmedLeave && !busy, onClick = {
                        formSubmitted = true
                        if (ready.admin && leaveName != catalog.pharmacy?.name) { revealInvalid(); return@AppButton }
                        vm.command(Route.Home) { vm.admin.leave(leaveName, true) }
                    }) { Text(uiText(R.string.catalog_leave_delete_local_action)) }
                }
            }
            (!ready.admin && state.destination != Route.Admin) -> Text(uiText(R.string.shared_admin_authorization_required_message))
            ready.requiresUpdate && state.destination !in setOf(Route.Review, Route.Admin) -> Text(uiText(R.string.shared_compatible_version_required_message))
            ready.maintenancePaused && state.destination !in setOf(Route.Admin, Route.Review) -> Text(uiText(R.string.maintenance_complete_before_editing_message))
            ready.isDemo && state.destination == Route.Admin -> {
                InformationCard(uiText(R.string.review_demo_admin), uiText(R.string.review_demo_edit_help))
                NavigationCard(uiText(R.string.navigation_add_medication_title), { vm.navigate(Route.EditMedication()) })
                NavigationCard(uiText(R.string.navigation_taxonomy_title), { vm.navigate(Route.Taxonomy) })
                NavigationCard(uiText(R.string.navigation_history_title), { vm.openHistory() })
                NavigationCard(uiText(R.string.navigation_publication_review_title), vm::review)
            }
            ready.isDemo && state.destination !in setOf(Route.Taxonomy, Route.Review) -> Text(uiText(R.string.review_demo_local_only))
            state.destination == Route.Setup -> TelegramSetup(vm, busy, catalog.medications.isEmpty())
            state.destination == Route.ExportConnection -> ConnectionExport(vm, ready.lifecycle == "SHARED", busy)
            state.destination == Route.Taxonomy -> TaxonomyEditor(catalog, vm, busy)
            else -> {
                if (ready.admin && !ready.isDemo) SetupChecklist(ready) { vm.navigate(it) }
                if (!ready.admin) {
                    NavigationCard(uiText(R.string.navigation_promote_admin_title), { vm.navigate(Route.Promote) })
                    NavigationCard(uiText(R.string.navigation_maintenance_title), { vm.navigate(Route.Maintenance) })
                    NavigationCard(uiText(R.string.navigation_leave_pharmacy_title), { vm.navigate(Route.Leave) }, destructive = true)
                    return@FormPage
                }
                NavigationCard(uiText(R.string.navigation_backup_title), onClick = { vm.navigate(Route.Backup) })
                NavigationCard(uiText(R.string.navigation_leave_pharmacy_title), { vm.navigate(Route.Leave) }, destructive = true)
                if (ready.lifecycle == "LOCAL_DRAFT") InformationCard(uiText(R.string.publication_local_draft_label), uiText(R.string.publication_local_draft_help))
                NavigationGroup(uiText(R.string.catalog_device_connection_heading)) {
                    NavigationRow(uiText(R.string.navigation_history_title), { vm.openHistory() })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.maintenance_connection_pin_action), { vm.navigate(Route.Maintenance) })
                }
                if (ready.maintenancePaused) {
                    Text(uiText(R.string.maintenance_editing_publication_blocked_message))
                    NavigationCard(uiText(R.string.navigation_leave_pharmacy_title), onClick = { vm.navigate(Route.Leave) }, destructive = true)
                    return@FormPage
                }
                if (ready.requiresUpdate) {
                    Text(uiText(R.string.shared_update_editing_blocked_help))
                    NavigationCard(uiText(R.string.publication_review_retained_work_action), vm::review)
                    return@FormPage
                }
                NavigationGroup(uiText(R.string.catalog_catalog_heading)) {
                    NavigationRow(uiText(R.string.navigation_add_medication_title), { vm.navigate(Route.EditMedication()) })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.navigation_taxonomy_title), { vm.navigate(Route.Taxonomy) })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.exchange_csv_import_navigation_label), { vm.navigate(Route.CsvImport) })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.exchange_reports_navigation_label), { vm.navigate(Route.Reports) })
                }
                NavigationGroup(uiText(R.string.connection_sharing_heading)) {
                    if (ready.lifecycle == "LOCAL_DRAFT") NavigationRow(uiText(R.string.navigation_telegram_setup_title), { vm.navigate(Route.Setup) })
                    if (ready.lifecycle == "LOCAL_DRAFT") HorizontalDivider()
                    NavigationRow(uiText(R.string.navigation_publication_review_title), vm::review)
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.navigation_connection_export_title), { vm.navigate(Route.ExportConnection) }, enabled = ready.lifecycle == "SHARED" && !ready.restorePaused)
                }
            }
        }
    }
}

private fun entityLabel(type: String, text: UiText? = null) = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    when (type) { "medication" -> uiText(R.string.catalog_entity_medication_label); "availability" -> uiText(R.string.catalog_entity_availability_label); "category" -> uiText(R.string.catalog_entity_category_label); "form" -> uiText(R.string.catalog_entity_dosage_form_label); "section" -> uiText(R.string.catalog_entity_section_label); else -> uiText(R.string.catalog_entity_pharmacy_label) }
}

internal fun fieldLabel(field: String, entityType: String? = null, text: UiText? = null) = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    when(field) {
    "connectionActivated" -> uiText(R.string.history_connection_activated_label); "maintenanceFinished" -> uiText(R.string.history_maintenance_completed_label)
    "record" -> uiText(R.string.history_observed_entity_change_label); "adminPinVerifier" -> uiText(R.string.history_admin_pin_verifier_label)
    "restoreReconciled" -> uiText(R.string.history_restore_reconciled_label); "restoreReplacementReviewed" -> uiText(R.string.history_restore_replacement_reviewed_label)
    "@entity" -> uiText(R.string.history_entity_change_label); "nameEn" -> if (entityType == "medication") uiText(R.string.catalog_medication_trade_name_english_label) else uiText(R.string.catalog_english_name_label); "nameAr" -> if (entityType == "medication") uiText(R.string.catalog_medication_trade_name_arabic_label) else uiText(R.string.catalog_arabic_name_label)
    "status" -> uiText(R.string.catalog_entity_availability_label); "strength" -> uiText(R.string.catalog_strength_label); "genericAr" -> uiText(R.string.catalog_scientific_name_arabic_label)
    "genericEn" -> uiText(R.string.catalog_scientific_name_english_label); "brand" -> uiText(R.string.catalog_brand_label); "note" -> uiText(R.string.catalog_note_label)
    "expiryDate" -> uiText(R.string.catalog_expiry_date_label)
    "expirySoonThresholdDays" -> uiText(R.string.catalog_expiry_warning_days_label); "categoryId" -> uiText(R.string.catalog_category_label); "formId" -> uiText(R.string.catalog_dosage_form_label)
    "isActive" -> uiText(R.string.catalog_active_label); "orderIndex" -> uiText(R.string.catalog_order_label); else -> field
}
}

internal fun reviewValue(field: String, raw: String, catalog: CatalogSnapshot, limit: Int = 500, text: UiText? = null): String {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    if (field == uiText(R.string.publication_changes_review_label)) return raw
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
    if (element == JsonNull) return if (field == "@entity") uiText(R.string.publication_deleted_value_label) else uiText(R.string.publication_empty_value_label)
    if (element is JsonObject) return MedicationPresentation.recordIdentity(element, catalog.forms) ?: uiText(R.string.publication_related_entity_label)
    val value = (element as? JsonPrimitive)?.contentOrNull ?: raw
    return when (field) {
        "status" -> runCatching { statusLabel(AvailabilityStatus.valueOf(value), text = uiText) }.getOrDefault(value)
        "categoryId" -> catalog.categories.find { it.id == value }?.nameAr ?: uiText(R.string.catalog_deleted_category_label)
        "formId" -> catalog.forms.find { it.id == value }?.nameAr ?: uiText(R.string.catalog_deleted_form_label)
        "isActive" -> if (value == "true") uiText(R.string.shared_yes_label) else uiText(R.string.shared_no_label)
        else -> value.take(limit)
    }
}


@Composable private fun TelegramSetup(vm: CatalogViewModel, busy: Boolean, empty: Boolean) {
    val uiText = rememberUiText()
    var token by remember { mutableStateOf("") }; var group by remember { mutableStateOf("") }
    var title by remember(token, group) { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var offerEmptyPublication by remember { mutableStateOf(false) }
    val challenge = remember { UUID.randomUUID().toString().replace("-", "").take(24) }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    SectionHeading(uiText(R.string.connection_create_bot_step_title))
    SupportingText(uiText(R.string.connection_create_bot_step_help))
    SectionHeading(uiText(R.string.connection_setup_group_step_title))
    SupportingText(uiText(R.string.connection_setup_group_step_help))
    SectionHeading(uiText(R.string.connection_discover_group_step_title))
    SupportingText(uiText(R.string.connection_discover_group_step_help))
    StepHeader(listOf(uiText(R.string.connection_discover_group_action), uiText(R.string.connection_verify_group_action), uiText(R.string.connection_save_setup_action)),
        if (title != null) 2 else if (group.isNotBlank()) 1 else 0)
    GroupedCard(title = uiText(R.string.connection_telegram_details_title)) {
        SecretField(uiText(R.string.connection_bot_token_label), token, { token = it }, required = true)
        SelectionContainer { Text(bidi("/aqsetup ${challenge}")) }
        AppButton(variant = AppButtonVariant.SECONDARY, onClick = { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Aqaqeer", "/aqsetup ${challenge}")) }) { Text(uiText(R.string.connection_copy_setup_command_action)) }
        AppButton(enabled = !busy && token.isNotBlank(), onClick = { vm.command { found = vm.sharing.discover(token, challenge) } }) { Text(uiText(R.string.connection_discover_group_action)) }
        if (found.isNotEmpty()) RadioChoiceRows(found, found.find { it.first.toString() == group },
            { group = it.first.toString() }, { it.second })
        FormField(uiText(R.string.connection_group_id_label), group, { group = groupIdInput(it) }, required = true, validate = FormGuidance::group, supportingText = uiText(R.string.connection_group_id_help), ltr = true, imeAction = ImeAction.Done)
        AppButton(enabled = !busy && group.toLongOrNull()?.let { it < 0 } == true, onClick = { vm.command { title = vm.sharing.inspectSetup(token, group.toLong()) } }) { Text(uiText(R.string.connection_verify_group_action)) }
        title?.let { name ->
            Text(uiText(R.string.connection_group_title_format, name))
            AppButton(enabled = !busy, onClick = { vm.command { vm.sharing.saveSetup(token, group.toLong(), name); token = ""; if (empty) offerEmptyPublication = true else vm.navigate(Route.Admin) } }) { Text(uiText(R.string.connection_save_setup_action)) }
        }
    }
    if (offerEmptyPublication) AppDialog(onDismissRequest = { offerEmptyPublication = false; vm.navigate(Route.Admin) },
        title = { Text(uiText(R.string.publication_empty_pharmacy_confirm_title)) },
        body = { Text(uiText(R.string.publication_empty_pharmacy_confirm_help)) },
        confirmButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { offerEmptyPublication = false; vm.navigate(Route.Admin) }) { Text(uiText(R.string.publication_defer_action)) } },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { offerEmptyPublication = false; vm.navigate(Route.Review); vm.publish() }) { Text(uiText(R.string.publication_publish_now_action)) } })
    SupportingText(uiText(R.string.pin_group_guidance))
    ExpandableInformation(uiText(R.string.navigation_telegram_setup_title), uiText(R.string.connection_dedicated_bot_help))
}

@Composable private fun PinRecoveryAction(enabled: Boolean, recover: () -> Unit) {
    val uiText = rememberUiText()
    GroupedCard(title = uiText(R.string.pin_recovery_title)) {
        SupportingText(uiText(R.string.pin_recovery_help))
        AppButton(variant = AppButtonVariant.QUIET, enabled = enabled, onClick = recover) { Text(uiText(R.string.pin_recovery_title)) }
    }
}

@Composable private fun ConnectionExport(vm: CatalogViewModel, shared: Boolean, busy: Boolean) {
    val uiText = rememberUiText()
    var password by remember { mutableStateOf("") }
    var outgoing by remember { mutableStateOf<CharArray?>(null) }
    var generated by remember { mutableStateOf(false) }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    DisposableEffect(Unit) { onDispose { outgoing?.fill('\u0000') } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.aqaqeer.connection")) { uri ->
        val pass = outgoing; outgoing = null
        if (uri == null) pass?.fill('\u0000') else if (pass != null) vm.secretCommand(null, pass) { vm.sharing.export(uri, pass) }
    }
    Text(uiText(R.string.connection_export_security_help))
    GroupedCard(title = uiText(R.string.connection_export_protection_title)) {
        SecretField(uiText(R.string.connection_file_password_label), password, { if (it.length <= 1024) { password = it; generated = false } }, required = true, validate = FormGuidance::pin, imeAction = ImeAction.Done, rule = uiText(R.string.connection_file_password_rule), tag = uiText(R.string.connection_file_password_label_with_guidance))
        SupportingText(uiText(R.string.logic_password_guidance))
        if (FormGuidance.simpleNumericPattern(password)) SupportingText(uiText(R.string.logic_password_pattern))
        ActionGroup {
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy && outgoing == null, onClick = {
                val random = java.security.SecureRandom()
                password = buildString { repeat(16) { append(('0'.code + random.nextInt(10)).toChar()) } }
                generated = true
            }) { Text(uiText(R.string.logic_generate_password)) }
            if (generated && password.isNotEmpty()) AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
                val clip = android.content.ClipData.newPlainText("Aqaqeer", password)
                if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply {
                    putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
                clipboard.setPrimaryClip(clip)
            }) { Text(uiText(R.string.logic_copy_password)) }
        }
        AppButton(enabled = shared && !busy && outgoing == null && FormGuidance.pin(password) == null, onClick = {
            outgoing = password.toCharArray(); password = ""; generated = false
            try { launcher.launch("pharmacy.aqz") } catch (_: Exception) {
                outgoing?.fill('\u0000'); outgoing = null
                vm.adminMessage.value = uiText(R.string.logic_file_destination_failed)
            }
        }) { Text(uiText(R.string.connection_save_file_action)) }
    }
}

@Composable private fun MedicationEditor(c: CatalogSnapshot, old: Medication?, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    val id = rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { old?.id ?: UUID.randomUUID().toString() }
    var en by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.nameEn ?: "") }; var ar by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.nameAr ?: "") }
    var strength by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.strength ?: "") }; var genericAr by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.genericAr ?: vm.newMedicationName.takeUnless { it.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }.orEmpty()) }
    var genericEn by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.genericEn ?: vm.newMedicationName.takeIf { it.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }.orEmpty()) }; var brand by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.brand ?: "") }
    var note by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.note ?: "") }; var expiry by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.expiryDate?.toString() ?: "") }
    var threshold by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf((old?.expirySoonThresholdDays ?: 60).toString()) }
    var category by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.categoryId ?: CatalogSeeds.UNCATEGORIZED_ID) }
    var form by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.formId ?: CatalogSeeds.defaultMedicationFormId(c.forms)) }
    var initialAvailability by rememberSaveable(id, stateSaver = InitialAvailabilitySaver) {
        mutableStateOf(emptyMap<String, AvailabilityStatus>())
    }
    var confirmDelete by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(false) }
    var submitted by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(false) }
    val formFocus = remember { FormFocusController() }
    val scope = rememberCoroutineScope()
    var optionalOpen by rememberSaveable(id) { mutableStateOf(false) }
    val scientificError = if (genericAr.isBlank() && genericEn.isBlank()) uiText(R.string.catalog_scientific_name_required_error) else null
    val comparison = Medication(id, en.trim().takeIf(String::isNotEmpty), strength, form, category,
        Instant.EPOCH, Instant.EPOCH, nameAr = ar.trim().takeIf(String::isNotEmpty),
        genericAr = genericAr.trim().takeIf(String::isNotEmpty), genericEn = genericEn.trim().takeIf(String::isNotEmpty))
    val duplicates = MedicationDuplicates.find(comparison, remember(c.medications) { MedicationDuplicates.index(c.medications) }).toSet()
    var duplicateConfirmed by rememberSaveable(en, ar, genericAr, genericEn, strength, form, duplicates) { mutableStateOf(false) }
    fun save() {
        submitted = true
        if (scientificError != null || strength.isBlank() || FormGuidance.date(expiry) != null || FormGuidance.threshold(threshold) != null) {
            if (FormGuidance.threshold(threshold) != null) optionalOpen = true
            scope.launch { formFocus.focusFirstInvalid() }; return
        }
        if (duplicates.isNotEmpty() && !duplicateConfirmed) {
            vm.adminMessage.value = uiText(R.string.logic_duplicate_warning)
            return
        }
        fun optional(s: String) = s.trim().takeIf { it.isNotEmpty() }
        val initialChoices = if (old == null) initialAvailability.toMap() else null
        vm.command {
            vm.admin.saveMedication(Medication(id = id, nameEn = optional(en), strength = strength.trim(), formId = form,
                categoryId = category, createdAt = Instant.now(), updatedAt = Instant.now(),
                nameAr = optional(ar), genericAr = optional(genericAr), genericEn = optional(genericEn), brand = optional(brand),
                note = optional(note), expiryDate = optional(expiry)?.let(LocalDate::parse), expirySoonThresholdDays = threshold.toInt()), expectedPharmacy = c.pharmacy?.pharmacyId, requireExisting = old != null, confirmedDuplicates = if (duplicateConfirmed) duplicates else emptySet(),
                initialAvailability = initialChoices)
            vm.newMedicationName = ""
            vm.medicationSaved(id, old == null)
        }
    }
    FormPage(focusController = formFocus, showFieldConvention = true, footer = {
        AppButton(enabled = !busy, onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.catalog_save_local_action)) }
    }) {
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    InlineOperationStatus(busy, message)
    if (old != null) MedicationAvailabilityControls(c, old, busy, isSaving = { vm.adminBusy.value }) { selection, status ->
        vm.saveAvailability(selection, BulkMedicationChanges(status = status)) { }
    }
    if (old == null) SupportingText(uiText(R.string.availability_initial_status_help))
    if (duplicates.isNotEmpty()) CheckChoiceRow(duplicateConfirmed,
        uiText(R.string.logic_duplicate_confirm), enabled = !busy) { duplicateConfirmed = it }
    val expiryDay = runCatching { LocalDate.parse(expiry) }.getOrNull()
    if (expiryDay != null && expiryDay < MedicationPresentation.today(Instant.now(), c.pharmacy?.timezone ?: "Asia/Damascus"))
        SupportingText(uiText(R.string.logic_past_expiry))
    GroupedCard(title = uiText(R.string.catalog_medication_identity_heading)) {
        FormField(uiText(R.string.catalog_scientific_name_arabic_label), genericAr, { genericAr = it }, submitted = submitted, supportingText = uiText(R.string.catalog_scientific_name_required_help), validate = { scientificError })
        FormField(uiText(R.string.catalog_scientific_name_english_label), genericEn, { genericEn = it }, ltr = true, submitted = submitted, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words), supportingText = uiText(R.string.catalog_scientific_name_required_help), validate = { scientificError })
        FormField(uiText(R.string.catalog_medication_strength_label), strength, { strength = it }, submitted = submitted, required = true, rule = uiText(R.string.catalog_medication_strength_rule), tag = uiText(R.string.catalog_strength_label_with_guidance))
        AdaptivePickers(first = {
            ChoicePicker(uiText(R.string.catalog_dosage_form_picker_label), c.forms, c.forms.find { it.id == form }, { it.nameAr }, itemKey = { it.id }) { form = it.id }
        }, second = {
            ChoicePicker(uiText(R.string.catalog_category_label), c.categories, c.categories.find { it.id == category }, { it.nameAr }, itemKey = { it.id }) { category = it.id }
        })
        GregorianDateField(uiText(R.string.catalog_expiry_date_label), expiry, { expiry = it }, submitted = submitted)

    }
    if (old == null) NewMedicationAvailabilityControls(c, initialAvailability, busy,
        reset = { initialAvailability = emptyMap() }) { section, status ->
        initialAvailability = if (status == AvailabilityStatus.AVAILABLE) initialAvailability - section
            else initialAvailability + (section to status)
    }
    CollapsibleGroup(uiText(R.string.catalog_medication_optional_information_heading), optionalOpen, { optionalOpen = !optionalOpen }) {
    GroupedCard {
        FormField(uiText(R.string.catalog_expiry_warning_days_label), threshold, { threshold = MedicationPresentation.westernDigits(it) }, submitted = submitted, required = true, validate = FormGuidance::threshold, ltr = true, imeAction = ImeAction.Done, onDone = { if (!busy) save() })
    }
    GroupedCard(title = uiText(R.string.catalog_trade_name_label_with_guidance)) {
        FormField(uiText(R.string.catalog_medication_trade_name_arabic_label), ar, { ar = it }, submitted = submitted, required = false)
        FormField(uiText(R.string.catalog_medication_trade_name_english_label), en, { en = it }, ltr = true, submitted = submitted, required = false)
    }
    GroupedCard(title = uiText(R.string.catalog_additional_information_heading)) {
        FormField(uiText(R.string.catalog_brand_label), brand, { brand = it }, submitted = submitted, required = false); MultilineField(uiText(R.string.catalog_note_label), note, { note = it }, submitted = submitted, required = false)
    }
    }
    if (c.medications.any { other -> other.id != id &&
        listOfNotNull(other.genericAr, other.genericEn).map(::normalizedName).any { candidate ->
            listOf(genericAr, genericEn).filter(String::isNotBlank).map(::normalizedName).any { it == candidate }
        } }) Text(uiText(R.string.catalog_duplicate_medication_warning))
    if (old != null) {
        GroupedCard(title = uiText(R.string.catalog_delete_medication_action)) {
            CheckChoiceRow(confirmDelete, uiText(R.string.catalog_confirm_medication_delete_label)) { confirmDelete = it }
            DestructiveConfirmButton(enabled = !busy && confirmDelete, onClick = { vm.command(Route.Home) { vm.admin.delete("medication", id) } }, label = uiText(R.string.catalog_delete_medication_action))
        }
    }
    }
}

@Composable private fun TaxonomyEditor(c: CatalogSnapshot, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    var type by remember { mutableStateOf("section") }; var selected by remember(type) { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var english by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }; var deleteName by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    fun resetConfirmation() { deleteName = ""; deleteConfirm = false }
    fun resetForm() { selected = null; name = ""; english = ""; active = true; resetConfirmation() }
    ChoicePicker(uiText(R.string.catalog_taxonomy_type_label), listOf("section", "category", "form"), type, ::entityLabel, itemKey = { it }) { type = it; resetForm() }
    data class RowValue(val id: String, val ar: String, val en: String?, val protected: Boolean, val active: Boolean = true)
    val rows = when (type) {
        "section" -> c.sections.map { RowValue(it.id, it.nameAr, it.nameEn, false, it.isActive) }
        "category" -> c.categories.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
        else -> c.forms.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
    }
    GroupedCard(title = uiText(R.string.catalog_taxonomy_items_heading)) {
        RadioChoiceRows(rows, rows.find { it.id == selected }, { row ->
            resetConfirmation()
            selected = row.id; name = row.ar; english = row.en ?: ""; active = row.active
        }, { row -> row.ar + when { row.protected -> uiText(R.string.catalog_protected_suffix); !row.active -> uiText(R.string.catalog_inactive_suffix); else -> "" } },
            choiceEnabled = { !it.protected })
    }
    AppButton(variant = AppButtonVariant.QUIET, onClick = ::resetForm) { Text(uiText(R.string.catalog_add_taxonomy_action)) }
    GroupedCard(title = uiText(R.string.catalog_edit_taxonomy_title)) {
        FormField(uiText(R.string.catalog_arabic_name_label), name, { name = it }, required = true); FormField(uiText(R.string.catalog_taxonomy_english_name_label), english, { english = it }, required = false, rule = uiText(R.string.catalog_taxonomy_english_name_rule), tag = uiText(R.string.catalog_english_name_label_with_guidance))
        if (type == "section") CheckChoiceRow(active, uiText(R.string.catalog_active_section_label)) { active = it }
        AppButton(enabled = !busy && name.isNotBlank(), onClick = { vm.command { vm.admin.saveTaxonomy(type, selected, name, english, active); resetForm() } }) { Text(uiText(R.string.catalog_save_taxonomy_action)) }
    }
    selected?.let { id ->
        GroupedCard(title = uiText(R.string.catalog_delete_taxonomy_action)) {
            Text(uiText(R.string.catalog_taxonomy_delete_consequences_help))
            if (type == "section") FormField(uiText(R.string.catalog_delete_section_name_confirmation_label), deleteName, { deleteName = it }, required = true)
            CheckChoiceRow(deleteConfirm, uiText(R.string.catalog_taxonomy_delete_confirmation_label)) { deleteConfirm = it }
            DestructiveConfirmButton(enabled = !busy && deleteConfirm, onClick = { vm.command { vm.admin.delete(type, id, deleteName); resetForm() } }, label = uiText(R.string.catalog_confirm_delete_action))
        }
    }
}
