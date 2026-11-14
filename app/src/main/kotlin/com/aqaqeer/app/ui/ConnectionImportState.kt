package com.aqaqeer.app.ui

import com.aqaqeer.app.uiText
import com.aqaqeer.app.R

import android.net.Uri
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
        EnvelopeError.AUTHENTICATION -> uiText(R.string.ui_connection_import_state_001)
        EnvelopeError.INVALID_CREDENTIAL -> uiText(R.string.ui_connection_import_state_002)
        EnvelopeError.TOO_LARGE -> uiText(R.string.ui_connection_import_state_003)
        EnvelopeError.RESOURCE_LIMIT -> uiText(R.string.ui_connection_import_state_004)
        EnvelopeError.UNSUPPORTED_FORMAT -> uiText(R.string.ui_connection_import_state_005)
        EnvelopeError.INVALID_FORMAT -> uiText(R.string.ui_connection_import_state_006)
        EnvelopeError.IO -> uiText(R.string.ui_connection_import_state_007)
        EnvelopeError.CRYPTO_UNAVAILABLE -> uiText(R.string.ui_connection_import_state_008)
    }
    is PayloadException -> when (error.error) {
        PayloadError.RESOURCE_LIMIT -> uiText(R.string.ui_connection_import_state_004)
        PayloadError.UNSUPPORTED_SCHEMA -> uiText(R.string.ui_connection_import_state_009)
        else -> uiText(R.string.ui_connection_import_state_010)
    }
    is ConnectionImportException -> when (error.error) {
        ConnectionImportError.CONFIGURED -> uiText(R.string.ui_connection_import_state_011)
        ConnectionImportError.DEMO -> uiText(R.string.ui_connection_import_state_012)
        ConnectionImportError.PENDING_EXISTS -> uiText(R.string.ui_connection_import_state_013)
        ConnectionImportError.INVALID_URI -> uiText(R.string.ui_connection_import_state_014)
    }
    is StorageRecoveryRequired -> uiText(R.string.ui_connection_import_state_015)
    is PendingConnectionChanged -> uiText(R.string.ui_connection_import_state_016)
    is SecurityException -> uiText(R.string.ui_connection_import_state_017)
    else -> uiText(R.string.ui_connection_import_state_018)
}

internal fun connectionVerificationMessage(error: Exception): String = when (error) {
    is SnapshotReadException -> when (error.error) {
        SnapshotReadError.INVALID_CREDENTIALS -> uiText(R.string.ui_connection_import_state_019)
        SnapshotReadError.ACCESS_DENIED -> uiText(R.string.ui_connection_import_state_020)
        SnapshotReadError.SNAPSHOT_UNAVAILABLE -> uiText(R.string.ui_connection_import_state_021)
        SnapshotReadError.RESPONSE_TOO_LARGE -> uiText(R.string.ui_connection_import_state_022)
        SnapshotReadError.RESOURCE_LIMIT -> uiText(R.string.ui_connection_import_state_023)
        SnapshotReadError.RATE_LIMITED -> uiText(R.string.ui_connection_import_state_024)
        SnapshotReadError.NETWORK -> uiText(R.string.ui_connection_import_state_025)
        SnapshotReadError.CHANGED_DURING_READ -> uiText(R.string.ui_connection_import_state_026)
        SnapshotReadError.CAPTION_MISMATCH -> uiText(R.string.ui_connection_import_state_027)
        SnapshotReadError.INVALID_RESPONSE -> uiText(R.string.ui_connection_import_state_028)
    }
    is EnvelopeException -> when (error.error) {
        EnvelopeError.AUTHENTICATION -> uiText(R.string.ui_connection_import_state_029)
        EnvelopeError.TOO_LARGE -> uiText(R.string.ui_connection_import_state_030)
        EnvelopeError.RESOURCE_LIMIT -> uiText(R.string.ui_connection_import_state_023)
        EnvelopeError.UNSUPPORTED_FORMAT -> uiText(R.string.ui_connection_import_state_031)
        else -> uiText(R.string.ui_connection_import_state_032)
    }
    is PayloadException -> when (error.error) {
        PayloadError.UNSUPPORTED_SCHEMA -> uiText(R.string.ui_connection_import_state_033)
        PayloadError.WRONG_PHARMACY -> uiText(R.string.ui_connection_import_state_034)
        PayloadError.WRONG_KEY_VERSION -> uiText(R.string.ui_connection_import_state_035)
        PayloadError.RESOURCE_LIMIT -> uiText(R.string.ui_connection_import_state_023)
        else -> uiText(R.string.ui_connection_import_state_036)
    }
    else -> connectionImportMessage(error)
}

internal fun refreshMessage(error: Exception): String = when (error) {
    is com.aqaqeer.app.data.RefreshException -> when (error.error) {
        com.aqaqeer.app.data.RefreshError.UPDATE_REQUIRED -> uiText(R.string.ui_connection_import_state_037)
        com.aqaqeer.app.data.RefreshError.PUBLICATION_RECOVERY -> uiText(R.string.ui_connection_import_state_038)
        com.aqaqeer.app.data.RefreshError.LOCAL_WORK -> uiText(R.string.ui_connection_import_state_039)
        com.aqaqeer.app.data.RefreshError.LOCAL_STATE_CHANGED -> uiText(R.string.ui_connection_import_state_040)
        else -> uiText(R.string.ui_connection_import_state_041)
    }
    else -> connectionVerificationMessage(error).replace(uiText(R.string.ui_connection_import_state_042), uiText(R.string.ui_connection_import_state_043))
}
