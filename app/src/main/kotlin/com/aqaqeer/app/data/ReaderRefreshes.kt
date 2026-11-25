package com.aqaqeer.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.aqaqeer.app.sync.SnapshotReadError
import com.aqaqeer.app.sync.SnapshotReadException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.readerRefreshStore by preferencesDataStore("reader_refresh")
private val scheduleKey = stringPreferencesKey("schedule")
private const val minuteMillis = 60_000L
private const val workName = "reader-catalog-refresh"

internal data class ReaderRefreshTarget(val connectionId: String, val lastSuccess: Long?, val requiresUpdate: Boolean)
internal class ReaderRefreshCooldown(val minutesRemaining: Long) : Exception()
internal class ReaderRefreshSkipped : Exception()
internal class ReaderRefreshDeferred : Exception()

@Serializable
private data class ReaderRefreshSchedule(
    val connectionId: String,
    val lastAttempt: Long?,
    val nextCheck: Long,
    val observedSuccess: Long? = null,
    val retryPending: Boolean = false,
    val serverWaitUntil: Long = 0,
    val readError: SnapshotReadError? = null,
) {
    val needsConnection get() = readError == SnapshotReadError.INVALID_CREDENTIALS || readError == SnapshotReadError.ACCESS_DENIED
    val manualDeadline get() = maxOf(serverWaitUntil, lastAttempt?.plus(20 * minuteMillis) ?: 0)
    val automaticDeadline get() = if (retryPending) maxOf(nextCheck, serverWaitUntil) else maxOf(nextCheck, manualDeadline)
}

