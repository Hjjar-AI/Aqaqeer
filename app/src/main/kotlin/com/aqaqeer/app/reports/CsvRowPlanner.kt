package com.aqaqeer.app.reports

import com.aqaqeer.app.admin.CatalogEdits
import com.aqaqeer.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal val clearableCsvFields = listOf("nameAr", "nameEn", "genericAr", "genericEn", "brand", "note", "expiryDate")
internal object CsvRowPlanner {
    private val previewAuthor = EditorRef("00000000-0000-4000-8000-000000000099", "معاينة CSV")
    private fun candidate(id: String, baseline: CatalogSnapshot, table: CsvTable, record: CsvRecord, decision: CsvDecision): CsvRowPlan? =
        try { plan(id, baseline, table, record, decision.copy(action = decision.action ?: CsvAction.CREATE)) }
        catch (_: IllegalArgumentException) { null }
        catch (_: java.time.DateTimeException) { null }
        catch (_: CsvProblem) { null }

    private fun resolveSection(catalog: CatalogSnapshot, reference: String): Section? = catalog.sections.find { it.id == reference }
        ?: catalog.sections.filter {
            normalizedName(it.nameAr) == normalizedName(reference) || it.nameEn?.let(::normalizedName) == normalizedName(reference)
        }.singleOrNull()

