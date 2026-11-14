package com.aqaqeer.app.maintenance

import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.files.SnapshotCandidate
import com.aqaqeer.app.security.files.SnapshotWire
import com.aqaqeer.app.security.files.confirmedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable internal data class PinChangeJournal(val id: String, val pharmacyId: String, val deviceId: String,
    val previous: PinVerifier, val next: PinVerifier, val operationId: String? = null)

internal fun bindPinChange(store: MaintenanceStore, intent: String?, device: DeviceIdentity, op: PendingOperation) {
    val record = requireNotNull(store.decode<PinChangeJournal>("pin-change"))
    adminCheck(record.id == intent && record.deviceId == device.id && record.pharmacyId == op.targetId)
    adminCheck(op.baseValueJson == editJson.encodeToString(record.previous) && op.intendedValueJson == editJson.encodeToString(record.next))
    store.encode("pin-change", record.copy(operationId = op.id))
}

internal suspend fun authorizeObservedPin(dao: CatalogDao, secure: SecureStore, incoming: SnapshotCandidate.Supported): Boolean {
    val pharmacy = requireNotNull(incoming.catalog.pharmacy)
    val verifier = Json.encodeToString(pharmacy.adminPinVerifier)
    val control = requireNotNull(dao.control()); val device = requireNotNull(dao.deviceIdentity())
    val authorized = control.authorizedPinVerifier ?: dao.pharmacy()?.let { Json.encodeToString(it.adminPinVerifier) }
    val record = MaintenanceStore(secure).decode<PinChangeJournal>("pin-change")
    val own = device.adminMode && record != null && record.deviceId == device.id && record.pharmacyId == pharmacy.pharmacyId &&
        record.next == pharmacy.adminPinVerifier && record.operationId != null && dao.attempts().any { attempt ->
            attempt.publishId == incoming.publishId && record.operationId in Json.decodeFromString<List<String>>(attempt.includedOperationIdsJson) &&
                Json.decodeFromString<SnapshotWire>(attempt.candidatePayloadJson).supported().confirmedJson() == incoming.confirmedJson()
        }
    val valid = device.adminMode && (authorized == verifier || own)
    dao.control(control.copy(observedPinVerifier = verifier, authorizedPinVerifier = if (own) verifier else control.authorizedPinVerifier))
    if (!valid && device.adminMode) dao.updateDeviceIdentity(device.copy(adminMode = false))
    return valid
}

internal fun guardPinOperations(store: MaintenanceStore, device: DeviceIdentity, operations: List<PendingOperation>, live: PinVerifier) {
    operations.filter { it.entityType == "pharmacy" && it.field == "adminPinVerifier" }.forEach { op ->
        val record = requireNotNull(store.decode<PinChangeJournal>("pin-change"))
        adminCheck(record.deviceId == device.id && record.pharmacyId == op.targetId && record.operationId == op.id && record.previous == live &&
            op.intendedValueJson == editJson.encodeToString(record.next)) { "أعد تغيير الرمز باستخدام رمز الإدارة الحالي" }
    }
}

class PinChanges @Inject constructor(private val admin: AdminRepository, private val store: MaintenanceStore) {
    suspend fun change(current: CharArray, next: CharArray, confirmation: CharArray) {
        try { withContext(Dispatchers.IO) {
            val a = PinSecurity.digits(next); val b = PinSecurity.digits(confirmation)
            try { adminRequire(a.contentEquals(b)) } finally { a.fill('\u0000'); b.fill('\u0000') }
            val verifier = withContext(Dispatchers.Default) { PinSecurity.create(next) }
            admin.sharingGate.withLock {
                val baseline = admin.transaction { dao ->
                    val device = admin.guard(dao)
                    adminCheck(dao.pending().none { it.entityType == "pharmacy" && it.field == "adminPinVerifier" })
                    adminCheck(dao.attemptCount() == 0)
                    val pharmacy = requireNotNull(dao.pharmacy())
                    val observed = dao.control()?.observedPinVerifier?.let { Json.decodeFromString<PinVerifier>(it) }
                    adminCheck(observed == null || observed == pharmacy.adminPinVerifier) { "صالح رمز الإدارة المنشور قبل تغييره" }
                    pharmacy to device
                }
                admin.verifyCurrentPin(baseline.first, current)
                val record = PinChangeJournal(newId(), baseline.first.pharmacyId, baseline.second.id, baseline.first.adminPinVerifier, verifier)
                store.encode("pin-change", record)
                admin.edit(pinIntent = record.id) { catalog, author, _, _ ->
                    adminCheck(catalog.pharmacy == baseline.first && author.id == baseline.second.id)
                    catalog.copy(pharmacy = baseline.first.copy(adminPinVerifier = verifier))
                }
            }
        } } finally { current.fill('\u0000'); next.fill('\u0000'); confirmation.fill('\u0000') }
    }
}
