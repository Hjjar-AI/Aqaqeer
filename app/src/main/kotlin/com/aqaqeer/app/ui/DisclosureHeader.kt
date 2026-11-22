package com.aqaqeer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import com.aqaqeer.app.R

internal enum class DisclosureStyle { PLAIN, SETTINGS, DRAWER }

@Composable internal fun DisclosureHeader(title: String, expanded: Boolean, toggle: () -> Unit,
    style: DisclosureStyle = DisclosureStyle.PLAIN, icon: UiIcon? = null) {
    val text = rememberUiText()
    val drawer = style == DisclosureStyle.DRAWER
    val settings = style == DisclosureStyle.SETTINGS
    AppButton(variant = if (drawer) AppButtonVariant.SECONDARY else AppButtonVariant.QUIET,
        onClick = toggle, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touch).semantics {
            stateDescription = text(if (expanded) R.string.shared_expanded_state_label else R.string.shared_collapsed_state_label)
        }, contentPadding = when (style) {
            DisclosureStyle.PLAIN -> ButtonDefaults.TextButtonContentPadding
            DisclosureStyle.SETTINGS -> PaddingValues(Space.none)
            DisclosureStyle.DRAWER -> PaddingValues(horizontal = UiSpacing.section, vertical = UiSpacing.related)
        }, shape = if (settings || drawer) MaterialTheme.shapes.medium else ButtonDefaults.shape,
        border = if (drawer) BorderStroke(AppStroke.hairline, MaterialTheme.colorScheme.outlineVariant) else null,
        colors = if (drawer) ButtonDefaults.outlinedButtonColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface) else ButtonDefaults.textButtonColors()) {
        if (drawer) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            icon?.let { AppIcon(it) }
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            ExpansionChevron(expanded)
        } else {
            Text(title, Modifier.weight(1f).then(if (settings) Modifier.semantics { heading() } else Modifier),
                style = if (settings) MaterialTheme.typography.titleMedium else LocalTextStyle.current)
            ExpansionChevron(expanded)
        }
    }
}
