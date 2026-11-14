package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*
import java.time.LocalDate

@Composable internal fun MedicationNameLines(ar: String?, en: String?, mode: NameDisplay, prominent: Boolean = false,
    query: String = "", primaryStyle: TextStyle = MaterialTheme.typography.titleLarge, maxLines: Int = Int.MAX_VALUE,
    secondary: Boolean = false, strength: String? = null, titleTrailing: (@Composable () -> Unit)? = null) {
    val separateStrength = strength != null && maxLines != Int.MAX_VALUE
    MedicationPresentation.names(ar, en, mode).forEachIndexed { index, name ->
        val highlight = MaterialTheme.colorScheme.primary
        val text = remember(name, query, highlight, strength, index, separateStrength) { buildAnnotatedString {
            if (name.english) append('\u2066')
            val start = length
            append(name.text)
            MedicationPresentation.matchRanges(name.text, query).forEach { range ->
                addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold), start + range.first, start + range.last + 1)
            }
            if (strength != null && index == 0 && !separateStrength) append(" - " + if (name.english) MedicationPresentation.westernDigits(strength) else bidi(MedicationPresentation.westernDigits(strength)))
            if (name.english) append('\u2069')
        } }
        val line: @Composable (Modifier) -> Unit = { modifier ->
            Text(text, modifier = modifier.then(if (secondary) Modifier.semantics { contentDescription = uiText(R.string.ui_medication_components_001, name.text) } else Modifier),
                fontFamily = if (name.english) FontFamily.SansSerif else MedicationHeadingFont,
                style = (if (prominent && index == 0) primaryStyle else if (secondary) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                    .copy(textDirection = if (name.english) TextDirection.Ltr else TextDirection.ContentOrRtl),
                color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                fontWeight = if (prominent && index == 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
        if (index == 0 && titleTrailing != null) Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            line(Modifier.weight(1f).alignByBaseline())
            Box(Modifier.alignBy { it.measuredHeight }) { titleTrailing() }
        } else line(Modifier)
        // A bounded name must never ellipsize its dose. Let the dose wrap independently.
        if (index == 0 && separateStrength) Text("- " + bidi(MedicationPresentation.westernDigits(strength.orEmpty())),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable internal fun MedicationSummary(medication: Medication, names: NameDisplay = NameDisplay.BOTH,
    prominent: Boolean = false, form: String? = null, query: String = "",
    primaryStyle: TextStyle = MaterialTheme.typography.titleLarge, titleTrailing: (@Composable () -> Unit)? = null) {
    MedicationNameLines(medication.genericAr, medication.genericEn, names, prominent, query, primaryStyle,
        strength = medication.strength, titleTrailing = titleTrailing)
    if (medication.nameAr != null || medication.nameEn != null) {
        Text(uiText(R.string.ui_medication_components_002), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MedicationNameLines(medication.nameAr, medication.nameEn, names, secondary = true)
    }
    form?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable internal fun statusTone(status: AvailabilityStatus): StatusTone = LocalStatusColors.current.let {
    when (status) {
        AvailabilityStatus.AVAILABLE -> it.available
        AvailabilityStatus.LOW -> it.low
        AvailabilityStatus.OUT -> it.out
        AvailabilityStatus.UNKNOWN -> it.unknown
    }
}

@Composable internal fun StatusBadge(status: AvailabilityStatus, onClick: (() -> Unit)? = null, editLabel: String? = null,
    dense: Boolean = false) {
    val tone = statusTone(status)
    val symbol = when (status) {
        AvailabilityStatus.AVAILABLE -> UiIcon.CHECK
        AvailabilityStatus.LOW -> UiIcon.WARNING
        AvailabilityStatus.OUT -> UiIcon.CLOSE
        AvailabilityStatus.UNKNOWN -> UiIcon.UNKNOWN
    }
    MedicationBadge(statusLabel(status), tone, dense = dense, icon = symbol, onClick = onClick,
        actionDescription = "${statusLabel(status)} · ${editLabel.orEmpty()}", actionLabel = uiText(R.string.ui_pharmacy_admin_screen_120))
}

@Composable internal fun MedicationCard(row: MedicationRowUi, sectionId: String?,
    preferences: HomePreferences, today: LocalDate, onQuickEdit: (() -> Unit)?,
    bulkSelected: Boolean?, toggleSelection: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    selectionEnabled: Boolean = true, query: String = "", unpublished: Boolean = false,
    onLongClick: (() -> Unit)? = null, highlighted: Boolean = false) {
    val medication = row.medication
    val compact = preferences.layout == CardLayout.COMPACT
    val form = row.form
    val name = MedicationPresentation.identity(medication, form?.nameAr ?: uiText(R.string.ui_aqaqeer_app_026))
    val status = row.status
    val edge by animateColorAsState(statusTone(status).edge, tween(180), label = "status edge")
    val unavailableStripe = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pendingMarker: (@Composable () -> Unit)? = if (unpublished) ({
        Box(Modifier.size(UiSpacing.pendingMarker).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            .semantics { contentDescription = uiText(R.string.ui_home_screen_044) })
    }) else null
    val interaction = if (bulkSelected == null) Modifier.combinedClickable(role = Role.Button, onClick = onClick,
        onLongClick = onLongClick, onLongClickLabel = if (onLongClick != null) uiText(R.string.ui_medication_components_003) else null)
        else Modifier.toggleable(value = bulkSelected, enabled = selectionEnabled, role = Role.Checkbox,
            onValueChange = { toggleSelection() }).semantics {
                contentDescription = uiText(R.string.ui_medication_components_004, name, statusLabel(status)) + if (unpublished) uiText(R.string.ui_medication_components_005) else ""
            }
    val quickEditAction = if (bulkSelected == null && onQuickEdit != null) Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(uiText(R.string.ui_pharmacy_admin_screen_120)) {
            onQuickEdit(); true
        })
    } else Modifier
    Card(modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).then(interaction).then(quickEditAction).testTag("med_${medication.nameEn ?: medication.id}"),
        border = BorderStroke(1.dp, if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = if (bulkSelected == true || highlighted)
            MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().drawBehind {
            if (status == AvailabilityStatus.OUT) {
                // Decoration stays behind content so names and controls retain full opacity.
                val spacing = 16.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(unavailableStripe, Offset(x, 0f), Offset(x + size.height, size.height), 2.dp.toPx())
                    x += spacing
                }
            }
            val stroke = 4.dp.toPx()
            val x = if (rtl) size.width - stroke / 2 else stroke / 2
            drawLine(edge, Offset(x, 0f), Offset(x, size.height), stroke)
        }.padding(if (compact) UiSpacing.related else UiSpacing.section), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            if (bulkSelected != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = bulkSelected, onCheckedChange = null, enabled = selectionEnabled,
                    modifier = Modifier.then(if (compact) Modifier.size(24.dp) else Modifier)
                        .testTag("select_${medication.id}").clearAndSetSemantics { })
                if (!compact) Text(uiText(R.string.ui_medication_components_006), modifier = Modifier.clearAndSetSemantics { }, style = MaterialTheme.typography.labelMedium)
            }
            if (compact) {
                MedicationNameLines(medication.genericAr, medication.genericEn, preferences.names,
                    prominent = true, query = query, primaryStyle = MaterialTheme.typography.titleMedium, maxLines = 2,
                    strength = medication.strength, titleTrailing = pendingMarker)
                Text(form?.nameAr ?: uiText(R.string.ui_aqaqeer_app_026), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MedicationSummary(medication, preferences.names, prominent = true, query = query, titleTrailing = pendingMarker)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                    Text(form?.nameAr ?: uiText(R.string.ui_aqaqeer_app_026), modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
                    form?.let { DosageFormIcon(it) }
                }
            }
            MedicationBadges(medication, today, sectionId?.let { status }, onQuickEdit, name)
            if (!compact) LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_073), row.category)
            val elsewhere = row.elsewhere
            if (elsewhere.isNotEmpty()) Text(uiText(R.string.ui_medication_components_007) + elsewhere.joinToString(uiText(R.string.ui_medication_components_008)) { it.nameAr },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("tip_${medication.id}"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MedicationBadges(medication: Medication, today: LocalDate, status: AvailabilityStatus?,
    onQuickEdit: (() -> Unit)?, name: String) {
    if (status == null && MedicationPresentation.expiry(medication, today) == null) return
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        if (status != null) Box(Modifier.align(Alignment.CenterVertically)) {
            StatusBadge(status, onQuickEdit, uiText(R.string.ui_medication_components_009, name), dense = true)
        }
        if (MedicationPresentation.expiry(medication, today) != null)
            Box(Modifier.align(Alignment.CenterVertically)) { ExpiryBadge(medication, today, dense = true) }
    }
}

@Composable internal fun ExpiryBadge(medication: Medication, today: LocalDate, dense: Boolean = false) {
    MedicationPresentation.expiry(medication, today)?.let { expiry ->
        val expired = expiry == ExpiryState.EXPIRED
        val tone = if (expired) LocalStatusColors.current.out else LocalStatusColors.current.low
        MedicationBadge(if (expired) uiText(R.string.ui_medication_components_010) else uiText(R.string.ui_medication_components_011), tone, dense = dense,
            icon = UiIcon.WARNING, modifier = Modifier.testTag("expiry_${medication.id}"))
    }
}
