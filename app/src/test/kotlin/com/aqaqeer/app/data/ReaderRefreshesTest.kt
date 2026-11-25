package com.aqaqeer.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.aqaqeer.app.sync.SnapshotReadError
import com.aqaqeer.app.sync.SnapshotReadException
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.ReadOnlyProperty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class ReaderRefreshesTest {
    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private val writes = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            writes.withLock { transform(data.value).also { data.value = it } }
    }

    private val context = mockk<Context>()
    private val store = MemoryPreferences()
    private val work = mockk<WorkManager>()
    private val scheduled = MutableStateFlow<List<WorkInfo>>(emptyList())
    private val enqueues = AtomicInteger()
    private val attempts = AtomicInteger()

    @Volatile private var connected = true
    @Volatile private var validated = true
    @Volatile private var target: ReaderRefreshTarget? =
        ReaderRefreshTarget("test-connection", System.currentTimeMillis() - 25 * hour, false)
    @Volatile private var beforeAdmission: () -> Unit = {}
    @Volatile private var result: suspend () -> RefreshOutcome = {
        target = target!!.copy(lastSuccess = System.currentTimeMillis())
        RefreshOutcome.UNCHANGED
    }

    private fun coordinator() = ReaderRefreshes(context, { target }) { connection, beforeRequest ->
        assertEquals(target?.connectionId, connection)
        beforeAdmission()
        beforeRequest()
        attempts.incrementAndGet()
        result()
    }

    @Before fun isolateAndroidBoundaries() {
        stores[context] = store
        val connectivity = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivity
        every { connectivity.activeNetwork } answers { network.takeIf { connected } }
        every { connectivity.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } answers { validated }

        mockkStatic(WorkManager::class)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns work
        val future = mockk<ListenableFuture<Operation.State.SUCCESS>>()
        every { future.isDone } returns true
        every { future.isCancelled } returns false
        every { future.get() } returns mockk<Operation.State.SUCCESS>()
        val completed = mockk<Operation>()
        every { completed.getResult() } returns future
        every { work.getWorkInfosForUniqueWorkFlow(workName) } returns scheduled
        every { work.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>()) } answers {
            val request = thirdArg<OneTimeWorkRequest>()
            assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
            assertTrue(request.workSpec.initialDelay >= 0)
            val info = mockk<WorkInfo>()
            every { info.state } returns WorkInfo.State.ENQUEUED
            every { info.tags } returns request.tags.toSet()
            scheduled.value = listOf(info)
            enqueues.incrementAndGet()
            completed
        }
        every { work.cancelUniqueWork(workName) } answers {
            scheduled.value = emptyList()
            completed
        }
    }

    @After fun releaseMocks() {
        stores.remove(context)
        unmockkObject(WorkManager.Companion)
        unmockkStatic(WorkManager::class)
    }

    private suspend fun plan() = Json.parseToJsonElement(requireNotNull(store.data.first()[scheduleKey])).jsonObject
    private fun JsonObject.time(name: String) = getValue(name).jsonPrimitive.long
    private suspend fun setTime(name: String, value: Long) {
        val updated = JsonObject(plan() + (name to JsonPrimitive(value)))
        store.edit { it[scheduleKey] = updated.toString() }
    }
    private fun deadline() = scheduled.value.single().tags.mapNotNull(String::toLongOrNull).single()

    private suspend inline fun <reified T : Exception> rejects(crossinline action: suspend () -> Unit): T =
        try {
            action()
            throw AssertionError("Expected ${T::class.java.simpleName}")
        } catch (error: Exception) {
            if (error is T) error else throw error
        }

    @Test fun reopeningBeforeDeadlineKeepsOneJobAndSendsNothing(): Unit = runBlocking {
        val verified = System.currentTimeMillis() - hour
        target = target!!.copy(lastSuccess = verified)
        repeat(3) { assertNull(coordinator().check(automatic = true, background = false) {}) }
        assertEquals(0, attempts.get())
        assertEquals(1, enqueues.get())
        assertTrue(deadline() in (verified + 12 * hour)..(verified + 14 * hour))
        assertEquals(plan().time("nextCheck"), deadline())
    }

    @Test fun unchangedReadReschedulesAndManualCooldownSurvivesCoordinatorRecreation(): Unit = runBlocking {
        val refresh = coordinator()
        assertEquals(RefreshOutcome.UNCHANGED, refresh.check(automatic = true, background = false) {})
        val persistedStart = plan().time("lastAttempt")
        val verified = target!!.lastSuccess!!
        assertTrue(deadline() in (verified + 12 * hour)..(verified + 14 * hour))
        val reopened = coordinator()
        assertTrue(rejects<ReaderRefreshCooldown> { reopened.check(automatic = false, background = false) {} }
            .minutesRemaining in 19L..20L)
        assertEquals(persistedStart, plan().time("lastAttempt"))
        assertEquals(1, attempts.get())
        setTime("lastAttempt", System.currentTimeMillis() - 21 * minute)
        assertEquals(RefreshOutcome.UNCHANGED, reopened.check(automatic = false, background = false) {})
        assertEquals(2, attempts.get())
    }

    @Test fun offlineDoesNotConsumeAttemptAndUnvalidatedNetworkOnlyAllowsManualCheck(): Unit = runBlocking {
        val refresh = coordinator()
        connected = false
        val oldSuccess = target!!.lastSuccess
        assertNull(refresh.check(automatic = false, background = false) {})
        rejects<ReaderRefreshDeferred> { refresh.check(automatic = true, background = true) {} }
        assertEquals(oldSuccess, plan().time("lastAttempt"))
        assertEquals(0, attempts.get())
        connected = true
        validated = false
        assertNull(refresh.check(automatic = true, background = false) {})
        assertEquals(0, attempts.get())
        assertEquals(RefreshOutcome.UNCHANGED, refresh.check(automatic = false, background = false) {})
        assertEquals(1, attempts.get())
    }

    @Test fun oneShortRetryBypassesManualCooldownThenReturnsToRegularInterval(): Unit = runBlocking {
        result = { throw SnapshotReadException(SnapshotReadError.NETWORK) }
        val refresh = coordinator()
        val start = System.currentTimeMillis()
        rejects<SnapshotReadException> { refresh.check(automatic = true, background = true) {} }
        assertTrue(deadline() in (start + 5 * minute)..(System.currentTimeMillis() + 7 * minute))
        assertTrue(plan().getValue("retryPending").jsonPrimitive.boolean)
        rejects<ReaderRefreshCooldown> { refresh.check(automatic = false, background = false) {} }
        assertEquals(1, attempts.get())

        // Advance the saved due time, leaving the original manual cooldown in force.
        setTime("nextCheck", System.currentTimeMillis() - 1)
        val retryStart = System.currentTimeMillis()
        rejects<SnapshotReadException> { refresh.check(automatic = true, background = true) {} }
        assertEquals(2, attempts.get())
        assertTrue(plan().time("lastAttempt") >= retryStart)
        assertFalse(plan()["retryPending"]?.jsonPrimitive?.boolean ?: false)
        assertTrue(deadline() in (retryStart + 12 * hour)..(System.currentTimeMillis() + 14 * hour))
        rejects<ReaderRefreshCooldown> { refresh.check(automatic = false, background = false) {} }
        assertEquals(2, attempts.get())
    }

    @Test fun telegramDelayIsPersistedAndGatesEveryTrigger(): Unit = runBlocking {
        val retryAfterSeconds = 45 * 60
        val failure = SnapshotReadException(SnapshotReadError.RATE_LIMITED, retryAfterSeconds)
        result = { throw failure }
        val start = System.currentTimeMillis()
        assertSame(failure, rejects<SnapshotReadException> {
            coordinator().check(automatic = false, background = false) {}
        })
        val serverDeadline = plan().time("serverWaitUntil")
        assertTrue(serverDeadline >= start + 45 * minute)
        assertTrue(deadline() in (serverDeadline + 15_000)..(serverDeadline + minute))
        val reopened = coordinator()
        val cooldown = rejects<ReaderRefreshCooldown> { reopened.check(automatic = false, background = false) {} }
        assertTrue(cooldown.minutesRemaining in 44L..45L)
        assertNull(reopened.check(automatic = true, background = false) {})
        rejects<ReaderRefreshDeferred> { reopened.check(automatic = true, background = true) {} }
        assertEquals(1, attempts.get())
    }

    @Test fun revokedCredentialsPauseAutomaticWorkUntilTheConnectionChanges(): Unit = runBlocking {
        val revoked = SnapshotReadException(SnapshotReadError.INVALID_CREDENTIALS)
        result = { throw revoked }
        val refresh = coordinator()
        assertSame(revoked, rejects<SnapshotReadException> { refresh.check(automatic = true, background = false) {} })
        assertTrue(scheduled.value.isEmpty())
        assertEquals(revoked.error, rejects<SnapshotReadException> {
            refresh.check(automatic = true, background = false) {}
        }.error)
        assertEquals(1, attempts.get())

        val verified = System.currentTimeMillis()
        target = ReaderRefreshTarget("replacement-connection", verified, false)
        assertNull(refresh.check(automatic = true, background = false) {})
        assertTrue(deadline() in (verified + 12 * hour)..(verified + 14 * hour))
        assertEquals(1, attempts.get())
    }

    @Test fun missingPinStaysOnRegularCadenceWithoutAQuickRetry(): Unit = runBlocking {
        val missing = SnapshotReadException(SnapshotReadError.SNAPSHOT_UNAVAILABLE)
        result = { throw missing }
        val refresh = coordinator()
        val started = System.currentTimeMillis()
        assertSame(missing, rejects<SnapshotReadException> { refresh.check(automatic = true, background = true) {} })
        assertTrue(deadline() in (started + 12 * hour)..(System.currentTimeMillis() + 14 * hour))
        assertFalse(plan()["retryPending"]?.jsonPrimitive?.boolean ?: false)
        assertEquals(missing.error, rejects<SnapshotReadException> {
            refresh.check(automatic = true, background = false) {}
        }.error)
        assertEquals(1, attempts.get())
    }

    @Test fun leavingBeforeTransportDoesNotConsumeTheCooldownOrKeepTheJob(): Unit = runBlocking {
        val verified = target!!.lastSuccess
        beforeAdmission = { target = null }
        assertNull(coordinator().check(automatic = true, background = false) {})
        assertEquals(verified, plan().time("lastAttempt"))
        assertEquals(0, attempts.get())
        assertTrue(scheduled.value.isEmpty())
    }

    @Test fun foregroundAndWorkerShareARequestEvenWhenOneWaiterCancels(): Unit = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val joined = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            result = {
                entered.complete(Unit)
                release.await()
                target = target!!.copy(lastSuccess = System.currentTimeMillis())
                RefreshOutcome.APPLIED
            }
            val refresh = coordinator()
            val worker = async { refresh.check(automatic = true, background = true) {} }
            try {
                entered.await()
                val foreground = async { refresh.check(automatic = false, background = false) { joined.complete(Unit) } }
                joined.await()
                worker.cancelAndJoin()
                release.complete(Unit)
                assertEquals(RefreshOutcome.APPLIED, foreground.await())
                assertEquals(1, attempts.get())
            } finally {
                release.complete(Unit)
            }
        }
    }

    @Test fun requiredUpdatePausesWorkerAndLosingReaderRoleCancelsPendingJob(): Unit = runBlocking {
        val refresh = coordinator()
        target = target!!.copy(lastSuccess = System.currentTimeMillis())
        assertNull(refresh.check(automatic = true, background = false) {})
        assertEquals(1, scheduled.value.size)
        target = target!!.copy(requiresUpdate = true)
        assertNull(refresh.check(automatic = true, background = true) {})
        assertTrue(scheduled.value.isEmpty())
        assertEquals(RefreshError.UPDATE_REQUIRED, rejects<RefreshException> {
            refresh.check(automatic = true, background = false) {}
        }.error)
        target = target!!.copy(requiresUpdate = false)
        assertNull(refresh.check(automatic = true, background = false) {})
        assertEquals(1, scheduled.value.size)
        target = null
        refresh.cancelIfIneligible()
        assertTrue(scheduled.value.isEmpty())
        assertEquals(0, attempts.get())
    }

    companion object {
        private const val workName = "reader-catalog-refresh"
        private const val minute = 60_000L
        private const val hour = 60 * minute
        private val scheduleKey = stringPreferencesKey("schedule")
        private val stores = ConcurrentHashMap<Context, DataStore<Preferences>>()
        @BeforeClass @JvmStatic fun redirectReaderDelegate() {
            mockkStatic(::preferencesDataStore)
            val delegate = ReadOnlyProperty<Context, DataStore<Preferences>> { context, _ -> stores.getValue(context) }
            every { preferencesDataStore("reader_refresh", null, any(), any()) } returns delegate
        }

        @AfterClass @JvmStatic fun restoreReaderDelegate() {
            unmockkStatic(::preferencesDataStore)
        }
    }
}
