package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.aqaqeer.app.data.MedicationPresentation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable internal fun TimezoneField(value: String, change: (String) -> Unit) {
    val uiText = rememberUiText()
    var open by rememberSaveable { mutableStateOf(false) }
    fun city(zone: String): String = android.icu.text.TimeZoneNames.getInstance(java.util.Locale.forLanguageTag("ar"))
        .getExemplarLocationName(zone) ?: zone.substringAfterLast('/').replace('_', ' ')
    NavigationRow(uiText(R.string.ui_technical_fields_001), { open = true }, supporting = city(value) + " · " + bidi(value))
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val zones = remember(value) {
            val pinned = listOf(value, ZoneId.systemDefault().id, "Asia/Damascus", "UTC").distinct()
            pinned + (ZoneId.getAvailableZoneIds() - pinned.toSet()).sorted()
        }
        val offsets = remember(zones) { val now = Instant.now(); zones.associateWith { ZoneId.of(it).rules.getOffset(now).id } }
        val visible = remember(query, zones) { zones.filter { it.contains(query.trim(), ignoreCase = true) || city(it).contains(query.trim(), ignoreCase = true) } }
        AppDialog(onDismissRequest = { open = false }, title = { Text(uiText(R.string.ui_technical_fields_002)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                FormField(uiText(R.string.ui_technical_fields_003), query, { query = it }, ltr = true)
                LazyColumn(Modifier.heightIn(max = viewportHeight(0.45f))) {
                    items(visible, key = { it }) { zone ->
                        AppButton(variant = AppButtonVariant.QUIET, onClick = { change(zone); open = false }, modifier = Modifier.fillMaxWidth()) { Column { Text(city(zone)); SupportingText(bidi("${zone} · UTC${offsets.getValue(zone).let { if (it == "Z") "+00:00" else it }}")) } }
                    }
                }
                if (visible.isEmpty()) Text(uiText(R.string.ui_technical_fields_004))
            }
        }, confirmButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { open = false }) { Text(uiText(R.string.ui_choice_components_004)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GregorianDateField(label: String, value: String, change: (String) -> Unit,
    submitted: Boolean = false, required: Boolean = false, tag: String = label) {
    val uiText = rememberUiText()
    var open by rememberSaveable(label) { mutableStateOf(false) }
    FormField(label, value, { change(MedicationPresentation.westernDigits(it)) }, supportingText = uiText(R.string.ui_technical_fields_005), required = required, submitted = submitted, validate = FormGuidance::date, tag = tag, ltr = true, trailingContent = {
            IconButton(onClick = { open = true }) {
                AppIcon(UiIcon.CALENDAR, description = uiText(R.string.ui_technical_fields_006, label))
            }
        })
    if (open) {
        val initial = remember(value) { runCatching { LocalDate.parse(value.trim()).also { require(it.year in 1..9999) }.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            AppButton(variant = AppButtonVariant.QUIET, enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text(uiText(R.string.ui_technical_fields_007)) }
        }, dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { open = false }) { Text(uiText(R.string.ui_bulk_medication_editor_012)) } }) {
            DatePicker(picker, title = { Text(uiText(R.string.ui_technical_fields_008), modifier = Modifier.padding(UiSpacing.page)) })
        }
    }
}

internal fun groupIdInput(value: String): String = MedicationPresentation.westernDigits(value.trim()).replace('−', '-')
