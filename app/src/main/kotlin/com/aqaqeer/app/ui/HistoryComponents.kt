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
        ?: uiText(R.string.ui_history_components_001, entry.entityId)
    val publication = entry.entityType == "snapshot" && entry.field == "publication"
    val recovery = entry.entityType == "snapshot" && entry.field == "recoveryCandidate"
    val restoreReview = entry.entityType == "snapshot" && entry.field in setOf("restoreReplacementReviewed", "restoreReconciled")
    val observed = entry.observedAt != null && !publication && !recovery && !restoreReview
    var details by rememberSaveable(entry.id) { mutableStateOf(false) }
    val actor = entry.editorName?.takeIf(String::isNotBlank) ?: uiText(R.string.ui_history_components_002)
    val time = if (observed) entry.observedAt else entry.editedAt ?: entry.observedAt
    val context = when {
        publication -> uiText(R.string.ui_history_components_003)
        recovery -> uiText(R.string.ui_history_components_004)
        entry.entityType == "maintenance" -> uiText(R.string.ui_history_components_005)
        restoreReview -> uiText(R.string.ui_history_components_006)
        observed -> uiText(R.string.ui_history_components_007)
        entry.editedAt != null -> uiText(R.string.ui_history_components_008)
        else -> uiText(R.string.ui_history_components_009)
    }
    GroupedCard(modifier = Modifier.animateContentSize(tween(180))) {
        Text(bidi(name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(context + if (publication || recovery) "" else " · ${fieldLabel(entry.field, entry.entityType)}")
        if (entry.entityType == "availability") {
            val sectionId = entry.entityId.substringAfter('/', "")
            Text(uiText(R.string.ui_history_components_010) + (catalog.sections.find { it.id == sectionId }?.nameAr ?: uiText(R.string.ui_history_components_011)))
        }
        if (!publication && !recovery) {
            if (entry.valuesHidden) Text(if (entry.entityType in setOf("snapshot", "maintenance"))
                uiText(R.string.ui_history_components_012)
                else uiText(R.string.ui_history_components_013))
            else {
                val beforeStatus = remember(entry.entityType, entry.before) { if (entry.entityType == "availability") historyStatus(entry.before) else null }
                val afterStatus = remember(entry.entityType, entry.after) { if (entry.entityType == "availability") historyStatus(entry.after) else null }
                if (beforeStatus != null && afterStatus != null) WrappingContent {
                    Column { Text(uiText(R.string.ui_pharmacy_admin_screen_035)); StatusBadge(beforeStatus) }
                    Column { Text(uiText(R.string.ui_pharmacy_admin_screen_036)); StatusBadge(afterStatus) }
                } else {
                    HistoryValueLine(uiText(R.string.ui_pharmacy_admin_screen_035), entry.field, entry.before, catalog)
                    HistoryValueLine(uiText(R.string.ui_pharmacy_admin_screen_036), entry.field, entry.after, catalog)
                }
            }
        }
        Text((if (publication) uiText(R.string.ui_history_components_014) else if (observed) uiText(R.string.ui_history_components_015) else uiText(R.string.ui_history_components_016)) + bidi(actor) + " · " +
            (if (observed) uiText(R.string.ui_history_components_017) else "") +
            (time?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.ui_history_components_018)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        time?.let { SecondaryHijriDate(MedicationPresentation.today(it, timezone), showHijri) }
        if (observed) SupportingText(uiText(R.string.ui_history_components_019))
        TextButton(onClick = { details = !details }) { Text(if (details) uiText(R.string.ui_history_components_020) else uiText(R.string.ui_history_components_021)) }
        if (details) {
            HorizontalDivider()
            LabeledValue(uiText(R.string.ui_history_components_022), bidi(entry.id))
            LabeledValue(if (publication || recovery) uiText(R.string.ui_history_components_023) else uiText(R.string.ui_history_components_024), bidi(entry.entityId))
            entry.editorId?.let { LabeledValue(uiText(R.string.ui_history_components_025), bidi(it)) }
            if (publication) Text(uiText(R.string.ui_history_components_026))
            if (entry.entityType == "maintenance") Text(uiText(R.string.ui_history_components_027))
            if (!publication) Text(uiText(R.string.ui_history_components_028) +
                (entry.editedAt?.let { bidi(MedicationPresentation.timestamp(it, timezone)) } ?: uiText(R.string.ui_history_components_002)))
            entry.editedAt?.takeIf { it != time }?.let { SecondaryHijriDate(MedicationPresentation.today(it, timezone), showHijri) }
            entry.observedAt?.let {
                Text(uiText(R.string.ui_history_components_029, bidi(MedicationPresentation.timestamp(it, timezone))))
                if (it != time) SecondaryHijriDate(MedicationPresentation.today(it, timezone), showHijri)
            }
            entry.publishedRevision?.let { Text(uiText(R.string.ui_history_components_030, bidi(it.toString()))) }
            // Missing acknowledgement is not proof that net work remains unpublished.
            if (entry.publishedRevision == null && !publication && !recovery)
                Text(uiText(R.string.ui_history_components_031))
        }
        if (medicationId != null) {
            if (medication == null) SupportingText(uiText(R.string.ui_history_components_032))
            else editMedication?.let { edit -> TextButton(onClick = { edit(medication.id) }) { Text(uiText(R.string.ui_history_components_033, bidi(name))) } }
        }
    }
}

@Composable private fun HistoryValueLine(label: String, field: String, preview: HistoryValue, catalog: CatalogSnapshot) {
    val raw = preview.text
    val element = remember(raw) { raw?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } }
    val text = when {
        raw == null -> uiText(R.string.ui_history_components_034)
        element == JsonNull -> if (field in setOf("@entity", "record")) uiText(R.string.ui_pharmacy_admin_screen_077) else uiText(R.string.ui_pharmacy_admin_screen_078)
        element is JsonObject -> {
            val name = remember(preview, catalog.forms) { historyRecordName(preview, catalog.forms) }
            name ?: (element["status"] as? JsonPrimitive)?.contentOrNull?.let {
                runCatching { statusLabel(AvailabilityStatus.valueOf(it)) }.getOrNull()
            } ?: uiText(R.string.ui_history_components_035)
        }
        field in setOf("@entity", "record") -> uiText(R.string.ui_history_components_035)
        element is JsonPrimitive -> reviewValue(field, raw, catalog, limit = 512)
        else -> raw
    }
    val display = historyPreview(text)
    Text("${label}: ${bidi(display.text.orEmpty())}")
    if (preview.shortened || display.shortened) Text(uiText(R.string.ui_history_components_036), style = MaterialTheme.typography.bodySmall)
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
