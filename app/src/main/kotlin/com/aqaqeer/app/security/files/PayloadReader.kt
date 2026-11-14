@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:kotlinx.serialization.UseSerializers(com.aqaqeer.app.data.InstantSerializer::class)

package com.aqaqeer.app.security.files

import com.aqaqeer.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.FilterInputStream
import java.time.Instant
import java.util.Base64
import java.util.UUID

enum class PayloadError { INVALID_PAYLOAD, UNSUPPORTED_SCHEMA, WRONG_PHARMACY, WRONG_KEY_VERSION, RESOURCE_LIMIT }
class PayloadException(val error: PayloadError) : Exception(error.name)

/** Candidate credentials only. No local authorization or network validation is implied.
 * The caller owns the result and must close it after activation/cancellation.
 */
class ConnectionCandidate internal constructor(
    val pharmacyId: String, val pharmacyName: String, val createdAt: Instant, val createdBy: EditorRef,
    private val token: String, val groupId: Long, private val key: ByteArray, val keyVersion: Int,
) : AutoCloseable {
    private var closed = false
    /** The returned key is a caller-owned copy; never log this value or retain it in preferences. */
    fun copySecrets(): ConnectionSecrets {
        check(!closed) { "Candidate closed" }
        return ConnectionSecrets(token, groupId, key.copyOf(), keyVersion)
    }
    override fun close() { key.fill(0); closed = true }
}

@Serializable
data class SnapshotCompatibility(val schemaVersion: Int, val minVersionCode: Int, val keyVersion: Int)

object SnapshotCompatibilityPolicy {
    fun supports(schemaVersion: Int, minVersionCode: Int, appVersionCode: Int): Boolean =
        schemaVersion == 1 && minVersionCode > 0 && minVersionCode <= appVersionCode
}

data class SnapshotPublication(val revision: Long, val publishId: String, val publishedAt: Instant)

/** An observation is not permission to apply data. Persistence/command guards belong to integration. */
sealed interface SnapshotCandidate {
    val publication: SnapshotPublication
    val compatibility: SnapshotCompatibility
    class RequiresUpdate internal constructor(val pharmacyId: String,
        override val compatibility: SnapshotCompatibility, override val publication: SnapshotPublication) : SnapshotCandidate
    class Supported internal constructor(
        override val compatibility: SnapshotCompatibility, val revision: Long, val publishId: String,
        val parentPublishId: String?, val basedOnRevision: Long, val publishedAt: Instant,
        val publishedBy: EditorRef, val catalog: CatalogSnapshot,
    ) : SnapshotCandidate {
        override val publication get() = SnapshotPublication(revision, publishId, publishedAt)
    }
}

/** Consumes/closes authenticated plaintext on every outcome, including queued cancellation.
 * No writes, network access, compatibility persistence, or authorization changes.
 */
class PayloadReader(private val workspaceBudget: () -> Long = ::availablePayloadWorkspace) {
    suspend fun connection(payload: AuthenticatedJson, expectedPharmacyId: String? = null): ConnectionCandidate {
        var candidate: ConnectionCandidate? = null
        try {
            return consume(payload, EnvelopeDomain.CONNECTION) { check ->
                connectionHeader(payload, check)
                val wire = decode<ConnectionWire>(payload, check)
                uuid(wire.pharmacyId)
                if (expectedPharmacyId != null && wire.pharmacyId != expectedPharmacyId) fail(PayloadError.WRONG_PHARMACY)
                name(wire.pharmacyName); editor(wire.createdBy)
                requirePayload(wire.botToken.isNotBlank() && wire.botToken == wire.botToken.trim())
                requirePayload(wire.groupId < 0 && wire.keyVersion > 0)
                val key = binary(wire.snapshotKey, 32)
                ConnectionCandidate(wire.pharmacyId, wire.pharmacyName, wire.createdAt, wire.createdBy,
                    wire.botToken, wire.groupId, key, wire.keyVersion).also { candidate = it }
            }
        } catch (error: Throwable) {
            // withContext can discard the completed candidate on dispatch-back cancellation.
            candidate?.close()
            throw error
        }
    }

