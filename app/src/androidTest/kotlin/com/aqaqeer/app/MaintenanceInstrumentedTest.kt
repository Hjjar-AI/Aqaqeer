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
    @Test fun beginPausesEditsAndRecreationRetainsBaselineThenCancellationCleansOwnedState(): Unit = runBlocking {
        coordinator().begin(MaintenanceKind.ROTATION)
        val journal = coordinator().journal(); assertNotNull(journal.stateDigest)
        assertEquals(MaintenancePhase.PREPARING, journal.phase)
        assertThrows(IllegalStateException::class.java) { runBlocking { p.medication("blocked") } }
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
        assertThrows(IllegalStateException::class.java) { runBlocking { coordinator().begin(MaintenanceKind.MIGRATION) } }
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
