package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import com.aqaqeer.app.admin.EditConflict
import com.aqaqeer.app.data.CatalogSnapshot

@Composable internal fun FormField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier,
    secret: Boolean = false, required: Boolean? = null, supportingText: String? = null,
    errorText: String? = null, submitted: Boolean = false, validate: ((String) -> String?)? = null,
    tag: String = label, keyboardOptions: androidx.compose.foundation.text.KeyboardOptions? = null,
    numericSecret: Boolean = false, ltr: Boolean = false, singleLine: Boolean = true,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default, onDone: (() -> Unit)? = null, trailingContent: (@Composable () -> Unit)? = null, rule: String? = null) {
    val uiText = rememberUiText()
    val focus = LocalFocusManager.current
    var focusedOnce by rememberSaveable(tag) { mutableStateOf(false) }
    var touched by rememberSaveable(tag) { mutableStateOf(false) }
    // Visibility is intentionally never saved across recreation.
    var visible by remember(label) { mutableStateOf(false) }
    val weakPin = secret && numericSecret && FormGuidance.simpleNumericPattern(value)
    val validationError = errorText ?: if (required == true && (if (secret) value.isEmpty() else value.isBlank()))
        uiText(R.string.shared_field_required_error) else validate?.invoke(value)
    val error = validationError.takeIf { touched || submitted }
    val shortLabel = label
    val hint = listOfNotNull(supportingText ?: rule,
        if (required == false) uiText(R.string.shared_field_optional_label) else null).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
    val focusRequester = remember { FocusRequester() }
    val reveal = remember { BringIntoViewRequester() }
    val focusController = LocalFormFocus.current
    val latestError by rememberUpdatedState(validationError)
    val target = remember { FormFocusTarget({ latestError != null }, { focusRequester.requestFocus() }, { reveal.bringIntoView() }) }
    DisposableEffect(focusController, target) {
        focusController?.targets?.add(target)
        onDispose { focusController?.targets?.remove(target) }
    }
    OutlinedTextField(value, change, label = { Text(shortLabel) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 8,
        textStyle = LocalTextStyle.current.copy(textDirection = if (ltr || numericSecret) TextDirection.Ltr else TextDirection.ContentOrRtl),
        isError = error != null,
        supportingText = if (error != null || hint != null || weakPin) ({
            Column { (error ?: hint)?.let { Text(it) }; if (weakPin) Text(uiText(R.string.logic_weak_pattern)) }
        }) else null,
        keyboardOptions = (keyboardOptions ?: if (secret) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numericSecret) androidx.compose.ui.text.input.KeyboardType.NumberPassword else androidx.compose.ui.text.input.KeyboardType.Password,
            autoCorrectEnabled = false) else androidx.compose.foundation.text.KeyboardOptions.Default).copy(imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) },
            onDone = { focus.clearFocus(); onDone?.invoke() }),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({ AppIconButton(onClick = { visible = !visible }) {
            AppIcon(UiIcon.VISIBILITY, description = if (visible) uiText(R.string.shared_secret_hide_description, shortLabel) else uiText(R.string.shared_secret_show_description, shortLabel))
        } }) else trailingContent,
        modifier = modifier.fillMaxWidth().testTag(tag).bringIntoViewRequester(reveal).focusRequester(focusRequester).onFocusChanged {
            if (it.isFocused) focusedOnce = true else if (focusedOnce) touched = true
        })
}

@Composable internal fun GroupedCard(modifier: Modifier = Modifier, title: String? = null,
    spacing: Dp = UiSpacing.related,
    content: @Composable ColumnScope.() -> Unit) {
    AppCard(modifier.fillMaxWidth(), shape = CardDefaults.shape) {
        ContentSection(title = title, modifier = Modifier.padding(UiSpacing.section), spacing = spacing, content = content)
    }
}

@Composable internal fun NavigationGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    GroupedCard(title = title, spacing = Space.none, content = content)
}

