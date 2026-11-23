package com.aqaqeer.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.R
import com.aqaqeer.app.data.Section

/** Optional compact location text. Settings owns visibility; there is no session dismissal. */
@Composable internal fun AvailabilityElsewhereTip(medicationId: String, @Suppress("UNUSED_PARAMETER") sectionId: String?, elsewhere: List<Section>) {
    val text = rememberUiText()
    if (elsewhere.isNotEmpty()) Text(
        text(R.string.availability_elsewhere_prefix) + elsewhere.joinToString(text(R.string.shared_arabic_list_separator)) { it.nameAr },
        modifier = Modifier.fillMaxWidth().testTag("tip_${medicationId}"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
