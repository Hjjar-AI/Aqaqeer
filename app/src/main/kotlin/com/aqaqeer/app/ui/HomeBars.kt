package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText

internal data class HomeBulkPresentation(val section: String, val selectedCount: Int, val valid: Boolean,
    val busy: Boolean, val visibleCount: Int)

@Composable internal fun HomeBulkBar(state: HomeBulkPresentation, cancel: () -> Unit, selectVisible: () -> Unit,
    clear: () -> Unit, openEditor: () -> Unit) {
    val uiText = rememberUiText()
    Box(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.page, vertical = UiSpacing.related)) {
        BulkSelectionBar(state.section, state.selectedCount, state.valid, state.busy, cancel = cancel,
            visibleCount = state.visibleCount, selectVisible = selectVisible, clear = clear) {
            ActionGroup {
                AppButton(enabled = state.selectedCount > 0 && state.valid && !state.busy, onClick = openEditor) {
                    Text(uiText(R.string.home_edit_selection_action))
                }
                AppButton(variant = AppButtonVariant.QUIET, enabled = !state.busy, onClick = clear) {
                    Text(uiText(R.string.home_clear_selection_action))
                }
            }
        }
    }
}

@Composable internal fun HomeBottomBars(searchAtBottom: Boolean, sectionsAtBottom: Boolean, reportHeight: (Int) -> Unit,
    search: @Composable () -> Unit, sections: @Composable () -> Unit) {
    Surface(modifier = Modifier.onSizeChanged { reportHeight(it.height) }, color = MaterialTheme.colorScheme.background) {
        ContentSection(modifier = Modifier.padding(horizontal = UiSpacing.page).padding(bottom = UiSpacing.related)) {
            if (searchAtBottom) search()
            if (sectionsAtBottom) sections()
        }
    }
}
