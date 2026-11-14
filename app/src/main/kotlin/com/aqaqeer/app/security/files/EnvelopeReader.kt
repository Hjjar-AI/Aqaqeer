package com.aqaqeer.app.security.files

import com.aqaqeer.app.security.PasswordEnvelopeKdfV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class EnvelopeDomain(val marker: String, val byteLimit: Long) {
    CONNECTION("aqz:v1", 10L * 1024 * 1024),
    BACKUP("aqbak:v1", 64L * 1024 * 1024),
    SNAPSHOT("aqsnapshot:v1", 10L * 1024 * 1024),
}
enum class EnvelopeError { TOO_LARGE, INVALID_FORMAT, UNSUPPORTED_FORMAT, AUTHENTICATION, INVALID_CREDENTIAL,
    RESOURCE_LIMIT, IO, CRYPTO_UNAVAILABLE }
class EnvelopeException(val error: EnvelopeError) : Exception(error.name)

// Envelope crypto and authenticated model decoding share one process-wide resource gate.
internal val fileOperationGate = Mutex()

/** Authenticated JSON syntax only. Identity/schema/compatibility validation is still mandatory. */
class AuthenticatedJson internal constructor(private val bytes: ByteArray,
    internal val domain: EnvelopeDomain) : AutoCloseable {
    private var closed = false
    val byteCount get() = bytes.size
    fun <T> withUtf8Reader(block: (Reader) -> T): T {
        check(!closed) { "Payload closed" }
        return bytes.inputStream().reader(Charsets.UTF_8).use(block)
    }
    internal fun <T> withInputStream(block: (InputStream) -> T): T {
        check(!closed) { "Payload closed" }
        return bytes.inputStream().use(block)
    }
    override fun close() { bytes.fill(0); closed = true }
}

/** No imports, DB writes, network access, or plaintext temporary files.
 * stagingRoot must be an app-owned directory under Context.noBackupFilesDir.
 * This class owns only its aqaqeer-envelope-stage subdirectory.
 */
