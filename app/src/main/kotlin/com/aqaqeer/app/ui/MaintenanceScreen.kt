package com.aqaqeer.app.ui

import androidx.compose.ui.Modifier

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.*
import com.aqaqeer.app.maintenance.*

@Composable internal fun MaintenanceScreen(ready: CatalogState.Ready?, vm: CatalogViewModel) {
    if (ready == null || ready.isDemo || ready.catalog.pharmacy == null) {
        InformationCard(uiText(R.string.ui_maintenance_screen_001), uiText(R.string.ui_maintenance_screen_002))
        return
    }
    val status by vm.maintenance.status.collectAsStateWithLifecycle(initialValue = MaintenanceStatus(loading = true))
    val conflicts by vm.maintenance.conflicts.collectAsStateWithLifecycle()
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val j = status.journal
    if (ready.maintenanceRecoveryRequired || status.unreadable || (j != null && j.pharmacyId != ready.catalog.pharmacy.pharmacyId)) {
        InformationCard(uiText(R.string.ui_maintenance_screen_003), uiText(R.string.ui_maintenance_screen_004))
        return
    }
    if (status.loading) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }; return }
    var choices by remember(j?.id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    fun run(action: suspend () -> Unit) { vm.command(queueOffline = false, action = action) }
    FormPage {
        Text(ready.catalog.pharmacy.name, style = MaterialTheme.typography.titleLarge)
        OperationStatus(busy, message, cancel = vm::cancelAdminOperation)
        if (ready.restorePaused) {
            InformationCard(uiText(R.string.ui_maintenance_screen_005), uiText(R.string.ui_maintenance_screen_006))
            NavigationCard(uiText(R.string.ui_aqaqeer_app_022), { vm.navigate("backup") })
            return@FormPage
        }
        if (j == null) {
            Text(uiText(R.string.ui_maintenance_screen_007))
            if (ready.lifecycle == "SHARED") ReplacementFileFields(vm, busy)
            else Text(uiText(R.string.ui_maintenance_screen_008))
            if (ready.admin && !ready.requiresUpdate) {
                HorizontalDivider()
                PinChangeFields(vm, busy)
                if (ready.lifecycle == "SHARED") {
                    GroupedCard(title = uiText(R.string.ui_maintenance_screen_009)) {
                        Text(uiText(R.string.ui_maintenance_screen_010))
                        if (ready.pendingCount > 0 || ready.uncertain) {
                            Text(uiText(R.string.ui_maintenance_screen_011))
                            TextButton(enabled = !busy, onClick = vm::review) { Text(uiText(R.string.ui_screen_titles_004)) }
                        }
                        Button(enabled = !busy && ready.pendingCount == 0 && !ready.uncertain,
                            onClick = { run { vm.maintenance.begin(MaintenanceKind.ROTATION) } }) { Text(uiText(R.string.ui_maintenance_screen_012)) }
                        TextButton(enabled = !busy && ready.pendingCount == 0 && !ready.uncertain,
                            onClick = { run { vm.maintenance.begin(MaintenanceKind.MIGRATION) } }) { Text(uiText(R.string.ui_maintenance_screen_013)) }
                    }
                }
            }
            return@FormPage
        }
        InformationCard(uiText(R.string.ui_maintenance_screen_014), maintenancePhaseLabel(j.phase))
        MaintenanceIdentity(uiText(R.string.ui_maintenance_screen_015), j.sourceTitle, j.sourceBotId, j.sourceGroupId, j.sourceId, j.sourceTime)
        if (j.sourceUnavailable) Text(uiText(R.string.ui_maintenance_screen_016), color = MaterialTheme.colorScheme.error)
        if (j.destinationGroupId != null) MaintenanceIdentity(uiText(R.string.ui_maintenance_screen_017), j.destinationTitle, j.destinationBotId,
            j.destinationGroupId, j.candidateId, j.candidateTime)
        if (ready.admin && !ready.requiresUpdate) ConflictChoices(conflicts, choices, ready.catalog) { choices = it }
        ContentSection(title = uiText(R.string.ui_maintenance_screen_018)) {
            if (j.kind == MaintenanceKind.REPLACEMENT) {
                when (j.phase) {
                    MaintenancePhase.PREPARING -> ReplacementFileFields(vm, busy)
                    MaintenancePhase.STAGED, MaintenancePhase.VERIFIED -> {
                        Button(enabled = !busy, onClick = { run { vm.maintenance.verifyReplacement() } }) { Text(uiText(R.string.ui_maintenance_screen_019)) }
                        if (j.phase == MaintenancePhase.VERIFIED) {
                            Text(uiText(R.string.ui_maintenance_screen_020))
                            Button(enabled = !busy, onClick = { run { vm.maintenance.activateReplacement() } }) { Text(uiText(R.string.ui_maintenance_screen_021)) }
                        }
                    }
                    MaintenancePhase.ACTIVATING -> Button(enabled = !busy, onClick = { run { vm.maintenance.activateReplacement() } }) { Text(uiText(R.string.ui_maintenance_screen_022)) }
                    MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE -> {
                        if (ready.pendingCount == 0 && !ready.uncertain)
                            Button(enabled = !busy, onClick = { run { vm.maintenance.finishReaderReplacement() } }) { Text(uiText(R.string.ui_maintenance_screen_023)) }
                        if (ready.admin && !ready.requiresUpdate) {
                            Text(uiText(R.string.ui_maintenance_screen_024))
                            Button(enabled = !busy, onClick = { run { vm.maintenance.replacementReview(choices) } }) { Text(uiText(R.string.ui_maintenance_screen_025)) }
                        } else AuthorizationNeeded(vm, busy)
                    }
                    else -> Text(uiText(R.string.ui_maintenance_screen_026))
                }
            } else if (!ready.admin || ready.requiresUpdate) AuthorizationNeeded(vm, busy)
            else {
                if (j.phase in setOf(MaintenancePhase.PREPARING, MaintenancePhase.SOURCE_READY, MaintenancePhase.BACKED_UP, MaintenancePhase.TOKEN_CHANGE)) {
                    var retained by remember(j.id) { mutableStateOf(false) }
                    if (j.kind == MaintenanceKind.MIGRATION) CheckChoiceRow(retained,
                        uiText(R.string.ui_maintenance_screen_027), enabled = !busy) { retained = it }
                    Button(enabled = !busy, onClick = { run { vm.maintenance.prepareSource(retained, choices) } }) { Text(uiText(R.string.ui_maintenance_screen_028)) }
                    if (j.sourceDigest != null) MaintenanceBackupFields(j, vm, busy)
                    if (j.receipt != null) Text(uiText(R.string.ui_maintenance_screen_029))
                }
                if (j.kind == MaintenanceKind.ROTATION) {
                    if (j.phase == MaintenancePhase.BACKED_UP) Button(enabled = !busy, onClick = { run { vm.maintenance.revocationGuide() } }) { Text(uiText(R.string.ui_maintenance_screen_030)) }
                    if (j.phase == MaintenancePhase.TOKEN_CHANGE) {
                        Text(uiText(R.string.ui_maintenance_screen_031))
                        var token by remember(j.id) { mutableStateOf("") }
                        FormField(uiText(R.string.ui_maintenance_screen_032), token, { token = it }, secret = true, required = true)
                        Button(enabled = !busy && token.isNotBlank() && j.receipt != null, onClick = {
                            val submitted = token; token = ""; run { vm.maintenance.rotationToken(submitted) }
                        }) { Text(uiText(R.string.ui_maintenance_screen_033)) }
                    }
                    if (j.phase in setOf(MaintenancePhase.TOKEN_CHANGE, MaintenancePhase.DESTINATION_READY, MaintenancePhase.PREPARED,
                            MaintenancePhase.UPLOAD_STARTED, MaintenancePhase.UPLOADED))
                        TextButton(enabled = !busy, onClick = { run { vm.maintenance.reviewCancellation(choices) } }) { Text(uiText(R.string.ui_maintenance_screen_034)) }
                } else if (j.phase == MaintenancePhase.BACKED_UP) DestinationFields(j, vm, busy)
                if (j.phase in setOf(MaintenancePhase.DESTINATION_READY, MaintenancePhase.PREPARED, MaintenancePhase.UPLOAD_STARTED,
                        MaintenancePhase.UPLOADED, MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING)) {
                    Text(uiText(R.string.ui_maintenance_screen_035))
                    Button(enabled = !busy, onClick = { run { vm.maintenance.publishMaintenance() } }) { Text(uiText(R.string.ui_maintenance_screen_036)) }
                    if (j.phase in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING)) {
                        TextButton(enabled = !busy, onClick = { run { vm.maintenance.reviewDestination(choices) } }) { Text(uiText(R.string.ui_maintenance_screen_037)) }
                        TextButton(enabled = !busy, onClick = { run { vm.maintenance.reviewCancellation(choices) } }) { Text(uiText(R.string.ui_maintenance_screen_038)) }
                    }
                }
                if (j.phase == MaintenancePhase.COMPLETE) {
                    Text(uiText(R.string.ui_maintenance_screen_039))
                    Button(enabled = !busy, onClick = { run { vm.maintenance.complete() } }) { Text(uiText(R.string.ui_maintenance_screen_040)) }
                }
            }
        }
        if (j.phase !in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING, MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE))
            TextButton(enabled = !busy, onClick = { run { vm.maintenance.cancel() } }) { Text(uiText(R.string.ui_maintenance_screen_041)) }
        else Text(uiText(R.string.ui_maintenance_screen_042))
    }
}

