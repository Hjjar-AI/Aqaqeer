package com.aqaqeer.app.reports

import android.content.Context
import android.net.Uri
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.UUID
import javax.inject.Inject

class CsvImportRepository @Inject constructor(private val admin: AdminRepository, private val output: DocumentOutput,
    @param:ApplicationContext private val context: Context) {
    private suspend fun catalog() = admin.transaction { dao -> admin.guard(dao); dao.catalog() }
    internal suspend fun load(uri: Uri): CsvImportSession = withContext(Dispatchers.IO) {
        catalog() // Reject readers/unsupported installations before opening an external file.
        require(uri.scheme == "content")
        val jobContext = currentCoroutineContext()
        val table = requireNotNull(context.contentResolver.openInputStream(uri)).use { CsvCodec.read(it) { jobContext.ensureActive() } }
        val baseline = catalog()
        CsvRowPlanner.preview(UUID.randomUUID().toString(), baseline, table, emptyMap()) { jobContext.ensureActive() }
    }
    internal suspend fun revise(session: CsvImportSession, number: Int, decision: CsvDecision): CsvImportSession = withContext(Dispatchers.Default) {
        val choices = session.rows.associate { it.record.number to it.decision }.toMutableMap()
        choices[number] = decision
        val jobContext = currentCoroutineContext()
        CsvRowPlanner.preview(session.id, session.baseline, session.table, choices, { jobContext.ensureActive() }, session, setOf(number))
    }
    internal suspend fun selectValid(session: CsvImportSession): CsvImportSession = session.copy(rows = session.rows.map { row ->
        row.copy(decision = row.decision.copy(selected = row.error == null && row.plan != null &&
            ((row.plan.category == null && row.plan.form == null) || row.decision.approveTaxonomy)))
    })

    internal suspend fun chooseDuplicates(session: CsvImportSession, update: Boolean): CsvImportSession = withContext(Dispatchers.Default) {
        val choices = session.rows.associate { row -> row.record.number to if (row.duplicates.isEmpty()) row.decision
            else if (!update) row.decision.copy(action = CsvAction.SKIP, selected = false)
            else if (row.duplicates.size == 1) row.decision.copy(action = CsvAction.UPDATE, targetId = row.duplicates.single(), selected = false)
            else row.decision }
        val owner = currentCoroutineContext()
        CsvRowPlanner.preview(session.id, session.baseline, session.table, choices) { owner.ensureActive() }
    }

    /** A chunk commits once; only then can its results become successes. No write is auto-replayed. */
    internal suspend fun commit(session: CsvImportSession, result: suspend (List<CsvRowResult>) -> Unit) {
        val duplicateTargets = session.rows.filter { it.decision.action == CsvAction.UPDATE && it.decision.selected }
            .groupingBy { it.decision.targetId }.eachCount().filterValues { it > 1 }.keys
        val sessionCreatedIds = session.rows.map { it.decision.createdId }.toSet()
        for (chunk in session.rows.chunked(50)) {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val outcomes = mutableListOf<CsvRowResult>()
                try {
                    admin.transaction { dao ->
                        val device = admin.guard(dao)
                        val author = EditorRef(device.id, requireNotNull(device.displayName))
                        var staged = dao.catalog()
                        val plans = mutableListOf<CsvRowPlan>()
                        val baselineIndex = session.duplicateIndex
                        for (row in chunk) {
                            val warning = row.warnings.joinToString("؛ ")
                            fun outcome(kind: CsvOutcome, error: String = "") = CsvRowResult(row.record.number, kind, warning, error)
                            if (row.decision.action == CsvAction.SKIP || (!row.decision.selected && row.error == null)) {
                                outcomes += outcome(CsvOutcome.SKIPPED); continue
                            }
                            if (row.error != null) { outcomes += outcome(CsvOutcome.FAILED, row.error); continue }
                            try {
                                if (staged.pharmacy?.pharmacyId != session.pharmacyId) throw CsvProblem("تغيرت الصيدلية؛ أعد المعاينة")
                                if (row.decision.targetId in duplicateTargets) throw CsvProblem("معرّف تحديث مكرر في الجلسة")
                                val old = session.baseline.medications.find { it.id == row.decision.targetId }
                                if (row.decision.action == CsvAction.UPDATE && staged.medications.find { it.id == row.decision.targetId } != old)
                                    throw CsvProblem("تغير الدواء منذ المعاينة؛ افتح الملف لمعاينة جديدة")
                                if (row.decision.action != CsvAction.UPDATE && staged.medications.any { it.id == row.decision.createdId })
                                    throw CsvProblem("هذا الصف محفوظ بالفعل؛ لا تعِد تشغيله تلقائياً")
                                val plan = CsvRowPlanner.plan(session.id, staged, session.table, row.record, row.decision)
                                if ((plan.category != null || plan.form != null) && !row.decision.approveTaxonomy)
                                    throw CsvProblem("أكد إضافة التصنيف أو الشكل المقترح أولاً")
                                plan.statuses.keys.forEach { section ->
                                    if (staged.status(plan.medication.id, section) != session.baseline.status(plan.medication.id, section))
                                        throw CsvProblem("تغير التوافر منذ المعاينة؛ يلزم معاينة جديدة")
                                }
                                val duplicates = MedicationDuplicates.find(plan.medication, MedicationDuplicates.index(staged.medications)).toSet()
                                val expected = MedicationDuplicates.find(plan.medication, baselineIndex).toSet()
                                if ((duplicates - sessionCreatedIds) != expected) throw CsvProblem("تغير التكرار منذ المعاينة؛ يلزم معاينة جديدة")
                                if (row.decision.action == CsvAction.CREATE && duplicates.isNotEmpty()) throw CsvProblem("الدواء مكرر؛ اختر إنشاء دواء آخر صراحة")
                                val next = plan.apply(staged, author, java.time.Instant.EPOCH)
                                CatalogRules.validate(next)
                                val changed = CatalogEdits.diff(staged, next, author, java.time.Instant.EPOCH, null).isNotEmpty()
                                staged = next
                                plans += plan
                                outcomes += outcome(if (!changed) CsvOutcome.UNCHANGED else if (row.decision.action == CsvAction.UPDATE) CsvOutcome.UPDATED else CsvOutcome.CREATED)
                            } catch (e: CsvProblem) { outcomes += outcome(CsvOutcome.FAILED, e.reason) }
                            catch (_: IllegalArgumentException) { outcomes += outcome(CsvOutcome.FAILED, "قيم غير صالحة؛ تحقق من الحقول والمراجع") }
                        }
                        // All row-level validation finishes before the first write. A storage error rolls back the chunk.
                        plans.forEach { plan -> admin.editInTransaction(dao) { current, editor, now, _ -> plan.apply(current, editor, now) } }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    val existing = outcomes.associateBy { it.number }
                    outcomes.clear()
                    outcomes += chunk.map { row ->
                        val prior = existing[row.record.number]
                        if (prior?.outcome in setOf(CsvOutcome.SKIPPED, CsvOutcome.FAILED)) requireNotNull(prior)
                        else CsvRowResult(row.record.number, CsvOutcome.FAILED, row.warnings.joinToString("؛ "),
                            "تعذر حفظ الدفعة؛ لم تُعتمد نتائج نجاح. أعد المعاينة قبل المحاولة")
                    }
                }
                result(outcomes)
            }
        }
    }

    internal suspend fun template(uri: Uri) {
        val c = catalog()
        val headers = visibleMedicationColumns + c.sections.map { "section:${it.id}" }
        val jobContext = currentCoroutineContext()
        output.write(uri) { CsvCodec.write(it, sequenceOf(headers), false) { jobContext.ensureActive() } }
    }
    internal suspend fun exportResults(uri: Uri, state: CsvImportState, retry: Boolean) {
        val c = catalog(); val session = requireNotNull(state.session)
        check(c.pharmacy?.pharmacyId == session.pharmacyId)
        check(state.finished)
        val rows = session.rows.filter { !retry || state.results[it.record.number]?.outcome == CsvOutcome.FAILED }
        val jobContext = currentCoroutineContext()
        output.write(uri) { stream ->
            val data = sequence {
                yield(session.table.headers + resultColumns)
                for (row in rows) {
                    val result = requireNotNull(state.results[row.record.number])
                    if (retry) yield(row.record.cells + listOf(result.number.toString(), result.outcome.name, result.warning, result.error))
                    else {
                        val original = row.record.cells.take(session.table.headers.size) +
                            List((session.table.headers.size - row.record.cells.size).coerceAtLeast(0)) { "" }
                        val extra = row.record.cells.drop(session.table.headers.size)
                        val error = result.error + if (extra.isEmpty()) "" else "؛ خلايا إضافية في المصدر: " + extra.joinToString(" | ")
                        yield(original + listOf(result.number.toString(), result.outcome.name, result.warning, error))
                    }
                }
            }
            CsvCodec.write(stream, data, spreadsheetSafe = !retry) { jobContext.ensureActive() }
        }
    }
}
