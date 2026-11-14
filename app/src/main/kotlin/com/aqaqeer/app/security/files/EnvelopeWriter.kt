@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.aqaqeer.app.security.files

import com.aqaqeer.app.security.PasswordEnvelopeKdfV1
import com.aqaqeer.app.admin.PinSecurity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** v1 production writer. Fixed crypto vectors are never sources of production randomness. */
internal object EnvelopeWriter {
    private const val LIMIT = 10 * 1024 * 1024
    private val json = Json { encodeDefaults = true }
    fun snapshot(wire: SnapshotWire, key: ByteArray): ByteArray {
        require(key.size == 32)
        return encode(wire, SnapshotWire.serializer()).let { plain ->
            try { encrypt(plain, key, null, "aqsnapshot:v1") } finally { plain.fill(0) }
        }
    }
    fun connection(wire: ConnectionWire, password: CharArray): ByteArray {
        val digits = PinSecurity.digits(password)
        try {
            require(digits.size >= 16) { "استخدم كلمة ملف اتصال عشوائية من ١٦ رقماً على الأقل" }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val spec = PBEKeySpec(digits, salt, PasswordEnvelopeKdfV1.ITERATIONS, 256)
            val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
                finally { spec.clearPassword() }
            val plain = encode(wire, ConnectionWire.serializer())
            return try { encrypt(plain, key, salt, "aqz:v1") } finally { key.fill(0); plain.fill(0) }
        } finally { digits.fill('\u0000'); password.fill('\u0000') }
    }
    private fun <T> encode(value: T, serializer: kotlinx.serialization.SerializationStrategy<T>): ByteArray {
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
        val cap = minOf((LIMIT - 512) * 3 / 4, ((available - 16L * 1024 * 1024).coerceAtLeast(0) / 16).toInt())
        val output = object : ByteArrayOutputStream() {
            override fun write(b: Int) { require(count < cap) { "البيانات أكبر من الحد المسموح" }; super.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { require(count.toLong() + len <= cap); super.write(b, off, len) }
            fun clear() { buf.fill(0) }
        }
        return try { json.encodeToStream(serializer, value, output); output.toByteArray() } finally { output.clear() }
    }
    private fun encrypt(plain: ByteArray, key: ByteArray, salt: ByteArray?, domain: String): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(domain.toByteArray() + (salt ?: byteArrayOf()) + nonce)
        }
        val encrypted = cipher.doFinal(plain)
        fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        return try {
            val envelope = buildJsonObject {
                put("v", 1)
                if (salt != null) { put("kdf", PasswordEnvelopeKdfV1.NAME); put("iter", PasswordEnvelopeKdfV1.ITERATIONS); put("salt", b64(salt)) }
                put("nonce", b64(nonce)); put("ct", b64(encrypted.copyOfRange(0, encrypted.size - 16)))
                put("tag", b64(encrypted.copyOfRange(encrypted.size - 16, encrypted.size)))
            }
            json.encodeToString(envelope).toByteArray().also { require(it.size <= LIMIT) }
        } finally { encrypted.fill(0) }
    }
}
