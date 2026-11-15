package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText
import com.aqaqeer.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeSectionsBar(active: List<Section>, selectedId: String?,
    outCounts: Map<String, Int>, selectSection: (String) -> Unit) {
    if (active.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val style = MaterialTheme.typography.titleSmall
        val density = LocalDensity.current
        val required = remember(active, outCounts, style, density) {
            active.maxOf { section ->
                val count = outCounts[section.id] ?: 0
                val label = section.nameAr + if (count > 0) " ${MedicationPresentation.easternDigits(count.toString())}" else ""
                with(density) { measurer.measure(androidx.compose.ui.text.AnnotatedString(label), style).size.width.toDp() } + UiSpacing.tabMeasurementAllowance
            } * active.size
        }
        val tabs: @Composable () -> Unit = {
            active.forEach { section ->
                val out = outCounts[section.id] ?: 0
                Tab(selected = selectedId == section.id, onClick = { selectSection(section.id) },
                    modifier = Modifier.testTag("section_${section.id}").semantics {
                        contentDescription = section.nameAr + if (out > 0) uiText(R.string.ui_home_screen_001, MedicationPresentation.easternDigits(out.toString())) else ""
                    }) {
                    Row(Modifier.heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.related),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                        Text(section.nameAr, style = style)
                        if (out > 0) Badge { Text(MedicationPresentation.easternDigits(out.toString())) }
                    }
                }
            }
        }
        if (required <= maxWidth) PrimaryTabRow(selectedTabIndex = active.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
            containerColor = MaterialTheme.colorScheme.background, tabs = tabs)
        else PrimaryScrollableTabRow(selectedTabIndex = active.indexOfFirst { it.id == selectedId }.coerceAtLeast(0), edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background, tabs = tabs)
    }
}

@Composable internal fun HomeSearchControls(query: TextFieldValue, changeQuery: (TextFieldValue) -> Unit,
    searching: Boolean, filters: HomeFilters, visibleCount: Int, totalCount: Int,
    openFilters: () -> Unit, clearFilters: () -> Unit) {
    val focus = LocalFocusManager.current
    ContentSection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            OutlinedTextField(value = query, onValueChange = changeQuery, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                placeholder = { Text(uiText(R.string.ui_home_screen_004)) },
                modifier = Modifier.weight(1f).testTag("search"),
                leadingIcon = { AppIcon(UiIcon.SEARCH) },
                trailingIcon = if (query.text.isNotEmpty()) ({
                    IconButton(onClick = { changeQuery(TextFieldValue("")); focus.clearFocus() },
                        modifier = Modifier.testTag("clear_search")) { AppIcon(UiIcon.CLOSE, description = uiText(R.string.ui_home_screen_005)) }
                }) else null)
            val filterCount = filters.statuses.size + filters.categoryIds.size
            OutlinedIconButton(onClick = {
                openFilters()
            },
                modifier = Modifier.size(UiSpacing.filterButton).semantics {
                    contentDescription = uiText(R.string.ui_home_screen_002) + if (filterCount > 0) uiText(R.string.ui_home_screen_003, MedicationPresentation.easternDigits(filterCount.toString())) else ""
                }) {
                BadgedBox(badge = {
                    if (filterCount > 0) Badge(Modifier.clearAndSetSemantics { }) { Text(MedicationPresentation.easternDigits(filterCount.toString())) }
                }) { AppIcon(UiIcon.FILTER) }
            }
        }
        if (searching || filters.statuses.isNotEmpty() || filters.categoryIds.isNotEmpty()) {
            WrappingContent {
                Text(uiText(R.string.ui_home_screen_006, MedicationPresentation.easternDigits(visibleCount.toString()), MedicationPresentation.easternDigits(totalCount.toString())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (filters.statuses.isNotEmpty() || filters.categoryIds.isNotEmpty())
                    TextButton(onClick = clearFilters) { Text(uiText(R.string.ui_history_screen_017)) }
            }
        }
    }
}
