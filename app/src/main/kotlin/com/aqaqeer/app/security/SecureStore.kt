package com.aqaqeer.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecureStore {
    val cacheScope: Any get() = this
    fun read(name: String): ByteArray?
    fun write(name: String, value: ByteArray)
}

class StorageRecoveryRequired(cause: Throwable? = null) : Exception("Protected storage needs recovery", cause)

/** Secrets and key wrappers live in noBackupFilesDir; no device/PIN authentication gate. */
class KeystoreSecureStore(context: Context, namespace: String = "main") : SecureStore {
    init { require(namespace.matches(Regex("[a-zA-Z0-9_-]+"))) }
    private val directory = File(context.noBackupFilesDir, "secure-$namespace")
    override val cacheScope: Any get() = directory.absolutePath
    val keyAlias: String = "${context.packageName}.aqaqeer.$namespace.v1"
    private val aadPrefix = "${context.packageName}:$namespace:v1:"
    private fun file(name: String): AtomicFile {
        require(name.matches(Regex("[a-zA-Z0-9_-]+")))
        check(directory.isDirectory || directory.mkdirs())
        return AtomicFile(File(directory, "$name.bin"))
    }
    private fun masterKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        if (!create || directory.listFiles().orEmpty().any { it.isFile }) throw StorageRecoveryRequired()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }
    override fun read(name: String): ByteArray? = synchronized(ProtectedReadCache.lock) {
        val atomic = file(name)
        // AtomicFile handles interrupted writes, including a surviving backup file.
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return@synchronized null
        try {
            val bytes = atomic.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return@synchronized DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                require(data.readInt() == MAGIC)
                val nonce = ByteArray(12).also(data::readFully)
                val length = data.readInt()
                require(length in 16..MAX_BYTES && length == data.available())
                val encrypted = ByteArray(length).also(data::readFully)
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, masterKey(create = false), GCMParameterSpec(128, nonce))
                    updateAAD((aadPrefix + name).toByteArray(Charsets.UTF_8))
                    doFinal(encrypted)
                }
            }
        } catch (e: Exception) { throw StorageRecoveryRequired(e) }
    }
    override fun write(name: String, value: ByteArray): Unit = synchronized(ProtectedReadCache.lock) {
        try {
            require(value.size <= MAX_BYTES - 64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, masterKey(create = true))
                updateAAD((aadPrefix + name).toByteArray(Charsets.UTF_8))
            }
            val encrypted = cipher.doFinal(value)
            val bytes = ByteArrayOutputStream().also { out ->
                DataOutputStream(out).use { data ->
                    data.writeInt(MAGIC); data.write(cipher.iv); data.writeInt(encrypted.size); data.write(encrypted)
                }
            }.toByteArray()
            val atomic = file(name)
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (e: Exception) { atomic.failWrite(stream); throw e }
        } catch (e: Exception) { throw StorageRecoveryRequired(e) }
        finally { ProtectedReadCache.invalidate(this, name) }
    }
    companion object {
        private const val MAGIC = 0x41515331
        private const val MAX_BYTES = 1_048_576
    }
}

class DatabaseKeyProvider(private val secureStore: SecureStore) {
    @Synchronized fun get(databaseExists: Boolean): ByteArray {
        secureStore.read("database-key")?.let {
            if (it.size != 32) throw StorageRecoveryRequired()
            return it
        }
        if (databaseExists) throw StorageRecoveryRequired()
        return ByteArray(32).also {
            SecureRandom().nextBytes(it)
            secureStore.write("database-key", it)
        }
    }
}
