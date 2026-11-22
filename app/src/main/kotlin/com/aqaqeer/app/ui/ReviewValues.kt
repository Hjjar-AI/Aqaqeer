package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

internal fun entityLabel(type: String, text: UiText? = null) = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    when (type) { "medication" -> uiText(R.string.catalog_entity_medication_label); "availability" -> uiText(R.string.catalog_entity_availability_label); "category" -> uiText(R.string.catalog_entity_category_label); "form" -> uiText(R.string.catalog_entity_dosage_form_label); "section" -> uiText(R.string.catalog_entity_section_label); else -> uiText(R.string.catalog_entity_pharmacy_label) }
}

internal fun fieldLabel(field: String, entityType: String? = null, text: UiText? = null) = run {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    when(field) {
    "connectionActivated" -> uiText(R.string.history_connection_activated_label); "maintenanceFinished" -> uiText(R.string.history_maintenance_completed_label)
    "record" -> uiText(R.string.history_observed_entity_change_label); "adminPinVerifier" -> uiText(R.string.history_admin_pin_verifier_label)
    "restoreReconciled" -> uiText(R.string.history_restore_reconciled_label); "restoreReplacementReviewed" -> uiText(R.string.history_restore_replacement_reviewed_label)
    "@entity" -> uiText(R.string.history_entity_change_label); "nameEn" -> if (entityType == "medication") uiText(R.string.catalog_medication_trade_name_english_label) else uiText(R.string.catalog_english_name_label); "nameAr" -> if (entityType == "medication") uiText(R.string.catalog_medication_trade_name_arabic_label) else uiText(R.string.catalog_arabic_name_label)
    "status" -> uiText(R.string.catalog_entity_availability_label); "strength" -> uiText(R.string.catalog_strength_label); "genericAr" -> uiText(R.string.catalog_scientific_name_arabic_label)
    "genericEn" -> uiText(R.string.catalog_scientific_name_english_label); "brand" -> uiText(R.string.catalog_brand_label); "note" -> uiText(R.string.catalog_note_label)
    "expiryDate" -> uiText(R.string.catalog_expiry_date_label)
    "expirySoonThresholdDays" -> uiText(R.string.catalog_expiry_warning_days_label); "categoryId" -> uiText(R.string.catalog_category_label); "formId" -> uiText(R.string.catalog_dosage_form_label)
    "isActive" -> uiText(R.string.catalog_active_label); "orderIndex" -> uiText(R.string.catalog_order_label); else -> field
}
}

internal fun reviewValue(field: String, raw: String, catalog: CatalogSnapshot, limit: Int = 500, text: UiText? = null): String {
    val uiText = text ?: UiText(com.aqaqeer.app.AppStrings.resources)
    if (field == uiText(R.string.publication_changes_review_label)) return raw
    val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
    if (element == JsonNull) return if (field == "@entity") uiText(R.string.publication_deleted_value_label) else uiText(R.string.publication_empty_value_label)
    if (element is JsonObject) return MedicationPresentation.recordIdentity(element, catalog.forms) ?: uiText(R.string.publication_related_entity_label)
    val value = (element as? JsonPrimitive)?.contentOrNull ?: raw
    return when (field) {
        "status" -> runCatching { statusLabel(AvailabilityStatus.valueOf(value), text = uiText) }.getOrDefault(value)
        "categoryId" -> catalog.categories.find { it.id == value }?.nameAr ?: uiText(R.string.catalog_deleted_category_label)
        "formId" -> catalog.forms.find { it.id == value }?.nameAr ?: uiText(R.string.catalog_deleted_form_label)
        "isActive" -> if (value == "true") uiText(R.string.shared_yes_label) else uiText(R.string.shared_no_label)
        else -> value.take(limit)
    }
}
