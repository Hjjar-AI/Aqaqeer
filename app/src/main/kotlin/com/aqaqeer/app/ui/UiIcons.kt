package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.aqaqeer.app.R

/** Small local vector set; directional resources mirror with the RTL layout. */
internal enum class UiIcon(val resource: Int, val intrinsicColors: Boolean = false) {
    FORM_TABLET(R.drawable.ic_form_tablet, true),
    FORM_CAPSULE(R.drawable.ic_form_capsule, true),
    FORM_BOTTLE(R.drawable.ic_form_bottle, true),
    FORM_SUSPENSION(R.drawable.ic_form_suspension, true),
    FORM_CREAM(R.drawable.ic_form_cream, true),
    FORM_GEL(R.drawable.ic_form_gel, true),
    FORM_INJECTION(R.drawable.ic_form_injection, true),
    FORM_DROP(R.drawable.ic_form_drop, true),
    FORM_TUBE(R.drawable.ic_form_tube, true),
    FORM_INHALER(R.drawable.ic_form_inhaler, true),
    FORM_PATCH(R.drawable.ic_form_patch, true),
    FORM_SACHET(R.drawable.ic_form_sachet, true),
    FORM_SPRAY(R.drawable.ic_form_spray, true),
    FORM_SUPPOSITORY(R.drawable.ic_form_suppository, true),
    FORM_FORM(R.drawable.ic_form_form, true),
    CATEGORY(R.drawable.ic_category), HOME(R.drawable.ic_home), REPORT(R.drawable.ic_report), BACKUP(R.drawable.ic_backup),
    TOOLS(R.drawable.ic_tools), SETUP(R.drawable.ic_setup), CONNECTION(R.drawable.ic_connection),
    SETTINGS(R.drawable.ic_settings), MENU(R.drawable.ic_menu), BACK(R.drawable.ic_back), MORE(R.drawable.ic_more), SEARCH(R.drawable.ic_search),
    FILTER(R.drawable.ic_filter), EDIT(R.drawable.ic_edit), CLOSE(R.drawable.ic_close),
    CHECK(R.drawable.ic_check), WARNING(R.drawable.ic_warning), UNKNOWN(R.drawable.ic_unknown),
    VISIBILITY(R.drawable.ic_visibility), INFO(R.drawable.ic_info), HISTORY(R.drawable.ic_history),
    PUBLISH(R.drawable.ic_publish), ADD(R.drawable.ic_add), CALENDAR(R.drawable.ic_calendar),
}

@Composable internal fun AppIcon(icon: UiIcon, modifier: Modifier = Modifier, description: String? = null, size: androidx.compose.ui.unit.Dp? = null) {
    Icon(painterResource(icon.resource), contentDescription = description, modifier = if (size == null) modifier else modifier.size(size),
        tint = if (icon.intrinsicColors) Color.Unspecified else LocalContentColor.current)
}

/** Only seeded IDs imply a dosage shape; custom forms retain their written label. */
@Composable internal fun DosageFormIcon(form: com.aqaqeer.app.data.MedicationForm) {
    val seed = com.aqaqeer.app.data.CatalogSeeds.forms.indexOfFirst { it.id == form.id }
    val icon = when (seed) {
        0 -> UiIcon.FORM_TABLET
        1 -> UiIcon.FORM_CAPSULE
        2 -> UiIcon.FORM_BOTTLE
        3 -> UiIcon.FORM_SUSPENSION
        4 -> UiIcon.FORM_INJECTION
        5 -> UiIcon.FORM_DROP
        6 -> UiIcon.FORM_CREAM
        7 -> UiIcon.FORM_TUBE
        13 -> UiIcon.FORM_GEL
        8 -> UiIcon.FORM_SUPPOSITORY
        9 -> UiIcon.FORM_INHALER
        10 -> UiIcon.FORM_PATCH
        11 -> UiIcon.FORM_SACHET
        12 -> UiIcon.FORM_SPRAY
        else -> UiIcon.FORM_FORM
    }
    AppIcon(icon, size = Size.iconL)
}
