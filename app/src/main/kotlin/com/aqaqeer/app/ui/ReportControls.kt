package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var fieldsOpen by remember { mutableStateOf(false) }
    var layoutOpen by remember { mutableStateOf(false) }
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
            ReportAvailability.entries, options.availability, {
                text(if (it == ReportAvailability.OVERALL) R.string.report_availability_overall else R.string.report_availability_sections)
            }, enabled = !busy, itemKey = { it.name }) { change(options.copy(availability = it)) }
    }
    CollapsibleGroup(text(R.string.report_layout), layoutOpen, { layoutOpen = !layoutOpen }) {
        ChoicePicker(text(R.string.report_sort), ReportSort.entries, options.sort, { text(sortLabel(it)) },
            enabled = !busy, itemKey = { it.name }) { change(options.copy(sort = it)) }
        CheckChoiceRow(options.groupByCategory, text(R.string.report_group_category), enabled = !busy) { change(options.copy(groupByCategory = it)) }
        ChoicePicker(text(R.string.report_paper), ReportPaper.entries, options.paper, { it.name },
            enabled = !busy, itemKey = { it.name }) { change(options.copy(paper = it)) }
        CheckChoiceRow(options.landscape, text(R.string.report_landscape), enabled = !busy) { change(options.copy(landscape = it)) }
        CheckChoiceRow(options.compact, text(R.string.report_compact), enabled = !busy) { change(options.copy(compact = it)) }
        SupportingText(text(R.string.report_layout_help))
    }
}
