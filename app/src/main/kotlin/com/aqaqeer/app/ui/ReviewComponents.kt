package com.aqaqeer.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText

/** Screen-owned expansion lets drawers, accordions and settings use different policies. */
@Composable internal fun CollapsibleGroup(title: String, expanded: Boolean, toggle: () -> Unit,
    modifier: Modifier = Modifier, contentSpacing: Dp = UiSpacing.related, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().animateContentSize()) {
        DisclosureHeader(title, expanded, toggle)
        if (expanded) ContentSection(spacing = contentSpacing, content = content)
    }
}

internal enum class NoticeSeverity { INFO, WARNING, ERROR }
internal data class Notice(val text: String, val severity: NoticeSeverity = NoticeSeverity.INFO,
    val actionLabel: String? = null, val action: (() -> Unit)? = null)

@Composable internal fun NoticeStrip(notice: Notice, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = when (notice.severity) {
            NoticeSeverity.ERROR -> colors.errorContainer
            NoticeSeverity.WARNING -> LocalStatusColors.current.low.container
            NoticeSeverity.INFO -> colors.surfaceVariant
        }) {
        Column(Modifier.padding(UiSpacing.related), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                AppIcon(if (notice.severity == NoticeSeverity.INFO) UiIcon.INFO else UiIcon.WARNING)
                Text(notice.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            if (notice.actionLabel != null && notice.action != null)
                AppButton(variant = AppButtonVariant.QUIET, enabled = enabled, onClick = notice.action) { Text(notice.actionLabel) }
        }
    }
}

@Composable internal fun StepHeader(steps: List<String>, current: Int) {
    require(steps.isNotEmpty() && current in steps.indices)
    val uiText = rememberUiText()
    fun stepState(index: Int) = uiText(when {
        index < current -> R.string.shared_step_completed
        index == current -> R.string.shared_step_current
        else -> R.string.shared_step_pending
    })
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        Text("${current + 1} / ${steps.size} · ${steps[current]}", style = MaterialTheme.typography.titleMedium)
        val compactHeight = compactHeight()
        if (compactHeight) {
            Row(Modifier.semantics {
                contentDescription = steps.mapIndexed { index, label -> "${index + 1}. $label · ${stepState(index)}" }.joinToString("; ")
            }, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                steps.forEachIndexed { index, _ ->
                    Box(Modifier.size(Size.dotS).background(
                        if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape))
                }
            }
        } else steps.forEachIndexed { index, label ->
            Box(Modifier.clearAndSetSemantics { contentDescription = label; stateDescription = stepState(index) }) {
                SupportingText("${if (index < current) "✓" else if (index == current) "●" else "○"} $label")
            }
        }
    }
}

@Composable internal fun AdaptivePickers(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth < LayoutTokens.pickerStackWidth * LocalLayoutMetrics.current.fontScale) Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            first(); second()
        } else Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() }
        }
    }
}

@Composable internal fun EmptyState(title: String, body: String? = null, actions: (@Composable () -> Unit)? = null) {
    val compactHeight = compactHeight()
    Column(Modifier.fillMaxWidth().padding(if (compactHeight) UiSpacing.section else UiSpacing.group), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        EmptyCapsule(Modifier.size(if (compactHeight) Size.illustrationSmall else Size.illustrationLarge))
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let { SupportingText(it) }
        actions?.invoke()
    }
}

@Composable internal fun CopyableValue(label: String, value: String) {
    val uiText = rememberUiText()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(value) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SupportingText(label); Text(bidi(value)) }
        AppButton(variant = AppButtonVariant.QUIET, onClick = { scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            copied = true
        } }) {
            Text(uiText(if (copied) R.string.review_copied else R.string.review_copy))
        }
    }
}

@Composable internal fun DestructiveConfirmButton(enabled: Boolean = true, onClick: () -> Unit, label: String) {
    AppButton(onClick = onClick, enabled = enabled, variant = AppButtonVariant.DESTRUCTIVE) { Text(label) }
}

/** Anchored menu; the screen freezes and validates its selection when opening it. */
@Composable internal fun EditableStatusPill(status: com.aqaqeer.app.data.AvailabilityStatus,
    onOpen: (() -> Unit)?, expanded: Boolean, dismiss: () -> Unit,
    choose: (com.aqaqeer.app.data.AvailabilityStatus) -> Unit, enabled: Boolean = true, dense: Boolean = false,
    editable: Boolean = onOpen != null) {
    val uiText = rememberUiText()
    Box(if (editable) Modifier.sizeIn(minWidth = UiSpacing.touch, minHeight = UiSpacing.touch) else Modifier, contentAlignment = Alignment.Center) {
        StatusBadge(status, if (enabled) onOpen else null, dense = dense)
        DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
            com.aqaqeer.app.data.MedicationPresentation.editableStatuses.forEach { candidate ->
                DropdownMenuItem(text = { Text(statusLabel(candidate, text = uiText)) }, enabled = enabled && candidate != status,
                    onClick = { choose(candidate) }, trailingIcon = if (candidate == status) ({ AppIcon(UiIcon.CHECK) }) else null)
            }
        }
    }
}

