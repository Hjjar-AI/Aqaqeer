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
            FormPage { InformationCard(uiText(R.string.ui_pharmacy_admin_screen_001), uiText(R.string.ui_pharmacy_admin_screen_002)) }
            return
        }
        val targetId = (state.destination as Route.EditMedication).id ?: "new"
        val medication = catalog.medications.find { it.id == targetId }
        if (targetId != "new" && medication == null) { FormPage { Text(uiText(R.string.ui_pharmacy_admin_screen_003)) }; return }
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
                    if (ready.maintenancePaused) NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_029), onClick = { vm.navigate(Route.Maintenance) })
                    if (ready.restorePaused) NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_030), onClick = { vm.navigate(Route.Backup) })
                    Text(uiText(R.string.ui_pharmacy_admin_screen_031, MedicationPresentation.displayDigits(ready.pendingCount.toString())), style = MaterialTheme.typography.titleLarge)
                    Text(if (ready.maintenancePaused) uiText(R.string.ui_pharmacy_admin_screen_032) else if (ready.restorePaused) uiText(R.string.ui_pharmacy_admin_screen_033) else uiText(R.string.ui_pharmacy_admin_screen_034))
                    WrappingContent {
                        AppChip(typeFilter == null, onClick = { typeFilter = null }, label = { Text(uiText(R.string.review_all) + " (${pending.size})") })
                        types.forEach { (type, operations) ->
                            AppChip(typeFilter == type, onClick = { typeFilter = type }, label = { Text(entityLabel(type) + " (${operations.size})") })
                        }
                    }
                }
            }
            items(visibleGroups, key = { "pending_${it.key.first.length}:${it.key.first}${it.key.second}" }) { group ->
                val operations = group.value
                val first = operations.first()
                val name = medicationsById[group.key.second]
                    ?.let { MedicationPresentation.identity(it, catalog.formsById[it.formId]?.nameAr) } ?: entityLabel(first.entityType)
                GroupedCard(title = name) {
                    operations.forEach { op ->
                        key(op.id) {
                            val before = remember(op.field, op.baseValueJson, catalog, configuration) {
                                reviewValue(op.field, op.baseValueJson, catalog)
                            }
                            val after = remember(op.field, op.intendedValueJson, catalog, configuration) {
                                reviewValue(op.field, op.intendedValueJson, catalog)
                            }
                            Text(fieldLabel(op.field, op.entityType), style = MaterialTheme.typography.titleSmall)
                            LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_035), before)
                            LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_036), after)
                        }
                    }
                }
            }
            item(key = "review_actions") {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                    if (ready.isDemo) SupportingText(uiText(R.string.review_demo_local_only))
                    ConflictChoices(sharing.conflicts, choices, catalog) { choices = it }
                    AppButton(enabled = !ready.isDemo && (ready.pendingCount > 0 || ready.uncertain || ready.lifecycle == "LOCAL_DRAFT") && !busy && !sharing.busy && !ready.requiresUpdate && !ready.restorePaused && !ready.maintenancePaused, onClick = { vm.publish(choices) }) { Text(uiText(R.string.ui_pharmacy_admin_screen_037)) }
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
                if (catalog.pharmacy != null || ready.isDemo) { Text(uiText(R.string.ui_pharmacy_admin_screen_004)); return@FormPage }
                var name by rememberSaveable(state.route) { mutableStateOf("") }; var zone by rememberSaveable(state.route) { mutableStateOf("Asia/Damascus") }
                var section by rememberSaveable(state.route) { mutableStateOf(uiText(R.string.ui_pharmacy_admin_screen_005)) }
                var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
                var pinEntryVersion by remember { mutableStateOf(0) }
                val focus = LocalFocusManager.current
                val creating = busy || vm.adminBusy.value
                GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_006)) {
                    FormField(uiText(R.string.ui_pharmacy_admin_screen_007), name, { name = it }, submitted = formSubmitted, required = true); TimezoneField(zone) { zone = it }
                    FormField(uiText(R.string.ui_pharmacy_admin_screen_009), section, { section = it }, submitted = formSubmitted, required = true)
                    if (creating) SupportingText(uiText(R.string.ui_pharmacy_admin_screen_010))
                    else key(pinEntryVersion) {
                        FormField(uiText(R.string.ui_pharmacy_admin_screen_011), pin, { pin = it }, secret = true, submitted = formSubmitted,
                            required = true, numericSecret = true, validate = FormGuidance::pin)
                        FormField(uiText(R.string.ui_pharmacy_admin_screen_012), confirm, { confirm = it }, secret = true, submitted = formSubmitted,
                            required = true, numericSecret = true, imeAction = ImeAction.Done, validate = {
                                if (it.map { c -> Character.digit(c, 10) } == pin.map { c -> Character.digit(c, 10) }) null
                                else uiText(R.string.ui_pharmacy_admin_screen_013)
                            })
                    }
                }
                Text(uiText(R.string.ui_pharmacy_admin_screen_014))
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
                }) { Text(uiText(R.string.ui_pharmacy_admin_screen_015)) }
            }
            state.destination == Route.Promote -> {
                var pin by remember { mutableStateOf("") }
                GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_016)) {
                    FormField(uiText(R.string.ui_pharmacy_admin_screen_017), pin, { pin = it }, secret = true, submitted = formSubmitted, required = true, numericSecret = true, imeAction = ImeAction.Done, validate = FormGuidance::pin)
                }
                Text(uiText(R.string.ui_pharmacy_admin_screen_018))
                AppButton(enabled = !busy, onClick = {
                    formSubmitted = true
                    if (FormGuidance.pin(pin) != null) { revealInvalid(); return@AppButton }
                    val p = pin.toCharArray(); pin = ""; vm.secretCommand(Route.Home, p) { vm.admin.promote(p) }
                }) { Text(uiText(R.string.ui_screen_titles_006)) }
            }
            state.destination == Route.Leave -> {
                Text(uiText(R.string.ui_screen_titles_007), style = MaterialTheme.typography.headlineSmall)
                GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_019)) {
                    Text(uiText(R.string.ui_pharmacy_admin_screen_020))
                    if (ready.admin) FormField(uiText(R.string.ui_pharmacy_admin_screen_021), leaveName, { leaveName = it }, submitted = formSubmitted, required = true,
                        validate = { if (it == catalog.pharmacy?.name) null else uiText(R.string.ui_pharmacy_admin_screen_022) })
                    CheckChoiceRow(confirmedLeave, uiText(R.string.ui_pharmacy_admin_screen_023)) { confirmedLeave = it }
                    if (ready.uncertain) NoticeStrip(Notice(uiText(R.string.ui_pharmacy_admin_screen_025), NoticeSeverity.WARNING))
                    AppButton(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), enabled = confirmedLeave && !busy, onClick = {
                        formSubmitted = true
                        if (ready.admin && leaveName != catalog.pharmacy?.name) { revealInvalid(); return@AppButton }
                        vm.command(Route.Home) { vm.admin.leave(leaveName, true) }
                    }) { Text(uiText(R.string.ui_pharmacy_admin_screen_024)) }
                }
            }
            (!ready.admin && state.destination != Route.Admin) -> Text(uiText(R.string.ui_pharmacy_admin_screen_026))
            ready.requiresUpdate && state.destination !in setOf(Route.Review, Route.Admin) -> Text(uiText(R.string.ui_pharmacy_admin_screen_027))
            ready.maintenancePaused && state.destination !in setOf(Route.Admin, Route.Review) -> Text(uiText(R.string.ui_pharmacy_admin_screen_028))
            ready.isDemo && state.destination == Route.Admin -> {
                InformationCard(uiText(R.string.review_demo_admin), uiText(R.string.review_demo_edit_help))
                NavigationCard(uiText(R.string.ui_screen_titles_009), { vm.navigate(Route.EditMedication()) })
                NavigationCard(uiText(R.string.ui_screen_titles_005), { vm.navigate(Route.Taxonomy) })
                NavigationCard(uiText(R.string.ui_screen_titles_015), { vm.openHistory() })
                NavigationCard(uiText(R.string.ui_screen_titles_004), vm::review)
            }
            ready.isDemo && state.destination !in setOf(Route.Taxonomy, Route.Review) -> Text(uiText(R.string.review_demo_local_only))
            state.destination == Route.Setup -> TelegramSetup(vm, busy, catalog.medications.isEmpty())
            state.destination == Route.ExportConnection -> ConnectionExport(vm, ready.lifecycle == "SHARED", busy)
            state.destination == Route.Taxonomy -> TaxonomyEditor(catalog, vm, busy)
            else -> {
                if (ready.admin && !ready.isDemo) SetupChecklist(ready) { vm.navigate(it) }
                if (!ready.admin) {
                    NavigationCard(uiText(R.string.ui_screen_titles_006), { vm.navigate(Route.Promote) })
                    NavigationCard(uiText(R.string.ui_screen_titles_018), { vm.navigate(Route.Maintenance) })
                    NavigationCard(uiText(R.string.ui_screen_titles_007), { vm.navigate(Route.Leave) }, destructive = true)
                    return@FormPage
                }
                NavigationCard(uiText(R.string.ui_screen_titles_011), onClick = { vm.navigate(Route.Backup) })
                NavigationCard(uiText(R.string.ui_screen_titles_007), { vm.navigate(Route.Leave) }, destructive = true)
                if (ready.lifecycle == "LOCAL_DRAFT") InformationCard(uiText(R.string.ui_pharmacy_admin_screen_038), uiText(R.string.ui_pharmacy_admin_screen_039))
                NavigationGroup(uiText(R.string.ui_pharmacy_admin_screen_040)) {
                    NavigationRow(uiText(R.string.ui_screen_titles_015), { vm.openHistory() })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_pharmacy_admin_screen_041), { vm.navigate(Route.Maintenance) })
                }
                if (ready.maintenancePaused) {
                    Text(uiText(R.string.ui_pharmacy_admin_screen_042))
                    NavigationCard(uiText(R.string.ui_screen_titles_007), onClick = { vm.navigate(Route.Leave) }, destructive = true)
                    return@FormPage
                }
                if (ready.requiresUpdate) {
                    Text(uiText(R.string.ui_pharmacy_admin_screen_043))
                    NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_044), vm::review)
                    return@FormPage
                }
                NavigationGroup(uiText(R.string.ui_pharmacy_admin_screen_045)) {
                    NavigationRow(uiText(R.string.ui_screen_titles_009), { vm.navigate(Route.EditMedication()) })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_screen_titles_005), { vm.navigate(Route.Taxonomy) })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_pharmacy_admin_screen_046), { vm.navigate(Route.CsvImport) })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_pharmacy_admin_screen_047), { vm.navigate(Route.Reports) })
                }
                NavigationGroup(uiText(R.string.ui_pharmacy_admin_screen_048)) {
                    if (ready.lifecycle == "LOCAL_DRAFT") NavigationRow(uiText(R.string.ui_screen_titles_003), { vm.navigate(Route.Setup) })
                    if (ready.lifecycle == "LOCAL_DRAFT") HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_screen_titles_004), vm::review)
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_screen_titles_008), { vm.navigate(Route.ExportConnection) }, enabled = ready.lifecycle == "SHARED" && !ready.restorePaused)
                }
            }
        }
    }
}

