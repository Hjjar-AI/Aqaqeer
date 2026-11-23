package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun PromoteAdminContent(vm: CatalogViewModel,
    busy: Boolean, submitted: MutableState<Boolean>, revealInvalid: () -> Unit) {
    val uiText = rememberUiText()
    var formSubmitted by submitted

    if (com.aqaqeer.app.BuildConfig.IS_DEMO) SupportingText(uiText(R.string.demo_pin_help))
    var pin by remember { mutableStateOf("") }
    GroupedCard(title = uiText(R.string.catalog_authorize_admin_title)) {
        SecretField(uiText(R.string.catalog_admin_pin_label), pin, { pin = it }, submitted = formSubmitted, required = true, numericSecret = true, imeAction = ImeAction.Done, validate = FormGuidance::pin)
    }
    Text(uiText(R.string.catalog_admin_authorization_help))
    AppButton(enabled = !busy, onClick = {
        formSubmitted = true
        if (FormGuidance.pin(pin) != null) { revealInvalid(); return@AppButton }
        val p = pin.toCharArray(); pin = ""; vm.secretCommand(Route.Home, p) { vm.admin.promote(p) }
    }) { Text(uiText(R.string.navigation_promote_admin_title)) }
}