@Composable internal fun SupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Use inside a grouped surface; navigation never commits an operation. */
@Composable internal fun NavigationRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, supporting: String? = null, destructive: Boolean = false) {
    Row(modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = UiSpacing.touch).padding(vertical = UiSpacing.related),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            })
            supporting?.let { SupportingText(it) }
        }
        RowChevron()
    }
}

@Composable internal fun NavigationCard(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, supporting: String? = null, destructive: Boolean = false) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(AppStroke.hairline, MaterialTheme.colorScheme.outline)) {
        Box(Modifier.padding(horizontal = UiSpacing.section)) {
            NavigationRow(label, onClick, enabled = enabled, supporting = supporting, destructive = destructive)
        }
    }
}

@Composable internal fun OperationStatus(busy: Boolean, error: String?, message: String? = null, cancel: (() -> Unit)? = null) {
    if (busy || error != null || message != null) GroupedCard {
        InlineOperationStatus(busy, error, message, cancel)
    }
}

@Composable internal fun InlineOperationStatus(busy: Boolean, error: String?, message: String? = null,
    cancel: (() -> Unit)? = null) {
    val uiText = rememberUiText()
    if (busy || error != null || message != null) Column(Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            cancel?.let { AppButton(variant = AppButtonVariant.QUIET, onClick = it) { Text(uiText(R.string.shared_cancel_operation_action)) } }
        }
        error?.let { NoticeStrip(Notice(it, NoticeSeverity.ERROR)) }
        message?.let { NoticeStrip(Notice(it)) }
    }
}

@Composable internal fun ConflictChoices(conflicts: List<EditConflict>, choices: Map<String, Boolean>,
    catalog: CatalogSnapshot, choose: (Map<String, Boolean>) -> Unit) {
    val uiText = rememberUiText()
    conflicts.forEach { conflict ->
        if (conflict.key.startsWith("missing:")) {
            RetainedPinRecoveryChoice(conflict, choices[conflict.key] == true) {
                choose(choices + (conflict.key to it))
            }
            return@forEach
        }
        GroupedCard(title = "${conflict.entity} · ${fieldLabel(conflict.field)}") {
            val deletion = conflict.key.startsWith("delete:") || conflict.field == uiText(R.string.publication_changes_review_label)
            val restoreParent = conflict.key.startsWith("parent:")
            val restoreReference = conflict.key.startsWith("reference:")
            Text(if (deletion) uiText(R.string.publication_delete_conflict_help) else uiText(R.string.publication_choose_value_help))
            AdaptivePickers(first = { Text(uiText(R.string.publication_local_value_format, reviewValue(conflict.field, conflict.mine, catalog, text = uiText))) },
                second = { Text(uiText(R.string.publication_published_value_format, reviewValue(conflict.field, conflict.theirs, catalog, text = uiText))) })
            ExpandableInformation(uiText(R.string.publication_original_value_label), reviewValue(conflict.field, conflict.base, catalog, text = uiText))
            RadioChoiceRows(listOf(true, false), choices[conflict.key],
                { choose(choices + (conflict.key to it)) },
                { mine -> when {
                    deletion -> if (mine) uiText(R.string.publication_confirm_delete_dependents_action) else uiText(R.string.publication_keep_published_entity_action)
                    restoreParent -> if (mine) uiText(R.string.publication_restore_related_entity_action) else uiText(R.string.publication_cancel_availability_change_action)
                    restoreReference -> if (mine) uiText(R.string.publication_restore_taxonomy_action) else uiText(R.string.publication_use_default_taxonomy_action)
                    else -> if (mine) uiText(R.string.publication_use_local_value_action) else uiText(R.string.publication_use_published_value_action)
                } })
        }
    }
}

