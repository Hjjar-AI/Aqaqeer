package com.aqaqeer.app.security.files

import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.StorageRecoveryRequired
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Known authenticated requirements, independent of a pending connection's lifetime. */
data class SnapshotRequirements(val schemaVersion: Int, val minVersionCode: Int) {
    fun requiresUpdate(appVersionCode: Int): Boolean =
        !SnapshotCompatibilityPolicy.supports(schemaVersion, minVersionCode, appVersionCode)
}

/** All callers share this injected instance; an older observation cannot lower known requirements. */
@Singleton
class SnapshotRequirementsStore @Inject constructor(private val secureStore: SecureStore) {
    @Synchronized fun read(pharmacyId: String): SnapshotRequirements? {
        return com.aqaqeer.app.security.ProtectedReadCache.read(secureStore, entry(pharmacyId)) { bytes ->
            try {
                require(bytes.size == 12)
                val buffer = ByteBuffer.wrap(bytes)
                require(buffer.int == MAGIC)
                val schema = buffer.int; val minimum = buffer.int
                require(schema > 0 && minimum > 0)
                SnapshotRequirements(schema, minimum)
            } catch (_: Exception) { throw StorageRecoveryRequired() }
        }
    }

    /** Call only after complete same-pharmacy authentication, caption agreement, and pin read-back. */
    @Synchronized internal fun observe(pharmacyId: String, compatibility: SnapshotCompatibility): SnapshotRequirements {
        require(compatibility.schemaVersion > 0 && compatibility.minVersionCode > 0)
        val existing = read(pharmacyId)
        val requirements = SnapshotRequirements(maxOf(existing?.schemaVersion ?: 1, compatibility.schemaVersion),
            maxOf(existing?.minVersionCode ?: 1, compatibility.minVersionCode))
        if (requirements != existing) {
            val bytes = ByteBuffer.allocate(12).putInt(MAGIC).putInt(requirements.schemaVersion)
                .putInt(requirements.minVersionCode).array()
            try { secureStore.write(entry(pharmacyId), bytes) }
            finally { bytes.fill(0); com.aqaqeer.app.security.ProtectedReadCache.invalidate(secureStore, entry(pharmacyId)) }
        }
        return requirements
    }

    private fun entry(pharmacyId: String): String {
        require(pharmacyId.length == 36 && UUID.fromString(pharmacyId).toString() == pharmacyId)
        return "snapshot-requirements-$pharmacyId"
    }
    companion object { private const val MAGIC = 0x41515231 }
}
