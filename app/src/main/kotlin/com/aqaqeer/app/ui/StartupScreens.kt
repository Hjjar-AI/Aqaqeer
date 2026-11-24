package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.aqaqeer.app.R
import com.aqaqeer.app.data.*

@Composable internal fun WelcomeScreen(openSettings: () -> Unit, openConnection: (() -> Unit)? = null, openCreate: (() -> Unit)? = null, openRestore: (() -> Unit)? = null) {
    val uiText = rememberUiText()
    val compactHeight = compactHeight(LayoutTokens.welcomeCompactHeight)
    Column(Modifier.fillMaxWidth().padding(vertical = UiSpacing.section), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Text(uiText(R.string.navigation_welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        BrandEmblem(Modifier.size(if (compactHeight) Size.emblemCompact else Size.emblemLarge))
        openConnection?.let { action ->
            GroupedCard(title = uiText(R.string.connection_join_with_file_title)) {
                Text(uiText(R.string.connection_join_with_file_help))
                AppButton(onClick = action) { Text(uiText(R.string.connection_join_with_file_action)) }
            }
        }
        openCreate?.let { action ->
            GroupedCard(title = uiText(R.string.catalog_create_pharmacy_welcome_title)) {
                Text(uiText(R.string.catalog_create_pharmacy_welcome_help))
                AppButton(variant = AppButtonVariant.SECONDARY, onClick = action, modifier = Modifier.testTag("create_pharmacy")) { Text(uiText(R.string.navigation_create_pharmacy_title)) }
            }
        }
        ActionGroup {
            openRestore?.let { action -> AppButton(variant = AppButtonVariant.QUIET, onClick = action) { Text(uiText(R.string.backup_restore_welcome_action)) } }
            AppButton(variant = AppButtonVariant.QUIET, onClick = openSettings) { Text(uiText(R.string.navigation_settings_about_action)) }
        }
    }
}

@Composable internal fun LoadingScreen() {
    val uiText = rememberUiText()
    Column(Modifier.fillMaxSize().padding(UiSpacing.page), verticalArrangement = Arrangement.spacedBy(UiSpacing.section)) {
        Text(uiText(R.string.shared_catalog_loading_message), style = MaterialTheme.typography.bodyMedium)
        AppSpinner()
    }
}

@Composable internal fun RecoveryScreen(retry: () -> Unit, openSettings: () -> Unit) {
    val uiText = rememberUiText()
    FormPage {
        Text(uiText(R.string.shared_protected_storage_retained_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionHeading(uiText(R.string.shared_protected_storage_unavailable_title))
        Text(uiText(R.string.shared_protected_storage_retry_help))
        AppButton(onClick = retry) { Text(uiText(R.string.history_retry_action)) }
        Text(uiText(R.string.shared_protected_storage_recovery_help))
        NavigationCard(uiText(R.string.navigation_help_about_action), openSettings)
    }
}

@Composable internal fun InformationCard(title: String, body: String) {
    GroupedCard(title = title) { SupportingText(body) }
}
