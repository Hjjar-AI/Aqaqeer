package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.aqaqeer.app.data.*

/** Navigation only; callers close the drawer and recheck context before executing callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeDrawer(ready: CatalogState.Ready?, filters: HomeFilters, busy: Boolean,
    openRoute: (String) -> Unit, chooseCategory: (String?) -> Unit, showDataTimes: () -> Unit) {
    val pharmacy = ready?.catalog?.pharmacy
    var categoriesOpen by rememberSaveable(pharmacy?.pharmacyId) { mutableStateOf(true) }
    var catalogOpen by rememberSaveable(pharmacy?.pharmacyId) { mutableStateOf(false) }
    var applicationOpen by rememberSaveable(pharmacy?.pharmacyId) { mutableStateOf(false) }
    val drawerWidth = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.width.toDp() - 72.dp).coerceIn(1.dp, 320.dp) }
    ModalDrawerSheet(modifier = Modifier.requiredWidth(drawerWidth)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(UiSpacing.related),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                BrandEmblem(Modifier.size(40.dp), description = null)
                Text(pharmacy?.name ?: uiText(R.string.ui_screen_titles_020), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium)
            }
            CompactDrawerItem(icon = UiIcon.HOME, label = uiText(R.string.ui_home_drawer_001), selected = true, onClick = { openRoute("home") })
            if (pharmacy != null) CollapsibleGroup(uiText(R.string.ui_home_drawer_002), categoriesOpen, { categoriesOpen = !categoriesOpen }, contentSpacing = 0.dp) {
                if (filters.categoryIds.size > 1) SupportingText(uiText(R.string.ui_home_drawer_003))
                CompactDrawerItem(label = uiText(R.string.ui_home_drawer_004), selected = filters.categoryIds.isEmpty(), onClick = { chooseCategory(null) })
                ready.catalog.categories.forEach { category ->
                    CompactDrawerItem(label = category.nameAr, selected = filters.categoryIds == setOf(category.id), onClick = { chooseCategory(category.id) })
                }
            }
            if (pharmacy != null && !ready.isDemo) CollapsibleGroup(uiText(R.string.ui_home_drawer_005), catalogOpen, { catalogOpen = !catalogOpen }, contentSpacing = 0.dp) {
                DrawerRow(uiText(R.string.ui_screen_titles_002), { openRoute("admin") }, icon = UiIcon.TOOLS)
            }
            CollapsibleGroup(uiText(R.string.ui_home_drawer_008), applicationOpen, { applicationOpen = !applicationOpen }, contentSpacing = 0.dp) {
                DrawerRow(uiText(R.string.ui_screen_titles_013), { openRoute("about") }, icon = UiIcon.INFO)
                if (pharmacy != null) DrawerRow(uiText(R.string.ui_home_drawer_009), showDataTimes, icon = UiIcon.CALENDAR)
            }
        }
        Surface(shadowElevation = UiSpacing.hair) {
            DrawerRow(uiText(R.string.ui_screen_titles_012), { openRoute("settings") }, icon = UiIcon.SETTINGS)
        }
    }
}

@Composable private fun DrawerRow(label: String, onClick: () -> Unit, enabled: Boolean = true,
    icon: UiIcon = UiIcon.EDIT, badge: String? = null) {
    CompactDrawerItem(label, selected = false, onClick = onClick, icon = icon, enabled = enabled, badge = badge)
}

/** Compact navigation rows retain a 48 dp target and expand naturally for larger labels. */
@Composable private fun CompactDrawerItem(label: String, selected: Boolean, onClick: () -> Unit,
    icon: UiIcon? = null, enabled: Boolean = true, badge: String? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).selectable(selected = selected, enabled = enabled,
        role = Role.Tab, onClick = onClick), shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant) {
        Row(Modifier.heightIn(min = UiSpacing.touch).padding(horizontal = UiSpacing.section, vertical = UiSpacing.related),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
            icon?.let { AppIcon(it) }
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            badge?.let { Badge { Text(it) } }
        }
    }
}
