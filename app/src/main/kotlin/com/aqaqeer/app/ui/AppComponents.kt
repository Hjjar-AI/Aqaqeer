package com.aqaqeer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

internal enum class AppButtonVariant { PRIMARY, SECONDARY, QUIET, DESTRUCTIVE, DESTRUCTIVE_OUTLINED, DESTRUCTIVE_TEXT }

/** Workflow state and authorization remain with the caller. */
@Composable internal fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors? = null, shape: Shape = ButtonDefaults.shape, border: BorderStroke? = null,
    contentPadding: PaddingValues? = null, variant: AppButtonVariant = AppButtonVariant.PRIMARY,
    content: @Composable RowScope.() -> Unit) {
    when (variant) {
        AppButtonVariant.PRIMARY, AppButtonVariant.DESTRUCTIVE -> Button(onClick = onClick,
            modifier = modifier, enabled = enabled, shape = shape, border = border,
            colors = colors ?: if (variant == AppButtonVariant.DESTRUCTIVE) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                else ButtonDefaults.buttonColors(),
            contentPadding = contentPadding ?: ButtonDefaults.ContentPadding, content = content)
        AppButtonVariant.SECONDARY, AppButtonVariant.DESTRUCTIVE_OUTLINED -> OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = shape, border = border ?: ButtonDefaults.outlinedButtonBorder(enabled),
            colors = colors ?: if (variant == AppButtonVariant.DESTRUCTIVE_OUTLINED)
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.outlinedButtonColors(),
            contentPadding = contentPadding ?: ButtonDefaults.ContentPadding, content = content)
        AppButtonVariant.QUIET, AppButtonVariant.DESTRUCTIVE_TEXT -> TextButton(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, border = border,
            colors = colors ?: if (variant == AppButtonVariant.DESTRUCTIVE_TEXT)
                ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.textButtonColors(),
            contentPadding = contentPadding ?: ButtonDefaults.TextButtonContentPadding, content = content)
    }
}

@Composable internal fun AppIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable internal fun AppSpinner() {
    CircularProgressIndicator()
}

@Composable internal fun AppPopupMenu(expanded: Boolean, onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, content = content)
}

@Composable internal fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        key(data) {
            SwipeToDismissBox(
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { if (state.currentSnackbarData === data) data.dismiss() },
            ) {
                val colors = MaterialTheme.colorScheme
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                    actionColor = colors.primary,
                    actionContentColor = colors.primary,
                    dismissActionContentColor = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable internal fun AppChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape) {
    FilterChip(selected = selected, onClick = onClick, label = label, modifier = modifier,
        enabled = enabled, leadingIcon = leadingIcon, shape = shape)
}

/** Bounded bodies opt in through body; pickers keep their own lazy viewport in text. */
@Composable internal fun AppDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, title = title,
        text = if (body != null) ({ DialogBody(content = body) }) else text)
}

internal enum class AppCardEmphasis { DEFAULT, SELECTED, HIGHLIGHTED }
@Composable internal fun AppCard(modifier: Modifier = Modifier, emphasis: AppCardEmphasis = AppCardEmphasis.DEFAULT,
    shape: Shape = MaterialTheme.shapes.medium, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = shape,
        border = BorderStroke(AppStroke.hairline, if (emphasis == AppCardEmphasis.HIGHLIGHTED)
            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (emphasis == AppCardEmphasis.DEFAULT)
            MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer), content = content)
}

@Composable internal fun StickyGroupHeader(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, content = content)
}
