package com.aqaqeer.app.security.files

import com.aqaqeer.app.data.EditorRef
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.util.UUID

/** Display metadata only; no credential, PIN, role, or device identity. */
data class PendingConnectionInfo(val id: String, val pharmacyId: String, val pharmacyName: String,
    val createdAt: Instant, val createdBy: EditorRef)

/** Bounded protected connection record. The default entry holds the pending join;
 * activation uses a separate write-ahead active entry with the same format.
 * The caller serializes operations. SecureStore supplies authenticated, atomic writes.
 */
internal class PendingConnectionStore(private val secureStore: SecureStore,
    private val entry: String = "pending-connection") {
    fun info(): PendingConnectionInfo? = read()?.use { it.info }

    suspend fun <T> withCandidate(expectedId: String, block: suspend (ConnectionCandidate) -> T): T {
        val record = read() ?: throw PendingConnectionChanged()
        return record.use {
            if (it.info.id != expectedId) throw PendingConnectionChanged()
            block(it.candidate)
        }
    }

    fun stage(candidate: ConnectionCandidate): PendingConnectionInfo {
        check(info() == null) { "Pending connection already exists" }
        return retain(candidate, UUID.randomUUID().toString())
    }

    /** Write-ahead credentials for activation; caller must establish an unconfigured catalog. */
    internal fun retain(candidate: ConnectionCandidate, id: String): PendingConnectionInfo {
        require(UUID.fromString(id).toString() == id)
        val info = PendingConnectionInfo(id, candidate.pharmacyId,
            candidate.pharmacyName, candidate.createdAt, candidate.createdBy)
        val secrets = candidate.copySecrets()
        val buffer = SecretBuffer()
        try {
            DataOutputStream(buffer).use { data ->
                data.writeInt(MAGIC); data.writeBoolean(true)
                fun text(value: String) {
                    if (value.length > MAX_CHARS) throw EnvelopeException(EnvelopeError.RESOURCE_LIMIT)
                    data.writeUTF(value)
                }
                text(info.id); text(info.pharmacyId); text(info.pharmacyName)
                text(info.createdAt.toString()); text(info.createdBy.id); text(info.createdBy.name)
                text(secrets.botToken); data.writeLong(secrets.groupId)
                data.writeInt(secrets.keyVersion); data.write(secrets.snapshotKey)
            }
            val bytes = buffer.toByteArray()
            try { secureStore.write(entry, bytes) } finally { bytes.fill(0) }
            return info
        } finally { secrets.snapshotKey.fill(0); buffer.clear() }
    }

    fun discard(expectedId: String) {
        val current = info() ?: return
        if (current.id != expectedId) throw PendingConnectionChanged()
        clear()
    }

    /** Caller must have committed an explicit discard/leave decision for this owned entry. */
    internal fun clear() {
        // An authenticated empty marker avoids expanding SecureStore with a deletion API.
        val bytes = byteArrayOf(0x41, 0x51, 0x50, 0x31, 0)
        try { secureStore.write(entry, bytes) } finally { bytes.fill(0) }
    }

    private class Record(val info: PendingConnectionInfo, val candidate: ConnectionCandidate) : AutoCloseable {
        override fun close() = candidate.close()
    }

    private fun read(): Record? {
        val bytes = secureStore.read(entry) ?: return null
        var key: ByteArray? = null
        try {
            require(bytes.size <= MAX_RECORD_BYTES)
            return DataInputStream(bytes.inputStream()).use { data ->
                require(data.readInt() == MAGIC)
                when (data.readUnsignedByte()) {
                    0 -> { require(data.available() == 0); null }
                    1 -> {
                        fun text(): String = data.readUTF().also { require(it.length <= MAX_CHARS) }
                        fun uuid(): String = text().also { require(UUID.fromString(it).toString() == it) }
                        fun name(): String = text().also { require(it.isNotBlank() && it == it.trim()) }
                        val id = uuid(); val pharmacyId = uuid(); val pharmacyName = name()
                        val createdAt = Instant.parse(text()); val editor = EditorRef(uuid(), name())
                        val token = name(); val groupId = data.readLong(); val keyVersion = data.readInt()
                        require(groupId < 0 && keyVersion > 0)
                        val snapshotKey = ByteArray(32).also { key = it; data.readFully(it) }
                        require(data.available() == 0)
                        Record(PendingConnectionInfo(id, pharmacyId, pharmacyName, createdAt, editor),
                            ConnectionCandidate(pharmacyId, pharmacyName, createdAt, editor, token, groupId, snapshotKey, keyVersion))
                    }
                    else -> throw StorageRecoveryRequired()
                }
            }
        } catch (error: Exception) {
            key?.fill(0)
            throw StorageRecoveryRequired()
        } finally { bytes.fill(0) }
    }

    private class SecretBuffer : ByteArrayOutputStream(MAX_RECORD_BYTES) {
        fun clear() { buf.fill(0); reset() }
    }
    companion object {
        private const val MAGIC = 0x41515031
        private const val MAX_CHARS = 8192
        // Seven bounded modified-UTF strings plus fixed metadata fit below this and SecureStore's cap.
        private const val MAX_RECORD_BYTES = 256 * 1024
    }
}

internal class PendingConnectionChanged : Exception("Pending connection changed")
