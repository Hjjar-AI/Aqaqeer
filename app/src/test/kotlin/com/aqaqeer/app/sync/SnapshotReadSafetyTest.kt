package com.aqaqeer.app.sync

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.ZoneOffset

/** Offline trust-boundary checks: transport calls and stream ownership are observed explicitly. */
class SnapshotReadSafetyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val document = SnapshotDocument(F.groupId, 12, "public-file", "public-unique",
        SnapshotPublication(1, F.publishId, F.time))
    private class Transport : SnapshotReadTransport {
        var bots = 0; var pins = 0; var downloads = 0; var closes = 0
        var botId = 123L
        var payload = F.snapshot()
        var pin: suspend (Int) -> SnapshotDocument = { error("Pin not configured") }
        override suspend fun bot(token: String): BotIdentity { bots++; return BotIdentity(botId) }
        override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument = pin(++pins)
        override suspend fun document(token: String, document: SnapshotDocument): java.io.InputStream {
            downloads++
            return object : ByteArrayInputStream(F.snapshotEnvelope(payload)) {
                override fun close() { closes++; super.close() }
            }
        }
    }
    private fun transport() = Transport().also { it.pin = { document } }
    private fun client(transport: Transport) = SnapshotReadClient(transport,
        EnvelopeReader(temporary.root) { Long.MAX_VALUE }, PayloadReader { Long.MAX_VALUE },
        Clock.fixed(F.time, ZoneOffset.UTC))
    private suspend fun cache() = requireNotNull(ValidatedSnapshotCache.fromConfirmed(
        document, F.pharmacyId, 1, 3, F.snapshot().toString()))
    private suspend fun rejects(expected: SnapshotReadError, action: suspend () -> Unit) {
        try { action(); fail("Expected $expected") }
        catch (error: SnapshotReadException) { assertEquals(expected, error.error) }
    }

    @Test fun invalidAppVersionRejectsBeforeAnyTransportCall(): Unit = runBlocking {
        val transport = transport(); val reader = client(transport)
        F.candidate().use { candidate ->
            for (version in listOf(0, -1, Int.MIN_VALUE)) {
                rejects(SnapshotReadError.INVALID_RESPONSE) { reader.read(candidate, version) }
            }
        }
        assertEquals(0, transport.bots); assertEquals(0, transport.pins); assertEquals(0, transport.downloads)
    }

    @Test fun nonpositiveBotIdentityRejectsBeforeReadingOrDownloadingPin(): Unit = runBlocking {
        for (id in listOf(0L, -1L)) {
            val transport = transport().also { it.botId = id }
            F.candidate().use { candidate -> rejects(SnapshotReadError.INVALID_RESPONSE) { client(transport).read(candidate, 3) } }
            assertEquals(1, transport.bots); assertEquals(0, transport.pins); assertEquals(0, transport.downloads)
        }
    }

    @Test fun foreignGroupPinRejectsBeforeOpeningAnyDocument(): Unit = runBlocking {
        val transport = transport().also { it.pin = { document.copy(groupId = F.groupId - 1) } }
        F.candidate().use { candidate -> rejects(SnapshotReadError.INVALID_RESPONSE) { client(transport).read(candidate, 3) } }
        assertEquals(1, transport.pins); assertEquals(0, transport.downloads)
    }

    @Test fun cacheCannotBypassPharmacyOrKeyVersionValidation(): Unit = runBlocking {
        val confirmed = cache()
        for ((pharmacy, version, expected) in listOf(
            Triple(F.otherPharmacyId, 1, PayloadError.WRONG_PHARMACY),
            Triple(F.pharmacyId, 2, PayloadError.WRONG_KEY_VERSION))) {
            val transport = transport()
            ConnectionCandidate(pharmacy, "fixture", F.time, F.token, F.groupId, F.key, version).use { candidate ->
                try { client(transport).readWithCache(candidate, 3, confirmed); fail("Foreign cache accepted") }
                catch (error: PayloadException) { assertEquals(expected, error.error) }
                val secrets = candidate.copySecrets()
                try { assertArrayEquals(F.key, secrets.snapshotKey) } finally { secrets.snapshotKey.fill(0) }
            }
            assertEquals(1, transport.downloads); assertEquals(1, transport.closes)
        }
    }

    @Test fun cachedSupportedSnapshotCannotHideRequiredAppUpdate(): Unit = runBlocking {
        val catalog = F.catalog().let { it.copy(pharmacy = it.pharmacy!!.copy(minVersionCode = 3)) }
        val payload = F.snapshot(catalog, minimum = 3)
        val confirmed = requireNotNull(ValidatedSnapshotCache.fromConfirmed(document, F.pharmacyId, 1, 3, payload.toString()))
        val transport = transport().also { it.payload = payload }
        F.candidate().use { candidate ->
            val result = client(transport).readWithCache(candidate, 2, confirmed)
            assertTrue(result.snapshot is SnapshotCandidate.RequiresUpdate)
            assertEquals(3, result.snapshot.compatibility.minVersionCode)
        }
        assertEquals(2, transport.pins); assertEquals(1, transport.downloads); assertEquals(1, transport.closes)
    }

    @Test fun mismatchedConfirmedPublicationCannotBecomeACacheEntry(): Unit = runBlocking {
        assertNull(ValidatedSnapshotCache.fromConfirmed(document.copy(publication = document.publication.copy(revision = 2)),
            F.pharmacyId, 1, 3, F.snapshot().toString()))
        assertNull(ValidatedSnapshotCache.fromConfirmed(document.copy(publication = document.publication.copy(publishId = F.otherPharmacyId)),
            F.pharmacyId, 1, 3, F.snapshot().toString()))
    }

    @Test fun fileAndMessageIdentityChangesInvalidateCache(): Unit = runBlocking {
        val confirmed = cache()
        for (changed in listOf(document.copy(messageId = 13), document.copy(fileId = "replacement"),
            document.copy(fileUniqueId = "replacement"))) {
            val transport = transport().also { it.pin = { changed } }
            F.candidate().use { candidate ->
                val result = client(transport).readWithCache(candidate, 3, confirmed)
                assertEquals(changed, result.document)
                assertEquals(F.catalog(), (result.snapshot as SnapshotCandidate.Supported).catalog)
            }
            assertEquals(2, transport.pins); assertEquals(1, transport.downloads); assertEquals(1, transport.closes)
        }
    }

    @Test fun changedPinAfterDownloadRejectsAndNextReadCanSucceed(): Unit = runBlocking {
        val transport = transport().also { t -> t.pin = { call -> if (call == 2) document.copy(messageId = 13) else document } }
        val reader = client(transport)
        F.candidate().use { candidate ->
            rejects(SnapshotReadError.CHANGED_DURING_READ) { reader.read(candidate, 3) }
            assertEquals(1, transport.downloads); assertEquals(1, transport.closes)
            assertEquals(F.catalog(), (reader.read(candidate, 3).snapshot as SnapshotCandidate.Supported).catalog)
        }
        assertEquals(4, transport.pins); assertEquals(2, transport.downloads); assertEquals(2, transport.closes)
        assertFalse(temporary.root.walk().any { it.extension == "stage" })
    }

    @Test fun rateLimitStopsFurtherTransportCallsButDoesNotBlockAnotherToken(): Unit = runBlocking {
        val transport = transport().also { t -> t.pin = { call ->
            if (call == 1) throw SnapshotReadException(SnapshotReadError.RATE_LIMITED, 86_400)
            document
        } }
        val reader = client(transport)
        F.candidate().use { candidate ->
            rejects(SnapshotReadError.RATE_LIMITED) { reader.read(candidate, 3) }
            rejects(SnapshotReadError.RATE_LIMITED) { reader.read(candidate, 3) }
            assertEquals(1, transport.bots); assertEquals(1, transport.pins); assertEquals(0, transport.downloads)
            ConnectionCandidate(F.pharmacyId, "fixture", F.time, "654321:another_public_test_token", F.groupId, F.key, 1).use {
                assertEquals(F.catalog(), (reader.read(it, 3).snapshot as SnapshotCandidate.Supported).catalog)
            }
        }
        assertEquals(2, transport.bots); assertEquals(3, transport.pins); assertEquals(1, transport.downloads)
    }

    @Test fun cancellingQueuedReadDoesNotStartTransportOrCancelActiveRead(): Unit = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val transport = transport().also { t -> t.pin = { call ->
                if (call == 1) { entered.complete(Unit); release.await() }
                document
            } }
            val reader = client(transport)
            F.candidate().use { first -> F.candidate().use { second ->
                val active = async { reader.read(first, 3) }
                entered.await()
                val queued = async(start = CoroutineStart.UNDISPATCHED) { reader.read(second, 3) }
                queued.cancelAndJoin()
                assertTrue(queued.isCancelled)
                assertEquals(1, transport.pins); assertEquals(0, transport.downloads)
                release.complete(Unit)
                assertEquals(F.catalog(), (active.await().snapshot as SnapshotCandidate.Supported).catalog)
                assertEquals(F.catalog(), (reader.read(second, 3).snapshot as SnapshotCandidate.Supported).catalog)
            } }
            assertEquals(1, transport.bots); assertEquals(4, transport.pins)
            assertEquals(2, transport.downloads); assertEquals(2, transport.closes)
        }
    }
}
