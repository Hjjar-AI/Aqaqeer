package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import com.aqaqeer.app.data.MedicationPresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Callers own state and workflows; a row exposes just one selection target. */
@Composable internal fun <T> RadioChoiceRows(choices: List<T>, selected: T?, select: (T) -> Unit,
    label: (T) -> String, enabled: Boolean = true, choiceModifier: (T) -> Modifier = { Modifier }, choiceEnabled: (T) -> Boolean = { true },
    content: (@Composable (T) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        choices.forEach { choice ->
            val rowEnabled = enabled && choiceEnabled(choice)
            Row(Modifier.fillMaxWidth().background(if (selected == choice) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .selectable(selected = selected == choice, enabled = rowEnabled,
                    role = Role.RadioButton, onClick = { select(choice) }).then(choiceModifier(choice))
                .heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.related, vertical = UiSpacing.hair),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.related), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == choice, onClick = null, enabled = rowEnabled)
                if (content != null) Box(Modifier.weight(1f)) { content(choice) }
                else Text(label(choice), modifier = Modifier.weight(1f), color = if (rowEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun CheckChoiceRow(checked: Boolean, label: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, supporting: String? = null, change: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = change)
        .heightIn(min = UiSpacing.touch).padding(vertical = UiSpacing.hair),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.related), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            supporting?.let { SupportingText(it) }
        }
    }
}

@Composable internal fun SwitchChoiceRow(checked: Boolean, label: String, supporting: String,
    modifier: Modifier = Modifier, change: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = change)
        .heightIn(min = UiSpacing.touch).padding(vertical = UiSpacing.hair),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.related), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label); SupportingText(supporting) }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable internal fun <T> PreferenceChoiceRow(title: String, choices: List<T>, selected: T,
    select: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier,
    choiceModifier: (T) -> Modifier = { Modifier },
    swatch: ((T) -> androidx.compose.ui.graphics.Color)? = null) {
    ContentSection(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        WrappingContent(Modifier.selectableGroup()) {
            choices.forEach { choice ->
                FilterChip(selected = choice == selected, onClick = { select(choice) }, label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                        swatch?.let { Box(Modifier.size(12.dp).background(it(choice), androidx.compose.foundation.shape.CircleShape)) }
                        Text(label(choice))
                    }
                },
                    leadingIcon = if (choice == selected) ({ AppIcon(UiIcon.CHECK) }) else null,
                    modifier = choiceModifier(choice).semantics { role = Role.RadioButton })
            }
        }
    }
}

@Composable internal fun <T> ChoicePicker(label: String, values: List<T>, selected: T?, name: (T) -> String,
    enabled: Boolean = true, choose: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.clickable(enabled = enabled, role = Role.Button) { expanded = true }
                .heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.section, vertical = UiSpacing.related), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                Column(Modifier.weight(1f)) {
                    SupportingText(label)
                    Text(selected?.let(name) ?: uiText(R.string.ui_choice_components_001), color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RowChevron(down = true)
            }
        }
        if (expanded && enabled) {
            var query by rememberSaveable { mutableStateOf("") }
            val labeled = remember(values, name) { values.map { value -> val text = name(value); Triple(value, text, MedicationPresentation.normalizedSearch(text)) } }
            val visible = remember(labeled, query) {
                val normalized = MedicationPresentation.normalizedSearch(query)
                labeled.filter { it.third.contains(normalized) }
            }
            AlertDialog(onDismissRequest = { expanded = false }, title = { Text(label) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                    if (values.size > 8) FormField(uiText(R.string.ui_choice_components_002), query, { query = it })
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = viewportHeight(0.5f))) {
                        items(visible) { (value, text, _) ->
                            TextButton(onClick = { choose(value); expanded = false }, modifier = Modifier.fillMaxWidth()) {
                                Text(text, modifier = Modifier.weight(1f))
                                if (value == selected) AppIcon(UiIcon.CHECK)
                            }
                        }
                    }
                    if (visible.isEmpty()) Text(uiText(R.string.ui_choice_components_003))
                }
            }, confirmButton = { TextButton(onClick = { expanded = false }) { Text(uiText(R.string.ui_choice_components_004)) } })
        }
    }
}

/** Decorative chevron follows navigation direction without adding a spoken label. */
@Composable internal fun RowChevron(down: Boolean = false) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(20.dp)) {
        val a: Offset; val b: Offset; val c: Offset
        if (down) {
            a = Offset(size.width * .25f, size.height * .4f)
            b = Offset(size.width * .5f, size.height * .65f)
            c = Offset(size.width * .75f, size.height * .4f)
        } else {
            val start = if (rtl) .65f else .35f
            val end = if (rtl) .35f else .65f
            a = Offset(size.width * start, size.height * .25f)
            b = Offset(size.width * end, size.height * .5f)
            c = Offset(size.width * start, size.height * .75f)
        }
        drawLine(color, a, b, strokeWidth = 2.dp.toPx())
        drawLine(color, b, c, strokeWidth = 2.dp.toPx())
    }
}