    suspend fun snapshot(payload: AuthenticatedJson, expectedPharmacyId: String,
        expectedKeyVersion: Int, appVersionCode: Int): SnapshotCandidate = consume(payload, EnvelopeDomain.SNAPSHOT) { check ->
        uuid(expectedPharmacyId)
        requirePayload(expectedKeyVersion > 0 && appVersionCode > 0)
        // Read only the stable authenticated header first. Future entity schemas need not deserialize.
        val header = header(payload, check)
        if (header.pharmacyId != expectedPharmacyId) fail(PayloadError.WRONG_PHARMACY)
        if (header.compatibility.keyVersion != expectedKeyVersion) fail(PayloadError.WRONG_KEY_VERSION)
        if (!SnapshotCompatibilityPolicy.supports(header.compatibility.schemaVersion,
                header.compatibility.minVersionCode, appVersionCode)) {
            SnapshotCandidate.RequiresUpdate(header.pharmacyId, header.compatibility, header.publication)
        } else {
            val fields = header(payload, check, supported = true)
            val wire = decode<SnapshotWire>(payload, check)
            val pharmacy = wire.pharmacy
            requirePayload(wire.compatibility == header.compatibility && pharmacy.pharmacyId == header.pharmacyId)
            requirePayload(fields.pharmacyCompatibility == wire.compatibility)
            requirePayload(fields.revision == wire.revision && fields.basedOnRevision == wire.basedOnRevision)
            requirePayload(header.publication == SnapshotPublication(wire.revision, wire.publishId, wire.publishedAt))
            requirePayload(pharmacy.schemaVersion == wire.compatibility.schemaVersion &&
                pharmacy.minVersionCode == wire.compatibility.minVersionCode && pharmacy.keyVersion == wire.compatibility.keyVersion)
            uuid(wire.publishId); wire.parentPublishId?.let(::uuid); editor(wire.publishedBy)
            requirePayload(wire.revision > 0 && wire.basedOnRevision >= 0 && wire.basedOnRevision == wire.revision - 1)
            requirePayload((wire.revision == 1L) == (wire.parentPublishId == null))
            requirePayload(wire.parentPublishId != wire.publishId)
            val catalog = CatalogSnapshot(pharmacy, wire.categories, wire.forms, wire.sections,
                wire.medications, wire.availability, wire.tombstones)
            CatalogRules.validate(catalog)
            binary(pharmacy.adminPinVerifier.salt, 16).fill(0)
            binary(pharmacy.adminPinVerifier.hash, 32).fill(0)
            catalog.categories.forEach { check(); uuid(it.id) }
            catalog.forms.forEach { check(); uuid(it.id) }
            catalog.sections.forEach { check(); uuid(it.id) }
            catalog.medications.forEach { medication -> check(); uuid(medication.id); editor(medication.updatedBy) }
            catalog.availability.forEach { availability -> check(); editor(availability.updatedBy) }
            requirePayload(catalog.tombstones.map { Triple(it.entityType, it.entityId, it.operationId) }.toSet().size == catalog.tombstones.size)
            catalog.tombstones.forEach { check(); uuid(it.entityId); uuid(it.operationId); name(it.entityType) }
            SnapshotCandidate.Supported(wire.compatibility, wire.revision, wire.publishId, wire.parentPublishId,
                wire.basedOnRevision, wire.publishedAt, wire.publishedBy, catalog)
        }
    }

    private suspend fun <T> consume(payload: AuthenticatedJson, domain: EnvelopeDomain, block: (() -> Unit) -> T): T =
        payload.use {
            withContext(Dispatchers.Default) {
                gate.withLock {
                    val context = currentCoroutineContext()
                    val check = { context.ensureActive() }
                    check()
                    requirePayload(payload.domain == domain)
                    // Models/strings/sets coexist with authenticated bytes. Reject before materializing them.
                    if (payload.byteCount * 32L + 65_536 > workspaceBudget().coerceAtLeast(0)) fail(PayloadError.RESOURCE_LIMIT)
                    try {
                        block(check).also { check() }
                    } catch (error: EnvelopeException) {
                        fail(if (error.error == EnvelopeError.RESOURCE_LIMIT) PayloadError.RESOURCE_LIMIT else PayloadError.INVALID_PAYLOAD)
                    } catch (_: SerializationException) {
                        fail(PayloadError.INVALID_PAYLOAD)
                    } catch (_: IllegalArgumentException) {
                        fail(PayloadError.INVALID_PAYLOAD)
                    } catch (_: java.time.DateTimeException) {
                        fail(PayloadError.INVALID_PAYLOAD)
                    }
                }
            }
        }

