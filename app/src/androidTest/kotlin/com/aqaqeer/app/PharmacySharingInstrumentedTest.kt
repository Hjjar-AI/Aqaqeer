package com.aqaqeer.app

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PharmacySharingInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val profiles = mutableListOf<Profile>()
    private inner class Profile {
        val namespace = "sharing_${UUID.randomUUID()}"
        val root = File(base.noBackupFilesDir, namespace).apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        val secure = KeystoreSecureStore(context, namespace)
        val factory = DatabaseFactory(context, namespace)
        val requirements = SnapshotRequirementsStore(secure)
        val repository = CatalogRepository(factory, secure, requirements, com.aqaqeer.app.maintenance.MaintenanceStage(context))
        val admin = AdminRepository(factory, requirements, secure, com.aqaqeer.app.maintenance.MaintenanceStage(context))
        suspend fun ready() = withTimeout(15000) { repository.observe().first { it !is CatalogState.Loading } as CatalogState.Ready }
        suspend fun create() { ready(); admin.create("صيدلية الاختبار", "Asia/Damascus", "المحرر", "الرئيسي", "123456".toCharArray(), "١٢٣٤٥٦".toCharArray()) }
        fun sharing(remote: Remote) = PharmacySharing(admin, secure, requirements,
            SnapshotReadClient(remote, EnvelopeReader(root)), remote, remote, context)
        suspend fun setup(remote: Remote, sharing: PharmacySharing) { sharing.saveSetup("123:test", -123L, remote.title) }
    }
    private fun profile() = Profile().also { profiles += it }
    private class Remote : TelegramSetupTransport, SnapshotWriteTransport, SnapshotReadTransport {
        val title = "مجموعة الاختبار"
        var current: SnapshotDocument? = null
        val files = mutableMapOf<String, ByteArray>()
        var next = 10L; var uploads = 0; var writes = 0
        var loseResponse = false; var foreign = false
        var duringUpload: suspend () -> Unit = {}
        override suspend fun validate(token: String, groupId: Long) = inspect(token, groupId)
        override suspend fun discover(token: String, challenge: String) = listOf(inspect(token, -123L))
        override suspend fun inspect(token: String, group: Long) = SetupGroup(group, title,
            if (foreign) "unrelated" else current?.let { "${it.messageId}:${it.fileId}:${it.publication.publishId}" })
        override suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String): UploadedDocument {
            uploads++; val id = next++; val file = "file-$id"; files[file] = bytes.copyOf()
            captions[id] = SnapshotCaption.decode(caption); duringUpload(); return UploadedDocument(id, file)
        }
        val captions = mutableMapOf<Long, SnapshotPublication>()
        override suspend fun replace(token: String, group: Long, message: Long, file: String, caption: String) {
            writes++; current = SnapshotDocument(group, message, file, "unique-$file", SnapshotCaption.decode(caption))
            if (loseResponse) { loseResponse = false; throw TelegramFailure() }
        }
        override suspend fun pin(token: String, group: Long, message: Long) {
            writes++; current = SnapshotDocument(group, message, "file-$message", "unique-file-$message", captions.getValue(message))
            if (loseResponse) { loseResponse = false; throw TelegramFailure() }
        }
        override suspend fun bot(token: String) = BotIdentity(123)
        override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument {
            if (foreign) throw SnapshotReadException(SnapshotReadError.SNAPSHOT_UNAVAILABLE)
            return current ?: throw SnapshotReadException(SnapshotReadError.SNAPSHOT_UNAVAILABLE)
        }
        override suspend fun document(token: String, document: SnapshotDocument) = files.getValue(document.fileId).inputStream()
    }
    private suspend fun medicine(p: Profile, name: String = "Medicine"): Medication {
        val value = Medication(newId(), name, "10 mg", CatalogSeeds.OTHER_FORM_ID, CatalogSeeds.UNCATEGORIZED_ID,
            Instant.now(), Instant.now(), EditorRef(newId(), "placeholder"))
        p.admin.saveMedication(value); return p.ready().catalog.medications.single { it.id == value.id }
    }
    @After fun cleanOwnedProfiles() { profiles.forEach { p ->
        p.context.deleteDatabase(p.factory.databaseName); p.root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(p.secure.keyAlias) }
    } }
    @Test fun createEditPublishExportJoinUpdatePublishAndRefreshAcrossTwoInstallations() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val owner = profile(); owner.create(); val med = medicine(owner)
        assertTrue(owner.ready().admin); assertEquals("LOCAL_DRAFT", owner.ready().lifecycle)
        val remote = Remote(); val sharing = owner.sharing(remote); owner.setup(remote, sharing)
        sharing.publish(); assertEquals("تم التحقق من النشر", sharing.status.value.message)
        assertEquals("SHARED", owner.ready().lifecycle); assertEquals(0, owner.ready().pendingCount)
        val export = File(owner.root, "test.aqz")
        sharing.export(Uri.fromFile(export), "123456".toCharArray())
        val reader = profile(); reader.ready()
        val candidate = PayloadReader().connection(EnvelopeReader(reader.root).password(export.inputStream(), EnvelopeDomain.CONNECTION, "123456".toCharArray()))
        candidate.use {
            val read = SnapshotReadClient(remote, EnvelopeReader(reader.root)).read(it, BuildConfig.VERSION_CODE)
            reader.requirements.observe(it.pharmacyId, read.snapshot.compatibility)
            reader.repository.activateReader(newId(), it, read, read.snapshot as SnapshotCandidate.Supported)
        }
        assertFalse(reader.ready().admin); assertEquals(med.nameEn, reader.ready().catalog.medications.single().nameEn)
        var rejected = false
        try { medicine(reader, "Unauthorized") } catch (_: Exception) { rejected = true }; assertTrue(rejected)
        owner.admin.saveMedication(med.copy(note = "shared update")); sharing.publish()
        SnapshotRefreshRepository(reader.factory, reader.secure, SnapshotReadClient(remote, EnvelopeReader(reader.root)), reader.requirements).refresh()
        assertEquals("shared update", reader.ready().catalog.medications.single().note)
        val pinClock = java.time.Clock.fixed(Instant.now(), java.time.ZoneOffset.UTC)
        val pinAdmin = AdminRepository(reader.factory, reader.requirements, reader.secure, com.aqaqeer.app.maintenance.MaintenanceStage(reader.context), pinClock)
        var failed = false
        try { pinAdmin.promote("000000".toCharArray(), "محرر ثانٍ") } catch (_: Exception) { failed = true }
        assertTrue(failed)
        val check = reader.factory.open()
        try { assertEquals(1, check.catalogDao().control()!!.failedPins) } finally { check.close() }
        failed = false
        try { AdminRepository(reader.factory, reader.requirements, reader.secure, com.aqaqeer.app.maintenance.MaintenanceStage(reader.context), pinClock).promote("123456".toCharArray(), "محرر ثانٍ") }
        catch (_: Exception) { failed = true }; assertTrue(failed)
        AdminRepository(reader.factory, reader.requirements, reader.secure, com.aqaqeer.app.maintenance.MaintenanceStage(reader.context),
            java.time.Clock.fixed(pinClock.instant().plusSeconds(3), java.time.ZoneOffset.UTC))
            .promote("123456".toCharArray(), "محرر ثانٍ")
        assertTrue(reader.ready().admin)
        val sharedMed = reader.ready().catalog.medications.single()
        owner.admin.saveMedication(sharedMed.copy(note = "owner conflict"))
        reader.admin.saveMedication(sharedMed.copy(note = "reader conflict"))
        reader.sharing(remote).publish()
        sharing.publish()
        assertEquals(1, sharing.status.value.conflicts.size)
        assertTrue(owner.ready().pendingCount > 0)
        sharing.publish(sharing.status.value.conflicts.associate { it.key to true })
        assertTrue(sharing.status.value.verified)
        assertEquals("owner conflict", owner.ready().catalog.medications.single().note)
        assertEquals(0, owner.ready().pendingCount)
        assertEquals(10L, remote.current!!.messageId)
    }
    @Test fun lostPinResponseReconcilesWithoutDuplicatePublication() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); medicine(p); val remote = Remote(); val sharing = p.sharing(remote); p.setup(remote, sharing)
        remote.loseResponse = true; sharing.publish()
        assertTrue(p.ready().uncertain); assertEquals("LOCAL_DRAFT", p.ready().lifecycle)
        val id = remote.current!!.publication.publishId
        p.sharing(remote).publish() // process recreation: reconcile durable attempt against remote
        assertFalse(p.ready().uncertain); assertEquals(0, p.ready().pendingCount)
        assertEquals(id, remote.current!!.publication.publishId); assertEquals(1, remote.uploads); assertEquals(1, remote.writes)
    }
    @Test fun editsDuringUploadRemainPendingForNextPublication() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); val med = medicine(p); val remote = Remote(); val sharing = p.sharing(remote); p.setup(remote, sharing)
        remote.duringUpload = { p.admin.saveMedication(med.copy(note = "created during upload")) }
        sharing.publish()
        assertEquals("تم التحقق من النشر", sharing.status.value.message)
        assertEquals("created during upload", p.ready().catalog.medications.single().note)
        assertEquals(1, p.ready().pendingCount)
        remote.duringUpload = {}; sharing.publish(); assertEquals(0, p.ready().pendingCount)
    }
    @Test fun appearingPinBlocksBootstrapAndRetainsWork() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); medicine(p); val remote = Remote(); val sharing = p.sharing(remote); p.setup(remote, sharing)
        remote.duringUpload = { remote.foreign = true }; sharing.publish()
        assertEquals(0, remote.writes); assertTrue(p.ready().uncertain); assertTrue(p.ready().pendingCount > 0)
    }
    @Test fun authorizationRestrictionAndDraftPersistAcrossRepositoryRecreation() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); val med = medicine(p)
        assertEquals("LOCAL_DRAFT", p.ready().lifecycle); assertTrue(p.ready().admin)
        p.requirements.observe(p.ready().catalog.pharmacy!!.pharmacyId, SnapshotCompatibility(1, Int.MAX_VALUE, 1))
        var rejected = false
        try { p.admin.saveMedication(med.copy(note = "blocked")) } catch (_: Exception) { rejected = true }
        assertTrue(rejected); assertNull(p.ready().catalog.medications.single().note)
    }
    @Test fun versionOneMigrationRetainsExistingCatalogAndMarksItShared() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.ready()
        var db = p.factory.open()
        try {
            db.catalogDao().insertPharmacy(com.aqaqeer.app.fixtures.M3Fixtures.catalog().pharmacy!!)
            val sql = db.openHelper.writableDatabase
            sql.execSQL("DROP TABLE local_control")
            sql.execSQL("UPDATE room_master_table SET identity_hash = '586815bf558773df0fa4177e8f09c6fc' WHERE id = 42")
            sql.version = 1
        } finally { db.close() }
        db = p.factory.open()
        try {
            assertEquals("SHARED", db.catalogDao().control()!!.lifecycle)
            assertEquals(com.aqaqeer.app.fixtures.M3Fixtures.pharmacyId, db.catalogDao().pharmacy()!!.pharmacyId)
            assertEquals(15, db.catalogDao().forms().size)
            assertFalse(db.catalogDao().deviceIdentity()!!.adminMode)
        } finally { db.close() }
    }
    @Test fun repeatedEditsReturningToBaseLeaveHistoryWithoutNetPendingChanges() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); val med = medicine(p)
        val remote = Remote(); val sharing = p.sharing(remote); p.setup(remote, sharing); sharing.publish()
        val original = p.ready().catalog
        p.admin.saveMedication(med.copy(note = "intermediate"))
        p.admin.saveMedication(med.copy(note = null))
        assertEquals(0, p.ready().pendingCount)
        assertEquals(original, p.ready().catalog)
        val db = p.factory.open()
        try { assertTrue(db.catalogDao().historyCount() >= 3) } finally { db.close() }
    }
    @Test fun creatingEditingAndDeletingAnUnpublishedMedicationRemovesNetWorkOnly() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); val med = medicine(p)
        p.admin.saveMedication(med.copy(note = "edited"))
        assertEquals(1, p.ready().pendingCount)
        p.admin.delete("medication", med.id)
        assertEquals(0, p.ready().pendingCount); assertTrue(p.ready().catalog.medications.isEmpty())
        assertTrue(p.ready().catalog.tombstones.isEmpty())
    }

    @Test fun realTelegramCreatePublishExportJoinThenReplaceAndRefresh(): Unit = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        assumeTrue(BuildConfig.APPLICATION_ID.contains(".verification"))
        val config = File(base.noBackupFilesDir, "telegram-test-config.local.json")
        assertTrue("Authorized live integration requires protected configuration; it must not silently skip", config.isFile)
        val value = Json.parseToJsonElement(config.readText()).jsonObject
        val token = value.getValue("botToken").jsonPrimitive.content
        val group = value.getValue("chatId").jsonPrimitive.long
        val transport = TelegramPublishing()
        val target = transport.validate(token, group)
        assertNull("Real integration requires a dedicated empty pin", target.pin)
        val owned = mutableListOf<Long>()
        val record = File(base.noBackupFilesDir, "telegram-test-owned-message-ids.json")
        val writer = object : SnapshotWriteTransport by transport {
            override suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String): UploadedDocument {
                val result = transport.upload(token, group, bytes, caption)
                owned += result.messageId
                record.writeText(Json.encodeToString(owned))
                return result
            }
        }
        val owner = profile(); owner.create(); val med = medicine(owner, "Integration verification only")
        val reads = SnapshotReadClient(TelegramSnapshotReadTransport(), EnvelopeReader(owner.root))
        val sharing = PharmacySharing(owner.admin, owner.secure, owner.requirements, reads, transport, writer, owner.context)
        sharing.saveSetup(token, group, target.title); sharing.publish()
        assertTrue("Initial publication must verify", sharing.status.value.verified)
        val first = owner.ready()
        assertEquals("SHARED", first.lifecycle); assertEquals(0, first.pendingCount)
        val output = File(owner.root, "real-test.aqz")
        sharing.export(Uri.fromFile(output), "12345678".toCharArray())
        val reader = profile(); reader.ready()
        val readerReads = SnapshotReadClient(TelegramSnapshotReadTransport(), EnvelopeReader(reader.root))
        val candidate = PayloadReader().connection(EnvelopeReader(reader.root).password(output.inputStream(), EnvelopeDomain.CONNECTION, "12345678".toCharArray()))
        candidate.use {
            val read = readerReads.read(it, BuildConfig.VERSION_CODE)
            reader.requirements.observe(it.pharmacyId, read.snapshot.compatibility)
            reader.repository.activateReader(newId(), it, read, read.snapshot as SnapshotCandidate.Supported)
        }
        assertEquals(med.nameEn, reader.ready().catalog.medications.single().nameEn)
        assertFalse(reader.ready().admin)
        owner.admin.saveMedication(med.copy(note = "Verified second publication")); sharing.publish()
        assertTrue("Media replacement must verify", sharing.status.value.verified)
        SnapshotRefreshRepository(reader.factory, reader.secure, readerReads, reader.requirements).refresh()
        assertEquals("Verified second publication", reader.ready().catalog.medications.single().note)
        assertEquals(2, owned.size)
        val backup = com.aqaqeer.app.backup.BackupRepository(owner.context, owner.factory, owner.secure,
            owner.admin, owner.requirements, java.time.Clock.systemUTC())
        val backupFile = File(owner.root, "live-recovery.aqbak")
        backup.export(Uri.fromFile(backupFile), "recovery passphrase".toCharArray(), "recovery passphrase".toCharArray())
        owner.admin.saveMedication(owner.ready().catalog.medications.single().copy(note = "after backup"))
        backup.restore(Uri.fromFile(backupFile), "recovery passphrase".toCharArray(), "صيدلية الاختبار", true)
        assertFalse(owner.ready().admin)
        owner.admin.promote("123456".toCharArray(), "محرر الاستعادة")
        sharing.confirmRestoredConnection(token, target.title)
        sharing.reconcileRestore(false, "")
        assertFalse("Live restore reconciliation must clear pause", owner.ready().restorePaused)
        assertEquals("Verified second publication", owner.ready().catalog.medications.single().note)
        assertEquals("Reconciliation must not upload", 2, owned.size)

        // Explicit manual republication after restore must extend the authenticated live lineage.
        val previous=owner.ready().catalog.medications.single()
        owner.admin.saveMedication(previous.copy(note="Manual republication after live reconciliation"))
        sharing.publish();assertTrue("Manual republication must verify",sharing.status.value.verified)
        SnapshotRefreshRepository(reader.factory,reader.secure,readerReads,reader.requirements).refresh()
        assertEquals("Manual republication after live reconciliation",reader.ready().catalog.medications.single().note)

        // Use a synthetic pharmacy PIN only; the actual bot token is never revoked here.
        reader.admin.promote("123456".toCharArray(),"قارئ اختبار مفوض")
        val pin=com.aqaqeer.app.maintenance.PinChanges(owner.admin,com.aqaqeer.app.maintenance.MaintenanceStore(owner.secure))
        pin.change("123456".toCharArray(),"654321".toCharArray(),"654321".toCharArray())
        sharing.publish();assertTrue(sharing.status.value.verified)
        assertTrue("Exact initiating device keeps its verified PIN authorization",owner.ready().admin)
        SnapshotRefreshRepository(reader.factory,reader.secure,readerReads,reader.requirements).refresh()
        assertFalse("A peer observing the changed live PIN must lose administration",reader.ready().admin)
        val replacementFile=File(owner.root,"replacement-live.aqz")
        sharing.export(Uri.fromFile(replacementFile),"12345678".toCharArray())
        val readerBackup=com.aqaqeer.app.backup.BackupRepository(reader.context,reader.factory,reader.secure,
            reader.admin,reader.requirements,java.time.Clock.systemUTC())
        val replacement=com.aqaqeer.app.maintenance.MaintenanceCoordinator(
            com.aqaqeer.app.maintenance.MaintenanceStore(reader.secure),com.aqaqeer.app.maintenance.MaintenanceStage(reader.context),
            reader.admin,readerReads,transport,writer,TelegramSnapshotReadTransport(),reader.requirements,readerBackup,reader.context)
        val uploadsBefore=owned.size
        replacement.stageReplacement(Uri.fromFile(replacementFile),"12345678".toCharArray())
        replacement.verifyReplacement();replacement.activateReplacement();replacement.finishReaderReplacement()
        assertFalse("Real connection-only replacement must not promote the reader",reader.ready().admin)
        assertFalse(reader.ready().maintenancePaused)
        assertEquals(owner.ready().catalog.pharmacy!!.pharmacyId,reader.ready().catalog.pharmacy!!.pharmacyId)
        assertEquals("Reader connection replacement must not upload",uploadsBefore,owned.size)
    }

    @Test fun editDuringConflictResolvedUploadCanBeReviewedAfterVerifiedRemoteWrite() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); val med = medicine(p); val remote = Remote(); val sharing = p.sharing(remote)
        p.setup(remote, sharing); sharing.publish()
        val db = p.factory.open()
        val baseline = try { Json.decodeFromString<SnapshotWire>(db.catalogDao().snapshotState()!!.confirmedPayloadJson!!).supported() }
            finally { db.close() }
        val changed = SnapshotCandidate.Supported(baseline.compatibility, 2, newId(), baseline.publishId, 1,
            Instant.now(), baseline.publishedBy, baseline.catalog.copy(medications = baseline.catalog.medications.map { it.copy(note = "theirs") }))
        val active = PendingConnectionStore(p.secure, "active-connection")
        active.withCandidate(active.info()!!.id) { c ->
            val secrets = c.copySecrets()
            try { remote.files["remote-change"] = EnvelopeWriter.snapshot(Json.decodeFromString(changed.confirmedJson()), secrets.snapshotKey) }
            finally { secrets.snapshotKey.fill(0) }
        }
        remote.current = SnapshotDocument(-123L, 10, "remote-change", "unique-remote-change", changed.publication)
        p.admin.saveMedication(med.copy(note = "mine"))
        sharing.publish(); assertEquals(1, sharing.status.value.conflicts.size)
        remote.duringUpload = { p.admin.saveMedication(med.copy(note = "later")) }
        sharing.publish(sharing.status.value.conflicts.associate { it.key to false })
        assertTrue(p.ready().uncertain); assertEquals(1, sharing.status.value.conflicts.size)
        remote.duringUpload = {}
        sharing.publish(sharing.status.value.conflicts.associate { it.key to true })
        assertTrue(sharing.status.value.verified); assertFalse(p.ready().uncertain)
        assertEquals("later", p.ready().catalog.medications.single().note); assertEquals(1, p.ready().pendingCount)
        sharing.publish(); assertTrue(sharing.status.value.verified); assertEquals(0, p.ready().pendingCount)
    }

    @Test fun pendingOrderUsesInsertionRatherThanDeviceTimeOrUuid() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val p = profile(); p.create(); val med = medicine(p)
        val db = p.factory.open()
        try {
            val dao = db.catalogDao()
            dao.deletePending(dao.pending().map { it.id })
            val first = PendingOperation(newId(), newId(), "medication", med.id, "note", "null", "\"first\"",
                med.updatedBy.id, med.updatedBy.name, Instant.parse("2026-11-10T12:00:00Z"), null)
            val second = first.copy(id = newId(), transactionId = newId(), baseValueJson = "\"first\"",
                intendedValueJson = "\"second\"", authoredAt = first.authoredAt.minusSeconds(500))
            dao.putPending(listOf(first, second))
            assertEquals(listOf(first.id, second.id), dao.pending().map { it.id })
            val base = dao.catalog()
            val local = base.copy(medications = listOf(med.copy(note = "second")))
            assertEquals("second", CatalogEdits.merge(base, local, base, dao.pending()).catalog.medications.single().note)
        } finally { db.close() }
    }

}
