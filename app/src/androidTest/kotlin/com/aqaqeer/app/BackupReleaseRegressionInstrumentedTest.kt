package com.aqaqeer.app

import com.aqaqeer.app.admin.catalog

import android.net.Uri
import com.aqaqeer.app.backup.BackupRepository
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/** Real encrypted databases and backup codec; faults use the existing destination seam. */
class BackupReleaseRegressionInstrumentedTest {
    private val profiles = mutableListOf<SuiteProfile>()
    @Before fun standardOnly() { Assume.assumeFalse(BuildConfig.IS_DEMO) }
    @After fun cleanup() { profiles.asReversed().forEach { it.close() } }
    private val password get() = " exact 😀 e\u0301 backup ".toCharArray()
    private suspend fun profile(create: Boolean = true) = SuiteProfile().also {
        profiles += it; if (create) it.create() else it.ready()
    }
    private fun writer(p: SuiteProfile, destination: (Uri) -> OutputStream?) = BackupRepository(
        p.context, p.factory, p.secure, p.admin, p.requirements, p.clock,
        openDatabase = { p.factory.open() }, openDestination = destination)
    private suspend fun state(p: SuiteProfile) = p.admin.transaction { dao ->
        listOf(dao.catalog(), dao.pending(), dao.attempts(), dao.historyCount(),
            dao.deviceIdentity(), dao.control(), dao.snapshotState())
    }
    private suspend fun export(p: SuiteProfile, name: String = "backup.aqbak") = File(p.root, name).also {
        p.backups.export(Uri.fromFile(it), password, password)
    }

