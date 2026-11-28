package com.aqaqeer.app

import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.admin.AdminProblem
import com.aqaqeer.app.admin.PinDelay
import com.aqaqeer.app.admin.PinSecurity
import com.aqaqeer.app.admin.PinThrottle
import com.aqaqeer.app.data.CatalogSearchIndex
import com.aqaqeer.app.data.HomeFilters
import com.aqaqeer.app.data.HomeSort
import com.aqaqeer.app.data.MedicationDuplicates
import com.aqaqeer.app.data.normalizedName
import com.aqaqeer.app.fixtures.M3Fixtures
import com.aqaqeer.app.fixtures.SuiteProfile
import com.aqaqeer.app.maintenance.MaintenanceStore
import com.aqaqeer.app.security.KeystoreSecureStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore

class Android6CompatibilityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()

    @Test fun arabicAndLatinSearchAndDuplicateKeysMatchExpectedSpellings() {
        val catalog = M3Fixtures.catalog()
        val original = catalog.medications.single().copy(nameEn = null, genericAr = "إِيبُـوبْرُوفِين", genericEn = "Café")
        val duplicate = original.copy(id = "other", genericAr = "ايبوبروفين", genericEn = "Cafe")
        assertEquals("ايبوبروفين", normalizedName(original.genericAr!!))
        assertEquals("cafe", normalizedName(original.genericEn!!))
        assertTrue(MedicationDuplicates.matches(original, duplicate))
        val index = CatalogSearchIndex(catalog.copy(medications = listOf(original)))
        for (query in listOf("ايبوبروفين", "CAFE")) {
            assertEquals(listOf(original.id), index.browse(catalog.sections.single().id, query,
                HomeFilters(), HomeSort.SCIENTIFIC_NAME).map { it.id })
        }
    }

    @Test fun exchangeFreshBackupAndPin(): Unit = runBlocking {
        val phase = arguments.getString("android6ExchangePhase")
        assumeTrue(phase != null)
        check(!BuildConfig.IS_DEMO)
        val api = Build.VERSION.SDK_INT
        val incoming = File(context.filesDir, "android6-exchange-in.aqbak")
        val outgoing = File(context.filesDir, "android6-exchange-out.aqbak")
        val passphrase = "  اختبار Passé 😀  "

        if (phase == "peer" || phase == "return") {
            assertEquals(if (phase == "peer") 35 else 23, api)
            check(incoming.isFile && incoming.length() in 1..1_048_576)
            val peerApi = if (api == 23) 35 else 23
            SuiteProfile().use { target ->
                target.ready()
                target.backups.restore(Uri.fromFile(incoming), passphrase.toCharArray(), "", false)
                val restored = target.ready()
                assertEquals("API $peerApi compatibility sample", restored.catalog.medications.single().genericEn)
                val verifier = checkNotNull(restored.catalog.pharmacy).adminPinVerifier
                assertTrue(PinSecurity.matches("١٢٣٤٥٦".toCharArray(), verifier))
                assertFalse(PinSecurity.matches("123457".toCharArray(), verifier))
                assertFalse(restored.admin)
                assertTrue(restored.restorePaused)
            }
            assertTrue(incoming.delete())
        }
        if (phase == "export" || phase == "peer") {
            assertEquals(if (phase == "export") 23 else 35, api)
            check(!outgoing.exists())
            SuiteProfile().use { source ->
                source.create()
                source.medication("API $api compatibility sample")
                source.backups.export(Uri.fromFile(outgoing), passphrase.toCharArray(), passphrase.toCharArray())
            }
            assertTrue(outgoing.isFile && outgoing.length() in 1..1_048_576)
        }
        check(phase in setOf("export", "peer", "return"))
    }

    @Test fun pinDelayAcrossProcessAndReboot() {
        val phase = arguments.getString("android6PinPhase")
        assumeTrue(phase != null)
        assertEquals(23, Build.VERSION.SDK_INT)
        check(!BuildConfig.IS_DEMO)
        val owner = requireNotNull(arguments.getString("android6PinOwner"))
        require(owner.matches(Regex("[a-f0-9]{32}")))
        val namespace = "android6_pin_$owner"
        val secure = KeystoreSecureStore(context, namespace)
        val store = MaintenanceStore(secure)
        val throttle = PinThrottle(context, secure)
        val checkpoint = File(context.noBackupFilesDir, "$namespace.txt")
        val pharmacy = M3Fixtures.pharmacyId
        val delay = 1000L shl 10
        val before = SystemClock.elapsedRealtime()

        if (phase == "arm") {
            check(!checkpoint.exists())
            assertThrows(AdminProblem::class.java) { throttle.ready(pharmacy, 10) }
            val saved = checkNotNull(store.decode<PinDelay>("pin-delay"))
            assertEquals(10, saved.failures)
            assertTrue(saved.retryAtElapsed in (before + delay)..(SystemClock.elapsedRealtime() + delay))
            assertTrue(checkNotNull(saved.bootSession).startsWith("boot:"))
            checkpoint.writeText("${android.os.Process.myPid()} ${saved.retryAtElapsed} ${saved.bootSession}")
            return
        }

        val (pid, deadline, boot) = checkpoint.readText().split(' ')
        val previous = checkNotNull(store.decode<PinDelay>("pin-delay"))
        assertEquals(10, previous.failures)
        assertEquals(deadline.toLong(), previous.retryAtElapsed)
        assertEquals(boot, previous.bootSession)
        assertThrows(AdminProblem::class.java) { throttle.ready(pharmacy) }
        val current = checkNotNull(store.decode<PinDelay>("pin-delay"))
        assertEquals(10, current.failures)
        if (phase == "restart") {
            assertNotEquals(pid.toInt(), android.os.Process.myPid())
            assertEquals(previous, current)
        } else {
            assertEquals("reboot", phase)
            assertNotEquals(previous.bootSession, current.bootSession)
            assertTrue(checkNotNull(current.bootSession).startsWith("boot:"))
            assertTrue(current.retryAtElapsed in (before + delay)..(SystemClock.elapsedRealtime() + delay))
            assertTrue(checkpoint.delete())
            assertTrue(File(context.noBackupFilesDir, "secure-$namespace").deleteRecursively())
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                assertTrue(containsAlias(secure.keyAlias))
                deleteEntry(secure.keyAlias)
                assertFalse(containsAlias(secure.keyAlias))
            }
        }
    }
}
