package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.aqaqeer.app.admin.*
import com.aqaqeer.app.data.*
import kotlinx.serialization.json.*

@Composable internal fun PublicationReviewScreen(ready: CatalogState.Ready, vm: CatalogViewModel,
    busy: Boolean, message: String?, sharing: SharingStatus, pending: List<PendingOperation>) {
    val uiText = rememberUiText()
    val catalog = ready.catalog

    val configuration = LocalConfiguration.current
    var typeFilter by rememberSaveable(catalog.pharmacy?.pharmacyId) { mutableStateOf<String?>(null) }
    var choices by remember(catalog.pharmacy?.pharmacyId) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val types = remember(pending) { pending.groupBy { it.entityType } }
    val grouped = remember(pending) { pending.groupBy { it.entityType to it.targetId.substringBefore('/') } }
    val activeType = typeFilter?.takeIf { it in types }
    val visibleGroups = remember(grouped, activeType) {
        grouped.entries.filter { activeType == null || it.key.first == activeType }
    }
    val medicationsById = remember(catalog.medications) { catalog.medications.associateBy { it.id } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.page),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
        item(key = "review_header") {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                OperationStatus(busy || sharing.busy, message, sharing.message)
                if (ready.maintenancePaused) {
                    NoticeStrip(Notice(uiText(R.string.publication_maintenance_block_message), NoticeSeverity.WARNING,
                        uiText(R.string.maintenance_review_first_action), { vm.navigate(Route.Maintenance) }))
                }
                if (ready.restorePaused) {
                    NoticeStrip(Notice(uiText(R.string.publication_restore_review_block_message), NoticeSeverity.WARNING,
                        uiText(R.string.backup_complete_restore_review_action), { vm.navigate(Route.Backup) }))
                }
                GroupedCard(title = uiText(R.string.publication_pending_count_format,
                    MedicationPresentation.displayDigits(ready.pendingCount.toString()))) {
                    if (!ready.maintenancePaused && !ready.restorePaused) {
                        SupportingText(uiText(R.string.publication_automatic_reconnect_help))
                    }
                    if (pending.isEmpty()) {
                        SupportingText(uiText(R.string.admin_review_empty_message))
                    } else {
                        SectionHeading(uiText(R.string.admin_review_filters_heading))
                        WrappingContent {
                            AppChip(activeType == null, onClick = { typeFilter = null }, label = {
                                Text(uiText(R.string.admin_review_filter_count, uiText(R.string.review_all), pending.size))
                            })
                            types.forEach { (type, operations) ->
                                AppChip(activeType == type, onClick = { typeFilter = type }, label = {
                                    Text(uiText(R.string.admin_review_filter_count,
                                        entityLabel(type, text = uiText), operations.size))
                                })
                            }
                        }
                    }
                }
            }
        }
        items(visibleGroups, key = { "pending_${it.key.first.length}:${it.key.first}${it.key.second}" }) { group ->
            val operations = group.value
            val first = operations.first()
            val name = medicationsById[group.key.second]
                ?.let { MedicationPresentation.identity(it, catalog.formsById[it.formId]?.nameAr) } ?: entityLabel(first.entityType, text = uiText)
            GroupedCard(title = name) {
                operations.forEachIndexed { index, op ->
                    key(op.id) {
                        if (index > 0) AppDivider()
                        val before = remember(op.field, op.baseValueJson, catalog, configuration) {
                            reviewValue(op.field, op.baseValueJson, catalog, text = uiText)
                        }
                        val after = remember(op.field, op.intendedValueJson, catalog, configuration) {
                            reviewValue(op.field, op.intendedValueJson, catalog, text = uiText)
                        }
                        ContentSection {
                            Text(fieldLabel(op.field, op.entityType, text = uiText), style = MaterialTheme.typography.titleSmall)
                            LabeledValue(uiText(R.string.publication_before_label), before)
                            LabeledValue(uiText(R.string.publication_after_label), after)
                        }
                    }
                }
            }
        }
        item(key = "review_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.group)) {
                ConflictChoices(sharing.conflicts, choices, catalog) { choices = it }
                val canPublish = !busy && !sharing.busy && !ready.requiresUpdate &&
                    !ready.restorePaused && !ready.maintenancePaused
                GroupedCard(title = uiText(R.string.admin_review_publish_heading)) {
                    if (ready.isDemo) SupportingText(uiText(R.string.review_demo_local_only))
                    AppButton(enabled = canPublish &&
                        (ready.pendingCount > 0 || ready.uncertain || ready.lifecycle == "LOCAL_DRAFT"),
                        onClick = { vm.publish(choices) }) { Text(uiText(R.string.publication_publish_verify_action)) }
                }
                if (!ready.isDemo && ready.lifecycle == "SHARED") {
                    PinRecoveryAction(enabled = canPublish, recover = { vm.publish(choices, recoverPin = true) })
                }
            }
        }
    }
}
