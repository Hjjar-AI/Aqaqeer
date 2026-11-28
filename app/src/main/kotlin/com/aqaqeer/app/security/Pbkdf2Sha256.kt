package com.aqaqeer.app.security

import android.os.Build
import java.nio.CharBuffer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** All v1 callers derive one 256-bit block from a nonempty, validated password. */
internal object Pbkdf2Sha256 {
    fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray =
        if (Build.VERSION.SDK_INT >= 26) platform(password, salt, iterations)
        else legacy(password, salt, iterations)

    internal fun platform(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    internal fun legacy(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(password.isNotEmpty() && salt.isNotEmpty() && iterations > 0)
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val key = ByteArray(encoded.remaining())
        val round = ByteArray(32)
        val result = ByteArray(32)
        var completed = false
        try {
            encoded.get(key)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, 1)) // Big-endian block index in RFC 8018.
            mac.doFinal(round, 0)
            round.copyInto(result)
            repeat(iterations - 1) {
                mac.update(round)
                mac.doFinal(round, 0)
                for (index in round.indices) {
                    result[index] = (result[index].toInt() xor round[index].toInt()).toByte()
                }
            }
            completed = true
            return result
        } finally {
            encoded.array().fill(0)
            key.fill(0)
            round.fill(0)
            if (!completed) result.fill(0)
        }
    }
}
