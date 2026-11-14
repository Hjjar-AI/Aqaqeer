package com.aqaqeer.app.security

/** Fixed v1 contracts. Changing either value requires its own compatibility review. */
internal object AdminPinKdfV1 {
    const val ITERATIONS = 200_000
}

internal object PasswordEnvelopeKdfV1 {
    const val ITERATIONS = 200_000
    const val NAME = "PBKDF2-HMAC-SHA256"
}
