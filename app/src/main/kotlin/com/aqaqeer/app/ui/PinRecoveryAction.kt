package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun PinRecoveryAction(enabled: Boolean, recover: () -> Unit) {
    val uiText = rememberUiText()
    GroupedCard(title = uiText(R.string.pin_recovery_title)) {
        SupportingText(uiText(R.string.pin_recovery_help))
        AppButton(variant = AppButtonVariant.QUIET, enabled = enabled, onClick = recover) { Text(uiText(R.string.pin_recovery_title)) }
    }
}
