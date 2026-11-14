package com.aqaqeer.app.data

import com.aqaqeer.app.maintenance.*

import androidx.room.withTransaction
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.VerifiedSnapshotRead
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val catalog: CatalogSnapshot, val isDemo: Boolean,
        val lastSuccessfulRefreshAt: Instant? = null, val publishedAt: Instant? = null,
        val requiresUpdate: Boolean = false, val lifecycle: String = "SHARED",
        val admin: Boolean = false, val pendingCount: Int = 0, val uncertain: Boolean = false,
        val restorePaused: Boolean = false, val restoreConnectionConfirmed: Boolean = false, val backupReminderAt: Long? = null, val restoreHasConnection: Boolean = false,
        val hasPublicationConnection: Boolean = false, val maintenancePaused: Boolean = false, val maintenanceRecoveryRequired: Boolean = false,
        val unpublishedMedicationIds: Set<String> = emptySet()) : CatalogState
    data object RecoveryRequired : CatalogState
}

@Singleton
class CatalogRepository @Inject constructor(private val factory: DatabaseFactory,
    private val secureStore: SecureStore, private val requirements: SnapshotRequirementsStore, private val maintenanceStage: MaintenanceStage, private val preferences: DisplayPreferences) {
    private val active = PendingConnectionStore(secureStore, "active-connection")

    /** Initial reader activation only; never replaces a configured pharmacy or local work. */
    internal suspend fun activateReader(id: String, candidate: ConnectionCandidate,
        read: VerifiedSnapshotRead, snapshot: SnapshotCandidate.Supported) {
        check(!BuildConfig.IS_DEMO)
        check(read.snapshot === snapshot)
        check(snapshot.compatibility.keyVersion == candidate.keyVersion)
        CatalogRules.validate(snapshot.catalog)
        val known = requirements.read(candidate.pharmacyId) ?: throw StorageRecoveryRequired()
        if (known.requiresUpdate(BuildConfig.VERSION_CODE)) throw PayloadException(PayloadError.UNSUPPORTED_SCHEMA)
        val confirmed = snapshot.confirmedJson()
        val lease = factory.acquire()
        val database = lease.database
        try {
            database.withTransaction {
                val dao = database.catalogDao()
                val installation = dao.installation() ?: throw StorageRecoveryRequired()
                check(!installation.isDemo)
                if (dao.pharmacy() != null) throw ConnectionImportException(ConnectionImportError.CONFIGURED)
                check(dao.pendingCount() == 0 && dao.attemptCount() == 0 && dao.historyCount() == 0)
                check(dao.tombstones().isEmpty())
                val device = dao.deviceIdentity() ?: throw StorageRecoveryRequired()
                val state = dao.snapshotState() ?: throw StorageRecoveryRequired()
                check(state.confirmedPayloadJson == null && state.publishId == null)
                check(snapshot.catalog.pharmacy?.pharmacyId == candidate.pharmacyId)
                // Write-ahead credentials: a crash here leaves an unconfigured, retryable join.
                // After Room commits, credentials already exist. Pending removal is cleanup only.
                active.retain(candidate, id)
                dao.clearAvailability(); dao.clearMedications(); dao.clearSections()
                dao.clearCategories(); dao.clearForms()
                dao.insertCategories(snapshot.catalog.categories); dao.insertForms(snapshot.catalog.forms)
                dao.insertSections(snapshot.catalog.sections)
                dao.insertPharmacy(requireNotNull(snapshot.catalog.pharmacy))
                dao.insertMedications(snapshot.catalog.medications); dao.insertAvailability(snapshot.catalog.availability)
                dao.insertTombstones(snapshot.catalog.tombstones)
                dao.updateSnapshotState(SnapshotState(publishedAt = snapshot.publishedAt.toString(), confirmedPayloadJson = confirmed,
                    revision = snapshot.revision, publishId = snapshot.publishId,
                    pinnedMessageId = read.document.messageId, documentFileId = read.document.fileId,
                    documentFileUniqueId = read.document.fileUniqueId, lastSuccessfulSyncAt = read.observedAt,
                    requiredSchemaVersion = known.schemaVersion, requiredMinVersionCode = known.minVersionCode))
                dao.updateDeviceIdentity(device.copy(adminMode = false, lastSeenAt = read.observedAt))
                dao.control(LocalControl(lifecycle = "SHARED"))
                // Joining starts with empty device-local history; shared contents are the baseline.
            }
        } finally { lease.close() }
    }

    suspend fun resetProtectedStorage() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try { factory.reset() }
        finally { com.aqaqeer.app.security.ProtectedReadCache.invalidateAll() }
    }

    private val contentTables = setOf("pharmacy", "categories", "forms", "sections", "medications", "availability", "tombstones")
    private class ReadCache {
        var cachedCatalog: CatalogSnapshot? = null
        var confirmedKey: Pair<String?, String?>? = null
        var confirmedCatalog: CatalogSnapshot? = null
    }
    private suspend fun initialize(dao: CatalogDao) {
        val existing = dao.installation()
        if (existing == null) {
            val seed = BuildSeed.catalog()
            CatalogRules.validate(seed)
            dao.insertCategories(seed.categories)
            dao.insertForms(seed.forms)
            dao.insertSections(seed.sections)
            seed.pharmacy?.let { dao.insertPharmacy(it) }
            dao.insertMedications(seed.medications)
            dao.insertAvailability(seed.availability)
            dao.insertSnapshotState(SnapshotState())
            dao.control(LocalControl(lifecycle = if (BuildConfig.IS_DEMO) "SHARED" else "UNCONFIGURED"))
            val now = Instant.now()
            dao.insertDeviceIdentity(DeviceIdentity(UUID.randomUUID().toString(), null, false, now, now))
            dao.insertInstallation(InstallationState(seedVersion = BuildSeed.VERSION, isDemo = BuildConfig.IS_DEMO))
        } else {
            check(existing.isDemo == BuildConfig.IS_DEMO) { "Data profile mismatch" }
            BuildSeed.refreshIfNeeded(dao, existing)
        }
    }
    private suspend fun readState(dao: CatalogDao, cache: ReadCache, changed: Set<String>): CatalogState.Ready {
        val beforeControl = dao.control()
        val activating = beforeControl?.leaving == true || beforeControl?.restoreActivationId != null ||
            try { MaintenanceStore(secureStore).read() != null } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                true
            }
        com.aqaqeer.app.admin.finishLeave(dao, secureStore, maintenanceStage, preferences)
        com.aqaqeer.app.backup.finishRestoreActivation(dao, secureStore, requirements)
        var maintenanceRecovery = false
        val maintenance = try {
            finishMaintenanceActivation(dao, MaintenanceStore(secureStore), maintenanceStage)
            MaintenanceStore(secureStore).read().also {
                if (it != null && it.pharmacyId != dao.pharmacy()?.pharmacyId) throw StorageRecoveryRequired()
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            maintenanceRecovery = true; null
        }
        val initialControl = requireNotNull(dao.control())
        if (dao.deviceIdentity()?.adminMode == true && initialControl.firstAdminAt == null)
            dao.control(initialControl.copy(firstAdminAt = System.currentTimeMillis()))
        val snapshot = if (cache.cachedCatalog == null || activating || changed.any { it in contentTables }) {
            CatalogSnapshot(dao.pharmacy(), dao.categories(), dao.forms(),
                dao.sections(), dao.medications(), dao.availability(), dao.tombstones()).also {
                CatalogRules.validate(it)
                cache.cachedCatalog = it
            }
        } else requireNotNull(cache.cachedCatalog)
        // Publication time is supplied by authenticated snapshot metadata in M3.
        // Never substitute a medication update time or the local launch time.
        val state = dao.snapshotMetadata()
        val pharmacy = snapshot.pharmacy
        val control = dao.control() ?: throw StorageRecoveryRequired()
        val lifecycle = control.lifecycle
        check(lifecycle in setOf("UNCONFIGURED", "LOCAL_DRAFT", "SHARED"))
        check((pharmacy == null) == (lifecycle == "UNCONFIGURED"))
        val known = if (!BuildConfig.IS_DEMO && pharmacy != null && lifecycle == "SHARED") {
            val connection = active.info() ?: throw StorageRecoveryRequired()
            if (connection.pharmacyId != pharmacy.pharmacyId) throw StorageRecoveryRequired()
            requirements.read(pharmacy.pharmacyId) ?: throw StorageRecoveryRequired()
        } else pharmacy?.let { requirements.read(it.pharmacyId) }
        val publishedAt = state?.publishedAt?.let(Instant::parse)
        val authorized = dao.deviceIdentity()?.adminMode == true
        val visible = if (!authorized && !control.restorePaused && dao.pendingCount() > 0 && state?.publishId != null) {
            val key = state.publishId to state.publishedAt
            if (cache.confirmedKey != key || cache.confirmedCatalog == null || activating) {
                cache.confirmedCatalog = Json.decodeFromString<SnapshotWire>(requireNotNull(dao.snapshotState()?.confirmedPayloadJson)).supported().catalog
                cache.confirmedKey = key
            }
            requireNotNull(cache.confirmedCatalog)
        } else snapshot
        return CatalogState.Ready(visible, BuildConfig.IS_DEMO, state?.lastSuccessfulSyncAt,
            publishedAt, known?.requiresUpdate(BuildConfig.VERSION_CODE) == true, lifecycle,
            authorized, dao.pendingCount(), dao.attemptCount() != 0, control.restorePaused, control.restoreConnectionConfirmed,
            if (authorized) com.aqaqeer.app.backup.BackupReminder(control.firstAdminAt, control.lastBackupAt,
                control.backupDismissedAt).nextAt() else null,
            control.restorePaused && (if (lifecycle == "SHARED") active.info() != null
                else PendingConnectionStore(secureStore, "setup-connection").info() != null),
            hasPublicationConnection = pharmacy != null && (lifecycle == "SHARED" ||
                PendingConnectionStore(secureStore, "setup-connection").info()?.pharmacyId == pharmacy.pharmacyId),
            maintenancePaused = maintenance != null || maintenanceRecovery, maintenanceRecoveryRequired = maintenanceRecovery,
            unpublishedMedicationIds = if (authorized) dao.pending().filter {
                it.entityType in setOf("medication", "availability")
            }.map { it.targetId.substringBefore('/') }.toSet() else emptySet())
    }

    /** Same initialization/recovery/integrity boundary as observation, without an invalidation flow. */
    internal suspend fun readOnce(): CatalogState = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val lease = factory.acquire()
            try { lease.database.withTransaction {
                val dao = lease.database.catalogDao()
                initialize(dao)
                readState(dao, ReadCache(), contentTables)
            } } finally { lease.close() }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { CatalogState.RecoveryRequired }
    }

    fun observe(): Flow<CatalogState> = flow<CatalogState> {
        val lease = factory.acquire()
        val database = lease.database
        try {
            val dao = database.catalogDao()
            val cache = ReadCache()
            database.withTransaction { initialize(dao) }
            emitAll(database.invalidationTracker.createFlow("pharmacy", "categories", "forms", "sections",
                "medications", "availability", "tombstones", "snapshot_state", "local_control",
                "device_identity", "pending_operations", "publish_attempts").combine(MaintenanceStore.changes) { tables, _ -> tables }.map { changed ->
                database.withTransaction { readState(dao, cache, changed) }
            }.distinctUntilChanged())
        } finally { lease.close() }
    }.catch { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        // Never expose SQL, keys, filenames, or platform exception messages to the UI.
        emit(CatalogState.RecoveryRequired)
    }.onStart { emit(CatalogState.Loading) }.flowOn(Dispatchers.IO)
}