@Composable private fun ReplacementFileFields(vm: CatalogViewModel, busy: Boolean) {
    var selected by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected = it }
    GroupedCard(title = uiText(R.string.ui_maintenance_screen_043)) {
        TextButton(enabled = !busy, onClick = {
            try { picker.launch(arrayOf("*/*")) } catch (_: Exception) { vm.adminMessage.value = uiText(R.string.ui_maintenance_screen_044) }
        }) { Text(uiText(R.string.ui_maintenance_screen_045)) }
        FormField(uiText(R.string.ui_maintenance_screen_046), password, { password = it }, secret = true, required = true)
        Button(enabled = !busy && selected != null && password.isNotEmpty(), onClick = {
            val uri = selected ?: return@Button; val secret = password.toCharArray(); password = ""
            vm.secretCommand(null, secret, queueOffline = false) { vm.maintenance.stageReplacement(uri, secret) }
        }) { Text(uiText(R.string.ui_maintenance_screen_047)) }
    }
}

@Composable private fun PinChangeFields(vm: CatalogViewModel, busy: Boolean) {
    var current by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    GroupedCard(title = uiText(R.string.ui_maintenance_screen_048)) {
        FormField(uiText(R.string.ui_maintenance_screen_049), current, { current = it }, secret = true, required = true, numericSecret = true, validate = FormGuidance::pin)
        FormField(uiText(R.string.ui_maintenance_screen_050), next, { next = it }, secret = true, required = true, numericSecret = true, validate = FormGuidance::pin)
        FormField(uiText(R.string.ui_maintenance_screen_051), confirmation, { confirmation = it }, secret = true, required = true, numericSecret = true, validate = { if (it.map { c -> Character.digit(c, 10) } == next.map { c -> Character.digit(c, 10) }) null else uiText(R.string.ui_pharmacy_admin_screen_013) })
        Button(enabled = !busy && current.isNotEmpty() && next.isNotEmpty() && confirmation.isNotEmpty(), onClick = {
            val a = current.toCharArray(); val b = next.toCharArray(); val c = confirmation.toCharArray()
            current = ""; next = ""; confirmation = ""
            vm.secretCommand(null, a, b, c) { vm.pinChanges.change(a, b, c) }
        }) { Text(uiText(R.string.ui_maintenance_screen_052)) }
        Text(uiText(R.string.ui_maintenance_screen_053))
    }
}

