package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aqaqeer.app.R
import com.aqaqeer.app.uiText

@Composable internal fun DemoModeControls(admin: Boolean, change: (Boolean) -> Unit, reset: () -> Unit) {
    val uiText = rememberUiText()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(uiText(R.string.review_demo_mode), initiallyExpanded = true) {
        RadioChoiceRows(listOf(false, true), admin, change,
            label = { uiText(if (it) R.string.review_demo_admin else R.string.review_demo_reader) })
        SupportingText(uiText(R.string.review_demo_edit_help))
        AppButton(variant = AppButtonVariant.DESTRUCTIVE_OUTLINED, onClick = { confirmReset = true }) {
            Text(uiText(R.string.review_demo_reset))
        }
    }
    if (confirmReset) AppDialog(onDismissRequest = { confirmReset = false },
        title = { Text(uiText(R.string.review_demo_reset)) },
        text = { Text(uiText(R.string.review_demo_reset_help)) },
        confirmButton = { DestructiveConfirmButton(true, { confirmReset = false; reset() }, uiText(R.string.review_demo_reset)) },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { confirmReset = false }) { Text(uiText(R.string.shared_close_action)) } })
}