    fun preview(id: String, baseline: CatalogSnapshot, source: CsvTable, decisions: Map<Int, CsvDecision>,
        previous: CsvImportSession? = null, changedRows: Set<Int>? = null, checkpoint: () -> Unit): CsvImportSession {
        // Bind aliases once to stable IDs. Renames/deletion must never retarget a confirmed row.
        val table = source.copy(sectionIds = source.sectionIds + source.headers.filter { it.startsWith("section:") && it !in source.sectionIds }
            .mapNotNull { header -> resolveSection(baseline, header.removePrefix("section:"))?.let { header to it.id } }.toMap())
        val index = previous?.duplicateIndex ?: MedicationDuplicates.index(baseline.medications)
        val candidates = if (previous != null && changedRows != null) previous.candidates.toMutableMap().also { cached ->
            table.records.filter { it.number in changedRows }.forEach { record ->
                checkpoint()
                cached[record.number] = candidate(id, baseline, table, record, decisions[record.number] ?: CsvDecision())
            }
        } else table.records.associate { record ->
            checkpoint()
            record.number to candidate(id, baseline, table, record, decisions[record.number] ?: CsvDecision())
        }
        val identities = if (previous != null && changedRows != null) previous.identityKeys.toMutableMap().also { keys ->
            changedRows.forEach { number -> keys[number] = candidates[number]?.medication?.let(MedicationDuplicates::keys).orEmpty() }
        } else candidates.mapValues { (_, value) -> value?.medication?.let(MedicationDuplicates::keys).orEmpty() }
        val identityRows = mutableMapOf<MedicationDuplicates.Key, MutableList<Int>>()
        identities.forEach { (number, keys) -> keys.forEach { identityRows.getOrPut(it) { mutableListOf() }.add(number) } }
        val repeatedTargets = decisions.values.filter { it.action == CsvAction.UPDATE && it.targetId != null }
            .groupingBy { it.targetId }.eachCount().filterValues { it > 1 }.keys
        val affected = changedRows?.toMutableSet()
        if (affected != null && previous != null) {
            val changedKeys = changedRows.flatMap { number -> identities[number].orEmpty() + previous.candidates[number]?.medication?.let(MedicationDuplicates::keys).orEmpty() }.toSet()
            val changedTargets = changedRows.flatMap { number -> listOfNotNull(decisions[number]?.targetId, previous.rows.find { it.record.number == number }?.decision?.targetId) }.toSet()
            val taxonomy = changedRows.flatMap { number -> listOfNotNull(candidates[number]?.category?.id, candidates[number]?.form?.id,
                previous.candidates[number]?.category?.id, previous.candidates[number]?.form?.id) }.toSet()
            table.records.forEach { record ->
                val number = record.number
                if (identities[number].orEmpty().any { it in changedKeys } || decisions[number]?.targetId in changedTargets ||
                    listOfNotNull(candidates[number]?.category?.id, candidates[number]?.form?.id).any { it in taxonomy }) affected.add(number)
            }
        }
        val oldRows = previous?.rows?.associateBy { it.record.number }.orEmpty()
        val rows = table.records.map { record ->
            checkpoint()
            if (affected != null && record.number !in affected) return@map oldRows.getValue(record.number)
            val initial = candidates[record.number]
            val duplicates = initial?.medication?.let { med -> MedicationDuplicates.keys(med).flatMap { index[it].orEmpty() }.distinct() }.orEmpty()
            val otherRows = identities[record.number].orEmpty().flatMap { identityRows[it].orEmpty() }.distinct().filter { it != record.number }
            val decision = decisions[record.number] ?: CsvDecision(action = if (duplicates.isEmpty() && otherRows.isEmpty()) CsvAction.CREATE else null)
            val warnings = buildList {
                if (duplicates.isNotEmpty()) add("يوجد دواء بالاسم والتركيز والشكل نفسه؛ اختر قراراً صريحاً")
                if (otherRows.isNotEmpty()) add("دواء مطابق داخل الملف في الصفوف: ${otherRows.joinToString()}")
            }
            if (decision.action == CsvAction.SKIP) CsvPreviewRow(record, decision, duplicates = duplicates, warnings = warnings, duplicateRows = otherRows)
            else try {
                if (decision.action == null) throw CsvProblem("اختر تخطّي أو إنشاء دواء آخر أو تحديث معرّف محدد")
                if (decision.action == CsvAction.CREATE && (duplicates.isNotEmpty() || otherRows.isNotEmpty()))
                    throw CsvProblem("يلزم اختيار إنشاء دواء آخر صراحة للتكرار")
                if (decision.action == CsvAction.UPDATE && decision.targetId in repeatedTargets) throw CsvProblem("أكثر من صف يحدث معرّف الدواء نفسه؛ اختر صفاً واحداً")
                val plan = plan(id, baseline, table, record, decision)
                val next = plan.apply(baseline, previewAuthor, Instant.EPOCH)
                CatalogRules.validate(next)
                val additions = buildList {
                    plan.category?.let { add("تصنيف مقترح: ${it.nameAr}") }
                    plan.form?.let { add("شكل دوائي مقترح: ${it.nameAr}") }
                }
                val changes = CatalogEdits.diff(baseline, next, previewAuthor, Instant.EPOCH, null).map { op ->
                    val field = if (op.entityType == "availability") "section:${op.targetId.substringAfter('/')}" else op.field
                    fun bounded(value: String) = historyPreview(value).let { it.text.orEmpty() + if (it.shortened) "… (مختصر)" else "" }
                    CsvFieldChange(field, bounded(op.baseValueJson), bounded(op.intendedValueJson))
                }
                CsvPreviewRow(record, decision, plan, changes, duplicates, warnings + additions, duplicateRows = otherRows)
            } catch (e: CsvProblem) { CsvPreviewRow(record, decision, duplicates = duplicates, warnings = warnings, error = e.reason, duplicateRows = otherRows) }
            catch (_: IllegalArgumentException) { CsvPreviewRow(record, decision, duplicates = duplicates, warnings = warnings, error = "قيم غير صالحة؛ تحقق من الحقول والمراجع", duplicateRows = otherRows) }
        }
        // Cache the effective decisions so generated create IDs stay stable across revisions.
        val effectiveCandidates = candidates.toMutableMap()
        rows.forEach { row ->
            effectiveCandidates[row.record.number] = row.plan ?: candidates[row.record.number]?.let { value ->
                value.copy(medication = value.medication.copy(id = if (row.decision.action == CsvAction.UPDATE) value.medication.id else row.decision.createdId))
            }
        }
        return CsvImportSession(id, requireNotNull(baseline.pharmacy).pharmacyId, baseline, table, rows, index, effectiveCandidates, identities)
    }

