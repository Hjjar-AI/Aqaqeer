package com.aqaqeer.app.ui

import android.net.Uri
import com.aqaqeer.app.data.CatalogState
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
    private lateinit var state: CatalogState.Ready
    private val commands = ArrayDeque<suspend () -> Unit>()
    private val feedback = MutableStateFlow<UiFeedback?>(null)
    private var options: ReportOptions? = ReportOptions()
    private var route = "reports"
    private lateinit var target: Uri

    @Before fun setup() {
        installReportStrings()
        val catalog = F.catalog()
        val pharmacy = requireNotNull(catalog.pharmacy)
        snapshot = ReportSnapshot(pharmacy.pharmacyId, pharmacy.name, pharmacy.timezone, F.time, F.time,
            ReportRequest(pharmacyId = pharmacy.pharmacyId), catalog.sections, catalog.medications,
            catalog.availability, catalog.categories, catalog.forms)
        state = CatalogState.Ready(catalog, false, admin = true)
        reports = mockk()
        target = mockk()
        coEvery { reports.source(false) } returns ReportSource(catalog, false, F.time)
        coEvery { reports.source(true) } returns ReportSource(catalog, true, F.time)
        coEvery { reports.pdf(any(), any()) } just Runs
        coEvery { reports.excel(any(), any()) } just Runs
        controller = ExchangeController(mockk(), reports, { state }, { route }, { false }, feedback, {},
            { queueOffline, action ->
                assertFalse("Reports never queue publication work", queueOffline)
                commands.addLast(action)
            })
    }

    @After fun cleanup() = restoreReportStrings()

    private suspend fun next() = commands.removeFirst().invoke()

    private fun request() = controller.reportDraft.value.copy(options = requireNotNull(options))

    private suspend fun loadSource() {
        controller.loadReportSource()
        next()
        assertNotNull(controller.reportSource.value)
    }

    private fun openOptions() {
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        controller.moveReportStep(forward = true)
        assertEquals(ReportStep.SCOPE, controller.reportStep.value)
        controller.moveReportStep(forward = true)
        assertEquals(ReportStep.OPTIONS, controller.reportStep.value)
    }

    private suspend fun preparePreview() {
        val request = request()
        snapshot = snapshot.copy(request = request)
        coEvery { reports.capture(request) } returns snapshot
        controller.prepareReport(request) { options }
        next()
        assertSame(snapshot, controller.reportSnapshot.value)
        assertEquals(ReportStep.PREVIEW, controller.reportStep.value)
    }

    private suspend fun preview() {
        loadSource()
        openOptions()
        preparePreview()
    }

    @Test fun bothFormatsUseTheSameCapturedSnapshot(): Unit = runBlocking {
        preview()
        assertTrue(controller.beginReportSave(ReportFormat.PDF, options))
        controller.saveReport(target) { options }
        next()
        assertTrue(controller.beginReportSave(ReportFormat.XLSX, options))
        controller.saveReport(target) { options }
        next()
        coVerify(exactly = 1) { reports.capture(snapshot.request) }
        coVerify(exactly = 1) { reports.pdf(target, snapshot) }
        coVerify(exactly = 1) { reports.excel(target, snapshot) }
        assertNotNull(feedback.value)
    }

    @Test fun changingAnySelectionInvalidatesPreviewAndPendingDestination(): Unit = runBlocking {
        preview()
        assertTrue(controller.beginReportSave(ReportFormat.XLSX, options))
        controller.reviseReport(request().copy(query = "different"))
        assertEquals(ReportStep.OPTIONS, controller.reportStep.value)
        assertNull(controller.reportSnapshot.value)

        preparePreview()
        assertFalse(controller.beginReportSave(ReportFormat.PDF, options))
        controller.saveReport(target) { options }
        assertTrue(commands.isEmpty())
        coVerify(exactly = 0) { reports.excel(any(), any()) }
        assertTrue(controller.beginReportSave(ReportFormat.PDF, options))
    }

    @Test fun cancelledDestinationClearsSaveAndDoesNotOpenAWriter(): Unit = runBlocking {
        preview()
        assertTrue(controller.beginReportSave(ReportFormat.PDF, options))
        controller.saveReport(null) { options }
        controller.saveReport(target) { options }
        assertTrue(commands.isEmpty())
        assertNull(feedback.value)
        coVerify(exactly = 0) { reports.pdf(any(), any()) }
        assertTrue(controller.beginReportSave(ReportFormat.PDF, options))
    }

    @Test fun sourceConsentChangeClearsLoadedSourceAndPreview(): Unit = runBlocking {
        preview()
        controller.reviseReport(request().copy(includeUnpublished = true))
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        assertNull(controller.reportSource.value)
        assertNull(controller.reportSnapshot.value)
        coVerify(exactly = 0) { reports.source(true) }
        loadSource()
        assertTrue(requireNotNull(controller.reportSource.value).includeUnpublished)
        coVerify(exactly = 1) { reports.source(true) }
    }

    @Test fun sourceFailureNeverFallsBackToLocalData(): Unit = runBlocking {
        val error = IOException("published source unavailable")
        coEvery { reports.source(false) } throws error
        controller.loadReportSource()
        assertSame(error, assertThrows(IOException::class.java) { runBlocking { next() } })
        controller.moveReportStep(forward = true)
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        assertNull(controller.reportSource.value)
        coVerify(exactly = 0) { reports.source(true) }
    }

    @Test fun optionChangeDuringCaptureDiscardsLatePreview(): Unit = runBlocking {
        loadSource()
        openOptions()
        val request = request()
        val entered = CompletableDeferred<ReportRequest>()
        val gate = CompletableDeferred<ReportSnapshot>()
        coEvery { reports.capture(request) } coAnswers {
            entered.complete(firstArg())
            gate.await()
        }
        controller.prepareReport(request) { options }
        val work = launch(start = CoroutineStart.UNDISPATCHED) { next() }
        assertTrue("Capture reached the suspended repository call", entered.isCompleted)
        assertEquals(request, entered.await())
        options = requireNotNull(options).copy(paper = ReportPaper.A4)
        gate.complete(snapshot.copy(request = request))
        work.join()
        assertNull(controller.reportSnapshot.value)
        assertEquals(ReportStep.OPTIONS, controller.reportStep.value)
        coVerify(exactly = 1) { reports.capture(request) }
    }

    @Test fun pharmacyChangeClearsFiltersConsentSourcePreviewAndSave(): Unit = runBlocking {
        controller.reviseReport(ReportRequest(pharmacyId = F.pharmacyId, includeUnpublished = true,
            query = "private", medicationIds = setOf(state.catalog.medications.first().id)))
        preview()
        assertTrue(controller.beginReportSave(ReportFormat.XLSX, options))
        val newPharmacy = requireNotNull(state.catalog.pharmacy).copy(pharmacyId = F.otherPharmacyId)
        state = state.copy(catalog = state.catalog.copy(pharmacy = newPharmacy))
        controller.reconcileAccess(newPharmacy.pharmacyId to true)
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        assertEquals(ReportRequest(), controller.reportDraft.value)
        assertNull(controller.reportSource.value)
        assertNull(controller.reportSnapshot.value)
        controller.saveReport(target) { options }
        assertTrue(commands.isEmpty())
        coVerify(exactly = 0) { reports.excel(any(), any()) }
    }

    @Test fun lostAuthorizationClearsEvenUnpreparedSelections(): Unit = runBlocking {
        val draft = ReportRequest(pharmacyId = F.pharmacyId, query = "private", includeUnpublished = true)
        controller.reviseReport(draft)
        loadSource()
        openOptions()
        assertEquals(draft, controller.reportDraft.value)
        state = state.copy(admin = false)
        controller.reconcileAccess(F.pharmacyId to false)
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        assertEquals(ReportRequest(), controller.reportDraft.value)
        assertNull(controller.reportSnapshot.value)
    }

    @Test fun sourceConsentChangedWhileLoadingDiscardsLateSource(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<ReportSource>()
        coEvery { reports.source(false) } coAnswers {
            started.complete(Unit)
            gate.await()
        }
        controller.loadReportSource()
        val work = launch(start = CoroutineStart.UNDISPATCHED) { next() }
        assertTrue("Source loading reached the suspended repository call", started.isCompleted)
        controller.reviseReport(ReportRequest(pharmacyId = F.pharmacyId, includeUnpublished = true))
        gate.complete(ReportSource(state.catalog, false, F.time))
        work.join()
        assertNull(controller.reportSource.value)
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        assertTrue(controller.reportDraft.value.includeUnpublished)
    }

    @Test fun failedOrCancelledWriterCannotAnnounceSuccess(): Unit = runBlocking {
        preview()
        for (error in listOf(IOException("provider close failed"), CancellationException("cancelled"))) {
            assertTrue(controller.beginReportSave(ReportFormat.XLSX, options))
            coEvery { reports.excel(target, snapshot) } throws error
            controller.saveReport(target) { options }
            assertSame(error, assertThrows(error.javaClass) { runBlocking { next() } })
            assertNull(feedback.value)
            controller.saveReport(target) { options }
            assertTrue(commands.isEmpty())
        }
        coVerify(exactly = 2) { reports.excel(target, snapshot) }
    }

    @Test fun completedExportDoesNotShowFeedbackOnAnotherScreen(): Unit = runBlocking {
        preview()
        assertTrue(controller.beginReportSave(ReportFormat.XLSX, options))
        controller.saveReport(target) { options }
        route = "home"
        next()
        coVerify(exactly = 1) { reports.excel(target, snapshot) }
        assertNull(feedback.value)
    }

    @Test fun accessLostAfterPickerResultBlocksQueuedWriter(): Unit = runBlocking {
        preview()
        assertTrue(controller.beginReportSave(ReportFormat.PDF, options))
        controller.saveReport(target) { options }
        assertEquals(1, commands.size)
        state = state.copy(admin = false)
        next()
        coVerify(exactly = 0) { reports.pdf(any(), any()) }
        assertNull(feedback.value)

        controller.reconcileAccess(F.pharmacyId to false)
        assertEquals(ReportStep.SOURCE, controller.reportStep.value)
        assertNull(controller.reportSnapshot.value)
        assertFalse(controller.beginReportSave(ReportFormat.PDF, options))
    }

    @Test fun compatibilityMaintenanceAndReaderTransitionsBlockQueuedPreview(): Unit = runBlocking {
        loadSource()
        openOptions()
        val ready = state
        val request = request()
        for (blocked in listOf(ready.copy(admin = false), ready.copy(requiresUpdate = true),
                ready.copy(maintenancePaused = true))) {
            controller.prepareReport(request) { options }
            state = blocked
            next()
            assertNull(controller.reportSnapshot.value)
            assertEquals(ReportStep.OPTIONS, controller.reportStep.value)
            state = ready
        }
        coVerify(exactly = 0) { reports.capture(any()) }
    }

    @Test fun eligibilityIsRecheckedAfterCapture(): Unit = runBlocking {
        loadSource()
        openOptions()
        val ready = state
        val request = request()
        for (blocked in listOf(ready.copy(admin = false), ready.copy(requiresUpdate = true),
                ready.copy(maintenancePaused = true))) {
            coEvery { reports.capture(request) } coAnswers {
                state = blocked
                snapshot
            }
            controller.prepareReport(request) { options }
            assertThrows(IllegalStateException::class.java) { runBlocking { next() } }
            assertNull(controller.reportSnapshot.value)
            assertEquals(ReportStep.OPTIONS, controller.reportStep.value)
            state = ready
        }
        coVerify(exactly = 3) { reports.capture(request) }
    }

    @Test fun backwardNavigationKeepsScopeAndFailedPreparationCanRetry(): Unit = runBlocking {
        loadSource()
        openOptions()
        controller.moveReportStep(forward = false)
        assertEquals(ReportStep.SCOPE, controller.reportStep.value)
        val scoped = request().copy(query = "aspirin", sectionIds = setOf(state.catalog.sections.first().id))
        controller.reviseReport(scoped)
        controller.moveReportStep(forward = false)
        openOptions()
        assertEquals(scoped, request())

        val error = IOException("preview failed")
        coEvery { reports.capture(scoped) } throws error
        controller.prepareReport(scoped) { options }
        assertSame(error, assertThrows(IOException::class.java) { runBlocking { next() } })
        assertEquals(ReportStep.OPTIONS, controller.reportStep.value)
        assertNull(controller.reportSnapshot.value)
        assertFalse(controller.beginReportSave(ReportFormat.PDF, options))
        preparePreview()
        coVerify(exactly = 2) { reports.capture(scoped) }
    }

    @Test fun pendingOrMismatchedOptionsBlockPreparationAndSave(): Unit = runBlocking {
        loadSource()
        openOptions()
        val request = request()
        options = null
        controller.prepareReport(request) { options }
        next()
        coVerify(exactly = 0) { reports.capture(any()) }
        assertEquals(ReportStep.OPTIONS, controller.reportStep.value)

        options = request.options
        preparePreview()
        val different = request.options.copy(landscape = !request.options.landscape)
        assertFalse(controller.beginReportSave(ReportFormat.PDF, different))
        assertTrue(controller.beginReportSave(ReportFormat.PDF, options))
        controller.saveReport(target) { options }
        options = different
        next()
        coVerify(exactly = 0) { reports.pdf(any(), any()) }
        assertNull(feedback.value)
    }
}
