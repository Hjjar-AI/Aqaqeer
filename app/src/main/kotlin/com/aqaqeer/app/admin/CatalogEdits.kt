package com.aqaqeer.app.admin

import com.aqaqeer.app.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

internal val editJson = Json { encodeDefaults = true; explicitNulls = true }
internal fun newId() = UUID.randomUUID().toString()
internal data class EditConflict(val key: String, val entity: String, val field: String,
    val base: String, val mine: String, val theirs: String)
internal data class MergeResult(val catalog: CatalogSnapshot, val conflicts: List<EditConflict>)

/** Field comparison deliberately excludes derived attribution. Entity additions/deletions are indivisible. */
internal object CatalogEdits {
    fun rows(c: CatalogSnapshot, unchangedFrom: CatalogSnapshot? = null, targets: Set<String>? = null): Map<String, JsonObject> = buildMap {
        fun <T, K> different(values: List<T>, old: List<T>?, key: (T) -> K): List<T> {
            if (old == null) return values
            val previous = old.associateBy(key)
            return values.filter { previous[key(it)] != it }
        }
        fun putRows(type: String, values: List<JsonObject>, id: (JsonObject) -> String = { it.getValue("id").jsonPrimitive.content }) {
            values.forEach { put("$type:${id(it)}", it) }
        }
        c.pharmacy?.takeUnless { (unchangedFrom != null && it == unchangedFrom.pharmacy) || (targets != null && "pharmacy:${it.pharmacyId}" !in targets) }?.let { put("pharmacy:${it.pharmacyId}", editJson.encodeToJsonElement(it).jsonObject) }
        putRows("category", different(c.categories, unchangedFrom?.categories) { it.id }.filter { targets == null || "category:${it.id}" in targets }.map { editJson.encodeToJsonElement(it).jsonObject })
        putRows("form", different(c.forms, unchangedFrom?.forms) { it.id }.filter { targets == null || "form:${it.id}" in targets }.map { editJson.encodeToJsonElement(it).jsonObject })
        putRows("section", different(c.sections, unchangedFrom?.sections) { it.id }.filter { targets == null || "section:${it.id}" in targets }.map { editJson.encodeToJsonElement(it).jsonObject })
        putRows("medication", different(c.medications, unchangedFrom?.medications) { it.id }.filter { targets == null || "medication:${it.id}" in targets }.map { editJson.encodeToJsonElement(it).jsonObject })
        putRows("availability", different(c.availability, unchangedFrom?.availability) { it.medicationId to it.sectionId }.filter { targets == null || "availability:${it.medicationId}/${it.sectionId}" in targets }.map { editJson.encodeToJsonElement(it).jsonObject }) {
            it.getValue("medicationId").jsonPrimitive.content + "/" + it.getValue("sectionId").jsonPrimitive.content
        }
    }
    fun diff(before: CatalogSnapshot, after: CatalogSnapshot, author: EditorRef, at: Instant,
        baseId: String?, transaction: String = newId()): List<PendingOperation> {
        val a = rows(before, after); val b = rows(after, before)
        return (a.keys + b.keys).flatMap { key ->
            val old = a[key]; val next = b[key]
            val fields = if (old == null || next == null) setOf("@entity") else
                (old.keys + next.keys) - setOf("updatedAt", "updatedBy", "createdAt")
            fields.mapNotNull { field ->
                val x = if (field == "@entity") old ?: JsonNull else old?.get(field) ?: JsonNull
                val y = if (field == "@entity") next ?: JsonNull else next?.get(field) ?: JsonNull
                if (x == y) null else PendingOperation(newId(), transaction, key.substringBefore(':'),
                    key.substringAfter(':'), field, x.toString(), y.toString(), author.id, author.name, at, baseId)
            }
        }
    }
    fun merge(base: CatalogSnapshot, local: CatalogSnapshot, remote: CatalogSnapshot,
        operations: List<PendingOperation>, choices: Map<String, Boolean> = emptyMap()): MergeResult {
        operations.forEach { op ->
            fun uuid(value: String) { require(UUID.fromString(value).toString() == value) }
            uuid(op.id); uuid(op.transactionId); uuid(op.authorId)
            require(op.entityType in setOf("pharmacy", "category", "form", "section", "medication", "availability"))
            val targets = op.targetId.split('/')
            require(targets.size == if (op.entityType == "availability") 2 else 1)
            targets.forEach(::uuid)
        }
        val r = rows(remote).toMutableMap(); val l = rows(local); val conflicts = mutableListOf<EditConflict>()
        val remoteFingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(editJson.encodeToString(remote).toByteArray()).joinToString("") { "%02x".format(it) }
        val accepted = mutableListOf<PendingOperation>()
        for ((tx, ops) in operations.groupBy { it.transactionId }) {
            // Deletion affects linked records too: any changed remote state requires a fresh transaction review.
            val deletes = ops.any { it.field == "@entity" && it.intendedValueJson == "null" }
            if (deletes && rows(base) != rows(remote)) {
                val key = "delete:$tx:$remoteFingerprint"
                if (choices[key] == null) {
                    val before = rows(base); val after = rows(remote)
                    fun display(value: JsonElement?): String = when (value) {
                        null, JsonNull -> "غير موجود"
                        is JsonObject -> MedicationPresentation.recordIdentity(value, local.forms + remote.forms) ?: "عنصر مرتبط"
                        is JsonPrimitive -> value.content.take(100)
                        else -> "عناصر مرتبطة"
                    }
                    val changes = (before.keys + after.keys).flatMap { rowKey ->
                        val old = before[rowKey]; val next = after[rowKey]
                        val fields = if (old == null || next == null) setOf("@entity") else (old.keys + next.keys) - setOf("updatedAt", "updatedBy")
                        fields.mapNotNull { field ->
                            val a = if (field == "@entity") old else old?.get(field)
                            val b = if (field == "@entity") next else next?.get(field)
                            if (a == b) null else "${display(next ?: old)} · $field: ${display(a)} ← ${display(b)}"
                        }
                    }.joinToString("\n")
                    require(changes.length <= 20000) { "تتطلب مراجعة الحذف حجماً أكبر من واجهة المراجعة الحالية" }
                    conflicts += EditConflict(key, "حذف وعناصر مرتبطة", "مراجعة التغييرات", "تغيرت النسخة المشتركة منذ قاعدة التعديل",
                        "تأكيد الحذف وإعادة حساب العناصر المرتبطة", changes)
                    continue
                }
                if (choices[key] == false) continue
            }
            for (op in ops) {
                val rowKey = "${op.entityType}:${op.targetId}"
                val current = r[rowKey]
                val old = editJson.parseToJsonElement(op.baseValueJson)
                val intended = editJson.parseToJsonElement(op.intendedValueJson)
                val theirs = if (op.field == "@entity") current ?: JsonNull else current?.get(op.field) ?: JsonNull
                if (theirs == intended) continue
                val key = "${op.id}:$remoteFingerprint"
                val requiresChoice = !deletes && (theirs != old || (current == null && op.field != "@entity"))
                if (requiresChoice && choices[key] == null) {
                    conflicts += EditConflict(key, local.medications.find { it.id == op.targetId.substringBefore('/') }?.let { med ->
                        MedicationPresentation.identity(med, local.forms.find { it.id == med.formId }?.nameAr)
                    } ?: l[rowKey]?.let { MedicationPresentation.recordIdentity(it, local.forms) } ?: op.entityType,
                        op.field, old.toString(), intended.toString(), theirs.toString())
                    continue
                }
                if (requiresChoice && choices[key] == false) continue
                if (op.field == "@entity") {
                    if (intended == JsonNull) r.remove(rowKey) else r[rowKey] = intended.jsonObject
                } else {
                    val original = current ?: l[rowKey] ?: continue
                    r[rowKey] = JsonObject(original + (op.field to intended))
                }
                accepted += op
            }
        }
        // Derived metadata never decides business conflicts; use stable accepted-change attribution.
        accepted.groupBy { "${it.entityType}:${it.targetId}" }.forEach { (key, ops) ->
            r[key]?.takeIf { "updatedAt" in it }?.let { row ->
                val winner = ops.maxWith(compareBy({ it.authoredAt }, { it.authorId }, { it.id }))
                r[key] = JsonObject(row + mapOf("updatedAt" to JsonPrimitive(winner.authoredAt.toString()),
                    "updatedBy" to editJson.encodeToJsonElement(EditorRef(winner.authorId, winner.authorName))))
            }
        }
        // An availability edit cannot disappear because its medication or section was deleted.
        r.filterKeys { it.startsWith("availability:") }.toMap().forEach { (key, row) ->
            for ((field, type) in listOf("medicationId" to "medication", "sectionId" to "section")) {
                val parent = "$type:${row.getValue(field).jsonPrimitive.content}"
                if (parent !in r && parent in l) {
                    val choiceKey = "parent:$key:$parent:$remoteFingerprint"
                    when (choices[choiceKey]) {
                        true -> r[parent] = l.getValue(parent)
                        false -> r.remove(key)
                        null -> conflicts += EditConflict(choiceKey, "توفر مرتبط بعنصر محذوف", field,
                            "العنصر موجود محلياً", "استعادة العنصر المرتبط بعد المراجعة", "إلغاء تعديل التوفر")
                    }
                }
            }
        }
        r.filterKeys { it.startsWith("medication:") }.toMap().forEach { (key, row) ->
            for ((field, type, fallback) in listOf(Triple("categoryId", "category", CatalogSeeds.UNCATEGORIZED_ID),
                    Triple("formId", "form", CatalogSeeds.OTHER_FORM_ID))) {
                val id = row.getValue(field).jsonPrimitive.content
                if ("$type:$id" !in r && accepted.none { it.entityType == type && it.targetId == id && it.intendedValueJson == "null" }) {
                    val choiceKey = "reference:$key:$field:$remoteFingerprint"
                    when (choices[choiceKey]) {
                        true -> l["$type:$id"]?.let { r["$type:$id"] = it }
                        false -> r[key] = JsonObject(r.getValue(key) + (field to JsonPrimitive(fallback)))
                        null -> conflicts += EditConflict(choiceKey, "تصنيف أو شكل محذوف", field,
                            id, "استعادة التصنيف أو الشكل المحلي", "إعادة الإسناد إلى القيمة الافتراضية")
                    }
                }
            }
        }
        // Individually valid branches can violate collection constraints when combined.
        // Review only the colliding rows; unrelated taxonomy additions and edits survive.
        val remoteRows = rows(remote)
        for (type in listOf("category", "form", "section")) {
            val groups = r.filterKeys { it.startsWith("$type:") }.entries.groupBy {
                normalizedName(it.value.getValue("nameAr").jsonPrimitive.content)
            }.values.filter { it.size > 1 }
            for (group in groups) {
                val keys = group.map { it.key }.sorted()
                val choiceKey = "names:$type:${keys.joinToString(",")}:$remoteFingerprint"
                fun preferred(branch: Map<String, JsonObject>) = keys.firstOrNull { key ->
                    branch[key]?.get("nameAr") == r.getValue(key)["nameAr"]
                } ?: keys.first()
                val mine = preferred(l); val theirs = preferred(remoteRows)
                fun label(key: String) = "${r.getValue(key).getValue("nameAr").jsonPrimitive.content} (${key.substringAfter(':')})"
                val effect = if (type == "section") "يُحذف توفر القسم المستبعد؛ الاحتفاظ بـ: " else "تُعاد أدوية العنصر المستبعد إلى القيمة الافتراضية؛ الاحتفاظ بـ: "
                val selected = choices[choiceKey]?.let { if (it) mine else theirs }
                if (selected == null) {
                    conflicts += EditConflict(choiceKey, "أسماء متطابقة بعد الدمج", type,
                        "اختر عنصراً واحداً، أو أعد تسمية العنصر المحلي قبل النشر",
                        effect + label(mine), effect + label(theirs))
                } else keys.filter { it != selected }.forEach { r.remove(it) }
            }
        }
        val sectionKeys = r.keys.filter { it.startsWith("section:") }.sorted()
        if (sectionKeys.size !in 1..5) {
            val choiceKey = "sections:${sectionKeys.joinToString(",")}:$remoteFingerprint"
            fun retained(branch: Map<String, JsonObject>) = sectionKeys.filter { it in branch }
                .ifEmpty { branch.keys.filter { it.startsWith("section:") } }
            val mine = retained(l)
            val theirs = retained(remoteRows)
            fun labels(keys: List<String>, branch: Map<String, JsonObject>) =
                "يُحذف توفر الأقسام المستبعدة؛ الاحتفاظ بـ: " + keys.joinToString("، ") {
                    (r[it] ?: branch.getValue(it)).getValue("nameAr").jsonPrimitive.content
                }
            val selected = choices[choiceKey]?.let { if (it) mine else theirs }
            if (selected == null) conflicts += EditConflict(choiceKey, "عدد الأقسام بعد الدمج", "الأقسام",
                "يلزم قسم واحد إلى خمسة أقسام؛ يمكن تعديل الأقسام محلياً قبل النشر", labels(mine, l), labels(theirs, remoteRows))
            else {
                // A concurrent deletion of every section needs an explicit branch restoration.
                val branch = if (choices[choiceKey] == true) l else remoteRows
                val retained = selected.ifEmpty { branch.keys.filter { it.startsWith("section:") } }
                sectionKeys.filter { it !in retained }.forEach { r.remove(it) }
                retained.filter { it !in r }.forEach { r[it] = branch.getValue(it) }
            }
        }
        fun <T> decode(type: String, decode: (JsonElement) -> T) = r.filterKeys { it.startsWith("$type:") }.values.map(decode)
        val categories = decode("category") { editJson.decodeFromJsonElement<Category>(it) }
        val forms = decode("form") { editJson.decodeFromJsonElement<MedicationForm>(it) }
        val sections = decode("section") { editJson.decodeFromJsonElement<Section>(it) }
        val medications = decode("medication") { editJson.decodeFromJsonElement<Medication>(it) }.map {
            val category = if (categories.any { c -> c.id == it.categoryId }) it.categoryId else CatalogSeeds.UNCATEGORIZED_ID
            val form = if (forms.any { f -> f.id == it.formId }) it.formId else CatalogSeeds.OTHER_FORM_ID
            val deletion = accepted.filter { op -> op.field == "@entity" && op.intendedValueJson == "null" &&
                ((op.entityType == "category" && op.targetId == it.categoryId) || (op.entityType == "form" && op.targetId == it.formId)) }
                .maxWithOrNull(compareBy({ op -> op.authoredAt }, { op -> op.authorId }, { op -> op.id }))
            it.copy(categoryId = category, formId = form,
                updatedAt = deletion?.authoredAt ?: it.updatedAt,
                updatedBy = deletion?.let { op -> EditorRef(op.authorId, op.authorName) } ?: it.updatedBy)
        }
        val availability = decode("availability") { editJson.decodeFromJsonElement<Availability>(it) }
            .filter { a -> medications.any { it.id == a.medicationId } && sections.any { it.id == a.sectionId } }
        val tombstones = (remote.tombstones + local.tombstones.filter { t ->
            "${t.entityType}:${t.entityId}" !in r
        }).distinctBy { Triple(it.entityType, it.entityId, it.operationId) }
        val result = CatalogSnapshot(decode("pharmacy") { editJson.decodeFromJsonElement<Pharmacy>(it) }.single(),
            categories, forms, sections, medications, availability, tombstones)
        if (conflicts.isEmpty()) CatalogRules.validate(result)
        return MergeResult(result, conflicts)
    }
}
