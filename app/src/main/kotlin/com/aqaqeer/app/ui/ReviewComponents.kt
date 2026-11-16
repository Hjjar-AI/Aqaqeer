package com.aqaqeer.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText

/** Screen-owned expansion lets drawers, accordions and settings use different policies. */
@Composable internal fun CollapsibleGroup(title: String, expanded: Boolean, toggle: () -> Unit,
    modifier: Modifier = Modifier, contentSpacing: Dp = UiSpacing.related, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().animateContentSize()) {
        TextButton(onClick = toggle, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touch)
            .semantics { stateDescription = uiText(if (expanded) R.string.ui_home_drawer_010 else R.string.ui_home_drawer_011) }) {
            Text(title, Modifier.weight(1f))
            Text(if (expanded) "▴" else "▾")
        }
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
                TextButton(enabled = enabled, onClick = notice.action) { Text(notice.actionLabel) }
        }
    }
}

@Composable internal fun StepHeader(steps: List<String>, current: Int) {
    require(steps.isNotEmpty() && current in steps.indices)
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        Text("${current + 1} / ${steps.size} · ${steps[current]}", style = MaterialTheme.typography.titleMedium)
        // Discrete named steps describe workflow state, not estimated work or time.
        steps.forEachIndexed { index, label ->
            SupportingText("${if (index < current) "✓" else if (index == current) "●" else "○"} $label")
        }
    }
}

@Composable internal fun AdaptivePickers(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth < 480.dp * LocalDensity.current.fontScale) Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            first(); second()
        } else Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() }
        }
    }
}

@Composable internal fun EmptyState(title: String, body: String? = null, actions: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(UiSpacing.group), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        AppIcon(UiIcon.FORM_CAPSULE, Modifier.size(96.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        body?.let { SupportingText(it) }
        actions?.invoke()
    }
}

@Composable internal fun CopyableValue(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(value) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SupportingText(label); Text(bidi(value)) }
        TextButton(onClick = { scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            copied = true
        } }) {
            Text(uiText(if (copied) R.string.review_copied else R.string.review_copy))
        }
    }
}

@Composable internal fun DestructiveConfirmButton(enabled: Boolean = true, onClick: () -> Unit, label: String) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text(label) }
}

/** Anchored menu; the screen freezes and validates its selection when opening it. */
@Composable internal fun EditableStatusPill(status: com.aqaqeer.app.data.AvailabilityStatus,
    onOpen: (() -> Unit)?, expanded: Boolean, dismiss: () -> Unit,
    choose: (com.aqaqeer.app.data.AvailabilityStatus) -> Unit, enabled: Boolean = true, dense: Boolean = false) {
    Box(Modifier.heightIn(min = UiSpacing.touch), contentAlignment = Alignment.Center) {
        StatusBadge(status, if (enabled) onOpen else null, dense = dense)
        DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
            com.aqaqeer.app.data.MedicationPresentation.editableStatuses.forEach { candidate ->
                DropdownMenuItem(text = { Text(statusLabel(candidate)) }, enabled = enabled && candidate != status,
                    onClick = { choose(candidate) }, trailingIcon = if (candidate == status) ({ AppIcon(UiIcon.CHECK) }) else null)
            }
        }
    }
}

@Composable internal fun SettingsGroup(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    GroupedCard { CollapsibleGroup(title, expanded, { expanded = !expanded }, content = content) }
}

@Composable internal fun CardLayoutPicker(selected: com.aqaqeer.app.data.CardLayout, change: (com.aqaqeer.app.data.CardLayout) -> Unit) {
    AdaptivePickers(first = { LayoutChoice(com.aqaqeer.app.data.CardLayout.COMPACT, selected, change) },
        second = { LayoutChoice(com.aqaqeer.app.data.CardLayout.EXPANDED, selected, change) })
}
@Composable private fun LayoutChoice(layout: com.aqaqeer.app.data.CardLayout, selected: com.aqaqeer.app.data.CardLayout,
    change: (com.aqaqeer.app.data.CardLayout) -> Unit) {
    FilterChip(selected == layout, onClick = { change(layout) }, label = {
        Column { Text(uiText(if (layout == com.aqaqeer.app.data.CardLayout.COMPACT) R.string.ui_aqaqeer_app_051 else R.string.ui_aqaqeer_app_052))
            repeat(if (layout == com.aqaqeer.app.data.CardLayout.COMPACT) 2 else 4) {
                HorizontalDivider(Modifier.width(72.dp).padding(vertical = 2.dp))
            }
        }
    })
}
@Composable internal fun MedicationLayoutPreview(layout: com.aqaqeer.app.data.CardLayout) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        Text(uiText(R.string.ui_aqaqeer_app_053), style = MaterialTheme.typography.labelMedium)
        Text(uiText(R.string.ui_aqaqeer_app_054), style = if (layout == com.aqaqeer.app.data.CardLayout.COMPACT) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
        if (layout == com.aqaqeer.app.data.CardLayout.EXPANDED) SupportingText(uiText(R.string.ui_aqaqeer_app_055))
        StatusBadge(com.aqaqeer.app.data.AvailabilityStatus.AVAILABLE)
    }
}

@Composable internal fun SetupChecklist(ready: com.aqaqeer.app.data.CatalogState.Ready, navigate: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pharmacy = ready.catalog.pharmacy?.pharmacyId ?: return
    val prefs = remember { context.getSharedPreferences("setup_checklist", android.content.Context.MODE_PRIVATE) }
    var hidden by rememberSaveable(pharmacy) { mutableStateOf(prefs.getBoolean(pharmacy, false)) }
    val tasks = listOf(
        Triple(R.string.review_check_catalog, ready.catalog.medications.isNotEmpty(), "edit-med:new"),
        Triple(R.string.review_check_connection, ready.hasPublicationConnection, "setup"),
        Triple(R.string.review_check_publish, ready.publishedAt != null, "review"),
        Triple(R.string.review_check_backup, ready.hasBackup, "backup"))
    if (!hidden && !ready.requiresUpdate && !ready.maintenancePaused && !ready.restorePaused) GroupedCard(title = uiText(R.string.review_setup_checklist)) {
        tasks.forEach { (label, complete, route) ->
            TextButton(onClick = { navigate(route) }, enabled = !complete) { Text("${if (complete) "✓" else "○"} ${uiText(label)}") }
        }
        if (tasks.all { it.second }) TextButton(onClick = { prefs.edit().putBoolean(pharmacy, true).apply(); hidden = true }) {
            Text(uiText(R.string.review_hide_checklist))
        }
    }
}
