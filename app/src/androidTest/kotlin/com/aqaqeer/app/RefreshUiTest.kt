package com.aqaqeer.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RefreshUiTest {
    @get:Rule val compose=createComposeRule()
    private fun awaitHome() = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag("home").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()
    }
    @Test fun retryIsExplicitBusyDisablesDuplicatesAndFailureKeepsCachedMedication() {
        var state by mutableStateOf(AppUiState(refresh=RefreshUiState(error="تعذر الاتصال؛ احتُفظ بالبيانات")))
        var calls=0
        compose.setContent { AqaqeerTheme(ThemeChoice.SYSTEM) {
            HomeScreen(CatalogState.Ready(F.catalog(),false),state,F.time,{}, {}, {}, {}, {}, {}, {},
                refresh={ calls++; state=state.copy(refresh=RefreshUiState(busy=true)) })
        } }
        awaitHome()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("refresh_error"))
        compose.onNodeWithTag("refresh_error").assertIsDisplayed()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("med_Fixture medication"))
        compose.onNodeWithTag("med_Fixture medication").assertIsDisplayed()
        compose.onNodeWithTag("home").performScrollToNode(hasTestTag("refresh_catalog"))
        compose.onNodeWithTag("refresh_catalog").performClick()
        compose.runOnIdle { assertEquals(1,calls) }
        compose.onNodeWithTag("refresh_catalog").assertIsNotEnabled()
        compose.onNodeWithTag("refresh_error").assertDoesNotExist()
    }
    @Test fun pullingTheCachedListRequestsRefresh() {
        var calls=0
        compose.setContent { AqaqeerTheme(ThemeChoice.SYSTEM) {
            HomeScreen(CatalogState.Ready(F.catalog(),false),AppUiState(),F.time,{}, {}, {}, {}, {}, {}, {},refresh={ calls++ })
        } }
        awaitHome()
        compose.onNodeWithTag("home").performTouchInput { swipeDown(startY=10f,endY=height.toFloat()-10f,durationMillis=500) }
        compose.waitUntil(5_000) { calls>0 }
        compose.runOnIdle { assertEquals(1,calls) }
    }
}