@Composable private fun AuthorizationNeeded(vm: CatalogViewModel, busy: Boolean) {
    Text(uiText(R.string.ui_maintenance_screen_054))
    NavigationCard(uiText(R.string.ui_maintenance_screen_055), { vm.navigate("promote") }, enabled = !busy)
}

@Composable internal fun MaintenanceIdentity(label: String, title: String?, bot: Long?, group: Long?, publication: String?, time: String?) {
    GroupedCard(title = label) {
        title?.let { Text(it) }
        LabeledValue(uiText(R.string.ui_maintenance_screen_056), bot?.let { bidi(it.toString()) } ?: uiText(R.string.ui_maintenance_screen_057))
        group?.let { LabeledValue(uiText(R.string.ui_maintenance_screen_058), bidi(it.toString())) }
        publication?.let { LabeledValue(uiText(R.string.ui_maintenance_screen_059), bidi(it)) }
        time?.let { LabeledValue(uiText(R.string.ui_maintenance_screen_060), bidi(it)) }
    }
}

@Composable private fun MaintenanceBackupFields(j: MaintenanceJournal, vm: CatalogViewModel, busy: Boolean) {
    var password by remember(j.id) { mutableStateOf("") }; var confirmation by remember(j.id) { mutableStateOf("") }
    var flow by remember { mutableStateOf<String?>(null) }
    val secrets = remember { BackupExportSecrets() }
    DisposableEffect(j.id) { onDispose { secrets.clear() } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = secrets.password; val c = secrets.confirmation; val expected = flow
        secrets.password = null; secrets.confirmation = null; flow = null
        if (uri == null || p == null || c == null || expected == null) { p?.fill('\u0000'); c?.fill('\u0000') }
        else vm.secretCommand(null, p, c, queueOffline = false) { vm.maintenance.backup(uri, p, c, expected) }
    }
    GroupedCard(title = uiText(R.string.ui_maintenance_screen_061)) {
        BackupPassphraseFields(password, confirmation, { password = it }, { confirmation = it })
        Button(enabled = !busy && password.codePointCount(0, password.length) >= 8 && password == confirmation, onClick = {
            secrets.clear(); secrets.password = password.toCharArray(); secrets.confirmation = confirmation.toCharArray(); flow = j.id
            password = ""; confirmation = ""
            try { save.launch("aqaqeer-maintenance-backup.aqbak") }
            catch (_: Exception) { secrets.clear(); flow = null; vm.adminMessage.value = uiText(R.string.ui_reports_screen_002) }
        }) { Text(uiText(R.string.ui_maintenance_screen_062)) }
        Text(uiText(R.string.ui_maintenance_screen_063))
    }
}

