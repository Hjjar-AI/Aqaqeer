package com.aqaqeer.app.admin

import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal data class DemoSessionState(val initial: CatalogSnapshot, val catalog: CatalogSnapshot,
    val workspaceId: String = UUID.randomUUID().toString(),
    val restorePaused: Boolean = false, val keyVersion: Int = 1, val admin: Boolean = false, val connected: Boolean = true, val online: Boolean = true,
    val publishedAt: Instant = Instant.now(), val refreshedAt: Instant = Instant.now(),
    val revision: Long = 1, val reconnectQueued: Boolean = false, val pending: List<PendingOperation> = emptyList(), val history: List<HistoryEntry> = emptyList())

/** Process-local demo workspace. Never authorizes a database identity or writes shared/installed data. */
internal object DemoSession {
    private val mutable = MutableStateFlow<DemoSessionState?>(null)
    val state = mutable.asStateFlow()
    private var pinDigits = "1234"
    private var batchState: DemoSessionState? = null
    private val identity = UUID.randomUUID().toString()

    @Synchronized fun initialize(): DemoSessionState {
        check(BuildConfig.IS_DEMO)
        return mutable.value ?: BuildSeed.catalog().let { DemoSessionState(it, it).also { next -> mutable.value = next } }
    }
    @Synchronized fun setAdmin(value: Boolean) {
        val current = initialize()
        mutable.value = current.copy(admin = value)
    }
    @Synchronized fun authorized(): DemoSessionState = (batchState ?: initialize()).also { adminCheck(it.admin) }
    @Synchronized fun promote(pin: CharArray) {
        val digits = PinSecurity.digits(pin)
        try { adminCheck(String(digits) == pinDigits) { "رمز الإدارة التجريبي غير صحيح" }; setAdmin(true) }
        finally { digits.fill('\u0000'); pin.fill('\u0000') }
    }
    @Synchronized fun changePin(currentPin: CharArray, newPin: CharArray, confirmation: CharArray) {
        authorized()
        val current = PinSecurity.digits(currentPin); val next = PinSecurity.digits(newPin); val confirm = PinSecurity.digits(confirmation)
        try {
            adminCheck(String(current) == pinDigits) { "رمز الإدارة التجريبي غير صحيح" }
            adminRequire(next.size >= 4 && next.contentEquals(confirm)) { "تحقق من الرمز الجديد وتأكيده" }
            pinDigits = String(next)
        } finally { current.fill('\u0000'); next.fill('\u0000'); confirm.fill('\u0000') }
    }
    @Synchronized fun replaceConnection(expectedWorkspace: String, expectedKeyVersion: Int) {
        val current = initialize()
        adminCheck(current.workspaceId == expectedWorkspace && current.keyVersion == expectedKeyVersion)
        mutable.value = current.copy(connected = true)
    }
    @Synchronized fun resumeRestore() { mutable.value = authorized().copy(restorePaused = false) }
    @Synchronized fun rotateConnection() { val current = authorized(); mutable.value = current.copy(connected = true, keyVersion = current.keyVersion + 1) }
    @Synchronized fun restore(catalog: CatalogSnapshot, confirmed: CatalogSnapshot, history: List<HistoryEntry>, expectedWorkspace: String, publishedAt: Instant, revision: Long) {
        val current = authorized()
        adminCheck(current.workspaceId == expectedWorkspace)
        CatalogRules.validate(catalog); CatalogRules.validate(confirmed)
        adminCheck(catalog.pharmacy?.pharmacyId == current.catalog.pharmacy?.pharmacyId && confirmed.pharmacy?.pharmacyId == catalog.pharmacy?.pharmacyId)
        val now = Instant.now()
        mutable.value = current.copy(catalog = catalog, initial = confirmed, history = history, admin = false, restorePaused = true,
            publishedAt = publishedAt, revision = revision, refreshedAt = now, reconnectQueued = false,
            workspaceId = UUID.randomUUID().toString(), pending = CatalogEdits.diff(confirmed, catalog, now, null, UUID.randomUUID().toString()))
    }
    @Synchronized fun setConnection(connected: Boolean) {
        val current = authorized()
        mutable.value = current.copy(connected = connected)
    }
    @Synchronized fun setOnline(online: Boolean) {
        val current = initialize()
        mutable.value = current.copy(online = online)
        if (!current.online && online && current.reconnectQueued && current.admin && current.connected && !current.restorePaused) publish()
    }
    @Synchronized fun publish() {
        val current = authorized()
        adminCheck(!current.restorePaused) { "راجع النسخة المستعادة أولاً" }
        adminCheck(current.connected && current.online) { "الاتصال التجريبي غير متاح" }
        if (current.pending.isEmpty()) return
        val now = Instant.now()
        val revision = current.revision + 1
        mutable.value = current.copy(initial = current.catalog, pending = emptyList(), reconnectQueued = false, publishedAt = now,
            refreshedAt = now, revision = revision, history = current.history.map { entry ->
                if (entry.publishedRevision == null) entry.copy(publishedRevision = revision) else entry
            })
    }
    @Synchronized fun refresh() {
        val current = initialize()
        adminCheck(current.connected && current.online) { "الاتصال التجريبي غير متاح" }
        mutable.value = current.copy(refreshedAt = Instant.now())
    }
    /** Hold the workspace monitor across shared CSV planning and all writes in one chunk. */
    @Synchronized fun <T> batch(action: () -> T): T {
        check(batchState == null)
        batchState = authorized()
        try {
            val result = action()
            mutable.value = requireNotNull(batchState)
            return result
        } finally { batchState = null }
    }
    @Synchronized fun reset() {
        val current = initialize()
        val catalog = BuildSeed.catalog()
        pinDigits = "1234"
        mutable.value = DemoSessionState(catalog, catalog, admin = current.admin)
    }
    @Synchronized fun edit(change: (CatalogSnapshot, DeviceIdentity, Instant, String) -> CatalogSnapshot) {
        val current = authorized()
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
        val updated = current.copy(catalog = next, reconnectQueued = current.reconnectQueued || !current.online,
            pending = CatalogEdits.diff(current.initial, next, now, null, tx), history = current.history + history)
        if (batchState != null) batchState = updated else mutable.value = updated
    }
    fun ready(source: CatalogState.Ready, session: DemoSessionState): CatalogState.Ready {
        check(BuildConfig.IS_DEMO && source.isDemo)
        return source.copy(catalog = if (session.admin || session.restorePaused) session.catalog else session.initial, admin = session.admin, pendingCount = if (session.admin) session.pending.size else 0,
            unpublishedMedicationIds = if (session.admin) session.pending.filter { it.entityType in setOf("medication", "availability") }
                .map { it.targetId.substringBefore('/') }.toSet() else emptySet(),
            restorePaused = session.restorePaused, hasPublicationConnection = session.connected, hasBackup = false, publishedAt = session.publishedAt, lastSuccessfulRefreshAt = session.refreshedAt)
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
                historyPreview(entry.oldValue), historyPreview(entry.newValue), entry.editedAt, entry.observedAt, entry.publishedRevision, false)
        }
        val options = session.catalog.medications.associate { it.id to MedicationPresentation.identity(it, session.catalog.formsById[it.formId]?.nameAr) }.toMutableMap()
        session.history.filter { it.entityType in setOf("medication", "availability") }.forEach { options.putIfAbsent(it.entityId.substringBefore('/'), it.entityName) }
        return HistoryPage(entries, offset.toLong() + 50 < filtered.size, options.map { HistoryOption(it.key, it.value) }.sortedBy { normalizedName(it.name) }, timezone)
    }
}
