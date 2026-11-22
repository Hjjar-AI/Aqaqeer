package com.aqaqeer.app.ui

import androidx.compose.runtime.SideEffect

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.aqaqeer.app.data.*

@Composable internal fun AvailabilityElsewhereTip(medicationId: String, sectionId: String?, elsewhere: List<Section>) {
    val uiText = rememberUiText()
    val tipIdentity = listOf(LocalNoticeScope.current, "availability-tip", medicationId, sectionId.orEmpty())
    val tipContent = elsewhere.flatMap { listOf(it.id, it.nameAr) }
    SideEffect { UiSession.reconcile(tipIdentity, if (elsewhere.isEmpty()) emptyMap() else mapOf(tipIdentity to tipContent)) }
    if (elsewhere.isNotEmpty() && !UiSession.isDismissed(tipIdentity, tipContent)) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(UiSpacing.hair).testTag("tip_${medicationId}"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.hair)) {
            AppIcon(UiIcon.INFO)
            Text(uiText(R.string.availability_elsewhere_prefix) + elsewhere.joinToString(uiText(R.string.shared_arabic_list_separator)) { it.nameAr },
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { UiSession.dismiss(tipIdentity, tipContent) }) {
                AppIcon(UiIcon.CLOSE, description = uiText(R.string.notice_dismiss))
            }
        }
    }
}
