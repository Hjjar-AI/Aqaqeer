package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeFilterSheet(catalog: CatalogSnapshot, filters: HomeFilters,
    toggleStatus: (AvailabilityStatus) -> Unit, toggleCategory: (String) -> Unit, clear: () -> Unit,
    admin: Boolean, toggleExtraFilter: (String) -> Unit, showStatuses: Boolean = true, toggleAvailable: () -> Unit = {}, dismiss: () -> Unit) {
    val uiText = rememberUiText()
    AppBottomSheet(dismiss, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SectionHeading(uiText(R.string.home_filter_sheet_title))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
            if (showStatuses) ContentSection(title = uiText(R.string.home_availability_filter_heading)) {
                WrappingContent(Modifier.testTag("status_filters")) {
                    if (admin) AppChip(
                        selected = filters.statuses == setOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW),
                        onClick = toggleAvailable, label = { Text(uiText(R.string.review_available_only)) })
                    MedicationPresentation.editableStatuses.forEach { status -> AppChip(selected = status in filters.statuses,
                        onClick = { toggleStatus(status) }, label = { Text(statusLabel(status, text = uiText)) },
                        modifier = Modifier.testTag("filter_${status.name}")) }
                }
            }
            ContentSection(title = uiText(R.string.catalog_category_label)) {
                WrappingContent(Modifier.testTag("category_filters")) {
                    catalog.categories.forEach { category -> AppChip(selected = category.id in filters.categoryIds,
                        onClick = { toggleCategory(category.id) }, label = { Text(category.nameAr) },
                        modifier = Modifier.testTag("category_${category.id}")) }
                }
            }
            ContentSection(title = uiText(R.string.review_extra_filters)) {
                HomeExtraFilters(filters, admin, toggleExtraFilter)
            }
            FilterSummary(filters, clear)
        }
        AppButton(onClick = dismiss) { Text(uiText(R.string.home_show_results_action)) }
    }
}
