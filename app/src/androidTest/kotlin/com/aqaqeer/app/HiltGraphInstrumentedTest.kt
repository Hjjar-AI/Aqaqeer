package com.aqaqeer.app

import com.aqaqeer.app.admin.*
import com.aqaqeer.app.backup.BackupRepository
import com.aqaqeer.app.data.*
import com.aqaqeer.app.di.AppModule
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.reports.*
import com.aqaqeer.app.security.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidTest
@UninstallModules(AppModule::class)
class HiltGraphInstrumentedTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject internal lateinit var profile: SuiteProfile
    @Inject lateinit var catalog: CatalogRepository
    @Inject lateinit var factory: DatabaseFactory
    @Inject lateinit var secure: SecureStore
    @Inject lateinit var admin: AdminRepository
    @Inject lateinit var backups: BackupRepository
    @Inject lateinit var history: HistoryRepository
    @Inject lateinit var reports: ReportRepository
    @Inject lateinit var imports: CsvImportRepository
    @Inject lateinit var maintenance: MaintenanceCoordinator
    @Inject lateinit var pins: PinChanges
    @Inject lateinit var clock: Clock
    @Inject internal lateinit var transport: SnapshotReadTransport
    @Before fun inject() { hilt.inject() }
    @After fun cleanup() { if (::profile.isInitialized) profile.close() }
    @Test fun fullGraphUsesReplacementStorageClockAndTransportBindings() = runBlocking {
        assertSame(profile.factory, factory); assertSame(profile.secure, secure)
        assertEquals(profile.clock.instant(), clock.instant()); assertTrue(transport is SuiteRemote)
        assertNotNull(backups); assertNotNull(history); assertNotNull(reports); assertNotNull(imports); assertNotNull(maintenance); assertNotNull(pins)
        profile.ready()
        if (!BuildConfig.IS_DEMO) {
            admin.create("حقن", "Asia/Damascus", "محرر", "قسم", "123456".toCharArray(), "123456".toCharArray())
            val db = factory.open()
            try { assertEquals(clock.millis(), db.catalogDao().control()!!.firstAdminAt) } finally { db.close() }
        }
    }
    @Module @InstallIn(SingletonComponent::class)
    object Bindings {
        @Provides @Singleton internal fun profile() = SuiteProfile()
        @Provides internal fun clock(profile: SuiteProfile): Clock = profile.clock
        @Provides @Singleton internal fun factory(profile: SuiteProfile): DatabaseFactory = profile.factory
        @Provides @Singleton internal fun secure(profile: SuiteProfile): SecureStore = profile.secure
        @Provides @Singleton fun preferences(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context) = DisplayPreferences(context)
        @Provides @Singleton internal fun remote() = SuiteRemote()
        @Provides internal fun setup(remote: SuiteRemote): TelegramSetupTransport = remote
        @Provides internal fun writes(remote: SuiteRemote): SnapshotWriteTransport = remote
        @Provides internal fun reads(remote: SuiteRemote): SnapshotReadTransport = remote
        @Provides @Singleton internal fun client(profile: SuiteProfile, transport: SnapshotReadTransport) = SnapshotReadClient(transport, EnvelopeReader(profile.root))
    }
}