    private fun connectionHeader(payload: AuthenticatedJson, check: () -> Unit) = payload.withUtf8Reader { reader ->
        val json = StrictJson(reader, check)
        var schema: Int? = null; var key: Int? = null; var group: Long? = null
        json.objectMembers { name ->
            when (name) {
                "schemaVersion" -> schema = positiveInteger(json)
                "keyVersion" -> key = positiveInteger(json)
                "groupId" -> {
                    val token = json.number()
                    requirePayload(Regex("-[1-9][0-9]*").matches(token))
                    group = token.toLongOrNull() ?: fail(PayloadError.INVALID_PAYLOAD)
                }
                else -> json.skipValue()
            }
        }
        json.end()
        requirePayload(schema != null)
        if (schema != 1) fail(PayloadError.UNSUPPORTED_SCHEMA)
        requirePayload(key != null && group != null)
    }

    private data class Header(val pharmacyId: String, val compatibility: SnapshotCompatibility,
        val pharmacyCompatibility: SnapshotCompatibility?, val revision: Long?, val basedOnRevision: Long?,
        val publication: SnapshotPublication)
    private fun header(payload: AuthenticatedJson, check: () -> Unit, supported: Boolean = false): Header = payload.withUtf8Reader { reader ->
        val json = StrictJson(reader, check)
        var pharmacyId: String? = null
        var compatibility: SnapshotCompatibility? = null
        var pharmacySchema: Int? = null; var pharmacyMinimum: Int? = null; var pharmacyKey: Int? = null
        var revision: Long? = null; var basedOnRevision: Long? = null
        var publishId: String? = null; var publishedAt: Instant? = null
        json.objectMembers { field ->
            when (field) {
                "compatibility" -> {
                    var schema: Int? = null; var minimum: Int? = null; var key: Int? = null
                    json.objectMembers(2) { name ->
                        when (name) {
                            "schemaVersion" -> schema = positiveInteger(json)
                            "minVersionCode" -> minimum = positiveInteger(json)
                            "keyVersion" -> key = positiveInteger(json)
                            else -> fail(PayloadError.INVALID_PAYLOAD)
                        }
                    }
                    compatibility = SnapshotCompatibility(schema ?: fail(PayloadError.INVALID_PAYLOAD),
                        minimum ?: fail(PayloadError.INVALID_PAYLOAD), key ?: fail(PayloadError.INVALID_PAYLOAD))
                }
                "pharmacy" -> json.objectMembers(2) { name ->
                    when (name) {
                        "pharmacyId" -> pharmacyId = json.string(36)
                        "schemaVersion" -> if (supported) pharmacySchema = positiveInteger(json) else json.skipValue()
                        "minVersionCode" -> if (supported) pharmacyMinimum = positiveInteger(json) else json.skipValue()
                        "keyVersion" -> if (supported) pharmacyKey = positiveInteger(json) else json.skipValue()
                        else -> json.skipValue()
                    }
                }
                "revision" -> revision = json.number().toLongOrNull() ?: fail(PayloadError.INVALID_PAYLOAD)
                "publishId" -> publishId = json.string(36)
                "publishedAt" -> publishedAt = Instant.parse(json.string(64))
                "basedOnRevision" -> if (supported) basedOnRevision = json.number().toLongOrNull() ?: fail(PayloadError.INVALID_PAYLOAD) else json.skipValue()
                else -> json.skipValue()
            }
        }
        json.end()
        val id = pharmacyId ?: fail(PayloadError.INVALID_PAYLOAD)
        uuid(id)
        val publicationId = publishId ?: fail(PayloadError.INVALID_PAYLOAD)
        uuid(publicationId)
        val publicationRevision = revision ?: fail(PayloadError.INVALID_PAYLOAD)
        requirePayload(publicationRevision > 0)
        val publication = SnapshotPublication(publicationRevision, publicationId,
            publishedAt ?: fail(PayloadError.INVALID_PAYLOAD))
        val metadata = if (pharmacySchema != null && pharmacyMinimum != null && pharmacyKey != null)
            SnapshotCompatibility(pharmacySchema, pharmacyMinimum, pharmacyKey) else null
        Header(id, compatibility ?: fail(PayloadError.INVALID_PAYLOAD), metadata, revision, basedOnRevision, publication)
    }