class EnvelopeReader(private val stagingRoot: File,
    private val workspaceBudget: () -> Long = ::availableWorkspace) {
    suspend fun password(input: InputStream, domain: EnvelopeDomain, password: CharArray): AuthenticatedJson {
        val copy = password.copyOf()
        try { return read(input, domain, copy, null) }
        finally { copy.fill('\u0000') }
    }
    suspend fun snapshot(input: InputStream, key: ByteArray): AuthenticatedJson {
        val copy = key.copyOf()
        try { return read(input, EnvelopeDomain.SNAPSHOT, null, copy) }
        finally { copy.fill(0) }
    }
    suspend fun cleanupAbandoned() = withContext(Dispatchers.IO) { gate.withLock { cleanup(directory()) } }

    private suspend fun read(input: InputStream, domain: EnvelopeDomain, password: CharArray?, suppliedKey: ByteArray?): AuthenticatedJson {
        var result: AuthenticatedJson? = null
        try { return input.use {
            withContext(Dispatchers.IO) {
                gate.withLock {
                    val context = currentCoroutineContext()
                    val checkpoint = { context.ensureActive() }
                    var staged: File? = null
                    try {
                        checkpoint()
                        val directory = directory()
                        cleanup(directory)
                        staged = File.createTempFile("envelope-", ".stage", directory)
                        val count = stage(input, staged, domain.byteLimit, checkpoint)
                        // Conservative allowance for provider buffers, arrays, duplicate-key sets and UTF-16 keys.
                        val estimate = count * 16L + 65_536L
                        if (estimate > workspaceBudget().coerceAtLeast(0)) throw EnvelopeException(EnvelopeError.RESOURCE_LIMIT)
                        val parsed = strictReader(staged.inputStream()).use { reader -> parse(reader, domain, checkpoint, count) }
                        try {
                            checkpoint()
                            val key = if (password != null) derive(domain, password, parsed.salt) else {
                                if (suppliedKey?.size != 32) throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
                                suppliedKey.copyOf()
                            }
                            try {
                                checkpoint()
                                decrypt(parsed, domain, key, checkpoint).also { result = it }
                            } finally { key.fill(0) }
                        } finally { parsed.clear() }
                    } catch (error: java.nio.charset.CharacterCodingException) {
                        invalid()
                    } catch (error: IOException) {
                        throw EnvelopeException(EnvelopeError.IO)
                    } catch (error: GeneralSecurityException) {
                        throw EnvelopeException(EnvelopeError.CRYPTO_UNAVAILABLE)
                    } finally {
                        staged?.let { if (it.exists() && !it.delete()) {
                            checkpoint()
                            throw EnvelopeException(EnvelopeError.IO)
                        } }
                    }
                }
            }
        } } catch (_: IOException) {
            result?.close()
            throw EnvelopeException(EnvelopeError.IO)
        } catch (error: Throwable) {
            // withContext can discard a finished result on cancellation during dispatch back.
            result?.close()
            throw error
        }
    }

    private fun directory(): File = File(stagingRoot, "aqaqeer-envelope-stage").apply {
        if ((!exists() && !mkdirs()) || !isDirectory) throw EnvelopeException(EnvelopeError.IO)
    }
    private fun cleanup(directory: File) {
        val files = directory.listFiles() ?: throw EnvelopeException(EnvelopeError.IO)
        files.filter { it.isFile && it.name.startsWith("envelope-") && it.name.endsWith(".stage") }
            .forEach { if (!it.delete()) throw EnvelopeException(EnvelopeError.IO) }
    }
    private fun stage(input: InputStream, file: File, limit: Long, check: () -> Unit): Long {
        var count = 0L
        val buffer = ByteArray(8192)
        file.outputStream().buffered().use { out ->
            while (true) {
                check()
                val length = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - count + 1).toInt())
                if (length == -1) break
                if (length == 0) {
                    val byte = input.read()
                    if (byte == -1) break
                    if (++count > limit) throw EnvelopeException(EnvelopeError.TOO_LARGE)
                    out.write(byte)
                } else {
                    count += length
                    if (count > limit) throw EnvelopeException(EnvelopeError.TOO_LARGE)
                    out.write(buffer, 0, length)
                }
            }
        }
        return count
    }
    private data class Parsed(val salt: ByteArray, val nonce: ByteArray, val ciphertext: ByteArray, val tag: ByteArray) {
        fun clear() { salt.fill(0); nonce.fill(0); ciphertext.fill(0); tag.fill(0) }
    }
    private fun parse(reader: Reader, domain: EnvelopeDomain, check: () -> Unit, size: Long): Parsed {
        val json = StrictJson(reader, check)
        val binary = HashMap<String, ByteArray>()
        var version: String? = null; var iterations: String? = null; var kdf: String? = null
        try {
            json.objectMembers { name ->
                when (name) {
                    "v" -> version = json.number()
                    "iter" -> iterations = json.number()
                    "kdf" -> kdf = json.string(64)
                    "salt", "nonce", "ct", "tag" -> {
                        val max = when (name) { "salt", "tag" -> 16L; "nonce" -> 12L; else -> size }
                        val decoder = BinaryString(max)
                        json.string(decoder::accept)
                        binary[name] = decoder.finish()
                    }
                    else -> invalid()
                }
            }
            json.end()
            if (version == null) invalid()
            if (!Regex("-?(0|[1-9][0-9]*)").matches(version)) invalid()
            if (iterations != null && !Regex("-?(0|[1-9][0-9]*)").matches(iterations)) invalid()
            if (version != "1") throw EnvelopeException(EnvelopeError.UNSUPPORTED_FORMAT)
            if (domain == EnvelopeDomain.SNAPSHOT) {
                if (kdf != null || iterations != null || "salt" in binary) invalid()
            } else {
                if (kdf == null || iterations == null || "salt" !in binary) invalid()
                if (kdf != PasswordEnvelopeKdfV1.NAME || iterations != PasswordEnvelopeKdfV1.ITERATIONS.toString()) throw EnvelopeException(EnvelopeError.UNSUPPORTED_FORMAT)
            }
            val salt = binary["salt"] ?: byteArrayOf()
            val nonce = binary["nonce"] ?: invalid()
            val tag = binary["tag"] ?: invalid()
            val ct = binary["ct"] ?: invalid()
            if (nonce.size != 12 || tag.size != 16 || ct.isEmpty() || (domain != EnvelopeDomain.SNAPSHOT && salt.size != 16)) invalid()
            return Parsed(salt, nonce, ct, tag)
        } catch (error: Throwable) { binary.values.forEach { it.fill(0) }; throw error }
    }
    private fun derive(domain: EnvelopeDomain, password: CharArray, salt: ByteArray): ByteArray {
        if (domain == EnvelopeDomain.SNAPSHOT) throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
        val normalized = password.copyOf()
        try {
            if (domain == EnvelopeDomain.CONNECTION) normalized.indices.forEach { index ->
                normalized[index] = when (val char = normalized[index]) {
                    in '0'..'9' -> char
                    in '٠'..'٩' -> '0' + (char - '٠')
                    in '۰'..'۹' -> '0' + (char - '۰')
                    else -> throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
                }
            }
            if (domain == EnvelopeDomain.CONNECTION && normalized.size < 6) throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
            if (domain == EnvelopeDomain.BACKUP && Character.codePointCount(normalized, 0, normalized.size) < 8)
                throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
            // Reject unpaired surrogates rather than allowing provider-specific replacement.
            var index = 0
            while (index < normalized.size) {
                val char = normalized[index++]
                if (char.isLowSurrogate()) throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
                if (char.isHighSurrogate() && (index == normalized.size || !normalized[index++].isLowSurrogate()))
                    throw EnvelopeException(EnvelopeError.INVALID_CREDENTIAL)
            }
            val spec = PBEKeySpec(normalized, salt, PasswordEnvelopeKdfV1.ITERATIONS, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        } finally { normalized.fill('\u0000') }
    }
    private fun decrypt(parsed: Parsed, domain: EnvelopeDomain, key: ByteArray, check: () -> Unit): AuthenticatedJson {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, parsed.nonce))
        cipher.updateAAD(domain.marker.toByteArray(Charsets.UTF_8) + parsed.salt + parsed.nonce)
        val combined = parsed.ciphertext + parsed.tag
        val plain = try { cipher.doFinal(combined) }
        catch (_: AEADBadTagException) { throw EnvelopeException(EnvelopeError.AUTHENTICATION) }
        finally { combined.fill(0) }
        try {
            check()
            strictReader(plain.inputStream()).use { StrictJson(it, check).validateObject() }
            check()
            return AuthenticatedJson(plain, domain)
        } catch (error: Throwable) { plain.fill(0); throw error }
    }
    companion object {
        private val gate = fileOperationGate
        private fun strictReader(input: InputStream): Reader = InputStreamReader(input,
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)).buffered()
        private fun availableWorkspace(): Long {
            val runtime = Runtime.getRuntime()
            return ((runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) - 16L * 1024 * 1024)
                .coerceAtLeast(0) / 4) * 3
        }
    }
}

/** Decode a JSON Base64 string incrementally, rejecting noncanonical encodings. */
private class BinaryString(private val limit: Long) {
    private val out = ByteArrayOutputStream()
    private val group = IntArray(4)
    private var position = 0
    private var padded = false
    fun accept(char: Char) {
        if (padded) invalid()
        group[position++] = when (char) {
            in 'A'..'Z' -> char - 'A'; in 'a'..'z' -> char - 'a' + 26
            in '0'..'9' -> char - '0' + 52; '+' -> 62; '/' -> 63; '=' -> -1
            else -> invalid()
        }
        if (position == 4) {
            val (a, b, c, d) = group
            if (a < 0 || b < 0 || (c < 0 && (d >= 0 || b and 15 != 0)) || (c >= 0 && d < 0 && c and 3 != 0)) invalid()
            emit((a shl 2) or (b shr 4))
            if (c >= 0) emit((b shl 4) or (c shr 2))
            if (d >= 0) emit((c shl 6) or d)
            padded = c < 0 || d < 0
            position = 0
        }
    }
    private fun emit(value: Int) { if (out.size().toLong() >= limit) invalid(); out.write(value) }
    fun finish(): ByteArray { if (position != 0 || out.size() == 0) invalid(); return out.toByteArray() }
}