private fun entityLabel(type: String) = when (type) { "medication" -> uiText(R.string.ui_pharmacy_admin_screen_049); "availability" -> uiText(R.string.ui_pharmacy_admin_screen_050); "category" -> uiText(R.string.ui_pharmacy_admin_screen_051); "form" -> uiText(R.string.ui_pharmacy_admin_screen_052); "section" -> uiText(R.string.ui_pharmacy_admin_screen_053); else -> uiText(R.string.ui_pharmacy_admin_screen_054) }
internal fun fieldLabel(field: String, entityType: String? = null) = when(field) {
    "connectionActivated" -> uiText(R.string.ui_pharmacy_admin_screen_055); "maintenanceFinished" -> uiText(R.string.ui_pharmacy_admin_screen_056)
    "record" -> uiText(R.string.ui_pharmacy_admin_screen_057); "adminPinVerifier" -> uiText(R.string.ui_pharmacy_admin_screen_058)
    "restoreReconciled" -> uiText(R.string.ui_pharmacy_admin_screen_059); "restoreReplacementReviewed" -> uiText(R.string.ui_pharmacy_admin_screen_060)
    "@entity" -> uiText(R.string.ui_pharmacy_admin_screen_061); "nameEn" -> if (entityType == "medication") uiText(R.string.ui_pharmacy_admin_screen_062) else uiText(R.string.ui_pharmacy_admin_screen_063); "nameAr" -> if (entityType == "medication") uiText(R.string.ui_pharmacy_admin_screen_064) else uiText(R.string.ui_pharmacy_admin_screen_065)
    "status" -> uiText(R.string.ui_pharmacy_admin_screen_050); "strength" -> uiText(R.string.ui_pharmacy_admin_screen_066); "genericAr" -> uiText(R.string.ui_pharmacy_admin_screen_067)
    "genericEn" -> uiText(R.string.ui_pharmacy_admin_screen_068); "brand" -> uiText(R.string.ui_pharmacy_admin_screen_069); "note" -> uiText(R.string.ui_pharmacy_admin_screen_070)
    "expiryDate" -> uiText(R.string.ui_pharmacy_admin_screen_071)
    "expirySoonThresholdDays" -> uiText(R.string.ui_pharmacy_admin_screen_072); "categoryId" -> uiText(R.string.ui_pharmacy_admin_screen_073); "formId" -> uiText(R.string.ui_pharmacy_admin_screen_074)
    "isActive" -> uiText(R.string.ui_pharmacy_admin_screen_075); "orderIndex" -> uiText(R.string.ui_pharmacy_admin_screen_076); else -> field
}
internal fun reviewValue(field: String, raw: String, catalog: CatalogSnapshot, limit: Int = 500): String {
    if (field == uiText(R.string.ui_admin_components_007)) return raw
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
    if (element == JsonNull) return if (field == "@entity") uiText(R.string.ui_pharmacy_admin_screen_077) else uiText(R.string.ui_pharmacy_admin_screen_078)
    if (element is JsonObject) return MedicationPresentation.recordIdentity(element, catalog.forms) ?: uiText(R.string.ui_pharmacy_admin_screen_079)
    val value = (element as? JsonPrimitive)?.contentOrNull ?: raw
    return when (field) {
        "status" -> runCatching { statusLabel(AvailabilityStatus.valueOf(value)) }.getOrDefault(value)
        "categoryId" -> catalog.categories.find { it.id == value }?.nameAr ?: uiText(R.string.ui_pharmacy_admin_screen_080)
        "formId" -> catalog.forms.find { it.id == value }?.nameAr ?: uiText(R.string.ui_pharmacy_admin_screen_081)
        "isActive" -> if (value == "true") uiText(R.string.ui_pharmacy_admin_screen_082) else uiText(R.string.ui_pharmacy_admin_screen_083)
        else -> value.take(limit)
    }
}


