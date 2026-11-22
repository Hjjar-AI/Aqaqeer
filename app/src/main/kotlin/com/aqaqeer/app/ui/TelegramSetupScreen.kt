package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import java.util.UUID
import kotlinx.serialization.json.*

@Composable internal fun TelegramSetup(vm: CatalogViewModel, busy: Boolean, empty: Boolean) {
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
