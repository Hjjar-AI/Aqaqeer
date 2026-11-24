package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppBottomSheet(dismiss: () -> Unit, state: SheetState,
    scroll: Modifier = Modifier, maxHeight: Dp = viewportHeight(), content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).then(scroll).padding(UiSpacing.page),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.related), content = content)
    }
}
