package com.aqaqeer.app.security

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.fixtures.MemorySecureStore
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.security.files.*
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ProtectedReadCacheTest {
    @Before fun clearBefore() { ProtectedReadCache.invalidateAll() }
    @After fun clearAfter() { ProtectedReadCache.invalidateAll() }
    private class Store(override val cacheScope: Any = Any()) : SecureStore {
        var bytes: ByteArray? = "value".toByteArray()
        var reads = 0
        var last: ByteArray? = null
        var fail = false
        override fun read(name: String): ByteArray? {
            reads++
            if (fail) throw StorageRecoveryRequired()
            return bytes?.copyOf()?.also { last = it }
        }
        override fun write(name: String, value: ByteArray) { bytes = value.copyOf() }
    }
    private fun read(s: Store, name: String = "metadata") = ProtectedReadCache.read(s, name) { it.toString(Charsets.UTF_8) }
    @Test fun decodedValueIsCachedAndTemporaryBytesAreErased() {
        val s = Store(); assertEquals("value", read(s)); assertEquals("value", read(s))
        assertEquals(1, s.reads); assertTrue(s.last!!.all { it == 0.toByte() })
        assertEquals("value", s.bytes!!.toString(Charsets.UTF_8))
    }
    @Test fun absentValueIsCachedUntilInvalidated() {
        val s = Store().apply { bytes = null }; assertNull(read(s)); s.bytes = "new".toByteArray()
        assertNull(read(s)); assertEquals(1, s.reads)
        ProtectedReadCache.invalidate(s, "metadata"); assertEquals("new", read(s))
    }
    @Test fun decodedNullIsCachedWithoutRetainingInput() {
        val s = Store(); var decodes = 0
        repeat(2) { assertNull(ProtectedReadCache.read<String>(s, "metadata") { decodes++; null }) }
        assertEquals(1, decodes); assertTrue(s.last!!.all { it == 0.toByte() })
    }
    @Test fun differentScopesNeverShareValues() {
        val a = Store(); val b = Store().apply { bytes = "other".toByteArray() }
        assertEquals("value", read(a)); assertEquals("other", read(b)); assertEquals(1, b.reads)
    }
    @Test fun sameScopeAllowsReopenedStoreToReuseMetadata() {
        val scope = Any(); val a = Store(scope); val b = Store(scope)
        assertEquals("value", read(a)); assertEquals("value", read(b)); assertEquals(0, b.reads)
        ProtectedReadCache.invalidate(b, "metadata"); assertEquals("value", read(a)); assertEquals(2, a.reads)
    }
    @Test fun entryNamesAreIndependent() {
        val s = Store(); read(s, "one"); s.bytes = "two".toByteArray()
        assertEquals("two", read(s, "two")); assertEquals("value", read(s, "one"))
    }
    @Test fun targetedInvalidationPreservesOtherEntries() {
        val s = Store(); read(s, "one"); read(s, "two"); s.bytes = "fresh".toByteArray()
        ProtectedReadCache.invalidate(s, "one")
        assertEquals("fresh", read(s, "one")); assertEquals("value", read(s, "two"))
    }
    @Test fun globalInvalidationDropsAllScopes() {
        val a = Store(); val b = Store(); read(a); read(b)
        a.bytes = "a".toByteArray(); b.bytes = "b".toByteArray(); ProtectedReadCache.invalidateAll()
        assertEquals("a", read(a)); assertEquals("b", read(b))
    }
    @Test fun readFailureIsNotCachedAndRetryReadsAgain() {
        val s = Store().apply { fail = true }
        assertThrows(StorageRecoveryRequired::class.java) { read(s) }
        s.fail = false; assertEquals("value", read(s)); assertEquals(2, s.reads)
    }
    @Test fun decodeFailureWipesBytesAndAllowsRetry() {
        val s = Store()
        assertThrows(IllegalArgumentException::class.java) { ProtectedReadCache.read<String>(s, "metadata") { throw IllegalArgumentException("bad") } }
        assertTrue(s.last!!.all { it == 0.toByte() }); assertEquals("value", read(s)); assertEquals(2, s.reads)
    }
    @Test fun concurrentReadersDecodeOnce() {
        val s = Store(); val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..16).map { pool.submit(Callable { read(s) }) }
            futures.forEach { assertEquals("value", it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, s.reads)
        } finally { pool.shutdownNow() }
    }
    @Test fun successfulRequirementWriteInvalidatesAnAlreadyCachedAbsence() {
        val secure = MemorySecureStore(); val store = SnapshotRequirementsStore(secure)
        assertNull(store.read(F.pharmacyId)); store.observe(F.pharmacyId, SnapshotCompatibility(2, 100, 1))
        assertEquals(SnapshotRequirements(2, 100), SnapshotRequirementsStore(secure).read(F.pharmacyId))
    }
    @Test fun failedRequirementWriteInvalidatesAndPreservesOldValue() {
        val secure = MemorySecureStore(); val store = SnapshotRequirementsStore(secure)
        store.observe(F.pharmacyId, SnapshotCompatibility(1, 5, 1)); store.read(F.pharmacyId)
        secure.failWrites = true
        assertThrows(StorageRecoveryRequired::class.java) { store.observe(F.pharmacyId, SnapshotCompatibility(2, 100, 1)) }
        assertEquals(SnapshotRequirements(1, 5), store.read(F.pharmacyId))
    }
    @Test fun maintenanceWriteAndClearInvalidateDecodedJournal() {
        val store = MaintenanceStore(MemorySecureStore())
        val journal = MaintenanceJournal(id = F.publishId, pharmacyId = F.pharmacyId, kind = MaintenanceKind.REPLACEMENT)
        assertNull(store.read()); store.write(journal); assertEquals(journal, store.read())
        store.write(null); assertNull(store.read())
    }
    @Test fun failedMaintenanceWriteDoesNotExposeUncommittedJournal() {
        val secure = MemorySecureStore(); val store = MaintenanceStore(secure)
        val journal = MaintenanceJournal(id = F.publishId, pharmacyId = F.pharmacyId, kind = MaintenanceKind.REPLACEMENT)
        store.write(journal); assertEquals(journal, store.read()); secure.failWrites = true
        assertThrows(StorageRecoveryRequired::class.java) { store.write(journal.copy(phase = MaintenancePhase.CANCELLING)) }
        assertEquals(journal, store.read())
    }
}
