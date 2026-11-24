package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.data.MedicationPresentation

@Composable internal fun BulkSelectionBar(section: String, count: Int, valid: Boolean, busy: Boolean,
    cancel: () -> Unit, visibleCount: Int = 0,
    selectVisible: () -> Unit = {}, clear: () -> Unit = {}, content: @Composable () -> Unit = {}) {
    val uiText = rememberUiText()
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("bulk_selection_bar")) {
        ContentSection(modifier = Modifier.padding(UiSpacing.related)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val countText = uiText(R.string.availability_selection_count_format, MedicationPresentation.displayDigits(count.toString()), section)
                val actions: @Composable () -> Unit = {
                    AppButton(variant = AppButtonVariant.QUIET, enabled = !busy && visibleCount > 0, onClick = selectVisible) { Text(uiText(R.string.availability_select_visible_action)) }
                    AppIconButton(enabled = !busy, onClick = cancel) { AppIcon(UiIcon.CLOSE, description = uiText(R.string.availability_finish_selection_description)) }
                }
                if (maxWidth < LayoutTokens.bulkHeaderStackWidth * maxOf(1f, LocalLayoutMetrics.current.fontScale)) {
                    Column {
                        Text(countText, Modifier.fillMaxWidth())
                        ActionGroup { actions() }
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(countText, Modifier.weight(1f))
                    actions()
                }
            }
            if (!valid) Text(uiText(R.string.availability_deleted_selection_error), color = MaterialTheme.colorScheme.error)
            content()
        }
    }
}
