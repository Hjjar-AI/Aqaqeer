package com.aqaqeer.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import com.aqaqeer.app.security.DatabaseKeyProvider
import com.aqaqeer.app.security.KeystoreSecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import net.zetetic.database.DatabaseErrorHandler
import java.time.Instant
import java.time.LocalDate

class DbConverters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun editorToString(value: EditorRef): String = Json.encodeToString(value)
    @TypeConverter fun stringToEditor(value: String): EditorRef = Json.decodeFromString(value)
    @TypeConverter fun verifierToString(value: PinVerifier): String = Json.encodeToString(value)
    @TypeConverter fun stringToVerifier(value: String): PinVerifier = Json.decodeFromString(value)
}

@Dao
interface CatalogDao {
    @Query("UPDATE history SET operationId = :replacement WHERE operationId IN (:ids) AND publishedRevision IS NULL")
    suspend fun retargetHistoryBatch(ids: List<String>, replacement: String)
    @Transaction suspend fun retargetHistory(ids: List<String>, replacement: String) {
        ids.chunked(500).forEach { retargetHistoryBatch(it, replacement) }
    }

    @Query("UPDATE history SET publishedRevision = :revision WHERE operationId IN (:ids)")
    suspend fun acknowledgeHistoryBatch(ids: List<String>, revision: Long)
    @Transaction suspend fun acknowledgeHistory(ids: List<String>, revision: Long) {
        ids.chunked(500).forEach { acknowledgeHistoryBatch(it, revision) }
    }

