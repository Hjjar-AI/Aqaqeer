package com.aqaqeer.app.ui

import androidx.compose.ui.Modifier

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.data.*
import com.aqaqeer.app.maintenance.*

@Composable internal fun MaintenanceScreen(ready: CatalogState.Ready?, vm: CatalogViewModel) {
    val uiText = rememberUiText()
    if (ready?.isDemo == true) { DemoConnectionScreen(ready, vm); return }
    if (ready == null || ready.catalog.pharmacy == null) {
        InformationCard(uiText(R.string.maintenance_title), uiText(R.string.maintenance_real_pharmacy_required_message))
        return
    }
    val status by vm.maintenance.status.collectAsStateWithLifecycle(initialValue = MaintenanceStatus(loading = true))
    val conflicts by vm.maintenance.conflicts.collectAsStateWithLifecycle()
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    val j = status.journal
    if (ready.maintenanceRecoveryRequired || status.unreadable || (j != null && j.pharmacyId != ready.catalog.pharmacy.pharmacyId)) {
        InformationCard(uiText(R.string.maintenance_protected_state_unreadable_title), uiText(R.string.maintenance_protected_state_retained_help))
        return
    }
    if (status.loading) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { AppSpinner() }; return }
    var choices by remember(j?.id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    fun run(action: suspend () -> Unit) { vm.command(queueOffline = false, action = action) }
    FormPage(showFieldConvention = true) {
        Text(ready.catalog.pharmacy.name, style = MaterialTheme.typography.titleLarge)
        OperationStatus(busy, message, cancel = vm::cancelAdminOperation)
        if (ready.admin && (j == null || j.kind != MaintenanceKind.REPLACEMENT))
            InformationCard(uiText(R.string.maintenance_sharing_heading), uiText(R.string.maintenance_coordination))
        if (ready.restorePaused) {
            InformationCard(uiText(R.string.maintenance_restore_review_required_title), uiText(R.string.maintenance_restore_review_required_help))
            NavigationCard(uiText(R.string.backup_review_restore_action), { vm.navigate(Route.Backup) })
            return@FormPage
        }
        if (j == null) {
            var openTask by rememberSaveable(ready.catalog.pharmacy.pharmacyId) { mutableStateOf<String?>(null) }
            fun toggle(task: String) { openTask = if (openTask == task) null else task }
            if (ready.lifecycle == "SHARED") CollapsibleGroup(uiText(R.string.maintenance_replace_local_connection_title), openTask == "replacement", { toggle("replacement") }) {
                ReplacementFileFields(vm, busy)
            }
            if (ready.admin && !ready.requiresUpdate) {
                CollapsibleGroup(uiText(R.string.maintenance_change_admin_pin_title), openTask == "pin", { toggle("pin") }) { PinChangeFields(vm, busy) }
                if (ready.lifecycle == "SHARED") listOf(MaintenanceKind.ROTATION, MaintenanceKind.MIGRATION).forEach { kind ->
                    val key = kind.name
                    CollapsibleGroup(uiText(if (kind == MaintenanceKind.ROTATION) R.string.maintenance_start_token_key_rotation_action else R.string.maintenance_start_relocation_action),
                        openTask == key, { toggle(key) }) {
                        NoticeStrip(Notice(uiText(R.string.review_sensitive), NoticeSeverity.WARNING))
                        SupportingText(uiText(R.string.maintenance_fresh_backup_required_help))
                        if (ready.pendingCount > 0 || ready.uncertain) {
                            SupportingText(uiText(R.string.maintenance_reconcile_pending_first_message))
                            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = vm::review) { Text(uiText(R.string.navigation_publication_review_title)) }
                        }
                        AppButton(enabled = !busy && ready.pendingCount == 0 && !ready.uncertain,
                            onClick = { run { vm.maintenance.begin(kind) } }) {
                            Text(uiText(if (kind == MaintenanceKind.ROTATION) R.string.maintenance_start_token_key_rotation_action else R.string.maintenance_start_relocation_action))
                        }
                    }
                }
            }
            return@FormPage
        }
        val phases = if (j.kind == MaintenanceKind.REPLACEMENT) listOf(MaintenancePhase.PREPARING, MaintenancePhase.STAGED,
            MaintenancePhase.VERIFIED, MaintenancePhase.ACTIVATING, MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE)
        else listOf(MaintenancePhase.PREPARING, MaintenancePhase.SOURCE_READY, MaintenancePhase.BACKED_UP) +
            (if (j.kind == MaintenanceKind.ROTATION) listOf(MaintenancePhase.TOKEN_CHANGE) else emptyList()) +
            listOf(MaintenancePhase.DESTINATION_READY, MaintenancePhase.PREPARED, MaintenancePhase.UPLOAD_STARTED,
                MaintenancePhase.UPLOADED, MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING, MaintenancePhase.COMPLETE)
        if (j.phase in phases) StepHeader(phases.map(::maintenancePhaseLabel), phases.indexOf(j.phase))
        else NoticeStrip(Notice(maintenancePhaseLabel(j.phase, text = uiText), NoticeSeverity.WARNING))
        MaintenanceIdentity(uiText(R.string.maintenance_source_heading), j.sourceTitle, j.sourceBotId, j.sourceGroupId, j.sourceId, j.sourceTime)
        if (j.sourceUnavailable) Text(uiText(R.string.maintenance_source_unverified_warning), color = MaterialTheme.colorScheme.error)
        if (j.destinationGroupId != null) MaintenanceIdentity(uiText(R.string.maintenance_destination_heading), j.destinationTitle, j.destinationBotId,
            j.destinationGroupId, j.candidateId, j.candidateTime)
        if (ready.admin && !ready.requiresUpdate) ConflictChoices(conflicts, choices, ready.catalog) { choices = it }
        ContentSection(title = uiText(R.string.maintenance_current_step_label)) {
            if (j.kind == MaintenanceKind.REPLACEMENT) {
                when (j.phase) {
                    MaintenancePhase.PREPARING -> ReplacementFileFields(vm, busy)
                    MaintenancePhase.STAGED, MaintenancePhase.VERIFIED -> {
                        AppButton(enabled = !busy, onClick = { run { vm.maintenance.verifyReplacement() } }) { Text(uiText(R.string.maintenance_verify_replacement_action)) }
                        if (j.phase == MaintenancePhase.VERIFIED) {
                            Text(uiText(R.string.maintenance_replacement_activation_help))
                            AppButton(enabled = !busy, onClick = { run { vm.maintenance.activateReplacement() } }) { Text(uiText(R.string.maintenance_activate_reader_connection_action)) }
                        }
                    }
                    MaintenancePhase.ACTIVATING -> AppButton(enabled = !busy, onClick = { run { vm.maintenance.activateReplacement() } }) { Text(uiText(R.string.maintenance_resume_activation_action)) }
                    MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE -> {
                        if (ready.pendingCount == 0 && !ready.uncertain)
                            AppButton(enabled = !busy, onClick = { run { vm.maintenance.finishReaderReplacement() } }) { Text(uiText(R.string.maintenance_finish_reader_replacement_action)) }
                        if (ready.admin && !ready.requiresUpdate) {
                            Text(uiText(R.string.maintenance_retained_work_review_help))
                            AppButton(enabled = !busy, onClick = { run { vm.maintenance.replacementReview(choices) } }) { Text(uiText(R.string.maintenance_reconcile_without_publishing_action)) }
                        } else AuthorizationNeeded(vm, busy)
                    }
                    else -> Text(uiText(R.string.maintenance_retained_connection_help))
                }
            } else if (!ready.admin || ready.requiresUpdate) AuthorizationNeeded(vm, busy)
            else {
                if (j.phase in setOf(MaintenancePhase.PREPARING, MaintenancePhase.SOURCE_READY, MaintenancePhase.BACKED_UP, MaintenancePhase.TOKEN_CHANGE)) {
                    var retained by remember(j.id) { mutableStateOf(false) }
                    if (j.kind == MaintenanceKind.MIGRATION) CheckChoiceRow(retained,
                        uiText(R.string.maintenance_use_retained_recovery_label), enabled = !busy) { retained = it }
                    AppButton(enabled = !busy, onClick = { run { vm.maintenance.prepareSource(retained, choices) } }) { Text(uiText(R.string.maintenance_inspect_source_action)) }
                    if (j.sourceDigest != null) MaintenanceBackupFields(j, vm, busy)
                    if (j.receipt != null) Text(uiText(R.string.maintenance_backup_completed_message))
                }
                if (j.kind == MaintenanceKind.ROTATION) {
                    if (j.phase == MaintenancePhase.BACKED_UP) AppButton(enabled = !busy, onClick = { run { vm.maintenance.revocationGuide() } }) { Text(uiText(R.string.maintenance_continue_to_botfather_action)) }
                    if (j.phase == MaintenancePhase.TOKEN_CHANGE) {
                        Text(uiText(R.string.maintenance_token_revocation_help))
                        var token by remember(j.id) { mutableStateOf("") }
                        SecretField(uiText(R.string.maintenance_replacement_bot_token_label), token, { token = it }, required = true)
                        AppButton(enabled = !busy && token.isNotBlank() && j.receipt != null, onClick = {
                            val submitted = token; token = ""; run { vm.maintenance.rotationToken(submitted) }
                        }) { Text(uiText(R.string.maintenance_prepare_rotated_key_action)) }
                    }
                    if (j.phase in setOf(MaintenancePhase.TOKEN_CHANGE, MaintenancePhase.DESTINATION_READY, MaintenancePhase.PREPARED,
                            MaintenancePhase.UPLOAD_STARTED, MaintenancePhase.UPLOADED))
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { run { vm.maintenance.reviewCancellation(choices) } }) { Text(uiText(R.string.maintenance_review_keep_key_action)) }
                } else if (j.phase == MaintenancePhase.BACKED_UP) DestinationFields(j, vm, busy)
                if (j.phase in setOf(MaintenancePhase.DESTINATION_READY, MaintenancePhase.PREPARED, MaintenancePhase.UPLOAD_STARTED,
                        MaintenancePhase.UPLOADED, MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING)) {
                    Text(uiText(R.string.maintenance_retry_verification_help))
                    AppButton(enabled = !busy, onClick = { run { vm.maintenance.publishMaintenance() } }) { Text(uiText(R.string.maintenance_verify_publish_continue_action)) }
                    if (j.phase in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING)) {
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { run { vm.maintenance.reviewDestination(choices) } }) { Text(uiText(R.string.maintenance_review_destination_snapshot_action)) }
                        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { run { vm.maintenance.reviewCancellation(choices) } }) { Text(uiText(R.string.maintenance_review_cancellation_action)) }
                    }
                }
                if (j.phase == MaintenancePhase.COMPLETE) {
                    Text(uiText(R.string.maintenance_activation_completed_help))
                    AppButton(enabled = !busy, onClick = { run { vm.maintenance.complete() } }) { Text(uiText(R.string.maintenance_confirm_completion_action)) }
                }
            }
        }
        if (j.phase !in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING, MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE))
            AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = { run { vm.maintenance.cancel() } }) { Text(uiText(R.string.maintenance_cancel_safely_action)) }
        else Text(uiText(R.string.maintenance_possible_remote_changes_warning))
    }
}

