package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import android.net.Uri
import com.aqaqeer.app.data.MedicationPresentation
import com.aqaqeer.app.data.ConnectionImportException
import com.aqaqeer.app.data.ConnectionImportError
import com.aqaqeer.app.data.PendingConnectionVerification
import com.aqaqeer.app.security.StorageRecoveryRequired
import com.aqaqeer.app.security.files.*
import com.aqaqeer.app.sync.SnapshotReadError
import com.aqaqeer.app.sync.SnapshotReadException

data class ConnectionImportUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val verifying: Boolean = false,
    val activating: Boolean = false,
    val storageReady: Boolean = false,
    val selectedUri: Uri? = null,
    val pending: PendingConnectionInfo? = null,
    val requirements: SnapshotRequirements? = null,
    val verification: PendingConnectionVerification? = null,
    val error: String? = null,
)

internal fun connectionImportMessage(error: Exception): String = when (error) {
    is EnvelopeException -> when (error.error) {
        EnvelopeError.AUTHENTICATION -> uiText(R.string.connection_import_password_or_corrupt_error)
        EnvelopeError.INVALID_CREDENTIAL -> uiText(R.string.connection_import_password_required_error)
        EnvelopeError.TOO_LARGE -> uiText(R.string.connection_import_file_size_error)
        EnvelopeError.RESOURCE_LIMIT -> uiText(R.string.connection_import_file_resources_error)
        EnvelopeError.UNSUPPORTED_FORMAT -> uiText(R.string.connection_import_file_format_unsupported_error)
        EnvelopeError.INVALID_FORMAT -> uiText(R.string.connection_import_invalid_file_error)
        EnvelopeError.IO -> uiText(R.string.connection_import_file_io_error)
        EnvelopeError.CRYPTO_UNAVAILABLE -> uiText(R.string.connection_import_crypto_unavailable_error)
    }
    is PayloadException -> when (error.error) {
        PayloadError.RESOURCE_LIMIT -> uiText(R.string.connection_import_file_resources_error)
        PayloadError.UNSUPPORTED_SCHEMA -> uiText(R.string.connection_import_file_version_unsupported_error)
        else -> uiText(R.string.connection_import_invalid_payload_error)
    }
    is ConnectionImportException -> when (error.error) {
        ConnectionImportError.CONFIGURED -> uiText(R.string.connection_import_already_configured_error)
        ConnectionImportError.DEMO -> uiText(R.string.connection_import_demo_blocked_error)
        ConnectionImportError.PENDING_EXISTS -> uiText(R.string.connection_import_pending_exists_error)
        ConnectionImportError.INVALID_URI -> uiText(R.string.connection_import_choose_file_error)
    }
    is StorageRecoveryRequired -> uiText(R.string.connection_import_protected_storage_error)
    is PendingConnectionChanged -> uiText(R.string.connection_import_pending_changed_error)
    is SecurityException -> uiText(R.string.connection_import_read_permission_error)
    else -> uiText(R.string.connection_import_operation_failed_error)
}

internal fun connectionVerificationMessage(error: Exception): String = when (error) {
    is SnapshotReadException -> when (error.error) {
        SnapshotReadError.INVALID_CREDENTIALS -> uiText(R.string.connection_import_bot_invalid_error)
        SnapshotReadError.ACCESS_DENIED -> uiText(R.string.connection_import_group_access_error)
        SnapshotReadError.SNAPSHOT_UNAVAILABLE -> uiText(R.string.connection_import_pinned_snapshot_missing_error)
        SnapshotReadError.RESPONSE_TOO_LARGE -> uiText(R.string.connection_import_download_size_error)
        SnapshotReadError.RESOURCE_LIMIT -> uiText(R.string.connection_import_snapshot_resources_error)
        SnapshotReadError.RATE_LIMITED -> uiText(R.string.connection_import_rate_limit_error) +
            if (error.retryAfterSeconds > 0) uiText(R.string.logic_retry_after, MedicationPresentation.displayDigits(error.retryAfterSeconds.toString())) else ""
        SnapshotReadError.NETWORK -> uiText(R.string.connection_import_network_error)
        SnapshotReadError.CHANGED_DURING_READ -> uiText(R.string.connection_import_snapshot_changed_error)
        SnapshotReadError.CAPTION_MISMATCH -> uiText(R.string.connection_import_caption_mismatch_error)
        SnapshotReadError.INVALID_RESPONSE -> uiText(R.string.connection_import_response_invalid_error)
    }
    is EnvelopeException -> when (error.error) {
        EnvelopeError.AUTHENTICATION -> uiText(R.string.connection_import_snapshot_authentication_error)
        EnvelopeError.TOO_LARGE -> uiText(R.string.connection_import_snapshot_size_error)
        EnvelopeError.RESOURCE_LIMIT -> uiText(R.string.connection_import_snapshot_resources_error)
        EnvelopeError.UNSUPPORTED_FORMAT -> uiText(R.string.connection_import_snapshot_format_unsupported_error)
        else -> uiText(R.string.connection_import_snapshot_io_error)
    }
    is PayloadException -> when (error.error) {
        PayloadError.UNSUPPORTED_SCHEMA -> uiText(R.string.connection_import_snapshot_version_unsupported_error)
        PayloadError.WRONG_PHARMACY -> uiText(R.string.connection_import_snapshot_pharmacy_mismatch_error)
        PayloadError.WRONG_KEY_VERSION -> uiText(R.string.connection_import_snapshot_key_mismatch_error)
        PayloadError.RESOURCE_LIMIT -> uiText(R.string.connection_import_snapshot_resources_error)
        else -> uiText(R.string.connection_import_snapshot_invalid_error)
    }
    else -> connectionImportMessage(error)
}

internal fun refreshMessage(error: Exception): String = when (error) {
    is com.aqaqeer.app.data.RefreshException -> when (error.error) {
        com.aqaqeer.app.data.RefreshError.UPDATE_REQUIRED -> uiText(R.string.connection_refresh_app_version_unsupported_error)
        com.aqaqeer.app.data.RefreshError.PUBLICATION_RECOVERY -> uiText(R.string.connection_refresh_lineage_review_error)
        com.aqaqeer.app.data.RefreshError.LOCAL_WORK -> uiText(R.string.connection_refresh_local_work_review_error)
        com.aqaqeer.app.data.RefreshError.LOCAL_STATE_CHANGED -> uiText(R.string.connection_refresh_local_data_changed_error)
        else -> uiText(R.string.connection_refresh_unavailable_error)
    }
    else -> connectionVerificationMessage(error).replace(uiText(R.string.connection_pending_status_label), uiText(R.string.connection_saved_data_status_label))
}
