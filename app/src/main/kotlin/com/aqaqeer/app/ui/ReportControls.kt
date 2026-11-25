package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aqaqeer.app.R
import com.aqaqeer.app.data.MedicationPresentation
import com.aqaqeer.app.reports.*

internal data class ReportChoice(val id: String, val label: String)

/** Reuses the viewport-aware lazy chooser; large catalogs are never expanded into form rows. */
@Composable internal fun ReportMultiPicker(title: String, values: List<ReportChoice>, selected: Set<String>,
    enabled: Boolean, emptyMeansAll: Boolean = false, change: (Set<String>) -> Unit) {
    val text = rememberUiText()
    var open by remember { mutableStateOf(false) }
    NavigationRow(title, { open = true }, enabled = enabled, supporting = if (selected.isEmpty() && emptyMeansAll)
        text(R.string.report_all) else text(R.string.report_selected_count, selected.size.toString()))
    if (open && enabled) {
        var query by remember { mutableStateOf("") }
        val indexed = remember(values) { values.map { it to MedicationPresentation.normalizedSearch(it.label) } }
        val visible = remember(indexed, query) {
            val tokens = MedicationPresentation.normalizedSearch(query).split(' ').filter { it.isNotEmpty() }
            indexed.filter { row -> tokens.all { it in row.second } }.map { it.first }
        }
        SearchableChoiceDialog(title, query, { query = it }, text(R.string.shared_search_label), true,
            visible.isEmpty(), text(R.string.shared_no_matches_message), { open = false }) {
            item {
                ActionGroup {
                    AppButton(variant = AppButtonVariant.QUIET, onClick = { change(values.map { it.id }.toSet()) }) { Text(text(R.string.report_select_all)) }
                    AppButton(variant = AppButtonVariant.QUIET, onClick = { change(emptySet()) }) { Text(text(R.string.report_clear_selection)) }
                }
                if (emptyMeansAll) SupportingText(text(R.string.report_empty_filter_all))
            }
            items(visible, key = { it.id }) { value -> CheckChoiceRow(value.id in selected, value.label) {
                change(if (value.id in selected) selected - value.id else selected + value.id)
            } }
        }
    }
}

@Composable internal fun ReportPresentationControls(options: ReportOptions, busy: Boolean, change: (ReportOptions) -> Unit) {
    val text = rememberUiText()
    var fieldsOpen by rememberSaveable { mutableStateOf(false) }
    var layoutOpen by rememberSaveable { mutableStateOf(false) }
    CollapsibleGroup(text(R.string.report_fields), fieldsOpen, { fieldsOpen = !fieldsOpen }) {
        ReportField.entries.forEach { field -> CheckChoiceRow(field in options.fields, text(field.label),
            enabled = !busy && field != ReportField.SCIENTIFIC) {
            change(options.copy(fields = if (field in options.fields) options.fields - field else options.fields + field))
        } }
        SupportingText(text(R.string.report_scientific_required))
        ChoicePicker(text(R.string.report_name_language), ReportNames.entries, options.names, {
            text(when (it) { ReportNames.ARABIC -> R.string.report_names_arabic; ReportNames.ENGLISH -> R.string.report_names_english; ReportNames.BOTH -> R.string.report_names_both })
        }, enabled = !busy, itemKey = { it.name }) { change(options.copy(names = it)) }
        if (ReportField.AVAILABILITY in options.fields) ChoicePicker(text(R.string.report_availability_output),
            ReportAvailability.entries, options.availability, { text(it.label) },
            enabled = !busy, itemKey = { it.name }) { change(options.copy(availability = it)) }
        ReportFieldOrderControls(options, busy, change)
    }
    CollapsibleGroup(text(R.string.report_layout), layoutOpen, { layoutOpen = !layoutOpen }) {
        ChoicePicker(text(R.string.report_sort), ReportSort.entries, options.sort, { text(sortLabel(it)) },
            enabled = !busy, itemKey = { it.name }) { change(options.copy(sort = it)) }
        ChoicePicker(text(R.string.report_sort_direction), ReportSortDirection.entries, options.sortDirection,
            { text(it.label) }, enabled = !busy, itemKey = { it.name }) { change(options.copy(sortDirection = it)) }
        CheckChoiceRow(options.groupByCategory, text(R.string.report_group_category), enabled = !busy) { change(options.copy(groupByCategory = it)) }
        ChoicePicker(text(R.string.report_paper), ReportPaper.entries, options.paper, { text(it.label) },
            enabled = !busy, itemKey = { it.name }) { change(options.copy(paper = it)) }
        ChoicePicker(text(R.string.report_side_margins), ReportSideMargins.entries, options.sideMargins,
            { text(it.label) }, enabled = !busy, itemKey = { it.name }) { change(options.copy(sideMargins = it)) }
        CheckChoiceRow(options.landscape, text(R.string.report_landscape), enabled = !busy) { change(options.copy(landscape = it)) }
        CheckChoiceRow(options.compact, text(R.string.report_compact), enabled = !busy) { change(options.copy(compact = it)) }
        SupportingText(text(R.string.report_layout_help))
    }
}

@Composable private fun ReportFieldOrderControls(options: ReportOptions, busy: Boolean, change: (ReportOptions) -> Unit) {
    val text = rememberUiText()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val fields = options.selectedFieldsInOrder
    fun swap(field: ReportField, adjacent: ReportField) {
        val order = options.allFieldsInOrder.toMutableList()
        val first = order.indexOf(field)
        val second = order.indexOf(adjacent)
        order[first] = adjacent
        order[second] = field
        change(options.copy(fieldOrder = order))
    }
    CollapsibleGroup(text(R.string.report_field_order), expanded, { expanded = !expanded }) {
        SupportingText(text(R.string.report_field_order_help))
        fields.forEachIndexed { index, field ->
            key(field) {
                val label = text(field.label)
                val earlier = fields.getOrNull(index - 1)
                val later = fields.getOrNull(index + 1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text(R.string.report_field_position, index + 1, label), Modifier.weight(1f))
                    FieldOrderButton("↑", text(R.string.report_move_field_earlier, label), !busy && earlier != null) {
                        earlier?.let { swap(field, it) }
                    }
                    FieldOrderButton("↓", text(R.string.report_move_field_later, label), !busy && later != null) {
                        later?.let { swap(field, it) }
                    }
                }
            }
        }
        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy && options.fieldOrder.isNotEmpty(),
            onClick = { change(options.copy(fieldOrder = emptyList())) }) {
            Text(text(R.string.report_reset_field_order))
        }
    }
}

@Composable private fun FieldOrderButton(arrow: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    AppIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(UiSpacing.touch)
        .semantics { contentDescription = label }) {
        Text(arrow, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.titleLarge)
    }
}
