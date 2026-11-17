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
    val uiText = rememberUiText()
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
    FormPage(Modifier.testTag("backup_screen"), showFieldConvention = true) {
        OperationStatus(busy || sharing.busy, message,
            if (exportSucceeded) uiText(R.string.backup_export_success_message) else if (restoreSession) sharing.message else null,
            vm::cancelAdminOperation)
        if (ready.restorePaused) {
            StepHeader(listOf(uiText(R.string.review_select_file), uiText(R.string.backup_restore_passphrase_label), uiText(R.string.backup_restore_paused_title)), 2)
            InformationCard(uiText(R.string.backup_restore_paused_title), uiText(R.string.backup_restore_admin_reconciliation_help))
            if (!ready.admin) {
                NavigationCard(uiText(R.string.backup_authorize_admin_action), { vm.navigate(Route.Promote) })
                return@FormPage
            }
            RestoreReview(ready, vm, busy || sharing.busy)
            Spacer(Modifier.height(UiSpacing.group))
        }
        if (ready.admin) {
            GroupedCard(title = uiText(R.string.backup_export_heading)) {
                Text(uiText(R.string.backup_export_contents_help))
                BackupPassphraseFields(password, confirmation, { password = it }, { confirmation = it })
                AppButton(enabled = !busy && password.codePointCount(0, password.length) >= 8 && password == confirmation, onClick = {
                    exportSucceeded = false
                    secrets.clear(); secrets.password = password.toCharArray(); secrets.confirmation = confirmation.toCharArray(); password = ""; confirmation = ""
                    val time = ZonedDateTime.now(ZoneId.of(ready.catalog.pharmacy?.timezone ?: "Asia/Damascus"))
                    try { export.launch("aqaqeer-backup-${time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))}.aqbak") }
                    catch (_: Exception) { secrets.clear(); vm.adminMessage.value = uiText(R.string.backup_file_picker_failed_message) }
                }) { Text(uiText(R.string.backup_choose_export_destination_action)) }
                SupportingText(uiText(R.string.backup_file_size_limit_help))
            }
            Spacer(Modifier.height(UiSpacing.group))
        }
        if (ready.maintenancePaused) {
            InformationCard(uiText(R.string.backup_restore_maintenance_blocked_title), uiText(R.string.backup_restore_maintenance_blocked_help))
            NavigationCard(uiText(R.string.backup_review_maintenance_action), { vm.navigate(Route.Maintenance) })
            return@FormPage
        }
        if (ready.catalog.pharmacy == null || ready.admin) {
            GroupedCard(title = uiText(R.string.backup_restore_welcome_action)) {
                StepHeader(listOf(uiText(R.string.review_select_file), uiText(R.string.backup_restore_passphrase_label), uiText(R.string.backup_restore_paused_title)), if (selected != null) 1 else 0)
                Text(uiText(R.string.backup_restore_validation_help))
                AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }) { Text(if (selected == null) uiText(R.string.backup_choose_restore_file_action) else uiText(R.string.backup_change_restore_file_action)) }
                if (selected != null) {
                    SecretField(uiText(R.string.backup_restore_passphrase_label), restorePassword, { restorePassword = it }, required = true)
                    if (ready.catalog.pharmacy != null) {
                        Text(uiText(R.string.backup_replace_local_data_warning_format, MedicationPresentation.displayDigits(ready.pendingCount.toString())))
                        FormField(uiText(R.string.backup_current_pharmacy_confirmation_label), typedName, { typedName = it }, required = true)
                        CheckChoiceRow(confirmed, uiText(R.string.backup_replace_local_data_acknowledgement_label)) { confirmed = it }
                    }
                    DestructiveConfirmButton(enabled = !busy && restorePassword.isNotEmpty() &&
                        (ready.catalog.pharmacy == null || confirmed && typedName == ready.catalog.pharmacy.name), onClick = {
                        val uri = selected ?: return@DestructiveConfirmButton; val p = restorePassword.toCharArray(); restorePassword = ""
                        vm.secretCommand(Route.Home, p) { vm.backups.restore(uri, p, typedName, confirmed); selected = null }
                    }, label = uiText(R.string.backup_validate_restore_action))
                }
            }
        } else Text(uiText(R.string.backup_restore_admin_only_message))
    }
}

@Composable private fun RestoreReview(ready: CatalogState.Ready, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    val sharing by vm.sharing.status.collectAsStateWithLifecycle()
    var replace by remember { mutableStateOf(false) }; var typedName by remember { mutableStateOf("") }
    var choices by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    if (ready.restoreHasConnection) RestoreConnectionCheck(ready.restoreConnectionConfirmed, vm, busy)
    ConflictChoices(sharing.conflicts, choices, ready.catalog) { choices = it }
    if (ready.lifecycle == "LOCAL_DRAFT") {
        AppButton(enabled = !busy && (!ready.restoreHasConnection || ready.restoreConnectionConfirmed),
            onClick = { vm.command(queueOffline = false) { vm.sharing.reconcileRestore(false, "", choices) } }) { Text(uiText(R.string.backup_continue_local_draft_action)) }
        return
    }
    GroupedCard(title = uiText(R.string.history_restore_reconciled_label)) {
        CheckChoiceRow(replace, uiText(R.string.backup_replace_published_contents_label)) { replace = it; choices = emptyMap() }
        if (replace) FormField(uiText(R.string.backup_restored_pharmacy_confirmation_label), typedName, { typedName = it }, required = true)
        AppButton(enabled = !busy && ready.restoreConnectionConfirmed && (!replace || typedName == ready.catalog.pharmacy?.name), onClick = {
            vm.command(queueOffline = false) { vm.sharing.reconcileRestore(replace, typedName, choices) }
        }) { Text(uiText(R.string.backup_inspect_reconciliation_action)) }
    }
}

@Composable private fun RestoreConnectionCheck(confirmed: Boolean, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    var token by remember { mutableStateOf("") }; var title by remember(token) { mutableStateOf<String?>(null) }
    GroupedCard(title = uiText(R.string.backup_confirm_restored_connection_title)) {
        SecretField(uiText(R.string.backup_replacement_bot_token_label), token, { token = it }, required = false, rule = uiText(R.string.backup_replacement_bot_token_rule), tag = uiText(R.string.backup_replacement_bot_token_label_with_guidance))
        Text(uiText(R.string.backup_replacement_bot_token_help))
        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { vm.command { title = vm.sharing.inspectRestoredConnection(token) } }) { Text(uiText(R.string.backup_verify_connection_group_action)) }
        title?.let { name ->
            LabeledValue(uiText(R.string.backup_confirm_group_title), name)
            AppButton(enabled = !busy, onClick = { vm.command { vm.sharing.confirmRestoredConnection(token, name); token = ""; title = null } }) { Text(uiText(R.string.backup_confirm_correct_group_action)) }
        }
        Text(if (confirmed) uiText(R.string.backup_group_confirmed_message) else uiText(R.string.backup_connection_unconfirmed_message))
    }
}
