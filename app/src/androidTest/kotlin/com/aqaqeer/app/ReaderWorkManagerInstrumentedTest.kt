package com.aqaqeer.app

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.data.ConnectionImportRepository
import com.aqaqeer.app.data.DisplayPreferences
import com.aqaqeer.app.data.HistoryRepository
import com.aqaqeer.app.data.ReaderRefreshCooldown
import com.aqaqeer.app.data.ReaderRefreshWorker
import com.aqaqeer.app.data.SnapshotRefreshRepository
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.fixtures.SuiteRemote
import com.aqaqeer.app.maintenance.MaintenanceCoordinator
import com.aqaqeer.app.maintenance.MaintenanceStage
import com.aqaqeer.app.maintenance.MaintenanceStore
import com.aqaqeer.app.maintenance.PinChanges
import com.aqaqeer.app.reports.CsvImportRepository
import com.aqaqeer.app.reports.DocumentOutput
import com.aqaqeer.app.reports.PdfReports
import com.aqaqeer.app.reports.ReportRepository
import com.aqaqeer.app.security.files.EnvelopeReader
import com.aqaqeer.app.security.files.SnapshotPublication
import com.aqaqeer.app.fixtures.M3Fixtures
import com.aqaqeer.app.sync.BotIdentity
import com.aqaqeer.app.sync.SnapshotDocument
import com.aqaqeer.app.sync.SnapshotReadClient
import com.aqaqeer.app.sync.SnapshotReadError
import com.aqaqeer.app.sync.SnapshotReadException
import com.aqaqeer.app.sync.SnapshotReadTransport
import com.aqaqeer.app.ui.CatalogViewModel
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@HiltAndroidTest
class ReaderWorkManagerInstrumentedTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @BindValue lateinit var refreshBinding: SnapshotRefreshRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.applicationContext
    private val models = ViewModelStore()
    private val remote = SuiteRemote()
    private val failNextRead = AtomicBoolean()
    private val pinReads = AtomicInteger()
    private val jobs = mutableListOf<UUID>()
    private lateinit var profile: SuiteProfile
    private lateinit var work: WorkManager
    private lateinit var reads: SnapshotReadClient
    private lateinit var refresh: SnapshotRefreshRepository
    private lateinit var initialSync: Instant
    private lateinit var pinned: SnapshotDocument
    private var preserveProfile = false
    private val phase get() = InstrumentationRegistry.getArguments().getString("readerLifecyclePhase")
    private val checkpoint get() = File(profile.root, "reader-lifecycle.txt")
    private var interruptedJob: UUID? = null

    @Before fun prepare(): Unit = runBlocking {
        assumeFalse(BuildConfig.IS_DEMO)
        val namespace = InstrumentationRegistry.getArguments().getString("readerLifecycleProfile")
        if (phase != null) {
            requireNotNull(namespace)
            require(namespace.matches(Regex("suite_[a-f0-9]{32}")))
            val existing = File(context.noBackupFilesDir, namespace)
            check(existing.exists() == (phase == "verify"))
        }
        profile = if (namespace == null) SuiteProfile() else SuiteProfile(namespace)
        if (phase != "verify") {
            profile.create()
            profile.medication()
            profile.publish(remote)
            val past = Instant.ofEpochMilli(System.currentTimeMillis()).minusSeconds(24 * 60 * 60)
            profile.admin.transaction { dao ->
                dao.updateSnapshotState(requireNotNull(dao.snapshotState()).copy(lastSuccessfulSyncAt = past))
            }
        }
        profile.admin.transaction { dao ->
            val snapshot = requireNotNull(dao.snapshotState())
            initialSync = requireNotNull(snapshot.lastSuccessfulSyncAt)
            pinned = SnapshotDocument(M3Fixtures.groupId, requireNotNull(snapshot.pinnedMessageId),
                requireNotNull(snapshot.documentFileId), requireNotNull(snapshot.documentFileUniqueId),
                SnapshotPublication(requireNotNull(snapshot.revision), requireNotNull(snapshot.publishId),
                    Instant.parse(requireNotNull(snapshot.publishedAt))))
        }
        val transport = object : SnapshotReadTransport by remote {
            override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument {
                pinReads.incrementAndGet()
                if (failNextRead.compareAndSet(true, false)) throw SnapshotReadException(SnapshotReadError.NETWORK)
                if (phase == "kill") {
                    writeCheckpoint(requireNotNull(interruptedJob), 0)
                    awaitCancellation()
                }
                return pinned
            }
        }
        reads = SnapshotReadClient(transport, EnvelopeReader(profile.root))
        val files = File(profile.root, "reader-files").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
        }
        refresh = SnapshotRefreshRepository(profile.factory, profile.secure, reads, profile.requirements, isolated)
        refreshBinding = refresh
        hilt.inject()
        work = runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
            WorkManager.getInstance(context)
        }
        if (phase != "verify") {
            assertTrue(work.getWorkInfosForUniqueWorkFlow("reader-catalog-refresh").first().all { it.state.isFinished })
        }
    }

    @After fun cleanup(): Unit = runBlocking {
        if (preserveProfile) return@runBlocking
        instrumentation.runOnMainSync { models.clear() }
        if (::work.isInitialized) {
            jobs.forEach { work.cancelWorkById(it).await() }
            if (::refresh.isInitialized && ::profile.isInitialized) {
                profile.admin.leave("صيدلية الاختبار", true)
                refresh.cancelReaderWorkIfIneligible()
            }
        }
        if (::profile.isInitialized) profile.close()
    }

    private suspend fun demote() {
        profile.admin.transaction { dao ->
            dao.updateDeviceIdentity(requireNotNull(dao.deviceIdentity()).copy(adminMode = false))
        }
    }

    private suspend fun enqueue(delayMillis: Long = 0): UUID {
        val request = OneTimeWorkRequestBuilder<ReaderRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        jobs += request.id
        interruptedJob = request.id
        work.enqueue(request).await()
        return request.id
    }

    private suspend fun awaitSuccess(id: UUID, timeoutMillis: Long = 60_000) {
        val terminal = withTimeout(timeoutMillis) { work.getWorkInfoByIdFlow(id).first { it?.state?.isFinished == true } }
        assertEquals(WorkInfo.State.SUCCEEDED, terminal?.state)
    }

    private fun writeCheckpoint(id: UUID, notBefore: Long) {
        val temporary = File(profile.root, "reader-lifecycle.new")
        temporary.writeText("${Process.myPid()} $id $notBefore\n")
        check(temporary.renameTo(checkpoint))
    }

    private fun resumeJob(): Pair<Int, UUID> {
        val values = checkpoint.readText().trim().split(' ')
        check(values.size == 3)
        val id = UUID.fromString(values[1])
        jobs += id
        return values[0].toInt() to id
    }

    private suspend fun nextPendingCheck() = withTimeout(15_000) {
        val now = System.currentTimeMillis()
        work.getWorkInfosForUniqueWorkFlow("reader-catalog-refresh").first { items ->
            items.any { it.state == WorkInfo.State.ENQUEUED && it.tags.any { tag -> (tag.toLongOrNull() ?: 0L) > now } }
        }
            .single { !it.state.isFinished }
    }

    private fun networkValidated(): Boolean {
        val networks = context.getSystemService(ConnectivityManager::class.java)
        return networks.getNetworkCapabilities(networks.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText().trim() }

    @Test fun dueReaderWorkerWaitsForNetworkThenCommitsAndSchedulesNextCheck(): Unit = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("ownedReaderNetworkFixture") == "true")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        assertTrue(shell("settings get global airplane_mode_on") in setOf("0", "null"))
        val power = context.getSystemService(PowerManager::class.java)
        assertFalse("The owned emulator must start with Battery Saver off", power.isPowerSaveMode)
        demote()
        assertTrue("The owned emulator must start with a validated network", networkValidated())
        try {
            shell("cmd connectivity airplane-mode enable")
            val manager = context.getSystemService(ConnectivityManager::class.java)
            withTimeout(20_000) {
                while (manager.activeNetwork != null) delay(100)
            }
            val id = enqueue()
            assertEquals(WorkInfo.State.ENQUEUED, work.getWorkInfoByIdFlow(id).first()?.state)
            delay(2_000)
            assertEquals(0, pinReads.get())
            assertEquals(initialSync, profile.ready().lastSuccessfulRefreshAt)
            println("Reader network observation: disconnected, requests=${pinReads.get()}")

            shell("dumpsys battery unplug")
            shell("cmd power set-mode 1")
            withTimeout(10_000) { while (!power.isPowerSaveMode) delay(100) }
            shell("cmd connectivity airplane-mode disable")
            withTimeout(45_000) { while (!networkValidated()) delay(100) }
            println("Reader Battery Saver observation: enabled=${power.isPowerSaveMode}, " +
                "work=${work.getWorkInfoByIdFlow(id).first()?.state}, pinReads=${pinReads.get()}")
            shell("cmd power set-mode 0")
            shell("dumpsys battery reset")
            awaitSuccess(id)
            assertEquals("One verified fetch reads the pin before and after validation", 2, pinReads.get())
            assertTrue(requireNotNull(profile.ready().lastSuccessfulRefreshAt).isAfter(initialSync))
            nextPendingCheck()
        } finally {
            shell("cmd connectivity airplane-mode disable")
            shell("cmd power set-mode 0")
            shell("dumpsys battery reset")
        }
    }

    @Test fun verifiedWorkerCheckClearsVisibleForegroundErrorForSamePharmacy(): Unit = runBlocking {
        assertTrue("The owned emulator must have a validated network", networkValidated())
        val profileContext = profile.context
        val preferences = DisplayPreferences(profileContext)
        val output = DocumentOutput(profileContext)
        val stage = MaintenanceStage(profileContext)
        val store = MaintenanceStore(profile.secure)
        val maintenance = MaintenanceCoordinator(store, stage, profile.admin, reads, remote, remote, remote,
            profile.requirements, profile.backups, profileContext)
        lateinit var model: CatalogViewModel
        instrumentation.runOnMainSync {
            val owner = object : ViewModelStoreOwner { override val viewModelStore = models }
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogViewModel(profile.repository, preferences, SavedStateHandle(),
                        ConnectionImportRepository(profileContext, profile.repository, profile.secure, reads, profile.requirements),
                        refresh, HistoryRepository(profile.factory), CsvImportRepository(profile.admin, output, profileContext),
                        ReportRepository(profile.admin, output, PdfReports(profileContext)), profile.admin, profile.sharing(remote),
                        profile.backups, maintenance, PinChanges(profile.admin, store), profileContext) as T
            }
            model = ViewModelProvider(owner, factory)[CatalogViewModel::class.java]
        }
        demote()
        val reader = withTimeout(15_000) {
            model.state.first { (it.catalog as? CatalogState.Ready)?.let { ready ->
                !ready.admin && ready.lifecycle == "SHARED"
            } == true }
        }
        assertEquals(initialSync, (reader.catalog as CatalogState.Ready).lastSuccessfulRefreshAt)
        failNextRead.set(true)
        instrumentation.runOnMainSync { model.refreshCatalog() }
        withTimeout(15_000) { model.refreshUiState.first { it.error != null && !it.busy } }
        assertEquals(1, pinReads.get())
        val retry = nextPendingCheck()
        val retryAt = retry.tags.mapNotNull(String::toLongOrNull).single()
        assertTrue("The foreground failure scheduled its short retry", retryAt - System.currentTimeMillis() in 1L..420_000L)
        withTimeout(480_000) {
            model.state.first {
                (it.catalog as? CatalogState.Ready)?.lastSuccessfulRefreshAt?.toEpochMilli()?.let { it >= retryAt } == true &&
                    it.refresh.error == null
            }
        }
        nextPendingCheck()
        assertEquals("One failed pin read followed by one verified worker fetch", 3, pinReads.get())
    }

    @Test fun interruptedReaderAttemptSurvivesProcessDeathWithoutImmediateDuplicate(): Unit = runBlocking {
        assertTrue("The owned emulator must have a validated network", networkValidated())
        when (phase) {
            "kill" -> {
                demote()
                enqueue()
                // The host kills this exact process after the pin callback confirms admission.
                delay(60_000)
                fail("The host did not terminate the admitted Reader attempt")
            }
            "verify" -> {
                val (previousPid, id) = resumeJob()
                assertNotEquals(previousPid, Process.myPid())
                assertEquals(initialSync, profile.ready().lastSuccessfulRefreshAt)
                val persisted = requireNotNull(work.getWorkInfoByIdFlow(id).first())
                assertFalse(persisted.state.isFinished)
                val cooldown = assertThrows(ReaderRefreshCooldown::class.java) {
                    runBlocking { refresh.refreshReader(automatic = false) }
                }
                assertTrue(cooldown.minutesRemaining in 1L..20L)
                assertEquals(0, pinReads.get())
                nextPendingCheck()
            }
            else -> error("This case requires the owned emulator process-death phases")
        }
    }

    @Test fun pendingReaderWorkResumesAfterEmulatorReboot(): Unit = runBlocking {
        when (phase) {
            "reboot" -> {
                demote()
                val delayMillis = 135_000L
                val notBefore = System.currentTimeMillis() + delayMillis
                val id = enqueue(delayMillis)
                assertEquals(WorkInfo.State.ENQUEUED, work.getWorkInfoByIdFlow(id).first()?.state)
                assertEquals(0, pinReads.get())
                writeCheckpoint(id, notBefore)
                preserveProfile = true
            }
            "verify" -> {
                val (_, id) = resumeJob()
                val pending = requireNotNull(work.getWorkInfoByIdFlow(id).first())
                assertEquals("The identical queued work item survived reboot", WorkInfo.State.ENQUEUED, pending.state)
                withTimeout(45_000) { while (!networkValidated()) delay(100) }
                awaitSuccess(id, timeoutMillis = 160_000)
                val committed = requireNotNull(profile.ready().lastSuccessfulRefreshAt)
                assertTrue(committed.toEpochMilli() >= checkpoint.readText().trim().substringAfterLast(' ').toLong())
                assertEquals(2, pinReads.get())
                nextPendingCheck()
            }
            else -> error("This case requires the owned emulator reboot phases")
        }
    }
}
