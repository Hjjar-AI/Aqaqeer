package com.aqaqeer.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.data.ExpiryState
import com.aqaqeer.app.data.Medication
import com.aqaqeer.app.data.MedicationPresentation
import java.time.LocalDate

@Composable internal fun ExpiryBadge(medication: Medication, today: LocalDate, dense: Boolean = false) {
    val expiry = remember(medication, today) { MedicationPresentation.expiry(medication, today) }
    expiry?.let { ExpiryBadge(it, dense, Modifier.testTag("expiry_${medication.id}")) }
}

/** Expiry owns its outline/calendar treatment; it never exposes an availability edit action. */
@Composable internal fun ExpiryBadge(expiry: ExpiryState, dense: Boolean, modifier: Modifier = Modifier) {
    val presentation = expiryBadge(expiry)
    val content by animateColorAsState(presentation.tone.content, tween(Motion.standard), label = "expiry content")
    val border by animateColorAsState(presentation.tone.edge, tween(Motion.standard), label = "expiry border")
    Surface(modifier = modifier.width(IntrinsicSize.Max), color = Color.Transparent, contentColor = content,
        shape = MaterialTheme.shapes.small, border = BorderStroke(AppStroke.badge, border)) {
        Row(Modifier.padding(horizontal = if (dense) UiSpacing.badgeHorizontal else UiSpacing.section,
            vertical = if (dense) UiSpacing.hair else UiSpacing.badgeVertical), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            AppIcon(UiIcon.CALENDAR, size = if (dense) Size.iconS else Size.iconM)
            Text(presentation.text, modifier = Modifier.weight(1f, fill = false),
                style = if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize))
        }
    }
}
