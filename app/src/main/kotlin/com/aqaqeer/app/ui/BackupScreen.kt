package com.aqaqeer.app.ui

import com.aqaqeer.app.data.MedicationPresentation

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.CatalogState
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun BackupScreen(state: AppUiState, vm: CatalogViewModel) {
    val ready = state.catalog as? CatalogState.Ready ?: return
    if (ready.isDemo) return
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val sharing by vm.sharing.status.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }; var selected by remember { mutableStateOf<Uri?>(null) }
    var typedName by remember { mutableStateOf("") }; var confirmed by remember { mutableStateOf(false) }
    val restoreSession = remember { ready.restorePaused }
    var exportSucceeded by remember { mutableStateOf(false) }
    val secrets = remember { BackupExportSecrets() }
    DisposableEffect(Unit) { onDispose { secrets.clear() } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val p = secrets.password; val c = secrets.confirmation; secrets.password = null; secrets.confirmation = null
        if (uri == null || p == null || c == null) { p?.fill('\u0000'); c?.fill('\u0000') }
        else vm.secretCommand(null, p, c) { vm.backups.export(uri, p, c); exportSucceeded = true }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected = it }
    FormPage(Modifier.testTag("backup_screen")) {
        OperationStatus(busy || sharing.busy, message,
            if (exportSucceeded) uiText(R.string.ui_backup_screen_001) else if (restoreSession) sharing.message else null,
            vm::cancelAdminOperation)
        if (ready.restorePaused) {
            InformationCard(uiText(R.string.ui_backup_screen_002), uiText(R.string.ui_backup_screen_003))
            if (!ready.admin) {
                NavigationCard(uiText(R.string.ui_backup_screen_004), { vm.navigate("promote") })
                return@FormPage
            }
            RestoreReview(ready, vm, busy || sharing.busy)
            HorizontalDivider()
        }
        if (ready.admin) {
            GroupedCard(title = uiText(R.string.ui_backup_screen_005)) {
                Text(uiText(R.string.ui_backup_screen_006))
                BackupPassphraseFields(password, confirmation, { password = it }, { confirmation = it })
                Button(enabled = !busy && password.codePointCount(0, password.length) >= 8 && password == confirmation, onClick = {
                    exportSucceeded = false
                    secrets.clear(); secrets.password = password.toCharArray(); secrets.confirmation = confirmation.toCharArray(); password = ""; confirmation = ""
                    val time = ZonedDateTime.now(ZoneId.of(ready.catalog.pharmacy?.timezone ?: "Asia/Damascus"))
                    try { export.launch("aqaqeer-backup-${time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.aqbak") }
                    catch (_: Exception) { secrets.clear(); vm.adminMessage.value = uiText(R.string.ui_backup_screen_007) }
                }) { Text(uiText(R.string.ui_backup_screen_008)) }
                Text(uiText(R.string.ui_backup_screen_009))
            }
            HorizontalDivider()
        }
        if (ready.maintenancePaused) {
            InformationCard(uiText(R.string.ui_backup_screen_010), uiText(R.string.ui_backup_screen_011))
            NavigationCard(uiText(R.string.ui_backup_screen_012), { vm.navigate("maintenance") })
            return@FormPage
        }
        if (ready.catalog.pharmacy == null || ready.admin) {
            GroupedCard(title = uiText(R.string.ui_aqaqeer_app_082)) {
                Text(uiText(R.string.ui_backup_screen_013))
                TextButton(enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }) { Text(if (selected == null) uiText(R.string.ui_backup_screen_014) else uiText(R.string.ui_backup_screen_015)) }
                if (selected != null) {
                    FormField(uiText(R.string.ui_backup_screen_016), restorePassword, { restorePassword = it }, secret = true, required = true)
                    if (ready.catalog.pharmacy != null) {
                        Text(uiText(R.string.ui_backup_screen_017, MedicationPresentation.easternDigits(ready.pendingCount.toString())))
                        FormField(uiText(R.string.ui_backup_screen_018), typedName, { typedName = it }, required = true)
                        CheckChoiceRow(confirmed, uiText(R.string.ui_backup_screen_019)) { confirmed = it }
                    }
                    Button(enabled = !busy && restorePassword.isNotEmpty() &&
                        (ready.catalog.pharmacy == null || confirmed && typedName == ready.catalog.pharmacy.name), onClick = {
                        val uri = selected ?: return@Button; val p = restorePassword.toCharArray(); restorePassword = ""
                        vm.secretCommand("home", p) { vm.backups.restore(uri, p, typedName, confirmed); selected = null }
                    }) { Text(uiText(R.string.ui_backup_screen_020)) }
                }
            }
        } else Text(uiText(R.string.ui_backup_screen_021))
    }
}

@Composable private fun RestoreReview(ready: CatalogState.Ready, vm: CatalogViewModel, busy: Boolean) {
    val sharing by vm.sharing.status.collectAsStateWithLifecycle()
    var replace by remember { mutableStateOf(false) }; var typedName by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    if (ready.restoreHasConnection) RestoreConnectionCheck(ready.restoreConnectionConfirmed, vm, busy)
    ConflictChoices(sharing.conflicts, choices, ready.catalog) { choices = it }
    if (ready.lifecycle == "LOCAL_DRAFT") {
        Button(enabled = !busy && (!ready.restoreHasConnection || ready.restoreConnectionConfirmed),
            onClick = { vm.command(queueOffline = false) { vm.sharing.reconcileRestore(false, "", choices) } }) { Text(uiText(R.string.ui_backup_screen_022)) }
        return
    }
    GroupedCard(title = uiText(R.string.ui_pharmacy_admin_screen_059)) {
        CheckChoiceRow(replace, uiText(R.string.ui_backup_screen_023)) { replace = it; choices = emptyMap() }
        if (replace) FormField(uiText(R.string.ui_backup_screen_024), typedName, { typedName = it }, required = true)
        Button(enabled = !busy && ready.restoreConnectionConfirmed && (!replace || typedName == ready.catalog.pharmacy?.name), onClick = {
            vm.command(queueOffline = false) { vm.sharing.reconcileRestore(replace, typedName, choices) }
        }) { Text(uiText(R.string.ui_backup_screen_025)) }
    }
}

@Composable private fun RestoreConnectionCheck(confirmed: Boolean, vm: CatalogViewModel, busy: Boolean) {
    var token by remember { mutableStateOf("") }; var title by remember(token) { mutableStateOf<String?>(null) }
    GroupedCard(title = uiText(R.string.ui_backup_screen_026)) {
        FormField(uiText(R.string.ui_backup_screen_027), token, { token = it }, secret = true, required = false)
        Text(uiText(R.string.ui_backup_screen_028))
        TextButton(enabled = !busy, onClick = { vm.command { title = vm.sharing.inspectRestoredConnection(token) } }) { Text(uiText(R.string.ui_backup_screen_029)) }
        title?.let { name ->
            LabeledValue(uiText(R.string.ui_backup_screen_030), name)
            Button(enabled = !busy, onClick = { vm.command { vm.sharing.confirmRestoredConnection(token, name); token = ""; title = null } }) { Text(uiText(R.string.ui_backup_screen_031)) }
        }
        Text(if (confirmed) uiText(R.string.ui_backup_screen_032) else uiText(R.string.ui_backup_screen_033))
    }
}