    @Query("SELECT * FROM local_control WHERE slot = 1") suspend fun control(): LocalControl?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun control(value: LocalControl)
    @Query("SELECT * FROM pending_operations ORDER BY rowid") suspend fun pending(): List<PendingOperation>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPending(value: List<PendingOperation>)
    @Query("DELETE FROM pending_operations WHERE id IN (:ids)") suspend fun deletePendingBatch(ids: List<String>)
    @Transaction suspend fun deletePending(ids: List<String>) {
        ids.chunked(500).forEach { deletePendingBatch(it) }
    }
    @Query("SELECT publishId, NULL AS basePayloadJson, '' AS candidatePayloadJson, '' AS includedOperationIdsJson, transportPhase, uploadedFileId, uploadedMessageId FROM publish_attempts ORDER BY rowid")
    suspend fun attemptMetadata(): List<PublishAttempt>
    @androidx.room.RawQuery fun backupCursor(query: androidx.sqlite.db.SupportSQLiteQuery): android.database.Cursor
    @androidx.room.Transaction suspend fun attempts(): List<PublishAttempt> = attemptMetadata().map { value ->
        value.copy(basePayloadJson = textColumn("publish_attempts", "publishId", value.publishId, "basePayloadJson"),
            candidatePayloadJson = requireNotNull(textColumn("publish_attempts", "publishId", value.publishId, "candidatePayloadJson")),
            includedOperationIdsJson = requireNotNull(textColumn("publish_attempts", "publishId", value.publishId, "includedOperationIdsJson")))
    }
    fun textColumn(table: String, key: String, id: String, column: String): String? {
        require((table == "snapshot_state" && key == "slot" && column == "confirmedPayloadJson") ||
            (table == "publish_attempts" && key == "publishId" && column in setOf("basePayloadJson", "candidatePayloadJson", "includedOperationIdsJson")))
        val bytes = backupCursor(androidx.sqlite.db.SimpleSQLiteQuery("SELECT length(CAST(`$column` AS BLOB)) FROM `$table` WHERE `$key`=?", arrayOf<Any?>(id))).use { if (!it.moveToFirst() || it.isNull(0)) return null else it.getLong(0) }
        val runtime = Runtime.getRuntime()
        require(bytes <= 50L * 1024 * 1024 && bytes * 6 < runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) { "Insufficient protected workspace" }
        return buildString {
            var offset = 1L
            while (true) {
                val chunk = backupCursor(androidx.sqlite.db.SimpleSQLiteQuery("SELECT substr(`$column`,?,8192) FROM `$table` WHERE `$key`=?", arrayOf<Any?>(offset, id))).use { check(it.moveToFirst()); it.getString(0) }
                if (chunk.isEmpty()) break
                append(chunk); offset += chunk.codePointCount(0, chunk.length)
            }
        }
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun attempt(value: PublishAttempt)
    @Query("DELETE FROM publish_attempts WHERE publishId = :id") suspend fun deleteAttempt(id: String)
    @Query("DELETE FROM pharmacy") suspend fun clearPharmacy()
    @Query("DELETE FROM history") suspend fun clearHistory()

    @Query("SELECT * FROM pharmacy LIMIT 1") suspend fun pharmacy(): Pharmacy?
    @Query("SELECT * FROM categories ORDER BY orderIndex, id") suspend fun categories(): List<Category>
    @Query("SELECT * FROM forms ORDER BY orderIndex, id") suspend fun forms(): List<MedicationForm>
    @Query("SELECT * FROM sections ORDER BY orderIndex, id") suspend fun sections(): List<Section>
    @Query("SELECT * FROM medications ORDER BY nameEn, id") suspend fun medications(): List<Medication>
    @Query("SELECT * FROM availability") suspend fun availability(): List<Availability>
    @Query("SELECT * FROM tombstones") suspend fun tombstones(): List<Tombstone>
    @Query("SELECT * FROM installation_state WHERE slot = 1") suspend fun installation(): InstallationState?
    @Query("SELECT slot, publishedAt, NULL AS confirmedPayloadJson, revision, publishId, pinnedMessageId, documentFileId, documentFileUniqueId, lastSuccessfulSyncAt, requiredSchemaVersion, requiredMinVersionCode FROM snapshot_state WHERE slot = 1")
    suspend fun snapshotMetadata(): SnapshotState?
    @androidx.room.Transaction suspend fun snapshotState(): SnapshotState? = snapshotMetadata()?.let {
        it.copy(confirmedPayloadJson = textColumn("snapshot_state", "slot", "1", "confirmedPayloadJson"))
    }
    @Query("SELECT COUNT(*) FROM publish_attempts") suspend fun attemptCount(): Int
    @Query("SELECT COUNT(*) FROM history") suspend fun historyCount(): Int
    @Query("DELETE FROM availability") suspend fun clearAvailability()
    @Query("DELETE FROM medications") suspend fun clearMedications()
    @Query("DELETE FROM sections") suspend fun clearSections()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM forms") suspend fun clearForms()
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTombstones(value: List<Tombstone>)
    @Query("DELETE FROM tombstones") suspend fun clearTombstones()
    @Update suspend fun updatePharmacy(value: Pharmacy)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHistory(value: List<HistoryEntry>)
    @Query("SELECT * FROM history ORDER BY id") suspend fun history(): List<HistoryEntry>
    @Update suspend fun updateSnapshotState(value: SnapshotState)
    @Update suspend fun updateDeviceIdentity(value: DeviceIdentity)
    @Query("SELECT COUNT(*) FROM pending_operations") suspend fun pendingCount(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPharmacy(value: Pharmacy)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategories(value: List<Category>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertForms(value: List<MedicationForm>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSections(value: List<Section>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMedications(value: List<Medication>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAvailability(value: List<Availability>)
    @Update suspend fun updateCategories(value: List<Category>)
    @Delete suspend fun deleteCategories(value: List<Category>)
    @Update suspend fun updateForms(value: List<MedicationForm>)
    @Delete suspend fun deleteForms(value: List<MedicationForm>)
    @Update suspend fun updateSections(value: List<Section>)
    @Delete suspend fun deleteSections(value: List<Section>)
    @Update suspend fun updateMedications(value: List<Medication>)
    @Delete suspend fun deleteMedications(value: List<Medication>)
    @Update suspend fun updateAvailability(value: List<Availability>)
    @Delete suspend fun deleteAvailability(value: List<Availability>)
    @Update suspend fun updateTombstones(value: List<Tombstone>)
    @Delete suspend fun deleteTombstones(value: List<Tombstone>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertInstallation(value: InstallationState)
    @Update suspend fun updateInstallation(value: InstallationState)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSnapshotState(value: SnapshotState)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDeviceIdentity(value: DeviceIdentity)
    @Query("SELECT * FROM device_identity LIMIT 1") suspend fun deviceIdentity(): DeviceIdentity?
}

@Database(entities = [Pharmacy::class, Category::class, MedicationForm::class, Section::class,
    Medication::class, Availability::class, Tombstone::class, HistoryEntry::class,
    DeviceIdentity::class, SnapshotState::class, PendingOperation::class, PublishAttempt::class,
    InstallationState::class, LocalControl::class], version = 1, exportSchema = true)
@TypeConverters(DbConverters::class)
abstract class AqaqeerDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}

class DatabaseFactory(private val context: Context, private val namespace: String = "main") {
    val databaseName: String = "aqaqeer-$namespace.db"
    private val leaseGate = kotlinx.coroutines.sync.Mutex()
    private val users = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var shared: AqaqeerDatabase? = null

    /** A lease never owns the connection. Only reset closes it after all users drain. */
    class Lease internal constructor(val database: AqaqeerDatabase, private val release: () -> Unit) : java.io.Closeable {
        private val closed = java.util.concurrent.atomic.AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }
    suspend fun acquire(): Lease = leaseGate.withLock {
        val database = shared ?: open().also { shared = it }
        users.update { it + 1 }
        Lease(database) { users.update { it - 1 } }
    }
    suspend fun reset() = leaseGate.withLock {
        users.first { it == 0 }
        val previous = shared
        shared = null
        previous?.close()
    }
    /** Explicit one-shot ownership for isolated staging and existing construction seams. */
    fun open(): AqaqeerDatabase {
        System.loadLibrary("sqlcipher")
        val path = context.getDatabasePath(databaseName)
        val key = DatabaseKeyProvider(KeystoreSecureStore(context, namespace)).get(path.exists())
        // SQLCipher's helper needs a retained private copy for lazy opening/reopening.
        val factory = PreservingCipherFactory(key.copyOf())
        key.fill(0)
        return Room.databaseBuilder(context, AqaqeerDatabase::class.java, databaseName)
            .openHelperFactory(factory)
            .enableMultiInstanceInvalidation()
            // No destructive migration or plaintext fallback.
            .build()
    }
}

/** SQLCipher's stock bridge passes a null native error handler, which can delete
 * a corrupt database. Keep the same Room/SQLCipher API, but fail closed instead. */
private class PreservingCipherFactory(private val password: ByteArray) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val callback = configuration.callback
        return object : SQLiteOpenHelper(configuration.context, configuration.name, password, null,
            callback.version, 0, DatabaseErrorHandler { _, exception ->
                throw StorageRecoveryRequired(exception)
            }, null, false) {
            override fun onConfigure(db: SQLiteDatabase) = callback.onConfigure(db)
            override fun onCreate(db: SQLiteDatabase) = callback.onCreate(db)
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = callback.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SQLiteDatabase) = callback.onOpen(db)
            override fun onBeforeDelete(db: SQLiteDatabase) { throw StorageRecoveryRequired() }
        }
    }
}
