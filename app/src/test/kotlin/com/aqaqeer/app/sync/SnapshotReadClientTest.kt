package com.aqaqeer.app.sync

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset

class SnapshotReadClientTest {
    @get:Rule val temporary = TemporaryFolder()
    private val publication = SnapshotPublication(1,F.publishId,F.time)
    private val document get() = SnapshotDocument(F.groupId,12,"public-file","public-unique",publication)
    private fun client(transport: SnapshotReadTransport) = SnapshotReadClient(transport,
        EnvelopeReader(temporary.root) { Long.MAX_VALUE }, PayloadReader { Long.MAX_VALUE }, Clock.fixed(F.time.plusSeconds(60),ZoneOffset.UTC))
    private class Tracked(bytes: ByteArray) : ByteArrayInputStream(bytes) { var closed = false; override fun close() { closed = true; super.close() } }
    private suspend fun rejects(code: SnapshotReadError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: SnapshotReadException) { assertEquals(code,e.error) }
    }
    @Test fun verifiesAuthenticatedCatalogAndPublicationWithSeparateObservationTime() = runBlocking {
        var pins = 0; val stream = Tracked(F.snapshotEnvelope())
        val transport = FakeSnapshotReadTransport(BotIdentity(123456),{ pins++; document },{ stream })
        F.candidate().use { candidate ->
            val result = client(transport).read(candidate,3)
            assertEquals(2,pins); assertTrue(stream.closed)
            assertEquals(F.time.plusSeconds(60),result.observedAt)
            assertEquals(F.catalog(),(result.snapshot as SnapshotCandidate.Supported).catalog)
            assertArrayEquals(F.key,candidate.copySecrets().snapshotKey)
        }
        assertFalse(temporary.root.walk().any { it.extension == "stage" })
    }
    @Test fun captionMismatchFailsBeforePinReadback() = runBlocking {
        var pins = 0; val stream = Tracked(F.snapshotEnvelope())
        val bad = document.copy(publication = publication.copy(revision = 2))
        val transport = FakeSnapshotReadTransport(BotIdentity(1),{ pins++; bad },{ stream })
        F.candidate().use { rejects(SnapshotReadError.CAPTION_MISMATCH) { client(transport).read(it,3) } }
        assertEquals(1,pins); assertTrue(stream.closed)
    }
    @Test fun detectsMediaOrCaptionReplacementEvenOnSamePinnedMessage() = runBlocking {
        for (after in listOf(document.copy(fileId = "changed"),document.copy(fileUniqueId = "changed"),
            document.copy(messageId = 13),document.copy(publication = publication.copy(publishedAt = F.time.plusSeconds(1))))) {
            var pins = 0
            val transport = FakeSnapshotReadTransport(BotIdentity(1),{ if (++pins == 1) document else after },{ F.snapshotEnvelope().inputStream() })
            F.candidate().use { rejects(SnapshotReadError.CHANGED_DURING_READ) { client(transport).read(it,3) } }
        }
    }
    @Test fun authenticatedFutureSchemaStillRequiresCaptionAgreementAndReadback() = runBlocking {
        val future = JsonObject(F.snapshot(schema = 2) + ("medications" to JsonPrimitive("future layout")))
        var pins = 0
        val transport = FakeSnapshotReadTransport(BotIdentity(1),{ pins++; document },{ F.snapshotEnvelope(future).inputStream() })
        F.candidate().use { assertTrue(client(transport).read(it,3).snapshot is SnapshotCandidate.RequiresUpdate) }
        assertEquals(2,pins)
        val bad = FakeSnapshotReadTransport(BotIdentity(1),{ document.copy(publication = publication.copy(revision = 2)) },{ F.snapshotEnvelope(future).inputStream() })
        F.candidate().use { rejects(SnapshotReadError.CAPTION_MISMATCH) { client(bad).read(it,3) } }
    }
    @Test fun wrongPharmacyAndWrongKeyCannotReturnAnyObservation() = runBlocking {
        val wrong = F.snapshot(F.catalog().copy(pharmacy = F.catalog().pharmacy!!.copy(pharmacyId = F.otherPharmacyId)))
        val transport = FakeSnapshotReadTransport(BotIdentity(1),{ document },{ F.snapshotEnvelope(wrong).inputStream() })
        F.candidate().use {
            try { client(transport).read(it,3); fail("Wrong pharmacy accepted") }
            catch (e: PayloadException) { assertEquals(PayloadError.WRONG_PHARMACY,e.error) }
        }
        val candidate = ConnectionCandidate(F.pharmacyId,"fixture",F.time,F.editor,F.token,F.groupId,ByteArray(32){99},1)
        val valid = FakeSnapshotReadTransport(BotIdentity(1),{ document },{ F.snapshotEnvelope().inputStream() })
        candidate.use {
            try { client(valid).read(it,3); fail("Wrong key accepted") }
            catch (e: EnvelopeException) { assertEquals(EnvelopeError.AUTHENTICATION,e.error) }
        }
    }
    @Test fun downloadFailureClosesStreamSanitizesIoAndReleasesReadGate() = runBlocking {
        val broken = object : java.io.InputStream() {
            var closed = false
            override fun read(): Int = throw IOException("secret URL must not escape")
            override fun close() { closed = true }
        }
        var downloads = 0
        val transport = FakeSnapshotReadTransport(BotIdentity(1),{ document },{ if (++downloads == 1) broken else F.snapshotEnvelope().inputStream() })
        val reader = client(transport)
        F.candidate().use {
            try { reader.read(it,3); fail("IO accepted") } catch (e: EnvelopeException) { assertEquals("IO",e.message); assertNull(e.cause) }
            assertTrue(broken.closed); assertNotNull(reader.read(it,3))
        }
    }
    @Test fun cancellationDuringTransportReadPreservesCallerCandidateAndAllowsRetry() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var pins = 0
        val transport = FakeSnapshotReadTransport(BotIdentity(1),{
            if (++pins == 1) { entered.complete(Unit); awaitCancellation() }
            document
        },{ F.snapshotEnvelope().inputStream() })
        val reader = client(transport)
        F.candidate().use { candidate ->
            val operation = launch { reader.read(candidate,3) }
            entered.await(); operation.cancelAndJoin()
            assertArrayEquals(F.key,candidate.copySecrets().snapshotKey)
            assertNotNull(reader.read(candidate,3))
        }
    }
    @Test fun captionCanonicalizationRejectsAmbiguousOrExtraFields() {
        val caption = SnapshotCaption.encode(publication)
        assertEquals(publication,SnapshotCaption.decode(caption))
        for (value in listOf(caption + "\n",caption.replace("revision=1","revision=01"),caption.replace("revision=1","revision=+1"),
            caption.replace("Z","+00:00"), caption.replace(F.publishId,"not-a-uuid"))) {
            assertThrows(SnapshotReadException::class.java) { SnapshotCaption.decode(value) }
        }
    }
    private suspend fun cache() = ValidatedSnapshotCache.fromConfirmed(document,F.pharmacyId,1,3,F.snapshot().toString())!!
    @Test fun unchangedValidatedDocumentAvoidsDownloadButStillRechecksPin() = runBlocking {
        var pins=0
        val reader=client(FakeSnapshotReadTransport(BotIdentity(1),{ pins++; document },{ throw AssertionError("Unchanged document downloaded") }))
        F.candidate().use { candidate ->
            val result=reader.readWithCache(candidate,3,cache())
            assertEquals(F.catalog(),(result.snapshot as SnapshotCandidate.Supported).catalog)
            assertEquals(2,pins); assertEquals(F.time.plusSeconds(60),result.observedAt)
        }
    }
    @Test fun mediaIdentityOrCaptionChangeForcesDownloadEvenOnTheSameMessage() = runBlocking {
        var downloads=0
        val reader=client(FakeSnapshotReadTransport(BotIdentity(1),{ document.copy(fileUniqueId="replacement") },{
            downloads++; F.snapshotEnvelope().inputStream()
        }))
        F.candidate().use { reader.readWithCache(it,3,cache()) }
        assertEquals(1,downloads)
    }
    @Test fun cachedShortcutRejectsChangedPinAndInvalidConfirmedPayload() = runBlocking {
        var pins=0
        val reader=client(FakeSnapshotReadTransport(BotIdentity(1),{ if (++pins==1) document else document.copy(fileId="changed") },{
            throw AssertionError("Cache downloaded")
        }))
        F.candidate().use { candidate -> rejects(SnapshotReadError.CHANGED_DURING_READ) { reader.readWithCache(candidate,3,cache()) } }
        try { ValidatedSnapshotCache.fromConfirmed(document,F.otherPharmacyId,1,3,F.snapshot().toString()); fail("Wrong cache pharmacy") }
        catch (error: PayloadException) { assertEquals(PayloadError.WRONG_PHARMACY,error.error) }
    }

}
