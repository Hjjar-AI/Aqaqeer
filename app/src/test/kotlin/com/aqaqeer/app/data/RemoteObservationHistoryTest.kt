package com.aqaqeer.app.data

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoteObservationHistoryTest {
    @Test fun unchangedOrReorderedCatalogCreatesNoDuplicateHistory() {
        val base=F.catalog()
        assertTrue(RemoteObservationHistory.changes(base,base.copy(forms=base.forms.reversed()),F.time,2).isEmpty())
    }
    @Test fun remoteFieldsHaveObservationTimeAndRevisionWithoutInventedAuthorshipOrIntermediateFlips() {
        val base=F.catalog()
        val next=base.copy(medications=base.medications.map { it.copy(nameEn="Changed",note="Visible final value",updatedAt=F.time.plusSeconds(10)) })
        val changes=RemoteObservationHistory.changes(base,next,F.time.plusSeconds(60),2)
        assertEquals(setOf("nameEn","note"),changes.map { it.field }.toSet())
        changes.forEach {
            assertEquals(F.time.plusSeconds(60),it.observedAt); assertEquals(2L,it.publishedRevision)
            assertNull(it.editedAt); assertNull(it.operationId)
        }
        assertEquals("\"Changed\"",changes.single { it.field=="nameEn" }.newValue)
    }
    @Test fun creationsDeletionsAvailabilityAndTombstonesAreObservedWithoutReconstructingOldHistory() {
        val base=F.catalog()
        val next=base.copy(medications=emptyList(),availability=emptyList(),
            tombstones=listOf(Tombstone("medication",base.medications.single().id,F.time,F.installationId)))
        val changes=RemoteObservationHistory.changes(base,next,F.time,2)
        assertEquals(setOf("medication","availability","tombstone"),changes.map { it.entityType }.toSet())
        assertEquals(3,changes.size)
        assertNull(changes.single { it.entityType=="medication" }.newValue)
        assertNotNull(changes.single { it.entityType=="tombstone" }.newValue)
    }
    @Test fun recoveryObservationDeduplicatesOnlyTheSameBaseAndIncomingPayload() {
        fun record(base: String,payload: String)=RemoteObservationHistory.recovery(F.pharmacyId,base,F.publishId,payload,F.time,2)
        val first=record(F.installationId,"payload")
        assertEquals(first.id,record(F.installationId,"payload").id)
        assertNotEquals(first.id,record(F.installationId,"different").id)
        assertNotEquals(first.id,record(F.otherPharmacyId,"payload").id)
        assertEquals("payload",first.newValue); assertEquals("recoveryCandidate",first.field); assertNull(first.operationId)
    }
    @Test fun publicationRetainsLineageAndTimesWithoutAttribution(): Unit = runBlocking {
        val snapshot=PayloadReader { Long.MAX_VALUE }.snapshot(AuthenticatedJson(F.snapshot().toString().toByteArray(),
            EnvelopeDomain.SNAPSHOT),F.pharmacyId,1,3) as SnapshotCandidate.Supported
        val event=RemoteObservationHistory.publication(F.otherPharmacyId,snapshot,F.time.plusSeconds(60))
        assertEquals("snapshot",event.entityType); assertEquals("publication",event.field)
        assertEquals(F.time,event.editedAt); assertEquals(F.time.plusSeconds(60),event.observedAt)
        val payload = requireNotNull(event.newValue)
        assertFalse(payload.contains("publisher")); assertTrue(payload.contains("publishId")); assertNull(event.operationId)
    }

}
