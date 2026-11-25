package com.aqaqeer.app.ui

import com.aqaqeer.app.data.CatalogState
import com.aqaqeer.app.fixtures.M3Fixtures as F
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshProgressTest {
    private val catalog = F.catalog()
    private val ready = CatalogState.Ready(catalog, isDemo = false, lastSuccessfulRefreshAt = F.time, publishedAt = F.time)
    private val error = "Could not refresh the saved catalog"

    @Test fun successfulUnchangedBackgroundCheckClearsForegroundError() {
        val progress = RefreshProgress(failure = RefreshFailure(F.pharmacyId, ready.lastSuccessfulRefreshAt, error))
        assertEquals(error, progress.presentation(ready).error)
        // An unchanged snapshot advances the check time while retaining the catalog and publication time.
        val checked = ready.copy(lastSuccessfulRefreshAt = F.time.plusSeconds(60))
        assertNull(progress.presentation(checked).error)
    }

    @Test fun unrelatedUpdatesKeepTheErrorAndAnotherPharmacyNeverReceivesIt() {
        val neverChecked = ready.copy(lastSuccessfulRefreshAt = null)
        val progress = RefreshProgress(busy = true, failure = RefreshFailure(F.pharmacyId, null, error))
        assertTrue(progress.presentation(neverChecked).busy)
        assertEquals(error, progress.presentation(neverChecked.copy(hasBackup = true)).error)
        assertNull(progress.presentation(neverChecked.copy(
            catalog = catalog.copy(pharmacy = catalog.pharmacy!!.copy(pharmacyId = F.otherPharmacyId)))).error)
        assertNull(progress.presentation(null).error)
        assertNull(progress.presentation(neverChecked.copy(lastSuccessfulRefreshAt = F.time)).error)
    }
}
