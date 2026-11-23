package com.aqaqeer.app.admin

import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.data.*
import org.junit.*
import org.junit.Assert.*

/** Flavor-specific sandbox behavior; standard repositories must never enter this workspace. */
class DemoSessionTest {
    @Before fun setup() {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        DemoSession.reset(); DemoSession.setAdmin(false)
    }
    @After fun cleanup() {
        if (BuildConfig.IS_DEMO) { DemoSession.reset(); DemoSession.setAdmin(false) }
    }
    private fun state() = DemoSession.initialize()
    private fun editNote(value: String) = DemoSession.edit { catalog, _, _, _ ->
        catalog.copy(medications = catalog.medications.mapIndexed { index, med -> if (index == 0) med.copy(note = value) else med })
    }
    private fun visible() = DemoSession.ready(CatalogState.Ready(state().initial, true), state())

    @Test fun samplesStartAsReaderWithCompleteThreeStateAvailability() {
        val session = state()
        assertFalse(session.admin); assertTrue(session.pending.isEmpty()); assertTrue(session.history.isEmpty())
        assertEquals(40, session.catalog.medications.size); assertEquals(2, session.catalog.sections.size)
        assertEquals(80, session.catalog.availability.size)
        assertEquals(setOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW, AvailabilityStatus.OUT), session.catalog.availability.map { it.status }.toSet())
        session.catalog.sections.forEach { section ->
            assertEquals(session.catalog.medications.map { it.id }.toSet(), session.catalog.availability.filter { it.sectionId == section.id }.map { it.medicationId }.toSet())
        }
    }
    @Test fun readerCannotEditPublishRotateOrExportAuthorizedState() {
        val before = state()
        assertThrows(AdminProblem::class.java) { editNote("blocked") }
        assertThrows(AdminProblem::class.java) { DemoSession.publish() }
        assertThrows(AdminProblem::class.java) { DemoSession.rotateConnection() }
        assertThrows(AdminProblem::class.java) { DemoSession.authorized() }
        assertEquals(before, state())
    }
    @Test fun readerSeesPublishedBaseUntilExplicitPublication() {
        val baseline = state().catalog
        DemoSession.setAdmin(true); editNote("draft")
        val pending = state().pending
        assertTrue(pending.isNotEmpty()); assertEquals("draft", visible().catalog.medications.first().note)
        DemoSession.setAdmin(false)
        assertEquals(baseline, visible().catalog); assertEquals(0, visible().pendingCount)
        assertTrue(visible().unpublishedMedicationIds.isEmpty()); assertEquals(pending, state().pending)
        DemoSession.setAdmin(true); DemoSession.publish(); DemoSession.setAdmin(false)
        assertEquals("draft", visible().catalog.medications.first().note)
        assertTrue(state().pending.isEmpty()); assertEquals(2L, state().revision)
        assertTrue(state().history.all { it.publishedRevision == 2L })
    }
    @Test fun returningToPublishedValueRemovesNetPendingButRetainsHistory() {
        val original = state().catalog.medications.first().note!!
        DemoSession.setAdmin(true); editNote("temporary"); editNote(original)
        assertTrue(state().pending.isEmpty()); assertEquals(2, state().history.size)
        val before = state(); DemoSession.publish(); assertEquals(before, state())
    }
    @Test fun refreshPreservesDraftPendingAndHistory() {
        DemoSession.setAdmin(true); editNote("keep")
        val before = state(); DemoSession.refresh(); val after = state()
        assertEquals(before.catalog, after.catalog); assertEquals(before.initial, after.initial)
        assertEquals(before.pending, after.pending); assertEquals(before.history, after.history)
        assertFalse(after.refreshedAt.isBefore(before.refreshedAt))
    }
    @Test fun disconnectedAndOfflinePublishFailWithoutAcknowledgingWork() {
        DemoSession.setAdmin(true); editNote("keep"); DemoSession.setConnection(false)
        var before = state()
        assertThrows(AdminProblem::class.java) { DemoSession.publish() }; assertEquals(before, state())
        DemoSession.setConnection(true); DemoSession.setOnline(false); before = state()
        assertThrows(AdminProblem::class.java) { DemoSession.publish() }; assertEquals(before, state())
        assertThrows(AdminProblem::class.java) { DemoSession.refresh() }; assertEquals(before, state())
    }
    @Test fun offlineEditPublishesOnSimulatedReconnectButOnlineEditWaits() {
        DemoSession.setAdmin(true); editNote("online draft")
        DemoSession.setOnline(false); DemoSession.setOnline(true)
        assertTrue(state().pending.isNotEmpty()); assertEquals(1L, state().revision)
        DemoSession.setOnline(false); editNote("offline draft"); DemoSession.setOnline(true)
        assertTrue(state().pending.isEmpty()); assertEquals("offline draft", state().initial.medications.first().note)
        assertEquals(2L, state().revision)
    }
    @Test fun reconnectAsReaderNeverPublishesQueuedAdminWork() {
        DemoSession.setAdmin(true); DemoSession.setOnline(false); editNote("queued")
        DemoSession.setAdmin(false); DemoSession.setOnline(true)
        assertEquals(1L, state().revision); assertTrue(state().pending.isNotEmpty())
        assertNotEquals("queued", visible().catalog.medications.first().note)
    }
    @Test fun batchFailureRollsBackCatalogPendingAndHistory() {
        DemoSession.setAdmin(true); val before = state()
        assertThrows(IllegalStateException::class.java) {
            DemoSession.batch { editNote("first row"); error("injected later row failure") }
        }
        assertEquals(before, state())
        DemoSession.batch { editNote("committed") }
        assertEquals("committed", state().catalog.medications.first().note)
    }
    @Test fun invalidCatalogAndPharmacyChangesLeaveWorkspaceUntouched() {
        DemoSession.setAdmin(true); val before = state()
        assertThrows(IllegalArgumentException::class.java) { DemoSession.edit { c, _, _, _ -> c.copy(forms = emptyList()) } }
        assertThrows(AdminProblem::class.java) { DemoSession.edit { c, _, _, _ -> c.copy(pharmacy = c.pharmacy!!.copy(name = "other")) } }
        assertEquals(before, state())
    }
    @Test fun resetClearsWorkAndChangesGenerationWithoutChangingChosenRole() {
        DemoSession.setAdmin(true); editNote("temporary"); val generation = state().workspaceId
        DemoSession.rotateConnection(); DemoSession.reset()
        assertTrue(state().admin); assertTrue(state().pending.isEmpty()); assertTrue(state().history.isEmpty())
        assertEquals(state().initial, state().catalog); assertEquals(1, state().keyVersion)
        assertNotEquals(generation, state().workspaceId)
    }
    @Test fun samplePinChangeUsesCurrentPinAndResetRestoresInitialPin() {
        DemoSession.promote("١٢٣٤".toCharArray())
        assertThrows(AdminProblem::class.java) { DemoSession.changePin("9999".toCharArray(), "5678".toCharArray(), "5678".toCharArray()) }
        DemoSession.changePin("1234".toCharArray(), "5678".toCharArray(), "5678".toCharArray())
        DemoSession.setAdmin(false)
        assertThrows(AdminProblem::class.java) { DemoSession.promote("1234".toCharArray()) }
        DemoSession.promote("5678".toCharArray()); assertTrue(state().admin)
        DemoSession.reset(); DemoSession.setAdmin(false); DemoSession.promote("1234".toCharArray())
        assertTrue(state().admin)
    }
    @Test fun staleConnectionReplacementCannotTargetResetOrRotatedWorkspace() {
        DemoSession.setAdmin(true); val before = state()
        DemoSession.rotateConnection()
        assertThrows(AdminProblem::class.java) { DemoSession.replaceConnection(before.workspaceId, before.keyVersion) }
        val rotated = state(); DemoSession.reset()
        assertThrows(AdminProblem::class.java) { DemoSession.replaceConnection(rotated.workspaceId, rotated.keyVersion) }
    }
    @Test fun restoredWorkRevokesAdminAndRequiresReviewBeforePublishing() {
        DemoSession.setAdmin(true); editNote("restored draft"); val archive = state()
        DemoSession.restore(archive.catalog, archive.initial, archive.history, archive.workspaceId, archive.publishedAt, archive.revision)
        assertFalse(state().admin); assertTrue(state().restorePaused); assertNotEquals(archive.workspaceId, state().workspaceId)
        assertEquals(archive.catalog, visible().catalog)
        DemoSession.promote("1234".toCharArray())
        assertThrows(AdminProblem::class.java) { DemoSession.publish() }
        DemoSession.resumeRestore(); assertTrue(state().pending.isNotEmpty()); assertEquals(1L, state().revision)
        DemoSession.publish(); assertEquals("restored draft", state().initial.medications.first().note)
    }
}