@Composable private fun TelegramSetup(vm: CatalogViewModel, busy: Boolean, empty: Boolean) {
    var token by remember { mutableStateOf("") }; var group by remember { mutableStateOf("") }
    var title by remember(token, group) { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var offerEmptyPublication by remember { mutableStateOf(false) }
    val challenge = remember { UUID.randomUUID().toString().replace("-", "").take(24) }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    SectionHeading(uiText(R.string.ui_pharmacy_admin_screen_084))
    SupportingText(uiText(R.string.ui_pharmacy_admin_screen_085))
    SectionHeading(uiText(R.string.ui_pharmacy_admin_screen_086))
    SupportingText(uiText(R.string.ui_pharmacy_admin_screen_087))
    SectionHeading(uiText(R.string.ui_pharmacy_admin_screen_088))
    SupportingText(uiText(R.string.ui_pharmacy_admin_screen_089))
    StepHeader(listOf(uiText(R.string.ui_pharmacy_admin_screen_093), uiText(R.string.ui_pharmacy_admin_screen_096), uiText(R.string.ui_pharmacy_admin_screen_098)),
        if (title != null) 2 else if (group.isNotBlank()) 1 else 0)
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_090)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_091), token, { token = it }, secret = true, required = true)
        SelectionContainer { Text(bidi("/aqsetup ${challenge}")) }
        AppButton(variant = AppButtonVariant.SECONDARY, onClick = { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Aqaqeer", "/aqsetup ${challenge}")) }) { Text(uiText(R.string.ui_pharmacy_admin_screen_092)) }
        AppButton(enabled = !busy && token.isNotBlank(), onClick = { vm.command { found = vm.sharing.discover(token, challenge) } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_093)) }
        if (found.isNotEmpty()) RadioChoiceRows(found, found.find { it.first.toString() == group },
            { group = it.first.toString() }, { it.second })
        FormField(uiText(R.string.ui_pharmacy_admin_screen_094), group, { group = groupIdInput(it) }, required = true, validate = FormGuidance::group,
            supportingText = uiText(R.string.ui_pharmacy_admin_screen_095), ltr = true, imeAction = ImeAction.Done)
        AppButton(enabled = !busy && group.toLongOrNull()?.let { it < 0 } == true, onClick = { vm.command { title = vm.sharing.inspectSetup(token, group.toLong()) } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_096)) }
        title?.let { name ->
            Text(uiText(R.string.ui_pharmacy_admin_screen_097, name))
            AppButton(enabled = !busy, onClick = { vm.command { vm.sharing.saveSetup(token, group.toLong(), name); token = ""; if (empty) offerEmptyPublication = true else vm.navigate(Route.Admin) } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_098)) }
        }
    }
    if (offerEmptyPublication) AppDialog(onDismissRequest = { offerEmptyPublication = false; vm.navigate(Route.Admin) },
        title = { Text(uiText(R.string.ui_pharmacy_admin_screen_099)) },
        text = { DialogBody { Text(uiText(R.string.ui_pharmacy_admin_screen_100)) } },
        confirmButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { offerEmptyPublication = false; vm.navigate(Route.Admin) }) { Text(uiText(R.string.ui_pharmacy_admin_screen_101)) } },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { offerEmptyPublication = false; vm.navigate(Route.Review); vm.publish() }) { Text(uiText(R.string.ui_pharmacy_admin_screen_102)) } })
    SupportingText(uiText(R.string.pin_group_guidance))
    ExpandableInformation(uiText(R.string.ui_screen_titles_003), uiText(R.string.ui_pharmacy_admin_screen_103))
}

