package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.*

@Composable internal fun MedicationEditor(c: CatalogSnapshot, old: Medication?, vm: CatalogViewModel, busy: Boolean) {
    val uiText = rememberUiText()
    val id = rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { old?.id ?: UUID.randomUUID().toString() }
    var en by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.nameEn ?: "") }; var ar by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.nameAr ?: "") }
    var strength by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.strength ?: "") }; var genericAr by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.genericAr ?: vm.newMedicationName.takeUnless { it.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }.orEmpty()) }
    var genericEn by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.genericEn ?: vm.newMedicationName.takeIf { it.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }.orEmpty()) }; var brand by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.brand ?: "") }
    var note by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.note ?: "") }; var expiry by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.expiryDate?.toString() ?: "") }
    var threshold by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf((old?.expirySoonThresholdDays ?: 60).toString()) }
    var category by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.categoryId ?: CatalogSeeds.UNCATEGORIZED_ID) }
    var form by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(old?.formId ?: CatalogSeeds.defaultMedicationFormId(c.forms)) }
    var initialAvailability by rememberSaveable(id, stateSaver = InitialAvailabilitySaver) {
        mutableStateOf(emptyMap<String, AvailabilityStatus>())
    }
    var confirmDelete by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(false) }
    var submitted by rememberSaveable(c.pharmacy?.pharmacyId, old?.id) { mutableStateOf(false) }
    val formFocus = remember { FormFocusController() }
    val scope = rememberCoroutineScope()
    var optionalOpen by rememberSaveable(id) { mutableStateOf(false) }
    val scientificError = if (genericAr.isBlank() && genericEn.isBlank()) uiText(R.string.catalog_scientific_name_required_error) else null
    val comparison = Medication(id, en.trim().takeIf(String::isNotEmpty), strength, form, category,
        Instant.EPOCH, Instant.EPOCH, nameAr = ar.trim().takeIf(String::isNotEmpty),
        genericAr = genericAr.trim().takeIf(String::isNotEmpty), genericEn = genericEn.trim().takeIf(String::isNotEmpty))
    val duplicates = MedicationDuplicates.find(comparison, remember(c.medications) { MedicationDuplicates.index(c.medications) }).toSet()
    var duplicateConfirmed by rememberSaveable(en, ar, genericAr, genericEn, strength, form, duplicates) { mutableStateOf(false) }
    fun save() {
        submitted = true
        if (scientificError != null || strength.isBlank() || FormGuidance.date(expiry) != null || FormGuidance.threshold(threshold) != null) {
            if (FormGuidance.threshold(threshold) != null) optionalOpen = true
            scope.launch { formFocus.focusFirstInvalid() }; return
        }
        if (duplicates.isNotEmpty() && !duplicateConfirmed) {
            vm.adminMessage.value = uiText(R.string.logic_duplicate_warning)
            return
        }
        fun optional(s: String) = s.trim().takeIf { it.isNotEmpty() }
        val initialChoices = if (old == null) initialAvailability.toMap() else null
        vm.command {
            vm.admin.saveMedication(Medication(id = id, nameEn = optional(en), strength = strength.trim(), formId = form,
                categoryId = category, createdAt = Instant.now(), updatedAt = Instant.now(),
                nameAr = optional(ar), genericAr = optional(genericAr), genericEn = optional(genericEn), brand = optional(brand),
                note = optional(note), expiryDate = optional(expiry)?.let(LocalDate::parse), expirySoonThresholdDays = threshold.toInt()), expectedPharmacy = c.pharmacy?.pharmacyId, requireExisting = old != null, confirmedDuplicates = if (duplicateConfirmed) duplicates else emptySet(),
                initialAvailability = initialChoices)
            vm.newMedicationName = ""
            vm.medicationSaved(id, old == null)
        }
    }
    FormPage(focusController = formFocus, showFieldConvention = true, footer = {
        AppButton(enabled = !busy, onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text(uiText(R.string.catalog_save_local_action)) }
    }) {
    val message by vm.adminMessage.collectAsStateWithLifecycle()
    InlineOperationStatus(busy, message)
    if (old != null) MedicationAvailabilityControls(c, old, busy, isSaving = { vm.adminBusy.value }) { selection, status ->
        vm.saveAvailability(selection, BulkMedicationChanges(status = status)) { }
    }
    if (duplicates.isNotEmpty()) CheckChoiceRow(duplicateConfirmed,
        uiText(R.string.logic_duplicate_confirm), enabled = !busy) { duplicateConfirmed = it }
    val expiryDay = runCatching { LocalDate.parse(expiry) }.getOrNull()
    GroupedCard(title = uiText(R.string.catalog_medication_identity_heading)) {
        FormField(uiText(R.string.catalog_scientific_name_arabic_label), genericAr, { genericAr = it }, submitted = submitted, supportingText = uiText(R.string.catalog_scientific_name_required_help), validate = { scientificError })
        FormField(uiText(R.string.catalog_scientific_name_english_label), genericEn, { genericEn = it }, ltr = true, submitted = submitted, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words), supportingText = uiText(R.string.catalog_scientific_name_required_help), validate = { scientificError })
        FormField(uiText(R.string.catalog_medication_strength_label), strength, { strength = it }, submitted = submitted, required = true, rule = uiText(R.string.catalog_medication_strength_rule), tag = uiText(R.string.catalog_strength_label_with_guidance))
        AdaptivePickers(first = {
            ChoicePicker(uiText(R.string.catalog_dosage_form_picker_label), c.forms, c.forms.find { it.id == form }, { it.nameAr }, itemKey = { it.id }) { form = it.id }
        }, second = {
            ChoicePicker(uiText(R.string.catalog_category_label), c.categories, c.categories.find { it.id == category }, { it.nameAr }, itemKey = { it.id }) { category = it.id }
        })
        GregorianDateField(uiText(R.string.catalog_expiry_date_label), expiry, { expiry = it }, submitted = submitted)
        if (expiryDay != null && expiryDay < MedicationPresentation.today(Instant.now(), c.pharmacy?.timezone ?: DEFAULT_PRESENTATION_TIMEZONE)) {
            SupportingText(uiText(R.string.logic_past_expiry))
        }
    }
    if (old == null) NewMedicationAvailabilityControls(c, initialAvailability, busy,
        reset = { initialAvailability = emptyMap() }) { section, status ->
        initialAvailability = if (status == AvailabilityStatus.AVAILABLE) initialAvailability - section
            else initialAvailability + (section to status)
    }
    CollapsibleGroup(uiText(R.string.catalog_medication_optional_information_heading), optionalOpen, { optionalOpen = !optionalOpen }) {
        FormField(uiText(R.string.catalog_expiry_warning_days_label), threshold,
            { threshold = MedicationPresentation.westernDigits(it) }, submitted = submitted, required = true,
            validate = FormGuidance::threshold, ltr = true, imeAction = ImeAction.Done, onDone = { if (!busy) save() })
        GroupedCard(title = uiText(R.string.catalog_trade_name_label_with_guidance)) {
            FormField(uiText(R.string.catalog_medication_trade_name_arabic_label), ar, { ar = it }, submitted = submitted, required = false)
            FormField(uiText(R.string.catalog_medication_trade_name_english_label), en, { en = it }, ltr = true, submitted = submitted, required = false)
        }
        GroupedCard(title = uiText(R.string.catalog_additional_information_heading)) {
            FormField(uiText(R.string.catalog_brand_label), brand, { brand = it }, submitted = submitted, required = false)
            MultilineField(uiText(R.string.catalog_note_label), note, { note = it }, submitted = submitted, required = false)
        }
    }
    if (c.medications.any { other -> other.id != id &&
        listOfNotNull(other.genericAr, other.genericEn).map(::normalizedName).any { candidate ->
            listOf(genericAr, genericEn).filter(String::isNotBlank).map(::normalizedName).any { it == candidate }
        } }) Text(uiText(R.string.catalog_duplicate_medication_warning))
    if (old != null) {
        GroupedCard(title = uiText(R.string.catalog_delete_medication_action)) {
            CheckChoiceRow(confirmDelete, uiText(R.string.catalog_confirm_medication_delete_label)) { confirmDelete = it }
            DestructiveConfirmButton(enabled = !busy && confirmDelete, onClick = { vm.command(Route.Home) { vm.admin.delete("medication", id) } }, label = uiText(R.string.catalog_delete_medication_action))
        }
    }
    }
}