@Composable private fun DestinationFields(j: MaintenanceJournal, vm: CatalogViewModel, busy: Boolean) {
    var token by remember(j.id) { mutableStateOf("") }; var group by remember(j.id) { mutableStateOf("") }
    var title by remember(token, group) { mutableStateOf<String?>(null) }
    GroupedCard(title = uiText(R.string.ui_maintenance_screen_064)) {
        FormField(uiText(R.string.ui_maintenance_screen_065), token, { token = it }, secret = true, required = true)
        FormField(uiText(R.string.ui_maintenance_screen_066), group, { group = groupIdInput(it) }, required = true, validate = FormGuidance::group, ltr = true,
            supportingText = uiText(R.string.ui_maintenance_screen_067))
        Text(uiText(R.string.ui_maintenance_screen_068))
        TextButton(enabled = !busy && token.isNotBlank() && group.toLongOrNull()?.let { it < 0 } == true, onClick = {
            val t = token; val g = group.toLong()
            vm.command(queueOffline = false) { title = vm.maintenance.inspectDestination(t, g) }
        }) { Text(uiText(R.string.ui_maintenance_screen_069)) }
        title?.let { name ->
            LabeledValue(uiText(R.string.ui_maintenance_screen_070), name + "\n" + bidi(group))
            Button(enabled = !busy, onClick = {
                val t = token; val g = group.toLong(); token = ""; title = null
                vm.command(queueOffline = false) { vm.maintenance.confirmDestination(t, g, name) }
            }) { Text(uiText(R.string.ui_maintenance_screen_071)) }
        }
    }
}

private fun maintenancePhaseLabel(phase: MaintenancePhase): String = when (phase) {
    MaintenancePhase.PREPARING -> uiText(R.string.ui_maintenance_screen_072)
    MaintenancePhase.STAGED -> uiText(R.string.ui_maintenance_screen_073)
    MaintenancePhase.VERIFIED -> uiText(R.string.ui_maintenance_screen_074)
    MaintenancePhase.SOURCE_READY -> uiText(R.string.ui_maintenance_screen_075)
    MaintenancePhase.BACKED_UP -> uiText(R.string.ui_maintenance_screen_076)
    MaintenancePhase.TOKEN_CHANGE -> uiText(R.string.ui_maintenance_screen_077)
    MaintenancePhase.DESTINATION_READY -> uiText(R.string.ui_maintenance_screen_078)
    MaintenancePhase.PREPARED -> uiText(R.string.ui_maintenance_screen_079)
    MaintenancePhase.UPLOAD_STARTED -> uiText(R.string.ui_maintenance_screen_080)
    MaintenancePhase.UPLOADED -> uiText(R.string.ui_maintenance_screen_081)
    MaintenancePhase.WRITE_STARTED -> uiText(R.string.ui_maintenance_screen_082)
    MaintenancePhase.ACTIVATING -> uiText(R.string.ui_maintenance_screen_083)
    MaintenancePhase.REVIEW -> uiText(R.string.ui_maintenance_screen_084)
    MaintenancePhase.COMPLETE -> uiText(R.string.ui_maintenance_screen_085)
    MaintenancePhase.CANCELLING -> uiText(R.string.ui_maintenance_screen_086)
}
