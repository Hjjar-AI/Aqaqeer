package com.aqaqeer.app.ui

import android.os.Build
import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aqaqeer.app.data.MedicationPresentation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

@Composable internal fun TimezoneField(value: String, change: (String) -> Unit) {
    val uiText = rememberUiText()
    var open by rememberSaveable { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val legacyCities = remember(resources) {
        if (Build.VERSION.SDK_INT >= 24) emptyMap() else {
            resources.openRawResource(R.raw.timezone_cities_ar).bufferedReader().useLines { lines ->
                lines.associate { line -> line.substringBefore('\t') to line.substringAfter('\t') }
            }
        }
    }
    fun city(zone: String): String {
        val localized = if (Build.VERSION.SDK_INT >= 24) {
            android.icu.text.TimeZoneNames.getInstance(Locale.forLanguageTag("ar")).getExemplarLocationName(zone)
        } else legacyCities[zone]
        return localized ?: zone.substringAfterLast('/').replace('_', ' ')
    }
    NavigationRow(uiText(R.string.shared_timezone_label), { open = true }, supporting = city(value) + " · " + bidi(value))
    if (open) {
        var query by rememberSaveable { mutableStateOf("") }
        val zones = remember(value) {
            val pinned = listOf(value, ZoneId.systemDefault().id, DEFAULT_PRESENTATION_TIMEZONE, "UTC").distinct()
            pinned + (ZoneId.getAvailableZoneIds() - pinned.toSet()).sorted()
        }
        val offsets = remember(zones) { val now = Instant.now(); zones.associateWith { ZoneId.of(it).rules.getOffset(now).id } }
        val visible = remember(query, zones) { zones.filter { it.contains(query.trim(), ignoreCase = true) || city(it).contains(query.trim(), ignoreCase = true) } }
        SearchableChoiceDialog(uiText(R.string.shared_timezone_picker_title), query, { query = it },
            uiText(R.string.shared_timezone_search_placeholder), showSearch = true, empty = visible.isEmpty(),
            emptyMessage = uiText(R.string.shared_timezone_no_matches_help), dismiss = { open = false },
            searchLtr = true, viewportFraction = 0.85f, listFraction = 0.45f) {
            items(visible, key = { it }) { zone ->
                AppButton(variant = AppButtonVariant.QUIET, onClick = { change(zone); open = false }, modifier = Modifier.fillMaxWidth()) {
                    Column { Text(city(zone)); SupportingText(bidi("${zone} · UTC${offsets.getValue(zone).let { if (it == "Z") "+00:00" else it }}")) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GregorianDateField(label: String, value: String, change: (String) -> Unit,
    submitted: Boolean = false, required: Boolean = false, tag: String = label) {
    val uiText = rememberUiText()
    var open by rememberSaveable(label) { mutableStateOf(false) }
    FormField(label, value, { change(MedicationPresentation.westernDigits(it)) }, supportingText = uiText(R.string.shared_gregorian_date_format_help), required = required, submitted = submitted, validate = FormGuidance::date, tag = tag, ltr = true, trailingContent = {
            AppIconButton(onClick = { open = true }) {
                AppIcon(UiIcon.CALENDAR, description = uiText(R.string.shared_gregorian_picker_description, label))
            }
        })
    if (open) {
        // The legacy Material calendar has a Julian/Gregorian transition in 1582.
        val years = if (Build.VERSION.SDK_INT < 26) 1583..9999 else 1..9999
        val initial = remember(value, years) {
            runCatching { LocalDate.parse(value.trim()) }.getOrNull()?.takeIf { it.year in years }
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial, yearRange = years)
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            AppButton(variant = AppButtonVariant.QUIET, enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text(uiText(R.string.shared_date_picker_confirm_action)) }
        }, dismissButton = { AppButton(variant = AppButtonVariant.QUIET, onClick = { open = false }) { Text(uiText(R.string.availability_bulk_cancel_action)) } }) {
            DatePicker(picker, title = { Text(uiText(R.string.shared_gregorian_picker_title), modifier = Modifier.padding(UiSpacing.page)) })
        }
    }
}

internal fun groupIdInput(value: String): String = MedicationPresentation.westernDigits(value.trim()).replace('−', '-')
