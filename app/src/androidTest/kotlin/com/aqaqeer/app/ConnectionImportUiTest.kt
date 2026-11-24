package com.aqaqeer.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.aqaqeer.app.data.CatalogSeeds
import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.PendingConnectionInfo
import com.aqaqeer.app.security.files.SnapshotRequirements
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConnectionImportUiTest {
    @get:Rule val compose = createComposeRule()
    private val pending = PendingConnectionInfo("public-stage",F.pharmacyId,"صيدلية الاختبار",F.time)
    private fun awaitImportScreen() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("connection_import").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
    private fun importNode(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("connection_import").performScrollToNode(matcher)
        return compose.onNode(matcher)
    }
    @Test fun explicitVerificationButtonTargetsPendingIdAndBusyStateDisablesDuplicateRequests() {
        var state by mutableStateOf(ConnectionImportUiState(loading=false,storageReady=true,pending=pending))
        var calls=0; var selected: String?=null
        compose.setContent { AqaqeerTheme(com.aqaqeer.app.data.ThemeChoice.SYSTEM) {
            ConnectionImportScreen(state,CatalogState.Ready(CatalogSeeds.unconfigured(),false),{}, {}, {}, {}, {}, {
                calls++; selected=it; state=state.copy(busy=true,verifying=true)
            }, {})
        } }
        awaitImportScreen()
        importNode(hasTestTag("verify_pending_connection")).performClick()
        compose.runOnIdle { assertEquals(1,calls); assertEquals(pending.id,selected) }
        importNode(hasTestTag("verify_pending_connection")).assertIsNotEnabled()
        importNode(hasText(uiText(R.string.connection_import_verification_progress))).assertIsDisplayed()
    }
    @Test fun savedUpdateRequirementIsVisibleWithoutStartingVerificationAndDiscardNeedsConfirmation() {
        var verifyCalls=0; var discardCalls=0
        compose.setContent { AqaqeerTheme(com.aqaqeer.app.data.ThemeChoice.SYSTEM) {
            ConnectionImportScreen(ConnectionImportUiState(loading=false,storageReady=true,pending=pending,
                requirements=SnapshotRequirements(2,100)),CatalogState.Ready(CatalogSeeds.unconfigured(),false),{}, {}, {},
                { discardCalls++ },{}, { verifyCalls++ }, {})
        } }
        awaitImportScreen()
        importNode(hasText(uiText(R.string.home_update_required_title))).assertIsDisplayed()
        importNode(hasText(uiText(R.string.connection_import_discard_pending_action))).performClick()
        compose.runOnIdle { assertEquals(0,verifyCalls); assertEquals(0,discardCalls) }
        compose.onNodeWithText("احتفاظ").performClick()
        compose.runOnIdle { assertEquals(0,discardCalls) }
    }
    @Test fun activationIsExplicitTargetsTheStagedIdAndBusyStatePreventsDuplicateActivation() {
        var state by mutableStateOf(ConnectionImportUiState(loading=false,storageReady=true,pending=pending))
        var calls = 0
        var target: String? = null
        compose.setContent { AqaqeerTheme(com.aqaqeer.app.data.ThemeChoice.SYSTEM) {
            ConnectionImportScreen(state,CatalogState.Ready(CatalogSeeds.unconfigured(),false),{}, {}, {}, {}, {}, {}, {
                calls++; target=it; state=state.copy(busy=true,activating=true)
            })
        } }
        awaitImportScreen()
        compose.runOnIdle { assertEquals(0,calls) }
        importNode(hasTestTag("activate_pending_connection")).performClick()
        compose.runOnIdle { assertEquals(1,calls); assertEquals(pending.id,target) }
        importNode(hasTestTag("activate_pending_connection")).assertIsNotEnabled()
        importNode(hasTestTag("verify_pending_connection")).assertIsNotEnabled()
        importNode(hasText(uiText(R.string.connection_import_activation_progress))).assertIsDisplayed()
    }
    @Test fun retainedFutureRequirementsDisableActivationButKeepVerificationAvailable() {
        compose.setContent { AqaqeerTheme(com.aqaqeer.app.data.ThemeChoice.SYSTEM) {
            ConnectionImportScreen(ConnectionImportUiState(loading=false,storageReady=true,pending=pending,
                requirements=SnapshotRequirements(2,1)),CatalogState.Ready(CatalogSeeds.unconfigured(),false),
                {}, {}, {}, {}, {}, {}, {})
        } }
        awaitImportScreen()
        importNode(hasTestTag("activate_pending_connection")).assertIsNotEnabled()
        importNode(hasTestTag("verify_pending_connection")).assertIsEnabled()
    }

}
