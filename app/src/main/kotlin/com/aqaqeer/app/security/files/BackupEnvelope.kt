package com.aqaqeer.app.security.files

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Ephemeral, independently authenticated chunks. Plaintext never reaches a temporary file. */
internal class ProtectedSpool(root: File) : AutoCloseable {
    private val file = File.createTempFile("backup-", ".protected", root)
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)
    fun output(): OutputStream = object : OutputStream() {
        val out = DataOutputStream(file.outputStream().buffered())
        val buffer = ByteArray(64 * 1024)
        var count = 0; var index = 0; var closed = false; var total = 0L
        fun chunk(last: Boolean) {
            val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD("aqbackup-stage:$index:$last".toByteArray())
            val encrypted = cipher.doFinal(buffer, 0, count)
            out.writeInt(if (last) -encrypted.size else encrypted.size); out.write(nonce); out.write(encrypted)
            encrypted.fill(0); buffer.fill(0); count = 0; index++
        }
        override fun write(b: Int) { if (++total > V1BackupEnvelope.LIMIT) throw EnvelopeException(EnvelopeError.TOO_LARGE); if (count == buffer.size) chunk(false); buffer[count++] = b.toByte() }
        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed); total += len; if (total > V1BackupEnvelope.LIMIT) throw EnvelopeException(EnvelopeError.TOO_LARGE); var p = off; var left = len
            while (left > 0) { if (count == buffer.size) chunk(false); val n = minOf(left, buffer.size - count); b.copyInto(buffer, count, p, p + n); count += n; p += n; left -= n }
        }
        override fun close() { if (!closed) { closed = true; try { chunk(true) } finally { buffer.fill(0); out.close() } } }
    }
    fun input(): InputStream = object : InputStream() {
        val source = DataInputStream(file.inputStream().buffered())
        var buffer = byteArrayOf(); var offset = 0; var index = 0; var last = false
        fun next(): Boolean {
            if (offset < buffer.size) return true
            buffer.fill(0)
            if (last) { require(source.read() == -1); return false }
            val size = source.readInt(); require(size != Int.MIN_VALUE && kotlin.math.abs(size) in 16..65552)
            last = size < 0
            val nonce = ByteArray(12).also(source::readFully)
            val encrypted = ByteArray(kotlin.math.abs(size)).also(source::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD("aqbackup-stage:$index:$last".toByteArray())
            buffer = try { cipher.doFinal(encrypted) } finally { encrypted.fill(0) }
            offset = 0; index++
            return if (buffer.isEmpty()) { require(last); require(source.read() == -1); false } else true
        }
        override fun read(): Int = if (!next()) -1 else buffer[offset++].toInt() and 255
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!next()) return -1
            val n = minOf(len, buffer.size - offset); buffer.copyInto(b, off, offset, offset + n); offset += n; return n
        }
        override fun close() { buffer.fill(0); source.close() }
    }
    override fun close() { key.fill(0); if (file.exists() && !file.delete()) throw IOException("Protected staging cleanup failed") }
}

internal interface BackupEnvelopeCodec {
    suspend fun write(plain: ProtectedSpool, destination: File, password: CharArray)
    suspend fun read(source: InputStream, root: File, password: CharArray): ProtectedSpool
}

