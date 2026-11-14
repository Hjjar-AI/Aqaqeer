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
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("bulk_selection_bar")) {
        ContentSection(modifier = Modifier.padding(UiSpacing.related)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(R.string.ui_bulk_selection_bar_001, MedicationPresentation.easternDigits(count.toString()), section), Modifier.weight(1f))
                TextButton(enabled = !busy && visibleCount > 0, onClick = selectVisible) { Text(uiText(R.string.ui_bulk_selection_bar_002)) }
                IconButton(enabled = !busy, onClick = cancel) { AppIcon(UiIcon.CLOSE, description = uiText(R.string.ui_bulk_selection_bar_003)) }
            }
            if (!valid) Text(uiText(R.string.ui_bulk_selection_bar_004), color = MaterialTheme.colorScheme.error)
            content()
        }
    }
}