@Composable internal fun SettingsGroup(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    GroupedCard {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            DisclosureHeader(title, expanded, { expanded = !expanded }, style = DisclosureStyle.SETTINGS)
            if (expanded) ContentSection(modifier = Modifier.padding(top = UiSpacing.related), spacing = UiSpacing.group, content = content)
        }
    }
}

/** Settings choices keep a stable aligned row per value instead of mixing wrapped chips. */
@Composable internal fun <T> SettingsChoiceRows(title: String, choices: List<T>, selected: T,
    select: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier,
    choiceModifier: (T) -> Modifier = { Modifier },
    swatch: ((T) -> androidx.compose.ui.graphics.Color)? = null) {
    ContentSection(modifier = modifier) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        RadioChoiceRows(choices, selected, select, label, choiceModifier = choiceModifier) { choice ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                swatch?.let { Box(Modifier.size(Size.dotL).background(it(choice), CircleShape)) }
                Text(label(choice), Modifier.weight(1f))
            }
        }
    }
}

@Composable internal fun CardLayoutPicker(selected: com.aqaqeer.app.data.CardLayout, change: (com.aqaqeer.app.data.CardLayout) -> Unit) {
    val uiText = rememberUiText()
    ContentSection {
        Text(uiText(R.string.settings_medication_cards_label), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        AdaptivePickers(first = { LayoutChoice(com.aqaqeer.app.data.CardLayout.COMPACT, selected, change) },
            second = { LayoutChoice(com.aqaqeer.app.data.CardLayout.EXPANDED, selected, change) })
    }
}
@Composable private fun LayoutChoice(layout: com.aqaqeer.app.data.CardLayout, selected: com.aqaqeer.app.data.CardLayout,
    change: (com.aqaqeer.app.data.CardLayout) -> Unit) {
    val uiText = rememberUiText()
    AppChip(selected == layout, onClick = { change(layout) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, label = {
        Column { Text(uiText(if (layout == com.aqaqeer.app.data.CardLayout.COMPACT) R.string.settings_card_compact_label else R.string.settings_card_expanded_label))
            repeat(if (layout == com.aqaqeer.app.data.CardLayout.COMPACT) 2 else 4) {
                HorizontalDivider(Modifier.width(Size.layoutPreviewDivider).padding(vertical = Space.xxs))
            }
        }
    })
}
@Composable internal fun MedicationLayoutPreview(layout: com.aqaqeer.app.data.CardLayout) {
    val uiText = rememberUiText()
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        border = BorderStroke(AppStroke.hairline, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(UiSpacing.section), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            Text(uiText(R.string.settings_card_preview_label), style = MaterialTheme.typography.labelMedium)
            Text(uiText(R.string.settings_card_preview_scientific_name), style = if (layout == com.aqaqeer.app.data.CardLayout.COMPACT) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
            if (layout == com.aqaqeer.app.data.CardLayout.EXPANDED) SupportingText(uiText(R.string.settings_card_preview_details))
            StatusBadge(com.aqaqeer.app.data.AvailabilityStatus.AVAILABLE)
        }
    }
}

@Composable internal fun SetupChecklist(ready: com.aqaqeer.app.data.CatalogState.Ready, navigate: (Route) -> Unit) {
    val uiText = rememberUiText()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pharmacy = ready.catalog.pharmacy?.pharmacyId ?: return
    val prefs = remember { context.getSharedPreferences("setup_checklist", android.content.Context.MODE_PRIVATE) }
    var hidden by rememberSaveable(pharmacy) { mutableStateOf(prefs.getBoolean(pharmacy, false)) }
    val tasks = listOf(
        Triple(R.string.review_check_catalog, ready.catalog.medications.isNotEmpty(), Route.EditMedication()),
        Triple(R.string.review_check_connection, ready.hasPublicationConnection, Route.Setup),
        Triple(R.string.review_check_publish, ready.publishedAt != null, Route.Review),
        Triple(R.string.review_check_backup, ready.hasBackup, Route.Backup))
    if (!hidden && !ready.requiresUpdate && !ready.maintenancePaused && !ready.restorePaused) GroupedCard(title = uiText(R.string.review_setup_checklist)) {
        tasks.forEach { (label, complete, route) ->
            AppButton(variant = AppButtonVariant.QUIET, onClick = { navigate(route) }, enabled = !complete,
                modifier = Modifier.semantics { stateDescription = uiText(if (complete) R.string.shared_step_completed else R.string.shared_step_pending) }) {
                Text("${if (complete) "✓" else "○"} ${uiText(label)}", Modifier.clearAndSetSemantics { contentDescription = uiText(label) })
            }
        }
        if (tasks.all { it.second }) AppButton(variant = AppButtonVariant.QUIET, onClick = { prefs.edit().putBoolean(pharmacy, true).apply(); hidden = true }) {
            Text(uiText(R.string.review_hide_checklist))
        }
    }
}