    @Test fun destinationCloseFailureRetainsDataAndDoesNotRecordSuccessfulBackup(): Unit = runBlocking {
        val p = profile(); p.medication(); val before = state(p)
        val output = File(p.root, "close-failure.aqbak"); val closes = AtomicInteger()
        val api = writer(p) { object : FilterOutputStream(output.outputStream()) {
            override fun close() { out.close(); closes.incrementAndGet(); throw IOException("public close fixture") }
        } }
        val pass = password; val confirmation = password
        assertThrows(IOException::class.java) { runBlocking { api.export(Uri.fromFile(output), pass, confirmation) } }
        assertEquals(1, closes.get()); assertEquals(before, state(p))
        assertTrue(pass.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        // Prove this was a close fault after a complete encrypted write.
        V1BackupEnvelope { Long.MAX_VALUE }.read(output.inputStream(), p.root, password).use {
            assertTrue(it.input().use { input -> input.read() >= 0 })
        }
        assertFalse(File(p.root, "aqaqeer-backup-stage").walk().any { it.isFile && it.extension == "aqbak" })
    }

    @Test fun nullDestinationAndWriteFailureLeaveTheEntireExistingStateUntouched(): Unit = runBlocking {
        val p = profile(); p.medication(); val before = state(p); val writes = AtomicInteger()
        for (output in listOf<OutputStream?>(null, object : OutputStream() {
            override fun write(value: Int) { writes.incrementAndGet(); throw IOException("public write fixture") }
        })) {
            val pass = password; val confirmation = password
            assertThrows(Exception::class.java) { runBlocking {
                writer(p) { output }.export(Uri.fromFile(File(p.root, "unused")), pass, confirmation)
            } }
            assertEquals(before, state(p))
            assertTrue(pass.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        }
        assertEquals(1, writes.get())
    }

    @Test fun cancellationDuringDestinationWriteClosesOutputWithoutAdvancingSuccess(): Unit = runBlocking {
        val p = profile(); p.medication(); val before = state(p)
        val writes = AtomicInteger(); val closes = AtomicInteger()
        val pass = password; val confirmation = password
        val operation = async {
            val context = currentCoroutineContext()
            writer(p) { object : OutputStream() {
                override fun write(value: Int) { writes.incrementAndGet(); context.cancel() }
                override fun write(bytes: ByteArray, offset: Int, length: Int) { writes.incrementAndGet(); context.cancel() }
                override fun close() { closes.incrementAndGet() }
            } }.export(Uri.fromFile(File(p.root, "unused")), pass, confirmation)
        }
        try { operation.await(); fail("Cancelled output reported success") } catch (_: CancellationException) { }
        assertTrue(operation.isCancelled); assertTrue(writes.get() > 0); assertEquals(1, closes.get())
        assertEquals(before, state(p))
        assertTrue(pass.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
    }

    @Test fun samePharmacyRestorePreservesTargetIdentityAndLocalReminderCheckpoints(): Unit = runBlocking {
        val p = profile(); val med = p.medication(); val file = export(p)
        p.admin.transaction { dao -> dao.control(dao.control()!!.copy(
            firstAdminAt = 100, lastBackupAt = 200, backupDismissedAt = 300)) }
        val identity = p.admin.transaction { it.deviceIdentity()!!.id }
        p.admin.saveMedication(med.copy(note = "later local edit"))
        p.backups.restore(Uri.fromFile(file), password, "صيدلية الاختبار", true)
        p.admin.transaction { dao ->
            assertEquals(identity, dao.deviceIdentity()!!.id); assertFalse(dao.deviceIdentity()!!.adminMode)
            assertNull(dao.catalog().medications.single().note)
            assertEquals(100L, dao.control()!!.firstAdminAt); assertEquals(200L, dao.control()!!.lastBackupAt)
            assertEquals(300L, dao.control()!!.backupDismissedAt); assertTrue(dao.control()!!.restorePaused)
        }
    }

    @Test fun differentPharmacyReplacementPreservesTargetIdentityAndResetsLocalReminders(): Unit = runBlocking {
        val donor = profile(); donor.medication("donor"); val file = export(donor)
        val target = profile(); target.medication("target")
        val donorId = donor.admin.transaction { it.pharmacy()!!.pharmacyId }
        val targetId = target.admin.transaction { it.pharmacy()!!.pharmacyId }
        assertNotEquals(donorId, targetId)
        val identity = target.admin.transaction { it.deviceIdentity()!!.id }
        target.backups.restore(Uri.fromFile(file), password, "صيدلية الاختبار", true)
        target.admin.transaction { dao ->
            assertEquals(donorId, dao.pharmacy()!!.pharmacyId); assertEquals(identity, dao.deviceIdentity()!!.id)
            assertFalse(dao.deviceIdentity()!!.adminMode); assertTrue(dao.control()!!.restorePaused)
            assertNull(dao.control()!!.firstAdminAt); assertNull(dao.control()!!.lastBackupAt)
            assertNull(dao.control()!!.backupDismissedAt)
            assertEquals("donor", dao.catalog().medications.single().genericEn)
        }
    }

    @Test fun olderBackupCannotEraseAProtectedSamePharmacyUpdateRequirement(): Unit = runBlocking {
        val donor = profile(); donor.medication(); val file = export(donor)
        val id = donor.admin.transaction { it.pharmacy()!!.pharmacyId }
        val target = profile(false)
        target.requirements.observe(id, SnapshotCompatibility(2, Int.MAX_VALUE, 1))
        target.backups.restore(Uri.fromFile(file), password, "", false)
        val known = requireNotNull(target.requirements.read(id))
        assertEquals(2, known.schemaVersion); assertEquals(Int.MAX_VALUE, known.minVersionCode)
        assertTrue(target.ready().requiresUpdate)
        assertTrue(target.admin.transaction { it.control()!!.restorePaused })
    }

    @Test fun repeatedExportsUseFreshEncryptionAndBothAuthenticateWithExactUnicodePassphrase(): Unit = runBlocking {
        val p = profile(); p.medication()
        val first = export(p, "first.aqbak"); val second = export(p, "second.aqbak")
        assertFalse(first.readBytes().contentEquals(second.readBytes()))
        for (file in listOf(first, second)) {
            V1BackupEnvelope { Long.MAX_VALUE }.read(file.inputStream(), p.root, password).use { plain ->
                assertTrue(plain.input().use { it.read() >= 0 })
            }
            assertThrows(EnvelopeException::class.java) { runBlocking {
                V1BackupEnvelope { Long.MAX_VALUE }.read(file.inputStream(), p.root,
                    "exact 😀 e\u0301 backup".toCharArray()).close()
            } }
        }
    }
}