    private inline fun <reified T> decode(payload: AuthenticatedJson, crossinline check: () -> Unit): T =
        payload.withInputStream { input ->
            // Check cancellation while the streaming serializer reads, without another plaintext copy/tree.
            val checked = object : FilterInputStream(input) {
                override fun read(): Int { check(); return input.read() }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    check(); return input.read(buffer, offset, length)
                }
            }
            json.decodeFromStream<T>(checked)
        }

    companion object {
        private val gate = fileOperationGate
        private val json = Json { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
        private fun positiveInteger(json: StrictJson): Int {
            val token = json.number()
            requirePayload(token.all { it in '0'..'9' })
            return (token.toIntOrNull() ?: fail(PayloadError.INVALID_PAYLOAD)).also { requirePayload(it > 0) }
        }
        private fun uuid(value: String) {
            requirePayload(value.length == 36 && UUID.fromString(value).toString() == value)
        }
        private fun name(value: String) { requirePayload(value.isNotBlank() && value == value.trim()) }
        private fun editor(value: EditorRef) { uuid(value.id); name(value.name) }
        private fun binary(value: String, length: Int): ByteArray {
            requirePayload(value.length == ((length + 2) / 3) * 4)
            val bytes = Base64.getDecoder().decode(value)
            if (bytes.size != length || Base64.getEncoder().encodeToString(bytes) != value) {
                bytes.fill(0)
                fail(PayloadError.INVALID_PAYLOAD)
            }
            return bytes
        }
        private fun requirePayload(value: Boolean) { if (!value) fail(PayloadError.INVALID_PAYLOAD) }
        private fun fail(error: PayloadError): Nothing = throw PayloadException(error)
    }
}

@Serializable
internal class ConnectionWire(
    val schemaVersion: Int, val pharmacyId: String, val pharmacyName: String,
    val botToken: String, val groupId: Long, val snapshotKey: String, val keyVersion: Int,
    val createdAt: Instant, val createdBy: EditorRef,
)

@Serializable
internal class SnapshotWire(
    val compatibility: SnapshotCompatibility, val revision: Long, val publishId: String,
    val parentPublishId: String?, val basedOnRevision: Long, val publishedAt: Instant,
    val publishedBy: EditorRef, val pharmacy: Pharmacy, val categories: List<Category>,
    val forms: List<MedicationForm>, val sections: List<Section>, val medications: List<Medication>,
    val availability: List<Availability>, val tombstones: List<Tombstone>,
)

private fun availablePayloadWorkspace(): Long {
    val runtime = Runtime.getRuntime()
    return ((runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) - 16L * 1024 * 1024)
        .coerceAtLeast(0) / 4) * 3
}

/** Complete supported wire payload as the confirmed base, without connection secrets. */
internal fun SnapshotCandidate.Supported.confirmedJson(): String = confirmedSnapshotJson.encodeToString(SnapshotWire(
    compatibility, revision, publishId, parentPublishId, basedOnRevision, publishedAt, publishedBy,
    requireNotNull(catalog.pharmacy), catalog.categories, catalog.forms, catalog.sections,
    catalog.medications, catalog.availability, catalog.tombstones,
))

private val confirmedSnapshotJson = Json { encodeDefaults = true }
