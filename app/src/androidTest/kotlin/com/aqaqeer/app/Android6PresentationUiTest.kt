package com.aqaqeer.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.os.ConfigurationCompat
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.ui.AqaqeerTheme
import com.aqaqeer.app.ui.GregorianDateField
import com.aqaqeer.app.ui.TimezoneField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class Android6PresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun arabicCalendarPreservesTypedEarlyDatesAndRoundTripsLeapDay() {
        val label = "تاريخ الصلاحية"
        var value by mutableStateOf("1582-10-04")
        compose.setContent { ArabicContent { GregorianDateField(label, value, { value = it }, submitted = true) } }
        val confirm = compose.onNodeWithText(uiText(R.string.shared_date_picker_confirm_action))
        val picker = compose.onNodeWithContentDescription(uiText(R.string.shared_gregorian_picker_description, label))
        picker.performClick()
        confirm.assertIsNotEnabled()
        compose.onNodeWithText(uiText(R.string.availability_bulk_cancel_action)).performClick()
        compose.runOnIdle { assertEquals("1582-10-04", value) }

        compose.onNodeWithTag(label).performTextReplacement("2024-02-29")
        compose.runOnIdle { assertEquals("2024-02-29", value) }
        picker.performClick()
        compose.onAllNodesWithText("فبراير", substring = true).onFirst().assertIsDisplayed()
        confirm.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("2024-02-29", value) }
    }

    @Test fun timezoneSearchAcceptsArabicCityAndTechnicalId() {
        var value by mutableStateOf("UTC")
        compose.setContent { ArabicContent { TimezoneField(value) { value = it } } }
        val timezone = compose.onNodeWithText(uiText(R.string.shared_timezone_label))
        timezone.performClick()
        compose.onNode(hasSetTextAction()).performTextInput("دمشق")
        compose.onNodeWithText("Asia/Damascus", substring = true).performClick()
        compose.runOnIdle { assertEquals("Asia/Damascus", value) }

        timezone.performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Europe/Paris")
        compose.onNode(hasText("باريس") and hasText("Europe/Paris", substring = true))
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("Europe/Paris", value) }
    }

    @Composable private fun ArabicContent(content: @Composable () -> Unit) {
        val baseContext = LocalContext.current
        val configuration = LocalConfiguration.current
        // English device configuration, with the same Arabic override as the app activity.
        val arabicContext = remember(baseContext, configuration) {
            val english = Configuration(configuration).apply { setLocale(Locale.US) }
            val englishContext = baseContext.createConfigurationContext(english)
            assertEquals("en", ConfigurationCompat.getLocales(englishContext.resources.configuration)[0]?.language)
            val arabic = Configuration(englishContext.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("ar"))
            }
            englishContext.createConfigurationContext(arabic)
        }
        CompositionLocalProvider(LocalContext provides arabicContext,
            LocalConfiguration provides arabicContext.resources.configuration,
            LocalLayoutDirection provides LayoutDirection.Rtl) {
            AqaqeerTheme(ThemeChoice.LIGHT, content = content)
        }
    }
}
