package com.aqaqeer.app.ui

import com.aqaqeer.app.R

import androidx.compose.material3.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aqaqeer.app.sync.SetupGroup
import java.util.UUID

@Composable internal fun TelegramSetup(vm: CatalogViewModel, busy: Boolean, empty: Boolean) {
    val uiText = rememberUiText()
    var token by remember { mutableStateOf(if (com.aqaqeer.app.BuildConfig.IS_DEMO) "DEMO-CONNECTION" else "") }; var group by remember { mutableStateOf(if (com.aqaqeer.app.BuildConfig.IS_DEMO) "-901" else "") }
    var inspected by remember(token, group) { mutableStateOf<SetupGroup?>(null) }
    var confirmUnpin by remember(token, group, inspected) { mutableStateOf(false) }
    val title = inspected?.title
    var found by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var offerEmptyPublication by remember { mutableStateOf(false) }
    val challenge = remember { UUID.randomUUID().toString().replace("-", "").take(24) }
    val clipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    if (com.aqaqeer.app.BuildConfig.IS_DEMO) NoticeStrip(Notice(uiText(R.string.demo_connection_help)))
    var guideExpanded by rememberSaveable { mutableStateOf(false) }
    GroupedCard {
        CollapsibleGroup(uiText(R.string.admin_setup_instructions_heading), guideExpanded,
            { guideExpanded = !guideExpanded }) {
            ContentSection(title = uiText(R.string.connection_create_bot_step_title)) {
                SupportingText(uiText(R.string.connection_create_bot_step_help))
            }
            ContentSection(title = uiText(R.string.connection_setup_group_step_title)) {
                SupportingText(uiText(R.string.connection_setup_group_step_help))
            }
            SupportingText(uiText(R.string.connection_dedicated_bot_help))
        }
    }
    StepHeader(listOf(uiText(R.string.connection_discover_group_action),
        uiText(R.string.connection_verify_group_action), uiText(R.string.admin_setup_group_confirmation_heading)),
        if (title != null) 2 else if (group.isNotBlank()) 1 else 0)
    GroupedCard(title = uiText(R.string.connection_telegram_details_title)) {
        SecretField(uiText(R.string.connection_bot_token_label), token, { token = it }, required = true)
        ContentSection(title = uiText(R.string.connection_discover_group_step_title)) {
            SupportingText(uiText(R.string.connection_discover_group_step_help))
            SelectionContainer { Text(bidi("/aqsetup $challenge"), style = MaterialTheme.typography.bodyLarge) }
            ActionGroup {
                AppButton(variant = AppButtonVariant.SECONDARY, onClick = {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Aqaqeer", "/aqsetup $challenge"))
                }) { Text(uiText(R.string.connection_copy_setup_command_action)) }
                AppButton(enabled = !busy && token.isNotBlank(),
                    onClick = { vm.command { found = vm.sharing.discover(token, challenge) } }) {
                    Text(uiText(R.string.connection_discover_group_action))
                }
            }
            if (found.isNotEmpty()) RadioChoiceRows(found, found.find { it.first.toString() == group },
                { group = it.first.toString() }, { it.second })
        }
    }
    GroupedCard(title = uiText(R.string.connection_verify_group_action)) {
        FormField(uiText(R.string.connection_group_id_label), group, { group = groupIdInput(it) },
            required = true, validate = FormGuidance::group, supportingText = uiText(R.string.connection_group_id_help),
            ltr = true, imeAction = ImeAction.Done)
        AppButton(variant = AppButtonVariant.SECONDARY,
            enabled = !busy && group.toLongOrNull()?.let { it < 0 } == true, onClick = {
                val submittedToken = token
                val submittedGroup = group.toLong()
                vm.command {
                    val result = vm.sharing.inspectSetupGroup(submittedToken, submittedGroup)
                    if (token == submittedToken && group.toLongOrNull() == submittedGroup) inspected = result
                }
            }) { Text(uiText(R.string.connection_verify_group_action)) }
    }
    title?.let { name ->
        GroupedCard(title = uiText(R.string.admin_setup_group_confirmation_heading)) {
            Text(uiText(R.string.connection_group_title_format, name))
            SupportingText(uiText(R.string.pin_group_guidance))
            if (inspected?.pin != null) {
                NoticeStrip(Notice(uiText(R.string.pin_setup_unpin_help), NoticeSeverity.WARNING))
                AppButton(variant = AppButtonVariant.DESTRUCTIVE_OUTLINED, enabled = !busy,
                    onClick = { confirmUnpin = true }) { Text(uiText(R.string.pin_setup_unpin_action)) }
            }
            AppButton(enabled = !busy && inspected?.pin == null, onClick = {
                vm.command {
                    vm.sharing.saveSetup(token, group.toLong(), name)
                    token = ""
                    if (empty) offerEmptyPublication = true else vm.navigate(Route.Admin)
                }
            }) { Text(uiText(R.string.connection_save_setup_action)) }
        }
    }
    if (confirmUnpin) AppDialog(onDismissRequest = { if (!busy) confirmUnpin = false },
        title = { Text(uiText(R.string.pin_setup_unpin_action)) },
        body = { Text(uiText(R.string.pin_setup_unpin_warning, title.orEmpty())) },
        confirmButton = { AppButton(variant = AppButtonVariant.DESTRUCTIVE, enabled = !busy, onClick = {
            val expected = inspected ?: return@AppButton
            val submittedToken = token
            val submittedGroup = group.toLong()
            vm.command {
                val result = vm.sharing.clearSetupPin(submittedToken, submittedGroup, expected)
                if (token == submittedToken && group.toLongOrNull() == submittedGroup) inspected = result
                confirmUnpin = false
            }
        }) { Text(uiText(R.string.pin_setup_unpin_action)) } },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, enabled = !busy,
            onClick = { confirmUnpin = false }) { Text(uiText(R.string.pin_setup_unpin_cancel)) } })
    if (offerEmptyPublication) AppDialog(onDismissRequest = { offerEmptyPublication = false; vm.navigate(Route.Admin) },
        title = { Text(uiText(R.string.publication_empty_pharmacy_confirm_title)) },
        body = { Text(uiText(R.string.publication_empty_pharmacy_confirm_help)) },
        confirmButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { offerEmptyPublication = false; vm.navigate(Route.Admin) }) { Text(uiText(R.string.publication_defer_action)) } },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { offerEmptyPublication = false; vm.navigate(Route.Review); vm.publish() }) { Text(uiText(R.string.publication_publish_now_action)) } })
}
