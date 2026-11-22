package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun CreatePharmacyContent(state: AppUiState, ready: CatalogState.Ready, vm: CatalogViewModel,
    busy: Boolean, submitted: MutableState<Boolean>, revealInvalid: () -> Unit) {
    val uiText = rememberUiText()
    val catalog = ready.catalog
    var formSubmitted by submitted

    if (catalog.pharmacy != null || ready.isDemo) { Text(uiText(R.string.catalog_device_already_configured_message)); return }
    var name by rememberSaveable(state.route) { mutableStateOf("") }; var zone by rememberSaveable(state.route) { mutableStateOf(DEFAULT_PRESENTATION_TIMEZONE) }
    var section by rememberSaveable(state.route) { mutableStateOf(uiText(R.string.catalog_initial_section_default_name)) }
    var pin by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    var pinEntryVersion by remember { mutableStateOf(0) }
    val focus = LocalFocusManager.current
    val creating = busy || vm.adminBusy.value
    GroupedCard(title = uiText(R.string.catalog_pharmacy_setup_title)) {
        FormField(uiText(R.string.catalog_pharmacy_name_label), name, { name = it }, submitted = formSubmitted, required = true); TimezoneField(zone) { zone = it }
        FormField(uiText(R.string.catalog_initial_section_name_label), section, { section = it }, submitted = formSubmitted, required = true)
        if (creating) SupportingText(uiText(R.string.catalog_creation_progress))
        else key(pinEntryVersion) {
            SecretField(uiText(R.string.catalog_creation_admin_pin_label), pin, { pin = it }, submitted = formSubmitted, required = true, numericSecret = true, validate = FormGuidance::pin, rule = uiText(R.string.catalog_creation_admin_pin_rule), tag = uiText(R.string.catalog_admin_pin_label_with_guidance))
            SecretField(uiText(R.string.catalog_admin_pin_confirmation_label), confirm, { confirm = it }, submitted = formSubmitted, required = true, numericSecret = true, imeAction = ImeAction.Done, validate = {
                    if (it.map { c -> Character.digit(c, 10) } == pin.map { c -> Character.digit(c, 10) }) null
                    else uiText(R.string.catalog_admin_pin_mismatch_error)
                })
        }
    }
    Text(uiText(R.string.catalog_offline_creation_help))
    AppButton(enabled = !creating, onClick = {
        if (vm.adminBusy.value) return@AppButton
        formSubmitted = true
        if (name.isBlank() || section.isBlank() || FormGuidance.timezone(zone) != null ||
            FormGuidance.pin(pin) != null || FormGuidance.pin(confirm) != null ||
            pin.map { Character.digit(it, 10) } != confirm.map { Character.digit(it, 10) }) {
            revealInvalid(); return@AppButton
        }
        val a = pin.toCharArray(); val b = confirm.toCharArray()
        formSubmitted = false; pinEntryVersion++
        focus.clearFocus()
        pin = ""; confirm = ""
        vm.secretCommand(Route.Home, a, b) { vm.admin.create(name, zone, section, a, b) }
    }) { Text(uiText(R.string.catalog_create_pharmacy_action)) }
}
