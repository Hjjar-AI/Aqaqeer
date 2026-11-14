package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*

/** Navigation only; callers close the drawer and recheck context before executing callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeDrawer(ready: CatalogState.Ready?, filters: HomeFilters, busy: Boolean,
    openRoute: (String) -> Unit, openReview: () -> Unit, openHistory: () -> Unit,
    chooseCategory: (String?) -> Unit, showDataTimes: () -> Unit) {
    val pharmacy = ready?.catalog?.pharmacy
    val realAdmin = ready?.let { it.admin && !it.isDemo } == true
    val editable = ready?.let { realAdmin && !it.requiresUpdate && !it.maintenancePaused } == true
    var categoriesOpen by rememberSaveable(pharmacy?.pharmacyId, realAdmin) { mutableStateOf(true) }
    var catalogOpen by rememberSaveable(pharmacy?.pharmacyId, realAdmin) { mutableStateOf(true) }
    var sharingOpen by rememberSaveable(pharmacy?.pharmacyId, realAdmin) { mutableStateOf(true) }
    var applicationOpen by rememberSaveable(pharmacy?.pharmacyId, realAdmin) { mutableStateOf(true) }
    val drawerWidth = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.width.toDp() - 72.dp).coerceIn(1.dp, 320.dp) }
    ModalDrawerSheet(modifier = Modifier.requiredWidth(drawerWidth)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(UiSpacing.related),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            Row(Modifier.fillMaxWidth().padding(vertical = UiSpacing.related),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                BrandEmblem(Modifier.size(40.dp), description = null)
                Text(pharmacy?.name ?: uiText(R.string.ui_screen_titles_020), modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium)
            }
            NavigationDrawerItem(icon = { AppIcon(UiIcon.HOME) }, label = { Text(uiText(R.string.ui_home_drawer_001)) }, selected = true, onClick = { openRoute("home") })
            HorizontalDivider()
            if (pharmacy != null) {
                DrawerGroup(uiText(R.string.ui_home_drawer_002) + if (filters.categoryIds.isNotEmpty())
                    " (${MedicationPresentation.easternDigits(filters.categoryIds.size.toString())})" else "",
                    categoriesOpen, { categoriesOpen = !categoriesOpen }) {
                    if (filters.categoryIds.size > 1) SupportingText(uiText(R.string.ui_home_drawer_003))
                    NavigationDrawerItem(label = { Text(uiText(R.string.ui_home_drawer_004)) }, selected = filters.categoryIds.isEmpty(),
                        onClick = { chooseCategory(null) })
                    ready.catalog.categories.forEach { category ->
                        NavigationDrawerItem(label = { Text(category.nameAr) }, selected = filters.categoryIds == setOf(category.id),
                            onClick = { chooseCategory(category.id) })
                    }
                }
            }
            if (realAdmin) {
                HorizontalDivider()
                DrawerGroup(uiText(R.string.ui_home_drawer_005), catalogOpen, { catalogOpen = !catalogOpen }) {
                    DrawerRow(uiText(R.string.ui_screen_titles_005), { openRoute("taxonomy") }, enabled = editable && !busy, icon = UiIcon.FORM_FORM)
                    DrawerRow(uiText(R.string.ui_screen_titles_015), openHistory, enabled = !busy, icon = UiIcon.HISTORY)
                    DrawerRow(uiText(R.string.ui_pharmacy_admin_screen_046), { openRoute("csv-import") }, enabled = editable && !busy, icon = UiIcon.ADD)
                    DrawerRow(uiText(R.string.ui_pharmacy_admin_screen_047), { openRoute("reports") }, enabled = editable && !busy, icon = UiIcon.REPORT)
                    DrawerRow(uiText(R.string.ui_screen_titles_002), { openRoute("admin") })
                }
                HorizontalDivider()
                DrawerGroup(uiText(R.string.ui_home_drawer_006), sharingOpen, { sharingOpen = !sharingOpen }) {
                    DrawerRow(uiText(R.string.ui_screen_titles_004), openReview, enabled = !busy, icon = UiIcon.PUBLISH,
                        badge = if (ready.pendingCount > 0) MedicationPresentation.easternDigits(ready.pendingCount.toString()) else null)
                    if (ready.lifecycle == "LOCAL_DRAFT") DrawerRow(uiText(R.string.ui_home_drawer_007), { openRoute("setup") }, enabled = editable && !busy, icon = UiIcon.SETUP)
                    DrawerRow(uiText(R.string.ui_screen_titles_008), { openRoute("export-connection") },
                        enabled = editable && !busy && ready.lifecycle == "SHARED" && !ready.restorePaused, icon = UiIcon.CONNECTION)
                    DrawerRow(uiText(R.string.ui_screen_titles_011), { openRoute("backup") }, enabled = !busy, icon = UiIcon.BACKUP)
                    DrawerRow(uiText(R.string.ui_pharmacy_admin_screen_041), { openRoute("maintenance") }, enabled = !busy, icon = UiIcon.TOOLS)
                }
            } else if (pharmacy != null && !ready.isDemo) {
                DrawerRow(uiText(R.string.ui_screen_titles_006), { openRoute("promote") }, enabled = !busy, icon = UiIcon.EDIT)
            }
            HorizontalDivider()
            DrawerGroup(uiText(R.string.ui_home_drawer_008), applicationOpen, { applicationOpen = !applicationOpen }) {
                DrawerRow(uiText(R.string.ui_screen_titles_012), { openRoute("settings") }, icon = UiIcon.SETTINGS)
                DrawerRow(uiText(R.string.ui_screen_titles_013), { openRoute("about") }, icon = UiIcon.INFO)
                if (pharmacy != null) DrawerRow(uiText(R.string.ui_home_drawer_009), showDataTimes, icon = UiIcon.CALENDAR)
            }
        }
    }
}

@Composable private fun DrawerGroup(title: String, expanded: Boolean, toggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    TextButton(onClick = toggle, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.touch)
        .semantics { stateDescription = if (expanded) uiText(R.string.ui_home_drawer_010) else uiText(R.string.ui_home_drawer_011) }) {
        Text(title, modifier = Modifier.weight(1f))
        Box(Modifier.rotate(if (expanded) 180f else 0f)) { RowChevron(down = true) }
    }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(180))) {
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = UiSpacing.related), content = content)
    }
}

@Composable private fun DrawerRow(label: String, onClick: () -> Unit, enabled: Boolean = true,
    icon: UiIcon = UiIcon.EDIT, badge: String? = null) {
    NavigationDrawerItem(selected = false, label = { Text(label) },
        icon = { AppIcon(icon) }, badge = if (badge != null) ({ Badge { Text(badge) } }) else null,
        onClick = { if (enabled) onClick() }, modifier = Modifier.then(if (enabled) Modifier else Modifier.semantics { disabled() }))
}
