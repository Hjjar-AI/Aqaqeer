package com.aqaqeer.app.maintenance

import android.content.Context
import android.net.Uri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.backup.BackupRepository
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceCoordinator @Inject internal constructor(internal val store: MaintenanceStore, private val stage: MaintenanceStage,
    internal val admin: AdminRepository, internal val reads: SnapshotReadClient, internal val setup: TelegramSetupTransport,
    internal val writes: SnapshotWriteTransport, internal val transport: SnapshotReadTransport, private val requirements: SnapshotRequirementsStore,
    private val backups: BackupRepository, @param:ApplicationContext private val context: Context) {
    internal val status: Flow<MaintenanceStatus> = MaintenanceStore.changes.map {
        try { MaintenanceStatus(store.read()) } catch (error: CancellationException) { throw error }
        catch (_: Exception) { MaintenanceStatus(unreadable = true) }
    }.flowOn(Dispatchers.IO)
    internal val conflicts = MutableStateFlow<List<EditConflict>>(emptyList())
    private val active = PendingConnectionStore(store.secure, "active-connection")
    private val envelopes = EnvelopeReader(context.noBackupFilesDir)

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        admin.sharingGate.withLock { block() }
    }
    internal suspend fun journal(): MaintenanceJournal = withContext(Dispatchers.IO) {
        val j = admin.transaction { dao ->
            finishMaintenanceActivation(dao, store, stage)
            requireNotNull(store.read()).also { check(dao.pharmacy()?.pharmacyId == it.pharmacyId) }
        }
        if (j.stateDigest == null && j.phase == MaintenancePhase.PREPARING) prepareBaseline(j) else j
    }
    private suspend fun prepareBaseline(j: MaintenanceJournal): MaintenanceJournal {
        val base = baseline() // The journal already pauses new edits; wait for any committing edit.
        check(base.catalog.pharmacy?.pharmacyId == j.pharmacyId)
        stage.save(base)
        val retained = base.state.confirmedPayloadJson?.let { Json.decodeFromString<SnapshotWire>(it) }
        return j.copy(stateDigest = maintenanceStateDigest(base.catalog, base.state, base.pending, base.attempts, base.historyCount),
            sourceId = retained?.publishId, sourceRevision = retained?.revision, sourceTime = retained?.publishedAt?.toString()).also(store::write)
    }
    private suspend fun baseline() = admin.transaction { dao ->
        MaintenanceBaseline(dao.catalog(), requireNotNull(dao.snapshotState()), dao.pending(), dao.attempts(), dao.historyCount())
    }
    internal suspend fun administrator(j: MaintenanceJournal) = admin.transaction { dao ->
        val device = admin.guard(dao, allowMaintenance = true)
        check(dao.pharmacy()?.pharmacyId == j.pharmacyId && dao.control()?.restorePaused != true)
        device
    }
    internal suspend fun begin(kind: MaintenanceKind) = serialized {
        check(store.read() == null)
        val pharmacy = admin.transaction { dao ->
            check(!BuildConfig.IS_DEMO && dao.control()?.lifecycle == "SHARED" && dao.control()?.restorePaused != true && dao.control()?.leaving != true)
            if (kind != MaintenanceKind.REPLACEMENT) { admin.guard(dao); check(dao.pendingCount() == 0 && dao.attemptCount() == 0) }
            requireNotNull(dao.pharmacy())
        }
        val id = newId()
        val info = requireNotNull(active.info())
        val sourceGroup = active.withCandidate(info.id) { store.connection("old").retain(it, id); it.groupId }
        var j = MaintenanceJournal(id = id, pharmacyId = pharmacy.pharmacyId, kind = kind, sourceGroupId = sourceGroup)
        store.write(j)
        j = prepareBaseline(j)
        if (kind != MaintenanceKind.REPLACEMENT) {
            val current = baseline(); check(current.pending.isEmpty() && current.attempts.isEmpty())
        }
    }
    internal suspend fun authenticated(candidate: ConnectionCandidate): Pair<VerifiedSnapshotRead, SnapshotCandidate.Supported> {
        requirements.read(candidate.pharmacyId)
        val read = reads.read(candidate, BuildConfig.VERSION_CODE)
        val known = withContext(NonCancellable) { requirements.observe(candidate.pharmacyId, read.snapshot.compatibility) }
        check(!known.requiresUpdate(BuildConfig.VERSION_CODE))
        return read to (read.snapshot as? SnapshotCandidate.Supported ?: error("يلزم إصدار متوافق"))
    }
    internal suspend fun stageReplacement(uri: Uri, password: CharArray) {
        try { withContext(Dispatchers.IO) {
            if (store.read() == null) begin(MaintenanceKind.REPLACEMENT)
            serialized {
                val j = journal(); check(j.kind == MaintenanceKind.REPLACEMENT && j.phase == MaintenancePhase.PREPARING)
                require(uri.scheme == "content")
                val input = requireNotNull(context.contentResolver.openInputStream(uri))
                PayloadReader().connection(envelopes.password(input, EnvelopeDomain.CONNECTION, password), j.pharmacyId).use { candidate ->
                    admin.transaction { dao -> check(dao.pharmacy()?.pharmacyId == j.pharmacyId && dao.control()?.leaving != true) }
                    store.connection("candidate").retain(candidate, j.id)
                    store.write(j.copy(phase = MaintenancePhase.STAGED))
                }
            }
        } } finally { password.fill('\u0000') }
    }
    internal suspend fun verifyReplacement() = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.REPLACEMENT && j.phase in setOf(MaintenancePhase.STAGED, MaintenancePhase.VERIFIED))
        store.connection(j.activationEntry).withCandidate(j.id) { candidate ->
            val (read, live) = authenticated(candidate)
            stage.payload(j.id, live.confirmedJson())
            store.write(observation(j, read, live).copy(phase = MaintenancePhase.VERIFIED))
        }
    }
    internal suspend fun activateReplacement() = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.REPLACEMENT && j.phase in setOf(MaintenancePhase.VERIFIED, MaintenancePhase.ACTIVATING))
        store.connection(j.activationEntry).withCandidate(j.id) { candidate ->
            val (read, live) = authenticated(candidate)
            stage.payload(j.id, live.confirmedJson())
            activate(observation(j, read, live), read, live, preserveWork = true)
        }
    }
    internal fun observation(j: MaintenanceJournal, read: VerifiedSnapshotRead, live: SnapshotCandidate.Supported) = j.copy(
        candidateId = live.publishId, candidateTime = live.publishedAt.toString(), destinationGroupId = read.document.groupId, targetMessageId = read.document.messageId, targetFileId = read.document.fileId,
        targetUniqueId = read.document.fileUniqueId)

    internal suspend fun activate(journal: MaintenanceJournal, read: VerifiedSnapshotRead, live: SnapshotCandidate.Supported, preserveWork: Boolean) {
        val old = stage.baseline(journal.id)
        val j = journal.copy(phase = MaintenancePhase.ACTIVATING, checkpointId = newId())
        store.write(j)
        withContext(NonCancellable) {
            admin.transaction { dao ->
                check(dao.pharmacy()?.pharmacyId == j.pharmacyId)
                check(maintenanceStateDigest(dao.catalog(), requireNotNull(dao.snapshotState()), dao.pending(), dao.attempts(), dao.historyCount()) == j.stateDigest)
                val visible = if (preserveWork && dao.pendingCount() > 0) old.catalog.copy(pharmacy = live.catalog.pharmacy) else live.catalog
                authorizeObservedPin(dao, store.secure, live)
                dao.replace(visible)
                val known = requireNotNull(requirements.read(j.pharmacyId))
                dao.updateSnapshotState(SnapshotState(publishedAt = live.publishedAt.toString(), confirmedPayloadJson = live.confirmedJson(), revision = live.revision, publishId = live.publishId,
                    pinnedMessageId = read.document.messageId, documentFileId = read.document.fileId, documentFileUniqueId = read.document.fileUniqueId,
                    lastSuccessfulSyncAt = read.observedAt, requiredSchemaVersion = known.schemaVersion, requiredMinVersionCode = known.minVersionCode))
                old.state.confirmedPayloadJson?.let { wire -> dao.insertHistory(listOf(RemoteObservationHistory.recovery(j.pharmacyId,
                    old.state.publishId.orEmpty(), Json.decodeFromString<SnapshotWire>(wire).publishId, wire, read.observedAt, Json.decodeFromString<SnapshotWire>(wire).revision))) }
                dao.insertHistory(RemoteObservationHistory.changes(old.catalog, live.catalog, read.observedAt, live.revision) +
                    RemoteObservationHistory.publication(old.state.publishId.orEmpty(), live, read.observedAt))
                dao.control(requireNotNull(dao.control()).copy(reconnectPending = false, lifecycle = "SHARED"))
                recordMaintenanceCheckpoint(dao, j, "connectionActivated")
            }
            admin.transaction { finishMaintenanceActivation(it, store, stage) }
        }
    }
    internal suspend fun replacementReview(choices: Map<String, Boolean>) = serialized {
        conflicts.value = emptyList()
        try {
            val j = journal(); check(j.kind == MaintenanceKind.REPLACEMENT && j.phase in setOf(MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE))
            val device = administrator(j)
            val old = stage.baseline(j.id)
            active.withCandidate(j.id) { candidate ->
                val (read, live) = authenticated(candidate)
                val authorized = admin.transaction { authorizeObservedPin(it, store.secure, live) }; check(authorized)
                val key = "replacement:${j.id}:${MaintenanceStore.digest(live.confirmedJson())}"
                val attempt = old.attempts.singleOrNull()
                val attemptAccepted = attempt != null && attempt.publishId == live.publishId &&
                    Json.decodeFromString<SnapshotWire>(attempt.candidatePayloadJson).supported().confirmedJson() == live.confirmedJson()
                val accepted = if (attemptAccepted) Json.decodeFromString<List<String>>(requireNotNull(attempt).includedOperationIdsJson).toSet() else emptySet()
                if (attempt != null && !attemptAccepted && choices[key] == null) throw MaintenanceReview(listOf(EditConflict(key,
                    "محاولة نشر محفوظة", "مصالحة المحاولة", attempt.publishId, "أرشفة المحاولة وإعادة دمج العمل دون إعادة النقل", live.publishId)))
                if (attempt != null && !attemptAccepted) check(choices[key] == true)
                val pending = old.pending.filterNot { it.id in accepted }.filter { op ->
                    if (op.entityType != "pharmacy" || op.field != "adminPinVerifier") true else {
                        val association = store.decode<PinChangeJournal>("pin-change")
                        val valid = association != null && association.operationId == op.id && association.deviceId == device.id &&
                            association.pharmacyId == j.pharmacyId && association.previous == live.catalog.pharmacy!!.adminPinVerifier &&
                            op.intendedValueJson == editJson.encodeToString(association.next)
                        if (!valid) {
                            val pinKey = "replacement-pin:${op.id}:${MaintenanceStore.digest(live.confirmedJson())}"
                            if (choices[pinKey] != false) throw MaintenanceReview(listOf(EditConflict(pinKey, "نية تغيير رمز سابقة", "رمز الإدارة",
                                "تفويض سابق", "يلزم تغيير الرمز من جديد بالرمز الحالي بعد المراجعة", "إلغاء هذه النية فقط مع إبقاء السجل")))
                        }
                        valid
                    }
                }
                fun keyVersion(c: CatalogSnapshot) = c.copy(pharmacy = requireNotNull(c.pharmacy).copy(keyVersion = candidate.keyVersion))
                val base = requireNotNull(old.state.confirmedPayloadJson).let { Json.decodeFromString<SnapshotWire>(it).supported() }
                val merged = CatalogEdits.merge(keyVersion(base.catalog), keyVersion(old.catalog), live.catalog, pending, choices)
                if (merged.conflicts.isNotEmpty()) throw MaintenanceReview(merged.conflicts)
                val rebased = CatalogEdits.diff(live.catalog, merged.catalog, EditorRef(device.id, requireNotNull(device.displayName)), Instant.now(), live.publishId).map { op ->
                    pending.lastOrNull { it.entityType == op.entityType && it.targetId == op.targetId && it.field == op.field }?.let { prior ->
                        op.copy(id = prior.id, transactionId = prior.transactionId, authorId = prior.authorId, authorName = prior.authorName, authoredAt = prior.authoredAt)
                    } ?: op
                }
                val finishing = j.copy(phase = MaintenancePhase.COMPLETE, checkpointId = newId())
                store.write(finishing)
                withContext(NonCancellable) {
                    admin.transaction { dao ->
                        admin.guard(dao, allowMaintenance = true)
                        check(dao.pending() == old.pending && dao.attempts().map { it.publishId } == old.attempts.map { it.publishId })
                        if (attempt != null && !attemptAccepted) dao.insertHistory(listOf(RemoteObservationHistory.recovery(j.pharmacyId,
                            base.publishId, attempt.publishId, attempt.candidatePayloadJson, read.observedAt, Json.decodeFromString<SnapshotWire>(attempt.candidatePayloadJson).revision)))
                        val liveRows = CatalogEdits.rows(live.catalog)
                        val acknowledged = old.pending.filter { op -> op.id in accepted && (if (op.field == "@entity")
                            liveRows["${op.entityType}:${op.targetId}"]?.toString() ?: "null" else
                            liveRows["${op.entityType}:${op.targetId}"]?.get(op.field)?.toString() ?: "null") == op.intendedValueJson }.map { it.id }
                        dao.acknowledgeHistory(acknowledged, live.revision)
                        dao.deletePending(dao.pending().map { it.id }); dao.putPending(rebased)
                        dao.attempts().forEach { dao.deleteAttempt(it.publishId) }; dao.replace(merged.catalog)
                        val known = requireNotNull(requirements.read(j.pharmacyId))
                        dao.updateSnapshotState(SnapshotState(publishedAt = live.publishedAt.toString(), confirmedPayloadJson = live.confirmedJson(), revision = live.revision, publishId = live.publishId,
                            pinnedMessageId = read.document.messageId, documentFileId = read.document.fileId, documentFileUniqueId = read.document.fileUniqueId,
                            lastSuccessfulSyncAt = read.observedAt, requiredSchemaVersion = known.schemaVersion, requiredMinVersionCode = known.minVersionCode))
                        dao.control(requireNotNull(dao.control()).copy(reconnectPending = false))
                        recordMaintenanceCheckpoint(dao, finishing, "maintenanceFinished")
                    }
                    admin.transaction { finishMaintenanceActivation(it, store, stage) }
                }
            }
        } catch (review: MaintenanceReview) { conflicts.value = review.conflicts }
    }
    internal suspend fun prepareSource(allowRetained: Boolean, choices: Map<String, Boolean> = emptyMap()) = serialized {
        conflicts.value = emptyList()
        try {
            val j = journal(); check(j.kind != MaintenanceKind.REPLACEMENT && j.phase in setOf(MaintenancePhase.PREPARING,
                MaintenancePhase.SOURCE_READY, MaintenancePhase.BACKED_UP, MaintenancePhase.TOKEN_CHANGE))
            administrator(j)
            val entry = if (j.phase == MaintenancePhase.TOKEN_CHANGE && store.connection("transport").info() != null) "transport" else "old"
            store.connection(entry).withCandidate(j.id) { connection ->
                var observed: Pair<VerifiedSnapshotRead, SnapshotCandidate.Supported>? = null
                try { observed = authenticated(connection) }
                catch (failure: SnapshotReadException) {
                    if (failure.error !in setOf(SnapshotReadError.NETWORK, SnapshotReadError.INVALID_CREDENTIALS,
                            SnapshotReadError.ACCESS_DENIED, SnapshotReadError.SNAPSHOT_UNAVAILABLE)) throw failure
                    if (j.kind != MaintenanceKind.MIGRATION || !allowRetained) throw failure
                }
                if (observed == null) {
                    val base = baseline(); check(base.pending.isEmpty() && base.attempts.isEmpty())
                    val wire = Json.decodeFromString<SnapshotWire>(requireNotNull(base.state.confirmedPayloadJson)).supported()
                    val key = "retained:${j.id}:${MaintenanceStore.digest(wire.confirmedJson())}"
                    if (choices[key] != true) throw MaintenanceReview(listOf(EditConflict(key, "المصدر غير متاح", "مراجعة استرداد صريحة",
                        "${wire.publishId} · ${wire.publishedAt}", "استخدام النسخة المحتفظ بها؛ قد توجد تغييرات بعيدة غير مرئية لا يمكن استعادتها", "إيقاف الترحيل")))
                    stage.save(base)
                    store.write(j.copy(phase = MaintenancePhase.SOURCE_READY, sourceId = wire.publishId, sourceRevision = wire.revision,
                        sourceTime = wire.publishedAt.toString(), sourceDigest = MaintenanceStore.digest(wire.confirmedJson()),
                        stateDigest = maintenanceStateDigest(base.catalog, base.state, base.pending, base.attempts, base.historyCount), receipt = null, sourceUnavailable = true,
                        sourceGroupId = connection.groupId, sourceTitle = "مصدر محتفظ به — غير متحقق الآن"))
                } else {
                    val secrets = connection.copySecrets()
                    try {
                        val bot = transport.bot(secrets.botToken)
                        val group = writes.inspect(secrets.botToken, secrets.groupId)
                        applySource(j.copy(sourceBotId = bot.id, sourceGroupId = secrets.groupId, sourceTitle = group.title), observed.first, observed.second, choices)
                    } finally { secrets.snapshotKey.fill(0) }
                }
            }
        } catch (review: MaintenanceReview) { conflicts.value = review.conflicts }
    }
    private suspend fun applySource(j: MaintenanceJournal, read: VerifiedSnapshotRead, live: SnapshotCandidate.Supported, choices: Map<String, Boolean>) {
        val base = baseline(); check(base.pending.isEmpty() && base.attempts.isEmpty())
        val old = Json.decodeFromString<SnapshotWire>(requireNotNull(base.state.confirmedPayloadJson)).supported()
        if (old.confirmedJson() != live.confirmedJson() && runCatching { publicationDecision(old, live) }.isFailure) {
            val key = "source:${j.id}:${MaintenanceStore.digest(live.confirmedJson())}"
            if (choices[key] != true) throw MaintenanceReview(listOf(EditConflict(key, "مراجعة تسلسل المصدر", "سلسلة النشر",
                old.publishId, "اعتماد المصدر الموثق بعد مراجعة تسلسله", live.publishId)))
        }
        val valid = admin.transaction { authorizeObservedPin(it, store.secure, live) }; check(valid)
        admin.transaction { dao ->
            admin.guard(dao, allowMaintenance = true)
            check(dao.catalog() == base.catalog && dao.pendingCount() == 0 && dao.attemptCount() == 0 && dao.snapshotState() == base.state)
            if (old.confirmedJson() != live.confirmedJson()) dao.insertHistory(RemoteObservationHistory.changes(old.catalog, live.catalog,
                read.observedAt, live.revision) + RemoteObservationHistory.publication(old.publishId, live, read.observedAt))
            dao.replace(live.catalog)
            val known = requireNotNull(requirements.read(j.pharmacyId))
            dao.updateSnapshotState(SnapshotState(publishedAt = live.publishedAt.toString(), confirmedPayloadJson = live.confirmedJson(), revision = live.revision, publishId = live.publishId,
                pinnedMessageId = read.document.messageId, documentFileId = read.document.fileId, documentFileUniqueId = read.document.fileUniqueId,
                lastSuccessfulSyncAt = read.observedAt, requiredSchemaVersion = known.schemaVersion, requiredMinVersionCode = known.minVersionCode))
            dao.control(requireNotNull(dao.control()).copy(reconnectPending = false))
        }
        val updated = baseline(); stage.save(updated)
        val digest = MaintenanceStore.digest(live.confirmedJson())
        store.write(j.copy(phase = if (j.phase == MaintenancePhase.TOKEN_CHANGE) j.phase else if (j.receipt != null && j.sourceDigest == digest)
            MaintenancePhase.BACKED_UP else MaintenancePhase.SOURCE_READY, sourceId = live.publishId, sourceRevision = live.revision,
            sourceTime = live.publishedAt.toString(), sourceDigest = digest,
            stateDigest = maintenanceStateDigest(updated.catalog, updated.state, updated.pending, updated.attempts, updated.historyCount),
            receipt = j.receipt?.takeIf { j.sourceDigest == digest }, sourceUnavailable = false))
    }
    internal suspend fun backup(uri: Uri, password: CharArray, confirmation: CharArray, expectedFlowId: String) {
        try {
            val j = journal(); check(j.id == expectedFlowId && j.kind != MaintenanceKind.REPLACEMENT && j.sourceDigest != null)
            // Export owns the sharing mutex; never acquire it twice.
            backups.exportForMaintenance(j.id, uri, password, confirmation)
        } finally { password.fill('\u0000'); confirmation.fill('\u0000') }
    }
    private suspend fun sourceUnchanged(j: MaintenanceJournal) {
        val current = baseline()
        check(current.pending.isEmpty() && current.attempts.isEmpty())
        check(maintenanceStateDigest(current.catalog, current.state, current.pending, current.attempts, current.historyCount) == j.stateDigest)
        val connection = if (store.connection("transport").info() != null && j.kind == MaintenanceKind.ROTATION) "transport" else "old"
        store.connection(connection).withCandidate(j.id) { candidate ->
            var observed: Pair<VerifiedSnapshotRead, SnapshotCandidate.Supported>? = null
            try { observed = authenticated(candidate) }
            catch (failure: SnapshotReadException) {
                if (!j.sourceUnavailable || j.kind != MaintenanceKind.MIGRATION || failure.error !in setOf(SnapshotReadError.NETWORK,
                        SnapshotReadError.INVALID_CREDENTIALS, SnapshotReadError.ACCESS_DENIED, SnapshotReadError.SNAPSHOT_UNAVAILABLE)) throw failure
            }
            observed?.let { (read, live) ->
                val valid = admin.transaction { authorizeObservedPin(it, store.secure, live) }
                if (MaintenanceStore.digest(live.confirmedJson()) != j.sourceDigest) {
                    val canRestart = j.phase !in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING, MaintenancePhase.COMPLETE)
                    if (canRestart) store.connection("candidate").let { it.info()?.let { info -> it.discard(info.id) } }
                    store.write(j.copy(receipt = null, phase = if (!canRestart) j.phase else if (j.kind == MaintenanceKind.ROTATION)
                        MaintenancePhase.TOKEN_CHANGE else MaintenancePhase.SOURCE_READY,
                        candidateId = if (canRestart) null else j.candidateId,
                        uploadedFileId = if (canRestart) null else j.uploadedFileId, uploadedMessageId = if (canRestart) null else j.uploadedMessageId))
                    throw MaintenanceProblem("تغير المصدر؛ افحصه وصالحه ثم احفظ نسخة جديدة داخل الصيانة")
                }
                check(valid)
            }
        }
        val receipt = requireNotNull(j.receipt) { "احفظ نسخة احتياطية جديدة داخل هذه الصيانة" }
        check(receipt.flowId == j.id && receipt.sourceId == j.sourceId && receipt.stateDigest == j.stateDigest)
    }
    internal suspend fun revocationGuide() = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.ROTATION && j.phase == MaintenancePhase.BACKED_UP)
        administrator(j); sourceUnchanged(j)
        store.write(j.copy(phase = MaintenancePhase.TOKEN_CHANGE))
    }
    internal suspend fun rotationToken(token: String) = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.ROTATION && j.phase == MaintenancePhase.TOKEN_CHANGE)
        val device = administrator(j)
        check(transport.bot(token).id == requireNotNull(j.sourceBotId))
        store.connection("old").withCandidate(j.id) { old ->
            val secrets = old.copySecrets()
            try {
                val group = setup.validate(token, secrets.groupId)
                ConnectionCandidate(old.pharmacyId, old.pharmacyName, Instant.now(), EditorRef(device.id, requireNotNull(device.displayName)),
                    token, old.groupId, secrets.snapshotKey.copyOf(), old.keyVersion).use { replacement ->
                    val (read, live) = authenticated(replacement)
                    store.connection("transport").retain(replacement, j.id)
                    if (MaintenanceStore.digest(live.confirmedJson()) != j.sourceDigest) {
                        applySource(j.copy(receipt = null), read, live, emptyMap())
                        throw MaintenanceProblem("تغير المصدر بعد استبدال الرمز؛ احفظ نسخة جديدة ثم تابع")
                    }
                }
                sourceUnchanged(j)
                val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
                check(old.keyVersion < Int.MAX_VALUE)
                ConnectionCandidate(old.pharmacyId, old.pharmacyName, Instant.now(), EditorRef(device.id, requireNotNull(device.displayName)),
                    token, old.groupId, key, old.keyVersion + 1).use { store.connection("candidate").retain(it, j.id) }
                store.write(j.copy(phase = MaintenancePhase.DESTINATION_READY, destinationTitle = group.title,
                    destinationBotId = j.sourceBotId, destinationGroupId = old.groupId, destinationConfirmed = true))
            } finally { secrets.snapshotKey.fill(0) }
        }
    }
    internal suspend fun inspectDestination(token: String, group: Long): String = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.MIGRATION && j.phase == MaintenancePhase.BACKED_UP)
        administrator(j); sourceUnchanged(j)
        setup.validate(token, group).also { check(it.pin == null) }.title
    }
    internal suspend fun confirmDestination(token: String, group: Long, title: String) = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.MIGRATION && j.phase == MaintenancePhase.BACKED_UP)
        val device = administrator(j); sourceUnchanged(j)
        val validated = setup.validate(token, group); check(validated.title == title && validated.pin == null)
        val bot = transport.bot(token)
        val pharmacy = requireNotNull(baseline().catalog.pharmacy); check(pharmacy.keyVersion < Int.MAX_VALUE)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        ConnectionCandidate(pharmacy.pharmacyId, pharmacy.name, Instant.now(), EditorRef(device.id, requireNotNull(device.displayName)),
            token, group, key, pharmacy.keyVersion + 1).use { store.connection("candidate").retain(it, j.id) }
        store.write(j.copy(phase = MaintenancePhase.DESTINATION_READY, destinationTitle = title,
            destinationBotId = bot.id, destinationGroupId = group, destinationConfirmed = true))
    }
    internal suspend fun publishMaintenance() = serialized {
        var j = journal(); check(j.kind != MaintenanceKind.REPLACEMENT)
        val device = administrator(j)
        check(j.phase in setOf(MaintenancePhase.DESTINATION_READY, MaintenancePhase.PREPARED, MaintenancePhase.UPLOAD_STARTED,
            MaintenancePhase.UPLOADED, MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING))
        store.connection("candidate").withCandidate(j.id) { connection ->
            val secrets = connection.copySecrets()
            try {
                if (j.phase == MaintenancePhase.DESTINATION_READY) {
                    sourceUnchanged(j)
                    val source = requireNotNull(baseline().state.confirmedPayloadJson).let { Json.decodeFromString<SnapshotWire>(it).supported() }
                    check(source.revision < Long.MAX_VALUE && j.destinationConfirmed)
                    val catalog = source.catalog.copy(pharmacy = requireNotNull(source.catalog.pharmacy).copy(keyVersion = connection.keyVersion))
                    val candidate = SnapshotCandidate.Supported(SnapshotCompatibility(1, catalog.pharmacy!!.minVersionCode, connection.keyVersion),
                        source.revision + 1, newId(), source.publishId, source.revision, Instant.now(),
                        EditorRef(device.id, requireNotNull(device.displayName)), catalog)
                    val target = setup.validate(secrets.botToken, secrets.groupId)
                    if (j.kind == MaintenanceKind.MIGRATION) check(target.pin == null)
                    stage.payload(j.id, candidate.confirmedJson())
                    j = j.copy(phase = MaintenancePhase.PREPARED, candidateId = candidate.publishId, originalPin = target.pin,
                        targetMessageId = if (j.kind == MaintenanceKind.ROTATION) baseline().state.pinnedMessageId else null)
                    store.write(j)
                }
                val candidateText = stage.payload(j.id)
                val candidate = Json.decodeFromString<SnapshotWire>(candidateText).supported()
                check(candidate.publishId == j.candidateId)
                var target = writes.inspect(secrets.botToken, secrets.groupId)
                if (target.pin != null) {
                    var observed: Pair<VerifiedSnapshotRead, SnapshotCandidate.Supported>? = null
                    try { observed = authenticated(connection) }
                    catch (failure: EnvelopeException) {
                        if (failure.error != EnvelopeError.AUTHENTICATION || j.kind != MaintenanceKind.ROTATION || target.pin != j.originalPin) throw failure
                    }
                    if (observed != null) {
                        check(observed.second.confirmedJson() == candidateText) { "تغيرت نسخة الوجهة؛ يلزم استرداد صريح" }
                        activate(observation(j, observed.first, observed.second), observed.first, observed.second, preserveWork = false)
                        return@withCandidate
                    }
                } else check(j.kind == MaintenanceKind.MIGRATION)
                // Candidate has not been verified as current. Revalidate the source/checkpoint before another write.
                sourceUnchanged(j)
                check(target.pin == j.originalPin)
                if (j.uploadedFileId == null) {
                    j = j.copy(phase = MaintenancePhase.UPLOAD_STARTED); store.write(j)
                    val bytes = EnvelopeWriter.snapshot(Json.decodeFromString(candidateText), secrets.snapshotKey)
                    val uploaded = try { writes.upload(secrets.botToken, secrets.groupId, bytes, SnapshotCaption.encode(candidate.publication)) }
                        finally { bytes.fill(0) }
                    j = j.copy(phase = MaintenancePhase.UPLOADED, uploadedFileId = uploaded.fileId, uploadedMessageId = uploaded.messageId)
                    store.write(j)
                }
                sourceUnchanged(j)
                val latest = writes.inspect(secrets.botToken, secrets.groupId)
                check(latest.pin == target.pin)
                j = j.copy(phase = MaintenancePhase.WRITE_STARTED); store.write(j)
                if (j.kind == MaintenanceKind.MIGRATION) writes.pin(secrets.botToken, secrets.groupId, requireNotNull(j.uploadedMessageId))
                else writes.replace(secrets.botToken, secrets.groupId, requireNotNull(j.targetMessageId), requireNotNull(j.uploadedFileId), SnapshotCaption.encode(candidate.publication))
                val (read, live) = authenticated(connection)
                check(live.confirmedJson() == candidateText)
                activate(observation(j, read, live), read, live, preserveWork = false)
            } finally { secrets.snapshotKey.fill(0) }
        }
    }
    internal suspend fun finishReaderReplacement() = serialized {
        val j = journal(); check(j.kind == MaintenanceKind.REPLACEMENT && j.phase in setOf(MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE))
        val retained = stage.baseline(j.id); check(retained.pending.isEmpty() && retained.attempts.isEmpty())
        val current = baseline(); check(current.pending.isEmpty() && current.attempts.isEmpty())
        store.connection(j.activationEntry).withCandidate(j.id) { candidate ->
            val (read, live) = authenticated(candidate)
            if (current.state.confirmedPayloadJson != live.confirmedJson()) {
                stage.payload(j.id, live.confirmedJson())
                activate(observation(j, read, live).copy(stateDigest = maintenanceStateDigest(current.catalog, current.state,
                    current.pending, current.attempts, current.historyCount)), read, live, preserveWork = true)
            }
        }
        val finishing = requireNotNull(store.read()).copy(phase = MaintenancePhase.COMPLETE, checkpointId = newId())
        store.write(finishing)
        withContext(NonCancellable) {
            admin.transaction { dao ->
                check(dao.pharmacy()?.pharmacyId == j.pharmacyId && dao.pendingCount() == 0 && dao.attemptCount() == 0)
                recordMaintenanceCheckpoint(dao, finishing, "maintenanceFinished")
            }
            admin.transaction { finishMaintenanceActivation(it, store, stage) }
        }
    }
    internal suspend fun reviewDestination(choices: Map<String, Boolean>) = serialized {
        conflicts.value = emptyList()
        try {
            val j = journal(); check(j.kind != MaintenanceKind.REPLACEMENT && j.phase in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING))
            administrator(j)
            store.connection(j.activationEntry).withCandidate(j.id) { candidate ->
                val (read, live) = authenticated(candidate)
                val expected = stage.payload(j.id)
                if (live.confirmedJson() != expected) {
                    val key = "destination:${j.id}:${MaintenanceStore.digest(live.confirmedJson())}"
                    if (choices[key] != true) throw MaintenanceReview(listOf(EditConflict(key, "نسخة مختلفة في الوجهة", "مراجعة استرداد",
                        requireNotNull(j.candidateId), "قبول النسخة الموثقة بعد مراجعة صاحب الصيدلية؛ الاحتفاظ بالمرشح السابق في السجل", live.publishId)))
                    val own = Json.decodeFromString<SnapshotWire>(expected)
                    admin.transaction { dao -> dao.insertHistory(listOf(RemoteObservationHistory.recovery(j.pharmacyId,
                        j.sourceId.orEmpty(), own.publishId, expected, read.observedAt, own.revision))) }
                    stage.payload(j.id, live.confirmedJson())
                }
                val latest = baseline()
                activate(observation(j, read, live).copy(stateDigest = maintenanceStateDigest(latest.catalog, latest.state,
                    latest.pending, latest.attempts, latest.historyCount)), read, live, preserveWork = false)
            }
        } catch (review: MaintenanceReview) { conflicts.value = review.conflicts }
    }
    internal suspend fun complete() = serialized {
        val j = journal(); check(j.kind != MaintenanceKind.REPLACEMENT && j.phase == MaintenancePhase.COMPLETE)
        administrator(j)
        active.withCandidate(j.id) { connection ->
            val (_, live) = authenticated(connection)
            val authorized = admin.transaction { authorizeObservedPin(it, store.secure, live) }; check(authorized)
            check(live.confirmedJson() == stage.payload(j.id))
        }
        val finishing = j.copy(checkpointId = newId())
        store.write(finishing)
        withContext(NonCancellable) {
            admin.transaction { dao -> admin.guard(dao, allowMaintenance = true); recordMaintenanceCheckpoint(dao, finishing, "maintenanceFinished") }
            admin.transaction { finishMaintenanceActivation(it, store, stage) }
        }
    }
    /** A possible remote write is never dismissed locally. Inspect and explicitly accept a usable live connection. */
    internal suspend fun reviewCancellation(choices: Map<String, Boolean>) = serialized {
        conflicts.value = emptyList()
        try {
            val j = journal()
            check(j.kind != MaintenanceKind.REPLACEMENT && (j.phase in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING) ||
                (j.kind == MaintenanceKind.ROTATION && j.phase in setOf(MaintenancePhase.TOKEN_CHANGE, MaintenancePhase.DESTINATION_READY,
                    MaintenancePhase.PREPARED, MaintenancePhase.UPLOAD_STARTED, MaintenancePhase.UPLOADED))))
            administrator(j)
            if (j.kind == MaintenanceKind.MIGRATION) {
                store.connection("candidate").withCandidate(j.id) { candidate ->
                    val secrets = candidate.copySecrets()
                    try {
                        if (writes.inspect(secrets.botToken, secrets.groupId).pin != null)
                            throw MaintenanceProblem("للوجهة رسالة مثبتة؛ صالح النسخة هناك وأكمل التفعيل قبل الرجوع")
                    } finally { secrets.snapshotKey.fill(0) }
                }
            }
            val entry = if (j.kind == MaintenanceKind.ROTATION) "transport" else "old"
            store.connection(entry).withCandidate(j.id) { usable ->
                val (read, live) = authenticated(usable)
                val authorized = admin.transaction { authorizeObservedPin(it, store.secure, live) }; check(authorized)
                val key = "cancel:${j.id}:${MaintenanceStore.digest(live.confirmedJson())}"
                if (choices[key] != true) throw MaintenanceReview(listOf(EditConflict(key, "مراجعة الرجوع بعد محاولة خارجية", "الاتصال الموثق الآن",
                    j.candidateId.orEmpty(), "اعتماد النسخة الموثقة بالمفتاح السابق واتصال صالح، والاحتفاظ بالمرشح في السجل؛ لا يُلغى إجراء خارجي", live.publishId)))
                stage.payloadOrNull(j.id)?.let { expected ->
                    val previous = Json.decodeFromString<SnapshotWire>(expected)
                    admin.transaction { dao ->
                        admin.guard(dao, allowMaintenance = true)
                        check(dao.pendingCount() == 0 && dao.attemptCount() == 0)
                        dao.insertHistory(listOf(RemoteObservationHistory.recovery(j.pharmacyId, j.sourceId.orEmpty(),
                            previous.publishId, expected, read.observedAt, previous.revision)))
                    }
                }
                val current = baseline()
                // Preserve both credential records. A restart now follows ordinary replacement activation.
                val reviewed = observation(j, read, live).copy(kind = MaintenanceKind.REPLACEMENT, activationEntry = entry,
                    phase = MaintenancePhase.VERIFIED, receipt = null, stateDigest = maintenanceStateDigest(current.catalog,
                        current.state, current.pending, current.attempts, current.historyCount))
                store.write(reviewed)
                stage.payload(j.id, live.confirmedJson())
                activate(reviewed, read, live, preserveWork = true)
            }
        } catch (review: MaintenanceReview) { conflicts.value = review.conflicts }
    }
    internal suspend fun cancel() = serialized {
        val j = journal()
        check(j.phase !in setOf(MaintenancePhase.WRITE_STARTED, MaintenancePhase.ACTIVATING, MaintenancePhase.REVIEW, MaintenancePhase.COMPLETE)) {
            "قد يكون التفعيل أو النشر اكتمل؛ صالح النتيجة قبل المتابعة"
        }
        if (j.kind != MaintenanceKind.REPLACEMENT) {
            administrator(j)
            // A token revocation is external and cannot be undone by cancelling the wizard.
            if (j.kind == MaintenanceKind.ROTATION && j.phase in setOf(MaintenancePhase.TOKEN_CHANGE, MaintenancePhase.DESTINATION_READY,
                    MaintenancePhase.PREPARED, MaintenancePhase.UPLOAD_STARTED, MaintenancePhase.UPLOADED)) {
                store.connection("old").withCandidate(j.id) { authenticated(it) }
            }
        }
        store.write(j.copy(phase = MaintenancePhase.CANCELLING))
        admin.transaction { finishMaintenanceActivation(it, store, stage) }
        conflicts.value = emptyList()
    }
}
