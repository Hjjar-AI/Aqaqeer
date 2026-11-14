package com.aqaqeer.app.admin

import com.aqaqeer.app.security.AdminPinKdfV1
import com.aqaqeer.app.data.PinVerifier
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PinSecurity {
    fun digits(input: CharArray): CharArray = input.map { c ->
        val n = Character.digit(c, 10); adminRequire(n in 0..9) { "أدخل أرقاماً فقط" }; ('0'.code + n).toChar()
    }.toCharArray()
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, AdminPinKdfV1.ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun create(input: CharArray): PinVerifier {
        val pin = digits(input)
        try {
            adminRequire(pin.size >= 4) { "رمز الإدارة أربعة أرقام على الأقل" }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = derive(pin, salt)
            return try { PinVerifier(Base64.getEncoder().encodeToString(salt), AdminPinKdfV1.ITERATIONS, Base64.getEncoder().encodeToString(hash)) }
            finally { hash.fill(0) }
        } finally { pin.fill('\u0000') }
    }
    fun matches(input: CharArray, verifier: PinVerifier): Boolean {
        val pin = digits(input)
        return try {
            require(pin.size >= 4 && verifier.iterations == AdminPinKdfV1.ITERATIONS)
            val hash = derive(pin, Base64.getDecoder().decode(verifier.salt))
            try { MessageDigest.isEqual(hash, Base64.getDecoder().decode(verifier.hash)) } finally { hash.fill(0) }
        } finally { pin.fill('\u0000') }
    }
}
