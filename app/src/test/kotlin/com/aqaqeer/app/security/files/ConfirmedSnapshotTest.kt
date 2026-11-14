package com.aqaqeer.app.security.files

import com.aqaqeer.app.data.Tombstone
import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ConfirmedSnapshotTest {
    private suspend fun decode(payload: JsonObject) = PayloadReader { Long.MAX_VALUE }.snapshot(
        AuthenticatedJson(payload.toString().toByteArray(),EnvelopeDomain.SNAPSHOT),F.pharmacyId,1,3) as SnapshotCandidate.Supported

    @Test fun initialConfirmedBaseRetainsFullCatalogAndPublicationWithoutCredentials() = runBlocking {
        val first=decode(F.snapshot())
        val encoded=first.confirmedJson()
        val restored=decode(Json.parseToJsonElement(encoded).jsonObject)
        assertEquals(first.catalog,restored.catalog)
        assertEquals(first.compatibility,restored.compatibility)
        assertEquals(first.publication,restored.publication)
        assertEquals(first.publishedBy,restored.publishedBy)
        assertNull(restored.parentPublishId); assertEquals(0L,restored.basedOnRevision)
        assertFalse(encoded.contains(F.token)); assertFalse(encoded.contains("snapshotKey"))
        assertFalse(encoded.contains("adminMode")); assertFalse(encoded.contains("history"))
    }
    @Test fun confirmedBasePreservesLineageTombstonesAndPublicationTimeExactly() = runBlocking {
        val catalog=F.catalog().copy(tombstones=listOf(Tombstone("medication",F.otherPharmacyId,F.time,F.editor.id)))
        val source=JsonObject(F.snapshot(catalog)+mapOf(
            "revision" to JsonPrimitive(2),"parentPublishId" to JsonPrimitive(F.otherPharmacyId),
            "basedOnRevision" to JsonPrimitive(1)))
        val first=decode(source)
        val restored=decode(Json.parseToJsonElement(first.confirmedJson()).jsonObject)
        assertEquals(catalog,restored.catalog)
        assertEquals(2L,restored.revision); assertEquals(1L,restored.basedOnRevision)
        assertEquals(F.otherPharmacyId,restored.parentPublishId)
        assertEquals(F.time,restored.publishedAt)
    }
}
