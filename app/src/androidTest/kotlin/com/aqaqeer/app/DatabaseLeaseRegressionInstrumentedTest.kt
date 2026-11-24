package com.aqaqeer.app

import com.aqaqeer.app.admin.catalog

import com.aqaqeer.app.fixtures.SuiteProfile
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class DatabaseLeaseRegressionInstrumentedTest {
    private lateinit var p: SuiteProfile
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        p = SuiteProfile(); p.create(); p.medication()
    }
    @After fun cleanup() { if (::p.isInitialized) p.close() }

    @Test fun resetWaitsForEveryLeaseAndBlocksNewAcquisitionWithoutLosingData(): Unit = runBlocking {
        withTimeout(20_000) {
            val first = p.factory.acquire(); val second = p.factory.acquire()
            try {
                assertSame(first.database, second.database)
                val before = first.database.catalogDao().catalog()
                // UNDISPATCHED reaches users.first while holding the production lease gate.
                val reset = async(start = CoroutineStart.UNDISPATCHED) { p.factory.reset() }
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { p.factory.acquire() }
                assertFalse(reset.isCompleted); assertFalse(waiting.isCompleted)
                first.close(); first.close()
                assertFalse(reset.isCompleted); assertFalse(waiting.isCompleted)
                assertEquals(before, second.database.catalogDao().catalog())
                second.close(); reset.await()
                val fresh = waiting.await()
                try {
                    assertNotSame(first.database, fresh.database)
                    assertEquals(before, fresh.database.catalogDao().catalog())
                } finally { fresh.close() }
            } finally { first.close(); second.close(); p.factory.reset() }
        }
    }

    @Test fun cancelledResetReleasesTheGateAndLeavesTheBorrowedDatabaseUsable(): Unit = runBlocking {
        withTimeout(20_000) {
            val lease = p.factory.acquire()
            try {
                val before = lease.database.catalogDao().catalog()
                val reset = async(start = CoroutineStart.UNDISPATCHED) { p.factory.reset() }
                assertFalse(reset.isCompleted)
                reset.cancelAndJoin(); assertTrue(reset.isCancelled)
                val another = p.factory.acquire()
                try {
                    assertSame(lease.database, another.database)
                    assertEquals(before, another.database.catalogDao().catalog())
                } finally { another.close() }
            } finally { lease.close(); p.factory.reset() }
        }
    }
}