/** Installation-local timing and one shared in-flight Reader check. */
internal class ReaderRefreshes(
    private val context: Context,
    private val target: suspend () -> ReaderRefreshTarget?,
    private val read: suspend (String, suspend () -> Unit) -> RefreshOutcome,
) {
    private val store = context.readerRefreshStore
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val work by lazy { WorkManager.getInstance(context) }
    private val gate = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flight: Deferred<RefreshOutcome?>? = null
    private var flightConnection: String? = null

    suspend fun check(automatic: Boolean, background: Boolean, onStarted: () -> Unit): RefreshOutcome? {
        val request = gate.withLock {
            val current = target()
            if (current == null) {
                if (background || stored() != null) schedule(null)
                return null
            }
            flight?.takeIf { it.isActive }?.let { return@withLock it.takeIf { flightConnection == current.connectionId } }
            val now = System.currentTimeMillis()
            val previous = stored()
            val plan = previous?.takeIf { it.connectionId == current.connectionId && it.observedSuccess == current.lastSuccess }
                ?: initialSchedule(current, previous, now).also { save(it) }
            val paused = current.requiresUpdate || plan.needsConnection
            val deadline = if (automatic) plan.automaticDeadline else plan.manualDeadline
            if ((automatic && paused) || now < deadline || !hasNetwork(automatic)) {
                schedule(plan.automaticDeadline.takeUnless { paused })
                if (!automatic && now < deadline) throw ReaderRefreshCooldown((deadline - now + minuteMillis - 1) / minuteMillis)
                if (background && !paused) throw ReaderRefreshDeferred()
                if (automatic && !background) {
                    if (current.requiresUpdate) throw RefreshException(RefreshError.UPDATE_REQUIRED)
                    plan.readError?.takeUnless { it == SnapshotReadError.RATE_LIMITED }?.let { throw SnapshotReadException(it) }
                }
                return null
            }
            // Keep a wake-up in place while a foreground caller owns the attempt.
            if (!background) schedule(plan.automaticDeadline.takeUnless { paused })
            flightConnection = current.connectionId
            scope.async(start = CoroutineStart.LAZY) { attempt(current, plan, automatic) }
                .also { flight = it; it.start() }
        } ?: return null
        onStarted()
        return request.await()
    }

    suspend fun cancelIfIneligible() = gate.withLock {
        if (stored() != null && target() == null) schedule(null)
    }

    private suspend fun attempt(current: ReaderRefreshTarget, plan: ReaderRefreshSchedule, automatic: Boolean): RefreshOutcome? {
        var issued = plan
        val outcome = try {
            read(current.connectionId) {
                val live = target()
                if (live == null || live.connectionId != current.connectionId ||
                    (automatic && (live.requiresUpdate || live.lastSuccess != current.lastSuccess))) throw ReaderRefreshSkipped()
                if (!hasNetwork(automatic)) throw ReaderRefreshDeferred()
                val now = System.currentTimeMillis()
                // Persist an attempt before transport access so a process restart cannot issue it again immediately.
                issued = plan.copy(lastAttempt = now, nextCheck = regularDeadline(now), retryPending = false)
                save(issued)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: ReaderRefreshSkipped) {
            finish(issued)
            return null
        } catch (error: ReaderRefreshDeferred) {
            throw error
        } catch (error: Exception) {
            val now = System.currentTimeMillis()
            val remote = error as? SnapshotReadException
            val code = remote?.error
            val rateLimited = remote?.error == SnapshotReadError.RATE_LIMITED
            val serverWait = if (rateLimited) now + maxOf(2, remote.retryAfterSeconds) * 1000L else issued.serverWaitUntil
            val retry = !plan.retryPending && code in setOf(SnapshotReadError.NETWORK, SnapshotReadError.CHANGED_DURING_READ, SnapshotReadError.RATE_LIMITED)
            val scheduled = if (retry) now + retryDelay() else regularDeadline(now)
            val next = if (rateLimited) maxOf(scheduled, serverWait + Random.nextLong(15_000, minuteMillis)) else scheduled
            val persistentError = if (plan.needsConnection) plan.readError else code
            finish(issued.copy(nextCheck = next, retryPending = retry, serverWaitUntil = serverWait, readError = persistentError))
            throw error
        }
        finish(issued, success = true)
        return outcome
    }

    private suspend fun finish(plan: ReaderRefreshSchedule, success: Boolean = false) = gate.withLock {
        val current = target()
        val now = System.currentTimeMillis()
        val updated = when {
            current != null && success && current.connectionId == plan.connectionId ->
                initialSchedule(current, plan, now).copy(lastAttempt = plan.lastAttempt)
            current != null && (current.connectionId != plan.connectionId || current.lastSuccess != plan.observedSuccess) ->
                initialSchedule(current, plan, now)
            else -> plan
        }
        save(updated)
        schedule(updated.automaticDeadline.takeIf {
            current != null && !current.requiresUpdate && !updated.needsConnection
        })
    }

    private fun initialSchedule(current: ReaderRefreshTarget, previous: ReaderRefreshSchedule?, now: Long) =
        ReaderRefreshSchedule(
            connectionId = current.connectionId,
            lastAttempt = listOfNotNull(previous?.lastAttempt, current.lastSuccess).maxOrNull(),
            nextCheck = regularDeadline(current.lastSuccess?.coerceAtMost(now) ?: now),
            observedSuccess = current.lastSuccess,
        )

    private suspend fun schedule(deadline: Long?) = withContext(Dispatchers.IO) {
        val existing = work.getWorkInfosForUniqueWorkFlow(workName).first().filterNot { it.state.isFinished }
        if (deadline == null) {
            if (existing.isNotEmpty()) work.cancelUniqueWork(workName).await()
            return@withContext
        }
        val tag = deadline.toString()
        if (existing.any { tag in it.tags }) return@withContext
        val request = OneTimeWorkRequestBuilder<ReaderRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay((deadline - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .addTag(tag)
            .build()
        // A Worker can replace its own wake-up after committing the next deadline. The enqueue
        // and any joined foreground caller belong to the application-owned attempt.
        work.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request).await()
        Unit
    }

    private fun hasNetwork(automatic: Boolean): Boolean {
        val network = connectivity.activeNetwork ?: return false
        return !automatic || connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private suspend fun stored(): ReaderRefreshSchedule? = store.data.first()[scheduleKey]?.let { Json.decodeFromString(it) }
    private suspend fun save(plan: ReaderRefreshSchedule) {
        store.edit { it[scheduleKey] = Json.encodeToString(plan) }
    }
    private fun regularDeadline(from: Long) = from + Random.nextLong(12 * 60 * minuteMillis, 14 * 60 * minuteMillis + 1)
    private fun retryDelay() = Random.nextLong(5 * minuteMillis, 7 * minuteMillis + 1)
}
