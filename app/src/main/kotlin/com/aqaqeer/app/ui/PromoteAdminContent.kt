package com.aqaqeer.app.ui

import com.aqaqeer.app.R

import androidx.compose.material3.Text
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*

@Composable internal fun PromoteAdminContent(vm: CatalogViewModel,
    busy: Boolean, submitted: MutableState<Boolean>, revealInvalid: () -> Unit) {
    val uiText = rememberUiText()
    var formSubmitted by submitted

    var pin by remember { mutableStateOf("") }
    GroupedCard(title = uiText(R.string.catalog_authorize_admin_title)) {
        SupportingText(uiText(R.string.catalog_admin_authorization_help))
        if (com.aqaqeer.app.BuildConfig.IS_DEMO) NoticeStrip(Notice(uiText(R.string.demo_pin_help)))
        SecretField(uiText(R.string.catalog_admin_pin_label), pin, { pin = it }, submitted = formSubmitted,
            required = true, numericSecret = true, imeAction = ImeAction.Done, validate = FormGuidance::pin)
        AppButton(enabled = !busy, onClick = {
            formSubmitted = true
            if (FormGuidance.pin(pin) != null) {
                revealInvalid()
                return@AppButton
            }
            val secret = pin.toCharArray()
            pin = ""
            vm.secretCommand(Route.Home, secret) { vm.admin.promote(secret) }
        }) { Text(uiText(R.string.navigation_promote_admin_title)) }
    }
}
