package com.aqaqeer.app.fixtures

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.os.Process
import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.backup.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.reports.*
import com.aqaqeer.app.security.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import com.aqaqeer.app.ui.CatalogViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.io.FilterOutputStream
import java.io.OutputStream
import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class RestoreCrashGate(private val marker: File, private val checkpoint: String, private val event: String) {
    fun matches(name: String) = checkpoint == name
    val exporting get() = checkpoint.startsWith("export_")
    val sqlTable: String? get() = when (checkpoint) {
        "inside_sql_insert" -> "pharmacy"
        "inside_sql_history" -> "history"
        else -> null
    }
    fun hit(name: String, proof: Map<String, Boolean> = emptyMap()) {
        if (name != checkpoint) return
        val value = buildJsonObject {
            put("checkpoint", name); put("event", event); put("pid", Process.myPid()); put("uid", Process.myUid())
            proof.forEach { (key, value) -> put(key, value) }
        }
        marker.parentFile!!.mkdirs()
        val temporary = File(marker.parentFile, marker.name + ".tmp")
        temporary.outputStream().use { it.write(value.toString().toByteArray()); it.fd.sync() }
        check(temporary.renameTo(marker))
        android.system.Os.chmod(marker.path, 384)
        val request = File(marker.parentFile, marker.name.replace("marker-", "kill-"))
        val deadline = android.os.SystemClock.elapsedRealtime() + 300000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (request.isFile) {
                val command = Json.parseToJsonElement(request.readText()).jsonObject
                check(command.getValue("event").jsonPrimitive.content == event &&
                    command.getValue("pid").jsonPrimitive.int == Process.myPid() &&
                    command.getValue("uid").jsonPrimitive.int == Process.myUid())
                // Real SIGKILL: no coroutine cancellation, Java finally or runner.finish.
                Process.killProcess(Process.myPid())
                error("Self SIGKILL unexpectedly returned")
            }
            Thread.sleep(50)
        }
        error("Host did not request process death")
    }
    fun sql(db: SQLiteDatabase) {
        val native = Thread.currentThread().stackTrace.any { it.isNativeMethod && it.className.contains("SQLiteConnection") }
        check(db.inTransaction() && native) { "SQL gate must execute inside the real native write transaction" }
        hit(checkpoint, mapOf("transactionOpen" to true, "nativeSqlCallback" to native))
    }
}

