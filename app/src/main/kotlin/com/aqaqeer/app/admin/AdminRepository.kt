package com.aqaqeer.app.admin

import androidx.room.withTransaction
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

internal suspend fun CatalogDao.catalog() = CatalogSnapshot(pharmacy(), categories(), forms(), sections(),
    medications(), availability(), tombstones())
internal suspend fun CatalogDao.replace(catalog: CatalogSnapshot) {
    CatalogRules.validate(catalog)
    clearAvailability(); clearMedications(); clearSections(); clearCategories(); clearForms(); clearTombstones()
    insertCategories(catalog.categories); insertForms(catalog.forms); insertSections(catalog.sections)
    catalog.pharmacy?.let { if (pharmacy() == null) insertPharmacy(it) else updatePharmacy(it) }
    insertMedications(catalog.medications); insertAvailability(catalog.availability); insertTombstones(catalog.tombstones)
}

/** Apply only changed rows. Parent updates precede child reassignment; removals follow it. */
internal suspend fun CatalogDao.applyChanges(before: CatalogSnapshot, after: CatalogSnapshot) {
    CatalogRules.validate(after)
    suspend fun <T, K> write(old: List<T>, next: List<T>, key: (T) -> K,
        insert: suspend (List<T>) -> Unit, update: suspend (List<T>) -> Unit) {
        val previous = old.associateBy(key)
        val added = next.filter { key(it) !in previous }
        val changed = next.filter { key(it) in previous && previous[key(it)] != it }
        if (added.isNotEmpty()) insert(added)
        if (changed.isNotEmpty()) update(changed)
    }
    suspend fun <T, K> remove(old: List<T>, next: List<T>, key: (T) -> K, delete: suspend (List<T>) -> Unit) {
        val retained = next.map(key).toSet()
        old.filter { key(it) !in retained }.takeIf { it.isNotEmpty() }?.let { delete(it) }
    }
    if (before.pharmacy != after.pharmacy) after.pharmacy?.let { if (before.pharmacy == null) insertPharmacy(it) else updatePharmacy(it) }
    write(before.categories, after.categories, { it.id }, ::insertCategories, ::updateCategories)
    write(before.forms, after.forms, { it.id }, ::insertForms, ::updateForms)
    write(before.sections, after.sections, { it.id }, ::insertSections, ::updateSections)
    remove(before.availability, after.availability, { it.medicationId to it.sectionId }, ::deleteAvailability)
    write(before.medications, after.medications, { it.id }, ::insertMedications, ::updateMedications)
    write(before.availability, after.availability, { it.medicationId to it.sectionId }, ::insertAvailability, ::updateAvailability)
    remove(before.medications, after.medications, { it.id }, ::deleteMedications)
    remove(before.sections, after.sections, { it.id }, ::deleteSections)
    remove(before.categories, after.categories, { it.id }, ::deleteCategories)
    remove(before.forms, after.forms, { it.id }, ::deleteForms)
    remove(before.tombstones, after.tombstones, { Triple(it.entityType, it.entityId, it.operationId) }, ::deleteTombstones)
    write(before.tombstones, after.tombstones, { Triple(it.entityType, it.entityId, it.operationId) }, ::insertTombstones, ::updateTombstones)
}

