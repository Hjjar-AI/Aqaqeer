package com.aqaqeer.app.sync

import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant

/** A verified observation only. The caller must persist compatibility/apply data through domain commands. */
class VerifiedSnapshotRead internal constructor(val document: SnapshotDocument,
    val snapshot: SnapshotCandidate, val observedAt: Instant)

/** A cached base must be decoded/validated from protected confirmed storage, never supplied by UI. */
internal class ValidatedSnapshotCache private constructor(val document: SnapshotDocument, val pharmacyId: String,
    val keyVersion: Int, val snapshot: SnapshotCandidate.Supported) {
    companion object {
        suspend fun fromConfirmed(document: SnapshotDocument, pharmacyId: String, keyVersion: Int,
            appVersionCode: Int, payload: String): ValidatedSnapshotCache? {
            if (payload.length > 32 * 1024 * 1024) return null
            val result = PayloadReader().snapshot(AuthenticatedJson(payload.toByteArray(), EnvelopeDomain.SNAPSHOT),
                pharmacyId, keyVersion, appVersionCode) as? SnapshotCandidate.Supported ?: return null
            if (result.publication != document.publication) return null
            return ValidatedSnapshotCache(document, pharmacyId, keyVersion, result)
        }
    }
}

/** Initial verification/recovery always download. Refresh may reuse a validated unchanged document. */
class SnapshotReadClient(private val transport: SnapshotReadTransport,
    private val envelopes: EnvelopeReader, private val payloads: PayloadReader = PayloadReader(),
    private val clock: Clock = Clock.systemUTC()) {
    private val gate = Mutex()

    /** Caller owns candidate; this operation clears only its copied snapshot key. */
    suspend fun read(candidate: ConnectionCandidate, appVersionCode: Int): VerifiedSnapshotRead = readWithCache(candidate, appVersionCode, null)

    internal suspend fun readWithCache(candidate: ConnectionCandidate, appVersionCode: Int,
        cache: ValidatedSnapshotCache?): VerifiedSnapshotRead = gate.withLock {
        val context = currentCoroutineContext()
        context.ensureActive()
        if (appVersionCode <= 0) invalidResponse()
        val secrets = candidate.copySecrets()
        try {
            val bot = transport.bot(secrets.botToken)
            if (bot.id <= 0) invalidResponse()
            val before = transport.pinned(secrets.botToken, secrets.groupId, bot)
            validate(before, secrets.groupId)
            context.ensureActive()
            val reuse = cache?.takeIf { it.document == before && it.pharmacyId == candidate.pharmacyId &&
                it.keyVersion == candidate.keyVersion && SnapshotCompatibilityPolicy.supports(
                    it.snapshot.compatibility.schemaVersion, it.snapshot.compatibility.minVersionCode, appVersionCode) }
            val snapshot = reuse?.snapshot ?: run {
                val input = transport.document(secrets.botToken, before)
                val plaintext = envelopes.snapshot(input, secrets.snapshotKey)
                payloads.snapshot(plaintext, candidate.pharmacyId, secrets.keyVersion, appVersionCode)
            }
            if (snapshot.publication != before.publication) throw SnapshotReadException(SnapshotReadError.CAPTION_MISMATCH)
            context.ensureActive()
            val after = transport.pinned(secrets.botToken, secrets.groupId, bot)
            validate(after, secrets.groupId)
            if (before != after) throw SnapshotReadException(SnapshotReadError.CHANGED_DURING_READ)
            context.ensureActive()
            VerifiedSnapshotRead(after, snapshot, clock.instant())
        } finally { secrets.snapshotKey.fill(0) }
    }

    private fun validate(document: SnapshotDocument, groupId: Long) {
        if (document.groupId != groupId || groupId >= 0 || document.messageId <= 0 ||
            document.fileId.isBlank() || document.fileUniqueId.isBlank() ||
            document.fileId.length > 4096 || document.fileUniqueId.length > 4096) invalidResponse()
        // Validate fake/custom adapters through the same caption representation.
        SnapshotCaption.encode(document.publication)
    }
}
