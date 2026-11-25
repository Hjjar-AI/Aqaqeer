package com.aqaqeer.app.ui

import com.aqaqeer.app.R

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.aqaqeer.app.data.CatalogState

@Composable internal fun LeavePharmacyContent(ready: CatalogState.Ready, vm: CatalogViewModel,
    busy: Boolean, submitted: MutableState<Boolean>, revealInvalid: () -> Unit, leaveNameState: MutableState<String>, confirmedLeaveState: MutableState<Boolean>) {
    val uiText = rememberUiText()
    val catalog = ready.catalog
    var formSubmitted by submitted
    var leaveName by leaveNameState
    var confirmedLeave by confirmedLeaveState

    GroupedCard(title = uiText(R.string.catalog_leave_confirmation_title)) {
        NoticeStrip(Notice(uiText(if (ready.isDemo) R.string.demo_leave_help else R.string.catalog_leave_consequences_help),
            NoticeSeverity.WARNING))
        if (ready.uncertain) NoticeStrip(Notice(uiText(R.string.catalog_leave_uncertain_publication_warning), NoticeSeverity.WARNING))
        if (ready.admin) FormField(uiText(R.string.catalog_leave_pharmacy_name_label), leaveName, { leaveName = it }, submitted = formSubmitted, required = true, validate = { if (it == catalog.pharmacy?.name) null else uiText(R.string.catalog_leave_pharmacy_name_mismatch_error) })
        CheckChoiceRow(confirmedLeave, uiText(R.string.catalog_leave_acknowledgement_label)) { confirmedLeave = it }
        AppButton(variant = AppButtonVariant.DESTRUCTIVE, enabled = confirmedLeave && !busy, onClick = {
            formSubmitted = true
            if (ready.admin && leaveName != catalog.pharmacy?.name) { revealInvalid(); return@AppButton }
            vm.command(Route.Home) { vm.admin.leave(leaveName, true) }
        }) { Text(uiText(R.string.catalog_leave_delete_local_action)) }
    }
}