    fun plan(sessionId: String, catalog: CatalogSnapshot, table: CsvTable, record: CsvRecord, decision: CsvDecision): CsvRowPlan {
        if (record.malformed || record.cells.size != table.headers.size) throw CsvProblem("عدد خلايا الصف لا يطابق رأس CSV")
        val values = table.headers.zip(record.cells).toMap()
        if (decision.clear.any { it !in clearableCsvFields }) throw CsvProblem("لا يمكن مسح حقل مطلوب")
        val old = if (decision.action == CsvAction.UPDATE) catalog.medications.find { it.id == decision.targetId }
            ?: throw CsvProblem("اختر دواء موجوداً لتحديثه") else null
        fun value(key: String) = values[key]?.trim()?.takeIf(String::isNotEmpty)
        fun required(key: String, previous: String?): String = value(key) ?: previous ?: throw CsvProblem("الحقل مطلوب: $key")
        fun optional(key: String, previous: String?): String? = if (key in decision.clear) null else value(key) ?: previous
        fun stableId(type: String, name: String) = UUID.nameUUIDFromBytes("$sessionId/$type/${normalizedName(name)}".toByteArray(Charsets.UTF_8)).toString()
        val categoryName = value("categoryAr")
        val formName = value("formAr")
        val foundCategory = categoryName?.let { name -> catalog.categories.find { normalizedName(it.nameAr) == normalizedName(name) } }
        val foundForm = formName?.let { name -> catalog.forms.find { normalizedName(it.nameAr) == normalizedName(name) } }
        val category = if (categoryName != null && foundCategory == null) Category(stableId("category", categoryName), categoryName,
            orderIndex = (catalog.categories.maxOfOrNull { it.orderIndex } ?: 0) + 1) else null
        val form = if (formName != null && foundForm == null) MedicationForm(stableId("form", formName), formName,
            orderIndex = (catalog.forms.maxOfOrNull { it.orderIndex } ?: 0) + 1) else null
        fun <T> field(key: String, raw: String, parse: () -> T): T = try { parse() }
            catch (_: IllegalArgumentException) { throw CsvProblem("قيمة غير صالحة في $key: ${raw.take(80)}") }
            catch (_: java.time.DateTimeException) { throw CsvProblem("قيمة غير صالحة في $key: ${raw.take(80)}") }
        val expiry = optional("expiryDate", old?.expiryDate?.toString())?.let { raw -> field("expiryDate", raw) {
            val ascii = MedicationPresentation.westernDigits(raw)
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(ascii))
            LocalDate.parse(ascii).also { require(it.year in 1..9999) }
        } }
        val threshold = value("expirySoonThresholdDays")?.let { raw -> field("expirySoonThresholdDays", raw) {
            val ascii = MedicationPresentation.westernDigits(raw)
            require(Regex("[0-9]+").matches(ascii))
            ascii.toInt().also { require(it >= 0) }
        } } ?: old?.expirySoonThresholdDays ?: 60
        val resolved = mutableSetOf<String>()
        val explicitStatuses = values.filterKeys { it.startsWith("section:") }.mapNotNull { (header, raw) ->
            val reference = header.removePrefix("section:")
            val bound = table.sectionIds[header]
            val section = (if (bound != null) catalog.sections.find { it.id == bound } else resolveSection(catalog, reference))
                ?: throw CsvProblem("قسم غير معروف أو ملتبس أو محذوف: ${reference.take(80)}")
            if (!resolved.add(section.id)) throw CsvProblem("أعمدة متعددة للقسم نفسه: ${section.nameAr}")
            raw.trim().takeIf(String::isNotEmpty)?.let { value ->
                val status = AvailabilityStatus.entries.find { it.name.equals(value, ignoreCase = true) || MedicationPresentation.statusLabel(it) == value }
                    ?: throw CsvProblem("قيمة غير صالحة في ${header.take(80)}: ${value.take(80)}")
                section.id to status
            }
        }.toMap()
        val statuses = if (old == null) catalog.sections.associate { it.id to AvailabilityStatus.AVAILABLE } + explicitStatuses else explicitStatuses
        val medication = Medication(old?.id ?: decision.createdId, optional("nameEn", old?.nameEn), MedicationDuplicates.strength(required("strength", old?.strength)),
            foundForm?.id ?: form?.id ?: old?.formId ?: CatalogSeeds.defaultMedicationFormId(catalog.forms),
            foundCategory?.id ?: category?.id ?: old?.categoryId ?: CatalogSeeds.UNCATEGORIZED_ID,
            old?.createdAt ?: Instant.EPOCH, old?.updatedAt ?: Instant.EPOCH, old?.updatedBy ?: previewAuthor,
            nameAr = optional("nameAr", old?.nameAr), genericAr = optional("genericAr", old?.genericAr),
            genericEn = optional("genericEn", old?.genericEn), brand = optional("brand", old?.brand), note = optional("note", old?.note),
            expiryDate = expiry, expirySoonThresholdDays = threshold)
        if (medication.genericAr == null && medication.genericEn == null)
            throw CsvProblem("أدخل اسماً علمياً بالعربية أو الإنجليزية؛ لا يمكن مسح آخر اسم علمي")
        return CsvRowPlan(medication, category, form, statuses)
    }
}
