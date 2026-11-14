package com.aqaqeer.app.di

import android.content.Context
import com.aqaqeer.app.data.DatabaseFactory
import com.aqaqeer.app.data.DisplayPreferences
import com.aqaqeer.app.security.KeystoreSecureStore
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.files.EnvelopeReader
import com.aqaqeer.app.sync.SnapshotReadClient
import com.aqaqeer.app.sync.SnapshotReadTransport
import com.aqaqeer.app.sync.TelegramSnapshotReadTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides fun clock(): java.time.Clock = java.time.Clock.systemUTC()
    @Provides @Singleton internal fun telegramPublishing() = com.aqaqeer.app.sync.TelegramPublishing()
    @Provides internal fun telegramSetup(value: com.aqaqeer.app.sync.TelegramPublishing): com.aqaqeer.app.sync.TelegramSetupTransport = value
    @Provides internal fun telegramWrites(value: com.aqaqeer.app.sync.TelegramPublishing): com.aqaqeer.app.sync.SnapshotWriteTransport = value

    @Provides @Singleton fun factory(@ApplicationContext context: Context) = DatabaseFactory(context)
    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = DisplayPreferences(context)
    @Provides @Singleton fun secureStore(@ApplicationContext context: Context): SecureStore = KeystoreSecureStore(context)
    @Provides @Singleton fun snapshotTransport(): SnapshotReadTransport = TelegramSnapshotReadTransport()
    @Provides @Singleton fun snapshotReads(@ApplicationContext context: Context, transport: SnapshotReadTransport) =
        SnapshotReadClient(transport, EnvelopeReader(context.noBackupFilesDir))
}