@Composable private fun PinRecoveryAction(enabled: Boolean, recover: () -> Unit) {
    GroupedCard(title = uiText(R.string.pin_recovery_title)) {
        SupportingText(uiText(R.string.pin_recovery_help))
        AppButton(variant = AppButtonVariant.QUIET, enabled = enabled, onClick = recover) { Text(uiText(R.string.pin_recovery_title)) }
    }
}

@Composable private fun ConnectionExport(vm: CatalogViewModel, shared: Boolean, busy: Boolean) {
    var password by remember { mutableStateOf("") }
    var outgoing by remember { mutableStateOf<CharArray?>(null) }
    var generated by remember { mutableStateOf(false) }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    DisposableEffect(Unit) { onDispose { outgoing?.fill('\u0000') } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.aqaqeer.connection")) { uri ->
        val pass = outgoing; outgoing = null
        if (uri == null) pass?.fill('\u0000') else if (pass != null) vm.secretCommand(null, pass) { vm.sharing.export(uri, pass) }
    }
    Text(uiText(R.string.ui_pharmacy_admin_screen_104))
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_105)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_106), password, { if (it.length <= 1024) { password = it; generated = false } },
            secret = true, required = true, validate = FormGuidance::pin, imeAction = ImeAction.Done)
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
        }) { Text(uiText(R.string.ui_pharmacy_admin_screen_107)) }
    }
}

