package com.aqaqeer.app.maintenance

import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.PendingConnectionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable internal enum class MaintenanceKind { REPLACEMENT, ROTATION, MIGRATION }
@Serializable internal enum class MaintenancePhase { PREPARING, STAGED, VERIFIED, SOURCE_READY, BACKED_UP, TOKEN_CHANGE,
    DESTINATION_READY, PREPARED, UPLOAD_STARTED, UPLOADED, WRITE_STARTED, ACTIVATING, REVIEW, COMPLETE, CANCELLING }
@Serializable internal data class MaintenanceReceipt(val flowId: String, val sourceId: String, val stateDigest: String, val closedAt: Long)
@Serializable internal data class MaintenanceJournal(val version: Int = 1, val id: String, val pharmacyId: String,
    val kind: MaintenanceKind, val activationEntry: String = "candidate", val phase: MaintenancePhase = MaintenancePhase.PREPARING,
    val sourceId: String? = null, val sourceRevision: Long? = null, val sourceTime: String? = null,
    val sourceDigest: String? = null, val stateDigest: String? = null, val receipt: MaintenanceReceipt? = null,
    val sourceBotId: Long? = null, val sourceGroupId: Long? = null,
    val destinationBotId: Long? = null, val destinationGroupId: Long? = null,
    val sourceUnavailable: Boolean = false, val sourceTitle: String? = null, val destinationTitle: String? = null,
    val destinationConfirmed: Boolean = false, val candidateId: String? = null, val candidateTime: String? = null,
    val uploadedFileId: String? = null, val uploadedMessageId: Long? = null, val originalPin: String? = null,
    val targetMessageId: Long? = null, val targetFileId: String? = null, val targetUniqueId: String? = null,
    val checkpointId: String? = null)

internal data class MaintenanceStatus(val journal: MaintenanceJournal? = null, val unreadable: Boolean = false, val loading: Boolean = false)

internal class MaintenanceProblem(val reason: String) : Exception()
internal class MaintenanceReview(val conflicts: List<com.aqaqeer.app.admin.EditConflict>) : Exception()

@Singleton
class MaintenanceStore @Inject constructor(internal val secure: SecureStore) {
    internal fun read(): MaintenanceJournal? = decode<MaintenanceJournal>("maintenance-journal")?.also { j ->
        try {
            fun uuid(value: String) { require(UUID.fromString(value).toString() == value) }
            require(j.version == 1); uuid(j.id); uuid(j.pharmacyId)
            listOfNotNull(j.sourceId, j.candidateId, j.checkpointId).forEach(::uuid)
            require(j.activationEntry in setOf("candidate", "old", "transport"))
            require(j.activationEntry == "candidate" || j.kind == MaintenanceKind.REPLACEMENT)
            require(j.sourceRevision == null || j.sourceRevision > 0)
            require(j.phase in setOf(MaintenancePhase.PREPARING, MaintenancePhase.CANCELLING) || j.stateDigest != null)
            if (j.kind == MaintenanceKind.REPLACEMENT) require(j.phase in setOf(MaintenancePhase.PREPARING,
                MaintenancePhase.STAGED, MaintenancePhase.VERIFIED, MaintenancePhase.ACTIVATING, MaintenancePhase.REVIEW,
                MaintenancePhase.COMPLETE, MaintenancePhase.CANCELLING))
            j.receipt?.let { require(it.flowId == j.id && it.sourceId == j.sourceId && it.stateDigest == j.stateDigest && it.closedAt > 0) }
        } catch (error: Exception) { throw StorageRecoveryRequired(error) }
    }
    internal fun write(value: MaintenanceJournal?) {
        encode("maintenance-journal", value)
        changes.value += 1
    }
    internal inline fun <reified T : Any> decode(name: String): T? {
        val decodeBytes: (ByteArray) -> T? = { bytes ->
            try {
                if (bytes.isEmpty()) null else {
                require(bytes.size <= 256 * 1024)
                Json.decodeFromString<T>(bytes.toString(Charsets.UTF_8))
                }
            } catch (error: Exception) { throw StorageRecoveryRequired(error) }
        }
        if (name == "maintenance-journal") return com.aqaqeer.app.security.ProtectedReadCache.read(secure, name, decodeBytes)
        val bytes = secure.read(name) ?: return null
        return try { decodeBytes(bytes) } finally { bytes.fill(0) }
    }
    internal inline fun <reified T> encode(name: String, value: T?) {
        val bytes = value?.let { Json.encodeToString(it).toByteArray(Charsets.UTF_8) } ?: byteArrayOf()
        try { require(bytes.size <= 256 * 1024); secure.write(name, bytes) } finally { bytes.fill(0); com.aqaqeer.app.security.ProtectedReadCache.invalidate(secure, name) }
    }
    internal fun connection(entry: String) = PendingConnectionStore(secure, "maintenance-$entry")
    internal fun discardConnections() {
        for (entry in listOf("old", "candidate", "transport")) connection(entry).clear()
    }
    internal fun clearForRestore() { write(null); encode<PinChangeJournal>("pin-change", null); discardConnections() }
    companion object {
        internal val changes = MutableStateFlow(0L)
        internal fun digest(text: String): String = MessageDigest.getInstance("SHA-256").let {
            it.text(text); it.digest().hex()
        }
    }
}

/** Frame nullable fields and feed small UTF-8 chunks without copying full recovery payloads. */
private fun MessageDigest.text(value: String?) {
    if (value == null) { update(0.toByte()); return }
    update(1.toByte())
    update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array())
    var offset = 0
    while (offset < value.length) {
        var end = minOf(offset + 8192, value.length)
        if (end < value.length && Character.isHighSurrogate(value[end - 1])) end--
        val bytes = value.substring(offset, end).toByteArray(Charsets.UTF_8)
        try { update(bytes) } finally { bytes.fill(0) }
        offset = end
    }
}
private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

internal fun maintenanceStateDigest(catalog: CatalogSnapshot, state: SnapshotState, pending: List<PendingOperation>, attempts: List<PublishAttempt>, historyCount: Int): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.text(com.aqaqeer.app.admin.editJson.encodeToString(catalog))
    digest.text(state.publishId); digest.text(state.confirmedPayloadJson); digest.text(historyCount.toString())
    digest.text(pending.size.toString())
    pending.forEach { op ->
        listOf(op.id, op.transactionId, op.entityType, op.targetId, op.field, op.baseValueJson, op.intendedValueJson,
            op.authorId, op.authorName, op.authoredAt.toString(), op.basePublishId).forEach { digest.text(it) }
    }
    digest.text(attempts.size.toString())
    attempts.forEach { attempt ->
        listOf(attempt.publishId, attempt.basePayloadJson, attempt.candidatePayloadJson, attempt.includedOperationIdsJson,
            attempt.transportPhase, attempt.uploadedFileId, attempt.uploadedMessageId?.toString()).forEach { digest.text(it) }
    }
    return digest.digest().hex()
}
