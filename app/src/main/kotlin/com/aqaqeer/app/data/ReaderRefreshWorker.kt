package com.aqaqeer.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.EnvelopeException
import com.aqaqeer.app.security.files.PayloadException
import com.aqaqeer.app.sync.SnapshotReadException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class ReaderRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (BuildConfig.IS_DEMO) return Result.success()
        return try {
            EntryPointAccessors.fromApplication(applicationContext, ReaderRefreshEntryPoint::class.java)
                .snapshotRefreshRepository().refreshReader(automatic = true, background = true)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: ReaderRefreshDeferred) {
            Result.retry()
        } catch (_: SnapshotReadException) {
            Result.success()
        } catch (_: RefreshException) {
            Result.success()
        } catch (_: EnvelopeException) {
            Result.success()
        } catch (_: PayloadException) {
            Result.success()
        } catch (_: StorageRecoveryRequired) {
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReaderRefreshEntryPoint {
    fun snapshotRefreshRepository(): SnapshotRefreshRepository
}
