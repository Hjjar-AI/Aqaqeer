package com.aqaqeer.app.admin

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException

class DemoFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var filesApi: DemoFiles
    private val files = mutableMapOf<Uri, ByteArray>()
    private var failClose = false
    @Before fun setup() {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        DemoSession.reset(); DemoSession.setAdmin(true)
        context = mockk(); resolver = mockk()
        every { context.noBackupFilesDir } returns temporary.root
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(any()) } answers { files.getValue(firstArg()).inputStream() }
        every { resolver.openOutputStream(any(), "wt") } answers {
            val uri = firstArg<Uri>()
            object : ByteArrayOutputStream() {
                override fun close() { files[uri] = toByteArray(); super.close(); if (failClose) throw IOException("injected provider close") }
            }
        }
        filesApi = DemoFiles(context)
    }
    @After fun cleanup() { if (BuildConfig.IS_DEMO) { DemoSession.reset(); DemoSession.setAdmin(false) } }
    private fun uri() = mockk<Uri>().also { every { it.scheme } returns "content" }
    private suspend fun backup(target: Uri) = filesApi.exportBackup(target, "demo passphrase".toCharArray(), "demo passphrase".toCharArray())
    private fun editNote(note: String) = DemoSession.edit { c, _, _, _ ->
        c.copy(medications = c.medications.mapIndexed { i, med -> if (i == 0) med.copy(note = note) else med })
    }
    private suspend fun encryptedBackup(payload: String): ByteArray {
        val target = temporary.newFile()
        try {
            ProtectedSpool(temporary.root).use { plain ->
                plain.output().use { it.write(payload.toByteArray()) }
                V1BackupEnvelope().write(plain, target, "demo passphrase".toCharArray())
            }
            return target.readBytes()
        } finally { check(target.delete()) }
    }
    @Test fun connectionRoundTripRestoresOnlyMatchingDemoConnection(): Unit = runBlocking {
        val target = uri(); val before = DemoSession.authorized()
        filesApi.exportConnection(target, "123456".toCharArray())
        DemoSession.setConnection(false); DemoSession.setAdmin(false)
        filesApi.importConnection(target, "123456".toCharArray())
        val after = DemoSession.initialize()
        assertTrue(after.connected); assertFalse(after.admin)
        assertEquals(before.catalog, after.catalog); assertEquals(before.history, after.history)
        assertEquals(before.pending, after.pending)
    }
    @Test fun demoConnectionIsRejectedByProductionPayloadReader(): Unit = runBlocking {
        val target = uri(); filesApi.exportConnection(target, "123456".toCharArray())
        val payload = EnvelopeReader(temporary.root).password(files.getValue(target).inputStream(), EnvelopeDomain.CONNECTION, "123456".toCharArray())
        payload.use { assertThrows(PayloadException::class.java) { runBlocking { PayloadReader { Long.MAX_VALUE }.connection(it) } } }
    }
    @Test fun realConnectionAndStaleRotatedDemoCredentialsCannotReplaceActiveConnection(): Unit = runBlocking {
        val target = uri(); filesApi.exportConnection(target, "123456".toCharArray())
        DemoSession.rotateConnection(); var before = DemoSession.authorized()
        assertThrows(AdminProblem::class.java) { runBlocking { filesApi.importConnection(target, "123456".toCharArray()) } }
        assertEquals(before, DemoSession.authorized())
        files[target] = F.connectionEnvelope(); before = DemoSession.authorized()
        assertThrows(AdminProblem::class.java) { runBlocking { filesApi.importConnection(target, "123456".toCharArray()) } }
        assertEquals(before, DemoSession.authorized())
    }
    @Test fun wrongConnectionPasswordPreservesActiveWorkspace(): Unit = runBlocking {
        val target = uri(); filesApi.exportConnection(target, "123456".toCharArray()); val before = DemoSession.authorized()
        val failure = assertThrows(EnvelopeException::class.java) { runBlocking { filesApi.importConnection(target, "654321".toCharArray()) } }
        assertEquals(EnvelopeError.AUTHENTICATION, failure.error); assertEquals(before, DemoSession.authorized())
    }
    @Test fun encryptedBackupRestoresCatalogBaseHistoryAndPausedPendingWork(): Unit = runBlocking {
        editNote("retained draft"); val saved = DemoSession.authorized(); val target = uri(); backup(target)
        assertFalse(files.getValue(target).toString(Charsets.UTF_8).contains("retained draft"))
        editNote("later edit")
        filesApi.restoreBackup(target, "demo passphrase".toCharArray(), saved.catalog.pharmacy!!.name, true)
        val restored = DemoSession.initialize()
        assertEquals(saved.catalog, restored.catalog); assertEquals(saved.initial, restored.initial)
        assertEquals(saved.history, restored.history); assertEquals(saved.publishedAt, restored.publishedAt)
        assertEquals(saved.revision, restored.revision); assertFalse(restored.admin); assertTrue(restored.restorePaused)
        assertTrue(restored.pending.isNotEmpty()); assertNotEquals(saved.workspaceId, restored.workspaceId)
        assertEquals(saved.keyVersion, restored.keyVersion); assertEquals(saved.connected, restored.connected)
        assertFalse(temporary.root.walk().any { it.isFile })
    }
    @Test fun backupDoesNotExportOrRestoreSamplePinAuthorization(): Unit = runBlocking {
        val target = uri(); backup(target)
        DemoSession.changePin("1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray())
        val name = DemoSession.authorized().catalog.pharmacy!!.name
        filesApi.restoreBackup(target, "demo passphrase".toCharArray(), name, true)
        assertThrows(AdminProblem::class.java) { DemoSession.promote("1234".toCharArray()) }
        DemoSession.promote("5678".toCharArray()); assertTrue(DemoSession.authorized().restorePaused)
        assertThrows(AdminProblem::class.java) { DemoSession.publish() }
    }
    @Test fun wrongPassphraseOrReplacementConfirmationNeverChangesCurrentData(): Unit = runBlocking {
        val target = uri(); backup(target); val before = DemoSession.authorized(); val name = before.catalog.pharmacy!!.name
        assertThrows(EnvelopeException::class.java) { runBlocking { filesApi.restoreBackup(target, "wrong passphrase".toCharArray(), name, true) } }
        assertEquals(before, DemoSession.authorized())
        assertThrows(AdminProblem::class.java) { runBlocking { filesApi.restoreBackup(target, "demo passphrase".toCharArray(), "wrong name", true) } }
        assertThrows(AdminProblem::class.java) { runBlocking { filesApi.restoreBackup(target, "demo passphrase".toCharArray(), name, false) } }
        assertEquals(before, DemoSession.authorized())
    }
    @Test fun backupProviderCloseFailurePropagatesAndCleansOwnedStaging(): Unit = runBlocking {
        val before = DemoSession.authorized(); failClose = true
        assertThrows(IOException::class.java) { runBlocking { backup(uri()) } }
        assertEquals(before, DemoSession.authorized()); assertFalse(temporary.root.walk().any { it.isFile })
    }
    @Test fun invalidPassphraseAndReaderFailBeforeExternalOutput(): Unit = runBlocking {
        assertThrows(AdminProblem::class.java) { runBlocking { filesApi.exportBackup(uri(), "demo passphrase".toCharArray(), "different passphrase".toCharArray()) } }
        DemoSession.setAdmin(false)
        assertThrows(AdminProblem::class.java) { runBlocking { backup(uri()) } }
        assertThrows(AdminProblem::class.java) { runBlocking { filesApi.exportConnection(uri(), "123456".toCharArray()) } }
        verify(exactly = 0) { resolver.openOutputStream(any(), any()) }
    }
    @Test fun authenticatedProductionStyleBackupCannotReplaceDemoWorkspace(): Unit = runBlocking {
        val target = uri(); files[target] = encryptedBackup("{\"header\":{\"schemaVersion\":1,\"lifecycle\":\"SHARED\"},\"tables\":{}}")
        val before = DemoSession.authorized()
        assertThrows(SerializationException::class.java) {
            runBlocking { filesApi.restoreBackup(target, "demo passphrase".toCharArray(), before.catalog.pharmacy!!.name, true) }
        }
        assertEquals(before, DemoSession.authorized())
    }
    @Test fun authenticatedInvalidCatalogRejectsBeforeReplacement(): Unit = runBlocking {
        val target = uri(); backup(target); val before = DemoSession.authorized()
        val archive = EnvelopeReader(temporary.root).password(files.getValue(target).inputStream(), EnvelopeDomain.BACKUP, "demo passphrase".toCharArray()).use { plain ->
            plain.withUtf8Reader { Json.parseToJsonElement(it.readText()).jsonObject }
        }
        val invalidCatalog = JsonObject(archive.getValue("catalog").jsonObject + ("forms" to JsonArray(emptyList())))
        files[target] = encryptedBackup(JsonObject(archive + ("catalog" to invalidCatalog)).toString())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { filesApi.restoreBackup(target, "demo passphrase".toCharArray(), before.catalog.pharmacy!!.name, true) }
        }
        assertEquals(before, DemoSession.authorized())
    }
}
