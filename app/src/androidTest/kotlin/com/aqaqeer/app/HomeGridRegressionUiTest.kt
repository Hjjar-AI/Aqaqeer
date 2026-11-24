package com.aqaqeer.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Measure the real production grid, including its padding, gaps and layout-metrics provider. */
class HomeGridRegressionUiTest {
    @get:Rule val compose = createComposeRule()
    private var width by mutableIntStateOf(320)
    private var fontScale by mutableFloatStateOf(1f)
    private var adaptive by mutableStateOf(true)
    private var medications by mutableStateOf((1..3).map { F.catalog().medications.single().copy(id = "item$it") })

    private fun showGrid() {
        compose.setContent {
            // One pixel per dp makes the exact boundary independent of emulator density rounding.
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                AqaqeerTheme(ThemeChoice.LIGHT) {
                    Box(Modifier.size(width.dp, 400.dp)) { ProvideLayoutMetrics {
                        HomeGrid(rememberLazyGridState(), adaptive, Modifier,
                            HomeBrowseResult(visible = medications), emptyList(), false, emptyList(), {},
                            leadingContent = {
                                item(key = "notice", span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("notice"))
                                }
                            },
                            card = { medication ->
                                var clicks by remember { mutableIntStateOf(0) }
                                Text("${medication.id}:$clicks", Modifier.fillMaxWidth().height(48.dp)
                                    .testTag(medication.id).clickable { clicks++ })
                            }, otherCard = { _, _ -> error("Unexpected other-section row") })
                    } }
                }
            }
        }
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertFirstRow(columns: Int) {
        val first = bounds("item1"); val second = bounds("item2")
        assertEquals("Fixture width was constrained by the host", width.toFloat(), bounds("home").width, 0.5f)
        if (columns == 1) {
            assertTrue(second.top > first.bottom)
            assertEquals(first.left, second.left, 0.5f)
        } else {
            assertEquals(first.top, second.top, 0.5f)
            assertTrue("RTL places the first card on the right", first.left > second.left)
            assertEquals(8f, first.left - second.right, 0.5f)
        }
    }

    @Test fun twoColumnsStartAt320DpIncludingPagePaddingAndGap() {
        width = 319; showGrid(); assertFirstRow(1)
        compose.runOnIdle { width = 320 }; assertFirstRow(2)
        assertEquals(140f, bounds("item1").width, 0.5f)
        compose.runOnIdle { width = 321 }; assertFirstRow(2)
    }

    @Test fun largerFontsRaiseTheTwoColumnBoundary() {
        fontScale = 1.5f; width = 459; showGrid(); assertFirstRow(1)
        compose.runOnIdle { width = 460 }; assertFirstRow(2)
        assertEquals(210f, bounds("item1").width, 0.5f)
        compose.runOnIdle { fontScale = 2f; width = 320 }; assertFirstRow(1)
    }

    @Test fun smallerFontsDoNotReduceTheMinimumCardWidth() {
        fontScale = 0.8f; width = 319; showGrid(); assertFirstRow(1)
        compose.runOnIdle { width = 320 }; assertFirstRow(2)
        assertEquals(140f, bounds("item1").width, 0.5f)
    }

    @Test fun preferenceDisablesColumnsAndRestoresAdaptiveLayoutWithoutLosingItemState() {
        showGrid(); assertFirstRow(2)
        compose.onNodeWithTag("item1").performClick()
        compose.runOnIdle { adaptive = false }; assertFirstRow(1)
        compose.onNodeWithTag("item1").assertTextEquals("item1:1")
        compose.runOnIdle { adaptive = true }; assertFirstRow(2)
        compose.onNodeWithTag("item1").assertTextEquals("item1:1")
    }

    @Test fun fullSpanNoticeKeepsItsWidthWhenAdaptiveGridAddsAThirdColumn() {
        width = 467; showGrid()
        assertTrue(bounds("item3").top > bounds("item1").bottom)
        compose.runOnIdle { width = 468 }
        val first = bounds("item1"); val third = bounds("item3"); val notice = bounds("notice")
        assertEquals(first.top, third.top, 0.5f)
        assertEquals(140f, first.width, 0.5f)
        assertEquals(436f, notice.width, 0.5f)
        assertEquals(16f, notice.left - bounds("home").left, 0.5f)
        assertTrue(first.top > notice.bottom)
    }

    @Test fun stableMedicationKeysPreserveRememberedStateAfterReordering() {
        showGrid()
        compose.onNodeWithTag("item1").performClick()
        compose.runOnIdle { medications = medications.reversed() }
        compose.onNodeWithTag("item1").assertTextEquals("item1:1")
        compose.onNodeWithTag("item2").assertTextEquals("item2:0")
        compose.onNodeWithTag("item3").assertTextEquals("item3:0")
        assertTrue(bounds("item1").top > bounds("item3").bottom)
    }
}
