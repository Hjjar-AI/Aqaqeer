package com.aqaqeer.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import com.aqaqeer.app.R

/** Availability owns its filled pill, edit affordance and stable interaction/progress slot. */
@Composable internal fun AvailabilityBadge(text: String, tone: StatusTone, modifier: Modifier = Modifier,
    dense: Boolean = false, onClick: (() -> Unit)? = null, editable: Boolean = onClick != null,
    enabled: Boolean = true, saving: Boolean = false,
    actionDescription: String = text, actionLabel: String = text) {
    val container by animateColorAsState(tone.container, tween(Motion.standard), label = "availability container")
    val content by animateColorAsState(tone.content, tween(Motion.standard), label = "availability content")
    val savingDescription = rememberUiText()(R.string.availability_saving_description)
    val canClick = editable && enabled && !saving && onClick != null
    val interaction = if (!editable) Modifier else Modifier
        .sizeIn(minWidth = UiSpacing.touch, minHeight = UiSpacing.touch)
        .then(if (canClick) Modifier.clickable(role = Role.Button, onClickLabel = actionLabel) { onClick?.invoke() } else Modifier)
        .clearAndSetSemantics {
            role = Role.Button
            contentDescription = actionDescription
            if (saving) stateDescription = savingDescription
            if (!canClick) disabled()
            if (canClick) this.onClick(label = actionLabel) { onClick.invoke(); true }
        }
    Box(modifier.then(interaction), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.width(IntrinsicSize.Max), color = container, contentColor = content, shape = CircleShape) {
            Row(Modifier.padding(horizontal = if (dense) UiSpacing.badgeHorizontal else UiSpacing.section,
                vertical = if (dense) UiSpacing.hair else UiSpacing.badgeVertical), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                Text(text, modifier = Modifier.weight(1f, fill = false),
                    style = if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    fontWeight = if (dense) FontWeight.Normal else FontWeight.Bold)
                if (editable) Box(Modifier.size(Size.iconS), contentAlignment = Alignment.Center) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
                        color = content, strokeWidth = AppStroke.emphasis)
                    else AppIcon(UiIcon.EDIT, size = Size.iconS)
                }
            }
        }
    }
}