/** Real protected storage, reopened by namespace after the previous VM has died. */
internal class RestoreCrashProfile(val base: Context, val root: File, val namespace: String,
    private val gate: RestoreCrashGate? = null) {
    val context: Context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir() = root
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, name)
        override fun deleteDatabase(name: String): Boolean {
            if (name == "aqaqeer-backup_stage.db") gate?.hit("before_stage_cleanup")
            return android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        }
    }
    val real: KeystoreSecureStore = KeystoreSecureStore(context, namespace)
    val secure = object : SecureStore {
        override fun read(name: String) = real.read(name)
        override fun write(name: String, value: ByteArray) {
            if (name == "maintenance-old" && value.contentEquals(byteArrayOf(0x41,0x51,0x50,0x31,0)) &&
                gate?.matches("maintenance_during_cleanup") == true) {
                val bytes=real.read("maintenance-journal")
                try {
                    val phase=bytes?.let { Json.parseToJsonElement(it.toString(Charsets.UTF_8)).jsonObject["phase"]?.jsonPrimitive?.content }
                    if(phase=="COMPLETE") {
                        assertMaintenanceStageEmpty()
                        assertNotNull(PendingConnectionStore(real,"maintenance-old").info())
                        gate.hit("maintenance_during_cleanup",mapOf("stagingRowsCleared" to true,"oldConnectionNotDiscarded" to true))
                    }
                } finally { bytes?.fill(0) }
            }
            if (name == "restore-journal") gate?.hit("before_journal")
            if (name == "active-connection") gate?.hit("before_activation")
            if (name == "active-connection") gate?.hit("maintenance_before_active")
            real.write(name, value)
            when (name) {
                "restore-journal" -> gate?.hit("after_journal")
                "active-connection" -> gate?.hit("after_active")
                "setup-connection" -> gate?.hit("after_setup")
                "pending-connection" -> gate?.hit("after_pending")
                else -> if (name.startsWith("snapshot-requirements-")) gate?.hit("after_requirements")
            }
            if (name == "active-connection") gate?.hit("maintenance_after_active")
            if (name == "maintenance-journal" && value.isNotEmpty()) {
                val phase = MaintenanceStore(real).read()?.phase?.name
                when (phase) {
                    "PREPARING" -> gate?.hit("maintenance_preparing")
                    "SOURCE_READY" -> gate?.hit("maintenance_source_ready")
                    "BACKED_UP" -> gate?.hit("maintenance_backed_up")
                    "WRITE_STARTED" -> gate?.hit("maintenance_write_started")
                }
            }
        }
    }
    val factory = DatabaseFactory(context, namespace)
    val requirements = SnapshotRequirementsStore(secure)
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
    val admin = AdminRepository(factory, requirements, secure, MaintenanceStage(context), clock)
    val repository = CatalogRepository(factory, secure, requirements, MaintenanceStage(context))
    val backups = BackupRepository(context, factory, secure, admin, requirements, clock,
        openDatabase = {
            factory.open().also { database ->
                if (gate?.sqlTable != null) {
                    val raw = database.openHelper.writableDatabase as SQLiteDatabase
                    raw.addCustomFunction("aq_acceptance_sql_gate", 0) { gate.sql(raw) }
                    // TEMP trigger/function affect only this real encrypted test connection.
                    raw.execSQL("CREATE TEMP TRIGGER aq_acceptance_gate BEFORE INSERT ON `${gate.sqlTable}` BEGIN SELECT aq_acceptance_sql_gate(); END")
                }
            }
        },
        openDestination = { uri ->
            val output = context.contentResolver.openOutputStream(uri, "wt")
            if (output == null || gate?.exporting != true) output else object : FilterOutputStream(output) {
                private val first = AtomicBoolean(true)
                override fun write(bytes: ByteArray, offset: Int, count: Int) {
                    out.write(bytes, offset, count)
                    if (first.getAndSet(false)) gate.hit("export_during_write")
                }
                override fun close() {
                    out.close()
                    gate.hit("export_after_close")
                }
            }
        })

    /** Production clear retains an empty encrypted staging database and its wrapper. */
    fun assertMaintenanceStageEmpty() {
        val database=DatabaseFactory(context,"maintenance_stage").open()
        try {
            val raw=database.openHelper.readableDatabase
            for(table in backupTables.keys.filter { it!="snapshot_state" }) {
                raw.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    assertTrue(cursor.moveToFirst());assertEquals("Staged content remains in $table",0,cursor.getInt(0))
                }
            }
            raw.query("SELECT * FROM snapshot_state").use { cursor ->
                var rows=0
                while(cursor.moveToNext()) {
                    rows++;assertEquals(1,cursor.getInt(cursor.getColumnIndexOrThrow("slot")))
                    for(column in 0 until cursor.columnCount) if(cursor.getColumnName(column)!="slot")
                        assertTrue("Staged snapshot metadata remains",cursor.isNull(column))
                }
                assertTrue(rows<=1)
            }
        } finally { database.close() }
    }

    suspend fun ready() = withTimeout(90000) {
        repository.observe().first { it !is CatalogState.Loading }.also { check(it is CatalogState.Ready) } as CatalogState.Ready
    }
    suspend fun create(name: String) {
        ready()
        admin.create(name, "Asia/Damascus", "محرر الاختبار", "الرئيسي", "123456".toCharArray(), "123456".toCharArray())
    }
    suspend fun medication(name: String): Medication {
        val med = M3Fixtures.catalog().medications.single().copy(id = newId(), nameEn = name)
        admin.saveMedication(med)
        return ready().catalog.medications.single { it.id == med.id }
    }
    fun sharing(remote: SuiteRemote) = PharmacySharing(admin, secure, requirements,
        SnapshotReadClient(remote, EnvelopeReader(root)), remote, remote, context)

    /** No value is printed: this complete comparison snapshot is encrypted by the coordinator. */
    suspend fun tables(): JsonObject = withContext(Dispatchers.IO) {
        val database = factory.open()
        try {
            val tables = backupTables.keys + listOf("device_identity", "installation_state", "local_control")
            buildJsonObject {
                for (table in tables) {
                    val rows = mutableListOf<JsonObject>()
                    database.openHelper.readableDatabase.query("SELECT * FROM " + table).use { cursor ->
                        while (cursor.moveToNext()) rows += buildJsonObject {
                            for (i in 0 until cursor.columnCount) put(cursor.getColumnName(i), when (cursor.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> JsonNull
                                Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(i))
                                Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(i))
                                Cursor.FIELD_TYPE_BLOB -> JsonPrimitive(Base64.getEncoder().encodeToString(cursor.getBlob(i)))
                                else -> JsonPrimitive(cursor.getString(i))
                            })
                        }
                    }
                    put(table, JsonArray(rows.sortedBy { it.toString() }))
                }
            }
        } finally { database.close() }
    }
    fun rawCredentials(ids: List<String>): JsonObject = buildJsonObject {
        for (name in listOf("active-connection", "setup-connection", "pending-connection") + ids.map { "snapshot-requirements-" + it }) {
            val bytes = real.read(name)
            try { put(name, bytes?.let { JsonPrimitive(Base64.getEncoder().encodeToString(it)) } ?: JsonNull) }
            finally { bytes?.fill(0) }
        }
    }
    suspend fun connection(entry: String): JsonElement {
        val store = PendingConnectionStore(real, entry)
        val info = store.info() ?: return JsonNull
        return store.withCandidate(info.id) { candidate ->
            val secrets = candidate.copySecrets()
            try { buildJsonObject {
                put("pharmacy", candidate.pharmacyId); put("name", candidate.pharmacyName)
                put("keyVersion", candidate.keyVersion); put("createdAt", candidate.createdAt.toString())
                put("editor", Json.encodeToJsonElement(candidate.createdBy))
                put("group", secrets.groupId); put("token", secrets.botToken)
                put("key", Base64.getEncoder().encodeToString(secrets.snapshotKey))
            } } finally { secrets.snapshotKey.fill(0) }
        }
    }

    suspend fun throughViewModel(): CatalogState.Ready {
        val transportCalls = AtomicInteger()
        fun forbidden(): Nothing { transportCalls.incrementAndGet(); error("Unexpected recovery network operation") }
        val remote = object : SnapshotReadTransport {
            override suspend fun bot(token: String): BotIdentity = forbidden()
            override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument = forbidden()
            override suspend fun document(token: String, document: SnapshotDocument): java.io.InputStream = forbidden()
        }
        val reads = SnapshotReadClient(remote, EnvelopeReader(root))
        val local = SuiteRemote()
        val output = DocumentOutput(context)
        val models = ViewModelStore()
        lateinit var vm: CatalogViewModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val owner = object : ViewModelStoreOwner { override val viewModelStore = models }
            val builder = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogViewModel(repository, DisplayPreferences(context), SavedStateHandle(),
                        ConnectionImportRepository(context, repository, secure, reads, requirements),
                        SnapshotRefreshRepository(factory, secure, reads, requirements), HistoryRepository(factory),
                        CsvImportRepository(admin, output, context), ReportRepository(admin, output, PdfReports(context)),
                        admin, PharmacySharing(admin, secure, requirements, reads,
                            object : TelegramSetupTransport {
                                override suspend fun validate(token: String, groupId: Long): SetupGroup = forbidden()
                                override suspend fun discover(token: String, challenge: String): List<SetupGroup> = forbidden()
                            },
                            object : SnapshotWriteTransport {
                                override suspend fun inspect(token: String, group: Long): SetupGroup = forbidden()
                                override suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String): UploadedDocument = forbidden()
                                override suspend fun replace(token: String, group: Long, message: Long, file: String, caption: String): Unit = forbidden()
                                override suspend fun pin(token: String, group: Long, message: Long): Unit = forbidden()
                            }, context), backups,
                        MaintenanceCoordinator(MaintenanceStore(secure), MaintenanceStage(context), admin, reads,
                            local, local, local, requirements, backups, context),
                        PinChanges(admin, MaintenanceStore(secure)), context) as T
            }
            vm = ViewModelProvider(owner, builder)[CatalogViewModel::class.java]
        }
        try {
            val state = withTimeout(120000) { vm.state.first { it.catalog !is CatalogState.Loading } }.catalog
            assertTrue("Cold startup must expose usable data", state is CatalogState.Ready)
            if ((state as CatalogState.Ready).restorePaused || state.maintenancePaused) {
                instrumentation.runOnMainSync { vm.onForeground(true); vm.refreshCatalog() }
                instrumentation.waitForIdleSync()
            }
            // Await cleanup independently; the production ViewModel launches it asynchronously.
            withTimeout(90000) {
                while (File(root, "aqaqeer-backup-stage").listFiles().orEmpty().isNotEmpty() ||
                    context.getDatabasePath("aqaqeer-backup_stage.db").exists() ||
                    File(root, "secure-backup_stage").exists() ||
                    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                        .containsAlias(base.packageName + ".aqaqeer.backup_stage.v1")) delay(100)
            }
            assertEquals("Recovery must not use a network transport", 0, transportCalls.get())
            assertEquals("Recovery must not upload", 0, local.uploads)
            assertEquals("Recovery must not publish", 0, local.writes)
            return state
        } finally { instrumentation.runOnMainSync { models.clear() } }
    }
    fun deleteOwned() {
        check(namespace.startsWith("crash_"))
        root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            for (name in listOf(namespace, "backup_stage", "maintenance_stage"))
                deleteEntry(base.packageName + ".aqaqeer." + name + ".v1")
        }
    }
}
