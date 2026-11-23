package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.aqaqeer.app.data.*

/** Navigation only; callers close the drawer and recheck context before executing callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun HomeDrawer(ready: CatalogState.Ready?, filters: HomeFilters, busy: Boolean,
    openRoute: (Route) -> Unit, chooseCategory: (String?) -> Unit, showDataTimes: () -> Unit) {
    val uiText = rememberUiText()
    val pharmacy = ready?.catalog?.pharmacy
    val categories = ready?.catalog?.categories.orEmpty()
    val drawerCategories = remember(categories) {
        categories.sortedWith(compareBy<Category> { it.id == CatalogSeeds.UNCATEGORIZED_ID }
            .thenBy { normalizedName(it.nameAr) }.thenBy { it.nameAr }.thenBy { it.id })
    }
    var categoriesOpen by rememberSaveable(pharmacy?.pharmacyId) { mutableStateOf(true) }
    var catalogOpen by rememberSaveable(pharmacy?.pharmacyId) { mutableStateOf(false) }
    var applicationOpen by rememberSaveable(pharmacy?.pharmacyId) { mutableStateOf(false) }
    val drawerWidth = (LocalLayoutMetrics.current.width - Size.drawerOutside).coerceIn(Size.drawerMin, Size.drawerMax)
    ModalDrawerSheet(modifier = Modifier.requiredWidth(drawerWidth)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(UiSpacing.related),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.related)) {
                BrandEmblem(Modifier.size(Size.avatarM), description = null)
                Text(pharmacy?.name ?: uiText(R.string.navigation_app_title), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium)
            }
            CompactDrawerItem(icon = UiIcon.HOME, label = uiText(R.string.navigation_drawer_home_label), selected = true, onClick = { openRoute(Route.Home) })
            if (ready?.isDemo == true) DrawerRow(uiText(R.string.review_demo_mode), { openRoute(Route.Settings) }, icon = UiIcon.SETTINGS)
            if (pharmacy != null) DrawerGroup(uiText(R.string.navigation_drawer_categories_heading), categoriesOpen, { categoriesOpen = !categoriesOpen }, UiIcon.CATEGORY) {
                if (filters.categoryIds.size > 1) SupportingText(uiText(R.string.home_drawer_replace_categories_help))
                CompactDrawerItem(label = uiText(R.string.home_drawer_all_categories_label), selected = filters.categoryIds.isEmpty(), onClick = { chooseCategory(null) })
                drawerCategories.forEach { category ->
                    CompactDrawerItem(label = category.nameAr, selected = filters.categoryIds == setOf(category.id), onClick = { chooseCategory(category.id) })
                }
            }
            if (pharmacy != null) DrawerGroup(uiText(R.string.navigation_drawer_catalog_history_heading), catalogOpen, { catalogOpen = !catalogOpen }, UiIcon.HISTORY) {
                DrawerRow(uiText(R.string.navigation_admin_tools_title), { openRoute(Route.Admin) }, icon = UiIcon.TOOLS)
            }
            DrawerGroup(uiText(R.string.navigation_drawer_application_heading), applicationOpen, { applicationOpen = !applicationOpen }, UiIcon.INFO) {
                DrawerRow(uiText(R.string.navigation_about_title), { openRoute(Route.About) }, icon = UiIcon.INFO)
                if (pharmacy != null) DrawerRow(uiText(R.string.home_drawer_data_times_label), showDataTimes, icon = UiIcon.CALENDAR)
            }
        }
        Surface(shadowElevation = UiSpacing.hair) {
            DrawerRow(uiText(R.string.navigation_settings_title), { openRoute(Route.Settings) }, icon = UiIcon.SETTINGS)
        }
    }
}

/** Drawer disclosure headings use the same icon language and corners as navigation rows. */
@Composable private fun DrawerGroup(title: String, expanded: Boolean, toggle: () -> Unit,
    icon: UiIcon, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        DisclosureHeader(title, expanded, toggle, style = DisclosureStyle.DRAWER, icon = icon)
        if (expanded) ContentSection(spacing = Space.none, content = content)
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