@Singleton
class AdminRepository @Inject constructor(private val factory: DatabaseFactory,
    private val requirements: SnapshotRequirementsStore, private val secure: SecureStore, private val maintenanceStage: MaintenanceStage,
    private val pinThrottle: PinThrottle, private val preferences: DisplayPreferences,
    private val clock: Clock = Clock.systemUTC()) {
    internal val sharingGate = Mutex()
    internal suspend fun <T> transaction(block: suspend (CatalogDao) -> T): T = withContext(Dispatchers.IO) {
        val lease = factory.acquire()
        val db = lease.database
        try { db.withTransaction { block(db.catalogDao()) } } finally { lease.close() }
    }
    internal suspend fun guard(dao: CatalogDao, allowMaintenance: Boolean = false): DeviceIdentity {
        if (!allowMaintenance) adminCheck(MaintenanceStore(secure).read() == null) { "أكمل مراجعة صيانة الاتصال أولاً" }
        adminCheck(!BuildConfig.IS_DEMO) { "النسخة التجريبية للقراءة" }
        adminCheck(dao.control()?.leaving != true && dao.control()?.restoreActivationId == null)
        val pharmacy = requireNotNull(dao.pharmacy())
        adminCheck(requirements.read(pharmacy.pharmacyId)?.requiresUpdate(BuildConfig.VERSION_CODE) != true) { "يلزم تحديث التطبيق" }
        return requireNotNull(dao.deviceIdentity()).also { adminCheck(it.adminMode) { "يلزم تفويض الإدارة" } }
    }
    suspend fun create(name: String, timezone: String, editor: String, section: String, pin: CharArray, confirm: CharArray) {
        try {
            val normalized = PinSecurity.digits(pin)
            try {
                val confirmation = PinSecurity.digits(confirm)
                try { adminRequire(normalized.contentEquals(confirmation)) { "رمزا الإدارة غير متطابقين" } }
                finally { confirmation.fill('\u0000') }
            } finally { normalized.fill('\u0000') }
            adminRequire(name.trim().isNotEmpty() && editor.trim().isNotEmpty() && section.trim().isNotEmpty()) { "أكمل الحقول المطلوبة" }
            ZoneId.of(timezone)
            val verifier = withContext(Dispatchers.Default) { PinSecurity.create(pin) }
            transaction { dao ->
                adminCheck(!BuildConfig.IS_DEMO && dao.pharmacy() == null && dao.pendingCount() == 0 && dao.attemptCount() == 0)
                adminCheck(PendingConnectionStore(secure).info() == null) { "ألغِ ملف الاتصال المعلّق أولاً" }
                val now = clock.instant()
                val p = Pharmacy(newId(), name.trim(), timezone, verifier)
                val catalog = CatalogSeeds.unconfigured().copy(pharmacy = p,
                    sections = listOf(Section(CatalogSeeds.STARTER_SECTION_ID, section.trim())))
                dao.replace(catalog)
                val device = requireNotNull(dao.deviceIdentity())
                dao.updateDeviceIdentity(device.copy(displayName = editor.trim(), adminMode = true, lastSeenAt = now))
                dao.control(LocalControl(lifecycle = "LOCAL_DRAFT", observedPinVerifier = Json.encodeToString(verifier), authorizedPinVerifier = Json.encodeToString(verifier), firstAdminAt = clock.millis()))
                dao.updateSnapshotState(SnapshotState())
            }
        } finally { pin.fill('\u0000'); confirm.fill('\u0000') }
    }
    suspend fun promote(pin: CharArray, name: String) {
        try {
            adminRequire(name.trim().isNotEmpty()) { "أدخل اسم المحرر" }
            // Failure count commits even when authorization fails.
            val authorized = transaction { dao ->
                adminCheck(!BuildConfig.IS_DEMO)
                val c = requireNotNull(dao.control()); val p = requireNotNull(dao.pharmacy())
                adminCheck(c.restorePaused || dao.snapshotState()?.confirmedPayloadJson != null) { "حدّث بيانات الصيدلية أولاً" }
                val now = clock.millis()
                val throttle = pinThrottle.ready(p.pharmacyId, c.failedPins)
                val verifier = c.observedPinVerifier?.let { Json.decodeFromString<PinVerifier>(it) } ?: p.adminPinVerifier
                val valid = runCatching { PinSecurity.matches(pin, verifier) }.getOrDefault(false)
                pinThrottle.record(throttle, valid)
                if (valid) {
                    dao.control(c.copy(failedPins = 0, retryPinAt = 0, authorizedPinVerifier = Json.encodeToString(verifier), firstAdminAt = c.firstAdminAt ?: now))
                    val d = requireNotNull(dao.deviceIdentity())
                    dao.updateDeviceIdentity(d.copy(displayName = name.trim(), adminMode = true))
                } else {
                    val failures = (throttle.failures + 1).coerceAtMost(30)
                    dao.control(c.copy(failedPins = failures, retryPinAt = 0))
                }
                valid
            }
            adminCheck(authorized) { "رمز الإدارة غير صحيح" }
        } finally { pin.fill('\u0000') }
    }
    internal suspend fun verifyCurrentPin(expected: Pharmacy, pin: CharArray) {
        val valid = transaction { dao ->
            guard(dao)
            adminCheck(dao.pharmacy() == expected) { "تغيرت بيانات الصيدلية؛ أعد المحاولة" }
            val throttle = pinThrottle.ready(expected.pharmacyId, dao.control()?.failedPins ?: 0)
            val matches = runCatching { PinSecurity.matches(pin, expected.adminPinVerifier) }.getOrDefault(false)
            pinThrottle.record(throttle, matches)
            dao.control(requireNotNull(dao.control()).copy(failedPins = if (matches) 0 else (throttle.failures + 1).coerceAtMost(30), retryPinAt = 0))
            matches
        }
        adminCheck(valid) { "رمز الإدارة غير صحيح" }
    }
    internal suspend fun edit(pinIntent: String? = null, change: (CatalogSnapshot, EditorRef, Instant, String) -> CatalogSnapshot) = transaction { dao ->
        editInTransaction(dao, pinIntent, change)
    }
    internal suspend fun editInTransaction(dao: CatalogDao, pinIntent: String? = null,
        change: (CatalogSnapshot, EditorRef, Instant, String) -> CatalogSnapshot) {
        val d = guard(dao); val author = EditorRef(d.id, requireNotNull(d.displayName)); val now = clock.instant(); val tx = newId()
        val before = dao.catalog(); var next = change(before, author, now, tx)
        CatalogRules.validate(next)
        val state = requireNotNull(dao.snapshotState())
        val changes = CatalogEdits.diff(before, next, author, now, state.publishId, tx)
        if (changes.isEmpty()) return
        changes.filter { it.entityType == "pharmacy" && it.field == "adminPinVerifier" }.forEach { op ->
            bindPinChange(MaintenanceStore(secure), pinIntent, d, op)
        }
        val attempts = dao.attempts().flatMap { Json.decodeFromString<List<String>>(it.includedOperationIdsJson) }.toSet()
        val oldPending = dao.pending()
        for (op in changes) {
            val prior = oldPending.lastOrNull { it.entityType == op.entityType && it.targetId == op.targetId && it.field == op.field && it.id !in attempts }
            if (prior != null && op.field != "@entity") {
                dao.retargetHistory(listOf(prior.id), op.id)
                dao.deletePending(listOf(prior.id))
                if (prior.baseValueJson != op.intendedValueJson) dao.putPending(listOf(op.copy(baseValueJson = prior.baseValueJson, basePublishId = prior.basePublishId)))
            } else dao.putPending(listOf(op))
        }
        dao.insertHistory(changes.map { op -> HistoryEntry(newId(), op.id, op.entityType, op.targetId,
            next.historyName(op.entityType, op.targetId) ?: before.historyName(op.entityType, op.targetId) ?: op.entityType, op.field,
            op.baseValueJson, op.intendedValueJson, d.id, d.displayName, now, null, null) })
        // Fold edits to a not-yet-shared addition into its entity operation. A create/delete
        // round trip leaves history but no net operation. Never cross an in-flight boundary.
        val pending = dao.pending()
        val additions = pending.filter { it.field == "@entity" && it.baseValueJson == "null" && it.id !in attempts }
        val rows = if (additions.isEmpty()) emptyMap() else CatalogEdits.rows(next,
            targets = additions.map { "${it.entityType}:${it.targetId}" }.toSet())
        val pendingGroups = pending.filter { it.id !in attempts }.groupBy { it.entityType to it.targetId }
        additions.forEach { addition ->
            val same = pendingGroups[addition.entityType to addition.targetId].orEmpty()
            if (same.size > 1) {
                dao.deletePending(same.map { it.id })
                val row = rows["${addition.entityType}:${addition.targetId}"]
                if (row == null) {
                    next = next.copy(tombstones = next.tombstones.filterNot { it.entityType == addition.entityType && it.entityId == addition.targetId })
                } else {
                    val latest = same.maxWith(compareBy({ it.authoredAt }, { it.id }))
                    dao.putPending(listOf(addition.copy(intendedValueJson = row.toString(), authoredAt = latest.authoredAt,
                        authorId = latest.authorId, authorName = latest.authorName)))
                    dao.retargetHistory(same.map { it.id }, addition.id)
                }
            }
        }
        val confirmed = state.confirmedPayloadJson?.let { Json.decodeFromString<SnapshotWire>(it).supported().catalog }
        if (confirmed != null) {
            val net = dao.pending()
            next = next.copy(medications = next.medications.map { med ->
                val old = confirmed.medications.firstOrNull { it.id == med.id }
                if (old != null && net.none { it.entityType == "medication" && it.targetId == med.id })
                    med.copy(updatedAt = old.updatedAt, updatedBy = old.updatedBy) else med
            }, availability = next.availability.map { a ->
                val old = confirmed.availability.firstOrNull { it.medicationId == a.medicationId && it.sectionId == a.sectionId }
                if (old != null && net.none { it.entityType == "availability" && it.targetId == "${a.medicationId}/${a.sectionId}" })
                    a.copy(updatedAt = old.updatedAt, updatedBy = old.updatedBy) else a
            })
        }
        dao.applyChanges(before, next)
    }
    suspend fun saveMedication(value: Medication, expectedPharmacy: String? = null, requireExisting: Boolean = false, confirmedDuplicates: Set<String> = emptySet()) = edit { c, author, now, _ ->
        if (expectedPharmacy != null) adminCheck(c.pharmacy?.pharmacyId == expectedPharmacy)
        val old = c.medications.firstOrNull { it.id == value.id }
        val normalized = value.copy(strength = MedicationDuplicates.strength(value.strength))
        val duplicates = MedicationDuplicates.find(normalized, MedicationDuplicates.index(c.medications)).toSet()
        adminCheck(duplicates == confirmedDuplicates) { "يوجد دواء بالاسم والتركيز والشكل نفسه؛ راجع التكرار وأكد الحفظ" }
        adminCheck(!requireExisting || old != null) { "لم يعد هذا الدواء موجوداً" }
        c.copy(medications = c.medications.filterNot { it.id == value.id } + normalized.copy(
            createdAt = old?.createdAt ?: now, updatedAt = now, updatedBy = author),
            availability = if (old != null) c.availability else c.availability + c.sections.map {
                Availability(value.id, it.id, AvailabilityStatus.AVAILABLE, now, author)
            })
    }
    suspend fun availability(medication: String, section: String, status: AvailabilityStatus) =
        bulkAvailability(setOf(medication), section, status)

    /** One section and one edit transaction; stale previews fail before any rows change. */
    suspend fun bulkAvailability(medications: Set<String>, section: String, status: AvailabilityStatus,
        expected: Map<String, AvailabilityStatus>? = null, expectedPharmacy: String? = null) = edit { c, author, now, _ ->
        if (expectedPharmacy != null) adminCheck(c.pharmacy?.pharmacyId == expectedPharmacy)
        adminRequire(medications.isNotEmpty())
        adminRequire(c.sections.any { it.id == section })
        adminRequire(medications.all { id -> c.medications.any { it.id == id } })
        if (expected != null) {
            adminRequire(expected.keys == medications)
            adminCheck(medications.all { c.status(it, section) == expected.getValue(it) })
        }
        // A missing legacy pair already displays UNKNOWN. Saving it unchanged is inert.
        val changed = medications.filter { c.status(it, section) != status }.toSet()
        c.copy(availability = c.availability.filterNot { it.sectionId == section && it.medicationId in changed } +
            changed.map { Availability(it, section, status, now, author) })
    }
    /** All requested fields commit together; compare only fields the user chose to replace. */
    suspend fun bulkMedicationChanges(medications: Set<String>, section: String, changes: BulkMedicationChanges,
        expected: Map<String, BulkMedicationExpected>, expectedPharmacy: String): AvailabilityUndo? {
        var receipt: AvailabilityUndo? = null
        edit { c, author, now, _ ->
        adminCheck(c.pharmacy?.pharmacyId == expectedPharmacy)
        adminRequire(medications.isNotEmpty() && expected.keys == medications && !changes.isEmpty)
        adminRequire(c.sections.any { it.id == section && it.isActive }) { "القسم لم يعد نشطاً" }
        adminRequire(changes.formId == null || c.forms.any { it.id == changes.formId }) { "الشكل الدوائي لم يعد موجوداً" }
        adminRequire(changes.categoryId == null || c.categories.any { it.id == changes.categoryId }) { "التصنيف لم يعد موجوداً" }
        val live = c.medications.associateBy { it.id }
        adminCheck(medications.all { it in live }) { "حُذف دواء محدد؛ أعد المعاينة" }
        adminCheck(medications.all { id ->
            val med = live.getValue(id); val before = expected.getValue(id)
            (changes.status == null || c.status(id, section) == before.status) &&
                (changes.formId == null || med.formId == before.formId) &&
                (changes.categoryId == null || med.categoryId == before.categoryId)
        }) { "تغيرت بيانات مختارة؛ أعد المعاينة" }
        val statusChanges = if (changes.status == null) emptySet() else medications.filter {
            c.status(it, section) != changes.status
        }.toSet()
        val next = c.copy(medications = c.medications.map { med ->
            if (med.id !in medications) med else {
                val form = changes.formId ?: med.formId; val category = changes.categoryId ?: med.categoryId
                if (form == med.formId && category == med.categoryId) med
                else med.copy(formId = form, categoryId = category, updatedAt = now, updatedBy = author)
            }
        }, availability = c.availability.filterNot { it.sectionId == section && it.medicationId in statusChanges } +
            statusChanges.map { Availability(it, section, requireNotNull(changes.status), now, author) })
        val changedIds = medications.filter { id ->
            val old = live.getValue(id)
            id in statusChanges || changes.formId?.let { it != old.formId } == true || changes.categoryId?.let { it != old.categoryId } == true
        }.toSet()
        if (changedIds.isNotEmpty()) receipt = AvailabilityUndo(expectedPharmacy, section, changes,
            changedIds.associateWith { id -> live.getValue(id).let { BulkMedicationExpected(c.status(id, section), it.formId, it.categoryId) } },
            changedIds.associateWith { id -> live.getValue(id).let { BulkMedicationExpected(changes.status ?: c.status(id, section),
                changes.formId ?: it.formId, changes.categoryId ?: it.categoryId) } },
            changedIds.filter { c.availability(it, section) == null }.toSet())
        next
        }
        return receipt
    }

    suspend fun undoAvailability(receipt: AvailabilityUndo) = edit { c, author, now, _ ->
        adminCheck(c.pharmacy?.pharmacyId == receipt.pharmacyId)
        adminCheck(c.sections.any { it.id == receipt.sectionId && it.isActive })
        val live = c.medications.associateBy { it.id }
        adminCheck(receipt.after.all { (id, after) -> live[id]?.let { med ->
            (receipt.fields.status == null || c.status(id, receipt.sectionId) == after.status) &&
                (receipt.fields.formId == null || med.formId == after.formId) &&
                (receipt.fields.categoryId == null || med.categoryId == after.categoryId)
        } == true }) { "تغيرت البيانات؛ تعذر التراجع بأمان" }
        c.copy(medications = c.medications.map { med ->
            val before = receipt.before[med.id]
            if (before == null || receipt.fields.formId == null && receipt.fields.categoryId == null) med
            else med.copy(formId = if (receipt.fields.formId != null) before.formId else med.formId,
                categoryId = if (receipt.fields.categoryId != null) before.categoryId else med.categoryId,
                updatedAt = now, updatedBy = author)
        }, availability = if (receipt.fields.status == null) c.availability else
            c.availability.filterNot { it.sectionId == receipt.sectionId && it.medicationId in receipt.before } +
                receipt.before.filterKeys { it !in receipt.missingBefore }.map { (id, before) ->
                    Availability(id, receipt.sectionId, before.status, now, author)
                })
    }

    suspend fun saveTaxonomy(type: String, id: String?, name: String, english: String?, active: Boolean = true) = edit { c, _, _, _ ->
        val key = id ?: newId(); val ar = name.trim(); val en = english?.trim()?.takeIf { it.isNotEmpty() }
        adminRequire(ar.isNotEmpty()) { "أدخل الاسم" }
        when (type) {
            "category" -> {
                val old = c.categories.find { it.id == key }
                adminRequire(id == null || old != null) { "لم يعد التصنيف موجوداً" }
                adminRequire(old?.isSystem != true)
                val row = old?.copy(nameAr = ar, nameEn = en) ?: Category(key, ar, en,
                    orderIndex = Math.addExact(c.categories.maxOfOrNull { it.orderIndex } ?: -1, 1))
                c.copy(categories = c.categories.filterNot { it.id == key } + row)
            }
            "form" -> {
                val old = c.forms.find { it.id == key }
                adminRequire(id == null || old != null) { "لم يعد الشكل موجوداً" }
                adminRequire(old?.isSystem != true)
                val row = old?.copy(nameAr = ar, nameEn = en) ?: MedicationForm(key, ar, en,
                    orderIndex = Math.addExact(c.forms.maxOfOrNull { it.orderIndex } ?: -1, 1))
                c.copy(forms = c.forms.filterNot { it.id == key } + row)
            }
            "section" -> {
                val old = c.sections.find { it.id == key }
                adminRequire(id == null || old != null) { "لم يعد القسم موجوداً" }
                val row = old?.copy(nameAr = ar, nameEn = en, isActive = active) ?: Section(key, ar, en,
                    orderIndex = Math.addExact(c.sections.maxOfOrNull { it.orderIndex } ?: -1, 1), isActive = active)
                c.copy(sections = c.sections.filterNot { it.id == key } + row)
            }
            else -> error("نوع غير صالح")
        }
    }

    suspend fun delete(type: String, id: String, typedName: String = "") = edit { c, author, now, tx ->
        val next = when (type) {
            "medication" -> c.copy(medications = c.medications.filterNot { it.id == id }, availability = c.availability.filterNot { it.medicationId == id })
            "category" -> { adminRequire(c.categories.none { it.id == id && it.isSystem }); c.copy(categories = c.categories.filterNot { it.id == id }, medications = c.medications.map {
                if (it.categoryId == id) it.copy(categoryId = CatalogSeeds.UNCATEGORIZED_ID, updatedAt = now, updatedBy = author) else it }) }
            "form" -> { adminRequire(c.forms.none { it.id == id && it.isSystem }); c.copy(forms = c.forms.filterNot { it.id == id }, medications = c.medications.map {
                if (it.formId == id) it.copy(formId = CatalogSeeds.OTHER_FORM_ID, updatedAt = now, updatedBy = author) else it }) }
            "section" -> { adminRequire(c.sections.single { it.id == id }.nameAr == typedName) { "اكتب اسم القسم لتأكيد الحذف" }; c.copy(sections = c.sections.filterNot { it.id == id }, availability = c.availability.filterNot { it.sectionId == id }) }
            else -> error("نوع غير صالح")
        }
        next.copy(tombstones = next.tombstones + Tombstone(type, id, now, tx))
    }
    internal suspend fun markReconnect() = transaction { dao ->
        val control = dao.control() ?: return@transaction
        if (MaintenanceStore(secure).read() == null && !control.restorePaused && dao.pendingCount() > 0 && dao.deviceIdentity()?.adminMode == true)
            dao.control(control.copy(reconnectPending = true))
    }
    internal suspend fun shouldReconnect(): Boolean = transaction { dao ->
        MaintenanceStore(secure).read() == null && dao.control()?.let { !it.restorePaused && it.restoreActivationId == null && it.reconnectPending && it.lifecycle == "SHARED" } == true &&
            dao.deviceIdentity()?.adminMode == true && dao.pharmacy()?.let {
                requirements.read(it.pharmacyId)?.requiresUpdate(BuildConfig.VERSION_CODE) != true
            } == true
    }
    internal suspend fun clearReconnectIfDone(verified: Boolean = false) = transaction { dao ->
        if (verified || (dao.pendingCount() == 0 && dao.attemptCount() == 0)) dao.control()?.let { dao.control(it.copy(reconnectPending = false)) }
    }
    suspend fun pending(): List<PendingOperation> = transaction { dao -> adminCheck(dao.deviceIdentity()?.adminMode == true); dao.pending() }
    suspend fun leave(typedName: String, confirmed: Boolean) = sharingGate.withLock {
        transaction { dao ->
            adminCheck(!BuildConfig.IS_DEMO && confirmed)
            val p = requireNotNull(dao.pharmacy()); val d = requireNotNull(dao.deviceIdentity())
            adminRequire(!d.adminMode || typedName == p.name) { "اكتب اسم الصيدلية لتأكيد المغادرة" }
            dao.control(requireNotNull(dao.control()).copy(leaving = true))
        }
        transaction { finishLeave(it, secure, maintenanceStage, preferences) }
    }
}

