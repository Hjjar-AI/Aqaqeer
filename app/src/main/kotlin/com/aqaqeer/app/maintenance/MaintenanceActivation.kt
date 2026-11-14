package com.aqaqeer.app.maintenance

import androidx.sqlite.db.SimpleSQLiteQuery
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.PendingConnectionStore
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

internal fun maintenanceCheckpoint(dao: CatalogDao, journal: MaintenanceJournal, field: String): Boolean {
    val id = journal.checkpointId ?: return false
    return dao.backupCursor(SimpleSQLiteQuery("SELECT COUNT(*) FROM history WHERE id=? AND entityType='maintenance' AND entityId=? AND field=? AND newValue=?",
        arrayOf<Any?>(id, journal.pharmacyId, field, JsonPrimitive(journal.id).toString()))).use { it.moveToFirst(); it.getInt(0) == 1 }
}
internal suspend fun recordMaintenanceCheckpoint(dao: CatalogDao, journal: MaintenanceJournal, field: String) {
    val device = requireNotNull(dao.deviceIdentity())
    dao.insertHistory(listOf(HistoryEntry(requireNotNull(journal.checkpointId), null, "maintenance", journal.pharmacyId,
        requireNotNull(dao.pharmacy()).name, field, null, JsonPrimitive(journal.id).toString(), device.id, device.displayName, Instant.now(), null, null)))
}

/** Only a checkpoint already committed in Room can authorize switching protected credentials. */
internal suspend fun finishMaintenanceActivation(dao: CatalogDao, store: MaintenanceStore, stage: MaintenanceStage) {
    val journal = store.read() ?: return
    if (journal.phase == MaintenancePhase.CANCELLING) {
        stage.clear(); store.discardConnections(); store.write(null); return
    }
    if (journal.phase == MaintenancePhase.COMPLETE && maintenanceCheckpoint(dao, journal, "maintenanceFinished")) {
        stage.clear(); store.discardConnections(); store.write(null); return
    }
    val original = store.connection("old").info() ?: throw StorageRecoveryRequired()
    if (original.id != journal.id || original.pharmacyId != journal.pharmacyId) throw StorageRecoveryRequired()
    if (journal.stateDigest != null) stage.validate(journal.pharmacyId)
    val needsCandidate = journal.phase !in setOf(MaintenancePhase.PREPARING, MaintenancePhase.SOURCE_READY,
        MaintenancePhase.BACKED_UP, MaintenancePhase.TOKEN_CHANGE)
    if (needsCandidate) {
        val candidate = store.connection(journal.activationEntry).info() ?: throw StorageRecoveryRequired()
        if (candidate.id != journal.id || candidate.pharmacyId != journal.pharmacyId) throw StorageRecoveryRequired()
    }
    if (journal.phase == MaintenancePhase.ACTIVATING && maintenanceCheckpoint(dao, journal, "connectionActivated")) {
        if (dao.pharmacy()?.pharmacyId != journal.pharmacyId || dao.snapshotMetadata()?.publishId != journal.candidateId) throw StorageRecoveryRequired()
        store.connection(journal.activationEntry).withCandidate(journal.id) { candidate ->
            if (candidate.pharmacyId != journal.pharmacyId || candidate.keyVersion != dao.pharmacy()?.keyVersion) throw StorageRecoveryRequired()
            PendingConnectionStore(store.secure, "active-connection").retain(candidate, journal.id)
        }
        store.write(journal.copy(phase = if (journal.kind == MaintenanceKind.REPLACEMENT) MaintenancePhase.REVIEW else MaintenancePhase.COMPLETE))
    }
}
