package com.aqaqeer.app

import com.aqaqeer.app.admin.*
import android.net.Uri
import com.aqaqeer.app.backup.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.maintenance.MaintenanceStore
import com.aqaqeer.app.maintenance.PinChanges
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.TelegramFailure
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.Clock

class BackupRestoreInstrumentedTest {
    private val profiles = mutableListOf<SuiteProfile>()
    private suspend fun profile(create: Boolean = true) = SuiteProfile().also { profiles += it; if (create) it.create() else it.ready() }
    @Before fun standardOnly() { Assume.assumeFalse(BuildConfig.IS_DEMO) }
    @After fun cleanup() { profiles.asReversed().forEach { it.close() } }
    private val password get() = " exact 😀 backup ".toCharArray()
    private suspend fun export(p: SuiteProfile): File = File(p.root, "roundtrip.aqbak").also { p.backups.export(Uri.fromFile(it), password, password) }
    @Test fun draftRoundTripRetainsHistoryPendingAndCatalogButExcludesIdentityAndAuthorization(): Unit = runBlocking {
        val owner = profile(); owner.medication()
        val before = owner.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        val file = export(owner)
        val reader = profile(false); val identity = reader.admin.transaction { it.deviceIdentity()!!.id }
        val pass = password
        reader.backups.restore(Uri.fromFile(file), pass, "", false)
        assertTrue(pass.all { it == '\u0000' })
        reader.admin.transaction { dao ->
            assertEquals(before.first, dao.catalog()); assertEquals(before.second, dao.pending()); assertEquals(before.third, dao.historyCount())
            assertEquals(identity, dao.deviceIdentity()!!.id); assertFalse(dao.deviceIdentity()!!.adminMode)
            val control = dao.control()!!
            assertTrue(control.restorePaused); assertNull(control.restoreActivationId)
            assertNull(control.firstAdminAt); assertNull(control.lastBackupAt); assertNull(control.backupDismissedAt)
        }
    }
    @Test fun wrongPassphraseCorruptionAndMissingConfirmationPreserveExistingData(): Unit = runBlocking {
        val p = profile(); p.medication(); val file = export(p)
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) }
        assertThrows(Exception::class.java) { runBlocking { p.backups.restore(Uri.fromFile(file), "wrong password".toCharArray(), "صيدلية الاختبار", true) } }
        assertThrows(Exception::class.java) { runBlocking { p.backups.restore(Uri.fromFile(file), password, "wrong name", true) } }
        assertThrows(Exception::class.java) { runBlocking { p.backups.restore(Uri.fromFile(file), password, "صيدلية الاختبار", false) } }
        file.writeText("{\"v\":999}")
        assertThrows(Exception::class.java) { runBlocking { p.backups.restore(Uri.fromFile(file), password, "صيدلية الاختبار", true) } }
        assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.historyCount()) })
    }
    @Test fun successfulCloseAdvancesReminderButFailedExportDoesNot(): Unit = runBlocking {
        val p = profile()
        val first = p.admin.transaction { it.control()!!.firstAdminAt }
        assertNotNull(first)
        val due = requireNotNull(first) + BackupReminder.WEEK
        val dueClock = Clock.fixed(java.time.Instant.ofEpochMilli(due), p.clock.zone)
        val backups = BackupRepository(p.context, p.factory, p.secure, p.admin, p.requirements, dueClock)
        assertEquals(due, p.ready().backupReminderAt)
        assertThrows(Exception::class.java) { runBlocking { backups.export(Uri.fromFile(File(p.root,"missing/backup")), password, password) } }
        assertNull(p.admin.transaction { it.control()!!.lastBackupAt })
        assertEquals(due, p.ready().backupReminderAt)
        backups.export(Uri.fromFile(File(p.root, "reminder.aqbak")), password, password)
        assertEquals(due, p.admin.transaction { it.control()!!.lastBackupAt })
        assertEquals(due + BackupReminder.WEEK, p.ready().backupReminderAt)
        backups.dismissReminder()
        assertEquals(due, p.admin.transaction { it.control()!!.backupDismissedAt })

        p.admin.leave("صيدلية الاختبار", true)
        assertNull(p.ready().backupReminderAt)
        p.admin.transaction { dao ->
            val control = requireNotNull(dao.control())
            assertNull(control.firstAdminAt)
            assertNull(control.lastBackupAt)
            assertNull(control.backupDismissedAt)
        }
    }
    @Test fun readersCannotExportOrReplaceConfiguredCatalog(): Unit = runBlocking {
        val p = profile(); val file = export(p)
        p.admin.transaction { it.updateDeviceIdentity(it.deviceIdentity()!!.copy(adminMode = false)) }
        assertThrows(IllegalStateException::class.java) { runBlocking { export(p) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { p.backups.restore(Uri.fromFile(file), password, "صيدلية الاختبار", true) } }
    }
    @Test fun cancellationAndMismatchedPassphrasesEraseInputsAndDoNotAdvanceSuccess(): Unit = runBlocking {
        val p = profile(); val a = password; val b = "different password".toCharArray()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { p.backups.export(Uri.fromFile(File(p.root,"bad.aqbak")), a, b) } }
        assertTrue(a.all { it == '\u0000' }); assertTrue(b.all { it == '\u0000' })
        val pass = password; val confirm = password
        val job = launch(start = CoroutineStart.LAZY) { p.backups.export(Uri.fromFile(File(p.root,"cancel.aqbak")), pass, confirm) }
        // Enter export with an already-cancelled coroutine to exercise its finally boundary.
        val active = launch { currentCoroutineContext().cancel(); p.backups.export(Uri.fromFile(File(p.root,"cancel2.aqbak")), pass, confirm) }
        active.join(); job.cancel()
        assertTrue(pass.all { it == '\u0000' }); assertTrue(confirm.all { it == '\u0000' })
        assertNull(p.admin.transaction { it.control()!!.lastBackupAt })
    }
    @Test fun sharedRestoreRetainsCredentialsButPausesAutomaticPublicationAndRevokesAdmin(): Unit = runBlocking {
        val p = profile(); p.medication(); p.publish(SuiteRemote())
        val file = export(p); val restored = profile(false)
        restored.backups.restore(Uri.fromFile(file), password, "", false)
        restored.admin.transaction { dao ->
            assertEquals("SHARED", dao.control()!!.lifecycle); assertTrue(dao.control()!!.restorePaused)
            assertFalse(dao.deviceIdentity()!!.adminMode); assertNotNull(dao.snapshotState()!!.confirmedPayloadJson)
        }
        assertNotNull(PendingConnectionStore(restored.secure, "active-connection").info())
        assertFalse(restored.admin.shouldReconnect())
    }
    @Test fun restoreReviewAndRepublicationRequireEachCurrentLivePin(): Unit = runBlocking {
        val owner = profile()
        val original = owner.medication()
        val remote = SuiteRemote()
        val publisher = owner.publish(remote)
        val backupPin = owner.ready().catalog.pharmacy!!.adminPinVerifier
        val file = export(owner)
        val pins = PinChanges(owner.admin, MaintenanceStore(owner.secure))
        val name = owner.ready().catalog.pharmacy!!.name

        owner.admin.saveMedication(original.copy(note = "Live note changed after backup"))
        pins.change("123456".toCharArray(), "654321".toCharArray(), "654321".toCharArray())
        publisher.publish()
        assertTrue(publisher.status.value.verified)
        val livePin = owner.ready().catalog.pharmacy!!.adminPinVerifier
        assertNotEquals(backupPin, livePin)

        val restored = profile(false)
        restored.backups.restore(Uri.fromFile(file), password, "", false)
        assertEquals(backupPin, restored.ready().catalog.pharmacy!!.adminPinVerifier)
        val sharing = restored.sharing(remote)
        val originalCounts = remote.uploads to remote.writes
        restored.admin.promote("123456".toCharArray())
        sharing.confirmRestoredConnection(M3Fixtures.token, remote.title)
        sharing.reconcileRestore(true, name)
        assertTrue(restored.ready().restorePaused)
        assertFalse(restored.ready().admin)
        sharing.publish()
        assertFalse(sharing.status.value.verified)
        assertEquals(originalCounts, remote.uploads to remote.writes)

        restored.admin.promote("654321".toCharArray())
        sharing.reconcileRestore(true, name)
        val review = sharing.status.value.conflicts.single()
        assertTrue(review.key.startsWith("restore-replace:"))
        sharing.reconcileRestore(true, name, mapOf(review.key to true))
        val reviewed = restored.ready()
        assertFalse(reviewed.restorePaused)
        assertEquals(livePin, reviewed.catalog.pharmacy!!.adminPinVerifier)
        assertEquals(original.note, reviewed.catalog.medications.single().note)
        assertTrue(reviewed.pendingCount > 0)
        assertEquals(originalCounts, remote.uploads to remote.writes)

        pins.change("654321".toCharArray(), "987654".toCharArray(), "987654".toCharArray())
        publisher.publish()
        assertTrue(publisher.status.value.verified)
        val latest = requireNotNull(remote.current)
        val latestPin = owner.ready().catalog.pharmacy!!.adminPinVerifier
        val latestCounts = remote.uploads to remote.writes
        sharing.publish()
        assertFalse(sharing.status.value.verified)
        assertFalse(restored.ready().admin)
        assertEquals(reviewed.pendingCount, restored.ready().pendingCount)
        assertEquals(latestCounts, remote.uploads to remote.writes)
        restored.admin.promote("987654".toCharArray())
        sharing.publish()
        assertTrue(sharing.status.value.verified)
        val published = requireNotNull(remote.current)
        assertEquals(latest.messageId, published.messageId)
        assertEquals(latest.publication.revision + 1, published.publication.revision)
        assertEquals(latestCounts.first + 1 to latestCounts.second + 1, remote.uploads to remote.writes)
        val payload = restored.admin.transaction { requireNotNull(it.snapshotState()?.confirmedPayloadJson) }
        assertEquals(latest.publication.publishId, Json.decodeFromString(SnapshotWire.serializer(), payload).parentPublishId)
        assertEquals(latestPin, restored.ready().catalog.pharmacy!!.adminPinVerifier)
        assertEquals(original.note, restored.ready().catalog.medications.single().note)
        assertEquals(0, restored.ready().pendingCount)
    }

    @Test fun restoreAcknowledgesAnAcceptedUncertainPinPublicationWithoutReplayingIt(): Unit = runBlocking {
        val owner = profile()
        val medication = owner.medication()
        val remote = SuiteRemote()
        val publisher = owner.publish(remote)
        val baseline = requireNotNull(remote.current)
        val startingCounts = remote.uploads to remote.writes

        PinChanges(owner.admin, MaintenanceStore(owner.secure)).change(
            "123456".toCharArray(), "654321".toCharArray(), "654321".toCharArray())
        val acceptedPin = requireNotNull(owner.ready().catalog.pharmacy).adminPinVerifier
        owner.admin.saveMedication(medication.copy(note = "Included before the lost response"))
        val included = owner.admin.transaction { it.pending() }
        assertTrue(included.any { it.entityType == "pharmacy" && it.field == "adminPinVerifier" })
        remote.loseWriteResponse = true
        publisher.publish()
        assertFalse(publisher.status.value.verified)
        val attempt = owner.admin.transaction { it.attempts().single() }
        val accepted = requireNotNull(remote.current)
        assertEquals("WRITE_STARTED", attempt.transportPhase)
        assertEquals(attempt.publishId, accepted.publication.publishId)
        assertEquals(baseline.publication.revision + 1, accepted.publication.revision)
        assertEquals(startingCounts.first + 1 to startingCounts.second + 1, remote.uploads to remote.writes)

        val draft = owner.ready().catalog.medications.single().copy(brand = "Edited after the lost response")
        owner.admin.saveMedication(draft)
        val originalPending = owner.admin.transaction { it.pending() }
        val retained = setOf(originalPending.single { it.targetId == draft.id && it.field == "brand" }.id)
        assertEquals(included.map { it.id }.toSet() + retained, originalPending.map { it.id }.toSet())
        val restored = profile(false)
        restored.backups.restore(Uri.fromFile(export(owner)), password, "", false)
        assertEquals(attempt, restored.admin.transaction { it.attempts().single() })
        assertEquals(originalPending, restored.admin.transaction { it.pending() })
        val counts = remote.uploads to remote.writes
        val sharing = restored.sharing(remote)
        restored.admin.promote("654321".toCharArray())
        sharing.confirmRestoredConnection(M3Fixtures.token, remote.title)
        sharing.reconcileRestore(false, "")

        assertTrue(sharing.status.value.conflicts.isEmpty())
        assertFalse(restored.ready().restorePaused)
        assertEquals(accepted, remote.current)
        assertEquals(counts, remote.uploads to remote.writes)
        restored.admin.transaction { dao ->
            assertTrue(dao.attempts().isEmpty())
            assertEquals(accepted.publication.publishId, dao.snapshotState()?.publishId)
            assertEquals(acceptedPin, dao.pharmacy()?.adminPinVerifier)
            assertEquals(retained, dao.pending().map { it.id }.toSet())
            val history = dao.history()
            included.forEach { operation ->
                assertTrue(history.any { it.operationId == operation.id && it.publishedRevision == accepted.publication.revision })
            }
            assertEquals(1, history.count { it.field == "recoveryCandidate" && it.entityId == attempt.publishId })
            assertEquals("Included before the lost response", dao.catalog().medications.single { it.id == medication.id }.note)
            assertEquals(draft.brand, dao.catalog().medications.single().brand)
        }

        sharing.publish()
        assertTrue(sharing.status.value.verified)
        assertEquals(counts.first + 1 to counts.second + 1, remote.uploads to remote.writes)
        restored.admin.transaction { dao ->
            val published = Json.decodeFromString<SnapshotWire>(requireNotNull(dao.snapshotState()?.confirmedPayloadJson)).supported()
            assertEquals(accepted.publication.publishId, published.parentPublishId)
            assertEquals(accepted.publication.revision + 1, published.revision)
            assertEquals(acceptedPin, published.catalog.pharmacy?.adminPinVerifier)
            assertEquals(draft.brand, published.catalog.medications.single().brand)
            assertTrue(dao.pending().isEmpty())
        }
    }

    @Test fun restoreArchivesAnUnacceptedAttemptAndDropsItsPendingPinBeforeSeparatePublication(): Unit = runBlocking {
        val owner = profile()
        val medication = owner.medication()
        val remote = SuiteRemote()
        val publisher = owner.publish(remote)
        val baseline = requireNotNull(remote.current)

        // Restore a second installation at the common authenticated base before either edit.
        val peer = profile(false)
        peer.backups.restore(Uri.fromFile(export(owner)), password, "", false)
        val peerSharing = peer.sharing(remote)
        peer.admin.promote("123456".toCharArray())
        peerSharing.confirmRestoredConnection(M3Fixtures.token, remote.title)
        peerSharing.reconcileRestore(false, "")
        assertFalse(peer.ready().restorePaused)
        val baselineCounts = remote.uploads to remote.writes

        PinChanges(owner.admin, MaintenanceStore(owner.secure)).change(
            "123456".toCharArray(), "654321".toCharArray(), "654321".toCharArray())
        owner.admin.saveMedication(medication.copy(note = "Retain local draft"))
        remote.beforeReplace = { throw TelegramFailure() }
        try { publisher.publish() } finally { remote.beforeReplace = {} }
        assertFalse(publisher.status.value.verified)
        val attempt = owner.admin.transaction { it.attempts().single() }
        assertEquals("WRITE_STARTED", attempt.transportPhase)
        assertTrue(requireNotNull(attempt.uploadedFileId) in remote.files)
        assertEquals(baselineCounts.first + 1 to baselineCounts.second, remote.uploads to remote.writes)
        assertEquals(baseline, remote.current)
        val pending = owner.admin.transaction { it.pending() }
        val pin = pending.single { it.entityType == "pharmacy" && it.field == "adminPinVerifier" }
        val note = pending.single { it.targetId == medication.id && it.field == "note" }
        val file = export(owner)

        PinChanges(peer.admin, MaintenanceStore(peer.secure)).change(
            "123456".toCharArray(), "987654".toCharArray(), "987654".toCharArray())
        peer.admin.saveMedication(medication.copy(brand = "Retain live edit"))
        peerSharing.publish()
        assertTrue(peerSharing.status.value.verified)
        val live = requireNotNull(remote.current)
        val livePin = peer.ready().catalog.pharmacy!!.adminPinVerifier
        val counts = remote.uploads to remote.writes
        assertNotEquals(attempt.publishId, live.publication.publishId)

        val restored = profile(false)
        restored.backups.restore(Uri.fromFile(file), password, "", false)
        val sharing = restored.sharing(remote)
        restored.admin.promote("654321".toCharArray())
        sharing.confirmRestoredConnection(M3Fixtures.token, remote.title)
        sharing.reconcileRestore(false, "")
        assertTrue(restored.ready().restorePaused)
        assertFalse(restored.ready().admin)
        assertEquals(pending, restored.admin.transaction { it.pending() })
        assertEquals(attempt, restored.admin.transaction { it.attempts().single() })
        assertEquals(counts, remote.uploads to remote.writes)

        restored.admin.promote("987654".toCharArray())
        sharing.reconcileRestore(false, "")
        val review = sharing.status.value.conflicts.single()
        assertEquals("restore-attempt:${attempt.publishId}:${live.publication.publishId}", review.key)
        assertTrue(restored.ready().restorePaused)
        assertEquals(attempt, restored.admin.transaction { it.attempts().single() })
        sharing.reconcileRestore(false, "", mapOf(review.key to true))
        assertTrue(sharing.status.value.conflicts.isEmpty())
        assertFalse(restored.ready().restorePaused)
        assertEquals(live, remote.current)
        assertEquals(counts, remote.uploads to remote.writes)
        restored.admin.transaction { dao ->
            assertTrue(dao.attempts().isEmpty())
            assertEquals(livePin, dao.pharmacy()?.adminPinVerifier)
            assertEquals(note.id, dao.pending().single().id)
            assertEquals(live.publication.publishId, dao.pending().single().basePublishId)
            val merged = dao.catalog().medications.single()
            assertEquals("Retain local draft", merged.note)
            assertEquals("Retain live edit", merged.brand)
            val history = dao.history()
            assertTrue(history.any { it.operationId == pin.id && it.publishedRevision == null })
            assertEquals(attempt.candidatePayloadJson,
                history.single { it.field == "recoveryCandidate" && it.entityId == attempt.publishId }.newValue)
        }

        sharing.publish()
        assertTrue(sharing.status.value.verified)
        assertEquals(counts.first + 1 to counts.second + 1, remote.uploads to remote.writes)
        restored.admin.transaction { dao ->
            val published = Json.decodeFromString<SnapshotWire>(requireNotNull(dao.snapshotState()?.confirmedPayloadJson)).supported()
            assertEquals(live.publication.publishId, published.parentPublishId)
            assertEquals(live.publication.revision + 1, published.revision)
            assertEquals(livePin, published.catalog.pharmacy?.adminPinVerifier)
            assertTrue(dao.pending().isEmpty())
        }
    }
    @Test fun androidStreamingEnvelopeAuthenticatesUnicodeAndRejectsInsufficientWorkspace(): Unit = runBlocking {
        val p = profile(); val file = File(p.root, "stream.aqbak")
        ProtectedSpool(p.root).use { plain ->
            plain.output().use { it.write("سجل 😀".repeat(20000).toByteArray()) }
            V1BackupEnvelope { Long.MAX_VALUE }.write(plain, file, password)
        }
        V1BackupEnvelope { Long.MAX_VALUE }.read(file.inputStream(), p.root, password).use { plain ->
            assertEquals("سجل 😀".repeat(20000), plain.input().reader(Charsets.UTF_8).use { it.readText() })
        }
        assertEquals(EnvelopeError.RESOURCE_LIMIT, assertThrows(EnvelopeException::class.java) {
            runBlocking { V1BackupEnvelope { 0 }.read(file.inputStream(), p.root, password) }
        }.error)
    }
    @Test fun historyAboveSnapshotLimitSurvivesArchiveAndRepositoryRoundTrip(): Unit = runBlocking {
        val p = profile(); val med = p.medication()
        p.admin.transaction { dao ->
            dao.insertHistory((0 until 180).map { HistoryEntry(newId(),null,"medication",med.id,MedicationPresentation.identity(med),"note",null,
                "x".repeat(65536),null,p.clock.instant(),null) })
        }
        val before = p.admin.transaction { it.historyCount() }
        val file = export(p); assertTrue(file.length() > 10L * 1024 * 1024)
        val restored = profile(false); restored.backups.restore(Uri.fromFile(file), password, "", false)
        assertEquals(before, restored.admin.transaction { it.historyCount() })
        restored.admin.transaction { dao ->
            dao.backupCursor(androidx.sqlite.db.SimpleSQLiteQuery("SELECT MAX(length(newValue)) FROM history")).use { c -> c.moveToFirst(); assertEquals(65536, c.getInt(0)) }
        }
    }
}
