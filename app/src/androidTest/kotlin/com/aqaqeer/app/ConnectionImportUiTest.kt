package com.aqaqeer.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
    private val pending = PendingConnectionInfo("public-stage",F.pharmacyId,"صيدلية الاختبار",F.time,F.editor)
    private fun awaitImportScreen() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("connection_import").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
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
        compose.onNodeWithTag("verify_pending_connection").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,calls); assertEquals(pending.id,selected) }
        compose.onNodeWithTag("verify_pending_connection").assertIsNotEnabled()
        compose.onNodeWithText("جار التحقق عبر تيليغرام…").performScrollTo().assertIsDisplayed()
    }
    @Test fun savedUpdateRequirementIsVisibleWithoutStartingVerificationAndDiscardNeedsConfirmation() {
        var verifyCalls=0; var discardCalls=0
        compose.setContent { AqaqeerTheme(com.aqaqeer.app.data.ThemeChoice.SYSTEM) {
            ConnectionImportScreen(ConnectionImportUiState(loading=false,storageReady=true,pending=pending,
                requirements=SnapshotRequirements(2,100)),CatalogState.Ready(CatalogSeeds.unconfigured(),false),{}, {}, {},
                { discardCalls++ },{}, { verifyCalls++ }, {})
        } }
        awaitImportScreen()
        compose.onNodeWithText("يلزم إصدار متوافق من التطبيق").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("إلغاء الاتصال المعلق").performScrollTo().performClick()
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
        compose.onNodeWithTag("activate_pending_connection").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1,calls); assertEquals(pending.id,target) }
        compose.onNodeWithTag("activate_pending_connection").assertIsNotEnabled()
        compose.onNodeWithTag("verify_pending_connection").assertIsNotEnabled()
        compose.onNodeWithText("جار التحقق وتفعيل الصيدلية…").performScrollTo().assertIsDisplayed()
    }
    @Test fun retainedFutureRequirementsDisableActivationButKeepVerificationAvailable() {
        compose.setContent { AqaqeerTheme(com.aqaqeer.app.data.ThemeChoice.SYSTEM) {
            ConnectionImportScreen(ConnectionImportUiState(loading=false,storageReady=true,pending=pending,
                requirements=SnapshotRequirements(2,1)),CatalogState.Ready(CatalogSeeds.unconfigured(),false),
                {}, {}, {}, {}, {}, {}, {})
        } }
        awaitImportScreen()
        compose.onNodeWithTag("activate_pending_connection").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("verify_pending_connection").performScrollTo().assertIsEnabled()
    }

}
