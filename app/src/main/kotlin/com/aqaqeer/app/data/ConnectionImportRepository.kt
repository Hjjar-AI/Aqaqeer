package com.aqaqeer.app.data

import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.SnapshotReadClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionImportError { CONFIGURED, DEMO, PENDING_EXISTS, INVALID_URI }
class ConnectionImportException(val error: ConnectionImportError) : Exception(error.name)

/** Explicit staging, verification and reader activation. No admin authorization or publishing. */
@Singleton
class ConnectionImportRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val catalog: CatalogRepository,
    secureStore: SecureStore,
    private val snapshotReads: SnapshotReadClient,
    private val requirements: SnapshotRequirementsStore,
) {
    private val pending = PendingConnectionStore(secureStore)
    private val active = PendingConnectionStore(secureStore, "active-connection")
    private val envelopes = EnvelopeReader(context.noBackupFilesDir)
    private val payloads = PayloadReader()
    private val gate = Mutex()

    suspend fun loadPending(): PendingConnectionState = withContext(Dispatchers.IO) {
        gate.withLock {
            envelopes.cleanupAbandoned()
            var info = if (BuildConfig.IS_DEMO) null else pending.info()
            if (info != null) {
                val state = catalog.observe().first { it !is CatalogState.Loading }
                if (state !is CatalogState.Ready) throw com.aqaqeer.app.security.StorageRecoveryRequired()
                if (state.catalog.pharmacy != null) {
                    // Recover a process death after Room commit, before pending-marker cleanup.
                    val committed = active.info() ?: throw com.aqaqeer.app.security.StorageRecoveryRequired()
                    if (committed.id != info.id || committed.pharmacyId != state.catalog.pharmacy.pharmacyId)
                        throw com.aqaqeer.app.security.StorageRecoveryRequired()
                    pending.discard(info.id)
                    info = null
                }
            }
            PendingConnectionState(info, info?.let { requirements.read(it.pharmacyId) })
        }
    }

    /** Password remains caller-owned; EnvelopeReader copies and clears its own buffer. */
    suspend fun stage(uri: Uri, password: CharArray): PendingConnectionState = withContext(Dispatchers.IO) {
        gate.withLock {
            requireUnconfigured()
            if (uri.scheme != "content") throw ConnectionImportException(ConnectionImportError.INVALID_URI)
            if (pending.info() != null) throw ConnectionImportException(ConnectionImportError.PENDING_EXISTS)
            val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException()
            // Reader owns the stream from here, including failures and queued cancellation.
            val plaintext = envelopes.password(input, EnvelopeDomain.CONNECTION, password)
            payloads.connection(plaintext).use { candidate ->
                requireUnconfigured()
                val knownRequirements = requirements.read(candidate.pharmacyId)
                currentCoroutineContext().ensureActive()
                PendingConnectionState(pending.stage(candidate), knownRequirements)
            }
        }
    }

    suspend fun verify(id: String): PendingConnectionVerification = withContext(Dispatchers.IO) {
        gate.withLock {
            requireUnconfigured()
            pending.withCandidate(id) { candidate ->
                // An unreadable requirement record must not be bypassed by a fresh online observation.
                requirements.read(candidate.pharmacyId)
                val read = snapshotReads.read(candidate, BuildConfig.VERSION_CODE)
                val known = withContext(NonCancellable) {
                    // Finish this short security-state write even if cancellation follows authentication.
                    requirements.observe(candidate.pharmacyId, read.snapshot.compatibility)
                }
                requireUnconfigured()
                currentCoroutineContext().ensureActive()
                val supported = read.snapshot as? SnapshotCandidate.Supported
                PendingConnectionVerification(id, read.snapshot.publication, read.observedAt,
                    supported?.catalog?.pharmacy?.name, supported?.catalog?.medications?.size, known,
                    read.snapshot is SnapshotCandidate.RequiresUpdate || known.requiresUpdate(BuildConfig.VERSION_CODE))
            }
        }
    }

    /** Always re-download; a verification summary is never authority to apply cached UI data. */
    suspend fun activate(id: String) = withContext(Dispatchers.IO) {
        gate.withLock {
            requireUnconfigured()
            pending.withCandidate(id) { candidate ->
                val prior = requirements.read(candidate.pharmacyId)
                if (prior?.requiresUpdate(BuildConfig.VERSION_CODE) == true)
                    throw PayloadException(PayloadError.UNSUPPORTED_SCHEMA)
                val read = snapshotReads.read(candidate, BuildConfig.VERSION_CODE)
                val known = withContext(NonCancellable) {
                    requirements.observe(candidate.pharmacyId, read.snapshot.compatibility)
                }
                val supported = read.snapshot as? SnapshotCandidate.Supported
                if (supported == null || known.requiresUpdate(BuildConfig.VERSION_CODE))
                    throw PayloadException(PayloadError.UNSUPPORTED_SCHEMA)
                requireUnconfigured()
                currentCoroutineContext().ensureActive()
                // Cancellation can stop network work, but cannot split the short local commit/cleanup.
                withContext(NonCancellable) {
                    catalog.activateReader(id, candidate, read, supported)
                    pending.discard(id)
                }
            }
        }
    }

    suspend fun discard(id: String) = withContext(Dispatchers.IO) {
        gate.withLock {
            if (BuildConfig.IS_DEMO) throw ConnectionImportException(ConnectionImportError.DEMO)
            currentCoroutineContext().ensureActive()
            val state = catalog.observe().first { it !is CatalogState.Loading }
            if (state !is CatalogState.Ready) throw com.aqaqeer.app.security.StorageRecoveryRequired()
            withContext(NonCancellable) {
                // A cancelled/failed pre-commit activation can leave write-ahead credentials.
                if (state.catalog.pharmacy == null && active.info()?.id == id) active.discard(id)
                pending.discard(id)
            }
        }
    }

    private suspend fun requireUnconfigured() {
        if (BuildConfig.IS_DEMO) throw ConnectionImportException(ConnectionImportError.DEMO)
        val state = catalog.observe().first { it !is CatalogState.Loading }
        if (state !is CatalogState.Ready) throw com.aqaqeer.app.security.StorageRecoveryRequired()
        if (state.catalog.pharmacy != null) throw ConnectionImportException(ConnectionImportError.CONFIGURED)
    }
}
