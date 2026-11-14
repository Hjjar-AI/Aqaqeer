package com.aqaqeer.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared visual treatment; interactive badges keep a full touch target around the same pill. */
@Composable internal fun MedicationBadge(text: String, tone: StatusTone, modifier: Modifier = Modifier,
    dense: Boolean = false, icon: UiIcon? = null, onClick: (() -> Unit)? = null,
    actionDescription: String = text, actionLabel: String = text) {
    val container by animateColorAsState(tone.container, tween(180), label = "badge container")
    val content by animateColorAsState(tone.content, tween(180), label = "badge content")
    val interaction = if (onClick == null) Modifier else Modifier
        .sizeIn(minWidth = UiSpacing.touch, minHeight = UiSpacing.touch)
        .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
        .clearAndSetSemantics {
            role = Role.Button
            contentDescription = actionDescription
            this.onClick(label = actionLabel) { onClick(); true }
        }
    Box(modifier.then(interaction), contentAlignment = Alignment.Center) {
        Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
            Row(Modifier.padding(horizontal = if (dense) UiSpacing.hair else UiSpacing.badgeHorizontal,
                vertical = UiSpacing.hair), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                if (!dense && icon != null) AppIcon(icon, modifier = Modifier.size(18.dp))
                Text(text, style = if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (dense) FontWeight.Normal else FontWeight.Bold)
            }
        }
    }
}
