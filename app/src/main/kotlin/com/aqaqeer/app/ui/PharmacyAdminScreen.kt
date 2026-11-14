package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
    if (state.route.startsWith("edit-med:")) {
        if (ready.isDemo || !ready.admin || ready.requiresUpdate || ready.maintenancePaused) {
            FormPage { InformationCard(uiText(R.string.ui_pharmacy_admin_screen_001), uiText(R.string.ui_pharmacy_admin_screen_002)) }
            return
        }
        val targetId = state.route.removePrefix("edit-med:")
        val medication = catalog.medications.find { it.id == targetId }
        if (targetId != "new" && medication == null) { FormPage { Text(uiText(R.string.ui_pharmacy_admin_screen_003)) }; return }
        key(catalog.pharmacy?.pharmacyId, targetId) { MedicationEditor(catalog, medication, vm, busy) }
        return
    }
    FormPage {
        val formFocus = LocalFormFocus.current
        val formScope = rememberCoroutineScope()
        fun revealInvalid() { formSubmitted = true; formScope.launch { formFocus?.focusFirstInvalid() } }
        OperationStatus(busy || sharing.busy, message, sharing.message)
        when {
            state.route == "create" -> {
                if (catalog.pharmacy != null || ready.isDemo) { Text(uiText(R.string.ui_pharmacy_admin_screen_004)); return@FormPage }
                var name by rememberSaveable(state.route) { mutableStateOf("") }; var zone by rememberSaveable(state.route) { mutableStateOf("Asia/Damascus") }
                var editor by rememberSaveable(state.route) { mutableStateOf("") }; var section by rememberSaveable(state.route) { mutableStateOf(uiText(R.string.ui_pharmacy_admin_screen_005)) }
                var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
                var pinEntryVersion by remember { mutableStateOf(0) }
                val focus = LocalFocusManager.current
                val creating = busy || vm.adminBusy.value
                GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_006)) {
                    FormField(uiText(R.string.ui_pharmacy_admin_screen_007), name, { name = it }, submitted = formSubmitted, required = true); TimezoneField(zone) { zone = it }
                    FormField(uiText(R.string.ui_pharmacy_admin_screen_008), editor, { editor = it }, submitted = formSubmitted, required = true); FormField(uiText(R.string.ui_pharmacy_admin_screen_009), section, { section = it }, submitted = formSubmitted, required = true)
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
                Button(enabled = !creating, onClick = {
                    if (vm.adminBusy.value) return@Button
                    formSubmitted = true
                    if (name.isBlank() || editor.isBlank() || section.isBlank() || FormGuidance.timezone(zone) != null ||
                        FormGuidance.pin(pin) != null || FormGuidance.pin(confirm) != null ||
                        pin.map { Character.digit(it, 10) } != confirm.map { Character.digit(it, 10) }) {
                        revealInvalid(); return@Button
                    }
                    val a = pin.toCharArray(); val b = confirm.toCharArray()
                    formSubmitted = false; pinEntryVersion++
                    focus.clearFocus()
                    pin = ""; confirm = ""
                    vm.secretCommand("home", a, b) { vm.admin.create(name, zone, editor, section, a, b) }
                }) { Text(uiText(R.string.ui_pharmacy_admin_screen_015)) }
            }
            state.route == "promote" -> {
                var name by rememberSaveable(state.route) { mutableStateOf("") }; var pin by remember { mutableStateOf("") }
                GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_016)) {
                    FormField(uiText(R.string.ui_pharmacy_admin_screen_008), name, { name = it }, submitted = formSubmitted, required = true); FormField(uiText(R.string.ui_pharmacy_admin_screen_017), pin, { pin = it }, secret = true, submitted = formSubmitted, required = true, numericSecret = true, imeAction = ImeAction.Done, validate = FormGuidance::pin)
                }
                Text(uiText(R.string.ui_pharmacy_admin_screen_018))
                Button(enabled = !busy, onClick = {
                    formSubmitted = true
                    if (name.isBlank() || FormGuidance.pin(pin) != null) { revealInvalid(); return@Button }
                    val p = pin.toCharArray(); pin = ""; vm.secretCommand("home", p) { vm.admin.promote(p, name) }
                }) { Text(uiText(R.string.ui_screen_titles_006)) }
            }
            state.route == "leave" -> {
                Text(uiText(R.string.ui_screen_titles_007), style = MaterialTheme.typography.headlineSmall)
                GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_019)) {
                    Text(uiText(R.string.ui_pharmacy_admin_screen_020))
                    if (ready.admin) FormField(uiText(R.string.ui_pharmacy_admin_screen_021), leaveName, { leaveName = it }, submitted = formSubmitted, required = true,
                        validate = { if (it == catalog.pharmacy?.name) null else uiText(R.string.ui_pharmacy_admin_screen_022) })
                    CheckChoiceRow(confirmedLeave, uiText(R.string.ui_pharmacy_admin_screen_023)) { confirmedLeave = it }
                    Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), enabled = confirmedLeave && !busy, onClick = {
                        formSubmitted = true
                        if (ready.admin && leaveName != catalog.pharmacy?.name) { revealInvalid(); return@Button }
                        vm.command("home") { vm.admin.leave(leaveName, true) }
                    }) { Text(uiText(R.string.ui_pharmacy_admin_screen_024)) }
                    if (ready.uncertain) Text(uiText(R.string.ui_pharmacy_admin_screen_025))
                }
            }
            !ready.admin || ready.isDemo -> Text(uiText(R.string.ui_pharmacy_admin_screen_026))
            ready.requiresUpdate && state.route !in setOf("review", "admin") -> Text(uiText(R.string.ui_pharmacy_admin_screen_027))
            ready.maintenancePaused && state.route !in setOf("admin", "review") -> Text(uiText(R.string.ui_pharmacy_admin_screen_028))
            state.route == "setup" -> TelegramSetup(vm, busy, catalog.medications.isEmpty())
            state.route == "export-connection" -> ConnectionExport(vm, ready.lifecycle == "SHARED", busy)
            state.route == "review" -> {
                if (ready.maintenancePaused) NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_029), onClick = { vm.navigate("maintenance") })
                if (ready.restorePaused) NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_030), onClick = { vm.navigate("backup") })
                Text(uiText(R.string.ui_pharmacy_admin_screen_031, MedicationPresentation.easternDigits(ready.pendingCount.toString())), style = MaterialTheme.typography.titleLarge)
                Text(if (ready.maintenancePaused) uiText(R.string.ui_pharmacy_admin_screen_032) else if (ready.restorePaused) uiText(R.string.ui_pharmacy_admin_screen_033) else uiText(R.string.ui_pharmacy_admin_screen_034))
                pending.groupBy { it.entityType to it.targetId.substringBefore('/') }.forEach { (_, operations) ->
                    val first = operations.first()
                    val name = catalog.medications.find { it.id == first.targetId.substringBefore('/') }
                        ?.let { MedicationPresentation.identity(it, catalog.formsById[it.formId]?.nameAr) } ?: entityLabel(first.entityType)
                    GroupedCard(title = name) {
                        operations.forEach { op ->
                            Text(fieldLabel(op.field, op.entityType), style = MaterialTheme.typography.titleSmall)
                            LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_035), reviewValue(op.field, op.baseValueJson, catalog))
                            LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_036), reviewValue(op.field, op.intendedValueJson, catalog))
                        }
                    }
                }
                var choices by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
                ConflictChoices(sharing.conflicts, choices, catalog) { choices = it }
                Button(enabled = (ready.pendingCount > 0 || ready.uncertain || ready.lifecycle == "LOCAL_DRAFT") && !busy && !sharing.busy && !ready.requiresUpdate && !ready.restorePaused && !ready.maintenancePaused, onClick = { vm.publish(choices) }) { Text(uiText(R.string.ui_pharmacy_admin_screen_037)) }
            }
            state.route == "taxonomy" -> TaxonomyEditor(catalog, vm, busy)
            else -> {
                NavigationCard(uiText(R.string.ui_screen_titles_011), onClick = { vm.navigate("backup") })
                if (ready.lifecycle == "LOCAL_DRAFT") InformationCard(uiText(R.string.ui_pharmacy_admin_screen_038), uiText(R.string.ui_pharmacy_admin_screen_039))
                NavigationGroup(uiText(R.string.ui_pharmacy_admin_screen_040)) {
                    NavigationRow(uiText(R.string.ui_screen_titles_015), { vm.openHistory() })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_pharmacy_admin_screen_041), { vm.navigate("maintenance") })
                }
                if (ready.maintenancePaused) {
                    Text(uiText(R.string.ui_pharmacy_admin_screen_042))
                    NavigationCard(uiText(R.string.ui_screen_titles_007), onClick = { vm.navigate("leave") }, destructive = true)
                    return@FormPage
                }
                if (ready.requiresUpdate) {
                    Text(uiText(R.string.ui_pharmacy_admin_screen_043))
                    NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_044), vm::review)
                    return@FormPage
                }
                NavigationGroup(uiText(R.string.ui_pharmacy_admin_screen_045)) {
                    NavigationRow(uiText(R.string.ui_screen_titles_009), { vm.navigate("edit-med:new") })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_screen_titles_005), { vm.navigate("taxonomy") })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_pharmacy_admin_screen_046), { vm.navigate("csv-import") })
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_pharmacy_admin_screen_047), { vm.navigate("reports") })
                }
                NavigationGroup(uiText(R.string.ui_pharmacy_admin_screen_048)) {
                    if (ready.lifecycle == "LOCAL_DRAFT") NavigationRow(uiText(R.string.ui_screen_titles_003), { vm.navigate("setup") })
                    if (ready.lifecycle == "LOCAL_DRAFT") HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_screen_titles_004), vm::review)
                    HorizontalDivider()
                    NavigationRow(uiText(R.string.ui_screen_titles_008), { vm.navigate("export-connection") }, enabled = ready.lifecycle == "SHARED" && !ready.restorePaused)
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
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_090)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_091), token, { token = it }, secret = true, required = true)
        SelectionContainer { Text(bidi("/aqsetup ${challenge}")) }
        OutlinedButton(onClick = { clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Aqaqeer", "/aqsetup ${challenge}")) }) { Text(uiText(R.string.ui_pharmacy_admin_screen_092)) }
        Button(enabled = !busy && token.isNotBlank(), onClick = { vm.command { found = vm.sharing.discover(token, challenge) } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_093)) }
        if (found.isNotEmpty()) RadioChoiceRows(found, found.find { it.first.toString() == group },
            { group = it.first.toString() }, { it.second })
        FormField(uiText(R.string.ui_pharmacy_admin_screen_094), group, { group = groupIdInput(it) }, required = true, validate = FormGuidance::group,
            supportingText = uiText(R.string.ui_pharmacy_admin_screen_095), ltr = true, imeAction = ImeAction.Done)
        Button(enabled = !busy && group.toLongOrNull()?.let { it < 0 } == true, onClick = { vm.command { title = vm.sharing.inspectSetup(token, group.toLong()) } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_096)) }
        title?.let { name ->
            Text(uiText(R.string.ui_pharmacy_admin_screen_097, name))
            Button(enabled = !busy, onClick = { vm.command { vm.sharing.saveSetup(token, group.toLong(), name); token = ""; if (empty) offerEmptyPublication = true else vm.navigate("admin") } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_098)) }
        }
    }
    if (offerEmptyPublication) AlertDialog(onDismissRequest = { offerEmptyPublication = false; vm.navigate("admin") },
        title = { Text(uiText(R.string.ui_pharmacy_admin_screen_099)) },
        text = { DialogBody { Text(uiText(R.string.ui_pharmacy_admin_screen_100)) } },
        confirmButton = { TextButton(onClick = { offerEmptyPublication = false; vm.navigate("admin") }) { Text(uiText(R.string.ui_pharmacy_admin_screen_101)) } },
        dismissButton = { TextButton(onClick = { offerEmptyPublication = false; vm.navigate("review"); vm.publish() }) { Text(uiText(R.string.ui_pharmacy_admin_screen_102)) } })
    ExpandableInformation(uiText(R.string.ui_screen_titles_003), uiText(R.string.ui_pharmacy_admin_screen_103))
}

