package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
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
        val required = remember(active, style, density) {
            active.maxOf { section -> with(density) {
                measurer.measure(androidx.compose.ui.text.AnnotatedString(section.nameAr), style).size.width.toDp()
            } + UiSpacing.tabMeasurementAllowance } * active.size
        }
        val tabs: @Composable () -> Unit = {
            active.forEach { section ->
                Tab(selected = selectedId == section.id, onClick = { selectSection(section.id) },
                    modifier = Modifier.testTag("section_${section.id}").semantics {
                        contentDescription = section.nameAr
                    }) {
                    Row(Modifier.heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.related),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                        Text(section.nameAr, style = style)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeSearchControls(query: TextFieldValue, changeQuery: (TextFieldValue) -> Unit,
    searching: Boolean, filters: HomeFilters, visibleCount: Int, totalCount: Int,
    openFilters: () -> Unit, clearFilters: () -> Unit, select: (() -> Unit)? = null, compact: Boolean = false) {
    val focus = LocalFocusManager.current
    ContentSection {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            val clearSearch: (@Composable () -> Unit)? = if (query.text.isNotEmpty()) ({
                IconButton(onClick = { changeQuery(TextFieldValue("")); focus.clearFocus() },
                    modifier = Modifier.testTag("clear_search")) {
                    AppIcon(UiIcon.CLOSE, description = uiText(R.string.ui_home_screen_005))
                }
            }) else null
            if (compact) {
                val interaction = remember { MutableInteractionSource() }
                BasicTextField(value = query, onValueChange = changeQuery, singleLine = true,
                    modifier = Modifier.weight(1f).heightIn(min = UiSpacing.touch).testTag("search"),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    interactionSource = interaction,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(value = query.text, innerTextField = innerTextField,
                            enabled = true, singleLine = true, visualTransformation = VisualTransformation.None,
                            interactionSource = interaction,
                            placeholder = { Text(uiText(R.string.ui_home_screen_004)) },
                            leadingIcon = { AppIcon(UiIcon.SEARCH) }, trailingIcon = clearSearch,
                            contentPadding = PaddingValues(horizontal = UiSpacing.section, vertical = UiSpacing.related))
                    })
            } else {
                OutlinedTextField(value = query, onValueChange = changeQuery, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    placeholder = { Text(uiText(R.string.ui_home_screen_004)) },
                    modifier = Modifier.weight(1f).testTag("search"),
                    leadingIcon = { AppIcon(UiIcon.SEARCH) }, trailingIcon = clearSearch)
            }
            select?.let { TextButton(onClick = it) { Text(uiText(R.string.review_select)) } }
            val filterCount = filters.statuses.size + filters.categoryIds.size + listOf(filters.expiringSoon, filters.unpublishedOnly, filters.fetchedOnly).count { it }
            OutlinedIconButton(onClick = {
                openFilters()
            },
                modifier = Modifier.size(UiSpacing.filterButton).semantics {
                    contentDescription = uiText(R.string.ui_home_screen_002) + if (filterCount > 0) uiText(R.string.ui_home_screen_003, MedicationPresentation.displayDigits(filterCount.toString())) else ""
                }) {
                BadgedBox(badge = {
                    if (filterCount > 0) Badge(Modifier.clearAndSetSemantics { }) { Text(MedicationPresentation.displayDigits(filterCount.toString())) }
                }) { AppIcon(UiIcon.FILTER) }
            }
        }
        if (searching || filters.statuses.isNotEmpty() || filters.categoryIds.isNotEmpty() || filters.expiringSoon || filters.unpublishedOnly || filters.fetchedOnly) {
            WrappingContent {
                Text(uiText(R.string.ui_home_screen_006, MedicationPresentation.displayDigits(visibleCount.toString()), MedicationPresentation.displayDigits(totalCount.toString())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (filters.statuses.isNotEmpty() || filters.categoryIds.isNotEmpty() || filters.expiringSoon || filters.unpublishedOnly || filters.fetchedOnly)
                    TextButton(onClick = clearFilters) { Text(uiText(R.string.ui_history_screen_017)) }
            }
        }
    }
}

@Composable internal fun HomeQuickFilters(filters: HomeFilters, admin: Boolean,
    toggleAvailable: () -> Unit, toggleExtra: (String) -> Unit,
    hasFetchedChanges: Boolean) {
    val scroll = rememberScrollState()
    val showFetched = hasFetchedChanges || filters.fetchedOnly
    LaunchedEffect(showFetched) {
        if (showFetched) scroll.animateScrollTo(0)
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).heightIn(min = UiSpacing.touch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        if (showFetched) HomeQuickFilterChip(filters.fetchedOnly, uiText(R.string.review_whats_new)) { toggleExtra("fetched") }
        val available = setOf(AvailabilityStatus.AVAILABLE, AvailabilityStatus.LOW)
        HomeQuickFilterChip(filters.statuses == available, uiText(R.string.review_available_only), toggleAvailable)
        if (admin) HomeQuickFilterChip(filters.unpublishedOnly, uiText(R.string.review_unpublished)) { toggleExtra("unpublished") }
    }
}

@Composable private fun HomeQuickFilterChip(selected: Boolean, label: String, toggle: () -> Unit) {
    FilterChip(selected = selected, onClick = toggle,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) })
}

@Composable internal fun HomeExtraFilters(filters: HomeFilters, admin: Boolean, toggle: (String) -> Unit) {
    WrappingContent { ExtraFilterChips(filters, admin, toggle) }
}
@Composable private fun ExtraFilterChips(filters: HomeFilters, admin: Boolean, toggle: (String) -> Unit) {
    FilterChip(filters.expiringSoon, onClick = { toggle("expiry") }, label = { Text(uiText(R.string.review_expiry_soon)) })
    if (admin) FilterChip(filters.unpublishedOnly, onClick = { toggle("unpublished") }, label = { Text(uiText(R.string.review_unpublished)) })
    FilterChip(filters.fetchedOnly, onClick = { toggle("fetched") }, label = { Text(uiText(R.string.review_whats_new)) })
}
