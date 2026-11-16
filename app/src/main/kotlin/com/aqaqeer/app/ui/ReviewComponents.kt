package com.aqaqeer.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText

/** Screen-owned expansion lets drawers, accordions and settings use different policies. */
@Composable internal fun CollapsibleGroup(title: String, expanded: Boolean, toggle: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().animateContentSize()) {
        TextButton(onClick = toggle, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touch)
            .semantics { stateDescription = uiText(if (expanded) R.string.ui_home_drawer_010 else R.string.ui_home_drawer_011) }) {
            Text(title, Modifier.weight(1f))
            Text(if (expanded) "▴" else "▾")
        }
        if (expanded) ContentSection(content = content)
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
    val clipboard = LocalClipboardManager.current
    var copied by remember(value) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SupportingText(label); Text(bidi(value)) }
        TextButton(onClick = { clipboard.setText(AnnotatedString(value)); copied = true }) {
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