/** Streaming Base64 and encryption; GCM decryption provider buffering is explicitly budgeted. */
internal class V1BackupEnvelope(private val workspace: () -> Long = {
    val r = Runtime.getRuntime(); r.maxMemory() - (r.totalMemory() - r.freeMemory()) - 24L * 1024 * 1024
}) : BackupEnvelopeCodec {
    override suspend fun write(plain: ProtectedSpool, destination: File, password: CharArray) = fileOperationGate.withLock {
        val context = currentCoroutineContext()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = derive(password, salt)
        try {
            val cipher = cipher(Cipher.ENCRYPT_MODE, key, salt, nonce)
            destination.outputStream().buffered().use { raw ->
                val out = LimitedOutput(raw)
                fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                text("{\"v\":1,\"kdf\":\"PBKDF2-HMAC-SHA256\",\"iter\":200000,\"salt\":\"${b64(salt)}\",\"nonce\":\"${b64(nonce)}\",\"ct\":\"")
                val encoded = Base64.getEncoder().wrap(object : FilterOutputStream(out) { override fun close() { flush() } })
                plain.input().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    try { while (true) { context.ensureActive(); val n = readBackupChunk(input, buffer); if (n < 0) break; val encrypted = cipher.update(buffer, 0, n); if (encrypted != null) { encoded.write(encrypted); encrypted.fill(0) } } }
                    finally { buffer.fill(0) }
                }
                val final = cipher.doFinal()
                try { require(final.size >= 16); encoded.write(final, 0, final.size - 16); encoded.close(); text("\",\"tag\":\"${b64(final.copyOfRange(final.size - 16, final.size))}\"}") }
                finally { final.fill(0) }
            }
        } finally { key.fill(0) }
    }

    override suspend fun read(source: InputStream, root: File, password: CharArray): ProtectedSpool {
        var completed: ProtectedSpool? = null
        try {
            return source.use {
                fileOperationGate.withLock {
                    val context = currentCoroutineContext(); val check = { context.ensureActive() }
                    val staged = File.createTempFile("backup-", ".envelope", root)
                    val ciphertext = try { File.createTempFile("backup-", ".ciphertext", root) } catch (failure: Throwable) { staged.delete(); throw failure }
                    var result: ProtectedSpool? = null
                    try {
                        staged.outputStream().use { out -> val buffer = ByteArray(65536); var total = 0L
                            while (true) { check(); val n = readBackupChunk(source, buffer); if (n < 0) break; total += n; if (total > LIMIT) throw EnvelopeException(EnvelopeError.TOO_LARGE); out.write(buffer, 0, n) }
                        }
                        var version: String? = null; var kdf: String? = null; var iterations: String? = null
                        var salt: ByteArray? = null; var nonce: ByteArray? = null; var tag: ByteArray? = null; var hasCiphertext = false
                        strict(staged.inputStream()).use { reader ->
                            val json = StrictJson(reader, check)
                            json.objectMembers { name -> when (name) {
                                "v" -> version = json.number()
                                "kdf" -> kdf = json.string(64)
                                "iter" -> iterations = json.number()
                                "salt" -> salt = binary(json.string(64), 16)
                                "nonce" -> nonce = binary(json.string(64), 12)
                                "tag" -> tag = binary(json.string(64), 16)
                                "ct" -> { hasCiphertext = true; ciphertext.outputStream().buffered().use { out ->
                                    val decoder = BinarySink(out, LIMIT)
                                    json.string(decoder::accept); decoder.finish()
                                } }
                                else -> invalid()
                            } }; json.end()
                        }
                        if (version != "1") throw EnvelopeException(EnvelopeError.UNSUPPORTED_FORMAT)
                        if (kdf != "PBKDF2-HMAC-SHA256" || iterations != "200000" || !hasCiphertext) invalid()
                        val s = salt ?: invalid(); val n = nonce ?: invalid(); val t = tag ?: invalid()
                        // Android/JCA providers may buffer every decrypted byte until authentication.
                        if (ciphertext.length() * 4 + 2L * 1024 * 1024 > workspace().coerceAtLeast(0)) throw EnvelopeException(EnvelopeError.RESOURCE_LIMIT)
                        val key = derive(password, s)
                        try {
                            val spool = ProtectedSpool(root); result = spool
                            val cipher = cipher(Cipher.DECRYPT_MODE, key, s, n)
                            spool.output().use { output ->
                                ciphertext.inputStream().use { input -> val buffer = ByteArray(65536)
                                    try { while (true) { check(); val count = readBackupChunk(input, buffer); if (count < 0) break; val plain = cipher.update(buffer, 0, count); if (plain != null) { try { output.write(plain) } finally { plain.fill(0) } } } }
                                    finally { buffer.fill(0) }
                                }
                                val final = try { cipher.doFinal(t) } catch (_: javax.crypto.BadPaddingException) { throw EnvelopeException(EnvelopeError.AUTHENTICATION) }
                                try { check(); output.write(final) } finally { final.fill(0) }
                            }
                            check(); spool.also { completed = it }
                        } finally { key.fill(0) }
                    } catch (e: Throwable) { result?.close(); throw e }
                    finally {
                        val first = !staged.exists() || staged.delete(); val second = !ciphertext.exists() || ciphertext.delete()
                        if (!first || !second) { result?.close(); throw EnvelopeException(EnvelopeError.IO) }
                    }
                }
            }
        } catch (failure: Throwable) {
            completed?.close()
            throw failure
        }
    }

    companion object {
        const val LIMIT = 67_108_864L
        fun validatePassword(password: CharArray) { if (Character.codePointCount(password, 0, password.size) < 8 || password.any { it.isSurrogate() } && !validSurrogates(password)) throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL) }
        private fun validSurrogates(chars: CharArray): Boolean { var i = 0; while (i < chars.size) { val c = chars[i++]; if (c.isHighSurrogate()) { if (i == chars.size || !chars[i++].isLowSurrogate()) return false } else if (c.isLowSurrogate()) return false }; return true }
        private fun derive(password: CharArray, salt: ByteArray): ByteArray { validatePassword(password); val spec = PBEKeySpec(password, salt, 200000, 256); return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() } }
        private fun cipher(mode: Int, key: ByteArray, salt: ByteArray, nonce: ByteArray): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD("aqbak:v1".toByteArray(Charsets.UTF_8) + salt + nonce) }
        private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        private fun binary(s: String, size: Int): ByteArray = try { Base64.getDecoder().decode(s).also { if (it.size != size || b64(it) != s) invalid() } } catch (_: IllegalArgumentException) { invalid() }
        fun strict(input: InputStream): Reader = InputStreamReader(input, Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)).buffered()
    }
    private class LimitedOutput(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { if (++count > LIMIT) throw EnvelopeException(EnvelopeError.TOO_LARGE); out.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { count += len; if (count > LIMIT) throw EnvelopeException(EnvelopeError.TOO_LARGE); out.write(b, off, len) }
    }
}

