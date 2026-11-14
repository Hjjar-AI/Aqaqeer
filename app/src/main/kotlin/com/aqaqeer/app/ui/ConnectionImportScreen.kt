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
        if (catalog is CatalogState.Loading || state.loading) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() } }
        } else if (!eligible) {
            item { InformationCard(uiText(R.string.ui_connection_import_screen_001), when {
                ready?.isDemo == true -> uiText(R.string.ui_connection_import_screen_002)
                ready != null -> uiText(R.string.ui_connection_import_screen_003)
                else -> uiText(R.string.ui_connection_import_screen_004)
            }) }
        } else {
            item { InformationCard(uiText(R.string.ui_connection_import_screen_005), uiText(R.string.ui_connection_import_screen_006)) }
            state.error?.let { message -> item { OperationStatus(false, message) } }
            pickerError?.let { message -> item { OperationStatus(false, message) } }
            if (!state.storageReady) {
                item { Button(onClick = retryStorage, enabled = !state.busy) { Text(uiText(R.string.ui_connection_import_screen_007)) } }
            } else {
                val pending = state.pending
                if (pending != null) {
                    item { InformationCard(uiText(R.string.ui_connection_import_screen_008), pending.pharmacyName) }
                    val verification = state.verification?.takeIf { it.pendingId == pending.id }
                    val requiresUpdate = state.requirements?.requiresUpdate(BuildConfig.VERSION_CODE) == true ||
                        verification?.requiresUpdate == true
                    if (requiresUpdate) item {
                        InformationCard(uiText(R.string.ui_home_screen_045), uiText(R.string.ui_connection_import_screen_009))
                    }
                    if (verification != null) {
                        item { InformationCard(if (requiresUpdate) uiText(R.string.ui_connection_import_screen_010) else uiText(R.string.ui_connection_import_screen_011),
                            verification.pharmacyName ?: uiText(R.string.ui_connection_import_screen_012)) }
                        item { Text(uiText(R.string.ui_connection_import_screen_013) + bidi(MedicationPresentation.timestamp(verification.checkedAt, "UTC"))) }
                        item { Text(uiText(R.string.ui_connection_import_screen_014) + bidi(MedicationPresentation.timestamp(verification.publication.publishedAt, "UTC"))) }
                        verification.medicationCount?.let { count -> item {
                            Text(uiText(R.string.ui_connection_import_screen_015) + MedicationPresentation.easternDigits(count.toString()))
                        } }
                    }
                    item { Text(uiText(R.string.ui_connection_import_screen_016)) }
                    item { Button(onClick = { verify(pending.id) }, enabled = !state.busy,
                        modifier = Modifier.testTag("verify_pending_connection")) {
                        Text(if (verification == null) uiText(R.string.ui_connection_import_screen_017) else uiText(R.string.ui_connection_import_screen_018))
                    } }
                    item { Text(uiText(R.string.ui_connection_import_screen_019)) }
                    item { Button(onClick = { activate(pending.id) }, enabled = !state.busy && !requiresUpdate,
                        modifier = Modifier.testTag("activate_pending_connection")) {
                        Text(uiText(R.string.ui_connection_import_screen_020))
                    } }
                    item { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = { confirmDiscard = true }, enabled = !state.busy) { Text(uiText(R.string.ui_connection_import_screen_021)) } }
                } else if (!state.busy) {
                    item {
                        Button(onClick = {
                            focus.clearFocus(); pickerError = null
                            try { picker.launch(arrayOf("*/*")) }
                            catch (_: android.content.ActivityNotFoundException) { pickerError = uiText(R.string.ui_connection_import_screen_022) }
                            catch (_: SecurityException) { pickerError = uiText(R.string.ui_connection_import_screen_023) }
                        }, modifier = Modifier.testTag("choose_connection")) {
                            Text(if (state.selectedUri == null) uiText(R.string.ui_connection_import_screen_024) else uiText(R.string.ui_connection_import_screen_025))
                        }
                    }
                    if (state.selectedUri != null) {
                        item {
                            GroupedCard(title = uiText(R.string.ui_connection_import_screen_026)) {
                                Text(uiText(R.string.ui_connection_import_screen_027))
                                FormField(uiText(R.string.ui_connection_import_screen_028), password, { if (it.length <= 1024) password = it }, secret = true,
                                    required = true, tag = "connection_password",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    validate = { if (it.length >= 6 && it.all { c -> Character.digit(c, 10) in 0..9 }) null else uiText(R.string.ui_connection_import_screen_029) })
                                Button(onClick = {
                                    val owned = password.toCharArray()
                                    password = ""; focus.clearFocus(); stage(owned)
                                }, enabled = password.isNotEmpty(), modifier = Modifier.testTag("stage_connection")) {
                                    Text(uiText(R.string.ui_connection_import_screen_030))
                                }
                                SupportingText(uiText(R.string.ui_connection_import_screen_031))
                            }
                        }
                    }
                }
                if (state.busy) {
                    item {
                        OperationStatus(true, null,
                            when { state.activating -> uiText(R.string.ui_connection_import_screen_032); state.verifying -> uiText(R.string.ui_connection_import_screen_033); else -> uiText(R.string.ui_connection_import_screen_034) }, cancel)
                    }
                }
            }
        }
    }
    if (confirmDiscard && state.pending != null) {
        val pending = state.pending
        AlertDialog(onDismissRequest = { if (!state.busy) confirmDiscard = false },
            title = { Text(uiText(R.string.ui_connection_import_screen_035)) },
            text = { DialogBody { Text(uiText(R.string.ui_connection_import_screen_036, pending.pharmacyName)) } },
            confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), onClick = { confirmDiscard = false; discard(pending.id) }, enabled = !state.busy) { Text(uiText(R.string.ui_connection_import_screen_037)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }, enabled = !state.busy) { Text(uiText(R.string.ui_connection_import_screen_038)) } })
    }
}
