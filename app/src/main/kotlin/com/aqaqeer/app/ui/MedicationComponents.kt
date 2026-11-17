package com.aqaqeer.app.ui

import androidx.compose.runtime.SideEffect

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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.aqaqeer.app.data.*
import java.time.LocalDate

@Composable internal fun MedicationNameLines(ar: String?, en: String?, mode: NameDisplay, prominent: Boolean = false,
    query: String = "", primaryStyle: TextStyle = MaterialTheme.typography.titleLarge, maxLines: Int = Int.MAX_VALUE,
    secondary: Boolean = false, strength: String? = null, titleTrailing: (@Composable () -> Unit)? = null) {
    val uiText = rememberUiText()
    val separateStrength = strength != null && maxLines != Int.MAX_VALUE
    val names = remember(ar, en, mode) { MedicationPresentation.names(ar, en, mode) }
    names.forEachIndexed { index, name ->
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
    val uiText = rememberUiText()
    MedicationNameLines(medication.genericAr, medication.genericEn, names, prominent, query, primaryStyle,
        strength = medication.strength, titleTrailing = titleTrailing)
    if (medication.nameAr != null || medication.nameEn != null) {
        val commercial = MedicationPresentation.names(medication.nameAr, medication.nameEn, names).joinToString(" · ") { it.text }
        Text(uiText(R.string.ui_medication_components_002) + ": " + bidi(commercial),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val uiText = rememberUiText()
    val presentation = availabilityBadge(status)
    MedicationBadge(presentation.text, presentation.tone, dense = dense, icon = presentation.icon, onClick = onClick,
        actionDescription = "${presentation.text} · ${editLabel.orEmpty()}", actionLabel = uiText(R.string.ui_pharmacy_admin_screen_120))
}

@Composable internal fun MedicationCard(row: MedicationRowUi, sectionId: String?,
    preferences: HomePreferences, today: LocalDate, onQuickEdit: (() -> Unit)?,
    bulkSelected: Boolean?, toggleSelection: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier,
    selectionEnabled: Boolean = true, query: String = "", unpublished: Boolean = false,
    onLongClick: (() -> Unit)? = null, highlighted: Boolean = false,
    reserveSelectionSpace: Boolean = false, quickMenuOpen: Boolean = false, dismissQuickMenu: () -> Unit = {}, chooseStatus: (AvailabilityStatus) -> Unit = {}) {
    val uiText = rememberUiText()
    val medication = row.medication
    val compact = preferences.layout == CardLayout.COMPACT
    val form = row.form
    val formName = form?.nameAr ?: uiText(R.string.ui_aqaqeer_app_026)
    val name = remember(medication, formName) { MedicationPresentation.identity(medication, formName) }
    val status = row.status
    val edge by animateColorAsState(statusTone(status).edge, tween(Motion.standard), label = "status edge")
    val unavailableStripe = MaterialTheme.colorScheme.onSurface.copy(alpha = Alpha.stripe)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pendingMarker: (@Composable () -> Unit)? = if (unpublished || row.fetched) ({
        Column {
            if (unpublished) Text(changeBadge(fetched = false).text, style = MaterialTheme.typography.labelSmall)
            if (row.fetched) Text(changeBadge(fetched = true).text, style = MaterialTheme.typography.labelSmall)
        }
    }) else null
    val interaction = if (bulkSelected == null) Modifier.combinedClickable(role = Role.Button, onClick = onClick,
        onLongClick = onLongClick, hapticFeedbackEnabled = false, onLongClickLabel = if (onLongClick != null) uiText(R.string.ui_medication_components_003) else null)
        else Modifier.toggleable(value = bulkSelected, enabled = selectionEnabled, role = Role.Checkbox,
            onValueChange = { toggleSelection() }).semantics {
                contentDescription = uiText(R.string.ui_medication_components_004, name, statusLabel(status, text = uiText)) + if (unpublished) uiText(R.string.ui_medication_components_005) else ""
            }
    val quickEditAction = if (bulkSelected == null && onQuickEdit != null) Modifier.semantics {
        customActions = listOf(CustomAccessibilityAction(uiText(R.string.ui_pharmacy_admin_screen_120)) {
            onQuickEdit(); true
        })
    } else Modifier
    AppCard(modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).then(interaction).then(quickEditAction).testTag("med_${medication.nameEn ?: medication.id}"),
        emphasis = when {
            highlighted -> AppCardEmphasis.HIGHLIGHTED
            bulkSelected == true -> AppCardEmphasis.SELECTED
            else -> AppCardEmphasis.DEFAULT
        }) {
        Column(Modifier.fillMaxWidth().drawWithCache {
            val stripes = Path().apply {
                val spacing = Space.lg.toPx()
                var x = -size.height
                while (x < size.width) {
                    moveTo(x, 0f); lineTo(x + size.height, size.height)
                    x += spacing
                }
            }
            val stripeStroke = Stroke(AppStroke.emphasis.toPx())
            val stroke = AppStroke.accent.toPx()
            val x = if (rtl) size.width - stroke / 2 else stroke / 2
            onDrawBehind {
                if (status == AvailabilityStatus.OUT) drawPath(stripes, unavailableStripe, style = stripeStroke)
                drawLine(edge, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        }.padding(if (compact) UiSpacing.related else UiSpacing.section), verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair), verticalAlignment = Alignment.Top) {
                // Fixed slot prevents selection-mode row/width changes for editable cards.
                if (reserveSelectionSpace || bulkSelected != null) Box(Modifier.size(Space.xl)) {
                    if (bulkSelected != null) Checkbox(checked = bulkSelected, onCheckedChange = null,
                        enabled = selectionEnabled, modifier = Modifier.size(Space.xl).testTag("select_${medication.id}").clearAndSetSemantics { })
                }
                form?.let { DosageFormIcon(it) }
                Column(Modifier.weight(1f)) {
            if (compact) {
                MedicationNameLines(medication.genericAr, medication.genericEn, preferences.names,
                    prominent = true, query = query, primaryStyle = MaterialTheme.typography.titleMedium, maxLines = 2,
                    strength = medication.strength, titleTrailing = pendingMarker)
                Text(form?.nameAr ?: uiText(R.string.ui_aqaqeer_app_026), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MedicationSummary(medication, preferences.names, prominent = true, query = query, titleTrailing = pendingMarker)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                    Text(form?.nameAr ?: uiText(R.string.ui_aqaqeer_app_026), modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)

                }
            }
                }
            }
            MedicationBadges(medication, today, sectionId?.let { status }, onQuickEdit, name,
                quickMenuOpen, dismissQuickMenu, chooseStatus, selectionEnabled)

            if (!compact && !(preferences.groupingEnabled && preferences.grouping == HomeGrouping.CATEGORY)) LabeledValue(uiText(R.string.ui_pharmacy_admin_screen_073), row.category)
            val elsewhere = row.elsewhere
            val tipIdentity = listOf(LocalNoticeScope.current, "availability-tip", medication.id, sectionId.orEmpty())
            val tipContent = elsewhere.flatMap { listOf(it.id, it.nameAr) }
            SideEffect { UiSession.reconcile(tipIdentity, if (elsewhere.isEmpty()) emptyMap() else mapOf(tipIdentity to tipContent)) }
            if (elsewhere.isNotEmpty() && !UiSession.isDismissed(tipIdentity, tipContent)) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Row(Modifier.fillMaxWidth().padding(UiSpacing.hair).testTag("tip_${medication.id}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
                    AppIcon(UiIcon.INFO)
                    Text(uiText(R.string.ui_medication_components_007) + elsewhere.joinToString(uiText(R.string.ui_medication_components_008)) { it.nameAr },
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { UiSession.dismiss(tipIdentity, tipContent) }) {
                        AppIcon(UiIcon.CLOSE, description = uiText(R.string.notice_dismiss))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MedicationBadges(medication: Medication, today: LocalDate, status: AvailabilityStatus?,
    onQuickEdit: (() -> Unit)?, name: String, expanded: Boolean, dismiss: () -> Unit,
    choose: (AvailabilityStatus) -> Unit, enabled: Boolean) {
    val expiry = remember(medication, today) { MedicationPresentation.expiry(medication, today) }
    if (status == null && expiry == null) return
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
        if (status != null) Box(Modifier.align(Alignment.CenterVertically)) {
            EditableStatusPill(status, onQuickEdit, expanded, dismiss, choose, enabled, dense = true)
        }
        if (expiry != null)
            Box(Modifier.align(Alignment.CenterVertically)) { ExpiryBadge(expiry, dense = true, modifier = Modifier.testTag("expiry_${medication.id}")) }
    }
}

@Composable internal fun ExpiryBadge(medication: Medication, today: LocalDate, dense: Boolean = false) {
    val expiry = remember(medication, today) { MedicationPresentation.expiry(medication, today) }
    expiry?.let { ExpiryBadge(it, dense, Modifier.testTag("expiry_${medication.id}")) }
}

@Composable private fun ExpiryBadge(expiry: ExpiryState, dense: Boolean, modifier: Modifier) {
    val uiText = rememberUiText()
    val presentation = expiryBadge(expiry)
    MedicationBadge(presentation.text, presentation.tone, dense = dense, icon = presentation.icon, modifier = modifier)
}
