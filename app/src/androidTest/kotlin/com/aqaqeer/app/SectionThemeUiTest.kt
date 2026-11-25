package com.aqaqeer.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.data.Section
import com.aqaqeer.app.data.ThemeAccent
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.ui.AqaqeerTheme
import com.aqaqeer.app.ui.HomeSectionsBar
import com.aqaqeer.app.ui.appColorScheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SectionThemeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun retainedAccentKeysAndSectionIndicatorUseTheNewPalette() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val copper = ThemeAccent.fromStoredName("PURPLE")
        for (stored in listOf(null, "GOLD", "unknown")) {
            assertEquals(copper, ThemeAccent.fromStoredName(stored))
        }
        assertEquals(ThemeAccent.ROSE, ThemeAccent.fromStoredName("CRIMSON"))
        assertEquals(Color(0xFF8B4C23), appColorScheme(ThemeChoice.LIGHT, copper, false, resources).primary)
        assertEquals(Color(0xFF405F98), appColorScheme(ThemeChoice.LIGHT, ThemeAccent.BLUE, false, resources).primary)
        for (mode in listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.AMOLED)) {
            val green = appColorScheme(mode, ThemeAccent.GREEN, false, resources).primary
            val rose = appColorScheme(mode, ThemeAccent.ROSE, false, resources).primary
            assertTrue("Green and Rose should differ by lightness in $mode",
                green.luminance() - rose.luminance() >= 0.04f)
        }

        val sections = listOf(Section("a", "الأول"), Section("b", "الثاني"))
        var selected by mutableStateOf(sections.first().id)
        var theme by mutableStateOf(ThemeChoice.LIGHT)
        compose.setContent {
            AqaqeerTheme(theme, ThemeAccent.BLUE) {
                Box(Modifier.width(280.dp).testTag("section-bar")) {
                    HomeSectionsBar(sections, selected, emptyMap(), { selected = it })
                }
            }
        }
        val host = compose.onNodeWithTag("section-bar")
        for (mode in listOf(ThemeChoice.LIGHT, ThemeChoice.DARK, ThemeChoice.AMOLED)) {
            compose.runOnIdle { theme = mode }
            val next = if (mode == ThemeChoice.DARK) sections.first() else sections.last()
            val tab = compose.onNodeWithTag("section_${next.id}")
            tab.assertIsDisplayed().performClick().assertIsSelected()

            val colors = appColorScheme(mode, ThemeAccent.BLUE, false, resources)
            assertNotEquals(colors.primary, colors.primaryContainer)
            val hostBounds = host.fetchSemanticsNode().boundsInRoot
            val tabBounds = tab.fetchSemanticsNode().boundsInRoot
            val pixels = host.captureToImage().toPixelMap()
            val x = ((tabBounds.left + tabBounds.right) / 2f - hostBounds.left).roundToInt()
            val slice = (x - 3)..(x + 3)
            assertTrue("Selected tab shows its tonal fill in $mode",
                (pixels.height / 8 until pixels.height / 2).any { y ->
                    slice.any { px -> pixels[px, y].toArgb() == colors.primaryContainer.toArgb() }
                })
            assertTrue("The underline below the selected tab shows a distinct color in $mode",
                (pixels.height * 7 / 8 until pixels.height).any { y ->
                    slice.any { px -> pixels[px, y].toArgb() == colors.primary.toArgb() }
                })
        }
    }
}
