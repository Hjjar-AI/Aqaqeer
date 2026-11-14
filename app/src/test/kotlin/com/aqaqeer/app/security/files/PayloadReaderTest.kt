package com.aqaqeer.app.security.files

import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PayloadReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val reader = PayloadReader { Long.MAX_VALUE }
    private fun authenticated(json: JsonObject, domain: EnvelopeDomain = EnvelopeDomain.SNAPSHOT) = AuthenticatedJson(json.toString().toByteArray(), domain)
    private suspend fun snapshot(json: JsonObject = F.snapshot(), pharmacy: String = F.pharmacyId, keyVersion: Int = 1) =
        reader.snapshot(authenticated(json), pharmacy, keyVersion, 3)
    private suspend fun rejects(code: PayloadError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: PayloadException) { assertEquals(code, e.error); assertNull(e.cause) }
    }
    @Test fun encryptedConnectionAndSnapshotReachValidCandidates() = runBlocking {
        val envelopes = EnvelopeReader(temporary.root) { Long.MAX_VALUE }
        reader.connection(envelopes.password(F.connectionEnvelope().inputStream(), EnvelopeDomain.CONNECTION, "١٢٣٤٥٦".toCharArray())).use {
            assertEquals(F.pharmacyId, it.pharmacyId)
            val secrets = it.copySecrets()
            assertArrayEquals(F.key, secrets.snapshotKey); secrets.snapshotKey.fill(0)
        }
        val result = reader.snapshot(envelopes.snapshot(F.snapshotEnvelope().inputStream(), F.key), F.pharmacyId, 1, 3) as SnapshotCandidate.Supported
        assertEquals(F.catalog(), result.catalog)
        assertEquals(F.time, result.publication.publishedAt)
        assertFalse(temporary.root.walk().any { it.extension == "stage" })
    }
    @Test fun connectionRejectsUnknownMissingWrongTypedAndForbiddenAuthorizationFields() = runBlocking {
        val valid = F.connection()
        val invalid = listOf(JsonObject(valid - "createdBy"), JsonObject(valid + ("adminMode" to JsonPrimitive(true))),
            JsonObject(valid + ("groupId" to JsonPrimitive("-123"))), JsonObject(valid + ("schemaVersion" to JsonPrimitive(1.0))),
            JsonObject(valid + ("snapshotKey" to JsonPrimitive(F.b64(ByteArray(31))))),
            JsonObject(valid + ("pharmacyName" to JsonPrimitive(" padded "))))
        for (json in invalid) rejects(PayloadError.INVALID_PAYLOAD) { reader.connection(authenticated(json, EnvelopeDomain.CONNECTION)) }
    }
    @Test fun unsupportedConnectionSchemaAndWrongReplacementPharmacyAreSeparateFailures() = runBlocking {
        rejects(PayloadError.UNSUPPORTED_SCHEMA) { reader.connection(authenticated(JsonObject(F.connection() + ("schemaVersion" to JsonPrimitive(2))), EnvelopeDomain.CONNECTION)) }
        rejects(PayloadError.WRONG_PHARMACY) { reader.connection(authenticated(F.connection(), EnvelopeDomain.CONNECTION), F.otherPharmacyId) }
    }
    @Test fun wrongDomainsCannotReachEitherPayloadType() = runBlocking {
        rejects(PayloadError.INVALID_PAYLOAD) { reader.connection(authenticated(F.connection(), EnvelopeDomain.BACKUP)) }
        rejects(PayloadError.INVALID_PAYLOAD) { reader.snapshot(authenticated(F.snapshot(), EnvelopeDomain.CONNECTION), F.pharmacyId, 1, 3) }
    }
    @Test fun plaintextIsWipedAndClosedOnSuccessFailureAndResourceRejection() = runBlocking {
        for (invalid in listOf(false, true)) {
            val bytes = (if (invalid) JsonObject(F.snapshot() - "sections") else F.snapshot()).toString().toByteArray()
            val payload = AuthenticatedJson(bytes, EnvelopeDomain.SNAPSHOT)
            if (invalid) rejects(PayloadError.INVALID_PAYLOAD) { reader.snapshot(payload, F.pharmacyId, 1, 3) }
            else reader.snapshot(payload, F.pharmacyId, 1, 3)
            assertTrue(bytes.all { it == 0.toByte() })
            assertThrows(IllegalStateException::class.java) { payload.withUtf8Reader { it.read() } }
        }
        val bytes = F.snapshot().toString().toByteArray()
        rejects(PayloadError.RESOURCE_LIMIT) { PayloadReader { 0 }.snapshot(AuthenticatedJson(bytes, EnvelopeDomain.SNAPSHOT), F.pharmacyId, 1, 3) }
        assertTrue(bytes.all { it == 0.toByte() })
    }
    @Test fun cancellationBeforeDispatchStillClosesPlaintext() = runBlocking {
        val bytes = F.snapshot().toString().toByteArray()
        val cancelled = Job().apply { cancel() }
        try { withContext(cancelled) { snapshot() }; fail("Expected cancellation") } catch (_: CancellationException) { }
        // Enter consume with an already cancelled context, rather than cancelling before the call can start.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            reader.snapshot(AuthenticatedJson(bytes, EnvelopeDomain.SNAPSHOT), F.pharmacyId, 1, 3)
        }
        job.join()
        assertTrue(bytes.all { it == 0.toByte() })
    }
    @Test fun futureSchemaSkipsChangedEntityShapesButRequiresStablePublication() = runBlocking {
        val future = JsonObject(F.snapshot(schema = 2) + ("medications" to buildJsonObject { put("future", true) }))
        val result = snapshot(future) as SnapshotCandidate.RequiresUpdate
        assertEquals(2, result.compatibility.schemaVersion)
        assertEquals(F.publishId, result.publication.publishId)
        rejects(PayloadError.INVALID_PAYLOAD) { snapshot(JsonObject(future - "publishedAt")) }
    }
    @Test fun newerMinimumAppReturnsUpdateObservationAndIdentityChecksPrecedeIt() = runBlocking {
        val future = F.snapshot(minimum = 100)
        assertTrue(snapshot(future) is SnapshotCandidate.RequiresUpdate)
        rejects(PayloadError.WRONG_PHARMACY) { snapshot(future, F.otherPharmacyId) }
        rejects(PayloadError.WRONG_KEY_VERSION) { snapshot(future, keyVersion = 2) }
    }
    @Test fun malformedCompatibilityAndMetadataDisagreementAreRejected() = runBlocking {
        val root = F.snapshot()
        for (bad in listOf(JsonObject(root - "compatibility"), JsonObject(root + ("compatibility" to buildJsonObject {
            put("schemaVersion", "1"); put("minVersionCode", 1); put("keyVersion", 1)
        })), JsonObject(root + ("pharmacy" to JsonObject(root["pharmacy"]!!.jsonObject - "schemaVersion"))))) {
            rejects(PayloadError.INVALID_PAYLOAD) { snapshot(bad) }
        }
    }
    @Test fun invalidReferencesPricesEditorsAndPinVerifierCannotReturnSupportedCatalog() = runBlocking {
        val catalog = F.catalog(); val med = catalog.medications.single(); val pharmacy = catalog.pharmacy!!
        val invalid = listOf(catalog.copy(medications = listOf(med.copy(categoryId = F.otherPharmacyId))),
            catalog.copy(medications = listOf(med.copy(priceMinor = -1, currency = com.aqaqeer.app.data.Currency.USD))),
            catalog.copy(medications = listOf(med.copy(updatedBy = F.editor.copy(name = " ")))),
            catalog.copy(pharmacy = pharmacy.copy(adminPinVerifier = pharmacy.adminPinVerifier.copy(hash = "AA=="))),
            catalog.copy(availability = catalog.availability + catalog.availability))
        for (value in invalid) rejects(PayloadError.INVALID_PAYLOAD) { snapshot(F.snapshot(value)) }
    }
    @Test fun publicationLineageAndUnexpectedLocalFieldsAreRejected() = runBlocking {
        val root = F.snapshot()
        for ((field, value) in listOf("revision" to JsonPrimitive(0), "basedOnRevision" to JsonPrimitive(1),
            "parentPublishId" to JsonPrimitive(F.publishId), "history" to JsonArray(emptyList()), "revision" to JsonPrimitive("1"))) {
            rejects(PayloadError.INVALID_PAYLOAD) { snapshot(JsonObject(root + (field to value))) }
        }
    }
    @Test fun candidateCloseWipesOwnedKeyWithoutWipingCopiedSecrets() {
        val owned = F.key
        val candidate = ConnectionCandidate(F.pharmacyId, "fixture", F.time, F.editor, F.token, F.groupId, owned, 1)
        val secrets = candidate.copySecrets(); candidate.close()
        assertTrue(owned.all { it == 0.toByte() }); assertArrayEquals(F.key, secrets.snapshotKey)
        assertThrows(IllegalStateException::class.java) { candidate.copySecrets() }; secrets.snapshotKey.fill(0)
    }
}
