package com.aqaqeer.app.reports

import android.content.res.Resources
import com.aqaqeer.app.AppStrings
import io.mockk.*

/** JVM-only resource boundary; report selection and column values remain production code. */
internal fun installReportStrings() {
    val resources = mockk<Resources>()
    every { resources.getString(any()) } answers { "resource-${firstArg<Int>()}" }
    mockkObject(AppStrings)
    every { AppStrings.resources } returns resources
}

internal fun restoreReportStrings() = unmockkObject(AppStrings)
