package com.aqaqeer.app.ui

import com.aqaqeer.app.data.HistoryFilter
import com.aqaqeer.app.data.HistoryPage

internal data class HistoryRequest(val filter: HistoryFilter = HistoryFilter(), val page: Int = 0,
    val refresh: Int = 0)
internal data class HistoryUiState(val request: HistoryRequest = HistoryRequest(), val loading: Boolean = false,
    val page: HistoryPage? = null, val error: String? = null,
    val pharmacyId: String? = null, val medicationScope: String? = null)
