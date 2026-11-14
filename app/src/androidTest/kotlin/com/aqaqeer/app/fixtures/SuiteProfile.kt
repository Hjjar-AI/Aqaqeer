package com.aqaqeer.app.fixtures

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.backup.BackupRepository
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.KeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Owns every database, wrapper and temporary file it removes. */
internal class SuiteProfile : AutoCloseable {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    val namespace = "suite_${UUID.randomUUID().toString().replace("-", "")}"
    val root = File(base.noBackupFilesDir, namespace).apply { mkdirs() }
    val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir() = root
        override fun getDatabasePath(name: String) = File(root, name)
        override fun deleteDatabase(name: String) = android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    }
    val secure = KeystoreSecureStore(context, namespace)
    val factory = DatabaseFactory(context, namespace)
    val requirements = SnapshotRequirementsStore(secure)
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC)
    val repository = CatalogRepository(factory, secure, requirements, com.aqaqeer.app.maintenance.MaintenanceStage(context))
    val admin = AdminRepository(factory, requirements, secure, com.aqaqeer.app.maintenance.MaintenanceStage(context), clock)
    val backups = BackupRepository(context, factory, secure, admin, requirements, clock)
    suspend fun ready(): CatalogState.Ready = withTimeout(20000) { repository.observe().first { it !is CatalogState.Loading } as CatalogState.Ready }
    suspend fun create() {
        check(!BuildConfig.IS_DEMO)
        ready(); admin.create("صيدلية الاختبار", "Asia/Damascus", "محرر الاختبار", "الرئيسي", "123456".toCharArray(), "123456".toCharArray())
    }
    suspend fun medication(name: String = "Suite medicine"): Medication {
        val value = M3Fixtures.catalog().medications.single().copy(id = newId(), nameEn = name)
        admin.saveMedication(value)
        return ready().catalog.medications.single { it.id == value.id }
    }
    fun sharing(remote: SuiteRemote) = PharmacySharing(admin, secure, requirements,
        SnapshotReadClient(remote, EnvelopeReader(root)), remote, remote, context)
    suspend fun publish(remote: SuiteRemote): PharmacySharing {
        val sharing = sharing(remote)
        sharing.saveSetup(M3Fixtures.token, M3Fixtures.groupId, remote.title)
        sharing.publish(); check(sharing.status.value.verified)
        return sharing
    }
    override fun close() {
        root.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            listOf(namespace, "backup_stage", "maintenance_stage").forEach { deleteEntry("${base.packageName}.aqaqeer.$it.v1") }
        }
    }
}

internal class SuiteRemote : TelegramSetupTransport, SnapshotWriteTransport, SnapshotReadTransport {
    val title = "مجموعة الاختبار"
    var current: SnapshotDocument? = null
    val files = mutableMapOf<String, ByteArray>()
    val captions = mutableMapOf<Long, SnapshotPublication>()
    var uploads = 0
    var writes = 0
    var loseWriteResponse = false
    var beforeUpload: suspend () -> Unit = {}
    override suspend fun validate(token: String, groupId: Long) = inspect(token, groupId)
    override suspend fun discover(token: String, challenge: String) = listOf(inspect(token, M3Fixtures.groupId))
    override suspend fun inspect(token: String, group: Long) = SetupGroup(group, title,
        current?.takeIf { it.groupId == group }?.let { "${it.messageId}:${it.fileId}:${it.publication.publishId}" })
    override suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String): UploadedDocument {
        beforeUpload(); val id = 100L + ++uploads; val file = "suite-$id"
        files[file] = bytes.copyOf(); captions[id] = SnapshotCaption.decode(caption)
        return UploadedDocument(id, file)
    }
    override suspend fun replace(token: String, group: Long, message: Long, file: String, caption: String) {
        writes++; current = SnapshotDocument(group, message, file, "unique-$file", SnapshotCaption.decode(caption))
        if (loseWriteResponse) { loseWriteResponse = false; throw TelegramFailure() }
    }
    override suspend fun pin(token: String, group: Long, message: Long) {
        writes++; current = SnapshotDocument(group, message, "suite-$message", "unique-suite-$message", captions.getValue(message))
        if (loseWriteResponse) { loseWriteResponse = false; throw TelegramFailure() }
    }
    override suspend fun bot(token: String) = BotIdentity(123456)
    override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity) = current?.takeIf { it.groupId == groupId }
        ?: throw SnapshotReadException(SnapshotReadError.SNAPSHOT_UNAVAILABLE)
    override suspend fun document(token: String, document: SnapshotDocument) = files.getValue(document.fileId).inputStream()
}
