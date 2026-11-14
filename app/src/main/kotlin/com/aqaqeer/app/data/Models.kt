@file:kotlinx.serialization.UseSerializers(InstantSerializer::class, DateSerializer::class)

package com.aqaqeer.app.data

import androidx.room.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
object DateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable enum class AvailabilityStatus { AVAILABLE, LOW, OUT, UNKNOWN }
@Serializable data class EditorRef(val id: String, val name: String)
@Serializable data class PinVerifier(val salt: String, val iterations: Int, val hash: String)

@Serializable @Entity(tableName = "pharmacy")
data class Pharmacy(
    @PrimaryKey val pharmacyId: String,
    val name: String,
    val timezone: String,
    val adminPinVerifier: PinVerifier,
    val schemaVersion: Int = 1,
    val minVersionCode: Int = 1,
    val keyVersion: Int = 1,
)
@Serializable @Entity(tableName = "categories", indices = [Index(value = ["orderIndex"])])
data class Category(@PrimaryKey val id: String, val nameAr: String, val nameEn: String? = null,
                    val orderIndex: Int = 0, val isSystem: Boolean = false)
@Serializable @Entity(tableName = "forms", indices = [Index(value = ["orderIndex"])])
data class MedicationForm(@PrimaryKey val id: String, val nameAr: String, val nameEn: String? = null,
                          val orderIndex: Int = 0, val isSystem: Boolean = false)
@Serializable @Entity(tableName = "sections", indices = [Index(value = ["orderIndex"])])
data class Section(@PrimaryKey val id: String, val nameAr: String, val nameEn: String? = null,
                   val orderIndex: Int = 0, val isActive: Boolean = true)

@Serializable
@Entity(tableName = "medications", foreignKeys = [
    ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = MedicationForm::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("categoryId"), Index("formId")])
data class Medication(
    @PrimaryKey val id: String,
    val nameEn: String? = null,
    val strength: String,
    val formId: String,
    val categoryId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: EditorRef,
    val nameAr: String? = null,
    val genericAr: String? = null,
    val genericEn: String? = null,
    val brand: String? = null,
    val note: String? = null,
    val expiryDate: LocalDate? = null,
    val expirySoonThresholdDays: Int = 60,
)

@Serializable
@Entity(tableName = "availability", primaryKeys = ["medicationId", "sectionId"], foreignKeys = [
    ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Section::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("sectionId")])
data class Availability(val medicationId: String, val sectionId: String,
                        val status: AvailabilityStatus = AvailabilityStatus.UNKNOWN,
                        val updatedAt: Instant, val updatedBy: EditorRef)

@Serializable @Entity(tableName = "tombstones", primaryKeys = ["entityType", "entityId", "operationId"])
data class Tombstone(val entityType: String, val entityId: String, val deletedAt: Instant, val operationId: String)

// Local records are deliberately separate from the shared catalog payload.
@Entity(tableName = "history", indices = [Index("entityId"), Index("operationId")])
data class HistoryEntry(
    @PrimaryKey val id: String, val operationId: String?, val entityType: String, val entityId: String,
    val entityName: String, val field: String, val oldValue: String?, val newValue: String?,
    val editorId: String?, val editorName: String?, val editedAt: Instant?,
    val observedAt: Instant?, val publishedRevision: Long?,
)
@Entity(tableName = "device_identity")
data class DeviceIdentity(@PrimaryKey val id: String, val displayName: String?, val adminMode: Boolean,
                          val createdAt: Instant, val lastSeenAt: Instant)
@Entity(tableName = "snapshot_state")
data class SnapshotState(
    @PrimaryKey val slot: Int = 1,
    // Exact ISO Instant text: publication identities must not lose sub-millisecond precision.
    val publishedAt: String? = null,
    val confirmedPayloadJson: String? = null, val revision: Long? = null, val publishId: String? = null,
    val pinnedMessageId: Long? = null, val documentFileId: String? = null,
    val documentFileUniqueId: String? = null, val lastSuccessfulSyncAt: Instant? = null,
    val requiredSchemaVersion: Int? = null, val requiredMinVersionCode: Int? = null,
)
@Entity(tableName = "pending_operations", indices = [Index("transactionId"), Index("targetId")])
data class PendingOperation(
    @PrimaryKey val id: String, val transactionId: String, val entityType: String, val targetId: String,
    val field: String, val baseValueJson: String, val intendedValueJson: String,
    val authorId: String, val authorName: String, val authoredAt: Instant, val basePublishId: String?,
)
@Entity(tableName = "publish_attempts")
data class PublishAttempt(
    @PrimaryKey val publishId: String, val basePayloadJson: String?, val candidatePayloadJson: String,
    val includedOperationIdsJson: String, val transportPhase: String,
    val uploadedFileId: String? = null, val uploadedMessageId: Long? = null,
)
@Entity(tableName = "installation_state")
data class InstallationState(@PrimaryKey val slot: Int = 1, val seedVersion: Int, val isDemo: Boolean)

// This value belongs in SecureStore, never DataStore or a shared snapshot.
data class ConnectionSecrets(val botToken: String, val groupId: Long, val snapshotKey: ByteArray, val keyVersion: Int)

@Serializable
data class CatalogSnapshot(
    val pharmacy: Pharmacy?, val categories: List<Category>, val forms: List<MedicationForm>,
    val sections: List<Section>, val medications: List<Medication> = emptyList(),
    val availability: List<Availability> = emptyList(), val tombstones: List<Tombstone> = emptyList(),
) {
    // Delegated caches have no backing fields and are excluded by serialization automatically.
    private val availabilityIndex by lazy { availability.associateBy { it.medicationId to it.sectionId } }
    val formsById by lazy { forms.associateBy { it.id } }
    val categoriesById by lazy { categories.associateBy { it.id } }
    val outCounts by lazy { availability.filter { it.status == AvailabilityStatus.OUT }.groupingBy { it.sectionId }.eachCount() }
    fun availability(medicationId: String, sectionId: String): Availability? = availabilityIndex[medicationId to sectionId]
    fun status(medicationId: String, sectionId: String): AvailabilityStatus =
        availability(medicationId, sectionId)?.status ?: AvailabilityStatus.UNKNOWN
}

/** Device-local lifecycle; never serialized into shared data. */
@Entity(tableName = "local_control")
data class LocalControl(@PrimaryKey val slot: Int = 1, val lifecycle: String = "UNCONFIGURED",
    val failedPins: Int = 0, val retryPinAt: Long = 0, val reconnectPending: Boolean = false, val leaving: Boolean = false,
    val observedPinVerifier: String? = null, val authorizedPinVerifier: String? = null,
    @ColumnInfo(defaultValue = "0") val restoreConnectionConfirmed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val restorePaused: Boolean = false, val restoreActivationId: String? = null,
    val firstAdminAt: Long? = null, val lastBackupAt: Long? = null, val backupDismissedAt: Long? = null)
