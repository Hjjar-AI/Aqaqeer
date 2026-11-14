package com.aqaqeer.app.data

import com.aqaqeer.app.security.files.SnapshotCandidate
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RefreshError { DEMO, UNCONFIGURED, LOCAL_STATE_CHANGED, UPDATE_REQUIRED, PUBLICATION_RECOVERY, LOCAL_WORK }
class RefreshException(val error: RefreshError) : Exception(error.name)
enum class RefreshOutcome { UNCHANGED, APPLIED }

/** UUID/time ordering cannot establish publication ancestry. Gaps require explicit recovery. */
internal fun publicationDecision(base: SnapshotCandidate.Supported, incoming: SnapshotCandidate.Supported): RefreshOutcome {
    if (base.publishId == incoming.publishId && base.revision == incoming.revision &&
        base.compatibility == incoming.compatibility && base.parentPublishId == incoming.parentPublishId &&
        base.basedOnRevision == incoming.basedOnRevision && base.publishedAt == incoming.publishedAt &&
        base.publishedBy == incoming.publishedBy && base.catalog.sameContents(incoming.catalog)) return RefreshOutcome.UNCHANGED
    if (incoming.revision == base.revision + 1 && incoming.parentPublishId == base.publishId && incoming.publishId != base.publishId)
        return RefreshOutcome.APPLIED
    throw RefreshException(RefreshError.PUBLICATION_RECOVERY)
}

/** One app-owned foreground request, shared by callers. Cancelling a waiter cannot cancel another. */
internal class RefreshFlight(private val scope: CoroutineScope) {
    private val gate = Mutex()
    private var flight: Deferred<RefreshOutcome>? = null
    suspend fun run(action: suspend () -> RefreshOutcome): RefreshOutcome {
        val request = gate.withLock {
            flight?.takeIf { it.isActive } ?: scope.async(start = CoroutineStart.LAZY) { action() }
                .also { flight = it; it.start() }
        }
        return request.await()
    }
}

/** Wire arrays and Room queries need not have the same iteration order. */
internal fun CatalogSnapshot.sameContents(other: CatalogSnapshot): Boolean = ordered() == other.ordered()
private fun CatalogSnapshot.ordered() = copy(categories = categories.sortedBy { it.id }, forms = forms.sortedBy { it.id },
    sections = sections.sortedBy { it.id }, medications = medications.sortedBy { it.id },
    availability = availability.sortedWith(compareBy({ it.medicationId }, { it.sectionId })),
    tombstones = tombstones.sortedWith(compareBy({ it.entityType }, { it.entityId }, { it.operationId })))
