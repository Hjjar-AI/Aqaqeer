package com.aqaqeer.app.sync

import com.aqaqeer.app.security.files.SnapshotPublication
import java.io.InputStream
import java.time.Instant
import java.util.UUID

enum class SnapshotReadError {
    INVALID_CREDENTIALS, ACCESS_DENIED, SNAPSHOT_UNAVAILABLE, INVALID_RESPONSE,
    RESPONSE_TOO_LARGE, RESOURCE_LIMIT, RATE_LIMITED, NETWORK, CHANGED_DURING_READ, CAPTION_MISMATCH,
}
class SnapshotReadException(val error: SnapshotReadError, val retryAfterSeconds: Int = 0) : Exception(error.name)

data class BotIdentity(val id: Long)
data class SnapshotDocument(val groupId: Long, val messageId: Long, val fileId: String,
    val fileUniqueId: String, val publication: SnapshotPublication)

/** Read-only transport. The caller owns/closes the returned document stream. */
interface SnapshotReadTransport {
    suspend fun bot(token: String): BotIdentity
    suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument
    suspend fun document(token: String, document: SnapshotDocument): InputStream
}

/** Deterministic injected fake adapter, with no bundled payload, credential, or automatic app wiring. */
class FakeSnapshotReadTransport(
    private val identity: BotIdentity,
    private val readPin: suspend (Long) -> SnapshotDocument,
    private val openDocument: suspend (SnapshotDocument) -> InputStream,
) : SnapshotReadTransport {
    override suspend fun bot(token: String) = identity
    override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument {
        if (bot != identity) throw SnapshotReadException(SnapshotReadError.INVALID_RESPONSE)
        return readPin(groupId)
    }
    override suspend fun document(token: String, document: SnapshotDocument): InputStream = openDocument(document)
}

/** Caption is discovery metadata only. Compare it to authenticated publication metadata before use. */
object SnapshotCaption {
    fun encode(publication: SnapshotPublication): String {
        validate(publication)
        return "aqaqeer:v1\nrevision=${publication.revision}\npublishId=${publication.publishId}\npublishedAt=${publication.publishedAt}"
    }
    fun decode(value: String): SnapshotPublication {
        if (value.length > 256) invalidResponse()
        val lines = value.split('\n')
        if (lines.size != 4 || lines[0] != "aqaqeer:v1" || !lines[1].startsWith("revision=") ||
            !lines[2].startsWith("publishId=") || !lines[3].startsWith("publishedAt=")) invalidResponse()
        try {
            val publication = SnapshotPublication(lines[1].removePrefix("revision=").toLong(),
                lines[2].removePrefix("publishId="), Instant.parse(lines[3].removePrefix("publishedAt=")))
            validate(publication)
            if (encode(publication) != value) invalidResponse()
            return publication
        } catch (_: IllegalArgumentException) { invalidResponse() }
        catch (_: java.time.DateTimeException) { invalidResponse() }
    }
    private fun validate(value: SnapshotPublication) {
        try {
            if (value.revision <= 0 || value.publishId.length != 36 ||
                UUID.fromString(value.publishId).toString() != value.publishId) invalidResponse()
        } catch (_: IllegalArgumentException) { invalidResponse() }
    }
}

internal fun invalidResponse(): Nothing = throw SnapshotReadException(SnapshotReadError.INVALID_RESPONSE)
