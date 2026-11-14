package com.aqaqeer.app.data

import com.aqaqeer.app.maintenance.*

import androidx.room.withTransaction
import com.aqaqeer.app.admin.CatalogEdits
import com.aqaqeer.app.admin.replace
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/** Foreground reads only. No edit acknowledgement, attempt reconciliation or publication commands. */
@Singleton
class SnapshotRefreshRepository @Inject constructor(private val factory: DatabaseFactory,
    private val secureStore: SecureStore, private val reads: SnapshotReadClient,
    private val requirements: SnapshotRequirementsStore) {
    private val active = PendingConnectionStore(secureStore, "active-connection")
    private val flight = RefreshFlight(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    private data class Base(val connectionId: String, val catalog: CatalogSnapshot, val state: SnapshotState,
        val requirements: SnapshotRequirements)

    suspend fun refresh(): RefreshOutcome = flight.run { perform() }

    private suspend fun perform(): RefreshOutcome {
        if (BuildConfig.IS_DEMO) throw RefreshException(RefreshError.DEMO)
        val base = load()
        return active.withCandidate(base.connectionId) { connection ->
            val pharmacy = requireNotNull(base.catalog.pharmacy)
            if (connection.pharmacyId != pharmacy.pharmacyId || connection.keyVersion != pharmacy.keyVersion)
                throw StorageRecoveryRequired()
            val wire = requireNotNull(base.state.confirmedPayloadJson)
            val document = SnapshotDocument(connection.groupId, requireNotNull(base.state.pinnedMessageId),
                requireNotNull(base.state.documentFileId), requireNotNull(base.state.documentFileUniqueId),
                SnapshotPublication(requireNotNull(base.state.revision), requireNotNull(base.state.publishId), java.time.Instant.parse(requireNotNull(base.state.publishedAt))))
            // An unsupported requirement forces a fresh authenticated observation, never a cached bypass.
            val cache = if (base.requirements.requiresUpdate(BuildConfig.VERSION_CODE)) null
                else ValidatedSnapshotCache.fromConfirmed(document, pharmacy.pharmacyId, connection.keyVersion,
                    BuildConfig.VERSION_CODE, wire)
            val read = reads.readWithCache(connection, BuildConfig.VERSION_CODE, cache)
            currentCoroutineContext().ensureActive()
            // Persist a completed authenticated requirement even if the caller subsequently cancels.
            val known = withContext(NonCancellable) { requirements.observe(pharmacy.pharmacyId, read.snapshot.compatibility) }
            currentCoroutineContext().ensureActive()
            apply(base, read, known)
        }
    }

    private suspend fun load(): Base {
        val lease = factory.acquire()
        val db = lease.database
        try {
            return db.withTransaction {
                val dao = db.catalogDao()
                check(MaintenanceStore(secureStore).read() == null)
                check(dao.control()?.restorePaused != true && dao.control()?.restoreActivationId == null)
                val pharmacy = dao.pharmacy() ?: throw RefreshException(RefreshError.UNCONFIGURED)
                val connection = active.info() ?: throw StorageRecoveryRequired()
                if (connection.pharmacyId != pharmacy.pharmacyId) throw StorageRecoveryRequired()
                val known = requirements.read(pharmacy.pharmacyId) ?: throw StorageRecoveryRequired()
                val snapshot = dao.snapshotState() ?: throw StorageRecoveryRequired()
                if (snapshot.publishedAt == null || snapshot.confirmedPayloadJson == null || snapshot.revision == null || snapshot.publishId == null ||
                    snapshot.pinnedMessageId == null || snapshot.documentFileId == null || snapshot.documentFileUniqueId == null)
                    throw StorageRecoveryRequired()
                Base(connection.id, catalog(dao), snapshot, known)
            }
        } finally { lease.close() }
    }

    private suspend fun apply(base: Base, read: VerifiedSnapshotRead, known: SnapshotRequirements): RefreshOutcome {
        val incoming = read.snapshot as? SnapshotCandidate.Supported
        val supported = incoming != null && !known.requiresUpdate(BuildConfig.VERSION_CODE)
        val baseWire = kotlinx.serialization.json.Json.decodeFromString<SnapshotWire>(base.state.confirmedPayloadJson!!)
        val baseline = baseWire.supported()
        if (baseline.publication.revision != base.state.revision || baseline.publishId != base.state.publishId ||
            baseline.catalog.pharmacy?.pharmacyId != base.catalog.pharmacy?.pharmacyId) throw StorageRecoveryRequired()
        val decision = if (supported) try { publicationDecision(baseline, incoming) }
            catch (_: RefreshException) { null } else null
        val payload = if (supported) incoming.confirmedJson() else null
        if (payload != null) {
            // Leave room for both per-field observation maps, retained strings, and the Room batch.
            val runtime = Runtime.getRuntime()
            val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) - 16L * 1024 * 1024
            val characters = base.state.confirmedPayloadJson.length.toLong() + payload.length
            if (payload.length > 32 * 1024 * 1024 || characters * 16 > available.coerceAtLeast(0))
                throw PayloadException(PayloadError.RESOURCE_LIMIT)
        }
        var rejection: RefreshError? = null
        val lease = factory.acquire()
        val db = lease.database
        try {
            val outcome = db.withTransaction {
                val dao = db.catalogDao()
                check(MaintenanceStore(secureStore).read() == null)
                check(dao.control()?.restorePaused != true && dao.control()?.restoreActivationId == null)
                val current = dao.snapshotState() ?: throw StorageRecoveryRequired()
                val pharmacy = dao.pharmacy() ?: throw RefreshException(RefreshError.LOCAL_STATE_CHANGED)
                if (pharmacy.pharmacyId != base.catalog.pharmacy?.pharmacyId || active.info()?.id != base.connectionId ||
                    current != base.state || !catalog(dao).sameContents(base.catalog))
                    throw RefreshException(RefreshError.LOCAL_STATE_CHANGED)
                val securityState = current.copy(requiredSchemaVersion = known.schemaVersion,
                    requiredMinVersionCode = known.minVersionCode)
                if (!supported) {
                    dao.updateSnapshotState(securityState)
                    rejection = RefreshError.UPDATE_REQUIRED
                    return@withTransaction RefreshOutcome.UNCHANGED
                }
                val localWork = dao.pendingCount() != 0 || dao.attemptCount() != 0
                // Changed bases require the later rebase/uncertain-attempt command. Never discard local work.
                val rebased = if (supported && decision == RefreshOutcome.APPLIED && dao.attemptCount() == 0 && dao.pendingCount() > 0)
                    runCatching { CatalogEdits.merge(baseline.catalog, base.catalog, incoming.catalog, dao.pending()) }.getOrNull() else null
                if (decision == null || (decision == RefreshOutcome.APPLIED && localWork &&
                        (dao.attemptCount() != 0 || rebased == null || rebased.conflicts.isNotEmpty()))) {
                    dao.insertHistory(listOf(RemoteObservationHistory.recovery(pharmacy.pharmacyId,
                        baseline.publishId, incoming.publishId, payload!!, read.observedAt, incoming.revision)))
                    dao.updateSnapshotState(securityState)
                    rejection = if (decision == null) RefreshError.PUBLICATION_RECOVERY else RefreshError.LOCAL_WORK
                    return@withTransaction RefreshOutcome.UNCHANGED
                }
                authorizeObservedPin(dao, secureStore, incoming)
                if (decision == RefreshOutcome.APPLIED) {
                    // Without pending work, the visible catalog must equal its confirmed base.
                    if (!localWork && !base.catalog.sameContents(baseline.catalog)) throw RefreshException(RefreshError.LOCAL_STATE_CHANGED)
                    dao.insertHistory(RemoteObservationHistory.changes(baseline.catalog, incoming.catalog,
                        read.observedAt, incoming.revision) + RemoteObservationHistory.publication(
                            baseline.publishId, incoming, read.observedAt))
                    dao.replace(rebased?.catalog ?: incoming.catalog)
                }
                dao.updateSnapshotState(securityState.copy(publishedAt = incoming.publishedAt.toString(), confirmedPayloadJson = payload, revision = incoming.revision,
                    publishId = incoming.publishId, pinnedMessageId = read.document.messageId,
                    documentFileId = read.document.fileId, documentFileUniqueId = read.document.fileUniqueId,
                    lastSuccessfulSyncAt = read.observedAt))
                decision
            }
            rejection?.let { throw RefreshException(it) }
            return outcome
        } finally { lease.close() }
    }

    private suspend fun catalog(dao: CatalogDao): CatalogSnapshot = CatalogSnapshot(dao.pharmacy(), dao.categories(),
        dao.forms(), dao.sections(), dao.medications(), dao.availability(), dao.tombstones()).also(CatalogRules::validate)
}

internal fun SnapshotWire.supported() = SnapshotCandidate.Supported(compatibility, revision, publishId,
    parentPublishId, basedOnRevision, publishedAt, publishedBy,
    CatalogSnapshot(pharmacy, categories, forms, sections, medications, availability, tombstones))
