package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aqaqeer.app.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeBulkSheet(selection: AvailabilitySelection?, catalog: CatalogSnapshot,
    availabilityBusy: Boolean, availabilityMessage: String?, availabilityInProgress: () -> Boolean,
    filtersOpen: () -> Boolean, canOpenHomeModal: () -> Boolean,
    saveBulk: ((AvailabilitySelection, BulkMedicationChanges, () -> Unit) -> Unit)?,
    isCurrent: (AvailabilitySelection) -> Boolean, dismissSheet: () -> Unit, exitBulk: () -> Unit, complete: () -> Unit) {

    val savingNow by rememberUpdatedState(availabilityInProgress)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !savingNow() })
    AppBottomSheet(dismiss = { if (!availabilityInProgress()) dismissSheet() }, state = sheet,
        scroll = Modifier.verticalScroll(rememberScrollState())) {
            selection?.let { captured ->
                BulkMedicationEditor(captured, catalog, availabilityBusy, availabilityMessage, dismiss = exitBulk,
                    save = { changes ->
                        if (isCurrent(captured) && !availabilityInProgress() && !filtersOpen() && canOpenHomeModal())
                            saveBulk?.invoke(captured, changes) {
                                if (isCurrent(captured)) complete()
                            }
                    },
                    current = { changes -> captured.medications.all { before ->
                        catalog.medications.find { it.id == before.id }?.let { live ->
                            (changes.status == null || catalog.status(before.id, captured.section.id) == captured.expected[before.id]) &&
                                (changes.formId == null || live.formId == before.formId) &&
                                (changes.categoryId == null || live.categoryId == before.categoryId)
                        } == true
                    } }, isSaving = availabilityInProgress)
            }

    }
}
