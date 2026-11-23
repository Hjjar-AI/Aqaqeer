package com.aqaqeer.app

import com.aqaqeer.app.data.*
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.reports.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*

class ReportPreferencesInstrumentedTest {
    private lateinit var profile: SuiteProfile
    private lateinit var preferences: DisplayPreferences
    private lateinit var originalOptions: ReportOptions
    private lateinit var originalHome: HomePreferences
    @Before fun setup(): Unit = runBlocking {
        profile = SuiteProfile()
        preferences = DisplayPreferences(profile.context)
        originalOptions = preferences.reportOptions.first()
        originalHome = preferences.home.first()
    }
    @After fun cleanup(): Unit = runBlocking {
        try {
            if (::originalOptions.isInitialized) preferences.setReportOptions(originalOptions)
            if (::originalHome.isInitialized) preferences.setOtherSectionAvailability(originalHome.showOtherSectionAvailability)
        } finally {
            if (::profile.isInitialized) profile.close()
        }
    }
    @Test fun everyPresentationOptionAndHintTogglePersistForANewReader(): Unit = runBlocking {
        withTimeout(10000) {
            val original = preferences.home.first(); val sections = preferences.sections.first()
            val options = ReportOptions(ReportField.entries.toSet(), ReportNames.ENGLISH, ReportSort.TRADE, false,
                ReportAvailability.PER_SECTION, ReportPaper.A4, true, false)
            preferences.setReportOptions(options); preferences.setOtherSectionAvailability(true)
            val next = DisplayPreferences(profile.context)
            assertEquals(options, next.reportOptions.first())
            assertEquals(original.copy(showOtherSectionAvailability = true), next.home.first())
            assertEquals(sections, next.sections.first())
            preferences.setOtherSectionAvailability(false)
            assertFalse(DisplayPreferences(profile.context).home.first().showOtherSectionAvailability)
            assertEquals(options, next.reportOptions.first())
        }
    }
    @Test fun invalidScientificFieldSelectionRejectsWithoutOverwritingSavedDefaults(): Unit = runBlocking {
        val saved = ReportOptions(paper = ReportPaper.A4)
        preferences.setReportOptions(saved)
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            preferences.setReportOptions(ReportOptions(fields = setOf(ReportField.NOTE)))
        } }
        assertEquals(saved, preferences.reportOptions.first())
    }
}
