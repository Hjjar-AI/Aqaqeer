package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.data.MedicationPresentation
import com.aqaqeer.app.BuildConfig

@Composable internal fun ConnectionImportScreen(state: ConnectionImportUiState, catalog: CatalogState,
    select: (android.net.Uri) -> Unit, stage: (CharArray) -> Unit, cancel: () -> Unit,
    discard: (String) -> Unit, retryStorage: () -> Unit, verify: (String) -> Unit,
    activate: (String) -> Unit) {
    val uiText = rememberUiText()
    val focus = LocalFocusManager.current
    var password by remember(state.selectedUri) { mutableStateOf("") }
    var confirmDiscard by remember(state.pending?.id) { mutableStateOf(false) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { password = ""; pickerError = null; select(uri) }
    }
    val ready = catalog as? CatalogState.Ready
    val eligible = ready != null && !ready.isDemo && ready.catalog.pharmacy == null
    LazyColumn(Modifier.fillMaxSize().testTag("connection_import"),
        contentPadding = PaddingValues(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        if (eligible) item { StepHeader(listOf(uiText(R.string.review_select_file), uiText(R.string.review_check_connection), uiText(R.string.review_apply_file)),
            if (state.verification != null) 2 else if (state.pending != null) 1 else 0) }
        if (catalog is CatalogState.Loading || state.loading) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { AppSpinner() } }
        } else if (!eligible) {
            item { InformationCard(uiText(R.string.connection_import_unavailable_title), when {
                ready?.isDemo == true -> uiText(R.string.connection_import_demo_unavailable_help)
                ready != null -> uiText(R.string.connection_import_configured_pharmacy_help)
                else -> uiText(R.string.connection_import_protected_storage_help)
            }) }
        } else {
            item { InformationCard(uiText(R.string.connection_import_pending_title), uiText(R.string.connection_import_pending_activation_help)) }
            state.error?.let { message -> item { OperationStatus(false, message) } }
            pickerError?.let { message -> item { OperationStatus(false, message) } }
            if (!state.storageReady) {
                item { AppButton(onClick = retryStorage, enabled = !state.busy) { Text(uiText(R.string.connection_import_retry_storage_action)) } }
            } else {
                val pending = state.pending
                if (pending != null) {
                    item { InformationCard(uiText(R.string.connection_import_pending_saved_title), pending.pharmacyName) }
                    val verification = state.verification?.takeIf { it.pendingId == pending.id }
                    val requiresUpdate = state.requirements?.requiresUpdate(BuildConfig.VERSION_CODE) == true ||
                        verification?.requiresUpdate == true
                    if (requiresUpdate) item {
                        InformationCard(uiText(R.string.home_update_required_title), uiText(R.string.connection_import_update_required_help))
                    }
                    if (verification != null) {
                        item { InformationCard(if (requiresUpdate) uiText(R.string.connection_import_verified_update_required_title) else uiText(R.string.connection_import_verified_title),
                            verification.pharmacyName ?: uiText(R.string.connection_import_verified_identity_help)) }
                        item { Text(uiText(R.string.connection_import_verified_time_utc_prefix) + bidi(MedicationPresentation.timestamp(verification.checkedAt, "UTC"))) }
                        item { Text(uiText(R.string.connection_import_publication_time_utc_prefix) + bidi(MedicationPresentation.timestamp(verification.publication.publishedAt, "UTC"))) }
                        verification.medicationCount?.let { count -> item {
                            Text(uiText(R.string.connection_import_medication_count_prefix) + MedicationPresentation.displayDigits(count.toString()))
                        } }
                    }
                    item { Text(uiText(R.string.connection_import_not_activated_help)) }
                    item { AppButton(onClick = { verify(pending.id) }, enabled = !state.busy,
                        modifier = Modifier.testTag("verify_pending_connection")) {
                        Text(if (verification == null) uiText(R.string.connection_import_verify_action) else uiText(R.string.connection_import_reverify_action))
                    } }
                    item { Text(uiText(R.string.connection_import_activation_help)) }
                    item { AppButton(onClick = { activate(pending.id) }, enabled = !state.busy && !requiresUpdate,
                        modifier = Modifier.testTag("activate_pending_connection")) {
                        Text(uiText(R.string.connection_import_activate_reader_action))
                    } }
                    item { AppButton(variant = AppButtonVariant.DESTRUCTIVE_TEXT, onClick = { confirmDiscard = true }, enabled = !state.busy) { Text(uiText(R.string.connection_import_discard_pending_action)) } }
                } else if (!state.busy) {
                    item {
                        AppButton(onClick = {
                            focus.clearFocus(); pickerError = null
                            try { picker.launch(arrayOf("*/*")) }
                            catch (_: android.content.ActivityNotFoundException) { pickerError = uiText(R.string.connection_import_file_picker_missing_message) }
                            catch (_: SecurityException) { pickerError = uiText(R.string.connection_import_file_picker_failed_message) }
                        }, modifier = Modifier.testTag("choose_connection")) {
                            Text(if (state.selectedUri == null) uiText(R.string.connection_import_choose_connection_file_action) else uiText(R.string.connection_import_choose_another_file_action))
                        }
                    }
                    if (state.selectedUri != null) {
                        item {
                            GroupedCard(title = uiText(R.string.connection_import_open_file_title)) {
                                Text(uiText(R.string.connection_import_file_selected_help))
                                SecretField(uiText(R.string.connection_import_file_password_label), password, { if (it.length <= 1024) password = it }, required = true, tag = "connection_password", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), validate = { if (it.length >= 4 && it.all { c -> Character.digit(c, 10) in 0..9 }) null else uiText(R.string.connection_import_password_min_digits_error) })
                                AppButton(onClick = {
                                    val owned = password.toCharArray()
                                    password = ""; focus.clearFocus(); stage(owned)
                                }, enabled = password.isNotEmpty(), modifier = Modifier.testTag("stage_connection")) {
                                    Text(uiText(R.string.connection_import_decrypt_stage_action))
                                }
                                SupportingText(uiText(R.string.connection_import_file_password_help))
                            }
                        }
                    }
                }
                if (state.busy) {
                    item {
                        OperationStatus(true, null,
                            when { state.activating -> uiText(R.string.connection_import_activation_progress); state.verifying -> uiText(R.string.connection_import_verification_progress); else -> uiText(R.string.connection_import_operation_progress) }, cancel)
                    }
                }
            }
        }
    }
    if (confirmDiscard && state.pending != null) {
        val pending = state.pending
        AppDialog(onDismissRequest = { if (!state.busy) confirmDiscard = false },
            title = { Text(uiText(R.string.connection_import_discard_confirmation_title)) },
            body = { Text(uiText(R.string.connection_import_discard_confirmation_help_format, pending.pharmacyName)) },
            confirmButton = { AppButton(variant = AppButtonVariant.DESTRUCTIVE_TEXT, onClick = { confirmDiscard = false; discard(pending.id) }, enabled = !state.busy) { Text(uiText(R.string.connection_import_confirm_discard_action)) } },
            dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { confirmDiscard = false }, enabled = !state.busy) { Text(uiText(R.string.connection_import_keep_pending_action)) } })
    }
}