@Composable private fun RetainedPinRecoveryChoice(conflict: EditConflict, confirmed: Boolean,
    confirm: (Boolean) -> Unit) {
    val uiText = rememberUiText()
    GroupedCard(title = uiText(R.string.pin_recovery_title)) {
        LabeledValue(uiText(R.string.pin_recovery_retained), bidi(conflict.base))
        Text(conflict.theirs)
        Text(conflict.mine, color = MaterialTheme.colorScheme.error)
        CheckChoiceRow(confirmed, uiText(R.string.pin_recovery_confirm), change = confirm)
    }
}

@Composable internal fun BackupReminderCard(export: () -> Unit, dismiss: () -> Unit) {
    val uiText = rememberUiText()
    GroupedCard(title = uiText(R.string.backup_reminder_title)) {
        SupportingText(uiText(R.string.backup_reminder_help))
        ActionGroup {
            AppButton(onClick = export) { Text(uiText(R.string.backup_reminder_export_action)) }
            AppButton(variant = AppButtonVariant.QUIET, onClick = dismiss) { Text(uiText(R.string.backup_reminder_snooze_action)) }
        }
    }
}

/** Shared actions, with each caller retaining its own picker and workflow. */
@Composable internal fun ExportButtons(enabled: Boolean, actions: List<Pair<String, () -> Unit>>) {
    ActionGroup {
        actions.forEach { (label, action) -> AppButton(enabled = enabled, onClick = action) { Text(label) } }
    }
}

/** Secrets belong only to the open picker composition, never saved state or preferences. */
internal class BackupExportSecrets {
    var password: CharArray? = null
    var confirmation: CharArray? = null
    fun clear() { password?.fill('\u0000'); confirmation?.fill('\u0000'); password = null; confirmation = null }
}

@Composable internal fun BackupPassphraseFields(password: String, confirmation: String, setPassword: (String) -> Unit,
    setConfirmation: (String) -> Unit) {
    val uiText = rememberUiText()
    SecretField(uiText(R.string.backup_passphrase_label), password, setPassword, required = true, supportingText = uiText(R.string.backup_passphrase_help), validate = { if (it.codePointCount(0, it.length) >= 8) null else uiText(R.string.backup_passphrase_min_length_error) })
    SecretField(uiText(R.string.backup_passphrase_confirmation_label), confirmation, setConfirmation, required = true, imeAction = ImeAction.Done, validate = { if (it == password) null else uiText(R.string.backup_passphrase_mismatch_error) })
}

/** A short explanation stays available without occupying the primary reading path. */
@Composable internal fun ExpandableInformation(title: String, body: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    CollapsibleGroup(title, expanded, { expanded = !expanded }) { SupportingText(body) }
}

@Composable internal fun SecretField(label: String,
    value: String,
    change: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean? = null,
    supportingText: String? = null,
    errorText: String? = null,
    submitted: Boolean = false,
    validate: ((String) -> String?)? = null,
    tag: String = label,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions? = null,
    numericSecret: Boolean = false,
    ltr: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    rule: String? = null) {
    FormField(label = label, value = value, change = change, modifier = modifier, required = required, supportingText = supportingText, errorText = errorText, submitted = submitted, validate = validate, tag = tag, keyboardOptions = keyboardOptions, numericSecret = numericSecret, ltr = ltr, imeAction = imeAction, onDone = onDone, rule = rule, secret = true)
}

@Composable internal fun MultilineField(label: String,
    value: String,
    change: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean? = null,
    supportingText: String? = null,
    errorText: String? = null,
    submitted: Boolean = false,
    validate: ((String) -> String?)? = null,
    tag: String = label,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions? = null,
    ltr: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
    rule: String? = null) {
    FormField(label = label, value = value, change = change, modifier = modifier, required = required, supportingText = supportingText, errorText = errorText, submitted = submitted, validate = validate, tag = tag, keyboardOptions = keyboardOptions, ltr = ltr, imeAction = imeAction, onDone = onDone, rule = rule, singleLine = false)
}
