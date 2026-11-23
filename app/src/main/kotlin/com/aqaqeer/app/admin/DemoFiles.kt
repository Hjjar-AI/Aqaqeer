@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.aqaqeer.app.admin

import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.data.*
import com.aqaqeer.app.reports.DocumentOutput
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.time.Instant
import java.util.Base64

@Serializable private data class DemoHistoryRecord(
    val id: String, val operationId: String?, val entityType: String, val entityId: String,
    val entityName: String, val field: String, val oldValue: String?, val newValue: String?,
    val editedAt: String?, val observedAt: String?, val publishedRevision: Long?,
) {
    fun entry() = HistoryEntry(id, operationId, entityType, entityId, entityName, field, oldValue, newValue,
        editedAt?.let(Instant::parse), observedAt?.let(Instant::parse), publishedRevision)
}
@Serializable private data class DemoArchive(
    val kind: String, val catalog: CatalogSnapshot, val confirmed: CatalogSnapshot,
    val publishedAt: String, val revision: Long,
    val history: List<DemoHistoryRecord>,
)

/** Real file pickers/crypto over sample data; never reads or activates production credentials. */
internal class DemoFiles(private val context: Context) {
    private val output = DocumentOutput(context)
    private val json = Json { encodeDefaults = true }
    private fun checkDemo() { check(BuildConfig.IS_DEMO) }
    suspend fun exportConnection(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        checkDemo()
        val session = DemoSession.authorized()
        adminCheck(session.connected && !session.restorePaused)
        val pharmacy = requireNotNull(session.catalog.pharmacy)
        // Schema 0 is deliberately rejected by the standard connection reader.
        val wire = ConnectionWire(0, pharmacy.pharmacyId, pharmacy.name, FAKE_TOKEN, -901,
            Base64.getEncoder().encodeToString(ByteArray(32)), session.keyVersion, Instant.now())
        val bytes = EnvelopeWriter.connection(wire, password)
        try { output.write(uri) { it.write(bytes) } } finally { bytes.fill(0); password.fill('\u0000') }
    }
    suspend fun importConnection(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        checkDemo(); require(uri.scheme == "content")
        val session = DemoSession.initialize()
        val wire = EnvelopeReader(context.noBackupFilesDir).password(
            requireNotNull(context.contentResolver.openInputStream(uri)), EnvelopeDomain.CONNECTION, password).use { authenticated ->
            authenticated.withUtf8Reader { json.decodeFromString<ConnectionWire>(it.readText()) }
        }
        adminCheck(wire.botToken == FAKE_TOKEN && wire.groupId == -901L && wire.schemaVersion == 0 &&
            wire.pharmacyId == session.catalog.pharmacy?.pharmacyId && wire.keyVersion == session.keyVersion) { "اختر ملف اتصال تجريبياً من هذه النسخة فقط" }
        DemoSession.replaceConnection(session.workspaceId, session.keyVersion)
    }
    suspend fun exportBackup(uri: Uri, password: CharArray, confirmation: CharArray) = withContext(Dispatchers.IO) {
        checkDemo(); adminRequire(password.contentEquals(confirmation)) { "عبارتا المرور غير متطابقتين" }
        val session = DemoSession.authorized()
        val archive = DemoArchive(kind = "aqaqeer-demo-only-v1", catalog = session.catalog, confirmed = session.initial,
            publishedAt = session.publishedAt.toString(), revision = session.revision,
            history = session.history.map { entry -> DemoHistoryRecord(entry.id, entry.operationId, entry.entityType,
                entry.entityId, entry.entityName, entry.field, entry.oldValue, entry.newValue,
                entry.editedAt?.toString(), entry.observedAt?.toString(), entry.publishedRevision) })
        val root = File(context.noBackupFilesDir, "demo-file-staging").apply { check(isDirectory || mkdir()) }
        val encrypted = File.createTempFile("demo-", ".envelope", root)
        try {
            ProtectedSpool(root).use { spool ->
                spool.output().use { json.encodeToStream(DemoArchive.serializer(), archive, it) }
                V1BackupEnvelope().write(spool, encrypted, password)
            }
            // Verify that the same reader can authenticate and decode the complete output.
            readArchive(encrypted.inputStream(), password)
            adminCheck(DemoSession.authorized().workspaceId == session.workspaceId)
            output.write(uri) { destination -> encrypted.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    destination.write(buffer, 0, count)
                }
            } }
        } finally { password.fill('\u0000'); confirmation.fill('\u0000'); check(!encrypted.exists() || encrypted.delete()) }
    }
    private suspend fun readArchive(input: java.io.InputStream, password: CharArray): DemoArchive =
        EnvelopeReader(context.noBackupFilesDir).password(input, EnvelopeDomain.BACKUP, password).use { authenticated ->
            authenticated.withUtf8Reader { json.decodeFromString<DemoArchive>(it.readText()) }
        }.also {
            adminRequire(it.kind == "aqaqeer-demo-only-v1")
            CatalogRules.validate(it.catalog); CatalogRules.validate(it.confirmed)
            Instant.parse(it.publishedAt); adminRequire(it.revision > 0)
        }
    suspend fun restoreBackup(uri: Uri, password: CharArray, typedName: String, confirmed: Boolean) = withContext(Dispatchers.IO) {
        checkDemo(); require(uri.scheme == "content")
        val current = DemoSession.authorized()
        adminRequire(confirmed && typedName == current.catalog.pharmacy?.name)
        val archive = try { readArchive(requireNotNull(context.contentResolver.openInputStream(uri)), password) }
            finally { password.fill('\u0000') }
        val history = archive.history.map { it.entry() }
        currentCoroutineContext().ensureActive()
        DemoSession.restore(archive.catalog, archive.confirmed, history, current.workspaceId, Instant.parse(archive.publishedAt), archive.revision)
    }
    companion object { private const val FAKE_TOKEN = "DEMO_ONLY_NO_TELEGRAM_ACCESS" }
}
