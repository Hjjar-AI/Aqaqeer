package com.aqaqeer.app.admin

import com.aqaqeer.app.maintenance.*

import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.SecureStore
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

internal data class SharingStatus(val busy: Boolean = false, val message: String? = null,
    val verified: Boolean = false, val retryable: Boolean = false, val retryAfterSeconds: Int = 0,
    val conflicts: List<EditConflict> = emptyList())

@Singleton
class PharmacySharing @Inject internal constructor(private val admin: AdminRepository,
    private val secure: SecureStore, private val requirements: SnapshotRequirementsStore,
    private val reads: SnapshotReadClient, private val setup: TelegramSetupTransport,
    private val writes: SnapshotWriteTransport, @param:ApplicationContext private val context: Context) {
    private val gate = admin.sharingGate
    private val mutable = MutableStateFlow(SharingStatus())
    internal val status = mutable.asStateFlow()
    private val staged = PendingConnectionStore(secure, "setup-connection")
    private val active = PendingConnectionStore(secure, "active-connection")
    private var retryAt = 0L
    suspend fun inspectSetup(token: String, group: Long): String {
        admin.transaction { admin.guard(it); check(it.attemptCount() == 0) }
        return setup.validate(token, group).title
    }
    suspend fun discover(token: String, challenge: String): List<Pair<Long, String>> {
        admin.transaction { admin.guard(it) }
        return setup.discover(token, challenge).map { it.id to it.title }
    }
    suspend fun saveSetup(token: String, group: Long, expectedTitle: String) = gate.withLock {
        val validated = setup.validate(token, group)
        check(validated.title == expectedTitle) { "تغيرت المجموعة؛ تحقق مجدداً" }
        admin.transaction { dao ->
            val d = admin.guard(dao)
            check(dao.control()?.lifecycle == "LOCAL_DRAFT" && dao.attemptCount() == 0) { "إعداد الاتصال متاح للمسودة فقط" }
            check(validated.pin == null) { "المجموعة تحتوي رسالة مثبتة؛ اختر مجموعة مخصصة فارغة" }
            val p = requireNotNull(dao.pharmacy())
            dao.control(requireNotNull(dao.control()).copy(restoreConnectionConfirmed = true))
            if (staged.info() != null) {
                var same = false
                staged.withCandidate(staged.info()!!.id) { candidate ->
                    val secrets = candidate.copySecrets()
                    try { same = candidate.pharmacyId == p.pharmacyId && secrets.botToken == token && secrets.groupId == group }
                    finally { secrets.snapshotKey.fill(0) }
                }
                if (same) return@transaction
            }
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            ConnectionCandidate(p.pharmacyId, p.name, Instant.now(), EditorRef(d.id, d.displayName!!), token, group, key, p.keyVersion).use {
                staged.retain(it, newId())
            }
        }
    }
    internal suspend fun publish(choices: Map<String, Boolean> = emptyMap()) = gate.withLock {
        if (mutable.value.busy) return@withLock
        val remaining = retryAt - android.os.SystemClock.elapsedRealtime()
        if (remaining > 0) {
            mutable.value = SharingStatus(message = "انتظر قبل إعادة المحاولة", retryable = true,
                retryAfterSeconds = ((remaining + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            return@withLock
        }
        mutable.value = SharingStatus(busy = true)
        try {
            val published = publishLocked(choices)
            mutable.value = if (published) SharingStatus(message = "تم التحقق من النشر", verified = true)
                else SharingStatus(message = "لا توجد تعديلات للنشر")
        } catch (e: CancellationException) { throw e }
        catch (e: ReviewRequired) { mutable.value = SharingStatus(message = "راجع التعارضات ثم أعد النشر", conflicts = e.conflicts) }
        catch (e: TelegramFailure) {
            retryAt = android.os.SystemClock.elapsedRealtime() + maxOf(2, e.retryAfter) * 1000L
            mutable.value = SharingStatus(message = "تعذر إتمام النشر؛ احتُفظ بالعمل. تحقق من الاتصال وصلاحيات البوت ثم أعد المحاولة.",
                retryable = e.code == 0 || e.code == 429 || e.code >= 500, retryAfterSeconds = maxOf(2, e.retryAfter))
        } catch (_: Exception) {
            mutable.value = SharingStatus(message = "تعذر إتمام النشر؛ احتُفظ بالعمل. راجع الاتصال أو حدّث البيانات ثم أعد المحاولة.")
        } finally { mutable.value = mutable.value.copy(busy = false) }
    }
    private data class Local(val catalog: CatalogSnapshot, val state: SnapshotState, val ops: List<PendingOperation>,
        val attempt: PublishAttempt?, val lifecycle: String, val editor: EditorRef)
    private suspend fun local() = admin.transaction { dao ->
        val d = admin.guard(dao)
        Local(dao.catalog(), requireNotNull(dao.snapshotState()), dao.pending(), dao.attempts().singleOrNull(),
            requireNotNull(dao.control()).lifecycle, EditorRef(d.id, d.displayName!!))
    }
    private suspend fun publishLocked(choices: Map<String, Boolean>): Boolean {
        admin.transaction { check(it.control()?.restorePaused != true) { "راجع النسخة المستعادة أولاً" } }
        val initial = local()
        if (initial.lifecycle == "SHARED" && initial.ops.isEmpty() && initial.attempt == null) {
            admin.clearReconnectIfDone()
            return false
        }
        val store = if (initial.lifecycle == "LOCAL_DRAFT") staged else active
        val info = store.info() ?: error("أكمل إعداد تيليغرام أولاً")
        check(info.pharmacyId == initial.catalog.pharmacy!!.pharmacyId)
        store.withCandidate(info.id) { connection ->
            val secrets = connection.copySecrets()
            try {
                val group = setup.validate(secrets.botToken, secrets.groupId)
                var incoming: VerifiedSnapshotRead? = null
                if (group.pin != null) incoming = reads.read(connection, BuildConfig.VERSION_CODE)
                var remote = incoming?.let { read ->
                    val known = withContext(NonCancellable) { requirements.observe(connection.pharmacyId, read.snapshot.compatibility) }
                    check(!known.requiresUpdate(BuildConfig.VERSION_CODE))
                    read.snapshot as? SnapshotCandidate.Supported ?: error("يلزم تحديث التطبيق")
                }
                val observedRemote = remote
                if (observedRemote != null) {
                    val valid = admin.transaction { dao -> authorizeObservedPin(dao, secure, observedRemote) }
                    check(valid) { "تغير رمز الإدارة؛ أعد التفويض" }
                }
                if (initial.attempt != null && observedRemote?.publishId == initial.attempt.publishId) {
                    val expected = Json.decodeFromString<SnapshotWire>(initial.attempt.candidatePayloadJson).supported()
                    check(expected.confirmedJson() == observedRemote.confirmedJson())
                    acknowledge(initial.attempt, requireNotNull(incoming), connection, info.id, choices)
                    return@withCandidate
                }
                if (observedRemote != null) admin.transaction { dao ->
                    guardPinOperations(MaintenanceStore(secure), admin.guard(dao), initial.ops, observedRemote.catalog.pharmacy!!.adminPinVerifier)
                }
                val base = initial.state.confirmedPayloadJson?.let { Json.decodeFromString<SnapshotWire>(it).supported() }
                if (initial.lifecycle == "LOCAL_DRAFT") check(remote == null) { "المجموعة تحتوي صيدلية أخرى" }
                else if (remote == null) {
                    val retained = requireNotNull(base)
                    val key = "missing:${retained.publishId}"
                    if (choices[key] != true) throw ReviewRequired(listOf(EditConflict(key, "النسخة المشتركة مفقودة", "استرداد صريح",
                        "${retained.publishId} — ${retained.publishedAt}",
                        "إعادة نشر البيانات المحتفظ بها. قد تكون هناك تغييرات بعيدة غير مرئية لا يمكن استعادتها.", "لا توجد رسالة مثبتة")))
                    remote = retained
                }
                val live = remote
                if (live != null && base != null) {
                    val linear = runCatching { publicationDecision(base, live) }.isSuccess
                    if (!linear) {
                        val key = "recovery:${base.publishId}:${live.publishId}"
                        admin.transaction { dao ->
                            dao.insertHistory(listOf(RemoteObservationHistory.recovery(connection.pharmacyId, base.publishId,
                                live.publishId, live.confirmedJson(), (incoming?.observedAt ?: Instant.now()), live.revision)))
                        }
                        if (choices[key] != true) throw ReviewRequired(listOf(EditConflict(key, "استرداد النسخة المشتركة", "سلسلة النشر",
                            base.publishId, "قبول النسخة بعد المراجعة وإعادة دمج تعديلاتي", live.publishId)))
                    }
                }
                var attempt = initial.attempt
                val parentMatches = attempt?.let {
                    val candidate = Json.decodeFromString<SnapshotWire>(it.candidatePayloadJson)
                    candidate.parentPublishId == live?.publishId && candidate.basedOnRevision == (live?.revision ?: 0)
                } == true
                if (attempt != null && !parentMatches) {
                    val key = "uncertain:${attempt.publishId}:${live?.publishId}"
                    if (choices[key] != true) throw ReviewRequired(listOf(EditConflict(key, "نتيجة نشر غير مؤكدة", "مصالحة المحاولة",
                        attempt.publishId, "مراجعة المنشور وإعادة دمج العمل المحتفظ به", live?.publishId ?: "لا توجد نسخة")))
                    // Retain the uncertain candidate in local recovery history before preparing a replacement.
                    admin.transaction { dao ->
                        admin.guard(dao)
                        dao.insertHistory(listOf(RemoteObservationHistory.recovery(connection.pharmacyId,
                            initial.state.publishId ?: "", attempt!!.publishId, attempt!!.candidatePayloadJson, Instant.now(),
                            Json.decodeFromString<SnapshotWire>(attempt!!.candidatePayloadJson).revision)))
                    }
                    attempt = null
                }
                if (attempt == null) {
                    val merged = if (live == null) MergeResult(initial.catalog, emptyList()) else
                        CatalogEdits.merge(base!!.catalog, initial.catalog, live.catalog, initial.ops, choices)
                    if (merged.conflicts.isNotEmpty()) throw ReviewRequired(merged.conflicts)
                    check(live?.revision != Long.MAX_VALUE) { "بلغت مراجعات النشر الحد الأقصى" }
                    val candidate = SnapshotCandidate.Supported(
                        SnapshotCompatibility(1, merged.catalog.pharmacy!!.minVersionCode, connection.keyVersion),
                        (live?.revision ?: 0) + 1, newId(), live?.publishId, live?.revision ?: 0,
                        Instant.now(), initial.editor, merged.catalog)
                    attempt = PublishAttempt(candidate.publishId, live?.confirmedJson(), candidate.confirmedJson(),
                        Json.encodeToString(initial.ops.map { it.id }), "PREPARED")
                    val saved = attempt
                    admin.transaction { dao ->
                        admin.guard(dao)
                        check(dao.snapshotState() == initial.state && dao.catalog() == initial.catalog && dao.pending() == initial.ops)
                        initial.attempt?.let { dao.deleteAttempt(it.publishId) }
                        dao.attempt(saved)
                    }
                }
                var currentAttempt = requireNotNull(attempt)
                val candidate = Json.decodeFromString<SnapshotWire>(currentAttempt.candidatePayloadJson).supported()
                val caption = SnapshotCaption.encode(candidate.publication)
                if (currentAttempt.uploadedFileId == null) {
                    currentAttempt = currentAttempt.copy(transportPhase = "UPLOAD_STARTED")
                    saveAttempt(currentAttempt)
                    val bytes = EnvelopeWriter.snapshot(Json.decodeFromString(currentAttempt.candidatePayloadJson), secrets.snapshotKey)
                    val upload = try { writes.upload(secrets.botToken, secrets.groupId, bytes, caption) } finally { bytes.fill(0) }
                    currentAttempt = currentAttempt.copy(uploadedFileId = upload.fileId, uploadedMessageId = upload.messageId, transportPhase = "UPLOADED")
                    saveAttempt(currentAttempt)
                }
                // Re-read immediately before mutation. Telegram provides no conditional-write lock.
                val latest = writes.inspect(secrets.botToken, secrets.groupId)
                check(latest.pin == group.pin) { "تغيرت الرسالة المثبتة؛ أعد المراجعة" }
                val targetMessage = incoming?.document?.messageId
                currentAttempt = currentAttempt.copy(transportPhase = "WRITE_STARTED")
                saveAttempt(currentAttempt)
                if (targetMessage == null) writes.pin(secrets.botToken, secrets.groupId, requireNotNull(currentAttempt.uploadedMessageId))
                else writes.replace(secrets.botToken, secrets.groupId, targetMessage, requireNotNull(currentAttempt.uploadedFileId), caption)
                val verified = reads.read(connection, BuildConfig.VERSION_CODE)
                val published = verified.snapshot as? SnapshotCandidate.Supported ?: error("يلزم تحديث التطبيق")
                requirements.observe(connection.pharmacyId, published.compatibility)
                check(published.confirmedJson() == candidate.confirmedJson()) { "لم يتم تأكيد النشر" }
                acknowledge(currentAttempt, verified, connection, info.id, choices)
            } finally { secrets.snapshotKey.fill(0) }
        }
        return true
    }
    private suspend fun saveAttempt(attempt: PublishAttempt) = admin.transaction { dao ->
        admin.guard(dao); check(dao.attempts().any { it.publishId == attempt.publishId }); dao.attempt(attempt)
    }
    private suspend fun acknowledge(attempt: PublishAttempt, read: VerifiedSnapshotRead, connection: ConnectionCandidate, connectionId: String, choices: Map<String, Boolean>) {
        val incoming = read.snapshot as SnapshotCandidate.Supported
        val included = Json.decodeFromString<List<String>>(attempt.includedOperationIdsJson).toSet()
        admin.transaction { dao ->
            admin.guard(dao)
            check(dao.pharmacy()?.pharmacyId == connection.pharmacyId)
            check(dao.attempts().any { it.publishId == attempt.publishId })
            check(authorizeObservedPin(dao, secure, incoming))
            val remaining = dao.pending().filterNot { it.id in included }
            val candidate = Json.decodeFromString<SnapshotWire>(attempt.candidatePayloadJson).supported()
            val visible = CatalogEdits.merge(candidate.catalog, dao.catalog(), incoming.catalog, remaining, choices)
            if (visible.conflicts.isNotEmpty()) throw ReviewRequired(visible.conflicts)
            val old = dao.snapshotState()?.confirmedPayloadJson?.let { Json.decodeFromString<SnapshotWire>(it).supported() }
            val known = requirements.observe(connection.pharmacyId, incoming.compatibility)
            check(!known.requiresUpdate(BuildConfig.VERSION_CODE))
            // Write-ahead active credentials make a crash before the Room commit retryable.
            active.retain(connection, connectionId)
            dao.replace(visible.catalog)
            dao.updateSnapshotState(SnapshotState(publishedAt = incoming.publishedAt.toString(), confirmedPayloadJson = incoming.confirmedJson(), revision = incoming.revision,
                publishId = incoming.publishId, pinnedMessageId = read.document.messageId, documentFileId = read.document.fileId,
                documentFileUniqueId = read.document.fileUniqueId, lastSuccessfulSyncAt = read.observedAt,
                requiredSchemaVersion = known.schemaVersion, requiredMinVersionCode = known.minVersionCode))
            dao.control(requireNotNull(dao.control()).copy(lifecycle = "SHARED"))
            val pendingBeforeAck = dao.pending()
            dao.deletePending(included.toList()); dao.deleteAttempt(attempt.publishId)
            if (choices.isNotEmpty() && remaining.isNotEmpty()) {
                // Explicit review can reconcile an edit made while the candidate was uploading.
                // Keep its author/history, and bind the reviewed intention to the verified base.
                val device = requireNotNull(dao.deviceIdentity())
                val rebased = CatalogEdits.diff(incoming.catalog, visible.catalog,
                    EditorRef(device.id, device.displayName!!), Instant.now(), incoming.publishId).map { op ->
                    val prior = remaining.lastOrNull { it.entityType == op.entityType && it.targetId == op.targetId && it.field == op.field }
                    if (prior == null) op else op.copy(id = prior.id, transactionId = prior.transactionId,
                        authorId = prior.authorId, authorName = prior.authorName, authoredAt = prior.authoredAt)
                }
                dao.deletePending(remaining.map { it.id }); dao.putPending(rebased)
            }
            val rows = CatalogEdits.rows(incoming.catalog)
            val acceptedIds = Json.decodeFromString<List<String>>(attempt.includedOperationIdsJson).filter { id ->
                val op = pendingBeforeAck.firstOrNull { it.id == id }
                op != null && (if (op.field == "@entity") rows["${op.entityType}:${op.targetId}"]?.toString() ?: "null"
                    else rows["${op.entityType}:${op.targetId}"]?.get(op.field)?.toString() ?: "null") == op.intendedValueJson
            }
            dao.acknowledgeHistory(acceptedIds, incoming.revision)
            val remoteBase = attempt.basePayloadJson?.let { Json.decodeFromString<SnapshotWire>(it).supported() }
            if (old != null && remoteBase != null && old.publishId != remoteBase.publishId)
                dao.insertHistory(RemoteObservationHistory.changes(old.catalog, remoteBase.catalog, read.observedAt, remoteBase.revision))
            dao.insertHistory(listOf(RemoteObservationHistory.publication(old?.publishId ?: "", incoming, read.observedAt)))
        }
    }

    /** Explicit restore review only. This command never uploads or schedules publication. */
    internal suspend fun reconcileRestore(replaceLive: Boolean, typedName: String,
        choices: Map<String, Boolean> = emptyMap()) = gate.withLock {
        mutable.value = SharingStatus(busy = true)
        try {
            val initial = local()
            admin.transaction { check(it.control()?.restorePaused == true) }
            val store = if (initial.lifecycle == "LOCAL_DRAFT") staged else active
            if (initial.lifecycle == "LOCAL_DRAFT" && initial.attempt == null) {
                admin.transaction { dao ->
                    admin.guard(dao)
                    check(staged.info() == null || dao.control()?.restoreConnectionConfirmed == true) { "أكد المجموعة المحفوظة أولاً" }
                    dao.control(requireNotNull(dao.control()).copy(restorePaused = false, reconnectPending = false))
                }
                mutable.value = SharingStatus(message = "استؤنفت المسودة المحلية؛ المشاركة تحتاج إعداداً ونشراً صريحاً")
                return@withLock
            }
            val info = requireNotNull(store.info())
            admin.transaction { check(it.control()?.restoreConnectionConfirmed == true) { "أكد هوية المجموعة أولاً" } }
            store.withCandidate(info.id) { connection ->
                val secrets = connection.copySecrets()
                val group = try { setup.validate(secrets.botToken, secrets.groupId) } finally { secrets.snapshotKey.fill(0) }
                if (initial.lifecycle == "LOCAL_DRAFT" && group.pin == null) {
                    val attempt = requireNotNull(initial.attempt)
                    val key = "restore-bootstrap:${attempt.publishId}"
                    if (choices[key] != true) throw ReviewRequired(listOf(EditConflict(key, "محاولة النشر الأول", "استئناف بعد الاستعادة",
                        attempt.publishId, "المجموعة بلا رسالة مثبتة؛ الاحتفاظ بالمرشح وإعادة الرفع عند النشر اليدوي التالي", "نتيجة النقل السابقة غير مؤكدة")))
                    admin.transaction { dao ->
                        admin.guard(dao); check(dao.attempts().singleOrNull() == attempt)
                        dao.attempt(attempt.copy(transportPhase = "PREPARED", uploadedFileId = null, uploadedMessageId = null))
                        dao.control(requireNotNull(dao.control()).copy(restorePaused = false, reconnectPending = false))
                    }
                    mutable.value = SharingStatus(message = "تمت مراجعة المحاولة؛ الرفع والنشر يحتاجان تأكيداً يدوياً جديداً")
                    return@withCandidate
                }
                val read = reads.read(connection, BuildConfig.VERSION_CODE)
                val known = requirements.observe(connection.pharmacyId, read.snapshot.compatibility)
                check(!known.requiresUpdate(BuildConfig.VERSION_CODE))
                val live = read.snapshot as? SnapshotCandidate.Supported ?: error("يلزم تحديث التطبيق")
                val verifier = Json.encodeToString(live.catalog.pharmacy!!.adminPinVerifier)
                val authorized = admin.transaction { dao ->
                    val control = requireNotNull(dao.control()); val d = requireNotNull(dao.deviceIdentity())
                    val matches = d.adminMode && control.authorizedPinVerifier == verifier
                    dao.control(control.copy(observedPinVerifier = verifier))
                    if (!matches) dao.updateDeviceIdentity(d.copy(adminMode = false))
                    matches
                }
                check(authorized) { "أعد التفويض برمز الإدارة الحالي للصيدلية" }
                val restoredBase = requireNotNull(initial.state.confirmedPayloadJson ?: initial.attempt?.candidatePayloadJson)
                    .let { Json.decodeFromString<SnapshotWire>(it).supported() }
                val attempt = initial.attempt
                val acknowledged = attempt != null && attempt.publishId == live.publishId &&
                    Json.decodeFromString<SnapshotWire>(attempt.candidatePayloadJson).supported().confirmedJson() == live.confirmedJson()
                val included = if (acknowledged) Json.decodeFromString<List<String>>(attempt.includedOperationIdsJson).toSet() else emptySet()
                if (attempt != null && !acknowledged) {
                    val key = "restore-attempt:${attempt.publishId}:${live.publishId}"
                    if (choices[key] != true) throw ReviewRequired(listOf(EditConflict(key, "محاولة نشر محفوظة", "مصالحة المحاولة",
                        attempt.publishId, "الاحتفاظ بالمحاولة في السجل وإعادة دمج العمل دون إعادة تشغيل النقل", live.publishId)))
                }
                fun withLivePin(c: CatalogSnapshot) = c.copy(pharmacy = requireNotNull(c.pharmacy).copy(adminPinVerifier = live.catalog.pharmacy.adminPinVerifier))
                val pending = initial.ops.filterNot { it.id in included || (it.entityType == "pharmacy" && it.field == "adminPinVerifier") }
                val merged = if (replaceLive) {
                    require(typedName == initial.catalog.pharmacy!!.name)
                    val key = "restore-replace:${live.publishId}:${java.security.MessageDigest.getInstance("SHA-256").digest(live.confirmedJson().toByteArray()).joinToString("") { "%02x".format(it) }}"
                    if (choices[key] != true) throw ReviewRequired(listOf(EditConflict(key, "استبدال المحتوى المنشور", "تأكيد الاستبدال",
                        "المراجعة ${live.revision}", "اعتماد محتوى النسخة الاحتياطية مع إبقاء رمز الإدارة الحالي؛ النشر خطوة منفصلة", live.catalog.pharmacy.name)))
                    MergeResult(withLivePin(initial.catalog), emptyList())
                } else CatalogEdits.merge(withLivePin(if (acknowledged) live.catalog else restoredBase.catalog),
                    withLivePin(initial.catalog), live.catalog, pending, choices)
                if (merged.conflicts.isNotEmpty()) throw ReviewRequired(merged.conflicts)
                CatalogRules.validate(merged.catalog)
                admin.transaction { dao ->
                    val device = admin.guard(dao)
                    check(active.info()?.id == info.id && dao.control()?.restorePaused == true &&
                        dao.catalog() == initial.catalog && dao.pending() == initial.ops && dao.snapshotState() == initial.state)
                    check(requireNotNull(dao.control()).authorizedPinVerifier == verifier)
                    val changes = CatalogEdits.diff(live.catalog, merged.catalog, initial.editor, Instant.now(), live.publishId).map { op ->
                        val prior = if (replaceLive) null else pending.lastOrNull { it.entityType == op.entityType && it.targetId == op.targetId && it.field == op.field }
                        if (prior == null) op else op.copy(id = prior.id, transactionId = prior.transactionId,
                            authorId = prior.authorId, authorName = prior.authorName, authoredAt = prior.authoredAt)
                    }
                    if (attempt != null) {
                        dao.insertHistory(listOf(RemoteObservationHistory.recovery(connection.pharmacyId, restoredBase.publishId,
                            attempt.publishId, attempt.candidatePayloadJson, read.observedAt,
                            Json.decodeFromString<SnapshotWire>(attempt.candidatePayloadJson).revision)))
                        dao.deleteAttempt(attempt.publishId)
                    }
                    if (acknowledged) {
                        val rows = CatalogEdits.rows(live.catalog)
                        val accepted = initial.ops.filter { op -> op.id in included &&
                            (if (op.field == "@entity") rows["${op.entityType}:${op.targetId}"]?.toString() ?: "null"
                            else rows["${op.entityType}:${op.targetId}"]?.get(op.field)?.toString() ?: "null") == op.intendedValueJson }.map { it.id }
                        dao.acknowledgeHistory(accepted, live.revision)
                    }
                    val retainedIds = initial.ops.map { it.id }.toSet()
                    dao.insertHistory(changes.filter { it.id !in retainedIds }.map { op -> HistoryEntry(newId(), op.id,
                        op.entityType, op.targetId, merged.catalog.historyName(op.entityType, op.targetId)
                            ?: live.catalog.historyName(op.entityType, op.targetId) ?: op.entityType,
                        op.field, op.baseValueJson, op.intendedValueJson,
                        op.authorId, op.authorName, op.authoredAt, null, null) })
                    active.retain(connection, info.id)
                    dao.deletePending(initial.ops.map { it.id }); dao.putPending(changes)
                    dao.replace(merged.catalog)
                    dao.updateSnapshotState(SnapshotState(publishedAt = live.publishedAt.toString(), confirmedPayloadJson = live.confirmedJson(), revision = live.revision,
                        publishId = live.publishId, pinnedMessageId = read.document.messageId, documentFileId = read.document.fileId,
                        documentFileUniqueId = read.document.fileUniqueId, lastSuccessfulSyncAt = read.observedAt,
                        requiredSchemaVersion = known.schemaVersion, requiredMinVersionCode = known.minVersionCode))
                    dao.control(requireNotNull(dao.control()).copy(lifecycle = "SHARED", restorePaused = false, reconnectPending = false, observedPinVerifier = verifier))
                    dao.insertHistory(listOf(HistoryEntry(newId(), null, "snapshot", live.publishId,
                        "استعادة نسخة احتياطية", if (replaceLive) "restoreReplacementReviewed" else "restoreReconciled",
                        restoredBase.publishId, live.publishId, device.id, device.displayName, Instant.now(), read.observedAt, null)))
                }
            }
            if (mutable.value.busy) mutable.value = SharingStatus(message = "تمت المصالحة. راجع التعديلات ثم انشر يدوياً عند الحاجة")
        } catch (e: CancellationException) { throw e }
        catch (e: ReviewRequired) { mutable.value = SharingStatus(message = "راجع الخيارات ثم أعد المصالحة", conflicts = e.conflicts) }
        catch (_: Exception) { mutable.value = SharingStatus(message = "تعذرت المصالحة؛ احتُفظ بالعمل والنشر متوقف. تحقق من الاتصال ورمز الإدارة الحالي") }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }

    private suspend fun restoredStore(): PendingConnectionStore = admin.transaction { dao ->
        admin.guard(dao); check(dao.control()?.restorePaused == true)
        if (dao.control()?.lifecycle == "LOCAL_DRAFT") staged else active
    }
    internal suspend fun inspectRestoredConnection(token: String): String = gate.withLock {
        val store = restoredStore()
        val info = requireNotNull(store.info())
        store.withCandidate(info.id) { candidate ->
            val secrets = candidate.copySecrets()
            try {
                val replacement = token.ifBlank { secrets.botToken }
                require(replacement.substringBefore(':') == secrets.botToken.substringBefore(':')) { "البوت المختلف يحتاج ترحيل اتصال" }
                setup.validate(replacement, secrets.groupId).title
            } finally { secrets.snapshotKey.fill(0) }
        }
    }
    internal suspend fun confirmRestoredConnection(token: String, expectedTitle: String) = gate.withLock {
        val store = restoredStore()
        val info = requireNotNull(store.info())
        store.withCandidate(info.id) { candidate ->
            val secrets = candidate.copySecrets()
            try {
                val replacement = token.ifBlank { secrets.botToken }
                require(replacement.substringBefore(':') == secrets.botToken.substringBefore(':'))
                check(setup.validate(replacement, secrets.groupId).title == expectedTitle)
                ConnectionCandidate(candidate.pharmacyId, candidate.pharmacyName, candidate.createdAt, candidate.createdBy,
                    replacement, candidate.groupId, secrets.snapshotKey.copyOf(), candidate.keyVersion).use { updated -> store.retain(updated, info.id) }
                admin.transaction { dao -> admin.guard(dao); dao.control(requireNotNull(dao.control()).copy(restoreConnectionConfirmed = true)) }
            } finally { secrets.snapshotKey.fill(0) }
        }
    }

    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        try {
            admin.transaction { check(it.control()?.restorePaused != true) }
            val state = local()
            check(state.lifecycle == "SHARED" && state.state.confirmedPayloadJson != null)
            val info = requireNotNull(active.info())
            active.withCandidate(info.id) { candidate ->
                val secrets = candidate.copySecrets()
                try {
                    val p = requireNotNull(state.catalog.pharmacy)
                    val wire = ConnectionWire(1, p.pharmacyId, p.name, secrets.botToken, secrets.groupId,
                        Base64.getEncoder().encodeToString(secrets.snapshotKey), secrets.keyVersion, Instant.now(), state.editor)
                    val bytes = EnvelopeWriter.connection(wire, password)
                    try { (context.contentResolver.openOutputStream(uri, "wt") ?: error("تعذر فتح الملف")).use { it.write(bytes) } }
                    finally { bytes.fill(0) }
                } finally { secrets.snapshotKey.fill(0) }
            }
        } finally { password.fill('\u0000') }
    }
    private class ReviewRequired(val conflicts: List<EditConflict>) : Exception()
}
