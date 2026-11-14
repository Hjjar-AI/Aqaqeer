package com.aqaqeer.app

import com.aqaqeer.app.admin.*
import android.net.Uri
import com.aqaqeer.app.backup.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

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
        assertThrows(Exception::class.java) { runBlocking { p.backups.export(Uri.fromFile(File(p.root,"missing/backup")), password, password) } }
        assertNull(p.admin.transaction { it.control()!!.lastBackupAt })
        export(p)
        assertEquals(p.clock.millis(), p.admin.transaction { it.control()!!.lastBackupAt })
        p.backups.dismissReminder()
        assertEquals(p.clock.millis(), p.admin.transaction { it.control()!!.backupDismissedAt })
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
            dao.insertHistory((0 until 180).map { HistoryEntry(newId(),null,"medication",med.id,med.nameEn,"note",null,
                "x".repeat(65536),null,null,null,p.clock.instant(),null) })
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