@Composable private fun ReplacementFileFields(vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    var selected by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected = it }
    GroupedCard(title = uiText(R.string.maintenance_replace_local_connection_title)) {
        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = {
            try { picker.launch(arrayOf("*/*")) } catch (_: Exception) { vm.adminMessage.value = uiText(R.string.maintenance_file_picker_failed_message) }
        }) { Text(uiText(R.string.maintenance_choose_replacement_file_action)) }
        SecretField(uiText(R.string.maintenance_replacement_file_password_label), password, { password = it }, required = true)
        AppButton(enabled = !busy && selected != null && password.isNotEmpty(), onClick = {
            val uri = selected ?: return@AppButton; val secret = password.toCharArray(); password = ""
            vm.secretCommand(null, secret, queueOffline = false) { vm.maintenance.stageReplacement(uri, secret) }
        }) { Text(uiText(R.string.maintenance_stage_replacement_file_action)) }
    }
}

@Composable private fun PinChangeFields(vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    var current by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var confirmation by remember { mutableStateOf("") }
    GroupedCard(title = uiText(R.string.maintenance_change_admin_pin_title)) {
        SecretField(uiText(R.string.maintenance_current_admin_pin_label), current, { current = it }, required = true, numericSecret = true, validate = FormGuidance::pin)
        SecretField(uiText(R.string.maintenance_new_admin_pin_label), next, { next = it }, required = true, numericSecret = true, validate = FormGuidance::pin, rule = uiText(R.string.maintenance_new_admin_pin_rule), tag = uiText(R.string.maintenance_new_admin_pin_label_with_guidance))
        SecretField(uiText(R.string.maintenance_confirm_new_admin_pin_label), confirmation, { confirmation = it }, required = true, numericSecret = true, validate = { if (it.map { c -> Character.digit(c, 10) } == next.map { c -> Character.digit(c, 10) }) null else uiText(R.string.catalog_admin_pin_mismatch_error) })
        SupportingText(uiText(R.string.maintenance_admin_pin_propagation_help))
        AppButton(enabled = !busy && current.isNotEmpty() && next.isNotEmpty() && confirmation.isNotEmpty(), onClick = {
            val a = current.toCharArray(); val b = next.toCharArray(); val c = confirmation.toCharArray()
            current = ""; next = ""; confirmation = ""
            vm.secretCommand(null, a, b, c) { vm.pinChanges.change(a, b, c) }
        }) { Text(uiText(R.string.maintenance_save_admin_pin_change_action)) }
    }
}

