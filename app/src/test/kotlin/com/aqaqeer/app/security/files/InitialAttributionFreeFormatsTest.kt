package com.aqaqeer.app.security.files

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class InitialAttributionFreeFormatsTest {
    private val reader = PayloadReader { Long.MAX_VALUE }
    private val oldAttribution = buildJsonObject { put("id", F.installationId); put("name", "Old editor") }
    private suspend fun readSnapshot(root: JsonObject) = reader.snapshot(
        AuthenticatedJson(root.toString().toByteArray(), EnvelopeDomain.SNAPSHOT), F.pharmacyId, 1, 1)
    private suspend fun invalid(root: JsonObject, connection: Boolean = false) {
        val bytes = root.toString().toByteArray()
        try {
            if (connection) reader.connection(AuthenticatedJson(bytes, EnvelopeDomain.CONNECTION)).close() else
                reader.snapshot(AuthenticatedJson(bytes, EnvelopeDomain.SNAPSHOT), F.pharmacyId, 1, 1)
            fail("Incompatible attribution-bearing file accepted")
        } catch (error: PayloadException) { assertEquals(PayloadError.INVALID_PAYLOAD, error.error) }
        assertTrue(bytes.all { it == 0.toByte() })
    }
    @Test fun initialVersionOneRoundTripsWithoutAttributionAndRetainsPublicationLineage(): Unit = runBlocking {
        assertFalse(F.connection().containsKey("createdBy")); assertFalse(F.snapshot().containsKey("publishedBy"))
        val candidate = readSnapshot(F.snapshot()) as SnapshotCandidate.Supported
        assertEquals(1, candidate.compatibility.schemaVersion); assertEquals(1L, candidate.revision)
        assertEquals(F.publishId, candidate.publishId); assertEquals(F.time, candidate.publishedAt)
        assertEquals(F.catalog(), candidate.catalog)
        val restored = readSnapshot(Json.parseToJsonElement(candidate.confirmedJson()).jsonObject) as SnapshotCandidate.Supported
        assertEquals(candidate.catalog, restored.catalog); assertEquals(candidate.publication, restored.publication)
        assertEquals(candidate.parentPublishId, restored.parentPublishId); assertEquals(candidate.basedOnRevision, restored.basedOnRevision)
    }
    @Test fun oldConnectionCreatorAndSnapshotPublisherAreRejectedWithoutConversion(): Unit = runBlocking {
        invalid(JsonObject(F.connection() + ("createdBy" to oldAttribution)), connection = true)
        invalid(JsonObject(F.snapshot() + ("publishedBy" to oldAttribution)))
    }
    @Test fun nestedMedicationAndAvailabilityAttributionAreRejected(): Unit = runBlocking {
        for (array in listOf("medications", "availability")) {
            val root = F.snapshot()
            val changed = JsonArray(root.getValue(array).jsonArray.map { JsonObject(it.jsonObject + ("updatedBy" to oldAttribution)) })
            invalid(JsonObject(root + (array to changed)))
        }
    }
}
