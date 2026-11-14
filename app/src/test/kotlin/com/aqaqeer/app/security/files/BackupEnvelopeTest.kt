package com.aqaqeer.app.security.files

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import kotlinx.serialization.json.*

class BackupEnvelopeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val password get() = " exact 😀 passphrase ".toCharArray()
    private fun spool(bytes: ByteArray) = ProtectedSpool(temporary.root).also { it.output().use { out -> out.write(bytes) } }
    @Test fun protectedChunksRoundTripAndNeverStagePlaintext() {
        val bytes = "confidential history 😀".repeat(10000).toByteArray()
        spool(bytes).use { plain ->
            assertArrayEquals(bytes, plain.input().use { it.readBytes() })
            val disk = temporary.root.listFiles()!!.single().readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(disk.contains("confidential history"))
        }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
    @Test fun truncatedReorderedOrModifiedProtectedChunksCannotBeRead() {
        spool(ByteArray(150000) { (it % 251).toByte() }).use { plain ->
            val file = temporary.root.listFiles()!!.single()
            val bytes = file.readBytes(); bytes[20] = (bytes[20].toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertThrows(Exception::class.java) { plain.input().use { it.readBytes() } }
        }
    }
    @Test fun freshSaltNonceExactUnicodePasswordAndWrongPasswordAuthentication() = runBlocking {
        val codec = V1BackupEnvelope { Long.MAX_VALUE }; val a = temporary.newFile(); val b = temporary.newFile()
        val bytes = "{\"history\":\"سجل 😀\"}".toByteArray()
        spool(bytes).use { plain -> codec.write(plain, a, password); codec.write(plain, b, password) }
        fun obj(f: File) = Json.parseToJsonElement(f.readText()).jsonObject
        assertNotEquals(obj(a).getValue("salt"), obj(b).getValue("salt"))
        assertNotEquals(obj(a).getValue("nonce"), obj(b).getValue("nonce"))
        codec.read(a.inputStream(), temporary.root, password).use { assertArrayEquals(bytes, it.input().use { input -> input.readBytes() }) }
        val failure = assertThrows(EnvelopeException::class.java) { runBlocking { codec.read(a.inputStream(), temporary.root, String(password).trim().toCharArray()) } }
        assertEquals(EnvelopeError.AUTHENTICATION, failure.error)
    }
    @Test fun passwordLengthCountsCodePointsAndRejectsUnpairedSurrogates() {
        V1BackupEnvelope.validatePassword("😀".repeat(8).toCharArray())
        listOf("😀".repeat(7), "1234567", "12345678\uD800", "\uDC0012345678").forEach { text ->
            assertEquals(EnvelopeError.INVALID_CREDENTIAL, assertThrows(EnvelopeException::class.java) {
                V1BackupEnvelope.validatePassword(text.toCharArray())
            }.error)
        }
    }
    @Test fun malformedUnsupportedAndResourceFailuresCleanStagingAndCloseInput() = runBlocking {
        val file = temporary.newFile(); val codec = V1BackupEnvelope { Long.MAX_VALUE }
        spool("payload".toByteArray()).use { codec.write(it, file, password) }
        val valid = file.readText()
        val cases = listOf(valid.replace("\"v\":1", "\"v\":2"), valid.replace("200000", "200001"),
            valid.replace("\"v\":1", "\"v\":1,\"v\":1"), valid.dropLast(1))
        for (text in cases) {
            var closed = false
            val source = object : ByteArrayInputStream(text.toByteArray()) { override fun close() { closed = true; super.close() } }
            assertThrows(Exception::class.java) { runBlocking { codec.read(source, temporary.root, password) } }
            assertTrue(closed); assertEquals(listOf(file.name), temporary.root.listFiles()!!.map { it.name })
        }
        assertEquals(EnvelopeError.RESOURCE_LIMIT, assertThrows(EnvelopeException::class.java) {
            runBlocking { V1BackupEnvelope { 0 }.read(file.inputStream(), temporary.root, password) }
        }.error)
    }
    @Test fun completeEnvelopeExact64MiBAcceptsAndOneExtraByteRejectsUnknownLengthStream() = runBlocking {
        val file = temporary.newFile(); val codec = V1BackupEnvelope { Long.MAX_VALUE }
        spool("{}".toByteArray()).use { codec.write(it, file, password) }
        fun padded(extra: Int): InputStream {
            val prefix = file.readBytes(); val limit = V1BackupEnvelope.LIMIT + extra
            return object : InputStream() {
                var offset = 0L
                override fun available() = 0
                override fun read(): Int = if (offset >= limit) -1 else { val n = if (offset < prefix.size) prefix[offset.toInt()].toInt() and 255 else 32; offset++; n }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (offset >= limit) return -1
                    val n = minOf(len.toLong(), limit - offset).toInt()
                    for (i in 0 until n) b[off + i] = if (offset + i < prefix.size) prefix[(offset + i).toInt()] else 32
                    offset += n; return n
                }
            }
        }
        codec.read(padded(0), temporary.root, password).use { assertEquals("{}", it.input().reader().use { r -> r.readText() }) }
        assertEquals(EnvelopeError.TOO_LARGE, assertThrows(EnvelopeException::class.java) { runBlocking { codec.read(padded(1), temporary.root, password) } }.error)
    }
    @Test fun zeroBulkReadsMakeProgressAndCancellationCleansResources() = runTest {
        val stream = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun read(b: ByteArray, off: Int, len: Int) = 0
        }
        val buffer = ByteArray(8)
        assertEquals(1, readBackupChunk(stream, buffer)); assertEquals(1.toByte(), buffer[0]); assertEquals(-1, readBackupChunk(stream, buffer))
        val file = temporary.newFile()
        spool("payload".toByteArray()).use { plain ->
            val job = launch { cancel(); V1BackupEnvelope().write(plain, file, password) }
            job.join(); assertTrue(job.isCancelled)
        }
    }
    @Test fun historyPayloadLargerThanSnapshotLimitRoundTrips() = runBlocking {
        val bytes = ByteArray(11 * 1024 * 1024) { (it % 251).toByte() }
        val file = temporary.newFile(); val codec = V1BackupEnvelope { Long.MAX_VALUE }
        spool(bytes).use { codec.write(it, file, password) }
        assertTrue(file.length() > 10 * 1024 * 1024)
        codec.read(file.inputStream(), temporary.root, password).use { result -> assertArrayEquals(bytes, result.input().use { it.readBytes() }) }
    }
}
