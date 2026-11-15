package com.aqaqeer.app

import androidx.lifecycle.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
                        SnapshotRefreshRepository(p.factory,p.secure,reads,p.requirements),HistoryRepository(p.factory),imports,reports,
                        p.admin,p.sharing(remote),p.backups,maintenance,PinChanges(p.admin,MaintenanceStore(p.secure)),p.context) as T
            }
            vm = ViewModelProvider(owner,factory)[CatalogViewModel::class.java]
        }
        withTimeout(15000) { vm.state.first { it.catalog is CatalogState.Ready } }
    }
    @After fun cleanup() { if (::vm.isInitialized) main { models.clear() }; if (::p.isInitialized) p.close() }
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
    private fun chooseType(label: Int) {
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_124)).performScrollTo().performClick()
        compose.onNodeWithText(uiText(label)).performClick()
    }
    private fun row(name: String) = compose.onNodeWithText(name, substring = true).performScrollTo()
    private fun confirmDeletion() = compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_136)).performScrollTo()
    private fun deleteButton() = compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_137)).performScrollTo()

    @Test fun taxonomyDeleteConfirmationDoesNotCarryAcrossRowsTypesOrNew(): Unit = runBlocking {
        p.admin.saveTaxonomy("category", null, "تصنيف ألف", null)
        p.admin.saveTaxonomy("category", null, "تصنيف باء", null)
        p.admin.saveTaxonomy("form", null, "شكل خاص", null)
        withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.categories?.size == 3 } }
        val before = p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) }
        taxonomy(); chooseType(R.string.ui_pharmacy_admin_screen_051)
        row("تصنيف ألف").performClick(); confirmDeletion().performClick(); deleteButton().assertIsEnabled()
        row("تصنيف باء").performClick(); confirmDeletion().assertIsOff(); deleteButton().assertIsNotEnabled()
        confirmDeletion().performClick()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_128)).performScrollTo().performClick()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_136)).assertDoesNotExist()
        row("تصنيف ألف").performClick(); deleteButton().assertIsNotEnabled(); confirmDeletion().performClick()
        chooseType(R.string.ui_pharmacy_admin_screen_052)
        row("شكل خاص").performClick(); confirmDeletion().assertIsOff(); deleteButton().assertIsNotEnabled()
        assertEquals(before, p.admin.transaction { Triple(it.catalog(), it.pending(), it.history()) })
    }

    @Test fun blankSectionsDefaultActiveAfterTypeChangeNewAndSuccessfulSave(): Unit = runBlocking {
        p.admin.saveTaxonomy("section", null, "قسم متوقف", null, active = false)
        withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.sections?.size == 2 } }
        taxonomy(); row("قسم متوقف").performClick()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_131)).performScrollTo().assertIsOff()
        chooseType(R.string.ui_pharmacy_admin_screen_051); chooseType(R.string.ui_pharmacy_admin_screen_053)
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_131)).performScrollTo().assertIsOn()
        row("قسم متوقف").performClick()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_128)).performScrollTo().performClick()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_131)).performScrollTo().assertIsOn()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("قسم جديد")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_132)).performScrollTo().performClick()
        val saved = withTimeout(10000) { vm.state.first { (it.catalog as? CatalogState.Ready)?.catalog?.sections?.any { section -> section.nameAr == "قسم جديد" } == true } }
        assertTrue((saved.catalog as CatalogState.Ready).catalog.sections.single { it.nameAr == "قسم جديد" }.isActive)
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_131)).performScrollTo().assertIsOn()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_136)).assertDoesNotExist()
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
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_131)).performScrollTo().assertIsOn()
        compose.onNodeWithText(uiText(R.string.ui_pharmacy_admin_screen_136)).assertDoesNotExist()
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
