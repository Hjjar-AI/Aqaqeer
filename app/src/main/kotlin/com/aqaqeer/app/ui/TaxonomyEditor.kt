package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun TaxonomyEditor(c: CatalogSnapshot, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    val pharmacy = c.pharmacy?.pharmacyId
    var type by rememberSaveable(pharmacy) { mutableStateOf("section") }
    var selected by rememberSaveable(pharmacy, type) { mutableStateOf<String?>(null) }
    var name by rememberSaveable(pharmacy) { mutableStateOf("") }
    var english by rememberSaveable(pharmacy) { mutableStateOf("") }
    var active by rememberSaveable(pharmacy) { mutableStateOf(true) }
    // Retain ordinary edits, but require a fresh destructive confirmation after recreation.
    var deleteName by remember(pharmacy, type, selected) { mutableStateOf("") }
    var deleteConfirm by remember(pharmacy, type, selected) { mutableStateOf(false) }
    fun resetConfirmation() { deleteName = ""; deleteConfirm = false }
    fun resetForm() { selected = null; name = ""; english = ""; active = true; resetConfirmation() }
    data class RowValue(val id: String, val ar: String, val en: String?, val protected: Boolean, val active: Boolean = true)
    val rows = remember(type, c.sections, c.categories, c.forms) { when (type) {
        "section" -> c.sections.map { RowValue(it.id, it.nameAr, it.nameEn, false, it.isActive) }
        "category" -> c.categories.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
        else -> c.forms.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
    } }
    val choose: (RowValue) -> Unit = { row ->
        resetConfirmation()
        selected = row.id; name = row.ar; english = row.en ?: ""; active = row.active
    }
    val label: (RowValue) -> String = { row -> row.ar + when {
        row.protected -> uiText(R.string.catalog_protected_suffix)
        !row.active -> uiText(R.string.catalog_inactive_suffix)
        else -> ""
    } }
    GroupedCard(title = uiText(R.string.catalog_taxonomy_items_heading)) {
        ChoicePicker(uiText(R.string.catalog_taxonomy_type_label), listOf("section", "category", "form"), type,
            ::entityLabel, enabled = !busy, itemKey = { it }) { type = it; resetForm() }
        AppDivider()
        if (type == "section") RadioChoiceRows(rows, rows.find { it.id == selected }, choose, label,
            enabled = !busy, choiceEnabled = { !it.protected })
        else key(type) { ChoicePicker(uiText(R.string.catalog_taxonomy_items_heading), rows,
            rows.find { it.id == selected }, label, enabled = !busy, itemKey = { it.id },
            choiceEnabled = { !it.protected }, choose = choose) }
        AppButton(variant = AppButtonVariant.QUIET, enabled = !busy, onClick = ::resetForm) {
            Text(uiText(R.string.catalog_add_taxonomy_action))
        }
    }
    GroupedCard(title = uiText(if (selected == null) R.string.admin_taxonomy_new_heading else R.string.catalog_edit_taxonomy_title)) {
        FormField(uiText(R.string.catalog_arabic_name_label), name, { name = it }, required = true)
        FormField(uiText(R.string.catalog_taxonomy_english_name_label), english, { english = it }, required = false, rule = uiText(R.string.catalog_taxonomy_english_name_rule), tag = uiText(R.string.catalog_english_name_label_with_guidance))
        if (type == "section") CheckChoiceRow(active, uiText(R.string.catalog_active_section_label)) { active = it }
        AppButton(enabled = !busy && name.isNotBlank(), onClick = { vm.command { vm.admin.saveTaxonomy(type, selected, name, english, active); resetForm() } }) { Text(uiText(R.string.catalog_save_taxonomy_action)) }
    }
    selected?.let { id ->
        GroupedCard(title = uiText(R.string.catalog_delete_taxonomy_action)) {
            NoticeStrip(Notice(uiText(R.string.catalog_taxonomy_delete_consequences_help), NoticeSeverity.WARNING))
            if (type == "section") FormField(uiText(R.string.catalog_delete_section_name_confirmation_label), deleteName, { deleteName = it }, required = true)
            CheckChoiceRow(deleteConfirm, uiText(R.string.catalog_taxonomy_delete_confirmation_label)) { deleteConfirm = it }
            DestructiveConfirmButton(enabled = !busy && deleteConfirm, onClick = { vm.command { vm.admin.delete(type, id, deleteName); resetForm() } }, label = uiText(R.string.catalog_confirm_delete_action))
        }
    }
}
