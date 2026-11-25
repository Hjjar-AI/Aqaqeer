package com.aqaqeer.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.aqaqeer.app.R

/** The caller supplies the current step and owns every workflow transition. */
@Composable internal fun WorkflowStepCard(
    title: String,
    index: Int,
    current: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val active = index == current
    AppCard(Modifier.fillMaxWidth().animateContentSize(tween(Motion.standard))) {
        WorkflowStepLabel(title, index, current,
            modifier = Modifier.background(if (active) colors.primaryContainer else colors.surface))
        if (active) {
            ContentSection(modifier = Modifier.padding(UiSpacing.section), spacing = UiSpacing.section, content = content)
        }
    }
}

/** A compact progress summary for flows whose buttons and gates are owned by the surrounding screen. */
@Composable internal fun StepHeader(steps: List<String>, current: Int) {
    require(current in steps.indices)
    val text = rememberUiText()
    var expanded by rememberSaveable { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth().animateContentSize(tween(Motion.standard))) {
        WorkflowStepLabel(steps[current], current, current,
            progress = text(R.string.shared_step_position, current + 1, steps.size),
            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
            expanded = expanded, toggle = if (steps.size > 1) ({ expanded = !expanded }) else null)
        if (expanded) {
            steps.forEachIndexed { index, title ->
                if (index != current) WorkflowStepLabel(title, index, current)
            }
        }
    }
}

@Composable private fun WorkflowStepLabel(
    title: String,
    index: Int,
    current: Int,
    progress: String? = null,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    toggle: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val text = rememberUiText()
    val active = index == current
    val complete = index < current
    val status = text(when {
        active -> R.string.shared_step_current
        complete -> R.string.shared_step_completed
        else -> R.string.shared_step_pending
    })
    val disclosureState = toggle?.let {
        text(if (expanded) R.string.shared_expanded_state_label else R.string.shared_collapsed_state_label)
    }
    val description = listOfNotNull(status, progress, disclosureState).joinToString(" · ")
    val disclosure = if (toggle == null) Modifier else Modifier.clickable(
        role = Role.Button, onClickLabel = text(R.string.shared_other_steps), onClick = toggle)
    Row(modifier.fillMaxWidth().then(disclosure).semantics(mergeDescendants = true) {
        heading()
        stateDescription = description
    }.padding(horizontal = UiSpacing.section, vertical = UiSpacing.related),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
        Surface(modifier = Modifier.clearAndSetSemantics { }, shape = MaterialTheme.shapes.small,
            color = when {
                active -> colors.primary
                complete -> colors.primaryContainer
                else -> colors.surfaceVariant
            }, contentColor = when {
                active -> colors.onPrimary
                complete -> colors.onPrimaryContainer
                else -> colors.onSurfaceVariant
            }) {
            Box(Modifier.sizeIn(minWidth = Size.avatarS, minHeight = Size.avatarS).padding(Space.xs),
                contentAlignment = Alignment.Center) {
                if (complete) AppIcon(UiIcon.CHECK, size = Size.iconControl)
                else Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (active) colors.onPrimaryContainer else colors.onSurface)
            Text(progress ?: status, Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelMedium,
                color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        if (toggle != null) ExpansionChevron(expanded)
    }
}