@Composable private fun AuthorizationNeeded(vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    Text(uiText(R.string.maintenance_admin_authorization_required_help))
    NavigationCard(uiText(R.string.maintenance_authorize_admin_action), { vm.navigate(Route.Promote) }, enabled = !busy)
}

@Composable internal fun MaintenanceIdentity(label: String, title: String?, bot: Long?, group: Long?, publication: String?, time: String?) {
    val uiText = rememberUiText()
    var expanded by rememberSaveable(label, publication) { mutableStateOf(false) }
    CollapsibleGroup(label, expanded, { expanded = !expanded }) {
        title?.let { Text(it) }
        bot?.let { CopyableValue(uiText(R.string.maintenance_bot_label), it.toString()) }
        group?.let { CopyableValue(uiText(R.string.maintenance_group_label), it.toString()) }
        publication?.let { CopyableValue(uiText(R.string.maintenance_publication_label), it) }
        time?.let { LabeledValue(uiText(R.string.maintenance_publication_time_utc_label), bidi(it)) }
    }
}

@Composable private fun MaintenanceBackupFields(j: MaintenanceJournal, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
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
    GroupedCard(title = uiText(R.string.maintenance_fresh_backup_title)) {
        BackupPassphraseFields(password, confirmation, { password = it }, { confirmation = it })
        SupportingText(uiText(R.string.maintenance_backup_limits_help))
        AppButton(enabled = !busy && password.codePointCount(0, password.length) >= 8 && password == confirmation, onClick = {
            secrets.clear(); secrets.password = password.toCharArray(); secrets.confirmation = confirmation.toCharArray(); flow = j.id
            password = ""; confirmation = ""
            try { save.launch("aqaqeer-maintenance-backup.aqbak") }
            catch (_: Exception) { secrets.clear(); flow = null; vm.adminMessage.value = uiText(R.string.exchange_report_save_picker_failed_message) }
        }) { Text(uiText(R.string.maintenance_export_verified_backup_action)) }
    }
}

