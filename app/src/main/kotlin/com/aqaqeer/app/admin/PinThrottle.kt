package com.aqaqeer.app.admin

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.aqaqeer.app.maintenance.MaintenanceStore
import com.aqaqeer.app.security.SecureStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable internal data class PinDelay(val pharmacyId: String, val failures: Int,
    val boot: Int, val retryAtElapsed: Long)

/** Called inside the catalog transaction, shared by promotion and current-PIN verification. */
@Singleton
class PinThrottle @Inject constructor(@param:ApplicationContext private val context: Context,
    secure: SecureStore) {
    private val store = MaintenanceStore(secure)
    private fun delay(failures: Int) = if (failures == 0) 0L else 1000L shl failures.coerceAtMost(10)
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    internal fun ready(pharmacyId: String, legacyFailures: Int = 0): PinDelay {
        val boot = boot(); val now = SystemClock.elapsedRealtime()
        val saved = store.decode<PinDelay>("pin-delay")?.takeIf { it.pharmacyId == pharmacyId }
        val state = when {
            saved == null -> PinDelay(pharmacyId, legacyFailures.coerceIn(0, 30), boot,
                now + delay(legacyFailures.coerceIn(0, 30)))
            saved.boot != boot -> saved.copy(boot = boot, retryAtElapsed = now + delay(saved.failures))
            else -> saved
        }
        if (state != saved) store.encode("pin-delay", state)
        check(now >= state.retryAtElapsed) { "انتظر قليلاً قبل المحاولة التالية" }
        return state
    }
    internal fun record(state: PinDelay, valid: Boolean) {
        val failures = if (valid) 0 else (state.failures + 1).coerceAtMost(30)
        store.encode("pin-delay", state.copy(failures = failures,
            retryAtElapsed = SystemClock.elapsedRealtime() + delay(failures)))
    }
}
