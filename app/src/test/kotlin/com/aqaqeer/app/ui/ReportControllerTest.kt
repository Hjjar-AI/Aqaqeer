package com.aqaqeer.app.ui

import android.net.Uri
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.reports.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

class ReportControllerTest {
    private lateinit var reports: ReportRepository
    private lateinit var controller: ExchangeController
    private lateinit var snapshot: ReportSnapshot
    private lateinit var state: CatalogState
    private val commands = ArrayDeque<suspend () -> Unit>()
    private val feedback = MutableStateFlow<UiFeedback?>(null)
    private var route = "reports"
    private lateinit var target: Uri
    @Before fun setup() {
        installReportStrings()
        val c = F.catalog()
        snapshot = ReportSnapshot(F.pharmacyId, c.pharmacy!!.name, "Asia/Damascus", F.time, F.time,
            ReportRequest(), c.sections, c.medications, c.availability, c.categories, c.forms)
        state = CatalogState.Ready(c, false, admin = true)
        reports = mockk(); target = mockk()
        coEvery { reports.capture(any()) } returns snapshot
        coEvery { reports.source(false) } returns ReportSource(c, false, F.time)
        coEvery { reports.pdf(any(), any()) } just Runs
        coEvery { reports.excel(any(), any()) } just Runs
        controller = ExchangeController(mockk(), reports, { state }, { route }, { false }, feedback, {},
            { queueOffline, action -> assertFalse("Reports never queue publication work", queueOffline); commands.addLast(action) })
    }
    @After fun cleanup() = restoreReportStrings()
    private suspend fun next() = commands.removeFirst().invoke()
    private suspend fun preview() { controller.prepareReport(ReportRequest()); next(); assertSame(snapshot, controller.reportSnapshot.value) }

    @Test fun bothFormatsUseTheSameCapturedSnapshot(): Unit = runBlocking {
        preview()
        controller.beginReportSave(ReportFormat.PDF); controller.saveReport(target); next()
        controller.beginReportSave(ReportFormat.XLSX); controller.saveReport(target); next()
        coVerify(exactly = 1) { reports.capture(any()) }
        coVerify(exactly = 1) { reports.pdf(target, snapshot) }
        coVerify(exactly = 1) { reports.excel(target, snapshot) }
        assertNotNull(feedback.value)
    }
    @Test fun changingAnySelectionInvalidatesPreviewAndPendingDestination(): Unit = runBlocking {
        preview(); controller.beginReportSave(ReportFormat.XLSX)
        controller.reviseReport(ReportRequest(query = "different"))
        assertNull(controller.reportSnapshot.value)
        controller.saveReport(target)
        assertTrue(commands.isEmpty())
        coVerify(exactly = 0) { reports.excel(any(), any()) }
    }
    @Test fun cancelledDestinationClearsSaveAndDoesNotOpenAWriter(): Unit = runBlocking {
        preview(); controller.beginReportSave(ReportFormat.PDF)
        controller.saveReport(null); controller.saveReport(target)
        assertTrue(commands.isEmpty()); assertNull(feedback.value)
        coVerify(exactly = 0) { reports.pdf(any(), any()) }
    }
    @Test fun sourceConsentChangeClearsLoadedSourceAndPreview(): Unit = runBlocking {
        controller.loadReportSource(); next(); assertNotNull(controller.reportSource.value)
        preview()
        controller.reviseReport(ReportRequest(includeUnpublished = true))
        assertNull(controller.reportSource.value); assertNull(controller.reportSnapshot.value)
        coVerify(exactly = 0) { reports.source(true) }
    }
    @Test fun sourceFailureNeverFallsBackToLocalData(): Unit = runBlocking {
        val error = IOException("published source unavailable")
        coEvery { reports.source(false) } throws error
        controller.loadReportSource()
        assertSame(error, assertThrows(IOException::class.java) { runBlocking { next() } })
        assertNull(controller.reportSource.value)
        coVerify(exactly = 0) { reports.source(true) }
    }
    @Test fun optionChangeDuringCaptureDiscardsLatePreview(): Unit = runBlocking {
        val gate = CompletableDeferred<ReportSnapshot>()
        coEvery { reports.capture(any()) } coAnswers { gate.await() }
        controller.prepareReport(ReportRequest())
        val work = launch(start = CoroutineStart.UNDISPATCHED) { next() }
        controller.reviseReport(ReportRequest(options = ReportOptions(paper = ReportPaper.A4)))
        gate.complete(snapshot); work.join()
        assertNull(controller.reportSnapshot.value)
    }
    @Test fun pharmacyChangeClearsFiltersConsentSourcePreviewAndSave(): Unit = runBlocking {
        controller.loadReportSource(); next(); preview()
        controller.reviseReport(ReportRequest(pharmacyId = F.pharmacyId, includeUnpublished = true, query = "private", medicationIds = setOf(F.publishId)))
        preview(); controller.beginReportSave(ReportFormat.XLSX)
        controller.reconcileAccess(F.otherPharmacyId to true)
        assertEquals(ReportRequest(), controller.reportDraft.value)
        assertNull(controller.reportSource.value); assertNull(controller.reportSnapshot.value)
        controller.saveReport(target); assertTrue(commands.isEmpty())
    }
    @Test fun lostAuthorizationClearsEvenUnpreparedSelections() {
        controller.reviseReport(ReportRequest(query = "private", includeUnpublished = true))
        controller.reconcileAccess(F.pharmacyId to false)
        assertEquals(ReportRequest(), controller.reportDraft.value)
        assertNull(controller.reportSnapshot.value)
    }
    @Test fun sourceConsentChangedWhileLoadingDiscardsLateSource(): Unit = runBlocking {
        val gate = CompletableDeferred<ReportSource>()
        coEvery { reports.source(false) } coAnswers { gate.await() }
        controller.loadReportSource()
        val work = launch(start = CoroutineStart.UNDISPATCHED) { next() }
        controller.reviseReport(ReportRequest(includeUnpublished = true))
        gate.complete(ReportSource(F.catalog(), false, F.time)); work.join()
        assertNull(controller.reportSource.value)
    }
    @Test fun failedOrCancelledWriterCannotAnnounceSuccess(): Unit = runBlocking {
        for (error in listOf(IOException("provider close failed"), CancellationException("cancelled"))) {
            preview(); controller.beginReportSave(ReportFormat.XLSX)
            coEvery { reports.excel(any(), any()) } throws error
            controller.saveReport(target)
            assertSame(error, assertThrows(error.javaClass) { runBlocking { next() } })
            assertNull(feedback.value)
            controller.saveReport(target); assertTrue(commands.isEmpty())
        }
    }
    @Test fun completedExportDoesNotShowFeedbackOnAnotherScreen(): Unit = runBlocking {
        preview(); controller.beginReportSave(ReportFormat.XLSX)
        controller.saveReport(target); route = "home"; next()
        coVerify(exactly = 1) { reports.excel(target, snapshot) }
        assertNull(feedback.value)
    }
    @Test fun compatibilityMaintenanceAndReaderTransitionsBlockQueuedPreview(): Unit = runBlocking {
        val ready = state as CatalogState.Ready
        for (blocked in listOf(ready.copy(admin = false), ready.copy(requiresUpdate = true), ready.copy(maintenancePaused = true))) {
            state = ready
            controller.prepareReport(ReportRequest())
            state = blocked
            assertThrows(IllegalStateException::class.java) { runBlocking { next() } }
            assertNull(controller.reportSnapshot.value)
        }
    }
}
