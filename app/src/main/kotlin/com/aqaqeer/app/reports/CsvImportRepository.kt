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
        CsvRowPlanner.preview(session.id, session.baseline, session.table, choices) { jobContext.ensureActive() }
    }
    internal suspend fun selectValid(session: CsvImportSession): CsvImportSession = session.copy(rows = session.rows.map { row ->
        row.copy(decision = row.decision.copy(selected = row.error == null && row.plan != null &&
            ((row.plan.category == null && row.plan.form == null) || row.decision.approveTaxonomy)))
    })

    /** Each short non-cancellable row records its result before cancellation can advance the loop. */
    internal suspend fun commit(session: CsvImportSession, result: suspend (CsvRowResult) -> Unit) {
        val duplicateTargets = session.rows.filter { it.decision.action == CsvAction.UPDATE && it.decision.selected }
            .groupingBy { it.decision.targetId }.eachCount().filterValues { it > 1 }.keys
        val sessionCreatedIds = session.rows.map { it.decision.createdId }.toSet()
        for (row in session.rows) {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val warning = row.warnings.joinToString("؛ ")
                val outcome = when {
                    row.decision.action == CsvAction.SKIP -> CsvRowResult(row.record.number, CsvOutcome.SKIPPED, warning)
                    row.error != null -> CsvRowResult(row.record.number, CsvOutcome.FAILED, warning, row.error)
                    !row.decision.selected -> CsvRowResult(row.record.number, CsvOutcome.SKIPPED, warning)
                    else -> try {
                        if (row.decision.targetId in duplicateTargets) throw CsvProblem("معرّف تحديث مكرر في الجلسة")
                        var changed = false
                        admin.edit { current, author, now, _ ->
                            check(current.pharmacy?.pharmacyId == session.pharmacyId)
                            val old = session.baseline.medications.find { it.id == row.decision.targetId }
                            if (row.decision.action == CsvAction.UPDATE && current.medications.find { it.id == row.decision.targetId } != old)
                                throw CsvProblem("تغير الدواء منذ المعاينة؛ افتح الملف لمعاينة جديدة")
                            if (row.decision.action != CsvAction.UPDATE && current.medications.any { it.id == row.decision.createdId })
                                throw CsvProblem("هذا الصف محفوظ بالفعل؛ لا تعِد تشغيله تلقائياً")
                            val plan = CsvRowPlanner.plan(session.id, current, session.table, row.record, row.decision)
                            if ((plan.category != null || plan.form != null) && !row.decision.approveTaxonomy)
                                throw CsvProblem("أكد إضافة التصنيف أو الشكل المقترح أولاً")
                            // Availability changes cannot overwrite a value changed since preview.
                            plan.statuses.keys.forEach { section ->
                                if (current.status(plan.medication.id, section) != session.baseline.status(plan.medication.id, section))
                                    throw CsvProblem("تغير التوافر منذ المعاينة؛ يلزم معاينة جديدة")
                            }
                            val duplicateNames = current.medications.filter { it.id != plan.medication.id &&
                                CsvRowPlanner.names(it).intersect(CsvRowPlanner.names(plan.medication)).isNotEmpty() }.map { it.id }.toSet()
                            val expectedDuplicates = session.baseline.medications.filter { it.id != plan.medication.id &&
                                CsvRowPlanner.names(it).intersect(CsvRowPlanner.names(plan.medication)).isNotEmpty() }.map { it.id }.toSet()
                            if ((duplicateNames - sessionCreatedIds) != expectedDuplicates)
                                throw CsvProblem("تغيرت الأسماء المكررة منذ المعاينة؛ يلزم معاينة جديدة")
                            if (row.decision.action == CsvAction.CREATE && duplicateNames.isNotEmpty())
                                throw CsvProblem("الأسماء مكررة؛ اختر إنشاء دواء آخر صراحة")
                            val next = plan.apply(current, author, now)
                            changed = CatalogEdits.diff(current, next, author, now, null).isNotEmpty()
                            next
                        }
                        CsvRowResult(row.record.number, if (!changed) CsvOutcome.UNCHANGED else if (row.decision.action == CsvAction.UPDATE)
                            CsvOutcome.UPDATED else CsvOutcome.CREATED, warning)
                    } catch (e: CsvProblem) { CsvRowResult(row.record.number, CsvOutcome.FAILED, warning, e.reason) }
                    catch (_: Exception) { CsvRowResult(row.record.number, CsvOutcome.FAILED, warning,
                        "تعذر حفظ الصف؛ تحقق من التفويض والتوافق والمراجع والحقول. لم يُحفظ جزء من هذا الصف") }
                }
                result(outcome)
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