@Composable private fun MedicationEditor(c: CatalogSnapshot, old: Medication?, vm: CatalogViewModel, busy: Boolean) {
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
    val scientificError = if (genericAr.isBlank() && genericEn.isBlank()) uiText(R.string.ui_pharmacy_admin_screen_108) else null
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
        AppButton(enabled = !busy, onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_pharmacy_admin_screen_109)) }
    }) {
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    InlineOperationStatus(busy, message)
    if (old != null) MedicationAvailabilityControls(c, old, busy, isSaving = { vm.adminBusy.value }) { selection, status ->
        vm.saveAvailability(selection, BulkMedicationChanges(status = status)) { }
    }
    if (old == null) SupportingText(uiText(R.string.ui_pharmacy_admin_screen_110))
    if (duplicates.isNotEmpty()) CheckChoiceRow(duplicateConfirmed,
        uiText(R.string.logic_duplicate_confirm), enabled = !busy) { duplicateConfirmed = it }
    val expiryDay = runCatching { LocalDate.parse(expiry) }.getOrNull()
    if (expiryDay != null && expiryDay < MedicationPresentation.today(Instant.now(), c.pharmacy?.timezone ?: "Asia/Damascus"))
        SupportingText(uiText(R.string.logic_past_expiry))
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_111)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_067), genericAr, { genericAr = it }, submitted = submitted,
            supportingText = uiText(R.string.ui_pharmacy_admin_screen_112), validate = { scientificError })
        FormField(uiText(R.string.ui_pharmacy_admin_screen_068), genericEn, { genericEn = it }, ltr = true, submitted = submitted,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
            supportingText = uiText(R.string.ui_pharmacy_admin_screen_112), validate = { scientificError })
        FormField(uiText(R.string.ui_pharmacy_admin_screen_113), strength, { strength = it }, submitted = submitted, required = true)
        AdaptivePickers(first = {
            ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_114), c.forms, c.forms.find { it.id == form }, { it.nameAr }, itemKey = { it.id }) { form = it.id }
        }, second = {
            ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_073), c.categories, c.categories.find { it.id == category }, { it.nameAr }, itemKey = { it.id }) { category = it.id }
        })
        GregorianDateField(uiText(R.string.ui_pharmacy_admin_screen_071), expiry, { expiry = it }, submitted = submitted)

    }
    if (old == null) NewMedicationAvailabilityControls(c, initialAvailability, busy,
        reset = { initialAvailability = emptyMap() }) { section, status ->
        initialAvailability = if (status == AvailabilityStatus.AVAILABLE) initialAvailability - section
            else initialAvailability + (section to status)
    }
    CollapsibleGroup(uiText(R.string.ui_pharmacy_admin_screen_116), optionalOpen, { optionalOpen = !optionalOpen }) {
    GroupedCard {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_072), threshold, { threshold = MedicationPresentation.westernDigits(it) }, submitted = submitted,
            required = true, validate = FormGuidance::threshold, ltr = true, imeAction = ImeAction.Done, onDone = { if (!busy) save() })
    }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_117)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_064), ar, { ar = it }, submitted = submitted, required = false)
        FormField(uiText(R.string.ui_pharmacy_admin_screen_062), en, { en = it }, ltr = true, submitted = submitted, required = false)
    }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_118)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_069), brand, { brand = it }, submitted = submitted, required = false); FormField(uiText(R.string.ui_pharmacy_admin_screen_070), note, { note = it }, submitted = submitted, required = false, singleLine = false)
    }
    }
    if (c.medications.any { other -> other.id != id &&
        listOfNotNull(other.genericAr, other.genericEn).map(::normalizedName).any { candidate ->
            listOf(genericAr, genericEn).filter(String::isNotBlank).map(::normalizedName).any { it == candidate }
        } }) Text(uiText(R.string.ui_pharmacy_admin_screen_119))
    if (old != null) {
        GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_122)) {
            CheckChoiceRow(confirmDelete, uiText(R.string.ui_pharmacy_admin_screen_123)) { confirmDelete = it }
            DestructiveConfirmButton(enabled = !busy && confirmDelete, onClick = { vm.command(Route.Home) { vm.admin.delete("medication", id) } }, label = uiText(R.string.ui_pharmacy_admin_screen_122))
        }
    }
    }
}

