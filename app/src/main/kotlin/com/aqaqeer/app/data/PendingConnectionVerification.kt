package com.aqaqeer.app.data

import com.aqaqeer.app.security.files.PendingConnectionInfo
import com.aqaqeer.app.security.files.SnapshotPublication
import com.aqaqeer.app.security.files.SnapshotRequirements
import java.time.Instant

/** Metadata only: never retain downloaded catalog contents or credentials in UI state. */
data class PendingConnectionVerification(
    val pendingId: String, val publication: SnapshotPublication, val checkedAt: Instant,
    val pharmacyName: String?, val medicationCount: Int?, val requirements: SnapshotRequirements,
    val requiresUpdate: Boolean,
)

data class PendingConnectionState(val info: PendingConnectionInfo?, val requirements: SnapshotRequirements?)
