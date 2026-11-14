package com.aqaqeer.app.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.aqaqeer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

internal const val UNKNOWN_HISTORY_EDITOR = "unknown"
internal data class HistoryFilter(val from: LocalDate? = null, val through: LocalDate? = null,
    val editorId: String? = null, val medicationId: String? = null, val entityType: String? = null)
internal data class HistoryOption(val id: String, val name: String)
internal data class HistoryValue(val text: String?, val shortened: Boolean = false)
internal data class HistoryDisplayEntry(val id: String, val entityType: String, val entityId: String,
    val entityName: String, val field: String, val before: HistoryValue, val after: HistoryValue,
    val editorId: String?, val editorName: String?, val editedAt: Instant?, val observedAt: Instant?,
    val publishedRevision: Long?, val valuesHidden: Boolean)
internal data class HistoryPage(val entries: List<HistoryDisplayEntry>, val hasMore: Boolean,
    val medications: List<HistoryOption>, val editors: List<HistoryOption>, val timezone: String)

/** SQL character bounds count Unicode code points, not UTF-16 units. */
internal fun historyPreview(value: String?): HistoryValue {
    if (value == null) return HistoryValue(null)
    val shortened = value.codePointCount(0, value.length) > 512
    return HistoryValue(if (shortened) value.substring(0, value.offsetByCodePoints(0, 512)) else value, shortened)
}

