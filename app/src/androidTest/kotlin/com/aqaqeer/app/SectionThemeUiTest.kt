package com.aqaqeer.app

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
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
import com.aqaqeer.app.ui.AppIcon
import com.aqaqeer.app.ui.AqaqeerTheme
import com.aqaqeer.app.ui.HomeSectionsBar
import com.aqaqeer.app.ui.UiIcon
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = context.resources
        val copper = ThemeAccent.fromStoredName("PURPLE")
        assertEquals(ThemeAccent.BLUE, ThemeAccent.fromStoredName(null))
        for (stored in listOf("GOLD", "VIOLET", "unknown")) {
            assertEquals(copper, ThemeAccent.fromStoredName(stored))
        }
        for (accent in ThemeAccent.entries) assertEquals(accent, ThemeAccent.fromStoredName(accent.name))
        assertEquals(ThemeAccent.ROSE, ThemeAccent.fromStoredName("CRIMSON"))
        assertEquals("أزرق", resources.getString(R.string.settings_accent_blue_label))
        assertEquals(Color(0xFF8B4C23), appColorScheme(ThemeChoice.LIGHT, copper, false, resources).primary)
        assertEquals(Color(0xFF48549A), appColorScheme(ThemeChoice.LIGHT, ThemeAccent.BLUE, false, resources).primary)
        assertEquals(Color(0xFFBBC2FF), appColorScheme(ThemeChoice.DARK, ThemeAccent.BLUE, false, resources).primary)
        assertEquals(Color(0xFF96323F), appColorScheme(ThemeChoice.LIGHT, ThemeAccent.ROSE, false, resources).primary)
        assertEquals(Color(0xFFFFA8AE), appColorScheme(ThemeChoice.DARK, ThemeAccent.ROSE, false, resources).primary)
        for ((systemDark, background) in listOf(false to Color(0xFFF2F6FF), true to Color(0xFF131925))) {
            val configured = context.createConfigurationContext(resources.configuration.withSystemDark(systemDark)).resources
            assertEquals(background.toArgb(), configured.getColor(R.color.splash_background, null))
            assertEquals(background, appColorScheme(ThemeChoice.SYSTEM, ThemeAccent.BLUE, systemDark, configured).background)
        }
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
            val oppositeSystemMode = LocalConfiguration.current.withSystemDark(theme == ThemeChoice.LIGHT)
            CompositionLocalProvider(LocalConfiguration provides oppositeSystemMode) {
                AqaqeerTheme(theme, ThemeAccent.BLUE) {
                    Column {
                        Box(Modifier.width(280.dp).testTag("section-bar")) {
                            HomeSectionsBar(sections, selected, selectSection = { selected = it })
                        }
                        AppIcon(UiIcon.FORM_TABLET, Modifier.testTag("dosage-sample"), size = 32.dp)
                    }
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
            val dosage = compose.onNodeWithTag("dosage-sample").captureToImage().toPixelMap()
            val body = if (mode == ThemeChoice.LIGHT) Color(0xFFD9E1EC) else Color(0xFF34455D)
            assertTrue("Dosage artwork uses the app's $mode palette with the opposite system mode",
                (0 until dosage.height).any { y ->
                    (0 until dosage.width).any { x -> dosage[x, y].toArgb() == body.toArgb() }
                })
        }
    }

    private fun Configuration.withSystemDark(dark: Boolean) = Configuration(this).apply {
        val night = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    }
}
