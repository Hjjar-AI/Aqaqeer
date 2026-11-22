package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun TaxonomyEditor(c: CatalogSnapshot, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    var type by remember { mutableStateOf("section") }; var selected by remember(type) { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var english by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }; var deleteName by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    fun resetConfirmation() { deleteName = ""; deleteConfirm = false }
    fun resetForm() { selected = null; name = ""; english = ""; active = true; resetConfirmation() }
    ChoicePicker(uiText(R.string.catalog_taxonomy_type_label), listOf("section", "category", "form"), type, ::entityLabel, itemKey = { it }) { type = it; resetForm() }
    data class RowValue(val id: String, val ar: String, val en: String?, val protected: Boolean, val active: Boolean = true)
    val rows = when (type) {
        "section" -> c.sections.map { RowValue(it.id, it.nameAr, it.nameEn, false, it.isActive) }
        "category" -> c.categories.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
        else -> c.forms.map { RowValue(it.id, it.nameAr, it.nameEn, it.isSystem) }
    }
    GroupedCard(title = uiText(R.string.catalog_taxonomy_items_heading)) {
        RadioChoiceRows(rows, rows.find { it.id == selected }, { row ->
            resetConfirmation()
            selected = row.id; name = row.ar; english = row.en ?: ""; active = row.active
        }, { row -> row.ar + when { row.protected -> uiText(R.string.catalog_protected_suffix); !row.active -> uiText(R.string.catalog_inactive_suffix); else -> "" } },
            choiceEnabled = { !it.protected })
    }
    AppButton(variant = AppButtonVariant.QUIET, onClick = ::resetForm) { Text(uiText(R.string.catalog_add_taxonomy_action)) }
    GroupedCard(title = uiText(R.string.catalog_edit_taxonomy_title)) {
        FormField(uiText(R.string.catalog_arabic_name_label), name, { name = it }, required = true); FormField(uiText(R.string.catalog_taxonomy_english_name_label), english, { english = it }, required = false, rule = uiText(R.string.catalog_taxonomy_english_name_rule), tag = uiText(R.string.catalog_english_name_label_with_guidance))
        if (type == "section") CheckChoiceRow(active, uiText(R.string.catalog_active_section_label)) { active = it }
        AppButton(enabled = !busy && name.isNotBlank(), onClick = { vm.command { vm.admin.saveTaxonomy(type, selected, name, english, active); resetForm() } }) { Text(uiText(R.string.catalog_save_taxonomy_action)) }
    }
    selected?.let { id ->
        GroupedCard(title = uiText(R.string.catalog_delete_taxonomy_action)) {
            Text(uiText(R.string.catalog_taxonomy_delete_consequences_help))
            if (type == "section") FormField(uiText(R.string.catalog_delete_section_name_confirmation_label), deleteName, { deleteName = it }, required = true)
            CheckChoiceRow(deleteConfirm, uiText(R.string.catalog_taxonomy_delete_confirmation_label)) { deleteConfirm = it }
            DestructiveConfirmButton(enabled = !busy && deleteConfirm, onClick = { vm.command { vm.admin.delete(type, id, deleteName); resetForm() } }, label = uiText(R.string.catalog_confirm_delete_action))
        }
    }
}
