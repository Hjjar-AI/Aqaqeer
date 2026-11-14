package com.aqaqeer.app.security.files

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.fixtures.MemorySecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SnapshotRequirementsStoreTest {
    @Test fun requirementsSurviveRestartPendingDiscardAndOlderCredentialReimport() {
        val secure = MemorySecureStore(); val requirements = SnapshotRequirementsStore(secure)
        val pending = PendingConnectionStore(secure); val first = F.candidate().use { pending.stage(it) }
        requirements.observe(F.pharmacyId, SnapshotCompatibility(2, 100, 1))
        pending.discard(first.id); F.candidate().use { pending.stage(it) }
        val reopened = SnapshotRequirementsStore(secure)
        assertEquals(SnapshotRequirements(2,100), reopened.read(F.pharmacyId))
        reopened.observe(F.pharmacyId, SnapshotCompatibility(1,1,1))
        assertEquals(SnapshotRequirements(2,100), reopened.read(F.pharmacyId))
        assertNull(reopened.read(F.otherPharmacyId))
    }
    @Test fun appUpgradeChangesEligibilityWithoutClearingSavedRequirement() {
        val secure = MemorySecureStore(); val store = SnapshotRequirementsStore(secure)
        val requirement = store.observe(F.pharmacyId, SnapshotCompatibility(1, 10, 1))
        assertTrue(requirement.requiresUpdate(3)); assertFalse(requirement.requiresUpdate(10))
        assertEquals(requirement, store.read(F.pharmacyId))
        assertTrue(SnapshotRequirements(2,1).requiresUpdate(Int.MAX_VALUE))
    }
    @Test fun concurrentObservationsCannotLoseHigherSchemaOrMinimum() {
        val store = SnapshotRequirementsStore(MemorySecureStore()); val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = (1..40).map { minimum -> workers.submit { store.observe(F.pharmacyId, SnapshotCompatibility(if (minimum == 20) 2 else 1, minimum,1)) } }
            tasks.forEach { it.get(5,TimeUnit.SECONDS) }
            assertEquals(SnapshotRequirements(2,40),store.read(F.pharmacyId))
        } finally { workers.shutdownNow() }
    }
    @Test fun malformedProtectedRequirementIsPreservedAndBlocksObservation() {
        for (bytes in listOf(byteArrayOf(1), ByteBuffer.allocate(12).putInt(0x41515231).putInt(0).putInt(1).array())) {
            val secure = MemorySecureStore(); val name = "snapshot-requirements-${F.pharmacyId}"
            secure.entries[name] = bytes.copyOf(); val store = SnapshotRequirementsStore(secure)
            assertThrows(StorageRecoveryRequired::class.java) { store.read(F.pharmacyId) }
            assertThrows(StorageRecoveryRequired::class.java) { store.observe(F.pharmacyId,SnapshotCompatibility(1,1,1)) }
            assertArrayEquals(bytes,secure.entries[name]); assertEquals(0,secure.writes)
        }
    }
    @Test fun failedWritePreservesPreviouslyKnownRequirementAndInputBuffersAreCleared() {
        val secure = MemorySecureStore(); val store = SnapshotRequirementsStore(secure)
        store.observe(F.pharmacyId,SnapshotCompatibility(1,5,1)); val before = secure.entries.values.single().copyOf()
        secure.failWrites = true
        assertThrows(StorageRecoveryRequired::class.java) { store.observe(F.pharmacyId,SnapshotCompatibility(2,10,1)) }
        assertArrayEquals(before,secure.entries.values.single())
        assertTrue(secure.lastRead!!.all { it == 0.toByte() })
    }
}
