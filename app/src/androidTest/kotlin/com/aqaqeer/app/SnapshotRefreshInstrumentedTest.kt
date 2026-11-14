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
class SnapshotRefreshInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "refresh_${UUID.randomUUID()}"
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

    private suspend fun activate() {
        val info=stage(); repository().activate(info.id)
    }
    private fun refresh(transport: SnapshotReadTransport = transport(), at: java.time.Instant = checkedAt.plusSeconds(60)) =
        SnapshotRefreshRepository(factory,store,SnapshotReadClient(transport,EnvelopeReader(root),
            clock=Clock.fixed(at,ZoneOffset.UTC)),requirements)
    private fun next(catalog: CatalogSnapshot = F.catalog(), revision: Int = 2, id: String = F.otherPharmacyId,
        parent: String = F.publishId): JsonObject = JsonObject(F.snapshot(catalog)+mapOf(
            "revision" to JsonPrimitive(revision),"publishId" to JsonPrimitive(id),
            "parentPublishId" to JsonPrimitive(parent),"basedOnRevision" to JsonPrimitive(revision-1)))
    private fun remote(payload: JsonObject, identity: String = "replacement", pin: suspend () -> Unit = {}) =
        transport({ payload }, { pin(); document.copy(fileId=identity,publication=SnapshotPublication(
            payload.getValue("revision").jsonPrimitive.long,payload.getValue("publishId").jsonPrimitive.content,
            java.time.Instant.parse(payload.getValue("publishedAt").jsonPrimitive.content))) })
    private suspend fun state(): SnapshotState {
        val db=factory.open()
        try { return db.catalogDao().snapshotState()!! } finally { db.close() }
    }
    private suspend fun history(): List<HistoryEntry> {
        val db=factory.open()
        try { return db.catalogDao().history() } finally { db.close() }
    }

    @Test fun demoAndUnconfiguredRefreshRejectBeforeNetwork() = runBlocking {
        var calls=0
        val error=rejects<RefreshException> { refresh(transport(pin={ calls++; document })).refresh() }
        assertEquals(if (BuildConfig.IS_DEMO) RefreshError.DEMO else RefreshError.UNCONFIGURED,error.error)
        assertEquals(0,calls)
    }
    @Test fun unchangedRefreshUsesValidatedCacheUpdatesOnlyLocalTimeAndPreservesHistory() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate(); var downloads=0; var pins=0
        val remote=FakeSnapshotReadTransport(BotIdentity(123456),{ pins++; document },{
            downloads++; throw AssertionError("Unchanged document downloaded")
        })
        assertEquals(RefreshOutcome.UNCHANGED,refresh(remote).refresh())
        assertEquals(0,downloads); assertEquals(2,pins); assertTrue(history().isEmpty())
        assertEquals(checkedAt.plusSeconds(60),ready().lastSuccessfulRefreshAt)
        assertEquals(F.time,ready().publishedAt); assertEquals(F.catalog(),ready().catalog)
    }
    @Test fun descendantAppliesAllFieldsAndRecordsOnlyObservedChangesOnce() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val changed=F.catalog().copy(medications=F.catalog().medications.map { it.copy(nameEn="Remote name",note="New note") })
        val repository=refresh(remote(next(changed)))
        assertEquals(RefreshOutcome.APPLIED,repository.refresh()); assertEquals(changed,ready().catalog)
        assertEquals(setOf("nameEn","note"),history().filter { it.entityType=="medication" }.map { it.field }.toSet())
        history().filter { it.entityType=="medication" }.forEach {
            assertEquals(2L,it.publishedRevision); assertNull(it.editedAt); assertNull(it.editorId)
        }
        val publication=history().single { it.field=="publication" }
        assertEquals(F.editor.id,publication.editorId); assertEquals(F.time,publication.editedAt)
        assertEquals(RefreshOutcome.UNCHANGED,repository.refresh()); assertEquals(3,history().size)
        assertEquals(F.otherPharmacyId,state().publishId)
    }
    @Test fun sameMessageMediaChangeCannotReuseSamePublicationWithDifferentContents() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val original=state(); val changed=F.catalog().copy(medications=F.catalog().medications.map { it.copy(note="Reused publication ID") })
        val repository=refresh(remote(F.snapshot(changed)))
        assertEquals(RefreshError.PUBLICATION_RECOVERY,rejects<RefreshException> { repository.refresh() }.error)
        assertEquals(F.catalog(),ready().catalog); assertEquals(original.lastSuccessfulSyncAt,state().lastSuccessfulSyncAt)
        val recovery=history().single(); assertEquals("recoveryCandidate",recovery.field)
        assertTrue(recovery.newValue!!.contains("Reused publication ID"))
        rejects<RefreshException> { repository.refresh() }; assertEquals(1,history().size)
    }
    @Test fun branchesGapsAndRegressionAreDurablyRetainedWithoutReplacingTheBase() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val original=state()
        for (payload in listOf(next(revision=1,parent=F.editor.id).let { JsonObject(it+("parentPublishId" to JsonNull)) },
            next(revision=3,parent=F.editor.id))) {
            assertEquals(RefreshError.PUBLICATION_RECOVERY,rejects<RefreshException> { refresh(remote(payload)).refresh() }.error)
            assertEquals(original.confirmedPayloadJson,state().confirmedPayloadJson)
        }
        assertEquals(2,history().size)
        refresh(remote(next())).refresh()
        assertEquals(RefreshError.PUBLICATION_RECOVERY,rejects<RefreshException> { refresh(transport()).refresh() }.error)
        assertEquals(2L,state().revision); assertEquals(F.otherPharmacyId,state().publishId)
    }
    @Test fun failedNetworkAuthenticationAndReadbackLeaveCatalogHistoryAndRefreshTimeUntouched() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate(); val original=state()
        var calls=0
        val failures=listOf(
            transport(pin={ throw SnapshotReadException(SnapshotReadError.NETWORK) }),
            transport({ F.snapshot(F.catalog().copy(pharmacy=F.catalog().pharmacy!!.copy(pharmacyId=F.otherPharmacyId))) },
                { document.copy(fileId="wrong") }),
            transport(pin={ if (++calls==1) document else document.copy(fileId="changed") }))
        for (remote in failures) {
            rejects<Exception> { refresh(remote).refresh() }
            assertEquals(original,state()); assertEquals(F.catalog(),ready().catalog); assertTrue(history().isEmpty())
        }
    }
    @Test fun unsupportedObservationRetainsUsableCacheAndPersistsRestrictionsAcrossRestart() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate(); val original=state()
        assertEquals(RefreshError.UPDATE_REQUIRED,rejects<RefreshException> {
            refresh(remote(F.snapshot(schema=2,minimum=100))).refresh()
        }.error)
        assertEquals(original.confirmedPayloadJson,state().confirmedPayloadJson)
        assertEquals(original.lastSuccessfulSyncAt,state().lastSuccessfulSyncAt)
        assertTrue(ready(CatalogRepository(factory,store,SnapshotRequirementsStore(store),com.aqaqeer.app.maintenance.MaintenanceStage(context))).requiresUpdate)
        assertEquals(RefreshError.UPDATE_REQUIRED,rejects<RefreshException> { refresh(transport()).refresh() }.error)
        assertEquals(SnapshotRequirements(2,100),requirements.read(F.pharmacyId)); assertEquals(F.catalog(),ready().catalog)
    }
    @Test fun pendingAndUncertainAttemptsArePreservedAndChangedRemoteCandidateIsRetained() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate(); val original=state()
        val db=factory.open()
        try {
            val sql=db.openHelper.writableDatabase
            val fixtures=listOf(
                "pending_operations" to "INSERT INTO pending_operations (id,transactionId,entityType,targetId,field,baseValueJson,intendedValueJson,authorId,authorName,authoredAt) VALUES ('op','tx','medication','target','nameEn','null','null','author','name',1)",
                "publish_attempts" to "INSERT INTO publish_attempts (publishId,candidatePayloadJson,includedOperationIdsJson,transportPhase) VALUES ('attempt','{}','[]','UNCERTAIN')")
            for ((table,insert) in fixtures) {
                sql.execSQL(insert)
                assertEquals(RefreshError.LOCAL_WORK,rejects<RefreshException> { refresh(remote(next())).refresh() }.error)
                sql.query("SELECT COUNT(*) FROM $table").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(1,cursor.getInt(0)) }
                assertEquals(original.confirmedPayloadJson,state().confirmedPayloadJson); assertEquals(F.catalog(),ready().catalog)
                assertEquals(RefreshOutcome.UNCHANGED,refresh().refresh())
                sql.execSQL("DELETE FROM $table")
            }
            assertEquals(1,history().count { it.field=="recoveryCandidate" })
        } finally { db.close() }
    }
    @Test fun sqlFailureAfterHistoryInsertionRollsBackCatalogHistoryAndTimestamps() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate(); val original=state()
        val changed=F.catalog().copy(medications=F.catalog().medications.map { it.copy(note="New data") })
        val db=factory.open()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER refresh_test_failure BEFORE INSERT ON medications BEGIN SELECT RAISE(ABORT,'injected failure'); END")
            rejects<Exception> { refresh(remote(next(changed))).refresh() }
            assertEquals(original,state()); assertEquals(F.catalog(),ready().catalog); assertTrue(history().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER refresh_test_failure")
            assertEquals(RefreshOutcome.APPLIED,refresh(remote(next(changed))).refresh())
            assertEquals(changed,ready().catalog); assertEquals(2,history().size)
        } finally { db.close() }
    }
    @Test fun overlappingRefreshRequestsShareNetworkAndOneCancelledWaiterCannotAbortTheOther() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); var pins=0
        val repository=refresh(remote(next(),pin={ if (++pins==1) { entered.complete(Unit); release.await() } }))
        val first=async { repository.refresh() }; entered.await()
        val second=async(start=CoroutineStart.UNDISPATCHED) { repository.refresh() }
        first.cancelAndJoin(); release.complete(Unit)
        assertEquals(RefreshOutcome.APPLIED,second.await()); assertEquals(2,pins); assertEquals(2L,state().revision)
    }
    @Test fun localStateChangedDuringNetworkCannotBeSilentlyOverwritten() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>(); var pins=0
        val request=async { try { refresh(remote(next(),pin={ if (++pins==1) { entered.complete(Unit); release.await() } })).refresh(); null }
            catch (error: RefreshException) { error.error } }
        entered.await()
        val db=factory.open()
        try { db.catalogDao().updateSnapshotState(state().copy(documentFileId="local replacement")) } finally { db.close() }
        release.complete(Unit)
        assertEquals(RefreshError.LOCAL_STATE_CHANGED,request.await()); assertEquals("local replacement",state().documentFileId)
        assertEquals(F.catalog(),ready().catalog)
    }
    @Test fun changedPinVerifierRevokesAuthorizationButPreservesDeviceIdentity() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val db=factory.open()
        val device=try { db.catalogDao().deviceIdentity()!!.also { db.catalogDao().updateDeviceIdentity(it.copy(adminMode=true)) } } finally { db.close() }
        val changed=F.catalog().copy(pharmacy=F.catalog().pharmacy!!.copy(adminPinVerifier=PinVerifier(F.b64(ByteArray(16){1}),200_000,F.b64(ByteArray(32){2}))))
        refresh(remote(next(changed))).refresh()
        val check=factory.open()
        try { assertEquals(device.id,check.catalogDao().deviceIdentity()!!.id); assertFalse(check.catalogDao().deviceIdentity()!!.adminMode) }
        finally { check.close() }
    }
    @Test fun remoteDeletionReplacesAvailabilityAndTombstonesWhileKeepingPriorLocalHistory() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO); activate()
        val changed=F.catalog().copy(medications=F.catalog().medications.map { it.copy(note="Observed before deletion") })
        refresh(remote(next(changed))).refresh()
        assertEquals(2,history().size)
        val deleted=changed.copy(medications=emptyList(),availability=emptyList(),tombstones=listOf(
            Tombstone("medication",changed.medications.single().id,F.time,F.otherPharmacyId)))
        assertEquals(RefreshOutcome.APPLIED,refresh(remote(next(deleted,3,F.editor.id,F.otherPharmacyId))).refresh())
        assertEquals(deleted,ready().catalog); assertEquals(3L,state().revision)
        assertEquals(6,history().size)
        assertEquals("\"Observed before deletion\"",history().single { it.field=="note" }.newValue)
    }

}
