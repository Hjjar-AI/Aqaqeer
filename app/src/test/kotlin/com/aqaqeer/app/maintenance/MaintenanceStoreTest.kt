package com.aqaqeer.app.maintenance

import app.cash.turbine.test
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.MemorySecureStore
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MaintenanceStoreTest {
    private fun journal() = MaintenanceJournal(id = F.publishId, pharmacyId = F.pharmacyId, kind = MaintenanceKind.REPLACEMENT)
    @Test fun writesReopenAndNotifyWhileTemporarySecretBuffersAreErased() = runTest {
        val secure = mockk<SecureStore>(); var retained: ByteArray? = null; var passed: ByteArray? = null
        every { secure.write(any(), any()) } answers { passed = secondArg(); retained = passed!!.copyOf() }
        every { secure.read(any()) } answers { retained?.copyOf() }
        val store = MaintenanceStore(secure)
        MaintenanceStore.changes.test {
            val before = awaitItem(); store.write(journal())
            assertEquals(before + 1, awaitItem())
            assertTrue(passed!!.all { it == 0.toByte() })
            assertEquals(journal(), MaintenanceStore(secure).read())
            store.write(null); awaitItem(); assertNull(store.read())
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 2) { secure.write("maintenance-journal", any()) }
    }
    @Test fun corruptOrInconsistentJournalsFailClosedWithoutErasingEvidence() {
        val secure = MemorySecureStore(); val store = MaintenanceStore(secure)
        listOf(journal().copy(version = 2), journal().copy(id = "bad"),
            journal().copy(activationEntry = "old", kind = MaintenanceKind.ROTATION),
            journal().copy(phase = MaintenancePhase.BACKED_UP),
            journal().copy(sourceRevision = 0),
            journal().copy(receipt = MaintenanceReceipt("other", F.publishId, "digest", 1))).forEach { invalid ->
            store.write(invalid); val bytes = secure.entries.getValue("maintenance-journal").copyOf()
            assertThrows(StorageRecoveryRequired::class.java) { store.read() }
            assertArrayEquals(bytes, secure.entries.getValue("maintenance-journal"))
        }
        secure.write("maintenance-journal", "{".toByteArray())
        assertThrows(StorageRecoveryRequired::class.java) { store.read() }
        secure.write("maintenance-journal", ByteArray(256 * 1024 + 1))
        assertThrows(StorageRecoveryRequired::class.java) { store.read() }
    }
    @Test fun receiptMustBindExactFlowSourceAndState() {
        val store = MaintenanceStore(MemorySecureStore())
        val valid = journal().copy(kind = MaintenanceKind.ROTATION, phase = MaintenancePhase.BACKED_UP,
            sourceId = F.publishId, stateDigest = "state", receipt = MaintenanceReceipt(F.publishId, F.publishId, "state", 100))
        store.write(valid); assertEquals(valid, store.read())
        store.write(valid.copy(stateDigest = "changed"))
        assertThrows(StorageRecoveryRequired::class.java) { store.read() }
    }
    @Test fun stateDigestChangesForWorkHistoryAndPublicationIdentity() {
        val c = F.catalog(); val s = SnapshotState(publishId = F.publishId, confirmedPayloadJson = "😀".repeat(10000))
        fun digest(state: SnapshotState = s, history: Int = 0) = maintenanceStateDigest(c, state, emptyList(), emptyList(), history)
        assertEquals(digest(), digest()); assertEquals(64, digest().length)
        assertNotEquals(digest(), digest(history = 1))
        assertNotEquals(digest(), digest(s.copy(publishId = null)))
        assertNotEquals(MaintenanceStore.digest("ab"), MaintenanceStore.digest("a\u0000b"))
    }
    @Test fun failedWritesDoNotPublishChangeNotifications() {
        val secure = MemorySecureStore().apply { failWrites = true }
        val before = MaintenanceStore.changes.value
        assertThrows(StorageRecoveryRequired::class.java) { MaintenanceStore(secure).write(journal()) }
        assertEquals(before, MaintenanceStore.changes.value)
    }
}
