package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun HistoryEntryCard(entry: HistoryDisplayEntry, catalog: CatalogSnapshot, timezone: String,
    medicationNames: Map<String, String>, editMedication: ((String) -> Unit)?, showHijri: Boolean = false) {
    val uiText = rememberUiText()
    val recordBeforeName = remember(entry.before, catalog.forms) { historyRecordName(entry.before, catalog.forms) }
    val recordAfterName = remember(entry.after, catalog.forms) { historyRecordName(entry.after, catalog.forms) }
    val medicationId = when (entry.entityType) {
        "medication" -> entry.entityId
        "availability" -> entry.entityId.substringBefore('/')
        else -> null
    }
    val medication = catalog.medications.find { it.id == medicationId }
    val name = entry.entityName.takeIf { it.isNotBlank() && it !in setOf(entry.entityType, entry.entityId, medicationId) }
        ?: catalog.historyName(entry.entityType, entry.entityId)
        ?: medicationId?.let(medicationNames::get)
        ?: recordAfterName ?: recordBeforeName
        ?: uiText(R.string.history_entity_identifier_format, entry.entityId)
    val publication = entry.entityType == "snapshot" && entry.field == "publication"
    val recovery = entry.entityType == "snapshot" && entry.field == "recoveryCandidate"
    val restoreReview = entry.entityType == "snapshot" && entry.field in setOf("restoreReplacementReviewed", "restoreReconciled")
    val observed = entry.observedAt != null && !publication && !recovery && !restoreReview
    var details by rememberSaveable(entry.id) { mutableStateOf(false) }
    val time = if (observed) entry.observedAt else entry.editedAt ?: entry.observedAt
    val context = when {
        publication -> uiText(R.string.history_verified_publication_label)
        recovery -> uiText(R.string.history_retained_unapplied_snapshot_label)
        entry.entityType == "maintenance" -> uiText(R.string.history_local_maintenance_label)
        restoreReview -> uiText(R.string.history_restore_review_label)
        observed -> uiText(R.string.history_observed_remote_change_label)
        entry.editedAt != null -> uiText(R.string.history_local_change_label)
        else -> uiText(R.string.history_unknown_source_label)
    }
    GroupedCard(modifier = Modifier.animateContentSize(tween(Motion.standard))) {
        Text(bidi(name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(context + if (publication || recovery) "" else " · ${fieldLabel(entry.field, entry.entityType)}")
        if (entry.entityType == "availability") {
            val sectionId = entry.entityId.substringAfter('/', "")
            Text(uiText(R.string.history_section_prefix) + (catalog.sections.find { it.id == sectionId }?.nameAr ?: uiText(R.string.history_deleted_section_label)))
        }
        if (!publication && !recovery) {
            if (entry.valuesHidden) Text(if (entry.entityType in setOf("snapshot", "maintenance"))
                uiText(R.string.history_recovery_values_redacted_help)
                else uiText(R.string.history_sensitive_values_redacted_help))
            else {
                val beforeStatus = remember(entry.entityType, entry.before) { if (entry.entityType == "availability") historyStatus(entry.before) else null }
                val afterStatus = remember(entry.entityType, entry.after) { if (entry.entityType == "availability") historyStatus(entry.after) else null }
                if (beforeStatus != null && afterStatus != null) WrappingContent {
                    Column { Text(uiText(R.string.publication_before_label)); StatusBadge(beforeStatus) }
                    Text("→")
                    Column { Text(uiText(R.string.publication_after_label)); StatusBadge(afterStatus) }
                } else {
                    HistoryValueLine(uiText(R.string.publication_before_label), entry.field, entry.before, catalog)
                    Text("→")
                    HistoryValueLine(uiText(R.string.publication_after_label), entry.field, entry.after, catalog)
                }
            }
        }
        Text((if (observed) uiText(R.string.history_observed_time_prefix) else "") +
            (time?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.history_unknown_time_label)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        time?.let { SecondaryHijriDate(MedicationPresentation.today(it, timezone), showHijri) }
        if (observed) SupportingText(uiText(R.string.history_observed_time_caveat_help))
        AppButton(variant = AppButtonVariant.QUIET, onClick = { details = !details }) { Text(if (details) uiText(R.string.history_hide_technical_details_action) else uiText(R.string.history_technical_details_title)) }
        if (details) {
            HorizontalDivider()
            CopyableValue(uiText(R.string.history_event_identifier_label), entry.id)
            CopyableValue(if (publication || recovery) uiText(R.string.history_publication_identifier_label) else uiText(R.string.history_entity_identifier_label), entry.entityId)
            if (publication) Text(uiText(R.string.history_publication_attribution_help))
            if (entry.entityType == "maintenance") Text(uiText(R.string.history_non_medication_event_help))
            if (!publication) Text(uiText(R.string.history_original_edit_time_prefix) +
                (entry.editedAt?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.history_unknown_label)))
            entry.editedAt?.takeIf { it != time }?.let { SecondaryHijriDate(MedicationPresentation.today(it, timezone), showHijri) }
            entry.observedAt?.let {
                Text(uiText(R.string.history_device_observed_time_format, bidi(MedicationPresentation.timestamp(it, timezone))))
                if (it != time) SecondaryHijriDate(MedicationPresentation.today(it, timezone), showHijri)
            }
            entry.publishedRevision?.let { Text(uiText(R.string.history_publication_revision_format, bidi(it.toString()))) }
            // Missing acknowledgement is not proof that net work remains unpublished.
            if (entry.publishedRevision == null && !publication && !recovery)
                Text(uiText(R.string.history_missing_publication_revision_help))
        }
        if (details && medicationId != null) {
            if (medication == null) SupportingText(uiText(R.string.history_deleted_medication_label))
            else editMedication?.let { edit -> AppButton(variant = AppButtonVariant.QUIET, onClick = { edit(medication.id) }) { Text(uiText(R.string.history_edit_medication_action_format, bidi(name))) } }
        }
    }
}

@Composable private fun HistoryValueLine(label: String, field: String, preview: HistoryValue, catalog: CatalogSnapshot) {
    val uiText = rememberUiText()
    val raw = preview.text
    val element = remember(raw) { raw?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } }
    val text = when {
        raw == null -> uiText(R.string.history_missing_value_label)
        element == JsonNull -> if (field in setOf("@entity", "record")) uiText(R.string.publication_deleted_value_label) else uiText(R.string.publication_empty_value_label)
        element is JsonObject -> {
            val name = remember(preview, catalog.forms) { historyRecordName(preview, catalog.forms) }
            name ?: (element["status"] as? JsonPrimitive)?.contentOrNull?.let {
                runCatching { statusLabel(AvailabilityStatus.valueOf(it), text = uiText) }.getOrNull()
            } ?: uiText(R.string.history_related_record_label)
        }
        field in setOf("@entity", "record") -> uiText(R.string.history_related_record_label)
        element is JsonPrimitive -> reviewValue(field, raw, catalog, limit = 512, text = uiText)
        else -> raw
    }
    val display = historyPreview(text)
    Text("${label}: ${bidi(display.text.orEmpty())}")
    if (preview.shortened || display.shortened) Text(uiText(R.string.history_truncated_display_help), style = MaterialTheme.typography.bodySmall)
}

private fun historyRecordName(preview: HistoryValue, forms: List<MedicationForm>): String? {
    val element = preview.text?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return null
    return MedicationPresentation.recordIdentity(element, forms)
}

private fun historyStatus(preview: HistoryValue): AvailabilityStatus? = runCatching {
    val element = Json.parseToJsonElement(preview.text ?: return@runCatching null)
    val raw = if (element is JsonObject) element["status"] else element
    AvailabilityStatus.valueOf((raw as? JsonPrimitive)?.contentOrNull ?: return@runCatching null)
}.getOrNull()
