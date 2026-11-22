package com.aqaqeer.app.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.aqaqeer.app.data.*

internal data class HomeSelectionInputs(val catalog: CatalogSnapshot, val section: Section?,
    val canEdit: Boolean, val isSaving: () -> Boolean) {
    val contextKey: String get() = "${requireNotNull(catalog.pharmacy).pharmacyId}/${section?.id}/${canEdit}"
}

/** Saveable payloads stay unchanged; live inputs never replace captured edit expectations. */
internal class HomeSelectionState(
    val bulkModeState: MutableState<Boolean>, val selectedIdsState: MutableState<Set<String>>,
    val editorState: MutableState<AvailabilitySelection?>, val bulkSheetState: MutableState<Boolean>,
    private val capturedContext: MutableState<String>, private val inputs: State<HomeSelectionInputs>) {
    var bulkMode by bulkModeState
    var selectedIds by selectedIdsState
    var editor by editorState
    var bulkSheetOpen by bulkSheetState
    val contextValid: Boolean get() = capturedContext.value == inputs.value.contextKey

    fun clear() { editor = null; selectedIds = emptySet(); bulkMode = false; bulkSheetOpen = false }
    fun exitBulk() { if (!inputs.value.isSaving()) clear() }
    fun reconcileContext() {
        if (!contextValid) { clear(); capturedContext.value = inputs.value.contextKey }
    }
    fun resolveRestored() {
        if (editor?.restored == true) editor = editor?.resolve(inputs.value.catalog)
    }
    fun complete(id: String?) { if (id != null && editor?.id == id) clear() }

    fun capture(ids: Set<String>): AvailabilitySelection? {
        val current = inputs.value
        val catalog = current.catalog
        return current.section?.let { section ->
            AvailabilitySelection(requireNotNull(catalog.pharmacy).pharmacyId, section, catalog.medications.filter { it.id in ids },
                ids.associateWith { catalog.status(it, section.id) }, bulkMode,
                formNames = catalog.forms.associate { it.id to it.nameAr },
                categoryNames = catalog.categories.associate { it.id to it.nameAr })
        }
    }
    fun changeBulkSelection(ids: Set<String>) {
        if (inputs.value.isSaving()) return
        val fresh = capture(ids) ?: return
        val old = editor?.takeIf { it.bulk && contextValid }?.resolve(inputs.value.catalog)
        selectedIds = ids
        editor = fresh.copy(bulk = true, id = old?.id ?: fresh.id,
            medications = ids.mapNotNull { id -> old?.medications?.find { it.id == id }
                ?: fresh.medications.find { it.id == id } },
            expected = fresh.expected.mapValues { (id, value) -> old?.expected?.get(id) ?: value },
            formNames = fresh.formNames + old?.formNames.orEmpty(),
            categoryNames = fresh.categoryNames + old?.categoryNames.orEmpty(),
            originalFields = fresh.originalFields + old?.originalFields.orEmpty().filterKeys { it in ids })
    }
}

@Composable internal fun rememberHomeSelectionState(catalog: CatalogSnapshot, section: Section?, canEdit: Boolean,
    isSaving: () -> Boolean, completionId: String?): HomeSelectionState {
    val pharmacy = requireNotNull(catalog.pharmacy).pharmacyId
    val bulk = rememberSaveable(pharmacy, section?.id, canEdit) { mutableStateOf(false) }
    val ids = rememberSaveable(pharmacy, section?.id, canEdit,
        stateSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })) { mutableStateOf(emptySet<String>()) }
    val editor = rememberSaveable(pharmacy, section?.id, canEdit, stateSaver = AvailabilitySelectionSaver) { mutableStateOf<AvailabilitySelection?>(null) }
    val latest = rememberUpdatedState(HomeSelectionInputs(catalog, section, canEdit, isSaving))
    val capturedContext = rememberSaveable { mutableStateOf(latest.value.contextKey) }
    val sheet = rememberSaveable(latest.value.contextKey) { mutableStateOf(false) }
    val holder = remember(bulk, ids, editor, sheet, capturedContext) { HomeSelectionState(bulk, ids, editor, sheet, capturedContext, latest) }
    SideEffect { holder.resolveRestored() }
    LaunchedEffect(latest.value.contextKey) { holder.reconcileContext() }
    LaunchedEffect(completionId) { holder.complete(completionId) }
    LaunchedEffect(holder.bulkMode, holder.contextValid) {
        if (holder.bulkMode && holder.contextValid && holder.editor == null) holder.changeBulkSelection(holder.selectedIds)
    }
    return holder
}