@Composable private fun ConnectionExport(vm: CatalogViewModel, shared: Boolean, busy: Boolean) {
    var password by remember { mutableStateOf("") }
    var outgoing by remember { mutableStateOf<CharArray?>(null) }
    DisposableEffect(Unit) { onDispose { outgoing?.fill('\u0000') } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.aqaqeer.connection")) { uri ->
        val pass = outgoing; outgoing = null
        if (uri == null) pass?.fill('\u0000') else if (pass != null) vm.secretCommand(null, pass) { vm.sharing.export(uri, pass) }
    }
    Text(uiText(R.string.ui_pharmacy_admin_screen_104))
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_105)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_106), password, { password = it }, secret = true, required = true, imeAction = ImeAction.Done)
        Button(enabled = shared && !busy && outgoing == null, onClick = { outgoing = password.toCharArray(); password = ""; launcher.launch("pharmacy.aqz") }) { Text(uiText(R.string.ui_pharmacy_admin_screen_107)) }
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
    var confirmDelete by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(false) }
    var submitted by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(false) }
    val formFocus = remember { FormFocusController() }
    val scope = rememberCoroutineScope()
    var optionalOpen by rememberSaveable(id) { mutableStateOf(false) }
    val scientificError = if (genericAr.isBlank() && genericEn.isBlank()) uiText(R.string.ui_pharmacy_admin_screen_108) else null
    fun save() {
        submitted = true
        if (scientificError != null || strength.isBlank() || FormGuidance.date(expiry) != null || FormGuidance.threshold(threshold) != null) {
            scope.launch { formFocus.focusFirstInvalid() }; return
        }
        fun optional(s: String) = s.trim().takeIf { it.isNotEmpty() }
        vm.command {
            vm.admin.saveMedication(Medication(id = id, nameEn = optional(en), strength = strength.trim(), formId = form,
                categoryId = category, createdAt = Instant.now(), updatedAt = Instant.now(), updatedBy = EditorRef(id, ""),
                nameAr = optional(ar), genericAr = optional(genericAr), genericEn = optional(genericEn), brand = optional(brand),
                note = optional(note), expiryDate = optional(expiry)?.let(LocalDate::parse), expirySoonThresholdDays = threshold.toInt()), expectedPharmacy = c.pharmacy?.pharmacyId, requireExisting = old != null)
            vm.newMedicationName = ""
            vm.medicationSaved(id, old == null)
        }
    }
    FormPage(focusController = formFocus, footer = {
        Button(enabled = !busy, onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.ui_pharmacy_admin_screen_109)) }
    }) {
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    InlineOperationStatus(busy, message)
    if (old == null) SupportingText(uiText(R.string.ui_pharmacy_admin_screen_110))
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_111)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_067), genericAr, { genericAr = it }, submitted = submitted,
            supportingText = uiText(R.string.ui_pharmacy_admin_screen_112), validate = { scientificError })
        FormField(uiText(R.string.ui_pharmacy_admin_screen_068), genericEn, { genericEn = it }, ltr = true, submitted = submitted,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
            supportingText = uiText(R.string.ui_pharmacy_admin_screen_112), validate = { scientificError })
        FormField(uiText(R.string.ui_pharmacy_admin_screen_113), strength, { strength = it }, submitted = submitted, required = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            Box(Modifier.weight(1f)) {
                ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_114), c.forms, c.forms.find { it.id == form }, { it.nameAr }) { form = it.id }
            }
            Box(Modifier.weight(1f)) {
                ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_073), c.categories, c.categories.find { it.id == category }, { it.nameAr }) { category = it.id }
            }
        }
        GregorianDateField(uiText(R.string.ui_pharmacy_admin_screen_071), expiry, { expiry = it }, submitted = submitted)
        FormField(uiText(R.string.ui_pharmacy_admin_screen_072), threshold, { threshold = MedicationPresentation.westernDigits(it) }, submitted = submitted,
            required = true, validate = FormGuidance::threshold, ltr = true, imeAction = ImeAction.Done, onDone = { if (!busy) save() })
    }
    TextButton(onClick = { optionalOpen = !optionalOpen }) { Text(if (optionalOpen) uiText(R.string.ui_pharmacy_admin_screen_115) else uiText(R.string.ui_pharmacy_admin_screen_116)) }
    Column(Modifier.animateContentSize(tween(180))) {
    if (optionalOpen) {
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_117)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_064), ar, { ar = it }, submitted = submitted, required = false)
        FormField(uiText(R.string.ui_pharmacy_admin_screen_062), en, { en = it }, ltr = true, submitted = submitted, required = false)
    }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_118)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_069), brand, { brand = it }, submitted = submitted, required = false); FormField(uiText(R.string.ui_pharmacy_admin_screen_070), note, { note = it }, submitted = submitted, required = false, singleLine = false)
    }
    }
    }
    if (c.medications.any { other -> other.id != id &&
        listOfNotNull(other.genericAr, other.genericEn).map(::normalizedName).any { candidate ->
            listOf(genericAr, genericEn).filter(String::isNotBlank).map(::normalizedName).any { it == candidate }
        } }) Text(uiText(R.string.ui_pharmacy_admin_screen_119))
    if (old != null) {
        NavigationCard(uiText(R.string.ui_pharmacy_admin_screen_120), onClick = { vm.navigate("med:${id}") },
            supporting = uiText(R.string.ui_pharmacy_admin_screen_121))
        GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_122)) {
            CheckChoiceRow(confirmDelete, uiText(R.string.ui_pharmacy_admin_screen_123)) { confirmDelete = it }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), enabled = !busy && confirmDelete, onClick = { vm.command("home") { vm.admin.delete("medication", id) } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_122)) }
        }
    }
    }
}

