package com.aqaqeer.app.fixtures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.aqaqeer.app.data.*
import com.aqaqeer.app.ui.AppUiState
import kotlinx.coroutines.runBlocking

/** Supplies the real immutable projection to isolated Home composables without a ViewModel/network. */
@Composable
internal fun homeProjection(catalog: CatalogSnapshot, state: AppUiState = AppUiState(), now: java.time.Instant = M3Fixtures.time): HomeBrowseResult =
    remember(catalog, state.sectionId, state.appliedQuery, state.filters, state.homePreferences, state.fetchedIds, now) {
        val active = catalog.sections.filter { it.isActive }
        val section = active.firstOrNull { it.id == state.sectionId } ?: active.firstOrNull()
        runBlocking { CatalogSearchIndex(catalog).project(section?.id, state.appliedQuery, state.filters, state.homePreferences, emptySet(), state.fetchedIds, MedicationPresentation.today(now, catalog.pharmacy?.timezone ?: "Asia/Damascus")) }
    }
