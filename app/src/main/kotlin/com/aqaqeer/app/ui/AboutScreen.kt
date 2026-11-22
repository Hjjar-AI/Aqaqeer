package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*

@Composable internal fun AboutScreen(contact: () -> Unit) {
    val uiText = rememberUiText()
    LazyColumn(Modifier.fillMaxSize().testTag("about"), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section), horizontalAlignment = Alignment.CenterHorizontally) {
        item { BrandEmblem(Modifier.size(Size.emblemLarge)) }
        item { ExpandableInformation(uiText(R.string.review_legend), uiText(R.string.review_legend_body) + "\n" + uiText(R.string.review_changed) + ": " + uiText(R.string.review_changed_help)) }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(uiText(R.string.navigation_app_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(bidi(BuildConfig.VERSION_NAME), fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (BuildConfig.IS_DEMO) Text(uiText(R.string.navigation_demo_build_label), color = MaterialTheme.colorScheme.secondary)
            }
        }
        item { InformationCard(uiText(R.string.navigation_about_app_title), uiText(R.string.navigation_about_app_help)) }
        item {
            GroupedCard(title = uiText(R.string.navigation_development_heading)) {
                Text(uiText(R.string.navigation_developer_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(bidi(uiText(R.string.contact_email)), fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.testTag("developer_email"))
                AppButton(variant = AppButtonVariant.SECONDARY, onClick = contact, modifier = Modifier.testTag("email_contact")) { Text(uiText(R.string.navigation_email_contact_action)) }
            }
        }
        item { InformationCard(uiText(R.string.navigation_app_update_title), uiText(R.string.navigation_manual_update_help)) }
    }
}
