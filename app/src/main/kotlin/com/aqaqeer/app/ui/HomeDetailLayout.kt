package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** The list occupies one stable slot as selection changes in the adjacent detail pane. */
@Composable internal fun HomeDetailLayout(dualPane: Boolean, showDetail: Boolean,
    list: @Composable () -> Unit, detail: @Composable () -> Unit,
    detailHeader: (@Composable () -> Unit)? = null) {
    if (dualPane) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Box(Modifier.weight(1f).fillMaxHeight()) { list() }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = UiSpacing.formWidth).fillMaxSize()) {
                detailHeader?.let { header ->
                    header()
                    AppDivider()
                }
                Box(Modifier.weight(1f)) { detail() }
            }
        }
    } else if (showDetail) detail() else list()
}
