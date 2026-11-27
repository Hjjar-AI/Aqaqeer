package com.aqaqeer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.data.*

private enum class HomeGridContent { MEDICATION, GROUP_HEADER, SECTION_HEADER }

/** Rendering only. Home retains projection freshness, selection bases and edit authorization. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun HomeGrid(state: LazyGridState, adaptive: Boolean, modifier: Modifier,
    projection: HomeBrowseResult?, otherSections: List<SectionSearchResults>, groupingEnabled: Boolean,
    collapsedGroups: List<String>, toggleGroup: (String) -> Unit,
    leadingContent: LazyGridScope.() -> Unit,
    card: @Composable LazyGridItemScope.(Medication) -> Unit,
    otherCard: @Composable LazyGridItemScope.(SectionSearchResults, MedicationRowUi) -> Unit) {
    val columns = if (adaptive) GridCells.Adaptive(Size.gridCellMin * maxOf(1f, LocalLayoutMetrics.current.fontScale)) else GridCells.Fixed(1)
    LazyVerticalGrid(columns = columns, state = state, modifier = Modifier.fillMaxSize().then(modifier).testTag("home"),
        contentPadding = PaddingValues(start = UiSpacing.page, end = UiSpacing.page, top = UiSpacing.related, bottom = UiSpacing.page),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.related), verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
        leadingContent()
        if (groupingEnabled) {
            projection?.groups.orEmpty().forEach { group ->
                stickyHeader(key = group.key, contentType = HomeGridContent.GROUP_HEADER) {
                    StickyGroupHeader {
                        CollapsibleGroup(group.label, group.key !in collapsedGroups, toggle = { toggleGroup(group.key) }) {}
                    }
                }
                if (group.key !in collapsedGroups) items(group.medications, key = { it.id },
                    contentType = { HomeGridContent.MEDICATION }, itemContent = card)
            }
        } else items(projection?.visible.orEmpty(), key = { it.id }, contentType = { HomeGridContent.MEDICATION }, itemContent = card)
        otherSections.forEach { result ->
            stickyHeader(key = "other_search_section_${result.section.id}", contentType = HomeGridContent.SECTION_HEADER) {
                StickyGroupHeader { SectionHeading(result.section.nameAr, Modifier.padding(top = UiSpacing.section)) }
            }
            items(result.rows, key = { "other_search_${result.section.id}_${it.medication.id}" },
                contentType = { HomeGridContent.MEDICATION }) { row -> otherCard(result, row) }
        }
    }
}
