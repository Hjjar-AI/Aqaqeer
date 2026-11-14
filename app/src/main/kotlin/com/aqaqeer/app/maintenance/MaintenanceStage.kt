package com.aqaqeer.app.maintenance

import android.content.Context
import androidx.room.withTransaction
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class MaintenanceBaseline(val catalog: CatalogSnapshot, val state: SnapshotState,
    val pending: List<PendingOperation>, val attempts: List<PublishAttempt>, val historyCount: Int = 0)

/** Isolated SQLCipher namespace; uses existing tables and bounded large-text readers. */
@Singleton
class MaintenanceStage @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val factory = DatabaseFactory(context, "maintenance_stage")
    private suspend fun <T> transaction(action: suspend (CatalogDao) -> T): T = withContext(Dispatchers.IO) {
        val db = factory.open()
        try { db.withTransaction { action(db.catalogDao()) } } finally { db.close() }
    }
    internal suspend fun validate(pharmacyId: String) = transaction { dao ->
        check(dao.pharmacy()?.pharmacyId == pharmacyId && dao.snapshotMetadata() != null)
    }
    internal suspend fun save(base: MaintenanceBaseline) = transaction { dao ->
        dao.attempts().forEach { dao.deleteAttempt(it.publishId) }
        dao.deletePending(dao.pending().map { it.id }); dao.clearHistory(); dao.replace(base.catalog)
        if (dao.snapshotMetadata() == null) dao.insertSnapshotState(base.state) else dao.updateSnapshotState(base.state)
        dao.putPending(base.pending); base.attempts.forEach { dao.attempt(it) }
    }
    internal suspend fun baseline(flowId: String) = transaction { dao ->
        MaintenanceBaseline(dao.catalog(), requireNotNull(dao.snapshotState()), dao.pending(), dao.attempts().filterNot { it.publishId == flowId })
    }
    internal suspend fun payload(flowId: String, value: String) = transaction { dao ->
        dao.attempt(PublishAttempt(flowId, null, value, "[]", "PREPARED"))
    }
    internal suspend fun payloadOrNull(flowId: String): String? = transaction { dao -> dao.attempts().find { it.publishId == flowId }?.candidatePayloadJson }
    internal suspend fun payload(flowId: String): String = requireNotNull(payloadOrNull(flowId))
    /** Only the existing confirmed Leave path may remove unreadable operation staging. */
    internal suspend fun discardAfterConfirmedLeave() = withContext(Dispatchers.IO) {
        val path = context.getDatabasePath(factory.databaseName)
        check(!path.exists() || context.deleteDatabase(factory.databaseName))
    }
    internal suspend fun clear() = transaction { dao ->
        dao.attempts().forEach { dao.deleteAttempt(it.publishId) }; dao.deletePending(dao.pending().map { it.id })
        dao.clearHistory(); dao.clearAvailability(); dao.clearMedications(); dao.clearSections(); dao.clearCategories(); dao.clearForms()
        dao.clearTombstones(); dao.clearPharmacy(); dao.snapshotMetadata()?.let { dao.updateSnapshotState(SnapshotState()) }
    }
}
