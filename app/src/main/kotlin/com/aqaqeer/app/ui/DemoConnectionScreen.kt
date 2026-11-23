package com.aqaqeer.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.R
import com.aqaqeer.app.admin.DemoSession
import com.aqaqeer.app.data.CatalogState

/** Exercises connection controls against a process-local simulated group only. */
@Composable internal fun DemoConnectionScreen(ready: CatalogState.Ready, vm: CatalogViewModel) {
    val text = rememberUiText()
    val session by DemoSession.state.collectAsStateWithLifecycle()
    val current = session ?: return
    val busy by vm.adminBusy.collectAsStateWithLifecycle()
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var confirmRotation by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected = it }
    FormPage(showFieldConvention = true) {
        OperationStatus(busy, message, cancel = vm::cancelAdminOperation)
        InformationCard(text(R.string.navigation_demo_build_label), text(R.string.demo_connection_help))
        GroupedCard(title = text(R.string.connection_telegram_details_title)) {
            LabeledValue(text(R.string.demo_group_label), text(R.string.demo_group_name))
            LabeledValue(text(R.string.demo_connection_status), text(if (current.connected) R.string.demo_connected else R.string.demo_disconnected))
            LabeledValue(text(R.string.demo_connection_version), current.keyVersion.toString())
            SwitchChoiceRow(current.online, text(R.string.demo_online), text(R.string.demo_online_help), change = {
                vm.command(queueOffline = false) { DemoSession.setOnline(it) }
            })
            AppButton(enabled = !busy, onClick = { vm.command(queueOffline = false) { DemoSession.refresh() } }) {
                Text(text(R.string.review_refresh_now))
            }
            if (ready.admin) {
                AppButton(enabled = !busy, onClick = { vm.navigate(Route.Setup) }) { Text(text(R.string.navigation_telegram_setup_title)) }
                AppButton(enabled = !busy, onClick = { vm.command(queueOffline = false) { DemoSession.setConnection(!current.connected) } }) {
                    Text(text(if (current.connected) R.string.demo_disconnect else R.string.demo_connect))
                }
                AppButton(enabled = !busy && current.connected, onClick = { vm.navigate(Route.ExportConnection) }) { Text(text(R.string.navigation_connection_export_title)) }
            }
        }
        GroupedCard(title = text(R.string.demo_replace_connection)) {
            AppButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text(text(R.string.review_select_file)) }
            if (selected != null) {
                SecretField(text(R.string.connection_file_password_label), password, { password = it }, required = true)
                AppButton(enabled = !busy && password.isNotEmpty(), onClick = {
                    val uri = selected ?: return@AppButton
                    val pass = password.toCharArray(); password = ""
                    vm.secretCommand(null, pass, queueOffline = false) { vm.demoFiles.importConnection(uri, pass); selected = null }
                }) { Text(text(R.string.demo_replace_connection)) }
            }
        }
        if (ready.admin) {
            GroupedCard(title = text(R.string.maintenance_connection_pin_action)) {
                SupportingText(text(R.string.demo_pin_help))
                SecretField(text(R.string.demo_current_pin), oldPin, { oldPin = it }, required = true, numericSecret = true)
                SecretField(text(R.string.demo_new_pin), newPin, { newPin = it }, required = true, numericSecret = true)
                SecretField(text(R.string.demo_confirm_pin), confirmation, { confirmation = it }, required = true, numericSecret = true)
                AppButton(enabled = !busy && oldPin.isNotEmpty() && newPin.length >= 4 && newPin == confirmation, onClick = {
                    val old = oldPin.toCharArray(); val next = newPin.toCharArray(); val confirm = confirmation.toCharArray()
                    oldPin = ""; newPin = ""; confirmation = ""
                    vm.secretCommand(null, old, next, confirm, queueOffline = false) { DemoSession.changePin(old, next, confirm) }
                }) { Text(text(R.string.demo_change_pin)) }
            }
            AppButton(enabled = !busy, onClick = { confirmRotation = true }) { Text(text(R.string.demo_rotate_connection)) }
        }
    }
    if (confirmRotation) AppDialog(onDismissRequest = { confirmRotation = false },
        title = { Text(text(R.string.demo_rotate_connection)) },
        body = { Text(text(R.string.demo_rotation_help)) },
        confirmButton = { AppButton(enabled = !busy, onClick = {
            confirmRotation = false
            vm.command(queueOffline = false) { DemoSession.rotateConnection() }
        }) { Text(text(R.string.demo_rotate_connection)) } },
        dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { confirmRotation = false }) { Text(text(R.string.shared_close_action)) } })
}