/** Canonical incremental Base64 without per-quartet strings/arrays for large backups. */
private class BinarySink(private val out: OutputStream, private val limit: Long) {
    private val group = IntArray(4)
    private var position = 0
    private var padded = false
    private var count = 0L
    fun accept(char: Char) {
        if (padded) invalid()
        group[position++] = when (char) {
            in 'A'..'Z' -> char - 'A'; in 'a'..'z' -> char - 'a' + 26
            in '0'..'9' -> char - '0' + 52; '+' -> 62; '/' -> 63; '=' -> -1
            else -> invalid()
        }
        if (position == 4) {
            val a = group[0]; val b = group[1]; val c = group[2]; val d = group[3]
            if (a < 0 || b < 0 || (c < 0 && (d >= 0 || b and 15 != 0)) || (c >= 0 && d < 0 && c and 3 != 0)) invalid()
            emit((a shl 2) or (b shr 4))
            if (c >= 0) emit((b shl 4) or (c shr 2))
            if (d >= 0) emit((c shl 6) or d)
            padded = c < 0 || d < 0; position = 0
        }
    }
    private fun emit(value: Int) { if (++count > limit) throw EnvelopeException(EnvelopeError.TOO_LARGE); out.write(value) }
    fun finish() { if (position != 0 || count == 0L) invalid() }
}

/** Some document providers return zero for a bulk read; consume one byte rather than spin. */
internal fun readBackupChunk(input: InputStream, buffer: ByteArray): Int {
    val count = input.read(buffer)
    if (count != 0) return count
    val byte = input.read()
    if (byte < 0) return -1
    buffer[0] = byte.toByte()
    return 1
}
