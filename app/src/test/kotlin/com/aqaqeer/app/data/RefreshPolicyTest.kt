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
    @Test fun exactPublicationAndReorderedArraysAreUnchanged() = runBlocking {
        val base=snapshot()
        assertEquals(RefreshOutcome.UNCHANGED,publicationDecision(base,snapshot(catalog=F.catalog().copy(forms=F.catalog().forms.reversed()))))
    }
    @Test fun onlyDirectDescendantAutomaticallyAdvancesConfirmedState() = runBlocking {
        val base=snapshot()
        assertEquals(RefreshOutcome.APPLIED,publicationDecision(base,snapshot(2,F.otherPharmacyId,F.publishId)))
        for (incoming in listOf(snapshot(1,F.otherPharmacyId),snapshot(3,F.otherPharmacyId,F.editor.id),
            snapshot(catalog=F.catalog().copy(medications=F.catalog().medications.map { it.copy(note="same UUID different content") })))) {
            val error=assertThrows(RefreshException::class.java) { publicationDecision(base,incoming) }
            assertEquals(RefreshError.PUBLICATION_RECOVERY,error.error)
        }
        val ahead=snapshot(2,F.otherPharmacyId,F.publishId)
        assertThrows(RefreshException::class.java) { publicationDecision(ahead,base) }
        Unit
    }
    @Test fun concurrentWaitersShareOneRequestAndCancellationDoesNotCancelTheOther() = runBlocking {
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
    @Test fun sharedFailureDoesNotPoisonNextRefresh() = runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val flight=RefreshFlight(owner)
            try { flight.run { throw RefreshException(RefreshError.LOCAL_WORK) }; fail("Failure hidden") }
            catch (error: RefreshException) { assertEquals(RefreshError.LOCAL_WORK,error.error) }
            assertEquals(RefreshOutcome.UNCHANGED,flight.run { RefreshOutcome.UNCHANGED })
        } finally { owner.cancel() }
    }
}