/** A durable user-confirmed leave checkpoint completes after interruption, including secret cleanup. */
internal suspend fun finishLeave(dao: CatalogDao, secure: SecureStore, maintenanceStage: MaintenanceStage, preferences: DisplayPreferences) {
    if (dao.control()?.leaving != true) return
    dao.pharmacy()?.pharmacyId?.let { preferences.clearSection(it) }
    for (entry in listOf("active-connection", "pending-connection", "setup-connection", "restore-candidate")) {
        PendingConnectionStore(secure, entry).clear()
    }
    maintenanceStage.discardAfterConfirmedLeave()
    MaintenanceStore(secure).clearForRestore()
    secure.write("restore-journal", byteArrayOf())
    secure.write("pin-delay", byteArrayOf())
    dao.clearAvailability(); dao.clearMedications(); dao.clearSections(); dao.clearCategories(); dao.clearForms(); dao.clearTombstones()
    dao.clearPharmacy(); dao.clearHistory(); dao.deletePending(dao.pending().map { it.id })
    dao.attempts().forEach { dao.deleteAttempt(it.publishId) }
    val seed = CatalogSeeds.unconfigured(); dao.insertCategories(seed.categories); dao.insertForms(seed.forms); dao.insertSections(seed.sections)
    dao.updateSnapshotState(SnapshotState()); dao.control(LocalControl())
    val d = requireNotNull(dao.deviceIdentity())
    dao.updateDeviceIdentity(d.copy(displayName = null, adminMode = false))
}
