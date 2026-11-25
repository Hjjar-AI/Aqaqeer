package com.aqaqeer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.*
import com.aqaqeer.app.maintenance.*
import com.aqaqeer.app.reports.*
import com.aqaqeer.app.security.files.EnvelopeReader
import com.aqaqeer.app.sync.SnapshotReadClient
import com.aqaqeer.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class ViewModelInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var p: SuiteProfile
    private lateinit var vm: CatalogViewModel
    private lateinit var remote: SuiteRemote
    private val models = ViewModelStore()
    private val saved = SavedStateHandle()
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    @Before fun setup(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO); p = SuiteProfile(); p.create(); remote = SuiteRemote()
        val reads = SnapshotReadClient(remote, EnvelopeReader(p.root))
        val prefs = DisplayPreferences(p.context); val output = DocumentOutput(p.context)
        val imports = CsvImportRepository(p.admin,output,p.context)
        val reports = ReportRepository(p.admin,output,PdfReports(p.context))
        val maintenance = MaintenanceCoordinator(MaintenanceStore(p.secure),MaintenanceStage(p.context),p.admin,reads,remote,remote,remote,p.requirements,p.backups,p.context)
        main {
            val owner = object : ViewModelStoreOwner { override val viewModelStore = models }
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CatalogViewModel(p.repository,prefs,saved,ConnectionImportRepository(p.context,p.repository,p.secure,reads,p.requirements),
                        SnapshotRefreshRepository(p.factory,p.secure,reads,p.requirements,p.context),HistoryRepository(p.factory),imports,reports,
                        p.admin,p.sharing(remote),p.backups,maintenance,PinChanges(p.admin,MaintenanceStore(p.secure)),p.context) as T
            }
            vm = ViewModelProvider(owner,factory)[CatalogViewModel::class.java]
        }
        withTimeout(15000) { vm.state.first { it.catalog is CatalogState.Ready } }
    }
    @After fun cleanup() { if (::vm.isInitialized) main { models.clear() }; if (::p.isInitialized) p.close() }

    @Test fun reportAccordionRevealsOnlyTheCurrentStepActions(): Unit = runBlocking {
        val medication = p.medication("Report accordion fixture")
        p.publish(remote)
        val ready = withTimeout(15_000) {
            vm.state.first { state -> (state.catalog as? CatalogState.Ready)?.let { it.admin && it.publishedAt != null } == true }
                .catalog as CatalogState.Ready
        }
        main { vm.navigate(Route.Reports) }
        val exports = mutableListOf<ReportFormat>()
        compose.setContent {
            AqaqeerTheme(ThemeChoice.LIGHT) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(320.dp)) { ReportsScreen(ready, vm) { exports.add(it) } }
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        fun button(id: Int): SemanticsNodeInteraction {
            val matcher = hasText(uiText(id)) and hasClickAction()
            list.performScrollToNode(matcher)
            return compose.onNode(matcher).assertIsDisplayed()
        }
        fun step(title: Int, status: Int) {
            val text = hasText(uiText(title))
            list.performScrollToNode(text)
            compose.onNode(text).assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, uiText(status)))
        }
        val next = R.string.report_next_step_action
        val previous = R.string.report_previous_step_action
        val prepare = R.string.exchange_report_prepare_preview_action
        val pdf = R.string.exchange_report_save_pdf_action
        val excel = R.string.report_save_excel

        compose.waitUntil(15_000) {
            vm.reportSource.value != null && vm.reportOptions.value != null && !vm.adminBusy.value
        }
        step(R.string.report_source_step, R.string.shared_step_current)
        step(R.string.report_scope_step, R.string.shared_step_pending)
        compose.onNodeWithText(uiText(previous)).assertDoesNotExist()
        button(next).assertIsEnabled().performClick()

        step(R.string.report_source_step, R.string.shared_step_completed)
        compose.onNodeWithText(uiText(R.string.report_summary_source)).assertDoesNotExist()
        step(R.string.report_scope_step, R.string.shared_step_current)
        button(next).assertIsEnabled().performClick()
        step(R.string.report_scope_step, R.string.shared_step_completed)
        compose.onNodeWithText(uiText(R.string.report_sections)).assertDoesNotExist()
        step(R.string.report_options_step, R.string.shared_step_current)
        compose.onNodeWithText(uiText(excel)).assertDoesNotExist()
        compose.onNodeWithText(uiText(pdf)).assertDoesNotExist()

        button(previous).performClick()
        step(R.string.report_scope_step, R.string.shared_step_current)
        button(next).performClick()
        button(prepare).assertIsEnabled().performClick()
        compose.waitUntil(15_000) { vm.reportStep.value == ReportStep.PREVIEW && !vm.adminBusy.value }
        assertEquals(listOf(medication.id), vm.reportSnapshot.value?.medications?.map { it.id })
        step(R.string.exchange_report_preview_title, R.string.shared_step_current)
        button(excel).assertIsEnabled().performClick()
        button(pdf).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(ReportFormat.XLSX, ReportFormat.PDF), exports) }

        button(previous).performClick()
        step(R.string.report_options_step, R.string.shared_step_current)
        step(R.string.exchange_report_preview_title, R.string.shared_step_pending)
        compose.onNodeWithText(uiText(pdf)).assertDoesNotExist()
        compose.onNodeWithText(uiText(excel)).assertDoesNotExist()
    }

    @Test fun adminHubKeepsOneLeaveActionAndShowsOnlyToolsPermittedByState(): Unit = runBlocking {
        val shared = p.ready().copy(admin = true, lifecycle = "SHARED")
        var ready by mutableStateOf(shared.copy(admin = false))
        compose.setContent {
            AqaqeerTheme(ThemeChoice.DARK) {
                Box(Modifier.width(320.dp)) { FormPage { AdminHubContent(ready, vm) } }
            }
        }
        val leave = compose.onNodeWithText(uiText(R.string.navigation_leave_pharmacy_title))
        val add = compose.onNodeWithText(uiText(R.string.navigation_add_medication_title))
        val reports = compose.onNodeWithText(uiText(R.string.exchange_reports_navigation_label))
        val export = compose.onNodeWithText(uiText(R.string.navigation_connection_export_title))
        val backup = compose.onNodeWithText(uiText(R.string.navigation_backup_title))
        compose.onNodeWithText(uiText(R.string.navigation_promote_admin_title)).assertIsDisplayed()
        add.assertDoesNotExist()
        backup.assertDoesNotExist()
        leave.performScrollTo().assertIsDisplayed()

        compose.runOnIdle { ready = shared.copy(maintenancePaused = true) }
        compose.onNodeWithText(uiText(R.string.maintenance_review_first_action)).performScrollTo().assertIsEnabled()
        add.assertDoesNotExist()
        reports.assertDoesNotExist()
        export.assertDoesNotExist()
        backup.performScrollTo().assertIsEnabled()
        // This scroll/assertion also requires a unique leave node.
        leave.performScrollTo().assertIsDisplayed()

        compose.runOnIdle { ready = shared.copy(requiresUpdate = true) }
        compose.onNodeWithText(uiText(R.string.publication_review_retained_work_action)).performScrollTo().assertIsEnabled()
        add.assertDoesNotExist()
        export.assertDoesNotExist()
        leave.performScrollTo().assertIsDisplayed()

        compose.runOnIdle { ready = shared.copy(restorePaused = true) }
        compose.onNodeWithText(uiText(R.string.backup_complete_restore_review_action)).performScrollTo().assertIsEnabled()
        add.performScrollTo().assertIsEnabled()
        export.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(uiText(R.string.admin_hub_connection_after_restore)).assertIsDisplayed()
        compose.runOnIdle { ready = shared }
        export.performScrollTo().assertIsEnabled()
    }

    @Test fun adminChecklistOpensSetupGuideAndBackReturnsToTools(): Unit = runBlocking {
        val ready = p.ready()
        assertTrue(ready.admin)
        assertEquals("LOCAL_DRAFT", ready.lifecycle)
        assertTrue(ready.catalog.medications.isEmpty())
        assertFalse(ready.hasPublicationConnection || ready.hasBackup)
        assertNull(ready.publishedAt)
        main { vm.navigate(Route.Admin) }
        val route = saved.getStateFlow("route", "home")
        compose.setContent {
            AqaqeerTheme(ThemeChoice.LIGHT) {
                Box(Modifier.width(320.dp)) {
                    val destination by route.collectAsState()
                    key(destination) {
                        when (RouteStorage.decode(destination)) {
                            Route.Admin -> FormPage { AdminHubContent(ready, vm) }
                            Route.Setup -> FormPage { TelegramSetup(vm, busy = false, empty = true) }
                            else -> Unit
                        }
                    }
                }
            }
        }
        val progress = compose.onNodeWithText(uiText(R.string.admin_hub_checklist_progress,
            uiText(R.string.review_setup_checklist), 0, 4))
        val connect = compose.onNodeWithText(uiText(R.string.review_check_connection))
        progress.assertIsDisplayed()
        connect.assertDoesNotExist()
        progress.performClick()
        connect.performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, uiText(R.string.shared_step_pending)))
            .performClick()
        val guide = compose.onNodeWithText(uiText(R.string.admin_setup_instructions_heading))
        val instructions = compose.onNodeWithText(uiText(R.string.connection_create_bot_step_help))
        compose.runOnIdle { assertEquals("setup", route.value) }
        instructions.assertDoesNotExist()
        guide.performScrollTo().performClick()
        instructions.performScrollTo().assertIsDisplayed()
        val discoverLabel = hasText(uiText(R.string.connection_discover_group_action))
        val stepDescription = listOf(uiText(R.string.shared_step_current), uiText(R.string.shared_step_position, 1, 3),
            uiText(R.string.shared_collapsed_state_label)).joinToString(" · ")
        compose.onNode(discoverLabel and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, stepDescription))
            .performScrollTo().assertIsDisplayed()
        val discover = compose.onNode(discoverLabel and SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        discover.performScrollTo().assertIsNotEnabled()
        main { vm.back() }
        compose.runOnIdle { assertEquals("admin", route.value) }
        progress.assertIsDisplayed()
    }

    @Test fun backupReaderHasNoExportOrReplacementControls(): Unit = runBlocking {
        val ready = p.ready().copy(admin = false)
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { BackupScreen(AppUiState(catalog = ready), vm) } }
        compose.onNodeWithText(uiText(R.string.backup_restore_admin_only_message)).assertIsDisplayed()
        compose.onNodeWithTag(uiText(R.string.backup_passphrase_label)).assertDoesNotExist()
        compose.onNodeWithText(uiText(R.string.backup_choose_restore_file_action)).assertDoesNotExist()
    }

    @Test fun backupPassphraseUsesUnicodeCodePointsAndScreenDisposalClearsSecrets(): Unit = runBlocking {
        val ready = p.ready(); var visible by mutableStateOf(true)
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) {
            if (visible) BackupScreen(AppUiState(catalog = ready), vm)
        } }
        val password = uiText(R.string.backup_passphrase_label)
        val confirmation = uiText(R.string.backup_passphrase_confirmation_label)
        val action = uiText(R.string.backup_choose_export_destination_action)
        compose.onNodeWithTag(password).performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)).performTextInput("😀".repeat(7))
        compose.onNodeWithTag(confirmation).performScrollTo().performTextInput("😀".repeat(7))
        compose.onNodeWithText(action).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(password).performScrollTo().performTextInput("😀")
        compose.onNodeWithTag(confirmation).performScrollTo().performTextInput("😀")
        compose.onNodeWithText(action).performScrollTo().assertIsEnabled()
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("backup_screen").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag(password).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag(confirmation).performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(action).performScrollTo().assertIsNotEnabled()
    }

    @Test fun backupExportRemainsDisabledWhileAnotherAdminCommandOwnsTheBusyGate(): Unit = runBlocking {
        val ready = p.ready(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        compose.setContent { AqaqeerTheme(ThemeChoice.LIGHT) { BackupScreen(AppUiState(catalog = ready), vm) } }
        compose.onNodeWithTag(uiText(R.string.backup_passphrase_label)).performScrollTo().performTextInput("public fixture")
        compose.onNodeWithTag(uiText(R.string.backup_passphrase_confirmation_label)).performScrollTo().performTextInput("public fixture")
        compose.onNodeWithText(uiText(R.string.backup_choose_export_destination_action)).performScrollTo().assertIsEnabled()
        main { vm.command(queueOffline = false) { entered.complete(Unit); release.await() } }
        try {
            withTimeout(10_000) { entered.await() }
            compose.onNodeWithText(uiText(R.string.backup_choose_export_destination_action)).performScrollTo().assertIsNotEnabled()
        } finally { release.complete(Unit) }
        withTimeout(10_000) { vm.adminBusy.first { !it } }
        compose.onNodeWithText(uiText(R.string.backup_choose_export_destination_action)).performScrollTo().assertIsEnabled()
    }

    @Test fun restorePickerRequiresExactPharmacyAndAcknowledgmentAtLargeRtlText(): Unit = runBlocking {
        p.medication()
        val ready = p.ready()
        assertTrue(ready.pendingCount > 0)
        val name = requireNotNull(ready.catalog.pharmacy).name
        var launchedRequestCode = -1
        var intent: Intent? = null
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                input: I, options: ActivityOptionsCompat?) {
                intent = contract.createIntent(p.context, input)
                launchedRequestCode = requestCode
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f),
                LocalActivityResultRegistryOwner provides owner) {
                AqaqeerTheme(ThemeChoice.LIGHT) {
                    Box(Modifier.width(320.dp)) { BackupScreen(AppUiState(catalog = ready), vm) }
                }
            }
        }
        val select = compose.onNodeWithText(uiText(R.string.backup_choose_restore_file_action))
        select.performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent?.action)
            assertTrue(launchedRequestCode >= 0)
            assertTrue(registry.dispatchResult(launchedRequestCode, Activity.RESULT_OK,
                Intent().setData(Uri.parse("content://com.aqaqeer.fixture/selected.aqbak"))))
        }
        val validate = compose.onNodeWithText(uiText(R.string.backup_validate_restore_action))
        val typedName = compose.onNodeWithTag(uiText(R.string.backup_current_pharmacy_confirmation_label))
        val warning = uiText(R.string.backup_replace_local_data_warning_format,
            MedicationPresentation.displayDigits(ready.pendingCount.toString()))
        compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(uiText(R.string.backup_restore_passphrase_label))
            .performScrollTo().performTextInput("fixture passphrase")
        typedName.performScrollTo().performTextInput(name)
        validate.performScrollTo().assertIsNotEnabled()
        typedName.performScrollTo().performTextInput(" مختلفة")
        compose.onNodeWithText(uiText(R.string.backup_replace_local_data_acknowledgement_label))
            .performScrollTo().performClick()
        validate.performScrollTo().assertIsNotEnabled()
        typedName.performScrollTo().performTextClearance()
        typedName.performTextInput(name)
        validate.performScrollTo().assertIsEnabled()

        compose.onNodeWithText(uiText(R.string.backup_change_restore_file_action)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(registry.dispatchResult(launchedRequestCode, Activity.RESULT_CANCELED, null)) }
        validate.assertDoesNotExist()
        select.performScrollTo().assertIsDisplayed()
        assertEquals(ready.pendingCount, p.admin.transaction { it.pendingCount() })
    }

    @Test fun searchDebouncesAndNavigationFiltersPersistInSavedState(): Unit = runBlocking {
        main { vm.setQuery("new query"); vm.toggleStatus(AvailabilityStatus.OUT); vm.navigate("settings") }
        val state = withTimeout(10000) { vm.state.first { it.appliedQuery == "new query" && it.route == "settings" } }
        assertEquals(setOf(AvailabilityStatus.OUT),state.filters.statuses)
        assertEquals("new query",saved.get<String>("query"))
        main { vm.setQuery(""); vm.clearFilters(); vm.back() }
        withTimeout(10000) { vm.state.first { it.appliedQuery.isEmpty() && it.route == "home" && it.filters.statuses.isEmpty() } }
    }
    @Test fun busyCommandRejectsDuplicateAndImmediatelyErasesTransferredSecrets(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var duplicate = false
        main { vm.command(queueOffline = false) { entered.complete(Unit); release.await() } }
        withTimeout(10000) { entered.await() }; val secret = "passphrase".toCharArray()
        main { vm.secretCommand(null,secret,queueOffline = false) { duplicate = true } }
        assertTrue(secret.all { it == '\u0000' }); assertFalse(duplicate)
        release.complete(Unit)
        withTimeout(10000) { vm.adminBusy.first { !it } }
    }
    @Test fun failingOrCancelledSecretCommandsEraseInputsAndReleaseBusyState(): Unit = runBlocking {
        val secret = "passphrase".toCharArray()
        main { vm.secretCommand(null,secret,queueOffline = false) { throw IllegalStateException("private diagnostic") } }
        withTimeout(10000) { vm.adminBusy.first { !it } }
        assertTrue(secret.all { it == '\u0000' }); assertNotNull(vm.adminMessage.value)
        assertFalse(vm.adminMessage.value!!.contains("private diagnostic"))
        val entered = CompletableDeferred<Unit>(); val cancelled = "other secret".toCharArray()
        main { vm.secretCommand(null,cancelled,queueOffline = false) { entered.complete(Unit); awaitCancellation() } }
        withTimeout(10000) { entered.await() }; main { vm.cancelAdminOperation() }
        withTimeout(10000) { vm.adminBusy.first { !it } }
        assertTrue(cancelled.all { it == '\u0000' })
    }
    @Test fun bottomPlacementPreferencesPersistIndependentlyWithoutCatalogOrPendingChanges(): Unit = runBlocking {
        val preferences = DisplayPreferences(p.context); val original = preferences.home.first()
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) }
        try {
            for ((sections, search) in listOf(true to true, true to false, false to true, false to false)) {
                main { vm.setSectionsAtBottom(sections); vm.setSearchAtBottom(search) }
                val changed = withTimeout(10000) { preferences.home.first { it.sectionsAtBottom == sections && it.searchAtBottom == search } }
                assertEquals(original.copy(sectionsAtBottom = sections, searchAtBottom = search), changed)
                assertEquals(changed, DisplayPreferences(p.context).home.first())
                withTimeout(10000) { vm.state.first { it.homePreferences == changed } }
            }
            assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) })
        } finally {
            preferences.setSectionsAtBottom(original.sectionsAtBottom); preferences.setSearchAtBottom(original.searchAtBottom)
        }
    }

    private fun taxonomy() {
        main { vm.navigate("taxonomy") }
        runBlocking { withTimeout(10000) { vm.state.first { it.route == "taxonomy" } } }
        compose.setContent {
            val state by vm.state.collectAsState()
            AqaqeerTheme(ThemeChoice.LIGHT) { PharmacyAdminScreen(state, vm) }
        }
    }
    private var taxonomyType = R.string.catalog_entity_section_label
    private fun chooseType(label: Int) {
        compose.onNodeWithText(uiText(R.string.catalog_taxonomy_type_label)).performScrollTo().performClick()
        compose.onNodeWithText(uiText(label)).performClick()
        taxonomyType = label
    }
    private fun row(name: String): SemanticsNodeInteraction {
        if (taxonomyType != R.string.catalog_entity_section_label) {
            compose.onNode(hasText(uiText(R.string.catalog_taxonomy_items_heading)) and hasClickAction())
                .performScrollTo().performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(name, substring = true))
            return compose.onNodeWithText(name, substring = true)
        }
        return compose.onNodeWithText(name, substring = true).performScrollTo()
    }
    private fun confirmDeletion() = compose.onNodeWithText(uiText(R.string.catalog_taxonomy_delete_confirmation_label)).performScrollTo()
    private fun deleteButton() = compose.onNodeWithText(uiText(R.string.catalog_confirm_delete_action)).performScrollTo()

    @Test fun taxonomyDeleteConfirmationDoesNotCarryAcrossRowsTypesOrNew(): Unit = runBlocking {
        p.admin.saveTaxonomy("category", null, "تصنيف ألف", null)
        p.admin.saveTaxonomy("category", null, "تصنيف باء", null)
        p.admin.saveTaxonomy("form", null, "شكل خاص", null)
        withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.categories?.size == 3 } }
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) }
        taxonomy(); chooseType(R.string.catalog_entity_category_label)
        row("تصنيف ألف").performClick(); confirmDeletion().performClick(); deleteButton().assertIsEnabled()
        row("تصنيف باء").performClick(); confirmDeletion().assertIsOff(); deleteButton().assertIsNotEnabled()
        confirmDeletion().performClick()
        compose.onNodeWithText(uiText(R.string.catalog_add_taxonomy_action)).performScrollTo().performClick()
        compose.onNodeWithText(uiText(R.string.catalog_taxonomy_delete_confirmation_label)).assertDoesNotExist()
        row("تصنيف ألف").performClick(); deleteButton().assertIsNotEnabled(); confirmDeletion().performClick()
        chooseType(R.string.catalog_entity_dosage_form_label)
        row("شكل خاص").performClick(); confirmDeletion().assertIsOff(); deleteButton().assertIsNotEnabled()
        assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) })
    }

    @Test fun blankSectionsDefaultActiveAfterTypeChangeNewAndSuccessfulSave(): Unit = runBlocking {
        p.admin.saveTaxonomy("section", null, "قسم متوقف", null, active = false)
        withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.sections?.size == 2 } }
        taxonomy(); row("قسم متوقف").performClick()
        compose.onNodeWithText(uiText(R.string.catalog_active_section_label)).performScrollTo().assertIsOff()
        chooseType(R.string.catalog_entity_category_label); chooseType(R.string.catalog_entity_section_label)
        compose.onNodeWithText(uiText(R.string.catalog_active_section_label)).performScrollTo().assertIsOn()
        row("قسم متوقف").performClick()
        compose.onNodeWithText(uiText(R.string.catalog_add_taxonomy_action)).performScrollTo().performClick()
        compose.onNodeWithText(uiText(R.string.catalog_active_section_label)).performScrollTo().assertIsOn()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("قسم جديد")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText(uiText(R.string.catalog_save_taxonomy_action)).performScrollTo().performClick()
        val saved = withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.sections?.any { section -> section.nameAr == "قسم جديد" } == true } }
        assertTrue((saved.catalog as CatalogState.Ready).catalog.sections.single { it.nameAr == "قسم جديد" }.isActive)
        compose.onNodeWithText(uiText(R.string.catalog_active_section_label)).performScrollTo().assertIsOn()
        compose.onNodeWithText(uiText(R.string.catalog_taxonomy_delete_confirmation_label)).assertDoesNotExist()
    }

    @Test fun sectionTypedNameClearsOnSelectionAndSuccessfulDeleteResetsForm(): Unit = runBlocking {
        p.admin.saveTaxonomy("section", null, "قسم متوقف", null, active = false)
        withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.sections?.size == 2 } }
        val original = p.ready().catalog.sections.first { it.isActive }.nameAr
        taxonomy(); row("قسم متوقف").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("قسم متوقف")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); confirmDeletion().performClick()
        row(original).performClick(); confirmDeletion().assertIsOff()
        assertEquals("", compose.onAllNodes(hasSetTextAction())[2].fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        row("قسم متوقف").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("قسم متوقف")
        androidx.test.espresso.Espresso.closeSoftKeyboard(); confirmDeletion().performClick(); deleteButton().performClick()
        withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.sections?.none { section -> section.nameAr == "قسم متوقف" } == true } }
        compose.onNodeWithText(uiText(R.string.catalog_active_section_label)).performScrollTo().assertIsOn()
        compose.onNodeWithText(uiText(R.string.catalog_taxonomy_delete_confirmation_label)).assertDoesNotExist()
    }
    @Test fun presentationPreferenceEmitsAndPersistsWithoutChangingCatalog(): Unit = runBlocking {
        val preferences = DisplayPreferences(p.context); val before = preferences.home.first(); val catalog = p.ready().catalog
        try {
            preferences.home.test {
                awaitItem(); main { vm.setSecondaryHijri(!before.secondaryHijri) }
                val changed = withTimeout(10000) { preferences.home.first { it.secondaryHijri != before.secondaryHijri } }
                assertEquals(!before.secondaryHijri,changed.secondaryHijri)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(!before.secondaryHijri,DisplayPreferences(p.context).home.first().secondaryHijri)
            assertEquals(catalog,p.ready().catalog)
        } finally { preferences.setSecondaryHijri(before.secondaryHijri) }
    }
}
