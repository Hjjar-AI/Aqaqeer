package com.aqaqeer.app.fixtures

import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Public fixtures included only in JVM/instrumentation source sets, never in the application. */
object M3Fixtures {
    const val pharmacyId = "00000000-0000-4000-8000-000000000abc"
    const val otherPharmacyId = "00000000-0000-4000-8000-000000000def"
    const val publishId = "00000000-0000-4000-8000-000000000123"
    const val token = "123456:public_test_token"
    const val groupId = -1001234567890L
    val key get() = ByteArray(32) { it.toByte() }
    val time: Instant = Instant.parse("2026-11-09T12:00:00Z")
    val editor = EditorRef("00000000-0000-4000-8000-000000000456", "محرر الاختبار")
    val json = Json { encodeDefaults = true }
    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun catalog(): CatalogSnapshot {
        val seed = CatalogSeeds.unconfigured()
        val pharmacy = Pharmacy(pharmacyId, "صيدلية الاختبار", "Asia/Damascus",
            PinVerifier(b64(ByteArray(16)), 200_000, b64(ByteArray(32))))
        val med = Medication("00000000-0000-4000-8000-000000000789", "Fixture medication", "10 mg",
            CatalogSeeds.OTHER_FORM_ID, CatalogSeeds.UNCATEGORIZED_ID, time, time, editor)
        return seed.copy(pharmacy = pharmacy, medications = listOf(med), availability = listOf(
            Availability(med.id, seed.sections.single().id, AvailabilityStatus.AVAILABLE, time, editor)))
    }
    fun snapshot(catalog: CatalogSnapshot = catalog(), schema: Int = 1, minimum: Int = 1): JsonObject =
        JsonObject(json.parseToJsonElement(json.encodeToString(catalog)).jsonObject + mapOf(
            "compatibility" to buildJsonObject { put("schemaVersion", schema); put("minVersionCode", minimum); put("keyVersion", 1) },
            "revision" to JsonPrimitive(1), "publishId" to JsonPrimitive(publishId),
            "parentPublishId" to JsonNull, "basedOnRevision" to JsonPrimitive(0),
            "publishedAt" to JsonPrimitive(time.toString()), "publishedBy" to json.parseToJsonElement(json.encodeToString(editor))))
    fun connection(): JsonObject = buildJsonObject {
        put("schemaVersion", 1); put("pharmacyId", pharmacyId); put("pharmacyName", "صيدلية الاختبار")
        put("botToken", token); put("groupId", groupId); put("snapshotKey", b64(key)); put("keyVersion", 1)
        put("createdAt", time.toString()); put("createdBy", json.parseToJsonElement(json.encodeToString(editor)))
    }
    fun candidate(): ConnectionCandidate = ConnectionCandidate(pharmacyId, "صيدلية الاختبار", time, editor, token, groupId, key, 1)
    fun snapshotEnvelope(payload: JsonObject = snapshot()): ByteArray = encrypt(payload.toString(), EnvelopeDomain.SNAPSHOT)
    fun connectionEnvelope(payload: JsonObject = connection()): ByteArray = encrypt(payload.toString(), EnvelopeDomain.CONNECTION)
    private fun encrypt(payload: String, domain: EnvelopeDomain): ByteArray {
        // Fixed test-only nonce/salt/credentials are public and excluded from application artifacts.
        val nonce = ByteArray(12) { (64 + it).toByte() }; val salt = ByteArray(16)
        val derived = if (domain == EnvelopeDomain.SNAPSHOT) key else {
            val spec = PBEKeySpec("123456".toCharArray(), salt, 200_000, 256)
            try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(domain.marker.toByteArray() + (if (domain == EnvelopeDomain.SNAPSHOT) byteArrayOf() else salt) + nonce)
            val encrypted = cipher.doFinal(payload.toByteArray())
            return buildJsonObject {
                put("v", 1); put("nonce", b64(nonce))
                if (domain != EnvelopeDomain.SNAPSHOT) { put("kdf", "PBKDF2-HMAC-SHA256"); put("iter", 200_000); put("salt", b64(salt)) }
                put("ct", b64(encrypted.copyOfRange(0, encrypted.size - 16)))
                put("tag", b64(encrypted.copyOfRange(encrypted.size - 16, encrypted.size)))
            }.toString().toByteArray()
        } finally { derived.fill(0) }
    }
}

class MemorySecureStore : SecureStore {
    val entries = mutableMapOf<String, ByteArray>()
    var failWrites = false
    var writes = 0
    var lastRead: ByteArray? = null
    override fun read(name: String): ByteArray? = entries[name]?.copyOf()?.also { lastRead = it }
    override fun write(name: String, value: ByteArray) {
        if (failWrites) throw StorageRecoveryRequired()
        entries[name] = value.copyOf(); writes++
    }
}
