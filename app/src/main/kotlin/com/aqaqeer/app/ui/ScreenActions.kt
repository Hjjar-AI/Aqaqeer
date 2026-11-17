package com.aqaqeer.app.ui

import androidx.compose.runtime.*
import com.aqaqeer.app.data.*

/** Remembered action bundles read current callbacks; they never freeze a prior screen/context. */
internal data class HomeCallbacks(
    val selectSection: (String) -> Unit,
    val setQuery: (String) -> Unit,
    val toggleStatus: (AvailabilityStatus) -> Unit,
    val toggleCategory: (String) -> Unit,
    val clearFilters: () -> Unit,
    val openMedication: (String) -> Unit,
    val openSettings: () -> Unit,
    val openConnection: (() -> Unit)? = null,
    val refresh: (() -> Unit)? = null,
    val openCreate: (() -> Unit)? = null,
    val openRestore: (() -> Unit)? = null,
    val saveAvailability: ((AvailabilitySelection, AvailabilityStatus, () -> Unit) -> Unit)? = null,
    val openAddMedication: (() -> Unit)? = null,
    val onModalVisibilityChanged: (Boolean) -> Unit = {},
    val availabilityInProgress: () -> Boolean = { false },
    val canOpenHomeModal: () -> Boolean = { true },
    val setGroupingEnabled: (Boolean) -> Unit = {},
    val setGrouping: (HomeGrouping) -> Unit = {},
    val saveBulk: ((AvailabilitySelection, BulkMedicationChanges, () -> Unit) -> Unit)? = null,
    val prefillMedication: (String) -> Unit = {},
    val onBulkModeChanged: (Boolean) -> Unit = {},
    val toggleExtraFilter: (String) -> Unit = {},
    val onBottomBarsHeightChanged: (Int) -> Unit = {},
    val toggleAvailableFilter: () -> Unit = {},
    val editMedication: ((String) -> Unit)? = null
)

@Stable
internal class HomeActions(private val current: State<HomeCallbacks>) {
    val selectSection: (String) -> Unit get() = current.value.selectSection
    val setQuery: (String) -> Unit get() = current.value.setQuery
    val toggleStatus: (AvailabilityStatus) -> Unit get() = current.value.toggleStatus
    val toggleCategory: (String) -> Unit get() = current.value.toggleCategory
    val clearFilters: () -> Unit get() = current.value.clearFilters
    val openMedication: (String) -> Unit get() = current.value.openMedication
    val openSettings: () -> Unit get() = current.value.openSettings
    val openConnection: (() -> Unit)? get() = current.value.openConnection
    val refresh: (() -> Unit)? get() = current.value.refresh
    val openCreate: (() -> Unit)? get() = current.value.openCreate
    val openRestore: (() -> Unit)? get() = current.value.openRestore
    val saveAvailability: ((AvailabilitySelection, AvailabilityStatus, () -> Unit) -> Unit)? get() = current.value.saveAvailability
    val openAddMedication: (() -> Unit)? get() = current.value.openAddMedication
    val onModalVisibilityChanged: (Boolean) -> Unit get() = current.value.onModalVisibilityChanged
    val availabilityInProgress: () -> Boolean get() = current.value.availabilityInProgress
    val canOpenHomeModal: () -> Boolean get() = current.value.canOpenHomeModal
    val setGroupingEnabled: (Boolean) -> Unit get() = current.value.setGroupingEnabled
    val setGrouping: (HomeGrouping) -> Unit get() = current.value.setGrouping
    val saveBulk: ((AvailabilitySelection, BulkMedicationChanges, () -> Unit) -> Unit)? get() = current.value.saveBulk
    val prefillMedication: (String) -> Unit get() = current.value.prefillMedication
    val onBulkModeChanged: (Boolean) -> Unit get() = current.value.onBulkModeChanged
    val toggleExtraFilter: (String) -> Unit get() = current.value.toggleExtraFilter
    val onBottomBarsHeightChanged: (Int) -> Unit get() = current.value.onBottomBarsHeightChanged
    val toggleAvailableFilter: () -> Unit get() = current.value.toggleAvailableFilter
    val editMedication: ((String) -> Unit)? get() = current.value.editMedication
}

@Composable internal fun rememberHomeActions(callbacks: HomeCallbacks): HomeActions {
    val current = rememberUpdatedState(callbacks)
    return remember { HomeActions(current) }
}

internal data class SettingsCallbacks(
    val setTheme: (ThemeChoice) -> Unit,
    val setSort: (HomeSort) -> Unit,
    val setLayout: (CardLayout) -> Unit,
    val setNames: (NameDisplay) -> Unit,
    val setSecondaryHijri: (Boolean) -> Unit,
    val setAccent: (ThemeAccent) -> Unit,
    val setTwoColumns: (Boolean) -> Unit,
    val setSectionsAtBottom: (Boolean) -> Unit,
    val setSearchAtBottom: (Boolean) -> Unit,
    val setGroupingEnabled: (Boolean) -> Unit,
    val setGrouping: (HomeGrouping) -> Unit,
    val openBackup: (() -> Unit)? = null,
    val openMaintenance: (() -> Unit)? = null,
    val openLeave: (() -> Unit)? = null,
    val changeDemoAdmin: (Boolean) -> Unit = {},
    val resetDemo: () -> Unit = {},
    val openAbout: () -> Unit
)

@Stable
internal class SettingsActions(private val current: State<SettingsCallbacks>) {
    val setTheme: (ThemeChoice) -> Unit get() = current.value.setTheme
    val setSort: (HomeSort) -> Unit get() = current.value.setSort
    val setLayout: (CardLayout) -> Unit get() = current.value.setLayout
    val setNames: (NameDisplay) -> Unit get() = current.value.setNames
    val setSecondaryHijri: (Boolean) -> Unit get() = current.value.setSecondaryHijri
    val setAccent: (ThemeAccent) -> Unit get() = current.value.setAccent
    val setTwoColumns: (Boolean) -> Unit get() = current.value.setTwoColumns
    val setSectionsAtBottom: (Boolean) -> Unit get() = current.value.setSectionsAtBottom
    val setSearchAtBottom: (Boolean) -> Unit get() = current.value.setSearchAtBottom
    val setGroupingEnabled: (Boolean) -> Unit get() = current.value.setGroupingEnabled
    val setGrouping: (HomeGrouping) -> Unit get() = current.value.setGrouping
    val openBackup: (() -> Unit)? get() = current.value.openBackup
    val openMaintenance: (() -> Unit)? get() = current.value.openMaintenance
    val openLeave: (() -> Unit)? get() = current.value.openLeave
    val changeDemoAdmin: (Boolean) -> Unit get() = current.value.changeDemoAdmin
    val resetDemo: () -> Unit get() = current.value.resetDemo
    val openAbout: () -> Unit get() = current.value.openAbout
}

@Composable internal fun rememberSettingsActions(callbacks: SettingsCallbacks): SettingsActions {
    val current = rememberUpdatedState(callbacks)
    return remember { SettingsActions(current) }
}