@Composable private fun DestinationFields(j: MaintenanceJournal, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    var token by remember(j.id) { mutableStateOf("") }; var group by remember(j.id) { mutableStateOf("") }
    var title by remember(token, group) { mutableStateOf<String?>(null) }
    GroupedCard(title = uiText(R.string.maintenance_destination_connection_title)) {
        SecretField(uiText(R.string.maintenance_destination_bot_token_label), token, { token = it }, required = true)
        FormField(uiText(R.string.maintenance_destination_group_id_label), group, { group = groupIdInput(it) }, required = true, validate = FormGuidance::group, ltr = true, supportingText = uiText(R.string.maintenance_destination_group_id_help))
        Text(uiText(R.string.maintenance_empty_destination_help))
        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy && token.isNotBlank() && group.toLongOrNull()?.let { it < 0 } == true, onClick = {
            val t = token; val g = group.toLong()
            vm.command(queueOffline = false) { title = vm.maintenance.inspectDestination(t, g) }
        }) { Text(uiText(R.string.maintenance_verify_destination_action)) }
        title?.let { name ->
            LabeledValue(uiText(R.string.maintenance_confirm_destination_title), name + "\n" + bidi(group))
            AppButton(enabled = !busy, onClick = {
                val t = token; val g = group.toLong(); token = ""; title = null
                vm.command(queueOffline = false) { vm.maintenance.confirmDestination(t, g, name) }
            }) { Text(uiText(R.string.maintenance_relocation_confirmation_label)) }
        }
    }
}

private fun maintenancePhaseLabel(phase: MaintenancePhase, text: UiText? = null): String = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    when (phase) {
    MaintenancePhase.PREPARING -> uiText(R.string.maintenance_prepare_keep_current_connection_action)
    MaintenancePhase.STAGED -> uiText(R.string.maintenance_phase_replacement_pending_message)
    MaintenancePhase.VERIFIED -> uiText(R.string.maintenance_phase_replacement_verified_message)
    MaintenancePhase.SOURCE_READY -> uiText(R.string.maintenance_phase_source_reviewed_message)
    MaintenancePhase.BACKED_UP -> uiText(R.string.maintenance_phase_backup_saved_message)
    MaintenancePhase.TOKEN_CHANGE -> uiText(R.string.maintenance_phase_token_revocation_message)
    MaintenancePhase.DESTINATION_READY -> uiText(R.string.maintenance_phase_destination_ready_message)
    MaintenancePhase.PREPARED -> uiText(R.string.maintenance_phase_publication_prepared_message)
    MaintenancePhase.UPLOAD_STARTED -> uiText(R.string.maintenance_phase_upload_started_message)
    MaintenancePhase.UPLOADED -> uiText(R.string.maintenance_phase_uploaded_message)
    MaintenancePhase.WRITE_STARTED -> uiText(R.string.maintenance_phase_write_started_message)
    MaintenancePhase.ACTIVATING -> uiText(R.string.maintenance_phase_activating_message)
    MaintenancePhase.REVIEW -> uiText(R.string.maintenance_phase_review_message)
    MaintenancePhase.COMPLETE -> uiText(R.string.maintenance_phase_complete_message)
    MaintenancePhase.CANCELLING -> uiText(R.string.maintenance_phase_cancelling_message)
}
}
