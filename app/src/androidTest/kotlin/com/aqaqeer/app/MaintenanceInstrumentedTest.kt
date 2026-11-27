package com.aqaqeer.app

import android.net.Uri
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class MaintenanceInstrumentedTest {
    private lateinit var p: SuiteProfile
    private lateinit var remote: SuiteRemote
    private fun coordinator(): MaintenanceCoordinator = MaintenanceCoordinator(MaintenanceStore(p.secure), MaintenanceStage(p.context),
        p.admin, SnapshotReadClient(remote, EnvelopeReader(p.root)), remote, remote, remote, p.requirements, p.backups, p.context)
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO); p = SuiteProfile(); p.create(); p.medication(); remote = SuiteRemote(); p.publish(remote)
    }
    @After fun cleanup() { if (::p.isInitialized) p.close() }
    @Test fun preparingJournalWithoutBaselineRebuildsItOnRecreationWithoutRemoteWrites(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.ROTATION)
        val store = MaintenanceStore(p.secure); val original = flow.journal()
        val before = p.admin.transaction { it.catalog() }
        val uploads = remote.uploads; val writes = remote.writes
        MaintenanceStage(p.context).clear()
        store.write(original.copy(stateDigest = null, sourceId = null, sourceRevision = null, sourceTime = null))
        val resumed = coordinator().journal()
        assertEquals(original.id, resumed.id); assertEquals(MaintenancePhase.PREPARING, resumed.phase)
        assertNotNull(resumed.stateDigest)
        assertEquals(before, MaintenanceStage(p.context).baseline(original.id).catalog)
        assertEquals(uploads, remote.uploads); assertEquals(writes, remote.writes)
        assertThrows(AdminProblem::class.java) { runBlocking { p.medication("still blocked") } }
        coordinator().cancel()
    }

    private suspend fun rotated(): MaintenanceCoordinator {
        val flow = coordinator(); flow.begin(MaintenanceKind.ROTATION); flow.prepareSource(false)
        flow.backup(Uri.fromFile(File(p.root, "recovery.aqbak")), "passphrase".toCharArray(),
            "passphrase".toCharArray(), flow.journal().id)
        flow.revocationGuide(); flow.rotationToken(M3Fixtures.token); flow.publishMaintenance()
        assertEquals(MaintenancePhase.COMPLETE, flow.journal().phase)
        return flow
    }

    @Test fun postActivationVerificationFailureRetainsJournalAndCanRetryWithoutUploading(): Unit = runBlocking {
        val flow = rotated(); val journal = flow.journal()
        val pin = remote.current; val uploads = remote.uploads; val writes = remote.writes
        remote.current = null
        assertThrows(SnapshotReadException::class.java) { runBlocking { coordinator().complete() } }
        assertEquals(journal, MaintenanceStore(p.secure).read())
        assertNotNull(PendingConnectionStore(p.secure, "active-connection").info())
        remote.current = pin
        coordinator().complete()
        assertNull(MaintenanceStore(p.secure).read())
        assertEquals(uploads, remote.uploads); assertEquals(writes, remote.writes)
    }

    @Test fun committedCompletionCheckpointFinishesLogicalCleanupAfterRecreation(): Unit = runBlocking {
        val flow = rotated(); val store = MaintenanceStore(p.secure)
        val finishing = flow.journal().copy(checkpointId = newId())
        val before = p.admin.transaction { it.catalog() }; val uploads = remote.uploads
        store.write(finishing)
        // Stop at the durable checkpoint, before logical cleanup, then use startup recovery.
        p.admin.transaction { recordMaintenanceCheckpoint(it, finishing, "maintenanceFinished") }
        assertNotNull(store.read())
        p.admin.transaction { finishMaintenanceActivation(it, MaintenanceStore(p.secure), MaintenanceStage(p.context)) }
        p.admin.transaction { finishMaintenanceActivation(it, MaintenanceStore(p.secure), MaintenanceStage(p.context)) }
        assertNull(store.read())
        for (entry in listOf("old", "candidate", "transport")) assertNull(store.connection(entry).info())
        assertEquals(before, p.admin.transaction { it.catalog() }); assertEquals(uploads, remote.uploads)
        val factory = DatabaseFactory(p.context, "maintenance_stage")
        assertTrue(p.context.getDatabasePath(factory.databaseName).isFile)
        val db = factory.open()
        try { assertNull(db.catalogDao().pharmacy()); assertTrue(db.catalogDao().attempts().isEmpty()) }
        finally { db.close() }
    }

    @Test fun beginPausesEditsAndRecreationRetainsBaselineThenCancellationCleansOwnedState(): Unit = runBlocking {
        coordinator().begin(MaintenanceKind.ROTATION)
        val journal = coordinator().journal(); assertNotNull(journal.stateDigest)
        assertEquals(MaintenancePhase.PREPARING, journal.phase)
        assertThrows(AdminProblem::class.java) { runBlocking { p.medication("blocked") } }
        assertEquals(journal, coordinator().journal())
        coordinator().cancel()
        assertNull(MaintenanceStore(p.secure).read())
        assertNull(MaintenanceStore(p.secure).connection("old").info())
        p.medication("unblocked")
    }
    @Test fun pendingWorkAndReaderRoleBlockRotationBeforeAnyRemoteWrite(): Unit = runBlocking {
        p.medication("pending")
        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator().begin(MaintenanceKind.ROTATION) } }
        assertNull(MaintenanceStore(p.secure).read())
        p.admin.transaction { it.updateDeviceIdentity(it.deviceIdentity()!!.copy(adminMode = false)) }
        assertThrows(AdminProblem::class.java) { runBlocking { coordinator().begin(MaintenanceKind.MIGRATION) } }
        assertEquals(1, remote.uploads)
    }
    @Test fun sourcePreparationRequiresFreshSuccessfulBackupBeforeTokenGuide(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.ROTATION); flow.prepareSource(false)
        assertEquals(MaintenancePhase.SOURCE_READY, flow.journal().phase)
        assertThrows(IllegalStateException::class.java) { runBlocking { flow.revocationGuide() } }
        val file = File(p.root, "maintenance.aqbak"); val id = flow.journal().id
        flow.backup(Uri.fromFile(file), " exact backup ".toCharArray(), " exact backup ".toCharArray(), id)
        assertEquals(MaintenancePhase.BACKED_UP, coordinator().journal().phase)
        assertEquals(id, coordinator().journal().receipt!!.flowId)
        coordinator().revocationGuide()
        assertEquals(MaintenancePhase.TOKEN_CHANGE, coordinator().journal().phase)
        coordinator().cancel() // Old public transport remains usable; cancellation verifies it.
        assertNull(MaintenanceStore(p.secure).read())
    }
    @Test fun unsuccessfulBackupCannotCreateReceiptAndErasesPassphraseInputs(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.MIGRATION); flow.prepareSource(false)
        val a = "passphrase".toCharArray(); val b = a.copyOf()
        assertThrows(Exception::class.java) { runBlocking {
            flow.backup(Uri.fromFile(File(p.root, "missing/backup")), a, b, flow.journal().id)
        } }
        assertTrue(a.all { it == '\u0000' }); assertTrue(b.all { it == '\u0000' })
        assertNull(flow.journal().receipt); assertEquals(MaintenancePhase.SOURCE_READY, flow.journal().phase)
        flow.cancel()
    }
    @Test fun completedBackupReceiptCannotAuthorizeAnotherMaintenanceOperation(): Unit = runBlocking {
        val flow = coordinator().apply { begin(MaintenanceKind.ROTATION); prepareSource(false) }
        val first = flow.journal()
        val file = File(p.root, "first-operation.aqbak")
        flow.backup(Uri.fromFile(file), "exact passphrase".toCharArray(), "exact passphrase".toCharArray(), first.id)
        val oldReceipt = requireNotNull(flow.journal().receipt)
        assertEquals(first.id, oldReceipt.flowId)
        flow.cancel()

        val next = coordinator().apply { begin(MaintenanceKind.ROTATION); prepareSource(false) }
        val journal = next.journal()
        assertNotEquals(first.id, journal.id)
        assertNull(journal.receipt)
        assertEquals(journal.sourceId, oldReceipt.sourceId)
        assertEquals(journal.stateDigest, oldReceipt.stateDigest)
        val store = MaintenanceStore(p.secure)
        store.write(journal.copy(phase = MaintenancePhase.BACKED_UP, receipt = oldReceipt))
        val writes = remote.writes
        val uploads = remote.uploads
        try {
            assertThrows(IllegalStateException::class.java) { runBlocking { next.revocationGuide() } }
            assertEquals(MaintenancePhase.BACKED_UP, next.journal().phase)
            assertEquals(writes, remote.writes)
            assertEquals(uploads, remote.uploads)
        } finally {
            store.write(journal)
            next.cancel()
        }
    }
    @Test fun candidateCannotActivateWithoutCommittedRoomCheckpoint(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.REPLACEMENT)
        val store = MaintenanceStore(p.secure); val j = flow.journal()
        store.connection("old").withCandidate(j.id) { store.connection("candidate").retain(it, j.id) }
        val before = PendingConnectionStore(p.secure, "active-connection").info()
        store.write(j.copy(phase = MaintenancePhase.ACTIVATING, checkpointId = newId(), candidateId = newId()))
        p.admin.transaction { finishMaintenanceActivation(it, store, MaintenanceStage(p.context)) }
        assertEquals(before, PendingConnectionStore(p.secure, "active-connection").info())
        assertEquals(MaintenancePhase.ACTIVATING, store.read()!!.phase)
        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator().cancel() } }
    }
    @Test fun missingStagingFailsClosedRatherThanReplacingLiveCatalog(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.REPLACEMENT)
        val before = p.admin.transaction { it.catalog() }
        MaintenanceStage(p.context).clear()
        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator().journal() } }
        assertEquals(before, p.admin.transaction { it.catalog() })
        assertNotNull(MaintenanceStore(p.secure).read())
    }
    @Test fun rotationPublishesFreshKeyThenCompletesAfterVerifiedReadback(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.ROTATION); flow.prepareSource(false)
        val id = flow.journal().id
        flow.backup(Uri.fromFile(File(p.root, "rotation.aqbak")), "passphrase".toCharArray(), "passphrase".toCharArray(), id)
        flow.revocationGuide(); flow.rotationToken(M3Fixtures.token)
        assertEquals(MaintenancePhase.DESTINATION_READY, flow.journal().phase)
        flow.publishMaintenance()
        assertEquals(MaintenancePhase.COMPLETE, flow.journal().phase)
        assertEquals(2, p.admin.transaction { it.pharmacy()!!.keyVersion })
        flow.complete(); assertNull(MaintenanceStore(p.secure).read())
        assertEquals(2, remote.uploads)
    }
    @Test fun lostRotationWriteResponseResumesByReadbackWithoutDuplicateUpload(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.ROTATION); flow.prepareSource(false)
        flow.backup(Uri.fromFile(File(p.root, "rotation.aqbak")), "passphrase".toCharArray(), "passphrase".toCharArray(), flow.journal().id)
        flow.revocationGuide(); flow.rotationToken(M3Fixtures.token); remote.loseWriteResponse = true
        assertThrows(TelegramFailure::class.java) { runBlocking { flow.publishMaintenance() } }
        assertEquals(MaintenancePhase.WRITE_STARTED, coordinator().journal().phase)
        coordinator().publishMaintenance()
        assertEquals(2, remote.uploads); assertEquals(MaintenancePhase.COMPLETE, coordinator().journal().phase)
        coordinator().complete()
    }
    @Test fun readerCanReplaceSamePharmacyConnectionWithoutBeingPromoted(): Unit = runBlocking {
        p.admin.transaction { it.updateDeviceIdentity(it.deviceIdentity()!!.copy(adminMode = false)) }
        val flow = coordinator(); flow.begin(MaintenanceKind.REPLACEMENT)
        val store = MaintenanceStore(p.secure); val journal = flow.journal()
        store.connection("old").withCandidate(journal.id) { store.connection("candidate").retain(it, journal.id) }
        store.write(journal.copy(phase = MaintenancePhase.STAGED))
        flow.verifyReplacement(); flow.activateReplacement()
        assertEquals(MaintenancePhase.REVIEW, flow.journal().phase)
        assertFalse(p.admin.transaction { it.deviceIdentity()!!.adminMode })
        flow.finishReaderReplacement()
        assertNull(store.read()); assertFalse(p.admin.transaction { it.deviceIdentity()!!.adminMode })
    }
    @Test fun missingMigrationSourceRequiresExplicitRetainedReviewAndNeverWritesAutomatically(): Unit = runBlocking {
        val flow = coordinator(); flow.begin(MaintenanceKind.MIGRATION)
        val pinned = remote.current; remote.current = null
        assertThrows(SnapshotReadException::class.java) { runBlocking { flow.prepareSource(false) } }
        flow.prepareSource(true)
        val conflict = flow.conflicts.value.single()
        assertEquals(MaintenancePhase.PREPARING, flow.journal().phase)
        flow.prepareSource(true, mapOf(conflict.key to true))
        assertTrue(flow.journal().sourceUnavailable)
        assertEquals(MaintenancePhase.SOURCE_READY, flow.journal().phase)
        assertEquals(1, remote.uploads); remote.current = pinned; flow.cancel()
    }

}
