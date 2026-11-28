package com.aqaqeer.app.admin

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.aqaqeer.app.maintenance.MaintenanceStore
import com.aqaqeer.app.security.SecureStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable internal data class PinDelay(val pharmacyId: String, val failures: Int,
    val boot: Int, val retryAtElapsed: Long, val bootSession: String? = null)

/** Called inside the catalog transaction, shared by promotion and current-PIN verification. */
@Singleton
class PinThrottle @Inject constructor(@param:ApplicationContext private val context: Context,
    secure: SecureStore) {
    private val store = MaintenanceStore(secure)
    private fun delay(failures: Int) = if (failures == 0) 0L else 1000L shl failures.coerceAtMost(10)
    private fun bootCount(): Int? {
        if (Build.VERSION.SDK_INT < 24) return null
        return try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 } }
        catch (_: SecurityException) { null }
    }

    internal fun ready(pharmacyId: String, legacyFailures: Int = 0): PinDelay {
        val count = bootCount()
        val boot = count ?: -1
        val session = if (count == null) {
            if (Build.VERSION.SDK_INT == 23) legacyBootSession else processSession
        } else null
        val now = SystemClock.elapsedRealtime()
        val saved = store.decode<PinDelay>("pin-delay")?.takeIf { it.pharmacyId == pharmacyId }
        val state = when {
            saved == null -> PinDelay(pharmacyId, legacyFailures.coerceIn(0, 30), boot,
                now + delay(legacyFailures.coerceIn(0, 30)), session)
            saved.boot != boot || saved.bootSession != session ->
                saved.copy(boot = boot, bootSession = session, retryAtElapsed = now + delay(saved.failures))
            else -> saved
        }
        if (state != saved) store.encode("pin-delay", state)
        adminCheck(now >= state.retryAtElapsed) { "انتظر قليلاً قبل المحاولة التالية" }
        return state
    }
    internal fun record(state: PinDelay, valid: Boolean) {
        val failures = if (valid) 0 else (state.failures + 1).coerceAtMost(30)
        store.encode("pin-delay", state.copy(failures = failures,
            retryAtElapsed = SystemClock.elapsedRealtime() + delay(failures)))
    }

    private companion object {
        val processSession by lazy { "process:${UUID.randomUUID()}" }
        val legacyBootSession by lazy {
            val bootId = try {
                File("/proc/sys/kernel/random/boot_id").bufferedReader().use { it.readLine()?.trim() }
            } catch (_: IOException) { null }
            catch (_: SecurityException) { null }
            bootId?.takeIf { it.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) }
                ?.let { "boot:$it" } ?: processSession
        }
    }
}
