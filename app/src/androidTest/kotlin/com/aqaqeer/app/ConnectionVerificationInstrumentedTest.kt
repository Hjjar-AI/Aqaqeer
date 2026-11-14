package com.aqaqeer.app

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ConnectionVerificationInstrumentedTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "m3test_${UUID.randomUUID()}"
    private val root = File(base.noBackupFilesDir,namespace).apply { mkdirs() }
    private val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
    private val protected = KeystoreSecureStore(context,namespace)
    private var afterWrite: (String) -> Unit = { }
    private val store = object : SecureStore {
        override fun read(name: String) = protected.read(name)
        override fun write(name: String, value: ByteArray) { protected.write(name,value); afterWrite(name) }
    }
    private val factory = DatabaseFactory(context,namespace)
    private val catalog = CatalogRepository(factory,store,SnapshotRequirementsStore(store),com.aqaqeer.app.maintenance.MaintenanceStage(context))
    private val pending = PendingConnectionStore(store)
    private val requirements = SnapshotRequirementsStore(store)
    private val document = SnapshotDocument(F.groupId,12,"public-file","public-unique",SnapshotPublication(1,F.publishId,F.time))
    private fun stage() = F.candidate().use { pending.stage(it) }
    private fun repository(transport: SnapshotReadTransport): ConnectionImportRepository = ConnectionImportRepository(context,catalog,store,
        SnapshotReadClient(transport,EnvelopeReader(root)),requirements)
    private fun transport(payload: () -> JsonObject = { F.snapshot() }, pin: suspend () -> SnapshotDocument = { document }) =
        FakeSnapshotReadTransport(BotIdentity(123456),{ pin() },{ F.snapshotEnvelope(payload()).inputStream() })
    @After fun cleanupOnlyOwnedProfile() {
        context.deleteDatabase(factory.databaseName)
        root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(protected.keyAlias) }
    }
    @Test fun startupImportSummaryMakesNoNetworkCallAndConfiguredOrDemoCommandsAreRejected() = runBlocking {
        val info = stage(); var calls = 0
        val repository = repository(transport(pin = { calls++; document }))
        val state = repository.loadPending()
        if (BuildConfig.IS_DEMO) assertNull(state.info) else {
            assertEquals(info,state.info)
            catalog.observe().first { it is CatalogState.Ready }
            repository(transport()).activate(info.id)
        }
        try { repository.verify(info.id); fail("Configured/demo verification accepted") }
        catch (e: ConnectionImportException) { assertEquals(if (BuildConfig.IS_DEMO) ConnectionImportError.DEMO else ConnectionImportError.CONFIGURED,e.error) }
        assertEquals(0,calls)
        if (BuildConfig.IS_DEMO) assertEquals(info,pending.info()) else assertNull(pending.info())
    }
    @Test fun supportedVerificationReturnsMetadataWithoutActivatingPharmacy() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info = stage(); val repository = repository(transport())
        val result = repository.verify(info.id)
        assertEquals(info.id,result.pendingId); assertEquals("صيدلية الاختبار",result.pharmacyName)
        assertEquals(1,result.medicationCount); assertFalse(result.requiresUpdate)
        val ready = catalog.observe().first { it is CatalogState.Ready } as CatalogState.Ready
        assertNull(ready.catalog.pharmacy); assertTrue(ready.catalog.medications.isEmpty())
        assertEquals(info,repository.loadPending().info)
        assertEquals(info,PendingConnectionStore(KeystoreSecureStore(context,namespace)).info())
    }
    @Test fun actualContentUriImportRejectsWrongPasswordThenStagesWithoutNetwork() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".m3-import"
        val uri = Uri.parse("content://$authority/valid")
        var networkCalls = 0
        val repository = repository(transport(pin = { networkCalls++; document }))
        try { repository.stage(uri,"654321".toCharArray()); fail("Wrong password accepted") }
        catch (e: EnvelopeException) { assertEquals(EnvelopeError.AUTHENTICATION,e.error) }
        assertNull(pending.info())
        val state = repository.stage(uri,"١٢٣٤٥٦".toCharArray())
        assertEquals(F.pharmacyId,state.info!!.pharmacyId); assertEquals(0,networkCalls)
        try { repository.stage(uri,"123456".toCharArray()); fail("Pending connection overwritten") }
        catch (e: ConnectionImportException) { assertEquals(ConnectionImportError.PENDING_EXISTS,e.error) }
        assertEquals(state.info,pending.info()); assertFalse(root.walk().any { it.extension == "stage" })
    }
    @Test fun actualProviderIgnoresFalseSizeAndRejectsInvalidAndOversizedInputWithoutSaving() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val authority = InstrumentationRegistry.getInstrumentation().context.packageName + ".m3-import"
        val repository = repository(transport())
        for ((path,error) in listOf("invalid" to EnvelopeError.INVALID_FORMAT,"oversize" to EnvelopeError.TOO_LARGE)) {
            try { repository.stage(Uri.parse("content://$authority/$path"),"123456".toCharArray()); fail("Invalid input accepted") }
            catch (e: EnvelopeException) { assertEquals(error,e.error) }
            assertNull(pending.info()); assertFalse(root.walk().any { it.extension == "stage" })
        }
    }
    @Test fun futureRequirementsSurviveProtectedReopenDiscardReimportAndOlderLiveSnapshot() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        var payload = F.snapshot(schema=2,minimum=100)
        val repository = repository(transport(payload = { payload }))
        val first = stage()
        assertTrue(repository.verify(first.id).requiresUpdate)
        assertEquals(SnapshotRequirements(2,100),SnapshotRequirementsStore(KeystoreSecureStore(context,namespace)).read(F.pharmacyId))
        repository.discard(first.id); val next = stage(); payload = F.snapshot()
        assertTrue(repository.verify(next.id).requiresUpdate)
        assertEquals(SnapshotRequirements(2,100),repository.loadPending().requirements)
    }
    @Test fun networkWrongPharmacyCaptionAndMediaRaceDoNotPersistRequirementsOrLosePendingState() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info = stage()
        val wrong = F.snapshot(F.catalog().copy(pharmacy=F.catalog().pharmacy!!.copy(pharmacyId=F.otherPharmacyId)))
        var pins = 0
        val failures = listOf(
            transport(pin={ throw SnapshotReadException(SnapshotReadError.NETWORK) }),
            transport(payload={ wrong }),
            transport(pin={ document.copy(publication=document.publication.copy(revision=2)) }),
            transport(pin={ if (++pins == 1) document else document.copy(fileId="replacement") }))
        for (transport in failures) {
            try { repository(transport).verify(info.id); fail("Invalid observation accepted") }
            catch (e: Exception) { assertTrue(e is SnapshotReadException || e is PayloadException) }
            assertEquals(info,pending.info()); assertNull(requirements.read(F.pharmacyId))
        }
    }
    @Test fun stalePendingIdCannotStartNetworkVerification() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        stage(); var calls = 0
        try { repository(transport(pin={ calls++; document })).verify("stale"); fail("Stale pending accepted") }
        catch (_: PendingConnectionChanged) { }
        assertEquals(0,calls)
    }
    @Test fun cancellationBeforeObservationPreservesPendingAndClosesCredentialScope() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); val entered=CompletableDeferred<Unit>()
        val repository=repository(transport(pin={ entered.complete(Unit); awaitCancellation() }))
        val operation=launch { repository.verify(info.id) }
        entered.await(); operation.cancelAndJoin()
        assertEquals(info,pending.info()); assertNull(requirements.read(F.pharmacyId))
        assertFalse(root.walk().any { it.extension == "stage" })
    }
    @Test fun cancellationAtSecurityWriteRetainsAuthenticatedUpdateRequirements() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); lateinit var operation: Job
        afterWrite={ name -> if (name.startsWith("snapshot-requirements-")) operation.cancel() }
        val repository=repository(transport(payload={ F.snapshot(schema=2,minimum=100) }))
        operation=launch(start=CoroutineStart.LAZY) { repository.verify(info.id) }
        operation.start(); operation.join()
        assertTrue(operation.isCancelled); assertEquals(info,pending.info())
        assertEquals(SnapshotRequirements(2,100),requirements.read(F.pharmacyId))
    }
    @Test fun corruptProtectedRequirementBlocksFreshObservationWithoutReplacingEitherRecord() = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val info=stage(); store.write("snapshot-requirements-${F.pharmacyId}",byteArrayOf(1))
        var calls=0
        try { repository(transport(pin={ calls++; document })).verify(info.id); fail("Corrupt requirement bypassed") }
        catch (_: StorageRecoveryRequired) { }
        assertEquals(0,calls); assertEquals(info,pending.info())
        assertArrayEquals(byteArrayOf(1),store.read("snapshot-requirements-${F.pharmacyId}"))
    }
}
