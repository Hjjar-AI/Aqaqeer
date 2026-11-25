package com.aqaqeer.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Installation-local presentation state, excluded from explicit catalog backups and shared files. */
internal object FetchedChanges {
    // Order launch/finish events and post-refresh commits without blocking activity creation.
    val sessionDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val sessionScope = CoroutineScope(SupervisorJob() + sessionDispatcher)
    private val mutable = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val ids = mutable.asStateFlow()
    private var active = false
    private var session = 0L
    private var preferences: android.content.SharedPreferences? = null
    private val retained = mutableMapOf<Pair<String, String>, Long>()

    fun scheduleSessionStart(context: Context) {
        val application = context.applicationContext
        sessionScope.launch { beginSession(application) }
    }

    fun scheduleTaskFinish() {
        sessionScope.launch { finishTask() }
    }

    @Synchronized fun beginSession(context: Context) {
        if (active) return
        load(context, newSession = true)
        active = true
        persist()
    }

    private fun load(context: Context, newSession: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences("fetched_changes", Context.MODE_PRIVATE)
        preferences = prefs
        session = prefs.getLong("session", 0L) + if (newSession) 1 else 0
        retained.clear()
        prefs.getStringSet("markers", emptySet()).orEmpty().forEach { line ->
            val parts = line.split('|')
            if (parts.size == 3) parts[2].toLongOrNull()?.takeIf { it >= session }?.let {
                retained[parts[0] to parts[1]] = it
            }
        }
    }
    @Synchronized fun finishTask() { active = false }

    @Synchronized fun record(context: Context, pharmacyId: String, medications: Set<String>) {
        if (medications.isEmpty()) return
        if (preferences == null) load(context, newSession = false)
        val expiry = session + if (active) 1 else 2
        medications.forEach { retained[pharmacyId to it] = expiry }
        persist()
    }
    private fun persist() {
        // Small non-secret state is committed before advertising it to the browse projection.
        val stored = preferences?.edit()?.putLong("session", session)?.putStringSet("markers",
            retained.map { (key, expiry) -> "${key.first}|${key.second}|$expiry" }.toSet())?.commit() == true
        if (stored) mutable.value = retained.keys.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }
}
