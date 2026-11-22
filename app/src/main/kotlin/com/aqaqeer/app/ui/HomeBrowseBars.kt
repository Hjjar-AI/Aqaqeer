package com.aqaqeer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText
import com.aqaqeer.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeSectionsBar(active: List<Section>, selectedId: String?,
    outCounts: Map<String, Int>, selectSection: (String) -> Unit, availableSearchSections: Set<String> = emptySet()) {
    val uiText = rememberUiText()
    if (active.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        val density = LocalDensity.current
        val required = remember(active, style, density) {
            active.maxOf { section -> with(density) {
                measurer.measure(androidx.compose.ui.text.AnnotatedString(section.nameAr), style).size.width.toDp()
            } + UiSpacing.tabMeasurementAllowance } * active.size
        }
        val tabs: @Composable () -> Unit = {
            active.forEach { section ->
                val selected = selectedId == section.id
                val searchMatch = section.id in availableSearchSections
                Tab(selected = selected, onClick = { selectSection(section.id) },
                    selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedContentColor = if (searchMatch) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(MaterialTheme.shapes.medium)
                        .background(when { selected -> MaterialTheme.colorScheme.primary; searchMatch -> MaterialTheme.colorScheme.secondaryContainer; else -> Color.Transparent })
                        .testTag("section_${section.id}").semantics {
                        contentDescription = section.nameAr + if (searchMatch) " · " + uiText(R.string.review_section_search_stock) else ""
                    }) {
                    Row(Modifier.heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.related),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                        Box(Modifier.size(Size.tabIconSlot)) { if (searchMatch) AppIcon(UiIcon.SEARCH, Modifier.fillMaxSize()) }
                        Text(section.nameAr, style = style, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(AppStroke.hairline, MaterialTheme.colorScheme.outlineVariant)) {
            if (required <= maxWidth) PrimaryTabRow(selectedTabIndex = active.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.background, divider = {}, tabs = tabs)
            else PrimaryScrollableTabRow(selectedTabIndex = active.indexOfFirst { it.id == selectedId }.coerceAtLeast(0), edgePadding = Space.none,
                containerColor = MaterialTheme.colorScheme.background, divider = {}, tabs = tabs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeSearchControls(query: TextFieldValue, changeQuery: (TextFieldValue) -> Unit,
    searching: Boolean, filters: HomeFilters, visibleCount: Int, totalCount: Int,
    openFilters: () -> Unit, clearFilters: () -> Unit, compact: Boolean = false,
    showFetchedChip: Boolean = false, toggleFetched: () -> Unit = {},
    addMedication: (() -> Unit)? = null, addEnabled: Boolean = true) {
    val uiText = rememberUiText()
    val focus = LocalFocusManager.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compactFetchedControl = maxWidth < LayoutTokens.searchControlsCompactWidth * maxOf(1f, LocalLayoutMetrics.current.fontScale)
        val keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None)
        val keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
        val searchShape = AppShapeTokens.search
        val hasFilters = filters.hasAny
        val showSummary = searching || hasFilters
        val filterCount = filters.statuses.size + filters.categoryIds.size +
            listOf(filters.expiringSoon, filters.unpublishedOnly, filters.fetchedOnly).count { it }
        val leadingAction: @Composable () -> Unit = {
            if (query.text.isNotEmpty()) IconButton(
                onClick = { changeQuery(TextFieldValue("")); focus.clearFocus() },
                modifier = Modifier.testTag("clear_search")) {
                AppIcon(UiIcon.CLOSE, description = uiText(R.string.home_clear_search_description))
            } else AppIcon(UiIcon.SEARCH)
        }
        // Filters stay last; the optional What’s new chip sits immediately before them.
        val filterAction: @Composable () -> Unit = {
            IconButton(onClick = openFilters, modifier = Modifier.size(UiSpacing.filterButton).semantics {
                contentDescription = uiText(R.string.home_filters_action) +
                    if (filterCount > 0) uiText(R.string.home_selected_count_suffix, MedicationPresentation.displayDigits(filterCount.toString())) else ""
            }) {
                BadgedBox(badge = {
                    if (filterCount > 0) Badge(Modifier.clearAndSetSemantics { }) {
                        Text(MedicationPresentation.displayDigits(filterCount.toString()))
                    }
                }) { AppIcon(UiIcon.FILTER) }
            }
        }
        val trailingActions: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                if (showFetchedChip) {
                    if (compactFetchedControl) TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(uiText(R.string.review_whats_new)) } },
                        state = rememberTooltipState()) {
                        IconToggleButton(checked = filters.fetchedOnly, onCheckedChange = { toggleFetched() },
                            modifier = Modifier.size(UiSpacing.touch).semantics { selected = filters.fetchedOnly }) {
                            AppIcon(UiIcon.HISTORY, description = uiText(R.string.review_whats_new))
                        }
                    } else CompactFilterChip(filters.fetchedOnly, uiText(R.string.review_whats_new), toggleFetched)
                }
                filterAction()
            }
        }
        ContentSection {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                if (compact) {
                    val interaction = remember { MutableInteractionSource() }
                    BasicTextField(value = query, onValueChange = changeQuery, singleLine = true,
                        modifier = Modifier.weight(1f).heightIn(min = UiSpacing.touch).testTag("search"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), interactionSource = interaction,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(value = query.text, innerTextField = innerTextField,
                                enabled = true, singleLine = true, visualTransformation = VisualTransformation.None,
                                interactionSource = interaction, placeholder = { Text(uiText(R.string.home_search_placeholder)) },
                                leadingIcon = leadingAction, trailingIcon = trailingActions,
                                contentPadding = PaddingValues(horizontal = UiSpacing.section, vertical = UiSpacing.related),
                                container = {
                                    OutlinedTextFieldDefaults.Container(enabled = true, isError = false,
                                        interactionSource = interaction, colors = OutlinedTextFieldDefaults.colors(), shape = searchShape)
                                })
                        })
                } else OutlinedTextField(value = query, onValueChange = changeQuery, singleLine = true, shape = searchShape,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    placeholder = { Text(uiText(R.string.home_search_placeholder)) },
                    modifier = Modifier.weight(1f).testTag("search"), leadingIcon = leadingAction, trailingIcon = trailingActions)
                addMedication?.let { add ->
                    FilledIconButton(onClick = add, enabled = addEnabled,
                        modifier = Modifier.size(UiSpacing.touch), shape = MaterialTheme.shapes.medium) {
                        AppIcon(UiIcon.ADD, description = uiText(R.string.navigation_add_medication_title))
                    }
                }
            }
            if (showSummary) WrappingContent {
                if (showSummary) Text(uiText(R.string.home_results_count_format,
                    MedicationPresentation.displayDigits(visibleCount.toString()), MedicationPresentation.displayDigits(totalCount.toString())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasFilters) AppButton(variant = AppButtonVariant.QUIET, onClick = clearFilters) { Text(uiText(R.string.history_clear_filters_action)) }
            }
        }
    }
}

@Composable private fun CompactFilterChip(selected: Boolean, label: String, toggle: () -> Unit) {
    AppChip(selected = selected, onClick = toggle,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) })
}

@Composable internal fun HomeExtraFilters(filters: HomeFilters, admin: Boolean, toggle: (String) -> Unit) {
    WrappingContent { ExtraFilterChips(filters, admin, toggle) }
}
@Composable private fun ExtraFilterChips(filters: HomeFilters, admin: Boolean, toggle: (String) -> Unit) {
    val uiText = rememberUiText()
    AppChip(filters.expiringSoon, onClick = { toggle("expiry") }, label = { Text(uiText(R.string.review_expiry_soon)) })
    if (admin) AppChip(filters.unpublishedOnly, onClick = { toggle("unpublished") }, label = { Text(uiText(R.string.review_unpublished)) })
    AppChip(filters.fetchedOnly, onClick = { toggle("fetched") }, label = { Text(uiText(R.string.review_whats_new)) })
}