internal fun CatalogSnapshot.historyName(type: String, id: String): String? = when (type) {
    "medication", "availability" -> medications.find { it.id == id.substringBefore('/') }?.let { MedicationPresentation.identity(it, forms.find { form -> form.id == it.formId }?.nameAr) }
    "category" -> categories.find { it.id == id }?.nameAr
    "form" -> forms.find { it.id == id }?.nameAr
    "section" -> sections.find { it.id == id }?.nameAr
    "pharmacy" -> pharmacy?.takeIf { it.pharmacyId == id }?.name
    else -> null
}

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryRepository @Inject constructor(private val factory: DatabaseFactory) {
    private val optionsLock = Any()
    private var optionsDatabase: AqaqeerDatabase? = null
    private var optionsObserver: androidx.room.InvalidationTracker.Observer? = null
    private var cachedOptions: Pair<List<HistoryOption>, List<HistoryOption>>? = null
    private val optionsRegistration = kotlinx.coroutines.sync.Mutex()
    private suspend fun observeOptions(database: AqaqeerDatabase) = optionsRegistration.withLock {
        val registration = synchronized(optionsLock) {
            if (optionsDatabase === database) null else {
                val previous = optionsDatabase to optionsObserver
                cachedOptions = null
                optionsDatabase = database
                val observer = object : androidx.room.InvalidationTracker.Observer("history", "medications", "forms", "pharmacy") {
                    override fun onInvalidated(tables: Set<String>) { synchronized(optionsLock) { cachedOptions = null } }
                }
                optionsObserver = observer
                Triple(previous.first, previous.second, observer)
            }
        }
        // Do not hold the option-cache lock while registering/removing Room observers.
        registration?.let { (previous, observer, next) ->
            if (observer != null) previous?.invalidationTracker?.removeObserver(observer)
            database.invalidationTracker.addObserver(next)
        }
    }
    private fun options(dao: CatalogDao): Pair<List<HistoryOption>, List<HistoryOption>> = synchronized(optionsLock) {
        cachedOptions ?: (medicationOptions(dao) to editorOptions(dao)).also { cachedOptions = it }
    }
    /** History viewing remains available to authorized admins under compatibility restrictions. */
    internal fun observe(pharmacyId: String, filter: HistoryFilter, page: Int): Flow<HistoryPage> = flow {
        require(page >= 0)
        require(filter.from == null || filter.from.year in 1..9999)
        require(filter.through == null || filter.through.year in 1..9999)
        require(filter.from == null || filter.through == null || filter.from <= filter.through)
        val lease = factory.acquire()
        val database = lease.database
        try {
            observeOptions(database)
            emitAll(database.invalidationTracker.createFlow("history", "medications", "forms", "pharmacy", "device_identity", "local_control")
                .mapLatest {
                    database.withTransaction {
                        val dao = database.catalogDao()
                        check(!BuildConfig.IS_DEMO && dao.deviceIdentity()?.adminMode == true)
                        val control = requireNotNull(dao.control())
                        check(!control.leaving && control.restoreActivationId == null)
                        val pharmacy = requireNotNull(dao.pharmacy())
                        check(pharmacy.pharmacyId == pharmacyId)
                        read(dao, filter, page, pharmacy.timezone)
                    }.also { currentCoroutineContext().ensureActive() }
                })
        } finally { lease.close() }
    }.flowOn(Dispatchers.IO)

    private fun read(dao: CatalogDao, filter: HistoryFilter, page: Int, timezone: String): HistoryPage {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        val zone = ZoneId.of(timezone)
        filter.from?.let { where += "COALESCE(h.editedAt,h.observedAt) >= ?"; args += it.atStartOfDay(zone).toInstant().toEpochMilli() }
        filter.through?.let { where += "COALESCE(h.editedAt,h.observedAt) < ?"; args += it.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
        filter.editorId?.let {
            if (it == UNKNOWN_HISTORY_EDITOR) where += "h.editorId IS NULL"
            else { where += "h.editorId = ?"; args += it }
        }
        filter.medicationId?.let {
            where += "((h.entityType='medication' AND h.entityId=?) OR (h.entityType='availability' AND substr(h.entityId,1,instr(h.entityId,'/')-1)=?))"
            args += it; args += it
        }
        filter.entityType?.let {
            when (it) {
                "taxonomy" -> where += "h.entityType IN ('category','form','section')"
                "availability", "medication", "pharmacy", "snapshot", "maintenance" -> { where += "h.entityType=?"; args += it }
                else -> throw IllegalArgumentException("Unknown history entity type")
            }
        }
        // Never return verifier material, pharmacy record bodies, or snapshot/recovery JSON.
        val hidden = "(h.entityType IN ('snapshot','maintenance') OR h.field='adminPinVerifier' OR (h.entityType='pharmacy' AND h.field IN ('@entity','record')))"
        val sql = """SELECT h.id,h.entityType,h.entityId,substr(h.entityName,1,513),h.field,
            CASE WHEN $hidden THEN NULL ELSE substr(h.oldValue,1,513) END,
            CASE WHEN $hidden THEN NULL ELSE substr(h.newValue,1,513) END,
            h.editorId,substr(h.editorName,1,513),h.editedAt,h.observedAt,h.publishedRevision,$hidden
            FROM history h ${if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")}
            ORDER BY COALESCE(h.editedAt,h.observedAt,-9223372036854775808) DESC,h.id DESC LIMIT 51 OFFSET ?"""
        args += page.toLong() * 50
        val entries = dao.backupCursor(SimpleSQLiteQuery(sql, args.toTypedArray())).use { cursor ->
            buildList {
                fun string(index: Int): String? = if (cursor.isNull(index)) null else cursor.getString(index)
                fun instant(index: Int): Instant? = if (cursor.isNull(index)) null else Instant.ofEpochMilli(cursor.getLong(index))
                while (cursor.moveToNext()) add(HistoryDisplayEntry(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    historyPreview(string(3)).let { it.text.orEmpty() + if (it.shortened) "…" else "" }, cursor.getString(4),
                    historyPreview(string(5)), historyPreview(string(6)), string(7), historyPreview(string(8)).text,
                    instant(9), instant(10), if (cursor.isNull(11)) null else cursor.getLong(11), cursor.getInt(12) != 0))
            }
        }
        val (medications, editors) = options(dao)
        return HistoryPage(entries.take(50), entries.size > 50, medications, editors, timezone)
    }

    private fun medicationOptions(dao: CatalogDao): List<HistoryOption> {
        val result = linkedMapOf<String, String>()
        val sql = """WITH medication_history AS (
            SELECT CASE WHEN entityType='availability' THEN substr(entityId,1,instr(entityId,'/')-1) ELSE entityId END AS medicationId,
                substr(entityName,1,513) AS entityName,COALESCE(editedAt,observedAt,-9223372036854775808) AS eventAt,id
            FROM history WHERE entityType IN ('medication','availability'))
            SELECT h.medicationId,(SELECT substr(n.entityName,1,513) FROM medication_history n
                WHERE n.medicationId=h.medicationId AND n.entityName NOT IN ('medication','availability',n.medicationId) AND n.entityName<>''
                ORDER BY n.eventAt DESC,n.id DESC LIMIT 1)
            FROM medication_history h GROUP BY h.medicationId"""
        dao.backupCursor(SimpleSQLiteQuery(sql)).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                result[id] = if (cursor.isNull(1)) "معرّف دواء: $id" else historyPreview(cursor.getString(1)).text.orEmpty()
            }
        }
        dao.backupCursor(SimpleSQLiteQuery("SELECT m.id,substr(COALESCE(m.genericAr,m.genericEn,m.nameAr,m.nameEn,'') || ' · ' || m.strength || ' · ' || COALESCE(f.nameAr,''),1,513) FROM medications m LEFT JOIN forms f ON f.id=m.formId")).use { cursor ->
            while (cursor.moveToNext()) result[cursor.getString(0)] = historyPreview(cursor.getString(1)).text.orEmpty()
        }
        return result.map { HistoryOption(it.key, it.value) }.sortedWith(compareBy({ it.name }, { it.id }))
    }

    private fun editorOptions(dao: CatalogDao): List<HistoryOption> {
        val sql = """SELECT h.editorId,(SELECT substr(n.editorName,1,513) FROM history n WHERE n.editorId=h.editorId AND n.editorName IS NOT NULL
            ORDER BY COALESCE(n.editedAt,n.observedAt,-9223372036854775808) DESC,n.id DESC LIMIT 1)
            FROM history h GROUP BY h.editorId"""
        return dao.backupCursor(SimpleSQLiteQuery(sql)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = if (cursor.isNull(0)) UNKNOWN_HISTORY_EDITOR else cursor.getString(0)
                    val name = if (id == UNKNOWN_HISTORY_EDITOR) "محرر غير معروف"
                        else if (cursor.isNull(1)) "معرّف محرر: $id" else historyPreview(cursor.getString(1)).text.orEmpty()
                    add(HistoryOption(id, name))
                }
            }.sortedWith(compareBy({ it.name }, { it.id }))
        }
    }
}
