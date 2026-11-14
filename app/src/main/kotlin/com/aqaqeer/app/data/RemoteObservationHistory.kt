package com.aqaqeer.app.data

import com.aqaqeer.app.security.files.SnapshotCandidate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Observed changes only: the last publisher/record editor is not proof of a particular field's author. */
internal object RemoteObservationHistory {
    private val json = Json { encodeDefaults = true }
    fun changes(before: CatalogSnapshot, after: CatalogSnapshot, at: Instant, revision: Long): List<HistoryEntry> {
        val old = records(before); val next = records(after)
        return (old.keys + next.keys).flatMap { key ->
            val left = old[key]; val right = next[key]
            val fields = if (left == null || right == null) setOf("record")
                else (left.keys + right.keys) - setOf("updatedAt", "updatedBy")
            fields.mapNotNull { field ->
                val previous = if (field == "record") left else left?.get(field)
                val current = if (field == "record") right else right?.get(field)
                if (previous == current) null else HistoryEntry(UUID.randomUUID().toString(), null,
                    key.first, key.second, if (key.first == "availability") {
                        (after.medications + before.medications).firstOrNull { it.id == key.second.substringBefore('/') }
                            ?.let { MedicationPresentation.identity(it, (after.forms + before.forms).find { form -> form.id == it.formId }?.nameAr) } ?: key.second
                    } else display(right ?: left!!, key.second, after.forms + before.forms), field,
                    previous?.toString(), current?.toString(), null, null, null, at, revision)
            }
        }
    }
    /** Publisher attribution belongs to a publication event, never to an inferred field edit. */
    fun publication(baseId: String, snapshot: SnapshotCandidate.Supported, at: Instant): HistoryEntry {
        val metadata = buildJsonObject {
            put("publishId", snapshot.publishId); put("publishedAt", snapshot.publishedAt.toString())
            put("publisherId", snapshot.publishedBy.id); put("publisherName", snapshot.publishedBy.name)
        }
        return HistoryEntry(UUID.randomUUID().toString(), null, "snapshot", snapshot.publishId,
            requireNotNull(snapshot.catalog.pharmacy).name, "publication", baseId, metadata.toString(),
            snapshot.publishedBy.id, snapshot.publishedBy.name, snapshot.publishedAt, at, snapshot.revision)
    }
    private fun display(record: JsonObject, fallback: String, forms: List<MedicationForm>): String =
        MedicationPresentation.recordIdentity(record, forms) ?: fallback
    private fun records(catalog: CatalogSnapshot): Map<Pair<String,String>,JsonObject> = buildMap {
        fun add(type: String, id: String, record: JsonElement) { put(type to id, record.jsonObject) }
        catalog.pharmacy?.let { add("pharmacy", it.pharmacyId, json.parseToJsonElement(json.encodeToString(it))) }
        catalog.categories.forEach { add("category", it.id, json.parseToJsonElement(json.encodeToString(it))) }
        catalog.forms.forEach { add("form", it.id, json.parseToJsonElement(json.encodeToString(it))) }
        catalog.sections.forEach { add("section", it.id, json.parseToJsonElement(json.encodeToString(it))) }
        catalog.medications.forEach { add("medication", it.id, json.parseToJsonElement(json.encodeToString(it))) }
        catalog.availability.forEach { add("availability", "${it.medicationId}/${it.sectionId}", json.parseToJsonElement(json.encodeToString(it))) }
        catalog.tombstones.forEach { add("tombstone", "${it.entityType}/${it.entityId}/${it.operationId}", json.parseToJsonElement(json.encodeToString(it))) }
    }
    /** A durable encrypted-DB observation retains the incoming candidate without replacing the base.
     * Deterministic identity deduplicates repeated reads; it is never a pending/publish operation.
     */
    fun recovery(pharmacyId: String, baseId: String, publishId: String, payload: String, at: Instant, revision: Long): HistoryEntry {
        val digest = MessageDigest.getInstance("SHA-256").digest((pharmacyId + baseId + payload).toByteArray())
        return HistoryEntry(UUID.nameUUIDFromBytes(digest).toString(), null, "snapshot", publishId,
            "نسخة منشورة بانتظار الاستعادة", "recoveryCandidate", baseId, payload,
            null, null, null, at, revision)
    }
}