@Composable private fun TaxonomyEditor(c: CatalogSnapshot, vm: CatalogViewModel, busy: Boolean) {
    var type by remember { mutableStateOf("section") }; var selected by remember(type) { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var english by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }; var deleteName by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    fun resetConfirmation() { deleteName = ""; deleteConfirm = false }
    fun resetForm() { selected = null; name = ""; english = ""; active = true; resetConfirmation() }
    ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_124), listOf("section", "category", "form"), type, ::entityLabel, itemKey = { it }) { type = it; resetForm() }
    data class RowValue(val id: String, val ar: String, val en: String?, val protected: Boolean, val active: Boolean = true)
    val rows = when (type) {
        "section" -> c.sections.map { RowValue(it.id, it.nameAr, it.nameEn, false, it.isActive) }
        "category" -> c.categories.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
        else -> c.forms.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
    }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_125)) {
        RadioChoiceRows(rows, rows.find { it.id == selected }, { row ->
            resetConfirmation()
            selected = row.id; name = row.ar; english = row.en ?: ""; active = row.active
        }, { row -> row.ar + when { row.protected -> uiText(R.string.ui_pharmacy_admin_screen_126); !row.active -> uiText(R.string.ui_pharmacy_admin_screen_127); else -> "" } },
            choiceEnabled = { !it.protected })
    }
    AppButton(variant = AppButtonVariant.QUIET, onClick = ::resetForm) { Text(uiText(R.string.ui_pharmacy_admin_screen_128)) }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_129)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_065), name, { name = it }, required = true); FormField(uiText(R.string.ui_pharmacy_admin_screen_130), english, { english = it }, required = false)
        if (type == "section") CheckChoiceRow(active, uiText(R.string.ui_pharmacy_admin_screen_131)) { active = it }
        AppButton(enabled = !busy && name.isNotBlank(), onClick = { vm.command { vm.admin.saveTaxonomy(type, selected, name, english, active); resetForm() } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_132)) }
    }
    selected?.let { id ->
        GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_133)) {
            Text(uiText(R.string.ui_pharmacy_admin_screen_134))
            if (type == "section") FormField(uiText(R.string.ui_pharmacy_admin_screen_135), deleteName, { deleteName = it }, required = true)
            CheckChoiceRow(deleteConfirm, uiText(R.string.ui_pharmacy_admin_screen_136)) { deleteConfirm = it }
            DestructiveConfirmButton(enabled = !busy && deleteConfirm, onClick = { vm.command { vm.admin.delete(type, id, deleteName); resetForm() } }, label = uiText(R.string.ui_pharmacy_admin_screen_137))
        }
    }
}
