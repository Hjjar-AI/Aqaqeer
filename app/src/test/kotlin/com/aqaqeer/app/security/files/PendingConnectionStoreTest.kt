package com.aqaqeer.app.security.files

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.fixtures.MemorySecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PendingConnectionStoreTest {
    @Test fun stagedCredentialsReopenWithoutPasswordAndExposeMetadataOnly() = runBlocking {
        val secure = MemorySecureStore(); val store = PendingConnectionStore(secure)
        val info = F.candidate().use { store.stage(it) }
        assertEquals(info, PendingConnectionStore(secure).info())
        var loaded: ConnectionCandidate? = null
        store.withCandidate(info.id) {
            loaded = it
            val secrets = it.copySecrets()
            assertEquals(F.token, secrets.botToken); assertEquals(F.groupId, secrets.groupId)
            assertArrayEquals(F.key, secrets.snapshotKey); secrets.snapshotKey.fill(0)
        }
        assertThrows(IllegalStateException::class.java) { loaded!!.copySecrets() }
        assertTrue(secure.lastRead!!.all { it == 0.toByte() })
    }
    @Test fun existingCandidateAndStaleIdsCannotBeOverwrittenOrUsed() = runBlocking {
        val secure = MemorySecureStore(); val store = PendingConnectionStore(secure)
        val info = F.candidate().use { store.stage(it) }; val original = secure.entries.values.single().copyOf()
        F.candidate().use { candidate -> assertThrows(IllegalStateException::class.java) { store.stage(candidate) } }
        assertThrows(PendingConnectionChanged::class.java) { store.discard("stale") }
        var invoked = false
        try { store.withCandidate("stale") { invoked = true }; fail("Stale ID accepted") } catch (_: PendingConnectionChanged) { }
        assertFalse(invoked); assertArrayEquals(original, secure.entries.values.single())
        assertEquals(info, store.info())
    }
    @Test fun failedWritesRetainOldStateAndSuccessfulDiscardRequiresNoDeleteApi() {
        val secure = MemorySecureStore(); val store = PendingConnectionStore(secure)
        val info = F.candidate().use { store.stage(it) }; val before = secure.entries.values.single().copyOf()
        secure.failWrites = true
        assertThrows(StorageRecoveryRequired::class.java) { store.discard(info.id) }
        assertArrayEquals(before, secure.entries.values.single())
        secure.failWrites = false; store.discard(info.id)
        assertNull(PendingConnectionStore(secure).info())
        F.candidate().use { assertNotEquals(info.id, store.stage(it).id) }
    }
    @Test fun corruptTruncatedAndUnsupportedProtectedRecordsAreNeverReplaced() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1,2,3), byteArrayOf(0x41,0x51,0x50,0x31,2))) {
            val secure = MemorySecureStore(); secure.entries["pending-connection"] = bytes.copyOf()
            val store = PendingConnectionStore(secure)
            assertThrows(StorageRecoveryRequired::class.java) { store.info() }
            F.candidate().use { candidate -> assertThrows(StorageRecoveryRequired::class.java) { store.stage(candidate) } }
            assertArrayEquals(bytes, secure.entries.values.single()); assertEquals(0, secure.writes)
        }
    }
    @Test fun oversizedLocalFieldsFailBeforeAnyWriteAndLeaveCandidateCallerOwned() {
        val secure = MemorySecureStore(); val store = PendingConnectionStore(secure)
        val candidate = ConnectionCandidate(F.pharmacyId, "x".repeat(8193), F.time, F.editor, F.token, F.groupId, F.key, 1)
        try { store.stage(candidate); fail("Oversized record accepted") }
        catch (e: EnvelopeException) { assertEquals(EnvelopeError.RESOURCE_LIMIT, e.error) }
        val copy = candidate.copySecrets(); assertArrayEquals(F.key, copy.snapshotKey)
        copy.snapshotKey.fill(0); candidate.close(); assertEquals(0, secure.writes)
    }
    @Test fun cancellationInsideCredentialScopeClosesCandidateWithoutDiscardingRecord() = runBlocking {
        val secure = MemorySecureStore(); val store = PendingConnectionStore(secure)
        val info = F.candidate().use { store.stage(it) }; var loaded: ConnectionCandidate? = null
        val entered = CompletableDeferred<Unit>()
        val operation = launch {
            store.withCandidate(info.id) { loaded = it; entered.complete(Unit); awaitCancellation() }
        }
        entered.await(); operation.cancelAndJoin()
        assertThrows(IllegalStateException::class.java) { loaded!!.copySecrets() }
        assertEquals(info, store.info())
    }
    @Test fun activeJournalUsesIndependentEntryAndRetainsTheExactStagingId() = runBlocking {
        val secure=MemorySecureStore(); val pending=PendingConnectionStore(secure)
        val info=F.candidate().use { pending.stage(it) }
        val active=PendingConnectionStore(secure,"active-connection")
        F.candidate().use { assertEquals(info,active.retain(it,info.id)) }
        assertEquals(2,secure.entries.size)
        pending.discard(info.id)
        assertNull(pending.info()); assertEquals(info,active.info())
        active.withCandidate(info.id) {
            val secrets=it.copySecrets()
            try { assertEquals(F.token,secrets.botToken); assertArrayEquals(F.key,secrets.snapshotKey) }
            finally { secrets.snapshotKey.fill(0) }
        }
        active.discard(info.id); assertNull(active.info())
    }
    @Test fun failedActiveJournalWritePreservesPendingAndPreviousJournal() {
        val secure=MemorySecureStore(); val pending=PendingConnectionStore(secure)
        val info=F.candidate().use { pending.stage(it) }
        val active=PendingConnectionStore(secure,"active-connection")
        F.candidate().use { active.retain(it,info.id) }
        val before=secure.entries["active-connection"]!!.copyOf()
        secure.failWrites=true
        F.candidate().use { candidate ->
            assertThrows(StorageRecoveryRequired::class.java) { active.retain(candidate,F.publishId) }
        }
        assertArrayEquals(before,secure.entries["active-connection"])
        assertEquals(info,pending.info()); assertEquals(info,active.info())
    }
    @Test fun journalRejectsNoncanonicalIdsBeforeWritingSecrets() {
        val secure=MemorySecureStore(); val active=PendingConnectionStore(secure,"active-connection")
        F.candidate().use { candidate ->
            for (id in listOf("stale",F.pharmacyId.uppercase())) {
                assertThrows(IllegalArgumentException::class.java) { active.retain(candidate,id) }
            }
        }
        assertEquals(0,secure.writes)
    }

}
