package com.aqaqeer.app.ui

import com.aqaqeer.app.R
import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp

internal object UiSpacing {
    val hair = Space.xs
    val badgeHorizontal = Size.badgePadding
    val page = Space.lg
    val section = Space.md
    val related = Space.sm
    val group = Space.xl
    val pendingMarker = Space.sm
    val filterButton = Size.touchMin
    val fabClearance = Size.illustrationLarge
    val tabMeasurementAllowance = Size.touchMin
    val touch = Size.touchMin
    val listDetailBreakpoint = LayoutTokens.expandedWidth
    val contentWidth = LayoutTokens.contentMaxWidth
    val formWidth = LayoutTokens.formMaxWidth
}

internal class FormFocusTarget(val invalid: () -> Boolean, val focus: () -> Unit, val reveal: suspend () -> Unit)
internal class FormFocusController {
    val targets = mutableListOf<FormFocusTarget>()
    suspend fun focusFirstInvalid() {
        targets.firstOrNull { it.invalid() }?.let { it.reveal(); it.focus() }
    }
}
internal val LocalFormFocus = staticCompositionLocalOf<FormFocusController?> { null }

/** Constrain the viewport rather than each item, keeping scrollbars and gestures together. */
@Composable internal fun ScreenFrame(form: Boolean, wide: Boolean = false, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = if (form) UiSpacing.formWidth else if (wide) Dp.Infinity else UiSpacing.contentWidth)
            .fillMaxSize(), content = content)
    }
}

/** Screens retain their own fields and operation state; this owns only scrolling and spacing. */
@Composable internal fun FormPage(modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null, focusController: FormFocusController? = null, showFieldConvention: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val uiText = rememberUiText()
    val focus = focusController ?: remember { FormFocusController() }
    CompositionLocalProvider(LocalFormFocus provides focus) {
        Column(modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(UiSpacing.page),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                if (showFieldConvention) SupportingText(uiText(R.string.review_required_convention))
                content()
            }
            footer?.let { Surface(shadowElevation = UiSpacing.hair) { Box(Modifier.fillMaxWidth().padding(UiSpacing.related)) { it() } } }
        }
    }
}

@Composable internal fun ContentSection(modifier: Modifier = Modifier, title: String? = null,
    spacing: Dp = UiSpacing.related, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        title?.let { SectionHeading(it) }
        content()
    }
}

@Composable internal fun DialogBody(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.heightIn(max = viewportHeight(0.65f)).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.related), content = content)
}

@Composable internal fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.fillMaxWidth().semantics { heading() },
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable internal fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    WrappingContent(modifier) {
        Text("${label}:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.text.selection.SelectionContainer {
            ReadableText(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable internal fun ReadableText(text: String, modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current, fontFamily: FontFamily? = null, fontWeight: FontWeight? = null) {
    Text(text, modifier, style = style.copy(lineBreak = LineBreak.Paragraph), softWrap = true,
        fontFamily = fontFamily, fontWeight = fontWeight)
}

/** Actions wrap as units, retaining their own enabled state, semantics and callbacks. */
@Composable internal fun ActionGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    WrappingContent(modifier, content)
}

/** Noninteractive values can share a line without squeezing their text on short/narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WrappingContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.related),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) { content() }
}

/** Uses the actual window and font-aware scroll containers rather than a fixed pixel-height cap. */
@Composable internal fun viewportHeight(fraction: Float = 0.85f): Dp =
    LocalLayoutMetrics.current.height * fraction