@Composable private fun TaxonomyEditor(c: CatalogSnapshot, vm: CatalogViewModel, busy: Boolean) {
    var type by remember { mutableStateOf("section") }; var selected by remember(type) { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var english by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }; var deleteName by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    ChoicePicker(uiText(R.string.ui_pharmacy_admin_screen_124), listOf("section", "category", "form"), type, ::entityLabel) { type = it; selected = null; name = ""; english = "" }
    data class RowValue(val id: String, val ar: String, val en: String?, val protected: Boolean, val active: Boolean = true)
    val rows = when (type) {
        "section" -> c.sections.map { RowValue(it.id, it.nameAr, it.nameEn, false, it.isActive) }
        "category" -> c.categories.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
        else -> c.forms.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
    }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_125)) {
        RadioChoiceRows(rows, rows.find { it.id == selected }, { row ->
            selected = row.id; name = row.ar; english = row.en ?: ""; active = row.active
        }, { row -> row.ar + when { row.protected -> uiText(R.string.ui_pharmacy_admin_screen_126); !row.active -> uiText(R.string.ui_pharmacy_admin_screen_127); else -> "" } },
            choiceEnabled = { !it.protected })
    }
    TextButton(onClick = { selected = null; name = ""; english = ""; active = true }) { Text(uiText(R.string.ui_pharmacy_admin_screen_128)) }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_129)) {
        FormField(uiText(R.string.ui_pharmacy_admin_screen_065), name, { name = it }, required = true); FormField(uiText(R.string.ui_pharmacy_admin_screen_130), english, { english = it }, required = false)
        if (type == "section") CheckChoiceRow(active, uiText(R.string.ui_pharmacy_admin_screen_131)) { active = it }
        Button(enabled = !busy && name.isNotBlank(), onClick = { vm.command { vm.admin.saveTaxonomy(type, selected, name, english, active); selected = null; name = ""; english = "" } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_132)) }
    }
    selected?.let { id ->
        GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_133)) {
            Text(uiText(R.string.ui_pharmacy_admin_screen_134))
            if (type == "section") FormField(uiText(R.string.ui_pharmacy_admin_screen_135), deleteName, { deleteName = it }, required = true)
            CheckChoiceRow(deleteConfirm, uiText(R.string.ui_pharmacy_admin_screen_136)) { deleteConfirm = it }
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), enabled = !busy && deleteConfirm, onClick = { vm.command { vm.admin.delete(type, id, deleteName); selected = null; name = ""; english = ""; deleteConfirm = false } }) { Text(uiText(R.string.ui_pharmacy_admin_screen_137)) }
        }
    }
}
