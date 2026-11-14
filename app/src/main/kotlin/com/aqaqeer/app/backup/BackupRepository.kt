package com.aqaqeer.app.backup

import com.aqaqeer.app.maintenance.*
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class RestoreJournal(val id: String, val active: String?, val setup: String?,
    val pharmacyId: String, val requiredSchema: Int, val requiredMinimum: Int)

/** Replay only after the matching Room replacement committed; every step is idempotent. */
internal suspend fun finishRestoreActivation(dao: CatalogDao, secure: SecureStore, requirements: SnapshotRequirementsStore) {
    val control = dao.control() ?: return
    val id = control.restoreActivationId ?: return
    val bytes = secure.read("restore-journal") ?: throw StorageRecoveryRequired()
    val journal = try { Json.decodeFromString<RestoreJournal>(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
    check(journal.id == id && dao.pharmacy()?.pharmacyId == journal.pharmacyId)
    for ((name, value) in listOf("active-connection" to journal.active, "setup-connection" to journal.setup, "pending-connection" to null)) {
        val record = value?.let { Base64.getDecoder().decode(it) } ?: byteArrayOf(0x41, 0x51, 0x50, 0x31, 0)
        try { secure.write(name, record) } finally { record.fill(0) }
    }
    requirements.observe(journal.pharmacyId, SnapshotCompatibility(journal.requiredSchema, journal.requiredMinimum, requireNotNull(dao.pharmacy()).keyVersion))
    MaintenanceStore(secure).clearForRestore()
    dao.control(control.copy(restoreActivationId = null))
    // Keep the journal until a later operation replaces it; clearing before Room commits is unsafe.
}

internal data class BackupReminder(val firstAdminAt: Long?, val lastExportAt: Long?, val dismissedAt: Long?) {
    fun nextAt(): Long? = firstAdminAt?.let { maxOf(it, lastExportAt ?: it, dismissedAt ?: it) + WEEK }
    companion object { const val WEEK = 7L * 24 * 60 * 60 * 1000 }
}

@Singleton
class BackupRepository @Inject constructor(@param:ApplicationContext private val context: Context,
    private val factory: DatabaseFactory, private val secure: SecureStore,
    private val admin: AdminRepository, private val requirements: SnapshotRequirementsStore, private val clock: Clock) {
    private var openDatabase: (() -> AqaqeerDatabase)? = null
    private var openDestination: (Uri) -> java.io.OutputStream? = { context.contentResolver.openOutputStream(it, "wt") }

    /** Internal dependency seam for real SQL/stream interruption tests; app injection uses the defaults. */
    internal constructor(context: Context, factory: DatabaseFactory, secure: SecureStore,
        admin: AdminRepository, requirements: SnapshotRequirementsStore, clock: Clock,
        openDatabase: () -> AqaqeerDatabase, openDestination: (Uri) -> java.io.OutputStream?) :
        this(context, factory, secure, admin, requirements, clock) {
        this.openDatabase = openDatabase
        this.openDestination = openDestination
    }

    private val codec: BackupEnvelopeCodec = V1BackupEnvelope()
    private val root = File(context.noBackupFilesDir, "aqaqeer-backup-stage")
    private val stageFactory = DatabaseFactory(context, "backup_stage")

    private fun staging(): File {
        check(root.isDirectory || root.mkdirs())
        root.listFiles().orEmpty().forEach { check(it.isFile && it.delete()) }
        val storage = context.getSystemService(android.os.storage.StorageManager::class.java)
        check(storage.getAllocatableBytes(storage.getUuidForPath(root)) >= V1BackupEnvelope.LIMIT * 5) { "Insufficient private storage" }
        return root
    }
    private suspend fun authorized(dao: CatalogDao) {
        check(!BuildConfig.IS_DEMO && dao.deviceIdentity()?.adminMode == true && dao.pharmacy() != null)
        check(dao.control()?.leaving != true && dao.control()?.restoreActivationId == null)
    }
    private suspend fun connection(entry: String): ConnectionWire? {
        val store = PendingConnectionStore(secure, entry); val info = store.info() ?: return null
        return store.withCandidate(info.id) { candidate ->
            val s = candidate.copySecrets()
            try { ConnectionWire(1, candidate.pharmacyId, candidate.pharmacyName, s.botToken, s.groupId,
                Base64.getEncoder().encodeToString(s.snapshotKey), s.keyVersion, candidate.createdAt, candidate.createdBy) }
            finally { s.snapshotKey.fill(0) }
        }
    }
    private suspend fun header(dao: CatalogDao): BackupHeader {
        val p = requireNotNull(dao.pharmacy()); val state = dao.snapshotMetadata(); val known = requirements.read(p.pharmacyId)
        return BackupHeader(lifecycle = requireNotNull(dao.control()).lifecycle,
            active = if (dao.control()?.lifecycle == "SHARED") connection("active-connection")?.takeIf { it.pharmacyId == p.pharmacyId } else null,
            setup = connection("setup-connection")?.takeIf { it.pharmacyId == p.pharmacyId },
            requiredSchemaVersion = maxOf(p.schemaVersion, state?.requiredSchemaVersion ?: 1, known?.schemaVersion ?: 1),
            requiredMinVersionCode = maxOf(p.minVersionCode, state?.requiredMinVersionCode ?: 1, known?.minVersionCode ?: 1))
    }

    suspend fun export(uri: Uri, password: CharArray, confirmation: CharArray) = exportCore(uri, password, confirmation, null)

    internal suspend fun exportForMaintenance(flowId: String, uri: Uri, password: CharArray, confirmation: CharArray): MaintenanceReceipt {
        exportCore(uri, password, confirmation, flowId)
        val journal = requireNotNull(MaintenanceStore(secure).read()); check(journal.id == flowId)
        return requireNotNull(journal.receipt)
    }

    private suspend fun exportCore(uri: Uri, password: CharArray, confirmation: CharArray, maintenanceId: String?) = try { withContext(Dispatchers.IO) {
            V1BackupEnvelope.validatePassword(password); require(password.contentEquals(confirmation))
            admin.sharingGate.withLock {
                val maintenance = if (maintenanceId == null) null else requireNotNull(MaintenanceStore(secure).read()).also {
                    check(it.id == maintenanceId && it.kind != MaintenanceKind.REPLACEMENT && it.sourceDigest != null &&
                        it.phase in setOf(MaintenancePhase.SOURCE_READY, MaintenancePhase.BACKED_UP, MaintenancePhase.TOKEN_CHANGE))
                }
                val directory = staging(); val ctx = currentCoroutineContext(); val checkpoint = { ctx.ensureActive() }
                ProtectedSpool(directory).use { plain ->
                    val lease = if (openDatabase == null) factory.acquire() else null
                val db = lease?.database ?: requireNotNull(openDatabase).invoke()
                    val pharmacyId: String
                    try { pharmacyId = db.withTransaction {
                        val dao = db.catalogDao(); finishRestoreActivation(dao, secure, requirements); authorized(dao)
                        if (maintenance != null) {
                            admin.guard(dao, allowMaintenance = true)
                            check(dao.snapshotMetadata()?.publishId == maintenance.sourceId && dao.pendingCount() == 0 && dao.attemptCount() == 0)
                            check(maintenanceStateDigest(dao.catalog(), requireNotNull(dao.snapshotState()), dao.pending(), dao.attempts(), dao.historyCount()) == maintenance.stateDigest)
                        }
                        val h = header(dao)
                        plain.output().bufferedWriter(Charsets.UTF_8).use { out -> BackupArchive.capture(db.openHelper.writableDatabase, h, out, checkpoint) }
                        requireNotNull(dao.pharmacy()).pharmacyId
                    } } finally { if (lease != null) lease.close() else db.close() }
                    val envelope = File.createTempFile("backup-", ".aqbak", directory)
                    try {
                        codec.write(plain, envelope, password)
                        clearStageDatabase()
                        val validation = stageFactory.open()
                        try { readStage(envelope.inputStream(), directory, password, validation, checkpoint) }
                        finally { validation.close(); clearStageDatabase() }
                        checkpoint()
                        (openDestination(uri) ?: error("Cannot open backup destination")).use { output ->
                            envelope.inputStream().use { input -> val buffer = ByteArray(65536)
                                while (true) { checkpoint(); val n = readBackupChunk(input, buffer); if (n < 0) break; output.write(buffer, 0, n) }
                            }
                        }
                        // Destination close above is part of success, even if the coroutine is cancelled next.
                        withContext(NonCancellable) { admin.transaction { dao ->
                            if (dao.pharmacy()?.pharmacyId == pharmacyId) dao.control(requireNotNull(dao.control()).copy(lastBackupAt = clock.millis()))
                            if (maintenance != null) {
                                val store = MaintenanceStore(secure); check(store.read() == maintenance)
                                check(maintenanceStateDigest(dao.catalog(), requireNotNull(dao.snapshotState()), dao.pending(), dao.attempts(), dao.historyCount()) == maintenance.stateDigest)
                                store.write(maintenance.copy(receipt = MaintenanceReceipt(maintenance.id, requireNotNull(maintenance.sourceId),
                                    requireNotNull(maintenance.stateDigest), clock.millis()), phase = if (maintenance.phase == MaintenancePhase.TOKEN_CHANGE)
                                        MaintenancePhase.TOKEN_CHANGE else MaintenancePhase.BACKED_UP))
                            }
                        } }
                    } finally { if (envelope.exists() && !envelope.delete()) throw java.io.IOException("Backup staging cleanup failed") }
                }
            }
        }
    } finally { password.fill('\u0000'); confirmation.fill('\u0000') }

    suspend fun restore(uri: Uri, password: CharArray, typedName: String, confirmedReplace: Boolean) = try { withContext(Dispatchers.IO) {
        admin.sharingGate.withLock {
            val directory = staging(); val ctx = currentCoroutineContext(); val checkpoint = { ctx.ensureActive() }
            check(!BuildConfig.IS_DEMO)
            check(MaintenanceStore(secure).read() == null) { "أكمل الصيانة أو ألغها قبل الاستعادة" }
            val before = admin.transaction { dao ->
                finishRestoreActivation(dao, secure, requirements)
                Triple(dao.pharmacy()?.also { authorized(dao); require(confirmedReplace && typedName == it.name) }, dao.historyCount(), dao.snapshotMetadata())
            }
            clearStageDatabase()
            val staged = stageFactory.open()
            try {
                val h = readStage(context.contentResolver.openInputStream(uri) ?: error("Cannot open backup"),
                    directory, password, staged, checkpoint)
                checkpoint()
                val journal = journal(h, requireNotNull(staged.catalogDao().pharmacy()).pharmacyId)
                val journalBytes = Json.encodeToString(journal).toByteArray(Charsets.UTF_8)
                try { secure.write("restore-journal", journalBytes) } finally { journalBytes.fill(0) }
                val lease = if (openDatabase == null) factory.acquire() else null
                val db = lease?.database ?: requireNotNull(openDatabase).invoke()
                try { db.withTransaction {
                    val dao = db.catalogDao(); check(dao.pharmacy()?.pharmacyId == before.first?.pharmacyId && dao.historyCount() == before.second && dao.snapshotMetadata() == before.third)
                    if (before.first != null) { authorized(dao); require(dao.pharmacy()?.name == typedName && confirmedReplace) }
                    val device = requireNotNull(dao.deviceIdentity())
                    val target = db.openHelper.writableDatabase
                    // The encrypted staging tables remain intact until the whole replacement commits.
                    backupTables.keys.toList().asReversed().forEach { table -> target.execSQL("DELETE FROM `$table`") }
                    copyCells(staged.openHelper.writableDatabase, target, checkpoint)
                    BackupArchive.materialize(target, checkpoint, validateUtf8 = false)
                    target.execSQL("DROP TABLE backup_cells")
                    dao.updateDeviceIdentity(device.copy(adminMode = false))
                    val previousControl = requireNotNull(dao.control())
                    val samePharmacy = before.first?.pharmacyId == journal.pharmacyId
                    dao.control(LocalControl(lifecycle = h.lifecycle, restorePaused = true, restoreActivationId = journal.id,
                        firstAdminAt = if (samePharmacy) previousControl.firstAdminAt else null,
                        lastBackupAt = if (samePharmacy) previousControl.lastBackupAt else null,
                        backupDismissedAt = if (samePharmacy) previousControl.backupDismissedAt else null))
                }
                withContext(NonCancellable) { db.withTransaction { finishRestoreActivation(db.catalogDao(), secure, requirements) } }
                } finally { if (lease != null) lease.close() else db.close() }
            } finally { staged.close(); clearStageDatabase() }
        }
    } } finally { password.fill('\u0000') }

    private suspend fun readStage(source: java.io.InputStream, directory: File, password: CharArray,
        staged: AqaqeerDatabase, check: () -> Unit): BackupHeader {
        val header = codec.read(source, directory, password).use { plain ->
            staged.withTransaction {
                val db = staged.openHelper.writableDatabase
                val header = BackupArchive.parse(plain, db, check)
                BackupArchive.materialize(db, check); header
            }
        }
        validate(staged, header, check)
        return header
    }

    private suspend fun validate(db: AqaqeerDatabase, h: BackupHeader, check: () -> Unit) {
        require(h.schemaVersion == 1 && h.lifecycle in setOf("LOCAL_DRAFT", "SHARED"))
        require(h.requiredSchemaVersion > 0 && h.requiredMinVersionCode > 0)
        val dao = db.catalogDao(); val catalog = dao.catalog(); CatalogRules.validate(catalog)
        val p = requireNotNull(catalog.pharmacy)
        val sql = db.openHelper.writableDatabase
        for (table in listOf("pharmacy", "snapshot_state")) sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); require(it.getLong(0) == 1L) }
        require(dao.attemptCount() <= 1 && p.schemaVersion == 1)
        require(h.requiredSchemaVersion >= p.schemaVersion && h.requiredMinVersionCode >= p.minVersionCode)
        val pin = p.adminPinVerifier
        require(Base64.getDecoder().decode(pin.salt).size == 16 && Base64.getDecoder().decode(pin.hash).size == 32)
        for (wire in listOfNotNull(h.active, h.setup)) {
            PayloadReader().connection(AuthenticatedJson(Json.encodeToString(wire).toByteArray(Charsets.UTF_8), EnvelopeDomain.CONNECTION), p.pharmacyId).use {
                require(it.keyVersion == p.keyVersion)
            }
        }
        val state = requireNotNull(dao.snapshotState()); require(state.slot == 1)
        require(h.requiredSchemaVersion >= (state.requiredSchemaVersion ?: 1) && h.requiredMinVersionCode >= (state.requiredMinVersionCode ?: 1))
        val base = state.confirmedPayloadJson?.let { validateSnapshot(it, p) }
        require(state.publishedAt == base?.publishedAt?.toString())
        if (h.lifecycle == "SHARED") {
            require(h.active != null && base != null && base.publishId == state.publishId && base.revision == state.revision)
            require(h.requiredSchemaVersion >= base.compatibility.schemaVersion && h.requiredMinVersionCode >= base.compatibility.minVersionCode)
        }
        else require(base == null && h.active == null && state.revision == null && state.publishId == null)
        val ops = dao.pending()
        ops.forEach { op ->
            require(op.authorName.isNotBlank() && op.authorName == op.authorName.trim())
            op.basePublishId?.let { require(java.util.UUID.fromString(it).toString() == it) }
            require(op.entityType != "pharmacy" || (op.targetId == p.pharmacyId && op.field != "@entity"))
        }
        CatalogEdits.merge(base?.catalog ?: catalog, catalog, base?.catalog ?: catalog, ops)
        require(ops.map { it.id }.distinct().size == ops.size)
        if (h.lifecycle == "LOCAL_DRAFT" && dao.attemptCount() > 0) require(h.setup != null)
        dao.attempts().forEach { attempt ->
            check(); val candidate = validateSnapshot(attempt.candidatePayloadJson, p)
            require(candidate.publishId == attempt.publishId && h.requiredSchemaVersion >= candidate.compatibility.schemaVersion && h.requiredMinVersionCode >= candidate.compatibility.minVersionCode)
            val attemptBase = attempt.basePayloadJson?.let { validateSnapshot(it, p) }
            require(candidate.parentPublishId == attemptBase?.publishId && candidate.basedOnRevision == (attemptBase?.revision ?: 0L))
            val included = Json.decodeFromString<List<String>>(attempt.includedOperationIdsJson)
            require(included.distinct().size == included.size && included.all { id -> ops.any { it.id == id } })
            require(attempt.transportPhase in setOf("PREPARED", "UPLOAD_STARTED", "UPLOADED", "WRITE_STARTED"))
        }
        // Validate local-only history metadata one row at a time; large recovery values remain encrypted SQL TEXT.
        sql.query("SELECT id,operationId,editorId,editedAt,observedAt,publishedRevision FROM history").use { rows ->
            while (rows.moveToNext()) { check(); for (i in 0..2) if (!rows.isNull(i)) require(java.util.UUID.fromString(rows.getString(i)).toString() == rows.getString(i))
                for (i in 3..5) if (!rows.isNull(i)) require(rows.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER)
            }
        }
        backupTables.forEach { (table, _) ->
            sql.query("PRAGMA table_info(`$table`)").use { info -> while (info.moveToNext()) {
                if (info.getString(2) == "INTEGER") {
                    val column = info.getString(1)
                    sql.query("SELECT COUNT(*) FROM `$table` WHERE `$column` IS NOT NULL AND typeof(`$column`) != 'integer'").use { c -> c.moveToFirst(); require(c.getLong(0) == 0L) }
                    if (column in setOf("isSystem", "isActive")) sql.query("SELECT COUNT(*) FROM `$table` WHERE `$column` NOT IN (0,1)").use { c -> c.moveToFirst(); require(c.getLong(0) == 0L) }
                }
            } }
        }
    }
    private suspend fun validateSnapshot(text: String, p: Pharmacy): SnapshotCandidate.Supported =
        PayloadReader().snapshot(AuthenticatedJson(text.toByteArray(Charsets.UTF_8), EnvelopeDomain.SNAPSHOT), p.pharmacyId, p.keyVersion, Int.MAX_VALUE) as? SnapshotCandidate.Supported ?: error("Unsupported backup snapshot")

    private suspend fun journal(h: BackupHeader, pharmacyId: String): RestoreJournal {
        suspend fun record(wire: ConnectionWire?): String? {
            if (wire == null) return null
            val recordStore = PendingConnectionStore(secure, "restore-candidate")
            val key = Base64.getDecoder().decode(wire.snapshotKey)
            ConnectionCandidate(wire.pharmacyId, wire.pharmacyName, wire.createdAt, wire.createdBy, wire.botToken, wire.groupId, key, wire.keyVersion).use {
                recordStore.retain(it, newId())
            }
            val bytes = requireNotNull(secure.read("restore-candidate"))
            return try { Base64.getEncoder().encodeToString(bytes) } finally { bytes.fill(0); recordStore.info()?.let { recordStore.discard(it.id) } }
        }
        return RestoreJournal(newId(), record(h.active), record(h.setup), pharmacyId,
            h.requiredSchemaVersion, h.requiredMinVersionCode)
    }
    private fun copyCells(source: SupportSQLiteDatabase, target: SupportSQLiteDatabase, check: () -> Unit) {
        target.execSQL("CREATE TABLE backup_cells(t TEXT NOT NULL,r INTEGER NOT NULL,c INTEGER NOT NULL,n INTEGER NOT NULL,v BLOB,PRIMARY KEY(t,r,c,n))")
        source.query("SELECT t,r,c,n,v FROM backup_cells ORDER BY t,r,c,n").use { rows -> while (rows.moveToNext()) {
            check(); target.execSQL("INSERT INTO backup_cells VALUES(?,?,?,?,?)", arrayOf<Any?>(rows.getString(0), rows.getLong(1), rows.getInt(2), rows.getInt(3), if (rows.isNull(4)) null else rows.getBlob(4)))
        } }
    }
    private fun clearStageDatabase() {
        check(!context.getDatabasePath(stageFactory.databaseName).exists() || context.deleteDatabase(stageFactory.databaseName))
        val wrappers = File(context.noBackupFilesDir, "secure-backup_stage")
        check(!wrappers.exists() || wrappers.deleteRecursively())
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("${context.packageName}.aqaqeer.backup_stage.v1") }
    }

    suspend fun cleanupAbandoned() = withContext(Dispatchers.IO) { admin.sharingGate.withLock {
        root.listFiles().orEmpty().forEach { if (it.isFile && !it.delete()) throw java.io.IOException("Backup cleanup failed") }
        clearStageDatabase()
        val candidate = PendingConnectionStore(secure, "restore-candidate")
        candidate.info()?.let { candidate.discard(it.id) }
    } }

    suspend fun dismissReminder() = admin.transaction { dao -> authorized(dao); dao.control(requireNotNull(dao.control()).copy(backupDismissedAt = clock.millis())) }
}
