package com.aqaqeer.app.sync

import android.os.Build
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.PKIXReason
import javax.net.ssl.SSLHandshakeException

/** In a Telegram HTTPS call, identify a missing trust anchor without exposing the TLS exception. */
internal fun IOException.hasMissingTlsTrustAnchor(): Boolean =
    generateSequence<Throwable>(this) { it.cause }.take(16)
        .dropWhile { it !is SSLHandshakeException }
        .drop(1)
        .any { cause ->
            cause is CertPathValidatorException && (
                // Android 6's Conscrypt sets the message without a PKIX reason.
                cause.message == "Trust anchor for certification path not found." ||
                    (Build.VERSION.SDK_INT >= 24 && cause.reason == PKIXReason.NO_TRUST_ANCHOR)
            )
        }

internal class TelegramCertificateTrustFailure : Exception()

internal val Exception.isTelegramCertificateTrustFailure: Boolean
    get() = this is TelegramCertificateTrustFailure ||
        (this is SnapshotReadException && error == SnapshotReadError.CERTIFICATE_TRUST)
