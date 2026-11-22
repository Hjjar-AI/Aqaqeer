package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aqaqeer.app.R

/** Callers keep query, filtering, stable keys, selection and item presentation. */
@Composable internal fun SearchableChoiceDialog(title: String, query: String, changeQuery: (String) -> Unit,
    searchLabel: String, showSearch: Boolean, empty: Boolean, emptyMessage: String, dismiss: () -> Unit,
    searchLtr: Boolean = false, viewportFraction: Float = 0.65f, listFraction: Float? = null,
    items: LazyListScope.() -> Unit) {
    val text = rememberUiText()
    AppDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Dialog constraints include IME resizing; never subtract the keyboard again.
            Column(Modifier.heightIn(max = minOf(maxHeight, viewportHeight(viewportFraction))),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                if (showSearch) FormField(searchLabel, query, changeQuery, ltr = searchLtr)
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).then(
                    listFraction?.let { Modifier.heightIn(max = viewportHeight(it)) } ?: Modifier), content = items)
                if (empty) Text(emptyMessage)
            }
        }
    }, confirmButton = {
        AppButton(variant = AppButtonVariant.QUIET, onClick = dismiss) { Text(text(R.string.shared_close_action)) }
    })
}
