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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.aqaqeer.app.admin.EditConflict
import com.aqaqeer.app.data.CatalogSnapshot

@Composable internal fun FormField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier,
    secret: Boolean = false, required: Boolean? = null, supportingText: String? = null,
    errorText: String? = null, submitted: Boolean = false, validate: ((String) -> String?)? = null,
    tag: String = label, keyboardOptions: androidx.compose.foundation.text.KeyboardOptions? = null,
    numericSecret: Boolean = false, ltr: Boolean = false, singleLine: Boolean = true,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default, onDone: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    var focusedOnce by remember(label) { mutableStateOf(false) }
    var touched by remember(label) { mutableStateOf(false) }
    // Visibility is intentionally never saved across recreation.
    var visible by remember(label) { mutableStateOf(false) }
    val validationError = errorText ?: if (required == true && (if (secret) value.isEmpty() else value.isBlank()))
        uiText(R.string.ui_admin_components_001) else validate?.invoke(value)
    val error = validationError.takeIf { touched || submitted }
    val shortLabel = label.substringBefore(" — ").substringBefore(" YYYY-MM-DD")
    val rule = label.removePrefix(shortLabel).trim().trimStart('—').trim().takeIf { it.isNotBlank() }
    val hint = listOfNotNull(supportingText ?: rule,
        when (required) { true -> uiText(R.string.ui_admin_components_002); false -> uiText(R.string.ui_admin_components_003); null -> null }).distinct().joinToString(" · ").takeIf { it.isNotBlank() }
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
        supportingText = if (error != null || hint != null) ({ Text(error ?: hint.orEmpty()) }) else null,
        keyboardOptions = (keyboardOptions ?: if (secret) androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numericSecret) androidx.compose.ui.text.input.KeyboardType.NumberPassword else androidx.compose.ui.text.input.KeyboardType.Password,
            autoCorrectEnabled = false) else androidx.compose.foundation.text.KeyboardOptions.Default).copy(imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) },
            onDone = { focus.clearFocus(); onDone?.invoke() }),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({ IconButton(onClick = { visible = !visible }) {
            AppIcon(UiIcon.VISIBILITY, description = if (visible) uiText(R.string.ui_admin_components_004, shortLabel) else uiText(R.string.ui_admin_components_005, shortLabel))
        } }) else null,
        modifier = modifier.fillMaxWidth().testTag(tag).bringIntoViewRequester(reveal).focusRequester(focusRequester).onFocusChanged {
            if (it.isFocused) focusedOnce = true else if (focusedOnce) touched = true
        })
}

@Composable internal fun GroupedCard(modifier: Modifier = Modifier, title: String? = null,
    spacing: Dp = UiSpacing.related,
    content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        ContentSection(title = title, modifier = Modifier.padding(UiSpacing.section), spacing = spacing, content = content)
    }
}

@Composable internal fun NavigationGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    GroupedCard(title = title, spacing = 0.dp, content = content)
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
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
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
    if (busy || error != null || message != null) Column(Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            cancel?.let { TextButton(onClick = it) { Text(uiText(R.string.ui_admin_components_006)) } }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it) }
    }
}

@Composable internal fun ConflictChoices(conflicts: List<EditConflict>, choices: Map<String, Boolean>,
    catalog: CatalogSnapshot, choose: (Map<String, Boolean>) -> Unit) {
    conflicts.forEach { conflict ->
        GroupedCard(title = "${conflict.entity} · ${fieldLabel(conflict.field)}") {
            val deletion = conflict.key.startsWith("delete:") || conflict.field == uiText(R.string.ui_admin_components_007)
            val restoreParent = conflict.key.startsWith("parent:")
            val restoreReference = conflict.key.startsWith("reference:")
            Text(if (deletion) uiText(R.string.ui_admin_components_008) else uiText(R.string.ui_admin_components_009))
            Text(uiText(R.string.ui_admin_components_010, reviewValue(conflict.field, conflict.mine, catalog)))
            Text(uiText(R.string.ui_admin_components_011, reviewValue(conflict.field, conflict.theirs, catalog)))
            ExpandableInformation(uiText(R.string.ui_admin_components_012), reviewValue(conflict.field, conflict.base, catalog))
            RadioChoiceRows(listOf(true, false), choices[conflict.key],
                { choose(choices + (conflict.key to it)) },
                { mine -> when {
                    deletion -> if (mine) uiText(R.string.ui_admin_components_013) else uiText(R.string.ui_admin_components_014)
                    restoreParent -> if (mine) uiText(R.string.ui_admin_components_015) else uiText(R.string.ui_admin_components_016)
                    restoreReference -> if (mine) uiText(R.string.ui_admin_components_017) else uiText(R.string.ui_admin_components_018)
                    else -> if (mine) uiText(R.string.ui_admin_components_019) else uiText(R.string.ui_admin_components_020)
                } })
        }
    }
}

@Composable internal fun BackupReminderCard(export: () -> Unit, dismiss: () -> Unit) {
    GroupedCard(title = uiText(R.string.ui_admin_components_021)) {
        SupportingText(uiText(R.string.ui_admin_components_022))
        ActionGroup {
            Button(onClick = export) { Text(uiText(R.string.ui_admin_components_023)) }
            TextButton(onClick = dismiss) { Text(uiText(R.string.ui_admin_components_024)) }
        }
    }
}

/** Shared actions, with each caller retaining its own picker and workflow. */
@Composable internal fun ExportButtons(enabled: Boolean, actions: List<Pair<String, () -> Unit>>) {
    ActionGroup {
        actions.forEach { (label, action) -> Button(enabled = enabled, onClick = action) { Text(label) } }
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
    FormField(uiText(R.string.ui_admin_components_025), password, setPassword, secret = true, required = true,
        supportingText = uiText(R.string.ui_admin_components_026), validate = { if (it.codePointCount(0, it.length) >= 8) null else uiText(R.string.ui_admin_components_027) })
    FormField(uiText(R.string.ui_admin_components_028), confirmation, setConfirmation, secret = true, required = true, imeAction = ImeAction.Done, validate = { if (it == password) null else uiText(R.string.ui_admin_components_029) })
}

/** A short explanation stays available without occupying the primary reading path. */
@Composable internal fun ExpandableInformation(title: String, body: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.animateContentSize(tween(180))) {
        TextButton(onClick = { expanded = !expanded }) {
            AppIcon(UiIcon.INFO)
            Spacer(Modifier.width(UiSpacing.hair))
            Text(if (expanded) uiText(R.string.ui_admin_components_030, title) else title)
            RowChevron(down = true)
        }
        if (expanded) Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
