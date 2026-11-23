package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppNavHost(state: AppUiState, viewModel: CatalogViewModel, now: java.time.Instant, dualPane: Boolean,
    backProgress: Float, destinationStates: androidx.compose.runtime.saveable.SaveableStateHolder,
    connectionImport: ConnectionImportUiState, adminBusy: Boolean, adminMessage: String?, availabilitySavedId: String?,
    sharing: com.aqaqeer.app.admin.SharingStatus, configured: Boolean, homeModalOpen: Boolean, dataTimesOpen: Boolean,
    onHomeModalChanged: (Boolean) -> Unit, canOpenHomeModal: () -> Boolean, navigate: (Route) -> Unit, back: () -> Unit,
    snackbar: SnackbarHostState, medicationActions: @Composable (CatalogState.Ready, Medication) -> Unit) {
    val uiText = rememberUiText()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AnimatedContent(targetState = state, modifier = Modifier.graphicsLayer {
        scaleX = 1f - 0.04f * backProgress
        scaleY = scaleX
        alpha = 1f - 0.12f * backProgress
    }, contentKey = { appDestinationKey(it.destination, dualPane) }, label = "route",
        transitionSpec = { fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.standard)) }) { displayed ->
        val destinationContent: @Composable () -> Unit = {
            val state = displayed
            if (state.catalog is CatalogState.RecoveryRequired && state.destination !in setOf(Route.Settings, Route.About)) {
                RecoveryScreen(viewModel::retryProtectedStorage) { navigate(Route.Settings) }
            } else when (state.destination) {
                Route.Connection -> ConnectionImportScreen(connectionImport, state.catalog,
                    { viewModel.openConnectionImport(it) }, viewModel::stageConnection,
                    viewModel::cancelConnectionImport, viewModel::discardPendingConnection,
                    viewModel::reloadConnectionImport, viewModel::verifyPendingConnection, viewModel::activatePendingConnection)
                Route.Settings -> SettingsScreen(theme = state.theme,
                            preferences = state.homePreferences,
                            accent = state.accent,
                            demo = BuildConfig.IS_DEMO,
                            demoAdmin = (state.catalog as? CatalogState.Ready)?.admin == true,
                            actions = rememberSettingsActions(SettingsCallbacks(
                                setTheme = viewModel::setTheme,
                                setSort = viewModel::setSort,
                                setLayout = viewModel::setLayout,
                                setNames = viewModel::setNames,
                                setSecondaryHijri = viewModel::setSecondaryHijri,
                                setOtherSectionAvailability = viewModel::setOtherSectionAvailability,
                                setAccent = viewModel::setAccent,
                                setTwoColumns = viewModel::setTwoColumns,
                                setSectionsAtBottom = viewModel::setSectionsAtBottom,
                                setSearchAtBottom = viewModel::setSearchAtBottom,
                                setGroupingEnabled = viewModel::setGroupingEnabled,
                                setGrouping = viewModel::setGrouping,
                                openBackup = if (state.catalog is CatalogState.Ready && !state.catalog.isDemo && !configured) ({ navigate(Route.Backup) }) else null,
                                openMaintenance = null,
                                openLeave = null,
                                changeDemoAdmin = viewModel::setDemoAdmin,
                                resetDemo = viewModel::resetDemo,
                                openAbout = { navigate(Route.About) })))
                Route.Maintenance -> MaintenanceScreen(state.catalog as? CatalogState.Ready, viewModel)
                Route.Backup -> BackupScreen(state, viewModel)
                Route.CsvImport -> CsvImportScreen(state.catalog as? CatalogState.Ready, viewModel)
                Route.Reports -> ReportsScreen(state.catalog as? CatalogState.Ready, viewModel)
                Route.About -> AboutScreen {
                    try {
                        context.startActivity(Intent(Intent.ACTION_SENDTO,
                            ("mailto:" + context.getString(R.string.contact_email)).toUri()))
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar(uiText(R.string.navigation_email_app_missing_message), withDismissAction = true, duration = SnackbarDuration.Short) }
                    } catch (_: SecurityException) {
                        scope.launch { snackbar.showSnackbar(uiText(R.string.navigation_email_app_open_failed_message), withDismissAction = true, duration = SnackbarDuration.Short) }
                    }
                }
                Route.Create, Route.Admin, Route.Setup, Route.Review, Route.Taxonomy, Route.Promote, Route.Leave, Route.ExportConnection -> PharmacyAdminScreen(state, viewModel)
                is Route.History -> HistoryScreen(
                    state.catalog as? CatalogState.Ready,
                    state.destination.medicationId, viewModel,
                    showHijri = state.homePreferences.secondaryHijri)
                is Route.EditMedication -> PharmacyAdminScreen(state, viewModel)
                Route.Home, is Route.Medication, is Route.Unknown -> when (val catalogState = state.catalog) {
                    CatalogState.Loading -> LoadingScreen()
                    CatalogState.RecoveryRequired -> RecoveryScreen(viewModel::retryProtectedStorage) { navigate(Route.Settings) }
                    is CatalogState.Ready -> {
                        HomeRoute(state, catalogState, viewModel, now, adminBusy, adminMessage, availabilitySavedId, sharing,
                            dualPane, homeModalOpen, dataTimesOpen, onHomeModalChanged, canOpenHomeModal, navigate, back, medicationActions)
                    }
                }
            }
        }
        val displayedDestination = appDestinationKey(displayed.destination, dualPane)
        if (displayedDestination == Route.Home || displayedDestination == Route.Taxonomy || displayed.destination is Route.EditMedication)
            destinationStates.SaveableStateProvider(viewModel.destinationStateKey(displayedDestination), destinationContent)
        else destinationContent()
    }
}

internal fun appDestinationKey(route: Route, dualPane: Boolean): Route =
    if (dualPane && route is Route.Medication) Route.Home else route
