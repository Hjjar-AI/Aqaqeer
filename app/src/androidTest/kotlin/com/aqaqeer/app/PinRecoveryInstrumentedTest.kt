package com.aqaqeer.app

import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.security.files.EnvelopeReader
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

/** Entirely offline: real repositories/storage with a fake Telegram transport. */
class PinRecoveryInstrumentedTest {
    private lateinit var p: SuiteProfile
    private lateinit var remote: RecoveryRemote
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        p = SuiteProfile(); p.create(); p.medication(); remote = RecoveryRemote()
        p.publish(remote.backing)
    }
    @After fun cleanup() { if (::p.isInitialized) p.close() }
    private fun sharing() = PharmacySharing(p.admin, p.secure, p.requirements,
        SnapshotReadClient(remote, EnvelopeReader(p.root)), remote, remote, p.context)
    private fun confirm(sharing: PharmacySharing) = sharing.status.value.conflicts.associate { it.key to true }
    private suspend fun state() = p.admin.transaction { it.snapshotState()!! }
    private suspend fun retained() = p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) }

    @Test fun missingPinWithNoPendingEditsRequiresReviewThenPublishesAndPinsNextRevision(): Unit = runBlocking {
        val original = state(); val catalog = p.ready().catalog
        remote.hideSnapshot = true
        val publisher = sharing(); publisher.publish()
        assertEquals(1, remote.backing.uploads); assertEquals(1, remote.backing.writes)
        publisher.publish(recoverPin = true)
        val review = publisher.status.value.conflicts.single()
        assertTrue(review.key.startsWith("missing:")); assertTrue(review.base.contains(original.publishId!!))
        assertTrue(review.base.contains(original.publishedAt!!))
        assertEquals(1, remote.backing.uploads); assertEquals(original, state())
        publisher.publish(confirm(publisher), recoverPin = true)
        assertTrue(publisher.status.value.verified)
        assertEquals(original.revision!! + 1, state().revision)
        assertNotEquals(original.pinnedMessageId, state().pinnedMessageId)
        assertEquals(original.publishId, Json.parseToJsonElement(state().confirmedPayloadJson!!).jsonObject["parentPublishId"]!!.jsonPrimitive.content)
        assertEquals(catalog, p.ready().catalog); assertEquals(0, p.ready().pendingCount)
        assertEquals(2, remote.backing.uploads); assertEquals(2, remote.backing.writes)
    }

    @Test fun unrelatedPinRecoveryPublishesReviewedPendingEditsAndAcknowledgesOnlyAfterReadback(): Unit = runBlocking {
        val med = p.ready().catalog.medications.single()
        p.admin.saveMedication(med.copy(note = "reviewed local edit"))
        remote.hideSnapshot = true; remote.unrelatedPin = humanPin(20)
        val publisher = sharing(); publisher.publish(recoverPin = true)
        assertEquals(1, remote.backing.writes); assertTrue(p.ready().pendingCount > 0)
        publisher.publish(confirm(publisher), recoverPin = true)
        assertTrue(publisher.status.value.verified)
        assertEquals("reviewed local edit", p.ready().catalog.medications.single().note)
        assertEquals(0, p.ready().pendingCount); assertFalse(p.ready().uncertain)
        assertEquals(2, remote.backing.writes)
    }

    @Test fun changedPinInvalidatesEarlierRecoveryConfirmationWithoutUploading(): Unit = runBlocking {
        remote.hideSnapshot = true; remote.unrelatedPin = humanPin(20)
        val publisher = sharing(); publisher.publish(recoverPin = true)
        val old = confirm(publisher)
        remote.unrelatedPin = humanPin(21)
        publisher.publish(old, recoverPin = true)
        assertFalse(publisher.status.value.verified)
        assertTrue(publisher.status.value.conflicts.single().key !in old)
        assertEquals(1, remote.backing.uploads); assertEquals(1, remote.backing.writes)
        publisher.publish(confirm(publisher), recoverPin = true)
        assertTrue(publisher.status.value.verified)
    }

    @Test fun pinChangedDuringUploadBlocksMutationAndRetainsDurableAttempt(): Unit = runBlocking {
        remote.hideSnapshot = true; remote.unrelatedPin = humanPin(20)
        val original = state(); val publisher = sharing(); publisher.publish(recoverPin = true)
        remote.backing.beforeUpload = { remote.unrelatedPin = humanPin(21) }
        publisher.publish(confirm(publisher), recoverPin = true)
        assertFalse(publisher.status.value.verified)
        assertEquals(2, remote.backing.uploads); assertEquals(1, remote.backing.writes)
        assertEquals(original, state())
        assertEquals("UPLOADED", p.admin.transaction { it.attempts().single().transportPhase })
        remote.backing.beforeUpload = {}
        val retry = sharing(); retry.publish(recoverPin = true)
        assertTrue(retry.status.value.conflicts.single().key.startsWith("missing:"))
        assertEquals(1, remote.backing.writes)
    }

    @Test fun networkCredentialsAndDamagedPayloadNeverEnableForcedRecovery(): Unit = runBlocking {
        val before = retained(); val original = state()
        remote.hideSnapshot = true; remote.unrelatedPin = humanPin(20)
        for (error in listOf(SnapshotReadError.NETWORK, SnapshotReadError.INVALID_CREDENTIALS, SnapshotReadError.ACCESS_DENIED)) {
            remote.failure = error
            val publisher = sharing(); publisher.publish(recoverPin = true)
            assertFalse(publisher.status.value.verified); assertTrue(publisher.status.value.conflicts.isEmpty())
            assertEquals(before, retained()); assertEquals(original, state())
        }
        remote.failure = null; remote.hideSnapshot = false
        val document = remote.backing.current!!
        remote.backing.files[document.fileId] = "damaged".toByteArray()
        val publisher = sharing(); publisher.publish(recoverPin = true)
        assertTrue(publisher.status.value.conflicts.isEmpty()); assertFalse(publisher.status.value.verified)
        assertEquals(1, remote.backing.uploads); assertEquals(1, remote.backing.writes)
        assertEquals(before, retained()); assertEquals(original, state())
    }

    @Test fun lostPinResponseIsAcknowledgedAfterRecreationWithoutAnotherUpload(): Unit = runBlocking {
        remote.hideSnapshot = true
        val publisher = sharing(); publisher.publish(recoverPin = true)
        remote.backing.loseWriteResponse = true
        publisher.publish(confirm(publisher), recoverPin = true)
        assertFalse(publisher.status.value.verified); assertTrue(p.ready().uncertain)
        val published = remote.backing.current!!.publication.publishId
        val retry = sharing(); retry.publish(recoverPin = true)
        assertTrue(retry.status.value.verified); assertFalse(p.ready().uncertain)
        assertEquals(published, state().publishId)
        assertEquals(2, remote.backing.uploads); assertEquals(2, remote.backing.writes)
    }

    @Test fun hiddenWriteStartedAttemptNeedsSeparateReconciliationBeforeReplacement(): Unit = runBlocking {
        remote.hideSnapshot = true
        val publisher = sharing(); publisher.publish(recoverPin = true)
        remote.backing.loseWriteResponse = true
        publisher.publish(confirm(publisher), recoverPin = true)
        val uncertain = p.admin.transaction { it.attempts().single() }
        assertEquals("WRITE_STARTED", uncertain.transportPhase)
        remote.hideSnapshot = true; remote.unrelatedPin = humanPin(25)
        val retry = sharing(); retry.publish(recoverPin = true)
        val retainedChoice = confirm(retry)
        retry.publish(retainedChoice, recoverPin = true)
        assertTrue(retry.status.value.conflicts.single().key.startsWith("uncertain:"))
        assertEquals(listOf(uncertain), p.admin.transaction { it.attempts() })
        assertEquals(2, remote.backing.uploads)
        retry.publish(retainedChoice + confirm(retry), recoverPin = true)
        assertTrue(retry.status.value.verified)
        assertTrue(p.admin.transaction { it.history() }.any { it.field == "recoveryCandidate" && it.newValue == uncertain.candidatePayloadJson })
        assertTrue(p.admin.transaction { it.attempts() }.isEmpty())
        assertEquals(3, remote.backing.uploads); assertEquals(3, remote.backing.writes)
    }

    @Test fun validLiveSnapshotRefusesForcedRepublication(): Unit = runBlocking {
        val original = state(); val publisher = sharing()
        publisher.publish(recoverPin = true)
        assertFalse(publisher.status.value.verified); assertTrue(publisher.status.value.conflicts.isEmpty())
        assertEquals(original, state()); assertEquals(1, remote.backing.uploads); assertEquals(1, remote.backing.writes)
    }

    @Test fun multipleStoredAttemptsBlockBeforeNetworkAndPreserveBothCandidates(): Unit = runBlocking {
        val original = state()
        val attempts = (1..2).map {
            val id = newId()
            val payload = JsonObject(Json.parseToJsonElement(original.confirmedPayloadJson!!).jsonObject + mapOf(
                "revision" to JsonPrimitive(original.revision!! + 1), "publishId" to JsonPrimitive(id),
                "parentPublishId" to JsonPrimitive(original.publishId!!), "basedOnRevision" to JsonPrimitive(original.revision)))
            PublishAttempt(id, original.confirmedPayloadJson, payload.toString(), "[]", "PREPARED")
        }
        p.admin.transaction { dao -> attempts.forEach { dao.attempt(it) } }
        val publisher = sharing(); publisher.publish(recoverPin = true)
        assertEquals(uiText(R.string.publish_multiple_attempts), publisher.status.value.message)
        assertEquals(attempts.toSet(), p.admin.transaction { it.attempts() }.toSet())
        assertEquals(0, remote.observations); assertEquals(1, remote.backing.uploads)
    }

    private fun humanPin(id: Long) = buildJsonObject {
        put("message_id", id); put("from", buildJsonObject { put("id", 999); put("is_bot", false) })
        put("text", "Public unrelated pin fixture")
    }.toString()

    private class RecoveryRemote : TelegramSetupTransport, SnapshotWriteTransport, SnapshotReadTransport {
        val backing = SuiteRemote()
        var hideSnapshot = false
        var unrelatedPin: String? = null
        var failure: SnapshotReadError? = null
        var observations = 0
        override suspend fun validate(token: String, groupId: Long) = inspect(token, groupId)
        override suspend fun discover(token: String, challenge: String) = listOf(inspect(token, M3Fixtures.groupId))
        override suspend fun inspect(token: String, group: Long): SetupGroup {
            observations++
            return if (hideSnapshot) SetupGroup(group, backing.title, unrelatedPin) else backing.inspect(token, group)
        }
        override suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String) = backing.upload(token, group, bytes, caption)
        override suspend fun replace(token: String, group: Long, message: Long, file: String, caption: String) = backing.replace(token, group, message, file, caption)
        override suspend fun pin(token: String, group: Long, message: Long) {
            try { backing.pin(token, group, message) }
            finally { if (backing.current?.messageId == message) { hideSnapshot = false; unrelatedPin = null } }
        }
        override suspend fun bot(token: String): BotIdentity {
            failure?.let { throw SnapshotReadException(it) }
            return backing.bot(token)
        }
        override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument {
            failure?.let { throw SnapshotReadException(it) }
            if (hideSnapshot) throw SnapshotReadException(SnapshotReadError.SNAPSHOT_UNAVAILABLE)
            return backing.pinned(token, groupId, bot)
        }
        override suspend fun document(token: String, document: SnapshotDocument) = backing.document(token, document)
    }
}
