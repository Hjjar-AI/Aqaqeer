package com.aqaqeer.app.admin

import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal data class DemoSessionState(val initial: CatalogSnapshot, val catalog: CatalogSnapshot,
    val admin: Boolean = false, val pending: List<PendingOperation> = emptyList(), val history: List<HistoryEntry> = emptyList())

/** Process-local demo workspace. Never authorizes a database identity or writes shared/installed data. */
internal object DemoSession {
    private val mutable = MutableStateFlow<DemoSessionState?>(null)
    val state = mutable.asStateFlow()
    private val identity = UUID.randomUUID().toString()

    @Synchronized fun initialize(): DemoSessionState {
        check(BuildConfig.IS_DEMO)
        return mutable.value ?: BuildSeed.catalog().let { DemoSessionState(it, it).also { next -> mutable.value = next } }
    }
    @Synchronized fun setAdmin(value: Boolean) {
        val current = initialize()
        mutable.value = current.copy(admin = value)
    }
    @Synchronized fun reset() {
        val current = initialize()
        val catalog = BuildSeed.catalog()
        mutable.value = DemoSessionState(catalog, catalog, admin = current.admin)
    }
    @Synchronized fun edit(change: (CatalogSnapshot, DeviceIdentity, Instant, String) -> CatalogSnapshot) {
        val current = initialize()
        adminCheck(current.admin)
        val now = Instant.now()
        val tx = UUID.randomUUID().toString()
        val next = change(current.catalog, DeviceIdentity(identity, true, now, now), now, tx)
        // Demo catalog editing cannot change PINs, pharmacy identity or connection metadata.
        adminCheck(next.pharmacy == current.catalog.pharmacy)
        CatalogRules.validate(next)
        val changes = CatalogEdits.diff(current.catalog, next, now, null, tx)
        if (changes.isEmpty()) return
        val history = changes.map { op -> HistoryEntry(UUID.randomUUID().toString(), op.id, op.entityType, op.targetId,
            next.historyName(op.entityType, op.targetId) ?: current.catalog.historyName(op.entityType, op.targetId) ?: op.entityType,
            op.field, op.baseValueJson, op.intendedValueJson, now, null, null) }
        mutable.value = current.copy(catalog = next,
            pending = CatalogEdits.diff(current.initial, next, now, null, tx), history = current.history + history)
    }
    fun ready(source: CatalogState.Ready, session: DemoSessionState): CatalogState.Ready {
        check(BuildConfig.IS_DEMO && source.isDemo)
        return source.copy(catalog = session.catalog, admin = session.admin, pendingCount = if (session.admin) session.pending.size else 0,
            unpublishedMedicationIds = if (session.admin) session.pending.filter { it.entityType in setOf("medication", "availability") }
                .map { it.targetId.substringBefore('/') }.toSet() else emptySet(),
            hasPublicationConnection = false, hasBackup = false, publishedAt = null, lastSuccessfulRefreshAt = null)
    }
    fun historyPage(session: DemoSessionState, pharmacyId: String, filter: HistoryFilter, page: Int): HistoryPage {
        check(BuildConfig.IS_DEMO && session.admin && session.catalog.pharmacy?.pharmacyId == pharmacyId)
        require(page >= 0)
        val timezone = requireNotNull(session.catalog.pharmacy).timezone
        val zone = ZoneId.of(timezone)
        val filtered = session.history.asReversed().filter { entry ->
            val date = entry.editedAt?.atZone(zone)?.toLocalDate()
            (filter.from == null || date != null && date >= filter.from) &&
                (filter.through == null || date != null && date <= filter.through) &&
                (filter.medicationId == null || entry.entityType in setOf("medication", "availability") && entry.entityId.substringBefore('/') == filter.medicationId) &&
                (filter.entityType == null || if (filter.entityType == "taxonomy") entry.entityType in setOf("category", "form", "section") else entry.entityType == filter.entityType)
        }
        val offset = (page.toLong() * 50).coerceAtMost(filtered.size.toLong()).toInt()
        val entries = filtered.drop(offset).take(50).map { entry ->
            HistoryDisplayEntry(entry.id, entry.entityType, entry.entityId, entry.entityName, entry.field,
                historyPreview(entry.oldValue), historyPreview(entry.newValue), entry.editedAt, entry.observedAt, null, false)
        }
        val options = session.catalog.medications.associate { it.id to MedicationPresentation.identity(it, session.catalog.formsById[it.formId]?.nameAr) }.toMutableMap()
        session.history.filter { it.entityType in setOf("medication", "availability") }.forEach { options.putIfAbsent(it.entityId.substringBefore('/'), it.entityName) }
        return HistoryPage(entries, offset.toLong() + 50 < filtered.size, options.map { HistoryOption(it.key, it.value) }.sortedBy { normalizedName(it.name) }, timezone)
    }
}
