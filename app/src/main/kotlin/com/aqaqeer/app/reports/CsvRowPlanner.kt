package com.aqaqeer.app.reports

import com.aqaqeer.app.admin.CatalogEdits
import com.aqaqeer.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal val clearableCsvFields = listOf("nameAr", "nameEn", "genericAr", "genericEn", "brand", "note", "expiryDate")
internal object CsvRowPlanner {
    private val previewAuthor = EditorRef("00000000-0000-4000-8000-000000000099", "معاينة CSV")
    fun names(medication: Medication) = listOfNotNull(medication.genericAr, medication.genericEn, medication.nameAr, medication.nameEn).map(::normalizedName).toSet()
    private fun rawNames(table: CsvTable, record: CsvRecord): Set<String> = listOf("genericAr", "genericEn", "nameAr", "nameEn").mapNotNull { key ->
        record.cells.getOrNull(table.headers.indexOf(key))?.trim()?.takeIf(String::isNotEmpty)?.let(::normalizedName)
    }.toSet()

    fun preview(id: String, baseline: CatalogSnapshot, table: CsvTable, decisions: Map<Int, CsvDecision>, checkpoint: () -> Unit): CsvImportSession {
        val inputNames = table.records.associate { it.number to rawNames(table, it) }
        val nameRows = mutableMapOf<String, MutableList<Int>>()
        inputNames.forEach { (number, names) -> names.forEach { nameRows.getOrPut(it) { mutableListOf() }.add(number) } }
        val repeatedTargets = decisions.values.filter { it.action == CsvAction.UPDATE && it.targetId != null }
            .groupingBy { it.targetId }.eachCount().filterValues { it > 1 }.keys
        val rows = table.records.map { record ->
            checkpoint()
            val duplicates = baseline.medications.filter { names(it).intersect(inputNames.getValue(record.number)).isNotEmpty() }.map { it.id }
            val otherRows = inputNames.getValue(record.number).flatMap { nameRows[it].orEmpty() }.distinct().filter { it != record.number }
            val decision = decisions[record.number] ?: CsvDecision(action = if (duplicates.isEmpty() && otherRows.isEmpty()) CsvAction.CREATE else null)
            val warnings = buildList {
                if (duplicates.isNotEmpty()) add("توجد أسماء مطابقة بعد التطبيع؛ اختر قراراً صريحاً")
                if (otherRows.isNotEmpty()) add("أسماء مطابقة داخل الملف في الصفوف: ${otherRows.joinToString()}")
            }
            if (decision.action == CsvAction.SKIP) CsvPreviewRow(record, decision, duplicates = duplicates, warnings = warnings)
            else try {
                if (decision.action == null) throw CsvProblem("اختر تخطّي أو إنشاء دواء آخر أو تحديث معرّف محدد")
                if (decision.action == CsvAction.CREATE && (duplicates.isNotEmpty() || otherRows.isNotEmpty()))
                    throw CsvProblem("يلزم اختيار إنشاء دواء آخر صراحة للأسماء المكررة")
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
                val finalDuplicates = baseline.medications.filter { it.id != plan.medication.id &&
                    names(it).intersect(names(plan.medication)).isNotEmpty() }.map { it.id }
                val duplicateWarning = if (finalDuplicates.isNotEmpty() && duplicates.isEmpty()) listOf("الدواء الناتج له أسماء مطابقة في الكتالوج") else emptyList()
                CsvPreviewRow(record, decision, plan, changes, finalDuplicates, warnings + additions + duplicateWarning)
            } catch (e: CsvProblem) { CsvPreviewRow(record, decision, duplicates = duplicates, warnings = warnings, error = e.reason) }
            catch (_: Exception) { CsvPreviewRow(record, decision, duplicates = duplicates, warnings = warnings, error = "قيم غير صالحة؛ تحقق من الحقول والمراجع والتاريخ") }
        }
        return CsvImportSession(id, requireNotNull(baseline.pharmacy).pharmacyId, baseline, table, rows)
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
        val expiry = optional("expiryDate", old?.expiryDate?.toString())?.let {
            val ascii = MedicationPresentation.westernDigits(it)
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(ascii))
            LocalDate.parse(ascii).also { date -> require(date.year in 1..9999) }
        }
        val threshold = value("expirySoonThresholdDays")?.let {
            val ascii = MedicationPresentation.westernDigits(it); require(Regex("[0-9]+").matches(ascii)); ascii.toInt()
        } ?: old?.expirySoonThresholdDays ?: 60
        val explicitStatuses = values.filterKeys { it.startsWith("section:") }.mapNotNull { (header, raw) ->
            val id = header.removePrefix("section:")
            if (catalog.sections.none { it.id == id }) throw CsvProblem("قسم غير معروف: $id")
            raw.trim().takeIf(String::isNotEmpty)?.let { id to AvailabilityStatus.valueOf(it) }
        }.toMap()
        val statuses = if (old == null) catalog.sections.associate { it.id to AvailabilityStatus.AVAILABLE } + explicitStatuses else explicitStatuses
        val medication = Medication(old?.id ?: decision.createdId, optional("nameEn", old?.nameEn), required("strength", old?.strength),
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
