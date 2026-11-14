package com.aqaqeer.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReaderActivationInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "activation_${UUID.randomUUID()}"
    private val root = File(base.noBackupFilesDir, namespace).apply { mkdirs() }
    private val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
    private val protected = KeystoreSecureStore(context, namespace)
    private var beforeWrite: (String, ByteArray) -> Unit = { _, _ -> }
    private var afterWrite: (String) -> Unit = {}
    private val store = object : SecureStore {
        override fun read(name: String) = protected.read(name)
        override fun write(name: String, value: ByteArray) {
            beforeWrite(name, value); protected.write(name, value); afterWrite(name)
        }
    }
    private val factory = DatabaseFactory(context, namespace)
    private val requirements = SnapshotRequirementsStore(store)
    private val catalog = CatalogRepository(factory, store, requirements, com.aqaqeer.app.maintenance.MaintenanceStage(context))
    private val pending = PendingConnectionStore(store)
    private val active = PendingConnectionStore(store, "active-connection")
    private val document = SnapshotDocument(F.groupId, 12, "public-file", "public-unique", SnapshotPublication(1, F.publishId, F.time))
    private val checkedAt = F.time.plusSeconds(600)
    private fun stage() = F.candidate().use { pending.stage(it) }
    private fun transport(payload: () -> JsonObject = { F.snapshot() }, pin: suspend () -> SnapshotDocument = { document }) =
        FakeSnapshotReadTransport(BotIdentity(123456), { pin() }, { F.snapshotEnvelope(payload()).inputStream() })
    private fun reads(transport: SnapshotReadTransport) = SnapshotReadClient(transport, EnvelopeReader(root),
        clock = Clock.fixed(checkedAt, ZoneOffset.UTC))
    private fun repository(transport: SnapshotReadTransport = transport()) = ConnectionImportRepository(
        context, catalog, store, reads(transport), requirements)
    private suspend fun ready(repository: CatalogRepository = catalog): CatalogState.Ready = withTimeout(10_000) {
        repository.observe().first { it !is CatalogState.Loading } as CatalogState.Ready
    }
    private suspend fun assertUnconfigured(info: PendingConnectionInfo) {
        assertNull(ready().catalog.pharmacy)
        assertTrue(ready().catalog.medications.isEmpty())
        assertEquals(info, pending.info())
    }
    private suspend inline fun <reified T : Exception> rejects(crossinline action: suspend () -> Unit): T {
        try { action(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Exception) { if (error is T) return error else throw error }
        throw AssertionError("No exception")
    }
    @After fun cleanupOnlyOwnedProfile() {
        context.deleteDatabase(factory.databaseName)
        root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(protected.keyAlias) }
    }

    @Test fun activationRechecksLiveDataAndCommitsReaderBaselineWithoutImportedHistory() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info = stage()
        val before = ready()
        val db = factory.open()
        val identity = try { db.catalogDao().deviceIdentity()!! } finally { db.close() }
        var payload = F.snapshot()
        var pins = 0
        val repository = repository(transport({ payload }, { pins++; document }))
        assertEquals(1, repository.verify(info.id).medicationCount)
        val changed = F.catalog().copy(medications = F.catalog().medications.map { it.copy(nameEn="Fresh activation data") })
        payload = F.snapshot(changed)
        repository.activate(info.id)
        assertEquals(4, pins)
        assertNull(before.catalog.pharmacy)
        val current = ready()
        assertEquals(changed, current.catalog)
        assertEquals(checkedAt, current.lastSuccessfulRefreshAt)
        assertEquals(F.time, current.publishedAt)
        assertFalse(current.requiresUpdate)
        assertFalse(current.isDemo)
        assertNull(pending.info()); assertEquals(info, active.info())
        active.withCandidate(info.id) {
            val secrets = it.copySecrets()
            try { assertArrayEquals(F.key, secrets.snapshotKey); assertEquals(F.token, secrets.botToken) }
            finally { secrets.snapshotKey.fill(0) }
        }
        val reopened = CatalogRepository(DatabaseFactory(context,namespace),store,SnapshotRequirementsStore(store),com.aqaqeer.app.maintenance.MaintenanceStage(context))
        assertEquals(current, ready(reopened))
        val check = factory.open()
        try {
            val dao = check.catalogDao()
            val device = dao.deviceIdentity()!!
            assertEquals(identity.id, device.id); assertFalse(device.adminMode)
            assertEquals(0, dao.historyCount()); assertEquals(0, dao.pendingCount()); assertEquals(0, dao.attemptCount())
            val state = dao.snapshotState()!!
            assertEquals(F.publishId, state.publishId); assertEquals(1L, state.revision)
            assertEquals(document.messageId, state.pinnedMessageId)
            assertEquals(document.fileId, state.documentFileId); assertEquals(document.fileUniqueId, state.documentFileUniqueId)
            assertEquals(F.time, Json.decodeFromString<SnapshotWire>(state.confirmedPayloadJson!!).publishedAt)
        } finally { check.close() }
    }
    @Test fun existingObserverSeesAtomicActivationThroughSeparateDatabaseHandle() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); val initial=CompletableDeferred<Unit>(); val result=CompletableDeferred<CatalogState.Ready>()
        val observer=launch(Dispatchers.Default) {
            catalog.observe().filterIsInstance<CatalogState.Ready>().collect {
                if (it.catalog.pharmacy == null) initial.complete(Unit) else result.complete(it)
            }
        }
        try {
            withTimeout(10_000) { initial.await() }
            repository().activate(info.id)
            val current=withTimeout(10_000) { result.await() }
            assertEquals(F.catalog(),current.catalog)
        } finally { observer.cancelAndJoin() }
    }
    @Test fun futureSchemaObservationPersistsRestrictionWithoutApplyingCatalog() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage()
        val error=rejects<PayloadException> { repository(transport({ F.snapshot(schema=2) })).activate(info.id) }
        assertEquals(PayloadError.UNSUPPORTED_SCHEMA,error.error)
        assertUnconfigured(info); assertNull(active.info())
        assertEquals(SnapshotRequirements(2,1),SnapshotRequirementsStore(store).read(F.pharmacyId))
    }
    @Test fun retainedRequirementsRejectOlderLiveContentBeforeNetworkAndSurviveRestart() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); requirements.observe(F.pharmacyId,SnapshotCompatibility(1,Int.MAX_VALUE,1))
        var calls=0
        val error=rejects<PayloadException> { repository(transport(pin={ calls++; document })).activate(info.id) }
        assertEquals(PayloadError.UNSUPPORTED_SCHEMA,error.error); assertEquals(0,calls)
        assertUnconfigured(info); assertNull(active.info())
        assertTrue(SnapshotRequirementsStore(store).read(F.pharmacyId)!!.requiresUpdate(BuildConfig.VERSION_CODE))
    }
    @Test fun networkIdentityCaptionAndMediaFailuresLeaveSeedAndPendingIntact() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); var pins=0
        val wrong=F.snapshot(F.catalog().copy(pharmacy=F.catalog().pharmacy!!.copy(pharmacyId=F.otherPharmacyId)))
        val failures=listOf(
            transport(pin={ throw SnapshotReadException(SnapshotReadError.NETWORK) }),
            transport(payload={ wrong }),
            transport(payload={ JsonObject(F.snapshot()+("compatibility" to buildJsonObject {
                put("schemaVersion",1); put("minVersionCode",1); put("keyVersion",2)
            })) }),
            FakeSnapshotReadTransport(BotIdentity(123456),{ document },{
                val envelope=Json.parseToJsonElement(F.snapshotEnvelope().decodeToString()).jsonObject
                JsonObject(envelope+("tag" to JsonPrimitive(F.b64(ByteArray(16))))).toString().byteInputStream()
            }),
            transport(pin={ document.copy(publication=document.publication.copy(revision=2)) }),
            transport(pin={ if (++pins==1) document else document.copy(fileUniqueId="changed") }))
        for (transport in failures) {
            val error=rejects<Exception> { repository(transport).activate(info.id) }
            assertTrue(error is SnapshotReadException || error is PayloadException || error is EnvelopeException)
            assertUnconfigured(info); assertNull(active.info()); assertNull(requirements.read(F.pharmacyId))
        }
    }
    @Test fun staleStageAndDemoActivationCannotStartNetwork() = runBlocking {
        val info=stage(); var calls=0
        val repository=repository(transport(pin={ calls++; document }))
        if (BuildConfig.IS_DEMO) {
            assertEquals(ConnectionImportError.DEMO,rejects<ConnectionImportException> { repository.activate(info.id) }.error)
        } else rejects<PendingConnectionChanged> { repository.activate("stale") }
        assertEquals(0,calls); assertEquals(info,pending.info())
    }
    @Test fun configuredActivationIsRejectedWithoutNetworkOrCatalogReplacement() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); repository().activate(info.id)
        var calls=0
        val error=rejects<ConnectionImportException> { repository(transport(pin={ calls++; document })).activate(info.id) }
        assertEquals(ConnectionImportError.CONFIGURED,error.error); assertEquals(0,calls)
        assertEquals(F.catalog(),ready().catalog); assertEquals(info,active.info())
    }
    @Test fun cancellationDuringNetworkPreservesPendingWithoutActiveCredentials() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); val entered=CompletableDeferred<Unit>()
        val operation=launch { repository(transport(pin={ entered.complete(Unit); awaitCancellation() })).activate(info.id) }
        entered.await(); operation.cancelAndJoin()
        assertUnconfigured(info); assertNull(active.info()); assertNull(requirements.read(F.pharmacyId))
    }
    @Test fun activeCredentialWriteFailureLeavesSeedAndRetrySucceeds() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage()
        beforeWrite={ name,_ -> if (name=="active-connection") throw StorageRecoveryRequired() }
        rejects<StorageRecoveryRequired> { repository().activate(info.id) }
        assertUnconfigured(info); assertNull(active.info())
        beforeWrite={ _,_ -> }; repository().activate(info.id)
        assertEquals(F.catalog(),ready().catalog); assertNull(pending.info())
    }
    @Test fun failureAfterActiveWriteRollsBackCatalogAndCanBeRetried() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage()
        afterWrite={ name -> if (name=="active-connection") throw StorageRecoveryRequired() }
        rejects<StorageRecoveryRequired> { repository().activate(info.id) }
        assertUnconfigured(info); assertEquals(info,active.info())
        afterWrite={}; repository().activate(info.id)
        assertEquals(F.catalog(),ready().catalog); assertNull(pending.info())
    }
    @Test fun pendingCleanupFailureKeepsCommittedCatalogAndReopensWithoutNetwork() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage()
        beforeWrite={ name,value -> if (name=="pending-connection" && value.size==5) throw StorageRecoveryRequired() }
        rejects<StorageRecoveryRequired> { repository().activate(info.id) }
        assertEquals(F.catalog(),ready().catalog); assertEquals(info,pending.info()); assertEquals(info,active.info())
        beforeWrite={ _,_ -> }
        val offline=repository(transport(pin={ throw AssertionError("Cleanup must be local") }))
        assertNull(offline.loadPending().info); assertNull(pending.info()); assertEquals(F.catalog(),ready().catalog)
    }
    @Test fun cancellationAtActiveWriteCompletesCommitAndCleanupWithoutAdminPromotion() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); lateinit var operation: Job
        afterWrite={ name -> if (name=="active-connection") operation.cancel() }
        operation=launch(start=CoroutineStart.LAZY) { repository().activate(info.id) }
        operation.start(); operation.join()
        assertTrue(operation.isCancelled); assertNull(pending.info()); assertEquals(info,active.info())
        assertEquals(F.catalog(),ready().catalog)
        val db=factory.open()
        try { assertFalse(db.catalogDao().deviceIdentity()!!.adminMode) } finally { db.close() }
    }
    @Test fun startupFinishesInterruptedPostCommitCleanupLocally() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); ready()
        F.candidate().use { candidate ->
            val read=reads(transport()).read(candidate,BuildConfig.VERSION_CODE)
            requirements.observe(F.pharmacyId,read.snapshot.compatibility)
            catalog.activateReader(info.id,candidate,read,read.snapshot as SnapshotCandidate.Supported)
        }
        assertEquals(info,pending.info()); assertEquals(info,active.info())
        val offline=repository(transport(pin={ throw AssertionError("Startup must be local") }))
        assertNull(offline.loadPending().info)
        assertEquals(F.catalog(),ready().catalog)
    }
    @Test fun discardingPreCommitJournalClearsCredentialsButRetainsKnownRequirements() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); requirements.observe(F.pharmacyId,SnapshotCompatibility(1,1,1))
        F.candidate().use { active.retain(it,info.id) }
        repository().discard(info.id)
        assertNull(pending.info()); assertNull(active.info()); assertNull(ready().catalog.pharmacy)
        assertEquals(SnapshotRequirements(1,1),requirements.read(F.pharmacyId))
    }
    @Test fun reopenedCachedCatalogRetainsFutureRequirementAndNeverUsesNetwork() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); repository().activate(info.id)
        requirements.observe(F.pharmacyId,SnapshotCompatibility(2,100,1))
        val reopened=CatalogRepository(DatabaseFactory(context,namespace),store,SnapshotRequirementsStore(store),com.aqaqeer.app.maintenance.MaintenanceStage(context))
        val current=ready(reopened)
        assertEquals(F.catalog(),current.catalog); assertTrue(current.requiresUpdate)
        assertEquals(checkedAt,current.lastSuccessfulRefreshAt); assertEquals(F.time,current.publishedAt)
    }
    @Test fun corruptedActiveCredentialsFailClosedWithoutDeletingCatalog() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); repository().activate(info.id)
        store.write("active-connection",byteArrayOf(1))
        val state=catalog.observe().first { it !is CatalogState.Loading }
        assertEquals(CatalogState.RecoveryRequired,state)
        val db=factory.open()
        try { assertEquals(F.pharmacyId,db.catalogDao().pharmacy()!!.pharmacyId) } finally { db.close() }
    }
    @Test fun corruptedRequirementsRejectActivationBeforeNetwork() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); store.write("snapshot-requirements-${F.pharmacyId}",byteArrayOf(1)); var calls=0
        rejects<StorageRecoveryRequired> { repository(transport(pin={ calls++; document })).activate(info.id) }
        assertEquals(0,calls); assertUnconfigured(info); assertNull(active.info())
    }
    @Test fun initialActivationRefusesEveryKindOfExistingLocalWorkWithoutDeletingIt() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); ready()
        val db=factory.open()
        try {
            val sql=db.openHelper.writableDatabase
            val fixtures=listOf(
                "pending_operations" to "INSERT INTO pending_operations (id,transactionId,entityType,targetId,field,baseValueJson,intendedValueJson,authorId,authorName,authoredAt) VALUES ('op','tx','medication','target','nameEn','null','null','author','name',1)",
                "publish_attempts" to "INSERT INTO publish_attempts (publishId,candidatePayloadJson,includedOperationIdsJson,transportPhase) VALUES ('attempt','{}','[]','UNCERTAIN')",
                "history" to "INSERT INTO history (id,entityType,entityId,entityName,field) VALUES ('history','medication','target','name','nameEn')",
                "tombstones" to "INSERT INTO tombstones (entityType,entityId,operationId,deletedAt) VALUES ('medication','00000000-0000-4000-8000-000000000def','00000000-0000-4000-8000-000000000456',1)"
            )
            for ((table,insert) in fixtures) {
                sql.execSQL(insert)
                rejects<IllegalStateException> { repository().activate(info.id) }
                assertNull(active.info()); assertEquals(info,pending.info())
                assertNull(db.catalogDao().pharmacy())
                sql.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(1,cursor.getInt(0))
                }
                sql.execSQL("DELETE FROM $table")
            }
        } finally { db.close() }
    }

    @Test fun cancellationAfterRequirementObservationStopsBeforeCatalogCommit() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); lateinit var operation: Job
        afterWrite={ name -> if (name.startsWith("snapshot-requirements-")) operation.cancel() }
        operation=launch(start=CoroutineStart.LAZY) { repository().activate(info.id) }
        operation.start(); operation.join()
        assertTrue(operation.isCancelled); assertUnconfigured(info); assertNull(active.info())
        assertEquals(SnapshotRequirements(1,1),requirements.read(F.pharmacyId))
        afterWrite={}; repository().activate(info.id)
        assertEquals(F.catalog(),ready().catalog)
    }

    @Test fun databaseFailureAfterSeedReplacementRollsBackAllCatalogAndIdentityChanges() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); val original=ready()
        val db=factory.open()
        try {
            val identity=db.catalogDao().deviceIdentity()
            val state=db.catalogDao().snapshotState()
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER activation_test_failure BEFORE INSERT ON medications BEGIN SELECT RAISE(ABORT,'injected activation failure'); END")
            rejects<Exception> { repository().activate(info.id) }
            assertEquals(original,ready()); assertEquals(info,pending.info()); assertEquals(info,active.info())
            assertEquals(identity,db.catalogDao().deviceIdentity()); assertEquals(state,db.catalogDao().snapshotState())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER activation_test_failure")
            repository().activate(info.id)
            assertEquals(F.catalog(),ready().catalog); assertNull(pending.info())
        } finally { db.close() }
    }

}
