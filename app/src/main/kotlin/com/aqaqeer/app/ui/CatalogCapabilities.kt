package com.aqaqeer.app.ui

import com.aqaqeer.app.data.CatalogState

internal val CatalogState.Ready.canEditCatalog: Boolean
    get() = admin && !requiresUpdate && !maintenancePaused
internal val CatalogState.Ready.canRefreshCatalog: Boolean
    get() = !restorePaused && !maintenancePaused && lifecycle == "SHARED"
