package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    private suspend fun snapshot(revision: Int = 1, id: String = F.publishId, parent: String? = null,
        catalog: CatalogSnapshot = F.catalog()): SnapshotCandidate.Supported {
        val payload=JsonObject(F.snapshot(catalog)+mapOf("revision" to JsonPrimitive(revision),
            "publishId" to JsonPrimitive(id),"parentPublishId" to (parent?.let(::JsonPrimitive) ?: JsonNull),
            "basedOnRevision" to JsonPrimitive(revision-1)))
        return PayloadReader { Long.MAX_VALUE }.snapshot(AuthenticatedJson(payload.toString().toByteArray(),EnvelopeDomain.SNAPSHOT),
            F.pharmacyId,1,3) as SnapshotCandidate.Supported
    }
    @Test fun exactPublicationAndReorderedArraysAreUnchanged(): Unit = runBlocking {
        val base=snapshot()
        assertEquals(RefreshOutcome.UNCHANGED,publicationDecision(base,snapshot(catalog=F.catalog().copy(forms=F.catalog().forms.reversed()))))
    }
    @Test fun publicationAndLocalWorkPolicyStillRequiresDirectAncestry(): Unit = runBlocking {
        val base=snapshot()
        assertEquals(RefreshOutcome.APPLIED,publicationDecision(base,snapshot(2,F.otherPharmacyId,F.publishId)))
        for (incoming in listOf(snapshot(1,F.otherPharmacyId),snapshot(3,F.otherPharmacyId,F.installationId),
            snapshot(catalog=F.catalog().copy(medications=F.catalog().medications.map { it.copy(note="same UUID different content") })))) {
            val error=assertThrows(RefreshException::class.java) { publicationDecision(base,incoming) }
            assertEquals(RefreshError.PUBLICATION_RECOVERY,error.error)
        }
        val ahead=snapshot(2,F.otherPharmacyId,F.publishId)
        assertThrows(RefreshException::class.java) { publicationDecision(ahead,base) }
    }
    @Test fun concurrentWaitersShareOneRequestAndCancellationDoesNotCancelTheOther(): Unit = runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val flight=RefreshFlight(owner); val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
            var calls=0
            val first=async { flight.run { calls++; entered.complete(Unit); release.await(); RefreshOutcome.APPLIED } }
            entered.await()
            val second=async(start=CoroutineStart.UNDISPATCHED) { flight.run { fail("Duplicate request"); RefreshOutcome.UNCHANGED } }
            first.cancelAndJoin(); release.complete(Unit)
            assertEquals(RefreshOutcome.APPLIED,second.await()); assertEquals(1,calls)
            assertEquals(RefreshOutcome.UNCHANGED,flight.run { calls++; RefreshOutcome.UNCHANGED })
            assertEquals(2,calls)
        } finally { owner.cancel() }
    }
    @Test fun sharedFailureDoesNotPoisonNextRefresh(): Unit = runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val flight=RefreshFlight(owner)
            try { flight.run { throw RefreshException(RefreshError.LOCAL_WORK) }; fail("Failure hidden") }
            catch (error: RefreshException) { assertEquals(RefreshError.LOCAL_WORK,error.error) }
            assertEquals(RefreshOutcome.UNCHANGED,flight.run { RefreshOutcome.UNCHANGED })
        } finally { owner.cancel() }
    }

    @Test fun allCancelledWaitersLeaveTheAppOwnedRequestRunning(): Unit = runBlocking {
        withTimeout(5_000) {
            val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val flight = RefreshFlight(owner)
                val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
                val completed = CompletableDeferred<Unit>()
                val first = async(start = CoroutineStart.UNDISPATCHED) {
                    flight.run { entered.complete(Unit); release.await(); completed.complete(Unit); RefreshOutcome.APPLIED }
                }
                entered.await()
                first.cancelAndJoin()
                val replacement = async(start = CoroutineStart.UNDISPATCHED) {
                    flight.run { fail("Cancellation started a duplicate refresh"); RefreshOutcome.UNCHANGED }
                }
                release.complete(Unit)
                assertEquals(RefreshOutcome.APPLIED, replacement.await())
                completed.await()
            } finally { owner.cancel() }
        }
    }

    @Test fun everyConcurrentWaiterReceivesTheOriginalFailureAndNextRequestCanSucceed(): Unit = runBlocking {
        withTimeout(5_000) {
            val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val flight = RefreshFlight(owner)
                val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
                val expected = RefreshException(RefreshError.LOCAL_STATE_CHANGED)
                val first = async(start = CoroutineStart.UNDISPATCHED) {
                    runCatching { flight.run { entered.complete(Unit); release.await(); throw expected } }
                }
                entered.await()
                val waiters = List(8) {
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching { flight.run { fail("Failure waiter started its own refresh"); RefreshOutcome.UNCHANGED } }
                    }
                }
                release.complete(Unit)
                assertSame(expected, first.await().exceptionOrNull())
                waiters.forEach { assertSame(expected, it.await().exceptionOrNull()) }
                assertEquals(RefreshOutcome.APPLIED, flight.run { RefreshOutcome.APPLIED })
            } finally { owner.cancel() }
        }
    }

    @Test fun cancellingOwnerCancelsTheSharedOperationAndItsWaiters(): Unit = runBlocking {
        withTimeout(5_000) {
            val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val entered = CompletableDeferred<Unit>(); val cleaned = CompletableDeferred<Unit>()
                val flight = RefreshFlight(owner)
                val waiter = async {
                    flight.run {
                        entered.complete(Unit)
                        try { awaitCancellation() } finally { cleaned.complete(Unit) }
                    }
                }
                entered.await(); owner.cancel()
                try { waiter.await(); fail("Owner cancellation returned a refresh result") }
                catch (_: CancellationException) { assertTrue(waiter.isCancelled) }
                cleaned.await()
            } finally { owner.cancel() }
        }
    }

    @Test fun completedRequestIsNeverReusedAsTheNextRefresh(): Unit = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val flight = RefreshFlight(owner)
            var calls = 0
            repeat(4) { index ->
                val expected = if (index % 2 == 0) RefreshOutcome.APPLIED else RefreshOutcome.UNCHANGED
                assertEquals(expected, flight.run { calls++; expected })
                assertEquals(index + 1, calls)
            }
        } finally { owner.cancel() }
    }

    @Test fun samePublicationIdCannotMaskChangedAuthenticatedMetadata(): Unit = runBlocking {
        val base = snapshot(2, F.otherPharmacyId, F.publishId)
        val payload = JsonObject(F.snapshot() + mapOf("revision" to JsonPrimitive(2),
            "basedOnRevision" to JsonPrimitive(1), "publishId" to JsonPrimitive(F.otherPharmacyId),
            "parentPublishId" to JsonPrimitive(F.publishId)))
        val mutations = listOf(
            "publishedAt" to JsonPrimitive(F.time.plusSeconds(1).toString()),
            "parentPublishId" to JsonPrimitive(F.installationId))
        for ((field, value) in mutations) {
            val incoming = PayloadReader { Long.MAX_VALUE }.snapshot(
                AuthenticatedJson(JsonObject(payload + (field to value)).toString().toByteArray(), EnvelopeDomain.SNAPSHOT),
                F.pharmacyId, 1, 3) as SnapshotCandidate.Supported
            val error = assertThrows(RefreshException::class.java) { publicationDecision(base, incoming) }
            assertEquals(field, RefreshError.PUBLICATION_RECOVERY, error.error)
        }
    }

    @Test fun samePublicationWithDifferentCompatibilityRequiresRecovery(): Unit = runBlocking {
        val base = snapshot()
        val changed = PayloadReader { Long.MAX_VALUE }.snapshot(
            AuthenticatedJson(F.snapshot(F.catalog().let { it.copy(pharmacy = it.pharmacy!!.copy(minVersionCode = 2)) },
                minimum = 2).toString().toByteArray(), EnvelopeDomain.SNAPSHOT),
            F.pharmacyId, 1, 3) as SnapshotCandidate.Supported
        val error = assertThrows(RefreshException::class.java) {
            publicationDecision(base, changed)
        }
        assertEquals(RefreshError.PUBLICATION_RECOVERY, error.error)
    }
}
